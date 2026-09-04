package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region

/**
 * Whether [point] falls within [region]'s own tile footprint at [zoom] — the missing half of
 * "offline maps are referenced by which tiles the data actually sits on" (Journal Stage 2a
 * dispatch): [EstimateOfflineTileCount] already answers "how many tiles does this region cover,"
 * this answers "is this one coordinate inside that coverage," built on the same [SlippyMapTile]
 * conversion and the same [GeoDistance.boundingBox] center+radius-to-box math
 * [estimateOfflineTileCount] already uses — [region]'s footprint is exactly the box that function's
 * own `tileCountAtZoom` sizes.
 *
 * **[zoom] is a caller-supplied parameter, not something this function assumes.** A coarser zoom's
 * tiles cover far more ground than a finer one's — testing membership at [region]'s own
 * [com.forager.app.domain.OfflineMapRepository.MIN_ZOOM] would call a point kilometers outside the
 * region "within its tiles," while [com.forager.app.domain.OfflineMapRepository.MAX_ZOOM] gives the
 * tightest, most literal answer to "does this exact point's finest-detail tile exist in what was
 * downloaded." This dispatch is data-layer only — which zoom a real membership check should use for
 * "track tiles and find tiles should coincide" is a 2b UI/design decision this function deliberately
 * leaves to its caller rather than presuming.
 *
 * Antimeridian-aware the same way [estimateOfflineTileCount]'s own `tileCountAtZoom` is: a region's
 * box crossing longitude ±180° (`box.east < box.west`) wraps the tile-column test around the grid
 * rather than treating it as an empty or negative range.
 */
fun isCoordinateWithinRegionTiles(point: LatLng, region: Region, zoom: Int): Boolean {
    val box = GeoDistance.boundingBox(LatLng(region.lat, region.lng), region.radiusKm)

    val pointTileX = SlippyMapTile.x(point.lng, zoom)
    val pointTileY = SlippyMapTile.y(point.lat, zoom)

    val xMin = SlippyMapTile.x(box.west, zoom)
    val xMax = SlippyMapTile.x(box.east, zoom)
    val yMin = SlippyMapTile.y(box.north, zoom)
    val yMax = SlippyMapTile.y(box.south, zoom)

    val xInRange = if (box.east >= box.west) pointTileX in xMin..xMax else pointTileX >= xMin || pointTileX <= xMax
    val yInRange = pointTileY in yMin..yMax

    return xInRange && yInRange
}
