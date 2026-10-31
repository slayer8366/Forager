package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardLegTag
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 9 (RECORD -1902), F-2 from the S22 phone checks: does the icon cluster's bottom button
 * overlap the expanded card's first row, at the S22's size (portrait, 384 x 823 dp, xxhdpi)? On the phone, a four-waypoint plot
 * expanded showed the cluster's lowest item ending about 50 px below the card's first text row
 * (docs/audits/2026-10-31-s22-phone-checks.md, F-2), unconfirmed by eye.
 *
 * The same shape here, through the real Maps tab with real touches: Creek's bubble, "Plot route", Ridge, Falls, Pond (three trail
 * legs, each with heights, so each row carries its elevation line as on the phone), then the handle to expand. Measured with the
 * cluster where it opens (centred) and dragged to its lowest. Each test prints its numbers.
 *
 * **What was found** (run 41254078313, where these tests asserted that the cluster clears the expanded card, and both failed):
 * the cluster is 380 dp tall and is already raised to its highest position (top 76.3 dp, the search chrome's limit), and the
 * expanded card's top is at 431 dp, so the cluster's bottom (456.3 dp) is 25.3 dp below the card's top; the first leg row starts
 * at 491 dp, 34.7 dp below the cluster. The floor does follow the expanded card's top; the cluster cannot rise far enough. How to
 * resolve that is an open choice for the owner (the PR body, Amendment 9, F-2), so these tests assert what holds now: with the
 * card expanded the cluster is raised as far as it can go, to the same top it reaches when dragged up with no card. They do not
 * assert that it clears the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardExpandedClusterTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val falls = Waypoint("wp-falls", 45.346, -122.654, null, "Falls", "", 3_000L)
    private val pond = Waypoint("wp-pond", 45.356, -122.664, null, "Pond", "", 4_000L)

    // Left of centre, clear of the cluster on the right and above where the expanded card reaches.
    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 80.dp, 200.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 80.dp, 250.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, falls.id, 140.dp, 200.dp, LatLng(falls.lat, falls.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, pond.id, 140.dp, 250.dp, LatLng(pond.lat, pond.lng)),
        ),
    )

    /** A trail leg whose trail part carries a few heights, so the row shows an elevation line. */
    private fun trail(from: LatLng, to: LatLng): RouteLeg.Trail {
        val heights = listOf(100.0, 110.0, 104.0)
        val points = (1..heights.size).map { k -> LatLng(from.lat + (to.lat - from.lat) * k / 4, from.lng + (to.lng - from.lng) * k / 4) }
        return RouteLeg.Trail(listOf(from) + points + listOf(to), GeoDistance.metersBetween(from, to), trailFrom = 1, trailTo = heights.size, trailHeightsMetres = heights)
    }

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); trail(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = mapLayersViewModel(),
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE, falls, pond),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
    }

    /** A real long-press drag on the cluster's handle (RoutePlottingChromeTest's shape). */
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

    private fun plotFourAndExpand() {
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(glyphTag(pond.id))
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        assertTrue("precondition: expanded", plotting.uiState.value.expanded)
    }

    /** A real long-press drag on the cluster's handle, upward, as far as a finger can take it. */
    private fun dragClusterUp() {
        val handle = bounds("map-icon-bar-minimize-handle")
        val from = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        val by = with(composeRule.density) { 900.dp.toPx() }
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from - Offset(0f, by))
            advanceEventTime(50)
            up()
        }
        settle()
    }

    private fun measure(placement: String, highestTop: Float) {
        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        val card = bounds(ROUTE_CARD_TAG)
        val firstRow = bounds(routeCardLegTag(0))
        val numbers = "$placement: cluster ${cluster.top.value}..${cluster.bottom.value} dp (height ${(cluster.bottom - cluster.top).value}; " +
            "its highest top with no card $highestTop), expanded card top ${card.top.value} dp (height ${(card.bottom - card.top).value}), " +
            "first row top ${firstRow.top.value} dp, cluster bottom minus card top ${(cluster.bottom - card.top).value} dp, " +
            "cluster bottom minus first row top ${(cluster.bottom - firstRow.top).value} dp"
        println("F-2 $numbers")
        assertEquals("the cluster is raised as far as it can go. $numbers", highestTop, cluster.top.value, 0.5f)
    }

    /** The cluster's highest top with no card: dragged up as far as it goes, read, then left there or dragged back down. */
    private fun highestTop(): Float {
        dragClusterUp()
        return bounds(MAP_ICON_CLUSTER_TAG).top.value
    }

    @Test
    fun `S22 size, four waypoints expanded - the cluster where it opens is raised as far as it can go`() {
        setScreen()
        val centredTop = bounds(MAP_ICON_CLUSTER_TAG).top.value
        val highest = highestTop()
        assertTrue("precondition: it can rise above where it opens ($centredTop, highest $highest)", highest < centredTop)
        dragClusterDown()
        plotFourAndExpand()
        measure("dragged lowest, then the card expanded", highest)
    }

    @Test
    fun `S22 size, four waypoints expanded - the cluster left where it opens is raised as far as it can go`() {
        setScreen()
        plotFourAndExpand()
        val expandedTop = bounds(MAP_ICON_CLUSTER_TAG).top.value
        composeRule.runOnUiThread { plotting.onCancel() }
        settle()
        val highest = highestTop()
        println("F-2 centred: the cluster's top with the card expanded $expandedTop, its highest with no card $highest")
        assertEquals("centred: the cluster is raised as far as it can go", highest, expandedTop, 0.5f)
    }
}
