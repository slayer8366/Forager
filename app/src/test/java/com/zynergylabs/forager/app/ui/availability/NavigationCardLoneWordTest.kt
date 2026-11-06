package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): a word with no kind beside it ("Arrived", the dash before the first route), the owner: "In
 * the left half", in the distance's own place, ending at the middle; and where the word is wider than that, "Left, crossing as
 * little (Recommended)": it starts at the left part's left edge and crosses the middle only as far as it must. With an evening
 * time beside it, "Arrived" is wider than its half everywhere but 384 dp at normal text size; the dash never is.
 *
 * The branch drew this before the rulings came (the previous build's placeholder matched the ruling), so these cases cannot fail
 * on the branch: what holds them is the revert check in the completion report, which puts the word back at the middle regardless
 * of its width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardLoneWordTest : NavigationCardTopLineRules() {

    private fun assertClean(vararg readings: Reading) {
        val problems = readings.flatMap { it.problems }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0, Dark since - Arrived and the dash end at the middle`() = assertClean(
        topLine("384/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = false),
        topLine("384/1.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0, Dark since - Arrived starts at the left edge, the dash ends at the middle`() = assertClean(
        topLine("360/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true),
        topLine("360/1.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, Dark since - Arrived starts at the left edge, the dash ends at the middle`() = assertClean(
        topLine("320/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true),
        topLine("320/1.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, Dark since - Arrived starts at the left edge, the dash ends at the middle`() = assertClean(
        topLine("823x384/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true),
        topLine("823x384/1.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, Dark since - Arrived starts at the left edge, the dash ends at the middle`() = assertClean(
        topLine("780x360/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true),
        topLine("780x360/1.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0, Dark since - Arrived starts at the left edge, the dash ends at the middle`() = assertClean(
        topLine("384/2.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true),
        topLine("384/2.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0, Dark since - Arrived starts at the left edge, the dash ends at the middle`() = assertClean(
        topLine("823x384/2.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true),
        topLine("823x384/2.0", State.DASH, Evening.DARK_SINCE, crosses = false),
    )
}
