package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.dropoff.GateResult
import com.zynergylabs.forager.app.domain.dropoff.LegGates
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import java.io.File

/**
 * Settles the two ends of a route [FindWayToWaypointUseCase] found (way-to dispatch 2026-10-28, PR 5; the owner's
 * ruling of 10-28 and go of ~15:10). The engine ends a route on the way nearest each end. This decides whether that
 * stands, or where the route must end instead, and whether the straight legs may be drawn.
 */
interface WayToEnds {
    /**
     * [route] runs from near [walker] to near [waypoint] on [routeData]. [reroute] asks the engine for a route from the
     * walker to another end, for a street drop-off.
     */
    suspend fun settle(walker: LatLng, waypoint: LatLng, route: TrailRoute, routeData: File, reroute: suspend (LatLng) -> TrailRouteResult): Settled

    sealed interface Settled {
        /** The route to draw: [route] as found, or one to a drop-off. Its legs passed every gate. */
        data class Route(val route: TrailRoute) : Settled

        /** No route may be drawn: [note] says why, [reason] says more for the log. */
        data class Refused(val note: WayToWaypointNote, val reason: String) : Settled
    }
}

/**
 * [WayToEnds] by the street drop-off ([StreetDropOff]) and the gates ([LegGates]):
 * - **The waypoint's end.** A street end ends the route at the chosen drop-off: [reroute] to it, then gate the leg from
 *   where that route actually ends. A trail end keeps the engine's end and gates its leg. No drop-off, a leg that fails,
 *   or a map that cannot vouch: refused, with its note, never a line (rule 4).
 * - **The walker's end.** The lead-in from the walker to the route's first point is gated the same way (the owner: the
 *   legs "can stay so long as they pass our determined gates"). The walker's own building, within 15 m, is theirs.
 */
class StreetDropOffEnds(
    private val mapData: ReadNearbyMapData,
    private val routeWays: RouteWays,
) : WayToEnds {

    override suspend fun settle(
        walker: LatLng,
        waypoint: LatLng,
        route: TrailRoute,
        routeData: File,
        reroute: suspend (LatLng) -> TrailRouteResult,
    ): WayToEnds.Settled {
        val near = mapData(waypoint, RADIUS_METERS).getOrElse { return notChecked("the map round the waypoint could not be read: ${it.message}") }
        val ways = routeWays.near(waypoint, RADIUS_METERS, routeData).getOrElse { return notChecked("the ways round the waypoint could not be read: ${it.message}") }
        val settled = when (val end = StreetDropOff.classify(waypoint, near, ways)) {
            is StreetDropOff.End.Unknown -> return notChecked("the waypoint's end: ${end.reason}")
            StreetDropOff.End.Trail -> when (val gates = LegGates.check(route.points.last(), waypoint, near)) {
                GateResult.Passes -> route
                else -> return refusedFor(gates, "the leg from the trail to the waypoint")
            }
            is StreetDropOff.End.Street -> when (val choice = StreetDropOff.choose(waypoint, end.building, near, ways)) {
                is StreetDropOff.Choice.NoneQualifies -> return refusedForAll(choice.tried)
                is StreetDropOff.Choice.Found -> {
                    val toDropOff = when (val r = reroute(choice.dropOff.at)) {
                        is TrailRouteResult.Found -> r.route
                        is TrailRouteResult.Failed -> return WayToEnds.Settled.Refused(WayToWaypointNote.NO_ROUTE_FOUND, "no route to the drop-off: ${r.failure}")
                    }
                    if (toDropOff.points.size < 2) return WayToEnds.Settled.Refused(WayToWaypointNote.NO_ROUTE_FOUND, "the route to the drop-off has ${toDropOff.points.size} points")
                    // The engine matches the drop-off to its nearest way, which is the street it lies on; whatever it
                    // matched, the leg actually drawn is the one gated.
                    val gates = LegGates.check(toDropOff.points.last(), waypoint, near, listOfNotNull(end.building))
                    if (gates != GateResult.Passes) return refusedFor(gates, "the leg from the drop-off")
                    toDropOff
                }
            }
        }
        return leadIn(walker, settled, routeData) ?: WayToEnds.Settled.Route(settled)
    }

    /** The lead-in's gates: null when it passes. */
    private suspend fun leadIn(walker: LatLng, route: TrailRoute, routeData: File): WayToEnds.Settled? {
        val first = route.points.first()
        val radius = GeoDistance.metersBetween(walker, first) + LEAD_IN_MARGIN_METERS
        val near = mapData(walker, radius).getOrElse { return notChecked("the map round the walker could not be read: ${it.message}") }
        val ways = routeWays.near(walker, radius, routeData).getOrElse { return notChecked("the ways round the walker could not be read: ${it.message}") }
        val own = (StreetDropOff.classify(walker, near, ways) as? StreetDropOff.End.Street)?.building
        return when (val gates = LegGates.check(walker, first, near, listOfNotNull(own))) {
            GateResult.Passes -> null
            is GateResult.Fails -> WayToEnds.Settled.Refused(WayToWaypointNote.LEAD_IN_BLOCKED, "the lead-in crosses ${gates.crossings}")
            is GateResult.Unchecked -> notChecked("the lead-in: ${gates.what.joinToString { "${it.what}: ${it.reason}" }}")
        }
    }

    private fun refusedFor(gates: GateResult, leg: String): WayToEnds.Settled.Refused = when (gates) {
        is GateResult.Fails -> WayToEnds.Settled.Refused(WayToWaypointNote.WAY_IN_BLOCKED, "$leg crosses ${gates.crossings}")
        is GateResult.Unchecked -> notChecked("$leg: ${gates.what.joinToString { "${it.what}: ${it.reason}" }}")
        GateResult.Passes -> error("a passing leg is not refused")
    }

    /** No drop-off qualified: blocked when every one tried failed on a crossing; not checked when any could not be vouched for. */
    private fun refusedForAll(tried: List<StreetDropOff.Tried>): WayToEnds.Settled.Refused {
        val unchecked = tried.mapNotNull { it.gates as? GateResult.Unchecked }
        val why = if (tried.isEmpty()) "no public street within ${StreetDropOff.STREET_METERS.toInt()} m" else tried.joinToString { "${it.rank} ${it.legMeters.toInt()} m: ${it.gates}" }
        return if (unchecked.isNotEmpty()) notChecked("no drop-off qualified: $why") else WayToEnds.Settled.Refused(WayToWaypointNote.WAY_IN_BLOCKED, "no drop-off qualified: $why")
    }

    private fun notChecked(reason: String) = WayToEnds.Settled.Refused(WayToWaypointNote.MAP_NOT_CHECKED, reason)

    companion object {
        /** Round the waypoint: the 150 m street search, plus the 300 m walk along access ways, which starts within 25 m. */
        const val RADIUS_METERS = 350.0

        /** Round the lead-in: its length plus room to see the walker's own building and the street near them. */
        const val LEAD_IN_MARGIN_METERS = 160.0
    }
}
