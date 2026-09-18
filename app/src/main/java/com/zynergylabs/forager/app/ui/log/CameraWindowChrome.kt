package com.zynergylabs.forager.app.ui.log

import android.util.Log
import android.view.View
import android.view.Window
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

private const val TAG = "CameraWindowChrome"
