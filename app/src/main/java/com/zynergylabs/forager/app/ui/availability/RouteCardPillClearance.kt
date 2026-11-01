package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_MAX_WIDTH

/** The room the landscape L's horizontal pill takes from its side of the map: its edge inset, two buttons, and the same inset again as the gap. */
internal val ROUTE_CARD_L_PILL_CLEARANCE: Dp = MAP_ICON_BAR_EDGE_INSET + MIN_TOUCH_TARGET * 2 + MAP_ICON_BAR_EDGE_INSET

/** Where the card sits across the width it is given, in px. */
internal data class RouteCardSpan(val startPx: Int, val widthPx: Int)

/** Stub for the failing tests of Amendment 13: the card centred, as it has always been. */
internal fun routeCardSpan(availablePx: Int, maxCardPx: Int, clearancePx: Int, clusterOnLeft: Boolean): RouteCardSpan {
    val width = minOf(availablePx, maxCardPx)
    return RouteCardSpan((availablePx - width) / 2, width)
}

/** Lays the card out at its [routeCardSpan]; `null` (portrait) leaves it to its own width and alignment. */
internal fun Modifier.routeCardClearOfPill(clusterOnLeft: Boolean?): Modifier {
    if (clusterOnLeft == null) return this
    return layout { measurable, constraints ->
        val span = routeCardSpan(constraints.maxWidth, ROUTE_CARD_MAX_WIDTH.roundToPx(), ROUTE_CARD_L_PILL_CLEARANCE.roundToPx(), clusterOnLeft)
        val card = measurable.measure(constraints.copy(minWidth = span.widthPx, maxWidth = span.widthPx))
        layout(constraints.maxWidth, card.height) { card.place(span.startPx, 0) }
    }
}
