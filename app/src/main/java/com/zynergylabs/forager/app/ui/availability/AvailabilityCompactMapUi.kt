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

import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.JournalEntriesMapChip
import kotlinx.coroutines.launch
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_CORNER_RADIUS
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING
import com.zynergylabs.forager.app.ui.map.mapIconChromeFillColor
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapIconBarMinimizeHandle
import com.zynergylabs.forager.app.ui.map.MapIconBarRestoreHandle
import com.zynergylabs.forager.app.ui.map.mapIconStackBorderColor
import com.zynergylabs.forager.app.ui.map.mapIconClusterContainerColor
import com.zynergylabs.forager.app.ui.map.mapIconClusterChildColor
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.map.MAPS_TAB_OVERLAYS
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapLayersSheet
import com.zynergylabs.forager.app.ui.map.MapLegendChip
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.mapLegendFor
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapBubbleLayer
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapBubbleTarget
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.focusedFeature
import com.zynergylabs.forager.app.ui.map.focusedObservationId
import com.zynergylabs.forager.app.ui.map.tappedThingOf
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * The icon cluster's position on the compact Map tab, as one holder so it can live *above*
 * [CompactMapTab] — in `compactMainScaffold`, which persists across tab changes — rather than in
 * the tab's own `remember`s, which are disposed with it on every tab switch. Direct owner request:
 * "switching between tabs resets the icon position; have it remember positions when switching in
 * and out of maps." That reversed the icon-bar-position-memory dispatch's working rule that
 * nothing survives a tab change, and the owner then made the general principle explicit —
 * CLAUDE.md, "UX defaults": user-set state survives navigating away and back by default, and an
 * unrequested reset is a bug unless the exception is stated for the case. Still session-only —
 * nothing here is persisted across app restarts, which is a separate per-case decision.
 *
 * Holds the cluster's user-set state: [userChosenOffsetPx] (the single source of truth, written
 * only by drags), [displayedOffsetPx] (the Animatable that is always the clamp of it under the
 * bounds in force — kept too, so returning to the tab does not glide in from zero),
 * [isOnLeftSide], and [isMinimized] (the fullscreen-fixes dispatch's "minimising resets when the
 * user leaves the Map tab" was a planner rule with no owner exception behind it, so the default
 * applies). The cluster's measurements (heights, the bar's centre) are not here — they are re-measured
 * on every mount and mean nothing across one. On return, the tab's bounds-change effect re-clamps
 * the memory under the bounds then in force (the tab change also exited fullscreen), so a position
 * chosen low in fullscreen comes back above the nav, and re-entering fullscreen glides it down
 * again — the same behaviour a fullscreen exit already has, now also across a tab change.
 */
internal class MapIconClusterPositionState {
    var userChosenOffsetPx by mutableStateOf(0f)
    val displayedOffsetPx = Animatable(0f)
    var isOnLeftSide by mutableStateOf(false)
    var isMinimized by mutableStateOf(false)

    // Landscape B2 (S6): a short landscape window's own cluster position, separate from the
    // portrait one above so that turning the phone neither carries a portrait drag into landscape
    // nor loses it on the way back. The side is stored as port or punch-hole, not left or right,
    // and translated to a window side from the current port edge where the cluster is anchored,
    // so turning between ROTATION_90 and ROTATION_270 keeps the cluster on the same device edge.
    // Defaults to the punch-hole side. Session only, like the portrait fields.
    var landscapeOnPortSide by mutableStateOf(false)
    var landscapeUserChosenOffsetPx by mutableStateOf(0f)
    val landscapeDisplayedOffsetPx = Animatable(0f)
    var landscapeIsMinimized by mutableStateOf(false)
}

@Composable
internal fun rememberMapIconClusterPositionState(): MapIconClusterPositionState = remember { MapIconClusterPositionState() }

/**
 * The Maps tab in its full-bleed, compact-only form — decision #2 in `docs/plans/map-redesign.md`:
 * the map fills the entire content area, with the top compass/elevation strip and the right-edge
 * icon stack drawn over it.
 *
 * Scoped to `WindowWidthClass.COMPACT` only; `MEDIUM`/`EXPANDED` keep using the unmodified [MapTab]
 * inside [CombinedResultsPane] — see the plan doc's "Scope decision" section for why this is a
 * separate composable rather than a conditional threaded through [MapTab] itself.
 *
 * Owns the location-placing flow exactly as [MapTab] does — see that composable's doc comment for
 * the mechanics [PendingMapAction] drives; [TripDatePickerDialog]/[defaultTripName] are shared,
 * unmodified, but the "what would you like to do here" chooser itself is [AddActionTile] here
 * rather than [MapTab]'s [ThreeWayActionDialog] — see that composable's own doc comment for why.
 * The icon stack's add (+) button reuses this exact same flow — it sets [showActionMenu] directly,
 * the identical trigger the map's own dedicated button sets on [MapTab], rather than a parallel
 * dialog/handler — so the two entry points can never drift apart. Unlike before this rework, it no
 * longer needs to hand the flow a starting location itself: [CentrePinLocationPicker]'s own camera
 * tracking supplies that once a choice is made, the same as every other site.
 */
