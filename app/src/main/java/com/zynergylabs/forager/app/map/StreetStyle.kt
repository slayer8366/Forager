package com.zynergylabs.forager.app.map

/**
 * Forager's own Street map: a vector style served by the owner's mapping Worker, in place of OpenStreetMap's
 * raster tiles (dispatch 2026-10-18-05 Part 2; RECORD -1281, -1282, -1297, -1300, -1302).
 *
 * **The release switch.** [STREET_SERVED_BY_OWN_MAP] is the one place that decides which Street the app draws,
 * at compile time. The owner's ruling (-1282): Street uses the Cloudflare map "if it is ready and passes its phone
 * check before the release build"; otherwise Street ships on OpenStreetMap's raster tiles, and "nothing switches
 * while the app is in use". So nothing reads the network to decide it. Since RECORD -1513 there is one fallback at
 * run time: if the own Street's style fails to load, the map draws OpenStreetMap's Street until the app restarts
 * (`ui/map/OwnStreetFallback.kt`).
 *
 * It is `true` since RECORD -1511 (the owner: "Flip it now"), after the Worker was deployed and the phone review
 * passed. The -1300 (3) condition (Alaska and Hawaii covered) is the map archive's, not this file's. With it `false`
 * every function here gives exactly what the app did before this file existed: [offlineDownloadStyleUrl] is
 * [OFFLINE_STYLE_URL], [offlineRenderStyleUrl] is [OFFLINE_STYLE_URL], and no region is ever older than the current style.
 *
 * The functions take the switch as a parameter, defaulting to the constant, so a unit test can drive both
 * values; production callers pass nothing.
 */
internal const val STREET_SERVED_BY_OWN_MAP: Boolean = false // REVERT CHECK (never merged)

/**
 * The Street style document, served by the Worker at `/style/street-v1.json` (`server/pmtiles-worker`), built by
 * `map-style/generate-street.mjs`. Like [OFFLINE_STYLE_URL] it is a URL that never changes meaning: MapLibre's
 * offline store keys a downloaded region by the style URL, so a later design ships as `street-v2.json` beside it.
 */
internal const val STREET_STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v1.json"

/**
 * The always-visible credit over the own Street map: the same Protomaps tiles as the offline style, so the
 * same credit ([OFFLINE_STYLE_ATTRIBUTION]).
 */
internal const val STREET_STYLE_ATTRIBUTION = OFFLINE_STYLE_ATTRIBUTION

/**
 * The own Street map's maximum zoom (-1289, the owner: "Stop at 18"). The tiles carry detail to zoom 15; past that
 * the map enlarges what it has, which stays sharp because it is drawn, not scaled. An operating limit chosen by the
 * owner, not the tiles' reported range (CLAUDE.md).
 */
internal const val STREET_STYLE_MAX_ZOOM = 18

/** The style a new offline download is built against: the own Street style with the switch on, [OFFLINE_STYLE_URL] otherwise. */
internal fun offlineDownloadStyleUrl(ownStreet: Boolean = STREET_SERVED_BY_OWN_MAP): String =
    if (ownStreet) STREET_STYLE_URL else OFFLINE_STYLE_URL

/**
 * The style the map loads when "use offline tiles" is on, from the style URLs the downloaded regions were built
 * against ([downloadedStyleUrls], read from MapLibre's own region definitions).
 *
 * The store serves a style only under the URL a region stored it with, so the map can only load a style some region
 * holds. With the switch on and at least one region in the Street style, that is the Street style: the tiles every
 * region holds are the same tile URLs under either style, and the fonts and icons a Street download stores are shared
 * by URL, so the older regions draw in it too. With no Street region yet, it is [OFFLINE_STYLE_URL], the style every
 * older region holds, so they keep drawing exactly as they did (-1294). With the switch off it is always
 * [OFFLINE_STYLE_URL].
 */
internal fun offlineRenderStyleUrl(downloadedStyleUrls: Collection<String?>, ownStreet: Boolean = STREET_SERVED_BY_OWN_MAP): String =
    if (ownStreet && STREET_STYLE_URL in downloadedStyleUrls) STREET_STYLE_URL else OFFLINE_STYLE_URL

/**
 * Whether a region's row shows the -1294 notice ([OLDER_MAP_STYLE_NOTICE]): a downloaded region built against a
 * style other than the one a download would use now. A region whose style URL is unknown (`null`: not downloaded on
 * this phone, or a definition that names no URL) never shows it: the notice is a fact about the region, never a
 * guess. With the switch off, every region was built against today's download style, so none shows it.
 */
internal fun regionHasOlderMapStyle(
    regionStyleUrl: String?,
    isDownloaded: Boolean,
    ownStreet: Boolean = STREET_SERVED_BY_OWN_MAP,
): Boolean = isDownloaded && regionStyleUrl != null && regionStyleUrl != offlineDownloadStyleUrl(ownStreet)

/**
 * The one-line notice on an older region (-1294; the wording offered in the Part 1 report, question 5, and accepted
 * with the owner's -1300). No re-download is forced or started on its own.
 */
internal const val OLDER_MAP_STYLE_NOTICE = "Older map style. Download again to update."

/**
 * The sprite's icon names, which the app inverts one by one at night (MapLibre cannot list a style's images).
 * The same list the style records as `metadata["forager:sprite-icons"]`; `StreetStyleTest` checks the two agree.
 */
internal val STREET_SPRITE_ICON_NAMES: List<String> = listOf(
    "NL:S-road-1char", "NL:S-road-2char", "NL:S-road-3char", "NL:S-road-4char", "NL:S-road-5char",
    "US:I-1char", "US:I-2char", "US:I-3char", "US:I-4char", "US:I-5char",
    "aerodrome", "animal", "arrow", "artwork", "attraction", "bar", "beach", "beauty", "bench", "books", "building",
    "bus_stop", "cafe", "capital", "clothes", "convenience", "drinking_water", "electronics", "fast_food",
    "ferry_terminal", "forest", "garden",
    "generic_shield-1char", "generic_shield-2char", "generic_shield-3char", "generic_shield-4char", "generic_shield-5char",
    "library", "marina", "museum", "park", "peak", "post_office", "restaurant", "school", "stadium", "supermarket",
    "theatre", "toilets", "townspot", "train_station", "university", "zoo",
)
