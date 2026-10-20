package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Waypoint

/*
 * Phase 2.1 of the UI split (dispatch 2026-10-19-28, RECORD -1436): [AvailabilityScreen]'s return and waypoint navigation inputs,
 * grouped into one bundle as `CompactMapTabInputs.kt` grouped [CompactMapTab]'s (RECORD -739).
 * These were 11 separate parameters of the screen; each field keeps that parameter's default and doc
 * comment, and the screen unpacks the bundle into locals with the old names at the top of its body, so
 * the body reads as it did. Public because [AvailabilityScreen] is public.
 */

/** The return and waypoint navigation: state, route and controls, for [AvailabilityScreen]. Every field is defaulted, as the parameters were. */
data class AvailabilityScreenNavigation(
    /**
     * Bearing/distance/elevation difference back to the active track's start point, from the
     * device's current position — `null` whenever nothing is being recorded, no fix has come in
     * yet, or no breadcrumb has landed to compute a start from. See [ReturnToStartInfo]'s own doc
     * comment for why there's no ETA, and [CompassElevationStripContent] for where this renders.
     */
    val returnToStart: ReturnToStartInfo? = null,
    /** Whether the walker has said they're heading back — see [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.startReturn]'s own doc comment. */
    val isReturning: Boolean = false,
    /**
     * RECORD -844, -845: the return has just ended on arrival and the navigation display holds "Arrived",
     * with the start ringed, until the ViewModel's hold ends; see
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.isArrivalHeld]. Counts as navigating, so
     * the display stays up for the hold and then closes to the plain map.
     */
    val isArrivalHeld: Boolean = false,
    /** Set once [isReturning] and the walker has gone off the path they walked out, until back on it — see `OffTrackJudge` (dispatch 2026-09-28-425). */
    val isOffTrack: Boolean = false,
    val onToggleReturning: () -> Unit = {},
    /**
     * Navigation HUD stage one's target — the active track's origin waypoint
     * ([com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.originWaypoint]), `null` when there is
     * none. Threaded separately from [AvailabilityScreenRecords.waypoints] because it decides which of them the *map* draws
     * ([mapVisibleWaypoints]) as well as what the HUD points at.
     */
    val navigationTarget: Waypoint? = null,
    /**
     * The route home while returning (dispatch 2026-09-28-423, plan task T6), read from
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.routeHome] by [returnRouteOf].
     * The HUD's needle, large figure and "Try again" come from it; see [NavigationHud]. Not
     * nullable: this screen's HUD is the return HUD, and a return is always in route mode (the
     * planner's ruling), so no caller can put it in the straight-line mode by leaving this out.
     * [ReturnRoute.Pending] until there is a result, as `MainActivity` passes it.
     */
    val returnRoute: ReturnRoute = ReturnRoute.Pending,
    /** The way back drawn on the map while returning (dispatch 2026-09-28-497); `null` draws none. */
    val routeLine: RouteLine? = null,
    /** The HUD's "Try again": `TrackRecordingViewModel.retryRoute`. */
    val onRetryRoute: () -> Unit = {},
    /**
     * Dispatch 2026-09-28-502 (plan tasks T8 and T9): "Navigate" on a waypoint, given its id
     * (`AvailabilityViewModel.onNavigateToWaypoint`). Offered in the Maps tab's bubbles, the waypoint's
     * details sheet and the Records rows; `null` (the default) offers it nowhere, as [AvailabilityScreenRecords.onDeleteTrack]
     * does for its delete.
     */
    val onNavigateToWaypoint: ((String) -> Unit)? = null,
    /** Dispatch 2026-09-28-502: ends the waypoint navigation (`AvailabilityViewModel.onStopWaypointNavigation`). */
    val onStopWaypointNavigation: () -> Unit = {},
)
