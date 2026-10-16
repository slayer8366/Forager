package com.zynergylabs.forager.app.photo.engine

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.zynergylabs.forager.app.domain.CameraCapabilities
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.EncoderCapabilities
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.CameraSessionState
import com.zynergylabs.forager.app.photo.FlashMode
import com.zynergylabs.forager.app.photo.RawShot
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Where the engine is. Observable state for the tests and the summary; the screen reads [CameraCaptureSession.state]. */
internal enum class EnginePhase { Idle, Opening, AwaitingViewfinder, Configuring, Streaming, Paused, Disconnected, FellBack, Closed }

/** The device's orientation, from `OrientationEventListener` on a phone. Returns the stop. */
internal fun interface OrientationSource {
    fun start(onDegrees: (Int) -> Unit): () -> Unit

    companion object {
        val None = OrientationSource { { } }
    }
}

internal data class EngineTimeouts(
    /** After the repeating request starts, no frame within this is a stream that failed: fall back. */
    val firstFrameMillis: Long = 3_000L,
    /** A shot taken before the viewfinder streams waits this long for it. */
    val streamingWaitMillis: Long = 3_000L,
    /** CameraX's 3A-with-flash wait is of this order; the shot is taken when it times out, as CameraX does. */
    val precaptureMillis: Long = 3_000L,
    val lockMillis: Long = 3_000L,
    val burstMillis: Long = 10_000L,
)

internal sealed interface BurstReport {
    data class Done(val line: String, val frames: Int, val requested: Int, val activePhysicalIds: Set<String?>) : BurstReport
    data class Unsupported(val reason: String) : BurstReport
    data class Failed(val reason: String) : BurstReport
}

/**
 * Camera engine A (dispatch 2026-10-16-02): the in-app camera run through the platform camera interface, behind
 * [CameraCaptureSession]. Built in the next commit; these are the stubs the tests are written against.
 */
internal class PlatformCameraEngine(
    private val camera: PlatformCamera,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val log: (String) -> Unit = { Log.i(ENGINE_LOG_TAG, it) },
    private val forceFallback: () -> Boolean = { false },
    private val orientationSource: OrientationSource = OrientationSource.None,
    private val timeouts: EngineTimeouts = EngineTimeouts(),
) : CameraCaptureSession {

    /** Called once, after the engine has released the camera, when it gives up: open, configure or stream failed. */
    var onFallback: ((reason: String) -> Unit)? = null

    val phase: EnginePhase get() = TODO("engine: built in the next commit")
    val facts: CameraFacts? get() = TODO("engine: built in the next commit")

    /** The window's rotation, from the viewfinder: the fallback for a shot taken before the orientation listener has read. */
    var displayRotation: Int? = null

    override val state: CameraSessionState get() = TODO("engine: built in the next commit")
    override val deviceRotation: Int? get() = TODO("engine: built in the next commit")
    override val hasFlashUnit: Boolean get() = TODO("engine: built in the next commit")
    override val flashMode: FlashMode get() = TODO("engine: built in the next commit")
    override val capabilities: CameraCapabilities? get() = TODO("engine: built in the next commit")
    override val encoderCapabilities: EncoderCapabilities? get() = TODO("engine: built in the next commit")
    override val outputFormat: CameraFormat get() = TODO("engine: built in the next commit")

    override fun open(lifecycleOwner: LifecycleOwner): Unit = TODO("engine: built in the next commit")
    override fun close(): Unit = TODO("engine: built in the next commit")
    override fun setFlashMode(mode: FlashMode): Unit = TODO("engine: built in the next commit")
    override fun requestOutputFormat(format: CameraFormat): Unit = TODO("engine: built in the next commit")
    override suspend fun capture(destination: File): Result<Unit> = TODO("engine: built in the next commit")
    override suspend fun captureRaw(jpegDestination: File): Result<RawShot> = TODO("engine: built in the next commit")

    fun onDeviceOrientation(orientationDegrees: Int): Unit = TODO("engine: built in the next commit")
    fun onPreviewTargetAvailable(target: PreviewTarget): Unit = TODO("engine: built in the next commit")
    fun onPreviewTargetGone(target: PreviewTarget): Unit = TODO("engine: built in the next commit")

    /** The 8-frame RAW burst, a debug method with no UI: in this running session, exposure and white balance locked. */
    suspend fun burst(frames: Int = BURST_FRAMES): BurstReport = TODO("engine: built in the next commit")

    fun summary(): String = TODO("engine: built in the next commit")

    companion object {
        const val BURST_FRAMES = 8
        const val IN_USE_MESSAGE = "The camera is in use by another app. It comes back when that app lets it go."
    }
}

/** `adb logcat -v raw -s CameraEngineA`: every line the prototype writes, CameraX's timings included. */
internal const val ENGINE_LOG_TAG = "CameraEngineA"
