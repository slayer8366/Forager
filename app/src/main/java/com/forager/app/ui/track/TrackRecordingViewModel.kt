package com.forager.app.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.ErrorLog
import com.forager.app.domain.GetTracksUseCase
import com.forager.app.domain.GetWaypointsUseCase
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.StartTrackUseCase
import com.forager.app.domain.SystemCurrentTimeProvider
import com.forager.app.domain.TrackRepository
import com.forager.app.domain.model.ReturnToStartInfo
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.domain.model.TrackRecordingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns track-recording and waypoint UI state — kept in its own package/ViewModel rather than
 * folded into [com.forager.app.ui.availability.AvailabilityViewModel], the same reasoning
 * `LogPanel`'s doc comment gives for the mushroom log's own `MushroomLogViewModel`. Unlike the log,
 * this doesn't get a separate screen: breadcrumbs and waypoint markers are overlays on the same map
 * [com.forager.app.ui.availability.AvailabilityScreen] already renders, threaded in as state the
 * same way `compassProvider`/`locateMeStatus` already are — a genuinely separate *ViewModel*, not a
 * separate *destination*.
 *
 * ## Why breadcrumbs are polled, not reactive
 *
 * [TrackRepository] is plain suspend calls, matching every other Room-backed repository in this
 * app — there is no `Flow<Track>` to collect. [com.forager.app.service.TrackRecordingService]
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
 * it — the "resumable/closeable state" [com.forager.app.service.TrackRecordingService]'s own doc
 * comment already flags as the UI's responsibility, deliberately not built here to keep this pass
 * scoped to starting, stopping, and showing a recording that's actually running.
 *
 * ## Why return-to-start is fed by [locationTracker], not a one-shot fetch
 *
 * The first pass of the return-to-vehicle screen recomputed [ReturnToStartInfo] from
 * `AvailabilityViewModel`'s `locateMeStatus` — a one-shot "where am I right now" fetch, refreshed
 * only when the user taps the locate-me icon. That made the bearing/distance shown stale between
 * taps, and made [DetectOffTrackUseCase] unworkable — it needs a running series of readings, not
 * one. This ViewModel now collects [LocationTracker.fixes] itself, the same continuous stream
 * [com.forager.app.service.TrackRecordingService] collects for the track's own points, whenever a
 * recording is active — so [TrackRecordingUiState.returnToStart] and [TrackRecordingUiState.isOffTrack]
 * update on every fix, not just on demand. This does mean two independent OS location-listener
 * registrations while recording (the service's and this one) rather than one shared stream — a
 * real, accepted duplication, not a hidden one, in exchange for not re-plumbing the service to
 * publish its fixes back out to the UI layer for this one reader.
 */
class TrackRecordingViewModel(
    private val trackRepository: TrackRepository,
    private val startTrack: StartTrackUseCase,
    private val getWaypoints: GetWaypointsUseCase,
    private val createWaypoint: CreateWaypointUseCase,
    private val deleteWaypoint: DeleteWaypointUseCase,
    private val computeReturnToStart: ComputeReturnToStartUseCase,
    private val detectOffTrack: DetectOffTrackUseCase,
    private val locationTracker: LocationTracker,
    private val getTracks: GetTracksUseCase,
    /**
     * Logs a failure's throwable for diagnosis, without ever exposing its text to the user — see
     * [ErrorLog]'s own doc comment for why this exists rather than calling [android.util.Log]
     * directly. Defaults to discarding the throwable, which is exactly what makes every existing
     * test safe under a plain JVM run with no per-test setup; `MainActivity` wires the real
     * `Log.w`-backed one for production.
     */
    private val errorLog: ErrorLog = ErrorLog { _, _, _ -> },
    /** Injected so a test can fix the off-track alert cooldown's clock — see [returnToStart]'s own doc comment. */
    private val currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackRecordingUiState())
    val uiState: StateFlow<TrackRecordingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var locationJob: Job? = null

    // Oldest first, cleared on every startReturn()/stopReturn()/stopRecording() — see
    // returnToStart()'s doc comment for why this lives here rather than in TrackRecordingUiState
    // itself (it's tracking history feeding a decision, not state the UI reads directly).
    private val recentReturnDistancesMeters = mutableListOf<Double>()

    // When the last off-track alert fired, for returnToStart()'s own cooldown — cleared alongside
    // recentReturnDistancesMeters for the same reason: a new return attempt should never be blocked
    // by a cooldown left over from a much earlier one.
    private var lastOffTrackAlertAtMillis: Long? = null

    init {
        loadWaypoints()
        loadTracks()
    }

    /**
     * Creates and persists the [com.forager.app.domain.model.Track] row the recording will write
     * into. Starting the actual foreground service (an Android-layer action, needing a `Context`
     * this ViewModel doesn't hold) is the caller's job once [TrackRecordingUiState.activeTrack]
     * becomes non-null — see `MainActivity`'s `LaunchedEffect` on this state.
     */
    fun startRecording(mode: TrackRecordingMode = TrackRecordingMode.BALANCED) {
        viewModelScope.launch {
            startTrack(null)
                .onSuccess { track ->
                    _uiState.update {
                        it.copy(
                            activeTrack = ActiveTrack(track.id, track.startedAtEpochMillis, mode),
                            startRecordingErrorMessage = null,
                            breadcrumbPoints = emptyList(),
                        )
                    }
                    beginPolling(track.id)
                    beginLocationTracking()
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
     * [com.forager.app.ui.availability.AvailabilityViewModel.onLocateMePermissionDenied]: the
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
     * Clears local recording state. Ending the track's own row (`endedAtEpochMillis`) is the
     * foreground service's job once it receives the stop intent — see
     * [com.forager.app.service.TrackRecordingService.stopRecording] — not duplicated here.
     */
    fun stopRecording() {
        pollingJob?.cancel()
        pollingJob = null
        locationJob?.cancel()
        locationJob = null
        recentReturnDistancesMeters.clear()
        lastOffTrackAlertAtMillis = null
        _uiState.update { it.copy(activeTrack = null, isReturning = false, isOffTrack = false, returnToStart = null) }
    }

    /**
     * Marks the walker as now heading back to the track's start — the only state
     * [DetectOffTrackUseCase] runs against. Outbound travel away from the start isn't "off track"
     * by any definition available here (there's no planned route to deviate from, only the trail
     * being made right now), so the heuristic would be meaningless, and noisy, applied to it.
     * A no-op while nothing is recording — there is nothing to return to yet.
     */
    fun startReturn() {
        if (!uiState.value.isRecording) return
        recentReturnDistancesMeters.clear()
        _uiState.update { it.copy(isReturning = true, isOffTrack = false) }
    }

    /** Clears returning/off-track state without touching the recording itself — see [startReturn]. */
    fun stopReturn() {
        recentReturnDistancesMeters.clear()
        lastOffTrackAlertAtMillis = null
        _uiState.update { it.copy(isReturning = false, isOffTrack = false) }
    }

    private fun beginPolling(trackId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                trackRepository.getById(trackId).onSuccess { track ->
                    _uiState.update { it.copy(breadcrumbPoints = track?.points.orEmpty()) }
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    /** See this class's own doc comment for why [returnToStart] is driven from here rather than a one-shot fetch. */
    private fun beginLocationTracking() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationTracker.fixes.collect { fix ->
                if (fix is LocationFix.Update) {
                    returnToStart(
                        TrackPoint(
                            lat = fix.lat,
                            lng = fix.lng,
                            altitude = fix.altitude,
                            accuracyMeters = fix.accuracyMeters,
                            timestampEpochMillis = fix.timestampEpochMillis,
                        ),
                    )
                }
            }
        }
    }

    fun loadWaypoints() {
        viewModelScope.launch {
            getWaypoints()
                .onSuccess { waypoints -> _uiState.update { it.copy(waypoints = waypoints, waypointsErrorMessage = null) } }
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
    fun loadTracks() {
        viewModelScope.launch {
            getTracks()
                .onSuccess { tracks ->
                    _uiState.update { it.copy(tracks = tracks.sortedByDescending(Track::startedAtEpochMillis)) }
                }
                .onFailure { error -> errorLog.w(TAG, "Couldn't load tracks.", error) }
        }
    }

    fun addWaypoint(lat: Double, lng: Double, name: String, note: String = "") {
        viewModelScope.launch {
            createWaypoint(lat, lng, altitude = null, name = name, note = note)
                .onSuccess { loadWaypoints() }
                .onFailure { error ->
                    errorLog.w(TAG, "Couldn't save waypoint.", error)
                    _uiState.update { it.copy(waypointsErrorMessage = "Couldn't save waypoint.") }
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
     * [ReturnToStartInfo] from [current] back to the active track's first recorded point, or
     * `null` if either is unavailable. Called on every fix [beginLocationTracking] collects, and
     * also directly by tests — its result is written to [TrackRecordingUiState.returnToStart]
     * either way, so a direct call and a collected fix behave identically. While
     * [TrackRecordingUiState.isReturning], this doubles as the off-track heuristic's own data feed:
     * each call's distance joins [recentReturnDistancesMeters], and [DetectOffTrackUseCase] re-runs
     * against the updated history, updating [TrackRecordingUiState.isOffTrack]. Not fed at all while
     * not returning, so the history only ever reflects an actual return attempt, never outbound
     * travel.
     *
     * **Field-test dispatch item 4.** [DetectOffTrackUseCase]'s own output used to reach a user
     * nowhere but an icon tint (see [com.forager.app.ui.availability.AvailabilityScreen]'s
     * `MapIconBar`) — nothing a forager with the phone pocketed on the return leg, exactly the body
     * state this alert exists for, could ever perceive. Every call where the heuristic reads `true`
     * bumps [TrackRecordingUiState.offTrackAlertId] — which `MainActivity` observes to post a
     * notification and vibrate — but **only** once [OFF_TRACK_ALERT_COOLDOWN_MILLIS] has passed
     * since the last one: this method runs on every live fix while returning (as often as every few
     * seconds — see [com.forager.app.domain.model.TrackRecordingMode]), and a heuristic that stays
     * `true` for a sustained drift would otherwise re-fire on every single one of those, buzzing a
     * wandering forager continuously rather than reminding them periodically. Deliberately **not**
     * edge-triggered (alert only on the false→true transition): a real, sustained drift should keep
     * reminding every cooldown window for as long as it lasts, not go silent after the first buzz —
     * see [DetectOffTrackUseCase]'s own doc comment on why the heuristic itself is left exactly as
     * it was; only where its output goes is new here.
     */
    fun returnToStart(current: TrackPoint): ReturnToStartInfo? {
        val start = uiState.value.breadcrumbPoints.firstOrNull() ?: return null
        val info = computeReturnToStart(current, start)
        if (uiState.value.isReturning) {
            recentReturnDistancesMeters += info.distanceMeters
            val isOffTrackNow = detectOffTrack(recentReturnDistancesMeters)
            val shouldAlert = isOffTrackNow && canFireOffTrackAlert()
            if (shouldAlert) lastOffTrackAlertAtMillis = currentTime.nowEpochMillis()
            _uiState.update {
                it.copy(
                    returnToStart = info,
                    isOffTrack = isOffTrackNow,
                    offTrackAlertId = if (shouldAlert) it.offTrackAlertId + 1 else it.offTrackAlertId,
                )
            }
        } else {
            _uiState.update { it.copy(returnToStart = info) }
        }
        return info
    }

    private fun canFireOffTrackAlert(): Boolean {
        val last = lastOffTrackAlertAtMillis ?: return true
        return currentTime.nowEpochMillis() - last >= OFF_TRACK_ALERT_COOLDOWN_MILLIS
    }

    override fun onCleared() {
        pollingJob?.cancel()
        locationJob?.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L
        const val TAG = "TrackRecordingViewModel"

        /**
         * Long enough that a forager checking their pocket after one buzz has time to actually
         * look and self-correct before a second one, short enough that a sustained drift is still
         * a real, periodic reminder rather than a single easily-missed alert — an adjustable
         * assumption in the same spirit as [DetectOffTrackUseCase]'s own threshold, not a
         * data-derived constant (this project has no field data yet on what cooldown a real
         * forager would actually want).
         */
        const val OFF_TRACK_ALERT_COOLDOWN_MILLIS = 120_000L
    }
}
