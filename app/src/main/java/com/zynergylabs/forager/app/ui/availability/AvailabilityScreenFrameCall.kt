package com.zynergylabs.forager.app.ui.availability

// Dispatch 2026-10-26-17 (RECORD -1682), a pure move: the frame's call, CompactMainScaffold, moved out of AvailabilityScreen's
// body, where it was the body of the `compactMainScaffold` lambda the drawer host composes. The body's lambda now calls this,
// and nothing else, so the frame is composed at the same point, inside the drawer host, as before. The call itself is moved
// unchanged, one indent shallower. Every name it reads is bound at the top of this function under the name the body used:
//  - the body's remembered State is reached through the holders it is remembered in (ScreenTabSelections, ScreenToolsDrawer,
//    ScreenToolsOfflineMapsReturn, ScreenPendingJournalRequests, MapFullscreenFlag, NavigationViewRequestsState) or, for the
//    four States still remembered in the body, through those States themselves; each is a delegate onto the original State,
//    never a copy (Stage G), and is read where the body's lambda read it, inside this call, so a change still recomposes the
//    frame's call and not the body;
//  - the bundle fields only this call read are unpacked here, moved from the body's unpacks with their only reader; the
//    navigation and map-controls fields the body also reads are unpacked here too, from the same bundles, so they are the same
//    values;
//  - everything else is a parameter of the same name: values the body works out on each composition, captured by the body's
//    lambda as they were, so none can go stale (the case Stage G's getter rule exists for).

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin

