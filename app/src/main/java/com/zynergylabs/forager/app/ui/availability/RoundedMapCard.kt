package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapChromeEdges
import com.zynergylabs.forager.app.ui.map.mapChromeOutlinePath

/**
 * Dispatch 2026-10-12-06 (RECORD -932, -934): the rounded, tucked look of the map's top chrome, shared by the search pill
 * ([SearchEntryBar] over the map), the compass strip and the navigation card.
 *
 * **Zero side margin** (RECORD -934, the owner: "leave zero margin between the card and the edge of the screen, the curves
 * can meet the edge"): the pieces keep the full width they had, and only their corners round. The planner read the pill and
 * the strip as matching the card's width; recorded there for the owner to correct from the screenshots.
 *
 * **The tuck** (portrait): the card sits under the pill with the pill's lower edge over its top. Built so no point of the
 * map is painted twice (CLAUDE.md, "Layered fills composite to that value; they do not each carry it"): the card's own
 * layout starts at the pill's bottom, as the strip did, and its fill is drawn [MAP_CARD_TUCK] higher than that, *minus* the
 * pill's own rounded shape. What that adds is only the two wedges under the pill's rounded lower corners, which would
 * otherwise show the map between the pill and the card. The pill is composed before the card in `CompactMapTab`'s Box, so
 * the card is above it in z-order; the card takes no touch of its own (a background, no `Surface`, no pointer input), so a
 * touch on the pill's lower edge still falls through to the pill. The pill is a `Surface`, which clips to its rounded shape
 * before its pointer input, so its corner gaps pass touches to the map; both are coordinate-touch tested.
 *
 * Rejected: raising the pill over the card with `zIndex`. It would lift the bar above every sibling composed after it in that
 * Box (the bubble, the cluster, the pickers), whose order is recorded as load-bearing (`CompactMapTab`, the mapSlot comment).
 * Rejected too: letting the card's fill run under the pill whole. The overlap would composite to 0.96, not the 0.8 token.
 *
 * **The shadow** is cast by the card's visible part only (its own bounds, not the tuck), so it does not darken the pill
 * above it. Like every shadow under the 0.8 fill here, it shows through slightly (`MapChromeColourPixelsTest`'s
 * shadowed-colour rule, 3% in light and 7% in dark as measured there).
 */
internal val MAP_CARD_CORNER_RADIUS = 16.dp

/** How far above its own top the card's fill reaches, behind the pill's lower corners: the pill's corner radius. */
internal val MAP_CARD_TUCK = MAP_CARD_CORNER_RADIUS

/** How far the pill's cast shadow reaches down onto the tucked card. */
internal val MAP_CARD_TUCK_SHADOW_DEPTH = 6.dp

/**
 * Each dp of the cast shadow lays this much black; the dp nearest the pill is covered by every step, so it is darkest
 * (about 1 - (1 - a)^n of black over the card's fill there, about 17% at 6 steps of 0.03), fading to one step's worth.
 */
internal const val MAP_CARD_TUCK_SHADOW_STEP_ALPHA = 0.03f

/** The shadow under the pill and the card, the elevation the control pill and the icon bar already use over the map. */
internal val MAP_CARD_SHADOW_ELEVATION = 2.dp

/**
 * Which corners of a top-chrome piece round. Portrait: the pill rounds all four; the card rounds its lower two (its upper
 * two run into the tuck, under the pill). Landscape, side by side (RECORD -934: "the pill and card touch edge to edge (no
 * join line)"): each rounds its outer side's two corners and keeps the joined side square, so the two read as one bar. RECORD
 * -967 replaces "no join line" (the owner: "1 C one line between the two like there is now"): the pill's outline is the one line
 * on the join.
 */
