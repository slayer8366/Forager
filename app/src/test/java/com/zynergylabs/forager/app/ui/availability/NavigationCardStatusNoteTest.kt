package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10 (RECORD -2181), Amendment 1, F2, the owner: "Keep the ones asking something": "Remove the ones that only
 * say why (no routes here, another area, far from any trail, no clear way in…). Keep "Trail routes not downloaded" and "Trail
 * routes need an app update", since you could act on them." Widening F1: "the straight line is the explanation".
 *
 * Through the real screen, in portrait and landscape and at font scales 1.0 and 2.0, each window reads three states of the way to
 * a waypoint, the straight line:
 * - with no route given (the straight HUD as it was, no note), the card's own height with no status line;
 * - with a note that goes, "No trail routes here" ([State.STRAIGHT_NOTE_GONE]): no status line, and the card exactly as tall;
 * - with a note that stays, "Trail routes not downloaded" ([State.STRAIGHT_NOTE_KEPT]): its words, whole or short, and the card
 *   exactly that line taller. This is the positive control that the status line still works for the two that stay.
 *
 * The note itself is unchanged (its straight line, its retry and its log line); only its words leave the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardStatusNoteTest : NavigationCardKindLineScreen() {

    /**
     * Since Amendment 1, H2 and H4, the note that stays reads without "· straight line", in the right part's status space, wrapping
     * there; where that space holds it beside the left part's two lines ([twoLines]), the card is exactly as tall as with no note. Was
     * `assertOneLineShorter`'s claim for it: whole or short ("Trail routes not downloaded · straight line") on a line of its own under
     * the parts, the card "exactly that line taller".
     */
    private fun assertNoteGoesAndStays(where: String, twoLines: Boolean) {
        val problems = mutableListOf<String>()
        fun height(c: Card) = c.hud.bottom - c.hud.top
        show(State.STRAIGHT, Evening.NONE)
        val plain = card(where).also { printed(where, State.STRAIGHT, it) }
        show(State.STRAIGHT_NOTE_KEPT, Evening.NONE)
        val kept = card(where).also { printed(where, State.STRAIGHT_NOTE_KEPT, it) }
        show(State.STRAIGHT_NOTE_GONE, Evening.NONE)
        val gone = card(where).also { printed(where, State.STRAIGHT_NOTE_GONE, it) }
        // Positive controls: each state is the straight line, and the plain one has no status line to begin with.
        for ((name, c) in listOf("plain" to plain, "kept" to kept, "gone" to gone)) {
            if (c.figure.text != "≈ 1250 ft" || c.kind?.text != "straight") problems += "positive control: $name reads <${c.figure.text}> <${c.kind?.text}>, not the straight line"
        }
        if (plain.status.isNotEmpty()) problems += "positive control: the straight line with no route has a status line <${plain.status}>"
        // The note that stays still shows, without "· straight line" (H4).
        if (kept.status != "Trail routes not downloaded") problems += "the note that asks something reads <${kept.status}>, not <Trail routes not downloaded>"
        val line = kept.statusBox?.let { it.bottom - it.top }
        if (line == null || line.value < 1f) problems += "positive control: the note that stays has no height ($line)"
        if (kept.statusBox != null && kept.statusBox.left < kept.divider.right - 0.5.dp) problems += "the note that stays ${kept.statusBox} is not in the right part (divider ${kept.divider})"
        // F2: the note that only says why leaves no status line, and the card is the plain straight line's.
        if (gone.status.isNotEmpty()) problems += "the note that only says why still reads <${gone.status}>: F2 leaves no status line"
        if (abs((height(gone) - height(plain)).value) > 0.5f) problems += "the card with the note gone is ${height(gone)}, not the plain straight line's ${height(plain)}"
        if (twoLines && abs((height(kept) - height(plain)).value) > 0.5f) problems += "the card with the note that stays is ${height(kept)}, not the plain ${height(plain)}: H2's two lines"
        println("STATUSNOTE|$where|plain ${height(plain)}|kept ${height(kept)} <${kept.status}> lines ${kept.statusLaid?.layout?.lineCount}|gone ${height(gone)} <${gone.status}>|note $line")
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - a note that only says why leaves no status line, and the one that stays keeps the card two lines tall`() = assertNoteGoesAndStays("384/1.0", twoLines = true)

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0 - a note that only says why leaves no status line, and the one that stays keeps the card two lines tall`() = assertNoteGoesAndStays("320/1.0", twoLines = true)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0 - a note that only says why leaves no status line, and the one that stays keeps the card two lines tall`() = assertNoteGoesAndStays("823x384/1.0", twoLines = true)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0 - a note that only says why leaves no status line, and the one that stays keeps the card two lines tall`() = assertNoteGoesAndStays("780x360/1.0", twoLines = true)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0 - a note that only says why leaves no status line, and the one that stays is in the right part`() = assertNoteGoesAndStays("384/2.0", twoLines = false)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0 - a note that only says why leaves no status line, and the one that stays is in the right part`() = assertNoteGoesAndStays("823x384/2.0", twoLines = false)
}
