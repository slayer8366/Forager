package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.5 PR 2 (dispatch 2026-10-19-39, RECORD -1477): the Maps tab's call, moved from CompactMainScaffold's tab
// `when` (AvailabilityCompactScaffold.kt lines 1263-1535 at ui-split-2-5-dropdown 488d5864, 1268-1540 at b60d7fba) into this
// function, which the frame's `CompactTab.MAP ->` branch calls at the same place, inside its TabCrossfade. The block is moved with
// its indentation, as the frame's own Stage G extraction did. The only edits to it:
//  - the frame's measured sizes are read and written through [ScaffoldMeasurements], and the dropdown's two States through
//    [ScaffoldSearchDropdownState]: a getter and a setter on the frame's own State, at the same places;
//  - the CompactMapRecording and CompactMapNavigation bundles are built at the frame's call site, in its argument list, and
//    passed in (planner's go, so this function's signature stays small enough for ART: RECORD -739). Their lines are moved
//    there unchanged; here they are `recording = recording` and `navigation = navigation`. Both are plain data built from the
//    frame's parameters, with no State read, so building them a few arguments earlier changes nothing they hold;
//  - everything else it used is a parameter of the same name and type: the frame's getter parameters (compactTab,
//    isMapFullscreen, isDrawerOpen, mapMode, mapTaxonFilter) as the getters themselves, called where they were called; the
//    frame's other parameters as they are; TabCrossfade's `tab`; and the frame's locals (showRail, landscapeSearchWidth,
//    landscapeSearchAlignment, barMeetsStrip, mapSearchBarHeight, the three attribution insets, safeAnimatedTopInset,
//    mapControlsPadding, journalSearchHeld, onLogFindHere, onBottomNavTabSelected) as values. Those locals are worked out again
//    on every composition from inputs read in the frame's own scopes, so any change to one recomposes a scope that calls this
//    again with the new value: a value here cannot go stale, which is the case Stage G's getter rule exists for (planner,
//    dispatch 2026-10-19-39's go).
// The block reads one real inset (WindowInsets.statusBars, the search bar's slide distance) at the same place, inside
// searchBarSlot, and pads by mapControlsPadding, which holds the cut-out and navigation-bar insets. Robolectric reports them as
// zero, so what they do is checked on the S22 (CLAUDE.md, "Robolectric reports zero window insets").

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import java.time.LocalDate

