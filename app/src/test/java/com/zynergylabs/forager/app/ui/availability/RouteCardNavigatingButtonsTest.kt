package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_REASON_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 5 (RECORD -2187): while navigating, the route card hides Navigate, Directions and the deleted-stop
 * reason line, and they come back when navigation ends. The owner: "A bug: navigate and directions are still visible on the route card
 * while navigating. Fix this by removing both buttons.", and of the reason line, "They should hide. If they don't now, they need to."
 *
 * On the real compact Maps tab with the real [RoutePlottingViewModel] and the real navigation start and end
 * (`AvailabilityViewModel.onNavigateToWaypoint` and `onStopWaypointNavigation`). Navigation is started by a real touch on the card's
 * Navigate and ended by Back through the activity's dispatcher, or by a real touch on the navigation card's ✕. Where Navigate is greyed
 * (the reason case), navigation to another waypoint is started through the view model's own start, the one the bubble's and the
 * Records row's Navigate call, since no control on this screen can start it while the card's own Navigate is greyed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardNavigatingButtonsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /** A route saved before schema 24: its stops keep only a name and a spot, so a moved waypoint leads nowhere. */
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

    private fun touch(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun navigation() = viewModel.uiState.value.waypointNavigation
    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertButtons(shown: Boolean, when_: String) {
        val expected = if (shown) 1 else 0
        assertEquals("$when_: Navigate ${if (shown) "shows" else "is hidden"}", expected, count(ROUTE_CARD_NAVIGATE_TAG))
        assertEquals("$when_: Directions ${if (shown) "shows" else "is hidden"}", expected, count(ROUTE_CARD_DIRECTIONS_TAG))
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `while navigating from the card, Navigate and Directions are hidden, and Back ending it brings them back`() {
        setScreen()
        open(ROUTE_TRIP)
        assertButtons(shown = true, when_ = "the card open, not navigating")

        touch(ROUTE_CARD_NAVIGATE_TAG)
        assertNotNull("Navigate started navigating", navigation())
        assertButtons(shown = false, when_ = "navigating")

        pressBack()
        assertNull("one Back ended the navigation", navigation())
        assertButtons(shown = true, when_ = "after Back ended the navigation")
    }

    @Test
    fun `the navigation card's X ending the navigation brings Navigate and Directions back`() {
        setScreen()
        open(ROUTE_TRIP)
        touch(ROUTE_CARD_NAVIGATE_TAG)
        assertButtons(shown = false, when_ = "navigating")

        touch(NAVIGATION_HUD_EXIT_TAG)
        assertNull("the X ended the navigation", navigation())
        assertButtons(shown = true, when_ = "after the X ended the navigation")
    }

    @Test
    fun `the deleted-stop reason hides while navigating, and comes back when navigation ends`() {
        setScreen(waypoints = listOf(CREEK.copy(lat = CREEK.lat + 0.001), RIDGE))
        open(oldTrip)
        assertEquals("not navigating: the reason shows", 1, count(ROUTE_CARD_NAVIGATE_REASON_TAG))

        composeRule.runOnIdle { viewModel.onNavigateToWaypoint(RIDGE.id) }
        settle()
        assertEquals("navigating to Ridge", RIDGE.id, navigation()?.waypointId)
        assertEquals("navigating: the reason is hidden", 0, count(ROUTE_CARD_NAVIGATE_REASON_TAG))
        assertButtons(shown = false, when_ = "navigating, the selected stop leading nowhere")

        touch(NAVIGATION_HUD_EXIT_TAG)
        assertNull("the X ended the navigation", navigation())
        assertEquals("after the X ended the navigation: the reason shows again", 1, count(ROUTE_CARD_NAVIGATE_REASON_TAG))
        assertButtons(shown = true, when_ = "after the X ended the navigation")
    }
}
