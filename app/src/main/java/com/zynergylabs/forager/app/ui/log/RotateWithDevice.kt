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
import kotlin.math.max

/**
 * Rotates a control in place so it reads upright to the person holding the phone while the
 * window stays locked ([LockWindowOrientation]). The one shared piece for every control that
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
 * The angle comes from the same surface rotation the capture session's orientation listener
 * already snaps to ([CameraCaptureSession.deviceRotation]), so the controls and the photo's own
 * rotation tag agree by construction. [uprightRotationDegrees] is the mapping; it is pure and
 * unit-tested. The animation takes the short way round: 0° to 270° is a quarter turn back, not
 * three quarters forward, which [shortestRotationTarget] decides, also pure. `null` (no reading
 * yet, or no sensor) means upright, which is where the controls already are.
 */
@Composable
internal fun Modifier.rotateWithDevice(surfaceRotation: Int?): Modifier {
    val target = uprightRotationDegrees(surfaceRotation)
    var unwrapped by remember { mutableFloatStateOf(target) }
    LaunchedEffect(target) { unwrapped = shortestRotationTarget(unwrapped, target) }
    val degrees by animateFloatAsState(targetValue = unwrapped, label = "rotateWithDevice")
    return this
        .squareFootprint()
        .graphicsLayer { rotationZ = degrees }
}

/**
 * Degrees, clockwise-positive as Compose's `rotationZ`, that a control must turn to read upright
 * when the device is held at [surfaceRotation] and the window has not rotated with it.
 *
 * `Surface.ROTATION_90` is what `UseCase.snapToSurfaceRotation` returns for the device turned a
 * quarter counter-clockwise (its right side up), so the control turns a quarter clockwise to
 * meet it; `ROTATION_270` is the opposite; `ROTATION_180` is upside down either way.
 */
internal fun uprightRotationDegrees(surfaceRotation: Int?): Float = when (surfaceRotation) {
    Surface.ROTATION_90 -> 90f
    Surface.ROTATION_180 -> 180f
    Surface.ROTATION_270 -> -90f
    else -> 0f
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
