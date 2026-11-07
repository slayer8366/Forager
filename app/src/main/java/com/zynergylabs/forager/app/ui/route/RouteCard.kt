package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.RouteElevation
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
 * ViewModel, so it survives a tab change (CLAUDE.md, UX defaults). Between them the rows slide, following the finger
 * (Amendment 12, RECORD -1910, `RouteCardReveal.kt`).
 *
 * Its bounds are its own: the width its host gives it (`RouteCardPlacement.kt`: one width from the first tap, C16 with C19, RECORD
 * -1946, -1953) and a height that wraps its content, the expanded rows capped and scrolling. A line that can't fit wraps, and nothing
 * is cut with an ellipsis (Amendment 16, RECORD -1932, the owner: "Wrap to two lines (Recommended)"), so a long name takes as many
 * lines as it needs. Nothing in it fills the map box (CLAUDE.md, the Surface pitfall), so a long-press above or beside it reaches
 * the map.
 *
 * Words on screen, all approved: "Undo", "Cancel", "Save", "No trail data, straight line" (RECORD -1845 and Amendment 1),
 * "Directions" on a saved route (Amendment 1, Q7), and Amendment 6's (RECORD -1887, in RouteCardText.kt): "1 Waypoint
 * selected, tap another to route", which replaced "Tap the next waypoint", "<from> to <to>", "N Waypoints routed" and
 * "Elevation:".
 *
 * The layout is the owner's, from the first use on the S22 (Amendment 6) and Amendment 12 (RECORD -1910): the handle, a saved
 * route's name, the rows when open, the total line, the buttons. The total line is "N Waypoints routed" with the total at its
 * right. Each row is a leg with its distance at the right. Under each, an elevation line when the content carries one (the seam
 * for #447), filled while plotting since Amendment 8 (RECORD -1896, `RouteLegElevation.kt`); a saved route carries none until
 * step 3b. The total line sits under the rows and over the buttons, so it stays put on screen while the rows slide up above it.
 */

/** The card's padding at each side, which [rememberRouteCardWidth] counts. */
internal val ROUTE_CARD_SIDE_PADDING: Dp = Spacing.md

/** The progress mark's size, which [rememberRouteCardWidth] counts. */
internal val ROUTE_CARD_PROGRESS_SIZE: Dp = 16.dp

/** How far a drag on the handle must go to move the card between peek and expanded. */
private val HANDLE_DRAG_THRESHOLD = 24.dp

/** The room the handle takes in the card's layout, as it always has: the bar sits in the middle of it. */
private val HANDLE_LAYOUT_HEIGHT = 32.dp

/**
 * The handle's touch target (Amendment 5, N-9: "a 48 dp touch target, with the same look"), Material's minimum. The extra
 * 16 dp reach down over what comes next, so the card and the bar do not move. That can be the rows, which scroll, so the handle
 * is above them in touch order ([RouteCardHandle]'s zIndex).
 */
private val HANDLE_TOUCH_HEIGHT = 48.dp

/** The card's content: the route being plotted or the saved route opened. */
sealed interface RouteCardContent {
    val stops: List<RouteStop>
    val legs: List<RouteLeg>

    /**
     * Amendment 6's seam (RECORD -1887): an optional elevation for each leg, by the leg's index. The whole route's line is added
     * up from these ([routeTotalElevationText]). Filled while plotting from the trail data's heights (Amendment 8, RECORD -1896);
     * empty for a saved route until step 3b.
     */
    val legElevations: List<RouteElevation?>

    /** Plotting: [legInFlight] while the leg to the last stop is still being routed. */
    data class Plotting(
        override val stops: List<RouteStop>,
        override val legs: List<RouteLeg>,
        val canSave: Boolean,
        override val legElevations: List<RouteElevation?> = emptyList(),
    ) : RouteCardContent

    /**
     * A saved route, under its [name]. [selectedStop] is the stop Navigate goes to, [canNavigate] whether it can, and
     * [stopLeadsNowhere] whether that is because the stop's waypoint was moved or deleted, which the card then says (dispatch
     * 2026-11-06-05, Amendment 2; `RouteCardStops.kt`). A host that navigates nowhere (tests) greys Navigate with no reason.
     */
    data class Saved(
        val name: String,
        override val stops: List<RouteStop>,
        override val legs: List<RouteLeg>,
        override val legElevations: List<RouteElevation?> = emptyList(),
        val selectedStop: Int = 0,
        val canNavigate: Boolean = false,
        val stopLeadsNowhere: Boolean = false,
    ) : RouteCardContent
}

/** What the card's buttons do. Each is a plain lambda; the host wires them to [RoutePlottingActions]. */
class RouteCardCallbacks(
    val onToggleExpanded: (Boolean) -> Unit,
    val onUndo: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDirections: () -> Unit = {},
    /** The ✕ on a saved route's card (dispatch 2026-11-06-05, Amendment 1, step 3; `RouteCardEdges.kt`). */
    val onClose: () -> Unit = {},
    /** Navigate, left of Directions, to the selected stop (Amendment 2, item 3). */
    val onNavigate: () -> Unit = {},
    /** A stop's row in the open saved card tapped (Amendment 2, item 4). */
    val onSelectStop: (Int) -> Unit = {},
)

@Composable
fun RouteCard(
    content: RouteCardContent,
    expanded: Boolean,
    distanceUnit: DistanceUnit,
    callbacks: RouteCardCallbacks,
    expandedMaxHeight: Dp,
    modifier: Modifier = Modifier,
    /** Its outline: square on a screen edge it sits against, round facing the map (C17, C18; `RouteCardPlacement.kt`). */
    shape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    /** Kept empty under the buttons: the part of the card tucked behind the landscape navigation card (C11, restored by C21, RECORD -1955). */
    contentBottomPadding: Dp = 0.dp,
    /**
     * D37 (dispatch 2026-10-30-08, RECORD -2006): told the height of the card's lines other than the rows (the handle, the name, the
     * total line, the buttons and [contentBottomPadding]), in px, whenever it changes: what the rows leave of the card's height.
     */
    onLinesHeightPx: (Int) -> Unit = {},
    /**
     * D40 (RECORD -2009, the owner: "Hide elevation while open"): the total line's elevation line is left out, where even D37's raised
     * cap leaves the rows no room. [onLinesHeightPx] still counts it, at its last measured height, so the answer does not flip back.
     */
    hideElevation: Boolean = false,
    /**
     * D42 (RECORD -2021): told the card's natural heights in px, whatever the cap squeezes them to: its lines (the handle, the name,
     * the total line with its elevation line counted even while D40 leaves it out, the buttons and [contentBottomPadding]), the total
     * line's elevation line, and the first leg row (0 while no row is composed). Intrinsic heights at the card's width, so they do not
     * depend on the room the card is given.
     */
    onNaturalHeightsPx: (RouteCardNaturalHeights) -> Unit = {},
    /** The screen edge the card is flush against, which its outline leaves open (dispatch 2026-11-06-05, step 6, and Amendment 3; `RouteCardEdges.kt`). */
    openEdges: Set<RouteCardEdge> = emptySet(),
) {
    val fill = mapChromeFill(navigationBarContainerColor(), overMap = true)
    val contentColour = contentColorFor(navigationBarContainerColor())
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ROUTE_CARD_TAG)
            .mapChromeContainerColor(fill),
        shape = shape,
        color = fill,
        contentColor = contentColour,
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val reveal = remember { RouteCardReveal(expanded) }
        LaunchedEffect(expanded) { reveal.settle(expanded) }
        var columnPx by remember { mutableIntStateOf(0) }
        var rowsPx by remember { mutableIntStateOf(0) }
        var elevationPx by remember { mutableIntStateOf(0) }
        val currentOnLines by rememberUpdatedState(onLinesHeightPx)
        val elevationLeftOut = if (hideElevation) elevationPx else 0
        LaunchedEffect(columnPx, rowsPx, elevationLeftOut) { if (columnPx > 0) currentOnLines(columnPx - rowsPx + elevationLeftOut) }
        var nameNatural by remember { mutableIntStateOf(0) }
        var totalNatural by remember { mutableIntStateOf(0) }
        var buttonsNatural by remember { mutableIntStateOf(0) }
        var firstRowNatural by remember { mutableIntStateOf(0) }
        val currentOnNatural by rememberUpdatedState(onNaturalHeightsPx)
        val handlePx = with(density) { HANDLE_LAYOUT_HEIGHT.roundToPx() }
        val bottomPx = with(density) { contentBottomPadding.roundToPx() }
        val linesNatural = handlePx + nameNatural + totalNatural + elevationLeftOut + buttonsNatural + bottomPx
        LaunchedEffect(linesNatural, elevationPx, firstRowNatural) { if (totalNatural > 0 && buttonsNatural > 0) currentOnNatural(RouteCardNaturalHeights(linesNatural, elevationPx, firstRowNatural)) }
        Box {
        Column(Modifier.mapChromeContentColor(LocalContentColor.current).padding(start = ROUTE_CARD_SIDE_PADDING, end = ROUTE_CARD_SIDE_PADDING, bottom = contentBottomPadding).onSizeChanged { columnPx = it.height + with(density) { contentBottomPadding.roundToPx() } }) {
            RouteCardHandle(expanded, reveal, callbacks.onToggleExpanded)
            if (content is RouteCardContent.Saved) {
                DisposableEffect(Unit) { onDispose { nameNatural = 0 } }
                Text(content.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = ROUTE_CARD_CLOSE_TOUCH - ROUTE_CARD_SIDE_PADDING).testTag(ROUTE_CARD_NAME_TAG).naturalHeight { nameNatural = it })
            }
            if ((expanded || reveal.showsRows) && (content.legs.isNotEmpty() || content.legInFlight)) {
                DisposableEffect(Unit) { onDispose { rowsPx = 0; firstRowNatural = 0 } }
                // Amendment 2, item 4: a saved route lists its waypoints, each with the leg leading to it (RouteCardStops.kt).
                if (content is RouteCardContent.Saved) {
                    RouteCardStopRows(content, distanceUnit, reveal, expandedMaxHeight, callbacks.onSelectStop, onHeightPx = { rowsPx = it }, onFirstRowNaturalPx = { firstRowNatural = it })
                } else {
                    RouteLegRows(content, distanceUnit, reveal, expandedMaxHeight, onHeightPx = { rowsPx = it }, onFirstRowNaturalPx = { firstRowNatural = it })
                }
            }
            Box(Modifier.naturalHeight { totalNatural = it }) {
                if (content.legs.isNotEmpty()) RouteTotalLine(content, distanceUnit, showElevation = !hideElevation, onElevationHeightPx = { elevationPx = it }) else RouteNoLegsLine(content)
            }
            Box(Modifier.naturalHeight { buttonsNatural = it }) { RouteCardButtons(content, callbacks) }
        }
        if (content is RouteCardContent.Saved) RouteCardCloseButton(callbacks.onClose)
        RouteCardOutline(shape, openEdges)
        }
    }
}

