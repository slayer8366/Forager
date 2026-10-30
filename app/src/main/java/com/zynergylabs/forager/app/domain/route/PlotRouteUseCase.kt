package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
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

/**
 * Plots a route between waypoints (dispatch 2026-10-30-02, step 2; the plan is
 * docs/plans/2026-10-29-waypoint-route-plotting-plan.md). Pure domain, behind the interfaces Forager owns: it reads the
 * downloaded regions and routes through the engine [engines] gives; no Android. **Unwired** until step 3 gives it a
 * caller (Amendment 1, Q8).
 *
 * Each leg is routed as the way to a waypoint routes (`FindWayToWaypointUseCase`), one after another, never in parallel:
 * the engine runs one route at a time in the whole process (`routing/BRouterTrailRouter.kt:199-201`).
 * 1. **The region:** the newest download with routes holding both of the leg's stops ([routeDataForPoints]).
 * 2. **One trail route** between them. The engine starts and ends it on the trail nearest each stop.
 * 3. **The area check:** every point of it inside the rectangle that region's routes were cut to.
 * 4. **The leg:** stop → trail → stop. The joins from each stop to its nearest trail point are straight and count in the
 *    leg's metres; [RouteLeg.Trail.trailFrom] and [RouteLeg.Trail.trailTo] keep them apart from the trail part, so the
 *    drawing can dash them (Amendment 1, the owner: "Dashed link to trail (Recommended)").
 *
 * Any link that fails gives a straight leg between the two stops, with its [StraightReason], **logged once per leg**
 * through [errorLog] (CLAUDE.md: no fallback that isn't logged), in words, never a position.
 *
 * Two consecutive stops at the same position are refused (the owner: "Ignore the second tap (Recommended)"; step 3
 * ignores the tap before calling). A → B → A is allowed.
 */
