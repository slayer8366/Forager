package com.zynergylabs.forager.app.domain.routenav

import com.zynergylabs.forager.app.domain.FixFreshness
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ageMillis
import com.zynergylabs.forager.app.domain.fixFreshness
import com.zynergylabs.forager.app.domain.hasArrived
import com.zynergylabs.forager.app.domain.mayAct
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_STRAY_METERS
import kotlin.math.hypot

/*
 * The route navigation's progress (dispatch 2026-11-03-03): the stop headed to, the distance to it, and moving on at each stop.
 * Pure: no fix is held here and nothing is decided about the screen. The rulings it follows (A4, A5):
 * - **Only the next stop counts** ("Only the next stop counts (Recommended)"): reaching a later stop early does nothing.
 * - **GPS only** ("GPS only (Recommended)"): only a real GPS fix moves you on ([com.zynergylabs.forager.app.domain.FixProvider]'s
 *   `mayAct`), within [hasArrived]'s radius, the one the waypoint's "Arrived" uses (twice the fix's accuracy, never under 15 m).
 *   And not a lost fix, as the waypoint's "Arrived" refuses one (`arrivedAtStart`).
 * - **One stop per fix** (A5, the planner's call): two stops closer than the arrival radius move on one per fix, never two on one.
 * - **The distance** ("To the next stop (Recommended)"): along the route to the next stop. More than [WAY_HOME_STRAY_METERS]
 *   (50 m) off the leg, straight-line to the next stop instead ("Keep the saved route (Recommended)"); the route is never
 *   redrawn, so, unlike the way to a waypoint (`WayToStray`), there is no re-route here.
 * - **The first stop** ("Like a waypoint (Recommended)"): the route line starts at stop 1, so the way there is the way to a
 *   waypoint's, worked out by the caller; here it is only [RouteStopDistance.ToFirstStop].
 */

/** What a [RouteNavigation]'s next stop index resolves to on its route. */
sealed interface RouteNavigationTarget {
    /**
     * Headed to [stop], the route's stop at [index] of [stopCount]. [leg] is the route's leg that ends at it, `null` for the first
     * stop, which no leg of the route reaches.
     */
    data class ToStop(val index: Int, val stop: RouteStop, val leg: RouteLeg?, val stopCount: Int) : RouteNavigationTarget

    /** Every stop has been reached: "Arrived" (A1). */
    data object Finished : RouteNavigationTarget

    /**
     * The index is past the route's end, which no navigation of this route can reach. Said, never guessed onto a stop: the caller
     * logs it and ends the navigation.
     */
    data class OutOfRange(val nextStopIndex: Int, val stopCount: Int) : RouteNavigationTarget
}

/** Resolves [nextStopIndex] on [route]. */
fun routeNavigationTarget(route: PlottedRoute, nextStopIndex: Int): RouteNavigationTarget {
    val count = route.stops.size
    return when {
        nextStopIndex < count -> RouteNavigationTarget.ToStop(nextStopIndex, route.stops[nextStopIndex], route.legs.getOrNull(nextStopIndex - 1), count)
        nextStopIndex == count -> RouteNavigationTarget.Finished
        else -> RouteNavigationTarget.OutOfRange(nextStopIndex, count)
    }
}

/** What one fix does to a route navigation. */
sealed interface RouteNavigationStep {
    /** Nothing: no stop reached, or nothing left to reach. */
    data object Stays : RouteNavigationStep

    /**
     * The stop at [reachedIndex] was reached, and the navigation moves on to [next], whose next stop is the one after it. When
     * [reachedIndex] was the last stop, [next] is finished ([routeNavigationTarget] gives [RouteNavigationTarget.Finished]).
     */
    data class Reached(val reachedIndex: Int, val next: RouteNavigation) : RouteNavigationStep
}

/**
 * What [fix] does to [navigation] on [route]. Moves on at most one stop: only the next stop is judged, so a fix that is also within
 * reach of the stop after it waits for the next fix (A5). A fix that is not GPS, or is lost, moves nothing.
 */
