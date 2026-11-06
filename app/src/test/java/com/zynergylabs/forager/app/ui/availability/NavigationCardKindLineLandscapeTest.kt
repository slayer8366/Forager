package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181; the owner: "And do the same for landscape"): the landscape card on the bottom edge, at the
 * S22's window (823 × 384) and a smaller phone's (780 × 360). As [NavigationCardKindLinePortraitTest]: with no evening time, the
 * distance and its kind meet at the left part's middle on one baseline; a figure with no kind ends at the middle; nothing in the
 * left part is below the turn row. Only the cases every reading of the rulings agrees on (CI run 42171184260).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLineLandscapeTest : NavigationCardKindLineScreen() {

    private fun assertMeets(where: String, vararg states: State) {
        val problems = states.flatMap { meetsAtTheMiddle(where, it) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, no evening time - the top line meets at the middle`() = assertMeets("823x384/1.0", State.TRAIL, State.STRAIGHT, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, no evening time - the top line meets at the middle`() = assertMeets("780x360/1.0", State.TRAIL, State.STRAIGHT, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0, no evening time - the dash ends at the middle`() = assertMeets("823x384/2.0", State.DASH)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
    fun `780 x 360, font 2,0, no evening time - the dash ends at the middle`() = assertMeets("780x360/2.0", State.DASH)
}
