package com.forager.app.ui.log

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
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
import java.time.LocalDate

/**
 * The compact bottom nav's Journal destination — two tabs (journal restructure Stage 1): the
 * project owner's own framing, "**Records is a logbook** — raw, complete, machine-generated data.
 * **Cartography is a journal** — what the user chose to write down and keep." [selectedTopTab]
 * picks between them with the same [SecondaryTabRow] pattern [LogGalleryScreen]'s own
 * `LogGalleryTab` already established — this codebase has no navigation library, so, like every
 * other "route" here, this is a private enum plus local `remember` state, not a real destination.
 *
 * **Cartography, Stage 1: unchanged content, new wrapper only.** Everything this composable
 * rendered before this tab existed — [LogGalleryScreen] by default, an entry's own
 * [LogEntryReportScreen] once one is opened from the gallery, [LogEntryDetailScreen] once "Edit
 * entry" is chosen from the report (or immediately, for a brand-new entry — see [mode]'s doc
 * comment), and [CentrePinLocationPicker]/[PullPhotoPickerScreen] while placing a location or
 * pulling a photo for the entry currently open in [LogEntryDetailScreen]) — is exactly what the
 * Cartography tab shows now. Restructuring Cartography's own internals (Entries/Drafts submenus,
 * trip containers) is Stage 2, explicitly not built here.
 *
 * **Records, new in Stage 1:** [RecordsTab] — Waypoints, Offline Maps, Recorded Tracks, relocated
 * from Settings/the Tools drawer unmodified. See that composable's own doc comment.
 *
 * **Switching away from Cartography mid-edit is an incidental exit**, the same as switching bottom
 * nav tabs away from Journal entirely, backgrounding, or the entry form's own back arrow (see
 * [MushroomLogViewModel.onLeaveEditingIncidentally]'s doc comment on the three exits this is now a
 * fourth of) — [onLeaveEditingIncidentally] fires before the tab switch takes effect whenever
 * [editing] is non-null and [mode] is [JournalEntryMode.EDIT], the same guard the bottom nav's own
 * tab-switch handler already uses in `AvailabilityScreen.kt`.
 *
 * Workstream L4 (`docs/plans/pr26-rework.md`): the gallery's "+" tile now goes straight to the edit
 * form with no location placed at all — [MushroomLogEntry.foundAt]'s own doc comment covers why
 * that's representable since L3. [LogEntryLocationPicker] (the old "pick a location, *then* create
 * the entry" screen) is deleted, not converted — there is no step left before the entry page for it
 * to occupy. The centre-pin picker still exists here, just retargeted: it now sets a location on an
 * *already-open* entry, reached via [LogEntryDetailScreen]'s own "Add Location" button rather than
 * the gallery's "+".
 *
 * This exists alongside [LogPanel] rather than replacing it: [LogPanel] is still what the
 * medium/expanded window's drawer shows (`DrawerPanel.Log` in `AvailabilityScreen.kt`), and gained
 * the identical two-tab restructure as part of this same Stage 1 dispatch — see that composable's
 * own doc comment for why it needed the same change to avoid becoming a half-done implementation.
 * This is the compact-only equivalent, reached from the bottom nav instead of the drawer, so it
 * owns no "back to search" affordance — there is no drawer to return to, only another bottom nav
 * tab to tap.
 *
 * Workstream G3: [LogEntryDetailScreen]'s "From Album" button opens [PullPhotoPickerScreen] the
 * same way "Add Location" opens [CentrePinLocationPicker] — a full-screen state swap owned by this
 * tab, not embedded in the form. [LogPanel] gained the identical picker state for the same reason
 * it gained the location one.
 *
 * [onStartEntry] is the exact same handler the map's "Log a find" option calls (see
 * `AvailabilityScreen.kt`'s `onLogFindHere`) — that option still collects a location via its own map
 * confirmation before calling it (owner decision, 2026-08-22: "'Log a find' keeps its map
 * confirmation and arrives at the entry page with the location filled in. Journal '+' arrives with
 * none. Both end on the entry page — that is what 'same flow' meant."), so [onStartEntry]'s
 * `location` parameter is nullable to serve both callers, not because this tab itself ever passes a
 * non-null one.
 */
