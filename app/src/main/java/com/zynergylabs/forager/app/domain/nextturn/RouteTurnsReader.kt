package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Reads a route's junctions for the line it is held beside (dispatch 2026-11-06-01, Amendment 1, H4, 2b-1: "`routeTurnsFor`
 * held where each line is held (WayHomeWatch, AvailabilityWaypointRoute) with its route data folder, recomputed on each
 * re-route"). The project's own seam, so the holders depend on it and not on the route data's reader.
 *
 * Never throws but for cancellation: every failure is a [RouteTurns.Unread] saying why, so a holder never has to guess
 * what a missing answer means.
 */
fun interface RouteTurnsReader {
    /** The junctions along [wayHome], whose walker is placed on [line], from the route data in [routeData] (`null`: none known). */
    suspend fun turnsFor(wayHome: WayHome, line: WayHomeLine, routeData: File?): RouteTurns
}

/**
 * The production [RouteTurnsReader]: [routeTurnsFor] through [routeWays] (F1). A failure is warned of through [errorLog] when
 * it fires, as CLAUDE.md asks of every fallback, since the card's space shows blank for it.
 */
class RouteWaysTurnsReader(
    private val routeWays: RouteWays,
    private val errorLog: ErrorLog,
    private val nanoTime: () -> Long = System::nanoTime,
) : RouteTurnsReader {
    override suspend fun turnsFor(wayHome: WayHome, line: WayHomeLine, routeData: File?): RouteTurns {
        if (routeData == null) {
            val why = "no route data folder"
            errorLog.w(TAG, "A route's junctions were not read for the next turn: $why.", RouteTurnsLog.RouteTurnsNotRead(why, null))
            return RouteTurns.Unread(why, NOTHING_READ)
        }
        return try {
            routeTurnsFor(wayHome, line, routeWays, routeData, nanoTime).also { turns ->
                if (turns is RouteTurns.Unread) {
                    errorLog.w(TAG, "A route's junctions could not be read for the next turn: ${turns.why}.", RouteTurnsLog.RouteTurnsNotRead(turns.why, turns.cause))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, as the way home's own job (Sorrel's M-395-2): a reader's error must not end the app. The class only,
            // never the message, which can name a route square.
            val why = e.javaClass.simpleName
            errorLog.w(TAG, "A route's junctions could not be read for the next turn: $why.", RouteTurnsLog.RouteTurnsNotRead(why, e))
            RouteTurns.Unread(why, NOTHING_READ, e)
        }
    }

    private companion object {
        const val TAG = RouteTurnsLog.TAG
        val NOTHING_READ = WaysReading(millis = 0L, reads = 0, ways = 0)
    }
}
