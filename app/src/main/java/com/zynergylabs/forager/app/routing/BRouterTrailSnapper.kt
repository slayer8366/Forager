package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import com.zynergylabs.forager.app.domain.routing.TrailSnapper
import java.io.File

/**
 * [TrailSnapper] on BRouter v1.7.10 (dispatch 2026-10-23-09 Amendment 1, part 1), both [TrailSnapMethod]s, through
 * [BRouterTrailRouter]'s own engine run: the same request, the same one-route lock, the same failure names and the
 * same cancel (#382 M1). Which method ships is the owner's "Measure, then pick" (RECORD -1589), timed on the S22.
 *
 * **Why a route from a point to itself is a snap.** BRouter v1.7.10's own single-point modes (`GETELEV`, `GETINFO`)
 * keep the matched point in fields this code cannot read (`OsmTrack.matchedWaypoints` is protected; `doGetInfo` keeps
 * its track local). A routing request whose two ends are the same point does what is wanted through the public
 * result: the engine matches the point to its nearest way within its catching range and returns that one matched
 * point, with no error. Seen on the Harriman fixture before this was built: a point 34 m from a trail came back as the
 * point on the trail 34 m away, the same point a full route from there starts at, in about 4 ms on a laptop JVM against
 * 37 ms for that full route (warm; one run each, not a measurement). `BRouterTrailSnapperTest` pins both.
 */
class BRouterTrailSnapper(
    private val router: BRouterTrailRouter,
    private val method: TrailSnapMethod,
) : TrailSnapper {

    override suspend fun nearestTrail(point: LatLng, toward: LatLng, routeData: File): TrailSnapResult =
        when (method) {
            TrailSnapMethod.SNAP -> router.firstRoutePoint(point, point, routeData)
            TrailSnapMethod.FULL_ROUTE -> router.firstRoutePoint(point, toward, routeData)
        }
}
