package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.domain.nextturn.nextTurnArrow
import com.zynergylabs.forager.app.domain.nextturn.nextTurnSpace
import com.zynergylabs.forager.app.domain.nextturn.nextTurnWords
import com.zynergylabs.forager.app.domain.nextturn.spokenFigure

/**
 * What the next-turn space draws and says (dispatch 2026-11-06-01, Amendment 1, J, 2b-2 step 1): [arrow] and [figure] side
 * by side, and [spoken] for TalkBack. All three are `null` when the space is blank: it keeps its place, empty (E1).
 */
internal data class NextTurnSpaceShown(val arrow: NextTurnArrow?, val figure: String?, val spoken: String?) {
    companion object {
        val BLANK = NextTurnSpaceShown(null, null, null)
    }
}

/**
 * The space for the card's own [readout] (with [hasFix], as [nextTurnCardState] reads it) and [ahead], the domain's next turn
 * ([com.zynergylabs.forager.app.domain.nextturn.nextTurn]), in the card's [unit].
 *
 * - **E1**, the owner: "Blank (Recommended)": every card state but a figure blanks the space ([nextTurnCardState], then
 *   [nextTurnSpace]).
 * - **E2 and E3**: the way's own blanks (no trail route, the straight line, the track's turns unknown) come from [ahead].
 * - **D1**: a turn's or a fork's figure is its [NextTurn.Turn.metersAway] from the needle's place, in [unit], written as the
 *   top line writes a trail route's figure ([formatDistanceMeters]).
 * - **A3 and E3**: the straight-ahead arrow, shown only on a trail route with no turn left, carries the top line's own
 *   string ([NavigationHudReadout.distanceText]), so the two never disagree.
 * - **G3**: [spoken] from [nextTurnWords]. A figure whose unit cannot be spelled out ([spokenFigure] says `null`) is spoken as
 *   it is written, and logged.
 */
internal fun nextTurnSpaceShown(readout: NavigationHudReadout, hasFix: Boolean, ahead: NextTurn, unit: DistanceUnit): NextTurnSpaceShown {
    val space = nextTurnSpace(nextTurnCardState(readout, hasFix), ahead)
    // A blank space (E1 to E3) has no arrow, and so nothing at all: the arrow is the one gate.
    val arrow = nextTurnArrow(space) ?: return NextTurnSpaceShown.BLANK
    val figure = when (space) {
        is NextTurn.Turn -> formatDistanceMeters(space.metersAway, unit)
        is NextTurn.Fork -> formatDistanceMeters(space.metersAway, unit)
        NextTurn.NoTurnLeft -> readout.distanceText
        is NextTurn.Blank -> null
    }
    val spoken = figure?.let { f -> nextTurnWords(space, spokenDistanceOf(f)) }
    return NextTurnSpaceShown(arrow, figure, spoken)
}

/**
 * The next turn the card's [route] carries (dispatch 2026-11-06-01, Amendment 1, J2: worked out where the line and the progress live
 * and passed "to the card with the route it already receives"): a route ahead's own ([ReturnRoute.Ahead.nextTurn]). Every other
 * route is blank for its own reason: no route at all or [ReturnRoute.Straight] is the straight line (E3, the owner: "Blank"),
 * [ReturnRoute.Pending] is not yet known, and [ReturnRoute.Unavailable] is the held-back route (E1).
 */
internal fun nextTurnOf(route: ReturnRoute?): NextTurn = when (route) {
    is ReturnRoute.Ahead -> route.nextTurn
    null, is ReturnRoute.Straight -> NextTurn.Blank(NextTurnBlank.NO_TRAIL_ROUTE)
    ReturnRoute.Pending -> NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN)
    is ReturnRoute.Unavailable -> NextTurn.Blank(NextTurnBlank.ROUTE_WITHHELD)
}

private fun spokenDistanceOf(figure: String): String = spokenFigure(figure) ?: figure.also {
    android.util.Log.w(NEXT_TURN_SPACE_LOG_TAG, "the next turn's figure has no unit to spell out; TalkBack reads it as written")
}

private const val NEXT_TURN_SPACE_LOG_TAG = "NextTurn"
