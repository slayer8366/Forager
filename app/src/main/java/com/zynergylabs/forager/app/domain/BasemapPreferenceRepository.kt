package com.zynergylabs.forager.app.domain

/**
 * Where the Maps tab's basemap choice persists across restarts (dispatch 2026-09-28-708). The owner,
 * to the step path: "have the app remember which map modes you had it on last so we don't have to
 * keep switching to the favorite", then "Yes, that's it (Recommended)".
 *
 * A flat preference with no relations, so DataStore, not Room (CLAUDE.md); the real implementation is
 * `DataStoreMapPreferencesRepository`, in the same `map_preferences` file as the layer choices
 * ([MapLayerPreferencesRepository]), under the key `map.basemap`. A sibling interface rather than two
 * more methods on [MapLayerPreferencesRepository], because the basemap is not a layer and that
 * interface's fakes have no reason to grow.
 *
 * The value is a plain key string, so the domain names no map type: `MapMode.storageKey` writes it and
 * `MapMode.forStoredKey` reads it back, and the caller (`AvailabilityViewModel`) decides what a key
 * naming no mode becomes. Nothing here checks it.
 */
interface BasemapPreferenceRepository {
    /** The stored key, or `null` when nothing has been stored (a phone that has never changed it). */
    suspend fun getBasemapKey(): Result<String?>

    suspend fun setBasemapKey(key: String): Result<Unit>
}
