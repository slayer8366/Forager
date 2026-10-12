package com.zynergylabs.forager.app.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.NETWORK_FIXES_RECORDING_NOTICE
import com.zynergylabs.forager.app.domain.alertAudibilityWarning
import com.zynergylabs.forager.app.domain.BACKGROUND_RUN_PROMPT
import com.zynergylabs.forager.app.domain.isMostlyNetworkFixes
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.BackByChoice
import com.zynergylabs.forager.app.domain.BackByControl
import com.zynergylabs.forager.app.domain.NoBackBy
import com.zynergylabs.forager.app.domain.backByAtFor
import com.zynergylabs.forager.app.domain.SundownShown
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.LocationSampler
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.routeHome
import com.zynergylabs.forager.app.domain.nextRouteLine
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.mayAct
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.autoWaypointName
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.domain.PendingDeleteSlot
import com.zynergylabs.forager.app.domain.RecordingHalt
import com.zynergylabs.forager.app.domain.RecordingHaltReason
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.ReturnWatchState
import com.zynergylabs.forager.app.ui.log.PendingDeleteCommitScope
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns track-recording and waypoint UI state — kept in its own package/ViewModel rather than
 * folded into [com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel], the same reasoning
 * `LogPanel`'s doc comment gives for the mushroom log's own `MushroomLogViewModel`. Unlike the log,
 * this doesn't get a separate screen: breadcrumbs and waypoint markers are overlays on the same map
 * [com.zynergylabs.forager.app.ui.availability.AvailabilityScreen] already renders, threaded in as state the
 * same way `compassProvider`/`locateMeStatus` already are — a genuinely separate *ViewModel*, not a
 * separate *destination*.
 *
 * ## Why breadcrumbs are polled, not reactive
 *
 * [TrackRepository] is plain suspend calls, matching every other Room-backed repository in this
 * app — there is no `Flow<Track>` to collect. [com.zynergylabs.forager.app.service.TrackRecordingService]
 * itself batches writes (every 20 points or 30 seconds, whichever first) rather than writing per
 * fix, so polling at roughly that same cadence ([POLL_INTERVAL_MILLIS]) shows breadcrumbs about as
 * fresh as the data actually is, without adding a reactive-query layer this database has never
 * needed anywhere else.
 *
 * ## What this does not handle
 *
 * A track left recording when the app process dies (OS kills it, not a user-initiated stop) is not
 * resumed here — [activeTrack][TrackRecordingUiState.activeTrack] is in-memory ViewModel state, lost
 * on process death same as the rest of this screen's state, while the foreground service and its
 * Room row survive independently. That leaves a track with `endedAt == null` and no UI pointing at
 * it — the "resumable/closeable state" [com.zynergylabs.forager.app.service.TrackRecordingService]'s own doc
 * comment already flags as the UI's responsibility, deliberately not built here to keep this pass
 * scoped to starting, stopping, and showing a recording that's actually running.
 *
 * **One case of that is now handled (dispatch 2026-09-28-400, Amendment 3):** when the Activity is
 * destroyed but the process and the service live on (the app swiped away from recents), a new
 * ViewModel takes the running recording up; see [takeUpRunningRecording]. A process that died is
 * still not resumed, and the open row it leaves is still not treated as a recording.
 *
 * ## Why return-to-start is fed by [locationTracker], not a one-shot fetch
 *
 * The first pass of the return-to-vehicle screen recomputed [ReturnToStartInfo] from
 * `AvailabilityViewModel`'s `locateMeStatus` — a one-shot "where am I right now" fetch, refreshed
 * only when the user taps the locate-me icon. That made the bearing/distance shown stale between
 * taps. This ViewModel now collects [LocationTracker.fixes] itself, the same continuous stream
 * [com.zynergylabs.forager.app.service.TrackRecordingService] collects for the track's own points, whenever a
 * recording is active — so [TrackRecordingUiState.returnToStart] updates on every fix, not just on
 * demand. This does mean two independent OS location-listener
 * registrations while recording (the service's and this one) rather than one shared stream — a
 * real, accepted duplication, not a hidden one, in exchange for not re-plumbing the service to
 * publish its fixes back out to the UI layer for this one reader.
 *
 * ## The off-track decision is not here any more (dispatch 2026-09-28-400, Amendment 2)
 *
 * It was: the returning flag, the rolling window, the cooldown and the alert were this class's,
 * fed by the collection above, and all of it died when the app was swiped away. They are
 * [ReturnWatch]'s now, held by the app's container and fed by the recording service. This class
 * calls the watch from [startReturn] and [stopReturn], hands it the start point when that changes,
 * and copies two things from it into [TrackRecordingUiState]: [TrackRecordingUiState.isReturning]
 * and [TrackRecordingUiState.isOffTrack]. The distance on the Return button is still worked out
 * here ([returnToStart]), from this class's own fixes.
 *
 * So the same sum is done in two places, from the two listeners that already existed. They can
 * differ by one fix: the button's distance is from the last fix this class's listener received,
 * "off track now" from the last the service's received. On a phone both arrive about once a
 * second from the same source. Nothing on screen compares the two.
 */
class TrackRecordingViewModel(
    private val trackRepository: TrackRepository,
    private val startTrack: StartTrackUseCase,
    private val getWaypoints: GetWaypointsUseCase,
    private val createWaypoint: CreateWaypointUseCase,
    private val deleteWaypoint: DeleteWaypointUseCase,
    private val computeReturnToStart: ComputeReturnToStartUseCase,
    private val locationTracker: LocationTracker,
    private val getTracks: GetTracksUseCase,
    /**
     * Where the off-track decision lives and where its alert is delivered from: the app's one
     * [ReturnWatch], the same instance the recording service feeds. Required, with no default: a
     * ViewModel that quietly built its own would be the old fault again, a decision that dies
     * with the screen. This class no longer takes an `AlertDelivery`, because it delivers nothing.
     */
    private val returnWatch: ReturnWatch,
    /** Read once per [startRecording] for [TrackRecordingUiState.tripStartWarning]; never watched live. */
    private val alertAudibility: AlertAudibility,
    /**
     * Logs a failure's throwable for diagnosis, without ever exposing its text to the user — see
     * [ErrorLog]'s own doc comment for why this exists rather than calling [android.util.Log]
     * directly. Defaults to discarding the throwable, which is exactly what makes every existing
     * test safe under a plain JVM run with no per-test setup; `MainActivity` wires the real
     * `Log.w`-backed one for production.
     */
    private val errorLog: ErrorLog = ErrorLog { _, _, _ -> },
    /** The clock the auto-created waypoints' names read. The off-track cooldown's clock is [ReturnWatch]'s own. */
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    /**
     * How many Cartography entries currently keep a reference to a waypoint — Journal Stage 2b's
     * 4b deletion warning. A plain suspend function rather than threading the whole
     * `CartographyEntryRepository`/`GetEntryReferenceCountUseCase` in: this ViewModel needs exactly
     * one query from that surface, and a function type keeps this constructor (and every existing
     * test's fixture) from having to stand up a Cartography repository just to construct it. Defaults
     * to always reporting zero, matching every other optional dependency here.
     */
    private val getWaypointReferenceCount: suspend (String) -> Int = { 0 },
    /** The zone the auto-created origin/end waypoints' default names are written in — injected so a test can pin the wall-clock text. */
    private val zone: ZoneId = ZoneId.systemDefault(),
    /**
     * The phone's 12/24-hour setting, read each time an origin/end waypoint is named (data part D, RECORD -703). MainActivity
     * passes `DateFormat.is24HourFormat`; the default, 12-hour, is for tests that do not name waypoints by the clock.
     */
    private val is24HourClock: () -> Boolean = { false },
    /**
     * The sundown line the recording's [com.zynergylabs.forager.app.domain.SundownWatch] publishes
     * (dispatch 2026-09-28-592, Amendment 1, RECORD -593): copied into
     * [TrackRecordingUiState.sundownLine] while it is for this screen's recording. The watch is the
     * one place the line's start-back time is computed, the leave-by alert's own; this ViewModel's
     * own countdown, which nothing rendered, is retired. Defaults to a watch that publishes nothing.
     */
    private val sundownShown: StateFlow<SundownShown?> = MutableStateFlow(null),
    /**
     * Back by (dispatch 2026-09-28-645, plan task T15): the app's one
     * [com.zynergylabs.forager.app.domain.BackByWatch], the instance the recording service drives. This
     * screen sets and clears it ([setBackBy], [clearBackBy]) and copies what it publishes into
     * [TrackRecordingUiState.backBy] while it is for this screen's recording. Defaults to [NoBackBy].
     */
    private val backBy: BackByControl = NoBackBy,
    /**
     * Where a waypoint delete still pending when this ViewModel is cleared is committed (journal
     * redesign J4): `viewModelScope` is cancelled by then. See [PendingDeleteCommitScope].
     */
    private val pendingDeleteCommitScope: CoroutineScope = PendingDeleteCommitScope,
    /**
     * How many journal entries keep a track, for the Undo snackbar's warning, read the way
     * [getWaypointReferenceCount] is. Part 2 follow-ups F1 item 5.
     */
    private val getTrackReferenceCount: suspend (String) -> Int = { 0 },
    /** The real delete of a track, run only when its Undo window closes. Part 2 follow-ups F1 item 5. */
    private val deleteTrack: DeleteTrackUseCase,
    /**
     * The start marker of a recording this ViewModel takes up ([takeUpRunningRecording]): the one
     * the track row points at. Required, with no default: a default that answered "none recorded"
     * would let a forgotten wiring place a second start marker without saying so (dispatch
     * 2026-09-28-400, Amendment 3, the planner's ruling on the coder's stop).
     */
    private val getTrackOriginWaypoint: GetTrackOriginWaypointUseCase,
    /**
     * What the user is told when Record is pressed while another recording is running: the
     * owner's sentence, held in `strings.xml` and read by `MainActivity`, which has the `Context`
     * this class does not. Required for the same reason: one copy of the words.
     */
    private val alreadyRecordingMessage: String,
    /**
     * The process's one sweep of tracks an earlier process left open (dispatch 2026-09-28-400,
     * Amendment 3, Part 3b). The first recording ViewModel to be created launches it; see
     * [AbandonedTrackSweepOnce] for why here and not at app start, and why that is safe. Required,
     * with no default, so a forgotten wiring does not compile.
     */
    private val abandonedTrackSweepOnce: AbandonedTrackSweepOnce,
    /**
     * The way-home search the route tick runs, [routeHome] in production. A parameter so a test
     * can count the searches: "one route search per tick" (dispatch 2026-09-28-423) is a number.
     */
    private val findRouteHome: (track: Track, current: LatLng, origin: Waypoint?, previousHopBand: HopBand) -> RouteHome = ::routeHome,
    /**
     * Dispatch 2026-09-28-626 (plan T14; Amendment 1, RECORD -627): whether the recording that has
     * just started should show [BACKGROUND_RUN_PROMPT], asked once per [startRecording] beside the
     * silenced-phone read. `MainActivity` wires [com.zynergylabs.forager.app.domain.OffTrackReminderCheck.atRecordingStart].
     * Defaults to never, like the optional dependencies above.
     */
    private val shouldPromptBackgroundRun: suspend () -> Boolean = { false },
    /**
     * RECORD -660 (item 4): the recording service's report of a recording it stopped on its own,
     * `AppContainer.recordingHalts` in production. A report for this screen's recording turns it to
     * not recording, with the reason. Defaults to a service that never reports.
     */
    private val recordingHalts: StateFlow<RecordingHalt?> = MutableStateFlow(null),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackRecordingUiState())
    val uiState: StateFlow<TrackRecordingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    /** The route tick, running only while returning. See [beginRouteTicks]. */
    private var routeJob: Job? = null

    /** The arrival hold's timer (RECORD -845), running only while [TrackRecordingUiState.isArrivalHeld]. See [holdArrival]. */
    private var arrivalHoldJob: Job? = null

    /** The track the 15 s poll last read for the active recording: what the route tick routes along. */
    private var lastPolledTrack: Track? = null

    /** The one waypoint whose delete is pending (journal redesign J4) — see [requestRemoveWaypoint]. */
    private val waypointDeletes = PendingDeleteSlot<String, Waypoint> { it.id }

    /** The one track whose delete is pending (Part 2 follow-ups F1 item 5) — see [requestRemoveTrack]. */
    private val trackDeletes = PendingDeleteSlot<String, Track> { it.id }
    private var locationJob: Job? = null

    // Navigation HUD stage one — the auto-created origin/end waypoints. lastGatedFix is the most
    // recent fix that passed the active mode's own accuracy gate (LocationSampler's first-fix
    // rule), what stopRecording() seeds the end waypoint from; originCreationInFlight stops a
    // second fix arriving during the origin's own async save from creating a second origin.
    //
    // Dispatch 2026-09-28-527 split it in two. lastGatedGpsFix is the same rule for GPS fixes only,
    // and is what everything that acts reads: the origin and end waypoints and the route home (RECORD
    // -558, and the dispatch's rule 2 for the end). lastGatedFix, any provider, was read only by the
    // sundown countdown, and went with it (dispatch 2026-09-28-592): the line is the watch's now, which
    // takes its sunset from any fix as that countdown did.
    private var lastGatedGpsFix: TrackPoint? = null

    private var originCreationInFlight = false
    private var recordingNoticeIds = 0
    private var networkFixesNoticeShown = false

    /**
     * How the start marker is settled for a recording this ViewModel took up, or `null` for a
     * recording it started itself (whose marker comes from the first good fix, as always).
     * See [settleTakenUpOrigin].
     */
    private var takenUpOrigin: TakenUpOrigin? = null
    private var takeUpInFlight = false

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

    init {
        loadWaypoints()
        loadTracks()
        viewModelScope.launch { takeUpRunningRecording() }
        viewModelScope.launch { sweepAbandonedTracks() }
        // The watch changes on the service's thread as well as from this class's own calls (a
        // fix decides off track; a stop from the notification ends it), so it is collected, not
        // only read after each call.
        viewModelScope.launch { returnWatch.state.collect(::copyFromWatch) }
        // The sundown line, the same way: published by the watch the service ticks.
        viewModelScope.launch { sundownShown.collect { copySundownLine() } }
        // Back by, the same way: the service's ticks and the alert's buttons change it too.
        viewModelScope.launch { backBy.shown.collect { copyBackBy() } }
        viewModelScope.launch { recordingHalts.collect { halt -> if (halt != null) onRecordingHalted(halt) } }
    }

    /**
     * The quick menu's "+1 h", "+2 h", "+3 h" or a picked time (dispatch 2026-09-28-645): sets this
     * recording's back-by time, worked out from this class's clock and zone ([backByAtFor]). Does
     * nothing with no recording: Back by exists only while recording (the owner, "During a recording
     * only"), and the menu offers no choice then.
     */
    fun setBackBy(choice: BackByChoice) {
        val trackId = uiState.value.activeTrack?.trackId ?: run {
            // Dispatch 2026-09-28-796: no longer silent. The menu offers no choice without a recording,
            // so reaching here means the screen and the recording disagree; it is logged and recorded.
            errorLog.w(TAG, "Back by was not set: no recording on this screen.", IllegalStateException("back-by with no active track"))
            backBy.setWithNoRecording()
            return
        }
        val at = backByAtFor(choice, currentTime.nowEpochMillis(), zone)
        if (!backBy.set(trackId, at)) {
            errorLog.w(TAG, "Back by was not set for track '$trackId': another recording is being watched.", IllegalStateException("back-by refused"))
        }
    }

    /** The quick menu's "Clear". */
    fun clearBackBy() {
        val trackId = uiState.value.activeTrack?.trackId ?: return
        backBy.clear(trackId)
    }

    /** Copies the watch's back-by time into the screen's state while it is for this screen's recording, as [copySundownLine] does. */
    private fun copyBackBy() {
        _uiState.update { state ->
            val shown = backBy.shown.value
            state.copy(backBy = shown?.takeIf { state.activeTrack != null && it.trackId == state.activeTrack.trackId })
        }
    }

    /**
     * The service stopped [halt]'s recording on its own (RECORD -660, item 4; the owner: "Fix it, say
     * why"). If it is this screen's recording, the screen shows not recording, exactly as a stop leaves
     * it, and says why through [TrackRecordingUiState.startRecordingErrorMessage], the field the screen
     * already shows as a Toast. A report for any other track is ignored. The service has already ended
     * the track and stopped itself; the stop this sends it in turn finds nothing running.
     */
    private fun onRecordingHalted(halt: RecordingHalt) {
        if (uiState.value.activeTrack?.trackId != halt.trackId) return
        stopRecording()
        val message = when (halt.reason) {
            RecordingHaltReason.FOREGROUND_REFUSED -> RECORDING_COULD_NOT_START_MESSAGE
            RecordingHaltReason.NO_LOCATION_PERMISSION -> RECORDING_NEEDS_LOCATION_PERMISSION_MESSAGE
        }
        _uiState.update { it.copy(startRecordingErrorMessage = message) }
    }

    /**
     * Launches the process's one sweep of tracks left open by an earlier process, and reads the
     * track list again when it has ended any, so a track it finished reads as finished on this
     * screen without Records being reopened. `null` from [AbandonedTrackSweepOnce.runOnce] means
     * another ViewModel already ran it in this process; nothing to do. A failure to list the tracks
     * is logged, and the next ViewModel to be created tries again.
     */
    private suspend fun sweepAbandonedTracks() {
        val result = abandonedTrackSweepOnce.runOnce() ?: return
        result
            .onSuccess { sweep -> if (sweep.ended.isNotEmpty() || sweep.endedWithNoStoredPoint.isNotEmpty()) loadTracks() }
            .onFailure { error -> errorLog.w(TAG, "Couldn't list the tracks to end the ones left open by an earlier process; none was changed.", error) }
    }

    /**
     * Copies the watch's returning and off-track flags into the screen's state, **only when the
     * watch is for the track this ViewModel is recording**. A ViewModel with no active track, or
     * with a different one, shows no return: a screen reopened after a swipe-away does not adopt
     * the running recording yet (that is the dispatch's second path, Part 3), and until it does it
     * must not show half of one.
     */
    private fun copyFromWatch(watch: ReturnWatchState = returnWatch.state.value) {
        holdArrivalIfJustArrived(watch)
        _uiState.update { state ->
            val mine = state.activeTrack != null && watch.trackId == state.activeTrack.trackId
            state.copy(isReturning = mine && watch.isReturning, isOffTrack = mine && watch.isOffTrack)
        }
        copySundownLine()
        copyBackBy()
        // The route tick runs exactly while this screen shows a return, whichever way the return
        // reached it: this screen's own Return, or a recording taken up mid-return.
        if (uiState.value.isReturning) {
            if (routeJob == null) beginRouteTicks()
        } else if (routeJob != null) {
            endRouteTicks()
        }
    }

    /**
     * Copies the watch's sundown line into the screen's state while it is for this screen's
     * recording, the rule [copyFromWatch] follows; `null` otherwise. Called when the watch publishes
     * and from [copyFromWatch], which runs whenever this screen's recording changes.
     */
    private fun copySundownLine() {
        _uiState.update { state ->
            val shown = sundownShown.value
            state.copy(sundownLine = shown?.line?.takeIf { state.activeTrack != null && shown.trackId == state.activeTrack.trackId })
        }
    }

    /** The start point as the screen shows it: the origin waypoint once it exists, the first breadcrumb before that. */
    private fun startPoint(): TrackPoint? =
        uiState.value.originWaypoint?.asStartPoint() ?: uiState.value.breadcrumbPoints.firstOrNull()

    /**
     * Gives the watch the start point this ViewModel shows, so the alert and the screen measure to
     * one place. Called whenever that point can have changed. The watch keeps the last one after
     * this ViewModel is gone.
     */
    private fun handStartPointToWatch() {
        val active = uiState.value.activeTrack ?: return
        val start = startPoint() ?: return
        returnWatch.setStartPoint(active.trackId, start)
    }

    /**
     * Creates and persists the [com.zynergylabs.forager.app.domain.model.Track] row the recording will write
     * into. Starting the actual foreground service (an Android-layer action, needing a `Context`
     * this ViewModel doesn't hold) is the caller's job once [TrackRecordingUiState.activeTrack]
     * becomes non-null — see `MainActivity`'s `LaunchedEffect` on this state.
     */
    fun startRecording(mode: TrackRecordingMode = TrackRecordingMode.BALANCED) {
        // One recording at a time (dispatch 2026-09-28-400, Amendment 3, step 4). The begun watch
        // is the live answer to "is something recording". If it is, and this screen is not the
        // one showing it, a second track row would get no points and could never be ended, which
        // is the fault Part 1 pinned. So no row is made, and the user is told in the owner's
        // words. A screen that has taken the recording up offers Stop and does not reach here;
        // this is the guard behind that.
        val running = returnWatch.state.value
        if (running.isBegun && running.trackId != uiState.value.activeTrack?.trackId && !isOwnStoppedRecording(running)) {
            errorLog.w(
                TAG,
                "Record was refused: the recording service is already recording track '${running.trackId}'.",
                IllegalStateException("a recording is already running"),
            )
            _uiState.update { it.copy(startRecordingErrorMessage = alreadyRecordingMessage) }
            // The screen should be showing that recording. If it is not yet, take it up now, so
            // that "Stop it" is something the user can do from here.
            viewModelScope.launch { takeUpRunningRecording() }
            return
        }
        viewModelScope.launch {
            startTrack(null)
                .onSuccess { track ->
                    lastGatedGpsFix = null
                    lastPolledTrack = null
                    originCreationInFlight = false
                    takenUpOrigin = null
                    networkFixesNoticeShown = false
                    // Alert-delivery dispatch, Item 3: "the start of a trip" is here — the user
                    // just chose to rely on the app, and the screen is on because they tapped.
                    // Read once; a phone silenced later in the trip is not re-checked (Item 3.5).
                    val warning = alertAudibilityWarning(alertAudibility.current())
                    _uiState.update {
                        it.copy(
                            activeTrack = ActiveTrack(track.id, track.startedAtEpochMillis, mode),
                            startRecordingErrorMessage = null,
                            breadcrumbPoints = emptyList(),
                            originWaypoint = null,
                            routeHome = null,
                            routeLine = null,
                            tripStartWarning = warning?.let { message -> RecordingNotice(++recordingNoticeIds, message) },
                            networkFixesNotice = null,
                            backgroundRunPrompt = null,
                        )
                    }
                    copyFromWatch()
                    beginPolling(track.id)
                    beginLocationTracking()
                    // Dispatch 2026-09-28-626: "the first time a recording starts with the reminder
                    // on", read once here, as the silenced-phone warning is. Its own launch, so a
                    // slow settings read never holds up the recording.
                    viewModelScope.launch {
                        if (shouldPromptBackgroundRun()) {
                            _uiState.update { it.copy(backgroundRunPrompt = RecordingNotice(++recordingNoticeIds, BACKGROUND_RUN_PROMPT)) }
                        }
                    }
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't start recording.", error)
                    _uiState.update { it.copy(startRecordingErrorMessage = "Couldn't start recording.") }
                }
        }
    }

    /**
     * The Activity's location-permission gate refused to even attempt starting the recording —
     * see `MainActivity`'s `onToggleRecording`/`LaunchedEffect(trackUiState.activeTrack)`. Mirrors
     * [com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onLocateMePermissionDenied]: the
     * permission check itself belongs to the Activity/Context layer, not here, so this only
     * records the outcome onto the existing [TrackRecordingUiState.startRecordingErrorMessage]
     * field — the same one [startRecording]'s own failure path writes to — for
     * `AvailabilityScreen`'s existing Toast-on-error-message rendering to pick up. Never touches
     * [TrackRecordingUiState.activeTrack]: either recording never began (the caller checked before
     * calling [startRecording] at all), or the caller is rolling one back via [stopRecording]
     * immediately after this — either way this method only ever reports the reason.
     */
    fun onStartRecordingPermissionDenied(message: String) {
        _uiState.update { it.copy(startRecordingErrorMessage = message) }
    }

    /**
     * Everything "this ViewModel is no longer recording" means, in one place.
     *
     * Extracted so [stopRecording] and [resyncRecordingState] cannot drift: §2 of the resync
     * dispatch requires the resync to leave the ViewModel in the state a normal stop leaves it in,
     * and sharing the code is the only way that stays true without someone re-checking two lists.
     * The difference between the two callers is then exactly one documented side effect, the end
     * waypoint, which lives in [stopRecording] and not here.
     *
     * Cancelling [locationJob] is what releases the platform location listener, not merely what
     * stops this ViewModel reading it: `LocationTracker.fixes` is a `callbackFlow` whose
     * `awaitClose { removeUpdates(listener) }` runs on cancellation and at no other time. See
     * [com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onLeftForeground], where this
     * project recorded the same mechanism after finding a subscription that outlived every
     * backgrounding.
     */
    private fun clearRecordingState() {
        endArrivalHold()
        pollingJob?.cancel()
        pollingJob = null
        routeJob?.cancel()
        routeJob = null
        lastPolledTrack = null
        locationJob?.cancel()
        locationJob = null
        // This screen's return is over with its recording. The service ends the watch itself when
        // it stops; this covers the moment before it has, and a recording the service never began.
        uiState.value.activeTrack?.let {
            returnWatch.stopReturn(it.trackId)
            stoppedTrackId = it.trackId
        }
        lastGatedGpsFix = null
        originCreationInFlight = false
        takenUpOrigin = null
        _uiState.update {
            it.copy(activeTrack = null, isReturning = false, isOffTrack = false, returnToStart = null, originWaypoint = null, routeHome = null, routeLine = null, sundownLine = null, backBy = null)
        }
    }

    /**
     * Takes up a recording that is running without this screen (dispatch 2026-09-28-400,
     * Amendment 3, step 2). The owner's path: `Recording > swipe the app away > open Forager again
     * > the screen shows the recording still running, and the return HUD if you were heading back`.
     *
     * **What it goes by:** [ReturnWatch]'s state saying it is begun. That is true only while the
     * recording service is recording. An open track row is not enough: a killed process leaves the
     * same row, with nothing recording into it.
     *
     * **What is taken up:** the track id and mode from the watch; the start time and the
     * breadcrumbs from the track's row; the returning and off-track flags, copied from the watch
     * as for any recording; the start marker ([settleTakenUpOrigin]). This ViewModel's own poll
     * and fix collection start, as [startRecording] starts them.
     *
     * **What is not:** the trip-start warning. It belongs to a Record tap, and this is not one.
     * The network-fixes notice has no memory outside this class, so it can show a second time for
     * a recording that is taken up; that is reported in Part 3a's report, not designed around.
     *
     * **When:** when this ViewModel is created, when the app comes to the foreground
     * ([onEnteredForeground]), and when Record is refused ([startRecording]). A no-op whenever
     * this ViewModel already has a recording, or the watch is not begun: a recording that ended
     * while the app was away is not taken up, and the screen offers Record.
     *
     * **A row that cannot be read takes up nothing** and is logged. "Could not read the row" is
     * not "no recording", and it is tried again at the next foreground.
     */
    private suspend fun takeUpRunningRecording() {
        if (uiState.value.activeTrack != null || takeUpInFlight) return
        val running = returnWatch.state.value
        if (!running.isBegun || isOwnStoppedRecording(running)) return
        val trackId = running.trackId ?: return
        val mode = running.mode ?: return
        takeUpInFlight = true
        try {
            val track = trackRepository.getById(trackId).getOrElse { error ->
                errorLog.w(TAG, "Couldn't read track '$trackId' to take up its recording; the screen shows no recording.", error)
                return
            }
            if (track == null) {
                errorLog.w(
                    TAG,
                    "The recording service is recording track '$trackId', which has no row; the screen shows no recording.",
                    IllegalStateException("no track '$trackId'"),
                )
                return
            }
            // The read suspended. Go by what is true now, not by what was true before it.
            val stillRunning = returnWatch.state.value
            if (uiState.value.activeTrack != null || !stillRunning.isBegun || stillRunning.trackId != trackId) return
            if (isOwnStoppedRecording(stillRunning)) return

            val active = ActiveTrack(trackId, track.startedAtEpochMillis, mode)
            lastGatedGpsFix = null
            lastPolledTrack = null
            originCreationInFlight = false
            takenUpOrigin = TakenUpOrigin.NOT_LOOKED_UP
            _uiState.update {
                it.copy(
                    activeTrack = active,
                    startRecordingErrorMessage = null,
                    breadcrumbPoints = track.points,
                    originWaypoint = null,
                    routeHome = null,
                    routeLine = null,
                )
            }
            copyFromWatch()
            settleTakenUpOrigin(active, track)
            beginPolling(trackId)
            beginLocationTracking()
        } finally {
            takeUpInFlight = false
        }
    }

    /**
     * Whether the watch is still begun for the recording this screen has just stopped.
     *
     * **The window this closes** (found in the planner's review of Part 3a): Stop clears this
     * screen's recording at once, but the service is stopped a moment later, when `MainActivity`'s
     * effect has sent `ACTION_STOP` and the service has handled it. Until then the watch is still
     * begun for the stopped track while this screen has no active track, which is the very state
     * the refusal in [startRecording] and [takeUpRunningRecording] act on. Without this, a second
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
    private fun isOwnStoppedRecording(running: ReturnWatchState): Boolean {
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
     * walker is now and not where they started; that is why [beginLocationTracking] makes no
     * marker for a taken-up recording. If the track has no recorded point yet, none is made until
     * one exists. Called when the recording is taken up and again on every poll, so a late first
     * point, a failed lookup or a failed save is picked up on the next one.
     *
     * "First recorded point" is the first of the track's points as this screen reads them, the
     * same point the Return control already falls back to.
     */
    private suspend fun settleTakenUpOrigin(active: ActiveTrack, track: Track) {
        if (takenUpOrigin == TakenUpOrigin.NOT_LOOKED_UP) {
            getTrackOriginWaypoint(active.trackId)
                .onSuccess { waypoint ->
                    if (uiState.value.activeTrack?.trackId != active.trackId || takenUpOrigin != TakenUpOrigin.NOT_LOOKED_UP) return
                    if (waypoint != null) {
                        takenUpOrigin = TakenUpOrigin.FOUND
                        _uiState.update { it.copy(originWaypoint = waypoint) }
                        handStartPointToWatch()
                    } else {
                        takenUpOrigin = TakenUpOrigin.NONE_RECORDED
                    }
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't read the start marker of track '${active.trackId}'; none is shown, and none is made until it can be read.", error)
                }
        }
        if (takenUpOrigin == TakenUpOrigin.NONE_RECORDED && uiState.value.originWaypoint == null && !originCreationInFlight) {
            val first = track.points.firstOrNull() ?: return
            createOriginWaypoint(active, first, namedAtEpochMillis = first.timestampEpochMillis)
        }
    }

    /**
     * Clears local recording state. Ending the track's own row (`endedAtEpochMillis`) is the
     * foreground service's job once it receives the stop intent — see
     * [com.zynergylabs.forager.app.service.TrackRecordingService.stopRecording] — not duplicated here.
     *
     * Navigation HUD stage one: also creates the track's **end waypoint** (see
     * [WaypointDesignation.END]) from the last fix that passed the mode's accuracy gate, linked by
     * `trackId`. None if no fix ever passed — the same "validly absent" rule the origin follows.
     * Async, after local state is cleared, so the stop itself is never held up by the save.
     */
    fun stopRecording() {
        val endingTrack = uiState.value.activeTrack
        val endFix = lastGatedGpsFix
        clearRecordingState()
        if (endingTrack != null && endFix != null) {
            viewModelScope.launch {
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
    }

    /**
     * The hosting Activity reached `ON_START`. Resynchronizes against storage — see
     * [resyncRecordingState].
     */
    fun onEnteredForeground() {
        viewModelScope.launch {
            // First: a recording running without this screen is taken up (Amendment 3). Then the
            // resync, unchanged, for a recording this screen already knows.
            takeUpRunningRecording()
            resyncRecordingState()
        }
    }

    /**
     * The hosting Activity reached `ON_STOP`. Resynchronizes against storage, identically to
     * [onEnteredForeground], and the identical body is the point rather than an oversight.
     *
     * **Why both, when the dispatch asked only for resume.** Resume alone leaves the case where the
     * user stops from the shade and never reopens the app: nothing runs, so nothing clears, and
     * [locationJob] keeps a platform location listener registered with no foreground service and no
     * notification behind it. Resyncing here closes that, because backgrounding is the one thing
     * that reliably happens after a stop from the shade.
     *
     * **Why this and not the foreground gate `AvailabilityViewModel` uses.** That gate releases the
     * subscription on `ON_STOP` unconditionally, which is right there and would be a regression
     * here: two things in [beginLocationTracking]'s collector must keep running with the screen off
     * during a *legitimate* recording.
     *
     * 1. The origin waypoint. It is seeded from the first fix that clears the mode's accuracy gate,
     *    with no timeout, and [com.zynergylabs.forager.app.domain.pathHome]'s own doc records that under
     *    canopy that fix may arrive very late or never. A pocketed phone is the normal way to walk
     *    a track. Gate the collector off and a canopy recording acquires no origin at all, and the
     *    navigation HUD then has no target to point at — the return-to-vehicle safety feature.
     * 2. The off-track alert. [returnToStart] was fed from that collector and ran the decision.
     *    **No longer true since dispatch 2026-09-28-400, Amendment 2:** the decision is
     *    [ReturnWatch]'s and is fed by the recording service, so it does not depend on this
     *    collector at all. Reason 1 still stands on its own, so the collector is left as it was.
     *
     * So the collector stays bounded by the recording, as it always was. What changes is that
     * "the recording" now means the track's own row rather than a field nothing repopulates.
     *
     * **What this does not close, recorded rather than fixed.** During a legitimate recording,
     * backgrounding still does not release this ViewModel's subscription, and it is a *second*
     * platform registration on top of the service's own — `LocationTracker.fixes` is a cold
     * `callbackFlow`, so every collector runs `requestLocationUpdates` independently. Routing this
     * ViewModel off the service's fixes instead is the structural change and is out of this scope.
     */
    fun onLeftForeground() {
        viewModelScope.launch { resyncRecordingState() }
    }

    /**
     * Reconciles this ViewModel's in-memory [TrackRecordingUiState.activeTrack] against the track's
     * own row, and clears local recording state if the row says the recording is over.
     *
     * **The defect this exists for.** `TrackRecordingService.stopRecording()` and
     * [stopRecording] are two different methods with the same name on two different classes. The
     * notification's Stop action calls the first and nothing calls the second, so the service ends
     * the track and this ViewModel never hears about it. [TrackRecordingUiState.isRecording] is
     * `activeTrack != null`, an in-memory field nothing repopulates from storage, so the record
     * button goes on claiming a recording that ended.
     *
     * **Why this is not routed through [stopRecording], which §2 asks to be justified.** It shares
     * [clearRecordingState] with it, so the state left behind is identical by construction rather
     * than by inspection. What it deliberately does **not** inherit is [stopRecording]'s end
     * waypoint, which is seeded from [lastGatedGpsFix] — the last GPS fix that cleared the gate, which by
     * the time this runs is where the walker was when they backgrounded the app, not where they
     * stopped recording. Writing a waypoint there would be a data write from a stale position onto
     * an already-ended track, which is the class of thing this whole change is about not doing.
     * A track stopped from the shade therefore still has no end waypoint; that is unchanged by
     * this, and recorded as a known gap rather than fixed here on a position nobody measured.
     *
     * **A read failure clears nothing** and is logged. "Could not read the row" is not evidence the
     * recording ended, and silently treating it as such would stop a live recording's UI on a
     * transient database error. That is the only branch [errorLog] carries, because it is the only
     * one holding a `Throwable` — [ErrorLog] takes a non-null one by design, and fabricating an
     * exception to report a state through it would misuse the abstraction rather than honour the
     * "never swallow a failure" rule it exists for.
     *
     * **A missing row clears, in the same branch as an ended one**, and that is a state rather than
     * an error: a track that no longer exists cannot still be recording, so there is no failure to
     * report. The two are folded together because the ViewModel's response to them is identical and
     * splitting them would imply a distinction the code does not make.
     *
     * Cheap, and a no-op when nothing is recording: it returns before touching storage unless
     * [TrackRecordingUiState.activeTrack] is set. A background-and-return **during** a live
     * recording reads one row, finds `endedAtEpochMillis` null, and changes nothing.
     */
    private suspend fun resyncRecordingState() {
        val active = uiState.value.activeTrack ?: return
        trackRepository.getById(active.trackId)
            .onSuccess { track ->
                if (track == null || track.endedAtEpochMillis != null) clearRecordingState()
            }
            .onFailure { error ->
                errorLog.w(TAG, "Couldn't re-read track '${active.trackId}'; leaving recording state as it is.", error)
            }
    }

    /**
     * Marks the walker as now heading back to the track's start — the only state
     * [com.zynergylabs.forager.app.domain.OffTrackJudge] runs against, and, as of navigation HUD stage one, **the only way the
     * HUD appears**: `CompactMapTab` shows the HUD while this is true, targeting
     * [TrackRecordingUiState.originWaypoint]. Stage two's target picker (This Trip / Recents /
     * Nearby) will need a way to navigate *without* returning, so this coupling is stage one's
     * deliberate smallness, not a design — expect it to be loosened then.
     * Outbound travel away from the start isn't "off track"
     * by any definition available here (there's no planned route to deviate from, only the trail
     * being made right now), so the heuristic would be meaningless, and noisy, applied to it.
     * A no-op while nothing is recording — there is nothing to return to yet.
     */
    fun startReturn() {
        val active = uiState.value.activeTrack ?: return
        endArrivalHold()
        if (!returnWatch.startReturn(active.trackId)) {
            // The watch is begun for another track: the service is recording something this
            // screen did not start (today, a reopened screen that pressed Record again). Nothing
            // would feed a return for this track, so none is shown.
            errorLog.w(
                TAG,
                "Return was not started for track '${active.trackId}': the recording service is recording another track.",
                IllegalStateException("the return watch is for track '${returnWatch.state.value.trackId}'"),
            )
            return
        }
        handStartPointToWatch()
        // Restarted so the track the route tick routes along is read now, not up to 15 s from now.
        // Restarting also re-reads the breadcrumbs, which is harmless, and the network-fixes notice
        // is guarded by its own once-per-recording flag. Launched before copyFromWatch starts the
        // route tick, so on the same dispatcher the poll's read runs first and the first route uses it.
        beginPolling(active.trackId)
        copyFromWatch()
    }

    /**
     * RECORD -844, -845: the return this screen was showing has just ended because the walker reached
     * the start ([ReturnWatchState.arrivedAfterReturn] with the return gone). The navigation display
     * holds "Arrived" for [ARRIVAL_HOLD_MILLIS], then closes by itself. Only a return this screen was
     * showing starts a hold: a screen opened after the arrival goes straight to the plain map. Called
     * from [copyFromWatch] before it copies the watch, so "was showing" is still this screen's last
     * state.
     */
    private fun holdArrivalIfJustArrived(watch: ReturnWatchState) {
        val state = uiState.value
        val active = state.activeTrack ?: return
        if (!state.isReturning || watch.trackId != active.trackId || watch.isReturning || !watch.arrivedAfterReturn) return
        holdArrival()
    }

    /** Starts the arrival hold, ending by itself after [ARRIVAL_HOLD_MILLIS]. */
    private fun holdArrival() {
        arrivalHoldJob?.cancel()
        _uiState.update { it.copy(isArrivalHeld = true) }
        arrivalHoldJob = viewModelScope.launch {
            delay(ARRIVAL_HOLD_MILLIS)
            _uiState.update { it.copy(isArrivalHeld = false) }
            arrivalHoldJob = null
        }
    }

    /** Ends the arrival hold now, if one is running: the walker closed it, tapped Return again, or stopped the recording. */
    private fun endArrivalHold() {
        arrivalHoldJob?.cancel()
        arrivalHoldJob = null
        if (uiState.value.isArrivalHeld) _uiState.update { it.copy(isArrivalHeld = false) }
    }

    /**
     * Clears returning/off-track state without touching the recording itself — see [startReturn]. Also
     * the way to close the arrival hold early (RECORD -845): the display's exit while it holds "Arrived".
     */
    fun stopReturn() {
        endArrivalHold()
        uiState.value.activeTrack?.let { returnWatch.stopReturn(it.trackId) }
        _uiState.update { it.copy(isReturning = false, isOffTrack = false) }
        endRouteTicks()
    }

    /**
     * The HUD's "Try again" (dispatch 2026-09-28-423): the route recomputed now instead of at the
     * next tick. It cannot make data appear; the HUD offers it only where the inputs can change
     * (off the route), never for a track with no usable points.
     */
    fun retryRoute() {
        updateRouteHome()
    }

    /**
     * The route tick (dispatch 2026-09-28-423, plan task T6; decision D4, every 5 s): one route
     * search per tick, at once and then every [ROUTE_TICK_MILLIS], while this screen shows a
     * return ([copyFromWatch] starts and ends it). Its own job beside [beginPolling], not a branch
     * inside it, because the two run at different rates. Like the poll it is unbounded, so its
     * tests stop the recording inside the test body, in a `finally`
     * (`TrackRecordingViewModelTest.runRecordingTest`); stopping the recording cancels it in
     * [clearRecordingState], and [stopReturn] ends it.
     */
    private fun beginRouteTicks() {
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            while (true) {
                updateRouteHome()
                delay(ROUTE_TICK_MILLIS)
            }
        }
    }

    /** Stops the route tick and clears its result: no return is shown, so there is no route. */
    private fun endRouteTicks() {
        routeJob?.cancel()
        routeJob = null
        _uiState.update { it.copy(routeHome = null, routeLine = null) }
    }

    private fun beginPolling(trackId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                trackRepository.getById(trackId).onSuccess { track ->
                    _uiState.update { it.copy(breadcrumbPoints = track?.points.orEmpty()) }
                    val active = uiState.value.activeTrack
                    if (track != null && active != null && active.trackId == trackId) settleTakenUpOrigin(active, track)
                    if (track != null && uiState.value.activeTrack?.trackId == trackId) lastPolledTrack = track
                    handStartPointToWatch()
                    // Timestamp-filter dispatch, Item 3: once per recording, the moment the read
                    // seam is seen to be excluding most of this track — see isMostlyNetworkFixes for
                    // why the threshold also waits for ten stored points before it can fire.
                    if (track != null && !networkFixesNoticeShown && track.isMostlyNetworkFixes()) {
                        networkFixesNoticeShown = true
                        _uiState.update { it.copy(networkFixesNotice = RecordingNotice(++recordingNoticeIds, NETWORK_FIXES_RECORDING_NOTICE)) }
                    }
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * [TrackRecordingUiState.routeHome] from the track the poll last read and the last
     * accuracy-gated fix, while returning ([endRouteTicks] clears it when the return ends). Before
     * there is both a polled track and a gated fix it leaves the value as it is, `null` at the start
     * of a return, which the HUD shows as a dash rather than a failure (the planner's ruling on
     * question 3). The hop band is carried from the previous result, a withheld one included, as
     * [RouteHome.Withheld.hopBand] asks (hysteresis, see [com.zynergylabs.forager.app.domain.pathHome],
     * "The hop"); a new return starts at [HopBand.NONE].
     *
     * Runs on the tick's own coroutine, the main dispatcher, as the poll's path-home search did:
     * about 19 ms on a desktop JVM at the four-hour HIGH_ACCURACY cap (the join dispatch's
     * measurement, for the same search), now every 5 s instead of every 15 s. A device figure
     * does not exist yet; if one says that is a visible stall, moving it off the main dispatcher is
     * the recorded next step, not done here on an estimate. The gated fix, not any fix: the same
     * accuracy rule that seeds the origin.
     */
    private fun updateRouteHome() {
        val state = uiState.value
        if (!state.isReturning) return
        val track = lastPolledTrack ?: return
        val current = lastGatedGpsFix ?: return
        val previousHopBand = when (val previous = state.routeHome) {
            is RouteHome.Ahead -> previous.hopBand
            is RouteHome.Withheld -> previous.hopBand ?: HopBand.NONE
            null -> HopBand.NONE
        }
        val next = findRouteHome(track, LatLng(current.lat, current.lng), state.originWaypoint, previousHopBand)
        // Dispatch -497: the line the map draws moves with the route (nextRouteLine).
        _uiState.update { it.copy(routeHome = next, routeLine = nextRouteLine(it.routeLine, next)) }
    }

    /**
     * See this class's own doc comment for why [returnToStart] is driven from here rather than a
     * one-shot fetch. Navigation HUD stage one: this same stream is what seeds the track's origin
     * waypoint — the **first fix that passes the active mode's accuracy gate**, no timeout (owner
     * decision): the origin lands where the recorder actually stood within seconds in the open,
     * and under canopy it may never, in which case the track validly has no origin. The last-known
     * fix from elsewhere was rejected as a seed because it may be minutes old and somewhere the
     * user has already left; the first breadcrumb because it arrives 30–45 s late via the
     * service's flush and this ViewModel's poll.
     */
    private fun beginLocationTracking() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationTracker.fixes.collect { fix ->
                if (fix is LocationFix.Update) {
                    val point = TrackPoint(
                        lat = fix.lat,
                        lng = fix.lng,
                        altitude = fix.altitude,
                        accuracyMeters = fix.accuracyMeters,
                        timestampEpochMillis = fix.timestampEpochMillis,
                    )
                    val active = uiState.value.activeTrack
                    // LocationSampler's own first-fix rule *is* the accuracy gate: with no
                    // lastAccepted it accepts exactly the fixes whose reported accuracy clears the
                    // mode's ceiling — reused rather than restated.
                    if (active != null && LocationSampler(active.mode).shouldAccept(lastAccepted = null, candidate = point)) {
                        // Dispatch 2026-09-28-527: only a GPS fix acts.
                        if (fix.provider.mayAct) {
                            lastGatedGpsFix = point
                            // Not for a recording that was taken up: its marker is the one it already
                            // has, or its first recorded point, never this fix. See settleTakenUpOrigin.
                            if (takenUpOrigin == null && uiState.value.originWaypoint == null && !originCreationInFlight) createOriginWaypoint(active, point)
                        }
                    }
                    returnToStart(point)
                }
            }
        }
    }

    /**
     * [namedAtEpochMillis] is the time in the marker's default name. For a marker made from the
     * first good fix it is now, which is when that fix arrived. For one made later from a track's
     * first recorded point it is that point's own time, so the name says when the walk started
     * and not when the app was reopened.
     */
    private fun createOriginWaypoint(active: ActiveTrack, fix: TrackPoint, namedAtEpochMillis: Long = currentTime.nowEpochMillis()) {
        originCreationInFlight = true
        viewModelScope.launch {
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
                    if (uiState.value.activeTrack?.trackId == active.trackId) {
                        _uiState.update { it.copy(originWaypoint = waypoint) }
                        handStartPointToWatch()
                    }
                    loadWaypoints()
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't save the track's origin waypoint.", error)
                    // Cleared so the next gated fix tries again rather than leaving the track
                    // origin-less for a transient write failure.
                    originCreationInFlight = false
                }
        }
    }

    fun loadWaypoints(): Job {
        return viewModelScope.launch {
            getWaypoints()
                .onSuccess { waypoints ->
                    _uiState.update { it.copy(waypoints = waypoints, waypointsErrorMessage = null, waypointsLoaded = true) }
                    // A handful of rows at most (see this list's own empty-state copy) — one query
                    // per waypoint is simpler than a batched read this table has no precedent for,
                    // and cheap at this scale. See TrackRecordingUiState.waypointEntryReferenceCounts'
                    // own doc comment for what this feeds.
                    val counts = waypoints.associate { it.id to getWaypointReferenceCount(it.id) }
                    _uiState.update { it.copy(waypointEntryReferenceCounts = counts) }
                }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't load waypoints.", error)
                    _uiState.update { it.copy(waypointsErrorMessage = "Couldn't load waypoints.") }
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
        return viewModelScope.launch {
            getTracks()
                .onSuccess { tracks ->
                    _uiState.update { it.copy(tracks = tracks.sortedByDescending(Track::startedAtEpochMillis), tracksErrorMessage = null) }
                    // One query per track, as the waypoints do (a handful of rows at most): what the Undo
                    // snackbar's "used in N journal entries" reads (Part 2 follow-ups F1 item 5).
                    val counts = tracks.associate { it.id to getTrackReferenceCount(it.id) }
                    _uiState.update { it.copy(trackEntryReferenceCounts = counts) }
                }
                .onFailure { error -> errorLog.w(TAG, "Couldn't load tracks.", error) }
        }
    }

    /**
     * The unfiltered read path for GPX export — see [TrackRepository.getFullRecord]'s own doc
     * comment. A plain passthrough, not cached in [uiState]: unlike [loadTracks], this is called
     * once per share tap, right before the exporter writes the file, not kept live.
     */
    suspend fun getFullRecord(trackId: String): Result<List<TrackPointRecord>> = trackRepository.getFullRecord(trackId)

    /**
     * The map's '+' > Waypoint > name dialog (MainActivity's `onDropWaypoint`), the one place a user adds a
     * waypoint. Dispatch -616 (plan T10): a waypoint dropped while a recording runs is linked to it by
     * `trackId`, the column the origin and end waypoints already fill, so Records lists it under that walk
     * and the walk's GPX carries it. "Runs" means what the screen shows, [TrackRecordingUiState.activeTrack],
     * read when the name is confirmed. Two consequences, recorded rather than designed around: a waypoint
     * dropped in the moment after the app reopens and before it has taken up the service's running
     * recording ([takeUpRunningRecording]) is standalone, and so is one whose recording stopped while its
     * name dialog was open. With no recording it is standalone, as before.
     */
    fun addWaypoint(lat: Double, lng: Double, name: String, note: String = "") {
        val trackId = uiState.value.activeTrack?.trackId
        viewModelScope.launch {
            createWaypoint(lat, lng, altitude = null, name = name, note = note, trackId = trackId)
                .onSuccess { loadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't save waypoint.", error)
                    _uiState.update { it.copy(waypointsErrorMessage = "Couldn't save waypoint.") }
                }
        }
    }

    /**
     * A swipe on a waypoint's Records row (journal redesign J4): the waypoint becomes pending —
     * hidden from [TrackRecordingUiState.visibleWaypoints] at once, still saved — and the Undo
     * snackbar shows. Its real delete ([deleteWaypoint], unchanged) runs from [commitRemoveWaypoint]
     * when the snackbar ends without Undo, from here when a second waypoint is swiped while this one
     * is still pending (the first is committed then), or from [onCleared].
     *
     * The reference count carried for the snackbar's warning is the one already loaded, read the
     * way the old confirm dialog read it: a waypoint missing from
     * [TrackRecordingUiState.waypointEntryReferenceCounts] counts as zero (that field's own doc
     * comment). An id not in the list is logged and pends nothing: there is no row for it.
     */
    fun requestRemoveWaypoint(id: String) {
        val state = _uiState.value
        val waypoint = state.waypoints.firstOrNull { it.id == id }
        if (waypoint == null) {
            errorLog.w(TAG, "A delete was asked for waypoint '$id', which is not loaded; nothing pended.", IllegalStateException("no waypoint '$id'"))
            return
        }
        val displaced = waypointDeletes.pend(waypoint, state.waypointEntryReferenceCounts[id] ?: 0)
        _uiState.update { it.copy(pendingWaypointDelete = waypointDeletes.pending) }
        displaced?.let(::commitWaypointDelete)
    }

    /** The snackbar's Undo: the pending waypoint shows again. Nothing was deleted, so nothing is restored. */
    fun undoRemoveWaypoint(id: String) {
        if (waypointDeletes.undo(id) == null) {
            errorLog.w(TAG, "Undo for waypoint '$id' came after its delete was committed; nothing to undo.", IllegalStateException("waypoint '$id' not pending"))
        }
        _uiState.update { it.copy(pendingWaypointDelete = waypointDeletes.pending) }
    }

    /** The snackbar ended without Undo (timed out, or a newer snackbar replaced it): the pending waypoint's delete runs, once. */
    fun commitRemoveWaypoint(id: String) {
        val waypoint = waypointDeletes.commit(id)
        _uiState.update { it.copy(pendingWaypointDelete = waypointDeletes.pending) }
        waypoint?.let(::commitWaypointDelete)
    }

    /**
     * The real delete of a waypoint whose pending time is over. It leaves [TrackRecordingUiState.waypoints]
     * at once rather than when the reload lands, so it does not flash back onto the screen between
     * the snackbar closing and the delete finishing. A failed delete puts it back where it was and
     * reports the failure the way [removeWaypoint] does.
     */
    private fun commitWaypointDelete(waypoint: Waypoint) {
        val index = _uiState.value.waypoints.indexOfFirst { it.id == waypoint.id }
        _uiState.update { state -> state.copy(waypoints = state.waypoints.filterNot { it.id == waypoint.id }) }
        viewModelScope.launch {
            deleteWaypoint(waypoint.id)
                .onSuccess { loadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete waypoint.", error)
                    _uiState.update { state ->
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
     * while this one is pending (the first is committed then), or from [onCleared].
     *
     * **Never a track that is recording.** A track whose end time is null, or the one this ViewModel is
     * recording into, is refused and logged, so a caller that offers Delete on it by mistake still cannot
     * delete a recording. An id not in the list is logged and pends nothing.
     */
    fun requestRemoveTrack(id: String) {
        val state = _uiState.value
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
        _uiState.update { it.copy(pendingTrackDelete = trackDeletes.pending) }
        displaced?.let(::commitTrackDelete)
    }

    /** The snackbar's Undo: the pending track shows again. Nothing was deleted, so nothing is restored. */
    fun undoRemoveTrack(id: String) {
        if (trackDeletes.undo(id) == null) {
            errorLog.w(TAG, "Undo for track '$id' came after its delete was committed; nothing to undo.", IllegalStateException("track '$id' not pending"))
        }
        _uiState.update { it.copy(pendingTrackDelete = trackDeletes.pending) }
    }

    /** The snackbar ended without Undo (timed out, or a newer one replaced it): the pending track's delete runs, once. */
    fun commitRemoveTrack(id: String) {
        val track = trackDeletes.commit(id)
        _uiState.update { it.copy(pendingTrackDelete = trackDeletes.pending) }
        track?.let(::commitTrackDelete)
    }

    /**
     * The real delete of a track whose pending time is over. It leaves [TrackRecordingUiState.tracks] at once
     * rather than when the reload lands, so it does not flash back between the snackbar closing and the delete
     * finishing. A failed delete puts it back where it was and reports the failure
     * ([TrackRecordingUiState.tracksErrorMessage]), never as if it had worked.
     */
    private fun commitTrackDelete(track: Track) {
        val index = _uiState.value.tracks.indexOfFirst { it.id == track.id }
        _uiState.update { state -> state.copy(tracks = state.tracks.filterNot { it.id == track.id }) }
        viewModelScope.launch {
            deleteTrack(track.id)
                .onSuccess { loadTracks() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete track.", error)
                    _uiState.update { state ->
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

    fun removeWaypoint(id: String) {
        viewModelScope.launch {
            deleteWaypoint(id)
                .onSuccess { loadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't delete waypoint.", error)
                    _uiState.update { it.copy(waypointsErrorMessage = "Couldn't delete waypoint.") }
                }
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
        _uiState.update { it.copy(returnToStart = info) }
        return info
    }

    private fun Waypoint.asStartPoint() = TrackPoint(
        lat = lat,
        lng = lng,
        altitude = altitude,
        accuracyMeters = null,
        timestampEpochMillis = createdAtEpochMillis,
    )

    override fun onCleared() {
        pollingJob?.cancel()
        locationJob?.cancel()
        // Journal redesign J4: a waypoint still pending is committed here, since no snackbar is left
        // to end. viewModelScope is already cancelled, so the delete runs on pendingDeleteCommitScope.
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

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L

        /** Decision D4: the route is recomputed every 5 s while returning. */
        const val ROUTE_TICK_MILLIS = 5_000L
        const val TAG = "TrackRecordingViewModel"
    }
}

/** The owner's words (RECORD -660, item 4): the service was refused the foreground, so nothing is recording. */
internal const val RECORDING_COULD_NOT_START_MESSAGE = "Recording couldn't start. Open Forager and tap Record again."

/**
 * The permission case (RECORD -660, item 4): the service has no location permission, at the start or
 * lost during the walk. The planner's wording, pointing to the permission; the owner: "Use it as written" (RECORD -661).
 */
internal const val RECORDING_NEEDS_LOCATION_PERMISSION_MESSAGE =
    "Recording stopped because Forager can't use your location. Allow location for Forager in your phone's Settings, then tap Record again."

/**
 * How long the navigation display holds "Arrived" after a return ends on arrival, before it closes by
 * itself (RECORD -845). The owner chose "Arrived for a few seconds, then closes (Recommended)" over
 * "Arrived stays until closed" and "closes at once with a short notice"; the ruling records it as
 * "about 10 seconds": long enough to glance down at a phone and read it, short enough that the plain
 * map is back before the walker is driving. A held reading, not a live one, so a drive-off inside the
 * hold does not turn it back into a distance.
 */
const val ARRIVAL_HOLD_MILLIS = 10_000L
