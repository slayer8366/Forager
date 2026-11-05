package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.routing.DropOffTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Basemap patch 0004's fence mark, defined once (dispatch 2026-11-04-09, B1 P7): the reader, the layered read and the
 * copy's fetch all ask [BarrierMark]. Read on the drop-off fixture's two builds of its one tile.
 */
class BarrierMarkTest {

    @Test
    fun `the tile as the 11-01 build writes it carries the mark, and the same tile from before it does not`() {
        assertEquals(TileMark.Marked, BarrierMark.of(DropOffTestData.resource(DropOffTestData.TILE).readBytes()))
        assertEquals(TileMark.Unmarked, BarrierMark.of(DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()))
    }

    @Test
    fun `bytes that are not a vector tile are undecodable, never read as unmarked`() {
        val mark = BarrierMark.of(byteArrayOf(0x1a, 0x7f, 0x00))
        assertTrue("$mark", mark is TileMark.Undecodable)
    }

    @Test
    fun `a decoded tile carries the mark exactly when it has a barriers layer`() {
        assertTrue(BarrierMark.carriedBy(MvtDecoder.decode(DropOffTestData.resource(DropOffTestData.TILE).readBytes())))
        assertEquals(false, BarrierMark.carriedBy(MvtDecoder.decode(DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes())))
    }
}
