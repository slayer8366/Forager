package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.layout.Layout
import com.zynergylabs.forager.app.ui.motion.WordSwap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The left part, the full-height divider and the right part (dispatch 2026-10-12-08). The right part is as wide as the widest
 * evening piece ([NavigationInfoFit.rightPx]), and since Amendment 1, H2 and H5, never narrower than the evening line's widest on the
 * phone's clock, evening line or none; the left takes the rest, and the divider is measured last, at the taller part's height, so it
 * always runs the parts' full height (RECORD -945; H2: "the divider runs its full height").
 */
@Composable
internal fun InfoParts(
    readout: NavigationHudReadout,
    fit: NavigationInfoFit,
    metrics: InfoMetrics,
    sundownLine: String?,
    backByLine: String?,
    sundownVisibility: MutableTransitionState<Boolean>,
    backByVisibility: MutableTransitionState<Boolean>,
    /** H5: whether the evening line breaks at the owner's ▪︎ ([CardEvening.breaks]). */
    eveningBreaks: Boolean,
    /** Whether an evening line is shown, or one is still leaving. */
    eveningHeld: Boolean,
    /** H8: whether the status words are in the right part; `false` where they have their own line under the parts. */
    statusInRight: Boolean,
    onLeftHeight: (Float) -> Unit,
    onStatusHeight: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dividerColor = if (LocalForagerDarkTheme.current) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT
    Layout(
        modifier = modifier,
        contents = listOf(
            { LeftPart(readout, fit, Modifier.onGloballyPositioned { onLeftHeight(it.size.height.toFloat()) }) },
            {
                Box(
                    modifier = Modifier
                        .padding(horizontal = CARD_DIVIDER_GAP)
                        .width(CARD_DIVIDER_WIDTH)
                        .background(dividerColor)
                        .testTag(NAVIGATION_HUD_DIVIDER_TAG),
                )
            },
            { RightPart(readout, sundownLine, backByLine, sundownVisibility, backByVisibility, eveningBreaks, eveningHeld, statusInRight, onStatusHeight) },
        ),
    ) { (left, divider, right), constraints ->
        val total = constraints.maxWidth
        // The right part keeps its width; where even that leaves the left part short (an overflow the fit reports), the left part
        // stays whole and the right part's lines wrap in what is left, never cut (RECORD -941). One function with the top line's fit,
        // so the kind is placed on the width drawn here (dispatch 2026-11-05-10).
        val leftWidth = leftPartWidthPx(total, metrics.dividerPx, fit)
        val rightWidth = maxOf(0, total - metrics.dividerPx - leftWidth)
        val leftPlaceable = left.single().measure(constraints.copy(minWidth = leftWidth, maxWidth = leftWidth, minHeight = 0))
        val rightPlaceable = right.single().measure(constraints.copy(minWidth = rightWidth, maxWidth = rightWidth, minHeight = 0))
        val height = maxOf(leftPlaceable.height, rightPlaceable.height)
        val dividerPlaceable = divider.single().measure(
            constraints.copy(minWidth = metrics.dividerPx, maxWidth = metrics.dividerPx, minHeight = height, maxHeight = height),
        )
        layout(total, height) {
            leftPlaceable.place(0, 0)
            dividerPlaceable.place(leftWidth, 0)
            rightPlaceable.place(leftWidth + metrics.dividerPx, 0)
        }
    }
}

/**
 * The left part: the distance and its kind on the top line ([CardTopLine]; dispatch 2026-11-05-10, RECORD -2181: "move the "by
 * trail" from the bottom to the top right half, on the same line as 2.2 mi"); under it the turn line ([CardTurnLine]). Nothing under
 * the turn line. Was (RECORD -945) the distance, the turn line and the kind word ("by trail under figure"), each centred.
 */
@Composable
private fun LeftPart(readout: NavigationHudReadout, fit: NavigationInfoFit, modifier: Modifier = Modifier) {
    val distanceColor = if (readout.distanceDeEmphasised) LocalContentColor.current.copy(alpha = 0.5f) else LocalContentColor.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CardTopLine(readout, fit, distanceColor)
        CardTurnLine(readout, fit)
    }
}

/**
 * The turn line, centred in the left part, Amendment 1 (RECORD -2181):
 * - G2, the owner: "Drop the words, keep the degrees (Recommended)": "109°", never "right 109°";
 * - G3, "The turn line": "The degrees, the needle, the north arrow and the heading each sit in a fixed space, so the compass never
 *   jumps": the degrees are centred in a space as wide as the widest degrees ([NavigationInfoFit.turnSpacePx]), held empty where there
 *   are none (the approach, no compass, no aim), so the needle stays where it is. A word in their place ("Target" with no fix, "No
 *   target"; the planner's reading of Q5: not turn words, so they stay) is centred on the same space, as wide as it is;
 * - H1, the owner: "Keep the compass strip the exact same when in navigation mode as out; so keep the north compass out of the
 *   navigation panel": the north arrow and the heading are the strip's. Was (RECORD -950): the turn words, the needle, then the north
 *   arrow and the heading, "The arrows being side by side is a compass visual"; and the crossed-out compass for a compass status
 *   that did not fit (RECORD -934).
 */
@Composable
private fun CardTurnLine(readout: NavigationHudReadout, fit: NavigationInfoFit) {
    val small = MaterialTheme.typography.labelMedium
    val turnSpace = with(LocalDensity.current) { fit.turnSpacePx.toDp() }
    Row(
        modifier = Modifier.heightIn(min = COMPASS_ICON_SIZE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(turnSpace), contentAlignment = Alignment.Center) {
            WordSwap(text = fit.turnText, modifier = Modifier.wrapContentWidth(unbounded = true), contentAlignment = Alignment.Center) { shown ->
                Text(
                    text = shown,
                    style = small,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag(NAVIGATION_HUD_TARGET_TAG),
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        // The needle stays whatever the degrees beside it do (T11 fixes), dimmed when there is nothing to point at. Its tag is on the
        // unrotated square it is laid out in, so its bounds are where it sits, not its rotated drawing's box.
        Box(modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NEEDLE_TAG)) {
            Icon(
                imageVector = Icons.Filled.Navigation,
                contentDescription = null,
                tint = if (readout.targetArrowDegrees != null) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f),
                modifier = Modifier.size(COMPASS_ICON_SIZE).rotate(readout.targetArrowDegrees ?: 0f),
            )
        }
    }
}

internal val COMPASS_ICON_SIZE = 22.dp

/** Dispatch 2026-10-12-06: the card's column dividers, thin lines with this gap either side (6 dp, RECORD -936's tighter spacing). */
internal val CARD_DIVIDER_WIDTH = 1.dp
internal val CARD_DIVIDER_GAP = 6.dp
