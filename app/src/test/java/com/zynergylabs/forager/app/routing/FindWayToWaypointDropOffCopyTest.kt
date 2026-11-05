package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.data.dropoffmap.DropOffMapStore
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.mapdata.LayeredOfflineBasemap
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
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.StreetDropOffEnds
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * The way to a waypoint reading the app's own drop-off map copy (dispatch 2026-11-04-09), through its real entry point:
 * the real use case, the real vendored BRouter, the real route-square reader, the real tile reader over the real layered
 * read and the real copy's folders. Only MapLibre's store is given by hand, holding the drop-off fixture's one tile as a
 * build from before the fence mark wrote it, as on an area downloaded before 2026-11-03. The fixture is the invented
 * block (`src/test/resources/routing/dropoff/README.md`); no tile or position of anyone's real area is used.
 */
class FindWayToWaypointDropOffCopyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }
    private val copyLog = mutableListOf<String>()
    private val profile by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, errorLog) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    private val fixtureTile = TileId(15, 9875, 12393)
    private val marked = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val unmarked = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()
    private val tileJson = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/tilejson-with-0003.json")).toURI()).readText()

    private val store by lazy { DropOffMapStore(File(tmp.root, "offline_basemap"), { message, _ -> synchronized(copyLog) { copyLog += message } }) }

    /** MapLibre's store holding [stored] as the fixture tile, or nothing at all when null. */
    private fun mapLibre(stored: ByteArray?) = object : OfflineBasemap {
        override suspend fun tileJson() = Result.success<String?>(tileJson)
        override suspend fun tile(id: TileId) = Result.success(if (id == fixtureTile) stored else null)
    }

    private fun copyHolds(bytes: ByteArray, regionId: Long = 7) {
        assertTrue(store.beginPass(regionId))
        store.write(regionId, fixtureTile, bytes)
        store.endPass(regionId)
    }

    private fun find(mapLibre: OfflineBasemap) = runBlocking {
        val layered = LayeredOfflineBasemap(mapLibre, store) { message, _ -> synchronized(copyLog) { copyLog += message } }
        val ends = StreetDropOffEnds(ReadNearbyMapData(layered), BRouterRouteWays(profile))
        FindWayToWaypointUseCase(FakeRegions(region()), engines, errorLog, ends)(DropOffTestData.point("walker"), DropOffTestData.point("pin_a"))
    }

    private fun region() = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))

    private fun today() = runBlocking {
        FindWayToWaypointUseCase(FakeRegions(region()), engines, ErrorLog { _, _, _ -> }, AcceptEnds)(DropOffTestData.point("walker"), DropOffTestData.point("pin_a"))
    }

    private fun byTrailLines() = synchronized(logged) { logged.filter { "by trail" in it } }

    private fun way(result: WayToWaypointResult): WayHome {
        assertTrue("along trails, not $result (log: $logged)", result is WayToWaypointResult.AlongTrails)
        return (result as WayToWaypointResult.AlongTrails).way
    }

    private fun assertTrailEndsAt(way: WayHome, end: String) {
        val off = GeoDistance.metersBetween(way.trail.points.last(), DropOffTestData.point(end))
        assertEquals("the trail ends $off m from $end (log: $logged)", 0.0, off, 1.0)
    }

    @Test
    fun `MapLibre's tile from before the fence mark, and the copy's marked one, give the drop-off with no by-trail line`() {
        copyHolds(marked)

        val way = way(find(mapLibre(stored = unmarked)))

        assertTrailEndsAt(way, "dropoff_a")
        assertEquals(DropOffTestData.point("pin_a"), way.trackToStart.points.last())
        assertEquals("no end went by trail: $logged", emptyList<String>(), byTrailLines())
    }

    @Test
    fun `a tile neither store holds marked still goes by trail as today, with its by-trail line for each end`() {
        // A10 (replacing A3's first clause): "where no marked tile is available from either store, the end goes by trail
        // and is logged, exactly as dispatch 2026-11-04-04 A1 rules". The copy holds nothing here.
        val result = find(mapLibre(stored = unmarked))

        assertEquals("the same way as with no ends step (log: $logged)", today(), result)
        assertTrailEndsAt(way(result), "engine_a")
        val lines = byTrailLines()
        assertEquals("one line per end: $logged", 2, lines.size)
        assertTrue(lines.all { "has no barrier mark" in it })
    }

    @Test
    fun `a copy whose tile does not decode goes by trail with the same line, and the bad tile is removed and logged`() {
        copyHolds(byteArrayOf(0x1a, 0x7f, 0x00))

        val result = find(mapLibre(stored = unmarked))

        assertEquals("the same way as with no ends step (log: $logged)", today(), result)
        val lines = byTrailLines()
        assertEquals("one line per end: $logged", 2, lines.size)
        assertTrue(lines.all { "has no barrier mark" in it })
        assertFalse("the bad tile is gone, so the next pass fetches it again", store.holds(7, fixtureTile))
        assertTrue("its removal is logged: $copyLog", copyLog.any { "15/9875/12393" in it && "does not decode" in it })
    }

    @Test
    fun `MapLibre's tile marked is read as today, with the copy holding nothing`() {
        assertTrailEndsAt(way(find(mapLibre(stored = marked))), "dropoff_a")
        assertEquals(emptyList<String>(), byTrailLines())
    }

    @Test
    fun `a MapLibre store that cannot be read goes by trail as today, whatever the copy holds`() {
        copyHolds(marked)
        val broken = object : OfflineBasemap {
            override suspend fun tileJson() = Result.failure<String?>(IOException("mbgl-offline.db is locked"))
            override suspend fun tile(id: TileId) = Result.failure<ByteArray?>(IOException("mbgl-offline.db is locked"))
        }

        val result = find(broken)

        assertEquals("the same way as with no ends step (log: $logged)", today(), result)
        val lines = byTrailLines()
        assertEquals("one line per end: $logged", 2, lines.size)
        assertTrue(lines.all { "could not be read" in it && "mbgl-offline.db is locked" in it })
    }
}
