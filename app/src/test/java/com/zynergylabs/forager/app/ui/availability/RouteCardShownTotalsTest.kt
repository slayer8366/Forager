package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.RouteCard
import com.zynergylabs.forager.app.ui.route.RouteCardCallbacks
import com.zynergylabs.forager.app.ui.route.RouteCardContent
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.routeCardLegDistanceTag
import com.zynergylabs.forager.app.ui.route.routeCardLegElevationTag
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-30-08, Amendment 10 (RECORD -1904, the owner: "Yes, add up what's shown (Recommended)"), on the real Maps tab
 * with real touches: every total on the card is the sum of the rows' shown, rounded figures, in the routed total row and the
 * peek. The phone's distance case (docs/audits/2026-10-31-s22-phone-checks.md): 0.4 + 0.4 + 0.4 mi under a total of 1.3 mi.
 *
 * The router hands back three trail legs of 0.44 mi, each climbing 4.6 m: in feet each shows 15, and in metres 5, and the
 * metres summed (13.8) would show 14.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardShownTotalsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val falls = Waypoint("wp-falls", 45.346, -122.654, null, "Falls", "", 3_000L)
    private val pond = Waypoint("wp-pond", 45.356, -122.664, null, "Pond", "", 4_000L)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 80.dp, 200.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 80.dp, 250.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, falls.id, 140.dp, 200.dp, LatLng(falls.lat, falls.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, pond.id, 140.dp, 250.dp, LatLng(pond.lat, pond.lng)),
        ),
    )

    private fun trail(from: LatLng, to: LatLng) = RouteLeg.Trail(listOf(from, from, to, to), 0.44 * 1609.344, trailFrom = 1, trailTo = 2, trailHeightsMetres = listOf(100.0, 104.6))

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); trail(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen(units: UnitSystem) {
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
                waypoints = listOf(CREEK, RIDGE, falls, pond),
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

    private fun plotFour() {
        touchCentreOf(glyphTag(CREEK.id))
        touchCentreOf(MAP_BUBBLE_PLOT_ROUTE_TAG)
        touchCentreOf(glyphTag(RIDGE.id))
        touchCentreOf(glyphTag(falls.id))
        touchCentreOf(glyphTag(pond.id))
    }

    @Test
    fun `imperial - the peek and the routed row both total 1_2 mi under rows of 0_4 mi`() {
        setScreen(UnitSystem.IMPERIAL)
        plotFour()
        assertEquals("the peek's total", "1.2 mi", text(ROUTE_CARD_ROUTED_TOTAL_TAG))

        touchCentreOf(ROUTE_CARD_HANDLE_TAG)
        (0..2).forEach { assertEquals("0.4 mi", text(routeCardLegDistanceTag(it))) }
        assertEquals("the routed total", "1.2 mi", text(ROUTE_CARD_ROUTED_TOTAL_TAG))
    }

    @Test
    fun `metric - the elevation total adds up the shown rows`() {
        setScreen(UnitSystem.METRIC)
        plotFour()
        touchCentreOf(ROUTE_CARD_HANDLE_TAG)

        (0..2).forEach { assertEquals("Elevation: +5 m (↑ 5 ↓ 0)", text(routeCardLegElevationTag(it))) }
        assertEquals("Elevation: +15 m (↑ 15 ↓ 0)", text(ROUTE_CARD_ROUTED_ELEVATION_TAG))
    }

    @Test
    fun `a saved route's peek totals its rows too`() {
        val legs = listOf(trail(CREEK.stop().position, RIDGE.stop().position), trail(RIDGE.stop().position, CREEK.stop().position), trail(CREEK.stop().position, RIDGE.stop().position))
        val content = RouteCardContent.Saved("Loop", listOf(CREEK.stop(), RIDGE.stop(), CREEK.stop(), RIDGE.stop()), legs)
        composeRule.setContent {
            MaterialTheme {
                RouteCard(content, expanded = false, distanceUnit = DistanceUnit.MILES, callbacks = RouteCardCallbacks(onToggleExpanded = {}), expandedMaxHeight = 400.dp)
            }
        }
        composeRule.waitForIdle()
        assertEquals("1.2 mi", text(ROUTE_CARD_ROUTED_TOTAL_TAG))
    }
}
