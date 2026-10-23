package com.zynergylabs.forager.app.domain.routedata

import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import java.io.File

/**
 * "Routes for the area containing X" (dispatch 2026-10-23-03, item 5): which downloaded region's route folder the
 * trail router (`domain/routing/TrailRouter`, dispatch 2026-10-23-02) should be handed for [invoke]'s point. Pure
 * domain: it reads [OfflineMapRepository.listRegions] and decides; no Android.
 *
 * A region covers a point when the point is inside the rectangle its routes were cut to ([routeAreaFor]), not merely
 * inside its drawn circle: the corners of the rectangle hold route data too.
 *
 * **When several regions cover the point**, the newest download with stored routes wins: its files come from the
 * newest route-data build this phone has. The coder's choice, stated so it can be changed: nothing calls this yet,
 * and a caller routing between two points may want the region covering both, which is a different question.
 */
class FindRouteDataForPointUseCase(private val offlineMapRepository: OfflineMapRepository) {

    suspend operator fun invoke(point: LatLng): Result<RouteDataForPoint> =
        offlineMapRepository.listRegions().map { regions -> routeDataForPoint(point, regions) }
}

/** The decision [FindRouteDataForPointUseCase] makes, on a list already read. */
fun routeDataForPoint(point: LatLng, regions: List<OfflineRegionSummary>): RouteDataForPoint {
    val covering = regions
        .filter { it.isDownloaded && routeAreaFor(it.region).contains(point) }
        .sortedByDescending { it.createdAtEpochMillis }
    if (covering.isEmpty()) return RouteDataForPoint.NoRegionCovers
    val square = routeSquareName(point)
    val storedHere = covering.mapNotNull { region -> (region.routeData as? RegionRouteData.Stored)?.let { region to it } }
    val usable = storedHere.firstOrNull { (_, routes) -> square !in routes.missingSquares }
    return when {
        usable != null -> RouteDataForPoint.Found(regionId = usable.first.id, folder = usable.second.folder, runId = usable.second.runId)
        // Routes are stored for the area, and the point's own square is one the Worker's build lacked.
        storedHere.isNotEmpty() -> RouteDataForPoint.SquareMissing(regionId = storedHere.first().first.id, square = square)
        // The newest covering region's state, so the caller can say why (none here, missing, never fetched).
        else -> RouteDataForPoint.NotStored(regionId = covering.first().id, routeData = covering.first().routeData)
    }
}

/** What [FindRouteDataForPointUseCase] found for a point. */
sealed interface RouteDataForPoint {
    /** [folder] holds the region's stored route files; hand it to the trail router. */
    data class Found(val regionId: Long, val folder: File, val runId: String) : RouteDataForPoint

    /** Downloaded regions cover the point and none has stored routes; [routeData] is the newest one's state. */
    data class NotStored(val regionId: Long, val routeData: RegionRouteData) : RouteDataForPoint

    /** A region with stored routes covers the point, and the point's [square] was missing from the build it came from. */
    data class SquareMissing(val regionId: Long, val square: String) : RouteDataForPoint

    /** No downloaded region covers the point. */
    data object NoRegionCovers : RouteDataForPoint
}
