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
     * Every recorded track, newest-started first — the Settings "Recorded Tracks" export surface's
     * only data source. Loaded on init and refreshed whenever that panel is opened (see
     * [TrackRecordingViewModel.loadTracks]), not reactively: [TrackRepository] is plain suspend
     * calls, same reasoning as [breadcrumbPoints]'s own doc comment.
     */
    val tracks: List<Track> = emptyList(),
    /**
     * Field-test dispatch item 4: incremented each time [TrackRecordingViewModel.returnToStart]
     * decides a pocketed-phone off-track alert should fire — a one-shot event counter, the same
     * "increment, and let the observer diff against the last value it saw" shape
     * [startRecordingErrorMessage]'s own `LaunchedEffect` consumer uses for a Toast, except this
     * needs to fire again on a *repeated* condition ("still off track"), which a nulled-out single
     * message field can't represent. `MainActivity` observes this to post a notification and
     * vibrate — see [TrackRecordingViewModel.returnToStart]'s own doc comment for the debounce this
     * counter reflects. Starts at 0, which is never itself treated as a real alert — the observer's
     * `LaunchedEffect` fires once on first composition with whatever value is already here.
     */
    val offTrackAlertId: Int = 0,
) {
    val isRecording: Boolean get() = activeTrack != null
}
