package com.zynergylabs.forager.app.data.backup

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** How close two centres must be to be the same region (owner 3.1: "the same centre within 1 m"). */
internal const val SAME_REGION_CENTRE_METRES = 1.0

private const val EARTH_RADIUS_METRES = 6_371_000.0

/**
 * Whether two offline regions are the same one (owner 3.1): the same name, the same radius, and centres within
 * [SAME_REGION_CENTRE_METRES] on the ground (haversine, so a degree of longitude is not taken as a fixed length and
 * the antimeridian is not a special case). Pure arithmetic, so a restore's rule is testable without a database.
 */
internal fun sameOfflineRegion(
    nameA: String, latA: Double, lngA: Double, radiusKmA: Int,
    nameB: String, latB: Double, lngB: Double, radiusKmB: Int,
): Boolean {
    if (nameA != nameB || radiusKmA != radiusKmB) return false
    val phiA = Math.toRadians(latA)
    val phiB = Math.toRadians(latB)
    val dPhi = phiB - phiA
    val dLambda = Math.toRadians(lngB - lngA)
    val h = sin(dPhi / 2).pow(2) + cos(phiA) * cos(phiB) * sin(dLambda / 2).pow(2)
    val metres = 2 * EARTH_RADIUS_METRES * asin(sqrt(h.coerceIn(0.0, 1.0)))
    return metres <= SAME_REGION_CENTRE_METRES
}
