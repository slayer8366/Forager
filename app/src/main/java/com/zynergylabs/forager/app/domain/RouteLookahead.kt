package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import kotlin.math.hypot

/**
 * Where the needle aims along a line home: the lookahead and its corner guard, moved unchanged out of [routeHome]
 * (dispatch 2026-10-24-10 Amendment 1, PR B) so the way home along trails aims the same way
 * (`domain/wayhome/WayHomeAhead`). The rules and their reasons are [routeHome]'s doc, "The lookahead" and "The corner
 * guard"; nothing here is new.
 *
 * [nodes] is the line, [alongMeters] each node's distance along it from its first node. [walkerPlace] is the node the
 * walker is taken to be at; [wantedAlongMeters] is how far along the line the lookahead would like to be. Returns the
 * chosen node's index: the first node at least [wantedAlongMeters] along, or the line's end, pulled back to the last
 * node for which every node from [walkerPlace] up to it lies within [SELF_JOIN_EPSILON_METERS] of the straight line
 * from [current].
 */
internal fun lookaheadIndex(nodes: List<LatLng>, alongMeters: DoubleArray, walkerPlace: Int, wantedAlongMeters: Double, current: LatLng): Int {
    val last = nodes.lastIndex
    val wanted = (walkerPlace..last).firstOrNull { alongMeters[it] >= wantedAlongMeters } ?: last
    var chosen = walkerPlace
    for (k in walkerPlace + 1..wanted) {
        val keepsToTheRoute = (walkerPlace until k).all { offLineMeters(nodes[it], current, nodes[k]) <= SELF_JOIN_EPSILON_METERS }
        if (!keepsToTheRoute) break
        chosen = k
    }
    return chosen
}

/**
 * Metres from [point] to the straight segment from [from] to [to], on a flat local projection
 * about [from]. The distances here are tens of metres, where the flat projection's error is far
 * below the fixes' own.
 */
internal fun offLineMeters(point: LatLng, from: LatLng, to: LatLng): Double {
    val (metersPerDegreeLat, metersPerDegreeLng) = GeoDistance.metersPerDegree(from.lat)
    val bx = (to.lng - from.lng) * metersPerDegreeLng
    val by = (to.lat - from.lat) * metersPerDegreeLat
    val px = (point.lng - from.lng) * metersPerDegreeLng
    val py = (point.lat - from.lat) * metersPerDegreeLat
    val lengthSquared = bx * bx + by * by
    val t = if (lengthSquared == 0.0) 0.0 else ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(px - t * bx, py - t * by)
}
