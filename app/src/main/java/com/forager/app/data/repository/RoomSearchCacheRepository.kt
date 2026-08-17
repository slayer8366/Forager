package com.forager.app.data.repository

import android.util.Log
import com.forager.app.data.local.CachedSearchDao
import com.forager.app.data.local.CachedSearchEntity
import com.forager.app.data.local.CachedSearchPayload
import com.forager.app.data.local.cachedSearchKey
import com.forager.app.domain.CachedAvailability
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.SearchCacheRepository
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter

/**
 * Room-backed [SearchCacheRepository]; the only place [CachedSearchEntity] and
 * [AvailabilityForecast] meet.
 *
 * **Least-recently-used, five deep.** Five because the picker it feeds is a short list a user
 * scans, not a history to search — and because the searches worth having offline are the handful
 * of places somebody actually forages, not everywhere they have ever tapped. It is a stated,
 * adjustable choice ([MAX_CACHED_SEARCHES]), not a figure derived from anything.
 *
 * **Storage failures degrade to "no cache" and are logged**, never thrown — see
 * [SearchCacheRepository]'s doc comment for why a cache failure must not be able to present itself
 * as a failed search. Logged rather than dropped, per CLAUDE.md: a fallback that fires silently is
 * exactly the kind that goes unnoticed for months.
 */
class RoomSearchCacheRepository(
    private val dao: CachedSearchDao,
    private val currentTime: CurrentTimeProvider,
) : SearchCacheRepository {

    override suspend fun getCached(region: Region, month: Int, filter: TaxonFilter): CachedAvailability? =
        runCatchingCancellable {
            // The read bumps the row's LRU stamp inside the DAO's transaction, so a cached search
            // that keeps being used keeps its place.
            dao.getByKeyAndTouch(cachedSearchKey(region, month, filter), currentTime.nowEpochMillis())
                ?.toCachedAvailability()
        }.getOrElse { error ->
            Log.w(TAG, "Couldn't read the search cache; treating it as a miss.", error)
            null
        }

    override suspend fun save(forecast: AvailabilityForecast) {
        runCatchingCancellable {
            val now = currentTime.nowEpochMillis()
            dao.upsertAndEvictBeyond(forecast.toEntity(now), MAX_CACHED_SEARCHES)
        }.onFailure { error ->
            Log.w(TAG, "Couldn't write the search cache; this result won't be available offline.", error)
        }
    }

    override suspend fun getRecent(): List<CachedSearchSummary> =
        runCatchingCancellable {
            dao.getAllOrderedByLastAccessed()
                // Eviction already bounds the table; this makes the interface's "up to 5" true of
                // this method rather than only true of whatever last wrote to the table.
                .take(MAX_CACHED_SEARCHES)
                .map { it.toSummary() }
        }.getOrElse { error ->
            Log.w(TAG, "Couldn't read recent searches; showing none.", error)
            emptyList()
        }

    private companion object {
        const val TAG = "SearchCache"

        /** How many distinct searches are kept. See this class's doc comment. */
        const val MAX_CACHED_SEARCHES = 5
    }
}

private fun AvailabilityForecast.toEntity(nowEpochMillis: Long): CachedSearchEntity {
    // Exhaustive on TaxonFilter's two shapes, so a third one is a compile error here rather than a
    // row that stores a label and loses the query it stood for.
    val iconicTaxonName = when (val stored = filter) {
        is TaxonFilter.IconicCategory -> stored.iconicTaxonName
        is TaxonFilter.SpecificTaxon -> null
    }
    val taxonId = when (val stored = filter) {
        is TaxonFilter.IconicCategory -> null
        is TaxonFilter.SpecificTaxon -> stored.taxonId
    }
    return CachedSearchEntity(
        key = cachedSearchKey(region, month, filter),
        lat = region.lat,
        lng = region.lng,
        radiusKm = region.radiusKm,
        month = month,
        filterLabel = filter.label,
        filterIconicTaxonName = iconicTaxonName,
        filterTaxonId = taxonId,
        filterExcludedTaxonId = filter.excludedTaxonId,
        entriesJson = CachedSearchPayload.encode(entries),
        fetchedAtEpochMillis = nowEpochMillis,
        lastAccessedAtEpochMillis = nowEpochMillis,
    )
}

private fun CachedSearchEntity.toCachedAvailability() = CachedAvailability(
    forecast = AvailabilityForecast(
        region = toRegion(),
        month = month,
        filter = toFilter(),
        entries = CachedSearchPayload.decode(entriesJson),
    ),
    cachedAtEpochMillis = fetchedAtEpochMillis,
)

private fun CachedSearchEntity.toSummary() = CachedSearchSummary(
    region = toRegion(),
    month = month,
    filter = toFilter(),
    cachedAtEpochMillis = fetchedAtEpochMillis,
)

private fun CachedSearchEntity.toRegion() = Region(lat = lat, lng = lng, radiusKm = radiusKm)

/**
 * Rebuilds the stored filter. A row carrying neither discriminant cannot have been written by
 * [toEntity], so it is a corrupt row: it throws rather than guessing a filter, and the caller
 * turns that into a logged cache miss (see [RoomSearchCacheRepository]).
 */
private fun CachedSearchEntity.toFilter(): TaxonFilter = when {
    filterIconicTaxonName != null -> TaxonFilter.IconicCategory(
        iconicTaxonName = filterIconicTaxonName,
        label = filterLabel,
        excludedTaxonId = filterExcludedTaxonId,
    )

    filterTaxonId != null -> TaxonFilter.SpecificTaxon(taxonId = filterTaxonId, label = filterLabel)

    else -> error("Cached search '$key' stores neither an iconic taxon name nor a taxon id.")
}
