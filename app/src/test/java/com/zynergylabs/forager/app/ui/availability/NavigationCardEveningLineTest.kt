package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10, Amendment 1, H5 (RECORD -2181), through the real screen ([NavigationCardKindLineScreen]). The owner:
 * "Shorten to Sunset: 4:52 PM ▪︎ Dark: 5:23 PM status words are on the right panel"; then, the one line being too wide beside the
 * left part on most phones, "Split it at the ▪ (Recommended)": "Where the one line doesn't fit, it breaks at the ▪ into two lines,
 * still with no countdown. The right side stays about today's width". The planner's readings: it stays one line only where it fits
 * whole beside the left part with the next-turn space reserved; only the before-sunset form changes; and in daytime the right part
 * keeps the broken form's width, so nothing moves when the evening line comes (Q3).
 *
 * The rig's evening before sunset is sunset 8:28 PM and dark 9:00 PM on its UTC clock. Was three pieces, "Sunset 8:28 PM", "in 2 h
 * 28 min", "dark 9:00".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardEveningLineTest : NavigationCardKindLineScreen() {

    private fun pieces(where: String, e: Evening): List<String> {
        show(State.TRAIL, e)
        val c = card(where).also { printed(where, State.TRAIL, it) }
        println("EVENINGLINE|$where|$e|pieces ${c.pieces.map { it.text }}|card height ${c.hud.bottom - c.hud.top}|divider ${c.divider}")
        return c.pieces.map { it.text }
    }

    private fun assertPieces(where: String, expected: List<String>) {
        val drawn = pieces(where, Evening.BEFORE_SUNSET)
        val problems = mutableListOf<String>()
        if (drawn != expected) problems += "before sunset the card reads $drawn, not $expected"
        if (drawn.any { it.startsWith("in ") }) problems += "the countdown is still on the card: $drawn"
        // Positive control: between sunset and dusk the line keeps its words (only the before-sunset form changes).
        val after = pieces(where, Evening.AFTER_SUNSET)
        if (after != listOf("Sun set 5:50 PM", "dark in 21 min")) problems += "positive control: between sunset and dusk the card reads $after"
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    private fun assertNothingMovesWhenItComes(where: String) {
        show(State.TRAIL, Evening.NONE)
        val day = card(where)
        show(State.TRAIL, Evening.BEFORE_SUNSET)
        val evening = card(where)
        val problems = mutableListOf<String>()
        if (day.pieces.isNotEmpty() || evening.pieces.isEmpty()) problems += "positive control: ${day.pieces.size} pieces in daytime, ${evening.pieces.size} before sunset"
        for ((name, a, b) in listOf(Triple("the divider", day.divider.left, evening.divider.left), Triple("the distance", day.figure.bounds.left, evening.figure.bounds.left), Triple("the needle", day.needle.left, evening.needle.left))) {
            if (abs((a - b).value) > 0.5f) problems += "$name moves when the evening line comes: $a in daytime, $b before sunset"
        }
        println("EVENINGCOMES|$where|day divider ${day.divider}|evening divider ${evening.divider}|day height ${day.hud.bottom - day.hud.top}|evening height ${evening.hud.bottom - evening.hud.top}")
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    private val broken = listOf("Sunset: 8:28 PM", "Dark: 9:00 PM")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H5, 384 dp, font 1,0 - before sunset the line is broken at the square, no countdown`() = assertPieces("384/1.0", broken)

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `H5, 320 dp, font 1,0 - before sunset the line is broken at the square, no countdown`() = assertPieces("320/1.0", broken)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `H5, 823 x 384, font 1,0 - before sunset the line is broken at the square, no countdown`() = assertPieces("823x384/1.0", broken)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `H5, 384 dp, font 2,0 - before sunset the line is broken at the square, no countdown`() = assertPieces("384/2.0", broken)

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi")
    fun `H5, 480 dp, font 1,0 - before sunset the line is one line where it fits whole beside the reserved next turn`() =
        assertPieces("480/1.0", listOf("Sunset: 8:28 PM ▪︎ Dark: 9:00 PM"))

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H5 and Q3, 384 dp, font 1,0 - nothing moves when the evening line comes`() = assertNothingMovesWhenItComes("384/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `H5 and Q3, 823 x 384, font 1,0 - nothing moves when the evening line comes`() = assertNothingMovesWhenItComes("823x384/1.0")
}
