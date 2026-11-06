package com.zynergylabs.forager.app.data.raw

import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.LensShadingMap
import com.zynergylabs.forager.app.domain.raw.SRational
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lays out one DNG 1.4 file: a little-endian TIFF with a single IFD, IFD 0, which is the raw CFA image itself
 * (NewSubFileType 0), in tiles of lossless JPEG, 256 x 256 or the image's size if smaller ([tileSide]), (Compression 7). No preview or thumbnail IFD.
 *
 * It writes only the tags listed in [entriesFor], each from a value it was given. Make, Model, Software, DateTime,
 * DateTimeOriginal, location and any EXIF or GPS IFD are never among them (RECORD -975, -1008); the check that follows
 * the write ([DngCheck]) does not take that on trust.
 *
 * Little-endian only. The input must already have passed [DngImageLimits].
 */
internal object DngFileWriter {

    const val TILE_SIZE = 256

    // TIFF and DNG tag numbers (TIFF 6.0; TIFF-EP for the CFA pair; DNG 1.4 chapter 4).
    private const val NEW_SUBFILE_TYPE = 254
    private const val IMAGE_WIDTH = 256
    private const val IMAGE_LENGTH = 257
    private const val BITS_PER_SAMPLE = 258
    private const val COMPRESSION = 259
    private const val PHOTOMETRIC = 262
    private const val ORIENTATION = 274
    private const val SAMPLES_PER_PIXEL = 277
    private const val PLANAR_CONFIGURATION = 284
    private const val TILE_WIDTH = 322
    private const val TILE_LENGTH = 323
    private const val TILE_OFFSETS = 324
    private const val TILE_BYTE_COUNTS = 325
    private const val CFA_REPEAT_PATTERN_DIM = 33421
    private const val CFA_PATTERN = 33422
    private const val DNG_VERSION = 50706
    private const val DNG_BACKWARD_VERSION = 50707
    private const val UNIQUE_CAMERA_MODEL = 50708
    private const val CFA_PLANE_COLOR = 50710
    private const val CFA_LAYOUT = 50711
    private const val BLACK_LEVEL_REPEAT_DIM = 50713
    private const val BLACK_LEVEL = 50714
    private const val WHITE_LEVEL = 50717
    private const val COLOR_MATRIX_1 = 50721
    private const val COLOR_MATRIX_2 = 50722
    private const val CAMERA_CALIBRATION_1 = 50723
    private const val CAMERA_CALIBRATION_2 = 50724
    private const val AS_SHOT_NEUTRAL = 50728
    private const val BASELINE_EXPOSURE = 50730
    private const val CALIBRATION_ILLUMINANT_1 = 50778
    private const val CALIBRATION_ILLUMINANT_2 = 50779
    private const val ACTIVE_AREA = 50829
    private const val FORWARD_MATRIX_1 = 50964
    private const val FORWARD_MATRIX_2 = 50965
    private const val OPCODE_LIST_2 = 51009
    private const val NOISE_PROFILE = 51041

    private const val COMPRESSION_JPEG = 7
    private const val PHOTOMETRIC_CFA = 32803

    /**
     * A tile side: [TILE_SIZE], or the image's own side rounded up to a multiple of 16 (TIFF 6.0 requires multiples of
     * 16) when that is smaller. A tile wider than the image is legal TIFF, but one common raw library wraps a tile's
     * rows at the image width in that case and scrambles the image from its second row on (LibRaw 0.22.1, seen on a
     * CI-written 64 x 48 file; `lossless_dng_load_raw` in its src/decoders/dng.cpp). Phone sensors are far wider than
     * 256, so this matters only for small images, and for a width that is not a multiple of 16 below 256 that library
     * can still misplace samples: recorded in the dispatch's completion report, not worked around.
     */
    private fun tileSide(imageSide: Int): Int = minOf(TILE_SIZE, (imageSide + 15) / 16 * 16)

