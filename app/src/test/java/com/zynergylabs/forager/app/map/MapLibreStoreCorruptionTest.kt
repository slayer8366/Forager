package com.zynergylabs.forager.app.map

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.relief.ReliefTileRead
import com.zynergylabs.forager.app.routing.DropOffTestData
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
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

/**
 * Dispatch 2026-11-05-01, T2 (Amendment 1, A6, B2): no read of MapLibre's store can delete it. Android's default error
 * handler, which `openDatabase(path, null, OPEN_READONLY)` installs, deletes the database file when SQLite reports
 * corruption (`DefaultDatabaseErrorHandler`, AOSP Android 16, the framework Robolectric runs here). On the app's read-only
 * connection that file would be MapLibre's store, with every offline area. Each of the three reads of the store in the app
 * is given a store SQLite reports corrupt ([MapLibreStoreFixtures.corruptRootPages]): the read fails, as a read failure
 * does today, the file is left byte for byte, a corruption line is logged, and nothing is retried (B1: only a refusal is).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapLibreStoreCorruptionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logs = mutableListOf<String>()
    private val log: (String, Throwable?) -> Unit = { message, _ -> synchronized(logs) { logs += message } }
    private val pauses = mutableListOf<Long>()

    private val template = "https://tiles.example.invalid/us/{z}/{x}/{y}.mvt"
    private val fixtureTile = TileId(15, 9875, 12393)
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val tileJson = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/tilejson-with-0003.json")).toURI()).readText()

    private fun corruptBasemapStore(): File = MapLibreStoreFixtures.corruptRootPages(
        MapLibreStoreFixtures.store(File(tmp.newFolder("basemap"), "mbgl-offline.db"), tileJson, template, mapOf(fixtureTile to tile)),
    )

    /** The database the SDK itself wrote (`OfflineReliefTileStoreTest`), reported corrupt. */
    private fun corruptReliefStore(): File {
        val store = File(tmp.newFolder("relief"), "mbgl-offline.db")
        javaClass.getResourceAsStream("/relief/mbgl-offline-relief.db")!!.use { input -> store.outputStream().use { input.copyTo(it) } }
        return MapLibreStoreFixtures.corruptRootPages(store)
    }

    private fun assertStoreKept(store: File, before: ByteArray) {
        assertTrue("the store is still there after a corruption report on a read (logged: $logs)", store.isFile)
        assertArrayEquals("the store is byte for byte as it was", before, store.readBytes())
    }

    private fun assertReportedNotRetried() {
        assertTrue("a corruption report is logged: $logs", logs.any { "corrupt" in it })
        assertEquals("a corruption report is not a refusal, so nothing is retried", emptyList<Long>(), pauses)
    }

    // ── The fixture's own control: it passes before and after the change, by design ──

    @Test
    fun `fixture control - a query on the store with its root pages overwritten is reported corrupt to the error handler`() {
        val store = corruptBasemapStore()
        val reports = AtomicInteger()
        assertThrows(SQLiteDatabaseCorruptException::class.java) {
            SQLiteDatabase.openDatabase(store.path, null, SQLiteDatabase.OPEN_READONLY, DatabaseErrorHandler { reports.incrementAndGet() }).use { db ->
                db.rawQuery("SELECT data FROM resources", null).use { it.moveToFirst() }
            }
        }
        assertTrue("the error handler was told", reports.get() > 0)
    }

    // ── MapLibreOfflineBasemap ──

    @Test
    fun `a basemap read of a store SQLite reports corrupt fails, and the store is kept, byte for byte`() {
        val store = corruptBasemapStore()
        val before = store.readBytes()

        val read = runBlocking { MapLibreOfflineBasemap(store, log = log, pause = { pauses += it }).tileJson() }

        assertStoreKept(store, before)
        assertTrue("the read fails, as a read failure does today: $read", read.exceptionOrNull() is SQLiteDatabaseCorruptException)
        assertReportedNotRetried()
    }

    // ── OfflineReliefTileStore: its two reads ──

    @Test
    fun `a relief tile read of a store SQLite reports corrupt fails, and the store is kept, byte for byte`() {
        val store = corruptReliefStore()
        val before = store.readBytes()

        val read = OfflineReliefTileStore(store, log = log, pause = { pauses += it }).tile(11, 331, 733)

        assertStoreKept(store, before)
        assertTrue("the read fails, as a read failure does today", read is ReliefTileRead.Failed && read.error is SQLiteDatabaseCorruptException)
        assertReportedNotRetried()
    }

    @Test
    fun `a relief fingerprint read of a store SQLite reports corrupt is no fingerprint, and the store is kept, byte for byte`() {
        val store = corruptReliefStore()
        val before = store.readBytes()

        val fingerprint = OfflineReliefTileStore(store, log = log, pause = { pauses += it }).linkedFingerprint(11, 331, 733)

        assertStoreKept(store, before)
        assertEquals("no fingerprint, as when the store cannot be read today", null, fingerprint)
        assertReportedNotRetried()
    }
}
