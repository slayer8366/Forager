package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * Dispatch 2026-11-06-05, Amendment 1, plan step 1 (RECORD -2187), the owner: "(b) Card leaves room", whose option read "the card
 * never moves, but while navigating there is an empty band under it". It replaces -1861 ("Pill sits above the card") and -1943's rise
 * (Re-center rose by the card's height where the two shared a column). Now Re-center never moves for the card: it stays at its own
 * place, bottom centre, and the open card sits higher by Re-center's height and a gap while navigating, so Re-center is under it
 * whenever it shows and the card does not move as it appears or goes. Not navigating, Re-center never shows and the card sits where it
 * always did. In landscape the card is placed by `RouteCardPlacement.kt` and Re-center by #506's fit test (it shows only where its word
 * covers no card, else the ringed locate row stands in), so nothing is under the card there and no room is left.
 *
 * Decided from where the two are placed, never from their measured bounds, so the card's place cannot depend on whether Re-center
 * shows, and nothing here feeds back into itself.
 */

/**
 * The portrait card's span across the map's box, in px from the box's left: flush to the screen edge away from the icon bar
 * ([onLeft], absolute), inside the controls' padding ([leftPadPx], [rightPadPx]), its width the reference card's, [referenceWidthPx],
 * capped by the room beside the bar's column, [barColumnPx] (C16 with C19, `RouteCardPlacement.kt`'s `RouteCardWithin`).
 */
internal fun routeCardPortraitSpanPx(boxWidthPx: Float, leftPadPx: Float, rightPadPx: Float, barColumnPx: Float, referenceWidthPx: Float, onLeft: Boolean): ClosedFloatingPointRange<Float> {
    val width = minOf(referenceWidthPx, (boxWidthPx - leftPadPx - rightPadPx - barColumnPx).coerceAtLeast(0f))
    return if (onLeft) leftPadPx..leftPadPx + width else (boxWidthPx - rightPadPx - width)..(boxWidthPx - rightPadPx)
}

/** Re-center's span at its own place, bottom centre of the box inside the controls' padding, with its word, [wordWidthPx] wide. */
internal fun reCenterSpanAtBottomCentrePx(boxWidthPx: Float, leftPadPx: Float, rightPadPx: Float, wordWidthPx: Float): ClosedFloatingPointRange<Float> {
    val centre = leftPadPx + (boxWidthPx - leftPadPx - rightPadPx) / 2f
    return (centre - wordWidthPx / 2f)..(centre + wordWidthPx / 2f)
}

/**
 * Whether the open card leaves Re-center's room under it: while navigating ([isNavigating], the only time Re-center can show), in
 * portrait ([portrait]), where Re-center at its own place, [reCenter], shares the card's columns, [card]. A card in the other column (a
 * tablet in portrait) leaves no band, and Re-center stays beside it as before. Before Re-center's word is measured ([reCenter] null) the
 * room is left, so the common case never moves when the measurement lands.
 */
internal fun routeCardLeavesReCenterRoom(isNavigating: Boolean, portrait: Boolean, card: ClosedFloatingPointRange<Float>, reCenter: ClosedFloatingPointRange<Float>?): Boolean =
    isNavigating && portrait && (reCenter == null || (reCenter.start < card.endInclusive && card.start < reCenter.endInclusive))

/** How much higher the card sits for Re-center: its height with its word, [wordHeight] (at least the 48 dp target), and a gap. */
internal fun routeCardReCenterRoom(wordHeight: Dp): Dp = max(wordHeight, RETURN_TO_ROUTE_PILL_HEIGHT) + Spacing.sm

/**
 * The room the portrait card leaves under it for Re-center, 0 dp wherever it leaves none ([routeCardLeavesReCenterRoom]). [wordSize] is
 * Re-center's size with its word, measured on D34's probe (`ReturnToRouteWordProbe`: never placed, no semantics, 0 x 0), as the bottom
 * chrome measures it, so it follows the font scale; zero before it is measured. [cardOnLeft], [barColumn] and [referenceWidth] are what
 * the card is placed by (`CompactMapRouteCard`).
 */
@Composable
internal fun routeCardReCenterRoomFor(
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    isNavigating: Boolean,
    portrait: Boolean,
    cardOnLeft: Boolean,
    barColumn: Dp,
    referenceWidth: Dp,
    wordSize: IntSize,
): Dp {
    val layoutDirection = LocalLayoutDirection.current
    return with(LocalDensity.current) {
        val boxWidth = cluster.mapContentBoxWidthPx
        val leftPad = controlsPadding.calculateLeftPadding(layoutDirection).toPx()
        val rightPad = controlsPadding.calculateRightPadding(layoutDirection).toPx()
        // The card aligns to its start edge when [cardOnLeft]; absolute left only in a left-to-right layout.
        val onLeft = cardOnLeft == (layoutDirection == LayoutDirection.Ltr)
        val card = routeCardPortraitSpanPx(boxWidth, leftPad, rightPad, barColumn.toPx(), referenceWidth.toPx(), onLeft)
        val word = wordSize.takeIf { it != IntSize.Zero && boxWidth > 0f }
        val reCenter = word?.let { reCenterSpanAtBottomCentrePx(boxWidth, leftPad, rightPad, it.width.toFloat()) }
        if (routeCardLeavesReCenterRoom(isNavigating, portrait, card, reCenter)) routeCardReCenterRoom(word?.height?.toDp() ?: 0.dp) else 0.dp
    }
}
