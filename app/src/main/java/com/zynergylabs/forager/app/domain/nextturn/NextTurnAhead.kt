package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress
import com.zynergylabs.forager.app.domain.wayhome.aimFrom

/** The way being navigated, as the next turn needs it. */
sealed interface NextTurnWay {
    /** The way is still being worked out (the straight line shows meanwhile). */
    data object Pending : NextTurnWay

    /** The way has no trail route: the straight line to a waypoint, or the way home along the walker's track (E2). */
    data object NoTrailRoute : NextTurnWay

    /** The way along trails: [line] the walker is placed on, and its [turns], `null` while they are still being read. */
    data class AlongTrails(val line: WayHomeLine, val turns: RouteTurns?) : NextTurnWay
}

/**
 * The next turn on [way] for a walker at [current], placed by [progress] ([com.zynergylabs.forager.app.domain.wayhome.placeOnWayHome],
 * `null` before the first fix places them).
 *
 * **D1, the planner's call (O5, option C):** "the figure counts from the needle's place, never below 0. A turn is passed
 * only when the walker's place passes its node, so it never passes early, and the place's only-forward rule is the
 * hysteresis." The needle's place is the one the needle aims from ([aimFrom]); the walker's place is [progress]'s, which
 * is never further on than the walker is.
 *
 * **What it is:** the first turn or fork from the walker's place on, unless a junction before it is unknown
 * ([NextTurnBlank.JUNCTION_UNREAD]). With none left: [NextTurn.NoTurnLeft] when every junction to the target is known,
 * and on a way home whose last part is the walker's own track [NextTurnBlank.TRACK_TURNS_UNKNOWN], as past its last
 * joint (E2, the planner's reading). On the way home's track back the next turn is the coming joint or beyond it, as
 * that reading has it: the track's own turns there are not looked for.
 *
 * **The count** (the owner's TalkBack ruling, "When paths come first (Recommended)"): the paths leaving on the turn's side
 * at the junctions the route passes straight through from the walker's place to the turn ([JunctionClass.STRAIGHT_ON];
 * a turn or a fork is a next turn itself, so none lies between), walkable ways only (B5, [countsAsPath]).
 */
fun nextTurn(way: NextTurnWay, progress: WayHomeProgress?, current: LatLng?): NextTurn {
    val along = when (way) {
        NextTurnWay.Pending -> return NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN)
        NextTurnWay.NoTrailRoute -> return NextTurn.Blank(NextTurnBlank.NO_TRAIL_ROUTE)
        is NextTurnWay.AlongTrails -> way
    }
    val turns = when (val t = along.turns) {
        null -> return NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN)
        is RouteTurns.Unread -> return NextTurn.Blank(NextTurnBlank.WAYS_UNREAD)
        is RouteTurns.Read -> t
    }
    val place = when (progress) {
        null -> return NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN)
        is WayHomeProgress.OffLine -> return NextTurn.Blank(NextTurnBlank.ROUTE_WITHHELD)
        is WayHomeProgress.Placed -> progress.place
    }
    if (current == null) return NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN)
    val line = along.line
    val trackAhead = turns.trackAheadAfterNode
    if (trackAhead != null && place.index > trackAhead) return NextTurn.Blank(NextTurnBlank.TRACK_TURNS_UNKNOWN)
    val needle = aimFrom(line, place.index, current)
    var left = 0
    var right = 0
    for (junction in turns.junctions) {
        if (junction.node < place.index) continue
        val judgement = junction.judgement
        val metersAway = (line.alongMeters[junction.node] - line.alongMeters[needle]).coerceAtLeast(0.0)
        when (judgement.junctionClass) {
            JunctionClass.UNMATCHED -> return NextTurn.Blank(NextTurnBlank.JUNCTION_UNREAD)
            // judgeJunction gives every turn its band and side, and every fork its side; one without is unknown, said so.
            JunctionClass.TURN -> {
                val band = judgement.band ?: return NextTurn.Blank(NextTurnBlank.JUNCTION_UNREAD)
                val side = judgement.side ?: return NextTurn.Blank(NextTurnBlank.JUNCTION_UNREAD)
                return NextTurn.Turn(band, side, metersAway, if (side == TurnSide.LEFT) left else right)
            }
            JunctionClass.FORK -> return NextTurn.Fork(judgement.keep ?: return NextTurn.Blank(NextTurnBlank.JUNCTION_UNREAD), metersAway)
            JunctionClass.STRAIGHT_ON -> {
                left += judgement.leftPaths
                right += judgement.rightPaths
            }
            JunctionClass.NO_LEG -> Unit
        }
    }
    return if (trackAhead != null) NextTurn.Blank(NextTurnBlank.TRACK_TURNS_UNKNOWN) else NextTurn.NoTurnLeft
}

/** What the card's top line holds, as E1 needs it: said by the card (Phase 2b) from its own readout. */
enum class NextTurnCardState {
    /** A figure: the next turn's space shows what lies ahead. */
    SHOWS_DISTANCE,
    ARRIVED,
    NO_DISTANCE,
    NO_FIX,
    WITHIN_FIX_CIRCLE,
}

/** E1, the owner: "Blank (Recommended)". Every state of the card but a figure blanks the space, whatever [ahead] is. */
fun nextTurnSpace(card: NextTurnCardState, ahead: NextTurn): NextTurn = when (card) {
    NextTurnCardState.SHOWS_DISTANCE -> ahead
    NextTurnCardState.ARRIVED -> NextTurn.Blank(NextTurnBlank.ARRIVED)
    NextTurnCardState.NO_DISTANCE -> NextTurn.Blank(NextTurnBlank.NO_DISTANCE)
    NextTurnCardState.NO_FIX -> NextTurn.Blank(NextTurnBlank.NO_FIX)
    NextTurnCardState.WITHIN_FIX_CIRCLE -> NextTurn.Blank(NextTurnBlank.WITHIN_FIX_CIRCLE)
}
