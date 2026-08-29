package com.forager.app.domain.model

/**
 * Settings' theme choice, persisted via [com.forager.app.domain.AppThemePreferenceRepository].
 * [LIGHT]/[DARK] are explicit, direct choices independent of the device's own theme — the same
 * "app remembers a direct choice, doesn't infer one" precedent
 * [com.forager.app.domain.MapPreferencesRepository]'s own night-mode-maps preference already set.
 * [SYSTEM_DEFAULT] is the one case that *does* follow the device: resolving it needs
 * `androidx.compose.foundation.isSystemInDarkTheme()`, a `@Composable`-only signal domain/ViewModel
 * code can't read, so that resolution happens in `MainActivity` — see its own `onCreate`, not here
 * or in `AvailabilityViewModel`.
 */
enum class AppThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM_DEFAULT("System Default"),
}
