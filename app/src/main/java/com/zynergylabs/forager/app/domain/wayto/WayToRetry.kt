package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routedata.RouteDataForPoints
import com.zynergylabs.forager.app.domain.routedata.contains
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
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
 * - **Walking on inside an area,** for a note walking can change ([WALKING_CAN_CHANGE]): while the walker's fix is
 *   inside a downloaded area ([insideAnArea]), more than [WAY_HOME_STRAY_METERS] (50 m, the stray figure) from where the
 *   search was asked, so once per 50 m walked from the last ask. Until Amendment 2 this was
 *   [WayToWaypointNote.WALKER_FAR_FROM_TRAIL] alone, where the trails are being the engine's to know; Amendment 2, item 3
 *   (RECORD -1670, the owner: "Every 50 m walked (Recommended)") adds the others a walk can change. Amendment 3, item 3
 *   (RECORD -1671) makes "inside" exact, as the owner's question put it ("Inside an area, after a straight result"):
 *   outside every downloaded area there is no 50 m retry, so no search on a fix outside one (Amendment 1); walking into
 *   an area holding both ends still tries at once, by the trigger above.
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

    /**
     * Where the last search was asked from: [searchedFrom], then each [here] this says to search from, so the 50 m is
     * counted from the last ask point (Amendment 2, item 3). The ViewModel's collaborator builds a new instance from each
     * search's own point, so there this only matters to a caller that keeps asking the same instance.
     */
    private var lastAsk = searchedFrom

    /** Whether to search again from [here], given the region list [regions] as it is now. */
    fun judge(here: LatLng, regions: List<OfflineRegionSummary>): Boolean {
        val holdsNow = holdsBoth(here, regions)
        val walkedIn = holdsNow && !heldBoth
        heldBoth = holdsNow
        val again = walkedIn ||
            (note in WALKING_CAN_CHANGE && insideAnArea(here, regions) && GeoDistance.metersBetween(lastAsk, here) > WAY_HOME_STRAY_METERS)
        if (again) lastAsk = here
        return again
    }

    private fun holdsBoth(walker: LatLng, regions: List<OfflineRegionSummary>) =
        routeDataForPoints(walker, waypoint, regions) is RouteDataForPoints.Found
}

/**
 * Whether [walker] is inside a downloaded area (Amendment 3, item 3): a region downloaded on this phone whose route area
 * holds the fix, as [routeDataForPoints] judges a point in a downloaded region. The waypoint need not be in it.
 */
private fun insideAnArea(walker: LatLng, regions: List<OfflineRegionSummary>): Boolean =
    regions.any { it.isDownloaded && routeAreaFor(it.region).contains(walker) }

/** The notes a walk can change, tried again every 50 m walked (Amendment 2, item 3; RECORD -1670), as the dispatch lists them. */
private val WALKING_CAN_CHANGE = setOf(
    WayToWaypointNote.LEAVES_AREA,
    WayToWaypointNote.NO_ROUTE_FOUND,
    WayToWaypointNote.NO_TRAIL_ROUTES,
    WayToWaypointNote.WALKER_FAR_FROM_TRAIL,
    // Way-to dispatch 2026-10-28: a lead-in blocked from here may be clear from a step further on.
    WayToWaypointNote.LEAD_IN_BLOCKED,
    WayToWaypointNote.WAYPOINT_IN_ANOTHER_AREA,
)
