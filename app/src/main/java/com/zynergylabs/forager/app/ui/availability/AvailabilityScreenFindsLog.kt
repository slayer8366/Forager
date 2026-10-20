package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import java.time.LocalDate

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s finds log state and callbacks,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 19 separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** The finds log's state, its entry and photo callbacks, and the album's photo callbacks, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenFindsLog(
    /**
     * The mushroom log's own state, read by the Journal tab ([JournalTab]).
     * Defaulted, like the screen's `mapSlot`, so the many existing tests of this screen that have nothing
     * to do with the log don't need to pass log-specific state and callbacks just to compile.
     */
    val logUiState: MushroomLogUiState = MushroomLogUiState(),
    /** Starts and immediately opens a new log entry — the map's "Log a find" option is the only production caller; entries have no other creation path (see `docs/plans/mushroom-log.md`'s Navigation section). */
    val onStartLogEntry: (LatLng?, LocalDate) -> Unit = { _, _ -> },
    val onOpenLogEntry: (String) -> Unit = {},
    val onCloseLogEntry: () -> Unit = {},
    val onLogEntryChanged: (MushroomLogEntry) -> Unit = {},
    /** Begins editing the currently-open (committed) entry — see [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onStartEditingEntry]'s own doc comment. A no-op if it's already a draft. */
    val onStartEditingLogEntry: () -> Unit = {},
    /**
     * Opens a row and, if it's a committed entry, immediately begins editing it — one atomic
     * ViewModel operation ([com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onOpenEntryForEditing]),
     * not [onOpenLogEntry] and [onStartEditingLogEntry] chained here. A find tile's long-press Edit
     * in [JournalTab] calls it.
     */
    val onOpenLogEntryForEditing: (String) -> Unit = {},
    /** Save — commits the currently-open entry. See [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onSaveEntry]'s own doc comment. */
    val onSaveLogEntry: () -> Unit = {},
    /** Cancel — the only exit that discards anything. See [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onCancelEditing]'s own doc comment. */
    val onCancelLogEntryEditing: () -> Unit = {},
    /** Leaving without answering — tab switch, backgrounding, back — persists the draft without committing. See [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onLeaveEditingIncidentally]'s own doc comment. */
    val onLeaveLogEntryEditingIncidentally: () -> Unit = {},
    /** Discards a draft by id outright — the compact scaffold's "Discard" Snackbar action target, since by the time it's tapped [logUiState].editingEntry is already null. Same operation as [onDeleteLogEntry]. */
    val onDiscardLogDraft: (String) -> Unit = {},
    val onAddLogPhoto: (PhotoSource) -> Unit = {},
    val onRemoveLogPhoto: (LogPhoto) -> Unit = {},
    val onPullLogPhoto: (LogPhoto) -> Unit = {},
    val onDeleteLogEntry: (String) -> Unit = {},
    val onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = {},
    /** Standalone-photos dispatch: Camera/Gallery acquisition, no owning find — the album's own Camera/Import buttons (the Journal's Entries album). */
    val onAddGalleryPhoto: (PhotoSource) -> Unit = {},
    /** Clears [logUiState]'s `saveErrorMessage` once its Toast has shown — see [JournalTab]'s identical parameter. */
    val onSaveLogErrorDismissed: () -> Unit = {},
    /**
     * An album photo's long-press Delete (J4b L3): a *pending* delete with Undo
     * (`MushroomLogViewModel.requestDeleteGalleryPhoto`), for the compact tree's album only. `null`
     * (the default) leaves the photos without the menu.
     */
    val onRequestDeleteGalleryPhoto: ((String) -> Unit)? = null,
)