    /** The 2x2 CFA cell's colours, row-major, TIFF-EP codes 0 red, 1 green, 2 blue. */
    fun cfaColours(cfa: CfaPattern): IntArray = when (cfa) {
        CfaPattern.RGGB -> intArrayOf(0, 1, 1, 2)
        CfaPattern.GRBG -> intArrayOf(1, 0, 2, 1)
        CfaPattern.GBRG -> intArrayOf(1, 2, 0, 1)
        CfaPattern.BGGR -> intArrayOf(2, 1, 1, 0)
    }

    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    /** Writes [image] to [destination] and returns the number of bytes written. Throws on I/O failure. */
    fun write(image: DngImage, destination: File): Long {
        val tileWidth = tileSide(image.width)
        val tileLength = tileSide(image.height)
        val across = (image.width + tileWidth - 1) / tileWidth
        val down = (image.height + tileLength - 1) / tileLength
        val tiles = ArrayList<ByteArray>(across * down)
        for (ty in 0 until down) {
            for (tx in 0 until across) {
                tiles += LosslessJpegEncoder.encodeTile(
                    image.samples, image.width, image.height, tx * tileWidth, ty * tileLength, tileWidth, tileLength,
                )
            }
        }

        val entries = entriesFor(image, tiles, tileWidth, tileLength).sortedBy { it.tag }
        val ifdOffset = 8L
        val ifdSize = 2L + 12L * entries.size + 4L
        // Out-of-line values follow the IFD, each on an even offset (TIFF 6.0: values begin on a word boundary).
        var cursor = ifdOffset + ifdSize
        val valueOffsets = LongArray(entries.size)
        for ((i, e) in entries.withIndex()) {
            if (e.data.size > 4) {
                if (cursor % 2 != 0L) cursor++
                valueOffsets[i] = cursor
                cursor += e.data.size
            }
        }
        if (cursor % 2 != 0L) cursor++
        val tileOffsets = LongArray(tiles.size)
        for ((i, t) in tiles.withIndex()) {
            tileOffsets[i] = cursor
            cursor += t.size
            if (cursor % 2 != 0L) cursor++
        }
        if (cursor > 0xFFFFFFFFL) throw IllegalStateException("the file would be $cursor bytes, past TIFF's 4 GB")

        // TileOffsets now has its real values; its size was already fixed, so the layout above still holds.
        val offsetsIndex = entries.indexOfFirst { it.tag == TILE_OFFSETS }
        tileOffsets.forEachIndexed { i, o -> ByteBuffer.wrap(entries[offsetsIndex].data).order(ByteOrder.LITTLE_ENDIAN).putInt(i * 4, o.toInt()) }

        val counter = CountingStream(BufferedOutputStream(FileOutputStream(destination), 1 shl 16))
        counter.use { out ->
            out.write(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0))
            out.write(le32(ifdOffset))
            val ifd = ByteBuffer.allocate(ifdSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            ifd.putShort(entries.size.toShort())
            for ((i, e) in entries.withIndex()) {
                ifd.putShort(e.tag.toShort())
                ifd.putShort(e.type.toShort())
                ifd.putInt(e.count)
                if (e.data.size > 4) {
                    ifd.putInt(valueOffsets[i].toInt())
                } else {
                    ifd.put(e.data)
                    repeat(4 - e.data.size) { ifd.put(0.toByte()) }
                }
            }
            ifd.putInt(0) // no next IFD
            out.write(ifd.array())
            for ((i, e) in entries.withIndex()) {
                if (e.data.size > 4) {
                    padTo(out, valueOffsets[i])
                    out.write(e.data)
                }
            }
            for ((i, t) in tiles.withIndex()) {
                padTo(out, tileOffsets[i])
                out.write(t)
            }
            padTo(out, cursor)
        }
        return counter.written
    }

