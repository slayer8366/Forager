package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.routeCardLegTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val STRIP = "compass-elevation-strip"

/**
 * Dispatch 2026-10-30-08, Amendment 14, Part 2 (RECORD -1928). The owner: "make landscape card the same size as the navigation card
 * that pops up, but have it pop up behind the navigation card and extend above it is they're already navigating"; "Same place, swaps
 * with the bar (Recommended)"; "Rests on top of the nav card (Recommended)". The real Maps tab in a landscape window, recording: the
 * strip's width, on the bottom edge, in the column away from the L, and on top of the navigation card while navigating. The L never
 * changes, and record, return, Layers, "+" and Save take real touches across their bounds.
 */
abstract class RouteCardLandscapeBarTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun lowerStops(stops: Int, expanded: Boolean, where: String, body: () -> Unit) {
        rig.plotTo(stops)
        rig.setExpanded(expanded)
        val save = rig.bounds(ROUTE_CARD_SAVE_TAG)
        val card = rig.bounds(ROUTE_CARD_TAG)
        assertTrue("$where: Save ${save.top}..${save.bottom} is inside the card ${card.top}..${card.bottom}", save.top >= card.top && save.bottom <= card.bottom + 0.5.dp)
        if (expanded && stops >= 5) assertRowsScroll(where, stops)
        body()
    }

    /** The rows' last leg is laid out below where the rows stop showing, so they scroll, and Save stayed on the card. */
    private fun assertRowsScroll(where: String, stops: Int) {
        val rows = rig.bounds("route-card-legs")
        val last = rig.bounds(routeCardLegTag(stops - 2))
        val total = rig.bounds("route-card-routed")
        println("A14 $where: rows ${rows.top.value}..${rows.bottom.value}, last leg ${last.top.value}..${last.bottom.value}, total line top ${total.top.value}")
        assertTrue("$where: the last leg's bottom ${last.bottom} runs past the total line's top ${total.top}, so the rows scroll", last.bottom > total.top)
    }

    private fun assertTouches(where: String) {
        rig.touchesReachStopAndReturn(where, ACROSS)
        rig.touchesReachLayersAndAdd(where, ACROSS)
        rig.touchesAcrossSave(where, ACROSS)
    }

    private fun assertInItsColumn(where: String, barOnLeft: Boolean, strip: DpRect, topLimit: DpRect) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        assertEquals("$where: the strip's width", (strip.right - strip.left).value, (card.right - card.left).value, 0.5f)
        assertEquals("$where: on the screen's bottom edge", rig.screen().bottom.value, card.bottom.value, 0.5f)
        if (barOnLeft) {
            assertEquals("$where: in the column on the right, under the strip", strip.right.value, card.right.value, 0.5f)
        } else {
            assertTrue("$where: in the column on the left: ${card.right} clears the L's ${bar.left}", card.right <= bar.left)
            assertEquals("$where: at the left", 0f, card.left.value, 0.5f)
        }
        assertTrue("$where: the top ${card.top} is below the strip's bottom ${strip.bottom}", card.top >= strip.bottom)
        assertTrue("$where: and below the search bar's bottom ${topLimit.bottom}", card.top >= topLimit.bottom)
    }

    private fun walk(side: String, barOnLeft: Boolean) {
        rig.setScreen()
        if (!barOnLeft) rig.dragBarAcross(rightward = true)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        assertEquals("precondition: the L's side", barOnLeft, bar.right < rig.screen().right / 2)
        val strip = rig.bounds(STRIP)
        val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
        println("A14 $side: strip ${strip.left.value}..${strip.right.value} x ${strip.top.value}..${strip.bottom.value}, search ${search.left.value}..${search.right.value} x ..${search.bottom.value}")
        listOf(1, 3, 5, 6).forEach { stops ->
            listOf(false, true).forEach { expanded ->
                val where = "landscape $side, $stops stops, ${if (expanded) "expanded" else "peek"}"
                lowerStops(stops, expanded, where) {
                    assertInItsColumn(where, barOnLeft, strip, search)
                    rig.assertBarUntouched(where, bar)
                    assertTouches(where)
                }
            }
        }
    }

    @Test
    fun `the L on the left - the card is in the strip's column at the strip's width`() = walk("L left", barOnLeft = true)

    @Test
    fun `the L on the right - the card swaps to the left column at the strip's width`() = walk("L right", barOnLeft = false)

    @Test
    fun `moving the L across shifts the card over, peek and expanded`() {
        rig.setScreen()
        val strip = rig.bounds(STRIP)
        val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            rig.setExpanded(expanded)
            rig.dragBarAcross(rightward = true)
            assertInItsColumn("to the right, expanded $expanded", barOnLeft = false, strip, search)
            rig.dragBarAcross(rightward = false)
            assertInItsColumn("back, expanded $expanded", barOnLeft = true, strip, search)
        }
    }

    /** Whether [back] is painted and hit-tested under [front], from their order under their nearest shared ancestor. */
    private fun isBehind(back: SemanticsNode, front: SemanticsNode): Boolean {
        fun chain(node: SemanticsNode): List<SemanticsNode> = generateSequence(node) { it.parent }.toList().reversed()
        val a = chain(back)
        val b = chain(front)
        val split = a.indices.first { i -> a[i].id != b.getOrNull(i)?.id }
        val siblings = a[split - 1].children
        return siblings.indexOfFirst { it.id == a[split].id } < siblings.indexOfFirst { it.id == b[split].id }
    }

    @Test
    fun `navigating - the card rests on the navigation card, behind it, with Save above it`() {
        rig.setScreen(navigating = true)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val strip = rig.bounds(STRIP)
        val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
        println("A14 navigating: navigation card ${hud.left.value}..${hud.right.value} x ${hud.top.value}..${hud.bottom.value}, strip bottom ${strip.bottom.value}, search bottom ${search.bottom.value}")
        listOf(1, 3, 5, 6).forEach { stops ->
            listOf(false, true).forEach { expanded ->
                val where = "navigating, $stops stops, ${if (expanded) "expanded" else "peek"}"
                lowerStops(stops, expanded, where) {
                    val card = rig.bounds(ROUTE_CARD_TAG)
                    val save = rig.bounds(ROUTE_CARD_SAVE_TAG)
                    println("A14 $where: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, Save ${save.top.value}..${save.bottom.value}")
                    assertEquals("$where: the navigation card's left", hud.left.value, card.left.value, 0.5f)
                    assertEquals("$where: the navigation card's right", hud.right.value, card.right.value, 0.5f)
                    assertTrue("$where: its lower edge ${card.bottom} is tucked behind the navigation card's top ${hud.top}", card.bottom > hud.top)
                    assertTrue("$where: Save's bottom ${save.bottom} shows above the navigation card's top ${hud.top}", save.bottom <= hud.top + 0.5.dp)
                    assertTrue("$where: the top ${card.top} is below the strip's bottom ${strip.bottom}", card.top >= strip.bottom)
                    rig.assertBarUntouched(where, bar)
                    assertTouches(where)
                }
            }
        }
        val card = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val navigationCard = composeRule.onNodeWithTag(NAVIGATION_HUD_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the card is behind the navigation card", isBehind(card, navigationCard))
    }

    @Test
    fun `the credit and the i rise above the card only where it sits over them, with the L on the right`() {
        rig.setScreen()
        val before = rig.map.renderMode!!.bottomInset
        rig.plotTo(3)
        assertEquals("the L on the left, the card is away from the credit", before.value, rig.map.renderMode!!.bottomInset.value, 0.5f)
        rig.dragBarAcross(rightward = true)
        val card = rig.bounds(ROUTE_CARD_TAG)
        val inset = rig.map.renderMode!!.bottomInset
        assertTrue("the credit's inset $inset clears the card's height ${card.bottom - card.top}", inset >= card.bottom - card.top)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardLandscapeBarTest : RouteCardLandscapeBarTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
class RouteCardShortLandscapeBarTest : RouteCardLandscapeBarTests()
