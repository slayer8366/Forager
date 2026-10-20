package com.zynergylabs.forager.app.diagnostics.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
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
import com.zynergylabs.forager.app.diagnostics.burst.PlatformBurstCameraImpl
import com.zynergylabs.forager.app.diagnostics.burst.ResumedActivityTracker
import com.zynergylabs.forager.app.diagnostics.burst.findPreviewView
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Part 3's camera (dispatch 2026-10-15-12): CameraX lets the camera go, the platform camera interface makes three
 * attempts at RAW from the main wide sensor only, and CameraX gets the camera back. Debug source set only. Nothing is
 * saved: each RAW image is read for its size and timestamp and closed.
 *
 * The borrow and the hand-back are the fast-burst probe's (`PlatformBurstCameraImpl`, read and copied, not edited): a
 * placeholder preview bound to the probe's own lifecycle suspends the dialog's binding, the placeholder is then suspended
 * so nothing is attached, the probe waits for CameraX's state to read CLOSED before it opens anything, and at the end it
 * closes every platform device it opened, awaits `onClosed`, unbinds the placeholder (which unsuspends the dialog's
 * binding as it was bound, so the user's chosen format is back) and waits for OPEN and the viewfinder STREAMING.
 *
 * ## What the platform documents about each attempt (READ, AOSP frameworks/base, the camera2 sources' own docs)
 *
 * 1. Physical stream. `OutputConfiguration.setPhysicalCameraId` "maps the OutputConfiguration to the physical camera
 *    id", one of `getPhysicalCameraIds()`. The multi-camera guide's only guarantee is that a logical stream "of type YUV or
 *    RAW can be replaced with two streams of identical type and size" from two physical cameras of the group; one RAW
 *    physical stream beside a logical preview is not in a guaranteed table, so whether it configures is asked of the
 *    phone (`isSessionConfigurationSupported`, API 29) and then tried.
 * 2. Direct open. `getCameraIdList` "doesn't contain physical cameras that can only be used as part of a logical
 *    multi-camera device", and `openCamera` throws IllegalArgumentException when the ID "does not match any currently or
 *    previously available camera device". On both test phones physical camera 5 is not in the list (dumpsys: public IDs
 *    0, 1 and 3), so a refusal is expected; it is tried and its error logged, not assumed.
 * 3. Logical control. The platform documents that "in case a RAW stream is configured, to maintain backward
 *    compatibility, the camera device makes sure the default active physical camera remains active and does not switch to
 *    other physical cameras" (CameraMetadata, LOGICAL_MULTI_CAMERA). The control checks that on these phones.
 */
