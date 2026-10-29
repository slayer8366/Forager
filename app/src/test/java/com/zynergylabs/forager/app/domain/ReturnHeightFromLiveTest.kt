package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.withLiveHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch 2026-10-28-13 ("Yes, same as the strip (Recommended)"): the Return control's spoken height difference is the
 * start's stored height, put on sea level by the live conversion's offset, minus the strip's steadied height, not two
 * raw fixes' difference. Made-up heights.
 */
class ReturnHeightFromLiveTest {

    private val start = TrackPoint(45.52, -122.68, 40.0, 5f, 1_790_000_000_000L)
    private val here = TrackPoint(45.53, -122.68, 47.0, 5f, 1_790_000_600_000L)

    @Test
    fun `the difference is the start above sea level minus the strip's height`() {
        val info = ComputeReturnToStartUseCase()(here, start)
        assertEquals("positive control: as computed, two raw heights", -7.0, info.elevationDifferenceMeters!!, 1e-9)

        // The strip shows 65.0 (the barometer has steadied a raw 47 m to 43.4 m, plus 21.6 to sea level).
        val heard = info.withLiveHeight(ShownHeight.Known(meters = 65.0, aboveRawMeters = 21.6))
        assertEquals("(40 + 21.6) - 65", -3.4, heard.elevationDifferenceMeters!!, 1e-9)
    }

    @Test
    fun `with no live height to show the difference is unavailable, not the raw one`() {
        val info = ComputeReturnToStartUseCase()(here, start)
        assertNull(info.withLiveHeight(ShownHeight.Unavailable).elevationDifferenceMeters)
        assertNull(info.withLiveHeight(null).elevationDifferenceMeters)
    }
}
