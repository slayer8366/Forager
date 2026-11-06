package com.zynergylabs.forager.app.domain.mapdata

/**
 * One tile's answer from the Trail Map's own tile address, as the Offline Maps picker's warning reads it (known bug 14;
 * dispatch 2026-11-02-02 Amendment 3, A7, RECORD -2150, the planner's call on the Phase 1b report's S5, option a).
 *
 * The Phase 1b report (`prompts/preserved/2026-11-02-02-phase1b-report.md`, section 1) found "no detail" in two forms: south
 * of the archive's south bound the server answers 204, and outside the source data north of it the answer is a 200 tile
 * holding only the build's fills, the land (`earth`) and, on a coast, the sea (`water` of kind `ocean`). A covered tile
 * carries the map's own data: roads, land use, water, places, boundaries, buildings or points of interest. The Phase 2 session
 * read the same four classes at zoom 11 from the live server, in four requests.
 *
 * **The one dependency on the map build:** [LAND_FILL_LAYER] and the sea fill ([SEA_FILL_LAYER] of kind [SEA_FILL_KIND]) are
 * named here once. A future build that adds another fill drawn everywhere would make uncovered tiles read as [Detail], and
 * the line would stop showing; that is the map side's to tell this file (Phase 1b, section 5).
 */
sealed interface TileDetail {
    /** A 204, or a tile of nothing but the land fill (with the sea fill on a coast): none of the map's data is drawn there. */
    data object NoDetail : TileDetail

    /** Only the sea fill. It counts neither way (A7). */
    data object Sea : TileDetail

    /** Anything else the tile carries: the map's data is drawn there. */
    data object Detail : TileDetail

    /** Not an answer that can be read, for [reason]: never taken as either, and never kept for later. */
    data class Unread(val reason: String) : TileDetail
}

/** Reads a 200's tile as a [TileDetail]. Pure, so every class is tested without the network. */
object TileDetailReader {
    const val LAND_FILL_LAYER = "earth"
    const val SEA_FILL_LAYER = TileFeatureReader.WATER
    const val SEA_FILL_KIND = "ocean"

    /** [mvt] is the tile's decompressed bytes. Bytes that are not a vector tile are [TileDetail.Unread]. */
    fun read(mvt: ByteArray): TileDetail = try {
        read(MvtDecoder.decode(mvt))
    } catch (e: MvtFormatException) {
        TileDetail.Unread("not a vector tile (${e.message})")
    }

    // Fail-first stub (dispatch 2026-11-02-02, B2): every tile reads as detail until the next commit.
    @Suppress("UNUSED_PARAMETER")
    fun read(tile: MvtTile): TileDetail = TileDetail.Detail
}
