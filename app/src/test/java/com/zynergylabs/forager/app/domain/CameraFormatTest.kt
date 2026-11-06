package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures.everythingSupported
import com.zynergylabs.forager.app.domain.CameraCapabilityFixtures.nothingSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format chip's rules (`domain/CameraFormat.kt`, dispatch 2026-10-15-01): which camera offers RAW and at what size,
 * what the menu says a shot weighs, and where a RAW shot gives way to its JPG for want of storage. Plain JUnit: no
 * Android in the rules.
 */
class CameraFormatTest {

    private fun withRaw(vararg sizes: PixelSize) = everythingSupported.copy(rawSizes = Probed.Supported(sizes.toList(), "test"))

    @Test
    fun `no probe yet, or RAW+JPEG not reported supported, offers no RAW`() {
        assertNull("no probe", rawCaptureSize(null))
        assertNull("RAW+JPEG unknown", rawCaptureSize(nothingSupported))
        assertNull("RAW+JPEG unsupported", rawCaptureSize(everythingSupported.copy(rawPlusJpegOutput = Probed.Unsupported("test"))))
        assertNull("no RAW sizes read", rawCaptureSize(everythingSupported.copy(rawSizes = Probed.Unknown("test"))))
        assertNull("an empty RAW size list", rawCaptureSize(withRaw()))
    }

    @Test
    fun `the S22 Ultra's reading binds at 4000x3000 and the S26 Ultra's at 4080x3060`() {
        // RECORD -996 and -998, verbatim sizes.
        assertEquals(PixelSize(4000, 3000), rawCaptureSize(withRaw(PixelSize(4000, 3000))))
        assertEquals("above the 4032x3024 bound, the closest higher", PixelSize(4080, 3060), rawCaptureSize(withRaw(PixelSize(4080, 3060))))
    }

    @Test
    fun `among several sizes, the largest at or under the bound, as the selector chooses`() {
        assertEquals(PixelSize(4000, 3000), rawCaptureSize(withRaw(PixelSize(2000, 1500), PixelSize(4000, 3000), PixelSize(4080, 3060))))
    }

    @Test
    fun `a RAW above the 12_6 MP operating limit is never offered, even when it is the only one`() {
        assertNull("50 MP only", rawCaptureSize(withRaw(PixelSize(8160, 6120))))
        assertEquals("the 12 MP one, not the 50 MP one", PixelSize(4080, 3060), rawCaptureSize(withRaw(PixelSize(8160, 6120), PixelSize(4080, 3060))))
        assertEquals("the limit itself is admitted", 12_600_000L, RAW_MAX_PIXELS)
    }

    @Test
    fun `the menu's sizes come from the bound size and the estimates, in whole megabytes`() {
        val s22 = PixelSize(4000, 3000)
        assertEquals("a third of a byte a pixel", 3_960_000.0, approximateShotBytes(CameraFormat.Jpg, s22).toDouble(), 2.0)
        assertEquals("the JPG and the DNG together", 18_360_000.0, approximateShotBytes(CameraFormat.Raw, s22).toDouble(), 2.0)
        assertEquals("~4 MB", approximateSizeLabel(approximateShotBytes(CameraFormat.Jpg, s22)))
        assertEquals("~18 MB", approximateSizeLabel(approximateShotBytes(CameraFormat.Raw, s22)))
        assertEquals("never zero", "~1 MB", approximateSizeLabel(10))
    }

    @Test
    fun `a RAW shot writes its DNG only with 500 MB plus its own worst case free, to the byte`() {
        val worst = worstCaseRawShotBytes(4000, 3000)
        assertEquals("both files at their largest, twice for the copy into photos/", 2 * (12_000_000L * 4 + 1_000_000 + 18_000_000), worst)
        val floor = LOW_STORAGE_FLOOR_BYTES + worst
        assertTrue("exactly at the floor", rawShotFits(floor, 4000, 3000))
        assertFalse("one byte under", rawShotFits(floor - 1, 4000, 3000))
        assertFalse("a nearly full phone", rawShotFits(100_000_000L, 4000, 3000))
    }
}
