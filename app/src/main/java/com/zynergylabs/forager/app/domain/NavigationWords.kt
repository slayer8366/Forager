package com.zynergylabs.forager.app.domain

import kotlin.math.roundToInt

/*
 * The navigation display's words (dispatch 2026-09-28-677, data part B; the owner in RECORD -656, "Plain words + labels
 * (Recommended)": turns in words with an arrow, never 0 to 359, written "slight left 10°" since dispatch 2026-10-12-06; the distance says
 * "0.4 mi by trail" or "0.3 mi straight"). Pure, so a band edge or a side swapped is a pinned-literal test failure.
 */

/** How far to turn, in words. The bands are [TURN_SLIGHT_FROM_DEGREES] and the three constants after it. */
enum class TurnBand { AHEAD, SLIGHT, TURN, SHARP, BEHIND }

/** Which way to turn. */
enum class TurnSide { LEFT, RIGHT }

/**
 * A turn as the display words it: its [band], its [side] (`null` for [TurnBand.AHEAD] and [TurnBand.BEHIND], where a side
 * says nothing the arrow does not), and [degrees], the size of the turn, 0 to 180, whole degrees.
 */
data class Turn(val band: TurnBand, val side: TurnSide?, val degrees: Int)

/*
 * **Provisional: the bands are the coder's proposal and a stop for the owner** (dispatch -677: "Define the turn wording's
 * angle bands in your report; they are a stop"). Fixed by the owner's two examples: 10° is "Slight", 45° is a plain turn.
 * Each band runs from its own constant up to, not including, the next one's, on the turn's size rounded to a whole degree,
 * so the words and the number shown beside them can never disagree.
 *
 *   0 to 9    Ahead
 *   10 to 44  Slight left / Slight right
 *   45 to 134 Left / Right
 *   135 to 169 Sharp left / Sharp right
 *   170 to 180 Behind
 */
const val TURN_SLIGHT_FROM_DEGREES = 10
const val TURN_PLAIN_FROM_DEGREES = 45
const val TURN_SHARP_FROM_DEGREES = 135
const val TURN_BEHIND_FROM_DEGREES = 170

/**
 * The turn for a relative bearing, [relativeDegrees] as [relativeBearingDegrees] gives it: clockwise from dead ahead, so
 * under 180 is to the right and over 180 to the left. Any value is accepted and wrapped into a turn of 0 to 180.
 */
fun turnFor(relativeDegrees: Float): Turn {
    val relative = ((relativeDegrees % 360f) + 360f) % 360f
    val toTheRight = relative <= 180f
    val degrees = (if (toTheRight) relative else 360f - relative).roundToInt()
    val band = when {
        degrees < TURN_SLIGHT_FROM_DEGREES -> TurnBand.AHEAD
        degrees < TURN_PLAIN_FROM_DEGREES -> TurnBand.SLIGHT
        degrees < TURN_SHARP_FROM_DEGREES -> TurnBand.TURN
        degrees < TURN_BEHIND_FROM_DEGREES -> TurnBand.SHARP
        else -> TurnBand.BEHIND
    }
    val side = when (band) {
        TurnBand.AHEAD, TurnBand.BEHIND -> null
        else -> if (toTheRight) TurnSide.RIGHT else TurnSide.LEFT
    }
    return Turn(band, side, degrees)
}

/**
 * "slight left 10°", "right 45°", "ahead 4°", "behind 175°": lower case, the degrees after the words with no separator, and the
 * needle drawn after them on the display (dispatch 2026-10-12-06; the owner's mockup and step path, RECORD -932, confirmed in
 * RECORD -934 as superseding -656's "Slight left · 10°" form). The bands are unchanged.
 */
fun turnWords(turn: Turn): String {
    val side = when (turn.side) {
        TurnSide.LEFT -> "left"
        TurnSide.RIGHT -> "right"
        null -> ""
    }
    val words = when (turn.band) {
        TurnBand.AHEAD -> "ahead"
        TurnBand.SLIGHT -> "slight $side"
        TurnBand.TURN -> side
        TurnBand.SHARP -> "sharp $side"
        TurnBand.BEHIND -> "behind"
    }
    return "$words ${turn.degrees}°"
}

/** [turnWords] for a relative bearing: what the display's turn line reads. */
fun turnText(relativeDegrees: Float): String = turnWords(turnFor(relativeDegrees))

/**
 * What a distance on the display measures, said beside it (the owner's "0.4 mi by trail" or "0.3 mi straight"). [words] is
 * what is written after the figure.
 */
enum class DistanceKind(val words: String) {
    /** Along the walked route home ([RouteHome]). */
    BY_TRAIL("by trail"),

    /** The straight line from the walker to the target. */
    STRAIGHT("straight"),
}
