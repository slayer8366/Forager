package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TOP_PADDING_FRACTION
import com.zynergylabs.forager.app.ui.map.NavigationSideInsets

/*
 * Dispatch 2026-11-06-01, Amendment 1, J8 (RECORD -2183). Sorrel's review: in landscape at font 2.0 with Try again and both evening
 * lines, the navigation card has no room to move up (J5: NoRoom), stays on the bottom edge and covers the walker's dot. The owner:
 * "No, fix it", then "Map moves dot up (Recommended)" ("The map keeps your position just above the card's top; the card stays
 * whole"); with the route card resting on the navigation card, "(b) Above top card (Recommended)"; "(a) Landscape only".
 *
 * While navigating in landscape, where the card on top (the navigation card, or what rests on it) would cover the dot, the navigation
 * view's bottom padding is just enough that the dot's centre sits [WALKER_DOT_CLEARANCE] above that card's resting top; else 0. It
 * goes to the camera through D29's padding (NavigationSideInsets.bottomPx, the shift effect in MapNavigationView), so the dot glides
 * there and back on the view's own timing. Only the camera moves: the card's own decisions read the dot where the view puts it with no
 * bottom padding ([walkerDotCentreInRootPx]), so this cannot feed back into the card.
 */
private const val LOG_TAG = "NavigationDotAboveCard"

/**
 * The bottom padding, in map px, for a map [mapWidthPx] x [mapHeightPx], with D29's [side] padding (which places the dot across).
 * [cardInMap] is the navigation card's resting bounds in the map's px; [topCardTopInMap] the resting top of what rests on it, or `null`
 * for the card's own top. 0 where the card on top overlaps none of the dot's square ([clearancePx] each way), across and up and down.
 * [DotAboveCard.capped] where the dot cannot rise that far within the view's top padding, which then holds it at the top padding's
 * edge plus its clearance.
 */
internal fun navigationDotAboveCardBottomPx(
    mapWidthPx: Float,
    mapHeightPx: Float,
    side: NavigationSideInsets,
    cardInMap: Rect?,
    topCardTopInMap: Float?,
    clearancePx: Float,
): DotAboveCard {
    if (cardInMap == null || mapWidthPx <= 0f || mapHeightPx <= 0f) return DotAboveCard.NONE
    val topPx = mapHeightPx * NAVIGATION_VIEW_TOP_PADDING_FRACTION.toFloat()
    val dotX = side.leftPx.toFloat() + (mapWidthPx - side.leftPx.toFloat() - side.rightPx.toFloat()) / 2f
    val dotY = (mapHeightPx + topPx) / 2f
    val top = topCardTopInMap ?: cardInMap.top
    val across = cardInMap.left < dotX + clearancePx && cardInMap.right > dotX - clearancePx
    val upAndDown = top < dotY + clearancePx && cardInMap.bottom > dotY - clearancePx
    if (!across || !upAndDown) return DotAboveCard.NONE
    val wanted = 2f * (dotY - (top - clearancePx))
    val most = 2f * (dotY - topPx - clearancePx)
    return if (wanted > most) DotAboveCard(most.coerceAtLeast(0f).toDouble(), capped = true) else DotAboveCard(wanted.toDouble(), capped = false)
}

internal data class DotAboveCard(val bottomPx: Double, val capped: Boolean) {
    companion object {
        val NONE = DotAboveCard(0.0, capped = false)
    }
}

/** [navigationDotAboveCardBottomPx] from what the cards write into [cluster], in the root, moved into the map's px. */
internal fun navigationDotAboveCardBottomPx(cluster: MapIconClusterState, side: NavigationSideInsets, clearancePx: Float): DotAboveCard {
    val dx = -cluster.mapContentBoxLeftInRootPx
    val dy = -cluster.mapContentBoxTopInRootPx
    return navigationDotAboveCardBottomPx(
        mapWidthPx = cluster.mapContentBoxWidthPx,
        mapHeightPx = cluster.mapContentBoxHeightPx,
        side = side,
        cardInMap = cluster.navigationCardRestingBoundsInRoot?.translate(dx, dy),
        topCardTopInMap = cluster.navigationRestingOnCardTopInRootPx?.let { it + dy },
        clearancePx = clearancePx,
    )
}

/**
 * The navigation view's padding with J8's bottom added to D29's [side], where [applies] (navigating, landscape); [side] as it is
 * otherwise. Logs when the dot cannot rise far enough (CLAUDE.md: no unlogged fallback).
 */
@Composable
internal fun navigationInsetsWithDotAboveCard(side: NavigationSideInsets, applies: Boolean, cluster: MapIconClusterState): NavigationSideInsets {
    val clearancePx = with(LocalDensity.current) { WALKER_DOT_CLEARANCE.toPx() }
    val above = if (applies) navigationDotAboveCardBottomPx(cluster, side, clearancePx) else DotAboveCard.NONE
    LaunchedEffect(above.capped) {
        if (above.capped) Log.i(LOG_TAG, "J8: the card on top reaches above the view's top padding; the dot is held at its edge, ${above.bottomPx} px")
    }
    return if (above.bottomPx > 0.0) side.copy(bottomPx = above.bottomPx) else side
}
