package com.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.model.DistanceUnit
import kotlinx.coroutines.flow.first

/**
 * [DistanceUnitPreferenceRepository] backed by Jetpack DataStore — see that interface's doc comment
 * for why (a single scalar preference, not rows to query, and one that needs to survive a
 * configuration change, which plain Compose state does not).
 *
 * Built via [PreferenceDataStoreFactory.create] directly rather than the `by preferencesDataStore(name = ...)`
 * singleton delegate, for the same Robolectric test-isolation reason [DataStoreMapPreferencesRepository]
 * documents on itself: that delegate caches per-process, not per instance, which breaks deleting the
 * backing file between `@Test` methods.
 */
class DataStoreDistanceUnitPreferenceRepository(context: Context) : DistanceUnitPreferenceRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun getDistanceUnit(): Result<DistanceUnit> = runCatchingCancellable {
        val stored = dataStore.data.first()[KEY_DISTANCE_UNIT]
        stored?.let { DistanceUnit.valueOf(it) } ?: DistanceUnit.MILES
    }

    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_DISTANCE_UNIT] = unit.name }
    }

    private companion object {
        const val DATA_STORE_NAME = "distance_unit_preferences"
        val KEY_DISTANCE_UNIT = stringPreferencesKey("distance_unit.selected")
    }
}
