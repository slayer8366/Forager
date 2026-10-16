package com.zynergylabs.forager.app.photo.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.zynergylabs.forager.app.diagnostics.burst.findPreviewView
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.CameraXCaptureSession
import com.zynergylabs.forager.app.photo.RawShot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Today's CameraX engine with the prototype's measurements around it, for comparison (dispatch 2026-10-16-02: "Include the
 * same figures for today's CameraX engine where they can be logged without changing main"). Chosen over adb as
 * `--es engine camerax-timed`; the session inside is main's `CameraXCaptureSession`, untouched, and every call is passed
 * straight to it. What it can measure from outside:
 *
 * - **open**: `open()` to the session reading Ready, which for CameraX is the bind returning, not the device opening;
 * - **first frame**: `open()` to the `PreviewView` reading STREAMING;
 * - **shutter to saved** for JPG and RAW, and the heap after each.
 *
 * Not measured: the viewfinder's frame rate, which needs a capture callback on CameraX's preview use case, a change to
 * main's `CameraXCaptureSession`.
 */
internal fun timedCameraXEngine(context: Context): CameraEngine = TimedCameraXEngine(CameraXCaptureSession(context.applicationContext))

internal class TimedCameraXEngine(private val inner: CameraXCaptureSession) : CameraEngine, CameraCaptureSession by inner {
    private val metrics = EngineMetrics("camerax")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var readyWatch: Job? = null
    private var streamed = false

    override val session: CameraCaptureSession get() = this

    private fun log(line: String) = Log.i(ENGINE_LOG_TAG, line)
    private fun now() = SystemClock.elapsedRealtimeNanos()
    private fun heap() = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    override fun open(lifecycleOwner: LifecycleOwner) {
        metrics.openRequested(now())
        streamed = false
        inner.open(lifecycleOwner)
        readyWatch?.cancel()
        readyWatch = scope.launch {
            snapshotFlow { inner.state }.first { it == CameraSessionState.Ready }
            log(metrics.deviceOpened(now()) + " (CameraX: the bind returning)")
            log(metrics.heap("open", heap()))
        }
    }

    override fun close() {
        readyWatch?.cancel()
        inner.close()
        log(metrics.summary())
    }

    override suspend fun capture(destination: File): Result<Unit> {
        val shutter = now()
        return inner.capture(destination).also { result ->
            if (result.isSuccess) {
                log(metrics.shot(ShotKind.Jpg, shutter, now()))
                log(metrics.heap("jpg_shot", heap()))
            }
        }
    }

    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> {
        val shutter = now()
        return inner.captureRaw(jpegDestination).also { result ->
            if (result.isSuccess) {
                log(metrics.shot(ShotKind.Raw, shutter, now()))
                log(metrics.heap("raw_shot", heap()))
            }
        }
    }

    @Composable
    override fun Viewfinder(modifier: Modifier, held: Boolean) {
        inner.Viewfinder(modifier, held = held)
        val root = LocalView.current
        DisposableEffect(root) {
            var watched: PreviewView? = null
            val observer = Observer<PreviewView.StreamState> { stream ->
                if (stream == PreviewView.StreamState.STREAMING && !streamed) {
                    streamed = true
                    log(metrics.firstFrame(now()) + " (CameraX: PreviewView STREAMING)")
                    log(metrics.heap("first_frame", heap()))
                }
            }
            // After this frame's layout, when the PreviewView is in the tree.
            root.post {
                watched = findPreviewView(root.rootView)
                watched?.previewStreamState?.observeForever(observer)
                    ?: log("engine=camerax event=first_frame_unmeasured reason=no PreviewView found in the window")
            }
            onDispose { watched?.previewStreamState?.removeObserver(observer) }
        }
    }
}
