package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181), Amendment 1: a word or mark with no kind beside it, centred in the left part, its centre at
 * the turn row's.
 * - B3, "Arrived": the owner, "Arrived is simply centered with no extras", confirmed by B5, "make Arrived center".
 * - B4, the dash before a fix or a route: "Centre it like Arrived (Recommended)", "so a word or mark on its own is always centred".
 *
 * Was (B1, "In the left half", then B2, "Left, crossing as little (Recommended)"): ending at the middle, or starting at the left
 * part's left edge where wider than its half. With "Dark since 5:23 PM" beside it, "Arrived" is wider than its half everywhere but
 * 384 dp at normal text size, and the dash never is, so each case names which ([crosses]) and is checked against both old rules.
 *
 * "Arrived" and the dash are separate methods, so each ruling's revert check can be read by name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardLoneWordTest : NavigationCardTopLineRules() {

    private fun assertClean(reading: Reading) = assertTrue(reading.problems.joinToString("\n"), reading.problems.isEmpty())

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0, Dark since - Arrived is centred`() = assertClean(topLine("384/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0, Dark since - the dash is centred`() = assertClean(topLine("384/1.0", State.DASH, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0, Dark since - Arrived is centred`() = assertClean(topLine("360/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true))

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0, Dark since - the dash is centred`() = assertClean(topLine("360/1.0", State.DASH, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, Dark since - Arrived is centred`() = assertClean(topLine("320/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true))

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, Dark since - the dash is centred`() = assertClean(topLine("320/1.0", State.DASH, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, Dark since - Arrived is centred`() = assertClean(topLine("823x384/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true))

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, Dark since - the dash is centred`() = assertClean(topLine("823x384/1.0", State.DASH, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, Dark since - Arrived is centred`() = assertClean(topLine("780x360/1.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true))

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, Dark since - the dash is centred`() = assertClean(topLine("780x360/1.0", State.DASH, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0, Dark since - Arrived is centred`() = assertClean(topLine("384/2.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true))

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0, Dark since - the dash is centred`() = assertClean(topLine("384/2.0", State.DASH, Evening.DARK_SINCE, crosses = false))

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0, Dark since - Arrived is centred`() = assertClean(topLine("823x384/2.0", State.ARRIVED, Evening.DARK_SINCE, crosses = true))

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0, Dark since - the dash is centred`() = assertClean(topLine("823x384/2.0", State.DASH, Evening.DARK_SINCE, crosses = false))
}
