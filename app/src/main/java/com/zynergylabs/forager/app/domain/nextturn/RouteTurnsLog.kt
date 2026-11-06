package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** Which way a route is, for the M1 line. */
enum class RouteKind(val words: String) {
    WAY_TO_WAYPOINT("way to a waypoint"),
    WAY_HOME("way home"),
}

/**
 * The M1 info line (dispatch 2026-11-06-01, Amendment 1, B1 and H2): for every trail route the way to a waypoint or the
 * way home works out, at the first search and at every re-route, its junctions read through [routeWays] and judged, then
 * written through [info] with no position of any kind. The planner reads it on the S22, on the owner's own routes, before
 * B1's thresholds are kept.
 *
 * Nothing a user sees: the card is Phase 2b. The read runs on [scope], so the route is shown when it is found, as before,
 * and the line follows once the ways are read.
 */
class RouteTurnsLog(
    private val scope: CoroutineScope,
    private val routeWays: RouteWays,
    private val info: (tag: String, message: String) -> Unit,
    private val errorLog: ErrorLog,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    /** A trail route of [kind] was found: [wayHome], on the route data in [routeData]. */
    fun trailRouteFound(kind: RouteKind, wayHome: WayHome, routeData: File) {
    }

    companion object {
        const val TAG = "NextTurn"
    }
}
