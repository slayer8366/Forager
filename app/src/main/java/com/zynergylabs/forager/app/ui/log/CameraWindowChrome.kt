package com.zynergylabs.forager.app.ui.log

import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The camera's two claims on **the Activity's own window**, for as long as the camera is composed:
 * the system status bar is hidden, and the window rotates seamlessly. Both are put back when the
 * camera leaves composition.
 *
 * ## Superseding note, 2026-09-19: this used to be the dialog's window, and both halves changed with it
 *
 * Until `1e2e184` the camera was a `Dialog`, and this file hid the bar on that dialog's own window,
 * reached by casting `LocalView.current.parent` to `DialogWindowProvider`. The restore was free by
 * construction, which is why that window was chosen: *"when the dialog goes, its window is destroyed
 * and the Activity's — bar and all — is what remains"*, covering every exit including the
 * four-minute absence timeout, *"the one a restore-on-exit design would most likely miss"*.
 *
 * That window is gone. The camera now draws in the Activity's own window ([InAppCameraDialog]'s
 * `Box`), because a fullscreen `Dialog` above the Activity makes seamless rotation unreachable —
 * the Shell reads the *task's* main window, which is only ever `TYPE_BASE_APPLICATION`, and then
 * requires that window to be the top fullscreen opaque one, which the dialog was
 * (`docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`). Owner's ruling, 2026-09-19.
 *
 * **So restore is no longer free, and this is how it is paid.** Both effects below are
 * `DisposableEffect`s on the camera's own composition, and **every exit ends with the camera leaving
 * composition**, so `onDispose` covers all of them by construction rather than by enumeration:
 *
 * - **Back** — `BackHandler` in [InAppCameraDialog] calls the same `onDismiss` the dialog's
 *   `onDismissRequest` used to, reaching `InAppCameraViewModel.close()`, which nulls the target
 *   `InAppCameraHost` composes from. The camera leaves composition.
 * - **The four-minute absence timeout** — `InAppCameraViewModel.onReturnedToApp` → `close()`, the
 *   same null. It closes the camera from a different place than the user-initiated paths, which is
 *   exactly why this is a dispose and not a call at each exit site.
 * - **The Activity going away** — the window goes with it, and there is nothing to restore; if it is
 *   *recreated* instead (a night-mode toggle), the old instance's `onDispose` restores on the way
 *   out and the new composition hides again. Same shape as [RequestWindowOrientation]'s restore.
 * - **A touch outside** was the fourth exit on the old list and is not one here: it was
 *   `DialogProperties`' `dismissOnClickOutside`, structurally unreachable on a window with no
 *   outside, and it has no equivalent now.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` keeps a swipe from the screen's top edge able to reveal
 * the bar and pull the shade. Only `statusBars()` is touched; the navigation bar is untouched, and
 * nothing else in this app hides a system bar (grep, 2026-09-19), so "restore" means "shown",
 * which is the state every other screen is in.
 *
 * ## White icons on the revealed bar (owner, 2026-09-19)
 *
 * The camera also asks for **white** status bar icons while it is open. Two reasons, one certain
 * and one a hypothesis the device will settle.
 *
 * *Certain:* the icon appearance otherwise comes from the app's **theme** — `MainActivity`'s
 * `enableEdgeToEdge` derives it from light/dark mode — and a light theme asks for dark icons. That
 * is a statement about the app's own background, not about a camera viewfinder, and dark icons over
 * an arbitrary scene are simply wrong. It is also inconsistent with this camera's own contrast rule
 * (`CameraOverlay.kt`): every overlay glyph is white with a black outline.
 *
 * *The band itself is not ours and is not going away.* The revealed bar carries a grey darkening on
 * the owner's S26 Ultra that the navigation bar does not, and the owner has since confirmed that
 * **Samsung's own camera shows the same darkening on the same phone**. So it is One UI's treatment
 * of a transiently revealed bar, and the reference app does not avoid it either. The platform
 * agrees: the two flags that draw it, `APPEARANCE_OPAQUE_STATUS_BARS` and
 * `APPEARANCE_SEMI_TRANSPARENT_STATUS_BARS`, are both `@hide` and system-set; AOSP adds the latter
 * only over a letterboxed activity, which this is not; and for a target of API 35+
 * `Window.setStatusBarColor` is documented as forced transparent and unchangeable. **There is no
 * transparency value an app can raise.** On the API 36 emulator the revealed bar is already fully
 * transparent, carrying neither flag, so the band cannot be reproduced or tested here at all.
 *
 * White icons were tried against that band first and are kept on the first reason alone. Reverting
 * them is one line if the owner prefers the theme's appearance over the viewfinder.
 *
 * ## What is and is not tested
 *
 * [StatusBarHider] is the seam. The production one ([SystemStatusBarHider]) is a thin call into
 * `WindowInsetsControllerCompat`, whose effect Robolectric cannot observe; what a test *can*
 * establish is that the hider is asked once with the Activity's window, and asked to restore when
 * the camera leaves. Whether the bar actually disappears, whether it returns after each exit, and
 * whether the swipe reveals it are emulator and device items, listed as such.
 */
internal interface StatusBarHider {
    fun hide(window: Window, view: View)

    /** Puts the bar back. Called when the camera leaves composition — see this file's own doc comment on why that covers every exit. */
    fun show(window: Window, view: View)
}

internal val SystemStatusBarHider = object : StatusBarHider {
    // Nothing here about the display cut-out, and that is measured rather than assumed: with the
    // camera open, `dumpsys window` on the API 36 emulator reports this window at [0,0][1080,2400]
    // with layoutInDisplayCutoutMode=always — the full display, cut-out band included — with no
    // attribute set here. The Activity calls `enableEdgeToEdge()`, and the strip's own band is what
    // keeps its controls out of the cut-out (CameraBands.kt). A first cut set the mode explicitly,
    // on a misreading of the camera-in-use privacy indicator as the Activity showing through; the
    // counterfactual build without it reported the same frame, so it was removed.
    override fun hide(window: Window, view: View) {
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun show(window: Window, view: View) {
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.statusBars())
    }
}

/**
 * Hides the status bar on the Activity's window while the camera is composed, and shows it again
 * when the camera leaves.
 *
 * **One-shot, deliberately.** Re-asserting the hide on every recomposition would cancel a transient
 * reveal: the user swipes the bar down, the session's next state change recomposes, and the bar
 * would vanish under their finger. The icons' colour is a separate question and is *not* set here —
 * see [systemBarIconsAreWhite] for why the camera cannot win that one and who decides it instead.
 */
@Composable
internal fun HideStatusBarWhileCameraIsOpen(hider: StatusBarHider) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window
    DisposableEffect(window, hider) {
        if (window == null) {
            Log.w(TAG, "No Activity behind this context; the status bar cannot be hidden here and was not.")
            return@DisposableEffect onDispose {}
        }
        hider.hide(window, view)
        onDispose { hider.show(window, view) }
    }
}

/**
 * Whether the system bars should carry **white** icons: when the app's own theme is dark, or
 * whenever the camera is open, because the camera's surface is a viewfinder and never the app's
 * background.
 *
 * ## Why this is decided here and applied by `MainActivity`, rather than by the camera
 *
 * It was first built as a claim the camera made on the window, beside hiding the bar, and
 * **measured not to hold**: the camera set the appearance and it was back a second later. The probe
 * logged `useWhiteIcons: previous=true readBack=false` at the moment of the call and
 * `poll: lightStatusBars=true` 1.5 s afterwards, on every poll. `MainActivity` calls
 * `enableEdgeToEdge` from a `SideEffect`, which re-runs on **every** recomposition and re-derives
 * the icon appearance from the app's theme — and it recomposes for reasons the camera's own subtree
 * does not, so re-asserting from the camera is a race the camera loses.
 *
 * The fix is not to assert harder but to have one place decide. `MainActivity` already owns the
 * window's system-bar appearance and already collects the camera's open state, so it reads this
 * function and there is nothing left to fight over. Pure, and tested as such.
 *
 * **What this does not do** is change the grey band on a revealed bar. That band is One UI's — the
 * owner confirmed Samsung's own camera shows the same darkening on the same phone — and no app-side
 * API reaches it; see this file's own doc comment for the platform reading.
 */
internal fun systemBarIconsAreWhite(appThemeIsDark: Boolean, cameraIsOpen: Boolean): Boolean =
    appThemeIsDark || cameraIsOpen

/**
 * Asks the platform to rotate the Activity's window seamlessly while the camera is composed: when
 * the display turns, the window is re-laid out in the new rotation and the next frame is the new
 * layout, with no rotation animation and no fade. Restored on the way out, so only the camera
 * rotates this way.
 *
 * ## Why this window, and why not the manifest
 *
 * The platform picks a rotation's animation from the **task's main window**:
 * `Transition.getTaskRotationAnimation` reads `ActivityRecord.findMainWindow`, which returns only
 * `TYPE_BASE_APPLICATION` — this window — and then requires that same window to be the display
 * policy's top fullscreen opaque window. Both hold here and are measured: with the camera open,
 * `dumpsys window` names this `ty=BASE_APPLICATION` window as `mTopFullscreenOpaqueWindowState` and
 * as `mFocusedWindow`, and there is no second window in the dump. The Shell then logged
 * `nav bar allows seamless` and the display change carried rotation-animation `3`
 * (`ROTATION_ANIMATION_SEAMLESS`), where the same probe against the old `Dialog` logged
 * `task N isn't requesting seamless` and animation `0` on every turn.
 *
 * **The manifest attribute `android:rotationAnimation` must stay undeclared**, and it is (grep:
 * zero hits in `AndroidManifest.xml`, 2026-09-19). It is read *first*, through
 * `ActivityInfo.rotationAnimation` → `ActivityRecord.mRotationAnimationHint`, and short-circuits
 * before `mAttrs.rotationAnimation` is consulted — so declaring it would both defeat this runtime
 * request and apply to every rotation anywhere in the app rather than only while the camera is
 * open. It will look like an improvement to someone later. It is not.
 *
 * ## When the platform still animates
 *
 * It falls back when the window is mid-animation, when a picture-in-picture task or a system alert
 * window is present, and — on a device whose navigation bar cannot change sides and whose build
 * does not set `config_allowSeamlessRotationDespiteNavBarMoving` (gesture navigation does on stock
 * builds) — on any turn to or from reverse portrait. What plays then, under shell transitions, is
 * the ordinary rotate animation, not the crossfade the constant's own documentation promises; that
 * promise describes the legacy path. Which of these a given phone hits is a device item.
 */
@Composable
internal fun RequestSeamlessRotation() {
    val window = LocalView.current.context.findActivity()?.window
    DisposableEffect(window) {
        if (window == null) {
            Log.w(TAG, "No Activity behind this context; seamless rotation cannot be requested here and was not.")
            return@DisposableEffect onDispose {}
        }
        val previous = window.attributes.rotationAnimation
        window.attributes = window.attributes.apply { rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS }
        onDispose {
            window.attributes = window.attributes.apply { rotationAnimation = previous }
        }
    }
}

private const val TAG = "CameraWindowChrome"
