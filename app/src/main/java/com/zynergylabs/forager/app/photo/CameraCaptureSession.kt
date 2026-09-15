package com.zynergylabs.forager.app.photo

import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * What the in-app camera screen needs from a camera, and nothing else — the interface this project
 * owns, per CLAUDE.md's rule that an external integration is wrapped rather than depended on
 * directly. [CameraXCaptureSession] is the only implementation that touches `androidx.camera`.
 *
 * ## Why this exists rather than the screen calling CameraX
 *
 * Not tidiness. **CameraX cannot run in this project's test harness at all** — Robolectric has no
 * camera provider, no camera2 HAL and no surface to draw a viewfinder on — so a screen that called
 * CameraX directly would have no testable behaviour left: not the shutter, not the running count,
 * not what happens when a capture fails. Behind this interface, a fake session makes all of that
 * ordinary Robolectric work, and what stays device-only is exactly the part that genuinely is
 * device-only: whether a real camera opens and produces a real JPEG.
 *
 * That split is the point. It is worth being plain that it does not make the feature verified —
 * see [com.zynergylabs.forager.app.ui.log.InAppCameraDialog] and the completion report for what a
 * green suite here does and does not establish.
 */
internal interface CameraCaptureSession {

    /** Where the camera is in opening. The screen renders from this; it never infers readiness from a null check. */
    val state: CameraSessionState

    /**
     * Starts the camera: fetches the provider, binds the use cases to [lifecycleOwner], and moves
     * [state] from `Opening` to `Ready`, or to `Unavailable` with a reason. Called by the screen
     * when it enters composition, **not** by the viewfinder.
     *
     * That distinction is the fix for a deadlock found on device on 2026-09-15: the screen composes
     * its viewfinder only once `Ready`, and the version that shipped did the opening *inside* the
     * viewfinder, so the screen waited for `Ready` before composing the only thing that could
     * produce it. Opening is the screen's call; the viewfinder only draws.
     */
    fun open(lifecycleOwner: LifecycleOwner)

    /**
     * Releases whatever [open] acquired: unbinds, stops listening for orientation, drops the
     * viewfinder surface. Safe to call when [open] never completed, and a second call is a no-op.
     * Called by the screen when it leaves composition.
     */
    fun close()

    /**
     * Writes one photo to [destination], suspending until it is on disk or has failed.
     *
     * Returns [Result] rather than throwing, and rather than a `Boolean`: a failed capture must
     * reach the user as a failure carrying its cause, not as a silently skipped photo. CLAUDE.md,
     * on partial results never being presented as success.
     */
    suspend fun capture(destination: File): Result<Unit>
}

/**
 * Three states, not a nullable camera. [Unavailable] carries its own reason because "the camera
 * didn't open" is the one the user can sometimes act on (no camera on the device, another app
 * holding it, the permission withdrawn mid-session) and a bare null cannot say which.
 */
internal sealed interface CameraSessionState {
    /** Bound and ready to take a photo. */
    data object Ready : CameraSessionState

    /** The provider is still starting. Normal, and usually brief. */
    data object Opening : CameraSessionState

    /** No camera to bind, with why. [reason] is shown to the user, so it is written for one. */
    data class Unavailable(val reason: String) : CameraSessionState
}
