package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides

/*
 * Sorrel's review of PR #507, item 1.1, and the owner's answer (RECORD -2187, 2026-11-08T22:50:26Z): "(c) All three (Recommended)".
 * Wherever the landscape route card or its tucked strip meets the side bar (resting on the navigation card while navigating, the
 * plotting card in the navigation card's spot, and the tucked strip's edge, navigating and plotting), the card draws no line of its own
 * on that edge: the side bar's is the one shared line, RECORD -967 (the owner, 2026-10-14): "Where two outlined pieces touch, one shared
 * 1 dp line is drawn: ... strip or card against the rail". This qualifies Amendment 5's "Plain line all the way round, like a bubble"
 * and the tab's "Same as the card" at that edge only; every other edge keeps its line.
 *
 * Decided by the navigation card's own rule, [landscapeNavigationCardOutlineSides] (which that card draws by, so the call is one, not
 * a copy), so the two stacked cards agree at that edge in every case: bare while the side bar shows;
 * once it has slid away (fullscreen), lined where a system bar is left between the card and the screen's edge, bare where nothing is.
 *
 * Rejected: a rule of the route card's own ("no line on the rail side while the rail shows"). It would agree today and drift apart the
 * next time the navigation card's rule changes, and the owner asked for the navigation card's rule.
 */

/**
 * The landscape navigation card's outlined sides (dispatch 2026-10-13-02, RECORD -966, -967): its top and rounded top corners and its
 * side facing the map; its rail side once the rail has slid away, unless that side is then on the screen's edge; its bottom is the
 * screen's, here (that card adds its own bottom once it moves up off the screen's edge, J4, RECORD -2183). Read by that card
 * (`CompactMapLandscapeNavigationCard.kt`) and by [routeCardBareSide], so both read one rule.
 */
internal fun landscapeNavigationCardOutlineSides(
    railPortEdge: ScreenEdge,
    isFullscreen: Boolean,
    controlsPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): MapChromeOutlineSides = landscapeColumnOutlineSides(
    railPortEdge = railPortEdge,
    railShown = !isFullscreen,
    railSideInset = if (railPortEdge == ScreenEdge.Left) {
        controlsPadding.calculateLeftPadding(layoutDirection)
    } else {
        controlsPadding.calculateRightPadding(layoutDirection)
    },
    innerSideDrawn = true,
    top = true,
    bottom = false,
)

/**
 * The side of the landscape route card that draws no line of its own, or null when every side keeps its line: in portrait ([room]
 * null), and in landscape when the card sits in the far corner from the side bar ([cardOnLeft] away from it), where no side meets it.
 * Otherwise the side bar's side, wherever the navigation card's rule leaves that side unlined.
 */
internal fun routeCardBareSide(
    room: RouteCardLandscapeRoom?,
    cardOnLeft: Boolean,
    isFullscreen: Boolean,
    controlsPadding: PaddingValues,
    layoutDirection: LayoutDirection,
): ScreenEdge? {
    if (room == null || cardOnLeft != room.railOnLeft) return null
    val railPortEdge = if (room.railOnLeft) ScreenEdge.Left else ScreenEdge.Right
    val sides = landscapeNavigationCardOutlineSides(railPortEdge, isFullscreen, controlsPadding, layoutDirection)
    val railSideDrawn = if (room.railOnLeft) sides.left else sides.right
    return if (railSideDrawn) null else railPortEdge
}
