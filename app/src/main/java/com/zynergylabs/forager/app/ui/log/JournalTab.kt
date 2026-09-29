package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.adaptive.isShortWindow
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.OPEN_FIND_LABEL
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
 * The compact bottom nav's Journal destination — two tabs (journal restructure Stage 1): the
 * project owner's own framing, "**Records is a logbook** — raw, complete, machine-generated data.
 * **Cartography is where those records are compiled into a coherent story.**" [selectedTopTab]
 * picks between them — a `SecondaryTabRow` until journal redesign J2, now an Entries | Records
 * segmented button (Cartography reads "Entries" on screen; see the switch below). This codebase has
 * no navigation library, so, like every other "route" here, this is an enum plus state (hoisted
 * into [JournalScreenState] since J1), not a real destination.
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
 * [com.zynergylabs.forager.app.ui.map.MapOverlayContent.resumeTrackingRequestId] already establishes elsewhere
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
    /** The Camera buttons of this tab's three surfaces call up to one hoisted dialog — see [InAppCameraHost]. */
    onOpenCameraForLogEntry: () -> Unit,
    onOpenCameraForAlbum: () -> Unit,
    onOpenCameraForCartographyEntry: () -> Unit,
    mapSlot: MapSlot,
    pickerRegion: Region,
    /** The device's current position, if one is in hand — opens the Add/Change Location picker there instead of on [pickerRegion] (find-location-at-creation dispatch, Fix 3; see [findLocationPickerRegion]). Defaulted so callers and tests that have no fix are unchanged. */
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
     * Clears [CartographyUiState.saveErrorMessage] once its Toast (below) has shown, the day entries'
     * counterpart of [onSaveErrorDismissed] (intent `2026-09-28-68`, continuation `2026-09-28-76`).
     * The default is only for callers that host this on its own (tests); `AvailabilityScreen` passes
     * `CartographyViewModel.onSaveErrorDismissed`.
     */
    onCartographySaveErrorDismissed: () -> Unit = {},
    onDeleteCartographyEntry: (String) -> Unit,
    /**
     * An entry card's Delete (journal redesign J4b L2: the revealed Delete, a full swipe, or the
     * card's accessibility action), which asks for a *pending* delete with Undo
     * (`CartographyViewModel.requestDeleteEntry`). [onDeleteCartographyEntry] stays the report's and
     * edit screen's immediate delete behind their confirm dialogs. `null` (the default) leaves the
     * cards without the swipe.
     */
    onRequestDeleteCartographyEntry: ((String) -> Unit)? = null,
    /**
     * A find tile's long-press Edit (journal redesign J4b L1): `MushroomLogViewModel.onOpenEntryForEditing`,
     * the one call that opens a find and starts editing it atomically (`LogPanel`'s open path). The
     * tile's menu also offers Delete, which goes through [onDeleteEntry] (J4's pending delete). `null`
     * (the default) gives the tiles a menu of Delete only.
     */
    onOpenEntryForEditing: ((String) -> Unit)? = null,
    /**
     * An album photo's long-press Delete (J4b L3): a *pending* delete with Undo
     * (`MushroomLogViewModel.requestDeleteGalleryPhoto`). `null` (the default) leaves the photos without
     * the menu; the photo's corner delete button (immediate, after its dialog) is unchanged either way.
     */
    onRequestDeleteGalleryPhoto: ((String) -> Unit)? = null,
    /** [CartographyEntryReportScreen]'s own map, Stage 2d — see that composable's doc comment. */
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    /** F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, for the cards' thumbnails; see [CartographyEntryListScreen]. */
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
    /** GPX full-record export dispatch — see [com.zynergylabs.forager.app.ui.track.TrackExportList]'s own doc comment. Defaults empty/no-op so no other caller of this tab changes. */
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    /** Part 2 follow-ups F1 item 5 (owner "Option A"): a finished track's swipe or details Delete asks for a pending delete with Undo; `null` (the default) leaves tracks without a delete. */
    onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed track delete failed and the track is back; shown above the Tracks list. */
    tracksErrorMessage: String? = null,
    waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** See this composable's own doc comment, "The map '+' routing bug" — Stage 2d. `null` (the default) is a no-op, so every other caller of this tab is unaffected. */
    pendingDestination: PendingJournalDestination? = null,
    /** M1: the find a [PendingJournalDestination.VIEW_FIND] request opens over the Journal. */
    pendingFindId: String? = null,
    /** J8-4: the day entry a [PendingJournalDestination.VIEW_ENTRY] request opens. */
    pendingEntryId: String? = null,
    /** Fires once [pendingDestination] has been applied, so [AvailabilityScreen] clears its own copy and is ready for the next request. */
    onPendingDestinationConsumed: () -> Unit = {},
    /** J8-3: the entry report's "Show on map" and "Hide from map", threaded to [CartographyScreen]. `null` offers neither. */
    onSetCartographyEntryShownOnMap: ((entryId: String, shown: Boolean) -> Unit)? = null,
    /**
     * The Journal's user-set UI state (top tab, Records selection), hoisted and saveable — journal
     * redesign J1, S1; see [JournalScreenState]. `AvailabilityScreen` creates it above the
     * `when (compactTab)` branch this tab is composed in and passes it down, so leaving the Journal
     * tab no longer resets it. The default is only for callers that host this tab on its own (tests).
     */
    journalState: JournalScreenState = rememberJournalScreenState(),
    /**
     * Whether the Journal's own Back handlers may take Back: this tab's three, and through
     * [RecordsTab] and [CartographyScreen] every one below them. The compact scaffold passes `false`
     * while the Tools drawer is open over the Journal (intent 2026-09-28-28, the owner: "The intended
     * action is to close the drawer while it's open"). The drawer's own handler,
     * `AvailabilityScreen`'s `BackHandler(enabled = isDrawerOpen)`, is registered before any of these,
     * and the most recently registered enabled handler wins, so while these were on they took Back
     * and the drawer stayed open. Turned off, not outranked: a handler registered after these to
     * outrank them would also outrank the drawer's own Settings step (`CompactToolsDrawerContent`),
     * which is registered with the drawer's content. `true` (the default) is every other caller,
     * the wide tree's [LogPanel] among them, unchanged.
     */
    backEnabled: Boolean = true,
    /**
     * Whether an open day entry shows its report or its editor, handed to [CartographyScreen]
     * (intent 2026-09-28-44, F2; see that screen's parameter of the same name). The compact scaffold
     * passes state held in `AvailabilityScreen`, so the mode outlives this tab. The default is only
     * for callers that host this tab on its own (tests).
     */
    cartographyEntryModeState: MutableState<CartographyEntryMode> = remember { mutableStateOf(CartographyEntryMode.VIEW) },
    /**
     * Intent 2026-09-28-44, F3 (the owner: "Keep finds open too (Recommended)"): an open find's
     * report-or-editor mode ([mode]) and M1's find over the view ([FindOverView]), held by the caller.
     * Both were plain `remember` here, so while a find stayed open in the ViewModel across a tab
     * change, it came back in its report whatever mode it was left in, and a find opened over the
     * view from a map bubble came back hidden under it. The compact scaffold passes state held in
     * `AvailabilityScreen`, above the Journal branch; the defaults are for callers that host this tab
     * on its own (tests).
     */
    findEntryModeState: MutableState<JournalEntryMode> = remember { mutableStateOf(JournalEntryMode.REPORT) },
    findOverViewState: MutableState<FindOverView?> = remember { mutableStateOf(null) },
    /**
     * J6a (ruling 1, list-detail): the wide tree's detail slot, handed by [LogPanel]. With it, an open
     * find (its report, editor and pickers), an open day entry ([CartographyScreen]) and a record's
     * details ([RecordsTab]) register there and take the whole right side while the lists stay where
     * they are. `null` (the default, every compact caller) draws each in place, as before.
     */
    detailSlot: JournalDetailSlot? = null,
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
    // Intent 2026-09-28-68, continuation 2026-09-28-76 (the owner: "Option B"): a day entry's failed
    // save, Finish, Save as draft, Discard or delete, told the same way as the find's just above and
    // cleared once shown. Hosted here, not in AvailabilityScreen, so a failure raised while the
    // Journal is not on screen waits in the ViewModel and shows when the Journal next opens.
    LaunchedEffect(cartographyUiState.saveErrorMessage) {
        cartographyUiState.saveErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onCartographySaveErrorDismissed()
        }
    }

    // REPORT for an entry opened from the gallery (there's something to compile a report from and
    // no reason to assume an edit is wanted), EDIT for one just started (nothing to report yet, so
    // reporting first would just be an empty screen between creation and the form the user is there
    // for). Reset to REPORT whenever a *different* entry becomes the open one, so returning to an
    // entry after editing shows the freshly-recompiled report rather than staying in edit mode.
    // Intent 2026-09-28-44, F3: held by the caller (findEntryModeState), so a find left in its
    // editor comes back in its editor.
    var mode by findEntryModeState
    // J4b L1: a find tile's long-press Edit, straight into the edit form (EDIT mode, as a draft opens
    // and as the report's own Edit switches to). onOpenEntryForEditing opens and starts editing in one
    // critical section, the call LogPanel already uses; null means the tiles offer Delete only.
    val editFind: ((String) -> Unit)? = onOpenEntryForEditing?.let { open ->
        { id: String ->
            mode = JournalEntryMode.EDIT
            open(id)
        }
    }
    val editing = uiState.editingEntry

    // Only meaningful while editing — set by LogEntryDetailScreen's own "Add Location" button, read
    // back down here rather than hoisted into MushroomLogUiState, since no other consumer of that
    // state needs to know a location picker happens to be open mid-edit.
    var pickingLocationForEditingEntry by remember { mutableStateOf(false) }

    // Same shape as pickingLocationForEditingEntry, for LogEntryDetailScreen's "From Album" button
    // (Workstream G3) instead of "Add Location".
    var pullingPhotoForEditingEntry by remember { mutableStateOf(false) }

    // Hoisted into journalState (journal redesign J1, S1) — see JournalScreenState for why.
    var selectedTopTab by journalState::topTab

    // The second, local latch this composable's own "map '+' routing bug" doc comment describes —
    // staged here rather than forwarding pendingDestination straight to RecordsTab, since RecordsTab
    // only exists in the composition once selectedTopTab has already become RECORDS.
    var recordsPendingSubTab by remember { mutableStateOf<RecordsSubTab?>(null) }

    // J8-4: the same kind of local latch for a VIEW_ENTRY request: CartographyScreen only exists in the
    // composition once selectedTopTab is CARTOGRAPHY, so the entry is staged here and handed to it.
    var entryOpenRequest by remember { mutableStateOf<String?>(null) }

    // M1: a find opened from a map bubble, shown over whatever the Journal was showing (FindOverView).
    var findOverView by findOverViewState
    LaunchedEffect(findOverView, editing?.id) {
        findOverView = nextFindOverView(findOverView, editing?.id)
    }
    val findOverViewVisible = editing != null && findOverView.let { it != null && (it.shown || editing.id == it.findId) }

    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            PendingJournalDestination.EDIT_NEW_FIND -> {
                selectedTopTab = JournalTopTab.RECORDS
                mode = JournalEntryMode.EDIT
                recordsPendingSubTab = RecordsSubTab.FINDS
                onPendingDestinationConsumed()
            }
            // M1, the Maps tab's "Open in Journal": the caller has already asked the ViewModel to
            // open the find; it shows in its report over the Journal, which keeps its top tab and its
            // saved Records chip (planner's ruling on F3), and Back returns to them.
            PendingJournalDestination.VIEW_FIND -> {
                pendingFindId?.let { findOverView = FindOverView(it) }
                mode = JournalEntryMode.REPORT
                onPendingDestinationConsumed()
            }
            // J8-4, the Maps tab's "Open entry": the entry opens on Entries, in its report, through
            // CartographyScreen's openEntryRequest (which asks first over an unsaved edit).
            PendingJournalDestination.VIEW_ENTRY -> {
                selectedTopTab = JournalTopTab.CARTOGRAPHY
                entryOpenRequest = pendingEntryId
                onPendingDestinationConsumed()
            }
            null -> Unit
        }
    }

    // See this composable's own doc comment on "leaving Records mid-find-edit" for why this now
    // guards leaving Records (inverted from Stage 1, which guarded leaving Cartography — finds lived
    // there then).
    //
    // J5: reads the open find through rememberUpdatedState. The function reference handed to
    // RecordsTab (onFindsTabLeft) and to the short window's header row can be memoized by the Compose
    // compiler with the find open at the composition that created it; read straight from `editing`,
    // a reference kept from before the find opened saw null and skipped the incidental exit
    // (RecordsFilterChipsTest caught it while J5 was being built; instrumented, the call ran with
    // editing = null and mode = EDIT). The latest value is what the rule is about.
    val latestEditing by rememberUpdatedState(editing)
    val latestOnLeaveEditingIncidentally by rememberUpdatedState(onLeaveEditingIncidentally)
    fun leaveFindEditingIfNeeded() {
        if (latestEditing != null && mode == JournalEntryMode.EDIT) latestOnLeaveEditingIncidentally()
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
    fun unwindFindsSection() {
        when {
            pickingLocationForEditingEntry -> pickingLocationForEditingEntry = false
            pullingPhotoForEditingEntry -> pullingPhotoForEditingEntry = false
            editing != null && mode == JournalEntryMode.EDIT -> onLeaveEditingIncidentally()
            editing != null -> onCloseEntry()
        }
    }
    BackHandler(enabled = backEnabled && findsSectionHasBackStack) { unwindFindsSection() }

    // Back-nav-and-save-flow dispatch, Item 1: Records → Cartography is the next layer out once
    // Finds has nothing left to unwind — Cartography is the left tab and the entry point, so this
    // is the only top-tab direction that needs a step; back from Cartography's own top level
    // (nothing open there either) falls straight through to AvailabilityScreen's go-home handler,
    // unchanged. Explicitly excludes findsSectionHasBackStack rather than trusting declaration
    // order against the handler above, which lives at the same structural level (a sibling
    // BackHandler here, not nested inside it) — see AvailabilityScreen's own outer BackHandlers for
    // why this codebase never trusts declaration order for that.
    BackHandler(enabled = backEnabled && selectedTopTab == JournalTopTab.RECORDS && !findsSectionHasBackStack) {
        selectedTopTab = JournalTopTab.CARTOGRAPHY
    }

    // Journal Stage 2b, relocated verbatim from this tab's own former Cartography branch — see this
    // composable's own doc comment. A closure, not a separate file-level composable, so it keeps
    // reading/writing mode/pickingLocationForEditingEntry/pullingPhotoForEditingEntry via this
    // function's own remembered state regardless of which RecordsTab sub-tab slot renders it.
    // J6a: the Finds section is two parts, so the wide tree can put them in different places. The list
    // (the gallery) stays with the Records chip; the detail (an open find's report, editor and pickers)
    // is what the right side shows when there is a slot. With no slot, [findsSection] draws whichever
    // applies exactly as the one `when` did before the split.
    val findsDetail: @Composable ColumnScope.() -> Unit = {
        when {
            editing != null && mode == JournalEntryMode.EDIT && pickingLocationForEditingEntry -> CentrePinLocationPicker(
                mapSlot = mapSlot,
                region = findLocationPickerRegion(deviceLocation, pickerRegion),
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
                // Entry-photo-acquisition dispatch, Item 2: the same shared composable gains
                // Camera/Import for both its callers, not just Cartography's — "acquire and
                // attach" here reuses onAddPhoto whole, the exact same already-correct
                // persist-attach-and-GPS-patch path LogEntryDetailScreen's own PhotosSection
                // already calls a few lines below, and onPhotoAcquisitionInFlightChanged is the
                // same guard threaded there too: the two call sites are mutually exclusive
                // branches of this same `when`, so whichever is actually composed is the only one
                // whose in-flight state matters at a time. Reusing both rather than defaulting
                // them to no-ops, which would have rendered live Camera/Import buttons that
                // silently discarded whatever they captured.
                onOpenCamera = onOpenCameraForLogEntry,
                onPhotoAcquired = onAddPhoto,
                onAcquisitionInFlightChanged = onPhotoAcquisitionInFlightChanged,
                modifier = Modifier.weight(1f),
            )

            editing != null && mode == JournalEntryMode.EDIT -> LogEntryDetailScreen(
                entry = editing,
                onOpenCamera = onOpenCameraForLogEntry,
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
            else -> Unit
        }
    }
    val findsList: @Composable ColumnScope.() -> Unit = {
        FindsGalleryScreen(
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
            // J4b L1: a tile's long-press menu. Delete is the report's own pending delete (J4);
            // Edit opens the find straight into its edit form.
            onDeleteEntry = onDeleteEntry,
            onEditEntry = editFind,
        )
    }
    val findsSection: @Composable ColumnScope.() -> Unit = {
        if (editing != null) findsDetail() else findsList()
    }
    // J6a: with a slot, an open find registers as a detail (the whole right side) and the Finds chip
    // keeps only the list. Above every other detail in priority: a find opened over a day entry covers it.
    JournalDetail(detailSlot, active = editing != null, priority = JournalDetailPriority.FIND) {
        Column(modifier = Modifier.fillMaxSize()) { findsDetail() }
    }

    fun selectTopTab(tab: JournalTopTab) {
        // Leaving Records mid-find-edit for Entries is an incidental exit — see this composable's
        // own doc comment.
        if (tab == JournalTopTab.CARTOGRAPHY) leaveFindEditingIfNeeded()
        selectedTopTab = tab
    }

    // Journal redesign J5 (plan L1-L3; owner's ruling 1): in a short landscape window the switch
    // moves into one pinned 48 dp row with the search icon and the screen's action, so it is not
    // drawn here. Entries draws that row itself (CartographyScreen, which owns the New and photo
    // actions and the state they need); Records gets it from the RECORDS branch below. `null` in
    // portrait and in every window that is not short, which is exactly as before.
    val shortLandscape = isShortLandscapeJournal()
    // Any entry open, find or Cartography entry: the scaffold hides the search header then anyway
    // (its isEditingJournalEntry rule, the same two fields), so the row's search icon is left out.
    val journalEntryOpen = editing != null || cartographyUiState.editingEntry != null
    val shortWindowHeader: (@Composable ((@Composable () -> Unit)?) -> Unit)? = if (shortLandscape) {
        { action ->
            ShortWindowJournalHeader(
                selectedTopTab = selectedTopTab,
                onSelectTopTab = ::selectTopTab,
                showSearch = !journalEntryOpen,
                searchRevealed = journalState.searchHeaderRevealed,
                onToggleSearch = { journalState.searchHeaderRevealed = !journalState.searchHeaderRevealed },
                action = action,
            )
        }
    } else {
        null
    }

    // M1: the entry map's bubbles (owner: "Yes, same bubbles"), from the lists this tab already holds.
    // "Open find" opens the find over the day entry, which stays composed under it, so Back returns to
    // the same entry, view and scroll (owner, Q4: "Open find, Back returns").
    val entryMapBubbleSources = MapRecordSources(
        finds = uiState.entries,
        galleryPhotos = galleryPhotos,
        photoEntryReferenceCounts = galleryPhotoEntryReferenceCounts,
        waypoints = waypoints,
        waypointEntryReferenceCounts = waypointEntryReferenceCounts,
        tracks = tracks,
        offlineRegions = availabilityUiState.visibleOfflineRegions,
        distanceUnit = distanceUnit,
        staleThresholdDays = availabilityUiState.offlineStaleThresholdDays,
        nowEpochMillis = currentTime::nowEpochMillis,
        getFullRecord = getFullRecord,
        onOpenFind = { id ->
            leaveFindEditingIfNeeded()
            mode = JournalEntryMode.REPORT
            findOverView = FindOverView(id)
            onOpenEntry(id)
        },
        openFindLabel = OPEN_FIND_LABEL,
    )

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Journal redesign J2, T1 (plan J1): one single-choice segmented button replaces the
        // SecondaryTabRow that used to sit here, so the Journal's top level no longer reads as a
        // second tab row stacked on the bottom bar. Only the on-screen label changed: the
        // CARTOGRAPHY value, CartographyScreen and CartographyEntry keep their names. The selection
        // is still journalState's hoisted top tab (J1, S1), and the Records -> Entries Back step is
        // the BackHandler above, unchanged. In a short window it sits in the L1 row instead (above).
        if (!shortLandscape) {
            JournalSwitch(
                selected = selectedTopTab,
                onSelect = ::selectTopTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
                onOpenCameraForAlbum = onOpenCameraForAlbum,
                onOpenCameraForEntry = onOpenCameraForCartographyEntry,
                onAddGalleryPhoto = onAddGalleryPhoto,
                distanceUnit = distanceUnit,
                mapSlot = mapSlot,
                night = night,
                initialMapMode = MapMode.forBasemap(basemap),
                mapLayers = mapLayers,
                onMapLayerVisibilityChanged = onMapLayerVisibilityChanged,
                getMapData = getCartographyEntryMapData,
                getCoveringOfflineRegion = getCartographyEntryOfflineRegion,
                getCurrentLocation = getCartographyEntryCurrentLocation,
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
                onAcquirePhotoForEntry = onAcquirePhotoForCartographyEntry,
                onFinishEntry = onFinishCartographyEntry,
                onSaveEntry = onSaveCartographyEntry,
                onDiscardEntryChanges = onDiscardCartographyEntryChanges,
                onSaveEntryAsDraft = onSaveCartographyEntryAsDraft,
                onDeleteEntry = onDeleteCartographyEntry,
                modifier = Modifier.weight(1f),
                // J3, C4 (plan J9): one full-width column in compact portrait; `columns` stays the
                // only width knob. A short window (a phone on its side) keeps two columns, which J5's
                // sideways cards use (plan L4); the rule is J3's, unchanged.
                columns = if (isShortWindow()) SHORT_WINDOW_ENTRY_COLUMNS else COMPACT_PORTRAIT_ENTRY_COLUMNS,
                entriesViewState = journalState.entriesViewState,
                // J3, C3: the track list this tab already receives for Records (MainActivity's
                // trackUiState.tracks, through AvailabilityScreen and CompactMainScaffold), joined in
                // memory by the Entries cards for their thumbnails.
                tracks = tracks,
                getSavedTrackPaths = getSavedTrackPaths,
                // J3, C5: the album's find badge marks saved finds only; the draft finds' ids are
                // already in this tab's MushroomLogUiState.
                draftFindIds = uiState.draftEntries.mapTo(HashSet()) { it.id },
                onRequestDeleteEntry = onRequestDeleteCartographyEntry,
                onRequestDeleteGalleryPhoto = onRequestDeleteGalleryPhoto,
                // J5: the L1 row (null outside a short landscape window), drawn by Entries itself
                // with its own action; see CartographyScreen's shortWindowHeader.
                shortWindowHeader = shortWindowHeader,
                backEnabled = backEnabled,
                mapBubbleSources = entryMapBubbleSources,
                entryModeState = cartographyEntryModeState,
                openEntryRequest = entryOpenRequest,
                onOpenEntryRequestConsumed = { entryOpenRequest = null },
                onSetShownOnMap = onSetCartographyEntryShownOnMap,
                detailSlot = detailSlot,
            )

            // J5: a Column in every window, so RecordsTab keeps one place in the composition when
            // the phone turns (plan L7: a rotation is not a recreation here, and a moved call site
            // would drop its remember state); the L1 row sits above it only in a short window, with
            // no action (portrait's Records has no floating button to move into it, L2).
            JournalTopTab.RECORDS -> Column(modifier = Modifier.weight(1f)) {
                shortWindowHeader?.invoke(null)
                RecordsTab(
                    modifier = Modifier.weight(1f),
                    waypoints = waypoints,
                    waypointsErrorMessage = waypointsErrorMessage,
                    onDeleteWaypoint = onDeleteWaypoint,
                    onDeleteTrack = onDeleteTrack,
                    tracksErrorMessage = tracksErrorMessage,
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
                    onDownloadAgain = onDownloadAgain,
                    tracks = tracks,
                    onTracksOpened = onTracksOpened,
                    getFullRecord = getFullRecord,
                    // M1: while a find is open over the view, the Finds slot under it draws nothing,
                    // so the find is composed once, in the overlay.
                    // J6a: with a slot the open find is the pane's, so the Finds chip keeps only its list.
                    findsContent = { if (detailSlot != null) findsList() else if (findOverView == null) findsSection() },
                    finds = uiState.entries,
                    // The All logbook's find tap: RecordsTab has already selected the Finds chip; this
                    // opens the report there, exactly as the Finds gallery's own tile does.
                    onOpenFind = { id ->
                        mode = JournalEntryMode.REPORT
                        onOpenEntry(id)
                    },
                    // J4b L1: the All logbook's find tiles get the same long-press menu.
                    onDeleteFind = onDeleteEntry,
                    onEditFind = editFind,
                    onFindsTabLeft = ::leaveFindEditingIfNeeded,
                    findsEditingInProgress = findsSectionHasBackStack,
                    pendingSubTab = recordsPendingSubTab,
                    onPendingSubTabConsumed = { recordsPendingSubTab = null },
                    selectedTabState = journalState.recordsFilterState,
                    // J5, L3 and L5a: in a short window the filter chips are the second row, which
                    // gets out of the way while the list scrolls, with the tighter chip spacing.
                    shortWindow = shortLandscape,
                    backEnabled = backEnabled,
                    detailSlot = detailSlot,
                )
            }
        }
    }

    // M1: the find opened from a map bubble, over the view (FindOverView). An opaque Surface, so no
    // touch reaches the view under it, and its own Back handler, composed after everything under it,
    // so Back unwinds the find first (a picker, the edit form, then the report) and only then the view.
    // J6a: not with a slot, where the open find is drawn by the pane and there is nothing to overlay.
    if (findOverViewVisible && detailSlot == null) {
        BackHandler(enabled = backEnabled) { unwindFindsSection() }
        Surface(modifier = Modifier.fillMaxSize().testTag(FIND_OVER_VIEW_TAG)) {
            Column(modifier = Modifier.fillMaxSize()) { findsSection() }
        }
    }
    }

    // J5 (owner's ruling 1): Back puts a brought-up search header away before anything else in the
    // Journal takes Back. Composed after both branches, so it outranks their own handlers (the most
    // recently composed enabled handler wins). Off while an entry is open: the header is hidden then
    // regardless, and Back belongs to the entry.
    BackHandler(enabled = backEnabled && shortLandscape && journalState.searchHeaderRevealed && !journalEntryOpen) {
        journalState.searchHeaderRevealed = false
    }
}

