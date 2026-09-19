package com.zynergylabs.forager.app.ui.log

import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the system status bar on **the camera dialog's own window**, never the Activity's. Owner's
 * rule, 2026-09-18: the bar is hidden while the camera is open, the navigation bar stays, and the
 * bar must return on every exit.
 *
 * ## Why the dialog's window, and how that is what makes "every exit" hold
 *
 * The camera is a `Dialog`, so it has a window of its own above the Activity's. Hiding the bar
 * there means the Activity's window is never asked to change, and there is nothing to restore:
 * when the dialog goes, its window is destroyed and the Activity's — bar and all — is what remains.
 * Every exit is then covered by construction, and the exits were enumerated from the code rather
 * than assumed: Done (`onDismiss`), Back and a touch outside (`Dialog.onDismissRequest`), the
 * four-minute absence timeout (`InAppCameraViewModel.onReturnedToApp` → `close()`, which nulls the
 * target `InAppCameraHost` composes the dialog from), and the Activity going away (the dialog's
 * window goes with it). The timeout is the one a restore-on-exit design would most likely miss,
 * since it closes the dialog from a different place than the user-initiated paths; here it needs
 * nothing, which is the point of choosing this window.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` keeps a swipe from the screen's top edge able to reveal
 * the bar and pull the shade. Only `statusBars()` is hidden; the navigation bar is untouched.
 *
 * ## What is and is not tested
 *
 * [StatusBarHider] is the seam. The production one ([SystemStatusBarHider]) is a thin call into
 * `WindowInsetsControllerCompat`, which Robolectric cannot observe the effect of; what a test *can*
 * establish is that the hider is asked once, with a window that is the dialog's and not the
 * Activity's, and that the Activity's window is never passed. Whether the bar actually disappears,
 * whether it returns after each exit, and whether the swipe reveals it are emulator and device
 * items, listed as such.
 *
 * If this is ever composed outside a dialog there is no window to hide the bar on, and that is
 * logged rather than ignored — a fallback that fires silently is what CLAUDE.md rules out.
 */
internal fun interface StatusBarHider {
    fun hide(window: Window, view: View)
}

internal val SystemStatusBarHider = StatusBarHider { window, view ->
    // Nothing here about the display cut-out, and that is measured rather than assumed: with the
    // camera open, `dumpsys window` on the API 36 emulator reports this dialog's window at
    // [0,0][1080,2400] with layoutInDisplayCutoutMode=always — the full display, cut-out band
    // included — with no attribute set here. A Compose Dialog with decorFitsSystemWindows = false
    // over an edge-to-edge Activity is already full-bleed, and the strip's own band is what keeps
    // its controls out of the cut-out (CameraBands.kt). A first cut set the mode explicitly, on a
    // misreading of the camera-in-use privacy indicator as the Activity showing through; the
    // counterfactual build without it reported the same frame, so it was removed.
    WindowCompat.getInsetsController(window, view).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
    }
}

/** Must be composed inside the `Dialog`'s content, so that `LocalView` is the dialog's view. */
@Composable
internal fun HideStatusBarOnThisWindow(hider: StatusBarHider) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, hider) {
        if (window != null) {
            hider.hide(window, view)
        } else {
            Log.w(TAG, "Not inside a dialog window; the status bar cannot be hidden here and was not.")
        }
        // Nothing to undo: the window this hid the bar on is destroyed with the dialog.
        onDispose { }
    }
}

/**
 * Asks the platform to rotate **this dialog's window** seamlessly: when the display turns, the
 * window is re-laid out in the new rotation and the next frame is simply the new layout — no
 * rotation animation, no crossfade. This is what replaces the window lock for the setting-off
 * case ([RequestWindowOrientation], 2026-09-19): the window now follows the device so the system
 * status bar can sit on the phone's top edge in every hold, and the flip the lock existed to
 * prevent is suppressed here instead.
 *
 * ## Why this window's own attributes, and not the manifest
 *
 * The platform decides seamlessness on the **top fullscreen opaque window's** own layout params:
 * `DisplayRotation.shouldRotateSeamlessly` (android16-release) takes
 * `DisplayPolicy.getTopFullscreenOpaqueWindow()`, requires it to be the focused window, and
 * requires `mAttrs.rotationAnimation == ROTATION_ANIMATION_SEAMLESS` on *that* window. With the
 * camera open that window is this dialog's, not the Activity's — `dumpsys window` on the API 36
 * emulator, 2026-09-19: `mTopFullscreenOpaqueWindowState` and `mFocusedWindow` both name the
 * `ty=APPLICATION (0,0)(fillxfill)` window that is the dialog, above the `BASE_APPLICATION` one —
 * because `DisplayPolicy.applyPostLayoutPolicyLw` counts any unattached application-type window
 * whose params are `isFullscreen()` (origin 0,0 and MATCH_PARENT both ways), which a Compose
 * `Dialog` with `usePlatformDefaultWidth = false` and `decorFitsSystemWindows = false` is. The
 * manifest attribute `android:rotationAnimation` goes elsewhere: `ActivityInfo.rotationAnimation`
 * → `ActivityRecord.mRotationAnimationHint` → the *task's* animation in
 * `Transition.getTaskRotationAnimation`, never into a window's attrs, and it would make every
 * rotation anywhere in the app a jump cut. So the request is made here, on the dialog's window,
 * scoped to the camera by construction: the window is destroyed with the dialog.
 *
 * ## When it cannot apply, and what plays instead
 *
 * The platform falls back when the window is mid-animation (the dialog's own entry or exit), when
 * a picture-in-picture task or a system alert window is on screen, and — on a device whose
 * navigation bar cannot change sides and whose configuration does not allow seamless rotation
 * regardless (`config_allowSeamlessRotationDespiteNavBarMoving`, true under gesture navigation on
 * stock builds) — on any turn to or from reverse portrait. What plays then, under shell
 * transitions, is the ordinary rotate animation (`DefaultTransitionHandler.getRotationAnimationHint`:
 * the hint defaults to `ROTATION_ANIMATION_ROTATE`), not the crossfade the constant's own
 * documentation promises; that promise describes the legacy path. Which of these a given phone
 * hits is a device item, listed as such.
 *
 * Not testable under Robolectric beyond the attribute being set on the dialog's window, which the
 * dialog test reads back through the same window the status-bar seam captures.
 *
 * ## MEASURED 2026-09-19: this does not suppress the animation, and cannot while the camera is a Dialog
 *
 * The request is made and the window manager accepts it — `shouldRotateSeamlessly` returned true on
 * every turn of the emulator probe, logging `because seamless rotating` once per turn — but the
 * **animation** is chosen elsewhere and ignores it. Under shell transitions the display's change
 * carries explicit seamless only via `Transition.setSeamlessRotation`, which is called only from
 * `DisplayContent.setSeamlessTransitionForFixedRotation` (the fixed-rotation-launch path); an
 * ordinary rotation reaches only `Transition.onSeamlessRotating`, which overrides the surface sync
 * method and sets no animation flag. So the Shell's one remaining route is the **task** path,
 * `Transition.getTaskRotationAnimation`, which reads `ActivityRecord.findMainWindow` — restricted to
 * `TYPE_BASE_APPLICATION`, so never a Dialog — and then rejects unless that same window is the top
 * fullscreen opaque window, which with this dialog up it is not. No window can satisfy both halves
 * while a fullscreen Dialog covers the Activity, so putting the attribute on the Activity's window
 * or in the manifest does not help either. The probe logged
 * `task N isn't requesting seamless, so not seamless` and rotation-animation `0` (rotate) on all
 * four turns, with animation scales at 1.0.
 *
 * **This composable is therefore not doing its job today.** It is kept, not reverted, because it is
 * correct for the design it belongs to and costs nothing; the decision on what to do instead is the
 * owner's and is recorded in `docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`.
 */
@Composable
internal fun RotateThisWindowSeamlessly() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        if (window != null) {
            val attributes = window.attributes
            attributes.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
            window.attributes = attributes
        } else {
            Log.w(TAG, "Not inside a dialog window; seamless rotation cannot be requested here and was not.")
        }
        // Nothing to undo: the window this was requested on is destroyed with the dialog.
        onDispose { }
    }
}

private const val TAG = "CameraWindowChrome"
