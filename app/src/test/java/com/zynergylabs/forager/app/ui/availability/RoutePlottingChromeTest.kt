package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_CANCEL_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The route card and the chrome it shares the bottom of the map with (dispatch 2026-10-30-08, Amendment 3, RECORD -1861), through
 * the real screen and a real [RoutePlottingViewModel]:
 * - the owner: "Cluster stays above card (Recommended)". While the card shows, the cluster's lowest position is the card's top;
 *   when the card closes it goes back, and the height the user chose stays remembered (CLAUDE.md, UX defaults).
 * - the owner: "Pill sits above the card (Recommended)". "Return to Route" moves up to clear the card, and both stay tappable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingChromeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.32, lng = -122.63, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.33, lng = -122.63, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 200.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 200.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen(navigating: Boolean = false) {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                uiStateTransform = { if (navigating) it.copy(liveFix = fix) else it },
                navigation = if (navigating) AvailabilityScreenNavigation(isReturning = true, onToggleReturning = {}, navigationTarget = start) else AvailabilityScreenNavigation(),
                recording = AvailabilityScreenRecording(isRecording = navigating),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** A real long-press drag on the cluster's handle, as a finger moves it (AttributionClearOfLTest's shape). */
    private fun dragClusterDown() {
        val handle = bounds("map-icon-bar-minimize-handle")
        val from = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        val by = with(composeRule.density) { 900.dp.toPx() }
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from + Offset(0f, by))
            advanceEventTime(50)
            up()
        }
        settle()
    }

    private fun startPlotting() {
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        settle()
        composeRule.onNodeWithTag(glyphTag(RIDGE.id)).performTouchInput { click(center) }
        settle()
    }

    @Test
    fun `while the card shows, the icon cluster's lowest position is the card's top, and it goes back when the card closes`() {
        setScreen()
        dragClusterDown()
        val lowest = bounds(MAP_ICON_CLUSTER_TAG)

        startPlotting()
        val card = bounds(ROUTE_CARD_TAG)
        assertTrue("precondition: the cluster dragged to its lowest (bottom ${lowest.bottom}) reaches below the card's top (${card.top})", lowest.bottom > card.top)
        val withCard = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("the cluster's bottom ${withCard.bottom} is at or above the card's top ${card.top}", withCard.bottom <= card.top + 0.5.dp)

        composeRule.runOnUiThread { plotting.onSetExpanded(true) }
        settle()
        val expanded = bounds(ROUTE_CARD_TAG)
        val withExpanded = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("expanded: the cluster's bottom ${withExpanded.bottom} is at or above the card's top ${expanded.top}", withExpanded.bottom <= expanded.top + 0.5.dp)

        composeRule.runOnUiThread { plotting.onCancel() }
        settle()
        assertEquals(RoutePlotMode.Off, plotting.uiState.value.mode)
        assertEquals("the card closed, the cluster is back at the height the user chose", lowest.bottom.value, bounds(MAP_ICON_CLUSTER_TAG).bottom.value, 1f)
    }

    @Test
    fun `Return to Route sits above the card, and real touches across each reach their own control`() {
        setScreen(navigating = true)
        startPlotting()
        val view = map.renderMode?.navigationView ?: throw AssertionError("navigating, so the map was asked for its navigation view")
        composeRule.runOnUiThread { view.onLeftView() }
        settle()
        assertTrue("moved away from the navigation view, so the pill shows", shown(RETURN_TO_ROUTE_TAG))

        val pill = bounds(RETURN_TO_ROUTE_TAG)
        val card = bounds(ROUTE_CARD_TAG)
        assertTrue("the pill's bottom ${pill.bottom} is at or above the card's top ${card.top}", pill.bottom <= card.top + 0.5.dp)

        // The pill: one real touch at one of the five points; each tap brings the view back, so it is moved away again first.
        ACROSS.forEach { (fx, fy) ->
            val viewNow = map.renderMode!!.navigationView!!
            composeRule.runOnUiThread { viewNow.onLeftView() }
            settle()
            val before = map.renderMode!!.navigationView!!.restoreRequestId
            composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("a touch at ($fx, $fy) on the pill was its own", before + 1, map.renderMode!!.navigationView!!.restoreRequestId)
            assertTrue("and plotting went on", plotting.uiState.value.mode is RoutePlotMode.Plotting)
        }

        // The card's Save, with the pill showing above it.
        composeRule.runOnUiThread { map.renderMode!!.navigationView!!.onLeftView() }
        settle()
        assertTrue(shown(RETURN_TO_ROUTE_TAG))
        ACROSS.forEach { (fx, fy) ->
            composeRule.onNodeWithTag(ROUTE_CARD_SAVE_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertTrue("a touch at ($fx, $fy) on Save opened the save dialog", shown(SAVE_ROUTE_DIALOG_TAG))
            composeRule.onNodeWithTag(SAVE_ROUTE_CANCEL_TAG).performTouchInput { click(center) }
            settle()
        }
    }
}
