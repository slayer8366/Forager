package com.zynergylabs.forager.app.ui.map

/**
 * The basemaps the map can draw its tiles from, as this project's own type rather than a vendor's.
 *
 * This is the [MapSlot] idea one level down: the choice a user makes is described here in pure
 * Kotlin — no MapLibre, no Compose, no Android — and [styleJsonFor] (in `BasemapStyles.kt`) is the
 * single place that turns it into a MapLibre `Style`. So the zoom ceilings and credits
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
 * only (`com.zynergylabs.forager.app.map.MapLibreOfflineMapRepository`), which is separately built, merged, and
 * hardware-verified — reusing it for live browsing too is a real product/infrastructure decision
 * (four times the request volume against a personal-account Cloudflare Worker, for one) that this
 * renderer-swap commit does not make silently. Tracked as follow-up work, not dropped.
 *
 * ## How a basemap actually gets chosen
 *
 * Not from this enum directly. [MapMode] names exactly two, Street and Topographical (both
 * OpenStreetMap-derived), picked from the Layers sheet's "Map type" row (`MapLayersSheet`) or the
 * map's own quick-fire `MapModePicker` (removed 2026-10-19, RECORD -1392). See [MapMode]'s own doc comment for the full picture,
 * including what this superseded (a two-tier service/mode split, and PR #13's original USGS Topo
 * default), and for how the choice persists across restarts.
 *
 * ## Satellite was removed (dispatch 2026-09-28-708)
 *
 * `USGS_IMAGERY_ONLY` (USGS The National Map's orthoimagery, shown as "Satellite") was deleted with
 * everything that existed only for it: its night-paint exemption in `BasemapStyles.kt`, its credit,
 * its chip, and the United-States-only coverage note it was the last user of. The owner: "I'm also
 * wondering about what would be lost if I removed satellite view from the maps and leave it at Topo
 * + street view. Satellite loses quality fast and is less useful when zoomed in", then "Remove now,
 * revisit later (Recommended)". **To be revisited once the forecast's habitat layers are on the
 * map.** Anyone who had Satellite chosen is moved to Topographical ("Topo (Recommended)"); see
 * [MapMode.forStoredKey]. Its service facts (real tiles through z16, 404 from z17 and outside the
 * US) are in git history and in `scripts/verify-usgs-basemap.sh`, for whoever revisits it.
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
 * The USGS figure is a case study in why the rule exists (USGS left the app with Satellite, see above;
 * the case is kept as the reason the rule is applied to the two that remain). Three sources disagree:
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
 * `attribution` field in the style JSON — `BasemapStyles.kt` sets it to [fullAttribution], which is
 * this same [attribution] string except where the full form would wrap the caption) but that is tap-to-reveal, not always-drawn the way `CopyrightOverlay` was. [SightingsMap]
 * therefore also renders [attribution] as a small always-visible Compose caption over the map,
 * so the always-drawn guarantee this app relied on for USGS/ODbL credit doesn't quietly become
 * tap-to-reveal-only as a side effect of the renderer swap.
 */
enum class Basemap(
    val label: String,
    val description: String,
    /** The highest zoom this app will let the user reach on this basemap. See the class doc. */
    val maxZoom: Int,
    val attribution: String,
    /**
     * The raw `{z}`/`{x}`/`{y}` tile URL template MapLibre substitutes into, per basemap.
     *
     * Both remaining basemaps use `{z}/{x}/{y}`. The removed USGS basemaps used ArcGIS's
     * `tile/{z}/{y}/{x}` — row before column — which is the failure mode worth naming for any
     * basemap added back: transposed coordinates return a perfectly valid tile from the wrong place
     * on Earth, so the map looks like it works. `BasemapStyleTest` pins the built URL for a known
     * tile.
     */
    internal val tileUrlTemplate: String,
    /**
     * The credit as its source asks for it, for MapLibre's tap-to-reveal attribution: `BasemapStyles.kt` writes it
     * into each style source's `attribution`, which is what that dialog lists. [attribution] is the always-visible
     * caption's line. They differ only where the full form would wrap the caption (dispatch 2026-10-18-04, item 3,
     * the owner's "option c"); otherwise this is [attribution].
     */
    val fullAttribution: String = attribution,
) {
    OPEN_TOPO_MAP(
        label = "OpenTopoMap",
        description = "Topographic contours and hillshading, worldwide.",
        maxZoom = 17,
        // The caption's line is shortened from the full credit below -- the full form wrapped to two
        // lines over the always-visible caption (see SightingsMap.kt), covering more of the map than
        // a credit line should. It still names all three required credits (OpenStreetMap, SRTM,
        // OpenTopoMap's CC-BY-SA), without the "contributors" qualifier and the " | " separator.
        attribution = "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",
        tileUrlTemplate = "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
        // OpenTopoMap's page asks for "Kartendaten: © OpenStreetMap-Mitwirkende, SRTM | Kartendarstellung:
        // © OpenTopoMap (CC-BY-SA)" (docs/audits/2026-10-18-map-services-policy-and-cost-research.md, Q2). This
        // English form goes in the tap-to-reveal attribution and the caption keeps the short line: the owner's
        // "option c" (dispatch 2026-10-18-04, item 3), chosen over a two-line caption or a smaller one.
        fullAttribution = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
    ),

    OSM_STANDARD(
        label = "OpenStreetMap",
        description = "The standard street map: roads, paths and buildings.",
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
