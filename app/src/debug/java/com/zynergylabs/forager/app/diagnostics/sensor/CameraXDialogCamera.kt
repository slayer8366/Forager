package com.zynergylabs.forager.app.diagnostics.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.impl.CameraCaptureCallback
import androidx.camera.core.impl.CameraCaptureResult
import androidx.camera.core.impl.CameraInfoInternal
import androidx.camera.lifecycle.LifecycleCamera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The camera dialog's own camera, for the watch (Part 1) and the zoom stepper (Part 2), on CameraX 1.6.2 (dispatch
 * 2026-10-15-12). Debug source set only; nothing in src/main changes and main exposes no hook for it.
 *
 * ## How the watch sees every capture result of the dialog's session (READ from the pinned 1.6.2 artifacts, javap,
 *    sha256 matched against the publisher's .sha256 files)
 *
 * The dispatch asked for "the interop session capture callback that stage 2 uses". That callback is set on a use case
 * when it is built (`Camera2Interop.Extender.setSessionCaptureCallback`), and the dialog's use cases are built in main,
 * so adding ours that way would mean either editing main or binding an extra use case into the dialog's session, which
 * changes the session the watch is meant to observe. The watch uses the camera-level route instead:
 *
 * - `CameraInfoInternal.addSessionCaptureCallback(Executor, CameraCaptureCallback)` is on the camera's info, not on a
 *   use case. `ProcessCameraProvider.getCameraInfo` returns an `AdapterCameraInfo`, whose `ForwardingCameraInfo` base
 *   forwards the call to the camera's own info; camera-camera2 1.6.2's `CameraInfoAdapter` adds it to the camera's
 *   `CameraCallbackMap`.
 * - `CameraGraphConfigProvider` passes that same `CameraCallbackMap` as one of the two `defaultListeners` of every
 *   `CameraGraph.Config` it builds, and a graph's default listeners hear every request on it: the repeating preview and
 *   every still alike.
 * - Each result arrives as a `CameraCaptureResult`; camera-camera2's `CaptureResultAdapter.getCaptureResult()` unwraps
 *   the platform's `TotalCaptureResult`, so the keys read below are camera2's own.
 *
 * `CameraInfoInternal` and `CameraCaptureCallback` are public classes marked restricted to the library group: a lint
 * finding, not a compile error, and this project's CI runs no lint. Suppressed here with this reason, as the fast-burst
 * probe does for `LifecycleCamera.suspend()`.
 *
 * ## How the zoom stepper reaches the dialog's camera control
 *
 * `setZoomRatio` is on the `CameraControl` of the camera the dialog bound, and the dialog keeps that object private
 * (`CameraXCaptureSession.camera`). CameraX keeps every bound camera in its `LifecycleCameraRepository`, reached from the
 * provider through two private fields (`ProcessCameraProvider.lifecycleCameraProvider`,
 * `LifecycleCameraProviderImpl.lifecycleCameraRepository`) and a package-private method (`getLifecycleCameras()`), all
 * READ in 1.6.2's classes. This file reads them by reflection, picks the one active lifecycle camera that has a preview
 * bound on the dialog's camera ID, and calls the standard `CameraControl.setZoomRatio` on it. If CameraX's internals are
 * not where they were read, the zoom is reported unavailable with the reflection's own error, never guessed at.
 */
@SuppressLint("RestrictedApi")
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
internal class CameraXDialogCamera(private val context: Context) : DialogCamera {

