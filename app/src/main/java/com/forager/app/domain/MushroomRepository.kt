package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult

/**
 * Owned abstraction over iNaturalist observation and taxon data. Domain and UI code depend on
 * this interface, never on Retrofit or the iNaturalist DTOs directly (CLAUDE.md: wrap external
 * integrations behind an interface this project owns). "Forager" originally meant fungi only;
 * [filter] is what now scopes each query to fungi, plants, lichens, or one specific species.
 */
interface MushroomRepository {
    /** Verifiable observation counts matching [filter] for [region], filtered to observations made in [month] (1-12), across all years. */
    suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>>

    /**
     * Individual verifiable observations matching [filter] for [region] and [month] (1-12),
     * across all years, that have a mappable position. iNaturalist omits or obscures the
     * location of some observations (e.g. for conservation-sensitive taxa); those are left
     * out here rather than plotted at a fabricated position.
     */
    suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<List<Sighting>>

    /** Name search (common or scientific) for building a [TaxonFilter.SpecificTaxon]. */
    suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>>
}
