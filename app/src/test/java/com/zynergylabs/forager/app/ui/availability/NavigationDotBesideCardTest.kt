package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.NavigationSideInsets
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
 * D29 (dispatch 2026-10-30-08, RECORD -1979, -1980). The owner: "Keep the dot clear of the card (Recommended)", whose option read
 * "While the route card is expanded, the navigation view shifts so your dot sits in the open map beside the card, and moves back when
 * the card collapses." Seen on the S22 (-1978): landscape, navigating, the card expanded, the walker's dot under the card's left part.
 *
 * The camera is MapLibre's and cannot run under Robolectric, so the map is a stub. What this asserts, on the real Maps tab: the side
 * padding the navigation view is asked for (`NavigationViewRequest.sideInsetsPx`), and where that puts the walker's dot. With a
 * padding of l and r the dot sits at the middle of what is left, l + (W - l - r) / 2, which this checks lies in the open map between
 * the L and the card, read from their own nodes, a dot's radius clear of each. Peek, no card and a closed card ask for none. Whether
 * MapLibre then moves the camera so is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class NavigationDotBesideCardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun insets(): NavigationSideInsets =
        rig.map.renderMode?.navigationView?.sideInsetsPx ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    private fun assertNone(where: String) = assertEquals("$where: the navigation view asks for no side padding", NavigationSideInsets.NONE, insets())

    private fun assertDotBesideTheCard(where: String) {
        val side = insets()
        val slot = rig.bounds("map-slot")
        val card = rig.bounds(ROUTE_CARD_TAG)
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val (dotX, open) = with(composeRule.density) {
            val width = (slot.right - slot.left).toPx()
            val dot = side.leftPx + (width - side.leftPx - side.rightPx) / 2
            (slot.left + dot.toFloat().toDp()) to ((slot.left + side.leftPx.toFloat().toDp())..(slot.right - side.rightPx.toFloat().toDp()))
        }
        val (gapStart, gapEnd) = if (card.left > l.left) l.right to card.left else card.right to l.left
        println("D29 $where: side $side, dot x $dotX, open $open, gap $gapStart..$gapEnd, card ${card.left}..${card.right}, L ${l.left}..${l.right}")
        assertTrue("$where: the dot at $dotX is clear of the card ${card.left}..${card.right} by its radius", dotX + WALKER_DOT_CLEARANCE <= card.left || dotX - WALKER_DOT_CLEARANCE >= card.right)
        assertTrue("$where: the dot at $dotX is clear of the L ${l.left}..${l.right} by its radius", dotX + WALKER_DOT_CLEARANCE <= l.left || dotX - WALKER_DOT_CLEARANCE >= l.right)
        assertTrue("$where: the dot at $dotX is in the open map between the L and the card, $gapStart..$gapEnd", dotX > gapStart && dotX < gapEnd)
        assertEquals("$where: the dot is in the middle of the open map, within a dp", ((gapStart + gapEnd) / 2).value, dotX.value, 1f)
    }

    @Test
    fun `expanded, the dot shifts into the open map beside the card at 1, 3 and 6 stops, with the L on either side, and back when the card collapses`() {
        rig.setScreen(navigating = true)
        assertNone("no card")
        listOf(false, true).forEach { rightward ->
            rig.dragBarAcross(rightward)
            val side = if (rightward) "the L right" else "the L left"
            listOf(1, 3, 6).forEach { n ->
                rig.plotTo(n)
                rig.setExpanded(true)
                assertDotBesideTheCard("$side, $n stops, expanded")
                rig.setExpanded(false)
                assertNone("$side, $n stops, collapsed")
            }
            rig.setExpanded(true)
            rig.closeCard()
            assertNone("$side, the card closed while expanded")
        }
    }

    @Test
    fun `not navigating, the expanded card asks the map for nothing`() {
        rig.setScreen()
        rig.plotTo(3)
        rig.setExpanded(true)
        assertEquals("not navigating: no navigation view", null, rig.map.renderMode?.navigationView)
        assertTrue("precondition: the card shows", rig.count(ROUTE_CARD_TAG) > 0)
        assertTrue("precondition: a map", (rig.bounds("map-slot").right - rig.bounds("map-slot").left) > 0.dp)
    }
}
