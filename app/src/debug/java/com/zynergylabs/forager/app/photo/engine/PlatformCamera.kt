package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.RawFrameReadings
import com.zynergylabs.forager.app.photo.FlashMode
import java.io.File

/*
 * Camera engine A, the manageability prototype (dispatch 2026-10-16-02, RECORD -1083). Debug source set only.
 *
 * [PlatformCamera] is what the engine ([PlatformCameraEngine]) needs from the platform camera interface, and nothing else:
 * the interface this project owns over camera2 (CLAUDE.md, architecture), so the engine's state machine runs headless
 * against a fake (`FakePlatformCamera` in testDebug) and the camera2 calls stay in one file, `Camera2PlatformCamera.kt`.
 * No camera2 type crosses it: what the engine decides (which flash mode means which request, when a shot needs the
 * precapture sequence, which orientation a shot asks for, when to fall back) is plain Kotlin over these types.
 */
internal interface PlatformCamera {

    /** Chooses the camera (the back camera, else the front, as `CameraXCaptureSession.open` does) and reads what the engine needs. Never throws. */
    fun describe(): CameraDescription

    /**
     * Opens the device. [events] receives what happens to it afterwards (another app taking it, an error), on the main
     * thread. Cancelling the call while the open is pending closes the device when it lands.
     */
    suspend fun open(facts: CameraFacts, events: (DeviceEvent) -> Unit): OpenOutcome

    /** Configures the session for [plan] with [preview] as the viewfinder's stream. Replaces a session already configured. */
    suspend fun configure(facts: CameraFacts, preview: PreviewTarget, plan: StreamPlan): ConfigureOutcome

    /** Starts (or replaces) the repeating viewfinder request. Each frame's result reaches [onFrame] on the main thread. */
    fun startRepeating(controls: PreviewControls, onFrame: (PreviewFrame) -> Unit): Result<Unit>

    /** Runs the platform's AE precapture sequence on the running session and waits for it to settle or time out. */
    suspend fun precapture(controls: PreviewControls, timeoutMillis: Long): PrecaptureOutcome

    /** Ends a precapture sequence (the trigger's cancel), so the next preview frames meter freely again. */
    fun endPrecapture(controls: PreviewControls)

    /** One still: the JPEG stream only, written to [destination]. */
    suspend fun captureJpeg(still: StillControls, destination: File): Result<JpegFrame>

    /** One still with the JPEG and the RAW streams in the same request, so both come from one exposure. */
    suspend fun captureRawJpeg(still: StillControls, jpegDestination: File): Result<RawJpegFrame>

    /** Locks AE and AWB on the repeating request and waits for both to read locked, or times out. */
    suspend fun lockExposure(controls: PreviewControls, timeoutMillis: Long): LockOutcome

    /** [count] RAW requests in one burst on the running session; every image is discarded as it arrives. */
    suspend fun burst(count: Int, still: StillControls, timeoutMillis: Long): Result<BurstFrames>

    /** Closes the session only; the device stays open, so a new viewfinder stream can be configured. */
    suspend fun closeSession()

    /** Closes the session and the device and waits for the device to report it has closed. Safe to call twice. */
    suspend fun release(): ReleaseOutcome

    /** Calls [onAvailable] once, on the main thread, when the camera [cameraId] is free again. Returns the cancel. */
    fun watchAvailability(cameraId: String, onAvailable: () -> Unit): () -> Unit

    /** `SystemClock.elapsedRealtimeNanos()` on a phone; the same clock as the sensor timestamps. */
    fun nowNanos(): Long

    /** The Java heap in use, for the memory figures. */
    fun heapUsedBytes(): Long
}

internal sealed interface CameraDescription {
    data class Described(val facts: CameraFacts) : CameraDescription
    data class NoCamera(val reason: String) : CameraDescription
}

/**
 * The chosen camera, read once at open. [rawSize] is null when the camera offers no RAW within the operating limit
 * (`domain/CameraFormat.kt`'s `rawCaptureSize`), and then no RAW stream is configured and the chip is not offered.
 */
