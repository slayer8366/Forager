package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-14, UI split Phase 1, PR 9a).

import androidx.compose.runtime.State
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.map.RouteOnMap
import com.zynergylabs.forager.app.ui.map.NavigationViewRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapBubbleTarget
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.focusedFeature
import com.zynergylabs.forager.app.ui.map.focusedObservationId
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.MapRenderMode

/** The map itself ([mapSlot] and what it draws), moved out of [CompactMapTab] unchanged (RECORD -741); [fanBackEnabled] is worked out there. */
@Composable
internal fun CompactMapMapSlot(
    mapSlot: MapSlot,
    displayRegion: Region,
    filteredSightings: List<Sighting>,
    uiState: AvailabilityUiState,
    recording: CompactMapRecording,
    navigation: CompactMapNavigation,
    arrivedAt: LatLng?,
    resumeTrackingRequestId: Int,
    resetOrientationRequestId: Int,
    tapped: TappedMapThing?,
    onTapped: (TappedMapThing) -> Unit,
    mapLayers: MapLayersControls,
    renderMode: MapRenderMode,
    fanBackEnabled: Boolean,
    onFeatureTap: (MapFeatureTap) -> Unit,
    onCloseBubble: () -> Unit,
    cameraMemory: MapCameraMemory,
    returnMemory: MapReturnMemory,
    navigationFacing: NavigationFacing,
    onLeftView: () -> Unit,
    onStartZoomApplied: () -> Unit,
    trueHeading: State<TrueHeadingReading>,
    cluster: MapIconClusterState,
    focusOverride: LatLng?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onCameraIdle: (LatLng) -> Unit,
) {
    val breadcrumbPoints = recording.breadcrumbPoints
    val waypoints = recording.waypoints
    val isNavigating = navigation.isNavigating
    val navigationTarget = navigation.navigationTarget
    val routeLine = navigation.routeLine
    val waypointStraightLine = navigation.waypointStraightLine
    val navigationFollowing = navigation.navigationFollowing
    val navigationViewRequestId = navigation.navigationViewRequestId
    val navigationZoomPending = navigation.navigationZoomPending
    val landscapeCluster = cluster.landscape
    mapSlot(
        displayRegion,
        MapOverlayContent(
            sightings = filteredSightings,
            plannedTrips = uiState.plannedTrips,
            breadcrumbPoints = breadcrumbPoints,
            // Dispatch -497: once arrived, the start's pin is left out and the arrival
            // ring drawn in its place (RouteHomeLayers.kt), so the start changes form.
            waypoints = if (arrivedAt != null) waypoints.filterNot { it.id == navigationTarget?.id } else waypoints,
            // Dispatch -502: a waypoint navigation's dashed line beside the return's way back. There is no way back to
            // draw meanwhile: the return the waypoint overrules is paused, and pausing it clears its line
            // (TrackRecordingViewModel.stopReturn), so nothing here has to hide it.
            route = if (isNavigating) RouteOnMap(routeLine, arrivedAt, waypointStraightLine?.points, waypointStraightLine?.isCurrent ?: true, waypointStraightLine?.isOffline ?: false) else null,
            resumeTrackingRequestId = resumeTrackingRequestId,
            // RECORD -750, item 5: each location search frames its region, following or not.
            searchFrameRequestId = uiState.searchFrameSerial,
            resetOrientationRequestId = resetOrientationRequestId,
            focusedObservationId = tapped.focusedObservationId,
            focusedFeature = tapped.focusedFeature,
            // Map layers L0b, B2 (owner: "Every saved record"): every saved find with a
            // location, every ended track, every located album photo and every offline
            // region, pending deletes left out, each visible by default.
            keptTrackPolylines = mapLayers.records.trackPolylines,
            findMarkers = mapLayers.records.findMarkers,
            photoMarkers = mapLayers.records.photoMarkers,
            offlineRegionCircles = mapLayers.records.offlineRegionCircles,
            // J8-2: the shown entries' kept records, highlighted under their own glyphs.
            journalHighlights = mapLayers.journalHighlights,
        ),
        renderMode.copy(
            // The fan's Back goes after the add-action menu and the pin pickers (dispatch 2026-09-28-298):
            // their handler above is the one asked while they are up. Kept apart from the bubble's gate
            // below, which is the bubble's own.
            backEnabled = fanBackEnabled,
            onFeatureTap = onFeatureTap,
            onCloseBubble = onCloseBubble,
            cameraMemory = cameraMemory,
            returnMemory = returnMemory,
            // Dispatch 2026-09-28-430: the navigation view while navigating, and the one
            // true heading for the puck and a facing-up map (ruling A).
            navigationView = if (isNavigating) NavigationViewRequest(navigationFacing, navigationFollowing, navigationViewRequestId, onLeftView, navigationZoomPending, onStartZoomApplied) else null,
            trueHeading = trueHeading,
            // Item 1 (dispatch 2026-09-29-57, amendment -262, "Move the 'i'"): the landscape L's measured bounds, in the map's own
            // pixels, for MapLibre's attribution button to keep clear of. The L keeps its bottom limit at the nav inset; the
            // button moves (SightingsMap, attributionEndInsetClearOf). Only the landscape L: portrait is unchanged.
            attributionKeepClear = if (landscapeCluster) {
                cluster.clusterBoundsInRoot?.translate(-cluster.mapContentBoxLeftInRootPx, -cluster.mapContentBoxTopInRootPx)
            } else {
                null
            },
            // RECORD -954: in a short landscape window the "i" sits beside the map credit strip (SightingsMap).
            attributionBesideCredit = landscapeCluster,
        ),
        focusOverride,
        {},
        // Tapping the map restores chrome while fullscreen — decision #5 — AND dismisses
        // the bubble below regardless of fullscreen state (a plain tap on empty map is
        // its dismiss gesture). Since M1 a tap on a glyph is not a plain tap (owner's
        // ruling 1, "Bubble only"), so it opens its bubble and does neither.
        {
            if (isFullscreen) onToggleFullscreen()
            onCloseBubble()
        },
        { sighting, screenPosition, bearingDeg ->
            onTapped(TappedMapThing(MapBubbleTarget.SightingTarget(sighting), screenPosition, bearingDeg))
        },
        onCameraIdle,
        Modifier.fillMaxSize(),
    )
}
