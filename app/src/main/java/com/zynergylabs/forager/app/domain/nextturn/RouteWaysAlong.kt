package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import java.io.File

/** What [readWaysAlong] read: the ways kept, or the first read's failure, and how many reads it took. */
internal class WaysAlong(val ways: Result<List<RouteWay>>, val reads: Int)

/**
 * The data's ways that touch [route] (the engine's trail route, in walking order), through [routeWays] (F1), every way the
 * data holds, whatever its tags (B1: "every way in the data counts").
 *
 * **How it is read.** One read round each stretch of the route: a read round a route point takes in every route point
 * within [WAYS_READ_RADIUS_METERS] of it, and the next read is round the first point beyond that. A read returns every
 * way of each data cell its circle touches (`RouteWays.near`), so each route point's cell is read, and with it every way
 * that ends at the point, since the data keeps a way at both its ends. Not one read round the whole route, which on a
 * long way home would hold every way of a large area at once.
 *
 * **What is kept.** A way with a point that is one of the route's points, or that passes within [ON_WAY_METERS] of the
 * route's first or last point (the engine's matched ends, which lie between a way's points). Each way once, however many
 * reads return it and from whichever end.
 */
internal suspend fun readWaysAlong(route: List<LatLng>, routeWays: RouteWays, routeData: File): WaysAlong {
    val centres = mutableListOf<LatLng>()
    for (p in route) if (centres.isEmpty() || GeoDistance.metersBetween(centres.last(), p) > WAYS_READ_RADIUS_METERS) centres += p
    val routeKeys: Set<PointKey> = route.mapTo(HashSet()) { keyOf(it) }
    val ends = listOfNotNull(route.firstOrNull(), route.lastOrNull())
    val kept = LinkedHashMap<List<PointKey>, RouteWay>()
    var reads = 0
    for (centre in centres) {
        reads++
        val ways = routeWays.near(centre, WAYS_READ_RADIUS_METERS, routeData).getOrElse { return WaysAlong(Result.failure(it), reads) }
        for (way in ways) {
            if (way.points.size < 2) continue
            val keys = way.points.map(::keyOf)
            val touches = keys.any { it in routeKeys } ||
                ends.any { end -> (0 until way.points.lastIndex).any { metersFromSegment(end, way.points[it], way.points[it + 1]) <= ON_WAY_METERS } }
            if (!touches) continue
            kept.putIfAbsent(canonical(keys), way)
        }
    }
    return WaysAlong(Result.success(kept.values.toList()), reads)
}

/** A way's points as one list whichever end it was read from: the smaller of the two orders. */
private fun canonical(keys: List<PointKey>): List<PointKey> {
    val reversed = keys.asReversed()
    for (i in keys.indices) {
        val a = keys[i]
        val b = reversed[i]
        if (a != b) return if (a.lat < b.lat || (a.lat == b.lat && a.lng < b.lng)) keys else reversed.toList()
    }
    return keys
}

/**
 * The radius of each read round the route ([readWaysAlong]). A read takes in the data cells its circle touches, 1/32
 * degree each, so up to four of them at 1 km. Not tuned: M2 on the S22 reads how long a route's reads take.
 */
internal const val WAYS_READ_RADIUS_METERS = 1_000.0
