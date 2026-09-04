package com.forager.app.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Standard slippy-map (Web Mercator / EPSG:3857) tile-grid math — which integer tile column/row a
 * coordinate falls into at a given integer zoom. Extracted from [EstimateOfflineTileCount]'s own
 * former private `lonToTileX`/`latToTileY` (Journal Stage 2a dispatch) once a second, independent
 * caller needed the exact same math: [isCoordinateWithinRegionTiles] below, testing whether one
 * point's tile matches a region's tile footprint, a different question from
 * [estimateOfflineTileCount]'s "how many tiles total" but built on the identical per-coordinate
 * conversion.
 *
 * **Widening to a shared, `internal` object — not making [EstimateOfflineTileCount]'s own functions
 * `internal`.** The dispatch that created this file asked which was the right call: reusing
 * [EstimateOfflineTileCount]'s functions in place would have widened that file's own API surface
 * (currently two private helpers behind one public download-size estimate) to serve a second,
 * unrelated purpose it was never written to advertise — a real cost, since the next reader of that
 * file would have to work out that its own private-looking helpers are actually depended on
 * elsewhere. A shared, purpose-built home costs nothing extra (both real callers already exist, so
 * this isn't the speculative abstraction CLAUDE.md warns against) and reads correctly on its own:
 * "slippy-map tile math," not "part of a tile-count estimator."
 */
internal object SlippyMapTile {
    /** The tile column [lonDeg] falls into, at [zoom] — clamped to the grid's own valid column range. */
    fun x(lonDeg: Double, zoom: Int): Int {
        val tilesPerAxis = 1 shl zoom
        return floor((lonDeg + 180.0) / 360.0 * tilesPerAxis).toInt().coerceIn(0, tilesPerAxis - 1)
    }

    /** The tile row [latDeg] falls into, at [zoom] — clamped to the grid's own valid row range and to Web Mercator's valid latitude span. */
    fun y(latDeg: Double, zoom: Int): Int {
        val tilesPerAxis = 1 shl zoom
        val latRad = Math.toRadians(latDeg.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * tilesPerAxis
        return floor(y).toInt().coerceIn(0, tilesPerAxis - 1)
    }

    /** Web Mercator's own valid latitude range — beyond this the projection's y coordinate diverges to infinity. */
    const val MAX_MERCATOR_LATITUDE: Double = 85.0511287798
}
