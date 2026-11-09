package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-14, UI split Phase 1, PR 9a).

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING
import com.zynergylabs.forager.app.ui.map.mapIconChromeFillColor
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.mapIconClusterChildColor
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.theme.Spacing

/** The icon cluster, moved out of [CompactMapTab] unchanged (RECORD -741). [topLimitPx] and [noticeBottomPx] are worked out there. */
@Composable
internal fun BoxScope.CompactMapIconCluster(
    cluster: MapIconClusterState,
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    recording: CompactMapRecording,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
    onResumeTracking: () -> Unit,
    onResetOrientation: () -> Unit,
    mapMode: MapMode,
    onOpenLayers: () -> Unit,
    onOpenActionMenu: () -> Unit,
    topLimitPx: Float,
    noticeBottomPx: Float,
    controlsPadding: PaddingValues,
) {
    val isNavigating = navigation.isNavigating
    val onReturnToRoute = navigation.onReturnToRoute
    val returnToStart = navigation.returnToStart
    val isReturning = navigation.isReturning
    val isOffTrack = navigation.isOffTrack
    val onToggleReturning = navigation.onToggleReturning
    val isRecording = recording.isRecording
    val onToggleRecording = recording.onToggleRecording
    // The icon cluster (the bar and the record | return pill, their handles, drag, snap and clamps):
    // MapIconCluster, shared with the tablet's map (J6c). Composed *before* CompassElevationStrip,
    // not after: composition order is paint and hit-test order for overlapping siblings in this
    // Box, and MapIconBar's Surface intercepts touches across its full bounds, which on a short
    // viewport reach up into the strip's row; the strip's own control must win any overlap
    // (AvailabilityScreenMapIconStackTest's touch-interaction test on a w360dp-h640dp viewport).
    val phoneBar: @Composable (Modifier, Color, Dp, Boolean) -> Unit = { barModifier, barFill, barRowSpacing, barFullSquareHits ->
        MapIconBar(
            isFullscreen = isFullscreen,
            onToggleFullscreen = onToggleFullscreen,
            onLocateMe = {
                // Ruling D: while navigating, locate brings the navigation view back, as
                // "Return to Route" does; plain tracking would drop the compass follow.
                if (isNavigating) onReturnToRoute() else onResumeTracking()
                onLocateMe()
            },
            onResetOrientation = onResetOrientation,
            mapMode = mapMode,
            onOpenLayers = onOpenLayers,
            onAdd = {
                // No location to grab any more — the button just opens
                // the menu; the location comes from
                // CentrePinLocationPickerOverlay's own camera tracking
                // once a choice is made. See this function's own doc
                // comment.
                onOpenActionMenu()
            },
            fillColor = barFill,
            rowSpacing = barRowSpacing,
            fullSquareHits = barFullSquareHits,
            modifier = barModifier,
        )
    }
    MapIconCluster(
        state = cluster,
        isFullscreen = isFullscreen,
        // The cluster cannot rise above where SearchDropdown itself starts: topInset (about the
        // search bar's height) plus the strip's own clearance (icon-bar-drag-refinements, Item 4).
        // Owner's ruling (a), continuation 2026-09-28-172 ("never above the search bar's bottom"): in the landscape L the limit is
        // searchBarBottom, the search bar's own bottom (settled, not animated), without the strip clearance (the compass strip is in the other corner there,
        // nothing else is drawn in that band beside the notice and the chips, which make room for the L, and the SearchDropdown
        // starts below it); the L pushes down to it as well as up. Portrait keeps topInset + the clearance.
        topLimitPx = topLimitPx,
        noticeBottomPx = noticeBottomPx,
        controlsPadding = controlsPadding,
        bar = { barModifier -> phoneBar(barModifier, mapIconClusterChildColor(), Spacing.xs, false) },
        // Landscape L: the bar's rows 48 dp apart with no end padding (240 dp), one layer at the standing 0.8 fill, every row
        // taking touches across its full 48 x 48 square (owner's "A" and ruling (d), continuations -160 and -172).
        landscapeBar = { barModifier -> phoneBar(barModifier, Color.Unspecified, MAP_ICON_BAR_LANDSCAPE_ROW_SPACING, true) },
        pill = { onLeftSide ->
            // Composed whenever MapIconBar is (regardless of isRecording — record start/stop must
            // stay reachable before the first recording starts; isRecording flows in as a plain
            // parameter, see TrailheadControls' own doc comment, not a presence check).
            TrailheadControls(
                isRecording = isRecording,
                onToggleRecording = onToggleRecording,
                returnToStart = returnToStart,
                isReturning = isReturning,
                isOffTrack = isOffTrack,
                onToggleReturning = onToggleReturning,
                distanceUnit = uiState.distanceUnit,
                onLeftSide = onLeftSide,
                // Dispatch -502: the X-circle for either navigation, a waypoint's included.
                isNavigating = isNavigating,
            )
        },
        // Landscape L: the pill turned horizontal (record under the bar's column, return inboard, 96 x 48), one layer at the
        // standing 0.8 fill, both buttons taking touches across their full 48 x 48 squares.
        landscapePill = { onLeftSide ->
            TrailheadControls(
                isRecording = isRecording,
                onToggleRecording = onToggleRecording,
                returnToStart = returnToStart,
                isReturning = isReturning,
                isOffTrack = isOffTrack,
                onToggleReturning = onToggleReturning,
                distanceUnit = uiState.distanceUnit,
                onLeftSide = onLeftSide,
                horizontal = true,
                fillColor = mapIconChromeFillColor(),
                rowSpacing = MAP_ICON_BAR_LANDSCAPE_ROW_SPACING,
                isNavigating = isNavigating,
            )
        },
        // T11 fixes (RECORD -766, P4): the L slides below the navigation display while navigating on its side.
        isNavigating = isNavigating,
    )
}
