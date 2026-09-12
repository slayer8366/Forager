package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES
import com.zynergylabs.forager.app.domain.SundownPreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * [SundownPreferencesRepository] backed by Jetpack DataStore.
 *
 * Built via [PreferenceDataStoreFactory.create] directly rather than the
 * `by preferencesDataStore(name = ...)` Context-extension delegate, for the reason
 * [DataStoreMapPreferencesRepository]'s header records at length: that delegate caches its
 * `DataStore` for the lifetime of the *process*, not per instance, which is invisible in
 * production (`AppContainer` constructs exactly one) and breaks Robolectric isolation, because a
 * suite JVM keeps the cache alive across every `@Test` in a class and deleting the backing file
 * between tests cannot reset it.
 *
 * Its own file, `sundown_preferences`, matching the one-file-per-concern shape of the three
 * sibling repositories.
 */
class DataStoreSundownPreferencesRepository(context: Context) : SundownPreferencesRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATA_STORE_NAME) },
    )

    override suspend fun getDarknessMarginMinutes(): Result<Int> = runCatchingCancellable {
        dataStore.data.first()[KEY_DARKNESS_MARGIN_MINUTES] ?: DEFAULT_DARKNESS_MARGIN_MINUTES
    }

    override suspend fun setDarknessMarginMinutes(minutes: Int): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_DARKNESS_MARGIN_MINUTES] = minutes }
    }

    override suspend fun getAlertsEnabled(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_ALERTS_ENABLED] ?: DEFAULT_ALERTS_ENABLED
    }

    override suspend fun setAlertsEnabled(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_ALERTS_ENABLED] = enabled }
    }

    private companion object {
        const val DATA_STORE_NAME = "sundown_preferences"

        /** See [SundownPreferencesRepository.getAlertsEnabled] for why this is on rather than off. */
        const val DEFAULT_ALERTS_ENABLED = true

        val KEY_DARKNESS_MARGIN_MINUTES = intPreferencesKey("sundown.darkness_margin_minutes")
        val KEY_ALERTS_ENABLED = booleanPreferencesKey("sundown.alerts_enabled")
    }
}
