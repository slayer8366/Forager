package com.zynergylabs.forager.app.domain.route

/**
 * A route while it is being plotted (dispatch 2026-10-30-08, step 3): the stops tapped so far and the legs routed so far.
 * Pure Kotlin, so the plotting rules are tested without a screen. A [PlottedRoute] is made from it only once every leg is in
 * ([toRoute]).
 *
 * Legs are routed one at a time, in order: [nextLeg] is the one to route now, and [withLeg] adds it. A stop is added at once
 * when it is tapped, so the stops can run ahead of the legs while a leg is being routed.
 */
data class RoutePlotDraft(val stops: List<RouteStop> = emptyList(), val legs: List<RouteLeg> = emptyList()) {

    /** [stop] added at the end, or `null` when it is at the last stop's position (the owner: "Ignore the second tap"). */
    fun withStop(stop: RouteStop): RoutePlotDraft? = this // STUB

    /** The two stops of the leg to route next, or `null` when every leg is in. */
    val nextLeg: Pair<RouteStop, RouteStop>? get() = null // STUB

    /** [leg] added as [nextLeg]. Refused when there is no leg to add or [leg] does not run between its two stops. */
    fun withLeg(leg: RouteLeg): RoutePlotDraft = this // STUB

    /** The last stop removed, with its leg (Undo). The draft unchanged when it has no stops. */
    fun undo(): RoutePlotDraft = this // STUB

    /** The route, once there are two stops or more and every leg is in; `null` before. */
    fun toRoute(): PlottedRoute? = null // STUB

    /** The length of the legs routed so far. A leg still being routed is not counted. */
    val routedMeters: Double get() = 0.0 // STUB
}

/**
 * The name a saved route is offered (RECORD -1845): "<first> and back" when the last stop is at the first stop's position
 * (the planner's ruling on Q10: by position, not by name), else "<first> to <last>".
 */
fun defaultRouteName(stops: List<RouteStop>): String = "" // STUB
