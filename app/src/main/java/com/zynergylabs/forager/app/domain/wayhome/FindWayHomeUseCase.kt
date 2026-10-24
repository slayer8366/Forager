package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.joinedTrackRoute
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataForPoint
import com.zynergylabs.forager.app.domain.routedata.contains
import com.zynergylabs.forager.app.domain.routedata.needsAppUpdate
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import com.zynergylabs.forager.app.domain.routedata.routeDataForPoint
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import com.zynergylabs.forager.app.domain.routing.TrailSnapper
import java.io.File

/**
 * The way home along trails, or why it is the walker's track (dispatch 2026-10-23-09, plan step 5; Amendment 1, part
 * 1; the rulings are RECORD -1589's). Pure domain: it reads the downloaded regions, tests track points through
 * [TrailSnapper] and routes through [TrailRouter], the interfaces Forager owns; no Android. Nothing calls it in
 * production yet (part 2 does, once at Return and again if the walker strays, RECORD -1589).
 *
 * ## The steps
 *
 * 1. **The region.** The trail route ends where the walker started, so it is routed on the routes of the downloaded
 *    region covering the track's first point ([routeDataForPoint]: the newest download with routes for its square).
 * 2. **The entry point**, where the walker first joined a trail, and **the leave point**, where they last left one
 *    ([findTrailJoin], at [WAY_HOME_TRAIL_JOIN_METERS]).
 * 3. **The trail route** from the leave point to the entry point.
 * 4. **The area checks** (the owner: "Edge + length check"): every point of the trail route inside the rectangle the
 *    region's routes were cut to, and the trail route no longer than [WAY_HOME_DETOUR_RATIO] times the walker's own
 *    track between the same two points, checked only when that track is over [WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS]
 *    (RECORD -1600, option (b)).
 * 5. **The line**: the walker's track back to the leave point, the trail route, and the walker's track from the entry
 *    point to the first stored point, then the origin waypoint (RECORD -1589, "Back on your track").
 *
 * ## The fallbacks, and the log
 *
 * Every way it falls back to the track says which [WayHomeNote] goes with it, and is logged once per call, through
 * [errorLog] (CLAUDE.md: no default fallback that isn't logged when it fires). The log names the note and the reason
 * in words, never a position. The router logs its own engine failures besides.
 */
