package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.route.RoutePlotDraft
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_PLOT_ROUTE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A waypoint bubble's three actions, Navigate, Plot route and Details, at font scale 2.0 on a narrow phone (dispatch
 * 2026-10-30-08, Amendment 3): since that dispatch they sit in a FlowRow, which wraps rather than squeezing a third action
 * out. Each is displayed, inside the bubble and the window, clear of the others, and "Plot route" answers real touches across
 * it. Navigate is offered because the screen is handed a waypoint navigation entry point, as MainActivity hands it one.
 *
 * Native graphics (Amendment 4), as LandscapeLargeFontTest: without it Robolectric measures text at a few dp wide, the three
 * actions fit on one line at any font scale, and the FlowRow never wraps, which is why a `maxLines = 1` revert passed both tests
 * (run 41135424879). The first test now also checks the row did wrap, so it is about wrapping and not only about fitting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoutePlottingBubbleFontScaleTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng))))
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private val navigated = mutableListOf<String>()

    private fun setScreen() {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                navigation = AvailabilityScreenNavigation(onNavigateToWaypoint = { navigated += it }),
            )
        }
        composeRule.waitForIdle()
    }

    private fun touchAcross(node: SemanticsNodeInteraction, fx: Float, fy: Float) {
        node.performTouchInput { click(Offset(width * fx, height * fy)) }
        composeRule.waitForIdle()
    }

    private fun openCreekBubble() {
        touchAcross(composeRule.onNodeWithTag(glyphTag(CREEK.id)), 0.5f, 0.5f)
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun DpRect.contains(inner: DpRect): Boolean {
        val slack = 0.5.dp
        return inner.left >= left - slack && inner.top >= top - slack && inner.right <= right + slack && inner.bottom <= bottom + slack
    }

    private fun DpRect.overlaps(other: DpRect): Boolean {
        val slack = 0.5.dp
        return left < other.right - slack && other.left < right - slack && top < other.bottom - slack && other.top < bottom - slack
    }

    private val actions = listOf("Navigate" to MAP_BUBBLE_NAVIGATE_TAG, "Plot route" to MAP_BUBBLE_PLOT_ROUTE_TAG, "Details" to MAP_BUBBLE_DETAILS_TAG)

    @Test
    fun `at font scale 2,0 the three actions are displayed inside the bubble and the window, and none overlaps another`() {
        setScreen()
        assertEquals("the font scale this test is about", 2.0f, composeRule.density.fontScale, 0.01f)
        openCreekBubble()
        val bubble = bounds(MAP_BUBBLE_TAG)
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        val placed = actions.map { (label, tag) ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            val at = bounds(tag)
            assertTrue("$label $at is inside the bubble $bubble", bubble.contains(at))
            assertTrue("$label $at is inside the window $window", window.contains(at))
            label to at
        }
        assertTrue(
            "at font scale 2.0 the three do not fit on one line, so the row wrapped: ${placed.map { (l, at) -> "$l top ${at.top}" }}",
            placed.map { it.second.top.value.toInt() }.distinct().size > 1,
        )
        placed.forEachIndexed { i, (a, aAt) ->
            placed.drop(i + 1).forEach { (b, bAt) -> assertTrue("$a $aAt and $b $bAt do not overlap", !aAt.overlaps(bAt)) }
        }
    }

    @Test
    fun `at font scale 2,0 real touches across Plot route each start plotting from the waypoint`() {
        setScreen()
        ACROSS.forEach { (fx, fy) ->
            composeRule.runOnUiThread { plotting.onCancel() }
            composeRule.waitForIdle()
            openCreekBubble()
            touchAcross(composeRule.onNodeWithTag(MAP_BUBBLE_PLOT_ROUTE_TAG), fx, fy)

            assertEquals("a touch at ($fx, $fy) on Plot route", RoutePlotMode.Plotting(RoutePlotDraft(listOf(CREEK.stop()))), plotting.uiState.value.mode)
            assertEquals("a touch at ($fx, $fy): the bubble closed", 0, composeRule.onAllNodesWithTag(MAP_BUBBLE_TAG).fetchSemanticsNodes().size)
            assertEquals("a touch at ($fx, $fy): Navigate was not the one touched", emptyList<String>(), navigated)
        }
    }
}
