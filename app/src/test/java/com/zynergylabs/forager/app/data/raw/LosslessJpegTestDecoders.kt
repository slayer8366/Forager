package com.zynergylabs.forager.app.data.raw

import java.io.File

/**
 * Two lossless JPEG (ITU T.81 process 14, SOF3) decoders written for the tests only, independently of the writer's
 * encoder and of each other, so a round trip through either is evidence about the stream, not about shared code.
 *
 * - [TableDecoder] follows T.81 Annex F.2.2.3's DECODE procedure (MINCODE / MAXCODE / VALPTR) and Annex H's predictors.
 * - [BitStringDecoder] builds a map from each code, as a string of '0' and '1', to its symbol, and reads one bit at a
 *   time until the accumulated string is a code. Slow and obvious, on purpose.
 *
 * Both return one [Frame]: samples in scan order, which for a DNG tile is the tile's own row-major order.
 */
internal class Frame(
    val precision: Int,
    val height: Int,
    val width: Int,
    val components: Int,
    val predictor: Int,
    val samples: IntArray,
)

internal object TableDecoder {

    private class Table(bits: IntArray, val values: IntArray) {
        val minCode = IntArray(17)
        val maxCode = IntArray(18) { -1 }
        val valPtr = IntArray(17)

        init {
            var code = 0
            var k = 0
            for (l in 1..16) {
                valPtr[l] = k
                minCode[l] = code
                code += bits[l]
                k += bits[l]
                maxCode[l] = if (bits[l] == 0) -1 else code - 1
                code = code shl 1
            }
            maxCode[17] = Int.MAX_VALUE
        }
    }

    private class Bits(private val data: ByteArray, private var pos: Int) {
        private var current = 0
        private var left = 0

        fun bit(): Int {
            if (left == 0) {
                val b = if (pos < data.size) data[pos++].toInt() and 0xFF else 0
                if (b == 0xFF) {
                    val next = if (pos < data.size) data[pos].toInt() and 0xFF else 0
                    require(next == 0) { "marker 0xFF%02X inside the entropy-coded data".format(next) }
                    pos++
                }
                current = b
                left = 8
            }
            left--
            return (current shr left) and 1
        }

        fun bits(n: Int): Int {
            var v = 0
            repeat(n) { v = (v shl 1) or bit() }
            return v
        }
    }

    fun decode(stream: ByteArray): Frame {
        require(stream[0] == 0xFF.toByte() && stream[1] == 0xD8.toByte()) { "no SOI" }
        var pos = 2
        val tables = HashMap<Int, Table>()
        var precision = 0
        var height = 0
        var width = 0
        val componentIds = mutableListOf<Int>()
        while (true) {
            require(stream[pos] == 0xFF.toByte()) { "expected a marker at $pos" }
            val marker = stream[pos + 1].toInt() and 0xFF
            val length = ((stream[pos + 2].toInt() and 0xFF) shl 8) or (stream[pos + 3].toInt() and 0xFF)
            val body = pos + 4
            when (marker) {
                0xC3 -> {
                    precision = stream[body].toInt() and 0xFF
                    height = ((stream[body + 1].toInt() and 0xFF) shl 8) or (stream[body + 2].toInt() and 0xFF)
                    width = ((stream[body + 3].toInt() and 0xFF) shl 8) or (stream[body + 4].toInt() and 0xFF)
                    val n = stream[body + 5].toInt() and 0xFF
                    for (i in 0 until n) componentIds += stream[body + 6 + i * 3].toInt() and 0xFF
                }
                0xC4 -> {
                    var p = body
                    while (p < pos + 2 + length) {
                        val id = stream[p].toInt() and 0xFF
                        require(id shr 4 == 0) { "AC table in a lossless stream" }
                        val bits = IntArray(17)
                        var total = 0
                        for (l in 1..16) { bits[l] = stream[p + l].toInt() and 0xFF; total += bits[l] }
                        val values = IntArray(total) { stream[p + 17 + it].toInt() and 0xFF }
                        tables[id and 15] = Table(bits, values)
                        p += 17 + total
                    }
                }
                0xDA -> {
                    val n = stream[body].toInt() and 0xFF
                    require(n == componentIds.size) { "scan has $n components, frame has ${componentIds.size}" }
                    val componentTables = List(n) { tables.getValue((stream[body + 2 + it * 2].toInt() shr 4) and 15) }
                    val predictor = stream[body + 1 + n * 2].toInt() and 0xFF
                    val pointTransform = stream[body + 3 + n * 2].toInt() and 15
                    require(pointTransform == 0) { "point transform $pointTransform" }
                    val samples = decodeScan(stream, pos + 2 + length, width, height, precision, predictor, componentTables)
                    return Frame(precision, height, width, n, predictor, samples)
                }
                else -> error("unexpected marker 0xFF%02X".format(marker))
            }
            pos += 2 + length
        }
    }

