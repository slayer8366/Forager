package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint

/*
 * Navigating to a planned trip as itself (dispatch 2026-11-05-05, Amendment 1, A8; RECORD -2148, the owner: "As a trip
 * (Recommended)", "Navigate goes to the trip itself, with the same screen, the way along trails, the drop-off, and the
 * arrival. Back returns to the trip. ... nothing new appears in Records."): the navigation of WaypointNavigation.kt's step
 * path, with the trip as its target. Nothing is stored for it but the kept navigation's kind. The trip stays a trip: no
 * waypoint is made at its spot (the Phase 1 report's Option 2, not chosen).
 */

/** What a [WaypointNavigation]'s id names: a waypoint (every navigation kept before trips could be navigated to) or a planned trip. */
enum class NavigationTargetKind { WAYPOINT, TRIP }

/** The target a navigation follows, its id and kind without the return flag: what the line and the way along trails start and stop on. */
data class NavigationTargetRef(val id: String, val kind: NavigationTargetKind)

/** This navigation's target. */
val WaypointNavigation.targetRef: NavigationTargetRef get() = NavigationTargetRef(waypointId, kind)

/**
 * The trip as the navigation's target point: its id, name and spot, in the shape the HUD, the line, the arrival and the way
 * along trails take a target in. Each of them reads the id and the position only (NavigationReadout, NavigationArrival,
 * AvailabilityWaypointRoute, CompactMapArrivedAt; and `mapVisibleWaypoints` the id). A value in memory, never stored, so it
 * never reaches Records. No height (a trip stores none) and no designation (it is not a track's start or end). A trip has no
 * creation time, and nothing reads [Waypoint.createdAtEpochMillis] of a target, so it is 0, shown nowhere.
 */
fun PlannedTrip.asNavigationTarget(): Waypoint =
    Waypoint(id = id, lat = location.lat, lng = location.lng, altitude = null, name = name, note = "", createdAtEpochMillis = 0L)

/**
 * What [navigation] resolves to. A waypoint's resolves against [waypoints] exactly as before ([waypointNavigationTarget]),
 * the trips unread. A trip's resolves against [trips] only, never the waypoints: found, it is the point
 * [asNavigationTarget] makes; not found before [tripsLoaded], it is waited for (a kept navigation picked back up at start,
 * before the trips have been read, is not a deletion); not found after, it was deleted.
 */
fun navigationTargetOf(
    navigation: WaypointNavigation?,
    waypoints: List<Waypoint>,
    waypointsLoaded: Boolean,
    trips: List<PlannedTrip>,
    tripsLoaded: Boolean,
): WaypointNavigationTarget {
    if (navigation == null) return WaypointNavigationTarget.None
    if (navigation.kind == NavigationTargetKind.WAYPOINT) return waypointNavigationTarget(navigation, waypoints, waypointsLoaded)
    val found = trips.firstOrNull { it.id == navigation.waypointId }
    return when {
        found != null -> WaypointNavigationTarget.Found(found.asNavigationTarget())
        tripsLoaded -> WaypointNavigationTarget.Gone
        else -> WaypointNavigationTarget.Waiting
    }
}
