package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.LaunchedEffect
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.track.RecordingNotice

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s track recording inputs,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 11 separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** Track recording: its state, toggle, notices, breadcrumbs, sundown line and Back by, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenRecording(
    /**
     * Whether a track is currently being recorded, and [MapIconBar]'s start/stop toggle for it —
     * that bar's 5th row (fullscreen, orientation-reset, GPS/locate-me, map mode, **record**,
     * return-to-vehicle, search, add), not the compass/elevation/MGRS strip: record and
     * return-to-vehicle moved out of the strip and into the icon bar on 2026-08-26 (see
     * `map-redesign.md`'s "Icon stack: superseded from 5 to a 7-icon stopgap" section), and the
     * bar itself grew from floating circles to one panel bar the same session (see [MapIconBar]'s
     * own doc comment for the current 8-row shape). **Corrected 2026-08-28** — this comment
     * previously described the pre-2026-08-26 placement. See `MainActivity`'s `LaunchedEffect` on
     * [isRecording] for what actually starts/stops [com.zynergylabs.forager.app.service.TrackRecordingService].
     */
    val isRecording: Boolean = false,
    val onToggleRecording: () -> Unit = {},
    /** Set when the most recent [onToggleRecording]-triggered start failed or was refused — shown as a one-shot Toast in [CompactMapTab], the same [LaunchedEffect]-on-a-status-field shape as its existing `locateMeStatus` Toast. */
    val startRecordingErrorMessage: String? = null,
    /**
     * Alert-delivery dispatch, Item 3: the one-time silenced-phone warning for the recording that
     * just started — shown once as a `SnackbarDuration.Long` Snackbar in the map Scaffold's existing
     * host, no action button (the copy never tells the user to change a setting). Keyed on its id so
     * the same text re-shows on a later trip. See [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.tripStartWarning].
     */
    val tripStartWarning: RecordingNotice? = null,
    /**
     * Timestamp-filter dispatch, Item 3: the once-per-recording notice that most of the active
     * track is being excluded as network-provider fixes — same host, same shape as
     * [tripStartWarning]. See [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.networkFixesNotice].
     */
    val networkFixesNotice: RecordingNotice? = null,
    /**
     * Dispatch 2026-09-28-626 (plan T14): the off-track reminder's background prompt, shown once in
     * the same host as [tripStartWarning]; a tap anywhere on it opens Forager's App info page. See
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.backgroundRunPrompt].
     */
    val backgroundRunPrompt: RecordingNotice? = null,
    /**
     * The active track's recorded points, oldest first — see [com.zynergylabs.forager.app.ui.map.MapSlot]'s own
     * doc comment on this same parameter for how it's drawn. Empty whenever [isRecording] is false.
     */
    val breadcrumbPoints: List<LatLng> = emptyList(),
    /**
     * The recording's sundown line (dispatch 2026-09-28-592, plan task T3), from
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.sundownLine]: the strip shows it while
     * recording, the navigation display once Return or Navigate is tapped. `null` with no recording.
     */
    val recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine? = null,
    /**
     * This recording's Back by (dispatch 2026-09-28-645, plan task T15), from
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.backBy]: the quick menu's top line,
     * the gear's dot, and the strip's line in its last hour. `null` with none set or no recording.
     */
    val recordingBackBy: com.zynergylabs.forager.app.domain.BackByShown? = null,
    /** The quick menu's "+1 h", "+2 h", "+3 h" and picked time: [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.setBackBy]. */
    val onBackByChoice: (com.zynergylabs.forager.app.domain.BackByChoice) -> Unit = {},
    /** The quick menu's "Clear": [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.clearBackBy]. */
    val onClearBackBy: () -> Unit = {},
)
