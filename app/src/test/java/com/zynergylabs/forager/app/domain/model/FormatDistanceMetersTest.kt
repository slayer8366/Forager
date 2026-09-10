package com.zynergylabs.forager.app.domain.model

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

    // ── formatDistanceWithAccuracy — location-accuracy dispatch, item 2 ─────────────────────
    // Every expected string below is worked by hand from the pinned conversions and the step
    // tables (1/5/10/50/100/500 m; 1/5/10/50/100/500/1000 ft), never from the formatter itself.

    @Test
    fun `inside the error circle - within the accuracy, in the display unit`() {
        // 5 m is 16.40 ft. The "0 ft" the owner saw on device, made honest.
        assertEquals("within 16 ft", formatDistanceWithAccuracy(0.3, 5f, DistanceUnit.MILES))
        assertEquals("within 5 m", formatDistanceWithAccuracy(0.3, 5f, DistanceUnit.KILOMETERS))
        // 12.5 m: whole metres round half up to 13; 41.01 ft rounds to 41.
        assertEquals("within 13 m", formatDistanceWithAccuracy(10.0, 12.5f, DistanceUnit.KILOMETERS))
        assertEquals("within 41 ft", formatDistanceWithAccuracy(10.0, 12.5f, DistanceUnit.MILES))
    }

    @Test
    fun `the within boundary - equal is inside, a hair over is outside and rounded to the accuracy's step`() {
        assertEquals("within 8 m", formatDistanceWithAccuracy(8.0, 8f, DistanceUnit.KILOMETERS))
        // 8.01 m > 8 m: step 10 (the first step >= 8); 0.801 rounds to 1 → 10 m.
        assertEquals("≈ 10 m", formatDistanceWithAccuracy(8.01, 8f, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `outside the circle - rounded to a step no finer than the accuracy, marked approximate`() {
        assertEquals("≈ 10 m", formatDistanceWithAccuracy(12.0, 8f, DistanceUnit.KILOMETERS))
        // 15 m accuracy → step 50; 340 / 50 = 6.8 → 7 → 350.
        assertEquals("≈ 350 m", formatDistanceWithAccuracy(340.0, 15f, DistanceUnit.KILOMETERS))
        // 2.5 m accuracy → step 5; 2.6 / 5 = 0.52 → 1 → 5.
        assertEquals("≈ 5 m", formatDistanceWithAccuracy(2.6, 2.5f, DistanceUnit.KILOMETERS))
        // 8 m is 26.25 ft → step 50 ft; 100 m is 328.08 ft; 328.08 / 50 = 6.56 → 7 → 350.
        assertEquals("≈ 350 ft", formatDistanceWithAccuracy(100.0, 8f, DistanceUnit.MILES))
    }

    @Test
    fun `a step no coarser than the natural resolution carries no marker`() {
        // 0.8 m accuracy → step 1 m: the same as today's whole-metre formatting, so no "≈".
        assertEquals("12 m", formatDistanceWithAccuracy(12.0, 0.8f, DistanceUnit.KILOMETERS))
        // 0.2 m is 0.66 ft → step 1 ft; 3 m is 9.84 ft → 10.
        assertEquals("10 ft", formatDistanceWithAccuracy(3.0, 0.2f, DistanceUnit.MILES))
    }

    @Test
    fun `no accuracy reported - exactly today's formatting, no marker`() {
        assertEquals("12 m", formatDistanceWithAccuracy(12.0, null, DistanceUnit.KILOMETERS))
        assertEquals("0 m", formatDistanceWithAccuracy(0.3, null, DistanceUnit.KILOMETERS))
        assertEquals("1 ft", formatDistanceWithAccuracy(0.3, null, DistanceUnit.MILES))
        assertEquals("1.2 km", formatDistanceWithAccuracy(1_234.0, null, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `at or above the kilometre and quarter-mile switch the one-decimal formatting is already coarser - unchanged`() {
        assertEquals("1.2 km", formatDistanceWithAccuracy(1_200.0, 8f, DistanceUnit.KILOMETERS))
        // 500 m is 0.31 mi, above the quarter-mile switch.
        assertEquals("0.3 mi", formatDistanceWithAccuracy(500.0, 30f, DistanceUnit.MILES))
    }
}
