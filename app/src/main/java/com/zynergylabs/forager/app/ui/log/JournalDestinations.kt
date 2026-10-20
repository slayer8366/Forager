package com.zynergylabs.forager.app.ui.log

// Moved from JournalTab.kt unchanged (UI split Phase 1, PR 10e, dispatch 2026-10-19-22).

/**
 * Which screen [JournalTab]'s relocated Finds section shows for [MushroomLogUiState.editingEntry] —
 * "editing" is the accurate name for what that field means (see [MushroomLogViewModel]'s doc comment
 * on the persisted-draft model), but which of [LogEntryReportScreen]/[LogEntryDetailScreen] the
 * *user* sees for it depends on how they got there, tracked here rather than inferred from the
 * entry's own content (an entry with nothing recorded yet is a legitimate thing to view a report of
 * too, once the user backs out of editing it without filling anything in — REPORT stays correct for
 * that case where "does it have data" would not).
 */
internal enum class JournalEntryMode { REPORT, EDIT }

/**
 * A one-shot request to open a specific place inside the Journal destination, made from outside it
 * — Stage 2d's routing fix for the map "+" icon bar's "Log a find" flow. See [JournalTab]'s own doc
 * comment, "The map '+' routing bug," for the full trace and for why this is one small enum (not a
 * boolean, so a future caller wanting a different destination adds a case here rather than a second
 * flag) rather than a shared navigation abstraction. `internal`, not `private`: [JournalTab] consumes it,
 * and `AvailabilityScreen.kt` owns the single instance the map's "Log a find" closures set.
 */
internal enum class PendingJournalDestination {
    /** Land in Records → Finds, editing the entry [MushroomLogViewModel.onStartNewEntry] just created. */
    EDIT_NEW_FIND,

    /**
     * M1: show the find the caller has just opened (`onOpenEntry`, with its id passed beside this) in
     * its report, over whatever the Journal is showing, without changing the top tab or the Records
     * chip; Back returns to them ([FindOverView]). The Maps tab's "Open in Journal".
     */
    VIEW_FIND,

    /**
     * J8-4: open the day entry whose id is passed beside this in its report, on Entries, the one
     * top-tab change opening it requires (the saved Records chip is untouched). The Maps tab's "Open
     * entry" (owner's Q1 ruling, "Open in Journal, prompt first"); [CartographyScreen]'s
     * `openEntryRequest` asks first when another entry is open in its editor with unsaved changes.
     */
    VIEW_ENTRY,

    /**
     * Plan T16: after a GPX import, Records on the Tracks chip with the first new track's details open
     * (its id passed beside this), or the Tracks list alone when the file was not imported.
     */
    VIEW_IMPORTED_TRACK,

    /**
     * RECORD -868: the Tools drawer's "Offline maps" button ("Records, on the Offline maps chip (Recommended)").
     * Records on the Offline maps chip. Back from there is `AvailabilityScreen`'s, which remembers where the button
     * was tapped (RECORD -871).
     */
    VIEW_OFFLINE_MAPS,
}