internal data class CameraFacts(
    val cameraId: String,
    val facing: CameraFacing,
    val sensorOrientation: Int,
    val hasFlashUnit: Boolean,
    val jpegSize: PixelSize,
    val previewSize: PixelSize,
    val rawSize: PixelSize?,
    /** The main wide physical camera behind a logical one (RECORD -1079), or null with [mainWideNote] saying why. */
    val mainWidePhysicalId: String?,
    val mainWideNote: String,
    /** Whether the camera offers noise reduction OFF, which every RAW request asks for where offered (RECORD -1064). */
    val noiseReductionOffOffered: Boolean,
    /** What the chip and the probe line read, as `CameraXCaptureSession.capabilities` reports them. */
    val capabilities: CameraCapabilities,
    val notes: List<String> = emptyList(),
    /**
     * The phone's real back lenses for the lens chooser (dispatch 2026-10-16-04), or null for no row, with [lensNote]
     * saying why. Null keeps the engine exactly as it was before the lens chooser: logical viewfinder and JPEG streams.
     */
    val lenses: LensSet? = null,
    val lensNote: String = "",
    /** Each lens's and the logical camera's static zoom facts, logged once at open (dispatch 2026-10-17-01). */
    val lensStatics: List<LensStatic> = emptyList(),
)

internal sealed interface DeviceEvent {
    /** Another client took the camera (`onDisconnected`). */
    data object Disconnected : DeviceEvent

    /** `onError`, with the platform's error named. [inUse] for the two errors that mean another client holds it. */
    data class Error(val name: String, val inUse: Boolean) : DeviceEvent
}

internal sealed interface OpenOutcome {
    data object Opened : OpenOutcome

    /** Another client holds the camera, or too many cameras are open. */
    data class InUse(val reason: String) : OpenOutcome

    data class Failed(val reason: String) : OpenOutcome
}

/**
 * What the session carries: the viewfinder, the JPEG and, where offered, the RAW stream. With [lens] set (the lens
 * chooser), all three are physical streams of that one lens, at its own sizes: the documented-safe combination for a
 * hidden physical camera (preview + JPEG + RAW, the RAW table of `CameraDevice`, through its logical camera, the
 * LOGICAL_MULTI_CAMERA notes; Phase 1 report item 3).
 */
internal data class StreamPlan(val raw: Boolean, val lens: Lens? = null)

internal sealed interface ConfigureOutcome {
    /** [rawSource] says where the RAW stream comes from: the pinned physical camera, the logical camera, or none. */
    data class Configured(val rawSource: RawSource, val note: String) : ConfigureOutcome
    data class Failed(val reason: String) : ConfigureOutcome
}

internal sealed interface RawSource {
    data object None : RawSource

    /** The RAW stream is the logical camera's; with a RAW stream configured the platform keeps its default physical camera active. */
    data object Logical : RawSource

    /** A physical stream from [physicalId], the main wide, pinned (RECORD -1079 attempt 1). */
    data class Physical(val physicalId: String) : RawSource
}

/** The viewfinder's stream, handed in by the viewfinder composable. Opaque to the engine. */
internal interface PreviewTarget {
    /** Releases what the target holds, once the session no longer draws into it. */
    fun release()
}

/** What the repeating viewfinder request carries. */
internal data class PreviewControls(
    val flash: FlashMode,
    val aeLock: Boolean = false,
    val awbLock: Boolean = false,
    /** The lens chooser's zoom on the streaming lens, or null without the lens chooser (no crop, as before). */
    val lensCrop: LensCrop? = null,
    /** How the zoom is asked of the camera (dispatch 2026-10-17-01's adb switch); [ZoomForm.Auto] is today's form. */
    val zoomForm: ZoomForm = ZoomForm.Auto,
)

