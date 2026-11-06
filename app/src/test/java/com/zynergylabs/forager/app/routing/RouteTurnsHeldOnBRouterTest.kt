package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.nextturn.RouteTurns
import com.zynergylabs.forager.app.domain.nextturn.RouteWaysTurnsReader
import com.zynergylabs.forager.app.domain.nextturn.routeTurnsFor
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_FROM
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_TO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Each trail route's result carries the route data folder it was found on, and the holders' reader reads the same
 * junctions from it as the M1 line does (dispatch 2026-11-06-01, Amendment 1, H4, 2b-1), on the real vendored BRouter and
 * the public routing fixture with heights, through the use cases' own entry points. No position is printed or asserted on.
 */
class RouteTurnsHeldOnBRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val warned = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(warned) { warned += message } }

    private val profile: File by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, errorLog) }
    private val data: File by lazy { RoutingTestData.heightedSegments(tmp) }
    private val routeWays by lazy { BRouterRouteWays(profile) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    @Test
    fun `H4 - the way to a waypoint's result carries its route data, and the reader reads the M1 line's junctions from it`() {
        val region = Ground.region(id = 7, centre = ACROSS_FROM, radiusKm = 20, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        val result = runBlocking { FindWayToWaypointUseCase(FakeRegions(region), engines, errorLog, AcceptEnds)(ACROSS_FROM, ACROSS_TO) }
        assertTrue("along trails, not $result (log: $warned)", result is WayToWaypointResult.AlongTrails)
        result as WayToWaypointResult.AlongTrails
        assertEquals("the folder it was found on", data, result.routeData)

        val line = WayHomeLine(result.way, trailAllowanceMeters = 0.0)
        val clock = { 0L }
        val held = runBlocking { RouteWaysTurnsReader(routeWays, errorLog, clock).turnsFor(result.way, line, result.routeData) }
        val logged = runBlocking { routeTurnsFor(result.way, line, routeWays, data, clock) }
        assertTrue("read, not $held", held is RouteTurns.Read)
        assertEquals("the same junctions the M1 line judges", logged, held)
    }

    @Test
    fun `H4 - the way home's result carries its route data`() {
        val outOnTrails = RoutingTestData.commandLineRoute("inside-across-the-degree-line", "brouter-cli/heighted").take(57)
        val turn = outOnTrails.last()
        val perDegree = GeoDistance.metersPerDegree(turn.lat)
        val west = (2..8).map { LatLng(turn.lat, turn.lng - it * 25.0 / perDegree.lng) }
        val track = Track("t", null, 0L, null, (outOnTrails + west).mapIndexed { i, p -> TrackPoint(p.lat, p.lng, null, 5f, i * 1_000L) })
        val region = Ground.region(id = 7, centre = turn, radiusKm = 5, routeData = RegionRouteData.Stored("run", "t", 10L, data))

        val result = runBlocking { WayHomeRouting(engines, FakeRegions(region), errorLog).find(track, null) }

        assertTrue("along trails, not $result (log: $warned)", result is WayHomeResult.AlongTrails)
        assertEquals("the folder it was found on", data, (result as WayHomeResult.AlongTrails).routeData)
    }
}
