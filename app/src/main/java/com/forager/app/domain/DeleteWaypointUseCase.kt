package com.forager.app.domain

/** Removes a waypoint by id. A one-line wrapper — see [DeletePlannedTripUseCase]. */
class DeleteWaypointUseCase(
    private val repository: WaypointRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}
