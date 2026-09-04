package com.forager.app.domain

import com.forager.app.domain.model.TaxonSearchResult

/**
 * Name search (common or scientific) for building a [com.forager.app.domain.model.TaxonFilter.SpecificTaxon].
 *
 * Split out of [MushroomRepository] rather than kept as one of its methods: search now answers
 * from a bundled local index (`LocalFungiIndexRepository`), while [MushroomRepository]'s other
 * two methods stay live queries against iNaturalist — two different backends behind two owned
 * interfaces, the same one-interface-per-storage-backend shape every other repository in this
 * codebase already follows (`RoomSearchCacheRepository`, `RoomTrackRepository`, etc.), rather
 * than one repository class straddling both.
 */
interface TaxonSearchRepository {
    suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>>
}
