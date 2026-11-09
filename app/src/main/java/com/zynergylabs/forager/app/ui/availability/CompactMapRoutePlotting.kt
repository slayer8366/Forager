package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import androidx.activity.compose.BackHandler
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.route.RouteCardBackStep
import com.zynergylabs.forager.app.domain.route.asRouteStop
import com.zynergylabs.forager.app.domain.route.defaultRouteName
import com.zynergylabs.forager.app.domain.route.legElevation
import com.zynergylabs.forager.app.domain.route.routeFrame
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.map.MapCameraRequest
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.PlottedRouteOnMap
import com.zynergylabs.forager.app.ui.motion.NoTouchTargetExpansion
import com.zynergylabs.forager.app.ui.motion.leavingTakesNoTouches
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import com.zynergylabs.forager.app.ui.route.DiscardRouteDialog
import com.zynergylabs.forager.app.ui.route.RouteCard
import com.zynergylabs.forager.app.ui.route.RouteCardCallbacks
import com.zynergylabs.forager.app.ui.route.RouteCardContent
import com.zynergylabs.forager.app.ui.route.legInFlight
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.route.rememberRouteCardWidth
import com.zynergylabs.forager.app.ui.route.RoutePlotMode
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.SaveRouteDialog
import java.time.LocalDate

/*
 * Route plotting on the Maps tab (dispatch 2026-10-30-08, step 3): how a tap reaches the plotting, the card, its Back tier,
 * its dialogs, the line the map draws and the fit when a saved route opens. Kept out of AvailabilityCompactMapUi.kt, which
 * calls each at its place (the first stop, (g)). Every input arrives in MapRecordSources.routePlotting (the planner's ruling on
 * Q11), so the scaffold is unchanged.
 */

/**
 * Whether route plotting takes [tap] instead of a bubble opening:
 * - a waypoint while plotting is added as the next stop (the first stop, (a));
 * - a planned trip with a route opens the route at once, with no bubble (Amendment 1, Q7: "Opens the route straight away");
 * - a planned trip with no route, while plotting, is added as the next stop, under its name, as a waypoint is (dispatch
 *   2026-11-05-05, A11; RECORD -2148, the owner: "Add it as a stop (Recommended)"). Not plotting, it opens its bubble.
 * Every other tap is the bubble's, as before.
 */
internal fun routePlottingTakesTap(tap: MapFeatureTap, sources: MapRecordSources): Boolean {
    val plotting = sources.routePlotting ?: return false
    // Dispatch 2026-11-06-05, Amendment 2, item 4: on a saved route, a stop's marker is selected too, and its bubble opens as before.
    selectRouteStopOnTap(tap, sources)
    return when (tap.layerId) {
        MapLayerIds.WAYPOINTS -> {
            if (plotting.state.mode !is RoutePlotMode.Plotting) return false
            val waypoint = sources.waypoints.firstOrNull { it.id == tap.featureId }
            if (waypoint == null) {
                Log.w(ROUTE_PLOTTING_LOG_TAG, "Waypoint ${tap.featureId} is not in Records; it was not added to the route.")
            } else {
                plotting.actions.onWaypointTapped(waypoint.asRouteStop())
            }
            true
        }
        MapLayerIds.PLANNED_TRIPS -> {
            val trip = sources.plannedTrips.firstOrNull { it.id == tap.featureId } ?: return false
            when {
                trip.route != null -> plotting.actions.onOpenSavedRoute(trip, fromTripPlanner = false)
                plotting.state.mode is RoutePlotMode.Plotting -> plotting.actions.onWaypointTapped(trip.asRouteStop())
                else -> return false
            }
            true
        }
        else -> false
    }
}

/**
 * The line the map draws: the route being plotted, or a saved one with a dot at each stop (Amendment 1, Q8), and on a saved route its
 * selected stop's marker in the accent colour (dispatch 2026-11-06-05, Amendment 2, item 4; [selectedStopMarker]); `null` for none.
 */
internal fun plottedRouteOnMap(
    controls: RoutePlottingControls?,
    sources: MapRecordSources? = null,
    waypointsDrawn: Boolean = true,
    tripsDrawn: Boolean = true,
): PlottedRouteOnMap? = when (val mode = controls?.state?.mode) {
    // Dispatch 2026-11-02-04 (A2): the stops' names, from RouteStop.name, while plotting and on a saved route.
    is RoutePlotMode.Plotting -> PlottedRouteOnMap(mode.draft.legs, stopNames = mode.draft.stops)
    is RoutePlotMode.Showing -> mode.trip.route?.let { route -> PlottedRouteOnMap(route.legs, route.stops.map { it.position }, route.stops, selectedStopMarker(controls, sources, waypointsDrawn, tripsDrawn)) }
    RoutePlotMode.Off, null -> null
}

