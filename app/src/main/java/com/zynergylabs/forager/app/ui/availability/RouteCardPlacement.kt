package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.map.NavigationSideInsets
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
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
 *
 * [leaveRoom] (D32, RECORD -1987): navigating, how much narrower both cards are, from the L's side, so Re-center's icon fits beside
 * the L at the bottom ([routeCardLeaveRoomPx]); 0 dp wherever it already fits, and not navigating.
 */
internal data class RouteCardLandscapeRoom(val onNavigationCard: Dp, val railOnLeft: Boolean, val maxHeight: Dp, val bottom: Dp = 0.dp, val tuck: Dp = 0.dp, val leaveRoom: Dp = 0.dp)

/** D32: the width both cards take while the route card rests on the navigation card: the strip's, less [RouteCardLandscapeRoom.leaveRoom]. */
internal val RouteCardLandscapeRoom.onNavigationCardWidth: Dp get() = (onNavigationCard - leaveRoom).coerceAtLeast(0.dp)

/** How far the card's lower edge sits behind the landscape navigation card: past that card's rounded top corners (C11, C21). */
internal val ROUTE_CARD_NAVIGATION_TUCK: Dp = 16.dp

/**
 * `null` outside a landscape window, and for the frames before the strip is measured, when there is no width to take. Navigating, until
 * the navigation card is first measured, the card sits at its width on the bottom edge, behind it.
 */