internal data class MapCardCorners(val topLeft: Dp, val topRight: Dp, val bottomRight: Dp, val bottomLeft: Dp) {
    fun shape(): Shape = AbsoluteRoundedCornerShape(
        topLeft = CornerSize(topLeft),
        topRight = CornerSize(topRight),
        bottomRight = CornerSize(bottomRight),
        bottomLeft = CornerSize(bottomLeft),
    )

    companion object {
        val ALL = MapCardCorners(MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS)
        val BOTTOM = MapCardCorners(0.dp, 0.dp, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS)

        /** RECORD -953: the landscape navigation card on the screen's bottom edge: its top two corners round, its bottom square. */
        val TOP = MapCardCorners(MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS, 0.dp, 0.dp)

        /**
         * Landscape, navigating: the display beside the bar, taller than it. Its two [outer] corners round, and its inner bottom
         * corner, below the bar; only its inner top corner, where it meets the bar, stays square.
         */
        fun besideBar(outer: ScreenEdge?): MapCardCorners = when (outer) {
            ScreenEdge.Left -> MapCardCorners(MAP_CARD_CORNER_RADIUS, 0.dp, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS)
            ScreenEdge.Right -> MapCardCorners(0.dp, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS)
            else -> ALL
        }

        /** Landscape: the two corners on [outer] round; the joined side stays square. All four with no join ([outer] `null`). */
        fun outerSide(outer: ScreenEdge?): MapCardCorners = when (outer) {
            null -> ALL
            ScreenEdge.Left -> MapCardCorners(MAP_CARD_CORNER_RADIUS, 0.dp, 0.dp, MAP_CARD_CORNER_RADIUS)
            ScreenEdge.Right -> MapCardCorners(0.dp, MAP_CARD_CORNER_RADIUS, MAP_CARD_CORNER_RADIUS, 0.dp)
            else -> ALL
        }
    }
}

/**
 * The card's surface: its shadow, cast by [corners]' shape on its own bounds, and its [fill], drawn from [tuck] above its
 * top down, less the pill's rounded shape above it (see this file's comment). [tuck] 0 draws exactly the card's own shape.
 * No pointer input: the card stays transparent to touches everywhere but its own controls.
 */
internal fun Modifier.mapCardSurface(
    fill: Color,
    corners: MapCardCorners,
    tuck: Dp = 0.dp,
    /**
     * Dispatch 2026-10-12-08 (RECORD -950): the corners of the piece this card is tucked under, whose lower two decide what the
     * tuck cuts away. The pill in portrait rounds all four; the landscape compass strip, which the navigation card hangs under,
     * is square on the side where it meets the pill (RECORD -934), so its inner lower corner cuts nothing round.
     */
    upperCorners: MapCardCorners = MapCardCorners.ALL,
    /**
     * Dispatch 2026-10-13-02 (RECORD -966, -967): the map chrome outline ([com.zynergylabs.forager.app.ui.map.mapChromeOutline])
     * along [outlineSides] and every rounded corner of [corners], drawn on the card's own bounds over its content. Never along
     * the tuck: the tucked top is a side the pill draws ([portraitStripOutlineSides]). With a tuck, a drawn side runs up into it,
     * clipped at the pill ([tuckedCardEdges], RECORD -976). `null` draws none.
     */
    outline: BorderStroke? = null,
    outlineSides: MapChromeOutlineSides? = null,
): Modifier = this
    .graphicsLayer {
        shadowElevation = MAP_CARD_SHADOW_ELEVATION.toPx()
        shape = corners.shape()
        clip = false
    }
    .drawBehind {
        val tuckPx = tuck.toPx()
        val shape = corners.shape()
        if (tuckPx <= 0f) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutlineFill(outline, fill)
            return@drawBehind
        }
        val pill = upperPiecePath(size, tuckPx, upperCorners)
        val fillPath = tuckedCardFillPath(size, tuckPx, corners, upperCorners)
        drawPath(fillPath, fill)
        // RECORD -943's follow-up (the owner: "don't forget the card tuck. I don't see it on the preview"): the pill's shadow
        // cast onto the card, which the 2 dp elevation did not show through the 0.8 fills. Drawn here, on the card, as the pill's
        // own shape stepped down a dp at a time in faint black, clipped to the card's fill, so the band hugs the pill's lower
        // edge and its rounded corners and fades over MAP_CARD_TUCK_SHADOW_DEPTH. Drawn under the card's content.
        val steps = MAP_CARD_TUCK_SHADOW_DEPTH.value.toInt().coerceAtLeast(1)
        val step = MAP_CARD_TUCK_SHADOW_DEPTH.toPx() / steps
        clipPath(fillPath) {
            for (i in 1..steps) {
                val shifted = Path().apply { addPath(pill, androidx.compose.ui.geometry.Offset(0f, i * step)) }
                drawPath(shifted, Color.Black.copy(alpha = MAP_CARD_TUCK_SHADOW_STEP_ALPHA))
            }
        }
    }
    .then(
        if (outline != null && outlineSides != null) {
            if (tuck > 0.dp && (outlineSides.left || outlineSides.right)) {
                Modifier.tuckedCardEdges(outline, corners, tuck, upperCorners, outlineSides)
            } else {
                Modifier.mapChromeEdges(outline, corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft, outlineSides)
            }
        } else {
            Modifier
        },
    )

