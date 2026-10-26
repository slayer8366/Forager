package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.ui.map.MapCompass
import com.zynergylabs.forager.app.ui.map.MapPosition
import com.zynergylabs.forager.app.ui.map.fanout.MapKeepOuts
import com.zynergylabs.forager.app.ui.map.rememberShownPosition

/**
 * What every map under the screen reads through the drawer host's composition locals: the fan-out's keep-outs, the compass
 * and the position. Passed on as the objects [rememberScreenMapLocals] remembers. Built afresh on each composition and not
 * remembered, so it adds no slot.
 */
internal class ScreenMapLocals(
    val mapKeepOuts: MapKeepOuts,
    val mapCompass: MapCompass,
    val mapPosition: MapPosition,
)

/**
 * Moved out of [AvailabilityScreen] whole by dispatch 2026-10-26-17 (RECORD -1682), a pure move, and called at the line where
 * the group stood, so its remembers keep their order and keys (-1484).
 */
@Composable
internal fun rememberScreenMapLocals(
    uiState: AvailabilityUiState,
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    currentTime: CurrentTimeProvider,
): ScreenMapLocals {
    // Where the controls over the map are, for the marker fan-out to keep clear of (MapKeepOut.kt).
    val mapKeepOuts = remember { MapKeepOuts() }
    // Ruling A (continuation 2026-09-28-432): every map under this screen can point its puck to true
    // north, the pickers included, with no new parameter through the Journal (the planner's ruling (i)).
    val liveFixForMaps = rememberUpdatedState(uiState.liveFix)
    // Dispatch 2026-09-28-510: true north is worked out at the best position there is, approximate or
    // last known included (AvailabilityUiState.headingFix), so the puck's heading does not wait for GPS.
    val headingFixForMaps = rememberUpdatedState(uiState.headingFix)
    val mapCompass = remember(compassProvider, computeTrueHeading, headingFixForMaps) { MapCompass(compassProvider, computeTrueHeading, headingFixForMaps) }
    // Dispatch 2026-09-28-510 (the owner: "App feeds the dot"): the position every map under this screen
    // draws its dot at, the same way the compass reaches them.
    val shownPosition = rememberShownPosition(uiState.liveFix, uiState.approximateFix, uiState.lastKnownFix, currentTime)
    val mapPosition = remember(shownPosition, liveFixForMaps) { MapPosition(shownPosition, liveFixForMaps) }
    return ScreenMapLocals(mapKeepOuts, mapCompass, mapPosition)
}
