package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-05, Amendment 5, its added item (RECORD -2187; the owner, of the tucked card's tab: "Same as the card"): the tab
 * draws the map chrome's outline as the card does since Amendment 5, 1 dp, Bark #3B2E24 at 40% by day and white at 40% at night
 * (`RouteCardOutlineReading.kt`, the colours written out as the ruling gives them), all the way round, the side flush to its screen edge
 * included. Before this the tab drew step 6's solid 1 dp `onSurface` line on the three sides facing the map and none on its edge side.
 *
 * Read from drawn pixels on the real Maps tab ([RouteCardBarRig]), the card tucked by a real sideways swipe, in each placement the tab
 * has: portrait (a saved route, against either edge, and while plotting) and landscape ([RouteCardTabOutlineLandscapeTest]).
 *
 * Where each side is read: the tab is 24 dp wide and 56 dp tall, square on its edge side and round (16 dp) toward the map, with a 4 by
 * 24 dp grip at its centre. The sides are read along the middle row; the top and bottom down the column 4 dp in from the edge side,
 * where they are straight (the round corners take the 16 dp nearest the map). The reference pixels 4 and 5 dp in miss the grip.
 */
abstract class RouteCardTabOutlineTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    protected val rig = RouteCardBarRig(composeRule)

    /** A real sideways swipe across the card toward its edge, so it tucks and leaves the tab. */
    protected fun tuck() {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val onRight = (rig.screen().right - card.right).value < 1f
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            val from = if (onRight) width * 0.2f else width * 0.8f
            val to = if (onRight) width * 0.8f else width * 0.2f
            swipe(Offset(from, y), Offset(to, y), durationMillis = 300)
        }
        rig.settle()
        assertTrue("precondition: the swipe tucked the card", rig.plotting.uiState.value.tucked)
        assertEquals("precondition: the tab shows", 1, rig.count(ROUTE_CARD_TAB_DRAWN_TAG))
    }

    /** The tab's four sides each carry the ruled line, [dark] or by day, in [where]. */
    protected fun assertTabOutline(where: String, dark: Boolean) {
        val name = if (dark) "night" else "day"
        val tab = rig.bounds(ROUTE_CARD_TAB_DRAWN_TAG)
        val onRight = tab.left.value > rig.screen().right.value / 2
        val pixels = composeRule.onNodeWithTag(ROUTE_CARD_TAB_DRAWN_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val px = composeRule.density.density
        val n = with(composeRule.density) { 8.dp.roundToPx() }
        val fourIn = with(composeRule.density) { 4.dp.roundToPx() }
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val columnX = if (onRight) pixels.width - 1 - fourIn else fourIn
        val column = (0 until pixels.height).map { pixels[columnX, it] }
        val mapSide = if (onRight) row.take(n) else row.takeLast(n).reversed()
        val edgeSide = if (onRight) row.takeLast(n).reversed() else row.take(n)
        val sides = listOf(
            "top" to column.take(n),
            "bottom" to column.takeLast(n).reversed(),
            "side facing the map" to mapSide,
            "side on its screen edge" to edgeSide,
        )
        println("MEASURED $name, $where: the tab at ${tab.left.value}..${tab.right.value} x ${tab.top.value}..${tab.bottom.value}, on the ${if (onRight) "right" else "left"}")
        val off = sides.mapNotNull { (side, edge) ->
            println("MEASURED $name, $where, the tab's $side: ${edge.take(6).map { it.hex() }}")
            whyNotRuledLine(edge, dark, px)?.let { "$side: $it" }
        }
        assertTrue("$name, $where: the tucked card's tab carries the ruled line on every side, its screen edge's too; off: $off", off.isEmpty())
    }
}

/** Portrait: a saved route's tab against each edge (the icon bar where it starts, then moved across), and a plotting card's. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardTabOutlineTest : RouteCardTabOutlineTests() {

    /** [barMoved]: the icon bar dragged across to the other side first, so the card and its tab sit against the other edge. */
    private fun savedRouteTab(dark: Boolean, barMoved: Boolean) {
        val trip = rig.savedTrip("Creek loop", 2)
        rig.setScreen(trips = listOf(trip), dark = dark)
        val barBefore = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val barStartsLeft = barBefore.right < rig.screen().right / 2
        if (barMoved) rig.dragBarAcross(rightward = barStartsLeft)
        val barNowLeft = rig.bounds(MAP_ICON_CLUSTER_TAG).right < rig.screen().right / 2
        assertEquals("precondition: the bar moved across only when asked", barMoved, barNowLeft != barStartsLeft)
        rig.open(trip)
        tuck()
        assertTabOutline(if (barMoved) "a saved route's tab, the icon bar moved across" else "a saved route's tab, the icon bar where it starts", dark)
    }

    private fun plottingTab(dark: Boolean) {
        rig.setScreen(dark = dark)
        rig.plotTo(2)
        tuck()
        assertTabOutline("while plotting", dark)
    }

    @Test
    fun `by day, a saved route's tab, the icon bar where it starts, has the map chrome's outline all the way round`() = savedRouteTab(dark = false, barMoved = false)

    @Test
    fun `at night, a saved route's tab, the icon bar where it starts, has the map chrome's outline all the way round`() = savedRouteTab(dark = true, barMoved = false)

    @Test
    fun `by day, a saved route's tab, the icon bar moved across, has the map chrome's outline all the way round`() = savedRouteTab(dark = false, barMoved = true)

    @Test
    fun `at night, a saved route's tab, the icon bar moved across, has the map chrome's outline all the way round`() = savedRouteTab(dark = true, barMoved = true)

    @Test
    fun `by day, the tab while plotting has the map chrome's outline all the way round`() = plottingTab(dark = false)

    @Test
    fun `at night, the tab while plotting has the map chrome's outline all the way round`() = plottingTab(dark = true)
}

/** Landscape, recording, a route plotted: the card rises from the bottom edge, and its tab sits at that corner. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardTabOutlineLandscapeTest : RouteCardTabOutlineTests() {

    private fun landscapeTab(dark: Boolean) {
        rig.setScreen(dark = dark)
        rig.plotTo(2)
        tuck()
        assertTabOutline("in landscape", dark)
    }

    @Test
    fun `by day in landscape, the tab has the map chrome's outline all the way round`() = landscapeTab(dark = false)

    @Test
    fun `at night in landscape, the tab has the map chrome's outline all the way round`() = landscapeTab(dark = true)
}
