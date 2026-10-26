package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RouteDataForPoints
import com.zynergylabs.forager.app.domain.routedata.routeDataForPoints
import com.zynergylabs.forager.app.domain.wayhome.WAY_HOME_STRAY_METERS

/**
 * When a straight-line result tries the trail route again by itself, without restarting Navigate (dispatch 2026-10-26-11
 * Amendment 1, item C; RECORD -1669, the owner: "Try again on entering an area (Recommended)"). One instance per
 * straight-line result: [judge] is asked at each later fix, while no search is running, and says whether to search again.
 * Two triggers, both from figures the code already has, and neither a search per fix:
 *
 * - **Walking into an area:** the first fix at which a downloaded region with usable routes holds both the walker and
 *   [waypoint] ([routeDataForPoints] says [RouteDataForPoints.Found]), when the fix before did not, or, for the first
 *   fix, when the place the search was asked from did not. A geometry check on the region list already read: no engine,
 *   no storage. Walking out and back in tries again; walking on inside does not. The same rising edge takes routes
 *   arriving for an area already holding both ends (a download, or its routes, finishing while navigating), since the
 *   list it is handed is read afresh at each fix.
 * - **Walking on,** for a note walking can change ([WALKING_CAN_CHANGE]): more than [WAY_HOME_STRAY_METERS] (50 m, the
 *   stray figure) from where the search was asked, so once per 50 m walked from the last ask. Until Amendment 2 this was
 *   [WayToWaypointNote.WALKER_FAR_FROM_TRAIL] alone, where the trails are being the engine's to know; Amendment 2, item 3
 *   (RECORD -1670, the owner: "Every 50 m walked (Recommended)") adds the others a walk can change, inside an area too.
 *
 * The rest wait for the area trigger: [WayToWaypointNote.WAYPOINT_FAR_FROM_TRAIL] (walking does not move the waypoint),
 * [WayToWaypointNote.ROUTES_UNREADABLE] and [WayToWaypointNote.ROUTES_NEED_APP_UPDATE] (the area's data, which walking
 * inside it does not change), as the owner's ruling lists them, and the two it does not list,
 * [WayToWaypointNote.ROUTES_NOT_DOWNLOADED] and [WayToWaypointNote.ROUTES_FETCHING], which the region list's own change
 * reaches through the area trigger. Pure: no clock, no Android.
 */
class WayToRetry(
    private val waypoint: LatLng,
    private val note: WayToWaypointNote,
    private val searchedFrom: LatLng,
    regionsAtSearch: List<OfflineRegionSummary>,
) {
    private var heldBoth = holdsBoth(searchedFrom, regionsAtSearch)

    /** Whether to search again from [here], given the region list [regions] as it is now. */
    fun judge(here: LatLng, regions: List<OfflineRegionSummary>): Boolean {
        val holdsNow = holdsBoth(here, regions)
        val walkedIn = holdsNow && !heldBoth
        heldBoth = holdsNow
        if (walkedIn) return true
        return note in WALKING_CAN_CHANGE && GeoDistance.metersBetween(searchedFrom, here) > WAY_HOME_STRAY_METERS
    }

    private fun holdsBoth(walker: LatLng, regions: List<OfflineRegionSummary>) =
        routeDataForPoints(walker, waypoint, regions) is RouteDataForPoints.Found
}

/** The notes a walk can change, tried again every 50 m walked (Amendment 2, item 3; RECORD -1670), as the dispatch lists them. */
private val WALKING_CAN_CHANGE = setOf(
    WayToWaypointNote.LEAVES_AREA,
    WayToWaypointNote.NO_ROUTE_FOUND,
    WayToWaypointNote.NO_TRAIL_ROUTES,
    WayToWaypointNote.WALKER_FAR_FROM_TRAIL,
    WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA,
)