    private fun decodeValue(bits: Bits, table: Table): Int {
        var code = bits.bit()
        var l = 1
        while (code > table.maxCode[l]) {
            code = (code shl 1) or bits.bit()
            l++
            require(l <= 16) { "no code of 16 bits or fewer matches" }
        }
        return table.values[table.valPtr[l] + code - table.minCode[l]]
    }

    private fun decodeScan(
        stream: ByteArray,
        start: Int,
        width: Int,
        height: Int,
        precision: Int,
        predictor: Int,
        componentTables: List<Table>,
    ): IntArray {
        val n = componentTables.size
        val out = IntArray(width * height * n)
        val bits = Bits(stream, start)
        val mask = (1 shl 16) - 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (c in 0 until n) {
                    val ssss = decodeValue(bits, componentTables[c])
                    val diff = when (ssss) {
                        0 -> 0
                        16 -> 32768
                        else -> {
                            val v = bits.bits(ssss)
                            if (v < (1 shl (ssss - 1))) v - (1 shl ssss) + 1 else v
                        }
                    }
                    fun at(yy: Int, xx: Int) = out[(yy * width + xx) * n + c]
                    val prediction = when {
                        y == 0 && x == 0 -> 1 shl (precision - 1)
                        y == 0 -> at(0, x - 1)
                        x == 0 -> at(y - 1, 0)
                        else -> {
                            val ra = at(y, x - 1)
                            val rb = at(y - 1, x)
                            val rc = at(y - 1, x - 1)
                            when (predictor) {
                                1 -> ra
                                2 -> rb
                                3 -> rc
                                4 -> ra + rb - rc
                                5 -> ra + ((rb - rc) shr 1)
                                6 -> rb + ((ra - rc) shr 1)
                                7 -> (ra + rb) / 2
                                else -> error("predictor $predictor")
                            }
                        }
                    }
                    out[(y * width + x) * n + c] = (prediction + diff) and mask
                }
            }
        }
        return out
    }
}

internal object BitStringDecoder {

