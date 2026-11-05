package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.map.MapLibreOfflineBasemap
import com.zynergylabs.forager.app.map.MapLibreStoreFixtures
import com.zynergylabs.forager.app.routing.DropOffTestData
import java.io.File
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import kotlinx.coroutines.Dispatchers
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
 * Dispatch 2026-11-05-01, T1 through the copy pass's own read (A5): S22 session 14's step 3, where both areas' first pass
 * stopped at once with "MapLibre's store could not be read" (SQLITE_READONLY_ROLLBACK). Here [DropOffMapFetch.pass] over the
 * fake tile server, with the real store reader on MapLibre's store held mid-save
 * ([MapLibreStoreFixtures.hotJournalCopy]); the save ends at the first wait. The area is `DropOffMapFetchTest`'s nine tiles.
 *
 * Robolectric, for Android's SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DropOffMapFetchStoreRefusalTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val marked = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val logs = mutableListOf<String>()
    private val log: (String, Throwable?) -> Unit = { message, _ -> synchronized(logs) { logs += message } }
    private val server = FakeTileServer(marked)

    /** The pass's clock, which moves only when its pacing pauses (as in `DropOffMapFetchTest`). */
    private var now = 0L

    /** A rectangle just inside the zoom-15 tiles x 9874..9876 by y 12392..12394, from the tiles' own corners. */
    private val area: GeoBoundingBox = run {
        fun westLng(x: Int) = x / 32768.0 * 360.0 - 180.0
        fun northLat(y: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / 32768.0))))
        val inset = 1e-6
        GeoBoundingBox(north = northLat(12392) - inset, south = northLat(12395) + inset, east = westLng(9877) - inset, west = westLng(9874) + inset)
    }

    @Test
    fun `a pass whose first read of MapLibre's store is refused mid-save still runs, and the area is made whole`() {
        val live = MapLibreStoreFixtures.store(File(temp.newFolder("live"), "mbgl-offline.db"), FakeTileServer.TILE_JSON, FakeTileServer.TEMPLATE)
        val copy = MapLibreStoreFixtures.hotJournalCopy(live, temp.newFolder("mid-save"), "UPDATE resources SET accessed = accessed + 1")
        val waits = mutableListOf<Long>()
        val storeLog = mutableListOf<String>()
        val mapLibre = MapLibreOfflineBasemap(copy.database, log = { message, _ -> synchronized(storeLog) { storeLog += message } }, pause = { waits += it; copy.journal.delete() })
        val fetch = DropOffMapFetch(server.client, DropOffMapStore(File(temp.root, "offline_basemap"), log), mapLibre, log, Dispatchers.IO, clock = { now }, pause = { now += it })

        val result = runBlocking { fetch.pass(3, area) }

        assertTrue("the pass was not stopped by a read of MapLibre's store (log: $logs)", logs.none { "MapLibre's store could not be read" in it })
        assertEquals("every tile of the cover asked for and kept (log: $logs)", DropOffMapPass.Whole(requests = 9, stored = 9, empty = 0), result)
        assertEquals("one wait, the first of B1's", listOf(50L), waits)
        assertEquals("one retry line from the store's reader: $storeLog", 1, storeLog.size)
    }
}
