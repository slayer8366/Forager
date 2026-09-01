package com.forager.app.ui.log

import androidx.compose.foundation.layout.Box
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
 * framing). Four submenus: **Waypoints** (used to be a [WaypointsSection] inside
 * `SearchControls`, reachable from both window classes' own drawers), **Offline Maps** and
 * **Recorded Tracks** (used to be Settings submenus, reached via `DrawerPanel.OfflineMaps`/
 * `DrawerPanel.Tracks` on medium/expanded and `showOfflineMaps`/`showTracks` local state on
 * compact), and **Finds** — added Journal Stage 2b, `amendment-2b-finds-and-trash.md`: raw
 * `MushroomLogEntry` field records, moved here **unmodified, still working exactly as it did in
 * Cartography** — override text: "the override is for the move, not for Records generally." None
 * of the four screens' own content changed — only where they're reached from.
 *
 * **[findsContent] is a slot, not inlined logic.** Compact and expanded kept their own,
 * genuinely different find-browsing/editing shapes (a report-then-edit two-step via
 * [LogGalleryScreen] for compact, straight-to-edit via [LogEntryListScreen] for expanded) — see
 * `amendment-2b-finds-and-trash.md`'s own two-things-that-will-bite note and the override's exact
 * limits; unifying those two shapes was section 1's original ask before the amendment narrowed it
 * to "relocate ... working as it does today." [JournalTab]/[LogPanel] each still own their find-
 * editing state (mode, pickers) exactly as before, just render it into this tab's fourth slot now
 * instead of directly into a "Cartography" tab. [onFindsTabLeft] fires whenever [selectedTab]
 * changes away from [RecordsSubTab.FINDS] to a sibling sub-tab — the same "leaving mid-edit is an
 * incidental exit" signal [JournalTab]'s own top-level tab switch already sent before finds moved
 * here; now that finds live *inside* Records, switching among Records' own sub-tabs can interrupt
 * an edit too, a scenario that didn't exist before this move.
 *
 * Follows this app's one existing nested-tab precedent, [LogGalleryScreen]'s
 * `SecondaryTabRow`/`LogGalleryTab` — this codebase has no navigation library (no `NavHost`, no
 * `NavController`), so, like every other "route" in this app, [RecordsSubTab] is a private enum
 * plus local `remember` state, not a real navigation destination.
 *
 * **No header, no back arrow, unlike the drill-in shape these screens used inside Settings.**
 * A flat `SecondaryTabRow` sub-tab is left by tapping another tab, not by a back affordance
 * embedded in the content — so [OfflineMapsPanel]/[TrackExportList] are called here without the
 * header rows (`OfflineMapsHeader`, `TrackExportHeader`) their old drill-in homes needed; both
 * were deleted as dead code once this became their only caller's shape.
 *
 * [onOfflineMapsOpened]/[onTracksOpened] fire once per entry into their own sub-tab (via
 * [LaunchedEffect] keyed on [RecordsSubTab]), the same "lazy-load on becoming visible" semantics
 * their old `onOpenOfflineMaps`/`onOpenTracks` callbacks had when tapping the old Settings entry
 * rows opened the drill-in submenu. Waypoints/Finds need no such callback: both already load
 * eagerly at their owning `ViewModel`'s init, unconditional on anything being opened.
 *
 * **`Modifier.weight(1f)` on every branch, [findsContent] included, is load-bearing** — see this
 * file's own git history (Stage 1's `WaypointsSection` regression) and
 * `amendment-2b-finds-and-trash.md`'s own reminder: a branch without it is measured as though the
 * tab row above took no space and can overflow.
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
    findsContent: @Composable () -> Unit,
    onFindsTabLeft: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(RecordsSubTab.WAYPOINTS) }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            RecordsSubTab.OFFLINE_MAPS -> onOfflineMapsOpened()
            RecordsSubTab.RECORDED_TRACKS -> onTracksOpened()
            RecordsSubTab.WAYPOINTS, RecordsSubTab.FINDS -> Unit
        }
    }

    fun selectTab(tab: RecordsSubTab) {
        if (selectedTab == RecordsSubTab.FINDS && tab != RecordsSubTab.FINDS) onFindsTabLeft()
        selectedTab = tab
    }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == RecordsSubTab.WAYPOINTS,
                onClick = { selectTab(RecordsSubTab.WAYPOINTS) },
                text = { Text("Waypoints") },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.OFFLINE_MAPS,
                onClick = { selectTab(RecordsSubTab.OFFLINE_MAPS) },
                text = { Text("Offline Maps") },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.RECORDED_TRACKS,
                onClick = { selectTab(RecordsSubTab.RECORDED_TRACKS) },
                text = { Text("Recorded Tracks") },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.FINDS,
                onClick = { selectTab(RecordsSubTab.FINDS) },
                text = { Text("Finds") },
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

            RecordsSubTab.FINDS -> Box(modifier = Modifier.weight(1f)) { findsContent() }
        }
    }
}

/** Which of [RecordsTab]'s four sub-tabs is selected — ordinal order matches display order. */
private enum class RecordsSubTab { WAYPOINTS, OFFLINE_MAPS, RECORDED_TRACKS, FINDS }
