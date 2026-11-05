package com.zynergylabs.forager.app.data.dropoffmap

/**
 * This app's own operating limits on the drop-off map copy's fetch (dispatch 2026-11-04-09, B1 P5, accepted as proposed in
 * the Phase 1 report, section 3). CLAUDE.md: a capacity is not a safe operating limit. The server has no rate limit on tile
 * requests (its two limits are for route data only, `server/pmtiles-worker/wrangler.toml`), and MapLibre's own client
 * allows 20 requests per host (`map/MapHttpClient.kt`); neither number is used here. Each is a stated limit, not a measured one.
 */
internal object DropOffMapLimits {
    /** Requests in flight at once: a fifth of MapLibre's per-host 20, so the copy never crowds the map drawing beside it. */
    const val MAX_IN_FLIGHT = 4

    /** Requests started per second. The S22's 604 then take at least about a minute: a background pass, not a burst. */
    const val MAX_STARTS_PER_SECOND = 10

    /** The gap between two starts that [MAX_STARTS_PER_SECOND] gives. */
    const val MIN_START_GAP_MILLIS = 1_000L / MAX_STARTS_PER_SECOND

    /**
     * Answers in a row that are neither a tile (200) nor an empty tile (204) before the pass stops: a server that is down,
     * refusing or rate limiting answers every request so. A connection failure stops the pass at once.
     */
    const val MAX_SERVER_ERRORS_IN_A_ROW = 3

    /**
     * Zoom-15 tiles in one area's cover. The largest area the app allows (24 km, `OfflineMapRepository.MAX_RADIUS_KM`)
     * covers at most 3,844 at 49.6°N (the Phase 1 report's sweep), so a cover over this is a bad row: refused, logged.
     */
    const val MAX_AREA_TILES = 4_000L

    /**
     * Bytes of one tile's body as read, 512 KiB: OkHttp has already unwrapped the edge's gzip, so this bounds the tile
     * itself and not only what crossed the network. The largest zoom-15 tile measured is 62 KB unwrapped (the Phase 1
     * report, section 3). Over it, the tile is not stored, and the pass goes on.
     */
    const val MAX_TILE_BYTES = 512L * 1024
}
