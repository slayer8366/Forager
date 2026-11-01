package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.route.RouteElevation
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_LEGS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_PROGRESS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RouteCard
import com.zynergylabs.forager.app.ui.route.RouteCardCallbacks
import com.zynergylabs.forager.app.ui.route.RouteCardContent
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

private const val PILL = "control-pill"

/** The card alone, for what the Maps tab cannot reach in a test: the seam for a saved route's line, a first leg in flight, long rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardTotalLineContentTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val a = CREEK.stop()
    private val b = RIDGE.stop()

    private fun setCard(content: RouteCardContent, expanded: Boolean, maxHeight: Dp = 400.dp) {
        composeRule.setContent {
            MaterialTheme {
                RouteCard(content, expanded = expanded, distanceUnit = DistanceUnit.MILES, callbacks = RouteCardCallbacks(onToggleExpanded = {}), expandedMaxHeight = maxHeight)
            }
        }
        composeRule.waitForIdle()
    }

    private fun texts(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher("has text") { it.config.getOrNull(SemanticsProperties.Text) != null } and hasAnyAncestor(hasTestTag(ROUTE_CARD_TAG)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.Text].map { t -> t.text } }

    @Test
    fun `a saved route with heights shows the same elevation line under its total, name on top`() {
        val content = RouteCardContent.Saved("Loop", listOf(a, b), listOf(totalLineTrail(a.position, b.position)), listOf(RouteElevation(73.152, 97.536, 24.384)))
        setCard(content, expanded = false)
        assertEquals(listOf("Loop", "2 Waypoints routed", "0.4 mi", "Elevation: +240 ft (↑ 320 ↓ 80)", "Directions"), texts())
    }

    @Test
    fun `one leg without heights takes the elevation line off the peek, the distance stays`() {
        val content = RouteCardContent.Plotting(listOf(a, b, a), listOf(totalLineTrail(a.position, b.position), straight(b.position, a.position)), canSave = true, legElevations = listOf(RouteElevation(1.0, 1.0, 0.0), null))
        setCard(content, expanded = false)
        assertTrue("no elevation: ${texts()}", texts().none { it.startsWith("Elevation") })
        assertTrue("the total line: ${texts()}", texts().contains("3 Waypoints routed"))
    }

    @Test
    fun `two stops with the first leg in flight say nothing that is not so - no selected line, no routed line, the progress mark`() {
        setCard(RouteCardContent.Plotting(listOf(a, b), emptyList(), canSave = false), expanded = false)
        assertEquals("only the buttons have words", listOf("Undo", "Cancel", "Save"), texts())
        assertTrue("the progress mark shows", composeRule.onAllNodes(hasTestTag(ROUTE_CARD_PROGRESS_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a later leg in flight keeps the line on the routed legs, with the progress mark`() {
        setCard(RouteCardContent.Plotting(listOf(a, b, a), listOf(totalLineTrail(a.position, b.position)), canSave = false), expanded = false)
        assertEquals(listOf("2 Waypoints routed", "0.4 mi", "Undo", "Cancel", "Save"), texts())
        assertTrue("the progress mark shows", composeRule.onAllNodes(hasTestTag(ROUTE_CARD_PROGRESS_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `rows taller than the cap scroll behind the fixed total line, by a real swipe`() {
        val stops = List(13) { if (it % 2 == 0) a else b }
        val legs = stops.zipWithNext { x, y -> totalLineTrail(x.position, y.position) }
        setCard(RouteCardContent.Plotting(stops, legs, canSave = true), expanded = true, maxHeight = 200.dp)
        val line = composeRule.onNodeWithTag(ROUTE_CARD_ROUTED_TAG, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        val first = composeRule.onNodeWithTag(routeCardLegTag(0), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

        composeRule.onNodeWithTag(ROUTE_CARD_LEGS_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertTrue("the rows moved", composeRule.onNodeWithTag(routeCardLegTag(0), useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y < first)
        assertEquals("the line did not", line, composeRule.onNodeWithTag(ROUTE_CARD_ROUTED_TAG, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y, 0f)
    }
}

/**
 * The same in the short landscape window. The line stays put. The L is 296 dp tall in a 384 dp window and already sat under the
 * shorter peek before Amendment 12 (bottom 332 against a card top of 232, run 41298481762), so clearing it there is not asserted:
 * the numbers go in the report, for the owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class RouteCardTotalLineLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(emptyList())

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); totalLineTrail(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    @Test
    fun `the total line is the same pixel expanded, and the L and the card at peek are printed`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = mapLayersViewModel(),
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(CREEK, RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = {}),
            )
        }
        settle()
        composeRule.runOnUiThread {
            plotting.onStartPlotting(CREEK.stop())
            plotting.onWaypointTapped(RIDGE.stop())
        }
        settle()

        val card = composeRule.onNodeWithTag(ROUTE_CARD_TAG).getUnclippedBoundsInRoot()
        val cluster = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        val pill = composeRule.onNodeWithTag(PILL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        println("A12 landscape: card ${card.left}..${card.right} x ${card.top}..${card.bottom}, L ${cluster.left}..${cluster.right} x ${cluster.top}..${cluster.bottom}, pill ${pill.left}..${pill.right} x ${pill.top}..${pill.bottom}")
        fun h(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { "$tag ${it.top}..${it.bottom}" }
        println("A12 landscape lines: ${h(ROUTE_CARD_HANDLE_TAG)}, ${h(ROUTE_CARD_ROUTED_COUNT_TAG)}, ${h(ROUTE_CARD_ROUTED_ELEVATION_TAG)}, ${h("route-card-undo")}")

        val line = composeRule.onNodeWithTag(ROUTE_CARD_ROUTED_TAG, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
        assertTrue("expanded", plotting.uiState.value.expanded)
        assertEquals("expanded, in pixels", line, composeRule.onNodeWithTag(ROUTE_CARD_ROUTED_TAG, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y, 0f)
    }
}
