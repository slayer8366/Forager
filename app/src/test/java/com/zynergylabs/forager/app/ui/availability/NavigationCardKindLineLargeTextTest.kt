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

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 2,0 - both at the middle without an evening time, on their own line with one`() = assertClean(
        topLine("384/2.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("384/2.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = false),
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
        // Positive controls: the phone faced as each case says (169° off, 1° off, no compass), and that changed the left part's
        // width, so the step had a reason to move.
        assertEquals("$where: the headings drawn", listOf("281° W", "89° E", null), readings.map { it.heading })
        assertEquals("$where: the turn as drawn", listOf("169°", "1°", ""), readings.map { it.turn })
        assertTrue("$where: the left part's width did not change as the phone turned: $widths", widths.distinct().size > 1)
        assertEquals("$where: the kind's step as the phone turned (left part $widths px)", List(readings.size) { readings.first().drawn }, readings.map { it.drawn })
        assertEquals("$where: the step it holds", Step.OWN_LINE, readings.first().drawn)
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 2,0, Dark since - by trail keeps its step as the phone turns`() = assertTheKindHoldsItsStepWhileTurning("384/2.0 turning")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 2,0, Dark since - by trail keeps its step as the phone turns`() = assertTheKindHoldsItsStepWhileTurning("823x384/2.0 turning")
}
