package com.zynergylabs.forager.app.ui.availability

import android.util.Log
import com.zynergylabs.forager.app.domain.NavigationTargetKind
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.RecordDetailsTarget
import com.zynergylabs.forager.app.ui.map.MapBubbleKind
import com.zynergylabs.forager.app.ui.map.MapBubbleTarget
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.TappedMapThing
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds

/*
 * What Back opens again on the Maps tab after it ended a navigation started there (dispatch 2026-09-28-502, Amendment 1, the
 * owner's "Back to where you tapped Navigate"), for a waypoint as before and, since dispatch 2026-11-05-05 (A8, "Back returns
 * to the trip"), for a trip. The waypoint's bubble case moved here from AvailabilityCompactMapUi.kt's LaunchedEffect, which
 * still runs it at the same place, with its log line unchanged.
 */

/**
 * The bubble [origin] names, where it was when Navigate was tapped: a waypoint's or a trip's glyph, looked up in [sources].
 * The map re-anchors it on its glyph at the next camera idle (both are point kinds), as after a pan. `null`, with a logged
 * line, when the record has gone meanwhile: no bubble is opened for it.
 */
internal fun reopenedBubble(origin: WaypointNavigationOrigin.MapBubble, sources: MapRecordSources): TappedMapThing? {
    val target = when (origin.kind) {
        NavigationTargetKind.WAYPOINT -> sources.waypoints.firstOrNull { it.id == origin.waypointId }?.let { waypoint ->
            MapBubbleTarget.FeatureTarget(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, waypoint.id, LatLng(waypoint.lat, waypoint.lng))
        }
        NavigationTargetKind.TRIP -> sources.plannedTrips.firstOrNull { it.id == origin.waypointId }?.let { trip ->
            MapBubbleTarget.FeatureTarget(MapBubbleKind.PLANNED_TRIP, MapLayerIds.PLANNED_TRIPS, trip.id, trip.location)
        }
    }
    if (target == null) {
        val what = if (origin.kind == NavigationTargetKind.TRIP) "Trip" else "Waypoint"
        Log.w(COMPACT_MAP_LOG_TAG, "$what ${origin.waypointId} is gone; its bubble was not opened again after Back ended its navigation.")
        return null
    }
    return TappedMapThing(target, origin.anchorPx, origin.bearingDeg)
}

/** The card [origin] names, opened again in the bubble layer: a waypoint's, or a trip's; `null` for any other origin. */
internal fun reopenedDetails(origin: WaypointNavigationOrigin?): RecordDetailsTarget? = (origin as? WaypointNavigationOrigin.MapDetails)?.let {
    when (it.kind) {
        NavigationTargetKind.WAYPOINT -> RecordDetailsTarget.WaypointDetails(it.waypointId)
        NavigationTargetKind.TRIP -> RecordDetailsTarget.TripDetails(it.waypointId)
    }
}

/** Dispatch 2026-09-28-502: the tag the reopen's log line goes under, the Maps tab's (moved here with it from AvailabilityCompactMapUi.kt). */
private const val COMPACT_MAP_LOG_TAG = "CompactMapTab"
