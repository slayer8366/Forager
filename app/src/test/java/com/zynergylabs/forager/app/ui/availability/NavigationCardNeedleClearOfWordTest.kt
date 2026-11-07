package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10, Amendment 1, E17 (RECORD -2181), from the S22 check on build 4825: at font 2.0, portrait, after 5 minutes
 * without a fix (the card's "—" with "Target"), the greyed needle overlapped "Target" by about 8 dp. The owner: "Fix it in #498
 * first" ("A coder moves the needle clear of "Target" in that case (nothing else changes)").
 *
 * Through the real screen, in every screen and font the card's tests cover (portrait 320, 360, 384, 412 and 480 dp, landscape 780 x
 * 360 and 823 x 384, fonts 1.0, 1.3 and 2.0), with the word the turn line draws in the degrees' space in place of degrees:
 * - "Target", the way home 6 minutes after the last fix ([State.LOST]);
 * - "No target", the way home with no start ([State.NO_TARGET]).
 * The claims, for each word:
 * - the word's right edge is at or left of the needle's left edge (the needle's tag is its unrotated square, the word's its laid-out
 *   line, so this is stricter than ink);
 * - controls (they held before E17 too): the word is centred where the degrees are ("169°" facing the turn, read first), so the word
 *   itself does not move; and the needle stays inside the left part.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardNeedleClearOfWordTest : NavigationCardKindLineScreen() {

    private fun clear(where: String) {
        val problems = mutableListOf<String>()
        show(State.TRAIL, Evening.NONE, Facing.TURN)
        val turning = card("$where TRAIL")
        val degrees = turning.turn
        if (degrees == null || degrees.text != "169°") {
            problems += "positive control: $where TRAIL: the turn line reads <${degrees?.text}>, not <169°>"
        }
        val degreesCentre = degrees?.let { (it.bounds.left + it.bounds.right) / 2 }
        val spacePx = degrees?.let { d -> (0..180).maxOf { widthPx(d, "$it°") } }
        for ((s, word) in listOf(State.LOST to "Target", State.NO_TARGET to "No target")) {
            show(s, Evening.NONE, Facing.TURN)
            val c = card("$where $s")
            printed("$where $s", s, c)
            val turn = c.turn
            if (turn == null || turn.text != word) {
                problems += "positive control: $where $s: the turn line reads <${turn?.text}>, not <$word>"
                continue
            }
            val wordWidth = turn.bounds.right - turn.bounds.left
            println(
                "NEEDLE|$where|$s|word <$word> ${turn.bounds.left}..${turn.bounds.right} ($wordWidth)|" +
                    "degrees' space ${spacePx?.let { it / densityPx }} dp|needle ${c.needle.left}..${c.needle.right}|" +
                    "gap ${c.needle.left - turn.bounds.right}|left part ${c.topLine.left}..${c.leftPartRight}|divider ${c.divider.left}",
            )
            if (turn.bounds.right.value > c.needle.left.value + 0.05f) {
                problems += "$where $s: the needle ${c.needle.left}..${c.needle.right} overlaps <$word> ${turn.bounds.left}..${turn.bounds.right} by ${turn.bounds.right - c.needle.left}"
            }
            val centre = (turn.bounds.left + turn.bounds.right) / 2
            if (degreesCentre != null && abs((centre - degreesCentre).value) > 0.5f) {
                problems += "$where $s: <$word> is centred at $centre, not where the degrees are ($degreesCentre)"
            }
            if (c.needle.right.value > c.leftPartRight.value + 0.05f) {
                problems += "$where $s: the needle ${c.needle.left}..${c.needle.right} leaves the left part (its right edge ${c.leftPartRight})"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0 - the needle clears the word in its place`() = clear("320/1.0")

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun `320 dp, font 1,3 - the needle clears the word in its place`() = clear("320/1.3")

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `320 dp, font 2,0 - the needle clears the word in its place`() = clear("320/2.0")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0 - the needle clears the word in its place`() = clear("360/1.0")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun `360 dp, font 1,3 - the needle clears the word in its place`() = clear("360/1.3")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `360 dp, font 2,0 - the needle clears the word in its place`() = clear("360/2.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - the needle clears the word in its place`() = clear("384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f)
    fun `384 dp, font 1,3 - the needle clears the word in its place`() = clear("384/1.3")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0 - the needle clears the word in its place`() = clear("384/2.0")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `412 dp, font 1,0 - the needle clears the word in its place`() = clear("412/1.0")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun `412 dp, font 1,3 - the needle clears the word in its place`() = clear("412/1.3")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 2.0f)
    fun `412 dp, font 2,0 - the needle clears the word in its place`() = clear("412/2.0")

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi")
    fun `480 dp, font 1,0 - the needle clears the word in its place`() = clear("480/1.0")

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 1.3f)
    fun `480 dp, font 1,3 - the needle clears the word in its place`() = clear("480/1.3")

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 2.0f)
    fun `480 dp, font 2,0 - the needle clears the word in its place`() = clear("480/2.0")

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0 - the needle clears the word in its place`() = clear("780x360/1.0")

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.3f)
    fun `780 x 360, font 1,3 - the needle clears the word in its place`() = clear("780x360/1.3")

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
    fun `780 x 360, font 2,0 - the needle clears the word in its place`() = clear("780x360/2.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0 - the needle clears the word in its place`() = clear("823x384/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f)
    fun `823 x 384, font 1,3 - the needle clears the word in its place`() = clear("823x384/1.3")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0 - the needle clears the word in its place`() = clear("823x384/2.0")
}
