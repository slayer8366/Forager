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
    val stored = covering.firstNotNullOfOrNull { region -> (region.routeData as? RegionRouteData.Stored)?.let { region to it } }
    return if (stored != null) {
        RouteDataForPoint.Found(regionId = stored.first.id, folder = stored.second.folder, runId = stored.second.runId)
    } else {
        // The newest covering region's state, so the caller can say why (none here, missing, never fetched).
        RouteDataForPoint.NotStored(regionId = covering.first().id, routeData = covering.first().routeData)
    }
}

/** What [FindRouteDataForPointUseCase] found for a point. */
sealed interface RouteDataForPoint {
    /** [folder] holds the region's stored route files; hand it to the trail router. */
    data class Found(val regionId: Long, val folder: File, val runId: String) : RouteDataForPoint

    /** Downloaded regions cover the point and none has stored routes; [routeData] is the newest one's state. */
    data class NotStored(val regionId: Long, val routeData: RegionRouteData) : RouteDataForPoint

    /** No downloaded region covers the point. */
    data object NoRegionCovers : RouteDataForPoint
}
