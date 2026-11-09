package com.zynergylabs.forager.app.ui.route

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.ClipOp
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

/*
 * A2 (RECORD -2187), the owner: "The route card is one size, and its tucked drag bar is another size. Have the drag bar be the same size
 * as the card." and "It should animate to the edge and meet the bar that appears, so it doesn't show the bar appearing, it just shows
 * the card moving to the edge and staying there, with the no size change", the strip "24 dp, as today (Recommended)", the content
 * "Fade (Recommended)". Tucking slides the card itself toward its edge until [ROUTE_CARD_STRIP] of it is left showing, at its full
 * height; the part past the edge is not drawn (in landscape that edge is the side bar's, which would otherwise show the card through
 * its 80% fill), and the edge the card is cut at carries the outline, so the strip is outlined all the way round (Amendment 5).
 * The card's bounds do not move: only what it draws, and where its content is drawn.
 */

/** The width of the card left showing at its edge while it is tucked (the owner: "24 dp, as today (Recommended)"). */
internal val ROUTE_CARD_STRIP: Dp = 24.dp

/**
 * A2: where the card is in its tuck. [slide] is how far it has slid toward its edge (0 resting, 1 tucked, the strip left showing),
 * toward the left edge if [towardLeft]; [faded] how far its words and buttons have faded out (and its grip in); [tucked] whether it
 * is tucked, when its content leaves the semantics tree, as the card did when it left the screen before A2.
 */
@Immutable
data class RouteCardTuckDraw(val slide: Float = 0f, val faded: Float = 0f, val towardLeft: Boolean = false, val tucked: Boolean = false) {
    /** The slide in px for a card [widthPx] wide, the strip [stripPx]: the card's width less the strip, signed toward its edge. */
    fun slidePx(widthPx: Float, stripPx: Float): Float = (if (towardLeft) -1f else 1f) * slide * (widthPx - stripPx).coerceAtLeast(0f)

    companion object {
        val RESTING = RouteCardTuckDraw()
    }
}

/**
 * The card's outline as drawn: [base] (the card's shape) over the card's [size] and [cornerFillPx] further down, so its sides run on
 * past its bottom, with no line along that extra bottom (the fill below cuts it away, [routeCardFillPath]); slid [slidePx] sideways
 * (A2) and cut at its bounds' sides, so the part past its edge is not drawn and the cut is a side of its own.
 */
internal fun Density.routeCardOutlinedPath(base: Shape, size: Size, layoutDirection: LayoutDirection, cornerFillPx: Float, slidePx: Float = 0f): Path {
    val tall = Size(size.width, size.height + cornerFillPx)
    val card = Path().apply { addOutline(base.createOutline(tall, layoutDirection, this@routeCardOutlinedPath)) }
    if (slidePx == 0f) return card
    card.translate(Offset(slidePx, 0f))
    val bounds = Path().apply { addRect(Rect(Offset.Zero, tall)) }
    return Path().apply { op(card, bounds, PathOperation.Intersect) }
}

/** A2: the card's shape within its bounds as it slides, for its Surface's clip and so its touches: what is drawn of it there. */
internal class RouteCardSlidShape(private val base: Shape, private val tuck: RouteCardTuckDraw) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val slidePx = tuck.slidePx(size.width, with(density) { ROUTE_CARD_STRIP.toPx() })
        if (slidePx == 0f) return base.createOutline(size, layoutDirection, density)
        return Outline.Generic(density.routeCardOutlinedPath(base, size, layoutDirection, 0f, slidePx))
    }
}

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
 * bubbles draw it), in [base], and [cornerFill] past its bottom into the corners of the card it rests on (A1; 0 dp everywhere else),
 * slid as far as [tuck] says (A2), with no line of its own on the left side if [bareSideOnLeft], the right if false, where it meets the side bar (`RouteCardRailSide.kt`),
 * its corner there run out to the side bar's line and, beside [railCorner] (the side bar's rounded bottom corner), its edge carrying the
 * line where the side bar's curves away (`RouteCardRailEdge.kt`).
 * The line is the stroke at twice its width along [routeCardOutlinedPath], clipped to the fill, as `Modifier.border` draws a shape's:
 * so it lies inside the card's edge, and where the fill is cut by the card below, no line is drawn. Draw only: it measures nothing and
 * takes no touch.
 */
