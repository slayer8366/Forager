package com.zynergylabs.forager.app.photo.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FlashMode
import com.zynergylabs.forager.app.photo.LensControlsSlot
import com.zynergylabs.forager.app.photo.RawShot
import java.io.File

/**
 * The session the dialog gets when the debug switch chooses engine A: the platform engine first, and CameraX for the rest
 * of this camera session the moment the platform engine gives up (dispatch 2026-10-16-02: "Automatic fallback to CameraX
 * if the prototype can't open, configure or stream, logged with the reason").
 *
 * Every member reads whichever engine is [active], and [active] is Compose state, so the dialog recomposes onto CameraX's
 * session, and its viewfinder, in the frame the fallback lands. The platform engine has released the camera before it
 * reports the fallback ([PlatformCameraEngine.onFallback]), so CameraX never opens a device the prototype still holds.
 *
 * What does not carry over: the flash mode (CameraX opens at Off, as every camera does) and the shot count's camera (the
 * dialog's count is its own and is kept). The format does carry: the chip's request is replayed to CameraX before its open,
 * as the dialog itself does at open.
 */
internal class EngineASession(
    private val platform: PlatformCameraEngine,
    private val cameraXFactory: () -> CameraEngine,
    private val platformViewfinder: @Composable (Modifier, Boolean) -> Unit,
    private val log: (String) -> Unit,
) : CameraCaptureSession, CameraEngine {

    override val session: CameraCaptureSession get() = this

    private var cameraX: CameraEngine? by mutableStateOf(null)
    private val active: CameraCaptureSession get() = cameraX?.session ?: platform

    var fallbackReason: String? by mutableStateOf(null)
        private set

    private var requestedFormat = CameraFormat.Jpg
    private var owner: LifecycleOwner? = null
    private var isOpen = false

    init {
        platform.onFallback = { reason -> fallBack(reason) }
    }

    private fun fallBack(reason: String) {
        if (cameraX != null) return
        fallbackReason = reason
        log("engine=composite event=camerax_fallback reason=$reason")
        val lifecycleOwner = owner
        if (!isOpen || lifecycleOwner == null) {
            log("engine=composite event=camerax_fallback_skipped reason=the dialog closed before the fallback landed")
            return
        }
        val engine = cameraXFactory()
        engine.session.requestOutputFormat(requestedFormat)
        engine.session.open(lifecycleOwner)
        cameraX = engine
    }

    override val state: CameraSessionState get() = active.state
    override val deviceRotation: Int? get() = active.deviceRotation
    override val hasFlashUnit: Boolean get() = active.hasFlashUnit
    override val flashMode: FlashMode get() = active.flashMode
    override val capabilities: CameraCapabilities? get() = active.capabilities
    override val encoderCapabilities: EncoderCapabilities? get() = active.encoderCapabilities
    override val outputFormat: CameraFormat get() = active.outputFormat

    override fun open(lifecycleOwner: LifecycleOwner) {
        if (isOpen) return
        isOpen = true
        owner = lifecycleOwner
        active.open(lifecycleOwner)
    }

    override fun close() {
        if (!isOpen) return
        isOpen = false
        owner = null
        if (ActivePlatformEngine.current === platform) ActivePlatformEngine.current = null
        // The platform engine's close is a no-op once it has fallen back (it released then); CameraX's closes CameraX.
        platform.close()
        cameraX?.session?.close()
    }

    override fun setFlashMode(mode: FlashMode) = active.setFlashMode(mode)

    override fun requestOutputFormat(format: CameraFormat) {
        requestedFormat = format
        active.requestOutputFormat(format)
    }

    override suspend fun capture(destination: File): Result<Unit> = active.capture(destination)

    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> = active.captureRaw(jpegDestination)

    /**
     * The lens row (dispatch 2026-10-16-04): the platform engine's, while it runs and has a lens set; none once CameraX has
     * taken over, so the fallback camera is today's camera exactly. Both reads are Compose state, so the row leaves in the
     * frame the fallback lands.
     */
    override val lensControls: LensControlsSlot?
        get() = if (cameraX == null && platform.lensSet != null) platformLensRow else null

    private val platformLensRow: LensControlsSlot = { vertical, deviceRotation, displayRotation ->
        LensRow(platform, vertical, deviceRotation, displayRotation)
    }

    @Composable
    override fun Viewfinder(modifier: Modifier, held: Boolean) {
        val fallback = cameraX
        if (fallback != null) fallback.Viewfinder(modifier, held) else platformViewfinder(modifier, held)
    }
}
