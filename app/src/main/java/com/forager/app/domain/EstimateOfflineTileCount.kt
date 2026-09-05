package com.forager.app.domain

import com.forager.app.domain.model.GeoBoundingBox
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Estimates how many tiles `OfflineManager` will actually download for [region] across
 * [minZoom]..[maxZoom] — the pre-flight check `AvailabilityViewModel.onDownloadOfflineMaps` runs
 * before starting a download, since hardware testing found MapLibre's own
 * `setOfflineMapboxTileCountLimit` does not stop an explicit region download from exceeding it (see
 * [OfflineMapRepository.TILE_COUNT_LIMIT]'s doc comment for what that testing found).
 *
 * Built on [SlippyMapTile], the same standard slippy-map (Web Mercator / EPSG:3857) tile-grid math
 * `OfflineTilePyramidRegionDefinition` itself uses internally — not a distance/area approximation —
 * so this tracks what MapLibre will actually request closely enough to be a real ceiling, not just
 * a rough guess. Sums the tile count at every integer zoom level [minZoom]..[maxZoom] covers
 * (rounded to the nearest enclosing integers, since a tile grid only exists at integer zooms).
 *
 * Handles the antimeridian the same way [GeoDistance.boundingBox] already documents it can produce
 * a box crossing it (`east < west`): the tile-x range wraps around the grid rather than coming out
 * negative or empty.
 */
/**
 * The estimate for what a download of [region] will *actually* enumerate: [estimateOfflineTileCount]
 * over [OfflineMapRepository.MIN_ZOOM]..`min(`[OfflineMapRepository.MAX_ZOOM]`, `
 * [OfflineMapRepository.SERVED_MAX_ZOOM]`)`, because MapLibre clamps an offline pyramid to the
 * zoom the tile source advertises, not to the zoom the definition asks for — see
 * [OfflineMapRepository.SERVED_MAX_ZOOM] for the evidence and what keeps it honest. The one
 * function both the panel's "~N tiles" label and the ViewModel's pre-flight gate call, so the
 * number shown and the number gated cannot drift from each other again.
 */
fun estimateServedOfflineTileCount(region: Region): Int = estimateOfflineTileCount(
    region,
    OfflineMapRepository.MIN_ZOOM,
    minOf(OfflineMapRepository.MAX_ZOOM, OfflineMapRepository.SERVED_MAX_ZOOM),
)

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
    val xMin = SlippyMapTile.x(box.west, zoom)
    val xMax = SlippyMapTile.x(box.east, zoom)
    val yMin = SlippyMapTile.y(box.north, zoom)
    val yMax = SlippyMapTile.y(box.south, zoom)

    val tilesX = if (box.east >= box.west) xMax - xMin + 1 else tilesPerAxis - xMin + xMax + 1
    val tilesY = yMax - yMin + 1
    return tilesX * tilesY
}
