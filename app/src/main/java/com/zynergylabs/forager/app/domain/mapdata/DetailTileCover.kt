package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The tiles the Offline Maps picker's warning asks about for one area (known bug 14, dispatch 2026-11-02-02): every zoom-[ZOOM]
 * tile the area's rectangle touches. The rectangle is the one the download is made for (`routeAreaFor`, as
 * `MapLibreOfflineMapRepository` downloads it), so "any part outside" (the owner, RECORD -2144) is judged on what would be
 * downloaded. The formula is [DropOffTileCover]'s, at this zoom.
 */
object DetailTileCover {
    /**
     * Zoom 11. The owner, RECORD -2151, chose "Medium (Recommended)", whose option read "Checks in about 16 km squares. An area
     * can be partly blank without the warning only within about 16 km of the edge. About 16 to 25 small requests per
     * placement; a dense covered city at the largest radius costs a few MB." The Phase 1b report had proposed zoom 10.
     */
    const val ZOOM = 11

    /**
     * The tiles [box] touches, by the slippy-map formula the tile reader places tiles with ([TileFeatureReader.tileOf]).
     * Null for a box that crosses the antimeridian, which this does not cover: unsupported, said so, never guessed.
     */
    fun of(box: GeoBoundingBox): Cover? {
        if (box.west > box.east) return null
        val last = (1 shl ZOOM) - 1
        val nw = TileFeatureReader.tileOf(LatLng(box.north, box.west), ZOOM)
        val se = TileFeatureReader.tileOf(LatLng(box.south, box.east), ZOOM)
        return Cover(nw.x.coerceIn(0, last)..se.x.coerceIn(0, last), nw.y.coerceIn(0, last)..se.y.coerceIn(0, last))
    }

    /** The tiles x in [xs] by y in [ys], all at [ZOOM]. [size] is counted without listing them, so an area over the cap costs nothing to refuse. */
    data class Cover(val xs: IntRange, val ys: IntRange) {
        val size: Long get() = xs.count().toLong() * ys.count().toLong()

        /** West to east, and north to south within each column. */
        fun tiles(): List<TileId> = xs.flatMap { x -> ys.map { y -> TileId(ZOOM, x, y) } }
    }
}
