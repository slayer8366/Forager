package com.zynergylabs.forager.app.ui.route

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/*
 * The route card's line where it meets the side bar, beside the bare band of Sorrel's review of PR #507, item 1.1 (RECORD -2187, "(c) All
 * three"; -967: the side bar's line is the one shared line there). Two defects the S22 found on the combined build 5032 (RECORD -2187,
 * "S22 part A on the combined build"; the owner: "Fix the three"), each the route card's copy of a fix the navigation card already has.
 * Kept out of RouteCardFace.kt, which draws with them (.claude/skills/modular-code/SKILL.md).
 *
 * (1) The top corner on the side bar's side ended about 1 dp short of the side bar's line, map showing between. The card's line lies
 * inside its edge (its stroke is centred on the edge and clipped to its fill), and the bare band took the corner's last few dp, so the
 * curve ended one line inside the card. RECORD -990 fixed the same step for the navigation card and the strip (the owner: "there's still
 * a slight gap in the outline in landscape mode"): on a side left open for a neighbour's line, the rounded corners "run out to that line's
 * centre, one stroke beyond this piece's own edge" (`MapChromeOutline.kt`, `leftShared`/`rightShared`), and the piece's clip to its
 * bounds keeps the line inside them. The route card draws its own path, not `mapChromeOutlinePath`, so the same run-out is made here in
 * its own terms: the shape is laid one stroke wider on the bare side, so its corner's line, the stroke inside that wider edge, covers
 * exactly the radii the navigation card's covers (from the same centre), and it is let show inside the card's bounds, not only inside
 * its narrower fill. Its straight side there then lies wholly past the bounds and is not drawn, which is what the bare band did at rest.
 *
 * (2) In landscape, the card on the screen's bottom edge beside the side bar (the plotting card, resting or tucked) had no line on that
 * edge where the side bar's bottom corner curves away (RECORD -952's rounded inner corners): the side bar's line leaves the card's edge
 * there and nothing of the card's replaces it. This is the navigation card's E20/E21 case (RECORD -2181, the owner: "Outline the card's
 * end there", then "one shared line, no 1 dp step"; `NavigationCardEndBesideRailCorner.kt` on PR #500): over the rows where that corner
 * curves away, the card's edge carries the line, a stroke wide, just beyond the edge in the side bar's line's columns, so it continues
 * that line straight down. #500 is not in this branch, so its band rule (the side bar's corner from its bottom up its radius) is
 * written again here, three lines, rather than shared; once both PRs are in one tree the two can be one function.
 *
 * Rejected for (1): drawing the corner's line unclipped, past the card's bounds. It would show under the side bar's 80% fill, which E21
 * rejected for the navigation card for the same reason. Rejected for (2): a line down the card's whole edge; above the corner the side
 * bar's line is already there, and two 40% lines over each other read darker.
 */

/**
 * (2) The side bar's rounded bottom corner beside the card: its bottom [below] the card's bottom (the controls' bottom padding, and the
 * card's own lift off the bottom edge where it rests on the navigation card), and its [radius].
 */
@Immutable
data class RouteCardRailCorner(val below: Dp, val radius: Dp)

/**
 * (1) The card's outline as stroked when its side on [bareOnLeft]'s side meets the side bar: [base] laid over the card's [size] one
 * [strokePx] wider on that side (and [cornerFillPx] taller, as [routeCardOutlinedPath]), so its corner there runs out to the side bar's
 * line's centre.
 */
internal fun Density.routeCardRunOutOutline(base: Shape, size: Size, layoutDirection: LayoutDirection, cornerFillPx: Float, strokePx: Float, bareOnLeft: Boolean): Path {
    val wider = Size(size.width + strokePx, size.height + cornerFillPx)
    return Path().apply {
        addOutline(base.createOutline(wider, layoutDirection, this@routeCardRunOutOutline))
        if (bareOnLeft) translate(Offset(-strokePx, 0f))
    }
}

/**
 * (1) Where the line is let show: the card's [filled] shape, and, within its bounds above its bottom line, the inside of [runOut]. The
 * second adds only the sliver between the card's own corner and the run-out one on the bare side (elsewhere the two shapes are the
 * same, and below the bottom the fill's own cut stands, A1).
 */
internal fun routeCardRunOutLineClip(filled: Path, runOut: Path, size: Size, strokePx: Float): Path {
    val withinBounds = Path().apply { addRect(Rect(0f, 0f, size.width, (size.height - strokePx).coerceAtLeast(0f))) }
    val corner = Path().apply { op(runOut, withinBounds, PathOperation.Intersect) }
    return Path().apply { op(filled, corner, PathOperation.Union) }
}

/**
 * (2) The rows, in the card's own px, over which the side bar's rounded bottom corner curves away beside a card [heightPx] tall whose
 * bottom sits [railBelowPx] above the side bar's: from the corner's top ([railCornerPx] up from the side bar's bottom) down to the card's
 * bottom. `null` where the corner lies wholly below the card (resting on the navigation card, for one). The navigation card's rule
 * (E20, `railCornerBand` on PR #500).
 */
internal fun routeCardRailCornerRows(heightPx: Float, railBelowPx: Float, railCornerPx: Float): ClosedFloatingPointRange<Float>? {
    val railBottom = heightPx + railBelowPx
    val from = (railBottom - railCornerPx).coerceAtLeast(0f)
    val to = minOf(heightPx, railBottom)
    return if (from < to) from..to else null
}

/** (2) The x, in the card's own px, of the centre of the line beside the side bar's corner: half a stroke past the card's edge. */
internal fun routeCardRailCornerLineX(bareOnLeft: Boolean, widthPx: Float, strokePx: Float): Float =
    if (bareOnLeft) -strokePx / 2f else widthPx + strokePx / 2f
