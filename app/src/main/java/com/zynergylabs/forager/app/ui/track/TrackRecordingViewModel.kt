package com.zynergylabs.forager.app.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zynergylabs.forager.app.domain.AbandonedTrackSweepOnce
import com.zynergylabs.forager.app.domain.AlertAudibility
import com.zynergylabs.forager.app.domain.ComputeReturnToStartUseCase
import com.zynergylabs.forager.app.domain.EntryReferenceCountSource
import com.zynergylabs.forager.app.domain.NoEntryReferenceCounts
import com.zynergylabs.forager.app.domain.alertAudibilityWarning
import com.zynergylabs.forager.app.domain.BACKGROUND_RUN_PROMPT
import com.zynergylabs.forager.app.domain.CreateWaypointUseCase
import com.zynergylabs.forager.app.domain.BackByChoice
import com.zynergylabs.forager.app.domain.BackByControl
import com.zynergylabs.forager.app.domain.NoBackBy
import com.zynergylabs.forager.app.domain.SundownShown
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeleteTrackUseCase
import com.zynergylabs.forager.app.domain.DeleteWaypointUseCase
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GetTrackOriginWaypointUseCase
import com.zynergylabs.forager.app.domain.GetTracksUseCase
import com.zynergylabs.forager.app.domain.GetWaypointsUseCase
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.KeptTail
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.routeHome
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.StartTrackUseCase
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.domain.TrackRepository
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.domain.RecordingHalt
import com.zynergylabs.forager.app.domain.RecordingHaltReason
import com.zynergylabs.forager.app.domain.ReturnWatch
import com.zynergylabs.forager.app.domain.ReturnWatchState
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import com.zynergylabs.forager.app.ui.log.PendingDeleteCommitScope
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * Recorded on [TrackRecordingPoller], which holds the poll (UI split Phase 2.7, PR 4).
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
 * Recorded on [TrackRecordingLocationFixes], which holds that collection since the split before the way home
 * (dispatch 2026-10-23-09 Amendment 1, part 0).
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
     * How many journal entries keep each waypoint and each track, live (dispatch 2026-10-28-08): the waypoint's
     * "Used in" and both Undo snackbars' warnings. Defaults to no counts, every one reading 0, as before.
     */
    private val entryReferenceCounts: EntryReferenceCountSource = NoEntryReferenceCounts,
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
    /**
     * Dispatch 2026-10-21-02 (RECORD -1521): the id of the last track whose end the recording service has
     * stored, `AppContainer.trackEnds` in production. This screen reloads its track list on each one, so
     * a finished track is in Records at once, and, for its own recording, shows not recording at once
     * (dispatch 2026-10-21-04: see [onTrackEnded]). Defaults to a service that never reports.
     */
    private val trackEnds: StateFlow<String?> = MutableStateFlow(null),
    /** The app's way home along trails (dispatch 2026-10-24-10, RECORD -1613): found at Return, stopped with it. `null`: none. */
    private val wayHomeWatch: WayHomeWatch? = null,
    /** The service's newest kept points, for the line to reach the dot (dispatch 2026-10-26-05). `null`: the stored track only. */
    private val recordingKeptTail: StateFlow<KeptTail>? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackRecordingUiState())
    val uiState: StateFlow<TrackRecordingUiState> = _uiState.asStateFlow()

    /**
     * The breadcrumb poll, with the track it last read and the network-fixes flag, moved to its own class by
     * UI split Phase 2.7 PR 4. It launches in this ViewModel's own scope; [startRecording],
     * [takeUpRunningRecording], [startReturn], [clearRecordingState] and [onCleared] start and stop it,
     * synchronously, as they did.
     */
    private val poller = TrackRecordingPoller(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        trackRepository = trackRepository,
        settleTakenUpOrigin = { active, track -> takeUp.settleTakenUpOrigin(active, track) },
        handStartPointToWatch = { handStartPointToWatch() },
        nextNoticeId = { ++recordingNoticeIds },
        keptTail = recordingKeptTail,
    )

    /**
     * The route tick, running only while returning, moved to its own class by UI split Phase 2.7 PR 3. It
     * launches in this ViewModel's own scope; [copyFromWatch], [stopReturn] and [clearRecordingState] start
     * and stop it, synchronously, as they did.
     */
    private val routeTicks = TrackRecordingRouteTicks(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        lastPolledTrack = { poller.lastPolledTrack },
        lastGatedGpsFix = { locationFixes.lastGatedGpsFix },
        findRouteHome = findRouteHome,
        wayHomeProgress = { trackId -> wayHomeWatch?.progressFor(trackId) },
    )

    /**
     * The waypoint and track lists, moved to their own class by UI split Phase 2.7 PR 2. It reads and
     * writes this screen's state through the getter and setter here and launches in this ViewModel's own scope.
     */
    private val lists = TrackRecordingLists(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        getWaypoints = getWaypoints,
        getTracks = getTracks,
        createWaypoint = createWaypoint,
        deleteWaypoint = deleteWaypoint,
        entryReferenceCounts = entryReferenceCounts,
        abandonedTrackSweepOnce = abandonedTrackSweepOnce,
        errorLog = errorLog,
    )

    /**
     * The one waypoint and the one track whose delete is pending (journal redesign J4; Part 2 follow-ups F1
     * item 5), moved to their own class by UI split Phase 2.7 PR 1. It reads and writes this screen's state
     * through the getter and setter here and launches in this ViewModel's own scope.
     */
    private val pendingDeletes = TrackRecordingPendingDeletes(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        pendingDeleteCommitScope = pendingDeleteCommitScope,
        deleteWaypoint = deleteWaypoint,
        deleteTrack = deleteTrack,
        errorLog = errorLog,
        reloadWaypoints = { loadWaypoints() },
        reloadTracks = { loadTracks() },
    )
    // originCreationInFlight stops a second fix arriving during the origin's own async save from creating a
    // second origin. The last gated GPS fix it sits beside is [TrackRecordingLocationFixes.lastGatedGpsFix].
    private var originCreationInFlight = false
    private var recordingNoticeIds = 0

    /**
     * The take-up memory (the recording this screen stopped, and how a taken-up recording's start marker is
     * settled), moved to its own class by UI split Phase 2.7 PR 5. Take-up itself, [takeUpRunningRecording]
     * with [takeUpInFlight], stays here, and so does every start and stop of this ViewModel's jobs.
     */
    private val takeUp = TrackRecordingTakeUp(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        getTrackOriginWaypoint = getTrackOriginWaypoint,
        errorLog = errorLog,
        handStartPointToWatch = { handStartPointToWatch() },
        originCreationInFlight = { originCreationInFlight },
        createOriginWaypoint = { active, fix, namedAtEpochMillis -> createOriginWaypoint(active, fix, namedAtEpochMillis) },
    )

    private var takeUpInFlight = false

    /** Back by, the watch copies and the arrival hold's timer (nav step 2, PR 2): see [TrackRecordingWatchCopies]. */
    private val watchCopies = TrackRecordingWatchCopies(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        sundownShown = sundownShown,
        backBy = backBy,
        errorLog = errorLog,
        currentTime = currentTime,
        zone = zone,
        wayHomeShown = wayHomeWatch?.shown,
    )

    /** The origin and end waypoints and the start point (nav step 2, PR 2): see [TrackRecordingAutoWaypoints]. */
    private val autoWaypoints = TrackRecordingAutoWaypoints(
        uiState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        scope = { viewModelScope },
        trackRepository = trackRepository,
        createWaypoint = createWaypoint,
        computeReturnToStart = computeReturnToStart,
        returnWatch = returnWatch,
        errorLog = errorLog,
        currentTime = currentTime,
        zone = zone,
        is24HourClock = is24HourClock,
        setOriginCreationInFlight = { originCreationInFlight = it },
        loadWaypoints = { loadWaypoints() },
    )

    /** The live-fix collection and the last gated GPS fix (dispatch 2026-10-23-09, part 0): see [TrackRecordingLocationFixes]. */
    private val locationFixes = TrackRecordingLocationFixes(
        uiState = { _uiState.value },
        scope = { viewModelScope },
        locationTracker = locationTracker,
        isTakenUp = { takeUp.isTakenUp },
        originCreationInFlight = { originCreationInFlight },
        createOriginWaypoint = { active, fix -> createOriginWaypoint(active, fix) },
        returnToStart = { point -> returnToStart(point) },
    )

    /** The reads of the track's own row for take-up and the resync (dispatch 2026-10-23-09, part 0): see [TrackRecordingRowReads]. */
    private val rowReads = TrackRecordingRowReads(uiState = { _uiState.value }, trackRepository = trackRepository, errorLog = errorLog)

    init {
        lists.watchReferenceCounts()
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
        // The way home, the same way; a new one swaps in at once, not at the next route tick (dispatch 2026-10-24-10).
        wayHomeWatch?.let { watch -> viewModelScope.launch { watch.shown.collect { watchCopies.copyWayHome(); routeTicks.updateRouteHome() } } }
        viewModelScope.launch { recordingHalts.collect { halt -> if (halt != null) onRecordingHalted(halt) } }
        // A track's end is stored by the service after this screen's Stop has returned, so the list is
        // reloaded on the service's report of that write, not on Stop (dispatch 2026-10-21-02, TrackEnds);
        // and a recording the service ended without this screen is shown as ended (dispatch 2026-10-21-04).
        viewModelScope.launch { trackEnds.collect { endedTrackId -> if (endedTrackId != null) onTrackEnded(endedTrackId) } }
    }

    /** The quick menu's "+1 h", "+2 h", "+3 h" or a picked time: see [TrackRecordingWatchCopies.setBackBy]. */
    fun setBackBy(choice: BackByChoice) = watchCopies.setBackBy(choice)

    /** The quick menu's "Clear": see [TrackRecordingWatchCopies.clearBackBy]. */
    fun clearBackBy() = watchCopies.clearBackBy()

    /** See [TrackRecordingWatchCopies.copyBackBy]. */
    private fun copyBackBy() = watchCopies.copyBackBy()

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
     * The service stored the end of [endedTrackId] (dispatch 2026-10-21-02; [TrackEnds]). A reload of the
     * track list is started. If it is this screen's recording, the screen also resynchronises against the
     * row, as on a resume ([resyncRecordingState]).
     *
     * **The defect this closes (dispatch 2026-10-21-04, RECORD -1527).** Stop from the notification reaches the
     * service and never this ViewModel, and the resync ran only on ON_START and ON_STOP. Pulling the shade
     * back up fires neither, so the map went on showing a recording, and Delete refused the track as still
     * recording ([TrackRecordingPendingDeletes.requestRemoveTrack]), until the app went to the background.
     *
     * **Why the resync and not [stopRecording].** The same reason [resyncRecordingState] gives: this inherits
     * the identical cleared state by construction, without [stopRecording]'s end waypoint, whose last fix is
     * not where the walker pressed Stop. A track stopped from the shade still has no end waypoint, the gap
     * already recorded there. The screen's own Stop has cleared [TrackRecordingUiState.activeTrack] before the
     * report arrives, and a halt clears it through [onRecordingHalted] first, so both are untouched by this.
     */
    private suspend fun onTrackEnded(endedTrackId: String) {
        loadTracks()
        if (uiState.value.activeTrack?.trackId == endedTrackId) resyncRecordingState()
    }

    /** The process's one sweep of tracks an earlier process left open: see [TrackRecordingLists.sweepAbandonedTracks]. */
    private suspend fun sweepAbandonedTracks() = lists.sweepAbandonedTracks()

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
        watchCopies.copyWayHome()
        // The route tick runs exactly while this screen shows a return, whichever way the return
        // reached it: this screen's own Return, or a recording taken up mid-return.
        if (uiState.value.isReturning) {
            if (!routeTicks.isRunning) routeTicks.begin()
        } else if (routeTicks.isRunning) {
            routeTicks.end()
        }
    }

    /** See [TrackRecordingWatchCopies.copySundownLine]. */
    private fun copySundownLine() = watchCopies.copySundownLine()

    /** See [TrackRecordingAutoWaypoints.handStartPointToWatch]. */
    private fun handStartPointToWatch() = autoWaypoints.handStartPointToWatch()

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
        if (running.isBegun && running.trackId != uiState.value.activeTrack?.trackId && !takeUp.isOwnStoppedRecording(running)) {
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
                    locationFixes.lastGatedGpsFix = null
                    poller.resetForNewRecording()
                    originCreationInFlight = false
                    takeUp.clear()
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
                    poller.begin(track.id)
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
     * Cancelling the location collection is what releases the platform location listener: see
     * [TrackRecordingLocationFixes.cancel].
     */
    private fun clearRecordingState() {
        endArrivalHold()
        poller.cancel()
        routeTicks.cancel()
        poller.resetPolledTrack()
        locationFixes.cancel()
        // This screen's return is over with its recording. The service ends the watch itself when
        // it stops; this covers the moment before it has, and a recording the service never began.
        uiState.value.activeTrack?.let {
            returnWatch.stopReturn(it.trackId)
            wayHomeWatch?.stop(it.trackId)
            takeUp.rememberStopped(it.trackId)
        }
        locationFixes.lastGatedGpsFix = null
        originCreationInFlight = false
        takeUp.clear()
        _uiState.update {
            it.copy(activeTrack = null, isReturning = false, isOffTrack = false, returnToStart = null, originWaypoint = null, routeHome = null, routeLine = null, wayHome = null, sundownLine = null, backBy = null, breadcrumbPoints = emptyList())
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
     * as for any recording; the start marker ([TrackRecordingTakeUp.settleTakenUpOrigin]). This ViewModel's own poll
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
     * **A row that cannot be read takes up nothing** and is logged: see [TrackRecordingRowReads.trackToTakeUp].
     */
    private suspend fun takeUpRunningRecording() {
        if (uiState.value.activeTrack != null || takeUpInFlight) return
        val running = returnWatch.state.value
        if (!running.isBegun || takeUp.isOwnStoppedRecording(running)) return
        val trackId = running.trackId ?: return
        val mode = running.mode ?: return
        takeUpInFlight = true
        try {
            val track = rowReads.trackToTakeUp(trackId) ?: return
            // The read suspended. Go by what is true now, not by what was true before it.
            val stillRunning = returnWatch.state.value
            if (uiState.value.activeTrack != null || !stillRunning.isBegun || stillRunning.trackId != trackId) return
            if (takeUp.isOwnStoppedRecording(stillRunning)) return

            val active = ActiveTrack(trackId, track.startedAtEpochMillis, mode)
            locationFixes.lastGatedGpsFix = null
            poller.resetPolledTrack()
            originCreationInFlight = false
            takeUp.markNotLookedUp()
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
            takeUp.settleTakenUpOrigin(active, track)
            poller.begin(trackId)
            beginLocationTracking()
        } finally {
            takeUpInFlight = false
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
        val endFix = locationFixes.lastGatedGpsFix
        clearRecordingState()
        if (endingTrack != null && endFix != null) {
            autoWaypoints.saveEndWaypoint(endingTrack, endFix)
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
     * the location collection keeps a platform location listener registered with no foreground service and no
     * notification behind it. Resyncing here closes that, because backgrounding is the one thing
     * that reliably happens after a stop from the shade.
     *
     * **Why this and not the foreground gate `AvailabilityViewModel` uses,** and what that leaves open: recorded on
     * [TrackRecordingLocationFixes], "Why the collection keeps running with the screen off".
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
     * waypoint, which is seeded from [TrackRecordingLocationFixes.lastGatedGpsFix] — the last GPS fix that cleared the gate, which by
     * the time this runs is where the walker was when they backgrounded the app, not where they
     * stopped recording. Writing a waypoint there would be a data write from a stale position onto
     * an already-ended track, which is the class of thing this whole change is about not doing.
     * A track stopped from the shade therefore still has no end waypoint; that is unchanged by
     * this, and recorded as a known gap rather than fixed here on a position nobody measured.
     *
     * **A read failure clears nothing** and is logged; **a missing row clears, in the same branch as an ended
     * one.** Both are [TrackRecordingRowReads.rowSaysEnded]'s, where the reasons are recorded.
     *
     * Cheap, and a no-op when nothing is recording: it returns before touching storage unless
     * [TrackRecordingUiState.activeTrack] is set. A background-and-return **during** a live
     * recording reads one row, finds `endedAtEpochMillis` null, and changes nothing.
     */
    private suspend fun resyncRecordingState() {
        val active = uiState.value.activeTrack ?: return
        if (rowReads.rowSaysEnded(active)) clearRecordingState()
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
        // Dispatch 2026-10-24-10: the way home along trails is worked out now; the track shows until it swaps in.
        wayHomeWatch?.find(active.trackId, uiState.value.originWaypoint)
        // Restarted so the track the route tick routes along is read now, not up to 15 s from now.
        // Restarting also re-reads the breadcrumbs, which is harmless, and the network-fixes notice
        // is guarded by its own once-per-recording flag. Launched before copyFromWatch starts the
        // route tick, so on the same dispatcher the poll's read runs first and the first route uses it.
        poller.begin(active.trackId)
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

    /** See [TrackRecordingWatchCopies.holdArrival]. */
    private fun holdArrival() = watchCopies.holdArrival()

    /** See [TrackRecordingWatchCopies.endArrivalHold]. */
    private fun endArrivalHold() = watchCopies.endArrivalHold()

    /**
     * Clears returning/off-track state without touching the recording itself — see [startReturn]. Also
     * the way to close the arrival hold early (RECORD -845): the display's exit while it holds "Arrived".
     */
    fun stopReturn() {
        endArrivalHold()
        uiState.value.activeTrack?.let { returnWatch.stopReturn(it.trackId) }
        uiState.value.activeTrack?.let { wayHomeWatch?.stop(it.trackId) }
        _uiState.update { it.copy(isReturning = false, isOffTrack = false) }
        routeTicks.end()
    }

    /**
     * The HUD's "Try again" (dispatch 2026-09-28-423): the route recomputed now instead of at the
     * next tick. It cannot make data appear; the HUD offers it only where the inputs can change
     * (off the route), never for a track with no usable points.
     */
    fun retryRoute() {
        routeTicks.updateRouteHome()
    }

    /**
     * Starts the live-fix collection that feeds [returnToStart] and seeds the origin waypoint: see
     * [TrackRecordingLocationFixes.begin]. The decision to start it stays here (RECORD -1450).
     */
    private fun beginLocationTracking() = locationFixes.begin()

    /** Saves the origin waypoint: see [TrackRecordingAutoWaypoints.createOriginWaypoint]. */
    private fun createOriginWaypoint(active: ActiveTrack, fix: TrackPoint, namedAtEpochMillis: Long = currentTime.nowEpochMillis()) =
        autoWaypoints.createOriginWaypoint(active, fix, namedAtEpochMillis)

    /** Reads the waypoints and their reference counts: see [TrackRecordingLists.loadWaypoints]. */
    fun loadWaypoints(): Job = lists.loadWaypoints()

    /** Reads the tracks and their reference counts: see [TrackRecordingLists.loadTracks]. */
    fun loadTracks(): Job = lists.loadTracks()

    /**
     * The unfiltered read path for GPX export — see [TrackRepository.getFullRecord]'s own doc
     * comment. A plain passthrough, not cached in [uiState]: unlike [loadTracks], this is called
     * once per share tap, right before the exporter writes the file, not kept live.
     */
    suspend fun getFullRecord(trackId: String): Result<List<TrackPointRecord>> = trackRepository.getFullRecord(trackId)

    /** The map's '+' > Waypoint > name dialog: see [TrackRecordingLists.addWaypoint]. */
    fun addWaypoint(lat: Double, lng: Double, name: String, note: String = "") = lists.addWaypoint(lat, lng, name, note)

    /** A swipe on a waypoint's Records row: see [TrackRecordingPendingDeletes.requestRemoveWaypoint]. */
    fun requestRemoveWaypoint(id: String) = pendingDeletes.requestRemoveWaypoint(id)

    /** The snackbar's Undo: see [TrackRecordingPendingDeletes.undoRemoveWaypoint]. */
    fun undoRemoveWaypoint(id: String) = pendingDeletes.undoRemoveWaypoint(id)

    /** The snackbar ended without Undo: see [TrackRecordingPendingDeletes.commitRemoveWaypoint]. */
    fun commitRemoveWaypoint(id: String) = pendingDeletes.commitRemoveWaypoint(id)

    /** A swipe on a finished track's Records row, or its Delete: see [TrackRecordingPendingDeletes.requestRemoveTrack]. */
    fun requestRemoveTrack(id: String) = pendingDeletes.requestRemoveTrack(id)

    /** The snackbar's Undo: see [TrackRecordingPendingDeletes.undoRemoveTrack]. */
    fun undoRemoveTrack(id: String) = pendingDeletes.undoRemoveTrack(id)

    /** The snackbar ended without Undo: see [TrackRecordingPendingDeletes.commitRemoveTrack]. */
    fun commitRemoveTrack(id: String) = pendingDeletes.commitRemoveTrack(id)

    /** Deletes a waypoint at once, with no Undo: see [TrackRecordingLists.removeWaypoint]. */
    fun removeWaypoint(id: String) = lists.removeWaypoint(id)

    /** The Return button's distance, on every fix the collector gets and from tests: see [TrackRecordingAutoWaypoints.returnToStart]. */
    fun returnToStart(current: TrackPoint): ReturnToStartInfo? = autoWaypoints.returnToStart(current)

    override fun onCleared() {
        poller.cancelOnCleared()
        locationFixes.cancel()
        // Journal redesign J4: a waypoint or track still pending is committed there, since no snackbar is
        // left to end. viewModelScope is already cancelled, so the delete runs on pendingDeleteCommitScope.
        pendingDeletes.commitOnCleared()
    }

    internal companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L

        /** Decision D4: the route is recomputed every 5 s while returning. */
        const val ROUTE_TICK_MILLIS = 5_000L
        const val TAG = "TrackRecordingViewModel"
    }
}
