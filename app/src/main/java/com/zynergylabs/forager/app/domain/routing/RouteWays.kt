package com.zynergylabs.forager.app.domain.routing

import com.zynergylabs.forager.app.domain.model.LatLng
import java.io.File

/**
 * The ways in the stored route data near a point, every way the data holds, whether the walking profile may use it or
 * not, with its tags (way-to dispatch 2026-10-28, PR 3; the owner's go: closed ways are decoded from the route square,
 * and the profile is not edited). The street drop-off reads it to find where a private driveway comes out onto a public
 * street. The engine itself never matches a waypoint to a closed way. The one implementation is BRouter's
 * (`routing/BRouterRouteWays`).
 */
interface RouteWays {
    /**
     * The ways of [routeData] (a folder of `.rd5` squares, as [TrailRouter.route] takes) with a point within the
     * BRouter data cells a circle of [radiusMeters] round [center] touches. A way runs between two of the engine's graph
     * nodes (junctions and ends), with the shape points between them. A square with no file, or a file that cannot be
     * read, is a failure.
     */
    suspend fun near(center: LatLng, radiusMeters: Double, routeData: File): Result<List<RouteWay>>
}

/** One way between two graph nodes: its points from one end to the other, and its tags as the route data keeps them. */
data class RouteWay(val points: List<LatLng>, val tags: Map<String, String>)
