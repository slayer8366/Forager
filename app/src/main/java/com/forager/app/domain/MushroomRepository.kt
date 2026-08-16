package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SpeciesObservationCount

/**
 * Owned abstraction over mushroom observation data. Domain and UI code depend on this
 * interface, never on Retrofit or the iNaturalist DTOs directly (CLAUDE.md: wrap external
 * integrations behind an interface this project owns).
 */
interface MushroomRepository {
    /** Verifiable fungi observation counts for [region], filtered to observations made in [month] (1-12), across all years. */
    suspend fun getSpeciesCounts(region: Region, month: Int): Result<List<SpeciesObservationCount>>

    /**
     * Individual verifiable fungi observations for [region] and [month] (1-12), across all
     * years, that have a mappable position. iNaturalist omits or obscures the location of
     * some observations (e.g. for conservation-sensitive taxa); those are left out here
     * rather than plotted at a fabricated position.
     */
    suspend fun getSightings(region: Region, month: Int): Result<List<Sighting>>
}
