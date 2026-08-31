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
     * across all years, that have a mappable, non-obscured position, plus iNaturalist's own
     * total for the query (which includes observations excluded below — see [SightingsPage] for
     * why the total travels separately from the filtered list).
     *
     * Two kinds of observation are left out of
     * [SightingsPage.sightings][com.forager.app.domain.model.SightingsPage.sightings], never
     * plotted at a fabricated or approximate position:
     * - iNaturalist withheld the location entirely (fully private geoprivacy) — no coordinates
     *   are present to plot.
     * - iNaturalist marked the observation `obscured` (e.g. conservation-sensitive taxa, or the
     *   observer's own choice) — a `location` *is* present here, but it's been randomized to a
     *   coarse cell tens of kilometres across, not the true point, so it is excluded rather than
     *   plotted as if precise. See
     *   [com.forager.app.data.repository.INaturalistMushroomRepository]'s `toDomain` for the
     *   field this is gated on and why.
     *
     * Every [Sighting][com.forager.app.domain.model.Sighting] that does come back carries its own
     * positional accuracy (ordinary GPS error, which is not filtered — see
     * [Sighting.positionalAccuracyMeters][com.forager.app.domain.model.Sighting.positionalAccuracyMeters]),
     * surfaced rather than used as a further cutoff.
     */
    suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage>
}
