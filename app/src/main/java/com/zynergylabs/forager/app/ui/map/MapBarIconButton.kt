package com.zynergylabs.forager.app.ui.map

// Moved from MapChrome.kt unchanged (UI split Phase 1, PR 7, dispatch 2026-10-19-16).

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.indication
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.motion.IconSwap
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.PressHighlight
import com.zynergylabs.forager.app.ui.motion.pressBounce
import com.zynergylabs.forager.app.ui.theme.MapIconBarAccent
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * One row inside [MapIconBar] — a plain tap target tinted by state, not its own circle: the bar
 * itself is the shared background now, so a per-icon background is reserved for the two rows that
 * carry real, not merely decorative, fill state ([filled]/[fillColor]/[fillContentColor]: the add
 * button's permanent accent, the record button's error accent while active — see
 * [MapIconBarAccent]'s own doc comment for why each caller passes its own theme-swapped pair here
 * rather than reading [MaterialTheme.colorScheme.primary]/[error] directly).
 *
 * **Motion, Part 1** (dispatch 2026-09-28-652; the owner's choices in RECORD -651):
 * - **Press highlight** (item 2, "Rounded, fits the bar"): drawn by [PressHighlight] in [highlight]'s shape, inset inside the
 *   row so it never reaches past the bar's edges in portrait or landscape (see [MapBarHighlight] for the arithmetic). The
 *   `clickable` stays on the full 48 x 48 box with no indication of its own, so **the touch area is exactly what it was**,
 *   corners included (the landscape L's full-square hits, owner's ruling (d), continuation 2026-09-28-172).
 * - **Press bounce** (item 4, "Small press bounce"): the icon and its badge dip and spring back ([pressBounce]); not under
 *   reduced motion, where the highlight alone shows the press.
 * - **Icon crossfade** (item 5, "Quick crossfade"): a change of [icon] crossfades with a slight grow ([IconSwap]); the badge
 *   fades in and out with it (the red recording circle, B11), and the tint (B12's normal, red off track, primary while
 *   navigating) and the disabled dimming (B12's 40%) fade on the same spec instead of jumping.
 *
 * The content description sits on the row's own box, not on the icon, because for the length of a swap two icons are
 * composed. The merged node a test or TalkBack finds is the same 48 x 48 clickable box as before.
 */
@Composable
internal fun MapBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gives this row its own filled circle in [fillColor] rather than tinting just the icon. */
    filled: Boolean = false,
    fillColor: Color = Color.Unspecified,
    /** The icon's own tint while [filled] — must be picked for contrast against [fillColor] specifically, not [MapIconBar]'s own bar-level contentColor. */
    fillContentColor: Color = Color.White,
    /**
     * `false` dims and disables the tap target — the return-to-vehicle row's state while nothing
     * is recording, when there is nothing yet to return to.
     */
    enabled: Boolean = true,
    /** Tints just the icon (not a background) for a toggle that is currently "on" but not [filled] — the return-to-vehicle row. */
    activeColor: Color? = null,
    /**
     * The press highlight's shape: [MapBarHighlight.ROUNDED_SQUARE] for a plain row, [MapBarHighlight.BADGE] for the two rows
     * whose control is a round badge, Add and Record (the owner, RECORD -651: "round highlights on the round Add and Record
     * badges"). Record passes [MapBarHighlight.BADGE] whether or not it is recording, so the highlight does not change shape
     * under the finger when the tap starts or stops a recording.
     */
    highlight: MapBarHighlight = MapBarHighlight.ROUNDED_SQUARE,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val reduceMotion = LocalReduceMotion.current
    val dimmed by animateFloatAsState(
        targetValue = if (enabled) 1f else MAP_BAR_DISABLED_ALPHA,
        animationSpec = MotionTokens.iconSwapSpec(),
        label = "mapBarDimmed",
    )
    val badgeShown by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = MotionTokens.iconSwapSpec(),
        label = "mapBarBadge",
    )
    // filled rows sit on their own saturated circle (green/error), so their icon needs
    // fillColor's own contrast pair, not MapIconBar's bar-level contentColor; everything
    // else inherits that bar-level color via LocalContentColor, unless a state override
    // (activeColor) says otherwise.
    val tint by animateColorAsState(
        targetValue = if (filled) fillContentColor else activeColor ?: LocalContentColor.current,
        animationSpec = MotionTokens.iconSwapSpec(),
        label = "mapBarTint",
    )
    Box(
        modifier = modifier
            .size(MIN_TOUCH_TARGET)
            // Was Modifier.alpha(0.4f) while disabled: the same 40%, now faded (B12). No clip, so no effect on where it takes touches.
            .graphicsLayer { alpha = dimmed }
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // The button's face: the badge and the icon, which dip together on a press. The touch box above does not move.
        Box(modifier = Modifier.matchParentSize().pressBounce(interactionSource), contentAlignment = Alignment.Center) {
            if (badgeShown > 0f) {
                Box(
                    modifier = Modifier
                        .size(MAP_ICON_BAR_FILL_DIAMETER)
                        .graphicsLayer {
                            alpha = badgeShown
                            // The badge grows in with the icon's swap, slightly, from the same start scale; a plain fade under reduced motion.
                            val grow = if (reduceMotion) 1f else MotionTokens.ICON_SWAP_ENTER_SCALE + (1f - MotionTokens.ICON_SWAP_ENTER_SCALE) * badgeShown
                            scaleX = grow
                            scaleY = grow
                        }
                        .background(color = fillColor, shape = CircleShape),
                )
            }
            IconSwap(targetState = icon) { shown ->
                Icon(imageVector = shown, contentDescription = null, tint = tint)
            }
        }
        PressHighlight(
            interactionSource = interactionSource,
            shape = highlight.shape,
            modifier = Modifier.padding(highlight.inset),
            testTag = MAP_BAR_PRESS_HIGHLIGHT_TAG,
        )
    }
}

