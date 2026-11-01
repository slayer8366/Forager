package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import kotlin.math.roundToInt

/**
 * Where the record and return pill's bottom sits while the column is folded for the route card, in the content box's px: where
 * the pill sits in the column ([columnBottomPx], the column's own bottom), raised only as far as the lowest edge the column keeps
 * above ([lowestEdgePx]: the card's top or the legend chip's, less the gap; `null` for none). The top limit wins where they meet,
 * as it does for the column.
 */
internal fun heldPillBottomPx(columnBottomPx: Float, lowestEdgePx: Float?, pillHeightPx: Float, topLimitPx: Float): Float {
    val bottomPx = if (lowestEdgePx != null) minOf(columnBottomPx, lowestEdgePx) else columnBottomPx
    return maxOf(bottomPx, topLimitPx + pillHeightPx)
}

/**
 * The record and return pill on its own, while the route card holds the column folded (dispatch 2026-10-30-08, Amendment 11,
 * RECORD -1909, the owner: "Keep the pill showing (Recommended)"): stop and return stay reachable, on the column's side and at its
 * place, raised clear of the card. Not shown for a column the user minimised themselves, whose pill stays folded as they left it.
 * A sibling of the column, not part of it: the column's container is unmounted while folded. It writes nothing to the cluster's
 * state, so the column comes back exactly as the user had it.
 */
@Composable
internal fun BoxScope.RouteCardHeldPill(
    visible: Boolean,
    state: MapIconClusterState,
    lowestEdgePx: Float?,
    topLimitPx: Float,
    controlsPadding: PaddingValues,
    pill: @Composable (onLeftSide: Boolean) -> Unit,
) {
    var pillHeightPx by remember { mutableFloatStateOf(0f) }
    val slide: (Int) -> Int = { fullWidth -> if (state.isOnLeftSide) -fullWidth else fullWidth }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), initialOffsetX = slide),
        exit = slideOutHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), targetOffsetX = slide),
        modifier = Modifier
            .align(state.sideAlignment)
            .padding(controlsPadding)
            .offset {
                // Centred in the box before this offset, as the column is: the column's bottom is the same arithmetic as its clamps.
                val boxPx = state.mapContentBoxHeightPx
                val columnBottomPx = (boxPx + state.clusterHeightPx) / 2f + state.displayedOffsetPx.value
                val bottomPx = heldPillBottomPx(columnBottomPx, lowestEdgePx, pillHeightPx, topLimitPx)
                IntOffset(0, (bottomPx - pillHeightPx / 2f - boxPx / 2f).roundToInt())
            },
    ) {
        Box(
            modifier = Modifier
                .padding(MAP_ICON_BAR_EDGE_INSET)
                .mapKeepOut(MapKeepOutIds.HELD_PILL)
                .onSizeChanged { pillHeightPx = it.height.toFloat() },
        ) {
            pill(state.isOnLeftSide)
        }
    }
}