/**
 * The Entries | Records switch (journal redesign J2, T1; plan J1): one single-choice segmented
 * button. [JournalTab] draws it full width at the top in portrait; in a short window it sits at the
 * start of the L1 row ([ShortWindowJournalHeader], J5), sized to its labels. Extracted by J5 so the
 * two places draw one control with one set of tags.
 */
@Composable
internal fun JournalSwitch(selected: JournalTopTab, onSelect: (JournalTopTab) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.testTag(JOURNAL_SWITCH_TAG)) {
        JournalTopTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = JournalTopTab.entries.size),
                modifier = Modifier.testTag(journalSwitchTestTag(tab)),
                label = { Text(tab.switchLabel) },
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
internal enum class JournalTopTab(
    /** The on-screen label on [JournalTab]'s Entries | Records switch (J2, T1): CARTOGRAPHY reads "Entries" (plan J1). */
    val switchLabel: String,
) {
    CARTOGRAPHY("Entries"),
    RECORDS("Records"),
}

/** Entries timeline columns in compact portrait (J3, C4; plan J9). */
private const val COMPACT_PORTRAIT_ENTRY_COLUMNS = 1

/** Entries timeline columns in a short window, unchanged by J3 (stage J5 owns short windows). */
private const val SHORT_WINDOW_ENTRY_COLUMNS = 2

