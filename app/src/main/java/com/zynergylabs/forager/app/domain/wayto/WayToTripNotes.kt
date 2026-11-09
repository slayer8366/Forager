package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.NavigationTargetKind

/**
 * This note's words for a navigation to a target of [kind] (dispatch 2026-11-05-05, Amendment 1, A8; RECORD -2148): for a
 * trip, "each existing message that names "the waypoint" names "the trip" instead, otherwise identical"; for a waypoint, and
 * for every note that names no waypoint, [WayToWaypointNote.text] exactly. Kept here, beside the notes, and in its own file,
 * so the notes' enum is unchanged.
 */
fun WayToWaypointNote.textFor(kind: NavigationTargetKind): String = when (kind) {
    NavigationTargetKind.WAYPOINT -> text
    NavigationTargetKind.TRIP -> when (this) {
        WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL -> "The trip is far from any trail · straight line"
        WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA -> "The trip is in another area · straight line"
        WayToWaypointNote.WAY_IN_BLOCKED -> "No clear way in to the trip · straight line"
        else -> text
    }
}
