package com.zynergylabs.forager.app.domain.wayto

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.LatLng

/** Stub, fail first (dispatch 2026-10-26-11 Amendment 1, item C): never tries again. */
class WayToRetry(
    private val waypoint: LatLng,
    private val note: WayToWaypointNote,
    private val searchedFrom: LatLng,
    regionsAtSearch: List<OfflineRegionSummary>,
) {
    fun judge(here: LatLng, regions: List<OfflineRegionSummary>): Boolean = false
}
