package com.zynergylabs.forager.app.map

import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * What makes [png] not whole by its own checksums, or `null` when it is: checked before anything decodes it (dispatch
 * 2026-11-05-01, Amendment 2, A10; the owner, to "The waypoint bubble's ground height could, rarely, read a half-saved
 * image. What should happen?": "Add a check (Recommended)").
 *
 * **Two checks, both the PNG's own:**
 * - every chunk's CRC-32, over its type and data, from the signature to IEND;
 * - the image data's zlib stream, every IDAT chunk's data in order, inflated to its end, where zlib checks the
 *   Adler-32 of everything it inflated. The inflated bytes are not kept.
 *
 * **Why.** A read of MapLibre's store can overlap MapLibre's save, and a blob that spans pages can then come back with
 * some pages from before the save and some from after (the completion report, section 5). A row MapLibre stored deflated
 * has zlib's check already (`OfflineReliefTileStore.inflate`); a raw PNG has only its own, and only a decoder that reads
 * them protects it.
 *
 * **Not left to the decoder** (the alternative). Observed on the JVM (Robolectric's Android 16 native graphics, the
 * tests-first run 42066897340 of `ReliefImageChecksTest`): the platform decoder already gives no image for a failed IDAT
 * CRC-32, but takes an image whose CRC-32s all hold and whose zlib stream breaks, as a tear where a chunk ends leaves it,
 * and its unread rows come back zero, which is Terrarium's -32768 m. Why, read in Skia's `SkPngCodec.cpp` and Android's
 * `BitmapFactory.cpp` at main, not at the Android 16 tags: libpng treats that zlib error as a warning, so the codec
 * reports incomplete input, which BitmapFactory accepts. The phone's own build of the decoder was not read. This check
 * makes the outcome the same whatever decoder the phone has.
 *
 * Bytes after IEND, and after the zlib stream's end, are not refused: no decoder reads them, so they cannot change a
 * height, and refusing them is not in the ruling.
 */
internal fun pngIntegrityProblem(png: ByteArray): String? {
    if (png.size < SIGNATURE.size || !png.copyOf(SIGNATURE.size).contentEquals(SIGNATURE)) return "it has no PNG signature"
    val imageData = mutableListOf<IntRange>()
    var at = SIGNATURE.size
    var chunk = 0
    while (true) {
        chunk++
        if (png.size - at < 12) return "it ends inside chunk $chunk"
        val length = ByteBuffer.wrap(png, at, 4).int
        if (length < 0 || length > png.size - at - 12) return "chunk $chunk's length runs past its end"
        val type = String(png, at + 4, 4, Charsets.ISO_8859_1)
        val crc = CRC32().apply { update(png, at + 4, 4 + length) }.value
        if (crc != (ByteBuffer.wrap(png, at + 8 + length, 4).int.toLong() and 0xffffffffL)) return "chunk $chunk ($type) fails its CRC-32"
        if (type == "IDAT") imageData += at + 8 until at + 8 + length
        at += 12 + length
        if (type == "IEND") break
    }
    return zlibProblem(png, imageData)
}

/** What makes the zlib stream in [png]'s [ranges], read in order, not inflate to its end with its Adler-32, or `null`. */
private fun zlibProblem(png: ByteArray, ranges: List<IntRange>): String? {
    val inflater = Inflater()
    try {
        val out = ByteArray(64 * 1024)
        for (range in ranges) {
            if (inflater.finished()) break
            inflater.setInput(png, range.first, range.last - range.first + 1)
            while (!inflater.finished() && !inflater.needsInput()) {
                if (inflater.inflate(out) == 0 && inflater.needsDictionary()) return "its image data's zlib stream asks for a preset dictionary"
            }
        }
        if (!inflater.finished()) return "its image data's zlib stream ends early, before its Adler-32"
        return null
    } catch (e: DataFormatException) {
        return "its image data's zlib stream fails: ${e.message}"
    } finally {
        inflater.end()
    }
}

private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
