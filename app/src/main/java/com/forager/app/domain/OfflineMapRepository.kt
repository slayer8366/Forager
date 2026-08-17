package com.forager.app.domain

import com.forager.app.domain.model.Region

/**
 * Owned abstraction over a persistent, on-disk store of downloaded USGS map tiles for one region —
 * the same pattern as [MushroomRepository]/[WeatherProvider]/[LocationProvider]: domain and UI code
 * depend on this interface, never on osmdroid's `CacheManager` directly. The real implementation,
 * `OsmdroidOfflineMapRepository`, lives in a new `map/` package parallel to `location/`.
 *
 * ## Why [style] and not a basemap parameter
 *
 * The task this interface was specified from described `download` as taking just a [Region] and a
 * progress callback. That signature cannot actually express *which* USGS raster to fetch — Topo and
 * Imagery are different tile servers — and the type that names that choice,
 * `com.forager.app.ui.map.Basemap`, deliberately lives in `ui/map/` and not `domain/` (see its own
 * doc comment: it is the vendor-selection boundary one level below `MapSlot`, and `domain/` stays
 * free of it same as it stays free of osmdroid itself). [OfflineBasemapStyle] is the smallest
 * domain-safe stand-in: it names "which of the two USGS styles" without importing the UI-owned enum
 * that also carries OpenStreetMap's non-downloadable options. The caller —
 * `AvailabilityViewModel`, which already sits in the UI layer — maps the live `Basemap` down to this
 * two-value style immediately before calling [download]; see its own doc comment for where.
 *
 * ## USGS-only is enforced by the caller, not by this interface
 *
 * This interface has no way to be pointed at a non-USGS tile source at all — [OfflineBasemapStyle]
 * only names the two USGS styles — so there is structurally nothing here to gate. The gate that
 * matters is one level up: `AvailabilityScreen`'s "Offline Maps" section is only ever reachable when
 * `MapService.USGS` is the selected service. See [OfflineBasemapStyle]'s doc comment for why this is
 * USGS-only at all — OpenStreetMap's and OpenTopoMap's own tile-usage policies prohibit bulk
 * downloading.
 */
interface OfflineMapRepository {

    /**
     * Downloads every tile covering [region] for the given [style], reporting progress via
     * [onProgress] (tiles downloaded so far, total tiles). Replaces whatever was previously
     * downloaded, if anything — there is only ever one downloaded region at a time (see
     * [getStatus]'s doc comment for why this doesn't need its own Room table).
     */
    suspend fun download(
        region: Region,
        style: OfflineBasemapStyle,
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

/**
 * Which USGS raster style an offline download targets — this project's domain-safe stand-in for the
 * two USGS entries of `com.forager.app.ui.map.Basemap` (`USGS_TOPO`, `USGS_IMAGERY_TOPO`). See
 * [OfflineMapRepository]'s doc comment for why `domain/` names its own two-value type here rather
 * than importing the UI-owned one.
 *
 * Offline downloading is USGS-only, for two independent reasons recorded in full on
 * `com.forager.app.ui.map.MapService`'s doc comment: USGS's own low zoom ceiling (15) keeps a region
 * download a practical size regardless, and — the reason the feature is not merely inconvenient but
 * actually unavailable elsewhere — both OpenStreetMap's and OpenTopoMap's tile-usage policies
 * prohibit bulk/prefetch downloading against their servers. osmdroid's own pinned artifact encodes
 * the OpenStreetMap half of that directly: `TileSourceFactory.MAPNIK`'s `TileSourcePolicy` sets
 * `FLAG_NO_BULK`, citing `https://operations.osmfoundation.org/policies/tiles/`. The OpenTopoMap half
 * could not be confirmed the same way — the pinned artifact's `TileSourceFactory.OpenTopo` entry
 * carries no explicit policy at all, so nothing in the library itself blocks a bulk request against
 * it — so that finding is one step removed: a web search of `opentopomap.org`'s and
 * `operations.osmfoundation.org`'s own policy text, gathered because this environment's egress proxy
 * blocked both domains on a direct fetch. Treat it as worth a primary-source spot-check before
 * relying on it further, per this task's own instructions.
 */
enum class OfflineBasemapStyle { TOPO, IMAGERY }

data class OfflineMapInfo(
    val region: Region,
    val style: OfflineBasemapStyle,
    val tileCount: Int,
    val sizeBytes: Long,
    val downloadedAtEpochMillis: Long,
)