/** The most of the map's height the expanded card's rows take before they scroll (the first stop, (b)). */
internal const val ROUTE_CARD_EXPANDED_FRACTION = 0.5f

/** The side margin of a saved route's fit, and the gap kept between the route and the card's top. */
internal const val ROUTE_FIT_MARGIN_DP = 48

/**
 * The fit for a saved route just opened (the dispatch: "moves the map to fit the whole route"), once the card's height
 * ([cardHeightDp]) is known, so the route is fitted above it; `null` otherwise. Its id names the open, so each open fits once,
 * and it ends GPS following (the planner accepted "leaving GPS-follow when a route opens").
 */
internal fun savedRouteCameraRequest(controls: RoutePlottingControls?, cardHeightDp: Int): MapCameraRequest? {
    val showing = controls?.state?.mode as? RoutePlotMode.Showing ?: return null
    val route = showing.trip.route ?: return null
    if (cardHeightDp <= 0) return null
    return MapCameraRequest(
        id = "saved-route-${showing.trip.id}-${showing.serial}",
        frame = routeFrame(route, sideDp = ROUTE_FIT_MARGIN_DP, bottomDp = cardHeightDp + ROUTE_FIT_MARGIN_DP),
        stopsFollowing = true,
    )
}

/**
 * What the card shows for [controls]' state, `null` for no card; on a saved route, its selected stop, whether Navigate can go, and
 * whether a navigation is running ([navigating], the screen's one navigation predicate), which hides its buttons (Amendment 5).
 */
private fun routeCardContent(controls: RoutePlottingControls?, sources: MapRecordSources?, navigating: Boolean): RouteCardContent? = when (val mode = controls?.state?.mode) {
    // Amendment 8 (RECORD -1896): the elevation lines while plotting, from each leg's trail heights. A saved route has none
    // until step 3b stores them.
    is RoutePlotMode.Plotting -> RouteCardContent.Plotting(
        mode.draft.stops,
        mode.draft.legs,
        canSave = mode.draft.toRoute() != null,
        legElevations = mode.draft.legs.map(::legElevation),
    )
    is RoutePlotMode.Showing -> mode.trip.route?.let {
        RouteCardContent.Saved(mode.trip.name, it.stops, it.legs, selectedStop = controls.state.selectedStop, canNavigate = routeCardCanNavigate(controls, sources), stopLeadsNowhere = selectedStopTarget(controls, sources) == null, navigating = navigating)
    }
    RoutePlotMode.Off, null -> null
}

/**
 * The card, at the bottom of the map's Box, above the attribution as "Return to Route" is ([bottomInset] and its clearance), inside
 * [controlsPadding] so it keeps clear of the landscape rail; and the dialogs. [onCardHeightDp] is told the card's height whenever it
 * changes, 0 as soon as there is none, so what rises above the card comes back as it starts to leave.
 *
 * One width from the first tap, the reference card's, within the room beside the bar (C16 with C19, RECORD -1946, -1953). In portrait
 * it slides in from the edge away from the bar (Amendment 15, RECORD -1929) and sits flush against it, square on that side (C17). In
 * landscape it rises from the bottom edge in the corner away from the L (C18 with C20, RECORD -1947, -1953), or, while navigating,
 * from behind the navigation card to rest on it at its width, filling its rounded top corners (C21 with C23, -1955, -1956; Amendment 6,
 * A1, -2187). See `RouteCardPlacement.kt`. A card still leaving draws what it last showed, and takes no touch.
 */
