package com.zynergylabs.forager.app.ui.log

// Moved from CartographyEntryReportScreen.kt unchanged (UI split Phase 1, PR 10e, dispatch 2026-10-19-22).

import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.RecordPoint

/** The report header's overflow menu, for tests. */
internal const val ENTRY_OVERFLOW_MENU_TAG = "entry-overflow-menu"

/** The entry delete dialog, for tests. */
internal const val ENTRY_DELETE_DIALOG_TAG = "entry-delete-dialog"

/**
 * Data part A (dispatch 2026-09-28-667) put it in the owner's word, "include" for "keep" (RECORD
 * -656), and "offline maps" for "offline regions", as the editor's panel names them.
 *
 * Reported verbatim per the Stage 2d dispatch's own instruction ("write the wording yourself... and
 * report the exact string you used, so the owner can adjust it") — a `const val` so the exact text
 * is visible in code, not buried in a chained string-builder call. Deliberately not a copy of
 * [LogEntryReportScreen]'s own empty-state text: a find and a Cartography entry hold different
 * things (a find is an observation record with taxonomic fields; a Cartography entry is a day
 * assembled from that day's records), so this names what an entry actually holds rather than
 * echoing the find screen's wording.
 */
internal const val EMPTY_ENTRY_MESSAGE =
    "This entry has nothing included yet. An entry can hold the finds, tracks, waypoints, offline " +
        "maps, and photos you choose to include from a day's records, plus anything you write. " +
        "Tap the three-dot menu, then Edit, to add something."

/** Reported verbatim, same reasoning as [EMPTY_ENTRY_MESSAGE] — the toggle's own label, Journal Stage 2e-i. */
internal const val OFFLINE_TOGGLE_LABEL = "Offline map"

/**
 * Reported verbatim, same reasoning as [EMPTY_ENTRY_MESSAGE]. States the tradeoff plainly rather than
 * dressing it up as a warning — the current offline style is a hosting-cost constraint the owner
 * intends to lift later, not a design choice, but it is today's reality and a user should not meet it
 * as a surprise the first time they flip this switch.
 */
internal const val OFFLINE_TOGGLE_CAPTION = "Offline maps show shapes only — no place names, road names, or icons."

/**
 * The entry map's waypoint pins (M1): each resolved marker, named by the entry's own snapshot of that
 * waypoint (`CartographyEntry.waypointDecisions`), which is what the entry's text lists too. A marker
 * the entry has no decision for keeps the placeholder name it always had.
 */
internal fun entryMapWaypoints(entry: CartographyEntry, markers: List<RecordPoint>): List<Waypoint> = markers.map { marker ->
    Waypoint(
        id = marker.recordId,
        lat = marker.at.lat,
        lng = marker.at.lng,
        altitude = null,
        name = entry.waypointDecisions.firstOrNull { it.waypointId == marker.recordId }?.name ?: "Waypoint",
        note = "",
        createdAtEpochMillis = 0L,
    )
}
