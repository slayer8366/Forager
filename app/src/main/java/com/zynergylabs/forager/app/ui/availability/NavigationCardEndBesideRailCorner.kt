package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge

/**
 * Dispatch 2026-11-05-10, Amendment 1, E20 (RECORD -2181). The owner's marked screenshot puts the missing line at the bottom of the
 * screen, where the landscape navigation card's end meets the side bar's rounded bottom corner. The owner: "Outline the card's end
 * there" ("Where the side bar's corner curves away, the card's own end edge carries the outline, so the line is continuous").
 *
 * While the side bar is shown, the card leaves its side-bar side open, because the side bar's inner edge draws the one line there
 * ([landscapeColumnOutlineSides], RECORD -967, -990). But the side bar's inner bottom corner is rounded ([MAP_CARD_CORNER_RADIUS],
 * RECORD -952) and clipped to that shape. So over that corner's radius its edge curves away from the card, and map shows beside the
 * card's end with no line on either piece. This draws the card's own end edge over exactly that band, from where the side bar's
 * corner starts to curve down to the card's bottom, and nowhere else.
 *
 * The line lies inside the card's edge, a stroke wide, where [com.zynergylabs.forager.app.ui.map.mapChromeOutlinePath] lays a drawn
 * side. It is not out on the side bar's line, half a stroke beyond the card's edge: the card is clipped to its bounds
 * ([CompactMapNavigationDisplay]), so a line there would not show. Below the corner's start the line therefore runs one stroke nearer
 * the map than the side bar's line above it, touching it.
 *
 * Rejected: drawing the end edge down the card's full height. Above the corner the side bar's line is already there, and two lines
 * side by side would read as one line twice as thick.
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
        val railBottom = size.height + bottomGap.toPx()
        val from = (railBottom - railCornerRadius.toPx()).coerceAtLeast(0f)
        val to = minOf(size.height, railBottom)
        if (from < to) {
            val x = if (railPortEdge == ScreenEdge.Left) strokePx / 2f else size.width - strokePx / 2f
            drawLine(stroke.brush, Offset(x, from), Offset(x, to), strokeWidth = strokePx)
        }
    }
}
