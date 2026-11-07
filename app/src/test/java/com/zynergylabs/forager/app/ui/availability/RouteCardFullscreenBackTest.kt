package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.SelectedStopMarker
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
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
 * Dispatch 2026-11-06-05, Amendment 3 (RECORD -2187), on the real compact Maps tab with the real [RoutePlottingViewModel] and the real
 * navigation start and end. Back after the card's Navigate in fullscreen undoes whichever came last (the owner: "Only leave full screen
 * if they entered full screen after tapping navigate. If they entered navigation mode after full screen mode, then leave navigate.";
 * restated and confirmed, "Yes, that's it"):
 *   card → Navigate → fullscreen: Back 1 leaves fullscreen, Back 2 ends navigation, Back 3 closes the card;
 *   fullscreen → card → Navigate: Back 1 ends navigation, Back 2 leaves fullscreen, Back 3 closes the card.
 * And the selected stop's accent marker hides with the waypoints layer ("Hide with the layer"). Fullscreen, Navigate, the route's pin
 * and the Map Layers switch are each reached by a real touch at screen coordinates; Back goes through the activity's dispatcher.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardFullscreenBackTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 200.dp, 300.dp, ROUTE_TRIP.location),
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
        viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
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

    private fun openRoute() {
        composeRule.onNodeWithTag(glyphTag(ROUTE_TRIP.id)).performTouchInput { click(center) }
        settle()
        assertTrue("the route opened", plotting.uiState.value.mode is RoutePlotMode.Showing)
    }

    private fun touchNavigate() {
        composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_TAG).performTouchInput { click(center) }
        settle()
        assertEquals("Navigate started to the first stop", CREEK.id, navigatingTo())
    }

    /** A real touch on the icon bar's Fullscreen (or Exit fullscreen) control, at its centre in root coordinates. */
    private fun touchControl(description: String) {
        val b = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
        val at = with(composeRule.density) { Offset(((b.left + b.right) / 2).toPx(), ((b.top + b.bottom) / 2).toPx()) }
        composeRule.onRoot().performTouchInput { click(at) }
        settle()
    }

    private fun enterFullscreen() {
        touchControl("Fullscreen")
        assertTrue("fullscreen is on", fullscreen())
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun fullscreen() = composeRule.onAllNodesWithContentDescription("Exit fullscreen").fetchSemanticsNodes().isNotEmpty()
    private fun navigatingTo() = viewModel.uiState.value.waypointNavigation?.waypointId
    private fun cardOpen() = plotting.uiState.value.mode is RoutePlotMode.Showing

    @Test
    fun `card, Navigate, fullscreen - Back leaves fullscreen, then ends navigation, then closes the card`() {
        setScreen()
        openRoute()
        touchNavigate()
        enterFullscreen()

        pressBack()
        assertTrue("Back 1 leaves fullscreen", !fullscreen())
        assertEquals("Back 1 leaves the navigation running", CREEK.id, navigatingTo())
        assertTrue("Back 1 leaves the card open", cardOpen())

        pressBack()
        assertNull("Back 2 ends the navigation", navigatingTo())
        assertTrue("Back 2 leaves the card open", cardOpen())

        pressBack()
        assertEquals("Back 3 closes the card", RoutePlotMode.Off, plotting.uiState.value.mode)
    }

    @Test
    fun `fullscreen, card, Navigate - Back ends navigation, then leaves fullscreen, then closes the card`() {
        setScreen()
        enterFullscreen()
        openRoute()
        touchNavigate()
        assertTrue("Navigate does not change fullscreen", fullscreen())

        pressBack()
        assertNull("Back 1 ends the navigation", navigatingTo())
        assertTrue("Back 1 stays in fullscreen", fullscreen())
        assertTrue("Back 1 leaves the card open", cardOpen())

        pressBack()
        assertTrue("Back 2 leaves fullscreen", !fullscreen())
        assertTrue("Back 2 leaves the card open", cardOpen())

        pressBack()
        assertEquals("Back 3 closes the card", RoutePlotMode.Off, plotting.uiState.value.mode)
    }

    @Test
    fun `fullscreen left and entered again after Navigate is the later of the two, so Back leaves it first`() {
        setScreen()
        enterFullscreen()
        openRoute()
        touchNavigate()
        touchControl("Exit fullscreen")
        enterFullscreen()

        pressBack()
        assertTrue("Back 1 leaves the fullscreen entered last", !fullscreen())
        assertEquals("Back 1 leaves the navigation running", CREEK.id, navigatingTo())
    }

    @Test
    fun `in fullscreen without Navigate the card's Back still goes first, as before`() {
        setScreen()
        enterFullscreen()
        openRoute()

        pressBack()
        assertEquals("Back closes the card (-1852, \"Yes, cancel first\")", RoutePlotMode.Off, plotting.uiState.value.mode)
        assertTrue("fullscreen stays", fullscreen())
    }

    @Test
    fun `the selected stop's accent marker hides when the Waypoints layer is switched off, and comes back with it`() {
        setScreen()
        openRoute()
        val accent = SelectedStopMarker(LatLng(CREEK.lat, CREEK.lng), trip = false)
        assertEquals("precondition: the accent marker at the first stop", accent, map.content?.plottedRoute?.selected)

        setWaypointsLayer(firstOpening = true)
        assertEquals("the switch turned the layer off", false, viewModel.uiState.value.mapLayers.stateOf(MapLayerIds.WAYPOINTS).visible)
        assertNull("with Waypoints off, no accent marker is drawn", map.content?.plottedRoute?.selected)
        assertTrue("the route still shows", cardOpen())

        setWaypointsLayer(firstOpening = false)
        assertEquals("the switch turned the layer on", true, viewModel.uiState.value.mapLayers.stateOf(MapLayerIds.WAYPOINTS).visible)
        assertEquals("with Waypoints on again, the accent marker is back", accent, map.content?.plottedRoute?.selected)
    }

    /** Tools, Map Layers (unfolded on its [firstOpening]; it stays open after) and its Waypoints switch, each by a real touch; then Tools closes. */
    private fun setWaypointsLayer(firstOpening: Boolean) {
        composeRule.openTools()
        val folded = composeRule.onAllNodesWithTag(mapLayerSwitchTag(MapLayerIds.WAYPOINTS)).fetchSemanticsNodes().isEmpty()
        if (firstOpening) assertTrue("Map Layers is folded on its first opening", folded)
        if (folded) composeRule.toggleMapLayersFold()
        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.WAYPOINTS)).performScrollTo().performTouchInput { click(center) }
        settle()
        composeRule.closeTools()
        settle()
    }
}
