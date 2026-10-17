package com.zynergylabs.forager.app.photo.engine

import android.util.Log
import android.util.Size
import androidx.camera.core.UseCase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.Probed
import com.zynergylabs.forager.app.domain.RawFrameMapping
import com.zynergylabs.forager.app.domain.mapRawFrame
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FlashMode
import com.zynergylabs.forager.app.photo.OrientationReapplyOutcome
import com.zynergylabs.forager.app.photo.RawShot
import com.zynergylabs.forager.app.photo.rawOrientationFor
import com.zynergylabs.forager.app.photo.reapplyIntendedOrientation
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Where the engine is. Observable state for the tests and the summary; the screen reads [CameraCaptureSession.state]. */
internal enum class EnginePhase { Idle, Opening, AwaitingViewfinder, Configuring, Streaming, Paused, Disconnected, FellBack, Closed }

/** The device's orientation, from `OrientationEventListener` on a phone. Returns the stop. */
internal fun interface OrientationSource {
    fun start(onDegrees: (Int) -> Unit): () -> Unit

    companion object {
        val None = OrientationSource { { } }
    }
}

internal data class EngineTimeouts(
    /** After the repeating request starts, no frame within this is a stream that failed: fall back. */
    val firstFrameMillis: Long = 3_000L,
    /** A shot taken before the viewfinder streams waits this long for it. */
    val streamingWaitMillis: Long = 3_000L,
    /** CameraX's 3A-with-flash wait is of this order; the shot is taken when it times out, as CameraX does. */
    val precaptureMillis: Long = 3_000L,
    val lockMillis: Long = 3_000L,
    val burstMillis: Long = 10_000L,
)

internal sealed interface BurstReport {
    data class Done(val line: String, val frames: Int, val requested: Int, val activePhysicalIds: Set<String?>) : BurstReport
    data class Unsupported(val reason: String) : BurstReport
    data class Failed(val reason: String) : BurstReport
}

/**
 * Camera engine A (dispatch 2026-10-16-02, RECORD -1083): the in-app camera run through the platform camera interface,
 * behind [CameraCaptureSession], so the dialog drives it exactly as it drives CameraX. Debug source set only; the camera2
 * calls are in [PlatformCamera]'s one implementation, `Camera2PlatformCamera`, and every decision is here, over a fake in
 * `PlatformCameraEngineTest`.
 *
 * ## The state machine
 *
 * `open` watches the dialog's lifecycle: at ON_START the engine opens the device and reads [CameraSessionState.Ready] as
 * soon as it is open, which is what makes the dialog compose the viewfinder (the deadlock of 2026-09-15 was a camera that
 * waited for a viewfinder that waited for it). The viewfinder hands its surface in ([onPreviewTargetAvailable]); the
 * session is configured with it and the repeating request started; the first frame makes the phase Streaming.
 *
 * - **ON_STOP** (background, screen off) closes the device and keeps the viewfinder surface and the Ready state, as CameraX
 *   does: the dialog never sees a change, and ON_START reopens into the same surface.
 * - **Another app takes the camera** after the first stream: [CameraSessionState.Unavailable] with [IN_USE_MESSAGE], the
 *   device released, and an availability watch; when the camera is free the engine reopens. Today's CameraX recovers
 *   silently and reads Ready throughout (`CameraXCaptureSession` watches no camera state but stream configuration), so this
 *   reports more than today, as the dispatch asks.
 * - **Fallback**: any failure before the first stream (no camera, the open, the configure, the repeating request, no frame
 *   within [EngineTimeouts.firstFrameMillis]), a non-recoverable device error at any time, or the debug flag: the engine
 *   releases the camera and then calls [onFallback] with the reason, and `EngineASession` opens CameraX. Released first, so
 *   CameraX never meets a device the prototype still holds.
 *
 * Every camera operation runs under one lock ([cameraOps]), so a release never interleaves with an open on a phone, where
 * both wait on callbacks; a shot or the burst runs under [shotOps], one at a time.
 *
 * ## Matching today's camera
 *
 * - **Focus and exposure**: today's camera sets nothing of its own: CameraX's defaults are continuous-picture autofocus,
 *   auto exposure and auto white balance on the preview, and no tap to focus anywhere in the dialog (no
 *   `startFocusAndMetering` or camera controller in `src/main`, grep 2026-10-16). In MINIMIZE_LATENCY a shot without flash
 *   is a plain capture, no AF trigger (`CapturePipelineImpl.defaultNoFlashCapture`). The prototype does the same.
 * - **Orientation**: the shot's request is CameraX's rule ([jpegOrientationDegrees]), the device rotation snapped by
 *   CameraX's own `UseCase.snapToSurfaceRotation`, the window's rotation only as the fallback, and after the file is saved
 *   main's [reapplyIntendedOrientation] runs exactly as `CameraXCaptureSession.capture` runs it. The DNG takes the JPG's
 *   final tag by main's [rawOrientationFor]. The scrub is the persist path's, untouched.
 * - **Flash**: decision B's table ([flashRequest]); the precapture sequence when CameraX would run it ([needsPrecapture]).
 * - **RAW**: one request for the JPEG and the RAW streams, so both come from one exposure, noise reduction OFF where the
 *   camera offers it (RECORD -1064), the lens-shading map asked for (stage 2), the DNG input mapped by main's
 *   [mapRawFrame] and written by the dialog's own `RawShotSaver` through `ForagerDngWriter`, as stage 2 does.
 *   **Consequence to report:** one exposure means one request, so the JPG of a RAW shot also has the phone's noise
 *   reduction off. Two requests would give the JPG its noise reduction and the two files different exposures.
 */
