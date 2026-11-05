package com.zynergylabs.forager.app.map

import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.util.Log
import com.zynergylabs.forager.app.domain.relief.ReliefTile
import com.zynergylabs.forager.app.domain.relief.ReliefTileRead
import com.zynergylabs.forager.app.domain.relief.ReliefTileStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * The Trail Map's relief tiles as downloaded regions hold them, read out of MapLibre's own offline database
 * (`files/mbgl-offline.db`, `MapLibreStorage.kt`) for the waypoint bubble's ground height (dispatch 2026-11-03-07, D2).
 *
 * **There is no SDK call for this.** MapLibre Android 13.5.0 reads stored tiles only to draw them; this reads its table
 * directly, so it depends on the SDK's internal schema. What the spike found, on a database the SDK itself wrote on a
 * CI emulator (branch waypoint-bubble-elevation-spike, run 41752813862), and in MapLibre Native at tag android-v13.5.0:
 * - `PRAGMA user_version` 6 (`offline_database.cpp` migrates every older database up to 6).
 * - Table `tiles`, unique on `url_template, pixel_ratio, z, x, y`; `data` the tile's bytes, `compressed` 1 when `data`
 *   is zlib-deflated. The SDK deflates every tile and keeps the deflated bytes only when smaller (`offline_database.cpp`
 *   `putInternal`), so a PNG can be stored either way: the spike's relief tile was, 141037 bytes to 140876.
 * - `pixel_ratio` is 1 for this template: it has no `{ratio}` (`resource.cpp` `Resource::tile`).
 * - `region_tiles(region_id, tile_id)` links a tile to the regions that downloaded it. A tile with no link is the
 *   ambient cache, from viewing the map online; it is not counted (the planner's call: "count only tiles linked to a
 *   downloaded region, as the owner's option text says").
 * - A tile the server answered with no content is stored with `data` NULL (RECORD -1676, the relief's 404s).
 *
 * **Defensive** (the planner's acceptance of the spike): opened read-only for each read; the version and every column
 * this reads are checked first, and anything else is [ReliefTileRead.Unsupported], which the caller logs once and shows
 * as no line. So an SDK upgrade that changes the schema loses the line, never shows a wrong height. An SDK upgrade
 * re-runs the spike's check.
 *
 * @param database MapLibre's offline database file.
 * @param urlTemplate the relief source's tile URL as the style gives it: the key the SDK stores it under.
 */
class OfflineReliefTileStore(
    private val database: File,
    private val urlTemplate: String = RELIEF_TILE_URL_TEMPLATE,
    private val log: (String, Throwable?) -> Unit = { message, error -> Log.w(TAG, message, error) },
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
) : ReliefTileStore {

    override fun tile(z: Int, x: Int, y: Int): ReliefTileRead {
        // No database: nothing has ever been downloaded or cached on this phone.
        if (!database.isFile) return ReliefTileRead.NotStored
        return try {
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                schemaProblem(db)?.let { return ReliefTileRead.Unsupported(it) }
                db.rawQuery(TILE_QUERY, arrayOf(urlTemplate, z.toString(), x.toString(), y.toString())).use { row ->
                    if (!row.moveToFirst()) return ReliefTileRead.NotStored
                    if (row.isNull(0)) return ReliefTileRead.Empty
                    val stored = row.getBlob(0)
                    val png = if (row.getInt(1) == 1) inflate(stored) else stored
                    when (val decoded = decode(png)) {
                        is ReliefTileRead.Found -> ReliefTileRead.Found(decoded.tile, row.getString(2))
                        else -> decoded
                    }
                }
            }
        } catch (e: Exception) {
            ReliefTileRead.Failed(e)
        }
    }

    /**
     * The tile row's identity while a region links to it: its id, `modified`, size and `compressed`, so a row deleted,
     * unlinked or rewritten answers differently. `null` when no region holds it, or when the database cannot be read
     * (the caller then reads the tile again, which reports why).
     */
    override fun linkedFingerprint(z: Int, x: Int, y: Int): String? {
        if (!database.isFile) return null
        return try {
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                if (schemaProblem(db) != null) return null
                db.rawQuery(FINGERPRINT_QUERY, arrayOf(urlTemplate, z.toString(), x.toString(), y.toString())).use { row ->
                    if (row.moveToFirst()) row.getString(0) else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** What makes [db] a database this does not read, or null. */
    private fun schemaProblem(db: SQLiteDatabase): String? {
        val version = db.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
        if (version != SCHEMA_VERSION) return "user_version $version, not $SCHEMA_VERSION"
        for ((table, needed) in NEEDED_COLUMNS) {
            val columns = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            }
            val missing = needed - columns
            if (missing.isNotEmpty()) return "table $table lacks ${missing.sorted()}"
        }
        return null
    }

    private fun decode(png: ByteArray): ReliefTileRead {
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size, BitmapFactory.Options().apply { inPremultiplied = false })
            ?: return ReliefTileRead.Failed(IllegalStateException("the stored relief tile (${png.size} bytes) does not decode as an image"))
        try {
            if (bitmap.width != bitmap.height) return ReliefTileRead.Unsupported("a ${bitmap.width}x${bitmap.height} relief tile")
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return ReliefTileRead.Found(ReliefTile(bitmap.width, pixels))
        } finally {
            bitmap.recycle()
        }
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream(bytes.size * 2)
            val buffer = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw DataFormatException("relief tile's deflate stream ends early")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    companion object {
        private const val TAG = "OfflineReliefTileStore"

        /** The schema version this reads (MapLibre Native's `offline_schema`, unchanged at android-v13.5.0). */
        const val SCHEMA_VERSION = 6

        private val NEEDED_COLUMNS = mapOf(
            "tiles" to setOf("id", "url_template", "pixel_ratio", "z", "x", "y", "modified", "data", "compressed"),
            "region_tiles" to setOf("region_id", "tile_id"),
        )

        // pixel_ratio 1: see the class doc. Only a tile some region links to (the ambient cache is not counted).
        private const val LINKED_TILE =
            "FROM tiles t WHERE t.url_template = ? AND t.pixel_ratio = 1 AND t.z = ? AND t.x = ? AND t.y = ? " +
                "AND EXISTS (SELECT 1 FROM region_tiles rt WHERE rt.tile_id = t.id) LIMIT 1"

        /** The row's identity: [linkedFingerprint]. */
        private const val FINGERPRINT = "t.id || ':' || IFNULL(t.modified, '') || ':' || IFNULL(length(t.data), -1) || ':' || t.compressed"

        private const val TILE_QUERY = "SELECT t.data, t.compressed, $FINGERPRINT $LINKED_TILE"

        private const val FINGERPRINT_QUERY = "SELECT $FINGERPRINT $LINKED_TILE"
    }
}

/**
 * The Trail Map's relief tile URL, as `map-style/forager-trail-v4.json`'s `dem` source gives it: the key MapLibre stores
 * those tiles under. `ReliefTileUrlTemplateTest` holds it to that file.
 */
const val RELIEF_TILE_URL_TEMPLATE = "https://forager-pmtiles.brandonlee1-894.workers.dev/relief/{z}/{x}/{y}.png"

/**
 * MapLibre's offline database's file name, under the app's files directory: where the SDK keeps it, observed on the S22
 * and the tablet (`MapLibreStorage.kt`) and on the spike's CI emulator.
 */
const val MAPLIBRE_OFFLINE_DATABASE_FILE = "mbgl-offline.db"
