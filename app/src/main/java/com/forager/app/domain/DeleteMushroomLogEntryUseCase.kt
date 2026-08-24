package com.forager.app.domain

import com.forager.app.domain.model.LogPhoto

/**
 * Removes a log entry by [id], and — new as of this class — its photo files, matching the same
 * "the user asked for the entry gone" scope [PhotoStore.delete] already documents. Previously this
 * deleted only the Room rows (`log_photos`/`mushroom_log_entries`, both gone via
 * `MushroomLogRepository.delete`); [PhotoStore.delete] was never called from any delete path, so a
 * deleted entry's photo files were orphaned on disk permanently — a live bug, not new scope.
 *
 * **Rows first, then files.** [repository].delete(id) runs first; [photoStore] cleanup for [photos]
 * only runs if that succeeds. A crash between the two leaves orphaned files — the same outcome
 * today's bug already produces for every deletion, not a new failure mode this introduces. The
 * reverse order (files first) would leave `mushroom_log_entries`/`log_photos` rows pointing at
 * files that no longer exist, which the UI could render as broken images — worse than today, since
 * today's bug at least leaves a consistent (if leaky) state.
 *
 * **A file-deletion failure never fails this use case.** The user asked for the entry gone; a
 * filesystem problem finishing the row deletion isn't theirs to absorb, and per
 * `docs/error-presentation-spec.md` it isn't belief-changing either — nothing the user believed
 * ("this entry is deleted") becomes false because a leftover file didn't get cleaned up. Each
 * failure is still reported, not silently dropped (CLAUDE.md) — via [onPhotoDeleteFailed], the same
 * shape [OfflineMapRepository.download]'s `onProgress` already uses to let a domain function report
 * an in-band event without doing the actual logging itself, which stays the caller's job:
 * [com.forager.app.ui.log.MushroomLogViewModel] logs directly with `android.util.Log.w`, the same
 * as its own other failure paths, rather than this class taking on a logging dependency no other
 * use case in this package has needed yet.
 */
class DeleteMushroomLogEntryUseCase(
    private val photoStore: PhotoStore,
    private val repository: MushroomLogRepository,
) {
    suspend operator fun invoke(
        id: String,
        photos: List<LogPhoto>,
        onPhotoDeleteFailed: (LogPhoto, Throwable) -> Unit = { _, _ -> },
    ): Result<Unit> {
        val result = repository.delete(id)
        if (result.isSuccess) {
            photos.forEach { photo ->
                photoStore.delete(photo).onFailure { error -> onPhotoDeleteFailed(photo, error) }
            }
        }
        return result
    }
}
