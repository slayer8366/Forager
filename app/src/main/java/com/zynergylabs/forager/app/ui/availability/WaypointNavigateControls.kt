package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin

/**
 * What the compact scaffold hands its two tabs for navigating to a chosen waypoint (dispatch
 * 2026-09-28-502), gathered in one parameter rather than six through a scaffold that already takes over a
 * hundred. `AvailabilityScreen` owns all of it:
 * - [isNavigatingToWaypoint]: the Maps tab's HUD is the straight-line one, and the map draws the dashed
 *   line, not the return's.
 * - [straightLine]: that dashed line, current or kept faded after the fix was lost (the owner's "Keep the last
 *   line, faded"), worked out and held in `AvailabilityScreen` so it outlives the Maps tab leaving composition.
 * - [onNavigate]: Records' Navigate (its rows and its details sheet), with where it was tapped. The Maps
 *   tab's bubbles reach the same callback through `MapRecordSources.onNavigateToWaypoint`.
 * - [mapReopen] and [onMapReopenConsumed]: Back ended a navigation started from the Maps tab's bubble or its
 *   details sheet, which the Maps tab opens again, once.
 * - [recordsReopenDetails] and [onRecordsReopenConsumed]: likewise for the details sheet opened in Records,
 *   by the waypoint's id.
 */
internal data class WaypointNavigateControls(
    val isNavigatingToWaypoint: Boolean = false,
    val straightLine: StraightLine? = null,
    val onNavigate: ((WaypointNavigationOrigin) -> Unit)? = null,
    val mapReopen: WaypointNavigationOrigin? = null,
    val onMapReopenConsumed: () -> Unit = {},
    val recordsReopenDetails: String? = null,
    val onRecordsReopenConsumed: () -> Unit = {},
)
