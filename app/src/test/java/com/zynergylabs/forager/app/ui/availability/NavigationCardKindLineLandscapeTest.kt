package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181; the owner: "And do the same for landscape"): the landscape card on the bottom edge, at the
 * S22's window (823 × 384) and a smaller phone's (780 × 360), with no evening time. As [NavigationCardKindLinePortraitTest]: each in
 * its own space (Amendment 1, G3), a figure with no kind centred (B3, B4), checked by [NavigationCardTopLineRules]; the right part
 * keeps the broken evening line's width in daytime (H5, Q3). Was (Phase 2): `meetsAtTheMiddle`, the distance and its kind meeting at
 * the left part's middle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLineLandscapeTest : NavigationCardTopLineRules() {

    private fun assertClean(vararg readings: Reading) = assertTrue(readings.flatMap { it.problems }.joinToString("\n"), readings.all { it.problems.isEmpty() })

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, no evening time - both beside, straight's space wider than its half, and a word alone is centred`() = assertClean(
        topLine("823x384/1.0", State.TRAIL, Evening.NONE, step = Step.BESIDE),
        topLine("823x384/1.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("823x384/1.0", State.ARRIVED, Evening.NONE),
        topLine("823x384/1.0", State.DASH, Evening.NONE),
    )

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, no evening time - both beside, straight's space wider than its half, and a word alone is centred`() = assertClean(
        topLine("780x360/1.0", State.TRAIL, Evening.NONE, step = Step.BESIDE),
        topLine("780x360/1.0", State.STRAIGHT, Evening.NONE, step = Step.BESIDE, shifted = true),
        topLine("780x360/1.0", State.ARRIVED, Evening.NONE),
        topLine("780x360/1.0", State.DASH, Evening.NONE),
    )

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0, no evening time - the dash is centred`() = assertClean(topLine("823x384/2.0", State.DASH, Evening.NONE))

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
    fun `780 x 360, font 2,0, no evening time - the dash is centred`() = assertClean(topLine("780x360/2.0", State.DASH, Evening.NONE))
}
