package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.DeleteMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.PendingDeleteSlot
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The pending deletes of [MushroomLogViewModel] (UI split Phase 2.8, PR 5, dispatch 2026-10-19-34, RECORD
 * -1460): the one find and the one album photo whose delete waits on the Undo snackbar, moved out of the
 * ViewModel unchanged, in the shape of Phase 2.7's `TrackRecordingPendingDeletes`. It holds the two
 * [PendingDeleteSlot]s and nothing else. Every value it reads from the screen's state comes through
 * [uiState], every write goes through [updateState], and its coroutines launch in [scope], the
 * ViewModel's own `viewModelScope`, read each time rather than kept. It holds no job and cancels none.
 *
 * **[editingEntryMutex] is the ViewModel's own lock, passed by reference, never a copy** (see
 * [MushroomLogViewModel]'s "Serialized editing-entry mutations"). Two critical sections live here,
 * unchanged: [requestDeleteEntry]'s, whose displaced find is committed only after the lock is released,
 * and [commitFindDelete]'s, inside its own launch. No method here is called by a path that holds the
 * lock, and nothing called while holding it here acquires it: [PendingDeleteSlot.pend], [updateState],
 * [deleteEntry] and [onFindDeleted]. [reloadEntries], which takes the lock in its own launch, is called
 * only from [commitGalleryPhotoDelete], which never holds it.
 */
