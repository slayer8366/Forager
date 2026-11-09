package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The waypoint and track lists of [TrackRecordingViewModel] (UI split Phase 2.7, PR 2, dispatch 2026-10-19-30,
 * RECORD -1439): loading both lists with their reference counts, adding and removing a waypoint, and the
 * process's one sweep of tracks left open, moved out of the ViewModel unchanged. It holds no state of its
 * own. Every value it reads from the screen's state comes through [uiState], every write goes through
 * [updateState], and its coroutines launch in [scope], the ViewModel's own `viewModelScope`, read each time
 * rather than kept. It starts and stops none of the recording's jobs.
 */
internal class TrackRecordingLists(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val getWaypoints: GetWaypointsUseCase,
    private val getTracks: GetTracksUseCase,
    private val createWaypoint: CreateWaypointUseCase,
    private val deleteWaypoint: DeleteWaypointUseCase,
    private val getWaypointReferenceCount: suspend (String) -> Int,
    private val getTrackReferenceCount: suspend (String) -> Int,
    private val abandonedTrackSweepOnce: AbandonedTrackSweepOnce,
    private val errorLog: ErrorLog,
) {

    /**
     * Launches the process's one sweep of tracks left open by an earlier process, and reads the
     * track list again when it has ended any, so a track it finished reads as finished on this
     * screen without Records being reopened. `null` from [AbandonedTrackSweepOnce.runOnce] means
     * another ViewModel already ran it in this process; nothing to do. A failure to list the tracks
     * is logged, and the next ViewModel to be created tries again.
     */
    suspend fun sweepAbandonedTracks() {
        val result = abandonedTrackSweepOnce.runOnce() ?: return
        result
            .onSuccess { sweep -> if (sweep.ended.isNotEmpty() || sweep.endedWithNoStoredPoint.isNotEmpty()) loadTracks() }
            .onFailure { error -> errorLog.w(TAG, "Couldn't list the tracks to end the ones left open by an earlier process; none was changed.", error) }
    }

    fun loadWaypoints(): Job {
        return scope().launch {
            getWaypoints()
                .onSuccess { waypoints ->
                    updateState { it.copy(waypoints = waypoints, waypointsErrorMessage = null, waypointsLoaded = true) }
                    // A handful of rows at most (see this list's own empty-state copy) — one query
                    // per waypoint is simpler than a batched read this table has no precedent for,
                    // and cheap at this scale. See TrackRecordingUiState.waypointEntryReferenceCounts'
                    // own doc comment for what this feeds.
                    val counts = waypoints.associate { it.id to getWaypointReferenceCount(it.id) }
                    updateState { it.copy(waypointEntryReferenceCounts = counts) }
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't load waypoints.", error)
                    updateState { it.copy(waypointsErrorMessage = "Couldn't load waypoints.") }
                }
        }
    }

    /**
     * Refreshes [TrackRecordingUiState.tracks] — called on init and again whenever the Settings
     * "Recorded Tracks" export panel opens, the same "reload on open" shape
     * `AvailabilityViewModel.onOfflineMapsOpened` already uses for its own submenu. A failure here
     * just leaves the list stale rather than surfacing a dedicated error message: unlike starting a
     * recording or saving a waypoint, this isn't a user-triggered write whose outcome needs
     * reporting, only a read backing a read-only export list.
     */
    fun loadTracks(): Job {
        return scope().launch {
            getTracks()
                .onSuccess { tracks ->
                    updateState { it.copy(tracks = tracks.sortedByDescending(Track::startedAtEpochMillis), tracksErrorMessage = null) }
                    // One query per track, as the waypoints do (a handful of rows at most): what the Undo
                    // snackbar's "used in N journal entries" reads (Part 2 follow-ups F1 item 5).
                    val counts = tracks.associate { it.id to getTrackReferenceCount(it.id) }
                    updateState { it.copy(trackEntryReferenceCounts = counts) }
                }
                .onFailure { error -> errorLog.w(TAG, "Couldn't load tracks.", error) }
        }
    }

    /**
     * The map's '+' > Waypoint > name dialog (MainActivity's `onDropWaypoint`), the one place a user adds a
     * waypoint. Dispatch -616 (plan T10): a waypoint dropped while a recording runs is linked to it by
     * `trackId`, the column the origin and end waypoints already fill, so Records lists it under that walk
     * and the walk's GPX carries it. "Runs" means what the screen shows, [TrackRecordingUiState.activeTrack],
     * read when the name is confirmed. Two consequences, recorded rather than designed around: a waypoint
     * dropped in the moment after the app reopens and before it has taken up the service's running
     * recording ([TrackRecordingViewModel.takeUpRunningRecording]) is standalone, and so is one whose recording stopped while its
     * name dialog was open. With no recording it is standalone, as before.
     */
    fun addWaypoint(lat: Double, lng: Double, name: String, note: String = "") {
        val trackId = uiState().activeTrack?.trackId
        scope().launch {
            createWaypoint(lat, lng, altitude = null, name = name, note = note, trackId = trackId)
                .onSuccess { loadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't save waypoint.", error)
                    updateState { it.copy(waypointsErrorMessage = "Couldn't save waypoint.") }
                }
        }
    }

    fun removeWaypoint(id: String) {
        scope().launch {
            deleteWaypoint(id)
                .onSuccess { loadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete waypoint.", error)
                    updateState { it.copy(waypointsErrorMessage = "Couldn't delete waypoint.") }
                }
        }
    }
}
