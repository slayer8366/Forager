package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.RouteWay
import com.zynergylabs.forager.app.domain.routing.RouteWays
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
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
import java.io.IOException

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

    /** [stored] is the one fixture tile's bytes; null stores no tile at all. */
    private fun find(pin: String, json: String = tileJson("tilejson-with-0003.json"), stored: ByteArray? = tile) =
        findOn(pin, if (stored == null) nothingStored(json) else basemap(json, stored))

    private fun findOn(pin: String, basemap: OfflineBasemap, ways: RouteWays = BRouterRouteWays(profile)) = runBlocking {
        val ends = StreetDropOffEnds(ReadNearbyMapData(basemap), ways)
        FindWayToWaypointUseCase(FakeRegions(region()), engines, errorLog, ends)(DropOffTestData.point("walker"), DropOffTestData.point(pin))
    }

    private fun region() = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))

    /** Today's way, with no ends step at all: the engine's own ends and ungated legs. A1's "exactly as it does today". */
    private fun today(pin: String) = runBlocking {
        FindWayToWaypointUseCase(FakeRegions(region()), engines, ErrorLog { _, _, _ -> }, AcceptEnds)(DropOffTestData.point("walker"), DropOffTestData.point(pin))
    }

    private fun nothingStored(json: String) = object : OfflineBasemap {
        override suspend fun tileJson() = Result.success<String?>(json)
        override suspend fun tile(id: TileId) = Result.success<ByteArray?>(null)
    }

    /** The "by trail" lines logged, one per end that went by trail (A1: never silent). */
    private fun byTrailLines() = synchronized(logged) { logged.filter { "by trail" in it } }

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
    fun `a tile from before fences were carried goes by trail as today, with no drop-off, and says so in the log`() {
        // A1 (the owner, 2026-11-04): "In areas without fence data, Navigate works exactly as it does today (by trail),
        // with no street drop-off." A tile downloaded before the 11-01 build has no barrier mark, whatever the TileJSON says.
        val result = find("pin_a", tileJson("tilejson-with-0003.json"), tileBefore0003)
        assertEquals("the same way as with no ends step (log: $logged)", today("pin_a"), result)
        assertTrailEndsAt(way(result), "engine_a")
        val lines = byTrailLines()
        assertEquals("one line per end, the waypoint's and the lead-in's: $logged", 2, lines.size)
        assertTrue(lines.all { "no barrier mark" in it })
        assertTrue(lines.any { "the waypoint's end" in it } && lines.any { "the lead-in" in it })
        assertTrue("no straight line: $logged", logged.none { "straight line" in it })
    }

    @Test
    fun `a basemap store that cannot be read goes by trail as today, and the error is logged`() {
        // A4 (the owner, 2026-11-04): a store-read failure is "Same as an older map: Navigate goes by trail as today".
        val broken = object : OfflineBasemap {
            override suspend fun tileJson() = Result.failure<String?>(IOException("mbgl-offline.db is locked"))
            override suspend fun tile(id: TileId) = Result.failure<ByteArray?>(IOException("mbgl-offline.db is locked"))
        }
        val result = findOn("pin_a", broken)
        assertEquals("the same way as with no ends step (log: $logged)", today("pin_a"), result)
        assertTrailEndsAt(way(result), "engine_a")
        val lines = byTrailLines()
        assertEquals("one line per end: $logged", 2, lines.size)
        assertTrue(lines.all { "could not be read" in it && "mbgl-offline.db is locked" in it })
    }

    @Test
    fun `a stored tile that cannot be decoded goes by trail as today, and the error is logged`() {
        val result = find("pin_a", stored = byteArrayOf(0x1a, 0x7f, 0x00))
        assertEquals("the same way as with no ends step (log: $logged)", today("pin_a"), result)
        assertTrailEndsAt(way(result), "engine_a")
        assertEquals("one line per end: $logged", 2, byTrailLines().size)
    }

    @Test
    fun `route ways that cannot be read go by trail as today, and the error is logged`() {
        // A5 (the owner, 2026-11-04): an end whose route ways (.rd5) cannot be read goes by trail, logged, like a
        // store-read failure. The tile here is the marked one, so only the ways stand between the end and its drop-off.
        val unreadable = object : RouteWays {
            override suspend fun near(center: LatLng, radiusMeters: Double, routeData: File) =
                Result.failure<List<RouteWay>>(IOException("E5_N45.rd5 cannot be read"))
        }
        val result = findOn("pin_a", basemap(tileJson("tilejson-with-0003.json"), tile), unreadable)
        assertEquals("the same way as with no ends step (log: $logged)", today("pin_a"), result)
        assertTrailEndsAt(way(result), "engine_a")
        val lines = byTrailLines()
        assertEquals("one line per end: $logged", 2, lines.size)
        assertTrue(lines.all { "the route ways could not be read" in it && "E5_N45.rd5 cannot be read" in it })
        assertTrue(lines.any { "the waypoint's end" in it } && lines.any { "the lead-in" in it })
        assertTrue("no straight line: $logged", logged.none { "straight line" in it })
    }

    @Test
    fun `an end whose tiles are not stored at all is still the straight line, saying the map could not be checked`() {
        // A3 under A1: MAP_NOT_CHECKED is left for an end whose tiles are not stored.
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.MAP_NOT_CHECKED), find("pin_a", stored = null))
        assertTrue(logged.any { "MAP_NOT_CHECKED" in it && "is not stored" in it })
    }
}
