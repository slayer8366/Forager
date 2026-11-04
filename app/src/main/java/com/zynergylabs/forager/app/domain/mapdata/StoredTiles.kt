package com.zynergylabs.forager.app.domain.mapdata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * The pure parts of reading MapLibre's offline store (way-to dispatch 2026-10-28, PR 2). The Android part is
 * `map/MapLibreOfflineBasemap`, which only runs the SQL. Read against MapLibre Native at the app's pinned 13.5.0
 * (tag `android-v13.5.0`):
 * - `platform/default/include/mbgl/storage/offline_schema.sql`: `resources` (kind 2 is a source's TileJSON) and
 *   `tiles` (`url_template`, `z`, `x`, `y`, `data`, `compressed`), both with `compressed` meaning "Deflate".
 * - `platform/default/src/mbgl/storage/offline_database.cpp`: data is stored compressed only when that is smaller,
 *   and read back with `util::decompress`.
 */
object StoredTiles {

    /**
     * A tile or resource's stored bytes as they were served. [compressed] is the row's flag: zlib-wrapped Deflate. A body
     * still in gzip, as a tile served without HTTP decoding would be, is unwrapped as well.
     */
    fun bytesOf(data: ByteArray, compressed: Boolean): ByteArray {
        val served = if (compressed) inflate(data) else data
        return if (served.size >= 2 && served[0] == GZIP_0 && served[1] == GZIP_1) gunzip(served) else served
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw IOException("a stored compressed row ends before its data does")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } catch (e: DataFormatException) {
            throw IOException("a stored compressed row is not Deflate: ${e.message}", e)
        } finally {
            inflater.end()
        }
    }

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(data.inputStream()).use { it.readBytes() }

    /**
     * Of the stored source TileJSONs, the basemap's: the one whose `vector_layers` include `buildings`, as the Forager
     * basemap's do and the reference-trail overlay's do not. Returns its text and the tile URL template MapLibre keys its
     * tiles by (its first `tiles` entry), or null when none is the basemap's.
     */
    fun basemapSource(tileJsons: List<String>): BasemapSource? = tileJsons.firstNotNullOfOrNull { text ->
        val json = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return@firstNotNullOfOrNull null
        val layers = (json["vector_layers"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.content }
        val template = (json["tiles"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content
        if (TileFeatureReader.BUILDINGS in layers && template != null) BasemapSource(text, template) else null
    }

    data class BasemapSource(val tileJson: String, val urlTemplate: String)

    private const val GZIP_0 = 0x1f.toByte()
    private const val GZIP_1 = 0x8b.toByte()
}
