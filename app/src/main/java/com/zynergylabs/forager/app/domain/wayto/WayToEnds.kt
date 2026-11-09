package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.dropoff.GateResult
import com.zynergylabs.forager.app.domain.dropoff.LegGates
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff
import com.zynergylabs.forager.app.domain.mapdata.BuildingOutline
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
         * What each end became, for the log (dispatch 2026-11-05-07), on a route and on a refusal alike; null only from a
         * [WayToEnds] that records nothing, which the caller says.
         */
        val ends: WayToEndsRecord?

        /**
         * The route to draw: [route] as found, or one to a drop-off. Its legs passed every gate, except at the ends in
         * [byTrail]: those kept the engine's end with their leg ungated, as before the ends step, because the map there
         * could not be read or carries no fence data (A1 and A4 of dispatch 2026-11-04-04). The caller logs each one.
         */
        data class Route(val route: TrailRoute, val byTrail: List<ByTrail> = emptyList(), override val ends: WayToEndsRecord? = null) : Settled

        /** No route may be drawn: [note] says why, [reason] says more for the log. */
        data class Refused(val note: WayToWaypointNote, val reason: String, override val ends: WayToEndsRecord? = null) : Settled
    }

    /** An end that went by trail as today: [reason] for the log, with [cause] when a read failed. */
    data class ByTrail(val reason: String, val cause: Throwable? = null)
}

