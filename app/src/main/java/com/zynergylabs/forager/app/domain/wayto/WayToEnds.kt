package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.dropoff.GateResult
import com.zynergylabs.forager.app.domain.dropoff.LegGates
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff
import com.zynergylabs.forager.app.domain.mapdata.Coverage
import com.zynergylabs.forager.app.domain.mapdata.NearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
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
     * [route] runs from near [walker] to near [waypoint] on [routeData]. [walkerAccuracyMeters] is the accuracy the
     * walker's fix reports, null when it reports none. [reroute] asks the engine for a route between two other ends, for
     * a street drop-off at the waypoint's end or a pick-up at the walker's (dispatch 2026-11-04-04, A2).
     */
    suspend fun settle(
        walker: LatLng,
        walkerAccuracyMeters: Float?,
        waypoint: LatLng,
        route: TrailRoute,
        routeData: File,
        reroute: suspend (from: LatLng, to: LatLng) -> TrailRouteResult,
    ): Settled

    sealed interface Settled {
        /**
         * The route to draw: [route] as found, or one to a drop-off. Its legs passed every gate, except at the ends in
         * [byTrail]: those kept the engine's end with their leg ungated, as before the ends step, because the map there
         * could not be read or carries no fence data (A1 and A4 of dispatch 2026-11-04-04). The caller logs each one.
         */
        data class Route(val route: TrailRoute, val byTrail: List<ByTrail> = emptyList()) : Settled

        /** No route may be drawn: [note] says why, [reason] says more for the log. */
        data class Refused(val note: WayToWaypointNote, val reason: String) : Settled
    }

    /** An end that went by trail as today: [reason] for the log, with [cause] when a read failed. */
    data class ByTrail(val reason: String, val cause: Throwable? = null)
}

/**
 * [WayToEnds] by the street drop-off ([StreetDropOff]) and the gates ([LegGates]):
 * - **The waypoint's end.** A street end ends the route at the chosen drop-off: [reroute] to it, then gate the leg from
 *   where that route actually ends. A trail end keeps the engine's end and gates its leg. No drop-off, a leg that fails,
 *   or a map that cannot vouch: refused, with its note, never a line (rule 4).
 * - **The walker's end.** The lead-in from the walker to the route's first point is gated the same way (the owner: the
 *   legs "can stay so long as they pass our determined gates"). The walker's own building, within 15 m, is theirs.
 * - **By trail, as today** (dispatch 2026-11-04-04, A1 and A4). Before an end is classified or gated, the map round it
 *   is read. If that read fails (`mbgl-offline.db`, its TileJSON, or a tile that will not decode), or any stored tile
 *   within the end's reach has no barrier mark, that end keeps the engine's end with its leg ungated, and the reason
 *   goes back in [WayToEnds.Settled.Route.byTrail] for the log. The owner on A1: "In areas without fence data,
 *   Navigate works exactly as it does today (by trail), with no street drop-off. The drop-off only appears where the
 *   map can check for houses, fences and water. Nothing goes dark." On which tiles: "If any stored tile within the
 *   check's reach (about 350 m round the waypoint, or along your lead-in) has no fence data, that end goes by trail as
 *   today." On a read failure: "Same as an older map: Navigate goes by trail as today, with no drop-off, and the error
 *   is logged." The same for the route ways round the end (`.rd5`), read after the map (A5, the owner, 2026-11-04: an
 *   end whose route ways cannot be read also goes by trail, logged, like a store-read failure; a square with no file is
 *   a failure of [RouteWays.near] too). [WayToWaypointNote.MAP_NOT_CHECKED] is left for an end whose tiles are not stored.
 */