    private fun entriesFor(image: DngImage, tiles: List<ByteArray>, tileWidth: Int, tileLength: Int): List<Entry> {
        val e = mutableListOf<Entry>()
        val colours = cfaColours(image.cfa)
        e += longs(NEW_SUBFILE_TYPE, 0)
        e += longs(IMAGE_WIDTH, image.width.toLong())
        e += longs(IMAGE_LENGTH, image.height.toLong())
        e += shorts(BITS_PER_SAMPLE, 16)
        e += shorts(COMPRESSION, COMPRESSION_JPEG)
        e += shorts(PHOTOMETRIC, PHOTOMETRIC_CFA)
        e += shorts(ORIENTATION, image.orientation)
        e += shorts(SAMPLES_PER_PIXEL, 1)
        e += shorts(PLANAR_CONFIGURATION, 1)
        e += longs(TILE_WIDTH, tileWidth.toLong())
        e += longs(TILE_LENGTH, tileLength.toLong())
        e += Entry(TILE_OFFSETS, TiffEntry.TYPE_LONG, tiles.size, ByteArray(4 * tiles.size))
        e += longs(TILE_BYTE_COUNTS, *LongArray(tiles.size) { tiles[it].size.toLong() })
        e += shorts(CFA_REPEAT_PATTERN_DIM, 2, 2)
        e += bytes(CFA_PATTERN, *colours)
        // DNG 1.4.0.0; readable by readers of 1.1.0.0 onwards, the first version whose lossless JPEG is T.81-exact
        // (Appendix A, Compatibility Issue 2).
        e += bytes(DNG_VERSION, 1, 4, 0, 0)
        e += bytes(DNG_BACKWARD_VERSION, 1, 1, 0, 0)
        e += ascii(UNIQUE_CAMERA_MODEL, DngCheck.FORAGER)
        e += bytes(CFA_PLANE_COLOR, 0, 1, 2)
        e += shorts(CFA_LAYOUT, 1)
        e += shorts(BLACK_LEVEL_REPEAT_DIM, 2, 2)
        e += blackLevel(image.blackLevel)
        e += longs(WHITE_LEVEL, image.whiteLevel.toLong())
        e += srationals(COLOR_MATRIX_1, image.calibration1.colorMatrix)
        e += shorts(CALIBRATION_ILLUMINANT_1, image.calibration1.illuminant)
        image.calibration1.cameraCalibration?.let { e += srationals(CAMERA_CALIBRATION_1, it) }
        image.calibration1.forwardMatrix?.let { e += srationals(FORWARD_MATRIX_1, it) }
        image.calibration2?.let { c ->
            e += srationals(COLOR_MATRIX_2, c.colorMatrix)
            e += shorts(CALIBRATION_ILLUMINANT_2, c.illuminant)
            c.cameraCalibration?.let { e += srationals(CAMERA_CALIBRATION_2, it) }
            c.forwardMatrix?.let { e += srationals(FORWARD_MATRIX_2, it) }
        }
        e += rationals(AS_SHOT_NEUTRAL, image.asShotNeutral.map { it.numerator to it.denominator })
        image.baselineExposure?.let { e += srationals(BASELINE_EXPOSURE, listOf(it)) }
        image.activeArea?.let { a -> e += longs(ACTIVE_AREA, *LongArray(4) { a[it].toLong() }) }
        image.noiseProfile?.let { e += noiseProfile(it, colours) }
        image.lensShading?.let { e += gainMaps(image, it, colours) }
        return e
    }

