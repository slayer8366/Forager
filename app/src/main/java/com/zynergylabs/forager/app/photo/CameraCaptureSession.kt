package com.zynergylabs.forager.app.photo

import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.domain.raw.DngImage
import java.io.File

/**
 * What the in-app camera screen needs from a camera, and nothing else — the interface this project
 * owns, per CLAUDE.md's rule that an external integration is wrapped rather than depended on
 * directly. [CameraXCaptureSession] is the only implementation that touches `androidx.camera`
 * (with its capability reader, `CameraXCapabilityReader.kt`, since dispatch 2026-10-13-04).
 *
 * ## Why this exists rather than the screen calling CameraX
 *
 * Not tidiness. **CameraX cannot run in this project's test harness at all** — Robolectric has no
 * camera provider, no camera2 HAL and no surface to draw a viewfinder on — so a screen that called
 * CameraX directly would have no testable behaviour left: not the shutter, not the running count,
 * not what happens when a capture fails. Behind this interface, a fake session makes all of that
 * ordinary Robolectric work, and what stays device-only is exactly the part that genuinely is
 * device-only: whether a real camera opens and produces a real JPEG.
 *
 * That split is the point. It is worth being plain that it does not make the feature verified —
 * see [com.zynergylabs.forager.app.ui.log.InAppCameraDialog] and the completion report for what a
 * green suite here does and does not establish.
 */
internal interface CameraCaptureSession {

    /** Where the camera is in opening. The screen renders from this; it never infers readiness from a null check. */
    val state: CameraSessionState

    /**
     * The device's orientation as a `Surface.ROTATION_*` value, from the same listener that tags
     * each photo's rotation; `null` until it has reported, or if the device has no sensor. Read
     * by the screen to turn its controls in place while the window stays locked — see
     * `ui/log/RotateWithDevice.kt`. Observable in the CameraX implementation, so a change
     * recomposes what reads it.
     */
    val deviceRotation: Int?

    /**
     * Starts the camera: fetches the provider, binds the use cases to [lifecycleOwner], and moves
     * [state] from `Opening` to `Ready`, or to `Unavailable` with a reason. Called by the screen
     * when it enters composition, **not** by the viewfinder.
     *
     * That distinction is the fix for a deadlock found on device on 2026-09-15: the screen composes
     * its viewfinder only once `Ready`, and the version that shipped did the opening *inside* the
     * viewfinder, so the screen waited for `Ready` before composing the only thing that could
     * produce it. Opening is the screen's call; the viewfinder only draws.
     */
    fun open(lifecycleOwner: LifecycleOwner)

    /**
     * Releases whatever [open] acquired: unbinds, stops listening for orientation, drops the
     * viewfinder surface. Safe to call when [open] never completed, and a second call is a no-op.
     * Called by the screen when it leaves composition.
     */
    fun close()

    /**
     * Writes one photo to [destination], suspending until it is on disk or has failed.
     *
     * Returns [Result] rather than throwing, and rather than a `Boolean`: a failed capture must
     * reach the user as a failure carrying its cause, not as a silently skipped photo. CLAUDE.md,
     * on partial results never being presented as success.
     */
    suspend fun capture(destination: File): Result<Unit>

    /**
     * Whether the bound camera has a flash unit. `false` before [open] has bound a camera and
     * after [close]: until a camera is bound there is no unit to report, and `false` is what hides
     * the flash chip. Observable, so the chip appears when the bind lands.
     */
    val hasFlashUnit: Boolean

    /**
     * The flash mode the camera is in. [FlashMode.Off] until [setFlashMode] changes it, and reset
     * to `Off` by [close]: no flash mode persists past the camera closing, and nothing stores it.
     * Observable, so the chip's glyph follows the session rather than keeping a copy of its own.
     */
    val flashMode: FlashMode

    /**
     * Asks for [mode]. On a session with no flash unit this changes nothing and is logged, rather
     * than reporting a mode the hardware cannot be in.
     */
    fun setFlashMode(mode: FlashMode)

