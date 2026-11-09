package com.zynergylabs.forager.app.map

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.zynergylabs.forager.app.domain.mapdata.TileId
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Deflater
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue

/**
 * Stores for dispatch 2026-11-05-01's tests: MapLibre's offline store as the app's read-only readers meet it while
 * MapLibre saves (the Phase 1 report, section 4), and as SQLite reports it corrupt. Built by the tests, never by MapLibre:
 * what MapLibre's own commits look like on a phone is a device check, not something these can prove.
 */
internal object MapLibreStoreFixtures {

    /** The first eight bytes of a valid rollback journal's header (SQLite's file format, section 3). */
    private val JOURNAL_MAGIC = intArrayOf(0xd9, 0xd5, 0x05, 0xf9, 0x20, 0xa1, 0x63, 0xd7).map { it.toByte() }.toByteArray()

    /**
     * A store laid out as MapLibre Native 13.5.0 lays out `mbgl-offline.db`: its `resources` and `tiles` tables, column for
     * column as `MapLibreOfflineBasemapTest` builds them, with [tileJson] as the one source TileJSON and [tiles] stored
     * deflated under [template]. In DELETE journal mode, as the phone's store is (the Phase 1 report, section 1.2).
     */
    fun store(file: File, tileJson: String, template: String, tiles: Map<TileId, ByteArray> = emptyMap()): File {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.rawQuery("PRAGMA journal_mode = DELETE", null).use { it.moveToFirst() }
            db.execSQL(
                "CREATE TABLE resources (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, url TEXT NOT NULL, kind INTEGER NOT NULL, " +
                    "expires INTEGER, modified INTEGER, etag TEXT, data BLOB, compressed INTEGER NOT NULL DEFAULT 0, " +
                    "accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0, UNIQUE (url))",
            )
            db.execSQL(
                "CREATE TABLE tiles (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, url_template TEXT NOT NULL, pixel_ratio INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, expires INTEGER, modified INTEGER, etag TEXT, data BLOB, " +
                    "compressed INTEGER NOT NULL DEFAULT 0, accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0, " +
                    "UNIQUE (url_template, pixel_ratio, z, x, y))",
            )
            db.insertOrThrow("resources", null, ContentValues().apply {
                put("url", "https://tiles.example.invalid/source.json"); put("kind", 2); put("modified", 5)
                put("data", deflate(tileJson.toByteArray())); put("compressed", 1); put("accessed", 0)
            })
            for ((id, bytes) in tiles) {
                db.insertOrThrow("tiles", null, ContentValues().apply {
                    put("url_template", template); put("pixel_ratio", 1); put("z", id.z); put("x", id.x); put("y", id.y)
                    put("data", deflate(bytes)); put("compressed", 1); put("accessed", 0)
                })
            }
        }
        return file
    }

    /**
     * A copy of [store] and of its rollback journal into [folder], taken while the test's own writer holds a transaction
     * open with [write] made and not committed. No connection holds a lock on the copy, so SQLite judges its journal hot
     * (its locking document, section 4.0: no RESERVED lock), and a read-only connection refuses it with
     * SQLITE_READONLY_ROLLBACK, as on the phone, where the reader cannot see MapLibre's lock (the Phase 1 report, 1.3).
     *
     * The writer runs at `synchronous = OFF`. At MapLibre's FULL the journal's header stays zero until the commit, so a copy
     * taken mid-transaction would not be hot and a test on it could not fail (observed, the Phase 1 report's scratch). The
     * copy is checked to be a hot pair before it is handed back; if it is not, the fixture fails, never the test's claim.
     */
    fun hotJournalCopy(store: File, folder: File, write: String): HotCopy {
        folder.mkdirs()
        val copy = HotCopy(File(folder, store.name))
        val writer = SQLiteDatabase.openDatabase(store.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            writer.rawQuery("PRAGMA journal_mode = DELETE", null).use { it.moveToFirst() }
            writer.execSQL("PRAGMA synchronous = OFF")
            writer.beginTransaction()
            try {
                writer.execSQL(write)
                store.copyTo(copy.database, overwrite = true)
                File(store.path + "-journal").copyTo(copy.journal, overwrite = true)
            } finally {
                writer.endTransaction() // not marked successful: rolled back, so the original store is left as it was
            }
        } finally {
            writer.close()
        }
        val journal = copy.journal.readBytes()
        assertTrue("fixture: the copied journal is ${journal.size} bytes, not over 512, so it is not hot", journal.size > 512)
        assertArrayEquals("fixture: the copied journal has no valid header, so it is not hot", JOURNAL_MAGIC, journal.copyOf(8))
        return copy
    }

    /**
     * [store] with the root page of every table and index overwritten with 0xFF, and `sqlite_master` left whole. A
     * statement prepares (it reads only the schema) and fails at its first step with SQLITE_CORRUPT, which is where
     * Android's query reports corruption to the database's error handler (`SQLiteQuery.fillWindow`, AOSP Android 16).
     */
    fun corruptRootPages(store: File): File {
        val (pageSize, roots) = SQLiteDatabase.openDatabase(store.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val size = db.rawQuery("PRAGMA page_size", null).use { c -> c.moveToFirst(); c.getInt(0) }
            val pages = db.rawQuery("SELECT rootpage FROM sqlite_master WHERE rootpage > 1", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getInt(0)) }
            }
            size to pages
        }
        assertTrue("fixture: no table or index root page to overwrite", roots.isNotEmpty())
        RandomAccessFile(store, "rw").use { f ->
            for (page in roots) {
                f.seek((page - 1L) * pageSize)
                f.write(ByteArray(pageSize) { 0xff.toByte() })
            }
        }
        return store
    }

    fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(bytes)
        d.finish()
        val out = ByteArray(bytes.size + 1024)
        val n = d.deflate(out)
        d.end()
        return out.copyOf(n)
    }
}

/** A store's copy and its journal, from [MapLibreStoreFixtures.hotJournalCopy]. */
internal class HotCopy(val database: File) {
    val journal: File = File(database.path + "-journal")

    /** Both files' bytes, to show a read wrote neither: no rollback, no write. */
    fun bytes(): List<ByteArray> = listOf(database.readBytes(), journal.readBytes())
}
