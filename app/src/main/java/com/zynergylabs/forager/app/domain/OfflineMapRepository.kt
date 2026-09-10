package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Region

/**
 * Owned abstraction over a persistent store of downloaded map regions — the same pattern as
 * [MushroomRepository]/[WeatherProvider]/[LocationProvider]: domain and UI code depend on this
 * interface, never on a map vendor's download API directly. The real implementation,
 * `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository`, lives in the `map/` package parallel to
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
 * of any live-map toggle — see `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository`'s doc comment for
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
         * [com.zynergylabs.forager.app.domain.estimateOfflineTileCount] and
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
         * number. What it buys changed when [SERVED_MAX_ZOOM] rose to 15 (two-data-corrections
         * dispatch): with zoom 15 in every download a 15 km region at 45°N is ~1 781 tiles, not the
         * ~480 it was at ceiling 14, so this budget now holds about three such regions rather than
         * about nine, and the per-unit default radii (8 km / 10 km, see
         * [com.zynergylabs.forager.app.domain.model.defaultOfflineMapRadiusKm]) cost roughly 530–700 and
         * 820–1 010 tiles depending on latitude — 9–17 % of the budget each. The owner's decision
         * was to shrink the radius ceiling ([MAX_RADIUS_KM]) to fit this budget rather than raise
         * the budget: it moves with the planned Cloudflare upgrade, not before, and there is still
         * no usage data saying how many regions a trip needs (CLAUDE.md: don't build speculative
         * limits without real data).
         */
        const val TILE_COUNT_LIMIT: Long = 6000L

        /**
         * The zoom range every downloaded region covers — promoted here from what were originally
         * `OFFLINE_MIN_ZOOM`/`OFFLINE_MAX_ZOOM`, private constants inside
         * `com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository` (still the one place that actually reads
         * them to build a download definition) so [com.zynergylabs.forager.app.domain.estimateOfflineTileCount]
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
         * regardless of the [MAX_ZOOM] the definition asks for.
         *
         * **Current value, 15.0, verified 2026-09-06 by the owner against the deployed worker:**
         * `us.json` reports `"maxzoom": 15` at the top level and a zoom-15 tile returns HTTP 200
         * with content. The worker's deploy pipeline had been broken for over two weeks
         * (Cloudflare's production branch pointed at a branch deleted on merge), which is why the
         * previous value here was 14.0 — verified 2026-09-05 against a deployed worker that still
         * advertised 14 and 404'd zoom 15 while the repo's worker source already served it. The
         * pipeline is fixed and the zoom-15 overflow is live (`server/pmtiles-worker/src/index.ts`,
         * `OVERFLOW_MAX_ZOOM = 15`).
         *
         * Why this exists (tile-estimate dispatch, owner finding on device): the pre-flight estimate
         * counted zoom 15 while the download enumerated 10..14, so a 15 km region at 45°N showed
         * "~1774 tiles" and downloaded 480 — 3.7× apart — and larger radii were refused against a
         * budget they actually fit. [com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount] estimates
         * against `min(MAX_ZOOM, SERVED_MAX_ZOOM)` so the number shown, the number gated and the
         * number downloaded agree. **That `min` is a no-op today** — both constants read 15.0 — and
         * is kept on purpose as the seam for the next time the two facts diverge (the worker
         * regressing, or [MAX_ZOOM] rising ahead of it); see that function's own doc comment.
         *
         * **What to check, and the consequence, if this ever moves again:** fetch `us.json` and read
         * `maxzoom`. Lowering this back to 14.0 shrinks every count ~3.7× and the radius ceiling
         * [MAX_RADIUS_KM] would then be far more conservative than the budget requires; raising it
         * past [MAX_ZOOM] does nothing until [MAX_ZOOM] follows. Either way the guard test
         * (`OfflineMapRadiusBudgetGuardTest`) pins the arithmetic that ties this constant,
         * [TILE_COUNT_LIMIT] and [MAX_RADIUS_KM] together and fails the build when one moves
         * without the others.
         */
        const val SERVED_MAX_ZOOM: Double = 15.0

        /**
         * The offline-map radius slider's ceiling, in km — **the largest radius whose download fits
         * [TILE_COUNT_LIMIT] on an otherwise empty budget everywhere the tile archive reaches**,
         * rounded to a value that reads cleanly in miles (two-data-corrections dispatch, Part B,
         * owner decision). Deliberately *not* [com.zynergylabs.forager.app.domain.model.Region.MAX_RADIUS_KM]
         * (50), which the iNaturalist search radius keeps: the search costs no tiles, and the two
         * used to share one constant only because nothing had yet made the offline radius cost
         * anything the search radius did not.
         *
         * **The arithmetic, so the next person can redo it rather than trust it.** The archive is a
         * continental-US extract to 49.60°N (`server/pmtiles-worker/README.md`, `--bbox`), so the
         * worst case the app supports is a centre on that edge; Web Mercator tile counts grow with
         * latitude, and also vary by up to one extra column and row per zoom with where the centre
         * sits relative to tile edges, so the ceiling was sized against the *worst alignment* at
         * 49.60°N (a 40 × 40 sweep of the centre across one zoom-15 tile), summed over zooms
         * 10–15 with the same slippy-map math [estimateOfflineTileCount] uses, re-derived
         * independently in the dispatch's pre-build report
         * (`docs/audits/2026-09-06-filter-and-tile-cost-prebuild-report.md`, B2):
         *
         *   24 km → 5 246 worst-case tiles (4 405 at 45.357°N)
         *   25 km → 5 718 (4 860)
         *   26 km → 6 075 (5 246) — over budget.
         *
         * 25 km is the true maximum, at 95 % of the budget; the owner chose **24 km** for the
         * margin against alignment variance and because it reads round in the unit 25 does not
         * ("15 mi" — 24 × 0.621371 = 14.91; 25 km would read "16 mi"), the same reasoning that once
         * left 50 km reading "31 mi" rather than rounding up past the budget. The previous 50 km
         * was never honest at ceiling 15: it costs 18 696 tiles at the owner's latitude and 22 028
         * worst-case at 49.60°N.
         *
         * **Stated, not derived — and guarded.** A runtime derivation from the budget and the
         * ceiling was considered and rejected: it would need the archive's northern latitude as
         * yet another client constant encoding a server fact, plus an alignment argument in code,
         * and would leave the slider's maximum a number nobody can read off the source. Instead
         * `OfflineMapRadiusBudgetGuardTest` pins the three figures above as hand-derived literals
         * and asserts this value fits while a radius two steps larger does not — so when the
         * budget rises with the Cloudflare upgrade, raising [TILE_COUNT_LIMIT] alone fails the
         * build and names this constant as the one to move. Reversal is one constant here plus
         * redoing the table above.
         */
        const val MAX_RADIUS_KM: Int = 24

        /** [MAX_RADIUS_KM]'s clamp — the offline radius's own, distinct from [com.zynergylabs.forager.app.domain.model.Region.clampRadiusKm]. */
        fun clampRadiusKm(radiusKm: Int): Int = radiusKm.coerceIn(com.zynergylabs.forager.app.domain.model.Region.MIN_RADIUS_KM, MAX_RADIUS_KM)
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
