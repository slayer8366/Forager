package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackPoint
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.floor

/**
 * ε — how close two points of one track must be for the track to be joined to itself there.
 * Path-home join dispatch (2026-09-08), under the owner's ruling of 2026-09-07
 * (`docs/audits/2026-09-07-ruling-path-home-self-intersection.md`).
 *
 * **The ruling's condition, which is part of the ruling and not commentary on it:** ε is
 * acceptable only while the gap it bridges is within GPS error of the walked path anyway — i.e.
 * while stepping across ε is not meaningfully different from standing where the walker already
 * stood. The retrace ruling exists because a straight line crosses *unknown* ground in failing
 * light; ε metres between two points the walker actually occupied is a different object from a
 * straight line across a valley, but it is not zero. **Every request to raise this number is a
 * request to reopen that ruling and must be treated as one.** It is one constant, in one place,
 * not user-facing and not adaptive (build dispatch).
 *
 * **Why 10 m (the coder's choice, reported for the owner):** the dispatch set the floor by the
 * synthetic switchback — two 220 m legs 10 m apart across a slope — and asked for the smallest ε
 * that closes it. Those legs are 10 m apart by construction, so nothing under 10 m can join them
 * (the pre-build report tried 8 and 12; `TrackSelfJoinTest` pins 9 open and 10 closed). The two
 * real patch tracks saturate at 4 m, so 10 m closes them with room to spare; the synthetic
 * genuinely-distant walker's zig-zag (points 11 m apart two legs on) does *not* close at 10 m and
 * does at 12 m, which is the other reason to stop at the floor rather than above it. **This is
 * not yet the real number**: on the one device measured GPS accuracy is a constant 3.79 m
 * placeholder, so the field cannot say what ε real fix noise supports. Beta data decides.
 */
const val SELF_JOIN_EPSILON_METERS = 10.0

/**
 * The shortest way home along a track joined to itself — the track-network candidate the owner
 * ruled inside the retrace ruling. The track is a graph: every consecutive pair of stored points
 * is an edge, and additionally any two points within [epsilonMeters] of each other are an edge
 * (a *join*). The result is the shortest route through that graph from the most recent point to
 * the first, in metres.
 *
 * **The retrace guarantee, kept by construction:** every edge is either a consecutive-point leg
 * or a join between two points the walker actually occupied, so no route ever crosses more than
 * ε of ground the walker has not walked, and it does so only where the walker stood within ε of
 * standing there before. A loop whose far sides are a kilometre apart has no join and reads its
 * full length until the rim is regained; a walker returning over their own points is joined at
 * 0 m and reads the ground still ahead — which is the defect this fixes: the plain sum of the
 * legs could never decrease during a recording, so it read the whole trip beside the car.
 *
 * ## Cost
 *
 * The join scan is the expensive half. Naively it is every pair, O(n²) — 4.1 M haversines at a
 * four-hour HIGH_ACCURACY track's 2,880 points. Here each point is dropped into a grid of
 * ε-sized cells over a local flat projection and compared only with points in its own and the
 * eight neighbouring cells, so the scan is linear in the point count for any track a walker can
 * record (it degrades toward n² only when thousands of points share one ε cell, which the
 * sampler's distance floor prevents). The cell is 1 % wider than ε so the projection's own error
 * can never drop a pair the exact haversine test would accept; every candidate pair is then
 * tested with [GeoDistance.metersBetween], so the projection decides only *which* pairs are
 * looked at, never whether they join. The shortest route is Dijkstra with a binary heap over
 * n nodes and n − 1 + joins edges, stopping when the first point is settled. Measured figures are
 * in the dispatch's completion report; recomputed from scratch per call (the function is pure —
 * points only append during a recording, so an incremental graph is possible, but not built
 * until a measurement says it is needed).
 *
 * [points] must be non-empty; a single point is a zero-length way home with no joins.
 */
