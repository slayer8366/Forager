package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.GridMode

/**
 * The 3 by 3 grid over the preview (decision record B7): two vertical and two horizontal lines at
 * the thirds of whatever it is given to fill, drawn at [GridMode.Grid] and [GridMode.GridLevel],
 * nothing at [GridMode.Off].
 *
 * - **Over the preview, under the bands**: `InAppCameraDialog` composes it after the viewfinder and
 *   before the strip and the shutter band, so every control draws above it.
 * - **Not rotated with the glyphs** (B7c). It divides the preview into thirds whichever way the
 *   phone is held, as in all four reference apps.
 * - **The overlay colour rule** (`CameraOverlay.kt`): a white line with a black outline, so it reads
 *   over a pale cap and a dark litter layer alike. [GRID_LINE_WIDTH] of white inside
 *   [GRID_LINE_OUTLINE] of black on each side; thinner than a glyph's outline, since a grid that
 *   competes with the subject is the wrong grid.
 * - **Touches pass through.** Each line is a plain box with a background and no pointer input, so
 *   nothing here is a hit-test target (CLAUDE.md, the `Surface` pitfall read the right way round).
 *
 * Each line is its own layout node, rather than strokes on one canvas, so a test can read where
 * each one is.
 */
@Composable
internal fun GridOverlay(mode: GridMode, modifier: Modifier = Modifier) {
    if (mode == GridMode.Off) return
    BoxWithConstraints(modifier.fillMaxSize().testTag(CAMERA_GRID_TAG)) {
        val thickness = GRID_LINE_WIDTH + GRID_LINE_OUTLINE * 2
        for (i in 1..2) {
            GridLine(
                Modifier
                    .offset(x = maxWidth * i / 3 - thickness / 2)
                    .width(thickness)
                    .fillMaxHeight()
                    .testTag(gridLineTag('v', i)),
            )
            GridLine(
                Modifier
                    .offset(y = maxHeight * i / 3 - thickness / 2)
                    .height(thickness)
                    .fillMaxWidth()
                    .testTag(gridLineTag('h', i)),
            )
        }
    }
}

/** One line: the outline colour across its full thickness, the fill inset by the outline. */
@Composable
private fun GridLine(modifier: Modifier) {
    Box(modifier.background(OverlayOutline)) {
        Box(Modifier.fillMaxSize().padding(GRID_LINE_OUTLINE).background(OverlayFill))
    }
}

internal val GRID_LINE_WIDTH: Dp = 1.dp
internal val GRID_LINE_OUTLINE: Dp = 0.5.dp
internal const val CAMERA_GRID_TAG = "in-app-camera-grid"
internal fun gridLineTag(axis: Char, index: Int) = "in-app-camera-grid-$axis$index"
