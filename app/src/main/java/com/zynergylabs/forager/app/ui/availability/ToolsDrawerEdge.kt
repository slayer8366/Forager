package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

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
