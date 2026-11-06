package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import java.io.File

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

    /** No junction is known: [why] says which step failed, in words, never a position. */
    data class Unread(val why: String, override val reading: WaysReading) : RouteTurns
}

/**
 * The junctions along [wayHome], whose walker is placed on [line] (built from that same [wayHome]), read through
 * [routeWays] from [routeData] (F1: the project's own interface; the vendored engine is not touched). Never throws for
 * a read that fails: that is [RouteTurns.Unread].
 */
suspend fun routeTurnsFor(
    wayHome: WayHome,
    line: WayHomeLine,
    routeWays: RouteWays,
    routeData: File,
    nanoTime: () -> Long = System::nanoTime,
): RouteTurns = RouteTurns.Read(emptyList(), null, WaysReading(0L, 0, 0))

/** The judging alone, on [ways] already read: see [routeTurnsFor]. */
internal fun judgeRouteTurns(wayHome: WayHome, line: WayHomeLine, ways: List<RouteWay>, reading: WaysReading): RouteTurns =
    RouteTurns.Read(emptyList(), null, reading)
