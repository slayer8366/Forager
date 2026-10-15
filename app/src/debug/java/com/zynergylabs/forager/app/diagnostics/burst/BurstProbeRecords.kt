package com.zynergylabs.forager.app.diagnostics.burst

/*
 * RAW stage 3, piece 1 (dispatch 2026-10-15-06, RECORD -1048): what the burst-rate and lock probe reads, as plain data.
 * Debug source set only, like every other dev tool (-930, -1046 Q4); a release build contains none of this.
 *
 * Nothing here touches Android. The camera side (CameraXBurstProbeCamera) fills these in; BurstProbe orders the runs and
 * restores the camera; BurstProbeLines turns them into the log lines. That split is what lets the line format, the run
 * summary and the restore-on-every-path rule be tested on the JVM with no camera (CLAUDE.md, architecture).
 */

/** What a run's capture use case outputs. Both are read in memory and discarded; nothing is written anywhere. */
internal enum class ProbeOutput(val label: String) {
    RAW_JPEG("raw+jpeg"),
    RAW_ONLY("raw"),
}

/** The four runs the dispatch names, in its order. */
internal enum class ProbeRun(val id: String, val output: ProbeOutput, val locked: Boolean) {
    RAW_JPEG_UNLOCKED("raw_jpeg_unlocked", ProbeOutput.RAW_JPEG, locked = false),
    RAW_JPEG_LOCKED("raw_jpeg_locked", ProbeOutput.RAW_JPEG, locked = true),
    RAW_ONLY_UNLOCKED("raw_only_unlocked", ProbeOutput.RAW_ONLY, locked = false),
    RAW_ONLY_LOCKED("raw_only_locked", ProbeOutput.RAW_ONLY, locked = true),
}

/**
 * One frame of a run, as read. Times are milliseconds since the run's own start (`SystemClock.elapsedRealtimeNanos`
 * base); every reading that could not be made is null, and [notes] says why. A frame that never arrived, or that the
 * library reported as failed, carries [error]; its readings are then whatever was read before the failure.
 *
 * The three states are the camera2 integer constants as read from the capture result; [BurstProbeLines] names them.
 */
internal data class ProbeFrame(
    val index: Int,
    val submitMs: Double?,
    val startedMs: Double?,
    val rawArrivalMs: Double?,
    val jpegArrivalMs: Double?,
    val sensorTimestampNs: Long?,
    /** Whether this frame's capture result was found (by sensor timestamp). The readings below it come from that result. */
    val resultMatched: Boolean,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val postRawBoost: Int?,
    val aeState: Int?,
    val awbState: Int?,
    val afState: Int?,
    val focusDistanceDiopters: Float?,
    val blackLevel: List<Float>?,
    val whiteLevel: Int?,
    val rawCopyMs: Double?,
    val sharpness: Double?,
    val notes: List<String> = emptyList(),
    val error: String? = null,
)

/** Once per run, beside the frames: sizes, the sensor's own published limits at the bound RAW size, and memory. */
internal data class ProbeRunFacts(
    val rawSize: String?,
    val jpegSize: String?,
    val minFrameDurationNs: Long?,
    val stallDurationNs: Long?,
    val heapUsedPeakBytes: Long?,
    val timestampSource: String?,
    val pipelineMaxDepth: Int?,
    val notes: List<String> = emptyList(),
)

/** `Runtime.maxMemory()` and the heap in use (`totalMemory - freeMemory`), at one moment. */
internal data class MemoryReading(val maxMemoryBytes: Long, val usedBytes: Long)

/** What a bind produced. Only [Bound] is followed by a capture; the other two are reported with their reason. */
internal sealed interface BindOutcome {
    /** Bound and streaming; what the bind noticed goes into the run's facts. */
    class Bound : BindOutcome
    data class Unsupported(val reason: String) : BindOutcome
    data class Failed(val reason: String) : BindOutcome
}

/** Whether the AE and AWB lock reached `LOCKED` in a capture result before the first frame. */
internal sealed interface LockOutcome {
    data class Reached(val waitMs: Double, val aeState: Int, val awbState: Int) : LockOutcome
    data class NotReached(val reason: String, val lastAeState: Int?, val lastAwbState: Int?) : LockOutcome
}

/** The lock's release, read back: [cleared] is true only when the interop options no longer carry either lock key. */
internal data class LockRelease(val cleared: Boolean, val detail: String? = null)

/** The camera handed back: [probeUnbound] is true only when the probe's own use cases are read back as no longer bound. */
internal data class Handback(val probeUnbound: Boolean, val detail: String? = null)

/** A run's frames and its once-per-run facts. */
internal data class BurstCapture(val frames: List<ProbeFrame>, val facts: ProbeRunFacts)

/**
 * The camera, as the probe needs it. The CameraX implementation binds its own preview and capture to its own lifecycle,
 * which CameraX makes the active one (the camera dialog's are suspended, not unbound), and [release] unbinds them, after
 * which CameraX resumes the dialog's own. A fake implements it in the tests.
 */
internal interface BurstProbeCamera {
    fun memory(): MemoryReading
    suspend fun bind(output: ProbeOutput): BindOutcome
    suspend fun lock(timeoutMillis: Long): LockOutcome
    suspend fun unlock(): LockRelease
    suspend fun capture(output: ProbeOutput, count: Int, frameTimeoutMillis: Long): BurstCapture
    suspend fun release(): Handback
}

/** The run's own record of how it ended, for its line. */
internal enum class RunOutcomeKind(val label: String) {
    COMPLETE("complete"),
    PARTIAL("partial"),
    FAILED("failed"),
    UNSUPPORTED("unsupported"),
}

/**
 * A run's summary over its frames (the dispatch's "rate, spread and lock hold"). A figure that cannot be computed from
 * what arrived is null, never zero: one frame has no interval, and a held exposure needs every arrived frame's reading.
 */
internal data class RunSummary(
    val framesExpected: Int,
    val framesArrived: Int,
    val intervalsMs: List<Double>,
    val meanIntervalMs: Double?,
    val minIntervalMs: Double?,
    val maxIntervalMs: Double?,
    val spreadMs: Double?,
    val framesPerSecond: Double?,
    val firstSubmitToLastArrivalMs: Double?,
    val framesWithResult: Int,
    val aeLockedFrames: Int,
    val awbLockedFrames: Int,
    val exposureHeld: Boolean?,
    val isoHeld: Boolean?,
    val focusHeld: Boolean?,
    val focusMinDiopters: Float?,
    val focusMaxDiopters: Float?,
    val lockHeld: Boolean?,
)