@Composable
internal fun BoxScope.CompactMapRouteCard(
    controls: RoutePlottingControls?,
    cluster: MapIconClusterState,
    /** The landscape card's place, null in portrait ([routeCardLandscapeRoom]). */
    landscapeRoom: RouteCardLandscapeRoom?,
    distanceUnit: DistanceUnit,
    controlsPadding: PaddingValues,
    bottomInset: Dp,
    expandedMaxHeight: Dp,
    onCardHeightDp: (Int) -> Unit,
    /** Amendment 17, part 2 ruled: the card's left and right in root px while it shows, `null` as soon as it starts to leave. */
    onCardSpanPx: (ClosedFloatingPointRange<Float>?) -> Unit = {},
    /** D28 (RECORD -1979, -1980): the card's bounds in the root while it shows, `null` as soon as it starts to leave, for the "i". */
    onCardBoundsInRoot: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    /** Amendment 6, the credit (RECORD -2187): the part of the card drawn, its strip once tucked, for the "i" (RouteCardAttributionCover.kt). */
    onCardCoverInRoot: (androidx.compose.ui.geometry.Rect?) -> Unit = {},
    today: () -> LocalDate = LocalDate::now,
    /** Dispatch 2026-11-06-05, Amendment 2: the records and the navigation Navigate and the selection read; `null` offers no Navigate. */
    sources: MapRecordSources? = null,
    /** Amendment 3: whether the map is fullscreen when Navigate is tapped, for the Back order (`RouteCardBackOrder.kt`). */
    isFullscreen: Boolean = false,
    /**
     * Amendment 1, step 1: navigating, when Re-center can show, so the portrait card leaves its room (RouteCardReCenterRoom.kt); and
     * Amendment 5: while navigating, a saved route's card hides Navigate, Directions and the reason.
     */
    isNavigating: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val state = controls?.state
    val actions = controls?.actions
    val content = routeCardContent(controls, sources, isNavigating)
    val shownContent = rememberLastShown(content)
    val shownActions = rememberLastShown(actions)
    val leaving = content == null
    // Dispatch 2026-11-06-05, Amendment 1, step 2 (RECORD -2187): tucked, the card is slid to its edge with a strip of it left showing.
    // Since Amendment 6, A2, it keeps reporting its height, span and bounds as when it shows ("Stays aside", "Stays under"), so nothing
    // else moves; only a card leaving reports nothing (RouteCardTuck.kt).
    val tucked = !leaving && state?.tucked == true
    LaunchedEffect(leaving) {
        if (leaving) {
            onCardHeightDp(0)
            onCardSpanPx(null)
            onCardBoundsInRoot(null)
            onCardCoverInRoot(null)
        }
    }
    val showing = state?.mode as? RoutePlotMode.Showing
    val barOnLeft = cluster.isOnLeftSide
    val portrait = !cluster.landscape
    val cardOnLeft = routeCardOnLeft(barOnLeft, landscapeRoom)
    // Opening and closing keep their own motion; tucking moves the card inside it (A2, RouteCardTuck.kt).
    val (enter, exit) = if (portrait) routeCardPortraitTransitions(barOnLeft) else navigationChromeFromBottomTransitions()
    val barColumn = routeCardBarColumn(with(density) { cluster.clusterWidthPx.toDp() })
    val sides = routeCardSides(barOnLeft = barOnLeft, barColumn = barColumn)
    // C21 with C23 (RECORD -1955, -1956): on the navigation card, its width, edge to edge, whatever C19's reference is; lines wrap.
    // D32 (RECORD -1987): both narrower by the room Re-center's icon needs beside the L, where it would not fit.
    val onNavigationCard = landscapeRoom?.takeIf { it.onNavigationCard > 0.dp }?.onNavigationCardWidth
    val shape = if (portrait) routeCardPortraitShape(barOnLeft) else ROUTE_CARD_LANDSCAPE_SHAPE
    val referenceWidth = rememberRouteCardWidth(distanceUnit)
    // Dispatch 2026-11-06-05, Amendment 1, step 1 (RECORD -2187, the owner: "(b) Card leaves room"): in portrait while navigating, the
    // card sits higher by Re-center's height and a gap wherever Re-center shares its column, so Re-center is under it and the card never
    // moves as Re-center appears or goes (RouteCardReCenterRoom.kt). Tucked, the card's strip keeps the card's place, so it keeps the same.
    var reCenterWordSize by remember { mutableStateOf(IntSize.Zero) }
    if (portrait) ReturnToRouteWordProbe { reCenterWordSize = it }
    val reCenterRoom = routeCardReCenterRoomFor(cluster, controlsPadding, isNavigating, portrait, cardOnLeft, barColumn, referenceWidth, reCenterWordSize)
    // C21 (RECORD -1955): on the navigation card while navigating in landscape, its bottom on that card's top (A1, RECORD -2187).
    val bottomPadding = landscapeRoom?.bottom ?: (bottomInset + LEGEND_ATTRIBUTION_CLEARANCE + reCenterRoom)
    // D37 (RECORD -2006, the owner: "Cover the top bar (Recommended)"): where the peek lines fill the cap, the expanded card's cap rises
    // over the strip; collapsing brings the cap, and so the strip, back at once (the planner accepted that, -2008).
    var linesPx by remember { mutableIntStateOf(0) }
    val hasRows = shownContent?.let { it.legs.isNotEmpty() || it.legInFlight } == true
    val coversStrip = landscapeRoom != null && routeCardCoversStrip(state?.expanded == true, hasRows, linesPx, with(density) { landscapeRoom.maxHeight.toPx() })
    val cardMaxHeight = landscapeRoom?.let { if (coversStrip) it.overStripMaxHeight else it.maxHeight }
    // D40 (RECORD -2009, the owner: "Hide elevation while open"): where the lines fill even that raised cap, the total line's elevation
    // line is left out while expanded, and comes back on collapse.
    val hideElevationOpen = coversStrip && landscapeRoom != null && routeCardCoversStrip(true, hasRows, linesPx, with(density) { landscapeRoom.overStripMaxHeight.toPx() })
    // D42 (RECORD -2021): while the card rests on the navigation card, how low that card's top may sit for this one to fit, which keeps
    // the navigation card's sundown line out only where it would take the room. Its cap's top is fixed by the strip (or, raised, by the
    // controls), whatever the navigation card's height: the cap's bottom and height move together.
    var naturalHeights by remember { mutableStateOf<com.zynergylabs.forager.app.ui.route.RouteCardNaturalHeights?>(null) }
    val onNavigationCardNow = landscapeRoom != null && landscapeRoom.onNavigationCard > 0.dp && !leaving
    val navigationTopLimit = naturalHeights?.takeIf { onNavigationCardNow }?.let { heights ->
        with(density) {
            val room = landscapeRoom!!
            val boxBottom = cluster.mapContentBoxTopInRootPx + cluster.mapContentBoxHeightPx
            routeCardNavigationTopLimitPx(
                heights = heights,
                expanded = state?.expanded == true,
                hasRows = hasRows,
                capTopPx = boxBottom - (room.bottom + room.maxHeight).toPx(),
                raisedCapTopPx = boxBottom - (room.bottom + room.overStripMaxHeight).toPx(),
            )
        }
    }
    // The navigation card's top with its evening lines: where it is while they show, or where its height when they last showed puts it,
    // so stepping aside does not feed back into the decision. A gap once stepped aside, so a top a pixel either side cannot flip it.
    val navigationCard = cluster.navigationCardBoundsInRoot
    val boxBottomPx = cluster.mapContentBoxTopInRootPx + cluster.mapContentBoxHeightPx
    val tallestWithEvening = cluster.navigationCardHeightWithEveningPx
    val topWithEvening = when {
        navigationCard == null -> null
        // While they show, the higher of where it is and where its tallest with them puts it, so a frame of their grow is not read
        // as room the lines leave.
        cluster.navigationEveningShown -> minOf(navigationCard.top, tallestWithEvening?.let { boxBottomPx - it } ?: navigationCard.top)
        else -> tallestWithEvening?.let { boxBottomPx - it }
    }
    var steppedAside by remember { mutableStateOf(false) }
    val stepsAside = navigationTopLimit != null && topWithEvening != null &&
        routeCardSundownStepsAside(navigationTopLimit, topWithEvening, marginPx = if (steppedAside) with(density) { Spacing.xs.toPx() } else 0f)
    SideEffect {
        steppedAside = stepsAside
        cluster.navigationEveningStepsAside = stepsAside
    }
    DisposableEffect(Unit) { onDispose { cluster.navigationEveningStepsAside = false } }
    // D44 (RECORD -2027, the owner: "Elevation steps aside here too"): at peek, where the lines do not fit the cap even with D42
    // applied (the cap is read after the sundown line has stepped aside), the total line's elevation line is left out too. The lines
    // count it at its last height while it is out, so the answer does not flip back.
    val hideElevationPeek = landscapeRoom != null && state?.expanded != true && naturalHeights?.let { heights ->
        routeCardPeekLeavesElevationOut(heights, with(density) { landscapeRoom.maxHeight.toPx() })
    } == true
    val hideElevation = hideElevationOpen || hideElevationPeek
    NoTouchTargetExpansion(active = leaving) {
        AnimatedVisibility(
            visible = !leaving && (portrait || landscapeRoom != null),
            enter = enter,
            exit = exit,
            modifier = Modifier
                .align(if (cardOnLeft) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(controlsPadding)
                .padding(bottom = bottomPadding)
                .leavingTakesNoTouches(leaving = leaving),
        ) {
            if (shownContent != null && shownActions != null) {
                val card: @Composable (Dp) -> Unit = { width ->
                    // A2: a card leaving while tucked stays where it was in its tuck as it goes.
                    val tuck = rememberRouteCardTuckDraw(rememberLastShown(if (leaving) null else tucked) ?: false, cardOnLeft)
                    var cardInRoot by remember { mutableStateOf<Rect?>(null) }
                    ReportRouteCardAttributionCover(cardInRoot, tuck, leaving, onCardCoverInRoot)
                    Box {
                    RouteCard(
                        content = shownContent,
                        expanded = state?.expanded == true,
                        distanceUnit = distanceUnit,
                        callbacks = RouteCardCallbacks(
                            onToggleExpanded = shownActions::onSetExpanded,
                            onUndo = shownActions::onUndo,
                            onCancel = shownActions::onCancel,
                            onSave = shownActions::onOpenSaveDialog,
                            onDirections = { showing?.trip?.let { trip -> launchDirections(context, trip) } },
                            // Step 3: the ✕ is Back's close of a saved route, without Back's reopening of the Tools drawer (the plan).
                            onClose = shownActions::onCloseSavedRoute,
                            // Amendment 2, items 3 and 4: Navigate to the selected stop, and a stop's row selecting it.
                            onNavigate = { startRouteCardNavigation(controls, sources, isFullscreen) },
                            onSelectStop = shownActions::onSelectStop,
                        ),
                        expandedMaxHeight = expandedMaxHeight,
                        shape = shape,
                        cornerFill = landscapeRoom?.cornerFill ?: 0.dp,
                        onLinesHeightPx = { linesPx = it },
                        hideElevation = hideElevation,
                        onNaturalHeightsPx = { naturalHeights = it },
                        tuck = tuck,
                        // Sorrel's review of PR #507, item 1.1 (RECORD -2187, "(c) All three"; -967): no line of its own against the side bar.
                        bareSideOnLeft = routeCardBareSide(landscapeRoom, cardOnLeft, isFullscreen, controlsPadding, layoutDirection)?.let { it == ScreenEdge.Left },
                        // S22 defect (2) (RECORD -2187; E20's rule): the side bar's rounded bottom corner, below the card by the controls'
                        // bottom padding and the card's own lift; the card's edge carries the line where that corner curves away.
                        railCorner = com.zynergylabs.forager.app.ui.route.RouteCardRailCorner(below = controlsPadding.calculateBottomPadding() + bottomPadding, radius = MAP_CARD_CORNER_RADIUS),
                        modifier = Modifier
                            // Not there at all while tucked: a disabled draggable still counts as a touch target, so the card's
                            // whole bounds kept long-presses from the map (run 42493712752's grid; RouteCardTuckCloseTest).
                            .then(if (tucked) Modifier else Modifier.routeCardTuckSwipe { shownActions.onSetTucked(true) })
                            .width(width)
                            .then(if (cardMaxHeight != null) Modifier.heightIn(max = cardMaxHeight) else Modifier)
                            .onGloballyPositioned {
                                if (!leaving) {
                                    // What rises above the card, and the map's fit, clear it wherever it sits, so a card resting on the
                                    // navigation card counts from the map's bottom edge (C21, as C11 did).
                                    // Step 1: in portrait, Re-center's room under it too, so the legend chip and the route's fit clear it.
                                    onCardHeightDp(with(density) { it.size.height.toDp().value.toInt() + (landscapeRoom?.bottom?.value?.toInt() ?: reCenterRoom.value.toInt()) })
                                    val left = it.positionInRoot().x
                                    onCardSpanPx(left..left + it.size.width)
                                    onCardBoundsInRoot(it.boundsInRoot())
                                    cardInRoot = it.boundsInRoot()
                                }
                            },
                    )
                    if (tucked) RouteCardStripTouch(cardOnLeft = cardOnLeft, onBringBack = { shownActions.onSetTucked(false) })
                    }
                }
                if (onNavigationCard != null) card(onNavigationCard) else RouteCardWithin(referenceWidth, sides, card)
            }
        }
    }
    if (content == null || state == null || actions == null) return
    val plotting = state.mode as? RoutePlotMode.Plotting
    if (plotting != null && state.saveDialogOpen) {
        SaveRouteDialog(
            defaultName = defaultRouteName(plotting.draft.stops),
            today = today(),
            saving = state.saving,
            saveFailed = state.saveFailed,
            onSave = actions::onSave,
            onDismiss = actions::onDismissSaveDialog,
        )
    }
    if (state.pendingDiscard != null) {
        // Amendment 5, N-2: Keep plotting after a Trip Planner row's tap puts the user back where they were, the tab the
        // drawer was over and the drawer open, as Back does from a route opened there (RoutePlottingTrips.kt).
        val fromTripPlanner = state.pendingDiscardFromTripPlanner
        DiscardRouteDialog(
            onDiscard = actions::onConfirmDiscard,
            onKeepPlotting = {
                actions.onKeepPlotting()
                if (fromTripPlanner) controls.onReturnToTripPlanner()
            },
        )
    }
}

/**
 * The card's Back tier (the first stop, (a)): while plotting, Back cancels the route in one tap, expanded or not (RECORD -1817;
 * the planner's ruling on Q6); on a saved route, Back closes the card and the line, and reopens the Tools drawer when the route
 * was opened from its Trip Planner ([onReopenDrawer]; the planner's ruling on Q5). Composed before the bubble's, so a bubble,
 * a fan or the crowded list drawn over the card closes first; [enabled] leaves out the drawer, the menu and the pickers, as
 * their tiers are above it. It outranks fullscreen (Amendment 1, Q3: "cancel first").
 *
 * It stands aside while a navigation started from this card's Navigate runs ([navigatingToWaypoint]; dispatch 2026-11-06-05, Amendment
 * 1, step 5, the plan the owner approved: "One Back ends navigation, with the route card still open"), so that Back is the screen's
 * waypoint-navigation step, which ends it and reopens nothing; the next Back closes the card, as before. The way in was the card, then
 * Navigate, so Back undoes Navigate first.
 *
 * Amendment 3 (the owner: "Only leave full screen if they entered full screen after tapping navigate. If they entered navigation mode
 * after full screen mode, then leave navigate."): in fullscreen, Back undoes whichever came last. Fullscreen entered after Navigate is
 * the screen's fullscreen step, so this tier stands aside; Navigate tapped in fullscreen is ended here, and then fullscreen is left
 * before the card closes. With neither pending, the card's Back still goes before fullscreen (-1852, "Yes, cancel first").
 */
@Composable
internal fun RoutePlottingBackHandler(
    controls: RoutePlottingControls?,
    enabled: Boolean,
    onReopenDrawer: () -> Unit,
    navigatingToWaypoint: Boolean = false,
    isFullscreen: Boolean = false,
) {
    val mode = controls?.state?.mode
    val navigationFromCard = navigatingToWaypoint && controls?.state?.navigatedFromCard == true
    // Amendment 3: Back undoes whichever came last of Navigate and fullscreen (RouteCardBackOrder.kt). Leaving fullscreen, by any
    // route, clears the order; the screen's fullscreen step and its waypoint-navigation step take the presses this tier stands aside for.
    LaunchedEffect(isFullscreen) { if (!isFullscreen) controls?.actions?.onFullscreenLeft() }
    // Sorrel's review of PR #507, note 3.2: when the waypoint navigation is not running, none is the card's, so a later one started
    // elsewhere (a bubble, the Records list) is not taken for it. Keyed on the navigation only: Navigate tells the card before it starts.
    LaunchedEffect(navigatingToWaypoint) { if (!navigatingToWaypoint) controls?.actions?.onWaypointNavigationOver() }
    val step = controls?.state?.backOrder?.step(isFullscreen, navigationFromCard)
    val takes = when (step) {
        RouteCardBackStep.CLOSE_CARD -> true
        // Outside fullscreen the screen's waypoint-navigation step ends it; in fullscreen that step is off, so this tier does.
        RouteCardBackStep.END_NAVIGATION -> isFullscreen
        RouteCardBackStep.LEAVE_FULLSCREEN, null -> false
    }
    BackHandler(enabled = enabled && mode != null && mode != RoutePlotMode.Off && takes) {
        when {
            step == RouteCardBackStep.END_NAVIGATION -> {
                controls?.actions?.onCardNavigationEndedByBack(isFullscreen)
                controls?.onEndNavigation?.invoke()
            }
            mode is RoutePlotMode.Plotting -> controls?.actions?.onCancel()
            mode is RoutePlotMode.Showing -> {
                controls?.actions?.onCloseSavedRoute()
                if (mode.fromTripPlanner) onReopenDrawer()
            }
            else -> Unit
        }
    }
}

private const val ROUTE_PLOTTING_LOG_TAG = "RoutePlotting"
