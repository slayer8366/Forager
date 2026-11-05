package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.routing.DropOffTestData
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * One area's pass of the drop-off map copy (dispatch 2026-11-04-09), over a fake tile server and a given MapLibre store:
 * which tiles are asked for, what is kept of each answer, when the pass stops, and the operating limits
 * ([DropOffMapLimits], B1 P5). The areas are rectangles drawn round chosen zoom-15 tiles of the drop-off fixture's
 * invented block, in the open Atlantic, so each test knows its cover without the code under test.
 */
class DropOffMapFetchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val marked = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val unmarked = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()

    private val logs = mutableListOf<String>()
    private val log: (String, Throwable?) -> Unit = { message, _ -> synchronized(logs) { logs += message } }
    private val store by lazy { DropOffMapStore(File(temp.root, "offline_basemap"), log) }
    private val server = FakeTileServer(marked)

    /** MapLibre's store: [held] tiles, every other tile not stored; every tile read is counted. */
    private class MapLibre(val held: Map<TileId, ByteArray> = emptyMap(), val json: String? = FakeTileServer.TILE_JSON) : OfflineBasemap {
        val reads = AtomicInteger()
        override suspend fun tileJson() = Result.success(json)
        override suspend fun tile(id: TileId): Result<ByteArray?> {
            reads.incrementAndGet()
            return Result.success(held[id])
        }
    }

    /** The test's clock, in milliseconds: it moves only when the pass pauses, so the start gaps are read exactly. */
    private var now = 0L

    private fun fetch(mapLibre: OfflineBasemap) =
        DropOffMapFetch(server.client, store, mapLibre, log, Dispatchers.IO, clock = { now }, pause = { now += it })

    private fun pass(mapLibre: OfflineBasemap, area: GeoBoundingBox, regionId: Long = 3) = runBlocking { fetch(mapLibre).pass(regionId, area) }

    /** A rectangle just inside the zoom-15 tiles x in [xs] by y in [ys], found from the tiles' own corners. */
    private fun areaOver(xs: IntRange, ys: IntRange): GeoBoundingBox {
        val inset = 1e-6
        return GeoBoundingBox(
            north = northLat(ys.first) - inset,
            south = northLat(ys.last + 1) + inset,
            east = westLng(xs.last + 1) - inset,
            west = westLng(xs.first) + inset,
        )
    }

    private fun westLng(x: Int) = x / 32768.0 * 360.0 - 180.0

    private fun northLat(y: Int) = Math.toDegrees(atan(sinh(PI * (1 - 2.0 * y / 32768.0))))

    private fun tiles(xs: IntRange, ys: IntRange) = xs.flatMap { x -> ys.map { y -> TileId(15, x, y) } }

    private val xs = 9874..9876
    private val ys = 12392..12394
    private val nine = tiles(xs, ys)
    private val area = areaOver(xs, ys)

    @Test
    fun `only the wanted tiles are asked for - none MapLibre holds marked, none the copy already holds - and all are kept`() {
        val markedInMapLibre = TileId(15, 9875, 12393)
        val unmarkedInMapLibre = TileId(15, 9874, 12392)
        val alreadyCopied = TileId(15, 9876, 12394)
        assertTrue(store.beginPass(3))
        store.write(3, alreadyCopied, marked)
        store.endPass(3)

        val result = pass(MapLibre(mapOf(markedInMapLibre to marked, unmarkedInMapLibre to unmarked)), area)

        assertEquals((nine - markedInMapLibre - alreadyCopied).toSet(), server.asked.toSet())
        assertEquals("each asked once", 7, server.asked.size)
        assertEquals(DropOffMapPass.Whole(requests = 7, stored = 7, empty = 0), result)
        assertTrue((nine - markedInMapLibre).all { store.holds(3, it) })
        assertTrue(store.isWhole(3))
    }

    @Test
    fun `an answer without the mark is not stored, stays wanted, is asked again next time, and is counted in the log line`() {
        val old = TileId(15, 9875, 12393)
        server.answers[old] = { FakeTileServer.tileAnswer(unmarked) }

        val result = pass(MapLibre(), area)

        assertEquals(DropOffMapPass.Partial(wanted = 9, requests = 9, stored = 8, empty = 0, notStored = 1, stoppedBy = null), result)
        assertFalse(store.holds(3, old))
        assertFalse(store.isWhole(3))
        assertTrue(logs.toString(), logs.any { "region 3" in it && "1 without the mark" in it })

        server.asked.clear()
        pass(MapLibre(), area)
        assertEquals("only the tile still wanted", listOf(old), server.asked.toList())
    }

    @Test
    fun `an empty answer is recorded and never asked for again`() {
        val empty = TileId(15, 9874, 12394)
        val old = TileId(15, 9876, 12392)
        server.answers[empty] = { FakeTileServer.empty() }
        server.answers[old] = { FakeTileServer.tileAnswer(unmarked) }

        val result = pass(MapLibre(), area)

        assertEquals(DropOffMapPass.Partial(wanted = 9, requests = 9, stored = 7, empty = 1, notStored = 1, stoppedBy = null), result)
        assertTrue(store.holds(3, empty))
        server.asked.clear()
        pass(MapLibre(), area)
        assertEquals(listOf(old), server.asked.toList())
    }

    @Test
    fun `a connection failure stops the pass at once, keeping each tile already written, whole`() {
        server.dropped += nine.first()
        server.holdMillis = 50

        val result = pass(MapLibre(), area)

        assertTrue("$result", result is DropOffMapPass.Partial)
        result as DropOffMapPass.Partial
        assertTrue(result.stoppedBy, result.stoppedBy!!.startsWith("a connection failure"))
        assertTrue("stopped before asking for all nine: ${server.asked}", server.asked.size <= DropOffMapLimits.MAX_IN_FLIGHT)
        assertFalse(store.holds(3, nine.first()))
        assertEquals("every 200 that came back is kept", server.asked.size - 1, nine.count { store.holds(3, it) })
        assertFalse(store.isWhole(3))
        assertTrue(logs.toString(), logs.any { "region 3" in it && "stopped" in it && "connection" in it })
    }

    @Test
    fun `three server errors in a row stop the pass`() {
        nine.forEach { server.answers[it] = { FakeTileServer.status(503) } }

        val result = pass(MapLibre(), area)

        assertTrue("$result", result is DropOffMapPass.Partial)
        result as DropOffMapPass.Partial
        assertEquals("3 server errors in a row (the last HTTP 503)", result.stoppedBy)
        assertTrue("stopped early: ${server.asked.size} asked", server.asked.size in 3..(3 + DropOffMapLimits.MAX_IN_FLIGHT - 1))
        assertEquals(0, result.stored)
    }

    @Test
    fun `two server errors that are not in a row do not stop it`() {
        server.answers[nine[1]] = { FakeTileServer.status(500) }
        server.answers[nine[7]] = { FakeTileServer.status(429) }

        val result = pass(MapLibre(), area)

        assertEquals(DropOffMapPass.Partial(wanted = 9, requests = 9, stored = 7, empty = 0, notStored = 2, stoppedBy = null), result)
    }

    @Test
    fun `a body over 512 KiB is not stored, is counted, and the pass goes on`() {
        val big = TileId(15, 9875, 12392)
        server.answers[big] = { FakeTileServer.tileAnswer(ByteArray((DropOffMapLimits.MAX_TILE_BYTES + 1).toInt())) }

        val result = pass(MapLibre(), area)

        assertEquals(DropOffMapPass.Partial(wanted = 9, requests = 9, stored = 8, empty = 0, notStored = 1, stoppedBy = null), result)
        assertFalse(store.holds(3, big))
        assertTrue(logs.toString(), logs.any { "region 3" in it && "1 over 524288 bytes" in it })
    }

    @Test
    fun `an area over 4,000 zoom-15 tiles is refused before any request or read, and that is logged`() {
        val mapLibre = MapLibre()
        // 64 by 64 tiles: 4,096.
        val result = pass(mapLibre, areaOver(9850..9913, 12370..12433))

        assertTrue("$result", result is DropOffMapPass.NotRun)
        assertTrue((result as DropOffMapPass.NotRun).reason, "4096" in result.reason && "4000" in result.reason)
        assertEquals(emptyList<TileId>(), server.asked.toList())
        assertEquals(0, mapLibre.reads.get())
        assertTrue(logs.toString(), logs.any { "region 3" in it && "4096" in it })
    }

    @Test
    fun `requests in flight never exceed 4`() {
        server.holdMillis = 40
        val result = pass(MapLibre(), areaOver(9874..9877, 12390..12395))

        println("MEASURED most requests in flight at once: ${server.maxInFlight.get()} of 24 tiles")
        assertEquals(DropOffMapPass.Whole(requests = 24, stored = 24, empty = 0), result)
        assertTrue("at most ${server.maxInFlight.get()} in flight", server.maxInFlight.get() <= DropOffMapLimits.MAX_IN_FLIGHT)
    }

    @Test
    fun `starts are at least 100 ms apart, so no more than 10 start in a second`() {
        val result = pass(MapLibre(), area)

        assertEquals(DropOffMapPass.Whole(requests = 9, stored = 9, empty = 0), result)
        assertTrue("nine starts took $now ms of the clock", now >= 8 * DropOffMapLimits.MIN_START_GAP_MILLIS)
    }

    @Test
    fun `a whole area writes its record, and its next pass asks for nothing and reads nothing from MapLibre`() {
        val mapLibre = MapLibre()
        assertEquals(DropOffMapPass.Whole(requests = 9, stored = 9, empty = 0), pass(mapLibre, area))
        val reads = mapLibre.reads.get()
        server.asked.clear()

        assertEquals(DropOffMapPass.Whole(requests = 0, stored = 0, empty = 0), pass(mapLibre, area))
        assertEquals(emptyList<TileId>(), server.asked.toList())
        assertEquals(reads, mapLibre.reads.get())
    }

    @Test
    fun `an area MapLibre already holds marked throughout is whole with no request`() {
        val result = pass(MapLibre(nine.associateWith { marked }), area)

        assertEquals(DropOffMapPass.Whole(requests = 0, stored = 0, empty = 0), result)
        assertEquals(emptyList<TileId>(), server.asked.toList())
        assertTrue(store.isWhole(3))
    }

    @Test
    fun `with no basemap TileJSON in MapLibre's store, nothing is fetched, and that is logged`() {
        val result = pass(MapLibre(json = null), area)

        assertTrue("$result", result is DropOffMapPass.NotRun)
        assertEquals(emptyList<TileId>(), server.asked.toList())
        assertTrue(logs.toString(), logs.any { "region 3" in it && "TileJSON" in it })
    }

    @Test
    fun `a pass for an area whose pass is running is not run`() {
        assertTrue(store.beginPass(3))

        val result = pass(MapLibre(), area)

        store.endPass(3)
        assertEquals(DropOffMapPass.NotRun("a pass for it is already running"), result)
        assertEquals(emptyList<TileId>(), server.asked.toList())
    }
}