/**
 * The piece above a tucked card (the pill in portrait, the compass strip in landscape) in the card's coordinates: as wide as the
 * card, its lower edge on the card's top, its lower corners [upperCorners]'. Its top is far above, so only its lower corners
 * decide what is cut away.
 */
private fun androidx.compose.ui.unit.Density.upperPiecePath(size: Size, tuckPx: Float, upperCorners: MapCardCorners): Path = Path().apply {
    addRoundRect(
        RoundRect(
            left = 0f, top = -tuckPx - 4 * MAP_CARD_CORNER_RADIUS.toPx(), right = size.width, bottom = 0f,
            bottomLeftCornerRadius = CornerRadius(upperCorners.bottomLeft.toPx()),
            bottomRightCornerRadius = CornerRadius(upperCorners.bottomRight.toPx()),
        ),
    )
}

/** A tucked card's fill: from [tuckPx] above its top down, less the piece above it ([upperPiecePath]); see this file's comment. */
private fun androidx.compose.ui.unit.Density.tuckedCardFillPath(
    size: Size,
    tuckPx: Float,
    corners: MapCardCorners,
    upperCorners: MapCardCorners,
): Path {
    val card = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f, top = -tuckPx, right = size.width, bottom = size.height,
                bottomLeftCornerRadius = CornerRadius(corners.bottomLeft.toPx()),
                bottomRightCornerRadius = CornerRadius(corners.bottomRight.toPx()),
            ),
        )
    }
    return Path().apply { op(card, upperPiecePath(size, tuckPx, upperCorners), PathOperation.Difference) }
}

/**
 * Dispatch 2026-10-13-02, RECORD -976 (the owner: "In portrait mode: Have the outlines follow the entire pill at top"): a tucked
 * card's outline when it carries a side line. The sides run up from the card's own top into the tuck, so they follow the card's
 * fill up the two wedges under the pill's rounded lower corners to where the pill's own outline curves away, and the whole is
 * clipped to the fill ([tuckedCardFillPath]). So nothing is drawn where the pill is: the card is above the pill in z-order, and an
 * unclipped side would lie over the pill's corner and double its line. The top stays open; the pill's lower edge is the one line
 * there. Draw only, like [mapChromeEdges].
 */