/**
 * The handle: a tap toggles the card, and a drag up or down past [HANDLE_DRAG_THRESHOLD] expands or collapses it. It is laid
 * out [HANDLE_LAYOUT_HEIGHT] tall and takes touches over [HANDLE_TOUCH_HEIGHT] from its top (Amendment 5, N-9).
 */
@Composable
private fun RouteCardHandle(expanded: Boolean, reveal: RouteCardReveal, onToggleExpanded: (Boolean) -> Unit) {
    val currentExpanded by rememberUpdatedState(expanded)
    val currentToggle by rememberUpdatedState(onToggleExpanded)
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { HANDLE_DRAG_THRESHOLD.toPx() }
    Box(
        modifier = Modifier
            .zIndex(1f)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val touch = HANDLE_TOUCH_HEIGHT.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = touch, maxHeight = touch))
                layout(placeable.width, HANDLE_LAYOUT_HEIGHT.roundToPx()) { placeable.place(0, 0) }
            }
            .testTag(ROUTE_CARD_HANDLE_TAG)
            .clickable(role = Role.Button) { currentToggle(!currentExpanded) }
            .draggable(
                state = rememberDraggableState(reveal::dragBy),
                orientation = Orientation.Vertical,
                onDragStarted = { reveal.grab() },
                onDragStopped = {
                    val target = when {
                        reveal.draggedPx <= -thresholdPx -> true
                        reveal.draggedPx >= thresholdPx -> false
                        else -> currentExpanded
                    }
                    if (target != currentExpanded) currentToggle(target)
                    reveal.release(target)
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

/**
 * The total line (Amendment 12, RECORD -1910): "3 Waypoints routed   1.9 mi", with the whole route's elevation under it in the
 * form of the rows'. It is at peek and expanded, the only place the total shows. The count is the waypoints the routed legs join,
 * so a stop whose leg is still in flight is not counted until it lands.
 */
@Composable
private fun RouteTotalLine(content: RouteCardContent, distanceUnit: DistanceUnit, showElevation: Boolean = true, onElevationHeightPx: (Int) -> Unit = {}) {
    Column(Modifier.padding(vertical = Spacing.xs).testTag(ROUTE_CARD_ROUTED_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(waypointsRoutedText(content.legs.size + 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).testTag(ROUTE_CARD_ROUTED_COUNT_TAG))
            Text(routeTotalDistanceText(content.legs.map { it.meters }, distanceUnit), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(ROUTE_CARD_ROUTED_TOTAL_TAG))
            if (content.legInFlight) RouteCardProgress()
        }
        routeTotalElevationText(content.legElevations, UnitSystem.forDistanceUnit(distanceUnit))?.takeIf { showElevation }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(ROUTE_CARD_ROUTED_ELEVATION_TAG).naturalHeight(onElevationHeightPx))
        }
    }
}

