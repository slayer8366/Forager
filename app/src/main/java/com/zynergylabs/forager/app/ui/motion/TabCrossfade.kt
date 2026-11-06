package com.zynergylabs.forager.app.ui.motion

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.roundToInt

/*
 * Motion Part 2, items 1 and 2 (dispatch 2026-09-28-666; the owner, RECORD -651: tabs "Quick crossfade", bottom bar "Fade with
 * the tab": "Solid to 80% and back fades in time with the tab change").
 *
 * **What has to hold while two tabs are on screen at once.** A crossfade keeps the outgoing tab composed for the length of the
 * fade, and the Maps tab hosts the live map, which nothing may re-measure (AvailabilityCompactScaffold's comments on the map's
 * measured height). Two things would re-measure it: the bottom bar appearing as the Maps tab leaves (the content area gets
 * shorter by the bar's height), and in landscape the rail beside the content appearing (narrower). So the outgoing tab is held
 * ([holdWhileLeaving]) at the size it had, and where it was on screen, until it has gone: it is not measured again with the new
 * room. And from its first leaving frame it takes no touch ([leavingTakesNoTouches]), so a touch on bare background of the
 * incoming tab cannot reach a map that is fading away beneath it.
 *
 * **Not verified anywhere** (nothing in this change has been compiled): what a SurfaceView-backed MapLibre map looks like under a
 * Compose alpha fade. Its surface is composited by the system, not drawn by Compose, so the fade may not apply to it (inferred
 * from how SurfaceView works, not observed). That, and the cost of keeping a second map alive for the fade, are the S22's.
 */

/** Whether a tab's content is the one leaving a [TabCrossfade], readable in tests. */
val TabLeavingKey = SemanticsPropertyKey<Boolean>("TabLeaving")
var SemanticsPropertyReceiver.tabLeaving by TabLeavingKey

/**
 * One tab's content at a time, crossfading to the next when [targetState] changes, on [MotionTokens.tabCrossfadeSpec]. Under
 * reduced motion ([LocalReduceMotion]) the change is instant, as before this existed, and no outgoing tab is kept.
 *
 * [content] must draw from the tab it is handed, never from the caller's current tab: while two are on screen the outgoing one
 * is drawn from its own value.
 */
@Composable
fun <T> TabCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = MotionTokens.tabCrossfadeSpec<Float>()
    val inertBack = rememberInertBackOwner()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                // No size transform: both tabs fill the same box, and a size animation would re-measure them.
                (fadeIn(animationSpec = fade) togetherWith fadeOut(animationSpec = fade)).using(null)
            }
        },
        contentAlignment = Alignment.TopStart,
        label = "tabCrossfade",
    ) { tab ->
        val leaving = tab != targetState
        // Amendment 1 (RECORD -672), item 5: from the moment a tab starts to leave its Back handlers are off, so a Back during
        // the fade acts on the arriving tab (or the screen), never on one already going. Done by handing the leaving tab Back
        // dispatchers nothing presses, so its BackHandlers re-register there. Both locals: activity-compose 1.13's BackHandler
        // uses the navigation-event owner when there is one and the OnBackPressed owner otherwise (read from its bytecode).
        // One provider call either way (an empty set while not leaving), so starting to leave does not rebuild the tab.
        val inertProvided: Array<ProvidedValue<*>> = if (leaving) {
            arrayOf(
                LocalNavigationEventDispatcherOwner provides inertBack,
                LocalOnBackPressedDispatcherOwner provides inertBack,
            )
        } else {
            emptyArray<ProvidedValue<*>>()
        }
        CompositionLocalProvider(*inertProvided) {
            // RECORD -691: no minimum touch target while it leaves (NoTouchTargetExpansion).
            NoTouchTargetExpansion(active = leaving) {
            Box(
                Modifier
                    .holdWhileLeaving(leaving)
                    .leavingTakesNoTouches(leaving)
                    // Amendment 2 (the planner, after the build): from its first leaving frame the outgoing tab is gone from the
                    // semantics tree too, so a screen reader (or a test) finds one of each control, the arriving one, never the
                    // one that takes no touch. Only the marker that it is leaving stays.
                    .then(if (leaving) Modifier.clearAndSetSemantics { tabLeaving = true } else Modifier.semantics { tabLeaving = false }),
            ) { content(tab) }
            }
        }
    }
}

/** Back dispatchers that nothing presses, for a leaving tab's handlers (see [TabCrossfade]), and a leaving page's ([PageSlide]). Shares the screen's lifecycle. */
internal class InertBack(private val lifecycleOwner: LifecycleOwner) : OnBackPressedDispatcherOwner, NavigationEventDispatcherOwner {
    override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()
    override val navigationEventDispatcher: NavigationEventDispatcher = NavigationEventDispatcher()
    override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
}

@Composable
internal fun rememberInertBackOwner(): InertBack {
    val lifecycleOwner = LocalLifecycleOwner.current
    return remember(lifecycleOwner) { InertBack(lifecycleOwner) }
}