private fun Modifier.tuckedCardEdges(
    stroke: BorderStroke,
    corners: MapCardCorners,
    tuck: Dp,
    upperCorners: MapCardCorners,
    sides: MapChromeOutlineSides,
): Modifier = drawWithContent {
    drawContent()
    val tuckPx = tuck.toPx()
    val strokePx = stroke.width.toPx()
    val tall = Size(size.width, size.height + tuckPx)
    val path = mapChromeOutlinePath(
        tall, strokePx, corners.topLeft.toPx(), corners.topRight.toPx(), corners.bottomRight.toPx(), corners.bottomLeft.toPx(),
        sides.copy(top = false),
    )
    path.translate(Offset(0f, -tuckPx))
    clipPath(tuckedCardFillPath(size, tuckPx, corners, upperCorners)) {
        drawPath(path, brush = stroke.brush, style = Stroke(width = strokePx))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOutlineFill(outline: Outline, fill: Color) {
    when (outline) {
        is Outline.Rectangle -> drawRect(fill, topLeft = outline.rect.topLeft, size = outline.rect.size)
        is Outline.Rounded -> drawPath(Path().apply { addRoundRect(outline.roundRect) }, fill)
        is Outline.Generic -> drawPath(outline.path, fill)
    }
}

/** The tuck for a card whose top is [topInset] below the map's top: none once the pill has slid away (fullscreen), and never more than the pill's corner. */
internal fun mapCardTuck(topInset: Dp): Dp = topInset.coerceIn(0.dp, MAP_CARD_TUCK)

/**
 * Dispatch 2026-10-13-02 (see [MapChromeOutlineSides]): the portrait compass strip's outlined sides. RECORD -976 (the owner: "In
 * portrait mode: Have the outlines follow the entire pill at top") replaces -967's bottom only: its bottom, its rounded lower
 * corners and its two sides, though they lie on the screen's edges. Its top is open: it is either tucked under the pill, whose
 * own lower edge is the one line between them, or, once the pill has slid away in fullscreen, the top of the map's area. While
 * tucked the sides run up into the wedges under the pill's corners, clipped at the pill ([tuckedCardEdges]), so no line is drawn
 * where the pill is.
 */
internal fun portraitStripOutlineSides(): MapChromeOutlineSides = MapChromeOutlineSides(left = true, top = false, right = true, bottom = true)

/**
 * Dispatch 2026-10-13-02, RECORD -967 (see [MapChromeOutlineSides]): the outlined sides of a landscape piece in the rail's
 * column, the compass strip at the top or the navigation card on the bottom edge.
 *
 * - Its rail side: open while the rail is shown, since the rail draws its inner edge, the one line between them. With the rail
 *   gone (fullscreen) it faces the map and is drawn, unless nothing is left between it and the screen's edge ([railSideInset]
 *   0: Robolectric's zero insets, or no system bar on that side).
 * - Its other side: drawn when [innerSideDrawn]: the card's always faces the map; the strip's meets the search pill, which
 *   draws that join, so the strip draws it only once the pill has slid away.
 * - [top] and [bottom] as the caller says: the strip's top is the top of the map's area, the card's bottom the screen's.
 */
internal fun landscapeColumnOutlineSides(
    railPortEdge: ScreenEdge,
    railShown: Boolean,
    railSideInset: Dp,
    innerSideDrawn: Boolean,
    top: Boolean,
    bottom: Boolean,
    /**
     * RECORD -988: draw the rail side once the rail has gone even when it then lies on the screen's edge (the compass strip,
     * outlined all the way round). False keeps -967's rule for the navigation card, which -988 leaves unchanged.
     */
    railSideOnScreenEdgeDrawn: Boolean = false,
): MapChromeOutlineSides {
    val railSide = !railShown && (railSideInset > 0.dp || railSideOnScreenEdgeDrawn)
    // RECORD -990: while the rail is shown it draws the shared line, and this piece's rounded corners on that side meet it.
    return if (railPortEdge == ScreenEdge.Left) {
        MapChromeOutlineSides(left = railSide, top = top, right = innerSideDrawn, bottom = bottom, leftShared = railShown)
    } else {
        MapChromeOutlineSides(left = innerSideDrawn, top = top, right = railSide, bottom = bottom, rightShared = railShown)
    }
}
