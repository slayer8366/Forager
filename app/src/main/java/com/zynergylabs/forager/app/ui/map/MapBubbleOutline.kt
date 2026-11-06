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
import kotlin.math.sign

/*
 * Dispatch 2026-11-05-06 (RECORD -2156, the owner: "Outline around both (Recommended)", whose option read "One continuous line around
 * the bubble and out along the tail's two sides to its tip, with no line across where they join, like an outlined speech bubble"):
 * the bubble's card and tail as one shape, which MapBubbleShell fills once and outlines once.
 */

/**
 * The bubble's tail in the shell's own coordinates: the two corners of its base, both on the card's edge, and its tip. [inward] is
 * the card's inward normal at that edge.
 */
internal class BubbleTail(val base1: Offset, val tip: Offset, val base2: Offset, val inward: Offset) {
    /**
     * The triangle, with its base carried [depth] into the card. That part lies inside the card and so adds nothing to the joined
     * shape; it is there so the triangle and the card overlap rather than meet along one line, which a path union need not join
     * cleanly. The two sides, from the base's corners on the card's edge to the tip, are exactly the triangle's.
     */
    fun path(depth: Float): Path = Path().apply {
        moveTo(base1.x, base1.y)
        lineTo(tip.x, tip.y)
        lineTo(base2.x, base2.y)
        lineTo(base2.x + inward.x * depth, base2.y + inward.y * depth)
        lineTo(base1.x + inward.x * depth, base1.y + inward.y * depth)
        close()
    }
}

/**
 * The tail for a shell [size] big, whose card is [tailPx] in from each side with [cornerPx] corners, toward [tip]; `null` where the
 * tip is inside the card (the clamp pushed the card over the point) and no tail is needed.
 *
 * RECORD -2160 (the owner: "Clean triangle (Recommended)", whose option read "Both sides start on the card's edge and run straight to
 * the tip, with no kink. The tail's root changes slightly on slanted tails"): the base lies along the card's edge, [halfWidthPx]
 * either side of where the line from the card's centre to the tip leaves the card's rectangle, kept on that edge's straight stretch
 * clear of the corners. Was the sighting bubble's geometry, the base square to the tail and centred on the edge: on a slanted tail
 * one end of it lay outside the card, so that side began with a step, which the outline (RECORD -2156) made visible.
 */
internal fun bubbleTail(size: Size, tip: Offset, tailPx: Float, halfWidthPx: Float, cornerPx: Float): BubbleTail? {
    val center = Offset(size.width / 2f, size.height / 2f)
    val toTip = tip - center
    val cardHalfWidth = center.x - tailPx
    val cardHalfHeight = center.y - tailPx
    if (abs(toTip.x) <= cardHalfWidth && abs(toTip.y) <= cardHalfHeight) return null
    val angleDeg = Math.toDegrees(atan2(toTip.x.toDouble(), -toTip.y.toDouble())).toFloat()
    val hit = rectEdgeIntersection(cardHalfWidth, cardHalfHeight, angleDeg)
    // The edge the line leaves by: the one whose half-size it reaches.
    val onTopOrBottom = abs(abs(hit.y) - cardHalfHeight) <= abs(abs(hit.x) - cardHalfWidth)
    val base: Offset
    val along: Offset
    val inward: Offset
    if (onTopOrBottom) {
        val reach = (cardHalfWidth - cornerPx - halfWidthPx).coerceAtLeast(0f)
        base = center + Offset(hit.x.coerceIn(-reach, reach), hit.y)
        along = Offset(1f, 0f)
        inward = Offset(0f, -sign(hit.y))
    } else {
        val reach = (cardHalfHeight - cornerPx - halfWidthPx).coerceAtLeast(0f)
        base = center + Offset(hit.x, hit.y.coerceIn(-reach, reach))
        along = Offset(0f, 1f)
        inward = Offset(-sign(hit.x), 0f)
    }
    if ((tip - base).getDistance() <= 0f) return null
    return BubbleTail(base1 = base - along * halfWidthPx, tip = tip, base2 = base + along * halfWidthPx, inward = inward)
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
    return if (tail == null) card else Path.combine(PathOperation.Union, card, tail.path(depth = cornerPx / 2f))
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
