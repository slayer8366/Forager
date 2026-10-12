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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.map.LocalMapPosition
import com.zynergylabs.forager.app.ui.map.MapBubbleKind
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.log.RecordDetailsTarget
import com.zynergylabs.forager.app.ui.map.RouteOnMap
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.ui.map.rememberNavigationFacing
import com.zynergylabs.forager.app.ui.map.NavigationViewRequest
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
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
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clipToBounds
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion
import com.zynergylabs.forager.app.ui.motion.MapPopUp
import com.zynergylabs.forager.app.ui.motion.PopUpPivot
import com.zynergylabs.forager.app.ui.motion.isOnScreen
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import com.zynergylabs.forager.app.ui.motion.rememberPopUpState
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerPopUp
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
 * unrequested reset is a bug unless the exception is stated for the case. Persisting across app
 * restarts was a separate per-case decision; since RECORD -711 the side and height persist (below),
 * and the minimised flags do not.
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
 *
 * **Side and height persist across restarts since RECORD -711** (the owner: "have the map icon bar persist
 * between restarts so left handed users don't need to change it every time they open the app", then
 * "Side and height (Recommended)"); the minimised flags stay session-only. [placement] reads the four
 * stored values out, [applyPlacement] puts a stored set back once at launch, and [settledCount] counts
 * the drags that have ended, which is what `AvailabilityScreen` stores on. Until [placementApplied] is
 * true the cluster is not drawn at all (`MapIconCluster` returns early), so a restored left side is never
 * preceded by a frame on the right. A holder made with nothing to wait for starts applied.
 */
internal class MapIconClusterPositionState(awaitingPlacement: Boolean = false) {
    var userChosenOffsetPx by mutableStateOf(0f)
    val displayedOffsetPx = Animatable(0f)
    var isOnLeftSide by mutableStateOf(false)
    var isMinimized by mutableStateOf(false)

    // Landscape B2 (S6): a short landscape window's own cluster position, separate from the
    // portrait one above so that turning the phone neither carries a portrait drag into landscape
    // nor loses it on the way back. The side is stored as port or punch-hole, not left or right,
    // and translated to a window side from the current port edge where the cluster is anchored,
    // so turning between ROTATION_90 and ROTATION_270 keeps the cluster on the same device edge.
    // Defaults to the punch-hole side. Side and height persist like the portrait ones (RECORD -711); minimised does not.
    var landscapeOnPortSide by mutableStateOf(false)
    var landscapeUserChosenOffsetPx by mutableStateOf(0f)
    val landscapeDisplayedOffsetPx = Animatable(0f)
    var landscapeIsMinimized by mutableStateOf(false)

    /** False until a stored placement has been applied; the cluster is not drawn before. See the class doc. */
    var placementApplied by mutableStateOf(!awaitingPlacement)
        private set

    /** Increments each time a drag of the cluster ends (or is cancelled after moving it): the moment its side and height are stored. */
    var settledCount by mutableIntStateOf(0)
        private set

    fun onDragSettled() {
        settledCount++
    }

    /**
     * Set by [applyPlacement] for each orientation, cleared by the cluster once it has clamped that
     * orientation's restored height against measured bounds: the first fit of a restored height is a
     * snap, not the glide a bounds change gets, so a height stored in a taller window does not visibly
     * slide in from beyond the current one's limits.
     */
    var snapRestoredPortrait = false
    var snapRestoredLandscape = false

    /** The side and height of both orientations as stored values, the heights in dp. */
    fun placement(density: Density): MapIconClusterPlacement = with(density) {
        MapIconClusterPlacement(
            portraitOnLeft = isOnLeftSide,
            portraitOffsetDp = userChosenOffsetPx.toDp().value,
            landscapeOnPortSide = landscapeOnPortSide,
            landscapeOffsetDp = landscapeUserChosenOffsetPx.toDp().value,
        )
    }

    /**
     * Puts a stored [placement] back, once: the sides as stored, the heights as the user-chosen memory and
     * as what is drawn. The heights are not clamped here, because no bounds are measured yet; the cluster
     * clamps what it draws against the window it is in (`MapIconCluster`'s bounds effect), with a snap the
     * first time ([snapRestoredPortrait]). The memory keeps the stored height, as it keeps a drag's height
     * through a fullscreen change, so a larger window later gets it back. Does nothing once applied.
     */
    suspend fun applyPlacement(placement: MapIconClusterPlacement, density: Density) {
        if (placementApplied) return
        val portraitPx = with(density) { placement.portraitOffsetDp.dp.toPx() }
        val landscapePx = with(density) { placement.landscapeOffsetDp.dp.toPx() }
        isOnLeftSide = placement.portraitOnLeft
        userChosenOffsetPx = portraitPx
        displayedOffsetPx.snapTo(portraitPx)
        landscapeOnPortSide = placement.landscapeOnPortSide
        landscapeUserChosenOffsetPx = landscapePx
        landscapeDisplayedOffsetPx.snapTo(landscapePx)
        snapRestoredPortrait = true
        snapRestoredLandscape = true
        placementApplied = true
    }
}

