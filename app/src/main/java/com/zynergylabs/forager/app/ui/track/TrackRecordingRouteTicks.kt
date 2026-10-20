package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.nextRouteLine
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.ROUTE_TICK_MILLIS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The route tick of [TrackRecordingViewModel] (UI split Phase 2.7, PR 3, dispatch 2026-10-19-30, RECORD
 * -1439): the way-home search while a return is shown, moved out of the ViewModel unchanged. It holds the
 * tick's one job and nothing else. The ViewModel decides when it starts and stops, from the same lines as
 * before: [begin] and [end] from `copyFromWatch`, [end] from `stopReturn`, and [cancel] from
 * `clearRecordingState`, each synchronous. The tick launches in [scope], the ViewModel's own
 * `viewModelScope`, read each time rather than kept, so it gets no scope, dispatcher or lifetime of its own.
 * Every value it reads comes through a getter ([uiState], [lastPolledTrack], [lastGatedGpsFix]) and every
 * write goes through [updateState].
 */
internal class TrackRecordingRouteTicks(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val lastPolledTrack: () -> Track?,
    private val lastGatedGpsFix: () -> TrackPoint?,
    private val findRouteHome: (track: Track, current: LatLng, origin: Waypoint?, previousHopBand: HopBand) -> RouteHome,
) {

    /** The route tick, running only while returning. See [begin]. */
    private var routeJob: Job? = null

    /** Whether the tick is running: what `copyFromWatch` read as `routeJob != null`. */
    val isRunning: Boolean get() = routeJob != null

    /**
     * The route tick (dispatch 2026-09-28-423, plan task T6; decision D4, every 5 s): one route
     * search per tick, at once and then every [ROUTE_TICK_MILLIS], while this screen shows a
     * return ([TrackRecordingViewModel.copyFromWatch] starts and ends it). Its own job beside [TrackRecordingPoller.begin], not a branch
     * inside it, because the two run at different rates. Like the poll it is unbounded, so its
     * tests stop the recording inside the test body, in a `finally`
     * (`TrackRecordingViewModelTest.runRecordingTest`); stopping the recording cancels it in
     * [TrackRecordingViewModel.clearRecordingState], and [TrackRecordingViewModel.stopReturn] ends it.
     */
    fun begin() {
        routeJob?.cancel()
        routeJob = scope().launch {
            while (true) {
                updateRouteHome()
                delay(ROUTE_TICK_MILLIS)
            }
        }
    }

    /** Stops the route tick and clears its result: no return is shown, so there is no route. */
    fun end() {
        routeJob?.cancel()
        routeJob = null
        updateState { it.copy(routeHome = null, routeLine = null) }
    }

    /**
     * [TrackRecordingUiState.routeHome] from the track the poll last read and the last
     * accuracy-gated fix, while returning ([end] clears it when the return ends). Before
     * there is both a polled track and a gated fix it leaves the value as it is, `null` at the start
     * of a return, which the HUD shows as a dash rather than a failure (the planner's ruling on
     * question 3). The hop band is carried from the previous result, a withheld one included, as
     * [RouteHome.Withheld.hopBand] asks (hysteresis, see [com.zynergylabs.forager.app.domain.pathHome],
     * "The hop"); a new return starts at [HopBand.NONE].
     *
     * Runs on the tick's own coroutine, the main dispatcher, as the poll's path-home search did:
     * about 19 ms on a desktop JVM at the four-hour HIGH_ACCURACY cap (the join dispatch's
     * measurement, for the same search), now every 5 s instead of every 15 s. A device figure
     * does not exist yet; if one says that is a visible stall, moving it off the main dispatcher is
     * the recorded next step, not done here on an estimate. The gated fix, not any fix: the same
     * accuracy rule that seeds the origin.
     */
    fun updateRouteHome() {
        val state = uiState()
        if (!state.isReturning) return
        val track = lastPolledTrack() ?: return
        val current = lastGatedGpsFix() ?: return
        val previousHopBand = when (val previous = state.routeHome) {
            is RouteHome.Ahead -> previous.hopBand
            is RouteHome.Withheld -> previous.hopBand ?: HopBand.NONE
            null -> HopBand.NONE
        }
        val next = findRouteHome(track, LatLng(current.lat, current.lng), state.originWaypoint, previousHopBand)
        // Dispatch -497: the line the map draws moves with the route (nextRouteLine).
        updateState { it.copy(routeHome = next, routeLine = nextRouteLine(it.routeLine, next)) }
    }

    /**
     * Stops the tick without clearing its result: `clearRecordingState`'s own two lines, moved here
     * unchanged. That function clears the route fields itself, in its one state update.
     */
    fun cancel() {
        routeJob?.cancel()
        routeJob = null
    }
}
