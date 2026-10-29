package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import com.zynergylabs.forager.app.domain.wayhome.FakeSnapper
import com.zynergylabs.forager.app.domain.wayhome.Ground
import java.io.File
import kotlin.math.hypot

/**
 * A router over trails drawn as straight lines on [Ground] (dispatch 2026-10-26-10, step 6 PR 2), doing what the step's
 * use case relies on the engine for: each end is matched to its foot on the nearest trail within 250 m (BRouter's
 * catching range with dynamic range off), an end with none fails as outside the data for that end, and the route runs
 * from the start's foot to the end's foot, through [via] when it is set. [failWith] makes every call fail.
 */
internal class FakeTrailRouter(
    private val trails: List<Pair<LatLng, LatLng>>,
    var via: List<LatLng> = emptyList(),
    var failWith: TrailRouteFailure? = null,
) : TrailRouter {
    val asked = mutableListOf<Triple<LatLng, LatLng, File>>()

    override suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult {
        asked += Triple(start, end, routeData)
        failWith?.let { return TrailRouteResult.Failed(it) }
        val from = nearestFoot(start) ?: return TrailRouteResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.START))
        val to = nearestFoot(end) ?: return TrailRouteResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.END))
        val points = listOf(from) + via + to
        val metres = points.zipWithNext { a, b -> GeoDistance.metersBetween(a, b) }.sum()
        return TrailRouteResult.Found(TrailRoute(points, Math.round(metres).toInt()))
    }

    private fun nearestFoot(point: LatLng): LatLng? =
        trails.map { (a, b) -> foot(point, a, b) }
            .minByOrNull { GeoDistance.metersBetween(point, it) }
            ?.takeIf { GeoDistance.metersBetween(point, it) <= REACH_METERS }

    private fun foot(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val (ax, ay) = Ground.east(a) to Ground.north(a)
        val (bx, by) = Ground.east(b) to Ground.north(b)
        val (px, py) = Ground.east(p) to Ground.north(p)
        val dx = bx - ax
        val dy = by - ay
        val t = if (hypot(dx, dy) == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return Ground.at(ax + t * dx, ay + t * dy)
    }

    companion object {
        const val REACH_METERS = 250.0
    }
}

/** An engine source handing out [router] (and an unused snapper), or [failWith] when set; counts the asks. */
internal class FakeEngines(private val router: TrailRouter, var failWith: Exception? = null) : TrailEngineSource {
    var asked = 0

    override suspend fun engine(): Result<TrailEngine> {
        asked++
        failWith?.let { return Result.failure(it) }
        return Result.success(TrailEngine(router, FakeSnapper(emptyList())))
    }
}

/**
 * Ends that keep every route as the engine found it (way-to dispatch 2026-10-28, PR 5): for the tests of the use case's
 * steps before the ends, on ground with no map data. The ends' own tests use the real [StreetDropOffEnds].
 */
internal object AcceptEnds : WayToEnds {
    override suspend fun settle(
        walker: LatLng,
        waypoint: LatLng,
        route: TrailRoute,
        routeData: File,
        reroute: suspend (LatLng) -> TrailRouteResult,
    ): WayToEnds.Settled = WayToEnds.Settled.Route(route)
}