/** [awaitingPlacement]: `true` only for a host that will apply a stored placement (`AvailabilityScreen`); see [MapIconClusterPositionState]. */
@Composable
internal fun rememberMapIconClusterPositionState(awaitingPlacement: Boolean = false): MapIconClusterPositionState =
    remember { MapIconClusterPositionState(awaitingPlacement) }

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
    // RECORD -739: the grouped inputs this function still reads, unpacked under their old parameter names (RECORD -741: the rest are
    // read in the parts that use them, below).
    val isNavigating = navigation.isNavigating
    val navigationTarget = navigation.navigationTarget
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
    var resumeTrackingRequestId by remember { mutableStateOf(0) }
    // See MapOverlayContent.resetOrientationRequestId's own doc comment.
    var resetOrientationRequestId by remember { mutableStateOf(0) }
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
            // Dispatch 2026-09-28-510: at the best position there is (AvailabilityUiState.headingFix), so the
            // HUD's needle and the strip's heading work while the position is approximate or last known.
            // RECORD -761 (the owner: "Keep last reading (Recommended)"): starts from the last reading, kept with the camera above the tab.
            val trueHeading = rememberTrueHeading(compassProvider, computeTrueHeading, uiState.headingFix, cameraMemory.heading, currentTime::nowEpochMillis)
            // Dispatch 2026-09-28-510: what is shown in place of GPS, for the strip. Read only in its leaf.
            val positionNote = rememberPositionNote(LocalMapPosition.current, currentTime)
            // Dispatch 2026-09-28-430: which way the map faces while navigating, from the same heading.
            val navigationFacing by rememberNavigationFacing(trueHeading, isNavigating, currentTime)
            // Dispatch -497: where the start is once the walker has arrived, by the same rule the HUD's
            // "Arrived" reads (arrivedAtStart); null otherwise.
            // RECORD -845: held while the display holds "Arrived" after a return ended on arrival, whatever the fix says.
            val arrivedAt = if (isNavigating && navigationTarget != null && (navigation.isArrivalHeld || arrivedAtStart(uiState.liveFix, navigationTarget, currentTime.nowEpochMillis()))) {
                com.zynergylabs.forager.app.domain.model.LatLng(navigationTarget.lat, navigationTarget.lng)
            } else {
                null
            }
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
            // RECORD -741: the three measured sizes are handed to CompactMapTopStrip as State objects.
            val compassStripHeightState = remember { mutableIntStateOf(0) }
            val compassStripHeightPx by compassStripHeightState
            // RECORD -729: the strip's measured height in a landscape window (the search bar is given it); 0 elsewhere.
            val landscapeStripHeightState = remember { mutableIntStateOf(0) }
            // RECORD -732: and its measured width (the scaffold gives the bar the rest of the room); 0 elsewhere.
            val landscapeStripWidthState = remember { mutableIntStateOf(0) }
            val compassStripClearance = remember(compassStripLabelStyle, compassStripDensity) {
                with(compassStripDensity) {
                    compassStripTextMeasurer.measure("Mg", compassStripLabelStyle).size.height.toDp()
                }
            }
            // RECORD -709 (the owner: "Follow the strip's real height (Recommended)"): what sits below the strip in portrait (the
            // observation bubble's top, the icon bar's drag limit, the taxon and journal chips; the search dropdown, in the
            // scaffold, through searchBarSlot) clears the strip's measured height, not one text line. Back by's three-dot button
            // (dispatch 2026-09-28-645) gave the strip a 36 dp floor, twice that line, and its sundown and Back by lines grow it
            // too. The one-line clearance stays the floor, and is what is used while the strip is not measured: before its first
            // layout, while navigating (the display replaces it), and in a landscape window, where the strip sits in the other
            // corner and is not measured. The comment above records a regression from reading a measured height back for the
            // bubble's minY; the strip's measured height was already read here for the search notice (searchBarSlot), and the
            // test that caught that regression ("tapping elsewhere on the map dismisses the observation bubble") is @Ignore'd
            // for an unrelated harness reason, so whether the regression returns is a device item.
            val compassStripBottomClearance = with(compassStripDensity) { compassStripHeightPx.toDp() }.coerceAtLeast(compassStripClearance)
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
                    minY = topInset + compassStripBottomClearance,
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
                    topLimitPx = with(compassStripDensity) { (if (landscapeCluster) searchBarBottom else topInset + compassStripBottomClearance).toPx() },
                    noticeBottomPx = with(compassStripDensity) { searchNoticeBottom.toPx() },
                    controlsPadding = controlsPadding,
                )
                // The compass strip and the landscape join (RECORD -741: CompactMapTopStrip, moved out unchanged; its comments are there).
                CompactMapTopStrip(
                    uiState = uiState,
                    navigation = navigation,
                    landscape = landscape,
                    controlsPadding = controlsPadding,
                    topInset = topInset,
                    isFullscreen = isFullscreen,
                    showDecimalDegrees = showDecimalDegrees,
                    onToggleCoordinateFormat = onToggleCoordinateFormat,
                    recordingSundownLine = recordingSundownLine,
                    quickSettings = quickSettings,
                    trueHeading = trueHeading,
                    positionNote = positionNote,
                    sundownClock = sundownClock,
                    compassStripHeight = compassStripHeightState,
                    landscapeStripHeight = landscapeStripHeightState,
                    landscapeStripWidth = landscapeStripWidthState,
                )

                // The chips under the strip (RECORD -741: CompactMapChips, moved out unchanged; its comments are there).
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

                // The navigation display (RECORD -741: CompactMapNavigationDisplay, moved out unchanged; its comments are there).
                CompactMapNavigationDisplay(
                    uiState = uiState,
                    navigation = navigation,
                    landscape = landscape,
                    cluster = cluster,
                    controlsPadding = controlsPadding,
                    topInset = topInset,
                    trueHeading = trueHeading,
                    navigationFacing = navigationFacing,
                    currentTime = currentTime,
                    showDecimalDegrees = showDecimalDegrees,
                    onToggleCoordinateFormat = onToggleCoordinateFormat,
                    recordingSundownLine = recordingSundownLine,
                    quickSettings = quickSettings,
                    sundownClock = sundownClock,
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

/** The map itself ([mapSlot] and what it draws), moved out of [CompactMapTab] unchanged (RECORD -741); [fanBackEnabled] is worked out there. */
@Composable
private fun CompactMapMapSlot(
    mapSlot: MapSlot,
    displayRegion: Region,
    filteredSightings: List<Sighting>,
    uiState: AvailabilityUiState,
    recording: CompactMapRecording,
    navigation: CompactMapNavigation,
    arrivedAt: LatLng?,
    resumeTrackingRequestId: Int,
    resetOrientationRequestId: Int,
    tapped: TappedMapThing?,
    onTapped: (TappedMapThing) -> Unit,
    mapLayers: MapLayersControls,
    renderMode: MapRenderMode,
    fanBackEnabled: Boolean,
    onFeatureTap: (MapFeatureTap) -> Unit,
    onCloseBubble: () -> Unit,
    cameraMemory: MapCameraMemory,
    returnMemory: MapReturnMemory,
    navigationFacing: NavigationFacing,
    onLeftView: () -> Unit,
    onStartZoomApplied: () -> Unit,
    trueHeading: State<TrueHeadingReading>,
    cluster: MapIconClusterState,
    focusOverride: LatLng?,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onCameraIdle: (LatLng) -> Unit,
) {
    val breadcrumbPoints = recording.breadcrumbPoints
    val waypoints = recording.waypoints
    val isNavigating = navigation.isNavigating
    val navigationTarget = navigation.navigationTarget
    val routeLine = navigation.routeLine
    val waypointStraightLine = navigation.waypointStraightLine
    val navigationFollowing = navigation.navigationFollowing
    val navigationViewRequestId = navigation.navigationViewRequestId
    val navigationZoomPending = navigation.navigationZoomPending
    val landscapeCluster = cluster.landscape
    mapSlot(
        displayRegion,
        MapOverlayContent(
            sightings = filteredSightings,
            plannedTrips = uiState.plannedTrips,
            breadcrumbPoints = breadcrumbPoints,
            // Dispatch -497: once arrived, the start's pin is left out and the arrival
            // ring drawn in its place (RouteHomeLayers.kt), so the start changes form.
            waypoints = if (arrivedAt != null) waypoints.filterNot { it.id == navigationTarget?.id } else waypoints,
            // Dispatch -502: a waypoint navigation's dashed line beside the return's way back. There is no way back to
            // draw meanwhile: the return the waypoint overrules is paused, and pausing it clears its line
            // (TrackRecordingViewModel.stopReturn), so nothing here has to hide it.
            route = if (isNavigating) RouteOnMap(routeLine, arrivedAt, waypointStraightLine?.points, waypointStraightLine?.isCurrent ?: true, waypointStraightLine?.isOffline ?: false) else null,
            resumeTrackingRequestId = resumeTrackingRequestId,
            // RECORD -750, item 5: each location search frames its region, following or not.
            searchFrameRequestId = uiState.searchFrameSerial,
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
        renderMode.copy(
            // The fan's Back goes after the add-action menu and the pin pickers (dispatch 2026-09-28-298):
            // their handler above is the one asked while they are up. Kept apart from the bubble's gate
            // below, which is the bubble's own.
            backEnabled = fanBackEnabled,
            onFeatureTap = onFeatureTap,
            onCloseBubble = onCloseBubble,
            cameraMemory = cameraMemory,
            returnMemory = returnMemory,
            // Dispatch 2026-09-28-430: the navigation view while navigating, and the one
            // true heading for the puck and a facing-up map (ruling A).
            navigationView = if (isNavigating) NavigationViewRequest(navigationFacing, navigationFollowing, navigationViewRequestId, onLeftView, navigationZoomPending, onStartZoomApplied) else null,
            trueHeading = trueHeading,
            // Item 1 (dispatch 2026-09-29-57, amendment -262, "Move the 'i'"): the landscape L's measured bounds, in the map's own
            // pixels, for MapLibre's attribution button to keep clear of. The L keeps its bottom limit at the nav inset; the
            // button moves (SightingsMap, attributionEndInsetClearOf). Only the landscape L: portrait is unchanged.
            attributionKeepClear = if (landscapeCluster) {
                cluster.clusterBoundsInRoot?.translate(-cluster.mapContentBoxLeftInRootPx, -cluster.mapContentBoxTopInRootPx)
            } else {
                null
            },
        ),
        focusOverride,
        {},
        // Tapping the map restores chrome while fullscreen — decision #5 — AND dismisses
        // the bubble below regardless of fullscreen state (a plain tap on empty map is
        // its dismiss gesture). Since M1 a tap on a glyph is not a plain tap (owner's
        // ruling 1, "Bubble only"), so it opens its bubble and does neither.
        {
            if (isFullscreen) onToggleFullscreen()
            onCloseBubble()
        },
        { sighting, screenPosition, bearingDeg ->
            onTapped(TappedMapThing(MapBubbleTarget.SightingTarget(sighting), screenPosition, bearingDeg))
        },
        onCameraIdle,
        Modifier.fillMaxSize(),
    )
}

/** Motion Part 2, item 3: how the strip and the navigation display come and go; see [CompactMapTopStrip]. Moved out unchanged (RECORD -741). */
@Composable
private fun navigationChromeTransitions(): Pair<EnterTransition, ExitTransition> {
    val reduceMotion = LocalReduceMotion.current
    val navigationChromeSlide = MotionTokens.navigationViewChromeSpec<IntOffset>()
    val navigationChromeTimedFade = MotionTokens.navigationViewChromeSpec<Float>()
    val reducedFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val navigationChromeEnter = if (reduceMotion) {
        fadeIn(animationSpec = reducedFade)
    } else {
        slideInVertically(animationSpec = navigationChromeSlide) { fullHeight -> -fullHeight } + fadeIn(animationSpec = navigationChromeTimedFade)
    }
    val navigationChromeExit = if (reduceMotion) {
        fadeOut(animationSpec = reducedFade)
    } else {
        slideOutVertically(animationSpec = navigationChromeSlide) { fullHeight -> -fullHeight } + fadeOut(animationSpec = navigationChromeTimedFade)
    }
    return navigationChromeEnter to navigationChromeExit
}

/**
 * The tapped thing's bubble, moved out of [CompactMapTab] unchanged (RECORD -741): see that function's comments where it is called.
 * Its [minY] and [backEnabled] are worked out there, from state the tab owns.
 */
@Composable
private fun BoxScope.CompactMapBubble(
    tapped: TappedMapThing?,
    onCloseBubble: () -> Unit,
    bubbleSources: MapRecordSources,
    returnMemory: MapReturnMemory,
    renderMode: MapRenderMode,
    controlsPadding: PaddingValues,
    cluster: MapIconClusterState,
    noticeInsetDp: Dp,
    minY: Dp,
    backEnabled: Boolean,
    navigation: CompactMapNavigation,
) {
    val context = LocalContext.current
    val landscapeCluster = cluster.landscape
    val waypointReopen = navigation.waypointReopen
    val onWaypointReopenConsumed = navigation.onWaypointReopenConsumed
    // Dispatch 2026-09-28-535 (RECORD -534): no label under the dot. The owner: "the bubble message is
    // repeating what the strip says. One has to go, and my vote is for the bubble message." The strip
    // below, or the HUD while navigating, carries the words.
    // minY = compassStripClearance, a real measurement of the strip's own type style: the
    // strip is composed after this in the same Box (so its own controls win any overlap)
    // and is full-width against the map's top edge, so a glyph tapped near the top would
    // otherwise anchor a bubble under that strip's band, where its taps (the close button's
    // included) would never reach it (CLAUDE.md, the Surface pitfall). See
    // compassStripClearance's own comment for why it is a one-time text measurement.
    //
    // Back closes the bubble (M1), except while something above the map owns Back: the
    // Tools drawer (intent 2026-09-28-28's precedence), the add menu or a picker.
    // The bubble's clamp box stops short of the rail (the measured width, in controlsPadding) and, on its
    // side, of the L: the value already worked out for the search notice above, 8 + the L's measured width + 8,
    // and inside the rail's own padding because the L is (MapIconCluster applies controlsPadding first).
    // Only in the landscape L; portrait's cluster is a column at the edge and keeps the old clamp.
    val bubbleLayoutDirection = LocalLayoutDirection.current
    val bubbleClusterInset = if (landscapeCluster) noticeInsetDp else 0.dp
    val bubbleInsetLeft = controlsPadding.calculateLeftPadding(bubbleLayoutDirection) + if (cluster.isOnLeftSide) bubbleClusterInset else 0.dp
    val bubbleInsetRight = controlsPadding.calculateRightPadding(bubbleLayoutDirection) + if (cluster.isOnLeftSide) 0.dp else bubbleClusterInset
    MapBubbleLayer(
        tapped = tapped,
        onDismiss = onCloseBubble,
        // Item 8: "Open in Journal" remembers where the find was opened from (its id, the fan open then, the bubble's
        // anchor) before the screen switches to the Journal. `tapped` is read here, in composition, so the closure holds
        // the bubble that is showing, not the null the dismissal inside the layer leaves behind.
        sources = bubbleSources.copy(
            onOpenFind = bubbleSources.onOpenFind?.let { open ->
                val shown = tapped
                val remembering: (String) -> Unit = { id ->
                    returnMemory.remember(id, shown?.anchorPx ?: Offset.Zero, shown?.bearingDeg ?: 0f)
                    open(id)
                }
                remembering
            },
            // Part B of dispatch 2026-09-28-387: a bubble's "kept in" line remembers the bubble it was tapped in, and the fan open then, before it opens the entry.
            onOpenEntry = bubbleSources.onOpenEntry?.let { open ->
                val shown = tapped
                val remembering: (String) -> Unit = { entryId ->
                    if (shown != null) returnMemory.rememberEntryOpen(entryId, shown)
                    open(entryId)
                }
                remembering
            },
        ),
        forecast = renderMode.forecast,
        onViewSightingOnINaturalist = { sighting ->
            launchINaturalistObservation(context, sighting.observationId)
            onCloseBubble()
        },
        minY = minY,
        backEnabled = backEnabled,
        insetLeft = bubbleInsetLeft,
        insetRight = bubbleInsetRight,
        // Dispatch -502, Amendment 1: Back ended a waypoint navigation started in the waypoint's details sheet here.
        reopenDetails = (waypointReopen as? WaypointNavigationOrigin.MapDetails)?.let { RecordDetailsTarget.WaypointDetails(it.waypointId) },
        onReopenDetailsConsumed = onWaypointReopenConsumed,
    )
}

/** The icon cluster, moved out of [CompactMapTab] unchanged (RECORD -741). [topLimitPx] and [noticeBottomPx] are worked out there. */
@Composable
private fun BoxScope.CompactMapIconCluster(
    cluster: MapIconClusterState,
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    recording: CompactMapRecording,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
    onResumeTracking: () -> Unit,
    onResetOrientation: () -> Unit,
    mapMode: MapMode,
    onOpenLayers: () -> Unit,
    onOpenActionMenu: () -> Unit,
    topLimitPx: Float,
    noticeBottomPx: Float,
    controlsPadding: PaddingValues,
) {
    val isNavigating = navigation.isNavigating
    val onReturnToRoute = navigation.onReturnToRoute
    val returnToStart = navigation.returnToStart
    val isReturning = navigation.isReturning
    val isOffTrack = navigation.isOffTrack
    val onToggleReturning = navigation.onToggleReturning
    val isRecording = recording.isRecording
    val onToggleRecording = recording.onToggleRecording
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
                // Ruling D: while navigating, locate brings the navigation view back, as
                // "Return to Route" does; plain tracking would drop the compass follow.
                if (isNavigating) onReturnToRoute() else onResumeTracking()
                onLocateMe()
            },
            onResetOrientation = onResetOrientation,
            mapMode = mapMode,
            onOpenLayers = onOpenLayers,
            onAdd = {
                // No location to grab any more — the button just opens
                // the menu; the location comes from
                // CentrePinLocationPickerOverlay's own camera tracking
                // once a choice is made. See this function's own doc
                // comment.
                onOpenActionMenu()
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
        // searchBarBottom, the search bar's own bottom (settled, not animated), without the strip clearance (the compass strip is in the other corner there,
        // nothing else is drawn in that band beside the notice and the chips, which make room for the L, and the SearchDropdown
        // starts below it); the L pushes down to it as well as up. Portrait keeps topInset + the clearance.
        topLimitPx = topLimitPx,
        noticeBottomPx = noticeBottomPx,
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
                // Dispatch -502: the X-circle for either navigation, a waypoint's included.
                isNavigating = isNavigating,
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
                isNavigating = isNavigating,
            )
        },
        // T11 fixes (RECORD -766, P4): the L slides below the navigation display while navigating on its side.
        isNavigating = isNavigating,
    )
}

