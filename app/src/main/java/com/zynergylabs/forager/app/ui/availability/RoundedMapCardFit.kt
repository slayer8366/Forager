package com.zynergylabs.forager.app.ui.availability

/**
 * Dispatch 2026-10-12-08 (RECORD -945, -950): the navigation info's two parts, and what gives way when they do not fit its width.
 * The pure half: every choice is a function of measured widths, so a wrong order is a pinned-literal test failure
 * (`RoundedMapCardFitTest`), not a visual one. Replaces dispatch 2026-10-12-06's three-column fit: the middle column (Alt over the
 * compass) and the grid reference's place are gone, since height and the grid reference are on the compass strip while navigating
 * (RECORD -950), and the kind word is a line of its own under the turn line (RECORD -945), so it no longer competes for width.
 *
 * The parts, as the owner's approved drawing has them:
 * - left: the distance; the turn words, the needle, the north arrow and the heading on one line ("ahead 1° ▲ ▲ 134° SE"); the
 *   kind word ("by trail");
 * - a divider, always (RECORD -951, even with no evening stack);
 * - right: the evening stack (RECORD -941), as wide as its widest piece.
 *
 * The order, what is left of the owner's (RECORD -934, -936) once the moot steps are gone (RECORD -950: "the remaining fit steps
 * keep their order"):
 * 1. the turn words shrink to the degrees and the needle ("169° ▲");
 * 2. "within 16 ft" takes its short form, "≤ 16 ft" ([distanceShortForm], RECORD -768, -936).
 * Nothing past that (RECORD -950: "Invent no new trims"): where even step 2 does not fit, [NavigationInfoFit.overflows] says so, it
 * is logged, and the coder's report gives the numbers. The left part then stays whole and the evening pieces wrap in what is left.
 *
 * A compass status that is words, not a reading ("Compass unavailable"), takes the crossed-out compass ([StripHeadingStatusIcon]'s
 * icon, RECORD -735) in place of the north arrow and its words where they do not fit (RECORD -934). It is tried at each step before
 * the next step is taken, since the icon removes nothing a reading would show.
 */
internal data class NavigationInfoFit(
    /** The turn as drawn before the needle: the whole words, the degrees alone, or "" (the needle alone). */
    val turnText: String,
    /** Whether the compass status is drawn as the crossed-out compass rather than the north arrow and its words. */
    val headingAsIcon: Boolean,
    /** Step 2: the figure is drawn in its short form ("≤ 16 ft"). */
    val figureShort: Boolean = false,
    /** Whether even the last step left the parts wider than the room ([NavigationInfoWidths.availablePx]): reported, not trimmed further. */
    val overflows: Boolean = false,
    /** What the left part needs at the chosen step, in pixels. */
    val leftPx: Int = 0,
    /** What the right part keeps: the widest evening piece, 0 with none. */
    val rightPx: Int = 0,
)

/**
 * The measured widths the fit is decided on, in pixels. `null` for a part that is not there in this state. The figure, the kind and
 * the heading are the texts the display would draw; [turnWholePx] and [turnShortPx] the turn's two forms (`null` where the form does
 * not exist).
 */
internal data class NavigationInfoWidths(
    /** What the two parts and the divider between them may take: the info less its padding and the X. */
    val availablePx: Int,
    /** The divider with the gap on either side of it. */
    val dividerPx: Int,
    /** The gap between the turn words, the needle, the north arrow and the heading. */
    val gapPx: Int,
    /** The needle's and the north arrow's square. */
    val arrowPx: Int,
    val figurePx: Int,
    /** The figure's short form ("≤ 16 ft"), `null` where it has none. */
    val figureShortPx: Int? = null,
    val kindPx: Int?,
    val turnWholePx: Int?,
    val turnShortPx: Int?,
    /** The heading's text: a reading ("134° SE") or a status's words; 0 where none is drawn (no fix, only the dash). */
    val headingPx: Int,
    /** True when the heading is a status in words rather than a reading; only a status may become the crossed-out icon. */
    val headingIsStatus: Boolean,
    /** The crossed-out compass's square. */
    val headingIconPx: Int,
    /** The widest of the evening pieces stacked in the right part (RECORD -941); `null` with none. */
    val eveningPiecePx: Int? = null,
)

private enum class TurnForm { WHOLE, SHORT }
private data class Step(val turn: TurnForm, val figureShort: Boolean)

internal fun navigationInfoFit(w: NavigationInfoWidths, turnWhole: String, turnShort: String?): NavigationInfoFit {
    fun turnPx(form: TurnForm): Int {
        val words = when (form) {
            TurnForm.WHOLE -> w.turnWholePx
            TurnForm.SHORT -> w.turnShortPx ?: w.turnWholePx
        }
        return words?.let { it + w.gapPx + w.arrowPx } ?: w.arrowPx
    }
    fun arrowLinePx(s: Step, icon: Boolean): Int {
        val compass = if (icon) w.headingIconPx else w.arrowPx + if (w.headingPx > 0) w.gapPx + w.headingPx else 0
        return turnPx(s.turn) + w.gapPx + compass
    }
    fun leftPx(s: Step, icon: Boolean): Int {
        val figure = if (s.figureShort) w.figureShortPx ?: w.figurePx else w.figurePx
        return maxOf(figure, arrowLinePx(s, icon), w.kindPx ?: 0)
    }
    val rightPx = w.eveningPiecePx ?: 0
    fun total(s: Step, icon: Boolean): Int = leftPx(s, icon) + w.dividerPx + rightPx
    val steps = listOf(
        Step(turn = TurnForm.WHOLE, figureShort = false),
        Step(turn = TurnForm.SHORT, figureShort = false),
        Step(turn = TurnForm.SHORT, figureShort = true),
    )
    val tries = steps.flatMap { step -> if (w.headingIsStatus) listOf(step to false, step to true) else listOf(step to false) }
    val chosen = tries.firstOrNull { (s, icon) -> total(s, icon) <= w.availablePx }
    val (step, icon) = chosen ?: tries.last()
    return NavigationInfoFit(
        turnText = when (step.turn) {
            TurnForm.WHOLE -> turnWhole
            TurnForm.SHORT -> turnShort ?: turnWhole
        },
        headingAsIcon = icon,
        figureShort = step.figureShort && w.figureShortPx != null,
        overflows = chosen == null,
        leftPx = leftPx(step, icon),
        rightPx = rightPx,
    )
}
