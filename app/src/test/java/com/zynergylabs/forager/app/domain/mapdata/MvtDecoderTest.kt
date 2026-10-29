package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.routing.DropOffTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MVT decoder and the reader that places its features, on the drop-off fixture's tile (way-to dispatch 2026-10-28,
 * PR 2). The tile was written by `make-dropoff-fixture.py` from the same drawing as `points.json`, so every position
 * asserted here is one the drawing put there, not one read back from this decoder.
 */
class MvtDecoderTest {

    private val tileId = TileId(15, 9875, 12393)
    private val bytes = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val mvt = MvtDecoder.decode(bytes)
    private val features = TileFeatureReader.read(tileId, mvt)

    @Test
    fun `the tile's layers and their feature counts are the drawing's`() {
        assertEquals(listOf("landuse", "water", "roads", "buildings", "barriers"), mvt.layers.map { it.name })
        assertEquals(listOf(2, 3, 6, 8, 2), mvt.layers.map { it.features.size }) // barriers: fence B and the mark
        assertTrue(mvt.layers.all { it.extent == 4096 })
    }

    @Test
    fun `fields come back with their types - strings and integers`() {
        val address = mvt.layer("buildings")!!.features.first { it.string("addr_housenumber") == "12" }
        assertEquals(MvtGeometryType.POINT, address.type)
        assertEquals("Front Road", address.string("addr_street"))
        val building = mvt.layer("buildings")!!.features.first { it.type == MvtGeometryType.POLYGON }
        assertEquals(400L, building.fields["sort_rank"])
    }

    @Test
    fun `each address point lands on its house's centre, with its street`() {
        for ((pin, street, number) in listOf(Triple("pin_a", "Front Road", "12"), Triple("pin_b", "Front Road", "40"), Triple("pin_c", "West Road", "7"))) {
            val address = features.addresses.single { it.housenumber == number && it.street == street }
            val off = GeoDistance.metersBetween(address.at, DropOffTestData.point(pin))
            assertEquals("$pin's address point is $off m from the pin", 0.0, off, 0.5)
        }
    }

    @Test
    fun `a house outline is a closed ring of its four corners, 11_3 m from its centre`() {
        assertEquals(4, features.buildings.size)
        val houseA = features.buildings.single { b -> b.rings.single().all { GeoDistance.metersBetween(it, DropOffTestData.point("pin_a")) < 15 } }
        val ring = houseA.rings.single()
        assertEquals(5, ring.size)
        assertEquals(ring.first(), ring.last())
        ring.dropLast(1).forEach { assertEquals(11.31, GeoDistance.metersBetween(it, DropOffTestData.point("pin_a")), 0.5) }
    }

    @Test
    fun `water keeps tunnel, so the culvert is told from the streams`() {
        assertEquals(3, features.water.size)
        assertEquals(listOf(null, "culvert", null), features.water.map { it.tunnel })
        assertTrue(features.water.all { it.kind == "stream" && !it.isArea })
    }

    @Test
    fun `the fence is a barrier line 46 m either side of house B's centre`() {
        val fence = features.barriers.single()
        assertEquals("fence", fence.kind)
        val line = fence.lines.single()
        assertEquals(2, line.size)
        line.forEach { assertEquals(46.1, GeoDistance.metersBetween(it, DropOffTestData.point("pin_b")), 0.5) }
    }

    @Test
    fun `land use areas keep their kinds`() {
        assertEquals(listOf("residential", "forest"), features.landuse.map { it.kind })
    }

    @Test
    fun `a tile cut short fails with what was wrong, never a partial tile`() {
        val e = assertThrows(MvtFormatException::class.java) { MvtDecoder.decode(bytes.copyOf(bytes.size - 7)) }
        assertTrue(e.message, e.message!!.contains("runs past the end"))
    }

    @Test
    fun `tileOf gives the tile the fixture was drawn in`() {
        assertEquals(tileId, TileFeatureReader.tileOf(DropOffTestData.point("pin_a"), 15))
    }
}
