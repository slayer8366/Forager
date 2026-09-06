package com.forager.app.domain

import com.forager.app.domain.model.WaypointDesignation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The *default* name of an auto-created origin/end waypoint — "Start · Sep 5, 9:41 AM",
 * "End · Sep 5, 11:02 AM" — the owner's own display naming. Only a default: the user may rename it,
 * and nothing navigational ever reads it back (see [WaypointDesignation] for why that is a field).
 * [zone] is a parameter so a test can pin the wall-clock text; production passes the device zone.
 */
fun autoWaypointName(designation: WaypointDesignation, epochMillis: Long, zone: ZoneId): String {
    val label = when (designation) {
        WaypointDesignation.ORIGIN -> "Start"
        WaypointDesignation.END -> "End"
    }
    val time = AUTO_WAYPOINT_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    return "$label · $time"
}

private val AUTO_WAYPOINT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
