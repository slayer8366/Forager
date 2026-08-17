package com.forager.app.domain

/**
 * The searches the recent-searches picker offers, newest-used first.
 *
 * Deliberately thin — it adds no ordering, no filtering and no formatting of its own; the LRU
 * order is a property of the store (see [SearchCacheRepository.getRecent]) and the labels are the
 * UI's business. It exists so the ViewModel depends on a use case like it does for every other
 * read, rather than being the one place that reaches into a repository directly.
 */
class GetRecentSearchesUseCase(
    private val cache: SearchCacheRepository,
) {
    suspend operator fun invoke(): List<CachedSearchSummary> = cache.getRecent()
}
