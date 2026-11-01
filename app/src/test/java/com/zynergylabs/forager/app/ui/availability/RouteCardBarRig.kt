package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.map.MAP_TYPE_PANEL_SCRIM_TAG
import com.zynergylabs.forager.app.ui.map.MAP_TYPE_PANEL_TAG
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal const val BAR_RECORD = "control-pill-record"
internal const val BAR_RETURN = "control-pill-return-to-vehicle"
internal const val BAR_PILL = "control-pill"
internal const val BAR_ADD = "Plan a trip or log a find here"
internal const val BAR_RESTORE = "Show map controls"
internal const val BAR_MINIMIZE_TAG = "map-icon-bar-minimize-handle"

/**
 * Five points across the upright pill's squares: its round ends are clipped to the shape, so a touch 10% in from a corner of the
 * square is outside it and lands on the column (Amendment 11's report). These stay inside what the pill draws.
 */
internal val ACROSS_ROUND = listOf(0.5f to 0.2f, 0.2f to 0.5f, 0.5f to 0.5f, 0.8f to 0.5f, 0.5f to 0.8f)

/**
 * The real Maps tab with a route card over it, recording, six waypoints to plot, for the rule of Amendments 14 and 15 (RECORD
 * -1928, -1929): the card fits beside the icon bar, and the bar, its height and its side never change for the card.
 */
internal class RouteCardBarRig(private val composeRule: RouteRule) {
    private val stops = listOf(
        CREEK,
        RIDGE,
        Waypoint("wp-falls", 45.346, -122.654, null, "Falls", "", 3_000L),
        Waypoint("wp-pond", 45.356, -122.664, null, "Pond", "", 4_000L),
        Waypoint("wp-bluff", 45.366, -122.674, null, "Bluff", "", 5_000L),
        Waypoint("wp-spring", 45.376, -122.684, null, "Spring", "", 6_000L),
    )

    val map = RouteMapSlot(emptyList())

    /** A trail leg with heights, so each row carries its elevation line and the card is as tall as on the phone. */
    private fun trail(from: LatLng, to: LatLng): RouteLeg.Trail {
        val heights = listOf(100.0, 110.0, 104.0)
        val points = (1..heights.size).map { k -> LatLng(from.lat + (to.lat - from.lat) * k / 4, from.lng + (to.lng - from.lng) * k / 4) }
        return RouteLeg.Trail(listOf(from) + points + listOf(to), GeoDistance.metersBetween(from, to), trailFrom = 1, trailTo = heights.size, trailHeightsMetres = heights)
    }

