package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
 * join line)"): each rounds its outer side's two corners and keeps the joined side square, so the two read as one bar.
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
internal fun Modifier.mapCardSurface(fill: Color, corners: MapCardCorners, tuck: Dp = 0.dp): Modifier = this
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
        val r = MAP_CARD_CORNER_RADIUS.toPx()
        val card = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = -tuckPx, right = size.width, bottom = size.height,
                    bottomLeftCornerRadius = CornerRadius(corners.bottomLeft.toPx()),
                    bottomRightCornerRadius = CornerRadius(corners.bottomRight.toPx()),
                ),
            )
        }
        // The pill: as wide as the card (both span the window in portrait), its lower edge on the card's top, its lower
        // corners rounded by the same radius. Its top is far above, so only its lower corners decide what is cut away.
        val pill = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = -tuckPx - 4 * r, right = size.width, bottom = 0f,
                    bottomLeftCornerRadius = CornerRadius(r),
                    bottomRightCornerRadius = CornerRadius(r),
                ),
            )
        }
        val fillPath = Path().apply { op(card, pill, PathOperation.Difference) }
        drawPath(fillPath, fill)
        // RECORD -943's follow-up (the owner: "don't forget the card tuck. I don't see it on the preview"): the pill's shadow
        // cast onto the card, which the 2 dp elevation did not show through the 0.8 fills. Drawn here, on the card, as the pill's
        // own shape stepped down a dp at a time in faint black, clipped to the card's fill, so the band hugs the pill's lower
        // edge and its rounded corners and fades over MAP_CARD_TUCK_SHADOW_DEPTH. Drawn under the card's content.
        val steps = MAP_CARD_TUCK_SHADOW_DEPTH.value.toInt().coerceAtLeast(1)
        val step = MAP_CARD_TUCK_SHADOW_DEPTH.toPx() / steps
        if (steps < 0) clipPath(fillPath) {
            for (i in 1..steps) {
                val shifted = Path().apply { addPath(pill, androidx.compose.ui.geometry.Offset(0f, i * step)) }
                drawPath(shifted, Color.Black.copy(alpha = MAP_CARD_TUCK_SHADOW_STEP_ALPHA))
            }
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
