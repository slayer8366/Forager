package com.forager.app.ui.log

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.CartographyEntry
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
 * **Cartography is where those records are compiled into a coherent story.**" [selectedTopTab]
 * picks between them with the same [SecondaryTabRow] pattern [RecordsTab] already established —
 * this codebase has no navigation library, so, like every other "route" here, this is a private
 * enum plus local `remember` state, not a real destination.
 *
 * **Cartography, Stage 2b: [CartographyScreen], a new authored entity's own Entries/Drafts/Album
 * submenus** — see that composable's own doc comment. Distinct from browsing raw
 * [MushroomLogEntry] finds, which moved into [RecordsTab] as its fourth submenu
 * (`amendment-2b-finds-and-trash.md`).
 *
 * **Finds relocated into Records, working exactly as they did in Cartography before this
 * dispatch** — [mode]/[pickingLocationForEditingEntry]/[pullingPhotoForEditingEntry] are unchanged
 * from Stage 1, just rendered into [RecordsTab]'s `findsContent` slot instead of directly into a
 * "Cartography" tab. See [findsSection]'s own local definition below.
 *
 * **Leaving Records mid-find-edit is an incidental exit** — inverted from Stage 1's own version of
 * this rule (which fired on leaving *Cartography*, back when finds lived there): now that finds live
 * *inside* Records, [onLeaveEditingIncidentally] fires whenever the top-level tab switches away from
 * Records **or** [RecordsTab]'s own sub-tab switches away from Finds to a sibling sub-tab (via
 * [RecordsTab]'s `onFindsTabLeft`) — a scenario that didn't exist before this move, since Finds now
 * has Records' three other submenus as new siblings it didn't have as a top-level Cartography tab.
 *
 * Workstream L4 (`docs/plans/pr26-rework.md`): the gallery's "+" tile now goes straight to the edit
 * form with no location placed at all — [MushroomLogEntry.foundAt]'s own doc comment covers why
 * that's representable since L3. The centre-pin picker sets a location on an *already-open* entry,
 * reached via [LogEntryDetailScreen]'s own "Add Location" button.
 *
 * This exists alongside [LogPanel] rather than replacing it: [LogPanel] is still what the
 * medium/expanded window's drawer shows (`DrawerPanel.Log` in `AvailabilityScreen.kt`), and gained
 * the identical restructure — see that composable's own doc comment. This is the compact-only
 * equivalent, reached from the bottom nav instead of the drawer, so it owns no "back to search"
 * affordance — there is no drawer to return to, only another bottom nav tab to tap.
 *
 * [onStartEntry] is the exact same handler the map's "Log a find" option calls (see
 * `AvailabilityScreen.kt`'s `onLogFindHere`) — that option still collects a location via its own map
 * confirmation before calling it (owner decision, 2026-08-22: "'Log a find' keeps its map
 * confirmation and arrives at the entry page with the location filled in. Journal '+' arrives with
 * none. Both end on the entry page — that is what 'same flow' meant."), so [onStartEntry]'s
 * `location` parameter is nullable to serve both callers, not because this tab itself ever passes a
 * non-null one.
 *
 * ## The map "+" routing bug, and why the fix is two local one-shot latches, not a shared navigation type (Stage 2d)
 *
 * `onLogFindHere` used to switch only `AvailabilityScreen`'s own `compactTab`, leaving this tab's
 * own [selectedTopTab] (defaults to [JournalTopTab.CARTOGRAPHY]) and [RecordsTab]'s own
 * `selectedTab` (defaults to `RecordsSubTab.WAYPOINTS`) untouched — landing the user on Cartography
 * instead of the find form a device report found. The picked location was never lost (it lands
 * correctly in [MushroomLogUiState.editingEntry] via [onStartEntry] either way); the bug is purely
 * that nothing steered the three-plus layers of local `remember` navigation state this app's
 * no-navigation-library convention has accumulated (this tab's own [selectedTopTab] and [mode],
 * [RecordsTab]'s own `selectedTab`, `LogPanel`'s parallel copies) past the first.
 *
 * [pendingDestination] is the external half of the fix: a single-purpose, one-shot request
 * [AvailabilityScreen] sets and this tab consumes, in the same request-token shape
 * [com.forager.app.ui.map.MapOverlayContent.resumeTrackingRequestId] already establishes elsewhere
 * in this app (a value the requester sets and the consumer clears, not a shared destination type
 * layered on top of the three enums already here). Consuming it sets [selectedTopTab]/[mode]
 * directly, and stages [RecordsSubTab.FINDS] into `recordsPendingSubTab` — a second, *local* latch,
 * not [pendingDestination] forwarded as-is, because [RecordsTab] only mounts once [selectedTopTab]
 * has already flipped to [JournalTopTab.RECORDS] on this same recomposition; clearing
 * [pendingDestination] immediately (so [AvailabilityScreen] is ready for the next request) would
 * otherwise race [RecordsTab] ever seeing a non-null value.
 *
 * **Deliberately not generalized into a shared navigation abstraction.** This is now the fourth
 * instance of "a local enum plus `remember` state stands in for a route" in this codebase
 * ([JournalTopTab], [JournalEntryMode], `RecordsSubTab`, and now [PendingJournalDestination] making
 * a fifth if the picker's own local state is counted separately) — real, visible debt, logged here
 * rather than fixed, since Stage 2c's own [CartographyEntryMode] split this same dispatch touches
 * has not yet been verified on a device, and this dispatch is already the widest since Stage 2b.
 * Consolidating the pattern is a legitimate future dispatch, not a quiet side effect of this one.
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
    /** Reports whether a camera/gallery round-trip is in flight for the open find — device-check patch, Items 2/3. See [LogEntryDetailScreen]'s own doc comment on [onPhotoAcquisitionInFlightChanged]. */
    onPhotoAcquisitionInFlightChanged: (Boolean) -> Unit = {},
    onAddPhoto: (PhotoSource) -> Unit,
    onRemovePhoto: (LogPhoto) -> Unit,
    onPullPhoto: (LogPhoto) -> Unit,
    onDeleteEntry: (String) -> Unit,
    /** Clears [MushroomLogUiState.saveErrorMessage] once its Toast (below) has shown — see [LogPanel]'s identical parameter for the full reasoning. */
    onSaveErrorDismissed: () -> Unit,
    /** Threaded straight through from [MushroomLogUiState] into [CartographyScreen]'s own Album tab. */
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
    onFinishCartographyEntry: () -> Unit,
    /** Explicit Save for a committed Cartography entry — device-check patch, Item 1. See [CartographyEntryEditScreen]'s own doc comment. */
    onSaveCartographyEntry: () -> Unit = {},
    /** The leave-prompt's Discard option — device-check patch, Item 1. See [CartographyEntryEditScreen]'s own doc comment. */
    onDiscardCartographyEntryChanges: () -> Unit = {},
    onDeleteCartographyEntry: (String) -> Unit,
    /** [CartographyEntryReportScreen]'s own map, Stage 2d — see that composable's doc comment. */
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    /** [CartographyEntryReportScreen]'s own offline-map toggle, Stage 2e-i — see that composable's doc comment. */
    getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
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
    /** See this composable's own doc comment, "The map '+' routing bug" — Stage 2d. `null` (the default) is a no-op, so every other caller of this tab is unaffected. */
    pendingDestination: PendingJournalDestination? = null,
    /** Fires once [pendingDestination] has been applied, so [AvailabilityScreen] clears its own copy and is ready for the next request. */
    onPendingDestinationConsumed: () -> Unit = {},
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

    // The second, local latch this composable's own "map '+' routing bug" doc comment describes —
    // staged here rather than forwarding pendingDestination straight to RecordsTab, since RecordsTab
    // only exists in the composition once selectedTopTab has already become RECORDS.
    var recordsPendingSubTab by remember { mutableStateOf<RecordsSubTab?>(null) }

    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            PendingJournalDestination.EDIT_NEW_FIND -> {
                selectedTopTab = JournalTopTab.RECORDS
                mode = JournalEntryMode.EDIT
                recordsPendingSubTab = RecordsSubTab.FINDS
                onPendingDestinationConsumed()
            }
            null -> Unit
        }
    }

    // See this composable's own doc comment on "leaving Records mid-find-edit" for why this now
    // guards leaving Records (inverted from Stage 1, which guarded leaving Cartography — finds lived
    // there then).
    fun leaveFindEditingIfNeeded() {
        if (editing != null && mode == JournalEntryMode.EDIT) onLeaveEditingIncidentally()
    }

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
    //
    // Extracted to a val (back-nav-and-save-flow dispatch, Item 1) — findsSectionHasBackStack is
    // reused below both to gate the new Records→Cartography step and to tell RecordsTab's own new
    // sub-tab-stepping BackHandler to stay out of the way while this one is live. This one's
    // condition is unchanged from before that dispatch.
    val findsSectionHasBackStack = editing != null || pickingLocationForEditingEntry || pullingPhotoForEditingEntry
    BackHandler(enabled = findsSectionHasBackStack) {
        when {
            pickingLocationForEditingEntry -> pickingLocationForEditingEntry = false
            pullingPhotoForEditingEntry -> pullingPhotoForEditingEntry = false
            editing != null && mode == JournalEntryMode.EDIT -> onLeaveEditingIncidentally()
            editing != null -> onCloseEntry()
        }
    }

    // Back-nav-and-save-flow dispatch, Item 1: Records → Cartography is the next layer out once
    // Finds has nothing left to unwind — Cartography is the left tab and the entry point, so this
    // is the only top-tab direction that needs a step; back from Cartography's own top level
    // (nothing open there either) falls straight through to AvailabilityScreen's go-home handler,
    // unchanged. Explicitly excludes findsSectionHasBackStack rather than trusting declaration
    // order against the handler above, which lives at the same structural level (a sibling
    // BackHandler here, not nested inside it) — see AvailabilityScreen's own outer BackHandlers for
    // why this codebase never trusts declaration order for that.
    BackHandler(enabled = selectedTopTab == JournalTopTab.RECORDS && !findsSectionHasBackStack) {
        selectedTopTab = JournalTopTab.CARTOGRAPHY
    }

    // Journal Stage 2b, relocated verbatim from this tab's own former Cartography branch — see this
    // composable's own doc comment. A closure, not a separate file-level composable, so it keeps
    // reading/writing mode/pickingLocationForEditingEntry/pullingPhotoForEditingEntry via this
    // function's own remembered state regardless of which RecordsTab sub-tab slot renders it.
    val findsSection: @Composable ColumnScope.() -> Unit = {
        when {
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
                onPhotoAcquisitionInFlightChanged = onPhotoAcquisitionInFlightChanged,
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

            else -> FindsGalleryScreen(
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
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                cameraCaptureFiles = cameraCaptureFiles,
                onAddGalleryPhoto = onAddGalleryPhoto,
                distanceUnit = distanceUnit,
                mapSlot = mapSlot,
                basemap = basemap,
                night = night,
                getMapData = getCartographyEntryMapData,
                getCoveringOfflineRegion = getCartographyEntryOfflineRegion,
                onOpenEntry = onOpenCartographyEntry,
                onStartEntry = onStartCartographyEntry,
                onCloseEntry = onCloseCartographyEntry,
                onTextChanged = onCartographyTextChanged,
                onTagsChanged = onCartographyTagsChanged,
                onSetFindDecision = onSetFindDecision,
                onSetTrackDecision = onSetTrackDecision,
                onSetWaypointDecision = onSetWaypointDecision,
                onSetOfflineRegionDecision = onSetOfflineRegionDecision,
                onToggleKeptPhoto = onToggleKeptPhoto,
                onFinishEntry = onFinishCartographyEntry,
                onSaveEntry = onSaveCartographyEntry,
                onDiscardEntryChanges = onDiscardCartographyEntryChanges,
                onDeleteEntry = onDeleteCartographyEntry,
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
                findsEditingInProgress = findsSectionHasBackStack,
                pendingSubTab = recordsPendingSubTab,
                onPendingSubTabConsumed = { recordsPendingSubTab = null },
            )
        }
    }
}

