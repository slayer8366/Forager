package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.NAVIGATE_UNAVAILABLE_REASON
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_DIRECTIONS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_REASON_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_NAVIGATE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SIDE_PADDING
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
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-05, Amendment 4 (RECORD -2187), on the real compact Maps tab at the S22's portrait window (`w384dp`), with real
 * text measurement (native graphics). Found on the S22 (build 4914): with a stop's waypoint deleted, the reason sat in the button row
 * and wrapped into a column about 36 dp wide, breaking words mid-word. The owner: "Own line under buttons": the reason takes the
 * card's full width on its own line under Navigate and Directions, which keep their places in the row. The words stay as written
 * (Amendment 2: "As I wrote it"). Checked at the default text size and at the largest, 2.0.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardNavigateReasonLineTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 200.dp, 300.dp, ROUTE_TRIP.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )
    private var fontScaleSeen: Float? = null

    /** The route's first stop is Creek, whose waypoint is deleted: only Ridge is still on the phone. */
    private fun showWithFirstStopDeleted() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            fontScaleSeen = LocalDensity.current.fontScale
            val state by plotting.uiState.collectAsState()
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                waypoints = listOf(RIDGE),
                routePlotting = RoutePlottingControls(state, plotting),
                navigation = AvailabilityScreenNavigation(
                    onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                    onNavigateToTrip = viewModel::onNavigateToTrip,
                    onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                ),
            )
        }
        settle()
        composeRule.onNodeWithTag(glyphTag(ROUTE_TRIP.id)).performTouchInput { click(center) }
        settle()
        assertEquals("the route opened", ROUTE_TRIP.id, (plotting.uiState.value.mode as? RoutePlotMode.Showing)?.trip?.id)
        composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_TAG).assertIsNotEnabled()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    @Test
    fun `at the default text size the reason has its own line under Navigate and Directions, at the card's full width, no word broken`() {
        showWithFirstStopDeleted()
        assertEquals("this case runs at the default text size", 1f, fontScaleSeen ?: 0f, 0.001f)
        checkOwnLine("fontScale=1.0")
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `at the largest text size, 2_0, the reason has its own line under Navigate and Directions, at the card's full width, no word broken`() {
        showWithFirstStopDeleted()
        assertEquals("this case runs at font scale 2.0", 2f, fontScaleSeen ?: 0f, 0.001f)
        checkOwnLine("fontScale=2.0")
    }

    private fun checkOwnLine(case: String) {
        val node = composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_REASON_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val text = layout.layoutInput.text.text
        val lines = (0 until layout.lineCount).map { text.substring(layout.getLineStart(it), layout.getLineEnd(it)) }
        val reason = composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_REASON_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val navigate = composeRule.onNodeWithTag(ROUTE_CARD_NAVIGATE_TAG).getUnclippedBoundsInRoot()
        val directions = composeRule.onNodeWithTag(ROUTE_CARD_DIRECTIONS_TAG).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithTag(ROUTE_CARD_TAG).getUnclippedBoundsInRoot()
        println("MEASURED $case: reason ${reason.left}..${reason.right} (${reason.width}) top ${reason.top}; card ${card.left}..${card.right}; Navigate ${navigate.left}..${navigate.right} bottom ${navigate.bottom}; Directions ${directions.left}..${directions.right} bottom ${directions.bottom}; ${layout.lineCount} line(s): $lines")

        // The owner's words, unchanged, and every character laid out.
        assertEquals(NAVIGATE_UNAVAILABLE_REASON, text)
        assertEquals("This stop's waypoint was moved or deleted", text)
        assertFalse("$case: nothing cut off", layout.hasVisualOverflow)
        assertEquals("$case: every character laid out", text.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))

        // Its own line, under the buttons, which keep their places: one row, Navigate left of Directions.
        assertEquals("$case: Navigate and Directions in one row", navigate.top.value, directions.top.value, 0.5f)
        assertTrue("$case: Navigate left of Directions: ${navigate.right} vs ${directions.left}", navigate.right <= directions.left)
        assertTrue("$case: the reason under the buttons: its top ${reason.top} vs their bottoms ${navigate.bottom}, ${directions.bottom}", reason.top >= navigate.bottom && reason.top >= directions.bottom)

        // The card's full width: from its side padding to its side padding.
        assertEquals("$case: the reason starts at the card's side padding", (card.left + ROUTE_CARD_SIDE_PADDING).value, reason.left.value, 1f)
        assertEquals("$case: the reason ends at the card's side padding", (card.right - ROUTE_CARD_SIDE_PADDING).value, reason.right.value, 1f)

        // No word broken: every line but the last ends at a space.
        (0 until layout.lineCount - 1).forEach { i ->
            val end = layout.getLineEnd(i)
            assertTrue("$case: line ${i + 1} of ${layout.lineCount} ends mid-word: $lines", text[end - 1] == ' ')
        }
    }
}