internal fun Modifier.routeCardFace(
    base: Shape,
    fill: Color,
    stroke: BorderStroke,
    cornerFill: Dp = 0.dp,
    tuck: RouteCardTuckDraw = RouteCardTuckDraw.RESTING,
    bareSideOnLeft: Boolean? = null,
    railCorner: RouteCardRailCorner? = null,
): Modifier = drawWithCache {
    val cornerFillPx = cornerFill.toPx()
    val slidePx = tuck.slidePx(size.width, ROUTE_CARD_STRIP.toPx())
    val outlined = routeCardOutlinedPath(base, size, layoutDirection, cornerFillPx, slidePx)
    val filled = routeCardFillPath(outlined, size, cornerFillPx)
    val strokePx = stroke.width.toPx()
    // S22 defect (1) (RouteCardRailEdge.kt): at rest, the side bar's side is stroked run out to the side bar's line's centre, as -990
    // runs the navigation card's out, and that run-out shape leaves its straight side past the bounds, so no band is needed there.
    // Sliding or tucked, the band takes the line off the strip's cut edge, as before.
    val runOut = if (bareSideOnLeft != null && slidePx == 0f) routeCardRunOutOutline(base, size, layoutDirection, cornerFillPx, strokePx, bareSideOnLeft) else null
    val stroked = runOut ?: outlined
    val lineClip = runOut?.let { routeCardRunOutLineClip(filled, it, size, strokePx) } ?: filled
    val bare = if (runOut == null) bareSideOnLeft?.let { routeCardBareBand(it, size, strokePx, cornerFillPx) } else null
    // S22 defect (2): beside the side bar's rounded bottom corner, the card's edge carries the line, in the side bar's line's columns.
    val cornerRows = if (bareSideOnLeft != null && railCorner != null) routeCardRailCornerRows(size.height, railCorner.below.toPx(), railCorner.radius.toPx()) else null
    onDrawWithContent {
        drawPath(filled, fill)
        drawContent()
        clipPath(lineClip) {
            if (bare == null) {
                drawPath(stroked, brush = stroke.brush, style = Stroke(width = strokePx * 2f))
            } else {
                clipRect(bare.left, bare.top, bare.right, bare.bottom, clipOp = ClipOp.Difference) {
                    drawPath(stroked, brush = stroke.brush, style = Stroke(width = strokePx * 2f))
                }
            }
        }
        if (cornerRows != null && bareSideOnLeft != null) {
            val x = routeCardRailCornerLineX(bareSideOnLeft, size.width, strokePx)
            drawLine(stroke.brush, Offset(x, cornerRows.start), Offset(x, cornerRows.endInclusive), strokeWidth = strokePx)
        }
    }
}

/**
 * Sorrel's review of PR #507, item 1.1 (RECORD -2187, the owner: "(c) All three (Recommended)"; -967): the band along the left side ([onLeft]) or the right of the
 * card's bounds where its line is not drawn, because the side bar's line there is the shared one (`RouteCardRailSide.kt` decides
 * which side). As wide as the line, at the bounds' edge, so it takes the card's line on that side and nothing else: resting or
 * tucked alike, since the tucked strip's cut edge is the bounds' side. It starts below the top line, so the top runs on to the edge;
 * it stops above a bottom line ([cornerFillPx] 0: the plotting card on the screen's bottom edge), and runs on down into the corner
 * wedge where the card rests on the navigation card (that edge has no line; A1).
 */
internal fun routeCardBareBand(onLeft: Boolean, size: Size, strokePx: Float, cornerFillPx: Float): Rect {
    val bottom = if (cornerFillPx > 0f) size.height + cornerFillPx + strokePx else size.height - strokePx
    return if (onLeft) {
        Rect(left = -strokePx, top = strokePx, right = strokePx, bottom = bottom)
    } else {
        Rect(left = size.width - strokePx, top = strokePx, right = size.width + strokePx, bottom = bottom)
    }
}

/**
 * A2: the card's content, slid with the card and faded out as it goes ([RouteCardTuckDraw.faded]); while tucked it leaves the semantics
 * tree, so nothing reads controls that do not show. A layer only: its size and place in the card's layout do not change.
 */
internal fun Modifier.routeCardTuckContent(tuck: RouteCardTuckDraw): Modifier = graphicsLayer {
    translationX = tuck.slidePx(size.width, ROUTE_CARD_STRIP.toPx())
    alpha = 1f - tuck.faded
}.then(if (tuck.tucked) Modifier.clearAndSetSemantics {} else Modifier)

/**
 * A2: the strip's grip, "a short vertical grip bar in the card handle's look" as the tab had (Amendment 1), on the part of the card
 * left showing when it is tucked (the side facing the map), sliding with the card and fading in as its content fades out.
 */
@Composable
internal fun BoxScope.RouteCardStripGrip(tuck: RouteCardTuckDraw) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                translationX = tuck.slidePx(size.width, ROUTE_CARD_STRIP.toPx())
                alpha = tuck.faded
            },
        contentAlignment = if (tuck.towardLeft) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.width(ROUTE_CARD_STRIP), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 4.dp, height = 24.dp).background(LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
        }
    }
}

