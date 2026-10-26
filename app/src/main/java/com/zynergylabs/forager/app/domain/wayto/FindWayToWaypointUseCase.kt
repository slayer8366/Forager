package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource

/** STUB (dispatch 2026-10-26-10, fail first): always the straight line, never routed, never logged. */
class FindWayToWaypointUseCase(
    private val offlineMapRepository: OfflineMapRepository,
    private val engines: TrailEngineSource,
    private val errorLog: ErrorLog,
) {
    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(from: LatLng, waypoint: LatLng): WayToWaypointResult = WayToWaypointResult.StraightLine(WayToWaypointNote.NO_ROUTE_FOUND)

    class WayToWaypointFallback(reason: String, cause: Throwable?) : Exception(reason, cause)
}
