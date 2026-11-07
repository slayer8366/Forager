package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.PlottedRoute
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.RouteStopLink
import com.zynergylabs.forager.app.testing.MadeUpPlace
import com.zynergylabs.forager.app.ui.map.SelectedStopMarker
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
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
 * Dispatch 2026-11-06-05, Amendment 3, last bullet (2026-11-07T16:01:33Z; Q-E of the completion report's part 4): a trip stop's accent
 * flag hides when the Planned Trips layer is switched off, as the waypoint pin hides with Waypoints (the owner: "Hide with its layer").
 * On the real compact Maps tab with the real [RoutePlottingViewModel]; the route's pin and the Map Layers switch are each reached by a
 * real touch. The accent is read as the data handed to the map (the map is a stub, as in [RouteCardFullscreenBackTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardTripAccentLayerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /** A planned trip 0.02 degrees north of CREEK, the saved route's first stop (so its accent is a flag). */
    private val saddle = PlannedTrip("trip-saddle", "Saddle loop", LatLng(MadeUpPlace.LAT + 0.02, MadeUpPlace.LNG), null)
    private val saddleStop = RouteStop(saddle.name, saddle.location, RouteStopLink.Trip(saddle.id))
    private val routeTrip = PlannedTrip(
        "route-saddle", "Saddle to Creek", saddle.location, null,
        PlottedRoute(listOf(saddleStop, CREEK.stop()), listOf(straight(saddle.location, CREEK.stop().position))),
    )

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.PLANNED_TRIPS, saddle.id, 60.dp, 300.dp, saddle.location),
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 360.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, routeTrip.id, 200.dp, 300.dp, routeTrip.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen() {
        viewModel = mapLayersViewModel(plannedTrips = listOf(saddle, routeTrip))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK),
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

    private fun tripsLayerOn() = viewModel.uiState.value.mapLayers.stateOf(MapLayerIds.PLANNED_TRIPS).visible

    @Test
    fun `a trip stop's accent flag hides when the Planned Trips layer is switched off, and comes back with it`() {
        setScreen()
        composeRule.onNodeWithTag(glyphTag(routeTrip.id)).performTouchInput { click(center) }
        settle()
        assertTrue("the route opened", plotting.uiState.value.mode is RoutePlotMode.Showing)
        val flag = SelectedStopMarker(saddle.location, trip = true)
        assertEquals("precondition: the accent flag at the first stop, a trip", flag, map.content?.plottedRoute?.selected)
        assertEquals("precondition: the Planned Trips layer is on", true, tripsLayerOn())

        setPlannedTripsLayer(firstOpening = true)
        assertEquals("the switch turned the layer off", false, tripsLayerOn())
        assertNull("with Planned Trips off, no accent flag is drawn", map.content?.plottedRoute?.selected)
        assertTrue("the route still shows", plotting.uiState.value.mode is RoutePlotMode.Showing)

        setPlannedTripsLayer(firstOpening = false)
        assertEquals("the switch turned the layer on", true, tripsLayerOn())
        assertEquals("with Planned Trips on again, the accent flag is back", flag, map.content?.plottedRoute?.selected)
    }

    /** Tools, Map Layers (unfolded on its [firstOpening]; it stays open after) and its Planned Trips switch, each by a real touch. */
    private fun setPlannedTripsLayer(firstOpening: Boolean) {
        composeRule.openTools()
        val folded = composeRule.onAllNodesWithTag(mapLayerSwitchTag(MapLayerIds.PLANNED_TRIPS)).fetchSemanticsNodes().isEmpty()
        if (firstOpening) assertTrue("Map Layers is folded on its first opening", folded)
        if (folded) composeRule.toggleMapLayersFold()
        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.PLANNED_TRIPS)).performScrollTo().performTouchInput { click(center) }
        settle()
        composeRule.closeTools()
        settle()
    }
}
