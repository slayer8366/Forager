package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.CommitCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.DeleteCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.GetCartographyEntryUseCase
import com.zynergylabs.forager.app.domain.SaveCartographyEntryUseCase
import com.zynergylabs.forager.app.ui.log.CartographyViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Leaving or settling the open entry in [CartographyViewModel] (UI split Phase 2.8, PR 3, dispatch
 * 2026-10-19-34, RECORD -1460): close, finish, save, save as draft, discard, and the immediate delete,
 * moved out of the ViewModel unchanged. It holds no state of its own. Every value it reads from the
 * screen's state comes through [uiState], every write goes through [updateState], and its coroutines
 * launch in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept. A finished
 * entry's list refresh goes through [reloadEntries], the ViewModel's own `loadEntries`. It holds no job
 * and cancels none.
 */
internal class CartographyEntryExits(
    private val uiState: () -> CartographyUiState,
    private val updateState: ((CartographyUiState) -> CartographyUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val commitEntry: CommitCartographyEntryUseCase,
    private val saveEntry: SaveCartographyEntryUseCase,
    private val getEntry: GetCartographyEntryUseCase,
    private val deleteEntry: DeleteCartographyEntryUseCase,
    private val reloadEntries: () -> Unit,
) {

    /**
     * Closes the currently-open entry — draft-lifecycle dispatch fix. An open **draft** is merged
     * back into [CartographyUiState.draftEntries] locally before clearing, the same "surface the
     * abandoned draft immediately, don't wait for the next incidental [CartographyViewModel.loadEntries] call" shape
     * [MushroomLogViewModel.onLeaveEditingIncidentally] already establishes for finds. Before this
     * fix, closing did nothing but null out [CartographyUiState.editingEntry] — the draft this
     * ViewModel had *just* saved to disk stayed invisible in the Drafts list until some unrelated
     * action happened to call [CartographyViewModel.loadEntries] (e.g. [onFinishEntry]'s own success path), so the only
     * affordance the user could see was "+" again, minting an unrelated second draft rather than
     * resuming the first.
     *
     * **A committed entry needs the identical merge (device-check patch, Item 1 fix).** This used to
     * read "closing it changes nothing [CartographyUiState.entries] doesn't already reflect," on the
     * premise that [CartographyViewModel.onOpenEntry] only ever finds one already present there — true, but that premise
     * cut both ways: it meant a committed entry's edits, though durably saved to disk by [CartographyEntryEdits.persist],
     * never made it back into [CartographyUiState.entries] either, so the Entries list (and any
     * reopen within the same session) kept showing the pre-edit row. Merged here only when the entry
     * is not dirty by the time this runs — [onSaveEntry] and [onDiscardEntryChanges] each already
     * settle [CartographyUiState.entries] themselves before this is reached via the leave-prompt's
     * Save/Discard path, so this branch only ever re-merges already-clean data there; it's the
     * ordinary "closed without any unsaved change" path (nothing to prompt about) that actually needs
     * it.
     *
     * **Enforced, not assumed (intent 2026-09-28-44, F2).** The guarantee above was true only of the
     * paths it names: a committed entry that came back from a tab change in its report view reached
     * this dirty from the report's back arrow, and its unsaved edit was merged into [entries], so
     * the Entries card showed text the store never received, with nothing marked unsaved
     * (`docs/audits/2026-09-28-leaving-the-journal-investigation.md`, Behaviour 2). A dirty committed
     * entry is now never merged: the list keeps the stored row. On the leave prompt's Save the list
     * is then settled by [onSaveEntry]'s own upsert when the write lands, which is what it already
     * did when this ran first.
     */
    fun onCloseEntry() {
        val current = uiState().editingEntry
        val closingUnsavedEdit = current != null && !current.isDraft && uiState().hasUnsavedChanges
        if (closingUnsavedEdit) {
            Log.i(TAG, "Closing entry '${current.id}' with unsaved changes: the Entries list keeps the stored row.")
        }
        updateState { state ->
            state.copy(
                entries = when {
                    current == null || current.isDraft || closingUnsavedEdit -> state.entries
                    state.entries.any { it.id == current.id } -> state.entries.map { if (it.id == current.id) current else it }
                    else -> state.entries + current
                },
                draftEntries = when {
                    current == null || !current.isDraft -> state.draftEntries
                    state.draftEntries.any { it.id == current.id } -> state.draftEntries.map { if (it.id == current.id) current else it }
                    else -> state.draftEntries + current
                },
                editingEntry = null,
                candidatesForEditingEntry = null,
                candidateOfflineRegionsForEditingEntry = emptyList(),
                hasUnsavedChanges = false,
            )
        }
    }

    /** Finishes the currently-open draft — see [CommitCartographyEntryUseCase]'s own doc comment. A no-op if nothing is open or it's already committed. */
    fun onFinishEntry() {
        val entry = uiState().editingEntry?.takeIf { it.isDraft } ?: return
        scope().launch {
            commitEntry(entry).fold(
                onSuccess = { committed ->
                    updateState { it.copy(editingEntry = committed, saveErrorMessage = null, hasUnsavedChanges = false) }
                    reloadEntries()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't finish entry '${entry.id}'.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't finish that entry.") }
                },
            )
        }
    }

    /**
     * Explicit Save for a **committed** entry's edits (device-check patch, Item 1). Saves in place —
     * unlike [onCloseEntry], this never nulls out [CartographyUiState.editingEntry], the same
     * "Finish entry" stays on the just-committed entry's own edit screen precedent [onFinishEntry]
     * already sets. Also settles [CartographyUiState.entries] immediately (upsert by id) rather than
     * waiting for the next [CartographyViewModel.loadEntries] — the same reasoning [onCloseEntry]'s own doc comment gives
     * for why that list needs to track a committed entry's edits at all. A no-op if nothing is open or
     * it's a draft: drafts never accumulate [CartographyUiState.hasUnsavedChanges] in the first place,
     * since [CartographyEntryEdits.persist] autosaves them unconditionally, unchanged from before this dispatch.
     */
    fun onSaveEntry() {
        val entry = uiState().editingEntry?.takeUnless { it.isDraft } ?: return
        scope().launch {
            saveEntry(entry).fold(
                onSuccess = { saved ->
                    updateState { state ->
                        state.copy(
                            entries = if (state.entries.any { it.id == saved.id }) {
                                state.entries.map { if (it.id == saved.id) saved else it }
                            } else {
                                state.entries + saved
                            },
                            // Only if nothing was typed while the write was in flight: replacing
                            // a newer editingEntry with the stamped copy of the older one would
                            // silently drop that keystroke (SaveCartographyEntryUseCase's own doc
                            // comment on the same race for drafts).
                            editingEntry = if (state.editingEntry == entry) saved else state.editingEntry,
                            hasUnsavedChanges = false,
                            saveErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${entry.id}'.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                },
            )
        }
    }

    /**
     * Demotes the currently-open **committed** entry back to a draft, with the pending edit in place
     * — the backgrounding-return prompt's "Save as draft" option (pending-edit-and-fixes dispatch,
     * Item 1). Same row, same id: `entry.copy(isDraft = true)`, saved, moved from
     * [CartographyUiState.entries] to [CartographyUiState.draftEntries] locally so the Entries/Drafts
     * lists reflect it without waiting for [CartographyViewModel.loadEntries]. **Closes the entry** rather than staying
     * open in draft mode (owner correction) — demoting a committed entry is a consequential act, and
     * leaving the screen open with its character silently changed underneath the user (still showing
     * as if editing a committed entry one moment, a draft the next, with nothing said) would hide
     * that; closing and letting them see it land under Drafts makes it visible. A no-op if nothing is
     * open or it's already a draft.
     */
    fun onSaveEntryAsDraft() {
        val entry = uiState().editingEntry?.takeUnless { it.isDraft } ?: return
        val demoted = entry.copy(isDraft = true)
        scope().launch {
            saveEntry(demoted).fold(
                onSuccess = { saved ->
                    updateState { state ->
                        state.copy(
                            entries = state.entries.filterNot { it.id == saved.id },
                            draftEntries = if (state.draftEntries.any { it.id == saved.id }) {
                                state.draftEntries.map { if (it.id == saved.id) saved else it }
                            } else {
                                state.draftEntries + saved
                            },
                            editingEntry = null,
                            candidatesForEditingEntry = null,
                            candidateOfflineRegionsForEditingEntry = emptyList(),
                            hasUnsavedChanges = false,
                            saveErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save entry '${entry.id}' as a draft.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't save that as a draft.") }
                },
            )
        }
    }

    /**
     * Discards the currently-open **committed** entry's in-progress edits, by reloading it straight
     * from the database — never an in-memory revert (owner decision, device-check patch): a revert
     * built from whatever the ViewModel remembers as "the last-saved shape" can drift from what's
     * actually stored, and the one thing that can't drift is the store itself. Closes the entry too,
     * the same as [onCloseEntry] — this is the leave-prompt's Discard option, not a "keep editing but
     * reset the fields" action, so it settles [CartographyUiState.entries] from the *reloaded* row
     * directly rather than routing through [onCloseEntry] (which would otherwise merge the very edits
     * being discarded). A no-op if nothing is open, it's a draft, or the entry is no longer stored.
     */
    fun onDiscardEntryChanges() {
        val id = uiState().editingEntry?.takeUnless { it.isDraft }?.id ?: return
        scope().launch {
            getEntry(id).fold(
                onSuccess = { stored ->
                    updateState { state ->
                        state.copy(
                            entries = if (stored != null && state.entries.any { it.id == id }) {
                                state.entries.map { if (it.id == id) stored else it }
                            } else {
                                state.entries
                            },
                            editingEntry = null,
                            candidatesForEditingEntry = null,
                            candidateOfflineRegionsForEditingEntry = emptyList(),
                            hasUnsavedChanges = false,
                            // As every other success path does (the planner's ruling in -76).
                            saveErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't reload entry '$id' to discard changes.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't discard those changes.") }
                },
            )
        }
    }

    fun onDeleteEntry(id: String) {
        scope().launch {
            deleteEntry(id).fold(
                onSuccess = {
                    updateState { state ->
                        state.copy(
                            entries = state.entries.filterNot { it.id == id },
                            draftEntries = state.draftEntries.filterNot { it.id == id },
                            editingEntry = state.editingEntry?.takeUnless { it.id == id },
                            saveErrorMessage = null,
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't delete entry '$id'.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't delete that entry.") }
                },
            )
        }
    }
}
