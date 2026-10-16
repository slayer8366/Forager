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
import com.zynergylabs.forager.app.diagnostics.burst.PlatformBurstCameraImpl
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
 * The reach probe's camera (dispatch 2026-10-16-01): every camera the phone describes, read from characteristics; then,
 * with the dialog's camera borrowed from CameraX, RAW attempts on each back camera through the platform camera
 * interface. Debug source set only. Nothing is saved: each RAW image is read for its size and timestamp and closed.
 *
 * The borrow and the hand-back are the pin probe's own ([PlatformPinCamera], used, not copied): its survey finds the
 * dialog's camera, its borrow makes CameraX let go, and its hand-back gives the camera back. This class closes every
 * device it opened itself, waiting for any open still unanswered, before it asks for that hand-back.
 *
 * Every wait here is bounded, so an attempt always ends and its `finally` always closes what it opened: the open 5 s,
 * the configure 5 s, the exposure settling 3 s, the single frame 3 s, the burst 2 s plus 1 s a frame, each close 3 s.
 * [ReachProbe] cuts off any attempt that still overruns.
 *
 * ## What each attempt is (the platform's own documentation, as READ for the pin probe; see PlatformPinCamera's header)
 *
 * (a) A RAW stream with `OutputConfiguration.setPhysicalCameraId` on a logical camera that lists the physical camera.
 *     One physical RAW stream beside a logical preview is not in a guaranteed table, so the phone is asked first
 *     (`isSessionConfigurationSupported`, API 29), and a no is logged `unsupported` without configuring.
 * (b) `openCamera` with the camera's own ID, made only for IDs in `getCameraIdList`.
 * (c) Two physical RAW streams of one size on one logical camera: the multi-camera guarantee is that a logical RAW
 *     stream "can be replaced with two streams of identical type and size" from two of its physical cameras. One frame
 *     only, for the outcome.
 */
@SuppressLint("MissingPermission")
internal class PlatformReachCamera(private val context: Context) : ReachCamera {

    private val triggerNanos = SystemClock.elapsedRealtimeNanos()
    private fun now(): Double = (SystemClock.elapsedRealtimeNanos() - triggerNanos) / 1_000_000.0

    private val cameraThread = HandlerThread("SensorReachCamera").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val handlerExecutor = Executor { handler.post(it) }
    private val manager: CameraManager = context.getSystemService(CameraManager::class.java)

    /** CameraX's side: the pin probe's borrow and hand-back. */
    private val cameraX = PlatformPinCamera(context)

    // Platform devices this probe opened and has not seen close.
    private val openDevices: MutableList<CameraDevice> = Collections.synchronizedList(mutableListOf())
    private val deviceEvents: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val closedSignals: MutableMap<String, CompletableDeferred<Unit>> = Collections.synchronizedMap(HashMap())
    private val pendingOpens = AtomicInteger(0)

