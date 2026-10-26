package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import kotlinx.coroutines.flow.Flow

/** What the Maps tab shows of the way to a waypoint along trails: what the HUD is given, and the line the map draws. */
data class WaypointRouteShown(val hud: ReturnRoute?, val line: RouteLine?)

/** STUB for the fail-first commit (dispatch 2026-10-26-11): works nothing out. */
internal class AvailabilityWaypointRoute(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val readWaypoint: suspend (String) -> Result<Waypoint?>,
    private val findWayToWaypoint: (suspend (LatLng, LatLng) -> WayToWaypointResult)?,
    private val errorLog: ErrorLog,
) {
    suspend fun follow(waypointId: String?, liveFixes: Flow<LocationFix.Update?>) {
        updateState { it.copy(waypointRoute = null) }
    }
}
