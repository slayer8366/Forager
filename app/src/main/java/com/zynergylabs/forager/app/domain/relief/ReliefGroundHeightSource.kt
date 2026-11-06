package com.zynergylabs.forager.app.domain.relief

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightSource
import com.zynergylabs.forager.app.domain.GroundHeightUnknown
import com.zynergylabs.forager.app.domain.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/**
 * The ground's height at a point from the Trail Map's relief tiles that downloaded regions hold (dispatch 2026-11-03-07,
 * D2; the owner: "Map relief tiles (Recommended)"). Pure Kotlin: the tiles come through [ReliefTileStore], whose one
 * implementation reads MapLibre's offline database (`map/OfflineReliefTileStore`).
 *
 * **The grid.** The relief source is Terrarium PNG at zoom [RELIEF_ZOOM], 256 px tiles (`map-style/forager-trail-v4.json`,
 * source `dem`; about 54 m a pixel at 45°N). A pixel's height is its value at the pixel's centre, in Web Mercator.
 * A point between centres is interpolated bilinearly from the four nearest pixels, each read from its own tile, so a
 * point near a tile's edge reads its neighbour's pixels and not a copy of its own edge. A pixel whose weight is zero is
 * never read, so a point on a pixel's centre reads that one pixel only.
 *
 * **No height** ([GroundHeight.Unknown]) when any pixel it needs is in a tile no downloaded region holds, a tile stored
 * with no content, or the store cannot be read. Each of those is logged once per tile and reason (dispatch B3), and an
 * unreadable store once per process: never a made-up or nearest-guess height (CLAUDE.md, Errors).
 *
 * **Threading.** Every read is on [ioDispatcher], one at a time. Tiles that were read are kept, up to [CACHED_TILES], so
 * a bubble opened again nearby decodes nothing; a tile that was not there is asked again next time, since a download may
 * since have stored it.
 *
 * **A kept tile is used only while a region still holds it** (S22 session 11: after a region was deleted, a waypoint in
 * another region sharing its relief tile still read its old "Elevation" line from this cache, with no region link left
 * on disk). Each use asks [ReliefTileStore.linkedFingerprint] first, one small query and no decode: the same answer as
 * when the tile was read keeps it; anything else (no region holds it now, or its stored row changed) drops it and reads
 * the tile again, which then answers as any read does. So every way a region link goes (a delete, a replaced region, the
 * repository's clean-up of an unfinished download, the SDK's own) is seen at the next lookup, with no signal to wire.
 */