    fun decode(stream: ByteArray): Frame {
        // Strip the markers by walking segment lengths, collecting what each says.
        var i = 2
        var codes = emptyMap<String, Int>()
        var precision = 0
        var height = 0
        var width = 0
        var components = 0
        var predictor = 0
        var scanStart = -1
        while (scanStart < 0) {
            val marker = stream[i + 1].toInt() and 0xFF
            val length = (stream[i + 2].toInt() and 0xFF) * 256 + (stream[i + 3].toInt() and 0xFF)
            val seg = stream.copyOfRange(i + 4, i + 2 + length).map { it.toInt() and 0xFF }
            when (marker) {
                0xC3 -> { precision = seg[0]; height = seg[1] * 256 + seg[2]; width = seg[3] * 256 + seg[4]; components = seg[5] }
                0xC4 -> {
                    // One table only is expected, shared by every component.
                    require(seg.size == 17 + seg.subList(1, 17).sum()) { "expected exactly one Huffman table" }
                    val map = HashMap<String, Int>()
                    var code = 0
                    var v = 17
                    for (len in 1..16) {
                        repeat(seg[len]) {
                            map[Integer.toBinaryString(code).padStart(len, '0')] = seg[v++]
                            code++
                        }
                        code *= 2
                    }
                    codes = map
                }
                0xDA -> { predictor = seg[1 + seg[0] * 2]; scanStart = i + 2 + length }
            }
            i += 2 + length
        }
        require(predictor == 1) { "this decoder knows predictor 1 only, the stream uses $predictor" }

        // Unstuff the entropy-coded bytes into one string of bits.
        val bitString = StringBuilder()
        var p = scanStart
        while (p < stream.size) {
            val b = stream[p].toInt() and 0xFF
            if (b == 0xFF) {
                if ((stream[p + 1].toInt() and 0xFF) != 0) break
                p++
            }
            bitString.append(Integer.toBinaryString(b).padStart(8, '0'))
            p++
        }
        var cursor = 0
        fun readSymbol(): Int {
            val code = StringBuilder()
            while (true) {
                code.append(bitString[cursor++])
                codes[code.toString()]?.let { return it }
                require(code.length <= 16) { "no code matches $code" }
            }
        }
        fun readBits(n: Int): Int {
            val v = if (n == 0) 0 else bitString.substring(cursor, cursor + n).toInt(2)
            cursor += n
            return v
        }

        val rowLength = width * components
        val out = IntArray(rowLength * height)
        for (k in out.indices) {
            val y = k / rowLength
            val x = (k % rowLength) / components
            val ssss = readSymbol()
            val diff = if (ssss == 16) {
                -32768
            } else {
                val raw = readBits(ssss)
                if (ssss > 0 && raw shr (ssss - 1) == 0) raw - (1 shl ssss) + 1 else raw
            }
            val prediction = when {
                x == 0 && y == 0 -> 1 shl (precision - 1)
                x == 0 -> out[k - rowLength]
                else -> out[k - components]
            }
            out[k] = Math.floorMod(prediction + diff, 65536)
        }
        return Frame(precision, height, width, components, predictor, out)
    }
}

/** Reassembles the image a DNG's tiles hold, using [TiffReader] for the tile geometry and [decoder] for each tile. */
internal fun decodeDngSamples(file: File, decoder: (ByteArray) -> Frame): ShortArray {
    val ifd0 = TiffReader.read(file).ifd0
    fun number(tag: Int) = ifd0.find(tag)!!.unsignedIntegers().single().toInt()
    val width = number(256)
    val height = number(257)
    val tileWidth = number(322)
    val tileLength = number(323)
    val offsets = ifd0.find(324)!!.unsignedIntegers()
    val counts = ifd0.find(325)!!.unsignedIntegers()
    val across = (width + tileWidth - 1) / tileWidth
    val down = (height + tileLength - 1) / tileLength
    require(offsets.size == across * down && counts.size == offsets.size) { "tile count does not match the geometry" }
    val bytes = file.readBytes()
    val out = ShortArray(width * height)
    for (t in offsets.indices) {
        val frame = decoder(bytes.copyOfRange(offsets[t].toInt(), (offsets[t] + counts[t]).toInt()))
        require(frame.width * frame.components == tileWidth && frame.height == tileLength) {
            "tile $t is ${frame.width}x${frame.components} by ${frame.height}, the tile is $tileWidth by $tileLength"
        }
        val x0 = (t % across) * tileWidth
        val y0 = (t / across) * tileLength
        for (k in frame.samples.indices) {
            val x = x0 + k % tileWidth
            val y = y0 + k / tileWidth
            if (x < width && y < height) out[y * width + x] = frame.samples[k].toShort()
        }
    }
    return out
}
