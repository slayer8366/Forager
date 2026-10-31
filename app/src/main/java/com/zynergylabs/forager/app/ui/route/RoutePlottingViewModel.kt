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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Maps tab's route card is showing (dispatch 2026-10-30-08, step 3). */
sealed interface RoutePlotMode {
    /** No card and no plotted line. */
    data object Off : RoutePlotMode

    /** A route being plotted. A leg is being routed while [RoutePlotDraft.nextLeg] is not `null`. */
    data class Plotting(val draft: RoutePlotDraft) : RoutePlotMode

    /**
     * A saved route, opened from its Trip Planner row or its pin. [trip]'s route is never `null`. [serial] counts the opens, so
     * the map fits the route each time it is opened, the same route again included. [fromTripPlanner]: Back reopens the Tools
     * drawer it was opened from (the planner's ruling on Q5: "Back retraces the way in").
     */
    data class Showing(val trip: PlannedTrip, val serial: Int = 0, val fromTripPlanner: Boolean = false) : RoutePlotMode
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
    val pendingDiscardFromTripPlanner: Boolean = false,
)

/** What the route card, the bubble and the Trip Planner ask of the plotting; [RoutePlottingViewModel] does each. */
interface RoutePlottingActions {
    fun onStartPlotting(stop: RouteStop)
    fun onWaypointTapped(stop: RouteStop)
    fun onUndo()
    fun onCancel()
    fun onSetExpanded(expanded: Boolean)
    fun onOpenSaveDialog()
    fun onDismissSaveDialog()
    fun onSave(name: String, date: LocalDate?)
    fun onOpenSavedRoute(trip: PlannedTrip, fromTripPlanner: Boolean)
    fun onConfirmDiscard()
    fun onKeepPlotting()
    fun onCloseSavedRoute()
}

/**
 * The Maps tab's plotting inputs, carried in `MapRecordSources` (the planner's ruling on Q11): the state and the actions, and
 * [onReturnToTripPlanner], what Back does after closing a route opened from the Trip Planner (the planner's ruling on Q5: the
 * page it was opened over comes back and the Tools drawer reopens).
 */
