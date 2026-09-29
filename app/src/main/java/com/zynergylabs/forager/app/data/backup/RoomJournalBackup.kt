package com.zynergylabs.forager.app.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zynergylabs.forager.app.data.local.ForagerDatabase
import com.zynergylabs.forager.app.domain.BackupException
import com.zynergylabs.forager.app.domain.BackupReport
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.JournalBackup
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.domain.RestoreReport
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy
import com.zynergylabs.forager.app.domain.UnreadablePhotosException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seams a test uses to stop the operation at a chosen point: [snapshotLockHeld] runs while the snapshot holds
 * the database's write lock, [afterPhotosCopied] once a restore has copied photo files in but before it writes
 * rows, and [afterRowsWritten] inside the restore's transaction after the rows are written and before it
 * commits. A hook that throws makes the operation fail there, which is how the rollback is exercised.
 * Production passes none.
 */
class RoomJournalBackupHooks(
    val snapshotLockHeld: () -> Unit = {},
    val afterPhotosCopied: () -> Unit = {},
    val afterRowsWritten: () -> Unit = {},
)

/**
 * The journal backup over the app's Room database and photo folder (dispatch 2026-09-28-127). The format is
 * [BackupManifest]'s; the table knowledge is [JournalTables]'s.
 *
 * ## The snapshot
 *
 * `VACUUM INTO` would be the one-statement answer but needs SQLite 3.27, which Android ships from API 30, and
 * `minSdk` is 26. A plain copy of `forager.db` under WAL can be torn by a checkpoint landing mid-copy. So:
 * checkpoint (best effort, to keep the WAL small), then open a write transaction, whose write lock stops any
 * other writer committing and so stops any auto-checkpoint, copy the database file **and its WAL**, release,
 * and fold the copy (open it, checkpoint it, switch it to a rollback journal) into one self-contained file.
 * Readers keep working throughout, and the live database is only ever read.
 *
 * ## Restore
 *
 * Both modes first stage the archive in a scratch folder and verify it, all before the live data is touched: the
 * manifest, every SHA-256, the schema version (newer than this build is refused; older is migrated **on the
 * scratch copy** by Room's registered migrations, with no destructive fallback), and `integrity_check` before
 * and after the migration. The live Room database is never closed or swapped; rows are written through one
 * Room transaction, so a failure rolls back and observers see either everything or nothing.
 *
 * - **Replace:** photo files are copied in first (a live file of the same name with different bytes is moved
 *   aside, not overwritten), then one transaction deletes the journal tables' rows and inserts the
 *   backup's. On any failure the transaction rolls back and the photo step is undone. Only after the commit are
 *   the phone's old photo files that no restored row uses deleted.
 * - **Merge:** [JournalTables]'s rule. A record whose id is on the phone is skipped and the phone's copy wins,
 *   whole; a record the phone lacks is inserted with its dependent rows, each kept only if every record it names
 *   is on the phone afterwards, so nothing dangles; a nullable link to a record that is nowhere is set NULL.
 *   Photo files are copied only for photo rows that were inserted.
 */
