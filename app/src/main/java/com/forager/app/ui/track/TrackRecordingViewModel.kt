package com.forager.app.ui.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forager.app.domain.ComputeReturnToStartUseCase
import com.forager.app.domain.CreateWaypointUseCase
import com.forager.app.domain.DeleteWaypointUseCase
import com.forager.app.domain.GetWaypointsUseCase
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
 */
class TrackRecordingViewModel(
    private val trackRepository: TrackRepository,
    private val startTrack: StartTrackUseCase,
    private val getWaypoints: GetWaypointsUseCase,
    private val createWaypoint: CreateWaypointUseCase,
    private val deleteWaypoint: DeleteWaypointUseCase,
    private val computeReturnToStart: ComputeReturnToStartUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackRecordingUiState())
    val uiState: StateFlow<TrackRecordingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

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
        _uiState.update { it.copy(activeTrack = null) }
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

    /** [ReturnToStartInfo] from [current] back to the active track's first recorded point, or `null` if either is unavailable. */
    fun returnToStart(current: TrackPoint): ReturnToStartInfo? {
        val start = uiState.value.breadcrumbPoints.firstOrNull() ?: return null
        return computeReturnToStart(current, start)
    }

    override fun onCleared() {
        pollingJob?.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 15_000L
    }
}
