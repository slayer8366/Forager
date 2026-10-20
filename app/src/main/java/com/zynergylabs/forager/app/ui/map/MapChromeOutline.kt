package com.zynergylabs.forager.app.ui.map

// Moved from MapChrome.kt unchanged (UI split Phase 1, PR 7, dispatch 2026-10-19-16).

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dispatch 2026-10-13-02 (RECORD -966, -967; the owner: "See the icon bar where there's an outline? Can you give the same
 * treatment to the botton navigation bar and the top telemetry header?"): the one outline every outlined piece of map chrome
 * draws, the icon bar's own: [MAP_CHROME_OUTLINE_WIDTH] wide in [mapIconStackBorderColor]. [MapIconBar] reads it, and so do
 * the search pill, the compass strip, the navigation card, the bottom bar and the rail ([mapChromeEdges]), so the pieces
 * cannot drift apart.
 */
@Composable
@ReadOnlyComposable
internal fun mapChromeOutline(): BorderStroke = BorderStroke(MAP_CHROME_OUTLINE_WIDTH, mapIconStackBorderColor())

/** The outline's width, the icon bar's hairline: see [mapChromeOutline]. */
internal val MAP_CHROME_OUTLINE_WIDTH = 1.dp

/**
 * Which sides of a piece of map chrome carry the outline (RECORD -967, the owner: "1 C one line between the two like there is
 * now" and "2 yes"). Absolute sides, since the pieces sit against absolute screen edges.
 *
 * - **A side on the screen's own edge carries none** (RECORD -967): the rail's outer side (by the system's navigation buttons),
 *   the bottom bar's bottom and the landscape card's bottom. The rail's top and bottom carry it since RECORD -990.
 * - **Except, since the owner's later answers:**
 *   - the top pills in portrait (RECORD -976, "Have the outlines follow the entire pill at top"): the search pill all the way
 *     round, the compass strip on its sides and bottom, its top left open under the pill;
 *   - the top pills in landscape (RECORD -988, "The pill in landscape isn't fully outlined"): the pill all the way round, the
 *     strip on its top and bottom and on any side not joined to the pill or the rail;
 *   - the bottom bar's sides (RECORD -989, "give the side of the bottom navigation strip the outline too. All the way down.
 *     Just not the bottom since it's supposed to meld into the android navbar").
 * - **Where two outlined pieces touch, one line is drawn, by the bar**: the search pill draws its join with the compass strip
 *   (its bottom in portrait, its inner side in landscape), and the rail draws its inner edge where the landscape strip and the
 *   navigation card sit against it. The strip and the card leave that side open while the bar beside them is shown; with the
 *   bar gone (fullscreen) the side faces the map and they draw it. Chosen so the line stays put while the bar is there: the rail
 *   is one straight edge the full height of the screen, and the strip and card cover parts of it that change while navigating.
 * - **A rounded corner always carries the outline**: its gap shows the map, whichever sides it joins.
 */
internal data class MapChromeOutlineSides(
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean,
    /**
     * RECORD -990 (the owner: "there's still a slight gap in the outline in landscape mode"): a side left open because the piece
     * beside it draws the one shared line there (the strip and the navigation card beside the rail). Its rounded corners then
     * run out to that line's centre, one stroke beyond this piece's own edge, so they meet the neighbour's line with no step.
     * Before -990 they ended inside the piece, one stroke short of the rail's line, and the outline stepped out by that much
     * between the corners (the owner's S26 Ultra screenshot, the strip's end against the rail).
     */
    val leftShared: Boolean = false,
    val rightShared: Boolean = false,
)

/**
 * The open outline of a piece [size] big with these corner radii (px), as one path: the drawn [sides] and every rounded corner,
 * stroked [strokePx] wide inside the piece's edge, where `Modifier.border` lays the icon bar's. Where a drawn side meets a
 * square corner whose other side is not drawn, it runs on to the piece's bounds, so it meets the line the piece beside it
 * draws without a gap; where both sides of a square corner are drawn, the path turns the corner unbroken. One path, so where
 * two of its parts meet nothing is laid twice.
 */
internal fun mapChromeOutlinePath(
    size: Size,
    strokePx: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    sides: MapChromeOutlineSides,
): Path {
    val h = strokePx / 2f
    val l = if (sides.leftShared && !sides.left) -h else h
    val t = h
    val r = if (sides.rightShared && !sides.right) size.width + h else size.width - h
    val b = size.height - h
    val tl = (topLeft - h).coerceAtLeast(0f)
    val tr = (topRight - h).coerceAtLeast(0f)
    val br = (bottomRight - h).coerceAtLeast(0f)
    val bl = (bottomLeft - h).coerceAtLeast(0f)
    val roundTl = topLeft > 0f
    val roundTr = topRight > 0f
    val roundBr = bottomRight > 0f
    val roundBl = bottomLeft > 0f
    val path = Path()
    if (sides.left && sides.top && sides.right && sides.bottom) {
        path.addRoundRect(
            RoundRect(
                left = l, top = t, right = r, bottom = b,
                topLeftCornerRadius = CornerRadius(tl),
                topRightCornerRadius = CornerRadius(tr),
                bottomRightCornerRadius = CornerRadius(br),
                bottomLeftCornerRadius = CornerRadius(bl),
            ),
        )
        return path
    }
    // Clockwise, from the top-left corner: corner, side, corner, side ... Corners are even, sides odd.
    val sideDrawn = booleanArrayOf(sides.top, sides.right, sides.bottom, sides.left)
    val cornerRound = booleanArrayOf(roundTl, roundTr, roundBr, roundBl)
    fun drawn(i: Int): Boolean = if (i % 2 == 1) {
        sideDrawn[i / 2]
    } else {
        val c = i / 2
        cornerRound[c] || (sideDrawn[c] && sideDrawn[(c + 3) % 4])
    }
    // At least one side is not drawn (the closed case returned above); start just after an element that is not.
    val start = (0 until 8).first { !drawn(it) }
    var penDown = false
    for (k in 1..8) {
        val i = (start + k) % 8
        if (!drawn(i)) {
            penDown = false
            continue
        }
        when (i) {
            0 -> if (roundTl) { path.arcTo(Rect(l, t, l + 2 * tl, t + 2 * tl), 180f, 90f, !penDown); penDown = true }
            2 -> if (roundTr) { path.arcTo(Rect(r - 2 * tr, t, r, t + 2 * tr), 270f, 90f, !penDown); penDown = true }
            4 -> if (roundBr) { path.arcTo(Rect(r - 2 * br, b - 2 * br, r, b), 0f, 90f, !penDown); penDown = true }
            6 -> if (roundBl) { path.arcTo(Rect(l, b - 2 * bl, l + 2 * bl, b), 90f, 90f, !penDown); penDown = true }
            else -> {
                val (p0, p1) = when (i) {
                    // Top, left to right.
                    1 -> Pair(
                        if (roundTl) l + tl to t else if (sides.left) l to t else 0f to t,
                        if (roundTr) r - tr to t else if (sides.right) r to t else size.width to t,
                    )
                    // Right, top to bottom.
                    3 -> Pair(
                        if (roundTr) r to t + tr else if (sides.top) r to t else r to 0f,
                        if (roundBr) r to b - br else if (sides.bottom) r to b else r to size.height,
                    )
                    // Bottom, right to left.
                    5 -> Pair(
                        if (roundBr) r - br to b else if (sides.right) r to b else size.width to b,
                        if (roundBl) l + bl to b else if (sides.left) l to b else 0f to b,
                    )
                    // Left, bottom to top.
                    else -> Pair(
                        if (roundBl) l to b - bl else if (sides.bottom) l to b else l to size.height,
                        if (roundTl) l to t + tl else if (sides.top) l to t else l to 0f,
                    )
                }
                if (penDown) path.lineTo(p0.first, p0.second) else path.moveTo(p0.first, p0.second)
                path.lineTo(p1.first, p1.second)
                penDown = true
            }
        }
    }
    return path
}

/**
 * Draws [stroke] along this piece's [sides] and rounded corners ([mapChromeOutlinePath]), over its fill and content, as
 * `Modifier.border` draws the icon bar's. Draw only: it measures nothing and takes no touch, so no size or touch area changes.
 */
internal fun Modifier.mapChromeEdges(
    stroke: BorderStroke,
    topLeft: Dp,
    topRight: Dp,
    bottomRight: Dp,
    bottomLeft: Dp,
    sides: MapChromeOutlineSides,
): Modifier = drawWithContent {
    drawContent()
    val strokePx = stroke.width.toPx()
    val path = mapChromeOutlinePath(size, strokePx, topLeft.toPx(), topRight.toPx(), bottomRight.toPx(), bottomLeft.toPx(), sides)
    drawPath(path, brush = stroke.brush, style = Stroke(width = strokePx))
}

/**
 * Dispatch 2026-10-17-09 (RECORD -1224, -1225): the shape a piece of chrome is clipped or filled to, set beside its test tag on
 * the search pill, the bottom bar and the rail, so a test reads the shape the piece was given rather than its pixels. Semantics
 * only: nothing is drawn, measured or touched by it.
 */
internal val MapChromeShape = SemanticsPropertyKey<Shape>("MapChromeShape")

/** Marks this node with the shape it was given ([MapChromeShape]). */
internal fun Modifier.mapChromeShape(shape: Shape): Modifier = semantics { set(MapChromeShape, shape) }

/** The outline [mapChromeEdgesReported] drew on a piece: its corner radii and its [MapChromeOutlineSides]. */
internal data class MapChromeEdgesDrawn(
    val topLeft: Dp,
    val topRight: Dp,
    val bottomRight: Dp,
    val bottomLeft: Dp,
    val sides: MapChromeOutlineSides,
)

/** The outline a piece carries, for tests (dispatch 2026-10-17-09). Absent where no outline is drawn. */
internal val MapChromeEdges = SemanticsPropertyKey<MapChromeEdgesDrawn>("MapChromeEdges")

/**
 * [mapChromeEdges], with what it draws set on the node as [MapChromeEdges], from the same arguments, so the reading cannot drift
 * from the drawing. Used by the search pill, the bottom bar and the rail.
 */
internal fun Modifier.mapChromeEdgesReported(
    stroke: BorderStroke,
    topLeft: Dp,
    topRight: Dp,
    bottomRight: Dp,
    bottomLeft: Dp,
    sides: MapChromeOutlineSides,
): Modifier = semantics { set(MapChromeEdges, MapChromeEdgesDrawn(topLeft, topRight, bottomRight, bottomLeft, sides)) }
    .mapChromeEdges(stroke, topLeft, topRight, bottomRight, bottomLeft, sides)