/** The row holding [JournalTab]'s Entries | Records switch. */
internal const val JOURNAL_SWITCH_TAG = "journal-switch"

/** One side of [JournalTab]'s Entries | Records switch: fixed strings, not enum names, so a rename cannot move a test's target. */
internal fun journalSwitchTestTag(tab: JournalTopTab): String = when (tab) {
    JournalTopTab.CARTOGRAPHY -> "journal-switch-entries"
    JournalTopTab.RECORDS -> "journal-switch-records"
}

/**
 * Which screen [JournalTab]'s relocated Finds section shows for [MushroomLogUiState.editingEntry] —
 * "editing" is the accurate name for what that field means (see [MushroomLogViewModel]'s doc comment
 * on the persisted-draft model), but which of [LogEntryReportScreen]/[LogEntryDetailScreen] the
 * *user* sees for it depends on how they got there, tracked here rather than inferred from the
 * entry's own content (an entry with nothing recorded yet is a legitimate thing to view a report of
 * too, once the user backs out of editing it without filling anything in — REPORT stays correct for
 * that case where "does it have data" would not).
 */
internal enum class JournalEntryMode { REPORT, EDIT }

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

    /**
     * M1: show the find the caller has just opened (`onOpenEntry`, with its id passed beside this) in
     * its report, over whatever the Journal is showing, without changing the top tab or the Records
     * chip; Back returns to them ([FindOverView]). The Maps tab's "Open in Journal".
     */
    VIEW_FIND,

    /**
     * J8-4: open the day entry whose id is passed beside this in its report, on Entries, the one
     * top-tab change opening it requires (the saved Records chip is untouched). The Maps tab's "Open
     * entry" (owner's Q1 ruling, "Open in Journal, prompt first"); [CartographyScreen]'s
     * `openEntryRequest` asks first when another entry is open in its editor with unsaved changes.
     */
    VIEW_ENTRY,
}

