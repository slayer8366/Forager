package com.zynergylabs.forager.app.domain

/**
 * The map's layer choices as stored (map layers L0b, B3): which overlays are on, each colour field's
 * opacity, and the colour fields' order, by registry layer id. Plain values, so the domain names no
 * map type; `restoreMapLayersState` in `ui/map/layers` turns them into the state the map draws with,
 * and it is what checks them against the registry.
 *
 * Only what the user changed is here. A layer with no entry has its default: visible, at opacity 1,
 * in registry order (owner's rulings: "On by default", colour fields at 100% in registry order).
 * [order] is bottom to top, the way `MapLayersState.reorderableOrder` holds it.
 */
data class MapLayerPreferences(
    val visibility: Map<String, Boolean>,
    val opacity: Map<String, Float>,
    val order: List<String>,
) {
    companion object {
        val NONE = MapLayerPreferences(emptyMap(), emptyMap(), emptyList())
    }
}

/**
 * Where the map's layer choices persist across restarts (map layers L0b, B3; owner: "Across app
 * restarts (Recommended)"). Flat settings with no relations, so DataStore, not Room (CLAUDE.md); the
 * real implementation is `DataStoreMapPreferencesRepository`, in the existing `map_preferences` file
 * under `map.layer` keys. The basemap is not here, though it now persists too: it is not a layer, so
 * it has its own sibling, [BasemapPreferenceRepository], in the same file. Until dispatch
 * 2026-09-28-708 this comment said the basemap "stays session-only"; the owner then asked for the
 * app to "remember which map modes you had it on last so we don't have to keep switching to the
 * favorite".
 */
interface MapLayerPreferencesRepository {
    suspend fun getMapLayerPreferences(): Result<MapLayerPreferences>

    suspend fun setLayerVisible(layerId: String, visible: Boolean): Result<Unit>

    suspend fun setLayerOpacity(layerId: String, opacity: Float): Result<Unit>

    /** The colour fields' order, bottom to top. Replaces any order stored before. */
    suspend fun setLayerOrder(layerIds: List<String>): Result<Unit>
}
