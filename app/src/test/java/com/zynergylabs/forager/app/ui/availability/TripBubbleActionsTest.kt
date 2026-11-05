package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.GroundHeightSource
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.route.RoutePlotDraft
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.LocalGroundHeightSource
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-05, A1 (RECORD -2140, the owner: "The trip icon should show a navigate option, along with elevation
 * data. Remove the directions from the bubble add Navigate, Plot Route Details. Remove the long/lat coordinates from the
 * Trip icon, and keep the military style"): a trip's bubble on the Maps tab reads its name, its date, its MGRS line and the
 * ground's height where it is known, then Navigate · Plot route · Details, with no latitude/longitude and no Directions.
 *
 * Through the real [AvailabilityScreen], the real [AvailabilityViewModel] and a real [RoutePlottingViewModel], wired as
 * `MainActivity` wires them. The trip's flag is opened by a real touch on its glyph, and every action is touched at five
 * points across its own bounds ([ACROSS]; CLAUDE.md, "a finger is not a point"), the bubble opened afresh each time since
 * every action closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TripBubbleActionsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(listOf(SADDLE_GLYPH))
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private lateinit var viewModel: AvailabilityViewModel

    private fun setScreen(heights: GroundHeightSource = SpotGroundHeights(mapOf(SADDLE.location to GroundHeight.Known(412.4)))) {
        viewModel = mapLayersViewModel(plannedTrips = listOf(SADDLE))
        composeRule.setContent {
            val plot by plotting.uiState.collectAsState()
            CompositionLocalProvider(LocalGroundHeightSource provides heights) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = OneCellStore(),
                    routePlotting = RoutePlottingControls(plot, plotting),
                    navigation = AvailabilityScreenNavigation(
                        onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                        onNavigateToTrip = viewModel::onNavigateToTrip,
                        onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                    ),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openBubble() {
        composeRule.onNodeWithTag(glyphTag(SADDLE.id)).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
    }

    private fun inBubble(text: String) = composeRule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG)), useUnmergedTree = true)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun label(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    @Test
    fun `the bubble reads the trip's name, date, MGRS and elevation, in that order, with no coordinates and no Directions`() {
        setScreen()
        openBubble()

        val title = inBubble("Saddle loop").getUnclippedBoundsInRoot()
        val date = inBubble("Oct 3, 2026").getUnclippedBoundsInRoot()
        val grid = inBubble(gridOf(SADDLE.location)).getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).assertTextEquals("Elevation 412 m")
        val elevation = composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the date under the name: ${date.top} vs ${title.bottom}", date.top >= title.bottom)
        assertTrue("the MGRS line under the date: ${grid.top} vs ${date.bottom}", grid.top >= date.bottom)
        assertTrue("the elevation under the MGRS line: ${elevation.top} vs ${grid.bottom}", elevation.top >= grid.bottom)
        assertEquals("no latitude/longitude line", 0, composeRule.onAllNodes(hasText(SADDLE_COORDINATES) and hasAnyAncestor(hasTestTag(MAP_BUBBLE_TAG)), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("no Directions", 0, count(MAP_BUBBLE_DIRECTIONS_TAG))
    }

    @Test
    fun `its actions are Navigate, Plot route and Details, left to right, under the elevation`() {
        setScreen()
        openBubble()

        assertEquals("Navigate", label(MAP_BUBBLE_NAVIGATE_TAG))
        assertEquals("Plot route", label(MAP_BUBBLE_PLOT_ROUTE_TAG))
        assertEquals("Details", label(MAP_BUBBLE_DETAILS_TAG))
        val navigate = composeRule.onNodeWithTag(MAP_BUBBLE_NAVIGATE_TAG).getUnclippedBoundsInRoot()
        val plot = composeRule.onNodeWithTag(MAP_BUBBLE_PLOT_ROUTE_TAG).getUnclippedBoundsInRoot()
        val details = composeRule.onNodeWithTag(MAP_BUBBLE_DETAILS_TAG).getUnclippedBoundsInRoot()
        val elevation = composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Navigate first: ${navigate.right} vs ${plot.left}", navigate.right <= plot.left)
        assertTrue("then Plot route: ${plot.right} vs ${details.left}", plot.right <= details.left)
        assertEquals("one row at this font", navigate.top.value, details.top.value, 0.5f)
        assertTrue("under the lines: ${navigate.top} vs ${elevation.bottom}", navigate.top >= elevation.bottom)
    }

    @Test
    fun `outside every downloaded area the bubble has no elevation line, and asks only for the trip's own spot`() {
        val heights = SpotGroundHeights(emptyMap())
        setScreen(heights)
        openBubble()

        inBubble(gridOf(SADDLE.location)).assertIsDisplayed()
        assertEquals("no elevation line", 0, count(MAP_BUBBLE_ELEVATION_TAG))
        assertEquals(listOf(SADDLE.location), heights.asked)
    }

    @Test
    fun `in feet the trip's height reads as a whole number`() {
        val heights = SpotGroundHeights(mapOf(SADDLE.location to GroundHeight.Known(412.0)))
        viewModel = mapLayersViewModel(plannedTrips = listOf(SADDLE), unitSystemPreferences = ImperialUnits)
        composeRule.setContent {
            CompositionLocalProvider(LocalGroundHeightSource provides heights) {
                MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = OneCellStore())
            }
        }
        composeRule.waitForIdle()
        openBubble()

        composeRule.onNodeWithTag(MAP_BUBBLE_ELEVATION_TAG, useUnmergedTree = true).assertTextEquals("Elevation 1352 ft")
    }

    @Test
    fun `real touches across Navigate each start navigating to the trip as a trip, and the bubble closes`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Navigate"
            composeRule.runOnIdle { viewModel.onStopWaypointNavigation() }
            composeRule.waitForIdle()
            openBubble()

            composeRule.touchAcrossTag(MAP_BUBBLE_NAVIGATE_TAG, fx, fy)

            assertEquals(where, WaypointNavigation(SADDLE.id, resumesReturn = false, kind = NavigationTargetKind.TRIP), viewModel.uiState.value.waypointNavigation)
            assertEquals("$where: the bubble closes", 0, count(MAP_BUBBLE_TAG))
        }
    }

    @Test
    fun `real touches across Plot route each start plotting from the trip's spot under its name, and the bubble closes`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Plot route"
            composeRule.runOnIdle { plotting.onCancel() }
            composeRule.waitForIdle()
            openBubble()

            composeRule.touchAcrossTag(MAP_BUBBLE_PLOT_ROUTE_TAG, fx, fy)

            assertEquals(where, RoutePlotMode.Plotting(RoutePlotDraft(listOf(RouteStop("Saddle loop", SADDLE.location)))), plotting.uiState.value.mode)
            assertEquals("$where: the bubble closes", 0, count(MAP_BUBBLE_TAG))
            composeRule.onNodeWithTag(ROUTE_CARD_COUNT_TAG).assertIsDisplayed()
            assertNull("$where: nothing is navigated to", viewModel.uiState.value.waypointNavigation)
        }
    }

    @Test
    fun `real touches across Details each open the trip's card, and the bubble closes`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            val where = "a touch at ($fx, $fy) on Details"
            openBubble()

            composeRule.touchAcrossTag(MAP_BUBBLE_DETAILS_TAG, fx, fy)

            assertEquals("$where: the card opens", 1, composeRule.onAllNodesWithTag(TRIP_CARD_SHEET_TAG).fetchSemanticsNodes().size)
            composeRule.onNodeWithTag(TRIP_CARD_TITLE_TAG).assertTextEquals("Saddle loop")
            assertEquals("$where: the bubble closes", 0, count(MAP_BUBBLE_TAG))
            composeRule.pressBackOnSheetWindow()
            assertEquals("$where: Back closes the card", 0, composeRule.onAllNodesWithTag(TRIP_CARD_SHEET_TAG).fetchSemanticsNodes().size)
        }
    }

    private object ImperialUnits : com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository {
        override suspend fun getUnitSystem() = Result.success(com.zynergylabs.forager.app.domain.model.UnitSystem.IMPERIAL)
        override suspend fun setUnitSystem(system: com.zynergylabs.forager.app.domain.model.UnitSystem) = Result.success(Unit)
    }

    private companion object {
        // Literals, so this compiles against the base it was written before.
        const val TRIP_CARD_SHEET_TAG = "record-details-sheet"
        const val TRIP_CARD_TITLE_TAG = "record-details-title"
    }
}
