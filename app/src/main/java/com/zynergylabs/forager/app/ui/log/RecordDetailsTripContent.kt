package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.availability.decimalDegreesLabel
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * A planned trip's Details card (dispatch 2026-11-05-05; A4 as A9 rules it, RECORD -2148, the owner: "Leave both off
 * (Recommended)", "Name, the trip's date, MGRS and coordinates, then Navigate and Directions, centred. Nothing on it is
 * empty or always the same."): laid out as the waypoint's card ([WaypointDetails]), with no Created row (a trip stores no
 * time it was made) and no Used in row (no journal entry can keep a trip, so it could only ever read "no journal entries").
 *
 * - The date row is "Trip date" (A10, the owner: "Trip date (Recommended)"), in the bubble's and the Trip Planner row's
 *   form ([displayDate]); an undated trip (schema 23) has none.
 * - MGRS as the row and the bubble show it, and no line rather than a wrong one where the grid cannot say ([MgrsCoordinate]).
 * - The coordinates as the waypoint's card shows them (A6 keeps them on the cards).
 * - Navigate and Directions, centred ([NavigateDirectionsRow]), only where the card has Navigate ([onNavigate]).
 *
 * Everything shown is the trip as the Maps tab already holds it (`RecordDetailsSheet`'s rule): no read, column or migration.
 */
@Composable
internal fun TripDetails(trip: PlannedTrip, onNavigate: ((String) -> Unit)?) {
    DetailsTitle(trip.name)
    trip.date?.let { DetailField(FIELD_TRIP_DATE, TRIP_DATE_LABEL, displayDate(it)) }
    when (val mgrs = MgrsConverter.convert(trip.location)) {
        is MgrsCoordinate.Grid -> DetailField(FIELD_MGRS, "MGRS", mgrs.value)
        is MgrsCoordinate.Unsupported -> Unit
    }
    DetailField(FIELD_COORDINATES, "Coordinates", decimalDegreesLabel(trip.location.lat, trip.location.lng))
    onNavigate?.let { navigate -> NavigateDirectionsRow(onNavigate = { navigate(trip.id) }, name = trip.name, at = trip.location) }
}

/** The trip card's date row (A10, the owner: "Trip date (Recommended)", "Says plainly that it's the day the trip is planned for."). */
internal const val TRIP_DATE_LABEL = "Trip date"

internal const val FIELD_TRIP_DATE = "trip-date"
