package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataForPoint
import com.zynergylabs.forager.app.domain.routedata.RouteDataForPoints
import com.zynergylabs.forager.app.domain.routedata.contains
import com.zynergylabs.forager.app.domain.routedata.needsAppUpdate
import com.zynergylabs.forager.app.domain.routedata.routeDataForPoints
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomePart
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind

/**
 * The way from the walker to a waypoint along trails, or why it is today's straight line (dispatch 2026-10-26-10, step 6
 * PR 2; the rulings are RECORD -1652 and -1653). Pure domain: it reads the downloaded regions and routes through the
 * engine [engines] gives, behind the interfaces Forager owns; no Android, and no fix of its own (the caller passes the
 * walker's position). Nothing calls it in production yet: PR 3 does, at Navigate and on a re-route, in the foreground
 * only (Q5).
 *
 * ## The steps
 *
 * 1. **The region:** the newest download with routes holding both ends ([routeDataForPoints]).
 * 2. **One trail route** from the walker to the waypoint. The engine matches each end to its nearest routable way within
 *    its reach (250 m for BRouter v1.7.10 with dynamic range off) and starts and ends the route there, on the trail
 *    (`domain/routing/TrailRouter`'s [com.zynergylabs.forager.app.domain.routing.TrailRoute]: "the gap from the walker to
 *    the trail is not part of it"). So the route's first point is the trail point nearest the walker and its last the
 *    trail point nearest the waypoint, and no separate snap is asked for: one engine run, not three
 *    (`BRouterTrailSnapperTest` pins that a snap is where a route from the point starts). An end with no way within
 *    reach fails for that end, which names the note (Q2).
 * 3. **The area check:** every point of the trail route inside the rectangle the region's routes were cut to.
 * 4. **The line:** a straight lead-in to the route's first point (Q1 (a)), the route, and a straight final leg from its
 *    last point to the waypoint (Q2), in [WayHome]'s three slots ([WayToWaypointResult.AlongTrails]).
 *
 * **Always worth it** (Q3 as superseded at RECORD -1653, the owner: "It's always worth it"): there is no short-walk
 * cut-off and no detour limit. A waypoint 100 m away near a trail gets the trail route like any other.
 *
 * ## The fallbacks, and the log
 *
 * Every way it falls back to the straight line names its [WayToWaypointNote] and is logged once per call, through
 * [errorLog] (CLAUDE.md: no default fallback that isn't logged when it fires), in words, never a position.
 */
