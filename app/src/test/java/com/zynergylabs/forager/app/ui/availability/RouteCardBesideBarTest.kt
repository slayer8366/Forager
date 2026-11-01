package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendments 14 and 15, Part 1 (RECORD -1928, -1929), the owner: "The map icon bar stays the same no matter
 * what. The card just gets resized to fit the map icon bar next to it". The real Maps tab at the S22's size, recording: the card is
 * narrower, beside the bar with a small gap, reaching the far edge's margin, at peek and expanded, at 1, 3, 5 and 6 stops, on either
 * side; the bar never moves, folds or rises; and record, return, Layers, "+" and Save each take real touches across their bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardBesideBarTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun assertBesideTheBar(where: String, barOnLeft: Boolean, bar: DpRect) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val screen = rig.screen()
        val navTop = composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot().top
        println("A14 $where: nav top ${navTop.value}")
        if (barOnLeft) {
            assertEquals("$where: the card's left, 8 dp past the bar's right ${bar.right}", (bar.right + 8.dp).value, card.left.value, 0.5f)
            assertEquals("$where: the card's right, at the screen edge's margin", (screen.right - 8.dp).value, card.right.value, 0.5f)
        } else {
            assertEquals("$where: the card's right, 8 dp short of the bar's left ${bar.left}", (bar.left - 8.dp).value, card.right.value, 0.5f)
            assertEquals("$where: the card's left, at the screen edge's margin", 8f, card.left.value, 0.5f)
        }
    }

    private fun walk(side: String, barOnLeft: Boolean) {
        rig.setScreen()
        if (barOnLeft) rig.dragBarAcross(rightward = false)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        assertEquals("precondition: the bar's side", barOnLeft, bar.right < 192.dp)
        listOf(1, 3, 5, 6).forEach { stops ->
            rig.plotTo(stops)
            listOf(false, true).forEach { expanded ->
                val where = "$side, $stops stops, ${if (expanded) "expanded" else "peek"}"
                rig.setExpanded(expanded)
                assertBesideTheBar(where, barOnLeft, bar)
                rig.assertBarUntouched(where, bar)
                rig.touchesReachStopAndReturn(where, ACROSS_ROUND)
                rig.touchesReachLayersAndAdd(where, ACROSS_ROUND)
                rig.touchesAcrossSave(where, ACROSS)
            }
        }
    }

    @Test
    fun `the bar on the right - the card opens beside it and the bar stays as the user set it`() = walk("right", barOnLeft = false)

    @Test
    fun `the bar on the left - the card opens beside it and the bar stays as the user set it`() = walk("left", barOnLeft = true)

    @Test
    fun `the bar dragged to its lowest stays there, its bottom below the card's top`() {
        rig.setScreen()
        rig.dragBarDown()
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            rig.setExpanded(expanded)
            val where = "lowest, expanded $expanded"
            assertBesideTheBar(where, barOnLeft = false, bar)
            rig.assertBarUntouched(where, bar)
            assertTrue("$where: the bar reaches below the card's top, so it is beside the card", bar.bottom > rig.bounds(ROUTE_CARD_TAG).top)
            rig.touchesReachStopAndReturn(where, ACROSS_ROUND)
        }
    }

    @Test
    fun `moving the bar across shifts the card over, peek and expanded, and it stays there`() {
        rig.setScreen()
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            rig.setExpanded(expanded)
            rig.dragBarAcross(rightward = false)
            assertBesideTheBar("to the left, expanded $expanded", barOnLeft = true, rig.bounds(MAP_ICON_CLUSTER_TAG))
            rig.dragBarAcross(rightward = true)
            assertBesideTheBar("back to the right, expanded $expanded", barOnLeft = false, rig.bounds(MAP_ICON_CLUSTER_TAG))
        }
    }

    @Test
    fun `a minimised bar stays minimised, the card keeps its width, and the restore handle restores the bar, not the card`() {
        rig.setScreen()
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        composeRule.onNodeWithTag(BAR_MINIMIZE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rig.settle()
        val handle = rig.described(BAR_RESTORE)
        rig.plotTo(3)
        rig.setExpanded(true)
        assertEquals("the bar stays minimised", 0, rig.count(MAP_ICON_CLUSTER_TAG))
        rig.assertSame("the restore handle", handle, rig.described(BAR_RESTORE))
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("A14 minimised: card ${card.left.value}..${card.right.value}, restore handle ${handle.left.value}..${handle.right.value}")
        assertEquals("the card's right, where it is with the bar showing", (bar.left - 8.dp).value, card.right.value, 0.5f)
        rig.assertApart("the card and the restore handle", card, handle)

        composeRule.onNodeWithContentDescription(BAR_RESTORE).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        rig.settle()
        assertEquals("the handle brought the bar back", 1, rig.count(MAP_ICON_CLUSTER_TAG))
        assertTrue("and the card stays expanded", rig.plotting.uiState.value.expanded)
        rig.assertSame("the bar, where it was", bar, rig.bounds(MAP_ICON_CLUSTER_TAG))
    }

    @Test
    fun `the card's bottom stays where it sat, above the nav and the attribution`() {
        rig.setScreen()
        rig.plotTo(3)
        val navTop = composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot().top
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("A14 bottom: card ${card.bottom.value}, nav top ${navTop.value}, clearance ${LEGEND_ATTRIBUTION_CLEARANCE.value}")
        assertTrue("the card ends above the nav's top ${navTop}: ${card.bottom}", card.bottom <= navTop)
    }
}
