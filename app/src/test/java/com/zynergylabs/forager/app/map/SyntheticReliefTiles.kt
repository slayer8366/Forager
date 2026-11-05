package com.zynergylabs.forager.app.map

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource.Companion.RELIEF_TILE_SIZE
import com.zynergylabs.forager.app.domain.relief.ReliefGroundHeightSource.Companion.RELIEF_ZOOM
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sinh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Synthetic relief tiles for dispatch 2026-11-05-01, Amendment 2 (B4), and a store that holds one. No tile from anyone's
 * downloaded area: every byte is made here.
 *
 * **Real-shaped,** as the SDK-written relief tile in `src/test/resources/relief/` is laid out (read with the laptop's
 * Python): 256 x 256, 8-bit RGB, not interlaced; a level-9 zlib stream (header 78 da) in IDAT chunks of 65,536 bytes; no
 * ancillary chunks. That tile is 141,037 bytes in three IDAT chunks. These are about 146 KB in three, because the
 * Terrarium blue channel (1/256 m) carries noise here as real relief does, so they compress about as poorly.
 *
 * Each image has a flat 5 x 5 patch of [TARGET_X], [TARGET_Y] at one height, so the bubble's height source, which reads
 * the pixels nearest the point, gives that height exactly whichever of them it reads.
 */
internal object SyntheticReliefTiles {

    /** The synthetic tile's place: 11/1024/1024, just south-east of 0°, 0°, at sea in the Gulf of Guinea. */
    const val Z = RELIEF_ZOOM
    const val X = 1024
    const val Y = 1024

    /** The pixel the tests read, low in the image, so a tear in the first chunk reaches its row. */
    const val TARGET_X = 128
    const val TARGET_Y = 200

    /** The first IDAT chunk's start: the 8-byte signature and IHDR's 25 bytes. */
    const val FIRST_IDAT = 33

    /** Bytes per IDAT chunk, as the SDK-written tile has them. */
    const val IDAT_CHUNK = 65_536

    /** Where the first IDAT chunk ends and the second begins: its 12 bytes of length, type and CRC around its data. */
    const val FIRST_IDAT_END = FIRST_IDAT + 12 + IDAT_CHUNK

    /** The point at [TARGET_X], [TARGET_Y]'s centre, in Web Mercator at [Z], as `ReliefGroundHeightSource` places pixels. */
    val TARGET: LatLng = run {
        val world = (1 shl Z) * RELIEF_TILE_SIZE.toDouble()
        val gx = X * RELIEF_TILE_SIZE + TARGET_X + 0.5
        val gy = Y * RELIEF_TILE_SIZE + TARGET_Y + 0.5
        LatLng(Math.toDegrees(atan(sinh(PI * (1 - 2 * gy / world)))), gx / world * 360.0 - 180.0)
    }

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    /** One synthetic tile: its PNG, and the Terrarium height of every pixel as its bytes encode it, row by row. */
    class Image(val png: ByteArray, val metres: DoubleArray) {
        fun metresAt(x: Int, y: Int) = metres[y * RELIEF_TILE_SIZE + x]
    }

    /** A tile whose flat patch stands at [flatMetres], with the rest sloping away from it and noise from [seed]. */
    fun image(flatMetres: Double, seed: Long): Image {
        val size = RELIEF_TILE_SIZE
        val random = Random(seed)
        val metres = DoubleArray(size * size)
        val rows = ByteArrayOutputStream(size * (1 + size * 3))
        for (y in 0 until size) {
            rows.write(0) // filter type None
            for (x in 0 until size) {
                val flat = x in TARGET_X - 2..TARGET_X + 2 && y in TARGET_Y - 2..TARGET_Y + 2
                val v = (if (flat) flatMetres else flatMetres + (x - TARGET_X) * 0.5 + (y - TARGET_Y) * 0.25) + 32768.0
                val r = (v / 256).toInt()
                val g = floor(v).toInt() % 256
                val b = if (flat) ((v - floor(v)) * 256).toInt() else random.nextInt(256)
                rows.write(r); rows.write(g); rows.write(b)
                metres[y * size + x] = r * 256.0 + g + b / 256.0 - 32768.0
            }
        }
        val stream = deflate(rows.toByteArray(), Deflater.BEST_COMPRESSION)
        val png = ByteArrayOutputStream(stream.size + 1024)
        png.write(SIGNATURE)
        png.chunk("IHDR", ByteBuffer.allocate(13).putInt(size).putInt(size).put(byteArrayOf(8, 2, 0, 0, 0)).array())
        for (start in stream.indices step IDAT_CHUNK) png.chunk("IDAT", stream.copyOfRange(start, minOf(start + IDAT_CHUNK, stream.size)))
        png.chunk("IEND", ByteArray(0))
        return Image(png.toByteArray(), metres).also { checkShape(it.png) }
    }

