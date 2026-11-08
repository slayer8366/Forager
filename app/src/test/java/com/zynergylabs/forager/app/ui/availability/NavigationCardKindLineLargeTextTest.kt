package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the top line at font 2.0, the owner's "Keep the text size the same size" (nothing shrinks
 * to fit), in portrait (384, 360, 320 dp) and landscape (823 × 384, 780 × 360).
 *
 * With no evening time the left part has the card's whole width, and the cases sit near the thresholds, each within a few dp:
 * straight's longest figure just fits left of the middle at 384 dp and just doesn't at 360; by trail's just fits in landscape and
 * just doesn't at 320; straight just misses beside at 320 and takes its own line. With an evening time the evening pieces leave
 * the left part short, and it is as wide as its widest line, the turn row, which changes as the walker turns. The planner's call
 * (2026-11-06), under the option the owner chose ("Decided for the longest distance the card could show, so it doesn't jump
 * between lines as you walk"): the step is decided for the narrowest the left part can be across every heading and turn word.
 * There, that is no wider than the kind, so both kinds take their own line, whichever way the phone faces; the readings print the
 * step the rulings would give at the width drawn, for the report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLineLargeTextTest : NavigationCardTopLineRules() {

    private fun assertClean(vararg readings: Reading) {
        val problems = readings.flatMap { it.problems }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // Amendment 1, H5 and Q3: at 384 dp the status space at font 2.0 holds the status words' widest word, so H8 does not apply and the
    // right part keeps the broken evening line's width in daytime; that leaves the left part its widest figure's width, and both kinds
    // take their own line. Was "384 dp, font 2,0 - both at the middle without an evening time, on their own line with one", with
    // `step = Step.BESIDE, shifted = false` for both with no evening time (the right part had no width then).
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 2,0 - both on their own line, with an evening time or without`() = assertClean(
        topLine("384/2.0", State.TRAIL, Evening.NONE, step = Step.OWN_LINE),
        topLine("384/2.0", State.STRAIGHT, Evening.NONE, step = Step.OWN_LINE),
        topLine("384/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("384/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 2,0 - straight moved right beside without an evening time, both on their own line with one`() = assertClean(
        topLine("360/2.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("360/2.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("360/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("360/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 2,0 - by trail moved right beside and straight on its own line, both on their own line with an evening time`() = assertClean(
        topLine("320/2.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("320/2.0", State.STRAIGHT, Evening.NONE, step = Step.OWN_LINE, shifted = true),
        topLine("320/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("320/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    // Dispatch 2026-11-06-01, Amendment 1, J4 (RECORD -2183, the owner: "Accept it", to ""by trail"/"straight" under the distance where
    // the arrow takes the right part"): with no evening time the next turn takes the right part (J3 step 2), so both kinds take their own
    // line under the distance. Was "by trail at the middle and straight moved right without an evening time", `step = Step.BESIDE` with
    // `shifted = false` and `true` (the right part had no width then).
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 2,0 - J4 - both on their own line, the next turn in the right part without an evening time and the card up with one`() = assertClean(
        topLine("823x384/2.0", State.TRAIL, Evening.NONE, step = Step.OWN_LINE),
        topLine("823x384/2.0", State.STRAIGHT, Evening.NONE, step = Step.OWN_LINE),
        topLine("823x384/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("823x384/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    // Dispatch 2026-11-06-01, Amendment 1, J4 (RECORD -2183, the owner: "Accept it", to ""by trail"/"straight" under the distance where
    // the arrow takes the right part"): with no evening time the next turn takes the right part (J3 step 2), so both kinds take their own
    // line under the distance. Was "by trail at the middle and straight moved right without an evening time", `step = Step.BESIDE` with
    // `shifted = false` and `true` (the right part had no width then).
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 2,0 - J4 - both on their own line, the next turn in the right part without an evening time and the card up with one`() = assertClean(
        topLine("780x360/2.0", State.TRAIL, Evening.NONE, step = Step.OWN_LINE),
        topLine("780x360/2.0", State.STRAIGHT, Evening.NONE, step = Step.OWN_LINE),
        topLine("780x360/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("780x360/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    /**
     * By trail with an evening time, facing 169° off, 1° off, and with no compass: the left part's widest and narrowest turn rows.
     * [leftOutWithoutCompass]: Amendment 1, H12 (the owner: "(a) Leave out "Dark since"", the option reading "In that one case the
     * evening line doesn't show; "by trail" keeps its own line"): with no compass, "No compass" takes a line under the parts (H8), and
     * the card with both the evening line and "by trail" on its own line rises past its limit (RECORD -955), so the line is left out.
     * Was: the evening line drawn in all three, which at 823 × 384 never settled (E13, stop 2: AppNotIdle).
     */
    private fun turning(where: String, leftOutWithoutCompass: Boolean): List<Reading> = Facing.entries.map { f ->
        topLine(where, State.TRAIL, Evening.DARK_SINCE, facing = f, eveningLeftOut = DARK_SINCE_PIECE.takeIf { leftOutWithoutCompass && f == Facing.NO_COMPASS })
    }

    private fun assertTheKindHoldsItsStepWhileTurning(where: String, leftOutWithoutCompass: Boolean = false) {
        val readings = turning(where, leftOutWithoutCompass)
        // Positive control on the piece H12 leaves out: it is the one drawn with the compass.
        if (leftOutWithoutCompass) assertEquals("$where: the evening line with the compass", listOf(DARK_SINCE_PIECE), readings.first().pieces)
        assertClean(*readings.toTypedArray())
        val widths = readings.map { it.leftPartPx }
        // Positive controls: the phone faced as each case says (169° off, 1° off, no compass). Since Amendment 1, H1, the card draws no
        // heading (was `listOf("281° W", "89° E", null)`), so the turn alone shows it.
        assertEquals("$where: no heading in the card", listOf<String?>(null, null, null), readings.map { it.heading })
        assertEquals("$where: the turn as drawn", listOf("169°", "1°", ""), readings.map { it.turn })
        // G3: the turn line is one width in every state, so the left part keeps one width as the phone turns, and the needle one place.
        // Was a positive control that the width did change (`widths.distinct().size > 1`), which gave the step a reason to move.
        assertEquals("$where: the left part's width as the phone turned", 1, widths.distinct().size)
        assertEquals("$where: the needle's place as the phone turned", 1, readings.map { it.needleLeft }.distinct().size)
        assertEquals("$where: the kind's step as the phone turned (left part $widths px)", List(readings.size) { readings.first().drawn }, readings.map { it.drawn })
        assertEquals("$where: the step it holds", Step.OWN_LINE, readings.first().drawn)
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 2,0, Dark since - by trail keeps its step as the phone turns`() = assertTheKindHoldsItsStepWhileTurning("384/2.0 turning")

    // Dispatch 2026-11-06-01, Amendment 1, J3 step 3 (RECORD -2183, the owner: "Own line, card moves up", "the evening line stays on
    // the card"): where the line with "No compass" would take the card over the walker's dot, the card now moves up instead, so H12's
    // case no longer arises here and the line shows in all three. Was "..., the evening line left out with no compass",
    // `leftOutWithoutCompass = true`.
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 2,0, Dark since - by trail keeps its step as the phone turns, the evening line staying with no compass (J3 step 3)`() =
        assertTheKindHoldsItsStepWhileTurning("823x384/2.0 turning")

    /**
     * Amendment 1, H12, the planner's reading: "the decision must settle, never loop. Where showing the evening line would push "by
     * trail" onto its own line and the card then no longer fits, the evening line is left out and stays out." Through the real screen
     * at 823 × 384, font 2.0, "Dark since": settled with the compass (the line shows), then the compass goes, and after 3 s of frames
     * each of the next 60 frames is read with the clock stepped by hand. In every frame: no evening piece, "by trail" on its own line,
     * and the card, the distance, the kind and the divider where they were in the frame before. The left part keeps the width it had
     * with the line (the right part keeps the line's width), so nothing else moves as it goes. Before H12 the card took the line and
     * dropped it in a four-frame cycle (E13, stop 2, run 42278700471), and this case failed as AppNotIdle.
     */
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 2,0, Dark since - with no compass the evening line stays on and the card stays still across frames (J3 step 3)`() {
        val where = "823x384/2.0 settling"
        val (before, frames) = framesAfterTheCompassGoes(State.TRAIL, Evening.DARK_SINCE, where)
        val problems = mutableListOf<String>()
        // Positive controls: with the compass the line shows and the kind is on its own line; without it, "No compass" is the status.
        if (before.pieces.map { it.text } != listOf(DARK_SINCE_PIECE)) problems += "positive control: with the compass the evening pieces are ${before.pieces.map { it.text }}"
        if (before.kind == null || before.kind.bounds.top < before.figure.bounds.bottom - 0.5.dp) problems += "positive control: with the compass the kind ${before.kind?.bounds} is not on its own line"
        if (frames.last().notice?.text != "No compass" && frames.last().status != "No compass") problems += "positive control: no \"No compass\" without the compass (status <${frames.last().status}>, notice <${frames.last().notice?.text}>)"
        fun still(c: Card) = listOf(c.hud, c.figure.bounds, c.kind?.bounds, c.divider, c.topLine)
        frames.forEachIndexed { i, c ->
            // J3 step 3 (see the turning test above): the card moves up and the line stays. Was `c.eveningPieces != 0` (H12, left out).
            if (c.eveningPieces != 1) problems += "frame ${i + 1}: ${c.eveningPieces} evening pieces drawn, not the one that stays"
            if (c.kind == null || c.kind.bounds.top < c.figure.bounds.bottom - 0.5.dp) problems += "frame ${i + 1}: the kind ${c.kind?.bounds} is not on its own line under ${c.figure.bounds}"
            if (i > 0 && still(c) != still(frames[i - 1])) problems += "frame ${i + 1}: moved from ${still(frames[i - 1])} to ${still(c)}"
        }
        val span = { c: Card -> c.topLine.left to c.topLine.right }
        if (span(frames.first()) != span(before)) problems += "the left part moved as the evening line went: ${span(before)} to ${span(frames.first())}"
        println("SETTLING|$where|before card ${before.hud} pieces ${before.pieces.map { it.text }}|frames ${frames.map { "${it.eveningPieces}:${it.hud.top}" }.distinct()}")
        assertTrue(problems.distinct().take(12).joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        /** The evening line as the card draws it here: 37 min after "5:23 PM" on the test clock ([Evening.DARK_SINCE]). */
        const val DARK_SINCE_PIECE = "Dark since 5:23 PM"
    }
}
