package com.zynergylabs.forager.app.diagnostics.burst

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.google.common.util.concurrent.ListenableFuture
import com.zynergylabs.forager.app.photo.RawPlane
import com.zynergylabs.forager.app.photo.copyRawPlane
import com.zynergylabs.forager.app.photo.standardPhotoSizeSelector
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.math.roundToLong
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import android.hardware.camera2.CameraCaptureSession as PlatformCaptureSession

/*
 * The probe's camera, on CameraX 1.6.2 (dispatch 2026-10-15-06). Debug source set only; the experimental camera2 interop
 * it needs (the lock, the capture results, the characteristics) is confined to this file, as stage 2 confined its own to
 * `CameraXRawCapture.kt`. Nothing in `src/main` changes: the probe reaches the camera through CameraX's process-wide
 * provider and the main source set's `internal` helpers (`standardPhotoSizeSelector`, `copyRawPlane`), which it reads and
 * calls but does not edit.
 *
 * ## How the probe borrows the dialog's camera without touching it (READ, camera-lifecycle 1.6.2, javap)
 *
 * The probe binds its own preview and capture to its own lifecycle owner, held RESUMED. It never unbinds anything but its
 * own use cases and never calls `unbindAll`, which would unbind the dialog's too.
 * - `LifecycleCameraRepository.createLifecycleCamera` suspends a new camera with no use cases, and `bindToLifecycleCamera`
 *   adds the use cases while it is suspended, then calls `setActive` for an owner at least STARTED. `setActive` suspends
 *   the use cases of the owner at the top of its active-owner stack (the dialog's) and only then unsuspends the new one.
 *   So the dialog's and the probe's streams are never attached to the camera at the same time.
 * - `ProcessCameraProvider.unbind(UseCase...)` reaches `LifecycleCameraRepository.unbind`, which removes them and, when a
 *   lifecycle camera is left with none, calls `setInactive` for its owner. `setInactive` removes that owner from the stack
 *   and unsuspends the next one: the dialog's. Its preview and capture reattach as they were bound, so the user's chosen
 *   format (JPG, or stage 2's RAW+JPEG) is back without a rebind. Whether the viewfinder resumes on a given phone is
 *   device-only.
 *
 * ## What one shared thing the probe must clean up (READ, camera-camera2 1.6.2)
 *
 * `Camera2CameraControl.from` unwraps to the camera's single `CameraControlAdapter`, so the interop's request options
 * belong to the camera, not to the probe's binding: a lock left set would follow the dialog's preview and shots. The lock
 * is released with `clearCaptureRequestOptions`, which clears every interop option; main sets none (`git grep
 * Camera2CameraControl` over `app/src` finds only this file), so nothing of the dialog's is cleared with it. The release is
 * read back with `getCaptureRequestOptions` before the run is called handed back.
 *
 * ## One path this file cannot close (main's own, unchanged)
 *
 * The camera's `CameraState` is shared. If the probe's streams fail to configure (`ERROR_STREAM_CONFIG`) while the dialog
 * has RAW chosen, the dialog's own watcher (`CameraXCaptureSession.watchForStreamConfigErrors`) rebinds its capture as
 * JPG, by design, which changes the user's format. The probe checks the combination with `isSessionConfigSupported` before
 * binding, and logs any camera-state error it sees during a run, naming this path, so it cannot pass unnoticed.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class CameraXBurstProbeCamera(private val context: Context) : BurstProbeCamera {

    private val mainExecutor = ContextCompat.getMainExecutor(context)

    /** Image callbacks: record the arrival, copy the RAW plane, close the image. Nothing slow, so arrivals are not delayed. */
    private val imageExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "BurstProbeImages") }

    /** Per-frame work after the image is closed: the capture-result lookup and the sharpness figure. */
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "BurstProbeAnalysis") }

    /** Drains the probe's own preview stream. */
    private val previewThread = HandlerThread("BurstProbePreview").apply { start() }
    private val previewHandler = Handler(previewThread.looper)

    private var provider: ProcessCameraProvider? = null
    private var owner: ProbeLifecycleOwner? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var camera: Camera? = null
    private var results: ProbeCaptureCallback? = null
    private var stateObserver: Observer<CameraState>? = null
    private val runNotes: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun memory(): MemoryReading {
        val runtime = Runtime.getRuntime()
        return MemoryReading(maxMemoryBytes = runtime.maxMemory(), usedBytes = runtime.totalMemory() - runtime.freeMemory())
    }

    override suspend fun bind(output: ProbeOutput): BindOutcome {
        checkMainThread()
        runNotes.clear()
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (failure: Exception) {
            return BindOutcome.Failed("the camera provider did not start: $failure")
        }
        this.provider = provider
        // The same choice as the dialog's (CameraXCaptureSession.open): the back camera, else the front.
        val selector = when {
            runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
            runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> return BindOutcome.Failed("this device reports no back or front camera")
        }
        runNotes += if (selector == CameraSelector.DEFAULT_BACK_CAMERA) "back camera" else "front camera (no back camera)"
        val cameraInfo = try {
            provider.getCameraInfo(selector)
        } catch (failure: Exception) {
            return BindOutcome.Failed("the camera's info could not be read: $failure")
        }
        val format = when (output) {
            ProbeOutput.RAW_JPEG -> ImageCapture.OUTPUT_FORMAT_RAW_JPEG
            ProbeOutput.RAW_ONLY -> ImageCapture.OUTPUT_FORMAT_RAW
        }
        val offered = ImageCapture.getImageCaptureCapabilities(cameraInfo).supportedOutputFormats
        if (format !in offered) {
            return BindOutcome.Unsupported("CameraX lists output formats $offered for this camera; ${output.label} ($format) is not among them")
        }

        val callback = ProbeCaptureCallback()
        val newPreview = Preview.Builder().build()
        val newCapture = probeImageCapture(format, callback)
        if (!cameraInfo.isSessionConfigSupported(SessionConfig(listOf(newPreview, newCapture)))) {
            return BindOutcome.Unsupported("CameraX reports a preview beside a ${output.label} capture as unsupported on this camera")
        }
        newPreview.setSurfaceProvider(mainExecutor) { request -> provideDiscardingSurface(request) }
        val newOwner = ProbeLifecycleOwner().also { it.resume() }
        owner = newOwner
        preview = newPreview
        capture = newCapture
        results = callback
        val bound = try {
            provider.bindToLifecycle(newOwner, selector, newPreview, newCapture)
        } catch (failure: Exception) {
            return BindOutcome.Failed("binding the probe's preview and ${output.label} capture threw: $failure")
        }
        camera = bound
        watchCameraState(bound, newOwner)

        val streamStart = SystemClock.elapsedRealtimeNanos()
        if (callback.awaitLatest(STREAM_START_TIMEOUT_MILLIS) { true } == null) {
            return BindOutcome.Failed("the probe's session produced no capture result within $STREAM_START_TIMEOUT_MILLIS ms of binding")
        }
        // The dialog's own session had converged; the probe's is new. Its first frames would be read mid-convergence,
        // which is a different measurement from a burst taken in a running camera, so the run waits for AE to settle.
        val settled = callback.awaitLatest(SETTLE_TIMEOUT_MILLIS) { result -> result.get(CaptureResult.CONTROL_AE_STATE)?.let { it in AE_SETTLED } == true }
        val settleMs = (SystemClock.elapsedRealtimeNanos() - streamStart) / 1_000_000.0
        runNotes += if (settled != null) {
            "AE settled ${settleMs.roundToLong()} ms after the probe's session started"
        } else {
            "AE did not settle within $SETTLE_TIMEOUT_MILLIS ms (last state ${callback.latestAeState()?.let { BurstProbeLines.aeStateName(it) }}); captured anyway"
        }
        newCapture.resolutionInfo?.let { runNotes += "capture resolutionInfo ${it.resolution} rotation ${it.rotationDegrees}" }
        return BindOutcome.Bound()
    }

    override suspend fun lock(timeoutMillis: Long): LockOutcome {
        checkMainThread()
        val bound = camera ?: return LockOutcome.NotReached("no camera is bound", null, null)
        val callback = results ?: return LockOutcome.NotReached("no capture results are being read", null, null)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
            .build()
        val asked = SystemClock.elapsedRealtimeNanos()
        Camera2CameraControl.from(bound.cameraControl).addCaptureRequestOptions(options).await()
        // A result reads AE LOCKED only while AE lock is on, and AWB LOCKED only while AWB lock is on (camera2's state
        // machines), so a result reading both is the lock in effect, not merely requested.
        val reached = callback.awaitLatest(timeoutMillis) {
            it.get(CaptureResult.CONTROL_AE_STATE) == CaptureResult.CONTROL_AE_STATE_LOCKED &&
                it.get(CaptureResult.CONTROL_AWB_STATE) == CaptureResult.CONTROL_AWB_STATE_LOCKED
        }
        val waitMs = (SystemClock.elapsedRealtimeNanos() - asked) / 1_000_000.0
        return if (reached != null) {
            LockOutcome.Reached(waitMs, CaptureResult.CONTROL_AE_STATE_LOCKED, CaptureResult.CONTROL_AWB_STATE_LOCKED)
        } else {
            LockOutcome.NotReached(
                "no capture result read AE and AWB LOCKED within $timeoutMillis ms of the lock request",
                callback.latestAeState(),
                callback.latestAwbState(),
            )
        }
    }

    override suspend fun unlock(): LockRelease {
        checkMainThread()
        val bound = camera ?: return LockRelease(cleared = false, detail = "no camera was bound to release the lock on")
        val control = Camera2CameraControl.from(bound.cameraControl)
        val confirmed = withTimeoutOrNull(UNLOCK_TIMEOUT_MILLIS) {
            control.clearCaptureRequestOptions().await()
            true
        } ?: false
        val remaining = control.captureRequestOptions
        val stillSet = listOfNotNull(
            remaining.getCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK)?.let { "CONTROL_AE_LOCK=$it" },
            remaining.getCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK)?.let { "CONTROL_AWB_LOCK=$it" },
        )
        return when {
            stillSet.isNotEmpty() -> LockRelease(cleared = false, detail = "read back after the clear: ${stillSet.joinToString()}")
            !confirmed -> LockRelease(cleared = true, detail = "the clear did not report done within $UNLOCK_TIMEOUT_MILLIS ms; read back clear")
            else -> LockRelease(cleared = true)
        }
    }

    override suspend fun capture(output: ProbeOutput, count: Int, frameTimeoutMillis: Long): BurstCapture {
        checkMainThread()
        val use = capture ?: error("capture() before a successful bind()")
        val callback = results ?: error("capture() with no capture-result callback")
        val info = camera?.cameraInfo ?: error("capture() with no bound camera")
        val characteristics = Camera2CameraInfo.from(info)
        fun <T> characteristic(key: CameraCharacteristics.Key<T>): T? = runCatching { characteristics.getCameraCharacteristic(key) }.getOrNull()
        val sensor = SensorFacts(
            colorFilterArrangement = characteristic(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
            staticBlackLevel = characteristic(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
                ?.let { pattern -> IntArray(4).also { pattern.copyTo(it, 0) }.map { it.toFloat() } },
            staticWhiteLevel = characteristic(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL),
        )
        runNotes += "static black ${sensor.staticBlackLevel}, static white ${sensor.staticWhiteLevel}, CFA ${sensor.colorFilterArrangement}"

        val peakHeap = AtomicLong(memory().usedBytes)
        val runStart = SystemClock.elapsedRealtimeNanos()
        val frames = List(count) { FrameState(index = it, expectJpeg = output == ProbeOutput.RAW_JPEG) }
        // Queued back to back: CameraX keeps one request in flight and issues the next when the previous one's capture
        // completes (TakePictureManagerImpl.issueNextRequest, read 1.6.2), so this is the fastest its still path runs.
        for (frame in frames) {
            frame.submitNanos = SystemClock.elapsedRealtimeNanos()
            use.takePicture(imageExecutor, frameCallback(frame, callback, sensor, peakHeap))
        }
        val deadline = runStart + count * frameTimeoutMillis * 1_000_000L
        while (!frames.all { it.done() } && SystemClock.elapsedRealtimeNanos() < deadline) delay(POLL_MILLIS)

        val probeFrames = frames.map { it.toProbeFrame(runStart, count * frameTimeoutMillis) }
        val rawSize = frames.firstNotNullOfOrNull { it.rawSize }
        val streamMap = characteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val notes = runNotes.toMutableList()
        val minFrame = rawSize?.let { size -> sensorLimit(streamMap, notes, "minimum frame duration") { it.getOutputMinFrameDuration(ImageFormat.RAW_SENSOR, size) } }
        val stall = rawSize?.let { size -> sensorLimit(streamMap, notes, "stall duration") { it.getOutputStallDuration(ImageFormat.RAW_SENSOR, size) } }
        if (rawSize == null) notes += "no RAW frame arrived, so the bound RAW size and the sensor's limits at it are unknown"
        return BurstCapture(
            frames = probeFrames,
            facts = ProbeRunFacts(
                rawSize = rawSize?.let { "${it.width}x${it.height}" },
                jpegSize = frames.firstNotNullOfOrNull { it.jpegSize }?.let { "${it.width}x${it.height}" },
                minFrameDurationNs = minFrame,
                stallDurationNs = stall,
                heapUsedPeakBytes = peakHeap.get(),
                timestampSource = when (characteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)) {
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
                    null -> null
                    else -> "OTHER"
                },
                pipelineMaxDepth = characteristic(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH)?.toInt(),
                notes = notes,
            ),
        )
    }

    override suspend fun release(): Handback {
        checkMainThread()
        val provider = provider
        val useCases = listOfNotNull(preview, capture)
        val stillBound = if (provider != null && useCases.isNotEmpty()) {
            provider.unbind(*useCases.toTypedArray())
            useCases.filter { provider.isBound(it) }
        } else {
            emptyList()
        }
        val info = camera?.cameraInfo
        stateObserver?.let { observer -> info?.cameraState?.removeObserver(observer) }
        stateObserver = null
        owner?.destroy()
        owner = null
        preview = null
        capture = null
        camera = null
        results = null
        if (stillBound.isNotEmpty()) {
            return Handback(probeUnbound = false, detail = "${stillBound.size} of the probe's use cases still read as bound after unbind")
        }
        // What the camera does once the dialog's binding is active again. Informational: OPEN with the dialog open,
        // CLOSED if there was no dialog to hand back to.
        delay(HANDBACK_SETTLE_MILLIS)
        val state = info?.cameraState?.value
        return Handback(probeUnbound = true, detail = "camera state $HANDBACK_SETTLE_MILLIS ms after hand-back: ${state?.type}${state?.error?.let { " error ${it.code}" } ?: ""}")
    }

    /** Shuts the probe's own threads. Called once, after the last run. */
    fun close() {
        imageExecutor.shutdown()
        analysisExecutor.shutdown()
        previewThread.quitSafely()
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * The probe's capture: the same settings as stage 2's RAW capture (`buildRawJpegImageCapture`: minimise latency, the
     * standard photo size, the lens-shading map asked for), with this run's output format and the probe's own result
     * callback. A copy rather than a call, because stage 2's builder fixes the format and its callback; it is not edited.
     */
    private fun probeImageCapture(format: Int, callback: ProbeCaptureCallback): ImageCapture {
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(standardPhotoSizeSelector())
            .setOutputFormat(format)
        Camera2Interop.Extender(builder)
            .setSessionCaptureCallback(callback)
            .setCaptureRequestOption(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
        return builder.build()
    }

    /**
     * The probe's preview goes to an image reader that drops every frame as it arrives: the preview stream exists so the
     * session matches the dialog's (a preview beside the capture), not to be seen. If the reader cannot be made, the
     * surface is declined and that is noted.
     */
    private fun provideDiscardingSurface(request: SurfaceRequest) {
        val size = request.resolution
        val reader = try {
            ImageReader.newInstance(size.width, size.height, ImageFormat.PRIVATE, PREVIEW_READER_IMAGES)
        } catch (failure: Exception) {
            runNotes += "the probe's preview surface could not be made ($failure); the preview was declined"
            request.willNotProvideSurface()
            return
        }
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, previewHandler)
        runNotes += "probe preview ${size.width}x${size.height}"
        request.provideSurface(reader.surface, mainExecutor) { reader.close() }
    }

    private fun watchCameraState(bound: Camera, lifecycleOwner: LifecycleOwner) {
        val observer = Observer<CameraState> { state ->
            val error = state.error ?: return@Observer
            runNotes += if (error.code == CameraState.ERROR_STREAM_CONFIG) {
                "camera state ERROR_STREAM_CONFIG during the run: with RAW chosen in the camera dialog, its own watcher rebinds as JPG (CameraXCaptureSession.watchForStreamConfigErrors); check the dialog's format"
            } else {
                "camera state error ${error.code} (${state.type}) during the run"
            }
        }
        bound.cameraInfo.cameraState.observe(lifecycleOwner, observer)
        stateObserver = observer
    }

    private fun frameCallback(frame: FrameState, callback: ProbeCaptureCallback, sensor: SensorFacts, peakHeap: AtomicLong) =
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val arrived = SystemClock.elapsedRealtimeNanos()
                when (image.format) {
                    ImageFormat.RAW_SENSOR -> {
                        val copyStart = System.nanoTime()
                        val plane = runCatching { copyRawPlane(image) }
                        val copyNanos = System.nanoTime() - copyStart
                        image.close()
                        peakHeap.accumulateAndGet(memory().usedBytes) { a, b -> maxOf(a, b) }
                        plane.fold(
                            onSuccess = { copied ->
                                frame.rawArrived(arrived, copyNanos, Size(copied.width, copied.height))
                                analysisExecutor.execute { frame.analysed(analyse(copied, callback, sensor)) }
                            },
                            onFailure = { failure -> frame.fail("the RAW plane could not be copied: $failure", arrived) },
                        )
                    }
                    ImageFormat.JPEG -> {
                        val size = Size(image.width, image.height)
                        image.close()
                        frame.jpegArrived(arrived, size)
                    }
                    else -> {
                        val format = image.format
                        image.close()
                        frame.note("an image of format $format arrived and was closed unread")
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                frame.fail("the library reported error ${exception.imageCaptureError}: ${exception.message}", null)
            }
        }

    /** On the analysis thread: the frame's capture result, by sensor timestamp, and its sharpness. */
    private fun analyse(plane: RawPlane, callback: ProbeCaptureCallback, sensor: SensorFacts): RawAnalysis {
        val notes = mutableListOf<String>()
        val result = callback.resultBlocking(plane.timestampNanos, RESULT_TIMEOUT_MILLIS)
        if (result == null) notes += "no capture result with this frame's sensor timestamp within $RESULT_TIMEOUT_MILLIS ms"
        val dynamicBlack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result?.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)?.toList() else null
        val dynamicWhite = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) result?.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL) else null
        val black = dynamicBlack ?: sensor.staticBlackLevel
        val white = dynamicWhite ?: sensor.staticWhiteLevel
        if (result != null && dynamicBlack == null) notes += "no dynamic black level; sharpness used the static pattern"
        val sharpness = if (black == null || white == null) {
            notes += "no black or white level to normalise by; no sharpness"
            null
        } else {
            when (val s = greenSharpness(plane.samples, plane.width, plane.height, sensor.colorFilterArrangement, black, white)) {
                is Sharpness.Value -> s.meanAbsGradient
                is Sharpness.Unsupported -> {
                    notes += "sharpness unsupported: ${s.reason}"
                    null
                }
            }
        }
        return RawAnalysis(
            sensorTimestampNs = plane.timestampNanos,
            result = result,
            startedNanos = callback.startedAt(plane.timestampNanos),
            dynamicBlack = dynamicBlack,
            dynamicWhite = dynamicWhite,
            sharpness = sharpness,
            notes = notes,
        )
    }

    private fun <T> sensorLimit(map: StreamConfigurationMap?, notes: MutableList<String>, what: String, read: (StreamConfigurationMap) -> T): T? {
        if (map == null) {
            notes += "no stream configuration map, so no $what"
            return null
        }
        return try {
            read(map)
        } catch (failure: Exception) {
            notes += "the $what for RAW_SENSOR at the bound size could not be read: $failure"
            null
        }
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) { "the probe's camera calls run on the main thread" }

    private class SensorFacts(val colorFilterArrangement: Int?, val staticBlackLevel: List<Float>?, val staticWhiteLevel: Int?)

    private class RawAnalysis(
        val sensorTimestampNs: Long,
        val result: TotalCaptureResult?,
        val startedNanos: Long?,
        val dynamicBlack: List<Float>?,
        val dynamicWhite: Int?,
        val sharpness: Double?,
        val notes: List<String>,
    )

    /** One frame's progress across the image, analysis and main threads; every access is under its own lock. */
    private class FrameState(val index: Int, val expectJpeg: Boolean) {
        @Volatile var submitNanos: Long = 0L
        private var rawArrivalNanos: Long? = null
        private var jpegArrivalNanos: Long? = null
        private var copyNanos: Long? = null
        private var analysis: RawAnalysis? = null
        private var error: String? = null
        private val notes = mutableListOf<String>()
        var rawSize: Size? = null
            private set
        var jpegSize: Size? = null
            private set

        @Synchronized fun rawArrived(at: Long, copy: Long, size: Size) {
            rawArrivalNanos = at
            copyNanos = copy
            rawSize = size
        }

        @Synchronized fun jpegArrived(at: Long, size: Size) {
            jpegArrivalNanos = at
            jpegSize = size
        }

        @Synchronized fun analysed(result: RawAnalysis) {
            analysis = result
        }

        @Synchronized fun fail(reason: String, at: Long?) {
            if (error == null) error = reason
            if (at != null && rawArrivalNanos == null) rawArrivalNanos = at
        }

        @Synchronized fun note(text: String) {
            notes += text
        }

        @Synchronized fun done(): Boolean = error != null || (analysis != null && (!expectJpeg || jpegArrivalNanos != null))

        @Synchronized fun toProbeFrame(runStart: Long, waitedMillis: Long): ProbeFrame {
            fun ms(nanos: Long?) = nanos?.let { (it - runStart) / 1_000_000.0 }
            val a = analysis
            val r = a?.result
            val missing = buildList {
                if (rawArrivalNanos == null) add("RAW")
                else if (a == null) add("RAW analysis")
                if (expectJpeg && jpegArrivalNanos == null) add("JPEG")
            }
            val frameError = error ?: missing.takeIf { it.isNotEmpty() }
                ?.let { "${it.joinToString(" and ")} did not arrive within $waitedMillis ms of the first submit" }
            return ProbeFrame(
                index = index,
                submitMs = ms(submitNanos),
                startedMs = ms(a?.startedNanos),
                rawArrivalMs = ms(rawArrivalNanos),
                jpegArrivalMs = ms(jpegArrivalNanos),
                sensorTimestampNs = a?.sensorTimestampNs,
                resultMatched = r != null,
                exposureTimeNs = r?.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                iso = r?.get(CaptureResult.SENSOR_SENSITIVITY),
                postRawBoost = r?.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST),
                aeState = r?.get(CaptureResult.CONTROL_AE_STATE),
                awbState = r?.get(CaptureResult.CONTROL_AWB_STATE),
                afState = r?.get(CaptureResult.CONTROL_AF_STATE),
                focusDistanceDiopters = r?.get(CaptureResult.LENS_FOCUS_DISTANCE),
                blackLevel = a?.dynamicBlack,
                whiteLevel = a?.dynamicWhite,
                rawCopyMs = copyNanos?.let { it / 1_000_000.0 },
                sharpness = a?.sharpness,
                notes = notes + (a?.notes ?: emptyList()),
                error = frameError,
            )
        }
    }

    /**
     * Keeps the probe session's recent capture results by sensor timestamp, the time each exposure started, and the latest
     * result. The callback sees every preview frame as well as the stills, so only the last [CAPACITY] are kept: about two
     * seconds of preview at 30 frames a second, where a still's result is looked up within [RESULT_TIMEOUT_MILLIS] of its
     * RAW image arriving.
     */
    private class ProbeCaptureCallback : PlatformCaptureSession.CaptureCallback() {
        private val lock = Any()
        private val results = object : LinkedHashMap<Long, TotalCaptureResult>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TotalCaptureResult>?) = size > CAPACITY
        }
        private val started = object : LinkedHashMap<Long, Long>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Long>?) = size > CAPACITY
        }
        @Volatile private var latest: TotalCaptureResult? = null

        override fun onCaptureStarted(session: PlatformCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
            val now = SystemClock.elapsedRealtimeNanos()
            synchronized(lock) { started[timestamp] = now }
        }

        override fun onCaptureCompleted(session: PlatformCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
            synchronized(lock) { results[timestamp] = result }
            latest = result
        }

        fun startedAt(timestampNanos: Long): Long? = synchronized(lock) { started[timestampNanos] }

        fun latestAeState(): Int? = latest?.get(CaptureResult.CONTROL_AE_STATE)
        fun latestAwbState(): Int? = latest?.get(CaptureResult.CONTROL_AWB_STATE)

        /** The latest result once it satisfies [predicate], polled until [timeoutMillis]; null if none did. */
        suspend fun awaitLatest(timeoutMillis: Long, predicate: (TotalCaptureResult) -> Boolean): TotalCaptureResult? {
            val deadline = SystemClock.elapsedRealtimeNanos() + timeoutMillis * 1_000_000L
            while (true) {
                latest?.takeIf(predicate)?.let { return it }
                if (SystemClock.elapsedRealtimeNanos() >= deadline) return null
                delay(POLL_MILLIS)
            }
        }

        /** On the analysis thread, which may block: the result for [timestampNanos], or null after [timeoutMillis]. */
        fun resultBlocking(timestampNanos: Long, timeoutMillis: Long): TotalCaptureResult? {
            val deadline = SystemClock.elapsedRealtimeNanos() + timeoutMillis * 1_000_000L
            while (true) {
                synchronized(lock) { results[timestampNanos] }?.let { return it }
                if (SystemClock.elapsedRealtimeNanos() >= deadline) return null
                Thread.sleep(POLL_MILLIS)
            }
        }

        companion object {
            const val CAPACITY = 64
        }
    }

    /** A lifecycle the probe owns: RESUMED while it holds the camera, DESTROYED when it hands it back. Main thread only. */
    private class ProbeLifecycleOwner : LifecycleOwner {
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
        const val STREAM_START_TIMEOUT_MILLIS = 5_000L
        const val SETTLE_TIMEOUT_MILLIS = 3_000L
        const val UNLOCK_TIMEOUT_MILLIS = 2_000L
        const val RESULT_TIMEOUT_MILLIS = 1_000L
        const val HANDBACK_SETTLE_MILLIS = 500L
        const val POLL_MILLIS = 10L
        const val PREVIEW_READER_IMAGES = 4

        /** AE states that mean the exposure has settled: CONVERGED, LOCKED, FLASH_REQUIRED. */
        val AE_SETTLED = setOf(
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
        )
    }
}

/** Awaits a [ListenableFuture] without blocking; its failure is thrown. */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (failure: java.util.concurrent.ExecutionException) {
                continuation.resumeWithException(failure.cause ?: failure)
            } catch (failure: Exception) {
                continuation.resumeWithException(failure)
            }
        },
        { it.run() },
    )
}
