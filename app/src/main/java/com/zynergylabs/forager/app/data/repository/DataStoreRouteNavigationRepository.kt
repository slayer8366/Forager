package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zynergylabs.forager.app.domain.SettingsResetListener
import com.zynergylabs.forager.app.domain.routenav.RouteNavigation
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * [RouteNavigationRepository] backed by Jetpack DataStore (dispatch 2026-11-03-03, A4: "Closing the app mid-route also picks up
 * again, as waypoint navigation does today"), shaped as [DataStoreWaypointNavigationRepository] is.
 *
 * DataStore rather than Room, by CLAUDE.md's rule: the one current route navigation, which nothing will join to or filter against.
 * Three keys: the trip's id, the next stop's index, and whether a return was paused for it, written in one edit and cleared in one
 * edit. Its own file, `route_navigation`, built per instance with [PreferenceDataStoreFactory.create] for the Robolectric isolation
 * reason [DataStoreMapPreferencesRepository] records.
 */
class DataStoreRouteNavigationRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /** Told when this file was corrupt and has been reset (RECORD -660). */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : RouteNavigationRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

    /**
     * The kept navigation, or `null` for none. A trip id kept without its index or its return flag cannot have been written by
     * [setCurrent], which writes all three in one edit; it is refused as a failed read (which the caller logs) rather than read
     * with a guessed value. So is a negative index, which [RouteNavigation] refuses.
     */
    override suspend fun getCurrent(): Result<RouteNavigation?> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        prefs[KEY_TRIP_ID]?.let { id ->
            val index = prefs[KEY_NEXT_STOP_INDEX] ?: error("route navigation '$id' was kept without its next stop")
            val resumesReturn = prefs[KEY_RESUMES_RETURN] ?: error("route navigation '$id' was kept without its return flag")
            RouteNavigation(id, index, resumesReturn)
        }
    }

    override suspend fun setCurrent(navigation: RouteNavigation?): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs ->
            if (navigation == null) {
                prefs.remove(KEY_TRIP_ID)
                prefs.remove(KEY_NEXT_STOP_INDEX)
                prefs.remove(KEY_RESUMES_RETURN)
            } else {
                prefs[KEY_TRIP_ID] = navigation.tripId
                prefs[KEY_NEXT_STOP_INDEX] = navigation.nextStopIndex
                prefs[KEY_RESUMES_RETURN] = navigation.resumesReturn
            }
        }
    }

    private companion object {
        const val DATA_STORE_NAME = "route_navigation"
        val KEY_TRIP_ID = stringPreferencesKey("route_navigation.trip_id")
        val KEY_NEXT_STOP_INDEX = intPreferencesKey("route_navigation.next_stop_index")
        val KEY_RESUMES_RETURN = booleanPreferencesKey("route_navigation.resumes_return")
    }
}