class ReliefGroundHeightSource(
    private val store: ReliefTileStore,
    private val errorLog: ErrorLog,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GroundHeightSource {

    private val oneAtATime = Mutex()
    private val cached = object : LinkedHashMap<TileKey, KeptTile>(CACHED_TILES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, KeptTile>?): Boolean = size > CACHED_TILES
    }
    private val logged = mutableSetOf<String>()

    override suspend fun groundHeightAt(point: LatLng): GroundHeight = withContext(ioDispatcher) {
        oneAtATime.withLock { lookUp(point) }
    }

    private fun lookUp(point: LatLng): GroundHeight {
        val world = (1 shl RELIEF_ZOOM) * RELIEF_TILE_SIZE
        val lat = point.lat.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        // Global pixel coordinates of the point, less half a pixel: pixel i's centre sits at i + 0.5.
        val gx = (point.lng + 180.0) / 360.0 * world - 0.5
        val gy = (1.0 - asinh(tan(Math.toRadians(lat))) / PI) / 2.0 * world - 0.5
        val x0 = floor(gx).toInt()
        val y0 = floor(gy).toInt()
        val fx = gx - x0
        val fy = gy - y0
        var sum = 0.0
        for ((dx, dy, weight) in listOf(
            Triple(0, 0, (1 - fx) * (1 - fy)),
            Triple(1, 0, fx * (1 - fy)),
            Triple(0, 1, (1 - fx) * fy),
            Triple(1, 1, fx * fy),
        )) {
            if (weight == 0.0) continue
            val px = Math.floorMod(x0 + dx, world)
            val py = (y0 + dy).coerceIn(0, world - 1)
            val key = TileKey(RELIEF_ZOOM, px / RELIEF_TILE_SIZE, py / RELIEF_TILE_SIZE)
            val tile = when (val read = tile(key)) {
                is TileOrWhyNot.Tile -> read.tile
                is TileOrWhyNot.WhyNot -> return GroundHeight.Unknown(read.reason)
            }
            sum += weight * tile.heightAt(px % RELIEF_TILE_SIZE, py % RELIEF_TILE_SIZE)
        }
        return GroundHeight.Known(sum)
    }

    private fun tile(key: TileKey): TileOrWhyNot {
        cached[key]?.let { kept ->
            if (kept.fingerprint != null && stillLinkedAs(key) == kept.fingerprint) return TileOrWhyNot.Tile(kept.tile)
            cached.remove(key)
        }
        val read = try {
            store.tile(key.z, key.x, key.y)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The store's contract is to answer Failed, never to throw; a throw is still logged, never swallowed.
            ReliefTileRead.Failed(e)
        }
        return when (read) {
            is ReliefTileRead.Found -> {
                if (read.tile.size != RELIEF_TILE_SIZE) {
                    logOnce("size", "Relief tile $key is ${read.tile.size} px, not $RELIEF_TILE_SIZE: no ground height from it.")
                    TileOrWhyNot.WhyNot(GroundHeightUnknown.UNSUPPORTED)
                } else {
                    // A tile with no fingerprint cannot be checked later, so it is not kept.
                    read.fingerprint?.let { cached[key] = KeptTile(read.tile, it) }
                    TileOrWhyNot.Tile(read.tile)
                }
            }
            ReliefTileRead.NotStored -> {
                logOnce("not-stored $key", "No downloaded region holds relief tile $key: no ground height there.")
                TileOrWhyNot.WhyNot(GroundHeightUnknown.NOT_DOWNLOADED)
            }
            ReliefTileRead.Empty -> {
                logOnce("empty $key", "Relief tile $key was stored with no content (downloaded while the relief answered 404): no ground height there.")
                TileOrWhyNot.WhyNot(GroundHeightUnknown.NO_DATA)
            }
            is ReliefTileRead.Unsupported -> {
                logOnce("unsupported", "The offline tile store cannot be read for relief: ${read.detail}. No ground heights this run.")
                TileOrWhyNot.WhyNot(GroundHeightUnknown.UNSUPPORTED)
            }
            is ReliefTileRead.Failed -> {
                errorLog.w(TAG, "Reading relief tile $key failed: no ground height there.", read.error)
                TileOrWhyNot.WhyNot(GroundHeightUnknown.READ_FAILED)
            }
        }
    }

    /** [key]'s fingerprint as a region holds it now, or null; a throw is a "no", logged, so the tile is read again. */
    private fun stillLinkedAs(key: TileKey): String? = try {
        store.linkedFingerprint(key.z, key.x, key.y)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errorLog.w(TAG, "Checking kept relief tile $key failed: reading it again.", e)
        null
    }

    private class KeptTile(val tile: ReliefTile, val fingerprint: String)

    private fun logOnce(key: String, message: String) {
        if (logged.add(key)) errorLog.w(TAG, message, ReliefNotice(message))
    }

    private sealed interface TileOrWhyNot {
        class Tile(val tile: ReliefTile) : TileOrWhyNot

        class WhyNot(val reason: GroundHeightUnknown) : TileOrWhyNot
    }

    private data class TileKey(val z: Int, val x: Int, val y: Int) {
        override fun toString() = "$z/$x/$y"
    }

    /** The throwable [ErrorLog] takes for a logged fact that is not an exception. */
    private class ReliefNotice(message: String) : Exception(message)

    companion object {
        private const val TAG = "ReliefGroundHeight"

        /** The relief source's finest zoom (`maxzoom` of `dem` in `map-style/forager-trail-v4.json`). */
        const val RELIEF_ZOOM = 11

        /** The relief source's `tileSize`, and the PNGs' size in pixels. */
        const val RELIEF_TILE_SIZE = 256

        /** Kept tiles: four covers a point at a tile's corner. */
        const val CACHED_TILES = 4

        private const val MAX_MERCATOR_LATITUDE = 85.0511287798
    }
}

/** Where [ReliefGroundHeightSource] reads relief tiles from. Called on the source's IO dispatcher; may block. */
fun interface ReliefTileStore {
    /** The relief tile [z]/[x]/[y] (XYZ scheme), as a downloaded region holds it. Never throws: a failure is [ReliefTileRead.Failed]. */
    fun tile(z: Int, x: Int, y: Int): ReliefTileRead

    /**
     * What identifies the stored row [tile] would read for [z]/[x]/[y], while a downloaded region holds it; `null` when
     * none does, or when it cannot tell. Cheap: no decode. [ReliefGroundHeightSource] keeps a decoded tile only while
     * this answers what [ReliefTileRead.Found.fingerprint] did. The default, `null`, keeps nothing.
     */
    fun linkedFingerprint(z: Int, x: Int, y: Int): String? = null
}

/** What [ReliefTileStore.tile] found. */
sealed interface ReliefTileRead {
    /** [fingerprint]: [ReliefTileStore.linkedFingerprint]'s answer for this row when it was read; `null` keeps it uncached. */
    class Found(val tile: ReliefTile, val fingerprint: String? = null) : ReliefTileRead

    /** No downloaded region holds this tile (the ambient cache is not counted: dispatch D2, the planner's call). */
    data object NotStored : ReliefTileRead

    /** A region holds the tile with no content: the server answered "no tile" when it was downloaded. */
    data object Empty : ReliefTileRead

    /** The store is in a form this build does not read; [detail] says how. */
    data class Unsupported(val detail: String) : ReliefTileRead

    class Failed(val error: Throwable) : ReliefTileRead
}

/**
 * One decoded relief tile: [size] × [size] pixels, [rgb] row by row from the top-left, each `0xRRGGBB` (any alpha bits
 * ignored). Heights are Terrarium's: `R × 256 + G + B / 256 − 32768` metres.
 */
class ReliefTile(val size: Int, private val rgb: IntArray) {
    init {
        require(rgb.size == size * size) { "${rgb.size} pixels for a $size px tile" }
    }

    fun heightAt(px: Int, py: Int): Double = terrariumMetres(rgb[py * size + px])
}

/** Terrarium's height for one pixel, `0xRRGGBB` (alpha ignored). */
fun terrariumMetres(rgb: Int): Double {
    val r = (rgb shr 16) and 0xff
    val g = (rgb shr 8) and 0xff
    val b = rgb and 0xff
    return r * 256.0 + g + b / 256.0 - 32768.0
}
