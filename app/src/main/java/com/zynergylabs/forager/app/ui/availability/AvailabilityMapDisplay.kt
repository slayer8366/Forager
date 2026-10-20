package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ForecastAvailability
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.isoWeekStart
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.moveColourField
import com.zynergylabs.forager.app.ui.map.layers.restoreMapLayersState
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Maps tab draws from [AvailabilityViewModel] (UI split Phase 2.4, PR 3, dispatch 2026-10-19-33,
 * RECORD -1453), moved out of the ViewModel unchanged: the saved records and forecast groups read when the
 * tab is shown, the Layers sheet's choices, the basemap and the icon cluster's placement. It holds no state
 * of its own. Every value it reads from the screen's state comes through [uiState], every write goes
 * through [updateState], and its coroutines launch in [scope], the ViewModel's own `viewModelScope`, read
 * each time rather than kept. It starts and stops none of the ViewModel's tracked jobs; the ViewModel's
 * `init` calls its three loaders at the same positions as before.
 */
internal class AvailabilityMapDisplay(
    private val uiState: () -> AvailabilityUiState,
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val getMapRecords: suspend () -> MapRecords,
    private val forecastCellStore: ForecastCellStore,
    private val today: () -> LocalDate,
    private val mapLayerPreferencesRepository: MapLayerPreferencesRepository,
    private val basemapPreferenceRepository: BasemapPreferenceRepository,
    private val mapIconClusterPlacementRepository: MapIconClusterPlacementRepository,
    private val errorLog: ErrorLog,
) {

    /**
     * The Maps tab was shown (map layers L0b, B2 and B5): `AvailabilityScreen` calls this every time
     * the tab comes into view, compact or wide, which is this data's freshness mechanism. It reloads
     * every saved record the tab draws ([getMapRecords]), so a find saved on the Journal tab is on the
     * map the next time the map is shown, and asks the forecast store which groups it has data for in
     * this ISO week. A kind whose read failed is logged here and drawn as absent; the other kinds still
     * draw (CLAUDE.md, Errors).
     */
    fun onMapShown() {
        scope().launch { loadMapRecords() }
        scope().launch {
            val week = isoWeekStart(today())
            val groups = when (val availability = forecastCellStore.availability(week)) {
                ForecastAvailability.NoForecastData -> emptySet()
                is ForecastAvailability.Groups -> availability.groups
            }
            updateState { it.copy(forecastWeek = week, forecastGroups = groups) }
        }
    }

    /**
     * A find's delete has finished (dispatch 2026-09-28-291): the map no longer holds it. The records are a
     * snapshot taken when the tab was shown, and while the delete was pending the screen hid the find with a
     * filter; once the delete commits that filter stops, so the snapshot itself must lose the find or it is
     * drawn, and collected by a fan, again. Removed from the snapshot rather than re-read: the database row
     * is already gone, so there is nothing to wait for and no read that could still see it.
     */
    fun onFindDeleted(id: String) {
        updateState { state ->
            state.copy(mapRecords = state.mapRecords.copy(findMarkers = state.mapRecords.findMarkers.filterNot { it.recordId == id }))
        }
    }

    /**
     * An album photo's delete has finished (dispatch 2026-09-28-297): [onFindDeleted]'s reasoning, for the
     * photo markers. Removed from the snapshot, not re-read, for the same reason.
     */
    fun onPhotoDeleted(id: String) {
        updateState { state ->
            state.copy(mapRecords = state.mapRecords.copy(photoMarkers = state.mapRecords.photoMarkers.filterNot { it.recordId == id }))
        }
    }

    /**
     * An offline region's delete has finished (dispatch 2026-09-28-297): [onFindDeleted]'s reasoning, for the
     * region circles. A record's id here is the region's id as a string, as [GetMapRecordsUseCase] makes it.
     * Called from [AvailabilityOfflineRegionDeletes.commitOfflineRegionDelete]'s own success path, so no wiring through `MainActivity` is needed.
     */
    fun dropOfflineRegionFromMapRecords(id: Long) {
        updateState { state ->
            state.copy(
                mapRecords = state.mapRecords.copy(
                    offlineRegionCircles = state.mapRecords.offlineRegionCircles.filterNot { it.recordId == id.toString() },
                ),
            )
        }
    }

    /** The Maps tab's saved records, read now; the read [onMapShown] does and [AvailabilityViewModel.reloadAfterRestore] repeats. */
    suspend fun loadMapRecords() {
        val records = getMapRecords()
        records.failures.forEach { failure ->
            errorLog.w(TAG, "Couldn't load ${mapRecordKindLabel(failure.kind)} for the map.", failure.error)
        }
        updateState { it.copy(mapRecords = records) }
    }

    /**
     * The Layers sheet switched [layerId] (map layers L0b, B1 and B3): shown at once, then stored. A
     * failed write is logged; the choice still holds for this session.
     */
    fun onMapLayerVisibilityChanged(layerId: String, visible: Boolean) {
        updateState { state ->
            state.copy(mapLayers = state.mapLayers.withLayer(layerId) { it.copy(visible = visible) })
        }
        storeLayerChoice { mapLayerPreferencesRepository.setLayerVisible(layerId, visible) }
    }

    /**
     * The Layers sheet's opacity slider moved for [layerId]: the L0a multiplier, 0 to 1. A value
     * outside that range is refused and logged, never clamped (CLAUDE.md, Errors; `LayerState`'s own
     * rule), and nothing is stored.
     */
    fun onMapLayerOpacityChanged(layerId: String, opacity: Float) {
        if (!(opacity in 0f..1f)) {
            errorLog.w(
                TAG,
                "Refused a map layer opacity of $opacity for $layerId: outside 0 to 1.",
                IllegalArgumentException("opacity $opacity"),
            )
            return
        }
        updateState { state ->
            state.copy(mapLayers = state.mapLayers.withLayer(layerId) { it.copy(opacity = opacity) })
        }
        storeLayerChoice { mapLayerPreferencesRepository.setLayerOpacity(layerId, opacity) }
    }

    /**
     * The Layers sheet moved a colour field one place ("Move up", "Move down", or a step of its drag
     * handle). The whole colour-field order is stored, bottom to top. A move past either end changes
     * nothing and stores nothing. The map draws the new order at its next style load (terminal
     * `2026-09-27-72`, Deviations 4).
     */
    fun onColourFieldMoved(layerId: String, move: ColourFieldMove) {
        val before = uiState().mapLayers
        val after = moveColourField(before, MAP_LAYER_REGISTRY, layerId, move)
        if (after == before) return
        updateState { it.copy(mapLayers = after) }
        storeLayerChoice { mapLayerPreferencesRepository.setLayerOrder(after.reorderableOrder) }
    }

    private fun storeLayerChoice(write: suspend () -> Result<Unit>) {
        scope().launch {
            write().onFailure { error -> errorLog.w(TAG, "Couldn't store a map layer choice.", error) }
        }
    }

    /**
     * The stored layer choices, at start (map layers L0b, B3). Every stored choice the registry does
     * not allow is refused, logged one by one, and left at its default (`restoreMapLayersState`); a
     * failed read is logged and leaves every layer at its default ("On by default").
     */
    fun loadMapLayerPreferences() {
        scope().launch {
            mapLayerPreferencesRepository.getMapLayerPreferences().fold(
                onSuccess = { stored ->
                    val restored = restoreMapLayersState(stored, MAP_LAYER_REGISTRY)
                    restored.rejected.forEach { line ->
                        errorLog.w(TAG, "Refused a stored map layer choice: $line", IllegalStateException(line))
                    }
                    updateState { it.copy(mapLayers = restored.state) }
                },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the map layer choices.", error) },
            )
        }
    }

    /**
     * The Maps tab's basemap was picked, from the Layers sheet or the quick-fire picker (dispatch
     * 2026-09-28-708): shown at once, then stored. A failed write is logged; the choice still holds
     * for this session, as a layer choice's does ([storeLayerChoice]).
     */
    fun onMapModeSelected(mode: MapMode) {
        updateState { it.copy(mapMode = mode) }
        storeBasemap(mode)
    }

    /**
     * RECORD -711: a drag of the icon cluster ended, so its side and height (both orientations) are
     * stored, and kept here so a recreated screen restores the latest rather than the launch value. A
     * failed write is logged; the placement still holds for this session.
     */
    fun onMapIconClusterPlacementChanged(placement: MapIconClusterPlacement) {
        updateState { it.copy(mapIconClusterPlacement = placement) }
        scope().launch {
            mapIconClusterPlacementRepository.setMapIconClusterPlacement(placement)
                .onFailure { error -> errorLog.w(TAG, "Couldn't store the map icon cluster's position.", error) }
        }
    }

    /**
     * The stored icon cluster placement, at start (RECORD -711). Nothing stored is
     * [MapIconClusterPlacement.DEFAULT], where it always opened; a failed read is logged and also opens
     * there, storing nothing. Either way [AvailabilityUiState.mapIconClusterPlacement] is set, which is what
     * lets the screen draw the cluster. A drag that ended before the read landed wins over it.
     */
    fun loadMapIconClusterPlacement() {
        scope().launch {
            val restored = mapIconClusterPlacementRepository.getMapIconClusterPlacement().fold(
                onSuccess = { it ?: MapIconClusterPlacement.DEFAULT },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't read the map icon cluster's position; opening it where it always opened.", error)
                    MapIconClusterPlacement.DEFAULT
                },
            )
            updateState { if (it.mapIconClusterPlacement == null) it.copy(mapIconClusterPlacement = restored) else it }
        }
    }

    private fun storeBasemap(mode: MapMode) {
        scope().launch {
            basemapPreferenceRepository.setBasemapKey(mode.storageKey)
                .onFailure { error -> errorLog.w(TAG, "Couldn't store the basemap choice.", error) }
        }
    }

    /**
     * The stored basemap, at start (dispatch 2026-09-28-708). Nothing stored opens on
     * [MapMode.DEFAULT], as every launch did before the choice persisted. A stored key naming no map
     * type (Satellite's, removed by the same dispatch, or anything else) opens on
     * [MapMode.REPLACEMENT_FOR_UNKNOWN] (owner: "Topo (Recommended)"), is logged (CLAUDE.md: no unlogged
     * fallback), and the replacement is stored, so the log line fires once, not on every launch. A
     * failed read is logged and opens on [MapMode.DEFAULT], storing nothing: the stored value may be
     * fine and the next launch may read it.
     *
     * Every outcome sets [AvailabilityUiState.mapMode], which is what releases the map's style gate,
     * so it cannot hold the map blank. A pick made before the read lands wins over the read.
     */
    fun loadBasemapPreference() {
        scope().launch {
            val restored = basemapPreferenceRepository.getBasemapKey().fold(
                onSuccess = { key ->
                    if (key == null) {
                        MapMode.DEFAULT
                    } else {
                        MapMode.forStoredKey(key) ?: MapMode.REPLACEMENT_FOR_UNKNOWN.also { replacement ->
                            errorLog.w(
                                TAG,
                                "The stored basemap \"$key\" names no map type; opening on ${replacement.label} and storing that.",
                                IllegalStateException("unknown basemap key $key"),
                            )
                            storeBasemap(replacement)
                        }
                    }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't read the stored basemap; opening on ${MapMode.DEFAULT.label}.", error)
                    MapMode.DEFAULT
                },
            )
            updateState { if (it.mapMode == null) it.copy(mapMode = restored) else it }
        }
    }
}
