package com.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.model.Region
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
 */
class DataStoreMapPreferencesRepository(context: Context) : MapPreferencesRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

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

    private companion object {
        const val DATA_STORE_NAME = "map_preferences"
        val KEY_LAST_PICKED_LAT = doublePreferencesKey("offline_map.last_picked_lat")
        val KEY_LAST_PICKED_LNG = doublePreferencesKey("offline_map.last_picked_lng")
        val KEY_LAST_PICKED_RADIUS_KM = intPreferencesKey("offline_map.last_picked_radius_km")
        val KEY_STALE_THRESHOLD_DAYS = intPreferencesKey("offline_map.stale_threshold_days")
        val KEY_NIGHT_MODE_MAPS = booleanPreferencesKey("night_mode.maps")
    }
}
