package com.forager.app.domain

import com.forager.app.domain.model.Waypoint

/** Every stored waypoint. A one-line wrapper — see [DeletePlannedTripUseCase]'s doc comment for why one exists at all. */
class GetWaypointsUseCase(
    private val repository: WaypointRepository,
) {
    suspend operator fun invoke(): Result<List<Waypoint>> = repository.getAll()
}