    /** [png] with one bit of one byte changed inside its first IDAT chunk's data: its CRC-32 no longer matches. */
    fun oneByteChanged(png: ByteArray): ByteArray {
        val at = FIRST_IDAT + 8 + 1000
        assertTrue("fixture: the byte is inside the first IDAT chunk's data", at < FIRST_IDAT_END - 4)
        return png.copyOf().also { it[at] = (it[at].toInt() xor 0x01).toByte() }
    }

    /**
     * What a read torn across a save could hand back: [first]'s bytes up to [at], then [second]'s, at [second]'s length.
     * [at] is a page boundary as the tests choose it; on the phone a blob's pages fall wherever its row sits, at 4,092-byte
     * steps (an overflow page's payload at page size 4,096), so any offset can be one.
     */
    fun splice(first: ByteArray, second: ByteArray, at: Int): ByteArray {
        assertTrue("fixture: both images reach the splice", first.size > at && second.size > at)
        return first.copyOf(at) + second.copyOfRange(at, second.size)
    }

    /** Every chunk of [png], in order: its type and whether its CRC-32 (type and data) matches. The fixture's own walk. */
    fun chunks(png: ByteArray): List<Pair<String, Boolean>> = buildList {
        val buffer = ByteBuffer.wrap(png)
        buffer.position(SIGNATURE.size)
        while (buffer.remaining() >= 12) {
            val length = buffer.getInt()
            if (length < 0 || length.toLong() + 8 > buffer.remaining()) { add("a length past the end" to false); break }
            val typeAndData = ByteArray(4 + length).also { buffer.get(it) }
            val crc = buffer.getInt().toLong() and 0xffffffffL
            val type = String(typeAndData, 0, 4, Charsets.US_ASCII)
            add(type to (CRC32().apply { update(typeAndData) }.value == crc))
            if (type == "IEND") break
        }
    }

    /**
     * A store laid out as MapLibre Native 13.5.0 lays out `mbgl-offline.db` for what `OfflineReliefTileStore` reads
     * (`user_version` 6; `tiles` with the columns `MapLibreStoreFixtures` gives it; `region_tiles`), holding [png] as the
     * relief tile at [Z]/[X]/[Y], linked to a region. Stored deflated when [compressed], as the SDK stores a tile whose
     * deflated bytes are smaller, and as it is otherwise.
     */
    fun store(file: File, png: ByteArray, compressed: Boolean = false): File {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE tiles (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, url_template TEXT NOT NULL, pixel_ratio INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, expires INTEGER, modified INTEGER, etag TEXT, data BLOB, " +
                    "compressed INTEGER NOT NULL DEFAULT 0, accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0, " +
                    "UNIQUE (url_template, pixel_ratio, z, x, y))",
            )
            db.execSQL("CREATE TABLE region_tiles (region_id INTEGER NOT NULL, tile_id INTEGER NOT NULL, UNIQUE (region_id, tile_id))")
            db.execSQL("PRAGMA user_version = ${OfflineReliefTileStore.SCHEMA_VERSION}")
            val id = db.insertOrThrow("tiles", null, ContentValues().apply {
                put("url_template", RELIEF_TILE_URL_TEMPLATE); put("pixel_ratio", 1); put("z", Z); put("x", X); put("y", Y)
                put("modified", 5); put("data", if (compressed) deflate(png, Deflater.DEFAULT_COMPRESSION) else png)
                put("compressed", if (compressed) 1 else 0); put("accessed", 0)
            })
            db.insertOrThrow("region_tiles", null, ContentValues().apply { put("region_id", 1); put("tile_id", id) })
        }
        return file
    }

    /** The fixture's own check that an image has the real tile's layout, so a test that leans on it cannot pass on a different one. */
    private fun checkShape(png: ByteArray) {
        val chunks = chunks(png)
        assertEquals("fixture: IHDR, three IDAT chunks, IEND, every CRC-32 matching", listOf("IHDR" to true, "IDAT" to true, "IDAT" to true, "IDAT" to true, "IEND" to true), chunks)
        assertEquals("fixture: the first IDAT chunk is ${IDAT_CHUNK} bytes", IDAT_CHUNK, ByteBuffer.wrap(png, FIRST_IDAT, 4).getInt())
    }

    private fun ByteArrayOutputStream.chunk(type: String, data: ByteArray) {
        val typeAndData = type.toByteArray(Charsets.US_ASCII) + data
        write(ByteBuffer.allocate(4).putInt(data.size).array())
        write(typeAndData)
        write(ByteBuffer.allocate(4).putInt(CRC32().apply { update(typeAndData) }.value.toInt()).array())
    }

    private fun deflate(bytes: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level)
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream(bytes.size + 1024)
            val buffer = ByteArray(64 * 1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