@SuppressLint("RestrictedApi", "MissingPermission")
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class PlatformPinCamera(private val context: Context) : PinCamera {

    private val triggerNanos = SystemClock.elapsedRealtimeNanos()
    private fun now(): Double = (SystemClock.elapsedRealtimeNanos() - triggerNanos) / 1_000_000.0

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val cameraThread = HandlerThread("SensorPinCamera").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val handlerExecutor = Executor { handler.post(it) }
    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)

    // CameraX's side.
    private var provider: ProcessCameraProvider? = null
    private var selector: CameraSelector? = null
    private var logicalId: String? = null
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

    // Platform devices this probe opened and has not seen close.
    private val openDevices: MutableList<CameraDevice> = Collections.synchronizedList(mutableListOf())
    private val deviceEvents: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val closedSignals: MutableMap<String, CompletableDeferred<Unit>> = Collections.synchronizedMap(HashMap())

    /** Opens asked for that have not yet had their first answer (onOpened, onError, onDisconnected, or a throw). */
    private val pendingOpens = AtomicInteger(0)

    // ── Survey ──────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun survey(): PinSurvey {
        checkMainThread()
        val provider = ProcessCameraProvider.awaitInstance(context)
        this.provider = provider
        val chosen = when {
            runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
            runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> return PinSurvey.Unsupported("this device reports no back or front camera", null, emptyList())
        }
        selector = chosen
        val info = provider.getCameraInfo(chosen)
        cameraxInfo = info
        val id = Camera2CameraInfo.from(info).cameraId
        logicalId = id
        val listed = manager.cameraIdList.toSet()
        val logical = facts(id, listed)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PinSurvey.Unsupported("physical camera IDs need API 28; this is ${Build.VERSION.SDK_INT}", logical, emptyList())
        }
        val physicalIds = manager.getCameraCharacteristics(id).physicalCameraIds.toList()
        val physicals = physicalIds.map { facts(it, listed) }
        if (physicalIds.isEmpty()) {
            return PinSurvey.Unsupported("camera $id lists no physical cameras: it is not a logical multi-camera", logical, physicals)
        }
        return when (val choice = MainWideChooser.choose(logical, physicals)) {
            is MainWideChoice.Chosen -> PinSurvey.Ready(logical, physicals, choice.cameraId, choice.reason)
            is MainWideChoice.NotFound -> PinSurvey.Unsupported(choice.reason, logical, physicals)
        }
    }

    // ── Borrow ──────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun borrow(): PinBorrow {
        checkMainThread()
        val notes = mutableListOf<String>()
        fun detail() = notes + cameraStates.toList()
        val provider = provider ?: return PinBorrow.Failed("borrow() before a survey found the camera", detail())
        val selector = selector ?: return PinBorrow.Failed("borrow() before a survey chose a camera", detail())
        val info = cameraxInfo ?: return PinBorrow.Failed("borrow() with no camera info", detail())
        watchCameraxState(info)
        cameraxStateAtStart = cameraxState
        notes += watchViewfinder()

        val placeholderPreview = Preview.Builder().build()
        placeholderPreview.setSurfaceProvider(mainExecutor) { request -> provideDiscardingSurface(request) }
        placeholder = placeholderPreview
        val newOwner = ProbeOwner().also { it.resume() }
        owner = newOwner
        val bound = try {
            provider.bindToLifecycle(newOwner, selector, placeholderPreview)
        } catch (failure: Exception) {
            return PinBorrow.Failed("binding the probe's placeholder threw: $failure", detail())
        }
        val lifecycleCamera = bound as? LifecycleCamera
            ?: return PinBorrow.Failed("the bound camera is ${bound.javaClass.name}, not CameraX's LifecycleCamera; it cannot be suspended", detail())
        lifecycleCamera.suspend()
        notes += "placeholder bound and suspended at ${fmt(now())} ms"
        if (!awaitCondition(CAMERAX_CLOSE_TIMEOUT_MILLIS) { cameraxState == CameraState.Type.CLOSED }) {
            return PinBorrow.Failed(
                "CameraX's camera state read $cameraxState, not CLOSED, $CAMERAX_CLOSE_TIMEOUT_MILLIS ms after it was asked to let go; " +
                    "no platform open was attempted",
                detail(),
            )
        }
        notes += "CameraX CLOSED at ${fmt(now())} ms"
        return PinBorrow.Borrowed(detail())
    }

    // ── One attempt ─────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun attempt(attempt: PinAttempt, survey: PinSurvey.Ready, burstFrames: Int): PinAttemptResult {
        checkMainThread()
        val logical = logicalId ?: error("attempt() before a survey")
        val mainWide = survey.mainWideId.ifEmpty { null }
        val notes: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val openId: String
        val physicalStreamId: String?
        when (attempt) {
            PinAttempt.PHYSICAL_STREAM -> {
                openId = logical
                physicalStreamId = mainWide ?: error("the physical-stream attempt needs a main wide")
            }
            PinAttempt.PHYSICAL_DIRECT -> {
                openId = mainWide ?: error("the direct attempt needs a main wide")
                physicalStreamId = null
            }
            PinAttempt.LOGICAL_CONTROL -> {
                openId = logical
                physicalStreamId = null
            }
        }
        val rawSource = physicalStreamId ?: openId
        var configured: Boolean? = null
        var sessionQuery: String? = null
        var rawSizeText: String? = null
        val frames = mutableListOf<PinFrame>()
        var failureText: String? = null

        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var rawReader: ImageReader? = null
        var previewReader: ImageReader? = null
        try {
            if (attempt == PinAttempt.PHYSICAL_DIRECT) {
                val listed = manager.cameraIdList.toList()
                notes += "camera $openId ${if (openId in listed) "is" else "is not"} in getCameraIdList $listed"
            }
            val sourceChars = manager.getCameraCharacteristics(rawSource)
            val raw = rawSize(sourceChars)
                ?: return result(attempt, openId, physicalStreamId, null, null, null, frames, "camera $rawSource offers no RAW_SENSOR size at or under 16 MP", notes)
            rawSizeText = "${raw.width}x${raw.height}"
            val openChars = manager.getCameraCharacteristics(openId)
            val preview = PlatformBurstCameraImpl.choosePreviewSize(
                openChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.PRIVATE)?.toList().orEmpty(),
                raw,
            ) ?: return result(attempt, openId, physicalStreamId, null, null, rawSizeText, frames, "camera $openId offers no PRIVATE size at or under 1920x1080", notes)

            val opened = openDevice(openId)
            device = opened
            notes += "camera $openId opened at ${fmt(now())} ms"

            val rawImages = ImageReader.newInstance(raw.width, raw.height, ImageFormat.RAW_SENSOR, RAW_MAX_IMAGES)
            rawReader = rawImages
            val previewImages = ImageReader.newInstance(preview.width, preview.height, ImageFormat.PRIVATE, PREVIEW_MAX_IMAGES)
            previewImages.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
            previewReader = previewImages
            val rawOutput = OutputConfiguration(rawImages.surface)
            // physicalStreamId is non-null only with a main wide, which survey() finds only past its SDK_INT < P return.
            @Suppress("NewApi") // Kotlin's annotation: SuppressLint cannot target an expression
            if (physicalStreamId != null) rawOutput.setPhysicalCameraId(physicalStreamId)
            val outputs = listOf(OutputConfiguration(previewImages.surface), rawOutput)
            sessionQuery = querySession(opened, outputs)

            val configuredSession = configureSessionBounded(opened, outputs)
            configured = configuredSession != null
            if (configuredSession == null) {
                return result(
                    attempt, openId, physicalStreamId, false, sessionQuery, rawSizeText, frames,
                    "the session would not configure PRIV ${preview.width}x${preview.height} + RAW_SENSOR $rawSizeText" +
                        (physicalStreamId?.let { " on physical camera $it" } ?: "") + " (onConfigureFailed)",
                    notes,
                )
            }
            session = configuredSession

            // A running preview, so AE and AF act as they do in use; zoom 1.0 on the logical camera.
            val previewResults = LatestResult()
            val previewRequest = opened.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewImages.surface)
                if (openId == logical && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) set(CaptureRequest.CONTROL_ZOOM_RATIO, 1f)
            }
            configuredSession.setRepeatingRequest(previewRequest.build(), previewResults, handler)
            val settled = awaitCondition(SETTLE_TIMEOUT_MILLIS) {
                previewResults.latest?.get(CaptureResult.CONTROL_AE_STATE)?.let { it in AE_SETTLED } == true
            }
            notes += if (settled) "AE settled at ${fmt(now())} ms" else "AE did not settle within $SETTLE_TIMEOUT_MILLIS ms; captured anyway"
            previewResults.latest?.let { r ->
                notes += "preview active physical camera ${activeId(r)}, zoom ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) r.get(CaptureResult.CONTROL_ZOOM_RATIO) else null}"
            }

            val still = opened.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(rawImages.surface)
                if (openId == logical && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) set(CaptureRequest.CONTROL_ZOOM_RATIO, 1f)
            }.build()
            val cfaCache = HashMap<String, Int?>()
            frames += captureFrames(configuredSession, rawImages, listOf(still), "single", 0, physicalStreamId, openId, cfaCache, notes)
            frames += captureFrames(configuredSession, rawImages, List(burstFrames) { still }, "burst", 1, physicalStreamId, openId, cfaCache, notes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureText = when {
                device == null -> "camera $openId was not opened: ${failure.message ?: failure}"
                else -> "the attempt failed: $failure"
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { session?.close() }.onFailure { notes += "closing the session threw: $it" }
                device?.let { closeDevice(it, notes) }
                runCatching { rawReader?.close() }
                runCatching { previewReader?.close() }
            }
        }
        return result(attempt, openId, physicalStreamId, configured, sessionQuery, rawSizeText, frames, failureText, notes)
    }

    // ── Hand-back ───────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun handBack(): PinHandback {
        checkMainThread()
        val details = mutableListOf<String>()
        // An open an attempt was cut off from may still answer; wait for it, so its device is closed below, not after CameraX reopens.
        if (!awaitCondition(OPEN_TIMEOUT_MILLIS) { pendingOpens.get() == 0 }) {
            details += "${pendingOpens.get()} camera open(s) had not answered within $OPEN_TIMEOUT_MILLIS ms of the hand-back"
        }
        // Any device an attempt could not see close is closed again first, so CameraX's reopen never meets one.
        synchronized(openDevices) { openDevices.toList() }.forEach { closeDevice(it, details) }
        val platformClosed = synchronized(openDevices) { openDevices.isEmpty() }
        if (!platformClosed) details += "${openDevices.size} platform device(s) did not report onClosed"

        val provider = provider
        val placeholderPreview = placeholder
        var unbound = true
        if (provider != null && placeholderPreview != null) {
            try {
                provider.unbind(placeholderPreview)
                unbound = !provider.isBound(placeholderPreview)
                if (!unbound) details += "the probe's placeholder still reads as bound after unbind"
            } catch (failure: Exception) {
                unbound = false
                details += "unbinding the probe's placeholder threw: $failure"
            }
        }
        owner?.destroy()
        owner = null
        placeholder = null

        var cameraxBack = unbound
        if (cameraxStateAtStart == CameraState.Type.OPEN) {
            if (awaitCondition(HANDBACK_TIMEOUT_MILLIS) { cameraxState == CameraState.Type.OPEN }) {
                details += "CameraX OPEN at ${fmt(now())} ms"
            } else {
                cameraxBack = false
                details += "CameraX's camera did not read OPEN within $HANDBACK_TIMEOUT_MILLIS ms of the hand-back (last $cameraxState): the dialog's camera may be dead"
            }
        } else if (cameraxInfo != null) {
            details += "the camera read $cameraxStateAtStart, not OPEN, before the probe (no camera dialog open?), so no reopen is expected"
        }
        if (previewView != null) {
            if (awaitCondition(HANDBACK_TIMEOUT_MILLIS) { viewfinderStream == PreviewView.StreamState.STREAMING }) {
                details += "viewfinder STREAMING at ${fmt(now())} ms"
            } else {
                details += "the dialog's viewfinder did not read STREAMING within $HANDBACK_TIMEOUT_MILLIS ms (last $viewfinderStream)"
            }
        }
        stopWatching()
        details += cameraStates.toList()
        details += deviceEvents.toList()
        return PinHandback(platformClosed = platformClosed, cameraxBack = cameraxBack, detail = details.joinToString("; ").ifEmpty { null })
    }

    /** Ends the probe's own thread. Called once, after the hand-back. */
    fun close() {
        cameraThread.quitSafely()
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────────────────────────────

    private fun facts(id: String, listed: Set<String>): LensFacts = try {
        val chars = manager.getCameraCharacteristics(id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixels = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        LensFacts(
            cameraId = id,
            inCameraIdList = id in listed,
            focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
            physicalSizeMm = size?.let { listOf(it.width, it.height) },
            cfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
            pixelArray = pixels?.let { "${it.width}x${it.height}" },
            rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.map { "${it.width}x${it.height}" }.orEmpty(),
            capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty(),
        )
    } catch (failure: Exception) {
        LensFacts(id, id in listed, emptyList(), null, null, null, emptyList(), emptyList(), error = "$failure")
    }

    /** The largest RAW size within the fast-burst probe's operating limit (the binned ~12 MP, never a full-resolution mode). */
    private fun rawSize(chars: CameraCharacteristics): Size? =
        chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList().orEmpty()
            .filter { it.width.toLong() * it.height <= PlatformBurstCameraImpl.RAW_PIXEL_LIMIT }
            .maxByOrNull { it.width.toLong() * it.height }

    private fun activeId(result: CaptureResult): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null

    @Suppress("DEPRECATION") // getPhysicalCameraResults is deprecated at API 31, where getPhysicalCameraTotalResults replaces it.
    private fun physicalResultIds(result: TotalCaptureResult): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> result.physicalCameraTotalResults.keys.sorted()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> result.physicalCameraResults.keys.sorted()
        else -> emptyList()
    }

    private fun cfaOf(id: String?, cache: MutableMap<String, Int?>): Int? {
        if (id == null) return null
        return cache.getOrPut(id) { runCatching { manager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT) }.getOrNull() }
    }

    /**
     * Submits [requests] (one `capture`, or one `captureBurst`), waits for their RAW images and results, and reads each
     * image's size and timestamp before closing it. Frames are numbered from [firstIndex].
     */
    private suspend fun captureFrames(
        session: CameraCaptureSession,
        reader: ImageReader,
        requests: List<CaptureRequest>,
        kind: String,
        firstIndex: Int,
        physicalStreamId: String?,
        openId: String,
        cfaCache: MutableMap<String, Int?>,
        notes: MutableList<String>,
    ): List<PinFrame> {
        val images = Collections.synchronizedList(mutableListOf<Triple<Long, Int, Int>>())
        reader.setOnImageAvailableListener({ r ->
            try {
                r.acquireNextImage()?.use { image -> images += Triple(image.timestamp, image.width, image.height) }
            } catch (failure: IllegalStateException) {
                notes += "a RAW image could not be acquired: $failure"
            }
        }, handler)
        val callback = FramesCallback()
        if (requests.size == 1) session.capture(requests.single(), callback, handler) else session.captureBurst(requests, callback, handler)
        val count = requests.size
        val waitMillis = BASE_TIMEOUT_MILLIS + count * PER_FRAME_TIMEOUT_MILLIS
        awaitCondition(waitMillis) { images.size + callback.failures.size >= count && callback.completed() + callback.failures.size >= count }
        reader.setOnImageAvailableListener(null, null)

        val arrived = synchronized(images) { images.toList() }.sortedBy { it.first }
        val frames = arrived.mapIndexed { i, (timestamp, width, height) ->
            val result = callback.resultFor(timestamp)
            val active = result?.let { activeId(it) }
            val attributedTo = physicalStreamId ?: active ?: openId.takeIf { it != logicalId }
            PinFrame(
                index = firstIndex + i,
                kind = kind,
                sensorTimestampNs = timestamp,
                activePhysicalId = active,
                physicalResultIds = result?.let { physicalResultIds(it) }.orEmpty(),
                attributedTo = attributedTo,
                rawSize = "${width}x$height",
                cfa = cfaOf(attributedTo, cfaCache),
                error = null,
            )
        }.toMutableList()
        val missingResults = arrived.count { callback.resultFor(it.first) == null }
        if (missingResults > 0) notes += "$missingResults $kind RAW image(s) had no capture result with the same timestamp, so no active camera"
        for (missing in arrived.size until count) {
            frames += PinFrame(
                index = firstIndex + missing, kind = kind, sensorTimestampNs = null, activePhysicalId = null, physicalResultIds = emptyList(),
                attributedTo = null, rawSize = null, cfa = null,
                error = callback.failures.getOrNull(missing - arrived.size) ?: "the RAW image did not arrive within $waitMillis ms",
            )
        }
        return frames
    }

    /**
     * Opens [cameraId], bounded (dispatch 2026-10-16-01): an open that reports neither onOpened, onError nor
     * onDisconnected within [OPEN_TIMEOUT_MILLIS] throws, so the attempt ends and its `finally` runs. A device that opens
     * after that closes itself in its own callback, and the hand-back waits for any open still unanswered.
     */
    private suspend fun openDevice(cameraId: String): CameraDevice =
        withTimeoutOrNull(OPEN_TIMEOUT_MILLIS) { openDeviceUnbounded(cameraId) }
            ?: throw IllegalStateException("camera $cameraId reported neither onOpened, onError nor onDisconnected within $OPEN_TIMEOUT_MILLIS ms")

    private suspend fun openDeviceUnbounded(cameraId: String): CameraDevice = suspendCancellableCoroutine { continuation ->
        val answered = AtomicBoolean(false)
        fun answer() {
            if (answered.compareAndSet(false, true)) pendingOpens.decrementAndGet()
        }
        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                answer()
                deviceEvents += "camera ${camera.id} onOpened@${fmt(now())}"
                openDevices += camera
                if (continuation.isActive) continuation.resume(camera) else camera.close()
            }

            override fun onDisconnected(camera: CameraDevice) {
                answer()
                deviceEvents += "camera ${camera.id} onDisconnected@${fmt(now())}"
                camera.close()
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("disconnected before it opened"))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                answer()
                deviceEvents += "camera ${camera.id} onError ${PlatformBurstCameraImpl.deviceErrorName(error)}@${fmt(now())}"
                camera.close()
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("onError ${PlatformBurstCameraImpl.deviceErrorName(error)}"))
            }

            override fun onClosed(camera: CameraDevice) {
                deviceEvents += "camera ${camera.id} onClosed@${fmt(now())}"
                openDevices.remove(camera)
                closedSignals.remove(camera.id)?.complete(Unit)
            }
        }
        pendingOpens.incrementAndGet()
        try {
            manager.openCamera(cameraId, callback, handler)
        } catch (failure: CameraAccessException) {
            // A refused open may still post onDisconnected or onError to the callback (inferred, not verified); either way it is answered.
            answer()
            continuation.resumeWithException(IllegalStateException("openCamera threw CameraAccessException reason ${failure.reason}: ${failure.message}"))
        } catch (failure: Exception) {
            answer()
            continuation.resumeWithException(IllegalStateException("openCamera threw $failure"))
        }
    }

    /** Closes [device] and waits for `onClosed`; a device that does not report it stays in [openDevices] and is reported. */
    private suspend fun closeDevice(device: CameraDevice, notes: MutableList<String>) {
        if (device !in openDevices) return
        val signal = CompletableDeferred<Unit>()
        closedSignals[device.id] = signal
        runCatching { device.close() }.onFailure { notes += "closing camera ${device.id} threw: $it" }
        if (withTimeoutOrNull(DEVICE_CLOSE_TIMEOUT_MILLIS) { signal.await() } == null && device in openDevices) {
            notes += "camera ${device.id} did not report onClosed within $DEVICE_CLOSE_TIMEOUT_MILLIS ms"
        }
    }

    private fun querySession(device: CameraDevice, outputs: List<OutputConfiguration>): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "not asked: isSessionConfigurationSupported needs API 29"
        return try {
            val config = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                handlerExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) = Unit
                    override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                },
            )
            if (device.isSessionConfigurationSupported(config)) "supported" else "not supported"
        } catch (failure: Exception) {
            "the query threw: $failure"
        }
    }

    /** One configure's answer: the session, or null for onConfigureFailed. */
    private class Configured(val session: CameraCaptureSession?)

    /**
     * Configures, bounded (dispatch 2026-10-16-01): null for onConfigureFailed; a configure that reports neither callback
     * within [CONFIGURE_TIMEOUT_MILLIS] throws. A session configured after that closes itself in its own callback.
     */
    private suspend fun configureSessionBounded(device: CameraDevice, outputs: List<OutputConfiguration>): CameraCaptureSession? =
        (withTimeoutOrNull(CONFIGURE_TIMEOUT_MILLIS) { Configured(configureSession(device, outputs)) }
            ?: throw IllegalStateException("the session reported neither onConfigured nor onConfigureFailed within $CONFIGURE_TIMEOUT_MILLIS ms")).session

    @Suppress("DEPRECATION") // createCaptureSessionByOutputConfigurations is deprecated at API 30 but is the one form at the app's minSdk 26.
    private suspend fun configureSession(device: CameraDevice, outputs: List<OutputConfiguration>): CameraCaptureSession? =
        suspendCancellableCoroutine { continuation ->
            try {
                device.createCaptureSessionByOutputConfigurations(
                    outputs,
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
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }

    private fun watchCameraxState(info: CameraInfo) {
        val observer = Observer<CameraState> { state ->
            cameraxState = state.type
            cameraStates += "CameraX ${state.type}@${fmt(now())}" + (state.error?.let { " error ${it.code}" } ?: "")
        }
        info.cameraState.observeForever(observer)
        stateObserver = observer
    }

    private fun watchViewfinder(): String {
        val activity = ResumedActivityTracker.current()
            ?: return "no resumed activity recorded; the viewfinder is not watched"
        val view = activity.window?.decorView?.let { findPreviewView(it) }
            ?: return "no PreviewView in ${activity.javaClass.simpleName}'s window; the viewfinder is not watched"
        previewView = view
        val observer = Observer<PreviewView.StreamState> { stream ->
            viewfinderStream = stream
            cameraStates += "viewfinder $stream@${fmt(now())}"
        }
        view.previewStreamState.observeForever(observer)
        streamObserver = observer
        return "viewfinder watched, stream ${view.previewStreamState.value}"
    }

    private fun stopWatching() {
        stateObserver?.let { cameraxInfo?.cameraState?.removeObserver(it) }
        stateObserver = null
        streamObserver?.let { previewView?.previewStreamState?.removeObserver(it) }
        streamObserver = null
        previewView = null
    }

    /** The placeholder draws into a reader that drops every frame. It is suspended at once and never seen. */
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

    private fun result(
        attempt: PinAttempt,
        openId: String?,
        physicalStreamId: String?,
        configured: Boolean?,
        sessionQuery: String?,
        rawSize: String?,
        frames: List<PinFrame>,
        error: String?,
        notes: List<String>,
    ) = PinAttemptResult(attempt, openId, physicalStreamId, configured, sessionQuery, rawSize, frames.toList(), error, notes.toList())

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

    /** One capture's or burst's results by sensor timestamp, and its failures with their reasons. */
    private class FramesCallback : CameraCaptureSession.CaptureCallback() {
        private val results = Collections.synchronizedMap(HashMap<Long, TotalCaptureResult>())
        val failures: MutableList<String> = Collections.synchronizedList(mutableListOf())

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

        fun completed(): Int = results.size
        fun resultFor(timestamp: Long): TotalCaptureResult? = results[timestamp]
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

    private companion object {
        const val RAW_MAX_IMAGES = 8
        const val PREVIEW_MAX_IMAGES = 3
        const val CAMERAX_CLOSE_TIMEOUT_MILLIS = 3_000L
        const val SETTLE_TIMEOUT_MILLIS = 3_000L
        const val DEVICE_CLOSE_TIMEOUT_MILLIS = 3_000L
        const val OPEN_TIMEOUT_MILLIS = 5_000L
        const val CONFIGURE_TIMEOUT_MILLIS = 5_000L
        const val HANDBACK_TIMEOUT_MILLIS = 5_000L
        const val BASE_TIMEOUT_MILLIS = 2_000L
        const val PER_FRAME_TIMEOUT_MILLIS = 1_000L
        const val POLL_MILLIS = 5L

        /** CONVERGED, LOCKED or FLASH_REQUIRED: the exposure has settled. */
        val AE_SETTLED = setOf(
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
        )

        fun fmt(ms: Double): String = String.format(Locale.ROOT, "%.3f", ms)
    }
}
