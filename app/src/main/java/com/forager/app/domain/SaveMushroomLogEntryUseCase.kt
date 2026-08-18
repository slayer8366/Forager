package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry

/**
 * Persists [entry] as-is, replacing any stored entry with the same id. This is the deferred-
 * observation edit flow's save step — the forager reopens an entry created by
 * [CreateMushroomLogEntryUseCase], fills in what was left [com.forager.app.domain.model.Observed.NotObserved],
 * and this writes the result back.
 */
class SaveMushroomLogEntryUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: MushroomLogEntry): Result<Unit> = repository.save(entry)
}
