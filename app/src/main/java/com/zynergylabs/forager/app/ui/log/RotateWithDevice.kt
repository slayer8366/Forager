package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import kotlin.math.max

/**
 * Rotates a control in place so it reads upright to the person holding the phone whatever the
 * window is doing ([RequestWindowOrientation]). The one shared piece for every control that
 * turns with the device — the camera's Done row and photo count today, the torch icon next; the
 * tap-to-focus indicator, which must not turn, simply does not use it. Owner's instruction: a
 * single shared modifier from the start, not three call sites retrofitted later.
 *
 * ## What "in place" means here, and how it is guaranteed
 *
 * Two things, both by construction. The rotation is a `graphicsLayer` about the control's own
 * centre, so its centre never moves and its touch target turns with its pixels. And the control's
 * footprint is made square, the larger of its two sides, with the control centred in it, so a
 * wide control turned 90° stays inside the space it was given instead of poking into a
 * neighbour or off the screen edge. That footprint is the one visible cost: a 90×40 button
 * occupies 90×90 whether or not it is turned, and a stack of such controls sits a little further
 * apart than it did.
 *
 * ## The angle, and the way there
 *
 * The angle is **sensor rotation minus display rotation**. The sensor term is the same surface
 * rotation the capture session's orientation listener already snaps to
 * ([CameraCaptureSession.deviceRotation]), so the controls and the photo's own rotation tag agree
 * by construction. The display term is the window's own rotation, and it is what makes the
 * expression right whether or not the window moved: with the setting on the window is forced
 * portrait and the sensor term is pinned, so nothing turns; with it off the window follows the
 * device ([RequestWindowOrientation], since 2026-09-19 — before that it was locked and this branch
 * was written for the large screens where the platform ignores a lock), the two terms cancel in
 * every settled hold, and the controls stay put because the window carried them. Between the
 * device turning and the window catching up, the difference is what the controls turn by. No
 * branch, no capability check, no large-screen path; the owner's design. [uprightRotationDegrees] is the
 * mapping, pure and unit-tested at both display rotations. The animation takes the short way
 * round: 0° to 270° is a quarter turn back, not three quarters forward, which
 * [shortestRotationTarget] decides, also pure. `null` (no reading yet, or no sensor) means
 * upright, whatever the window is doing, which is where the controls already are.
 */
@Composable
internal fun Modifier.rotateWithDevice(surfaceRotation: Int?, displayRotation: Int = currentDisplayRotation()): Modifier {
    val target = uprightRotationDegrees(surfaceRotation, displayRotation)
    var unwrapped by remember { mutableFloatStateOf(target) }
    LaunchedEffect(target) { unwrapped = shortestRotationTarget(unwrapped, target) }
    val degrees by animateFloatAsState(targetValue = unwrapped, label = "rotateWithDevice")
    return this
        .squareFootprint()
        .graphicsLayer { rotationZ = degrees }
        .semantics { rotateWithDeviceTarget = target }
}

/**
 * The angle, in degrees, that [rotateWithDevice] is turning its control to: the settled target,
 * not the animation's current frame. Exposed in semantics for tests, because a **square** control
 * (the flash chip, and any `IconButton`) has the same bounds turned or not, so a test measuring
 * extents or positions passes whether or not it turned. Only the angle itself can fail there.
 */
internal val RotateWithDeviceTarget = SemanticsPropertyKey<Float>("RotateWithDeviceTarget")
internal var SemanticsPropertyReceiver.rotateWithDeviceTarget by RotateWithDeviceTarget

/**
 * Degrees, clockwise-positive as Compose's `rotationZ`, that a control must turn to read upright
 * when the device is held at [surfaceRotation] and the window is at [displayRotation]: the
 * device's turn minus the window's, folded into a half turn either way.
 *
 * `Surface.ROTATION_90` is what `UseCase.snapToSurfaceRotation` returns for the device turned a
 * quarter counter-clockwise (its right side up), so with the window at `ROTATION_0` the control
 * turns a quarter clockwise to meet it; `ROTATION_270` is the opposite; `ROTATION_180` is upside
 * down either way. A window that has itself turned to `ROTATION_90` has already carried the
 * control a quarter clockwise, so the same device reading then needs no turn at all. `null`
 * means no reading, and no turn, whatever the window is at.
 */
internal fun uprightRotationDegrees(surfaceRotation: Int?, displayRotation: Int = Surface.ROTATION_0): Float {
    if (surfaceRotation == null) return 0f
    return shortestRotationTarget(current = 0f, targetDegrees = clockwiseDegrees(surfaceRotation) - clockwiseDegrees(displayRotation))
}

/** A `Surface.ROTATION_*` value as the clockwise turn a control needs to meet it; anything unrecognised is upright. */
internal fun clockwiseDegrees(surfaceRotation: Int): Float = when (surfaceRotation) {
    Surface.ROTATION_90 -> 90f
    Surface.ROTATION_180 -> 180f
    Surface.ROTATION_270 -> -90f
    else -> 0f
}

/**
 * The window's current rotation. Read through `LocalView`'s display, and with `LocalConfiguration`
 * read alongside so that a window that does turn (an ignored lock) recomposes this; a view not yet
 * attached, or one with no display, reads as `ROTATION_0`.
 *
 * **Do not call this from inside a `Dialog`'s content.** Its invalidation rests on
 * `LocalConfiguration.current`, and inside a Dialog's content that local does not change when the
 * window turns — measured on 2026-09-18 with a probe that logged `LocalConfiguration.current.orientation`
 * still `LANDSCAPE` after the window was portrait. The value returned is then whatever the display
 * reported the last time something *else* recomposed the caller: plausible, and stale. The
 * placeholder label stayed a quarter turned after a setting-on landscape open for exactly this
 * reason while the count beside it, recomposed by an unrelated state change, read fresh
 * (`docs/audits/2026-09-18-placeholder-label-stale-display-term.md`). A caller inside a dialog must
 * be **passed** the value read at the dialog level, outside the `Dialog {}` block, where the local
 * does invalidate — which is what `InAppCameraDialog` now does for every glyph. The alternative,
 * making this function observe the window itself, was priced and not taken (owner, 2026-09-18).
 *
 * `internal` rather than private because [cameraArrangement] needs the same value at open, to put
 * the shutter on the device's port edge rather than on a fixed screen side — the two landscapes
 * are not interchangeable, which the 2026-09-17 device-check run found the hard way. One reader of
 * the window's rotation, not two spellings of it.
 */
@Composable
internal fun currentDisplayRotation(): Int {
    LocalConfiguration.current
    return LocalView.current.display?.rotation ?: Surface.ROTATION_0
}

/**
 * The value to animate to so that reaching [targetDegrees] (mod 360) from [current] is the
 * shorter turn: the result differs from [current] by at most 180°, a half turn going clockwise.
 */
internal fun shortestRotationTarget(current: Float, targetDegrees: Float): Float {
    var delta = (targetDegrees - current) % 360f
    if (delta > 180f) delta -= 360f
    if (delta <= -180f) delta += 360f
    return current + delta
}

/** A square footprint, the larger of the content's two sides, with the content centred in it — see [rotateWithDevice]. */
private fun Modifier.squareFootprint(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val side = max(placeable.width, placeable.height)
        .coerceIn(max(constraints.minWidth, constraints.minHeight), minOf(constraints.maxWidth, constraints.maxHeight))
    layout(side, side) {
        placeable.place((side - placeable.width) / 2, (side - placeable.height) / 2)
    }
}
