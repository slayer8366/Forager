package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeProgress

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
 * `null` before the first fix places them). D1: the figure counts from the needle's place, never below 0, and a turn is
 * passed only when the walker's place passes its node.
 */
fun nextTurn(way: NextTurnWay, progress: WayHomeProgress?, current: LatLng?): NextTurn = NextTurn.NoTurnLeft

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
fun nextTurnSpace(card: NextTurnCardState, ahead: NextTurn): NextTurn = ahead
