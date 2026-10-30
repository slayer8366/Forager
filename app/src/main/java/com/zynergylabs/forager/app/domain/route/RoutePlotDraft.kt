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
    fun withStop(stop: RouteStop): RoutePlotDraft? =
        if (stops.lastOrNull()?.position == stop.position) null else copy(stops = stops + stop)

    /** The two stops of the leg to route next, or `null` when every leg is in. */
    val nextLeg: Pair<RouteStop, RouteStop>? get() = if (legs.size < stops.size - 1) stops[legs.size] to stops[legs.size + 1] else null

    /** [leg] added as [nextLeg]. Refused when there is no leg to add or [leg] does not run between its two stops. */
    fun withLeg(leg: RouteLeg): RoutePlotDraft {
        val (from, to) = requireNotNull(nextLeg) { "every leg of the draft is in" }
        require(leg.line.first() == from.position && leg.line.last() == to.position) { "the leg does not run from stop ${legs.size} to stop ${legs.size + 1}" }
        return copy(legs = legs + leg)
    }

    /** The last stop removed, with its leg (Undo). The draft unchanged when it has no stops. */
    fun undo(): RoutePlotDraft =
        if (stops.isEmpty()) this else RoutePlotDraft(stops.dropLast(1), legs.take((stops.size - 2).coerceAtLeast(0)))

    /** The route, once there are two stops or more and every leg is in; `null` before. */
    fun toRoute(): PlottedRoute? = if (stops.size >= 2 && nextLeg == null) PlottedRoute(stops, legs) else null

    /** The length of the legs routed so far. A leg still being routed is not counted. */
    val routedMeters: Double get() = legs.sumOf { it.meters }
}

/**
 * The name a saved route is offered (RECORD -1845): "<first> and back" when the last stop is at the first stop's position
 * (the planner's ruling on Q10: by position, not by name), else "<first> to <last>".
 */
fun defaultRouteName(stops: List<RouteStop>): String {
    val first = stops.first()
    val last = stops.last()
    return if (stops.size > 1 && last.position == first.position) "${first.name} and back" else "${first.name} to ${last.name}"
}
