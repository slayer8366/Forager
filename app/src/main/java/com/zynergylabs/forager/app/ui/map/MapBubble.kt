package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.availability.rectEdgeIntersection
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.atan2

/**
 * M1's bubble shell and the layer every map host puts over its map (`prompts/preserved/2026-09-28-29.md`
 * B2 to B4, with continuation `2026-09-28-30`, which widened the scope so the shell could be extracted
 * from the sighting bubble and its tail fixed). What each bubble says is `MapBubbles.kt`.
 *
 * - [AnchoredAtScreenPoint] places a bubble so its tail's tip is on the tapped point, moved here from
 *   `AvailabilityMapOverlaysUi.kt` with the one fix the planner ruled: the tip is on the anchor even
 *   when the clamp moves the card, because the tail is drawn from the card's edge to the anchor
 *   itself ([bubblePlacement]) instead of from where the card would have been.
 * - [MapBubbleShell] is the card: the map chrome's 80% fill, the tail, a close button and a content
 *   slot. It swallows taps on itself, so a touch on a bubble never reaches the map beneath.
 *   `ObservationBubble` (the sighting bubble, content unchanged) is one kind of it; [MapFeatureBubble]
 *   is every other kind.
 * - [MapBubbleLayer] is one bubble at a time for one host, with its targets opened in place (owner's
 *   M1 ruling 2, "Open in place"): the J5c details sheet, the photo viewer, Directions, and the find
 *   route the host passes in [MapRecordSources.onOpenFind].
 */

/**
 * The direction a bubble sits from the point it names, measured clockwise from up, in the map's own
 * north-up frame, not screen space (unchanged from the sighting bubble): [AnchoredAtScreenPoint]
 * rotates it by the camera bearing, so up-and-left of the point at bearing 0 stays up-and-left of the
 * point as the map turns.
 */
private const val BUBBLE_BASE_DIRECTION_DEG = 315f

/** How far the tail's tip reaches past the card toward the point, unclamped; also the margin the shell reserves on every side. */
internal val BUBBLE_TAIL_LENGTH = Spacing.md

/** Half the width of the tail's base. */
private val BUBBLE_TAIL_BASE_HALF_WIDTH = Spacing.xs

/**
 * Places [content] so its tail's tip lands on [anchorPx], the tapped point in the map slot's own
 * coordinates, at [BUBBLE_BASE_DIRECTION_DEG] rotated by [bearingDeg], clamped to the map box and no
 * higher than [minY]. [content] gets the tip, in its own coordinates, as a [State] written during
 * placement, and draws its tail to it: so when the clamp moves the card near an edge, the tail moves
 * with the anchor, not with the card (planner's M1 ruling), and the tip still touches the point.
 *
 * [insetLeft] and [insetRight] narrow the box the card is clamped into on each side, for chrome that sits over
 * the map there (the landscape L and the overlaid rail; dispatch 2026-09-29-57, amendment -262, item 3, the owner's
 * "Push the card clear"): the card moves, the tip stays on the anchor. Zero for a host with no such chrome.
 *
 * A custom [Layout] reporting the whole incoming size, with the child placed freely inside it, for
 * the reason the sighting bubble's placement always had: placing a child outside its parent's own
 * bounds would leave it undependably hit-testable. The layout itself takes no touches; only the
 * shell's card does.
 */
@Composable
internal fun AnchoredAtScreenPoint(
    anchorPx: Offset,
    bearingDeg: Float,
    modifier: Modifier = Modifier,
    minY: Dp = 0.dp,
    insetLeft: Dp = 0.dp,
    insetRight: Dp = 0.dp,
    content: @Composable (tipInBubble: State<Offset?>) -> Unit,
) {
    val screenDirectionFromPointDeg = (BUBBLE_BASE_DIRECTION_DEG - bearingDeg).mod(360f)
    val arrowAngleDeg = (screenDirectionFromPointDeg + 180f).mod(360f)
    val tip: MutableState<Offset?> = remember { mutableStateOf(null) }
    Layout(content = { content(tip) }, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val placement = bubblePlacement(
            anchor = anchorPx,
            arrowAngleDeg = arrowAngleDeg,
            bubbleWidth = placeable.width,
            bubbleHeight = placeable.height,
            tailPx = BUBBLE_TAIL_LENGTH.toPx(),
            maxWidth = constraints.maxWidth - insetRight.roundToPx(),
            maxHeight = constraints.maxHeight,
            minY = minY.roundToPx(),
            minX = insetLeft.roundToPx(),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Read by the shell's tail at draw time, after this placement.
            tip.value = placement.tipInBubble
            placeable.placeRelative(placement.topLeftX, placement.topLeftY)
        }
    }
}

/**
 * The bubble's card (B2): the same theme-aware 80% fill as every control over the map, a tail from
 * the card's edge to [tipInBubble], [content] beside a close button, at most 280 dp wide. [cardTag]
 * is the card's test tag and [closeTag] the close button's.
 *
 * The card consumes its own taps with a plain `pointerInput` (no ripple, no click action to fake),
 * so a touch anywhere on it is never also the map's "tap elsewhere" dismiss; the buttons inside it
 * take their own taps first.
 */
@Composable
internal fun MapBubbleShell(
    tipInBubble: State<Offset?>,
    onDismiss: () -> Unit,
    cardTag: String,
    closeTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val fillColor = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    Box(
        modifier = modifier
            .drawBehind {
                val tip = tipInBubble.value ?: return@drawBehind
                val tailPx = BUBBLE_TAIL_LENGTH.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val toTip = tip - center
                val cardHalfWidth = center.x - tailPx
                val cardHalfHeight = center.y - tailPx
                // A tip inside the card (the clamp pushed the card over the point) needs no tail.
                if (abs(toTip.x) <= cardHalfWidth && abs(toTip.y) <= cardHalfHeight) return@drawBehind
                val angleDeg = Math.toDegrees(atan2(toTip.x.toDouble(), -toTip.y.toDouble())).toFloat()
                val base = center + rectEdgeIntersection(cardHalfWidth, cardHalfHeight, angleDeg)
                val length = (tip - base).getDistance().takeIf { it > 0f } ?: return@drawBehind
                val dir = (tip - base) / length
                val half = BUBBLE_TAIL_BASE_HALF_WIDTH.toPx()
                val base1 = Offset(base.x - dir.y * half, base.y + dir.x * half)
                val base2 = Offset(base.x + dir.y * half, base.y - dir.x * half)
                drawPath(
                    Path().apply {
                        moveTo(base1.x, base1.y)
                        lineTo(tip.x, tip.y)
                        lineTo(base2.x, base2.y)
                        close()
                    },
                    color = fillColor,
                )
            }
            .padding(BUBBLE_TAIL_LENGTH),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag(cardTag)
                .mapChromeContainerColor(fillColor)
                .pointerInput(Unit) { detectTapGestures {} },
            shape = RoundedCornerShape(20.dp),
            color = fillColor,
            contentColor = if (isDarkTheme) Color.White else Bark,
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
        ) {
            Row(
                // fillMaxWidth so the Column's weight(1f) has a bounded width to share (the sighting
                // bubble's own finding: without it the text wrapped a character per line).
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
                BouncingIconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).testTag(closeTag)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
