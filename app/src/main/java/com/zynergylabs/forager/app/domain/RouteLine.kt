package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The way back as the map draws it while returning (dispatch 2026-09-28-497, plan task T7, the
 * planner's ruling B). [atReturn] is the whole route as it was when Return was tapped, drawn dimmed
 * and grey; [ahead] is the latest route from the walker's assumed place to the start, drawn bright
 * over it. Whatever of the line at Return the bright line no longer covers is what the walker has
 * passed: no projection onto the route, by the owner's ruling for the route home, and a detour's
 * abandoned stretch stays grey too. [aheadIsCurrent] is false while the route is withheld: the last
 * line ahead stays, faded (the owner: "having your tracks disappear is a scary thing").
 */
data class RouteLine(
    val atReturn: List<LatLng>,
    val ahead: List<LatLng>,
    val aheadIsCurrent: Boolean,
)

/**
 * The line after [routeHome]: the first route of a return becomes the line at Return, later routes
 * move only the line ahead, a withheld route keeps the last line ahead and marks it faded (and draws
 * nothing when there is no line yet), and no result keeps what there is. The caller clears the line
 * when the return ends.
 */
fun nextRouteLine(previous: RouteLine?, routeHome: RouteHome?): RouteLine? = when (routeHome) {
    null -> previous
    is RouteHome.Ahead -> RouteLine(
        atReturn = previous?.atReturn ?: routeHome.route,
        ahead = routeHome.routeAhead,
        aheadIsCurrent = true,
    )
    is RouteHome.Withheld -> previous?.copy(aheadIsCurrent = false)
}
