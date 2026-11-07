package com.zynergylabs.forager.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.local.SchemaAssets
import com.zynergylabs.forager.app.domain.RestoreMode
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 2 (RECORD -2187): users' phones are at schema 22 (main), testers' at 23 (pre-main), and this
 * build writes 24. A backup made by either restores into this build through `ForagerDatabase.openForRestore` (the scratch copy's
 * migrations, no destructive fallback in any build), and every record in it survives: every journal table's every row, every
 * column the older schema had, with the value it had. The two new link columns arrive NULL.
 *
 * Not `JournalBackupTest`, which is over 900 lines (the modular-code skill). The backup is built here rather than with
 * `OlderBackup.build`, so its rows hang together (a route stop names its trip, so Replace keeps it) and can be read back to compare.
 *
 * Three values a Replace rewrites by design are compared by what they point at, not by value: an offline region gets a fresh
 * negative id, and the find and the journal entry that name it follow it (`JournalBackupTest`, "Replace gives a restored region a
 * fresh id"); `track_points.id` is an auto key that is never carried.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OlderBackupRestoreTo24Test {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var phone: Phone? = null

    @After
    fun close() {
        phone?.let { runCatching { it.close() } }
    }

    @Test
    fun `a backup made at schema 22 restores into 24 through openForRestore with every record surviving`() = restoresWhole(22)

    @Test
    fun `a backup made at schema 23, routes included, restores into 24 with every record surviving and no link`() = restoresWhole(23)

    private fun restoresWhole(version: Int) {
        val (archive, seeded) = buildBackup(version)
        val b = Phone(context, "restore-from-$version.db", tmp.root).also { phone = it }

        runBlocking { b.service.restore(ByteArrayInputStream(archive), RestoreMode.REPLACE) }.getOrThrow()

        assertEquals("nothing was logged as refused or dropped: ${b.logged}", emptyList<String>(), b.logged)
        val restoredVersion = b.database.openHelper.readableDatabase.version
        assertEquals("the phone is at this build's schema", 24, restoredVersion)
        val regionId = b.scalar("SELECT id FROM offline_regions") ?: "NULL"
        for ((table, rows) in seeded) {
            val columns = SchemaAssets.entities(version).first { it.first == table }.second.map { it.name }
            val restored = b.rows(table, columns)
            assertEquals("$table: every row survives", rows.size, restored.size)
            val expected = rows.map { row -> row.mapValues { (column, value) -> if (Pair(table, column) in FOLLOWS_REGION) regionId else value } }
            assertEquals("$table: every column the v$version schema had, with its value", expected.map { it.withoutKeys(table) }.sortedBy { it.toString() }, restored.map { it.withoutKeys(table) }.sortedBy { it.toString() })
        }
        if (version >= 23) {
            assertEquals("the route stop survived", 1L, b.count("planned_trip_route_stops"))
            assertEquals("the new link columns arrive NULL", "NULL|NULL", b.scalar("SELECT IFNULL(linkedWaypointId, 'NULL') || '|' || IFNULL(linkedTripId, 'NULL') FROM planned_trip_route_stops"))
        }
        assertEquals("the scratch copy is gone", emptyList<String>(), b.scratchLeftovers())
    }

    /**
     * A real database at [version], built from `<version>.json`, one row in every journal table (an affinity value per column, as
     * `OlderBackup.build` seeds), with the links that matter pointing at real rows; then archived as that version's app archives
     * it. Returns the archive and what was seeded, read back from the file, by table.
     */
    private fun buildBackup(version: Int): Pair<ByteArray, Map<String, List<Map<String, String>>>> {
        val helper = OlderBackup.helper()
        val name = "made-at-$version.db"
        helper.createDatabase(name, version).use { db ->
            for ((table, fields) in SchemaAssets.entities(version)) {
                if (table !in JOURNAL_TABLE_NAMES) continue
                val cv = ContentValues()
                for (f in fields) {
                    val override = OVERRIDES[Pair(table, f.name)]
                    when {
                        override != null -> cv.put(f.name, override)
                        f.name == "rawRelativePath" -> cv.putNull(f.name) // a made-up DNG path would be a file the restore looks for
                        f.affinity == "INTEGER" -> cv.put(f.name, 1L)
                        f.name == "id" -> cv.put(f.name, "$table-1")
                        f.affinity == "REAL" -> cv.put(f.name, 1.5)
                        else -> cv.put(f.name, "${f.name}-1")
                    }
                }
                check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) != -1L) { "seed insert into $table at v$version failed" }
            }
        }
        val file = context.getDatabasePath(name)
        val seeded = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == version) { "the backup's database is at ${db.version}, not $version" }
            SchemaAssets.entities(version).filter { it.first in JOURNAL_TABLE_NAMES }.associate { (table, fields) ->
                table to db.rawQuery("SELECT ${fields.joinToString { "`${it.name}`" }} FROM `$table`", null).use { it.allRows() }
            }
        }
        check(seeded.values.all { it.size == 1 }) { "every journal table was seeded with one row: $seeded" }
        val files = linkedMapOf("forager.db" to file.readBytes())
        return writeZip(linkedMapOf("manifest.json" to manifestJson(version, files)) + files) to seeded
    }

    private fun Phone.rows(table: String, columns: List<String>): List<Map<String, String>> =
        database.openHelper.readableDatabase.query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table`").use { it.allRows() }

    private fun Cursor.allRows(): List<Map<String, String>> = buildList {
        while (moveToNext()) add((0 until columnCount).associate { getColumnName(it) to cellText(it) })
    }

    private fun Cursor.cellText(i: Int): String = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> "NULL"
        Cursor.FIELD_TYPE_INTEGER -> getLong(i).toString()
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i).toString()
        Cursor.FIELD_TYPE_BLOB -> "blob:" + sha256(getBlob(i))
        else -> getString(i)
    }

    /** The row without the keys a Replace gives afresh (the region's id, the track point's auto key). */
    private fun Map<String, String>.withoutKeys(table: String): Map<String, String> = filterKeys { Pair(table, it) !in FRESH_KEYS }

    private companion object {
        /** The links that must point at a seeded row for that row to be kept: the route stop's trip (an OWNED row's owner). */
        val OVERRIDES = mapOf(Pair("planned_trip_route_stops", "tripId") to "planned_trips-1", Pair("planned_trip_route_legs", "tripId") to "planned_trips-1")

        val FRESH_KEYS = setOf(Pair("offline_regions", "id"), Pair("track_points", "id"))

        /** Columns naming the offline region, which follow its fresh id. */
        val FOLLOWS_REGION = setOf(Pair("mushroom_log_entries", "offlineRegionId"), Pair("cartography_entry_offline_region_refs", "offlineRegionId"))
    }
}