    /**
     * What the bound camera can do for the RAW pipeline (dispatch 2026-10-13-04): RAW output,
     * manual exposure, the exposure-compensation fallback, the sensor's RAW geometry and its
     * physical cameras, each supported, unsupported or unknown with a reason. Read once at bind and
     * logged; `null` before [open] has bound a camera and after [close], as [hasFlashUnit] is false
     * then. Observable. **Nothing a user sees reads it yet**: later stages decide what to show.
     */
    val capabilities: CameraCapabilities?

    /**
     * Whether the phone can encode HEIF and PNG (and has an HEVC video encoder). Read once per
     * process off the main thread, so it lands after [capabilities]; `null` until it has, and after
     * [close]. Observable. Not read by anything a user sees yet.
     */
    val encoderCapabilities: EncoderCapabilities?

    /**
     * The format the bound camera takes a shot in (dispatch 2026-10-15-01): [CameraFormat.Jpg] until a RAW+JPEG bind has
     * succeeded, and again after any RAW bind fails or the camera reports it cannot configure one. This, not the chip's
     * stored choice, is what a shot uses, so the screen never takes a shot the camera is not set up for. Observable.
     */
    val outputFormat: CameraFormat

    /**
     * Asks for [format]. Before [open] it is remembered and the first bind uses it (Amendment 1, item 1: capabilities are
     * read before the first bind, so a remembered RAW binds once). While open, the capture is rebound in this same session,
     * never a new one (a new session froze the picture once, `InAppCameraHost.kt`). A RAW that cannot bind falls back to
     * JPG, logged, and the camera keeps working: never a dead camera.
     */
    fun requestOutputFormat(format: CameraFormat)

    /**
     * Takes one RAW+JPEG shot: the JPG is written to [jpegDestination], as [capture] writes one, and the sensor data comes
     * back in memory, mapped to the DNG writer's input ([RawShot.dng]) or with the reason it could not be
     * ([RawShot.dngUnavailable]). A failure is a shot with no photo at all, as [capture]'s is. Only while [outputFormat] is
     * [CameraFormat.Raw].
     */
    suspend fun captureRaw(jpegDestination: File): Result<RawShot>
}

/**
 * One RAW+JPEG shot, its JPG already on disk. Exactly one of [dng] and [dngUnavailable] is set: the writer's input, or why
 * the camera's metadata could not make one (a value the DNG needs was not reported; never guessed, CLAUDE.md).
 */
internal class RawShot(val dng: DngImage?, val dngUnavailable: String?) {
    init {
        require((dng == null) != (dngUnavailable == null)) { "a RawShot carries either the image or the reason it has none" }
    }
}

/**
 * The flash chip's modes. Torch came first (owner, 2026-09-21): continuous light for gills and
 * pores under a cap. **Auto and On** — flash on capture — joined it in the same chip on 2026-09-26
 * (decision B8 in `docs/audits/2026-09-21-camera-strip-basics-decisions.md`, extending B2).
 *
 * Each mode sets two things: the torch, lit only at [Torch], and the flash the capture fires —
 * none at Off and Torch, the camera's choice at Auto, always at On. Whether CameraX honours a
 * capture flash while the torch is lit is unverified and does not arise: Torch's capture flash is
 * off.
 */
internal enum class FlashMode { Off, Auto, On, Torch }

/** What a tap on the flash chip asks for next: Off, Auto, On, Torch, and back to Off. */
internal fun FlashMode.next(): FlashMode = when (this) {
    FlashMode.Off -> FlashMode.Auto
    FlashMode.Auto -> FlashMode.On
    FlashMode.On -> FlashMode.Torch
    FlashMode.Torch -> FlashMode.Off
}

/**
 * Three states, not a nullable camera. [Unavailable] carries its own reason because "the camera
 * didn't open" is the one the user can sometimes act on (no camera on the device, another app
 * holding it, the permission withdrawn mid-session) and a bare null cannot say which.
 */
internal sealed interface CameraSessionState {
    /** Bound and ready to take a photo. */
    data object Ready : CameraSessionState

    /** The provider is still starting. Normal, and usually brief. */
    data object Opening : CameraSessionState

    /** No camera to bind, with why. [reason] is shown to the user, so it is written for one. */
    data class Unavailable(val reason: String) : CameraSessionState
}
