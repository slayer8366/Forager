package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * A saved route's card, open, and its buttons (dispatch 2026-11-06-05, Amendment 2, items 3 and 4; RECORD -2187).
 *
 * Open, the card lists the route's waypoints, each a row to tap, with the distance of the leg leading to it under it (the owner:
 * "Waypoints with leg distances", "Each waypoint is a tappable row, with the distance of the leg leading to it under it"). The first
 * stop has no leg leading to it, so nothing under it. Tapping a row selects that stop; the selection shows only by its marker on the
 * map ("Only the marker"), so the rows look alike, and a screen reader is told which one is selected. While plotting, the open card
 * keeps its leg rows, as before: there is nothing to select or navigate to until the route is saved.
 *
 * The buttons: Navigate, left of Directions (Amendment 1, step 5), to the selected stop. Where it cannot go, Navigate is greyed and the
 * owner's words stand at the row's start, where the space was, as written: "This stop's waypoint was moved or deleted".
 */

/** The owner's reason, verbatim (Amendment 2: "As I wrote it"). */
const val NAVIGATE_UNAVAILABLE_REASON = "This stop's waypoint was moved or deleted"

/** A row's touch height: Material's minimum, so a one-line row with no leg under it (the first) is as easy to hit as the rest. */
private val STOP_ROW_MIN_HEIGHT = 48.dp

/**
 * The saved route's stops as rows, in the place and the reveal of the leg rows they replace (`RouteCard`'s `RouteLegRows`): they
 * scroll past [maxHeight] and are measured last, so the total line and the buttons are never squeezed.
 */
@Composable
internal fun ColumnScope.RouteCardStopRows(
    content: RouteCardContent.Saved,
    distanceUnit: DistanceUnit,
    reveal: RouteCardReveal,
    maxHeight: androidx.compose.ui.unit.Dp,
    onSelectStop: (Int) -> Unit,
    onHeightPx: (Int) -> Unit = {},
    onFirstRowNaturalPx: (Int) -> Unit = {},
) {
    val unitSystem = UnitSystem.forDistanceUnit(distanceUnit)
    Box(Modifier.weight(1f, fill = false).onSizeChanged { onHeightPx(it.height) }.routeCardReveal(reveal)) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).testTag(ROUTE_CARD_STOPS_TAG)) {
            content.stops.forEachIndexed { i, stop ->
                val legIn = content.legs.getOrNull(i - 1)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = STOP_ROW_MIN_HEIGHT)
                        .clickable(role = Role.Button) { onSelectStop(i) }
                        .semantics { selected = i == content.selectedStop }
                        .padding(vertical = Spacing.xs)
                        .testTag(routeCardStopTag(i))
                        .then(if (i == 0) Modifier.naturalHeight(onFirstRowNaturalPx) else Modifier),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stop.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(routeCardStopNameTag(i)))
                    if (legIn != null) {
                        Text(formatDistanceMeters(legIn.meters, distanceUnit), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag(routeCardStopLegTag(i)))
                        content.legElevations.getOrNull(i - 1)?.let { Text(routeElevationText(it, unitSystem), style = MaterialTheme.typography.bodySmall) }
                        if (legIn is RouteLeg.Straight) Text(NO_TRAIL_DATA_NOTE, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** A saved route's buttons: the reason when Navigate is greyed, then Navigate, then Directions, at the end. */
@Composable
internal fun RouteCardSavedButtons(content: RouteCardContent.Saved, callbacks: RouteCardCallbacks) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (content.canNavigate) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(NAVIGATE_UNAVAILABLE_REASON, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).testTag(ROUTE_CARD_NAVIGATE_REASON_TAG))
        }
        TextButton(onClick = callbacks.onNavigate, enabled = content.canNavigate, modifier = Modifier.testTag(ROUTE_CARD_NAVIGATE_TAG)) { Text("Navigate") }
        TextButton(onClick = callbacks.onDirections, modifier = Modifier.testTag(ROUTE_CARD_DIRECTIONS_TAG)) { Text("Directions") }
    }
}

const val ROUTE_CARD_STOPS_TAG = "route-card-stops"
const val ROUTE_CARD_NAVIGATE_TAG = "route-card-navigate"
const val ROUTE_CARD_NAVIGATE_REASON_TAG = "route-card-navigate-reason"

fun routeCardStopTag(index: Int) = "route-card-stop-$index"
fun routeCardStopNameTag(index: Int) = "route-card-stop-name-$index"
fun routeCardStopLegTag(index: Int) = "route-card-stop-leg-$index"
