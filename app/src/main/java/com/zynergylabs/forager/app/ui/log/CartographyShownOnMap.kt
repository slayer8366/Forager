package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.SetCartographyEntryShownOnMapUseCase
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.ui.log.CartographyViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Show on map and Hide from map for [CartographyViewModel]'s saved entries (journal redesign J8; UI split
 * Phase 2.8, PR 2, dispatch 2026-10-19-34, RECORD -1460), moved out of the ViewModel unchanged. It holds
 * no state of its own. Every value it reads from the screen's state comes through [uiState], every write
 * goes through [updateState], and the write launches in [scope], the ViewModel's own `viewModelScope`,
 * read each time rather than kept. It holds no job and cancels none.
 */
internal class CartographyShownOnMap(
    private val uiState: () -> CartographyUiState,
    private val updateState: ((CartographyUiState) -> CartographyUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val setShownOnMap: SetCartographyEntryShownOnMapUseCase,
) {

    /**
     * J8: shows or hides a **saved** entry on the Maps tab — the report menu's "Show on map" and "Hide
     * from map", the chip's "Hide" and "Hide all". Writes [CartographyEntry.shownOnMap] alone
     * ([SetCartographyEntryShownOnMapUseCase]) and, once the write has landed, sets that one field on
     * the listed entry and on the open entry, so the chip, the highlight and the report's menu change at
     * once. Nothing else about the open entry changes: an unsaved edit stays in it, still unsaved, and
     * its later save carries the new value, since it is the open entry's own.
     *
     * A draft is never shown on the map (owner: "Saved entries only"): a call for one is refused and
     * logged. So is an id the ViewModel has not loaded. A failed write changes nothing on screen and is
     * logged, and sets its own [CartographyUiState.shownOnMapErrorMessage], exactly
     * [SHOWN_ON_MAP_FAILED_MESSAGE] (the owner, continuation `2026-09-28-65`: "Set it to "Changes not
     * applied. Try again.""), never [CartographyUiState.saveErrorMessage], which keeps its own text for
     * the failures that set it.
     */
    fun onSetShownOnMap(id: String, shown: Boolean) {
        val state = uiState()
        val entry = state.entries.firstOrNull { it.id == id }
            ?: state.draftEntries.firstOrNull { it.id == id }
            ?: state.editingEntry?.takeIf { it.id == id }
        when {
            entry == null -> {
                Log.w(TAG, "Show on map was asked for entry '$id', which is not loaded; nothing written.")
                return
            }
            entry.isDraft -> {
                Log.w(TAG, "Show on map was asked for draft '$id'; only a saved entry is shown on the map, so nothing was written.")
                return
            }
        }
        scope().launch {
            setShownOnMap(id, shown).fold(
                onSuccess = {
                    updateState { current ->
                        current.copy(
                            entries = current.entries.map { if (it.id == id) it.copy(shownOnMap = shown) else it },
                            editingEntry = current.editingEntry?.let { if (it.id == id) it.copy(shownOnMap = shown) else it },
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't ${if (shown) "show" else "hide"} entry '$id' on the map.", error)
                    updateState { it.copy(shownOnMapErrorMessage = SHOWN_ON_MAP_FAILED_MESSAGE) }
                },
            )
        }
    }

    /** Clears [CartographyUiState.shownOnMapErrorMessage] once its Toast has shown (`AvailabilityScreen`). */
    fun onShownOnMapErrorDismissed() {
        updateState { it.copy(shownOnMapErrorMessage = null) }
    }
}
