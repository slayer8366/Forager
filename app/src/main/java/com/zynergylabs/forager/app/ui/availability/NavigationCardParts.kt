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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExploreOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The left part, the full-height divider and the right part (dispatch 2026-10-12-08). The right part is as wide as the widest
 * evening piece ([NavigationInfoFit.rightPx]); the left takes the rest, and the divider is measured last, at the taller part's
 * height, so it always runs the parts' full height (RECORD -945).
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
    /** Whether the right part keeps its width: an evening line shown, or one still leaving. */
    reserveRight: Boolean,
    onLeftHeight: (Float) -> Unit,
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
            { EveningStack(sundownLine, backByLine, sundownVisibility, backByVisibility) },
        ),
    ) { (left, divider, right), constraints ->
        val total = constraints.maxWidth
        // The right part keeps the widest piece's width; where even that leaves the left part short (an overflow the fit
        // reports), the left part stays whole and the pieces wrap in what is left, never cut (RECORD -941).
        val wantRight = if (reserveRight) fit.rightPx else 0
        val rightWidth = if (fit.overflows) minOf(wantRight, maxOf(0, total - metrics.dividerPx - fit.leftPx)) else wantRight
        val leftWidth = maxOf(0, total - metrics.dividerPx - rightWidth)
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
 * The left part, each line centred (RECORD -945): the distance; the turn words, the needle, the north arrow and the heading on one
 * line, the two arrows neighbours (RECORD -950); the kind word, only when there is one.
 */
@Composable
private fun LeftPart(readout: NavigationHudReadout, fit: NavigationInfoFit, modifier: Modifier = Modifier) {
    val small = MaterialTheme.typography.labelMedium
    val distanceColor = if (readout.distanceDeEmphasised) LocalContentColor.current.copy(alpha = 0.5f) else LocalContentColor.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        WordSwap(text = columnFigure(readout, fit.figureShort)) { shown ->
            Text(
                text = shown,
                style = cardFigureStyle(),
                color = distanceColor,
                maxLines = 1,
                softWrap = false,
                // The route message's own tag is on its line when it moves there; the dash here is the left part's.
                modifier = if (readout.distanceText == ROUTE_UNAVAILABLE_TEXT) Modifier else Modifier.testTag(NAVIGATION_HUD_DISTANCE_TAG),
            )
        }
        Row(
            modifier = Modifier.heightIn(min = COMPASS_ICON_SIZE),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Always composed, empty when there is no turn to word (the approach, no compass, no aim), as before. Its gap is its
            // own end padding, there only with words, so an empty slot adds no gap (what [navigationInfoFit] measures).
            WordSwap(text = fit.turnText, contentAlignment = Alignment.CenterEnd) { shown ->
                Text(
                    text = shown,
                    style = small,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(end = if (shown.isEmpty()) 0.dp else Spacing.xs).testTag(NAVIGATION_HUD_TARGET_TAG),
                )
            }
            // The needle stays whatever the words beside it do (T11 fixes), dimmed when there is nothing to point at. Its tag is
            // on the unrotated square it is laid out in, so its bounds are where it sits, not its rotated drawing's box.
            Box(modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NEEDLE_TAG)) {
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = if (readout.targetArrowDegrees != null) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f),
                    modifier = Modifier.size(COMPASS_ICON_SIZE).rotate(readout.targetArrowDegrees ?: 0f),
                )
            }
            // The two arrows neighbours (RECORD -950: "The arrows being side by side is a compass visual"): one small gap.
            Spacer(Modifier.width(NAVIGATION_ARROWS_GAP))
            if (fit.headingAsIcon) {
                // RECORD -934: a compass status whose words do not fit takes RECORD -735's crossed-out compass, its words kept as
                // the icon's description for a screen reader.
                Icon(
                    imageVector = Icons.Filled.ExploreOff,
                    contentDescription = readout.headingText,
                    modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NORTH_ARROW_TAG),
                )
            } else {
                // The north compass, right after the needle (RECORD -950): rotated by minus the heading, as before.
                Box(modifier = Modifier.size(COMPASS_ICON_SIZE).testTag(NAVIGATION_HUD_NORTH_ARROW_TAG)) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(COMPASS_ICON_SIZE).rotate(readout.northArrowDegrees ?: 0f),
                    )
                }
                // Motion Part 2, item 6 (RECORD -651: "Numbers instant, words fade"). With no fix and only the dash to say, the
                // heading is not drawn (NavigationHudReadout.secondRowShown).
                if (readout.secondRowShown) {
                    Spacer(Modifier.width(Spacing.xs))
                    WordSwap(text = readout.headingText) { shown ->
                        Text(
                            text = shown,
                            style = small.copy(fontFeatureSettings = "tnum"),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag(NAVIGATION_HUD_HEADING_TAG),
                        )
                    }
                }
            }
        }
        // RECORD -945 ("by trail under figure"): the kind word, a small line of its own under the turn line, only when there is one.
        readout.distanceKindText?.let { kind -> DistanceKindText(kind, distanceColor) }
    }
}

/** What the large figure measures, "by trail" or "straight", in the status line's type (dispatch -677; RECORD -713). */
@Composable
private fun DistanceKindText(kind: String, color: Color, modifier: Modifier = Modifier) {
    WordSwap(text = kind, modifier = modifier) { shown ->
        Text(
            text = shown,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_KIND_TAG),
        )
    }
}

internal val COMPASS_ICON_SIZE = 22.dp

/** Dispatch 2026-10-12-08: the gap between the turn needle and the north arrow, neighbours on one line (RECORD -950). */
private val NAVIGATION_ARROWS_GAP = Spacing.xs

/** Dispatch 2026-10-12-06: the card's column dividers, thin lines with this gap either side (6 dp, RECORD -936's tighter spacing). */
internal val CARD_DIVIDER_WIDTH = 1.dp
internal val CARD_DIVIDER_GAP = 6.dp
