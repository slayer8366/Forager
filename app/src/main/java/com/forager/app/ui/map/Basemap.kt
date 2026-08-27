package com.forager.app.ui.map

/**
 * Where a basemap has tiles, and what to tell the user when the answer isn't "everywhere".
 *
 * [note] is non-null only for a limited-coverage basemap, so the UI has nothing to say — and says
 * nothing — about one that works anywhere. The limit is stated rather than detected: see
 * [Basemap]'s doc comment for why blank-tile detection was rejected.
 */
enum class BasemapCoverage(val note: String?) {
    WORLDWIDE(null),

    UNITED_STATES_ONLY(
        "United States only. Outside the US this basemap has no tiles and the map will come up " +
            "empty — switch to OpenStreetMap or OpenTopoMap there.",
    ),
}

/**
 * The basemaps the map can draw its tiles from, as this project's own type rather than a vendor's.
 *
 * This is the [MapSlot] idea one level down: the choice a user makes is described here in pure
 * Kotlin — no MapLibre, no Compose, no Android — and [styleJsonFor] (in `BasemapStyles.kt`) is the
 * single place that turns it into a MapLibre `Style`. So the coverage notes and the zoom ceilings
 * are all unit-testable headless (see `BasemapTest`); the vendor type stays behind one function
 * (`BasemapStyleTest` checks the style each entry actually produces).
 *
 * ## Migration note (osmdroid -> MapLibre, `docs/plans/maplibre-migration.md` §2)
 *
 * Before this change, [tileUrlTemplate] fed osmdroid's `ITileSource` machinery via
 * `BasemapTileSources.kt`'s `tileSourceFor`, deleted in the same change that added this field.
 * The four basemaps and every fact this class states about them — coverage, zoom ceiling,
 * attribution, the ArcGIS row/column URL order — are unchanged: this migration swaps which
 * renderer reads [tileUrlTemplate], not what the four services are or what limits apply to them.
 * `[MapLibre] becomes a catalogue of style URLs instead of a catalogue of ITileSources` is the
 * plan's own framing (§2); see `BasemapStyles.kt`'s doc comment for why that catalogue is built as
 * an inline style JSON per basemap rather than four separately hosted style documents — the two are
 * equivalent to a MapLibre `Style.Builder`, and building it from this enum's own data keeps
 * [Basemap] the single source of truth instead of splitting it across four hosted files that could
 * drift from what this class documents.
 *
 * All four basemaps stay **raster** sources through this step — the same tile services osmdroid
 * fetched from, wrapped in the minimal MapLibre raster-style JSON a raster source needs. The
 * OSM-derived **vector** basemap this project's own PMTiles Worker could serve
 * (`docs/plans/pmtiles-worker-android-wiring.md`, "steps 1-2") is deliberately not adopted here:
 * that Worker and its live-tile-serving cost are explicitly scoped to the *offline download* path
 * only (`com.forager.app.map.MapLibreOfflineMapRepository`), which is separately built, merged, and
 * hardware-verified — reusing it for live browsing too is a real product/infrastructure decision
 * (four times the request volume against a personal-account Cloudflare Worker, for one) that this
 * renderer-swap commit does not make silently. Tracked as follow-up work, not dropped.
 *
 * ## How a basemap actually gets chosen
 *
 * Not from this enum directly. [MapMode] names exactly three — Street and Topographical (both
 * OpenStreetMap-derived), Satellite (USGS) — picked from the map's own quick-fire "Map Mode"
 * control, [MapModePicker]. See [MapMode]'s own doc comment for the full picture, including what
 * this superseded (a two-tier service/mode split, and PR #13's original USGS Topo default).
 *
 * **USGS National Map covers the United States only**, which is why it cannot be a hardcoded
 * default regardless of which type resolves it: that would break the map outright for any user
 * outside the US. Two alternatives to stating the limit outright were rejected:
 *
 * - **Auto-detecting coverage and falling back to OpenStreetMap.** Blank-tile detection is
 *   unreliable, and CLAUDE.md forbids an unlogged silent fallback. Worse, the failure here is not
 *   even blank tiles: the service answers **HTTP 404** outside the US (measured — see
 *   `scripts/verify-usgs-basemap.sh`), which a raster loader reports as a missing tile
 *   indistinguishable from a network failure or a not-yet-loaded tile. A heuristic on top of that
 *   would be invented, not derived.
 * - **Picking by device locale or GPS position.** Both guess at the answer, and both switch the map
 *   out from under the user without being asked. The user chooses; nothing switches behind their
 *   back.
 *
 * So the coverage limit is carried as [BasemapCoverage] text shown next to the choice, which is
 * what the app can honestly say: it knows the documented limit, it cannot detect the boundary.
 *
 * ## [maxZoom] is an operating limit, not a reported range
 *
 * CLAUDE.md: a reported capability range describes what's possible, not what's safe to use. These
 * numbers are the explicit limit this app applies. [SightingsMap] installs each one twice, for two
 * different reasons: `"maxzoom"` inside the raster source's own style JSON tells MapLibre not to
 * request tiles the service doesn't have (MapLibre resamples the last real zoom level past that
 * point rather than requesting a 404), and `MapLibreMap.setMaxZoomPreference` stops the user's own
 * pinch/double-tap gesture from going there in the first place — the same two-part enforcement
 * `setTileSource` + `setMaxZoomLevel` did for osmdroid, now split the same way MapLibre's own API
 * splits it (declared-in-source vs. camera-preference).
 *
 * The USGS figure is a case study in why the rule exists. Three sources disagree:
 *
 * - The service's own metadata (`MapServer?f=json`) advertises `tileInfo.lods` up to level **23**.
 * - Requesting tiles directly returns 200 with a JPEG body through **z16** and **404 from z17** —
 *   checked at four US locations (Portland OR, Manhattan, Denver, rural Montana), both USGS
 *   services. The advertised 23 is simply wrong.
 * - osmdroid's own `TileSourceFactory.USGS_TOPO` — no longer in this app's dependency graph once
 *   this migration finishes, but the number it declared is the same one being carried forward here
 *   — declared **15**.
 *
 * This app uses 15: it sits one level below the *observed* ceiling, and four sample points are not
 * enough to establish that 16 holds everywhere when the service's own metadata is already
 * demonstrably unreliable. Trusting the advertised 23 would have produced exactly the blank-tile
 * wall this field exists to prevent.
 *
 * ## [attribution] is not the only on-map notice, but it is the guaranteed one
 *
 * osmdroid's `CopyrightOverlay` drew `ITileSource.getCopyrightNotice()` directly on the map, with no
 * user action needed to see it — and for the USGS sources that notice was the bare string `"USGS"`,
 * crediting neither The National Map nor the public-domain status, which is why [attribution] here
 * carries the fuller line instead of trusting that string. MapLibre has its own on-map affordance
 * (`UiSettings.isAttributionEnabled`, an "i" icon that opens a dialog built from each source's own
 * `attribution` field in the style JSON — `BasemapStyles.kt` sets it to this same [attribution]
 * string) but that is tap-to-reveal, not always-drawn the way `CopyrightOverlay` was. [SightingsMap]
 * therefore also renders [attribution] as a small always-visible Compose caption over the map,
 * so the always-drawn guarantee this app relied on for USGS/ODbL credit doesn't quietly become
 * tap-to-reveal-only as a side effect of the renderer swap.
 */
