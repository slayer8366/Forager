package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_BUTTON_DP
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_CLEAR_GAP_DP
import com.zynergylabs.forager.app.ui.map.attributionEndInsetClearOf
import com.zynergylabs.forager.app.ui.map.attributionHiddenUnder
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 6, "the credit 'i' while tucked" (RECORD -2187). The owner, to "While the route card is tucked to
 * a strip, should the map's 'i' (credit) button come back where the card rested?": "Come back"; restated and confirmed: tucked, the
 * spot where the card rested is clear, so the "i" shows there again; the card brought back out, the "i" hides under it as today (D28
 * with D30, AttributionUnderCardTest).
 *
 * As there, the "i" is MapLibre's own view and the map is a stub, so this asserts what the map is handed
 * (`renderMode.attributionHideUnder`) and the decision `MapAttributionInsets` makes with it (`attributionHiddenUnder`), against the
 * "i"'s rectangle worked out from the same margins and the cards as drawn: the route card's own node with it out, its strip's
 * (`ROUTE_CARD_TAB_DRAWN_TAG`) tucked. The card is tucked and brought back by real swipes and a real tap, as a finger does it.
 */
abstract class AttributionWhileTuckedTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule)

    private fun px(value: androidx.compose.ui.unit.Dp): Int = with(composeRule.density) { value.roundToPx() }

    private fun DpRect.inMapPx(): Rect {
        val slot = rig.bounds("map-slot")
        return with(composeRule.density) { Rect((left - slot.left).toPx(), (top - slot.top).toPx(), (right - slot.left).toPx(), (bottom - slot.top).toPx()) }
    }

    /** The "i"'s rectangle in the map's px, and whether the map hides it, from what the map is handed (MapLibre's margins as measured). */
    private fun iAndDecision(): Pair<Rect, Boolean> {
        val mode = rig.map.renderMode ?: throw AssertionError("the map slot was given a render mode")
        val slot = rig.bounds("map-slot")
        val width = px(slot.right - slot.left)
        val height = px(slot.bottom - slot.top)
        val defaults = intArrayOf(px(4.dp), px(4.dp), px(52.dp), px(4.dp))
        val bottom = px(mode.attributionBottomInset ?: mode.bottomInset)
        val button = px(ATTRIBUTION_BUTTON_DP.dp)
        val end = attributionEndInsetClearOf(mode.attributionKeepClear, width, height, defaults, bottom, px(mode.attributionEndInset), false, button, px(ATTRIBUTION_CLEAR_GAP_DP.dp))
        val right = (width - defaults[2] - end).toFloat()
        val bottomEdge = (height - defaults[3] - bottom).toFloat()
        return Rect(right - button, bottomEdge - button, right, bottomEdge) to
            attributionHiddenUnder(mode.attributionHideUnder, width, height, defaults, bottom, end, false, button)
    }

    private fun tucked() = rig.plotting.uiState.value.tucked

    /** The cards as drawn over the map: the route card whole while it is out, only its strip while it is tucked; the navigation card. */
    private fun drawnCards(navigationCard: Boolean): List<Pair<String, Rect>> = buildList {
        if (rig.count(ROUTE_CARD_TAG) > 0) {
            if (tucked()) add("route card's strip" to rig.bounds(ROUTE_CARD_TAB_DRAWN_TAG).inMapPx()) else add("route card" to rig.bounds(ROUTE_CARD_TAG).inMapPx())
        }
        if (navigationCard && rig.count(NAVIGATION_HUD_TAG) > 0) add("navigation card" to rig.bounds(NAVIGATION_HUD_TAG).inMapPx())
    }

    private fun near(a: Rect, b: Rect) = listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).all { kotlin.math.abs(it) <= 1.5f }

    /** The map is handed what is drawn, and hides the "i" exactly where something drawn covers it. Returns whether it is hidden. */
    protected fun assertDecision(where: String, navigationCard: Boolean): Boolean {
        val handed = rig.map.renderMode!!.attributionHideUnder
        val cards = drawnCards(navigationCard)
        cards.forEach { (name, rect) ->
            assertTrue("$where: the map is handed the $name as drawn, $rect (handed $handed)", handed.any { near(it, rect) })
        }
        assertEquals("$where: the map is handed only what is drawn, ${cards.map { it.first }} (handed $handed)", cards.size, handed.size)
        val (i, hidden) = iAndDecision()
        val covering = cards.filter { it.second.overlaps(i) }.map { it.first }
        println("credit-tucked $where: i $i, drawn ${cards.map { "${it.first} ${it.second}" }}, covered by $covering, hidden $hidden")
        assertEquals("$where: the \"i\" at $i is hidden exactly while something drawn covers it (covered by $covering)", covering.isNotEmpty(), hidden)
        return hidden
    }

    /** A real sideways swipe across the middle of the card, toward its edge, tucks it. */
    protected fun tuck() {
        val onRight = (rig.screen().right - rig.bounds(ROUTE_CARD_TAG).right).value < 1f
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            val from = if (onRight) width * 0.2f else width * 0.8f
            val to = if (onRight) width * 0.8f else width * 0.2f
            swipe(Offset(from, y), Offset(to, y), durationMillis = 300)
        }
        rig.settle()
        assertTrue("the swipe tucked the card", tucked())
    }

    /** A real tap on the strip brings the card back. */
    protected fun bringBack() {
        composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG).performTouchInput { click(center) }
        rig.settle()
        assertFalse("the tap brought the card back", tucked())
    }

    /** The map's fullscreen button, tapped where it is drawn. */
    protected fun enterFullscreen() {
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(center) }
        rig.settle()
    }

    /** Out, tucked, out again: the "i" hidden, shown, hidden, and its corner never moving. */
    protected fun assertOutTuckedOut(where: String, navigationCard: Boolean, hiddenWhileTucked: Boolean = false) {
        val before = iAndDecision().first
        assertTrue("$where, card out: precondition, the card covers the \"i\", hidden as today", assertDecision("$where, card out", navigationCard))
        tuck()
        assertEquals("$where, tucked: the \"i\"'s corner does not move", before, iAndDecision().first)
        assertEquals(
            "$where, tucked: ${if (hiddenWhileTucked) "the navigation card still covers the \"i\"" else "the spot where the card rested is clear, so the \"i\" comes back"}",
            hiddenWhileTucked,
            assertDecision("$where, tucked", navigationCard),
        )
        bringBack()
        assertTrue("$where, brought back out: the \"i\" hides under the card again", assertDecision("$where, brought back", navigationCard))
        assertEquals("$where, brought back: the \"i\"'s corner has not moved", before, iAndDecision().first)
    }
}

