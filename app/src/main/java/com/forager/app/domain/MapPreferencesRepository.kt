package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * Small user-intent map settings, owned behind an interface the same way [OfflineMapRepository]
 * wraps `OfflineManager` — the real implementation,
 * `com.forager.app.data.repository.DataStoreMapPreferencesRepository`, is backed by Jetpack
 * DataStore rather than Room: these are scalar preferences, not rows to query, so a key-value
 * store fits better than a table.
 *
 * [getLastPickedRegion]/[setLastPickedRegion] back the design doc's "Cold-start default": the
 * offline-region picker restores the last centre/radius someone actually chose, rather than
 * reopening a fixed point ~2000km from anywhere they've downloaded. This is remembered user
 * intent, not derived state, which is why it belongs in DataStore rather than `SavedStateHandle`
 * (which wouldn't survive the app being killed and reopened later).
 *
 * [getNightModeMaps]/[setNightModeMaps] are the same kind of remembered intent for a different
 * setting — see [getNightModeMaps]'s own doc comment.
 */
interface MapPreferencesRepository {

    /** The centre/radius last picked in the offline-region download picker, or `null` if none has been picked yet. */
    suspend fun getLastPickedRegion(): Result<Region?>

    suspend fun setLastPickedRegion(region: Region): Result<Unit>

    /** The staleness badge threshold, in days — see [isOfflineRegionStale]. */
    suspend fun getStaleThresholdDays(): Result<Int>

    suspend fun setStaleThresholdDays(days: Int): Result<Unit>

    /**
     * Whether the map renders in night mode. A direct, persistent preference — the Settings
     * "Night Maps" checkbox's own value — not derived from time of day or a session-local hold.
     * Defaults to `false` (day). Replaces the map's earlier civil-twilight-automatic/long-press-hold
     * control (`MapNightMode`), per the project owner's own request to move this to a plain
     * Settings checkbox instead.
     */
    suspend fun getNightModeMaps(): Result<Boolean>

    suspend fun setNightModeMaps(night: Boolean): Result<Unit>

    /**
     * Whether the Maps tab was left in fullscreen — restored on the next launch. **The first
     * per-case "yes" under CLAUDE.md's "UX defaults" restart rule, and the precedent the next one
     * will point at, so the reasoning is recorded here, not just the outcome.** The owner's
     * decision: the map is fully usable in fullscreen, the exit control sits on the icon bar in
     * plain view, and a user who chose fullscreen deliberately should not have it taken away by
     * closing the app. *Only* fullscreen persists — the cluster's minimised flag, drag position
     * and side stay session-only, because a cold launch showing a bare map with nothing but a
     * peeking handle is the least oriented the app can be, and that is not what persisting
     * fullscreen is for. This store, not a new one: a flat boolean with no relations belongs in
     * DataStore under the Room/DataStore split, and this repository already holds the map's other
     * flat preferences under the prescribed pattern.
     */
    suspend fun getMapFullscreen(): Result<Boolean>

    suspend fun setMapFullscreen(fullscreen: Boolean): Result<Unit>
}
