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
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-30-08, F-2 from the S22 phone checks. Amendment 9 (RECORD -1902) measured it at the S22's size (portrait,
 * 384 x 823 dp, xxhdpi), with four waypoints plotted and the card expanded (run 41254078313): the cluster is 380 dp tall, already
 * at its highest (top 76.3 dp, the search chrome's limit), and the expanded card's top is at 431 dp, so the cluster's bottom
 * (456.3 dp) is 25.3 dp below the card's top. It cannot fit.
 *
 * Amendment 10 (RECORD -1904, the owner: "Minimise the column while open (Recommended)"): while the card is **expanded** the column
 * folds to its minimised state, and when the card collapses or closes it comes back exactly as the user had it: side, height
 * and minimised flag. It never changes the remembered state. At peek it stays as Amendment 3 has it, raised above the card.
 *
 * The same shape as the phone, through the real Maps tab with real touches: Creek's bubble, "Plot route", Ridge, Falls, Pond
 * (three trail legs, each with heights, so each row carries its elevation line), then the handle to expand. The glyphs are clear
 * of the cluster on either side and above where the expanded card reaches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardExpandedClusterTest {

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

    /** A real long-press drag on the cluster's handle, past the side snap, to the screen's left. */
    private fun dragClusterToLeft() {
        val handle = bounds("map-icon-bar-minimize-handle")
        val from = with(composeRule.density) { Offset(((handle.left + handle.right) / 2).toPx(), ((handle.top + handle.bottom) / 2).toPx()) }
        val by = with(composeRule.density) { 200.dp.toPx() }
        composeRule.onRoot().performTouchInput {
            down(from)
            advanceEventTime(600)
            moveTo(from - Offset(by, 0f))
            advanceEventTime(50)
            up()
        }
        settle()
    }

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

    private fun clusterShows() = composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().isNotEmpty()

    private fun restoreHandleShows() = composeRule.onAllNodesWithContentDescription(RESTORE).fetchSemanticsNodes().isNotEmpty()

    private fun restoreHandle(): DpRect = composeRule.onNodeWithContentDescription(RESTORE).getUnclippedBoundsInRoot()

    private fun touchRestoreHandle() {
        composeRule.onNodeWithContentDescription(RESTORE).performTouchInput { click(center) }
        settle()
    }

    /** The column is folded and nothing of it is over the expanded card: Amendment 9's numbers, now required. */
    private fun assertFoldedClearOfTheCard(placement: String) {
        val card = bounds(ROUTE_CARD_TAG)
        val firstRow = bounds(routeCardLegTag(0))
        if (clusterShows()) {
            val cluster = bounds(MAP_ICON_CLUSTER_TAG)
            assertTrue(
                "$placement: the column overlaps the expanded card: cluster ${cluster.top.value}..${cluster.bottom.value} dp, card top ${card.top.value} dp, first row top ${firstRow.top.value} dp",
                cluster.bottom <= card.top + 0.5.dp,
            )
        }
        assertTrue("$placement: the column is folded to its restore handle", !clusterShows() && restoreHandleShows())
        val handle = restoreHandle()
        println("F-2 $placement: restore handle ${handle.top.value}..${handle.bottom.value} dp, card top ${card.top.value} dp, first row top ${firstRow.top.value} dp")
        assertTrue("$placement: the handle's bottom ${handle.bottom} is above the card's top ${card.top}", handle.bottom <= card.top + 0.5.dp)
    }

    private fun assertSamePlace(what: String, expected: DpRect, actual: DpRect) {
        assertEquals("$what: left", expected.left.value, actual.left.value, 1f)
        assertEquals("$what: right", expected.right.value, actual.right.value, 1f)
        assertEquals("$what: top", expected.top.value, actual.top.value, 1f)
        assertEquals("$what: bottom", expected.bottom.value, actual.bottom.value, 1f)
    }

    @Test
    fun `S22 size, four waypoints expanded - the column left where it opens folds, clear of the card`() {
        setScreen()
        plotFour()
        expandCard()
        assertFoldedClearOfTheCard("where it opens")
    }

    @Test
    fun `S22 size, four waypoints expanded - the column dragged to its lowest folds, clear of the card`() {
        setScreen()
        dragClusterDown()
        plotFour()
        expandCard()
        assertFoldedClearOfTheCard("dragged lowest")
    }

    @Test
    fun `at peek the column is not folded, and it stays raised above the card`() {
        setScreen()
        dragClusterDown()
        plotFour()
        assertTrue("the column shows at peek", clusterShows())
        assertTrue("no restore handle at peek", !restoreHandleShows())
        assertTrue("above the card", bounds(MAP_ICON_CLUSTER_TAG).bottom <= bounds(ROUTE_CARD_TAG).top + 0.5.dp)
    }

    @Test
    fun `the column comes back on the side and at the height the user chose, when the card collapses and when it closes`() {
        setScreen()
        dragClusterDown()
        dragClusterToLeft()
        val chosen = bounds(MAP_ICON_CLUSTER_TAG)
        assertTrue("precondition: on the left", chosen.left < 100.dp)
        plotFour()
        val atPeek = bounds(MAP_ICON_CLUSTER_TAG)

        expandCard()
        assertFoldedClearOfTheCard("on the left")
        assertTrue("the handle is on the side the user chose", restoreHandle().left < 100.dp)

        collapseCard()
        assertSamePlace("collapsed", atPeek, bounds(MAP_ICON_CLUSTER_TAG))

        expandCard()
        composeRule.onNodeWithTag(ROUTE_CARD_CANCEL_TAG).performTouchInput { click(center) }
        settle()
        assertEquals(RoutePlotMode.Off, plotting.uiState.value.mode)
        assertSamePlace("closed while expanded", chosen, bounds(MAP_ICON_CLUSTER_TAG))
    }

    @Test
    fun `a column the user minimised stays minimised after the card collapses and closes, and their own restore still works`() {
        setScreen()
        val open = bounds(MAP_ICON_CLUSTER_TAG)
        touchCentreOf("map-icon-bar-minimize-handle")
        assertTrue("precondition: minimised by the user", !clusterShows() && restoreHandleShows())

        plotFour()
        expandCard()
        assertFoldedClearOfTheCard("minimised by the user")
        collapseCard()
        assertTrue("collapsed: still as the user had it", !clusterShows() && restoreHandleShows())
        composeRule.onNodeWithTag(ROUTE_CARD_CANCEL_TAG).performTouchInput { click(center) }
        settle()
        assertTrue("closed: still as the user had it", !clusterShows() && restoreHandleShows())

        touchRestoreHandle()
        assertSamePlace("the user's own restore", open, bounds(MAP_ICON_CLUSTER_TAG))
    }

    @Test
    fun `a card still expanded from the last route folds the column only once a route shows`() {
        setScreen()
        plotFour()
        expandCard()
        composeRule.onNodeWithTag(ROUTE_CARD_CANCEL_TAG).performTouchInput { click(center) }
        settle()
        assertTrue("precondition: the card's anchor is kept", plotting.uiState.value.expanded)
        assertTrue("no card, so the column shows", clusterShows())

        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        assertTrue("the card opens expanded, so the column folds", !clusterShows() && restoreHandleShows())
    }

    @Test
    fun `a touch on the restore handle while the card is expanded collapses the card, and the column comes back`() {
        setScreen()
        plotFour()
        val atPeek = bounds(MAP_ICON_CLUSTER_TAG)
        expandCard()

        touchRestoreHandle()

        assertTrue("the card went back to its peek", !plotting.uiState.value.expanded)
        assertSamePlace("the column is back", atPeek, bounds(MAP_ICON_CLUSTER_TAG))
    }

    @Test
    fun `a touch on the restore handle of a column the user minimised collapses the card and leaves their minimised flag`() {
        setScreen()
        touchCentreOf("map-icon-bar-minimize-handle")
        plotFour()
        expandCard()

        touchRestoreHandle()

        assertTrue("the card went back to its peek", !plotting.uiState.value.expanded)
        assertTrue("still as the user had it", !clusterShows() && restoreHandleShows())
    }
}

private const val RESTORE = "Show map controls"

/**
 * The same rule in a short landscape window, where the column is the L with its own remembered position (Amendment 10): the card
 * expanded folds it, and collapsed it comes back where it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class RouteCardExpandedClusterLandscapeTest {

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

    private fun clusterShows() = composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the L folds while the card is expanded, and comes back in its place`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(viewModel = mapLayersViewModel(), mapSlot = map.slot, store = OneCellStore(), waypoints = listOf(CREEK, RIDGE), routePlotting = RoutePlottingControls(state, plotting))
        }
        settle()
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        settle()
        val atPeek = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertTrue("precondition: expanded", plotting.uiState.value.expanded)
        assertTrue("the L folded", !clusterShows())

        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        val back = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        assertEquals("left", atPeek.left.value, back.left.value, 1f)
        assertEquals("top", atPeek.top.value, back.top.value, 1f)
    }
}
