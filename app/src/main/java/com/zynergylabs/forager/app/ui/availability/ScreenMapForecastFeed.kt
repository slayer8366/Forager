package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.ui.map.MapForecastFeed
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.withUnavailableColourFieldsHidden

/**
 * What [rememberMapForecastFeed] works out for this composition: the colour fields with data, the layers the map draws, the
 * feed it reads its cells through, and the two States the feed reports to, read through their getters.
 */
internal class MapForecastFeedState(
    private val cellsShown: State<Map<String, ForecastCellsShown>>,
    private val zoomedOut: State<Boolean>,
    val availableColourFieldGroups: Map<String, String>,
    val drawnMapLayers: MapLayersState,
    val forecastFeed: MapForecastFeed?,
) {
    val forecastCellsShown: Map<String, ForecastCellsShown> get() = cellsShown.value
    val forecastZoomedOut: Boolean get() = zoomedOut.value
}

/**
 * The Maps tab's forecast cells (map layers L0b, B1 to B5; the comment above its call in [AvailabilityScreen] says why each
 * part is held where it is). Moved out of [AvailabilityScreen] whole by nav step 2, PR 4a (dispatch 2026-10-21-01, RECORD -1519), and called at the line
 * where the group stood, so its remembers and effects keep their order and keys (-1484). The holder wraps the original
 * State objects, never copies of them (Stage G). Made each composition, not remembered: the values in it are worked out each time,
 * as they were in the screen.
 */
@Composable
internal fun rememberMapForecastFeed(uiState: AvailabilityUiState, forecastCellStore: ForecastCellStore): MapForecastFeedState {
    val forecastCellsShownState = remember { mutableStateOf<Map<String, ForecastCellsShown>>(emptyMap()) }
    var forecastCellsShown by forecastCellsShownState
    val forecastZoomedOutState = remember { mutableStateOf(false) }
    var forecastZoomedOut by forecastZoomedOutState
    val availableColourFieldGroups = COLOUR_FIELDS.filter { it.group in uiState.forecastGroups }.associate { it.layerId to it.group }
    val drawnMapLayers = withUnavailableColourFieldsHidden(uiState.mapLayers, MAP_LAYER_REGISTRY, availableColourFieldGroups.keys)
    val forecastWeek = uiState.forecastWeek
    val forecastFeed = remember(forecastCellStore, forecastWeek, availableColourFieldGroups) {
        if (forecastWeek == null || availableColourFieldGroups.isEmpty()) {
            null
        } else {
            MapForecastFeed(
                store = forecastCellStore,
                week = forecastWeek,
                groupsByLayer = availableColourFieldGroups,
                onCellsShown = { forecastCellsShown = it },
                onZoomedOutChanged = { forecastZoomedOut = it },
            )
        }
    }
    return MapForecastFeedState(forecastCellsShownState, forecastZoomedOutState, availableColourFieldGroups, drawnMapLayers, forecastFeed)
}
