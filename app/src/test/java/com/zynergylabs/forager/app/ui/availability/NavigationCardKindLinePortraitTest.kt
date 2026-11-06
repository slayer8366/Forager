package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181), portrait, with no evening time: the distance and its kind on the top line, each centred in its
 * own space (Amendment 1, G3), and a figure with no kind ("Arrived", the dash) centred in the left part (B3 and B4); nothing in the
 * left part below the turn row. Through the real screen, checked by [NavigationCardTopLineRules].
 *
 * Since Amendment 1, H5 and the planner's reading of Q3, the right part keeps the broken evening line's width in daytime (107.3 dp at
 * font 1.0), so these windows have no longer the whole card's width for the left part. Each case names the step it is chosen to reach.
 * At font 2.0 (Amendment 1, H8) the status words take their own line under the parts wherever the status space is narrower than their
 * widest word, and the right part then keeps no daytime width; at 384 dp it is not narrower, so the daytime width stays and leaves the
 * left part its widest figure's width, the kind on its own line.
 *
 * Was (Phase 2, "Meet at the middle (Recommended)"): `meetsAtTheMiddle`, the distance ending at the left part's middle and the kind
 * starting there, on the cases where the widest figure fitted its half with room to spare; then, before this dispatch, the kind a
 * line of its own under the turn line, every line centred (RECORD -945).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLinePortraitTest : NavigationCardTopLineRules() {

    private fun assertClean(vararg readings: Reading) = assertTrue(readings.flatMap { it.problems }.joinToString("\n"), readings.all { it.problems.isEmpty() })

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0, no evening time - both beside in their halves, and a word alone is centred`() = assertClean(
        topLine("384/1.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("384/1.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("384/1.0", State.ARRIVED, Evening.NONE),
        topLine("384/1.0", State.DASH, Evening.NONE),
    )

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0, no evening time - both beside, straight's space wider than its half, and a word alone is centred`() = assertClean(
        topLine("360/1.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("360/1.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("360/1.0", State.ARRIVED, Evening.NONE),
        topLine("360/1.0", State.DASH, Evening.NONE),
    )

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, no evening time - by trail beside, straight on its own line, and a word alone is centred`() = assertClean(
        topLine("320/1.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("320/1.0", State.STRAIGHT, Evening.NONE, step = Step.OWN_LINE, shifted = true),
        topLine("320/1.0", State.ARRIVED, Evening.NONE),
        topLine("320/1.0", State.DASH, Evening.NONE),
    )

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0, no evening time - by trail on its own line, and the words alone are centred`() = assertClean(
        topLine("384/2.0", State.TRAIL, Evening.NONE, step = Step.OWN_LINE),
        topLine("384/2.0", State.ARRIVED, Evening.NONE),
        topLine("384/2.0", State.DASH, Evening.NONE),
    )

    // H8: at 360 dp and font 2.0 the status space is too narrow for the status words' widest word, so they take their own line under the
    // parts and the right part keeps no daytime width: the left part has the parts' width, and "by trail" sits beside the distance.
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `360 dp, font 2,0, no evening time - by trail beside in its half, and the words alone are centred`() = assertClean(
        topLine("360/2.0", State.TRAIL, Evening.NONE, step = Step.BESIDE, shifted = false),
        topLine("360/2.0", State.ARRIVED, Evening.NONE),
        topLine("360/2.0", State.DASH, Evening.NONE),
    )

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `320 dp, font 2,0, no evening time - the dash is centred`() = assertClean(topLine("320/2.0", State.DASH, Evening.NONE))
}
