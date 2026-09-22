package com.zynergylabs.forager.app.photo

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
 * - [capture] writes a real file. On success, two bytes. On failure — after [failEveryCapture],
 *   until [recover] — **a one-byte stub and then** `Result.failure`. The stub is the point: a
 *   camera that errors mid-write leaves something behind, and a fake that wrote nothing would let
 *   "a failed capture leaves no file behind" pass whether or not the screen cleaned up. A revert
 *   check found exactly that on the original.
 * - [captureCalls] counts, so "four photos" is distinguishable from "a count that went up".
 * - **Flash**, as the interface states it: [hasFlashUnit] is false until [open] reaches `Ready`,
 *   then [flashUnitOnOpen]; [setFlashMode] is counted in [setFlashModeCalls] and changes nothing
 *   without a unit; [close] puts the mode back to `Off` and drops the unit. A fake more permissive
 *   than the contract would let the screen pass on behaviour the real session refuses.
 */
internal class FakeCameraCaptureSession(
    state: CameraSessionState = CameraSessionState.Ready,
    private val readyOnOpen: Boolean = true,
    /** Whether the camera [open] binds has a flash unit. On by default, so the flash chip is there as on most phones. */
    private val flashUnitOnOpen: Boolean = true,
) : CameraCaptureSession {

    override var state: CameraSessionState by mutableStateOf(state)

    /** Settable so a test can turn the device and watch the screen's controls follow. */
    override var deviceRotation: Int? by mutableStateOf(null)

    /** Settable so a test can take the unit away mid-session; [open] and [close] set it as the real session does. */
    override var hasFlashUnit: Boolean by mutableStateOf(false)

    var setFlashModeCalls = 0
        private set

    private var currentFlashMode: FlashMode by mutableStateOf(FlashMode.Off)
    override val flashMode: FlashMode get() = currentFlashMode

    override fun setFlashMode(mode: FlashMode) {
        setFlashModeCalls += 1
        if (!hasFlashUnit) return // as the interface says: no unit, no change
        currentFlashMode = mode
    }

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
        if (state == CameraSessionState.Ready) hasFlashUnit = flashUnitOnOpen
    }

    override fun close() {
        closeCalls += 1
        currentFlashMode = FlashMode.Off
        hasFlashUnit = false
    }

    override suspend fun capture(destination: File): Result<Unit> {
        captureCalls += 1
        destination.parentFile?.mkdirs()
        if (failing) {
            destination.writeBytes(byteArrayOf(0xFF.toByte()))
            return Result.failure(IllegalStateException("the camera said no"))
        }
        destination.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        return Result.success(Unit)
    }
}
