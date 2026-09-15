package com.zynergylabs.forager.app.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * A [FakeCameraCaptureSession] whose [deviceRotation] goes through the real gate,
 * [effectiveDeviceRotation], exactly as `CameraXCaptureSession.deviceRotation` does: a test sets
 * the **sensor's** reading and the setting decides whether the screen ever sees it.
 *
 * Why a second fake rather than a flag on the first: every existing test sets
 * `FakeCameraCaptureSession.deviceRotation` directly, which is the right seam for "the dialog
 * turns a control for the value it is handed" — the wiring. It is the wrong seam for "with the
 * setting on, nothing turns whatever the device does", because a test that sets the gated value by
 * hand has bypassed the gate; that is how v4 step 4.2, the test named for it and the code disagreed
 * from the day the setting shipped without anything noticing (2026-09-18). This fake cannot bypass
 * it.
 */
internal class GatedFakeCameraCaptureSession(
    private val lockToPortrait: Boolean,
    private val inner: FakeCameraCaptureSession = FakeCameraCaptureSession(),
) : CameraCaptureSession {

    /** The orientation listener's snapped reading, as `CameraXCaptureSession.sensorRotation` holds it. */
    var sensorRotation: Int? by mutableStateOf(null)

    override val deviceRotation: Int? get() = effectiveDeviceRotation(lockToPortrait, sensorRotation)

    override val state: CameraSessionState get() = inner.state
    override fun open(lifecycleOwner: LifecycleOwner) = inner.open(lifecycleOwner)
    override fun close() = inner.close()
    override suspend fun capture(destination: File): Result<CaptureOutcome> = inner.capture(destination)
}
