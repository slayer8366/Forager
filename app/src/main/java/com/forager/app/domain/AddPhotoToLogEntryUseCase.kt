package com.forager.app.domain

import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource

/**
 * Persists a new photo via [photoStore], adds it to the gallery, references it from [entry], and
 * returns [entry] with that reference reflected in [MushroomLogEntry.photos]. Two writes, not one —
 * [MushroomLogRepository.addPhotoToGallery] (the photo existing at all) then
 * [MushroomLogRepository.attachPhotoToEntry] (this entry referencing it) — mirroring the two things
 * that are now true of a freshly captured/imported photo under gallery ownership: it's a gallery
 * photo first, and this entry is (so far) the only thing pointing at it.
 *
 * Kept as domain logic — not inline in a ViewModel — so the "persist then add-to-gallery then
 * attach, and don't attach on a failed persist" ordering is unit-testable headless, per CLAUDE.md.
 */
class AddPhotoToLogEntryUseCase(
    private val photoStore: PhotoStore,
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: MushroomLogEntry, source: PhotoSource): Result<MushroomLogEntry> {
        val photo = photoStore.persist(source).getOrElse { return Result.failure(it) }
        repository.addPhotoToGallery(photo).getOrElse { return Result.failure(it) }
        repository.attachPhotoToEntry(entry.id, photo.id).getOrElse { return Result.failure(it) }
        return Result.success(entry.copy(photos = entry.photos + photo))
    }
}
