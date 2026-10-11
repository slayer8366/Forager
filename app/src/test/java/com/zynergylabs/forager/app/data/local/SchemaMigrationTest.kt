package com.zynergylabs.forager.app.data.local

import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every registered migration from 4→5 through 19→20, asserted against the schema files Room exports
 * to `app/schemas/` — not against a hand-written fixture. For each: the database is created at
 * version N **from `N.json`**, every table is seeded with a row that satisfies every NOT NULL column
 * *as `N.json` declares them*, the migration runs, [MigrationTestHelper.runMigrationsAndValidate]
 * validates the result against `N+1.json`, and the rows are asserted to have survived with the
 * specific values each migration carries or transforms. The last test runs the whole chain 4→20.
 *
 * **3→4 is not here and cannot be**: there is no `3.json` — versions 1–3 predate `exportSchema`
 * (see `ForagerDatabase`'s own history comment). `MushroomLogMigrationTest`'s `LegacyForagerDatabaseV3`
 * fixture remains the only coverage of that step.
 *
 * ## How the schema files reach Robolectric (wire-migration-tests dispatch, 2026-09-13)
 *
 * [MigrationTestHelper] reads `<database class>/<version>.json` through the Instrumentation
 * context's [AssetManager], which under Robolectric is the application's own (verified: same object).
 * AGP fills that only from the app's merged assets and merges no unit-test assets, and
 * `AssetManager.addAssetPath` rejects a plain directory (cookie 0) but accepts a zip whose entries
 * sit under `assets/` — the layout an APK uses. So [SchemaAssets] zips `app/schemas/` under that
 * prefix once and adds it to the AssetManager before each test. Test-only; nothing reaches an APK.
 * If Robolectric ever stops honouring this, every test here fails loudly with the helper's own
 * "Cannot find the schema file" — it cannot pass silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SchemaMigrationTest {

    private lateinit var helper: MigrationTestHelper

    @Before
    fun setUp() {
        SchemaAssets.install(ApplicationProvider.getApplicationContext<Context>().assets)
        helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ForagerDatabase::class.java)
    }

    // ---- one migration at a time --------------------------------------------------------------

    @Test fun `4 to 5 - existing rows survive, three track tables appear`() = migrate(4, 5, MIGRATION_4_5)

    @Test fun `5 to 6 - offlineRegionId is added null, offline_regions appears`() = migrate(5, 6, MIGRATION_5_6) { db ->
        assertNull(db.scalar("SELECT offlineRegionId FROM mushroom_log_entries"))
    }

    @Test fun `6 to 7 - the entries rebuild carries lat and lng through the NOT NULL drop`() =
        migrate(6, 7, MIGRATION_6_7, overrides = mapOf("mushroom_log_entries" to mapOf("lat" to 45.4301, "lng" to -122.2869))) { db ->
            assertEquals(45.4301, db.scalar("SELECT lat FROM mushroom_log_entries") as Double, 1e-9)
            assertEquals(-122.2869, db.scalar("SELECT lng FROM mushroom_log_entries") as Double, 1e-9)
        }

    @Test fun `7 to 8 - a photo's entryId becomes a log_entry_photos cross-reference`() =
        migrate(7, 8, MIGRATION_7_8, overrides = mapOf("log_photos" to mapOf("entryId" to "mushroom_log_entries-1")),
            // The one migration that fills a table it creates: each old log_photos.entryId becomes one cross-reference row.
            filledNewTables = mapOf("log_entry_photos" to 1L)) { db ->
            assertEquals(1L, db.scalar("SELECT COUNT(*) FROM log_entry_photos WHERE entryId = 'mushroom_log_entries-1' AND photoId = 'log_photos-1'"))
            assertNull(db.scalar("SELECT createdAtEpochMillis FROM log_photos"))
        }

    @Test fun `8 to 9 - a pre-existing entry is committed, never draft`() = migrate(8, 9, MIGRATION_8_9) { db ->
        assertEquals(0L, db.scalar("SELECT isDraft FROM mushroom_log_entries"))
        assertNull(db.scalar("SELECT draftOfEntryId FROM mushroom_log_entries"))
    }

    @Test fun `9 to 10 - day-scoped indexes are added and rows are untouched`() = migrate(9, 10, MIGRATION_9_10)

    @Test fun `10 to 11 - six cartography tables appear, existing rows survive`() = migrate(10, 11, MIGRATION_10_11)

    @Test fun `11 to 12 - the photo rebuild adds null latitude and longitude, path carried`() =
        migrate(11, 12, MIGRATION_11_12, overrides = mapOf("log_photos" to mapOf("relativePath" to "photos/keep.jpg"))) { db ->
            assertEquals("photos/keep.jpg", db.scalar("SELECT relativePath FROM log_photos"))
            assertNull(db.scalar("SELECT latitude FROM log_photos")); assertNull(db.scalar("SELECT longitude FROM log_photos"))
        }

    @Test fun `12 to 13 - waypoints and tracks rebuild with null links, values carried`() =
        migrate(12, 13, MIGRATION_12_13, overrides = mapOf("waypoints" to mapOf("name" to "Truck"), "tracks" to mapOf("name" to "Morning loop"))) { db ->
            assertEquals("Truck", db.scalar("SELECT name FROM waypoints")); assertNull(db.scalar("SELECT trackId FROM waypoints"))
            assertEquals("Morning loop", db.scalar("SELECT name FROM tracks")); assertNull(db.scalar("SELECT originWaypointId FROM tracks"))
        }

    @Test fun `13 to 14 - the waypoint rebuild adds null designation, trackId carried`() =
        migrate(13, 14, MIGRATION_13_14, overrides = mapOf("waypoints" to mapOf("trackId" to "tracks-1"))) { db ->
            assertEquals("tracks-1", db.scalar("SELECT trackId FROM waypoints")); assertNull(db.scalar("SELECT designation FROM waypoints"))
        }

    @Test fun `14 to 15 - the track_points rebuild adds null speed columns, fix carried`() =
        migrate(14, 15, MIGRATION_14_15, overrides = mapOf("track_points" to mapOf("timestampEpochMillis" to 1_700_000_000_000L))) { db ->
            assertEquals(1_700_000_000_000L, db.scalar("SELECT timestampEpochMillis FROM track_points"))
            assertNull(db.scalar("SELECT speedMetersPerSecond FROM track_points")); assertNull(db.scalar("SELECT speedAccuracyMetersPerSecond FROM track_points"))
        }

    // J8: the cartography_entries rebuild. Every seeded column is carried (assertEverySeededValueSurvived
    // compares them all by name), both indexes come back (Room validates them against 16.json), and the
    // new column reads 0 on the pre-existing row.
    @Test fun `15 to 16 - the cartography_entries rebuild adds shownOnMap false, every value carried`() =
        migrate(15, 16, MIGRATION_15_16, overrides = mapOf("cartography_entries" to mapOf("text" to "A good day.", "isDraft" to 0L, "updatedAtEpochMillis" to 1_758_000_000_000L))) { db ->
            assertEquals("A good day.", db.scalar("SELECT text FROM cartography_entries"))
            assertEquals(1_758_000_000_000L, db.scalar("SELECT updatedAtEpochMillis FROM cartography_entries"))
            assertEquals(0L, db.scalar("SELECT shownOnMap FROM cartography_entries"))
        }

    // F3 (dispatch 2026-09-28-195): a new table, so nothing is rebuilt. Room validates the result against
    // 17.json (the table's columns, its (entryId, trackId) key and its trackId index); the seeded track ref
    // beside it is carried by assertEverySeededValueSurvived; and migrate() asserts the new table starts empty,
    // because no track has ever been deletable, so no path has ever needed saving (the design's "no backfill").
    @Test fun `16 to 17 - cartography_entry_track_paths is created empty, keyed by entry and track, and the refs beside it are carried`() =
        migrate(16, 17, MIGRATION_16_17, overrides = mapOf("cartography_entry_track_refs" to mapOf("entryId" to "e-1", "trackId" to "t-1", "name" to "Ridge Loop"))) { db ->
            assertEquals("Ridge Loop", db.scalar("SELECT name FROM cartography_entry_track_refs"))
            db.execSQL("INSERT INTO cartography_entry_track_paths (entryId, trackId, path) VALUES ('e-1', 't-1', x'0102')")
            db.execSQL("INSERT INTO cartography_entry_track_paths (entryId, trackId, path) VALUES ('e-1', 't-2', x'')")
            db.execSQL("INSERT INTO cartography_entry_track_paths (entryId, trackId, path) VALUES ('e-2', 't-1', x'')")
            assertEquals("a row's path bytes are stored and read back", "0102", db.scalar("SELECT hex(path) FROM cartography_entry_track_paths WHERE entryId = 'e-1' AND trackId = 't-1'"))
            val duplicate = runCatching { db.execSQL("INSERT INTO cartography_entry_track_paths (entryId, trackId, path) VALUES ('e-1', 't-1', x'03')") }
            assertTrue("a second row for the same (entryId, trackId) is refused, so the key is the pair", duplicate.exceptionOrNull() is android.database.sqlite.SQLiteConstraintException)
            assertEquals("the trackId index the delete-time copy and any 'who keeps this track' lookup use", 1L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_cartography_entry_track_paths_trackId'"))
        }

    // Plan T16 (GPX import, RECORD -636): the tracks rebuild. Every seeded column is carried
    // (assertEverySeededValueSurvived compares them all by name, originWaypointId included), both indexes
    // come back (Room validates them against 18.json), and the pre-existing row reads as a recorded walk:
    // importedAtEpochMillis NULL and importedWithoutTimes 0. The new columns then take an imported track's
    // values, and a row inserted without importedWithoutTimes gets the column's default 0.
    @Test fun `17 to 18 - the tracks rebuild adds importedAtEpochMillis null and importedWithoutTimes 0, every value carried`() =
        migrate(17, 18, MIGRATION_17_18, overrides = mapOf("tracks" to mapOf("name" to "Morning loop", "endedAtEpochMillis" to 1_758_000_000_000L, "originWaypointId" to "w-1"))) { db ->
            assertEquals("Morning loop", db.scalar("SELECT name FROM tracks"))
            assertEquals("w-1", db.scalar("SELECT originWaypointId FROM tracks"))
            assertNull("a recorded walk is not imported", db.scalar("SELECT importedAtEpochMillis FROM tracks"))
            assertEquals("a recorded walk has its times", 0L, db.scalar("SELECT importedWithoutTimes FROM tracks"))
            db.execSQL("INSERT INTO tracks (id, name, startedAtEpochMillis, endedAtEpochMillis, importedAtEpochMillis, importedWithoutTimes) VALUES ('t-imp', 'Imported', 1, 2, 1759800000000, 1)")
            assertEquals(1_759_800_000_000L, db.scalar("SELECT importedAtEpochMillis FROM tracks WHERE id = 't-imp'"))
            assertEquals(1L, db.scalar("SELECT importedWithoutTimes FROM tracks WHERE id = 't-imp'"))
            db.execSQL("INSERT INTO tracks (id, name, startedAtEpochMillis) VALUES ('t-default', NULL, 3)")
            assertEquals("the column's own default", 0L, db.scalar("SELECT importedWithoutTimes FROM tracks WHERE id = 't-default'"))
            assertEquals("both time indexes are back", 2L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'tracks' AND name IN ('index_tracks_startedAtEpochMillis', 'index_tracks_endedAtEpochMillis')"))
        }

    // Dispatch 2026-09-28-695, amendment 1 (RECORD -696): the cached_searches rebuild. Every seeded column is
    // carried (assertEverySeededValueSurvived compares them all by name, filterIconicTaxonName included, which
    // still tells a category row from a species row), and the pre-existing row's new speciesIconicTaxonName is
    // NULL: no backfill, since nothing stored says which group an old species row belongs to. The column then
    // takes a species row's group.
    @Test fun `18 to 19 - the cached_searches rebuild adds speciesIconicTaxonName null, every value carried`() =
        migrate(18, 19, MIGRATION_18_19, overrides = mapOf("cached_searches" to mapOf("key" to "k-species", "filterLabel" to "Fly Agaric", "filterIconicTaxonName" to null, "filterTaxonId" to 48715L, "filterExcludedTaxonId" to null))) { db ->
            assertEquals("Fly Agaric", db.scalar("SELECT filterLabel FROM cached_searches"))
            assertEquals(48715L, db.scalar("SELECT filterTaxonId FROM cached_searches"))
            assertNull("an old row is not given a group", db.scalar("SELECT speciesIconicTaxonName FROM cached_searches"))
            db.execSQL("UPDATE cached_searches SET speciesIconicTaxonName = 'Fungi' WHERE `key` = 'k-species'")
            assertEquals("Fungi", db.scalar("SELECT speciesIconicTaxonName FROM cached_searches"))
            assertNull("the category discriminant is a separate column", db.scalar("SELECT filterIconicTaxonName FROM cached_searches"))
        }

    // Data part D, dispatch -697 Amendment 4 (RECORD -703): the mushroom_log_entries rebuild. Every seeded column is carried
    // (assertEverySeededValueSurvived compares them all by name) and the old row's new foundAtEpochMillis is NULL: no time of
    // day is known for a find saved before this column. Both indexes are back, and the column takes a value.
    @Test fun `19 to 20 - the mushroom_log_entries rebuild adds foundAtEpochMillis null, every value carried`() =
        migrate(19, 20, MIGRATION_19_20, overrides = mapOf("mushroom_log_entries" to mapOf("ownIdentification" to "Morel", "foundOn" to "2026-10-07", "lat" to 45.43, "lng" to -122.29, "draftOfEntryId" to null))) { db ->
            assertEquals("Morel", db.scalar("SELECT ownIdentification FROM mushroom_log_entries"))
            assertEquals("2026-10-07", db.scalar("SELECT foundOn FROM mushroom_log_entries"))
            assertNull("an old find has no time of day", db.scalar("SELECT foundAtEpochMillis FROM mushroom_log_entries"))
            db.execSQL("UPDATE mushroom_log_entries SET foundAtEpochMillis = 1791403440000")
            assertEquals(1_791_403_440_000L, db.scalar("SELECT foundAtEpochMillis FROM mushroom_log_entries"))
            assertEquals("both indexes are back", 2L, db.scalar("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'mushroom_log_entries' AND name IN ('index_mushroom_log_entries_offlineRegionId', 'index_mushroom_log_entries_foundOn')"))
        }

    // ---- the whole chain ----------------------------------------------------------------------

    // The chain now ends at 20, the current version (RECORD -703; it ended at 19 before MIGRATION_19_20).
    @Test fun `4 to 20 - the full chain, validated against 20_json, every seeded value survives`() {
        val name = "chain.db"
        val seeded = helper.createDatabase(name, 4).use { db -> seedEveryTable(db, 4, mapOf("mushroom_log_entries" to mapOf("lat" to 45.4301, "lng" to -122.2869))) }
        val db = helper.runMigrationsAndValidate(name, 20, true, *ForagerDatabase.ALL_MIGRATIONS)
        try {
            assertEverySeededValueSurvived(db, seeded, 4, 20)
            assertEquals(0L, db.scalar("SELECT isDraft FROM mushroom_log_entries"))
            assertEquals(1L, db.scalar("SELECT COUNT(*) FROM log_entry_photos"))
        } finally { db.close() }
    }

    // ---- machinery ----------------------------------------------------------------------------

    /** Create at [from] from `from.json`, seed every table, migrate with [migration], validate against `to.json`, assert every value survived, then [extra]. */
    private fun migrate(
        from: Int, to: Int, migration: Migration,
        overrides: Map<String, Map<String, Any?>> = emptyMap(),
        filledNewTables: Map<String, Long> = emptyMap(),
        extra: (SupportSQLiteDatabase) -> Unit = {},
    ) {
        val name = "m$from.db"
        val seeded = helper.createDatabase(name, from).use { db -> seedEveryTable(db, from, overrides) }
        val db = helper.runMigrationsAndValidate(name, to, true, migration) // validates the result against to.json
        try {
            assertEverySeededValueSurvived(db, seeded, from, to)
            for (table in SchemaAssets.tables(to) - seeded.keys) {
                assertEquals("rows in new table $table after $from->$to", filledNewTables[table] ?: 0L, db.scalar("SELECT COUNT(*) FROM `$table`"))
            }
            extra(db)
        } finally { db.close() }
    }

    /**
     * Every table seeded at [from] still has its one row at [to], and every column that exists at both
     * versions reads back exactly what was seeded. Columns new at [to] are each test's own business;
     * columns dropped at [to] (log_photos.entryId at 7->8) are not compared. This is the check that
     * makes a rebuild migration's `INSERT ... SELECT` list honest: a column copied as NULL, or left
     * out of the list and defaulted, fails here by name.
     */
    private fun assertEverySeededValueSurvived(db: SupportSQLiteDatabase, seeded: Map<String, Map<String, Any?>>, from: Int, to: Int) {
        val columnsAt = SchemaAssets.entities(to).associate { (table, fields) -> table to fields.map { it.name }.toSet() }
        for ((table, row) in seeded) {
            val columns = columnsAt[table] ?: throw AssertionError("table $table exists at v$from and not at v$to")
            assertEquals("rows in $table after $from->$to", 1L, db.scalar("SELECT COUNT(*) FROM `$table`"))
            for (col in row.keys intersect columns) {
                val expected = row.getValue(col).let { if (it is ByteArray) it.toList() else it }
                val actual = db.scalar("SELECT `$col` FROM `$table`").let { if (it is ByteArray) it.toList() else it }
                assertEquals("$table.$col after $from->$to", expected, actual)
            }
        }
    }

    /**
     * One row per table at [version], every column filled with a value of its own affinity *as
     * `version.json` declares it* — nullable columns included, so a migration that loses one is
     * caught, not excused — with [overrides] for the values a test wants to see carried. Returns the
     * seeded row per table.
     */
    private fun seedEveryTable(db: SupportSQLiteDatabase, version: Int, overrides: Map<String, Map<String, Any?>>): Map<String, Map<String, Any?>> =
        SchemaAssets.entities(version).associate { (table, fields) ->
            val cv = ContentValues()
            val row = fields.associate { (col, affinity, _) ->
                val over = overrides[table]
                val v: Any? = if (over != null && over.containsKey(col)) over[col] else when {
                    // Affinity before name: track_points.id (v5+) and offline_regions.id (v6+) are
                    // INTEGER primary keys, and a string in a rowid alias is SQLITE_MISMATCH, not coercion.
                    affinity == "INTEGER" -> 1L
                    col == "id" -> "$table-1"
                    affinity == "REAL" -> 1.5
                    affinity == "TEXT" -> "$col-1"
                    // T16: 17.json is the first starting point with a BLOB column (cartography_entry_track_paths.path).
                    affinity == "BLOB" -> byteArrayOf(1, 2, 3)
                    else -> error("no seed rule for affinity $affinity at $table.$col in $version.json")
                }
                when (v) {
                    null -> cv.putNull(col)
                    is String -> cv.put(col, v); is Long -> cv.put(col, v); is Int -> cv.put(col, v); is Double -> cv.put(col, v); is ByteArray -> cv.put(col, v)
                    else -> error("unsupported seed value for $table.$col: $v")
                }
                col to v
            }
            check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) != -1L) { "seed insert failed for $table at v$version" }
            table to row
        }

    private fun SupportSQLiteDatabase.scalar(sql: String): Any? = query(sql).use { c ->
        check(c.moveToFirst()) { "no row for: $sql" }
        when (c.getType(0)) { android.database.Cursor.FIELD_TYPE_NULL -> null; android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(0); android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(0); android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(0); else -> c.getString(0) }
    }
}

