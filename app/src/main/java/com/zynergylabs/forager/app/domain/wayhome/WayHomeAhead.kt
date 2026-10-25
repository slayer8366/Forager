package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.HopBand
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
fun wayHomeAhead(line: WayHomeLine, progress: WayHomeProgress, current: LatLng, generation: Long): RouteHome = when (progress) {
    is WayHomeProgress.OffLine -> RouteHome.Withheld(RouteWithheldReason.OFF_ROUTE, hopBand = HopBand.FAR)
    is WayHomeProgress.Placed -> {
        val place = progress.place
        val nodes = line.nodes
        // The place is the earliest node near the walker, so it can be up to 25 m behind them: right for the distance,
        // which must never be short, wrong for the needle, which would aim back at it (and the corner guard would hold
        // it there). The needle aims from the node nearest the walker within the next stretch of line instead.
        val aimFrom = aimFrom(line, place.index, current)
        val chosen = lookaheadIndex(nodes, line.alongMeters, aimFrom, line.alongMeters[aimFrom] + ROUTE_LOOKAHEAD_METERS, current)
        RouteHome.Ahead(
            lookahead = nodes[chosen],
            routeMeters = place.remainingMeters,
            hopBand = place.hopBand,
            lookaheadAlongRouteMeters = line.alongMeters[chosen],
            aimsAtRouteEnd = chosen == nodes.lastIndex,
            pathHome = place.toPathHome(pointCount = nodes.size),
            route = nodes,
            routeAhead = nodes.subList(place.index, nodes.size),
            aheadParts = partsFrom(line, place.index),
            generation = generation,
        )
    }
}

/**
 * The node the needle aims from: of the nodes from [placeIndex] to [WAY_HOME_AIM_WINDOW_METERS] further along, the one
 * nearest [current]. For aiming only; the distance home is the place's.
 */
internal fun aimFrom(line: WayHomeLine, placeIndex: Int, current: LatLng): Int {
    val limit = line.alongMeters[placeIndex] + WAY_HOME_AIM_WINDOW_METERS
    var best = placeIndex
    var bestMeters = Double.POSITIVE_INFINITY
    var i = placeIndex
    while (i < line.nodes.size && line.alongMeters[i] <= limit) {
        val meters = GeoDistance.metersBetween(current, line.nodes[i])
        if (meters < bestMeters) {
            best = i
            bestMeters = meters
        }
        i++
    }
    return best
}

/** How far along the line from the place the needle may aim from: twice the 25 m the place can lie behind the walker. */
internal const val WAY_HOME_AIM_WINDOW_METERS = 2 * WAY_HOME_TRAIL_JOIN_METERS

/** The line from node [from] to its end, as runs of one kind each; neighbouring runs share their joining node. */
internal fun partsFrom(line: WayHomeLine, from: Int): List<RouteLinePart> {
    val runs = ArrayList<RouteLinePart>()
    var start = from
    for (i in from + 1..line.nodes.lastIndex) {
        if (i == line.nodes.lastIndex || line.kinds[i] != line.kinds[start]) {
            runs += RouteLinePart(line.kinds[start], line.nodes.subList(start, i + 1))
            start = i
        }
    }
    return runs
}
