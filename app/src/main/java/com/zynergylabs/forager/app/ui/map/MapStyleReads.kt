package com.zynergylabs.forager.app.ui.map

import android.util.Log
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.RecordPolyline
import com.zynergylabs.forager.app.domain.model.RecordRegion
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import org.maplibre.android.maps.Style

/**
 * The map's style as its readers take it (dispatch 2026-10-25-02 Amendment 1; RECORD -1634, the owner: "A check at each
 * reader (Recommended)"): [loaded], `SightingsMapState.loadedStyle`, only while MapLibre still holds it fully loaded.
 *
 * `MapLibreMap.setStyle` clears the old style at once, and every getter on it then throws; `loadedStyle` keeps that
 * old style until the new style's callback (`MapStyleReload.kt`). A reader that ran in that gap crashed the app on the
 * S22 (F1; #397 fixed the forecast cells only). Each reader now takes the style through here: in the gap it gets null,
 * the skip is logged with [reader], and it catches up when the new style loads, since every such reader is an effect
 * keyed on `loadedStyle`. Nothing else changes: the keys, and so when each effect runs, are as before.
 */
internal fun currentStyle(loaded: Style?, reader: String): Style? {
    if (loaded == null) return null
    if (loaded.isFullyLoaded) return loaded
    Log.w(SIGHTINGS_MAP_TAG, "$reader skipped: a newer style is loading. It runs again when that style has loaded.")
    return null
}

/** The way home's layers on the current style, or nothing in a style change (logged); true when pushed. `MapDataRefresh`'s first effect. */
internal fun routeHomeOnCurrentStyle(loaded: Style?, route: RouteOnMap?): Boolean {
    val style = currentStyle(loaded, "The way-home route") ?: return false
    updateRouteHomeLayers(style, route)
    return true
}

/**
 * The overlay data ([refreshOverlayData]) on the current style; the style it was pushed to, or null in a style change
 * (logged), when `MapDataRefresh`'s second effect stops before its camera work and runs again on the new style.
 */
internal fun overlayDataOnCurrentStyle(
    loaded: Style?,
    region: Region,
    sightings: List<Sighting>,
    plannedTrips: List<PlannedTrip>,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    focusedObservationId: Long?,
    keptTrackPolylines: List<RecordPolyline>,
    findMarkers: List<RecordPoint>,
    photoMarkers: List<RecordPoint>,
    offlineRegionCircles: List<RecordRegion>,
    showSearchCentre: Boolean,
    journalHighlights: JournalEntryHighlights,
): Style? {
    val style = currentStyle(loaded, "The map's overlay data") ?: return null
    refreshOverlayData(
        style, region, sightings, plannedTrips, breadcrumbPoints, waypoints, focusedObservationId,
        keptTrackPolylines, findMarkers, photoMarkers, offlineRegionCircles, showSearchCentre, journalHighlights,
    )
    return style
}

/**
 * A locate tap during a style change (dispatch 2026-10-25-02 Amendment 1; RECORD -1634, the owner: "Do it when the map
 * is ready (Recommended)"): remembered, and carried out once the new style has loaded, so the walker does not tap again.
 * Plain state for `MapLocationDot`: [onTap] says whether to locate now; [onStyle] whether a remembered tap is due.
 */
internal class PendingLocate {
    private var pending = false

    /** A locate tap; [styleCurrent] is whether the style can be read now. True: locate now. False: remembered. */
    fun onTap(styleCurrent: Boolean): Boolean {
        if (styleCurrent) return true
        pending = true
        Log.i(POSITION_DOT_LOG_TAG, "locate tapped during a style change: remembered until the new style loads")
        return false
    }

    /** The style changed; [styleCurrent] as for [onTap]. True: carry out the remembered tap now (and forget it). */
    fun onStyle(styleCurrent: Boolean): Boolean {
        if (!pending || !styleCurrent) return false
        pending = false
        Log.i(POSITION_DOT_LOG_TAG, "the remembered locate is carried out on the new style")
        return true
    }
}
