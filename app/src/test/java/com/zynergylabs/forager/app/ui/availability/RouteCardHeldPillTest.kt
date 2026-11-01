package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.center
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_LEGEND_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

private const val RECORD = "control-pill-record"
private const val RETURN = "control-pill-return-to-vehicle"
private const val PILL = "control-pill"
private const val RESTORE = "Show map controls"

/**
 * Dispatch 2026-10-30-08, Amendment 11 (RECORD -1909, the owner: "Keep the pill showing (Recommended)"), reversing Amendment 10's
 * folding of the pill: while the route card is expanded only the column folds, and the record and return pill stays showing,
 * reachable and clear of the card. The real Maps tab at the S22's size (portrait, xxhdpi) with four stops plotted and a recording
 * running, every claim by a real touch sampled across the target's own bounds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardHeldPillTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val falls = Waypoint("wp-falls", 45.346, -122.654, null, "Falls", "", 3_000L)
    private val pond = Waypoint("wp-pond", 45.356, -122.664, null, "Pond", "", 4_000L)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 150.dp, 200.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 150.dp, 250.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, falls.id, 220.dp, 200.dp, LatLng(falls.lat, falls.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, pond.id, 220.dp, 250.dp, LatLng(pond.lat, pond.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 150.dp, 320.dp, ROUTE_TRIP.location),
        ),
    )

    /** A trail leg with heights, so each row carries its elevation line and the card is as tall as on the phone. */
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

    private var stopTouches = 0
    private var returnTouches = 0

    private fun setScreen(plannedTrips: List<PlannedTrip> = emptyList(), withLegend: Boolean = false) {
        val store = OneCellStore()
        val viewModel = if (withLegend) mapLayersViewModel(store = store, plannedTrips = plannedTrips) else mapLayersViewModel(plannedTrips = plannedTrips)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = store,
                waypoints = listOf(CREEK, RIDGE, falls, pond),
                routePlotting = RoutePlottingControls(state, plotting),
                navigation = AvailabilityScreenNavigation(onToggleReturning = { returnTouches++ }),
                recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = { stopTouches++ }),
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

    private fun dragCluster(by: Offset) {
        val handle = bounds("map-icon-bar-minimize-handle")
        val from = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from + by)
            advanceEventTime(50)
            up()
        }
        settle()
    }

    private fun dragClusterDown() = dragCluster(Offset(0f, with(composeRule.density) { 900.dp.toPx() }))

    private fun dragClusterToLeft() = dragCluster(Offset(-with(composeRule.density) { 200.dp.toPx() }, 0f))

    private fun plotFour() {
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(glyphTag(pond.id))
    }

    private fun expandCard() {
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        assertTrue("precondition: expanded", plotting.uiState.value.expanded)
    }

    private fun collapseCard() {
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        assertTrue("precondition: collapsed", !plotting.uiState.value.expanded)
    }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun clusterShows() = shown(MAP_ICON_CLUSTER_TAG) > 0

    private fun assertPillShowsClearOfTheCard(placement: String) {
        assertTrue("$placement: only the column folds", !clusterShows() && composeRule.onAllNodesWithContentDescription(RESTORE).fetchSemanticsNodes().isNotEmpty())
        assertEquals("$placement: the record and return pill shows, once", 1, shown(PILL))
        val pill = bounds(PILL)
        val card = bounds(ROUTE_CARD_TAG)
        println("A11 $placement: pill ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value} dp, card top ${card.top.value} dp")
        assertTrue("$placement: the pill's bottom ${pill.bottom} is above the card's top ${card.top}", pill.bottom <= card.top + 0.5.dp)
        val handle = composeRule.onNodeWithContentDescription(RESTORE).getUnclippedBoundsInRoot()
        println("A11 $placement: restore handle ${handle.left.value}..${handle.right.value} x ${handle.top.value}..${handle.bottom.value} dp")
        assertTrue(
            "$placement: the pill ${pill.top.value}..${pill.bottom.value} dp is clear of the restore handle ${handle.top.value}..${handle.bottom.value} dp",
            pill.top >= handle.bottom - 0.5.dp || pill.bottom <= handle.top + 0.5.dp,
        )
    }

    /** Stop and return each take a real touch at five points across their own bounds, and neither moves the card or the route. */
    private fun assertStopAndReturnReachable(placement: String) {
        val modeBefore = plotting.uiState.value.mode
        listOf(RECORD, RETURN).forEach { tag ->
            ACROSS.forEach { (fx, fy) ->
                val stopsBefore = stopTouches
                val returnsBefore = returnTouches
                composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fx, height * fy)) }
                settle()
                val where = "$placement: a touch at ($fx, $fy) on $tag"
                assertEquals(where, if (tag == RECORD) stopsBefore + 1 else stopsBefore, stopTouches)
                assertEquals(where, if (tag == RETURN) returnsBefore + 1 else returnsBefore, returnTouches)
                assertTrue("$where: the card stays expanded", plotting.uiState.value.expanded)
                assertEquals(where, modeBefore, plotting.uiState.value.mode)
            }
        }
    }

    @Test
    fun `S22 size, four stops expanded, recording - the pill clears the card and takes real touches`() {
        setScreen()
        plotFour()
        expandCard()
        assertPillShowsClearOfTheCard("where it opens")
        assertStopAndReturnReachable("where it opens")
    }

    @Test
    fun `the column dragged to its lowest - the pill still clears the card and takes real touches`() {
        setScreen()
        dragClusterDown()
        plotFour()
        expandCard()
        assertPillShowsClearOfTheCard("dragged lowest")
        assertStopAndReturnReachable("dragged lowest")
    }

    @Test
    fun `the column on the left - the pill stays on the side the user chose`() {
        setScreen()
        dragClusterDown()
        dragClusterToLeft()
        plotFour()
        expandCard()
        assertPillShowsClearOfTheCard("on the left")
        assertTrue("on the left: ${bounds(PILL).left}", bounds(PILL).left < 100.dp)
        assertStopAndReturnReachable("on the left")
    }

    @Test
    fun `the pill keeps above the legend chip as it does the card`() {
        setScreen(withLegend = true)
        plotFour()
        expandCard()
        assertPillShowsClearOfTheCard("with the legend chip")
        val chip = bounds(MAP_LEGEND_CHIP_TAG)
        val pill = bounds(PILL)
        assertTrue("the pill's bottom ${pill.bottom} is above the chip's top ${chip.top}", pill.bottom <= chip.top + 0.5.dp)
    }

    @Test
    fun `a saved route's expanded card keeps the pill too, and Back closing it leaves the one pill in the column`() {
        setScreen(listOf(ROUTE_TRIP))
        touchCentreOf(glyphTag(ROUTE_TRIP.id))
        expandCard()
        assertPillShowsClearOfTheCard("saved route")
        assertStopAndReturnReachable("saved route")

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(RoutePlotMode.Off, plotting.uiState.value.mode)
        assertEquals("one pill, in the column", 1, shown(PILL))
        assertTrue("the column is back", clusterShows())
    }

    @Test
    fun `collapsing and closing the card leave the one pill back in the column at its place`() {
        setScreen()
        val open = bounds(PILL)
        plotFour()
        val atPeek = bounds(PILL)
        expandCard()
        collapseCard()
        assertEquals("collapsed: one pill", 1, shown(PILL))
        assertTrue("collapsed: the column is back", clusterShows())
        assertEquals("collapsed: its top", atPeek.top.value, bounds(PILL).top.value, 1f)

        expandCard()
        composeRule.onNodeWithTag(ROUTE_CARD_CANCEL_TAG).performTouchInput { click(center) }
        settle()
        assertEquals("closed: one pill", 1, shown(PILL))
        assertEquals("closed: its top", open.top.value, bounds(PILL).top.value, 1f)
    }

    @Test
    fun `a column the user minimised keeps its pill folded, as they left it, with the card expanded`() {
        setScreen()
        touchCentreOf("map-icon-bar-minimize-handle")
        assertEquals("precondition: minimised by the user", 0, shown(PILL))
        plotFour()
        expandCard()
        assertEquals("no pill: the user minimised it themselves", 0, shown(PILL))
    }
}

/** The same rule in a short landscape window, where the pill is the L's horizontal one and the L folds (Amendment 11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class RouteCardHeldPillLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(emptyList())

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    @Test
    fun `the L folds, its pill stays clear of the card, and stop takes real touches`() {
        var stopTouches = 0
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = mapLayersViewModel(),
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = { stopTouches++ }),
            )
        }
        settle()
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        settle()
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertTrue("precondition: expanded", plotting.uiState.value.expanded)
        assertEquals("the L folded", 0, composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().size)

        val pill = composeRule.onNodeWithTag(PILL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithTag(ROUTE_CARD_TAG).getUnclippedBoundsInRoot()
        println("A11 landscape: pill ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value} dp, card top ${card.top.value} dp, card ${card.left.value}..${card.right.value} dp")
        assertTrue("the pill's bottom ${pill.bottom} is above the card's top ${card.top}", pill.bottom <= card.top + 0.5.dp)
        ACROSS.forEachIndexed { i, (fx, fy) ->
            composeRule.onNodeWithTag(RECORD).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("a touch at ($fx, $fy)", i + 1, stopTouches)
        }
    }
}