    /**
     * NoiseProfile (DNG 1.4 chapter 4): one (S, O) pair per colour plane, in CFAPlaneColor order. The camera gives a
     * pair per CFA channel (four); each plane takes the pair of the first CFA position of its colour, so green takes
     * the first green's.
     */
    private fun noiseProfile(pairs: List<Double>, colours: IntArray): Entry {
        val out = ByteBuffer.allocate(6 * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (plane in 0..2) {
            val i = colours.indexOf(plane)
            out.putDouble(pairs[2 * i])
            out.putDouble(pairs[2 * i + 1])
        }
        return Entry(NOISE_PROFILE, TiffEntry.TYPE_DOUBLE, 6, out.array())
    }

    /**
     * OpcodeList2 with one GainMap (opcode 9, DNG 1.4 chapter 7) per CFA channel. OpcodeList2 applies to the image
     * after linearization, whose origin is the ActiveArea's top-left, so each map covers the active area with row and
     * column pitch 2, starting at its channel's position in the 2x2 cell. The camera's map is sampled at its grid
     * corners across the whole area: origin 0 and spacing 1 / (points - 1). Opcode lists are big-endian whatever the
     * file's byte order (chapter 7). Each GainMap is flagged optional (bit 0), so a reader that cannot apply it can
     * still open the file.
     */
    private fun gainMaps(image: DngImage, map: LensShadingMap, colours: IntArray): Entry {
        val area = image.activeArea ?: intArrayOf(0, 0, image.height, image.width)
        val areaHeight = area[2] - area[0]
        val areaWidth = area[3] - area[1]
        // The camera's channel order is R, Geven (green on the cell's first row), Godd (its second row), B.
        val positions = intArrayOf(
            colours.indexOf(0),
            if (colours[0] == 1) 0 else 1,
            if (colours[2] == 1) 2 else 3,
            colours.indexOf(2),
        )
        val points = map.columns * map.rows
        val paramSize = 10 * 4 + 4 * 8 + 4 + points * 4
        val out = ByteBuffer.allocate(4 + 4 * (16 + paramSize)).order(ByteOrder.BIG_ENDIAN)
        out.putInt(4)
        for ((channel, position) in positions.withIndex()) {
            out.putInt(9) // GainMap
            out.putInt(0x01030000) // defined in DNG 1.3.0.0
            out.putInt(1) // optional
            out.putInt(paramSize)
            out.putInt(position / 2) // Top
            out.putInt(position % 2) // Left
            out.putInt(areaHeight) // Bottom
            out.putInt(areaWidth) // Right
            out.putInt(0) // Plane
            out.putInt(1) // Planes
            out.putInt(2) // RowPitch
            out.putInt(2) // ColPitch
            out.putInt(map.rows) // MapPointsV
            out.putInt(map.columns) // MapPointsH
            out.putDouble(1.0 / maxOf(1, map.rows - 1)) // MapSpacingV
            out.putDouble(1.0 / maxOf(1, map.columns - 1)) // MapSpacingH
            out.putDouble(0.0) // MapOriginV
            out.putDouble(0.0) // MapOriginH
            out.putInt(1) // MapPlanes
            for (p in 0 until points) out.putFloat(map.gains[p * 4 + channel])
        }
        return Entry(OPCODE_LIST_2, TiffEntry.TYPE_UNDEFINED, out.capacity(), out.array())
    }

    /** BlackLevel as LONG when every level is whole, otherwise as RATIONAL (DNG 1.4 allows SHORT, LONG or RATIONAL). */
    private fun blackLevel(levels: List<Float>): Entry {
        if (levels.all { it == Math.floor(it.toDouble()).toFloat() }) {
            return longs(BLACK_LEVEL, *LongArray(levels.size) { levels[it].toLong() })
        }
        // A float's fraction below 2^-16 is not kept: no sensor's black level is set that finely.
        return rationals(BLACK_LEVEL, levels.map { level ->
            var numerator = Math.round(level.toDouble() * 65536.0)
            var denominator = 65536L
            while (denominator > 1 && numerator % 2 == 0L) { numerator /= 2; denominator /= 2 }
            numerator to denominator
        })
    }

    private fun shorts(tag: Int, vararg values: Int): Entry {
        val b = ByteBuffer.allocate(2 * values.size).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { b.putShort(it.toShort()) }
        return Entry(tag, TiffEntry.TYPE_SHORT, values.size, b.array())
    }

    private fun longs(tag: Int, vararg values: Long): Entry {
        val b = ByteBuffer.allocate(4 * values.size).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { b.putInt(it.toInt()) }
        return Entry(tag, TiffEntry.TYPE_LONG, values.size, b.array())
    }

    private fun bytes(tag: Int, vararg values: Int): Entry =
        Entry(tag, TiffEntry.TYPE_BYTE, values.size, ByteArray(values.size) { values[it].toByte() })

    private fun ascii(tag: Int, text: String): Entry {
        val data = (text + "\u0000").toByteArray(Charsets.US_ASCII)
        return Entry(tag, TiffEntry.TYPE_ASCII, data.size, data)
    }

    private fun rationals(tag: Int, values: List<Pair<Long, Long>>): Entry {
        val b = ByteBuffer.allocate(8 * values.size).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { (n, d) -> b.putInt(n.toInt()); b.putInt(d.toInt()) }
        return Entry(tag, TiffEntry.TYPE_RATIONAL, values.size, b.array())
    }

    private fun srationals(tag: Int, values: List<SRational>): Entry {
        val b = ByteBuffer.allocate(8 * values.size).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { b.putInt(it.numerator); b.putInt(it.denominator) }
        return Entry(tag, TiffEntry.TYPE_SRATIONAL, values.size, b.array())
    }

    private fun le32(value: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.toInt()).array()

    private fun padTo(out: CountingStream, position: Long) {
        check(out.written <= position) { "layout overran: at ${out.written}, next item at $position" }
        while (out.written < position) out.write(0)
    }

    private class CountingStream(private val inner: OutputStream) : OutputStream() {
        var written = 0L
            private set

        override fun write(b: Int) { inner.write(b); written++ }
        override fun write(b: ByteArray, off: Int, len: Int) { inner.write(b, off, len); written += len }
        override fun flush() = inner.flush()
        override fun close() = inner.close()
    }
}
