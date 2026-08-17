package com.forager.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter

/**
 * Room's on-disk shape for one cached ranked species list.
 *
 * Room annotations and serialization are data-layer only — never `domain/`, per CLAUDE.md and the
 * same boundary [PlannedTripEntity]'s doc comment states — so this type, its DTOs
 * ([CachedSearchPayload]), [CachedSearchDao] and `RoomSearchCacheRepository` all stay in `data/`,
 * and that repository is the only place this ever meets
 * [com.forager.app.domain.model.AvailabilityForecast].
 *
 * **[key] is the whole match rule.** It encodes the search's region, month and filter into one
 * string, and equality of that string is the lookup: exact, not fuzzy (see
 * [com.forager.app.domain.SearchCacheRepository]). The encoding is faithful to Kotlin's own
 * equality on those values rather than approximating it — [Double.toString] produces the shortest
 * decimal that reads back as the identical `Double`, so two [Region]s produce the same key exactly
 * when they are `==`, including the `-0.0` / `0.0` distinction Kotlin's generated `equals` also
 * makes.
 *
 * The searchable columns are stored alongside the key rather than parsed back out of it: the
 * recent-searches picker needs a real [Region], month and [TaxonFilter] to re-run a search, and
 * re-parsing a delimited string into typed values is a decoding bug waiting to happen for no gain.
 *
 * The ranked entries are the one thing kept as JSON ([entriesJson]) — they are a variable-length
 * list, which a row of columns cannot hold — and the two timestamps are separate on purpose:
 * [fetchedAtEpochMillis] is what the UI reports as the result's age, [lastAccessedAtEpochMillis]
 * is what LRU eviction orders by. Reusing one field for both would either re-date a result every
 * time it was read (making a stale list look fresh) or evict by age instead of by use.
 */
@Entity(tableName = "cached_searches")
data class CachedSearchEntity(
    @PrimaryKey val key: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
    val month: Int,
    val filterLabel: String,
    /** iNaturalist iconic taxon name; non-null exactly for [TaxonFilter.IconicCategory]. */
    val filterIconicTaxonName: String?,
    /** Taxon id; non-null exactly for [TaxonFilter.SpecificTaxon]. */
    val filterTaxonId: Long?,
    /** [TaxonFilter.excludedTaxonId], which only an [TaxonFilter.IconicCategory] can carry. */
    val filterExcludedTaxonId: Long?,
    /** [CachedSearchPayload]-encoded ranked entries. */
    val entriesJson: String,
    /** When the live search behind this row was answered — the age shown on screen. */
    val fetchedAtEpochMillis: Long,
    /** When this row was last written or read — the LRU order eviction uses. */
    val lastAccessedAtEpochMillis: Long,
)

/**
 * The lookup key for a search, and the single source of truth for what "the same search" means.
 * Built by both the read and the write path so they cannot drift apart.
 *
 * The filter part discriminates on [TaxonFilter]'s two shapes exhaustively via `when`, so adding a
 * third one is a compile error here rather than a silent key collision between two different
 * searches.
 */
internal fun cachedSearchKey(region: Region, month: Int, filter: TaxonFilter): String {
    val filterPart = when (filter) {
        is TaxonFilter.IconicCategory -> "iconic:${filter.iconicTaxonName}:without=${filter.excludedTaxonId}"
        is TaxonFilter.SpecificTaxon -> "taxon:${filter.taxonId}"
    }
    return "${region.lat}|${region.lng}|${region.radiusKm}|$month|$filterPart"
}
