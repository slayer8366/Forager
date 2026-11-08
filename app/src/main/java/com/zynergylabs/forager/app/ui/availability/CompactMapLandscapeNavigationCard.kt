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
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clipToBounds
import android.util.Log
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion

/**
 * RECORD -955, C: the highest the landscape card's top may reach, in the root: the walker's dot's bottom, and, where the L is in the
 * card's columns, the bottom of Reset north (the L's second row). `null` before the map is measured.
 */
private fun cardTopLimitInRootPx(cluster: MapIconClusterState, density: androidx.compose.ui.unit.Density): Float? {
    val dotY = walkerDotCentreInRootPx(cluster) ?: return null
    val dotBottom = dotY + with(density) { WALKER_DOT_CLEARANCE.toPx() }
    val l = lInCardColumns(cluster)
    val resetBottom = if (l != null) {
        l.top + with(density) { (com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET * 2 + com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_LANDSCAPE_ROW_SPACING).toPx() }
    } else {
        null
    }
    return maxOf(dotBottom, resetBottom ?: dotBottom)
}

/** RECORD -955: the L's bounds in the root where it shares the card's columns; `null` otherwise. */
private fun lInCardColumns(cluster: MapIconClusterState): androidx.compose.ui.geometry.Rect? {
    val l = cluster.clusterBoundsInRoot
    val card = cluster.navigationCardBoundsInRoot
    return l?.takeIf { card != null && l.left < card.right && card.left < l.right }
}

private const val LIFT_LOG_TAG = "NavigationCard"

/** RECORD -955: the walker's dot's radius, the clearance the card keeps below its centre (the tests' WALKER_DOT_RADIUS). */
internal val WALKER_DOT_CLEARANCE = 12.dp

/**
 * RECORD -953: the landscape navigation card's motion, `navigationChromeTransitions` (removed 2026-10-19, RECORD -1392) flipped: it slides up from the screen's bottom
 * edge by its own height, with a fade, on the same timing, and back down; under reduced motion it only fades.
 */
