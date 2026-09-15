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
 * Pins the Activity's window — to portrait, or to wherever it already is, by the setting — for as
 * long as this is in composition, and puts back what was there when it leaves. Owner's
 * requirement, 2026-09-15, from the device check: with
 * `configChanges` handled, a rotation re-laid out the window under the camera and the system
 * played its rotation animation over the viewfinder, so the frame visibly spun. *"The only thing
 * that should rotate is the text, and it should rotate in place. The frame itself should not
 * visually flip, because the screen is already rotating with the phone."*
 *
 * ## Why stop the window rather than counter-rotate the preview
 *
 * The preview's transform is `PreviewView`'s own business: it re-transforms on its display
 * listener (`onDisplayChanged` → `redrawPreview`, by `javap` on camera-view 1.6.2) and on a
 * layout-size change. With the window locked, neither happens: the Activity's `Display.getRotation()`
 * does not change and the view keeps its size, so the preview's transform never changes, and a
 * preview whose transform never changes shows the scene correctly to the person holding the
 * rotating phone. Counter-rotating it would be fighting a listener that is not firing.
 *
 * ## The value depends on the setting: `LOCKED` off, `PORTRAIT` on (device check on `51882cb`, step 3.4)
 *
 * Two constraints have to hold together, the owner's ruling: nothing in the camera layout moves
 * while the camera is open — no reflow, no rotation animation of the app — and in a landscape
 * window the shutter is on the right edge, never along the bottom. So the lock is conditional on
 * Settings' "Lock camera to portrait":
 *
 * - **Setting off: `SCREEN_ORIENTATION_LOCKED`.** The window pins to whatever it already was when
 *   the camera opened, so nothing is forced and there is no flip. Rotating the phone afterwards
 *   changes nothing about the window or the layout. The dialog lays out for the window's shape,
 *   read once at open ([CameraArrangement]) — which is what the first `LOCKED` version lacked when
 *   it was tried and rejected (step 2, case 2.4): it kept the landscape window but laid the
 *   portrait controls into it, shutter along the bottom. The window was right; the layout was not.
 * - **Setting on: `SCREEN_ORIENTATION_PORTRAIT`**, as before. Forced portrait is the point of the
 *   setting, so the one flip when opening from landscape is what was asked for, not a defect.
 *   `PORTRAIT` rather than `SENSOR_PORTRAIT` or `USER_PORTRAIT`: those admit reverse portrait, a
 *   half-turn flip of the window when the phone is held upside down, which is exactly the kind of
 *   movement this exists to stop.
 *
 * **What `LOCKED` pins, and when.** Android defines it as locking the orientation to its current
 * rotation, whatever that is, at the moment the request is made; that it does so was seen on the
 * device in step 2 (a camera opened while held landscape kept a landscape window). The request is
 * made in this effect, which runs once when the dialog enters composition; nothing else in the
 * app sets `requestedOrientation` (grep, 2026-09-15), `configChanges` keeps a rotation from
 * recreating the Activity, and the setting cannot change while the dialog covers Settings, so
 * nothing in the open path can move the window after this runs.
 *
 * **Two things the platform decides, on the device check.** OEMs vary in how they honour a
 * runtime `requestedOrientation`, and from Android 16 the platform ignores orientation requests
 * on large screens (smallest width 600dp and above) for apps targeting API 36+; this app targets
 * 37. On a phone the lock holds; on a tablet or an unfolded foldable it may not, and the frame
 * would rotate as before, with the arrangement held as chosen at open.
 *
 * ## Capture and capture rotation are untouched
 *
 * `CameraXCaptureSession` takes each shot's `targetRotation` from its own `OrientationEventListener`
 * (`CameraXCaptureSession.kt`, the `snapToSurfaceRotation` line), never from the display, which is
 * the decoupling that makes locking the window safe either way: the sensor keeps reporting while
 * the window stays put, so a landscape photo is still tagged landscape. The one display read left
 * in that path is the logged fallback for a shot with no sensor reading; under a landscape
 * `LOCKED` window it reads `ROTATION_90`, which is the right fallback for that window.
 *
 * ## Restore, and its edges
 *
 * The previous value is read once, when the effect starts, and written back when it ends; in this
 * app nothing else sets `requestedOrientation` (grep, 2026-09-15), so "previous" is the manifest's
 * `UNSPECIFIED`. *Dismissed normally:* Done leaves the dialog, the effect disposes, restored.
 * *Activity recreated while open* (a night-mode toggle; rotation itself no longer recreates and
 * is locked here anyway): the old instance's dispose restores on its way out, the ViewModel
 * reopens the dialog on the new instance and this locks it again. *Process death:* the request
 * lives on the Activity's window token, which is gone; a new process starts unlocked and the
 * camera closed. *No Activity behind the context* (a preview, a plain `ContextWrapper`): logged,
 * and the window is left to rotate rather than fabricating a lock on nothing.
 *
 * Reachable under Robolectric only as the requested value: `Activity.requestedOrientation` reads
 * back what was set. Whether the window actually stops rotating is the device's.
 */
@Composable
internal fun LockWindowOrientation(lockToPortrait: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, lockToPortrait) {
        if (activity == null) {
            Log.w(TAG, "No Activity behind this context; the camera's window is not locked and will rotate with the device.")
            return@DisposableEffect onDispose {}
        }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = windowLockFor(lockToPortrait)
        onDispose { activity.requestedOrientation = previous }
    }
}

/** The `requestedOrientation` the camera holds: forced portrait when the setting is on, otherwise pinned where the window already is. Pure, tested. */
internal fun windowLockFor(lockToPortrait: Boolean): Int =
    if (lockToPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LOCKED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "WindowOrientationLock"
