package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.PathHome
import com.zynergylabs.forager.app.domain.ROUTE_LOOKAHEAD_METERS
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteLinePart
import com.zynergylabs.forager.app.domain.RouteWithheldReason
import com.zynergylabs.forager.app.domain.lookaheadIndex
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * The way home along trails as the HUD and the map take it (dispatch 2026-10-24-10 Amendment 1, PR B): the same
 * [RouteHome] the track home gives, so everything downstream reads one shape.
 *
 * - **The place** is [WayHomeProgress]'s, never the most recent stored point: the trail part is not the walker's track.
 * - **The figure** is [WayHomePlace.remainingMeters], the 25 m trail allowance included.
 * - **The needle** aims [ROUTE_LOOKAHEAD_METERS] along the line from the place, held to the line by the same corner
 *   guard as the track home ([lookaheadIndex]).
 * - **The line ahead** is the line from the place, in its dotted and solid runs ([RouteHome.Ahead.aheadParts]).
 * - **Off the line** by more than the far band, the route is withheld as the far hop withholds it today
 *   ([RouteWithheldReason.OFF_ROUTE]); that is also when the way home re-routes (`WayHomeWatch`).
 *
 * [RouteHome.Ahead.pathHome] carries the figure as `trackMeters` (the line's metres from the place, the allowance
 * included) and the hop: nothing in production reads it apart from the HUD's own figure, which is the same number.
 */
fun wayHomeAhead(line: WayHomeLine, progress: WayHomeProgress, current: LatLng, generation: Long): RouteHome = TODO("stub: failing first")

/** The line from node [from] to its end, as runs of one kind each; neighbouring runs share their joining node. */
internal fun partsFrom(line: WayHomeLine, from: Int): List<RouteLinePart> {
    TODO("stub: failing first")
}
