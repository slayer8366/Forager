package com.zynergylabs.forager.app.diagnostics.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The switch detector and the watch summary (dispatch 2026-10-15-12: "Pure tests for ... the switch detector (from and
 * to, rate limiting) ... the summary"). The planner's table of which sensor is active when is built from the switch lines
 * and the summary, so a switch that goes unreported, or a rate limit that lets a 30-frame second through, breaks it.
 */
class SwitchDetectorTest {

    private val frameNanos = 33_333_333L // 30 frames a second

    private fun reading(
        arrivalNanos: Long,
        id: String?,
        still: Boolean = false,
        zoom: Float? = 1f,
        focus: Float? = 2f,
        exposure: Long? = 10_000_000L,
        iso: Int? = 100,
    ) = FrameReading(
        arrivalNanos = arrivalNanos,
        sensorTimestampNs = arrivalNanos - 5_000_000L,
        still = still,
        captureIntent = if (still) 2 else 1,
        activePhysicalId = id,
        zoomRatio = zoom,
        focusDistanceDiopters = focus,
        afState = 2,
        exposureTimeNs = exposure,
        iso = iso,
        aperture = 1.8f,
        brightnessKey = null,
        brightnessValue = null,
    )

    @Test
    fun `thirty preview frames in a second log five frame lines, spaced at least 200 ms apart`() {
        val detector = SwitchDetector()
        val logged = (0 until 30).filter { i -> detector.observe(reading(i * frameNanos, "5")).logFrame }

        assertEquals("frames logged out of a 30-frame second: $logged", listOf(0, 7, 14, 21, 28), logged)
        val spacings = logged.zipWithNext { a, b -> (b - a) * frameNanos }
        assertTrue("every gap is at least 200 ms: $spacings", spacings.all { it >= 200_000_000L })
        val summary = detector.summary()
        assertEquals(30, summary.framesSeen)
        assertEquals(5, summary.previewFramesLogged)
        assertEquals(25, summary.previewFramesSkipped)
    }

    @Test
    fun `a frame exactly 200 ms after the last one logged is logged`() {
        val detector = SwitchDetector()
        assertTrue(detector.observe(reading(0L, "5")).logFrame)
        assertFalse(detector.observe(reading(199_999_999L, "5")).logFrame)
        assertTrue(detector.observe(reading(200_000_000L, "5")).logFrame)
    }

    @Test
    fun `every still is logged, inside the preview limit, and does not use up the preview's allowance`() {
        val detector = SwitchDetector()
        assertTrue(detector.observe(reading(0L, "5")).logFrame)
        assertTrue("a still 10 ms later is logged", detector.observe(reading(10_000_000L, "5", still = true)).logFrame)
        assertTrue("so is the next one", detector.observe(reading(20_000_000L, "5", still = true)).logFrame)
        assertFalse("a preview frame 30 ms after the first is still limited", detector.observe(reading(30_000_000L, "5")).logFrame)
        assertTrue("and the preview's next slot is 200 ms after its own last line", detector.observe(reading(200_000_000L, "5")).logFrame)
        val summary = detector.summary()
        assertEquals(2, summary.stills)
        assertEquals(2, summary.previewFramesLogged)
        assertEquals(1, summary.previewFramesSkipped)
    }

    @Test
    fun `the first frame that names a camera is not a switch`() {
        val detector = SwitchDetector()
        assertNull(detector.observe(reading(0L, "5")).switch)
    }

    @Test
    fun `a change of active camera is reported from and to, with what changed`() {
        val detector = SwitchDetector()
        detector.observe(reading(0L, "5", zoom = 1f, focus = 2f, exposure = 10_000_000L, iso = 100))
        assertNull(detector.observe(reading(frameNanos, "5", zoom = 1f, focus = 2f, exposure = 10_000_000L, iso = 100)).switch)

        val switched = detector.observe(reading(2 * frameNanos, "2", zoom = 0.6f, focus = 2f, exposure = 10_000_000L, iso = 200)).switch

        assertNotNull(switched)
        assertEquals("5", switched!!.from)
        assertEquals("2", switched.to)
        assertEquals(listOf("zoom", "exposure"), switched.changed)
        assertEquals(1f, switched.before.zoomRatio)
        assertEquals(0.6f, switched.after.zoomRatio)
    }

    @Test
    fun `a switch with only the focus distance changed names focus alone`() {
        val detector = SwitchDetector()
        detector.observe(reading(0L, "5", focus = 2f))
        val switched = detector.observe(reading(frameNanos, "6", focus = 10f)).switch
        assertEquals(listOf("focus"), switched!!.changed)
    }

    @Test
    fun `a switch is reported even on a frame the rate limit does not log`() {
        val detector = SwitchDetector()
        detector.observe(reading(0L, "5"))
        val observation = detector.observe(reading(frameNanos, "6"))
        assertFalse("the frame itself is inside the limit", observation.logFrame)
        assertEquals("6", observation.switch?.to)
    }

    @Test
    fun `a frame that names no camera neither switches nor resets the comparison`() {
        val detector = SwitchDetector()
        detector.observe(reading(0L, "5"))
        assertNull(detector.observe(reading(frameNanos, null)).switch)
        val switched = detector.observe(reading(2 * frameNanos, "2")).switch
        assertEquals("5", switched?.from)
        assertEquals("2", switched?.to)
        assertNull("back to the camera the null frame sat between is not a switch from null", detector.observe(reading(3 * frameNanos, "2")).switch)
    }

    @Test
    fun `the summary counts frames per camera in the order first seen, switches, ids missing, zoom and span`() {
        val detector = SwitchDetector()
        val ids = listOf("5", "5", null, "2", "2", "2", "5", "6")
        val zooms = listOf(1f, 1f, 1f, 0.6f, 0.6f, 0.6f, 1f, 3f)
        ids.forEachIndexed { i, id -> detector.observe(reading(i * frameNanos, id, zoom = zooms[i])) }
        detector.observe(reading(8 * frameNanos, "6", still = true, zoom = 3f))

        val summary = detector.summary()
        assertEquals(9, summary.framesSeen)
        assertEquals(1, summary.stills)
        assertEquals("5 to 2, 2 to 5, 5 to 6", 3, summary.switches)
        assertEquals(listOf("5" to 3, "2" to 3, "6" to 2), summary.framesByPhysicalId.toList())
        assertEquals(1, summary.framesWithoutId)
        assertEquals("5", summary.firstPhysicalId)
        assertEquals("6", summary.lastPhysicalId)
        assertEquals(0.6f, summary.zoomMin)
        assertEquals(3f, summary.zoomMax)
        assertEquals(8 * frameNanos / 1_000_000.0, summary.spanMs!!, 1e-6)
    }

    @Test
    fun `an empty watch summarises to zero counts and null readings, not zeros`() {
        val summary = SwitchDetector().summary()
        assertEquals(0, summary.framesSeen)
        assertEquals(emptyMap<String, Int>(), summary.framesByPhysicalId)
        assertNull(summary.firstPhysicalId)
        assertNull(summary.zoomMin)
        assertNull(summary.spanMs)
    }
}
