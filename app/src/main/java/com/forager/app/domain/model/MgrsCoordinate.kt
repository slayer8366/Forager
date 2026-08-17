package com.forager.app.domain.model

/**
 * The result of converting a WGS84 [LatLng] to its Military Grid Reference System string, via
 * [com.forager.app.domain.MgrsConverter].
 *
 * A sealed type rather than a plain nullable `String?`, the same "no result is unlabeled" pattern
 * as [ForagingAreas]: a caller has to look at which branch it got, so a polar coordinate this
 * app cannot grid-reference can never be silently rendered as an empty or wrong string.
 */
sealed interface MgrsCoordinate {

    /** [value] is a normal MGRS grid reference, e.g. `"10T ER 25118 40235"`. */
    data class Grid(val value: String) : MgrsCoordinate

    /**
     * [LatLng.lat] fell outside MGRS's UTM-based coverage (north of 84°N or south of 80°S), where
     * the Universal Polar Stereographic (UPS) system applies instead — a real edge case, not a
     * hypothetical: Antarctic research stations and the high Arctic sit there. This app has no UPS
     * implementation, so this is the explicit "unsupported" CLAUDE.md requires rather than a
     * clamped or truncated MGRS string standing in for one.
     */
    data class Unsupported(val reason: String) : MgrsCoordinate
}
