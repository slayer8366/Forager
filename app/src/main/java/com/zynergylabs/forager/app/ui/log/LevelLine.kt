package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.LevelProvider
import kotlin.math.abs
import kotlin.math.round

/**
 * The level: a horizon line through the preview's centre that stays parallel to the real horizon as
 * the phone tilts, turning to [OverlayLevelFill] when the phone is level. Shown only at
 * [GridMode.GridLevel], and then **always**, not only near level (decision record B7b, a departure
 * from all four reference apps: a forager squaring up a scale card wants to see how far off they
 * are, not only when they arrive).
 *
 * - **The sensor is listened to only while the line is shown.** [LevelProvider.roll] is collected
 *   inside this composable after the mode check, so at Off or Grid nothing is collected, and the
 *   real provider registers nothing; when the camera closes the collection ends with it.
 * - **The angle on screen** is [horizonAngleOnScreen]: opposite to the phone's roll, less the
 *   window's own turn, so the line is flat whenever the phone is level in the window's frame.
 * - **Level** is [isLevel]: within [LEVEL_SNAP_DEGREES] of the nearest quarter turn, so a phone
 *   held level in landscape is level whether or not the window followed it.
 * - **The snapped state is a colour**, not a thickness: a change of fill is what the overlay rule
 *   says carries meaning (`CameraOverlay.kt`), and a thicker line would move its own edges while
 *   the user is trying to line something up against them.
 * - **No reading, no line**: a `null` roll (no sensor, or none yet) draws nothing rather than a flat
 *   line that would look level.
 *
 * Drawn over the preview and under the bands, like the grid. The line is half the preview's
 * shorter side long: long enough to judge against, short enough to stay clear of the strip and
 * the shutter in either hold.
 */
@Composable
internal fun LevelLine(mode: GridMode, levelProvider: LevelProvider, displayRotation: Int, modifier: Modifier = Modifier) {
    if (mode != GridMode.GridLevel) return
    val roll by levelProvider.roll.collectAsState(initial = null)
    val reading = roll ?: return
    val angle = horizonAngleOnScreen(reading, displayRotation)
    val snapped = isLevel(reading)
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(minOf(maxWidth, maxHeight) / 2)
                .height(LEVEL_LINE_WIDTH + LEVEL_LINE_OUTLINE * 2)
                .graphicsLayer { rotationZ = angle }
                .background(OverlayOutline)
                .semantics { levelLineAngle = angle; levelLineSnapped = snapped }
                .testTag(CAMERA_LEVEL_TAG),
        ) {
            Box(Modifier.fillMaxSize().padding(LEVEL_LINE_OUTLINE).background(if (snapped) OverlayLevelFill else OverlayFill))
        }
    }
}

/**
 * The line's angle on screen, clockwise-positive as Compose's `rotationZ`, for a phone at [roll]
 * (see `LevelProvider.roll`) in a window at [displayRotation]: opposite to the roll, since the
 * horizon appears to turn the other way, less the window's own turn ([clockwiseDegrees], the
 * mapping `rotateWithDevice` uses), folded into `(-180, 180]`.
 */
internal fun horizonAngleOnScreen(roll: Float, displayRotation: Int): Float {
    var degrees = (-roll - clockwiseDegrees(displayRotation)) % 360f
    if (degrees > 180f) degrees -= 360f
    if (degrees <= -180f) degrees += 360f
    return degrees
}

/** Within [LEVEL_SNAP_DEGREES] of the nearest quarter turn: level in any of the four holds. */
internal fun isLevel(roll: Float): Boolean = abs(roll - 90f * round(roll / 90f)) <= LEVEL_SNAP_DEGREES

internal val LEVEL_LINE_WIDTH: Dp = 2.dp
internal val LEVEL_LINE_OUTLINE: Dp = 1.dp

/** The dispatch's "within 1 degree". Inclusive. */
internal const val LEVEL_SNAP_DEGREES = 1f
internal const val CAMERA_LEVEL_TAG = "in-app-camera-level"

/** The line's angle on screen and whether it is in the level state, in semantics for tests: a turned line's bounds and its colour are not readable there. */
internal val LevelLineAngle = SemanticsPropertyKey<Float>("LevelLineAngle")
internal var SemanticsPropertyReceiver.levelLineAngle by LevelLineAngle
internal val LevelLineSnapped = SemanticsPropertyKey<Boolean>("LevelLineSnapped")
internal var SemanticsPropertyReceiver.levelLineSnapped by LevelLineSnapped