/**
 * The compass strip, its measured size and the landscape join line, moved out of [CompactMapTab] unchanged (RECORD -741).
 * The three measured sizes are the tab's state, handed in.
 */
@Composable
private fun BoxScope.CompactMapTopStrip(
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    landscape: CompactMapLandscape,
    controlsPadding: PaddingValues,
    topInset: Dp,
    isFullscreen: Boolean,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    trueHeading: State<TrueHeadingReading>,
    positionNote: State<PositionNote?>,
    sundownClock: SundownClock,
    compassStripHeight: MutableIntState,
    landscapeStripHeight: MutableIntState,
    landscapeStripWidth: MutableIntState,
) {
    val isNavigating = navigation.isNavigating
    val railPortEdge = landscape.railPortEdge
    val punchHoleEdge = landscape.punchHoleEdge
    val onLandscapeStripHeightMeasured = landscape.onLandscapeStripHeightMeasured
    val onLandscapeStripWidthMeasured = landscape.onLandscapeStripWidthMeasured
    val compassStripDensity = LocalDensity.current
    var compassStripHeightPx by compassStripHeight
    var landscapeStripHeightPx by landscapeStripHeight
    var landscapeStripWidthPx by landscapeStripWidth
    // Not composed at all while navigating (navigation-chrome dispatch, item 1) — the
    // HUD below carries the heading, elevation and coordinates then, and on device
    // both showing meant the heading appeared three times. Removed from composition
    // rather than made invisible: this strip's leaf is what reads the heading State
    // at sensor rate, and an invisible strip would still be recomposing at 16 Hz
    // alongside the HUD doing the same work. Gated on isNavigating, never isReturning,
    // so stage two's picker cannot bring it back by accident — see AvailabilityScreen's
    // own isNavigating doc comment.
    // Motion Part 2, item 3: how the strip and the navigation display come and go (their comments below). Up and out,
    // or down and in, by the panel's own height, with a fade, all one timed animation exactly as long as the map's
    // tilt (Amendment 1, RECORD -672). Under reduced motion the fade alone, on the pop-ups' fade.
    val (navigationChromeEnter, navigationChromeExit) = navigationChromeTransitions()
    // Dispatch 2026-10-12-06 (RECORD -932, -934): in portrait the strip is the rounded card tucked under the pill; none in
    // landscape, where the two sit side by side, or once the pill has slid away in fullscreen (mapCardTuck).
    val stripTuck = if (railPortEdge == null) mapCardTuck(topInset) else 0.dp
    // Landscape: rounded on its outer side and square where it meets the pill, edge to edge, no join line (RECORD -934).
    val stripCorners = when {
        railPortEdge == null -> MapCardCorners.BOTTOM
        punchHoleEdge != null && railPortEdge != punchHoleEdge -> MapCardCorners.outerSide(railPortEdge)
        else -> MapCardCorners.ALL
    }
    // Motion Part 2, item 3 (dispatch 2026-09-28-666, scouts N1 and N2; the owner, RECORD -651: "Move with the map"):
    // on starting navigation the strip slides up and out, clipped at its own top edge so it goes in under the search
    // bar, while the navigation display below slides down and in the same way, both on navigationViewChromeSpec,
    // exactly as long as the map's tilt; on stopping, the reverse. Each fades as it moves; under
    // reduced motion each only fades. A leaving strip takes no touch (item 5) and gives up its keep-out and its
    // measured height at once, so nothing waits on it. It is still composed for the length of its exit, so for that
    // moment it reads the heading at sensor rate beside the HUD (the paragraph above): bounded, and the S22's to judge.
    // RECORD -691: no minimum touch target while it leaves, so a touch near a leaving piece under 48 dp is not handed to it (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
    NoTouchTargetExpansion(active = isNavigating) {
    androidx.compose.animation.AnimatedVisibility(
        visible = !isNavigating,
        enter = navigationChromeEnter,
        exit = navigationChromeExit,
        modifier = if (railPortEdge != null) {
            // Landscape B2 (S4): the top corner on the rail side, below the
            // status bar only (the Scaffold's top inset), not below the search
            // bar, which is on the other side.
            Modifier
                .align(if (railPortEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                .padding(controlsPadding)
                // RECORD -940 (the owner: "Increase the length of the box. It can be the same size as portrait so that nothing
                // really needs to change"; "Option 1, but permanent"): as wide as in portrait, the window's short side, always.
                // This replaces RECORD -732's fit to its readouts with the bar's floor (landscapeStripFit), and the bar takes the
                // rest of the room beside it, whatever that leaves the bar.
                .width(mapCardWidth())
        } else {
            // Dispatch 2026-10-12-06: tucked under the pill, the card's fill reaching [stripTuck] above its own top (inside this
            // clip), while the strip's own bounds still start at the bar's bottom (the padding on its modifier below).
            Modifier
                .align(Alignment.TopCenter)
                .padding(controlsPadding)
                .fillMaxWidth()
                .padding(top = topInset - stripTuck)
        }
            .clipToBounds()
            .leavingTakesNoTouches(leaving = isNavigating),
    ) {
        CompassElevationStrip(
            heading = trueHeading,
            elevationMeters = uiState.liveAltitudeMeters,
            unitSystem = uiState.unitSystem,
            location = uiState.liveLocation,
            showDecimalDegrees = showDecimalDegrees,
            onToggleCoordinateFormat = onToggleCoordinateFormat,
            // Full width, "just below" SearchEntryBar rather than a narrow floating pill
            // with margins on both sides, per the project owner's own redesign call — topInset
            // is how that clearance reaches here now that the bar composes as a real overlay
            // in the same Box as this tab's own content (compactMainScaffold's own call
            // site) instead of a sibling Column entry above it; 0.dp (this parameter's own
            // default) reproduces the old flush-against-the-map-top behavior exactly. The
            // alignment and the padding are on the AnimatedVisibility above (motion Part 2).
            modifier = if (railPortEdge != null) {
                // RECORD -729: the strip fills its width. RECORD -736 (the owner: "The compass strip must remain the
                // same height as portrait though and that's important"): no height floor, so it is exactly as tall as in
                // portrait at every font; -729's floor, which grew it to the bar's field at large fonts, is gone. Its
                // measured height and width go up to the scaffold, which gives the bar that height and the rest of the
                // room; not while it leaves (motion Part 2).
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (!isNavigating) { landscapeStripHeightPx = it.height; landscapeStripWidthPx = it.width } }
            } else {
                Modifier
                    // Dispatch 2026-10-12-06: the strip's bounds start at the pill's bottom, below the tuck its fill reaches into.
                    .padding(top = stripTuck)
                    .fillMaxWidth()
                    // The strip's own height (item 2), and not while it leaves (motion Part 2).
                    .onSizeChanged { if (!isNavigating) compassStripHeightPx = it.height }
            }.then(if (!isNavigating) Modifier.mapKeepOut(MapKeepOutIds.TOP_STRIP) else Modifier),
            // RECORD -729: full width in landscape too, so the readouts take the whole strip.
            contentWidth = false,
            // RECORD -735: the crossed-out compass for no compass or an unreliable one in a short landscape window;
            // portrait keeps the full words.
            // RECORD -940: laid out exactly as in portrait, so the portrait words; RECORD -735's crossed-out compass was for the
            // narrower landscape strip that ruling replaces.
            shortHeadingStatus = false,
            positionNote = positionNote,
            // Amendment 2 (RECORD -595): hidden until its window opens; see isShown.
            sundownLine = recordingSundownLine?.let { sundownLineText(it, sundownClock) },
            backByLine = backByLineText(quickSettings?.backBy, sundownClock),
            // Amendment 3 (RECORD -648): "a 3 dot menu at the far right", placed by the strip,
            // not the screen (RECORD -649): the strip's own right end in every orientation.
            quickSettings = quickSettings,
            cardCorners = stripCorners,
            tuck = stripTuck,
        )
        DisposableEffect(Unit) { onDispose { compassStripHeightPx = 0; landscapeStripHeightPx = 0; landscapeStripWidthPx = 0 } }
    }
    }
    // Motion Part 2: the strip's measured height goes the moment it starts to leave, as it did when it left at once.
    LaunchedEffect(isNavigating) { if (isNavigating) { compassStripHeightPx = 0; landscapeStripHeightPx = 0; landscapeStripWidthPx = 0 } }
    // RECORD -754 (the owner's S26 on 1.0.3103: after a turn from portrait the bar kept a portrait-sized box, taller than the
    // strip and reaching the window's centre): out of landscape the landscape strip's measures go too. They are reported up only
    // when they change (below), and the scaffold zeroes its own copy whenever the bar stops meeting the strip, so a measure left
    // here from an earlier landscape turn was the same number on the next turn, was never reported again, and the bar stayed at
    // its own height and the half width it takes before the strip is measured. Found by a test turning into landscape twice
    // (LandscapeSearchFixesTest, "second turn"), not by reasoning.
    LaunchedEffect(railPortEdge) { if (railPortEdge == null) { landscapeStripHeightPx = 0; landscapeStripWidthPx = 0 } }
    // RECORD -729: the landscape strip's measured height, up to the scaffold, which gives the search bar that height; 0
    // while it is not measured (portrait, navigating, before its first layout), where the bar keeps its own.
    LaunchedEffect(landscapeStripHeightPx) { onLandscapeStripHeightMeasured(with(compassStripDensity) { landscapeStripHeightPx.toDp() }) }
    LaunchedEffect(landscapeStripWidthPx) { onLandscapeStripWidthMeasured(with(compassStripDensity) { landscapeStripWidthPx.toDp() }) }
    // RECORD -729, item 4 drew a 1 dp vertical line where the bar and the strip meet in landscape. Dispatch 2026-10-12-06
    // (RECORD -934, the owner's answer confirming "in landscape, the pill and card touch edge to edge (no join line)"): the
    // line is gone; the two meet square on their joined side and round on their outer sides (stripCorners, the bar's
    // pillCorners in the scaffold). LANDSCAPE_BAR_STRIP_LINE_TAG is kept so a test can say no such line is drawn.
}

/** The taxon and journal-entries chips, moved out of [CompactMapTab] unchanged (RECORD -741). */
@Composable
private fun BoxScope.CompactMapChips(
    mapTaxonFilterLabel: String?,
    onClearTaxonFilter: () -> Unit,
    mapLayers: MapLayersControls,
    landscape: CompactMapLandscape,
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    topInset: Dp,
    compassStripBottomClearance: Dp,
) {
    val punchHoleEdge = landscape.punchHoleEdge
    val landscapeSearchWidth = landscape.landscapeSearchWidth
    val isMapIconBarOnLeftSide by cluster::isOnLeftSide
    val mapIconClusterWidthPx by cluster::clusterWidthPx
    // Below the compass strip (topInset + compassStripClearance as top padding), same
    // reasoning as AnchoredAtScreenPoint's own minY: the strip is drawn across the map's
    // full width, so a chip placed underneath it would be hidden behind it, and where it
    // sat under the strip's coordinates or its quick-settings button (dispatch -645), its
    // "Show all species" tap would go to them. Corrected in dispatch 2026-09-28-645: this
    // said "the strip's Surface intercepts touches across its full width", which stopped
    // being true when the strip became a plain Box that takes no touches of its own
    // (CompassElevationStripContent); its real long-press tests reach the map through it. topInset itself (see this composable's own doc comment) clears whatever
    // chrome floats above the strip too — SearchEntryBar, on the Map tab.
    //
    // J8-3 (owner: "Top, by the species chip (Recommended)"): the journal-entries chip sits in
    // the row with the taxon chip, after it, at the same place in each window. A FlowRow sized
    // to its chips (it draws nothing and takes no touches itself, so the map keeps every touch
    // around them: CLAUDE.md, the Surface pitfall), so two chips wider than the room wrap to a
    // second line instead of running off the screen. It holds whichever chips there are.
    val shownJournalEntries = mapLayers.journalHighlights.shownEntries
    // Motion Part 2, item 4 (scout M1; the owner, RECORD -651: "Fade and grow", from where each belongs): each chip
    // fades and grows down from its top centre, under the strip it hangs from, and shrinks back up to it; item 5: a
    // leaving chip takes no touch. The row stays composed until its last chip has gone, and gives up its keep-out
    // the moment no chip is meant to show.
    val taxonChipShown = rememberLastShown(mapTaxonFilterLabel)
    val taxonChip = rememberPopUpState(mapTaxonFilterLabel != null)
    val journalChipShown = rememberLastShown(shownJournalEntries.takeIf { it.isNotEmpty() })
    val journalChip = rememberPopUpState(shownJournalEntries.isNotEmpty())
    val chipsMeantToShow = mapTaxonFilterLabel != null || shownJournalEntries.isNotEmpty()
    if (taxonChip.isOnScreen || journalChip.isOnScreen) {
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
                    .padding(top = topInset + compassStripBottomClearance + Spacing.sm)
            }.then(if (chipsMeantToShow) Modifier.mapKeepOut(MapKeepOutIds.CHIPS) else Modifier),
        ) {
            MapPopUp(state = taxonChip, pivot = PopUpPivot.TopCentre) {
                taxonChipShown?.let { label -> TaxonMapFilterChip(label = label, onClear = onClearTaxonFilter) }
            }
            MapPopUp(state = journalChip, pivot = PopUpPivot.TopCentre) {
                journalChipShown?.let { entries ->
                    JournalEntriesMapChip(
                        entries = entries,
                        onHide = mapLayers.onHideJournalEntry,
                        onHideAll = mapLayers.onHideAllJournalEntries,
                    )
                }
            }
        }
    }
}

