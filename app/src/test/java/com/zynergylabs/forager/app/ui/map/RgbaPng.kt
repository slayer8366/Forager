package com.zynergylabs.forager.app.ui.map

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

// A minimal PNG reader and writer for NightSpriteTest. The unit tests compile against Android's stubs, which have no
// java.awt or javax.imageio, so the test reads and writes the sprite sheets itself. Only what sprite v5 uses: 8-bit
// RGBA (colour type 6), not interlaced, the five standard row filters. Anything else fails loudly, never guessed.

/** An image as non-premultiplied ARGB ints, row by row, the form `Bitmap.getPixels` gives. */
internal class RgbaImage(val width: Int, val height: Int, val argb: IntArray = IntArray(width * height)) {
    fun pixels(x: Int, y: Int, w: Int, h: Int): IntArray = IntArray(w * h) { i -> argb[(y + i / w) * width + x + i % w] }

    fun setPixels(x: Int, y: Int, w: Int, h: Int, pixels: IntArray) {
        for (i in pixels.indices) argb[(y + i / w) * width + x + i % w] = pixels[i]
    }
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

internal fun readRgbaPng(file: File): RgbaImage {
    val bytes = ByteBuffer.wrap(file.readBytes())
    val signature = ByteArray(8).also { bytes.get(it) }
    require(signature.contentEquals(PNG_SIGNATURE)) { "$file is not a PNG" }
    var width = 0
    var height = 0
    val idat = ByteArrayOutputStream()
    while (bytes.hasRemaining()) {
        val length = bytes.int
        val type = ByteArray(4).also { bytes.get(it) }.toString(Charsets.US_ASCII)
        val data = ByteArray(length).also { bytes.get(it) }
        bytes.int // CRC
        when (type) {
            "IHDR" -> {
                val h = ByteBuffer.wrap(data)
                width = h.int
                height = h.int
                val depth = h.get().toInt()
                val colourType = h.get().toInt()
                h.get()
                h.get()
                val interlace = h.get().toInt()
                require(depth == 8 && colourType == 6 && interlace == 0) { "$file: depth $depth, colour type $colourType, interlace $interlace; only 8-bit RGBA, not interlaced, is read" }
            }
            "IDAT" -> idat.write(data)
            "IEND" -> break
        }
    }
    val stride = width * 4
    val raw = ByteArray((stride + 1) * height)
    Inflater().run {
        setInput(idat.toByteArray())
        var read = 0
        while (read < raw.size && !finished()) read += inflate(raw, read, raw.size - read)
        end()
        require(read == raw.size) { "$file: ${raw.size} bytes expected, $read inflated" }
    }
    val image = RgbaImage(width, height)
    val previous = IntArray(stride)
    val row = IntArray(stride)
    for (y in 0 until height) {
        val filter = raw[y * (stride + 1)].toInt()
        for (i in 0 until stride) {
            val x = raw[y * (stride + 1) + 1 + i].toInt() and 0xFF
            val a = if (i >= 4) row[i - 4] else 0
            val b = previous[i]
            val c = if (i >= 4) previous[i - 4] else 0
            row[i] = (x + when (filter) {
                0 -> 0
                1 -> a
                2 -> b
                3 -> (a + b) / 2
                4 -> paeth(a, b, c)
                else -> error("$file: row filter $filter")
            }) and 0xFF
        }
        for (x in 0 until width) {
            val o = x * 4
            image.argb[y * width + x] = (row[o + 3] shl 24) or (row[o] shl 16) or (row[o + 1] shl 8) or row[o + 2]
        }
        row.copyInto(previous)
    }
    return image
}

private fun paeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = kotlin.math.abs(p - a)
    val pb = kotlin.math.abs(p - b)
    val pc = kotlin.math.abs(p - c)
    return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
}

internal fun writeRgbaPng(image: RgbaImage, file: File) {
    val raw = ByteArrayOutputStream()
    for (y in 0 until image.height) {
        raw.write(0)
        for (x in 0 until image.width) {
            val p = image.argb[y * image.width + x]
            raw.write((p shr 16) and 0xFF)
            raw.write((p shr 8) and 0xFF)
            raw.write(p and 0xFF)
            raw.write((p ushr 24) and 0xFF)
        }
    }
    val deflated = ByteArrayOutputStream()
    Deflater(Deflater.BEST_COMPRESSION).run {
        setInput(raw.toByteArray())
        finish()
        val buffer = ByteArray(65536)
        while (!finished()) deflated.write(buffer, 0, deflate(buffer))
        end()
    }
    val out = ByteArrayOutputStream()
    out.write(PNG_SIGNATURE)
    fun chunk(type: String, data: ByteArray) {
        val d = DataOutputStream(out)
        d.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        d.write(typeBytes)
        d.write(data)
        d.writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
    }
    chunk("IHDR", ByteBuffer.allocate(13).putInt(image.width).putInt(image.height).put(8).put(6).put(0).put(0).put(0).array())
    chunk("IDAT", deflated.toByteArray())
    chunk("IEND", ByteArray(0))
    file.writeBytes(out.toByteArray())
}
