package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.adaptive.mainWindowSearchEdgeFor
import com.zynergylabs.forager.app.ui.adaptive.mainWindowRailEdgeFor
import com.zynergylabs.forager.app.domain.WaypointNavigationTarget
import com.zynergylabs.forager.app.domain.waypointNavigationTarget
import com.zynergylabs.forager.app.ui.map.ReferenceTrailsShown
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOriginSaver
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.domain.GetJournalEntryHighlightsUseCase
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.zynergylabs.forager.app.crash.CrashFileStore
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.sensor.AndroidCompassProvider
import com.zynergylabs.forager.app.sensor.AndroidDeclinationProvider
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.zynergylabs.forager.app.ui.adaptive.isLandscapeWindow
import com.zynergylabs.forager.app.ui.crash.CrashLogPanel
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import com.zynergylabs.forager.app.importgpx.gpxImportMessage
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.map.SightingsMapSlot
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapReturnMemory

/**
 * Map-first layout: the results (map or ranked list) own the content area, and everything set far
 * less than once per search — location, radius, month, the foraging-areas layer, and trip
 * planning — lives in a navigation drawer behind the app bar's tune icon, as two independently
 * collapsible sections; see [SearchControls]. Species/category, the one control used on nearly
 * every search, lives in the top bar itself; see [SearchEntryBar], which replaced the old
 * `AvailabilitySearchTopBar` (dispatch 2026-09-28-658, scout item F4: this linked a composable that
 * no longer exists).
 *
 * **Why the rest is still in a drawer.** The controls used to be stacked above the results in one
 * unscrolled [Column]. A Column measures its non-weighted children in order against the height
 * still unclaimed, so the eight-odd wrap-content controls took the viewport and whatever came
 * after them — the tab row, the conditions card, the map — was measured against what was left,
 * which on a ~780dp-tall screen is zero. Nothing scrolled, because the Column had no
 * `verticalScroll`, so the starved children were simply unreachable. Making that Column
 * scrollable was the other option and was rejected: a scrollable parent passes an infinite
 * height constraint down, which a map cannot be measured against at all, so the map would still
 * have needed a hard-coded height and would still not have been the primary thing on screen.
 *
 * The drawer is what makes the real fix possible: with most controls gone, the content area has
 * a small, bounded set of wrap-content siblings above the results (the top bar, the summary
 * strip, the tab row), and the results take the rest via [Modifier.weight], which is a definite
 * bounded height rather than a remainder.
 *
 * **The app bar is the one exception**, and the one place a change here can still reintroduce the
 * squeeze this file's whole layout exists to avoid. The fixed two-row bar this paragraph was
 * written about, `AvailabilitySearchTopBar`, is gone; [SearchEntryBar] pins its field to a short,
 * fixed height for the same reason (see its doc comment), and [AvailabilityScreenLayoutTest] holds
 * the measurement that verifies the squeeze has not come back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    uiState: AvailabilityUiState,
    /** Search and trip callbacks (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenSearchInputs]. Required: 15 of its 16 fields were required parameters here and stay required. */
    searchInputs: AvailabilityScreenSearchInputs,
    /**
     * Where "now" comes from for the relative times this screen renders — the offline banner's
     * "saved 3 hours ago" and the recent-searches picker's "cached 2 days ago".
     *
     * Injected rather than read off [System.currentTimeMillis] at the two call sites, and it is
     * the reason [CurrentTimeProvider] exists: a Robolectric test asserts the banner's actual
     * rendered text is on screen, and text derived from the real wall clock is not something a
     * test can name. Defaults to the real clock the same way [mapSlot] defaults to the real map,
     * so this stays optional for callers that render neither.
     */
    currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    /** Offline maps' callbacks (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenOfflineMaps]. Required: 7 of its 8 fields were required parameters here and stay required. */
    offlineMaps: AvailabilityScreenOfflineMaps,
    /** Settings' callbacks and the settings-reset notice (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenSettings]. Required: 2 of its 10 fields were required parameters here and stay required. */
    settings: AvailabilityScreenSettings,
    /** Settings' Backup section, the backup and restore requests, and the GPX import's notice and picker (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenBackupAndImport]. Defaulted, as its six fields were when they were parameters here. */
    backupAndImport: AvailabilityScreenBackupAndImport = AvailabilityScreenBackupAndImport(),
    /** The Maps tab's controls: fullscreen, locate, the Layers sheet, basemap and icon cluster (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenMapControls]. Defaulted, as its nine fields were when they were parameters here. */
    mapControls: AvailabilityScreenMapControls = AvailabilityScreenMapControls(),
    /** The finds log's state and callbacks (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenFindsLog]. Defaulted, as its 19 fields were when they were parameters here. */
    findsLog: AvailabilityScreenFindsLog = AvailabilityScreenFindsLog(),
    cameraCaptureFiles: CameraCaptureFiles = CameraCaptureFiles(LocalContext.current),
    /** The in-app camera's state and callbacks (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenCamera]. Defaulted, as its eight fields were when they were parameters here. */
    cameraInputs: AvailabilityScreenCamera = AvailabilityScreenCamera(),
    /** Journal entries' state and callbacks (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenJournalEntries]. Defaulted, as its 22 fields were when they were parameters here. */
    journalEntries: AvailabilityScreenJournalEntries = AvailabilityScreenJournalEntries(),
    /** The Journal's entry report and cards' readers (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenEntryReportData]. Defaulted, as its four fields were when they were parameters here. */
    entryReportData: AvailabilityScreenEntryReportData = AvailabilityScreenEntryReportData(),
    /** Track recording inputs (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenRecording]. Defaulted, as its 11 fields were when they were parameters here. */
    recording: AvailabilityScreenRecording = AvailabilityScreenRecording(),
    /** Saved waypoints, tracks and pending-delete notices (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenRecords]. Defaulted, as its 12 fields were when they were parameters here. */
    records: AvailabilityScreenRecords = AvailabilityScreenRecords(),
    /** Return and waypoint navigation inputs (Phase 2.1, dispatch 2026-10-19-28): see [AvailabilityScreenNavigation]. Defaulted, as its 11 fields were when they were parameters here. */
    navigation: AvailabilityScreenNavigation = AvailabilityScreenNavigation(),
    /**
     * What reads the device compass for the compact map's top strip. Defaults to the real sensor,
     * so no production caller passes it — same [mapSlot]/[cameraCaptureFiles] pattern below.
     */
    compassProvider: CompassProvider = AndroidCompassProvider(LocalContext.current),
    /**
     * Navigation HUD stage one: what turns [compassProvider]'s magnetic heading into a true one
     * (see [rememberTrueHeading]). Defaults to the real declination model so no production caller
     * *has* to pass it, same pattern as [compassProvider]; `MainActivity` passes the container's
     * own instance, and a test passes one over a fake [com.zynergylabs.forager.app.domain.DeclinationProvider]
     * to pin the sign.
     */
    computeTrueHeading: ComputeTrueHeadingUseCase = remember { ComputeTrueHeadingUseCase(AndroidDeclinationProvider()) },
    /**
     * What fills the map's box. Defaults to the real map, so no production caller passes it; see
     * [MapSlot] for why the map is reached through a slot rather than named directly here.
     */
    mapSlot: MapSlot = SightingsMapSlot,
    /**
     * The Settings tab's crash-log diagnostic surface — see [CrashLogPanel]. Defaults to the real
     * on-device store, same [mapSlot]/[cameraCaptureFiles]/[compassProvider] pattern above.
     */
    crashFileStore: CrashFileStore = CrashFileStore.forContext(LocalContext.current),
) {
    // Phase 2.6 B (dispatch 2026-10-19-40): the unpacks below are the fields this body still reads; the frame unpacks the rest of
    // findsLog, journalEntries, records, recording and searchInputs, and all of entryReportData and offlineMaps, itself.
    // Dispatch 2026-10-26-17: the fields only the frame's call or the in-app camera read moved with them (AvailabilityScreenFrameCall.kt, AvailabilityScreenInAppCamera.kt).
    // Phase 2.1 (dispatch 2026-10-19-28): the backup and import bundle, unpacked under the parameter names it replaced.
    val backup = backupAndImport.backup
    val returnToMapRequest = backupAndImport.returnToMapRequest
    val openBackupRequest = backupAndImport.openBackupRequest
    val gpxImportNotice = backupAndImport.gpxImportNotice
    val onGpxImportNoticeShown = backupAndImport.onGpxImportNoticeShown
    // Phase 2.1 (dispatch 2026-10-19-28): the map controls bundle, unpacked under the parameter names it replaced.
    val onMapFullscreenChanged = mapControls.onMapFullscreenChanged
    val onMapShown = mapControls.onMapShown
    val onMapsTabShownChanged = mapControls.onMapsTabShownChanged
    val onMapLayerVisibilityChanged = mapControls.onMapLayerVisibilityChanged
    val onMapLayerOpacityChanged = mapControls.onMapLayerOpacityChanged
    val onColourFieldMoved = mapControls.onColourFieldMoved
    val forecastCellStore = mapControls.forecastCellStore
    val onMapIconClusterPlacementChanged = mapControls.onMapIconClusterPlacementChanged
    // Phase 2.1 (dispatch 2026-10-19-28): the findsLog bundle, unpacked under the parameter names it replaced.
    val logUiState = findsLog.logUiState
    val onOpenLogEntry = findsLog.onOpenLogEntry
    val onLeaveLogEntryEditingIncidentally = findsLog.onLeaveLogEntryEditingIncidentally
    val onDiscardLogDraft = findsLog.onDiscardLogDraft
    // Phase 2.1 (dispatch 2026-10-19-28): the journalEntries bundle, unpacked under the parameter names it replaced.
    val cartographyUiState = journalEntries.cartographyUiState
    val onSetCartographyEntryShownOnMap = journalEntries.onSetCartographyEntryShownOnMap
    val onCartographyShownOnMapErrorDismissed = journalEntries.onCartographyShownOnMapErrorDismissed
    // Phase 2.1 (dispatch 2026-10-19-28): the records bundle, unpacked under the parameter names it replaced.
    val waypoints = records.waypoints
    val waypointEntryReferenceCounts = records.waypointEntryReferenceCounts
    val waypointsLoaded = records.waypointsLoaded
    val tracks = records.tracks
    val getFullRecord = records.getFullRecord
    val pendingDeleteNotices = records.pendingDeleteNotices
    // Phase 2.1 (dispatch 2026-10-19-28): the navigation bundle, unpacked under the parameter names it replaced.
    val isReturning = navigation.isReturning
    val isArrivalHeld = navigation.isArrivalHeld
    val onToggleReturning = navigation.onToggleReturning
    val navigationTarget = navigation.navigationTarget
    val onNavigateToWaypoint = navigation.onNavigateToWaypoint
    val onStopWaypointNavigation = navigation.onStopWaypointNavigation
    // Phase 2.1 (dispatch 2026-10-19-28): the recording bundle, unpacked under the parameter names it replaced.
    val tripStartWarning = recording.tripStartWarning
    val networkFixesNotice = recording.networkFixesNotice
    val backgroundRunPrompt = recording.backgroundRunPrompt
    // Phase 2.1 (dispatch 2026-10-19-28): the settings bundle, unpacked under the parameter names it replaced.
    val onNightModeMapsChanged = settings.onNightModeMapsChanged
    val settingsResetNoticePending = settings.settingsResetNoticePending
    val onSettingsResetNoticeShown = settings.onSettingsResetNoticeShown
    val onThemeModeChanged = settings.onThemeModeChanged
    val onDistanceUnitSelected = settings.onDistanceUnitSelected
    // Phase 2.1 (dispatch 2026-10-19-28): the searchInputs bundle, unpacked under the parameter names it replaced.
    val onMapTabSelected = searchInputs.onMapTabSelected
    val onSeasonalTabSelected = searchInputs.onSeasonalTabSelected
    val onReopenTaxonSuggestions = searchInputs.onReopenTaxonSuggestions
    val onDeletePlannedTrip = searchInputs.onDeletePlannedTrip

    // The tab, the Journal's places and open entry modes, the photo round-trip flag and the map's species filter, held above
    // the tabs: see rememberScreenTabSelections (ScreenTabSelections.kt), whose comments say why each is held here.
    val tabSelections = rememberScreenTabSelections()
    var selectedTab by tabSelections::selectedTab
    var compactTab by tabSelections::compactTab
    val journalScreenState = tabSelections.journalScreenState
    var mapTaxonFilter by tabSelections::mapTaxonFilter

    // Sets the filter and jumps to the Maps tab — both selectedTab and compactTab are updated; see
    // CompactTab's own doc comment for why the two are kept as separate state instead of one.
    val onViewSpeciesOnMap: (Long) -> Unit = { taxonId ->
        mapTaxonFilter = taxonId
        compactTab = CompactTab.MAP
        selectedTab = ResultsTab.MAP
    }

    val onClearMapTaxonFilter: () -> Unit = {
        mapTaxonFilter = null
    }

    // THE navigation predicate — defined once, here, and nowhere else (navigation-chrome dispatch,
    // item 1). Everything that means "a navigation mode is active" reads this: the HUD's presence,
    // the compass strip's absence, and which waypoints the map shows. Stage one has exactly one
    // mode, the return leg, so this is isReturning; stage two's target picker ORs its own state
    // into this one line, and the strip, the HUD and the map cannot drift apart because none of
    // them was ever written against isReturning directly.
    //
    // Dispatch 2026-09-28-502 (plan tasks T8 and T9) is that second mode: navigating to a chosen waypoint,
    // with or without a recording. The waypoint is the AvailabilityViewModel's (uiState.waypointNavigation),
    // resolved here against the waypoints the app has loaded; while it is found it is the target, and it
    // overrules the return, which the ViewModel paused when it began. Before the waypoints load it is
    // waited for; once loaded and missing, it was deleted, and the navigation ends with a message (below).
    val waypointTarget = waypointNavigationTarget(uiState.waypointNavigation, waypoints, waypointsLoaded)
    val navigatingToWaypoint = (waypointTarget as? WaypointNavigationTarget.Found)?.waypoint
    val isNavigating = isReturning || isArrivalHeld || navigatingToWaypoint != null
    val target = navigatingToWaypoint ?: navigationTarget
    // Navigation HUD stage one's display rules, applied once here so the map and the
    // Records list agree — see mapVisibleWaypoints. Records keeps the full list.
    val mapWaypoints = remember(waypoints, isNavigating, target) {
        mapVisibleWaypoints(waypoints, isNavigating = isNavigating, target = target)
    }
    // Dispatch -502, Amendment 1 ("Back to where you tapped Navigate"): where Navigate was tapped, so one Back ends the
    // waypoint navigation and puts the walker back at that step. Saved with the screen, so it outlives a tab change, a
    // recreation and the phone closing and restoring the app; a swipe-away loses it, and Back then ends the navigation on
    // the map (the planner accepted). Cleared wherever the navigation ends: Back, the X-circle or the HUD's X, a deletion.
    var waypointNavigationOrigin by rememberSaveable(stateSaver = WaypointNavigationOriginSaver) { mutableStateOf<WaypointNavigationOrigin?>(null) }
    // The step Back opens again, once, on the tab it switched to: a bubble or sheet on the Maps tab, or the sheet in Records.
    val mapWaypointReopenState = remember { mutableStateOf<WaypointNavigationOrigin?>(null) }
    var mapWaypointReopen by mapWaypointReopenState
    val recordsWaypointReopenState = remember { mutableStateOf<String?>(null) }
    var recordsWaypointReopen by recordsWaypointReopenState

    // The fullscreen flag and its one-time restore across restarts: see rememberMapFullscreen (ScreenMapFullscreen.kt),
    // whose comments say why it is written only at the user's mutation sites.
    val mapFullscreen = rememberMapFullscreen(uiState)
    var isMapFullscreen by mapFullscreen::isMapFullscreen
    // The icon cluster's position, persisted across restarts (RECORD -711): see rememberPersistedMapIconClusterPosition
    // (ScreenMapIconClusterPlacement.kt).
    val mapIconClusterPosition = rememberPersistedMapIconClusterPosition(uiState, onMapIconClusterPlacementChanged)
    // Part 1 layout fixes, item 4 (planner message 2026-09-28-98, under CLAUDE.md's UX defaults): the
    // camera the user left on the Maps tab, held here for the same reason, since the map and its camera
    // leave composition with the tab. See MapCameraMemory. Session only.
    val mapCameraMemory = remember { MapCameraMemory() }
    // Dispatch 2026-09-29-57, item 8 (amendments -256 and -262): the return request "Open in Journal" leaves, held here for the
    // same reason as the camera. Session only, like the camera: a recreation forgets it and Back does what it did before.
    val mapReturnMemory = remember { MapReturnMemory() }

    // The Maps tab's basemap, one MapMode value (see MapMode's own doc comment for the two-piece
    // service+mode split this replaced). Persisted across restarts since dispatch 2026-09-28-708 (the
    // owner: "have the app remember which map modes you had it on last so we don't have to keep
    // switching to the favorite"): uiState.mapMode is the stored choice, restored by the ViewModel
    // and updated by onMapModeSelected. This comment used to argue the opposite, that the basemap was
    // display-only state the ViewModel had no part in and that persisting it would be speculative;
    // the owner's request is the real need that argument was waiting for.
    //
    // chosenMapMode is the pick made on this screen, applied here as well as sent up. In the app the
    // two agree from the pick on; it is kept so a host that does not wire onMapModeSelected (the test
    // hosts that drive this screen without a ViewModel) still has a picker that changes the map, as
    // every host had before the choice persisted. Until either is known the map is not drawn at all
    // (the gate on mapRenderMode below), so DEFAULT here is never what a restored user sees first.
    val chosenMapModeState = remember { mutableStateOf<MapMode?>(null) }
    var chosenMapMode by chosenMapModeState
    val mapMode = chosenMapMode ?: uiState.mapMode ?: MapMode.DEFAULT
    val basemap = mapMode.basemap

    // Night mode: Settings' "Night Maps" checkbox, a direct persistent preference
    // (uiState.nightModeMaps, backed by MapPreferencesRepository.getNightModeMaps/setNightModeMaps)
    // rather than derived from time of day. Replaces this map's earlier civil-twilight-automatic/
    // long-press-hold control (MapNightMode) per the project owner's own request to move night
    // mode to a plain Settings checkbox instead.
    val isNightMode = uiState.nightModeMaps
    // nightModeLoaded: the cold-launch gate (colour build C1) — the map loads no style until the
    // preference read above has landed, so a night user's first style is the night one. Only this
    // map gets it; see MapRenderMode.nightModeLoaded for why the others keep the default.
    // Map layers L0b (B1 to B5). The colour fields with data this week (the store's answer, from
    // onMapShown), the state the map draws with (the user's choices with every colour field that has
    // no data hidden, so a release build never draws, lists, credits or legends one: planner's ruling
    // on F2), the forecast feed the map reads its cells through, and every saved record with the
    // pending deletes left out (planner's ruling on Q7). The legend's expanded flag is held here, above
    // the tab, so it survives leaving the Maps tab and coming back (CLAUDE.md, UX defaults).
    var mapLegendExpanded by rememberSaveable { mutableStateOf(false) }
    // Dispatch 2026-09-28-422 (plan task T18): the coordinate format, MGRS or decimal degrees, held
    // here above the tab like the legend's flag, so a format chosen on the Maps tab is the format it
    // shows after leaving the tab and coming back (CLAUDE.md, UX defaults). It was the Maps tab's own
    // `remember`, which leaves composition with the tab, so it reset. Held for the session only,
    // not across a restart (not asked for).
    val showDecimalDegreesState = rememberSaveable { mutableStateOf(false) }
    // Dispatch 2026-09-28-677 (data part B): whether the track sheet's "Details" fold is open, held here above every place
    // that sheet opens (Records, a map bubble's Details, an entry report's track row), so it survives within a session
    // (CLAUDE.md, UX defaults). One flag for every track's sheet. Not across a restart (not asked for).
    val trackDetailsFoldOpen = rememberSaveable { mutableStateOf(false) }
    // Dispatch 2026-10-28-02: whether Tools > Map Layers is open, held here as the Details fold above is, so it stays open
    // across closing the drawer, changing tab and a trip into Settings (CLAUDE.md, UX defaults). Folded the first time; the
    // dispatch: "It is not persisted across restarts."
    val mapLayersFoldOpen = rememberSaveable { mutableStateOf(false) }
    // Amendment 3 (RECORD -1770, N3; the owner: "Yes, same as Map Layers (Recommended)"): Trip Planner's fold, held the same way.
    val tripPlannerFoldOpen = rememberSaveable { mutableStateOf(false) }
    // The navigation view's follow and zoom requests, held above the tab (T22, -440): see rememberNavigationViewRequests
    // (ScreenNavigationViewRequests.kt).
    val navigationViewRequests = rememberNavigationViewRequests(isNavigating)
    var navigationFollowBroken by navigationViewRequests::navigationFollowBroken
    var navigationViewRequestId by navigationViewRequests::navigationViewRequestId
    // Dispatch -502: the dashed line to the waypoint, worked out and held by the ViewModel since step 6 PR 1 (dispatch
    // 2026-10-26-06; see AvailabilityWaypointLine), so it outlives a tab change. Drawn only while the target is found here.
    val nextWaypointLine = navigatingToWaypoint?.let { uiState.waypointStraightLine }
    // The forecast cells, the colour fields with data and the layers the map draws: see rememberMapForecastFeed
    // (ScreenMapForecastFeed.kt).
    val mapForecast = rememberMapForecastFeed(uiState, forecastCellStore)
    val forecastCellsShown by mapForecast::forecastCellsShown
    val forecastZoomedOut by mapForecast::forecastZoomedOut
    val availableColourFieldGroups = mapForecast.availableColourFieldGroups
    val drawnMapLayers = mapForecast.drawnMapLayers
    val forecastFeed = mapForecast.forecastFeed
    val mapRecordsDrawn = uiState.drawnMapRecords(logUiState)
    // J8-2 (owner: "Highlight in place", "Live records", "Saved entries only"): the saved entries shown
    // on the map and the records they keep, among the records the Maps tab draws. cartographyUiState is
    // what MainActivity passes, with a pending entry delete already left out (hidingPendingDelete), and
    // its entries are the saved ones; the use case holds the draft rule itself as well. Only the Maps
    // tab reads this: every other map draws no highlight. The waypoints are the ones this map draws,
    // mapWaypoints, not every waypoint (J8 follow-ups, continuation 2026-09-28-87, item 2): an ORIGIN
    // waypoint the map is not navigating to, and any END waypoint, is not drawn, so it gets no ring,
    // by J8's rule that a record not drawn is not highlighted.
    val journalHighlights = remember(cartographyUiState.entries, mapRecordsDrawn, mapWaypoints) {
        GetJournalEntryHighlightsUseCase()(cartographyUiState.entries, mapRecordsDrawn, mapWaypoints)
    }
    // Persisted via the ViewModel/DataStore — see AvailabilityUiState.distanceUnit's own doc
    // comment. mapMode above is persisted the same way since dispatch 2026-09-28-708.
    val distanceUnit = uiState.distanceUnit
    // The Tools drawer's state: see rememberScreenToolsDrawer (ScreenToolsDrawer.kt), whose comments say why isDrawerOpen, not
    // the drawer's own state, is the authority, and why the focus hand-back is guarded.
    val toolsDrawer = rememberScreenToolsDrawer()
    val drawerState = toolsDrawer.drawerState
    val context = LocalContext.current
    var isDrawerOpen by toolsDrawer::isDrawerOpen
    var openSettingsOnlyRequest by toolsDrawer::openSettingsOnlyRequest

    // The one-shot request into JournalTab and the ids that ride with it: see rememberPendingJournalRequests
    // (ScreenPendingJournalRequests.kt).
    val pendingJournalRequests = rememberPendingJournalRequests()
    var pendingJournalDestination by pendingJournalRequests::pendingJournalDestination
    var pendingJournalFindId by pendingJournalRequests::pendingJournalFindId
    var pendingJournalEntryId by pendingJournalRequests::pendingJournalEntryId
    var pendingJournalTrackId by pendingJournalRequests::pendingJournalTrackId
    // The effect that drives the animated drawer from isDrawerOpen (ScreenToolsDrawer.kt), keyed on the flag read here.
    ToolsDrawerFollowsFlag(isDrawerOpen = isDrawerOpen, toolsDrawer = toolsDrawer)

    // Tapping Done on the restore's loading page goes home (dispatch 2026-09-28-137, item 6): the Maps tab, the drawer
    // closed. The same two writes as "View on Map" above (onViewSpeciesOnMap): both tab states are set unconditionally,
    // since only the one the active layout reads has any effect. Keyed on the request's count, so a tab the person is
    // already on is set again harmlessly and 0, the default, does nothing.
    // A backup notification's tap (dispatch 2026-09-28-153): open Tools, then Settings, at the Backup section. The compact
    // drawer opens over whatever tab is showing.
    LaunchedEffect(openBackupRequest) {
        if (openBackupRequest > 0) {
            isDrawerOpen = true
        }
    }

    // Plan T16: a finished GPX import. Journal > Records > Tracks, the first new track's details open, and
    // the outcome's message (the owner's words for a failure, "Imported N tracks" for several) as a Toast,
    // the way the Journal says its own failures. The Tools drawer closes and fullscreen ends, as leaving the
    // Maps tab ends it (the tab bar's own rule), so the Journal is what shows. A failed import lands on the
    // Tracks list too, where "Import GPX" is.
    LaunchedEffect(gpxImportNotice?.seq) {
        val notice = gpxImportNotice ?: return@LaunchedEffect
        gpxImportMessage(notice.outcome)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        isDrawerOpen = false
        if (isMapFullscreen) {
            isMapFullscreen = false
            onMapFullscreenChanged(false)
        }
        pendingJournalTrackId = (notice.outcome as? GpxImportOutcome.Imported)?.trackIds?.firstOrNull()
        pendingJournalDestination = PendingJournalDestination.VIEW_IMPORTED_TRACK
        compactTab = CompactTab.JOURNAL
        onGpxImportNoticeShown(notice.seq)
    }

    // Where the Tools drawer's "Offline maps" row was tapped, so Back can retrace the way in (RECORD -868 and -871): see
    // rememberToolsOfflineMapsReturn (ScreenToolsOfflineMapsReturn.kt).
    val toolsOfflineMapsReturn = rememberToolsOfflineMapsReturn()
    var toolsReturn by toolsOfflineMapsReturn::toolsReturn
    var toolsReturnArrived by toolsOfflineMapsReturn::toolsReturnArrived
    // Phase 2.6, PR 1 (dispatch 2026-10-19-40): the holder the moved code reaches the body's state through (see
    // AvailabilityScreenState); it is not remembered. Built here, below toolsReturn and toolsReturnArrived, because the
    // journal-return code (Phase 2.6 PR 3) will reach those through it and a local can't be read above its declaration.
    val screenState = AvailabilityScreenState(
        compactTab = { compactTab },
        setCompactTab = { compactTab = it },
        selectedTab = { selectedTab },
        setSelectedTab = { selectedTab = it },
        journalScreenState = journalScreenState,
        waypointNavigationOrigin = { waypointNavigationOrigin },
        setWaypointNavigationOrigin = { waypointNavigationOrigin = it },
        setMapWaypointReopen = { mapWaypointReopen = it },
        setRecordsWaypointReopen = { recordsWaypointReopen = it },
        navigationFollowBroken = { navigationFollowBroken },
        setNavigationFollowBroken = { navigationFollowBroken = it },
        navigationViewRequestId = { navigationViewRequestId },
        setNavigationViewRequestId = { navigationViewRequestId = it },
        forecastCellsShown = { forecastCellsShown },
        forecastZoomedOut = { forecastZoomedOut },
        mapLegendExpanded = { mapLegendExpanded },
        setMapLegendExpanded = { mapLegendExpanded = it },
        chosenMapMode = { chosenMapMode },
        setPendingJournalDestination = { pendingJournalDestination = it },
        setPendingJournalFindId = { pendingJournalFindId = it },
        setPendingJournalEntryId = { pendingJournalEntryId = it },
        isMapFullscreen = { isMapFullscreen },
        setIsMapFullscreen = { isMapFullscreen = it },
        setIsDrawerOpen = { isDrawerOpen = it },
        toolsReturn = { toolsReturn },
        setToolsReturn = { toolsReturn = it },
        setToolsReturnArrived = { toolsReturnArrived = it },
        isDrawerOpen = { isDrawerOpen },
        openSettingsOnlyRequest = { openSettingsOnlyRequest },
        setOpenSettingsOnlyRequest = { openSettingsOnlyRequest = it },
    )
    // Dispatch -502: starting and ending a waypoint navigation, and the return control (AvailabilityScreenWaypointNavigation.kt).
    // Built here, once the holder is; every reader of the three is below this line.
    val waypointNavigation = waypointNavigationActions(
        state = screenState,
        isNavigating = isNavigating,
        navigatingToWaypoint = navigatingToWaypoint,
        onNavigateToWaypoint = onNavigateToWaypoint,
        onStopWaypointNavigation = onStopWaypointNavigation,
        onToggleReturning = onToggleReturning,
    )
    val startWaypointNavigation = waypointNavigation.start
    val endWaypointNavigation = waypointNavigation.end
    // Phase 2.6, PR 2: the Layers sheet's inputs and how the Maps tab's map is drawn (AvailabilityScreenMapInputs.kt), built
    // here, once the holder is; the map reads both only in the frame's call below.
    // Step 8b: whether the Maps tab's map last reported its loaded style draws the official-trails band, for the sheet's row.
    val referenceTrails = remember { ReferenceTrailsShown() }
    val mapLayersControls = availabilityMapLayersControls(
        state = screenState,
        referenceTrailsShown = referenceTrails.shown,
        uiState = uiState,
        availableColourFieldGroups = availableColourFieldGroups,
        mapRecordsDrawn = mapRecordsDrawn,
        journalHighlights = journalHighlights,
        onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
        onMapLayerVisibilityChanged = onMapLayerVisibilityChanged,
        onMapLayerOpacityChanged = onMapLayerOpacityChanged,
        onColourFieldMoved = onColourFieldMoved,
    )
    val mapRenderMode = availabilityMapRenderMode(
        state = screenState,
        uiState = uiState,
        basemap = basemap,
        isNightMode = isNightMode,
        drawnMapLayers = drawnMapLayers,
        forecastFeed = forecastFeed,
        onReferenceTrailsShown = referenceTrails.onShown,
    )
    // Phase 2.6, PR 3: the Tools drawer's "Offline maps" row, built in AvailabilityScreenJournalReturn.kt.
    val openOfflineMapsFromTools = buildOpenOfflineMapsFromTools(state = screenState, onMapFullscreenChanged = onMapFullscreenChanged)
    // Forgets where the row was tapped once the user moves on from the Offline maps chip (ScreenToolsOfflineMapsReturn.kt).
    ToolsOfflineMapsReturnWatch(toolsReturnSet = toolsReturn != null, toolsOfflineMapsReturn = toolsOfflineMapsReturn, tabSelections = tabSelections)

    LaunchedEffect(returnToMapRequest) {
        if (returnToMapRequest > 0) {
            isDrawerOpen = false
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
        }
    }

    // uiState.searchSerial (RECORD -723): a search run again with the same region, month and filter clears the
    // sightings too, and must fetch them again; see that field's doc comment.
    LaunchedEffect(selectedTab, uiState.region, uiState.selectedMonth, uiState.taxonFilter, uiState.searchSerial) {
        if (selectedTab == ResultsTab.MAP) onMapTabSelected()
        if (selectedTab == ResultsTab.SEASONAL) onSeasonalTabSelected()
    }

    // Phase 2.6, E2 (RECORD -1484): what Back does, the seven handlers in AvailabilityScreenBackHandlers.kt (their comments, the
    // Back order and why it matters, moved with them), composed here, unconditionally, where they were. It hands back the
    // exit-navigation prompt's state, which it remembers at its old position; the prompt below reads and writes it.
    var showExitNavigationPrompt by AvailabilityScreenBackHandlers(
        state = screenState,
        isNavigating = isNavigating,
        navigatingToWaypoint = navigatingToWaypoint,
        endWaypointNavigation = endWaypointNavigation,
        onMapFullscreenChanged = onMapFullscreenChanged,
        context = context,
    )

    // Tablets as a big phone (dispatch 2026-09-28-245, amended by -246): this tree is drawn at every
    // window size, and a window that is in landscape, tall or short, gets the phone's landscape
    // layout: a navigation rail on the charger-port edge in place of the bottom bar (see
    // compactMainScaffold's own showRail), and the L. portEdge is only read while showRail is true.
    val isLandscapeWindow = isLandscapeWindow()
    // Map layers L0b, B2: the Maps tab's freshness mechanism. Whenever the Maps tab comes into view
    // (the Maps bottom-nav tab or rail item) the saved records reload and the forecast store is
    // asked again, so a find saved on the Journal tab shows the next time the map is shown.
    val isMapsTabShown = compactTab == CompactTab.MAP
    // Dispatch 2026-10-28-13, Amendment 3: the same edge, both ways, for the barometer. Inside this effect rather than a
    // new one, so no composition slot after it moves.
    LaunchedEffect(isMapsTabShown) {
        if (isMapsTabShown) onMapShown()
        onMapsTabShownChanged(isMapsTabShown)
    }
    // Map chrome at 80% (dispatch 2026-09-28-56 as amended by -58; planner message -77, Q4): raised
    // only on the Maps tab, and follows the tab.
    if (showExitNavigationPrompt) {
        ExitNavigationPrompt(
            onExit = {
                showExitNavigationPrompt = false
                onToggleReturning()
            },
            onKeepNavigating = { showExitNavigationPrompt = false },
            overMap = compactTab == CompactTab.MAP,
        )
    }
    // RECORD -956 ("One should look exactly line the other"): the rail's edge and the search bar's edge are the screen's right and
    // left in landscape, whatever the rotation (mainWindowRailEdgeFor). The names stay portEdge and punchHoleEdge because every
    // site below reads them; they no longer follow the charging port or the camera. Was currentWindowPortEdge() and
    // punchHoleEdgeFor(rotation), which mirrored the screen at ROTATION_270.
    val mainWindowLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val portEdge = mainWindowRailEdgeFor(mainWindowLandscape)
    // Landscape B2 (S1): where the search bar, its filter chip and the icon cluster's default side go: the screen's left (-956).
    val punchHoleEdge = mainWindowSearchEdgeFor(mainWindowLandscape)

    // Workstream L4b-R2: the one wrapped "leaving without answering" callback, hoisted here (rather
    // than declared separately inside compactMainScaffold) so "every in-app exit offers a discard action" stays one fact about one
    // callback shared by every caller — the bottom nav's JournalTab and its own tab-switch
    // handler — rather than N independently-maintained copies.
    // Backgrounding is the deliberate, sole exception: see compactMainScaffold's own
    // DisposableEffect, which calls the *raw* onLeaveLogEntryEditingIncidentally directly, never this
    // wrapper, since there is no window left to show a Snackbar in by the time that fires.
    val logDraftSnackbarHostState = remember { SnackbarHostState() }
    val logDraftSnackbarScope = rememberCoroutineScope()
    // Phase 2.6, D (RECORD -1484): the pop-up notices' triggers, in AvailabilityScreenNotices.kt, composed here, unconditionally,
    // where the first of them was; the two remembers above stay here and are passed in as themselves.
    AvailabilityScreenNotices(
        state = screenState,
        logDraftSnackbarHostState = logDraftSnackbarHostState,
        logDraftSnackbarScope = logDraftSnackbarScope,
        settingsResetNoticePending = settingsResetNoticePending,
        onSettingsResetNoticeShown = onSettingsResetNoticeShown,
        pendingDeleteNotices = pendingDeleteNotices,
        backup = backup,
        tripStartWarning = tripStartWarning,
        backgroundRunPrompt = backgroundRunPrompt,
        networkFixesNotice = networkFixesNotice,
        waypointTarget = waypointTarget,
        endWaypointNavigation = endWaypointNavigation,
    )
    // Intent 2026-09-28-44, F1 (the owner: "Snackbar only for real drafts (Recommended)"). This used
    // to offer "Saved to Drafts" for whatever entry was open, and its Discard deleted that id through
    // the general delete: after only viewing a committed find, Discard deleted the committed find,
    // with no Undo (docs/audits/2026-09-28-leaving-the-journal-investigation.md, Behaviour 1). Now the
    // snackbar is offered only when the leave keeps a draft (leaveKeepsDraft, the ViewModel's own rule
    // for what it keeps), and Discard deletes that id only while it is still in Drafts when tapped: a
    // new find's draft saved while the snackbar is still up is committed under the same id, and a
    // Discard then would delete it.
    val latestLogUiState by rememberUpdatedState(logUiState)
    // Phase 2.6, PR 3: built in AvailabilityScreenJournalReturn.kt; the remembered latest state above is read through a getter.
    val leaveLogEntryEditingOfferingDiscard = buildLeaveLogEntryEditingOfferingDiscard(
        logUiState = logUiState,
        latestLogUiState = { latestLogUiState },
        onLeaveLogEntryEditingIncidentally = onLeaveLogEntryEditingIncidentally,
        onDiscardLogDraft = onDiscardLogDraft,
        logDraftSnackbarScope = logDraftSnackbarScope,
        logDraftSnackbarHostState = logDraftSnackbarHostState,
    )
    // Item 8: leaving the find that was opened from the Maps tab with Back, its own arrow, or a delete returns to the Maps tab,
    // where the bubble (and the fan) reopen; any other way of leaving it (another bottom tab, an edit, another record) forgets
    // the origin, and Back does what it did before. The origin is forgotten on a tab change here; the edit and the other
    // record are forgotten where they happen (the callbacks below and `onOpenEntry`).
    LaunchedEffect(compactTab) {
        if (compactTab != CompactTab.JOURNAL) mapReturnMemory.forget()
    }
    // Phase 2.6, PR 3: the three returns to the Maps tab, built in AvailabilityScreenJournalReturn.kt (Part B's notes are there).
    val mapReturn = mapReturnCallbacks(state = screenState, mapReturnMemory = mapReturnMemory)
    // Declared after leaveLogEntryEditingOfferingDiscard, which its onOpenFind calls (F3).
    // Phase 2.6, PR 2: the Maps tab's bubbles' sources, built in AvailabilityScreenMapInputs.kt (its doc comment carries M1's notes).
    val mapBubbleSources = availabilityMapBubbleSources(
        state = screenState,
        uiState = uiState,
        logUiState = logUiState,
        waypoints = waypoints,
        waypointEntryReferenceCounts = waypointEntryReferenceCounts,
        tracks = tracks,
        currentTime = currentTime,
        getFullRecord = getFullRecord,
        drawnMapLayers = drawnMapLayers,
        journalHighlights = journalHighlights,
        leaveLogEntryEditingOfferingDiscard = leaveLogEntryEditingOfferingDiscard,
        onOpenLogEntry = onOpenLogEntry,
        onNavigateToWaypoint = onNavigateToWaypoint,
        startWaypointNavigation = startWaypointNavigation,
    )

    // J8, continuation 2026-09-28-65 (the owner: "Set it to "Changes not applied. Try again.""): a failed
    // Show or Hide on map, from the Journal's report or the Maps tab's chip, told on whichever tab is up,
    // as a Toast like the Journal's own save failures, then cleared.
    LaunchedEffect(cartographyUiState.shownOnMapErrorMessage) {
        cartographyUiState.shownOnMapErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onCartographyShownOnMapErrorDismissed()
        }
    }

    // The frame: its call, and the comment on what it is, in AvailabilityScreenFrameCall.kt. The drawer host composes this lambda.
    val compactMainScaffold: @Composable () -> Unit = {
        AvailabilityScreenFrameCall(
            tabSelections = tabSelections,
            toolsDrawer = toolsDrawer,
            toolsOfflineMapsReturn = toolsOfflineMapsReturn,
            pendingJournalRequests = pendingJournalRequests,
            mapFullscreen = mapFullscreen,
            navigationViewRequests = navigationViewRequests,
            chosenMapModeState = chosenMapModeState,
            mapWaypointReopenState = mapWaypointReopenState,
            recordsWaypointReopenState = recordsWaypointReopenState,
            showDecimalDegreesState = showDecimalDegreesState,
            screenState = screenState,
            isLandscapeWindow = isLandscapeWindow,
            portEdge = portEdge,
            punchHoleEdge = punchHoleEdge,
            logDraftSnackbarHostState = logDraftSnackbarHostState,
            uiState = uiState,
            distanceUnit = distanceUnit,
            currentTime = currentTime,
            mapSlot = mapSlot,
            mapIconClusterPosition = mapIconClusterPosition,
            mapCameraMemory = mapCameraMemory,
            mapReturnMemory = mapReturnMemory,
            mapRenderMode = mapRenderMode,
            mapLayersControls = mapLayersControls,
            isNightMode = isNightMode,
            mapWaypoints = mapWaypoints,
            isNavigating = isNavigating,
            compassProvider = compassProvider,
            computeTrueHeading = computeTrueHeading,
            target = target,
            navigatingToWaypoint = navigatingToWaypoint,
            nextWaypointLine = nextWaypointLine,
            waypointNavigation = waypointNavigation,
            basemap = basemap,
            mapBubbleSources = mapBubbleSources,
            onClearMapTaxonFilter = onClearMapTaxonFilter,
            mapReturn = mapReturn,
            mapMode = mapMode,
            leaveLogEntryEditingOfferingDiscard = leaveLogEntryEditingOfferingDiscard,
            onViewSpeciesOnMap = onViewSpeciesOnMap,
            navigation = navigation,
            mapControls = mapControls,
            backupAndImport = backupAndImport,
            cameraInputs = cameraInputs,
            recording = recording,
            settings = settings,
            findsLog = findsLog,
            journalEntries = journalEntries,
            entryReportData = entryReportData,
            offlineMaps = offlineMaps,
            records = records,
            searchInputs = searchInputs,
        )
    }


    // The phone tree at every window size (dispatch 2026-09-28-245): a tablet is a big phone, so the tree does not
    // depend on the width. Landscape windows get the phone's landscape layout (the rail, the L), see isLandscapeWindow.
    // The effect that lowers isDrawerOpen after a close the drawer made itself (Landscape B3, P12): see ScreenToolsDrawer.kt.
    ToolsDrawerFollowsSettledClose(toolsDrawer = toolsDrawer)
    // The drawer opens from the rail side in a landscape window (Landscape B3, P12, owner ruling 2): see toolsDrawerDirection.
    val ambientDirection = LocalLayoutDirection.current
    val drawerDirection = toolsDrawerDirection(isLandscapeWindow, portEdge, ambientDirection)
    // The keep-outs, compass and position every map under this screen reads: see rememberScreenMapLocals (ScreenMapLocals.kt).
    val mapLocals = rememberScreenMapLocals(uiState, compassProvider, computeTrueHeading, currentTime)
    // Phase 2.6, PR 4: the composition locals, the Tools drawer and the frame it slides over (AvailabilityScreenDrawerHost.kt),
    // composed here, unconditionally, at the line this tree was; InAppCameraHost stays below it.
    AvailabilityScreenDrawerHost(
        state = screenState,
        drawerState = drawerState,
        drawerDirection = drawerDirection,
        ambientDirection = ambientDirection,
        mapKeepOuts = mapLocals.mapKeepOuts,
        mapCompass = mapLocals.mapCompass,
        mapPosition = mapLocals.mapPosition,
        trackDetailsFoldOpen = trackDetailsFoldOpen,
        mapLayersFoldOpen = mapLayersFoldOpen,
        tripPlannerFoldOpen = tripPlannerFoldOpen,
        mapLayers = mapLayersControls,
        uiState = uiState,
        distanceUnit = distanceUnit,
        onDistanceUnitSelected = onDistanceUnitSelected,
        onDeletePlannedTrip = onDeletePlannedTrip,
        isNightMode = isNightMode,
        onNightModeMapsChanged = onNightModeMapsChanged,
        crowdedIcons = CrowdedIconsControls(uiState.crowdedIconsMode, settings.onCrowdedIconsModeChanged),
        onThemeModeChanged = onThemeModeChanged,
        crashFileStore = crashFileStore,
        backup = backup,
        openBackupRequest = openBackupRequest,
        openOfflineMapsFromTools = openOfflineMapsFromTools,
        compactMainScaffold = compactMainScaffold,
    )
    // The in-app camera, once, after the tree above, which is what puts it on top: see AvailabilityScreenInAppCamera.kt.
    AvailabilityScreenInAppCamera(
        cameraInputs = cameraInputs,
        cameraCaptureFiles = cameraCaptureFiles,
        uiState = uiState,
        settings = settings,
        findsLog = findsLog,
        journalEntries = journalEntries,
    )
}
