package com.zynergylabs.forager.app.data.repository

import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Where `map_preferences` holds the map type the user chose (dispatch 2026-09-28-708). */
internal val BASEMAP_PREFERENCE_KEY = stringPreferencesKey("map.basemap")

/** Set once [BasemapKeyTrailStreetMigration] has run on this phone's `map_preferences`, so it never runs again. */
internal val BASEMAP_KEYS_TRAIL_STREET_DONE = booleanPreferencesKey("map.basemap.trail_street_codes")

/**
 * The one-time move of the stored map choice to the codes of dispatch 2026-11-03-05 (B1, RECORD -1999, -2000): the
 * Trail Map's key becomes "trail", and then OpenStreetMap's map, the Street map, takes "street". In that order, so a
 * Trail Map user is moved before "street" means the Street map, and once only, behind [BASEMAP_KEYS_TRAIL_STREET_DONE]:
 * after it, "street" is the Street map's own key, and a second run would turn a Street choice into the Trail Map.
 *
 * Every user keeps the map they chose: "street" (the Trail Map, A3) becomes "trail", "openstreetmap" becomes "street",
 * and anything else, "topographic", no value or a key no map has, is left as it is (an unknown key still moves to
 * Topographical, logged, where it is read: `AvailabilityMapDisplay`). A DataStore migration, so DataStore runs it before
 * the file's first read or write, in one transaction: no write of a new key can come before it.
 */
internal class BasemapKeyTrailStreetMigration(
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[BASEMAP_KEYS_TRAIL_STREET_DONE] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        val before = prefs[BASEMAP_PREFERENCE_KEY]
        var key = before
        // In this order (B1): the Trail Map's old key first, so only a key that was "openstreetmap" becomes "street".
        if (key == "street") key = "trail"
        if (key == "openstreetmap") key = "street"
        if (key != null && key != before) {
            prefs[BASEMAP_PREFERENCE_KEY] = key
            log("The stored map choice \"$before\" is now \"$key\" (dispatch 2026-11-03-05: Trail Map is \"trail\", the Street map \"street\").")
        }
        prefs[BASEMAP_KEYS_TRAIL_STREET_DONE] = true
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    private companion object {
        const val TAG = "BasemapKeyMigration"
    }
}