@Composable
internal fun ScaffoldMapTabCall(
    tab: CompactTab,
    compactTab: () -> CompactTab,
    isMapFullscreen: () -> Boolean,
    isDrawerOpen: () -> Boolean,
    mapMode: () -> MapMode,
    mapTaxonFilter: () -> Long?,
    dropdown: ScaffoldSearchDropdownState,
    measurements: ScaffoldMeasurements,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    mapIconClusterPosition: MapIconClusterPositionState,
    mapCameraMemory: MapCameraMemory,
    mapReturnMemory: MapReturnMemory,
    showRail: Boolean,
    portEdge: ScreenEdge,
    punchHoleEdge: ScreenEdge,
    landscapeSearchWidth: Dp?,
    landscapeSearchAlignment: Alignment.Horizontal,
    barMeetsStrip: Boolean,
    mapSearchBarHeight: Dp,
    safeAnimatedTopInset: Dp,
    safeAttributionBottomInset: Dp,
    safeAttributionEndInset: Dp,
    attributionButtonBottomInset: Dp?,
    mapControlsPadding: PaddingValues,
    journalSearchHeld: Boolean,
    mapRenderMode: MapRenderMode,
    onMapModeChange: (MapMode) -> Unit,
    mapLayers: MapLayersControls,
    mapBubbleSources: MapRecordSources,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    onIsMapFullscreenChange: (Boolean) -> Unit,
    onMapFullscreenChanged: (Boolean) -> Unit,
    onBottomNavTabSelected: (CompactTab) -> Unit,
    onLocateMe: () -> Unit,
    recording: CompactMapRecording,
    navigation: CompactMapNavigation,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    compassProvider: CompassProvider,
    computeTrueHeading: ComputeTrueHeadingUseCase,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    currentTime: CurrentTimeProvider,
    onClearMapTaxonFilter: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    appClearFocusInProgress: BooleanArray,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onClearSearch: () -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
) {
                            CompactMapTab(
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
                                    onRailWidthMeasured = { measurements.mapRailWidthPx = it },
                                    // RECORD -729: the landscape strip's height, which the bar takes (mapSearchBarHeight).
                                    onLandscapeStripHeightMeasured = {
                                        measurements.landscapeStripHeight = it
                                        if (it > 0.dp) measurements.lastMapsStripHeightDp = it.value
                                    },
                                    // RECORD -732: and its width; the bar takes the rest of the room (landscapeSearchWidth).
                                    onLandscapeStripWidthMeasured = {
                                        measurements.landscapeStripWidth = it
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
                                renderMode = mapRenderMode.copy(bottomInset = safeAttributionBottomInset, attributionEndInset = safeAttributionEndInset, attributionBottomInset = attributionButtonBottomInset, backEnabled = !isDrawerOpen() && !isMapFullscreen() && !dropdown.showSearchDropdown, leavingTab = tab != compactTab()),
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
                                onBottomNavHeightMeasured = { measurements.bottomNavHeightPx = it },
                                controlsPadding = mapControlsPadding,
                                onLocateMe = onLocateMe,
                                recording = recording,
                                navigation = navigation,
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
                                    pickingSearchLocation = dropdown.pickingSearchLocationOnMap,
                                    onSearchLocationPicked = { location ->
                                        onManualLatChanged("%.4f".format(location.lat))
                                        onManualLngChanged("%.4f".format(location.lng))
                                        onSearchManualCoordinates()
                                        dropdown.pickingSearchLocationOnMap = false
                                    },
                                    onCancelSearchLocationPick = { dropdown.pickingSearchLocationOnMap = false },
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
                                    with(LocalDensity.current) { measurements.searchChromeHeightPx.toDp() }
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
                                        LaunchedEffect(compassStripHeight) { measurements.mapCompassStripHeight = compassStripHeight }
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
                                            modifier = if (landscapeSearchWidth != null && barMeetsStrip && measurements.landscapeStripWidth > 0.dp) {
                                                // RECORD -732: the rest of the room, by the strip's measured width as padding on the
                                                // rail's side, so the bar ends on the strip's own first pixel whatever the rounding
                                                // (the rounding fix of -729, carried over).
                                                Modifier
                                                    .padding(mapControlsPadding)
                                                    .absolutePadding(
                                                        left = if (portEdge == ScreenEdge.Left) measurements.landscapeStripWidth else 0.dp,
                                                        right = if (portEdge == ScreenEdge.Right) measurements.landscapeStripWidth else 0.dp,
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
                                        Column(modifier = Modifier.mapKeepOut(MapKeepOutIds.SEARCH_BAR).onSizeChanged { measurements.searchChromeHeightPx = it.height }) {
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
                                                onFieldFocused = { if (!appClearFocusInProgress[0]) dropdown.showSearchDropdown = true },
                                                // The Maps tab's own bar, over its map (map chrome at 80%;
                                                // owner, "1 A": its suggestions stack over the 0.8 panel).
                                                overMap = true,
                                                // RECORD -729: the strip's height in landscape, not navigating; null keeps the bar's own.
                                                landscapeHeight = mapSearchBarHeight.takeIf { barMeetsStrip && measurements.landscapeStripHeight > 0.dp },
                                                // Dispatch 2026-10-12-06: the rounded pill. Beside the landscape strip it rounds its outer side
                                                // only and meets the strip square, edge to edge (RECORD -934; its "no join line" is replaced by
                                                // -967's one line, the pill's outline, below).
                                                pillCorners = if (landscapeSearchWidth != null && barMeetsStrip && measurements.landscapeStripWidth > 0.dp) {
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
}
