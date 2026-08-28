package com.forager.app.domain

/**
 * Settings' "Night Mode" checkbox — the app-wide light/dark [androidx.compose.material3.ColorScheme]
 * [com.forager.app.ui.theme.ForagerTheme] renders, as a direct, persistent preference. Same
 * DataStore-for-flat-settings reasoning as [MapPreferencesRepository] and
 * [DistanceUnitPreferenceRepository]'s own doc comments (a single scalar, not rows to query).
 *
 * Deliberately not derived from [androidx.compose.foundation.isSystemInDarkTheme] — same choice
 * this project already made for [MapPreferencesRepository.getNightModeMaps]/[setNightModeMaps]:
 * an explicit, persistent choice the app remembers, not automatic detection tied to the device's
 * system setting. Independent of [MapPreferencesRepository]'s own night-mode preference, which
 * controls only the map's basemap styling (see that interface's doc comment) — this one is the
 * checkbox the map one sits beneath in Settings, and covers the rest of the app's UI.
 *
 * Defaults to `false` (light) until the user has ever explicitly checked it — the same
 * always-explicit default [MapPreferencesRepository.getNightModeMaps] uses.
 */
interface AppThemePreferenceRepository {
    suspend fun getDarkTheme(): Result<Boolean>
    suspend fun setDarkTheme(dark: Boolean): Result<Unit>
}
