package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto

/**
 * Deletes [photo] from the gallery — Workstream G3, closing the gap G1 deliberately left open.
 * The warn-first confirmation (how many entries reference [photo]) is the caller's job, using
 * [com.forager.app.domain.model.GalleryPhoto.referencingEntryIds]; by the time this runs, the user
 * has already confirmed.
 *
 * **Ordering: rows first, file last.** [MushroomLogRepository.deletePhotoFromGallery] removes the
 * photo's own row and every entry's cross-reference to it in one transaction; only once that
 * succeeds does this delete the file via [PhotoStore]. A failed file deletion never leaves a row
 * pointing at nothing — the row is already gone by the time the file delete is even attempted, the
 * same "no dangling references" property the delete-first-then-file ordering can't offer. The
 * reverse order (file first) would risk exactly what this dispatch names as the alternative
 * considered and rejected: if the file delete succeeded but the row delete then failed, a row
 * would point at a file that no longer exists, which [com.forager.app.domain.model.GalleryPhoto]'s
 * own read path can't distinguish from ordinary corruption — [DecodedPhoto] would render it as a
 * plain box, quiet wrongness rather than a stated failure.
 *
 * A file-deletion failure is reported via [onFileDeleteFailed] rather than failing this whole
 * operation or being swallowed — the rows are already correctly gone (what the user asked for and
 * was warned about), and a leftover file on disk is a leak, not a correctness problem the caller
 * needs to react to beyond logging it. Mirrors [AddPhotoToLogEntryUseCase]/[DeleteMushroomLogEntryUseCase]'s
 * own "the use case reports, the ViewModel logs" split — no `Log.w` call lives in domain code here.
 */
class DeleteGalleryPhotoUseCase(
    private val repository: MushroomLogRepository,
    private val photoStore: PhotoStore,
) {
    suspend operator fun invoke(photo: LogPhoto, onFileDeleteFailed: (Throwable) -> Unit = {}): Result<Unit> {
        repository.deletePhotoFromGallery(photo.id).getOrElse { return Result.failure(it) }
        photoStore.delete(photo).onFailure(onFileDeleteFailed)
        return Result.success(Unit)
    }
}
