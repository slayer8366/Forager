package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_KEEP_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAME_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
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

/**
 * A route trip's row in the Tools drawer's Trip Planner (dispatch 2026-10-30-08, Amendment 3; the first stop, (h) 7), through
 * the real screen, the real [AvailabilityViewModel] and a real [RoutePlottingViewModel]. Every touch is a real one sampled
 * across the control's own bounds; Back goes through the real dispatcher. The way back is `rememberRoutePlottingLinks`
 * (RoutePlottingTrips.kt): the tab the drawer was opened over comes back and the drawer reopens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingTripPlannerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(emptyList())
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(trips: List<PlannedTrip> = listOf(ROUTE_TRIP)) {
        viewModel = mapLayersViewModel(plannedTrips = trips)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun mode() = plotting.uiState.value.mode

    private fun drawerShown(): Boolean = runCatching { composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).assertIsDisplayed() }.isSuccess

    /** Tools, then the Trip Planner fold, each by a real touch. The fold is held above the drawer, so it stays open after. */
    private fun openTripPlanner() {
        composeRule.openTools()
        assertTrue("the Tools drawer opened", drawerShown())
        composeRule.onNodeWithTag(collapsibleSectionHeaderTag("Trip Planner")).performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun row(trip: PlannedTrip = ROUTE_TRIP) = composeRule.onNodeWithTag(plannedRouteRowTag(trip.id)).performScrollTo()

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    /** What a touch on the row's body does: the drawer closes, the tab is Maps, the route opens from the Trip Planner. */
    private fun assertRouteOpened(where: String) {
        assertFalse("$where: the drawer closed", drawerShown())
        composeRule.onNodeWithText("Maps").assertIsSelected()
        val showing = mode() as? RoutePlotMode.Showing
        assertNotNull("$where: the route is showing, was ${mode()}", showing)
        assertEquals("$where: the route opened", ROUTE_TRIP, showing!!.trip)
        assertTrue("$where: opened from the Trip Planner", showing.fromTripPlanner)
        val request = map.renderMode?.cameraRequest
        assertEquals("$where: the fit for this open", "saved-route-${ROUTE_TRIP.id}-${showing.serial}", request?.id)
        assertEquals("$where: the card under the route's name", ROUTE_TRIP.name, text(ROUTE_CARD_NAME_TAG))
    }

    @Test
    fun `from the Maps tab, real touches across a route row's body each open the route, and Back reopens the drawer on the Trip Planner`() {
        setScreen()
        openTripPlanner()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the row's body"
            touchAcross(row(), fx, fy)
            assertRouteOpened(where)
            assertEquals("$where: the line is handed to the map", 2, map.content?.plottedRoute?.stopDots?.size)

            back()

            assertEquals("$where, then Back: the route closed", RoutePlotMode.Off, mode())
            assertEquals("$where, then Back: no card", 0, composeRule.onAllNodesWithTag(ROUTE_CARD_TAG).fetchSemanticsNodes().size)
            assertNull("$where, then Back: no line", map.content?.plottedRoute)
            assertTrue("$where, then Back: the drawer reopened", drawerShown())
            composeRule.onNodeWithText("Maps").assertIsSelected()
            // The Trip Planner fold is still open: the row is there to touch again.
            row().assertIsDisplayed()
        }
    }

    @Test
    fun `from the List tab, a route row opens the route on Maps, and Back brings back the List tab with the drawer open`() {
        setScreen()
        ACROSS.forEachIndexed { i, (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the row's body"
            if (i == 0) {
                composeRule.onNodeWithText("List").performTouchInput { click(center) }
                composeRule.waitForIdle()
                composeRule.onNodeWithText("List").assertIsSelected()
                openTripPlanner()
            }
            touchAcross(row(), fx, fy)
            assertRouteOpened(where)

            back()

            assertEquals("$where, then Back: the route closed", RoutePlotMode.Off, mode())
            assertTrue("$where, then Back: the drawer reopened", drawerShown())
            // The tab it was opened over came back (the map is not drawn there, so its last content is not read here).
            composeRule.onNodeWithText("List").assertIsSelected()
            row().assertIsDisplayed()
        }
    }

    @Test
    fun `real touches across a route row's Delete only delete it, opening nothing`() {
        val trips = ACROSS.indices.map { i -> ROUTE_TRIP.copy(id = "route-del-$i", name = "Route $i") }
        setScreen(trips)
        openTripPlanner()
        ACROSS.forEachIndexed { i, (fx, fy) ->
            val trip = trips[i]
            val where = "a touch at ($fx, $fy) on ${trip.id}'s Delete"
            touchAcross(composeRule.onNodeWithTag(plannedRouteDeleteTag(trip.id)).performScrollTo(), fx, fy)

            val left = viewModel.uiState.value.plannedTrips.map { it.id }
            assertFalse("$where: the trip was deleted, left $left", trip.id in left)
            assertEquals("$where: the others are kept", trips.drop(i + 1).map { it.id }.toSet(), left.toSet())
            assertEquals("$where: no route opened", RoutePlotMode.Off, mode())
            assertEquals("$where: no card", 0, composeRule.onAllNodesWithTag(ROUTE_CARD_TAG).fetchSemanticsNodes().size)
            assertTrue("$where: the drawer stays open", drawerShown())
        }
    }

    /**
     * Amendment 5, N-11 (Sorrel's review of #449): a route trip deleted from the Trip Planner while its route shows takes its
     * card and its line with it. The drawer is opened again over the route by a real touch, and Delete takes a real touch.
     */
    @Test
    fun `deleting a route trip while its route shows closes its card and its line`() {
        val trips = ACROSS.indices.map { i -> ROUTE_TRIP.copy(id = "route-shown-$i", name = "Route $i") }
        setScreen(trips)
        openTripPlanner()
        ACROSS.forEachIndexed { i, (fx, fy) ->
            val trip = trips[i]
            val where = "a touch at ($fx, $fy) on ${trip.id}'s Delete while it shows"
            if (i > 0) composeRule.openTools()
            touchAcross(row(trip), 0.5f, 0.5f)
            assertEquals("$where: the route opened first", trip.id, (mode() as? RoutePlotMode.Showing)?.trip?.id)
            composeRule.openTools()
            assertTrue("$where: the drawer is open over the route", drawerShown())

            touchAcross(composeRule.onNodeWithTag(plannedRouteDeleteTag(trip.id)).performScrollTo(), fx, fy)

            assertFalse("$where: the trip was deleted", trip.id in viewModel.uiState.value.plannedTrips.map { it.id })
            assertEquals("$where: the route closed with its trip", RoutePlotMode.Off, mode())
            assertEquals("$where: no card", 0, composeRule.onAllNodesWithTag(ROUTE_CARD_TAG).fetchSemanticsNodes().size)
            assertNull("$where: no line", map.content?.plottedRoute)
            back()
        }
    }

    /**
     * Amendment 5, N-2 (Sorrel's review of #449): a route row tapped mid-plot asks "Discard this route?"; Keep plotting puts the
     * user back where they were, the tab the drawer was over and the drawer open on the Trip Planner, with the plot kept.
     */
    @Test
    fun `Keep plotting after a route row tapped mid-plot brings back the tab and the drawer, with the plot kept`() {
        setScreen()
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        composeRule.waitForIdle()
        val plot = mode()
        composeRule.onNodeWithText("List").performTouchInput { click(center) }
        composeRule.waitForIdle()
        openTripPlanner()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Keep plotting"
            touchAcross(row(), 0.5f, 0.5f)
            composeRule.onNodeWithTag(DISCARD_ROUTE_DIALOG_TAG).assertIsDisplayed()

            touchAcross(composeRule.onNodeWithTag(DISCARD_ROUTE_KEEP_TAG), fx, fy)

            assertEquals("$where: the plot is kept", plot, mode())
            assertEquals("$where: the prompt closed", 0, composeRule.onAllNodesWithTag(DISCARD_ROUTE_DIALOG_TAG).fetchSemanticsNodes().size)
            assertTrue("$where: the drawer is open again", drawerShown())
            composeRule.onNodeWithText("List").assertIsSelected()
            row().assertIsDisplayed()
        }
    }
}
