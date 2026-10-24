package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.contains
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import com.zynergylabs.forager.app.domain.routing.TrailSnapper
import java.io.File

/**
 * Where a track meets the trails (dispatch 2026-10-23-09 report, section 2; Amendment 1, part 1). The engine can't say
 * this by itself: with dynamic range off it still matches a point to any way within 250 m, so handing it the walker's
 * position would route from a point the walker never stood on, joined to them by a straight line through the forest,
 * the shortcut RECORD -1498 rules out. So the points are the walker's own: the track is scanned for the first point,
 * in the order given, within [WAY_HOME_TRAIL_JOIN_METERS] of a trail.
 *
 * - **The entry point**, where the walker first joined a trail: scanned oldest first, from the track's first point.
 * - **The leave point**, where they last left one: scanned newest first, from the most recent stored point.
 *
 * A scan is not a search for a crossing: being on or off a trail is not monotonic along a walk, and what is wanted is
 * the first time, or the last time, the walker was on one.
 */
internal data class TrailJoin(
    /** The track point's index in the track. */
    val index: Int,
    val trackPoint: LatLng,
    /** Where the engine matched [trackPoint] to the trail, at most [WAY_HOME_TRAIL_JOIN_METERS] from it. */
    val trailPoint: LatLng,
)

/** What a scan for a [TrailJoin] found. */
internal sealed interface JoinScan {
    data class Found(val join: TrailJoin) : JoinScan

    /** No point the scan tested is near a trail. */
    data object NoneNear : JoinScan

    /** The scan could not go on: the data or the engine failed, so further points would fail the same way. */
    data class Stopped(val failure: TrailRouteFailure) : JoinScan
}

/**
 * The indices of [points] a scan tests, in [order]: its first index, then each one at least [spacingMeters] along the
 * track from the last one taken ([WAY_HOME_SCAN_SPACING_METERS]). [order] runs one step at a time, up or down.
 */
internal fun scanIndices(points: List<LatLng>, order: IntProgression, spacingMeters: Double = WAY_HOME_SCAN_SPACING_METERS): List<Int> {
    val taken = ArrayList<Int>()
    var sinceTaken = 0.0
    var previous: Int? = null
    for (i in order) {
        if (previous != null) sinceTaken += GeoDistance.metersBetween(points[previous], points[i])
        if (taken.isEmpty() || sinceTaken >= spacingMeters) {
            taken += i
            sinceTaken = 0.0
        }
        previous = i
    }
    return taken
}

/**
 * The first of [indices] whose point is within [WAY_HOME_TRAIL_JOIN_METERS] of a trail, on [routeData] by [snapper].
 * A point outside [area], the rectangle the region's routes were cut to, is not tested: a trail found there would be
 * outside the downloaded area. [toward] is handed to [snapper] for its full-route method.
 *
 * A point the engine finds no way near ([TrailRouteFailure.EndOutsideData] for the point's own end), or, for a full
 * route, a point no path joins to [toward] ([TrailRouteFailure.NoPath]), is not near a trail, and the scan goes on.
 * Every other failure stops it ([JoinScan.Stopped]): missing or corrupt data, an engine failure, or [toward] itself
 * off the network, would fail the same way for the next point.
 */
internal suspend fun findTrailJoin(
    points: List<LatLng>,
    indices: List<Int>,
    toward: LatLng,
    area: GeoBoundingBox,
    routeData: File,
    snapper: TrailSnapper,
): JoinScan {
    for (i in indices) {
        val point = points[i]
        if (!area.contains(point)) continue
        when (val result = snapper.nearestTrail(point, toward, routeData)) {
            is TrailSnapResult.OnNetwork ->
                if (GeoDistance.metersBetween(point, result.trailPoint) <= WAY_HOME_TRAIL_JOIN_METERS) {
                    return JoinScan.Found(TrailJoin(i, point, result.trailPoint))
                }
            is TrailSnapResult.Failed -> when (val failure = result.failure) {
                is TrailRouteFailure.EndOutsideData -> if (failure.end == RouteEnd.END) return JoinScan.Stopped(failure)
                is TrailRouteFailure.NoPath -> Unit
                is TrailRouteFailure.DataMissing, is TrailRouteFailure.DataCorrupt, is TrailRouteFailure.EngineFailed ->
                    return JoinScan.Stopped(failure)
            }
        }
    }
    return JoinScan.NoneNear
}
