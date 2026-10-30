package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back while plotting, with something else open over the Maps tab (dispatch 2026-10-30-08, Amendment 3; the first stop, (h),
 * "Back"): what was opened last closes first, and fullscreen waits for the route (Amendment 1, Q3: "cancel first"). Back goes
 * through the real activity dispatcher; what opens each layer is a real touch sampled across it. The fan's case is in
 * [RoutePlottingFanBackTest], which needs the fan's stub map.
 *
 * A waypoint's glyph cannot open a bubble while plotting (`routePlottingTakesTap` adds it as a stop), so the bubble here is a
 * planned trip's with no route, which plotting does not take.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RoutePlottingBackOrderTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val plainTrip = PlannedTrip("trip-plain", "Saddle loop", LatLng(45.56, -122.66), null)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, plainTrip.id, 60.dp, 420.dp, plainTrip.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(plainTrip))
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

    /** Five real touches inside a round control's circle: 0.2 and 0.8 of each side, and the centre (LandscapeLClusterTest). */
    private val ROUND_ACROSS = listOf(0.2f to 0.2f, 0.8f to 0.2f, 0.5f to 0.5f, 0.2f to 0.8f, 0.8f to 0.8f)

    private fun touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun mode() = plotting.uiState.value.mode

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun drawerShown(): Boolean = runCatching { composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).assertIsDisplayed() }.isSuccess

    private fun shown(contentDescription: String): Boolean =
        runCatching { composeRule.onNodeWithContentDescription(contentDescription).assertIsDisplayed() }.isSuccess

    /** Plotting from Creek with Ridge added by a real touch on its glyph; the card peeks. */
    private fun plotCreekToRidge() {
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()
        touchAcross(composeRule.onNodeWithTag(glyphTag(RIDGE.id)), 0.5f, 0.5f)
        assertEquals("plotting Creek to Ridge", listOf(CREEK.stop(), RIDGE.stop()), (mode() as RoutePlotMode.Plotting).draft.stops)
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).assertIsDisplayed()
        assertFalse("the card peeks", plotting.uiState.value.expanded)
    }

    @Test
    fun `a bubble opened while plotting closes on the first Back, and the second Back cancels plotting`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "the bubble opened by a touch at ($fx, $fy) on the trip's pin"
            plotCreekToRidge()
            touchAcross(composeRule.onNodeWithTag(glyphTag(plainTrip.id)), fx, fy)
            composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
            assertTrue("$where: plotting did not take the pin", mode() is RoutePlotMode.Plotting)

            back()
            assertEquals("$where, first Back: the bubble closed", 0, count(MAP_BUBBLE_TAG))
            assertEquals("$where, first Back: plotting stays", listOf(CREEK.stop(), RIDGE.stop()), (mode() as? RoutePlotMode.Plotting)?.draft?.stops)

            back()
            assertEquals("$where, second Back: plotting cancelled", RoutePlotMode.Off, mode())
            assertEquals("$where, second Back: no card", 0, count(ROUTE_CARD_TAG))
        }
    }

    @Test
    fun `the Tools drawer opened while plotting closes on the first Back, and the second Back cancels plotting`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "the drawer opened by a touch at ($fx, $fy) on Tools"
            plotCreekToRidge()
            touchAcross(composeRule.onNodeWithText("Tools"), fx, fy)
            assertTrue("$where: the drawer opened", drawerShown())

            back()
            assertFalse("$where, first Back: the drawer closed", drawerShown())
            assertTrue("$where, first Back: plotting stays", mode() is RoutePlotMode.Plotting)

            back()
            assertEquals("$where, second Back: plotting cancelled", RoutePlotMode.Off, mode())
        }
    }

    @Test
    fun `in fullscreen, the first Back cancels plotting and fullscreen stays, and the next Back leaves fullscreen`() {
        setScreen()
        // The Fullscreen control is a round icon button whose touches are clipped to its circle, so its samples sit at 0.2 and
        // 0.8 of its bounds, as LandscapeLClusterTest.fractions() samples the same control; a 0.1 corner falls outside the circle
        // (Amendment 4, the owner: "Apply both (Recommended)").
        ROUND_ACROSS.forEach { (fx, fy) ->
            val where = "fullscreen entered by a touch at ($fx, $fy)"
            // Fullscreen first, so the card is not yet over the map when its control is touched.
            touchAcross(composeRule.onNodeWithContentDescription("Fullscreen"), fx, fy)
            assertTrue("$where: fullscreen is on", shown("Exit fullscreen"))
            plotCreekToRidge()

            back()
            assertEquals("$where, first Back: plotting cancelled", RoutePlotMode.Off, mode())
            assertTrue("$where, first Back: fullscreen stays on", shown("Exit fullscreen"))

            back()
            assertTrue("$where, second Back: fullscreen is off", shown("Fullscreen"))
            assertFalse("$where, second Back: no exit control", shown("Exit fullscreen"))
        }
    }
}
