package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage F: trips and waypoints, moved verbatim out of
// AvailabilityScreen.kt. One block, lines 5116-5343 of the file as of 3bd0efe: TripPlannerSection,
// PlannedTripsList, PlannedTripRow, WaypointsSection, WaypointRow. Same package as Stages A to E,
// so every same-package reference resolves unchanged. Pure move: no signature, name or body
// changed. No widening: TripPlannerSection and WaypointsSection were already internal. No symbol
// left behind is reached from here. Seam F (the wide layout) was released by the owner for this
// split, as recorded in the Understory amendment merged in #130.

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import androidx.compose.runtime.key
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.TwoStageSwipeRow
import com.zynergylabs.forager.app.ui.log.opensRecordDetails
import com.zynergylabs.forager.app.ui.log.rememberSwipeRevealGroup
import com.zynergylabs.forager.app.ui.log.swipeRevealTouchWatcher
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * The Trip Planner drawer section's content: the planned-trips list (see [PlannedTripsList])
 * above the existing rain-driven trip windows, gated on a search the same way [MapTab] is —
 * before any region is chosen there is no rainfall history to plan from, so this says that rather
 * than rendering an empty [TripWindowsCard]. The planned-trips list has no such gate: it lists
 * absolute map points the user placed, independent of any search.
 */
