package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry

/**
 * Detaches [photo] from [entry] — removes the *reference* only, never the photo's file or its
 * gallery row (owner decision, 2026-08-22: gallery ownership — see
 * [com.forager.app.domain.model.LogPhoto]'s own doc comment).
 *
 * **This is a second reversal, the same shape as [DeleteMushroomLogEntryUseCase]'s, one layer
 * beneath it — found unprompted while investigating that one, not asked about directly.** Before
 * `MIGRATION_7_8` this use case deleted the photo file *and* detached it from [entry] in one
 * un-splittable call — correct then, for the same reason [DeleteMushroomLogEntryUseCase]'s old
 * behavior was: a photo's only reason to exist was one entry's ownership of it. Under gallery
 * ownership, tapping the "×" on a photo inside one entry must not delete a photo that may still be
 * referenced by other entries, or that simply still belongs in the gallery on its own — deleting
 * the photo itself is the gallery's job (a warn-then-remove flow, owner decision), not an entry
 * detail screen's. [PhotoStore] is no longer a dependency here; nothing left calls it.
 */
class RemovePhotoFromLogEntryUseCase(
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(entry: MushroomLogEntry, photo: LogPhoto): Result<MushroomLogEntry> {
        repository.detachPhotoFromEntry(entry.id, photo.id).getOrElse { return Result.failure(it) }
        return Result.success(entry.copy(photos = entry.photos.filterNot { it.id == photo.id }))
    }
}
