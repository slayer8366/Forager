package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SpeciesObservationCount

/**
 * Owned abstraction over mushroom observation data. Domain and UI code depend on this
 * interface, never on Retrofit or the iNaturalist DTOs directly (CLAUDE.md: wrap external
 * integrations behind an interface this project owns).
 */
interface MushroomRepository {
    /** Verifiable fungi observation counts for [region], filtered to observations made in [month] (1-12), across all years. */
    suspend fun getSpeciesCounts(region: Region, month: Int): Result<List<SpeciesObservationCount>>
}
