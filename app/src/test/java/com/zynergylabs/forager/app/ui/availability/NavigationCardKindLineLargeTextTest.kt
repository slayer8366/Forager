package com.zynergylabs.forager.app.ui.availability

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

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 2,0 - by trail at the middle and straight moved right without an evening time, both on their own line with one`() = assertClean(
        topLine("823x384/2.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("823x384/2.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("823x384/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("823x384/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 2,0 - by trail at the middle and straight moved right without an evening time, both on their own line with one`() = assertClean(
        topLine("780x360/2.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("780x360/2.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("780x360/2.0", State.TRAIL, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
        topLine("780x360/2.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    /** By trail with an evening time, facing 169° off, 1° off, and with no compass: the left part's widest and narrowest turn rows. */
    private fun turning(where: String): List<Reading> = Facing.entries.map { f -> topLine(where, State.TRAIL, Evening.DARK_SINCE, facing = f) }

    private fun assertTheKindHoldsItsStepWhileTurning(where: String) {
        val readings = turning(where)
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

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 2,0, Dark since - by trail keeps its step as the phone turns`() = assertTheKindHoldsItsStepWhileTurning("823x384/2.0 turning")
}
