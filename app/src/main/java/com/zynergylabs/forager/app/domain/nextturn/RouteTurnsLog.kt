package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
    /**
     * A trail route of [kind] was found: [wayHome], on the route data in [routeData]. Returns at once; the ways are read and
     * the line written on [scope]. A read that fails is logged as unread and warned of; a reader that throws is warned of,
     * and nothing is logged at info level for that route.
     */
    fun trailRouteFound(kind: RouteKind, wayHome: WayHome, routeData: File) {
        scope.launch {
            try {
                // The walker's line, as the way home and the way to a waypoint build it: its metres along are the same
                // either way (the trail allowance moves only the figure), and they are what the line's distances are.
                val turns = routeTurnsFor(wayHome, WayHomeLine(wayHome), routeWays, routeData, nanoTime)
                if (turns is RouteTurns.Unread) {
                    errorLog.w(TAG, "The ${kind.words}'s junctions could not be read for the next turn: ${turns.why}.", RouteTurnsNotRead(turns.why, turns.cause))
                }
                routeTurnsLogLines(kind, turns).forEach { info(TAG, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable, as the way home's own job (Sorrel's M-395-2): a reader's error must not end the app.
                errorLog.w(TAG, "The ${kind.words}'s next-turn line was not written: ${e.javaClass.simpleName}.", RouteTurnsNotRead(e.javaClass.simpleName, e))
            }
        }
    }

    /** What [errorLog] is handed: [ErrorLog] takes a throwable, and this names the step with the failure as its cause. */
    class RouteTurnsNotRead(reason: String, cause: Throwable?) : Exception(reason, cause)

    companion object {
        const val TAG = "NextTurn"
    }
}
