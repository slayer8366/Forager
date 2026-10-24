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
 *    track between the same two points.
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

    suspend operator fun invoke(track: Track, origin: Waypoint?): WayHomeResult = TODO("failing-first stub")

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
            is RouteDataForPoint.NotStored ->
                if (data.routeData is RegionRouteData.Damaged) {
                    Routes.Fallback(trackOnly(WayHomeNote.ROUTES_UNREADABLE, "region ${data.regionId}'s routes are damaged"))
                } else {
                    Routes.Fallback(trackOnly(WayHomeNote.NO_TRAIL_ROUTES, "region ${data.regionId} has no stored routes (${data.routeData.javaClass.simpleName})"))
                }
            is RouteDataForPoint.SquareMissing ->
                Routes.Fallback(trackOnly(WayHomeNote.NO_TRAIL_ROUTES, "region ${data.regionId}'s routes lack the start's square ${data.square}"))
        }

    private sealed interface Routes {
        class On(val regionId: Long, val folder: File, val area: GeoBoundingBox) : Routes

        class Fallback(val result: WayHomeResult) : Routes
    }

    /** The walker's own track from stored point [from] to stored point [to], along the track joined to itself, as `RouteHome` walks it. */
    private fun trackPart(stored: List<TrackPoint>, from: Int, to: Int, kind: WayHomePartKind): WayHomePart {
        // joinedTrackRoute walks from a list's last point to its first, so the list is put in that order.
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
