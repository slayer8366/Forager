package com.forager.app.domain.model

/**
 * A ranking of species by historical iNaturalist observation frequency for one region and
 * month. This is a statistical description of the past, not a weather-style forecast: the
 * UI should present it as "how often people have found this here in this month," not as a
 * guarantee.
 */
data class AvailabilityForecast(
    val region: Region,
    val month: Int,
    val filter: TaxonFilter,
    val entries: List<AvailabilityEntry>,
) {
    val totalObservationsConsidered: Int get() = entries.sumOf { it.species.observationCount }
}

data class AvailabilityEntry(
    val species: SpeciesObservationCount,
    /** [species.observationCount] divided by the top count in this result set; 1.0 is the most-observed species. */
    val relativeLikelihood: Float,
)