internal class PlatformCameraEngine(
    private val camera: PlatformCamera,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { Log.i(ENGINE_LOG_TAG, it) },
    private val forceFallback: () -> Boolean = { false },
    private val orientationSource: OrientationSource = OrientationSource.None,
    private val timeouts: EngineTimeouts = EngineTimeouts(),
    /** How the zoom is asked of the camera: the adb `zoom_form` switch (dispatch 2026-10-17-01); today's form by default. */
    private val zoomForm: () -> ZoomForm = { ZoomFormSwitch.form },
) : CameraCaptureSession {

    /** Called once, after the engine has released the camera, when it gives up: open, configure or stream failed. */
    var onFallback: ((reason: String) -> Unit)? = null

    var phase: EnginePhase by mutableStateOf(EnginePhase.Idle)
        private set

    /** The chosen camera, read at the first open; the viewfinder reads its sizes and sensor orientation. */
    var facts: CameraFacts? by mutableStateOf(null)
        private set

    /** The window's rotation, from the viewfinder: the fallback for a shot taken before the orientation listener has read. */
    @Volatile
    var displayRotation: Int? = null

    override var state: CameraSessionState by mutableStateOf(CameraSessionState.Opening)
        private set

    private var sensorRotation: Int? by mutableStateOf(null)
    override val deviceRotation: Int? get() = sensorRotation

    private var flashUnit: Boolean by mutableStateOf(false)
    override val hasFlashUnit: Boolean get() = flashUnit

    private var currentFlash: FlashMode by mutableStateOf(FlashMode.Off)
    override val flashMode: FlashMode get() = currentFlash

    override var capabilities: CameraCapabilities? by mutableStateOf(null)
        private set

    /** Not read by anything a user sees (`CameraCaptureSession.encoderCapabilities`); the prototype does not probe it. */
    override val encoderCapabilities: EncoderCapabilities? get() = null

    override var outputFormat: CameraFormat by mutableStateOf(CameraFormat.Jpg)
        private set

    private var requestedFormat = CameraFormat.Jpg
    private val metrics = EngineMetrics("platform")
    private val cameraOps = Mutex()
    private val shotOps = Mutex()

    private var isOpen = false
    private var started = false
    private var fellBack = false
    private var everOpened = false
    private var everStreamed = false
    private var recovering = false
    private var owner: LifecycleOwner? = null
    private var observer: LifecycleEventObserver? = null
    private var stopOrientation: (() -> Unit)? = null
    private var stopAvailability: (() -> Unit)? = null
    private var runJob: Job? = null
    private var deviceOpen = false
    private val target = MutableStateFlow<PreviewTarget?>(null)
    private var configuredTarget: PreviewTarget? = null
    private var rawSource: RawSource = RawSource.None
    private var firstFrame: CompletableDeferred<Unit>? = null
    private val streaming = MutableStateFlow(false)
    private var latestAeState: Int? = null
    private var latestActivePhysical: String? = null
    private var loggedRotationFallback = false

    // ── The lens chooser (dispatch 2026-10-16-04) ─────────────────────────────────────────────────────────────────────
    // Null [lensSet] (a camera with no lens set, and every fake without one) leaves every path above exactly as it was.

    /** The phone's back lenses, from the facts; null for no row. */
    var lensSet: LensSet? by mutableStateOf(null)
        private set

    /** The button lit: the lens in use, or the one being switched to, at once (RECORD -1107: "the new button lights at once"). */
    var litLens: Lens? by mutableStateOf(null)
        private set

    /** The zoom asked for, in the main wide's terms. Not shown anywhere (no new words, RECORD -1106). */
    var zoom: Double by mutableStateOf(1.0)
        private set

    /**
     * The viewfinder's extra scale: the part of the zoom the camera did not crop itself, read from each frame's result
     * (requested crop over achieved crop). 1 when the camera crops, or when it does not report what it did.
     */
    var previewScale: Float by mutableStateOf(1f)
        private set

    /** The viewfinder stream's size and the lens's sensor orientation, for the viewfinder's transform. */
    var viewfinderPreviewSize: PixelSize? by mutableStateOf(null)
        private set
    var viewfinderSensorOrientation: Int? by mutableStateOf(null)
        private set

    private var choice: LensChoice? = null
    private val closeFocus = CloseFocusRule()
    private var appliedLens: Lens? = null
    private var appliedCrop: LensCrop? = null
    private var lensWorker: Job? = null
    private var lensDirty = false
    private var lensReason = "open"
    private var switchStartedNanos = 0L
    private var loggedNoFocus = false

    // Phase 2a diagnostics (dispatch 2026-10-17-01): each line at most once a second, a changed request or lens at once.
    private val cropDiagLimiter = LineLimiter()
    private val focusLimiter = LineLimiter()

    // ── Open, lifecycle, close ────────────────────────────────────────────────────────────────────────────────────────

    override fun open(lifecycleOwner: LifecycleOwner) {
        if (isOpen) {
            log("engine=platform event=open_ignored reason=already open")
            return
        }
        isOpen = true
        fellBack = false
        state = CameraSessionState.Opening
        phase = EnginePhase.Opening
        metrics.openRequested(camera.nowNanos())
        stopOrientation = orientationSource.start { degrees -> onDeviceOrientation(degrees) }
        val watcher = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStarted()
                Lifecycle.Event.ON_STOP -> onStopped()
                else -> Unit
            }
        }
        owner = lifecycleOwner
        observer = watcher
        lifecycleOwner.lifecycle.addObserver(watcher)
    }

    override fun close() {
        if (!isOpen) return
        isOpen = false
        started = false
        runJob?.cancel()
        runJob = null
        lensWorker?.cancel()
        lensWorker = null
        observer?.let { watcher -> owner?.lifecycle?.removeObserver(watcher) }
        observer = null
        owner = null
        stopOrientation?.invoke()
        stopOrientation = null
        stopAvailability?.invoke()
        stopAvailability = null
        sensorRotation = null
        streaming.value = false
        // No flash mode persists past the camera closing, and nothing stores it (as CameraXCaptureSession.close).
        currentFlash = FlashMode.Off
        flashUnit = false
        capabilities = null
        outputFormat = CameraFormat.Jpg
        state = CameraSessionState.Opening
        val wasFellBack = fellBack
        phase = EnginePhase.Closed
        log(metrics.summary())
        val held = target.value
        target.value = null
        if (wasFellBack) {
            held?.release()
            return
        }
        // On the engine's own scope, not the dialog's: the release must finish after the dialog has gone.
        scope.launch {
            releaseCamera()
            held?.release()
        }
    }

    private fun onStarted() {
        started = true
        if (!isOpen || fellBack) return
        if (runJob?.isActive == true) return
        startRun()
    }

    private fun onStopped() {
        started = false
        if (!isOpen || fellBack) return
        runJob?.cancel()
        runJob = null
        streaming.value = false
        if (phase != EnginePhase.Disconnected) phase = EnginePhase.Paused
        log("engine=platform event=paused reason=the dialog's lifecycle stopped (background or screen off); the camera is released")
        scope.launch { releaseCamera() }
    }

    private fun startRun() {
        runJob = scope.launch { run() }
    }

    private suspend fun run() {
        try {
            if (forceFallback()) {
                fallBack("forced by the debug flag")
                return
            }
            val chosen = facts ?: when (val described = camera.describe()) {
                is CameraDescription.NoCamera -> {
                    fallBack("no camera to open: ${described.reason}")
                    return
                }
                is CameraDescription.Described -> described.facts.also { installFacts(it) }
            }
            phase = EnginePhase.Opening
            val opened = cameraOps.withLock { camera.open(chosen) { event -> onDeviceEvent(event) } }
            when (opened) {
                OpenOutcome.Opened -> Unit
                is OpenOutcome.InUse -> {
                    if (everStreamed) {
                        awaitCameraFree(chosen, "the camera is in use: ${opened.reason}")
                    } else {
                        fallBack("the camera did not open: ${opened.reason}")
                    }
                    return
                }
                is OpenOutcome.Failed -> {
                    fallBack("the camera did not open: ${opened.reason}")
                    return
                }
            }
            deviceOpen = true
            if (!everOpened) {
                everOpened = true
                log(metrics.deviceOpened(camera.nowNanos()))
                log(metrics.heap("open", camera.heapUsedBytes()))
            } else {
                log("engine=platform event=reopened")
            }
            if (recovering) {
                recovering = false
                log("engine=platform event=recovered reason=the camera is free again")
            }
            state = CameraSessionState.Ready
            phase = EnginePhase.AwaitingViewfinder
            val surface = target.filterNotNull().first()
            configureAndStream(chosen, surface)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Never a dead camera: anything this engine did not foresee hands the camera to CameraX.
            fallBack("the engine failed: ${failure::class.java.simpleName}: ${failure.message}")
        }
    }

    private suspend fun configureAndStream(chosen: CameraFacts, surface: PreviewTarget) {
        phase = EnginePhase.Configuring
        val lens = targetLens()
        val plan = if (lens != null) StreamPlan(raw = lens.rawSize != null, lens = lens) else StreamPlan(raw = chosen.rawSize != null)
        if (lens != null) {
            appliedLens = lens
            appliedCrop = lensCrop(lens, choice?.zoom ?: lens.zoom)
            viewfinderPreviewSize = lens.previewSize
            viewfinderSensorOrientation = lens.sensorOrientation
            updatePreviewScale(null)
        }
        when (val configured = cameraOps.withLock { camera.configure(chosen, surface, plan) }) {
            is ConfigureOutcome.Failed -> {
                fallBack("the session did not configure: ${configured.reason}")
                return
            }
            is ConfigureOutcome.Configured -> {
                configuredTarget = surface
                // A session asked for no RAW stream carries none, whatever the camera reports back.
                rawSource = if (plan.raw) configured.rawSource else RawSource.None
                // A lens session's own sizes (dispatch 2026-10-17-01, -1150 call iii); the logical camera's without a lens set.
                val preview = lens?.previewSize ?: chosen.previewSize
                val jpeg = lens?.jpegSize ?: chosen.jpegSize
                val raw = if (lens != null) lens.rawSize else chosen.rawSize
                log(
                    "engine=platform event=configured raw_source=${label(configured.rawSource)} preview=$preview " +
                        "jpeg=$jpeg raw=${raw ?: "none"} main_wide=${chosen.mainWidePhysicalId ?: "none"} note=${configured.note}",
                )
                if (lens != null && plan.raw && configured.rawSource == RawSource.None) {
                    log("engine=platform event=lens_no_raw lens=${lens.physicalId} reason=the session would not carry this lens's RAW stream; its shots are JPG")
                } else if (lens == null && configured.rawSource == RawSource.None && chosen.rawSize != null) {
                    // The camera offers RAW but no RAW stream would configure beside the others: the chip must not offer it.
                    capabilities = capabilities?.copy(
                        rawOutput = Probed.Unsupported("the platform engine's session could not carry a RAW stream"),
                        rawPlusJpegOutput = Probed.Unsupported("the platform engine's session could not carry a RAW stream"),
                    )
                }
                applyFormat()
            }
        }
        val waiting = CompletableDeferred<Unit>()
        firstFrame = waiting
        metrics.streamRestarted()
        camera.startRepeating(previewControls()) { frame -> onFrame(frame) }.onFailure { failure ->
            fallBack("the viewfinder request was refused: ${failure.message}")
            return
        }
        if (withTimeoutOrNull(timeouts.firstFrameMillis) { waiting.await() } == null) {
            fallBack("no viewfinder frame within ${timeouts.firstFrameMillis} ms")
            return
        }
        phase = EnginePhase.Streaming
        streaming.value = true
        everStreamed = true
        if (lens != null) {
            val switched = switchStartedNanos
            switchStartedNanos = 0L
            log(
                "engine=platform event=lens_streaming lens=${lens.physicalId} label=${lens.label} zoom=${fmt(choice?.zoom ?: lens.zoom)} " +
                    "crop=${fmt(appliedCrop?.crop ?: 1.0)} raw=${if (rawSource == RawSource.None) "none" else lens.rawSize}" +
                    if (switched != 0L) " switch_ms=${fmt((camera.nowNanos() - switched) / 1_000_000.0)}" else "",
            )
            // A tap or a pinch that landed while this lens was configuring is applied now.
            requestLensUpdate(lensReason)
        }
    }

    private fun onFrame(frame: PreviewFrame) {
        if (!isOpen || fellBack) return
        latestAeState = frame.aeState
        if (frame.activePhysicalId != latestActivePhysical) {
            log("engine=platform event=active_physical id=${frame.activePhysicalId ?: "unreported"} was=${latestActivePhysical ?: "none"}")
            latestActivePhysical = frame.activePhysicalId
        }
        firstFrame?.let { waiting ->
            if (!waiting.isCompleted) {
                if (!everStreamed) {
                    log(metrics.firstFrame(camera.nowNanos()))
                    log(metrics.heap("first_frame", camera.heapUsedBytes()))
                } else {
                    log("engine=platform event=stream_resumed")
                }
                waiting.complete(Unit)
            }
        }
        metrics.frame(frame.sensorTimestampNanos)?.let(log)
        if (phase == EnginePhase.Streaming) onLensFrame(frame)
    }

    // ── The lens chooser ──────────────────────────────────────────────────────────────────────────────────────────────

    /** The lens the camera should be on now: the wanted lens, or the main wide when the subject is too close for it. */
    private fun targetLens(): Lens? {
        val set = lensSet ?: return null
        val wanted = choice?.wanted ?: return null
        return effectiveLens(wanted, set, closeFocus.tooClose)
    }

    /** A tap on a lens button (`LensRow`): that lens, at its own zoom. */
    fun onLensTapped(lensId: String) {
        val c = choice ?: return
        val lens = c.set.byId(lensId) ?: return
        val before = c.wanted
        c.tap(lens)
        afterChoice(before, "tap")
    }

    /** A pinch on the picture (`Modifier.lensPinch`): [factor] is the gesture's zoom change since its last event. */
    fun onPinch(factor: Float) {
        val c = choice ?: return
        val before = c.wanted
        c.pinch(factor)
        afterChoice(before, "pinch")
    }

    private fun afterChoice(before: Lens, reason: String) {
        val c = choice ?: return
        val set = lensSet ?: return
        zoom = c.zoom
        if (c.wanted.physicalId != before.physicalId) {
            if (closeFocus.wantedChanged(c.wanted, set.main, appliedLens)) {
                log("engine=platform event=close_focus too_close=${closeFocus.tooClose} wanted=${c.wanted.physicalId} reason=the main wide's last reading is closer than this lens can focus")
            }
        }
        litLens = targetLens()
        requestLensUpdate(reason)
    }

    private fun onLensFrame(frame: PreviewFrame) {
        val c = choice ?: return
        val set = lensSet ?: return
        val streamingLens = appliedLens ?: return
        updatePreviewScale(cropInLensUnits(frame.cropAchieved, frame.zoomFormUsed, streamingLens))
        frame.zoomDiag?.let { diag ->
            if (cropDiagLimiter.allow(frame.sensorTimestampNanos, diag.plan to streamingLens.physicalId)) {
                log(cropDiagLine("preview", diag, streamingLens, appliedCrop))
            }
        }
        if (frame.focusDiopters == null && !loggedNoFocus) {
            loggedNoFocus = true
            log("engine=platform event=close_focus_inactive reason=the lens ${streamingLens.physicalId} reports no focus distance")
        }
        if (closeFocus.onFrame(streamingLens, c.wanted, set.main, FocusReading(frame.focusDiopters, frame.afState))) {
            log(
                "engine=platform event=close_focus too_close=${closeFocus.tooClose} wanted=${c.wanted.physicalId} streaming=${streamingLens.physicalId} " +
                    "focus_diopters=${frame.focusDiopters} af_state=${frame.afState} min_focus_diopters=${c.wanted.minFocusDiopters}",
            )
            litLens = targetLens()
            requestLensUpdate("close_focus")
        }
        logFocusReading(frame, streamingLens, c.wanted, set.main)
    }

    /**
     * The close-focus rule's readings while a telephoto is wanted (dispatch 2026-10-17-01, item 4), once a second: so a
     * case where nothing fires (RECORD -1147's first try) shows what the rule saw. The rule itself is unchanged.
     */
    private fun logFocusReading(frame: PreviewFrame, streamingLens: Lens, wanted: Lens, main: Lens) {
        if (!closeFocus.applies(wanted, main)) return
        if (!focusLimiter.allow(frame.sensorTimestampNanos, streamingLens.physicalId to wanted.physicalId)) return
        val nearLimit = wanted.minFocusDiopters * CloseFocusRule.NEAR_LIMIT
        log(
            "engine=platform event=focus_reading streaming=${streamingLens.physicalId} wanted=${wanted.physicalId} " +
                "focus_diopters=${frame.focusDiopters ?: "unreported"} af_state=${frame.afState ?: "unreported"} " +
                "min_focus_diopters=${wanted.minFocusDiopters} near_limit=${String.format(java.util.Locale.ROOT, "%.3f", nearLimit)} " +
                "near_frames=${closeFocus.nearFrames} far_frames=${closeFocus.farFrames} frames_to_decide=${CloseFocusRule.FRAMES_TO_DECIDE} " +
                "too_close=${closeFocus.tooClose}",
        )
    }

    private fun updatePreviewScale(achieved: Double?) {
        val requested = appliedCrop?.crop ?: 1.0
        val scale = if (achieved == null || achieved <= 0.0) 1f else (requested / achieved).coerceIn(1.0, requested).toFloat()
        // Logged when the reading changes, so the device check sees whether the phone crops the lens itself (the S26 lists no
        // per-lens crop key, RECORD -1106) or the viewfinder is making up the zoom.
        if (achieved != null) {
            val line = "engine=platform event=viewfinder_crop lens=${appliedCrop?.lensId} crop_requested=${fmt(requested)} " +
                "crop_achieved=${fmt(achieved)} view_scale=${fmt(scale.toDouble())}"
            if (line != lastCropLine) {
                lastCropLine = line
                log(line)
            }
        }
        previewScale = scale
    }

    private var lastCropLine: String? = null

    /**
     * Brings the camera to [targetLens] at the current zoom: a new session when the lens changes (the picture holds its last
     * frame and a shot waits, RECORD -1107), a new repeating request when only the crop does. One worker at a time; a
     * change that lands while it runs is applied when it finishes, so a fast pinch costs one switch, not one per event.
     */
    private fun requestLensUpdate(reason: String) {
        if (lensSet == null) return
        lensReason = reason
        if (lensWorker?.isActive == true) {
            lensDirty = true
            return
        }
        if (!isOpen || fellBack || phase != EnginePhase.Streaming) return
        lensWorker = scope.launch {
            try {
                do {
                    lensDirty = false
                    applyLens(lensReason)
                } while (lensDirty && isOpen && !fellBack && phase == EnginePhase.Streaming)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                fallBack("the lens change failed: ${failure::class.java.simpleName}: ${failure.message}")
            }
        }
    }

    private suspend fun applyLens(reason: String) {
        val lens = targetLens() ?: return
        val c = choice ?: return
        val chosen = facts ?: return
        val surface = configuredTarget ?: return
        if (appliedLens?.physicalId != lens.physicalId) {
            shotOps.withLock {
                if (!isOpen || fellBack || phase != EnginePhase.Streaming) return@withLock
                log("engine=platform event=lens_switch from=${appliedLens?.physicalId ?: "none"} to=${lens.physicalId} reason=$reason zoom=${fmt(c.zoom)}")
                switchStartedNanos = camera.nowNanos()
                // The shutter waits on this (awaitStreaming); the viewfinder keeps its last frame, its surface untouched.
                streaming.value = false
                configureAndStream(chosen, surface)
            }
            return
        }
        val crop = lensCrop(lens, c.zoom)
        if (crop == appliedCrop) return
        appliedCrop = crop
        updatePreviewScale(null)
        camera.startRepeating(previewControls()) { frame -> onFrame(frame) }.onFailure { failure ->
            log("engine=platform event=zoom_failed zoom=${fmt(c.zoom)} reason=${failure.message}")
        }
    }

    /** The DNG input with the hard crop for the shot's zoom (RECORD -1107); unchanged at the lens's own zoom. */
    private fun DngImage.croppedFor(crop: LensCrop?): DngImage {
        val factor = crop?.crop ?: return this
        val defaultCrop = dngDefaultCrop(width, height, activeArea, factor) ?: return this
        return copy(defaultCrop = defaultCrop)
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f", value)

    private fun onDeviceEvent(event: DeviceEvent) {
        if (!isOpen || fellBack) return
        when (event) {
            DeviceEvent.Disconnected -> onTakenAway("onDisconnected")
            is DeviceEvent.Error -> if (event.inUse) {
                onTakenAway("onError ${event.name}")
            } else {
                runJob?.cancel()
                scope.launch { fallBack("the camera reported ${event.name}") }
            }
        }
    }

    /** Another client has the camera. Before the first stream that is a camera that could not stream: CameraX takes over. */
    private fun onTakenAway(what: String) {
        runJob?.cancel()
        runJob = null
        streaming.value = false
        val chosen = facts
        if (!everStreamed || chosen == null) {
            scope.launch { fallBack("the camera was taken before it streamed ($what)") }
            return
        }
        log("engine=platform event=disconnected reason=$what")
        scope.launch { awaitCameraFree(chosen, what) }
    }

    private suspend fun awaitCameraFree(chosen: CameraFacts, why: String) {
        recovering = true
        phase = EnginePhase.Disconnected
        state = CameraSessionState.Unavailable(IN_USE_MESSAGE)
        releaseCamera()
        stopAvailability?.invoke()
        stopAvailability = camera.watchAvailability(chosen.cameraId) { onCameraFree() }
        log("engine=platform event=waiting_for_camera reason=$why")
    }

    private fun onCameraFree() {
        stopAvailability?.invoke()
        stopAvailability = null
        if (!isOpen || fellBack || phase != EnginePhase.Disconnected) return
        if (!started) {
            log("engine=platform event=camera_free reason=the dialog is in the background; it reopens on return")
            return
        }
        if (runJob?.isActive != true) startRun()
    }

    private suspend fun fallBack(reason: String) {
        if (!isOpen || fellBack) return
        fellBack = true
        phase = EnginePhase.FellBack
        streaming.value = false
        log("engine=platform event=fallback reason=$reason")
        withContext(NonCancellable) {
            releaseCamera()
            target.value?.let { surface ->
                target.value = null
                surface.release()
            }
        }
        onFallback?.invoke(reason)
    }

    /** Closes the session and the device and waits for it. Called on every path that ends a run: the hand-back. */
    private suspend fun releaseCamera() = withContext(NonCancellable) {
        streaming.value = false
        configuredTarget = null
        rawSource = RawSource.None
        appliedLens = null
        deviceOpen = false
        val released = cameraOps.withLock { camera.release() }
        if (!released.closed) log("engine=platform event=release_incomplete detail=${released.detail}")
        Unit
    }

    private fun installFacts(chosen: CameraFacts) {
        facts = chosen
        flashUnit = chosen.hasFlashUnit
        capabilities = chosen.capabilities
        log(
            "engine=platform event=camera id=${chosen.cameraId} facing=${chosen.facing.name.lowercase()} sensor_orientation=${chosen.sensorOrientation} " +
                "flash_unit=${chosen.hasFlashUnit} jpeg=${chosen.jpegSize} preview=${chosen.previewSize} raw=${chosen.rawSize ?: "none"} " +
                "main_wide=${chosen.mainWidePhysicalId ?: "none"} (${chosen.mainWideNote}) nr_off_offered=${chosen.noiseReductionOffOffered}",
        )
        chosen.notes.forEach { log("engine=platform event=camera_note $it") }
        chosen.lensStatics.forEach { log(lensStaticLine(it)) }
        viewfinderPreviewSize = chosen.previewSize
        viewfinderSensorOrientation = chosen.sensorOrientation
        val set = chosen.lenses
        if (set == null) {
            if (chosen.lensNote.isNotEmpty()) log("engine=platform event=lenses none reason=${chosen.lensNote}")
            return
        }
        val c = LensChoice(set, LensMemory.lastLensId)
        choice = c
        lensSet = set
        zoom = c.zoom
        litLens = targetLens()
        log(
            "engine=platform event=lenses " + set.lenses.joinToString(" ") { lens ->
                "${lens.physicalId}:${lens.label}(zoom=${fmt(lens.zoom)},jpeg=${lens.jpegSize},raw=${lens.rawSize ?: "none"},min_focus_diopters=${lens.minFocusDiopters})"
            } + " opens_on=${c.wanted.physicalId} remembered=${LensMemory.lastLensId ?: "none"}" +
                if (chosen.lensNote.isNotEmpty()) " note=${chosen.lensNote}" else "",
        )
    }

    // ── The viewfinder's surface ──────────────────────────────────────────────────────────────────────────────────────

    fun onPreviewTargetAvailable(surface: PreviewTarget) {
        target.value = surface
        if (!isOpen || fellBack) return
        // The first stream's run is waiting on the target itself; a later surface (the viewfinder recomposed while the
        // device stayed open) is configured here.
        if (deviceOpen && configuredTarget == null && runJob?.isActive != true && phase == EnginePhase.AwaitingViewfinder) {
            val chosen = facts ?: return
            runJob = scope.launch {
                try {
                    configureAndStream(chosen, surface)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    fallBack("the engine failed: ${failure::class.java.simpleName}: ${failure.message}")
                }
            }
        }
    }

    fun onPreviewTargetGone(surface: PreviewTarget) {
        if (target.value === surface) target.value = null
        if (configuredTarget !== surface) {
            surface.release()
            return
        }
        // The session still draws into it: close the session first, then let the surface go.
        configuredTarget = null
        streaming.value = false
        if (phase == EnginePhase.Streaming || phase == EnginePhase.Configuring) phase = EnginePhase.AwaitingViewfinder
        scope.launch {
            withContext(NonCancellable) { cameraOps.withLock { camera.closeSession() } }
            surface.release()
        }
    }

    // ── Orientation ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** The orientation listener's degrees, snapped by CameraX's own rule, as `CameraXCaptureSession.onDeviceOrientation`. */
    fun onDeviceOrientation(orientationDegrees: Int) {
        if (orientationDegrees < 0) return // OrientationEventListener.ORIENTATION_UNKNOWN
        sensorRotation = UseCase.snapToSurfaceRotation(orientationDegrees % 360)
    }

    private fun shotRotation(): Int {
        sensorRotation?.let { return it }
        val window = displayRotation
        if (!loggedRotationFallback) {
            loggedRotationFallback = true
            log(
                if (window != null) {
                    "engine=platform event=rotation_fallback reason=no orientation reading yet; this shot follows the window's rotation $window, which is wrong if auto-rotate is off"
                } else {
                    "engine=platform event=rotation_fallback reason=no orientation reading and no window rotation; the shot is tagged as held upright"
                },
            )
        }
        return window ?: 0
    }

    // ── Shots ─────────────────────────────────────────────────────────────────────────────────────────────────────────

    private suspend fun awaitStreaming(): Boolean {
        if (streaming.value) return true
        return withTimeoutOrNull(timeouts.streamingWaitMillis) { streaming.first { it } } != null
    }

    override suspend fun capture(destination: File): Result<Unit> {
        if (!awaitStreaming()) return Result.failure(IllegalStateException("The camera is not ready yet."))
        val chosen = facts ?: return Result.failure(IllegalStateException("The camera is not ready yet."))
        return shotOps.withLock {
            val degrees = jpegOrientationDegrees(appliedLens?.sensorOrientation ?: chosen.sensorOrientation, shotRotation(), chosen.facing)
            val flash = currentFlash
            val shutter = camera.nowNanos()
            val precapture = precaptureIfNeeded(flash)
            val crop = appliedCrop
            val frame = try {
                camera.captureJpeg(StillControls(flash, degrees, noiseReductionOff = false, lensCrop = crop, zoomForm = zoomForm()), destination)
            } finally {
                if (precapture) camera.endPrecapture(previewControls())
            }
            frame.fold(
                onSuccess = { jpeg ->
                    val outcome = withContext(ioDispatcher) { reapplyIntendedOrientation(destination, degrees, Size(jpeg.width, jpeg.height)) }
                    log("engine=platform event=jpg_shot request_degrees=$degrees size=${jpeg.width}x${jpeg.height} active_physical=${jpeg.activePhysicalId ?: "unreported"} tag=${describe(outcome)}" + cropText(crop, jpeg))
                    logShotDiag("jpg", crop, jpeg)
                    log(metrics.shot(ShotKind.Jpg, shutter, camera.nowNanos()))
                    log(metrics.heap("jpg_shot", camera.heapUsedBytes()))
                    Result.success(Unit)
                },
                onFailure = { failure ->
                    log("engine=platform event=shot_failed kind=jpg reason=${failure.message}")
                    Result.failure(failure)
                },
            )
        }
    }

    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> {
        if (!awaitStreaming()) return Result.failure(IllegalStateException("The camera is not ready yet."))
        val chosen = facts ?: return Result.failure(IllegalStateException("The camera is not ready yet."))
        if (lensSet != null && outputFormat == CameraFormat.Raw && rawSource == RawSource.None) {
            // A lens that cannot give RAW, with RAW chosen (-1103): the JPG is the photo, and the dialog says so.
            val lensId = appliedLens?.physicalId ?: "unknown"
            log("engine=platform event=raw_lens_unsupported lens=$lensId reason=this lens's session carries no RAW stream; saved as JPG")
            return capture(jpegDestination).map { RawShot(dng = null, dngUnavailable = "lens $lensId carries no RAW stream", lensWithoutRaw = true) }
        }
        if (outputFormat != CameraFormat.Raw || rawSource == RawSource.None) {
            return Result.failure(IllegalStateException("The camera is not set up for RAW."))
        }
        return shotOps.withLock {
            val degrees = jpegOrientationDegrees(appliedLens?.sensorOrientation ?: chosen.sensorOrientation, shotRotation(), chosen.facing)
            val flash = currentFlash
            val shutter = camera.nowNanos()
            val precapture = precaptureIfNeeded(flash)
            val crop = appliedCrop
            val frame = try {
                camera.captureRawJpeg(StillControls(flash, degrees, noiseReductionOff = chosen.noiseReductionOffOffered, lensCrop = crop, zoomForm = zoomForm()), jpegDestination)
            } finally {
                if (precapture) camera.endPrecapture(previewControls())
            }
            val captured = frame.getOrElse { failure ->
                log("engine=platform event=shot_failed kind=raw reason=${failure.message}")
                return@withLock Result.failure(failure)
            }
            val outcome = withContext(ioDispatcher) {
                reapplyIntendedOrientation(jpegDestination, degrees, Size(captured.jpeg.width, captured.jpeg.height))
            }
            val dngOrientation = rawOrientationFor(outcome, degrees)
            log(
                "engine=platform event=raw_shot raw_source=${label(captured.rawSource)} active_physical=${captured.rawActivePhysicalId ?: "unreported"} " +
                    "noise_reduction=${captured.noiseReductionModeReported ?: "unreported"} noise_reduction_off_asked=${chosen.noiseReductionOffOffered} " +
                    "request_degrees=$degrees jpg_tag=${describe(outcome)} dng_orientation=$dngOrientation" + cropText(crop, captured.jpeg),
            )
            logShotDiag("raw", crop, captured.jpeg)
            if (captured.rawSource is RawSource.Physical && captured.rawActivePhysicalId != null && captured.rawActivePhysicalId != captured.rawSource.physicalId) {
                log("engine=platform event=raw_sensor_mismatch pinned=${captured.rawSource.physicalId} reported=${captured.rawActivePhysicalId}")
            }
            val build = captured.readings
            val shot = if (build == null) {
                RawShot(dng = null, dngUnavailable = captured.readingsUnavailable ?: "the RAW frame came back without its readings")
            } else {
                val mapping = withContext(ioDispatcher) {
                    runCatching { mapRawFrame(build(dngOrientation)) }
                        .getOrElse { error -> RawFrameMapping.Unsupported("reading the frame's metadata threw ${error::class.java.simpleName}: ${error.message}") }
                }
                when (mapping) {
                    is RawFrameMapping.Mapped -> RawShot(dng = mapping.image.croppedFor(crop), dngUnavailable = null)
                    is RawFrameMapping.Unsupported -> RawShot(dng = null, dngUnavailable = mapping.reason)
                }
            }
            log(metrics.shot(ShotKind.Raw, shutter, camera.nowNanos()))
            log(metrics.heap("raw_shot", camera.heapUsedBytes()))
            Result.success(shot)
        }
    }

    /** CameraX's precapture rule; the shot is taken whatever the sequence reports, as CameraX takes it on a timeout. */
    private suspend fun precaptureIfNeeded(flash: FlashMode): Boolean {
        if (!flashUnit || !needsPrecapture(flash, latestAeState)) return false
        val outcome = camera.precapture(previewControls(), timeouts.precaptureMillis)
        log("engine=platform event=precapture flash=$flash outcome=$outcome")
        return true
    }

    // ── Flash and format ──────────────────────────────────────────────────────────────────────────────────────────────

    private fun previewControls() = PreviewControls(currentFlash, lensCrop = appliedCrop)

    /** The shot's crop asked and achieved, both in the lens's own units (dispatch 2026-10-17-01, item 3). */
    private fun cropText(crop: LensCrop?, jpeg: JpegFrame): String {
        crop ?: return ""
        val achieved = cropInLensUnits(jpeg.cropAchieved, jpeg.zoomFormUsed, lensSet?.byId(crop.lensId))
        return " lens=${crop.lensId} zoom=${fmt(crop.zoom)} crop_requested=${fmt(crop.crop)} crop_achieved=${achieved?.let { fmt(it) } ?: "unreported"}"
    }

    /** Every shot's `crop_diag` line, not rate-limited: the shot is what the device run compares with the photo. */
    private fun logShotDiag(kind: String, crop: LensCrop?, jpeg: JpegFrame) {
        val diag = jpeg.zoomDiag ?: return
        log(cropDiagLine(kind, diag, crop?.let { lensSet?.byId(it.lensId) }, crop))
    }

    override fun setFlashMode(mode: FlashMode) {
        if (!flashUnit) {
            log("engine=platform event=flash ignored mode=$mode reason=no flash unit on this camera")
            return
        }
        currentFlash = mode
        if (!streaming.value) return
        camera.startRepeating(previewControls()) { frame -> onFrame(frame) }.onFailure { failure ->
            log("engine=platform event=flash_failed mode=$mode reason=${failure.message}; back to Off")
            currentFlash = FlashMode.Off
        }
    }

    override fun requestOutputFormat(format: CameraFormat) {
        requestedFormat = format
        applyFormat()
    }

    /** The RAW stream is in the session from the start, so a format change is a value, never a new session: no blink. */
    private fun applyFormat() {
        // With the lens chooser the chip's RAW stands on a lens without one: that shot is a JPG with the lens's line (-1103).
        val lensesOfferRaw = lensSet?.lenses?.any { it.rawSize != null } == true
        val raw = requestedFormat == CameraFormat.Raw && (rawSource != RawSource.None || lensesOfferRaw)
        if (requestedFormat == CameraFormat.Raw && !raw && phase == EnginePhase.Streaming) {
            log("engine=platform event=format reason=RAW asked for, but this session carries no RAW stream; staying on JPG")
        }
        outputFormat = if (raw) CameraFormat.Raw else CameraFormat.Jpg
    }

    // ── The burst ─────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The 8-frame RAW burst (dispatch 2026-10-16-02, item 6), a debug method with no UI: in this running session, so no
     * camera hand-over and no viewfinder blink (the preview stream is in each burst request, so the viewfinder keeps
     * drawing). AE and AWB are locked on the live preview, then the burst, then the locks are lifted whatever happened.
     * The frames are discarded as they arrive. The torch stays lit if it was; no flash fires.
     */
    suspend fun burst(frames: Int = BURST_FRAMES): BurstReport {
        if (!streaming.value) return BurstReport.Unsupported("the viewfinder is not streaming; open the camera dialog with engine A first")
        val chosen = facts ?: return BurstReport.Unsupported("no camera facts")
        if (rawSource == RawSource.None) return BurstReport.Unsupported("this camera's session carries no RAW stream")
        return shotOps.withLock {
            val flash = if (currentFlash == FlashMode.Torch) FlashMode.Torch else FlashMode.Off
            val heapBefore = camera.heapUsedBytes()
            val lock = camera.lockExposure(PreviewControls(currentFlash, aeLock = true, awbLock = true, lensCrop = appliedCrop, zoomForm = zoomForm()), timeouts.lockMillis)
            log("engine=platform event=burst_lock outcome=$lock")
            try {
                val result = camera.burst(
                    frames,
                    StillControls(flash, jpegOrientationDegrees = 0, noiseReductionOff = chosen.noiseReductionOffOffered, aeAwbLocked = lock is LockOutcome.Locked, lensCrop = appliedCrop, zoomForm = zoomForm()),
                    timeouts.burstMillis,
                )
                result.fold(
                    onSuccess = { burst ->
                        val line = metrics.burst(
                            frames = burst.frames.size,
                            requested = frames,
                            sensorTimestamps = burst.frames.map { it.sensorTimestampNanos },
                            submittedNanos = burst.submittedNanos,
                            lastArrivalNanos = burst.frames.maxOfOrNull { it.arrivedNanos },
                        )
                        log(line)
                        val ids = burst.frames.map { it.activePhysicalId }.toSet()
                        log(
                            "engine=platform event=burst_frames active_physical=${ids.joinToString("|") { it ?: "unreported" }} " +
                                "noise_reduction=${burst.frames.map { it.noiseReductionMode ?: "unreported" }.toSet().joinToString("|")} " +
                                "raw_source=${label(rawSource)} failures=${burst.failures.size}" + if (burst.failures.isEmpty()) "" else " (${burst.failures.joinToString("; ")})",
                        )
                        if (ids.size > 1) log("engine=platform event=burst_sensor_mix reason=frames came from more than one physical camera (RECORD -1076): $ids")
                        log(metrics.heap("burst_before", heapBefore))
                        log(metrics.heap("burst_after", camera.heapUsedBytes()))
                        BurstReport.Done(line, burst.frames.size, frames, ids)
                    },
                    onFailure = { failure ->
                        log("engine=platform event=burst_failed reason=${failure.message}")
                        BurstReport.Failed(failure.message ?: failure.toString())
                    },
                )
            } finally {
                // The locks off, whatever happened: the repeating request without them.
                camera.startRepeating(previewControls()) { frame -> onFrame(frame) }.onFailure { failure ->
                    log("engine=platform event=burst_unlock_failed reason=${failure.message}")
                }
            }
        }
    }

    fun summary(): String = metrics.summary() + " phase=$phase fell_back=$fellBack"

    /** The adb switch changed the zoom form: the running viewfinder request is replaced, so the next frames use it. */
    fun onZoomFormChanged() {
        log("engine=platform event=zoom_form form=${zoomForm().id}")
        if (!isOpen || fellBack || !streaming.value) return
        camera.startRepeating(previewControls()) { frame -> onFrame(frame) }.onFailure { failure ->
            log("engine=platform event=zoom_form_failed form=${zoomForm().id} reason=${failure.message}")
        }
    }

    private fun describe(outcome: OrientationReapplyOutcome): String = when (outcome) {
        is OrientationReapplyOutcome.Rewritten -> "rewritten(${outcome.fromTag}->${outcome.toTag})"
        is OrientationReapplyOutcome.Kept -> "kept(${outcome.tag})"
        is OrientationReapplyOutcome.Declined -> "declined(${outcome.tag}: ${outcome.reason})"
        is OrientationReapplyOutcome.Failed -> "failed(${outcome.error::class.java.simpleName}: ${outcome.error.message})"
    }

    private fun label(source: RawSource): String = when (source) {
        RawSource.None -> "none"
        RawSource.Logical -> "logical"
        is RawSource.Physical -> "physical:${source.physicalId}"
    }

    companion object {
        const val BURST_FRAMES = 8
        const val IN_USE_MESSAGE = "The camera is in use by another app. It comes back when that app lets it go."
    }
}

/** `adb logcat -v raw -s CameraEngineA`: every line the prototype writes, CameraX's timings included. */
internal const val ENGINE_LOG_TAG = "CameraEngineA"
