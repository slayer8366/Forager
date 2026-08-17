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
 * The basemaps the map can draw its tiles from, as this project's own type rather than osmdroid's.
 *
 * This is the [MapSlot] idea one level down: the choice a user makes is described here in pure
 * Kotlin — no osmdroid, no Compose, no Android — and [tileSourceFor] is the single place that turns
 * it into an `ITileSource`. So the selector UI, the coverage notes and the zoom ceilings are all
 * unit-testable headless (see `BasemapTest`), and the vendor type stays behind one function
 * (`BasemapTileSourceTest` checks that mapping against the real artifact).
 *
 * ## Why a selector at all, and why USGS is the default
 *
 * For deciding where to walk, terrain, forest cover and water beat roads and building footprints,
 * so USGS Topo is the default. But **USGS National Map covers the United States only**, so it
 * cannot simply replace OpenStreetMap as a hardcoded default: that would break the map outright for
 * any user outside the US. Two alternatives were rejected:
 *
 * - **Auto-detecting coverage and falling back to OpenStreetMap.** Blank-tile detection is
 *   unreliable, and CLAUDE.md forbids an unlogged silent fallback. Worse, the failure here is not
 *   even blank tiles: the service answers **HTTP 404** outside the US (measured — see
 *   `scripts/verify-usgs-basemap.sh`), which osmdroid reports as a missing tile indistinguishable
 *   from a network failure or a not-yet-loaded tile. A heuristic on top of that would be invented,
 *   not derived.
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
 * numbers are the explicit limit this app applies, and [SightingsMap] installs each one on the
 * `MapView` via `setMaxZoomLevel` so interactive zoom stops there instead of running into a wall of
 * missing tiles the user would read as the app being broken.
 *
 * The USGS figure is a case study in why the rule exists. Three sources disagree:
 *
 * - The service's own metadata (`MapServer?f=json`) advertises `tileInfo.lods` up to level **23**.
 * - Requesting tiles directly returns 200 with a JPEG body through **z16** and **404 from z17** —
 *   checked at four US locations (Portland OR, Manhattan, Denver, rural Montana), both USGS
 *   services. The advertised 23 is simply wrong.
 * - osmdroid's own `TileSourceFactory.USGS_TOPO` declares **15**.
 *
 * This app uses 15: it sits one level below the *observed* ceiling, and four sample points are not
 * enough to establish that 16 holds everywhere when the service's own metadata is already
 * demonstrably unreliable. Trusting the advertised 23 would have produced exactly the blank-tile
 * wall this field exists to prevent. `BasemapTileSourceTest` asserts each value still equals what
 * the pinned osmdroid artifact reports, so an osmdroid bump that moves a ceiling fails a test
 * rather than silently changing what the map does.
 *
 * ## [attribution] is not the on-map notice
 *
 * osmdroid's `CopyrightOverlay` draws `ITileSource.getCopyrightNotice()`, which is non-empty for
 * every basemap here (verified in `BasemapTileSourceTest`) — but for the USGS sources it is the
 * bare string `"USGS"`, which credits neither The National Map nor the public-domain status. These
 * fuller lines are shown in the selector so the credit is somewhere a user can actually read it.
 */
enum class Basemap(
    val label: String,
    val description: String,
    val coverage: BasemapCoverage,
    /** The highest zoom this app will let the user reach on this basemap. See the class doc. */
    val maxZoom: Int,
    val attribution: String,
) {
    USGS_TOPO(
        label = "USGS Topo",
        description = "Topographic: terrain contours, forest cover and water. Public domain.",
        coverage = BasemapCoverage.UNITED_STATES_ONLY,
        maxZoom = 15,
        attribution = "USGS The National Map — public domain",
    ),

    USGS_IMAGERY_TOPO(
        label = "USGS Imagery",
        description = "Aerial imagery with topographic labels over it. Public domain.",
        coverage = BasemapCoverage.UNITED_STATES_ONLY,
        maxZoom = 15,
        attribution = "USGS The National Map, orthoimagery — public domain",
    ),

    OPEN_TOPO_MAP(
        label = "OpenTopoMap",
        description = "Topographic contours and hillshading, worldwide.",
        coverage = BasemapCoverage.WORLDWIDE,
        maxZoom = 17,
        attribution = "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)",
    ),

    OSM_STANDARD(
        label = "OpenStreetMap",
        description = "The standard street map: roads, paths and buildings.",
        coverage = BasemapCoverage.WORLDWIDE,
        maxZoom = 19,
        attribution = "© OpenStreetMap contributors",
    ),
    ;

    companion object {
        /**
         * What the map opens on. USGS Topo, because terrain is the useful thing for choosing where
         * to walk — see this class's doc comment, including why this is a default the user can see
         * and change rather than a hardcoded source.
         */
        val DEFAULT = USGS_TOPO
    }
}