    private val resultsExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "SensorWatchResults") }
    private var info: CameraInfoInternal? = null
    private var callback: CameraCaptureCallback? = null
    private val unreadable = AtomicInteger(0)
    private val callbackErrors = AtomicInteger(0)
    private var zoomCamera: LifecycleCamera? = null

    // Touched only on the results thread.
    private var brightnessSearched = false
    private var brightnessKey: CaptureResult.Key<Any>? = null

    override suspend fun attach(): DialogAttach {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return DialogAttach.Unavailable("the camera provider did not start: $failure")
        }
        val selector = selectorFor(provider) ?: return DialogAttach.Unavailable("this device reports no back or front camera")
        val cameraInfo = try {
            provider.getCameraInfo(selector)
        } catch (failure: Exception) {
            return DialogAttach.Unavailable("the camera's info could not be read: $failure")
        }
        val internal = cameraInfo as? CameraInfoInternal
            ?: return DialogAttach.Unavailable("CameraX's camera info is ${cameraInfo.javaClass.name}, not CameraInfoInternal; the session's results cannot be reached")
        info = internal
        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
        val notes = mutableListOf<String>()
        notes += if (selector == CameraSelector.DEFAULT_BACK_CAMERA) "back camera" else "front camera (no back camera)"
        val dialog = when (val found = findDialogCamera(provider, cameraId)) {
            is DialogLookup.Found -> describe(found.camera)
            is DialogLookup.NotFound -> {
                notes += "the dialog's bound use cases could not be read: ${found.reason}"
                emptyList()
            }
        }
        if (dialog.any { it.contains("RAW") }) {
            notes += "a RAW output is bound: the platform documents that with a RAW stream configured a logical camera keeps its " +
                "default physical camera active and does not switch (CameraMetadata, LOGICAL_MULTI_CAMERA)"
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) notes += "API ${Build.VERSION.SDK_INT}: the active physical camera key needs API 29, so it is null"
        return DialogAttach.Attached(cameraId, dialog, notes)
    }

    override fun startResults(onReading: (FrameReading) -> Unit) {
        val target = info ?: error("startResults() before a successful attach()")
        unreadable.set(0)
        callbackErrors.set(0)
        val newCallback = object : CameraCaptureCallback() {
            override fun onCaptureCompleted(captureConfigId: Int, cameraCaptureResult: CameraCaptureResult) {
                val arrival = SystemClock.elapsedRealtimeNanos()
                try {
                    val result: CaptureResult? = cameraCaptureResult.captureResult
                    if (result == null) {
                        unreadable.incrementAndGet()
                        return
                    }
                    onReading(read(result, arrival))
                } catch (failure: Exception) {
                    // Counted and reported at stop: one bad result must not stop the camera's callback thread.
                    callbackErrors.incrementAndGet()
                }
            }
        }
        target.addSessionCaptureCallback(resultsExecutor, newCallback)
        callback = newCallback
    }

    override fun stopResults(): List<String> {
        val notes = mutableListOf<String>()
        val registered = callback
        if (registered != null) {
            info?.removeSessionCaptureCallback(registered)
            notes += "the results callback was removed from the camera"
        }
        callback = null
        unreadable.get().takeIf { it > 0 }?.let { notes += "$it results carried no platform capture result and were not read" }
        callbackErrors.get().takeIf { it > 0 }?.let { notes += "$it results threw while being read and were not logged" }
        return notes
    }

    override suspend fun findZoom(): ZoomAccess {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return ZoomAccess.Unavailable("the camera provider did not start: $failure")
        }
        val selector = selectorFor(provider) ?: return ZoomAccess.Unavailable("this device reports no back or front camera")
        val cameraId = Camera2CameraInfo.from(provider.getCameraInfo(selector)).cameraId
        val camera = when (val found = findDialogCamera(provider, cameraId)) {
            is DialogLookup.Found -> found.camera
            is DialogLookup.NotFound -> return ZoomAccess.Unavailable("the dialog's camera was not found: ${found.reason}")
        }
        val state = camera.cameraInfo.zoomState.value
            ?: return ZoomAccess.Unavailable("the dialog's camera reports no zoom state yet")
        zoomCamera = camera
        return ZoomAccess.Available(ZoomRange(state.minZoomRatio, state.maxZoomRatio))
    }

    override suspend fun setZoom(ratio: Float): ZoomOutcome {
        val camera = zoomCamera ?: return ZoomOutcome.Failed("no dialog camera was found to zoom")
        return try {
            val done = withTimeoutOrNull(ZOOM_TIMEOUT_MILLIS) {
                camera.cameraControl.setZoomRatio(ratio).await()
                true
            }
            if (done == null) ZoomOutcome.Failed("setZoomRatio($ratio) did not complete within $ZOOM_TIMEOUT_MILLIS ms") else ZoomOutcome.Applied
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ZoomOutcome.Failed("setZoomRatio($ratio) failed: $failure")
        }
    }

    /** Ends the results thread. The callback is removed by [stopResults] first. */
    fun close() {
        resultsExecutor.shutdown()
    }

    // ── Internals ──────────────────────────────────────────────────────────────────────────────────────────────────

    /** The dialog's choice (CameraXCaptureSession.open): the back camera, else the front. */
    private fun selectorFor(provider: ProcessCameraProvider): CameraSelector? = when {
        runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
        runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
        else -> null
    }

    private fun read(result: CaptureResult, arrival: Long): FrameReading {
        val intent = result.get(CaptureResult.CONTROL_CAPTURE_INTENT)
        val (key, value) = brightness(result)
        return FrameReading(
            arrivalNanos = arrival,
            sensorTimestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP),
            still = intent == CaptureResult.CONTROL_CAPTURE_INTENT_STILL_CAPTURE || intent == CaptureResult.CONTROL_CAPTURE_INTENT_ZERO_SHUTTER_LAG,
            captureIntent = intent,
            activePhysicalId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID) else null,
            zoomRatio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null,
            focusDistanceDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            afState = result.get(CaptureResult.CONTROL_AF_STATE),
            exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
            iso = result.get(CaptureResult.SENSOR_SENSITIVITY),
            aperture = result.get(CaptureResult.LENS_APERTURE),
            brightnessKey = key,
            brightnessValue = value,
        )
    }

    /**
     * Camera2 defines no scene-brightness key. Some cameras offer one of their own in the result; the first result is
     * searched once for a key whose name says brightness, and that key is read from every result after. Its units are the
     * camera's own, so it is logged with its name, beside the ev100 the frame line computes from standard keys.
     */
    @Suppress("UNCHECKED_CAST")
    private fun brightness(result: CaptureResult): Pair<String?, Double?> {
        if (!brightnessSearched) {
            brightnessSearched = true
            brightnessKey = result.keys.firstOrNull { it.name.contains("brightness", ignoreCase = true) } as CaptureResult.Key<Any>?
        }
        val key = brightnessKey ?: return null to null
        val value = runCatching { result.get(key) }.getOrNull()
        return key.name to (value as? Number)?.toDouble()
    }

    private sealed interface DialogLookup {
        data class Found(val camera: LifecycleCamera) : DialogLookup
        data class NotFound(val reason: String) : DialogLookup
    }

    /** The one active lifecycle camera with a preview bound on [cameraId]: the dialog's. See the file comment. */
    private fun findDialogCamera(provider: ProcessCameraProvider, cameraId: String): DialogLookup {
        val all = try {
            val implField = ProcessCameraProvider::class.java.getDeclaredField("lifecycleCameraProvider").apply { isAccessible = true }
            val impl = implField.get(provider) ?: return DialogLookup.NotFound("the provider's lifecycleCameraProvider is null")
            val repositoryField = impl.javaClass.getDeclaredField("lifecycleCameraRepository").apply { isAccessible = true }
            val repository = repositoryField.get(impl) ?: return DialogLookup.NotFound("the lifecycleCameraRepository is null")
            val method = repository.javaClass.getDeclaredMethod("getLifecycleCameras").apply { isAccessible = true }
            (method.invoke(repository) as? Collection<*>)?.filterIsInstance<LifecycleCamera>()
                ?: return DialogLookup.NotFound("getLifecycleCameras() did not return a collection")
        } catch (failure: ReflectiveOperationException) {
            return DialogLookup.NotFound("CameraX 1.6.2's internals were not where they were read: $failure")
        } catch (failure: RuntimeException) {
            return DialogLookup.NotFound("reading CameraX's bound cameras threw: $failure")
        }
        val candidates = all.filter { camera ->
            camera.isActive &&
                camera.useCases.any { it is Preview } &&
                runCatching { Camera2CameraInfo.from(camera.cameraInfo).cameraId }.getOrNull() == cameraId
        }
        val seen = all.joinToString("; ") { camera ->
            "${runCatching { Camera2CameraInfo.from(camera.cameraInfo).cameraId }.getOrNull()} active ${camera.isActive} ${describe(camera)}"
        }
        return when (candidates.size) {
            1 -> DialogLookup.Found(candidates.single())
            0 -> DialogLookup.NotFound("no active camera with a preview on camera $cameraId (is the camera dialog open?); bound: [$seen]")
            else -> DialogLookup.NotFound("${candidates.size} active cameras with a preview on camera $cameraId; bound: [$seen]")
        }
    }

    private fun describe(camera: LifecycleCamera): List<String> = camera.useCases.map { useCase ->
        when (useCase) {
            is ImageCapture -> "ImageCapture ${outputFormatName(useCase.outputFormat)}"
            else -> useCase.javaClass.simpleName
        }
    }

    private fun outputFormatName(format: Int): String = when (format) {
        ImageCapture.OUTPUT_FORMAT_JPEG -> "JPEG"
        ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR -> "JPEG_ULTRA_HDR"
        ImageCapture.OUTPUT_FORMAT_RAW -> "RAW"
        ImageCapture.OUTPUT_FORMAT_RAW_JPEG -> "RAW_JPEG"
        else -> "format $format"
    }

    private companion object {
        const val ZOOM_TIMEOUT_MILLIS = 3_000L
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
