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

    /** In the card's right part, where the evening line goes, landscape at font 2.0 with no evening line (J3 step 2). */
    RIGHT_PART,
}

/**
 * Dispatch 2026-11-06-01, Amendment 1, J3 (RECORD -2183), the owner: "Arrow in the right part", "Own line, card moves up", "Centre
 * the line"; the step path confirmed, "Yes, that's it":
 * 1. portrait at every font, and landscape at fonts 1.0 and 1.3: J2's [decided] place, beside the needle or its own line;
 * 2. landscape at font 2.0 with no evening line: the card's right part ([NextTurnPlace.RIGHT_PART]);
 * 3. landscape at font 2.0 with an evening line: its own line, the card moving up where it must ([navigationCardLift]);
 * J4 ("Follow the largest rule"): steps 2 and 3 at fonts 1.5 and 1.8 too ([isLandscapeLargeText]);
 * 4. decided once per screen, font and clock; only steps 2 and 3 follow the evening line, as the owner's own steps do.
 *
 * Why: in landscape the card sits on the screen's bottom edge (RECORD -953), and at font 2.0 one more line takes its top over the
 * walker's dot (RECORD -766, -953) and pushes the evening line off the card (RECORD -955), measured in the "2b-2 step 2, built" run.
 * [eveningLine] is whether the card has an evening line to show (its pieces), never whether it is drawn, so the decision cannot feed
 * back through RECORD -955's room check.
 */
internal fun nextTurnPlaceNow(
    decided: NextTurnPlace,
    landscapeLargeText: Boolean,
    eveningLine: Boolean,
    /** J5 (stub, tests first): the evening line stepped aside for the route card resting on the card (D42). */
    @Suppress("UNUSED_PARAMETER") eveningStepsAside: Boolean = false,
): NextTurnPlace = when {
    !landscapeLargeText -> decided
    eveningLine -> NextTurnPlace.OWN_LINE
    else -> NextTurnPlace.RIGHT_PART
}

/**
 * J3: the case steps 2 and 3 name, landscape at font 2.0 (the largest Android offers; anything larger counts with it). J4 (RECORD -2183,
 * the owner: "Follow the largest rule"): landscape at fonts 1.5 and 1.8 too.
 */
internal fun isLandscapeLargeText(landscape: Boolean, fontScale: Float): Boolean = landscape && fontScale >= LANDSCAPE_LARGE_TEXT_FONT_SCALE

/** J3's "font 2.0", and by J4 ("Follow the largest rule") 1.5 and 1.8: Android's sizes after 1.3. Was 2.0 (J3 alone). */
private const val LANDSCAPE_LARGE_TEXT_FONT_SCALE = 1.5f

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
