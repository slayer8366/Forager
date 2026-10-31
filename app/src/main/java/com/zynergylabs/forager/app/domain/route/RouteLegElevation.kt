package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.bankedClimb

/*
 * The route card's elevation lines while plotting (dispatch 2026-10-30-08, Amendment 8, RECORD -1896), from the trail data's
 * heights on each leg's trail part ([RouteLeg.Trail.trailHeightsMetres], from `TrailRoute.heightsMetres`, #447). Pure Kotlin.
 *
 * - **Net change:** the last height minus the first.
 * - **Climb and descent:** [bankedClimb], the function the way home's climbing allowance uses, which banks exactly as the
 *   journal's Climb and Descent do (`ComputeTrackStatisticsUseCase`, `ElevationHysteresis.THRESHOLD_METERS`, 4 m).
 * - **The total:** every leg's heights in walking order, banked as one run, so a change still pending at one leg's end is
 *   counted in the next. Both ends of a join between legs are the same trail data's heights, so nothing is offset there.
 *
 * Missing heights are never 0 and never invented:
 * - a straight leg, a leg with no heights, and a leg with only some have no elevation. The last is the rule proposed in the
 *   PR body before building, the same one the way home uses for the same data (`trailDataHeights`, the owner's ruling P1 on
 *   #447): counting from the heighted points would read short without saying so, since a missing height resets the banking.
 * - the total only when every leg has one.
 *
 * The joins from each stop to the trail carry no heights (a stop is a name and a position), so a leg's elevation is its trail
 * part's. A saved route stores no heights until step 3b, so this is for plotting only.
 */

/** [leg]'s elevation, or `null` when it is straight or any point of its trail part has no height. */
fun legElevation(leg: RouteLeg): RouteElevation? = heightsOf(leg)?.let(::elevationOf)

/** The whole route's elevation over [legs], banked across them; `null` when there are none or any leg has no elevation. */
fun routeElevation(legs: List<RouteLeg>): RouteElevation? {
    if (legs.isEmpty()) return null
    val heights = legs.map { heightsOf(it) ?: return null }
    return elevationOf(heights.flatten())
}

private fun heightsOf(leg: RouteLeg): List<Double>? = when (leg) {
    is RouteLeg.Straight -> null
    is RouteLeg.Trail -> leg.trailHeightsMetres.takeIf { heights -> heights.none { it == null } }?.map { it!! }
}

private fun elevationOf(heights: List<Double>): RouteElevation {
    val banked = bankedClimb(heights)
    return RouteElevation(heights.last() - heights.first(), banked.climbMeters, banked.descentMeters)
}
