package com.zynergylabs.forager.app.photo

import android.graphics.ImageFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The [CameraCaptureSession] every test drives the camera screen with (groundwork PR, 2026-09-15).
 * Shared here, beside [FileProviderCacheReset], because it used to be a private class inside
 * `InAppCameraDialogTest` and every later capture mode's tests would have grown it or copied it.
 *
 * ## Scripted behaviours
 *
 * - [state] is **observable**: backed by `mutableStateOf`, so a test that flips it after
 *   composition sees the screen recompose. The private original was a plain `var`, which meant a
 *   mid-composition flip silently did nothing; no test relied on that, and one now asserts the
 *   opposite (`InAppCameraDialogTest`, "a session that becomes ready after composition …").
 * - [capture] writes a real file. On success, two bytes and a [CaptureOutcome] naming the
 *   destination and [ImageFormat.JPEG]. On failure — after [failEveryCapture], until [recover] —
 *   **a one-byte stub and then** `Result.failure`. The stub is the point: a camera that errors
 *   mid-write leaves something behind, and a fake that wrote nothing would let "a failed capture
 *   leaves no file behind" pass whether or not the screen cleaned up. A revert check found exactly
 *   that on the original.
 * - [captureCalls] counts calls, so a test can tell "four photos" from "a count that went up".
 */
internal class FakeCameraCaptureSession(
    state: CameraSessionState = CameraSessionState.Ready,
) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(state)

    var captureCalls = 0
        private set

    private var failing = false

    fun failEveryCapture() { failing = true }

    fun recover() { failing = false }

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
