package com.forager.app.ui.track

import com.forager.app.domain.model.ReturnToStartInfo
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
) {
    val isRecording: Boolean get() = activeTrack != null
}
