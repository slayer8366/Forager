package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10, Amendment 1 (RECORD -2181), the status words, through the real screen ([NavigationCardKindLineScreen]).
 * - H2, the owner: "Under the evening line (Recommended)", the option reading "Up one line, into the empty space under "Dark since…",
 *   left of the X. The card is two lines tall, and the divider runs its full height." The planner's readings: centred in their own
 *   space, the right part's second line, with or without the evening line; under the whole evening part where it is more than one
 *   line (Q2). Was: a line of its own under the parts, starting at the card's left (RECORD -935).
 * - H4, the owner: "Shorten the lines to just Off trail since it's obvious what to do from there. Wrap the text to "Trail routes not"
 *   and "downloaded" goes on the next line." The planner's reading: the two notes that stay lose "· straight line" on the card and
 *   wrap in the status space, which grows taller, never wider. Was "Off trail · Head back to the line", and "Trail routes not
 *   downloaded · straight line" on one line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardStatusSpaceTest : NavigationCardKindLineScreen() {

    private fun near(a: Dp, b: Dp, tolerance: Float = 0.5f) = abs((a - b).value) <= tolerance

    /** The right part: from the divider's right gap (6 dp) to where the X's 48 dp touch box starts, 24 dp left of its centre. */
    private fun rightPart(c: Card): Pair<Dp, Dp> = (c.divider.right + 6.dp) to ((c.exit.left + c.exit.right) / 2 - 24.dp)

    /** The centre of [laid]'s widest line, from its layout, in the root. */
    private fun lineCentre(laid: Laid): Dp? {
        val layout = laid.layout ?: return null
        val left = (0 until layout.lineCount).minOf { layout.getLineLeft(it) }
        val right = (0 until layout.lineCount).maxOf { layout.getLineRight(it) }
        return laid.bounds.left + ((left + right) / 2 / densityPx).dp
    }

    private fun h2(where: String, e: Evening, twoLines: Boolean) {
        val problems = mutableListOf<String>()
        show(State.TRAIL, e)
        val plain = card(where).also { printed(where, State.TRAIL, it) }
        show(State.APPROACHING, e)
        val c = card(where).also { printed(where, State.APPROACHING, it) }
        if (plain.status.isNotEmpty()) problems += "positive control: with no status the card reads <${plain.status}>"
        if (c.status != "Approaching") problems += "positive control: the state reads <${c.status}>, not <Approaching>"
        val box = c.statusBox
        val laid = c.statusLaid
        if (box == null || laid == null) {
            problems += "no status words drawn"
        } else {
            val (rightLeft, rightRight) = rightPart(c)
            // In the right part, left of the X.
            if (box.left < c.divider.right - 0.5.dp) problems += "the status ${box} starts left of the divider ${c.divider}: not in the right part"
            if (box.right > rightRight + 0.5.dp) problems += "the status ${box} runs past the X's touch box at $rightRight"
            // Centred in its space.
            val centre = lineCentre(laid)
            if (centre == null || !near(centre, (rightLeft + rightRight) / 2)) problems += "<${c.status}> is not centred in the right part $rightLeft..$rightRight: its centre $centre"
            // On its line: under the evening part, or the right part's second line with none.
            val lineHeight = box.bottom - box.top
            val expectedTop = c.pieces.maxOfOrNull { it.bounds.bottom } ?: (c.divider.top + lineHeight)
            if (!near(box.top, expectedTop)) problems += "the status's top ${box.top} is not ${if (c.pieces.isEmpty()) "the right part's second line" else "under the evening part"}, $expectedTop"
            if (box.top >= c.turnRowBottom - 0.5.dp) problems += "the status ${box} is under the parts (the turn row ends at ${c.turnRowBottom})"
            // The divider runs the parts' full height.
            if (c.divider.bottom < box.bottom - 0.5.dp) problems += "the divider ends at ${c.divider.bottom}, above the status's bottom ${box.bottom}"
        }
        // "The card is two lines tall": the status words take no height of their own where they fit.
        val height = c.hud.bottom - c.hud.top
        val plainHeight = plain.hud.bottom - plain.hud.top
        if (twoLines && !near(height, plainHeight)) problems += "with <${c.status}> the card is $height, not the $plainHeight it is without it"
        println("STATUSPLACE|$where|$e|plain $plainHeight|with status $height|status ${c.statusBox}|divider ${c.divider}")
        assertTrue(problems.joinToString("\n") { "$where $e: $it" }, problems.isEmpty())
    }

    /**
     * H8, the owner: "Own line when too narrow (Recommended)": "Where the space is too narrow for its words (largest text, narrow phones),
     * status words go back to their own full-width line under the card, as before." So at font 2.0 in landscape and at 360 dp, the status
     * words are under the parts, starting at the card's left, and the right part keeps no width in daytime.
     */
    private fun h8(where: String) {
        val problems = mutableListOf<String>()
        show(State.APPROACHING, Evening.NONE)
        val c = card(where).also { printed(where, State.APPROACHING, it) }
        if (c.status != "Approaching") problems += "positive control: the state reads <${c.status}>, not <Approaching>"
        val box = c.statusBox
        if (box == null) {
            problems += "no status words drawn"
        } else {
            if (box.top < c.turnRowBottom - 0.5.dp) problems += "the status ${box} is not under the parts (the turn row ends at ${c.turnRowBottom})"
            if (box.left > c.hud.left + 8.5.dp) problems += "the status ${box} does not start at the card's left ${c.hud.left}"
        }
        // In daytime the right part keeps no width: the divider is at the X's side, the left part has the parts' width.
        val (rightLeft, rightRight) = rightPart(c)
        if (rightRight - rightLeft > 0.5.dp) problems += "in daytime the right part is ${rightRight - rightLeft} wide, not empty ($rightLeft..$rightRight)"
        println("STATUSPLACE|$where|H8|status ${c.statusBox}|divider ${c.divider}|card height ${c.hud.bottom - c.hud.top}")
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    private fun offTrail(where: String) {
        show(State.OFF_TRAIL, Evening.NONE)
        val c = card(where).also { printed(where, State.OFF_TRAIL, it) }
        val problems = mutableListOf<String>()
        if (c.figure.text != "1280 ft" || c.kind?.text != "by trail") problems += "positive control: the way to the waypoint by trail reads <${c.figure.text}> <${c.kind?.text}>"
        if (c.status != "Off trail") problems += "off the trail line to a waypoint the card reads <${c.status}>, not <Off trail>"
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    private fun noteWraps(where: String, lines: List<String>) {
        show(State.STRAIGHT_NOTE_KEPT, Evening.NONE)
        val c = card(where).also { printed(where, State.STRAIGHT_NOTE_KEPT, it) }
        val problems = mutableListOf<String>()
        if (c.status != "Trail routes not downloaded") problems += "the note that stays reads <${c.status}>, not <Trail routes not downloaded>"
        val layout = c.statusLaid?.layout
        val drawn = layout?.let { l -> (0 until l.lineCount).map { c.status.substring(l.getLineStart(it), l.getLineEnd(it)).trim() } }
        if (drawn != lines) problems += "the note is laid out as $drawn, not $lines"
        val box = c.statusBox
        val (rightLeft, rightRight) = rightPart(c)
        if (box != null && (box.left < rightLeft - 6.5.dp || box.right > rightRight + 0.5.dp)) problems += "the status space ${box} is wider than the right part $rightLeft..$rightRight"
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H2, 384 dp, font 1,0, no evening time - Approaching on the right part's second line, the card two lines tall`() = h2("384/1.0", Evening.NONE, twoLines = true)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H2, 384 dp, font 1,0, Dark since - Approaching under the evening line, the card two lines tall`() = h2("384/1.0", Evening.DARK_SINCE, twoLines = true)

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `H2, 320 dp, font 1,0, Dark since - Approaching under the evening line, the card two lines tall`() = h2("320/1.0", Evening.DARK_SINCE, twoLines = true)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `H2, 823 x 384, font 1,0, before sunset - Approaching under the two evening lines`() = h2("823x384/1.0", Evening.BEFORE_SUNSET, twoLines = true)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `H2, 384 dp, font 2,0, no evening time - Approaching in the right part`() = h2("384/2.0", Evening.NONE, twoLines = false)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `H8, 823 x 384, font 2,0 - the status words take their own line under the parts, and the right part no daytime width`() = h8("823x384/2.0")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `H8, 360 dp, font 2,0 - the status words take their own line under the parts, and the right part no daytime width`() = h8("360/2.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H4, 384 dp, font 1,0 - off the trail to a waypoint the card reads Off trail`() = offTrail("384/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `H4, 823 x 384, font 1,0 - off the trail to a waypoint the card reads Off trail`() = offTrail("823x384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `H4, 384 dp, font 1,0 - Trail routes not downloaded loses its tail and wraps in the status space`() = noteWraps("384/1.0", listOf("Trail routes not", "downloaded"))

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `H4, 823 x 384, font 1,0 - Trail routes not downloaded loses its tail and wraps in the status space`() = noteWraps("823x384/1.0", listOf("Trail routes not", "downloaded"))
}
