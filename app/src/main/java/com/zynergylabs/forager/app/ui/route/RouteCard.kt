package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor

/*
 * The route card (dispatch 2026-10-30-08, step 3; the first stop, (b)): a non-modal card at the bottom of the Maps tab's map,
 * so the map above and beside it keeps its taps while plotting. Not a ModalBottomSheet, whose scrim would take the next
 * waypoint's tap. Its fill is the map chrome's 80% (CLAUDE.md, "Nothing fully obstructs the map"); its content colour opaque.
 *
 * Two states, peek and expanded, moved between by tapping the handle or dragging it. The state is user-set and kept in the
 * ViewModel, so it survives a tab change (CLAUDE.md, UX defaults).
 *
 * Its bounds are its own: a width cap of [ROUTE_CARD_MAX_WIDTH] and a height that wraps its content, the expanded rows capped
 * and scrolling. Nothing in it fills the map box (CLAUDE.md, the Surface pitfall), so a long-press above or beside it reaches
 * the map.
 *
 * Words on screen, all approved: "Tap the next waypoint", "Undo", "Cancel", "Save", "No trail data, straight line" (RECORD
 * -1845 and Amendment 1), and "Directions" on a saved route (Amendment 1, Q7).
 */

/** M3's sheet width cap (`BottomSheetDefaults.SheetMaxWidth`), so the card stays a readable column on a tablet. */
val ROUTE_CARD_MAX_WIDTH: Dp = 640.dp

/** How far a drag on the handle must go to move the card between peek and expanded. */
private val HANDLE_DRAG_THRESHOLD = 24.dp

/** The room the handle takes in the card's layout, as it always has: the bar sits in the middle of it. */
private val HANDLE_LAYOUT_HEIGHT = 32.dp

/**
 * The handle's touch target (Amendment 5, N-9: "a 48 dp touch target, with the same look"), Material's minimum. The extra
 * 16 dp reach down over the top of the summary line, which takes no touch of its own, so the card and the bar do not move.
 */
private val HANDLE_TOUCH_HEIGHT = 48.dp

/** The card's content: the route being plotted or the saved route opened. */
sealed interface RouteCardContent {
    val stops: List<RouteStop>
    val legs: List<RouteLeg>

    /** Plotting: [legInFlight] while the leg to the last stop is still being routed. */
    data class Plotting(override val stops: List<RouteStop>, override val legs: List<RouteLeg>, val canSave: Boolean) : RouteCardContent

    /** A saved route, under its [name]. */
    data class Saved(val name: String, override val stops: List<RouteStop>, override val legs: List<RouteLeg>) : RouteCardContent
}

/** What the card's buttons do. Each is a plain lambda; the host wires them to [RoutePlottingActions]. */
class RouteCardCallbacks(
    val onToggleExpanded: (Boolean) -> Unit,
    val onUndo: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDirections: () -> Unit = {},
)

@Composable
fun RouteCard(
    content: RouteCardContent,
    expanded: Boolean,
    distanceUnit: DistanceUnit,
    callbacks: RouteCardCallbacks,
    expandedMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val fill = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val contentColour = contentColorFor(navigationBarContainerColor())
    Surface(
        modifier = modifier
            .widthIn(max = ROUTE_CARD_MAX_WIDTH)
            .fillMaxWidth()
            .testTag(ROUTE_CARD_TAG)
            .mapChromeContainerColor(fill),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = fill,
        contentColor = contentColour,
    ) {
        Column(Modifier.mapChromeContentColor(LocalContentColor.current).padding(horizontal = Spacing.md)) {
            RouteCardHandle(expanded, callbacks.onToggleExpanded)
            RouteSummaryLine(content, distanceUnit)
            if (expanded) {
                Column(
                    Modifier
                        .heightIn(max = expandedMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .testTag(ROUTE_CARD_LEGS_TAG),
                ) {
                    RouteLegRows(content, distanceUnit)
                }
            }
            RouteCardButtons(content, callbacks)
        }
    }
}

/**
 * The handle: a tap toggles the card, and a drag up or down past [HANDLE_DRAG_THRESHOLD] expands or collapses it. It is laid
 * out [HANDLE_LAYOUT_HEIGHT] tall and takes touches over [HANDLE_TOUCH_HEIGHT] from its top (Amendment 5, N-9).
 */
@Composable
private fun RouteCardHandle(expanded: Boolean, onToggleExpanded: (Boolean) -> Unit) {
    val currentExpanded by rememberUpdatedState(expanded)
    val currentToggle by rememberUpdatedState(onToggleExpanded)
    val dragged = remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { HANDLE_DRAG_THRESHOLD.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val touch = HANDLE_TOUCH_HEIGHT.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = touch, maxHeight = touch))
                layout(placeable.width, HANDLE_LAYOUT_HEIGHT.roundToPx()) { placeable.place(0, 0) }
            }
            .testTag(ROUTE_CARD_HANDLE_TAG)
            .clickable(role = Role.Button) { currentToggle(!currentExpanded) }
            .draggable(
                state = rememberDraggableState { delta -> dragged.floatValue += delta },
                orientation = Orientation.Vertical,
                onDragStarted = { dragged.floatValue = 0f },
                onDragStopped = {
                    when {
                        dragged.floatValue <= -thresholdPx && !currentExpanded -> currentToggle(true)
                        dragged.floatValue >= thresholdPx && currentExpanded -> currentToggle(false)
                    }
                },
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = (HANDLE_LAYOUT_HEIGHT - 4.dp) / 2)
                .size(width = 32.dp, height = 4.dp)
                .background(LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
        )
    }
}

