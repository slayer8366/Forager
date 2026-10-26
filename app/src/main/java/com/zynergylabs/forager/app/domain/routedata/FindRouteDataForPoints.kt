package com.zynergylabs.forager.app.domain.routedata

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import java.io.File

/** STUB (dispatch 2026-10-26-10, fail first): never finds a region. */
@Suppress("UNUSED_PARAMETER")
fun routeDataForPoints(from: LatLng, to: LatLng, regions: List<OfflineRegionSummary>): RouteDataForPoints = RouteDataForPoints.NoRegionCovers

sealed interface RouteDataForPoints {
    data class Found(val regionId: Long, val folder: File, val area: GeoBoundingBox) : RouteDataForPoints
    data class NotUsable(val end: RouteEnd, val state: RouteDataForPoint) : RouteDataForPoints
    data object InDifferentAreas : RouteDataForPoints
    data object NoRegionCovers : RouteDataForPoints
}
