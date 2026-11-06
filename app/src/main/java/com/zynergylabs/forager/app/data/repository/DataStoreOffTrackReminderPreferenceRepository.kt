package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.zynergylabs.forager.app.domain.OffTrackReminderPreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [OffTrackReminderPreferenceRepository] backed by Jetpack DataStore (dispatch 2026-09-28-626).
 *
 * Built via [PreferenceDataStoreFactory.create] per instance, not the process-wide
 * `preferencesDataStore` delegate, for the Robolectric-isolation reason
 * [DataStoreMapPreferencesRepository]'s header records. Its own file, `off_track_reminder_preferences`.
 *
 * [enabledNow] is the checkbox's value as this instance last read or stored it; on, the default,
 * until something has read it.
 */
class DataStoreOffTrackReminderPreferenceRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : OffTrackReminderPreferenceRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

    /**
     * The checkbox as last read or stored by this instance. Production has one instance
     * (`AppContainer`) and every change goes through [setEnabled], so once anything has read it this
     * is the stored value. It is read when the app's screen starts (Settings loads it), when a
     * recording starts (the background check reads it) and when the recording service begins.
     *
     * Not read in a constructor: `AppContainer` is built for every Robolectric test, and a read
     * there makes this file's `DataStore` active, so any test building its own instance on the same
     * file fails with "multiple DataStores active for the same file". Seen on the first green run.
     */
    @Volatile private var cached: Boolean = DEFAULT_ENABLED

    /**
     * Serialises a read-and-cache against a store-and-cache, so a read that started before a
     * change cannot overwrite the cache with the value from before it.
     */
    private val cacheLock = Mutex()

    override suspend fun getEnabled(): Result<Boolean> = runCatchingCancellable {
        cacheLock.withLock { (dataStore.data.first()[KEY_ENABLED] ?: DEFAULT_ENABLED).also { cached = it } }
    }

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        cacheLock.withLock {
            dataStore.edit { prefs -> prefs[KEY_ENABLED] = enabled }
            cached = enabled
        }
    }

    override fun enabledNow(): Boolean = cached

    override suspend fun getLastSeenBlocked(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_LAST_SEEN_BLOCKED] ?: false
    }

    override suspend fun setLastSeenBlocked(blocked: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_LAST_SEEN_BLOCKED] = blocked }
    }

    private companion object {
        const val DATA_STORE_NAME = "off_track_reminder_preferences"

        /** The owner, Amendment 1 to dispatch -626: "on by default". */
        const val DEFAULT_ENABLED = true

        val KEY_ENABLED = booleanPreferencesKey("off_track_reminder.enabled")
        val KEY_LAST_SEEN_BLOCKED = booleanPreferencesKey("off_track_reminder.last_seen_blocked")
    }
}
