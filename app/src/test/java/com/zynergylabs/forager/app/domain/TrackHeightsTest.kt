package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * [trackHeights] while it is smoothed GPS height (RECORD -867, part 4; RECORD -864, the owner: "on a flat walk it
 * showed fluctuating values ... The phone was in my pocket"). Headless.
 */
class TrackHeightsTest {

    /** [heights] one point each, [stepMillis] apart, 0.0001° of latitude apart (about 11 m). */
    private fun points(heights: List<Double?>, stepMillis: Long = 5_000L) =
        heights.mapIndexed { i, h -> TrackPoint(lat = 45.0 + 0.0001 * i, lng = -122.0, altitude = h, accuracyMeters = 4f, timestampEpochMillis = 1_000_000L + i * stepMillis) }

    @Test
    fun `a lone spike on a flat walk is taken out`() {
        val flat = List(30) { 100.0 }.toMutableList<Double?>().also { it[12] = 70.0 }
        assertEquals(List(30) { 100.0 }, trackHeights(points(flat)))
    }

    @Test
    fun `a steady climb comes through exactly, ends included`() {
        val ramp = List(40) { 100.0 + 1.5 * it }
        val read = trackHeights(points(ramp))
        ramp.zip(read).forEach { (raw, smoothed) -> assertEquals(raw, smoothed!!, 1e-9) }
    }

    @Test
    fun `a point with no height stays without one, and nothing is bridged across it`() {
        val read = trackHeights(points(listOf(100.0, 140.0, null, 60.0, 60.0)))
        assertNull(read[2])
        assertEquals("a run of two is kept as recorded", listOf(100.0, 140.0), read.subList(0, 2))
        assertEquals(listOf(60.0, 60.0), read.subList(3, 5))
    }

    @Test
    fun `points a minute apart are too far apart to smooth, and keep their heights`() {
        val hill = listOf(100.0, 120.0, 150.0, 120.0, 100.0)
        assertEquals(hill, trackHeights(points(hill, stepMillis = 60_000L)))
    }

    /**
     * The owner's case in miniature: half an hour on the flat, a point every 5 s, GPS height jittering ±12 m around
     * 100 m with one sharp drop of 40 m. Raw, the chart spans 52 m; read (9 m by hand, a run's two end points kept as recorded), the jitter is averaged down and the drop is
     * gone. 12 m of jitter averaged over about 13 points leaves well under the 15 m span asserted here; the slow
     * wander a 60 s window cannot remove is the barometer's to fix.
     */
    @Test
    fun `jitter and a sharp drop on a flat walk are smoothed well below the raw spread`() {
        val raw = List(360) { i -> 100.0 + 12.0 * sin(i * 2.3) }.toMutableList<Double?>().also { it[200] = 60.0 }
        val read = trackHeights(points(raw)).map { it!! }
        val rawSpread = raw.maxOf { it!! } - raw.minOf { it!! }
        val readSpread = read.max() - read.min()
        assertTrue("raw spread $rawSpread", rawSpread > 50.0)
        assertTrue("read spread $readSpread", readSpread < 15.0)
        assertTrue("the drop is gone: lowest read ${read.min()}", read.min() > 85.0)
    }
}
