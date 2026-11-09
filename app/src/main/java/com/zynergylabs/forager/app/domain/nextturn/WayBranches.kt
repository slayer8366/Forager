package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * A point as the route data stores it, to the micro-degree. The engine's route points and the data's way points are both
 * whole micro-degrees (`BRouterTrailRouter.latLngOf`, `BRouterRouteWays.positionOf`), so one point of the data has one
 * key, whichever reader handed it on. Matching is by key, never by comparing doubles.
 */
internal data class PointKey(val lat: Long, val lng: Long)

internal fun keyOf(p: LatLng) = PointKey((p.lat * 1e6).roundToLong(), (p.lng * 1e6).roundToLong())

/**
 * How near a point must be to a way of the data to be on it: the engine's matched route ends lie on a way, rounded to the
 * micro-degree (about 0.1 m), and so does a route point on a way's first stretch.
 */
internal const val ON_WAY_METERS = 1.0

/** One direction leaving a point along one of the data's ways: the way's points from that point outward, and its tags. */
internal class Branch(val points: List<LatLng>, val tags: Map<String, String>) {
    private val along = metersAlong(points)

    /** The way's first point apart from the one it leaves from, or `null` for a way of no length. */
    val firstStep: LatLng? = keyOf(points.first()).let { start -> points.firstOrNull { keyOf(it) != start } }

    /** Its bearing from its first point to [TURN_MEASURE_METERS] along it, or to its end if it is shorter (B1). */
    fun bearing(): Double = GeoDistance.initialBearingDegrees(points.first(), pointAlong(points, along, TURN_MEASURE_METERS))

    /** Whether the route's neighbouring point [q] is the way this branch leaves along: its first step, or a point on its first stretch. */
    fun leavesToward(q: LatLng): Boolean {
        val step = firstStep ?: return false
        if (keyOf(q) == keyOf(step)) return true
        return GeoDistance.metersBetween(points.first(), q) > ON_WAY_METERS && metersFromSegment(q, points.first(), step) <= ON_WAY_METERS
    }
}

/** The data's ways round a route, indexed by their points, for the branches leaving a route point. */
internal class WaysAt(private val ways: List<RouteWay>) {
    private val byPoint = HashMap<PointKey, MutableList<Pair<RouteWay, Int>>>()

    init {
        for (way in ways) {
            // A point repeated in a row is one point: indexed at its first place, so it gives its two directions once.
            way.points.forEachIndexed { k, p ->
                if (k == 0 || keyOf(way.points[k - 1]) != keyOf(p)) byPoint.getOrPut(keyOf(p)) { mutableListOf() }.add(way to k)
            }
        }
    }

    /**
     * The branches leaving [p] when it is a point of the data's ways, `null` when it is not. A way's end gives one, a point
     * inside a way two (so a bend within a way is never three, B1), and a graph node one for each way that ends there.
     */
    fun atPoint(p: LatLng): List<Branch>? {
        val at = byPoint[keyOf(p)] ?: return null
        return at.flatMap { (way, k) ->
            listOfNotNull(
                if (k > 0) Branch(way.points.subList(0, k + 1).asReversed(), way.tags) else null,
                if (k < way.points.lastIndex) Branch(way.points.subList(k, way.points.size), way.tags) else null,
            )
        }.filter { it.firstStep != null }
    }

    /**
     * The branches leaving [p] when it lies on a way between two of its points, as the engine's matched route ends do (B2:
     * "the trail's two directions"): for each way within [ON_WAY_METERS], its two directions from the nearest stretch.
     */
    fun onWay(p: LatLng): List<Branch> = ways.flatMap { way ->
        val k = (0 until way.points.lastIndex).minByOrNull { metersFromSegment(p, way.points[it], way.points[it + 1]) } ?: return@flatMap emptyList()
        if (metersFromSegment(p, way.points[k], way.points[k + 1]) > ON_WAY_METERS) return@flatMap emptyList()
        listOf(Branch(listOf(p) + way.points.subList(0, k + 1).asReversed(), way.tags), Branch(listOf(p) + way.points.subList(k + 1, way.points.size), way.tags))
    }.filter { it.firstStep != null }
}

/** Metres from [p] to the stretch from [a] to [b], on the flat local projection round [p]. */
internal fun metersFromSegment(p: LatLng, a: LatLng, b: LatLng): Double {
    val perDegree = GeoDistance.metersPerDegree(p.lat)
    val ax = (a.lng - p.lng) * perDegree.lng
    val ay = (a.lat - p.lat) * perDegree.lat
    val dx = (b.lng - a.lng) * perDegree.lng
    val dy = (b.lat - a.lat) * perDegree.lat
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}

/** Each point's metres along [points] from the first. */
internal fun metersAlong(points: List<LatLng>): DoubleArray =
    DoubleArray(points.size).also { a -> for (i in 1 until points.size) a[i] = a[i - 1] + GeoDistance.metersBetween(points[i - 1], points[i]) }

/** The point [target] metres along [points] ([along] their metres), held to the first and last points. */
internal fun pointAlong(points: List<LatLng>, along: DoubleArray, target: Double): LatLng {
    if (target <= 0.0) return points.first()
    if (target >= along.last()) return points.last()
    var j = 0
    while (j < along.size - 2 && along[j + 1] < target) j++
    val span = along[j + 1] - along[j]
    val t = if (span <= 0.0) 0.0 else (target - along[j]) / span
    val a = points[j]
    val b = points[j + 1]
    return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
}