@Composable
internal fun navigationChromeFromBottomTransitions(): Pair<EnterTransition, ExitTransition> {
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
    /**
     * D32 (RECORD -1987, the owner: "Both cards narrow (Recommended)"): while the route card rests on this card, how much narrower both
     * are from the L's side, so Re-center's icon fits beside the L at the bottom; the right edge is kept. 0 dp otherwise.
     */
    narrowBy: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val railPortEdge = landscape.railPortEdge ?: return
    val isNavigating = navigation.isNavigating
    val density = LocalDensity.current
    val (bottomEnter, bottomExit) = navigationChromeFromBottomTransitions()
    // Dispatch 2026-11-06-01, Amendment 1, J3 step 3 and J4 (RECORD -2183, the owner: "Own line, card moves up", "Follow the largest
    // rule"): in landscape at font 1.5 or more with an evening line, where that line would take the card's top over the walker's dot,
    // the card moves up until its bottom is at the dot's top, and the line stays on it ([navigationCardLift]).
    var heightWithEveningPx by remember { mutableFloatStateOf(Float.NaN) }
    var landscapeLargeText by remember { mutableStateOf(false) }
    val topLimitPx = cardTopLimitInRootPx(cluster, density)
    val boxTopPx = cluster.mapContentBoxTopInRootPx
    val stripBottomPx = with(density) { boxTopPx + controlsPadding.calculateTopPadding().toPx() } + layout.landscapeStripHeightPx
    val lInColumns = lInCardColumns(cluster)
    // J7 (RECORD -2183, the owner: "(b) Arrow to the right part"): whether the card with its evening line could fit even moved up is
    // decided whether or not the line steps aside for the route card (D42), so the next turn's place can tell the line off for room
    // from the line stepped aside only for the route card. Stepping aside, the card does not move ([lift]), as before.
    val roomLift = navigationCardLift(
        liftCase = landscapeLargeText,
        heightWithEveningPx = heightWithEveningPx,
        bottomEdgePx = with(density) { boxTopPx + cluster.mapContentBoxHeightPx - controlsPadding.calculateBottomPadding().toPx() },
        topLimitPx = topLimitPx,
        dotTopPx = walkerDotCentreInRootPx(cluster)?.let { it - with(density) { WALKER_DOT_CLEARANCE.toPx() } },
        stripBottomPx = stripBottomPx.takeIf { layout.landscapeStripHeightPx > 0 },
        lTopPx = lInColumns?.top,
        lBottomPx = lInColumns?.bottom,
    )
    val lift = if (cluster.navigationEveningStepsAside) NavigationCardLift.OnBottomEdge else roomLift
    val noRoom = lift as? NavigationCardLift.NoRoom
    LaunchedEffect(noRoom) { if (noRoom != null) Log.i(LIFT_LOG_TAG, "J3 step 3: the card stays on the bottom edge and the evening line leaves it (RECORD -955): ${noRoom.reason}") }
    val liftPx = (lift as? NavigationCardLift.Up)?.px ?: 0f
    val movedUp = liftPx > 0f
    // J5 (RECORD -2183, the owner: "Animate the move"): the card glides to its place and back rather than jumping, on its own slide's
    // timing (RECORD -953's), or moves at once under reduced motion, as its slide becomes a fade there. What is decided from the card's
    // place (the evening line's limit, D42's height with the evening lines) reads where it is going, [liftPx]; what is drawn reads
    // where it is, [drawnLiftPx].
    val reduceMotion = LocalReduceMotion.current
    val drawnLiftPx by animateFloatAsState(
        targetValue = liftPx,
        animationSpec = if (reduceMotion) snap() else MotionTokens.navigationViewChromeSpec(),
        label = "navigationCardLift",
    )
    // J4 (RECORD -2183, the owner: "Round and outline"): once moved up off the screen's bottom edge, its bottom corners round and its
    // outline goes all round. J5: read from where it is drawn, so gliding back down it keeps them until it is on the edge again.
    val bottomRoundedAndOutlined = drawnLiftPx > 0.5f
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
        // J4 (RECORD -2183, the owner: "Round and outline"): once moved up off the screen's bottom edge, the outline goes all round.
        bottom = bottomRoundedAndOutlined,
    )
    val (sundownLine, backByLine) = compactMapEveningLines(uiState, currentTime, recordingSundownLine, quickSettings, sundownClock)
    // RECORD -953: the L is not told the card's edge, so it does not slide (see this function's doc).
    LaunchedEffect(Unit) {
        cluster.navigationDisplayBottomInRootPx = null
        // D42: measured afresh for each navigation.
        cluster.navigationCardHeightWithEveningPx = null
    }
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
            // J3 step 3: moved up where it must be ([navigationCardLift]); 0 on the bottom edge.
            .offset { IntOffset(0, -drawnLiftPx.roundToInt()) }
            .width(with(density) { (layout.landscapeStripWidthPx.toDp() - narrowBy).coerceAtLeast(0.dp) })
            // E21 (RECORD -2181, the owner: one shared line, no 1 dp step): clipped as before, but open a stroke beyond the side-bar edge
            // over the side bar's rounded bottom corner, where the card's end line continues the side bar's ([navigationCardEndBesideRailCorner]).
            .clipToBoundsOpenBesideRailCorner(com.zynergylabs.forager.app.ui.map.MAP_CHROME_OUTLINE_WIDTH, railPortEdge, railShown = !isFullscreen, bottomGap = controlsPadding.calculateBottomPadding())
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
            // Dispatch -502: the straight-line display (decision D2) for a chosen waypoint, until step 6 PR 3 (dispatch 2026-10-26-11):
            // the way to it along trails when there is one, "by trail", or the straight line with its note (WaypointRouteShown).
            route = if (navigation.isNavigatingToWaypoint) uiState.waypointRoute?.hud else navigation.returnRoute,
            onRetryRoute = navigation.onRetryRoute,
            facing = navigationFacing,
            currentTime = currentTime,
            showDecimalDegrees = showDecimalDegrees,
            onExit = navigation.onToggleReturning,
            // RECORD -845: "Arrived", held, after a return ended on arrival.
            arrivalHeld = navigation.isArrivalHeld,
            // Dispatch 2026-10-28-13: the strip's own height, so the card cannot read another.
            heightMeters = uiState.liveAltitudeMeters,
            sundownLine = sundownLine,
            backByLine = backByLine,
            // RECORD -955, C ("C yes", extending -770): the evening lines leave the card where they would take its top over Reset
            // north or the walker's dot. The dot sits where the navigation view puts it (the map's height times
            // NAVIGATION_VIEW_TOP_PADDING_FRACTION as top padding), WALKER_DOT_CLEARANCE its radius. Reset north is the L's second
            // row, counted only where the L and the card share columns.
            // J3 step 3: moved up, its bottom is at the dot's top, so the limit is the compass strip's bottom.
            eveningLineTopLimitInRootPx = if (movedUp) stripBottomPx else topLimitPx,
            // D42 (RECORD -2021, the owner: "Only where room is short"): they also step aside while the route card resting on this one
            // would not otherwise fit (the route card decides, from this card's measured heights), and come back when it closes.
            eveningStepsAside = cluster.navigationEveningStepsAside,
            onEveningShown = { cluster.navigationEveningShown = it },
            // J7: the evening line can't fit even moved up, so the next turn takes the right part, as in daytime.
            eveningOffForRoom = roomLift is NavigationCardLift.NoRoom,
            onHeightWithEvening = { height, large -> heightWithEveningPx = height; landscapeLargeText = large },
            // RECORD -953: its top two corners round; its bottom is square on the screen edge. No tuck. J4 (RECORD -2183, the owner:
            // "Round and outline"): moved up off that edge, its bottom corners round too.
            cardCorners = if (bottomRoundedAndOutlined) MapCardCorners.ALL else MapCardCorners.TOP,
            outlineSides = cardOutlineSides,
            modifier = Modifier
                .fillMaxWidth()
                // E20 (RECORD -2181, the owner: "Outline the card's end there"): its end edge beside the side bar's rounded bottom corner.
                .navigationCardEndBesideRailCorner(com.zynergylabs.forager.app.ui.map.mapChromeOutline(), railPortEdge, railShown = !isFullscreen, bottomGap = controlsPadding.calculateBottomPadding() + with(density) { drawnLiftPx.toDp() })
                // Its own keep-out, beside the strip's (MapKeepOut's registry holds one rectangle per id).
                .then(if (isNavigating) Modifier.mapKeepOut(MapKeepOutIds.NAVIGATION_CARD) else Modifier)
                // RECORD -955: its columns, for cardTopLimitInRootPx (whether the L shares them).
                .onGloballyPositioned { coordinates ->
                    cluster.navigationCardBoundsInRoot = coordinates.boundsInRoot()
                    // D42: the tallest it has been with its evening lines, so a frame of their grow or shrink is never taken for the
                    // card's height with them (that fed the route card's decision back into itself: Compose never idle, run 41753745001).
                    // J3 step 3: moved up, its height plus the move, so D42's top with the evening lines (the box's bottom less this)
                    // is where the moved card's top is, and stepping aside does not drop the card back into room it then reads as free.
                    if (cluster.navigationEveningShown) {
                        cluster.navigationCardHeightWithEveningPx = maxOf(cluster.navigationCardHeightWithEveningPx ?: 0f, coordinates.size.height.toFloat() + liftPx)
                    }
                },
        )
    }
    }
}
