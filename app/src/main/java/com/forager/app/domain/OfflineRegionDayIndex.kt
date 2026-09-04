package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * A day-scoped read of downloaded offline regions — Journal Stage 2a's derived-trip use case.
 *
 * **Deliberately a separate interface from [OfflineMapRepository], not one more method on it.**
 * [OfflineMapRepository.listRegions] correlates its Room-backed index against a *live* read of
 * MapLibre's own native `OfflineManager` (self-healing rows, pruning ones MapLibre no longer has,
 * rebuilding from a region's own metadata blob — see [OfflineMapRepository]'s implementation, in
 * `com.forager.app.map.MapLibreOfflineMapRepository`) — real device/SDK behavior this dispatch's own
 * "do not attempt device verification" instruction and "data layer only" scope both put out of
 * reach. A day-scoped read needs none of that: it is a plain, indexed `WHERE createdAtEpochMillis`
 * query against [com.forager.app.data.local.OfflineRegionEntity], the same table
 * [OfflineMapRepository]'s own implementation already treats as its source of truth. Reusing that
 * interface here would force every one of [OfflineMapRepository]'s existing test doubles (eleven of
 * them, across unrelated `AvailabilityViewModel`/`AvailabilityScreen` test files with nothing to do
 * with date-scoping) to grow a method they have no reason to implement, just to keep compiling — a
 * cost this dispatch's own indexed-query ask does not require paying.
 *
 * [OfflineRegionMetadata] — not [OfflineRegionSummary] — is what this returns: a Room row alone
 * cannot honestly report [OfflineRegionSummary.tileCount]/[OfflineRegionSummary.sizeBytes], since
 * those are read live from MapLibre's own `OfflineManager` and were never persisted to this table at
 * all (see [com.forager.app.data.local.OfflineRegionEntity]'s own doc comment). Reporting a fabricated
 * `0` for either would be exactly the "fabricated plausible value" CLAUDE.md's error-handling
 * standards forbid, so this type simply does not claim to have them.
 */
interface OfflineRegionDayIndex {
    /**
     * Every region *downloaded* on one local day — [com.forager.app.data.local.OfflineRegionEntity.createdAtEpochMillis],
     * not when the data it covers was collected. Half-open range `[dayStartInclusiveEpochMillis,
     * dayEndExclusiveEpochMillis)` — see [LocalDayRange]'s own doc comment for why.
     */
    suspend fun getRegionsCreatedOn(
        dayStartInclusiveEpochMillis: Long,
        dayEndExclusiveEpochMillis: Long,
    ): Result<List<OfflineRegionMetadata>>
}

/**
 * An offline region's own stored fields, without [OfflineRegionSummary]'s live-read-only
 * `tileCount`/`sizeBytes` — see [OfflineRegionDayIndex]'s own doc comment for why those cannot
 * honestly appear here.
 */
data class OfflineRegionMetadata(
    val id: Long,
    val name: String,
    val region: Region,
    val minZoom: Double,
    val maxZoom: Double,
    val createdAtEpochMillis: Long,
    val isEntryCapture: Boolean,
)
