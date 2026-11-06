package com.zynergylabs.forager.app.domain.mapdata

import java.io.ByteArrayOutputStream

/**
 * Small vector tiles made for tests of the Offline Maps picker's detail check (known bug 14), encoded to the MVT 2.1 wire
 * format [MvtDecoder] reads. Each tile carries the layers and feature kinds the Phase 1b report and the Phase 2 session read
 * from the live server, with made-up geometry: no live bytes are copied in.
 */
internal object TestMvt {
    class Feature(val type: Int, val kind: String, val geometry: List<Long>)

    class Layer(val name: String, val features: List<Feature>)

    fun layer(name: String, vararg features: Feature) = Layer(name, features.toList())

    /** A square over the whole tile, as the land and sea fills are drawn. */
    fun polygon(kind: String) = Feature(POLYGON, kind, SQUARE)

    fun line(kind: String) = Feature(LINESTRING, kind, LINE)

    fun point(kind: String) = Feature(POINT, kind, DOT)

    /** Land only: what the server answers outside the source data (Phase 1b, F1). */
    val LAND_ONLY: ByteArray by lazy { tile(layer("earth", polygon("earth"))) }

    /** Land and sea: an outside coast. */
    val LAND_AND_SEA: ByteArray by lazy { tile(layer("earth", polygon("earth")), layer("water", polygon("ocean"))) }

    /** Sea only. */
    val SEA_ONLY: ByteArray by lazy { tile(layer("water", polygon("ocean"))) }

    /** A covered tile: land, with a road, land use and a stream, as a sparse covered tile is. */
    val DETAIL: ByteArray by lazy {
        tile(
            layer("earth", polygon("earth")),
            layer("landuse", polygon("wood")),
            layer("roads", line("minor_road")),
            layer("water", line("stream")),
        )
    }

    fun tile(vararg layers: Layer): ByteArray {
        val out = ByteArrayOutputStream()
        for (layer in layers) out.field(3, encodeLayer(layer))
        return out.toByteArray()
    }

    private fun encodeLayer(layer: Layer): ByteArray {
        val out = ByteArrayOutputStream()
        out.key(15, VARINT)
        out.varint(2)
        out.field(1, layer.name.encodeToByteArray())
        val kinds = layer.features.map { it.kind }.distinct()
        for (feature in layer.features) out.field(2, encodeFeature(feature, kinds.indexOf(feature.kind).toLong()))
        out.field(3, "kind".encodeToByteArray())
        for (kind in kinds) {
            val value = ByteArrayOutputStream()
            value.field(1, kind.encodeToByteArray())
            out.field(4, value.toByteArray())
        }
        out.key(5, VARINT)
        out.varint(EXTENT)
        return out.toByteArray()
    }

    private fun encodeFeature(feature: Feature, kindValue: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.field(2, packed(listOf(0L, kindValue)))
        out.key(3, VARINT)
        out.varint(feature.type.toLong())
        out.field(4, packed(feature.geometry))
        return out.toByteArray()
    }

    private fun packed(values: List<Long>): ByteArray = ByteArrayOutputStream().also { out -> values.forEach { out.varint(it) } }.toByteArray()

    private fun ByteArrayOutputStream.field(number: Int, bytes: ByteArray) {
        key(number, LENGTH)
        varint(bytes.size.toLong())
        write(bytes)
    }

    private fun ByteArrayOutputStream.key(number: Int, wire: Int) = varint(((number shl 3) or wire).toLong())

    private fun ByteArrayOutputStream.varint(value: Long) {
        var v = value
        while (v >= 0x80) {
            write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        write(v.toInt())
    }

    private fun command(id: Int, count: Int): Long = ((id and 0x7) or (count shl 3)).toLong()

    private fun zigzag(n: Long): Long = (n shl 1) xor (n shr 63)

    private const val VARINT = 0
    private const val LENGTH = 2
    private const val POINT = 1
    private const val LINESTRING = 2
    private const val POLYGON = 3
    private const val EXTENT = 4096L

    private val SQUARE = listOf(
        command(1, 1), zigzag(0), zigzag(0),
        command(2, 3), zigzag(4096), zigzag(0), zigzag(0), zigzag(4096), zigzag(-4096), zigzag(0),
        command(7, 1),
    )
    private val LINE = listOf(command(1, 1), zigzag(100), zigzag(100), command(2, 1), zigzag(1000), zigzag(1000))
    private val DOT = listOf(command(1, 1), zigzag(2048), zigzag(2048))
}
