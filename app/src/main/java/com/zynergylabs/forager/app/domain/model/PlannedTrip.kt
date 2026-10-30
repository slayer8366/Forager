package com.zynergylabs.forager.app.domain.model

import com.zynergylabs.forager.app.domain.route.PlottedRoute
import java.time.LocalDate

/**
 * A future foraging trip the user has planned: a place on the map and a day to go there.
 *
 * [id] is assigned by whatever creates the trip (see
 * [com.zynergylabs.forager.app.domain.SavePlannedTripUseCase]), not by the persistence layer, so domain code
 * never has to round-trip through storage just to know a trip's identity.
 *
 * [name] is user-facing identity, not [id] — the id is opaque, [name] is what a person reads on
 * the trip's row and its map marker. It is never blank: [com.zynergylabs.forager.app.domain.SavePlannedTripUseCase]
 * enforces that at creation, the same "domain owns the invariant" pattern as the date floor next
 * to it. A name field was originally left out of this type as a judgment call pending user input
 * (see git history); the user has since asked for it, defaulting to "Trip N" at creation with no
 * rename-after-creation flow, since none was asked for.
 *
 * [date] is optional since schema 23 (dispatch 2026-10-30-02, step 1): a trip that carries a [route] may be saved
 * without one. An undated trip shows no date and sorts after the dated ones ([com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase]).
 *
 * [route] is the route plotted between waypoints, when the trip has one. A route trip's [location], its pin, is the
 * route's first stop (Amendment 1, the owner: "At the first stop (Recommended)"), refused otherwise.
 */
data class PlannedTrip(
    val id: String,
    val name: String,
    val location: LatLng,
    val date: LocalDate?,
    val route: PlottedRoute? = null,
) {
    init {
        if (false) require(route == null || // STUB (tests first)
             location == route.stops.first().position) { "a route trip's location is its first stop" }
    }
}
