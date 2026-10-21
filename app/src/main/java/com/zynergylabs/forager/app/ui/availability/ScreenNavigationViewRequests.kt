package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** The navigation view's follow and zoom requests, read and written through the States [rememberNavigationViewRequests] remembers. */
internal class NavigationViewRequestsState(
    private val followBroken: MutableState<Boolean>,
    private val viewRequestId: MutableIntState,
    private val zoomPending: MutableState<Boolean>,
) {
    var navigationFollowBroken: Boolean
        get() = followBroken.value
        set(value) { followBroken.value = value }
    var navigationViewRequestId: Int
        get() = viewRequestId.intValue
        set(value) { viewRequestId.intValue = value }
    var navigationZoomPending: Boolean
        get() = zoomPending.value
        set(value) { zoomPending.value = value }
}

/**
 * Whether the user has moved the map away from the navigation view, the "Return to Route" requests, and the start zoom's
 * pending flag, all held above the tab. Moved out of [AvailabilityScreen] whole by nav step 2, PR 4a (dispatch 2026-10-21-01, RECORD -1519), and called at the line
 * where the group stood, so its remembers and effects keep their order and keys (-1484). The holder wraps the original
 * State objects, never copies of them (Stage G). The four `rememberSaveable` calls move with it: their saved-state
 * keys change with the move, which a process restore within one build does not notice and an app update drops anyway
 * (the S22, 2026-10-21: saved state does not survive an update).
 */
@Composable
internal fun rememberNavigationViewRequests(isNavigating: Boolean): NavigationViewRequestsState {
    // Dispatch 2026-09-28-430 (plan task T22): whether the user has moved the map away from the
    // navigation view, and the "Return to Route" requests, held here above the tab so that leaving
    // the Maps tab and coming back keeps them (CLAUDE.md, UX defaults; the legend's flag above is
    // held the same way). Cleared when navigation stops, so the next Return starts in the view.
    val navigationFollowBrokenState = rememberSaveable { mutableStateOf(false) }
    var navigationFollowBroken by navigationFollowBrokenState
    val navigationViewRequestIdState = rememberSaveable { mutableIntStateOf(0) }
    var navigationViewRequestId by navigationViewRequestIdState
    LaunchedEffect(isNavigating) { if (!isNavigating) navigationFollowBroken = false }
    // Dispatch 2026-09-28-440: the set zoom is applied once per navigation, when it starts: pending
    // from the start until the map reports it applied. Held above the tab, so a tab change does not
    // apply it again; the navigating flag it is set from is saved too, so a configuration change
    // that recomposes this screen mid-navigation is not read as a new start.
    val navigationZoomPendingState = rememberSaveable { mutableStateOf(false) }
    var navigationZoomPending by navigationZoomPendingState
    var navigationWasOn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isNavigating) {
        if (isNavigating && !navigationWasOn) navigationZoomPending = true
        if (!isNavigating) navigationZoomPending = false
        navigationWasOn = isNavigating
    }
    return remember { NavigationViewRequestsState(navigationFollowBrokenState, navigationViewRequestIdState, navigationZoomPendingState) }
}
