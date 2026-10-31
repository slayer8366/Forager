package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardLegElevationTag
import com.zynergylabs.forager.app.ui.route.routeCardLegNameTag
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
 * Dispatch 2026-10-30-08, Amendment 8 (RECORD -1896): the elevation lines while plotting, on the real Maps tab with real
 * touches. The router hands back trail legs with known heights, as `PlotRouteUseCase.leg` does from the trail data:
 * - Creek to Ridge: 100, 110, 104, 107. Net +7 m, 10 m up, 6 m down; the last 3 m are short of 4 and stay pending.
 * - Ridge to Falls: 107, 110, 112. Net +5 m, 5 m up.
 * - The whole route, banked across both legs: 104 to 110 banks 6 m, so 16 m up and 6 m down, net +12 m. Summing the legs
 *   would give 15 m up.
 * - Falls to Ridge has no heights at all; Ridge to Creek has only some; Creek to Falls is a straight line. None shows a line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteElevationLinesTest {

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

    private fun heightsFor(from: LatLng, to: LatLng): List<Double?>? = when (from to to) {
        CREEK.stop().position to RIDGE.stop().position -> listOf(100.0, 110.0, 104.0, 107.0)
        RIDGE.stop().position to falls.stop().position -> listOf(107.0, 110.0, 112.0)
        falls.stop().position to RIDGE.stop().position -> listOf(null, null, null)
        RIDGE.stop().position to CREEK.stop().position -> listOf(112.0, null, 100.0)
        else -> null // a straight line
    }

    /** A trail leg whose trail part carries [heights], one per point between the two joins. */
    private fun trail(from: LatLng, to: LatLng, heights: List<Double?>): RouteLeg.Trail {
        val n = heights.size
        val points = (1..n).map { k -> LatLng(from.lat + (to.lat - from.lat) * k / (n + 1), from.lng + (to.lng - from.lng) * k / (n + 1)) }
        return RouteLeg.Trail(listOf(from) + points + listOf(to), GeoDistance.metersBetween(from, to), trailFrom = 1, trailTo = n, trailHeightsMetres = heights)
    }

    private val plotting = RoutePlottingViewModel(
        // Suspends once before it answers, as the app's router does (RouteCardLayoutTest's router).
        routeLeg = { a, b -> yield(); heightsFor(a, b)?.let { trail(a, b, it) } ?: straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen(units: UnitSystem): DistanceUnit {
        val vm = mapLayersViewModel(
            unitSystemPreferences = object : UnitSystemPreferenceRepository {
                override suspend fun getUnitSystem(): Result<UnitSystem> = Result.success(units)
                override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = Result.success(Unit)
            },
        )
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
        return vm.uiState.value.distanceUnit
    }

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    private fun text(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun cardTexts(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null } and hasAnyAncestor(hasTestTag(ROUTE_CARD_TAG)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    /** Creek's bubble, "Plot route", then Ridge and Falls, then the handle to expand the card. */
    private fun plotCreekRidgeFalls() {
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
    }

    @Test
    fun `imperial - each heighted leg and the whole route show their elevation, and a straight leg then takes the total away`() {
        assertEquals("the Units setting", DistanceUnit.MILES, setScreen(UnitSystem.IMPERIAL))
        plotCreekRidgeFalls()

        assertEquals("Creek to Ridge", text(routeCardLegNameTag(0)))
        assertEquals("Elevation: +23 ft (↑ 33 ↓ 20)", text(routeCardLegElevationTag(0)))
        assertEquals("Ridge to Falls", text(routeCardLegNameTag(1)))
        assertEquals("Elevation: +16 ft (↑ 16 ↓ 0)", text(routeCardLegElevationTag(1)))
        assertEquals("3 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertEquals("Elevation: +39 ft (↑ 52 ↓ 20)", text(ROUTE_CARD_ROUTED_ELEVATION_TAG))

        touchCentreOf(glyphTag(CREEK.id)) // Falls to Creek: a straight line

        assertEquals("Falls to Creek", text(routeCardLegNameTag(2)))
        assertTrue("no line for the straight leg", !exists(routeCardLegElevationTag(2)))
        assertEquals("the heighted legs keep theirs", "Elevation: +23 ft (↑ 33 ↓ 20)", text(routeCardLegElevationTag(0)))
        assertEquals("4 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertTrue("no total once a leg has no heights", !exists(ROUTE_CARD_ROUTED_ELEVATION_TAG))
    }

    @Test
    fun `metric - the same legs in metres`() {
        assertEquals("the Units setting", DistanceUnit.KILOMETERS, setScreen(UnitSystem.METRIC))
        plotCreekRidgeFalls()

        assertEquals("Elevation: +7 m (↑ 10 ↓ 6)", text(routeCardLegElevationTag(0)))
        assertEquals("Elevation: +5 m (↑ 5 ↓ 0)", text(routeCardLegElevationTag(1)))
        assertEquals("Elevation: +12 m (↑ 16 ↓ 6)", text(ROUTE_CARD_ROUTED_ELEVATION_TAG))
    }

    @Test
    fun `a leg with no heights and a leg with only some show no line, and never +0`() {
        setScreen(UnitSystem.METRIC)
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id)) // Falls to Ridge: no heights
        touchCentreOf(glyphTag(CREEK.id)) // Ridge to Creek: only some
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)

        assertEquals("Falls to Ridge", text(routeCardLegNameTag(0)))
        assertEquals("Ridge to Creek", text(routeCardLegNameTag(1)))
        assertEquals("3 Waypoints routed", text(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertTrue("no line for the leg with no heights", !exists(routeCardLegElevationTag(0)))
        assertTrue("no line for the leg with only some", !exists(routeCardLegElevationTag(1)))
        assertTrue("no total", !exists(ROUTE_CARD_ROUTED_ELEVATION_TAG))
        assertTrue("no elevation text and no +0 anywhere on the card: ${cardTexts()}", cardTexts().none { it.startsWith("Elevation") || "+0" in it })
    }
}
