package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * Where the route card sits beside the icon bar (dispatch 2026-10-30-08, Amendments 14 and 15, RECORD -1928, -1929). The owner: "The
 * map icon bar stays the same no matter what. The card just gets resized to fit the map icon bar next to it". The card reads the
 * bar; nothing here is told to the bar. That is the whole of the rule: Amendment 3's raising, Amendment 10's fold and Amendment 11's
 * held pill all went through the bar, and each had a state the bar was not where the user left it.
 */

/** The gap between the card's side edge and the bar (C17: the card's far side is flush to the screen's edge, with no margin). */
internal val ROUTE_CARD_SIDE_MARGIN: Dp = Spacing.sm

/** The radius of the card's round corners: the ones facing the map. */
internal val ROUTE_CARD_CORNER: Dp = 16.dp

/**
 * How much of the map's width the bar's column takes: its edge inset, its width and the gap. The width is the bar's measured one,
 * kept while it is minimised, so a minimised bar leaves the card as it was (the restore handle is narrower than the bar, and takes
 * less of that column); at least [MIN_TOUCH_TARGET], the bar's width, for a bar minimised since before it was first measured.
 */
internal fun routeCardBarColumn(barWidth: Dp): Dp = MAP_ICON_BAR_EDGE_INSET + max(barWidth, MIN_TOUCH_TARGET) + ROUTE_CARD_SIDE_MARGIN

/**
 * The room the card's width is capped by (C19: "never wider than the room beside the bar"), as padding inside the controls' own: the
 * bar's column on the bar's side, nothing on the card's own edge (C17: "Flush to the edge (Recommended)"). C18's "touching" the
 * navigation card took that card's width on the card's side too; C21 replaced it (the card rests on the navigation card instead, at
 * its width), and that half went with it.
 */
internal fun routeCardSides(barOnLeft: Boolean, barColumn: Dp): PaddingValues =
    if (barOnLeft) PaddingValues(start = barColumn) else PaddingValues(end = barColumn)

/**
 * The card's width (C16 with C19, RECORD -1946, -1953): the reference card's, [reference] (`RouteCardWidth.kt`), and never more than
 * the room left inside [sides]. One width for every state; it changes with the text size or the room, never with what the card shows.
 */
@Composable
internal fun RouteCardWithin(reference: Dp, sides: PaddingValues, content: @Composable (Dp) -> Unit) {
    BoxWithConstraints(Modifier.padding(sides)) { content(min(reference, maxWidth)) }
}

/**
 * The portrait card's shape (C17, RECORD -1946, the owner: "Flat on the side it comes from only"): square on the screen edge it
 * slid in from, both corners; round on the side facing the map, top and bottom. The handle stays on top.
 */
internal fun routeCardPortraitShape(barOnLeft: Boolean): Shape =
    if (barOnLeft) AbsoluteRoundedCornerShape(topLeft = ROUTE_CARD_CORNER, bottomLeft = ROUTE_CARD_CORNER) else AbsoluteRoundedCornerShape(topRight = ROUTE_CARD_CORNER, bottomRight = ROUTE_CARD_CORNER)

/** The landscape card's shape (C18: "Rises from the bottom, tab on top (Recommended)"): round top corners, square on the bottom edge. */
internal val ROUTE_CARD_LANDSCAPE_SHAPE: Shape = RoundedCornerShape(topStart = ROUTE_CARD_CORNER, topEnd = ROUTE_CARD_CORNER)

/**
 * The landscape card's place (C18 with C20, RECORD -1947, -1953; C21 with C23, -1955, -1956). On the screen's bottom edge, at C19's
 * width. Not navigating, in the corner away from the L: the navigation card's spot on the rail's side, or, with the L kept on the
 * rail's side, the other bottom corner (C20). The rail is on the screen's right at both rotations (RECORD -956), so both rotations
 * place the card alike.
 *
 * Navigating (C21, the owner: "On top of the navigation card (Recommended)", "sits on it, at its width"), [onNavigationCard] is the
 * navigation card's width, the strip's (`CompactMapLandscapeNavigationCard.kt`), and the card rests on that card at that width, edge to
 * edge, in its column on the rail's side ([railOnLeft]): [bottom] above the map's bottom edge, the navigation card's top less [tuck],
 * which the card keeps empty under its buttons so all its content, Save included, shows above (restored from C11, 8a6126f9d). At large
 * text the navigation card is narrower than C19's reference (329 dp against 409.67 at font 2.0) and the card keeps its width, its lines
 * wrapping (C23: "Navigation card's width, lines wrap (Recommended)"). Not navigating, [onNavigationCard], [bottom] and [tuck] are 0.
 *
 * At most [maxHeight] tall, below the strip and the search bar, the rows taking what is cut (`RouteCard`'s rows are measured last, and
 * scroll).
 */
