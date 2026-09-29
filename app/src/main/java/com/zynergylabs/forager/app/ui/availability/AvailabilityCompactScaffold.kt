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
// cartographyUiState, isShortLandscapeWindow, portEdge, logDraftSnackbarHostState, uiState,
// distanceUnit, currentTime, mapSlot, mapIconClusterPosition, mapRenderMode, isNightMode,
// isRecording, startRecordingErrorMessage, breadcrumbPoints, mapWaypoints, returnToStart,
// isReturning, isNavigating, isOffTrack, compassProvider, computeTrueHeading, navigationTarget,
// pathHomeMeters, basemap, tracks, waypoints, waypointsErrorMessage, waypointEntryReferenceCounts,
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

import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.padding
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
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
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
    keyboardController: SoftwareKeyboardController?,
    compactTab: () -> CompactTab,
    logUiState: MushroomLogUiState,
    logPhotoAcquisitionInFlight: () -> Boolean,
    cartographyUiState: CartographyUiState,
    isDrawerOpen: () -> Boolean,
    isShortLandscapeWindow: Boolean,
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
    /** J8-3: the entry report's "Show on map" and "Hide from map"; threaded to [JournalTab]. */
    onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit = { _, _ -> },
    /** M1: what the Maps tab's glyph bubbles look records up in; threaded to [CompactMapTab]. */
    mapBubbleSources: MapRecordSources = MapRecordSources(),
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    mapIconClusterPosition: MapIconClusterPositionState,
    /** Part 1 layout fixes, item 4: the Maps tab's camera, kept above the tab switch; threaded to [CompactMapTab]. */
    mapCameraMemory: MapCameraMemory,
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
    isOffTrack: Boolean,
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    navigationTarget: Waypoint?,
    pathHomeMeters: Double?,
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
    /** Intent 2026-09-28-44, F3: an open find's mode and M1's find over the view, handed to [JournalTab]; see its parameters. Not among the 109. */
    findEntryModeState: MutableState<JournalEntryMode>,
    findOverViewState: MutableState<FindOverView?>,
) {
        // SearchDropdown, under ActiveSearchSummary — see that composable's own onToggleSearch doc
        // comment. Local to this scaffold, not AvailabilityUiState: which panel is showing is a
        // display decision the ViewModel has no part in, same reasoning as mapMode/drawerPanel above.
        var showSearchDropdown by remember { mutableStateOf(false) }
        // The search bar's one-shot "open the manual coordinates" (continuation 2026-09-28-40); see
        // SearchDropdown's expandManualCoordinatesRequested. Armed once per opening of the dropdown,
        // when showSearchDropdown goes from false to true, never on a focus gain (continuation
        // 2026-09-28-42, "once, on the tap"): the dropdown's scroll clears the field's focus, and
        // re-arming on the focus coming back looped under Robolectric (547 s, OutOfMemoryError).
        var expandManualCoordinatesRequested by remember { mutableStateOf(false) }
        LaunchedEffect(showSearchDropdown) {
            if (showSearchDropdown) expandManualCoordinatesRequested = true
        }
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
            targetValue = if (isMapFullscreen()) 0.dp else searchBarHeight,
            animationSpec = MotionTokens.panelMotionSpec(),
            label = "mapTopInset",
        )
        val safeAnimatedTopInset = animatedTopInset.coerceAtLeast(0.dp)
        // Dispatch 2026-09-28-104, item 2: the search column's measured height (the bar, and the notice below the compass strip when
        // one shows). The Maps tab keeps the icon cluster below its bottom while a notice shows; nothing else reads it.
        var searchChromeHeightPx by remember { mutableStateOf(0) }
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
        LaunchedEffect(showSearchDropdown) {
            if (!showSearchDropdown) {
                focusManager.clearFocus(force = true)
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
        // own top level — DrawerPanel.Log's LogPanel too) shares this one wrapped callback, so the
        // same Snackbar covers every one of them from a single place rather than a
        // window-class-specific copy per host. Backgrounding above is the deliberate exception (see
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
        var bottomNavHeightPx by remember { mutableStateOf(0f) }
        val bottomNavDensity = LocalDensity.current
        // Landscape B1: in a short landscape window the bottom bar is replaced by a navigation
        // rail on the charger-port edge, so neither ForagerBottomNav call site renders. Two
        // containers for the one rail (Resolution R12, revised on the owner's correction: "the
        // map resizes when hiding the UI and that's a UX problem"):
        //  - Map tab: the rail is an 80% overlay on the map, composed inside CompactMapTab where
        //    the bottom bar's overlay is in portrait. The map stays full-bleed and never changes
        //    size; the map's *controls* are padded clear of the rail instead (mapControlsPadding
        //    below), the way portrait keeps them clear of the bottom bar by its measured height.
        //    In fullscreen the rail is absent, with no animation (R13 revised, interim until B2).
        //  - Every other tab: an opaque rail beside the content (railBeside), since there is no
        //    map to keep the size of and text under a translucent rail would hurt reading.
        // Portrait, and every window that is not short, is exactly as before.
        val showRail = isShortLandscapeWindow
        val railBeside = showRail && compactTab() != CompactTab.MAP
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
        var mapRailWidthPx by remember { mutableStateOf(0f) }
        val mapRailWidth = with(bottomNavDensity) { mapRailWidthPx.toDp() }
        // What the Map tab's controls are padded by in a short landscape window, and never the
        // map itself (R12, R13, R17, all revised): the displayCutout inset on the sides, so no
        // control sits in the cut-out band while the tiles draw under it; and on the port side the
        // rail's measured width, or, in fullscreen with the rail gone, the navigationBars inset,
        // so no control sits under the rail or the system bar. The top needs nothing here: the
        // Map tab's Scaffold padding already keeps the whole tab below the status bar, as in
        // portrait. Zero everywhere else, so portrait is untouched.
        val mapControlsPadding = if (showRail) {
            val portSide = portEdge.horizontalInsetsSide()
            val portInset = if (isMapFullscreen()) {
                WindowInsets.navigationBars.only(portSide)
            } else if (portEdge == ScreenEdge.Left) {
                WindowInsets(left = mapRailWidth)
            } else {
                WindowInsets(right = mapRailWidth)
            }
            WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal).union(portInset).asPaddingValues()
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
        Scaffold(
            snackbarHost = {
                // Material3's own snackbar, with its default colours passed explicitly. Its content
                // colour is its own role (`SnackbarDefaults.contentColor`), not derived from the fill.
                SnackbarHost(logDraftSnackbarHostState) { data ->
                    // Map chrome at 80% over the Maps tab, following the tab (planner message -77, Q4).
                    val snackbarColor = mapChromeFill(SnackbarDefaults.color, compactTab() == CompactTab.MAP)
                    val snackbarContentColor = SnackbarDefaults.contentColor
                    Snackbar(
                        snackbarData = data,
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
                            .then(
                                when {
                                    compactTab() != CompactTab.MAP -> Modifier
                                    showRail -> Modifier
                                        .padding(mapControlsPadding)
                                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                    isMapFullscreen() -> Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                                    else -> Modifier.padding(bottom = bottomNavHeight)
                                },
                            )
                            .testTag(COMPACT_SNACKBAR_TAG)
                            .mapChromeContainerColor(snackbarColor)
                            .mapChromeContentColor(snackbarContentColor),
                        containerColor = snackbarColor,
                        contentColor = snackbarContentColor,
                    )
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
                shortLandscapeContentInsets()
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
                if (compactTab() != CompactTab.MAP && !showRail) {
                    ForagerBottomNav(
                        selectedTab = compactTab(),
                        isDrawerOpen = isDrawerOpen(),
                        onTabSelected = onBottomNavTabSelected,
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
                if (railBeside && portEdge == ScreenEdge.Left) {
                    ForagerNavigationRail(
                        selectedTab = compactTab(),
                        isDrawerOpen = isDrawerOpen(),
                        onTabSelected = onBottomNavTabSelected,
                        portEdge = portEdge,
                    )
                }
                Column(
                    modifier = Modifier
                        // Landscape B1: the Row above carries Scaffold's padding now, and this
                        // Column takes whatever the rail (if any) leaves — in portrait, all of it.
                        .weight(1f)
                        .fillMaxHeight()
                        // Landscape B3 (P11, R9): beside the opaque rail, the tab's content — the
                        // search bar above it included — is capped at the readable width and centred
                        // in what the rail leaves. Once, here, not per destination. Seasonal's own
                        // cap (SeasonalTab) is the same 640 dp, so the two compose to 640. Scrolling
                        // stays each destination's own. Portrait and non-short windows skip this.
                        .then(
                            if (railBeside) {
                                Modifier
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                                    .widthIn(max = READABLE_CONTENT_MAX_WIDTH)
                                    .fillMaxWidth()
                            } else {
                                Modifier
                            },
                        ),
                ) {
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
                    // windows that are not short are unchanged. The rail shows exactly in a short
                    // landscape window (showRail), the dispatch's "isShortWindow() and landscape".
                    val journalHidesSearchHeader = showRail && compactTab() == CompactTab.JOURNAL && !journalScreenState.searchHeaderRevealed
                    // Hiding the header closes its dropdown too, so the dropdown's dismiss scrim
                    // (below) is never left over content with no bar above it.
                    LaunchedEffect(journalHidesSearchHeader) {
                        if (journalHidesSearchHeader) showSearchDropdown = false
                    }
                    if (!isMapFullscreen() && compactTab() != CompactTab.MAP && !isEditingJournalEntry && !journalHidesSearchHeader) {
                        SearchEntryBar(
                            uiState = uiState,
                            distanceUnit = distanceUnit,
                            onUseCurrentLocation = {
                                showSearchDropdown = false
                                onUseCurrentLocation()
                            },
                            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                            onTaxonSearchResultSelected = { result ->
                                onTaxonSearchResultSelected(result)
                                showSearchDropdown = false
                            },
                            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                            onFieldFocused = { showSearchDropdown = true },
                        )
                        SearchNotice(uiState)
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
                    // padded area) — see mainScaffold's own doc comment on this same pattern. Each branch
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
                            minOf(LANDSCAPE_SEARCH_MAX_WIDTH, maxWidth / 2 - punchHoleInset - LANDSCAPE_SEARCH_CENTRE_GAP).coerceAtLeast(0.dp)
                        } else {
                            null
                        }
                        val landscapeSearchAlignment = if (punchHoleEdge == ScreenEdge.Left) Alignment.Start else Alignment.End
                        when (compactTab()) {
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
                                // Landscape B2: the punch-hole side, and the search bar's capped
                                // width there (the chip sits under it, within it).
                                punchHoleEdge = if (showRail) punchHoleEdge else null,
                                landscapeSearchWidth = landscapeSearchWidth,
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
                                renderMode = mapRenderMode.copy(bottomInset = safeAttributionBottomInset, attributionEndInset = safeAttributionEndInset, attributionBottomInset = attributionButtonBottomInset),
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
                                // Landscape B1: in a short landscape window this tab overlays
                                // the rail on the port edge in place of its bottom bar, and pads
                                // its controls clear of it (showRail, mapControlsPadding).
                                railPortEdge = if (showRail) portEdge else null,
                                onRailWidthMeasured = { mapRailWidthPx = it },
                                controlsPadding = mapControlsPadding,
                                onLocateMe = onLocateMe,
                                isRecording = isRecording,
                                onToggleRecording = onToggleRecording,
                                startRecordingErrorMessage = startRecordingErrorMessage,
                                breadcrumbPoints = breadcrumbPoints,
                                waypoints = mapWaypoints,
                                onDropWaypoint = onDropWaypoint,
                                returnToStart = returnToStart,
                                isReturning = isReturning,
                                isNavigating = isNavigating,
                                isOffTrack = isOffTrack,
                                onToggleReturning = onToggleReturning,
                                compassProvider = compassProvider,
                                computeTrueHeading = computeTrueHeading,
                                navigationTarget = navigationTarget,
                                pathHomeMeters = pathHomeMeters,
                                currentTime = currentTime,
                                taxonFilter = mapTaxonFilter(),
                                onClearTaxonFilter = onClearMapTaxonFilter,
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
                                // so that the rule reads as the owner ruled it, not as its consequence.
                                searchBarSlot = if (isEditingJournalEntry && compactTab() == CompactTab.JOURNAL) {
                                    { _ -> }
                                } else {
                                    { compassStripHeight ->
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
                                            modifier = if (landscapeSearchWidth != null) {
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
                                                onUseCurrentLocation = {
                                                    showSearchDropdown = false
                                                    onUseCurrentLocation()
                                                },
                                                onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                                                onTaxonSearchResultSelected = { result ->
                                                    onTaxonSearchResultSelected(result)
                                                    showSearchDropdown = false
                                                },
                                                onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                                                onFieldFocused = { showSearchDropdown = true },
                                                // The Maps tab's own bar, over its map (map chrome at 80%;
                                                // owner, "1 A": its suggestions stack over the 0.8 panel).
                                                overMap = true,
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
                            CompactTab.JOURNAL -> JournalTab(
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
                                onCloseEntry = onCloseLogEntry,
                                onStartEntry = onStartLogEntry,
                                onEntryChanged = onLogEntryChanged,
                                onStartEditingEntry = onStartEditingLogEntry,
                                onSaveEntry = onSaveLogEntry,
                                onCancelEditing = onCancelLogEntryEditing,
                                onLeaveEditingIncidentally = leaveLogEntryEditingOfferingDiscard,
                                onPhotoAcquisitionInFlightChanged = { inFlight -> onLogPhotoAcquisitionInFlightChange(inFlight) },
                                onAddPhoto = onAddLogPhoto,
                                onRemovePhoto = onRemoveLogPhoto,
                                onPullPhoto = onPullLogPhoto,
                                onDeleteEntry = onDeleteLogEntry,
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
                                onOpenEntryForEditing = onOpenLogEntryForEditing,
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
                                onPendingDestinationConsumed = { onPendingJournalDestinationChange(null) },
                                onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
                                journalState = journalScreenState,
                                // Intent 2026-09-28-28: while the Tools drawer is open over the
                                // Journal, Back closes the drawer, so the Journal's own handlers are
                                // off. See JournalTab's backEnabled for why off rather than outranked.
                                backEnabled = !isDrawerOpen(),
                                cartographyEntryModeState = cartographyEntryModeState,
                                findEntryModeState = findEntryModeState,
                                findOverViewState = findOverViewState,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Never actually reached — CompactTab.TOOLS never becomes compactTab itself,
                            // see that entry's own doc comment. Kept as a real branch (not an else) so
                            // this stays an exhaustive, honest `when` rather than one that silently
                            // compiles around a case the compiler can't see is impossible.
                            CompactTab.TOOLS -> Unit
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
                                            top = if (compactTab() == CompactTab.MAP) searchBarHeight else 0.dp,
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
                            val searchDropdownTopOffset = if (compactTab() == CompactTab.MAP) searchBarHeight + compassStripClearance else 0.dp
                            // Part 1 layout fixes, item 3 (Part 1's device check, check 8): on the Maps tab
                            // this Box runs to the window's bottom, since the tab's contentWindowInsets
                            // reserve only the top and sides, so the keyboard does not shrink it. The panel's
                            // cap therefore keeps it above the keyboard's own inset, or above the nav band,
                            // whichever reaches higher. The other tabs' Scaffold padding (safeDrawing, which
                            // includes the keyboard) shrinks this Box already. Robolectric reports no
                            // keyboard, so this is device-only by construction.
                            val searchDropdownImeBottom = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
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
                                // via "Enter coordinates manually," exactly what
                                // AvailabilityScreenMapIconStackTest's own searchAReferenceRegion()
                                // helper does — measured tall enough to physically reach into the nav's
                                // own screen band and, being composed after it, won every tap there:
                                // confirmed via that test's own bounds queries (SearchDropdown's own
                                // reported bounds genuinely overlapped the nav's), not assumed. Capped
                                // to what's actually left below this panel's own top offset, minus the
                                // nav's own band on the Map tab, so it scrolls instead of overflowing.
                                modifier = if (landscapeSearchWidth != null) {
                                    // Landscape B2 (S2): the bar's width and side.
                                    Modifier
                                        .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                        .padding(mapControlsPadding)
                                        .padding(top = searchDropdownTopOffset)
                                        .width(landscapeSearchWidth)
                                        .heightIn(max = maxHeight - searchDropdownTopOffset)
                                } else {
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = searchDropdownTopOffset)
                                        .heightIn(
                                            max = maxHeight - searchDropdownTopOffset -
                                                (if (compactTab() == CompactTab.MAP) maxOf(bottomNavHeight, searchDropdownImeBottom) else 0.dp),
                                        )
                                },
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
                                    expandManualCoordinatesRequested = expandManualCoordinatesRequested,
                                    onManualCoordinatesExpandConsumed = { expandManualCoordinatesRequested = false },
                                    // Over the Maps tab's map only; on the other tabs its Month menu stays solid.
                                    overMap = compactTab() == CompactTab.MAP,
                                )
                            }
                        }
                    }
                }
                if (railBeside && portEdge != ScreenEdge.Left) {
                    ForagerNavigationRail(
                        selectedTab = compactTab(),
                        isDrawerOpen = isDrawerOpen(),
                        onTabSelected = onBottomNavTabSelected,
                        portEdge = portEdge,
                    )
                }
            }
        }
}

/** Landscape B2 (S2): the search bar's width cap in a short landscape window. */
internal val LANDSCAPE_SEARCH_MAX_WIDTH = 384.dp

/** Landscape B2 (S2): how far short of the map's centre line the capped search bar ends. */
internal val LANDSCAPE_SEARCH_CENTRE_GAP = 8.dp

/** The compact scaffold's snackbar, for tests (map chrome at 80%, dispatch 2026-09-28-56 as amended by -58). */
internal const val COMPACT_SNACKBAR_TAG = "compact-snackbar"
