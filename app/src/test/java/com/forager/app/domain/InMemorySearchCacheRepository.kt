package com.forager.app.domain

import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter

/**
 * A [SearchCacheRepository] held in a map, for tests about something other than storage.
 *
 * It mirrors the store's *contract* — exact region+month+filter matching, most-recently-used
 * first, at most [MAX_ENTRIES] — and nothing about how Room implements it. That division matters
 * per CLAUDE.md's warning about fakes that only echo an assumption back: the real store's
 * behaviour is pinned separately by `RoomSearchCacheRepositoryTest` against an actual database, so
 * nothing here is the evidence for it. What this is evidence for is the behaviour of the code
 * under test around a cache — which entries a ViewModel or use case asks for, and what it does
 * with the answer.
 */
class InMemorySearchCacheRepository(
    private val currentTime: CurrentTimeProvider = CurrentTimeProvider { 0L },
) : SearchCacheRepository {

    private data class Key(val region: Region, val month: Int, val filter: TaxonFilter)

    /** Most recently used last, so eviction takes from the front and [getRecent] reads it reversed. */
    private val entries = LinkedHashMap<Key, CachedAvailability>()

    /** How many times [save] has been called — proof a write-through actually happened. */
    var saveCount: Int = 0
        private set

    override suspend fun getCached(region: Region, month: Int, filter: TaxonFilter): CachedAvailability? {
        val key = Key(region, month, filter)
        val hit = entries.remove(key) ?: return null
        entries[key] = hit
        return hit
    }

    override suspend fun save(forecast: AvailabilityForecast) {
        saveCount++
        val key = Key(forecast.region, forecast.month, forecast.filter)
        entries.remove(key)
        entries[key] = CachedAvailability(forecast, currentTime.nowEpochMillis())
        while (entries.size > MAX_ENTRIES) {
            entries.remove(entries.keys.first())
        }
    }

    override suspend fun getRecent(): List<CachedSearchSummary> = entries.entries
        .reversed()
        .take(MAX_ENTRIES)
        .map { (key, cached) ->
            CachedSearchSummary(
                region = key.region,
                month = key.month,
                filter = key.filter,
                cachedAtEpochMillis = cached.cachedAtEpochMillis,
            )
        }

    private companion object {
        const val MAX_ENTRIES = 5
    }
}

/** A clock a test moves by hand, so "how long ago" is something the test states rather than races. */
class MutableClock(var now: Long = 0L) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
