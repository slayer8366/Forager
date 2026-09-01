package com.forager.app.ui.log

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.PhotoSource
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.Waypoint
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.ui.availability.AvailabilityUiState
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.CentrePinLocationPicker
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.theme.Spacing
import java.time.LocalDate

/**
 * The mushroom log's drawer destination — one of the ModalNavigationDrawer's panels in
 * `AvailabilityScreen`, reached the same way `DrawerPanel.Settings` is (see that file's
 * `DrawerPanel` enum and `SettingsEntryRow`'s call site).
 *
 * **Two tabs (journal restructure Stage 1), same shell as [JournalTab]'s compact equivalent** —
 * see that composable's own doc comment for the owner's "logbook vs. journal" framing this
 * mirrors, and for Stage 2b's restructure (Cartography's new [CartographyScreen], Finds relocated
 * into [RecordsTab]) this panel gained identically. This window class had no Records surface of
 * any kind before Stage 1 (unlike compact, where Waypoints/Offline Maps/Recorded Tracks were at
 * least reachable, just scattered across the Tools drawer and Settings) — `SearchControls`' own
 * Waypoints section and `DrawerPanel`'s `OfflineMaps`/`Tracks` panels were siblings of [Log], not
 * inside it, so giving this panel a Records tab is this window class's first time surfacing them
 * from here at all. [selectedTopTab] is local `remember` state, not a nav destination — this
 * codebase has no navigation library.
 *
 * **Finds relocated into Records, working exactly as they did in Cartography before this
 * dispatch** — [pickingLocationForEditingEntry]/[pullingPhotoForEditingEntry] are unchanged from
 * Stage 1, just rendered into [RecordsTab]'s `findsContent` slot instead of directly into a
 * "Cartography" tab. This panel never had a report step (see the "no separate report step" note
 * below), so its relocated Finds section has no `mode` the way [JournalTab]'s does.
 *
 * [uiState].editingEntry is the relocated Finds section's own list/detail navigation state — see
 * [MushroomLogUiState]'s doc comment.
 *
 * [mapSlot]/[region]/[basemap] serve two things now: [LogEntryDetailScreen]'s "Add Location"
 * picker (Workstream L4, `docs/plans/pr26-rework.md`) as before, and the Records tab's own Offline
 * Maps region picker (Stage 1) — [JournalTab]'s own identical dual use, mirrored here since both
 * composables render the same [LogEntryDetailScreen]/[RecordsTab]. This window class has no
 * entry-creation entry point of its own (`LogEntryListScreen`, this panel's list state, has no "+"
 * tile — see that composable's own doc comment); an entry only ever arrives here already created,
 * via the map's "Log a find" option, so the picker below only ever edits an existing entry's
 * location, never places one for a not-yet-created entry the way it once did.
 *
 * **Leaving Records mid-find-edit is an incidental exit** — see [JournalTab]'s own doc comment for
 * the identical inverted-from-Stage-1 guard, mirrored here: [onLeaveEditingIncidentally] fires
 * whenever the top-level tab switches away from Records, or [RecordsTab]'s own sub-tab switches
 * away from Finds, while [MushroomLogUiState.editingEntry] is non-null.
 */
