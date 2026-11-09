package com.zynergylabs.forager.app.domain.mapdata

/**
 * A Mapbox Vector Tile (MVT 2.1) decoded to its layers, features, fields and geometry in tile units (way-to dispatch
 * 2026-10-28, PR 2). Pure Kotlin with no dependency, so the way to a waypoint can read the offline basemap tiles the phone
 * already holds, headless and unit-testable.
 *
 * Only what the drop-off and its gates read is kept: each feature's type, fields and geometry. Its id is dropped.
 * Geometry is kept as the tile's own integer coordinates (0 to the layer's extent); [TileFeatureReader] places it on the
 * map. A tile that is not valid protobuf, or whose geometry commands run past their data, fails with
 * [MvtFormatException] naming what was wrong. It never comes back as a partial tile.
 *
 * Read against the specification (github.com/mapbox/vector-tile-spec, 2.1): Tile.layers is field 3; Layer has name 1,
 * features 2, keys 3, values 4, extent 5 (default 4096), version 15; Feature has id 1, tags 2 (packed), type 3, geometry 4
 * (packed); Value has string 1, float 2, double 3, int64 4, uint64 5, sint64 6, bool 7.
 */
object MvtDecoder {

    fun decode(bytes: ByteArray): MvtTile {
        val reader = Protobuf(bytes)
        val layers = mutableListOf<MvtLayer>()
        while (reader.more()) {
            val (field, wire) = reader.key()
            if (field == 3 && wire == Protobuf.LENGTH) layers += layer(reader.bytes()) else reader.skip(wire)
        }
        return MvtTile(layers)
    }

    private fun layer(bytes: ByteArray): MvtLayer {
        val reader = Protobuf(bytes)
        var name: String? = null
        var extent = DEFAULT_EXTENT
        val keys = mutableListOf<String>()
        val values = mutableListOf<Any>()
        val rawFeatures = mutableListOf<ByteArray>()
        while (reader.more()) {
            val (field, wire) = reader.key()
            when {
                field == 1 && wire == Protobuf.LENGTH -> name = reader.bytes().decodeToString()
                field == 2 && wire == Protobuf.LENGTH -> rawFeatures += reader.bytes()
                field == 3 && wire == Protobuf.LENGTH -> keys += reader.bytes().decodeToString()
                field == 4 && wire == Protobuf.LENGTH -> values += value(reader.bytes())
                field == 5 && wire == Protobuf.VARINT -> extent = reader.varint().toInt()
                else -> reader.skip(wire)
            }
        }
        val layerName = name ?: throw MvtFormatException("a layer has no name")
        if (extent <= 0) throw MvtFormatException("layer $layerName has extent $extent")
        return MvtLayer(layerName, extent, rawFeatures.map { feature(it, keys, values, layerName) })
    }

    private fun feature(bytes: ByteArray, keys: List<String>, values: List<Any>, layer: String): MvtFeature {
        val reader = Protobuf(bytes)
        var tags = LongArray(0)
        var type = MvtGeometryType.UNKNOWN
        var geometry = LongArray(0)
        while (reader.more()) {
            val (field, wire) = reader.key()
            when {
                field == 2 && wire == Protobuf.LENGTH -> tags = reader.packed()
                field == 3 && wire == Protobuf.VARINT -> type = MvtGeometryType.of(reader.varint())
                field == 4 && wire == Protobuf.LENGTH -> geometry = reader.packed()
                else -> reader.skip(wire)
            }
        }
        if (tags.size % 2 != 0) throw MvtFormatException("a feature in layer $layer has an odd number of tags")
        val fields = HashMap<String, Any>(tags.size / 2)
        for (i in tags.indices step 2) {
            val key = keys.getOrNull(tags[i].toInt()) ?: throw MvtFormatException("layer $layer: tag key ${tags[i]} out of range")
            val value = values.getOrNull(tags[i + 1].toInt()) ?: throw MvtFormatException("layer $layer: tag value ${tags[i + 1]} out of range")
            fields[key] = value
        }
        return MvtFeature(type, fields, parts(geometry, type, layer))
    }

