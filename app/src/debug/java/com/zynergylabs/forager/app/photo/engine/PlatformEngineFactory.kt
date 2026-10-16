package com.zynergylabs.forager.app.photo.engine

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraXCaptureSession
import com.zynergylabs.forager.app.photo.CameraXEngine
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Engine A as the dialog gets it (dispatch 2026-10-16-02): the composite over the platform engine, with today's CameraX
 * engine as its fallback. What `CameraEngineSwitch` installs in `CameraEngineSeam` when the prototype is chosen.
 *
 * The engine's scope is its own, not the dialog's, so the camera's release finishes after the dialog has gone.
 */
internal fun platformCameraEngine(context: Context): CameraEngine {
    val app = context.applicationContext
    val engine = PlatformCameraEngine(
        camera = Camera2PlatformCamera(app),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        forceFallback = { CameraEngineSwitch.forceFallback },
        orientationSource = AndroidOrientationSource(app),
    )
    ActivePlatformEngine.current = engine
    return EngineASession(
        platform = engine,
        cameraXFactory = { CameraXEngine(CameraXCaptureSession(app)) },
        platformViewfinder = { modifier, held -> PlatformViewfinder(engine, modifier, held) },
        log = { Log.i(ENGINE_LOG_TAG, it) },
    )
}

/** The device's orientation from the same listener `CameraXCaptureSession.open` uses; logged when the phone has none. */
internal class AndroidOrientationSource(private val context: Context) : OrientationSource {
    override fun start(onDegrees: (Int) -> Unit): () -> Unit {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) = onDegrees(orientation)
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        } else {
            Log.i(ENGINE_LOG_TAG, "engine=platform event=no_orientation_sensor reason=capture rotation will follow the window instead")
        }
        return { listener.disable() }
    }
}

/**
 * Engine A's viewfinder: a `TextureView` in the dialog's existing viewfinder slot, as `CameraXCaptureSession.Viewfinder`'s
 * `PreviewView` sits there, filling it and cropping the overflow (`PreviewView`'s default framing, [fillCenterScale]).
 *
 * The view's own SurfaceTexture is the session's viewfinder stream, handed to the engine when it is available and taken
 * back when it is destroyed; the listener returns false there, so the view does not release a texture the camera may
 * still be drawing into ([Camera2PreviewTarget]). A `TextureView` is used, not a `SurfaceView`, for the reason the CameraX
 * viewfinder uses one when the camera holds its natural frame (RECORD -985): a `SurfaceView` is its own surface and does
 * not follow a Compose layer's turn.
 *
 * Held ([held], the large-screen hold): the content is laid out in the device's natural frame and turned by the layer, so
 * the picture is drawn as in the natural frame, rotation 0, and the layer's turn is the only one. Not held (every phone,
 * and split screen): the window's own rotation is corrected, as the platform's camera sample does for a `TextureView`.
 */
@Composable
internal fun PlatformViewfinder(engine: PlatformCameraEngine, modifier: Modifier, held: Boolean) {
    val context = LocalContext.current
    val view = remember { TextureView(context) }
    val isHeld by rememberUpdatedState(held)

    DisposableEffect(view, engine) {
        var current: Camera2PreviewTarget? = null
        var disposed = false
        fun applyTransform() {
            val facts = engine.facts ?: return
            val windowRotation = view.display?.rotation ?: Surface.ROTATION_0
            engine.displayRotation = windowRotation
            val rotation = if (isHeld) Surface.ROTATION_0 else windowRotation
            view.setTransform(previewMatrix(view.width, view.height, facts.previewSize, facts.sensorOrientation, rotation))
        }
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                if (disposed) return
                engine.facts?.previewSize?.let { size -> surface.setDefaultBufferSize(size.width, size.height) }
                val target = Camera2PreviewTarget(surface)
                current = target
                applyTransform()
                engine.onPreviewTargetAvailable(target)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = applyTransform()

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                val target = current
                current = null
                if (target != null) {
                    if (!disposed) engine.onPreviewTargetGone(target)
                    target.onViewGone()
                }
                // False: the target releases the texture once the session has stopped drawing into it too.
                return target == null
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        view.surfaceTextureListener = listener
        view.surfaceTexture?.takeIf { view.isAvailable }?.let { listener.onSurfaceTextureAvailable(it, view.width, view.height) }
        onDispose {
            disposed = true
            // The session stops drawing into the surface now; the view lets go of it when it is detached, which calls
            // onSurfaceTextureDestroyed above, and only then is the texture released.
            current?.let { engine.onPreviewTargetGone(it) }
        }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

/**
 * The `TextureView` transform. Rotation 0 and 180: [fillCenterScale] about the centre (and a half turn for 180). Rotation
 * 90 and 270: the platform's own camera sample's `configureTransform`, which maps the buffer into the turned window and
 * fills it. Only rotation 0 is reached on a phone (the camera window is held portrait, RECORD -980 to -982).
 */
internal fun previewMatrix(viewWidth: Int, viewHeight: Int, preview: PixelSize, sensorOrientation: Int, displayRotation: Int): Matrix {
    val matrix = Matrix()
    if (viewWidth <= 0 || viewHeight <= 0) return matrix
    val centreX = viewWidth / 2f
    val centreY = viewHeight / 2f
    when (displayRotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> {
            val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            val bufferRect = RectF(0f, 0f, preview.height.toFloat(), preview.width.toFloat())
            bufferRect.offset(centreX - bufferRect.centerX(), centreY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(viewHeight.toFloat() / preview.height, viewWidth.toFloat() / preview.width)
            matrix.postScale(scale, scale, centreX, centreY)
            matrix.postRotate(90f * (displayRotation - 2), centreX, centreY)
        }
        else -> {
            val fill = fillCenterScale(viewWidth, viewHeight, preview.width, preview.height, sensorOrientation)
            matrix.setScale(fill.scaleX, fill.scaleY, centreX, centreY)
            if (displayRotation == Surface.ROTATION_180) matrix.postRotate(180f, centreX, centreY)
        }
    }
    return matrix
}
