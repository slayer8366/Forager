package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * Owned abstraction over a persistent, on-disk store of downloaded USGS Topo map tiles for one
 * region — the same pattern as [MushroomRepository]/[WeatherProvider]/[LocationProvider]: domain
 * and UI code depend on this interface, never on osmdroid's `CacheManager` directly. The real
 * implementation, `OsmdroidOfflineMapRepository`, lives in a new `map/` package parallel to
 * `location/`.
 *
 * ## Always USGS Topo, not a configurable choice
 *
 * An earlier revision of this interface took a style parameter naming which of USGS's two rasters
 * (Topo or Imagery) to fetch, resolved from whichever mode the map's own quick-fire icon happened
 * to be showing. The project owner's own framing, after seeing it built: "since offline maps only
 * loads USGS, then there's no need to have it react to the toggle. have it assume USGS usage and
 * have it ready to function." [download] now always fetches USGS Topo — a parameter with exactly
 * one possible value is dead configurability, not flexibility, so it was removed rather than kept
 * and hardcoded at the call site.
 *
 * ## USGS-only is enforced by [OsmdroidOfflineMapRepository], not by a live UI selection
 *
 * This interface has no way to be pointed at a non-USGS tile source at all — it doesn't take a tile
 * source or basemap parameter of any kind — so there is structurally nothing here to gate, and
 * nothing upstream needs to condition reachability on which map service is currently selected for
 * live browsing either: offline downloading was never coupled to that selection once it stopped
 * being a configurable choice. See `com.forager.app.ui.map.MapService`'s doc comment for why USGS
 * is the only source this ever downloads from at all — OpenStreetMap's and OpenTopoMap's own
 * tile-usage policies prohibit bulk downloading.
 */
interface OfflineMapRepository {

    /**
     * Downloads every USGS Topo tile covering [region], reporting progress via [onProgress] (tiles
     * downloaded so far, total tiles). Replaces whatever was previously downloaded, if anything —
     * there is only ever one downloaded region at a time (see [getStatus]'s doc comment for why
     * this doesn't need its own Room table).
     */
    suspend fun download(
        region: Region,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Result<OfflineMapInfo>

    /** Deletes the downloaded region, if any. A no-op success, not a failure, if nothing is downloaded. */
    suspend fun delete(): Result<Unit>

    /**
     * What's currently on disk, read from the store itself rather than a separately-maintained
     * record that could drift from it. `null` means nothing is downloaded — not an empty
     * [OfflineMapInfo], per CLAUDE.md's rule against a fabricated plausible value standing in for
     * "there is no answer".
     */
    suspend fun getStatus(): Result<OfflineMapInfo?>
}

data class OfflineMapInfo(
    val region: Region,
    val tileCount: Int,
    val sizeBytes: Long,
    val downloadedAtEpochMillis: Long,
)
