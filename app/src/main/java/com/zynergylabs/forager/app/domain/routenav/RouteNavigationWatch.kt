package com.zynergylabs.forager.app.domain.routenav

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The stop-checking of a route navigation (dispatch 2026-11-03-03, A8; the owner: "Use the same method as off track
 * notifications"). Held by the app's container, as `ReturnWatch` is, so it outlives the Activity; fed **every raw fix** by
 * `RouteNavigationService`, the background location service Navigate starts, so a stop is reached with the screen off; read by
 * the ViewModel ([state]), which never moves a stop itself, so the screen and the background cannot disagree.
 *
 * On each fix it asks [nextRouteNavigationStep] (only the next stop, GPS only, one stop per fix). A stop reached:
 * - [state] moves on, with [RouteNavigationWatchState.lastReached] for the navigation's "Reached <name>";
 * - the route's progress is kept ([RouteNavigationRepository.setProgress]), or cleared once the last stop is reached (A8: "cleared
 *   when the route is finished");
 * - [signal] gives the short buzz (A4, "A short buzz plus words"), which obeys silent mode (A8).
 * A failed write is logged; the navigation on screen carries on regardless, as the waypoint's does.
 *
 * Calls come from the service's collector and from the main thread, so every change is made under [lock].
 */
class RouteNavigationWatch(
    private val repository: RouteNavigationRepository,
    private val signal: StopReachedSignal,
    private val scope: CoroutineScope,
    private val errorLog: ErrorLog,
) {
    private val lock = Any()
    private val _state = MutableStateFlow(RouteNavigationWatchState())
    val state: StateFlow<RouteNavigationWatchState> = _state.asStateFlow()

    /** Begins watching [navigation] on [route], the trip named [tripName]. Replaces any navigation already watched. */
    fun begin(navigation: RouteNavigation, tripName: String, route: PlottedRoute) = synchronized(lock) {
        _state.value = RouteNavigationWatchState(navigation, tripName, route)
    }

    /** Ends the watch. What each route has kept stays kept (A8: a route's next stop survives Stop). */
    fun end() = synchronized(lock) {
        _state.value = RouteNavigationWatchState()
    }

    /** One raw fix, at [nowEpochMillis]. Nothing is watched, or nothing is left to reach: nothing happens. */
    fun onFix(fix: LocationFix.Update, nowEpochMillis: Long) {
        val reached = synchronized(lock) {
            val current = _state.value
            val navigation = current.navigation ?: return
            val route = current.route ?: return
            val step = nextRouteNavigationStep(navigation, route, fix, nowEpochMillis) as? RouteNavigationStep.Reached ?: return
            val stop = route.stops[step.reachedIndex]
            _state.value = current.copy(navigation = step.next, lastReached = ReachedStop(step.reachedIndex, stop.name, nowEpochMillis))
            step.next to (routeNavigationTarget(route, step.next.nextStopIndex) is RouteNavigationTarget.Finished)
        }
        val (next, finished) = reached
        scope.launch {
            repository.setProgress(next.tripId, if (finished) null else next.nextStopIndex).onFailure { error ->
                errorLog.w(TAG, "Couldn't keep route '${next.tripId}''s next stop; Navigate will not carry on from it.", error)
            }
        }
        try {
            signal.signal()
        } catch (e: Exception) {
            errorLog.w(TAG, "The stop-reached vibration could not be issued.", e)
        }
    }

    private companion object {
        const val TAG = "RouteNavigationWatch"
    }
}

/** What [RouteNavigationWatch] is watching: nothing ([navigation] `null`), or a route and the stop it is headed to. */
data class RouteNavigationWatchState(
    val navigation: RouteNavigation? = null,
    val tripName: String? = null,
    val route: PlottedRoute? = null,
    /** The stop reached last in this navigation, `null` before the first. */
    val lastReached: ReachedStop? = null,
)

/** A stop reached: its index and name, and when. */
data class ReachedStop(val index: Int, val name: String, val atEpochMillis: Long)

/**
 * The short buzz at a stop (A4: "the phone gives one short vibration"). An owned seam, as `AlertDelivery` is: the Android one is
 * `AndroidStopReachedSignal`. It obeys silent mode (A8, `overridesSilence = false`).
 */
fun interface StopReachedSignal {
    fun signal()
}
