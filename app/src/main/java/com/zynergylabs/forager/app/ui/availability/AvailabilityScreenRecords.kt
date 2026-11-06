package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.PendingDeleteNotice
import com.zynergylabs.forager.app.ui.log.PendingDeleteSnackbarEffects

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s saved waypoints, tracks and pending-delete notices,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 12 separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** Saved waypoints and tracks, their deletes and errors, and the Journal's pending-delete notices, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenRecords(
    /**
     * Saved waypoints — independent of any track recording, per [Waypoint]'s own doc comment.
     * Rendered as pins on the map ([com.zynergylabs.forager.app.ui.map.MapSlot]) and listed, with delete, in
     * the Tools drawer's "Waypoints" section ([WaypointsSection]).
     */
    val waypoints: List<Waypoint> = emptyList(),
    /** Set when the most recent waypoint load/add/remove failed — shown, with error color, in [WaypointsSection] in place of the list. */
    val waypointsErrorMessage: String? = null,
    /** How many Cartography entries currently keep a reference to each waypoint (by id) — Journal Stage 2b's 4b deletion warning, shown in [WaypointsSection]'s own confirm dialog. */
    val waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** Called with the placed location and the confirmed name when "Drop a waypoint" is chosen from [ThreeWayActionDialog] — see [WaypointNameDialog]. */
    val onDropWaypoint: (LatLng, String) -> Unit = { _, _ -> },
    val onDeleteWaypoint: (String) -> Unit = {},
    /** Part 2 follow-ups F1 item 5 (owner "Option A"): a finished track's swipe or details Delete asks for a pending delete with Undo; `null` (the default) leaves tracks without a delete. */
    val onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed track delete failed and the track is back; shown above the Tracks list. */
    val tracksErrorMessage: String? = null,
    /**
     * Dispatch 2026-09-28-502: whether [waypoints] has been read yet (`TrackRecordingUiState.waypointsLoaded`).
     * Until it has, a waypoint being navigated to that is missing from it is waited for, not taken as
     * deleted. `true` by default: a caller that hands a list has loaded it.
     */
    val waypointsLoaded: Boolean = true,
    /**
     * Every recorded track, for the Settings "Recorded Tracks" export panel — see
     * [com.zynergylabs.forager.app.ui.track.TrackExportPanel]'s own doc comment. Empty by default, the same
     * shape as the screen's `breadcrumbPoints` and [waypoints]: the real list is [trackUiState.tracks][com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.tracks],
     * threaded in by `MainActivity`.
     */
    val tracks: List<Track> = emptyList(),
    /** Refreshes [tracks] — called whenever the export panel opens, mirroring the screen's `onOfflineMapsOpened`'s own "reload on open" shape. */
    val onTracksOpened: () -> Unit = {},
    /**
     * GPX full-record export dispatch: the unfiltered read for a shared track's `<extensions>`
     * block — see [com.zynergylabs.forager.app.ui.track.TrackExportList]'s own doc comment. Empty by default,
     * same [tracks]/[waypoints] shape; the real function is
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.getFullRecord], threaded in by
     * `MainActivity`.
     */
    val getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    /**
     * The Journal's pending deletes, one per record type at most (journal redesign J4): each is shown
     * as an Undo snackbar in this screen's one snackbar host, the same slot "Saved to Drafts" and
     * the trip-start warning use, so it docks where those do and outlives a
     * Journal tab change. `MainActivity` builds them from the owning ViewModels' pending state; see
     * [PendingDeleteSnackbarEffects]. Empty by default, so no other caller changes.
     */
    val pendingDeleteNotices: List<PendingDeleteNotice> = emptyList(),
)
