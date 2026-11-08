package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sorrel's review of PR #507, note 3.2: "navigated from the card" was set when the card's Navigate was tapped and never cleared when
 * that navigation ended, so a later navigation started elsewhere, with the same card still open, was taken for the card's in Back's
 * order. It is cleared when the navigation ends, by any route. Nothing on screen changes (Sorrel traced every case to the same result);
 * this reads the card's state itself.
 *
 * On the real compact Maps tab with the real [RoutePlottingViewModel] and the real navigation start and end, as
 * [RouteCardNavigatingButtonsTest]: the card's Navigate and the navigation card's ✕ by real touches, Back through the activity's
 * dispatcher, and the later navigation through the view model's own start, the one a bubble's and the Records row's Navigate call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardNavigatedFromCardEndsTest {

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

    private fun touch(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun navigatingTo() = viewModel.uiState.value.waypointNavigation?.waypointId
    private fun fromCard() = plotting.uiState.value.navigatedFromCard

    /** The route open, Navigate touched: the card's own navigation runs and is the card's. */
    private fun navigateFromCard() {
        setScreen()
        touch(glyphTag(ROUTE_TRIP.id))
        assertTrue("precondition: the route opened", plotting.uiState.value.mode is RoutePlotMode.Showing)
        touch(ROUTE_CARD_NAVIGATE_TAG)
        assertEquals("precondition: Navigate started to the first stop", CREEK.id, navigatingTo())
        assertTrue("precondition: the navigation Navigate started is the card's", fromCard())
    }

    /** Navigation to Ridge started the way a bubble's Navigate starts it, the card still open: not the card's. */
    private fun assertLaterNavigationNotTheCards(after: String) {
        composeRule.runOnIdle { viewModel.onNavigateToWaypoint(RIDGE.id) }
        settle()
        assertEquals("$after, then a navigation to Ridge from outside the card: it runs", RIDGE.id, navigatingTo())
        assertTrue("$after: the card is still open", plotting.uiState.value.mode is RoutePlotMode.Showing)
        assertFalse("$after, the navigation to Ridge, not started from the card, is not counted as the card's", fromCard())
    }

    @Test
    fun `Back ending the card's navigation clears it, so a later navigation from outside the card is not the card's`() {
        navigateFromCard()
        pressBack()
        assertNull("one Back ended the navigation", navigatingTo())
        assertFalse("the card's navigation ended by Back: none is the card's now", fromCard())
        assertLaterNavigationNotTheCards("after Back ended the card's navigation")
    }

    @Test
    fun `the navigation card's X ending the card's navigation clears it, so a later navigation from outside the card is not the card's`() {
        navigateFromCard()
        touch(NAVIGATION_HUD_EXIT_TAG)
        assertNull("the X ended the navigation", navigatingTo())
        assertFalse("the card's navigation ended by the X: none is the card's now", fromCard())
        assertLaterNavigationNotTheCards("after the X ended the card's navigation")
    }
}