/**
 * [WayToEnds] by the street drop-off ([StreetDropOff]) and the gates ([LegGates]):
 * - **The waypoint's end.** A street end ends the route at the chosen drop-off. A trail end keeps the engine's end and
 *   gates its leg. No drop-off, a leg that fails, or a map that cannot vouch: refused, with its note, never a line
 *   (rule 4).
 * - **The walker's end** (dispatch 2026-11-04-04, A2 and A4). On a good fix ([PICK_UP_FIX_ACCURACY_METERS]) at a street
 *   end, the route starts at a pick-up, chosen exactly as the drop-off is ([StreetDropOff.choose], the walker as the
 *   pin); none qualifying is [WayToWaypointNote.LEAD_IN_BLOCKED] (A4: no new words). Otherwise the engine's start stands
 *   and the lead-in to it is gated (the owner: the legs "can stay so long as they pass our determined gates"). The
 *   walker's own building, within 15 m, is theirs.
 * - **One re-run** (A4, the planner: "settle both ends, then one two-ended re-run, so at most two engine runs"). Both
 *   ends are planned first, the waypoint's before the walker's, so a way in that is blocked is said before a lead-in that
 *   is. Then, if either end moved, [reroute] is asked once, from the pick-up (or the walker) to the drop-off (or the
 *   waypoint). Then the legs actually drawn are gated, from where that route really starts and ends: the engine matches
 *   a moved end to its nearest way, and whatever it matched, the drawn leg is the one checked.
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
 *   a failure of [RouteWays.near] too). On a good fix the read round the walker reaches [RADIUS_METERS], as a pick-up
 *   looks that far, so the tile check does too (A5, the planner accepting that reading).
 *   [WayToWaypointNote.MAP_NOT_CHECKED] is left for an end whose tiles are not stored.
 * - **The record** (dispatch 2026-11-05-07). Each end's plan carries what that end became ([WayToEndBecame]), and both go
 *   back on whatever [settle] returns, a route or a refusal ([WayToEnds.Settled.ends]), for the caller's one line. The
 *   record only describes the plan; nothing in it is read to decide anything.
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
        val far = waypointEnd(waypoint, route, routeData)
        if (far is EndPlan.Refused) return far.refused.copy(ends = WayToEndsRecord(walker = WayToEndBecame.NotReached, waypoint = far.became))
        val near = walkerEnd(walker, walkerAccuracyMeters, route, routeData)
        // Dispatch 2026-11-05-07: what both ends were planned as goes with whatever comes of them, a route or a refusal.
        val ends = WayToEndsRecord(walker = near.became, waypoint = far.became)
        return when (val settled = drawAndGate(walker, waypoint, route, far, near, reroute)) {
            is WayToEnds.Settled.Route -> settled.copy(ends = ends)
            is WayToEnds.Settled.Refused -> settled.copy(ends = ends)
        }
    }

    /** [settle]'s steps once both ends are planned, as they were before the end record (dispatch 2026-11-05-07): the one re-run, then the gates on the legs drawn. */
    private suspend fun drawAndGate(
        walker: LatLng,
        waypoint: LatLng,
        route: TrailRoute,
        far: EndPlan,
        near: EndPlan,
        reroute: suspend (from: LatLng, to: LatLng) -> TrailRouteResult,
    ): WayToEnds.Settled {
        if (near is EndPlan.Refused) return near.refused
        val from = (near as? EndPlan.MovedTo)?.at
        val to = (far as? EndPlan.MovedTo)?.at
        val drawn = if (from == null && to == null) {
            route
        } else {
            val what = listOfNotNull(from?.let { "from the pick-up" }, to?.let { "to the drop-off" }).joinToString(" ")
            when (val r = reroute(from ?: walker, to ?: waypoint)) {
                is TrailRouteResult.Found -> r.route.takeIf { it.points.size >= 2 }
                    ?: return WayToEnds.Settled.Refused(WayToWaypointNote.NO_ROUTE_FOUND, "the route $what has ${r.route.points.size} points")
                is TrailRouteResult.Failed -> return WayToEnds.Settled.Refused(WayToWaypointNote.NO_ROUTE_FOUND, "no route $what: ${r.failure}")
            }
        }
        far.gated()?.let { (map, own) ->
            val gates = LegGates.check(drawn.points.last(), waypoint, map, listOfNotNull(own))
            if (gates != GateResult.Passes) return refusedFor(gates, if (far is EndPlan.MovedTo) "the leg from the drop-off" else "the leg from the trail to the waypoint")
        }
        near.gated()?.let { (map, own) ->
            val leg = if (near is EndPlan.MovedTo) "the lead-in to the pick-up" else "the lead-in"
            when (val gates = LegGates.check(walker, drawn.points.first(), map, listOfNotNull(own))) {
                GateResult.Passes -> Unit
                is GateResult.Fails -> return WayToEnds.Settled.Refused(WayToWaypointNote.LEAD_IN_BLOCKED, "$leg crosses ${gates.crossings}")
                is GateResult.Unchecked -> return notChecked("$leg: ${gates.what.joinToString { "${it.what}: ${it.reason}" }}")
            }
        }
        return WayToEnds.Settled.Route(drawn, listOfNotNull((far as? EndPlan.ByTrail)?.why, (near as? EndPlan.ByTrail)?.why))
    }

    /** What an end will be, decided before the one re-run. */
    private sealed interface EndPlan {
        /** What this end became, for the log (dispatch 2026-11-05-07). */
        val became: WayToEndBecame

        /** The engine's end stands with its leg ungated: the map or the ways there cannot vouch (A1, A4, A5). */
        data class ByTrail(val why: WayToEnds.ByTrail, val because: ByTrailBecause) : EndPlan {
            override val became get() = WayToEndBecame.ByTrail(because)
        }

        /** The engine's end stands, and its leg is gated against [map], [own] (the end's own building) excepted. */
        data class Gated(val map: NearbyMapData, val own: BuildingOutline?, override val became: WayToEndBecame) : EndPlan

        /** The route moves to [at], a drop-off or a pick-up, and the leg there is gated against [map], [own] excepted. */
        data class MovedTo(val at: LatLng, val map: NearbyMapData, val own: BuildingOutline?, override val became: WayToEndBecame.MovedTo) : EndPlan

        data class Refused(val refused: WayToEnds.Settled.Refused) : EndPlan {
            override val became get() = WayToEndBecame.Refused(refused.note)
        }
    }

    /** The map and the own building a planned end's leg is gated against; null for an end with no gate. */
    private fun EndPlan.gated(): Pair<NearbyMapData, BuildingOutline?>? = when (this) {
        is EndPlan.Gated -> map to own
        is EndPlan.MovedTo -> map to own
        is EndPlan.ByTrail, is EndPlan.Refused -> null
    }

    /**
     * The map and the route ways round an end, or why that end goes by trail: the map read failed, a stored tile has no
     * barrier mark, or the route ways (`.rd5`) could not be read (A5).
     */
    private sealed interface MapRead {
        data class Read(val data: NearbyMapData, val ways: List<RouteWay>) : MapRead

        /** [why] for its own line, as always; [because], its kind, for the end record's (dispatch 2026-11-05-07). */
        data class ByTrail(val why: WayToEnds.ByTrail, val because: ByTrailBecause) : MapRead
    }

    private suspend fun read(end: String, at: LatLng, radius: Double, routeData: File): MapRead {
        val data = mapData(at, radius).getOrElse { return MapRead.ByTrail(WayToEnds.ByTrail("$end: the map could not be read: ${it.message}", it), ByTrailBecause.MAP_UNREADABLE) }
        unmarkedTile(data)?.let { return MapRead.ByTrail(WayToEnds.ByTrail("$end: $it"), ByTrailBecause.NO_FENCE_MARK) }
        val ways = routeWays.near(at, radius, routeData).getOrElse {
            return MapRead.ByTrail(WayToEnds.ByTrail("$end: the route ways could not be read: ${it.message}", it), ByTrailBecause.WAYS_UNREADABLE)
        }
        return MapRead.Read(data, ways)
    }

    /**
     * The waypoint's end: by trail where the map cannot vouch; the drop-off for a street end; the engine's end for a trail
     * end. A trail end's leg is gated here on the route as found, so a blocked way in is said before anything else is
     * read, and again on the route drawn.
     */
    private suspend fun waypointEnd(waypoint: LatLng, route: TrailRoute, routeData: File): EndPlan {
        val (map, ways) = when (val end = read("the waypoint's end", waypoint, RADIUS_METERS, routeData)) {
            is MapRead.ByTrail -> return EndPlan.ByTrail(end.why, end.because)
            is MapRead.Read -> end
        }
        return when (val end = StreetDropOff.classify(waypoint, map, ways)) {
            is StreetDropOff.End.Unknown -> EndPlan.Refused(notChecked("the waypoint's end: ${end.reason}"))
            StreetDropOff.End.Trail -> when (val gates = LegGates.check(route.points.last(), waypoint, map)) {
                GateResult.Passes -> EndPlan.Gated(map, null, WayToEndBecame.NotAStreetEnd)
                else -> EndPlan.Refused(refusedFor(gates, "the leg from the trail to the waypoint"))
            }
            is StreetDropOff.End.Street -> when (val choice = StreetDropOff.choose(waypoint, end.building, map, ways)) {
                is StreetDropOff.Choice.NoneQualifies -> EndPlan.Refused(refusedForAll(choice.tried, "drop-off", WayToWaypointNote.WAY_IN_BLOCKED))
                is StreetDropOff.Choice.Found -> EndPlan.MovedTo(choice.dropOff.at, map, end.building, choice.dropOff.became())
            }
        }
    }

    /**
     * The walker's end: by trail where the map cannot vouch; on a good fix at a street end, the pick-up, chosen as the
     * drop-off is ([StreetDropOff.choose] with the walker as the pin, A2); else the engine's end with its lead-in gated.
     */
    private suspend fun walkerEnd(walker: LatLng, accuracyMeters: Float?, route: TrailRoute, routeData: File): EndPlan {
        val goodFix = accuracyMeters != null && accuracyMeters <= PICK_UP_FIX_ACCURACY_METERS
        val leadIn = GeoDistance.metersBetween(walker, route.points.first()) + LEAD_IN_MARGIN_METERS
        // A pick-up looks as far as a drop-off does, so the read round the walker reaches as far as one round the waypoint.
        val radius = if (goodFix) maxOf(leadIn, RADIUS_METERS) else leadIn
        val (map, ways) = when (val end = read("the lead-in", walker, radius, routeData)) {
            is MapRead.ByTrail -> return EndPlan.ByTrail(end.why, end.because)
            is MapRead.Read -> end
        }
        val end = StreetDropOff.classify(walker, map, ways)
        val own = (end as? StreetDropOff.End.Street)?.building
        if (!goodFix || end !is StreetDropOff.End.Street) {
            // Why, for the end record (dispatch 2026-11-05-07): the fix is checked first, as the pick-up needs it first.
            val became = when {
                !goodFix -> WayToEndBecame.NotJudged(accuracyMeters)
                end is StreetDropOff.End.Unknown -> WayToEndBecame.StreetOrTrailUntold
                else -> WayToEndBecame.NotAStreetEnd
            }
            return EndPlan.Gated(map, own, became)
        }
        return when (val choice = StreetDropOff.choose(walker, own, map, ways)) {
            is StreetDropOff.Choice.NoneQualifies -> EndPlan.Refused(refusedForAll(choice.tried, "pick-up", WayToWaypointNote.LEAD_IN_BLOCKED))
            is StreetDropOff.Choice.Found -> EndPlan.MovedTo(choice.dropOff.at, map, own, choice.dropOff.became())
        }
    }

    /** A chosen drop-off or pick-up, for the end record: its rank and its leg, never where it is (dispatch 2026-11-05-07, A2). */
    private fun StreetDropOff.Tried.became() = WayToEndBecame.MovedTo(rank, legMeters)

    private fun refusedFor(gates: GateResult, leg: String): WayToEnds.Settled.Refused = when (gates) {
        is GateResult.Fails -> WayToEnds.Settled.Refused(WayToWaypointNote.WAY_IN_BLOCKED, "$leg crosses ${gates.crossings}")
        is GateResult.Unchecked -> notChecked("$leg: ${gates.what.joinToString { "${it.what}: ${it.reason}" }}")
        GateResult.Passes -> error("a passing leg is not refused")
    }

    /**
     * No [what] (drop-off or pick-up) qualified: [blocked] when every one tried failed on a crossing; not checked when any
     * could not be vouched for.
     */
    private fun refusedForAll(tried: List<StreetDropOff.Tried>, what: String, blocked: WayToWaypointNote): WayToEnds.Settled.Refused {
        val unchecked = tried.mapNotNull { it.gates as? GateResult.Unchecked }
        val why = if (tried.isEmpty()) "no public street within ${StreetDropOff.STREET_METERS.toInt()} m" else tried.joinToString { "${it.rank} ${it.legMeters.toInt()} m: ${it.gates}" }
        return if (unchecked.isNotEmpty()) notChecked("no $what qualified: $why") else WayToEnds.Settled.Refused(blocked, "no $what qualified: $why")
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

        /**
         * The pick-up at the walker's end is chosen only on a fix this accurate or better, in metres (dispatch
         * 2026-11-04-04, A4). The owner, "Need a good fix (Recommended)": "Only choose a pick-up point when the fix is
         * accurate to 15 m or better; with a rougher fix, your end works as today (gated straight lead-in). Re-checked as
         * you walk on." The fix's reported accuracy is what it claims, not a guarantee (CLAUDE.md), so this is the
         * operating limit applied to it. A fix that reports no accuracy is not known to be this good, so it counts as
         * rougher. The re-check is each later search for the way (a stray's re-route, or a retry after walking on), which
         * reads the fix of its own moment, with no new trigger (A5, the planner accepting that reading).
         */
        const val PICK_UP_FIX_ACCURACY_METERS = 15f
    }
}
