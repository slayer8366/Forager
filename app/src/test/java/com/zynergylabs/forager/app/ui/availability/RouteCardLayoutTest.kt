package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.RouteElevation
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_LEGS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAME_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.RouteCard
import com.zynergylabs.forager.app.ui.route.RouteCardCallbacks
import com.zynergylabs.forager.app.ui.route.RouteCardContent
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardLegDistanceTag
import com.zynergylabs.forager.app.ui.route.routeCardLegElevationTag
import com.zynergylabs.forager.app.ui.route.routeCardLegNameTag
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
 * Dispatch 2026-10-30-08, Amendment 6 (RECORD -1887): the route card's layout, from the owner's first use on the S22.
 *
 * Peek while plotting: "N Waypoints selected" with the total distance at its right; the arrow chain of names is gone.
 * Expanded: per leg "<from> to <to>" with its distance at the right; the last row "N Waypoints routed" with the total.
 * No elevation line yet: heights arrive with #447. The seam, an optional elevation per leg and for the total, is shown here
 * by handing the card one directly, since nothing in the app makes one yet.
 *
 * The screen tests walk the real Maps tab with real touches (Creek, then Ridge, then Creek again: A to B to A).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardLayoutTest {

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
    private val plotting = RoutePlottingViewModel(
        // The router suspends once before it answers, as the app's does (PlotRouteUseCase reads the downloaded areas from the
        // database). A router that answers without suspending is RoutePlottingImmediateRouterTest's (Amendment 7); this test is
        // about the card, so its router stays as it was.
        routeLeg = { a, b -> yield(); straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val viewModel by lazy { mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP)) }

    private fun setScreen() {
        val vm = viewModel
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = vm,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
            )
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    /** Every text on the card, from the unmerged tree. */
    private fun cardTexts(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null } and hasAnyAncestor(hasTestTag(ROUTE_CARD_TAG)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** [right] sits at [left]'s right, on the same line: it starts where [left] ends or later, and their middles share a line. */
    private fun assertAtRightOf(where: String, left: DpRect, right: DpRect) {
        assertTrue("$where: starts at ${right.left}, right of the text ending at ${left.right}", right.left >= left.right)
        val middle = (right.top + right.bottom) / 2
        assertTrue("$where: its middle $middle is on the text's line, ${left.top}..${left.bottom}", middle in left.top..left.bottom)
    }

    private fun plotCreekRidgeCreek() {
        touchCentreOf(glyphTag(CREEK.id)) // the bubble
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(CREEK.id))
    }

    private fun legMeters(i: Int): Double = (listOf(CREEK, RIDGE, CREEK).map { it.stop().position }.zipWithNext())[i].let { (a, b) -> straight(a, b).meters }

    @Test
    fun `the peek while plotting shows the count with the total at its right, and no chain of names`() {
        setScreen()
        plotCreekRidgeCreek()
        val unit = viewModel.uiState.value.distanceUnit

        assertEquals("3 Waypoints selected", text(ROUTE_CARD_COUNT_TAG))
        assertEquals(formatDistanceMeters(legMeters(0) + legMeters(1), unit), text(ROUTE_CARD_TOTAL_TAG))
        assertAtRightOf("the total", bounds(ROUTE_CARD_COUNT_TAG), bounds(ROUTE_CARD_TOTAL_TAG))
        assertTrue("no arrow on the card: ${cardTexts()}", cardTexts().none { "→" in it })
        assertTrue("no stop names on the peek: ${cardTexts()}", cardTexts().none { "Creek" in it || "Ridge" in it })
    }

    @Test
    fun `the expanded list has a row per leg with its distance at the right, then the routed total last, and no elevation`() {
        setScreen()
        plotCreekRidgeCreek()
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        val unit = viewModel.uiState.value.distanceUnit

        listOf("Creek to Ridge", "Ridge to Creek").forEachIndexed { i, name ->
            assertEquals("leg $i", name, text(routeCardLegNameTag(i)))
            assertEquals("leg $i's distance", formatDistanceMeters(legMeters(i), unit), text(routeCardLegDistanceTag(i)))
            assertAtRightOf("leg $i's distance", bounds(routeCardLegNameTag(i)), bounds(routeCardLegDistanceTag(i)))
            assertTrue("leg $i: no elevation line yet", !exists(routeCardLegElevationTag(i)))
        }
        assertEquals("3 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertEquals(text(ROUTE_CARD_TOTAL_TAG), text(ROUTE_CARD_ROUTED_TOTAL_TAG))
        assertAtRightOf("the routed total", bounds(ROUTE_CARD_ROUTED_COUNT_TAG), bounds(ROUTE_CARD_ROUTED_TOTAL_TAG))
        val legs = bounds(ROUTE_CARD_LEGS_TAG)
        val routed = bounds(ROUTE_CARD_ROUTED_TAG)
        assertTrue("the routed row is in the expanded list", routed.top >= legs.top && routed.bottom <= legs.bottom + 0.5.dp)
        assertTrue("the routed row is below the last leg", routed.top >= bounds(routeCardLegTag(1)).bottom)
        assertTrue("no elevation for the total yet", !exists(ROUTE_CARD_ROUTED_ELEVATION_TAG))
        assertTrue("no elevation text anywhere on the card: ${cardTexts()}", cardTexts().none { it.startsWith("Elevation") })
        assertTrue("no arrow on the card: ${cardTexts()}", cardTexts().none { "→" in it })
    }

    @Test
    fun `a saved route's peek shows its name with the total at its right and N Waypoints routed under it, opened by a real touch on its pin`() {
        setScreen()
        touchCentreOf(glyphTag(ROUTE_TRIP.id))
        val unit = viewModel.uiState.value.distanceUnit

        assertTrue("the card peeks", !plotting.uiState.value.expanded)
        assertEquals("Creek to Ridge", text(ROUTE_CARD_NAME_TAG))
        assertEquals(formatDistanceMeters(legMeters(0), unit), text(ROUTE_CARD_TOTAL_TAG))
        assertAtRightOf("the total", bounds(ROUTE_CARD_NAME_TAG), bounds(ROUTE_CARD_TOTAL_TAG))
        assertEquals("2 Waypoints routed", text(ROUTE_CARD_SAVED_COUNT_TAG))
        assertTrue("the routed count is under the name", bounds(ROUTE_CARD_SAVED_COUNT_TAG).top >= bounds(ROUTE_CARD_NAME_TAG).bottom)
        assertTrue("no arrow on the card: ${cardTexts()}", cardTexts().none { "→" in it })
        assertEquals("the peek's texts", listOf("Creek to Ridge", formatDistanceMeters(legMeters(0), unit), "2 Waypoints routed", "Directions"), cardTexts())
    }

    @Test
    fun `a saved route's expanded list has the same rows, opened by a real touch on its pin`() {
        setScreen()
        touchCentreOf(glyphTag(ROUTE_TRIP.id))
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)

        assertEquals("Creek to Ridge", text(routeCardLegNameTag(0)))
        assertEquals("2 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertTrue("no elevation for a saved route (step 3b): ${cardTexts()}", cardTexts().none { it.startsWith("Elevation") })
    }

    @Test
    fun `the seam - an elevation handed to the card shows under its leg and under the routed total, in the Units setting`() {
        val content = RouteCardContent.Plotting(
            stops = listOf(CREEK.stop(), RIDGE.stop(), CREEK.stop()),
            legs = listOf(straight(CREEK.stop().position, RIDGE.stop().position), straight(RIDGE.stop().position, CREEK.stop().position)),
            canSave = true,
            legElevations = listOf(RouteElevation(73.152, 97.536, 24.384), null),
            totalElevation = RouteElevation(-30.48, 30.48, 60.96),
        )
        var unit by mutableStateOf(DistanceUnit.MILES)
        composeRule.setContent {
            MaterialTheme {
                RouteCard(content, expanded = true, distanceUnit = unit, callbacks = RouteCardCallbacks(onToggleExpanded = {}), expandedMaxHeight = 400.dp)
            }
        }
        composeRule.waitForIdle()

        assertEquals("Elevation: +240 ft (↑ 320 ↓ 80)", text(routeCardLegElevationTag(0)))
        assertTrue("under its leg's name", bounds(routeCardLegElevationTag(0)).top >= bounds(routeCardLegNameTag(0)).bottom)
        assertTrue("a leg with no elevation shows none", !exists(routeCardLegElevationTag(1)))
        assertEquals("Elevation: -100 ft (↑ 100 ↓ 200)", text(ROUTE_CARD_ROUTED_ELEVATION_TAG))
        assertTrue("under the routed count", bounds(ROUTE_CARD_ROUTED_ELEVATION_TAG).top >= bounds(ROUTE_CARD_ROUTED_COUNT_TAG).bottom)

        unit = DistanceUnit.KILOMETERS
        composeRule.waitForIdle()
        assertEquals("Elevation: +73 m (↑ 98 ↓ 24)", text(routeCardLegElevationTag(0)))
        assertEquals("Elevation: -30 m (↑ 30 ↓ 61)", text(ROUTE_CARD_ROUTED_ELEVATION_TAG))
    }
}
