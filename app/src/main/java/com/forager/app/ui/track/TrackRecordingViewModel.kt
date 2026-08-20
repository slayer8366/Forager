package com.forager.app.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.DetectOffTrackUseCase
import com.forager.app.domain.GetWaypointsUseCase
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.StartTrackUseCase
import com.forager.app.domain.TrackRepository
import com.forager.app.domain.model.ReturnToStartInfo
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackRecordingUiState())
    val uiState: StateFlow<TrackRecordingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var locationJob: Job? = null

    // Oldest first, cleared on every startReturn()/stopReturn()/stopRecording() — see
    // returnToStart()'s doc comment for why this lives here rather than in TrackRecordingUiState
    // itself (it's tracking history feeding a decision, not state the UI reads directly).
    private val recentReturnDistancesMeters = mutableListOf<Double>()

    init {
        loadWaypoints()
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
                    _uiState.update { it.copy(startRecordingErrorMessage = error.message ?: "Couldn't start recording.") }
                }
        }
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
                .onFailure { error -> _uiState.update { it.copy(waypointsErrorMessage = error.message ?: "Couldn't load waypoints.") } }
        }
    }

    fun addWaypoint(lat: Double, lng: Double, name: String, note: String = "") {
        viewModelScope.launch {
            createWaypoint(lat, lng, altitude = null, name = name, note = note)
                .onSuccess { loadWaypoints() }
                .onFailure { error -> _uiState.update { it.copy(waypointsErrorMessage = error.message ?: "Couldn't save waypoint.") } }
        }
    }

    fun removeWaypoint(id: String) {
        viewModelScope.launch {
            deleteWaypoint(id)
                .onSuccess { loadWaypoints() }
                .onFailure { error -> _uiState.update { it.copy(waypointsErrorMessage = error.message ?: "Couldn't delete waypoint.") } }
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
     */
    fun returnToStart(current: TrackPoint): ReturnToStartInfo? {
        val start = uiState.value.breadcrumbPoints.firstOrNull() ?: return null
        val info = computeReturnToStart(current, start)
        if (uiState.value.isReturning) {
            recentReturnDistancesMeters += info.distanceMeters
            _uiState.update { it.copy(returnToStart = info, isOffTrack = detectOffTrack(recentReturnDistancesMeters)) }
        } else {
            _uiState.update { it.copy(returnToStart = info) }
        }
        return info
    }

    override fun onCleared() {
        pollingJob?.cancel()
        locationJob?.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L
    }
}
