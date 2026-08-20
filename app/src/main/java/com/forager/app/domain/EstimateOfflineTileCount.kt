package com.forager.app.domain

import com.forager.app.domain.model.GeoBoundingBox
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Estimates how many tiles `OfflineManager` will actually download for [region] across
 * [minZoom]..[maxZoom] — the pre-flight check `AvailabilityViewModel.onDownloadOfflineMaps` runs
 * before starting a download, since hardware testing found MapLibre's own
 * `setOfflineMapboxTileCountLimit` does not stop an explicit region download from exceeding it (see
 * [OfflineMapRepository.TILE_COUNT_LIMIT]'s doc comment for what that testing found).
 *
 * Reimplements the same standard slippy-map (Web Mercator / EPSG:3857) tile-grid math
 * `OfflineTilePyramidRegionDefinition` itself uses internally — not a distance/area approximation —
 * so this tracks what MapLibre will actually request closely enough to be a real ceiling, not just
 * a rough guess. Sums the tile count at every integer zoom level [minZoom]..[maxZoom] covers
 * (rounded to the nearest enclosing integers, since a tile grid only exists at integer zooms).
 *
 * Handles the antimeridian the same way [GeoDistance.boundingBox] already documents it can produce
 * a box crossing it (`east < west`): the tile-x range wraps around the grid rather than coming out
 * negative or empty.
 */
fun estimateOfflineTileCount(region: Region, minZoom: Double, maxZoom: Double): Int {
    val box = GeoDistance.boundingBox(LatLng(region.lat, region.lng), region.radiusKm)
    var total = 0
    for (zoom in ceil(minZoom).toInt()..floor(maxZoom).toInt()) {
        total += tileCountAtZoom(box, zoom)
    }
    return total
}

private fun tileCountAtZoom(box: GeoBoundingBox, zoom: Int): Int {
    val tilesPerAxis = 1 shl zoom
    val xMin = lonToTileX(box.west, tilesPerAxis)
    val xMax = lonToTileX(box.east, tilesPerAxis)
    val yMin = latToTileY(box.north, tilesPerAxis)
    val yMax = latToTileY(box.south, tilesPerAxis)

    val tilesX = if (box.east >= box.west) xMax - xMin + 1 else tilesPerAxis - xMin + xMax + 1
    val tilesY = yMax - yMin + 1
    return tilesX * tilesY
}

private fun lonToTileX(lonDeg: Double, tilesPerAxis: Int): Int =
    floor((lonDeg + 180.0) / 360.0 * tilesPerAxis).toInt().coerceIn(0, tilesPerAxis - 1)

private fun latToTileY(latDeg: Double, tilesPerAxis: Int): Int {
    val latRad = Math.toRadians(latDeg.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE))
    val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * tilesPerAxis
    return floor(y).toInt().coerceIn(0, tilesPerAxis - 1)
}

/** Web Mercator's own valid latitude range — beyond this the projection's y coordinate diverges to infinity. */
private const val MAX_MERCATOR_LATITUDE = 85.0511287798
