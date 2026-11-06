package com.zynergylabs.forager.app.photo

import android.content.Context
import android.graphics.ImageFormat
import androidx.camera.core.CameraState
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Observer
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.RawFrameMapping
import com.zynergylabs.forager.app.domain.mapRawFrame
import com.zynergylabs.forager.app.domain.rawCaptureSize
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFacing
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.domain.mapCameraCapabilities
import com.zynergylabs.forager.app.domain.mapEncoderCapabilities
import com.zynergylabs.forager.app.domain.toLogLine
import com.zynergylabs.forager.app.domain.toLogLines
import com.zynergylabs.forager.app.ui.log.currentWindowIsFullScreen
import com.zynergylabs.forager.app.ui.log.viewfinderInNaturalFrame
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The [CameraCaptureSession] backed by CameraX — one of the **two** files in this project that
 * import `androidx.camera`, with `CameraXCapabilityReader.kt` beside it (since dispatch
 * 2026-10-13-04, which keeps the RAW probe's reads and its experimental opt-in out of this file).
 * Everything else talks to the interface.
 *
 * ## The capability probe (RAW pipeline stage 1, dispatch 2026-10-13-04)
 *
 * After the bind reaches `Ready`, [probeCapabilities] reads what the bound camera can do, logs it
 * under [CAMERA_PROBE_LOG_TAG] and exposes it as [capabilities]; the front camera, when the bound
 * one is the back, is read unbound and only logged; and the encoders are read once per process
 * on a background dispatcher and logged when they land, with the same probe id and facing so the
 * lines can be matched (planner, 2026-10-14). The probe runs after `Ready` and inside its own
 * catch, so nothing it does can stop the camera opening. Nothing a user sees reads its result.
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
 * `bindToLifecycle` returns the [Camera], and until 2026-09-14 this class discarded it. It is kept
 * because it is the one object every next feature needs — `cameraControl` for torch and
 * tap-to-focus, `cameraInfo` for `hasFlashUnit` and metering support. Since 2026-09-21 the bind
 * hands its `hasFlashUnit()` and `enableTorch` to [installFlash]; tap-to-focus and metering are
 * still to come.
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
) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(CameraSessionState.Opening)
        private set

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var boundProvider: ProcessCameraProvider? = null

    /** The bound camera. Its torch and flash unit are read once, at bind, by [installFlash]; see the class doc. */
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
     * The sensor's reading, as the screen's control angle and this class's own [capture] both read it.
     * Until dispatch 2026-10-13-03 this went through a gate, `effectiveDeviceRotation`, that pinned it
     * to `ROTATION_0` with "Lock camera to portrait" on; the option and the gate were removed together
     * (RECORD -968), and the off branch was already this pass-through.
     */
    override val deviceRotation: Int? get() = sensorRotation
    private var loggedDisplayFallback = false

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
        probeScope = MainScope()

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
                val facing = if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraFacing.Front else CameraFacing.Back
                // RAW stage 2 (Amendment 1, item 1): a remembered RAW is checked against the camera before the first
                // bind, in its own catch, so it binds once; anything short of a yes binds JPG, logged.
                val wantRaw = requestedFormat == CameraFormat.Raw && rawBindableBeforeBind(provider, selector, facing)
                val newPreview = runCatching { Preview.Builder().build() }
                runCatching {
                    val previewUseCase = newPreview.getOrThrow()
                    runCatching { bindWithCapture(provider, lifecycleOwner, selector, previewUseCase, raw = wantRaw) }
                        .recoverCatching { error ->
                            if (!wantRaw) throw error
                            // Never a dead camera (dispatch abort condition): a RAW+JPEG capture that will not bind beside
                            // the preview gives way to today's JPG capture.
                            Log.w(TAG, "RAW+JPEG would not bind beside the preview; binding JPG instead.", error)
                            bindWithCapture(provider, lifecycleOwner, selector, previewUseCase, raw = false)
                        }.getOrThrow()
                }.fold(
                    onSuccess = { (capture, boundCamera, boundFormat) ->
                        preview = newPreview.getOrThrow()
                        installImageCapture(capture)
                        camera = boundCamera
                        boundLifecycleOwner = lifecycleOwner
                        boundSelector = selector
                        outputFormat = boundFormat
                        watchForStreamConfigErrors(boundCamera, lifecycleOwner)
                        installFlash(boundCamera.cameraInfo.hasFlashUnit()) { on -> enableTorch(boundCamera, on) }
                        boundProvider = provider
                        // The screen composes the viewfinder only once Ready, so normally there is
                        // no view yet and the attachment happens in Viewfinder. If one exists, attach now.
                        previewView?.let { view -> preview?.setSurfaceProvider(view.surfaceProvider) }
                        state = CameraSessionState.Ready
                        runCatching { probeCapabilities(provider, boundCamera, facing) }.onFailure { error ->
                            Log.w(CAMERA_PROBE_LOG_TAG, "The capability probe failed; the camera is unaffected and nothing is reported.", error)
                        }
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
        // Torch off before anything is unbound, and the flash state reset whether or not open
        // completed: torch does not persist past the camera closing (owner, 2026-09-21) and
        // nothing stores it. Unbinding would douse the LED anyway; asking first means the chip's
        // state and the camera's agree at every step rather than only at the end.
        if (currentFlashMode == FlashMode.Torch) torch?.invoke(false)
        torch = null
        boundHasFlashUnit = false
        currentFlashMode = FlashMode.Off
        capabilities = null
        encoderCapabilities = null
        probeScope?.cancel()
        probeScope = null
        if (!isOpen) return // never opened, never completed, or already closed: nothing to release
        isOpen = false
        openEpoch++
        stopWatchingStreamConfig()
        outputFormat = CameraFormat.Jpg
        boundLifecycleOwner = null
        boundSelector = null
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

    /**
     * Installs a [Preview] as [open]'s bind would. For the viewfinder-turn test (dispatch 2026-10-17-08), which needs a
     * real, unbound [Preview] for [Viewfinder] to attach its surface provider to; CameraX's camera does not run under
     * Robolectric.
     */
    internal fun installPreview(preview: Preview) {
        this.preview = preview
    }

    /**
     * How many times [Viewfinder] has attached its surface provider to the bound [Preview] in this session's life. On a
     * device each attachment after the first is a new surface request, and the camera rebuilds its session (the black
     * flash of dispatch 2026-10-17-08), so a turn must not add one. Read by tests only.
     */
    internal var viewfinderSurfaceAttachments: Int = 0
        private set

    /** The viewfinder's `PreviewView` mode while composed, `null` otherwise. Read by tests only. */
    internal val viewfinderImplementationMode: PreviewView.ImplementationMode?
        get() = previewView?.implementationMode

    override suspend fun capture(destination: File): Result<Unit> {
        val capture = imageCapture
            ?: return Result.failure(IllegalStateException("The camera is not ready yet."))
        // Per shot, from the device's own orientation — see the class doc for why not the display.
        // Read through deviceRotation, the one value the controls' angle reads too.
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
        if (saved.isFailure) return saved

        // The file's tag is the HAL's, not the request's — see the class doc. Put the request's
        // tag on it, when the pixels are the unrotated capture frame. Off the main thread: it reads
        // and may rewrite the file.
        if (intended == null) {
            Log.w(TAG, "No resolution info for this shot; '${destination.name}' keeps the HAL's orientation tag.")
            return saved
        }
        val degrees = intended.rotationDegrees
        when (val outcome = withContext(Dispatchers.IO) { reapplyIntendedOrientation(destination, degrees, intended.resolution) }) {
            is OrientationReapplyOutcome.Rewritten -> {
                Log.i(TAG, "Orientation tag of '${destination.name}' rewritten: the HAL wrote ${outcome.fromTag}, the shot asked for ${outcome.toTag} ($degrees°).")
            }
            is OrientationReapplyOutcome.Kept -> {
                Log.i(TAG, "Orientation tag of '${destination.name}' is ${outcome.tag}, already the shot's $degrees°.")
            }
            is OrientationReapplyOutcome.Declined -> {
                Log.i(TAG, "Orientation tag of '${destination.name}' left as CameraX wrote it (${outcome.tag}): ${outcome.reason}.")
            }
            is OrientationReapplyOutcome.Failed -> {
                Log.w(TAG, "Couldn't reapply the orientation tag to '${destination.name}'; it keeps the HAL's.", outcome.error)
            }
        }
        return saved
    }

    // ── RAW stage 2 (dispatch 2026-10-15-01, as amended by RECORD -1009 and -1011) ───────────────────────────────────

    /** What the next bind, or a rebind now, should use; see [CameraCaptureSession.requestOutputFormat]. Not stored: the chip's choice is (RECORD -1005). */
    private var requestedFormat: CameraFormat = CameraFormat.Jpg

    override var outputFormat: CameraFormat by mutableStateOf(CameraFormat.Jpg)
        private set

    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundSelector: CameraSelector? = null

    /** The capture results the RAW frames are matched with; one per RAW capture use case. */
    private var rawResults: CaptureResultCollector? = null

    /** The in-memory RAW callback copies a 12 MP frame; off the main thread, one shot at a time. */
    private val rawCallbackExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    private var cameraStateObserver: Observer<CameraState>? = null
    private var observedCamera: Camera? = null

    override fun requestOutputFormat(format: CameraFormat) {
        requestedFormat = format
        val provider = boundProvider ?: return // not bound yet: the next bind uses it
        if (format == outputFormat) return
        val raw = format == CameraFormat.Raw && rawCaptureSize(capabilities) != null
        if (format == CameraFormat.Raw && !raw) {
            Log.w(TAG, "RAW asked for, but this camera's probe does not offer it within the operating limit; staying on JPG.")
            return
        }
        rebindCapture(provider, raw)
    }

    /**
     * Swaps the capture use case in this same session: the preview stays bound and attached, which is why the owner's
     * accepted cost is a single blink, not a new camera (Amendment 1, item 2). A RAW rebind that fails gives way to JPG; a
     * JPG rebind that fails leaves the camera unavailable with its reason, never silently capture-less.
     */
    private fun rebindCapture(provider: ProcessCameraProvider, raw: Boolean) {
        val owner = boundLifecycleOwner ?: return
        val selector = boundSelector ?: return
        imageCapture?.let { old -> runCatching { provider.unbind(old) }.onFailure { Log.w(TAG, "Unbinding the old capture failed.", it) } }
        imageCapture = null
        val bound = runCatching { bindCaptureOnly(provider, owner, selector, raw) }
            .recoverCatching { error ->
                if (!raw) throw error
                Log.w(TAG, "RAW+JPEG would not bind beside the preview; back to JPG.", error)
                bindCaptureOnly(provider, owner, selector, raw = false)
            }
        bound.fold(
            onSuccess = { (capture, format) ->
                installImageCapture(capture)
                capture.flashMode = currentFlashMode.imageCaptureFlashMode()
                outputFormat = format
                Log.i(TAG, "Capture rebound as $format.")
            },
            onFailure = { error ->
                Log.w(TAG, "Rebinding the capture failed; the camera cannot take photos.", error)
                outputFormat = CameraFormat.Jpg
                state = CameraSessionState.Unavailable("The camera is in use by another app, or could not be bound.")
            },
        )
    }

    /** The first bind: the preview and one capture, RAW+JPEG or today's JPG. Returns the capture, the camera and the format bound. */
    private fun bindWithCapture(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        selector: CameraSelector,
        preview: Preview,
        raw: Boolean,
    ): Triple<ImageCapture, Camera, CameraFormat> {
        val (capture, format) = newCaptureUseCase(raw)
        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(owner, selector, preview, capture)
        return Triple(capture, boundCamera, format)
    }

    /** A capture alone, beside the preview that is already bound. */
    private fun bindCaptureOnly(provider: ProcessCameraProvider, owner: LifecycleOwner, selector: CameraSelector, raw: Boolean): Pair<ImageCapture, CameraFormat> {
        val (capture, format) = newCaptureUseCase(raw)
        provider.bindToLifecycle(owner, selector, capture)
        return capture to format
    }

    private fun newCaptureUseCase(raw: Boolean): Pair<ImageCapture, CameraFormat> =
        if (raw) {
            val results = CaptureResultCollector()
            rawResults = results
            buildRawJpegImageCapture(results) to CameraFormat.Raw
        } else {
            rawResults = null
            jpegImageCapture() to CameraFormat.Jpg
        }

    /** Whether the camera [selector] names offers RAW within the operating limit, read before binding. Any failure is a no, logged. */
    private fun rawBindableBeforeBind(provider: ProcessCameraProvider, selector: CameraSelector, facing: CameraFacing): Boolean =
        runCatching { rawCaptureSize(mapCameraCapabilities(readCameraCapabilities(provider.getCameraInfo(selector), facing))) != null }
            .onFailure { Log.w(TAG, "Couldn't read the camera's RAW support before binding; binding JPG.", it) }
            .getOrDefault(false)
            .also { ok -> if (!ok) Log.i(TAG, "RAW was asked for but this camera does not offer it within the operating limit; binding JPG.") }

    /**
     * A camera that cannot configure the RAW+JPEG streams reports it after the bind has returned
     * (`CameraState.ERROR_STREAM_CONFIG`, read from the 1.6.2 artifact). Then the capture goes back to JPG, logged: never a
     * dead camera. Whether a phone ever does this is device-only.
     */
    private fun watchForStreamConfigErrors(boundCamera: Camera, owner: LifecycleOwner) {
        stopWatchingStreamConfig()
        val observer = Observer<CameraState> { cameraState ->
            if (cameraState.error?.code == CameraState.ERROR_STREAM_CONFIG && outputFormat == CameraFormat.Raw) {
                Log.w(TAG, "The camera could not configure the RAW+JPEG streams; back to JPG.")
                boundProvider?.let { rebindCapture(it, raw = false) }
            }
        }
        boundCamera.cameraInfo.cameraState.observe(owner, observer)
        cameraStateObserver = observer
        observedCamera = boundCamera
    }

    private fun stopWatchingStreamConfig() {
        val observer = cameraStateObserver ?: return
        observedCamera?.cameraInfo?.cameraState?.removeObserver(observer)
        cameraStateObserver = null
        observedCamera = null
    }

    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> {
        val capture = imageCapture
            ?: return Result.failure(IllegalStateException("The camera is not ready yet."))
        val results = rawResults
        val boundCamera = camera
        if (outputFormat != CameraFormat.Raw || results == null || boundCamera == null) {
            return Result.failure(IllegalStateException("The camera is not set up for RAW."))
        }
        // The shot's rotation, exactly as capture() sets it: the device's own orientation, the display only as a fallback.
        val rotation = deviceRotation ?: previewView?.display?.rotation?.also {
            if (!loggedDisplayFallback) {
                loggedDisplayFallback = true
                Log.i(TAG, "No orientation reading yet; this shot's rotation comes from the display, which is wrong if auto-rotate is off.")
            }
        }
        rotation?.let { capture.targetRotation = it }
        val intended = capture.resolutionInfo
        Log.i(TAG, "RAW shot: deviceRotation=$rotation targetRotation=${capture.targetRotation} requestDegrees=${intended?.rotationDegrees} resolution=${intended?.resolution}")

        val plane = suspendCancellableCoroutine<Result<RawPlane>> { continuation ->
            var raw: RawPlane? = null
            var jpegWritten = false
            var finished = false
            fun finish(result: Result<RawPlane>) {
                if (finished) return
                finished = true
                if (continuation.isActive) continuation.resume(result)
            }
            capture.takePicture(
                rawCallbackExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            when (image.format) {
                                ImageFormat.RAW_SENSOR -> raw = copyRawPlane(image)
                                ImageFormat.JPEG -> {
                                    writeJpegImage(image, jpegDestination)
                                    jpegWritten = true
                                }
                                else -> error("an image of format ${image.format} in a RAW+JPEG shot")
                            }
                        } catch (error: Exception) {
                            Log.w(TAG, "A RAW+JPEG shot's image could not be kept.", error)
                            finish(Result.failure(error))
                        } finally {
                            image.close()
                        }
                        val frame = raw
                        if (frame != null && jpegWritten) finish(Result.success(frame))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "A RAW+JPEG capture failed; no photo was kept for it.", exception)
                        finish(Result.failure(exception))
                    }
                },
            )
        }
        val frame = plane.getOrElse { return Result.failure(it) }

        // The JPG gets the shot's intended orientation exactly as capture()'s does, and the DNG gets the JPG's final tag
        // (Amendment 1, item 4), when the JPG's pixels are the unrotated frame the RAW is in.
        val dngOrientation = withContext(Dispatchers.IO) {
            if (intended == null) {
                Log.w(TAG, "No resolution info for this RAW shot; the JPG keeps the HAL's tag and the DNG is written upright.")
                ExifInterface.ORIENTATION_NORMAL
            } else {
                rawOrientationFor(reapplyIntendedOrientation(jpegDestination, intended.rotationDegrees, intended.resolution), intended.rotationDegrees)
            }
        }

        val result = results.await(frame.timestampNanos)
        if (result == null) {
            return Result.success(RawShot(dng = null, dngUnavailable = "no capture result arrived for the RAW frame at ${frame.timestampNanos}"))
        }
        val mapping = withContext(Dispatchers.Default) {
            runCatching { mapRawFrame(readRawFrame(boundCamera.cameraInfo, frame, result, dngOrientation)) }
        }.getOrElse { error -> RawFrameMapping.Unsupported("reading the frame's metadata threw ${error::class.java.simpleName}: ${error.message}") }
        return Result.success(
            when (mapping) {
                is RawFrameMapping.Mapped -> {
                    if (mapping.notes.isNotEmpty()) Log.i(TAG, "RAW shot's DNG input: ${mapping.notes.joinToString("; ")}.")
                    RawShot(dng = mapping.image, dngUnavailable = null)
                }
                is RawFrameMapping.Unsupported -> RawShot(dng = null, dngUnavailable = mapping.reason)
            },
        )
    }

    private var boundHasFlashUnit: Boolean by mutableStateOf(false)
    override val hasFlashUnit: Boolean get() = boundHasFlashUnit

    private var currentFlashMode: FlashMode by mutableStateOf(FlashMode.Off)
    override val flashMode: FlashMode get() = currentFlashMode

    /** Lights or douses the bound camera's torch; null while no camera is bound. See [installFlash]. */
    private var torch: ((on: Boolean) -> Unit)? = null

    override fun setFlashMode(mode: FlashMode) {
        val switch = torch
        if (!boundHasFlashUnit || switch == null) {
            Log.w(TAG, "setFlashMode($mode) ignored: no flash unit on a bound camera.")
            return
        }
        switch(mode == FlashMode.Torch)
        // The capture flash, on the installed use case; null before the bind, which cannot be
        // reached here (installFlash runs after installImageCapture, and without it this returned
        // above). No mode is carried into a later bind: close resets to Off, a fresh ImageCapture
        // is already FLASH_MODE_OFF (decision B8, planner ruling 3).
        imageCapture?.flashMode = mode.imageCaptureFlashMode()
        currentFlashMode = mode
    }

    /** Decision B's table, the ImageCapture column. Torch lights the torch and fires no capture flash. */
    private fun FlashMode.imageCaptureFlashMode(): Int = when (this) {
        FlashMode.Off, FlashMode.Torch -> ImageCapture.FLASH_MODE_OFF
        FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.On -> ImageCapture.FLASH_MODE_ON
    }

    /**
     * The one place the bound camera's flash is installed: [open]'s bind calls it with
     * `cameraInfo.hasFlashUnit()` and a torch that calls `cameraControl.enableTorch`, and
     * `CameraXCaptureSessionFlashTest` calls it with a torch that records what it was asked. A
     * function rather than two assignments for the same reason as [installImageCapture].
     */
    internal fun installFlash(hasFlashUnit: Boolean, torch: (on: Boolean) -> Unit) {
        boundHasFlashUnit = hasFlashUnit
        this.torch = torch
    }

    /**
     * `enableTorch` on the bound camera, with its result read rather than dropped. A failure is
     * logged and the mode resynced from the camera's own torch state, so the chip never shows a
     * torch the camera is not running. A cancellation is not a failure: CameraX cancels a pending
     * `enableTorch` when a newer one arrives, and the newer one decides.
     */
    private fun enableTorch(camera: Camera, on: Boolean) {
        val future = camera.cameraControl.enableTorch(on)
        future.addListener(
            {
                runCatching { future.get() }.onFailure { error ->
                    if (error.cause is CameraControl.OperationCanceledException) {
                        Log.i(TAG, "enableTorch($on) superseded by a later request.")
                        return@onFailure
                    }
                    val lit = camera.cameraInfo.torchState.value == TorchState.ON
                    Log.w(TAG, "enableTorch($on) failed; the torch is ${if (lit) "on" else "off"}, and the chip now says so.", error)
                    onTorchRequestFailed(lit)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    /**
     * What a failed `enableTorch` leaves reported: the mode matches the hardware. A lit torch is
     * Torch, whatever was asked, with Torch's capture flash (off). An unlit torch contradicts only
     * Torch, which becomes Off; Off, Auto and On all have the torch off, so they stand — a
     * successful change to Auto or On is never overwritten by this. The listener in
     * [enableTorch] delegates here so the rule is testable without a bound camera.
     */
    internal fun onTorchRequestFailed(lit: Boolean) {
        val resynced = when {
            lit -> FlashMode.Torch
            currentFlashMode == FlashMode.Torch -> FlashMode.Off
            else -> currentFlashMode
        }
        imageCapture?.flashMode = resynced.imageCaptureFlashMode()
        currentFlashMode = resynced
    }

    override var capabilities: CameraCapabilities? by mutableStateOf(null)
        private set

    override var encoderCapabilities: EncoderCapabilities? by mutableStateOf(null)
        private set

    /** Created by [open], cancelled by [close]: the wait for the encoder line, so a closed session logs nothing more. */
    private var probeScope: CoroutineScope? = null

    /**
     * The probe at bind (class doc). The bound camera first, then the front camera unbound when the
     * bound one is the back and a front camera exists, then the wait for the encoders.
     */
    private fun probeCapabilities(provider: ProcessCameraProvider, boundCamera: Camera, facing: CameraFacing) {
        val probeId = nextProbeId.incrementAndGet()
        installCapabilities(probeId, mapCameraCapabilities(readCameraCapabilities(boundCamera.cameraInfo, facing)))
        if (facing == CameraFacing.Back) {
            runCatching {
                if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    val front = mapCameraCapabilities(readCameraCapabilities(provider.getCameraInfo(CameraSelector.DEFAULT_FRONT_CAMERA), CameraFacing.Front))
                    front.toLogLines(probeId, bound = false).forEach { Log.i(CAMERA_PROBE_LOG_TAG, it) }
                } else {
                    Log.i(CAMERA_PROBE_LOG_TAG, "probe=$probeId facing=front unbound: no front camera")
                }
            }.onFailure { error ->
                Log.i(CAMERA_PROBE_LOG_TAG, "probe=$probeId facing=front unbound: unknown(the front camera could not be read: ${error::class.java.simpleName}: ${error.message})")
            }
        }
        probeScope?.launch {
            // Every read inside is already caught, so this should not throw; if it ever does, it is
            // logged here rather than reaching the uncaught-exception handler from a launch.
            val encoders = try {
                processEncoderProbe.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(CAMERA_PROBE_LOG_TAG, "probe=$probeId facing=${facing.name.lowercase()} encoders: unknown(the encoder probe failed)", error)
                return@launch
            }
            installEncoders(probeId, facing, encoders)
        }
    }

    /**
     * The one place a camera's probe result is installed and logged: [probeCapabilities] calls it
     * at bind, and `CameraXCaptureSessionCapabilitiesTest` calls it directly, since Robolectric has no
     * camera to bind. A function for the same reason as [installFlash].
     */
    internal fun installCapabilities(probeId: Int, probed: CameraCapabilities) {
        capabilities = probed
        probed.toLogLines(probeId, bound = true).forEach { Log.i(CAMERA_PROBE_LOG_TAG, it) }
    }

    /** As [installCapabilities], for the encoder result that lands later from the once-per-process read. */
    internal fun installEncoders(probeId: Int, facing: CameraFacing, probed: EncoderCapabilities) {
        encoderCapabilities = probed
        Log.i(CAMERA_PROBE_LOG_TAG, probed.toLogLine(probeId, facing))
    }

    /**
     * The viewfinder: a [PreviewView], and the attachment of its surface provider to the [Preview]
     * that [open] bound. Nothing else — no fetching, no binding, no listener — since the deadlock
     * fix; see the class doc. A concrete member rather than part of [CameraCaptureSession]: a
     * `@Composable` on the interface would force every fake to be one too, and the screen takes its
     * viewfinder as a slot precisely so a test can pass a plain `Box`.
     */
    @Composable
    fun Viewfinder(
        modifier: Modifier = Modifier,
        /**
         * The camera's content is held in the device's natural frame inside a turned window (`NaturalOrientationHold`,
         * RECORD -985). Then the picture must turn with that layer, and must be laid out for the natural frame:
         *
         * - **`COMPATIBLE` mode, a `TextureView`**, because a `SurfaceView` is its own surface and does not follow a
         *   Compose layer's turn.
         * - **`Preview.targetRotation` = `ROTATION_0`**, the natural frame. Read in camera-view 1.6.2 (sources jar):
         *   with a target rotation specified and a `TextureView`, `PreviewView` does **not** substitute the display's
         *   rotation (`PreviewView.java:231-239`, `mUseDisplayRotation` false), and its `TextureView` correction is
         *   `-target` (`PreviewTransformation.java:182-193`), here 0. So it does not correct for the turned display a
         *   second time; the layer's turn is the only one.
         *
         * Not held (every phone, and split screen), nothing here changes: `PERFORMANCE`, and CameraX's own target
         * rotation. Leaving the hold puts back `PERFORMANCE` and the display's rotation. Whether the picture is the
         * right way up on a tablet is device-only: CameraX does not run under Robolectric.
         */
        held: Boolean = false,
    ) {
        val context = LocalContext.current
        val view = remember { PreviewView(context) }
        // Keyed on whether this window could be held, not on whether it is held right now: the second flips on every
        // portrait-landscape turn of a tablet, and each flip swapped the PreviewView's surface and restarted the camera
        // (dispatch 2026-10-17-08). See viewfinderInNaturalFrame.
        val naturalFrame = viewfinderInNaturalFrame(
            held = held,
            fullScreen = currentWindowIsFullScreen(),
            smallestScreenWidthDp = LocalConfiguration.current.smallestScreenWidthDp,
        )

        DisposableEffect(view, naturalFrame) {
            previewView = view
            val bound = preview
            if (bound == null) {
                // Ready gates this composable, so a bound Preview should exist. Logged, not
                // assumed: if it ever happens, this is where the spinner-forever bug would hide.
                Log.w(TAG, "Viewfinder composed before the camera was bound; nothing to draw into it yet.")
            } else {
                if (naturalFrame) {
                    // Before the surface provider: the mode is read when the next surface is requested.
                    view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    bound.targetRotation = Surface.ROTATION_0
                    Log.i(TAG, "Viewfinder in the natural frame: COMPATIBLE, Preview targetRotation=ROTATION_0, held=$held, display=${view.display?.rotation}.")
                }
                bound.setSurfaceProvider(view.surfaceProvider)
                viewfinderSurfaceAttachments++
            }
            onDispose {
                preview?.setSurfaceProvider(null)
                if (naturalFrame) {
                    view.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    preview?.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                }
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
 * The photo size the in-app camera captures at: the phone's standard size, about 12 MP, never its
 * largest mode. The owner chose "Phone's standard size (Recommended)" (dispatch 2026-09-28-658, RECORD
 * -655), against keeping the device's largest mode: the device reports what it can do, which is not
 * a safe operating size (CLAUDE.md), and a 50 MP or 200 MP capture costs storage, backup size and
 * decode memory with nothing a forager can see on a phone screen.
 *
 * 4032×3024 is the 12 MP 4:3 size phone cameras save by default. It is a bound, in the sensor's
 * landscape frame as CameraX states sizes: CameraX picks the closest supported size at or below it
 * (a phone whose standard mode is 4000×3000 gets that), and only on a camera with nothing that small
 * the closest above it. Which size a given phone then picks is device-only and not checked here.
 */
internal val STANDARD_PHOTO_SIZE = Size(4032, 3024)

/** The capability probe's log tag (dispatch 2026-10-13-04): `adb logcat -s CameraProbe` shows every probe line and nothing else. */
internal const val CAMERA_PROBE_LOG_TAG = "CameraProbe"

/** Numbers each bind's probe within the process, so its lines, the front camera's and the later encoder line can be matched. */
private val nextProbeId = AtomicInteger(0)

/**
 * The encoders, read once per process off the main thread (planner, 2026-10-14: listing the
 * codecs is a platform query whose cost on a phone is unmeasured). Started by the first probe that
 * asks; every later probe awaits the same result.
 */
private val processEncoderProbe: Deferred<EncoderCapabilities> by lazy {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).async { mapEncoderCapabilities(readEncoders()) }
}

/**
 * Today's JPG capture, exactly as it has been built since dispatch 2026-09-28-658: minimise latency, the phone's standard
 * photo size, never its largest mode (see [STANDARD_PHOTO_SIZE]). Moved out of [CameraXCaptureSession.open] unchanged when
 * the RAW capture arrived beside it (dispatch 2026-10-15-01), so the JPG path's builder is one function that nothing else
 * edits; its output is unchanged by construction.
 */
internal fun jpegImageCapture(): ImageCapture =
    ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        // Dispatch 2026-09-28-658: the phone's standard photo size, never its
        // largest mode. See STANDARD_PHOTO_SIZE.
        .setResolutionSelector(standardPhotoSizeSelector())
        .build()

/** The capture's [ResolutionSelector]: [STANDARD_PHOTO_SIZE] as the bound, the default 4:3 aspect kept. */
internal fun standardPhotoSizeSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(STANDARD_PHOTO_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
        .build()
