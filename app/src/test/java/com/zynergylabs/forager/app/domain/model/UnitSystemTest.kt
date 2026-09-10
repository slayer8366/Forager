package com.zynergylabs.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [UnitSystem]'s derivation of [DistanceUnit] and [formatRainfall]'s two forms — return-estimate
 * dispatch, Item 4. Every expected string is a literal worked by hand from the millimetre input
 * (25.4 mm to the inch), never from the formatter: 12.4 / 25.4 = 0.488 → "0.5 in"; 3.2 / 25.4 =
 * 0.126 → "0.1 in"; 38.1 / 25.4 = 1.5 exactly; 100 / 25.4 = 3.937 → "3.9 in"; 1.0 / 25.4 = 0.039,
 * below the 0.05 floor → trace.
 */
class UnitSystemTest {

    @Test
    fun `each system derives exactly one distance unit, and maps back to itself`() {
        assertEquals(DistanceUnit.KILOMETERS, UnitSystem.METRIC.distanceUnit)
        assertEquals(DistanceUnit.MILES, UnitSystem.IMPERIAL.distanceUnit)
        assertEquals(UnitSystem.METRIC, UnitSystem.forDistanceUnit(DistanceUnit.KILOMETERS))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.forDistanceUnit(DistanceUnit.MILES))
    }

    @Test
    fun `imperial rainfall reads in tenths of an inch`() {
        assertEquals("0.5 in", formatRainfall(12.4, UnitSystem.IMPERIAL))
        assertEquals("0.1 in", formatRainfall(3.2, UnitSystem.IMPERIAL))
        assertEquals("1.5 in", formatRainfall(38.1, UnitSystem.IMPERIAL))
        assertEquals("1.0 in", formatRainfall(25.4, UnitSystem.IMPERIAL))
        assertEquals("3.9 in", formatRainfall(100.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `a trace of rain never prints as zero inches, and no rain never prints as a trace`() {
        // Owner ruling: a printed zero after real rain would be a lie.
        assertEquals("< 0.1 in", formatRainfall(1.0, UnitSystem.IMPERIAL))
        assertEquals("< 0.1 in", formatRainfall(0.5, UnitSystem.IMPERIAL))
        assertEquals("< 0.1 in", formatRainfall(0.01, UnitSystem.IMPERIAL))
        assertEquals("0.0 in", formatRainfall(0.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `the imperial metric-decimals argument is ignored, since inches carry their own precision`() {
        assertEquals("0.5 in", formatRainfall(12.4, UnitSystem.IMPERIAL, metricDecimals = 0))
    }

    @Test
    fun `metric rainfall keeps each site's existing precision and no-space form, byte for byte`() {
        assertEquals("12.4mm", formatRainfall(12.4, UnitSystem.METRIC))
        assertEquals("3.2mm", formatRainfall(3.2, UnitSystem.METRIC))
        assertEquals("12mm", formatRainfall(12.4, UnitSystem.METRIC, metricDecimals = 0))
        assertEquals("40mm", formatRainfall(39.9, UnitSystem.METRIC, metricDecimals = 0))
        assertEquals("0.0mm", formatRainfall(0.0, UnitSystem.METRIC))
    }
}