/** The find shown over the Journal ([FindOverView]). */
internal const val FIND_OVER_VIEW_TAG = "journal-find-over-view"

/**
 * A find opened from a map bubble over whatever the Journal was showing (M1; continuation
 * `2026-09-28-30`: the owner's "Open find, Back returns" and "Open drawer to the find", and the
 * planner's ruling that the Maps route must not overwrite the saved Records filter). [findId] is the
 * find asked for; [shown] turns true once the ViewModel has opened it (opening is asynchronous,
 * `MushroomLogViewModel.onOpenEntry`), and the overlay then stays until no find is open any more
 * (Back, the report's own back arrow, a save or a delete), which leaves the view under it exactly as
 * it was: the same top tab, the same Records chip, the same day entry and its scroll.
 */
internal data class FindOverView(val findId: String, val shown: Boolean = false)

/**
 * [state] after the open find becomes [openFindId] (`MushroomLogUiState.editingEntry?.id`): shown
 * once the asked-for find is open, kept while any find stays open (starting an edit swaps the open
 * id to its draft row), and gone once none is. A request whose find never opened stays waiting.
 */
internal fun nextFindOverView(state: FindOverView?, openFindId: String?): FindOverView? = when {
    state == null -> null
    !state.shown -> if (openFindId == state.findId) state.copy(shown = true) else state
    openFindId == null -> null
    else -> state
}
