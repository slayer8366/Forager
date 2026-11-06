package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.SundownLine
import com.zynergylabs.forager.app.domain.isShown

/**
 * Dispatch 2026-11-05-10, Amendment 1, H5 (RECORD -2181): the navigation card's evening line before sunset. The owner, to "Before
 * sunset the evening part is three lines ("Sunset 4:52 PM", "in 2 h 28 min", "dark 5:23"). Where do status words go then?", typed:
 * "Shorten to Sunset: 4:52 PM ▪︎ Dark: 5:23 PM status words are on the right panel". Then, to "The one-line "Sunset: 4:52 PM ▪ Dark:
 * 5:23 PM" is too wide to sit beside the distance and turn line on most phones at today's text sizes. What should happen?": "Split it
 * at the ▪ (Recommended)", the option reading "Where the one line doesn't fit, it breaks at the ▪ into two lines, still with no
 * countdown. The right side stays about today's width, so the distance, "by trail" and the next-turn arrow keep their room. Status
 * words go under it."
 *
 * The planner's readings, open to the owner:
 * - only the before-sunset form with the countdown changes; "Sun set … · dark in …", "Dark since …" and the start-back time keep their
 *   words ([sundownLineText]);
 * - with no dusk the line is "Sunset: 4:52 PM" alone; a 24-hour clock gives "Sunset: 16:52 ▪︎ Dark: 17:23" (the phone's own format);
 * - the separator is the owner's as typed, U+25AA with U+FE0E (text presentation).
 *
 * The compass strip's own sundown line, out of navigation, is not the card's and is unchanged.
 */
internal fun cardSundownLineText(line: SundownLine, clock: SundownClock): String? = when {
    !line.isShown() -> null
    line is SundownLine.BeforeSunset && line.startBackAtEpochMillis == null -> {
        val sunset = "$CARD_SUNSET_LABEL ${clock.full(line.sunsetAtEpochMillis)}"
        line.civilDuskAtEpochMillis?.let { "$sunset$CARD_EVENING_SEPARATOR$CARD_DARK_LABEL ${clock.full(it)}" } ?: sunset
    }
    else -> sundownLineText(line, clock)
}

internal const val CARD_SUNSET_LABEL = "Sunset:"
internal const val CARD_DARK_LABEL = "Dark:"

/** The owner's separator, "▪︎", with a space either side: where the one line breaks into two (H5, "Split it at the ▪"). */
internal const val CARD_EVENING_SEPARATOR = " ▪︎ "

/**
 * The evening lines broken into the pieces the card stacks, one per line (RECORD -941): at their " · " joins, the sundown line's
 * first, then Back by's ("Sunset 12:41 AM", "in 2 h 28 min", "dark 1:13", "Back by …"). With [breakAtSquare], H5's line also breaks
 * at the owner's ▪︎ ("Sunset: 4:52 PM", "Dark: 5:23 PM"); without it, it stays one piece.
 */
internal fun eveningPieces(sundownLine: String?, backByLine: String?, breakAtSquare: Boolean = true): List<String> =
    listOfNotNull(sundownLine, backByLine)
        .flatMap { line -> line.split(" · ") }
        .flatMap { piece -> if (breakAtSquare) piece.split(CARD_EVENING_SEPARATOR.trim()) else listOf(piece) }
        .map(String::trim)
        .filter(String::isNotEmpty)

/**
 * The widest clock time [clock] writes, one for each hour of a day, by [widthOf]: the evening line's widest on this phone's clock
 * (12- or 24-hour, its own AM and PM), whatever today's times are. The card's figures are tabular, so the hour's digits decide it.
 */
internal fun widestClockTime(clock: SundownClock, widthOf: (String) -> Int): String =
    (0 until 24).map { hour -> clock.full(hour * 3_600_000L + 58 * 60_000L) }.maxBy(widthOf)

/**
 * H5's two forms at their widest on this phone's clock ([widestClockTime]): the one line, and the wider of its two lines once broken.
 * [oneLinePx] decides whether it breaks ([eveningBreaks]); the broken form's [brokenPx] is the right part's width in daytime (the
 * planner's reading of Q3: "size the daytime right panel for the broken form's widest line on this phone's clock").
 */
internal data class CardEveningWidths(val oneLinePx: Int, val brokenPx: Int)

internal fun cardEveningWidths(clock: SundownClock, widthOf: (String) -> Int): CardEveningWidths {
    val time = widestClockTime(clock, widthOf)
    val sunset = "$CARD_SUNSET_LABEL $time"
    val dark = "$CARD_DARK_LABEL $time"
    return CardEveningWidths(oneLinePx = widthOf("$sunset$CARD_EVENING_SEPARATOR$dark"), brokenPx = maxOf(widthOf(sunset), widthOf(dark)))
}

/**
 * Whether H5's line breaks at the ▪︎: it stays one line only where it fits whole beside what the left part needs ([leftNeedPx]) and the
 * divider, in [partsPx]. The planner's reading of what "fits" means: the left part keeps room for the next-turn space the owner has
 * ruled beside the needle (dispatch 2026-11-06-01, A1, about 75 dp at font 1.0), not built yet, so that the choice does not flip when
 * it arrives: [leftNeedPx] includes it ([nextTurnReservePx]).
 */
internal fun eveningBreaks(partsPx: Int, dividerPx: Int, leftNeedPx: Int, evening: CardEveningWidths): Boolean =
    leftNeedPx + dividerPx + evening.oneLinePx > partsPx

/**
 * The room the next-turn space will take beside the needle (dispatch 2026-11-06-01, A1: "the next turn gets its own fixed space
 * beside them"), reserved only for [eveningBreaks]' decision: a gap, its arrow (the needle's size, [arrowPx]), a gap, and its distance,
 * the widest a turn's distance reads in this unit ([turnDistancePx], "88.8 mi" or "88.8 km"). About 75 dp at font 1.0, as that
 * dispatch's Phase 1 measured.
 */
internal fun nextTurnReservePx(gapPx: Int, arrowPx: Int, turnDistancePx: Int): Int = gapPx + arrowPx + gapPx + turnDistancePx
