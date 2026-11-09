package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s settings callbacks and the
 * settings-reset notice, grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 10 separate parameters of the screen; each field keeps that parameter's default, or stays required where
 * the parameter was required, and its doc comment, and the screen unpacks the bundle into locals with the old names at
 * the top of its body, so the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** Settings' toggles and choices and the settings-reset notice, for [AvailabilityScreen]. Fields without a default were required parameters and stay required; the rest keep their defaults. */
data class AvailabilityScreenSettings(
    /** Settings' "Night Maps" checkbox — see [AvailabilityUiState.nightModeMaps]'s own doc comment. */
    val onNightModeMapsChanged: (Boolean) -> Unit,
    /** "Automatically Save Location to Photos" — see [AvailabilityUiState.autoSaveLocationToPhotos]; set from the camera's Location chip (Settings until dispatch 2026-09-28-707, then also the camera's gear panel until dispatch 2026-10-13-03). Defaulted, like [onDistanceUnitSelected], so a screen test that does not exercise this setting needs no argument for it. */
    val onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit = {},
    /** Settings' "Sundown alerts" (dispatch 2026-09-28-592) — see [AvailabilityUiState.sundownAlertsEnabled]. */
    val onSundownAlertsEnabledChanged: (Boolean) -> Unit = {},
    /** Settings' "Dark under trees" (dispatch 2026-09-28-592) — see [AvailabilityUiState.darknessMarginMinutes]. */
    val onDarknessMarginChanged: (Int) -> Unit = {},
    /** Settings' "Off-track reminder" (dispatch 2026-09-28-626) — see [AvailabilityUiState.offTrackReminderEnabled]. */
    val onOffTrackReminderChanged: (Boolean) -> Unit = {},
    /** The quick menu's "Battery saver" (dispatch 2026-09-28-767) — see [AvailabilityUiState.batterySaverEnabled]. */
    val onBatterySaverChanged: (Boolean) -> Unit = {},
    /**
     * RECORD -660/-661: a settings file was found corrupt and reset, and the one-time message is owed.
     * Shown as a snackbar that stays until dismissed, with a "Settings" action that opens Settings;
     * [onSettingsResetNoticeShown] is called as it is shown, so it is shown once. Defaulted to nothing owed.
     */
    val settingsResetNoticePending: Boolean = false,
    val onSettingsResetNoticeShown: () -> Unit = {},
    /** Settings' Light/Dark/System Default theme choice — see [AvailabilityUiState.themeMode]'s own doc comment. */
    val onThemeModeChanged: (AppThemeMode) -> Unit,
    /** The Settings panel's km/mi toggle — see [AvailabilityUiState.distanceUnit]'s own doc comment for why this is persisted rather than session-local state. */
    val onDistanceUnitSelected: (DistanceUnit) -> Unit = {},
)
