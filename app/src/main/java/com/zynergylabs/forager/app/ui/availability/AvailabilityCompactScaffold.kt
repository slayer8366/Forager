package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage G: the compact scaffold, extracted from AvailabilityScreen.kt. A
// behaviour-preserving extraction, not a pure move. The body of the compactMainScaffold lambda
// (AvailabilityScreen.kt lines 1304-2138 as of 29277b7; the lambda itself is 1303-2139) is moved
// here verbatim, indentation included, as CompactMainScaffold's body. AvailabilityScreen.kt keeps
// the lambda, which now only calls this function, so the ModalNavigationDrawer call that takes it
// as content is unchanged. What the lambda captured is now explicit parameters, 109 of them. The
// only edits to the body: (a) a read of state that AvailabilityScreen holds by remember {
// mutableStateOf(...) } calls a getter parameter, name(), at the same moment as before, at
// composition or at event time, and so in the same recomposition scope; a write of it calls a
// setter parameter, onNameChange(x); (b) every other captured value or callback is a parameter with
// the same name and type; (c) imports. One widening, private -> internal: ResultsTab in
// AvailabilityScreen.kt, which the selectedTab setter's type and its four writes name.
//
// The seven written states: isMapFullscreen, compactTab, selectedTab, isDrawerOpen, mapMode,
// pendingJournalDestination and logPhotoAcquisitionInFlight. Each but selectedTab is passed as a
// getter and a setter; selectedTab, written here and never read, as a setter only.
//
// Every parameter this extraction created, by kind. Getter and setter (6): isMapFullscreen and
// onIsMapFullscreenChange, compactTab and onCompactTabChange, isDrawerOpen and
// onIsDrawerOpenChange, mapMode and onMapModeChange, pendingJournalDestination and
// onPendingJournalDestinationChange, logPhotoAcquisitionInFlight and
// onLogPhotoAcquisitionInFlightChange. Getter only (1): mapTaxonFilter. Setter only (1):
// onSelectedTabChange. Values (95): focusManager, keyboardController, logUiState,
// cartographyUiState, isLandscapeWindow, portEdge, logDraftSnackbarHostState, uiState,
// distanceUnit, currentTime, mapSlot, mapIconClusterPosition, mapRenderMode, isNightMode,
// isRecording, startRecordingErrorMessage, breadcrumbPoints, mapWaypoints, returnToStart,
// isReturning, isNavigating, isOffTrack, compassProvider, computeTrueHeading, navigationTarget,
// pathHomeMeters (since replaced by returnRoute and onRetryRoute, dispatch 2026-09-28-423), basemap, tracks, waypoints, waypointsErrorMessage, waypointEntryReferenceCounts,
// onLocateMe, onLeaveLogEntryEditingIncidentally, leaveLogEntryEditingOfferingDiscard,
// onDismissTaxonSuggestions, onMapFullscreenChanged, onUseCurrentLocation,
// onTaxonSearchQueryChanged, onTaxonSearchResultSelected, onStartLogEntry, onViewSpeciesOnMap,
// onPlaceTripPin, onToggleRecording, onDropWaypoint, onToggleReturning, onClearMapTaxonFilter,
// onManualLatChanged, onManualLngChanged, onSearchManualCoordinates, onOpenCamera, onOpenLogEntry,
// onCloseLogEntry, onLogEntryChanged, onStartEditingLogEntry, onSaveLogEntry,
// onCancelLogEntryEditing, onAddLogPhoto, onRemoveLogPhoto, onPullLogPhoto, onDeleteLogEntry,
// onSaveLogErrorDismissed, onDeleteGalleryPhoto, onAddGalleryPhoto, onOpenCartographyEntry,
// onStartCartographyEntry, onCloseCartographyEntry, onCartographyTextChanged,
// onCartographyTagsChanged, onSetFindDecision, onSetTrackDecision, onSetWaypointDecision,
// onSetOfflineRegionDecision, onToggleKeptPhoto, onAcquirePhotoForCartographyEntry,
// onFinishCartographyEntry, onSaveCartographyEntry, onDiscardCartographyEntryChanges,
// onSaveCartographyEntryAsDraft, onDeleteCartographyEntry, getCartographyEntryMapData,
// getCartographyEntryOfflineRegion, getCartographyEntryCurrentLocation, onOfflineMapLatChanged,
// onOfflineMapLngChanged, onOfflineMapRadiusChanged, onOfflineMapNameChanged, onOfflineMapsOpened,
// onDownloadOfflineMaps, onDeleteOfflineRegion, onTracksOpened, getFullRecord, onDeleteWaypoint,
// onRecentSearchSelected, onRadiusChanged, onMonthSelected. Order: state and values first, then
// callbacks, each group in the order of first appearance in the body; function-typed values count
// as callbacks.
//
// Why: the owner chose this extraction as its own build, "Yes, as a second build", on the option
// "Build 2: the scaffold extraction on its own, reviewed and tested separately, still no intended
// behaviour change." The getter pattern and the ResultsTab widening are the planner's rulings on
// this build's two stops, quoted in RECORD.md intent 2026-09-27-21.
//
// 2026-10-21, Phase 2.6 B (dispatch 2026-10-19-40): 78 of those parameters, each a field the screen passed through unchanged, now
// arrive as the screen's own seven bundles (findsLog, journalEntries, entryReportData, offlineMaps, records, searchInputs,
// recording) and are unpacked at the top of the body under the same names and types, so the body below is unchanged.
// Phase 2.6 B2 (same dispatch): 36 more, the map and navigation values the frame hands the Maps tab, arrive as one
// CompactScaffoldMapInputs and are unpacked the same way.

import com.zynergylabs.forager.app.ui.adaptive.isLargeScreenWindow
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
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
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.InAppCameraTarget
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.log.CartographyEntryMode
import com.zynergylabs.forager.app.ui.log.FindOverView
import com.zynergylabs.forager.app.ui.log.JournalEntryMode
import com.zynergylabs.forager.app.ui.log.JournalScreenState
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.TabCrossfade
import com.zynergylabs.forager.app.ui.motion.LeavingPages
import com.zynergylabs.forager.app.ui.motion.LocalLeavingPages
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import com.zynergylabs.forager.app.ui.motion.TabChromeFade
import com.zynergylabs.forager.app.ui.motion.zeroHeightDrawnAbove
import com.zynergylabs.forager.app.ui.motion.zeroWidthDrawnInPlace
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController

