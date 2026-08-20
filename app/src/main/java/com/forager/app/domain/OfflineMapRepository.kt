package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * Owned abstraction over a persistent store of a downloaded map region — the same pattern as
 * [MushroomRepository]/[WeatherProvider]/[LocationProvider]: domain and UI code depend on this
 * interface, never on a map vendor's download API directly. The real implementation,
 * `com.forager.app.map.MapLibreOfflineMapRepository`, lives in the `map/` package parallel to
 * `location/`.
 *
 * ## One fixed source, not a configurable choice
 *
 * An earlier revision of this interface took a style parameter naming which of USGS's two rasters
 * (Topo or Imagery) to fetch, resolved from whichever mode the map's own quick-fire icon happened
 * to be showing. The project owner's own framing, after seeing it built: "since offline maps only
 * loads USGS, then there's no need to have it react to the toggle. have it assume USGS usage and
 * have it ready to function." That reasoning still holds after the underlying source changed:
 * [download] always fetches the same one thing — a parameter with exactly one possible value is
 * dead configurability, not flexibility, so this interface still doesn't take one.
 *
 * What that one fixed thing *is* has changed. Originally USGS Topo raster tiles via osmdroid's
 * `CacheManager` (`OsmdroidOfflineMapRepository`, since deleted). Now OSM-derived vector tiles read
 * from a self-hosted Cloudflare Worker over a continental-US PMTiles archive, via MapLibre's
 * `OfflineManager` (`MapLibreOfflineMapRepository`) — see that class's doc comment and
 * `docs/plans/pmtiles-worker-android-wiring.md` for why. The swap changed nothing about this
 * interface, which is the point of depending on it rather than a concrete implementation.
 *
 * This interface has no way to be pointed at a different source at all — it doesn't take a tile
 * source or basemap parameter of any kind — so there is structurally nothing here to gate, and
 * nothing upstream needs to condition reachability on which map service is currently selected for
 * live browsing either: offline downloading was never coupled to that selection once it stopped
 * being a configurable choice. See `com.forager.app.ui.map.MapService`'s doc comment for the fuller
 * history of that decoupling.
 */
interface OfflineMapRepository {

    /**
     * Downloads every tile covering [region] from this repository's one fixed source, reporting
     * progress via [onProgress] (tiles downloaded so far, total tiles). Replaces whatever was
     * previously downloaded, if anything — there is only ever one downloaded region at a time (see
     * [getStatus]'s doc comment for why this doesn't need its own Room table).
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
