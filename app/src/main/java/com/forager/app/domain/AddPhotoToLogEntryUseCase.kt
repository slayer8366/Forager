package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource

/**
 * Persists a new photo via [photoStore] and appends it to [entry], saving the updated entry.
 * Kept as domain logic — not inline in a ViewModel — so the "persist then attach then save, and
 * don't attach on a failed persist" ordering is unit-testable headless, per CLAUDE.md.
 */
class AddPhotoToLogEntryUseCase(
    private val photoStore: PhotoStore,
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: MushroomLogEntry, source: PhotoSource): Result<MushroomLogEntry> {
        val photo = photoStore.persist(source).getOrElse { return Result.failure(it) }
        val updated = entry.copy(photos = entry.photos + photo)
        return repository.save(updated).map { updated }
    }
}
