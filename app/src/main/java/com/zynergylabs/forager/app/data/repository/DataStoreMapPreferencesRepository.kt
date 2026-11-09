package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.zynergylabs.forager.app.domain.BasemapPreferenceRepository
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository
import com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.model.Region
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * [MapPreferencesRepository] backed by Jetpack DataStore rather than Room — see that interface's
 * doc comment for why a key-value store fits this better than a table.
 *
 * Built via [PreferenceDataStoreFactory.create] directly, holding its own [androidx.datastore.core.DataStore]
 * instance, rather than `by preferencesDataStore(name = ...)` — the usual Context-extension
 * singleton delegate. That delegate caches its `DataStore` for the lifetime of the *process* the
 * property is defined in, not per [DataStoreMapPreferencesRepository] instance, which is invisible
 * in production (`AppContainer` constructs exactly one of these) but broke test isolation: a
 * Robolectric test-suite JVM keeps that process-wide cache alive across every `@Test` method in a
 * class, so deleting the backing file between tests couldn't reset it. One instance per
 * [DataStoreMapPreferencesRepository] avoids that without losing anything real production code
 * depends on, since nothing else in this app ever constructs a second one against the same file.
 *
 * [scope] is DataStore's own default scope unless a caller passes one. A test passes one so it can
 * cancel it: DataStore closes its file when its scope completes, which is the only way to open a second
 * reader on the file and check what a restart would read (`DataStoreMapLayerPreferencesTest`).
 */
class DataStoreMapPreferencesRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : MapPreferencesRepository, MapLayerPreferencesRepository, BasemapPreferenceRepository,
    MapIconClusterPlacementRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

    override suspend fun getLastPickedRegion(): Result<Region?> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        val lat = prefs[KEY_LAST_PICKED_LAT]
        val lng = prefs[KEY_LAST_PICKED_LNG]
        val radiusKm = prefs[KEY_LAST_PICKED_RADIUS_KM]
        if (lat == null || lng == null || radiusKm == null) null else Region(lat, lng, radiusKm)
    }

    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_PICKED_LAT] = region.lat
            prefs[KEY_LAST_PICKED_LNG] = region.lng
            prefs[KEY_LAST_PICKED_RADIUS_KM] = region.radiusKm
        }
    }

    override suspend fun getStaleThresholdDays(): Result<Int> = runCatchingCancellable {
        dataStore.data.first()[KEY_STALE_THRESHOLD_DAYS] ?: DEFAULT_STALE_THRESHOLD_DAYS
    }

    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_STALE_THRESHOLD_DAYS] = days }
    }

    override suspend fun getNightModeMaps(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_NIGHT_MODE_MAPS] ?: false
    }

    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_NIGHT_MODE_MAPS] = night }
    }

    override suspend fun getMapFullscreen(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_MAP_FULLSCREEN] ?: false
    }

    override suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_MAP_FULLSCREEN] = fullscreen }
    }

    /**
     * Map layers L0b, B3: the layer choices live in this same file and this same [dataStore], under
     * `map.layer` keys keyed by registry id. The same instance, not a second repository with its own
     * `PreferenceDataStoreFactory.create`, because DataStore refuses a second live instance on one file
     * ("There are multiple DataStores active for the same file"; see
     * `DataStorePhotoLocationPreferenceRepositoryTest`), so this class implements both interfaces and
     * `AppContainer` hands the one instance out as each.
     *
     * Every key under [LAYER_KEY_PREFIX] is read back by its name: `map.layer.<id>.visible` (boolean)
     * and `map.layer.<id>.opacity` (float). The order is one string, [KEY_LAYER_ORDER], the ids joined
     * by [LAYER_ORDER_SEPARATOR]; registry ids never contain one. Nothing here checks a value against
     * the registry: `restoreMapLayersState` does, and reports what it refuses.
     */
    override suspend fun getMapLayerPreferences(): Result<MapLayerPreferences> = runCatchingCancellable {
        val prefs = dataStore.data.first().asMap()
        val visibility = mutableMapOf<String, Boolean>()
        val opacity = mutableMapOf<String, Float>()
        prefs.forEach { (key, value) ->
            val name = key.name
            if (!name.startsWith(LAYER_KEY_PREFIX)) return@forEach
            when {
                name.endsWith(VISIBLE_SUFFIX) && value is Boolean ->
                    visibility[name.removePrefix(LAYER_KEY_PREFIX).removeSuffix(VISIBLE_SUFFIX)] = value
                name.endsWith(OPACITY_SUFFIX) && value is Float ->
                    opacity[name.removePrefix(LAYER_KEY_PREFIX).removeSuffix(OPACITY_SUFFIX)] = value
            }
        }
        val order = prefs[KEY_LAYER_ORDER]?.let { it as? String }
            ?.split(LAYER_ORDER_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        MapLayerPreferences(visibility = visibility, opacity = opacity, order = order)
    }

    override suspend fun setLayerVisible(layerId: String, visible: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[booleanPreferencesKey("$LAYER_KEY_PREFIX$layerId$VISIBLE_SUFFIX")] = visible }
    }

    override suspend fun setLayerOpacity(layerId: String, opacity: Float): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[floatPreferencesKey("$LAYER_KEY_PREFIX$layerId$OPACITY_SUFFIX")] = opacity }
    }

    override suspend fun setLayerOrder(layerIds: List<String>): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_LAYER_ORDER] = layerIds.joinToString(LAYER_ORDER_SEPARATOR) }
    }

    /**
     * Dispatch 2026-09-28-708: the Maps tab's basemap, one string under [KEY_BASEMAP] in this same file
     * and [dataStore], for the same one-instance reason as the layer choices above. Stored as given and
     * read back as stored; `AvailabilityViewModel` resolves it against `MapMode` and replaces a key
     * that names no mode.
     */
    override suspend fun getBasemapKey(): Result<String?> = runCatchingCancellable {
        dataStore.data.first()[KEY_BASEMAP]
    }

    override suspend fun setBasemapKey(key: String): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_BASEMAP] = key }
    }

    /**
     * RECORD -711: the icon cluster's side and height, four keys under `map.icon_cluster` in this same file
     * and [dataStore]. `null` when none of the four is stored; a key missing beside others (which no write
     * here produces, since all four are written together) reads as [MapIconClusterPlacement.DEFAULT]'s value.
     */
    override suspend fun getMapIconClusterPlacement(): Result<MapIconClusterPlacement?> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        val portraitOnLeft = prefs[KEY_CLUSTER_PORTRAIT_LEFT]
        val portraitOffsetDp = prefs[KEY_CLUSTER_PORTRAIT_OFFSET_DP]
        val landscapeOnPortSide = prefs[KEY_CLUSTER_LANDSCAPE_PORT_SIDE]
        val landscapeOffsetDp = prefs[KEY_CLUSTER_LANDSCAPE_OFFSET_DP]
        if (portraitOnLeft == null && portraitOffsetDp == null && landscapeOnPortSide == null && landscapeOffsetDp == null) {
            null
        } else {
            val default = MapIconClusterPlacement.DEFAULT
            MapIconClusterPlacement(
                portraitOnLeft = portraitOnLeft ?: default.portraitOnLeft,
                portraitOffsetDp = portraitOffsetDp ?: default.portraitOffsetDp,
                landscapeOnPortSide = landscapeOnPortSide ?: default.landscapeOnPortSide,
                landscapeOffsetDp = landscapeOffsetDp ?: default.landscapeOffsetDp,
            )
        }
    }

    override suspend fun setMapIconClusterPlacement(placement: MapIconClusterPlacement): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs ->
            prefs[KEY_CLUSTER_PORTRAIT_LEFT] = placement.portraitOnLeft
            prefs[KEY_CLUSTER_PORTRAIT_OFFSET_DP] = placement.portraitOffsetDp
            prefs[KEY_CLUSTER_LANDSCAPE_PORT_SIDE] = placement.landscapeOnPortSide
            prefs[KEY_CLUSTER_LANDSCAPE_OFFSET_DP] = placement.landscapeOffsetDp
        }
    }

    private companion object {
        const val DATA_STORE_NAME = "map_preferences"
        val KEY_LAST_PICKED_LAT = doublePreferencesKey("offline_map.last_picked_lat")
        val KEY_LAST_PICKED_LNG = doublePreferencesKey("offline_map.last_picked_lng")
        val KEY_LAST_PICKED_RADIUS_KM = intPreferencesKey("offline_map.last_picked_radius_km")
        val KEY_STALE_THRESHOLD_DAYS = intPreferencesKey("offline_map.stale_threshold_days")
        val KEY_NIGHT_MODE_MAPS = booleanPreferencesKey("night_mode.maps")
        val KEY_MAP_FULLSCREEN = booleanPreferencesKey("map.fullscreen")
        val KEY_BASEMAP = stringPreferencesKey("map.basemap")
        val KEY_CLUSTER_PORTRAIT_LEFT = booleanPreferencesKey("map.icon_cluster.portrait_left")
        val KEY_CLUSTER_PORTRAIT_OFFSET_DP = floatPreferencesKey("map.icon_cluster.portrait_offset_dp")
        val KEY_CLUSTER_LANDSCAPE_PORT_SIDE = booleanPreferencesKey("map.icon_cluster.landscape_port_side")
        val KEY_CLUSTER_LANDSCAPE_OFFSET_DP = floatPreferencesKey("map.icon_cluster.landscape_offset_dp")

        // Map layers L0b, B3: per-layer keys are built from the registry id at use.
        const val LAYER_KEY_PREFIX = "map.layer."
        const val VISIBLE_SUFFIX = ".visible"
        const val OPACITY_SUFFIX = ".opacity"
        val KEY_LAYER_ORDER = stringPreferencesKey("map.layer_order")
        const val LAYER_ORDER_SEPARATOR = ","
    }
}
