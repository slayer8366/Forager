package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.SundownLine

/**
 * Dispatch 2026-11-06-01, Amendment 1, J2 (RECORD -2183), the owner, after the fit stop: "Own line where needed (Recommended)", and the
 * step path confirmed, "Yes, that's it": the next turn sits beside the needle where it fits (the card keeps its height); on its own line
 * under the turn line where it doesn't (the card one line taller); decided once per screen and text size, never flipping while
 * walking, as H8 decides the status words ([statusBelowTheParts]).
 *
 * **What "fits" is measured against.** The left part is what the right part leaves of the card, and the right part widens with the
 * evening line: on the S22 at normal text the left part is 207.7 dp with no evening line and 189.7 with "Dark since 10:23 PM" (fit
 * probe run 42389607585). A decision on the left part as drawn would flip as the evening came. So it is made on the left part at its
 * narrowest over every evening line the card can show on this phone's clock ([cardEveningPiecesAtWidest]): where the next turn fits
 * there, it fits in every state, and nothing moves when the sun sets.
 */
internal enum class NextTurnPlace {
    /** In its own space right of the needle, on the turn line, the whole line centred in the left part (A1). */
    BESIDE_NEEDLE,

    /** On its own line under the turn line, centred in the left part (J2, "Own line where needed"). */
    OWN_LINE,
}

/**
 * J2: where the next turn goes for this window and font. The left part at its narrowest is what the parts ([partsPx], less the divider)
 * leave beside the right part at its widest: its daytime width ([rightFloorPx]) or the widest evening piece it can stack
 * ([widestEveningPiecePx]); never narrower than the left part's own widest need without the next turn ([leftNeedPx]), where the card's
 * overflow rule keeps the left part whole (RECORD -941). The next turn is beside the needle where the turn line ([turnLinePx]), a gap
 * ([gapPx]) and the next turn's own space ([nextTurnSpacePx]) fit in that.
 */
internal fun nextTurnPlace(
    partsPx: Int,
    dividerPx: Int,
    leftNeedPx: Int,
    rightFloorPx: Int,
    widestEveningPiecePx: Int,
    turnLinePx: Int,
    gapPx: Int,
    nextTurnSpacePx: Int,
): NextTurnPlace {
    val narrowestLeftPx = maxOf(leftNeedPx, partsPx - dividerPx - maxOf(rightFloorPx, widestEveningPiecePx))
    return if (turnLinePx + gapPx + nextTurnSpacePx <= narrowestLeftPx) NextTurnPlace.BESIDE_NEEDLE else NextTurnPlace.OWN_LINE
}

/**
 * Every piece the card's right part can stack (RECORD -941: one per line, [eveningPieces]), at each hour of a day on [clock], with H5's
 * line broken at the ▪︎ or not as [breaks] says: the card's own evening line in each of its forms ([cardSundownLineText]: before
 * sunset, with a start-back time to come or gone, after sunset with dusk 59 minutes off, dark since, the sun staying down) and the
 * Back by line ([backBySetText]). Through the card's own formatters, so a change of words there changes the decision here.
 */
internal fun cardEveningPiecesAtWidest(clock: SundownClock, breaks: Boolean): List<String> = (0 until 24).flatMap { hour ->
    val at = hour * HOUR_MILLIS + 58 * MINUTE_MILLIS
    val lines = listOf(
        SundownLine.BeforeSunset(at - MINUTE_MILLIS, at, at, null),
        SundownLine.BeforeSunset(at - MINUTE_MILLIS, at, at, at),
        SundownLine.BeforeSunset(at, at, at, at),
        SundownLine.AfterSunset(at, at, at + 59 * MINUTE_MILLIS),
        SundownLine.DarkSince(at),
        SundownLine.SunStaysDown,
    ).mapNotNull { cardSundownLineText(it, clock) }
    (lines + backBySetText(at, clock)).flatMap { eveningPieces(it, null, breaks) }
}.distinct()

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
