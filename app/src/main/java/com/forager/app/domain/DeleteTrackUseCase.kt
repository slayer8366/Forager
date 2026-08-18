package com.forager.app.domain

/** Removes a track and its points by id. A one-line wrapper — see [DeletePlannedTripUseCase]. */
class DeleteTrackUseCase(
    private val repository: TrackRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}
