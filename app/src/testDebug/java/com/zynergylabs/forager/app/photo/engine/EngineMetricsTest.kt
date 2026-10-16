package com.zynergylabs.forager.app.photo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The measurement lines and the debug summary, with a scripted clock: the figures are read off the lines exactly. */
class EngineMetricsTest {

    private val ms = 1_000_000L

    @Test
    fun `open, first frame, shots and heap each report their figure from the open`() {
        val metrics = EngineMetrics("platform")
        metrics.openRequested(1_000 * ms)

        assertEquals("engine=platform event=open open_ms=459.0", metrics.deviceOpened(1_459 * ms))
        assertEquals("engine=platform event=first_frame first_frame_ms=752.5", metrics.firstFrame(1_752 * ms + 500_000L))
        assertEquals("engine=platform event=shot kind=jpg shutter_to_saved_ms=210.0", metrics.shot(ShotKind.Jpg, 5_000 * ms, 5_210 * ms))
        assertEquals("engine=platform event=shot kind=raw shutter_to_saved_ms=640.0", metrics.shot(ShotKind.Raw, 6_000 * ms, 6_640 * ms))
        assertEquals("engine=platform event=heap at=first_frame used_mb=64.0", metrics.heap("first_frame", 64L shl 20))
    }

    @Test
    fun `the viewfinder's frame rate is reported once a window of frames has arrived, from their sensor timestamps`() {
        val metrics = EngineMetrics("platform")
        metrics.openRequested(0)
        val frameNanos = 33_333_333L
        var line: String? = null
        for (i in 0 until EngineMetrics.FPS_WINDOW_FRAMES) {
            line = metrics.frame(i * frameNanos)
            if (i < EngineMetrics.FPS_WINDOW_FRAMES - 1) assertNull("frame $i", line)
        }
        assertEquals("engine=platform event=preview_fps frames=150 fps=30.0", line)
    }

    @Test
    fun `the burst line gives frames, rate, first to last and shutter to last`() {
        val metrics = EngineMetrics("platform")
        metrics.openRequested(0)
        val stamps = List(8) { 5_000 * ms + it * 33_333_333L }
        assertEquals(
            "engine=platform event=burst frames=8/8 fps=30.0 first_to_last_ms=233.3 shutter_to_last_ms=273.3",
            metrics.burst(frames = 8, requested = 8, sensorTimestamps = stamps, submittedNanos = 9_000 * ms, lastArrivalNanos = 9_273 * ms + 333_333L),
        )
    }

    @Test
    fun `the summary carries every figure, and says none where a figure was never measured`() {
        val metrics = EngineMetrics("platform")
        metrics.openRequested(1_000 * ms)
        metrics.deviceOpened(1_459 * ms)
        metrics.firstFrame(1_752 * ms)
        metrics.shot(ShotKind.Jpg, 5_000 * ms, 5_210 * ms)
        metrics.shot(ShotKind.Jpg, 7_000 * ms, 7_190 * ms)
        metrics.heap("open", 60L shl 20)
        metrics.heap("raw_shot", 112L shl 20)

        assertEquals(
            "engine=platform event=summary open_ms=459.0 first_frame_ms=752.0 preview_fps=none jpg_ms=[210.0,190.0] raw_ms=[] burst=none heap_peak_mb=112.0",
            metrics.summary(),
        )
    }
}
