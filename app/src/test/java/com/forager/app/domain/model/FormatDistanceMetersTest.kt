package com.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Navigation HUD stage one's unit-aware metre formatter — pinned conversions (1 mi = 1609.344 m, 1 m = 3.28084 ft). */
class FormatDistanceMetersTest {

    @Test
    fun `kilometres - metres below a kilometre, one decimal of kilometres from a kilometre on`() {
        assertEquals("412 m", formatDistanceMeters(412.4, DistanceUnit.KILOMETERS))
        assertEquals("999 m", formatDistanceMeters(999.4, DistanceUnit.KILOMETERS))
        assertEquals("1.2 km", formatDistanceMeters(1_234.0, DistanceUnit.KILOMETERS))
        assertEquals("12.3 km", formatDistanceMeters(12_345.0, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `miles - feet below a quarter mile, one decimal of miles from a quarter mile on`() {
        assertEquals("328 ft", formatDistanceMeters(100.0, DistanceUnit.MILES))
        assertEquals("1319 ft", formatDistanceMeters(402.0, DistanceUnit.MILES))
        assertEquals("0.3 mi", formatDistanceMeters(402.336, DistanceUnit.MILES))
        assertEquals("1.0 mi", formatDistanceMeters(1_609.344, DistanceUnit.MILES))
        assertEquals("5.0 mi", formatDistanceMeters(8_046.72, DistanceUnit.MILES))
    }
}
