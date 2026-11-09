package com.zynergylabs.forager.app.domain.dropoff

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import java.util.PriorityQueue
import kotlin.math.roundToLong

/**
 * Where a private access way comes out onto a public street: the street drop-off's first rank (way-to dispatch
 * 2026-10-28, PR 3; the owner's ruling of 10-28: "drop off at the street in front of the pin, the way Google does it",
 * never routing up a private way, the pin's own driveway included; thresholds 25 m and 300 m, the owner's go).
 *
 * From every closed way with an end within [REACH_METERS] of the target, the closed ways joined to it are followed, up
 * to [MAX_WALK_METERS] along them. Each node where they meet an open public way is a mouth. The nearest mouth along the
 * closed ways comes first. Nothing here routes: the caller routes the walker to a mouth on the public way and draws the
 * rest straight.
 */
object AccessWayMouths {
    const val REACH_METERS = 25.0
    const val MAX_WALK_METERS = 300.0

    /** A mouth: the node on the public way, how far it is along the closed ways from the target's end of them, and the public way's tags. */
    data class Mouth(val at: LatLng, val walkMeters: Double, val street: Map<String, String>)

    /**
     * [target]: the target's building outline rings (closed), or one ring of one point, the pin, when there is no
     * building. The distance to it is to its outline, or zero inside it.
     */
    fun find(target: List<List<LatLng>>, ways: List<RouteWay>): List<Mouth> {
        val closed = ways.filter { isClosed(it.tags) && it.points.size >= 2 }
        val open = ways.filter { isPublicStreet(it.tags) }
        val openAt = HashMap<Key, Map<String, String>>()
        for (w in open) for (p in listOf(w.points.first(), w.points.last())) openAt.putIfAbsent(key(p), w.tags)

        // The closed network: each closed way joins its two end nodes, its length its walk.
        val edges = HashMap<Key, MutableList<Pair<Key, Double>>>()
        val position = HashMap<Key, LatLng>()
        for (w in closed) {
            val a = key(w.points.first())
            val b = key(w.points.last())
            val length = w.points.zipWithNext { p, q -> GeoDistance.metersBetween(p, q) }.sum()
            position[a] = w.points.first()
            position[b] = w.points.last()
            edges.getOrPut(a) { mutableListOf() } += b to length
            edges.getOrPut(b) { mutableListOf() } += a to length
        }
        val starts = position.filterValues { distanceTo(target, it) <= REACH_METERS }.keys
        val best = HashMap<Key, Double>()
        val queue = PriorityQueue<Pair<Key, Double>>(compareBy { it.second })
        for (s in starts) {
            best[s] = 0.0
            queue += s to 0.0
        }
        val mouths = mutableListOf<Mouth>()
        while (queue.isNotEmpty()) {
            val (node, walked) = queue.poll()
            if (walked > (best[node] ?: Double.MAX_VALUE)) continue
            openAt[node]?.let { street ->
                mouths += Mouth(position.getValue(node), walked, street)
                continue // the walk stops on the street: never further along another closed way from there
            }
            for ((next, length) in edges[node].orEmpty()) {
                val total = walked + length
                if (total <= MAX_WALK_METERS && total < (best[next] ?: Double.MAX_VALUE)) {
                    best[next] = total
                    queue += next to total
                }
            }
        }
        return mouths.sortedBy { it.walkMeters }
    }

    /**
     * Closed to the walker as a drop-off route, as the owner's go reads it: access or foot private or no, or a driveway.
     * The profile may still let BRouter use some of these. Here they are never routed up, whatever the profile says.
     */
    fun isClosed(tags: Map<String, String>): Boolean =
        tags["access"] in CLOSED_ACCESS || tags["foot"] in CLOSED_ACCESS || tags["service"] == "driveway"

    /** An open public street a walker can be dropped off on. */
    fun isPublicStreet(tags: Map<String, String>): Boolean = !isClosed(tags) && tags["highway"] in PUBLIC_STREETS

    private val CLOSED_ACCESS = setOf("private", "no")
    private val PUBLIC_STREETS = setOf(
        "residential", "living_street", "unclassified", "service", "pedestrian", "road",
        "tertiary", "tertiary_link", "secondary", "secondary_link", "primary", "primary_link",
    )

    /** Metres from [p] to [target]'s outline, or 0 inside it; for a one-point ring, to that point. */
    internal fun distanceTo(target: List<List<LatLng>>, p: LatLng): Double {
        if (target.any { it.size >= 4 && contains(it, p) }) return 0.0
        return target.minOf { ring ->
            if (ring.size == 1) GeoDistance.metersBetween(ring[0], p) else ring.zipWithNext { a, b -> segmentDistance(p, a, b) }.min()
        }
    }

    private fun contains(ring: List<LatLng>, p: LatLng): Boolean {
        var inside = false
        for (i in 0 until ring.size - 1) {
            val a = ring[i]
            val b = ring[i + 1]
            if ((a.lat > p.lat) != (b.lat > p.lat) && p.lng < a.lng + (p.lat - a.lat) * (b.lng - a.lng) / (b.lat - a.lat)) inside = !inside
        }
        return inside
    }

    private fun segmentDistance(p: LatLng, a: LatLng, b: LatLng): Double {
        val m = GeoDistance.metersPerDegree(p.lat)
        val ax = (a.lng - p.lng) * m.lng
        val ay = (a.lat - p.lat) * m.lat
        val dx = (b.lng - a.lng) * m.lng
        val dy = (b.lat - a.lat) * m.lat
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
        return Math.hypot(ax + t * dx, ay + t * dy)
    }

    /** A node's key: its position to the route data's own micro-degree grain, so two ways' shared node is one key. */
    private data class Key(val lat: Long, val lng: Long)

    private fun key(p: LatLng) = Key((p.lat * 1e6).roundToLong(), (p.lng * 1e6).roundToLong())
}
