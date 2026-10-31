package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls

/**
 * Route plotting's links with the Tools drawer's Trip Planner (dispatch 2026-10-30-08, step 3): [controls] carries the way back,
 * and [onOpenFromTools] is a route row's tap; both `null` when there is no plotting (tests, other callers).
 */
internal class RoutePlottingLinks(val controls: RoutePlottingControls?, val onOpenFromTools: ((PlannedTrip) -> Unit)?)

/**
 * A route row in the Trip Planner opens the route on the Maps tab: the drawer closes, the tab becomes Maps, and the route opens
 * (fit, line, card). Back closes the card and the line, the tab it was opened over comes back and the drawer reopens (the
 * planner's ruling on Q5: "Back retraces the way in"). The tab is remembered here, above the tabs, so it survives the switch to
 * Maps; it is the screen's own state, not the plotting's.
 */
@Composable
internal fun rememberRoutePlottingLinks(controls: RoutePlottingControls?, state: AvailabilityScreenState): RoutePlottingLinks {
    val openedOver = remember { mutableStateOf(CompactTab.MAP) }
    if (controls == null) return RoutePlottingLinks(null, null)
    val withReturn = controls.copy(
        onReturnToTripPlanner = {
            state.setCompactTab(openedOver.value)
            state.setIsDrawerOpen(true)
        },
    )
    return RoutePlottingLinks(
        controls = withReturn,
        onOpenFromTools = { trip ->
            openedOver.value = state.compactTab()
            state.setIsDrawerOpen(false)
            state.setCompactTab(CompactTab.MAP)
            controls.actions.onOpenSavedRoute(trip, fromTripPlanner = true)
        },
    )
}

/**
 * Amendment 5, N-11 (Sorrel's review of #449): a saved route whose trip is no longer in [plannedTrips] (deleted from the Trip
 * Planner while it showed) closes its card and its line, as Back would. The route showing holds a copy of its trip, so nothing
 * else would notice the delete.
 */
@Composable
internal fun CloseDeletedRoute(controls: RoutePlottingControls?, plannedTrips: List<PlannedTrip>) {
    val showing = controls?.state?.mode as? RoutePlotMode.Showing
    val gone = showing != null && plannedTrips.none { it.id == showing.trip.id }
    LaunchedEffect(gone, showing?.serial) {
        if (gone) controls?.actions?.onCloseSavedRoute()
    }
}
