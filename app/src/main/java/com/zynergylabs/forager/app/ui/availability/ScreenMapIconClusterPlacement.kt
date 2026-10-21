package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement

/**
 * The icon cluster's position, with its stored side and height applied and every ended drag stored. Moved out of [AvailabilityScreen] whole by nav step 2, PR 4a (dispatch 2026-10-21-01, RECORD -1519), and called at the line
 * where the group stood, so its remembers and effects keep their order and keys (-1484). The holder wraps the original
 * State objects, never copies of them (Stage G).
 * It returns the holder [rememberMapIconClusterPositionState] makes, unchanged.
 */
@Composable
internal fun rememberPersistedMapIconClusterPosition(
    uiState: AvailabilityUiState,
    onMapIconClusterPlacementChanged: (MapIconClusterPlacement) -> Unit,
): MapIconClusterPositionState {
    // The icon cluster's position, held here rather than in CompactMapTab so it survives leaving
    // and returning to the Map tab — see MapIconClusterPositionState's own doc comment.
    //
    // RECORD -711: its side and height also persist across restarts (the owner: "have the map icon bar
    // persist between restarts so left handed users don't need to change it every time they open the
    // app", "Side and height (Recommended)"). The holder waits for uiState.mapIconClusterPlacement and is
    // not drawn until it has applied it (first effect), so there is no frame on the default side. Every
    // drag that ends is stored (second effect), keyed on the holder's own count of ended drags rather
    // than on the position itself: a write per drag, not per frame of one, and never a write of the
    // default before the read lands (the trap isMapFullscreen's comment above describes).
    val mapIconClusterPosition = rememberMapIconClusterPositionState(awaitingPlacement = true)
    val mapIconClusterDensity = LocalDensity.current
    LaunchedEffect(uiState.mapIconClusterPlacement) {
        uiState.mapIconClusterPlacement?.let { mapIconClusterPosition.applyPlacement(it, mapIconClusterDensity) }
    }
    LaunchedEffect(mapIconClusterPosition.settledCount) {
        if (mapIconClusterPosition.settledCount > 0) {
            onMapIconClusterPlacementChanged(mapIconClusterPosition.placement(mapIconClusterDensity))
        }
    }
    return mapIconClusterPosition
}
