package com.zynergylabs.forager.app.ui.route

import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.formatElevationChange
import com.zynergylabs.forager.app.domain.model.wholeLength
import com.zynergylabs.forager.app.domain.route.RouteElevation

/*
 * The route card's words (dispatch 2026-10-30-08, Amendment 6, RECORD -1887), all the owner's: "1 Waypoint selected, tap
 * another to route", "Waypoints selected", "to", "Waypoints routed", "Elevation:". Singular "Waypoint" only for 1.
 * Pure Kotlin, so each is tested headless (RouteCardTextTest).
 */

/** The peek's line while plotting: the hint at one stop (it replaces "Tap the next waypoint"), then the count. */
fun waypointsSelectedText(count: Int): String = if (count == 1) ONE_WAYPOINT_SELECTED else "$count Waypoints selected"

/** The expanded list's last row. A route has it from its first leg, so from two waypoints; it is never singular. */
fun waypointsRoutedText(count: Int): String {
    require(count >= 2) { "a routed line is for two waypoints or more; got $count" }
    return "$count Waypoints routed"
}

/** A leg's row: "Creek to Ridge". */
fun routeLegText(from: String, to: String): String = "$from to $to"

/**
 * "Elevation: +240 ft (↑ 320 ↓ 80)": the net change, signed, then the climb and the descent, in the Units setting (the
 * owner: "Both"). The net change is [formatElevationChange]'s, as the Return control's; the climb and descent are the same
 * whole numbers without the unit, which the net change has already named.
 */
fun routeElevationText(elevation: RouteElevation, unitSystem: UnitSystem): String =
    "Elevation: ${formatElevationChange(elevation.netMeters, unitSystem)} " +
        "(↑ ${wholeLength(elevation.climbMeters, unitSystem)} ↓ ${wholeLength(elevation.descentMeters, unitSystem)})"

/** STUB (tests first): the whole route's distance, summed in metres and rounded once, as before. */
fun routeTotalDistanceText(legMeters: List<Double>, distanceUnit: DistanceUnit): String = formatDistanceMeters(legMeters.sum(), distanceUnit)

/** STUB (tests first): the whole route's elevation, summed in metres and rounded once, as before; `null` unless every leg has one. */
fun routeTotalElevationText(legs: List<RouteElevation?>, unitSystem: UnitSystem): String? {
    if (legs.isEmpty()) return null
    val each = legs.map { it ?: return null }
    return routeElevationText(RouteElevation(each.sumOf { it.netMeters }, each.sumOf { it.climbMeters }, each.sumOf { it.descentMeters }), unitSystem)
}

const val ONE_WAYPOINT_SELECTED = "1 Waypoint selected, tap another to route"
