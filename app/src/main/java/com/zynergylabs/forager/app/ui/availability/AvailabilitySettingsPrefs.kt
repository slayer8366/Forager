package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.AppThemePreferenceRepository
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.defaultOfflineMapRadiusKm
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The settings [AvailabilityViewModel] stores (UI split Phase 2.4, PR 2, dispatch 2026-10-19-33, RECORD
 * -1453), moved out of the ViewModel unchanged: night maps, the photo-location preference, the Sundown
 * pair, the off-track reminder, Battery saver, map fullscreen, the theme and the unit system. It holds
 * no state of its own. Every write to the screen's state goes through [updateState], and its coroutines
 * launch in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept. It starts
 * and stops none of the ViewModel's tracked jobs; the ViewModel's `init` calls its eight loaders at the
 * same positions as before. The borrowed get/set pairs are the ViewModel's constructor parameters,
 * documented there.
 */
internal class AvailabilitySettingsPrefs(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val mapPreferencesRepository: MapPreferencesRepository,
    private val unitSystemPreferenceRepository: UnitSystemPreferenceRepository,
    private val appThemePreferenceRepository: AppThemePreferenceRepository,
    private val getAutoSaveLocationToPhotos: suspend () -> Result<Boolean>,
    private val setAutoSaveLocationToPhotos: suspend (Boolean) -> Result<Unit>,
    private val getSundownAlertsEnabled: suspend () -> Result<Boolean>,
    private val setSundownAlertsEnabled: suspend (Boolean) -> Result<Unit>,
    private val getDarknessMarginMinutes: suspend () -> Result<Int>,
    private val setDarknessMarginMinutes: suspend (Int) -> Result<Unit>,
    private val onDarknessMarginStored: (Int) -> Unit,
    private val getOffTrackReminderEnabled: suspend () -> Result<Boolean>,
    private val setOffTrackReminderEnabled: suspend (Boolean) -> Result<Unit>,
    private val getBatterySaverEnabled: suspend () -> Result<Boolean>,
    private val setBatterySaverEnabled: suspend (Boolean) -> Result<Unit>,
    private val errorLog: ErrorLog,
) {

    /**
     * Restores Settings' "Night Maps" checkbox — same read-failure treatment as
     * [AvailabilityOfflineMaps.loadOfflineMapPreferences], plus the cold-launch gate (colour build C1): either way the read
     * ends, [AvailabilityUiState.nightModeMapsLoaded] becomes `true` in the same update, so the map
     * loads its first style knowing the answer. A failed read falls back to day, logged.
     */
    fun loadNightModePreferences() {
        scope().launch {
            mapPreferencesRepository.getNightModeMaps().fold(
                onSuccess = { night -> updateState { it.copy(nightModeMaps = night, nightModeMapsLoaded = true) } },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't read the night-maps preference.", error)
                    updateState { it.copy(nightModeMaps = false, nightModeMapsLoaded = true) }
                },
            )
        }
    }

    /** Restores the "Automatically Save Location to Photos" preference — same read-failure treatment as [loadNightModePreferences]: log, and leave the on-by-default state alone. */
    fun loadAutoSaveLocationToPhotos() {
        scope().launch {
            getAutoSaveLocationToPhotos().fold(
                onSuccess = { enabled -> updateState { it.copy(autoSaveLocationToPhotos = enabled) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the photo-location preference.", error) },
            )
        }
    }

    /**
     * Settings' "Automatically Save Location to Photos" checkbox. Reflects the new value in state
     * immediately and persists in the background, the same shape and the same
     * not-essential-to-using-it failure treatment as [onNightModeMapsChanged] below.
     *
     * **The two ViewModels read this preference independently, on purpose.** This one holds it to
     * render the checkbox; `MushroomLogViewModel` re-reads it from the same repository at the
     * moment it is about to capture a position. Nothing is pushed from here to there, so there is
     * no window in which the find path acts on a value this screen has already changed.
     */
    fun onAutoSaveLocationToPhotosChanged(enabled: Boolean) {
        updateState { it.copy(autoSaveLocationToPhotos = enabled) }
        scope().launch {
            setAutoSaveLocationToPhotos(enabled).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the photo-location preference.", error) },
            )
        }
    }

    /** Restores the Sundown section's two settings; a failed read is logged and leaves the default (on, one hour) showing. */
    fun loadSundownPreferences() {
        scope().launch {
            getSundownAlertsEnabled().fold(
                onSuccess = { enabled -> updateState { it.copy(sundownAlertsEnabled = enabled) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read whether the sundown alerts are on; the default, on, is shown.", error) },
            )
            getDarknessMarginMinutes().fold(
                onSuccess = { minutes -> updateState { it.copy(darknessMarginMinutes = minutes) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the darkness margin; the default is shown.", error) },
            )
        }
    }

    /** Restores "Off-track reminder"; a failed read is logged and leaves the default, on, showing. */
    fun loadOffTrackReminder() {
        scope().launch {
            getOffTrackReminderEnabled().fold(
                onSuccess = { enabled -> updateState { it.copy(offTrackReminderEnabled = enabled) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read whether the off-track reminder is on; the default, on, is shown.", error) },
            )
        }
    }

    /** Restores "Battery saver"; a failed read is logged and leaves the default, off, showing. */
    fun loadBatterySaver() {
        scope().launch {
            getBatterySaverEnabled().fold(
                onSuccess = { enabled -> updateState { it.copy(batterySaverEnabled = enabled) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read whether Battery saver is on; the default, off, is shown.", error) },
            )
        }
    }

    /**
     * The quick menu's "Battery saver" switch (dispatch 2026-09-28-767). Shown at once and stored in the
     * background; storing it is what moves every fix request to the new interval, mid-recording included.
     * A failed store is logged and the switch goes back to what is stored, so it never shows a rate the
     * phone is not being asked at.
     */
    fun onBatterySaverChanged(enabled: Boolean) {
        updateState { it.copy(batterySaverEnabled = enabled) }
        scope().launch {
            setBatterySaverEnabled(enabled).onFailure { error ->
                errorLog.w(TAG, "Couldn't store whether Battery saver is on; the switch shows what is stored.", error)
                updateState { it.copy(batterySaverEnabled = !enabled) }
            }
        }
    }

    /**
     * Settings' "Off-track reminder" checkbox (dispatch 2026-09-28-626). Shown at once and stored in
     * the background; the off-track rule reads the stored value when it next decides to alert.
     */
    fun onOffTrackReminderChanged(enabled: Boolean) {
        updateState { it.copy(offTrackReminderEnabled = enabled) }
        scope().launch {
            setOffTrackReminderEnabled(enabled).onFailure { error -> errorLog.w(TAG, "Couldn't store whether the off-track reminder is on.", error) }
        }
    }

    /**
     * Settings' "Sundown alerts" checkbox. Gates the notifications only: the sundown line stays
     * (the owner's step path, RECORD -592). Shown at once and stored in the background, as
     * [onAutoSaveLocationToPhotosChanged] is; the watch reads the stored value at its next tick.
     */
    fun onSundownAlertsEnabledChanged(enabled: Boolean) {
        updateState { it.copy(sundownAlertsEnabled = enabled) }
        scope().launch {
            setSundownAlertsEnabled(enabled).onFailure { error -> errorLog.w(TAG, "Couldn't store whether the sundown alerts are on.", error) }
        }
    }

    /**
     * Settings' "Dark under trees" choice. Shown at once; once stored, the sundown line is told
     * ([onDarknessMarginStored]) so its start-back time moves at once (the owner's step path).
     */
    fun onDarknessMarginChanged(minutes: Int) {
        updateState { it.copy(darknessMarginMinutes = minutes) }
        scope().launch {
            setDarknessMarginMinutes(minutes).fold(
                onSuccess = { onDarknessMarginStored(minutes) },
                onFailure = { error -> errorLog.w(TAG, "Couldn't store the darkness margin; the line and the alerts keep the one stored.", error) },
            )
        }
    }

    /**
     * Settings' "Night Maps" checkbox. Updates [AvailabilityUiState.nightModeMaps] immediately —
     * the map should not wait on a DataStore round-trip to reflect what was just checked — then
     * persists in the background; a persist failure is logged but not surfaced, the same
     * not-essential-to-using-it treatment [loadNightModePreferences] gives a read failure.
     */
    fun onNightModeMapsChanged(night: Boolean) {
        updateState { it.copy(nightModeMaps = night) }
        scope().launch {
            mapPreferencesRepository.setNightModeMaps(night).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the night-maps preference.", error) },
            )
        }
    }

    /**
     * Restores whether the Maps tab was left in fullscreen — same read-failure treatment as
     * [loadNightModePreferences]: a failed read logs and leaves [AvailabilityUiState.persistedMapFullscreen]
     * `null`, so the screen simply starts as it always did. See
     * [com.zynergylabs.forager.app.domain.MapPreferencesRepository.getMapFullscreen] for the precedent this sets.
     */
    fun loadMapFullscreenPreference() {
        scope().launch {
            mapPreferencesRepository.getMapFullscreen().fold(
                onSuccess = { fullscreen -> updateState { it.copy(persistedMapFullscreen = fullscreen) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the map-fullscreen preference.", error) },
            )
        }
    }

    /**
     * The screen entered or left fullscreen by a user action (the icon bar's toggle, system back,
     * or leaving the Maps tab). Persists in the background; a persist failure is logged but not
     * surfaced, the same treatment [onNightModeMapsChanged] gives. Deliberately does *not* update
     * [AvailabilityUiState.persistedMapFullscreen]: that field is the launch-time value the screen
     * applies once, and the screen's own live flag is the source of truth after that.
     */
    fun onMapFullscreenChanged(fullscreen: Boolean) {
        scope().launch {
            mapPreferencesRepository.setMapFullscreen(fullscreen).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the map-fullscreen preference.", error) },
            )
        }
    }

    /** Restores Settings' theme choice (Light/Dark/System Default) — same read-failure treatment as [AvailabilityOfflineMaps.loadOfflineMapPreferences]. */
    fun loadThemeModePreference() {
        scope().launch {
            appThemePreferenceRepository.getThemeMode().fold(
                onSuccess = { mode -> updateState { it.copy(themeMode = mode) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the app theme preference.", error) },
            )
        }
    }

    /**
     * Settings' theme choice — the app-wide theme [MainActivity][com.zynergylabs.forager.app.MainActivity]
     * resolves and renders via [com.zynergylabs.forager.app.ui.theme.ForagerTheme]. Same immediate-update-then-
     * persist shape as [onNightModeMapsChanged].
     */
    fun onThemeModeChanged(mode: AppThemeMode) {
        updateState { it.copy(themeMode = mode) }
        scope().launch {
            appThemePreferenceRepository.setThemeMode(mode).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the app theme preference.", error) },
            )
        }
    }

    /**
     * The one place the display unit changes, from the persisted read at startup and from Settings'
     * toggle alike (radius-default dispatch, Item 2). Alongside the unit it applies the offline-map
     * radius's per-unit default — [defaultOfflineMapRadiusKm]: 8 km, which displays as "5 mi", when
     * the unit is miles; 10 km when it is kilometres — but only while the radius is still untouched
     * ([AvailabilityUiState.offlineMapRadiusTouched]). Selected by the unit, never converted from
     * one canonical value: a single stored default cannot be round in both units (15 km read as
     * "9 mi"; 5 mi is 8.05 km). Re-applies on every unit change while untouched, so a user who has
     * never set a radius and switches to kilometres gets 10 km, not a converted 8; once they have
     * touched it, or a last-picked region restored it, it is theirs and never moves.
     */
    private fun applyUnitSystem(system: UnitSystem) {
        updateState {
            it.copy(
                unitSystem = system,
                offlineMapRadiusKm = if (it.offlineMapRadiusTouched) it.offlineMapRadiusKm else defaultOfflineMapRadiusKm(system.distanceUnit),
            )
        }
    }

    /**
     * Restores the persisted unit system at startup — see [AvailabilityUiState.unitSystem]'s own
     * doc comment for the bug this fixes. A read failure keeps [AvailabilityUiState.unitSystem] at
     * its default rather than surfacing an error the user never asked for, the same reasoning
     * [AvailabilityOfflineMaps.loadOfflineMapPreferences] applies to its own reads; the throwable is still logged.
     */
    fun loadUnitSystemPreference() {
        scope().launch {
            unitSystemPreferenceRepository.getUnitSystem().fold(
                onSuccess = { system -> applyUnitSystem(system) },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the saved unit system.", error) },
            )
        }
    }

    /** The Settings panel's Metric/Imperial choice — updates the UI immediately and persists in the background. */
    fun onUnitSystemSelected(system: UnitSystem) {
        applyUnitSystem(system)
        scope().launch {
            unitSystemPreferenceRepository.setUnitSystem(system).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't save the selected unit system.", error) },
            )
        }
    }
}
