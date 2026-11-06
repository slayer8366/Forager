package com.zynergylabs.forager.app.data.raw

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A small TIFF structure reader: the "our own reader" that [DngCheck] re-parses a written DNG with. It reads the header,
 * every IFD in the chain from IFD 0, and every IFD reached through a pointer tag (SubIFDs, the EXIF IFD, the GPS IFD and
 * the interoperability IFD), and returns each entry's raw value bytes. It never reads pixel data: strips and tiles are
 * reached through offsets, which this reader returns as numbers and does not follow.
 *
 * Plain Kotlin, no Android imports. Malformed input throws [TiffFormatException]; the check turns that into a failure,
 * so a file this reader cannot fully walk is never passed as clean.
 */
internal class TiffReader private constructor(private val file: RandomAccessFile) {

    private var order: ByteOrder = ByteOrder.LITTLE_ENDIAN
    private val length = file.length()

    private fun parse(): TiffStructure {
        if (length < HEADER_SIZE) throw TiffFormatException("file is ${length} bytes, shorter than a TIFF header")
        val header = readAt(0, HEADER_SIZE)
        order = when {
            header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte() -> ByteOrder.LITTLE_ENDIAN
            header[0] == 'M'.code.toByte() && header[1] == 'M'.code.toByte() -> ByteOrder.BIG_ENDIAN
            else -> throw TiffFormatException("no TIFF byte-order mark")
        }
        val buffer = ByteBuffer.wrap(header).order(order)
        if (buffer.getShort(2).toInt() and 0xFFFF != TIFF_MAGIC) throw TiffFormatException("TIFF magic is not 42")
        val firstIfd = buffer.getInt(4).toLong() and 0xFFFFFFFFL

        val ifds = mutableListOf<TiffIfd>()
        val visited = mutableSetOf<Long>()
        // Work list of (offset, label). Every IFD's next pointer is followed, not only IFD 0's, so a chain hung off a
        // sub-IFD is walked too.
        val pending = ArrayDeque<Pair<Long, String>>()
        pending.add(firstIfd to "IFD 0")
        while (pending.isNotEmpty()) {
            val (offset, label) = pending.removeFirst()
            if (offset == 0L) continue
            if (!visited.add(offset)) throw TiffFormatException("$label at $offset is reached twice (an IFD loop)")
            if (visited.size > MAX_IFDS) throw TiffFormatException("more than $MAX_IFDS IFDs")
            val ifd = readIfd(offset, label)
            ifds += ifd
            for (entry in ifd.entries) {
                val kind = POINTER_TAGS[entry.tag] ?: continue
                for ((index, pointer) in entry.unsignedIntegers().withIndex()) {
                    pending.add(pointer to "$kind $index (from $label)")
                }
            }
            pending.add(ifd.nextIfdOffset to "IFD after $label")
        }
        return TiffStructure(order, ifds)
    }

    private fun readIfd(offset: Long, label: String): TiffIfd {
        if (offset < HEADER_SIZE || offset + 2 > length) throw TiffFormatException("$label offset $offset is outside the file")
        val count = ByteBuffer.wrap(readAt(offset, 2)).order(order).getShort().toInt() and 0xFFFF
        if (count == 0 || count > MAX_ENTRIES) throw TiffFormatException("$label has $count entries")
        val tableSize = count * ENTRY_SIZE + 4
        if (offset + 2 + tableSize > length) throw TiffFormatException("$label's entry table runs past the end of the file")
        val table = ByteBuffer.wrap(readAt(offset + 2, tableSize)).order(order)
        val entries = ArrayList<TiffEntry>(count)
        for (i in 0 until count) {
            val base = i * ENTRY_SIZE
            val tag = table.getShort(base).toInt() and 0xFFFF
            val type = table.getShort(base + 2).toInt() and 0xFFFF
            val valueCount = table.getInt(base + 4).toLong() and 0xFFFFFFFFL
            val unit = TYPE_SIZES[type] ?: throw TiffFormatException("$label tag $tag has unknown type $type")
            val size = unit.toLong() * valueCount
            if (size > MAX_VALUE_BYTES) throw TiffFormatException("$label tag $tag declares $size value bytes")
            val entryPosition = offset + 2 + base
            val valuePosition = if (size <= 4) entryPosition + 8 else table.getInt(base + 8).toLong() and 0xFFFFFFFFL
            if (valuePosition + size > length) throw TiffFormatException("$label tag $tag's value runs past the end of the file")
            val value = if (size <= 4) {
                ByteArray(size.toInt()).also { table.position(base + 8); table.get(it) }
            } else {
                readAt(valuePosition, size.toInt())
            }
            entries += TiffEntry(tag, type, valueCount, value, order, entryPosition, valuePosition)
        }
        val next = table.getInt(count * ENTRY_SIZE).toLong() and 0xFFFFFFFFL
        return TiffIfd(label, offset, entries, next)
    }

