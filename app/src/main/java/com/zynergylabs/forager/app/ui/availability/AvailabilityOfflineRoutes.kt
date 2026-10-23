package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.routedata.needsFetch
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Fetching the trail routes of regions already downloaded (dispatch 2026-10-23-03; the owner's answer, "Automatic,
 * button as backup (Recommended)"). Holds no state of its own: [AvailabilityUiState.offlineRouteFetchingIds] and
 * [AvailabilityUiState.offlineRouteFailedIds] are written through [updateState]. **Whether to fetch is decided by the
 * ViewModel** ([AvailabilityViewModel.onOfflineMapsOpened], [AvailabilityViewModel.onTryAgainRoutes]); this runs it.
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
        val fresh = ids.toList()
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

    /** A download's own route fetch is an attempt too: when it left routes still wanted, its row offers "Try again". */
    fun noteDownloaded(region: OfflineRegionSummary) {
        if (region.routeData.needsFetch()) updateState { it.copy(offlineRouteFailedIds = it.offlineRouteFailedIds + region.id) }
    }
}
