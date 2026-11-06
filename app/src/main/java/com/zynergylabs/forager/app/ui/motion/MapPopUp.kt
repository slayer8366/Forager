package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/*
 * Motion Part 2, item 4 (dispatch 2026-09-28-666; the owner, RECORD -651: "Fade and grow", "from where each belongs") and item 5
 * ("Let taps through at once": "From the moment something starts to leave, taps go to the map beneath it").
 *
 * **Why the grow is drawn and not laid out.** AnimatedVisibility's own `scaleIn` is a graphics-layer scale, and a layer's
 * transform is part of hit testing, so while a pop-up grew its controls would take touches only where they were drawn at that
 * moment: a touch-area change for the length of the grow. Here the grow is a draw-only scale ([drawGrow]): the pop-up is laid
 * out, and takes touches, at its full size from its first frame, exactly where it will settle, and only what is drawn grows. The
 * fade is alpha, which hit testing ignores. Nothing here changes a layout size, so nothing beside a pop-up re-measures.
 *
 * **Leaving.** From the first frame a pop-up starts to leave it takes no new touch ([leavingTakesNoTouches], Part 1's), while it
 * goes on drawing its fade and shrink.
 */

/** The live scale a [MapPopUp] draws at, 1 at rest, readable in tests (as `PressBounceScaleKey` is). */
val MapPopUpScaleKey = SemanticsPropertyKey<Float>("MapPopUpScale")
var SemanticsPropertyReceiver.mapPopUpScale by MapPopUpScaleKey

/**
 * Something drawn over the map that fades and grows in, from [pivot], and fades and shrinks back out towards it.
 *
 * [pivot] is where it grows from, in this pop-up's own pixels, given its size: a chip's top centre, the legend's bottom-end
 * corner, a bubble's glyph. [modifier] goes on the pop-up's outermost node, so a caller's `align` and padding go there as
 * they would on the pop-up itself.
 *
 * Composed whether or not it shows; [visible] decides. It does not animate on its first composition (a tab coming back to a map
 * whose legend is already on shows the legend, it does not grow it again). Under reduced motion ([LocalReduceMotion]) it fades
 * and does not grow (docs/motion-spec.md §4, "Panel motion: Fade or instant").
 *
 * **What [content] sees while it leaves.** The caller's state has usually gone by then (the bubble's tapped thing is null, the
 * legend has no fields). A caller whose content needs the value that was showing keeps it with [rememberLastShown].
 */
@Composable
fun MapPopUp(
    visible: Boolean,
    pivot: (Size) -> Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MapPopUp(state = rememberPopUpState(visible), pivot = pivot, modifier = modifier, content = content)
}

/**
 * The same, for a caller that also needs to know whether the pop-up is still on screen while it leaves ([isOnScreen]): the
 * chips' row stays composed until its last chip has gone. [state] comes from [rememberPopUpState].
 */
@Composable
fun MapPopUp(
    state: MutableTransitionState<Boolean>,
    pivot: (Size) -> Offset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = MotionTokens.mapPopUpFadeSpec<Float>()
    val grow = MotionTokens.mapPopUpGrowSpec<Float>()
    // RECORD -691: no minimum touch target while it leaves, so a touch near a leaving piece under 48 dp is not handed to it (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
    NoTouchTargetExpansion(active = !state.targetState) {
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier.leavingTakesNoTouches(leaving = !state.targetState),
        enter = fadeIn(animationSpec = fade),
        exit = fadeOut(animationSpec = fade),
        label = "mapPopUp",
    ) {
        val scale by transition.animateFloat(transitionSpec = { grow }, label = "mapPopUpGrow") { phase ->
            if (phase == EnterExitState.Visible || reduceMotion) 1f else MotionTokens.MAP_POPUP_ENTER_SCALE
        }
        // Read in composition too, as the press bounce's is, so the semantics below carry the live value for tests: this pop-up
        // recomposes on the frames of its own grow, and nothing else does. The drawing reads it in the draw phase.
        val current = scale
        Box(
            Modifier
                .drawGrow(scaleNow = { scale }, pivot = pivot)
                .semantics { mapPopUpScale = current },
        ) { content() }
    }
    }
}

/**
 * The visibility of one [MapPopUp], set to [visible] on every composition. Its initial state is the first value, so the first
 * composition plays no entrance.
 */
@Composable
fun rememberPopUpState(visible: Boolean): MutableTransitionState<Boolean> {
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    return state
}

/** Showing, or still leaving: on screen at all. Read in composition, so a caller recomposes when the leaving ends. */
val MutableTransitionState<Boolean>.isOnScreen: Boolean get() = currentState || targetState

/**
 * The last non-null value of [value], so a pop-up's content can go on drawing what it showed while it leaves. Null only before
 * the first non-null value.
 */
@Composable
fun <T : Any> rememberLastShown(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}

/**
 * A scale applied to the drawing alone, about [pivot] (in this node's pixels, given its size): the layout, and so where the node
 * and its children take touches, is untouched. [scaleNow] is read in the draw phase, so a running grow redraws without recomposing.
 */
fun Modifier.drawGrow(scaleNow: () -> Float, pivot: (Size) -> Offset): Modifier = drawWithContent {
    val s = scaleNow()
    if (s == 1f) {
        drawContent()
    } else {
        scale(scale = s, pivot = pivot(size)) { this@drawWithContent.drawContent() }
    }
}

/** Pivots for [MapPopUp], as fractions of the pop-up's own size. */
object PopUpPivot {
    val TopCentre: (Size) -> Offset = { Offset(it.width / 2f, 0f) }
    val BottomCentre: (Size) -> Offset = { Offset(it.width / 2f, it.height) }
    val Centre: (Size) -> Offset = { Offset(it.width / 2f, it.height / 2f) }
    val BottomEnd: (Size) -> Offset = { Offset(it.width, it.height) }
    val BottomStart: (Size) -> Offset = { Offset(0f, it.height) }
}