data class RoutePlottingControls(
    val state: RoutePlottingUiState,
    val actions: RoutePlottingActions,
    val onReturnToTripPlanner: () -> Unit = {},
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
) : ViewModel(), RoutePlottingActions {

    private val _uiState = MutableStateFlow(RoutePlottingUiState())
    val uiState: StateFlow<RoutePlottingUiState> = _uiState.asStateFlow()

    /** The leg being routed now, if any; [legFor] is its two stops, so a stop removed under it cancels it. */
    private var legJob: Job? = null
    private var legFor: Pair<RouteStop, RouteStop>? = null

    /** "Plot route" in a waypoint's bubble: plotting starts from [stop], and the card peeks at once (Amendment 1, Q1). */
    override fun onStartPlotting(stop: RouteStop) {
        cancelLeg()
        _uiState.value = RoutePlottingUiState(mode = RoutePlotMode.Plotting(RoutePlotDraft(listOf(stop))))
    }

    /** A waypoint tapped while plotting: added as the next stop, unless it is the last stop again. */
    override fun onWaypointTapped(stop: RouteStop) {
        val draft = plottingDraft() ?: return
        val next = draft.withStop(stop) ?: return
        setDraft(next)
        routeNextLeg()
    }

    /** "Undo": the last stop and its leg removed. */
    override fun onUndo() {
        val draft = plottingDraft() ?: return
        if (draft.stops.size < 2) return
        val next = draft.undo()
        if (legFor != null && legFor != next.nextLeg) cancelLeg()
        setDraft(next)
        routeNextLeg()
    }

    /** "Cancel", or Back: the route dropped, in one tap (RECORD -1817). */
    override fun onCancel() {
        if (_uiState.value.mode !is RoutePlotMode.Plotting) return
        cancelLeg()
        _uiState.value = RoutePlottingUiState(expanded = _uiState.value.expanded)
    }

    override fun onSetExpanded(expanded: Boolean) {
        _uiState.update { it.copy(expanded = expanded) }
    }

    /** "Save": the save dialog, once every leg is in. */
    override fun onOpenSaveDialog() {
        if (plottingDraft()?.toRoute() == null) return
        _uiState.update { it.copy(saveDialogOpen = true, saveFailed = false) }
    }

    override fun onDismissSaveDialog() {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saveDialogOpen = false, saveFailed = false) }
    }

    /** The save dialog's "Save". */
    override fun onSave(name: String, date: LocalDate?) {
        val state = _uiState.value
        if (!state.saveDialogOpen || state.saving) return
        val route = plottingDraft()?.toRoute() ?: return
        _uiState.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            saveRoute(route, name, date).fold(
                onSuccess = {
                    _uiState.value = RoutePlottingUiState(expanded = _uiState.value.expanded)
                    onSaved()
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't save the route.", error)
                    _uiState.update { it.copy(saving = false, saveFailed = true) }
                },
            )
        }
    }

    /** A saved route's row or pin. Asks "Discard this route?" first while one is being plotted (Amendment 1, Q5). */
    override fun onOpenSavedRoute(trip: PlannedTrip, fromTripPlanner: Boolean) {
        if (trip.route == null) {
            errorLog.w(TAG, "Trip ${trip.id} has no route; nothing was opened.", IllegalArgumentException("no route"))
            return
        }
        if (_uiState.value.mode is RoutePlotMode.Plotting) {
            _uiState.update { it.copy(pendingDiscard = trip, pendingDiscardFromTripPlanner = fromTripPlanner) }
        } else {
            show(trip, fromTripPlanner)
        }
    }

    /** "Discard": the route being plotted is dropped and the saved one opens. */
    override fun onConfirmDiscard() {
        val trip = _uiState.value.pendingDiscard ?: return
        cancelLeg()
        show(trip, _uiState.value.pendingDiscardFromTripPlanner)
    }

    /** "Keep plotting": the prompt closes and nothing else changes. */
    override fun onKeepPlotting() {
        _uiState.update { it.copy(pendingDiscard = null) }
    }

    /** Back on a saved route's card: the card and the line close. */
    override fun onCloseSavedRoute() {
        if (_uiState.value.mode !is RoutePlotMode.Showing) return
        _uiState.value = RoutePlottingUiState(expanded = _uiState.value.expanded)
    }

    /** Opens [trip]'s route with the card peeking (the dispatch: "peeks the card"). */
    private fun show(trip: PlannedTrip, fromTripPlanner: Boolean) {
        openCount++
        _uiState.value = RoutePlottingUiState(mode = RoutePlotMode.Showing(trip, openCount, fromTripPlanner))
    }

    private var openCount = 0

    private fun plottingDraft(): RoutePlotDraft? = (_uiState.value.mode as? RoutePlotMode.Plotting)?.draft

    private fun setDraft(draft: RoutePlotDraft) {
        _uiState.update { it.copy(mode = RoutePlotMode.Plotting(draft)) }
    }

    private fun cancelLeg() {
        legJob?.cancel()
        legJob = null
        legFor = null
    }

    /** Routes the draft's next leg unless one is being routed; when it is in, routes the one after. */
    private fun routeNextLeg() {
        if (legJob != null) return
        val leg = plottingDraft()?.nextLeg ?: return
        legFor = leg
        legJob = viewModelScope.launch {
            val routed = try {
                routeLeg(leg.first.position, leg.second.position)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // PlotRouteUseCase answers every failure with a straight leg, so this is a throw it did not expect. Said plainly
                // and its stop removed (with any tapped after it), rather than a straight leg with a reason that would not be true.
                errorLog.w(TAG, "Routing a plotted leg threw; its stop and any after it were removed.", e)
                null
            }
            legJob = null
            legFor = null
            val draft = plottingDraft() ?: return@launch
            if (draft.nextLeg != leg) return@launch
            val joined = routed?.let { joinLeg(draft, it) }
            setDraft(joined ?: RoutePlotDraft(draft.stops.take(draft.legs.size + 1), draft.legs))
            routeNextLeg()
        }
    }

    /**
     * [draft] with [routed] as its next leg, or `null` when the leg does not join its two stops (Amendment 5, N-10): the draft
     * refuses it, and that is a failed leg, logged and handled as a routing throw is, never a crash. `PlotRouteUseCase.leg`
     * always joins them today, so this is a guard for a router that might not.
     */
    private fun joinLeg(draft: RoutePlotDraft, routed: RouteLeg): RoutePlotDraft? = try {
        draft.withLeg(routed)
    } catch (e: RuntimeException) {
        // A refused leg (require) or one with no points (first() on an empty line); withLeg does not suspend, so no cancellation.
        errorLog.w(TAG, "A routed leg did not join its two stops; its stop and any after it were removed.", e)
        null
    }

    private companion object {
        const val TAG = "RoutePlotting"
    }
}
