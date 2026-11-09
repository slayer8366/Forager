package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.PathHome
import com.zynergylabs.forager.app.domain.PendingDelete
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.nextRouteLine
import com.zynergylabs.forager.app.domain.withoutPending
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.BackByShown
import com.zynergylabs.forager.app.domain.SundownLine
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
    /**
     * Whether [waypoints] has been read at least once (dispatch 2026-09-28-502). Until it has, an empty
     * list says nothing, so a waypoint being navigated to that is not in it is not yet "deleted".
     */
    val waypointsLoaded: Boolean = false,
    /** How many Cartography entries currently keep a reference to each waypoint (by id) — Journal Stage 2b's 4b deletion warning. Loaded alongside [waypoints]; a waypoint missing from this map has never been counted, treated as zero the same as an explicit zero. */
    val waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /**
     * Whether the walker has said they're now heading back, distinct from [isRecording] — outbound
     * travel is never "off track" (you're the one making the track), so
     * [com.zynergylabs.forager.app.domain.OffTrackJudge] only runs once this is true. See
     * [TrackRecordingViewModel.startReturn]'s doc comment for the full reasoning.
     *
     * A copy, since dispatch 2026-09-28-400, Amendment 2: the flag itself is
     * [com.zynergylabs.forager.app.domain.ReturnWatch]'s, which outlives this screen. The ViewModel copies it
     * here only while the watch is for the track this ViewModel is recording.
     */
    val isReturning: Boolean = false,
    /** What [com.zynergylabs.forager.app.domain.ReturnWatch] last decided, while [isReturning]. A copy, on the same rule as [isReturning]. */
    val isOffTrack: Boolean = false,
    /**
     * RECORD -844, -845: the return on this screen's recording has just ended because the walker reached
     * the start, and the navigation display is holding "Arrived", with the start ringed, for
     * [ARRIVAL_HOLD_MILLIS] before it closes by itself. Held, not live: what the display shows while this
     * is true does not depend on where the walker is. Never true while [isReturning].
     */
    val isArrivalHeld: Boolean = false,
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
     * The way home along the walked route ([com.zynergylabs.forager.app.domain.routeHome]): where
     * the HUD's needle aims, the route distance, or why there is none (dispatch 2026-09-28-423, plan
     * task T6). Computed by [TrackRecordingViewModel]'s route tick, every 5 s **only while
     * [isReturning]**, from the track its 15 s poll last read and the last accuracy-gated fix;
     * `null` otherwise, and until the first tick has both a polled track and a gated fix. Its hop
     * band is the hysteresis carried between ticks; a new return starts at
     * [com.zynergylabs.forager.app.domain.HopBand.NONE].
     */
    val routeHome: RouteHome? = null,
    /**
     * The way back as the map draws it while returning (dispatch 2026-09-28-497, plan task T7):
     * [nextRouteLine] of each [routeHome], cleared wherever [routeHome] is cleared.
     */
    val routeLine: RouteLine? = null,
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
     * [com.zynergylabs.forager.app.domain.AlertDelivery], which is called directly, since dispatch
     * 2026-09-28-400 Amendment 2 by [com.zynergylabs.forager.app.domain.ReturnWatch] and no longer by this
     * screen's ViewModel.
     */
    val tripStartWarning: RecordingNotice? = null,
    /**
     * Dispatch 2026-09-28-626 (plan T14): [com.zynergylabs.forager.app.domain.BACKGROUND_RUN_PROMPT]
     * for the recording that just started, when the phone would block the off-track reminder and
     * had not already been seen blocking it; `null` otherwise. Shown once in the map's Snackbar
     * host; a tap opens Forager's App info page. Same one-shot shape as [tripStartWarning].
     */
    val backgroundRunPrompt: RecordingNotice? = null,
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
     * The sundown line for this screen's recording, as [com.zynergylabs.forager.app.domain.SundownWatch]
     * publishes it (dispatch 2026-09-28-592, Amendment 1): the map's strip shows it while recording,
     * the navigation display once Return or Navigate is tapped. `null` with no recording. While
     * recording it is never blank: [SundownLine] has a case for every situation, "finding your
     * position" included. Replaces the countdown this state carried, which nothing rendered.
     */
    val sundownLine: SundownLine? = null,
    /**
     * This recording's Back by (dispatch 2026-09-28-645, plan task T15), as
     * [com.zynergylabs.forager.app.domain.BackByWatch] publishes it: the time set and the clock it was
     * published at. `null` with none set, or no recording. The strip shows its line in the last hour
     * ([BackByShown.lineShown]); the quick menu and the gear's dot show it whenever it is set.
     */
    val backBy: BackByShown? = null,
    /**
     * The waypoint whose delete was asked for (a swipe on its Records row, journal redesign J4) and
     * has not run yet: the Undo snackbar is still up. See [TrackRecordingViewModel.requestRemoveWaypoint].
     */
    val pendingWaypointDelete: PendingDelete<Waypoint>? = null,
    /** The track whose delete was asked for (swipe or the details' Delete) and has not run yet: the Undo snackbar is still up. Part 2 follow-ups F1 item 5. */
    val pendingTrackDelete: PendingDelete<Track>? = null,
    /** How many journal entries keep each track (loaded with [tracks]); a track missing here has no count and the snackbar says nothing about entries. */
    val trackEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** Set when a committed track delete failed (the track is back in [tracks]); cleared by the next successful load. */
    val tracksErrorMessage: String? = null,
) {
    val isRecording: Boolean get() = activeTrack != null

    /**
     * The walk back to the origin along the recorded track, joined to itself
     * ([com.zynergylabs.forager.app.domain.pathHome]): the [PathHome] [routeHome] was built from,
     * so it comes from the same route search (dispatch 2026-09-28-423, the planner's ruling on
     * question 1; until then the 15 s poll ran a search of its own for it). `null` whenever
     * [routeHome] is not [RouteHome.Ahead], which, unlike before, includes a walker in the far hop
     * band; nothing reads it there, since the HUD shows [routeHome] itself. The walking time built
     * on the same value ([com.zynergylabs.forager.app.domain.returnWalkingTime]) still has no
     * caller, on purpose.
     */
    val pathHome: PathHome? get() = (routeHome as? RouteHome.Ahead)?.pathHome

    /**
     * [waypoints] without [pendingWaypointDelete]: what every screen shows (J4, "a pending record is
     * hidden from every Journal list, count and the All logbook at once"). `MainActivity` passes this,
     * not [waypoints], to the screen, so the map hides a pending waypoint too until Undo brings it back.
     */
    val visibleWaypoints: List<Waypoint> get() = waypoints.withoutPending(pendingWaypointDelete) { it.id }

    /** [tracks] without [pendingTrackDelete]: what every screen shows (a pending record is hidden at once, as a pending waypoint is). */
    val visibleTracks: List<Track> get() = tracks.withoutPending(pendingTrackDelete) { it.id }
}

/** A one-shot message for the map's Snackbar host, keyed by [id] so an identical [message] re-shows — see [TrackRecordingUiState.tripStartWarning] and [TrackRecordingUiState.networkFixesNotice]. */
data class RecordingNotice(val id: Int, val message: String)
