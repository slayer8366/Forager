package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [viewerSampleSize] is the arithmetic behind [VIEWER_MAX_EDGE_PX] — the one place the viewer bounds
 * how much of a full-resolution photo it decodes. Pure integers, no bitmap, no Robolectric: the
 * decode call itself is a `BitmapFactory` pass-through that Robolectric's legacy shadow fakes (see
 * [DecodedPhotoTest]'s own doc comment), so the honest headless check is of the number handed to it.
 */
class PhotoViewerDecodeTest {

    @Test
    fun `a photo already inside the limit is not sampled`() {
        assertEquals(1, viewerSampleSize(width = 4032, height = 3024, maxEdgePx = 4096))
        assertEquals(1, viewerSampleSize(width = 4096, height = 4096, maxEdgePx = 4096))
        assertEquals(1, viewerSampleSize(width = 100, height = 100, maxEdgePx = 4096))
    }

    @Test
    fun `one pixel over the limit halves`() {
        assertEquals(2, viewerSampleSize(width = 4097, height = 100, maxEdgePx = 4096))
    }

    @Test
    fun `the longest edge is what is bounded, whichever axis it is on`() {
        // A 50 MP sensor's 8160×6120: halving brings 8160 to 4080, inside the limit.
        assertEquals(2, viewerSampleSize(width = 8160, height = 6120, maxEdgePx = 4096))
        assertEquals(2, viewerSampleSize(width = 6120, height = 8160, maxEdgePx = 4096))
    }

    /**
     * First written expecting 16385 to need 8: it does not, because the decoder floors — 16385 / 4
     * is 4096 wide, inside the limit — and the function's integer division matches that floor. The
     * expectation was the wrong half of that first run, so the case is kept with the decoder's
     * arithmetic and a genuinely over-the-edge width added beside it.
     */
    @Test
    fun `sample sizes climb by powers of two until the floored edge fits`() {
        assertEquals(4, viewerSampleSize(width = 16384, height = 100, maxEdgePx = 4096))
        assertEquals(4, viewerSampleSize(width = 16385, height = 100, maxEdgePx = 4096))
        assertEquals(8, viewerSampleSize(width = 16388, height = 100, maxEdgePx = 4096))
    }

    @Test
    fun `a non-positive limit is rejected rather than looping`() {
        assertThrows(IllegalArgumentException::class.java) { viewerSampleSize(width = 10, height = 10, maxEdgePx = 0) }
    }
}
