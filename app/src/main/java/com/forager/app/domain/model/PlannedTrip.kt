package com.forager.app.domain.model

import java.time.LocalDate

/**
 * A future foraging trip the user has planned: a place on the map and a day to go there.
 *
 * [id] is assigned by whatever creates the trip (see
 * [com.forager.app.domain.SavePlannedTripUseCase]), not by the persistence layer, so domain code
 * never has to round-trip through storage just to know a trip's identity.
 *
 * [name] is user-facing identity, not [id] — the id is opaque, [name] is what a person reads on
 * the trip's row and its map marker. It is never blank: [com.forager.app.domain.SavePlannedTripUseCase]
 * enforces that at creation, the same "domain owns the invariant" pattern as the date floor next
 * to it. A name field was originally left out of this type as a judgment call pending user input
 * (see git history); the user has since asked for it, defaulting to "Trip N" at creation with no
 * rename-after-creation flow, since none was asked for.
 */
data class PlannedTrip(
    val id: String,
    val name: String,
    val location: LatLng,
    val date: LocalDate,
)
