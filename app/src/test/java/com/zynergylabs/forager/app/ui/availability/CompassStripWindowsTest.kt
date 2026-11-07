package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10, Amendment 1, H7 (RECORD -2181), the owner: "Info that changes and shares a line with other text should get its
 * own window so it doesn't shift all text on the same line"; then "If it's too small, then have them squeeze together to make room. It
 * fit before, so it should still fit now". Through the real screen, out of navigation and in (H1 makes them one strip), the strip's line
 * read as each value changes in turn: the heading (facing 281° then 89°, "281° W" then "89° E"), the height (120 m then 3000 m, "394 ft"
 * then "9843 ft") and the grid reference's form (a tap on it). After each change, every other value is where it was; and the line keeps
 * what it kept before H7 (RECORD -713's order on the values as drawn). Was: the readouts a centred group, so a narrower heading drew the
 * height and the grid reference toward it. The grid reference's words are compared, never printed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompassStripWindowsTest : NavigationCardKindLineScreen() {

    private val tags = listOf(COMPASS_STRIP_HEADING_TAG, COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG)

    private class Piece(val bounds: DpRect, val text: String)

    private fun strip(): Map<String, Piece?> = tags.associateWith { tag ->
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.let { n ->
            val b = with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
            Piece(b, n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "")
        }
    }

    private fun centre(p: Piece) = (p.bounds.left + p.bounds.right) / 2

    /** Each problem with [changed] against [before]: [moved] is the value that changed; every other value's centre stays put. */
    private fun stays(where: String, what: String, before: Map<String, Piece?>, after: Map<String, Piece?>, moved: String): List<String> {
        val problems = mutableListOf<String>()
        val b = before[moved]
        val a = after[moved]
        if (b != null && a != null && b.text == a.text) problems += "positive control: $where, $what: <$moved> did not change"
        for (tag in tags - moved) {
            val x = before[tag]
            val y = after[tag]
            if ((x == null) != (y == null)) problems += "$where, $what: <$tag> is ${if (y == null) "gone" else "drawn"} after, ${if (x == null) "not drawn" else "drawn"} before"
            if (x != null && y != null && abs((centre(x) - centre(y)).value) > 0.5f) problems += "$where, $what: <$tag> moved from ${centre(x)} to ${centre(y)}"
        }
        return problems
    }

    /**
     * [toggle]: also tap the grid reference over to its other form. Not at 360 dp, where the decimal pair is narrow enough that "Alt"
     * fits again (RECORD -713 on the values as drawn, as before H7, which keeps the portrait line deciding on the form shown), so the
     * line itself changes there and the label's coming back moves its neighbours.
     */
    private fun assertNothingShifts(where: String, headingShown: Boolean, toggle: Boolean = true) {
        show(State.TRAIL, Evening.NONE, Facing.TURN)
        setNavigating(false)
        val problems = mutableListOf<String>()
        val first = strip()
        // Positive controls: what the line keeps here, as before H7 (RECORD -713 on the values as drawn).
        if ((first[COMPASS_STRIP_HEADING_TAG] != null) != headingShown) problems += "positive control: $where: the heading is ${if (headingShown) "not " else ""}drawn"
        if (first[COMPASS_STRIP_COORDINATES_TAG] == null) problems += "positive control: $where: no grid reference drawn"
        show(State.TRAIL, Evening.NONE, Facing.AHEAD)
        setNavigating(false)
        val turned = strip()
        if (headingShown) problems += stays(where, "the heading changing", first, turned, COMPASS_STRIP_HEADING_TAG)
        setAltitude(3_000.0)
        val higher = strip()
        if (higher[COMPASS_STRIP_ELEVATION_TAG] != null) problems += stays(where, "the height changing", turned, higher, COMPASS_STRIP_ELEVATION_TAG)
        if (!toggle) {
            println("STRIPWINDOWS|$where|first ${first.mapValues { it.value?.bounds }}|turned ${turned.mapValues { it.value?.bounds }}|higher ${higher.mapValues { it.value?.bounds }}")
            assertTrue(problems.joinToString("\n"), problems.isEmpty())
            return
        }
        composeRule.onNode(hasTestTag(COMPASS_STRIP_COORDINATES_TAG), useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val toggled = strip()
        val coordinatesChanged = higher[COMPASS_STRIP_COORDINATES_TAG]?.text != toggled[COMPASS_STRIP_COORDINATES_TAG]?.text
        if (!coordinatesChanged) problems += "positive control: $where: the grid reference's form did not change on a tap"
        for (tag in tags - COMPASS_STRIP_COORDINATES_TAG) {
            val x = higher[tag]
            val y = toggled[tag]
            if (x != null && y != null && abs((centre(x) - centre(y)).value) > 0.5f) problems += "$where, the form changing: <$tag> moved from ${centre(x)} to ${centre(y)}"
        }
        println("STRIPWINDOWS|$where|first ${first.mapValues { it.value?.bounds }}|turned ${turned.mapValues { it.value?.bounds }}|higher ${higher.mapValues { it.value?.bounds }}|toggled ${toggled.mapValues { it.value?.bounds }}")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - a heading, a height or the grid reference's form changing moves nothing else`() = assertNothingShifts("384/1.0", headingShown = true)

    /**
     * 360 dp: the line is short of room for the windows' widest values (307.3 dp needed of 290 with "Alt"), so they squeeze toward the
     * values shown and a change there may move its neighbours (the planner's reading: "The heading moves the others only when the strip
     * is short of room"). What the line keeps: the heading, the height and the grid reference, facing either way and at either height;
     * "Alt" comes and goes with the heading's width, as before H7.
     *
     * Amendment 1, H11 (the owner: "(b) Keep "Alt" when room", the option reading ""Alt" shows when the heading leaves room, so it comes
     * and goes as you turn, as it did before this update"): whether the heading stays is decided on its widest reading (H7 refined
     * again), but "Alt" shows wherever the heading as drawn leaves room. Facing 281° ("281° W") and 89° ("89° E"), and at 3000 m, it
     * does here, so the line keeps all three values and "Alt" in each. Under H7 refined again alone, "Alt" went in every state (E13,
     * stop 1: "the line keeps [heading, elevation, coordinates]").
     */
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0 - short of room, the line keeps the heading, Alt and the height as they change`() {
        val problems = mutableListOf<String>()
        fun kept(): List<String> = (tags + NAVIGATION_LABEL_TAG).filter { tag ->
            composeRule.onAllNodes(hasTestTag(tag) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag("compass-elevation-strip")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        show(State.TRAIL, Evening.NONE, Facing.TURN)
        setNavigating(false)
        val first = kept()
        show(State.TRAIL, Evening.NONE, Facing.AHEAD)
        setNavigating(false)
        val turned = kept()
        setAltitude(3_000.0)
        val higher = kept()
        // H11: the heading kept on its widest reading, "Alt" on the heading as drawn, each on the squeezed form (the planner: ""Alt" is
        // kept where it fits, follows "It fit before, so it should still fit now""): here all three values and "Alt", facing either way
        // and at either height.
        val all = tags + NAVIGATION_LABEL_TAG
        println("STRIPKEPT|360/1.0|facing 281° $first|facing 89° $turned|at 3000 m $higher")
        for ((name, kept) in listOf("facing 281°" to first, "facing 89°" to turned, "at 3000 m" to higher)) {
            if (kept != all) problems += "$name, the line keeps $kept, not $all"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0 - the heading stays off as the phone turns, and nothing else moves`() = assertHeadingStaysOff320()

    /**
     * Amendment 1, H7 refined again (the planner's call, RECORD -2181): "whether the heading stays on the strip is decided on its widest
     * reading, so it never appears and drops out as the phone turns. At 320 dp it stays off, as before this dispatch." So at 320 dp the
     * heading is drawn in no state, facing 281° or 89° and at either height, and the height and the grid reference stay where they
     * were as the phone turns. Was (H7 refined, decided on the squeezed form): "the heading is kept where the squeezed line holds it
     * ("89° E" yes, "281° W" no), so it comes and goes as the phone turns"; the claim then was only that the height and the grid
     * reference are always kept.
     */
    private fun assertHeadingStaysOff320() {
        val problems = mutableListOf<String>()
        val readings = mutableListOf<Pair<String, Map<String, Piece?>>>()
        for (facing in listOf(Facing.TURN, Facing.AHEAD)) {
            show(State.TRAIL, Evening.NONE, facing)
            setNavigating(false)
            readings += "facing ${if (facing == Facing.TURN) "281°" else "89°"}" to strip()
        }
        setAltitude(3_000.0)
        readings += "facing 89°, at 3000 m" to strip()
        for ((name, s) in readings) {
            if (s[COMPASS_STRIP_HEADING_TAG] != null) problems += "320/1.0 $name: the heading <${s[COMPASS_STRIP_HEADING_TAG]?.text}> is drawn"
            for (tag in listOf(COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG)) if (s[tag] == null) problems += "320/1.0 $name: <$tag> not kept"
            println("STRIPWINDOWS|320/1.0|$name|${s.mapValues { it.value?.bounds }}")
        }
        // The phone turning (the first two readings): neither the height nor the grid reference moves.
        val (a, b) = readings[0].second to readings[1].second
        for (tag in listOf(COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG)) {
            val x = a[tag]
            val y = b[tag]
            if (x != null && y != null && abs((centre(x) - centre(y)).value) > 0.5f) problems += "320/1.0, the phone turning: <$tag> moved from ${centre(x)} to ${centre(y)}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `412 dp, font 1,0 - a heading, a height or the grid reference's form changing moves nothing else`() = assertNothingShifts("412/1.0", headingShown = true)
}
