package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, B2 (dispatch 2026-10-19-40): the map and navigation values CompactMainScaffold hands the Maps tab, grouped as
// the owner's "B + C, bundles" asked. Each field was a frame parameter of the same name, type and comment; the screen builds this at
// its frame call from the same expressions it passed before, and the frame unpacks it under the old names. No defaults: the one
// caller sets every field, as it set every parameter. Wrapped and raw values are not mixed: onToggleReturning is the screen's
// navigation control (a waypoint navigation first), navigationTarget the resolved target and onMapModeChange the screen's wrapper,
// as the frame received them; the raw navigation and map-controls fields are not here.

import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapSlot

internal data class CompactScaffoldMapInputs(
    val mapSlot: MapSlot,
    val mapIconClusterPosition: MapIconClusterPositionState,
    /** Part 1 layout fixes, item 4: the Maps tab's camera, kept above the tab switch; threaded to [CompactMapTab]. */
    val mapCameraMemory: MapCameraMemory,
    /** Item 8 (dispatch 2026-09-29-57): what "Open in Journal" leaves for the Maps tab that Back returns to; see [MapReturnMemory]. */
    val mapReturnMemory: MapReturnMemory,
    val mapRenderMode: MapRenderMode,
    /**
     * Map layers L0b: the Maps tab's Layers sheet, legend and saved records ([MapLayersControls]),
     * threaded to [CompactMapTab], and the shared layer choices threaded to the Journal's entry map.
     * Nothing here reads it.
     */
    val mapLayers: MapLayersControls,
    val isNightMode: Boolean,
    val mapWaypoints: List<Waypoint>,
    val returnToStart: ReturnToStartInfo?,
    val isReturning: Boolean,
    val isNavigating: Boolean,
    /** See [AvailabilityScreen]'s own `isArrivalHeld` doc comment (RECORD -845). */
    val isArrivalHeld: Boolean,
    val isOffTrack: Boolean,
    /** See [CompactMapTab]'s own `quickSettings` doc comment (dispatch 2026-09-28-645). */
    val quickSettings: MapQuickSettings?,
    val compassProvider: CompassProvider,
    val computeTrueHeading: ComputeTrueHeadingUseCase,
    val navigationTarget: Waypoint?,
    val returnRoute: ReturnRoute,
    val routeLine: RouteLine?,
    val onRetryRoute: () -> Unit,
    /** Dispatch 2026-09-28-502: navigating to a chosen waypoint, threaded to [CompactMapTab] and [JournalTab]; see [WaypointNavigateControls]. */
    val waypointNavigate: WaypointNavigateControls,
    val showDecimalDegrees: Boolean,
    val onToggleCoordinateFormat: () -> Unit,
    val navigationFollowing: Boolean,
    val navigationViewRequestId: Int,
    val onLeftNavigationView: () -> Unit,
    val onReturnToRoute: () -> Unit,
    val navigationZoomPending: Boolean,
    val onNavigationZoomApplied: () -> Unit,
    val basemap: Basemap,
    val onLocateMe: () -> Unit,
    val onMapFullscreenChanged: (Boolean) -> Unit,
    /** M1: what the Maps tab's glyph bubbles look records up in; threaded to [CompactMapTab]. */
    val mapBubbleSources: MapRecordSources,
    val onMapModeChange: (MapMode) -> Unit,
    val onToggleReturning: () -> Unit,
    val onClearMapTaxonFilter: () -> Unit,
)
