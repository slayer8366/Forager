package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomePart
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind
import java.io.File
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sin

/**
 * Made-up ground for the next-turn tests (dispatch 2026-11-06-01, Phase 2a): metres east and north of the made-up point
 * [P], a public mountain summit the dispatch names for any new point. Nothing here is a real route or anyone's walk.
 */
internal object TurnGround {
    /** The dispatch's made-up point P. */
    val P = LatLng(45.3736, -121.6959)
    private val perDegree = GeoDistance.metersPerDegree(P.lat)

    fun at(east: Double, north: Double) = LatLng(P.lat + north / perDegree.lat, P.lng + east / perDegree.lng)

    fun at(eastNorth: Pair<Double, Double>) = at(eastNorth.first, eastNorth.second)

    /** [meters] from ([east], [north]) on [bearingDegrees], clockwise from north. */
    fun toward(east: Double, north: Double, bearingDegrees: Double, meters: Double): Pair<Double, Double> {
        val r = Math.toRadians(bearingDegrees)
        return (east + meters * sin(r)) to (north + meters * cos(r))
    }

    /** A way of the route data through these points (metres east, north), with no tags. */
    fun way(vararg eastNorth: Pair<Double, Double>) = RouteWay(eastNorth.map { at(it) }, emptyMap())

    fun part(kind: WayHomePartKind, points: List<Pair<Double, Double>>): WayHomePart {
        val latLngs = points.map { at(it) }
        return WayHomePart(kind, latLngs, latLngs.zipWithNext { a, b -> GeoDistance.metersBetween(a, b) }.sum())
    }

    /** A way to a waypoint: a straight lead-in [leadIn], the trail route [trail] and a straight final leg [finalLeg], each sharing its end points. */
    fun wayTo(leadIn: List<Pair<Double, Double>>, trail: List<Pair<Double, Double>>, finalLeg: List<Pair<Double, Double>>) =
        WayHome(part(WayHomePartKind.STRAIGHT, leadIn), part(WayHomePartKind.TRAIL, trail), part(WayHomePartKind.STRAIGHT, finalLeg), trailJointMeters = 0.0, regionId = 1)

    /**
     * A way home: the walker's track back, the trail part (its first and last stretches the two joints, of [jointMeters]
     * together) and the walker's track to the start.
     */
    fun wayHome(trackBack: List<Pair<Double, Double>>, trail: List<Pair<Double, Double>>, trackToStart: List<Pair<Double, Double>>, jointMeters: Double) =
        WayHome(part(WayHomePartKind.TRACK_BACK, trackBack), part(WayHomePartKind.TRAIL, trail), part(WayHomePartKind.TRACK_TO_START, trackToStart), trailJointMeters = jointMeters, regionId = 1)

    /** The line the way to a waypoint's walker is placed on (no trail allowance, as `AvailabilityWaypointRoute` builds it). */
    fun lineOf(way: WayHome) = WayHomeLine(way, trailAllowanceMeters = if (way.trackBack.kind == WayHomePartKind.STRAIGHT) 0.0 else 25.0)

    val FOLDER = File("/made-up/routes")
}

/**
 * Each read returns the [ways] with a point within the read's circle and 100 m more, as a read of the data's cells returns
 * somewhat more than its circle but never the whole map; or [failure] for every read. Counts the reads.
 */
internal class FakeRouteWays(private val ways: List<RouteWay>, private val failure: Throwable? = null) : RouteWays {
    var reads = 0
        private set

    override suspend fun near(center: LatLng, radiusMeters: Double, routeData: File): Result<List<RouteWay>> {
        reads++
        failure?.let { return Result.failure(it) }
        return Result.success(ways.filter { way -> way.points.any { GeoDistance.metersBetween(center, it) <= radiusMeters + 100.0 } })
    }

    companion object {
        fun failing() = FakeRouteWays(emptyList(), IOException("made-up square missing"))
    }
}