@Composable
internal fun CompactMainScaffold(
    isMapFullscreen: () -> Boolean,
    focusManager: FocusManager,
    /** Up while the app's own clearFocus is running: see its declaration in [AvailabilityScreen] (dispatch 2026-09-28-312). */
    appClearFocusInProgress: BooleanArray,
    keyboardController: SoftwareKeyboardController?,
    compactTab: () -> CompactTab,
    logPhotoAcquisitionInFlight: () -> Boolean,
    isDrawerOpen: () -> Boolean,
    /**
     * RECORD -871: whether Back is to retrace the Tools drawer's "Offline maps" row (`AvailabilityScreen`'s Tools return
     * step). While it is, the Journal's own Back handlers are off, as they are while the drawer is open.
     */
    toolsReturnPending: () -> Boolean,
    isLandscapeWindow: Boolean,
    portEdge: ScreenEdge,
    /** Landscape B2 (S1): the punch-hole edge from `punchHoleEdgeFor`; read only while the rail shows. */
    punchHoleEdge: ScreenEdge,
    logDraftSnackbarHostState: SnackbarHostState,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    pendingJournalDestination: () -> PendingJournalDestination?,
    /** M1: the find a [PendingJournalDestination.VIEW_FIND] request opens; threaded to [JournalTab]. */
    pendingJournalFindId: () -> String? = { null },
    /** J8-4: the day entry a [PendingJournalDestination.VIEW_ENTRY] request opens; threaded to [JournalTab]. */
    pendingJournalEntryId: () -> String? = { null },
    /** Plan T16: the imported track a [PendingJournalDestination.VIEW_IMPORTED_TRACK] request opens; threaded to [JournalTab]. */
    pendingJournalTrackId: () -> String? = { null },
    /** Plan T16: Records > Tracks > "Import GPX"; threaded to [JournalTab]. `null` shows no button. */
    onGpxFilePicked: ((android.net.Uri) -> Unit)? = null,
    currentTime: CurrentTimeProvider,
    /** Item 8: a find's report was closed (Back or its arrow); the id is the find that was open. The screen decides whether that is a return to the map. */
    onFindReportClosed: (String?) -> Unit,
    /** Item 8: a find was deleted from its page or its tile. */
    onFindDeleted: (String) -> Unit,
    /** Item 8: the user began editing a find (from its report or a tile), which ends "return to the map" for it. */
    onFindEditStarted: () -> Unit,
    /** Dispatch 2026-09-28-387, Part B: a journal entry's report or editor was left. The screen decides whether that is a return to the map. */
    onEntryClosed: (entryId: String, fromReport: Boolean) -> Unit = { _, _ -> },
    mapMode: () -> MapMode,
    mapTaxonFilter: () -> Long?,
    leaveLogEntryEditingOfferingDiscard: () -> Unit,
    onIsDrawerOpenChange: (Boolean) -> Unit,
    onIsMapFullscreenChange: (Boolean) -> Unit,
    onCompactTabChange: (CompactTab) -> Unit,
    onSelectedTabChange: (ResultsTab) -> Unit,
    onPendingJournalDestinationChange: (PendingJournalDestination?) -> Unit,
    onViewSpeciesOnMap: (Long) -> Unit,
    onOpenCamera: (InAppCameraTarget) -> Unit,
    onLogPhotoAcquisitionInFlightChange: (Boolean) -> Unit,
    /**
     * Journal redesign J1, S1: the Journal's hoisted, saveable UI state, created in
     * `AvailabilityScreen` beside `compactTab` and handed to [JournalTab] below. Added after the
     * extraction this file's header describes, so it is not among the 109 parameters counted there.
     */
    journalScreenState: JournalScreenState,
    /**
     * Intent 2026-09-28-44, F2: whether an open day entry shows its report or its editor, created in
     * `AvailabilityScreen` beside [journalScreenState] and handed to [JournalTab]'s
     * `CartographyScreen`, so it outlives the Journal branch. Not among the 109 either.
     */
    cartographyEntryModeState: MutableState<CartographyEntryMode>,
    /** Data part A (dispatch 2026-09-28-667): which groups of the entry editor's "In this entry" panel are open, held in `AvailabilityScreen` beside [cartographyEntryModeState] for the same reason. */
    cartographyOpenEntryGroupsState: MutableState<Set<EntryGroup>>,
    /** Intent 2026-09-28-44, F3: an open find's mode and M1's find over the view, handed to [JournalTab]; see its parameters. Not among the 109. */
    findEntryModeState: MutableState<JournalEntryMode>,
    findOverViewState: MutableState<FindOverView?>,
    /** Phase 2.6 B (dispatch 2026-10-19-40): the screen's own bundles, passed straight down and unpacked at the top of the body under the names they replaced. */
    findsLog: AvailabilityScreenFindsLog,
    journalEntries: AvailabilityScreenJournalEntries,
    entryReportData: AvailabilityScreenEntryReportData,
    offlineMaps: AvailabilityScreenOfflineMaps,
    records: AvailabilityScreenRecords,
    searchInputs: AvailabilityScreenSearchInputs,
    recording: AvailabilityScreenRecording,
    /** Phase 2.6 B2 (dispatch 2026-10-19-40): the map and navigation values handed to the Maps tab; see [CompactScaffoldMapInputs]. Unpacked at the top of the body. */
    mapInputs: CompactScaffoldMapInputs,
) {
        // Phase 2.6 B (dispatch 2026-10-19-40): the screen's bundles, unpacked under the parameter names they replaced, with those parameters' types and
        // comments, so nothing below this block changed. Each is a field the screen passed through unchanged; none is a getter.
        // AvailabilityScreenFindsLog.
        val logUiState: MushroomLogUiState = findsLog.logUiState
        val onLeaveLogEntryEditingIncidentally: () -> Unit = findsLog.onLeaveLogEntryEditingIncidentally
        val onStartLogEntry: (LatLng?, LocalDate) -> Unit = findsLog.onStartLogEntry
        val onOpenLogEntry: (String) -> Unit = findsLog.onOpenLogEntry
        val onCloseLogEntry: () -> Unit = findsLog.onCloseLogEntry
        val onLogEntryChanged: (MushroomLogEntry) -> Unit = findsLog.onLogEntryChanged
        val onStartEditingLogEntry: () -> Unit = findsLog.onStartEditingLogEntry
        val onSaveLogEntry: () -> Unit = findsLog.onSaveLogEntry
        val onCancelLogEntryEditing: () -> Unit = findsLog.onCancelLogEntryEditing
        val onAddLogPhoto: (PhotoSource) -> Unit = findsLog.onAddLogPhoto
        val onRemoveLogPhoto: (LogPhoto) -> Unit = findsLog.onRemoveLogPhoto
        val onPullLogPhoto: (LogPhoto) -> Unit = findsLog.onPullLogPhoto
        val onDeleteLogEntry: (String) -> Unit = findsLog.onDeleteLogEntry
        val onSaveLogErrorDismissed: () -> Unit = findsLog.onSaveLogErrorDismissed
        val onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = findsLog.onDeleteGalleryPhoto
        val onAddGalleryPhoto: (PhotoSource) -> Unit = findsLog.onAddGalleryPhoto
        // J4b L3: an album photo's long-press Delete (pending, with Undo); see [JournalTab].
        val onRequestDeleteGalleryPhoto: ((String) -> Unit)? = findsLog.onRequestDeleteGalleryPhoto
        // J4b L1: a find tile's long-press Edit (`MushroomLogViewModel.onOpenEntryForEditing`); see [JournalTab].
        val onOpenLogEntryForEditing: ((String) -> Unit)? = findsLog.onOpenLogEntryForEditing
        // AvailabilityScreenJournalEntries.
        val cartographyUiState: CartographyUiState = journalEntries.cartographyUiState
        // J8-3: the entry report's "Show on map" and "Hide from map"; threaded to [JournalTab].
        val onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit = journalEntries.onSetCartographyEntryShownOnMap
        val onOpenCartographyEntry: (String) -> Unit = journalEntries.onOpenCartographyEntry
        val onStartCartographyEntry: (LocalDate) -> Unit = journalEntries.onStartCartographyEntry
        val onCloseCartographyEntry: () -> Unit = journalEntries.onCloseCartographyEntry
        val onCartographyTextChanged: (String) -> Unit = journalEntries.onCartographyTextChanged
        val onCartographyTagsChanged: (List<String>) -> Unit = journalEntries.onCartographyTagsChanged
        val onSetFindDecision: (String, Boolean) -> Unit = journalEntries.onSetFindDecision
        val onSetTrackDecision: (String, Boolean) -> Unit = journalEntries.onSetTrackDecision
        val onSetWaypointDecision: (String, Boolean) -> Unit = journalEntries.onSetWaypointDecision
        val onSetOfflineRegionDecision: (Long, Boolean) -> Unit = journalEntries.onSetOfflineRegionDecision
        // Data part A (dispatch 2026-09-28-667): a group switch in the entry editor's "In this entry" panel.
        val onSetEntryGroupIncluded: (EntryGroup, Boolean) -> Unit = journalEntries.onSetEntryGroupIncluded
        val onToggleKeptPhoto: (String) -> Unit = journalEntries.onToggleKeptPhoto
        val onAcquirePhotoForCartographyEntry: (PhotoSource) -> Unit = journalEntries.onAcquirePhotoForCartographyEntry
        val onFinishCartographyEntry: () -> Unit = journalEntries.onFinishCartographyEntry
        val onSaveCartographyEntry: () -> Unit = journalEntries.onSaveCartographyEntry
        val onDiscardCartographyEntryChanges: () -> Unit = journalEntries.onDiscardCartographyEntryChanges
        val onSaveCartographyEntryAsDraft: () -> Unit = journalEntries.onSaveCartographyEntryAsDraft
        // Clears `CartographyUiState.saveErrorMessage` once [JournalTab]'s Toast of it has shown (intent `2026-09-28-68`).
        val onCartographySaveErrorDismissed: () -> Unit = journalEntries.onCartographySaveErrorDismissed
        val onDeleteCartographyEntry: (String) -> Unit = journalEntries.onDeleteCartographyEntry
        // J4b L2: an Entries card's swipe Delete (pending, with Undo); see [JournalTab].
        val onRequestDeleteCartographyEntry: ((String) -> Unit)? = journalEntries.onRequestDeleteCartographyEntry
        // AvailabilityScreenEntryReportData.
        val getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData = entryReportData.getCartographyEntryMapData
        // F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, for the Journal cards' thumbnails.
        val getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = entryReportData.getSavedTrackPaths
        val getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary? = entryReportData.getCartographyEntryOfflineRegion
        val getCartographyEntryCurrentLocation: suspend () -> LocationResult = entryReportData.getCartographyEntryCurrentLocation
        // AvailabilityScreenOfflineMaps.
        val onOfflineMapLatChanged: (String) -> Unit = offlineMaps.onOfflineMapLatChanged
        val onOfflineMapLngChanged: (String) -> Unit = offlineMaps.onOfflineMapLngChanged
        val onOfflineMapRadiusChanged: (Int) -> Unit = offlineMaps.onOfflineMapRadiusChanged
        val onOfflineMapNameChanged: (String) -> Unit = offlineMaps.onOfflineMapNameChanged
        val onOfflineMapsOpened: () -> Unit = offlineMaps.onOfflineMapsOpened
        val onDownloadOfflineMaps: () -> Unit = offlineMaps.onDownloadOfflineMaps
        val onDeleteOfflineRegion: (Long) -> Unit = offlineMaps.onDeleteOfflineRegion
        val onDownloadAgain: (Long) -> Unit = offlineMaps.onDownloadAgain
        // AvailabilityScreenRecords.
        val tracks: List<Track> = records.tracks
        val waypoints: List<Waypoint> = records.waypoints
        val waypointsErrorMessage: String? = records.waypointsErrorMessage
        val waypointEntryReferenceCounts: Map<String, Int> = records.waypointEntryReferenceCounts
        val onDropWaypoint: (LatLng, String) -> Unit = records.onDropWaypoint
        val onTracksOpened: () -> Unit = records.onTracksOpened
        val getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = records.getFullRecord
        val onDeleteWaypoint: (String) -> Unit = records.onDeleteWaypoint
        // Part 2 follow-ups F1 item 5 (owner "Option A"): a finished track's swipe or details Delete asks for a pending delete with Undo; `null` (the default) leaves tracks without a delete.
        val onDeleteTrack: ((String) -> Unit)? = records.onDeleteTrack
        // Set when a committed track delete failed and the track is back; shown above the Tracks list.
        val tracksErrorMessage: String? = records.tracksErrorMessage
        // AvailabilityScreenSearchInputs.
        val onDismissTaxonSuggestions: () -> Unit = searchInputs.onDismissTaxonSuggestions
        val onUseCurrentLocation: () -> Unit = searchInputs.onUseCurrentLocation
        val onTaxonSearchQueryChanged: (String) -> Unit = searchInputs.onTaxonSearchQueryChanged
        val onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit = searchInputs.onTaxonSearchResultSelected
        val onPlaceTripPin: (LatLng, LocalDate, String) -> Unit = searchInputs.onPlaceTripPin
        val onManualLatChanged: (String) -> Unit = searchInputs.onManualLatChanged
        val onManualLngChanged: (String) -> Unit = searchInputs.onManualLngChanged
        val onSearchManualCoordinates: () -> Unit = searchInputs.onSearchManualCoordinates
        val onRecentSearchSelected: (CachedSearchSummary) -> Unit = searchInputs.onRecentSearchSelected
        // RECORD -723: the search bar's Clear; see AvailabilityScreen's own parameter.
        val onClearSearch: () -> Unit = searchInputs.onClearSearch
        val onRadiusChanged: (Int) -> Unit = searchInputs.onRadiusChanged
        val onMonthSelected: (Int) -> Unit = searchInputs.onMonthSelected
        // AvailabilityScreenRecording.
        val isRecording: Boolean = recording.isRecording
        val startRecordingErrorMessage: String? = recording.startRecordingErrorMessage
        val breadcrumbPoints: List<LatLng> = recording.breadcrumbPoints
        // See [AvailabilityScreen]'s own `recordingSundownLine` doc comment.
        val recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine? = recording.recordingSundownLine
        val onToggleRecording: () -> Unit = recording.onToggleRecording
        // Phase 2.6 B2 (dispatch 2026-10-19-40): the map and navigation values, unpacked from mapInputs under the parameter names
        // they replaced, with those parameters' types and comments, so nothing below this block changed. None is a getter.
        val mapSlot: MapSlot = mapInputs.mapSlot
        val mapIconClusterPosition: MapIconClusterPositionState = mapInputs.mapIconClusterPosition
        // Part 1 layout fixes, item 4: the Maps tab's camera, kept above the tab switch; threaded to [CompactMapTab].
        val mapCameraMemory: MapCameraMemory = mapInputs.mapCameraMemory
        // Item 8 (dispatch 2026-09-29-57): what "Open in Journal" leaves for the Maps tab that Back returns to; see [MapReturnMemory].
        val mapReturnMemory: MapReturnMemory = mapInputs.mapReturnMemory
        val mapRenderMode: MapRenderMode = mapInputs.mapRenderMode
        // Map layers L0b: the Maps tab's Layers sheet, legend and saved records ([MapLayersControls]),
        // threaded to [CompactMapTab], and the shared layer choices threaded to the Journal's entry map.
        // Nothing here reads it.
        val mapLayers: MapLayersControls = mapInputs.mapLayers
        val isNightMode: Boolean = mapInputs.isNightMode
        val mapWaypoints: List<Waypoint> = mapInputs.mapWaypoints
        val returnToStart: ReturnToStartInfo? = mapInputs.returnToStart
        val isReturning: Boolean = mapInputs.isReturning
        val isNavigating: Boolean = mapInputs.isNavigating
        // See [AvailabilityScreen]'s own `isArrivalHeld` doc comment (RECORD -845).
        val isArrivalHeld: Boolean = mapInputs.isArrivalHeld
        val isOffTrack: Boolean = mapInputs.isOffTrack
        // See [CompactMapTab]'s own `quickSettings` doc comment (dispatch 2026-09-28-645).
        val quickSettings: MapQuickSettings? = mapInputs.quickSettings
        val compassProvider: CompassProvider = mapInputs.compassProvider
        val computeTrueHeading: ComputeTrueHeadingUseCase = mapInputs.computeTrueHeading
        val navigationTarget: Waypoint? = mapInputs.navigationTarget
        val returnRoute: ReturnRoute = mapInputs.returnRoute
        val routeLine: RouteLine? = mapInputs.routeLine
        val onRetryRoute: () -> Unit = mapInputs.onRetryRoute
        // Dispatch 2026-09-28-502: navigating to a chosen waypoint, threaded to [CompactMapTab] and [JournalTab]; see [WaypointNavigateControls].
        val waypointNavigate: WaypointNavigateControls = mapInputs.waypointNavigate
        val showDecimalDegrees: Boolean = mapInputs.showDecimalDegrees
        val onToggleCoordinateFormat: () -> Unit = mapInputs.onToggleCoordinateFormat
        val navigationFollowing: Boolean = mapInputs.navigationFollowing
        val navigationViewRequestId: Int = mapInputs.navigationViewRequestId
        val onLeftNavigationView: () -> Unit = mapInputs.onLeftNavigationView
        val onReturnToRoute: () -> Unit = mapInputs.onReturnToRoute
        val navigationZoomPending: Boolean = mapInputs.navigationZoomPending
        val onNavigationZoomApplied: () -> Unit = mapInputs.onNavigationZoomApplied
        val basemap: Basemap = mapInputs.basemap
        val onLocateMe: () -> Unit = mapInputs.onLocateMe
        val onMapFullscreenChanged: (Boolean) -> Unit = mapInputs.onMapFullscreenChanged
        // M1: what the Maps tab's glyph bubbles look records up in; threaded to [CompactMapTab].
        val mapBubbleSources: MapRecordSources = mapInputs.mapBubbleSources
        val onMapModeChange: (MapMode) -> Unit = mapInputs.onMapModeChange
        val onToggleReturning: () -> Unit = mapInputs.onToggleReturning
        val onClearMapTaxonFilter: () -> Unit = mapInputs.onClearMapTaxonFilter
        // SearchDropdown, under ActiveSearchSummary — see that composable's own onToggleSearch doc
        // comment. Local to this scaffold, not AvailabilityUiState: which panel is showing is a
        // display decision the ViewModel has no part in, same reasoning as mapMode/drawerPanel above.
        val showSearchDropdownState = remember { mutableStateOf(false) }
        var showSearchDropdown by showSearchDropdownState
        // The search bar's one-shot "open the manual coordinates" request (continuation 2026-09-28-40)
        // was armed here; dispatch 2026-09-28-697 put the coordinates at the top of SearchDropdown with
        // no fold, so there is nothing left to open and the request is gone.
        // Same measured clearance [CompactMapTab] already computes for its own compass strip (see
        // that composable's own compassStripClearance doc comment) — recomputed here rather than
        // threaded through as a parameter, since it depends only on MaterialTheme.typography and
        // this scaffold's own LocalDensity, not on anything CompactMapTab measures live. Used below
        // to keep SearchDropdown's own panel (filled at the map chrome's alpha) starting below the strip rather than painting
        // over it, on the Map tab specifically — the project owner's own direct ask: "have the
        // search window extend just below the compass strip to avoid overlaying it so people can
        // still track it if needed."
        val searchDropdownCompassStripTextMeasurer = rememberTextMeasurer()
        val searchDropdownCompassStripLabelStyle = MaterialTheme.typography.labelMedium
        val searchDropdownCompassStripDensity = LocalDensity.current
        val compassStripClearance = remember(searchDropdownCompassStripLabelStyle, searchDropdownCompassStripDensity) {
            with(searchDropdownCompassStripDensity) {
                searchDropdownCompassStripTextMeasurer.measure("Mg", searchDropdownCompassStripLabelStyle).size.height.toDp()
            }
        }
        // SearchEntryBar's own real rendered height, on the Map tab where it now composes as an
        // overlay above the map (this scaffold's own CompactMapTab call site below) rather than
        // in normal document flow — CompactMapTab needs it as a top inset so the compass strip,
        // the observation bubble's own minY, and the taxon-filter chip's top padding all shift
        // down to sit below the bar instead of underneath it. Derived the same one-time-
        // measurement way as compassStripClearance itself (fieldHeight there is exactly
        // compassStripClearance * 2 — see SearchEntryBar's own fieldHeight doc comment) plus the
        // bar's own fixed vertical padding and divider, matching its real Column structure
        // exactly. A plain derived Dp, not a live re-measurement: CompactMapTab's own
        // compassStripClearance doc comment documents a confirmed, bisected regression from
        // reading a real onGloballyPositioned height back through state for this exact class of
        // positioning — this stays a one-time text-measurement-derived constant instead, the
        // proven-safe shape that doc comment prescribes.
        val searchBarHeight = compassStripClearance * 2 + Spacing.xs * 3 + DividerDefaults.Thickness
        // RECORD -943: the Maps tab's pill is thinner (MAP_PILL_TOP_PADDING and MAP_PILL_BOTTOM_PADDING in SearchEntryBar); its
        // height, which everything placed by the bar's bottom on that tab reads, follows. The other tabs keep searchBarHeight.
        // RECORD -951: never under the pill's 40 dp floor (MAP_PILL_MIN_HEIGHT), as SearchEntryBar lays it out.
        val mapPillHeight = (compassStripClearance * 2 + MAP_PILL_TOP_PADDING + MAP_PILL_BOTTOM_PADDING + DividerDefaults.Thickness)
            .coerceAtLeast(MAP_PILL_MIN_HEIGHT)
        // RECORD -729 (the owner: "The search bar height can change to meet the height of the strip"; on navigation, "Strip
        // only"): in a short landscape window on the Maps tab, not navigating, the bar takes the compass strip's measured height
        // (CompactMapTab's onLandscapeStripHeightMeasured), and everything placed by the bar's bottom here follows it, as -709
        // has things follow the strip's measured bottom in portrait. Equal heights mean the bar's bottom is the strip's too.
        // 0 until the strip is measured, where the bar keeps its own height. Portrait never sets it. Since dispatch 2026-10-12-08
        // (RECORD -951) navigating too: the strip stays beside the bar, and nothing resizes when navigation starts.
        val landscapeStripHeightState = remember { mutableStateOf(0.dp) }
        var landscapeStripHeight by landscapeStripHeightState
        // RECORD -732: the strip's measured width there; the bar takes the rest of the room. 0 until measured.
        val landscapeStripWidthState = remember { mutableStateOf(0.dp) }
        var landscapeStripWidth by landscapeStripWidthState
        // Dispatch 2026-10-12-08 (RECORD -946, -950, -951): while navigating the compass strip stays beside the bar, the two still
        // the top bar, and nothing resizes when navigation starts (the owner: "Keep it exactly like this size. Exactly as we have
        // it today"; "Just rearrange it"). So the bar meets the strip navigating or not; it was "not navigating" only.
        val barMeetsStrip = isLandscapeWindow && compactTab() == CompactTab.MAP
        val mapSearchBarHeight = if (barMeetsStrip && landscapeStripHeight > 0.dp) landscapeStripHeight else mapPillHeight
        // A height left over from an earlier landscape Maps tab is not reused: it is measured again on the way back.
        LaunchedEffect(barMeetsStrip) { if (!barMeetsStrip) { landscapeStripHeight = 0.dp; landscapeStripWidth = 0.dp } }
        // Dispatch 2026-10-17-09, the owner's addition (RECORD -1227, "Move to Maps' spot"; and "The layout resizing between tabs
        // looks like a UI glitch"): in a landscape window the pill on List, Seasonal and Journal takes the Maps pill's place and
        // size, which on Maps follow the compass strip's measured width and height (RECORD -729, -732). The strip is drawn on Maps
        // only, so its last measurement in this landscape window is kept here for the other tabs. Separate from the two values
        // above, which Maps resets on leaving and measures again on its way back, so the Maps tab reads exactly what it read
        // before. 0 until Maps has been shown in this landscape window; the other tabs then take the Maps pill's own
        // before-measurement half width (RECORD -729) and its own height.
        // Dispatch 2026-10-18-03, item 2 (Forager RECORD -1269; Elder's top-up audit, item 2: half width, and the pill's own
        // height, until Maps had been shown in the window): the other tabs' pill is now worked out from the window as the Maps
        // strip is sized, before Maps is ever shown (landscapeTabPillFit for the width, the strip's one line for the height,
        // below). The measured height is kept only for what that one line does not model, the strip's evening lines (sundown,
        // Back by); saveable, so a process restart in landscape keeps it. Was `lastMapsStripWidth` and this, both plain
        // `remember`, 0 until Maps was shown.
        val lastMapsStripHeightDpState = rememberSaveable { mutableFloatStateOf(0f) }
        var lastMapsStripHeightDp by lastMapsStripHeightDpState
        val lastMapsStripHeight = lastMapsStripHeightDp.dp
        LaunchedEffect(isLandscapeWindow) { if (!isLandscapeWindow) lastMapsStripHeightDp = 0f }
        // The same inputs CompactMapTab sizes its landscape strip from (landscapeStripFit), from the same state.
        val tabPillStripNeed = rememberLandscapeStripNeed(uiState.liveAltitudeMeters, uiState.unitSystem, uiState.liveLocation, quickSettings != null)
        val tabPillBarFloorPx = rememberLandscapeBarFloorPx(uiState, uiState.distanceUnit)
        val tabPillStripLineHeight = rememberLandscapeStripLineHeight(uiState.liveLocation, quickSettings != null)
        // RECORD -709 (the owner: "Follow the strip's real height (Recommended)"): the Maps tab's compass strip as measured,
        // handed up through searchBarSlot; 0 while it is not measured (before its first layout, while navigating, in a landscape
        // window), where the dropdown keeps the one-line clearance above. See CompactMapTab's compassStripBottomClearance.
        val mapCompassStripHeightState = remember { mutableStateOf(0.dp) }
        var mapCompassStripHeight by mapCompassStripHeightState
        // Fullscreen-fixes dispatch, Item 2 ("slide the chrome away instead of cutting it") —
        // animated rather than the raw if/else this used to be, so CompassElevationStrip (and the
        // observation bubble/TaxonMapFilterChip below it, both of which also read CompactMapTab's
        // own topInset) slides smoothly into the space SearchEntryBar vacates instead of jumping
        // there the instant fullscreen toggles. Purely a Dp value driving Modifier.padding on Box
        // children of CompactMapTab's own fillMaxSize() Box — animating it has no bearing on that
        // Box's own size, the same "pure overlay" reasoning confirmed for SearchEntryBar's and
        // ForagerBottomNav's own slides below. Same MotionTokens.panelMotionSpec() this file
        // already uses for AddActionTile's own slide+fade, so the three move in visual lockstep.
        // coerceAtLeast(0.dp): panelMotionSpec() is a spring spec that accepts mild overshoot by
        // design (that spec's own doc comment) — animating toward 0.dp, it transiently swings
        // negative, and every consumer of this value below applies it as Modifier.padding(top =
        // ...), which throws IllegalArgumentException for a negative Dp. Confirmed, not guessed:
        // this crashed AvailabilityScreenMapIconStackTest's own new fullscreen-toggle test on the
        // very first run, uncoerced.
        val animatedTopInset by animateDpAsState(
            targetValue = if (isMapFullscreen()) 0.dp else mapSearchBarHeight,
            animationSpec = MotionTokens.panelMotionSpec(),
            label = "mapTopInset",
        )
        val safeAnimatedTopInset = animatedTopInset.coerceAtLeast(0.dp)
        // Dispatch 2026-09-28-104, item 2: the search column's measured height (the bar, and the notice below the compass strip when
        // one shows). The Maps tab keeps the icon cluster below its bottom while a notice shows; nothing else reads it.
        val searchChromeHeightPxState = remember { mutableIntStateOf(0) }
        var searchChromeHeightPx by searchChromeHeightPxState
        // "Set on map" (SearchDropdown's own Advanced Search section, dispatch C item 2) hands off to the exact same
        // pan-to-centre-pin-plus-confirm flow every other pin placement in this app uses
        // (CentrePinLocationPickerOverlay) rather than a second picker — see CompactMapTab's own
        // pendingAction-driven overlay for the established shape this mirrors. Lifted to this
        // scaffold's own scope, not into CompactMapTab, because the trigger (the dropdown) lives up
        // here and can be tapped from any bottom-nav tab, not just while already on Maps.
        val pickingSearchLocationOnMapState = remember { mutableStateOf(false) }
        var pickingSearchLocationOnMap by pickingSearchLocationOnMapState
        // UI split Phase 2.5: the dropdown's two States, wrapped, not copied (ScaffoldSearchDropdownState's doc comment).
        val searchDropdownState = ScaffoldSearchDropdownState(showSearchDropdownState, pickingSearchLocationOnMapState)
        // Nested inside this scaffold, so Compose's OnBackPressedDispatcher tries it before the
        // four home-chain handlers above (isDrawerOpen/isMapFullscreen/compactTab/exit-confirmation)
        // — the same "innermost enabled handler wins" precedence those four already rely on for
        // JournalTab/CompactSettingsTab/CompactMapTab. Genuinely missing before this fix: back
        // pressed while this panel was open (species field focused, keyboard up) fell straight
        // through to whichever of those four was enabled instead of just closing this panel first.
        // Intent 2026-09-28-44, F4: off while the Tools drawer is open, the drawer fix's pattern
        // (JournalTab's backEnabled). This handler is registered after AvailabilityScreen's
        // BackHandler(enabled = isDrawerOpen), and Tools does not close the dropdown, so with the
        // dropdown open under the drawer this took Back and the drawer stayed open. The dropdown
        // stays open under the drawer; the next Back, with the drawer closed, closes it.
        BackHandler(enabled = showSearchDropdown && !isDrawerOpen()) {
            showSearchDropdown = false
        }
        // Same "actually hide the IME" fix as isDrawerOpen's own LaunchedEffect above — this panel
        // holds the manual-coordinate TextFields, so it's exactly as prone to a stuck keyboard on
        // close (the BackHandler above, or collapsing the bar again) as the drawer's own fields are.
        // Dispatch 2026-09-28-312, item 12: appClearFocusInProgress is up while the clearFocus below runs, so the focus
        // the framework hands back to the search field, out of touch mode, does not reopen the dropdown this effect
        // has just seen close (the holder's own comment in AvailabilityScreen has the rule and the trace). The field's
        // two onFieldFocused below read it. Rejected: not clearing focus on close at all. The field would stay
        // focused, a second tap on it would gain no focus, and the dropdown would not open again; the keyboard would
        // need another way to be hidden.
        LaunchedEffect(showSearchDropdown) {
            if (!showSearchDropdown) {
                appClearFocusInProgress[0] = true
                try {
                    focusManager.clearFocus(force = true)
                } finally {
                    appClearFocusInProgress[0] = false
                }
                keyboardController?.hide()
            }
        }

        // Pings the device's live location once, as soon as the compact Maps experience is shown,
        // so the map opens already centred on it rather than waiting for an explicit locate-me tap
        // — see CompactMapTab's own doc comment on the pre-search display region this feeds. Fires
        // once per this scaffold's own composition lifetime (i.e. once per app open on a compact
        // window), not once per Maps-tab visit, since it's hoisted here rather than into
        // CompactMapTab itself, which enters and leaves composition on every bottom-nav switch.
        LaunchedEffect(Unit) { onLocateMe() }

        // Intent 2026-09-28-44, F3 (the owner: "Keep finds open too (Recommended)"): backgrounding no
        // longer closes an open find. This was an ON_STOP observer here (Workstream L4b-R) calling the
        // raw onLeaveLogEntryEditingIncidentally whenever a find was open on the Journal, in its report
        // or its editor, with the camera round trip excepted (logPhotoAcquisitionInFlight). A find open
        // in view or edit is now still open when the user returns, as a day entry already was. Nothing
        // is lost by not closing: a find's draft is written on every keystroke (onEntryEdited), and a
        // process death reloads it into Drafts. The observer is removed rather than gated, so the
        // camera exception it needed has no reader here any more (reported).

        // Search-focus-and-hide dispatch, Item 2's own hide condition (SearchEntryBar call sites
        // below) — "any entry open" (view or edit), not "specifically editing": from here,
        // CartographyEntryMode/JournalEntryMode (which distinguish the two) are local state one
        // level down in CartographyScreen.kt/JournalTab.kt, invisible at this scope. Owner decision:
        // hiding while merely viewing is acceptable rather than lifting that mode into shared state.
        val isEditingJournalEntry = logUiState.editingEntry != null || cartographyUiState.editingEntry != null
        // RECORD -692 (the owner: "Search bar returns after the slide (Recommended)"): the rule above, extended through the
        // slide-out. Once the entry closes, its page slides away for a moment (motion Part 3), and the bar stays hidden until no
        // Journal page is leaving any more; then it appears, at once, as before. Without this, the bar came back while the
        // closing editor still held focus, and when the editor went, focus fell to the bar's field and opened its dropdown over
        // the Journal (LeavingTheJournalFixesTest F1, measured frame by frame at motion Part 3's build). The hold starts on the
        // composition the entry closes in (entryJustClosed), so the bar is not composed for even one frame in between.
        val journalLeavingPages = remember { LeavingPages() }
        val wasEditingJournalEntry = remember { booleanArrayOf(false) }
        val entryJustClosed = wasEditingJournalEntry[0] && !isEditingJournalEntry
        var holdSearchAfterEntry by remember { mutableStateOf(false) }
        SideEffect {
            wasEditingJournalEntry[0] = isEditingJournalEntry
            if (entryJustClosed) holdSearchAfterEntry = true
        }
        LaunchedEffect(holdSearchAfterEntry, journalLeavingPages.count) {
            if (holdSearchAfterEntry && journalLeavingPages.count == 0) holdSearchAfterEntry = false
        }
        val journalSearchHeld = isEditingJournalEntry || entryJustClosed || (holdSearchAfterEntry && journalLeavingPages.count > 0)
        // RECORD -1330 to -1333: see JournalSearchPillRoom. Its full height is the pill's landscape height, set where the pill's
        // slot is composed, after composition (SideEffect).
        val journalSearchPillRoom = remember { JournalSearchPillRoom() }
        val journalSearchPillRoomDensity = LocalDensity.current
        // Search-focus-and-hide dispatch, Item 1: tried and deliberately NOT built here. The natural
        // generalization of the established LaunchedEffect(showSearchDropdown) pattern just above
        // — LaunchedEffect(isEditingJournalEntry) { focusManager.clearFocus(force = true) }, firing on
        // every transition rather than one direction — was built, and it made both currently-failing
        // tests fail differently, not pass: the "Advanced search" dropdown still opened (confirmed via
        // composeRule.onRoot().printToLog()/onAllNodesWithText node counts, not guessed), because
        // clearing focus at the exact recomposition where Item 2's hide condition also flips SearchEntryBar
        // back into existence left it as the only focusable candidate with nothing else claiming
        // focus — which is exactly the precondition this codebase's own default-focus-assignment
        // behavior needs to reclaim it right back. Removing the effect and keeping only Item 2's own
        // hide condition (SearchEntryBar not composed at all while `isEditingJournalEntry`, so there
        // is no candidate to reclaim) turned both tests green with no clearFocus() call anywhere in
        // this file. CartographyScreen's own ON_RESUME clearFocus() (that composable's own doc
        // comment) stays — it fires while an edit screen is still open, not into this same
        // hide/remount race, and full-suite verification found no regression from keeping it. The
        // "entering a fresh edit" direction of Item 1 (as opposed to "returned to") was never
        // exercised by any test either way and is not built — see this dispatch's own report for the
        // reasoning on leaving it out rather than guessing at a shape that avoids the same race.

        // Workstream L4b-R2: every *in-app* incidental exit (back arrow inside the edit form, the
        // journal tab's own BackHandler, switching to another bottom-nav tab, and — via the shared
        // leaveLogEntryEditingOfferingDiscard/logDraftSnackbarHostState this function hoisted to its
        // own top level) shares this one wrapped callback, so the
        // same Snackbar covers every one of them from a single place. Backgrounding above is the deliberate exception (see
        // that effect's own comment on why). The exit itself is never blocked on this:
        // onLeaveLogEntryEditingIncidentally() already ran, and the Snackbar only offers an undo: a
        // dismissed or ignored one leaves the draft exactly where that call already put it (owner
        // decision, 2026-08-25: Gmail-drafts-style).
        val onBottomNavTabSelected: (CompactTab) -> Unit = { tab ->
            // Intent 2026-09-28-44, F3 (the owner: "Keep finds open too (Recommended)"): switching
            // away from the Journal, or opening Tools over it, no longer leaves an open find. It
            // called leaveLogEntryEditingOfferingDiscard here (Workstream L4b), so a find open in
            // view or edit was closed, where a day entry stayed open. The find is now still open on
            // return, in the mode it was in (JournalTab's findEntryModeState). A route that opens
            // another find over it leaves it first (onLogFindHere below; AvailabilityScreen's
            // onOpenFind and its wide onLogFindHere).
            if (tab == CompactTab.TOOLS) {
                // CompactTab.TOOLS's own doc comment: opens the drawer as an
                // overlay over whatever tab is already showing, rather than
                // becoming compactTab itself — the same drawer the removed
                // MapIconBar search icon used to open (see that composable's own
                // doc comment), including the same "dismiss any open taxon
                // suggestion popup first" step openSearchDrawer used to do.
                onDismissTaxonSuggestions()
                onIsDrawerOpenChange(true)
            } else {
                // Fullscreen-fixes dispatch, Item 1: now that the bottom nav floats over the map
                // (reachable) instead of disappearing while fullscreen, tapping any other tab from
                // it is a real, newly-reachable path that must not leave isMapFullscreen stuck true
                // for a tab that isn't Map — the invariant this file's own fullscreen-scoped code
                // relies on ("isMapFullscreen can only be true while compactTab == MAP") no longer
                // holds by construction (the bar being unreachable) once the bar is reachable during
                // fullscreen, so it has to be enforced explicitly here instead.
                // Still exits fullscreen — not an exception to CLAUDE.md's "UX defaults" rule,
                // see the note recorded beside it: the nav is off screen in fullscreen, so this
                // path is unreachable from there in practice; it holds the invariant, and
                // persists the exit when it does fire.
                if (tab != CompactTab.MAP && isMapFullscreen()) {
                    onIsMapFullscreenChange(false)
                    onMapFullscreenChanged(false)
                }
                onCompactTabChange(tab)
                // Keep the shared ResultsTab-driven state in sync for the three
                // destinations both it and CompactTab describe — see compactTab's
                // own doc comment for why.
                when (tab) {
                    CompactTab.LIST -> onSelectedTabChange(ResultsTab.LIST)
                    CompactTab.MAP -> onSelectedTabChange(ResultsTab.MAP)
                    CompactTab.SEASONAL -> onSelectedTabChange(ResultsTab.SEASONAL)
                    CompactTab.JOURNAL -> Unit
                    CompactTab.TOOLS -> Unit // unreachable — handled above
                }
            }
        }
        // ForagerBottomNav's own real measured height, in px, hoisted here rather than kept local
        // to CompactMapTab (which composes the Map tab's own instance) — fullscreen-fixes dispatch
        // ("still shifting"). A flat 80.dp constant was tried here first and undershoots this bar's
        // own real rendered height by exactly the system navigation-bar inset, since Material3's
        // NavigationBar applies NavigationBarDefaults.windowInsets internally (confirmed against
        // AndroidX's own NavigationBar.kt) — invisible under Robolectric, which reports zero window
        // insets (see CLAUDE.md's own "Known pitfalls"), so a test measuring this constant's own
        // value could never catch the mismatch. A measured height can't drift from what's actually
        // drawn, the same reasoning mapIconClusterHeightPx already established in this file — read back
        // via bottomNavHeight below, needed in two places: the search-dropdown dismiss scrim and
        // its own SearchDropdown panel both need this same band excluded (their own doc comments).
        val bottomNavHeightPxState = remember { mutableFloatStateOf(0f) }
        var bottomNavHeightPx by bottomNavHeightPxState
        val bottomNavDensity = LocalDensity.current
        // Landscape B1: in a short landscape window the bottom bar is replaced by a navigation
        // rail on the charger-port edge, so neither ForagerBottomNav call site renders. Two
        // containers for the one rail (Resolution R12, revised on the owner's correction: "the
        // map resizes when hiding the UI and that's a UX problem"):
        //  - Map tab: the rail is an 80% overlay on the map, composed inside CompactMapTab where
        //    the bottom bar's overlay is in portrait. The map stays full-bleed and never changes
        //    size; the map's *controls* are padded clear of the rail instead (mapControlsPadding
        //    below), the way portrait keeps them clear of the bottom bar by its measured height.
        //    In fullscreen it slides off toward the port edge and back (B2's S7, CompactMapTab's call site;
        //    this line read "absent, with no animation", the interim R13 state, until motion Part 2, scout S4).
        //  - Every other tab: an opaque rail beside the content (railBeside), since there is no
        //    map to keep the size of and text under a translucent rail would hurt reading.
        // Portrait windows, short or not, are exactly as before. Since dispatch 2026-09-28-246 the rail is the
        // landscape layout of any window, tall or short, so a landscape tablet has it too.
        val showRail = isLandscapeWindow
        val railBeside = showRail && compactTab() != CompactTab.MAP
        // Dispatch 2026-10-18-10: a tablet or an unfolded foldable (smallest width 600 dp or more); see contentCapWidth below.
        val largeScreen = isLargeScreenWindow()
        // R18: no bottom band for a bar that is not there. The measured height is the portrait
        // bar's while turning into landscape (onGloballyPositioned stops firing once the bar
        // leaves composition), so it is zeroed, and read as zero in the meantime. In landscape
        // the rail's measured width takes its place, on the port side.
        LaunchedEffect(showRail) {
            if (showRail) bottomNavHeightPx = 0f
        }
        val bottomNavHeight = if (showRail) 0.dp else with(bottomNavDensity) { bottomNavHeightPx.toDp() }
        // The Map tab's overlaid rail's real measured width, in px — the landscape counterpart of
        // bottomNavHeightPx above, measured for the same reason (it includes the system
        // navigation-bar inset the rail consumes, which Robolectric reports as zero).
        val mapRailWidthPxState = remember { mutableFloatStateOf(0f) }
        var mapRailWidthPx by mapRailWidthPxState
        // UI split Phase 2.5: the frame's measured sizes, wrapped, not copied (ScaffoldMeasurements' doc comment).
        val scaffoldMeasurements = ScaffoldMeasurements(
            landscapeStripHeightState = landscapeStripHeightState,
            landscapeStripWidthState = landscapeStripWidthState,
            lastMapsStripHeightDpState = lastMapsStripHeightDpState,
            mapCompassStripHeightState = mapCompassStripHeightState,
            searchChromeHeightPxState = searchChromeHeightPxState,
            bottomNavHeightPxState = bottomNavHeightPxState,
            mapRailWidthPxState = mapRailWidthPxState,
        )
        val mapRailWidth = with(bottomNavDensity) { mapRailWidthPx.toDp() }
        // What the Map tab's controls are padded by in a short landscape window, and never the
        // map itself (R12, R13, R17, all revised): the displayCutout inset on the sides, so no
        // control sits in the cut-out band while the tiles draw under it; and on the port side the
        // rail's measured width, or, in fullscreen with the rail gone, the navigationBars inset,
        // so no control sits under the rail or the system bar. The top needs nothing here: the
        // Map tab's Scaffold padding already keeps the whole tab below the status bar, as in
        // portrait. Zero everywhere else, so portrait is untouched.
        // RECORD -956: the rail is on the screen's right at both rotations, but the system navigation bar stays on the port edge,
        // the screen's left at ROTATION_270: so the controls are kept clear of the navigation bar on both sides, always, and of the
        // rail on the right (the cut-out, on both sides, as before, wherever the camera falls).
        val mapControlsPadding = if (showRail) {
            val railInset = if (isMapFullscreen()) {
                WindowInsets(0.dp)
            } else if (portEdge == ScreenEdge.Left) {
                WindowInsets(left = mapRailWidth)
            } else {
                WindowInsets(right = mapRailWidth)
            }
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .union(railInset)
                .asPaddingValues()
        } else {
            PaddingValues(0.dp)
        }
        // Fullscreen-slide-out-fixes dispatch, Item 2: attribution's bottomInset must follow the
        // nav off screen, not hold the gap the nav used to occupy. bottomNavHeight above is a
        // *size* measurement (coordinates.size.height at the nav's own call site) — a slide is a
        // translation, so that size never changes during it, and once the nav's exit settles and
        // AnimatedVisibility disposes it, onGloballyPositioned stops firing and bottomNavHeightPx
        // simply keeps its last value forever. The inset does NOT animate for free; it needs this.
        //
        // Fullscreen target is 0 — the true bottom edge, level with MapLibre's own logo, the
        // dispatch's literal wording. A first version targeted the real system navigation-bar
        // inset instead (keeping the caption above the gesture pill, on the reasoning that the
        // nav's measured height already includes that inset); on device that read as the caption
        // still holding a band of empty map above the bottom, and the owner reversed it. Under
        // Robolectric the two were indistinguishable anyway (that inset is 0 there) — device-only
        // by construction; no test here claims to verify the on-screen result.
        //
        // navigationMotionSpec(), the spec the nav's own slide uses (its own call site in
        // CompactMapTab) — the dispatch named panelMotionSpec() for it, which the nav doesn't use;
        // the intent ("the same spec, so the two cannot drift out of sync") is what's honoured.
        // Same distance (the nav's own height) and same spring from the same trigger, so the two
        // curves match — one animation drives the nav's translation, this one drives the inset.
        // coerceAtLeast(0.dp): a spatial spring overshoots, and a negative Dp fed to
        // Modifier.padding throws — the exact crash animatedTopInset below already hit once.
        val animatedAttributionBottomInset by animateDpAsState(
            targetValue = if (isMapFullscreen()) 0.dp else bottomNavHeight,
            animationSpec = MotionTokens.navigationMotionSpec(),
            label = "attributionBottomInset",
        )
        val safeAttributionBottomInset = animatedAttributionBottomInset.coerceAtLeast(0.dp)
        // Part 2 follow-ups F1 item 2 (Part 2 item 37): in portrait fullscreen the inset above is 0 (the caption
        // goes to the true edge, the owner's ruling), which left MapLibre's own "i" inside the system navigation
        // band, where two real taps opened nothing. The button clears the navigation bar there instead: the real
        // inset, queried from the window, never a constant (Robolectric reports zero for it, so what MapLibre then
        // draws and whether a finger reaches the button is device-only). Out of fullscreen it is the nav's
        // measured height, the caption's own value, which already includes the bar. Not set (null) in the short
        // landscape window, where the rail and the end-edge inset above already govern the button, unchanged.
        val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val animatedAttributionButtonBottomInset by animateDpAsState(
            targetValue = if (isMapFullscreen()) navigationBarBottom else bottomNavHeight,
            animationSpec = MotionTokens.navigationMotionSpec(),
            label = "attributionButtonBottomInset",
        )
        val attributionButtonBottomInset: Dp? = if (showRail) null else animatedAttributionButtonBottomInset.coerceAtLeast(0.dp)
        // Part 1 layout fixes, item 5 (planner message 2026-09-28-98): MapLibre's "i" sits at the map's
        // bottom end, so beside the bottom inset above it keeps clear of whatever is on that end edge:
        // the overlaid rail, in a short landscape window at the rotation that puts the port there (90 in
        // a left-to-right layout). At the other rotation that edge is the punch-hole side, and the "i"
        // stays where it was, in the cut-out band: padding it inboard of the cut-out would put it under
        // the cluster's column. In fullscreen it goes to the edge, the caption's own treatment (the
        // planner's ruling: "It belongs with the caption"). Animated on the rail's own spec, as the
        // bottom inset is on the nav's. The rail's measured width includes the system bar it takes, which
        // Robolectric reports as zero: what the "i" clears on the phone is a device item.
        val attributionEndEdge = if (LocalLayoutDirection.current == LayoutDirection.Ltr) ScreenEdge.Right else ScreenEdge.Left
        val animatedAttributionEndInset by animateDpAsState(
            targetValue = if (showRail && !isMapFullscreen() && portEdge == attributionEndEdge) mapRailWidth else 0.dp,
            animationSpec = MotionTokens.navigationMotionSpec(),
            label = "attributionEndInset",
        )
        val safeAttributionEndInset = animatedAttributionEndInset.coerceAtLeast(0.dp)
        // Motion Part 2, item 7 (dispatch 2026-09-28-666, scout S6; the owner: "glide like the attribution insets beside them").
        // On the Maps tab the snackbar sits above the floating nav, or above the system bar in fullscreen and in the rail layout,
        // and rises above Return to Route while that shows (dispatch 2026-09-28-430). That height used to jump when any of these
        // changed; it now glides on the attribution insets' own spec. In fullscreen and the rail layout it is the system bar's
        // raw inset (navigationBarBottom, as the attribution button's), where it was a windowInsetsPadding: the same figure
        // unless something above has consumed that inset, which is device-only either way (Robolectric reports it as zero).
        // A tab change does not glide it: the Scaffold's own placement changes at once then (it rises above the solid bar off
        // Maps), so this figure jumps with it and the snackbar stays where it was on screen.
        val snackbarMapBottom = glidingSnackbarBottom(
            target = if (compactTab() != CompactTab.MAP) {
                0.dp
            } else {
                (if (showRail || isMapFullscreen()) navigationBarBottom else bottomNavHeight) +
                    (if (isNavigating && !navigationFollowing) RETURN_TO_ROUTE_SNACKBAR_LIFT else 0.dp)
            },
            tab = compactTab(),
        )
        Scaffold(
            snackbarHost = {
                // UI split Phase 2.5 PR 4 (dispatch 2026-10-19-39): the host's content, moved to ScaffoldSnackbarHost.kt.
                ScaffoldSnackbarHost(
                    logDraftSnackbarHostState = logDraftSnackbarHostState,
                    compactTab = compactTab,
                    showRail = showRail,
                    mapControlsPadding = mapControlsPadding,
                    snackbarMapBottom = snackbarMapBottom,
                )
            },
            // Fullscreen-fixes dispatch ("still shifting"): Material3's own Scaffold falls back to
            // this value's own bottom inset for its reported content padding whenever bottomBar
            // composes no content (`bottomBarHeight?.toDp() ?: insets.calculateBottomPadding()`,
            // confirmed against AndroidX's own Scaffold.kt, not assumed) — exactly the Map tab's own
            // case now, in both fullscreen states. That fallback exists so content isn't drawn
            // under the real system navigation bar when nothing else protects it — correct in
            // general, but redundant here specifically: ForagerBottomNav's own instance inside
            // CompactMapTab's Box already protects itself the identical way (Material3's
            // NavigationBar applies NavigationBarDefaults.windowInsets internally, confirmed against
            // AndroidX's own NavigationBar.kt), the same as the bottomBar-hosted instance the other
            // three tabs still use. Left in place, Scaffold's own fallback shrinks CompactMapTab's
            // Box above the real inset strip a second time, leaving that strip permanently
            // undrawn — on-device only, since Robolectric reports zero window insets and never
            // exercises this fallback at all (CLAUDE.md's own "Known pitfalls" now records this).
            // Excluding only the bottom side, only for the Map tab, hands that strip back to the
            // embedded nav's own self-consumed inset instead of double-reserving it.
            contentWindowInsets = if (showRail && compactTab() == CompactTab.MAP) {
                // Landscape B1: the map runs the whole width, under the cut-out and under its
                // overlaid rail (R12/R17 revised); only the top is reserved, as in portrait. The
                // controls are padded one by one instead — mapControlsPadding above.
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
            } else if (showRail) {
                // RECORD -1242: no keyboard in these (see shortLandscapeContentInsets), so the docked rail keeps its full
                // height under a keyboard; the content column reserves it instead (below).
                shortLandscapeContentInsets(railEdge = portEdge)
            } else if (compactTab() == CompactTab.MAP) {
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            } else {
                WindowInsets.safeDrawing
            },
            bottomBar = {
                // Fullscreen-fixes dispatch, Item 1 (third design, replacing two earlier attempts
                // that both failed AvailabilityScreenLayoutTest's own measured-height assertion —
                // see that test's own doc comment for the concrete numbers). The Map tab's own nav
                // no longer lives in this slot at all, in either fullscreen state: it renders as an
                // always-present overlay inside CompactMapTab's own content Box instead (that
                // composable's own doc comment). So this slot renders nothing while compactTab is
                // MAP, and the ordinary opaque bar otherwise — its own reported height then depends
                // only on compactTab, which the fullscreen toggle never changes, so Scaffold's own
                // content padding is stable across that toggle by construction, not by a padding
                // trick applied after the fact.
                // Landscape B1: nothing here in a short landscape window — the rail beside the
                // content replaces this bar (showRail's own comment).
                // Motion Part 2, item 2 (dispatch 2026-09-28-666, scout S3; the owner, RECORD -651: "Fade with the tab": "Solid
                // to 80% and back fades in time with the tab change"). The Maps tab's own 80% bar is inside that tab and fades
                // with it; this solid bar fades on the same spec, so across the change the fill passes between solid and 80%.
                // Its room comes and goes at once, as before, so this slot's reported height still depends on compactTab alone
                // (the paragraph above): leaving for Maps it reports no height at once and goes on drawing where it was
                // (zeroHeightDrawnAbove), taking no touch, for the length of the fade. Turning the phone plays no fade.
                TabChromeFade(
                    shown = compactTab() != CompactTab.MAP && !showRail,
                    windowKey = showRail,
                    zeroRoom = Modifier.zeroHeightDrawnAbove(),
                ) {
                    ForagerBottomNav(
                        selectedTab = compactTab(),
                        isDrawerOpen = isDrawerOpen(),
                        onTabSelected = onBottomNavTabSelected,
                        // Dispatch 2026-10-17-09 (RECORD -1224, -1225): the Maps bar's rounded top corners and outline, solid here
                        // (the default fill), so the bar keeps its shape across the tab switch.
                        topCornerRadius = MAP_CARD_CORNER_RADIUS,
                    )
                }
            },
        ) { padding ->
            // Scaffold's padding carries the system bar insets, so nothing here is laid
            // out under the status or navigation bar. No isMapFullscreen special-casing
            // needed any more — bottomBar's own reported height already never changes for
            // the Map tab (nothing renders there in either state), so the weight(1f) Box
            // below always gets the full remaining height on that tab, fullscreen or not.
            // Landscape B1: a Row, so on every tab but Map a short landscape window's opaque
            // navigation rail sits beside the content on the charger-port edge (Resolution R12,
            // revised) — first when that edge is the left, last when it is the right. The Map
            // tab's rail is an overlay inside CompactMapTab instead. Everywhere else the Row
            // holds the Column alone.
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Motion Part 2, item 2 (scout S4; the owner, RECORD -651: "Fade with the tab"): the opaque rail beside the
                // content fades in and out with the tab change, as the bottom bar does in portrait. Its room comes and goes at
                // once, as before; leaving for Maps it takes no width and is drawn where it was, taking no touch, and over the
                // incoming tab (zIndex: it comes first in the Row, so it would otherwise be drawn under it). Turning the phone,
                // or turning it over (the port edge changes side), plays no fade.
                TabChromeFade(
                    shown = railBeside && portEdge == ScreenEdge.Left,
                    windowKey = showRail to portEdge,
                    zeroRoom = Modifier.zIndex(1f).zeroWidthDrawnInPlace(atStart = true),
                ) {
                    ForagerNavigationRail(
                        selectedTab = compactTab(),
                        isDrawerOpen = isDrawerOpen(),
                        onTabSelected = onBottomNavTabSelected,
                        portEdge = portEdge,
                        // Dispatch 2026-10-17-09: the Maps rail's rounded inner corners and outline, solid here (the default fill).
                        innerCornerRadius = MAP_CARD_CORNER_RADIUS,
                    )
                }
                Column(
                    modifier = Modifier
                        // Landscape B1: the Row above carries Scaffold's padding now, and this
                        // Column takes whatever the rail (if any) leaves — in portrait, all of it.
                        .weight(1f)
                        // Landscape B3 (P11, R9)'s readable-width cap moved from here to the tab's content
                        // (readableContentCap, below) by dispatch 2026-10-17-09 (RECORD -1227): the search
                        // pill above the content now takes the Maps pill's place, which is not inside the cap.
                        .fillMaxHeight()
                        // RECORD -1242: beside the rail, the keyboard is reserved here, on the content column only, not on the
                        // Row (shortLandscapeChromeInsets above), so the page ends above the keyboard and the rail does not.
                        .then(if (railBeside) Modifier.windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)) else Modifier),
                ) {
                    // Landscape B3 (P11, R9): beside the opaque rail, the tab's content is capped at the
                    // readable width and centred in what the rail leaves. Once, here, not per destination.
                    // Seasonal's own cap (SeasonalTab) is the same 640 dp, so the two compose to 640.
                    // Scrolling stays each destination's own. Portrait and non-short windows skip this.
                    // Until dispatch 2026-10-17-09 the search bar was inside this cap too.
                    // Dispatch 2026-10-18-10 (RECORD -1340, the owner: "A: wider column", "Match content"): on a large screen the
                    // Journal's cap is 960 dp, one cap for every Journal screen, so its top line spans the same width as the content
                    // under it and does not change width between chips. Phones (smallest width under 600 dp) and the other tabs keep
                    // the 640 dp readable width.
                    // Amendment 4 (RECORD -1355, the owner: "Good. Make season and list the same", "Same as the Journal (960)"): List and
                    // Seasonal too, so on a large screen every tab beside the rail is the 960 dp column. Superseded: the Journal only.
                    val contentCapWidth = if (largeScreen) TABLET_JOURNAL_CONTENT_MAX_WIDTH else READABLE_CONTENT_MAX_WIDTH
                    val readableContentCap = if (railBeside) {
                        Modifier
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = contentCapWidth)
                            .fillMaxWidth()
                    } else {
                        Modifier
                    }
                    // Dispatch 2026-10-17-09: beside the rail the pill is the Maps pill's size, its start just clear of the system
                    // navigation bar (or of the cut-out, which the Row already reserves), so the screen's other side stays free
                    // for a floating keyboard. History: RECORD -1227 ("Move to Maps' spot") put it at the Maps pill's place; the
                    // owner's check of build 1.0.3414 found it passed over the navigation bar and asked for it centred; their
                    // check of 1.0.3416 asked for it at the left (RECORD -1238: "can it move a bit to the left to accommodate
                    // floating keyboards?", then "Yes, at the left (Recommended)").
                    // Why it once passed over the navigation bar: this Column sits inside shortLandscapeContentInsets(), which
                    // reserves the status bar, the cut-out and the keyboard but not a navigation bar on the side away from the
                    // rail (the rail takes its own side's). On Maps the pill is padded by mapControlsPadding, which does include
                    // the navigation bars. Robolectric reports zero insets; TabsMapChromeShapeLandscapeNavBarLeftTest dispatches
                    // one.
                    // Size: the Maps pill ends where the Maps compass strip begins (RECORD -732) and is its height (RECORD -729);
                    // the strip is drawn on Maps only, so since dispatch 2026-10-18-03 its width and its one line's height are
                    // worked out here as the strip's are (landscapeTabPillFit, tabPillStripLineHeight): this area less the
                    // navigation bar's inset (navBarFarSide) and less the strip's width, the strip's width left free on the rail's
                    // side. With no cut-out on the rail's side that is the Maps pill's own place. Until then the strip's last
                    // measurement on Maps was used, with half the area and the pill's own height before Maps had been shown. The
                    // padding beside it is not a surface and takes no touch. Its dropdown takes the same width and place.
                    val navBarFarSideDensity = LocalDensity.current
                    val navBarFarSideDirection = LocalLayoutDirection.current
                    val navBarFarSide: Dp = with(navBarFarSideDensity) {
                        // Only the part the Row's own cut-out padding has not already reserved on that side.
                        val px = if (portEdge == ScreenEdge.Right) {
                            WindowInsets.navigationBars.getLeft(this, navBarFarSideDirection) - WindowInsets.displayCutout.getLeft(this, navBarFarSideDirection)
                        } else {
                            WindowInsets.navigationBars.getRight(this, navBarFarSideDirection) - WindowInsets.displayCutout.getRight(this, navBarFarSideDirection)
                        }
                        px.coerceAtLeast(0).toDp()
                    }
                    // Dispatch 2026-10-18-03, item 2: the width is the room less the strip's width, the strip's width worked out by
                    // landscapeStripWidthPx from the same need and bar floor CompactMapTab's strip uses, at layout time, so it is
                    // the Maps pill's from the first frame (landscapeTabPillFit). Was the strip's last measurement on Maps
                    // (lastMapsStripWidth), and half the area before Maps had been shown.
                    // Dispatch 2026-10-18-10 Amendment 2 (RECORD -1350; the owner: "can the search bar in the journal be moved a bit from
                    // the edge? It sits very close to it. For tablets"): on a large screen the Journal's revealed pill, and the open
                    // search panel that takes this same place (searchDropdown, below), sit in the Journal's centred 960 dp column, edge
                    // to edge with its switch line and content (readableContentCap), not at the screen's edge. fillMaxWidth first, so
                    // the column is centred in the whole width here as the tab's content is. List and Seasonal keep the Maps pill's
                    // place at the left (RECORD -1238, "at the left ... to accommodate floating keyboards"); they have no 960 column.
                    // (Until Amendment 3, below.)
                    // Phones are unchanged.
                    // Amendment 3 (RECORD -1353, superseding -1238's "at the left" for tablets only; the owner: "Yes move those tabs
                    // also. Those were the ones I was mainly complaining about", "Line up with their content (Recommended)"): on a large
                    // screen List and Seasonal's pill and open panel take their own content column the same way, 640 dp
                    // (readableContentCap carries each tab's width; 960 dp on every tab since Amendment 4, -1355). Every tab beside the rail.
                    val tabLandscapePillPlace = if (railBeside && largeScreen) {
                        Modifier.fillMaxWidth().then(readableContentCap)
                    } else if (railBeside) {
                        val farIsLeft = portEdge == ScreenEdge.Right
                        val area = Modifier.absolutePadding(left = if (farIsLeft) navBarFarSide else 0.dp, right = if (farIsLeft) 0.dp else navBarFarSide)
                        area.landscapeTabPillFit(farIsLeft, tabPillStripNeed, tabPillBarFloorPx)
                    } else {
                        Modifier
                    }
                    // Map tab only: SearchEntryBar moves into CompactMapTab's own searchBarSlot
                    // instead (that call site's own doc comment), composed as a real overlay inside
                    // the SAME Box that hosts the map, so its 80% fill reveals map imagery through it
                    // the same as the compass strip and the two map-chrome pills — the owner's own
                    // direct call, scoped to the Map tab specifically so the other three tabs
                    // (List/Seasonal/Journal, none of which have anything worth showing through a
                    // translucent bar) keep this bar as ordinary opaque-backed chrome, unchanged.
                    // Search-focus-and-hide dispatch, Item 2 — owner decision, framed as a design change
                    // ("searching has nothing to do with editing an entry"), but this hide condition is
                    // what actually closes the dropdown-scrim-blocks-taps defect too: see
                    // `isEditingJournalEntry`'s own doc comment above for why Item 1's own fix (clearing
                    // focus explicitly) was tried first and made things worse, not better. Hidden via
                    // composition (an `if`, not an opacity/size-zero modifier) — "hide, do not remove"
                    // means the feature stays intact everywhere else, not that this specific instance
                    // keeps its state while invisible; an unmounted composable can't be the thing silently
                    // holding onto stale focus. The moment this bar *remounts*, right as an edit screen
                    // closes, was flagged as an unexercised race before this was built — now exercised and
                    // ruled out by `AvailabilityScreenBackNavigationTest`'s own "backgrounding and
                    // resuming mid-edit, then closing normally..." test.
                    // Journal redesign J5 (owner's ruling 1, "Hide header; icon reveals it
                    // (Recommended)"): on the Journal tab of a short landscape window the header is
                    // hidden, since height is the scarce axis there, until the Journal's own
                    // short-window header row brings it up (JournalScreenState.searchHeaderRevealed;
                    // its search icon and Back clear it again). Portrait, every other tab and
                    // portrait windows are unchanged. The rail shows exactly in a landscape
                    // window (showRail), since -246 whatever its height.
                    // Dispatch 2026-10-17-09: RECORD -1238 ("Always show it") replaced this for a while; RECORD -1242 ("Journal
                    // back to J5; keep the rest (Recommended)") restored it as it was. Revealed, the pill takes the same place
                    // as on List and Seasonal (tabLandscapePillPlace).
                    val journalHidesSearchHeader = showRail && compactTab() == CompactTab.JOURNAL && !journalScreenState.searchHeaderRevealed
                    // Hiding the header closes its dropdown too, so the dropdown's dismiss scrim
                    // (below) is never left over content with no bar above it.
                    LaunchedEffect(journalHidesSearchHeader) {
                        if (journalHidesSearchHeader) showSearchDropdown = false
                    }
                    // RECORD -1330 (the owner: "when switching from an open entry to list, or to seasonal, the search bar is empty"): an
                    // open Journal entry hides the bar on the Journal only, as -692 set it there. An entry stays open when the tab
                    // changes (F3), and until -1330 this held the bar off List and Seasonal too, whichever tab showed.
                    // RECORD -1330 to -1333: the pill's slot is measured here for a map under the Journal's switch line, which keeps
                    // its place while the line slides down with the pill (JournalSearchPillRoom). The Box is always composed, so a
                    // hidden pill measures 0.
                    val journalSearchPillFullHeight = if (railBeside) lastMapsStripHeight.takeIf { it > 0.dp } ?: tabPillStripLineHeight else 0.dp
                    SideEffect { journalSearchPillRoom.fullHeightPx = with(journalSearchPillRoomDensity) { journalSearchPillFullHeight.roundToPx() } }
                    // UI split Phase 2.5 PR 3 (dispatch 2026-10-19-39): the search pill on List, Seasonal and the Journal, with its
                    // measured slot, moved to ScaffoldHeaderSearch.kt and called here, at the same place.
                    ScaffoldHeaderSearch(
                        dropdown = searchDropdownState,
                        isMapFullscreen = isMapFullscreen,
                        compactTab = compactTab,
                        journalSearchHeld = journalSearchHeld,
                        journalHidesSearchHeader = journalHidesSearchHeader,
                        journalSearchPillRoom = journalSearchPillRoom,
                        tabLandscapePillPlace = tabLandscapePillPlace,
                        readableContentCap = readableContentCap,
                        railBeside = railBeside,
                        lastMapsStripHeight = lastMapsStripHeight,
                        tabPillStripLineHeight = tabPillStripLineHeight,
                        uiState = uiState,
                        distanceUnit = distanceUnit,
                        appClearFocusInProgress = appClearFocusInProgress,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onClearSearch = onClearSearch,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                    )

                    // Switches to the Journal tab and starts the entry there — the gallery's own edit
                    // form ([JournalTab]'s `editingEntry` branch) is what shows it next, the same
                    // "just-created entry opens for editing" behavior the drawer used to give this
                    // exact call. No drawer to open any more; Journal is a bottom-nav destination now.
                    val onLogFindHere: (LatLng) -> Unit = { location ->
                        // Intent 2026-09-28-44, F3 (continuation 2026-09-28-45, the owner: "Leave the
                        // kept one first (Recommended)"): a find kept open on the Journal is left
                        // before the new one opens over it, through the one wrapper.
                        if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
                        onCompactTabChange(CompactTab.JOURNAL)
                        // Stage 2d: lands JournalTab on Records -> Finds, editing, for the entry
                        // onStartLogEntry is about to create — see JournalTab's own doc comment, "The
                        // map '+' routing bug." Before this fix, compactTab alone left JournalTab's own
                        // selectedTopTab at its CARTOGRAPHY default, landing on Cartography instead.
                        onPendingJournalDestinationChange(PendingJournalDestination.EDIT_NEW_FIND)
                        onStartLogEntry(location, LocalDate.now())
                    }

                    // A Box, not a plain weighted child, as of map/navigation redesign dispatch C: this
                    // is now also where AdvancedSearchDropdown floats over whatever tab content shows
                    // below it, composed after that content so it draws on top by composition order
                    // alone (AdvancedSearchDropdown's own doc comment). weight(1f) here (unchanged from
                    // before this dispatch) states the intent: this gets whatever is left after the
                    // wrap-content siblings above (empty in fullscreen, so the map then gets the entire
                    // padded area) (the same pattern the removed tablet Scaffold used). Each branch
                    // below now fills this Box (fillMaxSize()) rather than carrying its own weight(1f),
                    // since a Box — unlike the Column this used to be a direct child of — doesn't
                    // distribute weight among its children.
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Landscape B2 (S2): on the Map tab of a short landscape window the search
                        // bar sits at the top on the punch-hole side, width min(384dp, distance
                        // from the punch-hole-side controls edge to the map's horizontal centre
                        // - 8dp), so it ends before the centre line; its dropdown takes the same
                        // width and side. The map fills this Box, so its centre is maxWidth / 2.
                        // SearchEntryBar takes no modifier, so the cap goes on its wrapper. Null
                        // everywhere else: portrait's full-width bar is unchanged.
                        val searchLayoutDirection = LocalLayoutDirection.current
                        val landscapeSearchWidth: Dp? = if (showRail && compactTab() == CompactTab.MAP) {
                            val punchHoleInset = if (punchHoleEdge == ScreenEdge.Left) {
                                mapControlsPadding.calculateLeftPadding(searchLayoutDirection)
                            } else {
                                mapControlsPadding.calculateRightPadding(searchLayoutDirection)
                            }
                            val railInset = if (portEdge == ScreenEdge.Left) {
                                mapControlsPadding.calculateLeftPadding(searchLayoutDirection)
                            } else {
                                mapControlsPadding.calculateRightPadding(searchLayoutDirection)
                            }
                            if (landscapeStripWidth > 0.dp) {
                                // RECORD -732 (the owner: "Join moves to fit the strip (Recommended)"): the bar takes the room the
                                // strip leaves and ends where the strip begins (CompactMapTab's landscapeStripFit, which keeps the
                                // bar's floor). No 384 dp cap and no gap. Dispatch 2026-10-12-08 (RECORD -951): navigating too, since
                                // the strip stays beside the bar at its own width and the navigation card hangs under the strip.
                                (maxWidth - punchHoleInset - railInset - landscapeStripWidth).coerceAtLeast(0.dp)
                            } else {
                                // Before the strip is first measured: RECORD -729's half, for that frame.
                                (maxWidth / 2 - punchHoleInset).coerceAtLeast(0.dp)
                            }
                        } else {
                            null
                        }
                        val landscapeSearchAlignment = if (punchHoleEdge == ScreenEdge.Left) Alignment.Start else Alignment.End
                        // Motion Part 2, item 1 (dispatch 2026-09-28-666, scouts S1 and S2; the owner, RECORD -651: "Quick crossfade"): one tab
                        // fades into the next, every route between tabs included (they all change compactTab). The outgoing tab is held at its
                        // size and place and takes no touch while it fades (TabCrossfade's doc comment), so the live map is not re-measured when
                        // the bottom bar or the landscape rail comes back as Maps leaves. The `when` reads the tab it is handed, not
                        // compactTab(), so the outgoing tab goes on drawing itself; the one exception is the Maps search bar, which leaves
                        // at once (its slot's comment). Instant under reduced motion. The cost of keeping the map alive through the fade is
                        // the S22's to judge (RECORD -651).
                        TabCrossfade(targetState = compactTab(), modifier = Modifier.fillMaxSize().then(readableContentCap)) { tab ->
                        // Amendment 1 (RECORD -672), item 8: the Maps tab is kept through its fade (KEEP_MAP_THROUGH_TAB_FADE); the
                        // fallback, if the S22 finds that too costly, is this one constant, which drops the leaving Maps tab at once.
                        if (tab == CompactTab.MAP && tab != compactTab() && !KEEP_MAP_THROUGH_TAB_FADE) Unit else
                        when (tab) {
                            CompactTab.LIST -> ListTab(
                                uiState = uiState,
                                currentTime = currentTime,
                                distanceUnit = distanceUnit,
                                onViewOnMap = onViewSpeciesOnMap,
                                modifier = Modifier.fillMaxSize(),
                            )
                            CompactTab.MAP -> ScaffoldMapTabCall(
                                tab = tab,
                                compactTab = compactTab,
                                isMapFullscreen = isMapFullscreen,
                                isDrawerOpen = isDrawerOpen,
                                mapMode = mapMode,
                                mapTaxonFilter = mapTaxonFilter,
                                dropdown = searchDropdownState,
                                measurements = scaffoldMeasurements,
                                uiState = uiState,
                                distanceUnit = distanceUnit,
                                mapSlot = mapSlot,
                                mapIconClusterPosition = mapIconClusterPosition,
                                mapCameraMemory = mapCameraMemory,
                                mapReturnMemory = mapReturnMemory,
                                showRail = showRail,
                                portEdge = portEdge,
                                punchHoleEdge = punchHoleEdge,
                                landscapeSearchWidth = landscapeSearchWidth,
                                landscapeSearchAlignment = landscapeSearchAlignment,
                                barMeetsStrip = barMeetsStrip,
                                mapSearchBarHeight = mapSearchBarHeight,
                                safeAnimatedTopInset = safeAnimatedTopInset,
                                safeAttributionBottomInset = safeAttributionBottomInset,
                                safeAttributionEndInset = safeAttributionEndInset,
                                attributionButtonBottomInset = attributionButtonBottomInset,
                                mapControlsPadding = mapControlsPadding,
                                journalSearchHeld = journalSearchHeld,
                                mapRenderMode = mapRenderMode,
                                onMapModeChange = onMapModeChange,
                                mapLayers = mapLayers,
                                mapBubbleSources = mapBubbleSources,
                                onPlaceTripPin = onPlaceTripPin,
                                onLogFindHere = onLogFindHere,
                                onIsMapFullscreenChange = onIsMapFullscreenChange,
                                onMapFullscreenChanged = onMapFullscreenChanged,
                                onBottomNavTabSelected = onBottomNavTabSelected,
                                onLocateMe = onLocateMe,
                                // RECORD -739: grouped so CompactMapTab's signature stays small enough for ART (CompactMapRecording).
                                recording = CompactMapRecording(
                                    isRecording = isRecording,
                                    onToggleRecording = onToggleRecording,
                                    startRecordingErrorMessage = startRecordingErrorMessage,
                                    breadcrumbPoints = breadcrumbPoints,
                                    waypoints = mapWaypoints,
                                    onDropWaypoint = onDropWaypoint,
                                ),
                                // RECORD -739: grouped so CompactMapTab's signature stays small enough for ART (CompactMapNavigation).
                                navigation = CompactMapNavigation(
                                    returnToStart = returnToStart,
                                    isReturning = isReturning,
                                    isNavigating = isNavigating,
                                    isArrivalHeld = isArrivalHeld,
                                    isOffTrack = isOffTrack,
                                    onToggleReturning = onToggleReturning,
                                    navigationTarget = navigationTarget,
                                    returnRoute = returnRoute,
                                    routeLine = routeLine,
                                    onRetryRoute = onRetryRoute,
                                    isNavigatingToWaypoint = waypointNavigate.isNavigatingToWaypoint,
                                    waypointStraightLine = waypointNavigate.straightLine,
                                    waypointReopen = waypointNavigate.mapReopen,
                                    onWaypointReopenConsumed = waypointNavigate.onMapReopenConsumed,
                                    navigationFollowing = navigationFollowing,
                                    navigationViewRequestId = navigationViewRequestId,
                                    onLeftNavigationView = onLeftNavigationView,
                                    onReturnToRoute = onReturnToRoute,
                                    navigationZoomPending = navigationZoomPending,
                                    onNavigationZoomApplied = onNavigationZoomApplied,
                                ),
                                recordingSundownLine = recordingSundownLine,
                                quickSettings = quickSettings,
                                compassProvider = compassProvider,
                                computeTrueHeading = computeTrueHeading,
                                showDecimalDegrees = showDecimalDegrees,
                                onToggleCoordinateFormat = onToggleCoordinateFormat,
                                currentTime = currentTime,
                                onClearMapTaxonFilter = onClearMapTaxonFilter,
                                onManualLatChanged = onManualLatChanged,
                                onManualLngChanged = onManualLngChanged,
                                onSearchManualCoordinates = onSearchManualCoordinates,
                                appClearFocusInProgress = appClearFocusInProgress,
                                onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                                onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                                onClearSearch = onClearSearch,
                                onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                            )
                            CompactTab.SEASONAL -> SeasonalTab(uiState = uiState, modifier = Modifier.fillMaxSize())
                            CompactTab.JOURNAL -> CompositionLocalProvider(LocalLeavingPages provides journalLeavingPages, LocalJournalSearchPillRoom provides journalSearchPillRoom.takeIf { railBeside }) { JournalTab(
                                uiState = logUiState,
                                onOpenCameraForLogEntry = { onOpenCamera(InAppCameraTarget.LOG_ENTRY) },
                                onOpenCameraForAlbum = { onOpenCamera(InAppCameraTarget.ALBUM) },
                                onOpenCameraForCartographyEntry = { onOpenCamera(InAppCameraTarget.CARTOGRAPHY_ENTRY) },
                                mapSlot = mapSlot,
                                pickerRegion = uiState.region ?: JOURNAL_PICKER_DEFAULT_REGION,
                                deviceLocation = uiState.liveFix?.let { LatLng(it.lat, it.lng) },
                                basemap = basemap,
                                night = isNightMode,
                                mapLayers = mapLayers.stored,
                                onMapLayerVisibilityChanged = mapLayers.onVisibilityChanged,
                                onOpenEntry = onOpenLogEntry,
                                // Item 8: the find open when the report closes, read before the close lands; Back and the report's own arrow
                                // both come through here, and the screen says whether the find came from the map.
                                onCloseEntry = {
                                    val closing = logUiState.editingEntry?.id
                                    onCloseLogEntry()
                                    onFindReportClosed(closing)
                                },
                                onStartEntry = onStartLogEntry,
                                onEntryChanged = onLogEntryChanged,
                                onStartEditingEntry = {
                                    onFindEditStarted()
                                    onStartEditingLogEntry()
                                },
                                onSaveEntry = onSaveLogEntry,
                                onCancelEditing = onCancelLogEntryEditing,
                                onLeaveEditingIncidentally = leaveLogEntryEditingOfferingDiscard,
                                onPhotoAcquisitionInFlightChanged = { inFlight -> onLogPhotoAcquisitionInFlightChange(inFlight) },
                                onAddPhoto = onAddLogPhoto,
                                onRemovePhoto = onRemoveLogPhoto,
                                onPullPhoto = onPullLogPhoto,
                                onDeleteEntry = { id ->
                                    onDeleteLogEntry(id)
                                    onFindDeleted(id)
                                },
                                onSaveErrorDismissed = onSaveLogErrorDismissed,
                                // Album folded into this tab as a third top tab (Log/Drafts/Album) — see
                                // LogGalleryScreen's own doc comment. Threaded through unchanged from
                                // where CompactTab.PHOTOS used to read them directly.
                                galleryPhotos = logUiState.galleryPhotos,
                                isLoadingGalleryPhotos = logUiState.isLoadingGalleryPhotos,
                                onDeleteGalleryPhoto = onDeleteGalleryPhoto,
                                onAddGalleryPhoto = onAddGalleryPhoto,
                                galleryLoadErrorMessage = logUiState.galleryLoadErrorMessage,
                                galleryPhotoEntryReferenceCounts = logUiState.cartographyEntryPhotoReferenceCounts,
                                // Journal Stage 2b: Cartography's own Entries/Drafts/Album — see
                                // CartographyScreen's own doc comment.
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
                                onRequestDeleteGalleryPhoto = onRequestDeleteGalleryPhoto,
                                // Null stays null (the tiles then offer Delete only); when set, starting an edit forgets the map origin (item 8).
                                onOpenEntryForEditing = onOpenLogEntryForEditing?.let { open ->
                                    val editing: (String) -> Unit = { id ->
                                        onFindEditStarted()
                                        open(id)
                                    }
                                    editing
                                },
                                getCartographyEntryMapData = getCartographyEntryMapData,
                                getSavedTrackPaths = getSavedTrackPaths,
                                getCartographyEntryOfflineRegion = getCartographyEntryOfflineRegion,
                                getCartographyEntryCurrentLocation = getCartographyEntryCurrentLocation,
                                // Journal restructure Stage 1: the Records tab's three submenus — see
                                // RecordsTab's own doc comment.
                                availabilityUiState = uiState,
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
                                pendingDestination = pendingJournalDestination(),
                                pendingFindId = pendingJournalFindId(),
                                pendingEntryId = pendingJournalEntryId(),
                                onEntryClosed = onEntryClosed,
                                onPendingDestinationConsumed = { onPendingJournalDestinationChange(null) },
                                onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
                                journalState = journalScreenState,
                                // Intent 2026-09-28-28: while the Tools drawer is open over the
                                // Journal, Back closes the drawer, so the Journal's own handlers are
                                // off. See JournalTab's backEnabled for why off rather than outranked.
                                // RECORD -871: off too while Back retraces the Tools drawer's "Offline maps" row.
                                backEnabled = !isDrawerOpen() && !toolsReturnPending(),
                                cartographyEntryModeState = cartographyEntryModeState,
                                cartographyOpenEntryGroupsState = cartographyOpenEntryGroupsState,
                                onSetCartographyEntryGroupIncluded = onSetEntryGroupIncluded,
                                findEntryModeState = findEntryModeState,
                                findOverViewState = findOverViewState,
                                modifier = Modifier.fillMaxSize(),
                                onNavigateToWaypoint = waypointNavigate.onNavigate,
                                reopenWaypointDetails = waypointNavigate.recordsReopenDetails,
                                onReopenWaypointDetailsConsumed = waypointNavigate.onRecordsReopenConsumed,
                                onGpxFilePicked = onGpxFilePicked,
                                pendingTrackId = pendingJournalTrackId(),
                            ) }
                            // Never actually reached — CompactTab.TOOLS never becomes compactTab itself,
                            // see that entry's own doc comment. Kept as a real branch (not an else) so
                            // this stays an exhaustive, honest `when` rather than one that silently
                            // compiles around a case the compiler can't see is impossible.
                            CompactTab.TOOLS -> Unit
                        }
                        }

                        // UI split Phase 2.5 PR 1 (dispatch 2026-10-19-39): the dismiss scrim and the search panel, moved to
                        // ScaffoldSearchDropdown.kt and called here, at the same place: after the tab content, so both still draw over it and
                        // are tried first for a touch.
                        ScaffoldSearchDropdownLayer(
                            dropdown = searchDropdownState,
                            isMapFullscreen = isMapFullscreen,
                            compactTab = compactTab,
                            mapSearchBarHeight = mapSearchBarHeight,
                            bottomNavHeight = bottomNavHeight,
                            mapControlsPadding = mapControlsPadding,
                            landscapeSearchWidth = landscapeSearchWidth,
                            mapCompassStripHeight = { mapCompassStripHeight },
                            compassStripClearance = compassStripClearance,
                            punchHoleEdge = punchHoleEdge,
                            railBeside = railBeside,
                            tabLandscapePillPlace = tabLandscapePillPlace,
                            uiState = uiState,
                            distanceUnit = distanceUnit,
                            currentTime = currentTime,
                            onRecentSearchSelected = onRecentSearchSelected,
                            onManualLatChanged = onManualLatChanged,
                            onManualLngChanged = onManualLngChanged,
                            onSearchManualCoordinates = onSearchManualCoordinates,
                            onRadiusChanged = onRadiusChanged,
                            onMonthSelected = onMonthSelected,
                            onUseCurrentLocation = onUseCurrentLocation,
                            onCompactTabChange = onCompactTabChange,
                            onSelectedTabChange = onSelectedTabChange,
                        )
                    }
                }
                // Motion Part 2, item 2 (scout S4; the owner, RECORD -651: "Fade with the tab"): the opaque rail beside the
                // content fades in and out with the tab change, as the bottom bar does in portrait. Its room comes and goes at
                // once, as before; leaving for Maps it takes no width and is drawn where it was, taking no touch.
                // Turning the phone, or turning it over (the port edge changes side), plays no fade.
                TabChromeFade(
                    shown = railBeside && portEdge != ScreenEdge.Left,
                    windowKey = showRail to portEdge,
                    zeroRoom = Modifier.zIndex(1f).zeroWidthDrawnInPlace(atStart = false),
                ) {
                    ForagerNavigationRail(
                        selectedTab = compactTab(),
                        isDrawerOpen = isDrawerOpen(),
                        onTabSelected = onBottomNavTabSelected,
                        portEdge = portEdge,
                        // Dispatch 2026-10-17-09: the Maps rail's rounded inner corners and outline, solid here (the default fill).
                        innerCornerRadius = MAP_CARD_CORNER_RADIUS,
                    )
                }
            }
        }
}
