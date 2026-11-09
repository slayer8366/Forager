package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityCompactMapUi.kt (dispatch 2026-10-19-15, UI split Phase 1, PR 9b).

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.State
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.map.MapKeepOutIds
import com.zynergylabs.forager.app.ui.map.mapKeepOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clipToBounds
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion

/**
 * RECORD -955, C: the highest the landscape card's top may reach, in the root: the walker's dot's bottom, and, where the L is in the
 * card's columns, the bottom of Reset north (the L's second row). `null` before the map is measured.
 */
private fun cardTopLimitInRootPx(cluster: MapIconClusterState, density: androidx.compose.ui.unit.Density): Float? {
    val mapHeight = cluster.mapContentBoxHeightPx
    if (mapHeight <= 0f) return null
    val dotY = cluster.mapContentBoxTopInRootPx + (mapHeight + mapHeight * com.zynergylabs.forager.app.ui.map.NAVIGATION_VIEW_TOP_PADDING_FRACTION.toFloat()) / 2f
    val dotBottom = dotY + with(density) { WALKER_DOT_CLEARANCE.toPx() }
    val l = cluster.clusterBoundsInRoot
    val card = cluster.navigationCardBoundsInRoot
    val resetBottom = if (l != null && card != null && l.left < card.right && card.left < l.right) {
        l.top + with(density) { (com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET * 2 + com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING).toPx() }
    } else {
        null
    }
    return maxOf(dotBottom, resetBottom ?: dotBottom)
}

/** RECORD -955: the walker's dot's radius, the clearance the card keeps below its centre (the tests' WALKER_DOT_RADIUS). */
internal val WALKER_DOT_CLEARANCE = 12.dp

/**
 * RECORD -953: the landscape navigation card's motion, `navigationChromeTransitions` (removed 2026-10-19, RECORD -1392) flipped: it slides up from the screen's bottom
 * edge by its own height, with a fade, on the same timing, and back down; under reduced motion it only fades.
 */
@Composable
private fun navigationChromeFromBottomTransitions(): Pair<EnterTransition, ExitTransition> {
    val reduceMotion = LocalReduceMotion.current
    val slide = MotionTokens.navigationViewChromeSpec<IntOffset>()
    val timedFade = MotionTokens.navigationViewChromeSpec<Float>()
    val reducedFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val enter = if (reduceMotion) fadeIn(animationSpec = reducedFade) else slideInVertically(animationSpec = slide) { fullHeight -> fullHeight } + fadeIn(animationSpec = timedFade)
    val exit = if (reduceMotion) fadeOut(animationSpec = reducedFade) else slideOutVertically(animationSpec = slide) { fullHeight -> fullHeight } + fadeOut(animationSpec = timedFade)
    return enter to exit
}

/**
 * Landscape (dispatch 2026-10-12-08, RECORD -946, -950, -951, -953): the navigation card, at the strip's exact width and in its
 * column, a shade darker than the strip, its content and order unchanged.
 *
 * RECORD -953, the owner: "An alternative is to have the card peak from the botton of the screen instead from the top. It can stay
 * beneath the heading, just peaking from the opposite direction"; "Landscape only. Portrait is dialed in"; "It stays exactly how it
 * is now. Just flipped upside down. No rearranging." So the card is anchored to the screen's bottom edge, not hung under the strip:
 * its top two corners rounded, its bottom square on the edge, no tuck, the 0.8 fill and its shadow. It slides up from the bottom
 * when navigation starts and back down when it ends, on the navigation view's timing (Motion Part 2), or only fades under reduced
 * motion. The strip stays exactly where it is. Was (-946 to -951): hung under the strip, tucked, sliding down from under it.
 *
 * Nothing else moves. The landscape L's slide below the display (T11 fixes, P4) is not fed the card's edge any more: below a card
 * on the bottom edge there is nowhere to slide, and sliding it would rearrange it. RECORD -770's limit on the evening lines was
 * worked out from the L below the display, and is not passed either. Whatever the card now covers at the bottom is reported with
 * its numbers, not moved (RECORD -953: "No rearranging").
 *
 * Its width is the strip's measured one (read, never fed back). Composed after the cluster (so its X wins any overlap with the
 * cluster) and before the bottom chrome and rail (so they keep winning their own bands), as the display always was; never touches
 * mapSlot.
 */