/** The S22 in landscape (823 x 384 dp), as AttributionUnderCardLandscapeTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class AttributionWhileTuckedLandscapeTest : AttributionWhileTuckedTests() {

    @Test
    fun `not navigating, the card over the corner of the i, tucked the i comes back and out again it hides, peek and expanded`() {
        rig.setScreen()
        rig.dragBarAcross(rightward = false)
        listOf(1, 3).forEach { n ->
            listOf(false, true).forEach { expanded ->
                rig.plotTo(n)
                rig.setExpanded(expanded)
                assertOutTuckedOut("landscape, the L left, $n stops, ${if (expanded) "expanded" else "peek"}", navigationCard = false)
            }
        }
    }

    /**
     * Resting on the navigation card (C21): the "i" is under the navigation card there, not the route card (AttributionUnderCardTest's
     * prints: the route card ends above the "i", the navigation card covers it), so tucking the route card leaves it covered and hidden,
     * by D28. What this asserts is that the route card counts only as its strip while tucked, and the "i" follows what is drawn.
     */
    @Test
    fun `navigating, on the navigation card, tucked the route card counts only as its strip and the navigation card still covers the i`() {
        rig.setScreen(navigating = true)
        listOf(false, true).forEach { expanded ->
            rig.plotTo(3)
            rig.setExpanded(expanded)
            assertOutTuckedOut("landscape navigating, 3 stops, ${if (expanded) "expanded" else "peek"}", navigationCard = true, hiddenWhileTucked = true)
        }
    }
}

/** The S22 in portrait. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AttributionWhileTuckedPortraitTest : AttributionWhileTuckedTests() {

    /**
     * In portrait the card rests LEGEND_ATTRIBUTION_CLEARANCE (32 dp) above the bottom inset, which clears the "i" until the "i" sits
     * higher than that: in fullscreen, where it clears the system navigation bar (AvailabilityCompactScaffold,
     * attributionButtonBottomInset), as Amendment 9 saw it on the S22. Robolectric reports no insets, so a 48 dp navigation bar (the
     * three-button bar) is delivered here as the platform would.
     */
    @Test
    fun `fullscreen with the bar on the left, the card over the i, tucked the i comes back and out again it hides`() {
        rig.setScreen()
        rig.dragBarAcross(rightward = false)
        rig.applyInsets(left = 0.dp, right = 0.dp, bottom = 48.dp)
        enterFullscreen()
        listOf(1, 3).forEach { n ->
            listOf(false, true).forEach { expanded ->
                rig.plotTo(n)
                rig.setExpanded(expanded)
                assertOutTuckedOut("portrait fullscreen, the bar left, $n stops, ${if (expanded) "expanded" else "peek"}", navigationCard = false)
            }
        }
    }
}
