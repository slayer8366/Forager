package com.zynergylabs.forager.app.data.mapdetail

import com.zynergylabs.forager.app.domain.mapdata.TileDetail
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetailLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Known bug 14 (dispatch 2026-11-02-02, call C3, RECORD -2150): the session's tile answers, up to 512, the least recently read dropped first. */
class TileDetailCacheTest {

    private fun tile(n: Int) = TileId(11, n, n)

    @Test
    fun `512 answers are kept, as the limit says`() {
        assertEquals(512, TrailMapDetailLimits.MAX_CACHED_ANSWERS)
        val cache = TileDetailCache(TrailMapDetailLimits.MAX_CACHED_ANSWERS)
        for (n in 0 until 600) cache.put(tile(n), TileDetail.Detail)
        assertEquals(512, cache.size)
        assertNull("the oldest went first", cache.get(tile(0)))
        assertEquals(TileDetail.Detail, cache.get(tile(599)))
    }

    @Test
    fun `the least recently read answer is the one dropped`() {
        val cache = TileDetailCache(3)
        cache.put(tile(1), TileDetail.Detail)
        cache.put(tile(2), TileDetail.NoDetail)
        cache.put(tile(3), TileDetail.Sea)
        cache.get(tile(1))
        cache.put(tile(4), TileDetail.Detail)
        assertEquals(TileDetail.Detail, cache.get(tile(1)))
        assertNull(cache.get(tile(2)))
        assertEquals(3, cache.size)
    }

    @Test
    fun `an unread answer is never kept`() {
        val cache = TileDetailCache(3)
        cache.put(tile(1), TileDetail.Unread("HTTP 503"))
        assertNull(cache.get(tile(1)))
        assertEquals(0, cache.size)
    }
}
