package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.SelectedStopMarker
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.NAVIGATE_UNAVAILABLE_REASON
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_REASON_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_STOPS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardStopLegTag
import com.zynergylabs.forager.app.ui.route.routeCardStopNameTag
import com.zynergylabs.forager.app.ui.route.routeCardStopTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 2, items 3 and 4 (RECORD -2187), on the real compact Maps tab with the real
 * [RoutePlottingViewModel] and the real navigation start (`AvailabilityViewModel.onNavigateToWaypoint`): Navigate, left of Directions,
 * goes to the selected stop (the first until one is picked, the owner's "Go to the first waypoint"); a stop is picked by its marker
 * ("Bubble opens and it's selected") or by its row in the open card ("Waypoints with leg distances"); one Back ends the navigation with
 * the card still open (Amendment 1, step 5); a stop that leads nowhere greys Navigate with "This stop's waypoint was moved or deleted".
 * Every touch is a real one at screen coordinates, several across each target (CLAUDE.md: a finger is not a point); Back goes through
 * the activity's dispatcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardNavigateTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /** A route saved before schema 24: its stops keep only a name and a spot. */
    private val oldTrip = PlannedTrip(
        "route-old", "Old loop", CREEK.stop().position, null,
        PlottedRoute(listOf(RouteStop(CREEK.name, CREEK.stop().position), RouteStop(RIDGE.name, RIDGE.stop().position)), SAVED_ROUTE.legs),
    )

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 200.dp, 300.dp, ROUTE_TRIP.location),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, oldTrip.id, 200.dp, 360.dp, oldTrip.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(waypoints: List<Waypoint> = listOf(CREEK, RIDGE)) {
        viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP, oldTrip))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = waypoints,
                routePlotting = RoutePlottingControls(state, plotting),
                navigation = AvailabilityScreenNavigation(
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onNavigateToTrip = viewModel::onNavigateToTrip,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun open(trip: PlannedTrip) {
        composeRule.onNodeWithTag(glyphTag(trip.id)).performTouchInput { click(center) }
        settle()
        assertEquals("${trip.name} opened", trip.id, (plotting.uiState.value.mode as? RoutePlotMode.Showing)?.trip?.id)
    }

    private fun expand() {
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(center) }
        settle()
        composeRule.onNodeWithTag(ROUTE_CARD_STOPS_TAG).assertIsDisplayed()
    }

    private fun touch(tag: String, fx: Float, fy: Float) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(androidx.compose.ui.geometry.Offset(width * fx, height * fy)) }
        settle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun stopNavigating() {
        composeRule.runOnIdle { viewModel.onStopWaypointNavigation() }
        settle()
    }

    private fun navigation() = viewModel.uiState.value.waypointNavigation
    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun label(tag: String): String = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun `Navigate sits left of Directions on a saved route's card`() {
        setScreen()
        open(ROUTE_TRIP)

        assertEquals("Navigate", label(ROUTE_CARD_NAVIGATE_TAG))
        val navigate = composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_TAG).getUnclippedBoundsInRoot()
        val directions = composeRule.onNodeWithTag(ROUTE_CARD_DIRECTIONS_TAG).getUnclippedBoundsInRoot()
        assertTrue("Navigate left of Directions: ${navigate.right} vs ${directions.left}", navigate.right <= directions.left)
        assertEquals("one row", navigate.top.value, directions.top.value, 0.5f)
    }

    @Test
    fun `real touches across Navigate each start navigating to the first stop's waypoint, through its link, and the card stays open`() {
        setScreen()
        open(ROUTE_TRIP)
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Navigate"
            stopNavigating()

            touch(ROUTE_CARD_NAVIGATE_TAG, fx, fy)

            assertEquals(where, WaypointNavigation(CREEK.id, resumesReturn = false, kind = NavigationTargetKind.WAYPOINT), navigation())
            assertTrue("$where: the card stays open", plotting.uiState.value.mode is RoutePlotMode.Showing)
        }
    }

    @Test
    fun `one Back ends the navigation the card started with the card still open, and the next Back closes the card`() {
        setScreen()
        open(ROUTE_TRIP)
        touch(ROUTE_CARD_NAVIGATE_TAG, 0.5f, 0.5f)
        assertEquals(CREEK.id, navigation()?.id)

        pressBack()

        assertNull("one Back ends the navigation", navigation())
        assertTrue("the card is still open", plotting.uiState.value.mode is RoutePlotMode.Showing)
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).assertIsDisplayed()

        pressBack()

        assertEquals("the next Back closes the card, as before", RoutePlotMode.Off, plotting.uiState.value.mode)
    }

    @Test
    fun `a route saved before the link finds its waypoint by spot and name, and goes there`() {
        setScreen()
        open(oldTrip)

        touch(ROUTE_CARD_NAVIGATE_TAG, 0.5f, 0.5f)

        assertEquals(WaypointNavigation(CREEK.id, resumesReturn = false, kind = NavigationTargetKind.WAYPOINT), navigation())
        assertEquals("no reason shows", 0, count(ROUTE_CARD_NAVIGATE_REASON_TAG))
    }

    @Test
    fun `when the stop's waypoint was moved, Navigate is greyed with the owner's reason, and real touches on it start nothing`() {
        setScreen(waypoints = listOf(CREEK.copy(lat = CREEK.lat + 0.001), RIDGE))
        open(oldTrip)

        composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_REASON_TAG, useUnmergedTree = true).assertTextEquals("This stop's waypoint was moved or deleted")
        assertEquals(NAVIGATE_UNAVAILABLE_REASON, "This stop's waypoint was moved or deleted")
        ACROSS.forEach { (fx, fy) ->
            touch(ROUTE_CARD_NAVIGATE_TAG, fx, fy)
            assertNull("a touch at ($fx, $fy) on the greyed Navigate", navigation())
        }
    }

    @Test
    fun `the open card lists each waypoint as a row, with the distance of the leg leading to it under it`() {
        setScreen()
        open(ROUTE_TRIP)
        expand()

        composeRule.onNodeWithTag(routeCardStopNameTag(0), useUnmergedTree = true).assertTextEquals("Creek")
        composeRule.onNodeWithTag(routeCardStopNameTag(1), useUnmergedTree = true).assertTextEquals("Ridge")
        assertEquals("the first stop has no leg leading to it", 0, count(routeCardStopLegTag(0)))
        composeRule.onNodeWithTag(routeCardStopLegTag(1), useUnmergedTree = true).assertTextEquals(formatDistanceMeters(SAVED_ROUTE.legs[0].meters, viewModel.uiState.value.distanceUnit))
        val name = composeRule.onNodeWithTag(routeCardStopNameTag(1), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val leg = composeRule.onNodeWithTag(routeCardStopLegTag(1), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the distance under the name: ${leg.top} vs ${name.bottom}", leg.top >= name.bottom)
    }

    @Test
    fun `real touches across a waypoint's row each select it, and Navigate then goes there`() {
        setScreen()
        open(ROUTE_TRIP)
        expand()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Ridge's row"
            composeRule.runOnIdle { plotting.onSelectStop(0) }
            stopNavigating()

            touch(routeCardStopTag(1), fx, fy)
            assertEquals(where, 1, plotting.uiState.value.selectedStop)

            touch(ROUTE_CARD_NAVIGATE_TAG, 0.5f, 0.5f)
            assertEquals("$where: Navigate", RIDGE.id, navigation()?.id)
        }
    }

    @Test
    fun `real touches across a stop's marker each open its bubble as before and select it`() {
        setScreen()
        open(ROUTE_TRIP)
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Ridge's marker"
            composeRule.runOnIdle { plotting.onSelectStop(0) }
            pressBackIfBubble()

            touch(glyphTag(RIDGE.id), fx, fy)

            composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
            assertEquals(where, 1, plotting.uiState.value.selectedStop)
        }
    }

    @Test
    fun `the selected stop's marker is drawn in the accent, at the first stop until another is picked`() {
        setScreen()
        open(ROUTE_TRIP)

        assertEquals("the first stop by default", SelectedStopMarker(LatLng(CREEK.lat, CREEK.lng), trip = false), map.content?.plottedRoute?.selected)

        expand()
        touch(routeCardStopTag(1), 0.5f, 0.5f)
        settle()

        assertEquals("the picked stop", SelectedStopMarker(LatLng(RIDGE.lat, RIDGE.lng), trip = false), map.content?.plottedRoute?.selected)
    }

    private fun pressBackIfBubble() {
        if (count(MAP_BUBBLE_TAG) > 0) pressBack()
    }
}
