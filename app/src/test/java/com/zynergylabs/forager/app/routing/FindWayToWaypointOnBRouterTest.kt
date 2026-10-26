package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.routing.TrailSnapResult
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_FROM
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_TO
import com.zynergylabs.forager.app.routing.RoutingTestData.NO_TRAIL_NEAR
import com.zynergylabs.forager.app.routing.RoutingTestData.OFF_TRAIL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The way to a waypoint on the real vendored BRouter and the Harriman fixture (dispatch 2026-10-26-10, step 6 PR 2).
 * Nothing is stubbed but the list of downloaded regions. What these pin is the premise the use case's one engine run
 * rests on: the route's first point is the trail point the engine matches the walker to, and its last the one it
 * matches the waypoint to, the same points a snap gives; and an end with no trail in reach fails for that end.
 */
class FindWayToWaypointOnBRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }

    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }
    private val data: File by lazy { RoutingTestData.segments(tmp) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    // 20 km round the trail start: the fixture's ways and NO_TRAIL_NEAR, about 14 km north, all inside its rectangle.
    private fun find(from: LatLng, waypoint: LatLng) = runBlocking {
        val region = Ground.region(id = 7, centre = ACROSS_FROM, radiusKm = 20, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        FindWayToWaypointUseCase(FakeRegions(region), engines, errorLog)(from, waypoint)
    }

    private fun snap(point: LatLng): LatLng {
        val result = runBlocking { BRouterTrailSnapper(router, TrailSnapMethod.SNAP).nearestTrail(point, point, data) }
        assertTrue("a point on the network, not $result", result is TrailSnapResult.OnNetwork)
        return (result as TrailSnapResult.OnNetwork).trailPoint
    }

    private fun way(result: WayToWaypointResult): WayHome {
        assertTrue("along trails, not $result (log: $logged)", result is WayToWaypointResult.AlongTrails)
        return (result as WayToWaypointResult.AlongTrails).way
    }

    @Test
    fun `from off the trail, the lead-in is a straight leg to the trail point the engine matches the walker to`() {
        val way = way(find(OFF_TRAIL, ACROSS_TO))

        assertEquals(WayHomePartKind.STRAIGHT, way.trackBack.kind)
        assertEquals(listOf(OFF_TRAIL, snap(OFF_TRAIL)), way.trackBack.points)
        assertEquals(34.3, way.trackBack.meters, 0.5)
        assertEquals(snap(OFF_TRAIL), way.trail.points.first())
    }

    @Test
    fun `to a waypoint off the trail, the final leg runs from the trail point the engine matches the waypoint to`() {
        val way = way(find(ACROSS_TO, OFF_TRAIL))

        assertEquals(WayHomePartKind.STRAIGHT, way.trackToStart.kind)
        assertEquals(OFF_TRAIL, way.trackToStart.points.last())
        val apart = GeoDistance.metersBetween(snap(OFF_TRAIL), way.trackToStart.points.first())
        assertEquals("the final leg starts $apart m from the waypoint's snap", 0.0, apart, 1.0)
        assertEquals(34.3, way.trackToStart.meters, 1.0)
    }

    @Test
    fun `an end with no trail in the engine's reach gets its own note`() {
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL), find(ACROSS_FROM, NO_TRAIL_NEAR))
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.WALKER_FAR_FROM_TRAIL), find(NO_TRAIL_NEAR, ACROSS_FROM))
    }
}
