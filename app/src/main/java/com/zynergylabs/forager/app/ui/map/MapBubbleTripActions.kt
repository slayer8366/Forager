package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.route.RouteStop

/*
 * A planned trip's actions in its bubble and its card (dispatch 2026-11-05-05; RECORD -2140, -2148), built here for
 * MapBubbleLayer, beside the waypoint's own closures there. Each closes what it was tapped in first, as the waypoint's do.
 */

/**
 * The trip bubble's "Navigate" (A8): the bubble closes, and the trip is navigated to as itself, from where its bubble was, so
 * Back opens it there again ("Back returns to the trip"). `null`, none, where the host navigates to no trip.
 */
internal fun tripBubbleNavigate(sources: MapRecordSources, tapped: TappedMapThing, onDismiss: () -> Unit): ((String) -> Unit)? =
    sources.onNavigateToTrip?.let { navigate ->
        { id: String ->
            onDismiss()
            navigate(WaypointNavigationOrigin.MapBubble(id, tapped.anchorPx, tapped.bearingDeg, NavigationTargetKind.TRIP))
        }
    }

/**
 * The trip bubble's "Plot route" (A3): the bubble closes, and plotting starts from the trip's spot under its name, as a
 * waypoint's Plot route starts from the waypoint's. Nothing changes in the plotting or what it saves: a stop is a name and a
 * position. A trip gone from the lists is logged and plots nothing. `null`, none, where the host has no route plotting.
 */
internal fun tripBubblePlotRoute(sources: MapRecordSources, onDismiss: () -> Unit): ((String) -> Unit)? =
    sources.routePlotting?.let { plotting ->
        { id: String ->
            val trip = sources.plannedTrips.firstOrNull { it.id == id }
            onDismiss()
            if (trip != null) {
                plotting.actions.onStartPlotting(RouteStop(trip.name, trip.location))
            } else {
                Log.w(TRIP_ACTIONS_LOG_TAG, "Trip $id is gone; plotting did not start.")
            }
        }
    }

/** The trip card's "Navigate" (A8): [closeCard], then the trip is navigated to, from its card, so Back opens the card again. */
internal fun tripCardNavigate(sources: MapRecordSources, closeCard: () -> Unit): ((String) -> Unit)? =
    sources.onNavigateToTrip?.let { navigate ->
        { id: String ->
            closeCard()
            navigate(WaypointNavigationOrigin.MapDetails(id, NavigationTargetKind.TRIP))
        }
    }

private const val TRIP_ACTIONS_LOG_TAG = "MapBubble"
