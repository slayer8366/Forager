package com.zynergylabs.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Dispatch 2026-09-28-549 (units-follow) and its Amendment 1 (RECORD -557): elevation, its change,
 * position accuracy and soil temperature follow the Units setting. Every figure below is worked by
 * hand from metres ÷ 0.3048 and °F = °C × 9/5 + 32. Metric cases pin today's exact strings.
 */
class UnitSystemFormatTest {

    @Test
    fun `a whole length reads in whole feet under imperial`() {
        // 114 / 0.3048 = 374.02
        assertEquals("374 ft", formatWholeLength(114.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `a whole length reads in whole metres under metric, as before`() {
        assertEquals("114 m", formatWholeLength(114.0, UnitSystem.METRIC))
        assertEquals("115 m", formatWholeLength(114.5, UnitSystem.METRIC))
    }

    @Test
    fun `an elevation change keeps its sign in feet under imperial`() {
        // 10 / 0.3048 = 32.81; -3.66 / 0.3048 = -12.01
        assertEquals("+33 ft", formatElevationChange(10.0, UnitSystem.IMPERIAL))
        assertEquals("-12 ft", formatElevationChange(-3.66, UnitSystem.IMPERIAL))
        assertEquals("+0 ft", formatElevationChange(0.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `an elevation change reads as before under metric`() {
        assertEquals("+10 m", formatElevationChange(10.0, UnitSystem.METRIC))
        assertEquals("-4 m", formatElevationChange(-3.66, UnitSystem.METRIC))
        assertEquals("+0 m", formatElevationChange(0.0, UnitSystem.METRIC))
    }

    @Test
    fun `soil temperature reads in Fahrenheit to one decimal under imperial`() {
        // 11.3 × 9/5 + 32 = 52.34
        assertEquals("52.3°F", formatSoilTemperature(11.3, UnitSystem.IMPERIAL))
    }

    @Test
    fun `soil temperature reads in Celsius as before under metric`() {
        assertEquals("11.3°C", formatSoilTemperature(11.3, UnitSystem.METRIC))
    }

    /** Amendment 1 (3): a point in both systems, even on a phone set to a comma language. */
    @Test
    fun `soil temperature keeps a decimal point on a comma-locale phone, in both systems`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("11.3°C", formatSoilTemperature(11.3, UnitSystem.METRIC))
            assertEquals("52.3°F", formatSoilTemperature(11.3, UnitSystem.IMPERIAL))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
