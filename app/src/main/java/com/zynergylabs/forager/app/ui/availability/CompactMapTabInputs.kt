package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin

/*
 * Holder classes for [CompactMapTab]'s inputs (RECORD -739).
 *
 * Why these exist: at 67 parameters, CompactMapTab's compiled form was rejected by ART's verifier on
 * every phone that installed main at 0e766d94 (a VerifyError at class load, so the app could not
 * start), while the JVM, Robolectric and CI accepted it. Grouping related parameters into these small
 * classes shrinks the method's signature, and the generated `$changed`/`$default` bookkeeping with it.
 * No behaviour changes: [CompactMapTab] unpacks each holder into locals with the old parameter names
 * at its top, so its body reads exactly as it did. Each field keeps the doc comment its parameter had.
 * The rejected alternative, disabling verification or changing build flags, would hide the next one.
 * The report is `docs/audits/2026-10-08-launch-verifyerror-report.md`.
 */

/** Everything [CompactMapTab] needs about navigation: the return, a waypoint navigation and the navigation view. */
internal data class CompactMapNavigation(
    val returnToStart: ReturnToStartInfo?,
    val isReturning: Boolean,
    /**
     * Whether *any* navigation mode is active — [AvailabilityScreen]'s one `isNavigating`, see its
     * doc comment. Gates the HUD's presence and the compass strip's absence together, so heading,
     * elevation and coordinates are on screen exactly once in either state. Distinct from
     * [isReturning], which is one such mode (the only one in stage one) and still drives the
     * control pill's lit return toggle and the off-track heuristic.
     */
    val isNavigating: Boolean,
    /** See [AvailabilityScreen]'s own `isArrivalHeld` doc comment (RECORD -845): the display holds "Arrived", whatever the fix. */
    val isArrivalHeld: Boolean = false,
    val isOffTrack: Boolean,
    val onToggleReturning: () -> Unit,
    /** See [AvailabilityScreen]'s own `navigationTarget` doc comment. */
    val navigationTarget: Waypoint?,
    /** See [AvailabilityScreen]'s own `returnRoute` doc comment. */
    val returnRoute: ReturnRoute,
    /** The way back to draw while navigating (dispatch 2026-09-28-497). */
    val routeLine: RouteLine?,
    /** See [AvailabilityScreen]'s own `onRetryRoute` doc comment. */
    val onRetryRoute: () -> Unit,
    /**
     * Dispatch 2026-09-28-502: the navigation on is to a chosen waypoint, [navigationTarget]. The HUD is
     * then the straight-line one (decision D2: no route given), the map draws the dashed line from the
     * walker to the waypoint instead of the return's way back, and the return control is the X-circle
     * whether or not a track is recording.
     */
    val isNavigatingToWaypoint: Boolean = false,
    /**
     * Dispatch 2026-09-28-502, step 3: the dashed line from the walker to the waypoint, current or kept faded after the
     * fix was lost (the owner's "Keep the last line, faded"). Worked out and held in [AvailabilityScreen], above the tab
     * switch; `null` draws none.
     */
    val waypointStraightLine: StraightLine? = null,
    /**
     * Dispatch 2026-09-28-502, Amendment 1: Back ended a waypoint navigation started from this tab's bubble
     * or the details sheet opened from it, and that step is opened again here, once; then
     * [onWaypointReopenConsumed]. `null` asks for nothing.
     */
    val waypointReopen: WaypointNavigationOrigin? = null,
    val onWaypointReopenConsumed: () -> Unit = {},
    /** Dispatch 2026-09-28-430: whether the map follows in the navigation view; false once the user has moved it away. */
    val navigationFollowing: Boolean,
    /** Dispatch 2026-09-28-430: counts "Return to Route" (and locate while navigating) requests. */
    val navigationViewRequestId: Int,
    /** Dispatch 2026-09-28-430: the map reports the user moving it away from the navigation view. */
    val onLeftNavigationView: () -> Unit,
    /** Dispatch 2026-09-28-430: "Return to Route", and locate while navigating. */
    val onReturnToRoute: () -> Unit,
    /** Dispatch 2026-09-28-440: the set zoom is still to be applied in this navigation. */
    val navigationZoomPending: Boolean,
    /** Dispatch 2026-09-28-440: the map has applied the set zoom. */
    val onNavigationZoomApplied: () -> Unit,
)

/** The track recording as [CompactMapTab] shows and controls it, with the waypoints dropped on the map. */
internal data class CompactMapRecording(
    val isRecording: Boolean,
    val onToggleRecording: () -> Unit,
    val startRecordingErrorMessage: String?,
    val breadcrumbPoints: List<LatLng>,
    val waypoints: List<Waypoint>,
    val onDropWaypoint: (LatLng, String) -> Unit,
)

/**
 * [CompactMapTab]'s short-landscape-window inputs. All default to portrait (no rail, no punch-hole side, no
 * capped search width, nothing reported), which is today's behaviour everywhere but a short landscape window.
 */
internal data class CompactMapLandscape(
    /**
     * Landscape B1 (Resolutions R12/R13, as revised on the owner's correction). Non-null in a
     * short landscape window: the charger-port edge, where this tab overlays
     * [ForagerNavigationRail] at 80% in place of its [ForagerBottomNav], in the same layer the
     * bottom bar occupies. The map under it stays full-bleed and never changes size; the rail is
     * absent in fullscreen, with no animation. Null everywhere else, which is today's behaviour.
     */
    val railPortEdge: ScreenEdge? = null,
    /**
     * Landscape B2 (S1): the punch-hole edge (`punchHoleEdgeFor`), non-null exactly when
     * [railPortEdge] is. The search bar, its filter chip and the cluster's landscape default sit
     * on this side.
     */
    val punchHoleEdge: ScreenEdge? = null,
    /** Landscape B2 (S2/S3): the search bar's capped width on the punch-hole side; null in portrait. */
    val landscapeSearchWidth: Dp? = null,
    /** Reports the overlaid rail's measured width up, as `onBottomNavHeightMeasured` does the bar's. */
    val onRailWidthMeasured: (Float) -> Unit = {},
    /**
     * RECORD -729: the compass strip's measured height in a landscape window, while it shows and is not leaving; 0 otherwise.
     * The scaffold gives the search bar this height, so the two meet at the centre at one height.
     */
    val onLandscapeStripHeightMeasured: (Dp) -> Unit = {},
    /**
     * RECORD -732: the compass strip's measured width in a landscape window, while it shows and is not leaving; 0 otherwise.
     * The scaffold gives the search bar the rest of the room, so the bar ends where the strip begins.
     */
    val onLandscapeStripWidthMeasured: (Dp) -> Unit = {},
)

/**
 * True while [AdvancedSearchDropdown]'s "Set on map" is active — a [compactMainScaffold]-owned
 * state, not local to this tab, since the dropdown that triggers it lives above the bottom-nav
 * switch and can be reached from any tab. Shows [CentrePinLocationPickerOverlay] over this same
 * map the same way `pendingAction` already does, rather than a second picker. The default picks nothing.
 */
internal data class CompactMapSearchLocationPick(
    val pickingSearchLocation: Boolean = false,
    val onSearchLocationPicked: (LatLng) -> Unit = {},
    val onCancelSearchLocationPick: () -> Unit = {},
)
