package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage F: the compact map tab, moved verbatim out of
// AvailabilityScreen.kt. Five blocks, lines of the file as of 3bd0efe: 3324-3354
// (MapIconClusterPositionState, rememberMapIconClusterPositionState), 3356-4420 (CompactMapTab),
// 4422-4434 (CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR), 4442-4443 (MAP_ICON_CLUSTER_TAG) and 5005-5006
// (MAP_MODE_PICKER_COMPACT_ANCHOR_OFFSET, since removed with the popover it anchored: map layers L0b
// replaced it with the Layers sheet). Same package as Stages A to E, so every same-package
// reference resolves unchanged. Pure move: no signature, name or body changed. Three widenings,
// private -> internal: rememberMapIconClusterPositionState, called from AvailabilityScreen.kt (and
// as CompactMapTab's parameter default here); MapIconClusterPositionState with it, since that
// function returns it; and CompactMapTab, composed from AvailabilityScreen.kt. This file is over
// the ~1,200-line target, accepted by the planner: CompactMapTab alone is 1,065 lines. It composes
// the Stage F siblings AvailabilityNavigationUi.kt, AvailabilityMapControlsUi.kt and
// AvailabilityMapOverlaysUi.kt; no symbol left behind in AvailabilityScreen.kt is reached from
// here. Seam F (the wide layout) was released by the owner for this split, as recorded in the
// Understory amendment merged in #130.

import android.util.Log
import androidx.compose.runtime.State
import com.zynergylabs.forager.app.ui.map.LocalMapPosition
import com.zynergylabs.forager.app.ui.map.MapBubbleKind
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.rememberNavigationFacing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapBubbleTarget
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.tappedThingOf
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import java.time.LocalDate

/**
 * The Maps tab in its full-bleed, compact-only form — decision #2 in `docs/plans/map-redesign.md`:
 * the map fills the entire content area, with the top compass/elevation strip and the right-edge
 * icon stack drawn over it.
 *
 * The one Maps tab at every window size (dispatch 2026-09-28-245: a tablet is a big phone). The
 * tablet's separate map pane, `MapTab` inside `CombinedResultsPane`, was removed.
 *
 * Owns the location-placing flow; [PendingMapAction] drives it, and [TripDatePickerDialog]/[defaultTripName]
 * are shared. The "what would you like to do here" chooser is [AddActionTile]. The icon stack's add (+)
 * button sets [showActionMenu] directly, rather than a parallel dialog/handler. Unlike before this rework, it no
 * longer needs to hand the flow a starting location itself: [CentrePinLocationPicker]'s own camera
 * tracking supplies that once a choice is made, the same as every other site.
 */
