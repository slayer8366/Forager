package com.forager.app.data.repository

import com.forager.app.data.local.fungiindex.FungiIndexDao
import com.forager.app.data.local.fungiindex.FungiNameMatchRow
import com.forager.app.domain.TaxonSearchRepository
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.normalizeSearchName

/**
 * [TaxonSearchRepository] backed by the bundled, offline fungi name index
 * ([com.forager.app.data.local.fungiindex.FungiIndexDatabase]) rather than a live iNaturalist
 * call — see [TaxonSearchRepository]'s doc comment for why search moved off the network
 * entirely. A side effect worth noting: species-name lookup now works with no connectivity,
 * which matters for an app used in the field.
 *
 * [FungiIndexDao.searchByNormalizedName] already returns rows ordered best-tier-first
 * (exact, then prefix, then substring match) and by observation count within a tier, but one
 * per *matching name*, not one per taxon — a well-documented species can match on several common
 * names at once. Deduplicating here, keeping the first (best) row seen per taxon, is what turns
 * that into "one ranked result per species".
 */
class LocalFungiIndexRepository(
    private val dao: FungiIndexDao,
) : TaxonSearchRepository {

    override suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>> {
        return runCatchingCancellable {
            val normalized = normalizeSearchName(query)
            // A query that normalizes away to nothing (e.g. "--") would otherwise match every
            // row as a substring of the empty string.
            if (normalized.isEmpty()) return@runCatchingCancellable emptyList()

            val seenTaxonIds = HashSet<Long>()
            dao.searchByNormalizedName(normalized, RAW_ROW_LIMIT)
                .asSequence()
                .filter { seenTaxonIds.add(it.taxonId) }
                .take(MAX_RESULTS)
                .map(::toDomain)
                .toList()
        }
    }

    /**
     * [TaxonSearchResult.rank] and [TaxonSearchResult.photoUrl] are always null here: the index
     * deliberately carries neither (see `data/species-index/README.md`), and nothing downstream
     * reads them for a search result today — better to say honestly "not known" than fabricate a
     * value. [TaxonSearchResult.iconicTaxonName] is always `"Fungi"`, not a guess: every taxon in
     * this index is one by the index's own taxonomic scope (see
     * `data/species-index/ascomycete-inclusion.md`), so it is true for every row, not an
     * approximation the way it would be for an unfiltered source.
     */
    private fun toDomain(row: FungiNameMatchRow): TaxonSearchResult = TaxonSearchResult(
        taxonId = row.taxonId,
        scientificName = row.scientificName,
        commonName = if (row.isScientific) null else row.matchedName,
        rank = null,
        iconicTaxonName = "Fungi",
        photoUrl = null,
    )

    companion object {
        /**
         * Raw, not-yet-deduplicated row cap — generous relative to [MAX_RESULTS] because a
         * popular, well-documented species can supply many matching name rows (locale variants)
         * ahead of a less common one with only a single matching name.
         */
        private const val RAW_ROW_LIMIT = 300
        private const val MAX_RESULTS = 15
    }
}
