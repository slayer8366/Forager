package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-21, UI split Phase 1, PR 9c).

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.MutableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MAPS_TAB_OVERLAYS
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapLayersSheet
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerPopUp

/**
 * The add-action menu, the Layers sheet and the centre-pin picker, moved out of [CompactMapTab] unchanged (RECORD -741).
 * Their state is the tab's, handed in; [currentCameraCenter] reads the map's centre when a choice is confirmed, as before.
 */
@Composable
internal fun BoxScope.CompactMapPickers(
    showActionMenuState: MutableState<Boolean>,
    showLayersSheetState: MutableState<Boolean>,
    pendingActionState: MutableState<PendingMapAction?>,
    pendingTripLocationState: MutableState<LatLng?>,
    pendingWaypointLocationState: MutableState<LatLng?>,
    currentCameraCenter: () -> LatLng,
    searchLocationPick: CompactMapSearchLocationPick,
    onLogFindHere: (LatLng) -> Unit,
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    mapLayers: MapLayersControls,
    landscape: CompactMapLandscape,
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    isFullscreen: Boolean,
    night: Boolean,
) {
    var showActionMenu by showActionMenuState
    var showLayersSheet by showLayersSheetState
    var pendingAction by pendingActionState
    var pendingTripLocation by pendingTripLocationState
    var pendingWaypointLocation by pendingWaypointLocationState
    val pickingSearchLocation = searchLocationPick.pickingSearchLocation
    val onSearchLocationPicked = searchLocationPick.onSearchLocationPicked
    val onCancelSearchLocationPick = searchLocationPick.onCancelSearchLocationPick
    val railPortEdge = landscape.railPortEdge
    val landscapeCluster = cluster.landscape
    val isMapIconBarOnLeftSide by cluster::isOnLeftSide
    val mapBottomNavHeightPx by cluster::bottomNavHeightPx
    // Inside this Box, not alongside it, so it can align near the add button's own
    // corner of the icon stack above — see AddActionTile's doc comment for why this
    // reads as opening "from" that button rather than as a centered system dialog.
    AddActionTile(
        visible = showActionMenu,
        onPlanTrip = {
            showActionMenu = false
            pendingAction = PendingMapAction.PLAN_TRIP
        },
        onLogFind = {
            showActionMenu = false
            pendingAction = PendingMapAction.LOG_FIND
        },
        onDropWaypoint = {
            showActionMenu = false
            pendingAction = PendingMapAction.DROP_WAYPOINT
        },
        onDismiss = { showActionMenu = false },
        // Landscape B1: the same padding as the cluster it is anchored to, so the two
        // share one frame.
        modifier = Modifier.fillMaxSize().padding(controlsPadding),
        // Expanded-panels dispatch: anchored to the bar's live side and drag offset
        // (see mapIconBarPanelAnchorOffset above), plus this panel's own row.
        anchor = cluster.sideAlignment,
        anchorOffset = cluster.panelAnchorOffset(LocalDensity.current).let { anchor ->
            DpOffset(x = anchor.x, y = anchor.y + (if (landscapeCluster) ADD_TILE_ANCHOR_OFFSET_LANDSCAPE else ADD_TILE_ANCHOR_OFFSET))
        },
        growsFrom = if (isMapIconBarOnLeftSide) Alignment.BottomStart else Alignment.BottomEnd,
    )

    // Map layers L0b, B1: the Layers sheet, in place of the basemap-only popover this row
    // used to open. A modal bottom sheet in its own window, so it needs no anchor to the
    // cluster and no padding for the rail.
    if (showLayersSheet) {
        MapLayersSheet(
            mapMode = mapMode,
            onMapModeSelected = onMapModeSelected,
            overlays = MAPS_TAB_OVERLAYS,
            colourFields = mapLayers.listedColourFields,
            state = mapLayers.stored,
            onVisibilityChanged = mapLayers.onVisibilityChanged,
            onOpacityChanged = mapLayers.onOpacityChanged,
            onColourFieldMoved = mapLayers.onColourFieldMoved,
            onDismiss = { showLayersSheet = false },
        )
    }

    // Owner finding on device: the OK/Cancel row sat under the app's nav (and under
    // Android's own navigation bar in fullscreen), because this Box spans the full
    // screen height. Outside fullscreen the nav's real measured height — which already
    // includes the system bar it consumes (CLAUDE.md, "Robolectric reports zero window
    // insets") — is what's underneath; in fullscreen the nav has slid away and only
    // the system navigation bar is. The fullscreen half is device-only by
    // construction: Robolectric reports that inset as zero.
    // Dispatch 2026-09-28-104, item 5: in the rail layout the nav-bar inset applies outside
    // fullscreen too (no bottom nav is measured there, so mapBottomNavHeightPx is zero), and the
    // row also takes controlsPadding (below) so it clears the rail. Device-only, as above.
    val centrePinConfirmBottomInset = if (isFullscreen || railPortEdge != null) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        with(LocalDensity.current) { mapBottomNavHeightPx.toDp() }
    }
    // Motion Part 2, item 4 (scout M9; "Fade and grow", from where each belongs): the centre pin grows from its tip, the
    // map's centre, and the OK/Cancel row up from its bottom edge; each shrinks back as it leaves, and from that moment
    // takes no touch (item 5). One overlay for both of its uses, the add menu's three and the search's "Set on map",
    // whose OK and Cancel stay as they were: the add menu's when an action is pending, the search's otherwise.
    CentrePinLocationPickerPopUp(
        visible = pendingAction != null || pickingSearchLocation,
        onConfirm = {
            // Read once into a local (dispatch 2026-10-19-06, item 2): the compiler knows a local val is non-null inside the if,
            // so the when needs no null branch. Every write to this state is a UI callback on the main thread, and nothing ran
            // between the two reads this replaces, so the value is the same.
            val action = pendingAction
            if (action != null) {
                when (action) {
                    PendingMapAction.PLAN_TRIP -> pendingTripLocation = currentCameraCenter()
                    PendingMapAction.LOG_FIND -> onLogFindHere(currentCameraCenter())
                    PendingMapAction.DROP_WAYPOINT -> pendingWaypointLocation = currentCameraCenter()
                }
                pendingAction = null
            } else if (pickingSearchLocation) {
                onSearchLocationPicked(currentCameraCenter())
            }
        },
        onCancel = {
            if (pendingAction != null) pendingAction = null else if (pickingSearchLocation) onCancelSearchLocationPick()
        },
        modifier = Modifier.fillMaxSize(),
        bottomInset = centrePinConfirmBottomInset,
        rowPadding = controlsPadding,
        // The map's own night (the renderMode handed to mapSlot above), so the pin
        // follows Night Maps (colour build C2 (e)).
        night = night,
    )
}