    // ── Survey ──────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun survey(): ReachSurvey {
        checkMainThread()
        val notes = mutableListOf<String>()
        // The pin probe's survey finds the dialog's camera for the borrow. It reads characteristics only.
        when (val found = cameraX.survey()) {
            is PinSurvey.Ready -> notes += "the dialog's camera is ${found.logical.cameraId}"
            is PinSurvey.Unsupported -> notes += "the dialog's camera: ${found.reason}"
        }
        val listed = manager.cameraIdList.toList()
        val physicalsOf = LinkedHashMap<String, List<String>>()
        for (id in listed) {
            physicalsOf[id] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { manager.getCameraCharacteristics(id).physicalCameraIds.toList() }
                    .onFailure { notes += "camera $id's physical IDs could not be read: $it" }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) notes += "physical camera IDs need API 28; this is ${Build.VERSION.SDK_INT}"
        val ids = LinkedHashSet<String>(listed)
        physicalsOf.values.forEach { ids += it }
        val cameras = ids.map { id ->
            entry(id, id in listed, physicalsOf[id].orEmpty(), physicalsOf.filterValues { id in it }.keys.toList())
        }
        return ReachSurvey(device = "${Build.MANUFACTURER} ${Build.MODEL}", cameraIdList = listed, cameras = cameras, notes = notes)
    }

    private fun entry(id: String, listed: Boolean, physicalIds: List<String>, logicalIds: List<String>): CameraEntry = try {
        val chars = manager.getCameraCharacteristics(id)
        val size = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val pixels = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        CameraEntry(
            cameraId = id,
            inCameraIdList = listed,
            facing = chars.get(CameraCharacteristics.LENS_FACING)?.let { facingName(it) },
            focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
            physicalSizeMm = size?.let { listOf(it.width, it.height) },
            cfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
            pixelArray = pixels?.let { "${it.width}x${it.height}" },
            rawSizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.RAW_SENSOR)
                ?.map { "${it.width}x${it.height}" }.orEmpty(),
            capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty(),
            physicalIds = physicalIds,
            logicalIds = logicalIds,
        )
    } catch (failure: Exception) {
        CameraEntry(id, listed, null, emptyList(), null, null, null, emptyList(), emptyList(), physicalIds, logicalIds, error = "$failure")
    }

    // ── Borrow ──────────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun borrow(): PinBorrow = cameraX.borrow()

    // ── One attempt ─────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun attempt(attempt: ReachAttempt, burstFrames: Int): ReachResult {
        checkMainThread()
        val notes: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val openId: String
        /** Each RAW stream's camera, and whether it is bound to a physical camera of [openId]. */
        val streams: List<String>
        val physicalBound: Boolean
        when (attempt) {
            is ReachAttempt.ThroughLogical -> {
                openId = attempt.logicalId
                streams = listOf(attempt.physicalId)
                physicalBound = true
            }
            is ReachAttempt.Direct -> {
                openId = attempt.cameraId
                streams = listOf(attempt.cameraId)
                physicalBound = false
            }
            is ReachAttempt.TwoStreams -> {
                openId = attempt.logicalId
                streams = listOf(attempt.first, attempt.second)
                physicalBound = true
            }
        }
        val pair = attempt is ReachAttempt.TwoStreams
        val burstAsked = if (pair) 0 else burstFrames
        var rawSizeText: String? = null
        var opened = false
        var sessionQuery: String? = null
        var configured: Boolean? = null
        val frames = mutableListOf<ReachFrame>()
        var stoppedAt: ReachStage? = null
        var failureText: String? = null
        fun result() = ReachResult(
            attempt, openId, streams, rawSizeText, opened, sessionQuery, configured, frames.toList(), burstAsked, stoppedAt, failureText,
            notes.toList(),
        )

        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        val readers = mutableListOf<ImageReader>()
        var stage = ReachStage.CHARACTERISTICS
        try {
            // The RAW size: the pair's planned one, else the largest the stream's own camera offers within the limit.
            val raw: Size? = if (attempt is ReachAttempt.TwoStreams) {
                parseSize(attempt.rawSize)
            } else {
                val offered = manager.getCameraCharacteristics(streams.single())
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.RAW_SENSOR)
                    ?.map { "${it.width}x${it.height}" }.orEmpty()
                ReachPlan.largestWithin(offered)?.let { parseSize(it) }.also {
                    if (it == null) notes += "camera ${streams.single()} offers no RAW_SENSOR size at or under ${ReachPlan.RAW_PIXEL_LIMIT / 1_000_000} MP (offered: $offered)"
                }
            }
            rawSizeText = raw?.let { "${it.width}x${it.height}" }
            val openChars = manager.getCameraCharacteristics(openId)
            // Zoom 1.0 is asked of a logical camera only, so it keeps its default physical camera for a direct open too.
            val openIsLogical = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && openChars.physicalCameraIds.isNotEmpty()
            val previewSizes = openChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.PRIVATE)?.toList().orEmpty()
            val preview = PlatformBurstCameraImpl.choosePreviewSize(previewSizes, raw ?: Size(4, 3))
            if (preview == null) {
                stoppedAt = ReachStage.CHARACTERISTICS
                failureText = "camera $openId offers no PRIVATE size at or under 1920x1080"
                return result()
            }

            stage = ReachStage.OPEN
            val openedDevice = openDevice(openId)
            device = openedDevice
            opened = true
            notes += "camera $openId opened at ${fmt(now())} ms"

            val previewReader = ImageReader.newInstance(preview.width, preview.height, ImageFormat.PRIVATE, PREVIEW_MAX_IMAGES)
            readers += previewReader
            previewReader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, handler)
            val previewOutput = OutputConfiguration(previewReader.surface)
            // With no RAW to ask for, a physical camera is reached by a preview stream bound to it instead.
            if (raw == null && physicalBound) previewOutput.setPhysicalCameraId(streams.single())
            val rawReaders = if (raw == null) {
                emptyList()
            } else {
                streams.map { ImageReader.newInstance(raw.width, raw.height, ImageFormat.RAW_SENSOR, RAW_MAX_IMAGES).also { readers += it } }
            }
            val rawOutputs = rawReaders.mapIndexed { i, reader ->
                OutputConfiguration(reader.surface).also { if (physicalBound) it.setPhysicalCameraId(streams[i]) }
            }
            val outputs = listOf(previewOutput) + rawOutputs

            stage = ReachStage.QUERY
            sessionQuery = querySession(openedDevice, outputs)
            if (sessionQuery == "not supported") {
                stoppedAt = ReachStage.QUERY
                failureText = "isSessionConfigurationSupported said no to PRIV ${preview.width}x${preview.height}" +
                    (rawSizeText?.let { " + ${streams.size} x RAW_SENSOR $it" } ?: "") + " on ${streamText(streams, physicalBound)}; not configured"
                return result()
            }

            stage = ReachStage.CONFIGURE
            val configuredSession = configureSessionBounded(openedDevice, outputs)
            configured = configuredSession != null
            if (configuredSession == null) {
                stoppedAt = ReachStage.CONFIGURE
                failureText = "the session would not configure (onConfigureFailed) on ${streamText(streams, physicalBound)}"
                return result()
            }
            session = configuredSession
            notes += "session configured at ${fmt(now())} ms"

            stage = ReachStage.CAPTURE
            val previewResults = LatestResult()
            val previewRequest = openedDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewReader.surface)
                if (openIsLogical && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) set(CaptureRequest.CONTROL_ZOOM_RATIO, 1f)
            }
            configuredSession.setRepeatingRequest(previewRequest.build(), previewResults, handler)
            val settled = awaitCondition(SETTLE_TIMEOUT_MILLIS) {
                previewResults.latest?.get(CaptureResult.CONTROL_AE_STATE)?.let { it in AE_SETTLED } == true
            }
            notes += if (settled) "AE settled at ${fmt(now())} ms" else "AE did not settle within $SETTLE_TIMEOUT_MILLIS ms; captured anyway"
            previewResults.latest?.let { notes += "preview active physical camera ${activeId(it)}, per-physical results ${physicalResultIds(it)}" }

            if (rawReaders.isEmpty()) {
                notes += "no RAW stream: the camera was reached by a preview${if (physicalBound) " bound to camera ${streams.single()}" else ""} only"
                return result()
            }
            val still = openedDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                rawReaders.forEach { addTarget(it.surface) }
                if (openIsLogical && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) set(CaptureRequest.CONTROL_ZOOM_RATIO, 1f)
            }.build()
            frames += captureFrames(configuredSession, rawReaders, streams, listOf(still), "single", 0, notes)
            if (burstAsked > 0) {
                frames += captureFrames(configuredSession, rawReaders, streams, List(burstAsked) { still }, "burst", 1, notes)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            stoppedAt = stage
            failureText = when {
                stage == ReachStage.OPEN && device == null -> "camera $openId was not opened: ${failure.message ?: failure}"
                failure is CameraAccessException -> "CameraAccessException reason ${failure.reason} at ${stage.id}: ${failure.message}"
                else -> "the attempt failed at ${stage.id}: $failure"
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { session?.close() }.onFailure { notes += "closing the session threw: $it" }
                device?.let { closeDevice(it, notes) }
                readers.forEach { reader -> runCatching { reader.close() } }
            }
        }
        return result()
    }

    // ── Hand-back ───────────────────────────────────────────────────────────────────────────────────────────────────

    override suspend fun handBack(): PinHandback {
        checkMainThread()
        val details = mutableListOf<String>()
        if (!awaitCondition(OPEN_TIMEOUT_MILLIS) { pendingOpens.get() == 0 }) {
            details += "${pendingOpens.get()} camera open(s) had not answered within $OPEN_TIMEOUT_MILLIS ms of the hand-back"
        }
        synchronized(openDevices) { openDevices.toList() }.forEach { closeDevice(it, details) }
        val ownClosed = synchronized(openDevices) { openDevices.isEmpty() }
        if (!ownClosed) details += "${openDevices.size} of the reach probe's platform device(s) did not report onClosed"
        details += deviceEvents.toList()
        // The pin probe's hand-back: its own devices (none here), then CameraX's camera back and the viewfinder live.
        val back = cameraX.handBack()
        return PinHandback(
            platformClosed = ownClosed && back.platformClosed,
            cameraxBack = back.cameraxBack,
            detail = (details + listOfNotNull(back.detail)).joinToString("; ").ifEmpty { null },
        )
    }

    /** Ends both probes' threads. Called once, after the hand-back. */
    fun close() {
        cameraThread.quitSafely()
        cameraX.close()
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────────────────────────────

    private fun streamText(streams: List<String>, physicalBound: Boolean): String =
        if (physicalBound) "physical camera(s) ${streams.joinToString(" and ")}" else "camera ${streams.single()}"

    private fun parseSize(text: String): Size? {
        val parts = text.split('x')
        val w = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val h = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return Size(w, h)
    }

    private fun activeId(result: CaptureResult): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null

    @Suppress("DEPRECATION") // getPhysicalCameraResults is deprecated at API 31, where getPhysicalCameraTotalResults replaces it.
    private fun physicalResultIds(result: TotalCaptureResult): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> result.physicalCameraTotalResults.keys.sorted()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> result.physicalCameraResults.keys.sorted()
        else -> emptyList()
    }

    /**
     * Submits [requests] (one `capture`, or one `captureBurst`), each targeting every RAW reader, waits for their images
     * and results (bounded), and reads each image's size and timestamp before closing it. Frames are numbered from
     * [firstIndex] per stream; a frame that did not arrive is logged with why.
     */
    private suspend fun captureFrames(
        session: CameraCaptureSession,
        readers: List<ImageReader>,
        streams: List<String>,
        requests: List<CaptureRequest>,
        kind: String,
        firstIndex: Int,
        notes: MutableList<String>,
    ): List<ReachFrame> {
        val images = readers.map { Collections.synchronizedList(mutableListOf<Triple<Long, Int, Int>>()) }
        readers.forEachIndexed { i, reader ->
            reader.setOnImageAvailableListener({ r ->
                try {
                    r.acquireNextImage()?.use { image -> images[i] += Triple(image.timestamp, image.width, image.height) }
                } catch (failure: IllegalStateException) {
                    notes += "a RAW image on camera ${streams[i]}'s stream could not be acquired: $failure"
                }
            }, handler)
        }
        val callback = FramesCallback()
        if (requests.size == 1) session.capture(requests.single(), callback, handler) else session.captureBurst(requests, callback, handler)
        val count = requests.size
        val waitMillis = BASE_TIMEOUT_MILLIS + count * PER_FRAME_TIMEOUT_MILLIS
        awaitCondition(waitMillis) {
            images.all { it.size + callback.failures.size >= count } && callback.completed() + callback.failures.size >= count
        }
        readers.forEach { it.setOnImageAvailableListener(null, null) }

        val frames = mutableListOf<ReachFrame>()
        readers.indices.forEach { i ->
            val arrived = synchronized(images[i]) { images[i].toList() }.sortedBy { it.first }
            arrived.forEachIndexed { n, (timestamp, width, height) ->
                val result = callback.resultFor(timestamp)
                frames += ReachFrame(
                    index = firstIndex + n,
                    kind = kind,
                    streamCameraId = streams[i],
                    sensorTimestampNs = timestamp,
                    activePhysicalId = result?.let { activeId(it) },
                    physicalResultIds = result?.let { physicalResultIds(it) }.orEmpty(),
                    rawSize = "${width}x$height",
                    error = null,
                )
            }
            val missingResults = arrived.count { callback.resultFor(it.first) == null }
            // A physical camera's frame may carry its own timestamp, not the logical result's (not verified); said, not hidden.
            if (missingResults > 0) notes += "$missingResults $kind RAW image(s) on camera ${streams[i]}'s stream had no capture result with the same timestamp"
            for (missing in arrived.size until count) {
                frames += ReachFrame(
                    index = firstIndex + missing, kind = kind, streamCameraId = streams[i], sensorTimestampNs = null, activePhysicalId = null,
                    physicalResultIds = emptyList(), rawSize = null,
                    error = callback.failures.getOrNull(missing - arrived.size) ?: "the RAW image did not arrive within $waitMillis ms",
                )
            }
        }
        return frames
    }

    /** Opens [cameraId], bounded; see PlatformPinCamera's own. A device that opens late closes itself. */
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
            answer()
            continuation.resumeWithException(IllegalStateException("openCamera threw CameraAccessException reason ${failure.reason}: ${failure.message}"))
        } catch (failure: Exception) {
            answer()
            continuation.resumeWithException(IllegalStateException("openCamera threw $failure"))
        }
    }

    /** Closes [device] and waits for `onClosed`, bounded; a device that does not report it stays in [openDevices] and is reported. */
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

    private class Configured(val session: CameraCaptureSession?)

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

    private companion object {
        /** A single and an 8-frame burst, held until read. */
        const val RAW_MAX_IMAGES = 9
        const val PREVIEW_MAX_IMAGES = 3
        const val OPEN_TIMEOUT_MILLIS = 5_000L
        const val CONFIGURE_TIMEOUT_MILLIS = 5_000L
        const val SETTLE_TIMEOUT_MILLIS = 3_000L
        const val DEVICE_CLOSE_TIMEOUT_MILLIS = 3_000L
        const val BASE_TIMEOUT_MILLIS = 2_000L
        const val PER_FRAME_TIMEOUT_MILLIS = 1_000L
        const val POLL_MILLIS = 5L

        val AE_SETTLED = setOf(
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
        )

        fun fmt(ms: Double): String = String.format(Locale.ROOT, "%.3f", ms)

        fun facingName(facing: Int): String = when (facing) {
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN_$facing"
        }
    }
}
