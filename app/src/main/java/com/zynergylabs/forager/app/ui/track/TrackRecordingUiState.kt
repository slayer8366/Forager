package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.PathHome
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint

/**
 * A track currently being recorded. [ActiveTrack.trackId] is the id
 * [com.zynergylabs.forager.app.service.TrackRecordingService] was told to record into — this ViewModel and the
 * service agree on it via [TrackRecordingViewModel.startRecording]'s created [com.zynergylabs.forager.app.domain.model.Track],
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
     * [com.zynergylabs.forager.app.domain.DetectOffTrackUseCase] only runs once this is true. See
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
     * [com.zynergylabs.forager.app.domain.model.Track.originWaypointId].
     */
    val originWaypoint: Waypoint? = null,
    /**
     * Path-home join dispatch: the walk back to the origin along the recorded track, joined to
     * itself ([com.zynergylabs.forager.app.domain.pathHome]) — the first and only production reader of that
     * function. Computed by [TrackRecordingViewModel]'s 15 s breadcrumb poll (the caller the
     * return-estimate work always intended), from the last accuracy-gated fix to the polled
     * track, **only while [isReturning]** — the HUD is its one surface, so nothing is computed
     * for a walker who has not turned round; `null` otherwise, before the first gated fix, and
     * whenever the track has no usable points. Its `hopBand` is the hysteresis carried between
     * polls; a new return starts at [com.zynergylabs.forager.app.domain.HopBand.NONE]. The HUD shows
     * [com.zynergylabs.forager.app.domain.PathHome.totalMeters] as one number; the walking time built on the
     * same value ([com.zynergylabs.forager.app.domain.returnWalkingTime]) still has no caller, on purpose.
     */
    val pathHome: PathHome? = null,
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
     * by [TrackRecordingViewModel.startRecording] from [com.zynergylabs.forager.app.domain.alertAudibilityWarning]
     * and shown once as a Snackbar over the map. Carries an [RecordingNotice.id] that increments
     * per recording so the *same* text on a later trip re-shows — the same "event, not condition"
     * shape [startRecordingErrorMessage]'s Toast uses, except that field only clears on the next
     * success and would not re-fire for an identical message.
     *
     * The off-track alert itself no longer passes through this state: it used to be an
     * `offTrackAlertId` counter here that a `LaunchedEffect` in `MainActivity` observed, and that
     * composed path is why nothing fired with the screen off — see
     * [com.zynergylabs.forager.app.domain.AlertDelivery], which [TrackRecordingViewModel.returnToStart] now
     * calls directly.
     */
    val tripStartWarning: RecordingNotice? = null,
    /**
     * Timestamp-filter dispatch, Item 3: the once-per-recording notice that the read seam is
     * excluding most of the active track as network-provider fixes
     * ([com.zynergylabs.forager.app.domain.isMostlyNetworkFixes]) — the case where a device's GPS clock is not
     * second-aligned and the map would otherwise show little or no line in silence. Set by the
     * breadcrumb poll the first time it sees the condition, never again within the recording, and
     * cleared on the next start. Same one-shot shape as [tripStartWarning].
     */
    val networkFixesNotice: RecordingNotice? = null,

    /**
     * When the light goes, for the position this recording last had a fix at.
     *
     * Always present and never null: [SundownCountdown] has a case for every situation including
     * "no position yet", so a screen cannot render a blank where a time should be. See that type
     * for why a blank is the specific failure worth designing against here.
     */
    val sundownCountdown: SundownCountdown = SundownCountdown.NoPositionYet,
) {
    val isRecording: Boolean get() = activeTrack != null
}

/** A one-shot message for the map's Snackbar host, keyed by [id] so an identical [message] re-shows — see [TrackRecordingUiState.tripStartWarning] and [TrackRecordingUiState.networkFixesNotice]. */
data class RecordingNotice(val id: Int, val message: String)
