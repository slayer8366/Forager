package com.zynergylabs.forager.app.photo.engine

/**
 * The prototype's measurements (dispatch 2026-10-16-02, "Measure and report"): open time, time to the first viewfinder
 * frame, the viewfinder's frame rate, shutter-to-saved per JPG and RAW shot, the burst, and the heap. Each event returns
 * its own log line, and [summary] is the debug summary. Times are from one clock, `elapsedRealtimeNanos` on a phone, and
 * are measured from the engine's `open()`. Plain Kotlin: tested with a scripted clock.
 */
internal class EngineMetrics(private val engine: String) {
    fun openRequested(nanos: Long): Unit = TODO("metrics: built in the next commit")
    fun deviceOpened(nanos: Long): String = TODO("metrics: built in the next commit")
    fun firstFrame(nanos: Long): String = TODO("metrics: built in the next commit")
    fun frame(sensorTimestampNanos: Long): String? = TODO("metrics: built in the next commit")
    fun shot(kind: ShotKind, shutterNanos: Long, savedNanos: Long): String = TODO("metrics: built in the next commit")
    fun burst(frames: Int, requested: Int, sensorTimestamps: List<Long>, submittedNanos: Long, lastArrivalNanos: Long?): String =
        TODO("metrics: built in the next commit")
    fun heap(at: String, usedBytes: Long): String = TODO("metrics: built in the next commit")
    fun summary(): String = TODO("metrics: built in the next commit")

    companion object {
        /** A frame-rate line every this many viewfinder frames: about every 5 s at 30 frames a second. No timer, so no poll loop. */
        const val FPS_WINDOW_FRAMES = 150
    }
}

internal enum class ShotKind(val label: String) { Jpg("jpg"), Raw("raw") }
