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
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-05, Amendment 1, A11 (RECORD -2148, the owner, to "While you're plotting a route, what should tapping a
 * trip's flag do?": "Add it as a stop (Recommended)", "The same as tapping a waypoint while plotting: the trip's spot joins
 * the route as the next stop"). A trip with no route only: one with a route still opens its route, as today (its own tests,
 * RoutePlottingScreenTest's "a route trip's pin tapped mid-plot asks first"). Not plotting, a trip's flag opens its bubble.
 *
 * Through the real screen, the real [AvailabilityViewModel] and a real [RoutePlottingViewModel] on a leg router that answers at
 * once; the flag is touched at five points across its own bounds ([ACROSS]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TripWhilePlottingTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /**
     * [SADDLE] moved off Creek's spot: SADDLE sits exactly where the route fixtures' Creek does, and a stop at the last stop's
     * position is refused by the plotting's own rule (RoutePlotDraft.withStop, the owner: "Ignore the second tap").
     */
    private val trip = SADDLE.copy(location = LatLng(45.346, -122.654))

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, trip.id, 60.dp, 420.dp, trip.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val saddleStop = RouteStop("Saddle loop", trip.location)

    private fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(trip))
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchFlag(fx: Float, fy: Float) {
        composeRule.onNodeWithTag(glyphTag(trip.id)).performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    private fun stops() = (plotting.uiState.value.mode as RoutePlotMode.Plotting).draft.stops

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun `while plotting, real touches across a trip's flag each add its spot as the next stop, under its name, and open no bubble`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on the trip's flag"
            composeRule.runOnIdle { plotting.onStartPlotting(CREEK.stop()) }
            composeRule.waitForIdle()

            touchFlag(fx, fy)

            assertEquals(where, listOf(CREEK.stop(), saddleStop), stops())
            assertEquals("$where: no bubble", 0, count(MAP_BUBBLE_TAG))
            composeRule.onNodeWithTag(ROUTE_CARD_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `the trip tapped twice in a row is one stop, and back and forth with a waypoint adds each, as a waypoint's taps do`() {
        setScreen()
        composeRule.runOnIdle { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()

        touchFlag(0.5f, 0.5f)
        touchFlag(0.5f, 0.5f)
        assertEquals("the same stop again is not added", listOf(CREEK.stop(), saddleStop), stops())

        composeRule.onNodeWithTag(glyphTag(CREEK.id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        touchFlag(0.5f, 0.5f)
        assertEquals(listOf(CREEK.stop(), saddleStop, CREEK.stop(), saddleStop), stops())
    }

    @Test
    fun `not plotting, real touches across a trip's flag each open its bubble, as before`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            composeRule.runOnIdle { plotting.onCancel() }
            composeRule.waitForIdle()

            touchFlag(fx, fy)

            composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
            assertEquals("a touch at ($fx, $fy): plotting is not started by it", RoutePlotMode.Off, plotting.uiState.value.mode)
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            assertEquals("Back closes the bubble before the next touch", 0, count(MAP_BUBBLE_TAG))
        }
    }
}
