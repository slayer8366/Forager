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
     */
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0 - short of room, the line keeps the heading, the height and the grid reference as they change`() {
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
        // What the line keeps is decided on the values as drawn, as before H7 (RECORD -713), so "Alt" comes and goes with the heading's
        // width here (281° W: no room for it; 89° E: room), as it did before H7; the three values stay in every state.
        for ((name, kept) in listOf("facing 281°" to first, "facing 89°" to turned, "at 3000 m" to higher)) {
            if (!kept.containsAll(tags)) problems += "$name, the line keeps $kept, not all of $tags"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0 - with the heading given way, a height or the form changing moves nothing else`() = assertNothingShifts("320/1.0", headingShown = false)

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `412 dp, font 1,0 - a heading, a height or the grid reference's form changing moves nothing else`() = assertNothingShifts("412/1.0", headingShown = true)
}
