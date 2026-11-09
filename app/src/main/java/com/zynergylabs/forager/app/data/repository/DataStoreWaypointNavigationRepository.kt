package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.WaypointNavigationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * [WaypointNavigationRepository] backed by Jetpack DataStore (dispatch 2026-09-28-502, Amendment 1).
 *
 * DataStore rather than Room, by CLAUDE.md's rule ("Room for data that relates; DataStore for flat
 * settings"): the one current navigation, cleared when it ends, which nothing will join to or filter
 * against. Two keys, not one: the waypoint's id, and whether a return was paused for it, so that ending
 * the navigation after the app was closed and opened again still picks the return back up (step 6). Both
 * are written in one edit and cleared in one edit.
 *
 * Its own file, `waypoint_navigation`, as each concern here has, built per instance with
 * [PreferenceDataStoreFactory.create] for the Robolectric isolation reason
 * [DataStoreMapPreferencesRepository] records. [scope] is the DataStore's own, as there, so a test can
 * close one instance before opening the next on the same file.
 */
class DataStoreWaypointNavigationRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : WaypointNavigationRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

    /**
     * The kept navigation, or `null` for none. An id kept without its return flag cannot have been
     * written by [setCurrent], which writes both in one edit; it is refused as a failed read (which the
     * caller logs) rather than read with a guessed flag.
     */
    override suspend fun getCurrent(): Result<WaypointNavigation?> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        prefs[KEY_TARGET_ID]?.let { id ->
            val resumesReturn = prefs[KEY_RESUMES_RETURN] ?: error("waypoint navigation '$id' was kept without its return flag")
            WaypointNavigation(id, resumesReturn)
        }
    }

    override suspend fun setCurrent(navigation: WaypointNavigation?): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs ->
            if (navigation == null) {
                prefs.remove(KEY_TARGET_ID)
                prefs.remove(KEY_RESUMES_RETURN)
            } else {
                prefs[KEY_TARGET_ID] = navigation.waypointId
                prefs[KEY_RESUMES_RETURN] = navigation.resumesReturn
            }
        }
    }

    private companion object {
        const val DATA_STORE_NAME = "waypoint_navigation"
        val KEY_TARGET_ID = stringPreferencesKey("waypoint_navigation.target_id")
        val KEY_RESUMES_RETURN = booleanPreferencesKey("waypoint_navigation.resumes_return")
    }
}
