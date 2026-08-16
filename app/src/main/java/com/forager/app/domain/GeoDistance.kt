package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle ("as the crow flies") distance between two geographic points.
 *
 * This exists because clustering and ordering both need a real-world distance, and the obvious
 * shortcut — Euclidean distance over raw lat/lng degrees — is wrong everywhere except the
 * equator. A degree of latitude is ~111 km anywhere, but a degree of longitude is ~111 km at
 * the equator, ~79 km at 45°N and ~56 km at 60°N. Treating degrees as a flat plane therefore
 * stretches east–west distances as latitude rises, which would silently merge separate spots
 * into one cluster (or split one spot in two) purely as a function of how far north the user is.
 *
 * Straight-line distance only. This says nothing about how far you would actually walk between
 * two points — there is no trail, terrain, or land-access data anywhere in this project, so a
 * walking distance is unsupported rather than approximated (CLAUDE.md: an unsupported capability
 * returns an explicit "unsupported", never a fabricated plausible value).
 */
object GeoDistance {

    /**
     * IUGG mean Earth radius. A sphere, not the WGS-84 ellipsoid: the error is under ~0.5%,
     * which is far below the precision anything in this app claims.
     */
    private const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

    /** Haversine great-circle distance between [a] and [b], in metres. */
    fun metersBetween(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val halfDLat = Math.toRadians(b.lat - a.lat) / 2
        val halfDLng = Math.toRadians(b.lng - a.lng) / 2

        val h = sin(halfDLat) * sin(halfDLat) +
            cos(lat1) * cos(lat2) * sin(halfDLng) * sin(halfDLng)
        // coerceAtMost(1.0) guards asin's domain against floating-point overshoot on antipodes.
        return 2 * EARTH_MEAN_RADIUS_METERS * asin(sqrt(h).coerceAtMost(1.0))
    }
}
