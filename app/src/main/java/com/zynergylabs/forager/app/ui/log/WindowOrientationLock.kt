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
 * Pins the Activity's window to portrait for as long as this is in composition, and puts back
 * what was there when it leaves. Owner's requirement, 2026-09-15, from the device check: with
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
 * ## `SCREEN_ORIENTATION_PORTRAIT`, after `LOCKED` was tried
 *
 * The first version used `LOCKED`, "whatever orientation the Activity is in now", so a camera
 * opened while the phone was held landscape kept a landscape window, and the dialog laid out for
 * it: the shutter landed at the bottom of a landscape frame instead of where it sits in portrait
 * (device check, step 2, case 2.4). The requirement is a frame that is identical every time the
 * camera opens, with only the controls turning, so the lock is now a fixed portrait value.
 * `PORTRAIT` rather than `SENSOR_PORTRAIT` or `USER_PORTRAIT`: those admit reverse portrait, a
 * half-turn flip of the window when the phone is held upside down, which is exactly the kind of
 * movement this exists to stop; and this app's own layouts are portrait and landscape, with no
 * reverse variants that a reverse-portrait window would serve. The accepted cost, the owner's
 * decision: opening the camera while holding the phone landscape shows one rotation animation as
 * the window flips to portrait, and nothing moves after it. Not suppressed.
 *
 * **Two things the platform decides, on the device check.** OEMs vary in how they honour a
 * runtime `requestedOrientation`, and from Android 16 the platform ignores orientation requests
 * on large screens (smallest width 600dp and above) for apps targeting API 36+; this app targets
 * 37. On a phone the lock holds; on a tablet or an unfolded foldable it may not, and the frame
 * would rotate as before, with everything else still correct.
 *
 * ## Capture and capture rotation are untouched
 *
 * `CameraXCaptureSession` takes each shot's `targetRotation` from its own `OrientationEventListener`
 * (`CameraXCaptureSession.kt`, the `snapToSurfaceRotation` line), never from the display, which is
 * the decoupling that makes locking the window safe: the sensor keeps reporting while the window
 * stays put, so a landscape photo is still tagged landscape.
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
internal fun LockWindowOrientation() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity == null) {
            Log.w(TAG, "No Activity behind this context; the camera's window is not locked and will rotate with the device.")
            return@DisposableEffect onDispose {}
        }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "WindowOrientationLock"