class PlotRouteUseCase(
    private val offlineMapRepository: OfflineMapRepository,
    private val engines: TrailEngineSource,
    private val errorLog: ErrorLog,
) {

    /**
     * The whole route through [stops], in order: the regions are listed once, then each leg is routed in turn.
     * Throws [IllegalArgumentException] for fewer than two stops or two consecutive stops at one position.
     */
    suspend operator fun invoke(stops: List<RouteStop>): PlottedRoute {
        require(stops.size >= 2) { "a route has at least two stops; got ${stops.size}" }
        stops.zipWithNext().forEachIndexed { i, (a, b) -> requireDistinct(a.position, b.position, i) }
        val regions = listRegions()
        val legs = stops.zipWithNext().mapIndexed { i, (a, b) -> legOn(regions, a.position, b.position, i) }
        return PlottedRoute(stops, legs)
    }

    /**
     * One leg, from [from] to [to], for adding a leg at a time (step 3 adds one per tap, and Undo removes one). Lists the
     * regions itself. Throws [IllegalArgumentException] when the two are at the same position.
     */
    suspend fun leg(from: LatLng, to: LatLng): RouteLeg {
        requireDistinct(from, to, 0)
        return legOn(listRegions(), from, to, 0)
    }

    private fun requireDistinct(a: LatLng, b: LatLng, i: Int) =
        require(a != b) { "stops $i and ${i + 1} are at the same position" }

    /** The regions, or the failure, which every leg then reports as unreadable routes. */
    private suspend fun listRegions(): Result<List<OfflineRegionSummary>> = offlineMapRepository.listRegions()

    private suspend fun legOn(regions: Result<List<OfflineRegionSummary>>, from: LatLng, to: LatLng, index: Int): RouteLeg {
        fun straight(reason: StraightReason, why: String, cause: Throwable? = null): RouteLeg {
            errorLog.w(TAG, "Plotted leg $index is a straight line (${reason.name}): $why.", PlottedLegFallback(why, cause))
            return RouteLeg.Straight(listOf(from, to), GeoDistance.metersBetween(from, to), reason)
        }
        val listed = regions.getOrElse { error -> return straight(StraightReason.ROUTES_UNREADABLE, "the downloaded regions could not be listed", error) }
        val routes = when (val data = routeDataForPoints(from, to, listed)) {
            is RouteDataForPoints.Found -> data
            RouteDataForPoints.InDifferentAreas -> return straight(StraightReason.IN_DIFFERENT_AREAS, "no one downloaded area with routes holds both stops")
            RouteDataForPoints.NoRegionCovers -> return straight(StraightReason.NO_REGION_COVERS, "no downloaded area covers either stop")
            is RouteDataForPoints.NotUsable -> {
                val (reason, why) = notUsable(data.state)
                return straight(reason, why)
            }
        }
        val engine = engines.engine().getOrElse { error ->
            return straight(StraightReason.NO_ROUTE_FOUND, "the routing profile could not be installed", error)
        }
        val route = when (val result = engine.router.route(from, to, routes.folder)) {
            is TrailRouteResult.Found -> result.route
            is TrailRouteResult.Failed -> return straight(failedOn(result.failure), "routing between the stops failed: ${describe(result.failure)}")
        }
        // The engine hands on no route of under two points (`BRouterTrailRouter.foundRoute`); said plainly if one ever comes.
        if (route.points.size < 2) return straight(StraightReason.NO_ROUTE_FOUND, "the trail route has ${route.points.size} points")
        if (route.points.any { !routes.area.contains(it) }) {
            return straight(StraightReason.LEAVES_AREA, "the trail route runs outside region ${routes.regionId}'s area")
        }
        val line = listOf(from) + route.points + listOf(to)
        val meters = GeoDistance.metersBetween(from, route.points.first()) + route.lengthMetres + GeoDistance.metersBetween(route.points.last(), to)
        return RouteLeg.Trail(line, meters, trailFrom = 1, trailTo = route.points.size)
    }

    /** A region holding both stops whose routes are not usable, as the way to a waypoint says the same states. */
    private fun notUsable(state: RouteDataForPoint): Pair<StraightReason, String> = when (state) {
        is RouteDataForPoint.SquareMissing -> StraightReason.NO_TRAIL_ROUTES to "region ${state.regionId}'s routes lack square ${state.square}"
        is RouteDataForPoint.NotStored -> when (val routes = state.routeData) {
            is RegionRouteData.Damaged -> StraightReason.ROUTES_UNREADABLE to "region ${state.regionId}'s routes are damaged"
            RegionRouteData.NotFetched -> StraightReason.ROUTES_NOT_DOWNLOADED to "region ${state.regionId}'s routes were never fetched"
            is RegionRouteData.Missing -> if (routes.reason.needsAppUpdate()) {
                StraightReason.ROUTES_NEED_APP_UPDATE to "region ${state.regionId}'s route fetch needs an app update (${routes.reason.code})"
            } else {
                StraightReason.ROUTES_NOT_DOWNLOADED to "region ${state.regionId}'s route fetch failed (${routes.reason.code})"
            }
            RegionRouteData.Fetching -> StraightReason.ROUTES_FETCHING to "region ${state.regionId}'s routes are being fetched"
            RegionRouteData.NoneHere -> StraightReason.NO_TRAIL_ROUTES to "the server has no trail routes for region ${state.regionId}"
            // routeDataForPoints never reports stored routes as not stored; said plainly if it ever does.
            is RegionRouteData.Stored -> StraightReason.ROUTES_UNREADABLE to "region ${state.regionId}'s stored routes were reported as not stored"
        }
        // routeDataForPoints hands on only the two states above; said plainly if it ever hands on another.
        is RouteDataForPoint.Found, RouteDataForPoint.NoRegionCovers -> StraightReason.ROUTES_UNREADABLE to "the routes were reported unusable as ${state.javaClass.simpleName}"
    }

    /** An engine failure as its reason: which stop is far from a trail, a square with no route file, unreadable data, or no route. */
    private fun failedOn(failure: TrailRouteFailure): StraightReason = when (failure) {
        // A square with no route file is a gap in the route build, not distance from a trail (RECORD -1667).
        is TrailRouteFailure.EndOutsideData -> when {
            failure.squareHasNoFile -> StraightReason.NO_TRAIL_ROUTES
            failure.end == RouteEnd.START -> StraightReason.START_FAR_FROM_TRAIL
            else -> StraightReason.END_FAR_FROM_TRAIL
        }
        is TrailRouteFailure.DataMissing, is TrailRouteFailure.DataCorrupt -> StraightReason.ROUTES_UNREADABLE
        is TrailRouteFailure.NoPath, is TrailRouteFailure.EngineFailed -> StraightReason.NO_ROUTE_FOUND
    }

    private fun describe(failure: TrailRouteFailure): String {
        val noFile = if (failure is TrailRouteFailure.EndOutsideData && failure.squareHasNoFile) " (the ${failure.end} stop's square has no route file)" else ""
        return failure.javaClass.simpleName + noFile
    }

    /** What [errorLog] is handed for a fallback: [ErrorLog] takes a throwable, and this names the reason. */
    class PlottedLegFallback(reason: String, cause: Throwable?) : Exception(reason, cause)

    private companion object {
        const val TAG = "PlotRoute"
    }
}
