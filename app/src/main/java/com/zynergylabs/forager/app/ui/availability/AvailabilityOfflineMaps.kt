package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.MapPreferencesRepository
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The offline maps of [AvailabilityViewModel] (UI split Phase 2.4, PR 4, dispatch 2026-10-19-33, RECORD
 * -1453), moved out of the ViewModel unchanged: the picker's fields, opening the picker, the region list,
 * the picker's restored preferences, the direct region delete, and the launches of the two downloads. It
 * holds no state of its own. Every write to the screen's state goes through [updateState], and its
 * coroutines launch in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept.
 *
 * **Whether a download starts is not decided here.** [AvailabilityViewModel.onDownloadAgain] and
 * [AvailabilityViewModel.onDownloadOfflineMaps] keep every check (the row, the coordinates, the tile
 * budget) and the opening `Downloading(0, 0)` write, at the same positions, and call [beginDownloadAgain]
 * or [beginDownload] where their launch used to be (RECORD -1450's reading, applied by the planner to
 * this series). The pending region delete is a separate class.
 */
internal class AvailabilityOfflineMaps(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val offlineMapRepository: OfflineMapRepository,
    private val mapPreferencesRepository: MapPreferencesRepository,
    private val locationProvider: LocationProvider,
    private val getOfflineRegionReferenceCount: suspend (Long) -> Int,
    private val errorLog: ErrorLog,
) {

    fun onOfflineMapLatChanged(text: String) {
        updateState { it.copy(offlineMapLatText = text) }
    }

    fun onOfflineMapLngChanged(text: String) {
        updateState { it.copy(offlineMapLngText = text) }
    }

    fun onOfflineMapRadiusChanged(radiusKm: Int) {
        // Touched: from here on the radius is the user's and the per-unit default never moves it —
        // see AvailabilityUiState.offlineMapRadiusTouched.
        // Clamped to the offline radius's own ceiling (OfflineMapRepository.MAX_RADIUS_KM, sized to the
        // tile budget), not the search radius's Region.clampRadiusKm it used to share.
        updateState { it.copy(offlineMapRadiusKm = OfflineMapRepository.clampRadiusKm(radiusKm), offlineMapRadiusTouched = true) }
    }

    fun onOfflineMapNameChanged(text: String) {
        updateState { it.copy(offlineMapNameText = text) }
    }

    /**
     * Called every time the "Offline Maps" submenu is opened. Two things happen:
     *
     * 1. Always tries the device's current location as the picker's opening default, overriding
     *    whatever centre was showing before (including a centre [loadOfflineMapPreferences]
     *    restored from a prior pick). The project owner's own call, after using the
     *    last-picked-centre default the design doc originally specified: opening the picker away
     *    from home is more common than opening it away from wherever was last downloaded, so "near
     *    me" should win on every open, not just the first. A denial or unavailable fix leaves
     *    whatever centre was already showing in place rather than clearing a good default just
     *    because this particular fetch failed.
     *
     * 2. Re-reads [OfflineMapRepository.listRegions] rather than trusting whatever
     *    [loadOfflineRegions] loaded once at ViewModel construction. Hardware testing found the
     *    list could come up empty right after a cold start with many regions already on disk
     *    (survived the restart). The cause, from the device logs, was not the native store still
     *    starting: the start-up read threw `MapLibreConfigurationException` because MapLibre was
     *    not yet initialised (fixed by initialising it in `ForagerApplication.onCreate`, dispatch
     *    2026-09-28-106). Re-reading on open is good practice regardless: this screen should show
     *    current state whenever it's opened.
     *
     * Both calls are safe unconditionally: [LocationProvider.getCurrentLocation] only checks
     * whether permission is already granted, never triggering the OS permission dialog itself, and
     * a `listRegions` re-read has no side effect beyond what [loadOfflineRegions] already does on
     * every call.
     */
    fun onOfflineMapsOpened() {
        scope().launch {
            val result = locationProvider.getCurrentLocation()
            if (result is LocationResult.Success) {
                updateState { it.copy(offlineMapPickerDefaultCenter = LatLng(result.lat, result.lng)) }
            }
        }
        loadOfflineRegions()
    }

    /**
     * Reads every region currently on disk — once at startup, and again every time
     * [onOfflineMapsOpened] fires. Same reasoning as [AvailabilityPlannedTrips.loadPlannedTrips] for the startup call:
     * downloaded regions have nothing to do with the region search, so this isn't gated behind one.
     *
     * A read failure (e.g. a corrupt metadata blob) is reported via
     * [AvailabilityUiState.offlineRegionsErrorMessage] — but per the error-presentation spec's
     * belief-changing rule, this is a region-*list-load* failure, not a download failure: the user
     * isn't in the middle of an action here, they just want to see what's already on disk, so this
     * renders as an absorbed, neutral read-failure the same way [conditionsErrorMessage]/
     * [plannedTripsErrorMessage] do, not with the error-red treatment [AvailabilityViewModel.onDownloadOfflineMaps]'s
     * failure gets. The prior list is kept on a failed refresh rather than cleared, so a transient
     * read error doesn't make regions that are still on disk disappear.
     */
    fun loadOfflineRegions(): Job {
        return scope().launch {
            offlineMapRepository.listRegions().fold(
                onSuccess = { downloaded ->
                    // Regions restored from a backup with no tiles here are listed after the downloaded ones (owner, "1 B").
                    // Dispatch 2026-09-28-658 (M2): a failed read of them is a partial list, and the
                    // screen says so, rather than showing the downloaded ones as if they were all.
                    var partialMessage: String? = null
                    val restored = offlineMapRepository.listNotDownloadedRegions().getOrElse { error ->
                        errorLog.w(TAG, "Couldn't read restored offline regions; showing the downloaded ones only, and saying so.", error)
                        partialMessage = RESTORED_REGIONS_UNREADABLE_MESSAGE
                        emptyList()
                    }
                    val regions = downloaded + restored
                    updateState { it.copy(offlineRegions = regions, offlineRegionsErrorMessage = partialMessage) }
                    // One query per region — see TrackRecordingViewModel.loadWaypoints' identical
                    // choice for why this scale doesn't need a batched read.
                    val counts = regions.associate { it.id to getOfflineRegionReferenceCount(it.id) }
                    updateState { it.copy(offlineRegionEntryReferenceCounts = counts) }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't read offline regions.", error)
                    updateState { it.copy(offlineRegionsErrorMessage = "Couldn't read offline regions.") }
                },
            )
        }
    }

    /**
     * Restores the picker's remembered radius and the staleness badge threshold from
     * [MapPreferencesRepository] — both remembered user intent rather than derived state. The
     * restored centre only matters as a fallback now: [onOfflineMapsOpened] overrides it with the
     * device's current location on every open when that succeeds — this is what the picker shows
     * before that ever runs, or if a later location fetch fails.
     *
     * A read failure here leaves the built-in fallbacks in place
     * ([AvailabilityUiState.offlineMapPickerDefaultCenter] stays `null`,
     * [AvailabilityUiState.offlineStaleThresholdDays] stays [com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS])
     * rather than blocking the picker on a preferences read that isn't essential to using it — this
     * is deliberately not surfaced to [AvailabilityUiState] at all, since there is no belief for the
     * user to hold about a preference they never see; the throwable is still logged, not silently
     * discarded, per CLAUDE.md.
     */
    fun loadOfflineMapPreferences() {
        scope().launch {
            mapPreferencesRepository.getLastPickedRegion().fold(
                onSuccess = { region ->
                    if (region != null) {
                        updateState {
                            it.copy(
                                offlineMapPickerDefaultCenter = LatLng(region.lat, region.lng),
                                // Clamped: a radius picked before the ceiling shrank to
                                // OfflineMapRepository.MAX_RADIUS_KM (up to 50 km) is restored at the
                                // ceiling, not above the slider's own range.
                                offlineMapRadiusKm = OfflineMapRepository.clampRadiusKm(region.radiusKm),
                                // A restored last-picked radius is the user's own — see
                                // AvailabilityUiState.offlineMapRadiusTouched.
                                offlineMapRadiusTouched = true,
                            )
                        }
                    }
                },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the last-picked offline region.", error) },
            )
            mapPreferencesRepository.getStaleThresholdDays().fold(
                onSuccess = { days -> updateState { it.copy(offlineStaleThresholdDays = days) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the offline staleness threshold.", error) },
            )
        }
    }

    /**
     * The launch of [AvailabilityViewModel.onDownloadAgain], moved here unchanged. The ViewModel decides
     * whether to download (the row, the tile budget) and writes the opening `Downloading(0, 0)` before calling this.
     */
    fun beginDownloadAgain(row: OfflineRegionSummary) {
        scope().launch {
            offlineMapRepository.download(row.name, row.region) { downloaded, total ->
                updateState { it.copy(offlineDownloadStatus = OfflineMapStatus.Downloading(downloaded, total)) }
            }.fold(
                onSuccess = { downloadedRegion ->
                    offlineMapRepository.replaceRegion(row.id, downloadedRegion.id).onFailure { error ->
                        errorLog.w(TAG, "Downloaded ${row.name} again, but couldn't move region ${row.id}'s references to ${downloadedRegion.id}.", error)
                    }
                    updateState { it.copy(offlineDownloadStatus = OfflineMapStatus.Succeeded) }
                    loadOfflineRegions()
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't download offline maps again.", error)
                    updateState { it.copy(offlineDownloadStatus = OfflineMapStatus.Failed("Couldn't download offline maps.")) }
                },
            )
        }
    }

    /**
     * The launch of [AvailabilityViewModel.onDownloadOfflineMaps], moved here unchanged. The ViewModel decides
     * whether to download (the coordinates, the tile budget) and writes the opening `Downloading(0, 0)` before calling this.
     */
    fun beginDownload(name: String, region: Region) {
        scope().launch {
            offlineMapRepository.download(name, region) { downloaded, total ->
                updateState { it.copy(offlineDownloadStatus = OfflineMapStatus.Downloading(downloaded, total)) }
            }.fold(
                onSuccess = {
                    updateState {
                        it.copy(
                            offlineDownloadStatus = OfflineMapStatus.Succeeded,
                            offlineMapLatText = "",
                            offlineMapLngText = "",
                            offlineMapNameText = "",
                        )
                    }
                    loadOfflineRegions()
                    scope().launch { mapPreferencesRepository.setLastPickedRegion(region) }
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't download offline maps.", error)
                    updateState {
                        it.copy(offlineDownloadStatus = OfflineMapStatus.Failed("Couldn't download offline maps."))
                    }
                },
            )
        }
    }

    /**
     * Per-region delete, replacing the old all-or-nothing delete. A failure here is reported
     * through the same neutral, non-belief-changing channel [loadOfflineRegions] uses
     * ([AvailabilityUiState.offlineRegionsErrorMessage]), not [AvailabilityUiState.offlineDownloadStatus]
     * — deletion isn't a download, and the region simply staying in the list on failure already
     * shows the delete didn't take effect, the same signal a stale list already carries.
     */
    fun onDeleteOfflineRegion(id: Long) {
        scope().launch {
            offlineMapRepository.deleteRegion(id).fold(
                onSuccess = { loadOfflineRegions() },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't delete that region.", error)
                    updateState {
                        it.copy(offlineRegionsErrorMessage = "Couldn't delete that region.")
                    }
                },
            )
        }
    }
}
