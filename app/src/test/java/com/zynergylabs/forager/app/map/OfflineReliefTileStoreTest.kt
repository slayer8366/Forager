package com.zynergylabs.forager.app.map

import android.database.sqlite.SQLiteDatabase
import com.zynergylabs.forager.app.domain.relief.ReliefTileRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

/**
 * Dispatch 2026-11-03-07, D2: the relief tile read out of MapLibre's offline database, on a database the SDK itself wrote
 * (`src/test/resources/relief/README.md`), and on copies of it changed one way each. Native graphics, so the stored PNG
 * is decoded for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfflineReliefTileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun fixture(): File {
        val copy = folder.newFile("mbgl-offline.db")
        javaClass.getResourceAsStream("/relief/mbgl-offline-relief.db")!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
        return copy
    }

    private fun File.change(vararg sql: String): File = also {
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db -> sql.forEach(db::execSQL) }
    }

    private fun found(read: ReliefTileRead): ReliefTileRead.Found {
        assertTrue("expected a tile, got $read", read is ReliefTileRead.Found)
        return read as ReliefTileRead.Found
    }

    @Test
    fun `the SDK's own stored relief tile, deflated, reads as the summit's height`() {
        val tile = found(OfflineReliefTileStore(fixture()).tile(11, 331, 733)).tile

        assertEquals(256, tile.size)
        // The same pixel the spike read on the emulator, and the live Worker's tile on the laptop.
        assertEquals(3370.01953125, tile.heightAt(175, 179), 0.0)
    }

    @Test
    fun `the same tile stored without compression reads the same`() {
        val db = fixture()
        val stored = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            d.rawQuery("SELECT data, compressed FROM tiles WHERE z = 11 AND x = 331 AND y = 733", null).use { c ->
                c.moveToFirst()
                assertEquals("the fixture's row is the compressed kind", 1, c.getInt(1))
                c.getBlob(0)
            }
        }
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
            d.execSQL("UPDATE tiles SET data = ?, compressed = 0 WHERE z = 11 AND x = 331 AND y = 733", arrayOf<Any>(inflate(stored)))
        }

        assertEquals(3370.01953125, found(OfflineReliefTileStore(db).tile(11, 331, 733)).tile.heightAt(175, 179), 0.0)
    }

    @Test
    fun `a tile no region links to, the ambient cache, is not counted`() {
        val db = fixture().change("DELETE FROM region_tiles")
        assertEquals(ReliefTileRead.NotStored, OfflineReliefTileStore(db).tile(11, 331, 733))
    }

    @Test
    fun `a tile no region stored is not stored`() {
        assertEquals(ReliefTileRead.NotStored, OfflineReliefTileStore(fixture()).tile(11, 332, 733))
    }

    @Test
    fun `a tile stored with no content is empty`() {
        val db = fixture().change("UPDATE tiles SET data = NULL")
        assertEquals(ReliefTileRead.Empty, OfflineReliefTileStore(db).tile(11, 331, 733))
    }

    @Test
    fun `another style's relief URL is not this one`() {
        val read = OfflineReliefTileStore(fixture(), urlTemplate = "https://example.invalid/relief/{z}/{x}/{y}.png").tile(11, 331, 733)
        assertEquals(ReliefTileRead.NotStored, read)
    }

    @Test
    fun `no database at all is nothing stored`() {
        assertEquals(ReliefTileRead.NotStored, OfflineReliefTileStore(File(folder.root, "absent.db")).tile(11, 331, 733))
    }

    @Test
    fun `another schema version is unsupported, and says which`() {
        val db = fixture().change("PRAGMA user_version = 7")
        assertEquals(ReliefTileRead.Unsupported("user_version 7, not 6"), OfflineReliefTileStore(db).tile(11, 331, 733))
    }

    @Test
    fun `a tiles table without a column this reads is unsupported, and says which`() {
        val db = File(folder.root, "other.db")
        SQLiteDatabase.openOrCreateDatabase(db, null).use { d ->
            d.execSQL("CREATE TABLE tiles (id INTEGER PRIMARY KEY, url_template TEXT, pixel_ratio INTEGER, z INTEGER, x INTEGER, y INTEGER, modified INTEGER, data BLOB)")
            d.execSQL("CREATE TABLE region_tiles (region_id INTEGER, tile_id INTEGER)")
            d.execSQL("PRAGMA user_version = 6")
        }
        assertEquals(ReliefTileRead.Unsupported("table tiles lacks [compressed]"), OfflineReliefTileStore(db).tile(11, 331, 733))
    }

    @Test
    fun `stored bytes that are not an image fail, never read as a height`() {
        val db = fixture()
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
            d.execSQL("UPDATE tiles SET data = ?, compressed = 0", arrayOf<Any>(ByteArray(64) { it.toByte() }))
        }
        val read = OfflineReliefTileStore(db).tile(11, 331, 733)
        assertTrue("expected a failure, got $read", read is ReliefTileRead.Failed)
    }

    /** What MapLibre's own region delete does (`offline_database.cpp` `deleteRegion` at android-v13.5.0): the region row, its links by cascade; the tile row stays. */
    private fun File.deleteRegionAsTheSdkDoes(): File = change("PRAGMA foreign_keys = ON", "DELETE FROM regions")

    @Test
    fun `a linked tile has a fingerprint, the same each time and the one its read carried`() {
        val store = OfflineReliefTileStore(fixture())
        val first = store.linkedFingerprint(11, 331, 733)
        assertTrue("a fingerprint while a region holds it", first != null)
        assertEquals(first, store.linkedFingerprint(11, 331, 733))
        assertEquals(first, found(store.tile(11, 331, 733)).fingerprint)
    }

    @Test
    fun `after the SDK's region delete the tile row stays, with no fingerprint and not stored`() {
        val db = fixture().deleteRegionAsTheSdkDoes()
        val rows = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            d.rawQuery("SELECT (SELECT count(*) FROM tiles), (SELECT count(*) FROM region_tiles)", null).use { c -> c.moveToFirst(); c.getInt(0) to c.getInt(1) }
        }
        assertEquals("as on the S22: the tile row kept, its region link gone", 1 to 0, rows)
        val store = OfflineReliefTileStore(db)
        assertEquals(null, store.linkedFingerprint(11, 331, 733))
        assertEquals(ReliefTileRead.NotStored, store.tile(11, 331, 733))
    }

    @Test
    fun `a height read before a region's delete is not shown after it`() = kotlinx.coroutines.test.runTest {
        // S22 session 11, through the real reader on the SDK's database: the source's kept tile must not outlive the delete.
        val db = fixture()
        val source = com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource(
            OfflineReliefTileStore(db),
            { _, _, _ -> },
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
        )
        val summit = com.zynergylabs.forager.app.domain.model.LatLng(45.3736, -121.6959)
        assertTrue("a height before the delete", source.groundHeightAt(summit) is com.zynergylabs.forager.app.domain.GroundHeight.Known)

        db.deleteRegionAsTheSdkDoes()

        assertEquals(
            com.zynergylabs.forager.app.domain.GroundHeight.Unknown(com.zynergylabs.forager.app.domain.GroundHeightUnknown.NOT_DOWNLOADED),
            source.groundHeightAt(summit),
        )
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) out.write(buffer, 0, inflater.inflate(buffer))
        inflater.end()
        return out.toByteArray()
    }
}
