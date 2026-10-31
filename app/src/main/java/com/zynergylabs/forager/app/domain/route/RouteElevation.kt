package com.zynergylabs.forager.app.domain.route

/**
 * A leg's or a whole route's elevation, in metres: the net change from its start to its end, then the climb and the descent
 * along it (dispatch 2026-10-30-08, Amendment 6, RECORD -1887; the owner: "Both", net change then climb and descent).
 *
 * Amendment 6 made this the seam; Amendment 8 (RECORD -1896) fills it while plotting, from the trail data's heights
 * (`TrailRoute.heightsMetres`, #447), in `RouteLegElevation.kt`. A saved route has none until step 3b stores heights.
 */
data class RouteElevation(val netMeters: Double, val climbMeters: Double, val descentMeters: Double)
