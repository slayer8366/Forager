package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PendingDeleteSlot
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The pending offline-region delete of [AvailabilityViewModel] (UI split Phase 2.4, PR 5, dispatch
 * 2026-10-19-33, RECORD -1453): the one region whose delete waits on the Undo snackbar, moved out of the
 * ViewModel unchanged, in the shape 2.7 PR 1 gave the recording engine's pending deletes
 * (`TrackRecordingPendingDeletes`). It holds the [PendingDeleteSlot] and nothing else. Every value it reads
 * from the screen's state comes through [uiState], every write goes through [updateState], and its
 * coroutines launch in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept. A
 * committed delete's success path drops the region from the map's records ([dropFromMapRecords]) and
 * reads the region list again ([reloadOfflineRegions]), in that order, as before. It starts and stops none
 * of the ViewModel's tracked jobs.
 */
internal class AvailabilityOfflineRegionDeletes(
    private val uiState: () -> AvailabilityUiState,
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val pendingDeleteCommitScope: CoroutineScope,
    private val offlineMapRepository: OfflineMapRepository,
    private val errorLog: ErrorLog,
    private val dropFromMapRecords: (Long) -> Unit,
    private val reloadOfflineRegions: () -> Unit,
) {

    /** The one offline region whose delete is pending (journal redesign J4) — see [requestDeleteOfflineRegion]. */
    private val offlineRegionDeletes = PendingDeleteSlot<Long, OfflineRegionSummary> { it.id }

    /**
     * A swipe on an offline region's Records row (journal redesign J4): the region becomes pending —
     * hidden from [AvailabilityUiState.visibleOfflineRegions] at once, its tiles still on disk — and
     * the Undo snackbar shows. The real delete ([OfflineMapRepository.deleteRegion], MapLibre's tile
     * delete and then the row, unchanged) runs from [commitDeleteOfflineRegion] when the snackbar
     * ends without Undo, from here when a second region is swiped while this one is pending (the
     * first is committed then), or from [AvailabilityViewModel.onCleared]. That deferral is the only thing that makes Undo
     * possible for a region at all: deleted tiles can only come back by a re-download, under a new
     * MapLibre id (J0 B1).
     *
     * A region still downloading is never a row here: [OfflineMapRepository.listRegions] only lists
     * complete regions (it deletes incomplete ones), and [AvailabilityUiState.offlineRegions] is only
     * ever that list, so there is no downloading region to swipe.
     *
     * Reference count: the loaded one, a missing entry counting as zero as
     * [AvailabilityUiState.offlineRegionEntryReferenceCounts] documents. An id not in the list is
     * logged and pends nothing.
     */
    fun requestDeleteOfflineRegion(id: Long) {
        val state = uiState()
        val region = state.offlineRegions.firstOrNull { it.id == id }
        if (region == null) {
            errorLog.w(TAG, "A delete was asked for offline region $id, which is not loaded; nothing pended.", IllegalStateException("no region $id"))
            return
        }
        val displaced = offlineRegionDeletes.pend(region, state.offlineRegionEntryReferenceCounts[id] ?: 0)
        updateState {
            it.copy(
                pendingOfflineRegionDelete = offlineRegionDeletes.pending,
                // The displaced region leaves its Undo window in this same update: it is never in neither.
                committedOfflineRegionDeleteIds = if (displaced == null) it.committedOfflineRegionDeleteIds else it.committedOfflineRegionDeleteIds + displaced.id,
            )
        }
        displaced?.let(::commitOfflineRegionDelete)
    }

    /** The snackbar's Undo: the pending region shows again. No tile was deleted, so nothing is restored. */
    fun undoDeleteOfflineRegion(id: Long) {
        if (offlineRegionDeletes.undo(id) == null) {
            errorLog.w(TAG, "Undo for offline region $id came after its delete was committed; nothing to undo.", IllegalStateException("region $id not pending"))
        }
        updateState { it.copy(pendingOfflineRegionDelete = offlineRegionDeletes.pending) }
    }

    /** The snackbar ended without Undo (timed out, or a newer snackbar replaced it): the pending region's tile and row delete runs, once. */
    fun commitDeleteOfflineRegion(id: Long) {
        val region = offlineRegionDeletes.commit(id)
        // Marked committed (commitOfflineRegionDelete's first update) before the pending marker is cleared, so the
        // Maps tab never sees a moment in which the region is neither (dispatch 2026-09-28-312, item 9).
        region?.let(::commitOfflineRegionDelete)
        updateState { it.copy(pendingOfflineRegionDelete = offlineRegionDeletes.pending) }
    }

    /**
     * The real delete of a region whose pending time is over. It leaves [AvailabilityUiState.offlineRegions]
     * at once, so it does not flash back between the snackbar closing and MapLibre finishing; a failed
     * delete puts it back where it was and reports the failure the way [AvailabilityOfflineMaps.onDeleteOfflineRegion] does.
     */
    private fun commitOfflineRegionDelete(region: OfflineRegionSummary) {
        val index = uiState().offlineRegions.indexOfFirst { it.id == region.id }
        updateState { state ->
            state.copy(
                offlineRegions = state.offlineRegions.filterNot { it.id == region.id },
                committedOfflineRegionDeleteIds = state.committedOfflineRegionDeleteIds + region.id,
            )
        }
        scope().launch {
            offlineMapRepository.deleteRegion(region.id).fold(
                onSuccess = {
                    dropFromMapRecords(region.id)
                    reloadOfflineRegions()
                },
                onFailure = { error ->
                    errorLog.w(TAG, "Couldn't delete that region.", error)
                    updateState { state ->
                        val restored = if (state.offlineRegions.any { it.id == region.id }) {
                            state.offlineRegions
                        } else {
                            state.offlineRegions.toMutableList().apply { add(index.coerceIn(0, size), region) }
                        }
                        state.copy(
                            offlineRegions = restored,
                            committedOfflineRegionDeleteIds = state.committedOfflineRegionDeleteIds - region.id,
                            offlineRegionsErrorMessage = "Couldn't delete that region.",
                        )
                    }
                },
            )
        }
    }

    /**
     * The ViewModel's `onCleared`: a region still pending is committed here, since no snackbar is left to
     * end. viewModelScope is already cancelled, so the delete runs on [pendingDeleteCommitScope].
     */
    fun commitOnCleared() {
        offlineRegionDeletes.takeAny()?.let { region ->
            pendingDeleteCommitScope.launch {
                offlineMapRepository.deleteRegion(region.id).onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete offline region ${region.id} pending when the screen closed; its tiles are still on disk.", error)
                }
            }
        }
    }
}
