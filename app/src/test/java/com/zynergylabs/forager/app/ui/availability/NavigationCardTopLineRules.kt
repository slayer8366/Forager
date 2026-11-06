package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the owner's rulings for the card's top line, worked out here from the drawn card's own
 * measured widths and checked against what it drew. Written from the rulings, not from the app's code, so a slip in either shows:
 * - "Meet at the middle (Recommended)": the distance ends half a word's space (Spacing.xs) left of the left part's middle, the kind
 *   starts as far right of it, on one baseline.
 * - "Shift the middle a little (Recommended)": where the longest distance (the widest of "8888 ft" and "88.8 mi" by trail, "≈ 8888
 *   ft" and "88.8 mi" straight, or the figure shown if wider) doesn't fit left of the middle, the meeting point moves right just
 *   enough for it.
 * - "Option 1 but stack by on top of trail first, then just shorten it to trail, and then drop it down", where the kind doesn't
 *   fit right of the meeting point; "straight" goes to its own line ("trail" alone needs what the stack needs, so it never shows).
 * - "On the distance's line (Recommended)": stacked, both words start at the meeting point, "trail" on the distance's baseline and
 *   "by" just above.
 * - "At the middle (Recommended)": on its own line under the distance, the kind starts at the meeting point, or as near it as fits.
 * - "In the left half", then "Left, crossing as little (Recommended)": with no kind the word ends where the distance would, at the
 *   middle; wider than that, it starts at the left part's left edge.
 * - "Keep the text size the same size": the kind is in the turn words' type in every form.
 * - The planner's call of 2026-11-06 under "Decided for the longest distance the card could show, so it doesn't jump between lines
 *   as you walk": the step is decided for the narrowest the left part can be while the card shows, across every heading and turn
 *   word. At its narrowest the left part is as wide as the widest of the narrowest figure ("8 ft"), the needle and one icon with a
 *   gap (the approach, or a compass status as the crossed-out compass), and the kind, wherever the evening pieces leave it short.
 *
 * Every width is measured in the card's own type ([widthPx]) and compared in whole pixels, as the card lays them out.
 */
abstract class NavigationCardTopLineRules : NavigationCardKindLineScreen() {

    /** What the kind does, as drawn; [ALONE] is a word with no kind ("Arrived", the dash). */
    protected enum class Step { BESIDE, STACKED, OWN_LINE, ALONE }

    /** One reading: what was drawn, what the rulings make of it, and the widths both were worked out on, in pixels. */
    protected class Reading(
        val drawn: Step,
        /** The rulings' step, decided on the narrowest left part ([narrowestPx]). */
        val ruled: Step,
        /** The rulings' step decided on the left part as drawn, without the planner's call, for the report. */
        val atDrawnWidth: Step,
        val leftPartPx: Int,
        val narrowestPx: Int,
        val longestPx: Int?,
        val shiftPx: Int,
        /** How near the room at the narrowest left part is to a step's threshold (the kind's or the stack's width); `null` with no kind. */
        val marginPx: Int?,
        val problems: List<String>,
    )

    private fun stepFor(widthPx: Int, gapPx: Int, longestPx: Int, kindPx: Int, stackPx: Int?): Step {
        val end = minOf(widthPx, maxOf(widthPx / 2 - gapPx / 2, longestPx))
        val room = widthPx - end - gapPx
        return when {
            kindPx <= room -> Step.BESIDE
            stackPx != null && stackPx <= room -> Step.STACKED
            else -> Step.OWN_LINE
        }
    }

    /**
     * Shows [s] with [e] and [facing], and lists each way its top line differs from the rulings, and from [step] and [shifted]
     * where they are given: the step this case is chosen to reach, and whether the meeting point moved right of the middle (by at
     * least 1 dp) or did not move at all. [crosses]: a word with no kind wider than its half (true) or within it (false).
     */
    protected fun topLine(
        where: String,
        s: State,
        e: Evening,
        facing: Facing = Facing.TURN,
        step: Step? = null,
        shifted: Boolean? = null,
        crosses: Boolean? = null,
    ): Reading {
        show(s, e, facing)
        val c = card(where)
        printed(where, s, c)
        val problems = mutableListOf<String>()
        val d = densityPx
        fun px(dp: Dp) = (dp.value * d).roundToInt()
        fun dpOf(p: Int) = (p / d).dp
        fun near(a: Dp, b: Dp) = abs((a - b).value) <= 0.5f
        val left = c.topLine.left
        val widthPx = px(c.topLine.right - c.topLine.left)
        val gapPx = (4 * d).roundToInt()
        // Positive controls: the state is the one named, and the right part is as the case says.
        if (c.figure.text != s.figure) problems += "positive control: the distance reads <${c.figure.text}>, not <${s.figure}>"
        if ((c.pieces.isNotEmpty()) != (e != Evening.NONE)) problems += "positive control: ${c.pieces.size} evening pieces drawn for $e"
        if (abs((c.topLine.right - c.leftPartRight).value) > 0.5f) problems += "positive control: the top line ${c.topLine} is not the left part's width (its right ${c.leftPartRight})"
        val figureEndAtMiddle = widthPx / 2 - gapPx / 2
        val drawn = when {
            c.kindWords.size == 2 -> Step.STACKED
            c.kind == null -> Step.ALONE
            c.kind.bounds.top >= c.figure.bounds.bottom - 0.5.dp -> Step.OWN_LINE
            else -> Step.BESIDE
        }
        if (s.kind == null) {
            if (c.kind != null) problems += "a kind <${c.kind.text}> is drawn where there is none"
            val figurePx = px(c.figure.bounds.right - c.figure.bounds.left)
            val wider = figurePx > figureEndAtMiddle
            if (crosses != null && wider != crosses) problems += "positive control: <${c.figure.text}> is ${dpOf(figurePx)} against its half's ${dpOf(figureEndAtMiddle)}, expected wider=$crosses"
            if (wider) {
                // "Left, crossing as little (Recommended)": from the left part's left edge.
                if (!near(c.figure.bounds.left, left)) problems += "<${c.figure.text}> ${c.figure.bounds}, wider than its half, does not start at the left part's left edge $left"
            } else if (!near(c.figure.bounds.right, left + dpOf(figureEndAtMiddle))) {
                problems += "<${c.figure.text}> ${c.figure.bounds} does not end at the middle, ${left + dpOf(figureEndAtMiddle)} (\"In the left half\")"
            }
            if (c.figure.bounds.bottom > c.turnRowTop + 0.5.dp) problems += "<${c.figure.text}> ${c.figure.bounds} is not above the turn row (top ${c.turnRowTop})"
            return Reading(drawn, Step.ALONE, Step.ALONE, widthPx, widthPx, null, 0, null, problems.map { "$where $s $e: $it" })
        }
        // The kind's type: its own text, a stacked word's, or the turn words' (the same labelMedium) where neither is a text.
        val kindType = c.kind?.takeIf { it.layout != null } ?: c.kindWords.firstOrNull() ?: checkNotNull(c.turn) { "$where: no text in the kind's type" }
        val longestTexts = if (s == State.STRAIGHT) listOf("≈ 8888 ft", "88.8 mi") else listOf("8888 ft", "88.8 mi")
        val longestPx = (longestTexts + c.figure.text).maxOf { widthPx(c.figure, it) }
        val kindPx = widthPx(kindType, s.kind)
        val stackPx = if (s.kind == "by trail") maxOf(widthPx(kindType, "by"), widthPx(kindType, "trail")) else null
        // The narrowest left part: the right part keeps the widest evening piece, and the left part is at least its narrowest need.
        val totalPx = px(c.exit.left - left)
        val dividerPx = d.roundToInt() + 2 * (6 * d).roundToInt()
        val wantRightPx = c.pieces.maxOfOrNull { widthPx(it, it.text) } ?: 0
        val basePx = totalPx - dividerPx - wantRightPx
        val needPx = maxOf(widthPx(c.figure, "8 ft"), 2 * (22 * d).roundToInt() + gapPx, kindPx)
        val narrowestPx = minOf(widthPx, if (needPx > basePx) minOf(needPx, totalPx - dividerPx) else basePx)
        val ruled = stepFor(narrowestPx, gapPx, longestPx, kindPx, stackPx)
        val atDrawn = stepFor(widthPx, gapPx, longestPx, kindPx, stackPx)
        val endMin = minOf(narrowestPx, maxOf(narrowestPx / 2 - gapPx / 2, longestPx))
        val roomPx = narrowestPx - endMin - gapPx
        // How near the narrowest room is to a step's threshold: within a pixel, either step is the rulings' reading.
        val marginPx = listOfNotNull(kindPx, stackPx).minOf { abs(roomPx - it) }
        // Where the line is drawn, on the left part as drawn: the meeting point, moved right only for the longest figure.
        val endPx = minOf(widthPx, maxOf(figureEndAtMiddle, longestPx))
        val kindStart = left + dpOf(endPx + gapPx)
        val shiftPx = endPx - figureEndAtMiddle
        if (step != null && drawn != step) problems += "the kind is drawn $drawn, this case is chosen to reach $step"
        if (drawn != ruled && marginPx > 1) problems += "the kind is drawn $drawn; the rulings make it $ruled (decided on ${dpOf(narrowestPx)}, room ${dpOf(roomPx)})"
        if (shifted == true && shiftPx < (1 * d).roundToInt()) problems += "positive control: the meeting point is ${dpOf(shiftPx)} right of the middle, expected moved"
        if (shifted == false && shiftPx != 0) problems += "positive control: the meeting point is ${dpOf(shiftPx)} right of the middle, expected at it"
        // "the 2.2 mi on the left half": the distance ends at the meeting point.
        if (!near(c.figure.bounds.right, left + dpOf(endPx))) problems += "the distance ${c.figure.bounds} does not end at the meeting point ${left + dpOf(endPx)} (middle ${left + dpOf(figureEndAtMiddle)}, longest ${dpOf(longestPx)})"
        if (c.figure.bounds.bottom > c.turnRowTop + 0.5.dp) problems += "the distance ${c.figure.bounds} is not above the turn row (top ${c.turnRowTop})"
        val kind = c.kind
        if (kind == null) {
            problems += "no kind drawn"
        } else {
            if (kind.text != s.kind) problems += "the kind reads <${kind.text}>, not <${s.kind}>"
            if (kind.bounds.right > c.topLine.right + 0.5.dp) problems += "the kind ${kind.bounds} runs past the left part's right ${c.topLine.right}"
            if (kind.bounds.bottom > c.turnRowTop + 0.5.dp) problems += "the kind ${kind.bounds} is not above the turn row (top ${c.turnRowTop})"
            val typeSp = c.turn?.takeIf { it.text.isNotEmpty() }?.fontSp
            for (word in listOfNotNull(kind.takeIf { it.layout != null }) + c.kindWords) {
                if (typeSp != null && word.fontSp != typeSp) problems += "<${word.text}> is ${word.fontSp} sp, the turn words $typeSp sp"
            }
            when (drawn) {
                Step.BESIDE -> {
                    if (!near(kind.bounds.left, kindStart)) problems += "the kind ${kind.bounds} does not start at the meeting point $kindStart"
                    if (abs((kind.baseline - c.figure.baseline).value) > 0.5f) problems += "the kind's baseline ${kind.baseline} is not the distance's ${c.figure.baseline}"
                }
                Step.STACKED -> {
                    val (upper, lower) = c.kindWords
                    if (upper.text != "by" || lower.text != "trail") problems += "the stack reads <${upper.text}> over <${lower.text}>"
                    for (w in c.kindWords) if (!near(w.bounds.left, kindStart)) problems += "stacked <${w.text}> ${w.bounds} does not start at the meeting point $kindStart"
                    if (abs((lower.baseline - c.figure.baseline).value) > 0.5f) problems += "stacked \"trail\"'s baseline ${lower.baseline} is not the distance's ${c.figure.baseline}"
                    if (upper.bounds.bottom > lower.bounds.top + 0.5.dp) problems += "stacked \"by\" ${upper.bounds} is not above \"trail\" ${lower.bounds}"
                    if (upper.baseline >= lower.baseline) problems += "stacked \"by\"'s baseline ${upper.baseline} is not above \"trail\"'s ${lower.baseline}"
                }
                Step.OWN_LINE -> {
                    val x = left + dpOf(maxOf(0, minOf(endPx + gapPx, widthPx - kindPx)))
                    if (!near(kind.bounds.left, x)) problems += "the kind on its own line ${kind.bounds} does not start at the meeting point or as near it as fits, $x"
                    if (kind.bounds.top < c.figure.bounds.bottom - 0.5.dp) problems += "the kind on its own line ${kind.bounds} is not under the distance ${c.figure.bounds}"
                }
                Step.ALONE -> Unit
            }
        }
        println(
            "RULES|$where|$s|$e|$facing|drawn $drawn|ruled $ruled|at the drawn width $atDrawn|left part ${dpOf(widthPx)}|narrowest ${dpOf(narrowestPx)}|" +
                "longest ${dpOf(longestPx)}|kind ${dpOf(kindPx)}|stack ${stackPx?.let(::dpOf)}|shift ${dpOf(shiftPx)}|room at the narrowest ${dpOf(roomPx)}, at the drawn width ${dpOf(widthPx - endPx - gapPx)}|nearest threshold ${dpOf(marginPx)}|right part wants ${dpOf(wantRightPx)}",
        )
        return Reading(drawn, ruled, atDrawn, widthPx, narrowestPx, longestPx, shiftPx, marginPx, problems.map { "$where $s $e $facing: $it" })
    }
}