class FindWayToWaypointUseCase(
    private val offlineMapRepository: OfflineMapRepository,
    private val engines: TrailEngineSource,
    private val errorLog: ErrorLog,
) {

    suspend operator fun invoke(from: LatLng, waypoint: LatLng): WayToWaypointResult {
        val regions = offlineMapRepository.listRegions().getOrElse { error ->
            return straight(WayToWaypointNote.ROUTES_UNREADABLE, "the downloaded regions could not be listed", error)
        }
        val routes = when (val data = routeDataForPoints(from, waypoint, regions)) {
            is RouteDataForPoints.Found -> data
            RouteDataForPoints.InDifferentAreas -> return straight(WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA, "no one downloaded area with routes holds both ends")
            RouteDataForPoints.NoRegionCovers -> return straight(WayToWaypointNote.NO_TRAIL_ROUTES, "no downloaded area covers either end")
            is RouteDataForPoints.NotUsable -> return notUsable(data)
        }
        val engine = engines.engine().getOrElse { error ->
            return straight(WayToWaypointNote.NO_ROUTE_FOUND, "the routing profile could not be installed", error)
        }
        val route = when (val result = engine.router.route(from, waypoint, routes.folder)) {
            is TrailRouteResult.Found -> result.route
            is TrailRouteResult.Failed -> return failedOn(result.failure)
        }
        // The engine hands on no route of under two points (`BRouterTrailRouter.foundRoute`); said plainly if one ever comes.
        if (route.points.size < 2) return straight(WayToWaypointNote.NO_ROUTE_FOUND, "the trail route has ${route.points.size} points")
        if (route.points.any { !routes.area.contains(it) }) {
            return straight(WayToWaypointNote.LEAVES_AREA, "the trail route runs outside region ${routes.regionId}'s area")
        }
        val nearWalker = route.points.first()
        val nearWaypoint = route.points.last()
        return WayToWaypointResult.AlongTrails(
            WayHome(
                trackBack = WayHomePart(WayHomePartKind.STRAIGHT, listOf(from, nearWalker), GeoDistance.metersBetween(from, nearWalker)),
                trail = WayHomePart(WayHomePartKind.TRAIL, route.points, route.lengthMetres.toDouble()),
                trackToStart = WayHomePart(WayHomePartKind.STRAIGHT, listOf(nearWaypoint, waypoint), GeoDistance.metersBetween(nearWaypoint, waypoint)),
                // The straight legs are their own parts here, so the trail part has no joints inside it.
                trailJointMeters = 0.0,
                regionId = routes.regionId,
            ),
        )
    }

    /** An end covered by a region whose routes are not usable, as the way home words the same states (`FindWayHomeUseCase.routesFor`). */
    private fun notUsable(data: RouteDataForPoints.NotUsable): WayToWaypointResult {
        val end = if (data.end == RouteEnd.START) "the walker's" else "the waypoint's"
        return when (val state = data.state) {
            is RouteDataForPoint.SquareMissing -> straight(WayToWaypointNote.NO_TRAIL_ROUTES, "region ${state.regionId}'s routes lack $end square ${state.square}")
            is RouteDataForPoint.NotStored -> when (val routes = state.routeData) {
                is RegionRouteData.Damaged -> straight(WayToWaypointNote.ROUTES_UNREADABLE, "region ${state.regionId}'s routes are damaged, at $end end")
                RegionRouteData.NotFetched -> straight(WayToWaypointNote.ROUTES_NOT_DOWNLOADED, "region ${state.regionId}'s routes were never fetched, at $end end")
                is RegionRouteData.Missing -> if (routes.reason.needsAppUpdate()) {
                    straight(WayToWaypointNote.ROUTES_NEED_APP_UPDATE, "region ${state.regionId}'s route fetch needs an app update (${routes.reason.code}), at $end end")
                } else {
                    straight(WayToWaypointNote.ROUTES_NOT_DOWNLOADED, "region ${state.regionId}'s route fetch failed (${routes.reason.code}), at $end end")
                }
                RegionRouteData.Fetching -> straight(WayToWaypointNote.ROUTES_FETCHING, "region ${state.regionId}'s routes are being fetched, at $end end")
                RegionRouteData.NoneHere -> straight(WayToWaypointNote.NO_TRAIL_ROUTES, "the server has no trail routes for region ${state.regionId}, at $end end")
                // routeDataForPoint never reports stored routes as not stored; said plainly if it ever does.
                is RegionRouteData.Stored -> straight(WayToWaypointNote.ROUTES_UNREADABLE, "region ${state.regionId}'s stored routes were reported as not stored")
            }
            // routeDataForPoints hands on only the two states above; said plainly if it ever hands on another.
            is RouteDataForPoint.Found, RouteDataForPoint.NoRegionCovers -> straight(WayToWaypointNote.ROUTES_UNREADABLE, "$end end's routes were reported unusable as ${state.javaClass.simpleName}")
        }
    }

    /** A failure from the engine, as its note: which end is far from a trail, unreadable data, or no route found. */
    private fun failedOn(failure: TrailRouteFailure): WayToWaypointResult {
        val note = when (failure) {
            is TrailRouteFailure.EndOutsideData -> if (failure.end == RouteEnd.START) WayToWaypointNote.WALKER_FAR_FROM_TRAIL else WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL
            is TrailRouteFailure.DataMissing, is TrailRouteFailure.DataCorrupt -> WayToWaypointNote.ROUTES_UNREADABLE
            is TrailRouteFailure.NoPath, is TrailRouteFailure.EngineFailed -> WayToWaypointNote.NO_ROUTE_FOUND
        }
        return straight(note, "routing from the walker to the waypoint failed: ${failure.javaClass.simpleName}")
    }

    /** The straight line, with [note]; logged once, here (see the class doc, "The fallbacks, and the log"). */
    private fun straight(note: WayToWaypointNote, reason: String, cause: Throwable? = null): WayToWaypointResult {
        errorLog.w(TAG, "The way to the waypoint is the straight line (${note.name}): $reason.", WayToWaypointFallback(reason, cause))
        return WayToWaypointResult.StraightLine(note)
    }

    /** What [errorLog] is handed for a fallback: [ErrorLog] takes a throwable, and this names the reason. */
    class WayToWaypointFallback(reason: String, cause: Throwable?) : Exception(reason, cause)

    private companion object {
        const val TAG = "FindWayToWaypoint"
    }
}
