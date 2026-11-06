package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.PendingDeleteSlot
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.ui.log.CartographyViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The pending entry delete of [CartographyViewModel] (UI split Phase 2.8, PR 1, dispatch 2026-10-19-34,
 * RECORD -1460): the one entry whose delete waits on the Undo snackbar, moved out of the ViewModel
 * unchanged, in the shape of Phase 2.7's `TrackRecordingPendingDeletes`. It holds the one
 * [PendingDeleteSlot] and nothing else. Every value it reads from the screen's state comes through
 * [uiState], every write goes through [updateState], and its coroutines launch in [scope], the
 * ViewModel's own `viewModelScope`, read each time rather than kept. It holds no job and cancels none.
 */
internal class CartographyPendingDeletes(
    private val uiState: () -> CartographyUiState,
    private val updateState: ((CartographyUiState) -> CartographyUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val pendingDeleteCommitScope: CoroutineScope,
    private val deleteEntry: DeleteCartographyEntryUseCase,
) {

    /** The one entry whose delete is pending (journal redesign J4b L2) — see [requestDeleteEntry]. */
    private val entryDeletes = PendingDeleteSlot<String, CartographyEntry> { it.id }

    /**
     * An entry card's Delete (journal redesign J4b L2, the owner's "Lists swipe": a full swipe, the
     * revealed Delete button, or the card's "Delete" accessibility action): the entry becomes pending
     * instead of being deleted, held in J4's [PendingDeleteSlot] exactly as J4's three owners hold
     * theirs. It is left out of [CartographyUiState.hidingPendingDelete]'s lists while the Undo
     * snackbar shows; the real delete ([deleteEntry], unchanged) runs from [commitDeleteEntry] when
     * the snackbar ends without Undo, from here when a second entry is deleted while this one is
     * pending (the first is committed then), or from [CartographyViewModel.onCleared].
     *
     * The report's and edit screen's own Delete, with their confirm dialogs, still call
     * [CartographyViewModel.onDeleteEntry] at once (the J4b dispatch: they "stay as they are"). If the entry being pended is
     * the open one, it is closed, as [CartographyViewModel.onDeleteEntry] closes it. An id found in neither list nor open
     * is logged and pends nothing.
     */
    fun requestDeleteEntry(id: String) {
        val state = uiState()
        val entry = state.entries.firstOrNull { it.id == id }
            ?: state.draftEntries.firstOrNull { it.id == id }
            ?: state.editingEntry?.takeIf { it.id == id }
        if (entry == null) {
            Log.w(TAG, "A delete was asked for entry '$id', which is not loaded; nothing pended.")
            return
        }
        val displaced = entryDeletes.pend(entry, entryReferenceCount = null)
        updateState { current ->
            if (current.editingEntry?.id == id) {
                current.copy(
                    pendingDelete = entryDeletes.pending,
                    editingEntry = null,
                    candidatesForEditingEntry = null,
                    candidateOfflineRegionsForEditingEntry = emptyList(),
                    hasUnsavedChanges = false,
                )
            } else {
                current.copy(pendingDelete = entryDeletes.pending)
            }
        }
        displaced?.let(::commitEntryDelete)
    }

    /** The snackbar's Undo: the pending entry shows again, in the list it was in. Nothing was deleted, so nothing is restored. */
    fun undoDeleteEntry(id: String) {
        if (entryDeletes.undo(id) == null) {
            Log.w(TAG, "Undo for entry '$id' came after its delete was committed; nothing to undo.")
        }
        updateState { it.copy(pendingDelete = entryDeletes.pending) }
    }

    /** The snackbar ended without Undo (timed out, or a newer snackbar replaced it): the pending entry's delete runs, once. */
    fun commitDeleteEntry(id: String) {
        val entry = entryDeletes.commit(id)
        updateState { it.copy(pendingDelete = entryDeletes.pending) }
        entry?.let(::commitEntryDelete)
    }

    /**
     * The real delete of an entry whose pending time is over. It leaves the lists at once, so it does
     * not flash back between the snackbar closing and the delete finishing. A failed delete puts it
     * back where it was and reports the failure the way [CartographyViewModel.onDeleteEntry] does.
     */
    private fun commitEntryDelete(entry: CartographyEntry) {
        val before = uiState()
        val entriesIndex = before.entries.indexOfFirst { it.id == entry.id }
        val draftsIndex = before.draftEntries.indexOfFirst { it.id == entry.id }
        updateState { state ->
            state.copy(
                entries = state.entries.filterNot { it.id == entry.id },
                draftEntries = state.draftEntries.filterNot { it.id == entry.id },
            )
        }
        scope().launch {
            deleteEntry(entry.id).fold(
                onSuccess = { updateState { it.copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't delete entry '${entry.id}'.", error)
                    updateState { state ->
                        state.copy(
                            entries = state.entries.reinsert(entry, entriesIndex),
                            draftEntries = state.draftEntries.reinsert(entry, draftsIndex),
                            saveErrorMessage = "Couldn't delete that entry.",
                        )
                    }
                },
            )
        }
    }

    /** [entry] back at [index] when it was in this list ([index] >= 0) and is not there now. */
    private fun List<CartographyEntry>.reinsert(entry: CartographyEntry, index: Int): List<CartographyEntry> =
        if (index < 0 || any { it.id == entry.id }) this else toMutableList().apply { add(index.coerceAtMost(size), entry) }

    /**
     * The ViewModel's `onCleared`: an entry still pending is committed here, since no snackbar is left to
     * end. viewModelScope is already cancelled, so the delete runs on [pendingDeleteCommitScope].
     */
    fun commitOnCleared() {
        entryDeletes.takeAny()?.let { entry ->
            pendingDeleteCommitScope.launch {
                deleteEntry(entry.id).onFailure { error ->
                    Log.w(TAG, "Couldn't delete entry '${entry.id}' pending when the screen closed; it is still saved.", error)
                }
            }
        }
    }
}
