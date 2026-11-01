package com.zynergylabs.forager.app.ui.route

import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.formatElevationChange
import com.zynergylabs.forager.app.domain.model.shownDistanceMeters
import com.zynergylabs.forager.app.domain.model.wholeLength
import com.zynergylabs.forager.app.domain.route.RouteElevation

/*
 * The route card's words (dispatch 2026-10-30-08, Amendment 6, RECORD -1887), all the owner's: "1 Waypoint selected, tap
 * another to route", "to", "Waypoints routed", "Elevation:". Amendment 12 (RECORD -1910) took "N Waypoints selected" off the card.
 * Pure Kotlin, so each is tested headless (RouteCardTextTest).
 */

/** The total line. A route has it from its first leg, so from two waypoints; it is never singular. */
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

/**
 * Amendment 10 (RECORD -1904, the owner: "Yes, add up what's shown (Recommended)"): the whole route's distance is the sum of the
 * rows' shown, rounded figures, so the digits add up: rows of 0.4, 0.4 and 0.4 mi total "1.2 mi", where the metres summed read
 * 1.3 (the S22 phone checks). Each row is taken at its shown figure ([shownDistanceMeters]), and the sum is printed as any distance
 * is. So rows in feet that reach a quarter mile total in miles, and a row in feet beside rows in miles counts as its feet.
 */
fun routeTotalDistanceText(legMeters: List<Double>, distanceUnit: DistanceUnit): String =
    formatDistanceMeters(legMeters.sumOf { shownDistanceMeters(it, distanceUnit) }, distanceUnit)

/**
 * The whole route's line, in [routeElevationText]'s form: each figure is the sum of the rows' shown whole numbers in the Units
 * setting (Amendment 10), so the digits add up; `null` when there are no legs or any has no elevation, as the total only shows
 * when every leg has one. A net change that adds up to nothing reads "+0".
 */
fun routeTotalElevationText(legs: List<RouteElevation?>, unitSystem: UnitSystem): String? {
    if (legs.isEmpty()) return null
    val each = legs.map { it ?: return null }
    val net = each.sumOf { wholeLength(it.netMeters, unitSystem) }
    val climb = each.sumOf { wholeLength(it.climbMeters, unitSystem) }
    val descent = each.sumOf { wholeLength(it.descentMeters, unitSystem) }
    val unit = if (unitSystem == UnitSystem.METRIC) "m" else "ft"
    return "Elevation: ${if (net >= 0) "+" else ""}$net $unit (↑ $climb ↓ $descent)"
}

/** The line at one stop (it replaces "Tap the next waypoint"). */
const val ONE_WAYPOINT_SELECTED = "1 Waypoint selected, tap another to route"
