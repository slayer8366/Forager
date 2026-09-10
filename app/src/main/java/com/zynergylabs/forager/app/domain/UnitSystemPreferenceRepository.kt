package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.UnitSystem

/**
 * The user's chosen system of units — the successor to the distance-only preference this replaces
 * (return-estimate dispatch, Item 4; see [UnitSystem]'s own doc comment for why a system rather
 * than a widened distance unit). Same reasoning as [MapPreferencesRepository]'s own doc comment for
 * why this is DataStore rather than Room (a single scalar preference, not rows to query) and rather
 * than plain Compose state (it must survive a configuration change — a real device report, not just
 * process death).
 */
interface UnitSystemPreferenceRepository {

    /** [UnitSystem.IMPERIAL] until the user has ever explicitly chosen otherwise — the app's users are US foragers. */
    suspend fun getUnitSystem(): Result<UnitSystem>

    suspend fun setUnitSystem(system: UnitSystem): Result<Unit>
}
