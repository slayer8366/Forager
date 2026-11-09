package com.zynergylabs.forager.app.ui.availability

/**
 * Dispatch 2026-10-12-08 (RECORD -945, -950): the navigation info's two parts, and what gives way when they do not fit its width.
 * The pure half: every choice is a function of measured widths, so a wrong order is a pinned-literal test failure
 * (`RoundedMapCardFitTest`), not a visual one. Replaces dispatch 2026-10-12-06's three-column fit: the middle column (Alt over the
 * compass) and the grid reference's place are gone, since height and the grid reference are on the compass strip while navigating
 * (RECORD -950).
 *
 * The parts:
 * - left: the distance and its kind (`NavigationCardTopLine.kt`); the turn line, the degrees in a fixed space and the needle;
 * - a divider, always (RECORD -951, even with no evening stack);
 * - right: the evening stack (RECORD -941), as wide as its widest piece; since Amendment 1, H2 and H5, with the status words under it,
 *   and never narrower than the evening line's widest on this phone's clock ([NavigationInfoWidths.rightFloorPx]), evening line or
 *   none, so the parts keep their places when it comes.
 *
 * Dispatch 2026-11-05-10, Amendment 1 (RECORD -2181):
 * - G2, the owner: "Drop the words, keep the degrees (Recommended)": the turn line reads "109°", never "right 109°", so the first of
 *   the old steps ("the turn words shrink to the degrees and the needle", RECORD -934) has nothing left to shrink and is gone;
 * - H1, the owner: "Keep the compass strip the exact same when in navigation mode as out; so keep the north compass out of the
 *   navigation panel": the north arrow and the heading are the strip's, so the line no longer holds them, nor the crossed-out compass
 *   that stood in for a compass status there (RECORD -934);
 * - G3, "The turn line": "The degrees, the needle, the north arrow and the heading each sit in a fixed space, so the compass never
 *   jumps" (the north arrow's and the heading's spaces went with H1): the degrees' space is as wide as the widest degrees the line can
 *   read ([NavigationInfoWidths.turnSpacePx]), held whether or not degrees are shown, so the line is one width for the walk.
 *
 * What is left of the owner's order (RECORD -934, -936, -950): "within 16 ft" takes its short form, "≤ 16 ft" ([distanceShortForm],
 * RECORD -768, -936). Nothing past that (RECORD -950: "Invent no new trims"): where even that does not fit,
 * [NavigationInfoFit.overflows] says so, it is logged, and the left part stays whole while the evening pieces wrap in what is left.
 */
internal data class NavigationInfoFit(
    /** The turn as drawn before the needle: the degrees ("109°"), or a word in their place ("Target"), or "" (the needle alone). */
    val turnText: String,
    /** The figure is drawn in its short form ("≤ 16 ft"). */
    val figureShort: Boolean = false,
    /** Whether even the last step left the parts wider than the room ([NavigationInfoWidths.availablePx]): reported, not trimmed further. */
    val overflows: Boolean = false,
    /** What the left part needs at the chosen step, in pixels. */
    val leftPx: Int = 0,
    /** What the right part keeps: the widest evening piece, 0 with none. */
    val rightPx: Int = 0,
    /** G3: the degrees' fixed space on the turn line, in pixels ([NavigationInfoWidths.turnSpacePx]). */
    val turnSpacePx: Int = 0,
    /**
     * Dispatch 2026-11-05-10 (RECORD -2181): where the kind word goes on the left part and the figure the meeting point is set for,
     * decided once the steps above are taken ([topLinePlace]); the steps above never read it, so the turn line and the evening stack
     * are as before.
     */
    val topLine: TopLinePlace = TopLinePlace.NONE,
)

/**
 * The measured widths the fit is decided on, in pixels. `null` for a part that is not there in this state. The figure and the kind
 * are the texts the display would draw.
 */