@Composable
internal fun JournalTab(
    uiState: MushroomLogUiState,
    cameraCaptureFiles: CameraCaptureFiles,
    mapSlot: MapSlot,
    pickerRegion: Region,
    basemap: Basemap,
    /** Night mode for the location picker this hosts, and the Records tab's Offline Maps picker — see [CentrePinLocationPicker]. */
    night: Boolean = false,
    onOpenEntry: (String) -> Unit,
    onCloseEntry: () -> Unit,
    onStartEntry: (LatLng?, LocalDate) -> Unit,
    onEntryChanged: (MushroomLogEntry) -> Unit,
    onStartEditingEntry: () -> Unit,
    onSaveEntry: () -> Unit,
    onCancelEditing: () -> Unit,
    /**
     * Leaving without answering (Workstream L4b-R) — see [MushroomLogViewModel.onLeaveEditingIncidentally]'s
     * own doc comment. Callers wrap this to offer a dismissible "Discard" action (Gmail-drafts-style)
     * around every incidental exit uniformly — see `AvailabilityScreen`'s own construction of this
     * callback, shared across this tab, [LogPanel], and the compact bottom nav's tab-switch handler,
     * so the same Snackbar covers every exit path from one place rather than three.
     */
    onLeaveEditingIncidentally: () -> Unit,
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    /** Clears [MushroomLogUiState.saveErrorMessage] once its Toast (below) has shown — see [LogPanel]'s identical parameter for the full reasoning. */
    onSaveErrorDismissed: () -> Unit,
    /**
     * The Album tab folded into [LogGalleryScreen] (map/navigation redesign dispatch B) — see that
     * composable's own doc comment. Threaded straight through from [MushroomLogUiState], the same
     * data the now-removed standalone `CompactTab.PHOTOS` destination used to receive directly.
     */
    galleryPhotos: List<GalleryPhoto> = emptyList(),
    isLoadingGalleryPhotos: Boolean = false,
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = {},
    galleryLoadErrorMessage: String? = null,
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
    // See LogPanel's identical effect for why this both shows and immediately clears the field.
    val context = LocalContext.current
    LaunchedEffect(uiState.saveErrorMessage) {
        uiState.saveErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onSaveErrorDismissed()
        }
    }

    // REPORT for an entry opened from the gallery (there's something to compile a report from and
    // no reason to assume an edit is wanted), EDIT for one just started (nothing to report yet, so
    // reporting first would just be an empty screen between creation and the form the user is there
    // for). Reset to REPORT whenever a *different* entry becomes the open one, so returning to an
    // entry after editing shows the freshly-recompiled report rather than staying in edit mode.
    var mode by remember { mutableStateOf(JournalEntryMode.REPORT) }
    val editing = uiState.editingEntry

    // Only meaningful while editing — set by LogEntryDetailScreen's own "Add Location" button, read
    // back down here rather than hoisted into MushroomLogUiState, since no other consumer of that
    // state needs to know a location picker happens to be open mid-edit.
    var pickingLocationForEditingEntry by remember { mutableStateOf(false) }

    // Same shape as pickingLocationForEditingEntry, for LogEntryDetailScreen's "From Album" button
    // (Workstream G3) instead of "Add Location".
    var pullingPhotoForEditingEntry by remember { mutableStateOf(false) }

    var selectedTopTab by remember { mutableStateOf(JournalTopTab.CARTOGRAPHY) }

    // System back unwinds one of this tab's own nested states before AvailabilityScreen's
    // top-level "switch away from a non-Maps tab" handler ever sees it — Compose's
    // OnBackPressedDispatcher tries the most-recently-composed enabled callback first, so this one
    // (composed as part of the Journal tab's own content) naturally takes priority. Mirrors the
    // `when` below's own branch order: out of a picker to the edit form, out of the edit form to
    // the report, out of the report to the gallery.
    //
    // Workstream L4b (corrected 2026-08-25, L4b-R): back out of EDIT mode is "leaving without
    // answering" — the same neither-commits-nor-discards exit as the back arrow inside
    // LogEntryDetailScreen, tab switch, and backgrounding (see MushroomLogViewModel's own doc
    // comment on the three exits) — never Cancel, which only the form's own explicit button
    // triggers.
    BackHandler(enabled = editing != null || pickingLocationForEditingEntry || pullingPhotoForEditingEntry) {
        when {
            pickingLocationForEditingEntry -> pickingLocationForEditingEntry = false
            pullingPhotoForEditingEntry -> pullingPhotoForEditingEntry = false
            editing != null && mode == JournalEntryMode.EDIT -> onLeaveEditingIncidentally()
            editing != null -> onCloseEntry()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTopTab.ordinal) {
            Tab(
                selected = selectedTopTab == JournalTopTab.CARTOGRAPHY,
                onClick = { selectedTopTab = JournalTopTab.CARTOGRAPHY },
                text = { Text("Cartography") },
            )
            Tab(
                selected = selectedTopTab == JournalTopTab.RECORDS,
                onClick = {
                    // Leaving Cartography mid-edit for Records is an incidental exit — see this
                    // composable's own doc comment.
                    if (editing != null && mode == JournalEntryMode.EDIT) onLeaveEditingIncidentally()
                    selectedTopTab = JournalTopTab.RECORDS
                },
                text = { Text("Records") },
            )
        }

        when (selectedTopTab) {
            JournalTopTab.CARTOGRAPHY -> when {
                editing != null && mode == JournalEntryMode.EDIT && pickingLocationForEditingEntry -> CentrePinLocationPicker(
                    mapSlot = mapSlot,
                    region = pickerRegion,
                    basemap = basemap,
                    night = night,
                    onConfirm = { location ->
                        pickingLocationForEditingEntry = false
                        onEntryChanged(editing.copy(foundAt = location))
                    },
                    onCancel = { pickingLocationForEditingEntry = false },
                    modifier = Modifier.weight(1f),
                )

                editing != null && mode == JournalEntryMode.EDIT && pullingPhotoForEditingEntry -> PullPhotoPickerScreen(
                    photos = uiState.galleryPhotos,
                    onPhotoSelected = { photo ->
                        pullingPhotoForEditingEntry = false
                        onPullPhoto(photo)
                    },
                    modifier = Modifier.weight(1f),
                )

                editing != null && mode == JournalEntryMode.EDIT -> LogEntryDetailScreen(
                    entry = editing,
                    cameraCaptureFiles = cameraCaptureFiles,
                    onEntryChanged = onEntryChanged,
                    onAddPhoto = onAddPhoto,
                    onRemovePhoto = onRemovePhoto,
                    onPullPhoto = { pullingPhotoForEditingEntry = true },
                    onAddLocation = { pickingLocationForEditingEntry = true },
                    onSave = { onSaveEntry(); mode = JournalEntryMode.REPORT },
                    onCancel = onCancelEditing,
                    onDeleteEntry = { onDeleteEntry(editing.id) },
                    onBack = onLeaveEditingIncidentally,
                    modifier = Modifier.weight(1f),
                )

                editing != null -> LogEntryReportScreen(
                    entry = editing,
                    onEdit = {
                        onStartEditingEntry()
                        mode = JournalEntryMode.EDIT
                    },
                    onDeleteEntry = { onDeleteEntry(editing.id) },
                    onBack = onCloseEntry,
                    modifier = Modifier.weight(1f),
                )

                else -> LogGalleryScreen(
                    entries = uiState.entries,
                    draftEntries = uiState.draftEntries,
                    isLoading = uiState.isLoadingEntries,
                    onOpenEntry = { id ->
                        mode = JournalEntryMode.REPORT
                        onOpenEntry(id)
                    },
                    onOpenDraftEntry = { id ->
                        // Workstream L4b-R: reinstates straight into EDIT, not REPORT — a draft (live,
                        // incidentally-exited, or crash-orphaned; see MushroomLogUiState.draftEntries) is
                        // inherently something to finish, not something to view a report of yet. No
                        // onStartEditingEntry() call needed: it's already a draft, so that would be a no-op.
                        mode = JournalEntryMode.EDIT
                        onOpenEntry(id)
                    },
                    onAddEntry = {
                        mode = JournalEntryMode.EDIT
                        onStartEntry(null, LocalDate.now())
                    },
                    modifier = Modifier.weight(1f),
                    loadErrorMessage = uiState.loadErrorMessage,
                    photos = galleryPhotos,
                    isLoadingPhotos = isLoadingGalleryPhotos,
                    onDeletePhoto = onDeleteGalleryPhoto,
                    photosLoadErrorMessage = galleryLoadErrorMessage,
                )
            }

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
            )
        }
    }
}

/**
 * Which of the Journal's two tabs is selected — Cartography (the existing journal content,
 * unchanged in Stage 1) or Records (new). Shared between [JournalTab] (compact) and [LogPanel]
 * (medium/expanded), which both use this same two-tab shell — `internal`, not `private`, for
 * exactly that reuse; declared once here rather than in each file, since Kotlin does not allow two
 * files in the same package to each declare a file-private top-level type of the same name.
 */
internal enum class JournalTopTab { CARTOGRAPHY, RECORDS }

/**
 * Which screen [JournalTab] shows for [MushroomLogUiState.editingEntry] — "editing" is the accurate
 * name for what that field means (see [MushroomLogViewModel]'s doc comment on the persisted-draft
 * model), but which
 * of [LogEntryReportScreen]/[LogEntryDetailScreen] the *user* sees for it depends on how they got
 * there, tracked here rather than inferred from the entry's own content (an entry with nothing
 * recorded yet is a legitimate thing to view a report of too, once the user backs out of editing it
 * without filling anything in — REPORT stays correct for that case where "does it have data" would
 * not).
 */
private enum class JournalEntryMode { REPORT, EDIT }
