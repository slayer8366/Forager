package com.zynergylabs.forager.app.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.domain.raw.CfaPattern
import com.zynergylabs.forager.app.domain.raw.DngCalibration
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import java.io.File

/**
 * The [CameraCaptureSession] every test drives the camera screen with. Shared here, beside
 * [FileProviderCacheReset], and **observable**: [state] is backed by `mutableStateOf`, so a flip
 * after composition recomposes the screen. It used to be a private class inside
 * `InAppCameraDialogTest` with a plain `var`, which is half of how the deadlock this was written
 * for went unseen (2026-09-15; see that test class's doc).
 *
 * ## Scripted behaviours
 *
 * - [open] moves [state] from `Opening` to `Ready` unless [readyOnOpen] is false, which models a
 *   provider that has not resolved yet. It never changes an `Unavailable` state: that models an
 *   open that failed. [close] only counts. Both count calls so a test can assert the screen
 *   opens once on enter and closes once on leave, which is the lifecycle the fix hangs on.
 * - [capture] writes a real file. On success, two bytes. On failure — after [failEveryCapture],
 *   until [recover] — **a one-byte stub and then** `Result.failure`. The stub is the point: a
 *   camera that errors mid-write leaves something behind, and a fake that wrote nothing would let
 *   "a failed capture leaves no file behind" pass whether or not the screen cleaned up. A revert
 *   check found exactly that on the original.
 * - [captureCalls] counts, so "four photos" is distinguishable from "a count that went up".
 * - **Flash**, as the interface states it: [hasFlashUnit] is false until [open] reaches `Ready`,
 *   then [flashUnitOnOpen]; [setFlashMode] is counted in [setFlashModeCalls] and changes nothing
 *   without a unit; [close] puts the mode back to `Off` and drops the unit. A fake more permissive
 *   than the contract would let the screen pass on behaviour the real session refuses.
 * - **Capabilities** (dispatch 2026-10-13-04), as the real session sets them: [capabilities] and
 *   [encoderCapabilities] are null until [open] reaches `Ready`, then [capabilitiesOnOpen] and
 *   [encodersOnOpen]; [close] drops both. Settable, so a test can land them after composition as
 *   the real bind and the real background encoder read do.
 */
internal class FakeCameraCaptureSession(
    state: CameraSessionState = CameraSessionState.Ready,
    private val readyOnOpen: Boolean = true,
    /** Whether the camera [open] binds has a flash unit. On by default, so the flash chip is there as on most phones. */
    private val flashUnitOnOpen: Boolean = true,
    /** What [open] reports the bound camera can do; null by default, as before the probe existed. */
    private val capabilitiesOnOpen: CameraCapabilities? = null,
    /** What [open] reports the encoders can do; null by default. */
    private val encodersOnOpen: EncoderCapabilities? = null,
) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(state)

    /** Settable so a test can turn the device and watch the screen's controls follow. */
    override var deviceRotation: Int? by mutableStateOf(null)

    /** Settable so a test can take the unit away mid-session; [open] and [close] set it as the real session does. */
    override var hasFlashUnit: Boolean by mutableStateOf(false)

    override var capabilities: CameraCapabilities? by mutableStateOf(null)

    override var encoderCapabilities: EncoderCapabilities? by mutableStateOf(null)

    var setFlashModeCalls = 0
        private set

    private var currentFlashMode: FlashMode by mutableStateOf(FlashMode.Off)
    override val flashMode: FlashMode get() = currentFlashMode

    override fun setFlashMode(mode: FlashMode) {
        setFlashModeCalls += 1
        if (!hasFlashUnit) return // as the interface says: no unit, no change
        currentFlashMode = mode
    }

    var openCalls = 0
        private set
    var closeCalls = 0
        private set
    var captureCalls = 0
        private set

    private var failing = false

    fun failEveryCapture() { failing = true }

    fun recover() { failing = false }

    override fun open(lifecycleOwner: LifecycleOwner) {
        events += "open"
        openCalls += 1
        if (readyOnOpen && state == CameraSessionState.Opening) state = CameraSessionState.Ready
        if (state == CameraSessionState.Ready) {
            hasFlashUnit = flashUnitOnOpen
            capabilities = capabilitiesOnOpen
            encoderCapabilities = encodersOnOpen
        }
    }

    override fun close() {
        closeCalls += 1
        currentFlashMode = FlashMode.Off
        hasFlashUnit = false
        capabilities = null
        encoderCapabilities = null
        outputFormat = CameraFormat.Jpg
    }

    // ── RAW stage 2 (dispatch 2026-10-15-01) ──────────────────────────────────────────────────────────────────

    override var outputFormat: CameraFormat by mutableStateOf(CameraFormat.Jpg)
        private set

    /** Every format the screen asked for, in order. */
    val requestedFormats = mutableListOf<CameraFormat>()

    /** When false, a RAW request leaves the session on JPG, as the real one does when RAW will not bind. */
    var rawBinds: Boolean = true

    var captureRawCalls = 0
        private set

    /** What the next RAW shot's sensor data maps to; a small, valid input by default. */
    var nextRawShot: () -> Result<RawShot> = { Result.success(RawShot(dng = fakeDngImage(), dngUnavailable = null)) }

    /** Requests and opens, in the order they reached the session. */
    val events = mutableListOf<String>()

    override fun requestOutputFormat(format: CameraFormat) {
        events += "request $format"
        requestedFormats += format
        outputFormat = if (format == CameraFormat.Raw && !rawBinds) CameraFormat.Jpg else format
    }

    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> {
        captureRawCalls += 1
        if (outputFormat != CameraFormat.Raw) return Result.failure(IllegalStateException("not bound for RAW"))
        jpegDestination.parentFile?.mkdirs()
        if (failing) return Result.failure(IllegalStateException("the camera said no"))
        jpegDestination.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        return nextRawShot()
    }

    override suspend fun capture(destination: File): Result<Unit> {
        captureCalls += 1
        destination.parentFile?.mkdirs()
        if (failing) {
            destination.writeBytes(byteArrayOf(0xFF.toByte()))
            return Result.failure(IllegalStateException("the camera said no"))
        }
        destination.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        return Result.success(Unit)
    }
}

/** A small, well-formed DNG input: 4x4 GRBG, one illuminant. Only its shape matters to the camera side; the writer is faked. */
internal fun fakeDngImage(width: Int = 4, height: Int = 4): DngImage = DngImage(
    width = width,
    height = height,
    samples = ShortArray(width * height) { (it * 37 % 1023).toShort() },
    cfa = CfaPattern.GRBG,
    blackLevel = listOf(64f, 64f, 64f, 64f),
    whiteLevel = 1023,
    calibration1 = DngCalibration(illuminant = 21, colorMatrix = List(9) { SRational(if (it % 4 == 0) 1 else 0, 1) }),
    asShotNeutral = listOf(URational(1, 2), URational(1, 1), URational(1, 2)),
    orientation = 1,
)
