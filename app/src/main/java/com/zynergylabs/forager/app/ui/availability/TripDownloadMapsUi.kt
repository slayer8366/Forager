package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.log.JournalTopTab
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * Dispatch 2026-11-05-08 (RECORD -2167, -2171): the screen's side of a trip's Download Maps. The state and the ViewModel's calls
 * are in TripDownloadMaps.kt.
 */

/** The two ViewModel calls a trip's Download Maps needs, for [AvailabilityScreen]; `MainActivity` passes the ViewModel's. */
class TripDownloadMapsActions(
    /** [AvailabilityViewModel.onDownloadMapsForTrip]. */
    val onOpen: (PlannedTrip) -> Unit,
    /** [AvailabilityViewModel.onLeftTripOfflineMaps]. */
    val onLeft: () -> Unit,
)

/**
 * A trip's Download Maps tap, from [AvailabilityScreen] down to the Trip Planner's rows; `null` (no provider: tests, other
 * callers) draws no icon. A composition local rather than a parameter so it reaches the rows without a change to
 * `AvailabilityScreenDrawerHost.kt`, `AvailabilitySettingsUi.kt` or `SearchControls.kt`, which other work was changing (the
 * planner's call, accepted for this build). Not static: the screen builds the lambda afresh on each composition, and a static
 * local would recompose everything under the drawer each time; this one recomposes only the list that reads it.
 */
internal val LocalDownloadMapsForTrip = compositionLocalOf<((PlannedTrip) -> Unit)?> { null }

/**
 * A trip's Download Maps (Amendment 1, A1; the owner: "A maps icon next to the navigate icon."): the bottom bar's Maps glyph, in
 * the same button as the row's Directions and Delete, so its size and colour are theirs (the planner's call, accepted). Left of
 * Directions on a plain trip, left of Delete on a route trip (A5: "Left of Directions (Recommended)").
 */
@Composable
internal fun TripDownloadMapsIcon(trip: PlannedTrip, onClick: () -> Unit) {
    BouncingIconButton(onClick = onClick, modifier = Modifier.testTag(tripDownloadMapsTag(trip.id))) {
        Icon(CompactTab.MAP.icon(), contentDescription = tripDownloadMapsLabel(trip))
    }
}

/** The icon's accessibility label, which keeps the owner's words (A1). */
internal fun tripDownloadMapsLabel(trip: PlannedTrip) = "Download Maps for ${trip.name}"

internal fun tripDownloadMapsTag(tripId: String) = "trip-download-maps-$tripId"

/**
 * A trip's tap: the ViewModel sets the page for the trip, then the Tools drawer's own Offline Maps row is run, so the page opens
 * where that row opens it and Back retraces the way in as from that row (B2: "no new Back handler"). `null` with no [actions].
 */
internal fun buildOpenTripOfflineMaps(actions: TripDownloadMapsActions?, openOfflineMapsFromTools: () -> Unit): ((PlannedTrip) -> Unit)? =
    actions?.let { a ->
        { trip ->
            a.onOpen(trip)
            openOfflineMapsFromTools()
        }
    }

/**
 * Forgets the trip once the user leaves Offline Maps (A2), the way `ToolsOfflineMapsReturnWatch` tells the user has moved on:
 * once the Journal has reached Records' Offline maps chip after the tap, any other tab, Journal tab or chip is leaving. Until it
 * has, the request is still on its way (JournalTab consumes it, then RecordsTab selects the chip, a frame or two later). Back
 * from the page is leaving too, since its first Back puts back the page the user came from.
 */
@Composable
internal fun TripOfflineMapsLeaveWatch(opened: Boolean, tabSelections: ScreenTabSelections, onLeft: (() -> Unit)?) {
    // STUB: tests first
}

/**
 * The owner's note for a route bigger than one download (A3, A6), shown while the trip's starting area stands: the pin has not
 * been moved ([pinMoved], the picker's own "touched") and the radius is still the starting one. A pin moved or a radius changed
 * is a different area, about which the note may not be true. Under "Pin at", after bug 14's line, in its style (the owner:
 * "Under "Pin at" (Recommended)"). At most one shows: [TripAreaNote.STOPS_OUTSIDE] wins over [TripAreaNote.TRAIL_OUTSIDE]
 * ([withTripOpened]).
 */
@Composable
internal fun TripAreaNoteLine(opening: TripMapsOpening?, radiusKm: Int, pinMoved: Boolean) {
    // STUB: tests first
}

/** The owner's words (A6), exactly. */
internal const val TRIP_STOPS_OUTSIDE_NOTE = "Some of this route's stops are outside this area. Download the rest as a second area."

/** The owner's words for C2 ("Its own note (Recommended)"), exactly. */
internal const val TRIP_TRAIL_OUTSIDE_NOTE = "Part of this route's trail is outside this area. Download the rest as a second area."

/** The note, for tests reading it. */
internal const val TRIP_AREA_NOTE_TAG = "trip-area-note"
