package com.zynergylabs.forager.app.data.raw

import java.io.ByteArrayOutputStream

/**
 * Lossless JPEG (ITU T.81 process 14: Huffman, SOF3) for one DNG tile of a CFA image, the form DNG 1.4's Compression
 * section allows for Compression 7 and common desktop raw readers decode: the tile is coded as an image half as wide
 * with two interleaved components, so that predictor 1 ("left") predicts each sample from the like-coloured sample two
 * columns over rather than from a different colour.
 *
 * Choices, each fixed:
 * - **Precision 16.** Samples are taken as unsigned 16-bit, so any value the sensor delivers fits; differences are
 *   modulo 2^16 (T.81 H.1.2.1), and the one 16-bit category, -32768, carries no extra bits, as T.81 specifies and DNG
 *   1.1.0.0 onwards expects (DNG 1.4 Appendix A, Compatibility Issue 2).
 * - **Predictor 1** (Ra, the left neighbour). Row starts use the sample above and the first sample uses 2^(P-1), as
 *   T.81 H.1.2.1 fixes for every predictor.
 * - **One optimal Huffman table per tile**, shared by both components, built from that tile's own category counts by
 *   T.81 Annex K.2's procedure and limited to 16-bit codes, with the all-ones code reserved.
 * - **No restart markers, point transform 0.**
 *
 * The decoder that proves the round trip lives in the tests, written independently of this file.
 */
internal object LosslessJpegEncoder {

    const val PRECISION = 16
    private const val CATEGORIES = 17 // SSSS 0..16

    /**
     * Encodes the [tileWidth] x [tileLength] tile whose top-left sample is ([x0], [y0]) in a [width] x [height] image of
     * [samples]. Positions past the image's right or bottom edge are padding: they repeat the nearest in-image sample of
     * the same CFA colour, which keeps the predictor's differences small. Readers discard padding (TIFF 6.0, tiles).
     */
    fun encodeTile(
        samples: ShortArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        tileWidth: Int,
        tileLength: Int,
    ): ByteArray {
        require(tileWidth % 2 == 0) { "tile width $tileWidth is odd" }
        val frameWidth = tileWidth / 2

        // Pass 1: every difference, in scan order, and how often each category occurs.
        val diffs = IntArray(tileWidth * tileLength)
        val frequencies = IntArray(CATEGORIES)
        val tile = IntArray(tileWidth * tileLength)
        for (r in 0 until tileLength) {
            val y = padded(y0 + r, height)
            for (c in 0 until tileWidth) {
                tile[r * tileWidth + c] = samples[y * width + padded(x0 + c, width)].toInt() and 0xFFFF
            }
        }
        for (r in 0 until tileLength) {
            for (c in 0 until tileWidth) {
                val k = r * tileWidth + c
                val prediction = when {
                    r == 0 && c < 2 -> 1 shl (PRECISION - 1)
                    c < 2 -> tile[k - tileWidth]
                    else -> tile[k - 2]
                }
                var diff = (tile[k] - prediction) and 0xFFFF
                if (diff >= 32768) diff -= 65536
                diffs[k] = diff
                frequencies[category(diff)]++
            }
        }

        val table = HuffmanTable.optimal(frequencies)

        val out = ByteArrayOutputStream(tileWidth * tileLength)
        // SOI
        out.write(0xFF); out.write(0xD8)
        // SOF3: precision, lines, samples per line, two components, each sampled 1x1 with no quantisation table.
        out.write(0xFF); out.write(0xC3)
        writeShort(out, 8 + 3 * 2)
        out.write(PRECISION)
        writeShort(out, tileLength)
        writeShort(out, frameWidth)
        out.write(2)
        out.write(1); out.write(0x11); out.write(0)
        out.write(2); out.write(0x11); out.write(0)
        // DHT: one DC-class table, id 0.
        out.write(0xFF); out.write(0xC4)
        writeShort(out, 2 + 1 + 16 + table.values.size)
        out.write(0x00)
        for (l in 1..16) out.write(table.counts[l])
        for (v in table.values) out.write(v)
        // SOS: both components on table 0, Ss = predictor 1, Se = 0, Ah = Al = 0.
        out.write(0xFF); out.write(0xDA)
        writeShort(out, 6 + 2 * 2)
        out.write(2)
        out.write(1); out.write(0x00)
        out.write(2); out.write(0x00)
        out.write(1)
        out.write(0)
        out.write(0)

        // Pass 2: the entropy-coded differences.
        val bits = BitWriter(out)
        for (diff in diffs) {
            val ssss = category(diff)
            bits.write(table.codes[ssss], table.lengths[ssss])
            if (ssss in 1..15) {
                val extra = if (diff < 0) diff - 1 else diff
                bits.write(extra and ((1 shl ssss) - 1), ssss)
            }
        }
        bits.flush()
        // EOI
        out.write(0xFF); out.write(0xD9)
        return out.toByteArray()
    }