    private fun readAt(position: Long, size: Int): ByteArray {
        val bytes = ByteArray(size)
        file.seek(position)
        file.readFully(bytes)
        return bytes
    }

    companion object {
        const val HEADER_SIZE = 8
        const val ENTRY_SIZE = 12
        const val TIFF_MAGIC = 42
        private const val MAX_IFDS = 64
        private const val MAX_ENTRIES = 1024
        private const val MAX_VALUE_BYTES = 64L * 1024 * 1024

        /** Tags whose values are offsets of further IFDs, and what those IFDs are. */
        val POINTER_TAGS: Map<Int, String> = mapOf(
            330 to "SubIFD",
            34665 to "EXIF IFD",
            34853 to "GPS IFD",
            40965 to "Interoperability IFD",
        )

        /** Bytes per value of each TIFF 6.0 field type (BYTE ... DOUBLE), plus IFD (13) from the TIFF supplements. */
        val TYPE_SIZES: Map<Int, Int> = mapOf(
            1 to 1, 2 to 1, 3 to 2, 4 to 4, 5 to 8, 6 to 1, 7 to 1, 8 to 2, 9 to 4, 10 to 8, 11 to 4, 12 to 8, 13 to 4,
        )

        fun read(file: File): TiffStructure =
            RandomAccessFile(file, "r").use { TiffReader(it).parse() }
    }
}

internal class TiffFormatException(message: String) : Exception(message)

internal class TiffStructure(val order: ByteOrder, val ifds: List<TiffIfd>) {
    /** IFD 0, the first in the main chain. */
    val ifd0: TiffIfd get() = ifds.first()
}

internal class TiffIfd(val label: String, val offset: Long, val entries: List<TiffEntry>, val nextIfdOffset: Long) {
    fun find(tag: Int): TiffEntry? = entries.firstOrNull { it.tag == tag }
}

/**
 * One IFD entry. [value] holds exactly the value's bytes, in the file's byte order (inline values are trimmed to their
 * declared size). [entryPosition] is where the 12-byte entry sits in the file; [valuePosition] is where the value sits.
 */
internal class TiffEntry(
    val tag: Int,
    val type: Int,
    val count: Long,
    val value: ByteArray,
    private val order: ByteOrder,
    val entryPosition: Long,
    val valuePosition: Long,
) {
    private fun buffer(): ByteBuffer = ByteBuffer.wrap(value).order(order)

    /** BYTE, SHORT, LONG or IFD values as unsigned numbers. */
    fun unsignedIntegers(): List<Long> {
        val b = buffer()
        return when (type) {
            TYPE_BYTE, TYPE_UNDEFINED -> value.map { it.toLong() and 0xFF }
            TYPE_SHORT -> List(count.toInt()) { b.getShort(it * 2).toLong() and 0xFFFF }
            TYPE_LONG, TYPE_IFD -> List(count.toInt()) { b.getInt(it * 4).toLong() and 0xFFFFFFFFL }
            else -> throw TiffFormatException("tag $tag has type $type, not an unsigned integer type")
        }
    }

    /** RATIONAL or SRATIONAL values as (numerator, denominator) pairs. */
    fun rationals(): List<Pair<Long, Long>> {
        val b = buffer()
        return when (type) {
            TYPE_RATIONAL -> List(count.toInt()) {
                (b.getInt(it * 8).toLong() and 0xFFFFFFFFL) to (b.getInt(it * 8 + 4).toLong() and 0xFFFFFFFFL)
            }
            TYPE_SRATIONAL -> List(count.toInt()) { b.getInt(it * 8).toLong() to b.getInt(it * 8 + 4).toLong() }
            else -> throw TiffFormatException("tag $tag has type $type, not a rational type")
        }
    }

    fun doubles(): List<Double> {
        if (type != TYPE_DOUBLE) throw TiffFormatException("tag $tag has type $type, not DOUBLE")
        val b = buffer()
        return List(count.toInt()) { b.getDouble(it * 8) }
    }

    /** An ASCII value up to its first NUL. */
    fun ascii(): String {
        if (type != TYPE_ASCII) throw TiffFormatException("tag $tag has type $type, not ASCII")
        val end = value.indexOf(0).let { if (it < 0) value.size else it }
        return String(value, 0, end, Charsets.ISO_8859_1)
    }

    companion object {
        const val TYPE_BYTE = 1
        const val TYPE_ASCII = 2
        const val TYPE_SHORT = 3
        const val TYPE_LONG = 4
        const val TYPE_RATIONAL = 5
        const val TYPE_UNDEFINED = 7
        const val TYPE_SRATIONAL = 10
        const val TYPE_FLOAT = 11
        const val TYPE_DOUBLE = 12
        const val TYPE_IFD = 13
    }
}