@Composable
internal fun CompactMapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    renderMode: MapRenderMode,
    modifier: Modifier = Modifier,
    /**
     * The icon cluster's position — vertical memory, displayed offset, side — held by the caller
     * so it survives leaving and returning to this tab. See [MapIconClusterPositionState]. The
     * default keeps any other caller self-contained.
     */
    clusterPosition: MapIconClusterPositionState = rememberMapIconClusterPositionState(),
    /**
     * Part 1 layout fixes, item 4: where the map keeps the camera the user left, held by the caller so
     * it survives leaving and returning to this tab, as [clusterPosition] is. See [MapCameraMemory].
     * The default keeps any other caller self-contained.
     */
    cameraMemory: MapCameraMemory = remember { MapCameraMemory() },
    /**
     * Dispatch 2026-09-29-57, item 8 (amendment -262, "Remember and reopen"): what "Open in Journal" leaves for the Maps
     * tab that comes back after Back from that find, held by the caller so it outlives this tab, as [cameraMemory] does.
     * See [MapReturnMemory]. The default keeps any other caller self-contained.
     */
    returnMemory: MapReturnMemory = remember { MapReturnMemory() },
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    /**
     * Fullscreen-fixes dispatch, Item 1 (third design) — this tab now hosts [ForagerBottomNav]
     * itself, inside its own content [Box] below, rather than the shared [Scaffold]'s `bottomBar`
     * slot ([compactMainScaffold]'s own `bottomBar` doc comment explains why: that slot's reported
     * height must never depend on [isFullscreen], and the only way to guarantee that is for the Map
     * tab's nav not to live there at all). [isDrawerOpen] and [onBottomNavTabSelected] are exactly
     * the two inputs [ForagerBottomNav] needs beyond its own `selectedTab` — hardcoded to
     * [CompactTab.MAP] at this composable's own call to it below, since that's the only tab this
     * composable is ever shown for. [onBottomNavHeightMeasured] reports the nav's own real measured
     * height back up to [compactMainScaffold]'s own scope — see that scope's own `bottomNavHeightPx`
     * doc comment for why this needs to be a real measurement, not a fixed constant, and why the
     * value is needed a second time there (the search-dropdown dismiss scrim and its own
     * `SearchDropdown` panel), which is why this tab doesn't just keep the measurement as private
     * local state the way [mapIconClusterHeightPx] below does.
     */
    isDrawerOpen: Boolean,
    onBottomNavTabSelected: (CompactTab) -> Unit,
    onBottomNavHeightMeasured: (Float) -> Unit,
    /**
     * Landscape B1: what this tab's controls are padded by, and never the map itself —
     * [compactMainScaffold]'s `mapControlsPadding` (the rail's measured width or, in fullscreen,
     * the navigation-bar inset on the port side; the cut-out inset on the sides). Applied to each
     * control's own modifier, the way portrait keeps controls clear of the bottom bar by its
     * measured height. Not applied to the centre-pin picker, which is positioned against the map
     * itself. **The tapped bubble is positioned against the map itself too, except that its clamp
     * box now stops short of the rail and of the L** (dispatch 2026-09-29-57, amendment -262, item 3,
     * the owner's "Push the card clear"): the left and right values here, plus the L's own width on
     * its side, narrow where the card may sit, the tip still landing on its glyph. That reverses the
     * earlier "positioned against the map itself" for those two only, because on a real S22 at 90 a
     * card opened under the L and past the rail, its close X under the rail, where no touch could
     * reach it. Zero by default, so portrait is unchanged.
     */
    controlsPadding: PaddingValues = PaddingValues(0.dp),
    onLocateMe: () -> Unit,
    /**
     * The recording's sundown line (dispatch 2026-09-28-592, plan task T3; placement RECORD -421,
     * "Strip, then HUD"): in the strip while recording, in the HUD while navigating. Navigating with no
     * recording, the HUD shows sunset and dark only, computed here ([rememberScreenSundownLine]).
     */
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine? = null,
    /**
     * The map's quick settings (dispatch 2026-09-28-645, Amendments 1 to 3): the three-dot button at
     * the strip's far right, and in the HUD while navigating, with Back by, the sundown settings and the off-track
     * reminder; its Back by also gives the strip's and the HUD's "Back by" line. `null` draws no button.
     */
    quickSettings: MapQuickSettings? = null,
    /** RECORD -739: the navigation inputs, grouped to keep this signature small enough for ART; see [CompactMapNavigation]. */
    navigation: CompactMapNavigation,
    /** RECORD -739: the recording and its waypoints; see [CompactMapRecording]. */
    recording: CompactMapRecording,
    /** RECORD -739: the short-landscape-window inputs, portrait by default; see [CompactMapLandscape]. */
    landscape: CompactMapLandscape = CompactMapLandscape(),
    /** RECORD -739: the search's "Set on map" pick; see [CompactMapSearchLocationPick]. */
    searchLocationPick: CompactMapSearchLocationPick = CompactMapSearchLocationPick(),
    compassProvider: CompassProvider,
    /** See [AvailabilityScreen]'s own `computeTrueHeading` doc comment. */
    computeTrueHeading: ComputeTrueHeadingUseCase,
    /** MGRS or decimal degrees, shared by the strip and the HUD; held in `AvailabilityScreen` (dispatch 2026-09-28-422). */
    showDecimalDegrees: Boolean,
    /** Flips [showDecimalDegrees]. */
    onToggleCoordinateFormat: () -> Unit,
    /** The HUD's fix-age clock — [AvailabilityScreen]'s own `currentTime`, so a test can pin an old fix as stale. */
    currentTime: CurrentTimeProvider,
    /** See [AvailabilityScreen]'s own `mapTaxonFilter` doc comment — "View on Map" from a List-tab row. */
    taxonFilter: Long?,
    onClearTaxonFilter: () -> Unit,
    /**
     * Extra top clearance beyond the compass strip's own row, for chrome this tab doesn't know
     * about that now floats above it — SearchEntryBar, on the compact scaffold's own Map tab (see
     * that call site's own doc comment). Defaults to 0.dp rather than being required: this
     * composable has exactly one call site today, but the strip/bubble/filter-chip positioning
     * below already treats "how much is above me" as a real, named input
     * ([compassStripClearance]) rather than assuming 0 — this parameter extends that same
     * assumption to cover chrome composed outside this function entirely, instead of baking a
     * second, undocumented assumption in above it.
     */
    topInset: Dp = 0.dp,
    /**
     * The search bar's bottom edge, settled: the L's top limit in a landscape window (`topLimitPx` below). Not [topInset], which
     * is animated to 0 while fullscreen hides the bar and back when it returns: the cluster re-clamps only when its own keys
     * change (`MapIconCluster`'s `LaunchedEffect`, keyed on `isFullscreen` but not on the limit), so on leaving fullscreen it
     * re-clamped against the animation's first frame, near 0, and the L settled at its centred position, above the search bar's
     * bottom (dispatch 2026-09-28-245, Part B; the S22-A record, A1 item 1). Defaults to [topInset], the previous behaviour, for
     * a caller that passes nothing.
     */
    searchBarBottom: Dp = topInset,
    /**
     * SearchEntryBar (plus its SearchNotice), composed as a slot inside this composable's own
     * Box rather than passed up and rendered at the call site — a deliberate, load-bearing
     * placement, not a style choice: this bar's own 80%-alpha fill needs to blend against real
     * map imagery to read as translucent chrome the way the compass strip and the two
     * TrailheadControls pills already do, and both of those live in this exact Box, as direct
     * siblings of [mapSlot]'s own [AndroidView][androidx.compose.ui.viewinterop.AndroidView]
     * content. Composing the bar even one level further out (a sibling of this whole composable's
     * own call, in [compactMainScaffold]'s outer `Box` instead) was tried first and shipped
     * fully opaque on a real device despite an identical `Surface`/color/alpha to the strip and
     * pills, and despite its own geometry measuring correctly positioned above the map — Compose's
     * alpha-blending coordination with an embedded native `View` (this map is `AndroidView`-hosted)
     * appears to be scoped to the immediate composition that hosts it, not just correct z-order
     * anywhere in the tree above it. Defaults to an empty slot: this composable has exactly one
     * call site today (compactMainScaffold's own Map tab branch), which supplies the bar; nothing
     * else needs to know this parameter exists.
     */
    searchBarSlot: @Composable (compassStripHeight: Dp) -> Unit = { _ -> },
    /**
     * Dispatch 2026-09-28-104, item 2 (owner, "Option A"): the bottom edge, in this Box's own coordinates, of the search notice while
     * one shows, else 0. The icon cluster's top limit follows it: the cluster is held at or below this edge and returns to where the
     * user left it when the notice clears (the clamp is display-only). Placement only.
     */
    searchNoticeBottom: Dp = 0.dp,
    /**
     * Map layers L0b: the Layers sheet's choices and callbacks, the legend and every saved record this
     * tab draws (see [MapLayersControls]). Defaulted so any other caller is unchanged.
     */
    mapLayers: MapLayersControls = MapLayersControls(),
    /**
     * M1: what this tab's glyph bubbles look records up in, and the targets they open (the J5c
     * details sheet's inputs, the photos, and "Open in Journal"). See [MapRecordSources]. Defaulted,
     * so another caller gets bubbles with nothing to find: each closes at once, with a logged line.
     */
    bubbleSources: MapRecordSources = MapRecordSources(),
) {
    // RECORD -739: the grouped inputs this function still reads, unpacked under their old parameter names (RECORD -741: the rest are
    // read in the parts that use them, below).
    val isNavigating = navigation.isNavigating
    val waypointReopen = navigation.waypointReopen
    val onWaypointReopenConsumed = navigation.onWaypointReopenConsumed
    val onLeftNavigationView = navigation.onLeftNavigationView
    val onNavigationZoomApplied = navigation.onNavigationZoomApplied
    val startRecordingErrorMessage = recording.startRecordingErrorMessage
    val waypoints = recording.waypoints
    val onDropWaypoint = recording.onDropWaypoint
    val railPortEdge = landscape.railPortEdge
    val punchHoleEdge = landscape.punchHoleEdge
    val pickingSearchLocation = searchLocationPick.pickingSearchLocation
    val onCancelSearchLocationPick = searchLocationPick.onCancelSearchLocationPick
    // RECORD -741: these five are handed to CompactMapPickers as State objects, so each is held as one and read through it here.
    val showActionMenuState = remember { mutableStateOf(false) }
    var showActionMenu by showActionMenuState
    val showLayersSheetState = remember { mutableStateOf(false) }
    var showLayersSheet by showLayersSheetState
    val pendingActionState = remember { mutableStateOf<PendingMapAction?>(null) }
    var pendingAction by pendingActionState
    val pendingTripLocationState = remember { mutableStateOf<LatLng?>(null) }
    var pendingTripLocation by pendingTripLocationState
    val pendingWaypointLocationState = remember { mutableStateOf<LatLng?>(null) }
    var pendingWaypointLocation by pendingWaypointLocationState
    // M1 (planner's ruling: one bubble at a time): the one tapped thing, a sighting or any glyph.
    // Item 8: a bubble waiting from "Open in Journal" comes back open, once, when this tab is created again by Back from that find.
    var tapped by remember {
        mutableStateOf<TappedMapThing?>(returnMemory.takeBubble(mapLayers.records.findMarkers) ?: returnMemory.takeEntryBubble(bubbleSources))
    }
    // A fan the map did not use before this tab left composition is not kept for a later map.
    DisposableEffect(returnMemory) { onDispose { returnMemory.clearRestore() } }
    // Dispatch -502, Amendment 1: Back ended a waypoint navigation started in a bubble, which opens again where it was.
    // The map re-anchors it on its glyph at the next camera idle (a waypoint is a point kind), as it does after a pan.
    LaunchedEffect(waypointReopen) {
        val bubble = waypointReopen as? WaypointNavigationOrigin.MapBubble ?: return@LaunchedEffect
        val waypoint = bubbleSources.waypoints.firstOrNull { it.id == bubble.waypointId }
        if (waypoint != null) {
            tapped = TappedMapThing(
                MapBubbleTarget.FeatureTarget(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, waypoint.id, LatLng(waypoint.lat, waypoint.lng)),
                bubble.anchorPx,
                bubble.bearingDeg,
            )
        } else {
            Log.w(COMPACT_MAP_LOG_TAG, "Waypoint ${bubble.waypointId} is gone; its bubble was not opened again after Back ended its navigation.")
        }
        onWaypointReopenConsumed()
    }
    val onFeatureTap: (MapFeatureTap) -> Unit = remember { { tap -> tappedThingOf(tap)?.let { tapped = it } } }
    // Part A of dispatch 2026-09-28-387: a tap on a stack closes the bubble that is showing. Remembered, like onFeatureTap, so renderMode compares equal across recompositions.
    val onCloseBubble: () -> Unit = remember { { tapped = null } }
    // See MapOverlayContent.resumeTrackingRequestId's own doc comment — incremented alongside the
    // existing onLocateMe() call below, not instead of it: that call still drives the compass
    // strip's own one-shot position/elevation text, this drives the map's live GPS camera puck.
    var resumeTrackingRequestId by remember { mutableIntStateOf(0) }
    // See MapOverlayContent.resetOrientationRequestId's own doc comment.
    var resetOrientationRequestId by remember { mutableIntStateOf(0) }
    // J6c: the cluster's state and its measurements live in MapIconClusterState (AvailabilityMapIconCluster.kt),
    // shared with the tablet's map. The names below are aliases, so what this tab still reads (the bar's side,
    // the container's width, the nav's height, the legend chip's top) reads as it did.
    val cluster = rememberMapIconClusterState(clusterPosition, railPortEdge, punchHoleEdge)
    val landscapeCluster = cluster.landscape
    var mapContentBoxHeightPx by cluster::mapContentBoxHeightPx
    var mapContentBoxTopInRootPx by cluster::mapContentBoxTopInRootPx
    var mapBottomNavHeightPx by cluster::bottomNavHeightPx
    // Landscape B1 (Resolution R18): with no bottom bar composed, its last measured height would
    // otherwise stay behind as a phantom bottom band for the cluster's drag clamp and the
    // centre-pin confirm row — onGloballyPositioned stops firing once the bar is gone.
    val showBottomNav = railPortEdge == null
    LaunchedEffect(showBottomNav) {
        if (!showBottomNav) mapBottomNavHeightPx = 0f
    }

    // AddActionTile and CentrePinLocationPickerOverlay are both plain overlays, not real Dialogs,
    // so — unlike TripDatePickerDialog below, an M3 DatePickerDialog whose own Dialog window
    // already handles system back for free — this needs its own BackHandler or system back would
    // fall straight through either, same reasoning as AvailabilityScreen's own top-level "unwind
    // before falling through" chain. One pop at a time: the picker phase first if it's showing,
    // the menu only once the picker's already closed. pickingSearchLocation joins the same picker
    // tier as pendingAction (both show the identical CentrePinLocationPickerOverlay, just for a
    // different caller) rather than a third priority level of its own.
    // Navigation is deliberately NOT here any more (navigation-chrome amendment). Stage one had
    // `|| isReturning` in this condition with `else -> onToggleReturning()`, and because this is
    // the deepest registered handler it exited navigation before fullscreen, the drawer or the
    // search dropdown unwound — see AvailabilityScreen's own back chain, where navigation now sits
    // as the last step before exit and raises a prompt rather than exiting.
    // Intent 2026-09-28-44, F4: off while the Tools drawer is open, the drawer fix's pattern
    // (JournalTab's backEnabled). Registered after AvailabilityScreen's drawer handler, this took
    // Back from the drawer over the centre-pin pickers and, in a short window where the menu's scrim
    // leaves the rail clear, over the add-action menu. Each stays up under the drawer; the next Back
    // unwinds it as before.
    BackHandler(enabled = !isDrawerOpen && (pendingAction != null || pickingSearchLocation || showActionMenu)) {
        when {
            pendingAction != null -> pendingAction = null
            pickingSearchLocation -> onCancelSearchLocationPick()
            else -> showActionMenu = false
        }
    }

    MapControlToasts(uiState.locateMeStatus, startRecordingErrorMessage)

    when {
        uiState.isLoadingSightings -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.sightingsErrorMessage != null -> MapMessage(
            uiState.sightingsErrorMessage,
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
        )

        else -> {
            // The map's own viewport, never null (see compactMapDisplayRegion).
            val displayRegion = compactMapDisplayRegion(uiState)

            // The GPS/locate-me pan target for a search that's already run — remember(uiState.region),
            // not just remember, so a brand new search (a different region) drops any earlier
            // locate-me override rather than keeping the map stuck on a now-stale GPS fix; see
            // MapSlot's focusOverride doc comment for why this is independent of region itself.
            // Pre-search, displayRegion already tracks locate-me directly (see above), so this stays
            // null rather than doubly panning the same fix through two different mechanisms.
            var focusOverride by remember(uiState.region) { mutableStateOf<LatLng?>(null) }
            LaunchedEffect(uiState.locateMeStatus) {
                val status = uiState.locateMeStatus
                if (uiState.region != null && status is LocateMeStatus.Located) focusOverride = status.location
            }

            // Sightings are only real once a search has actually run — before that, displayRegion is a
            // viewport with nothing plotted on it yet, not a stand-in search. Saved planned trips are
            // the user's own records, like the waypoints and finds below, so they are handed to the map
            // whether or not a search has run (owner, 2026-09-28, "Option A"; a real S22 drew none
            // until a search set the region).
            val hasSearched = uiState.region != null
            // "View on Map" from a List-tab row (see compactMapFilteredSightings and compactMapTaxonFilterLabel).
            val filteredSightings = compactMapFilteredSightings(uiState, hasSearched, taxonFilter)
            val mapTaxonFilterLabel = compactMapTaxonFilterLabel(uiState, taxonFilter, filteredSightings)

            var cameraCenter by remember(displayRegion) { mutableStateOf(LatLng(displayRegion.lat, displayRegion.lng)) }
            // A real clearance for "below the compass strip," derived from the strip's own actual
            // type style rather than a hardcoded touch-target constant (Part A item 1 of this
            // dispatch un-pinned the strip's height back to wrapping its text content, so a fixed
            // 48dp guess would now be too generous). Measured once via rememberTextMeasurer — the
            // same approach the since-removed DistanceArm used for its widest-string width — rather than
            // read back from the strip's real onGloballyPositioned layout: a state value written
            // during layout and read here to construct AnchoredAtScreenPoint's own minY argument
            // was tried and is a confirmed, reproducible regression — AvailabilityScreenMapIconStackTest's
            // own "tapping elsewhere on the map dismisses the observation bubble" test went from
            // passing to reliably failing on exactly that change (bisected line by line), corrupting
            // mapSlot(...)'s own onTap wiring one frame later for reasons this investigation could
            // not fully pin down inside Compose's own recomposition-scope internals. A remembered,
            // one-time measurement carries no such risk: it never changes after first composition,
            // so nothing here ever triggers a later recomposition.
            // Navigation HUD stage one: the ONE true-north heading both the compass strip and the
            // HUD read. Passed down as the State object; its .value is read only inside those two
            // leaves — reading it here would recompose this whole tab at sensor rate. See
            // rememberTrueHeading's own doc comment before touching this.
            // Dispatch 2026-09-28-510: at the best position there is (AvailabilityUiState.headingFix), so the
            // HUD's needle and the strip's heading work while the position is approximate or last known.
            // RECORD -761 (the owner: "Keep last reading (Recommended)"): starts from the last reading, kept with the camera above the tab.
            val trueHeading = rememberTrueHeading(compassProvider, computeTrueHeading, uiState.headingFix, cameraMemory.heading, currentTime::nowEpochMillis)
            // Dispatch 2026-09-28-510: what is shown in place of GPS, for the strip. Read only in its leaf.
            val positionNote = rememberPositionNote(LocalMapPosition.current, currentTime)
            // Dispatch 2026-09-28-430: which way the map faces while navigating, from the same heading.
            val navigationFacing by rememberNavigationFacing(trueHeading, isNavigating, currentTime)
            // Dispatch -497, RECORD -845: where the start is once the walker has arrived (see compactMapArrivedAt).
            val arrivedAt = compactMapArrivedAt(navigation, uiState, currentTime)
            val currentOnLeftNavigationView by rememberUpdatedState(onLeftNavigationView)
            val onLeftView: () -> Unit = remember { { currentOnLeftNavigationView() } }
            val currentOnNavigationZoomApplied by rememberUpdatedState(onNavigationZoomApplied)
            val onStartZoomApplied: () -> Unit = remember { { currentOnNavigationZoomApplied() } }
            // MGRS by default, the labelled decimal pair on tap — hoisted here from the strip's
            // own leaf (navigation-chrome dispatch) because the strip and the HUD now take turns
            // showing the coordinates: a format chosen while navigating must still be the format
            // the strip shows on exit (CLAUDE.md, UX defaults — user-set state that resets on its
            // own is a bug). Not AvailabilityUiState: purely which of two always-computable
            // representations of the same fix to display, nothing the ViewModel needs. Held in
            // AvailabilityScreen since dispatch 2026-09-28-422, so it also survives this tab
            // unmounting on a tab change; it used to reset then.
            val compassStripTextMeasurer = rememberTextMeasurer()
            // Dispatch 2026-09-28-592: the phone's clock format for the sundown line, in the strip and the HUD.
            val sundownClock = rememberSundownClock()
            val compassStripLabelStyle = MaterialTheme.typography.labelMedium
            val compassStripDensity = LocalDensity.current
            // The strip's real height, measured on the strip itself where it is composed below (item 2). Not
            // compassStripClearance, which is one text line's height.
            // RECORD -741: the three measured sizes are remembered here as State objects (handed on through the holder below).
            val compassStripHeightState = remember { mutableIntStateOf(0) }
            // RECORD -729: the strip's measured height in a landscape window (the search bar is given it); 0 elsewhere.
            val landscapeStripHeightState = remember { mutableIntStateOf(0) }
            // RECORD -732: and its measured width (the scaffold gives the bar the rest of the room); 0 elsewhere.
            val landscapeStripWidthState = remember { mutableIntStateOf(0) }
            // UI split Phase 2.3: the three sizes, read and written through one view over those States (see CompactMapLayoutState).
            // Remembered unkeyed here, in this branch and straight after them, so it lives exactly as long as they do.
            // The body still makes both reads of compassStripHeightPx, through the holder: inside stripBottomClearance, for the
            // clearance below, and directly through its getter for the search-bar slot's height.
            val layout = remember { CompactMapLayoutState(compassStripHeightState, landscapeStripHeightState, landscapeStripWidthState) }
            val compassStripClearance = remember(compassStripLabelStyle, compassStripDensity) {
                with(compassStripDensity) {
                    compassStripTextMeasurer.measure("Mg", compassStripLabelStyle).size.height.toDp()
                }
            }
            // RECORD -709: what sits below the strip in portrait clears its measured height, the one-line clearance the floor (see
            // CompactMapLayoutState.stripBottomClearance). Read here, in the body, and handed to the children as a value.
            val compassStripBottomClearance = layout.stripBottomClearance(compassStripClearance, compassStripDensity)
            Box(
                modifier = modifier
                    .fillMaxSize()
                    // Feeds mapIconBarDisplayedOffsetPx's own clamp below — see that variable's own
                    // doc comment.
                    .onGloballyPositioned { coordinates ->
                        mapContentBoxHeightPx = coordinates.size.height.toFloat()
                        mapContentBoxTopInRootPx = coordinates.positionInRoot().y
                        cluster.mapContentBoxLeftInRootPx = coordinates.positionInRoot().x
                    },
            ) {
                // The map (RECORD -741: CompactMapMapSlot, moved out unchanged; its comments are there).
                CompactMapMapSlot(
                    mapSlot = mapSlot,
                    displayRegion = displayRegion,
                    filteredSightings = filteredSightings,
                    uiState = uiState,
                    recording = recording,
                    navigation = navigation,
                    arrivedAt = arrivedAt,
                    resumeTrackingRequestId = resumeTrackingRequestId,
                    resetOrientationRequestId = resetOrientationRequestId,
                    tapped = tapped,
                    onTapped = { tapped = it },
                    mapLayers = mapLayers,
                    renderMode = renderMode,
                    // The fan's Back goes after the add-action menu and the pin pickers (dispatch 2026-09-28-298).
                    fanBackEnabled = renderMode.backEnabled && pendingAction == null && !pickingSearchLocation && !showActionMenu,
                    onFeatureTap = onFeatureTap,
                    onCloseBubble = onCloseBubble,
                    cameraMemory = cameraMemory,
                    returnMemory = returnMemory,
                    navigationFacing = navigationFacing,
                    onLeftView = onLeftView,
                    onStartZoomApplied = onStartZoomApplied,
                    trueHeading = trueHeading,
                    cluster = cluster,
                    focusOverride = focusOverride,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onCameraIdle = { location -> cameraCenter = location },
                )
                // Composed right after mapSlot — see searchBarSlot's own doc comment for why this
                // exact nesting (a direct sibling of the map's own AndroidView content, inside
                // this Box) is what makes its translucency actually work.
                // The strip's own measured height goes with it, so a notice in the slot can be placed below the strip
                // (item 2). Zero while the strip is not composed.
                // Owner's ruling (b), continuation 2026-09-28-172: a notice beside the landscape L is inset by the L (see
                // CompactMapLayoutState.searchNoticeInset). Worked out here, in the body, as before.
                val noticeInsetDp = layout.noticeInsetDp(cluster.clusterWidthPx, compassStripDensity)
                val searchNoticeInset = layout.searchNoticeInset(landscapeCluster, cluster, punchHoleEdge, noticeInsetDp)
                CompositionLocalProvider(LocalSearchNoticeInset provides searchNoticeInset) {
                    searchBarSlot(with(compassStripDensity) { layout.compassStripHeightPx.toDp() })
                }
                // The tapped thing's bubble (RECORD -741: CompactMapBubble, moved out unchanged; its comments are there).
                CompactMapBubble(
                    tapped = tapped,
                    onCloseBubble = onCloseBubble,
                    bubbleSources = bubbleSources,
                    returnMemory = returnMemory,
                    renderMode = renderMode,
                    controlsPadding = controlsPadding,
                    cluster = cluster,
                    noticeInsetDp = noticeInsetDp,
                    minY = layout.bubbleMinY(topInset, compassStripBottomClearance),
                    // Fullscreen and the search dropdown are left out as the drawer and the menu states are (dispatch 2026-09-28-312,
                    // item 11): a bubble closes after anything opened after it, and their Back handlers were registered before the
                    // bubble layer's, so without these terms a bubble took the first Back and they the second. The dropdown's state
                    // lives in the scaffold and arrives in renderMode.backEnabled (off while the drawer, fullscreen or the dropdown
                    // is up), the same value the fan's handler is gated on above.
                    backEnabled = !isDrawerOpen && !isFullscreen && renderMode.backEnabled && pendingAction == null && !pickingSearchLocation && !showActionMenu,
                    navigation = navigation,
                )
                // The icon cluster (RECORD -741: CompactMapIconCluster, moved out unchanged; its comments are there).
                CompactMapIconCluster(
                    cluster = cluster,
                    uiState = uiState,
                    navigation = navigation,
                    recording = recording,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onLocateMe = onLocateMe,
                    onResumeTracking = { resumeTrackingRequestId++ },
                    onResetOrientation = { resetOrientationRequestId++ },
                    mapMode = mapMode,
                    onOpenLayers = { showLayersSheet = true },
                    onOpenActionMenu = { showActionMenu = true },
                    topLimitPx = layout.clusterTopLimitPx(landscapeCluster, searchBarBottom, topInset, compassStripBottomClearance, compassStripDensity),
                    noticeBottomPx = with(compassStripDensity) { searchNoticeBottom.toPx() },
                    controlsPadding = controlsPadding,
                )
                // The chips under the strip (RECORD -741: CompactMapChips, moved out unchanged; its comments are there). Dispatch
                // 2026-10-12-08: composed before the strip, no longer after it. While navigating in portrait the strip's card now holds
                // the navigation info, which the chips' place (one line under the strip, as while the display replaced the strip)
                // overlaps; the display was composed after the chips, so it covered them and kept its touches, and composing the
                // chips first keeps that. Not navigating, the two do not overlap, so nothing else changes.
                CompactMapChips(
                    mapTaxonFilterLabel = mapTaxonFilterLabel,
                    onClearTaxonFilter = onClearTaxonFilter,
                    mapLayers = mapLayers,
                    landscape = landscape,
                    cluster = cluster,
                    controlsPadding = controlsPadding,
                    topInset = topInset,
                    compassStripBottomClearance = compassStripBottomClearance,
                )

                // The compass strip and the landscape join (RECORD -741: CompactMapTopStrip, moved out unchanged; its comments are there).
                CompactMapTopStrip(
                    uiState = uiState,
                    navigation = navigation,
                    landscape = landscape,
                    isFullscreen = isFullscreen,
                    controlsPadding = controlsPadding,
                    topInset = topInset,
                    showDecimalDegrees = showDecimalDegrees,
                    onToggleCoordinateFormat = onToggleCoordinateFormat,
                    recordingSundownLine = recordingSundownLine,
                    quickSettings = quickSettings,
                    trueHeading = trueHeading,
                    positionNote = positionNote,
                    sundownClock = sundownClock,
                    layout = layout,
                    portraitNavigationInfo = {
                        CompactMapPortraitNavigationInfo(
                            uiState = uiState,
                            navigation = navigation,
                            cluster = cluster,
                            trueHeading = trueHeading,
                            navigationFacing = navigationFacing,
                            currentTime = currentTime,
                            showDecimalDegrees = showDecimalDegrees,
                            recordingSundownLine = recordingSundownLine,
                            quickSettings = quickSettings,
                            sundownClock = sundownClock,
                        )
                    },
                )

                // The landscape navigation card, under the strip (dispatch 2026-10-12-08; in portrait the navigation info is inside the
                // strip's card above). Its comments are there.
                CompactMapNavigationDisplay(
                    uiState = uiState,
                    navigation = navigation,
                    landscape = landscape,
                    isFullscreen = isFullscreen,
                    cluster = cluster,
                    controlsPadding = controlsPadding,
                    trueHeading = trueHeading,
                    navigationFacing = navigationFacing,
                    currentTime = currentTime,
                    showDecimalDegrees = showDecimalDegrees,
                    recordingSundownLine = recordingSundownLine,
                    quickSettings = quickSettings,
                    sundownClock = sundownClock,
                    layout = layout,
                )

                // "Return to Route", the legend, the bottom bar and the rail (RECORD -741: CompactMapBottomChrome, moved out unchanged;
                // its comments are there). Composed here, after the ambient chrome and before the modal overlays below.
                CompactMapBottomChrome(
                    navigation = navigation,
                    landscape = landscape,
                    renderMode = renderMode,
                    mapLayers = mapLayers,
                    cluster = cluster,
                    controlsPadding = controlsPadding,
                    showBottomNav = showBottomNav,
                    isFullscreen = isFullscreen,
                    isDrawerOpen = isDrawerOpen,
                    onBottomNavTabSelected = onBottomNavTabSelected,
                    onBottomNavHeightMeasured = onBottomNavHeightMeasured,
                )

                // The add-action menu, the Layers sheet and the centre-pin picker (RECORD -741: CompactMapPickers, moved out
                // unchanged; its comments are there). Composed after the bottom bar and the rail, on purpose (see there).
                CompactMapPickers(
                    showActionMenuState = showActionMenuState,
                    showLayersSheetState = showLayersSheetState,
                    pendingActionState = pendingActionState,
                    pendingTripLocationState = pendingTripLocationState,
                    pendingWaypointLocationState = pendingWaypointLocationState,
                    currentCameraCenter = { cameraCenter },
                    searchLocationPick = searchLocationPick,
                    onLogFindHere = onLogFindHere,
                    mapMode = mapMode,
                    onMapModeSelected = onMapModeSelected,
                    mapLayers = mapLayers,
                    landscape = landscape,
                    cluster = cluster,
                    controlsPadding = controlsPadding,
                    isFullscreen = isFullscreen,
                    night = renderMode.night,
                )
            }
        }
    }

    pendingTripLocation?.let { location ->
        TripDatePickerDialog(
            defaultName = defaultTripName(uiState.plannedTrips.size),
            onConfirm = { date, name ->
                onPlaceTripPin(location, date, name)
                pendingTripLocation = null
            },
            onDismiss = { pendingTripLocation = null },
        )
    }

    pendingWaypointLocation?.let { location ->
        WaypointNameDialog(
            defaultName = defaultWaypointName(waypoints.size),
            onConfirm = { name ->
                onDropWaypoint(location, name)
                pendingWaypointLocation = null
            },
            onDismiss = { pendingWaypointLocation = null },
        )
    }
}

/** Dispatch 2026-09-28-502: the tag this tab's one log line goes under. */
private const val COMPACT_MAP_LOG_TAG = "CompactMapTab"