@Composable
internal fun TripPlannerSection(uiState: AvailabilityUiState, onDeletePlannedTrip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PlannedTripsList(plannedTrips = uiState.plannedTrips, onDeletePlannedTrip = onDeletePlannedTrip)
        HorizontalDivider()
        if (uiState.hasSearched) {
            TripWindowsCard(uiState = uiState)
        } else {
            Text(
                "Choose a region in search options to see rain-driven trip windows.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The list of trips the user has placed on the map, sorted by
 * [GetPlannedTripsUseCase][com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase] with any dated today
 * already moved to the front — this composable renders that order, it doesn't recompute it.
 */
@Composable
private fun PlannedTripsList(plannedTrips: List<PlannedTrip>, onDeletePlannedTrip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Planned Trips", style = MaterialTheme.typography.titleSmall)
        // Motion Part 3, Amendment 1 (RECORD -681: "Every list's rows close up and grow in"; scout P1): a deleted trip's card
        // fades and shrinks while the cards below close up (motion/ListMotion.kt). The last one leaves before the empty text.
        val rows = rememberListRows(plannedTrips, key = { it.id })
        if (rows.isEmpty()) {
            Text(
                "No trips planned yet. Tap the add button on the map to plan one.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val today = LocalDate.now()
            rows.forEach { row ->
                key(row.key) {
                    ListRowMotion(row) { trip ->
                        PlannedTripRow(
                            trip = trip,
                            isToday = trip.date == today,
                            onDelete = { onDeletePlannedTrip(trip.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One planned trip: its user-chosen [PlannedTrip.name] as the primary identifying text (more
 * prominent than the coordinates below it, per the user's own framing — the name is what a
 * person recognizes a trip by, the coordinates are supporting detail), its MGRS grid reference
 * (the format asked for on this screen; see [MgrsConverter]) with decimal degrees kept alongside
 * since some readers still want them and MGRS can't cover every point (see
 * [MgrsCoordinate.Unsupported]), a "Directions" action that hands the location to whatever
 * navigation app is installed (see [launchDirections]), and delete.
 */
@Composable
private fun PlannedTripRow(trip: PlannedTrip, isToday: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isToday) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(trip.name, style = MaterialTheme.typography.titleSmall)
                Text(displayDate(trip.date), style = MaterialTheme.typography.bodyMedium)
                when (val mgrs = MgrsConverter.convert(trip.location)) {
                    is MgrsCoordinate.Grid -> Text(mgrs.value, style = MaterialTheme.typography.bodySmall)
                    // No line at all rather than a wrong or truncated one — see MgrsCoordinate's
                    // own doc comment. The decimal-degrees line below still locates the trip.
                    is MgrsCoordinate.Unsupported -> Unit
                }
                Text(
                    "${"%.4f".format(trip.location.lat)}, ${"%.4f".format(trip.location.lng)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BouncingIconButton(onClick = { launchDirections(context, trip) }) {
                Icon(Icons.Filled.Directions, contentDescription = "Directions to ${trip.name}")
            }
            BouncingIconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove planned trip for ${displayDate(trip.date)}")
            }
        }
    }
}

/**
 * The saved-waypoints drawer section — same "list plus delete" shape as [PlannedTripsList], the
 * one existing precedent for a user-placed-point list in this drawer. Unlike planned trips,
 * waypoints have no date to sort by, so they're shown newest-first (the order
 * [com.zynergylabs.forager.app.domain.GetWaypointsUseCase] already returns — see that use case for why).
 *
 * [errorMessage] takes priority over the list, same branch order [TripWindowsCard] uses for
 * [AvailabilityUiState.tripWindowsErrorMessage] — a failed load/add/remove is belief-changing (the
 * list on screen may not be what's actually saved), so it replaces the list rather than sitting
 * beside it.
 *
 * Scrolls itself ([Modifier.verticalScroll]) rather than depending on a scrollable caller — same
 * reasoning as [OfflineMapsPanel]'s own outer `Column`. Journal restructure Stage 1 moved this out
 * of the Tools drawer's `SearchControls`, which supplied that scroll, into [com.zynergylabs.forager.app.ui.log.RecordsTab]'s
 * flat `Column(fillMaxSize())`, which does not — this is now `WaypointsSection`'s only caller.
 *
 * **Delete is a swipe, with Undo (journal redesign J4).** Each row is a
 * [TwoStageSwipeRow] since J4b (L6: a short swipe reveals Delete, a full swipe deletes); [onDeleteWaypoint] asks the owning ViewModel for a *pending* delete
 * (`TrackRecordingViewModel.requestRemoveWaypoint`), which hides the row and shows the Undo snackbar
 * carrying the reference warning. The confirm dialog Journal Stage 2b added here (its 4b warning)
 * is gone with the trash icon: the warning moved into the snackbar (owner ruling "In the Undo
 * snackbar (Recommended)"), and the delete itself waits for the snackbar to end.
 */
@Composable
internal fun WaypointsSection(
    waypoints: List<Waypoint>,
    errorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Journal redesign J5c: a tap on a closed row opens that waypoint's details sheet, given its id.
     * A tap on an open row closes it instead ([TwoStageSwipeRow]'s overlay takes that tap). `null`,
     * the default, leaves the rows without a tap.
     */
    onOpenWaypointDetails: ((String) -> Unit)? = null,
    /** Dispatch 2026-09-28-502: a row's Navigate, given the waypoint's id; `null` (the default) offers none. */
    onNavigateToWaypoint: ((String) -> Unit)? = null,
) {
    // J4b L6: one open row at a time, and a touch elsewhere on the list closes it.
    val swipeGroup = rememberSwipeRevealGroup()
    // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"; motion/ListMotion.kt; scout P2): a deleted waypoint's row
    // fades and shrinks while the rows below close up, and Undo brings it back the way it went. The last one leaves before the
    // empty text shows.
    val rows = rememberListRows(waypoints, key = { it.id })
    Column(
        modifier = modifier.swipeRevealTouchWatcher(swipeGroup).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when {
            errorMessage != null -> Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            rows.isEmpty() -> Text(
                "No waypoints dropped yet. Tap the add button on the map to drop one.",
                style = MaterialTheme.typography.bodySmall,
            )

            else -> rows.forEach { row ->
                key(row.key) { ListRowMotion(row) { waypoint ->
                    // J4b L6: two-stage swipe. No Edit: nothing in the app edits a waypoint after
                    // it is dropped (WaypointRepository.save's only caller is CreateWaypointUseCase).
                    TwoStageSwipeRow(
                        testTag = swipeToDeleteTag(RecordType.WAYPOINTS, waypoint.id),
                        rowKey = waypoint.id,
                        group = swipeGroup,
                        onDelete = { onDeleteWaypoint(waypoint.id) },
                        onEdit = null,
                        // The waypoint card's own shape, for the close tap's highlight (Amendment 1 to motion Part 1, RECORD -657).
                        highlightShape = CardDefaults.shape,
                    ) {
                        WaypointRow(
                            waypoint = waypoint,
                            onClick = onOpenWaypointDetails?.let { open -> { open(waypoint.id) } },
                            onNavigate = onNavigateToWaypoint?.let { navigate -> { navigate(waypoint.id) } },
                        )
                    }
                } }
            }
        }
    }
}

/**
 * One saved waypoint: its user-chosen [Waypoint.name] as the primary identifying text, the same
 * MGRS-plus-decimal-degrees coordinate display [PlannedTripRow] uses, and a "Directions" action
 * ([launchDirections]) reusing the exact same `geo:` intent machinery. No delete control of its
 * own since journal redesign J4: its callers wrap it in a [TwoStageSwipeRow] (J4b L6).
 *
 * [onClick] (journal redesign J5c) is what a tap on the card opens, the waypoint's details sheet, or
 * `null` for no card tap. The All logbook passes `null` and puts the tap on its badged row instead,
 * so the type badge takes it too. Directions keeps its own tap either way.
 *
 * [onNavigate] (dispatch 2026-09-28-502, plan task T9) is the app's own navigation to this waypoint, an
 * icon button before Directions; `null` leaves the row with Directions only. Like Directions it takes
 * its own tap, not the card's.
 */
@Composable
internal fun WaypointRow(waypoint: Waypoint, onClick: (() -> Unit)? = null, onNavigate: (() -> Unit)? = null) {
    val context = LocalContext.current
    val location = LatLng(waypoint.lat, waypoint.lng)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(CardDefaults.shape).opensRecordDetails(waypoint.name, onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(waypoint.name, style = MaterialTheme.typography.titleSmall)
                when (val mgrs = MgrsConverter.convert(location)) {
                    is MgrsCoordinate.Grid -> Text(mgrs.value, style = MaterialTheme.typography.bodySmall)
                    // No line at all rather than a wrong or truncated one — see PlannedTripRow's
                    // own use of the same MgrsCoordinate branch for why.
                    is MgrsCoordinate.Unsupported -> Unit
                }
                Text(decimalDegreesLabel(waypoint.lat, waypoint.lng), style = MaterialTheme.typography.bodySmall)
            }
            onNavigate?.let { navigate ->
                BouncingIconButton(onClick = navigate, modifier = Modifier.testTag(waypointRowNavigateTag(waypoint.id))) {
                    Icon(Icons.Filled.Navigation, contentDescription = "Navigate to ${waypoint.name}")
                }
            }
            BouncingIconButton(onClick = { launchDirections(context, waypoint.name, location) }) {
                Icon(Icons.Filled.Directions, contentDescription = "Directions to ${waypoint.name}")
            }
        }
    }
}

/** Dispatch 2026-09-28-502: a waypoint row's Navigate button. */
internal fun waypointRowNavigateTag(waypointId: String): String = "waypoint-row-navigate-$waypointId"

/**
 * A point in decimal degrees to four places, "45.3260, -122.6340": how [WaypointRow] and the offline
 * region row print coordinates, and (journal redesign J5c) how the details sheet prints them, so the
 * sheet shows the coordinates exactly as the row does.
 */
internal fun decimalDegreesLabel(lat: Double, lng: Double): String = "${"%.4f".format(lat)}, ${"%.4f".format(lng)}"
