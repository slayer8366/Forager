package com.zynergylabs.forager.app.ui.log

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Sets the Activity's `requestedOrientation` for as long as this is in composition — forced
 * portrait with "Lock camera to portrait" on, **following the device through all four
 * orientations** with it off — and puts back what was there when it leaves.
 *
 * ## Superseding note, 2026-09-19: the window follows the device; the lock is gone for setting-off
 *
 * Until `80855c3` this file was `WindowOrientationLock.kt` and this composable `LockWindowOrientation`:
 * with the setting off it pinned the window with `SCREEN_ORIENTATION_LOCKED` so that nothing in the
 * camera moved while it was open, and the arrangement, the glyph rotation, the device check and
 * two audit reports were written against that pin. The owner reversed it (dispatch of 2026-09-19)
 * for a reason the lock could not meet however it was tuned: **the system status bar belongs to the
 * top of the display as the system has it rotated, and an app cannot move it to another edge.**
 * While the window was pinned the display did not rotate, so the bar stayed on whichever physical
 * edge was up at open — raised three times, and impossible under any lock. So the window turns,
 * and the animation the lock existed to prevent is meant to be suppressed another way: the dialog's
 * window asks the platform for seamless rotation ([RotateThisWindowSeamlessly],
 * `CameraWindowChrome.kt`).
 *
 * **Measured 2026-09-19, and it does not work: the platform still plays its rotation animation on
 * every turn.** The reason is structural and is written up on [RotateThisWindowSeamlessly] — the
 * Shell picks the animation from the *task's* main window, which is never a Dialog. So this file's
 * `FULL_SENSOR` half is built and measured, and the half that makes it acceptable is not; the
 * change is **not shipped** pending the owner's decision
 * (`docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`).
 *
 * What the lock's history still explains: the arrangement follows the window
 * ([cameraArrangement], the keyed `remember` in `InAppCameraDialog`) because a `LOCKED` window
 * turned out to turn anyway across a background-and-return (device, 2026-09-18), and that following
 * is exactly what a window that now turns on every hold needs. The glyphs' angle is sensor minus
 * display ([rotateWithDevice]), written from the start so that a window that carries the controls
 * cancels the turn; with the window following, the two terms agree in every steady hold and the
 * glyphs stay put because the window moved them.
 *
 * ## The value depends on the setting: `FULL_SENSOR` off, `PORTRAIT` on
 *
 * - **Setting off: `SCREEN_ORIENTATION_FULL_SENSOR`.** All four orientations, reverse portrait
 *   included — `SENSOR` alone would leave the phone-held-upside-down case as the one hold where the
 *   bar sits on the wrong edge. Two consequences, from the platform's rotation policy
 *   (`DisplayRotation.rotationForOrientation`, android16-release, the `SENSOR`/`FULL_SENSOR`
 *   branch): the window follows the sensor **whether or not the user has auto-rotate locked** in
 *   system settings, and the rotation sensor is switched on for the camera's duration
 *   (`needSensorRunning`, same file). `FULL_USER`, which would honour the user's lock, was
 *   considered and not taken: the requirement is that the bar travels with the phone, and under
 *   a user lock it would not.
 * - **Setting on: `SCREEN_ORIENTATION_PORTRAIT`**, unchanged since `e51b3ae`. The setting's whole
 *   point is a portrait camera: one flip at open from a landscape window, then nothing turns —
 *   not the window, not a glyph ([effectiveDeviceRotation] pins the sensor term). `PORTRAIT`
 *   rather than `SENSOR_PORTRAIT`/`USER_PORTRAIT`, which admit reverse portrait.
 *
 * ## Capture and capture rotation are untouched
 *
 * `CameraXCaptureSession.capture` takes each shot's `targetRotation` from the session's own
 * `OrientationEventListener`, snapped by `snapToSurfaceRotation`, and the display enters that path
 * only as a logged fallback for a shot with no reading yet. The JPEG's requested rotation is the
 * sensor's mounting minus that target, and the window's rotation is not a term in it — measured on
 * the emulator before and after this change, four holds each, same `requestDegrees` in every hold
 * (`docs/audits/2026-09-19-unlock-seamless-rotation-prebuild-report.md`).
 *
 * ## Restore, and its edges
 *
 * The previous value is read once, when the effect starts, and written back when it ends; nothing
 * else in this app sets `requestedOrientation` (grep, 2026-09-19), so "previous" is the manifest's
 * `UNSPECIFIED`. *Dismissed with Back:* the effect disposes, restored — and if the user's own
 * rotation setting disagrees with the hold, the Activity's window turns back then, with the app's
 * ordinary rotation animation; the camera is already gone. *Activity recreated while open* (a
 * night-mode toggle; rotation itself does not recreate, `configChanges`): the old instance's
 * dispose restores on its way out, the ViewModel reopens the dialog on the new instance and this
 * requests again. *Process death:* the request lives on the Activity's window token, which is gone.
 * *No Activity behind the context* (a preview, a plain `ContextWrapper`): logged, and the window
 * is left to the platform's default rather than fabricating a request on nothing.
 *
 * Reachable under Robolectric only as the requested value: `Activity.requestedOrientation` reads
 * back what was set. Whether the window actually turns, and whether it turns without an animation,
 * are the device's and the emulator's.
 */
@Composable
internal fun RequestWindowOrientation(lockToPortrait: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, lockToPortrait) {
        if (activity == null) {
            Log.w(TAG, "No Activity behind this context; the camera's window orientation is not requested and is left to the platform.")
            return@DisposableEffect onDispose {}
        }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = windowOrientationFor(lockToPortrait)
        onDispose { activity.requestedOrientation = previous }
    }
}

/** The `requestedOrientation` the camera holds: forced portrait when the setting is on, otherwise following the device through all four orientations. Pure, tested. */
internal fun windowOrientationFor(lockToPortrait: Boolean): Int =
    if (lockToPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "WindowOrientation"
