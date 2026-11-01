package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.route.routeTotalDistanceText
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * A planned trip with a route, in the Trip Planner (dispatch 2026-10-30-08, step 3): its name, its distance and its date when
 * it has one; no MGRS or coordinates (Amendment 1, Q12: "No, keep it clean (Recommended)"). Climb joins it in step 3b. Tapping
 * the row's body opens the route on the map ([onOpen]); Delete only deletes. Directions to the first stop is on the route's
 * card (Amendment 1, Q7), so the row has none.
 */
@Composable
internal fun PlannedRouteRow(
    trip: PlannedTrip,
    isToday: Boolean,
    distanceUnit: DistanceUnit,
    onOpen: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val route = trip.route ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag(plannedRouteRowTag(trip.id))
                    .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (isToday) Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(trip.name, style = MaterialTheme.typography.titleSmall)
                // Amendment 11 (RECORD -1909, the owner: "Match the card (Recommended)"): the card's own total, so one route never shows two distances.
                Text(routeTotalDistanceText(route.legs.map { it.meters }, distanceUnit), style = MaterialTheme.typography.bodyMedium)
                trip.date?.let { Text(displayDate(it), style = MaterialTheme.typography.bodyMedium) }
            }
            BouncingIconButton(onClick = onDelete, modifier = Modifier.padding(end = Spacing.sm).testTag(plannedRouteDeleteTag(trip.id))) {
                Icon(Icons.Filled.Delete, contentDescription = plannedTripDeleteLabel(trip))
            }
        }
    }
}

internal fun plannedRouteRowTag(tripId: String) = "planned-route-row-$tripId"
internal fun plannedRouteDeleteTag(tripId: String) = "planned-route-delete-$tripId"
