package com.zynergylabs.forager.app.domain.routedata

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import java.io.File

/**
 * Which downloaded region's routes a route between two points runs on (dispatch 2026-10-26-10, step 6 PR 2): the
 * question [routeDataForPoint]'s own doc leaves open, "a caller routing between two points may want the region covering
 * both". Pure domain, on a list already read.
 *
 * **The region** is the newest download with stored routes whose route rectangle ([routeAreaFor]) holds both points and
 * whose build has both points' squares: the newest because its files come from the newest route-data build this phone
 * has, as for one point. A region newer than it that holds only one end is passed over.
 *
 * **When none holds both**, the answer says why, in this order:
 * 1. each end has routes on its own, in different regions: [RouteDataForPoints.InDifferentAreas];
 * 2. an end is covered and its routes are not usable (not stored, or its square missing), the walker's end ([from])
 *    first: [RouteDataForPoints.NotUsable], with that end's own state, so the caller can say what would fix it;
 * 3. one end has routes and the other is in no downloaded region: [RouteDataForPoints.InDifferentAreas];
 * 4. neither end is in a downloaded region: [RouteDataForPoints.NoRegionCovers].
 *
 * That order is the coder's choice, stated so it can be changed: a fixable state at an end (routes not downloaded) is
 * said before "another area", since it is the one the walker can act on.
 */
fun routeDataForPoints(from: LatLng, to: LatLng, regions: List<OfflineRegionSummary>): RouteDataForPoints {
    val squares = listOf(routeSquareName(from), routeSquareName(to))
    val both = regions
        .filter { it.isDownloaded }
        .mapNotNull { region -> (region.routeData as? RegionRouteData.Stored)?.let { region to it } }
        .filter { (region, routes) ->
            val area = routeAreaFor(region.region)
            area.contains(from) && area.contains(to) && squares.none { it in routes.missingSquares }
        }
        .maxByOrNull { (region, _) -> region.createdAtEpochMillis }
    if (both != null) {
        val (region, routes) = both
        return RouteDataForPoints.Found(regionId = region.id, folder = routes.folder, area = routeAreaFor(region.region))
    }
    val atFrom = routeDataForPoint(from, regions)
    val atTo = routeDataForPoint(to, regions)
    return when {
        atFrom is RouteDataForPoint.Found && atTo is RouteDataForPoint.Found -> RouteDataForPoints.InDifferentAreas
        atFrom.isUnusable() -> RouteDataForPoints.NotUsable(RouteEnd.START, atFrom)
        atTo.isUnusable() -> RouteDataForPoints.NotUsable(RouteEnd.END, atTo)
        atFrom is RouteDataForPoint.Found || atTo is RouteDataForPoint.Found -> RouteDataForPoints.InDifferentAreas
        else -> RouteDataForPoints.NoRegionCovers
    }
}

/** Covered by a downloaded region, and its routes not usable there: not stored, or the point's square missing. */
private fun RouteDataForPoint.isUnusable(): Boolean = this is RouteDataForPoint.NotStored || this is RouteDataForPoint.SquareMissing

/** What [routeDataForPoints] found for two points. */
sealed interface RouteDataForPoints {
    /** [folder] holds region [regionId]'s stored routes, which hold both points; [area] is the rectangle they were cut to. */
    data class Found(val regionId: Long, val folder: File, val area: GeoBoundingBox) : RouteDataForPoints

    /** [end] is covered and its routes are not usable there; [state] is [routeDataForPoint]'s answer for it. */
    data class NotUsable(val end: RouteEnd, val state: RouteDataForPoint) : RouteDataForPoints

    /** No one region with routes holds both, and at least one end has routes: the two are in different areas. */
    data object InDifferentAreas : RouteDataForPoints

    /** Neither point is inside a downloaded region. */
    data object NoRegionCovers : RouteDataForPoints
}
