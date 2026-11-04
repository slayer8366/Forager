package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FindWayHomeUseCase
import com.zynergylabs.forager.app.domain.wayhome.PartHeights
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The way home's trail part on real engine heights (dispatch 2026-10-30-06, Amendment 3; Sorrel's N4 and N6 on #447):
 * the only test that runs heights from the vendored BRouter, on the Harriman square with heights, through
 * [FindWayHomeUseCase]. Nothing is stubbed but the list of downloaded regions.
 *
 * The walk is `FindWayHomeOnBRouterTest`'s (out along the first 57 points of `inside-across-the-degree-line`, then 200 m
 * due west off the trails), with barometric pressure on every point so the track is barometric and the trail part's
 * heights are chosen at all; without a barometer the trail part is `NoBarometer` and never reads them (RECORD -1838,
 * "Keep it off"). That route's points are the same on both squares (`brouter-cli/` and `brouter-cli/heighted/`).
 */
class FindWayHomeHeightsOnBRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }

    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }

    private val outOnTrails = RoutingTestData.commandLineRoute("inside-across-the-degree-line", folder = "brouter-cli/heighted").take(57)
    private val turn = outOnTrails.last()
    private val west = (2..8).map { westOf(turn, it * 25.0) }

    private fun westOf(point: LatLng, metres: Double) =
        LatLng(point.lat, point.lng - metres / (111_320.0 * Math.cos(Math.toRadians(point.lat))))

    /** One point a second, every one with the barometer's pressure: level ground to the barometer. */
    private fun barometricTrack(points: List<LatLng>) =
        Track("t", null, 0L, null, points.mapIndexed { i, p -> TrackPoint(p.lat, p.lng, null, 5f, i * 1_000L, pressureHpa = 980f) })

    private fun regionsOn(data: File) = object : OfflineMapRepository {
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

    private fun wayHomeOn(data: File): WayHome {
        val result = runBlocking {
            FindWayHomeUseCase(regionsOn(data), router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP), errorLog)(barometricTrack(outOnTrails + west), null)
        }
        assertTrue("along trails, not $result (log: $logged)", result is WayHomeResult.AlongTrails)
        return (result as WayHomeResult.AlongTrails).wayHome
    }

    @Test
    fun `on route data with heights the trail part's heights are the engine's own, and nothing falls back`() {
        val data = RoutingTestData.heightedSegments(tmp)
        val wayHome = wayHomeOn(data)
        assertTrue("the walk is barometric, so the track parts carry its heights: ${wayHome.trackBack.heights}", wayHome.trackBack.heights is PartHeights.Barometric)

        val heights = wayHome.trail.heights
        assertTrue("the trail part's heights are the trail data's, not $heights (log: $logged)", heights is PartHeights.TrailData)
        val samples = (heights as PartHeights.TrailData).samples
        // The route's heights sit between the two height-less samples that keep each step uncounted (J1).
        val steps = samples.indices.filter { samples[it].height == null }
        assertEquals("one height-less sample at each step, and none elsewhere on a walk with a barometer throughout", 2, steps.size)
        val routeHeights = samples.subList(steps[0] + 1, steps[1]).map { it.height }

        // The same route, asked of the engine again between the trail part's own two ends.
        val trail = wayHome.trail.points
        val again = runBlocking { router.route(trail.first(), trail.last(), data) }
        assertTrue("$again", again is TrailRouteResult.Found)
        val engine = (again as TrailRouteResult.Found).route
        assertEquals("it is the trail part's route", trail.subList(1, trail.size - 1), engine.points)
        assertTrue("the engine gave every point a height: ${engine.heightsMetres.count { it == null }} missing", engine.heightsMetres.none { it == null })
        assertEquals("the trail part's route heights are the engine's, point for point", engine.heightsMetres, routeHeights)
        assertTrue("and none is below -500 m: ${routeHeights.filterNotNull().min()}", routeHeights.filterNotNull().all { it >= -500.0 })
        assertEquals("nothing fell back: $logged", emptyList<String>(), logged.toList())
    }

    @Test
    fun `the same walk on route data without heights takes the walker's track as the stand-in, and logs it once`() {
        val wayHome = wayHomeOn(RoutingTestData.segments(tmp))
        val heights = wayHome.trail.heights
        assertTrue("the stand-in, not $heights", heights is PartHeights.Barometric && heights.standIn)
        assertEquals("logged once, with the counts", 1, logged.size)
        val pointCount = wayHome.trail.points.size - 2
        assertEquals(
            "The trail part's climb is the walker's track between the trail points: 0 of $pointCount trail route points have heights.",
            logged.single(),
        )
    }
}
