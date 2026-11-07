package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the owner's rulings for the card's top line, worked out here from the drawn card's own
 * measured widths and checked against what it drew. Written from the rulings, not from the app's code, so a slip in either shows:
 * - Amendment 1, G3, "The top line": ""616 ft" centred in the left half and "by trail" centred in the right half, in place of meeting
 *   at the middle." The planner's reading: the line is divided half a word's space (Spacing.xs) either side of the left part's
 *   middle; where the longest distance (the widest of "8888 ft" and "88.8 mi" by trail, "≈ 8888 ft" and "88.8 mi" straight, or the
 *   figure shown if wider) doesn't fit its half, the distance's space grows to it ("Shift the middle a little (Recommended)" sized
 *   the meeting point the same way) and the kind takes the rest, on one baseline.
 * - Amendment 1, H14, the owner: "There's a space between the 1.5 mi and the by trail. It used to not be there before 498 to 500.
 *   Please put it back to where they're next to each other", and "(a) Fixed, distance pushed right" (the option read: "The distance
 *   hugs the right edge of its own window and "by trail" sits right after it. They're always side by side, and "by trail" never
 *   moves; a short distance leaves room on the left instead"), and for "straight", "Yes, same". The planner's reading: the
 *   distance's window keeps its width for the widest distance; the distance ends at the window's end, and the kind starts the word
 *   gap (Spacing.xs) after it. So beside, the distance ends where its space ends and the kind starts where its space starts. Was
 *   (G3): each centred in its space, ""616 ft" centred in the left half and "by trail" centred in the right half" ("the distance …
 *   is not centred in its space", "the kind … is not centred in its space"); before that, "Meet at the middle (Recommended)", with
 *   the same places H14 gives back.
 * - Amendment 1, H15, the owner, to "With the correct figures, where should "98 ft by trail" sit?": "Centre them together" (the
 *   option read: "The widest distance plus "by trail" centred as one block: "by trail" moves about 20 dp right (5 to 26 dp depending
 *   on screen and font), the distance starts further in from the left"). So beside, the block made of the distance's window (the
 *   widest distance exactly), the word gap and the kind is centred in the left part; the distance ends at the window's end and the
 *   kind starts the gap after it, so neither the kind nor the distance's right edge moves as the distance counts down. Stacked, the
 *   stack stands for the kind in the block (the coder's reading, disclosed). Was (H14): the window was the left half less half the
 *   gap, or the widest distance where wider, starting at the left part's left edge.
 * - "Option 1 but stack by on top of trail first, then just shorten it to trail, and then drop it down", where the kind doesn't
 *   fit its space; "straight" goes to its own line ("trail" alone needs what the stack needs, so it never shows).
 * - Stacked, H14 and H15 as above: the stack starts the word gap after the centred block's window; its two words each
 *   centred in it (G3's reading), so "trail", the wider, starts there too; "trail" still on the distance's baseline ("On the
 *   distance's line (Recommended)") and "by" just above. Was (G3): the stack centred in the kind's space; before that (A6), both
 *   words start at the meeting point.
 * - On its own line, the planner's reading of Q4: each line is its space, the distance centred on the top line and the kind on its
 *   own line under it. Was ("At the middle (Recommended)"): the distance ending at the meeting point and the kind starting there,
 *   or as near it as fits.
 * - B3, "Arrived is simply centered with no extras", and B4, "Centre it like Arrived (Recommended)": with no kind the word or mark
 *   ("Arrived", the dash) is centred in the left part, its centre at the turn row's. Was "In the left half", then "Left, crossing as
 *   little (Recommended)": ending at the middle, or starting at the left part's left edge where wider than its half.
 * - "Keep the text size the same size": the kind is in the turn words' type in every form.
 * - "Decided for the longest distance the card could show, so it doesn't jump between lines as you walk": the step is decided on
 *   the left part as drawn, which since G3 and H1 is one width for the walk: the right part keeps the widest evening piece, and never
 *   less than the broken evening line's wider line on the clock ([eveningFloorPx], H5 and the planner's reading of Q3); where that
 *   leaves the left part short, it is as wide as the widest figure either kind could show. Until G3 the step was decided for the
 *   narrowest the left part could be across every heading and turn word (the planner's call D1, 2026-11-06), since the left part
 *   then followed the turn row.
 *
 * Every width is measured in the card's own type ([widthPx]) and compared in whole pixels, as the card lays them out.
 */
abstract class NavigationCardTopLineRules : NavigationCardKindLineScreen() {

    /** What the kind does, as drawn; [ALONE] is a word with no kind ("Arrived", the dash). */
    protected enum class Step { BESIDE, STACKED, OWN_LINE, ALONE }

    /** One reading: what was drawn, what the rulings make of it, and the widths both were worked out on, in pixels. */
    protected class Reading(
        val drawn: Step,
        /** The rulings' step, decided on the left part as drawn. */
        val ruled: Step,
        val leftPartPx: Int,
        val longestPx: Int?,
        val shiftPx: Int,
        /** How near the room at the narrowest left part is to a step's threshold (the kind's or the stack's width); `null` with no kind. */
        val marginPx: Int?,
        val problems: List<String>,
        /** The turn words and the heading as drawn, for a case's positive controls. */
        val turn: String = "",
        val heading: String? = null,
        /** The needle's left edge, so a case can say it did not move (G3, "The turn line"). */
        val needleLeft: Dp = 0.dp,
        /** The evening pieces as drawn, for a case's positive controls (H12). */
        val pieces: List<String> = emptyList(),
        /** H14: the distance's bounds and the kind's (the stack's, stacked; `null` with none), and the left part's left and right edges. */
        val figure: DpRect? = null,
        val kind: DpRect? = null,
        val leftPart: Pair<Dp, Dp>? = null,
        /** H14 and H15: the distance's window's end, as the rulings work it out on the left part as drawn (the centred block's). */
        val windowEnd: Dp? = null,
    )

    /**
     * The degrees' fixed space (G3), in pixels in [like]'s type: since Amendment 1, E18 (the owner: "Never moves"), the widest of its
     * contents, "0°" to "180°", "Target" and "No target", in every state. Was the widest degrees alone.
     */
    private fun turnSpacePx(like: Laid): Int = maxOf((0..180).maxOf { widthPx(like, "$it°") }, widthPx(like, "Target"), widthPx(like, "No target"))

    /** The turn line's width: the degrees' fixed space, 4 dp and the 22 dp needle (G3 with H1), in pixels; `null` unmeasurable. */
    protected fun turnLineWidthPx(c: Card): Int? {
        val like = c.turn?.takeIf { it.layout != null } ?: return null
        return turnSpacePx(like) + (26 * densityPx).roundToInt()
    }

    /** Where the centred turn line puts the needle's left edge in a left part whose middle is [middle] (G3 with H1); `null` unmeasurable. */
    protected fun turnLineNeedleLeft(c: Card, middle: Dp): Dp? {
        val like = c.turn?.takeIf { it.layout != null } ?: return null
        return middle + (turnSpacePx(like) / densityPx).dp / 2 - 9.dp
    }

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
     * [eveningLeftOut]: Amendment 1, H12 (the owner: "(a) Leave out "Dark since"", the option reading "In that one case the evening
     * line doesn't show; "by trail" keeps its own line"): the evening piece the card leaves out here, as it would read; no piece is
     * drawn, and the card is laid out as with it, its right part keeping that piece's width, so nothing else moves as it goes.
     */
    protected fun topLine(
        where: String,
        s: State,
        e: Evening,
        facing: Facing = Facing.TURN,
        step: Step? = null,
        shifted: Boolean? = null,
        crosses: Boolean? = null,
        eveningLeftOut: String? = null,
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
        if (eveningLeftOut != null) {
            if (e == Evening.NONE) problems += "positive control: an evening piece <$eveningLeftOut> left out with no evening time"
            // H12: the line is left out here.
            if (c.pieces.isNotEmpty()) problems += "the evening line shows (${c.pieces.map { it.text }}) where H12 leaves it out"
        } else if ((c.pieces.isNotEmpty()) != (e != Evening.NONE)) {
            problems += "positive control: ${c.pieces.size} evening pieces drawn for $e"
        }
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
            // Positive control on the case: a word wider than its half, or within it. These are the cases that told the earlier
            // rulings apart ("In the left half", then "Left, crossing as little"), so a centred word is checked against both.
            val wider = figurePx > figureEndAtMiddle
            if (crosses != null && wider != crosses) problems += "positive control: <${c.figure.text}> is ${dpOf(figurePx)} against its half's ${dpOf(figureEndAtMiddle)}, expected wider=$crosses"
            // B3 and B4: centred in the left part, its centre at the left part's middle, which is the turn row's centre. Was (B1, then
            // B2): ending at the middle, or, wider than its half, starting at the left part's left edge.
            val middle = (c.topLine.left + c.topLine.right) / 2
            val centre = (c.figure.bounds.left + c.figure.bounds.right) / 2
            // The turn line is centred in the left part: the degrees' space, 4 dp and the 22 dp needle (G3), so the needle starts half
            // the space less 9 dp right of the middle. Was: the turn row's centre is the left part's middle.
            val needleAt = turnLineNeedleLeft(c, middle)
            if (needleAt != null && abs((c.needle.left - needleAt).value) > 1f) problems += "positive control: the needle ${c.needle} is not where the centred turn line puts it, $needleAt"
            if (!near(centre, middle)) problems += "<${c.figure.text}> ${c.figure.bounds} is not centred in the left part: its centre $centre, the left part's middle $middle"
            if (c.figure.bounds.bottom > c.turnRowTop + 0.5.dp) problems += "<${c.figure.text}> ${c.figure.bounds} is not above the turn row (top ${c.turnRowTop})"
            return Reading(drawn, Step.ALONE, widthPx, null, 0, null, problems.map { "$where $s $e: $it" })
        }
        // The kind's type: its own text, a stacked word's, or the turn words' (the same labelMedium) where neither is a text.
        val kindType = c.kind?.takeIf { it.layout != null } ?: c.kindWords.firstOrNull() ?: checkNotNull(c.turn) { "$where: no text in the kind's type" }
        val longestTexts = if (s.straight) listOf("≈ 8888 ft", "88.8 mi") else listOf("8888 ft", "88.8 mi")
        val longestPx = (longestTexts + c.figure.text).maxOf { widthPx(c.figure, it) }
        val kindPx = widthPx(kindType, s.kind)
        val stackPx = if (s.kind == "by trail") maxOf(widthPx(kindType, "by"), widthPx(kindType, "trail")) else null
        // The right part keeps the widest evening piece, and never less than the evening floor (H5, Q3). The two parts end where the
        // X's 48 dp touch box starts, 24 dp left of its centre (its tagged node is the 40 dp icon).
        val totalPx = px((c.exit.left + c.exit.right) / 2 - 24.dp - left)
        val dividerPx = d.roundToInt() + 2 * (6 * d).roundToInt()
        // H8: where the status space (the daytime width, or what the left part's widest need leaves) is narrower than the status words'
        // widest word, the status words take their own line under the parts and the right part keeps no daytime width.
        val floorPx = eveningFloorPx(kindType)
        val leftNeedPx = maxOf(listOf("≈ 8888 ft", "8888 ft", "88.8 mi").maxOf { widthPx(c.figure, it) }, turnLineWidthPx(c) ?: 0, listOf("by trail", "straight").maxOf { widthPx(kindType, it) })
        val widestWordPx = cardStatusTexts().flatMap { it.split(' ') }.filter { it.isNotEmpty() && it != "·" }.maxOf { widthPx(kindType, it) }
        val statusBelow = minOf(floorPx, totalPx - dividerPx - leftNeedPx) < widestWordPx
        // H12: a piece left out keeps its width ([eveningLeftOut]), in the evening's tabular type.
        val leftOutPx = eveningLeftOut?.let { widthPx(kindType, it, tabular = true) } ?: 0
        val wantRightPx = maxOf(if (statusBelow) 0 else floorPx, c.pieces.maxOfOrNull { widthPx(it, it.text) } ?: 0, leftOutPx)
        val basePx = totalPx - dividerPx - wantRightPx
        // Positive controls on that arithmetic: the left part is never narrower than what the evening piece leaves it, and where it
        // is wider (the fit overflowed) the right part is narrower than its widest piece, so that piece wraps.
        if (widthPx < basePx - 1) problems += "positive control: the left part ${dpOf(widthPx)} is narrower than the ${dpOf(basePx)} the evening piece leaves"
        // With no piece (daytime, H5's floor alone), there is nothing to wrap.
        if (widthPx > basePx + 1 && c.pieces.isNotEmpty() && c.pieces.none { px(it.bounds.right - it.bounds.left) < widthPx(it, it.text) - 1 }) {
            problems += "positive control: the left part ${dpOf(widthPx)} is wider than the ${dpOf(basePx)} the evening piece leaves, but no piece wraps"
        }
        val ruled = stepFor(widthPx, gapPx, longestPx, kindPx, stackPx)
        // The step is still decided where the line is divided (the middle, moved right only for the longest figure).
        val endPx = minOf(widthPx, maxOf(figureEndAtMiddle, longestPx))
        val roomPx = widthPx - endPx - gapPx
        // H15: beside or stacked, the block (the window, the widest distance exactly; the gap; the kind, or the stack) is centred in
        // the left part. Was (H14) the window ending at endPx. The step is drawn as decided, so the stack is the stacked block's kind.
        val blockKindPx = if (drawn == Step.STACKED) stackPx ?: kindPx else kindPx
        val windowEndPx = maxOf(0, (widthPx - (longestPx + gapPx + blockKindPx)) / 2) + longestPx
        val kindStart = left + dpOf(windowEndPx + gapPx)
        // How near the room is to a step's threshold: within a pixel, either step is the rulings' reading.
        val marginPx = listOfNotNull(kindPx, stackPx).minOf { abs(roomPx - it) }
        val shiftPx = endPx - figureEndAtMiddle
        if (step != null && drawn != step) problems += "the kind is drawn $drawn, this case is chosen to reach $step"
        if (drawn != ruled && marginPx > 1) problems += "the kind is drawn $drawn; the rulings make it $ruled (room ${dpOf(roomPx)})"
        if (shifted == true && shiftPx < (1 * d).roundToInt()) problems += "positive control: the distance's space is ${dpOf(shiftPx)} wider than its half, expected wider"
        if (shifted == false && shiftPx != 0) problems += "positive control: the distance's space is ${dpOf(shiftPx)} wider than its half, expected its half"
        val figureCentre = (c.figure.bounds.left + c.figure.bounds.right) / 2
        if (drawn == Step.OWN_LINE) {
            // Q4: the distance has the top line to itself, and is centred on it.
            if (!near(figureCentre, c.middle)) problems += "the distance ${c.figure.bounds} is not centred on the top line, its kind on its own line: its centre $figureCentre, the middle ${c.middle}"
        } else if (!near(c.figure.bounds.right, left + dpOf(windowEndPx))) {
            // H14: "The distance hugs the right edge of its own window"; H15: that window is the widest distance, at the start of the
            // centred block. Was (H14) the window from the left part's left edge to the middle less half the gap, or the widest
            // distance where wider; before that (G3) its centre at the space's, ""616 ft" centred in the left half".
            problems += "the distance ${c.figure.bounds} does not end at the centred block's window end ${left + dpOf(windowEndPx)} (window ${left + dpOf(windowEndPx - longestPx)}..${left + dpOf(windowEndPx)}, longest ${dpOf(longestPx)}, left part ${left}..${c.topLine.right})"
        }
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
                    // H14: ""by trail" sits right after it": the word gap after the window's end. Was (G3) its centre at the centre of
                    // the space, ""by trail" centred in the right half".
                    if (!near(kind.bounds.left, kindStart)) problems += "the kind ${kind.bounds} does not start at $kindStart, the word gap after the centred block's window (left part ${left}..${c.topLine.right})"
                    if (abs((kind.baseline - c.figure.baseline).value) > 0.5f) problems += "the kind's baseline ${kind.baseline} is not the distance's ${c.figure.baseline}"
                }
                Step.STACKED -> {
                    val (upper, lower) = c.kindWords
                    if (upper.text != "by" || lower.text != "trail") problems += "the stack reads <${upper.text}> over <${lower.text}>"
                    // H14: the stack starts at the kind's space's start; each word centred in the stack (G3), so "trail", the wider,
                    // starts there too. Was (G3) each word's centre at the space's centre.
                    if (!near(kind.bounds.left, kindStart)) problems += "the stack ${kind.bounds} does not start at $kindStart, the word gap after the centred block's window"
                    val stackCentre = (kind.bounds.left + kind.bounds.right) / 2
                    for (w in c.kindWords) {
                        val centre = (w.bounds.left + w.bounds.right) / 2
                        if (!near(centre, stackCentre)) problems += "stacked <${w.text}> ${w.bounds} is not centred in the stack ${kind.bounds}: its centre $centre, the stack's $stackCentre"
                    }
                    if (!near(lower.bounds.left, kindStart)) problems += "stacked \"trail\" ${lower.bounds} does not start at $kindStart, the word gap after the centred block's window"
                    if (abs((lower.baseline - c.figure.baseline).value) > 0.5f) problems += "stacked \"trail\"'s baseline ${lower.baseline} is not the distance's ${c.figure.baseline}"
                    if (upper.bounds.bottom > lower.bounds.top + 0.5.dp) problems += "stacked \"by\" ${upper.bounds} is not above \"trail\" ${lower.bounds}"
                    if (upper.baseline >= lower.baseline) problems += "stacked \"by\"'s baseline ${upper.baseline} is not above \"trail\"'s ${lower.baseline}"
                }
                Step.OWN_LINE -> {
                    val centre = (kind.bounds.left + kind.bounds.right) / 2
                    if (!near(centre, c.middle)) problems += "the kind on its own line ${kind.bounds} is not centred on it: its centre $centre, the middle ${c.middle}"
                    if (kind.bounds.top < c.figure.bounds.bottom - 0.5.dp) problems += "the kind on its own line ${kind.bounds} is not under the distance ${c.figure.bounds}"
                }
                Step.ALONE -> Unit
            }
        }
        println(
            "RULES|$where|$s|$e|$facing|drawn $drawn|ruled $ruled|left part ${dpOf(widthPx)}|status below $statusBelow (space ${dpOf(minOf(floorPx, totalPx - dividerPx - leftNeedPx))}, widest word ${dpOf(widestWordPx)})|" +
                "longest ${dpOf(longestPx)}|kind ${dpOf(kindPx)}|stack ${stackPx?.let(::dpOf)}|shift ${dpOf(shiftPx)}|room ${dpOf(roomPx)}|nearest threshold ${dpOf(marginPx)}|right part wants ${dpOf(wantRightPx)}|" +
                "turn <${c.turn?.text}> needle ${c.needle.left}|card height ${c.hud.bottom - c.hud.top}",
        )
        return Reading(drawn, ruled, widthPx, longestPx, shiftPx, marginPx, problems.map { "$where $s $e $facing: $it" }, c.turn?.text.orEmpty(), c.heading?.text, c.needle.left, c.pieces.map { it.text },
            c.figure.bounds, c.kind?.bounds, c.topLine.left to c.topLine.right, left + dpOf(windowEndPx))
    }
}
