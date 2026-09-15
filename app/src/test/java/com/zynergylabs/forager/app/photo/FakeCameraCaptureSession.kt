package com.zynergylabs.forager.app.photo

import android.graphics.ImageFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * The [CameraCaptureSession] every test drives the camera screen with. Shared here, beside
 * [FileProviderCacheReset], and **observable**: [state] is backed by `mutableStateOf`, so a flip
 * after composition recomposes the screen. It used to be a private class inside
 * `InAppCameraDialogTest` with a plain `var`, which is half of how the deadlock this was written
 * for went unseen (2026-09-15; see that test class's doc).
 *
 * ## Scripted behaviours
 *
 * - [open] moves [state] from `Opening` to `Ready` unless [readyOnOpen] is false, which models a
 *   provider that has not resolved yet. It never changes an `Unavailable` state: that models an
 *   open that failed. [close] only counts. Both count calls so a test can assert the screen
 *   opens once on enter and closes once on leave, which is the lifecycle the fix hangs on.
 * - [capture] writes a real file. On success, two bytes and a [CaptureOutcome] naming the
 *   destination and [ImageFormat.JPEG]. On failure — after [failEveryCapture],
 *   until [recover] — **a one-byte stub and then** `Result.failure`. The stub is the point: a
 *   camera that errors mid-write leaves something behind, and a fake that wrote nothing would let
 *   "a failed capture leaves no file behind" pass whether or not the screen cleaned up. A revert
 *   check found exactly that on the original.
 * - [captureCalls] counts, so "four photos" is distinguishable from "a count that went up".
 */
internal class FakeCameraCaptureSession(
    state: CameraSessionState = CameraSessionState.Ready,
    private val readyOnOpen: Boolean = true,
) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(state)

    /** Settable so a test can turn the device and watch the screen's controls follow. */
    override var deviceRotation: Int? by mutableStateOf(null)

    var openCalls = 0
        private set
    var closeCalls = 0
        private set
    var captureCalls = 0
        private set

    private var failing = false

    fun failEveryCapture() { failing = true }

    fun recover() { failing = false }

    override fun open(lifecycleOwner: LifecycleOwner) {
        openCalls += 1
        if (readyOnOpen && state == CameraSessionState.Opening) state = CameraSessionState.Ready
    }

    override fun close() {
        closeCalls += 1
    }

    override suspend fun capture(destination: File): Result<CaptureOutcome> {
        captureCalls += 1
        destination.parentFile?.mkdirs()
        if (failing) {
            destination.writeBytes(byteArrayOf(0xFF.toByte()))
            return Result.failure(IllegalStateException("the camera said no"))
        }
        destination.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        return Result.success(CaptureOutcome(destination, ImageFormat.JPEG))
    }
}
