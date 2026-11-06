package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The *default* name of an auto-created origin/end waypoint — "Start · Oct 7, 2026, 9:41 AM", or "Start · Oct 7, 2026, 09:41"
 * when [is24HourClock] — the owner's own display naming. Only a default: the user may rename it, and nothing navigational ever
 * reads it back (see [WaypointDesignation] for why that is a field). [zone] is a parameter so a test can pin the wall-clock text;
 * production passes the device zone, and the phone's 12/24-hour setting.
 *
 * Data part D (dispatch -697 Amendment 4, RECORD -703; the owner: "New ones follow the new style (Recommended)"): the date reads
 * "Oct 7, 2026" like every date in the app and the time follows the phone. It was "Sep 5, 9:41 AM", always 12-hour. Names already
 * saved keep the text they were given; only new waypoints take this. The pattern is the app's shared one
 * (`ui/format/DisplayDates.kt`), restated here because the domain layer does not depend on `ui`; the US locale it always used is
 * kept, since the name is stored text.
 */
fun autoWaypointName(designation: WaypointDesignation, epochMillis: Long, zone: ZoneId, is24HourClock: Boolean): String {
    val label = when (designation) {
        WaypointDesignation.ORIGIN -> "Start"
        WaypointDesignation.END -> "End"
    }
    val format = if (is24HourClock) AUTO_WAYPOINT_TIME_FORMAT_24 else AUTO_WAYPOINT_TIME_FORMAT_12
    val time = format.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    return "$label · $time"
}

private val AUTO_WAYPOINT_TIME_FORMAT_12: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)
private val AUTO_WAYPOINT_TIME_FORMAT_24: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", Locale.US)
