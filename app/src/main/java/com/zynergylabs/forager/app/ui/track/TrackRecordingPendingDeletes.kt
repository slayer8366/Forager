package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.PendingDeleteSlot
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The pending deletes of [TrackRecordingViewModel] (UI split Phase 2.7, PR 1, dispatch 2026-10-19-30,
 * RECORD -1439): the one waypoint and the one track whose delete waits on the Undo snackbar, moved out of
 * the ViewModel unchanged. It holds the two [PendingDeleteSlot]s and nothing else. Every value it reads
 * from the screen's state comes through [uiState], every write goes through [updateState], and its
 * coroutines launch in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept.
 * It starts and stops none of the recording's jobs.
 */
internal class TrackRecordingPendingDeletes(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val pendingDeleteCommitScope: CoroutineScope,
    private val deleteWaypoint: DeleteWaypointUseCase,
    private val deleteTrack: DeleteTrackUseCase,
    private val errorLog: ErrorLog,
    private val reloadWaypoints: () -> Unit,
    private val reloadTracks: () -> Unit,
) {

    /** The one waypoint whose delete is pending (journal redesign J4) — see [requestRemoveWaypoint]. */
    private val waypointDeletes = PendingDeleteSlot<String, Waypoint> { it.id }

    /** The one track whose delete is pending (Part 2 follow-ups F1 item 5) — see [requestRemoveTrack]. */
    private val trackDeletes = PendingDeleteSlot<String, Track> { it.id }

    /**
     * A swipe on a waypoint's Records row (journal redesign J4): the waypoint becomes pending —
     * hidden from [TrackRecordingUiState.visibleWaypoints] at once, still saved — and the Undo
     * snackbar shows. Its real delete ([deleteWaypoint], unchanged) runs from [commitRemoveWaypoint]
     * when the snackbar ends without Undo, from here when a second waypoint is swiped while this one
     * is still pending (the first is committed then), or from [TrackRecordingViewModel.onCleared].
     *
     * The reference count carried for the snackbar's warning is the one already loaded, read the
     * way the old confirm dialog read it: a waypoint missing from
     * [TrackRecordingUiState.waypointEntryReferenceCounts] counts as zero (that field's own doc
     * comment). An id not in the list is logged and pends nothing: there is no row for it.
     */
    fun requestRemoveWaypoint(id: String) {
        val state = uiState()
        val waypoint = state.waypoints.firstOrNull { it.id == id }
        if (waypoint == null) {
            errorLog.w(TAG, "A delete was asked for waypoint '$id', which is not loaded; nothing pended.", IllegalStateException("no waypoint '$id'"))
            return
        }
        val displaced = waypointDeletes.pend(waypoint, state.waypointEntryReferenceCounts[id] ?: 0)
        updateState { it.copy(pendingWaypointDelete = waypointDeletes.pending) }
        displaced?.let(::commitWaypointDelete)
    }

    /** The snackbar's Undo: the pending waypoint shows again. Nothing was deleted, so nothing is restored. */
    fun undoRemoveWaypoint(id: String) {
        if (waypointDeletes.undo(id) == null) {
            errorLog.w(TAG, "Undo for waypoint '$id' came after its delete was committed; nothing to undo.", IllegalStateException("waypoint '$id' not pending"))
        }
        updateState { it.copy(pendingWaypointDelete = waypointDeletes.pending) }
    }

    /** The snackbar ended without Undo (timed out, or a newer snackbar replaced it): the pending waypoint's delete runs, once. */
    fun commitRemoveWaypoint(id: String) {
        val waypoint = waypointDeletes.commit(id)
        updateState { it.copy(pendingWaypointDelete = waypointDeletes.pending) }
        waypoint?.let(::commitWaypointDelete)
    }

    /**
     * The real delete of a waypoint whose pending time is over. It leaves [TrackRecordingUiState.waypoints]
     * at once rather than when the reload lands, so it does not flash back onto the screen between
     * the snackbar closing and the delete finishing. A failed delete puts it back where it was and
     * reports the failure the way [TrackRecordingViewModel.removeWaypoint] does.
     */
    private fun commitWaypointDelete(waypoint: Waypoint) {
        val index = uiState().waypoints.indexOfFirst { it.id == waypoint.id }
        updateState { state -> state.copy(waypoints = state.waypoints.filterNot { it.id == waypoint.id }) }
        scope().launch {
            deleteWaypoint(waypoint.id)
                .onSuccess { reloadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete waypoint.", error)
                    updateState { state ->
                        val restored = if (state.waypoints.any { it.id == waypoint.id }) {
                            state.waypoints
                        } else {
                            state.waypoints.toMutableList().apply { add(index.coerceIn(0, size), waypoint) }
                        }
                        state.copy(waypoints = restored, waypointsErrorMessage = "Couldn't delete waypoint.")
                    }
                }
        }
    }

    /**
     * A swipe on a finished track's Records row, or the Delete on its details (Part 2 follow-ups F1 item 5,
     * owner "Option A", built like [requestRemoveWaypoint]): the track becomes pending, hidden from
     * [TrackRecordingUiState.visibleTracks] at once and still saved, and the Undo snackbar shows. The real
     * delete ([DeleteTrackUseCase], which detaches the track's waypoints first) runs from
     * [commitRemoveTrack] when the snackbar ends without Undo, from here when a second track is asked for
     * while this one is pending (the first is committed then), or from [TrackRecordingViewModel.onCleared].
     *
     * **Never a track that is recording.** A track whose end time is null, or the one this ViewModel is
     * recording into, is refused and logged, so a caller that offers Delete on it by mistake still cannot
     * delete a recording. An id not in the list is logged and pends nothing.
     */
    fun requestRemoveTrack(id: String) {
        val state = uiState()
        val track = state.tracks.firstOrNull { it.id == id }
        if (track == null) {
            errorLog.w(TAG, "A delete was asked for track '$id', which is not loaded; nothing pended.", IllegalStateException("no track '$id'"))
            return
        }
        if (track.endedAtEpochMillis == null || state.activeTrack?.trackId == id) {
            errorLog.w(TAG, "A delete was asked for track '$id', which is still recording; refused.", IllegalStateException("track '$id' is recording"))
            return
        }
        val displaced = trackDeletes.pend(track, state.trackEntryReferenceCounts[id] ?: 0)
        updateState { it.copy(pendingTrackDelete = trackDeletes.pending) }
        displaced?.let(::commitTrackDelete)
    }

    /** The snackbar's Undo: the pending track shows again. Nothing was deleted, so nothing is restored. */
    fun undoRemoveTrack(id: String) {
        if (trackDeletes.undo(id) == null) {
            errorLog.w(TAG, "Undo for track '$id' came after its delete was committed; nothing to undo.", IllegalStateException("track '$id' not pending"))
        }
        updateState { it.copy(pendingTrackDelete = trackDeletes.pending) }
    }

    /** The snackbar ended without Undo (timed out, or a newer one replaced it): the pending track's delete runs, once. */
    fun commitRemoveTrack(id: String) {
        val track = trackDeletes.commit(id)
        updateState { it.copy(pendingTrackDelete = trackDeletes.pending) }
        track?.let(::commitTrackDelete)
    }

    /**
     * The real delete of a track whose pending time is over. It leaves [TrackRecordingUiState.tracks] at once
     * rather than when the reload lands, so it does not flash back between the snackbar closing and the delete
     * finishing. A failed delete puts it back where it was and reports the failure
     * ([TrackRecordingUiState.tracksErrorMessage]), never as if it had worked.
     */
    private fun commitTrackDelete(track: Track) {
        val index = uiState().tracks.indexOfFirst { it.id == track.id }
        updateState { state -> state.copy(tracks = state.tracks.filterNot { it.id == track.id }) }
        scope().launch {
            deleteTrack(track.id)
                .onSuccess { reloadTracks() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete track.", error)
                    updateState { state ->
                        val restored = if (state.tracks.any { it.id == track.id }) {
                            state.tracks
                        } else {
                            state.tracks.toMutableList().apply { add(index.coerceIn(0, size), track) }
                        }
                        state.copy(tracks = restored, tracksErrorMessage = "Couldn't delete track.")
                    }
                }
        }
    }

    /**
     * The ViewModel's `onCleared`: a delete still pending is committed here, since no snackbar is left to
     * end. viewModelScope is already cancelled, so the delete runs on [pendingDeleteCommitScope].
     */
    fun commitOnCleared() {
        trackDeletes.takeAny()?.let { track ->
            pendingDeleteCommitScope.launch {
                deleteTrack(track.id).onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete track '${track.id}' pending when the screen closed; it is still saved.", error)
                }
            }
        }
        waypointDeletes.takeAny()?.let { waypoint ->
            pendingDeleteCommitScope.launch {
                deleteWaypoint(waypoint.id).onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete waypoint '${waypoint.id}' pending when the screen closed; it is still saved.", error)
                }
            }
        }
    }
}
