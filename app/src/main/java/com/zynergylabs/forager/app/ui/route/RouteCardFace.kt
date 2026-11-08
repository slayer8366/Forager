package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/*
 * The route card's face: its 80% fill and its outline, drawn here rather than by its Surface, because the card draws past its own
 * bounds where it rests on the landscape navigation card (dispatch 2026-11-06-05, Amendment 6, A1, RECORD -2187). Kept out of
 * RouteCard.kt, which is past the 400-line mark (.claude/skills/modular-code/SKILL.md).
 *
 * A1, the owner: "The outline doesn't cover the entire edge, it stops short of completion. Also there is a visible overlap between the
 * two cards.", the corners settled as "(2) Card fills corner". The card's bottom is the navigation card's top (RouteCardPlacement.kt),
 * so nothing of it lies behind that card, and its fill and side lines run on past its bottom, straight down into the two wedges the
 * navigation card's rounded top corners leave, less that card's own shape: so no map shows at either corner, and no point is painted
 * twice (CLAUDE.md: "Layered fills composite to that value; they do not each carry it"). The one line where they meet is the
 * navigation card's own top line with its curves; the card draws no line along it. Replaced Amendment 3's and 5's "Stop at the top"
 * clip, where the card ran 16 dp on behind the 80% navigation card (which showed through) and its sides stopped short of the curves.
 *
 * Rejected: keeping the card's bounds 16 dp behind the navigation card and cutting its drawing to the corners. It draws the same, but
 * the card's bounds would still say it runs on behind that card, which the ruling says it does not.
 */

/**
 * The card's outline as drawn: [base] (the card's shape) over the card's [size] and [cornerFillPx] further down, so its sides run on
 * past its bottom, with no line along that extra bottom (the fill below cuts it away, [routeCardFillPath]).
 */
internal fun Density.routeCardOutlinedPath(base: Shape, size: Size, layoutDirection: LayoutDirection, cornerFillPx: Float): Path =
    Path().apply { addOutline(base.createOutline(Size(size.width, size.height + cornerFillPx), layoutDirection, this@routeCardOutlinedPath)) }

/**
 * The card's fill: [outlined], less the card it rests on below its bottom, a card as wide as this one whose top corners round by
 * [cornerFillPx] (the navigation card's `MAP_CARD_CORNER_RADIUS`, `RoundedMapCard.kt`). What that leaves past the card's bottom is the
 * two wedges beside that card's rounded corners. [cornerFillPx] 0 is [outlined] as it is.
 */
internal fun routeCardFillPath(outlined: Path, size: Size, cornerFillPx: Float): Path {
    if (cornerFillPx <= 0f) return outlined
    val below = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset(0f, size.height), Size(size.width, cornerFillPx * 4f)),
                topLeft = CornerRadius(cornerFillPx),
                topRight = CornerRadius(cornerFillPx),
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero,
            ),
        )
    }
    return Path().apply { op(outlined, below, PathOperation.Difference) }
}

/**
 * Draws the card's [fill] under its content and its [stroke] over it (Amendment 5: the map chrome's outline all the way round, as the
 * bubbles draw it), in [base], and [cornerFill] past its bottom into the corners of the card it rests on (A1; 0 dp everywhere else).
 * The line is the stroke at twice its width along [routeCardOutlinedPath], clipped to the fill, as `Modifier.border` draws a shape's:
 * so it lies inside the card's edge, and where the fill is cut by the card below, no line is drawn. Draw only: it measures nothing and
 * takes no touch.
 */
internal fun Modifier.routeCardFace(base: Shape, fill: Color, stroke: BorderStroke, cornerFill: Dp = 0.dp): Modifier = drawWithCache {
    val cornerFillPx = cornerFill.toPx()
    val outlined = routeCardOutlinedPath(base, size, layoutDirection, cornerFillPx)
    val filled = routeCardFillPath(outlined, size, cornerFillPx)
    val strokePx = stroke.width.toPx()
    onDrawWithContent {
        drawPath(filled, fill)
        drawContent()
        clipPath(filled) { drawPath(outlined, brush = stroke.brush, style = Stroke(width = strokePx * 2f)) }
    }
}

