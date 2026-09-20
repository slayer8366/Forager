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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every registered migration from 4→5 through 14→15, asserted against the schema files Room exports
 * to `app/schemas/` — not against a hand-written fixture. For each: the database is created at
 * version N **from `N.json`**, every table is seeded with a row that satisfies every NOT NULL column
 * *as `N.json` declares them*, the migration runs, [MigrationTestHelper.runMigrationsAndValidate]
 * validates the result against `N+1.json`, and the rows are asserted to have survived with the
 * specific values each migration carries or transforms. The last test runs the whole chain 4→15.
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

    // ---- the whole chain ----------------------------------------------------------------------

    @Test fun `4 to 15 - the full chain, validated against 15_json, every seeded value survives`() {
        val name = "chain.db"
        val seeded = helper.createDatabase(name, 4).use { db -> seedEveryTable(db, 4, mapOf("mushroom_log_entries" to mapOf("lat" to 45.4301, "lng" to -122.2869))) }
        val db = helper.runMigrationsAndValidate(name, 15, true, *ALL_MIGRATIONS)
        try {
            assertEverySeededValueSurvived(db, seeded, 4, 15)
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
                assertEquals("$table.$col after $from->$to", row.getValue(col), db.scalar("SELECT `$col` FROM `$table`"))
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
                    else -> error("no seed rule for affinity $affinity at $table.$col in $version.json")
                }
                when (v) {
                    null -> cv.putNull(col)
                    is String -> cv.put(col, v); is Long -> cv.put(col, v); is Int -> cv.put(col, v); is Double -> cv.put(col, v)
                    else -> error("unsupported seed value for $table.$col: $v")
                }
                col to v
            }
            check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) != -1L) { "seed insert failed for $table at v$version" }
            table to row
        }

    private fun SupportSQLiteDatabase.scalar(sql: String): Any? = query(sql).use { c ->
        check(c.moveToFirst()) { "no row for: $sql" }
        when (c.getType(0)) { android.database.Cursor.FIELD_TYPE_NULL -> null; android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(0); android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(0); else -> c.getString(0) }
    }

    private companion object {
        val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
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