/** "Creek → Ridge → Falls   2.9 mi", or the hint while there is one stop (Amendment 1, Q1). */
@Composable
private fun RouteSummaryLine(content: RouteCardContent, distanceUnit: DistanceUnit) {
    if (content is RouteCardContent.Saved) {
        Text(content.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(ROUTE_CARD_NAME_TAG))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            content.stops.joinToString(" → ") { it.name },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f).testTag(ROUTE_CARD_STOPS_TAG),
        )
        if (content.legs.isNotEmpty()) {
            Text(
                formatDistanceMeters(content.legs.sumOf { it.meters }, distanceUnit),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(ROUTE_CARD_TOTAL_TAG),
            )
        }
        if (content.legInFlight) CircularProgressIndicator(Modifier.size(16.dp).testTag(ROUTE_CARD_PROGRESS_TAG), strokeWidth = 2.dp)
    }
    if (content.stops.size == 1) {
        Text(TAP_NEXT_WAYPOINT, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(ROUTE_CARD_HINT_TAG))
    }
}

/** One row per routed leg, "Creek → Ridge   1.4 mi", with the straight-line note; the leg in flight with a progress mark. */
@Composable
private fun RouteLegRows(content: RouteCardContent, distanceUnit: DistanceUnit) {
    content.legs.forEachIndexed { i, leg ->
        Column(Modifier.padding(vertical = Spacing.xs).testTag(routeCardLegTag(i))) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("${content.stops[i].name} → ${content.stops[i + 1].name}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatDistanceMeters(leg.meters, distanceUnit), style = MaterialTheme.typography.bodyMedium)
            }
            if (leg is RouteLeg.Straight) Text(NO_TRAIL_DATA_NOTE, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (content.legInFlight) {
        val i = content.legs.size
        Row(Modifier.padding(vertical = Spacing.xs).testTag(routeCardLegTag(i)), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("${content.stops[i].name} → ${content.stops[i + 1].name}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** Undo, Cancel and Save while plotting (Save at the end); Directions to the first stop on a saved route (Amendment 1, Q7). */
@Composable
private fun RouteCardButtons(content: RouteCardContent, callbacks: RouteCardCallbacks) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (content) {
            is RouteCardContent.Plotting -> {
                TextButton(onClick = callbacks.onUndo, enabled = content.stops.size >= 2, modifier = Modifier.testTag(ROUTE_CARD_UNDO_TAG)) { Text("Undo") }
                TextButton(onClick = callbacks.onCancel, modifier = Modifier.testTag(ROUTE_CARD_CANCEL_TAG)) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = callbacks.onSave, enabled = content.canSave, modifier = Modifier.testTag(ROUTE_CARD_SAVE_TAG)) { Text("Save") }
            }
            is RouteCardContent.Saved -> {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = callbacks.onDirections, modifier = Modifier.testTag(ROUTE_CARD_DIRECTIONS_TAG)) { Text("Directions") }
            }
        }
    }
}

/** A leg is being routed: the stops have run ahead of the legs. A saved route never has one. */
val RouteCardContent.legInFlight: Boolean get() = legs.size < stops.size - 1

const val TAP_NEXT_WAYPOINT = "Tap the next waypoint"
const val NO_TRAIL_DATA_NOTE = "No trail data, straight line"

const val ROUTE_CARD_TAG = "route-card"
const val ROUTE_CARD_HANDLE_TAG = "route-card-handle"
const val ROUTE_CARD_NAME_TAG = "route-card-name"
const val ROUTE_CARD_STOPS_TAG = "route-card-stops"
const val ROUTE_CARD_TOTAL_TAG = "route-card-total"
const val ROUTE_CARD_PROGRESS_TAG = "route-card-progress"
const val ROUTE_CARD_HINT_TAG = "route-card-hint"
const val ROUTE_CARD_LEGS_TAG = "route-card-legs"
const val ROUTE_CARD_UNDO_TAG = "route-card-undo"
const val ROUTE_CARD_CANCEL_TAG = "route-card-cancel"
const val ROUTE_CARD_SAVE_TAG = "route-card-save"
const val ROUTE_CARD_DIRECTIONS_TAG = "route-card-directions"

fun routeCardLegTag(index: Int) = "route-card-leg-$index"