/** The navigation display (HUD), moved out of [CompactMapTab] unchanged (RECORD -741). */
@Composable
private fun BoxScope.CompactMapNavigationDisplay(
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    landscape: CompactMapLandscape,
    /** T11 fixes (RECORD -766, P4): told the display's bottom edge while navigating, so the landscape L can slide below it. */
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    topInset: Dp,
    trueHeading: State<TrueHeadingReading>,
    navigationFacing: NavigationFacing,
    currentTime: CurrentTimeProvider,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    sundownClock: SundownClock,
) {
    val isNavigating = navigation.isNavigating
    val navigationTarget = navigation.navigationTarget
    val isNavigatingToWaypoint = navigation.isNavigatingToWaypoint
    val returnRoute = navigation.returnRoute
    val onRetryRoute = navigation.onRetryRoute
    val onToggleReturning = navigation.onToggleReturning
    val railPortEdge = landscape.railPortEdge
    val punchHoleEdge = landscape.punchHoleEdge
    val chromeLayoutDirection = LocalLayoutDirection.current
    val (navigationChromeEnter, navigationChromeExit) = navigationChromeTransitions()
    // Dispatch 2026-10-12-06 (RECORD -932, -934): in portrait the rounded card tucked under the pill; in landscape beside the bar,
    // rounded all round, at today's place and 360 dp cap (whether it meets the bar there depends on the window, as before).
    val hudTuck = if (railPortEdge == null) mapCardTuck(topInset) else 0.dp
    // T11 fixes: no display, no floor for the L.
    LaunchedEffect(isNavigating) { if (!isNavigating) cluster.navigationDisplayBottomInRootPx = null }
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
    // Motion Part 2, item 3: slides down and in from under the search bar as the strip above slides out, and back up
    // and out on stop (the strip's comment above). Leaving, it takes no touch and gives up its keep-out at once.
    // RECORD -691: no minimum touch target while it leaves, so a touch near a leaving piece under 48 dp is not handed to it (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
    NoTouchTargetExpansion(active = !isNavigating) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isNavigating,
        enter = navigationChromeEnter,
        exit = navigationChromeExit,
        modifier = if (railPortEdge != null) {
            // Landscape B2 (S4): the top corner on the rail side, below the
            // status bar only, at most 360dp wide.
            Modifier
                .align(if (railPortEdge == ScreenEdge.Left) Alignment.TopStart else Alignment.TopEnd)
                .padding(controlsPadding)
                .besideLandscapeSearchBar(railPortEdge, punchHoleEdge, controlsPadding, chromeLayoutDirection)
                .widthIn(max = LANDSCAPE_HUD_MAX_WIDTH)
                .fillMaxWidth()
        } else {
            // Dispatch 2026-10-12-06: tucked under the pill, as the strip is (CompactMapTopStrip's stripTuck).
            Modifier
                .align(Alignment.TopCenter)
                .padding(controlsPadding)
                .fillMaxWidth()
                .padding(top = topInset - hudTuck)
        }
            .clipToBounds()
            .leavingTakesNoTouches(leaving = !isNavigating),
    ) {
        NavigationHud(
            heading = trueHeading,
            liveFix = uiState.liveFix,
            // Dispatch 2026-09-28-510: shown in place of GPS by hudReadout's rule; never measured from for "Arrived".
            approximateFix = uiState.approximateFix,
            lastKnownFix = uiState.lastKnownFix,
            target = navigationTarget,
            distanceUnit = uiState.distanceUnit,
            // Dispatch -502: no route to a chosen waypoint, so the straight-line HUD (decision D2).
            route = if (isNavigatingToWaypoint) null else returnRoute,
            onRetryRoute = onRetryRoute,
            facing = navigationFacing,
            currentTime = currentTime,
            showDecimalDegrees = showDecimalDegrees,
            onToggleCoordinateFormat = onToggleCoordinateFormat,
            onExit = onToggleReturning,
            // RECORD -845: "Arrived", held, after a return ended on arrival.
            arrivalHeld = navigation.isArrivalHeld,
            // The recording's line, or with no recording sunset and dark only, computed here; either
            // hidden until its window opens (Amendment 2, RECORD -595).
            sundownLine = (recordingSundownLine ?: rememberScreenSundownLine(uiState.headingFix?.let { com.zynergylabs.forager.app.domain.model.LatLng(it.lat, it.lng) }, currentTime))
                .let { sundownLineText(it, sundownClock) },
            backByLine = backByLineText(quickSettings?.backBy, sundownClock),
            quickSettings = quickSettings,
            // T11 fixes (RECORD -766, P3): beside the landscape search bar, the evening lines share one and "Try again" sits
            // beside its message.
            landscape = railPortEdge != null,
            // RECORD -770: with the L below the display, the display ends no lower than the bar's lowest top plus its
            // Fullscreen row, or its evening line leaves.
            eveningLineBottomLimitInRootPx = if (navigationSlideApplies(cluster.landscape, isNavigating, cluster.isOnLeftSide, cluster.railPortEdge)) {
                cluster.navigationLowestBarTopInRootPx?.let { it + with(LocalDensity.current) { com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET.toPx() } }
            } else {
                null
            },
            // Landscape: beside the bar, square where it meets it at the top, rounded on its outer side and at its inner bottom
            // corner, which hangs below the bar (RECORD -934's edge to edge, the planner's call in -936).
            cardCorners = when {
                railPortEdge == null -> MapCardCorners.BOTTOM
                punchHoleEdge != null && railPortEdge != punchHoleEdge -> MapCardCorners.besideBar(railPortEdge)
                else -> MapCardCorners.ALL
            },
            tuck = hudTuck,
            modifier = Modifier
                // The card's bounds start at the pill's bottom, below the tuck its fill reaches into.
                .padding(top = hudTuck)
                .fillMaxWidth()
                .then(if (isNavigating) Modifier.mapKeepOut(MapKeepOutIds.TOP_STRIP) else Modifier)
                // T11 fixes (P4): the display's bottom edge, for the landscape L's slide below it; only while navigating, so a
                // leaving display hands the L back at once.
                .onGloballyPositioned { coordinates -> if (isNavigating) cluster.navigationDisplayBottomInRootPx = coordinates.boundsInRoot().bottom },
        )
    }
    }
}

