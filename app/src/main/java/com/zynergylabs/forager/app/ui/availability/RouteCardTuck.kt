package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.route.RouteCardTabOutline
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor

/*
 * The route card's tuck (dispatch 2026-11-06-05, Amendment 1, step 2, RECORD -2187). The owner: "Slides to the edge, tab stays"
 * ("Swipe the card sideways: it slides off to the screen edge, leaving a small tab; tap or drag the tab to bring it back"), and,
 * settling the plan, a **grip bar** tab, "24 dp visible at the card's bottom corner, 48×56 dp touch, 80% fill, the card's outline".
 * The card slides to the edge it already sits against: in portrait the edge away from the icon bar (C17), in landscape the side of
 * the corner away from the L it sits in (C18, C21). Tucked, the card reports no height, span or bounds, so Re-center goes back to
 * today's place and the navigation card's evening line gets its room back (step 2), as when the card leaves. Kept out of
 * CompactMapRoutePlotting.kt, which composes both (.claude/skills/modular-code/SKILL.md).
 */

/** How far a sideways swipe on the card must go to tuck it, or a drag on the tab away from the edge to bring it back. */
internal val ROUTE_CARD_TUCK_SWIPE: Dp = 48.dp

/** The tab's drawn width, against the screen edge (the plan: "24 dp visible"). */
internal val ROUTE_CARD_TAB_WIDTH: Dp = 24.dp

/** The tab's touch target (the plan: "48×56 dp touch"); its drawn height is the same. */
internal val ROUTE_CARD_TAB_TOUCH_WIDTH: Dp = 48.dp
internal val ROUTE_CARD_TAB_HEIGHT: Dp = 56.dp

/**
 * A sideways swipe on the card past [ROUTE_CARD_TUCK_SWIPE], either way, tucks it. Either way, so a finger that swipes toward the map
 * is not refused: the card has one edge to go to (the plan), and the owner's "Don't make me think" weighs against a direction that
 * silently does nothing. Vertical drags stay the handle's and the rows'.
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
 * The card's motion while tucking and coming back: sideways, to and from the edge it sits against ([cardOnLeft]), on the navigation
 * view chrome's timing with its fade, as the portrait card's own (`routeCardPortraitTransitions`). Under reduced motion it only fades.
 */
@Composable
internal fun routeCardTuckTransitions(cardOnLeft: Boolean): Pair<EnterTransition, ExitTransition> {
    val reduceMotion = LocalReduceMotion.current
    val slide = MotionTokens.navigationViewChromeSpec<IntOffset>()
    val timedFade = MotionTokens.navigationViewChromeSpec<Float>()
    val reducedFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val offEdge: (Int) -> Int = { fullWidth -> if (cardOnLeft) -fullWidth else fullWidth }
    val enter = if (reduceMotion) fadeIn(animationSpec = reducedFade) else slideInHorizontally(animationSpec = slide, initialOffsetX = offEdge) + fadeIn(animationSpec = timedFade)
    val exit = if (reduceMotion) fadeOut(animationSpec = reducedFade) else slideOutHorizontally(animationSpec = slide, targetOffsetX = offEdge) + fadeOut(animationSpec = timedFade)
    return enter to exit
}

/**
 * The tucked card's tab, at the card's bottom corner on its edge ([cardOnLeft]), inside [controlsPadding] and [bottomPadding] as the
 * card is. Drawn [ROUTE_CARD_TAB_WIDTH] wide against the edge, square there and round toward the map, in the map chrome's 80% fill
 * with the card's outline all the way round (Amendment 5's added item, "Same as the card"), and a short vertical grip bar in the card
 * handle's look. Its touch target is [ROUTE_CARD_TAB_TOUCH_WIDTH] by [ROUTE_CARD_TAB_HEIGHT]; the part past the drawn tab is clear, and every touch outside it reaches
 * the map. A tap, or a drag away from the edge past [ROUTE_CARD_TUCK_SWIPE], brings the card back ([onBringBack]).
 */
@Composable
internal fun BoxScope.RouteCardTab(cardOnLeft: Boolean, controlsPadding: PaddingValues, bottomPadding: Dp, onBringBack: () -> Unit) {
    val thresholdPx = with(LocalDensity.current) { ROUTE_CARD_TUCK_SWIPE.toPx() }
    val currentOnBringBack by rememberUpdatedState(onBringBack)
    var dragged by remember { mutableFloatStateOf(0f) }
    val shape: Shape = if (cardOnLeft) {
        AbsoluteRoundedCornerShape(topRight = ROUTE_CARD_CORNER, bottomRight = ROUTE_CARD_CORNER)
    } else {
        AbsoluteRoundedCornerShape(topLeft = ROUTE_CARD_CORNER, bottomLeft = ROUTE_CARD_CORNER)
    }
    val fill = mapChromeFill(navigationBarContainerColor(), overMap = true)
    Box(
        Modifier
            .align(if (cardOnLeft) Alignment.BottomStart else Alignment.BottomEnd)
            .padding(controlsPadding)
            .padding(bottom = bottomPadding)
            .size(width = ROUTE_CARD_TAB_TOUCH_WIDTH, height = ROUTE_CARD_TAB_HEIGHT)
            .testTag(ROUTE_CARD_TAB_TAG)
            .clickable(role = Role.Button, onClickLabel = "Show route") { currentOnBringBack() }
            .draggable(
                state = rememberDraggableState { dragged += it },
                orientation = Orientation.Horizontal,
                onDragStarted = { dragged = 0f },
                // Away from the edge: rightward for a tab on the left edge, leftward on the right.
                onDragStopped = { if ((if (cardOnLeft) dragged else -dragged) >= thresholdPx) currentOnBringBack() },
            ),
        contentAlignment = if (cardOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Surface(
            shape = shape,
            color = fill,
            contentColor = contentColorFor(navigationBarContainerColor()),
            modifier = Modifier
                .size(width = ROUTE_CARD_TAB_WIDTH, height = ROUTE_CARD_TAB_HEIGHT)
                .testTag(ROUTE_CARD_TAB_DRAWN_TAG)
                .mapChromeContainerColor(fill),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .mapChromeContentColor(LocalContentColor.current)
                        .size(width = 4.dp, height = 24.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(2.dp)),
                )
                RouteCardTabOutline(shape)
            }
        }
    }
}

const val ROUTE_CARD_TAB_TAG = "route-card-tab"
const val ROUTE_CARD_TAB_DRAWN_TAG = "route-card-tab-drawn"
