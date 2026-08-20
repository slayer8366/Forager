package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * Small user-intent settings for the offline-region picker, owned behind an interface the same way
 * [OfflineMapRepository] wraps `OfflineManager` — the real implementation,
 * `com.forager.app.data.repository.DataStoreMapPreferencesRepository`, is backed by Jetpack
 * DataStore rather than Room: this is a couple of scalar preferences, not rows to query, so a
 * key-value store fits better than a table.
 *
 * [getLastPickedRegion]/[setLastPickedRegion] back the design doc's "Cold-start default": the
 * picker restores the last centre/radius someone actually chose, rather than reopening a fixed
 * point ~2000km from anywhere they've downloaded. This is remembered user intent, not derived
 * state, which is why it belongs in DataStore rather than `SavedStateHandle` (which wouldn't
 * survive the app being killed and reopened later).
 */
interface MapPreferencesRepository {

    /** The centre/radius last picked in the offline-region download picker, or `null` if none has been picked yet. */
    suspend fun getLastPickedRegion(): Result<Region?>

    suspend fun setLastPickedRegion(region: Region): Result<Unit>

    /** The staleness badge threshold, in days — see [isOfflineRegionStale]. */
    suspend fun getStaleThresholdDays(): Result<Int>

    suspend fun setStaleThresholdDays(days: Int): Result<Unit>
}