/** The committed schemas, served to Robolectric as assets — see [SchemaMigrationTest]'s class doc. */
internal object SchemaAssets {
    private const val DB = "com.zynergylabs.forager.app.data.local.ForagerDatabase"
    private val root = File("schemas") // Gradle runs unit tests with workingDir = app/
    private val zip: File by lazy {
        File.createTempFile("forager-schemas-", ".zip").also { z ->
            ZipOutputStream(FileOutputStream(z)).use { out ->
                root.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { f ->
                    out.putNextEntry(ZipEntry("assets/" + f.relativeTo(root).path.replace(File.separatorChar, '/')))
                    f.inputStream().use { it.copyTo(out) }; out.closeEntry()
                }
            }
        }
    }

    fun install(assets: AssetManager) {
        val cookie = AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, zip.absolutePath) as Int
        check(cookie != 0) { "Robolectric's AssetManager rejected the schema zip at ${zip.absolutePath}" }
    }

    data class Field(val name: String, val affinity: String, val notNull: Boolean)

    /** Every entity at [version] with its fields, straight from `version.json`. */
    fun entities(version: Int): List<Pair<String, List<Field>>> {
        val json = Json.parseToJsonElement(File(root, "$DB/$version.json").readText()).jsonObject
        return json["database"]!!.jsonObject["entities"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            o["tableName"]!!.jsonPrimitive.content to o["fields"]!!.jsonArray.map { f ->
                val fo = f.jsonObject
                Field(fo["columnName"]!!.jsonPrimitive.content, fo["affinity"]!!.jsonPrimitive.content, fo["notNull"]?.jsonPrimitive?.booleanOrNull ?: false)
            }
        }
    }

    fun tables(version: Int): Set<String> = entities(version).map { it.first }.toSet()
}
