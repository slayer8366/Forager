package com.zynergylabs.forager.app.ui.log

// Moved from MushroomLogViewModel.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

import com.zynergylabs.forager.app.domain.model.MushroomLogEntry

/**
 * Shown when a capture lands with no find open — see
 * [MushroomLogGallery.rescueCaptureWithNoEditingEntry]. **Says where the photo went, not that
 * something went wrong** (owner's wording, 2026-09-17): the user's problem at that moment is
 * finding their picture. Internal
 * rather than private so the test asserts the string the user sees rather than a copy of it.
 */
internal const val PHOTO_SAVED_TO_ALBUM_MESSAGE = "Photo saved to album."

/**
 * Whether [current] is a re-edit's draft that still equals its committed parent in [entries]: the
 * case [MushroomLogViewModel.onLeaveEditingIncidentally] deletes rather than keeps (see that
 * function's doc comment for why the comparison is exact). The one definition, shared with
 * [leaveKeepsDraft].
 */
internal fun isUnchangedReEdit(current: MushroomLogEntry, entries: List<MushroomLogEntry>): Boolean {
    val parent = current.draftOfEntryId?.let { parentId -> entries.firstOrNull { it.id == parentId } } ?: return false
    return current.copy(id = parent.id, isDraft = false, draftOfEntryId = null) == parent
}

/**
 * Whether leaving [left] by [MushroomLogViewModel.onLeaveEditingIncidentally] leaves a draft row in
 * Drafts (intent 2026-09-28-44, F1; the owner: "Snackbar only for real drafts"). False for a
 * committed find that was only viewed (the leave only closes it) and for an unchanged re-edit (the
 * leave deletes its draft copy); true for a new find's draft and a changed re-edit. The "Saved to
 * Drafts" snackbar is offered exactly when this is true, so that it is never offered for a
 * committed find and never says a draft was saved when none was.
 */
internal fun leaveKeepsDraft(left: MushroomLogEntry, entries: List<MushroomLogEntry>): Boolean =
    left.isDraft && !isUnchangedReEdit(left, entries)