/** "Return to Route", the legend, the bottom bar and the rail, moved out of [CompactMapTab] unchanged (RECORD -741). */
@Composable
private fun BoxScope.CompactMapBottomChrome(
    navigation: CompactMapNavigation,
    landscape: CompactMapLandscape,
    renderMode: MapRenderMode,
    mapLayers: MapLayersControls,
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    showBottomNav: Boolean,
    isFullscreen: Boolean,
    isDrawerOpen: Boolean,
    onBottomNavTabSelected: (CompactTab) -> Unit,
    onBottomNavHeightMeasured: (Float) -> Unit,
) {
    val isNavigating = navigation.isNavigating
    val navigationFollowing = navigation.navigationFollowing
    val onReturnToRoute = navigation.onReturnToRoute
    val railPortEdge = landscape.railPortEdge
    val onRailWidthMeasured = landscape.onRailWidthMeasured
    val landscapeCluster = cluster.landscape
    val isMapIconBarOnLeftSide by cluster::isOnLeftSide
    val mapIconClusterWidthPx by cluster::clusterWidthPx
    var legendChipTopPx by cluster::legendChipTopPx
    val mapContentBoxTopInRootPx by cluster::mapContentBoxTopInRootPx
    var mapBottomNavHeightPx by cluster::bottomNavHeightPx
    // Dispatch 2026-09-28-430 (plan task T22): "Return to Route", while navigating once the
    // user has moved the map away from the navigation view. Bottom centre, above the
    // attribution caption as the legend is (renderMode.bottomInset, then the "i"'s
    // clearance), inside controlsPadding so it keeps clear of the landscape rail; the
    // snackbars rise above it while it shows (the scaffold). Composed before the nav, so the
    // nav keeps winning its own band.
    // Motion Part 2, item 4 (scout N3; "Fade and grow"): it grows up from its bottom centre and shrinks back down;
    // item 5: from the moment it starts to leave (a tap on it, which brings the view back) it takes no touch.
    val returnToRouteShown = isNavigating && !navigationFollowing
    MapPopUp(
        visible = returnToRouteShown,
        pivot = PopUpPivot.BottomCentre,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(controlsPadding)
            .padding(bottom = renderMode.bottomInset + LEGEND_ATTRIBUTION_CLEARANCE),
    ) {
        ReturnToRoutePill(
            onClick = onReturnToRoute,
            modifier = if (returnToRouteShown) Modifier.mapKeepOut(MapKeepOutIds.RETURN_TO_ROUTE) else Modifier,
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
    // Motion Part 2, item 4 (scout M2; "Fade and grow"): the legend grows out of its own bottom-end corner and shrinks
    // back into it; item 5: leaving, it takes no touch and gives up its keep-out at once. It keeps the cluster clear of
    // it (legendChipTopPx) until it has gone, so the cluster does not glide down over a legend still fading. Opening
    // and closing it (scout M3) is unchanged: not among the owner's choices.
    val legendNow = mapLegendFor(renderMode.layers, MAP_LAYER_REGISTRY, COLOUR_FIELDS, mapLayers.cellsShown, mapLayers.forecastZoomedOut)
    val legendShown = rememberLastShown(legendNow)
    MapPopUp(
        visible = legendNow != null,
        pivot = PopUpPivot.BottomEnd,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(controlsPadding)
            .padding(end = legendEndPadding, bottom = renderMode.bottomInset + LEGEND_ATTRIBUTION_CLEARANCE),
    ) {
        legendShown?.let { legend ->
            DisposableEffect(Unit) { onDispose { legendChipTopPx = null } }
            MapLegendChip(
                legend = legend,
                expanded = mapLayers.legendExpanded,
                onExpandedChange = mapLayers.onLegendExpandedChange,
                modifier = Modifier
                    .then(if (legendNow != null) Modifier.mapKeepOut(MapKeepOutIds.LEGEND) else Modifier)
                    .onGloballyPositioned { coordinates ->
                        legendChipTopPx = coordinates.positionInRoot().y - mapContentBoxTopInRootPx
                    },
            )
        }
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
                containerColor = navigationBarContainerColor().copy(alpha = MAP_CHROME_OVER_MAP_ALPHA),
                // RECORD -937: rounded top corners over the map, as the card's.
                topCornerRadius = MAP_CARD_CORNER_RADIUS,
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
                containerColor = navigationBarContainerColor().copy(alpha = MAP_CHROME_OVER_MAP_ALPHA),
                modifier = Modifier.mapKeepOut(MapKeepOutIds.RAIL).onGloballyPositioned { coordinates ->
                    onRailWidthMeasured(coordinates.size.width.toFloat())
                },
            )
        }
    }
}

