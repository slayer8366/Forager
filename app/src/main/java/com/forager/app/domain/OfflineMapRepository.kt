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
 * source, with no style/service parameter. An earlier revision of this interface took a style
 * parameter naming which of USGS's two rasters to fetch; the project owner's own framing, after
 * seeing it built, was that offline downloads should just always target one fixed source regardless
 * of any live-map toggle — see `com.forager.app.map.MapLibreOfflineMapRepository`'s doc comment for
 * the fuller history. Only *how many* regions can exist changed, not *what* they're made of.
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
         * The tile ceiling this app enforces itself before ever starting a download — see
         * [com.forager.app.domain.estimateOfflineTileCount] and
         * `AvailabilityViewModel.onDownloadOfflineMaps`'s pre-flight check.
         *
         * `OfflineManager.setOfflineMapboxTileCountLimit` (verified via `javap` against the pinned
         * `org.maplibre.gl:android-sdk:13.5.0` artifact to exist under this exact name, taking a
         * `Long` with no getter or callback) is *also* still called with this same value, but
         * hardware testing found it does not actually cap explicit offline-region downloads —
         * three regions totalling 9118 tiles downloaded successfully against a "limit" of 6000.
         * The native limit most plausibly caps only the ambient tile cache built up from ordinary
         * live-map browsing, not deliberate `OfflineManager.createOfflineRegion` downloads, which
         * are presumably treated as intentional and exempt. This constant is therefore the real,
         * enforced ceiling only because the app-side pre-flight check makes it one; the native call
         * is kept as a defensive floor, not relied on.
         *
         * Kept at 6000 — the library's own former default — rather than replaced with a guessed
         * number: the design doc's own math (a zoom-14 tile is ~1.7km across at 45°N, matching the
         * ~71 tiles observed for a 5km-radius region) puts a 15km-radius region at roughly 600
         * tiles, so this budget holds about nine such regions — a number with no real usage data
         * yet behind it to say is too few or too many (CLAUDE.md: don't build speculative limits
         * without real data).
         */
        const val TILE_COUNT_LIMIT: Long = 6000L

        /**
         * The zoom range every downloaded region covers — promoted here from what were originally
         * `OFFLINE_MIN_ZOOM`/`OFFLINE_MAX_ZOOM`, private constants inside
         * `com.forager.app.map.MapLibreOfflineMapRepository` (still the one place that actually reads
         * them to build a download definition) so [com.forager.app.domain.estimateOfflineTileCount]
         * can compute a pre-flight tile estimate without the domain layer depending on `map/`, per
         * this project's own "domain never depends on a vendor package" rule.
         *
         * [MAX_ZOOM] is **15.0**, not 14 — that's the branch's own already-verified value, carried
         * over unchanged from `MapLibreOfflineMapRepository`'s prior private constant, not the 14.0
         * an earlier plan draft assumed. Per that constant's own doc comment: the `us.pmtiles`
         * archive backing the download source is built to zoom 14, but the Cloudflare Worker now
         * range-reads and caches individual tiles one level beyond that directly from Protomaps'
         * live daily build (zoom 15, that build's own ceiling), scoped to whatever region an offline
         * download actually requests. 15 therefore stays one level ahead of the local archive's own
         * 14, not equal to it — going further would just 404 against the Worker's own
         * `OVERFLOW_MAX_ZOOM`. [MIN_ZOOM] is an adjustable assumption: a wider span means more usable
         * offline zoom range at the cost of more tiles, and this project has no usage data yet on
         * what span foraging trips actually need. Every downloaded region currently shares this one
         * fixed span; vector tiles overzoom cleanly, so a fixed zoom-15 ceiling renders sharp well
         * past it regardless of a region's radius.
         */
        const val MIN_ZOOM: Double = 10.0
        const val MAX_ZOOM: Double = 15.0

        /**
         * **A client-side constant encoding a server-side fact — it goes stale silently.** The
         * highest zoom the *deployed* tile worker actually advertises in its tileset JSON
         * (`https://forager-pmtiles.brandonlee1-894.workers.dev/us.json`, field `maxzoom`), which is
         * what MapLibre's `OfflineTilePyramidRegionDefinition` clamps an offline download to
         * regardless of the [MAX_ZOOM] the definition asks for. Verified 2026-09-05 by fetching that
         * JSON (`maxzoom: 14`) and probing a zoom-15 tile inside a real region (HTTP 404, zoom 14
         * HTTP 200). The repo's worker source (`server/pmtiles-worker`, commit 46e3647) advertises 15
         * and serves zoom-15 overflow, but the deployed worker does not — the "Workers Builds:
         * forager-pmtiles" check has been failing in zero seconds on every push since then, so that
         * deploy has not gone live. That is a separate dispatch.
         *
         * Why this exists (tile-estimate dispatch, owner finding on device): the pre-flight estimate
         * counted zoom 15 while the download enumerated 10..14, so a 15 km region at 45°N showed
         * "~1774 tiles" and downloaded 480 — 3.7× apart — and larger radii were refused against a
         * budget they actually fit. [com.forager.app.domain.estimateServedOfflineTileCount] estimates
         * against `min(MAX_ZOOM, SERVED_MAX_ZOOM)` so the number shown, the number gated and the
         * number downloaded agree.
         *
         * **What to check, and the consequence, if the worker deploy is ever fixed:** fetch `us.json`
         * again; if `maxzoom` reads 15, raise this to 15.0 — and know that every count then grows
         * ~3.7×, so the 6000-tile [TILE_COUNT_LIMIT] holds a 28 km radius at 45°N rather than the
         * slider's full 50 km (39 km vs 19 km at 60°N). Whether to accept that is a decision, not an
         * arithmetic fix, and belongs with that dispatch.
         */
        const val SERVED_MAX_ZOOM: Double = 14.0
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