class RoomJournalBackup(
    private val context: Context,
    private val database: ForagerDatabase,
    private val databaseFile: File,
    private val filesDir: File,
    private val scratchDir: File,
    private val appVersionCode: Long,
    private val errorLog: ErrorLog,
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val hooks: RoomJournalBackupHooks = RoomJournalBackupHooks(),
) : JournalBackup {

    override suspend fun backUp(sink: OutputStream, unreadablePhotos: UnreadablePhotoPolicy): Result<BackupReport> = withContext(Dispatchers.IO) {
        attempt("backup failed") { doBackUp(sink, unreadablePhotos) }
    }

    override suspend fun restore(source: InputStream, mode: RestoreMode): Result<RestoreReport> = withContext(Dispatchers.IO) {
        attempt("restore failed") { doRestore(source, mode) }
    }

    /** Runs [block]; anything it throws (other than cancellation) is logged with [what] and returned as a [BackupException] failure. */
    private inline fun <T> attempt(what: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackupException) {
        errorLog.w(TAG, "$what: ${e.message}", e)
        Result.failure(e)
    } catch (e: Throwable) {
        errorLog.w(TAG, "$what: ${e.message}", e)
        Result.failure(BackupException("$what: ${e.message}", e))
    }

    // ---- back up -------------------------------------------------------------------------------

    private fun doBackUp(sink: OutputStream, policy: UnreadablePhotoPolicy): BackupReport {
        val scratch = newScratch("backup")
        try {
            val snapshot = File(scratch, BackupManifest.DATABASE_ENTRY)
            takeSnapshot(snapshot)
            val (schemaVersion, photoPaths) = SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                requireIntegrity(db, "the snapshot")
                val paths = db.rawQuery("SELECT relativePath FROM log_photos", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(0)) }
                }
                db.version to paths
            }
            val photos = LinkedHashMap<String, File>()
            var missing = 0
            for (path in photoPaths.distinct().sorted()) {
                val file = File(filesDir, path)
                if (!BackupArchive.isSafeEntryName(path) || !file.isFile || !file.canRead()) {
                    missing++
                    errorLog.w(TAG, "backup: photo $path could not be read${if (policy == UnreadablePhotoPolicy.SKIP) " and is left out" else ""}", BackupException("unreadable photo file $path"))
                    continue
                }
                photos[path] = file
            }
            // Told to ask (owner, "3 A"): stop here, before a byte reaches the sink, so the person can choose.
            if (missing > 0 && policy == UnreadablePhotoPolicy.ASK) throw UnreadablePhotosException(missing)
            val listed = buildList {
                add(describe(BackupManifest.DATABASE_ENTRY, snapshot))
                photos.forEach { (path, file) -> add(describe(path, file)) }
            }
            val manifest = BackupManifest(
                formatVersion = BackupManifest.FORMAT_VERSION,
                appVersionCode = appVersionCode,
                schemaVersion = schemaVersion,
                createdAtEpochMillis = clock.nowEpochMillis(),
                files = listed,
            )
            val bytes = BackupArchive.write(sink, manifest, snapshot, photos)
            return BackupReport(photoFiles = photos.size, photoFilesMissing = missing, archiveBytes = bytes)
        } finally {
            scratch.deleteRecursively()
        }
    }

    private fun describe(path: String, file: File) = ManifestFile(path, BackupArchive.sha256Hex(file), file.length())

    private fun takeSnapshot(target: File) {
        val sql = database.openHelper.writableDatabase
        try {
            sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        } catch (e: Exception) {
            // Best effort: it only keeps the WAL small. The locked copy below is what makes the snapshot consistent.
            errorLog.w(TAG, "backup: the checkpoint before the snapshot failed; copying the WAL as well", e)
        }
        val walFile = File(databaseFile.path + "-wal")
        val targetWal = File(target.path + "-wal")
        database.runInTransaction(Runnable {
            // Take the write lock now: a statement that changes nothing still takes it, and from here to the end
            // of the transaction no other writer can commit, so nothing can checkpoint underneath the copy.
            sql.execSQL("DELETE FROM cached_searches WHERE 0")
            hooks.snapshotLockHeld()
            databaseFile.copyTo(target, overwrite = true)
            if (walFile.exists()) walFile.copyTo(targetWal, overwrite = true)
        })
        // Fold the copy into one file: opening it replays the copied WAL; then checkpoint and drop the WAL mode.
        SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
        }
        targetWal.delete()
        File(target.path + "-shm").delete()
    }

    // ---- restore -------------------------------------------------------------------------------

    private fun doRestore(source: InputStream, mode: RestoreMode): RestoreReport {
        val scratch = newScratch("restore")
        try {
            val staged = BackupArchive.stage(source, File(scratch, "staged"))
            checkSchemaVersion(staged.manifest)
            SQLiteDatabase.openDatabase(staged.databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                requireIntegrity(db, "the backup's database")
                if (db.version != staged.manifest.schemaVersion) {
                    throw BackupException("the manifest says schema ${staged.manifest.schemaVersion} but the database is schema ${db.version}")
                }
            }
            migrateOnScratchCopy(staged.databaseFile)
            return SQLiteDatabase.openDatabase(staged.databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { backupDb ->
                requireIntegrity(backupDb, "the backup's database after migration")
                when (mode) {
                    RestoreMode.REPLACE -> replace(staged, backupDb)
                    RestoreMode.MERGE -> merge(staged, backupDb)
                }
            }
        } finally {
            scratch.deleteRecursively()
        }
    }

    private fun checkSchemaVersion(manifest: BackupManifest) {
        if (manifest.schemaVersion > ForagerDatabase.SCHEMA_VERSION) {
            throw BackupException(
                "the backup is from schema ${manifest.schemaVersion}, newer than this app's ${ForagerDatabase.SCHEMA_VERSION}; " +
                    "restore it with an app that is at least as new as the one that made it",
            )
        }
    }

    /** Opens the scratch copy with Room, which runs the registered migrations up to this build's schema and checks the result against it; then closes it. */
    private fun migrateOnScratchCopy(file: File) {
        val scratchDb = ForagerDatabase.openForRestore(context, file.absolutePath)
        try {
            scratchDb.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        } finally {
            scratchDb.close()
        }
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    private fun replace(staged: StagedBackup, backupDb: SQLiteDatabase): RestoreReport {
        val photos = PhotoFileJournal(filesDir, errorLog)
        val sql = database.openHelper.writableDatabase
        val oldPhotoPaths = sql.query("SELECT relativePath FROM log_photos").use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        val newPhotoPaths = backupDb.rawQuery("SELECT relativePath FROM log_photos", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
        var inserted = 0
        var skipped = 0
        var dropped = 0
        try {
            for ((path, file) in staged.photoFiles) photos.place(path, file)
            hooks.afterPhotosCopied()
            database.runInTransaction(Runnable {
                // An incoming region that is one the phone already has (owner 3.1) keeps the phone's own row, and so its id,
                // which may be a live MapLibre region's; nothing is inserted for it and every reference follows to that id.
                val matches = matchingRegions(backupDb, sql)
                val keep = matches.values.toSet()
                for (spec in JournalTables.journal) {
                    if (spec.name == OFFLINE_REGIONS && keep.isNotEmpty()) sql.execSQL("DELETE FROM `${spec.name}` WHERE id NOT IN (${keep.joinToString(",")})")
                    else sql.execSQL("DELETE FROM `${spec.name}`")
                }
                // Every other restored region gets a fresh id by the rule Merge uses, negative, so that a later MapLibre download
                // arriving with the backup's old id can never overwrite a restored row (owner, "2 A"). Every reference in the
                // restored data follows it. The ids count down from below the lowest region that is left (-1 when none is).
                val regionIdMap = HashMap<String, String>()
                for ((old, phoneId) in matches) regionIdMap[old] = phoneId.toString()
                var nextRegionId = minOf(-1L, (sql.query("SELECT MIN(id) FROM offline_regions").use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L }) - 1)
                val refsSeen = HashSet<Pair<String, String>>()
                for (spec in JournalTables.journal) {
                    inserted += copyAll(backupDb, sql, spec.name, dropKey = null) { values ->
                        when (spec.name) {
                            OFFLINE_REGIONS -> {
                                val old = values.getAsString("id")
                                if (old in matches) {
                                    skipped++
                                    return@copyAll false
                                }
                                val fresh = nextRegionId--
                                values.put("id", fresh)
                                regionIdMap[old] = fresh.toString()
                            }
                            // A find's link to a region the backup does not hold is cleared: left as it was, its number could
                            // one day be a MapLibre id and point at a region that is not the one it meant.
                            "mushroom_log_entries" -> values.getAsString("offlineRegionId")?.let { old ->
                                regionIdMap[old]?.let { values.put("offlineRegionId", it.toLong()) } ?: values.putNull("offlineRegionId")
                            }
                            // An entry's ref to a region the backup does not hold is dropped, as Merge drops it (-155): kept, its
                            // number could meet a future MapLibre id. Two refs that now name the same region collapse into one.
                            "cartography_entry_offline_region_refs" -> {
                                val entry = values.getAsString("entryId")
                                val old = values.getAsString("offlineRegionId")
                                val mapped = regionIdMap[old]
                                if (mapped == null) {
                                    dropped++
                                    errorLog.w(TAG, "replace: a row of cartography_entry_offline_region_refs names a region the backup lacks and is left out", BackupException("dropped cartography_entry_offline_region_refs row of $entry, region $old"))
                                    return@copyAll false
                                }
                                values.put("offlineRegionId", mapped.toLong())
                                if (!refsSeen.add(entry to mapped)) return@copyAll false
                            }
                        }
                        true
                    }
                }
                hooks.afterRowsWritten()
            })
        } catch (t: Throwable) {
            photos.rollback()
            throw t
        }
        photos.commit()
        for (path in oldPhotoPaths - newPhotoPaths) photos.deleteOld(path)
        return RestoreReport(RestoreMode.REPLACE, rowsInserted = inserted, recordsSkipped = skipped, rowsDropped = dropped, photoFilesAdded = photos.placedCount)
    }

    private fun merge(staged: StagedBackup, backupDb: SQLiteDatabase): RestoreReport {
        val photos = PhotoFileJournal(filesDir, errorLog)
        val sql = database.openHelper.writableDatabase
        var inserted = 0
        var skipped = 0
        var dropped = 0
        // Photo files first, and only for photo rows the phone lacks.
        val photoSpec = JournalTables.journal.first { it.name == "log_photos" }
        val candidatePhotos = backupDb.rawQuery("SELECT id, relativePath FROM log_photos", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getString(1)) }
        }.filter { (id, _) -> !existsIn(sql, photoSpec.name, "id", id) }
        try {
            for ((_, path) in candidatePhotos) staged.photoFiles[path]?.let { photos.place(path, it) }
            hooks.afterPhotosCopied()
            database.runInTransaction(Runnable {
                val insertedKeys = HashMap<String, MutableSet<String>>()
                // Each incoming offline region gets a new id (owner, "2 A"); this maps the backup's id to it, and is the only
                // way a reference to a region is resolved on the way in (never by the backup's number, which on this phone can
                // name a different region).
                val regionIdMap = HashMap<String, String>()
                var nextRegionId = minOf(-1L, (sql.query("SELECT MIN(id) FROM offline_regions").use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L }) - 1)
                // An incoming region that is one the phone already has (owner 3.1) is skipped, and its references follow to the phone's.
                val regionMatches = matchingRegions(backupDb, sql)
                for ((old, phoneId) in regionMatches) regionIdMap[old] = phoneId.toString()
                for (spec in JournalTables.journal.filter { it.kind == JournalTables.Kind.RECORD }) {
                    val keys = insertedKeys.getOrPut(spec.name) { HashSet() }
                    backupDb.rawQuery("SELECT * FROM `${spec.name}`", null).use { c ->
                        while (c.moveToNext()) {
                            val key = keyOf(c, spec)
                            if (spec.name == OFFLINE_REGIONS && key.single() in regionMatches) {
                                skipped++
                            } else if (spec.name == OFFLINE_REGIONS) {
                                val newId = nextRegionId--
                                val values = c.toValues(dropColumn = null).also { it.put("id", newId) }
                                insertRow(sql, spec.name, values)
                                regionIdMap[key.single()] = newId.toString()
                                keys += newId.toString()
                                inserted++
                            } else if (existsIn(sql, spec.name, spec.keyColumns.single(), key.single())) {
                                skipped++
                            } else {
                                insertRow(sql, spec.name, c.toValues(dropColumn = null))
                                keys += key.single()
                                inserted++
                            }
                        }
                    }
                }
                // A nullable link on a record this merge inserted, to a record that is on neither phone, becomes NULL.
                for (spec in JournalTables.journal.filter { it.kind == JournalTables.Kind.RECORD && it.softLinks.isNotEmpty() }) {
                    for (link in spec.softLinks) for (key in insertedKeys.getValue(spec.name)) {
                        val target = sql.query("SELECT `${link.column}` FROM `${spec.name}` WHERE `${spec.keyColumns.single()}` = ?", arrayOf<Any?>(key)).use { c ->
                            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
                        }
                        if (target == null) continue
                        if (link.table == OFFLINE_REGIONS) {
                            // The backup's region number, resolved only through what this merge inserted: to the new id, or nowhere.
                            val mapped = regionIdMap[target]
                            sql.execSQL("UPDATE `${spec.name}` SET `${link.column}` = ? WHERE `${spec.keyColumns.single()}` = ?", arrayOf<Any?>(mapped?.toLong(), key))
                        } else if (!existsIn(sql, link.table, link.targetKey, target)) {
                            sql.execSQL("UPDATE `${spec.name}` SET `${link.column}` = NULL WHERE `${spec.keyColumns.single()}` = ?", arrayOf<Any?>(key))
                        }
                    }
                }
                // Dependent rows: only with an owner this merge inserted, and only if every record they name is on the phone.
                for (spec in JournalTables.journal.filter { it.kind == JournalTables.Kind.OWNED }) {
                    val owner = spec.owner!!
                    val ownerKeys = insertedKeys.getValue(owner.table)
                    backupDb.rawQuery("SELECT * FROM `${spec.name}`", null).use { c ->
                        while (c.moveToNext()) {
                            val ownerValue = c.getString(c.getColumnIndexOrThrow(owner.column))
                            if (ownerValue !in ownerKeys) continue
                            val values = c.toValues(dropColumn = if (spec.autoKey) spec.keyColumns.single() else null)
                            val dangling = spec.needs.any { need ->
                                val value = c.getString(c.getColumnIndexOrThrow(need.column))
                                if (need.table == OFFLINE_REGIONS) {
                                    val mapped = regionIdMap[value]
                                    if (mapped != null) values.put(need.column, mapped.toLong())
                                    mapped == null
                                } else {
                                    !existsIn(sql, need.table, need.targetKey, value)
                                }
                            }
                            if (dangling) {
                                dropped++
                                errorLog.w(TAG, "merge: a row of ${spec.name} names a record that is on neither phone and is left out", BackupException("dropped ${spec.name} row of $ownerValue"))
                                continue
                            }
                            // Two incoming regions that both matched one of the phone's give a second ref to the same pair: one is enough.
                            if (spec.needs.any { it.table == OFFLINE_REGIONS } && existsWithKey(sql, spec, values)) {
                                skipped++
                                continue
                            }
                            insertRow(sql, spec.name, values)
                            inserted++
                        }
                    }
                }
                hooks.afterRowsWritten()
            })
        } catch (t: Throwable) {
            photos.rollback()
            throw t
        }
        photos.commit()
        // A photo file was copied ahead of its row; if the row did not get inserted after all, the file goes.
        for ((id, path) in candidatePhotos) if (!existsIn(sql, photoSpec.name, "id", id)) photos.deleteOld(path)
        return RestoreReport(RestoreMode.MERGE, rowsInserted = inserted, recordsSkipped = skipped, rowsDropped = dropped, photoFilesAdded = photos.placedCount)
    }

    // ---- rows ----------------------------------------------------------------------------------

    /** Every row of [table] in [from], verbatim, into [into], except those [transform] returns false for (it may also change the values). Returns how many were inserted. */
    private fun copyAll(
        from: SQLiteDatabase,
        into: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        dropKey: String?,
        transform: (ContentValues) -> Boolean = { true },
    ): Int {
        var n = 0
        from.rawQuery("SELECT * FROM `$table`", null).use { c ->
            while (c.moveToNext()) {
                val values = c.toValues(dropKey)
                if (!transform(values)) continue
                insertRow(into, table, values)
                n++
            }
        }
        return n
    }

    /**
     * One row, inserted with plain SQL so that a failure **throws with SQLite's own message**. `SupportSQLiteDatabase.insert`
     * swallows the exception and returns -1, which is also the row id of a row inserted under the id -1 (a region a Merge
     * gave a new id), so its return value cannot say whether an insert worked.
     */
    private fun insertRow(into: androidx.sqlite.db.SupportSQLiteDatabase, table: String, values: ContentValues) {
        val columns = values.keySet().toList()
        val sql = "INSERT INTO `$table` (${columns.joinToString(", ") { "`$it`" }}) VALUES (${columns.joinToString(", ") { "?" }})"
        into.execSQL(sql, Array<Any?>(columns.size) { values.get(columns[it]) })
    }

    /**
     * For each region in [backupDb], the id of the phone's own region it is (same name, centre within 1 m and radius: [sameOfflineRegion]),
     * keyed by the backup's id as a string; a region with no match is absent. Reads only. The first matching phone region wins.
     */
    private fun matchingRegions(backupDb: SQLiteDatabase, sql: androidx.sqlite.db.SupportSQLiteDatabase): Map<String, Long> {
        class Region(val id: Long, val name: String, val lat: Double, val lng: Double, val radiusKm: Int)
        val query = "SELECT id, name, lat, lng, radiusKm FROM offline_regions ORDER BY id"
        val onPhone = sql.query(query).use { c -> buildList { while (c.moveToNext()) add(Region(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getInt(4))) } }
        if (onPhone.isEmpty()) return emptyMap()
        val incoming = backupDb.rawQuery(query, null).use { c -> buildList { while (c.moveToNext()) add(Region(c.getLong(0), c.getString(1), c.getDouble(2), c.getDouble(3), c.getInt(4))) } }
        val matches = LinkedHashMap<String, Long>()
        for (region in incoming) {
            val same = onPhone.firstOrNull { sameOfflineRegion(region.name, region.lat, region.lng, region.radiusKm, it.name, it.lat, it.lng, it.radiusKm) } ?: continue
            matches[region.id.toString()] = same.id
        }
        return matches
    }

    private fun existsWithKey(sql: androidx.sqlite.db.SupportSQLiteDatabase, spec: JournalTables.TableSpec, values: ContentValues): Boolean {
        val where = spec.keyColumns.joinToString(" AND ") { "`$it` = ?" }
        return sql.query("SELECT 1 FROM `${spec.name}` WHERE $where LIMIT 1", spec.keyColumns.map { values.get(it) }.toTypedArray()).use { it.moveToFirst() }
    }

    private fun keyOf(c: Cursor, spec: JournalTables.TableSpec): List<String> = spec.keyColumns.map { c.getString(c.getColumnIndexOrThrow(it)) }

    private fun existsIn(sql: androidx.sqlite.db.SupportSQLiteDatabase, table: String, column: String, value: String): Boolean =
        sql.query("SELECT 1 FROM `$table` WHERE `$column` = ? LIMIT 1", arrayOf<Any?>(value)).use { it.moveToFirst() }

    private fun Cursor.toValues(dropColumn: String?): ContentValues {
        val cv = ContentValues()
        for (i in 0 until columnCount) {
            val name = getColumnName(i)
            if (name == dropColumn) continue
            when (getType(i)) {
                Cursor.FIELD_TYPE_NULL -> cv.putNull(name)
                Cursor.FIELD_TYPE_INTEGER -> cv.put(name, getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> cv.put(name, getDouble(i))
                Cursor.FIELD_TYPE_BLOB -> cv.put(name, getBlob(i))
                else -> cv.put(name, getString(i))
            }
        }
        return cv
    }

    private fun requireIntegrity(db: SQLiteDatabase, what: String) {
        val result = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "no result" }
        if (result != "ok") throw BackupException("$what failed its integrity check: $result")
    }

    private fun newScratch(kind: String): File = File(scratchDir, "$kind-${UUID.randomUUID()}").also { it.mkdirs() }

    private companion object {
        const val OFFLINE_REGIONS = "offline_regions"
        const val TAG = "RoomJournalBackup"
    }
}
