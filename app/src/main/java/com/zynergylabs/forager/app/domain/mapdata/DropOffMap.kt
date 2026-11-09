package com.zynergylabs.forager.app.domain.mapdata

/**
 * The app's own copy of each downloaded area's zoom-15 basemap tiles, for the way-to's street drop-off (dispatch
 * 2026-11-04-09; the owner: "Automatic (Recommended)", RECORD -2107). MapLibre's offline store reuses a stored tile and
 * never revalidates it (dispatch 2026-11-04-08's report, section 2), so an area downloaded before basemap patch 0004 holds
 * tiles with no fence mark for good. The copy fetches, once, each of the area's zoom-15 tiles that MapLibre does not hold
 * marked, and the way-to reads it where MapLibre's tile lacks the mark ([LayeredOfflineBasemap]). MapLibre's store and
 * what the map draws are never written.
 */

/**
 * What the copy holds for one area, read from its folder on every listing ([com.zynergylabs.forager.app.domain.OfflineRegionSummary.dropOffMap]).
 * [sizeBytes] is inside the area's shown size (A5, the owner: "Include it (Recommended)"); [tiles] is not counted against
 * the tile budget (A6, "Don't count it (Recommended)"). [whole] is the area's one record: nothing is wanted, so it is never
 * fetched again (A7, "Only when missing (Recommended)").
 */
data class RegionDropOffMap(
    /** Marked tiles stored. */
    val tiles: Int,
    /** Tiles the server answered as empty (204), recorded so they are not asked for again. */
    val emptyTiles: Int,
    val sizeBytes: Long,
    val whole: Boolean,
) {
    companion object {
        /** An area the copy holds nothing for: never fetched, restored from a backup, or a build that keeps no copy. */
        val NONE = RegionDropOffMap(tiles = 0, emptyTiles = 0, sizeBytes = 0L, whole = false)
    }
}

/** How one area's pass ended. Each is logged where the pass ran, in words, never as a position. */
sealed interface DropOffMapPass {
    /** Nothing is wanted now: the area is whole and is not fetched again. [requests] is this pass's, 0 when it already was. */
    data class Whole(val requests: Int, val stored: Int, val empty: Int) : DropOffMapPass

    /**
     * Tiles are still wanted after this pass. [stoppedBy] says what stopped it early (a connection failure, the server
     * errors in a row, storage), or is null when it asked for every wanted tile and some answers could not be kept
     * ([notStored]: no mark, not a tile, over the size limit, or a server error).
     */
    data class Partial(
        val wanted: Int,
        val requests: Int,
        val stored: Int,
        val empty: Int,
        val notStored: Int,
        val stoppedBy: String?,
    ) : DropOffMapPass

    /** No tile was asked for, for [reason]: a pass for the area is already running, the area is refused, or a store could not be read. */
    data class NotRun(val reason: String) : DropOffMapPass
}

/** The copy as the way-to reads it: any area's tile, since areas overlap and the reader asks by tile alone. */
interface DropOffTileCopy {
    /** Tile [id] as decompressed MVT bytes from whichever area holds it, or null when none does. A copy that can't be read fails. */
    suspend fun tile(id: TileId): Result<ByteArray?>

    /** Removes [id] from every area that holds it, and their whole records, so the next pass fetches it again. Logs [reason]. */
    suspend fun discard(id: TileId, reason: String)
}
