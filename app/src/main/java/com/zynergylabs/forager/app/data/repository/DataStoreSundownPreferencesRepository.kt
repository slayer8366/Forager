package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES
import com.zynergylabs.forager.app.domain.SundownPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 *
 * [scope] is DataStore's own default unless a caller passes one, as [DataStoreMapPreferencesRepository]
 * takes it: a test cancels it to release the file and read the settings back through a recreated
 * instance, the way a restart reads them (dispatch 2026-09-28-592, "both survive closing the app").
 */
class DataStoreSundownPreferencesRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : SundownPreferencesRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

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