fun joinedTrackHome(points: List<TrackPoint>, epsilonMeters: Double = SELF_JOIN_EPSILON_METERS): JoinedTrackHome {
    require(points.isNotEmpty()) { "joinedTrackHome needs at least one point" }
    require(epsilonMeters >= 0.0) { "epsilonMeters must not be negative, was $epsilonMeters" }
    val n = points.size
    if (n == 1) return JoinedTrackHome(meters = 0.0, joinEdgeCount = 0, joinsOnRoute = 0)

    val latLngs = Array(n) { LatLng(points[it].lat, points[it].lng) }
    val legMeters = DoubleArray(n - 1) { GeoDistance.metersBetween(latLngs[it], latLngs[it + 1]) }

    // Join scan over a grid of ε cells in a local equirectangular projection about the first
    // point. Points are inserted in track order, so each point is compared only against earlier
    // ones and every pair is seen once. A consecutive pair is already a leg and is skipped.
    val metersPerDegreeLat = Math.PI * GeoDistance.EARTH_MEAN_RADIUS_METERS / 180.0
    val metersPerDegreeLng = metersPerDegreeLat * cos(Math.toRadians(latLngs[0].lat))
    val cellMeters = (epsilonMeters * GRID_CELL_SLACK).coerceAtLeast(MIN_CELL_METERS)
    val grid = HashMap<Long, MutableList<Int>>()
    val joins = arrayOfNulls<MutableList<Join>>(n)
    var joinEdgeCount = 0
    for (i in 0 until n) {
        val x = normalizeLongitudeDelta(latLngs[i].lng - latLngs[0].lng) * metersPerDegreeLng
        val y = (latLngs[i].lat - latLngs[0].lat) * metersPerDegreeLat
        val cx = floor(x / cellMeters).toInt()
        val cy = floor(y / cellMeters).toInt()
        for (dx in -1..1) {
            for (dy in -1..1) {
                val neighbours = grid[cellKey(cx + dx, cy + dy)] ?: continue
                for (j in neighbours) {
                    if (j >= i - 1) continue
                    val meters = GeoDistance.metersBetween(latLngs[i], latLngs[j])
                    if (meters <= epsilonMeters) {
                        (joins[i] ?: ArrayList<Join>(2).also { joins[i] = it }).add(Join(j, meters))
                        (joins[j] ?: ArrayList<Join>(2).also { joins[j] = it }).add(Join(i, meters))
                        joinEdgeCount++
                    }
                }
            }
        }
        grid.getOrPut(cellKey(cx, cy)) { ArrayList(2) }.add(i)
    }

    // Dijkstra from the most recent point to the first.
    val source = n - 1
    val distance = DoubleArray(n) { Double.POSITIVE_INFINITY }
    val previous = IntArray(n) { -1 }
    val previousViaJoin = BooleanArray(n)
    distance[source] = 0.0
    val queue = PriorityQueue<QueueEntry>()
    queue.add(QueueEntry(0.0, source))
    while (queue.isNotEmpty()) {
        val entry = queue.poll()
        val u = entry.node
        if (u == 0) break
        if (entry.distance > distance[u]) continue
        fun relax(v: Int, edgeMeters: Double, viaJoin: Boolean) {
            val candidate = entry.distance + edgeMeters
            if (candidate < distance[v]) {
                distance[v] = candidate
                previous[v] = u
                previousViaJoin[v] = viaJoin
                queue.add(QueueEntry(candidate, v))
            }
        }
        if (u > 0) relax(u - 1, legMeters[u - 1], viaJoin = false)
        if (u < n - 1) relax(u + 1, legMeters[u], viaJoin = false)
        joins[u]?.forEach { relax(it.to, it.meters, viaJoin = true) }
    }

    var joinsOnRoute = 0
    var v = 0
    while (v != source) {
        if (previousViaJoin[v]) joinsOnRoute++
        v = previous[v]
    }
    return JoinedTrackHome(meters = distance[0], joinEdgeCount = joinEdgeCount, joinsOnRoute = joinsOnRoute)
}

/** See [joinedTrackHome]. */
data class JoinedTrackHome(
    /** Along the joined track, most recent point back to the first. */
    val meters: Double,
    /** How many pairs of non-consecutive points lie within ε of each other — the graph's joins, whether or not the route used them. */
    val joinEdgeCount: Int,
    /** How many joins the shortest route home actually crosses — zero means the route is the plain retrace. */
    val joinsOnRoute: Int,
)

private class Join(val to: Int, val meters: Double)

private class QueueEntry(val distance: Double, val node: Int) : Comparable<QueueEntry> {
    override fun compareTo(other: QueueEntry): Int = distance.compareTo(other.distance)
}

/** The grid cell is this much wider than ε — see [joinedTrackHome], "Cost". */
private const val GRID_CELL_SLACK = 1.01

/** Guards the cell size against an ε of zero (which would divide by zero); exact duplicates still join at ε = 0. */
private const val MIN_CELL_METERS = 1e-6

private fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFF_FFFFL)

/** Longitude differences straddling the antimeridian fold back into [-180, 180]. */
private fun normalizeLongitudeDelta(deltaDegrees: Double): Double {
    var normalized = deltaDegrees % 360.0
    if (normalized > 180.0) normalized -= 360.0
    if (normalized < -180.0) normalized += 360.0
    return normalized
}
