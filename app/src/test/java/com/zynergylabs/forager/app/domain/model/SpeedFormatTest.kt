package com.zynergylabs.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The track sheet's Moving speed figure (dispatch 2026-09-28-677). Literals worked by hand. */
class SpeedFormatTest {

    @Test
    fun `miles per hour, one decimal`() {
        // 0.890 m/s, the 2026-09-07 walk's average: 0.890 x 3600 / 1609.344 = 1.991 mph.
        assertEquals("2.0 mph", formatSpeed(0.890, DistanceUnit.MILES))
        // 1 m/s = 2.237 mph.
        assertEquals("2.2 mph", formatSpeed(1.0, DistanceUnit.MILES))
    }

    @Test
    fun `kilometres per hour, one decimal`() {
        // 1 m/s = 3.6 km/h; 0.890 m/s = 3.204 km/h.
        assertEquals("3.6 km/h", formatSpeed(1.0, DistanceUnit.KILOMETERS))
        assertEquals("3.2 km/h", formatSpeed(0.890, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `standing still is zero, not blank`() {
        assertEquals("0.0 mph", formatSpeed(0.0, DistanceUnit.MILES))
        assertEquals("0.0 km/h", formatSpeed(0.0, DistanceUnit.KILOMETERS))
    }
}