/**
 * The add-action menu, the Layers sheet and the centre-pin picker, moved out of [CompactMapTab] unchanged (RECORD -741).
 * Their state is the tab's, handed in; [currentCameraCenter] reads the map's centre when a choice is confirmed, as before.
 */
@Composable
private fun BoxScope.CompactMapPickers(
    showActionMenuState: MutableState<Boolean>,
    showLayersSheetState: MutableState<Boolean>,
    pendingActionState: MutableState<PendingMapAction?>,
    pendingTripLocationState: MutableState<LatLng?>,
    pendingWaypointLocationState: MutableState<LatLng?>,
    currentCameraCenter: () -> LatLng,
    searchLocationPick: CompactMapSearchLocationPick,
    onLogFindHere: (LatLng) -> Unit,
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    mapLayers: MapLayersControls,
    landscape: CompactMapLandscape,
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    isFullscreen: Boolean,
    night: Boolean,
) {
    var showActionMenu by showActionMenuState
    var showLayersSheet by showLayersSheetState
    var pendingAction by pendingActionState
    var pendingTripLocation by pendingTripLocationState
    var pendingWaypointLocation by pendingWaypointLocationState
    val pickingSearchLocation = searchLocationPick.pickingSearchLocation
    val onSearchLocationPicked = searchLocationPick.onSearchLocationPicked
    val onCancelSearchLocationPick = searchLocationPick.onCancelSearchLocationPick
    val railPortEdge = landscape.railPortEdge
    val landscapeCluster = cluster.landscape
    val isMapIconBarOnLeftSide by cluster::isOnLeftSide
    val mapBottomNavHeightPx by cluster::bottomNavHeightPx
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
    // Motion Part 2, item 4 (scout M9; "Fade and grow", from where each belongs): the centre pin grows from its tip, the
    // map's centre, and the OK/Cancel row up from its bottom edge; each shrinks back as it leaves, and from that moment
    // takes no touch (item 5). One overlay for both of its uses, the add menu's three and the search's "Set on map",
    // whose OK and Cancel stay as they were: the add menu's when an action is pending, the search's otherwise.
    CentrePinLocationPickerPopUp(
        visible = pendingAction != null || pickingSearchLocation,
        onConfirm = {
            if (pendingAction != null) {
                when (pendingAction) {
                    PendingMapAction.PLAN_TRIP -> pendingTripLocation = currentCameraCenter()
                    PendingMapAction.LOG_FIND -> onLogFindHere(currentCameraCenter())
                    PendingMapAction.DROP_WAYPOINT -> pendingWaypointLocation = currentCameraCenter()
                    null -> Unit
                }
                pendingAction = null
            } else if (pickingSearchLocation) {
                onSearchLocationPicked(currentCameraCenter())
            }
        },
        onCancel = {
            if (pendingAction != null) pendingAction = null else if (pickingSearchLocation) onCancelSearchLocationPick()
        },
        modifier = Modifier.fillMaxSize(),
        bottomInset = centrePinConfirmBottomInset,
        rowPadding = controlsPadding,
        // The map's own night (the renderMode handed to mapSlot above), so the pin
        // follows Night Maps (colour build C2 (e)).
        night = night,
    )
}

/** Landscape B2 (S4): the navigation HUD's width cap in the rail-side top corner. */
internal val LANDSCAPE_HUD_MAX_WIDTH = 360.dp

/** Dispatch 2026-09-28-502: the tag this tab's one log line goes under. */
private const val COMPACT_MAP_LOG_TAG = "CompactMapTab"

/**
 * RECORD -940: the map's top cards (the navigation card and the compass strip) are as wide in landscape as in portrait, the
 * window's short side. Read from the window's own size, not the configuration, so it is the window this app has (split screen
 * included). Robolectric's window is the qualifiers' size; on a device, whether the window's short side matches the portrait
 * width the owner sees is a device item (system bars).
 */
@Composable
internal fun mapCardWidth(): Dp {
    val size = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { minOf(size.width, size.height).toDp() }
}