internal data class RouteCardLandscapeRoom(val onNavigationCard: Dp, val railOnLeft: Boolean, val maxHeight: Dp, val bottom: Dp = 0.dp, val tuck: Dp = 0.dp)

/** How far the card's lower edge sits behind the landscape navigation card: past that card's rounded top corners (C11, C21). */
internal val ROUTE_CARD_NAVIGATION_TUCK: Dp = 16.dp

/**
 * `null` outside a landscape window, and for the frames before the strip is measured, when there is no width to take. Navigating, until
 * the navigation card is first measured, the card sits at its width on the bottom edge, behind it.
 */
@Composable
internal fun routeCardLandscapeRoom(cluster: MapIconClusterState, stripWidthPx: Int, topLimitPx: Float, isNavigating: Boolean): RouteCardLandscapeRoom? {
    if (!cluster.landscape || stripWidthPx <= 0) return null
    val navigationCard = cluster.navigationCardBoundsInRoot?.takeIf { isNavigating }
    return with(LocalDensity.current) {
        val boxBottomPx = cluster.mapContentBoxTopInRootPx + cluster.mapContentBoxHeightPx
        val tuck = if (navigationCard != null) ROUTE_CARD_NAVIGATION_TUCK else 0.dp
        // Sorrel's N-5: a navigation card whose top is within the tuck of the box's bottom leaves no room to tuck into; never below 0.
        val bottom = if (navigationCard != null) ((boxBottomPx - navigationCard.top).toDp() - tuck).coerceAtLeast(0.dp) else 0.dp
        RouteCardLandscapeRoom(
            onNavigationCard = if (isNavigating) stripWidthPx.toDp() else 0.dp,
            railOnLeft = cluster.railPortEdge == ScreenEdge.Left,
            maxHeight = (cluster.mapContentBoxHeightPx - topLimitPx).toDp() - Spacing.sm - bottom,
            bottom = bottom,
            tuck = tuck,
        )
    }
}

/** Which side the card sits on: on the navigation card, the rail's; otherwise away from the bar. */
internal fun routeCardOnLeft(barOnLeft: Boolean, landscapeRoom: RouteCardLandscapeRoom?): Boolean =
    if (landscapeRoom != null && landscapeRoom.onNavigationCard > 0.dp) landscapeRoom.railOnLeft else !barOnLeft

/**
 * The portrait card's motion (Amendment 15, the owner: "Have the card slide in from the opposite side of the map icon bar in
 * portrait"): in from the edge away from the bar and back out to it, on the landscape navigation card's timing and with its fade.
 * Under reduced motion it only fades, as that card does.
 */
@Composable
internal fun routeCardPortraitTransitions(barOnLeft: Boolean): Pair<EnterTransition, ExitTransition> {
    val reduceMotion = LocalReduceMotion.current
    val slide = MotionTokens.navigationViewChromeSpec<IntOffset>()
    val timedFade = MotionTokens.navigationViewChromeSpec<Float>()
    val reducedFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val offEdge: (Int) -> Int = { fullWidth -> if (barOnLeft) fullWidth else -fullWidth }
    val enter = if (reduceMotion) fadeIn(animationSpec = reducedFade) else slideInHorizontally(animationSpec = slide, initialOffsetX = offEdge) + fadeIn(animationSpec = timedFade)
    val exit = if (reduceMotion) fadeOut(animationSpec = reducedFade) else slideOutHorizontally(animationSpec = slide, targetOffsetX = offEdge) + fadeOut(animationSpec = timedFade)
    return enter to exit
}

