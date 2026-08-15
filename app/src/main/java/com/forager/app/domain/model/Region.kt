package com.forager.app.domain.model

/** A circular search area: iNaturalist observations are queried within [radiusKm] of the point. */
data class Region(
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
) {
    companion object {
        const val MIN_RADIUS_KM = 1
        const val MAX_RADIUS_KM = 50

        /**
         * iNaturalist's API accepts a search radius up to 500km, but a foraging search that
         * wide stops telling you anything about where to actually go today. Clamp to the
         * app's own locally-meaningful range instead of trusting the API's full operating
         * envelope (CLAUDE.md: capability ranges are not operating envelopes).
         */
        fun clampRadiusKm(radiusKm: Int): Int = radiusKm.coerceIn(MIN_RADIUS_KM, MAX_RADIUS_KM)
    }
}
