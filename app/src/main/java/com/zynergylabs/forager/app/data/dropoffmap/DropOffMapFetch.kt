package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.BarrierMark
import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.mapdata.DropOffTileCover
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.StoredTiles
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.mapdata.TileMark
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One area's pass of the drop-off map copy (dispatch 2026-11-04-09): the area's zoom-15 tiles ([DropOffTileCover]) that
 * MapLibre's store does not hold with the fence mark, and the copy does not hold yet, are asked for once each from the
 * basemap's own tile address, within [DropOffMapLimits]. MapLibre's store is only read, through the same read-only
 * [OfflineBasemap] the way-to uses.
 *
 * **Wanted** (the Phase 1 report, section 4; A7, the owner: "Only when missing (Recommended)"): a tile of the cover whose
 * MapLibre copy is absent or decodes without the mark, and for which the copy holds neither a marked tile nor the
 * server's empty answer. A MapLibre tile that does not decode is not wanted: the way-to reads MapLibre's bytes for it
 * (`LayeredOfflineBasemap`), so a copy would never be read. When nothing is wanted the area is whole, the record is
 * written, and its later passes ask for nothing and read nothing.
 *
 * **What is kept of each answer:** a 200 whose tile decodes with the mark is stored; a 204 is recorded as empty; anything
 * else (a tile without the mark, not a tile, over [DropOffMapLimits.MAX_TILE_BYTES], any other status) is not kept,
 * counted, and stays wanted for the next pass. A connection failure stops the pass at once; so do
 * [DropOffMapLimits.MAX_SERVER_ERRORS_IN_A_ROW] statuses in a row that are neither 200 nor 204. Every tile written before
 * a stop is kept, each whole. The pass's outcome is logged in one line, in counts, never as a position.
 *
 * The template is the first tile address of the basemap TileJSON MapLibre's store holds ([StoredTiles.basemapSource], the
 * one the way-to keys its reads by), so the copy fetches exactly the tiles the reader reads. [clock] and [pause] are the
 * start pacing's seams, so a test reads the gaps exactly.
 */
