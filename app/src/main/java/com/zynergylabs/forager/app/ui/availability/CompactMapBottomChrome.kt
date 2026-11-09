package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-21, UI split Phase 1, PR 9c).

import androidx.compose.foundation.layout.BoxScope
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.ui.map.MapLayersSheet
import com.zynergylabs.forager.app.ui.map.MapLegendChip
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.mapLegendFor
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.MapPopUp
import com.zynergylabs.forager.app.ui.motion.PopUpPivot
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.theme.Spacing

/** "Return to Route", the legend, the bottom bar and the rail, moved out of [CompactMapTab] unchanged (RECORD -741). */
@Composable
internal fun BoxScope.CompactMapBottomChrome(
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
                // RECORD -952: its two corners facing the map rounded, as the portrait bottom bar's top two (RECORD -937).
                innerCornerRadius = MAP_CARD_CORNER_RADIUS,
                modifier = Modifier.mapKeepOut(MapKeepOutIds.RAIL).onGloballyPositioned { coordinates ->
                    onRailWidthMeasured(coordinates.size.width.toFloat())
                },
            )
        }
    }
}
