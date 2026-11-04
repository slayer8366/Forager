package com.zynergylabs.forager.app.domain.routenav

/*
 * Navigating a saved route, stop by stop (dispatch 2026-11-03-03, RECORD -1988, -1990). The step path the owner confirmed (A1):
 * a saved route's card > Navigate > the app's own navigation toward the route's first stop, along the route line > the navigation
 * card shows the next stop and the distance along the route to it > within the arrival distance of a stop it moves on to the
 * next, with nothing to tap > after the last stop, "Arrived", on until ended, as for a waypoint.
 *
 * This file and RouteNavigationProgress.kt are the domain part (B2: free of Android UI, tested headless). The ViewModel and the
 * cards come after, behind A7's rulings.
 *
 * Keyed by position, not by waypoint id (the planner accepted Hazel's verify-first, -2047): a route's stops are copies of
 * waypoints as they were when it was plotted, a name and a position, with no id kept (`RouteStop`), so a stop is never matched
 * back to a waypoint in Records.
 */

/**
 * The route being navigated: the saved trip's id ([tripId], a `PlannedTrip` with a route), the stop headed to next by its index
 * in the route's stops ([nextStopIndex]), and whether a return was paused for it ([resumesReturn]), so that ending the navigation
 * picks the return up again, including after the app was closed, as [com.zynergylabs.forager.app.domain.WaypointNavigation]
 * does. This is the navigation as it runs; what is kept is split ([RouteNavigationCurrent] and each route's progress). [nextStopIndex] equal to the number of stops means every stop has been reached: "Arrived" (A1). Whether that index is in
 * range for the route is [routeNavigationTarget]'s to judge, since the route is not here.
 */
data class RouteNavigation(val tripId: String, val nextStopIndex: Int, val resumesReturn: Boolean) {
    init {
        require(nextStopIndex >= 0) { "a route navigation's next stop index is at least 0; got $nextStopIndex" }
    }
}

/**
 * The navigation that is on, as kept: the trip's id and whether a return was paused for it. The next stop is not here: each route
 * keeps its own ([RouteNavigationRepository.getProgress]), which outlives this.
 */
data class RouteNavigationCurrent(val tripId: String, val resumesReturn: Boolean)

/**
 * Where route navigation is kept across the process. DataStore, not Room, by CLAUDE.md's rule, as the waypoint's is: flat values
 * that nothing will join to or filter against. Its own file. `Result`-returning, like this project's other repositories.
 *
 * Two things are kept (A8, the owner: each route keeps its own next stop, which survives Stop):
 * - **the current navigation** ([getCurrent]), cleared when it ends, so closing the app mid-route picks it up again (A4);
 * - **each route's progress** ([getProgress]), the next stop it had not reached, kept through Stop so Navigate carries on from
 *   there, and cleared when the route is finished or deleted (A8).
 */
interface RouteNavigationRepository {
    /** The navigation that was on when the app last wrote it, or `null` for none. */
    suspend fun getCurrent(): Result<RouteNavigationCurrent?>

    /** Keeps [current], or clears it with `null`. Leaves every route's progress as it is. */
    suspend fun setCurrent(current: RouteNavigationCurrent?): Result<Unit>

    /** The next stop index kept for trip [tripId], or `null` for none kept (start at the first stop). */
    suspend fun getProgress(tripId: String): Result<Int?>

    /** Keeps [nextStopIndex] for trip [tripId], or clears it with `null`. */
    suspend fun setProgress(tripId: String, nextStopIndex: Int?): Result<Unit>
}

/** No store at all: nothing is kept and nothing is read back. For a ViewModel built without one (tests). */
object NoStoredRouteNavigation : RouteNavigationRepository {
    override suspend fun getCurrent(): Result<RouteNavigationCurrent?> = Result.success(null)
    override suspend fun setCurrent(current: RouteNavigationCurrent?): Result<Unit> = Result.success(Unit)
    override suspend fun getProgress(tripId: String): Result<Int?> = Result.success(null)
    override suspend fun setProgress(tripId: String, nextStopIndex: Int?): Result<Unit> = Result.success(Unit)
}
