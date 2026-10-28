package com.zynergylabs.forager.app.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The map type panel's own `Surface`, for tests that measure where it opened. */
internal const val MAP_TYPE_PANEL_TAG = "map-type-panel"

/** The panel's scrim, which has no label of its own, for tests. */
internal const val MAP_TYPE_PANEL_SCRIM_TAG = "map-type-panel-scrim"

/** The Layers row is [MapIconBar]'s 4th of 5: its centre's offset from the bar's centre (52 dp). */
internal val MAP_TYPE_PANEL_ANCHOR_OFFSET: Dp = mapIconBarRowAnchorOffset(rowIndexFromTop = 4)

/** [MAP_TYPE_PANEL_ANCHOR_OFFSET] for the landscape L's bar, whose rows have no gap between them (48 dp). */
internal val MAP_TYPE_PANEL_ANCHOR_OFFSET_LANDSCAPE: Dp =
    mapIconBarRowAnchorOffset(rowIndexFromTop = 4, rowSpacing = MAP_ICON_BAR_LANDSCAPE_ROW_SPACING)

/**
 * The map type panel the Layers button opens (dispatch 2026-10-28-02, RECORD -1764; the owner: "Have the mapiconbar layers
 * tab return to the original animated state it was prior to the map layers addition."). The shape is `MapModePicker`'s
 * (`347859d50^:app/src/main/java/com/zynergylabs/forager/app/ui/map/MapChrome.kt:126`), rebuilt on today's parts: a scrim
 * fading in, then a small panel of Trail · Topo · OSM ([MapModeChipRow], so the grey "chosen, but Trail is showing" chip
 * keeps its meaning) fading and growing out of the Layers row, and shrinking back into it.
 *
 * - A chip tap applies that type and closes the panel: one tap.
 * - A tap on the scrim, or Back, closes it with nothing changed.
 * - [anchor] and [anchorOffset] place it beside the Layers row; [growsFrom] is the side of the row it grows from, the bar's
 *   side. Each host supplies them: the Maps tab from the cluster's live side and drag, the entry map from its fixed bar.
 * - Under reduced motion it only fades (`docs/motion-spec.md` §4, "Panel motion: Fade or instant").
 * - Its fill is the icon bar's, at the map chrome's 80% (CLAUDE.md, "Nothing fully obstructs the map view").
 *
 * Nothing is composed while it is closed and settled, so the map keeps every touch (CLAUDE.md, the `Surface` pitfall). Back is
 * registered only while it is open, which puts its handler after every other one and so asked first.
 */
@Composable
internal fun MapTypePanel(
    visible: Boolean,
    mapMode: MapMode,
    onModeSelected: (MapMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.CenterEnd,
    anchorOffset: DpOffset = DpOffset(x = -MAP_ICON_BAR_EDGE_INSET, y = MAP_TYPE_PANEL_ANCHOR_OFFSET),
    growsFrom: Alignment = Alignment.CenterEnd,
    /** See `MapModeChipRow`: the entry map's "Use offline tiles". */
    offlineTiles: Boolean = false,
    fallback: OwnStreetFallback = OWN_STREET_FALLBACK,
) {
    if (visible) BackHandler(onBack = onDismiss)
    val isDarkTheme = LocalForagerDarkTheme.current
    val reduceMotion = LocalReduceMotion.current
    val fill = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    val enter: EnterTransition = fadeIn(animationSpec = MotionTokens.panelMotionSpec()) +
        if (reduceMotion) EnterTransition.None else expandIn(animationSpec = MotionTokens.panelMotionSpec(), expandFrom = growsFrom)
    val exit: ExitTransition = fadeOut(animationSpec = MotionTokens.panelMotionSpec()) +
        if (reduceMotion) ExitTransition.None else shrinkOut(animationSpec = MotionTokens.panelMotionSpec(), shrinkTowards = growsFrom)
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(MAP_TYPE_PANEL_SCRIM_TAG)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = enter,
            exit = exit,
            modifier = Modifier.align(anchor).offset(x = anchorOffset.x, y = anchorOffset.y),
        ) {
            Surface(
                modifier = Modifier.testTag(MAP_TYPE_PANEL_TAG).mapChromeContainerColor(fill),
                shape = RoundedCornerShape(Spacing.md),
                color = fill,
                contentColor = if (isDarkTheme) Color.White else Bark,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            ) {
                MapModeChipRow(
                    mapMode = mapMode,
                    onMapModeSelected = { mode ->
                        onModeSelected(mode)
                        onDismiss()
                    },
                    offlineTiles = offlineTiles,
                    fallback = fallback,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
        }
    }
}
