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
 * Defaults to [AppThemeMode.SYSTEM_DEFAULT] for a brand-new install with nothing persisted at all —
 * the project owner's own explicit choice, made once [AppThemeMode.SYSTEM_DEFAULT] existed as an
 * option to default to. An existing install that already has a persisted choice (including the
 * pre-tri-state boolean this preference started as) keeps exactly what it already had; see
 * [com.forager.app.data.repository.DataStoreAppThemePreferenceRepository]'s own doc comment for the
 * two fallback layers that distinguish those cases.
 */
interface AppThemePreferenceRepository {
    suspend fun getThemeMode(): Result<AppThemeMode>
    suspend fun setThemeMode(mode: AppThemeMode): Result<Unit>
}
