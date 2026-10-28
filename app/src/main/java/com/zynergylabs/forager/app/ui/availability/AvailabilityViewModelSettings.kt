package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem

/*
 * The Settings functions of the ViewModel, each a delegate to [AvailabilitySettingsPrefs], moved out of AvailabilityViewModel.kt unchanged as extension functions on [AvailabilityViewModel]
 * (dispatch 2026-10-23-03 Amendment 1; the owner: "Split it now, before merge (Recommended)", RECORD -1490's
 * rule that a file over 800 lines is split before a feature goes in). A pure move: each function's body and doc
 * comment are as they were, they are still ViewModel functions (RECORD -1450: every decision to start or stop a job
 * stays in a ViewModel function), and every caller reaches them by the same name. The members they read were made
 * `internal` for this, and nothing else changed.
 */

/** See [AvailabilitySettingsPrefs.onAutoSaveLocationToPhotosChanged]. */
fun AvailabilityViewModel.onAutoSaveLocationToPhotosChanged(enabled: Boolean) = settingsPrefs.onAutoSaveLocationToPhotosChanged(enabled)

/** See [AvailabilitySettingsPrefs.onBatterySaverChanged]. */
fun AvailabilityViewModel.onBatterySaverChanged(enabled: Boolean) = settingsPrefs.onBatterySaverChanged(enabled)

/** See [AvailabilitySettingsPrefs.onOffTrackReminderChanged]. */
fun AvailabilityViewModel.onOffTrackReminderChanged(enabled: Boolean) = settingsPrefs.onOffTrackReminderChanged(enabled)

/** See [AvailabilitySettingsPrefs.onSundownAlertsEnabledChanged]. */
fun AvailabilityViewModel.onSundownAlertsEnabledChanged(enabled: Boolean) = settingsPrefs.onSundownAlertsEnabledChanged(enabled)

/** See [AvailabilitySettingsPrefs.onDarknessMarginChanged]. */
fun AvailabilityViewModel.onDarknessMarginChanged(minutes: Int) = settingsPrefs.onDarknessMarginChanged(minutes)

/** See [AvailabilitySettingsPrefs.onNightModeMapsChanged]. */
fun AvailabilityViewModel.onNightModeMapsChanged(night: Boolean) = settingsPrefs.onNightModeMapsChanged(night)

/** See [AvailabilitySettingsPrefs.onMapFullscreenChanged]. */
fun AvailabilityViewModel.onMapFullscreenChanged(fullscreen: Boolean) = settingsPrefs.onMapFullscreenChanged(fullscreen)

/** See [AvailabilitySettingsPrefs.onThemeModeChanged]. */
fun AvailabilityViewModel.onThemeModeChanged(mode: AppThemeMode) = settingsPrefs.onThemeModeChanged(mode)

/** See [AvailabilitySettingsPrefs.onUnitSystemSelected]. */
fun AvailabilityViewModel.onUnitSystemSelected(system: UnitSystem) = settingsPrefs.onUnitSystemSelected(system)

/**
 * The Settings control's existing callback, kept by name so `AvailabilityScreen.kt`'s plumbing
 * (nine sites, in a file whose split is held at seams F and G) does not change shape: it hands
 * over the chosen system by its distance unit, a bijection ([UnitSystem.forDistanceUnit]). The
 * name is legacy and says less than the call does; renaming it through the screen is the
 * split's job, not this dispatch's — recorded in the return-estimate completion report.
 */
fun AvailabilityViewModel.onDistanceUnitSelected(unit: DistanceUnit) = onUnitSystemSelected(UnitSystem.forDistanceUnit(unit))

/** See [AvailabilitySettingsPrefs.onCrowdedIconsModeChanged]. */
fun AvailabilityViewModel.onCrowdedIconsModeChanged(mode: CrowdedIconsMode) = settingsPrefs.onCrowdedIconsModeChanged(mode)
