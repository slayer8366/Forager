package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.ReturnWatchState
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.TAG

/**
 * The take-up memory of [TrackRecordingViewModel] (UI split Phase 2.7, PR 5, dispatch 2026-10-19-30,
 * RECORD -1439), moved out of the ViewModel unchanged. It holds the recording this screen has itself
 * stopped, and how a taken-up recording's start marker is being settled, and nothing else. Take-up itself
 * stays in the ViewModel: `takeUpRunningRecording`, its in-flight guard, and every start and stop of the
 * recording's jobs. This class launches nothing. Every value it reads from the screen comes through a
 * getter ([uiState], [originCreationInFlight]), every write goes through [updateState], and the
 * ViewModel's own functions are reached through [handStartPointToWatch] and [createOriginWaypoint].
 */
internal class TrackRecordingTakeUp(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val getTrackOriginWaypoint: GetTrackOriginWaypointUseCase,
    private val errorLog: ErrorLog,
    private val handStartPointToWatch: () -> Unit,
    private val originCreationInFlight: () -> Boolean,
    /** The ViewModel's `createOriginWaypoint(active, fix, namedAtEpochMillis)`. */
    private val createOriginWaypoint: (ActiveTrack, TrackPoint, Long) -> Unit,
) {

    /**
     * How the start marker is settled for a recording this ViewModel took up, or `null` for a
     * recording it started itself (whose marker comes from the first good fix, as always).
     * See [settleTakenUpOrigin].
     */
    private var takenUpOrigin: TakenUpOrigin? = null

    /**
     * The recording this screen has itself stopped, until the watch has moved on from it. See
     * [isOwnStoppedRecording].
     */
    private var stoppedTrackId: String? = null

    private enum class TakenUpOrigin {
        /** The track's own start marker has not been looked up yet, or the lookup failed and is tried again. */
        NOT_LOOKED_UP,

        /** The track has no start marker recorded: one is made at its first recorded point. */
        NONE_RECORDED,

        /** The track has one, and it is shown. */
        FOUND,
    }

    /**
     * Whether the watch is still begun for the recording this screen has just stopped.
     *
     * **The window this closes** (found in the planner's review of Part 3a): Stop clears this
     * screen's recording at once, but the service is stopped a moment later, when `MainActivity`'s
     * effect has sent `ACTION_STOP` and the service has handled it. Until then the watch is still
     * begun for the stopped track while this screen has no active track, which is the very state
     * the refusal in [TrackRecordingViewModel.startRecording] and [TrackRecordingViewModel.takeUpRunningRecording] act on. Without this, a second
     * quick tap on Record took the stopped recording back up, and the service then began
     * recording again into a track it had just ended.
     *
     * So a recording this screen stopped is neither refused against nor taken up. The memory is
     * dropped as soon as the watch is seen to have moved on (ended, or begun for another track),
     * so any other running recording is still refused against and still taken up.
     *
     * **What it rests on:** the stop reaching the service. If it never did, this screen would go
     * on leaving that recording alone until the watch changed. A new screen (after a swipe-away)
     * has no such memory and takes it up.
     */
    fun isOwnStoppedRecording(running: ReturnWatchState): Boolean {
        val stopped = stoppedTrackId ?: return false
        if (running.isBegun && running.trackId == stopped) return true
        stoppedTrackId = null
        return false
    }

    /**
     * The start marker of a recording that was taken up (Amendment 3, steps 2 and 5).
     *
     * First the marker the track already has, through [getTrackOriginWaypoint]. If it has none,
     * the owner's answer (path 2, "A"): **a start marker is placed at the track's first recorded
     * point.** Never at the fix this ViewModel receives after the reopen, which is where the
     * walker is now and not where they started; that is why [TrackRecordingViewModel.beginLocationTracking] makes no
     * marker for a taken-up recording. If the track has no recorded point yet, none is made until
     * one exists. Called when the recording is taken up and again on every poll, so a late first
     * point, a failed lookup or a failed save is picked up on the next one.
     *
     * "First recorded point" is the first of the track's points as this screen reads them, the
     * same point the Return control already falls back to.
     */
    suspend fun settleTakenUpOrigin(active: ActiveTrack, track: Track) {
        if (takenUpOrigin == TakenUpOrigin.NOT_LOOKED_UP) {
            getTrackOriginWaypoint(active.trackId)
                .onSuccess { waypoint ->
                    if (uiState().activeTrack?.trackId != active.trackId || takenUpOrigin != TakenUpOrigin.NOT_LOOKED_UP) return
                    if (waypoint != null) {
                        takenUpOrigin = TakenUpOrigin.FOUND
                        updateState { it.copy(originWaypoint = waypoint) }
                        handStartPointToWatch()
                    } else {
                        takenUpOrigin = TakenUpOrigin.NONE_RECORDED
                    }
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't read the start marker of track '${active.trackId}'; none is shown, and none is made until it can be read.", error)
                }
        }
        if (takenUpOrigin == TakenUpOrigin.NONE_RECORDED && uiState().originWaypoint == null && !originCreationInFlight()) {
            val first = track.points.firstOrNull() ?: return
            createOriginWaypoint(active, first, first.timestampEpochMillis)
        }
    }

    /**
     * Whether this screen's recording was taken up rather than started here: what the location
     * collector read as `takenUpOrigin != null`.
     */
    val isTakenUp: Boolean get() = takenUpOrigin != null

    /** A recording this screen started, or none: `takenUpOrigin = null`. */
    fun clear() {
        takenUpOrigin = null
    }

    /** A recording just taken up: `takenUpOrigin = TakenUpOrigin.NOT_LOOKED_UP`. */
    fun markNotLookedUp() {
        takenUpOrigin = TakenUpOrigin.NOT_LOOKED_UP
    }

    /** This screen has just stopped [trackId]'s recording: `stoppedTrackId = trackId`. */
    fun rememberStopped(trackId: String) {
        stoppedTrackId = trackId
    }
}
