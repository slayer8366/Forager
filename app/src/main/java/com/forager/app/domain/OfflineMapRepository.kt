package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * Owned abstraction over a persistent store of downloaded map regions — the same pattern as
 * [MushroomRepository]/[WeatherProvider]/[LocationProvider]: domain and UI code depend on this
 * interface, never on a map vendor's download API directly. The real implementation,
 * `com.forager.app.map.MapLibreOfflineMapRepository`, lives in the `map/` package parallel to
 * `location/`.
 *
 * ## Many regions, not one
 *
 * An earlier revision of this interface modeled "the one downloaded region" — `download()` deleted
 * whatever was already on disk first, and `getStatus()` answered a single nullable
 * `OfflineMapInfo?`. The design doc this multi-region version implements
 * (`docs/plans/journal-trips-and-offline-regions.md`, "Region management") found that
 * all-or-nothing shape wrong for how offline maps actually get used: a season of foraging visits
 * several distinct places, and downloading a second one shouldn't delete the first. [download] now
 * adds a region rather than replacing the store, [deleteRegion] removes one by its id, and
 * [listRegions] answers every region currently on disk.
 *
 * ## One fixed tile source, not a configurable choice
 *
 * That part of the original design is unchanged: [download] always fetches from the same one
 * source, with no style/service parameter — see the git history on this file for the fuller
 * reasoning, which still holds. Only *how many* regions can exist changed, not *what* they're made
 * of.
 */
interface OfflineMapRepository {

    /**
     * Downloads every tile covering [region] from this repository's one fixed source under [name],
     * reporting progress via [onProgress] (tiles downloaded so far, total tiles). Adds to whatever
     * is already downloaded — see this interface's doc comment for why this no longer replaces a
     * prior download.
     */
    suspend fun download(
        name: String,
        region: Region,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Result<OfflineRegionSummary>

    /** Deletes one downloaded region by [id]. A no-op success, not a failure, if [id] isn't found. */
    suspend fun deleteRegion(id: Long): Result<Unit>

    /**
     * Every region currently on disk, read from the store itself rather than a separately-maintained
     * record that could drift from it — [OfflineRegionSummary.tileCount]/[OfflineRegionSummary.sizeBytes]
     * always come from a live status read, never a variable set during a download, so this list
     * survives a cold start. Empty, not a failure, when nothing is downloaded.
     */
    suspend fun listRegions(): Result<List<OfflineRegionSummary>>

    companion object {
        /**
         * The tile ceiling this repository sets deliberately via
         * `OfflineManager.setOfflineMapboxTileCountLimit` (verified against the pinned
         * `org.maplibre.gl:android-sdk:13.5.0` artifact with `javap` — that method exists under
         * this exact name, takes a `Long`, and has no getter or callback, so this app must track
         * what it set rather than ever reading the value back).
         *
         * Left at the library's own default of 6000 rather than replaced with a guessed number:
         * the design doc's own math (a zoom-14 tile is ~1.7km across at 45°N, matching the ~71
         * tiles observed for a 5km-radius region) puts a 15km-radius region at roughly 600 tiles,
         * so this budget holds about nine such regions — a number with no real usage data yet
         * behind it to say is too few or too many (CLAUDE.md: don't build speculative limits
         * without real data). The point of this constant is that the limit is now chosen on
         * purpose and shown to the user, not that it differs from what MapLibre would have used
         * anyway.
         */
        const val TILE_COUNT_LIMIT: Long = 6000L
    }
}

/** One downloaded region, as read live from the store — see [OfflineMapRepository.listRegions]. */
data class OfflineRegionSummary(
    val id: Long,
    val name: String,
    val region: Region,
    val minZoom: Double,
    val maxZoom: Double,
    val tileCount: Int,
    val sizeBytes: Long,
    val createdAtEpochMillis: Long,
)