/**
 * While [leaving], measures what follows with the constraints it last had and keeps it where it last was on screen, so a
 * change in the room around it (a bar or rail appearing) neither re-measures it nor moves it. While not leaving it is a plain
 * pass-through: measured with the constraints it is given and placed where its parent puts it.
 *
 * Where it was is kept by placement, not by drawing, so a hosted `View` (the map's `AndroidView`) moves with what Compose draws.
 * The offset is read from where its parent put it, which is reported after placement, so the first leaving frame can be one
 * frame late to correct a move.
 */
@Composable
fun Modifier.holdWhileLeaving(leaving: Boolean): Modifier {
    val held = remember { HeldFrame() }
    return this
        .onPlaced { coordinates ->
            val at = coordinates.positionInRoot()
            if (leaving) held.leavingAt = at else held.settledAt = at
        }
        .layout { measurable, constraints ->
            val use: Constraints = if (leaving) held.constraints ?: constraints else constraints
            if (!leaving) held.constraints = constraints
            val placeable = measurable.measure(use)
            layout(constraints.constrainWidth(placeable.width), constraints.constrainHeight(placeable.height)) {
                val shift = if (leaving) held.shift() else Offset.Zero
                placeable.place(shift.x.roundToInt(), shift.y.roundToInt())
            }
        }
}

private class HeldFrame {
    var constraints: Constraints? = null
    var settledAt: Offset? by mutableStateOf(null)
    var leavingAt: Offset? by mutableStateOf(null)

    /** How far to move a leaving tab so it stays where it was: read in placement, so a change re-places it. */
    fun shift(): Offset {
        val settled = settledAt ?: return Offset.Zero
        val now = leavingAt ?: return Offset.Zero
        return settled - now
    }
}

/**
 * A bar or rail that comes and goes with a tab change (item 2): fading in over [MotionTokens.tabCrossfadeSpec] when [shown] turns
 * true, and fading out when it turns false, in step with [TabCrossfade]. Its room in the layout comes and goes at once, as before;
 * only its drawing fades. Leaving, it is drawn where it was but takes no room ([zeroRoom]), and no touch.
 *
 * [windowKey] is the window's shape (portrait or the landscape rail layout): when it changes, the bar is shown or not at once,
 * so turning the phone plays no fade, as before. Under reduced motion every change is instant.
 *
 * [zeroRoom] lays the leaving bar out at no size and draws it at its own size where it was: for a bottom bar, upwards from the
 * bottom edge; for a rail, inwards from its edge. [content] is the bar to draw.
 */
@Composable
fun TabChromeFade(
    shown: Boolean,
    windowKey: Any,
    // The root's modifier, by Compose's convention, ahead of zeroRoom, which is the leaving bar's room only (lint
    // ModifierParameter, dispatch 2026-10-17-03). Every caller names its arguments.
    modifier: Modifier = Modifier,
    zeroRoom: Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    // A fresh state, settled at the current value, whenever the window changes shape, and on every change under reduced motion:
    // a new state starts where it is and plays nothing.
    val state = remember(windowKey, if (reduceMotion) shown else null) { MutableTransitionState(shown) }
    state.targetState = shown
    val transition = rememberTransition(state, label = "tabChromeFade")
    val fadeSpec = MotionTokens.tabCrossfadeSpec<Float>()
    val alpha by transition.animateFloat(transitionSpec = { fadeSpec }, label = "tabChromeAlpha") { on -> if (on) 1f else 0f }
    // Read in composition for the semantics (tests read the live value); the layer reads it in the draw phase.
    val currentAlpha = alpha
    when {
        shown -> Box(modifier.graphicsLayer { this.alpha = alpha }.semantics { tabChromeAlpha = currentAlpha }) { content() }
        // Leaving: no room, no touch, and no semantics but its fade (Amendment 2), so a screen reader finds the arriving bar alone.
        // RECORD -691: no minimum touch target while it leaves (NoTouchTargetExpansion).
        state.currentState -> NoTouchTargetExpansion(active = true) {
            Box(
                modifier.then(zeroRoom)
                    .leavingTakesNoTouches(true)
                    .graphicsLayer { this.alpha = alpha }
                    .clearAndSetSemantics { tabChromeAlpha = currentAlpha },
            ) { content() }
        }
        else -> Unit
    }
}

/** The live alpha of a [TabChromeFade], readable in tests. */
val TabChromeAlphaKey = SemanticsPropertyKey<Float>("TabChromeAlpha")
var SemanticsPropertyReceiver.tabChromeAlpha by TabChromeAlphaKey

/** [TabChromeFade]'s room for a leaving bottom bar: no height, drawn upwards from where the bar's bottom was. */
fun Modifier.zeroHeightDrawnAbove(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
}

/**
 * [TabChromeFade]'s room for a leaving rail: no width, drawn where the rail was, [atStart] for a rail first in its row (drawn
 * forwards from the row's start) and otherwise last (drawn backwards from the row's end).
 */
fun Modifier.zeroWidthDrawnInPlace(atStart: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    layout(0, placeable.height) { placeable.place(if (atStart) 0 else -placeable.width, 0) }
}
