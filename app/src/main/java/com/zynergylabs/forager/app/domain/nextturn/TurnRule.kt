package com.zynergylabs.forager.app.domain.nextturn

/**
 * What one junction along a route is (dispatch 2026-11-06-01, Phase 2a): the turn rule B1, the forks B3 and the bands C1
 * of Amendment 1, on directions already measured. Pure: no data, no line.
 */
enum class JunctionClass {
    /** B1: an angled turn. */
    TURN,

    /** B3: the route keeps the straightest way while another way leaves within [FORK_WITHIN_DEGREES] of it. */
    FORK,

    /** The route carries on through: not a turn, not a fork. Its other ways count as paths passed on their sides. */
    STRAIGHT_ON,

    /**
     * Not judged: the line has no length before or after the point to take a direction from (a lead-in of no length,
     * when the walker stands on the trail; the line's own ends). Never a turn, and no side for its ways.
     */
    NO_LEG,

    /** The route's own way in or out could not be found among the data's ways there, so the junction is unknown. */
    UNMATCHED,
}

/**
 * One junction's judgement. [routeTurnDegrees] is the route's own change of direction, signed (right positive), measured
 * [TURN_MEASURE_METERS] either side; [marginDegrees] how much straighter the route is than the straightest other way
 * (below 0 when another way is straighter). Both `null` where nothing was measured ([JunctionClass.NO_LEG],
 * [JunctionClass.UNMATCHED]). [band] and [side] are a [JunctionClass.TURN]'s, [keep] a [JunctionClass.FORK]'s.
 * [leftPaths] and [rightPaths] count the other ways leaving to each side of the way in.
 */
data class JunctionJudgement(
    val junctionClass: JunctionClass,
    val routeTurnDegrees: Double? = null,
    val marginDegrees: Double? = null,
    val band: TurnBand? = null,
    val side: TurnSide? = null,
    val keep: TurnSide? = null,
    val leftPaths: Int = 0,
    val rightPaths: Int = 0,
)

/**
 * Judges a junction from the bearing the route comes in on ([inBearing], from [TURN_MEASURE_METERS] back to the node),
 * the bearing it leaves on ([outBearing], from the node to [TURN_MEASURE_METERS] on), and the bearing of each other way
 * there ([otherBearings], from the node to [TURN_MEASURE_METERS] along it, or to its end if it is shorter). Bearings in
 * degrees clockwise from north.
 */
internal fun judgeJunction(inBearing: Double, outBearing: Double, otherBearings: List<Double>): JunctionJudgement =
    JunctionJudgement(JunctionClass.STRAIGHT_ON)

/** The band of a turn of [turnDegrees] (C1). */
internal fun bandOf(turnDegrees: Double): TurnBand = TurnBand.PLAIN

/** [degrees] as an angle in (-180, 180]. */
internal fun normDegrees(degrees: Double): Double {
    var x = degrees % 360.0
    if (x <= -180.0) x += 360.0
    if (x > 180.0) x -= 360.0
    return x
}

/**
 * How far either side of a node a direction is measured (Phase 1, section 2: the class is the same at 10, 20 and 30 m for
 * 1,016 of 1,136 manoeuvres; it is the hiking profile's own `turnInstructionCatchingRange`). **Provisional** (B1).
 */
const val TURN_MEASURE_METERS = 20.0

/** B1's S: a route that is the straightest way turns only if it bends at least this much. **Provisional**, set on the public fixture; the planner reads M1 on the S22 before it is kept. */
const val TURN_MIN_BEND_DEGREES = 30.0

/** B1's M: ... and is the straightest by less than this. **Provisional**, as [TURN_MIN_BEND_DEGREES]. */
const val TURN_MAX_MARGIN_DEGREES = 30.0

/** B3, the planner's reading: another way leaving within this of the route's way makes a junction the route keeps straight on through a fork. */
const val FORK_WITHIN_DEGREES = 45.0

/** C1: a turn below this is slight. */
const val SLIGHT_BELOW_DEGREES = 45.0

/** C1: a turn from this is sharp; between [SLIGHT_BELOW_DEGREES] and this it is plain. */
const val SHARP_FROM_DEGREES = 135.0
