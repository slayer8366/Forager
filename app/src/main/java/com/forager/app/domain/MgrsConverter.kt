package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MgrsCoordinate
import mil.nga.grid.features.Point
import mil.nga.mgrs.MGRS

/**
 * Converts a WGS84 [LatLng] to its Military Grid Reference System string, for display on a
 * planned trip — see [PlannedTripRow][com.forager.app.ui.availability.PlannedTripRow].
 *
 * ## Why a library instead of hand-rolled UTM/grid-zone math
 *
 * MGRS asked for real correctness ("a wrong MGRS string handed to someone planning a real trip is
 * a fabricated plausible value" — CLAUDE.md), and the math it sits on is not simple: UTM zone
 * exceptions around Norway and Svalbard, 100km grid-square lettering that repeats on different
 * cycles for columns vs. rows, and the northern/southern row-lettering offset are all easy to get
 * subtly wrong by hand. `mil.nga:mgrs` (pinned in `gradle/libs.versions.toml`, like every other
 * dependency here) is published by the U.S. National Geospatial-Intelligence Agency itself — the
 * standards body that defines MGRS — under the MIT license, and its only runtime dependency is
 * NGA's own `mil.nga:grid`, both plain Java with no Android dependency, so calling it from here
 * doesn't violate CLAUDE.md's "domain stays free of Android imports" rule.
 *
 * Its correctness was cross-checked here (not just trusted) against `mgrs` (the Python binding
 * over NGA's original GeoTrans C implementation — a second, independently-written codebase, not
 * a second copy of this one) across 8 points spanning 7 different UTM zones and both hemispheres:
 * every one matched exactly. See `MgrsConverterTest` for those same points as pinned assertions.
 *
 * ## The UPS edge case
 *
 * That same cross-check also caught a real defect: `MGRS.from()` does *not* reject a latitude
 * outside its supported range (north of 84°N, south of 80°S, where the Universal Polar
 * Stereographic system applies instead of UTM/MGRS) — it silently clamps the latitude to
 * whichever bound was crossed and returns a grid reference for that clamped point, wrong by
 * construction. Both bounds are checked *before* the library is called, so this converter never
 * hands back that clamped value; a polar coordinate gets an explicit
 * [MgrsCoordinate.Unsupported] instead.
 */
object MgrsConverter {

    /** MGRS's UTM-based coverage starts here; below this, UPS applies instead. See [convert]. */
    const val MIN_SUPPORTED_LATITUDE = -80.0

    /** MGRS's UTM-based coverage ends here; above this, UPS applies instead. See [convert]. */
    const val MAX_SUPPORTED_LATITUDE = 84.0

    fun convert(location: LatLng): MgrsCoordinate {
        if (location.lat < MIN_SUPPORTED_LATITUDE || location.lat > MAX_SUPPORTED_LATITUDE) {
            return MgrsCoordinate.Unsupported(
                "MGRS only covers latitudes from $MIN_SUPPORTED_LATITUDE° to " +
                    "$MAX_SUPPORTED_LATITUDE°; ${location.lat}° is in the polar region " +
                    "covered by the Universal Polar Stereographic system instead, which this app " +
                    "does not support.",
            )
        }
        val mgrs = MGRS.from(Point.degrees(location.lng, location.lat))
        val zoneAndBand = "${mgrs.zone}${mgrs.band}"
        val easting = "%05d".format(mgrs.easting)
        val northing = "%05d".format(mgrs.northing)
        return MgrsCoordinate.Grid("$zoneAndBand ${mgrs.columnRowId} $easting $northing")
    }
}