@Composable
internal fun LogPanel(
    uiState: MushroomLogUiState,
    cameraCaptureFiles: CameraCaptureFiles,
    mapSlot: MapSlot,
    region: Region,
    basemap: Basemap,
    /** Night mode for the location picker this hosts, and the Records tab's Offline Maps picker — see [CentrePinLocationPicker]. */
    night: Boolean = false,
    /**
     * Opens a row and, if it's a committed entry, immediately begins editing it — one atomic
     * ViewModel operation ([MushroomLogViewModel.onOpenEntryForEditing]), not this composable
     * calling separate open/start-editing callbacks itself. This panel has no report step (see its
     * own doc comment), so opening a row always means "edit it" — see
     * [MushroomLogViewModel.onOpenEntryForEditing]'s own doc comment for why that combination lives
     * in the ViewModel rather than being composed here from two calls.
     */
    onOpenEntryForEditing: (String) -> Unit,
    onCloseEntry: () -> Unit,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onSaveEntry: () -> Unit,
    onCancelEditing: () -> Unit,
    onLeaveEditingIncidentally: () -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onBackToSearch: () -> Unit,
    /** Clears [MushroomLogUiState.saveErrorMessage] once its Toast (below) has shown — see [MushroomLogViewModel.onSaveErrorDismissed]. */
    onSaveErrorDismissed: () -> Unit,
    /** Threaded straight through into [CartographyScreen]'s own Album tab — this window class's first time showing it directly (it previously only reached the standalone photo library via `DrawerPanel.PhotoGallery`, a sibling panel, not this one). */
    galleryPhotos: List<GalleryPhoto> = emptyList(),
    isLoadingGalleryPhotos: Boolean = false,
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = {},
    galleryLoadErrorMessage: String? = null,
    galleryPhotoEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** See [CartographyScreen]'s own doc comment for all of the following — Journal Stage 2b's new entity. */
    cartographyUiState: CartographyUiState,
    onOpenCartographyEntry: (String) -> Unit,
    onStartCartographyEntry: (LocalDate) -> Unit,
    onCloseCartographyEntry: () -> Unit,
    onCartographyTextChanged: (String) -> Unit,
    onCartographyTagsChanged: (List<String>) -> Unit,
    onToggleKeptFind: (String) -> Unit,
    onToggleKeptTrack: (String) -> Unit,
    onToggleKeptWaypoint: (String) -> Unit,
    onToggleKeptOfflineRegion: (Long) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    onFinishCartographyEntry: () -> Unit,
    onDeleteCartographyEntry: (String) -> Unit,
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
    tracks: List<Track>,
    onTracksOpened: () -> Unit,
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    // Same one-shot-per-transition Toast shape as CompactMapTab's startRecordingErrorMessage
    // effect (AvailabilityScreen.kt) — belief-changing per docs/error-presentation-spec.md, so it
    // is told rather than absorbed. Unlike that field, this one also clears itself right after
    // showing: saveErrorMessage has no dismiss affordance to clear it from (the spec calls the
    // Toast provisional, explicitly ruling one out), so "cleared on dismiss" means clearing it the
    // moment the one-shot Toast has been shown — the other half of "dismiss or next successful
    // save, whichever comes first" is the five write sites in MushroomLogViewModel clearing it
    // themselves on success.
    val context = LocalContext.current
    LaunchedEffect(uiState.saveErrorMessage) {
        uiState.saveErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onSaveErrorDismissed()
        }
    }

    var pickingLocationForEditingEntry by remember { mutableStateOf(false) }
    // Same shape as pickingLocationForEditingEntry, for LogEntryDetailScreen's "From Album" button
    // (Workstream G3) instead of "Add Location".
    var pullingPhotoForEditingEntry by remember { mutableStateOf(false) }
    val editing = uiState.editingEntry

    var selectedTopTab by remember { mutableStateOf(JournalTopTab.CARTOGRAPHY) }

    // See this composable's own doc comment on "leaving Records mid-find-edit" for why this now
    // guards leaving Records (inverted from Stage 1, which guarded leaving Cartography — finds
    // lived there then).
    fun leaveFindEditingIfNeeded() {
        if (editing != null) onLeaveEditingIncidentally()
    }

    // This panel had no BackHandler before pickers existed — nothing here previously needed to
    // intercept system back, since the drawer's own chrome handled it. Workstream L4b adds the
    // editing != null branch: back out of an open entry (with no picker open) is "leaving without
    // answering," the same incidental-exit auto-save as the form's own back arrow, a tab switch, or
    // backgrounding — never Cancel, which only the form's explicit button triggers. This closes a
    // gap the L4b scoping pulse found: this panel previously had no way to close an open entry via
    // back at all, falling through to whatever AvailabilityScreen's top-level handling did instead.
    BackHandler(enabled = editing != null || pickingLocationForEditingEntry || pullingPhotoForEditingEntry) {
        when {
            pickingLocationForEditingEntry -> pickingLocationForEditingEntry = false
            pullingPhotoForEditingEntry -> pullingPhotoForEditingEntry = false
            editing != null -> onLeaveEditingIncidentally()
        }
    }

    // Journal Stage 2b, relocated verbatim from this panel's own former Cartography branch — see
    // this composable's own doc comment.
    val findsSection: @Composable ColumnScope.() -> Unit = {
        if (editing != null && pickingLocationForEditingEntry) {
            CentrePinLocationPicker(
                mapSlot = mapSlot,
                region = region,
                basemap = basemap,
                night = night,
                onConfirm = { location ->
                    pickingLocationForEditingEntry = false
                    onEntryChanged(editing.copy(foundAt = location))
                },
                onCancel = { pickingLocationForEditingEntry = false },
                modifier = Modifier.weight(1f),
            )
        } else if (editing != null && pullingPhotoForEditingEntry) {
            PullPhotoPickerScreen(
                photos = uiState.galleryPhotos,
                onPhotoSelected = { photo ->
                    pullingPhotoForEditingEntry = false
                    onPullPhoto(photo)
                },
                modifier = Modifier.weight(1f),
            )
        } else if (editing != null) {
            LogEntryDetailScreen(
                entry = editing,
                cameraCaptureFiles = cameraCaptureFiles,
                onEntryChanged = onEntryChanged,
                onAddPhoto = onAddPhoto,
                onRemovePhoto = onRemovePhoto,
                onPullPhoto = { pullingPhotoForEditingEntry = true },
                onAddLocation = { pickingLocationForEditingEntry = true },
                onSave = onSaveEntry,
                onCancel = onCancelEditing,
                onDeleteEntry = { onDeleteEntry(editing.id) },
                onBack = onLeaveEditingIncidentally,
                modifier = Modifier.weight(1f),
            )
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LogEntryListScreen(
                    entries = uiState.entries,
                    draftEntries = uiState.draftEntries,
                    isLoading = uiState.isLoadingEntries,
                    // This panel has no separate report step (see its own doc comment) —
                    // opening an entry goes straight to LogEntryDetailScreen above, so it must
                    // already be a draft by the time that happens. onOpenEntryForEditing is a
                    // no-op-shaped success for a row already a draft and creates the draft row
                    // for a committed one — correct for either case on its own, so
                    // LogEntryListScreen's onOpenDraftEntry can default to this same callback
                    // (its own default) rather than needing an override here.
                    onOpenEntry = onOpenEntryForEditing,
                    modifier = Modifier.weight(1f),
                    loadErrorMessage = uiState.loadErrorMessage,
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LogHeader(onBack = onBackToSearch)

        SecondaryTabRow(selectedTabIndex = selectedTopTab.ordinal) {
            Tab(
                selected = selectedTopTab == JournalTopTab.CARTOGRAPHY,
                onClick = {
                    // Leaving Records mid-find-edit for Cartography is an incidental exit — see
                    // this composable's own doc comment.
                    leaveFindEditingIfNeeded()
                    selectedTopTab = JournalTopTab.CARTOGRAPHY
                },
                text = { Text("Cartography") },
            )
            Tab(
                selected = selectedTopTab == JournalTopTab.RECORDS,
                onClick = { selectedTopTab = JournalTopTab.RECORDS },
                text = { Text("Records") },
            )
        }

        when (selectedTopTab) {
            JournalTopTab.CARTOGRAPHY -> CartographyScreen(
                uiState = cartographyUiState,
                galleryPhotos = galleryPhotos,
                isLoadingGalleryPhotos = isLoadingGalleryPhotos,
                galleryLoadErrorMessage = galleryLoadErrorMessage,
                galleryPhotoEntryReferenceCounts = galleryPhotoEntryReferenceCounts,
                onDeleteGalleryPhoto = onDeleteGalleryPhoto,
                distanceUnit = distanceUnit,
                onOpenEntry = onOpenCartographyEntry,
                onStartEntry = onStartCartographyEntry,
                onCloseEntry = onCloseCartographyEntry,
                onTextChanged = onCartographyTextChanged,
                onTagsChanged = onCartographyTagsChanged,
                onToggleKeptFind = onToggleKeptFind,
                onToggleKeptTrack = onToggleKeptTrack,
                onToggleKeptWaypoint = onToggleKeptWaypoint,
                onToggleKeptOfflineRegion = onToggleKeptOfflineRegion,
                onToggleKeptPhoto = onToggleKeptPhoto,
                onFinishEntry = onFinishCartographyEntry,
                onDeleteEntry = onDeleteCartographyEntry,
                // Expanded gets more grid columns, per owner decision #3 ("more of the same thing
                // at once") — see CartographyScreen's own doc comment.
                columns = EXPANDED_CARTOGRAPHY_COLUMNS,
                modifier = Modifier.weight(1f),
            )

            JournalTopTab.RECORDS -> RecordsTab(
                modifier = Modifier.weight(1f),
                waypoints = waypoints,
                waypointsErrorMessage = waypointsErrorMessage,
                onDeleteWaypoint = onDeleteWaypoint,
                waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                availabilityUiState = availabilityUiState,
                distanceUnit = distanceUnit,
                currentTime = currentTime,
                mapSlot = mapSlot,
                night = night,
                onOfflineMapRegionPicked = { location ->
                    onOfflineMapLatChanged(location.lat.toString())
                    onOfflineMapLngChanged(location.lng.toString())
                },
                onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                onOfflineMapNameChanged = onOfflineMapNameChanged,
                onOfflineMapsOpened = onOfflineMapsOpened,
                onDownloadOfflineMaps = onDownloadOfflineMaps,
                onDeleteOfflineRegion = onDeleteOfflineRegion,
                tracks = tracks,
                onTracksOpened = onTracksOpened,
                findsContent = findsSection,
                onFindsTabLeft = ::leaveFindEditingIfNeeded,
            )
        }
    }
}

/** Mirrors `AvailabilityScreen`'s `SettingsHeader` shape exactly — see that composable's own call site for why. */
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
        Text("Mushroom Log", style = MaterialTheme.typography.titleMedium)
    }
}

/** 3, not [CartographyEntryListScreen]'s own compact default of 2 — the medium/expanded drawer panel is wider, so "more of the same thing at once" (owner decision #3) means one more grid column, not a different arrangement. */
private const val EXPANDED_CARTOGRAPHY_COLUMNS = 3
