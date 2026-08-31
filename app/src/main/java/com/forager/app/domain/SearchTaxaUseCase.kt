package com.forager.app.domain

import com.forager.app.domain.model.TaxonSearchResult

/**
 * Name search for the species-search UI. Queries shorter than [MIN_QUERY_LENGTH] return an
 * empty result without calling the network, rather than firing a request on every keystroke.
 * Pure and Android-framework-free so it's unit-testable headless.
 */
class SearchTaxaUseCase(
    private val repository: TaxonSearchRepository,
) {
    suspend operator fun invoke(query: String): Result<List<TaxonSearchResult>> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return Result.success(emptyList())
        return repository.searchTaxa(trimmed)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
