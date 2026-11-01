package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapControlsUi.kt (dispatch 2026-10-19-12, UI split Phase 1, PR 8).

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_CORNER_RADIUS
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapBarIconButton
import com.zynergylabs.forager.app.ui.map.MapBarHighlight
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.mapIconClusterChildColor
import com.zynergylabs.forager.app.ui.map.mapIconBarRecordAccent
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The two Trailhead/Return controls — record start/stop and return-to-vehicle — anchored together
 * below [MapIconBar], per this dispatch's own Part B: they used to be split across a
 * [MapIconBar] row and a duplicate compass-strip readout; now they share one home.
 *
 * **Icon-bar-unify-container dispatch: laid out inside the cluster container's own Column, directly
 * below [MapIconBar], no anchoring of its own.** It used to offset itself by the bar's measured
 * bottom edge (`mapIconBarBottomPx`, from [CompactMapTab]'s `onGloballyPositioned` on the bar's
 * wrapper) — measured rather than derived from the bar's row count, which changed twice in quick
 * succession at the time. That measurement is gone: the Column's `spacedBy` is the gap now, and
 * the cluster container is what gets measured, dragged, clamped and minimised, so this pill is in
 * bounds because it is inside the thing that is bounded. On device before this change the record
 * pill hung off the bottom in fullscreen while the bar sat legally, and the directions pill was
 * left sitting on the nav after exiting — the bar was in bounds, the pills extended past it.
 *
 * **Icon-bar-drag-refinements dispatch, Items 2-3: [onLeftSide] follows [MapIconBar]'s own side.**
 * [ControlPill] stays pinned to the container's outer edge on whichever side (the Column's
 * `horizontalAlignment`). It hides and restores with the bar when minimised — by construction now.
 *
 * **The distance arm is gone (navigation-chrome dispatch, item 3).** `DistanceArm` used to extend
 * downward from [ControlPill] while returning, showing the return distance; navigation HUD stage
 * one then made the HUD visible on exactly the condition the arm rendered on, so the two were
 * always on screen together showing the same number ("0 ft beside 0 ft", on device). Confirmed
 * before removal: no state showed the arm without the HUD — both keyed on the one `isReturning`,
 * both in this tab's map Box — while the reverse (HUD without arm, cluster minimised) did exist.
 * The return row's `contentDescription` still carries the full bearing/distance/elevation
 * sentence, so the TalkBack path is unchanged; the visible distance is the HUD's. This Column is
 * kept rather than inlining the pill: it is the cluster's named "Trailhead/Return controls" slot,
 * and stage two's picker entry is expected to land here.
 *
 * `isRecording` is passed through as a plain parameter, not a presence check gating whether this
 * composable runs at all — record start/stop must stay reachable before the first recording
 * starts, the same as when it was an always-enabled [MapIconBar] row.
 */
@Composable
internal fun TrailheadControls(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    /** For the return row's `contentDescription` sentence — the same [formatDistanceMeters] the HUD's visible distance uses. */
    distanceUnit: DistanceUnit,
    onLeftSide: Boolean,
    modifier: Modifier = Modifier,
    /** Landscape L (dispatch 2026-09-28-160): turns the pill horizontal, record then return; see [ControlPill]. */
    horizontal: Boolean = false,
    /** Landscape L: the pill's own fill, at the standing chrome alpha, where no container sits under it; default is the cluster child fill. */
    fillColor: Color = Color.Unspecified,
    /** Landscape L: spacing and end padding inside the pill; [Spacing.xs] as it has always been, zero in the L. */
    rowSpacing: Dp = Spacing.xs,
    /**
     * Dispatch 2026-09-28-502: whether any navigation is on, a return or a chosen waypoint. The return
     * control is then the X in a circle, "Stop navigating", and takes a tap with or without a recording,
     * since a waypoint navigation needs none. [isReturning] by default, so the return alone is as before.
     */
    isNavigating: Boolean = isReturning,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (onLeftSide) Alignment.Start else Alignment.End,
    ) {
        ControlPill(
            horizontal = horizontal,
            onLeftSide = onLeftSide,
            fillColor = fillColor,
            rowSpacing = rowSpacing,
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
            returnToStart = returnToStart,
            isReturning = isReturning,
            isNavigating = isNavigating,
            isOffTrack = isOffTrack,
            onToggleReturning = onToggleReturning,
            distanceUnit = distanceUnit,
        )
    }
}

