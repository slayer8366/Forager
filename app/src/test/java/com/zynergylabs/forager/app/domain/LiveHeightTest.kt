package com.zynergylabs.forager.app.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-10-28-13: [LiveHeight], the barometer's anchoring run forward. Pure, on made-up walks only (never a
 * stored walk): fixes once a second, pressure once a second on the sensor's own clock.
 */
class LiveHeightTest {

    private fun known(meters: Double) = SeaLevelHeight.Known(meters, aboveRawMeters = 21.6)

    private fun reading(second: Long, hPa: Double) = PressureReading(hPa.toFloat(), second * 1_000_000_000L)

    private fun meters(shown: ShownHeight?): Double = (shown as ShownHeight.Known).meters

    /**
     * A flat walk: GPS height wandering by up to 10 m either way (a fixed, made-up sequence shaped like the 2026-10-28
     * walk's spread, the altitude report section 3), steady pressure. After the first minute's anchor, the shown
     * height steps by well under a metre a second where the GPS steps by up to 16 m.
     */
    @Test
    fun `on a flat walk with steady pressure the barometer steadies a wandering GPS height`() {
        val height = LiveHeight()
        val wander = listOf(0.0, 6.0, -4.0, 9.0, -10.0, 3.0, -7.0, 10.0, -2.0, 5.0, -9.0, 8.0)
        val shown = mutableListOf<Double>()
        val gps = mutableListOf<Double>()
        for (second in 0L until 600L) {
            height.onPressure(reading(second, 1000.0))
            val g = 72.0 + wander[(second % wander.size).toInt()]
            gps += g
            shown += meters(height.onFix(known(g), T0 + second * 1_000L))
        }
        val gpsLargestStep = gps.zipWithNext { a, b -> abs(b - a) }.max()
        val laterSteps = shown.drop(61).zipWithNext { a, b -> abs(b - a) }
        assertTrue("positive control: GPS alone steps by $gpsLargestStep m", gpsLargestStep >= 15.0)
        assertTrue("after the first minute the shown height steps by under 0.1 m (largest ${laterSteps.max()})", laterSteps.max() < 0.1)
        assertEquals("and it sits near the walk's true 72 m", 72.0, shown.last(), 3.0)
    }

    /** A 0.12 hPa fall with steady GPS is about 1 m up, and the shown height follows it within the window. */
    @Test
    fun `a pressure fall with steady GPS raises the shown height by about a metre`() {
        val height = LiveHeight()
        for (second in 0L..120L) {
            height.onPressure(reading(second, 1000.0))
            height.onFix(known(72.0), T0 + second * 1_000L)
        }
        val before = meters(height.onFix(known(72.0), T0 + 121_000L))
        var after: ShownHeight? = null
        for (second in 122L..182L) after = height.onPressure(reading(second, 999.88))
        val rise = meters(after) - before
        val expected = pressureAltitudeMeters(999.88) - pressureAltitudeMeters(1000.0)
        assertEquals("the barometer's own 1 m", expected, rise, 0.01)
    }

    @Test
    fun `with no barometer each fix's sea-level height is shown as it comes`() {
        val height = LiveHeight()
        height.onNoBarometer()
        listOf(72.0, 81.0, 65.0).forEachIndexed { i, g ->
            assertEquals(g, meters(height.onFix(known(g), T0 + i * 1_000L)), 0.0)
        }
    }

    /** "Follow the barometer (Recommended)": with no more fixes the anchor holds and the height moves on pressure. */
    @Test
    fun `when GPS drops out the shown height follows the barometer`() {
        val height = LiveHeight()
        for (second in 0L..90L) {
            height.onPressure(reading(second, 1000.0))
            height.onFix(known(72.0), T0 + second * 1_000L)
        }
        var shown: ShownHeight? = null
        for (second in 91L..200L) shown = height.onPressure(reading(second, 999.0))
        assertEquals(72.0 + pressureAltitudeMeters(999.0) - pressureAltitudeMeters(1000.0), meters(shown), 0.01)

        // A fix with no height at all is GPS gone too.
        assertEquals(meters(shown), meters(height.onFix(SeaLevelHeight.NoHeight, T0 + 201_000L)), 0.0)
    }

    @Test
    fun `a phone that cannot convert shows unavailable even with a barometer`() {
        val height = LiveHeight()
        height.onPressure(reading(0, 1000.0))
        assertEquals(ShownHeight.Unavailable, height.onFix(SeaLevelHeight.Unsupported, T0))
        assertEquals(ShownHeight.Unavailable, height.onPressure(reading(1, 999.0)))
    }

    @Test
    fun `nothing is shown before a fix, and a fix with no height and no anchor is unavailable`() {
        val height = LiveHeight()
        assertEquals(null, height.onPressure(reading(0, 1000.0)))
        assertEquals(ShownHeight.Unavailable, height.onFix(SeaLevelHeight.NoHeight, T0))
    }

    /** BarometricHeight's 30 m rule: a GPS height that far off does not move the anchor. */
    @Test
    fun `a GPS height more than 30 m off does not move the anchor`() {
        val height = LiveHeight()
        for (second in 0L..90L) {
            height.onPressure(reading(second, 1000.0))
            height.onFix(known(72.0), T0 + second * 1_000L)
        }
        val shown = meters(height.onFix(known(72.0 + 31.0), T0 + 30 * 60_000L))
        assertEquals(72.0, shown, 1e-9)
    }

    private companion object {
        const val T0 = 1_700_000_000_000L
    }
}
