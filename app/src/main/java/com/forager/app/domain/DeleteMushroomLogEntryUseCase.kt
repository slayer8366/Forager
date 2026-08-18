package com.forager.app.domain

/** Removes a log entry by id. A one-line wrapper, kept as its own class per the one-class-one-job pattern used throughout `domain/` — see [DeletePlannedTripUseCase]. */
class DeleteMushroomLogEntryUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.delete(id)
}
