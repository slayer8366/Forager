package com.zynergylabs.forager.app.data.mapdetail

import com.zynergylabs.forager.app.domain.mapdata.AreaDetail
import com.zynergylabs.forager.app.domain.mapdata.DetailTileCover
import com.zynergylabs.forager.app.domain.mapdata.TileDetail
import com.zynergylabs.forager.app.domain.mapdata.TileDetailReader
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetail
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetailLimits
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * [TrailMapDetail] asked of the map server (known bug 14, dispatch 2026-11-02-02, A7 to A9, RECORD -2150 and -2151): every
 * zoom-11 tile of the area's cover ([DetailTileCover]) is asked for from the Trail Map's own tile address ([template]) and read
 * ([TileDetailReader]); the Worker is unchanged (A7). Within [TrailMapDetailLimits]: an area over the tile cap is not run, at
 * most four requests are out at once, a tile over the byte cap is unread, and the first tile without detail ends the check,
 * cancelling the requests still out. Ended from outside (a newer area, the time-out), it cancels what is still out too.
 *
 * **The area's answer** (A4, the owner: "If any part is outside"): [AreaDetail.SomeOutside] on any tile without detail, even
 * when others could not be read, since that answer is real; [AreaDetail.AllInside] when every tile has detail or is sea;
 * otherwise [AreaDetail.NotRun], with counts and the first reason, never a position. Nothing is logged here: the one log line
 * for a check not run is the caller's (`AvailabilityOfflineDetail`).
 *
 * Definite tile answers are kept for the process ([TileDetailCache], call C3), so [known] can answer a pan inside tiles
 * already asked about without a request.
 */
internal class WorkerTrailMapDetail(
    private val http: OkHttpClient,
    /** The Trail Map's tile address, with `{z}`, `{x}` and `{y}` to fill: `Basemap.FORAGER_TRAIL`'s, passed in by `AppContainer`. */
    private val template: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TrailMapDetail {
    private val cache = TileDetailCache(TrailMapDetailLimits.MAX_CACHED_ANSWERS)

    override fun known(area: GeoBoundingBox): AreaDetail? {
        val cover = DetailTileCover.of(area) ?: return null
        if (cover.size > TrailMapDetailLimits.MAX_AREA_TILES) return null
        val answers = cover.tiles().map { cache.get(it) }
        return when {
            answers.any { it == TileDetail.NoDetail } -> AreaDetail.SomeOutside
            answers.all { it == TileDetail.Detail || it == TileDetail.Sea } -> AreaDetail.AllInside
            else -> null
        }
    }

    override suspend fun check(area: GeoBoundingBox): AreaDetail = withContext(io) {
        val cover = DetailTileCover.of(area)
            ?: return@withContext AreaDetail.NotRun("the area crosses the antimeridian, which the check does not cover")
        if (cover.size > TrailMapDetailLimits.MAX_AREA_TILES) {
            return@withContext AreaDetail.NotRun(
                "the area covers ${cover.size} zoom-${DetailTileCover.ZOOM} tiles, over the limit of ${TrailMapDetailLimits.MAX_AREA_TILES}",
            )
        }
        if (PLACEHOLDERS.any { it !in template }) return@withContext AreaDetail.NotRun("the Trail Map's tile address has no {z}/{x}/{y} to fill")
        known(area)?.let { return@withContext it }

        val tiles = cover.tiles()
        val tally = askAll(tiles.filter { cache.get(it) == null })
        when {
            tally.noDetail.get() > 0 -> AreaDetail.SomeOutside
            tally.unread.isEmpty() -> AreaDetail.AllInside
            else -> AreaDetail.NotRun(
                "${tally.unread.size} of ${tiles.size} zoom-${DetailTileCover.ZOOM} tiles could not be read (the first: ${tally.unread.peek()}), " +
                    "and none answered without detail",
            )
        }
    }

    /** Every tile of [wanted] in turn, at most [TrailMapDetailLimits.MAX_IN_FLIGHT] at once, until done or a tile has no detail. */
    private suspend fun askAll(wanted: List<TileId>): Tally = coroutineScope {
        val tally = Tally()
        val permits = Semaphore(TrailMapDetailLimits.MAX_IN_FLIGHT)
        val calls: MutableSet<Call> = ConcurrentHashMap.newKeySet()
        val jobs = mutableListOf<Job>()
        try {
            for (tile in wanted) {
                if (tally.stopped) break
                permits.acquire()
                if (tally.stopped) {
                    permits.release()
                    break
                }
                jobs += launch(io) {
                    try {
                        val detail = askOne(tile, calls)
                        cache.put(tile, detail)
                        tally.add(detail)
                        // The early stop: one tile without detail answers the area, so the requests still out are cancelled.
                        if (detail == TileDetail.NoDetail) calls.forEach { it.cancel() }
                    } finally {
                        permits.release()
                    }
                }
            }
            jobs.joinAll()
        } finally {
            // Ended from outside (a newer area, the time-out): what is still out is cancelled, not left to finish. A blocked
            // request does not see its coroutine's cancellation, so its call is cancelled here.
            calls.forEach { it.cancel() }
        }
        tally
    }

    /** One request. Every outcome is a [TileDetail]: a failed connection or an unexpected answer is [TileDetail.Unread]. */
    private fun askOne(tile: TileId, calls: MutableSet<Call>): TileDetail {
        val call = try {
            http.newCall(Request.Builder().url(tileUrl(tile)).build())
        } catch (e: IllegalArgumentException) {
            return TileDetail.Unread("the tile address is not a URL (${e.message})")
        }
        calls += call
        return try {
            call.execute().use { response ->
                when (response.code) {
                    200 -> {
                        val source = response.body?.source()
                        when {
                            source == null -> TileDetail.Unread("a 200 with no body")
                            source.request(TrailMapDetailLimits.MAX_TILE_BYTES + 1) -> TileDetail.Unread("a tile over ${TrailMapDetailLimits.MAX_TILE_BYTES} bytes")
                            else -> TileDetailReader.read(source.readByteArray())
                        }
                    }
                    // The server holds no tile there: south of the archive's bound (the Phase 1b report's F1).
                    204 -> TileDetail.Detail // Fail-first stub (B2): a 204 reads as detail until the next commit.
                    else -> TileDetail.Unread("HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            TileDetail.Unread("a connection failure (${e.message})")
        } finally {
            calls -= call
        }
    }

    private fun tileUrl(tile: TileId): String =
        template.replace("{z}", tile.z.toString()).replace("{x}", tile.x.toString()).replace("{y}", tile.y.toString())

    /** One check's answers, written from every request's thread. */
    private class Tally {
        @Volatile
        var stopped = false
            private set
        val noDetail = AtomicInteger()
        val unread = ConcurrentLinkedQueue<String>()

        fun add(detail: TileDetail) {
            when (detail) {
                TileDetail.NoDetail -> {
                    noDetail.incrementAndGet()
                    stopped = true
                }
                is TileDetail.Unread -> unread += detail.reason
                TileDetail.Detail, TileDetail.Sea -> Unit
            }
        }
    }

    private companion object {
        val PLACEHOLDERS = listOf("{z}", "{x}", "{y}")
    }
}
