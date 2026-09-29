package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.OPEN_FIND_LABEL
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * The Journal on the wide tree (MEDIUM and EXPANDED windows): the permanent drawer's `DrawerPanel.Log`,
 * reached from the Search panel's "Mushroom Log" row. Since J6a (dispatch 2026-09-28-152) it is a
 * back-arrow row labelled "Journal" above [JournalTab], the phone's own Journal, so the wide Journal
 * and the phone's are one implementation and the phone's rulings (the Entries | Records switch, the
 * report step and "+" tile on finds, the counts and delete paths, hoisted state, the leave rules) hold
 * here without a second copy. What is left here is only what the wide tree has and the phone does not:
 * the header row, the one Back handler that returns the Journal to the Search panel, and the hand-off
 * of the detail slot ([JournalDetailSlot]).
 *
 * **Why the header is a back row.** The drawer is a stack of panels over one column, and this row is
 * the wide tree's only visible route from the Journal back to Search (the phone has a bottom bar). The
 * owner's ruling on the words (`prompts/preserved/2026-09-29-25.md`): "Journal", the phone's name.
 *
 * **Why the Journal's own state comes from above.** [journalState] and the mode holders are
 * `AvailabilityScreen`'s, the same ones it hands the compact tree, so an open detail, the Timeline or
 * Album view, the Records chip and a find over a view survive switching panels and a change of tree
 * (J6a, ruling 5). The parameters default to local state only for callers that host this panel alone
 * (tests).
 *
 * The parameters that are not documented here are documented where [JournalTab] takes them.
 */
