package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.track.TrackRecordingReturnLeg
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel

/**
 * The one place the Maps tab's [AvailabilityViewModel] is built, moved unchanged out of `MainActivity`'s ViewModel factory
 * (nav step 2, dispatch 2026-10-21-01), the pattern of [createMushroomLogViewModel]. [trackRecording] is a provider, not the
 * ViewModel itself, so the recording ViewModel is still reached at the same point in the construction as before.
 */
internal fun createAvailabilityViewModel(
    container: AppContainer,
    androidErrorLog: ErrorLog,
    trackRecording: () -> TrackRecordingViewModel,
    /** The maps' "no signal" state (NoSignalTrailMap.kt), read when Offline maps opens (dispatch 2026-10-23-03). */
    noSignal: () -> Boolean,
): AvailabilityViewModel = AvailabilityViewModel(
        container.locationProvider,
        container.locationTracker,
        container.getAvailabilityUseCase,
        container.getRecentSearchesUseCase,
        container.getSightingsUseCase,
        container.searchTaxaUseCase,
        container.getConditionsUseCase,
        container.getTripWindowsUseCase,
        container.getPlannedTripsUseCase,
        container.savePlannedTripUseCase,
        container.deletePlannedTripUseCase,
        container.getSeasonalPatternUseCase,
        container.offlineMapRepository,
        androidErrorLog,
        container.mapPreferencesRepository,
        container.unitSystemPreferenceRepository,
        container.appThemePreferenceRepository,
        container.getTodaysForecastUseCase,
        entryReferenceCounts = container.entryReferenceCountSource,
        getAutoSaveLocationToPhotos = container.photoLocationPreferenceRepository::getAutoSaveLocationToPhotos,
        setAutoSaveLocationToPhotos = container.photoLocationPreferenceRepository::setAutoSaveLocationToPhotos,
        getSundownAlertsEnabled = container.sundownPreferencesRepository::getAlertsEnabled,
        setSundownAlertsEnabled = container.sundownPreferencesRepository::setAlertsEnabled,
        getDarknessMarginMinutes = container.sundownPreferencesRepository::getDarknessMarginMinutes,
        setDarknessMarginMinutes = container.sundownPreferencesRepository::setDarknessMarginMinutes,
        onDarknessMarginStored = container.sundownWatch::onMarginChanged,
        getOffTrackReminderEnabled = container.offTrackReminderPreferences::getEnabled,
        setOffTrackReminderEnabled = container.offTrackReminderPreferences::setEnabled,
        getBatterySaverEnabled = container.batterySaverPreferences::getEnabled,
        setBatterySaverEnabled = container.batterySaverPreferences::setEnabled,
        getMapRecords = { container.getMapRecordsUseCase() },
        mapLayerPreferencesRepository = container.mapLayerPreferencesRepository,
        forecastCellStore = container.forecastCellStore,
        // Dispatch 2026-09-28-502: the waypoint navigation kept across the process, and the return it pauses
        // and picks back up. The leg holds the recording ViewModel itself, not this Activity, so a recreated
        // Activity leaves nothing behind in it.
        waypointNavigationRepository = container.waypointNavigationRepository,
        returnLeg = TrackRecordingReturnLeg(trackRecording()),
        lastKnownLocation = container.lastKnownLocation,
        basemapPreferenceRepository = container.basemapPreferenceRepository,
        mapIconClusterPlacementRepository = container.mapIconClusterPlacementRepository,
        crowdedIconsRepository = container.crowdedIconsRepository,
        noSignal = noSignal,
        // Dispatch 2026-10-24-07: the service's stored-end report, so a finished track is on the map right after Stop.
        trackEnds = container.trackEnds.latest,
        // Step 6 PR 1 (RECORD -1655, -1657): the waypoint line's own lookup by id and its clock.
        readWaypoint = container.waypointRepository::getById,
        currentTime = container.currentTimeProvider,
        // Step 6 PR 3 (dispatch 2026-10-26-11): the way to a waypoint along trails, on the shared trail engine (PR 2).
        findWayToWaypoint = { from, waypoint -> container.findWayToWaypoint(from, waypoint) },
    )