@Composable
internal fun CompactMapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    renderMode: MapRenderMode,
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
     * Landscape B1 (Resolutions R12/R13, as revised on the owner's correction). Non-null in a
     * short landscape window: the charger-port edge, where this tab overlays
     * [ForagerNavigationRail] at 80% in place of its [ForagerBottomNav], in the same layer the
     * bottom bar occupies. The map under it stays full-bleed and never changes size; the rail is
     * absent in fullscreen, with no animation. Null everywhere else, which is today's behaviour.
     */
    railPortEdge: ScreenEdge? = null,
    /**
     * Landscape B2 (S1): the punch-hole edge (`punchHoleEdgeFor`), non-null exactly when
     * [railPortEdge] is. The search bar, its filter chip and the cluster's landscape default sit
     * on this side.
     */
    punchHoleEdge: ScreenEdge? = null,
    /** Landscape B2 (S2/S3): the search bar's capped width on the punch-hole side; null in portrait. */
    landscapeSearchWidth: Dp? = null,
    /** Reports the overlaid rail's measured width up, as [onBottomNavHeightMeasured] does the bar's. */
    onRailWidthMeasured: (Float) -> Unit = {},
    /**
     * Landscape B1: what this tab's controls are padded by, and never the map itself —
     * [compactMainScaffold]'s `mapControlsPadding` (the rail's measured width or, in fullscreen,
     * the navigation-bar inset on the port side; the cut-out inset on the sides). Applied to each
     * control's own modifier, the way portrait keeps controls clear of the bottom bar by its
     * measured height. Not applied to the tapped-sighting bubble or the centre-pin picker, which
     * are positioned against the map itself. Zero by default, so portrait is unchanged.
     */
    controlsPadding: PaddingValues = PaddingValues(0.dp),
    onLocateMe: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    startRecordingErrorMessage: String?,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    onDropWaypoint: (LatLng, String) -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    /**
     * Whether *any* navigation mode is active — [AvailabilityScreen]'s one `isNavigating`, see its
     * doc comment. Gates the HUD's presence and the compass strip's absence together, so heading,
     * elevation and coordinates are on screen exactly once in either state. Distinct from
     * [isReturning], which is one such mode (the only one in stage one) and still drives the
     * control pill's lit return toggle and the off-track heuristic.
     */
    isNavigating: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    compassProvider: CompassProvider,
    /** See [AvailabilityScreen]'s own `computeTrueHeading` doc comment. */
    computeTrueHeading: ComputeTrueHeadingUseCase,
    /** See [AvailabilityScreen]'s own `navigationTarget` doc comment. */
    navigationTarget: Waypoint?,
    /** See [AvailabilityScreen]'s own `pathHomeMeters` doc comment. */
    pathHomeMeters: Double?,
    /** The HUD's fix-age clock — [AvailabilityScreen]'s own `currentTime`, so a test can pin an old fix as stale. */
    currentTime: CurrentTimeProvider,
    /** See [AvailabilityScreen]'s own `mapTaxonFilter` doc comment — "View on Map" from a List-tab row. */
    taxonFilter: Long?,
    onClearTaxonFilter: () -> Unit,
    /**
     * True while [AdvancedSearchDropdown]'s "Set on map" is active — a [compactMainScaffold]-owned
     * state, not local to this tab, since the dropdown that triggers it lives above the bottom-nav
     * switch and can be reached from any tab. Shows [CentrePinLocationPickerOverlay] over this same
     * map the same way [pendingAction] already does, rather than a second picker.
     */
    pickingSearchLocation: Boolean = false,
    onSearchLocationPicked: (LatLng) -> Unit = {},
    onCancelSearchLocationPick: () -> Unit = {},
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
    modifier: Modifier = Modifier,
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
    var showActionMenu by remember { mutableStateOf(false) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingMapAction?>(null) }
    var pendingTripLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingWaypointLocation by remember { mutableStateOf<LatLng?>(null) }
    // M1 (planner's ruling: one bubble at a time): the one tapped thing, a sighting or any glyph.
    var tapped by remember { mutableStateOf<TappedMapThing?>(null) }
    val onFeatureTap: (MapFeatureTap) -> Unit = remember { { tap -> tappedThingOf(tap)?.let { tapped = it } } }
    // See MapOverlayContent.resumeTrackingRequestId's own doc comment — incremented alongside the
    // existing onLocateMe() call below, not instead of it: that call still drives the compass
    // strip's own one-shot position/elevation text, this drives the map's live GPS camera puck.
    var resumeTrackingRequestId by remember { mutableStateOf(0) }
    // See MapOverlayContent.resetOrientationRequestId's own doc comment.
    var resetOrientationRequestId by remember { mutableStateOf(0) }
    // J6c: the cluster's state and its measurements live in MapIconClusterState (AvailabilityMapIconCluster.kt),
    // shared with the tablet's map. The names below are aliases, so what this tab still reads (the bar's side,
    // the container's width, the nav's height, the legend chip's top) reads as it did.
    val cluster = rememberMapIconClusterState(clusterPosition, railPortEdge, punchHoleEdge)
    val landscapeCluster = cluster.landscape
    val isMapIconBarOnLeftSide by cluster::isOnLeftSide
    var mapContentBoxHeightPx by cluster::mapContentBoxHeightPx
    var mapContentBoxTopInRootPx by cluster::mapContentBoxTopInRootPx
    val mapIconClusterWidthPx by cluster::clusterWidthPx
    var mapBottomNavHeightPx by cluster::bottomNavHeightPx
    var legendChipTopPx by cluster::legendChipTopPx
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

    val context = LocalContext.current
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
            // The map's own viewport, never null — unlike uiState.region (only set once a real
            // search has run), so the map always has *something* to show rather than the earlier
            // revision's "choose a region" placeholder: the real search region once one exists,
            // otherwise the device's live location once locate-me above resolves one, otherwise a
            // fixed fallback while that's still pending. The project owner's own framing: the Maps
            // tab should already be showing a real map, centred on the user, the moment the app
            // opens — not a message asking them to search first.
            val located = (uiState.locateMeStatus as? LocateMeStatus.Located)?.location
            val displayRegion = uiState.region
                ?: located?.let { Region(lat = it.lat, lng = it.lng, radiusKm = JOURNAL_PICKER_DEFAULT_REGION.radiusKm) }
                ?: JOURNAL_PICKER_DEFAULT_REGION

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
            // "View on Map" from a List-tab row — see MapTab's own doc comment on the identical
            // filteredSightings/mapTaxonFilterLabel pair for why this filters uiState.sightings
            // itself rather than trusting uiState.forecast's own observationCount.
            val filteredSightings = when {
                !hasSearched -> emptyList()
                taxonFilter != null -> uiState.sightings.filter { it.taxonId == taxonFilter }
                else -> uiState.sightings
            }
            val mapTaxonFilterLabel = taxonFilter?.let { id ->
                val name = uiState.forecast?.entries?.firstOrNull { it.species.taxonId == id }?.species
                    ?.let { it.commonName ?: it.scientificName }
                    ?: filteredSightings.firstOrNull()?.let { it.commonName ?: it.scientificName }
                    ?: "this species"
                "$name (${filteredSightings.size})"
            }

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
            val trueHeading = rememberTrueHeading(compassProvider, computeTrueHeading, uiState.liveFix)
            // MGRS by default, the labelled decimal pair on tap — hoisted here from the strip's
            // own leaf (navigation-chrome dispatch) because the strip and the HUD now take turns
            // showing the coordinates: a format chosen while navigating must still be the format
            // the strip shows on exit (CLAUDE.md, UX defaults — user-set state that resets on its
            // own is a bug). Local state, not AvailabilityUiState: purely which of two always-
            // computable representations of the same fix to display, nothing the ViewModel or a
            // future session needs. Still resets when this tab unmounts, as it did before.
            var showDecimalDegrees by remember { mutableStateOf(false) }
            val onToggleCoordinateFormat = { showDecimalDegrees = !showDecimalDegrees }
            val compassStripTextMeasurer = rememberTextMeasurer()
            val compassStripLabelStyle = MaterialTheme.typography.labelMedium
            val compassStripDensity = LocalDensity.current
            // The strip's real height, measured on the strip itself where it is composed below (item 2). Not
            // compassStripClearance, which is one text line's height.
            var compassStripHeightPx by remember { mutableIntStateOf(0) }
            val compassStripClearance = remember(compassStripLabelStyle, compassStripDensity) {
                with(compassStripDensity) {
                    compassStripTextMeasurer.measure("Mg", compassStripLabelStyle).size.height.toDp()
                }
            }
            Box(
                modifier = modifier
                    .fillMaxSize()
                    // Feeds mapIconBarDisplayedOffsetPx's own clamp below — see that variable's own
                    // doc comment.
                    .onGloballyPositioned { coordinates ->
                        mapContentBoxHeightPx = coordinates.size.height.toFloat()
                        mapContentBoxTopInRootPx = coordinates.positionInRoot().y
                    },
            ) {
                mapSlot(
                    displayRegion,
                    MapOverlayContent(
                        sightings = filteredSightings,
                        plannedTrips = uiState.plannedTrips,
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = waypoints,
                        resumeTrackingRequestId = resumeTrackingRequestId,
                        resetOrientationRequestId = resetOrientationRequestId,
                        focusedObservationId = tapped.focusedObservationId,
                        focusedFeature = tapped.focusedFeature,
                        // Map layers L0b, B2 (owner: "Every saved record"): every saved find with a
                        // location, every ended track, every located album photo and every offline
                        // region, pending deletes left out, each visible by default.
                        keptTrackPolylines = mapLayers.records.trackPolylines,
                        findMarkers = mapLayers.records.findMarkers,
                        photoMarkers = mapLayers.records.photoMarkers,
                        offlineRegionCircles = mapLayers.records.offlineRegionCircles,
                        // J8-2: the shown entries' kept records, highlighted under their own glyphs.
                        journalHighlights = mapLayers.journalHighlights,
                    ),
                    renderMode.copy(onFeatureTap = onFeatureTap, cameraMemory = cameraMemory),
                    focusOverride,
                    {},
                    // Tapping the map restores chrome while fullscreen — decision #5 — AND dismisses
                    // the bubble below regardless of fullscreen state (a plain tap on empty map is
                    // its dismiss gesture). Since M1 a tap on a glyph is not a plain tap (owner's
                    // ruling 1, "Bubble only"), so it opens its bubble and does neither.
                    {
                        if (isFullscreen) onToggleFullscreen()
                        tapped = null
                    },
                    { sighting, screenPosition, bearingDeg ->
                        tapped = TappedMapThing(MapBubbleTarget.SightingTarget(sighting), screenPosition, bearingDeg)
                    },
                    { location -> cameraCenter = location },
                    Modifier.fillMaxSize(),
                )
                // Composed right after mapSlot — see searchBarSlot's own doc comment for why this
                // exact nesting (a direct sibling of the map's own AndroidView content, inside
                // this Box) is what makes its translucency actually work.
                // The strip's own measured height goes with it, so a notice in the slot can be placed below the strip
                // (item 2). Zero while the strip is not composed.
                // Owner's ruling (b), continuation 2026-09-28-172: in the landscape L, a notice on the same side as the L is inset on
                // that side by 8 + the L's measured width + 8, as the legend makes room (legendEndPadding below). Elsewhere none.
                val noticeAnchoredLeft = punchHoleEdge == ScreenEdge.Left
                val noticeInsetDp = MAP_ICON_BAR_EDGE_INSET + with(compassStripDensity) { cluster.clusterWidthPx.toDp() } + Spacing.sm
                val searchNoticeInset = when {
                    !landscapeCluster || cluster.isOnLeftSide != noticeAnchoredLeft -> SearchNoticeInset.None
                    cluster.isOnLeftSide -> SearchNoticeInset(left = noticeInsetDp)
                    else -> SearchNoticeInset(right = noticeInsetDp)
                }
                CompositionLocalProvider(LocalSearchNoticeInset provides searchNoticeInset) {
                    searchBarSlot(with(compassStripDensity) { compassStripHeightPx.toDp() })
                }
                // minY = compassStripClearance, a real measurement of the strip's own type style: the
                // strip is composed after this in the same Box (so its own controls win any overlap)
                // and is full-width against the map's top edge, so a glyph tapped near the top would
                // otherwise anchor a bubble under that strip's band, where its taps (the close button's
                // included) would never reach it (CLAUDE.md, the Surface pitfall). See
                // compassStripClearance's own comment for why it is a one-time text measurement.
                //
                // Back closes the bubble (M1), except while something above the map owns Back: the
                // Tools drawer (intent 2026-09-28-28's precedence), the add menu or a picker.
                MapBubbleLayer(
                    tapped = tapped,
                    onDismiss = { tapped = null },
                    sources = bubbleSources,
                    forecast = renderMode.forecast,
                    onViewSightingOnINaturalist = { sighting ->
                        launchINaturalistObservation(context, sighting.observationId)
                        tapped = null
                    },
                    minY = topInset + compassStripClearance,
                    backEnabled = !isDrawerOpen && pendingAction == null && !pickingSearchLocation && !showActionMenu,
                )
                // The icon cluster (the bar and the record | return pill, their handles, drag, snap and clamps):
                // MapIconCluster, shared with the tablet's map (J6c). Composed *before* CompassElevationStrip,
                // not after: composition order is paint and hit-test order for overlapping siblings in this
                // Box, and MapIconBar's Surface intercepts touches across its full bounds, which on a short
                // viewport reach up into the strip's row; the strip's own control must win any overlap
                // (AvailabilityScreenMapIconStackTest's touch-interaction test on a w360dp-h640dp viewport).
                val phoneBar: @Composable (Modifier, Color, Dp, Boolean) -> Unit = { barModifier, barFill, barRowSpacing, barFullSquareHits ->
                    MapIconBar(
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        onLocateMe = {
                            resumeTrackingRequestId++
                            onLocateMe()
                        },
                        onResetOrientation = { resetOrientationRequestId++ },
                        mapMode = mapMode,
                        onOpenLayers = { showLayersSheet = true },
                        onAdd = {
                            // No location to grab any more — the button just opens
                            // the menu; the location comes from
                            // CentrePinLocationPickerOverlay's own camera tracking
                            // once a choice is made. See this function's own doc
                            // comment.
                            showActionMenu = true
                        },
                        fillColor = barFill,
                        rowSpacing = barRowSpacing,
                        fullSquareHits = barFullSquareHits,
                        modifier = barModifier,
                    )
                }
                MapIconCluster(
                    state = cluster,
                    isFullscreen = isFullscreen,
                    // The cluster cannot rise above where SearchDropdown itself starts: topInset (about the
                    // search bar's height) plus the strip's own clearance (icon-bar-drag-refinements, Item 4).
                    // Owner's ruling (a), continuation 2026-09-28-172 ("never above the search bar's bottom"): in the landscape L the limit is
                    // topInset, the search bar's own bottom, without the strip clearance (the compass strip is in the other corner there,
                    // nothing else is drawn in that band beside the notice and the chips, which make room for the L, and the SearchDropdown
                    // starts below it); the L pushes down to it as well as up. Portrait keeps topInset + the clearance.
                    topLimitPx = with(compassStripDensity) { (if (landscapeCluster) topInset else topInset + compassStripClearance).toPx() },
                    noticeBottomPx = with(compassStripDensity) { searchNoticeBottom.toPx() },
                    controlsPadding = controlsPadding,
                    bar = { barModifier -> phoneBar(barModifier, mapIconClusterChildColor(), Spacing.xs, false) },
                    // Landscape L: the bar's rows 48 dp apart with no end padding (240 dp), one layer at the standing 0.8 fill, every row
                    // taking touches across its full 48 x 48 square (owner's "A" and ruling (d), continuations -160 and -172).
                    landscapeBar = { barModifier -> phoneBar(barModifier, Color.Unspecified, MAP_ICON_BAR_LANDSCAPE_ROW_SPACING, true) },
                    pill = { onLeftSide ->
                        // Composed whenever MapIconBar is (regardless of isRecording — record start/stop must
                        // stay reachable before the first recording starts; isRecording flows in as a plain
                        // parameter, see TrailheadControls' own doc comment, not a presence check).
                        TrailheadControls(
                            isRecording = isRecording,
                            onToggleRecording = onToggleRecording,
                            returnToStart = returnToStart,
                            isReturning = isReturning,
                            isOffTrack = isOffTrack,
                            onToggleReturning = onToggleReturning,
                            distanceUnit = uiState.distanceUnit,
                            onLeftSide = onLeftSide,
                        )
                    },
                    // Landscape L: the pill turned horizontal (record under the bar's column, return inboard, 96 x 48), one layer at the
                    // standing 0.8 fill, both buttons taking touches across their full 48 x 48 squares.
                    landscapePill = { onLeftSide ->
                        TrailheadControls(
                            isRecording = isRecording,
                            onToggleRecording = onToggleRecording,
                            returnToStart = returnToStart,
                            isReturning = isReturning,
                            isOffTrack = isOffTrack,
                            onToggleReturning = onToggleReturning,
                            distanceUnit = uiState.distanceUnit,
                            onLeftSide = onLeftSide,
                            horizontal = true,
                            fillColor = mapIconChromeFillColor(),
                            rowSpacing = MAP_ICON_BAR_LANDSCAPE_ROW_SPACING,
                        )
                    },
                )
                // Not composed at all while navigating (navigation-chrome dispatch, item 1) — the
                // HUD below carries the heading, elevation and coordinates then, and on device
                // both showing meant the heading appeared three times. Removed from composition
                // rather than made invisible: this strip's leaf is what reads the heading State
                // at sensor rate, and an invisible strip would still be recomposing at 16 Hz
                // alongside the HUD doing the same work. Gated on isNavigating, never isReturning,
                // so stage two's picker cannot bring it back by accident — see AvailabilityScreen's
                // own isNavigating doc comment.
                if (!isNavigating) {
                    CompassElevationStrip(
                        heading = trueHeading,
                        elevationMeters = uiState.liveAltitudeMeters,
                        location = uiState.liveLocation,
                        showDecimalDegrees = showDecimalDegrees,
                        onToggleCoordinateFormat = onToggleCoordinateFormat,
                        // Full width, "just below" SearchEntryBar rather than a narrow floating pill
                        // with margins on both sides, per the project owner's own redesign call — topInset
                        // is how that clearance reaches here now that the bar composes as a real overlay
                        // in the same Box as this tab's own content (compactMainScaffold's own call
                        // site) instead of a sibling Column entry above it; 0.dp (this parameter's own
                        // default) reproduces the old flush-against-the-map-top behavior exactly.
                        modifier = if (railPortEdge != null) {
                            // Landscape B2 (S4): the top corner on the rail side, below the
                            // status bar only (the Scaffold's top inset), not below the search
                            // bar, which is on the other side now; content-width.
                            Modifier
                                .align(if (railPortEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                .padding(controlsPadding)
                        } else {
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(controlsPadding)
                                .fillMaxWidth()
                                .padding(top = topInset)
                                // After the padding, so it is the strip's own height (item 2).
                                .onSizeChanged { compassStripHeightPx = it.height }
                        }.mapKeepOut(MapKeepOutIds.TOP_STRIP),
                        contentWidth = railPortEdge != null,
                    )
                    DisposableEffect(Unit) { onDispose { compassStripHeightPx = 0 } }
                }

                // Below the compass strip (topInset + compassStripClearance as top padding), same
                // reasoning as AnchoredAtScreenPoint's own minY — the strip's Surface intercepts
                // touches across its full width, so a chip placed underneath it would have its own
                // "Show all species" tap silently swallowed the same way a bubble anchored there
                // would. topInset itself (see this composable's own doc comment) clears whatever
                // chrome floats above the strip too — SearchEntryBar, on the Map tab.
                //
                // J8-3 (owner: "Top, by the species chip (Recommended)"): the journal-entries chip sits in
                // the row with the taxon chip, after it, at the same place in each window. A FlowRow sized
                // to its chips (it draws nothing and takes no touches itself, so the map keeps every touch
                // around them: CLAUDE.md, the Surface pitfall), so two chips wider than the room wrap to a
                // second line instead of running off the screen. It holds whichever chips there are.
                val shownJournalEntries = mapLayers.journalHighlights.shownEntries
                if (mapTaxonFilterLabel != null || shownJournalEntries.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        // Part 1 layout fixes (the owner's "Option B for the chips", planner message 2026-09-29-07): in
                        // short landscape the wrapped second line follows the first with no gap, so the drawn chips of a
                        // two-line row end above the window's central third. J8's chip keeps its 48 dp layout box (its
                        // drawn pill is centred in it, 8 dp of margin above and below), so the margin, not the chip,
                        // takes the space. Portrait keeps its 4 dp.
                        verticalArrangement = Arrangement.spacedBy(if (punchHoleEdge != null && landscapeSearchWidth != null) 0.dp else Spacing.xs),
                        modifier = if (punchHoleEdge != null && landscapeSearchWidth != null) {
                            // Landscape B2 (S3): directly under the search bar (the strip is in
                            // the rail corner now, not under the bar), in a column the bar's own
                            // width on the punch-hole side, aligned to the bar's start.
                            //
                            // Part 1 layout fixes, item 7 (the owner's "1 A", planner message 2026-09-29-04): the
                            // row aligns to the bar's end away from the cluster's current side, and its width is
                            // capped at the bar's width less the cluster's edge inset, measured width and the gap
                            // beside it, so two chips that do not fit wrap onto two lines instead of reaching
                            // under the cluster. Both follow the cluster when it is dragged or snapped across.
                            // Planner message 2026-09-29-05: the cap only where the cluster sits under the bar's
                            // reach (it is on the bar's side); elsewhere the chips have room and stay on one line.
                            val clusterUnderBar = isMapIconBarOnLeftSide == (punchHoleEdge == ScreenEdge.Left)
                            val clusterColumnDp = MAP_ICON_BAR_EDGE_INSET + with(LocalDensity.current) { mapIconClusterWidthPx.toDp() } + Spacing.sm
                            Modifier
                                .align(if (punchHoleEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                .padding(controlsPadding)
                                .padding(top = topInset + Spacing.sm)
                                .width(landscapeSearchWidth)
                                .wrapContentWidth(if (isMapIconBarOnLeftSide) AbsoluteAlignment.Right else AbsoluteAlignment.Left)
                                .widthIn(max = if (clusterUnderBar) (landscapeSearchWidth - clusterColumnDp).coerceAtLeast(0.dp) else landscapeSearchWidth)
                        } else {
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(controlsPadding)
                                .padding(top = topInset + compassStripClearance + Spacing.sm)
                        }.mapKeepOut(MapKeepOutIds.CHIPS),
                    ) {
                        mapTaxonFilterLabel?.let { label -> TaxonMapFilterChip(label = label, onClear = onClearTaxonFilter) }
                        if (shownJournalEntries.isNotEmpty()) {
                            JournalEntriesMapChip(
                                entries = shownJournalEntries,
                                onHide = mapLayers.onHideJournalEntry,
                                onHideAll = mapLayers.onHideAllJournalEntries,
                            )
                        }
                    }
                }

                // Navigation HUD stage one. Composed after the cluster (so its own exit wins any
                // overlap with a cluster dragged up to its upward bound) and before the nav
                // below (so the nav keeps winning its own band) — see NavigationHud's own doc
                // comment for the full mounting reasoning. Gated on the same isNavigating that
                // removes the compass strip above, so the two are never on screen together; in
                // stage one that is the return mode (TrackRecordingViewModel.startReturn). Top
                // padding is topInset alone — with the strip gone there is nothing above this
                // panel but the search bar, whose fullscreen slide it follows the way the strip
                // does; compassStripClearance stays in the taxon chip's and bubble's paths only
                // because those still clear the strip while not navigating. Never touches mapSlot.
                if (isNavigating) {
                    NavigationHud(
                        heading = trueHeading,
                        liveFix = uiState.liveFix,
                        target = navigationTarget,
                        distanceUnit = uiState.distanceUnit,
                        pathHomeMeters = pathHomeMeters,
                        currentTime = currentTime,
                        showDecimalDegrees = showDecimalDegrees,
                        onToggleCoordinateFormat = onToggleCoordinateFormat,
                        onExit = onToggleReturning,
                        modifier = if (railPortEdge != null) {
                            // Landscape B2 (S4): the top corner on the rail side, below the
                            // status bar only, at most 360dp wide.
                            Modifier
                                .align(if (railPortEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                                .padding(controlsPadding)
                                .widthIn(max = LANDSCAPE_HUD_MAX_WIDTH)
                                .fillMaxWidth()
                        } else {
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(controlsPadding)
                                .fillMaxWidth()
                                .padding(top = topInset)
                        }.mapKeepOut(MapKeepOutIds.TOP_STRIP),
                    )
                }

                // Map layers L0b, B4 (owner's ruling 5, "Bottom-right, above the 'i'"): the legend chip,
                // shown only while a colour field is visible. In the bottom-end corner, above MapLibre's
                // "i" (LEGEND_ATTRIBUTION_CLEARANCE) and above the nav in portrait (renderMode.bottomInset,
                // the attribution caption's own inset: the nav's height, or the system bar's in
                // fullscreen), and inside controlsPadding, so it stays clear of the landscape rail on
                // either edge. The cluster keeps clear of it through its clamp above (Q4). Composed
                // with the ambient chrome, before the nav and the modal overlays. Its placement depends
                // on real insets Robolectric reports as zero: device-only.
                // Part 1 layout fixes, items 1 and 2 in landscape (the owner's "2 A", planner message
                // 2026-09-28-109): in a short landscape window, with the cluster on the legend's side, the
                // cluster's column reaches the corner the legend sits in, so the legend moves just inboard of
                // it (the cluster's edge inset and measured width, plus the portrait gap), bottom-aligned as
                // before, collapsed or expanded. Otherwise it is in its corner as it always was.
                val legendEndPadding = if (landscapeCluster && !isMapIconBarOnLeftSide) {
                    MAP_ICON_BAR_EDGE_INSET + with(LocalDensity.current) { mapIconClusterWidthPx.toDp() } + Spacing.sm
                } else {
                    Spacing.sm
                }
                mapLegendFor(renderMode.layers, MAP_LAYER_REGISTRY, COLOUR_FIELDS, mapLayers.cellsShown)?.let { legend ->
                    DisposableEffect(Unit) { onDispose { legendChipTopPx = null } }
                    MapLegendChip(
                        legend = legend,
                        expanded = mapLayers.legendExpanded,
                        onExpandedChange = mapLayers.onLegendExpandedChange,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(controlsPadding)
                            .padding(end = legendEndPadding, bottom = renderMode.bottomInset + LEGEND_ATTRIBUTION_CLEARANCE)
                            .mapKeepOut(MapKeepOutIds.LEGEND)
                            .onGloballyPositioned { coordinates ->
                                legendChipTopPx = coordinates.positionInRoot().y - mapContentBoxTopInRootPx
                            },
                    )
                }

                // Fullscreen-fixes dispatch, Item 1 (third design). Composed here — after the
                // ambient chrome above (MapIconBar/CompassElevationStrip/TrailheadControls/
                // TaxonMapFilterChip, none of which reach this bar's own bottom band) but *before*
                // the modal overlays below (AddActionTile/MapLayersSheet/CentrePinLocationPickerOverlay)
                // — deliberately, not composed last: this Box now extends the full screen height in
                // both fullscreen states (CompactMapTab's own doc comment), so those modals'
                // fillMaxSize() content now reaches all the way down into this bar's own screen
                // region too. Composing this nav after them (drawn on top, hit-tested first) was
                // tried first and is a confirmed, reproducible regression — it silently swallowed
                // CentrePinLocationPickerOverlay's own "OK" confirm tap, caught by
                // AvailabilityScreenTripPlanningFlowTest's own trip-planning-flow tests going from
                // passing to reliably failing (not flaky) on exactly that ordering, the same class
                // of miss CLAUDE.md's own "Known pitfalls" already documents twice over for chrome
                // composed over a map. selectedTab is hardcoded to CompactTab.MAP — this composable
                // is only ever shown for that tab, so there's nothing else it could mean here. The
                // other three tabs still render this same composable, unconditionally opaque, from
                // compactMainScaffold's own bottomBar slot instead (that call site's own doc
                // comment) — this overlay and that one are the two places ForagerBottomNav renders,
                // never both for the same tab at once.
                //
                // Slides down and off the bottom edge while fullscreen — fullscreen-fixes dispatch,
                // Item 2 ("slide the chrome away instead of cutting it"), a deliberate change from
                // the crossfade-to-80%-opacity this bar used before: fullscreen is exited via
                // MapIconBar's own fullscreen control, never via this nav, so the nav is not the
                // way out and can safely leave the screen entirely. 80% opacity outside fullscreen
                // (the owner's own call, from a screenshot): it floats over the map whenever it's
                // on screen at all, so the standing 80%-over-the-map rule applies to it the same as
                // to every other piece of map chrome here — see the containerColor parameter's
                // own doc comment for the two prior flips of this exact value. A pure Box-child overlay,
                // same confirmed-safe reasoning as SearchEntryBar's own slide above — animating it
                // has no bearing on this Box's own size.
                // Landscape B1: not composed at all in a short landscape window, where the rail
                // beside this tab replaces it (compactMainScaffold's showRail) — an `if`, not
                // `visible`, so turning the phone does not play this bar's slide-out in landscape.
                if (showBottomNav) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isFullscreen,
                        enter = slideInVertically(animationSpec = MotionTokens.navigationMotionSpec()) { fullHeight -> fullHeight },
                        exit = slideOutVertically(animationSpec = MotionTokens.navigationMotionSpec()) { fullHeight -> fullHeight },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        ForagerBottomNav(
                            selectedTab = CompactTab.MAP,
                            // 80%, the standing opacity for chrome over the map — see this bar's own
                            // containerColor doc comment.
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA),
                            isDrawerOpen = isDrawerOpen,
                            onTabSelected = onBottomNavTabSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .mapKeepOut(MapKeepOutIds.BOTTOM_NAV)
                                .onGloballyPositioned { coordinates ->
                                    mapBottomNavHeightPx = coordinates.size.height.toFloat()
                                    onBottomNavHeightMeasured(coordinates.size.height.toFloat())
                                },
                        )
                    }
                }

                // Landscape B1 (R12/R13 revised): the rail, overlaid on the port edge in exactly
                // this layer — after the ambient chrome, before the modal overlays below, for the
                // same reasons the bottom bar above sits here. 80% over the map, like the bar. The
                // map under it keeps its size whether it shows or not; the controls are padded
                // clear of it by its measured width (controlsPadding). Absent in fullscreen, with
                // no animation — the slide toward the port edge is B2's (P10).
                // Landscape B2 (S7): on entering fullscreen the rail slides toward the port edge,
                // off the window, and back on exit, on the theme's motionScheme spatial spec (the
                // nav's own navigationMotionSpec, defaultSpatialSpec) — no ad-hoc tween. A pure
                // translation of a Box child: the map's size never changes. The rail leaves the
                // tree once its exit animation ends (AnimatedVisibility), as B1's absence did.
                if (railPortEdge != null) {
                    val railSlideOffset: (Int) -> Int = { fullWidth -> if (railPortEdge == ScreenEdge.Left) -fullWidth else fullWidth }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isFullscreen,
                        enter = slideInHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), initialOffsetX = railSlideOffset),
                        exit = slideOutHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), targetOffsetX = railSlideOffset),
                        modifier = Modifier.align(if (railPortEdge == ScreenEdge.Left) Alignment.CenterStart else Alignment.CenterEnd),
                    ) {
                        ForagerNavigationRail(
                            selectedTab = CompactTab.MAP,
                            isDrawerOpen = isDrawerOpen,
                            onTabSelected = onBottomNavTabSelected,
                            portEdge = railPortEdge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = MAP_CHROME_OVER_MAP_ALPHA),
                            modifier = Modifier.mapKeepOut(MapKeepOutIds.RAIL).onGloballyPositioned { coordinates ->
                                onRailWidthMeasured(coordinates.size.width.toFloat())
                            },
                        )
                    }
                }

                // Inside this Box, not alongside it, so it can align near the add button's own
                // corner of the icon stack above — see AddActionTile's doc comment for why this
                // reads as opening "from" that button rather than as a centered system dialog.
                AddActionTile(
                    visible = showActionMenu,
                    onPlanTrip = {
                        showActionMenu = false
                        pendingAction = PendingMapAction.PLAN_TRIP
                    },
                    onLogFind = {
                        showActionMenu = false
                        pendingAction = PendingMapAction.LOG_FIND
                    },
                    onDropWaypoint = {
                        showActionMenu = false
                        pendingAction = PendingMapAction.DROP_WAYPOINT
                    },
                    onDismiss = { showActionMenu = false },
                    // Landscape B1: the same padding as the cluster it is anchored to, so the two
                    // share one frame.
                    modifier = Modifier.fillMaxSize().padding(controlsPadding),
                    // Expanded-panels dispatch: anchored to the bar's live side and drag offset
                    // (see mapIconBarPanelAnchorOffset above), plus this panel's own row.
                    anchor = cluster.sideAlignment,
                    anchorOffset = cluster.panelAnchorOffset(LocalDensity.current).let { anchor ->
                        DpOffset(x = anchor.x, y = anchor.y + (if (landscapeCluster) ADD_TILE_ANCHOR_OFFSET_LANDSCAPE else ADD_TILE_ANCHOR_OFFSET))
                    },
                    growsFrom = if (isMapIconBarOnLeftSide) Alignment.BottomStart else Alignment.BottomEnd,
                )

                // Map layers L0b, B1: the Layers sheet, in place of the basemap-only popover this row
                // used to open. A modal bottom sheet in its own window, so it needs no anchor to the
                // cluster and no padding for the rail.
                if (showLayersSheet) {
                    MapLayersSheet(
                        mapMode = mapMode,
                        onMapModeSelected = onMapModeSelected,
                        overlays = MAPS_TAB_OVERLAYS,
                        colourFields = mapLayers.listedColourFields,
                        state = mapLayers.stored,
                        onVisibilityChanged = mapLayers.onVisibilityChanged,
                        onOpacityChanged = mapLayers.onOpacityChanged,
                        onColourFieldMoved = mapLayers.onColourFieldMoved,
                        onDismiss = { showLayersSheet = false },
                    )
                }

                // Owner finding on device: the OK/Cancel row sat under the app's nav (and under
                // Android's own navigation bar in fullscreen), because this Box spans the full
                // screen height. Outside fullscreen the nav's real measured height — which already
                // includes the system bar it consumes (CLAUDE.md, "Robolectric reports zero window
                // insets") — is what's underneath; in fullscreen the nav has slid away and only
                // the system navigation bar is. The fullscreen half is device-only by
                // construction: Robolectric reports that inset as zero.
                // Dispatch 2026-09-28-104, item 5: in the rail layout the nav-bar inset applies outside
                // fullscreen too (no bottom nav is measured there, so mapBottomNavHeightPx is zero), and the
                // row also takes controlsPadding (below) so it clears the rail. Device-only, as above.
                val centrePinConfirmBottomInset = if (isFullscreen || railPortEdge != null) {
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                } else {
                    with(LocalDensity.current) { mapBottomNavHeightPx.toDp() }
                }
                if (pendingAction != null) {
                    CentrePinLocationPickerOverlay(
                        onConfirm = {
                            when (pendingAction) {
                                PendingMapAction.PLAN_TRIP -> pendingTripLocation = cameraCenter
                                PendingMapAction.LOG_FIND -> onLogFindHere(cameraCenter)
                                PendingMapAction.DROP_WAYPOINT -> pendingWaypointLocation = cameraCenter
                                null -> Unit
                            }
                            pendingAction = null
                        },
                        onCancel = { pendingAction = null },
                        modifier = Modifier.fillMaxSize(),
                        bottomInset = centrePinConfirmBottomInset,
                        rowPadding = controlsPadding,
                        // The map's own night (the renderMode handed to mapSlot above), so the pin
                        // follows Night Maps (colour build C2 (e)).
                        night = renderMode.night,
                    )
                } else if (pickingSearchLocation) {
                    // AdvancedSearchDropdown's own "Set on map" — same overlay, same already-shown
                    // map, same cameraCenter this tab already tracks via onCameraIdle; see this
                    // param's own doc comment for why it isn't a second picker.
                    CentrePinLocationPickerOverlay(
                        onConfirm = { onSearchLocationPicked(cameraCenter) },
                        onCancel = onCancelSearchLocationPick,
                        modifier = Modifier.fillMaxSize(),
                        bottomInset = centrePinConfirmBottomInset,
                        rowPadding = controlsPadding,
                        // The map's own night (the renderMode handed to mapSlot above), so the pin
                        // follows Night Maps (colour build C2 (e)).
                        night = renderMode.night,
                    )
                }
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

/** Landscape B2 (S4): the navigation HUD's width cap in the rail-side top corner. */
private val LANDSCAPE_HUD_MAX_WIDTH = 360.dp
