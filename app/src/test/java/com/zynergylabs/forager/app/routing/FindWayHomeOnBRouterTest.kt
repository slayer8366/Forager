package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FindWayHomeUseCase
import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_TRAIL_JOIN_METERS
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The way home along trails on the real vendored BRouter and the Harriman fixture, by both ways of finding the trail
 * (dispatch 2026-10-23-09 Amendment 1, part 1; RECORD -1589, "Measure, then pick"). Nothing is stubbed but the list of
 * downloaded regions.
 *
 * The walk: out along the first 57 points of the command-line router's `inside-across-the-degree-line` route, which
 * lie on trails, then 200 m due west into ground with no way within 25 m. Before these tests were written, BRouter
 * v1.7.10 matched the points 50 to 200 m west of the turn to ways 46.6 to 195.2 m away, and the point 25 m west to
 * one 23.1 m away, so that point is left out.
 */
class FindWayHomeOnBRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }

    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }
    private val data: File by lazy { RoutingTestData.segments(tmp) }

    private val outOnTrails = RoutingTestData.commandLineRoute("inside-across-the-degree-line").take(57)
    private val turn = outOnTrails.last()
    private val west = (2..8).map { westOf(turn, it * 25.0) }

    private fun westOf(point: LatLng, metres: Double) =
        LatLng(point.lat, point.lng - metres / (111_320.0 * Math.cos(Math.toRadians(point.lat))))

    private fun track(points: List<LatLng>) = Track("t", null, 0L, null, points.mapIndexed { i, p -> TrackPoint(p.lat, p.lng, null, 5f, i * 1_000L) })

    private val regions = object : OfflineMapRepository {
        override suspend fun download(name: String, region: Region, onProgress: (downloaded: Int, total: Int) -> Unit): Result<OfflineRegionSummary> = error("not used")
        override suspend fun deleteRegion(id: Long): Result<Unit> = error("not used")
        override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(
            listOf(
                OfflineRegionSummary(
                    7, "Harriman", Region(turn.lat, turn.lng, 5), 10.0, 15.0, 100, 1_000L, 1_000L,
                    routeData = RegionRouteData.Stored("run", "t", 10L, data),
                ),
            ),
        )
    }

    private fun find(method: TrailSnapMethod, points: List<LatLng>) =
        runBlocking { FindWayHomeUseCase(regions, router, BRouterTrailSnapper(router, method), errorLog)(track(points), null) }

    private fun alongTrails(result: WayHomeResult): WayHome {
        assertTrue("along trails, not $result (log: $logged)", result is WayHomeResult.AlongTrails)
        return (result as WayHomeResult.AlongTrails).wayHome
    }

    @Test
    fun `the way home leaves the forest by the walker's own track, then takes the trail back to the start, by both methods`() {
        val bySnap = alongTrails(find(TrailSnapMethod.SNAP, outOnTrails + west))
        val byRoute = alongTrails(find(TrailSnapMethod.FULL_ROUTE, outOnTrails + west))

        assertEquals("the two methods find the same way home", bySnap, byRoute)
        assertEquals("the track back starts at the walker's most recent point", west.last(), bySnap.trackBack.points.first())
        assertEquals("and ends where they left the trail, at the turn", turn, bySnap.trackBack.points.last())
        assertEquals("the trail part runs from the turn", turn, bySnap.trail.points.first())
        assertEquals("to the walk's first point, on a trail", outOnTrails.first(), bySnap.trail.points.last())
        assertEquals("so the far end is the first point itself", listOf(outOnTrails.first()), bySnap.trackToStart.points)
        assertTrue("each joint is at most $WAY_HOME_TRAIL_JOIN_METERS m: ${bySnap.trailJointMeters}", bySnap.trailJointMeters <= 2 * WAY_HOME_TRAIL_JOIN_METERS)

        val walkedOut = outOnTrails.zipWithNext { a, b -> GeoDistance.metersBetween(a, b) }.sum()
        assertTrue("the trail route home, ${bySnap.trail.meters} m, is near the ${walkedOut} m walked out", bySnap.trail.meters in 0.8 * walkedOut..1.2 * walkedOut)
        assertEquals("nothing fell back: $logged", emptyList<String>(), logged.toList())
    }

    @Test
    fun `a walk that never came near a trail follows the track, saying so, by both methods`() {
        for (method in TrailSnapMethod.entries) {
            assertEquals("$method", WayHomeResult.TrackOnly(WayHomeNote.NEVER_NEAR_TRAIL), find(method, west))
        }
    }
}
