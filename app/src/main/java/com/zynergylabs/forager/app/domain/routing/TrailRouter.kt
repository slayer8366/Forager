package com.zynergylabs.forager.app.domain.routing

import com.zynergylabs.forager.app.domain.model.LatLng
import java.io.File

/**
 * Routes along trails, offline, on routing data stored on the phone (dispatch 2026-10-23-02, trail navigation
 * step 4). The interface Forager owns for its routing engine (CLAUDE.md, Architecture): domain logic depends on
 * this, never on the engine. The one implementation is BRouter's (`routing/BRouterTrailRouter`), chosen at
 * RECORD -484.
 *
 * Nothing calls this in production yet. The way home, waypoint navigation and the stored route data for a
 * download are later steps (RECORD -1568).
 */
interface TrailRouter {
    /**
     * A route from [start] to [end] along trails, on the routing data in [routeData]: a folder of BRouter `.rd5`
     * files, the stored cut for an area, each named for its five-degree square (`W125_N45.rd5`).
     *
     * Never runs on the caller's thread: an implementation moves the work to its own background dispatcher, so
     * this is safe to call from the main thread. Never throws for a routing outcome: every failure comes back as
     * [TrailRouteResult.Failed] with its kind named.
     *
     * An end outside the data fails as [TrailRouteFailure.EndOutsideData]; it is never moved to the nearest
     * trail it can find (RECORD -1546, "Say so plainly").
     */
    suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult
}

/** What [TrailRouter.route] returns: a route, or a failure saying which kind. */
sealed interface TrailRouteResult {
    data class Found(val route: TrailRoute) : TrailRouteResult

    data class Failed(val failure: TrailRouteFailure) : TrailRouteResult
}

/**
 * A route along trails. [points] run from the trail nearest the start to the trail nearest the end; the gap from
 * the walker to the trail is not part of it (the spike, item 4: the engine starts the route on the trail).
 */
data class TrailRoute(
    val points: List<LatLng>,
    /** The engine's own length of the route, in whole metres. */
    val lengthMetres: Int,
)

/** Which end of a route a failure is about. */
enum class RouteEnd { START, END }

/** Why there is no route. Each kind is one the caller can tell the walker about differently. */
sealed interface TrailRouteFailure {
    /**
     * [end] has no trail near it in the stored data: it lies outside the area the data covers.
     *
     * [squareHasNoFile] says the end's five-degree square has no route file at all, ordinarily a square the route
     * build lacked, rather than the engine finding no way within reach (dispatch 2026-10-26-10 Amendment 2, RECORD
     * -1667: the way to a waypoint says "No trail routes here" for it). It is declared in the body, so it is not part of
     * [equals]: every reader that does not ask for it, the way home's trail-join scan and its notes among them, sees the
     * same failure as before, which is the point (the owner: the way home's scan and notes unchanged).
     */
    data class EndOutsideData(val end: RouteEnd) : TrailRouteFailure {
        var squareHasNoFile: Boolean = false
            private set

        constructor(end: RouteEnd, squareHasNoFile: Boolean) : this(end) {
            this.squareHasNoFile = squareHasNoFile
        }
    }

    /** Both ends are on trails in the data, and no path joins them. */
    data class NoPath(val detail: String) : TrailRouteFailure

    /** There is no routing data to route on: the folder is missing, or holds no `.rd5` file. */
    data class DataMissing(val detail: String) : TrailRouteFailure

    /** A routing data file can't be read as BRouter data: cut short, a checksum that doesn't match, or another format. */
    data class DataCorrupt(val detail: String) : TrailRouteFailure

    /**
     * The engine failed for a reason that is none of the above: its time or memory limit, its profile missing,
     * or an error this code does not recognise. Logged when it happens; never presented as one of the kinds
     * above.
     */
    data class EngineFailed(val detail: String) : TrailRouteFailure
}