class FindWayHomeUseCase(
    private val offlineMapRepository: OfflineMapRepository,
    private val router: TrailRouter,
    private val snapper: TrailSnapper,
    private val errorLog: ErrorLog,
) {

    suspend operator fun invoke(track: Track, origin: Waypoint?): WayHomeResult {
        val stored = track.points
        if (stored.size < 2) return trackOnly(null, "the track has ${stored.size} stored points")
        val regions = offlineMapRepository.listRegions().getOrElse { error ->
            return trackOnly(WayHomeNote.ROUTES_UNREADABLE, "the downloaded regions could not be listed", error)
        }
        val points = stored.map { LatLng(it.lat, it.lng) }
        val routes = when (val found = routesFor(points, regions)) {
            is Routes.Fallback -> return found.result
            is Routes.On -> found
        }

        val entry = when (val scan = findTrailJoin(points, scanIndices(points, 0 until points.lastIndex), points.last(), routes.area, routes.folder, snapper)) {
            is JoinScan.Found -> scan.join
            JoinScan.NoneNear -> return trackOnly(WayHomeNote.NEVER_NEAR_TRAIL, "no tested track point is within $WAY_HOME_TRAIL_JOIN_METERS m of a trail")
            is JoinScan.Stopped -> return failedOn(scan.failure, "finding where the track joined a trail")
        }
        val leave = when (val scan = findTrailJoin(points, scanIndices(points, points.lastIndex downTo entry.index + 1), entry.trackPoint, routes.area, routes.folder, snapper)) {
            is JoinScan.Found -> scan.join
            // The track touches a trail only where it first joined one: the track itself is the way home.
            JoinScan.NoneNear -> return trackOnly(null, "the track is near a trail only where it first joined one")
            is JoinScan.Stopped -> return failedOn(scan.failure, "finding where the track left a trail")
        }

        val route = when (val result = router.route(leave.trackPoint, entry.trackPoint, routes.folder)) {
            is TrailRouteResult.Found -> result.route
            is TrailRouteResult.Failed -> return failedOn(result.failure, "routing from the leave point to the entry point")
        }
        val trailPoints = listOf(leave.trackPoint) + route.points + entry.trackPoint
        if (trailPoints.any { !routes.area.contains(it) }) {
            return trackOnly(WayHomeNote.LEAVES_AREA, "the trail route runs outside region ${routes.regionId}'s area")
        }
        val joints = GeoDistance.metersBetween(leave.trackPoint, route.points.first()) + GeoDistance.metersBetween(route.points.last(), entry.trackPoint)
        val trailMeters = route.lengthMetres + joints
        val trackBetween = trackPart(stored, from = leave.index, to = entry.index, kind = WayHomePartKind.TRACK_BACK).meters
        // RECORD -1600, the owner: "Switch to (b)": the length check only runs when the walker went over
        // WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS between the two trail points. Under that, a trail only crossed or walked
        // beside can trip it on its joints alone and falsely say the route leaves the area (Sorrel's review of #389,
        // M-389-2), so the route is used as found.
        if (true && trailMeters > WAY_HOME_DETOUR_RATIO * trackBetween) {
            return trackOnly(
                WayHomeNote.LEAVES_AREA,
                "the trail route, ${trailMeters.toInt()} m, is more than $WAY_HOME_DETOUR_RATIO times the track between the same points, ${trackBetween.toInt()} m",
            )
        }

        val toStart = trackPart(stored, from = entry.index, to = 0, kind = WayHomePartKind.TRACK_TO_START)
        val originLeg = origin?.let { LatLng(it.lat, it.lng) }
        return WayHomeResult.AlongTrails(
            WayHome(
                trackBack = trackPart(stored, from = points.lastIndex, to = leave.index, kind = WayHomePartKind.TRACK_BACK),
                trail = WayHomePart(WayHomePartKind.TRAIL, trailPoints, trailMeters),
                trackToStart = if (originLeg == null) {
                    toStart
                } else {
                    toStart.copy(points = toStart.points + originLeg, meters = toStart.meters + GeoDistance.metersBetween(points.first(), originLeg))
                },
                trailJointMeters = joints,
                regionId = routes.regionId,
            ),
        )
    }

    /** Which region's routes the way home is routed on, or the fallback when there are none to route on. See step 1. */
    private fun routesFor(points: List<LatLng>, regions: List<OfflineRegionSummary>): Routes =
        when (val data = routeDataForPoint(points.first(), regions)) {
            is RouteDataForPoint.Found -> {
                val region = regions.first { it.id == data.regionId }
                Routes.On(data.regionId, data.folder, routeAreaFor(region.region))
            }
            RouteDataForPoint.NoRegionCovers ->
                if (routeDataForPoint(points.last(), regions) is RouteDataForPoint.Found) {
                    Routes.Fallback(trackOnly(WayHomeNote.LEAVES_AREA, "the start is outside every downloaded area with routes, and the walker is inside one"))
                } else {
                    Routes.Fallback(trackOnly(WayHomeNote.NO_TRAIL_ROUTES, "no downloaded area covers the start"))
                }
            is RouteDataForPoint.NotStored -> Routes.Fallback(
                when (val state = data.routeData) {
                    is RegionRouteData.Damaged -> trackOnly(WayHomeNote.ROUTES_UNREADABLE, "region ${data.regionId}'s routes are damaged")
                    // RECORD -1592, "Two honest notes": routes not on the phone are never "no trail routes here".
                    RegionRouteData.NotFetched -> trackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED, "region ${data.regionId}'s routes were never fetched")
                    // RECORD -1597: as the Offline maps row says, only an app update fixes these two; every other failed fetch is "not downloaded" (-1595).
                    is RegionRouteData.Missing -> if (state.reason.needsAppUpdate()) {
                        trackOnly(WayHomeNote.ROUTES_NEED_APP_UPDATE, "region ${data.regionId}'s route fetch needs an app update (${state.reason.code})")
                    } else {
                        trackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED, "region ${data.regionId}'s route fetch failed (${state.reason.code})")
                    }
                    RegionRouteData.Fetching -> trackOnly(WayHomeNote.ROUTES_FETCHING, "region ${data.regionId}'s routes are being fetched")
                    RegionRouteData.NoneHere -> trackOnly(WayHomeNote.NO_TRAIL_ROUTES, "the server has no trail routes for region ${data.regionId}")
                    // routeDataForPoint never reports stored routes as not stored; said plainly if it ever does.
                    is RegionRouteData.Stored -> trackOnly(WayHomeNote.ROUTES_UNREADABLE, "region ${data.regionId}'s stored routes were reported as not stored")
                },
            )
            is RouteDataForPoint.SquareMissing ->
                Routes.Fallback(trackOnly(WayHomeNote.NO_TRAIL_ROUTES, "region ${data.regionId}'s routes lack the start's square ${data.square}"))
        }

    private sealed interface Routes {
        class On(val regionId: Long, val folder: File, val area: GeoBoundingBox) : Routes

        class Fallback(val result: WayHomeResult) : Routes
    }

    /** The walker's own track from stored point [from] to stored point [to], along the track joined to itself, as `RouteHome` walks it. */
    private fun trackPart(stored: List<TrackPoint>, from: Int, to: Int, kind: WayHomePartKind): WayHomePart {
        // joinedTrackRoute walks from a list's last point to its first, so the list is put in that order. Every caller
        // passes from >= to today (the leave index is above the entry index, and the track to the start runs down to 0),
        // so the reversed branch is not reached; it is kept so the function means what its name says (L-389-8).
        val section = if (from >= to) stored.subList(to, from + 1) else stored.subList(from, to + 1).asReversed()
        val joined = joinedTrackRoute(section)
        return WayHomePart(kind, joined.route.map { LatLng(section[it].lat, section[it].lng) }, joined.meters)
    }

    /** A failure from the snapper or the router, as its note: unreadable data, or no route found. */
    private fun failedOn(failure: TrailRouteFailure, step: String): WayHomeResult {
        val note = when (failure) {
            is TrailRouteFailure.DataMissing, is TrailRouteFailure.DataCorrupt -> WayHomeNote.ROUTES_UNREADABLE
            is TrailRouteFailure.EndOutsideData, is TrailRouteFailure.NoPath, is TrailRouteFailure.EngineFailed -> WayHomeNote.NO_ROUTE_FOUND
        }
        return trackOnly(note, "$step failed: ${failure.javaClass.simpleName}")
    }

    /** The track is the way home, with [note]; logged once, here (see the class doc, "The fallbacks, and the log"). */
    private fun trackOnly(note: WayHomeNote?, reason: String, cause: Throwable? = null): WayHomeResult {
        errorLog.w(TAG, "The way home follows the track (${note?.name ?: "no note"}): $reason.", WayHomeFallback(reason, cause))
        return WayHomeResult.TrackOnly(note)
    }

    /** What [errorLog] is handed for a fallback: [ErrorLog] takes a throwable, and this names the reason. */
    class WayHomeFallback(reason: String, cause: Throwable?) : Exception(reason, cause)

    private companion object {
        const val TAG = "FindWayHome"
    }
}
