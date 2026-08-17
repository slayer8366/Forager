package com.forager.app.domain

/** Removes a planned trip by id. A one-line wrapper, kept as its own class per the one-class-one-job pattern used throughout `domain/` — see [SavePlannedTripUseCase], [GetPlannedTripsUseCase]. */
class DeletePlannedTripUseCase(
    private val repository: PlannedTripRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}
