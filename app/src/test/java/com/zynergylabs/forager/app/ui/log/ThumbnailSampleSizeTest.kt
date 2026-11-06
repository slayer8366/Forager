package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Dispatch 2026-09-28-658 (photo size): a thumbnail is decoded to the cell it is shown in. The
 * expected values are worked by hand from the rule (the photo's shorter edge stays at least the
 * cell's longer edge; the longer edge never past the viewer's 4096), not read back from the code.
 */
class ThumbnailSampleSizeTest {

    @Test
    fun `a 12 MP photo in a 300 px cell decodes at an eighth, 504 by 378`() {
        // 3024/8 = 378 >= 300, 3024/16 = 189 < 300.
        assertEquals(8, thumbnailSampleSize(width = 4032, height = 3024, cellEdgePx = 300))
    }

    @Test
    fun `the same photo in a 1080 px cell decodes at a half, 2016 by 1512`() {
        // 3024/2 = 1512 >= 1080, 3024/4 = 756 < 1080.
        assertEquals(2, thumbnailSampleSize(width = 4032, height = 3024, cellEdgePx = 1080))
    }

    @Test
    fun `a portrait photo is sampled the same as its landscape twin`() {
        assertEquals(
            thumbnailSampleSize(width = 4032, height = 3024, cellEdgePx = 300),
            thumbnailSampleSize(width = 3024, height = 4032, cellEdgePx = 300),
        )
    }

    @Test
    fun `a 200 MP import in a 300 px cell decodes at a thirty-second`() {
        // 12240/32 = 382 >= 300, 12240/64 = 191 < 300. Before, a fixed 4 gave 4080 by 3060 per cell.
        assertEquals(32, thumbnailSampleSize(width = 16_320, height = 12_240, cellEdgePx = 300))
    }

    @Test
    fun `a photo smaller than its cell is not sampled at all`() {
        assertEquals(1, thumbnailSampleSize(width = 40, height = 20, cellEdgePx = 300))
    }

    @Test
    fun `a long panorama is still held to the viewer's 4096 on its long edge`() {
        // The short edge allows 2 (1000/2 = 500 >= 300), but 20000/2 and 20000/4 are past 4096; 20000/8 = 2500.
        assertEquals(8, thumbnailSampleSize(width = 20_000, height = 1_000, cellEdgePx = 300))
    }

    @Test
    fun `a cell of no size is refused`() {
        assertThrows(IllegalArgumentException::class.java) { thumbnailSampleSize(width = 4032, height = 3024, cellEdgePx = 0) }
    }
}
