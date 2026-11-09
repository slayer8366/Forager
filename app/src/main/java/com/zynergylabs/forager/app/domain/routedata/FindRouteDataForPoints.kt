package com.zynergylabs.forager.app.domain.routedata

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import java.io.File

/**
 * Which downloaded region's routes a route between two points runs on (dispatch 2026-10-26-10, step 6 PR 2): the
 * question [routeDataForPoint]'s own doc leaves open, "a caller routing between two points may want the region covering
 * both". Pure domain, on a list already read.
 *
 * **Both ends first** (Amendment 1, item 2; Sorrel's review of #412, S-412-1; RECORD -1666, the owner: "Fix: check both
 * ends first"). Only a downloaded region whose route rectangle ([routeAreaFor]) holds both points can give a route, so
 * only such a region's state is said:
 * 1. the newest of them with stored routes whose build has both points' squares: [RouteDataForPoints.Found]. The newest
 *    because its files come from the newest route-data build this phone has, as for one point;
 * 2. else, when one or more hold both, why none can be routed on, as [routeDataForPoint] says it for one point: a square
 *    missing from stored routes, or the newest one's state (not fetched, fetching, damaged, …):
 *    [RouteDataForPoints.NotUsable];
 * 3. else, when either point is in a downloaded region: [RouteDataForPoints.InDifferentAreas], whatever the state of
 *    that region's routes. Fetching them could never give a route between the two, so "not downloaded" there would
 *    point the walker at a fix that does not fix it (S-412-1);
 * 4. else, neither point is in a downloaded region: [RouteDataForPoints.NoRegionCovers].
 */
fun routeDataForPoints(from: LatLng, to: LatLng, regions: List<OfflineRegionSummary>): RouteDataForPoints {
    val holdingBoth = regions
        .filter { it.isDownloaded && routeAreaFor(it.region).let { area -> area.contains(from) && area.contains(to) } }
        .sortedByDescending { it.createdAtEpochMillis }
    val squares = listOf(routeSquareName(from), routeSquareName(to))
    val storedHere = holdingBoth.mapNotNull { region -> (region.routeData as? RegionRouteData.Stored)?.let { region to it } }
    val usable = storedHere.firstOrNull { (_, routes) -> squares.none { it in routes.missingSquares } }
    if (usable != null) {
        val (region, routes) = usable
        return RouteDataForPoints.Found(regionId = region.id, folder = routes.folder, area = routeAreaFor(region.region))
    }
    if (holdingBoth.isEmpty()) return endsApart(from, to, regions)
    return RouteDataForPoints.NotUsable(
        if (storedHere.isNotEmpty()) {
            // Routes are stored for an area holding both, and a point's square is one the Worker's build lacked.
            val (region, routes) = storedHere.first()
            RouteDataForPoint.SquareMissing(regionId = region.id, square = squares.first { it in routes.missingSquares })
        } else {
            RouteDataForPoint.NotStored(regionId = holdingBoth.first().id, routeData = holdingBoth.first().routeData)
        },
    )
}

/** No downloaded region holds both points: they are in different areas when either is in one (step 3 above). */
private fun endsApart(from: LatLng, to: LatLng, regions: List<OfflineRegionSummary>): RouteDataForPoints {
    val eitherCovered = regions.any { it.isDownloaded && routeAreaFor(it.region).let { area -> area.contains(from) || area.contains(to) } }
    return if (!eitherCovered) RouteDataForPoints.NoRegionCovers else RouteDataForPoints.InDifferentAreas
}

/** What [routeDataForPoints] found for two points. */
sealed interface RouteDataForPoints {
    /** [folder] holds region [regionId]'s stored routes, which hold both points; [area] is the rectangle they were cut to. */
    data class Found(val regionId: Long, val folder: File, val area: GeoBoundingBox) : RouteDataForPoints

    /**
     * Downloaded regions hold both points and none has usable routes for them; [state] says why, as [routeDataForPoint]
     * says it for one point: [RouteDataForPoint.SquareMissing] or [RouteDataForPoint.NotStored].
     */
    data class NotUsable(val state: RouteDataForPoint) : RouteDataForPoints

    /** No downloaded region holds both points, and at least one is in a downloaded region, whatever its routes' state. */
    data object InDifferentAreas : RouteDataForPoints

    /** Neither point is inside a downloaded region. */
    data object NoRegionCovers : RouteDataForPoints
}
