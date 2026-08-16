package com.forager.app.domain.model

/**
 * A place that has produced repeatedly: a dense group of iNaturalist observations, close enough
 * together to be "the same spot you'd walk around".
 *
 * This is a description of where observations bunch up in the data already on screen — not a
 * prediction, and not a claim that anything is fruiting there now.
 */
data class ForagingArea(
    /**
     * 1-based position in the suggested visiting order (see
     * [com.forager.app.domain.ClusterForagingAreasUseCase]). This is proximity order — nearest
     * unvisited area next, starting from the search centre — and explicitly not a ranking by
     * quality, nor an optimal or shortest route.
     */
    val visitOrder: Int,
    /** Mean position of the area's observations; the point the map numbers and connects. */
    val center: LatLng,
    /** Every observation in the area, including any with a null [Sighting.observedOn]. */
    val sightings: List<Sighting>,
    /** Distinct [Sighting.taxonId] count — six species beats twelve of the same thing. */
    val distinctSpeciesCount: Int,
    /**
     * Year of the most recent dated observation in the area, or null when *every* observation
     * here is undated. Undated observations still count toward [observationCount] and
     * [distinctSpeciesCount] — their location is real, which is what clustering is about — but
     * they can never contribute a year, and no year is guessed for them. See
     * [undatedObservationCount], which exists so the UI can say "n undated" instead of
     * presenting a partial recency as if it covered everything.
     */
    val mostRecentYear: Int?,
    /** How many of [sightings] have no `observedOn` date at all. */
    val undatedObservationCount: Int,
) {
    val observationCount: Int get() = sightings.size
}

/**
 * The result of looking for foraging areas in a set of sightings.
 *
 * A sealed type rather than a plain list, so "we found nothing" is a state the UI has to handle
 * explicitly with a reason, instead of rendering an empty map that looks identical to a
 * still-loading one (CLAUDE.md: partial or failed results are reported as such).
 */
sealed interface ForagingAreas {

    /** At least one group of observations met the density threshold. */
    data class Found(
        /** Areas in suggested visiting order; `areas[i].visitOrder == i + 1`. */
        val areas: List<ForagingArea>,
        /**
         * Observations that landed in no area — DBSCAN noise. Reported rather than hidden: the
         * user should be able to see that e.g. 40 of 55 pins are scattered singletons.
         */
        val ungroupedObservationCount: Int,
    ) : ForagingAreas

    /**
     * No area met the threshold. The threshold is *not* relaxed until something appears — a
     * fabricated area would be exactly the plausible-value-instead-of-unsupported failure
     * CLAUDE.md rules out.
     */
    data class None(
        val reason: Reason,
        /** How many sightings were examined, so the message can be specific. */
        val observationsConsidered: Int,
    ) : ForagingAreas

    enum class Reason {
        /** The sightings list was empty — nothing was mapped for this region/month/filter. */
        NO_OBSERVATIONS,

        /** Fewer observations exist in total than it takes to form even one area. */
        TOO_FEW_OBSERVATIONS,

        /** Observations exist, but none bunch together densely enough to count as a spot. */
        NO_GROUP_MET_THRESHOLD,
    }
}
