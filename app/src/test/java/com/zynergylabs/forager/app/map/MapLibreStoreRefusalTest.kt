package com.zynergylabs.forager.app.map

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import com.zynergylabs.forager.app.domain.GroundHeight
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource
import com.zynergylabs.forager.app.domain.relief.ReliefTileRead
import com.zynergylabs.forager.app.routing.DropOffTestData
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-01, T1 (Amendment 1, A5, A7, B1, B2): a read of MapLibre's store that SQLite refuses because
 * MapLibre is mid-save is tried again within the operating limits, by both readers of the store, and one still refused at
 * the bound fails with the refusal, as today, having written nothing. The refusal is made with
 * [MapLibreStoreFixtures.hotJournalCopy]: SQLITE_READONLY_ROLLBACK, the phone's error in S22 session 14. The test's pause
 * stands in for the wait, and removes the journal when a refusal is to clear, as MapLibre's commit does when it ends.
 *
 * Native graphics, so the relief tile's PNG is decoded for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapLibreStoreRefusalTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logs = mutableListOf<String>()
    private val log: (String, Throwable?) -> Unit = { message, _ -> synchronized(logs) { logs += message } }
    private val pauses = mutableListOf<Long>()

    private val template = "https://tiles.example.invalid/us/{z}/{x}/{y}.mvt"
    private val fixtureTile = TileId(15, 9875, 12393)
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val tileJson = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/tilejson-with-0003.json")).toURI()).readText()

    /** The basemap's store, held mid-save: its TileJSON and the fixture tile, with a resource read's `accessed` write open. */
    private fun basemapMidSave(): HotCopy {
        val store = MapLibreStoreFixtures.store(File(tmp.newFolder("live"), "mbgl-offline.db"), tileJson, template, mapOf(fixtureTile to tile))
        return MapLibreStoreFixtures.hotJournalCopy(store, tmp.newFolder("mid-save"), "UPDATE resources SET accessed = accessed + 1")
    }

    /** The relief's store, the database the SDK itself wrote (`OfflineReliefTileStoreTest`), held mid-save on a tile's `accessed`. */
    private fun reliefMidSave(): HotCopy {
        val store = File(tmp.newFolder("relief-live"), "mbgl-offline.db")
        javaClass.getResourceAsStream("/relief/mbgl-offline-relief.db")!!.use { input -> store.outputStream().use { input.copyTo(it) } }
        return MapLibreStoreFixtures.hotJournalCopy(store, tmp.newFolder("relief-mid-save"), "UPDATE tiles SET accessed = accessed + 1")
    }

    /** The pause: records each wait, and when [clears], the save ends at the first one (its journal goes, as at a commit's end). */
    private fun pause(copy: HotCopy, clears: Boolean): (Long) -> Unit = { millis ->
        pauses += millis
        if (clears) copy.journal.delete()
    }

    private fun assertNothingWritten(before: List<ByteArray>, copy: HotCopy) {
        val after = copy.bytes()
        assertArrayEquals("the database is byte for byte as it was: no rollback, no write", before[0], after[0])
        assertArrayEquals("the journal is byte for byte as it was: no rollback, no write", before[1], after[1])
    }

    private fun describe(read: ReliefTileRead) = when (read) {
        is ReliefTileRead.Failed -> "Failed(${read.error})"
        else -> read.javaClass.simpleName
    }

    // ── The fixture's own control: it passes before and after the change, by design ──

    @Test
    fun `fixture control - a plain read-only open of the store mid-save is refused with SQLITE_READONLY_ROLLBACK`() {
        val copy = basemapMidSave()
        val refused = assertThrows(SQLiteReadOnlyDatabaseException::class.java) {
            SQLiteDatabase.openDatabase(copy.database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT count(*) FROM tiles", null).use { it.moveToFirst() }
            }
        }
        assertTrue("the phone's code, 776: ${refused.message}", "776" in refused.message.orEmpty())
    }

    // ── MapLibreOfflineBasemap: the way-to's and the copy pass's reader ──

    @Test
    fun `a basemap read refused while MapLibre saves is tried again, reads the tile, and says how many attempts it took`() = runBlocking {
        val copy = basemapMidSave()
        val basemap = MapLibreOfflineBasemap(copy.database, log = log, pause = pause(copy, clears = true))

        val read = basemap.tile(fixtureTile)

        assertTrue("expected the tile, got $read (logged: $logs)", read.isSuccess)
        assertArrayEquals(tile, read.getOrThrow())
        assertEquals("one wait, the first of B1's", listOf(50L), pauses)
        assertEquals("one line for the read that needed a retry: $logs", 1, logs.size)
        assertTrue(logs.single(), "attempt 2 of 5" in logs.single())
    }

    @Test
    fun `a basemap read still refused at the bound fails with the refusal after 5 attempts, writes nothing, and says so once`() = runBlocking {
        val copy = basemapMidSave()
        val before = copy.bytes()
        val basemap = MapLibreOfflineBasemap(copy.database, log = log, pause = pause(copy, clears = false))

        val read = basemap.tileJson()

        assertTrue("expected the refusal, got $read", read.exceptionOrNull() is SQLiteReadOnlyDatabaseException)
        assertEquals("B1: 5 attempts, with waits of 50, 100, 200 and 400 ms between them", listOf(50L, 100L, 200L, 400L), pauses)
        assertEquals("one line for the read that failed at the bound: $logs", 1, logs.size)
        assertTrue(logs.single(), "5 attempts" in logs.single())
        assertNothingWritten(before, copy)
    }

    // ── OfflineReliefTileStore: the waypoint bubble's ground height (A7) ──

    @Test
    fun `a relief read refused while MapLibre saves is tried again and reads the summit's height`() {
        val copy = reliefMidSave()
        val store = OfflineReliefTileStore(copy.database, log = log, pause = pause(copy, clears = true))

        val read = store.tile(11, 331, 733)

        assertTrue("expected the tile, got ${describe(read)} (logged: $logs)", read is ReliefTileRead.Found)
        assertEquals(3370.01953125, (read as ReliefTileRead.Found).tile.heightAt(175, 179), 0.0)
        assertEquals("one wait, the first of B1's", listOf(50L), pauses)
        assertEquals("one line for the read that needed a retry: $logs", 1, logs.size)
        assertTrue(logs.single(), "attempt 2 of 5" in logs.single())
    }

    @Test
    fun `a relief read still refused at the bound fails with the refusal after 5 attempts, writes nothing, and says so once`() {
        val copy = reliefMidSave()
        val before = copy.bytes()
        val store = OfflineReliefTileStore(copy.database, log = log, pause = pause(copy, clears = false))

        val read = store.tile(11, 331, 733)

        assertTrue("expected the refusal, got ${describe(read)}", read is ReliefTileRead.Failed && read.error is SQLiteReadOnlyDatabaseException)
        assertEquals("B1: 5 attempts, with waits of 50, 100, 200 and 400 ms between them", listOf(50L, 100L, 200L, 400L), pauses)
        assertEquals("one line for the read that failed at the bound: $logs", 1, logs.size)
        assertTrue(logs.single(), "5 attempts" in logs.single())
        assertNothingWritten(before, copy)
    }

    @Test
    fun `the relief tile's fingerprint read refused while MapLibre saves is tried again and is the tile's own`() {
        val copy = reliefMidSave()
        // The same row read from the live store, which no save holds now (the writer rolled back).
        val expected = OfflineReliefTileStore(File(tmp.root, "relief-live/mbgl-offline.db")).linkedFingerprint(11, 331, 733)
        assertTrue("positive control: the live store's tile has a fingerprint", expected != null)
        val store = OfflineReliefTileStore(copy.database, log = log, pause = pause(copy, clears = true))

        assertEquals("logged: $logs", expected, store.linkedFingerprint(11, 331, 733))
        assertEquals(listOf(50L), pauses)
    }

    @Test
    fun `through the waypoint bubble's height source, a refused read still gives the summit's height`() = runTest {
        val copy = reliefMidSave()
        val source = ReliefGroundHeightSource(
            OfflineReliefTileStore(copy.database, log = log, pause = pause(copy, clears = true)),
            { _, message, _ -> synchronized(logs) { logs += message } },
            StandardTestDispatcher(testScheduler),
        )

        val height = source.groundHeightAt(LatLng(45.3736, -121.6959))

        assertTrue("expected a height, got $height (logged: $logs)", height is GroundHeight.Known)
        assertEquals("one retry line, and nothing else logged: $logs", 1, logs.size)
    }
}
