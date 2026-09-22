package com.zynergylabs.forager.app.photo

import android.content.Context
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.diagnostics.DebugDiagnostics
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The [CameraCaptureSession] backed by CameraX — the **only** file in this project that imports
 * `androidx.camera`. Everything else talks to the interface.
 *
 * ## Nothing here is exercised by any test, and that is structural
 *
 * Robolectric has no camera provider, so every line below runs for the first time on a device. That
 * is not a gap this dispatch chose to leave; it is what a camera is. What the interface buys is
 * that the *screen* around it is fully tested against a fake, so the untested surface is this one
 * file rather than the whole feature. Said plainly here so a green suite is never read as covering
 * it.
 *
 * ## API choices, and what they are chosen against
 *
 * - **`ProcessCameraProvider.getInstance` with a listener**, not the newer suspend `awaitInstance`.
 *   The future-and-listener form has been stable across the whole CameraX 1.x line. Where nothing
 *   can be tested before it ships, the deciding factor is which API is most certain to behave as
 *   expected on the device, not which reads best here.
 * - **`camera-view`'s [PreviewView] inside `AndroidView`**, not `camera-compose`'s
 *   `CameraXViewfinder`, for the same reason. Recorded in `app/build.gradle.kts` beside the
 *   dependency itself.
 * - **[ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY] set explicitly**, though it is also CameraX's
 *   own default. Stated rather than inherited because the whole point of this screen is shot after
 *   shot: a forager photographing a cluster from four angles should not wait on post-processing
 *   between frames, and a later reader should be able to see that was a decision.
 * - **Back camera, falling back to front.** A device with no back camera is rare and real; binding
 *   [CameraSelector.DEFAULT_BACK_CAMERA] unconditionally throws there rather than degrading.
 *
 * ## Orientation comes from the device's sensor, not the display (corrected 2026-09-14)
 *
 * [ImageCapture.setTargetRotation] is set immediately before each shot from an
 * [OrientationEventListener], snapped to a surface rotation with [UseCase.snapToSurfaceRotation].
 * The first version read `PreviewView.display.rotation` instead, and a verification pass found the
 * case that breaks: **with auto-rotate off and the phone held landscape, the display never
 * rotates, so the display read stays at `ROTATION_0` and every landscape photo is tagged
 * portrait.** CameraX's own reference for `setTargetRotation` names this directly — "display
 * orientation may be locked by device default, user setting, or app configuration ... In these
 * cases, set target rotation dynamically according to the android.view.OrientationEventListener"
 * — and the orientation guide's rotation examples all assume target rotation tracks the *device*,
 * which is what the listener reports and the display does not.
 *
 * The display read survives only as the fallback for a device whose listener reports it cannot
 * detect orientation, and for a shot taken before the listener's first reading; both are logged
 * once when they fire (CLAUDE.md: no unlogged fallback). The EXIF orientation tag is what the whole
 * display path reads, and what [scrubPhotoMetadata] deliberately preserves, so getting it wrong
 * here surfaces as sideways photos everywhere downstream.
 *
 * **Device-only, and unverified:** whether the tag comes out right, and specifically the
 * auto-rotate-off landscape case above, which is on the device check by name.
 *
 * ## The target reaches CameraX; the file's tag is the HAL's (device result on `b586195`, 2026-09-15)
 *
 * With "Lock camera to portrait" on and the phone held landscape, the saved photo came out
 * landscape and upright, identical to the setting being off, though [capture] had assigned
 * `ROTATION_0`. Traced hop by hop (window-lock report, addendum on the trace): every hop from the
 * stored setting to `ImageCapture.setTargetRotation` delivers the gated value, and every hop inside
 * CameraX 1.6.2 from the setter to the capture request delivers `JPEG_ORIENTATION` = the sensor's
 * orientation minus the target (90° for a locked shot on a 90° back sensor). What no hop delivers
 * is the *file's* tag: on every device not on CameraX's two-model quirk list, the tag CameraX
 * writes is the one the HAL wrote (`ProcessingInput2Packet.createPacketWithHalRotation`, then
 * `FileUtil.updateFileExif`, which rotates only a tag the HAL left at zero, by a packet rotation
 * that is itself the HAL's tag). A HAL that tags from its own motion sensor rather than from the
 * request produces the device result exactly, in both states of the setting — and every earlier
 * device photo, "upright, as held", is what such a HAL also produces, so none of them had ever
 * shown `targetRotation` to have an effect on this device. Inferred from the reads and the result,
 * not observed; the `Shot:` and orientation-tag log lines below are what the next device run
 * confirms it with.
 *
 * So after CameraX has saved the file, [capture] calls [reapplyIntendedOrientation] with the
 * request's degrees and resolution (both read back from the use case's `resolutionInfo` at the
 * moment of the shot) and the tag is set to what the shot asked for, when the pixels are the
 * unrotated capture frame. Rewritten, kept, declined and failed are each logged, since the rewrite
 * is a fallback firing (CLAUDE.md). The assignment itself is now under test against a real
 * `ImageCapture` (`CameraXCaptureSessionShotRotationTest`), which is why [installImageCapture]
 * and [onDeviceOrientation] are functions rather than the assignment and the listener body they
 * were: the test installs an unbound use case, feeds an orientation, drives [capture], and reads
 * `targetRotation` back from CameraX's own object.
 *
 * ## Opening is the screen's call, not the viewfinder's (deadlock fix, 2026-09-15)
 *
 * The version that shipped fetched the provider, bound the use cases and set `Ready` inside
 * [Viewfinder]'s `DisposableEffect`. The screen composes [Viewfinder] only once `Ready`. So on a
 * device the spinner never went away: the screen was waiting for the state that only the composable
 * it was withholding could produce. Found on the device check; the suite had missed it because its
 * fake defaulted to `Ready`, its slot was a plain `Box`, and the fake's state was a plain `var`
 * with no notion of being produced by opening, so the circularity had nothing to show up in.
 *
 * Now [open] holds the provider fetch, the bind and the orientation listener, and [close] releases
 * them; the screen calls both from its own `DisposableEffect`. [Viewfinder] keeps only the
 * [PreviewView] and the attachment of its surface provider to the already-bound [Preview] — which
 * `Preview.setSurfaceProvider` allows at any time after binding, and which is why the preview can
 * be bound before there is anything to draw it into. The state machine reads: `Opening` from
 * [open] until bound, `Ready` once bound, `Unavailable` on any failure.
 *
 * **Lifecycle edges, each handled here and named so the report can say which were checked:**
 * - *Dismissed while the provider is still resolving.* [close] bumps [openEpoch] and clears
 *   [isOpen]; the provider callback compares its captured epoch and does nothing, so nothing is
 *   bound to a screen that has gone.
 * - *[close] when [open] never completed.* Every field is nullable and the listener's `disable()`
 *   is safe whether or not `enable()` ran; there is nothing to unbind and nothing is.
 * - *Reopen after close.* [PhotoAcquisitionLaunchers] creates a fresh session per open, so in
 *   production this is a new instance. The same instance also reopens cleanly: a new epoch, a new
 *   listener, a fresh fetch and bind.
 * - *Orientation listener exactly once per open.* Created and enabled in [open], disabled and
 *   dropped in [close]; a second [open] without a [close] is refused and logged rather than
 *   stacking a second listener.
 *
 * ## The bound [Camera] is kept
 *
 * `bindToLifecycle` returns the [Camera], and until 2026-09-14 this class discarded it. Nothing
 * reads [camera] yet. It is kept because it is the one object every next feature needs —
 * `cameraControl` for torch and tap-to-focus, `cameraInfo` for `hasFlashUnit` and metering
 * support — and discarding it was the single line that would have forced the bind lambda to be
 * restructured later. Groundwork, recorded as such; the owner's plan of 2026-09-14 names it.
 *
 * ## No location is ever attached
 *
 * `ImageCapture.Metadata` carries an optional `location`, and this never sets one, so a photo taken
 * here has no GPS to strip in the first place. [scrubPhotoMetadata] still runs on persist, and
 * still should: the camera HAL writes make, model and timestamps regardless, and an allowlist that
 * only removes what someone remembered to worry about is the thing that scrub exists not to be.
 */