@Composable
internal fun BoxScope.CompactMapNavigationDisplay(
    uiState: AvailabilityUiState,
    navigation: CompactMapNavigation,
    landscape: CompactMapLandscape,
    /** Dispatch 2026-10-13-02 (RECORD -967): whether the rail has slid away, leaving the card's side by it facing the map. */
    isFullscreen: Boolean,
    /** T11 fixes (RECORD -766, P4): told the display's bottom edge while navigating, so the landscape L can slide below it. */
    cluster: MapIconClusterState,
    controlsPadding: PaddingValues,
    trueHeading: State<TrueHeadingReading>,
    navigationFacing: NavigationFacing,
    currentTime: CurrentTimeProvider,
    showDecimalDegrees: Boolean,
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine?,
    quickSettings: MapQuickSettings?,
    sundownClock: SundownClock,
    /** The landscape strip's measured size, read here; see [CompactMapLayoutState]. */
    layout: CompactMapLayoutState,
) {
    val railPortEdge = landscape.railPortEdge ?: return
    val isNavigating = navigation.isNavigating
    val density = LocalDensity.current
    val (bottomEnter, bottomExit) = navigationChromeFromBottomTransitions()
    // Dispatch 2026-10-13-02 (RECORD -966, -967): the card's outline: its top and rounded top corners and its side facing the
    // map; its rail side once the rail has slid away, unless that side is then on the screen's edge; its bottom is the screen's.
    val cardLayoutDirection = LocalLayoutDirection.current
    val cardOutlineSides = landscapeColumnOutlineSides(
        railPortEdge = railPortEdge,
        railShown = !isFullscreen,
        railSideInset = if (railPortEdge == ScreenEdge.Left) {
            controlsPadding.calculateLeftPadding(cardLayoutDirection)
        } else {
            controlsPadding.calculateRightPadding(cardLayoutDirection)
        },
        innerSideDrawn = true,
        top = true,
        bottom = false,
    )
    val (sundownLine, backByLine) = compactMapEveningLines(uiState, currentTime, recordingSundownLine, quickSettings, sundownClock)
    // RECORD -953: the L is not told the card's edge, so it does not slide (see this function's doc).
    LaunchedEffect(Unit) { cluster.navigationDisplayBottomInRootPx = null }
    // Before the strip is first measured there is no width to take; nothing is drawn for that frame.
    if (layout.landscapeStripWidthPx <= 0 || layout.landscapeStripHeightPx <= 0) return
    // Motion Part 2, item 3: leaving, it takes no touch and gives up its keep-out at once. RECORD -691: no minimum touch target
    // while it leaves (motion/LeavingTakesNoTouches.kt, NoTouchTargetExpansion).
    NoTouchTargetExpansion(active = !isNavigating) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isNavigating,
        enter = bottomEnter,
        exit = bottomExit,
        modifier = Modifier
            .align(if (railPortEdge == ScreenEdge.Left) Alignment.BottomStart else Alignment.BottomEnd)
            .padding(controlsPadding)
            .width(with(density) { layout.landscapeStripWidthPx.toDp() })
            .clipToBounds()
            .leavingTakesNoTouches(leaving = !isNavigating),
    ) {
        NavigationHud(
            heading = trueHeading,
            liveFix = uiState.liveFix,
            // Dispatch 2026-09-28-510: shown in place of GPS by hudReadout's rule; never measured from for "Arrived".
            approximateFix = uiState.approximateFix,
            lastKnownFix = uiState.lastKnownFix,
            target = navigation.navigationTarget,
            distanceUnit = uiState.distanceUnit,
            // Dispatch -502: no route to a chosen waypoint, so the straight-line HUD (decision D2).
            route = if (navigation.isNavigatingToWaypoint) null else navigation.returnRoute,
            onRetryRoute = navigation.onRetryRoute,
            facing = navigationFacing,
            currentTime = currentTime,
            showDecimalDegrees = showDecimalDegrees,
            onExit = navigation.onToggleReturning,
            // RECORD -845: "Arrived", held, after a return ended on arrival.
            arrivalHeld = navigation.isArrivalHeld,
            sundownLine = sundownLine,
            backByLine = backByLine,
            // RECORD -955, C ("C yes", extending -770): the evening lines leave the card where they would take its top over Reset
            // north or the walker's dot. The dot sits where the navigation view puts it (the map's height times
            // NAVIGATION_VIEW_TOP_PADDING_FRACTION as top padding), WALKER_DOT_CLEARANCE its radius. Reset north is the L's second
            // row, counted only where the L and the card share columns.
            eveningLineTopLimitInRootPx = cardTopLimitInRootPx(cluster, density),
            // RECORD -953: its top two corners round; its bottom is square on the screen edge. No tuck.
            cardCorners = MapCardCorners.TOP,
            outlineSides = cardOutlineSides,
            modifier = Modifier
                .fillMaxWidth()
                // Its own keep-out, beside the strip's (MapKeepOut's registry holds one rectangle per id).
                .then(if (isNavigating) Modifier.mapKeepOut(MapKeepOutIds.NAVIGATION_CARD) else Modifier)
                // RECORD -955: its columns, for cardTopLimitInRootPx (whether the L shares them).
                .onGloballyPositioned { coordinates -> cluster.navigationCardBoundsInRoot = coordinates.boundsInRoot() },
        )
    }
    }
}
