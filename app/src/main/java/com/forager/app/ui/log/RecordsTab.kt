package com.forager.app.ui.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.Waypoint
import com.forager.app.ui.availability.AvailabilityUiState
import com.forager.app.ui.availability.OfflineMapsPanel
import com.forager.app.ui.availability.WaypointsSection
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.track.TrackExportList

/**
 * The Journal's **Records** tab (journal restructure Stage 1) — "a logbook: raw, complete,
 * machine-generated data. It shows everything, it does not curate" (the project owner's own
 * framing). Three submenus, relocated unmodified from where they used to live rather than
 * rebuilt: **Waypoints** (used to be a [WaypointsSection] inside `SearchControls`, reachable from
 * both window classes' own drawers), **Offline Maps** and **Recorded Tracks** (used to be
 * Settings submenus, reached via `DrawerPanel.OfflineMaps`/`DrawerPanel.Tracks` on medium/expanded
 * and `showOfflineMaps`/`showTracks` local state on compact). None of the three screens
 * themselves changed — only where they're reached from.
 *
 * Follows this app's one existing nested-tab precedent, [LogGalleryScreen]'s
 * `SecondaryTabRow`/`LogGalleryTab` — this codebase has no navigation library (no `NavHost`, no
 * `NavController`), so, like every other "route" in this app, [RecordsSubTab] is a private enum
 * plus local `remember` state, not a real navigation destination.
 *
 * **No header, no back arrow, unlike the drill-in shape these three screens used inside Settings.**
 * A flat `SecondaryTabRow` sub-tab is left by tapping another tab, not by a back affordance
 * embedded in the content — so [OfflineMapsPanel]/[TrackExportList] are called here without the
 * header rows (`OfflineMapsHeader`, `TrackExportHeader`) their old drill-in homes needed; both
 * were deleted as dead code once this became their only caller's shape.
 *
 * [onOfflineMapsOpened]/[onTracksOpened] fire once per entry into their own sub-tab (via
 * [LaunchedEffect] keyed on [RecordsSubTab]), the same "lazy-load on becoming visible" semantics
 * their old `onOpenOfflineMaps`/`onOpenTracks` callbacks had when tapping the old Settings entry
 * rows opened the drill-in submenu. Waypoints needs no such callback: [waypoints] already loads
 * eagerly at `TrackRecordingViewModel` init, unconditional on anything being opened.
 */
@Composable
internal fun RecordsTab(
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    availabilityUiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    night: Boolean,
    onOfflineMapRegionPicked: (LatLng) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onOfflineMapsOpened: () -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
    tracks: List<Track>,
    onTracksOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(RecordsSubTab.WAYPOINTS) }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            RecordsSubTab.OFFLINE_MAPS -> onOfflineMapsOpened()
            RecordsSubTab.RECORDED_TRACKS -> onTracksOpened()
            RecordsSubTab.WAYPOINTS -> Unit
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == RecordsSubTab.WAYPOINTS,
                onClick = { selectedTab = RecordsSubTab.WAYPOINTS },
                text = { Text("Waypoints") },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.OFFLINE_MAPS,
                onClick = { selectedTab = RecordsSubTab.OFFLINE_MAPS },
                text = { Text("Offline Maps") },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.RECORDED_TRACKS,
                onClick = { selectedTab = RecordsSubTab.RECORDED_TRACKS },
                text = { Text("Recorded Tracks") },
            )
        }

        when (selectedTab) {
            RecordsSubTab.WAYPOINTS -> WaypointsSection(
                waypoints = waypoints,
                errorMessage = waypointsErrorMessage,
                onDeleteWaypoint = onDeleteWaypoint,
                entryReferenceCounts = waypointEntryReferenceCounts,
                modifier = Modifier.weight(1f),
            )

            RecordsSubTab.OFFLINE_MAPS -> OfflineMapsPanel(
                modifier = Modifier.weight(1f),
                uiState = availabilityUiState,
                distanceUnit = distanceUnit,
                currentTime = currentTime,
                mapSlot = mapSlot,
                isNightMode = night,
                onRegionPicked = onOfflineMapRegionPicked,
                onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                onOfflineMapNameChanged = onOfflineMapNameChanged,
                onDownloadOfflineMaps = onDownloadOfflineMaps,
                onDeleteOfflineRegion = onDeleteOfflineRegion,
            )

            RecordsSubTab.RECORDED_TRACKS -> TrackExportList(tracks = tracks, modifier = Modifier.weight(1f))
        }
    }
}

/** Which of [RecordsTab]'s three sub-tabs is selected — ordinal order matches display order. */
private enum class RecordsSubTab { WAYPOINTS, OFFLINE_MAPS, RECORDED_TRACKS }