/**
 * Whether the attribution "i" shares the card's edge in portrait. It sits in the bottom-end corner, so with the bar on the right the
 * card is away from it, and the "i" stays put, under the bar as it always was; with the bar on the left the card is over that corner.
 */
internal fun routeCardOverAttribution(cluster: MapIconClusterState): Boolean = cluster.landscape || cluster.isOnLeftSide

/**
 * In a landscape window the map's credit and its "i" sit at the bottom left (RECORD -954). Where the card covers the credit
 * ([cardOverCredit], [routeCardOverCredit]) they rise above it, by the inset they both read. They go back when the card closes or no
 * longer covers it.
 */
internal fun MapRenderMode.withCreditAboveRouteCard(cardHeightDp: Int, cardOverCredit: Boolean): MapRenderMode =
    if (cardHeightDp <= 0 || !cardOverCredit) this else copy(bottomInset = bottomInset + cardHeightDp.dp + Spacing.sm)

/**
 * D13 (RECORD -1955, the planner's call under D9: "the 'i' and other chrome keep clear of the card"): whether the card, [card], covers
 * the credit where it rests, [creditAtRest], both in root px. D16 (RECORD -1956, the planner's call): "meet" means the two rectangles
 * overlap; a card that only shares the credit's horizontal span, or only its rows, or only touches its edge, leaves it down. Was
 * "landscape with the L on the right", which left the credit under the card beside the navigation card on the S22 (D12: over Undo by
 * 9 px, Save by 46 px). The credit's resting place is judged, never where it is drawn, so a risen credit is not then found clear and
 * dropped back under the card. Landscape only for now (D16: portrait waits for the S22's pictures). No card or no measured credit: no
 * rise.
 */
internal fun routeCardOverCredit(card: Rect?, creditAtRest: Rect?): Boolean {
    if (card == null || creditAtRest == null) return false
    return card.left < creditAtRest.right && creditAtRest.left < card.right && card.top < creditAtRest.bottom && creditAtRest.top < card.bottom
}

/**
 * Where the map's credit strip rests, in root px: at the map box's bottom start, over the map's own [bottomInsetPx] (the inset before any
 * rise for the card; `SightingsMapLayout` pads it by that), at its laid-out [creditSizePx]. `null` until it is measured.
 */
internal fun mapCreditAtRest(cluster: MapIconClusterState, creditSizePx: IntSize, bottomInsetPx: Float): Rect? {
    if (creditSizePx.width <= 0 || creditSizePx.height <= 0) return null
    val bottom = cluster.mapContentBoxTopInRootPx + cluster.mapContentBoxHeightPx - bottomInsetPx
    val left = cluster.mapContentBoxLeftInRootPx
    return Rect(left, bottom - creditSizePx.height, left + creditSizePx.width, bottom)
}

/**
 * Whether "Re-center" rises for the card (Amendment 17, part 2 ruled, RECORD -1943, the owner: "Fix the S22 case, then check
 * (Recommended)"): only where its horizontal span, [pill], overlaps the card's, [card], both in root px. A card in the other column
 * leaves it where it is, so it never rises onto the search bar for a card it was never over (the S22, landscape, navigating,
 * expanded). Where they share a column it rises as before, which in a short landscape window can still reach the search bar: that
 * case is unruled (RECORD -1943). A pill not yet measured rises, so it is never drawn over the card; no card, no rise. The legend
 * chip's own lift is unchanged.
 */
internal fun reCenterRisesForRouteCard(pill: ClosedFloatingPointRange<Float>?, card: ClosedFloatingPointRange<Float>?): Boolean {
    if (card == null) return false
    if (pill == null) return true
    return pill.start < card.endInclusive && card.start < pill.endInclusive
}

/** How far inboard the legend chip sits while the card shows: past the bar in portrait with the bar on its side, as in the L. */
internal fun routeCardLegendEndPadding(barOnRight: Boolean, barWidth: Dp): Dp = if (barOnRight) MAP_ICON_BAR_EDGE_INSET + barWidth + Spacing.sm else Spacing.sm