@Composable
internal fun LogPanel(
    uiState: MushroomLogUiState,
    /** See [JournalTab]'s identical three — one hoisted dialog, [InAppCameraHost]. */
    onOpenCameraForLogEntry: () -> Unit,
    onOpenCameraForAlbum: () -> Unit,
    onOpenCameraForCartographyEntry: () -> Unit,
    mapSlot: MapSlot,
    region: Region,
    /** See [JournalTab]'s identical parameter — the device position the find's location picker opens on when one is in hand (find-location-at-creation dispatch, Fix 3). */
    deviceLocation: LatLng? = null,
    basemap: Basemap,
    /** Night mode for the location picker this hosts, and the Records tab's Offline Maps picker — see [CentrePinLocationPicker]. */
    night: Boolean = false,
    /**
     * Map layers L0b (owner's ruling 4, "Same sheet"): the layer choices the Maps tab and the entry map
     * share, and the entry map's Layers-sheet switch. Threaded through to `CartographyEntryReportScreen`
     * only; nothing here reads them. Defaulted so other callers and tests are unchanged.
     */
    mapLayers: MapLayersState = MapLayersState.DEFAULT,
    onMapLayerVisibilityChanged: (layerId: String, visible: Boolean) -> Unit = { _, _ -> },
    /**
     * Opens a row and, if it's a committed entry, immediately begins editing it — one atomic
     * ViewModel operation ([MushroomLogViewModel.onOpenEntryForEditing]), not this composable
     * calling separate open/start-editing callbacks itself. This panel has no report step (see its
     * own doc comment), so opening a row always means "edit it" — see
     * [MushroomLogViewModel.onOpenEntryForEditing]'s own doc comment for why that combination lives
     * in the ViewModel rather than being composed here from two calls.
     */
    onOpenEntryForEditing: (String) -> Unit,
    /**
     * M1: opens a find without starting an edit (`MushroomLogViewModel.onOpenEntry`), for the find
     * shown in its report over this panel. The default opens it for editing, as this panel always
     * did, so a caller that does not pass it gets the edit form under the report.
     */
    onOpenEntryForReport: (String) -> Unit = onOpenEntryForEditing,
    onCloseEntry: () -> Unit,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onSaveEntry: () -> Unit,
    onCancelEditing: () -> Unit,
    onLeaveEditingIncidentally: () -> Unit,
    /** Reports whether a camera/gallery round-trip is in flight for the open find — device-check patch, Items 2/3. See [LogEntryDetailScreen]'s own doc comment on [onPhotoAcquisitionInFlightChanged]. */
    onPhotoAcquisitionInFlightChanged: (Boolean) -> Unit = {},
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBackToSearch: () -> Unit,
    /** Clears [MushroomLogUiState.saveErrorMessage] once its Toast (`JournalTab`'s) has shown — see [MushroomLogViewModel.onSaveErrorDismissed]. */
    onSaveErrorDismissed: () -> Unit,
    /** Threaded straight through into [CartographyScreen]'s own Album tab — the wide Journal's album, the one place photos are browsed and, since J6a, long-press deleted. */
    galleryPhotos: List<GalleryPhoto> = emptyList(),
    isLoadingGalleryPhotos: Boolean = false,
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = {},
    /** Standalone-photos dispatch: Camera/Gallery acquisition on [CartographyScreen]'s own Album tab. */
    onAddGalleryPhoto: (PhotoSource) -> Unit = {},
    galleryLoadErrorMessage: String? = null,
    galleryPhotoEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** See [CartographyScreen]'s own doc comment for all of the following — Journal Stage 2b's new entity. */
    cartographyUiState: CartographyUiState,
    onOpenCartographyEntry: (String) -> Unit,
    onStartCartographyEntry: (LocalDate) -> Unit,
    onCloseCartographyEntry: () -> Unit,
    onCartographyTextChanged: (String) -> Unit,
    onCartographyTagsChanged: (List<String>) -> Unit,
    onSetFindDecision: (String, Boolean) -> Unit,
    onSetTrackDecision: (String, Boolean) -> Unit,
    onSetWaypointDecision: (String, Boolean) -> Unit,
    onSetOfflineRegionDecision: (Long, Boolean) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    /** Entry-photo-acquisition dispatch, Item 2. See [CartographyScreen]'s own doc comment on this same parameter. */
    onAcquirePhotoForCartographyEntry: (PhotoSource) -> Unit = {},
    onFinishCartographyEntry: () -> Unit,
    /** Explicit Save for a committed Cartography entry — device-check patch, Item 1. See [CartographyEntryEditScreen]'s own doc comment. */
    onSaveCartographyEntry: () -> Unit = {},
    /** The leave-prompt's Discard option — device-check patch, Item 1. See [CartographyEntryEditScreen]'s own doc comment. */
    onDiscardCartographyEntryChanges: () -> Unit = {},
    /** The backgrounding-return prompt's "Save as draft" option — pending-edit-and-fixes dispatch, Item 1. See [CartographyScreen]'s own lifecycle-observer doc comment. */
    onSaveCartographyEntryAsDraft: () -> Unit = {},
    /**
     * Clears [CartographyUiState.saveErrorMessage] once its Toast (`JournalTab`'s) has shown, the day entries'
     * counterpart of [onSaveErrorDismissed] (intent `2026-09-28-68`, continuation `2026-09-28-76`).
     * The default is only for callers that host this on its own (tests); `AvailabilityScreen` passes
     * `CartographyViewModel.onSaveErrorDismissed`.
     */
    onCartographySaveErrorDismissed: () -> Unit = {},
    onDeleteCartographyEntry: (String) -> Unit,
    /** [CartographyEntryReportScreen]'s own map, Stage 2d — see that composable's doc comment. */
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    /** F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, for the Journal cards' thumbnails. */
    getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    /** [CartographyEntryReportScreen]'s own offline-map toggle, Stage 2e-i — see that composable's doc comment. */
    getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
    /** [CartographyEntryReportScreen]'s own fullscreen recenter button — fullscreen-maps dispatch, see that composable's own doc comment, "Fullscreen." */
    getCartographyEntryCurrentLocation: suspend () -> LocationResult,
    /** See [RecordsTab]'s own doc comment for all of the following — Stage 1's Records tab. */
    availabilityUiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    onOfflineMapLatChanged: (String) -> Unit,
    onOfflineMapLngChanged: (String) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onOfflineMapsOpened: () -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
    onDownloadAgain: (Long) -> Unit = {},
    tracks: List<Track>,
    onTracksOpened: () -> Unit,
    /** GPX full-record export dispatch — see [com.zynergylabs.forager.app.ui.track.TrackExportList]'s own doc comment. Defaults empty/no-op so no other caller of this panel changes. */
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    /** Part 2 follow-ups F1 item 5 (owner "Option A"): a finished track's swipe or details Delete asks for a pending delete with Undo; `null` (the default) leaves tracks without a delete. */
    onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed track delete failed and the track is back; shown above the Tracks list. */
    tracksErrorMessage: String? = null,
    waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** See this composable's own doc comment — Stage 2d. `null` (the default) is a no-op, so every other caller of this panel is unaffected. */
    pendingDestination: PendingJournalDestination? = null,
    /** M1: the find a [PendingJournalDestination.VIEW_FIND] request opens over this panel. */
    pendingFindId: String? = null,
    /** J8-4: the day entry a [PendingJournalDestination.VIEW_ENTRY] request opens in this panel. */
    pendingEntryId: String? = null,
    /** Fires once [pendingDestination] has been applied, so `AvailabilityScreen` clears its own copy and is ready for the next request. */
    onPendingDestinationConsumed: () -> Unit = {},
    /** J8-3: the entry report's "Show on map" and "Hide from map", threaded to [CartographyScreen]. `null` offers neither. */
    onSetCartographyEntryShownOnMap: ((entryId: String, shown: Boolean) -> Unit)? = null,
    /** Starts a find with no picked location, the Finds gallery's "+" tile (`JournalTab`'s `onStartEntry`). J6a: the wide Journal has the tile now. */
    onStartEntry: (LatLng?, LocalDate) -> Unit = { _, _ -> },
    /** The find report's Edit (`JournalTab`'s `onStartEditingEntry`); `AvailabilityScreen` passes `onStartEditingLogEntry`. */
    onStartEditingEntry: () -> Unit = {},
    /** A day entry's swipe Delete, a pending delete with Undo (J4b L2); `null` leaves the cards without the swipe. */
    onRequestDeleteCartographyEntry: ((String) -> Unit)? = null,
    /** An album photo's long-press Delete, a pending delete with Undo (J4b L3; J6a, ruling 10). `null` leaves the photos without the menu. */
    onRequestDeleteGalleryPhoto: ((String) -> Unit)? = null,
    /**
     * The Journal's user-set state, held above the tree so it outlives this panel (J6a, ruling 5; J10):
     * the top tab, the Records chip, the Timeline/Album view. `AvailabilityScreen` passes the same
     * holders it hands the compact tree, so a panel switch or a change of tree keeps them.
     */
    journalState: JournalScreenState = rememberJournalScreenState(),
    cartographyEntryModeState: MutableState<CartographyEntryMode> = remember { mutableStateOf(CartographyEntryMode.VIEW) },
    findEntryModeState: MutableState<JournalEntryMode> = remember { mutableStateOf(JournalEntryMode.REPORT) },
    findOverViewState: MutableState<FindOverView?> = remember { mutableStateOf(null) },
    /** Where an opened entry, find, record's details or picker is drawn: the whole right side (J6a, ruling 1). `null` draws them in place, in this column. */
    detailSlot: JournalDetailSlot? = null,
    modifier: Modifier = Modifier,
) {
    // J6a, ruling 5: the last step of one Back order, whichever way the Journal was reached. Registered
    // first, before anything under it, so every deeper handler (a picker, an editor, a report, the
    // details, Records to Entries, the album to the timeline, the drafts list) is registered later and
    // outranks it (the most recently registered enabled handler wins). It is what is left once the
    // Journal has nothing to unwind: the Search panel. It replaces the route-dependent reset that only
    // a map route reached (`AvailabilityScreen`'s `isDrawerOpen`), and nothing here is enabled at the
    // Search panel, where Back reaches the exit handler as before.
    BackHandler(onBack = onBackToSearch)

    Column(modifier = modifier.fillMaxWidth()) {
        LogHeader(onBack = onBackToSearch)

        // J6a: the phone's Journal (`JournalTab`: the Entries | Records switch, the report step and "+"
        // tile on finds, the counts, the delete paths, the state holders, F2 and F3), so the wide
        // Journal is one implementation with the phone's and the two cannot drift. What is the wide
        // tree's own is around it: the header above, and the detail slot (list-detail).
        JournalTab(
            uiState = uiState,
            onOpenCameraForLogEntry = onOpenCameraForLogEntry,
            onOpenCameraForAlbum = onOpenCameraForAlbum,
            onOpenCameraForCartographyEntry = onOpenCameraForCartographyEntry,
            mapSlot = mapSlot,
            pickerRegion = region,
            deviceLocation = deviceLocation,
            basemap = basemap,
            night = night,
            mapLayers = mapLayers,
            onMapLayerVisibilityChanged = onMapLayerVisibilityChanged,
            onOpenEntry = onOpenEntryForReport,
            onCloseEntry = onCloseEntry,
            onStartEntry = onStartEntry,
            onEntryChanged = onEntryChanged,
            onStartEditingEntry = onStartEditingEntry,
            onSaveEntry = onSaveEntry,
            onCancelEditing = onCancelEditing,
            onLeaveEditingIncidentally = onLeaveEditingIncidentally,
            onPhotoAcquisitionInFlightChanged = onPhotoAcquisitionInFlightChanged,
            onAddPhoto = onAddPhoto,
            onRemovePhoto = onRemovePhoto,
            onPullPhoto = onPullPhoto,
            onDeleteEntry = onDeleteEntry,
            onSaveErrorDismissed = onSaveErrorDismissed,
            galleryPhotos = galleryPhotos,
            isLoadingGalleryPhotos = isLoadingGalleryPhotos,
            onDeleteGalleryPhoto = onDeleteGalleryPhoto,
            onAddGalleryPhoto = onAddGalleryPhoto,
            galleryLoadErrorMessage = galleryLoadErrorMessage,
            galleryPhotoEntryReferenceCounts = galleryPhotoEntryReferenceCounts,
            cartographyUiState = cartographyUiState,
            onOpenCartographyEntry = onOpenCartographyEntry,
            onStartCartographyEntry = onStartCartographyEntry,
            onCloseCartographyEntry = onCloseCartographyEntry,
            onCartographyTextChanged = onCartographyTextChanged,
            onCartographyTagsChanged = onCartographyTagsChanged,
            onSetFindDecision = onSetFindDecision,
            onSetTrackDecision = onSetTrackDecision,
            onSetWaypointDecision = onSetWaypointDecision,
            onSetOfflineRegionDecision = onSetOfflineRegionDecision,
            onToggleKeptPhoto = onToggleKeptPhoto,
            onAcquirePhotoForCartographyEntry = onAcquirePhotoForCartographyEntry,
            onFinishCartographyEntry = onFinishCartographyEntry,
            onSaveCartographyEntry = onSaveCartographyEntry,
            onDiscardCartographyEntryChanges = onDiscardCartographyEntryChanges,
            onSaveCartographyEntryAsDraft = onSaveCartographyEntryAsDraft,
            onCartographySaveErrorDismissed = onCartographySaveErrorDismissed,
            onDeleteCartographyEntry = onDeleteCartographyEntry,
            onRequestDeleteCartographyEntry = onRequestDeleteCartographyEntry,
            onOpenEntryForEditing = onOpenEntryForEditing,
            onRequestDeleteGalleryPhoto = onRequestDeleteGalleryPhoto,
            getCartographyEntryMapData = getCartographyEntryMapData,
            getSavedTrackPaths = getSavedTrackPaths,
            getCartographyEntryOfflineRegion = getCartographyEntryOfflineRegion,
            getCartographyEntryCurrentLocation = getCartographyEntryCurrentLocation,
            availabilityUiState = availabilityUiState,
            distanceUnit = distanceUnit,
            currentTime = currentTime,
            onOfflineMapLatChanged = onOfflineMapLatChanged,
            onOfflineMapLngChanged = onOfflineMapLngChanged,
            onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
            onOfflineMapNameChanged = onOfflineMapNameChanged,
            onOfflineMapsOpened = onOfflineMapsOpened,
            onDownloadOfflineMaps = onDownloadOfflineMaps,
            onDeleteOfflineRegion = onDeleteOfflineRegion,
            onDownloadAgain = onDownloadAgain,
            tracks = tracks,
            onTracksOpened = onTracksOpened,
            getFullRecord = getFullRecord,
            waypoints = waypoints,
            waypointsErrorMessage = waypointsErrorMessage,
            onDeleteWaypoint = onDeleteWaypoint,
            onDeleteTrack = onDeleteTrack,
            tracksErrorMessage = tracksErrorMessage,
            waypointEntryReferenceCounts = waypointEntryReferenceCounts,
            pendingDestination = pendingDestination,
            pendingFindId = pendingFindId,
            pendingEntryId = pendingEntryId,
            onPendingDestinationConsumed = onPendingDestinationConsumed,
            onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
            journalState = journalState,
            cartographyEntryModeState = cartographyEntryModeState,
            findEntryModeState = findEntryModeState,
            findOverViewState = findOverViewState,
            detailSlot = detailSlot,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The row above the Journal: a back arrow to the Search panel, labelled with the phone's name for this
 * tab, "Journal" (its bottom-bar label; owner's ruling, `prompts/preserved/2026-09-29-25.md`, replacing
 * "Mushroom Log"). Mirrors `AvailabilityScreen`'s `SettingsHeader` shape exactly — see that composable's
 * own call site for why.
 */
@Composable
private fun LogHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to search options")
        Text("Journal", style = MaterialTheme.typography.titleMedium)
    }
}
