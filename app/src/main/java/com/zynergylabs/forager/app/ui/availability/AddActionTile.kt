package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapControlsUi.kt (dispatch 2026-10-19-12, UI split Phase 1, PR 8).

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING
import com.zynergylabs.forager.app.ui.map.mapIconBarRowAnchorOffset
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * [CompactMapTab]'s three-way chooser — three choices over the shared [PendingMapAction] state,
 * presented as a small tile that grows out of the add button's own corner of the icon bar rather
 * than [AlertDialog]'s centered scale-in, per the project owner's own description of how it should
 * open. (The tablet's plain `ThreeWayActionDialog` was removed in dispatch 2026-09-28-245.)
 *
 * **Real [AssistChip]s, short labels, no title row.** The original had a "Add..." title plus four
 * full-width [TextButton]s (three choices, and its own "Cancel") — a real hardware-reported
 * problem, not a style preference: a full-width button is as wide as its longest label, which made
 * the whole tile noticeably wider than the icon bar it grows out of. Short labels ("Trip"/
 * "Find"/"Waypoint") in a [Row] instead of a [Column] let the tile's own width shrink to what three
 * short buttons actually need, rather than the longest of three sentences. No separate "Cancel"
 * button either — the scrim below already dismisses on a tap outside, and a fourth wide row would
 * undo the same fit this rewrite exists for.
 *
 * **[AssistChip], not the filled Material [Button] this tile used at first.** A solid
 * `colorScheme.primary`-filled button is its own large block of colour, and three of them side by
 * side visually crowded out the tile's own theme-aware card fill entirely — from the project
 * owner's own side-by-side comparison against `MapModePicker` (removed 2026-10-19, RECORD -1392), whose unselected [FilterChip]s stay
 * outlined and let that same card fill read clearly instead. [AssistChip] is the semantically
 * correct match, not [FilterChip] borrowed wholesale: these three rows perform an action each and
 * share no "currently selected" state the way `MapModePicker`'s three modes do, so nothing here is
 * ever passed a `selected` value.
 *
 * Same theme-aware, 80%-opacity fill as [MapIconBar]
 * ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) rather than a plain
 * [MaterialTheme.colorScheme.surface] — one visual language for every control floating over the
 * map, the same reasoning `MapModePicker` gives for the same choice.
 *
 * A scrim (its own [AnimatedVisibility], faded independently of the tile) makes the map behind it
 * unmistakably unavailable to tap while a choice is pending — the same modal intent the dialog it
 * replaces had, just without borrowing [AlertDialog]'s fixed presentation. The scrim carries no
 * visible label of its own (unlike the three buttons beside it), so it is addressed in tests by
 * [ADD_ACTION_TILE_SCRIM_TAG] rather than text.
 *
 * [anchor]/[anchorOffset] mirror `MapModePicker`'s (removed 2026-10-19, RECORD -1392) own pair exactly (expanded-panels dispatch):
 * this used to hardcode `CenterEnd` / `-Spacing.sm` / [ADD_TILE_ANCHOR_OFFSET] internally, which
 * was fine while [MapIconBar] could only ever sit at its default right-centre position, and
 * silently wrong once the bar became draggable — the tile kept opening at the bar's *old*
 * position. The single caller now feeds the bar's live side and drag offset. [growsFrom] is the
 * corner the tile grows out of / shrinks back into, the add row's own corner of the bar: bottom-
 * end on the right, bottom-start once the bar is snapped to the left — passed explicitly rather
 * than derived from [anchor] here, so the caller's intent is visible at the call site.
 */
@Composable
internal fun AddActionTile(
    visible: Boolean,
    onPlanTrip: () -> Unit,
    onLogFind: () -> Unit,
    onDropWaypoint: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.CenterEnd,
    anchorOffset: DpOffset = DpOffset(x = -MAP_ICON_BAR_EDGE_INSET, y = ADD_TILE_ANCHOR_OFFSET),
    growsFrom: Alignment = Alignment.BottomEnd,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Box(modifier = modifier) {
        // docs/motion-spec.md §2 "Panels and navigation": panels accept mild spring overshoot as
        // a taste call, per docs/adr/0002-motion-scheme-adoption.md. Passing MotionTokens's spec
        // explicitly here removes any dependence on AnimatedVisibility's own default spec.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ADD_ACTION_TILE_SCRIM_TAG)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()) +
                expandIn(
                    animationSpec = MotionTokens.panelMotionSpec(),
                    expandFrom = growsFrom,
                ),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()) +
                shrinkOut(
                    animationSpec = MotionTokens.panelMotionSpec(),
                    shrinkTowards = growsFrom,
                ),
            modifier = Modifier
                .align(anchor)
                .offset(x = anchorOffset.x, y = anchorOffset.y),
        ) {
            Surface(
                modifier = Modifier.testTag(ADD_ACTION_TILE_TAG).mapChromeContainerColor(if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight),
                shape = RoundedCornerShape(Spacing.md),
                shadowElevation = 4.dp,
                color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                contentColor = if (isDarkTheme) Color.White else Bark,
                border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AssistChip(onClick = onPlanTrip, label = { Text("Trip") })
                    AssistChip(onClick = onLogFind, label = { Text("Find") })
                    AssistChip(onClick = onDropWaypoint, label = { Text("Waypoint") })
                }
            }
        }
    }
}

/** See [AddActionTile]'s own doc comment — its scrim has no visible label, so tests address it by tag. */
internal const val ADD_ACTION_TILE_SCRIM_TAG = "add-action-tile-scrim"

/**
 * [AddActionTile]'s own panel `Surface` — what the expanded-panels dispatch's tests measure its
 * real edges by. Its chips are not a usable proxy for those edges: under Robolectric's near-zero-
 * width fonts each chip measures under 48dp wide, so Material's `minimumInteractiveComponentSize`
 * wrapper pads it out and the chip's own semantics bounds stop short of the panel's edge by a
 * font-dependent margin (measured: ~5.5–7dp) that a real device would not show.
 */
internal const val ADD_ACTION_TILE_TAG = "add-action-tile"

/**
 * [MapIconBar]'s add row is its 5th (last) of 5 — see [mapIconBarRowAnchorOffset], promoted into
 * `MapChrome.kt` (fullscreen-fixes dispatch, Item 2) so the Cartography entry map's own
 * `MapModePicker` call can use the same row-anchor arithmetic this file's calls already did.
 */
internal val ADD_TILE_ANCHOR_OFFSET = mapIconBarRowAnchorOffset(rowIndexFromTop = 5)

/**
 * [ADD_TILE_ANCHOR_OFFSET] for the landscape L's bar (dispatch 2026-09-28-160), whose rows are 48 dp apart, not 52: the add row's
 * centre is 96 dp below the bar's, not 104.
 */
internal val ADD_TILE_ANCHOR_OFFSET_LANDSCAPE = mapIconBarRowAnchorOffset(rowIndexFromTop = 5, rowSpacing = MAP_ICON_BAR_LANDSCAPE_ROW_SPACING)
