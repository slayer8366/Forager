package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.routedata.needsFetch
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetching the trail routes of regions already downloaded (dispatch 2026-10-23-03; the owner's answer, "Automatic,
 * button as backup (Recommended)"), and since dispatch 2026-11-04-09 their drop-off map copies, at the same moments
 * (the owner: "Automatic (Recommended)"). Holds no screen state of its own: [AvailabilityUiState.offlineRouteFetchingIds]
 * and [AvailabilityUiState.offlineRouteFailedIds] are written through [updateState]; the one thing it keeps is the lock
 * that runs the copy's passes one at a time. **Whether to fetch is decided by the ViewModel**
 * ([AvailabilityViewModel.onOfflineMapsOpened], [AvailabilityViewModel.onOfflineMapsSignalReturned],
 * [AvailabilityViewModel.onTryAgainRoutes], and a download's completion through [noteDownloaded]); this runs it.
 */
internal class AvailabilityOfflineRoutes(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val currentState: () -> AvailabilityUiState,
    private val scope: () -> CoroutineScope,
    private val offlineMapRepository: OfflineMapRepository,
    /** Reloads the region list, so each row reads its routes from storage after a fetch. */
    private val reloadRegions: () -> Job,
    private val errorLog: ErrorLog,
) {

    /** The listed regions whose routes the automatic fetch takes: never fetched, damaged, or missing for a reason a retry could help. */
    fun regionsNeedingRoutes(): List<Long> =
        currentState().visibleOfflineRegions.filter { it.isDownloaded && it.routeData.needsFetch() }.map { it.id }

    /**
     * Fetches [ids]' routes once each, in turn, skipping any already being fetched, then reloads the list. A fetch
     * that ends with routes still wanted, or fails outright, marks its region failed, which is what shows "Try again".
     * One pass: nothing here repeats it.
     */
    fun fetch(ids: Collection<Long>): Job? {
        val fresh = ids.filterNot { it in currentState().offlineRouteFetchingIds }
        if (fresh.isEmpty()) return null
        updateState { it.copy(offlineRouteFetchingIds = it.offlineRouteFetchingIds + fresh) }
        return scope().launch {
            for (id in fresh) {
                val failed = offlineMapRepository.fetchRoutes(id).fold(
                    onSuccess = { routes -> routes.needsFetch() },
                    onFailure = { error ->
                        errorLog.w(TAG, "Couldn't fetch the trail routes of region $id.", error)
                        true
                    },
                )
                updateState {
                    it.copy(
                        offlineRouteFetchingIds = it.offlineRouteFetchingIds - id,
                        offlineRouteFailedIds = if (failed) it.offlineRouteFailedIds + id else it.offlineRouteFailedIds - id,
                    )
                }
            }
            reloadRegions()
        }
    }

    /**
     * The listed regions the drop-off map pass takes: downloaded on this phone and not yet whole. Its own selection, not
     * [regionsNeedingRoutes]': that one takes only regions whose routes are still wanted, and both the S22's areas have
     * theirs (the Phase 1 report's wrong premise), so riding it would fetch nothing there.
     */
    fun regionsNeedingDropOffMaps(): List<Long> =
        currentState().visibleOfflineRegions.filter { it.isDownloaded && !it.dropOffMap.whole }.map { it.id }

    /**
     * Runs one drop-off map pass for each of [ids], in turn, then reloads the list so each row's size counts its copy (A5).
     * One pass at a time across every trigger, so the copy's operating limits (`data/dropoffmap/DropOffMapLimits`) hold
     * for the app, not per trigger: a second trigger's passes wait for the first's, and an area the first made whole then
     * asks for nothing. Nothing here repeats a pass. Each pass's outcome is logged where it ran; one that could not run
     * at all is logged here.
     */
    fun fetchDropOffMaps(ids: Collection<Long>): Job? {
        if (ids.isEmpty()) return null
        return scope().launch {
            dropOffPasses.withLock {
                for (id in ids) {
                    offlineMapRepository.fetchDropOffMap(id).onFailure { error ->
                        errorLog.w(TAG, "Couldn't run the drop-off map pass for region $id.", error)
                    }
                }
            }
            reloadRegions()
        }
    }

    /** Offline maps opened with no signal: no drop-off map pass, said once for the opening (CLAUDE.md: never silent). */
    fun noteNoSignalForDropOffMaps() {
        errorLog.w(TAG, "Offline maps opened with no signal: no drop-off map pass this time.", DropOffMapPassSkipped("no signal"))
    }

    private val dropOffPasses = Mutex()

    /**
     * A download's own route fetch is an attempt too: when it left routes still wanted, its row offers "Try again". The
     * download's completion also runs the new area's drop-off map pass (B1 P2). The ViewModel hands every completed
     * download here (`AvailabilityViewModel.offlineMaps`' `onDownloaded`), so that is where it decides.
     */
    fun noteDownloaded(region: OfflineRegionSummary) {
        if (region.routeData.needsFetch()) updateState { it.copy(offlineRouteFailedIds = it.offlineRouteFailedIds + region.id) }
        // B1 P2: a download's completion runs its area's drop-off map pass. A new download can reuse tiles stored before
        // the fence mark (dispatch 2026-11-04-08's report, section 2), so a new area can want them too.
        if (region.isDownloaded) fetchDropOffMaps(listOf(region.id))
    }
}

/** What [ErrorLog] is handed when no drop-off map pass runs: it takes a throwable, and this names the reason. */
private class DropOffMapPassSkipped(reason: String) : Exception(reason)
