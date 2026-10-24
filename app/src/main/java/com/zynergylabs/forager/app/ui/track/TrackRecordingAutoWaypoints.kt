package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.autoWaypointName
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.Companion.TAG
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The recording's automatic waypoints and the start point they define, for [TrackRecordingViewModel] (nav step 2, PR 2,
 * dispatch 2026-10-21-01; the pattern of UI split Phase 2.7, RECORD -1439), moved out of the ViewModel unchanged: saving
 * the origin and end waypoints, the start point the screen and the watch measure to, and the Return button's distance.
 * The ViewModel decides when: the fix collector still decides when an origin save starts (and [originCreationInFlight]
 * stays the ViewModel's flag, read and written here through the getter and setter), and `stopRecording` still decides
 * whether there is an end waypoint to save. Every value read from the screen comes through [uiState], every write goes
 * through [updateState], and the saves launch in [scope], the ViewModel's own `viewModelScope`, read each time.
 */
internal class TrackRecordingAutoWaypoints(
    private val uiState: () -> TrackRecordingUiState,
    private val updateState: ((TrackRecordingUiState) -> TrackRecordingUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val trackRepository: TrackRepository,
    private val createWaypoint: CreateWaypointUseCase,
    private val computeReturnToStart: ComputeReturnToStartUseCase,
    private val returnWatch: ReturnWatch,
    private val errorLog: ErrorLog,
    private val currentTime: CurrentTimeProvider,
    private val zone: ZoneId,
    private val is24HourClock: () -> Boolean,
    private val setOriginCreationInFlight: (Boolean) -> Unit,
    /** The ViewModel's `loadWaypoints`, after a save. */
    private val loadWaypoints: () -> Unit,
) {

    /** The start point as the screen shows it: the origin waypoint once it exists, the first breadcrumb before that. */
    fun startPoint(): TrackPoint? =
        uiState().originWaypoint?.asStartPoint() ?: uiState().breadcrumbPoints.firstOrNull()

    /**
     * Gives the watch the start point this ViewModel shows, so the alert and the screen measure to
     * one place. Called whenever that point can have changed. The watch keeps the last one after
     * this ViewModel is gone.
     */
    fun handStartPointToWatch() {
        val active = uiState().activeTrack ?: return
        val start = startPoint() ?: return
        returnWatch.setStartPoint(active.trackId, start)
    }

    /**
     * [namedAtEpochMillis] is the time in the marker's default name. For a marker made from the
     * first good fix it is now, which is when that fix arrived. For one made later from a track's
     * first recorded point it is that point's own time, so the name says when the walk started
     * and not when the app was reopened.
     */
    fun createOriginWaypoint(active: ActiveTrack, fix: TrackPoint, namedAtEpochMillis: Long) {
        setOriginCreationInFlight(true)
        scope().launch {
            createWaypoint(
                lat = fix.lat,
                lng = fix.lng,
                altitude = fix.altitude,
                name = autoWaypointName(WaypointDesignation.ORIGIN, namedAtEpochMillis, zone, is24HourClock()),
                trackId = active.trackId,
                designation = WaypointDesignation.ORIGIN,
            )
                .onSuccess { waypoint ->
                    // The pointer is what survives the process; the state field is this session's
                    // target. A failed pointer write is logged and the session still navigates —
                    // a partial result reported as such, not hidden and not fatal.
                    trackRepository.setOriginWaypoint(active.trackId, waypoint.id)
                        .onFailure { error -> errorLog.w(TAG, "Couldn't point track '${active.trackId}' at its origin waypoint.", error) }
                    if (uiState().activeTrack?.trackId == active.trackId) {
                        updateState { it.copy(originWaypoint = waypoint) }
                        handStartPointToWatch()
                    }
                    loadWaypoints()
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't save the track's origin waypoint.", error)
                    // Cleared so the next gated fix tries again rather than leaving the track
                    // origin-less for a transient write failure.
                    setOriginCreationInFlight(false)
                }
        }
    }

    /**
     * Saves the track's end waypoint from [endFix], the save [TrackRecordingViewModel.stopRecording] starts once it has
     * decided there is one to make (its doc comment gives the rule). Moved out of that function unchanged.
     */
    fun saveEndWaypoint(endingTrack: ActiveTrack, endFix: TrackPoint) {
        scope().launch {
            createWaypoint(
                lat = endFix.lat,
                lng = endFix.lng,
                altitude = endFix.altitude,
                name = autoWaypointName(WaypointDesignation.END, currentTime.nowEpochMillis(), zone, is24HourClock()),
                trackId = endingTrack.trackId,
                designation = WaypointDesignation.END,
            )
                .onSuccess { loadWaypoints() }
                .onFailure { error -> errorLog.w(TAG, "Couldn't save the track's end waypoint.", error) }
        }
    }

    /**
     * [ReturnToStartInfo] from [current] back to the active track's start, or `null` if either is
     * unavailable. Called on every fix [beginLocationTracking] collects, and also directly by
     * tests — its result is written to [TrackRecordingUiState.returnToStart] either way, so a
     * direct call and a collected fix behave identically. It is what the Return button describes.
     *
     * **It decides nothing.** Until dispatch 2026-09-28-400, Amendment 2, this method was also the
     * off-track decision: while returning, each call's distance joined a window here, the check
     * re-ran, and the alert was delivered from here. That is all [ReturnWatch]'s now, fed by the
     * recording service, so that it survives this ViewModel being cleared. The measurement stays
     * because the screen needs a distance whether or not anything is deciding on it (the planner's
     * ruling on question 1: the distance is not a copy of the watch's).
     */
    fun returnToStart(current: TrackPoint): ReturnToStartInfo? {
        // Navigation HUD stage one (owner decision): the origin *waypoint* is the target once it
        // exists, so the return arm and the HUD point at one place; before it exists — or for a
        // track that never gets one — the first breadcrumb stands in exactly as it always did.
        val start = startPoint() ?: return null
        val info = computeReturnToStart(current, start)
        updateState { it.copy(returnToStart = info) }
        return info
    }

    private fun Waypoint.asStartPoint() = TrackPoint(
        lat = lat,
        lng = lng,
        altitude = altitude,
        accuracyMeters = null,
        timestampEpochMillis = createdAtEpochMillis,
    )
}
