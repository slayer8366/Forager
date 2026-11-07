package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the top line at normal text size with an evening time beside it, "Dark since 5:23 PM" as
 * in the owner's screenshots, in portrait (384, 360, 320 dp) and landscape (823 × 384, 780 × 360). The evening time takes the
 * right part's width, so the longest distance no longer fits left of the middle in most of them, and the owner's "Shift the middle
 * a little (Recommended)" moves the meeting point right just enough ([NavigationCardTopLineRules]). Each case names the step it
 * is chosen to reach, so a case that stopped reaching it fails rather than passing on a different step.
 *
 * Since Amendment 1, G3, the distance and its kind are each centred in their own space instead of meeting there
 * ([NavigationCardTopLineRules]); "at the middle" and "moved right" in the names below are where the line is divided, at the half or
 * past it, which G3 keeps from A4. The steps are unchanged: "Dark since 5:23 PM" is wider than the right part's daytime width.
 *
 * Expected, in Noto Sans (the reasons are in the readings each case prints): by trail beside everywhere, moved right of the middle
 * except at 384 dp; straight beside, moved, except at 320 dp, where it takes its own line, as near the meeting point as fits.
 * Before this dispatch's Phase 2 rulings the branch read the middle strictly: where the longest figure did not fit its half the
 * kind took its own line, so every case here but by trail at 384 dp was on its own line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLineEveningTest : NavigationCardTopLineRules() {

    private fun assertClean(vararg readings: Reading) {
        val problems = readings.flatMap { it.problems }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0, Dark since - by trail at the middle, straight beside it moved right`() = assertClean(
        topLine("384/1.0", State.TRAIL, Evening.DARK_SINCE, step = Step.BESIDE, shifted = false),
        topLine("384/1.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
    )

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0, Dark since - both beside, straight moved right`() = assertClean(
        // By trail's longest figure is within a pixel of its half here, so whether it moves is not pinned.
        topLine("360/1.0", State.TRAIL, Evening.DARK_SINCE, step = Step.BESIDE),
        topLine("360/1.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
    )

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, Dark since - by trail beside moved right, straight on its own line`() = assertClean(
        topLine("320/1.0", State.TRAIL, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
        topLine("320/1.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.OWN_LINE, shifted = true),
    )

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, Dark since - both beside, moved right`() = assertClean(
        topLine("823x384/1.0", State.TRAIL, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
        topLine("823x384/1.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
    )

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, Dark since - both beside, moved right`() = assertClean(
        topLine("780x360/1.0", State.TRAIL, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
        topLine("780x360/1.0", State.STRAIGHT, Evening.DARK_SINCE, step = Step.BESIDE, shifted = true),
    )
}
