package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.route.routeCardRailCornerRows

/**
 * Dispatch 2026-11-05-10, Amendment 1, E20 (RECORD -2181). The owner's marked screenshot puts the missing line at the bottom of the
 * screen, where the landscape navigation card's end meets the side bar's rounded bottom corner. The owner: "Outline the card's end
 * there" ("Where the side bar's corner curves away, the card's own end edge carries the outline, so the line is continuous").
 *
 * While the side bar is shown, the card leaves its side-bar side open, because the side bar's inner edge draws the one line there
 * ([landscapeColumnOutlineSides], RECORD -967, -990). But the side bar's inner bottom corner is rounded ([MAP_CARD_CORNER_RADIUS],
 * RECORD -952) and clipped to that shape. So over that corner's radius its edge curves away from the card, and map shows beside the
 * card's end with no line on either piece. This draws the card's end line over exactly that band, from where the side bar's corner
 * starts to curve down to the card's bottom, and nowhere else.
 *
 * **E21** (RECORD -2181; on the S22 E20's line ran about 1 dp nearer the map than the side bar's line above it, a visible step. The
 * owner: "Option 1. It has never been any other way. This must be from context drift.", the option: "Where the navigation card meets
 * the side bar, one shared line, no 1 dp step"). The line lies where the side bar's lies: a stroke wide, just beyond the card's edge
 * (centred half a stroke out, as [com.zynergylabs.forager.app.ui.map.mapChromeOutlinePath] lays a shared side, RECORD -990), so it
 * continues the side bar's line straight down with no step. E20 had drawn it inside the card's edge, because the card's display is
 * clipped to its bounds ([CompactMapNavigationDisplay]); that clip is now [clipToBoundsOpenBesideRailCorner], which lets exactly this
 * band of one stroke beyond the edge show and clips everything else as before.
 *
 * Rejected: drawing the end edge down the card's full height. Above the corner the side bar's line is already there. Rejected for E21:
 * widening the clip a stroke down the card's whole side-bar edge, which would also show, under the side bar's 80% fill, whatever the
 * card draws past its edge there (its top corner's arc out to the side bar's line, RECORD -990): a change nobody asked for.
 *
 * Draw only: it measures nothing and takes no touch.
 */
internal fun Modifier.navigationCardEndBesideRailCorner(
    stroke: BorderStroke,
    railPortEdge: ScreenEdge,
    railShown: Boolean,
    /** How far the card's bottom sits above the side bar's (the controls' bottom padding). */
    bottomGap: Dp,
    railCornerRadius: Dp = MAP_CARD_CORNER_RADIUS,
): Modifier = if (!railShown) {
    this
} else {
    drawWithContent {
        drawContent()
        val strokePx = stroke.width.toPx()
        val band = routeCardRailCornerRows(size.height, bottomGap.toPx(), railCornerRadius.toPx()) ?: return@drawWithContent
        val x = if (railPortEdge == ScreenEdge.Left) -strokePx / 2f else size.width + strokePx / 2f
        drawLine(stroke.brush, Offset(x, band.start), Offset(x, band.endInclusive), strokeWidth = strokePx)
    }
}

/**
 * E21: the card's display clipped to its bounds, as before ([clipToBounds]), except, while the side bar is shown, one stroke beyond its
 * side-bar edge over the rows where the side bar's bottom corner curves away beside the card ([routeCardRailCornerRows], the one rule
 * the route card beside the side bar reads too, `RouteCardRailEdge.kt`), where [navigationCardEndBesideRailCorner]'s line lies. Nothing
 * else the card draws reaches there (it casts no shadow, RECORD -2154, and its bottom corners are square on the screen's edge), so only
 * that line is let through.
 */
internal fun Modifier.clipToBoundsOpenBesideRailCorner(
    strokeWidth: Dp,
    railPortEdge: ScreenEdge,
    railShown: Boolean,
    bottomGap: Dp,
    railCornerRadius: Dp = MAP_CARD_CORNER_RADIUS,
): Modifier = if (!railShown) clipToBounds() else clip(BoundsOpenBesideRailCorner(strokeWidth, railPortEdge, bottomGap, railCornerRadius))

private data class BoundsOpenBesideRailCorner(
    private val strokeWidth: Dp,
    private val railPortEdge: ScreenEdge,
    private val bottomGap: Dp,
    private val railCornerRadius: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bounds = Rect(0f, 0f, size.width, size.height)
        val band = with(density) { routeCardRailCornerRows(size.height, bottomGap.toPx(), railCornerRadius.toPx()) }
            ?: return Outline.Rectangle(bounds)
        val strokePx = with(density) { strokeWidth.toPx() }
        val column = if (railPortEdge == ScreenEdge.Left) {
            Rect(-strokePx, band.start, 0f, band.endInclusive)
        } else {
            Rect(size.width, band.start, size.width + strokePx, band.endInclusive)
        }
        return Outline.Generic(Path().apply { addRect(bounds); addRect(column) })
    }
}
