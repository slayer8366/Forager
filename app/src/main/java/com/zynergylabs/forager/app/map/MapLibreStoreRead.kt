package com.zynergylabs.forager.app.map

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import java.io.File

/*
 * Every read of MapLibre's offline store in the app (`files/mbgl-offline.db`: [MapLibreOfflineBasemap] for the way-to and
 * the drop-off map copy, [OfflineReliefTileStore] for the waypoint bubble's ground height) opens it with
 * [openMapLibreStoreReadOnly], inside [retryingRefusals] (dispatch 2026-11-05-01; the owner: "Retry, fail safe
 * (Recommended)", RECORD -2122). Nothing here can write to the store, and MapLibre's own configuration is not touched.
 *
 * Why a read is refused (the Phase 1 report, section 1.3):
 * - MapLibre writes the store through its own copy of SQLite, linked inside its native library; the app reads it through
 *   Android's. In one process the two cannot see each other's file locks (SQLite's "How To Corrupt", 2.2 and 2.3).
 * - So while MapLibre commits, its rollback journal is valid on disk and the app's connection sees no lock marking it in
 *   use. It judges the journal hot and, being read-only, refuses with SQLITE_READONLY_ROLLBACK (776) rather than roll back.
 * - MapLibre commits whenever it reads a tile or resource from its store, as well as when it stores one, so refusals come
 *   in runs as a map view starts: the moment Offline maps and Navigate both begin reading.
 *
 * Why retrying never writes: it is the same read-only open again, and a read-only connection cannot roll a journal back
 * (that refusal is what 776 is). Opening writable would roll back MapLibre's live commit, and is never done.
 *
 * What the retry does not do: the invisibility runs both ways, so MapLibre can commit in the middle of a read that has
 * already begun. Nothing inside this process stops that (the Phase 1 report, 1.5); the dispatch's completion report says
 * what such a read can yield here.
 */

/**
 * MapLibre's store, opened read-only with an error handler that never deletes it (A6). Android's default handler,
 * `DefaultDatabaseErrorHandler`, which `openDatabase(path, null, flags)` installs, deletes the database file when SQLite
 * reports corruption (AOSP Android 16; the phone's own framework was not read). On this connection that file is MapLibre's
 * store, with every offline area in it. This one logs the report and leaves the file; the read then fails with SQLite's
 * corruption error, as any failed read does.
 */
internal fun openMapLibreStoreReadOnly(database: File, log: (String, Throwable?) -> Unit): SQLiteDatabase =
    SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY, KeepTheStoreOnCorruption(database.name, log))

private class KeepTheStoreOnCorruption(private val name: String, private val log: (String, Throwable?) -> Unit) : DatabaseErrorHandler {
    override fun onCorruption(dbObj: SQLiteDatabase) {
        log("SQLite reported MapLibre's store ($name) corrupt on a read-only open. The file is left as it is, and the read fails.", null)
    }
}

/**
 * Runs [read], one read-only open of MapLibre's store and what it reads, and runs it again while SQLite refuses it with
 * [SQLiteReadOnlyDatabaseException], waiting [MapLibreStoreReadLimits.WAITS_MILLIS] between attempts, up to
 * [MapLibreStoreReadLimits.ATTEMPTS] (Amendment 1, B1). Any other exception fails at once, as before.
 * - One line is logged for a read that needed a retry, naming its attempts.
 * - One line is logged for a read still refused at the bound, which then fails with the refusal.
 *
 * **The whole read is retried, the open and its statements together.** The refusal comes at the connection's first read of
 * the file, and SQLite checks for a hot journal again at every statement that starts a read (its locking document, 4.1). On
 * the S22 that first read is a vendor step inside the open (`PRAGMA cache_size`); on AOSP's open, which reads nothing, it is
 * the first query. The statements only read, so running them again changes nothing.
 *
 * [pause] blocks the calling thread for each wait: both readers run on an IO dispatcher, and the waits total 750 ms.
 * [what] names the read in the log lines.
 */
internal fun <T> retryingRefusals(what: String, log: (String, Throwable?) -> Unit, pause: (Long) -> Unit, read: () -> T): T {
    var attempt = 1
    while (true) {
        try {
            val result = read()
            if (attempt > 1) log("$what was refused while MapLibre was saving, and read on attempt $attempt of ${MapLibreStoreReadLimits.ATTEMPTS}.", null)
            return result
        } catch (refused: SQLiteReadOnlyDatabaseException) {
            if (attempt == MapLibreStoreReadLimits.ATTEMPTS) {
                log("$what was refused while MapLibre was saving on all ${MapLibreStoreReadLimits.ATTEMPTS} attempts, over ${MapLibreStoreReadLimits.WAITS_MILLIS.sum()} ms, and fails.", refused)
                throw refused
            }
            pause(MapLibreStoreReadLimits.WAITS_MILLIS[attempt - 1])
            attempt++
        }
    }
}
