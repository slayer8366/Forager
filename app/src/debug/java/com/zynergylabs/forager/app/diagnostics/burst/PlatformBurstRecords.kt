package com.zynergylabs.forager.app.diagnostics.burst

/*
 * RAW stage 3, the fast-burst test (dispatch 2026-10-15-09, RECORD -1063): what the platform burst probe reads, as plain
 * data. Debug source set only, like every other dev tool (-930, -1046 Q4); a release build contains none of this.
 *
 * The probe borrows the camera from CameraX, takes four runs of 8 RAW frames through the platform's own camera interface,
 * and hands the camera back. Nothing here touches Android: the camera side (PlatformBurstCameraImpl) fills these in,
 * PlatformBurstProbe orders the steps and hands back on every path, PlatformBurstLines turns them into log lines and
 * PlatformBurstFiles names the saved frames. That split is what lets the line formats, the run summary, the file names
 * and the hand-back-on-every-path rule be tested on the JVM with no camera (CLAUDE.md, architecture).
 *
 * Every time is in milliseconds since the probe's trigger (the moment the borrow started), one base for every line, so
 * the swap pause, the burst and the hand-back can be read off one axis. A reading that was not made is null, never 0.
 */

/** The dispatch's four runs, in its order. Each is 8 RAW frames, AE and AWB locked after convergence. */
internal enum class PlatformRun(val id: String) {
    /** The still-capture template's own processing modes: what Forager's RAW shots get today, as near as the probe can tell. */
    DEFAULT_LOCKED("default_locked"),

    /** The same, with noise reduction OFF and hot-pixel correction OFF on the burst requests, where offered. */
    NR_HOTPIXEL_OFF("nr_hotpixel_off"),

    /** AE off, at the converged exposure time, ISO and frame duration, where MANUAL_SENSOR is offered. */
    MANUAL_EXPOSURE("manual_exposure"),

    /** Lens-shading correction mode OFF on the burst requests, where offered. */
    SHADING_OFF("shading_off"),
}

/** One RAW frame of a run, as read. [error] is set for a frame that never arrived or failed; its other readings are whatever was read. */
internal data class PlatformFrame(
    val index: Int,
    /** When the image was acquired from the reader, ms since the trigger. */
    val arrivalMs: Double?,
    val sensorTimestampNs: Long?,
    /** Whether this frame's capture result was found (by sensor timestamp). The readings from exposure to lensShadingMapMode come from it. */
    val resultMatched: Boolean,
    val exposureTimeNs: Long?,
    val iso: Int?,
    val frameDurationNs: Long?,
    val postRawBoost: Int?,
    /** The processing modes the capture result reports, as camera2 integers; [PlatformBurstLines] names them. */
    val noiseReductionMode: Int?,
    val hotPixelMode: Int?,
    val edgeMode: Int?,
    val shadingMode: Int?,
    val tonemapMode: Int?,
    val blackLevelLock: Boolean?,
    val lensShadingMapMode: Int?,
    val blackLevel: List<Float>?,
    val whiteLevel: Int?,
    /** Copying the RAW plane out of the image, ms. Done after the burst, so it never slows the burst itself. */
    val copyMs: Double?,
    /** Mean of the four corner blocks over the centre block, black subtracted ([rawCornerToCentre]); how strongly the RAW itself is vignetted. */
    val cornerToCentre: Double?,
    /** The saved DNG's path under the app's files directory, or null when none was saved. */
    val dngFile: String?,
    /** "written", or why no DNG was kept. */
    val dngOutcome: String?,
    val notes: List<String> = emptyList(),
    val error: String? = null,
)

/** Once per run, beside its frames. */
internal data class PlatformRunFacts(
    val rawSize: String?,
    /** `captureBurst`, and why, or whatever was used. */
    val requestMethod: String,
    /** The settings put on this run's burst requests beyond the template, as `KEY=VALUE`. */
    val requested: List<String>,
    val minFrameDurationNs: Long?,
    val stallDurationNs: Long?,
    val maxMemoryBytes: Long?,
    val heapBeforeBytes: Long?,
    val heapPeakBytes: Long?,
    val heapAfterBytes: Long?,
    /** The burst was submitted at this time, ms since the trigger. */
    val submitMs: Double?,
    /** Copying and writing the 8 DNGs after the burst, ms. */
    val saveMs: Double?,
    /** The run's folder, relative to the app's files directory. */
    val folder: String?,
    val notes: List<String> = emptyList(),
)

