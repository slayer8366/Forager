package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.SideEffect
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onPlaced
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.JournalEntriesMapChip
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_CORNER_RADIUS
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.HANDLE_DEFAULT_TAP_HEIGHT
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
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * The map icon cluster's state and layout, used by the Maps tab ([CompactMapTab]). It was extracted from
 * `CompactMapTab` in J6c (dispatch 2026-09-28-152 item 14, continuation 2026-09-28-173: "Extract, don't
 * copy") to be shared with the tablet's map pane, which was removed in dispatch 2026-09-28-245; the
 * extraction stays.
 *
 * What moved is the cluster as it was, not a rewrite: the container holding the bar and the record | return
 * pill, the minimise and restore handles, the long-press drag, the side snap, and the clamps, with the
 * comments that recorded why each is as it is. The bottom navigation, search dropdown, notice and legend
 * bound the drag. The bar and the pill are slots, so the Maps tab wires its own callbacks (locate,
 * orient, layers, add, record, return) without this file knowing them.
 *
 * The landscape L (a short window's `LandscapeLCluster`, dispatch 2026-09-28-160) is chosen by [MapIconClusterState.landscape],
 * true only when the compact scaffold gives the rail and punch-hole edges, that is in a landscape window,
 * whatever its size (dispatch 2026-09-28-246). The owner's ruling of 2026-09-29, "Never on portrait or
 * tablet mode", is superseded for a landscape tablet by -246.
 */

/**
 * The cluster: composed as a child of the map pane's `Box`, after the map and what sits over it, before the
 * chrome that must win overlaps (composition order is paint and hit-test order). [topLimitPx] is the
 * highest edge the cluster may reach (the compact tree's search dropdown; the tablet's strip and chip row),
 * [noticeBottomPx] the bottom of a search notice while one shows, else 0.
 */
@Composable
internal fun BoxScope.MapIconCluster(
    state: MapIconClusterState,
    isFullscreen: Boolean,
    topLimitPx: Float,
    noticeBottomPx: Float,
    controlsPadding: PaddingValues,
    bar: @Composable (Modifier) -> Unit,
    pill: @Composable (onLeftSide: Boolean) -> Unit,
    /**
     * The landscape L's own bar and pill (dispatch 2026-09-28-160, the owner's "A"): used in place of [bar] and [pill] while
     * [MapIconClusterState.landscape]. Null for a caller that never takes the L (the tablet), which then has only [bar] and [pill].
     */
    landscapeBar: (@Composable (Modifier) -> Unit)? = null,
    landscapePill: (@Composable (onLeftSide: Boolean) -> Unit)? = null,
    /** T11 fixes (RECORD -766, P4): navigation is on; with [MapIconClusterState.navigationDisplayBottomInRootPx], see [navigationSlideApplies]. */
    isNavigating: Boolean = false,
) {
    // RECORD -711: nothing is drawn until a stored side and height have been applied, so a cluster restored
    // to the left never shows a frame on the right first. See MapIconClusterPositionState.
    if (!state.position.placementApplied) return
    val compassStripDensity = LocalDensity.current
    val mapIconBarOffsetScope = rememberCoroutineScope()
    // A drag distance past this point (either direction) commits the bar to the opposite side —
    // deliberately more than a light brush, since a small accidental sideways slip while actually
    // trying to reposition vertically should not also relocate the whole bar to the other side of
    // the screen.
    val mapIconBarSideSnapThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    // Keeps at least one full touch target's worth of the bar/handle on screen at either vertical
    // extreme of a drag — reuses MIN_TOUCH_TARGET (MapChrome.kt) rather than inventing a second
    // margin constant for the same "don't let a control go fully off-screen" idea.
    val mapIconBarVerticalDragMarginPx = with(LocalDensity.current) { MIN_TOUCH_TARGET.toPx() }
    // MapIconBar composed *before* CompassElevationStrip now, not after — field-test
    // dispatch item 2 gave the strip a real touch target at its own far right edge, the
    // same horizontal column MapIconBar's CenterEnd alignment already claims.
    // MapIconBar's Surface intercepts touches across its full bounds (see this
    // composable's own CLAUDE.md-documented precedent), and on a short enough viewport
    // its vertically-centered row stack reaches all the way up into the compass strip's
    // own row — confirmed directly by AvailabilityScreenMapIconStackTest's own
    // touch-interaction test on a w360dp-h640dp viewport, not assumed from visual review
    // alone (the exact class of miss that same file's own history warns visual review
    // alone won't catch). Composition order is paint AND hit-test order for overlapping
    // siblings in a Box, so moving this earlier guarantees the strip's own control wins
    // any overlap on every screen size, not just typical ones — a small cosmetic cost
    // (the strip's background, at the map chrome's alpha, could cover a sliver of one icon bar row on a
    // screen too short for MapIconBar's own rows to fit at all — already a degraded
    // state before this change) traded for a control that always actually works. Still
    // true after MapIconBar's own return-to-vehicle row was removed (see that
    // composable's own doc comment) — the overlap this guards against is with the bar's
    // Surface as a whole, not specifically with that one row.
    // Fullscreen-fixes dispatch, Item 3 ("the icon bar can minimise, with a peeking
    // handle to restore it"). MapIconBar and TrailheadControls hide/show together,
    // gated on state.isMinimized rather than isMapFullscreen — that item's own "do
    // not tie it to isMapFullscreen" instruction, and the owner's own "Minimise means
    // the chrome goes away, not that it fragments." Since the icon-bar-unify-container
    // dispatch they are one cluster container, so hiding together is by construction
    // rather than two gates that happen to agree.
    //
    // MapIconBarMinimizeHandle is composed as a later sibling of that container (at the
    // bar's own vertical centre — "mid-height" — via mapIconBarCentreShiftOffset below,
    // and moving with it) rather than nested inside it: Surface clips to its shape and
    // the handle's mark straddles the container's outer edge by design, and there is no
    // on-screen room to place a full 48dp touch target beside the bar without
    // overlapping it (the bar's own Spacing.sm edge inset is far narrower than that),
    // so the handle deliberately overlaps the bar's own outermost sliver, attached to
    // its edge the way the owner described. Composed after the container (and so
    // painted and hit-tested on top of it) so it wins that overlap, the same
    // composition-order-is-hit-test-order convention this file already uses for
    // the container itself against CompassElevationStrip (see this block's own comment
    // above).
    //
    // Direct owner request, layered on top of the above: the cluster (and its two
    // handles) can be dragged to reposition vertically and snaps to either screen edge
    // — see state.isOnLeftSide/state.userChosenOffsetPx's own doc comments
    // above. mapIconBarSideAlignment/mapIconBarPositionOffset are shared by the
    // container and whichever handle is currently showing so they always move and
    // land on the same side together, as one unit. detectDragGesturesAfterLongPress,
    // not a plain drag detector or Modifier.draggable: a quick tap must keep reaching
    // Surface's own onClick (minimize/restore) unambiguously, and the long-press
    // threshold is what lets a tap and a drag share the same control with no gesture
    // conflict, a well-established Compose combination for exactly this pairing.
    // TrailheadControls follows the same side flip because it is laid out inside the
    // container, and nothing inside it needs mirroring (the since-removed DistanceArm
    // extended downward, side-agnostic by construction, for the same reason).
    val mapIconBarSideAlignment = state.sideAlignment
    val mapIconBarPositionOffset = Modifier.offset {
        IntOffset(state.horizontalDragPx.roundToInt(), state.displayedOffsetPx.value.roundToInt())
    }
    // Icon-bar-drag-refinements dispatch, Item 4: the bar cannot be dragged up far
    // enough to rise above where SearchDropdown itself starts. compactMainScaffold's
    // own searchDropdownTopOffset (a different, outer composable scope, not reachable
    // from here) is searchBarHeight + compassStripClearance; topInset (this composable's
    // own parameter, ≈ searchBarHeight — see that parameter's own doc comment) plus this
    // exact scope's own compassStripClearance above equal the same value, reachable
    // here without new plumbing — and already the established way this file computes
    // "how far below the top the search chrome reaches" (see the taxon filter chip's
    // own topInset + compassStripClearance padding a little further down).
    val dropdownTopPx = topLimitPx
    // Stale-clamp-bound dispatch (owner finding on device): every input to the clamp
    // below must be *live state*, never a plain value closed over. mapIconBarDragModifier's
    // pointerInput(Unit) block is started lazily on the first pointer event and never
    // restarted (its key is Unit, and a changed lambda instance does not restart it),
    // so the drag callback keeps the closure from the user's *first drag* for the life
    // of the handle. isFullscreen (a plain Boolean parameter) and dropdownTopPx (a
    // plain Float) were captured that way: whichever fullscreen state existed at the
    // first drag bounded every later drag — a first drag in fullscreen let later drags
    // outside it pass under the nav; a first drag outside it capped later fullscreen
    // drags at the nav's former top — while the LaunchedEffect below, re-run per
    // recomposition, always read the fresh values and corrected the position, which
    // the next drag then undid. Reproduced under Robolectric (enter fullscreen, drag
    // low, exit, drag low: 640dp vs the nav's 560dp top) before this fix. The nav's
    // height does not vary by theme; the theme the owner noticed was a different
    // first-drag order after the tab change a theme switch goes through. Every other
    // clamp input is already a MutableState delegate, read live. rememberUpdatedState
    // is the standard shape for a long-lived gesture block reading composition values —
    // one clamp, derived live, used by the drag path and the effect alike; no path
    // holds its own copy, and nothing re-runs the effect more often to paper over it.
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentDropdownTopPx by rememberUpdatedState(dropdownTopPx)
    // Part 1 layout fixes (the owner's "2 A", planner message 2026-09-29-04): not in short
    // landscape, where the legend now sits beside the cluster and no longer lies below it.
    // Map layers L0b (Q4): the chip's top, only while the chip is on the cluster's side
    // (it sits at the bottom-end corner), less a gap, as a further lowest edge for the
    // cluster. Display-only, like the nav's: the remembered position is never changed.
    val legendClusterGapPx = with(compassStripDensity) { Spacing.sm.toPx() }
    val legendBoundPx = state.legendChipTopPx?.takeIf { !state.isOnLeftSide && !state.landscape }?.let { it - legendClusterGapPx }
    val currentLegendBoundPx by rememberUpdatedState(legendBoundPx)
    // See the comment on the LaunchedEffect below for both bounds' derivations.
    fun clampBelowChromeVerticalOffset(offsetPx: Float): Float {
        // The lowest edge the bar may reach: this Box's own bottom in fullscreen, the
        // nav's own top edge otherwise (state.bottomNavHeightPx's own doc comment).
        val navBoundPx = state.mapContentBoxHeightPx - (if (currentIsFullscreen) 0f else state.bottomNavHeightPx)
        val bottomBoundPx = currentLegendBoundPx?.let { minOf(it, navBoundPx) } ?: navBoundPx
        val fallbackDownwardOffsetPx = (bottomBoundPx - state.mapContentBoxHeightPx / 2f - mapIconBarVerticalDragMarginPx).coerceAtLeast(0f)
        val maxDownwardOffsetPx = if (state.clusterHeightPx > 0f) {
            (bottomBoundPx - (state.mapContentBoxHeightPx + state.clusterHeightPx) / 2f).coerceAtLeast(0f)
        } else {
            fallbackDownwardOffsetPx
        }
        // Upward (negative) bound: the bar's own top edge, once centered then shifted
        // by the offset, is (state.mapContentBoxHeightPx - state.clusterHeightPx) / 2 + offset —
        // solved for the smallest offset that keeps that top edge at or below
        // dropdownTopPx, so the bar can't rise into the dropdown's own space
        // (icon-bar-drag-refinements dispatch, Item 4).
        val maxUpwardOffsetPx = if (state.clusterHeightPx > 0f) {
            (currentDropdownTopPx - (state.mapContentBoxHeightPx - state.clusterHeightPx) / 2f)
                // Owner's ruling (a), continuation 2026-09-28-172: in the landscape L the top limit pushes the L down as well as
                // pulling it up, so its top is never above the limit (the caller passes the search bar's bottom). Portrait and the
                // tablet keep 0 as the upper bound: the limit only ever raised a cluster that sat above it, never lowered a centred one.
                .coerceIn(-fallbackDownwardOffsetPx, if (state.landscape) Float.POSITIVE_INFINITY else 0f)
        } else {
            -fallbackDownwardOffsetPx
        }
        // Part 1 layout fixes, item 2 (the owner's Q4 ruling, "the cluster moves up when the
        // legend expands"; planner message 2026-09-28-98): the floor above keeps the cluster
        // at its centred position at the least, so a legend reaching above the centred
        // cluster's bottom was overlapped rather than cleared (122 px on the S22). Where the
        // legend is the lowest edge and its edge is above the centred bottom, the cluster
        // rises above centre: the downward limit is the legend's own (negative) offset, and
        // the upward limit reaches as far as that needs and never past the dropdown's top,
        // which still wins where the two meet. The nav's floor, and every state without a
        // legend, is unchanged.
        val legendLiftPx = currentLegendBoundPx
            ?.takeIf { it <= navBoundPx && state.clusterHeightPx > 0f }
            ?.let { it - (state.mapContentBoxHeightPx + state.clusterHeightPx) / 2f }
            ?.takeIf { it < 0f }
        if (legendLiftPx != null) {
            val dropdownLimitPx = currentDropdownTopPx - (state.mapContentBoxHeightPx - state.clusterHeightPx) / 2f
            val liftedUpwardOffsetPx = minOf(maxUpwardOffsetPx, maxOf(legendLiftPx, dropdownLimitPx))
            return offsetPx.coerceIn(liftedUpwardOffsetPx, maxOf(liftedUpwardOffsetPx, legendLiftPx))
        }
        return offsetPx.coerceIn(maxUpwardOffsetPx, maxOf(maxUpwardOffsetPx, maxDownwardOffsetPx))
    }
    // Dispatch 2026-09-28-104, item 2: while a search notice shows, the cluster's top is held at or below the
    // notice's measured bottom, as far down as the cluster may go at all (the lowest edge the clamp above
    // allows), and the clamp above already lets it rise back when the notice clears, because this is the
    // user-chosen offset clamped for display, never a change to the memory. Its own function on the clamp above,
    // not a condition inside it. The floor is the offset that puts the cluster's top edge at the notice's bottom:
    // the same arithmetic as the clamp's upward bound (the top edge is (box - cluster) / 2 + offset).
    val currentNoticeBottomPx by rememberUpdatedState(noticeBottomPx)
    fun clampMapIconBarVerticalOffset(offsetPx: Float): Float {
        val clamped = clampBelowChromeVerticalOffset(offsetPx)
        // Owner's ruling (b), continuation 2026-09-28-172: in the landscape L the notice makes room for the L instead (the notice's L-side
        // end is inset, see LocalSearchNoticeInset), so the L stays where it is. Portrait is unchanged.
        if (state.landscape || currentNoticeBottomPx <= 0f || state.clusterHeightPx <= 0f) return clamped
        val noticeFloorPx = currentNoticeBottomPx - (state.mapContentBoxHeightPx - state.clusterHeightPx) / 2f
        val lowestPx = clampBelowChromeVerticalOffset(Float.MAX_VALUE)
        return maxOf(clamped, minOf(noticeFloorPx, lowestPx))
    }
    // T11 fixes (RECORD -766, P4; the owner: "Right, landscape only", with "have the recording pill move back into its original
    // place when the icon bar moves back up"). While navigating, with the L on the navigation display's side of a landscape
    // window, the L is drawn below the display: its top no higher than the display's bottom, the pill beside "+" (see
    // LandscapeLCluster). **Display only**: the floor is applied to what is drawn, never written to the user's stored offset or
    // side (CLAUDE.md, UX defaults), so when navigation ends the L goes back to exactly where the user put it. Where the window
    // is too short for the L under the display (a 360 dp window at font 1.3 or more with an evening line; font 2.0), the L goes
    // as low as it may and the display, drawn after it, covers the shortfall at the top of its first row, Fullscreen (RECORD
    // -768, the owner: "Slide as far as it can (Recommended)"; the shortfalls are in the T11 report's "Fixes").
    val slideApplies = navigationSlideApplies(state.landscape, isNavigating, state.isOnLeftSide, state.railPortEdge) &&
        state.navigationDisplayBottomInRootPx != null
    val navigationFloorPx: Float? = if (slideApplies) (state.navigationDisplayBottomInRootPx ?: 0f) - state.clusterRestTopInRootPx else null
    val currentNavigationFloorPx by rememberUpdatedState(navigationFloorPx)
    fun displayedOffsetFor(userOffsetPx: Float): Float {
        val clamped = clampMapIconBarVerticalOffset(userOffsetPx)
        val floor = currentNavigationFloorPx ?: return clamped
        val lowest = clampBelowChromeVerticalOffset(Float.MAX_VALUE)
        return maxOf(clamped, minOf(floor, lowest))
    }
    // RECORD -770 (the owner: "Drop the evening line then (Recommended)"): how low the bar can go with the pill beside "+",
    // from the clamps' own arithmetic (the L's content top is (box - L) / 2 + offset, its bottom no lower than the nav's bound),
    // at the bar's own height rather than the L's current one, so it does not move while the pill does. Published for the
    // display, which leaves out its evening line rather than push the bar past its Fullscreen row.
    val navigationLowestBarTopPx: Float? = run {
        val barHeightPx = 2f * state.centreInClusterPx
        if (!state.landscape || state.clusterHeightPx <= 0f || barHeightPx <= 0f) return@run null
        val boxTopInRootPx = state.clusterRestTopInRootPx - (state.mapContentBoxHeightPx - state.clusterHeightPx) / 2f
        val navBoundPx = state.mapContentBoxHeightPx - (if (isFullscreen) 0f else state.bottomNavHeightPx)
        boxTopInRootPx + navBoundPx - barHeightPx
    }
    androidx.compose.runtime.SideEffect { state.navigationLowestBarTopInRootPx = navigationLowestBarTopPx }
    // The pill's move, under the bar (0) to beside "+" (1), on the cluster's own navigation spec; a cut under reduced motion.
    val pillBeside = remember { Animatable(if (slideApplies) 1f else 0f) }
    val reduceMotionForSlide = LocalReduceMotion.current
    val pillSpec = MotionTokens.navigationMotionSpec<Float>()
    LaunchedEffect(slideApplies, reduceMotionForSlide) {
        val target = if (slideApplies) 1f else 0f
        if (reduceMotionForSlide) pillBeside.snapTo(target) else pillBeside.animateTo(target, pillSpec)
    }
    // Expanded-panels dispatch: where AddActionTile below anchors — the bar's live
    // position, not its default one. (The map mode popover anchored here too until map
    // layers L0b replaced it with the Layers sheet, a bottom sheet with no anchor.)
    // Panels align to the same edge the bar is on (mapIconBarSideAlignment) and are
    // inset from it by the bar's own MAP_ICON_BAR_EDGE_INSET, so a panel's outer edge
    // lands exactly on the bar's
    // outer edge on either side (the same overlap the old fixed CenterEnd/-Spacing.sm
    // pair produced on the right, now mirrored on the left with a positive inset).
    // The vertical term is the bar's own drag offset (the same px
    // mapIconBarPositionOffset applies to the bar), converted to dp for
    // DpOffset; each caller adds its own row's mapIconBarRowAnchorOffset on top. The
    // horizontal drag px is included too — it is always zero once a drag ends, and no
    // panel can open mid-drag (the finger is on the handle), so this is parity with
    // mapIconBarPositionOffset rather than a visible effect.
    // Keyed on the two edges (landscape B2, S6): the gesture block keeps the closure it
    // started with, so a turn (portrait to landscape, or 90 to 270) must restart it or a
    // drag would write through the previous orientation's position and side. Constant
    // in portrait (both null), so portrait behaves as the Unit key did.
    // Motion Part 1 (dispatch 2026-09-28-652, item 6; scout B14, B15; the owner, RECORD -651: "Glide to its side", and back when
    // released short). Before, a release moved the cluster from the finger to its edge in one frame. Now **where it takes touches
    // moves exactly as before**, in that one frame, and only what is drawn glides there: the layout lands at once and a draw-only
    // translation ([clusterGlideDraw]) starts the drawing at the finger and runs it to the layout. A draw translation is not part
    // of hit testing, so nothing sweeps across the map catching touches mid-glide (the dispatch's condition). The handle side and
    // the landscape pill's button order change the moment the finger lifts, so it glides there already in its new order and
    // every touch is as it always was throughout (Amendment 1, RECORD -657, the owner: "At release"). Under reduced motion the
    // cluster moves at once, as before.
    val reduceMotionNow by rememberUpdatedState(LocalReduceMotion.current)
    val glideSpec = MotionTokens.navigationMotionSpec<Float>()
    fun startGlide() {
        val glide = state.glide
        glide.job?.cancel()
        // Set now, in the same snapshot as the release, so the first frame after it already draws the cluster at the finger.
        glide.remaining = 1f
        glide.job = mapIconBarOffsetScope.launch {
            try {
                animate(initialValue = 1f, targetValue = 0f, animationSpec = glideSpec) { value, _ -> glide.remaining = value }
            } finally {
                // Landed, or cut short by a new drag: either way it is drawn where it is laid out. Only if this is still the
                // current glide: a glide replaced by the next release must not reset the one that replaced it.
                if (glide.job === coroutineContext[Job]) glide.remaining = 0f
            }
        }
    }
    // RECORD -955: fixed to the screen's left while the window is landscape and navigation is on. When that starts or ends and the
    // side it gives differs from what is drawn, the bar glides there from where it is, as after a drag (or moves at once under
    // reduced motion). Read after layout (SideEffect), so the glide starts from the side last drawn.
    val fixedLeftNow = state.landscape && isNavigating
    SideEffect {
        if (state.fixedLeft != fixedLeftNow) {
            val wasLeft = state.isOnLeftSide
            val from = state.glide.leftInRootPx(minimized = state.isMinimized)
            state.fixedLeft = fixedLeftNow
            if (state.isOnLeftSide != wasLeft && !reduceMotionNow && from != null) {
                state.glide.fromLeftInRootPx = from
                startGlide()
            }
        }
    }
    // The release, as it always was (a side change past the threshold on a drag's end, none on a cancel), plus the glide.
    fun release(commitSide: Boolean) {
        val from = state.glide.leftInRootPx(minimized = state.isMinimized)
        val glides = !reduceMotionNow && state.horizontalDragPx != 0f && from != null
        if (commitSide) {
            when {
                state.horizontalDragPx <= -mapIconBarSideSnapThresholdPx -> state.isOnLeftSide = true
                state.horizontalDragPx >= mapIconBarSideSnapThresholdPx -> state.isOnLeftSide = false
            }
        }
        state.horizontalDragPx = 0f
        // RECORD -711: a drag has ended, so its side and height are stored (AvailabilityScreen watches this).
        state.position.onDragSettled()
        if (glides) {
            state.glide.fromLeftInRootPx = from
            startGlide()
        }
    }
    val mapIconBarDragModifier = Modifier.pointerInput(state.railPortEdge, state.punchHoleEdge) {
        detectDragGesturesAfterLongPress(
            // A new drag takes the cluster from wherever it is drawn: any glide still running ends where its layout already is.
            onDragStart = { state.glide.job?.cancel() },
            onDragEnd = { release(commitSide = true) },
            onDragCancel = { release(commitSide = false) },
        ) { change, dragAmount ->
            change.consume()
            state.horizontalDragPx += dragAmount.x
            // The finger is the source of truth during a drag: the clamped position
            // becomes the memory and is drawn immediately (snapTo, which also cancels
            // any bounds-change glide still in flight) — see
            // state.userChosenOffsetPx's own doc comment.
            // T11 fixes: while the L is held below the navigation display, the finger moves the stored offset by its own
            // distance, and what is drawn is that offset with the floor applied: a drag there never writes the floor itself into
            // the memory. Otherwise unchanged (displayedOffsetFor is the plain clamp when there is no floor).
            val fromPx = if (currentNavigationFloorPx != null) state.userChosenOffsetPx else state.displayedOffsetPx.value
            val draggedToPx = clampMapIconBarVerticalOffset(fromPx + dragAmount.y)
            state.userChosenOffsetPx = draggedToPx
            mapIconBarOffsetScope.launch { state.displayedOffsetPx.snapTo(displayedOffsetFor(draggedToPx)) }
        }
    }
    // Expanded-panels dispatch (sweep finding, owner-approved "fix the clamp"): the
    // bar's own measured top and bottom edges both stay on screen now, not just "at
    // least one touch target's worth of it". The old downward bound (bar centre no
    // further than MIN_TOUCH_TARGET above the Box's bottom) let the bar's last two
    // rows — layers and add, the rows the map mode popover (since replaced by the
    // Layers sheet) and AddActionTile anchored to — leave the screen at the bottom of
    // the drag range, which would have carried both panels off with them once they
    // followed the bar. Symmetric with Item 4's own upward bound: the bar's bottom edge, once centered then shifted by the
    // offset, is (state.mapContentBoxHeightPx + state.clusterHeightPx) / 2 + offset — solved
    // for the largest offset that keeps it at or above the lowest reachable edge
    // (the nav's top outside fullscreen, this Box's bottom in it — the nav is drawn
    // over this bar, so "on screen" alone would still leave the bottom rows under
    // it, untappable; a decision taken beyond the approved "keep the bottom edge on
    // screen", reported as such). Falls back to the old margin-based bound before
    // state.clusterHeightPx has its first real measurement, same as the upward bound
    // always did. Re-applied (the LaunchedEffect below) whenever a bound's input
    // changes, not only during a drag: a bar dragged to the very bottom while
    // fullscreen would otherwise end up under the nav once fullscreen is exited — the
    // same untappable-rows outcome this fix exists to rule out, just reached by a
    // different route. (The other route this used to catch — the restore handle
    // dragged lower than the bar may sit — no longer exists: the handle is bounded by
    // the bar's own measured height now, see state.clusterHeightPx's own doc comment.)
    //
    // Icon-bar-position-memory dispatch: the target is always the clamp of the
    // *user-chosen* offset, never of the displayed one, and the move is animated on
    // the nav's own spec — so the push-up on leaving fullscreen and the glide back on
    // re-entry read as one behaviour, and the memory survives the push untouched. See
    // state.userChosenOffsetPx's own doc comment. Not keyed on the memory itself:
    // a drag snaps the displayed value directly and is never animated.
    val mapIconBarOffsetSpec = MotionTokens.navigationMotionSpec<Float>()
    // RECORD -729: in the landscape L the top limit is the search bar's settled bottom, which now follows the compass strip's
    // measured height (36 dp at the default font, not the bar's own 45), so the L is re-fitted when it changes rather than kept
    // at the clamp of the first frame, before the strip was measured. A key only in landscape: portrait's limit animates with
    // the fullscreen slide (CompactMapTab's searchBarBottom comment), and portrait is unchanged.
    val landscapeTopLimitKey = if (state.landscape) topLimitPx else 0f
    // T11 fixes: the navigation floor is a key too, so the L slides down when navigation starts, follows the display as its
    // evening line comes and goes, and slides back up when navigation ends; a cut under reduced motion for that move alone.
    val previousFloor = remember { arrayOfNulls<Float>(1) }
    LaunchedEffect(state.clusterHeightPx, state.mapContentBoxHeightPx, state.bottomNavHeightPx, isFullscreen, state.landscape, legendBoundPx, currentNoticeBottomPx, landscapeTopLimitKey, navigationFloorPx) {
        val targetPx = displayedOffsetFor(state.userChosenOffsetPx)
        val floorMoved = previousFloor[0] != navigationFloorPx
        previousFloor[0] = navigationFloorPx
        // RECORD -711: a height restored at launch is fitted to this window's limits with a snap, not a
        // glide, so it never slides in from beyond them; from the first measured fit on, as before.
        if (state.snapRestored) {
            if (targetPx != state.displayedOffsetPx.value) state.displayedOffsetPx.snapTo(targetPx)
            if (state.clusterHeightPx > 0f) state.snapRestored = false
        } else if (targetPx != state.displayedOffsetPx.value) {
            if (floorMoved && reduceMotionForSlide) state.displayedOffsetPx.snapTo(targetPx) else state.displayedOffsetPx.animateTo(targetPx, mapIconBarOffsetSpec)
        }
    }
    // Owner request (alongside the fullscreen-slide-out-fixes dispatch): minimising
    // slides this cluster off whichever edge it's on, and the restore handle slides in
    // from that same edge, instead of the instant cut this used to be — "like the rest
    // of the UI," i.e. the same AnimatedVisibility slide SearchEntryBar and
    // ForagerBottomNav use for fullscreen. navigationMotionSpec(), the nav's own slide
    // spec — this is navigation chrome, not a panel. Pure translations of Box
    // children, no effect on this Box's own size, same reasoning as those two slides.
    //
    // Icon-bar-unify-container dispatch: what used to be three wrappers (bar, minimize
    // handle, TrailheadControls, each aligned separately and each trusting the others
    // to land in the right place) is now one wrapper around one filled container —
    // MapIconBar and TrailheadControls in a Column, the gap between them the Column's
    // own spacing rather than an offset from a measured bottom edge. The container is
    // what gets measured (state.clusterHeightPx), dragged, clamped and minimised, so
    // the bound is right by construction. The minimize handle is a *sibling* of the
    // container, not a child: Surface clips to its shape, and the handle's visible
    // mark straddles the container's outer edge by design, so inside it half the mark
    // would vanish. Both handles sit at the bar's own mid-height, not the container's
    // (mapIconBarCentreShiftOffset below), which is what "mid-height of the icon bar"
    // has always meant; the restore handle uses the last-measured values since the
    // bar is unmounted while minimised. The cluster, not the bar, is what's centred
    // at rest — so the bar sits ~60dp higher by default than it did as a lone
    // centred object. Owner's call: a default derived from the container is honest,
    // and correcting it back to preserve the old look would reintroduce exactly the
    // bar-specific arithmetic the container exists to remove.
    val mapIconBarSlideOffset: (Int) -> Int = { fullWidth -> if (state.isOnLeftSide) -fullWidth else fullWidth }
    val mapIconBarCentreShiftOffset = Modifier.offset {
        IntOffset(0, (state.centreInClusterPx - state.clusterHeightPx / 2f).roundToInt())
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = state.isMinimized,
        enter = slideInHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), initialOffsetX = mapIconBarSlideOffset),
        exit = slideOutHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), targetOffsetX = mapIconBarSlideOffset),
        modifier = Modifier
            .align(mapIconBarSideAlignment)
            .padding(controlsPadding)
            .then(mapIconBarPositionOffset)
            .then(mapIconBarCentreShiftOffset)
            .clusterGlideDraw(state.glide, minimized = true),
    ) {
        MapIconBarRestoreHandle(
            onRestore = { state.isMinimized = false },
            onLeftSide = state.isOnLeftSide,
            // Reports nothing into state.clusterHeightPx — its drag is clamped to
            // the cluster's own range, see that variable's doc comment.
            modifier = Modifier.then(mapIconBarDragModifier),
        )
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = !state.isMinimized,
        enter = slideInHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), initialOffsetX = mapIconBarSlideOffset),
        exit = slideOutHorizontally(animationSpec = MotionTokens.navigationMotionSpec(), targetOffsetX = mapIconBarSlideOffset),
        modifier = Modifier
            .align(mapIconBarSideAlignment)
            // Landscape B1: clear of the overlaid rail and the cut-out band.
            .padding(controlsPadding)
            .then(mapIconBarPositionOffset)
            .clusterGlideDraw(state.glide, minimized = false),
    ) {
        Box {
            // Feeds both drag clamps above — see state.clusterHeightPx's own doc comment. Measured on the container (the portrait
            // Surface, the landscape L's Box), never on its contents.
            val clusterMeasure = Modifier
                .padding(MAP_ICON_BAR_EDGE_INSET)
                .mapKeepOut(MapKeepOutIds.CLUSTER)
                .onGloballyPositioned { coordinates ->
                    state.clusterHeightPx = coordinates.size.height.toFloat()
                    state.clusterWidthPx = coordinates.size.width.toFloat()
                    state.clusterBoundsInRoot = coordinates.boundsInRoot()
                    // T11 fixes: the top at offset 0, read in the same pass the offset was placed with.
                    state.clusterRestTopInRootPx = coordinates.positionInRoot().y - state.displayedOffsetPx.value
                }
                .testTag(MAP_ICON_CLUSTER_TAG)
            // Feeds the panels' and handles' anchors — see MapIconClusterState.centreInClusterPx.
            val barMeasure = Modifier.onGloballyPositioned { coordinates -> state.centreInClusterPx = coordinates.boundsInParent().center.y }
            if (state.landscape) {
                // Landscape L (dispatch 2026-09-28-160; the owner: "Oh yeah on either side it looks like an L", and, on the height,
                // "A"). No container Surface: nothing is drawn around the L, and this Box draws nothing and takes no pointer input, so
                // the corner inboard of the bar and the gap between bar and pill reach the map.
                Box(modifier = clusterMeasure) {
                    LandscapeLCluster(
                        onLeftSide = state.isOnLeftSide,
                        pillBeside = pillBeside.value,
                        bar = { (landscapeBar ?: bar)(barMeasure) },
                        pill = { (landscapePill ?: pill)(state.isOnLeftSide) },
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
                    // The lighter of the two layered fills — see
                    // MAP_ICON_CLUSTER_CONTAINER_ALPHA's own doc comment for the
                    // compositing arithmetic and the values chosen.
                    color = mapIconClusterContainerColor(),
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, mapIconStackBorderColor()),
                    modifier = clusterMeasure.mapChromeContainerColor(mapIconClusterContainerColor()),
                ) {
                    Column(
                        horizontalAlignment = if (state.isOnLeftSide) Alignment.Start else Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR),
                    ) {
                        bar(barMeasure)
                        pill(state.isOnLeftSide)
                    }
                }
            }
            MapIconBarMinimizeHandle(
                onMinimize = { state.isMinimized = true },
                onLeftSide = state.isOnLeftSide,
                // Owner's ruling (c), continuation 2026-09-28-172: in the landscape L the box is one row tall, centred on the locate row.
                tapHeight = if (state.landscape) MIN_TOUCH_TARGET else HANDLE_DEFAULT_TAP_HEIGHT,
                modifier = Modifier
                    .align(mapIconBarSideAlignment)
                    .then(mapIconBarCentreShiftOffset)
                    .then(mapIconBarDragModifier),
            )
        }
    }
}

/** The cluster container's own `Surface` — what tests measure the cluster's real extent by (icon-bar-unify-container dispatch). */
internal const val MAP_ICON_CLUSTER_TAG = "map-icon-cluster"

/**
 * Draws this box translated from where [glide] started to where it is laid out, by the part of the glide still to run. Drawing
 * only: hit testing follows the layout, so the box takes touches where it lands from the first frame, exactly as before the
 * glide existed, and never along the way. Records the box's coordinates for [ClusterGlide.leftInRootPx].
 */
private fun Modifier.clusterGlideDraw(glide: ClusterGlide, minimized: Boolean): Modifier = this
    .onPlaced { coordinates -> if (minimized) glide.handleCoordinates = coordinates else glide.clusterCoordinates = coordinates }
    .drawWithContent {
        val remaining = glide.remaining
        val at = (if (minimized) glide.handleCoordinates else glide.clusterCoordinates)?.takeIf { it.isAttached }
        val dx = if (remaining == 0f || at == null) 0f else (glide.fromLeftInRootPx - at.positionInRoot().x) * remaining
        translate(left = dx) { this@drawWithContent.drawContent() }
    }
