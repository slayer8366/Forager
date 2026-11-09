package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlin.math.hypot

/**
 * The way home along the walked route, for the needle (dispatch 2026-09-28-417, way-back-route
 * Part 1; plan task T5). See [routeHome].
 */
sealed interface RouteHome {
    /**
     * The route is there. [lookahead] is the point the needle aims at: always a point of the walked
     * route, never off it. [routeMeters] is the distance home along the route, the same number
     * [PathHome.totalMeters] gives. [hopBand] is passed back as the next call's `previousHopBand`.
     * [lookaheadAlongRouteMeters] is how far along the route, from the most recent stored point,
     * the lookahead is. [aimsAtRouteEnd] is true when the route home is shorter than the lookahead
     * and the needle aims at the start itself. [pathHome] is the [PathHome] [routeMeters] is read
     * from, built from the same route search, so a caller that wants both needs one search, not two
     * (dispatch 2026-09-28-423, the planner's ruling on question 1).
     *
     * [route] is the whole route as a line, from the most recent stored point to the start (and the
     * origin waypoint when there is one), and [routeAhead] the part of it from the walker's assumed
     * place (the first route point at least `hop` metres along, the same place the lookahead is
     * measured from) to the end: the line the map draws while returning (dispatch 2026-09-28-497,
     * plan task T7). No projection onto the nearest point, by the same owner ruling.
     */
    data class Ahead(
        val lookahead: LatLng,
        val routeMeters: Double,
        val hopBand: HopBand,
        val lookaheadAlongRouteMeters: Double,
        val aimsAtRouteEnd: Boolean,
        val pathHome: PathHome,
        val route: List<LatLng> = emptyList(),
        val routeAhead: List<LatLng> = emptyList(),
    ) : RouteHome

    /**
     * No route to show, and why ([RouteWithheldReason]). [hopBand] is the band to pass back when
     * there was a hop to measure ([RouteWithheldReason.OFF_ROUTE]), so leaving the far band keeps
     * its hysteresis; `null` when there was none.
     */
    data class Withheld(val reason: RouteWithheldReason, val hopBand: HopBand?) : RouteHome
}

enum class RouteWithheldReason {
    /**
     * The walker is in [HopBand.FAR] of the most recent stored point: beyond 50 m, until back
     * under 45 m. The straight line they would have to walk to rejoin the walked route is longer
     * than the distance at which [pathHome] says a straight line stops standing in for walked
     * ground. Decision D5: withhold, "we don't know if it's going to be a straight line, they may
     * not see it before they turn".
     */
    OFF_ROUTE,

    /** The track has no point to route along. Never a straight-line stand-in. */
    NO_USABLE_POINTS,
}

/**
 * How far ahead along the route the needle aims, beyond where the walker must already be.
 * **Provisional.** The owner, 2026-10-02: "25m is fine. We can go with this at first and see how
 * well it behaves." It is the near end of the 25 to 50 m bracket set by [HOP_ENTER_ABOVE_METERS]
 * and [HOP_FAR_ENTER_ABOVE_METERS] (decision record, D1), chosen because a too-near lookahead
 * fails loudly, as a jittering needle, and a too-far one fails quietly, across a bend. Tuned on a
 * walk, here and nowhere else.
 */
const val ROUTE_LOOKAHEAD_METERS = 25.0

/**
 * The way home along the walked route: where the needle aims, and how far it is, or why there is
 * no route to show. Pure; the caller holds the hop band between calls, as [pathHome]'s callers do.
 *
 * ## The route
 *
 * The track as stored, joined to itself wherever it passes within [SELF_JOIN_EPSILON_METERS] of
 * itself, walked by the shortest route from the most recent stored point back to the first
 * ([joinedTrackRoute], the same search [pathHome]'s distance comes from), and then to the origin
 * waypoint when there is one. Decision D1: the needle aims at a point along this route, never at
 * the destination directly and never off the path. Decision D2: this is for the return to the
 * start only.
 *
 * Because the track goes on recording during the return, a detour the walker makes is recorded
 * and becomes part of the route home.
 *
 * ## Where the walker is on it
 *
 * As [pathHome] has it, at the **most recent stored point**, never projected onto the nearest
 * part of the track (owner ruling; [pathHome]'s "Where the walker is on the track"). The hop is
 * the straight line from the walker's fix to that point, banded with [nextHopBand], and
 * [HopBand.FAR] withholds the route.
 *
 * ## The lookahead: (hop + 25 m) along the route (the planner's ruling, option B)
 *
 * The stored track lags the walker. The service writes points every 30 s or every 20 kept points,
 * and the screen re-reads the track every 15 s, so the most recent stored point can be 30 to 45 s
 * behind the walker: at walking pace, 27 to 40 m, more than the lookahead itself. Measured from
 * that point alone, the lookahead would often land behind a walker heading home. The walker has
 * covered at least the straight-line hop of route since the stored point, so the lookahead is
 * measured from there: the first route point at least `hop + [ROUTE_LOOKAHEAD_METERS]` along the
 * route from the most recent stored point, or the route's end when the route is shorter.
 *
 * **Where it still lands behind, said plainly:** when the route winds hard inside the lag (the
 * walker has walked round a bend since the last stored point, so the straight-line hop is much
 * shorter than the route they covered), `hop + 25 m` is still behind them. `RouteHomeTest` shows
 * the case. It is left as the ruling chose it; the lag behind it is the service's batching.
 *
 * ## The corner guard (a second role for the self-join ε)
 *
 * The lookahead stops at the last route point for which **every route point from the walker's
 * assumed place (`hop` metres along) up to it** lies within [SELF_JOIN_EPSILON_METERS] of the
 * straight line from the walker. This is the 10 m self-join ruling reused in a second role, not a
 * separate decision: that ruling accepts crossing up to ε of ground the walker has not walked, and
 * no more, so the needle's line may stray from the walked route by up to ε and no more.
 * **Provisional, with the 25 m.** It keeps the needle from aiming across the inside of a bend,
 * which is the failure the decision record names as the one that costs something on a forest path.
 * As first worded it read from the most recent stored point, which pulled the lookahead back
 * behind the walker whenever the hop passed 10 m; the planner corrected it to read from the
 * walker's assumed place.
 *
 * **What that does to a walker beside the route, said plainly:** the rule assumes the walker is
 * `hop` metres along the route, not beside it. A walker standing 30 m to the side of the last
 * stored point is pointed about 45 m up the path. Also shown in `RouteHomeTest`, and also to be
 * checked on a walk.
 *
 * ## The distance
 *
 * [PathHome.totalMeters] for the same inputs, built from the same route search, so the HUD's
 * route figure and today's path-home figure cannot differ.
 *
 * ## Who calls it, and how often
 *
 * `TrackRecordingViewModel`'s route tick (dispatch 2026-09-28-423, plan task T6), every 5 s while
 * returning (decision D4), fed the track its 15 s poll last read, the last accuracy-gated fix and
 * the origin waypoint. That tick is also where the ViewModel's path-home figure now comes from,
 * [RouteHome.Ahead.pathHome], so there is one route search per tick and none in the poll.
 */
