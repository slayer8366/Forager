package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Waypoint

/*
 * Navigating to a planned trip as itself (dispatch 2026-11-05-05, Amendment 1, A8; RECORD -2148, the owner: "As a trip
 * (Recommended)"): the same navigation as to a waypoint, with the trip as its target.
 */

/** What a [WaypointNavigation]'s id names. */
enum class NavigationTargetKind { WAYPOINT, TRIP }

/** STUB (dispatch 2026-11-05-05, tests first): the trip as the navigation's target point. Built in a later commit. */
fun PlannedTrip.asNavigationTarget(): Waypoint = TODO("STUB (dispatch 2026-11-05-05, tests first)")

/**
 * STUB (dispatch 2026-11-05-05, tests first): what [navigation] resolves to. Today's waypoint-only resolution, the trips
 * unread; built in a later commit.
 */
fun navigationTargetOf(
    navigation: WaypointNavigation?,
    waypoints: List<Waypoint>,
    waypointsLoaded: Boolean,
    @Suppress("UNUSED_PARAMETER") trips: List<PlannedTrip>,
    @Suppress("UNUSED_PARAMETER") tripsLoaded: Boolean,
): WaypointNavigationTarget = waypointNavigationTarget(navigation, waypoints, waypointsLoaded)
