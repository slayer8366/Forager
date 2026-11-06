package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.NavigationTargetRef
import com.zynergylabs.forager.app.domain.asNavigationTarget
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint

/**
 * How the straight line ([AvailabilityWaypointLine]) and the way along trails ([AvailabilityWaypointRoute]) read the target
 * they follow (dispatch 2026-11-05-05, Amendment 1, A8): a waypoint by id from its store, as before (RECORD -1657, "look up
 * by id"); a planned trip from the trips as stored, through [readTrips] (`PlannedTripRepository` has no read by id), as the
 * point [asNavigationTarget] makes of it. A trip that is not stored reads as `null`, as a waypoint that is not stored does:
 * the screen resolves it as gone and ends the navigation with its message. Read from the store, not from the trips the
 * screen has loaded, so a kept trip navigation picked back up at start finds its trip whichever read lands first.
 *
 * The copy read here does not go stale: nothing moves a saved trip (`SavePlannedTripUseCase` and `SavePlottedRouteUseCase`
 * each write a new id, and there is no edit), as nothing moves a placed waypoint.
 */
internal fun navigationTargetReader(
    readWaypoint: suspend (String) -> Result<Waypoint?>,
    readTrips: suspend () -> Result<List<PlannedTrip>>,
): suspend (NavigationTargetRef) -> Result<Waypoint?> = { target ->
    when (target.kind) {
        NavigationTargetKind.WAYPOINT -> readWaypoint(target.id)
        NavigationTargetKind.TRIP -> readTrips().map { trips -> trips.firstOrNull { it.id == target.id }?.asNavigationTarget() }
    }
}
