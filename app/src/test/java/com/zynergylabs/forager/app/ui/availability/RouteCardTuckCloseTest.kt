package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.ForagerThemeWithNoSignal
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.runOf
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CLOSE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/**
 * Dispatch 2026-11-06-05, Amendment 1 (RECORD -2187): the saved route card's ✕ (step 3), its tuck and tab (step 2) and its outline
 * (step 6), on the real compact Maps tab with the real [RoutePlottingViewModel]. Every touch is a real one at screen coordinates,
 * several across each target (CLAUDE.md: a finger is not a point); the outline is read from drawn pixels, in the ruled colours
 * written out as hex (Re-center's, #505: Bark #3B2E24 by day, #EDE3D0 at night), so an outline drawn any other colour, or none, fails.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardTuckCloseTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 60.dp, 420.dp, ROUTE_TRIP.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private var drawerReopens = 0

    private fun setScreen(dark: Boolean = false) {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            ForagerThemeWithNoSignal(darkTheme = dark, noSignal = MutableStateFlow(false), offlineRegions = emptyList()) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = AbsentForecastCellStore,
                    waypoints = listOf(CREEK, RIDGE),
                    routePlotting = RoutePlottingControls(state, plotting, onReturnToTripPlanner = { drawerReopens++ }),
                )
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun openSavedRoute() {
        composeRule.onNodeWithTag(glyphTag(ROUTE_TRIP.id)).performTouchInput { click(center) }
        settle()
        assertTrue("the route trip's pin opened its route", plotting.uiState.value.mode is RoutePlotMode.Showing)
    }

    private fun touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        settle()
    }

    private fun cardShows() = composeRule.onAllNodesWithTag(ROUTE_CARD_TAG).fetchSemanticsNodes().isNotEmpty()
    private fun tabShows() = composeRule.onAllNodesWithTag(ROUTE_CARD_TAB_TAG).fetchSemanticsNodes().isNotEmpty()
    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()
    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** Whether the card sits against the right edge (the icon bar on the left) or the left. */
    private fun cardOnRight(): Boolean = (rootBounds().right - bounds(ROUTE_CARD_TAG).right).value < 1f

    /** A real sideways swipe across the middle of the card, [towardEdge] or toward the map. */
    private fun swipeCard(towardEdge: Boolean) {
        val onRight = cardOnRight()
        val rightward = onRight == towardEdge
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            val from = if (rightward) width * 0.2f else width * 0.8f
            val to = if (rightward) width * 0.8f else width * 0.2f
            swipe(Offset(from, y), Offset(to, y), durationMillis = 300)
        }
        settle()
    }

    @Test
    fun `real touches across the X on a saved route each close the card and the route, and never reopen the Tools drawer`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            openSavedRoute()
            touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_CLOSE_TAG), fx, fy)

            assertEquals("a touch at ($fx, $fy) on the X", RoutePlotMode.Off, plotting.uiState.value.mode)
            assertNull("a touch at ($fx, $fy) on the X: no line on the map", map.content?.plottedRoute)
            assertFalse("a touch at ($fx, $fy) on the X: no card", cardShows())
        }
        assertEquals("the X never reopens the Tools drawer (that stays Back's)", 0, drawerReopens)
    }

    @Test
    fun `while plotting the card has no X, Cancel does it`() {
        setScreen()
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        settle()
        assertTrue(cardShows())
        assertEquals(0, composeRule.onAllNodesWithTag(ROUTE_CARD_CLOSE_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `real touches across Directions still reach Directions, beside the X, and close nothing`() {
        setScreen()
        openSavedRoute()
        ACROSS.forEachIndexed { i, (fx, fy) ->
            touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_DIRECTIONS_TAG), fx, fy)
            // No maps app in the test, so Directions says so in a toast (L4): that toast is Directions being reached.
            assertEquals("a touch at ($fx, $fy) on Directions reaches it", i + 1, ShadowToast.shownToastCount())
            assertTrue("a touch at ($fx, $fy) on Directions leaves the route open", plotting.uiState.value.mode is RoutePlotMode.Showing)
        }
    }

    @Test
    fun `a real sideways swipe on the card, either way, tucks it to its edge with the tab left there`() {
        setScreen()
        listOf(true, false).forEach { towardEdge ->
            openSavedRoute()
            val onRight = cardOnRight()
            swipeCard(towardEdge)

            val where = if (towardEdge) "a swipe toward the edge" else "a swipe toward the map"
            assertTrue("$where tucks the card", plotting.uiState.value.tucked)
            assertFalse("$where: the card is off the map", cardShows())
            assertTrue("$where: the route stays on the map", map.content?.plottedRoute != null)
            composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG).assertIsDisplayed()
            val tab = bounds(ROUTE_CARD_TAB_DRAWN_TAG)
            val root = rootBounds()
            assertEquals("$where: the tab is drawn 24 dp wide", 24f, (tab.right - tab.left).value, 0.5f)
            assertEquals("$where: the tab is drawn 56 dp tall", 56f, (tab.bottom - tab.top).value, 0.5f)
            if (onRight) {
                assertEquals("$where: the tab sits against the right edge, where the card was", root.right.value, tab.right.value, 0.5f)
            } else {
                assertEquals("$where: the tab sits against the left edge, where the card was", root.left.value, tab.left.value, 0.5f)
            }
            composeRule.runOnUiThread { plotting.onCloseSavedRoute() }
            settle()
            assertFalse("closed, the tab goes with the route", tabShows())
        }
    }

    @Test
    fun `real touches across the tab each bring the card back`() {
        setScreen()
        openSavedRoute()
        ACROSS.forEach { (fx, fy) ->
            swipeCard(towardEdge = true)
            assertTrue(plotting.uiState.value.tucked)
            touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG), fx, fy)

            assertFalse("a touch at ($fx, $fy) on the tab brings the card back", plotting.uiState.value.tucked)
            assertTrue("a touch at ($fx, $fy) on the tab: the card shows", cardShows())
            assertFalse("a touch at ($fx, $fy) on the tab: the tab goes", tabShows())
        }
    }

    @Test
    fun `a real drag on the tab away from the edge brings the card back`() {
        setScreen()
        openSavedRoute()
        swipeCard(towardEdge = true)
        val onRight = (rootBounds().right - bounds(ROUTE_CARD_TAB_TAG).right).value < 1f
        composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG).performTouchInput {
            val from = if (onRight) Offset(width * 0.9f, height * 0.5f) else Offset(width * 0.1f, height * 0.5f)
            val to = from + Offset(if (onRight) -80.dp.toPx() else 80.dp.toPx(), 0f)
            swipe(from, to, durationMillis = 300)
        }
        settle()
        assertFalse("the drag brings the card back", plotting.uiState.value.tucked)
        assertTrue(cardShows())
    }

    @Test
    fun `while plotting the card tucks too, and a waypoint tapped then is still added`() {
        setScreen()
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        settle()
        swipeCard(towardEdge = true)
        assertTrue(plotting.uiState.value.tucked)
        composeRule.onNodeWithTag(glyphTag(RIDGE.id)).performTouchInput { click(center) }
        settle()
        assertEquals(listOf(CREEK.stop(), RIDGE.stop()), (plotting.uiState.value.mode as RoutePlotMode.Plotting).draft.stops)
    }

    @Test
    fun `real long-presses around the tab and where the tucked card was reach the map`() {
        setScreen()
        openSavedRoute()
        val card = bounds(ROUTE_CARD_TAG)
        swipeCard(towardEdge = true)
        val tab = bounds(ROUTE_CARD_TAB_TAG)
        val onRight = (rootBounds().right - tab.right).value < 1f
        val mapSide = if (onRight) tab.left - 12.dp else tab.right + 12.dp
        val points = listOf(
            "just above the tab" to ((tab.left + tab.right) / 2 to tab.top - 12.dp),
            "beside the tab, toward the map" to (mapSide to (tab.top + tab.bottom) / 2),
            "where the card's top was" to ((card.left + card.right) / 2 to card.top + 12.dp),
            "where the card's middle was" to ((card.left + card.right) / 2 to (card.top + card.bottom) / 2),
            "where the card's far corner was" to ((if (onRight) card.left + 12.dp else card.right - 12.dp) to card.top + 12.dp),
        )
        points.forEachIndexed { i, (where, at) ->
            val offset = with(composeRule.density) { Offset(at.first.toPx(), at.second.toPx()) }
            composeRule.onRoot().performTouchInput { longClick(offset) }
            settle()
            assertEquals("a long-press $where reaches the map", i + 1, map.longPresses)
        }
        assertTrue("the long-presses left the card tucked", plotting.uiState.value.tucked)
    }

    private fun assertCardOutline(dark: Boolean) {
        val outline = if (dark) Color(0xFFEDE3D0) else Color(0xFF3B2E24)
        val name = if (dark) "night" else "day"
        setScreen(dark)
        openSavedRoute()
        val onRight = cardOnRight()
        val pixels = composeRule.onNodeWithTag(ROUTE_CARD_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val n = with(composeRule.density) { 6.dp.roundToPx() }
        val row = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val column = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        val mapSide = if (onRight) row.take(n) else row.takeLast(n).reversed()
        val edgeSide = if (onRight) row.takeLast(n).reversed() else row.take(n)
        val faced = listOf("top" to column.take(n), "bottom" to column.takeLast(n).reversed(), "side facing the map" to mapSide)
        for ((side, edge) in faced) {
            val run = runOf(edge, outline)
            assertNotNull("$name, the card's $side: a ${outline.hex()} outline is drawn; the edge reads ${edge.map { it.hex() }}", run)
            assertTrue("$name, the card's $side: the outline is at the card's own edge, ${run!!.first} px in", run.first <= 1)
            println("MEASURED $name, the card's $side: ${outline.hex()} for ${run.last - run.first + 1} px from ${run.first} px in")
        }
        assertNull("$name, the side flush to the screen edge carries no outline; it reads ${edgeSide.map { it.hex() }}", runOf(edgeSide, outline))
    }

    @Test
    fun `by day, the saved route card has a 1 dp Bark outline on the three sides facing the map`() = assertCardOutline(dark = false)

    @Test
    fun `at night, the saved route card has a 1 dp pale outline on the three sides facing the map`() = assertCardOutline(dark = true)
}
