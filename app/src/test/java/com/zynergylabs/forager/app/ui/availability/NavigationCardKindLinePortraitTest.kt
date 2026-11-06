package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181), portrait: with no evening time beside it, the distance and its kind share the top line,
 * meeting at the left part's middle on one baseline, and a figure with no kind ("Arrived", the dash) is centred on the middle
 * (Amendment 1, B3 and B4; was, B1: ending at the middle); nothing in the left part is below the turn row. Through the real screen ([NavigationCardKindLineScreen]).
 *
 * The cases here are the ones every reading of the owner's rulings agrees on: where the widest figure the kind can show fits its
 * half with room to spare (8 dp or more, CI run 42171184260). Was, before this dispatch: the kind a line of its own under the turn
 * line, every line centred (RECORD -945).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLinePortraitTest : NavigationCardKindLineScreen() {

    private fun assertMeets(where: String, vararg states: State) {
        val problems = states.flatMap { meetsAtTheMiddle(where, it) }
        assertEquals("states measured", states.size, states.distinct().size)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0, no evening time - the top line meets at the middle, and a word alone is centred`() = assertMeets("384/1.0", State.TRAIL, State.STRAIGHT, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0, no evening time - the top line meets at the middle, and a word alone is centred`() = assertMeets("360/1.0", State.TRAIL, State.STRAIGHT, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, no evening time - the top line meets at the middle, and a word alone is centred`() = assertMeets("320/1.0", State.TRAIL, State.STRAIGHT, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0, no evening time - by trail meets at the middle, and the words alone are centred`() = assertMeets("384/2.0", State.TRAIL, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `360 dp, font 2,0, no evening time - by trail meets at the middle, and the words alone are centred`() = assertMeets("360/2.0", State.TRAIL, State.ARRIVED, State.DASH)

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `320 dp, font 2,0, no evening time - the dash is centred`() = assertMeets("320/2.0", State.DASH)
}
