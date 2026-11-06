package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.JournalTab
import java.time.LocalDate

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s Journal entries' state and callbacks,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 22 separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** The Journal's entries: their state and the entry editor's and report's callbacks, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenJournalEntries(
    /** Journal Stage 2b's new authored entity — see [com.zynergylabs.forager.app.ui.log.CartographyScreen]'s own doc comment for all of the following. Defaulted, same reasoning as the finds log's `logUiState`. */
    val cartographyUiState: CartographyUiState = CartographyUiState(),
    val onOpenCartographyEntry: (String) -> Unit = {},
    val onStartCartographyEntry: (LocalDate) -> Unit = {},
    val onCloseCartographyEntry: () -> Unit = {},
    val onCartographyTextChanged: (String) -> Unit = {},
    val onCartographyTagsChanged: (List<String>) -> Unit = {},
    val onSetFindDecision: (String, Boolean) -> Unit = { _, _ -> },
    val onSetTrackDecision: (String, Boolean) -> Unit = { _, _ -> },
    val onSetWaypointDecision: (String, Boolean) -> Unit = { _, _ -> },
    val onSetOfflineRegionDecision: (Long, Boolean) -> Unit = { _, _ -> },
    /** Data part A (dispatch 2026-09-28-667): a group switch in the entry editor's "In this entry" panel, `CartographyViewModel.onSetEntryGroupIncluded`. */
    val onSetEntryGroupIncluded: (EntryGroup, Boolean) -> Unit = { _, _ -> },
    val onToggleKeptPhoto: (String) -> Unit = {},
    /** Entry-photo-acquisition dispatch, Item 2. See [CartographyScreen]'s own doc comment on this same parameter. */
    val onAcquirePhotoForCartographyEntry: (PhotoSource) -> Unit = {},
    val onFinishCartographyEntry: () -> Unit = {},
    /** Explicit Save for a committed Cartography entry — device-check patch, Item 1. Threaded straight through to CartographyScreen, whose own lifecycle observer also uses it for the backgrounding-return prompt's Commit option (pending-edit-and-fixes dispatch, Item 1). */
    val onSaveCartographyEntry: () -> Unit = {},
    /** The leave-prompt's Discard option — device-check patch, Item 1. */
    val onDiscardCartographyEntryChanges: () -> Unit = {},
    /** The backgrounding-return prompt's "Save as draft" option — pending-edit-and-fixes dispatch, Item 1. See CartographyScreen's own lifecycle-observer doc comment. */
    val onSaveCartographyEntryAsDraft: () -> Unit = {},
    val onDeleteCartographyEntry: (String) -> Unit = {},
    /**
     * An Entries card's swipe Delete (journal redesign J4b L2): a *pending* delete with Undo
     * (`CartographyViewModel.requestDeleteEntry`), passed to `JournalTab`.
     * `null` (the default) leaves the cards without the swipe.
     */
    val onRequestDeleteCartographyEntry: ((String) -> Unit)? = null,
    /**
     * J8-3: shows or hides a saved entry on the Maps tab (`CartographyViewModel.onSetShownOnMap`): the
     * entry report's "Show on map" and "Hide from map", and the Maps-tab chip's "Hide"
     * and "Hide all". Defaulted, so the many tests of this screen that never show an entry are unchanged.
     */
    val onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit = { _, _ -> },
    /** Clears [cartographyUiState]'s `shownOnMapErrorMessage` once its Toast has shown (J8, continuation `2026-09-28-65`). */
    val onCartographyShownOnMapErrorDismissed: () -> Unit = {},
    /**
     * Clears [cartographyUiState]'s `saveErrorMessage` once its Toast has shown. That Toast is hosted
     * by the Journal itself, [JournalTab], not here, so this is
     * threaded to it (intent `2026-09-28-68`, continuation `2026-09-28-76`).
     */
    val onCartographySaveErrorDismissed: () -> Unit = {},
)
