package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.zynergylabs.forager.app.ui.availability.rectEdgeIntersection
import kotlin.math.abs
import kotlin.math.atan2

/*
 * Dispatch 2026-11-05-06 (RECORD -2156, the owner: "Outline around both (Recommended)", whose option read "One continuous line around
 * the bubble and out along the tail's two sides to its tip, with no line across where they join, like an outlined speech bubble"):
 * the bubble's card and tail as one shape, which MapBubbleShell fills once and outlines once.
 */

/** The bubble's tail in the shell's own coordinates: the two corners of its base and its tip. */
internal class BubbleTail(val base1: Offset, val tip: Offset, val base2: Offset) {
    fun path(): Path = Path().apply {
        moveTo(base1.x, base1.y)
        lineTo(tip.x, tip.y)
        lineTo(base2.x, base2.y)
        close()
    }
}

/**
 * The tail for a shell [size] big, whose card is [tailPx] in from each side, toward [tip]; `null` where the tip is inside the card
 * (the clamp pushed the card over the point) and no tail is needed. The geometry is the sighting bubble's, moved out of
 * MapBubbleShell's drawBehind unchanged: the base is centred where the line from the card's centre to the tip leaves the card's
 * rectangle, [halfWidthPx] either side of it, square to the tail.
 */
internal fun bubbleTail(size: Size, tip: Offset, tailPx: Float, halfWidthPx: Float): BubbleTail? {
    val center = Offset(size.width / 2f, size.height / 2f)
    val toTip = tip - center
    val cardHalfWidth = center.x - tailPx
    val cardHalfHeight = center.y - tailPx
    if (abs(toTip.x) <= cardHalfWidth && abs(toTip.y) <= cardHalfHeight) return null
    val angleDeg = Math.toDegrees(atan2(toTip.x.toDouble(), -toTip.y.toDouble())).toFloat()
    val base = center + rectEdgeIntersection(cardHalfWidth, cardHalfHeight, angleDeg)
    val length = (tip - base).getDistance().takeIf { it > 0f } ?: return null
    val dir = (tip - base) / length
    return BubbleTail(
        base1 = Offset(base.x - dir.y * halfWidthPx, base.y + dir.x * halfWidthPx),
        tip = tip,
        base2 = Offset(base.x + dir.y * halfWidthPx, base.y - dir.x * halfWidthPx),
    )
}

/**
 * The card and its [tail] as one path in the shell's coordinates: the card [tailPx] in from each side with [cornerPx] corners, joined
 * with the tail's triangle. Where the two overlap they are one area, so one fill lays the 0.8 there once (CLAUDE.md, "Layered fills
 * composite to that value; they do not each carry it"); before this the tail was filled under the card and the sliver they shared
 * read 0.96.
 */
internal fun bubbleShape(size: Size, tailPx: Float, cornerPx: Float, tail: BubbleTail?): Path {
    val card = Path().apply {
        addRoundRect(RoundRect(tailPx, tailPx, size.width - tailPx, size.height - tailPx, CornerRadius(cornerPx)))
    }
    return if (tail == null) card else Path.combine(PathOperation.Union, card, tail.path())
}

/**
 * The map-chrome hairline along [shape]'s edge, inside it, as `Modifier.border` lays a Surface's: a stroke twice [stroke]'s width
 * centred on the edge, clipped to the shape, so [stroke]'s width of it lies inside. One path, so where the tail joins the card no line
 * is drawn, and the line runs on along the tail's two sides to its tip.
 */
internal fun DrawScope.drawBubbleOutline(stroke: BorderStroke, shape: Path) {
    val strokePx = stroke.width.toPx()
    clipPath(shape) {
        drawPath(shape, brush = stroke.brush, style = Stroke(width = strokePx * 2f))
    }
}
