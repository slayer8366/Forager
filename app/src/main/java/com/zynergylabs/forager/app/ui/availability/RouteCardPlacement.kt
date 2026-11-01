package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.max
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.theme.Spacing

/*
 * Where the route card sits beside the icon bar (dispatch 2026-10-30-08, Amendments 14 and 15, RECORD -1928, -1929). The owner: "The
 * map icon bar stays the same no matter what. The card just gets resized to fit the map icon bar next to it". The card reads the
 * bar; nothing here is told to the bar. That is the whole of the rule: Amendment 3's raising, Amendment 10's fold and Amendment 11's
 * held pill all went through the bar, and each had a state the bar was not where the user left it.
 */

/** The gap between the card's side edge and the bar, and the card's margin from the far screen edge. */
internal val ROUTE_CARD_SIDE_MARGIN: Dp = Spacing.sm

/**
 * How much of the map's width the bar's column takes: its edge inset, its width and the gap. The width is the bar's measured one,
 * kept while it is minimised, so a minimised bar leaves the card as it was (the restore handle is narrower than the bar, and takes
 * less of that column); at least [MIN_TOUCH_TARGET], the bar's width, for a bar minimised since before it was first measured.
 */
internal fun routeCardBarColumn(barWidth: Dp): Dp = MAP_ICON_BAR_EDGE_INSET + max(barWidth, MIN_TOUCH_TARGET) + ROUTE_CARD_SIDE_MARGIN

/** The card's bottom corner in portrait: the one away from the bar. */
internal fun routeCardPortraitAlignment(barOnLeft: Boolean): Alignment = if (barOnLeft) Alignment.BottomEnd else Alignment.BottomStart

/** The card's side padding in portrait: the bar's column on its side, the margin on the other. */
@Composable
internal fun routeCardPortraitPadding(cluster: MapIconClusterState): PaddingValues {
    val barColumn = routeCardBarColumn(with(LocalDensity.current) { cluster.clusterWidthPx.toDp() })
    return if (cluster.isOnLeftSide) {
        PaddingValues(start = barColumn, end = ROUTE_CARD_SIDE_MARGIN)
    } else {
        PaddingValues(start = ROUTE_CARD_SIDE_MARGIN, end = barColumn)
    }
}

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

/** How far inboard the legend chip sits while the card shows: past the bar in portrait with the bar on its side, as in the L. */
internal fun routeCardLegendEndPadding(barOnRight: Boolean, barWidth: Dp): Dp = if (barOnRight) MAP_ICON_BAR_EDGE_INSET + barWidth + Spacing.sm else Spacing.sm

