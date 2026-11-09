package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.WaypointNavigation
import kotlinx.coroutines.flow.update

/*
 * The waypoint navigation's start and stop, moved out of AvailabilityViewModel.kt unchanged as extension functions on [AvailabilityViewModel]
 * (dispatch 2026-10-23-03 Amendment 1; the owner: "Split it now, before merge (Recommended)", RECORD -1490's
 * rule that a file over 800 lines is split before a feature goes in). A pure move: each function's body and doc
 * comment are as they were, they are still ViewModel functions (RECORD -1450: every decision to start or stop a job
 * stays in a ViewModel function), and every caller reaches them by the same name. The members they read were made
 * `internal` for this, and nothing else changed.
 */

/**
 * "Navigate" on waypoint [waypointId] (dispatch 2026-09-28-502, plan tasks T8 and T9), with or without a
 * recording. A return under way is paused first (step 6: the waypoint overrules it while active) and
 * remembered, so [onStopWaypointNavigation] picks it back up; choosing another waypoint while navigating
 * keeps that. The pause comes before this state changes, on the same thread, so the screen never sees a
 * moment with neither on: no new start zoom (the planner's ruling).
 */
fun AvailabilityViewModel.onNavigateToWaypoint(waypointId: String) {
    waypointNavigationStore.waypointNavigationChosenHere = true
    val current = _uiState.value.waypointNavigation
    val resumesReturn = when {
        current != null -> current.resumesReturn
        returnLeg.isReturning -> {
            returnLeg.pause()
            true
        }
        else -> false
    }
    val navigation = WaypointNavigation(waypointId, resumesReturn)
    _uiState.update { it.copy(waypointNavigation = navigation) }
    waypointNavigationStore.keepWaypointNavigation(navigation)
}

/**
 * Ends the waypoint navigation (Back, the X-circle, the HUD's ✕, or its waypoint deleted), clears what is
 * kept, and picks a paused return back up (step 6). The return captures its line afresh (the planner's
 * ruling). Nothing to end does nothing.
 */
fun AvailabilityViewModel.onStopWaypointNavigation() {
    waypointNavigationStore.waypointNavigationChosenHere = true
    val ending = _uiState.value.waypointNavigation ?: return
    _uiState.update { it.copy(waypointNavigation = null) }
    waypointNavigationStore.keepWaypointNavigation(null)
    if (ending.resumesReturn) returnLeg.resume()
}
