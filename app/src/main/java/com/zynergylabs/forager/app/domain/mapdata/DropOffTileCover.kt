package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * An area's zoom-15 tiles, the drop-off map copy's cover (dispatch 2026-11-04-09): every tile the area's rectangle
 * touches, the rectangle MapLibre downloads the area for (`routeAreaFor`, `MapLibreOfflineMapRepository`'s bounds). No
 * margin past its edge (B1 P6): MapLibre's store holds no tile there either, so an area's edge behaves as today. The
 * Phase 1 report found this cover equal to the zoom-15 tiles MapLibre linked to each of the S22's two areas.
 */
object DropOffTileCover {
    const val ZOOM = ReadNearbyMapData.ZOOM

    /**
     * The tiles [box] touches, by the same slippy-map formula the reader places tiles with ([TileFeatureReader.tileOf]).
     * Null for a box that crosses the antimeridian, which this does not cover: unsupported, said so, never guessed.
     */
    fun of(box: GeoBoundingBox): Cover? {
        if (box.west > box.east) return null
        val last = (1 shl ZOOM) - 1
        val nw = TileFeatureReader.tileOf(LatLng(box.north, box.west), ZOOM)
        val se = TileFeatureReader.tileOf(LatLng(box.south, box.east), ZOOM)
        return Cover(nw.x.coerceIn(0, last)..se.x.coerceIn(0, last), nw.y.coerceIn(0, last)..se.y.coerceIn(0, last))
    }

    /** The tiles x in [xs] by y in [ys], all at [ZOOM]. [size] is counted without listing them, so a bad row's huge cover costs nothing to refuse. */
    data class Cover(val xs: IntRange, val ys: IntRange) {
        val size: Long get() = xs.count().toLong() * ys.count().toLong()

        /** West to east, and north to south within each column. */
        fun tiles(): List<TileId> = xs.flatMap { x -> ys.map { y -> TileId(ZOOM, x, y) } }
    }
}
