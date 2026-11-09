package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.NAVIGATE_UNAVAILABLE_REASON
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_REASON_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardStopNameTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The S22 check of build 5054 (2026-11-09), step "delete a route's waypoint": a saved route whose first stop is linked to a waypoint
 * (schema 24), the waypoint deleted while the route's card is open and a navigation is running, then the navigation ended. Dispatch
 * 2026-11-06-05, Amendment 2 (RECORD -2187): the stop stays, Navigate is greyed with "This stop's waypoint was moved or deleted";
 * Amendment 5: while navigating the buttons and the reason hide ("They should hide."), and come back when navigation ends.
 *
 * On the S22 the navigation started at 12:36 was still running when the card was reopened after the delete (the card's dumps carry
 * "Stop navigating" and no Navigate or Directions, before the delete as after it), so no reason could show. This drives that order
 * on the real compact Maps tab: the delete is the map's waypoint list losing the waypoint, which is what the Journal swipe does to
 * `visibleWaypoints` (MainActivity.kt passes it as the map's waypoints); the navigation is ended by a real touch on the card's X.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardStopDeletedWhileNavigatingTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 200.dp, 300.dp, ROUTE_TRIP.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> error("nothing is saved here") },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val waypoints = mutableStateOf<List<Waypoint>>(listOf(CREEK, RIDGE))
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen() {
        viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            val shown by waypoints
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = shown,
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

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `a linked first stop deleted while navigating shows no reason until navigation ends, then Navigate greys with the reason`() {
        setScreen()
        composeRule.onNodeWithTag(glyphTag(ROUTE_TRIP.id)).performTouchInput { click(center) }
        settle()
        assertEquals("the route opened", ROUTE_TRIP.id, (plotting.uiState.value.mode as? RoutePlotMode.Showing)?.trip?.id)
        assertEquals("the first stop is selected by default", 0, plotting.uiState.value.selectedStop)
        // Expanded, as on the S22 (its dumps list the stops); collapsed, the card shows no stop rows.
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(center) }
        settle()
        assertEquals("the card expanded", true, plotting.uiState.value.expanded)
        assertEquals("before the delete, not navigating: Navigate shows", 1, count(ROUTE_CARD_NAVIGATE_TAG))
        assertEquals("before the delete, not navigating: no reason", 0, count(ROUTE_CARD_NAVIGATE_REASON_TAG))

        // As on the S22: a navigation to another waypoint is running (started from elsewhere), then the first stop's waypoint goes.
        composeRule.runOnIdle { viewModel.onNavigateToWaypoint(RIDGE.id) }
        settle()
        assertEquals("navigating to Ridge", RIDGE.id, viewModel.uiState.value.waypointNavigation?.waypointId)
        composeRule.runOnIdle { waypoints.value = listOf(RIDGE) }
        settle()
        composeRule.onNodeWithTag(routeCardStopNameTag(0), useUnmergedTree = true).assertTextEquals(CREEK.name)
        assertEquals("deleted while navigating: Navigate is hidden", 0, count(ROUTE_CARD_NAVIGATE_TAG))
        assertEquals("deleted while navigating: the reason is hidden", 0, count(ROUTE_CARD_NAVIGATE_REASON_TAG))

        composeRule.onNodeWithTag(NAVIGATION_HUD_EXIT_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertNull("the X ended the navigation", viewModel.uiState.value.waypointNavigation)
        composeRule.onNodeWithTag(routeCardStopNameTag(0), useUnmergedTree = true).assertTextEquals(CREEK.name)
        composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_REASON_TAG, useUnmergedTree = true).assertTextEquals(NAVIGATE_UNAVAILABLE_REASON)
    }
}
