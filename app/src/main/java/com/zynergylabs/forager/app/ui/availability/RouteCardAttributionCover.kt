package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_STRIP
import com.zynergylabs.forager.app.ui.route.RouteCardTuckDraw

/*
 * Dispatch 2026-11-06-05, Amendment 6, "the credit 'i' while tucked" (RECORD -2187). The owner, to "While the route card is tucked to
 * a strip, should the map's 'i' (credit) button come back where the card rested?": "Come back"; restated and confirmed: tucked, the
 * spot where the card rested is clear, so the "i" shows there again; the card brought back out, the "i" hides under it as today.
 *
 * Since A2 a tucked card keeps reporting its bounds ("Stays aside", "Stays under"), and those bounds are what the "i" hid under (D28
 * with D30, `attributionHiddenUnder`), so the "i" stayed hidden behind a card that was no longer there. What the "i" is handed is now
 * the part of the card that is drawn: the whole card at rest, its strip once tucked, and the part still over its place on the way
 * (`RouteCardFace.kt`'s slide, cut at the card's bounds). The bounds the card reports for everything else (the evening line, Re-center,
 * the walker's dot) are unchanged. The strip still counts, so the "i" stays hidden wherever the strip itself covers it, by D28.
 * Kept out of CompactMapRoutePlotting.kt, which calls it (.claude/skills/modular-code/SKILL.md).
 */

/**
 * The part of a card at [cardInRoot] that is drawn at [tuck]'s slide: the card's bounds moved by the slide and cut at those bounds, so
 * [stripPx] of it at its edge once tucked. `null` with no bounds yet.
 */
internal fun routeCardDrawnBounds(cardInRoot: Rect?, tuck: RouteCardTuckDraw, stripPx: Float): Rect? {
    if (cardInRoot == null) return null
    val slidePx = tuck.slidePx(cardInRoot.width, stripPx)
    if (slidePx == 0f) return cardInRoot
    return cardInRoot.translate(slidePx, 0f).intersect(cardInRoot)
}

/** Reports to [onCover] the card's drawn part ([routeCardDrawnBounds]) for the "i" to hide under, and nothing while it is [leaving]. */
@Composable
internal fun ReportRouteCardAttributionCover(cardInRoot: Rect?, tuck: RouteCardTuckDraw, leaving: Boolean, onCover: (Rect?) -> Unit) {
    val stripPx = with(LocalDensity.current) { ROUTE_CARD_STRIP.toPx() }
    val cover = if (leaving) null else routeCardDrawnBounds(cardInRoot, tuck, stripPx)
    LaunchedEffect(cover) { onCover(cover) }
}
