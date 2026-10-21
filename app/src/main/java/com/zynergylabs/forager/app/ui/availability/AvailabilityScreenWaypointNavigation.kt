package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, PR 1 (dispatch 2026-10-19-40): moved out of AvailabilityScreen's body as a plain function. Not
// composable, so composition is unchanged; the three lambdas are built where the body builds the holder, and every reader
// of them comes after that line.

import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.JournalTopTab
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin

/** Starting and ending a navigation to a chosen waypoint, and the return control that ends one first. */
internal class WaypointNavigationActions(
    val start: (WaypointNavigationOrigin) -> Unit,
    val end: (Boolean) -> Unit,
    val onNavigationControl: () -> Unit,
)

/**
 * [isNavigating] and [navigatingToWaypoint] are the values of the composition that builds these, as the
 * body's own lambdas captured them; every state the lambdas read or write goes through [state].
 */
internal fun waypointNavigationActions(
    state: AvailabilityScreenState,
    isNavigating: Boolean,
    navigatingToWaypoint: Waypoint?,
    onNavigateToWaypoint: ((String) -> Unit)?,
    onStopWaypointNavigation: () -> Unit,
    onToggleReturning: () -> Unit,
): WaypointNavigationActions {
    // Dispatch -502: "Navigate" on a waypoint, from the Maps tab's bubble or its sheet, or from Records. The Maps tab comes
    // up with the navigation view. Already navigating (a return, or another waypoint), isNavigating never goes false in
    // between, so there is no new start zoom (the planner's ruling); a view the walker had moved away from is taken back,
    // as "Return to Route" does.
    val startWaypointNavigation: (WaypointNavigationOrigin) -> Unit = { origin ->
        onNavigateToWaypoint?.let { navigate ->
            val wasNavigating = isNavigating
            state.setWaypointNavigationOrigin(origin)
            navigate(origin.waypointId)
            state.setCompactTab(CompactTab.MAP)
            state.setSelectedTab(ResultsTab.MAP)
            if (wasNavigating && state.navigationFollowBroken()) {
                state.setNavigationFollowBroken(false)
                state.setNavigationViewRequestId(state.navigationViewRequestId() + 1)
            }
        }
    }
    // Dispatch -502: ends the waypoint navigation. With [retrace] (Back) the walker is put back where Navigate was tapped,
    // one step, as the owner chose; the X-circle and the HUD's X end it where the walker is. A return it paused picks up
    // again (the ViewModel's doing).
    val endWaypointNavigation: (Boolean) -> Unit = { retrace ->
        val origin = state.waypointNavigationOrigin()
        state.setWaypointNavigationOrigin(null)
        onStopWaypointNavigation()
        if (retrace) {
            when (origin) {
                null -> Unit
                is WaypointNavigationOrigin.MapBubble, is WaypointNavigationOrigin.MapDetails -> state.setMapWaypointReopen(origin)
                is WaypointNavigationOrigin.RecordsRow, is WaypointNavigationOrigin.RecordsDetails -> {
                    state.journalScreenState.topTab = JournalTopTab.RECORDS
                    state.setCompactTab(CompactTab.JOURNAL)
                    if (origin is WaypointNavigationOrigin.RecordsDetails) state.setRecordsWaypointReopen(origin.waypointId)
                }
                // RECORD -890: back to the journal entry whose map Navigate was tapped on. The entry is still the
                // Journal's open one; the bubble is not reopened, as a Records row reopens nothing.
                is WaypointNavigationOrigin.JournalEntryMap -> {
                    state.journalScreenState.topTab = JournalTopTab.CARTOGRAPHY
                    state.setCompactTab(CompactTab.JOURNAL)
                }
            }
        }
    }
    // The return control and the HUD's X: a waypoint navigation first, while there is one; otherwise the return, as before.
    val onNavigationControl: () -> Unit = {
        if (navigatingToWaypoint != null) endWaypointNavigation(false) else onToggleReturning()
    }
    return WaypointNavigationActions(start = startWaypointNavigation, end = endWaypointNavigation, onNavigationControl = onNavigationControl)
}
