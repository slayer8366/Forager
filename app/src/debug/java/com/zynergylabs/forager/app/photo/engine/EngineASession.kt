package com.zynergylabs.forager.app.photo.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraEngine
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FlashMode
import com.zynergylabs.forager.app.photo.RawShot
import java.io.File

/**
 * The session the dialog gets when the debug switch chooses engine A: the platform engine first, and CameraX for the rest
 * of this camera session the moment the platform engine gives up (dispatch 2026-10-16-02: "Automatic fallback to CameraX
 * if the prototype can't open, configure or stream, logged with the reason"). Built in the next commit.
 */
internal class EngineASession(
    private val platform: PlatformCameraEngine,
    private val cameraXFactory: () -> CameraEngine,
    private val platformViewfinder: @Composable (Modifier, Boolean) -> Unit,
    private val log: (String) -> Unit,
) : CameraCaptureSession, CameraEngine {

    override val session: CameraCaptureSession get() = this

    /** Why the platform engine gave up, once it has; null while it runs. */
    val fallbackReason: String? get() = TODO("composite: built in the next commit")

    override val state: CameraSessionState get() = TODO("composite: built in the next commit")
    override val deviceRotation: Int? get() = TODO("composite: built in the next commit")
    override val hasFlashUnit: Boolean get() = TODO("composite: built in the next commit")
    override val flashMode: FlashMode get() = TODO("composite: built in the next commit")
    override val capabilities: CameraCapabilities? get() = TODO("composite: built in the next commit")
    override val encoderCapabilities: EncoderCapabilities? get() = TODO("composite: built in the next commit")
    override val outputFormat: CameraFormat get() = TODO("composite: built in the next commit")

    override fun open(lifecycleOwner: LifecycleOwner): Unit = TODO("composite: built in the next commit")
    override fun close(): Unit = TODO("composite: built in the next commit")
    override fun setFlashMode(mode: FlashMode): Unit = TODO("composite: built in the next commit")
    override fun requestOutputFormat(format: CameraFormat): Unit = TODO("composite: built in the next commit")
    override suspend fun capture(destination: File): Result<Unit> = TODO("composite: built in the next commit")
    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> = TODO("composite: built in the next commit")

    @Composable
    override fun Viewfinder(modifier: Modifier, held: Boolean): Unit = TODO("composite: built in the next commit")
}
