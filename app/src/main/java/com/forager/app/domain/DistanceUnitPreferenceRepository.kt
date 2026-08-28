package com.forager.app.domain

import com.forager.app.domain.model.DistanceUnit

/**
 * The user's chosen display unit for distances — same reasoning as [MapPreferencesRepository]'s
 * own doc comment for why this is DataStore rather than Room (a single scalar preference, not rows
 * to query) and rather than plain Compose state: it used to be `remember`'d directly in
 * `AvailabilityScreen`, which reset to the default on any configuration change (a system theme
 * switch among them, not just process death) — a real device report, since a config change is far
 * more frequent than a process kill. This is remembered user intent, the same category
 * [MapPreferencesRepository.getLastPickedRegion] already puts in DataStore for exactly that reason.
 */
interface DistanceUnitPreferenceRepository {

    /** [DistanceUnit.MILES] until the user has ever explicitly chosen otherwise — see [DistanceUnit]'s own doc comment on why that default changed from kilometers. */
    suspend fun getDistanceUnit(): Result<DistanceUnit>

    suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit>
}
