package com.zynergylabs.forager.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.data.local.SchemaAssets
import com.zynergylabs.forager.app.domain.ErrorLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

/**
 * A "phone" for the backup tests: its own Room database file, its own `files/` folder and scratch folder, and
 * the real [RoomJournalBackup] over them. Two phones in one test are how a backup is taken on one and restored
 * on another. Everything is real (Room on a file, real zip, real SHA-256); only the folders are temporary.
 */
internal class Phone(
    context: Context,
    val name: String,
    root: File,
    hooks: RoomJournalBackupHooks = RoomJournalBackupHooks(),
) {
    val filesDir = File(root, "$name-files").apply { mkdirs() }
    val scratchDir = File(root, "$name-scratch")
    val logged = mutableListOf<String>()
    val database: ForagerDatabase = ForagerDatabase.create(context, isDebug = false, name = name)
    val databaseFile: File = context.getDatabasePath(name)
    val service = RoomJournalBackup(
        context = context,
        database = database,
        databaseFile = databaseFile,
        filesDir = filesDir,
        scratchDir = scratchDir,
        appVersionCode = 42L,
        errorLog = ErrorLog { _, message, error -> logged += "$message :: ${error.message}" },
        hooks = hooks,
    )
    private val sql get() = database.openHelper.writableDatabase
    private var counter = 0

    /**
     * One row in [table]. Every NOT NULL column gets a value of its own affinity; a nullable column gets one too
     * unless it is one of the record links, which stay NULL unless a test names them (a made-up id there would
     * dangle). [values] override.
     */
    fun insert(table: String, vararg values: Pair<String, Any?>) {
        val overrides = values.toMap()
        val fields = SchemaAssets.entities(SCHEMA).first { it.first == table }.second
        val cv = ContentValues()
        for (f in fields) {
            val v: Any? = if (overrides.containsKey(f.name)) overrides[f.name] else when {
                f.name in LINK_COLUMNS -> null
                f.affinity == "INTEGER" -> if (f.name == "id") null else 1L
                f.affinity == "REAL" -> 1.5
                else -> "${f.name}-${++counter}"
            }
            when (v) {
                null -> cv.putNull(f.name)
                is String -> cv.put(f.name, v)
                is Long -> cv.put(f.name, v)
                is Int -> cv.put(f.name, v)
                is Double -> cv.put(f.name, v)
                is ByteArray -> cv.put(f.name, v)
                else -> error("unsupported seed value for $table.${f.name}: $v")
            }
        }
        check(sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) != -1L) { "seed insert into $table failed" }
    }

    /** A photo file the way the app stores one: `files/photos/<id>.jpg`. */
    fun writePhotoFile(id: String, bytes: ByteArray) {
        File(filesDir, "photos").mkdirs()
        File(filesDir, "photos/$id.jpg").writeBytes(bytes)
    }

    /** A `log_photos` row and its file. */
    fun addPhoto(id: String, bytes: ByteArray) {
        insert("log_photos", "id" to id, "relativePath" to "photos/$id.jpg")
        writePhotoFile(id, bytes)
    }

    /** Every journal-relevant table's rows, as strings, sorted, keyed by table. Compared whole between phones. */
    fun dump(tables: List<String> = allTables()): Map<String, List<String>> =
        tables.associateWith { table ->
            sql.query("SELECT * FROM `$table`").use { c ->
                val rows = mutableListOf<String>()
                while (c.moveToNext()) rows += (0 until c.columnCount).joinToString("|") { c.columnName(it) + "=" + c.cell(it) }
                rows.sorted()
            }
        }

    fun count(table: String): Long = sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getLong(0) }

    fun scalar(query: String): String? = sql.query(query).use { if (it.moveToFirst()) it.cell(0) else null }

    /** Everything under `files/`: relative path to SHA-256, so a test can say "no file changed". */
    fun files(): Map<String, String> =
        filesDir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(filesDir).path to sha256(it.readBytes()) }

    fun scratchLeftovers(): List<String> = if (scratchDir.exists()) scratchDir.walkTopDown().filter { it.isFile }.map { it.name }.toList() else emptyList()

    fun backUp(): ByteArray = ByteArrayOutputStream().also { out -> runBlocking { service.backUp(out) }.getOrThrow() }.toByteArray()

    fun close() = database.close()

    private fun Cursor.cell(i: Int): String = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> "NULL"
        Cursor.FIELD_TYPE_INTEGER -> getLong(i).toString()
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i).toString()
        Cursor.FIELD_TYPE_BLOB -> "blob:" + sha256(getBlob(i))
        else -> getString(i)
    }

    private fun Cursor.columnName(i: Int): String = getColumnName(i)

    companion object {
        const val SCHEMA = 17
        val LINK_COLUMNS = setOf("trackId", "originWaypointId", "offlineRegionId", "draftOfEntryId")

        fun allTables(): List<String> = SchemaAssets.entities(SCHEMA).map { it.first }
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** A backup archive's entries, name to bytes, in order. */
internal fun readZip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
        while (true) {
            val entry = zin.nextEntry ?: break
            out[entry.name] = zin.readBytes()
        }
    }
    return out
}

