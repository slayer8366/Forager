package com.zynergylabs.forager.app.diagnostics.burst

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.LifecycleCamera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.zynergylabs.forager.app.data.raw.ForagerDngWriter
import com.zynergylabs.forager.app.domain.RawFrameMapping
import com.zynergylabs.forager.app.domain.RawFrameReadings
import com.zynergylabs.forager.app.domain.mapRawFrame
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import com.zynergylabs.forager.app.domain.raw.SRational
import com.zynergylabs.forager.app.domain.raw.URational
import com.zynergylabs.forager.app.photo.deviceIdentityStrings
import java.io.File
import java.nio.ByteOrder
import java.util.Collections
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The fast-burst test's camera (dispatch 2026-10-15-09, RECORD -1063): CameraX lets the camera go, the platform camera
 * interface takes it for four 8-frame RAW bursts, and CameraX gets it back. Debug source set only; nothing in src/main
 * changes. It reads main's `mapRawFrame`, `ForagerDngWriter` and `deviceIdentityStrings` and calls them; it edits none.
 *
 * ## How CameraX lets the camera go (READ from the pinned 1.6.2 artifacts, checksums matched against the publisher's)
 *
 * - `LifecycleCameraRepository.setActive(owner)` returns early when the owner has no use case bound (`hasUseCaseBound`),
 *   so an empty binding cannot take the camera from the dialog. A bound owner at least STARTED becomes the active one:
 *   `setActive` suspends the use cases of the owner on top of its stack (the dialog's) and unsuspends its own.
 * - So the probe binds one placeholder preview to its own owner, which suspends the dialog's binding, and then suspends
 *   its own lifecycle camera (`LifecycleCamera.suspend()`, public in the class but marked restricted to the library
 *   group: a lint finding, not a compile error, and this project's CI runs no lint; suppressed here with this reason).
 *   No use case is then attached to the camera.
 * - camera-camera2 1.6.2 runs on the CameraPipe stack. `UseCaseManager.refreshAttachedUseCases` with nothing attached
 *   closes its current use cases and graph, and CameraX's camera state reads CLOSED. But CameraPipe closes the device
 *   itself on a graph close only when `CameraGraph.Flags.closeCameraDeviceOnClose` is set, which CameraX sets only for a
 *   device quirk (`CloseCameraOnCameraGraphClose`); by default the device may stay open, idle, in the same process.
 * - The platform's `openCamera` documents that "Opening the same camera ID twice in the same application will similarly
 *   cause the CameraDevice.StateCallback.onDisconnected(CameraDevice) callback being fired for the CameraDevice from the
 *   first open call". So the probe's open takes the device from CameraPipe's idle hold, if it still has one, with no graph
 *   of CameraX's running to notice (INFERRED: no CameraX graph is open to receive the error; the probe logs every
 *   CameraX camera-state change during the swap, so if CameraX reports anything it is in the borrow line).
 * - The dialog's own watcher (`CameraXCaptureSession.watchForStreamConfigErrors`) acts only on ERROR_STREAM_CONFIG with
 *   RAW chosen. The probe never configures a CameraX stream beyond the placeholder, and waits for CameraX to read CLOSED
 *   before it opens anything, refusing to open if it does not.
 *
 * ## Hand-back
 *
 * The platform session and device are closed first and `onClosed` awaited, so CameraX's reopen never meets a device
 * still held by the probe (were it still held, CameraX's own open would take it by the same rule). Then the placeholder
 * is unbound: `LifecycleCameraRepository.unbind` finds the probe's camera empty, calls `setInactive`, and unsuspends the
 * owner below it on the stack, the dialog's, whose use cases reattach as they were bound, so the user's chosen format is
 * back without a rebind. Then the probe waits for CameraX to read OPEN and the dialog's viewfinder to read STREAMING.
 */
@SuppressLint("RestrictedApi", "MissingPermission")
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class PlatformBurstCameraImpl(
    private val context: Context,
    private val sessionFolder: String,
) : PlatformBurstCamera {

    private val triggerNanos = SystemClock.elapsedRealtimeNanos()
    private fun now(): Double = (SystemClock.elapsedRealtimeNanos() - triggerNanos) / 1_000_000.0
    private fun msSinceTrigger(nanos: Long): Double = (nanos - triggerNanos) / 1_000_000.0

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val cameraThread = HandlerThread("PlatformBurstCamera").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)
    private val writer = ForagerDngWriter(deviceIdentityStrings())

    // CameraX's side.
    private var provider: ProcessCameraProvider? = null
    private var owner: ProbeOwner? = null
    private var placeholder: Preview? = null
    private var cameraxInfo: CameraInfo? = null
    private var stateObserver: Observer<CameraState>? = null
    @Volatile private var cameraxState: CameraState.Type? = null
    private var cameraxStateAtStart: CameraState.Type? = null
    private val cameraStates: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // The dialog's viewfinder, when found.
    private var previewView: PreviewView? = null
    private var streamObserver: Observer<PreviewView.StreamState>? = null
    @Volatile private var viewfinderStream: PreviewView.StreamState? = null
    private var viewfinderIdleMs: Double? = null

    // The platform side.
    private var characteristics: CameraCharacteristics? = null
    private var device: CameraDevice? = null
    private var deviceClosed = CompletableDeferred<Unit>()
    private val deviceEvents: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var captureSession: CameraCaptureSession? = null
    private var previewReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var rawSize: Size? = null
    private var previewBuilder: CaptureRequest.Builder? = null
    private val previewResults = LatestResult()

    override fun memory(): MemoryReading {
        val runtime = Runtime.getRuntime()
        return MemoryReading(maxMemoryBytes = runtime.maxMemory(), usedBytes = runtime.totalMemory() - runtime.freeMemory())
    }

    // ── Borrow ──────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun borrow(): BorrowOutcome {
        checkMainThread()
        val notes = mutableListOf<String>()
        var cameraId: String? = null
        var cameraxClosedMs: Double? = null
        var platformOpenedMs: Double? = null
        var sessionConfiguredMs: Double? = null
        var previewSize: Size? = null
        var viewfinder: String? = null
        fun timing() = SwapTiming(
            cameraId = cameraId,
            cameraxClosedMs = cameraxClosedMs,
            viewfinderIdleMs = viewfinderIdleMs,
            platformOpenedMs = platformOpenedMs,
            sessionConfiguredMs = sessionConfiguredMs,
            rawSize = rawSize?.let { "${it.width}x${it.height}" },
            previewSize = previewSize?.let { "${it.width}x${it.height}" },
            viewfinder = viewfinder,
            cameraStates = cameraStates.toList(),
            notes = notes + deviceEvents.toList(),
        )

        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (failure: Exception) {
            return BorrowOutcome.Failed("the camera provider did not start: $failure", timing())
        }
        this.provider = provider
        // The same choice as the dialog's (CameraXCaptureSession.open): the back camera, else the front.
        val selector = when {
            runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
            runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> return BorrowOutcome.Unsupported("this device reports no back or front camera", timing())
        }
        val info = provider.getCameraInfo(selector)
        cameraxInfo = info
        cameraId = Camera2CameraInfo.from(info).cameraId
        val chars = manager.getCameraCharacteristics(cameraId)
        characteristics = chars

        // Everything the session needs is checked before CameraX is asked to let go: an unsupported phone loses nothing.
        val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW !in capabilities) {
            return BorrowOutcome.Unsupported("camera $cameraId does not list the RAW capability (capabilities $capabilities)", timing())
        }
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return BorrowOutcome.Unsupported("camera $cameraId has no stream configuration map", timing())
        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
        val raw = rawSizes.filter { it.width.toLong() * it.height <= RAW_PIXEL_LIMIT }.maxByOrNull { it.width.toLong() * it.height }
            ?: return BorrowOutcome.Unsupported("no RAW_SENSOR size at or under ${RAW_PIXEL_LIMIT / 1_000_000} MP on camera $cameraId (offered: $rawSizes)", timing())
        rawSize = raw
        val preview = choosePreviewSize(map.getOutputSizes(ImageFormat.PRIVATE)?.toList().orEmpty(), raw)
            ?: return BorrowOutcome.Unsupported("no PRIVATE output size at or under 1920x1080 on camera $cameraId", timing())
        previewSize = preview
        notes += "hardware level ${chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)}, capabilities $capabilities"
        notes += "the session is PRIV ${preview.width}x${preview.height} (the probe's own, never shown) + RAW_SENSOR ${raw.width}x${raw.height}, " +
            "a guaranteed combination for a RAW-capable camera (\"Standard DNG capture\")"

        watchCameraxState(info)
        cameraxStateAtStart = cameraxState
        viewfinder = watchViewfinder()

        // CameraX lets the camera go: a placeholder binding suspends the dialog's, then the placeholder is suspended.
        val placeholderPreview = Preview.Builder().build()
        placeholderPreview.setSurfaceProvider(mainExecutor) { request -> provideDiscardingSurface(request) }
        placeholder = placeholderPreview
        val newOwner = ProbeOwner().also { it.resume() }
        owner = newOwner
        val bound = try {
            provider.bindToLifecycle(newOwner, selector, placeholderPreview)
        } catch (failure: Exception) {
            return BorrowOutcome.Failed("binding the probe's placeholder threw: $failure", timing())
        }
        val lifecycleCamera = bound as? LifecycleCamera
            ?: return BorrowOutcome.Failed("the bound camera is ${bound.javaClass.name}, not CameraX's LifecycleCamera; the probe cannot suspend it", timing())
        lifecycleCamera.suspend()
        notes += "placeholder bound and suspended at ${fmt(now())} ms"
        if (!awaitCondition(CAMERAX_CLOSE_TIMEOUT_MILLIS) { cameraxState == CameraState.Type.CLOSED }) {
            return BorrowOutcome.Failed(
                "CameraX's camera state read $cameraxState, not CLOSED, $CAMERAX_CLOSE_TIMEOUT_MILLIS ms after it was asked to let go; " +
                    "the platform open was not attempted, so CameraX was never contested",
                timing(),
            )
        }
        cameraxClosedMs = now()

        // The platform interface takes the camera.
        val opened = try {
            openDevice(cameraId)
        } catch (failure: Exception) {
            return BorrowOutcome.Failed("the platform interface could not open camera $cameraId: ${failure.message ?: failure}", timing())
        }
        device = opened
        platformOpenedMs = now()

        val rawImages = ImageReader.newInstance(raw.width, raw.height, ImageFormat.RAW_SENSOR, RAW_MAX_IMAGES)
        rawReader = rawImages
        val previewImages = ImageReader.newInstance(preview.width, preview.height, ImageFormat.PRIVATE, PREVIEW_MAX_IMAGES)
        previewImages.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
        previewReader = previewImages
        val configured = configureSession(opened, listOf(previewImages.surface, rawImages.surface))
            ?: return BorrowOutcome.Failed(
                "the platform session would not configure PRIV ${preview.width}x${preview.height} + RAW_SENSOR ${raw.width}x${raw.height} (onConfigureFailed)",
                timing(),
            )
        captureSession = configured
        sessionConfiguredMs = now()
        return BorrowOutcome.Borrowed(timing())
    }

    // ── Converge and lock ───────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun converge(): ConvergeOutcome {
        checkMainThread()
        val opened = device ?: return ConvergeOutcome.NotReached("no platform device is open", null, null)
        val session = captureSession ?: return ConvergeOutcome.NotReached("no platform session is configured", null, null)
        val surface = previewReader?.surface ?: return ConvergeOutcome.NotReached("no preview surface", null, null)
        val builder = opened.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        if (afModes().contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        previewBuilder = builder
        session.setRepeatingRequest(builder.build(), previewResults, handler)
        val converged = awaitCondition(CONVERGE_TIMEOUT_MILLIS) {
            previewResults.latest?.let { r ->
                r.get(CaptureResult.CONTROL_AE_STATE)?.let { it in AE_CONVERGED } == true && r.get(CaptureResult.CONTROL_AWB_STATE) == CaptureResult.CONTROL_AWB_STATE_CONVERGED
            } == true
        }
        if (!converged) {
            return ConvergeOutcome.NotReached(
                "AE and AWB did not both read CONVERGED within $CONVERGE_TIMEOUT_MILLIS ms",
                previewResults.latest?.get(CaptureResult.CONTROL_AE_STATE),
                previewResults.latest?.get(CaptureResult.CONTROL_AWB_STATE),
            )
        }
        val convergedMs = now()
        builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
        session.setRepeatingRequest(builder.build(), previewResults, handler)
        val locked = awaitCondition(CONVERGE_TIMEOUT_MILLIS) {
            previewResults.latest?.let { r ->
                r.get(CaptureResult.CONTROL_AE_STATE) == CaptureResult.CONTROL_AE_STATE_LOCKED &&
                    r.get(CaptureResult.CONTROL_AWB_STATE) == CaptureResult.CONTROL_AWB_STATE_LOCKED
            } == true
        }
        val result = previewResults.latest
        if (!locked || result == null) {
            return ConvergeOutcome.NotReached(
                "AE and AWB did not both read LOCKED within $CONVERGE_TIMEOUT_MILLIS ms of the lock request",
                result?.get(CaptureResult.CONTROL_AE_STATE),
                result?.get(CaptureResult.CONTROL_AWB_STATE),
            )
        }
        return ConvergeOutcome.Locked(
            convergedMs = convergedMs,
            lockedMs = now(),
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION),
        )
    }

    // ── One run ─────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun burst(run: PlatformRun, count: Int, converged: ConvergeOutcome.Locked): PlatformBurstOutcome {
        checkMainThread()
        val opened = device ?: error("burst() with no platform device open")
        val session = captureSession ?: error("burst() with no platform session")
        val reader = rawReader ?: error("burst() with no RAW reader")
        val size = rawSize ?: error("burst() with no RAW size")
        val chars = characteristics ?: error("burst() with no characteristics")
        val notes: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val requested = mutableListOf<String>()

        val builder = opened.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        builder.addTarget(reader.surface)
        builder.set(CaptureRequest.CONTROL_AE_LOCK, true)
        builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
        requested += "TEMPLATE_STILL_CAPTURE"
        requested += "CONTROL_AE_LOCK=true"
        requested += "CONTROL_AWB_LOCK=true"
        if (afModes().contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            requested += "CONTROL_AF_MODE=CONTINUOUS_PICTURE"
        }
        // The lens-shading map, as stage 2's RAW capture asks for it (CameraXRawCapture.kt): the DNG's gain map comes from it.
        val mapModes = chars.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES)?.toList().orEmpty()
        if (CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON in mapModes) {
            builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
            requested += "STATISTICS_LENS_SHADING_MAP_MODE=ON"
        } else {
            notes += "lens-shading map mode ON is not offered (offered $mapModes); the DNGs carry no gain map"
        }

        when (run) {
            PlatformRun.DEFAULT_LOCKED -> notes += "processing modes are the still-capture template's own; each frame line reports what the camera used"
            PlatformRun.NR_HOTPIXEL_OFF -> {
                val nr = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.toList().orEmpty()
                val hot = chars.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)?.toList().orEmpty()
                val nrOff = CaptureRequest.NOISE_REDUCTION_MODE_OFF in nr
                val hotOff = CaptureRequest.HOT_PIXEL_MODE_OFF in hot
                if (!nrOff && !hotOff) {
                    return PlatformBurstOutcome.Unsupported("neither noise reduction OFF (offered $nr) nor hot-pixel correction OFF (offered $hot) is offered")
                }
                if (nrOff) {
                    builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                    requested += "NOISE_REDUCTION_MODE=OFF"
                } else {
                    notes += "noise reduction OFF is not offered (offered $nr); left at the template's mode"
                }
                if (hotOff) {
                    builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF)
                    requested += "HOT_PIXEL_MODE=OFF"
                } else {
                    notes += "hot-pixel correction OFF is not offered (offered $hot); left at the template's mode"
                }
            }
            PlatformRun.MANUAL_EXPOSURE -> {
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
                if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR !in caps) {
                    return PlatformBurstOutcome.Unsupported("MANUAL_SENSOR is not offered (capabilities $caps)")
                }
                val exposure = converged.exposureTimeNs
                val iso = converged.iso
                if (exposure == null || iso == null) {
                    return PlatformBurstOutcome.Unsupported("the converged result reported no exposure time ($exposure) or ISO ($iso) to fix")
                }
                val minFrame = runCatching { chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR, size) }.getOrNull()
                val frameDuration = maxOf(exposure, converged.frameDurationNs ?: 0L, minFrame ?: 0L)
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
                builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration)
                requested += "CONTROL_AE_MODE=OFF"
                requested += "SENSOR_EXPOSURE_TIME=$exposure"
                requested += "SENSOR_SENSITIVITY=$iso"
                requested += "SENSOR_FRAME_DURATION=$frameDuration"
            }
            PlatformRun.SHADING_OFF -> {
                val shading = chars.get(CameraCharacteristics.SHADING_AVAILABLE_MODES)?.toList().orEmpty()
                if (CaptureRequest.SHADING_MODE_OFF !in shading) {
                    return PlatformBurstOutcome.Unsupported("lens-shading correction OFF is not offered (offered $shading)")
                }
                builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF)
                requested += "SHADING_MODE=OFF"
            }
        }

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val minFrameDuration = runCatching { map?.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR, size) }.getOrNull()
        val stallDuration = runCatching { map?.getOutputStallDuration(ImageFormat.RAW_SENSOR, size) }.getOrNull()

        // Images are acquired as they arrive and held open: the reader has room for all 8 (RAW_MAX_IMAGES), so the burst
        // never waits on the app. Copying and saving happen after the last frame, so they cannot slow the burst.
        val arrivals = Collections.synchronizedList(mutableListOf<Pair<Image, Long>>())
        reader.setOnImageAvailableListener({ r ->
            val at = SystemClock.elapsedRealtimeNanos()
            try {
                r.acquireNextImage()?.let { arrivals += it to at }
            } catch (failure: IllegalStateException) {
                notes += "an image could not be acquired at ${fmt(msSinceTrigger(at))} ms: $failure"
            }
        }, handler)
        val callback = BurstCallback()
        val memoryBefore = memory()
        val requests = List(count) { builder.build() }
        val submitMs = now()
        // captureBurst, not a repeating request: the platform documents a burst as "captured in the minimum amount of time
        // possible, and will not be interleaved with requests submitted by other capture or repeat calls", it delivers
        // exactly [count] frames with no stop to time, and a repeating RAW request would have to be drained at frame rate
        // ("the producer enqueueing additional images may stall until at least one image has been released", ImageReader).
        session.captureBurst(requests, callback, handler)
        val waitMillis = BURST_BASE_TIMEOUT_MILLIS + count * PER_FRAME_TIMEOUT_MILLIS
        awaitCondition(waitMillis) { arrivals.size + callback.failures.size >= count && callback.completedCount() + callback.failures.size >= count }
        reader.setOnImageAvailableListener(null, null)

        val runFolder = PlatformBurstFiles.runFolder(sessionFolder, run)
        val held = synchronized(arrivals) { arrivals.toList() }.sortedBy { it.first.timestamp }
        var peakHeap = memoryBefore.usedBytes
        val saveStart = SystemClock.elapsedRealtimeNanos()
        val frames = mutableListOf<PlatformFrame>()
        val frameBytes = size.width.toLong() * size.height * 2
        withContext(Dispatchers.IO) {
            held.forEachIndexed { index, (image, arrivedAt) ->
                val frameNotes = mutableListOf<String>()
                val timestamp = image.timestamp
                val result = callback.resultFor(timestamp)
                if (result == null) frameNotes += "no capture result with this frame's sensor timestamp"
                val copyStart = System.nanoTime()
                val samples = runCatching { copyRawImage(image) }
                val copyMs = (System.nanoTime() - copyStart) / 1_000_000.0
                image.close()
                peakHeap = maxOf(peakHeap, memory().usedBytes)
                val dynamicBlack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result?.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)?.toList() else null
                val dynamicWhite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result?.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) else null
                val black = dynamicBlack ?: staticBlack(chars)
                val plane = samples.getOrNull()
                val ratio = if (plane != null && black != null) rawCornerToCentre(plane, size.width, size.height, black) else null
                val relative = PlatformBurstFiles.frameFile(runFolder, index)
                val dngOutcome = when {
                    plane == null -> "not saved: the RAW plane could not be copied: ${samples.exceptionOrNull()}"
                    result == null -> "not saved: no capture result to describe the frame"
                    context.filesDir.usableSpace < frameBytes + STORAGE_FLOOR_BYTES ->
                        "not saved: ${context.filesDir.usableSpace} bytes free, below this frame plus the ${STORAGE_FLOOR_BYTES / 1_000_000} MB floor"
                    else -> saveDng(chars, size, plane, result, File(context.filesDir, relative))
                }
                peakHeap = maxOf(peakHeap, memory().usedBytes)
                frames += PlatformFrame(
                    index = index,
                    arrivalMs = msSinceTrigger(arrivedAt),
                    sensorTimestampNs = timestamp,
                    resultMatched = result != null,
                    exposureTimeNs = result?.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                    iso = result?.get(CaptureResult.SENSOR_SENSITIVITY),
                    frameDurationNs = result?.get(CaptureResult.SENSOR_FRAME_DURATION),
                    postRawBoost = result?.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
                    noiseReductionMode = result?.get(CaptureResult.NOISE_REDUCTION_MODE),
                    hotPixelMode = result?.get(CaptureResult.HOT_PIXEL_MODE),
                    edgeMode = result?.get(CaptureResult.EDGE_MODE),
                    shadingMode = result?.get(CaptureResult.SHADING_MODE),
                    tonemapMode = result?.get(CaptureResult.TONEMAP_MODE),
                    blackLevelLock = result?.get(CaptureResult.BLACK_LEVEL_LOCK),
                    lensShadingMapMode = result?.get(CaptureResult.STATISTICS_LENS_SHADING_MAP_MODE),
                    blackLevel = dynamicBlack,
                    whiteLevel = dynamicWhite,
                    copyMs = if (samples.isSuccess) copyMs else null,
                    cornerToCentre = ratio,
                    dngFile = if (dngOutcome == PlatformBurstLines.DNG_WRITTEN) relative else null,
                    dngOutcome = dngOutcome,
                    notes = frameNotes,
                )
            }
        }
        val saveMs = (SystemClock.elapsedRealtimeNanos() - saveStart) / 1_000_000.0
        for (missing in held.size until count) {
            val reason = callback.failures.getOrNull(missing - held.size)
                ?: "the RAW image did not arrive within $waitMillis ms of the burst's submit"
            frames += missingFrame(missing, reason)
        }
        if (held.size > count) notes += "${held.size} images arrived for $count requests"
        if (callback.failures.isNotEmpty()) notes += "capture failures: ${callback.failures}"
        val memoryAfter = memory()
        return PlatformBurstOutcome.Captured(
            frames = frames,
            facts = PlatformRunFacts(
                rawSize = "${size.width}x${size.height}",
                requestMethod = "captureBurst ($count requests in one call: not interleaved with the repeating preview, exactly $count frames, no draining at frame rate)",
                requested = requested,
                minFrameDurationNs = minFrameDuration,
                stallDurationNs = stallDuration,
                maxMemoryBytes = memoryBefore.maxMemoryBytes,
                heapBeforeBytes = memoryBefore.usedBytes,
                heapPeakBytes = maxOf(peakHeap, memoryAfter.usedBytes),
                heapAfterBytes = memoryAfter.usedBytes,
                submitMs = submitMs,
                saveMs = saveMs,
                folder = runFolder,
                notes = notes + callback.startedNotes(),
            ),
        )
    }

    // ── Locks and hand-back ─────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun releaseLocks(): LockRelease {
        checkMainThread()
        val session = captureSession
        val builder = previewBuilder
        if (session == null || builder == null) {
            return LockRelease(cleared = true, detail = "no platform session request carried a lock")
        }
        return try {
            builder.set(CaptureRequest.CONTROL_AE_LOCK, false)
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, false)
            session.setRepeatingRequest(builder.build(), previewResults, handler)
            LockRelease(cleared = true, detail = "the repeating request now carries AE and AWB lock off; closing the session ends every request")
        } catch (failure: Exception) {
            LockRelease(cleared = false, detail = "turning the locks off threw ($failure); closing the session still ends every request that carried them")
        }
    }

    override suspend fun handBack(): PlatformHandback {
        checkMainThread()
        val start = now()
        val details = mutableListOf<String>()

        // The platform side first, so CameraX's reopen never meets a device the probe still holds.
        runCatching { captureSession?.close() }.onFailure { details += "closing the platform session threw: $it" }
        captureSession = null
        previewBuilder = null
        val opened = device
        var platformClosed = true
        if (opened != null) {
            runCatching { opened.close() }.onFailure { details += "closing the platform device threw: $it" }
            val closed = withTimeoutOrNull(DEVICE_CLOSE_TIMEOUT_MILLIS) { deviceClosed.await() }
            if (closed == null) {
                platformClosed = false
                details += "the platform device did not report onClosed within $DEVICE_CLOSE_TIMEOUT_MILLIS ms"
            }
        }
        device = null
        val platformClosedMs = now()
        runCatching { rawReader?.close() }
        runCatching { previewReader?.close() }
        rawReader = null
        previewReader = null

        // Then CameraX: unbinding the placeholder empties the probe's camera, and CameraX resumes the dialog's binding.
        val provider = provider
        val placeholderPreview = placeholder
        var probeUnbound = true
        if (provider != null && placeholderPreview != null) {
            try {
                provider.unbind(placeholderPreview)
                probeUnbound = !provider.isBound(placeholderPreview)
                if (!probeUnbound) details += "the probe's placeholder still reads as bound after unbind"
            } catch (failure: Exception) {
                probeUnbound = false
                details += "unbinding the probe's placeholder threw: $failure"
            }
        }
        owner?.destroy()
        owner = null
        placeholder = null

        var cameraxOpenMs: Double? = null
        if (cameraxStateAtStart == CameraState.Type.OPEN) {
            if (awaitCondition(HANDBACK_TIMEOUT_MILLIS) { cameraxState == CameraState.Type.OPEN }) {
                cameraxOpenMs = now()
            } else {
                details += "CameraX's camera did not read OPEN within $HANDBACK_TIMEOUT_MILLIS ms of the hand-back (last $cameraxState): the dialog's camera may be dead"
            }
        } else if (cameraxInfo != null) {
            details += "the camera read $cameraxStateAtStart, not OPEN, before the probe (no camera dialog open?), so no reopen is expected"
        }
        var viewfinderLiveMs: Double? = null
        if (previewView != null) {
            if (awaitCondition(HANDBACK_TIMEOUT_MILLIS) { viewfinderStream == PreviewView.StreamState.STREAMING }) {
                viewfinderLiveMs = now()
            } else {
                details += "the dialog's viewfinder did not read STREAMING within $HANDBACK_TIMEOUT_MILLIS ms of the hand-back (last $viewfinderStream)"
            }
        }
        stopWatching()
        details += deviceEvents.toList()
        return PlatformHandback(
            platformClosed = platformClosed,
            probeUnbound = probeUnbound,
            handbackStartMs = start,
            platformClosedMs = platformClosedMs,
            cameraxOpenMs = cameraxOpenMs,
            viewfinderLiveMs = viewfinderLiveMs,
            cameraStates = cameraStates.toList(),
            detail = details.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
    }

    /** Ends the probe's own thread. Called once, after the hand-back. */
    fun close() {
        cameraThread.quitSafely()
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────────────────────────────

    private fun watchCameraxState(info: CameraInfo) {
        val observer = Observer<CameraState> { state ->
            cameraxState = state.type
            cameraStates += "${state.type}@${fmt(now())}" + (state.error?.let { " error ${it.code}" } ?: "")
        }
        info.cameraState.observeForever(observer)
        stateObserver = observer
    }

    /** Finds and watches the dialog's viewfinder. Returns what was seen, for the borrow line. */
    private fun watchViewfinder(): String {
        val activity = ResumedActivityTracker.current()
            ?: return "no resumed activity recorded; the viewfinder is not watched"
        val view = activity.window?.decorView?.let { findPreviewView(it) }
            ?: return "no PreviewView in ${activity.javaClass.simpleName}'s window; the viewfinder is not watched"
        previewView = view
        val observer = Observer<PreviewView.StreamState> { stream ->
            viewfinderStream = stream
            cameraStates += "viewfinder $stream@${fmt(now())}"
            if (stream == PreviewView.StreamState.IDLE && viewfinderIdleMs == null && placeholder != null) viewfinderIdleMs = now()
        }
        view.previewStreamState.observeForever(observer)
        streamObserver = observer
        return "PreviewView ${view.implementationMode}, stream ${view.previewStreamState.value}" +
            " (COMPATIBLE draws into a TextureView, which keeps its last frame when the stream stops: a freeze; PERFORMANCE may go blank)"
    }

    private fun stopWatching() {
        stateObserver?.let { cameraxInfo?.cameraState?.removeObserver(it) }
        stateObserver = null
        streamObserver?.let { previewView?.previewStreamState?.removeObserver(it) }
        streamObserver = null
        previewView = null
    }

    private suspend fun openDevice(cameraId: String): CameraDevice = suspendCancellableCoroutine { continuation ->
        deviceClosed = CompletableDeferred()
        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                deviceEvents += "platform device onOpened@${fmt(now())}"
                if (continuation.isActive) continuation.resume(camera) else camera.close()
            }

            override fun onDisconnected(camera: CameraDevice) {
                deviceEvents += "platform device onDisconnected@${fmt(now())} (another client took the camera)"
                camera.close()
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("disconnected before it opened"))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                deviceEvents += "platform device onError ${deviceErrorName(error)}@${fmt(now())}"
                camera.close()
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("onError ${deviceErrorName(error)}"))
            }

            override fun onClosed(camera: CameraDevice) {
                deviceEvents += "platform device onClosed@${fmt(now())}"
                deviceClosed.complete(Unit)
            }
        }
        try {
            manager.openCamera(cameraId, callback, handler)
        } catch (failure: CameraAccessException) {
            continuation.resumeWithException(IllegalStateException("openCamera threw CameraAccessException reason ${failure.reason}: ${failure.message}"))
        } catch (failure: Exception) {
            continuation.resumeWithException(failure)
        }
    }

    @Suppress("DEPRECATION") // createCaptureSession(List<Surface>, ...) is deprecated at API 30 but is the one form at the app's minSdk 26.
    private suspend fun configureSession(opened: CameraDevice, surfaces: List<Surface>): CameraCaptureSession? =
        suspendCancellableCoroutine { continuation ->
            opened.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (continuation.isActive) continuation.resume(session) else session.close()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                handler,
            )
        }

    /** The probe's CameraX placeholder draws into a reader that drops every frame. It is suspended at once and never seen. */
    private fun provideDiscardingSurface(request: SurfaceRequest) {
        val size = request.resolution
        val reader = try {
            ImageReader.newInstance(size.width, size.height, ImageFormat.PRIVATE, PREVIEW_MAX_IMAGES)
        } catch (failure: Exception) {
            request.willNotProvideSurface()
            return
        }
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
        request.provideSurface(reader.surface, mainExecutor) { reader.close() }
    }

    private fun afModes(): List<Int> = characteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList().orEmpty()

    private fun staticBlack(chars: CameraCharacteristics): List<Float>? =
        chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)?.let { pattern -> IntArray(4).also { pattern.copyTo(it, 0) }.map { it.toFloat() } }

    /** Maps the frame with main's [mapRawFrame] and writes it with main's [ForagerDngWriter], whose check after writing fails closed on this phone's identity strings. */
    private fun saveDng(chars: CameraCharacteristics, size: Size, samples: ShortArray, result: TotalCaptureResult, destination: File): String {
        val readings = readings(chars, size, samples, result)
        val image = when (val mapping = mapRawFrame(readings)) {
            is RawFrameMapping.Mapped -> mapping.image
            is RawFrameMapping.Unsupported -> return "not saved: the frame could not be described as a DNG: ${mapping.reason}"
        }
        destination.parentFile?.mkdirs()
        return when (val written = runCatching { writer.write(image, destination) }.getOrElse { DngWriteResult.Failed("the writer threw $it") }) {
            is DngWriteResult.Written -> if (destination.isFile) PlatformBurstLines.DNG_WRITTEN else "not saved: the writer reported ${written.bytes} bytes but there is no file"
            is DngWriteResult.Failed -> {
                destination.delete()
                "not saved: the DNG writer refused the file (${written.stage}): ${written.reason}"
            }
        }
    }

    /** The platform-interface twin of main's `readRawFrame` (photo/CameraXRawCapture.kt), which reads CameraX's camera info instead. */
    private fun readings(chars: CameraCharacteristics, size: Size, samples: ShortArray, result: TotalCaptureResult): RawFrameReadings {
        val shading = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP)
        return RawFrameReadings(
            width = size.width,
            height = size.height,
            samples = samples,
            colorFilterArrangement = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
            staticBlackLevel = chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)?.let { pattern -> IntArray(4).also { pattern.copyTo(it, 0) } },
            dynamicBlackLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL) else null,
            staticWhiteLevel = chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
            dynamicWhiteLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) else null,
            preCorrectionActiveArray = chars.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
                ?.let { intArrayOf(it.left, it.top, it.right, it.bottom) },
            referenceIlluminant1 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1),
            referenceIlluminant2 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt(),
            colorTransform1 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)?.toSRationals(),
            colorTransform2 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)?.toSRationals(),
            forwardMatrix1 = chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)?.toSRationals(),
            forwardMatrix2 = chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)?.toSRationals(),
            calibrationTransform1 = chars.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)?.toSRationals(),
            calibrationTransform2 = chars.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)?.toSRationals(),
            neutralColorPoint = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)?.map { URational(it.numerator.toLong(), it.denominator.toLong()) },
            noiseProfile = result.get(CaptureResult.SENSOR_NOISE_PROFILE)?.map { it.first to it.second },
            lensShadingColumns = shading?.columnCount,
            lensShadingRows = shading?.rowCount,
            lensShadingGains = shading?.let { m -> FloatArray(m.gainFactorCount).also { m.copyGainFactors(it, 0) } },
            postRawSensitivityBoost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
            // The sensor's own landscape frame: these are test frames for measuring noise, read offline, never a photo.
            orientation = 1,
        )
    }

    private fun missingFrame(index: Int, reason: String) = PlatformFrame(
        index = index, arrivalMs = null, sensorTimestampNs = null, resultMatched = false, exposureTimeNs = null, iso = null,
        frameDurationNs = null, postRawBoost = null, noiseReductionMode = null, hotPixelMode = null, edgeMode = null,
        shadingMode = null, tonemapMode = null, blackLevelLock = null, lensShadingMapMode = null, blackLevel = null,
        whiteLevel = null, copyMs = null, cornerToCentre = null, dngFile = null, dngOutcome = null, error = reason,
    )

    private suspend fun awaitCondition(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtimeNanos() + timeoutMillis * 1_000_000L
        while (true) {
            if (condition()) return true
            if (SystemClock.elapsedRealtimeNanos() >= deadline) return false
            delay(POLL_MILLIS)
        }
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) { "the probe's camera calls run on the main thread" }

    /** The latest result of the repeating preview request. */
    private class LatestResult : CameraCaptureSession.CaptureCallback() {
        @Volatile var latest: TotalCaptureResult? = null

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            latest = result
        }
    }

    /** One burst's results by sensor timestamp, its failures with their reasons, and when each exposure started. */
    private inner class BurstCallback : CameraCaptureSession.CaptureCallback() {
        private val results = Collections.synchronizedMap(HashMap<Long, TotalCaptureResult>())
        private val started = Collections.synchronizedList(mutableListOf<String>())
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
            if (started.size < 1) started += "first exposure started at ${fmt(now())} ms (frame $frameNumber)"
        }

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { results[it] = result }
        }

        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            val why = if (failure.reason == CaptureFailure.REASON_FLUSHED) "flushed" else "error"
            failures += "frame ${failure.frameNumber} failed ($why, image captured ${failure.wasImageCaptured()})"
        }

        override fun onCaptureBufferLost(session: CameraCaptureSession, request: CaptureRequest, target: Surface, frameNumber: Long) {
            failures += "frame $frameNumber's RAW buffer was lost"
        }

        fun completedCount(): Int = results.size
        fun resultFor(timestamp: Long): TotalCaptureResult? = results[timestamp]
        fun startedNotes(): List<String> = started.toList()
    }

    /** A lifecycle the probe owns: RESUMED while it holds the camera, DESTROYED when it hands it back. Main thread only. */
    private class ProbeOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        /** The operating limit on the RAW size (CLAUDE.md: a reported range is not a safe one): the binned ~12 MP RAW, never a full-resolution mode. */
        const val RAW_PIXEL_LIMIT = 16_000_000L

        /** Room for a whole run's 8 frames, held until the burst ends (dispatch: "enough buffers for 8 frames"). */
        const val RAW_MAX_IMAGES = 8
        const val PREVIEW_MAX_IMAGES = 3

        /** Saving stops this far above empty, so a test can never fill the phone. */
        const val STORAGE_FLOOR_BYTES = 1_000_000_000L

        const val CAMERAX_CLOSE_TIMEOUT_MILLIS = 3_000L
        const val CONVERGE_TIMEOUT_MILLIS = 3_000L
        const val DEVICE_CLOSE_TIMEOUT_MILLIS = 3_000L
        const val HANDBACK_TIMEOUT_MILLIS = 5_000L

        /** A burst of 8 gets 2 s plus 1 s a frame: the longest exposure the S22 offers is 164 ms (investigation A.1). */
        const val BURST_BASE_TIMEOUT_MILLIS = 2_000L
        const val PER_FRAME_TIMEOUT_MILLIS = 1_000L
        const val POLL_MILLIS = 5L

        /** CONVERGED, LOCKED or FLASH_REQUIRED: the exposure has settled. */
        val AE_CONVERGED = setOf(
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
        )

        /** The probe's preview: PRIVATE, at most 1920x1080 in area, the RAW's aspect where offered. */
        fun choosePreviewSize(sizes: List<Size>, raw: Size): Size? {
            val bounded = sizes.filter { it.width.toLong() * it.height <= 1920L * 1080 }
            val aspect = raw.width.toDouble() / raw.height
            val sameAspect = bounded.filter { kotlin.math.abs(it.width.toDouble() / it.height - aspect) < 0.01 }
            return (sameAspect.ifEmpty { bounded }).maxByOrNull { it.width.toLong() * it.height }
        }

        fun deviceErrorName(error: Int): String = when (error) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "ERROR_CAMERA_IN_USE"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "ERROR_MAX_CAMERAS_IN_USE"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "ERROR_CAMERA_DISABLED"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "ERROR_CAMERA_DEVICE"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "ERROR_CAMERA_SERVICE"
            else -> "error $error"
        }

        fun fmt(ms: Double): String = String.format(Locale.ROOT, "%.3f", ms)
    }
}

/**
 * Copies a RAW_SENSOR image's one plane into a row-major [ShortArray], honouring the row stride. The platform-interface
 * twin of main's `copyRawPlane` (which takes CameraX's ImageProxy); throws on anything but one 2-byte plane, never
 * guessing a layout.
 */
private fun copyRawImage(image: Image): ShortArray {
    check(image.format == ImageFormat.RAW_SENSOR) { "not a RAW_SENSOR image (format ${image.format})" }
    val plane = image.planes.singleOrNull() ?: error("a RAW_SENSOR image with ${image.planes.size} planes")
    check(plane.pixelStride == 2) { "RAW_SENSOR pixel stride ${plane.pixelStride}, not 2" }
    val width = image.width
    val height = image.height
    val samples = ShortArray(width * height)
    val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
    for (row in 0 until height) {
        buffer.position(row * plane.rowStride)
        buffer.asShortBuffer().get(samples, row * width, width)
    }
    return samples
}

private fun ColorSpaceTransform.toSRationals(): List<SRational> {
    val elements = arrayOfNulls<Rational>(9)
    copyElements(elements, 0)
    return elements.map { r -> SRational(r!!.numerator, r.denominator) }
}
