package com.zynergylabs.forager.app.domain.mapdata

/**
 * This app's own operating limits on the Offline Maps picker's detail check (known bug 14, dispatch 2026-11-02-02; the
 * Phase 1b report's section 3, accepted by the planner in RECORD -2150, with the tile cap recomputed for zoom 11 as Amendment 3,
 * A8, asks). CLAUDE.md: a capacity is not a safe operating limit. The server has no rate limit on tile requests, and MapLibre's
 * own client allows 20 requests per host (`map/MapHttpClient.kt`); neither number is used here. Each is a stated limit, not a
 * measured one. In `domain/` rather than beside the client because the screen's side reads two of them (the wait and the
 * time-out) and nothing under `ui/` imports from `data/`.
 */
object TrailMapDetailLimits {
    /** After the pin settles or the radius moves, the wait before asking: a second pan or slider step within it replaces the first. */
    const val SETTLE_DEBOUNCE_MILLIS = 750L

    /**
     * Zoom-11 tiles in one area's cover; over it, the check is not run, and that is logged (A8). **36, and why:** the largest
     * area the slider allows (24 km, `OfflineMapRepository.MAX_RADIUS_KM`) covers at most 36 zoom-11 tiles at any latitude its
     * download is allowed at. The download is held to the tile budget (`OfflineMapRepository.TILE_COUNT_LIMIT`, 6,000 on an
     * empty budget), which a 24 km area stops fitting at about 53.4 degrees of latitude; further from the equator the
     * budget refuses it. A smaller area the budget allows nearer the pole covers fewer: the Phase 2 sweep of every radius,
     * at every half degree and 36 placements each, found 30 at most. So the cap never stops the check for an area that can be
     * downloaded. For comparison: 16 at 16 degrees, 25 at 45 degrees, 81 at 70 degrees and 540 at 85 degrees for 24 km.
     * `TrailMapDetailCapGuardTest` re-derives these and fails when the budget or the radius ceiling moves without this.
     */
    const val MAX_AREA_TILES = 36L

    /** Requests in flight at once: a fifth of MapLibre's per-host 20, as the drop-off map copy's, so the picker's map tiles are not crowded. */
    const val MAX_IN_FLIGHT = 4

    /** The whole check, from its first request: then it is not run, logged, and its requests are cancelled, not left to finish. */
    const val CHECK_TIMEOUT_MILLIS = 8_000L

    /**
     * Bytes of one tile's body as read, 1 MiB: OkHttp has already unwrapped the edge's gzip, so this bounds the tile itself. The
     * largest measured was 410 KB at zoom 10 (Phase 1b, one sample); over it, the tile is unread.
     */
    const val MAX_TILE_BYTES = 1L * 1024 * 1024

    /**
     * Tile answers kept for this session (call C3, RECORD -2150), so a pan inside tiles already asked about asks nothing and the
     * line does not blink. Kept for the process only: a rebuilt map is read on the next start (the edge keeps answers a day).
     */
    const val MAX_CACHED_ANSWERS = 512
}
