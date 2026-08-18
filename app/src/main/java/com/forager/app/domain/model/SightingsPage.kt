package com.forager.app.domain.model

/**
 * The result of one [com.forager.app.domain.MushroomRepository.getSightings] call: the mappable
 * sightings themselves, plus the true count iNaturalist reports for the query behind them.
 *
 * [totalResults] exists so a caller can say how partial [sightings] is. iNaturalist's
 * `species_counts`/`observations` calls are unpaginated single requests capped at 200 results
 * (`INaturalistApi.getObservations`'s `perPage` default), and [sightings] additionally drops any
 * observation iNaturalist gave no mappable position for (see
 * [com.forager.app.data.repository.INaturalistMushroomRepository]'s `toDomain`). Comparing
 * `sightings.size` against the 200 cap to guess completeness would therefore be wrong in the
 * direction that matters least safely: a 200-result page that loses 30 to missing coordinates
 * yields 170 sightings, and `170 >= 200` reads as "this is everything" when it is not.
 * [totalResults] is iNaturalist's own count from before either filter, so it is never fooled by
 * either.
 */
data class SightingsPage(
    val sightings: List<Sighting>,
    /** iNaturalist's own `total_results` for the query, before this app's page-size cap and mappable-position filtering. */
    val totalResults: Int,
)
