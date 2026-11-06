package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.TripDownloadArea
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.tripDownloadArea
import kotlinx.coroutines.flow.update

/*
 * Dispatch 2026-11-05-08 (RECORD -2167, -2171): a trip's Download Maps in the Trip Planner opens Offline Maps with the pin and
 * the name already set. The owner, verbatim: "Then tap Download Maps button from the Trip Planner menu to be taken to the Offline
 * Maps page with the coordinates already fixed into the Offline Maps, so all they have to do is adjust tile size and download."
 * This file is the state and the ViewModel's two calls; the icon, the note and the screen's wiring are in TripDownloadMapsUi.kt.
 */

/** What the Offline Maps page held, as the fields a download reads: put back when the user leaves without downloading (A2). */
data class OfflineMapsEntry(
    val latText: String,
    val lngText: String,
    val nameText: String,
    val radiusKm: Int,
    val radiusTouched: Boolean,
)

/**
 * The owner's two notes for a route bigger than one download, shown under "Pin at" (RECORD -2171, A3 and A6; the placement and
 * the second note are the owner's later rulings on this build: "Under "Pin at" (Recommended)", "Its own note (Recommended)").
 */
enum class TripAreaNote {
    /** A stop is outside the area at 24 km. */
    STOPS_OUTSIDE,

    /** Every stop is inside at 24 km, but part of the line between them is not (Amendment 1, C2). */
    TRAIL_OUTSIDE,
}

/**
 * A trip's Download Maps, held in [AvailabilityUiState.offlineMapsTripOpening] from the tap until the user leaves Offline Maps, or a
 * download from the page succeeds.
 */
data class TripMapsOpening(
    /** What the page held before the first trip's tap since the user last left it (A2: "the page puts back what it held before the tap"). */
    val before: OfflineMapsEntry,
    /** A route trip's starting radius; `null` for a plain trip, which has no note. */
    val startingRadiusKm: Int?,
    /** The note the starting area carries, if any. Shown only while that area stands (A6). */
    val note: TripAreaNote?,
)

/**
 * [trip]'s opening, from [area]. The pin and the Name box are the trip's (B2). A route trip's radius is its starting radius, and
 * is held as the user's own so a change of units leaves it ([AvailabilityUiState.offlineMapRadiusTouched]). A plain trip gets the
 * radius the page held before the first tap, "today's starting value" (B2). [AvailabilityUiState.offlineMapPinRequest] counts up,
 * so a page already on screen moves its pin here even after a pan (B3). A second trip tapped before leaving keeps the first
 * one's [TripMapsOpening.before], so leaving still puts back the page as the user left it.
 */
internal fun AvailabilityUiState.withTripOpened(trip: PlannedTrip, area: TripDownloadArea): AvailabilityUiState {
    val before = offlineMapsTripOpening?.before
        ?: OfflineMapsEntry(offlineMapLatText, offlineMapLngText, offlineMapNameText, offlineMapRadiusKm, offlineMapRadiusTouched)
    val route = area as? TripDownloadArea.AroundRoute
    val note = when {
        route == null -> null
        route.stopsOutside -> TripAreaNote.STOPS_OUTSIDE
        !route.lineCovered -> TripAreaNote.TRAIL_OUTSIDE
        else -> null
    }
    return copy(
        offlineMapLatText = area.center.lat.toString(),
        offlineMapLngText = area.center.lng.toString(),
        offlineMapNameText = trip.name,
        offlineMapRadiusKm = route?.radiusKm ?: before.radiusKm,
        offlineMapRadiusTouched = if (route != null) true else before.radiusTouched,
        offlineMapPinRequest = offlineMapPinRequest + 1,
        offlineMapsTripOpening = TripMapsOpening(before = before, startingRadiusKm = route?.radiusKm, note = note),
    )
}

/** The page as it was before the trip's tap (A2), or unchanged when no trip opened it. */
internal fun AvailabilityUiState.withTripForgotten(): AvailabilityUiState {
    val before = offlineMapsTripOpening?.before ?: return this
    return copy(
        offlineMapLatText = before.latText,
        offlineMapLngText = before.lngText,
        offlineMapNameText = before.nameText,
        offlineMapRadiusKm = before.radiusKm,
        offlineMapRadiusTouched = before.radiusTouched,
        offlineMapsTripOpening = null,
    )
}

/**
 * A trip's Download Maps in the Trip Planner: the Offline Maps page's pin, name and (for a route) radius are set for [trip]
 * ([withTripOpened]). Nothing downloads here (B4): the user still taps the page's own Download Maps and answers "Download this
 * area?". The screen opens the page itself, the Tools row's way (B2).
 */
fun AvailabilityViewModel.onDownloadMapsForTrip(trip: PlannedTrip) {
    val area = tripDownloadArea(trip)
    _uiState.update { it.withTripOpened(trip, area) }
}

/**
 * The user left Offline Maps after a trip's Download Maps. The owner, verbatim: "Forget the trip (Recommended)", whose option read
 * "Offline Maps opens just as it does today, as if the trip's button was never tapped. No trip name is left in the box to end up
 * on an unrelated download by mistake." A download that succeeded from the page has already ended the opening
 * ([AvailabilityOfflineMaps.beginDownload]), so its own clearing stands, as after any download.
 */
fun AvailabilityViewModel.onLeftTripOfflineMaps() {
    _uiState.update { it.withTripForgotten() }
}
