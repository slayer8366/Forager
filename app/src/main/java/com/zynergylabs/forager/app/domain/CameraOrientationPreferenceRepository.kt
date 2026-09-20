package com.zynergylabs.forager.app.domain

/**
 * Settings' "Lock camera to portrait" (owner request, 2026-09-15, from the device check on the
 * window lock). Backed by DataStore, its own file, one repository per concern, the same reasons
 * [PhotoLocationPreferenceRepository] and [SundownPreferencesRepository] record.
 *
 * ## What the one flag does, and what it does not touch
 *
 * **Off** is the behaviour the camera has without the setting: the window is held portrait while
 * the camera is open, the controls turn in place to meet the phone, and a photo taken sideways is
 * saved landscape, from the orientation sensor, regardless of the system auto-rotate setting.
 * **On** freezes both: nothing in the camera turns, and a photo taken sideways is saved portrait.
 *
 * It is one gate, not two behaviours: the control angle and the shot's `targetRotation` both read
 * `CameraCaptureSession.deviceRotation`, and with the setting on that value is pinned to
 * `Surface.ROTATION_0` and the sensor is ignored, so both effects fall out of the one place
 * (`effectiveDeviceRotation`, in `CameraXCaptureSession.kt`). The window's own orientation lock
 * is not this setting's business: the window is held portrait in both states.
 *
 * **Defaults to `false`**, so an install that predates the setting keeps the camera it had. A
 * failed read is logged and leaves the off-by-default state alone, as the other preferences do.
 */
interface CameraOrientationPreferenceRepository {

    /** `false` until the user has ever turned it on. */
    suspend fun getLockCameraToPortrait(): Result<Boolean>

    suspend fun setLockCameraToPortrait(enabled: Boolean): Result<Unit>
}
