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

    /**
     * RECORD -2024 (the owner: "Yes it should"), replacing this test's earlier claim, "unavailable, not the raw one": with no
     * sea-level height, the difference falls back to the two raw GPS heights, start minus here, as the strip falls back to
     * the fix's GPS height (RECORD -2022). Both ends raw, never one of each.
     */
    @Test
    fun `before the first conversion the difference is the raw start minus the raw height here`() {
        val info = ComputeReturnToStartUseCase()(here, start)
        assertEquals("40 - 47, both raw", -7.0, info.withLiveHeight(null).elevationDifferenceMeters)
    }

    @Test
    fun `on a phone that cannot convert the difference is the raw start minus the raw height here`() {
        val info = ComputeReturnToStartUseCase()(here, start)
        assertEquals("40 - 47, both raw", -7.0, info.withLiveHeight(ShownHeight.Unavailable).elevationDifferenceMeters)
    }

    @Test
    fun `with no raw height at either end there is still no difference, whatever the live height`() {
        val noStartHeight = ComputeReturnToStartUseCase()(here, start.copy(altitude = null))
        assertNull(noStartHeight.withLiveHeight(null).elevationDifferenceMeters)
        assertNull(noStartHeight.withLiveHeight(ShownHeight.Unavailable).elevationDifferenceMeters)
        assertNull(noStartHeight.withLiveHeight(ShownHeight.Known(meters = 65.0, aboveRawMeters = 21.6)).elevationDifferenceMeters)

        val noHeightHere = ComputeReturnToStartUseCase()(here.copy(altitude = null), start)
        assertNull(noHeightHere.withLiveHeight(null).elevationDifferenceMeters)
        assertNull(noHeightHere.withLiveHeight(ShownHeight.Unavailable).elevationDifferenceMeters)
    }
}