    val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> yield(); trail(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    private var stopTouches = 0
    private var returnTouches = 0
    private var plotted = 0

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.32, lng = -122.63, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val origin = Waypoint(id = "origin", lat = 45.33, lng = -122.63, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1")

    fun setScreen(withLegend: Boolean = false, reduceMotion: Boolean = false, navigating: Boolean = false) {
        val store = OneCellStore()
        val viewModel = if (withLegend) mapLayersViewModel(store = store) else mapLayersViewModel()
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = store,
                    waypoints = stops,
                    routePlotting = RoutePlottingControls(state, plotting),
                    uiStateTransform = { if (navigating) it.copy(liveFix = fix) else it },
                    navigation = if (navigating) {
                        AvailabilityScreenNavigation(isReturning = true, onToggleReturning = { returnTouches++ }, navigationTarget = origin)
                    } else {
                        AvailabilityScreenNavigation(onToggleReturning = { returnTouches++ })
                    },
                    recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = { stopTouches++ }),
                )
            }
        }
        settle()
    }

    fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    fun described(description: String): DpRect = composeRule.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    fun describedCount(description: String) = composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size

    fun screen(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /** A long press on the bar's handle and a drag by [by], then the release. */
    fun dragBar(by: Offset) {
        val handle = bounds(BAR_MINIMIZE_TAG)
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

    fun dp(value: Dp): Float = with(composeRule.density) { value.toPx() }

    fun dragBarDown() = dragBar(Offset(0f, dp(900.dp)))

    fun dragBarAcross(rightward: Boolean) = dragBar(Offset(dp(if (rightward) 300.dp else (-300).dp), 0f))

    /** Plots up to [n] stops. The stops are the claim's setting, not its subject; the taps that plot are `RoutePlottingScreenTest`'s. */
    fun plotTo(n: Int) {
        composeRule.runOnUiThread {
            while (plotted < n) {
                if (plotted == 0) plotting.onStartPlotting(stops[0].stop()) else plotting.onWaypointTapped(stops[plotted].stop())
                plotted++
            }
        }
        settle()
    }

    fun touchHandle() {
        composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        settle()
    }

    fun setExpanded(expanded: Boolean) {
        if (plotting.uiState.value.expanded != expanded) touchHandle()
        assertEquals("precondition: the card's state", expanded, plotting.uiState.value.expanded)
    }

    fun touchesReachStopAndReturn(where: String, samples: List<Pair<Float, Float>>) {
        listOf(BAR_RECORD, BAR_RETURN).forEach { tag ->
            samples.forEach { (fx, fy) ->
                val stops = stopTouches
                val returns = returnTouches
                composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fx, height * fy)) }
                settle()
                assertEquals("$where: a touch at ($fx, $fy) on $tag, stop", if (tag == BAR_RECORD) stops + 1 else stops, stopTouches)
                assertEquals("$where: a touch at ($fx, $fy) on $tag, return", if (tag == BAR_RETURN) returns + 1 else returns, returnTouches)
            }
        }
    }

    fun touchesReachLayersAndAdd(where: String, samples: List<Pair<Float, Float>>) {
        samples.forEach { (fx, fy) ->
            composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("$where: a touch at ($fx, $fy) on Layers opens its panel", 1, count(MAP_TYPE_PANEL_TAG))
            composeRule.onNodeWithTag(MAP_TYPE_PANEL_SCRIM_TAG).performTouchInput { click(Offset(width * 0.5f, 4f)) }
            settle()
            assertEquals("$where: the panel closed", 0, count(MAP_TYPE_PANEL_TAG))
            composeRule.onNodeWithContentDescription(BAR_ADD).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("$where: a touch at ($fx, $fy) on + opens its menu", 1, count(ADD_ACTION_TILE_TAG))
            composeRule.onNodeWithTag(ADD_ACTION_TILE_SCRIM_TAG).performTouchInput { click(Offset(width * 0.5f, 4f)) }
            settle()
            assertEquals("$where: the menu closed", 0, count(ADD_ACTION_TILE_TAG))
        }
    }

    /** With a route to save, each touch opens the save dialog; with one stop Save is off, and the touch opens nothing. */
    fun touchesAcrossSave(where: String, samples: List<Pair<Float, Float>>) {
        val canSave = plotted >= 2
        samples.forEach { (fx, fy) ->
            composeRule.onNodeWithTag(ROUTE_CARD_SAVE_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("$where: a touch at ($fx, $fy) on Save", canSave, plotting.uiState.value.saveDialogOpen)
            if (canSave) {
                composeRule.runOnUiThread { plotting.onDismissSaveDialog() }
                settle()
            }
        }
        assertFalse("$where: the save dialog is closed", plotting.uiState.value.saveDialogOpen)
    }

    /** The card's bounds meet neither the bar's nor the pill's, and the bar's are the same as [barBefore]. */
    fun assertBarUntouched(where: String, barBefore: DpRect) {
        assertEquals("$where: the bar shows, once", 1, count(MAP_ICON_CLUSTER_TAG))
        val bar = bounds(MAP_ICON_CLUSTER_TAG)
        assertSame("$where: the bar", barBefore, bar)
        val card = bounds(ROUTE_CARD_TAG)
        val pill = bounds(BAR_PILL)
        println("A14 $where: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, bar ${bar.left.value}..${bar.right.value} x ${bar.top.value}..${bar.bottom.value}, pill ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}")
        assertApart("$where: the card and the bar", card, bar)
        assertApart("$where: the card and the pill", card, pill)
    }

    fun assertSame(what: String, expected: DpRect, actual: DpRect) {
        listOf(expected.left to actual.left, expected.top to actual.top, expected.right to actual.right, expected.bottom to actual.bottom).forEach { (e, a) ->
            assertEquals("$what: expected $expected, was $actual", e.value, a.value, 0.5f)
        }
    }

    fun assertApart(what: String, a: DpRect, b: DpRect) {
        val apart = a.right <= b.left + 0.5.dp || b.right <= a.left + 0.5.dp || a.bottom <= b.top + 0.5.dp || b.bottom <= a.top + 0.5.dp
        assertTrue("$what meet: $a and $b", apart)
    }

    /** Whether the text at [tag] is cut with an ellipsis, with its lines and the width it took, for a class under native graphics. */
    fun line(tag: String): Pair<Boolean, String>? {
        if (count(tag) == 0) return null
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val cut = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        return cut to "${layout.layoutInput.text}: lines ${layout.lineCount}, cut $cut, box ${composeRule.density.run { layout.size.width.toDp() }}, ink ${composeRule.density.run { (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }.toDp() }}"
    }
}
