package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind
import java.io.File
import kotlin.math.abs

/** Where along a route a judged point is: a junction on the trail part, or one of its two joins (B2). */
enum class JunctionKind {
    /** A node of the trail route where three or more of the data's ways meet (B1). */
    JUNCTION,

    /** Where the line first reaches the trail route: the way to a waypoint's lead-in, the way home's first joint (B2). */
    JOIN_START,

    /** Where the line leaves the trail route: the way to a waypoint's final leg, the way home's last joint (B2). */
    JOIN_END,
}

/**
 * One judged point of a route: [node] is its index in the [WayHomeLine] the walker is placed on, [alongMeters] that
 * line's metres to it, [ways] how many ways meet there (the join's leg counted as one), [judgement] what it is.
 */
data class RouteJunction(val node: Int, val alongMeters: Double, val kind: JunctionKind, val ways: Int, val judgement: JunctionJudgement)

/** How the route data's ways were read for one route (M2): how long it took, in how many reads, and how many ways were kept. */
data class WaysReading(val millis: Long, val reads: Int, val ways: Int)

/** The junctions of one route, or why they are unknown. */
sealed interface RouteTurns {
    val reading: WaysReading

    /**
     * Every point judged, in walking order. [trackAheadAfterNode] is set on a way home whose last part is the walker's own
     * track, with length: the node of the last join, after which no turn is known (E2, the planner's reading).
     */
    data class Read(val junctions: List<RouteJunction>, val trackAheadAfterNode: Int?, override val reading: WaysReading) : RouteTurns

    /**
     * No junction is known: [why] says which step failed, in words or as the failure's kind, never a position or an
     * exception's message (which can name a route square). [cause] is the failure itself, for the warning log.
     */
    data class Unread(val why: String, override val reading: WaysReading, val cause: Throwable? = null) : RouteTurns
}

/**
 * The junctions along [wayHome], whose walker is placed on [line] (built from that same [wayHome]), read through
 * [routeWays] from [routeData] (F1: the project's own interface; the vendored engine is not touched) and timed with
 * [nanoTime] (M2). A read that fails is [RouteTurns.Unread]; a reader that throws instead is not caught here, since the
 * interface promises a failure as a result, and its caller's job says so (`RouteTurnsLog`).
 *
 * **What is judged** (B1, B2), each point once, at the last of any run of repeated points:
 * - every point of the trail route between its ends where three or more of the data's ways meet ([JunctionKind.JUNCTION]);
 *   a point inside one way is two ways and never judged, so a bend within a way is never a turn;
 * - the two joins ([JunctionKind.JOIN_START], [JunctionKind.JOIN_END]): where the line reaches the trail route and where
 *   it leaves it, the way to a waypoint's lead-in and final leg, the way home's two joints. The ways there are the
 *   trail's directions from the point and the leg; a join where they are fewer than three (the trail ends there) is no
 *   junction.
 *
 * **How.** The route comes in on the bearing from [TURN_MEASURE_METERS] back along the line to the point, and leaves on
 * the bearing from the point to [TURN_MEASURE_METERS] on, measured on the line as drawn, legs included. Its own ways in
 * and out are the data's branches whose first step is the line's neighbouring point; every other branch there is judged
 * against them ([judgeJunction]). A route point not in the data, or a junction whose way in or out is not among its
 * branches, is [JunctionClass.UNMATCHED]: unknown, never guessed. A point with no line before or after it to take a
 * direction from is [JunctionClass.NO_LEG].
 */
suspend fun routeTurnsFor(
    wayHome: WayHome,
    line: WayHomeLine,
    routeWays: RouteWays,
    routeData: File,
    nanoTime: () -> Long = System::nanoTime,
): RouteTurns {
    val started = nanoTime()
    val read = readWaysAlong(wayHome.trailRoute, routeWays, routeData)
    val reading = WaysReading((nanoTime() - started) / 1_000_000L, read.reads, read.ways.getOrNull()?.size ?: 0)
    val ways = read.ways.getOrElse { return RouteTurns.Unread(it.javaClass.simpleName, reading, it) }
    return judgeRouteTurns(wayHome, line, ways, reading)
}

