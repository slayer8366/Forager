package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.data.dropoffmap.DropOffMapStore
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.mapdata.LayeredOfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.StreetDropOffEnds
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import com.zynergylabs.forager.app.map.HotCopy
import com.zynergylabs.forager.app.map.MapLibreOfflineBasemap
import com.zynergylabs.forager.app.map.MapLibreStoreFixtures
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-05-01, T1 through the way-to's own read (A5): S22 session 14's step 7, where the lead-in's read of
 * MapLibre's store was refused with SQLITE_READONLY_ROLLBACK and the walker's end went by trail. Here the real use case,
 * the real vendored BRouter, the real route-square reader, and the real store reader composed as `AppContainer` composes
 * them (MapLibre's store under the drop-off map copy, which is empty here), on the drop-off fixture's case A. MapLibre's
 * store holds the marked fixture tile, mid-save ([MapLibreStoreFixtures.hotJournalCopy]); the save ends at the first wait.
 *
 * Robolectric, for Android's SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindWayToWaypointStoreRefusalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }
    private val storeLog = mutableListOf<String>()
    private val profile by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, errorLog) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    private val fixtureTile = TileId(15, 9875, 12393)
    private val marked = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val tileJson = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/tilejson-with-0003.json")).toURI()).readText()
    private val template = "https://tiles.example.invalid/us/{z}/{x}/{y}.mvt"

    private fun mapLibreMidSave(): HotCopy {
        val store = MapLibreStoreFixtures.store(File(tmp.newFolder("live"), "mbgl-offline.db"), tileJson, template, mapOf(fixtureTile to marked))
        return MapLibreStoreFixtures.hotJournalCopy(store, tmp.newFolder("mid-save"), "UPDATE tiles SET accessed = accessed + 1")
    }

    private fun region() = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))

    @Test
    fun `a pin at a house is reached from its drop-off even when the first read of MapLibre's store is refused mid-save`() = runBlocking {
        val copy = mapLibreMidSave()
        val waits = mutableListOf<Long>()
        val mapLibre = MapLibreOfflineBasemap(copy.database, log = { message, _ -> synchronized(storeLog) { storeLog += message } }, pause = { waits += it; copy.journal.delete() })
        val dropOffCopy = DropOffMapStore(File(tmp.root, "offline_basemap"), { _, _ -> })
        val ends = StreetDropOffEnds(ReadNearbyMapData(LayeredOfflineBasemap(mapLibre, dropOffCopy) { _, _ -> }), BRouterRouteWays(profile))

        val result = FindWayToWaypointUseCase(FakeRegions(region()), engines, errorLog, ends)(DropOffTestData.point("walker"), DropOffTestData.point("pin_a"))

        assertTrue("no end went by trail because the map could not be read (log: $logged)", logged.none { "could not be read" in it })
        assertTrue("along trails, not $result (log: $logged)", result is WayToWaypointResult.AlongTrails)
        val way = (result as WayToWaypointResult.AlongTrails).way
        val off = GeoDistance.metersBetween(way.trail.points.last(), DropOffTestData.point("dropoff_a"))
        assertEquals("the trail ends $off m from the drop-off, as with no refusal (FindWayToWaypointDropOffTest)", 0.0, off, 1.0)
        assertEquals("one wait, the first of B1's", listOf(50L), waits)
        assertEquals("one retry line from the store's reader: $storeLog", 1, storeLog.size)
    }
}