    /** The geometry commands as parts: points one part each, lines one per MoveTo, rings closed by ClosePath. */
    private fun parts(commands: LongArray, type: MvtGeometryType, layer: String): List<List<TilePoint>> {
        val parts = mutableListOf<List<TilePoint>>()
        var current = mutableListOf<TilePoint>()
        var x = 0L
        var y = 0L
        var i = 0
        while (i < commands.size) {
            val command = (commands[i] and 0x7).toInt()
            val count = (commands[i] ushr 3).toInt()
            i++
            when (command) {
                MOVE_TO, LINE_TO -> {
                    if (i + 2 * count > commands.size) throw MvtFormatException("layer $layer: geometry runs past its data")
                    repeat(count) {
                        if (command == MOVE_TO && current.isNotEmpty()) {
                            parts += current
                            current = mutableListOf()
                        }
                        x += zigzag(commands[i])
                        y += zigzag(commands[i + 1])
                        i += 2
                        if (type == MvtGeometryType.POINT) parts += listOf(TilePoint(x, y)) else current += TilePoint(x, y)
                    }
                }
                CLOSE_PATH -> if (current.isNotEmpty()) current += current.first()
                else -> throw MvtFormatException("layer $layer: unknown geometry command $command")
            }
        }
        if (current.isNotEmpty()) parts += current
        return parts
    }

    private fun value(bytes: ByteArray): Any {
        val reader = Protobuf(bytes)
        var result: Any? = null
        while (reader.more()) {
            val (field, wire) = reader.key()
            result = when (field) {
                1 -> reader.bytes().decodeToString()
                2 -> java.lang.Float.intBitsToFloat(reader.fixed32()).toDouble()
                3 -> java.lang.Double.longBitsToDouble(reader.fixed64())
                4, 5 -> reader.varint()
                6 -> zigzag(reader.varint())
                7 -> reader.varint() != 0L
                else -> {
                    reader.skip(wire)
                    result
                }
            }
        }
        return result ?: throw MvtFormatException("a value has no type")
    }

    private fun zigzag(v: Long): Long = (v ushr 1) xor -(v and 1)

    private const val DEFAULT_EXTENT = 4096
    private const val MOVE_TO = 1
    private const val LINE_TO = 2
    private const val CLOSE_PATH = 7
}

data class MvtTile(val layers: List<MvtLayer>) {
    fun layer(name: String): MvtLayer? = layers.firstOrNull { it.name == name }
}

data class MvtLayer(val name: String, val extent: Int, val features: List<MvtFeature>)

/** [parts]: one point per part for points, one line per part for lines, one closed ring per part for polygons. */
data class MvtFeature(val type: MvtGeometryType, val fields: Map<String, Any>, val parts: List<List<TilePoint>>) {
    fun string(key: String): String? = fields[key] as? String
}

data class TilePoint(val x: Long, val y: Long)

enum class MvtGeometryType {
    UNKNOWN, POINT, LINESTRING, POLYGON;

    companion object {
        fun of(v: Long): MvtGeometryType = entries.getOrElse(v.toInt()) { UNKNOWN }
    }
}

class MvtFormatException(message: String) : Exception(message)

/** The protobuf wire format, as far as MVT uses it: varints, packed varints, length-delimited bytes, fixed32 and 64. */
private class Protobuf(private val bytes: ByteArray) {
    private var at = 0

    fun more(): Boolean = at < bytes.size

    fun key(): Pair<Int, Int> {
        val key = varint()
        return (key ushr 3).toInt() to (key and 0x7).toInt()
    }

    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (at >= bytes.size) throw MvtFormatException("a varint runs past the end of its message")
            if (shift >= 64) throw MvtFormatException("a varint is longer than 10 bytes")
            val b = bytes[at++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b < 0x80) return result
            shift += 7
        }
    }

    fun bytes(): ByteArray {
        val length = varint()
        if (length < 0 || length > bytes.size - at) throw MvtFormatException("a field of $length bytes runs past the end of its message")
        return bytes.copyOfRange(at, at + length.toInt()).also { at += length.toInt() }
    }

    fun packed(): LongArray {
        val inner = Protobuf(bytes())
        val out = ArrayList<Long>()
        while (inner.more()) out += inner.varint()
        return out.toLongArray()
    }

    fun fixed32(): Int {
        if (bytes.size - at < 4) throw MvtFormatException("a fixed32 runs past the end of its message")
        var v = 0
        for (i in 0 until 4) v = v or ((bytes[at + i].toInt() and 0xFF) shl (8 * i))
        at += 4
        return v
    }

    fun fixed64(): Long {
        if (bytes.size - at < 8) throw MvtFormatException("a fixed64 runs past the end of its message")
        var v = 0L
        for (i in 0 until 8) v = v or ((bytes[at + i].toLong() and 0xFF) shl (8 * i))
        at += 8
        return v
    }

    fun skip(wire: Int) {
        when (wire) {
            VARINT -> varint()
            FIXED64 -> fixed64()
            LENGTH -> bytes()
            FIXED32 -> fixed32()
            else -> throw MvtFormatException("unsupported wire type $wire")
        }
    }

    companion object {
        const val VARINT = 0
        const val FIXED64 = 1
        const val LENGTH = 2
        const val FIXED32 = 5
    }
}
