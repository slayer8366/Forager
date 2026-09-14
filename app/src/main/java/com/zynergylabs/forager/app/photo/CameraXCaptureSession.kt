package com.zynergylabs.forager.app.photo

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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
 * ## Orientation is read per capture, not once at bind
 *
 * [ImageCapture.setTargetRotation] is set from the viewfinder's own display immediately before each
 * shot. Setting it once at bind is the obvious version and wrong in a way that would be easy to
 * ship: turning the phone does not necessarily recreate the Activity, so a session bound in
 * portrait would tag every later landscape photo portrait. The EXIF orientation tag is what the
 * whole display path reads, and what [scrubPhotoMetadata] deliberately preserves, so getting it
 * wrong here surfaces as sideways photos everywhere downstream.
 *
 * **Device-only, and unverified:** whether the tag actually comes out right, on any of it.
 *
 * ## No location is ever attached
 *
 * `ImageCapture.Metadata` carries an optional `location`, and this never sets one, so a photo taken
 * here has no GPS to strip in the first place. [scrubPhotoMetadata] still runs on persist, and
 * still should: the camera HAL writes make, model and timestamps regardless, and an allowlist that
 * only removes what someone remembered to worry about is the thing that scrub exists not to be.
 */
internal class CameraXCaptureSession(private val appContext: Context) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(CameraSessionState.Opening)
        private set

    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null

    override suspend fun capture(destination: File): Result<Unit> {
        val capture = imageCapture
            ?: return Result.failure(IllegalStateException("The camera is not ready yet."))
        // Per shot, from the live display — see this class's own doc comment for why not at bind.
        previewView?.display?.rotation?.let { rotation -> capture.targetRotation = rotation }

        return suspendCancellableCoroutine { continuation ->
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
    }

    /**
     * The viewfinder, and the binding that feeds it. A concrete member rather than part of
     * [CameraCaptureSession]: a `@Composable` on the interface would force every fake to be one
     * too, and the screen takes its viewfinder as a slot precisely so a test can pass a plain
     * `Box`.
     */
    @Composable
    fun Viewfinder(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val view = remember { PreviewView(context) }

        DisposableEffect(lifecycleOwner, view) {
            previewView = view
            var bound: ProcessCameraProvider? = null
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
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
                        val preview = Preview.Builder().build()
                            .apply { setSurfaceProvider(view.surfaceProvider) }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        capture
                    }.fold(
                        onSuccess = { capture ->
                            imageCapture = capture
                            bound = provider
                            state = CameraSessionState.Ready
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Binding the camera to this screen failed.", error)
                            state = CameraSessionState.Unavailable("The camera is in use by another app, or could not be bound.")
                        },
                    )
                },
                ContextCompat.getMainExecutor(context),
            )

            onDispose {
                // The already-resolved provider, never a blocking `future.get()` on the main
                // thread: if it never resolved there is nothing bound to release anyway.
                bound?.unbindAll()
                imageCapture = null
                previewView = null
                state = CameraSessionState.Opening
            }
        }

        AndroidView(factory = { view }, modifier = modifier)
    }

    private companion object {
        const val TAG = "CameraXCaptureSession"
    }
}
