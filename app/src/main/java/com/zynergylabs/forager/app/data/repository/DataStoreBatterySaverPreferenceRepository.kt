package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.zynergylabs.forager.app.domain.BatterySaverPreferenceRepository
import com.zynergylabs.forager.app.domain.SettingsResetListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [BatterySaverPreferenceRepository] backed by Jetpack DataStore (dispatch 2026-09-28-767). Its own file,
 * `battery_saver_preferences`, built per instance as [DataStoreOffTrackReminderPreferenceRepository] is,
 * for the Robolectric-isolation reason [DataStoreMapPreferencesRepository]'s header records.
 *
 * [enabled] is not read in the constructor, for the reason [DataStoreOffTrackReminderPreferenceRepository]
 * records: `AppContainer` is built for every Robolectric test, and a read there makes the file active.
 * It is read when the map's screen loads and when the recording service begins.
 */
class DataStoreBatterySaverPreferenceRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : BatterySaverPreferenceRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

    private val state = MutableStateFlow(DEFAULT_ENABLED)
    override val enabled: StateFlow<Boolean> = state.asStateFlow()

    /** As [DataStoreOffTrackReminderPreferenceRepository]'s: a read that began before a change cannot overwrite it. */
    private val cacheLock = Mutex()

    override suspend fun getEnabled(): Result<Boolean> = runCatchingCancellable {
        cacheLock.withLock { (dataStore.data.first()[KEY_ENABLED] ?: DEFAULT_ENABLED).also { state.value = it } }
    }

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        cacheLock.withLock {
            dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
            state.value = enabled
        }
    }

    private companion object {
        const val DATA_STORE_NAME = "battery_saver_preferences"

        /** The owner (RECORD -766): off by default. */
        const val DEFAULT_ENABLED = false

        val KEY_ENABLED = booleanPreferencesKey("battery_saver.enabled")
    }
}