internal fun writeZip(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zout ->
        for ((name, data) in entries) {
            zout.putNextEntry(ZipEntry(name))
            zout.write(data)
            zout.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * Phone A's data: every journal table has rows, with real links between them. Waypoint `w1` and track `t1`
 * name each other; the find `f1` names region 7 and holds photos `p1` and `p2`; the entry `e1` keeps a track,
 * a waypoint, a region, a find and a photo. Region 7 is a MapLibre-style id (an integer, not a uuid).
 */
internal fun Phone.seedFullJournal() {
    insert("waypoints", "id" to "w1", "name" to "Creek pin", "trackId" to "t1")
    insert("waypoints", "id" to "w2", "name" to "Big oak")
    insert("tracks", "id" to "t1", "name" to "Morning loop", "originWaypointId" to "w1")
    insert("tracks", "id" to "t2", "name" to "Ridge")
    repeat(3) { insert("track_points", "trackId" to "t1") }
    repeat(2) { insert("track_points", "trackId" to "t2") }
    insert("offline_regions", "id" to 7L, "name" to "Cedar Creek")
    addPhoto("p1", ByteArray(64) { it.toByte() })
    addPhoto("p2", ByteArray(80) { (it * 3).toByte() })
    addPhoto("p3", ByteArray(48) { (it * 5).toByte() })
    addPhoto("p4", ByteArray(32) { (it * 7).toByte() })
    insert("mushroom_log_entries", "id" to "f1", "entryNotes" to "Chanterelles under the oaks", "offlineRegionId" to 7L, "isDraft" to 0L)
    insert("mushroom_log_entries", "id" to "f2", "entryNotes" to "A draft", "isDraft" to 1L, "draftOfEntryId" to "f1")
    insert("log_entry_photos", "entryId" to "f1", "photoId" to "p1")
    insert("log_entry_photos", "entryId" to "f1", "photoId" to "p2")
    insert("planned_trips", "id" to "trip-1", "name" to "Chanterelle weekend", "date" to "2026-10-04")
    insert("planned_trips", "id" to "trip-2", "name" to "Morels", "date" to "2027-04-20")
    insert("cartography_entries", "id" to "e1", "text" to "A good day", "isDraft" to 0L)
    insert("cartography_entry_track_refs", "entryId" to "e1", "trackId" to "t1")
    insert("cartography_entry_waypoint_refs", "entryId" to "e1", "waypointId" to "w1")
    insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 7L)
    insert("cartography_entry_find_refs", "entryId" to "e1", "findId" to "f1")
    insert("cartography_entry_photo_refs", "entryId" to "e1", "photoId" to "p3")
    // F3: e1's kept track t1 has a saved path too (a real one, so a restore that mangled the bytes would show).
    insert("cartography_entry_track_paths", "entryId" to "e1", "trackId" to "t1", "path" to com.zynergylabs.forager.app.domain.TrackPathCodec.encode(listOf(com.zynergylabs.forager.app.domain.model.LatLng(45.2, -122.5), com.zynergylabs.forager.app.domain.model.LatLng(45.21, -122.51))))
}

/** Each table's primary key columns, in key order, from `<version>.json`. */
internal fun schemaPrimaryKeys(version: Int): Map<String, List<String>> {
    val root = File("schemas/com.zynergylabs.forager.app.data.local.ForagerDatabase/$version.json")
    val entities = org.json.JSONObject(root.readText()).getJSONObject("database").getJSONArray("entities")
    return (0 until entities.length()).associate { i ->
        val e = entities.getJSONObject(i)
        val cols = e.getJSONObject("primaryKey").getJSONArray("columnNames")
        e.getString("tableName") to (0 until cols.length()).map { cols.getString(it) }
    }
}

/** An archive's manifest, in the format the report documents. Tests build archives by hand with it. */
internal fun manifestJson(schemaVersion: Int, files: Map<String, ByteArray>, formatVersion: Int = 1): ByteArray {
    val list = org.json.JSONArray()
    for ((path, data) in files) list.put(org.json.JSONObject().put("path", path).put("sha256", sha256(data)).put("bytes", data.size.toLong()))
    return org.json.JSONObject()
        .put("formatVersion", formatVersion).put("appVersionCode", 1L).put("schemaVersion", schemaVersion)
        .put("createdAtEpochMillis", 1_700_000_000_000L).put("files", list).toString().toByteArray()
}

/** A backup taken by an older app: a real database at an older schema version, built from that version's `N.json`, in an archive. */
internal class OlderBackup(val archive: ByteArray) {
    companion object {
        fun helper(): androidx.room.testing.MigrationTestHelper {
            SchemaAssets.install(androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>().assets)
            return androidx.room.testing.MigrationTestHelper(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), ForagerDatabase::class.java)
        }

        fun build(helper: androidx.room.testing.MigrationTestHelper, root: File, version: Int): OlderBackup {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
            val name = "older-$version.db"
            helper.createDatabase(name, version).use { db ->
                for ((table, fields) in SchemaAssets.entities(version)) {
                    val cv = ContentValues()
                    for (f in fields) when {
                        f.affinity == "INTEGER" -> cv.put(f.name, 1L)
                        f.name == "id" -> cv.put(f.name, "$table-1")
                        f.affinity == "REAL" -> cv.put(f.name, 1.5)
                        else -> cv.put(f.name, "${f.name}-1")
                    }
                    check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, cv) != -1L) { "seed insert into $table at v$version failed" }
                }
            }
            val bytes = context.getDatabasePath(name).readBytes()
            val files = linkedMapOf("forager.db" to bytes)
            return OlderBackup(writeZip(linkedMapOf("manifest.json" to manifestJson(version, files)) + files))
        }
    }
}
