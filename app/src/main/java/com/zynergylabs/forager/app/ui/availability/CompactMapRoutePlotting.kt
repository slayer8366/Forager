package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.domain.route.defaultRouteName
import com.zynergylabs.forager.app.domain.route.routeFrame
import com.zynergylabs.forager.app.ui.map.LEGEND_ATTRIBUTION_CLEARANCE
import com.zynergylabs.forager.app.ui.map.MapCameraRequest
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapLayerIds
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.PlottedRouteOnMap
import com.zynergylabs.forager.app.ui.route.DiscardRouteDialog
import com.zynergylabs.forager.app.ui.route.RouteCard
import com.zynergylabs.forager.app.ui.route.RouteCardCallbacks
import com.zynergylabs.forager.app.ui.route.RouteCardContent
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
 * - a planned trip with a route opens the route at once, with no bubble (Amendment 1, Q7: "Opens the route straight away").
 * Every other tap is the bubble's, as before.
 */
internal fun routePlottingTakesTap(tap: MapFeatureTap, sources: MapRecordSources): Boolean {
    val plotting = sources.routePlotting ?: return false
    return when (tap.layerId) {
        MapLayerIds.WAYPOINTS -> {
            if (plotting.state.mode !is RoutePlotMode.Plotting) return false
            val waypoint = sources.waypoints.firstOrNull { it.id == tap.featureId }
            if (waypoint == null) {
                Log.w(ROUTE_PLOTTING_LOG_TAG, "Waypoint ${tap.featureId} is not in Records; it was not added to the route.")
            } else {
                plotting.actions.onWaypointTapped(RouteStop(waypoint.name, LatLng(waypoint.lat, waypoint.lng)))
            }
            true
        }
        MapLayerIds.PLANNED_TRIPS -> {
            val trip = sources.plannedTrips.firstOrNull { it.id == tap.featureId && it.route != null } ?: return false
            plotting.actions.onOpenSavedRoute(trip, fromTripPlanner = false)
            true
        }
        else -> false
    }
}

/** The line the map draws: the route being plotted, or a saved one with a dot at each stop (Amendment 1, Q8); `null` for none. */
internal fun plottedRouteOnMap(controls: RoutePlottingControls?): PlottedRouteOnMap? = when (val mode = controls?.state?.mode) {
    is RoutePlotMode.Plotting -> PlottedRouteOnMap(mode.draft.legs)
    is RoutePlotMode.Showing -> mode.trip.route?.let { route -> PlottedRouteOnMap(route.legs, route.stops.map { it.position }) }
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
 * The card, at the bottom of the map's Box, above the attribution as "Return to Route" is ([bottomInset] and its clearance),
 * inside [controlsPadding] so it keeps clear of the landscape rail; and the dialogs. [onCardHeightDp] is told the card's
 * height whenever it changes, 0 when there is none.
 */
@Composable
internal fun BoxScope.CompactMapRouteCard(
    controls: RoutePlottingControls?,
    distanceUnit: DistanceUnit,
    controlsPadding: PaddingValues,
    bottomInset: Dp,
    expandedMaxHeight: Dp,
    onCardHeightDp: (Int) -> Unit,
    today: () -> LocalDate = LocalDate::now,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val state = controls?.state
    val actions = controls?.actions
    val content = when (val mode = state?.mode) {
        is RoutePlotMode.Plotting -> RouteCardContent.Plotting(mode.draft.stops, mode.draft.legs, canSave = mode.draft.toRoute() != null)
        is RoutePlotMode.Showing -> mode.trip.route?.let { RouteCardContent.Saved(mode.trip.name, it.stops, it.legs) }
        RoutePlotMode.Off, null -> null
    }
    if (content == null || state == null || actions == null) {
        LaunchedEffect(Unit) { onCardHeightDp(0) }
        return
    }
    val showing = state.mode as? RoutePlotMode.Showing
    RouteCard(
        content = content,
        expanded = state.expanded,
        distanceUnit = distanceUnit,
        callbacks = RouteCardCallbacks(
            onToggleExpanded = actions::onSetExpanded,
            onUndo = actions::onUndo,
            onCancel = actions::onCancel,
            onSave = actions::onOpenSaveDialog,
            onDirections = { showing?.trip?.let { trip -> launchDirections(context, trip) } },
        ),
        expandedMaxHeight = expandedMaxHeight,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(controlsPadding)
            .padding(bottom = bottomInset + LEGEND_ATTRIBUTION_CLEARANCE)
            .onGloballyPositioned { onCardHeightDp(with(density) { it.size.height.toDp().value.toInt() }) },
    )
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
        DiscardRouteDialog(onDiscard = actions::onConfirmDiscard, onKeepPlotting = actions::onKeepPlotting)
    }
}

/**
 * The card's Back tier (the first stop, (a)): while plotting, Back cancels the route in one tap, expanded or not (RECORD -1817;
 * the planner's ruling on Q6); on a saved route, Back closes the card and the line, and reopens the Tools drawer when the route
 * was opened from its Trip Planner ([onReopenDrawer]; the planner's ruling on Q5). Composed before the bubble's, so a bubble,
 * a fan or the crowded list drawn over the card closes first; [enabled] leaves out the drawer, the menu and the pickers, as
 * their tiers are above it. It outranks fullscreen (Amendment 1, Q3: "cancel first").
 */
@Composable
internal fun RoutePlottingBackHandler(controls: RoutePlottingControls?, enabled: Boolean, onReopenDrawer: () -> Unit) {
    val mode = controls?.state?.mode
    BackHandler(enabled = enabled && mode != null && mode != RoutePlotMode.Off) {
        when (mode) {
            is RoutePlotMode.Plotting -> controls.actions.onCancel()
            is RoutePlotMode.Showing -> {
                controls.actions.onCloseSavedRoute()
                if (mode.fromTripPlanner) onReopenDrawer()
            }
            else -> Unit
        }
    }
}

private const val ROUTE_PLOTTING_LOG_TAG = "RoutePlotting"
