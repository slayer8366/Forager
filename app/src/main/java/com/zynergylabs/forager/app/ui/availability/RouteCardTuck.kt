package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_STRIP
import com.zynergylabs.forager.app.ui.route.RouteCardTuckDraw

/*
 * The route card's tuck (dispatch 2026-11-06-05, Amendment 1, step 2, RECORD -2187). The owner: "Slides to the edge, tab stays"
 * ("Swipe the card sideways: it slides off to the screen edge, leaving a small tab; tap or drag the tab to bring it back"). The card
 * slides to the edge it already sits against: in portrait the edge away from the icon bar (C17), in landscape the side of the corner
 * away from the L it sits in (C18, C21). Kept out of CompactMapRoutePlotting.kt, which composes it (.claude/skills/modular-code/SKILL.md).
 *
 * Amendment 6, A2 (RECORD -2187), the owner: "Have the drag bar be the same size as the card." and "It should animate to the edge and
 * meet the bar that appears, so it doesn't show the bar appearing, it just shows the card moving to the edge and staying there, with
 * the no size change". So the card itself slides, one continuous movement, until a 24 dp strip of it is left at its edge at its full
 * height, its words and buttons fading out as it goes ("Fade (Recommended)") and the grip fading in (`RouteCardFace.kt`); tap or drag
 * the strip and it slides back, the content fading in. No separate tab appears or goes. Replaced Amendment 1's tab (24 dp drawn, 56 dp
 * tall, at the card's bottom corner), which appeared as the card started to leave, and the card's slide right off the screen.
 *
 * Tucked, the card keeps reporting its height, span and bounds, as when it shows (the owner: "Stays aside (Recommended)": the
 * navigation card's evening line stays stepped aside and the strip keeps its exact height and place, the line returning only on ✕;
 * "Stays under (Recommended)": in portrait Re-center stays under the strip). That replaced Amendment 1 step 2's "Tucking gives the
 * navigation card's evening line its room back". The map's credit "i" alone is handed only what is drawn, the strip once tucked, so it
 * comes back where the card rested (the owner: "Come back"; RouteCardAttributionCover.kt).
 */

/** How far a sideways swipe on the card must go to tuck it, or a drag on the strip away from the edge to bring it back. */
internal val ROUTE_CARD_TUCK_SWIPE: Dp = 48.dp

/**
 * The strip's touch target, from the edge: Material's 48 dp minimum, as the tab's was (the plan: "48×56 dp touch"), now at the card's
 * full height, as the strip is. The part past the 24 dp strip is clear map, and takes the touch only to bring the card back.
 */
internal val ROUTE_CARD_STRIP_TOUCH_WIDTH: Dp = 48.dp

/**
 * A sideways swipe on the card past [ROUTE_CARD_TUCK_SWIPE], either way, tucks it. Either way, so a finger that swipes toward the map
 * is not refused: the card has one edge to go to (the plan), and the owner's "Don't make me think" weighs against a direction that
 * silently does nothing. Vertical drags stay the handle's and the rows'. Not applied while the card is tucked: its strip takes the
 * touches, and a draggable, even disabled, would keep the card's whole bounds from the map.
 */
@Composable
internal fun Modifier.routeCardTuckSwipe(onTuck: () -> Unit): Modifier {
    val thresholdPx = with(LocalDensity.current) { ROUTE_CARD_TUCK_SWIPE.toPx() }
    val currentOnTuck by rememberUpdatedState(onTuck)
    var dragged by remember { mutableFloatStateOf(0f) }
    return draggable(
        state = rememberDraggableState { dragged += it },
        orientation = Orientation.Horizontal,
        onDragStarted = { dragged = 0f },
        onDragStopped = { if (kotlin.math.abs(dragged) >= thresholdPx) currentOnTuck() },
    )
}

/**
 * A2: the card's tuck as it moves, toward its edge ([cardOnLeft]) while [tucked]: the slide on the navigation view chrome's timing
 * (the card's own motion's, `routeCardPortraitTransitions`), and the content's fade with it. Under reduced motion the card is at once
 * where it ends, and only the content fades, as the card's own motion only fades there. Composed with the card, so a card opened
 * afresh starts where its state says, with no motion.
 */
@Composable
internal fun rememberRouteCardTuckDraw(tucked: Boolean, cardOnLeft: Boolean): RouteCardTuckDraw {
    val reduceMotion = LocalReduceMotion.current
    val progress by animateFloatAsState(
        targetValue = if (tucked) 1f else 0f,
        animationSpec = if (reduceMotion) MotionTokens.mapPopUpFadeSpec() else MotionTokens.navigationViewChromeSpec(),
        label = "route-card-tuck",
    )
    val slide = if (reduceMotion) (if (tucked) 1f else 0f) else progress
    return RouteCardTuckDraw(slide = slide, faded = progress, towardLeft = cardOnLeft, tucked = tucked)
}

/**
 * A2: the tucked card's strip takes the touches over its card's edge ([cardOnLeft]): a tap, or a drag away from the edge past
 * [ROUTE_CARD_TUCK_SWIPE], brings the card back ([onBringBack]). [ROUTE_CARD_STRIP_TOUCH_WIDTH] from the edge, at the card's full
 * height; every touch outside it reaches the map (the card's own shape is clipped to the strip while it is tucked). Drawn by the card
 * (`RouteCardFace.kt`); [ROUTE_CARD_TAB_DRAWN_TAG] marks where the strip is drawn, and draws nothing.
 */
@Composable
internal fun BoxScope.RouteCardStripTouch(cardOnLeft: Boolean, onBringBack: () -> Unit) {
    val thresholdPx = with(LocalDensity.current) { ROUTE_CARD_TUCK_SWIPE.toPx() }
    val currentOnBringBack by rememberUpdatedState(onBringBack)
    var dragged by remember { mutableFloatStateOf(0f) }
    val edge = if (cardOnLeft) Alignment.CenterStart else Alignment.CenterEnd
    Box(Modifier.matchParentSize()) {
        Box(Modifier.align(edge).width(ROUTE_CARD_STRIP).fillMaxHeight().testTag(ROUTE_CARD_TAB_DRAWN_TAG))
        Box(
            Modifier
                .align(edge)
                .width(ROUTE_CARD_STRIP_TOUCH_WIDTH)
                .fillMaxHeight()
                .testTag(ROUTE_CARD_TAB_TAG)
                .clickable(role = Role.Button, onClickLabel = "Show route") { currentOnBringBack() }
                .draggable(
                    state = rememberDraggableState { dragged += it },
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragged = 0f },
                    // Away from the edge: rightward for a strip on the left edge, leftward on the right.
                    onDragStopped = { if ((if (cardOnLeft) dragged else -dragged) >= thresholdPx) currentOnBringBack() },
                ),
        )
    }
}

const val ROUTE_CARD_TAB_TAG = "route-card-tab"
const val ROUTE_CARD_TAB_DRAWN_TAG = "route-card-tab-drawn"
