package com.zynergylabs.forager.app.ui.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.Role
import com.zynergylabs.forager.app.ui.theme.Spacing
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/*
 * Press feedback for Part 1 of the motion sweep (dispatch 2026-09-28-652, items 2 to 4; the owner's choices in RECORD -651).
 *
 * **Why the highlight is drawn apart from the touch.** A `clickable` draws its ripple over its own whole layout box, which is
 * what made the map icon bar's highlight a hard square. The obvious fix, clipping the control to its shape, would also change
 * where it takes touches: a clipping layer is part of hit testing, so the corners would stop catching taps (inferred from Compose's hit-test code as remembered, which tests a pointer against a
 * clipping layer's outline; not re-read for this change, and pinned instead by the real-touch tests that sample the corners).
 * The owner's ruling is "touch areas unchanged". So the control keeps its `clickable`
 * exactly where it was, with `indication = null`, and [PressHighlight] draws the same press (the theme's own
 * [LocalIndication], the ripple the `clickable` would have drawn) on a separate box that is clipped to the control's shape and
 * takes no pointer input of its own. Touches go where they always went; only the drawn highlight changes shape.
 */

/** The live scale of a [pressBounce], 1 at rest, readable in tests (the restore page's `restoreIconScale` does the same). */
val PressBounceScaleKey = SemanticsPropertyKey<Float>("PressBounceScale")
var SemanticsPropertyReceiver.pressBounceScale by PressBounceScaleKey

/** The shape a [PressHighlight] is clipped to, readable in tests. */
val PressHighlightShapeKey = SemanticsPropertyKey<Shape>("PressHighlightShape")
var SemanticsPropertyReceiver.pressHighlightShape by PressHighlightShapeKey

/**
 * The press highlight of the control whose `clickable` reports to [interactionSource], drawn over the parent box's bounds
 * (less whatever inset [modifier] adds) and clipped to [shape]. Composed last in the control's box, so it draws over the
 * control's content as a ripple does. No pointer input: it never takes a touch (see the file comment).
 */
@Composable
fun BoxScope.PressHighlight(
    interactionSource: InteractionSource,
    shape: Shape,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Box(
        Modifier
            .matchParentSize()
            .then(modifier)
            .then(if (testTag != null) Modifier.testTag(testTag).semantics { pressHighlightShape = shape } else Modifier)
            .clip(shape)
            .indication(interactionSource, LocalIndication.current),
    )
}

/**
 * The press bounce (the owner, RECORD -651: "Small press bounce"): while [interactionSource] reports a press the content dips to
 * [MotionTokens.PRESS_BOUNCE_SCALE] and springs back on release, on [MotionTokens.feedbackMotionSpec]. Under reduced motion
 * ([LocalReduceMotion]) it stays still and the highlight alone shows the press.
 *
 * A `graphicsLayer` scale, so nothing is re-measured. Apply it to the drawn content only, never to the box that holds the
 * `clickable`: a layer's transform is part of hit testing, and the touch area must not shrink with the dip.
 */
@Composable
fun Modifier.pressBounce(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) MotionTokens.PRESS_BOUNCE_SCALE else 1f,
        animationSpec = MotionTokens.feedbackMotionSpec(),
        label = "pressBounce",
    )
    // Read in composition, not only in the layer block, so the semantics below carry the live value for tests. It recomposes
    // this one control on the frames of a press, and nothing else.
    val current = scale
    return this
        .graphicsLayer {
            scaleX = current
            scaleY = current
        }
        .semantics { pressBounceScale = current }
}

/** Material 3's pressed state-layer opacity, applied to the content colour. */
private const val PRESSED_STATE_LAYER_ALPHA = 0.1f

/**
 * The shape a list row's or a text link's press is drawn in, where the control itself has no shape of its own (Amendment 1 to
 * dispatch 2026-09-28-652, RECORD -657: the other hard-cornered highlights are rounded too, "Yes, round them all").
 */
val ShapedPressDefaultShape: Shape = RoundedCornerShape(Spacing.sm)

/**
 * A press drawn as a shaped state layer over this node: the content colour at Material's pressed opacity, filled in [shape],
 * fading in while [interactionSource] reports a press and out after. Drawing only, with no clip and no layer, so it moves no
 * touch and clips none of the node's content or children; used on rows and links whose `clickable` (or `selectable`,
 * `toggleable`) is given `indication = null`. A state layer rather than a clipped ripple: a ripple can only be clipped to a
 * shape by clipping the node it draws in, which would clip the row's own content and its children's touches with it.
 */
@Composable
fun Modifier.shapedPressLayer(interactionSource: InteractionSource, shape: Shape = ShapedPressDefaultShape): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val color = LocalContentColor.current
    val alpha by animateFloatAsState(
        targetValue = if (pressed) PRESSED_STATE_LAYER_ALPHA else 0f,
        animationSpec = MotionTokens.iconSwapSpec(),
        label = "shapedPress",
    )
    return drawWithContent {
        drawContent()
        val a = alpha
        if (a > 0f) drawOutline(outline = shape.createOutline(size, layoutDirection, this), color = color, alpha = a)
    }
}

/**
 * `clickable` with its press drawn by [shapedPressLayer] in [shape] instead of a square ripple. The touch area is the
 * `clickable`'s, exactly as a plain `clickable` in the same place; same parameters as the app's calls use.
 */
@Composable
fun Modifier.clickableWithShapedPress(
    shape: Shape = ShapedPressDefaultShape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)
        .shapedPressLayer(interactionSource, shape)
}
