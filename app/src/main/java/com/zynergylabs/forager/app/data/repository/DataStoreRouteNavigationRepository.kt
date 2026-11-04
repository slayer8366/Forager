package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zynergylabs.forager.app.domain.SettingsResetListener
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationCurrent
import com.zynergylabs.forager.app.domain.routenav.RouteNavigationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * [RouteNavigationRepository] backed by Jetpack DataStore (dispatch 2026-11-03-03, A4 and A8), shaped as
 * [DataStoreWaypointNavigationRepository] is.
 *
 * DataStore rather than Room, by CLAUDE.md's rule: flat values that nothing will join to or filter against. The current navigation
 * is two keys, the trip's id and whether a return was paused for it, written in one edit and cleared in one edit. Each route's
 * progress is one key of its own, `route_navigation.progress.<trip id>`, the next stop it had not reached (A8: each route keeps its
 * own, through Stop). Its own file, `route_navigation`, built per instance with [PreferenceDataStoreFactory.create] for the
 * Robolectric isolation reason [DataStoreMapPreferencesRepository] records.
 */
class DataStoreRouteNavigationRepository(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /** Told when this file was corrupt and has been reset (RECORD -660). */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : RouteNavigationRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset, scope)

    /**
     * The kept navigation, or `null` for none. A trip id kept without its return flag cannot have been written by [setCurrent],
     * which writes both in one edit; it is refused as a failed read (which the caller logs) rather than read with a guessed flag.
     */
    override suspend fun getCurrent(): Result<RouteNavigationCurrent?> = runCatchingCancellable {
        val prefs = dataStore.data.first()
        prefs[KEY_TRIP_ID]?.let { id ->
            val resumesReturn = prefs[KEY_RESUMES_RETURN] ?: error("route navigation '$id' was kept without its return flag")
            RouteNavigationCurrent(id, resumesReturn)
        }
    }

    override suspend fun setCurrent(current: RouteNavigationCurrent?): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs ->
            if (current == null) {
                prefs.remove(KEY_TRIP_ID)
                prefs.remove(KEY_RESUMES_RETURN)
            } else {
                prefs[KEY_TRIP_ID] = current.tripId
                prefs[KEY_RESUMES_RETURN] = current.resumesReturn
            }
        }
    }

    /** A negative index cannot have been written by [setProgress], which refuses one; it is refused as a failed read. */
    override suspend fun getProgress(tripId: String): Result<Int?> = runCatchingCancellable {
        dataStore.data.first()[progressKey(tripId)]?.also { check(it >= 0) { "route '$tripId' was kept at stop index $it" } }
    }

    override suspend fun setProgress(tripId: String, nextStopIndex: Int?): Result<Unit> = runCatchingCancellable {
        require(nextStopIndex == null || nextStopIndex >= 0) { "a next stop index is at least 0; got $nextStopIndex" }
        dataStore.edit { prefs ->
            if (nextStopIndex == null) prefs.remove(progressKey(tripId)) else prefs[progressKey(tripId)] = nextStopIndex
        }
    }

    private companion object {
        const val DATA_STORE_NAME = "route_navigation"
        val KEY_TRIP_ID = stringPreferencesKey("route_navigation.trip_id")
        val KEY_RESUMES_RETURN = booleanPreferencesKey("route_navigation.resumes_return")
        fun progressKey(tripId: String) = intPreferencesKey("route_navigation.progress.$tripId")
    }
}
