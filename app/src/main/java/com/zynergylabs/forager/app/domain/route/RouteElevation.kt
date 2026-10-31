package com.zynergylabs.forager.app.domain.route

/**
 * A leg's or a whole route's elevation, in metres: the net change from its start to its end, then the climb and the descent
 * along it (dispatch 2026-10-30-08, Amendment 6, RECORD -1887; the owner: "Both", net change then climb and descent).
 *
 * The seam for the follow-up on #447: heights reach the app only through `TrailRoute.heightsMetres`, which is not merged, so
 * nothing makes one of these yet and the route card shows no elevation line. When the follow-up fills one in per leg and for
 * the total, the card shows it in the place it already has; no layout change is needed then.
 */
data class RouteElevation(val netMeters: Double, val climbMeters: Double, val descentMeters: Double)
