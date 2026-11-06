package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.mapdata.TestMvt.layer
import com.zynergylabs.forager.app.domain.mapdata.TestMvt.line
import com.zynergylabs.forager.app.domain.mapdata.TestMvt.point
import com.zynergylabs.forager.app.domain.mapdata.TestMvt.polygon
import com.zynergylabs.forager.app.domain.mapdata.TestMvt.tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known bug 14 (dispatch 2026-11-02-02, A7, RECORD -2150): one tile's answer read as no detail, sea, detail or unread. The tiles
 * are [TestMvt]'s, made to the layers and kinds the live server answered with.
 */
class TileDetailReaderTest {

    @Test
    fun `positive control - the test tiles decode, with the layers they were made with`() {
        assertEquals(listOf("earth"), MvtDecoder.decode(TestMvt.LAND_ONLY).layers.map { it.name })
        assertEquals(listOf("earth", "water"), MvtDecoder.decode(TestMvt.LAND_AND_SEA).layers.map { it.name })
        assertEquals("ocean", MvtDecoder.decode(TestMvt.SEA_ONLY).layer("water")!!.features.single().string("kind"))
    }

    @Test
    fun `land only has no detail`() {
        assertEquals(TileDetail.NoDetail, TileDetailReader.read(TestMvt.LAND_ONLY))
    }

    @Test
    fun `land and sea, an outside coast, has no detail`() {
        assertEquals(TileDetail.NoDetail, TileDetailReader.read(TestMvt.LAND_AND_SEA))
    }

    @Test
    fun `sea only counts neither way`() {
        assertEquals(TileDetail.Sea, TileDetailReader.read(TestMvt.SEA_ONLY))
    }

    @Test
    fun `a covered tile has detail`() {
        assertEquals(TileDetail.Detail, TileDetailReader.read(TestMvt.DETAIL))
    }

    @Test
    fun `any one of the map's own layers beside the land fill is detail`() {
        val layers = listOf(
            layer("roads", line("path")),
            layer("landuse", polygon("wetland")),
            layer("water", polygon("lake")),
            layer("water", line("stream")),
            layer("buildings", polygon("building")),
            layer("places", point("locality")),
            layer("boundaries", line("country")),
            layer("pois", point("park")),
        )
        for (extra in layers) {
            val kind = extra.features.single().kind
            assertEquals("earth with ${extra.name} ($kind)", TileDetail.Detail, TileDetailReader.read(tile(layer("earth", polygon("earth")), extra)))
            assertEquals("sea with ${extra.name} ($kind)", TileDetail.Detail, TileDetailReader.read(tile(layer("water", polygon("ocean")), extra)))
        }
    }

    @Test
    fun `bytes that are not a vector tile are unread, never either`() {
        val read = TileDetailReader.read(byteArrayOf(0x1A, 0x7F, 0x01))
        assertTrue("was $read", read is TileDetail.Unread && read.reason.startsWith("not a vector tile"))
    }

    @Test
    fun `a tile with no features is unread`() {
        assertEquals(TileDetail.Unread("a tile with no features"), TileDetailReader.read(ByteArray(0)))
        assertEquals(TileDetail.Unread("a tile with no features"), TileDetailReader.read(tile(layer("roads"))))
    }
}
