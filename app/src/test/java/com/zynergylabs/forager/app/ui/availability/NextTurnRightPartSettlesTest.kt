package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.routeCardLegTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-11-06-01, Amendment 1, J7 (RECORD -2183), the owner: "(b) Arrow to the right part", the option reading "where the
 * evening line has already stepped off for room, the arrow goes to the card's right part as it does in the daytime (one line shorter,
 * about 32 dp; it needs a check that this can't loop)"; the planner: "Prove it can't loop (a settling test across frames)".
 *
 * The S22's landscape window at font 2.0, in the evening, its insets and rotation, navigating, real touches on the route card's handle
 * ([RouteCardBarRig]): the case J6's stop measured, where the sundown line has already left the card for room before any route card
 * opens (the card can't fit it even moved up). The next turn sits in the right part, and the card, the next turn's place and the route
 * card's first leg row are read every frame for three seconds after each step has settled: a loop between the right part and the own
 * line (the card a line shorter making room for the evening line, which takes the right part back) would move one of them.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class NextTurnRightPartSettlesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT, clock = ROUTE_CARD_EVENING_CLOCK)

    private fun clipped(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private data class Frame(val card: DpRect, val nextTurn: DpRect, val legRow: DpRect?, val sundownLines: Int)

    private fun frame(withLegRow: Boolean) = Frame(
        card = rig.bounds(NAVIGATION_HUD_TAG),
        nextTurn = rig.bounds(NEXT_TURN_SPACE_TAG),
        legRow = if (withLegRow) clipped(routeCardLegTag(0)) else null,
        sundownLines = rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG),
    )

    /** [FRAMES] frames after [where] has settled, each read; every one must be the first. */
    private fun assertStaysPut(where: String, withLegRow: Boolean): Frame {
        rig.settle()
        composeRule.mainClock.autoAdvance = false
        val frames = try {
            (0 until FRAMES).map {
                composeRule.mainClock.advanceTimeByFrame()
                frame(withLegRow)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        val first = frames.first()
        val moved = frames.withIndex().filter { it.value != first }
        println("J7 settle, $where: $first; frames that differ: ${moved.size} of $FRAMES${moved.firstOrNull()?.let { ", first at ${it.index}: ${it.value}" } ?: ""}")
        assertTrue("J7, $where: nothing moves across $FRAMES frames once settled (no loop); first $first, then ${moved.take(3)}", moved.isEmpty())
        return first
    }

    /** The next turn is in the card's right part: right of the divider, its top within the parts' first line (not under the turn line). */
    private fun assertInRightPart(where: String, f: Frame) {
        val divider = rig.bounds(NAVIGATION_HUD_DIVIDER_TAG)
        assertTrue("J7, $where: the next turn is in the right part, right of the divider (${divider.right}); it is at ${f.nextTurn}", f.nextTurn.left >= divider.right - 0.5.dp)
    }

    private fun tapHandle() {
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rig.settle()
    }

    @Test
    fun `J7 - evening, 823 x 384, font 2_0, the line off for room - the next turn takes the right part and nothing loops, route card open or not`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)

        val closed = assertStaysPut("no route card", withLegRow = false)
        assertEquals("precondition: the sundown line has left the card for room before any route card opens (J6's measured case)", 0, closed.sundownLines)
        assertInRightPart("no route card", closed)

        rig.plotTo(6)
        if (rig.plotting.uiState.value.expanded) tapHandle()
        assertInRightPart("6 stops, peek", assertStaysPut("6 stops, peek", withLegRow = false))

        tapHandle()
        assertEquals("the route card is expanded", true, rig.plotting.uiState.value.expanded)
        val open = assertStaysPut("6 stops, expanded", withLegRow = true)
        assertInRightPart("6 stops, expanded", open)
        val row = open.legRow!!
        assertTrue("J7: with the card a line shorter, the route card's first leg row shows, height ${row.bottom - row.top}", row.bottom - row.top > 0.dp)
        assertEquals("the sundown line stays off", 0, open.sundownLines)

        tapHandle()
        rig.closeCard()
        val after = assertStaysPut("the route card closed", withLegRow = false)
        assertEquals("J7: closed again, the card is where it was before the route card opened", closed.card, after.card)
        assertEquals("J7: closed again, the next turn is where it was", closed.nextTurn, after.nextTurn)
    }

    private companion object {
        /** Three seconds of frames, longer than any of the cards' motion. */
        const val FRAMES = 180
    }
}
