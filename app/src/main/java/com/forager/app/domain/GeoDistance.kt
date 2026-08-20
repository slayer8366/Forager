package com.forager.app.domain

import com.forager.app.domain.model.GeoBoundingBox
import com.forager.app.domain.model.LatLng
import kotlin.math.asin
import kotlin.math.atan2
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

    /**
     * The lat/lng rectangle that circumscribes a circle of [radiusKm] centred on [center] — an
     * equirectangular approximation, not the true circumscribing box of a great-circle radius.
     * Built for the "Download Maps" region picker's offline downloader — osmdroid's
     * `OsmdroidOfflineMapRepository` originally, now [com.forager.app.map.MapLibreOfflineMapRepository]
     * — which needs a rectangle of tiles to hand its download API, not a precise circle (there is no
     * such thing as a circular tile download).
     *
     * A degree of longitude shrinks toward the poles — the same fact [metersBetween] exists to get
     * right for distance — so the north/south span in degrees and the east/west span in degrees are
     * computed separately: latitude uses the constant ~111km/degree, longitude divides that by
     * `cos(latitude)` so the box widens in degrees as the search moves toward a pole, staying
     * (approximately) [radiusKm] wide in real metres at every latitude it's drawn at.
     *
     * [GeoBoundingBox.east] and [.west][GeoBoundingBox.west] are normalized into `[-180, 180]` and
     * can cross the antimeridian (`east < west`) for a centre near longitude ±180°; north/south are
     * clamped to `[-90, 90]`. The `cos(latitude)` denominator is floored rather than left to approach
     * zero near a pole, which would otherwise blow the east/west span out to (or past) the whole
     * globe for a real, if unlikely, foraging search near 90°N.
     */
    fun boundingBox(center: LatLng, radiusKm: Int): GeoBoundingBox {
        require(radiusKm >= 0) { "radiusKm must not be negative, was $radiusKm" }
        val radiusMeters = radiusKm * 1_000.0

        val latSpanDegrees = Math.toDegrees(radiusMeters / EARTH_MEAN_RADIUS_METERS)
        val north = (center.lat + latSpanDegrees).coerceAtMost(90.0)
        val south = (center.lat - latSpanDegrees).coerceAtLeast(-90.0)

        val cosLat = cos(Math.toRadians(center.lat.coerceIn(-90.0, 90.0))).coerceAtLeast(MIN_COS_LAT)
        val lngSpanDegrees = Math.toDegrees(radiusMeters / (EARTH_MEAN_RADIUS_METERS * cosLat))
        val east = normalizeLongitudeDegrees(center.lng + lngSpanDegrees)
        val west = normalizeLongitudeDegrees(center.lng - lngSpanDegrees)

        return GeoBoundingBox(north = north, south = south, east = east, west = west)
    }

    /**
     * Initial great-circle bearing from [from] to [to], in degrees clockwise from true north, in
     * `[0, 360)`. This is the bearing to *start* walking on, not a bearing that stays constant
     * along a great-circle route of any real length — over the short distances a return-to-start
     * prompt is ever computed at, that distinction is immaterial. True north, not magnetic:
     * [com.forager.app.domain.CompassProvider.heading] is already magnetic-north-relative, so
     * combining the two is a UI-layer concern, not this function's.
     */
    fun initialBearingDegrees(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * The floor on `cos(latitude)` in [boundingBox]'s longitude-span denominator, equivalent to
     * stopping 0.1° short of an exact pole. Below this the true east/west span in degrees is not
     * meaningful anyway (a small circle around 90°N touches every longitude), so this is an
     * explicit operating limit rather than the unbounded value the raw formula would compute.
     */
    private val MIN_COS_LAT = cos(Math.toRadians(89.9))

    private fun normalizeLongitudeDegrees(lngDegrees: Double): Double {
        var normalized = lngDegrees % 360.0
        if (normalized > 180.0) normalized -= 360.0
        if (normalized < -180.0) normalized += 360.0
        return normalized
    }
}