/**
 * Which of the Journal's two tabs is selected — Cartography (Journal Stage 2b's new authored entity)
 * or Records (Stage 1, gained a fourth Finds submenu in 2b). Shared between [JournalTab] (compact)
 * and [LogPanel] (medium/expanded), which both use this same two-tab shell — `internal`, not
 * `private`, for exactly that reuse; declared once here rather than in each file, since Kotlin does
 * not allow two files in the same package to each declare a file-private top-level type of the same
 * name.
 */
internal enum class JournalTopTab { CARTOGRAPHY, RECORDS }

/**
 * Which screen [JournalTab]'s relocated Finds section shows for [MushroomLogUiState.editingEntry] —
 * "editing" is the accurate name for what that field means (see [MushroomLogViewModel]'s doc comment
 * on the persisted-draft model), but which of [LogEntryReportScreen]/[LogEntryDetailScreen] the
 * *user* sees for it depends on how they got there, tracked here rather than inferred from the
 * entry's own content (an entry with nothing recorded yet is a legitimate thing to view a report of
 * too, once the user backs out of editing it without filling anything in — REPORT stays correct for
 * that case where "does it have data" would not).
 */
private enum class JournalEntryMode { REPORT, EDIT }

/**
 * A one-shot request to open a specific place inside the Journal destination, made from outside it
 * — Stage 2d's routing fix for the map "+" icon bar's "Log a find" flow. See [JournalTab]'s own doc
 * comment, "The map '+' routing bug," for the full trace and for why this is one small enum (not a
 * boolean, so a future caller wanting a different destination adds a case here rather than a second
 * flag) rather than a shared navigation abstraction. `internal`, not `private`: both [JournalTab]
 * and [LogPanel] consume it, and `AvailabilityScreen.kt` owns the single instance both `onLogFindHere`
 * closures set.
 */
internal enum class PendingJournalDestination {
    /** Land in Records → Finds, editing the entry [MushroomLogViewModel.onStartNewEntry] just created. */
    EDIT_NEW_FIND,
}
