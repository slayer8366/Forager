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
 * - **Far from any trail** ([WayToWaypointNote.WALKER_FAR_FROM_TRAIL]): more than [WAY_HOME_STRAY_METERS] (50 m, the
 *   stray figure) from where the search was asked. Where the trails are is the engine's to know, so the walker's coming
 *   within its reach of one is found by asking again, once per 50 m walked from the last ask.
 *
 * Every other note waits for the area trigger: walking does not move the waypoint, and the rest name the area's data
 * or a route the engine did not find, which the owner's ruling does not ask to retry on walking. Pure: no clock, no
 * Android.
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
        return note == WayToWaypointNote.WALKER_FAR_FROM_TRAIL && GeoDistance.metersBetween(searchedFrom, here) > WAY_HOME_STRAY_METERS
    }

    private fun holdsBoth(walker: LatLng, regions: List<OfflineRegionSummary>) =
        routeDataForPoints(walker, waypoint, regions) is RouteDataForPoints.Found
}