internal data class NavigationInfoWidths(
    /** What the two parts and the divider between them may take: the info less its padding and the X. */
    val availablePx: Int,
    /** The divider with the gap on either side of it. */
    val dividerPx: Int,
    /** The gap between the degrees' space and the needle. */
    val gapPx: Int,
    /** The needle's square. */
    val arrowPx: Int,
    val figurePx: Int,
    /** The figure's short form ("≤ 16 ft"), `null` where it has none. */
    val figureShortPx: Int? = null,
    /** G3: the widest figure the walk could show ([longestCardFigurePx]); the left part is never sized narrower. 0 counts nothing. */
    val longestFigurePx: Int = 0,
    val kindPx: Int?,
    /** G3: the degrees' space, the widest of what the turn line can draw there ([widestTurnSpacePx]). */
    val turnSpacePx: Int,
    /** The widest of the evening pieces stacked in the right part (RECORD -941); `null` with none. */
    val eveningPiecePx: Int? = null,
    /**
     * Amendment 1, H2 and H5: the right part's width with no evening piece wider, kept in daytime too, where the status words sit:
     * the evening line's widest on this phone's clock ([CardEvening.rightFloorPx]). 0 keeps no width.
     */
    val rightFloorPx: Int = 0,
)

/** G3: the widest of the degrees the turn line can read, "0°" to "180°" ([com.zynergylabs.forager.app.domain.turnText]), by [widthOf]. */
internal fun widestTurnDegreesPx(widthOf: (String) -> Int): Int = (0..180).maxOf { widthOf("$it°") }

/** The words the turn line draws in the degrees' space in place of degrees: with no fix for 5 minutes, and with no target. */
internal const val TURN_LINE_LOST_WORD = "Target"
internal const val TURN_LINE_NO_TARGET_WORD = "No target"

/**
 * Amendment 1, E18 (RECORD -2181): the degrees' space, the widest of everything the turn line draws in it, "0°" to "180°"
 * ([widestTurnDegreesPx]), [TURN_LINE_LOST_WORD] and [TURN_LINE_NO_TARGET_WORD], in every state. E17 (the S22 check on build 4825: at
 * font 2.0 the greyed needle overlapped "Target" by about 8 dp) had drawn the needle half the word's overhang further right where a word
 * replaced the degrees, so it moved 6 to 32 dp at that change. The owner, to "Keep that, or size the space for "No target" all the time
 * so the needle never moves?": "Never moves" ("The degrees' space is always as wide as "No target", so the needle stays put; the card's
 * left part is a little tighter in every case"). So the word fits its space, clears the needle by the line's own gap, and the needle
 * keeps one place; E17's offset went.
 */
internal fun widestTurnSpacePx(widthOf: (String) -> Int): Int =
    maxOf(widestTurnDegreesPx(widthOf), widthOf(TURN_LINE_LOST_WORD), widthOf(TURN_LINE_NO_TARGET_WORD))

/** G3 with H1: the turn line's width, the degrees' space, a gap and the needle, the same in every state. */
internal fun turnLinePx(w: NavigationInfoWidths): Int = w.turnSpacePx + w.gapPx + w.arrowPx

internal fun navigationInfoFit(w: NavigationInfoWidths, turnText: String): NavigationInfoFit {
    fun leftPx(figureShort: Boolean): Int {
        val figure = if (figureShort) w.figureShortPx ?: w.figurePx else w.figurePx
        return maxOf(figure, w.longestFigurePx, turnLinePx(w), w.kindPx ?: 0)
    }
    val rightPx = maxOf(w.rightFloorPx, w.eveningPiecePx ?: 0)
    fun total(figureShort: Boolean): Int = leftPx(figureShort) + w.dividerPx + rightPx
    val chosen = listOf(false, true).firstOrNull { total(it) <= w.availablePx }
    val figureShort = chosen ?: true
    return NavigationInfoFit(
        turnText = turnText,
        figureShort = figureShort && w.figureShortPx != null,
        overflows = chosen == null,
        leftPx = leftPx(figureShort),
        rightPx = rightPx,
        turnSpacePx = w.turnSpacePx,
    )
}
