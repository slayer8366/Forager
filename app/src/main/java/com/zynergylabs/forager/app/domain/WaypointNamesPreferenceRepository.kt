package com.zynergylabs.forager.app.domain

/**
 * Where Settings > Map > "Waypoint names" persists across restarts (dispatch 2026-11-02-04, B2; RECORD -1974). The owner:
 * "Settings > Map", a checkbox "Waypoint names" under "Crowded icons", on by default. A flat preference with no relations,
 * so DataStore, not Room (CLAUDE.md); the real implementation is `DataStoreMapPreferencesRepository`, key
 * `map.waypoint_names`. A sibling interface, as [CrowdedIconsPreferenceRepository] is, so the many
 * [MapPreferencesRepository] fakes are untouched.
 */
interface WaypointNamesPreferenceRepository {
    /** `null` when nothing is stored (the caller uses the default, on). */
    suspend fun getWaypointNamesShown(): Result<Boolean?>

    suspend fun setWaypointNamesShown(shown: Boolean): Result<Unit>
}
