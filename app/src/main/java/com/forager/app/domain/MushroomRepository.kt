package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter

/**
 * Owned abstraction over iNaturalist observation data. Domain and UI code depend on this
 * interface, never on Retrofit or the iNaturalist DTOs directly (CLAUDE.md: wrap external
 * integrations behind an interface this project owns). "Forager" originally meant fungi only;
 * [filter] is what now scopes each query to fungi, plants, lichens, or one specific species.
 *
 * Taxon-name search is not one of this interface's methods — see [TaxonSearchRepository]'s doc
 * comment for why it was split out.
 */
interface MushroomRepository {
    /** Verifiable observation counts matching [filter] for [region], filtered to observations made in [month] (1-12), across all years. */
    suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter): Result<List<SpeciesObservationCount>>

    /**
     * Individual verifiable observations matching [filter] for [region] and [month] (1-12),
     * across all years, that have a mappable position, plus iNaturalist's own total for the
     * query. iNaturalist omits or obscures the location of some observations (e.g. for
     * conservation-sensitive taxa); those are left out of
     * [SightingsPage.sightings][com.forager.app.domain.model.SightingsPage.sightings] rather
     * than plotted at a fabricated position — see [SightingsPage] for why the total travels
     * separately from the filtered list.
     */
    suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage>
}