/** What one run produced. */
internal sealed interface PlatformBurstOutcome {
    data class Captured(val frames: List<PlatformFrame>, val facts: PlatformRunFacts) : PlatformBurstOutcome

    /** The phone does not offer what the run needs; nothing was submitted. */
    data class Unsupported(val reason: String) : PlatformBurstOutcome
}

/** The swap from CameraX to the platform session, as far as the app can see it. */
internal data class SwapTiming(
    val cameraId: String?,
    /** CameraX's camera state read CLOSED after it let the camera go. */
    val cameraxClosedMs: Double?,
    /** The dialog's viewfinder (PreviewView) reported its stream IDLE: from here the user sees a frozen or blank viewfinder. */
    val viewfinderIdleMs: Double?,
    /** The platform interface's onOpened. */
    val platformOpenedMs: Double?,
    /** The platform session's onConfigured. */
    val sessionConfiguredMs: Double?,
    val rawSize: String?,
    val previewSize: String?,
    /** What the probe could see of the dialog's viewfinder: whether a PreviewView was found, and its mode. */
    val viewfinder: String?,
    /** CameraX's camera-state transitions seen during the borrow, as `TYPE@ms` (with an error code when there was one). */
    val cameraStates: List<String>,
    val notes: List<String> = emptyList(),
)

internal sealed interface BorrowOutcome {
    val timing: SwapTiming

    data class Borrowed(override val timing: SwapTiming) : BorrowOutcome
    data class Unsupported(val reason: String, override val timing: SwapTiming) : BorrowOutcome
    data class Failed(val reason: String, override val timing: SwapTiming) : BorrowOutcome
}

/** AE and AWB on the platform session's preview, first converged and then locked. */
internal sealed interface ConvergeOutcome {
    data class Locked(
        val convergedMs: Double,
        val lockedMs: Double,
        val exposureTimeNs: Long?,
        val iso: Int?,
        val frameDurationNs: Long?,
    ) : ConvergeOutcome

    data class NotReached(val reason: String, val lastAeState: Int?, val lastAwbState: Int?) : ConvergeOutcome
}

/** The camera handed back to the dialog. */
internal data class PlatformHandback(
    /** The platform session and device were closed (or were never opened). */
    val platformClosed: Boolean,
    /** The probe's CameraX placeholder is read back as no longer bound, so CameraX resumed the dialog's own binding. */
    val probeUnbound: Boolean,
    /** Times since the trigger. */
    val handbackStartMs: Double?,
    val platformClosedMs: Double?,
    /** CameraX's camera state read OPEN again. */
    val cameraxOpenMs: Double?,
    /** The dialog's viewfinder (PreviewView) reported STREAMING again: frames are on screen. */
    val viewfinderLiveMs: Double?,
    val cameraStates: List<String>,
    val detail: String? = null,
)

/**
 * The camera, as the probe needs it. The production implementation borrows CameraX's camera and drives the platform
 * session; a fake implements it in the tests.
 */
internal interface PlatformBurstCamera {
    fun memory(): MemoryReading
    suspend fun borrow(): BorrowOutcome
    suspend fun converge(): ConvergeOutcome
    suspend fun burst(run: PlatformRun, count: Int, converged: ConvergeOutcome.Locked): PlatformBurstOutcome
    /** Takes AE and AWB lock off the platform session's requests. Safe to call when nothing was locked. */
    suspend fun releaseLocks(): LockRelease
    /** Closes the platform session and device and lets CameraX resume the dialog's binding. Safe to call from any state. */
    suspend fun handBack(): PlatformHandback
}

/** A run's summary over its frames. A figure that cannot be computed from what arrived is null, never zero. */
internal data class PlatformRunSummary(
    val framesExpected: Int,
    val framesArrived: Int,
    val intervalsMs: List<Double>,
    val meanIntervalMs: Double?,
    val minIntervalMs: Double?,
    val maxIntervalMs: Double?,
    val spreadMs: Double?,
    val framesPerSecond: Double?,
    /** First to last sensor timestamp: how long the photographer had to hold still. */
    val firstToLastMs: Double?,
    /** From the burst's submit to the last frame's arrival. */
    val submitToLastArrivalMs: Double?,
    val framesWithResult: Int,
    val framesSaved: Int,
)
