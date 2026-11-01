package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_PROGRESS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardLegDistanceTag
import com.zynergylabs.forager.app.ui.route.routeCardLegNameTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 7 (RECORD -1890): the leg stall found in Amendment 6. A router that answers without
 * suspending let `RoutePlottingViewModel.routeNextLeg`'s launched body finish inside `launch` on `Main.immediate`, so the
 * finished job was stored as the leg in flight and every later leg was never asked for.
 *
 * This router has no suspension point at all. The route is plotted on the real Maps tab with real touches, through the
 * bubble's "Plot route" and then the waypoints, and every leg must land. The other screen tests keep their `yield()` routers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingImmediateRouterTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val falls = Waypoint("wp-falls", 45.346, -122.654, null, "Falls", "", 3_000L)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, falls.id, 60.dp, 240.dp, LatLng(falls.lat, falls.lng)),
        ),
    )

    /** The legs asked for, in order. */
    private val asked = mutableListOf<Pair<LatLng, LatLng>>()

    private val plotting = RoutePlottingViewModel(
        // No suspension point: the leg is answered before routeLeg returns (Amendment 7).
        routeLeg = { a, b ->
            asked += a to b
            straight(a, b)
        },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val viewModel by lazy { mapLayersViewModel() }

    private fun setScreen() {
        val vm = viewModel
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = vm,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE, falls),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun metres(a: Waypoint, b: Waypoint) = straight(a.stop().position, b.stop().position).meters

    @Test
    fun `a router that answers without suspending - three stops, then a fourth, and every leg lands`() {
        setScreen()
        touchCentreOf(glyphTag(CREEK.id)) // the bubble
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        val unit = viewModel.uiState.value.distanceUnit

        assertEquals("both legs asked for, in order", listOf(CREEK.stop().position to RIDGE.stop().position, RIDGE.stop().position to falls.stop().position), asked)
        assertEquals("Creek to Ridge", text(routeCardLegNameTag(0)))
        assertEquals(formatDistanceMeters(metres(CREEK, RIDGE), unit), text(routeCardLegDistanceTag(0)))
        assertEquals("Ridge to Falls", text(routeCardLegNameTag(1)))
        assertEquals(formatDistanceMeters(metres(RIDGE, falls), unit), text(routeCardLegDistanceTag(1)))
        assertEquals("3 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        // Amendment 10 (RECORD -1904): the sum of the rows as shown; the metres summed read 2.7.
        assertEquals("2.8 km", text(ROUTE_CARD_ROUTED_TOTAL_TAG))
        assertTrue("no leg is still in flight", !exists(ROUTE_CARD_PROGRESS_TAG))

        touchCentreOf(glyphTag(CREEK.id))

        assertEquals("the third leg asked for", 3, asked.size)
        assertEquals("Falls to Creek", text(routeCardLegNameTag(2)))
        assertEquals(formatDistanceMeters(metres(falls, CREEK), unit), text(routeCardLegDistanceTag(2)))
        assertEquals("4 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertTrue("no leg is still in flight", !exists(ROUTE_CARD_PROGRESS_TAG))
    }
}
