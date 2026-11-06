package com.zynergylabs.forager.app.data.backup

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng

/** How close two centres must be to be the same region (owner 3.1: "the same centre within 1 m"). */
internal const val SAME_REGION_CENTRE_METRES = 1.0

/**
 * Whether two offline regions are the same one (owner 3.1): the same name, the same radius, and centres within
 * [SAME_REGION_CENTRE_METRES] on the ground (haversine, so a degree of longitude is not taken as a fixed length and
 * the antimeridian is not a special case). Pure arithmetic, so a restore's rule is testable without a database.
 *
 * The haversine is [GeoDistance.metersBetween] (dispatch 2026-09-28-658, D8). This file had its own copy, on a
 * 6,371,000 m Earth where `GeoDistance` uses the mean radius, 6,371,008.8 m. Distances scale with the radius, so the
 * shared one reads every distance longer by a factor of 1.0000014: at the 1 m line that is 1.4 micrometres. Only a
 * pair of centres between 0.9999986 m and 1 m apart could change from "same" to "not same", and a restore compares a
 * region with the copy of itself in a backup, whose centre is the same stored number.
 */
internal fun sameOfflineRegion(
    nameA: String, latA: Double, lngA: Double, radiusKmA: Int,
    nameB: String, latB: Double, lngB: Double, radiusKmB: Int,
): Boolean {
    if (nameA != nameB || radiusKmA != radiusKmB) return false
    return GeoDistance.metersBetween(LatLng(latA, lngA), LatLng(latB, lngB)) <= SAME_REGION_CENTRE_METRES
}
