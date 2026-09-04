package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource

/**
 * Persists a new photo via [photoStore] and adds it to the gallery — standalone-photos dispatch:
 * acquisition with no owning find. The two writes [AddPhotoToLogEntryUseCase] does before its own
 * third (attach), extracted so [PhotoGalleryScreen]'s own Camera/Gallery buttons can stop short of
 * attaching to anything. [AddPhotoToLogEntryUseCase] itself is untouched — same constructor, same
 * body, same three-step behavior from [LogEntryDetailScreen] — since the two use cases duplicate
 * only the two lines below rather than sharing a dependency edge that doesn't otherwise need to
 * exist between them.
 *
 * **No schema change enabled this.** `MIGRATION_7_8` already made a [LogPhoto] row independent of
 * any entry (see that entity's own doc comment); this is the write-path half that was missing, not
 * a new capability the schema had to grow.
 */
class AddPhotoToGalleryUseCase(
    private val photoStore: PhotoStore,
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(source: PhotoSource): Result<LogPhoto> {
        val photo = photoStore.persist(source).getOrElse { return Result.failure(it) }
        repository.addPhotoToGallery(photo).getOrElse { return Result.failure(it) }
        return Result.success(photo)
    }
}
