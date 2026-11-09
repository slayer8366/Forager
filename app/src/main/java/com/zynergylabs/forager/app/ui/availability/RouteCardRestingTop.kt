package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/*
 * Dispatch 2026-11-06-01, Amendment 1, J8, with its answers (RECORD -2183). The owner, with the route card resting on the navigation
 * card: "(b) Above top card (Recommended)": the walker's dot is kept just above whichever card is on top, the route card's top when
 * it rests on the navigation card, otherwise the navigation card's; while the route card is expanded or elsewhere, D29's sideways move
 * as today. #500 built the dot's side (NavigationDotAboveCard.kt) reading `MapIconClusterState.navigationRestingOnCardTopInRootPx`
 * as "the top of whatever rests on the navigation card"; this is the route card writing it (on #507, once #500 was in pre-main).
 *
 * Where it is going, not where it is drawn, as the navigation card's own resting bounds are (Sorrel's J8 review, item 1.1): the
 * navigation card's resting top, not its drawn top mid-glide; the card's laid-out height, which the open/close slide does not move;
 * and while it is tucked, expanded, leaving or not on the navigation card, nothing (null), so the navigation card's top is used.
 * Kept out of CompactMapRoutePlotting.kt, which composes it (.claude/skills/modular-code/SKILL.md).
 */

/**
 * The route card's top, in root px, where it rests on the landscape navigation card; `null` where it does not ([restsOnNavigationCard]
 * false), or before the navigation card's resting top or the card's height is known.
 *
 * The card's bottom sits on the navigation card's top less the controls' bottom padding ([controlsBottomPx]), as it is laid out
 * (`CompactMapRouteCard`: the controls' padding, then `RouteCardLandscapeRoom.bottom`, the box's bottom to the navigation card's top).
 * Its height is [cardHeightPx] as laid out, capped by the cap it will have at rest: [cardMaxHeightPx] less however much higher the
 * navigation card will rest than it is drawn ([navigationCardDrawnTopPx]), since the cap's bottom rises with that card's top.
 */
internal fun routeCardRestingOnCardTopPx(
    restsOnNavigationCard: Boolean,
    navigationCardRestingTopPx: Float?,
    navigationCardDrawnTopPx: Float?,
    controlsBottomPx: Float,
    cardHeightPx: Int?,
    cardMaxHeightPx: Float?,
): Float? {
    if (!restsOnNavigationCard || navigationCardRestingTopPx == null || cardHeightPx == null || cardHeightPx <= 0) return null
    val rise = navigationCardDrawnTopPx?.let { it - navigationCardRestingTopPx } ?: 0f
    val height = cardMaxHeightPx?.let { minOf(cardHeightPx.toFloat(), it - rise) } ?: cardHeightPx.toFloat()
    return navigationCardRestingTopPx - controlsBottomPx - height
}

/** Writes [routeCardRestingOnCardTopPx] to [cluster] for the walker's dot (J8), and clears it when the route card goes. */
@Composable
internal fun ReportRouteCardRestingTop(
    cluster: MapIconClusterState,
    restsOnNavigationCard: Boolean,
    cardHeightPx: Int?,
    cardMaxHeight: Dp?,
    controlsPadding: PaddingValues,
) {
    val top = with(LocalDensity.current) {
        routeCardRestingOnCardTopPx(
            restsOnNavigationCard = restsOnNavigationCard,
            navigationCardRestingTopPx = cluster.navigationCardRestingBoundsInRoot?.top,
            navigationCardDrawnTopPx = cluster.navigationCardBoundsInRoot?.top,
            controlsBottomPx = controlsPadding.calculateBottomPadding().toPx(),
            cardHeightPx = cardHeightPx,
            cardMaxHeightPx = cardMaxHeight?.toPx(),
        )
    }
    SideEffect { cluster.navigationRestingOnCardTopInRootPx = top }
    DisposableEffect(Unit) { onDispose { cluster.navigationRestingOnCardTopInRootPx = null } }
}
