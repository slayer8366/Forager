package com.zynergylabs.forager.app.photo.engine

import java.util.Locale

/**
 * The prototype's measurements (dispatch 2026-10-16-02, "Measure and report"): open time, time to the first viewfinder
 * frame, the viewfinder's frame rate, shutter-to-saved per JPG and RAW shot, the burst, and the heap. Each event returns
 * its own log line, and [summary] is the debug summary. Times are from one clock, `elapsedRealtimeNanos` on a phone, and
 * open and first frame are measured from the engine's `open()`. Plain Kotlin: tested with a scripted clock.
 *
 * The frame rate is computed from the frames' own sensor timestamps over a window of [FPS_WINDOW_FRAMES], so it is the
 * rate the camera delivered, not how often the main thread got to them; no timer drives it, so there is no poll loop
 * (CLAUDE.md, on unstopped poll loops in tests).
 */
internal class EngineMetrics(private val engine: String) {
    private var openNanos: Long? = null
    private var openMs: Double? = null
    private var firstFrameMs: Double? = null
    private val window = ArrayList<Long>(FPS_WINDOW_FRAMES)
    private var lastFps: Double? = null
    private val shots = mapOf(ShotKind.Jpg to mutableListOf<Double>(), ShotKind.Raw to mutableListOf())
    private var burstSummary: String? = null
    private var heapPeakBytes: Long = 0

    fun openRequested(nanos: Long) {
        openNanos = nanos
    }

    fun deviceOpened(nanos: Long): String {
        val ms = sinceOpen(nanos)
        openMs = ms
        return "engine=$engine event=open open_ms=${fmt(ms)}"
    }

    fun firstFrame(nanos: Long): String {
        val ms = sinceOpen(nanos)
        firstFrameMs = ms
        return "engine=$engine event=first_frame first_frame_ms=${fmt(ms)}"
    }

    /** One viewfinder frame; a frame-rate line when a window has filled, else null. */
    fun frame(sensorTimestampNanos: Long): String? {
        window += sensorTimestampNanos
        if (window.size < FPS_WINDOW_FRAMES) return null
        val span = window.last() - window.first()
        val frames = window.size
        window.clear()
        if (span <= 0) return "engine=$engine event=preview_fps frames=$frames fps=unknown(timestamps did not advance)"
        val fps = (frames - 1) / (span / 1e9)
        lastFps = fps
        return "engine=$engine event=preview_fps frames=$frames fps=${fmt(fps)}"
    }

    /** Clears the frame window, so a frame rate never spans a pause or a reopen. */
    fun streamRestarted() {
        window.clear()
    }

    fun shot(kind: ShotKind, shutterNanos: Long, savedNanos: Long): String {
        val ms = (savedNanos - shutterNanos) / 1e6
        shots.getValue(kind) += ms
        return "engine=$engine event=shot kind=${kind.label} shutter_to_saved_ms=${fmt(ms)}"
    }

    fun burst(frames: Int, requested: Int, sensorTimestamps: List<Long>, submittedNanos: Long, lastArrivalNanos: Long?): String {
        val sorted = sensorTimestamps.sorted()
        val span = if (sorted.size >= 2) sorted.last() - sorted.first() else null
        val fps = span?.takeIf { it > 0 }?.let { (sorted.size - 1) / (it / 1e9) }
        val firstToLast = span?.let { it / 1e6 }
        val shutterToLast = lastArrivalNanos?.let { (it - submittedNanos) / 1e6 }
        burstSummary = "$frames/$requested@${fps?.let { fmt(it) } ?: "none"}fps,${firstToLast?.let { fmt(it) } ?: "none"}ms"
        return "engine=$engine event=burst frames=$frames/$requested fps=${fps?.let { fmt(it) } ?: "none"} " +
            "first_to_last_ms=${firstToLast?.let { fmt(it) } ?: "none"} shutter_to_last_ms=${shutterToLast?.let { fmt(it) } ?: "none"}"
    }

    fun heap(at: String, usedBytes: Long): String {
        heapPeakBytes = maxOf(heapPeakBytes, usedBytes)
        return "engine=$engine event=heap at=$at used_mb=${fmt(usedBytes / MB)}"
    }

    fun summary(): String =
        "engine=$engine event=summary open_ms=${openMs?.let { fmt(it) } ?: "none"} first_frame_ms=${firstFrameMs?.let { fmt(it) } ?: "none"} " +
            "preview_fps=${lastFps?.let { fmt(it) } ?: "none"} jpg_ms=${list(ShotKind.Jpg)} raw_ms=${list(ShotKind.Raw)} " +
            "burst=${burstSummary ?: "none"} heap_peak_mb=${if (heapPeakBytes > 0) fmt(heapPeakBytes / MB) else "none"}"

    private fun list(kind: ShotKind) = shots.getValue(kind).joinToString(",", prefix = "[", postfix = "]") { fmt(it) }

    private fun sinceOpen(nanos: Long): Double = (nanos - (openNanos ?: nanos)) / 1e6

    companion object {
        /** A frame-rate line every this many viewfinder frames: about every 5 s at 30 frames a second. No timer, so no poll loop. */
        const val FPS_WINDOW_FRAMES = 150
        private const val MB = 1024.0 * 1024.0

        fun fmt(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
    }
}

internal enum class ShotKind(val label: String) { Jpg("jpg"), Raw("raw") }