internal class DropOffMapFetch(
    private val http: OkHttpClient,
    private val store: DropOffMapStore,
    private val mapLibre: OfflineBasemap,
    private val log: (String, Throwable?) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    /** When the last request started, across passes: the gap holds from one area's pass to the next. Written by one pass at a time. */
    private var lastStart: Long? = null

    suspend fun pass(regionId: Long, area: GeoBoundingBox): DropOffMapPass = withContext(io) {
        if (store.isWhole(regionId)) return@withContext DropOffMapPass.Whole(requests = 0, stored = 0, empty = 0)
        val begun = try {
            store.beginPass(regionId)
        } catch (e: IOException) {
            return@withContext notRun(regionId, "its folder could not be made (${e.message})", e)
        }
        if (!begun) return@withContext notRun(regionId, "a pass for it is already running")
        try {
            run(regionId, area)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Whatever it is, it is this pass's outcome, never the caller's crash; what was written stays, each tile whole.
            notRun(regionId, "the pass failed unexpectedly (${e.message})", e)
        } finally {
            store.endPass(regionId)
        }
    }

    private suspend fun run(regionId: Long, area: GeoBoundingBox): DropOffMapPass {
        val cover = DropOffTileCover.of(area) ?: return notRun(regionId, "its area crosses the antimeridian, which the copy does not cover")
        if (cover.size > DropOffMapLimits.MAX_AREA_TILES) {
            return notRun(regionId, "its area covers ${cover.size} zoom-15 tiles, over the limit of ${DropOffMapLimits.MAX_AREA_TILES}")
        }
        val tileJson = mapLibre.tileJson().getOrElse { return notRun(regionId, "MapLibre's store could not be read", it) }
        val template = tileJson?.let { StoredTiles.basemapSource(listOf(it))?.urlTemplate }
            ?: return notRun(regionId, "MapLibre's store holds no basemap TileJSON, so there is no tile address to ask")
        if (PLACEHOLDERS.any { it !in template }) return notRun(regionId, "the basemap's tile address has no {z}/{x}/{y} to fill")

        val wanted = mutableListOf<TileId>()
        for (tile in cover.tiles()) {
            if (store.holds(regionId, tile)) continue
            val held = mapLibre.tile(tile).getOrElse { return notRun(regionId, "MapLibre's store could not be read", it) }
            if (held == null || BarrierMark.of(held) == TileMark.Unmarked) wanted += tile
        }
        if (wanted.isEmpty()) return finishWhole(regionId, Tally())

        val tally = fetchAll(regionId, template, wanted)
        val nowWhole = tally.stoppedBy.get() == null && tally.stored.get() + tally.empty.get() == wanted.size
        if (nowWhole) return finishWhole(regionId, tally)
        val result = DropOffMapPass.Partial(
            wanted = wanted.size,
            requests = tally.requests.get(),
            stored = tally.stored.get(),
            empty = tally.empty.get(),
            notStored = tally.notStored(),
            stoppedBy = tally.stoppedBy.get(),
        )
        val ending = result.stoppedBy?.let { "stopped: $it" } ?: "every wanted tile was asked for"
        log("Drop-off map for region $regionId: ${tally.summary(wanted.size)}; $ending. ${wanted.size - result.stored - result.empty} still wanted, for the next pass.", null)
        return result
    }

    private fun finishWhole(regionId: Long, tally: Tally): DropOffMapPass {
        try {
            store.markWhole(regionId)
        } catch (e: IOException) {
            log("Drop-off map for region $regionId: nothing is wanted, but its whole record couldn't be written; the next pass looks again.", e)
        }
        log("Drop-off map for region $regionId: whole. ${tally.requests.get()} requests; ${tally.stored.get()} stored, ${tally.empty.get()} empty.", null)
        return DropOffMapPass.Whole(requests = tally.requests.get(), stored = tally.stored.get(), empty = tally.empty.get())
    }

    /** Every wanted tile in turn, at most [DropOffMapLimits.MAX_IN_FLIGHT] at once and paced, until done or stopped. */
    private suspend fun fetchAll(regionId: Long, template: String, wanted: List<TileId>): Tally = coroutineScope {
        val tally = Tally()
        val permits = Semaphore(DropOffMapLimits.MAX_IN_FLIGHT)
        for (tile in wanted) {
            if (tally.stoppedBy.get() != null) break
            permits.acquire()
            if (tally.stoppedBy.get() != null) {
                permits.release()
                break
            }
            awaitStartSlot()
            tally.requests.incrementAndGet()
            launch(io) {
                try {
                    fetchOne(regionId, template, tile, tally)
                } finally {
                    permits.release()
                }
            }
        }
        tally
    }

    /** Waits until [DropOffMapLimits.MIN_START_GAP_MILLIS] has passed since the last start. */
    private suspend fun awaitStartSlot() {
        lastStart?.let { last ->
            val wait = last + DropOffMapLimits.MIN_START_GAP_MILLIS - clock()
            if (wait > 0) pause(wait)
        }
        lastStart = clock()
    }

    private fun fetchOne(regionId: Long, template: String, tile: TileId, tally: Tally) {
        val answer = try {
            ask(tileUrl(template, tile))
        } catch (e: IOException) {
            tally.stop("a connection failure at tile ${tile.z}/${tile.x}/${tile.y} (${e.message})")
            return
        } catch (e: IllegalArgumentException) {
            tally.stop("the basemap's tile address is not a URL (${e.message})")
            return
        }
        when (answer) {
            is Answer.Tile -> {
                tally.answered()
                when (BarrierMark.of(answer.bytes)) {
                    TileMark.Marked -> try {
                        store.write(regionId, tile, answer.bytes)
                        tally.stored.incrementAndGet()
                    } catch (e: IOException) {
                        tally.stop("the copy could not be written (${e.message})")
                    }
                    TileMark.Unmarked -> tally.unmarked.incrementAndGet()
                    is TileMark.Undecodable -> tally.notATile.incrementAndGet()
                }
            }
            Answer.Empty -> {
                tally.answered()
                try {
                    store.markEmpty(regionId, tile)
                    tally.empty.incrementAndGet()
                } catch (e: IOException) {
                    tally.stop("the copy could not be written (${e.message})")
                }
            }
            Answer.TooBig -> {
                tally.answered()
                tally.tooBig.incrementAndGet()
            }
            is Answer.ServerError -> tally.serverError(answer.code)
        }
    }

    /** One request, its body read at most [DropOffMapLimits.MAX_TILE_BYTES] + 1 far. Throws an [IOException] for a failed connection. */
    private fun ask(url: String): Answer = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        when (response.code) {
            200 -> {
                val source = response.body?.source() ?: return@use Answer.Tile(ByteArray(0))
                if (source.request(DropOffMapLimits.MAX_TILE_BYTES + 1)) Answer.TooBig else Answer.Tile(source.readByteArray())
            }
            204 -> Answer.Empty
            else -> Answer.ServerError(response.code)
        }
    }

    private fun tileUrl(template: String, tile: TileId): String =
        template.replace("{z}", tile.z.toString()).replace("{x}", tile.x.toString()).replace("{y}", tile.y.toString())

    private fun notRun(regionId: Long, reason: String, error: Throwable? = null): DropOffMapPass {
        log("Drop-off map for region $regionId not fetched: $reason.", error)
        return DropOffMapPass.NotRun(reason)
    }

    private sealed interface Answer {
        class Tile(val bytes: ByteArray) : Answer

        data object Empty : Answer

        data object TooBig : Answer

        data class ServerError(val code: Int) : Answer
    }

    /** One pass's counts, written from every request's thread. The first stop reason wins. */
    private class Tally {
        val requests = AtomicInteger()
        val stored = AtomicInteger()
        val empty = AtomicInteger()
        val unmarked = AtomicInteger()
        val notATile = AtomicInteger()
        val tooBig = AtomicInteger()
        val serverErrors = AtomicInteger()
        val stoppedBy = AtomicReference<String?>(null)
        private var errorsInARow = 0

        fun stop(reason: String) {
            stoppedBy.compareAndSet(null, reason)
        }

        @Synchronized
        fun answered() {
            errorsInARow = 0
        }

        @Synchronized
        fun serverError(code: Int) {
            serverErrors.incrementAndGet()
            errorsInARow++
            if (errorsInARow >= DropOffMapLimits.MAX_SERVER_ERRORS_IN_A_ROW) stop("$errorsInARow server errors in a row (the last HTTP $code)")
        }

        fun notStored(): Int = unmarked.get() + notATile.get() + tooBig.get() + serverErrors.get()

        fun summary(wanted: Int): String =
            "$wanted wanted, ${requests.get()} requests; ${stored.get()} stored, ${empty.get()} empty, ${notStored()} not stored " +
                "(${unmarked.get()} without the mark, ${notATile.get()} not a tile, ${tooBig.get()} over ${DropOffMapLimits.MAX_TILE_BYTES} bytes, " +
                "${serverErrors.get()} server errors)"
    }

    private companion object {
        val PLACEHOLDERS = listOf("{z}", "{x}", "{y}")
    }
}
