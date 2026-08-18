package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry

/** The inverse of [AddPhotoToLogEntryUseCase]: deletes [photo] via [photoStore], detaches it from [entry], and saves the result. */
class RemovePhotoFromLogEntryUseCase(
    private val photoStore: PhotoStore,
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: MushroomLogEntry, photo: LogPhoto): Result<MushroomLogEntry> {
        photoStore.delete(photo).getOrElse { return Result.failure(it) }
        val updated = entry.copy(photos = entry.photos.filterNot { it.id == photo.id })
        return repository.save(updated).map { updated }
    }
}