    /** SSSS, the number of bits a difference's magnitude needs; -32768 is category 16 (T.81 Table H.2). */
    fun category(diff: Int): Int = when {
        diff == 0 -> 0
        diff == -32768 -> 16
        else -> 32 - Integer.numberOfLeadingZeros(if (diff < 0) -diff else diff)
    }

    /** Clamps a coordinate past the edge back inside it, onto the same CFA parity where the image allows. */
    private fun padded(v: Int, size: Int): Int {
        if (v < size) return v
        val last = size - 1
        return if ((v - last) % 2 == 0 || last == 0) last else last - 1
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    /** Writes bits most significant first, stuffing a zero byte after every 0xFF and padding the end with ones. */
    private class BitWriter(private val out: ByteArrayOutputStream) {
        private var buffer = 0L
        private var count = 0

        fun write(value: Int, length: Int) {
            if (length == 0) return
            buffer = (buffer shl length) or (value.toLong() and ((1L shl length) - 1))
            count += length
            while (count >= 8) {
                count -= 8
                emit(((buffer shr count) and 0xFF).toInt())
            }
            buffer = buffer and ((1L shl count) - 1)
        }

        fun flush() {
            if (count > 0) write((1 shl (8 - count)) - 1, 8 - count)
        }

        private fun emit(byte: Int) {
            out.write(byte)
            if (byte == 0xFF) out.write(0x00)
        }
    }
}

/**
 * A Huffman table for SSSS categories 0..16: [counts] (BITS, index 1..16), [values] (HUFFVAL), and per category its
 * [codes] and [lengths] (0 for a category with no code).
 */
internal class HuffmanTable private constructor(val counts: IntArray, val values: IntArray) {
    val codes = IntArray(17)
    val lengths = IntArray(17)

    init {
        // T.81 Annex C: canonical codes, consecutive within a length, shifted left between lengths.
        var code = 0
        var k = 0
        for (l in 1..16) {
            repeat(counts[l]) {
                codes[values[k]] = code
                lengths[values[k]] = l
                code++
                k++
            }
            code = code shl 1
        }
    }

    companion object {
        /**
         * T.81 Annex K.2's optimal table, limited to 16 bits (Figure K.3, Adjust_BITS), with one extra symbol of
         * frequency 1 so that no real symbol gets the all-ones code. Categories that never occur get no code.
         */
        fun optimal(frequencies: IntArray): HuffmanTable {
            val symbols = frequencies.size // 17; index 17 is the reserved symbol
            val freq = LongArray(symbols + 1) { if (it < symbols) frequencies[it].toLong() else 1L }
            val codeSize = IntArray(symbols + 1)
            val others = IntArray(symbols + 1) { -1 }
            while (true) {
                // V1: the least frequent symbol, the highest-numbered on a tie; V2: the next, excluding V1.
                var v1 = -1
                for (i in freq.indices) if (freq[i] > 0 && (v1 < 0 || freq[i] <= freq[v1])) v1 = i
                var v2 = -1
                for (i in freq.indices) if (i != v1 && freq[i] > 0 && (v2 < 0 || freq[i] <= freq[v2])) v2 = i
                if (v2 < 0) break
                freq[v1] += freq[v2]
                freq[v2] = 0
                var a = v1
                codeSize[a]++
                while (others[a] >= 0) { a = others[a]; codeSize[a]++ }
                others[a] = v2
                var b = v2
                codeSize[b]++
                while (others[b] >= 0) { b = others[b]; codeSize[b]++ }
            }
            val bits = IntArray(33)
            for (i in codeSize.indices) if (codeSize[i] > 0) bits[codeSize[i]]++
            var i = 32
            while (i > 16) {
                while (bits[i] > 0) {
                    var j = i - 2
                    while (bits[j] == 0) j--
                    bits[i] -= 2
                    bits[i - 1]++
                    bits[j + 1] += 2
                    bits[j]--
                }
                i--
            }
            // Remove the reserved symbol's code, which sits among the longest.
            i = 16
            while (bits[i] == 0) i--
            bits[i]--
            val values = mutableListOf<Int>()
            for (length in 1..32) {
                for (s in 0 until symbols) if (codeSize[s] == length) values += s
            }
            return HuffmanTable(bits.copyOf(17), values.toIntArray())
        }
    }
}
