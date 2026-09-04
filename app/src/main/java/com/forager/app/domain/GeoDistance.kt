package com.forager.app.domain

import com.forager.app.domain.model.GeoBoundingBox
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

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
     * A closed ring of [pointCount] points approximating the true circle of [radiusKm] around
     * [center] — Journal Stage 2d, for drawing an offline region's coverage as a map polygon rather
     * than [boundingBox]'s own rectangle (built for a tile-download API, not a drawn shape). The
     * standard spherical "destination point given start, bearing, distance" formula, one call per
     * point evenly spaced around the bearing circle — more accurate at high latitude than
     * [boundingBox]'s simpler equirectangular approximation, since a drawn circle's own distortion
     * is directly visible in a way a download rectangle's slack tile margin never is. The first and
     * last points are identical, closing the ring, since [org.maplibre.geojson.Polygon] (like GeoJSON
     * generally) requires a closed linear ring.
     */
    fun circlePolygonPoints(center: LatLng, radiusKm: Int, pointCount: Int = 32): List<LatLng> {
        require(radiusKm >= 0) { "radiusKm must not be negative, was $radiusKm" }
        require(pointCount >= 3) { "pointCount must be at least 3 to form a polygon, was $pointCount" }
        val radiusMeters = radiusKm * 1_000.0
        val angularDistance = radiusMeters / EARTH_MEAN_RADIUS_METERS
        val lat1 = Math.toRadians(center.lat)
        val lng1 = Math.toRadians(center.lng)

        val ring = (0 until pointCount).map { index ->
            val bearing = Math.toRadians(index * 360.0 / pointCount)
            val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing))
            val lng2 = lng1 + atan2(
                sin(bearing) * sin(angularDistance) * cos(lat1),
                cos(angularDistance) - sin(lat1) * sin(lat2),
            )
            LatLng(lat = Math.toDegrees(lat2), lng = Math.toDegrees(lng2))
        }
        return ring + ring.first()
    }

    /**
     * A [Region] (centre + radius) that encloses every one of [points] — Journal Stage 2d's camera
     * framing for the Cartography entry map, reusing the same `region`-driven camera control
     * [com.forager.app.ui.map.SightingsMap] already has (see that composable's own doc comment on
     * why this app has no true bounds-fit camera API) rather than adding one. `null` for an empty
     * [points] — a real, reachable state (an entry made entirely of photos with no coordinates),
     * reported as an explicit "nothing to frame" rather than a fabricated default location, per
     * CLAUDE.md's rule against inventing a plausible-looking value for an unsupported case.
     *
     * The centre is the bounding box's own midpoint (not every point's average, which would skew
     * toward a cluster of nearby points rather than covering the true extent); the radius is the
     * farthest single point's distance from that centre, rounded up to the nearest whole kilometre
     * and clamped into [Region]'s own [Region.MIN_RADIUS_KM]/[Region.MAX_RADIUS_KM] range — the same
     * clamp the search-radius UI already applies, reused here rather than a second one for a
     * different reason to bound the same field.
     */
    fun boundingRegion(points: List<LatLng>): Region? {
        if (points.isEmpty()) return null
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLng = points.minOf { it.lng }
        val maxLng = points.maxOf { it.lng }
        val center = LatLng(lat = (minLat + maxLat) / 2, lng = (minLng + maxLng) / 2)
        val maxDistanceMeters = points.maxOf { metersBetween(center, it) }
        val radiusKm = Region.clampRadiusKm((maxDistanceMeters / 1_000.0).roundToInt().coerceAtLeast(Region.MIN_RADIUS_KM))
        return Region(lat = center.lat, lng = center.lng, radiusKm = radiusKm)
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
