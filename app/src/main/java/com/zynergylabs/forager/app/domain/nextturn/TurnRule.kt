package com.zynergylabs.forager.app.domain.nextturn

import kotlin.math.abs

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

/** Another way at a junction: its bearing from the node, and whether it counts as a path passed there (B5, [countsAsPath]). */
data class OtherWay(val bearing: Double, val countsAsPath: Boolean = true)

/**
 * One junction's judgement. [routeTurnDegrees] is the route's own change of direction, signed (right positive), measured
 * [TURN_MEASURE_METERS] either side; [marginDegrees] how much straighter the route is than the straightest other way
 * (below 0 when another way is straighter). Both `null` where nothing was measured ([JunctionClass.NO_LEG],
 * [JunctionClass.UNMATCHED]). [band] and [side] are a [JunctionClass.TURN]'s, [keep] a [JunctionClass.FORK]'s.
 * [leftPaths] and [rightPaths] count the paths passed to each side of the way in at a [JunctionClass.STRAIGHT_ON] junction
 * (B5: walkable ways only), and [leftNotPaths] and [rightNotPaths] the other ways there that are not counted.
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
    val leftNotPaths: Int = 0,
    val rightNotPaths: Int = 0,
)

/**
 * Judges a junction from the bearing the route comes in on ([inBearing], from [TURN_MEASURE_METERS] back to the node),
 * the bearing it leaves on ([outBearing], from the node to [TURN_MEASURE_METERS] on), and each other way there ([others]:
 * its bearing from the node to [TURN_MEASURE_METERS] along it, or to its end if it is shorter, and whether it is a path).
 * Bearings in degrees clockwise from north. Every way counts for the turn (B1); only paths count as passed (B5).
 */
internal fun judgeJunction(inBearing: Double, outBearing: Double, others: List<OtherWay>): JunctionJudgement {
    val otherBearings = others.map { it.bearing }
    val turn = normDegrees(outBearing - inBearing)
    val bend = abs(turn)
    // Each other way's turn from the way in, signed as the route's is.
    val deviations = otherBearings.map { normDegrees(it - inBearing) }
    // A junction of three or more ways always has another way; said plainly, as no turn and no margin, if one comes without.
    val straightestOther = deviations.minOfOrNull { abs(it) } ?: return JunctionJudgement(JunctionClass.STRAIGHT_ON, turn)
    val margin = straightestOther - bend
    // B1: "another way there is straighter than the route's way, or ... the route bends at least 30° and is the
    // straightest by less than 30°".
    val isTurn = straightestOther < bend || (bend >= TURN_MIN_BEND_DEGREES && margin < TURN_MAX_MARGIN_DEGREES)
    // A turn's turn is never 0: either another way is straighter, so the bend is above 0, or the bend is at least S.
    if (isTurn) return JunctionJudgement(JunctionClass.TURN, turn, margin, band = bandOf(turn), side = if (turn < 0.0) TurnSide.LEFT else TurnSide.RIGHT)
    // B3: the nearest other way within 45 degrees of the route's own way out says which side the branch leaves on, and
    // the arrow keeps to the other. A branch exactly along the way out (measure zero) is taken as leaving right.
    val branch = otherBearings.map { normDegrees(it - outBearing) }.filter { abs(it) <= FORK_WITHIN_DEGREES }.minByOrNull { abs(it) }
    if (branch != null) return JunctionJudgement(JunctionClass.FORK, turn, margin, keep = if (branch >= 0.0) TurnSide.LEFT else TurnSide.RIGHT)
    // B5: the paths passed on each side are the walkable ways only; the others there are counted apart, for the M1 line.
    val left = others.filterIndexed { i, _ -> deviations[i] < 0.0 }
    val right = others.filterIndexed { i, _ -> deviations[i] > 0.0 }
    return JunctionJudgement(
        JunctionClass.STRAIGHT_ON, turn, margin,
        leftPaths = left.count { it.countsAsPath }, rightPaths = right.count { it.countsAsPath },
        leftNotPaths = left.count { !it.countsAsPath }, rightNotPaths = right.count { !it.countsAsPath },
    )
}

/** The band of a turn of [turnDegrees] (C1), either side. */
internal fun bandOf(turnDegrees: Double): TurnBand {
    val bend = abs(turnDegrees)
    return when {
        bend < SLIGHT_BELOW_DEGREES -> TurnBand.SLIGHT
        bend < SHARP_FROM_DEGREES -> TurnBand.PLAIN
        else -> TurnBand.SHARP
    }
}

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
