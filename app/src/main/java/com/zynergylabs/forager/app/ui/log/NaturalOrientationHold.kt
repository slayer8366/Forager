package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * The camera never appears to rotate (RECORD -981; the owner: "The camera is to never, ever, rotate like this. This is a
 * cardinal sin"), including where the platform turns its window anyway.
 *
 * ## Why this exists
 *
 * The camera asks for a portrait window ([RequestWindowOrientation]). With this app's `targetSdk` 37, Android 17 ignores
 * that request on displays whose smallest width is greater than 600 dp, with no opt-out (developer.android.com, "Restrictions
 * on orientation and resizability ignored", read 2026-10-13). There the window turns with the tablet, and the camera would
 * switch to its landscape arrangement, which counts as rotating. So the camera holds its own layout.
 *
 * ## What it does
 *
 * When the window is full screen and the display is turned (`ROTATION_90`, `ROTATION_180` or `ROTATION_270`), the content
 * is measured in the **device's natural frame** (width and height swapped after a quarter turn), centred, and turned back
 * by the display's rotation with a `graphicsLayer`. Inside, the content is told the natural frame's shape and a display
 * rotation of `ROTATION_0`, so the arrangement is the natural one and the icons turn by the sensor alone (RECORD -982:
 * "rotate based on accelerometer position"). The natural frame is whatever the device's is: portrait on a phone or most
 * tablets, landscape on a landscape-natural tablet (the owner: "Option A", RECORD -985).
 *
 * - **Touches** need no mapping of our own: Compose hit-testing applies a layer's inverse transform, so a finger reaches the
 *   control drawn under it (`InAppCameraLargeScreenHoldTest`, real touches across the shutter and every chip).
 * - **Insets** are the window's, so they are turned into the natural frame ([windowEdgesInNaturalFrame]) and handed to the
 *   bands through [LocalCameraBandInsets]; `CameraBand` pads by those instead of the window's sides while held.
 * - **The viewfinder** is told through [LocalNaturalOrientationHold], so the real one can switch to a `TextureView` that
 *   turns with the layer and ask CameraX for a natural-frame picture (`CameraXCaptureSession.Viewfinder`).
 *
 * **Not in split screen or a freeform window** (RECORD -985): there the content lays out to the window's own shape, as before.
 * **At `ROTATION_0` it does nothing**: the same tree with no layer and the window's own values, so phones, whose portrait
 * request is honoured, are unchanged. The tree has the same shape held or not, so turning into or out of the hold does not
 * recreate the camera's content.
 */
@Composable
internal fun HoldNaturalOrientation(
    windowIsLandscape: Boolean,
    displayRotation: Int,
    fullScreen: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(windowIsLandscape: Boolean, displayRotation: Int) -> Unit,
) {
    val turns = naturalHoldQuarterTurns(displayRotation, fullScreen)
    val held = turns != 0
    val density = LocalDensity.current
    val windowEdges = WindowInsets.displayCutout.union(WindowInsets.navigationBars)
    val bandInsets = if (!held) null else with(density) {
        windowEdgesInNaturalFrame(
            EdgeInsets(
                left = windowEdges.getLeft(density, LayoutDirection.Ltr).toDp(),
                top = windowEdges.getTop(density).toDp(),
                right = windowEdges.getRight(density, LayoutDirection.Ltr).toDp(),
                bottom = windowEdges.getBottom(density).toDp(),
            ),
            turns,
        )
    }
    val degrees = -90f * turns
    Layout(
        modifier = modifier,
        content = {
            Box(if (held) Modifier.graphicsLayer { rotationZ = degrees } else Modifier) {
                CompositionLocalProvider(LocalNaturalOrientationHold provides held, LocalCameraBandInsets provides bandInsets) {
                    content(
                        if (held) naturalFrameIsLandscape(windowIsLandscape, turns) else windowIsLandscape,
                        if (held) Surface.ROTATION_0 else displayRotation,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val swap = turns % 2 == 1
        val contentWidth = if (swap) height else width
        val contentHeight = if (swap) width else height
        val placeable = measurables.single().measure(Constraints.fixed(contentWidth, contentHeight))
        layout(width, height) { placeable.place((width - contentWidth) / 2, (height - contentHeight) / 2) }
    }
}

/** Insets in dp, one per absolute edge. */
internal data class EdgeInsets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)

/** Quarter turns the hold applies: the display's rotation in a full-screen window, none otherwise. */
internal fun naturalHoldQuarterTurns(displayRotation: Int, fullScreen: Boolean): Int =
    if (!fullScreen) 0 else when (displayRotation) {
        Surface.ROTATION_90 -> 1
        Surface.ROTATION_180 -> 2
        Surface.ROTATION_270 -> 3
        else -> 0
    }

/**
 * Whether the viewfinder draws in the natural frame (a `TextureView`, `Preview.targetRotation` = `ROTATION_0`), which is not
 * the same question as "held right now" (dispatch 2026-10-17-08; RECORD -1214, -1215).
 *
 * Until that dispatch the viewfinder keyed on [LocalNaturalOrientationHold] alone. That is false at `ROTATION_0` and true at
 * the other three, so every turn between portrait and landscape on a tablet swapped the `PreviewView` between a
 * `SurfaceView` and a `TextureView`. Each swap asked CameraX for a new surface and the camera rebuilt its session: the
 * restart and black flash in the owner's tablet log, on each of the 8 turns that touched `ROTATION_0` and on none of the 7
 * that stayed among 90, 180 and 270.
 *
 * So the question is now "could this window be held": a full-screen window on a large screen (smallest width 600 dp and up,
 * the platform's large-screen line, where the portrait request is ignored). That answer is the same at all four rotations,
 * so a turn re-requests nothing. Anything already held keeps the natural frame, as before. Phones (under 600 dp, never
 * held) and split screen are unchanged. At `ROTATION_0`, with no layer, the natural-frame setup should draw what the
 * display's own did (the `TextureView`'s correction is `-target`, 0, and the display is at 0); that is device-only.
 */
internal fun viewfinderInNaturalFrame(held: Boolean, fullScreen: Boolean, smallestScreenWidthDp: Int): Boolean =
    held || (fullScreen && smallestScreenWidthDp >= LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP)

/** The platform's large-screen line: a smallest width of 600 dp and up, where the portrait request is ignored. */
internal const val LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP = 600

/** The natural frame's shape: the window's after an even number of quarter turns, the other after an odd one. */
internal fun naturalFrameIsLandscape(windowIsLandscape: Boolean, quarterTurns: Int): Boolean =
    if (quarterTurns % 2 == 1) !windowIsLandscape else windowIsLandscape

/**
 * The window's edge insets as edges of the device's natural frame. After one quarter turn (`ROTATION_90`) the natural top
 * is the window's left, the natural right its top, the natural bottom its right and the natural left its bottom; each
 * further quarter turn moves them on by one. Pure, tested (`NaturalOrientationHoldTest`).
 */
internal fun windowEdgesInNaturalFrame(window: EdgeInsets, quarterTurns: Int): EdgeInsets = when (quarterTurns.mod(4)) {
    1 -> EdgeInsets(left = window.bottom, top = window.left, right = window.top, bottom = window.right)
    2 -> EdgeInsets(left = window.right, top = window.bottom, right = window.left, bottom = window.top)
    3 -> EdgeInsets(left = window.top, top = window.right, right = window.bottom, bottom = window.left)
    else -> window
}

/** Whether the camera's content is being held in the natural frame; the viewfinder reads it to choose how it draws. */
internal val LocalNaturalOrientationHold = staticCompositionLocalOf { false }

/** While held, the window's insets in the natural frame, which `CameraBand` pads by; `null` otherwise, for the window's own. */
internal val LocalCameraBandInsets = compositionLocalOf<EdgeInsets?> { null }

/**
 * Whether the Activity's window is full screen: not split screen, not a freeform window. Reads the configuration so it is
 * re-read when the window enters or leaves multi-window, which changes it. No Activity (a preview) reads as full screen.
 */
@Composable
internal fun currentWindowIsFullScreen(): Boolean {
    LocalConfiguration.current
    val activity = LocalContext.current.findActivity() ?: return true
    return !activity.isInMultiWindowMode
}
