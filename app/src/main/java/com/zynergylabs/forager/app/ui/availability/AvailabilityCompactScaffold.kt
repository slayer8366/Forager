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

import com.zynergylabs.forager.app.ui.adaptive.isLargeScreenWindow
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.platform.testTag
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.zynergylabs.forager.app.ui.motion.PressHighlight
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion
import com.zynergylabs.forager.app.ui.motion.TabCrossfade
import com.zynergylabs.forager.app.ui.motion.LeavingPages
import com.zynergylabs.forager.app.ui.motion.LocalLeavingPages
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.zIndex
import com.zynergylabs.forager.app.ui.motion.TabChromeFade
import com.zynergylabs.forager.app.ui.motion.zeroHeightDrawnAbove
import com.zynergylabs.forager.app.ui.motion.zeroWidthDrawnInPlace
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
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
    logUiState: MushroomLogUiState,
    logPhotoAcquisitionInFlight: () -> Boolean,
    cartographyUiState: CartographyUiState,
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
    /** J8-3: the entry report's "Show on map" and "Hide from map"; threaded to [JournalTab]. */
    onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit = { _, _ -> },
    /** M1: what the Maps tab's glyph bubbles look records up in; threaded to [CompactMapTab]. */
    mapBubbleSources: MapRecordSources = MapRecordSources(),
    /** Dispatch 2026-09-28-502: navigating to a chosen waypoint, threaded to [CompactMapTab] and [JournalTab]; see [WaypointNavigateControls]. */
    waypointNavigate: WaypointNavigateControls = WaypointNavigateControls(),
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    mapIconClusterPosition: MapIconClusterPositionState,
    /** Part 1 layout fixes, item 4: the Maps tab's camera, kept above the tab switch; threaded to [CompactMapTab]. */
    mapCameraMemory: MapCameraMemory,
    /** Item 8 (dispatch 2026-09-29-57): what "Open in Journal" leaves for the Maps tab that Back returns to; see [MapReturnMemory]. */
    mapReturnMemory: MapReturnMemory,
    /** Item 8: a find's report was closed (Back or its arrow); the id is the find that was open. The screen decides whether that is a return to the map. */
    onFindReportClosed: (String?) -> Unit,
    /** Item 8: a find was deleted from its page or its tile. */
    onFindDeleted: (String) -> Unit,
    /** Item 8: the user began editing a find (from its report or a tile), which ends "return to the map" for it. */
    onFindEditStarted: () -> Unit,
    /** Dispatch 2026-09-28-387, Part B: a journal entry's report or editor was left. The screen decides whether that is a return to the map. */
    onEntryClosed: (entryId: String, fromReport: Boolean) -> Unit = { _, _ -> },
    mapRenderMode: MapRenderMode,
    /**
     * Map layers L0b: the Maps tab's Layers sheet, legend and saved records ([MapLayersControls]),
     * threaded to [CompactMapTab], and the shared layer choices threaded to the Journal's entry map.
     * Nothing here reads it.
     */
    mapLayers: MapLayersControls,
    mapMode: () -> MapMode,
    isNightMode: Boolean,
    isRecording: Boolean,
    startRecordingErrorMessage: String?,
    breadcrumbPoints: List<LatLng>,
    mapWaypoints: List<Waypoint>,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isNavigating: Boolean,
    /** See [AvailabilityScreen]'s own `isArrivalHeld` doc comment (RECORD -845). */
    isArrivalHeld: Boolean,
    isOffTrack: Boolean,
    /** See [AvailabilityScreen]'s own `recordingSundownLine` doc comment. */
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    /** See [CompactMapTab]'s own `quickSettings` doc comment (dispatch 2026-09-28-645). */
    quickSettings: MapQuickSettings?,
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    navigationTarget: Waypoint?,
    returnRoute: ReturnRoute,
    routeLine: RouteLine?,
    onRetryRoute: () -> Unit,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    navigationFollowing: Boolean,
    navigationViewRequestId: Int,
    onLeftNavigationView: () -> Unit,
    onReturnToRoute: () -> Unit,
    navigationZoomPending: Boolean,
    onNavigationZoomApplied: () -> Unit,
    mapTaxonFilter: () -> Long?,
    basemap: Basemap,
    tracks: List<Track>,
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    waypointEntryReferenceCounts: Map<String, Int>,
    onLocateMe: () -> Unit,
    onLeaveLogEntryEditingIncidentally: () -> Unit,
    leaveLogEntryEditingOfferingDiscard: () -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onIsDrawerOpenChange: (Boolean) -> Unit,
    onIsMapFullscreenChange: (Boolean) -> Unit,
    onMapFullscreenChanged: (Boolean) -> Unit,
    onCompactTabChange: (CompactTab) -> Unit,
    onSelectedTabChange: (ResultsTab) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onPendingJournalDestinationChange: (PendingJournalDestination?) -> Unit,
    onStartLogEntry: (LatLng?, LocalDate) -> Unit,
    onViewSpeciesOnMap: (Long) -> Unit,
    onMapModeChange: (MapMode) -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onToggleRecording: () -> Unit,
    onDropWaypoint: (LatLng, String) -> Unit,
    onToggleReturning: () -> Unit,
    onClearMapTaxonFilter: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onOpenCamera: (InAppCameraTarget) -> Unit,
    onOpenLogEntry: (String) -> Unit,
    onCloseLogEntry: () -> Unit,
    onLogEntryChanged: (MushroomLogEntry) -> Unit,
    onStartEditingLogEntry: () -> Unit,
    onSaveLogEntry: () -> Unit,
    onCancelLogEntryEditing: () -> Unit,
    onLogPhotoAcquisitionInFlightChange: (Boolean) -> Unit,
    onAddLogPhoto: (PhotoSource) -> Unit,
    onRemoveLogPhoto: (LogPhoto) -> Unit,
    onPullLogPhoto: (LogPhoto) -> Unit,
    onDeleteLogEntry: (String) -> Unit,
    onSaveLogErrorDismissed: () -> Unit,
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit,
    onAddGalleryPhoto: (PhotoSource) -> Unit,
    onOpenCartographyEntry: (String) -> Unit,
    onStartCartographyEntry: (LocalDate) -> Unit,
    onCloseCartographyEntry: () -> Unit,
    onCartographyTextChanged: (String) -> Unit,
    onCartographyTagsChanged: (List<String>) -> Unit,
    onSetFindDecision: (String, Boolean) -> Unit,
    onSetTrackDecision: (String, Boolean) -> Unit,
    onSetWaypointDecision: (String, Boolean) -> Unit,
    onSetOfflineRegionDecision: (Long, Boolean) -> Unit,
    /** Data part A (dispatch 2026-09-28-667): a group switch in the entry editor's "In this entry" panel. */
    onSetEntryGroupIncluded: (EntryGroup, Boolean) -> Unit,
    onToggleKeptPhoto: (String) -> Unit,
    onAcquirePhotoForCartographyEntry: (PhotoSource) -> Unit,
    onFinishCartographyEntry: () -> Unit,
    onSaveCartographyEntry: () -> Unit,
    onDiscardCartographyEntryChanges: () -> Unit,
    onSaveCartographyEntryAsDraft: () -> Unit,
    /** Clears `CartographyUiState.saveErrorMessage` once [JournalTab]'s Toast of it has shown (intent `2026-09-28-68`). */
    onCartographySaveErrorDismissed: () -> Unit,
    onDeleteCartographyEntry: (String) -> Unit,
    /** J4b L2: an Entries card's swipe Delete (pending, with Undo); see [JournalTab]. */
    onRequestDeleteCartographyEntry: ((String) -> Unit)? = null,
    /** J4b L3: an album photo's long-press Delete (pending, with Undo); see [JournalTab]. */
    onRequestDeleteGalleryPhoto: ((String) -> Unit)? = null,
    /** J4b L1: a find tile's long-press Edit (`MushroomLogViewModel.onOpenEntryForEditing`); see [JournalTab]. */
    onOpenLogEntryForEditing: ((String) -> Unit)? = null,
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData,
    /** F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, for the Journal cards' thumbnails. */
    getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary?,
    getCartographyEntryCurrentLocation: suspend () -> LocationResult,
    onOfflineMapLatChanged: (String) -> Unit,
    onOfflineMapLngChanged: (String) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onOfflineMapsOpened: () -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
    onDownloadAgain: (Long) -> Unit = {},
    onTracksOpened: () -> Unit,
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>>,
    onDeleteWaypoint: (String) -> Unit,
    /** Part 2 follow-ups F1 item 5 (owner "Option A"): a finished track's swipe or details Delete asks for a pending delete with Undo; `null` (the default) leaves tracks without a delete. */
    onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed track delete failed and the track is back; shown above the Tracks list. */
    tracksErrorMessage: String? = null,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    /** RECORD -723: the search bar's Clear; see AvailabilityScreen's own parameter. */
    onClearSearch: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
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
) {
        // SearchDropdown, under ActiveSearchSummary — see that composable's own onToggleSearch doc
        // comment. Local to this scaffold, not AvailabilityUiState: which panel is showing is a
        // display decision the ViewModel has no part in, same reasoning as mapMode/drawerPanel above.
        var showSearchDropdown by remember { mutableStateOf(false) }
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
        var landscapeStripHeight by remember { mutableStateOf(0.dp) }
        // RECORD -732: the strip's measured width there; the bar takes the rest of the room. 0 until measured.
        var landscapeStripWidth by remember { mutableStateOf(0.dp) }
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
        var lastMapsStripHeightDp by rememberSaveable { mutableFloatStateOf(0f) }
        val lastMapsStripHeight = lastMapsStripHeightDp.dp
        LaunchedEffect(isLandscapeWindow) { if (!isLandscapeWindow) lastMapsStripHeightDp = 0f }
        // The same inputs CompactMapTab sizes its landscape strip from (landscapeStripFit), from the same state.
        val tabPillStripNeed = rememberLandscapeStripNeed(uiState.liveAltitudeMeters, uiState.unitSystem, uiState.liveLocation, quickSettings != null)
        val tabPillBarFloorPx = rememberLandscapeBarFloorPx(uiState, uiState.distanceUnit)
        val tabPillStripLineHeight = rememberLandscapeStripLineHeight(uiState.liveLocation, quickSettings != null)
        // RECORD -709 (the owner: "Follow the strip's real height (Recommended)"): the Maps tab's compass strip as measured,
        // handed up through searchBarSlot; 0 while it is not measured (before its first layout, while navigating, in a landscape
        // window), where the dropdown keeps the one-line clearance above. See CompactMapTab's compassStripBottomClearance.
        var mapCompassStripHeight by remember { mutableStateOf(0.dp) }
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
        var searchChromeHeightPx by remember { mutableIntStateOf(0) }
        // "Set on map" (SearchDropdown's own Advanced Search section, dispatch C item 2) hands off to the exact same
        // pan-to-centre-pin-plus-confirm flow every other pin placement in this app uses
        // (CentrePinLocationPickerOverlay) rather than a second picker — see CompactMapTab's own
        // pendingAction-driven overlay for the established shape this mirrors. Lifted to this
        // scaffold's own scope, not into CompactMapTab, because the trigger (the dropdown) lives up
        // here and can be tapped from any bottom-nav tab, not just while already on Maps.
        var pickingSearchLocationOnMap by remember { mutableStateOf(false) }
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
        var bottomNavHeightPx by remember { mutableFloatStateOf(0f) }
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
        var mapRailWidthPx by remember { mutableFloatStateOf(0f) }
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
                // Material3's own snackbar, with its default colours passed explicitly. Its content
                // colour is its own role (`SnackbarDefaults.contentColor`), not derived from the fill.
                SnackbarHost(logDraftSnackbarHostState) { data ->
                    // Map chrome at 80% over the Maps tab, following the tab (planner message -77, Q4).
                    // C1: the navigation bar's colour (was inverseSurface). Its default content colour, inverseOnSurface, and its
                    // action, inversePrimary, fail WCAG AA on that colour (ChromeContrastTest), so the text takes the navigation
                    // bar's own content colour, onSurfaceVariant, and the action `primary`.
                    val snackbarColor = mapChromeFill(navigationBarContainerColor(), compactTab() == CompactTab.MAP)
                    val snackbarContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    // Motion Part 1 (dispatch 2026-09-28-652, item 3, scout S7; the owner, RECORD -651: "Yes, round them all";
                    // Amendment 1, RECORD -657: "Trim to its visible edge"). Material's Snackbar(snackbarData) draws its surface
                    // 12 dp inside the modifier it is given (SNACKBAR_SURFACE_MARGIN), so a tap on that modifier also took touches
                    // in a 12 dp band around the drawn snackbar, over the map. The tap, the test tag and the chrome-colour
                    // semantics now sit on a box laid exactly over the drawn surface instead, so a touch beside the snackbar
                    // reaches the map, and the press highlight is that surface's rounded shape. A deliberate touch-area change,
                    // the owner's; pinned by SnackbarTrimmedTouchTest.
                    val tapInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            // Dispatch 2026-09-28-104, item 6: on the Maps tab the Scaffold has no bottom
                            // bar to sit above and its contentWindowInsets drop the bottom side (below), so
                            // the host lay under the system navigation buttons. Off the Maps tab the
                            // bottomBar is real and Scaffold already places the host above it, so nothing is
                            // added there. Device-only: Robolectric reports the system inset as zero.
                            // Continuation -12 (planner's ruling): on the Maps tab the snackbar sits above the
                            // floating bottom navigation, not only above the system bar. In portrait, outside
                            // fullscreen, that is the nav's own measured height (which already includes the
                            // system-bar inset it takes, so nothing is added twice). In fullscreen the nav is
                            // off screen and the snackbar keeps the system-bar inset alone. In a short
                            // landscape window the rail runs the whole height of the port edge, so the
                            // snackbar keeps the system-bar inset at the bottom and takes the map controls'
                            // own side padding (the cut-out and the rail's measured width), so the centred
                            // snackbar clears the rail.
                            // Motion Part 2, item 7 (scout S6): the bottom offset below glides (snackbarMapBottom), so these
                            // cases and the Return to Route lift are one figure now. The rail case keeps its side padding.
                            .then(if (compactTab() == CompactTab.MAP && showRail) Modifier.padding(mapControlsPadding) else Modifier)
                            .padding(bottom = snackbarMapBottom),
                    ) {
                        Snackbar(
                            snackbarData = data,
                            containerColor = snackbarColor,
                            contentColor = snackbarContentColor,
                            actionContentColor = MaterialTheme.colorScheme.primary,
                            dismissActionContentColor = snackbarContentColor,
                        )
                        // The drawn surface's own box. No pointer input unless the notice is tappable, so an ordinary notice's
                        // action and dismiss buttons, under it, keep their touches.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(SNACKBAR_SURFACE_MARGIN)
                                .testTag(COMPACT_SNACKBAR_TAG)
                                // Dispatch 2026-09-28-626: a prompt whose whole surface is its tap (TappableNoticeVisuals).
                                .then(
                                    if (data.visuals is TappableNoticeVisuals) {
                                        Modifier.clickable(interactionSource = tapInteraction, indication = null, role = Role.Button) { data.performAction() }
                                    } else {
                                        Modifier
                                    },
                                )
                                .mapChromeContainerColor(snackbarColor)
                                .mapChromeContentColor(snackbarContentColor),
                        )
                        if (data.visuals is TappableNoticeVisuals) {
                            PressHighlight(interactionSource = tapInteraction, shape = SnackbarDefaults.shape, modifier = Modifier.padding(SNACKBAR_SURFACE_MARGIN))
                        }
                    }
                }
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
                    Box(modifier = Modifier.measuresJournalSearchPill(journalSearchPillRoom)) {
                    if (!isMapFullscreen() && compactTab() != CompactTab.MAP && !(journalSearchHeld && compactTab() == CompactTab.JOURNAL)) {
                        // Dispatch 2026-10-17-09, RECORD -1244 (the owner: "And have the search bar slide up and down when
                        // revealing/hiding"): J5's reveal on the Journal (journalHidesSearchHeader) slides the pill down into place
                        // from above, and back up out of view, while the content below follows it (the expand and shrink), on
                        // MotionTokens.panelMotionSpec, the spec the Maps tab's own search bar slides on into and out of fullscreen.
                        // Arriving on a tab shows its pill as it stands, with no slide: only a reveal or a hide on the Journal plays
                        // one. Every other way the pill comes or goes is as before, at once. Under reduced motion the reveal is at
                        // once too (docs/motion-spec.md §4).
                        // Dispatch 2026-10-18-03, item 3 (Forager RECORD -1269, "Fix item 4 too"; Elder's top-up audit, item 4):
                        // this was a key(compactTab()) around the AnimatedVisibility, which re-created the pill on every tab
                        // switch, so its field lost focus and its cursor. Now the pill is composed once: a switch of tab is told
                        // apart from a reveal or hide on the same tab (searchHeaderSlideTab, the tab the last visibility change
                        // settled on), and the switch's change of visibility takes no transition.
                        val searchHeaderReduceMotion = LocalReduceMotion.current
                        val searchHeaderVisibility = remember { MutableTransitionState(!journalHidesSearchHeader) }
                        searchHeaderVisibility.targetState = !journalHidesSearchHeader
                        var searchHeaderSlideTab by remember { mutableStateOf(compactTab()) }
                        val searchHeaderArriving = searchHeaderSlideTab != compactTab()
                        LaunchedEffect(compactTab(), searchHeaderVisibility.isIdle) {
                            if (searchHeaderVisibility.isIdle) searchHeaderSlideTab = compactTab()
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = searchHeaderVisibility,
                            enter = if (searchHeaderReduceMotion || searchHeaderArriving) {
                                EnterTransition.None
                            } else {
                                slideInVertically(animationSpec = MotionTokens.panelMotionSpec()) { fullHeight -> -fullHeight } +
                                    expandVertically(animationSpec = MotionTokens.panelMotionSpec(), expandFrom = Alignment.Top)
                            },
                            exit = if (searchHeaderReduceMotion || searchHeaderArriving) {
                                ExitTransition.None
                            } else {
                                slideOutVertically(animationSpec = MotionTokens.panelMotionSpec()) { fullHeight -> -fullHeight } +
                                    shrinkVertically(animationSpec = MotionTokens.panelMotionSpec(), shrinkTowards = Alignment.Top)
                            },
                        ) {
                        Column {
                        // SearchEntryBar takes no modifier, so its place goes on this wrapper (tabLandscapePillPlace).
                        Box(modifier = tabLandscapePillPlace) {
                        SearchEntryBar(
                            uiState = uiState,
                            distanceUnit = distanceUnit,
                            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                            onTaxonSearchResultSelected = { result ->
                                // RECORD -725/-726, the owner: "just keep the keyboard open and fill it in, no search yet"; "The
                                // keyboard and the search bar". Only the suggestion list closes (the ViewModel empties it); the
                                // dropdown, the field's focus and the keyboard stay. It closed the dropdown here before.
                                onTaxonSearchResultSelected(result)
                            },
                            onClearSearch = onClearSearch,
                            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                            onFieldFocused = { if (!appClearFocusInProgress[0]) showSearchDropdown = true },
                            // Dispatch 2026-10-17-09 (RECORD -1224, -1225; the owner: "The updated UI still needs to be applied to
                            // the Journal, the Seasonal, and the List tab"): the Maps tab's pill, its corners and its outline all
                            // round, with no divider, solid here since no map is behind it (overMap false; the owner: "In the places
                            // that aren't covering a map, they can stay solid"). Its height is the Maps pill's, so it does not change
                            // size across the tab switch. In landscape it is the Maps pill's size, at the left, clear of the
                            // navigation bar (tabLandscapePillPlace), at the Maps strip's height (RECORD -729); with no strip
                            // beside it here it rounds all four corners and is outlined all round.
                            // Dispatch 2026-10-18-03, item 2: the strip's height as Maps last measured it, else its one line
                            // worked out here (the strip's evening lines are not modelled; listed in known-bugs).
                            landscapeHeight = if (railBeside) lastMapsStripHeight.takeIf { it > 0.dp } ?: tabPillStripLineHeight else null,
                            pillCorners = MapCardCorners.ALL,
                            pillOutlineSides = MapChromeOutlineSides(left = true, top = true, right = true, bottom = true),
                        )
                        }
                        // Under the pill, inside the readable-width cap, as it was before the pill moved.
                        Box(modifier = readableContentCap) { SearchNotice(uiState) }
                        }
                        }
                    }
                    }

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
                            CompactTab.MAP -> CompactMapTab(
                                uiState = uiState,
                                mapSlot = mapSlot,
                                clusterPosition = mapIconClusterPosition,
                                cameraMemory = mapCameraMemory,
                                returnMemory = mapReturnMemory,
                                // RECORD -739: grouped so CompactMapTab's signature stays small enough for ART (CompactMapLandscape).
                                landscape = CompactMapLandscape(
                                    // Landscape B1: in a short landscape window this tab overlays
                                    // the rail on the port edge in place of its bottom bar, and pads
                                    // its controls clear of it (showRail, mapControlsPadding).
                                    railPortEdge = if (showRail) portEdge else null,
                                    // Landscape B2: the punch-hole side, and the search bar's capped
                                    // width there (the chip sits under it, within it).
                                    punchHoleEdge = if (showRail) punchHoleEdge else null,
                                    landscapeSearchWidth = landscapeSearchWidth,
                                    onRailWidthMeasured = { mapRailWidthPx = it },
                                    // RECORD -729: the landscape strip's height, which the bar takes (mapSearchBarHeight).
                                    onLandscapeStripHeightMeasured = {
                                        landscapeStripHeight = it
                                        if (it > 0.dp) lastMapsStripHeightDp = it.value
                                    },
                                    // RECORD -732: and its width; the bar takes the rest of the room (landscapeSearchWidth).
                                    onLandscapeStripWidthMeasured = {
                                        landscapeStripWidth = it
                                    },
                                ),
                                // Attribution must rise above the floating bottom nav while the nav
                                // is there — fullscreen-fixes dispatch, Item 1 (third design) — and
                                // follow it off screen while it isn't: safeAttributionBottomInset
                                // (declared alongside bottomNavHeight above, own doc comment there)
                                // animates between the nav's real measured height and the system
                                // navigation-bar inset in lockstep with the nav's own slide. Safe to
                                // change every animation frame: SightingsMapSlot destructures this
                                // field at the boundary and the only consumer is the attribution
                                // Text's own padding — no map effect keys on it or on renderMode as a
                                // whole (SightingsMap's own LaunchedEffects, checked), so nothing here
                                // re-measures or re-fits the map.
                                // backEnabled: while the Tools drawer, fullscreen or the search dropdown is open, its Back goes
                                // first, so the fan's handler is not composed (dispatches 2026-09-28-293, -298 and -312;
                                // MarkerFanOutBackHandler's doc comment). The add-action menu and the pin pickers are state of
                                // CompactMapTab, which narrows this further at its own mapSlot call, and gates the bubble on the
                                // same value. The dropdown is here as of -312: what is drawn on top closes first (the owner:
                                // "Yes dropdown should go away first when hitting back ... I never said to have the fan close
                                // before what's drawn on top"). Its term was held out under continuation -303 only while the
                                // Robolectric harness reopened the dropdown after it closed, which the focus fix
                                // (appClearFocusInProgress) ended. The taxon suggestions list is still not here.
                                renderMode = mapRenderMode.copy(bottomInset = safeAttributionBottomInset, attributionEndInset = safeAttributionEndInset, attributionBottomInset = attributionButtonBottomInset, backEnabled = !isDrawerOpen() && !isMapFullscreen() && !showSearchDropdown, leavingTab = tab != compactTab()),
                                mapMode = mapMode(),
                                onMapModeSelected = { onMapModeChange(it) },
                                mapLayers = mapLayers,
                                bubbleSources = mapBubbleSources,
                                onPlaceTripPin = onPlaceTripPin,
                                // Opens straight to the log's edit form for the new entry, bypassing
                                // Search — see DrawerPanel's own doc comment on why Log is reachable
                                // both ways.
                                onLogFindHere = onLogFindHere,
                                isFullscreen = isMapFullscreen(),
                                onToggleFullscreen = {
                                    onIsMapFullscreenChange(!isMapFullscreen())
                                    onMapFullscreenChanged(isMapFullscreen())
                                },
                                isDrawerOpen = isDrawerOpen(),
                                onBottomNavTabSelected = onBottomNavTabSelected,
                                onBottomNavHeightMeasured = { bottomNavHeightPx = it },
                                controlsPadding = mapControlsPadding,
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
                                taxonFilter = mapTaxonFilter(),
                                onClearTaxonFilter = onClearMapTaxonFilter,
                                // RECORD -739: grouped so CompactMapTab's signature stays small enough for ART (CompactMapSearchLocationPick).
                                searchLocationPick = CompactMapSearchLocationPick(
                                    // AdvancedSearchDropdown's own "Set on map" hands off to this same map's
                                    // own CentrePinLocationPickerOverlay — see compactMainScaffold's own
                                    // pickingSearchLocationOnMap doc comment.
                                    pickingSearchLocation = pickingSearchLocationOnMap,
                                    onSearchLocationPicked = { location ->
                                        onManualLatChanged("%.4f".format(location.lat))
                                        onManualLngChanged("%.4f".format(location.lng))
                                        onSearchManualCoordinates()
                                        pickingSearchLocationOnMap = false
                                    },
                                    onCancelSearchLocationPick = { pickingSearchLocationOnMap = false },
                                ),
                                // SearchEntryBar now overlays this tab directly (see searchBarSlot's
                                // own doc comment below) rather than sitting above it in document
                                // flow, so the strip/bubble/filter-chip positioning CompactMapTab
                                // derives from compassStripClearance needs to start below the bar, not
                                // at this Box's own true top edge. animatedTopInset (declared above,
                                // own doc comment there — fullscreen-fixes dispatch, Item 2) animates
                                // this down to 0.dp rather than jumping there the instant fullscreen
                                // toggles: searchBarSlot below now slides its own content off-screen
                                // instead of unmounting it outright, and the strip/bubble/chip need to
                                // slide into the space it vacates in the same motion, not jump ahead of
                                // it.
                                topInset = safeAnimatedTopInset,
                                // The L's top limit: the bar's bottom whether or not fullscreen is hiding it (Part B, A1 item 1).
                                searchBarBottom = mapSearchBarHeight,
                                // Item 2: only while a notice shows, and not in fullscreen, where the whole search column slides away.
                                searchNoticeBottom = if (searchNoticeMessage(uiState) != null && !isMapFullscreen()) {
                                    with(LocalDensity.current) { searchChromeHeightPx.toDp() }
                                } else {
                                    0.dp
                                },
                                // Passed as a slot, not composed at this call site directly, so it
                                // renders inside CompactMapTab's own Box — see that parameter's own
                                // doc comment for why this specific nesting is load-bearing, not
                                // cosmetic. Empty while isEditingJournalEntry on the Journal tab (the gate's
                                // own comment below, intent 2026-09-28-17; until then unconditionally) —
                                // matches the bar's own old `!isEditingJournalEntry` gate from when it
                                // lived in this scaffold's outer Column, now reproduced here since the
                                // slot is CompactMapTab's to show or not. This is a real, device-
                                // confirmed bug fix (the search field silently regaining focus after
                                // backgrounding, opening its dropdown over the user's Journal entry) —
                                // an instant, unconditional unmount, deliberately NOT animated, since
                                // an animated exit would leave the field mounted (and re-focusable) for
                                // the duration of the slide, reopening the exact race this closes.
                                // isMapFullscreen, by contrast, drives an AnimatedVisibility inside the
                                // branch below rather than a second unmount condition here — fullscreen-
                                // fixes dispatch, Item 2: "slide the chrome away instead of cutting it."
                                // SearchEntryBar composes as a translucent overlay directly inside
                                // CompactMapTab's own Box (this same doc comment's own next paragraph),
                                // never a layout sibling whose position or presence participates in
                                // measuring the map — confirmed by reading every modifier in the chain,
                                // not assumed — so sliding it is a pure translation with zero effect on
                                // the map's own measurement, the same as the already-passing "fullscreen
                                // does not change the map's own measured height" test already covers.
                                // Intent 2026-09-28-17 (owner, "Keep entry, fix the bar (Recommended)"):
                                // an open entry stays open across a tab change, since nothing on the
                                // bottom nav or rail closes a Cartography entry, so "an entry is open"
                                // alone left the Maps tab with no search bar after the user tapped Maps
                                // from an open day entry. The gate is now "an entry is open and the
                                // Journal tab is the one showing"; on Maps the bar shows normally. This
                                // slot composes only in this MAP branch, where the second half is false,
                                // so here the conjunction always resolves to the bar; it is written out
                                // so that the rule reads as the owner decided it, not as its consequence.
                                // Motion Part 2, item 1: since the tabs crossfade, this branch is also drawn while Maps fades out,
                                // when compactTab() is already the next tab. The bar is then not composed at all (`tab !=
                                // compactTab()`), so it still leaves at once and no field stays focusable through the fade: the
                                // scout's recorded "appears and vanishes on purpose" item stays instant, and the gate above still
                                // reads compactTab(), so leaving Maps for an open entry unmounts the bar at once, as before.
                                searchBarSlot = if ((journalSearchHeld && compactTab() == CompactTab.JOURNAL) || tab != compactTab()) {
                                    { _ -> }
                                } else {
                                    { compassStripHeight ->
                                        LaunchedEffect(compassStripHeight) { mapCompassStripHeight = compassStripHeight }
                                        // Fullscreen-slide-out-fixes dispatch, Item 1: the slide distance
                                        // is the bar's own height PLUS the real status-bar inset, not
                                        // fullHeight alone. This bar's top edge is the content area's
                                        // top, which is the status bar's *bottom* edge (the Scaffold
                                        // consumes the top inset as padding on the Map tab — see its
                                        // contentWindowInsets), so a translation of exactly fullHeight
                                        // lands the bar's bottom at the status bar's bottom: its entire
                                        // travel and end position is the status-bar band, nothing clips
                                        // it, and edge-to-edge makes that band transparent — confirmed on
                                        // device as the bar's text drawn over the clock. Device-only by
                                        // construction: Robolectric reports this inset as 0, so this is
                                        // a no-op in every test here and deliberately has none.
                                        val statusBarTopPx = WindowInsets.statusBars.getTop(LocalDensity.current)
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = !isMapFullscreen(),
                                            enter = slideInVertically(animationSpec = MotionTokens.panelMotionSpec()) { fullHeight -> -(fullHeight + statusBarTopPx) },
                                            exit = slideOutVertically(animationSpec = MotionTokens.panelMotionSpec()) { fullHeight -> -(fullHeight + statusBarTopPx) },
                                            // Landscape B1: clear of the overlaid rail and the
                                            // cut-out band (mapControlsPadding); zero in portrait.
                                            // Landscape B2 (S2): capped and on the punch-hole side.
                                            modifier = if (landscapeSearchWidth != null && barMeetsStrip && landscapeStripWidth > 0.dp) {
                                                // RECORD -732: the rest of the room, by the strip's measured width as padding on the
                                                // rail's side, so the bar ends on the strip's own first pixel whatever the rounding
                                                // (the rounding fix of -729, carried over).
                                                Modifier
                                                    .padding(mapControlsPadding)
                                                    .absolutePadding(
                                                        left = if (portEdge == ScreenEdge.Left) landscapeStripWidth else 0.dp,
                                                        right = if (portEdge == ScreenEdge.Right) landscapeStripWidth else 0.dp,
                                                    )
                                                    .fillMaxWidth()
                                            } else if (landscapeSearchWidth != null) {
                                                Modifier
                                                    .padding(mapControlsPadding)
                                                    .fillMaxWidth()
                                                    .wrapContentWidth(landscapeSearchAlignment)
                                                    .width(landscapeSearchWidth)
                                            } else {
                                                Modifier.padding(mapControlsPadding)
                                            },
                                        ) {
                                        Column(modifier = Modifier.mapKeepOut(MapKeepOutIds.SEARCH_BAR).onSizeChanged { searchChromeHeightPx = it.height }) {
                                            SearchEntryBar(
                                                uiState = uiState,
                                                distanceUnit = distanceUnit,
                                                onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                                                onTaxonSearchResultSelected = { result ->
                                                    // RECORD -725/-726, the owner: "just keep the keyboard open and fill it in, no search yet"; "The
                                                    // keyboard and the search bar". Only the suggestion list closes (the ViewModel empties it); the
                                                    // dropdown, the field's focus and the keyboard stay. It closed the dropdown here before.
                                                    onTaxonSearchResultSelected(result)
                                                },
                                                onClearSearch = onClearSearch,
                                                onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                                                onFieldFocused = { if (!appClearFocusInProgress[0]) showSearchDropdown = true },
                                                // The Maps tab's own bar, over its map (map chrome at 80%;
                                                // owner, "1 A": its suggestions stack over the 0.8 panel).
                                                overMap = true,
                                                // RECORD -729: the strip's height in landscape, not navigating; null keeps the bar's own.
                                                landscapeHeight = mapSearchBarHeight.takeIf { barMeetsStrip && landscapeStripHeight > 0.dp },
                                                // Dispatch 2026-10-12-06: the rounded pill. Beside the landscape strip it rounds its outer side
                                                // only and meets the strip square, edge to edge (RECORD -934; its "no join line" is replaced by
                                                // -967's one line, the pill's outline, below).
                                                pillCorners = if (landscapeSearchWidth != null && barMeetsStrip && landscapeStripWidth > 0.dp) {
                                                    MapCardCorners.outerSide(if (portEdge == ScreenEdge.Left) ScreenEdge.Right else ScreenEdge.Left)
                                                } else {
                                                    MapCardCorners.ALL
                                                },
                                                // Dispatch 2026-10-13-02: the pill's outline, all the way round in both orientations. Portrait:
                                                // RECORD -976 (the owner: "In portrait mode: Have the outlines follow the entire pill at top").
                                                // Landscape: RECORD -988 (the owner: "The pill in landscape isn't fully outlined"), replacing
                                                // -967's "no line on the screen's own edge" there; its join with the strip is still the one line
                                                // between them, drawn by the pill (the strip leaves that side open). Until -988 landscape drew
                                                // only its bottom, its join and a side with room beside it, never its top.
                                                pillOutlineSides = MapChromeOutlineSides(left = true, top = true, right = true, bottom = true),
                                            )
                                            // Item 2 (owner, "Option A"): the notice sits just below the compass strip, which is drawn at the
                                            // bar's bottom over this column, so the notice starts where the strip's own measured height ends.
                                            // In a short landscape window the strip is in the other corner and the notice stays under the bar.
                                            if (searchNoticeMessage(uiState) != null && !showRail) {
                                                Spacer(Modifier.height(compassStripHeight))
                                            }
                                            SearchNotice(uiState, overMap = true)
                                        }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
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

                        if (!isMapFullscreen()) {
                            // Dismiss-elsewhere scrim for SearchEntryBar's own "tap to focus, dismiss
                            // elsewhere" model (map/navigation redesign dispatch D): while the dropdown
                            // is open, SearchDropdown's own bounds only cover its own (bounded, scrolled)
                            // content height, not the full remaining area below SearchEntryBar — a tap
                            // on visible tab content past that edge would otherwise reach the tab
                            // underneath (panning the map, tapping a sighting dot) with no way to close
                            // the panel except the back button. This is a real, intentional interception
                            // — the opposite of Understory rule 1's "nothing here swallows a touch meant
                            // for the map," which is about the *collapsed* state, not an actively open
                            // modal panel — present only while showSearchDropdown is true, composed
                            // before SearchDropdown so that panel's own controls still win the tap they
                            // sit on (composition-order-is-hit-test-order, the same rule this file's
                            // other overlapping surfaces already rely on).
                            if (showSearchDropdown) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Map tab only: excludes SearchEntryBar's own band from the
                                        // scrim's bounds entirely, top edge down. That bar now composes
                                        // inside CompactMapTab's own searchBarSlot (that call site's own
                                        // doc comment — needed for its 80% fill to actually blend with
                                        // the map, an interop-nesting requirement, not a cosmetic one),
                                        // which puts it earlier in this Box's composition order than
                                        // this scrim — composition-order-is-hit-test-order would
                                        // otherwise mean the scrim wins every tap on the bar while the
                                        // dropdown is open, breaking "type in the bar while the dropdown
                                        // is still showing." Excluding the region outright, rather than
                                        // reordering composition, keeps the scrim's own intercept of the
                                        // map underneath it intact (composed before CompactMapTab would
                                        // let the map's own pan gesture win those same taps instead).
                                        //
                                        // Same reasoning, bottom edge up, for the bottom nav — fullscreen-
                                        // fixes dispatch, Item 1 (third design). Before that dispatch,
                                        // the nav always lived in Scaffold's own bottomBar slot, a
                                        // separate composition subtree this scrim's fillMaxSize() never
                                        // reached, so the nav stayed tappable regardless of this dropdown
                                        // on every tab, Map included. Now that the Map tab's own nav
                                        // instance composes inside CompactMapTab's own Box (a descendant
                                        // of this same weight(1f) Box the scrim also fills), leaving this
                                        // unexcluded would silently swallow every tap on it while the
                                        // dropdown is open — confirmed, reproducible: this is exactly
                                        // what AvailabilityScreenMapIconStackTest's own bottom-nav tests
                                        // caught (map-slot still present after "tapping" List/Seasonal/
                                        // Tools, because the tap never reached the nav's own onClick at
                                        // all — though this scrim turned out not to be the actual
                                        // culprit there; see the SearchDropdown AnimatedVisibility's own
                                        // heightIn doc comment below for what was). bottomNavHeight
                                        // (that hoisted var's own doc comment explains why it's a live
                                        // measurement, not a fixed constant) applies only on the Map
                                        // tab, where the nav lives in this Box; every other tab keeps it
                                        // in bottomBar again, so this is a no-op there.
                                        .padding(
                                            top = if (compactTab() == CompactTab.MAP) mapSearchBarHeight else 0.dp,
                                            bottom = if (compactTab() == CompactTab.MAP) bottomNavHeight else 0.dp,
                                        )
                                        // Landscape B2 (S2): off the overlaid rail, so a tap on the
                                        // rail while the dropdown is open still reaches the rail.
                                        // Zero in portrait; Map tab only (elsewhere the rail is
                                        // beside this Box, not in it).
                                        .padding(if (compactTab() == CompactTab.MAP) mapControlsPadding else PaddingValues(0.dp))
                                        .testTag(SEARCH_DROPDOWN_SCRIM_TAG)
                                        .pointerInput(Unit) {
                                            detectTapGestures { showSearchDropdown = false }
                                        },
                                )
                            }
                            // Fully qualified: an implicit ColumnScope receiver is still in scope from
                            // the outer Column this Box sits inside, which makes the bare name resolve
                            // to ColumnScope's own AnimatedVisibility overload instead of this top-level
                            // one — Kotlin then refuses it ("cannot be called with an implicit
                            // receiver") since a BoxScope, not a ColumnScope, is this call's real one.
                            // fullscreen-fixes dispatch, Item 1 (third design): fed into heightIn below.
                            // RECORD -750, item 1 (the owner: "The drop down search panel has a gap between the top of the panel and
                            // the bottom of the bar. It should sit flush against the bar to look continuous"): in a landscape window
                            // the strip is beside the bar, not under it, so the panel starts at the bar's own bottom, its divider. The
                            // strip's clearance below was added there too, and since the landscape strip reports no portrait height
                            // (mapCompassStripHeight is 0 there) it fell back to one text line, the gap. Portrait is unchanged.
                            val searchDropdownTopOffset = when {
                                compactTab() != CompactTab.MAP -> 0.dp
                                landscapeSearchWidth != null -> mapSearchBarHeight
                                else -> mapSearchBarHeight + mapCompassStripHeight.coerceAtLeast(compassStripClearance)
                            }
                            // Part 1 layout fixes, item 3 (Part 1's device check, check 8): on the Maps tab
                            // this Box runs to the window's bottom, since the tab's contentWindowInsets
                            // reserve only the top and sides, so the keyboard does not shrink it. The panel's
                            // cap therefore keeps it above the keyboard's own inset, or above the nav band,
                            // whichever reaches higher. The other tabs' Scaffold padding (safeDrawing, which
                            // includes the keyboard) shrinks this Box already. Robolectric reports no
                            // keyboard, so this is device-only by construction.
                            val searchDropdownImeBottom = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
                            // RECORD -750, item 2 (the owner: "If the keyboard is floating keep it to the one side. The bar still should
                            // scroll up to reveal the search button at the bottom"): in landscape the panel's cap now follows the
                            // keyboard as portrait's does (below), and the panel is told the keyboard is up, read from the real IME
                            // insets rather than only from its own viewport shrinking: docked (a bottom inset) or floating (visible,
                            // but a floating keyboard reports no inset). Either way the panel scrolls to its end, so Search and Set on
                            // map sit above a docked keyboard's top or at the panel's own bottom. Its width and side never change.
                            // Device-only: Robolectric has no keyboard, and whether a floating keyboard reports itself visible is the
                            // OEM's (see the -750 report).
                            @OptIn(ExperimentalLayoutApi::class)
                            val searchDropdownKeyboardUp = landscapeSearchWidth != null && (searchDropdownImeBottom > 0.dp || WindowInsets.isImeVisible)
                            // RECORD -691: no minimum touch target while it leaves, so a touch near a leaving piece under 48 dp is not handed to it (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
                            NoTouchTargetExpansion(active = !showSearchDropdown) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showSearchDropdown,
                                enter = expandVertically(animationSpec = MotionTokens.panelMotionSpec()) + fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
                                exit = shrinkVertically(animationSpec = MotionTokens.panelMotionSpec()) + fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
                                // Starts below the compass strip rather than painting over it — Map tab
                                // only, since the strip only exists inside CompactMapTab; every other
                                // tab keeps the panel flush against the top like before. No extra gap
                                // beyond the strip's own measured height: the owner's own direct call
                                // ("bar, strip, drawer... each meeting the next without a break") — an
                                // earlier version added Spacing.sm here on top of compassStripClearance,
                                // which read as a seam between the strip and this panel rather than one
                                // continuous piece of chrome. searchBarHeight added on top of that, Map
                                // tab only: SearchEntryBar now overlays the map above the strip on this
                                // tab (see this scaffold's own searchBarHeight doc comment), so the
                                // drawer needs to start below both, not just the strip.
                                //
                                // heightIn(max=...) — fullscreen-fixes dispatch, Item 1 (third design):
                                // this panel's own SearchDropdown has a verticalScroll expecting a
                                // bounded parent, but nothing here previously bounded it — a latent
                                // overflow, harmless before this dispatch because the weight(1f) Box
                                // this sits in never held anything sensitive in the overflow region.
                                // Now that the Map tab's own bottom nav lives inside that same Box (see
                                // CompactMapTab's own doc comment), an unbounded panel here — expanded
                                // via "Enter coordinates manually" (a fold dispatch 2026-09-28-697 removed), exactly what
                                // AvailabilityScreenMapIconStackTest's own searchAReferenceRegion()
                                // helper does — measured tall enough to physically reach into the nav's
                                // own screen band and, being composed after it, won every tap there:
                                // confirmed via that test's own bounds queries (SearchDropdown's own
                                // reported bounds genuinely overlapped the nav's), not assumed. Capped
                                // to what's actually left below this panel's own top offset, minus the
                                // nav's own band on the Map tab, so it scrolls instead of overflowing.
                                modifier = (if (landscapeSearchWidth != null) {
                                    // Landscape B2 (S2): the bar's width and side.
                                    Modifier
                                        .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                        .padding(mapControlsPadding)
                                        .padding(top = searchDropdownTopOffset)
                                        .width(landscapeSearchWidth)
                                        // RECORD -750, item 2: above a docked keyboard, as portrait's cap is (no nav band in landscape).
                                        .heightIn(max = (maxHeight - searchDropdownTopOffset - searchDropdownImeBottom).coerceAtLeast(0.dp))
                                } else if (railBeside) {
                                    // Dispatch 2026-10-17-09: beside the rail the pill is the Maps pill's size, at the left
                                    // (tabLandscapePillPlace), so its panel takes the pill's width and place, as on Maps (Landscape B2,
                                    // S2), instead of the readable-width column the pill used to sit in. No nav band in landscape.
                                    Modifier
                                        .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                        .padding(top = searchDropdownTopOffset)
                                        .then(tabLandscapePillPlace)
                                        .heightIn(max = (maxHeight - searchDropdownTopOffset).coerceAtLeast(0.dp))
                                } else {
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = searchDropdownTopOffset)
                                        .heightIn(
                                            max = maxHeight - searchDropdownTopOffset -
                                                (if (compactTab() == CompactTab.MAP) maxOf(bottomNavHeight, searchDropdownImeBottom) else 0.dp),
                                        )
                                })
                                    // Scout item Q5 (dispatch 2026-09-28-652, item 7; the owner, RECORD -651: "Fix it in this
                                    // sweep"): from the moment the panel starts closing it takes no touch. The dismiss scrim above
                                    // is already gone by then (it is composed only while showSearchDropdown), so a tap anywhere,
                                    // the shrinking panel included, reaches the map, and none of the panel's buttons can be pressed.
                                    // The panel still draws its shrink and fade. Last in the chain, so its bounds are the panel's.
                                    .leavingTakesNoTouches(leaving = !showSearchDropdown),
                            ) {
                                SearchDropdown(
                                    uiState = uiState,
                                    distanceUnit = distanceUnit,
                                    onRecentSearchSelected = { summary ->
                                        showSearchDropdown = false
                                        onRecentSearchSelected(summary)
                                    },
                                    currentTime = currentTime,
                                    onManualLatChanged = onManualLatChanged,
                                    onManualLngChanged = onManualLngChanged,
                                    onSearchManualCoordinates = {
                                        showSearchDropdown = false
                                        onSearchManualCoordinates()
                                    },
                                    onRadiusChanged = onRadiusChanged,
                                    onMonthSelected = onMonthSelected,
                                    onUseCurrentLocation = {
                                        showSearchDropdown = false
                                        onUseCurrentLocation()
                                    },
                                    onSetOnMap = {
                                        showSearchDropdown = false
                                        onCompactTabChange(CompactTab.MAP)
                                        onSelectedTabChange(ResultsTab.MAP)
                                        pickingSearchLocationOnMap = true
                                    },
                                    // Over the Maps tab's map only; on the other tabs its Month menu stays solid.
                                    overMap = compactTab() == CompactTab.MAP,
                                    keyboardUp = searchDropdownKeyboardUp,
                                )
                            }
                            }
                        }
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
