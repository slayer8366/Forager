package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.sin

/**
 * Barometer height (RECORD -864, "Use the barometer"; RECORD -867, part 5), through [trackHeights] and Climb as the app
 * reads them. Headless: points carry a pressure as the recording service stores it. Every figure here was computed by
 * hand from the rule before the test was run (a port of [barometricHeights], 2026-10-11).
 */
class BarometricHeightTest {

    private val stats = ComputeTrackStatisticsUseCase()

    /** A point every 5 s from t = 0, 0.0001° apart, with [gps] height and [hPa] pressure for point i. */
    private fun walk(count: Int, gps: (Int) -> Double?, hPa: (Int) -> Float?) = List(count) { i ->
        TrackPoint(lat = 45.0 + 0.0001 * i, lng = -122.0, altitude = gps(i), accuracyMeters = 4f, timestampEpochMillis = i * 5_000L, pressureHpa = hPa(i))
    }

    private fun pressureAt(heightMeters: Double): Float = (STANDARD_SEA_LEVEL_HPA * (1.0 - heightMeters / 44_330.0).pow(5.255)).toFloat()

    @Test
    fun `pressure turns into height by the standard atmosphere`() {
        assertEquals(0.0, pressureAltitudeMeters(1013.25), 1e-9)
        assertEquals(1000.1, pressureAltitudeMeters(898.75), 0.05)
    }

    /**
     * The owner's case (RECORD -864: "on a flat walk it showed fluctuating values ... The phone was in my pocket"): two
     * hours on the flat, GPS height wandering ±17 m over about 47 minutes with ±6 m of jitter and one 40 m drop, as the
     * Oct 7 chart did (302 to 414 ft); pressure steady. Read: under 10 m of spread (6.4 m by hand before RECORD -889's window, 6.2 m after) and no climb.
     */
    @Test
    fun `a flat walk in a pocket reads flat, however GPS height wanders`() {
        val points = walk(1_440, gps = { i -> if (i == 700) 70.0 else 109.0 + 17.0 * sin(i / 90.0) + 6.0 * sin(i * 2.3) }, hPa = { i -> (1000.0 + 0.02 * sin(i * 1.7)).toFloat() })
        val heights = trackHeights(points).map { it!! }
        assertTrue("spread ${heights.max() - heights.min()}", heights.max() - heights.min() < 10.0)
        assertEquals("climb", 0.0, stats(points).elevationGainMeters!!, 1e-9)
        assertEquals("descent", 0.0, stats(points).elevationLossMeters!!, 1e-9)
    }

    /** A squeeze in a pocket: one reading 0.3 hPa low (about 2.5 m). The median takes it out. */
    @Test
    fun `a pocket squeeze on one reading moves nothing`() {
        val points = walk(600, gps = { 100.0 }, hPa = { i -> if (i == 300) 999.7f else 1000.0f })
        val heights = trackHeights(points).map { it!! }
        assertEquals(0.0, heights.max() - heights.min(), 1e-6)
        assertEquals(0.0, stats(points).elevationGainMeters!!, 1e-9)
    }

    /**
     * Weather: pressure falling 1 hPa an hour for three hours (strong; about 8 m an hour of apparent rise) with GPS flat.
     * The anchor follows GPS within 10 m (8.0 m by hand) and the false climb stays under 10 m (8.0 m by hand before RECORD -889's window, 4.0 m after): the
     * price of an anchor slow enough to ignore GPS's own wander, stated on [BARO_ANCHOR_TIME_CONSTANT_MILLIS].
     */
    @Test
    fun `weather drift is followed back to GPS height`() {
        val points = walk(2_160, gps = { 100.0 }, hPa = { i -> (1000.0 - i * 5.0 / 3_600.0).toFloat() })
        val heights = trackHeights(points).map { it!! }
        assertTrue("furthest from GPS ${heights.maxOf { kotlin.math.abs(it - 100.0) }}", heights.all { kotlin.math.abs(it - 100.0) < 10.0 })
        assertTrue("climb ${stats(points).elevationGainMeters}", stats(points).elevationGainMeters!! < 10.0)
    }

