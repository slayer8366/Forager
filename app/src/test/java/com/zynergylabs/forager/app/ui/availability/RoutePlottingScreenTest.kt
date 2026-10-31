package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.RoutePlotDraft
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_LEGS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAME_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CONFIRM_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DIALOG_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Route plotting on the compact Maps tab (dispatch 2026-10-30-08, step 3), through the real screen, the real
 * [AvailabilityViewModel] and a real [RoutePlottingViewModel] on a leg router that answers at once with a straight leg. Every
 * touch is a real one at screen coordinates, sampled across the control's own bounds (the first stop, (h)).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingScreenTest {

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
    private val saves = mutableListOf<Pair<String, LocalDate?>>()
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> saves += name to date; Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
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

    private fun RouteRule.touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        waitForIdle()
    }

    private fun RouteRule.tapGlyph(id: String) {
        onNodeWithTag(glyphTag(id)).performTouchInput { click(center) }
        waitForIdle()
    }

    private fun RouteRule.back() {
        runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        waitForIdle()
    }

    private fun mode() = plotting.uiState.value.mode

    private fun startAtCreekAndAddRidge() {
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()
        composeRule.tapGlyph(RIDGE.id)
    }

    @Test
    fun `real touches across Plot route in a waypoint bubble each start plotting from it, and the bubble closes`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            composeRule.runOnUiThread { plotting.onCancel() }
            composeRule.tapGlyph(CREEK.id)
            composeRule.touchAcross(composeRule.onNodeWithTag(MAP_BUBBLE_PLOT_ROUTE_TAG), fx, fy)

            assertEquals("a touch at ($fx, $fy)", RoutePlotMode.Plotting(RoutePlotDraft(listOf(CREEK.stop()))), mode())
            assertEquals(0, composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().size)
            composeRule.onNodeWithTag(ROUTE_CARD_COUNT_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun `while plotting, real touches across a waypoint glyph add it once, open no bubble, and A to B to A adds three stops`() {
        setScreen()
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()

        ACROSS.forEach { (fx, fy) -> composeRule.touchAcross(composeRule.onNodeWithTag(glyphTag(RIDGE.id)), fx, fy) }

        assertEquals(listOf(CREEK.stop(), RIDGE.stop()), (mode() as RoutePlotMode.Plotting).draft.stops)
        assertEquals(1, (mode() as RoutePlotMode.Plotting).draft.legs.size)
        assertEquals(0, composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().size)
        assertEquals("the route's line is handed to the map", 1, map.content?.plottedRoute?.legs?.size)

        composeRule.tapGlyph(CREEK.id)
        assertEquals(listOf(CREEK.stop(), RIDGE.stop(), CREEK.stop()), (mode() as RoutePlotMode.Plotting).draft.stops)
    }

    @Test
    fun `real touches across Undo remove the last stop each time`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            startAtCreekAndAddRidge()
            composeRule.touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_UNDO_TAG), fx, fy)
            assertEquals("a touch at ($fx, $fy)", listOf(CREEK.stop()), (mode() as RoutePlotMode.Plotting).draft.stops)
        }
    }

    @Test
    fun `real touches across Cancel drop the route and the card`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            startAtCreekAndAddRidge()
            composeRule.touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_CANCEL_TAG), fx, fy)
            assertEquals("a touch at ($fx, $fy)", RoutePlotMode.Off, mode())
            assertEquals(0, composeRule.onAllNodesWithTag(ROUTE_CARD_TAG).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `real touches across Save open the save dialog, and its Save saves under the default name`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            startAtCreekAndAddRidge()
            composeRule.touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_SAVE_TAG), fx, fy)
            composeRule.onNodeWithTag(SAVE_ROUTE_DIALOG_TAG).assertIsDisplayed()
            composeRule.touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_CANCEL_TAG), fx, fy)
            assertEquals(0, composeRule.onAllNodesWithTag(SAVE_ROUTE_DIALOG_TAG).fetchSemanticsNodes().size)
            composeRule.runOnUiThread { plotting.onCancel() }
        }
        startAtCreekAndAddRidge()
        composeRule.touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_SAVE_TAG), 0.5f, 0.5f)
        composeRule.touchAcross(composeRule.onNodeWithTag(SAVE_ROUTE_CONFIRM_TAG), 0.9f, 0.9f)

        assertEquals(listOf("Creek to Ridge" to null), saves)
        assertEquals(RoutePlotMode.Off, mode())
    }

    @Test
    fun `real touches across the handle toggle the card, and a real drag up expands it`() {
        setScreen()
        startAtCreekAndAddRidge()
        ACROSS.forEachIndexed { i, (fx, fy) ->
            composeRule.touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG), fx, fy)
            assertEquals("touch $i at ($fx, $fy)", i % 2 == 0, plotting.uiState.value.expanded)
        }
        // Five touches leave it expanded; one more collapses it, then a drag up expands it again.
        composeRule.touchAcross(composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG), 0.5f, 0.5f)
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { swipeUp(startY = bottom, endY = top - 80f) }
        composeRule.waitForIdle()
        assertTrue(plotting.uiState.value.expanded)
        composeRule.onNodeWithTag(ROUTE_CARD_LEGS_TAG).assertIsDisplayed()
    }

    @Test
    fun `real long-presses just above the card reach the map`() {
        setScreen()
        startAtCreekAndAddRidge()
        val card = composeRule.onNodeWithTag(ROUTE_CARD_TAG).getUnclippedBoundsInRoot()
        listOf(0.2f, 0.4f, 0.6f).forEachIndexed { i, fx ->
            val x = card.left + (card.right - card.left) * fx
            val at = with(composeRule.density) { Offset(x.toPx(), (card.top - 12.dp).toPx()) }
            composeRule.onRoot().performTouchInput { longClick(at) }
            composeRule.waitForIdle()
            assertEquals("a long-press at $fx across, just above the card", i + 1, map.longPresses)
        }
    }

    @Test
    fun `a route trip's pin opens the route at once, with no bubble, the card under its name, and a fit that ends following`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            composeRule.runOnUiThread { plotting.onCloseSavedRoute() }
            composeRule.waitForIdle()
            composeRule.touchAcross(composeRule.onNodeWithTag(glyphTag(ROUTE_TRIP.id)), fx, fy)

            assertEquals("a touch at ($fx, $fy)", ROUTE_TRIP, (mode() as RoutePlotMode.Showing).trip)
            assertEquals(0, composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().size)
            composeRule.onNodeWithTag(ROUTE_CARD_NAME_TAG).assertIsDisplayed()
            val request = assertNotNullAndGet(map.renderMode?.cameraRequest)
            assertTrue(request.stopsFollowing)
            assertEquals(2, map.content?.plottedRoute?.stopDots?.size)
        }
    }

    @Test
    fun `a route trip's pin tapped mid-plot asks first`() {
        setScreen()
        startAtCreekAndAddRidge()

        composeRule.tapGlyph(ROUTE_TRIP.id)

        assertEquals(ROUTE_TRIP, plotting.uiState.value.pendingDiscard)
        assertTrue(mode() is RoutePlotMode.Plotting)
    }

    @Test
    fun `Back with the card expanded cancels plotting in one tap`() {
        setScreen()
        startAtCreekAndAddRidge()
        composeRule.runOnUiThread { plotting.onSetExpanded(true) }
        composeRule.waitForIdle()
        // The planner's ruling on Q6: one tap, expanded or not.
        composeRule.back()
        assertEquals(RoutePlotMode.Off, mode())
    }

    @Test
    fun `Back on a saved route closes the card and the line`() {
        setScreen()
        composeRule.tapGlyph(ROUTE_TRIP.id)

        composeRule.back()

        assertEquals(RoutePlotMode.Off, mode())
        assertEquals(null, map.content?.plottedRoute)
    }

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
