package com.zynergylabs.forager.app.ui.availability

// Moved from AvailabilityScreen.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.ui.log.JournalTopTab
import com.zynergylabs.forager.app.ui.log.RecordsSubTab

internal enum class ResultsTab(val label: String) {
    LIST("List"),
    MAP("Maps"),
    SEASONAL("Seasonal"),
}

/** How long a first back press keeps "exit on the next one" armed — see [AvailabilityScreen]. */
internal const val DOUBLE_BACK_EXIT_WINDOW_MS = 2000L

/** The compact Tools drawer's sheet, for tests (map chrome at 80%, dispatch 2026-09-28-56 as amended by -58). */
internal const val TOOLS_DRAWER_SHEET_TAG = "tools-drawer-sheet"

/**
 * Where the Tools drawer's "Offline maps" row was tapped (RECORD -871): the bottom-nav tab and its results tab, the
 * Journal's top tab and Records chip as they were, and whether the map was fullscreen. Back from Records puts these
 * back under the reopened drawer; see `toolsReturn` in [AvailabilityScreen].
 */
internal data class ToolsOfflineMapsReturn(
    val compactTab: CompactTab,
    val selectedTab: ResultsTab,
    val journalTopTab: JournalTopTab,
    val recordsFilter: RecordsSubTab,
    val mapFullscreen: Boolean,
)

/**
 * Dispatch 2026-09-28-502, Amendment 1: what the walker is told when the waypoint they are navigating to
 * was deleted, meanwhile or while the app was closed. Provisional words, accepted by the planner.
 */
internal const val WAYPOINT_NAVIGATION_GONE_TEXT = "Stopped navigating: that waypoint was deleted."

/** Dispatch 2026-11-05-05, Amendment 1, A8 (RECORD -2148): the same for a planned trip, in its words. */
internal const val TRIP_NAVIGATION_GONE_TEXT = "Stopped navigating: that trip was deleted."

/** What the walker is told when the target of [kind] went away; a waypoint's words for a navigation with no kind, as before. */
internal fun navigationGoneText(kind: NavigationTargetKind?): String = when (kind) {
    NavigationTargetKind.TRIP -> TRIP_NAVIGATION_GONE_TEXT
    NavigationTargetKind.WAYPOINT, null -> WAYPOINT_NAVIGATION_GONE_TEXT
}
