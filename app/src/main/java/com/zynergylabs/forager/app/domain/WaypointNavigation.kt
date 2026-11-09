package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Waypoint

/*
 * Navigating to a chosen waypoint (dispatch 2026-09-28-502, plan tasks T8 and T9). The step path the
 * owner confirmed, with Amendment 1 (RECORD.md -503):
 * 1. Tap a waypoint, then "Navigate" in its bubble, its details sheet, or Records; with or without a
 *    recording.
 * 2. The navigation view starts as for a return; the HUD points at the waypoint with its straight-line
 *    distance; the navigation control shows the X in a circle, "Stop navigating".
 * 3. A straight dashed line from the walker to the waypoint.
 * 4. Arrival as at the start: the ring with a check and "Arrived"; navigation stays on until ended.
 * 5. Back or the X-circle ends it. Back goes back to where Navigate was tapped.
 * 6. A return under way is overruled while this is active, and picks up again when it ends.
 * 7. The off-track alert and the walked-path line belong to the return; neither runs meanwhile.
 * "Pick navigation back up": the chosen waypoint survives the app being closed by the phone.
 */

/**
 * The waypoint being navigated to, by id, and whether a return was paused for it ([resumesReturn]),
 * so that ending this navigation picks the return up again (step 6), including after the app was
 * closed and opened again.
 */
data class WaypointNavigation(val waypointId: String, val resumesReturn: Boolean)

/**
 * Where the current [WaypointNavigation] is kept across the process (Amendment 1, "Pick navigation
 * back up"). DataStore, not Room, by CLAUDE.md's rule: a single current value, cleared when the
 * navigation ends, that nothing will join to or filter against, so it reads as a flat setting. Its
 * own file, as each concern here has. `Result`-returning, like this project's other repositories.
 */
interface WaypointNavigationRepository {
    /** The navigation that was on when the app last wrote it, or `null` for none. */
    suspend fun getCurrent(): Result<WaypointNavigation?>

    /** Keeps [navigation], or clears it with `null`. */
    suspend fun setCurrent(navigation: WaypointNavigation?): Result<Unit>
}

/** No store at all: nothing is kept and nothing is read back. For a ViewModel built without one (tests). */
object NoStoredWaypointNavigation : WaypointNavigationRepository {
    override suspend fun getCurrent(): Result<WaypointNavigation?> = Result.success(null)
    override suspend fun setCurrent(navigation: WaypointNavigation?): Result<Unit> = Result.success(Unit)
}

/**
 * What navigating to a waypoint needs of the return (step 6): whether one is under way, and pausing
 * and resuming it. Pausing is the return's own Stop and resuming its own Return, so the off-track rule
 * and the walked-path line are untouched: they simply do not run while the return is off (step 7).
 * The resumed return captures its line afresh (the planner's ruling, -502 Amendment 1).
 */
interface ReturnLeg {
    val isReturning: Boolean
    fun pause()
    fun resume()
}

/** No return to pause: for a ViewModel built without one (tests). */
object NoReturnLeg : ReturnLeg {
    override val isReturning: Boolean = false
    override fun pause() = Unit
    override fun resume() = Unit
}

/** What a [WaypointNavigation] resolves to against the waypoints the app has loaded. */
sealed interface WaypointNavigationTarget {
    /** No waypoint is being navigated to. */
    data object None : WaypointNavigationTarget

    /** One is, but the waypoints have not loaded yet, so whether it still exists is not known. */
    data object Waiting : WaypointNavigationTarget

    /** The waypoint, found. */
    data class Found(val waypoint: Waypoint) : WaypointNavigationTarget

    /** The waypoints have loaded and it is not among them: it was deleted. */
    data object Gone : WaypointNavigationTarget
}

/**
 * Resolves [navigation] against [waypoints]. Until [waypointsLoaded], an id that is not in the list
 * is [WaypointNavigationTarget.Waiting], never [WaypointNavigationTarget.Gone]: an empty list before
 * the first load is not a deletion.
 */
fun waypointNavigationTarget(navigation: WaypointNavigation?, waypoints: List<Waypoint>, waypointsLoaded: Boolean): WaypointNavigationTarget {
    if (navigation == null) return WaypointNavigationTarget.None
    val found = waypoints.firstOrNull { it.id == navigation.waypointId }
    return when {
        found != null -> WaypointNavigationTarget.Found(found)
        waypointsLoaded -> WaypointNavigationTarget.Gone
        else -> WaypointNavigationTarget.Waiting
    }
}
