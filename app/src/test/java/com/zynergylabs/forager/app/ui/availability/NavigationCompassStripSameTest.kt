package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10, Amendment 1, H1 (RECORD -2181), the owner, verbatim: "Keep the compass strip the exact same when in
 * navigation mode as out; so keep the north compass out of the navigation panel." Through the real screen: the strip's line read
 * while navigating home by trail, then with navigation stopped, at the same fix and heading. Its north arrow, heading, height and grid
 * reference are each drawn, in the same words, at the same places. Was (RECORD -948): navigating, the line dropped the north arrow
 * and the heading, which the card showed once.
 *
 * The grid reference's words are compared, never printed: the point is the made-up P, but a grid reference is still not written out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCompassStripSameTest : NavigationCardKindLineScreen() {

    private class Piece(val bounds: DpRect, val text: String)

    private fun strip(): Map<String, Piece?> = listOf(COMPASS_STRIP_NEEDLE_TAG, COMPASS_STRIP_HEADING_TAG, COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG).associateWith { tag ->
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()?.let { n ->
            val b = with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
            Piece(b, n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "")
        }
    }

    private fun assertSameInAndOut(where: String) {
        show(State.TRAIL, Evening.NONE, Facing.TURN)
        val navigating = strip()
        setNavigating(false)
        val out = strip()
        val problems = mutableListOf<String>()
        // Positive controls: out of navigation the strip draws its arrow and its grid reference (the heading and the height give way
        // first where the line is short, RECORD -713, so they are compared wherever they are drawn), and the card is gone.
        for (tag in listOf(COMPASS_STRIP_NEEDLE_TAG, COMPASS_STRIP_COORDINATES_TAG)) if (out[tag] == null) problems += "positive control: $where: out of navigation the strip draws no <$tag>"
        val cardLeft = composeRule.onAllNodes(hasTestTag(NAVIGATION_HUD_EXIT_TAG), useUnmergedTree = true).fetchSemanticsNodes().size
        if (cardLeft != 0) problems += "positive control: $where: the card's X is still drawn out of navigation"
        for ((tag, o) in out) {
            val n = navigating[tag]
            when {
                o == null && n == null -> Unit
                o == null -> problems += "$where: navigating, the strip draws <$tag>, which it does not out of navigation"
                n == null -> problems += "$where: navigating, the strip draws no <$tag> (out of navigation it does)"
                else -> {
                    if (n.text != o.text) {
                        problems += if (tag == COMPASS_STRIP_COORDINATES_TAG) "$where: navigating, the grid reference reads differently" else "$where: navigating, <$tag> reads <${n.text}>, out of navigation <${o.text}>"
                    }
                    val moved = listOf(n.bounds.left - o.bounds.left, n.bounds.top - o.bounds.top, n.bounds.right - o.bounds.right, n.bounds.bottom - o.bounds.bottom).maxOf { abs(it.value) }
                    if (moved > 0.5f) problems += "$where: <$tag> is at ${n.bounds} navigating, ${o.bounds} out of navigation"
                }
            }
        }
        // The heading the strip shows is the one faced (281°, due west of north by 79°).
        val heading = out[COMPASS_STRIP_HEADING_TAG]?.text
        if (heading != null && heading != "281° W") problems += "positive control: $where: the strip's heading reads <$heading>, not <281° W>"
        println("STRIPSAME|$where|navigating ${navigating.mapValues { it.value?.bounds }}|out ${out.mapValues { it.value?.bounds }}")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - the strip is the same navigating as out of navigation`() = assertSameInAndOut("384/1.0")

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0 - the strip is the same navigating as out of navigation`() = assertSameInAndOut("320/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0 - the strip is the same navigating as out of navigation`() = assertSameInAndOut("823x384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0 - the strip is the same navigating as out of navigation`() = assertSameInAndOut("384/2.0")
}