/** The judging alone, on [ways] already read: see [routeTurnsFor]. */
internal fun judgeRouteTurns(wayHome: WayHome, line: WayHomeLine, ways: List<RouteWay>, reading: WaysReading): RouteTurns {
    val points = wayHome.line
    if (wayHome.trackBack.points.isEmpty()) return RouteTurns.Unread("the line has no first part", reading)
    val nodes = nodesOf(points, line) ?: return RouteTurns.Unread("the line's points are not among its nodes", reading)
    val keys = points.map(::keyOf)
    fun lastOfRun(i: Int): Int {
        var j = i
        while (j < points.lastIndex && keys[j + 1] == keys[i]) j++
        return j
    }
    fun previous(i: Int): LatLng? = (i - 1 downTo 0).firstOrNull { keys[it] != keys[i] }?.let { points[it] }
    fun next(i: Int): LatLng? = (i + 1..points.lastIndex).firstOrNull { keys[it] != keys[i] }?.let { points[it] }

    // The trail route within the line: the trail part, without its two joints when it has them (WayHome.trailRoute).
    val offset = wayHome.trackBack.points.lastIndex
    val joint = if (wayHome.trailJointMeters > 0.0) 1 else 0
    val start = lastOfRun(offset + joint)
    val end = lastOfRun(offset + wayHome.trail.points.lastIndex - joint)
    // The way home's last part is the walker's own track, whose turns no data holds (E2, the planner's reading).
    val trackAhead = nodes[end].takeIf { wayHome.trackToStart.kind == WayHomePartKind.TRACK_TO_START && wayHome.trackToStart.meters > 0.0 }
    if (end <= start) return RouteTurns.Read(emptyList(), trackAhead, reading)

    val along = metersAlong(points)
    val at = WaysAt(ways)
    val junctions = mutableListOf<RouteJunction>()
    var i = start
    while (i <= end) {
        val j = lastOfRun(i)
        val kind = when (j) {
            start -> JunctionKind.JOIN_START
            end -> JunctionKind.JOIN_END
            else -> JunctionKind.JUNCTION
        }
        judgeAt(points[j], previous(j), next(j), kind, at, points, along, along[j])?.let { (ways, judgement) ->
            junctions += RouteJunction(nodes[j], line.alongMeters[nodes[j]], kind, ways, judgement)
        }
        i = j + 1
    }
    return RouteTurns.Read(junctions, trackAhead, reading)
}

/** One point's judgement and how many ways meet there, or `null` when fewer than three do (no junction). */
private fun judgeAt(
    p: LatLng,
    previous: LatLng?,
    next: LatLng?,
    kind: JunctionKind,
    at: WaysAt,
    points: List<LatLng>,
    along: DoubleArray,
    alongHere: Double,
): Pair<Int, JunctionJudgement>? {
    val unmatched = JunctionJudgement(JunctionClass.UNMATCHED)
    val branches = when (kind) {
        // Between its ends the engine's route runs through the data's own points; one that is not there is unknown.
        JunctionKind.JUNCTION -> at.atPoint(p) ?: return 0 to unmatched
        JunctionKind.JOIN_START, JunctionKind.JOIN_END -> at.atPoint(p) ?: at.onWay(p)
    }
    // A join's leg is one of the ways that meet there; a join on no way of the data is unknown.
    if (kind != JunctionKind.JUNCTION && branches.isEmpty()) return 1 to unmatched
    val ways = branches.size + if (kind == JunctionKind.JUNCTION) 0 else 1
    if (ways < 3) return null
    val wayIn = if (kind == JunctionKind.JOIN_START) null else previous?.let { q -> branches.firstOrNull { it.leavesToward(q) } } ?: return ways to unmatched
    val wayOut = if (kind == JunctionKind.JOIN_END) null else next?.let { q -> branches.firstOrNull { it !== wayIn && it.leavesToward(q) } } ?: return ways to unmatched
    val back = pointAlong(points, along, alongHere - TURN_MEASURE_METERS)
    val ahead = pointAlong(points, along, alongHere + TURN_MEASURE_METERS)
    if (GeoDistance.metersBetween(back, p) < NO_LEG_METERS || GeoDistance.metersBetween(p, ahead) < NO_LEG_METERS) return ways to JunctionJudgement(JunctionClass.NO_LEG)
    val others = branches.filter { it !== wayIn && it !== wayOut }.map { it.bearing() }
    return ways to judgeJunction(GeoDistance.initialBearingDegrees(back, p), GeoDistance.initialBearingDegrees(p, ahead), others)
}

/**
 * Each of [points]' node in [line], built from the same parts: [WayHomeLine] keeps every part point as a node, in order,
 * with its filled points between. Found by walking both in step, so a route that comes back past a point is never matched
 * to its first pass; `null` if a point is missing, which would mean the line was built from other parts.
 */
private fun nodesOf(points: List<LatLng>, line: WayHomeLine): IntArray? {
    val nodes = IntArray(points.size)
    var from = 0
    for (i in points.indices) {
        val p = points[i]
        val k = (from until line.nodes.size).firstOrNull { abs(line.nodes[it].lat - p.lat) <= SAME_NODE_DEGREES && abs(line.nodes[it].lng - p.lng) <= SAME_NODE_DEGREES } ?: return null
        nodes[i] = k
        from = k + 1
    }
    return nodes
}

/** A line's filled points are computed, so a part's point and its node can differ by a rounding step: far under a millimetre. */
private const val SAME_NODE_DEGREES = 1e-9

/** Less than this of line before or after a point gives no direction to judge it by ([JunctionClass.NO_LEG]). */
internal const val NO_LEG_METERS = 0.5
