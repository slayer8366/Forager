package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.StreetDropOffEnds
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The way to a waypoint end to end on the drop-off fixture (way-to dispatch 2026-10-28, PR 5): the real use case, the
 * real vendored BRouter, the real route-square reader and the real tile reader. Only the downloaded regions and the
 * stored tile set are given by hand. This is the street-end test: the owner's case is fixture case A. On 4113c0780 the
 * route ends at the dead end behind the house, 141 m from where it must end (the PR quotes the run).
 */
class FindWayToWaypointDropOffTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }
    private val profile by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, errorLog) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    private fun tileJson(name: String) = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/$name")).toURI()).readText()
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val tileBefore0003 = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()
    private fun basemap(json: String, stored: ByteArray) = object : OfflineBasemap {
        override suspend fun tileJson() = Result.success<String?>(json)
        override suspend fun tile(id: TileId) = Result.success(if (id == TileId(15, 9875, 12393)) stored else null)
    }

    private fun find(pin: String, json: String = tileJson("tilejson-with-0003.json"), stored: ByteArray = tile) = runBlocking {
        val region = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        val ends = StreetDropOffEnds(ReadNearbyMapData(basemap(json, stored)), BRouterRouteWays(profile))
        FindWayToWaypointUseCase(FakeRegions(region), engines, errorLog, ends)(DropOffTestData.point("walker"), DropOffTestData.point(pin))
    }

    private fun way(result: WayToWaypointResult): WayHome {
        assertTrue("along trails, not $result (log: $logged)", result is WayToWaypointResult.AlongTrails)
        return (result as WayToWaypointResult.AlongTrails).way
    }

    private fun assertTrailEndsAt(way: WayHome, end: String) {
        val off = GeoDistance.metersBetween(way.trail.points.last(), DropOffTestData.point(end))
        assertEquals("the trail ends $off m from $end", 0.0, off, 1.0)
        assertEquals("the final leg starts where the trail ends", way.trail.points.last(), way.trackToStart.points.first())
    }

    @Test
    fun `a pin at a house is reached from the street in front, where its driveway comes out, never the dead end behind`() {
        val way = way(find("pin_a"))
        assertTrailEndsAt(way, "dropoff_a")
        assertEquals(DropOffTestData.point("pin_a"), way.trackToStart.points.last())
        assertEquals(77.6, way.trackToStart.meters, 1.0)
    }

    @Test
    fun `a house behind a fenced lane is reached from its address street`() {
        assertTrailEndsAt(way(find("pin_b")), "dropoff_b")
    }

    @Test
    fun `a house behind a stream is reached from the street across only a culvert`() {
        assertTrailEndsAt(way(find("pin_c")), "dropoff_c")
    }

    @Test
    fun `a pin in the wood keeps the trail's own end`() {
        assertTrailEndsAt(way(find("pin_d")), "engine_d")
    }

    @Test
    fun `a pin across a stream from the trail is the straight line, saying so plainly`() {
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.WAY_IN_BLOCKED), find("pin_d2"))
        assertTrue(logged.any { "WAY_IN_BLOCKED" in it && "crosses [WATER]" in it })
    }

    @Test
    fun `a tile from before fences were carried is the straight line, not a route it cannot vouch for`() {
        // A tile downloaded before the 11-01 build has no barrier mark, whatever the stored TileJSON says.
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.MAP_NOT_CHECKED), find("pin_a", tileJson("tilejson-with-0003.json"), tileBefore0003))
        assertTrue(logged.any { "MAP_NOT_CHECKED" in it && "no barrier mark" in it })
    }
}
