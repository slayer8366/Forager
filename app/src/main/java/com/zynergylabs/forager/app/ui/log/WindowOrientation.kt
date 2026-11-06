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
 * Sets the Activity's `requestedOrientation` for as long as this is in composition — **held
 * portrait** — and puts back what was there when it leaves.
 *
 * ## Since RECORD -980 and -981: the window is held portrait, always
 *
 * The owner, testing the removal below: "the camera rotates completely", "I never asked the lock to
 * be removed. I only wanted the keep in portrait feature removed. The camera is to remain as is",
 * then "The camera is to never, ever, rotate like this. This is a cardinal sin". So the request is
 * [CAMERA_WINDOW_ORIENTATION], `SCREEN_ORIENTATION_PORTRAIT`, with no setting. What the old lock
 * also did and this does **not**: it pinned the session's device rotation to `ROTATION_0`, so the
 * controls never turned and a sideways photo saved portrait. Now the controls turn in place by
 * [rotateWithDevice] (sensor minus a display that stays at `ROTATION_0`), and each shot takes the
 * sensor's rotation, so a photo taken sideways saves the right way up. That is the camera's shape
 * before 2026-09-19 (a held window, turning controls), with `PORTRAIT` in place of `LOCKED`.
 *
 * The 2026-09-19 reason for letting the window turn, below, was the system status bar, which an app
 * cannot move off the display's top edge. It no longer applies: the status bar is hidden while the
 * camera is open (`HideStatusBarWhileCameraIsOpen`, `CameraWindowChrome.kt`). The navigation bar
 * stays on the portrait window's bottom edge.
 *
 * **Large screens.** With `targetSdk` 37, Android 17 ignores orientation requests on displays whose
 * smallest width is greater than 600 dp, so there the window still turns. The arrangement follows the
 * window ([cameraArrangement]) and the glyphs cancel the turn, so the camera keeps working, but it
 * would visibly rotate there. Since RECORD -985 it does not: `NaturalOrientationHold` lays the camera out in the device's
 * natural frame and turns it back, in a full-screen window.
 *
 * Everything from here down is the history it replaces, left as written.
 *
 * ## Since dispatch 2026-10-13-03 there is no setting
 *
 * "Lock camera to portrait" asked for `SCREEN_ORIENTATION_PORTRAIT` instead; the owner had it
 * removed (RECORD -968), so the request is always the setting-off value below, which was also the
 * default. The removed branch and how to restore it are in `docs/archive/camera-portrait-lock.md`.
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
 * and the animation the lock existed to prevent is suppressed another way: the Activity's own
 * window asks the platform for seamless rotation ([RequestSeamlessRotation], `CameraWindowChrome.kt`),
 * under which the window is re-laid out in the new rotation with no animation.
 *
 * A first attempt made that request on a `Dialog`'s window and the platform ignored it, for a
 * structural reason recorded at `docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`:
 * the Shell picks a rotation's animation from the *task's main window*, which is never a Dialog.
 * The camera now draws in the Activity's own window, which is that window.
 *
 * What the lock's history still explains: the arrangement follows the window
 * ([cameraArrangement], the keyed `remember` in `InAppCameraDialog`) because a `LOCKED` window
 * turned out to turn anyway across a background-and-return (device, 2026-09-18), and that following
 * is exactly what a window that now turns on every hold needs. The glyphs' angle is sensor minus
 * display ([rotateWithDevice]), written from the start so that a window that carries the controls
 * cancels the turn; with the window following, the two terms agree in every steady hold and the
 * glyphs stay put because the window moved them.
 *
 * ## The value: `SCREEN_ORIENTATION_SENSOR`
 *
 * - **`SCREEN_ORIENTATION_SENSOR`** (the setting-off value while there was a setting). Portrait and both landscapes, and **never reverse
 *   portrait**. The platform's rotation policy is what draws that line
 *   (`DisplayRotation.rotationForOrientation`, android16-release): for `SENSOR` it takes the
 *   sensor's rotation, except that a sensor reading of `ROTATION_180` is only honoured when the
 *   device's own `config_allowAllRotations` is set (tablets) or the request is `FULL_SENSOR` /
 *   `FULL_USER`. On a phone under `SENSOR` a half turn therefore resolves to `lastRotation`: the
 *   window stays exactly where it was.
 *
 *   **That is the point, not a limitation** (owner, 2026-09-19, from the reference app). Turning the
 *   phone end-over-end with the camera open leaves the window alone, so there is no rotation to
 *   animate and the status bar does not move — which is what Samsung's camera was observed doing,
 *   and why photos still come out upright there: capture rotation comes from the sensor, not the
 *   window. Ours is the same split, and [CameraXCaptureSession]'s `OrientationEventListener` keeps
 *   reading all four regardless of what the window may occupy.
 *
 *   This replaced `SCREEN_ORIENTATION_FULL_SENSOR`, which was set earlier the same day so that the
 *   bar would be on the phone's top edge in *every* hold including upside-down. It bought that one
 *   hold at the price of a visible rotation animation on the half turn, and the owner's ruling is
 *   that the reference app's behaviour is the right one. Do not put `FULL_SENSOR` back without
 *   reading [cameraArrangement]'s note on what becomes reachable again if you do.
 *
 *   One consequence is unchanged from `FULL_SENSOR` and worth knowing: `SENSOR` sits outside the
 *   `USER_ROTATION_FREE` guard in that same branch, so the window follows the sensor **whether or
 *   not the user has auto-rotate locked**, and the rotation sensor runs for the camera's duration
 *   (`needSensorRunning`, same file). `USER` / `FULL_USER` would honour the lock, and were not
 *   taken: the requirement is that the bar travels with the phone, and under a user lock it would
 *   not.
 * - *Was, until dispatch 2026-10-13-03:* **setting on, `SCREEN_ORIENTATION_PORTRAIT`**, from
 *   `e51b3ae`: one flip at open from a landscape window, then nothing turned, and the photo was
 *   tagged portrait too, through a gate on the session's sensor reading that is also gone.
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
internal fun RequestWindowOrientation() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity == null) {
            Log.w(TAG, "No Activity behind this context; the camera's window orientation is not requested and is left to the platform.")
            return@DisposableEffect onDispose {}
        }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = CAMERA_WINDOW_ORIENTATION
        onDispose { activity.requestedOrientation = previous }
    }
}

/**
 * The `requestedOrientation` the camera holds: portrait (RECORD -980, -981). `PORTRAIT` rather than `SENSOR_PORTRAIT` or
 * `USER_PORTRAIT`, which admit reverse portrait (the old lock's reason, `e51b3ae`). Was `SCREEN_ORIENTATION_SENSOR`.
 */
internal const val CAMERA_WINDOW_ORIENTATION: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

/** The Activity behind a composition's context, or null — shared with `CameraWindowChrome`, which needs the same window. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "WindowOrientation"