/**
 * Before the first leg lands there is no total. At one stop this is the hint, as ever (Amendment 12: "one stop keeps ..."). At two
 * with the first leg in flight no words are true yet, and none are approved for it, so only the progress mark shows.
 */
@Composable
private fun RouteNoLegsLine(content: RouteCardContent) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        if (content.legInFlight) {
            RouteCardProgress()
        } else {
            Text(ONE_WAYPOINT_SELECTED, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag(ROUTE_CARD_COUNT_TAG))
        }
    }
}

@Composable
private fun RouteCardProgress() {
    CircularProgressIndicator(Modifier.size(ROUTE_CARD_PROGRESS_SIZE).testTag(ROUTE_CARD_PROGRESS_TAG), strokeWidth = 2.dp)
}

/**
 * The rows (Amendment 6): one per routed leg, "Creek to Ridge   1.4 mi", its elevation under it when the content carries one, and
 * the straight-line note; then the leg in flight with a progress mark. They scroll past [maxHeight], and show as far as [reveal]
 * says. Measured last among the card's lines (`weight`), so the total line and the buttons are never squeezed in a short window.
 */
@Composable
private fun ColumnScope.RouteLegRows(content: RouteCardContent, distanceUnit: DistanceUnit, reveal: RouteCardReveal, maxHeight: Dp, onHeightPx: (Int) -> Unit = {}, onFirstRowNaturalPx: (Int) -> Unit = {}) {
    val unitSystem = UnitSystem.forDistanceUnit(distanceUnit)
    Box(Modifier.weight(1f, fill = false).onSizeChanged { onHeightPx(it.height) }.routeCardReveal(reveal)) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).testTag(ROUTE_CARD_LEGS_TAG)) {
            content.legs.forEachIndexed { i, leg ->
                Column(Modifier.padding(vertical = Spacing.xs).testTag(routeCardLegTag(i)).then(if (i == 0) Modifier.naturalHeight(onFirstRowNaturalPx) else Modifier)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(routeLegText(content.stops[i].name, content.stops[i + 1].name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag(routeCardLegNameTag(i)))
                        Text(formatDistanceMeters(leg.meters, distanceUnit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(routeCardLegDistanceTag(i)))
                    }
                    content.legElevations.getOrNull(i)?.let {
                        Text(routeElevationText(it, unitSystem), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(routeCardLegElevationTag(i)))
                    }
                    if (leg is RouteLeg.Straight) Text(NO_TRAIL_DATA_NOTE, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (content.legInFlight) {
                val i = content.legs.size
                Row(Modifier.padding(vertical = Spacing.xs).testTag(routeCardLegTag(i)), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(routeLegText(content.stops[i].name, content.stops[i + 1].name), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag(routeCardLegNameTag(i)))
                    CircularProgressIndicator(Modifier.size(ROUTE_CARD_PROGRESS_SIZE), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** Undo, Cancel and Save while plotting (Save at the end); on a saved route, Navigate and Directions (`RouteCardStops.kt`). */
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
            is RouteCardContent.Saved -> RouteCardSavedButtons(content, callbacks)
        }
    }
}

/** D42 (RECORD -2021): the card's natural heights in px, [RouteCard]'s `onNaturalHeightsPx`. */
data class RouteCardNaturalHeights(val linesPx: Int, val elevationPx: Int, val firstRowPx: Int)

/** Reports its content's intrinsic height at the width it is given, then lays it out as it would anyway. */
internal fun Modifier.naturalHeight(onPx: (Int) -> Unit): Modifier = layout { measurable, constraints ->
    onPx(measurable.maxIntrinsicHeight(constraints.maxWidth))
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** A leg is being routed: the stops have run ahead of the legs. A saved route never has one. */
val RouteCardContent.legInFlight: Boolean get() = legs.size < stops.size - 1

const val NO_TRAIL_DATA_NOTE = "No trail data, straight line"

const val ROUTE_CARD_TAG = "route-card"
const val ROUTE_CARD_HANDLE_TAG = "route-card-handle"
const val ROUTE_CARD_NAME_TAG = "route-card-name"
const val ROUTE_CARD_PROGRESS_TAG = "route-card-progress"
const val ROUTE_CARD_LEGS_TAG = "route-card-legs"
const val ROUTE_CARD_UNDO_TAG = "route-card-undo"
const val ROUTE_CARD_CANCEL_TAG = "route-card-cancel"
const val ROUTE_CARD_SAVE_TAG = "route-card-save"
const val ROUTE_CARD_DIRECTIONS_TAG = "route-card-directions"

const val ROUTE_CARD_COUNT_TAG = "route-card-count"
const val ROUTE_CARD_ROUTED_TAG = "route-card-routed"
const val ROUTE_CARD_ROUTED_COUNT_TAG = "route-card-routed-count"
const val ROUTE_CARD_ROUTED_TOTAL_TAG = "route-card-routed-total"
const val ROUTE_CARD_ROUTED_ELEVATION_TAG = "route-card-routed-elevation"

fun routeCardLegTag(index: Int) = "route-card-leg-$index"
fun routeCardLegNameTag(index: Int) = "route-card-leg-name-$index"
fun routeCardLegDistanceTag(index: Int) = "route-card-leg-distance-$index"
fun routeCardLegElevationTag(index: Int) = "route-card-leg-elevation-$index"
