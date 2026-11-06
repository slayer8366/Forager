package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * An icon that changes its picture with a quick crossfade and a slight grow (dispatch 2026-09-28-652, item 5; the owner, RECORD
 * -651: "Quick crossfade"). [targetState] is what decides the picture (an `ImageVector`, a mode); [content] draws it. The old
 * picture fades out on [MotionTokens.iconSwapSpec] while the new one fades in on the same spec and grows from
 * [MotionTokens.ICON_SWAP_ENTER_SCALE] to full size on [MotionTokens.feedbackMotionSpec]. Under reduced motion
 * ([LocalReduceMotion]) it is the crossfade alone (docs/motion-spec.md §4: "Alpha cross-fade or instant").
 *
 * **Content descriptions belong on the control, not in [content].** For the length of a swap both pictures are composed, so a
 * description inside [content] would put two on the control at once; each caller sets its one description on the control's own
 * node and draws the icons with `contentDescription = null`.
 *
 * No size change: the swap is laid out at the larger of the two pictures, which for one icon set is the same size, and the size
 * transform does not clip, so the grow is never cut off.
 */
@Composable
fun <T> IconSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val fade = MotionTokens.iconSwapSpec<Float>()
    val grow = MotionTokens.feedbackMotionSpec<Float>()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val enter = if (reduceMotion) {
                fadeIn(animationSpec = fade)
            } else {
                fadeIn(animationSpec = fade) + scaleIn(animationSpec = grow, initialScale = MotionTokens.ICON_SWAP_ENTER_SCALE)
            }
            (enter togetherWith fadeOut(animationSpec = fade)).using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "iconSwap",
    ) { state -> content(state) }
}
