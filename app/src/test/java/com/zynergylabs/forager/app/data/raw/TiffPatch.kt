package com.zynergylabs.forager.app.data.raw

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Byte-level edits to a little-endian TIFF, for planting what the post-write check must catch. Used only through
 * [ForagerDngWriter]'s test hook, which runs between the write and the check.
 */
internal object TiffPatch {

    /** Appends [bytes] at the next even offset at the end of [file] and returns that offset. */
    fun append(file: File, bytes: ByteArray): Long = RandomAccessFile(file, "rw").use { raf ->
        var offset = raf.length()
        if (offset % 2 != 0L) {
            raf.seek(offset)
            raf.write(0)
            offset++
        }
        raf.seek(offset)
        raf.write(bytes)
        offset
    }

    /** Rewrites IFD 0's entry for [existingTag] in place as a different entry, keeping the IFD's entry count. */
    fun replaceEntry(file: File, existingTag: Int, tag: Int, type: Int, count: Int, valueOrOffset: Int) {
        val entry = TiffReader.read(file).ifd0.find(existingTag) ?: error("IFD 0 has no tag $existingTag to replace")
        val bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(tag.toShort())
            .putShort(type.toShort())
            .putInt(count)
            .putInt(valueOrOffset)
            .array()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(entry.entryPosition)
            raf.write(bytes)
        }
    }

    /** Plants a Make tag (271) holding an ASCII string, in place of CFALayout. */
    fun plantMake(file: File) {
        val text = "PlantedMake\u0000".toByteArray(Charsets.US_ASCII)
        val offset = append(file, text)
        replaceEntry(file, existingTag = 50711, tag = 271, type = 2, count = text.size, valueOrOffset = offset.toInt())
    }

    /** Plants a GPS IFD holding a GPSVersionID, pointed to by a GPSInfo tag (34853) in place of NewSubFileType. */
    fun plantGpsIfd(file: File) {
        val gpsIfd = ByteBuffer.allocate(2 + 12 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1)
            .putShort(0).putShort(1).putInt(4).put(byteArrayOf(2, 2, 0, 0))
            .putInt(0)
            .array()
        val offset = append(file, gpsIfd)
        replaceEntry(file, existingTag = 254, tag = 34853, type = 4, count = 1, valueOrOffset = offset.toInt())
    }

    /** Plants an ImageDescription (270), an ASCII tag the check does not forbid, holding [text], in place of CFALayout. */
    fun plantAscii(file: File, text: String) {
        val bytes = (text + "\u0000").toByteArray(Charsets.US_ASCII)
        val offset = append(file, bytes)
        replaceEntry(file, existingTag = 50711, tag = 270, type = 2, count = bytes.size, valueOrOffset = offset.toInt())
    }
}