    /** A real climb of 50 m over half an hour, then flat; GPS is the truth ±10 m. Climb is 50 m within 6 m (45.3 by hand, before and after RECORD -889's window). */
    @Test
    fun `a real climb is read from pressure`() {
        val truth = { i: Int -> 100.0 + minOf(i, 360) / 360.0 * 50.0 }
        val points = walk(720, gps = { i -> truth(i) + 10.0 * sin(i * 2.3) }, hPa = { i -> pressureAt(truth(i)) })
        assertEquals(50.0, stats(points).elevationGainMeters!!, 6.0)
        assertEquals(0.0, stats(points).elevationLossMeters!!, 1e-9)
    }

    /** A GPS height 60 m off does not move the anchor. */
    @Test
    fun `a wild GPS height does not move the barometer height`() {
        val points = walk(600, gps = { i -> if (i == 400) 160.0 else 100.0 }, hPa = { 1000.0f })
        val heights = trackHeights(points).map { it!! }
        assertEquals(0.0, heights.max() - heights.min(), 1e-6)
    }

    /** A step faster than a walker can climb (a car's vents, a door) is taken out, and so is its step back. */
    @Test
    fun `a jump faster than a walker climbs is not believed`() {
        // Ten readings in a row 3 hPa high (about 25 m in 5 s): longer than the median can take out.
        val points = walk(200, gps = { 100.0 }, hPa = { i -> if (i in 100 until 110) 997.0f else 1000.0f })
        val heights = trackHeights(points).map { it!! }
        assertEquals(0.0, heights.max() - heights.min(), 1e-6)
    }

    /**
     * RECORD -889, the walking step limit: pressure 1 hPa lower (about 8 m higher) for five minutes and then back, as a
     * phone in a car with the vents on might read; GPS flat. The window's median flips in one step of about 8 m in 5 s
     * (1.7 m/s), which the old 2 m/s limit let through (8.9 m of spread and 8.3 m of false climb, by a port of the code)
     * and the walking limit of 0.5 m/s takes out, with its step back.
     */
    @Test
    fun `a lasting step faster than a walker climbs is taken out, with its step back`() {
        val points = walk(300, gps = { 100.0 }, hPa = { i -> if (i in 120 until 180) 999.0f else 1000.0f })
        val heights = trackHeights(points).map { it!! }
        assertEquals(0.0, heights.max() - heights.min(), 1e-6)
    }

    /** Stairs climbed briskly, 36 m at 0.3 m/s, pass the walking step limit: Climb is 36 m within 2 m (35.4 by hand). */
    @Test
    fun `brisk stairs pass the walking step limit`() {
        val truth = { i: Int -> 100.0 + (i - 100).coerceIn(0, 24) * 1.5 }
        val points = walk(300, gps = truth, hPa = { i -> pressureAt(truth(i)) })
        assertEquals(36.0, stats(points).elevationGainMeters!!, 2.0)
    }

    @Test
    fun `a reading outside the operating range is not used`() {
        val points = walk(3, gps = { 100.0 }, hPa = { i -> if (i == 1) 1200.0f else 1000.0f })
        assertNull(barometricHeights(points)[1])
    }

    /** A walk with no pressure, or pressure on under half its points, reads smoothed GPS height, as every walk before the barometer does. */
    @Test
    fun `without pressure on most points the walk reads smoothed GPS height`() {
        val none = walk(30, gps = { i -> 100.0 + i }, hPa = { null })
        assertEquals(smoothedGpsHeights(none), trackHeights(none))
        val few = walk(30, gps = { i -> 100.0 + i }, hPa = { i -> if (i < 10) 1000.0f else null })
        assertEquals(smoothedGpsHeights(few), trackHeights(few))
    }

    @Test
    fun `a point takes the latest reading only while it is fresh`() {
        val reading = PressureReading(hPa = 1001.5f, elapsedRealtimeNanos = 10_000_000_000L)
        assertEquals(1001.5f, pressureForPoint(reading, 11_000_000_000L))
        assertEquals(1001.5f, pressureForPoint(reading, 12_000_000_000L))
        assertNull("older than 2 s", pressureForPoint(reading, 12_000_000_001L))
        assertNull("no reading", pressureForPoint(null, 12_000_000_000L))
    }
}
