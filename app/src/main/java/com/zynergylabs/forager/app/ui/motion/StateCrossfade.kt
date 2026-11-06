package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics

/*
 * Motion Part 3, Amendment 2 (RECORD -682, the planner's call under the owner's "Same rule everywhere"): a spinner, an empty
 * message or an error giving way to content, or back, crossfades quickly instead of swapping in one frame (scout J6, J10, F5, F8,
 * V4, K2). Not a page slide: nothing is opened on top and there is no Back to retrace.
 */

/** Whether a [StateCrossfade]'s content is the one fading out, readable in tests. */
val StateLeavingKey = SemanticsPropertyKey<Boolean>("StateLeaving")
var SemanticsPropertyReceiver.stateLeaving by StateLeavingKey

/**
 * Draws [content] for [targetState], crossfading on [MotionTokens.stateCrossfadeSpec] when [contentKey] of it changes; a change
 * that keeps the key (the same list with new rows, say) updates in place. The one fading out takes no touch and keeps only its
 * marker in the semantics tree, as a leaving page does. No size animation: the box takes the larger of the two while both show.
 * Under reduced motion ([LocalReduceMotion]) the change is instant, as before this existed.
 *
 * [content] must draw from the state it is handed, not the caller's current one, wherever the two can differ.
 */
@Composable
fun <T> StateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = MotionTokens.stateCrossfadeSpec<Float>()
    val targetKey = contentKey(targetState)
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                (fadeIn(animationSpec = fade) togetherWith fadeOut(animationSpec = fade)).using(null)
            }
        },
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        label = "stateCrossfade",
    ) { shown ->
        val leaving = contentKey(shown) != targetKey
        NoTouchTargetExpansion(active = leaving) {
            Box(
                modifier = Modifier
                    .leavingTakesNoTouches(leaving)
                    .then(if (leaving) Modifier.clearAndSetSemantics { stateLeaving = true } else Modifier),
                contentAlignment = contentAlignment,
            ) { content(shown) }
        }
    }
}