/**
 * The vertical control pill at the bottom right, below [MapIconBar] — record start/stop and
 * return-to-vehicle, the two Trailhead/Return controls, sharing one home per this dispatch's own
 * Part B rather than split across a [MapIconBar] row and a duplicate compass-strip readout.
 *
 * Same visual language as [MapIconBar] on purpose — same [MAP_ICON_BAR_CORNER_RADIUS] stadium
 * shape, same theme-aware surface/border colors, same [MapBarIconButton] rows — so this reads as a
 * sibling of the bar, not a new kind of floating control. Both rows carry over unchanged from
 * [MapIconBar]'s own former record/return-to-vehicle rows, including their accents
 * ([mapIconBarRecordAccent]) and the return row's disabled-while-not-recording treatment.
 */
@Composable
private fun ControlPill(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    /** Dispatch 2026-09-28-502: see [TrailheadControls]' parameter of the same name. */
    isNavigating: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    /** Which side the cluster is on: the horizontal pill keeps record at the outer end, under the bar, so its order mirrors. */
    onLeftSide: Boolean = true,
    fillColor: Color = Color.Unspecified,
    rowSpacing: Dp = Spacing.xs,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    // A child of the cluster container — see MAP_ICON_CLUSTER_CHILD_ALPHA's own doc comment — unless the landscape L hands it
    // its own single-layer fill.
    val pillFill = fillColor.takeOrElse { mapIconClusterChildColor() }
    val pillContentColor = if (isDarkTheme) Color.White else Bark
    val pillBorder = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT)
    val buttons: @Composable () -> Unit = {
        // The two buttons, once: the vertical pill lays them in a Column, the landscape L's horizontal pill (record under the bar's
        // column, return extending inboard) in a Row. Same buttons, tags, states and accents either way.
        val record: @Composable () -> Unit = {
            MapBarIconButton(
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) "Stop recording track" else "Start recording track",
                onClick = onToggleRecording,
                filled = isRecording,
                fillColor = mapIconBarRecordAccent(isDarkTheme).fill,
                fillContentColor = mapIconBarRecordAccent(isDarkTheme).onFill,
                // Round whether or not recording: the control is the Record badge (RECORD -651), and the shape must not flip under the finger.
                highlight = MapBarHighlight.BADGE,
                modifier = Modifier.testTag("control-pill-record"),
            )
        }
        val returnToVehicle: @Composable () -> Unit = {
            MapBarIconButton(
                icon = returnControlIcon(isNavigating),
                contentDescription = returnControlDescription(isNavigating, isRecording, returnToStart, distanceUnit),
                onClick = onToggleReturning,
                // Dispatch -502: a waypoint navigation needs no recording, and its X-circle must still end it.
                enabled = isRecording || isNavigating,
                activeColor = when {
                    isOffTrack -> MaterialTheme.colorScheme.error
                    isNavigating -> MaterialTheme.colorScheme.primary
                    else -> null
                },
                modifier = Modifier.testTag("control-pill-return-to-vehicle"),
            )
        }
        if (horizontal) {
            Row(
                modifier = Modifier.padding(horizontal = rowSpacing),
                horizontalArrangement = Arrangement.spacedBy(rowSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onLeftSide) {
                    record()
                    returnToVehicle()
                } else {
                    returnToVehicle()
                    record()
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(vertical = rowSpacing),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                record()
                returnToVehicle()
            }
        }
    }
    if (horizontal) {
        // Owner's ruling (d), continuation 2026-09-28-172: both buttons take touches across their full 48 x 48 squares, corners
        // included. The drawn pill is a content-less Surface underneath, so its rounded ends do not clip the buttons' hit areas.
        Box(modifier = modifier.testTag("control-pill").mapChromeContainerColor(pillFill)) {
            Surface(
                shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
                color = pillFill,
                shadowElevation = 2.dp,
                border = pillBorder,
                modifier = Modifier.matchParentSize(),
            ) {}
            CompositionLocalProvider(LocalContentColor provides pillContentColor) { buttons() }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
            color = pillFill,
            contentColor = pillContentColor,
            shadowElevation = 2.dp,
            border = pillBorder,
            modifier = modifier.testTag("control-pill").mapChromeContainerColor(pillFill),
        ) { buttons() }
    }
}
