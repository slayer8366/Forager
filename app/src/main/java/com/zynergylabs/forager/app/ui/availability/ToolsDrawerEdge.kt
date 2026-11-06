package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapChromeOutline
import com.zynergylabs.forager.app.ui.map.mapChromeOutlinePath

/*
 * Dispatch 2026-11-05-09 (RECORD -2180; the owner, with a screenshot of the open Tools drawer over the map: "Give the tools drawer
 * an outline also please", then "Add it to 495"): the Tools drawer's edge that faces the map.
 */

/**
 * The Tools drawer sheet's shape, built once in the drawer host and given both to the sheet and, for tests, to its node
 * (`mapChromeShape`), so a test reads the corners the sheet is really filled to.
 *
 * It is exactly what the sheet had before it was passed in: material3 1.5.0-alpha26's `DrawerDefaults.shape` for a modal drawer is
 * `ShapeKeyTokens.CornerLargeEnd`, which is the theme's `shapes.large` with its top-start and bottom-start corners set to
 * `CornerSize(0.dp)` (read from the bytecode: `ShapesKt.fromToken`, `end`, `ShapeTokens.CornerValueNone`). ToolsDrawerShapeTest
 * holds it equal to `DrawerDefaults.shape` under the app's theme. Under ForagerTheme `large` is 20 dp (Shapes.kt), so the drawer's
 * two end corners are 20 dp, not the 16 dp of the search pill and the strip.
 *
 * A [RoundedCornerShape], built from `large`'s own corner sizes rather than copied from it, so its outline is always a rounded
 * rectangle (or a plain one), which a line can follow. Copying would keep whatever kind of shape `large` is; ForagerShapes' and
 * Material's stock `large` are both rounded, so the two are equal either way.
 */
@Composable
@ReadOnlyComposable
internal fun toolsDrawerShape(): RoundedCornerShape {
    val large = MaterialTheme.shapes.large
    return RoundedCornerShape(
        topStart = CornerSize(0.dp),
        topEnd = large.topEnd,
        bottomEnd = large.bottomEnd,
        bottomStart = CornerSize(0.dp),
    )
}

/**
 * The map-chrome hairline [stroke] ([mapChromeOutline]: 1 dp, Bark at 0.4 by day, white at 0.4 at night) along the drawer sheet's
 * edge that faces the map and round the two rounded corners on that edge, over the sheet's fill and content, as one open path
 * ([mapChromeOutlinePath], the same one the bars, the rail and the strip draw). The sheet's other three sides lie on the screen's
 * own edges and carry none (RECORD -967: "A side on the screen's own edge carries none").
 *
 * - **Which edge:** the sheet's end edge, read from its own layout direction. The drawer is laid out in the direction whose start
 *   is the side it opens from (toolsDrawerDirection), so its end faces the map: left to right in portrait, its right edge; right to
 *   left in landscape, where it opens from the rail's side at both rotations, its left edge.
 * - **Its corners:** from [shape]'s outline at the sheet's own size and direction, the outline the sheet is filled and clipped to,
 *   so the line follows the sheet's curve. A [RoundedCornerShape]'s outline is a rounded rectangle, or a plain one when every corner
 *   is square, which reads here as radius 0.
 * - **Its ends:** each corner's curve runs on to the screen's top or bottom edge, where the sheet ends, under the status bar and
 *   the navigation bar on a phone (D1: "following the sheet's rounded edge and its corners").
 *
 * Draw only: it measures nothing and takes no touch, so no size or touch area changes.
 */
internal fun Modifier.toolsDrawerEdge(shape: RoundedCornerShape, stroke: BorderStroke): Modifier = drawWithContent {
    drawContent()
    val corners = (shape.createOutline(size, layoutDirection, this) as? Outline.Rounded)?.roundRect
    val endIsRight = layoutDirection == LayoutDirection.Ltr
    val strokePx = stroke.width.toPx()
    val path = mapChromeOutlinePath(
        size = size,
        strokePx = strokePx,
        topLeft = corners?.topLeftCornerRadius?.x ?: 0f,
        topRight = corners?.topRightCornerRadius?.x ?: 0f,
        bottomRight = corners?.bottomRightCornerRadius?.x ?: 0f,
        bottomLeft = corners?.bottomLeftCornerRadius?.x ?: 0f,
        sides = MapChromeOutlineSides(left = !endIsRight, top = false, right = endIsRight, bottom = false),
    )
    drawPath(path, brush = stroke.brush, style = Stroke(width = strokePx))
}