class StreetDropOffEnds(
    private val mapData: ReadNearbyMapData,
    private val routeWays: RouteWays,
) : WayToEnds {

    override suspend fun settle(
        walker: LatLng,
        walkerAccuracyMeters: Float?,
        waypoint: LatLng,
        route: TrailRoute,
        routeData: File,
        reroute: suspend (from: LatLng, to: LatLng) -> TrailRouteResult,
    ): WayToEnds.Settled {
        val byTrail = mutableListOf<WayToEnds.ByTrail>()
        val settled = when (val near = read("the waypoint's end", waypoint, RADIUS_METERS, routeData)) {
            is MapRead.ByTrail -> route.also { byTrail += near.why }
            is MapRead.Read -> when (val end = waypointEnd(waypoint, route, { to -> reroute(walker, to) }, near.data, near.ways)) {
                is WaypointEnd.Settled -> end.route
                is WaypointEnd.Refused -> return end.refused
            }
        }
        return when (val lead = leadIn(walker, settled, routeData)) {
            LeadIn.Passes -> WayToEnds.Settled.Route(settled, byTrail)
            is LeadIn.ByTrail -> WayToEnds.Settled.Route(settled, byTrail + lead.why)
            is LeadIn.Refused -> lead.refused
        }
    }

    /**
     * The map and the route ways round an end, or why that end goes by trail: the map read failed, a stored tile has no
     * barrier mark, or the route ways (`.rd5`) could not be read (A5).
     */
    private sealed interface MapRead {
        data class Read(val data: NearbyMapData, val ways: List<RouteWay>) : MapRead

        data class ByTrail(val why: WayToEnds.ByTrail) : MapRead
    }

    private suspend fun read(end: String, at: LatLng, radius: Double, routeData: File): MapRead {
        val data = mapData(at, radius).getOrElse { return MapRead.ByTrail(WayToEnds.ByTrail("$end: the map could not be read: ${it.message}", it)) }
        unmarkedTile(data)?.let { return MapRead.ByTrail(WayToEnds.ByTrail("$end: $it")) }
        val ways = routeWays.near(at, radius, routeData).getOrElse { return MapRead.ByTrail(WayToEnds.ByTrail("$end: the route ways could not be read: ${it.message}", it)) }
        return MapRead.Read(data, ways)
    }

    private sealed interface WaypointEnd {
        data class Settled(val route: TrailRoute) : WaypointEnd

        data class Refused(val refused: WayToEnds.Settled.Refused) : WaypointEnd
    }

    /** The waypoint's end on a map that can vouch for it: the drop-off for a street end, the gated leg for a trail end. */
    private suspend fun waypointEnd(
        waypoint: LatLng,
        route: TrailRoute,
        reroute: suspend (LatLng) -> TrailRouteResult,
        near: NearbyMapData,
        ways: List<RouteWay>,
    ): WaypointEnd {
        fun refused(r: WayToEnds.Settled.Refused) = WaypointEnd.Refused(r)
        val settled = when (val end = StreetDropOff.classify(waypoint, near, ways)) {
            is StreetDropOff.End.Unknown -> return refused(notChecked("the waypoint's end: ${end.reason}"))
            StreetDropOff.End.Trail -> when (val gates = LegGates.check(route.points.last(), waypoint, near)) {
                GateResult.Passes -> route
                else -> return refused(refusedFor(gates, "the leg from the trail to the waypoint"))
            }
            is StreetDropOff.End.Street -> when (val choice = StreetDropOff.choose(waypoint, end.building, near, ways)) {
                is StreetDropOff.Choice.NoneQualifies -> return refused(refusedForAll(choice.tried))
                is StreetDropOff.Choice.Found -> {
                    val toDropOff = when (val r = reroute(choice.dropOff.at)) {
                        is TrailRouteResult.Found -> r.route
                        is TrailRouteResult.Failed -> return refused(WayToEnds.Settled.Refused(WayToWaypointNote.NO_ROUTE_FOUND, "no route to the drop-off: ${r.failure}"))
                    }
                    if (toDropOff.points.size < 2) return refused(WayToEnds.Settled.Refused(WayToWaypointNote.NO_ROUTE_FOUND, "the route to the drop-off has ${toDropOff.points.size} points"))
                    // The engine matches the drop-off to its nearest way, which is the street it lies on; whatever it
                    // matched, the leg actually drawn is the one gated.
                    val gates = LegGates.check(toDropOff.points.last(), waypoint, near, listOfNotNull(end.building))
                    if (gates != GateResult.Passes) return refused(refusedFor(gates, "the leg from the drop-off"))
                    toDropOff
                }
            }
        }
        return WaypointEnd.Settled(settled)
    }

    private sealed interface LeadIn {
        data object Passes : LeadIn

        data class ByTrail(val why: WayToEnds.ByTrail) : LeadIn

        data class Refused(val refused: WayToEnds.Settled.Refused) : LeadIn
    }

    /** The lead-in's gates, or by trail as today where the map or the ways round the walker cannot vouch (A1, A4, A5). */
    private suspend fun leadIn(walker: LatLng, route: TrailRoute, routeData: File): LeadIn {
        val first = route.points.first()
        val radius = GeoDistance.metersBetween(walker, first) + LEAD_IN_MARGIN_METERS
        val (near, ways) = when (val end = read("the lead-in", walker, radius, routeData)) {
            is MapRead.ByTrail -> return LeadIn.ByTrail(end.why)
            is MapRead.Read -> end
        }
        val own = (StreetDropOff.classify(walker, near, ways) as? StreetDropOff.End.Street)?.building
        return when (val gates = LegGates.check(walker, first, near, listOfNotNull(own))) {
            GateResult.Passes -> LeadIn.Passes
            is GateResult.Fails -> LeadIn.Refused(WayToEnds.Settled.Refused(WayToWaypointNote.LEAD_IN_BLOCKED, "the lead-in crosses ${gates.crossings}"))
            is GateResult.Unchecked -> LeadIn.Refused(notChecked("the lead-in: ${gates.what.joinToString { "${it.what}: ${it.reason}" }}"))
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

    /**
     * Why [data] carries no fence data, or null when it does: the reason of the first stored tile with no barrier mark
     * (#439). A tile not stored is not counted here; it leaves the gates unchecked, which is [notChecked]'s, not by trail.
     */
    private fun unmarkedTile(data: NearbyMapData): String? =
        data.tiles.filter { it !in data.missingTiles }.firstNotNullOfOrNull { (data.fencesByTile[it] as? Coverage.NotInTileSet)?.reason }

    companion object {
        /** Round the waypoint: the 150 m street search, plus the 300 m walk along access ways, which starts within 25 m. */
        const val RADIUS_METERS = 350.0

        /** Round the lead-in: its length plus room to see the walker's own building and the street near them. */
        const val LEAD_IN_MARGIN_METERS = 160.0

        /** A2 STUB, tests first: the pick-up's fix limit, not yet read. */
        const val PICK_UP_FIX_ACCURACY_METERS = 15f
    }
}
