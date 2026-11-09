package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10, Amendment 1 (RECORD -2181): the card's turn line, through the real screen in the app's own type
 * ([NavigationCardKindLineScreen]), the way home by trail facing three ways in turn: 169° off the aim ([Facing.TURN]), 1° off it
 * ([Facing.AHEAD]), and with no compass ([Facing.NO_COMPASS]).
 * - G2, the owner: "Drop the words, keep the degrees (Recommended)", the option reading ""right 109°" becomes "109°". The needle shows
 *   which way, and the number how far." Was "sharp right 169°" and "ahead 1°".
 * - G3, "The turn line": "The degrees, the needle, the north arrow and the heading each sit in a fixed space, so the compass never
 *   jumps": the needle is at one place whatever the degrees read, or with none. Was: the row centred on its words, so the needle moved
 *   as they changed.
 * - H1: "Keep the compass strip the exact same when in navigation mode as out; so keep the north compass out of the navigation panel":
 *   no north arrow and no heading in the card. Was (RECORD -950): the north arrow and the heading right after the needle.
 * Each ruling has its own methods, so a revert of one reads by name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardTurnLineTest : NavigationCardKindLineScreen() {

    private fun facings(where: String): List<Pair<Facing, Card>> = listOf(Facing.TURN, Facing.AHEAD, Facing.NO_COMPASS).map { f ->
        show(State.TRAIL, Evening.NONE, f)
        f to card(where).also { printed("$where $f", State.TRAIL, it) }
    }

    private fun assertNone(problems: List<String>) = assertTrue(problems.joinToString("\n"), problems.isEmpty())

    private fun g2(where: String) {
        val read = facings(where).associate { (f, c) -> f to c.turn?.text.orEmpty() }
        val problems = mutableListOf<String>()
        if (read[Facing.TURN] != "169°") problems += "$where: facing 169° off the aim, the turn line reads <${read[Facing.TURN]}>, not <169°>"
        if (read[Facing.AHEAD] != "1°") problems += "$where: facing 1° off the aim, the turn line reads <${read[Facing.AHEAD]}>, not <1°>"
        // Positive control: with no compass there is no turn to give, as before.
        if (read[Facing.NO_COMPASS] != "") problems += "positive control: $where: with no compass the turn line reads <${read[Facing.NO_COMPASS]}>"
        assertNone(problems)
    }

    private fun g3(where: String) {
        val read = facings(where)
        val problems = mutableListOf<String>()
        // Positive control: three different turns were drawn.
        val turns = read.map { it.second.turn?.text.orEmpty() }
        if (turns.distinct().size != 3) problems += "positive control: $where: the turns drawn are $turns, not three different ones"
        val lefts: List<Dp> = read.map { it.second.needle.left }
        if (abs((lefts.max() - lefts.min()).value) > 0.5f) problems += "$where: the needle moves as the turn changes: ${read.map { "${it.first} <${it.second.turn?.text}> ${it.second.needle.left}" }}"
        // The degrees sit centred on one place too, wherever there are some.
        val centres = read.mapNotNull { (_, c) -> c.turn?.takeIf { it.text.isNotEmpty() }?.let { (it.bounds.left + it.bounds.right) / 2 } }
        if (centres.size == 2 && abs((centres[0] - centres[1]).value) > 0.5f) problems += "$where: the degrees' centre moves: $centres"
        assertNone(problems)
    }

    private fun h1(where: String) {
        val problems = mutableListOf<String>()
        for ((f, c) in facings(where)) {
            if (c.northArrow != null) problems += "$where $f: a north arrow (or the crossed-out compass) is drawn in the card at ${c.northArrow}"
            if (c.heading != null) problems += "$where $f: a heading <${c.heading.text}> is drawn in the card"
            // Positive control: the card is there, with its needle.
            if (c.needle.right <= c.needle.left) problems += "positive control: $where $f: no needle"
        }
        assertNone(problems)
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `G2, 384 dp, font 1,0 - the turn line reads the degrees alone`() = g2("384/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `G2, 823 x 384, font 1,0 - the turn line reads the degrees alone`() = g2("823x384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `G2, 384 dp, font 2,0 - the turn line reads the degrees alone`() = g2("384/2.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `G3, 384 dp, font 1,0 - the needle stays put as the turn changes`() = g3("384/1.0")

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `G3, 320 dp, font 1,0 - the needle stays put as the turn changes`() = g3("320/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `G3, 823 x 384, font 1,0 - the needle stays put as the turn changes`() = g3("823x384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `G3, 384 dp, font 2,0 - the needle stays put as the turn changes`() = g3("384/2.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H1, 384 dp, font 1,0 - no north arrow and no heading in the card`() = h1("384/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `H1, 823 x 384, font 1,0 - no north arrow and no heading in the card`() = h1("823x384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `H1, 384 dp, font 2,0 - no north arrow and no heading in the card`() = h1("384/2.0")
}
