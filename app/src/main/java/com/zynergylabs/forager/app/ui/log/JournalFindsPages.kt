package com.zynergylabs.forager.app.ui.log

// Moved from JournalTab.kt unchanged (UI split Phase 1, PR 10e, dispatch 2026-10-19-22).

import com.zynergylabs.forager.app.domain.model.MushroomLogEntry

/**
 * A page of the Finds section (motion Part 3, item 1): the gallery, or the open find in its report, its editor, or one of the
 * editor's two pickers. Each opened page carries the find it shows, so it can be drawn while it slides away after the find has
 * closed. [depth] is how far in it is (a page deeper than the current one slides in over it; a shallower one is uncovered by the
 * current one sliding out), and [kind] is which page it is: a change of the find within one page (a field typed, the open id
 * swapped for its draft row) updates it in place, with no slide.
 */
internal sealed interface FindsPage {
    val depth: Int
    val kind: String

    data object Gallery : FindsPage {
        override val depth = 0
        override val kind = "gallery"
    }

    sealed interface WithFind : FindsPage {
        val entry: MushroomLogEntry
    }

    data class Report(override val entry: MushroomLogEntry) : WithFind {
        override val depth = 1
        override val kind = "report"
    }

    data class Edit(override val entry: MushroomLogEntry) : WithFind {
        override val depth = 2
        override val kind = "edit"
    }

    data class PickLocation(override val entry: MushroomLogEntry) : WithFind {
        override val depth = 3
        override val kind = "pick-location"
    }

    data class PullPhoto(override val entry: MushroomLogEntry) : WithFind {
        override val depth = 3
        override val kind = "pull-photo"
    }
}

/** The find shown over the Journal ([FindOverView]). */
internal const val FIND_OVER_VIEW_TAG = "journal-find-over-view"

/**
 * A find opened from a map bubble over whatever the Journal was showing (M1; continuation
 * `2026-09-28-30`: the owner's "Open find, Back returns" and "Open drawer to the find", and the
 * planner's ruling that the Maps route must not overwrite the saved Records filter). [findId] is the
 * find asked for; [shown] turns true once the ViewModel has opened it (opening is asynchronous,
 * `MushroomLogViewModel.onOpenEntry`), and the overlay then stays until no find is open any more
 * (Back, the report's own back arrow, a save or a delete), which leaves the view under it exactly as
 * it was: the same top tab, the same Records chip, the same day entry and its scroll.
 */
internal data class FindOverView(val findId: String, val shown: Boolean = false)

/**
 * [state] after the open find becomes [openFindId] (`MushroomLogUiState.editingEntry?.id`): shown
 * once the asked-for find is open, kept while any find stays open (starting an edit swaps the open
 * id to its draft row), and gone once none is. A request whose find never opened stays waiting.
 *
 * [openFindDraftOf] (`editingEntry?.draftOfEntryId`) also shows it: a find opened straight into its edit form (the All
 * logbook's long-press Edit, RECORD -876) opens as its draft row, whose own id is never the asked-for find's.
 */
internal fun nextFindOverView(state: FindOverView?, openFindId: String?, openFindDraftOf: String? = null): FindOverView? = when {
    state == null -> null
    !state.shown -> if (openFindId == state.findId || openFindDraftOf == state.findId) state.copy(shown = true) else state
    openFindId == null -> null
    else -> state
}
