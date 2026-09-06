package com.forager.app.ui.track

import com.forager.app.domain.model.ReturnToStartInfo
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
import com.forager.app.domain.model.Waypoint

/**
 * A track currently being recorded. [ActiveTrack.trackId] is the id
 * [com.forager.app.service.TrackRecordingService] was told to record into — this ViewModel and the
 * service agree on it via [TrackRecordingViewModel.startRecording]'s created [com.forager.app.domain.model.Track],
 * not by the service inventing its own id.
 */
data class ActiveTrack(
    val trackId: String,
    val startedAtEpochMillis: Long,
    val mode: TrackRecordingMode,
)

data class TrackRecordingUiState(
    val activeTrack: ActiveTrack? = null,
    val startRecordingErrorMessage: String? = null,
    /**
     * The active track's points as of the last poll — see [TrackRecordingViewModel]'s doc comment
     * for why this is polled rather than reactive. Empty whenever [activeTrack] is null.
     */
    val breadcrumbPoints: List<TrackPoint> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
    val waypointsErrorMessage: String? = null,
    /** How many Cartography entries currently keep a reference to each waypoint (by id) — Journal Stage 2b's 4b deletion warning. Loaded alongside [waypoints]; a waypoint missing from this map has never been counted, treated as zero the same as an explicit zero. */
    val waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /**
     * Whether the walker has said they're now heading back, distinct from [isRecording] — outbound
     * travel is never "off track" (you're the one making the track), so
     * [com.forager.app.domain.DetectOffTrackUseCase] only runs once this is true. See
     * [TrackRecordingViewModel.startReturn]'s doc comment for the full reasoning.
     */
    val isReturning: Boolean = false,
    /** Set by [TrackRecordingViewModel.returnToStart] while [isReturning] — see that method's doc comment. */
    val isOffTrack: Boolean = false,
    /**
     * Bearing/distance/elevation back to the track's start, refreshed on every live fix while
     * recording — see [TrackRecordingViewModel]'s own doc comment for why this is now pushed from
     * a continuous stream rather than pulled on demand. `null` whenever [isRecording] is false or
     * no breadcrumb exists yet to compute a start point from.
     */
    val returnToStart: ReturnToStartInfo? = null,
    /**
     * The active track's origin waypoint — navigation HUD stage one's target — once
     * [TrackRecordingViewModel] has created it from the first accuracy-gated fix after
     * [TrackRecordingViewModel.startRecording]. `null` before that fix arrives, for the whole
     * recording if none ever passes the gate (under canopy, say — a valid state the HUD handles by
     * saying so, never by substituting the first breadcrumb), and whenever nothing is recording.
     * In-memory only, like [activeTrack]: the persisted pointer is
     * [com.forager.app.domain.model.Track.originWaypointId].
     */
    val originWaypoint: Waypoint? = null,
    /**
     * Every recorded track, newest-started first — the Settings "Recorded Tracks" export surface's
     * only data source. Loaded on init and refreshed whenever that panel is opened (see
     * [TrackRecordingViewModel.loadTracks]), not reactively: [TrackRepository] is plain suspend
     * calls, same reasoning as [breadcrumbPoints]'s own doc comment.
     */
    val tracks: List<Track> = emptyList(),
    /**
     * Alert-delivery dispatch, Item 3: the one-time "your phone is silenced" warning for the
     * recording that just started, or `null` when the device would deliver an alert normally. Set
     * by [TrackRecordingViewModel.startRecording] from [com.forager.app.domain.alertAudibilityWarning]
     * and shown once as a Snackbar over the map. Carries an [TripStartWarning.id] that increments
     * per recording so the *same* text on a later trip re-shows — the same "event, not condition"
     * shape [startRecordingErrorMessage]'s Toast uses, except that field only clears on the next
     * success and would not re-fire for an identical message.
     *
     * The off-track alert itself no longer passes through this state: it used to be an
     * `offTrackAlertId` counter here that a `LaunchedEffect` in `MainActivity` observed, and that
     * composed path is why nothing fired with the screen off — see
     * [com.forager.app.domain.AlertDelivery], which [TrackRecordingViewModel.returnToStart] now
     * calls directly.
     */
    val tripStartWarning: TripStartWarning? = null,
) {
    val isRecording: Boolean get() = activeTrack != null
}

/** See [TrackRecordingUiState.tripStartWarning]. */
data class TripStartWarning(val id: Int, val message: String)