enum class Basemap(
    val label: String,
    val description: String,
    val coverage: BasemapCoverage,
    /** The highest zoom this app will let the user reach on this basemap. See the class doc. */
    val maxZoom: Int,
    val attribution: String,
    /**
     * The raw `{z}`/`{x}`/`{y}` tile URL template MapLibre substitutes into, per basemap.
     *
     * The USGS pair use ArcGIS's `tile/{z}/{y}/{x}` — row before column — not the `{z}/{x}/{y}` the
     * other two use. Getting that backwards is the failure mode worth naming: transposed
     * coordinates return a perfectly valid tile from the wrong place on Earth, so the map looks
     * like it works. `BasemapStyleTest` pins the built URL for a known tile the same way
     * `BasemapTileSourceTest` did for osmdroid, and `scripts/verify-usgs-basemap.sh` is the live
     * check that this exact shape serves a real JPEG.
     */
    internal val tileUrlTemplate: String,
) {
    /**
     * Pure aerial orthoimagery, no topo labels drawn over it — the "other one," per the project
     * owner's own distinction from [USGS_IMAGERY_TOPO]'s look (deleted alongside [USGS_TOPO]; see
     * [MapMode]'s own doc comment for why neither is reachable any more). Confirmed live: real
     * JPEGs through z16, 404 from z17, 404 outside the US — the same behaviour this project already
     * verified for its USGS siblings before they were removed, via the same three-part check
     * `scripts/verify-usgs-basemap.sh` runs.
     */
    USGS_IMAGERY_ONLY(
        label = "USGS Satellite",
        description = "Aerial orthoimagery, no labels. Public domain.",
        coverage = BasemapCoverage.UNITED_STATES_ONLY,
        maxZoom = 15,
        attribution = "USGS The National Map, orthoimagery — public domain",
        tileUrlTemplate = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}",
    ),

    OPEN_TOPO_MAP(
        label = "OpenTopoMap",
        description = "Topographic contours and hillshading, worldwide.",
        coverage = BasemapCoverage.WORLDWIDE,
        maxZoom = 17,
        // Shortened from "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)" -- the
        // full form wrapped to two lines over the always-visible caption (see SightingsMap.kt),
        // covering more of the map than a credit line should. Still names all three required
        // credits (OpenStreetMap, SRTM, OpenTopoMap's CC-BY-SA), just without the "contributors"
        // qualifier and the " | " separator.
        attribution = "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",
        tileUrlTemplate = "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
    ),

    OSM_STANDARD(
        label = "OpenStreetMap",
        description = "The standard street map: roads, paths and buildings.",
        coverage = BasemapCoverage.WORLDWIDE,
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
        tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    ),
    ;

    companion object {
        /**
         * What the map opens on. Derives from [MapMode.DEFAULT] rather than naming a [Basemap]
         * directly, so there is exactly one place that decides the app's opening basemap — see
         * [MapMode]'s own doc comment for how the two types relate.
         */
        val DEFAULT get() = MapMode.DEFAULT.basemap
    }
}