/** [MapBarIconButton]'s disabled dimming, the 40% it has always had (B12); now a named value because it is animated to. */
private const val MAP_BAR_DISABLED_ALPHA = 0.4f

/** The press highlight inside a [MapBarIconButton], for tests (read with the row's content description as an ancestor). */
internal const val MAP_BAR_PRESS_HIGHLIGHT_TAG = "map-bar-press-highlight"

/**
 * The two shapes of a [MapBarIconButton]'s press highlight (dispatch 2026-09-28-652, item 2; the owner, RECORD -651: "Rounded,
 * fits the bar": "a rounded square inset in the bar, never past its edges in portrait or landscape; round highlights on the
 * round Add and Record badges").
 *
 * **Why these sizes fit the bar.** The bar is a stadium 48 dp wide with [MAP_ICON_BAR_CORNER_RADIUS] (24 dp) ends; each row
 * is 48 x 48. The worst row is an end row in the landscape L, which has no end padding, so the row's corner is the bar's
 * corner. A square inset by `d` with corner radius `r` stays inside a 24 dp end if its corner arc does, that is if
 * sqrt(2) x (24 - d - r) + r <= 24, which for d = 4 needs r >= 10.4. [ROUNDED_SQUARE] is d = 4 ([Spacing.xs]), r = 12
 * ([Spacing.md]): 40 x 40 with 12 dp corners, 0.7 dp clear of the end at its closest. In portrait the end rows sit 4 dp in
 * from the bar's ends, which only adds room (the condition there is 24 - d - r <= 16). The landscape pill (96 x 48, the same
 * 24 dp ends) is the same case as the L's end rows. Arithmetic, not measured: on the phone it is a device check.
 *
 * [BADGE] is the badge's own circle, [MAP_ICON_BAR_FILL_DIAMETER] (36 dp) centred in the row, 6 dp in from each side: the
 * highlight is exactly the round green Add badge and the red Record badge. Centred 24 dp from a 24 dp end it is 6 dp clear of
 * it in the L, and 2 dp clear in portrait's offset rows.
 */
internal enum class MapBarHighlight(val shape: Shape, val inset: Dp) {
    ROUNDED_SQUARE(RoundedCornerShape(Spacing.md), Spacing.xs),
    BADGE(CircleShape, (MIN_TOUCH_TARGET - MAP_ICON_BAR_FILL_DIAMETER) / 2),
}