@Composable
internal fun AvailabilityScreenFrameCall(
    // The body's remembered State, through its holders and the four States still remembered in the body.
    tabSelections: ScreenTabSelections,
    toolsDrawer: ScreenToolsDrawer,
    toolsOfflineMapsReturn: ScreenToolsOfflineMapsReturn,
    pendingJournalRequests: ScreenPendingJournalRequests,
    mapFullscreen: MapFullscreenFlag,
    navigationViewRequests: NavigationViewRequestsState,
    chosenMapModeState: MutableState<MapMode?>,
    mapWaypointReopenState: MutableState<WaypointNavigationOrigin?>,
    recordsWaypointReopenState: MutableState<String?>,
    showDecimalDegreesState: MutableState<Boolean>,
    screenState: AvailabilityScreenState,
    // The body's values of this composition.
    isLandscapeWindow: Boolean,
    portEdge: ScreenEdge,
    punchHoleEdge: ScreenEdge,
    logDraftSnackbarHostState: SnackbarHostState,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    mapIconClusterPosition: MapIconClusterPositionState,
    mapCameraMemory: MapCameraMemory,
    mapReturnMemory: MapReturnMemory,
    mapRenderMode: MapRenderMode,
    mapLayersControls: MapLayersControls,
    isNightMode: Boolean,
    mapWaypoints: List<Waypoint>,
    isNavigating: Boolean,
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    target: Waypoint?,
    navigatingToWaypoint: Waypoint?,
    nextWaypointLine: StraightLine?,
    waypointNavigation: WaypointNavigationActions,
    basemap: Basemap,
    mapBubbleSources: MapRecordSources,
    onClearMapTaxonFilter: () -> Unit,
    mapReturn: MapReturnCallbacks,
    mapMode: MapMode,
    leaveLogEntryEditingOfferingDiscard: () -> Unit,
    onViewSpeciesOnMap: (Long) -> Unit,
    // The screen's own bundles.
    navigation: AvailabilityScreenNavigation,
    mapControls: AvailabilityScreenMapControls,
    backupAndImport: AvailabilityScreenBackupAndImport,
    cameraInputs: AvailabilityScreenCamera,
    recording: AvailabilityScreenRecording,
    settings: AvailabilityScreenSettings,
    findsLog: AvailabilityScreenFindsLog,
    journalEntries: AvailabilityScreenJournalEntries,
    entryReportData: AvailabilityScreenEntryReportData,
    offlineMaps: AvailabilityScreenOfflineMaps,
    records: AvailabilityScreenRecords,
    searchInputs: AvailabilityScreenSearchInputs,
) {
    // The body's State, under the names the body used.
    var compactTab by tabSelections::compactTab
    var selectedTab by tabSelections::selectedTab
    var logPhotoAcquisitionInFlight by tabSelections::logPhotoAcquisitionInFlight
    val mapTaxonFilter by tabSelections::mapTaxonFilter
    val journalScreenState = tabSelections.journalScreenState
    val cartographyEntryModeState = tabSelections.cartographyEntryModeState
    val cartographyOpenEntryGroupsState = tabSelections.cartographyOpenEntryGroupsState
    val findEntryModeState = tabSelections.findEntryModeState
    val findOverViewState = tabSelections.findOverViewState
    val focusManager = toolsDrawer.focusManager
    val keyboardController = toolsDrawer.keyboardController
    val appClearFocusInProgress = toolsDrawer.appClearFocusInProgress
    var isDrawerOpen by toolsDrawer::isDrawerOpen
    val toolsReturn by toolsOfflineMapsReturn::toolsReturn
    var pendingJournalDestination by pendingJournalRequests::pendingJournalDestination
    var pendingJournalFindId by pendingJournalRequests::pendingJournalFindId
    var pendingJournalEntryId by pendingJournalRequests::pendingJournalEntryId
    var pendingJournalTrackId by pendingJournalRequests::pendingJournalTrackId
    var isMapFullscreen by mapFullscreen::isMapFullscreen
    var navigationFollowBroken by navigationViewRequests::navigationFollowBroken
    var navigationViewRequestId by navigationViewRequests::navigationViewRequestId
    var navigationZoomPending by navigationViewRequests::navigationZoomPending
    var chosenMapMode by chosenMapModeState
    val mapWaypointReopen by mapWaypointReopenState
    val recordsWaypointReopen by recordsWaypointReopenState
    var showDecimalDegrees by showDecimalDegreesState
    val startWaypointNavigation = waypointNavigation.start
    val onNavigationControl = waypointNavigation.onNavigationControl
    val onFindReportClosed = mapReturn.onFindReportClosed
    val onEntryClosed = mapReturn.onEntryClosed
    val onFindDeleted = mapReturn.onFindDeleted
    // Phase 2.1 (dispatch 2026-10-19-28): the camera bundle, unpacked under the parameter names it replaced.
    val onOpenCamera = cameraInputs.onOpenCamera
    // Phase 2.1 (dispatch 2026-10-19-28): the backup and import bundle, unpacked under the parameter names it replaced.
    val onGpxFilePicked = backupAndImport.onGpxFilePicked
    // Phase 2.1 (dispatch 2026-10-19-28): the map controls bundle, unpacked under the parameter names it replaced.
    val onMapFullscreenChanged = mapControls.onMapFullscreenChanged
    val onLocateMe = mapControls.onLocateMe
    val onMapModeSelected = mapControls.onMapModeSelected
    // Phase 2.1 (dispatch 2026-10-19-28): the navigation bundle, unpacked under the parameter names it replaced.
    val returnToStart = navigation.returnToStart
    val isReturning = navigation.isReturning
    val isArrivalHeld = navigation.isArrivalHeld
    val isOffTrack = navigation.isOffTrack
    val returnRoute = navigation.returnRoute
    val routeLine = navigation.routeLine
    val onRetryRoute = navigation.onRetryRoute
    val onNavigateToWaypoint = navigation.onNavigateToWaypoint
    // The compact-only Scaffold: full-bleed map, bottom nav instead of a top tab row, and — while
    // the Maps tab's fullscreen icon is toggled on — the top app bar and bottom nav both hidden,
    // leaving only the map and its floating icon stack. See docs/plans/map-redesign.md decisions
    // #2, #4, #5.
    //
    // No top app bar at all any more: the species/category search bar that used to live there has
    // moved twice since — first into the Tools drawer (per the project owner's own framing at the
    // time, "the whole side panel is the search feature"), then out again into [SearchDropdown]
    // alongside Recent Searches (dispatch C's own owner call, "move recent searches, and species
    // search... into the new search drawer" — see [SearchDropdown]'s own doc comment for the
    // resulting shape). [ActiveSearchSummary] is what remains visible up top: a read-only "what am
    // I currently searching" strip whose own tap opens [SearchDropdown] (`onToggleSearch`) — not a
    // second way to reopen the species suggestion popup, which is why `onReopenTaxonSuggestions` is
    // a no-op here.
    CompactMainScaffold(
        isMapFullscreen = { isMapFullscreen },
        focusManager = focusManager,
        appClearFocusInProgress = appClearFocusInProgress,
        keyboardController = keyboardController,
        compactTab = { compactTab },
        logPhotoAcquisitionInFlight = { logPhotoAcquisitionInFlight },
        isDrawerOpen = { isDrawerOpen },
        toolsReturnPending = { toolsReturn != null },
        isLandscapeWindow = isLandscapeWindow,
        portEdge = portEdge,
        punchHoleEdge = punchHoleEdge,
        logDraftSnackbarHostState = logDraftSnackbarHostState,
        uiState = uiState,
        distanceUnit = distanceUnit,
        pendingJournalDestination = { pendingJournalDestination },
        pendingJournalTrackId = { pendingJournalTrackId },
        onGpxFilePicked = onGpxFilePicked,
        currentTime = currentTime,
        // Phase 2.6 B2: the map and navigation values, the same expressions in the same order (CompactScaffoldMapInputs).
        mapInputs = CompactScaffoldMapInputs(
            mapSlot = mapSlot,
            mapIconClusterPosition = mapIconClusterPosition,
            mapCameraMemory = mapCameraMemory,
            mapReturnMemory = mapReturnMemory,
            mapRenderMode = mapRenderMode,
            mapLayers = mapLayersControls,
            isNightMode = isNightMode,
            mapWaypoints = mapWaypoints,
            returnToStart = returnToStart,
            isReturning = isReturning,
            isNavigating = isNavigating,
            isArrivalHeld = isArrivalHeld,
            isOffTrack = isOffTrack,
            // Dispatch 2026-09-28-645: the map's quick settings. Its sundown and off-track rows are
            // the Tools drawer's own values and callbacks (the same two classes its Sundown section is given below).
            quickSettings = mapQuickSettingsFor(uiState, recording, settings, currentTime),
            compassProvider = compassProvider,
            computeTrueHeading = computeTrueHeading,
            navigationTarget = target,
            returnRoute = returnRoute,
            routeLine = routeLine,
            onRetryRoute = onRetryRoute,
            waypointNavigate = waypointNavigateControlsFor(
                navigatingToWaypoint, nextWaypointLine, onNavigateToWaypoint, startWaypointNavigation,
                mapWaypointReopen, recordsWaypointReopen, screenState,
            ),
            showDecimalDegrees = showDecimalDegrees,
            onToggleCoordinateFormat = { showDecimalDegrees = !showDecimalDegrees },
            navigationFollowing = !navigationFollowBroken,
            navigationViewRequestId = navigationViewRequestId,
            onLeftNavigationView = { navigationFollowBroken = true },
            onReturnToRoute = {
                navigationFollowBroken = false
                navigationViewRequestId++
            },
            navigationZoomPending = navigationZoomPending,
            onNavigationZoomApplied = { navigationZoomPending = false },
            basemap = basemap,
            onLocateMe = onLocateMe,
            onMapFullscreenChanged = onMapFullscreenChanged,
            mapBubbleSources = mapBubbleSources,
            onMapModeChange = {
                chosenMapMode = it
                onMapModeSelected(it)
            },
            // Dispatch -502: the return control and the HUD's X end a waypoint navigation first, while there is one.
            onToggleReturning = onNavigationControl,
            onClearMapTaxonFilter = onClearMapTaxonFilter,
        ),
        onFindReportClosed = onFindReportClosed,
        onEntryClosed = onEntryClosed,
        onFindDeleted = onFindDeleted,
        onFindEditStarted = mapReturnMemory::forget,
        mapMode = { mapMode },
        mapTaxonFilter = { mapTaxonFilter },
        leaveLogEntryEditingOfferingDiscard = leaveLogEntryEditingOfferingDiscard,
        onIsDrawerOpenChange = { isDrawerOpen = it },
        onIsMapFullscreenChange = { isMapFullscreen = it },
        onCompactTabChange = { compactTab = it },
        onSelectedTabChange = { selectedTab = it },
        onPendingJournalDestinationChange = {
            pendingJournalDestination = it
            if (it == null) {
                pendingJournalFindId = null
                pendingJournalEntryId = null
                pendingJournalTrackId = null
            }
        },
        pendingJournalFindId = { pendingJournalFindId },
        pendingJournalEntryId = { pendingJournalEntryId },
        onViewSpeciesOnMap = onViewSpeciesOnMap,
        onOpenCamera = onOpenCamera,
        onLogPhotoAcquisitionInFlightChange = { logPhotoAcquisitionInFlight = it },
        journalScreenState = journalScreenState,
        cartographyEntryModeState = cartographyEntryModeState,
        cartographyOpenEntryGroupsState = cartographyOpenEntryGroupsState,
        findEntryModeState = findEntryModeState,
        findOverViewState = findOverViewState,
        // Phase 2.6 B: the screen's own bundles, passed straight down (the frame unpacks them).
        findsLog = findsLog,
        journalEntries = journalEntries,
        entryReportData = entryReportData,
        offlineMaps = offlineMaps,
        records = records,
        // RECORD -1525 (the owner: "Clear removes it too"): the search bar's Clear also drops the map's "View on Map" chip,
        // first, then the ViewModel's clear as before. Both Clear controls (portrait header, landscape map bar) take it from here.
        searchInputs = searchInputs.copy(onClearSearch = { onClearMapTaxonFilter(); searchInputs.onClearSearch() }),
        recording = recording,
    )
}
