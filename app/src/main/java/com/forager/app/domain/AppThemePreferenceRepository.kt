package com.forager.app.domain

import com.forager.app.domain.model.AppThemeMode

/**
 * Settings' theme choice — the app-wide light/dark [androidx.compose.material3.ColorScheme]
 * [com.forager.app.ui.theme.ForagerTheme] renders, as a direct, persistent preference. Same
 * DataStore-for-flat-settings reasoning as [MapPreferencesRepository] and
 * [DistanceUnitPreferenceRepository]'s own doc comments (a single scalar, not rows to query).
 *
 * A three-way [AppThemeMode], not a boolean — [AppThemeMode.SYSTEM_DEFAULT] added per the project
 * owner's own request, alongside the two explicit choices ([AppThemeMode.LIGHT]/
 * [AppThemeMode.DARK]) this preference started as. See [AppThemeMode]'s own doc comment for why
 * resolving [AppThemeMode.SYSTEM_DEFAULT] against the device's theme happens in `MainActivity`,
 * not here.
 *
 * Independent of [MapPreferencesRepository]'s own night-mode preference, which controls only the
 * map's basemap styling (see that interface's doc comment) — this one is the choice
 * [MapPreferencesRepository.getNightModeMaps]'s own Settings row sits beneath, and covers the rest
 * of the app's UI. That independence is deliberate and stays true of the tri-state form too: the
 * "have it only apply to the theme, not the maps" instruction that came with this change.
 *
 * Defaults to [AppThemeMode.LIGHT] until the user has ever explicitly chosen otherwise — the same
 * always-explicit-until-chosen default this preference already had as a boolean (`false`/light),
 * kept rather than defaulting fresh installs to [AppThemeMode.SYSTEM_DEFAULT]: adding a new option
 * to what's choosable here shouldn't also silently change what everyone who has never touched this
 * setting already sees.
 */
interface AppThemePreferenceRepository {
    suspend fun getThemeMode(): Result<AppThemeMode>
    suspend fun setThemeMode(mode: AppThemeMode): Result<Unit>
}