fun nextRouteNavigationStep(navigation: RouteNavigation, route: PlottedRoute, fix: LocationFix.Update, nowEpochMillis: Long): RouteNavigationStep {
    val target = routeNavigationTarget(route, navigation.nextStopIndex) as? RouteNavigationTarget.ToStop ?: return RouteNavigationStep.Stays
    if (!fix.provider.mayAct) return RouteNavigationStep.Stays
    if (fixFreshness(fix.ageMillis(nowEpochMillis)) == FixFreshness.LOST) return RouteNavigationStep.Stays
    val meters = GeoDistance.metersBetween(LatLng(fix.lat, fix.lng), target.stop.position)
    if (!hasArrived(meters, fix.accuracyMeters)) return RouteNavigationStep.Stays
    return RouteNavigationStep.Reached(target.index, navigation.copy(nextStopIndex = target.index + 1))
}

/** The distance the navigation shows to the next stop. */
sealed interface RouteStopDistance {
    /** Along the leg to the next stop, from the walker's nearest place on it. */
    data class AlongRoute(val meters: Double) : RouteStopDistance

    /** Straight to the next stop: the walker is more than [WAY_HOME_STRAY_METERS] off the leg. */
    data class StraightLine(val meters: Double) : RouteStopDistance

    /** The first stop, which no leg reaches: the way there is the way to a waypoint's, the caller's. */
    data object ToFirstStop : RouteStopDistance
}

/** The distance from [here] to [route]'s stop at [nextStopIndex]; `null` when there is no stop left to head to. */
fun distanceToNextStop(route: PlottedRoute, nextStopIndex: Int, here: LatLng): RouteStopDistance? {
    val target = routeNavigationTarget(route, nextStopIndex) as? RouteNavigationTarget.ToStop ?: return null
    val leg = target.leg ?: return RouteStopDistance.ToFirstStop
    val place = placeOnLine(leg.line, here)
    return if (place.offMeters > WAY_HOME_STRAY_METERS) {
        RouteStopDistance.StraightLine(GeoDistance.metersBetween(here, target.stop.position))
    } else {
        RouteStopDistance.AlongRoute(place.remainingMeters)
    }
}

/** Where a point lies against a line: how far off it, and how much of the line is left from its nearest place on it. */
internal data class LinePlace(val offMeters: Double, val remainingMeters: Double)

/**
 * The nearest place on [line] to [here], over every segment, the earliest of equals. Each segment is flattened about its own
 * start ([GeoDistance.metersPerDegree]), which is well inside this app's precision at a segment's length; what is left is the rest
 * of that segment and every segment after it, each measured as [GeoDistance.metersBetween] measures. A line that doubles back on
 * itself within one leg is placed on its nearer part, wherever the walker came from: no earlier place is kept.
 */
internal fun placeOnLine(line: List<LatLng>, here: LatLng): LinePlace {
    if (line.size < 2) return LinePlace(line.firstOrNull()?.let { GeoDistance.metersBetween(here, it) } ?: Double.POSITIVE_INFINITY, 0.0)
    val segmentMeters = DoubleArray(line.size - 1) { GeoDistance.metersBetween(line[it], line[it + 1]) }
    val after = DoubleArray(segmentMeters.size)
    for (i in segmentMeters.indices.reversed()) after[i] = if (i == segmentMeters.lastIndex) 0.0 else after[i + 1] + segmentMeters[i + 1]
    var best: LinePlace? = null
    for (i in segmentMeters.indices) {
        val a = line[i]
        val b = line[i + 1]
        val perDegree = GeoDistance.metersPerDegree(a.lat)
        val bx = (b.lng - a.lng) * perDegree.lng
        val by = (b.lat - a.lat) * perDegree.lat
        val px = (here.lng - a.lng) * perDegree.lng
        val py = (here.lat - a.lat) * perDegree.lat
        val lengthSquared = bx * bx + by * by
        val t = if (lengthSquared == 0.0) 0.0 else ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        val off = hypot(px - t * bx, py - t * by)
        if (best == null || off < best.offMeters) best = LinePlace(off, (1 - t) * segmentMeters[i] + after[i])
    }
    return checkNotNull(best)
}