fun routeHome(track: Track, current: LatLng, origin: Waypoint?, previousHopBand: HopBand = HopBand.NONE): RouteHome {
    val points = track.points
    if (points.isEmpty()) return RouteHome.Withheld(RouteWithheldReason.NO_USABLE_POINTS, hopBand = null)

    val mostRecent = points.last()
    val hopMeters = GeoDistance.metersBetween(current, LatLng(mostRecent.lat, mostRecent.lng))
    val hopBand = nextHopBand(previousHopBand, hopMeters)
    if (hopBand == HopBand.FAR) return RouteHome.Withheld(RouteWithheldReason.OFF_ROUTE, hopBand)

    val joined = joinedTrackRoute(points)
    val first = points.first()
    val originLegMeters = origin?.let { GeoDistance.metersBetween(LatLng(first.lat, first.lng), LatLng(it.lat, it.lng)) }
    // The same arithmetic as pathHome, from the same search: the route figure is PathHome's.
    val distance = PathHome(
        trackMeters = joined.meters,
        hopMeters = hopMeters,
        hopBand = hopBand,
        originLegMeters = originLegMeters,
        pointCount = points.size,
        joinsOnRoute = joined.joinsOnRoute,
    )

    val nodes = joined.route.map { LatLng(points[it].lat, points[it].lng) } + listOfNotNull(origin?.let { LatLng(it.lat, it.lng) })
    val alongMeters = DoubleArray(nodes.size)
    for (i in 1 until nodes.size) alongMeters[i] = alongMeters[i - 1] + GeoDistance.metersBetween(nodes[i - 1], nodes[i])

    val last = nodes.lastIndex
    val walkerPlace = (0..last).firstOrNull { alongMeters[it] >= hopMeters } ?: last
    val wanted = (walkerPlace..last).firstOrNull { alongMeters[it] >= hopMeters + ROUTE_LOOKAHEAD_METERS } ?: last
    var chosen = walkerPlace
    for (k in walkerPlace + 1..wanted) {
        val keepsToTheRoute = (walkerPlace until k).all { offLineMeters(nodes[it], current, nodes[k]) <= SELF_JOIN_EPSILON_METERS }
        if (!keepsToTheRoute) break
        chosen = k
    }

    return RouteHome.Ahead(
        lookahead = nodes[chosen],
        routeMeters = distance.totalMeters,
        hopBand = hopBand,
        lookaheadAlongRouteMeters = alongMeters[chosen],
        aimsAtRouteEnd = chosen == last,
        pathHome = distance,
        route = nodes,
        routeAhead = nodes.subList(walkerPlace, nodes.size),
    )
}

/**
 * Metres from [point] to the straight segment from [from] to [to], on a flat local projection
 * about [from]. The distances here are tens of metres, where the flat projection's error is far
 * below the fixes' own.
 */
private fun offLineMeters(point: LatLng, from: LatLng, to: LatLng): Double {
    val (metersPerDegreeLat, metersPerDegreeLng) = GeoDistance.metersPerDegree(from.lat)
    val bx = (to.lng - from.lng) * metersPerDegreeLng
    val by = (to.lat - from.lat) * metersPerDegreeLat
    val px = (point.lng - from.lng) * metersPerDegreeLng
    val py = (point.lat - from.lat) * metersPerDegreeLat
    val lengthSquared = bx * bx + by * by
    val t = if (lengthSquared == 0.0) 0.0 else ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(px - t * bx, py - t * by)
}