/** What a still request carries. [jpegOrientationDegrees] is the request's `JPEG_ORIENTATION`. */
internal data class StillControls(
    val flash: FlashMode,
    val jpegOrientationDegrees: Int,
    /** Asked on every RAW request where offered (RECORD -1064). */
    val noiseReductionOff: Boolean,
    val aeAwbLocked: Boolean = false,
    /** The zoom the shot is taken at, as [PreviewControls.lensCrop]. */
    val lensCrop: LensCrop? = null,
    val zoomForm: ZoomForm = ZoomForm.Auto,
)

/** One viewfinder frame's result. States are the platform's `CONTROL_AE_STATE` / `CONTROL_AWB_STATE` values. */
internal data class PreviewFrame(
    val sensorTimestampNanos: Long,
    val aeState: Int?,
    val awbState: Int?,
    val activePhysicalId: String?,
    /** The streaming lens's LENS_FOCUS_DISTANCE in diopters, for the close-focus rule; null when not reported. */
    val focusDiopters: Float? = null,
    /** The streaming lens's CONTROL_AF_STATE. */
    val afState: Int? = null,
    /**
     * How far the camera cropped the lens as its own result reports it (its active array over the result's crop region);
     * null when not read. In the main wide's terms under the logical zoom ratio: see [cropInLensUnits].
     */
    val cropAchieved: Double? = null,
    /** The zoom form the frame's request used, for reading [cropAchieved] in the lens's units; null when not known. */
    val zoomFormUsed: ZoomForm? = null,
    /** The frame's request and result, for the `crop_diag` line; null when not read. */
    val zoomDiag: FrameZoomDiag? = null,
)

internal sealed interface PrecaptureOutcome {
    data class Settled(val aeState: Int?, val waitedMillis: Long) : PrecaptureOutcome
    data class TimedOut(val aeState: Int?) : PrecaptureOutcome
    data class Failed(val reason: String) : PrecaptureOutcome
}

internal sealed interface LockOutcome {
    data class Locked(val exposureTimeNanos: Long?, val iso: Int?) : LockOutcome
    data class NotLocked(val reason: String, val aeState: Int?, val awbState: Int?) : LockOutcome
}

/** A still's JPEG on disk: its encoded size (the unrotated frame the orientation tag describes) and which sensor took it. */
internal data class JpegFrame(
    val width: Int,
    val height: Int,
    val activePhysicalId: String?,
    /** As [PreviewFrame.cropAchieved], for the shot's own result: logged beside the crop asked for. */
    val cropAchieved: Double? = null,
    val zoomFormUsed: ZoomForm? = null,
    val zoomDiag: FrameZoomDiag? = null,
)

/**
 * The JPEG on disk and the RAW frame of the same exposure. [readings] builds the DNG input once the orientation is known
 * (the DNG takes the JPG's final tag, Amendment 1 item 4 of dispatch 2026-10-15-01); null with [readingsUnavailable] when
 * the frame or its result could not be read.
 */
internal class RawJpegFrame(
    val jpeg: JpegFrame,
    val rawSource: RawSource,
    val rawActivePhysicalId: String?,
    /** The capture result's `NOISE_REDUCTION_MODE`, logged beside what was asked (RECORD -1064). */
    val noiseReductionModeReported: Int?,
    val readings: ((orientation: Int) -> RawFrameReadings)?,
    val readingsUnavailable: String?,
)

internal data class BurstFrame(val sensorTimestampNanos: Long, val arrivedNanos: Long, val activePhysicalId: String?, val noiseReductionMode: Int?)

internal data class BurstFrames(val submittedNanos: Long, val frames: List<BurstFrame>, val failures: List<String>)

internal data class ReleaseOutcome(val closed: Boolean, val detail: String? = null)

/** The platform's state constants the engine reads, restated so the engine needs no camera2 import (values from `CaptureResult`). */
internal object PlatformStates {
    const val AE_INACTIVE = 0
    const val AE_SEARCHING = 1
    const val AE_CONVERGED = 2
    const val AE_LOCKED = 3
    const val AE_FLASH_REQUIRED = 4
    const val AE_PRECAPTURE = 5
    const val AWB_LOCKED = 3
}