@Composable
internal fun routeCardLandscapeRoom(cluster: MapIconClusterState, stripWidthPx: Int, topLimitPx: Float, isNavigating: Boolean, controlsPadding: PaddingValues = PaddingValues(0.dp)): RouteCardLandscapeRoom? {
    if (!cluster.landscape || stripWidthPx <= 0) return null
    val navigationCard = cluster.navigationCardBoundsInRoot?.takeIf { isNavigating }
    val layoutDirection = LocalLayoutDirection.current
    return with(LocalDensity.current) {
        val railOnLeft = cluster.railPortEdge == ScreenEdge.Left
        // D32 (RECORD -1987): from the values both cards are placed by, never the cards' measured edges, which this moves.
        val leaveRoomPx = if (isNavigating && !railOnLeft) {
            routeCardLeaveRoomPx(
                besideLeftPx = cluster.mapContentBoxLeftInRootPx + (controlsPadding.calculateLeftPadding(layoutDirection) + routeCardBarColumn(cluster.clusterWidthPx.toDp())).toPx(),
                iconPx = RETURN_TO_ROUTE_PILL_HEIGHT.toPx(),
                gapPx = Spacing.sm.toPx(),
                naturalCardLeftPx = cluster.mapContentBoxLeftInRootPx + cluster.mapContentBoxWidthPx - controlsPadding.calculateRightPadding(layoutDirection).toPx() - stripWidthPx,
            )
        } else {
            0f
        }
        val boxBottomPx = cluster.mapContentBoxTopInRootPx + cluster.mapContentBoxHeightPx
        val tuck = if (navigationCard != null) ROUTE_CARD_NAVIGATION_TUCK else 0.dp
        // Sorrel's N-5: a navigation card whose top is within the tuck of the box's bottom leaves no room to tuck into; never below 0.
        val bottom = if (navigationCard != null) ((boxBottomPx - navigationCard.top).toDp() - tuck).coerceAtLeast(0.dp) else 0.dp
        RouteCardLandscapeRoom(
            onNavigationCard = if (isNavigating) stripWidthPx.toDp() else 0.dp,
            railOnLeft = railOnLeft,
            maxHeight = (cluster.mapContentBoxHeightPx - topLimitPx).toDp() - Spacing.sm - bottom,
            bottom = bottom,
            tuck = tuck,
            leaveRoom = leaveRoomPx.toDp(),
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

/**
 * D17 (RECORD -1958, the owner: "Into the open map, at the bottom (Recommended)", whose option read "While the route card sits on the
 * navigation card, Re-center moves sideways out of the card's column into the open map beside it, at the bottom next to the record
 * pill. It stays visible and tappable at every stop count, and doesn't move as the card expands."): whether Re-center goes beside the
 * L, which holds while the card is showing ([cardShowing]) and resting on the navigation card ([landscapeRoom]'s
 * [RouteCardLandscapeRoom.onNavigationCard]). Replaces D10's rise ([reCenterRisesForRouteCard]) for this case only.
 */
internal fun reCenterBesideTheL(landscapeRoom: RouteCardLandscapeRoom?, cardShowing: Boolean): Boolean =
    cardShowing && landscapeRoom != null && landscapeRoom.onNavigationCard > 0.dp

/**
 * D20 (RECORD -1960), as D27 (RECORD -1976) replaces it where room is short: the room for Re-center beside the L, from [leftPx] to a gap,
 * [gapPx], before the card's left edge, [cardLeftPx], all in root px; null while the card is not yet measured.
 */
internal fun reCenterRoomBesideTheL(leftPx: Float, cardLeftPx: Float?, gapPx: Float): Float? = cardLeftPx?.let { it - gapPx - leftPx }


/**
 * D32 (RECORD -1987, the owner: "Both cards narrow (Recommended)" and "Always (Recommended)"): how much narrower the route card and the
 * navigation card are, from the L's side, so Re-center's icon, [iconPx] (the 48 dp target), fits beside the L at the bottom with a
 * gap, [gapPx], before the cards. [besideLeftPx] is where Re-center starts beside the L (the L's right and a gap, as
 * `CompactMapBottomChrome` places it) and [naturalCardLeftPx] the cards' left at the strip's width, all in root px. 0 wherever the
 * icon already fits. Replaces D31's "the route card moves right" (the navigation card fills the bottom under the same span).
 */
internal fun routeCardLeaveRoomPx(besideLeftPx: Float, iconPx: Float, gapPx: Float, naturalCardLeftPx: Float): Float =
    (besideLeftPx + iconPx + gapPx - naturalCardLeftPx).coerceAtLeast(0f)

/** D27: whether Re-center beside the L keeps its word: where its measured width with the word, [wordWidthPx], fits the [roomPx]. */
internal fun reCenterShowsWord(wordWidthPx: Float, roomPx: Float): Boolean = wordWidthPx <= roomPx

/**
 * D17: how far above the map's [MapRenderMode.bottomInset] Re-center sits beside the L: the usual [LEGEND_ATTRIBUTION_CLEARANCE], or,
 * where the credit strip at the bottom start ([creditSizePx]) reaches Re-center's [start] and stands taller than that, a gap above the
 * credit's top, so it is never drawn on the credit (D17: "clear of the pill, the L and the credit").
 */
internal fun reCenterClearanceBesideTheL(creditSizePx: IntSize, start: Dp, density: Density): Dp = with(density) {
    if (creditSizePx.width.toDp() <= start) LEGEND_ATTRIBUTION_CLEARANCE else max(LEGEND_ATTRIBUTION_CLEARANCE, creditSizePx.height.toDp() + Spacing.sm)
}

/** How far inboard the legend chip sits while the card shows: past the bar in portrait with the bar on its side, as in the L. */
internal fun routeCardLegendEndPadding(barOnRight: Boolean, barWidth: Dp): Dp = if (barOnRight) MAP_ICON_BAR_EDGE_INSET + barWidth + Spacing.sm else Spacing.sm


/**
 * D28 with D30 (dispatch 2026-10-30-08, RECORD -1979, -1980): [cardsInRoot], the cards drawn over the map (the route card, and the
 * landscape navigation card while navigating), moved from root px into the map's own, for the map to hide the "i" under
 * (`MapRenderMode.attributionHideUnder`). [mapLeftInRootPx] and [mapTopInRootPx] are the map pane's place in the root.
 */
internal fun attributionCoversInMapPx(cardsInRoot: List<androidx.compose.ui.geometry.Rect>, mapLeftInRootPx: Float, mapTopInRootPx: Float): List<androidx.compose.ui.geometry.Rect> =
    cardsInRoot.map { it.translate(-mapLeftInRootPx, -mapTopInRootPx) }

/**
 * D29 (dispatch 2026-10-30-08, RECORD -1979, -1980; the owner: "Keep the dot clear of the card (Recommended)"): the navigation view's
 * side padding, in px, that puts the walker's dot in the middle of the open map between the L and the expanded route card, both in
 * the map's own px ([cardInMap], [lInMap]). On the card's side, the map's edge to the card's inner edge plus [gapPx]; on the far side,
 * the L's inner edge plus [gapPx] where the L is there, else nothing. [NavigationSideInsets.NONE] with no card, before the map has a
 * width, or where nothing would be left open between the two.
 */
internal fun navigationSideInsetsBesideCard(mapWidthPx: Float, cardInMap: androidx.compose.ui.geometry.Rect?, lInMap: androidx.compose.ui.geometry.Rect?, gapPx: Float): NavigationSideInsets {
    if (cardInMap == null || mapWidthPx <= 0f) return NavigationSideInsets.NONE
    val cardOnRight = cardInMap.center.x >= mapWidthPx / 2f
    val farL = lInMap?.takeIf { (it.center.x < mapWidthPx / 2f) == cardOnRight }
    val left = if (cardOnRight) (farL?.let { it.right + gapPx } ?: 0f) else cardInMap.right + gapPx
    val right = if (cardOnRight) mapWidthPx - cardInMap.left + gapPx else (farL?.let { mapWidthPx - it.left + gapPx } ?: 0f)
    if (left + right >= mapWidthPx) return NavigationSideInsets.NONE
    return NavigationSideInsets(left.toDouble(), right.toDouble())
}
