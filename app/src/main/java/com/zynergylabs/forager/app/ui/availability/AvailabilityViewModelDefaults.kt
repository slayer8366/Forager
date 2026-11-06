package com.zynergylabs.forager.app.ui.availability

// Moved from AvailabilityViewModel.kt unchanged (UI split Phase 1, PR 10g, dispatch 2026-10-19-24).

import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.MapRecordKind
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState

/** [MapLayersState] with [layerId]'s state changed by [change]. */
internal fun MapLayersState.withLayer(layerId: String, change: (LayerState) -> LayerState): MapLayersState =
    copy(layers = layers + (layerId to change(stateOf(layerId))))

/** How a failed kind reads in the log line: "Couldn't load tracks for the map." */
internal fun mapRecordKindLabel(kind: MapRecordKind): String = when (kind) {
    MapRecordKind.FINDS -> "finds"
    MapRecordKind.PHOTOS -> "photos"
    MapRecordKind.TRACKS -> "tracks"
    MapRecordKind.OFFLINE_REGIONS -> "offline regions"
}

/**
 * The default [MapLayerPreferencesRepository] for the suites that construct this ViewModel and never
 * touch the Layers sheet, the same shape as this class's other defaulted capabilities: nothing stored,
 * every write accepted. `MainActivity` passes the real `map_preferences` store.
 */
internal object NoStoredMapLayerPreferences : MapLayerPreferencesRepository {
    override suspend fun getMapLayerPreferences(): Result<MapLayerPreferences> = Result.success(MapLayerPreferences.NONE)

    override suspend fun setLayerVisible(layerId: String, visible: Boolean): Result<Unit> = Result.success(Unit)

    override suspend fun setLayerOpacity(layerId: String, opacity: Float): Result<Unit> = Result.success(Unit)

    override suspend fun setLayerOrder(layerIds: List<String>): Result<Unit> = Result.success(Unit)
}

/**
 * The default [BasemapPreferenceRepository] for the suites that construct this ViewModel and never
 * touch the basemap: nothing stored, so the map opens on [MapMode.DEFAULT]; every write accepted.
 * `MainActivity` passes the real `map_preferences` store.
 */
internal object NoStoredBasemap : BasemapPreferenceRepository {
    override suspend fun getBasemapKey(): Result<String?> = Result.success(null)

    override suspend fun setBasemapKey(key: String): Result<Unit> = Result.success(Unit)
}

/** The default [MapIconClusterPlacementRepository] for suites that never move the cluster: nothing stored, every write accepted. */
internal object NoStoredMapIconClusterPlacement : MapIconClusterPlacementRepository {
    override suspend fun getMapIconClusterPlacement(): Result<MapIconClusterPlacement?> = Result.success(null)

    override suspend fun setMapIconClusterPlacement(placement: MapIconClusterPlacement): Result<Unit> = Result.success(Unit)
}

/**
 * Shown above the Offline maps list when the regions restored from a backup could not be read, so the
 * list holds only the downloaded ones (dispatch 2026-09-28-658, M2). The owner's wording (RECORD -660,
 * Amendment 1 to -658). Reopening Offline maps reads both lists again.
 */
internal const val RESTORED_REGIONS_UNREADABLE_MESSAGE =
    "Couldn't read the regions from your backup. Showing downloaded regions only. Reopen Offline maps to try again."

/**
 * The banner when "Use current location", now the search panel's Search button, gets no fix. The owner's wording (RECORD -700:
 * "Option 1, but only refer to "Set on map" since that is right there."): Set on map sits beside Search in that panel.
 */
internal const val COULD_NOT_FIND_LOCATION_MESSAGE = "Couldn't find your location. Tap Set on map to choose a place."
