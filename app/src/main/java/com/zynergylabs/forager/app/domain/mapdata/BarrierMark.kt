package com.zynergylabs.forager.app.domain.mapdata

/**
 * Basemap patch 0004's fence mark, defined once (dispatch 2026-11-04-09, B1 P7: "the mark has one definition"). Patch
 * 0004 puts a mark in the barriers layer of every zoom-15 tile it builds (the owner's choice (b), 10-28), so a tile with
 * that layer was built with fences, and a tile without it was built before they were carried. Three readers ask this and
 * no other test: the way-to's tile reader ([ReadNearbyMapData]), the layered read ([LayeredOfflineBasemap]) and the drop-off
 * copy's fetch (`data/dropoffmap/DropOffMapFetch`). One definition, so they cannot drift apart.
 */
object BarrierMark {
    /** Whether [tile] carries the mark: it has a barriers layer, with or without fence lines in it. */
    fun carriedBy(tile: MvtTile): Boolean = tile.layer(TileFeatureReader.BARRIERS) != null

    /** What [mvt], a tile's decompressed bytes, says about the mark. Bytes that are not a vector tile say nothing either way. */
    fun of(mvt: ByteArray): TileMark = try {
        if (carriedBy(MvtDecoder.decode(mvt))) TileMark.Marked else TileMark.Unmarked
    } catch (e: MvtFormatException) {
        TileMark.Undecodable(e.message ?: "not a vector tile")
    }
}

/** What a tile's bytes say about the mark. */
sealed interface TileMark {
    data object Marked : TileMark

    data object Unmarked : TileMark

    /** Not a valid vector tile, for [reason]: the reader fails on such a tile ([ReadNearbyMapData]), so it is never read as unmarked. */
    data class Undecodable(val reason: String) : TileMark
}
