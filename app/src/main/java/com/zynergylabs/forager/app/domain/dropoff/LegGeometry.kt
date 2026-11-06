package com.zynergylabs.forager.app.domain.dropoff

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import kotlin.math.hypot

/**
 * Flat geometry in metres round one reference point, for legs of tens to a few hundred metres (way-to dispatch
 * 2026-10-28, PR 4). It uses the equirectangular projection [GeoDistance.metersPerDegree] gives. At 300 m its error is
 * far under a metre, below the map data's own.
 */
internal class LegGeometry(private val reference: LatLng) {
    private val m = GeoDistance.metersPerDegree(reference.lat)

    data class P(val x: Double, val y: Double)

    fun p(a: LatLng) = P((a.lng - reference.lng) * m.lng, (a.lat - reference.lat) * m.lat)

    fun latLng(p: P) = LatLng(lat = reference.lat + p.y / m.lat, lng = reference.lng + p.x / m.lng)

    /** Whether the segments a-b and c-d cross, touching at an end included. */
    fun crosses(a: LatLng, b: LatLng, c: LatLng, d: LatLng): Boolean {
        val (pa, pb, pc, pd) = listOf(p(a), p(b), p(c), p(d))
        val d1 = orient(pc, pd, pa)
        val d2 = orient(pc, pd, pb)
        val d3 = orient(pa, pb, pc)
        val d4 = orient(pa, pb, pd)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        return (d1 == 0.0 && onSegment(pc, pd, pa)) || (d2 == 0.0 && onSegment(pc, pd, pb)) ||
            (d3 == 0.0 && onSegment(pa, pb, pc)) || (d4 == 0.0 && onSegment(pa, pb, pd))
    }

    /** Whether the segment a-b crosses or enters any part of [line] (a polyline, or a closed ring). */
    fun crossesLine(a: LatLng, b: LatLng, line: List<LatLng>): Boolean = line.zipWithNext().any { (c, d) -> crosses(a, b, c, d) }

    /** Whether [point] is inside the closed [ring]. */
    fun inside(point: LatLng, ring: List<LatLng>): Boolean {
        if (ring.size < 4) return false
        val q = p(point)
        var inside = false
        val pts = ring.map { p(it) }
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            if ((a.y > q.y) != (b.y > q.y) && q.x < a.x + (q.y - a.y) * (b.x - a.x) / (b.y - a.y)) inside = !inside
        }
        return inside
    }

    /** The point of segment a-b nearest [point], and its distance in metres. */
    fun foot(point: LatLng, a: LatLng, b: LatLng): Pair<LatLng, Double> {
        val q = p(point)
        val pa = p(a)
        val pb = p(b)
        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (((q.x - pa.x) * dx + (q.y - pa.y) * dy) / l2).coerceIn(0.0, 1.0)
        val f = P(pa.x + t * dx, pa.y + t * dy)
        return latLng(f) to hypot(f.x - q.x, f.y - q.y)
    }

    /** Metres from [point] to the nearest point of [line], and that point. */
    fun nearestOn(point: LatLng, line: List<LatLng>): Pair<LatLng, Double> =
        if (line.size == 1) line[0] to GeoDistance.metersBetween(point, line[0]) else line.zipWithNext { a, b -> foot(point, a, b) }.minBy { it.second }

    /** Metres from [point] to [rings]' outline, or 0 inside one. */
    fun distanceToArea(point: LatLng, rings: List<List<LatLng>>): Double =
        if (rings.any { inside(point, it) }) 0.0 else rings.filter { it.isNotEmpty() }.minOfOrNull { nearestOn(point, it).second } ?: Double.MAX_VALUE

    /** Points along a-b every [stepMeters] or closer, both ends included. */
    fun along(a: LatLng, b: LatLng, stepMeters: Double): List<LatLng> {
        val pa = p(a)
        val pb = p(b)
        val n = maxOf(1, kotlin.math.ceil(hypot(pb.x - pa.x, pb.y - pa.y) / stepMeters).toInt())
        return (0..n).map { i -> latLng(P(pa.x + (pb.x - pa.x) * i / n, pa.y + (pb.y - pa.y) * i / n)) }
    }

    private fun orient(a: P, b: P, c: P) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun onSegment(a: P, b: P, c: P) =
        minOf(a.x, b.x) - 1e-9 <= c.x && c.x <= maxOf(a.x, b.x) + 1e-9 && minOf(a.y, b.y) - 1e-9 <= c.y && c.y <= maxOf(a.y, b.y) + 1e-9
}