internal class MushroomLogPendingDeletes(
    private val uiState: () -> MushroomLogUiState,
    private val updateState: ((MushroomLogUiState) -> MushroomLogUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val editingEntryMutex: Mutex,
    private val pendingDeleteCommitScope: CoroutineScope,
    private val deleteEntry: DeleteMushroomLogEntryUseCase,
    private val deleteGalleryPhoto: DeleteGalleryPhotoUseCase,
    private val onFindDeleted: (String) -> Unit,
    private val onPhotoDeleted: (String) -> Unit,
    /** The ViewModel's own `loadGalleryPhotos`. */
    private val reloadGalleryPhotos: () -> Unit,
    /** The ViewModel's own `loadEntries`, which takes the lock inside its own launch. */
    private val reloadEntries: () -> Unit,
) {

    /** The one find whose delete is pending (journal redesign J4) — see [requestDeleteEntry]. */
    private val findDeletes = PendingDeleteSlot<String, MushroomLogEntry> { it.id }

    /** The one gallery photo whose delete is pending (journal redesign J4b L3) — see [requestDeleteGalleryPhoto]. */
    private val photoDeletes = PendingDeleteSlot<String, GalleryPhoto> { it.photo.id }

    /**
     * The find report's (or edit form's) Delete (journal redesign J4, owner ruling "Keep inside the
     * report (Recommended)"): the find becomes pending instead of being deleted. Its report or form
     * closes, as it did when the delete ran at once, and it is left out of
     * [MushroomLogUiState.hidingPendingDelete]'s lists — the Finds gallery, the Finds chip's count,
     * the All logbook — while the Undo snackbar shows. The real delete ([deleteEntry], unchanged) runs
     * from [commitDeleteEntry] when the snackbar ends without Undo, from here when a second find is
     * deleted while this one is pending (the first is committed then), or from [MushroomLogViewModel.onCleared].
     *
     * Inside [editingEntryMutex], like every other write to [MushroomLogUiState.editingEntry]: a photo
     * attach still in flight for this find lands before the form closes rather than reopening it
     * afterwards. The displaced find's commit is launched after the lock is released — the lock is
     * not reentrant.
     *
     * A draft is found in [MushroomLogUiState.draftEntries] or, when it was never left, as the open
     * [MushroomLogUiState.editingEntry] itself. An id found nowhere is logged and pends nothing.
     */
    fun requestDeleteEntry(id: String) {
        scope().launch {
            val displaced = editingEntryMutex.withLock {
                val state = uiState()
                val entry = state.entries.firstOrNull { it.id == id }
                    ?: state.draftEntries.firstOrNull { it.id == id }
                    ?: state.editingEntry?.takeIf { it.id == id }
                if (entry == null) {
                    Log.w(TAG, "A delete was asked for find '$id', which is not loaded; nothing pended.")
                    return@withLock null
                }
                val displaced = findDeletes.pend(entry, entryReferenceCount = null)
                updateState {
                    it.copy(
                        pendingDelete = findDeletes.pending,
                        editingEntry = it.editingEntry?.takeUnless { open -> open.id == id },
                        // The displaced find leaves its Undo window in this same update: it is never in neither.
                        committedFindDeleteIds = if (displaced == null) it.committedFindDeleteIds else it.committedFindDeleteIds + displaced.id,
                    )
                }
                displaced
            }
            displaced?.let(::commitFindDelete)
        }
    }

    /**
     * The snackbar's Undo: the pending find shows again, in the list it was in; its report stays
     * closed. Nothing was deleted, so nothing is restored. A draft that was open and never left sat in
     * no list; it goes into [MushroomLogUiState.draftEntries] now, which is where leaving it would have
     * put it.
     */
    fun undoDeleteEntry(id: String) {
        val entry = findDeletes.undo(id)
        if (entry == null) {
            Log.w(TAG, "Undo for find '$id' came after its delete was committed; nothing to undo.")
        }
        updateState { state ->
            val listed = state.entries.any { it.id == id } || state.draftEntries.any { it.id == id }
            state.copy(
                pendingDelete = findDeletes.pending,
                draftEntries = if (entry != null && entry.isDraft && !listed) state.draftEntries + entry else state.draftEntries,
            )
        }
    }

    /** The snackbar ended without Undo (timed out, or a newer snackbar replaced it): the pending find's delete runs, once. */
    fun commitDeleteEntry(id: String) {
        val entry = findDeletes.commit(id)
        // The find is marked committed (commitFindDelete's first update) before the pending marker is cleared,
        // so the Maps tab never sees a moment in which it is neither (dispatch 2026-09-28-312, item 9).
        entry?.let(::commitFindDelete)
        updateState { it.copy(pendingDelete = findDeletes.pending) }
    }

    /**
     * The real delete of a find whose pending time is over. It leaves [MushroomLogUiState.entries]/
     * [MushroomLogUiState.draftEntries] at once, so it does not flash back between the snackbar
     * closing and the delete finishing; the delete itself runs inside [editingEntryMutex] as
     * [MushroomLogViewModel.onDeleteEntry]'s does. A failed delete puts it back where it was and reports the failure the
     * way [MushroomLogViewModel.onDeleteEntry] does.
     */
    private fun commitFindDelete(entry: MushroomLogEntry) {
        val before = uiState()
        val entriesIndex = before.entries.indexOfFirst { it.id == entry.id }
        val draftsIndex = before.draftEntries.indexOfFirst { it.id == entry.id }
        updateState { state ->
            state.copy(
                entries = state.entries.filterNot { it.id == entry.id },
                draftEntries = state.draftEntries.filterNot { it.id == entry.id },
                committedFindDeleteIds = state.committedFindDeleteIds + entry.id,
            )
        }
        scope().launch {
            editingEntryMutex.withLock {
                deleteEntry(entry.id).fold(
                    onSuccess = {
                        updateState { it.copy(saveErrorMessage = null) }
                        onFindDeleted(entry.id)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't delete entry '${entry.id}'.", error)
                        updateState { state ->
                            state.copy(
                                entries = state.entries.reinsert(entry, entriesIndex),
                                draftEntries = state.draftEntries.reinsert(entry, draftsIndex),
                                committedFindDeleteIds = state.committedFindDeleteIds - entry.id,
                                saveErrorMessage = "Couldn't delete that entry.",
                            )
                        }
                    },
                )
            }
        }
    }

    /** [entry] back at [index] when it was in this list ([index] >= 0) and is not there now. */
    private fun List<MushroomLogEntry>.reinsert(entry: MushroomLogEntry, index: Int): List<MushroomLogEntry> =
        if (index < 0 || any { it.id == entry.id }) this else toMutableList().apply { add(index.coerceAtMost(size), entry) }

    /**
     * The ViewModel's `onCleared`: a find or a photo still pending is committed here, since no snackbar is
     * left to end. viewModelScope is already cancelled, so the deletes run on [pendingDeleteCommitScope]:
     * the find first, then the photo, as before.
     */
    fun commitOnCleared() {
        findDeletes.takeAny()?.let { entry ->
            pendingDeleteCommitScope.launch {
                deleteEntry(entry.id).onFailure { error ->
                    Log.w(TAG, "Couldn't delete find '${entry.id}' pending when the screen closed; it is still saved.", error)
                }
            }
        }
        // J4b L3: the same for a pending gallery photo — its rows, then its file.
        photoDeletes.takeAny()?.let { photo ->
            pendingDeleteCommitScope.launch {
                deleteGalleryPhoto(photo.photo) { error ->
                    Log.w(TAG, "Couldn't delete the file for photo '${photo.photo.id}' pending when the screen closed.", error)
                }.onFailure { error ->
                    Log.w(TAG, "Couldn't delete photo '${photo.photo.id}' pending when the screen closed; it is still saved.", error)
                }
            }
        }
    }

    /**
     * An album photo's Delete (journal redesign J4b L3: the tile's long-press menu, or its "Delete"
     * accessibility action): the photo becomes pending instead of being deleted, held in J4's
     * [PendingDeleteSlot]. It is left out of [MushroomLogUiState.hidingPendingDelete]'s gallery and
     * finds' photo lists while the Undo snackbar shows.
     *
     * **The file delete waits too.** What is deferred is the whole [deleteGalleryPhoto] call, rows
     * then file ([DeleteGalleryPhotoUseCase], unchanged): nothing runs until [commitDeleteGalleryPhoto]
     * (the snackbar ended without Undo), a second photo delete displacing this one, or [MushroomLogViewModel.onCleared].
     * J0 B1 found a photo restorable only this way, since the JPEG is gone once its file is deleted.
     *
     * The reference count held with it is how many Cartography (journal) entries keep the photo,
     * read from [MushroomLogUiState.cartographyEntryPhotoReferenceCounts] now (missing counts as
     * zero, as the old confirm dialog read it). The drawer's gallery and the album's own trash
     * button still delete at once after their dialog, through [MushroomLogViewModel.onDeleteGalleryPhoto].
     */
    fun requestDeleteGalleryPhoto(photoId: String) {
        val state = uiState()
        val photo = state.galleryPhotos.firstOrNull { it.photo.id == photoId }
        if (photo == null) {
            Log.w(TAG, "A delete was asked for gallery photo '$photoId', which is not loaded; nothing pended.")
            return
        }
        val displaced = photoDeletes.pend(photo, entryReferenceCount = state.cartographyEntryPhotoReferenceCounts[photoId] ?: 0)
        updateState {
            it.copy(
                pendingPhotoDelete = photoDeletes.pending,
                committedPhotoDeleteIds = if (displaced == null) it.committedPhotoDeleteIds else it.committedPhotoDeleteIds + displaced.photo.id,
            )
        }
        displaced?.let(::commitGalleryPhotoDelete)
    }

    /** The snackbar's Undo: the pending photo shows again. Nothing was deleted, row or file, so nothing is restored. */
    fun undoDeleteGalleryPhoto(photoId: String) {
        if (photoDeletes.undo(photoId) == null) {
            Log.w(TAG, "Undo for gallery photo '$photoId' came after its delete was committed; nothing to undo.")
        }
        updateState { it.copy(pendingPhotoDelete = photoDeletes.pending) }
    }

    /** The snackbar ended without Undo: the pending photo's delete runs, once — its rows, then its file. */
    fun commitDeleteGalleryPhoto(photoId: String) {
        val photo = photoDeletes.commit(photoId)
        // Marked committed before the pending marker is cleared: see commitDeleteEntry.
        photo?.let(::commitGalleryPhotoDelete)
        updateState { it.copy(pendingPhotoDelete = photoDeletes.pending) }
    }

    /**
     * The real delete of a photo whose pending time is over: [MushroomLogViewModel.onDeleteGalleryPhoto]'s call, with the
     * photo taken out of [MushroomLogUiState.galleryPhotos] at once so it does not flash back before
     * the reload. A failed row delete puts it back where it was and reports the failure the way
     * [MushroomLogViewModel.onDeleteGalleryPhoto] does (the use case then never reaches the file).
     */
    private fun commitGalleryPhotoDelete(photo: GalleryPhoto) {
        val index = uiState().galleryPhotos.indexOfFirst { it.photo.id == photo.photo.id }
        updateState { state ->
            state.copy(
                galleryPhotos = state.galleryPhotos.filterNot { it.photo.id == photo.photo.id },
                committedPhotoDeleteIds = state.committedPhotoDeleteIds + photo.photo.id,
            )
        }
        scope().launch {
            deleteGalleryPhoto(photo.photo) { error ->
                Log.w(TAG, "Couldn't delete the file for photo '${photo.photo.id}'.", error)
            }.fold(
                onSuccess = {
                    updateState { it.copy(saveErrorMessage = null) }
                    onPhotoDeleted(photo.photo.id)
                    reloadGalleryPhotos()
                    // As onDeleteGalleryPhoto: loadEntries() takes editingEntryMutex itself.
                    reloadEntries()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't delete photo '${photo.photo.id}' from the gallery.", error)
                    updateState { state ->
                        val restored = if (index < 0 || state.galleryPhotos.any { it.photo.id == photo.photo.id }) {
                            state.galleryPhotos
                        } else {
                            state.galleryPhotos.toMutableList().apply { add(index.coerceAtMost(size), photo) }
                        }
                        state.copy(
                            galleryPhotos = restored,
                            committedPhotoDeleteIds = state.committedPhotoDeleteIds - photo.photo.id,
                            saveErrorMessage = "Couldn't delete that photo.",
                        )
                    }
                },
            )
        }
    }
}
