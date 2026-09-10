package com.zynergylabs.forager.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import kotlinx.coroutines.flow.first

/**
 * [UnitSystemPreferenceRepository] backed by Jetpack DataStore — the same file, factory and
 * Robolectric-isolation reasoning as the distance-unit repository it replaces (built via
 * [PreferenceDataStoreFactory.create] rather than the per-process `by preferencesDataStore`
 * delegate; see [DataStoreMapPreferencesRepository]).
 *
 * **Reads the legacy distance-unit key when its own key is absent.** The preference this replaces
 * stored `distance_unit.selected` in this same file; a tester who chose kilometres there must not
 * wake up in imperial. The mapping is [UnitSystem.forDistanceUnit], total by construction. The
 * legacy key is read, never written, and never deleted: a downgrade would still find it, and there
 * is nothing to gain from removing it. Once [setUnitSystem] has run, the new key wins.
 */
class DataStoreUnitSystemPreferenceRepository(context: Context) : UnitSystemPreferenceRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun getUnitSystem(): Result<UnitSystem> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        prefs[KEY_UNIT_SYSTEM]?.let { return@runCatchingCancellable UnitSystem.valueOf(it) }
        prefs[KEY_LEGACY_DISTANCE_UNIT]?.let { legacy ->
            val migrated = UnitSystem.forDistanceUnit(DistanceUnit.valueOf(legacy))
            Log.i(TAG, "No unit system stored; carrying the legacy distance unit '$legacy' forward as $migrated.")
            return@runCatchingCancellable migrated
        }
        UnitSystem.IMPERIAL
    }

    override suspend fun setUnitSystem(system: UnitSystem): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_UNIT_SYSTEM] = system.name }
    }

    private companion object {
        const val TAG = "UnitSystemPreference"

        /** Unchanged from the replaced repository, on purpose — it is what makes the legacy key readable. */
        const val DATA_STORE_NAME = "distance_unit_preferences"
        val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system.selected")
        val KEY_LEGACY_DISTANCE_UNIT = stringPreferencesKey("distance_unit.selected")
    }
}
