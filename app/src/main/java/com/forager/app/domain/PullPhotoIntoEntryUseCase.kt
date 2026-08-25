package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry

/**
 * References an existing gallery [photo] from [entry] — a reference only, via
 * [MushroomLogRepository.attachPhotoToEntry]. Workstream G3: unlike [AddPhotoToLogEntryUseCase],
 * this never touches [PhotoStore] at all — the photo already exists in the gallery, so there is no
 * new file and no new `log_photos` row, only a `log_entry_photos` row.
 *
 * **Idempotent**: pulling a photo already referenced by [entry] is a no-op that still succeeds,
 * not a duplicate reference or a duplicate thumbnail. `attachPhotoToEntry`'s own
 * `LogEntryPhotoCrossRef` composite primary key already makes a repeat insert a no-op at the
 * database level (`OnConflictStrategy.REPLACE` over an identical row changes nothing); this use
 * case only needs to avoid appending a second copy of [photo] to [entry.photos] itself.
 */
class PullPhotoIntoEntryUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: MushroomLogEntry, photo: LogPhoto): Result<MushroomLogEntry> {
        repository.attachPhotoToEntry(entry.id, photo.id).getOrElse { return Result.failure(it) }
        val photos = if (entry.photos.any { it.id == photo.id }) entry.photos else entry.photos + photo
        return Result.success(entry.copy(photos = photos))
    }
}
