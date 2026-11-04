package com.zynergylabs.forager.app.map

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.StoredTiles
import com.zynergylabs.forager.app.domain.mapdata.TileId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [OfflineBasemap] read from MapLibre's own offline store, `files/mbgl-offline.db` (`MapLibreStorage.kt`), read-only
 * (way-to dispatch 2026-10-28, PR 2). Nothing is written, and MapLibre keeps its own connection. This only runs two
 * SELECTs against the schema MapLibre Native 13.5.0 creates (`StoredTiles` names the source files):
 * - the basemap's TileJSON: the `resources` rows of kind 2 (a source), the one [StoredTiles.basemapSource] picks;
 * - a tile: the `tiles` row for that TileJSON's URL template at z/x/y, any pixel ratio.
 *
 * A store that is not there is no basemap stored (null), not a failure. A store that cannot be opened or read fails with
 * the SQLite or decompression error.
 */
class MapLibreOfflineBasemap(
    private val database: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OfflineBasemap {
    private val lock = Mutex()
    private var source: StoredTiles.BasemapSource? = null

    override suspend fun tileJson(): Result<String?> = read { db -> basemapSource(db)?.tileJson }

    override suspend fun tile(id: TileId): Result<ByteArray?> = read { db ->
        val template = basemapSource(db)?.urlTemplate ?: return@read null
        db.rawQuery(
            "SELECT data, compressed FROM tiles WHERE url_template = ? AND z = ? AND x = ? AND y = ? AND data IS NOT NULL LIMIT 1",
            arrayOf(template, id.z.toString(), id.x.toString(), id.y.toString()),
        ).use { c -> if (c.moveToFirst()) StoredTiles.bytesOf(c.getBlob(0), c.getInt(1) != 0) else null }
    }

    /** Read once per instance: the way to a waypoint builds one per search, so a refreshed TileJSON is seen by the next. */
    private fun basemapSource(db: SQLiteDatabase): StoredTiles.BasemapSource? {
        source?.let { return it }
        val jsons = db.rawQuery("SELECT data, compressed FROM resources WHERE kind = ? AND data IS NOT NULL ORDER BY modified DESC", arrayOf(KIND_SOURCE)).use { c ->
            buildList { while (c.moveToNext()) add(StoredTiles.bytesOf(c.getBlob(0), c.getInt(1) != 0).decodeToString()) }
        }
        return StoredTiles.basemapSource(jsons).also { source = it }
    }

    private suspend fun <T> read(block: (SQLiteDatabase) -> T?): Result<T?> = withContext(ioDispatcher) {
        lock.withLock {
            if (!database.isFile) return@withLock Result.success(null)
            try {
                SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { Result.success(block(it)) }
            } catch (e: SQLiteException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    private companion object {
        /** `Resource::Kind::Source` in offline_schema.sql. */
        const val KIND_SOURCE = "2"
    }
}