internal class CameraXCaptureSession(
    private val appContext: Context,
    /** Settings' "Lock camera to portrait": the one gate, see [effectiveDeviceRotation]. */
    private val lockToPortrait: Boolean = false,
) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(CameraSessionState.Opening)
        private set

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var boundProvider: ProcessCameraProvider? = null

    /** The bound camera. Unread today; see the class doc for why it is kept anyway. */
    private var camera: Camera? = null

    private var orientationListener: OrientationEventListener? = null

    /**
     * The latest surface rotation from the orientation listener; `null` until it has reported, or
     * if it cannot. Backed by Compose state since 2026-09-15 so the screen's controls can turn
     * with it ([deviceRotation]); the listener that writes it and the shot that reads it are
     * unchanged.
     */
    private var sensorRotation: Int? by mutableStateOf(null)

    /**
     * **The one gate.** Both the screen's control angle and this class's own [capture] read this,
     * never [sensorRotation] directly, so "Lock camera to portrait" pins one value and both
     * effects — nothing turns, a sideways photo saves portrait — fall out of that one place.
     */
    override val deviceRotation: Int? get() = effectiveDeviceRotation(lockToPortrait, sensorRotation)
    private var loggedDisplayFallback = false

    /**
     * The diagnostics store, or null when there is none to reach. Resolved once — `by lazy`, so the
     * reason is logged at most once per session rather than once per shot, which is the failure mode
     * a per-capture recorder has.
     *
     * A safe cast, deliberately unlike `MainActivity:43` and `TrackRecordingService:110`, which hard-cast
     * `application` to [ForagerApplication]. Those run inside components the platform built from this
     * app's manifest; this runs behind a [Context] handed in by a caller. Today there is one caller and
     * it passes `LocalContext.current.applicationContext` (`InAppCameraHost.kt:49`), so the cast
     * succeeds; a second caller passing something else must degrade to no diagnostics, never take the
     * camera down. Diagnostics are an observation surface, and an observation surface that can break
     * the thing it observes is worse than none.
     *
     * [ForagerApplication.diagnostics] is a `lateinit` assigned in `onCreate` before the container is
     * built, so any session — created by a user action inside a composed screen — necessarily comes
     * after it. Read rather than trusted: the access is guarded, so an ordering this reasoning did not
     * foresee costs the entries, not the shot.
     */
    private val diagnostics: DebugDiagnostics? by lazy { resolveDiagnostics() }

    private fun resolveDiagnostics(): DebugDiagnostics? {
        val application = appContext as? ForagerApplication
        if (application == null) {
            Log.i(TAG, "This session's context is not backed by ForagerApplication; capture entries go to logcat only.")
            return null
        }
        return runCatching { application.diagnostics }.getOrElse { error ->
            Log.i(TAG, "Diagnostics were not installed when this session resolved them; capture entries go to logcat only.", error)
            null
        }
    }

    /** Bumped by every [open] and [close]; a provider callback whose captured epoch has moved on does nothing. */
    private var openEpoch = 0
    private var isOpen = false

    override fun open(lifecycleOwner: LifecycleOwner) {
        if (isOpen) {
            Log.w(TAG, "open() on a session that is already open; ignored rather than binding twice.")
            return
        }
        isOpen = true
        val epoch = ++openEpoch
        state = CameraSessionState.Opening

        val listener = object : OrientationEventListener(appContext) {
            override fun onOrientationChanged(orientation: Int) = onDeviceOrientation(orientation)
        }
        orientationListener = listener
        if (listener.canDetectOrientation()) {
            listener.enable()
        } else {
            Log.i(TAG, "This device reports no orientation sensor; capture rotation will follow the display instead.")
        }

        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener(
            {
                if (!isOpen || epoch != openEpoch) {
                    Log.i(TAG, "The camera provider resolved after this session was closed; not binding.")
                    return@addListener
                }
                val provider = runCatching { future.get() }.getOrElse { error ->
                    Log.w(TAG, "The camera provider did not start.", error)
                    state = CameraSessionState.Unavailable("The camera could not be started on this device.")
                    return@addListener
                }
                val selector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> null
                }
                if (selector == null) {
                    state = CameraSessionState.Unavailable("This device has no camera available.")
                    return@addListener
                }
                runCatching {
                    val newPreview = Preview.Builder().build()
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    val boundCamera = provider.bindToLifecycle(lifecycleOwner, selector, newPreview, capture)
                    Triple(newPreview, capture, boundCamera)
                }.fold(
                    onSuccess = { (newPreview, capture, boundCamera) ->
                        preview = newPreview
                        installImageCapture(capture)
                        camera = boundCamera
                        boundProvider = provider
                        // The screen composes the viewfinder only once Ready, so normally there is
                        // no view yet and the attachment happens in Viewfinder. If one exists, attach now.
                        previewView?.let { newPreview.setSurfaceProvider(it.surfaceProvider) }
                        state = CameraSessionState.Ready
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Binding the camera failed.", error)
                        state = CameraSessionState.Unavailable("The camera is in use by another app, or could not be bound.")
                    },
                )
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    override fun close() {
        if (!isOpen) return // never opened, never completed, or already closed: nothing to release
        isOpen = false
        openEpoch++
        orientationListener?.disable()
        orientationListener = null
        sensorRotation = null
        preview?.setSurfaceProvider(null)
        boundProvider?.unbindAll()
        boundProvider = null
        camera = null
        imageCapture = null
        preview = null
        state = CameraSessionState.Opening
    }

    /**
     * What the orientation listener delivers — degrees, 0 to 359, or
     * [OrientationEventListener.ORIENTATION_UNKNOWN] — snapped to a surface rotation. Its own
     * function so a test can hand the session a device orientation without a sensor
     * (`CameraXCaptureSessionShotRotationTest`); the listener in [open] is a one-line delegate.
     */
    internal fun onDeviceOrientation(orientationDegrees: Int) {
        if (orientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) return
        sensorRotation = UseCase.snapToSurfaceRotation(orientationDegrees)
    }

    /**
     * The one place the bound [ImageCapture] is installed. The bind in [open] calls it, and so does
     * the shot-rotation test with a real, unbound [ImageCapture], whose `targetRotation` then reads
     * back what [capture] assigned; that test is why this is a function and not an assignment.
     */
    internal fun installImageCapture(capture: ImageCapture) {
        imageCapture = capture
    }

    override suspend fun capture(destination: File): Result<Unit> {
        val capture = imageCapture
            ?: return Result.failure(IllegalStateException("The camera is not ready yet."))
        // Per shot, from the device's own orientation — see the class doc for why not the display.
        // Read through the gate (deviceRotation), not the field: see effectiveDeviceRotation.
        val rotation = deviceRotation ?: previewView?.display?.rotation?.also {
            if (!loggedDisplayFallback) {
                loggedDisplayFallback = true
                Log.i(TAG, "No orientation reading yet; this shot's rotation comes from the display, which is wrong if auto-rotate is off.")
            }
        }
        rotation?.let { capture.targetRotation = it }
        // What CameraX will ask the HAL for, read back from the use case at the moment of the shot:
        // the request's degrees and the frame size the saved JPEG must match for its tag to be
        // reapplied below. Null until bound. Logged per shot: this line is what the device check
        // reads to tell "the target never reached CameraX" from "the HAL tagged it otherwise".
        val intended = capture.resolutionInfo
        // The window's rotation at the shot, beside the sensor's: the controls' angle is sensor
        // minus display and the arrangement is display alone, so a shot entry that shows only the
        // sensor cannot show the one disagreement every orientation question comes down to. Added
        // 2026-09-18 for the S26 Ultra inverted-glyph regression, and kept: this is the instrument.
        val displayRotation = previewView?.display?.rotation
        Log.i(
            TAG,
            "Shot: deviceRotation=$rotation displayRotation=$displayRotation targetRotation=${capture.targetRotation} " +
                "requestDegrees=${intended?.rotationDegrees} resolution=${intended?.resolution}",
        )
        diagnostics?.recordCaptureShot(
            deviceRotation = rotation,
            displayRotation = displayRotation,
            targetRotation = capture.targetRotation,
            requestDegrees = intended?.rotationDegrees,
            resolution = intended?.resolution?.toString(),
        )

        val saved = suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(destination).build(),
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        continuation.resume(Result.success(Unit))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "A capture failed; no file was written for it.", exception)
                        continuation.resume(Result.failure(exception))
                    }
                },
            )
        }
        if (saved.isFailure) {
            // The second entry this shot owes. Without it a failed capture leaves a lone Shot: line,
            // which reads as a dropped record rather than as the distinct path it is.
            diagnostics?.recordCaptureOrientation(
                fileName = destination.name,
                branch = "not attempted",
                reason = "the capture failed and no file was written",
                error = saved.exceptionOrNull(),
            )
            return saved
        }

        // The file's tag is the HAL's, not the request's — see the class doc. Put the request's
        // tag on it, when the pixels are the unrotated capture frame. Off the main thread: it reads
        // and may rewrite the file, and debug builds run StrictMode.
        if (intended == null) {
            Log.w(TAG, "No resolution info for this shot; '${destination.name}' keeps the HAL's orientation tag.")
            diagnostics?.recordCaptureOrientation(
                fileName = destination.name,
                branch = "not attempted",
                reason = "no resolution info for this shot; it keeps the HAL's tag",
            )
            return saved
        }
        val degrees = intended.rotationDegrees
        when (val outcome = withContext(Dispatchers.IO) { reapplyIntendedOrientation(destination, degrees, intended.resolution) }) {
            is OrientationReapplyOutcome.Rewritten -> {
                Log.i(TAG, "Orientation tag of '${destination.name}' rewritten: the HAL wrote ${outcome.fromTag}, the shot asked for ${outcome.toTag} ($degrees°).")
                diagnostics?.recordCaptureOrientation(destination.name, "rewritten", fromTag = outcome.fromTag, toTag = outcome.toTag, degrees = degrees)
            }
            is OrientationReapplyOutcome.Kept -> {
                Log.i(TAG, "Orientation tag of '${destination.name}' is ${outcome.tag}, already the shot's $degrees°.")
                diagnostics?.recordCaptureOrientation(destination.name, "kept", fromTag = outcome.tag, degrees = degrees)
            }
            is OrientationReapplyOutcome.Declined -> {
                Log.i(TAG, "Orientation tag of '${destination.name}' left as CameraX wrote it (${outcome.tag}): ${outcome.reason}.")
                diagnostics?.recordCaptureOrientation(destination.name, "declined", fromTag = outcome.tag, degrees = degrees, reason = outcome.reason)
            }
            is OrientationReapplyOutcome.Failed -> {
                Log.w(TAG, "Couldn't reapply the orientation tag to '${destination.name}'; it keeps the HAL's.", outcome.error)
                diagnostics?.recordCaptureOrientation(destination.name, "failed", degrees = degrees, error = outcome.error)
            }
        }
        return saved
    }

    // Not wired to CameraX yet: no unit reported, the mode stays Off, and a request says so.
    override val hasFlashUnit: Boolean get() = false
    override val flashMode: FlashMode get() = FlashMode.Off
    override fun setFlashMode(mode: FlashMode) {
        Log.w(TAG, "setFlashMode($mode) is unsupported: flash is not wired to CameraX yet.")
    }

    /**
     * The viewfinder: a [PreviewView], and the attachment of its surface provider to the [Preview]
     * that [open] bound. Nothing else — no fetching, no binding, no listener — since the deadlock
     * fix; see the class doc. A concrete member rather than part of [CameraCaptureSession]: a
     * `@Composable` on the interface would force every fake to be one too, and the screen takes its
     * viewfinder as a slot precisely so a test can pass a plain `Box`.
     */
    @Composable
    fun Viewfinder(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val view = remember { PreviewView(context) }

        DisposableEffect(view) {
            previewView = view
            val bound = preview
            if (bound == null) {
                // Ready gates this composable, so a bound Preview should exist. Logged, not
                // assumed: if it ever happens, this is where the spinner-forever bug would hide.
                Log.w(TAG, "Viewfinder composed before the camera was bound; nothing to draw into it yet.")
            } else {
                bound.setSurfaceProvider(view.surfaceProvider)
            }
            onDispose {
                preview?.setSurfaceProvider(null)
                previewView = null
            }
        }

        AndroidView(factory = { view }, modifier = modifier)
    }

    private companion object {
        const val TAG = "CameraXCaptureSession"
    }
}

/**
 * The device rotation the camera acts on — for the controls' angle and for each shot's
 * `targetRotation` alike. With "Lock camera to portrait" off it is the sensor's reading (`null`
 * until there is one); on, it is `Surface.ROTATION_0` and the sensor is ignored. One function so
 * the setting is one gate: there is no second place where either effect could be decided
 * differently. Pure, and tested as such.
 */
internal fun effectiveDeviceRotation(lockToPortrait: Boolean, sensorRotation: Int?): Int? =
    if (lockToPortrait) Surface.ROTATION_0 else sensorRotation
