package com.zynergylabs.forager.app.ui.route

import androidx.lifecycle.ViewModel
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RoutePlotDraft
import com.zynergylabs.forager.app.domain.route.RouteStop
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Maps tab's route card is showing (dispatch 2026-10-30-08, step 3). */
sealed interface RoutePlotMode {
    /** No card and no plotted line. */
    data object Off : RoutePlotMode

    /** A route being plotted. A leg is being routed while [RoutePlotDraft.nextLeg] is not `null`. */
    data class Plotting(val draft: RoutePlotDraft) : RoutePlotMode

    /** A saved route, opened from its Trip Planner row or its pin. [trip]'s route is never `null`. */
    data class Showing(val trip: PlannedTrip) : RoutePlotMode
}

/**
 * The route card's state. [expanded] is the card's anchor, user-set, so it is kept across a tab change (CLAUDE.md, UX
 * defaults). [pendingDiscard] is a saved route tapped while one is being plotted, waiting on "Discard this route?" (Amendment 1,
 * Q5). [saveFailed] keeps the save dialog open with "Couldn't save the route. Try again." (Amendment 1).
 */
data class RoutePlottingUiState(
    val mode: RoutePlotMode = RoutePlotMode.Off,
    val expanded: Boolean = false,
    val saveDialogOpen: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val pendingDiscard: PlannedTrip? = null,
)

/**
 * Route plotting on the Maps tab (dispatch 2026-10-30-08, step 3): the stops, the legs and the card, Activity-scoped so they
 * survive a tab change (the first stop, (d)). It decides when a leg is routed and when it is dropped; the routing itself is
 * [routeLeg] (`PlotRouteUseCase.leg` in production) and the save is [saveRoute] (`SavePlottedRouteUseCase`).
 *
 * Legs are routed one at a time, in order: the engine runs one route at a time in the whole process
 * (`routing/BRouterTrailRouter.kt:199-201`). Undo and Cancel cancel a leg whose stop they remove.
 */
class RoutePlottingViewModel(
    private val routeLeg: suspend (LatLng, LatLng) -> RouteLeg,
    private val saveRoute: suspend (PlottedRoute, String, LocalDate?) -> Result<PlannedTrip>,
    private val onSaved: () -> Unit,
    private val errorLog: ErrorLog,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutePlottingUiState())
    val uiState: StateFlow<RoutePlottingUiState> = _uiState.asStateFlow()

    /** "Plot route" in a waypoint's bubble: plotting starts from [stop], and the card peeks at once (Amendment 1, Q1). */
    fun onStartPlotting(stop: RouteStop) {}

    /** A waypoint tapped while plotting: added as the next stop, unless it is the last stop again. */
    fun onWaypointTapped(stop: RouteStop) {}

    /** "Undo": the last stop and its leg removed. */
    fun onUndo() {}

    /** "Cancel", or Back: the route dropped, in one tap (RECORD -1817). */
    fun onCancel() {}

    fun onSetExpanded(expanded: Boolean) {}

    /** "Save": the save dialog, once every leg is in. */
    fun onOpenSaveDialog() {}

    fun onDismissSaveDialog() {}

    /** The save dialog's "Save". */
    fun onSave(name: String, date: LocalDate?) {}

    /** A saved route's row or pin. Asks "Discard this route?" first while one is being plotted (Amendment 1, Q5). */
    fun onOpenSavedRoute(trip: PlannedTrip) {}

    /** "Discard": the route being plotted is dropped and the saved one opens. */
    fun onConfirmDiscard() {}

    /** "Keep plotting": the prompt closes and nothing else changes. */
    fun onKeepPlotting() {}

    /** Back on a saved route's card: the card and the line close. */
    fun onCloseSavedRoute() {}
}
