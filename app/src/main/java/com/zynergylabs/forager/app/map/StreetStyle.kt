package com.zynergylabs.forager.app.map

/**
 * Forager Trail Map: the app's own vector map, served by the owner's mapping Worker (dispatch 2026-10-18-05 Part 2;
 * RECORD -1281, -1297, -1300, -1302), and since dispatch 2026-10-22-01 (RECORD -1554, -1556) one of three map types,
 * the default, beside OpenStreetMap's raster map and Topographical. Earlier this file's identifiers called it "Street".
 *
 * **The release switch is gone.** `STREET_SERVED_BY_OWN_MAP` decided at compile time whether "Street" was this map or
 * OpenStreetMap's raster. With both always offered there is nothing left for it to decide, so it was removed rather
 * than left `true` (#359, which flipped it, is superseded). What it decided at run time is now the -1513 fallback
 * alone (`OwnStreetFallback`): Trail Map draws unless its style failed to load in this process.
 *
 * Offline downloads are Trail Map only (-1554): no OpenStreetMap tile is ever stored, its tile policy forbids it.
 */

/**
 * The Street style document the app loads and downloads in: Trail Map v3, which is v2 with the official-trails band
 * (step 8a, dispatch 2026-10-26-14; the switch is dispatch 2026-10-26-21, RECORD -1678: "switching is its own PR"),
 * served by the Worker at `/style/street-v3.json` (`server/pmtiles-worker`), built by `map-style/generate-street.mjs`.
 * Like [OFFLINE_STYLE_URL] it is a URL that never changes meaning: MapLibre's offline store keys a downloaded region by
 * the style URL, so a later design ships as `street-v4.json` beside it.
 */
internal const val STREET_STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v3.json"

/**
 * Trail Map with its character, before the band (dispatch 2026-10-22-03, RECORD -1561, the owner: "Yes, v2"): what the
 * app loaded before the switch. Still served, frozen, for the regions downloaded against it, which keep drawing in it
 * and show the -1294 notice (dispatch 2026-10-26-21: "v2 regions keep v2 and show the existing older-style notice").
 */
internal const val STREET_STYLE_V2_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v2.json"

/**
 * Trail Map's first style, before its character: still served, unchanged, for apps older than v2 and for the regions
 * downloaded against it, which keep drawing in it and show the -1294 notice ("The existing notice", RECORD -1561).
 */
internal const val STREET_STYLE_V1_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v1.json"

/** Every Trail Map style the app may load: the current one first, then the older, newest first. All take the Street night. */
internal val STREET_STYLE_URLS: List<String> = listOf(STREET_STYLE_URL, STREET_STYLE_V2_URL, STREET_STYLE_V1_URL)

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

/**
 * The style a new offline download is built against: Trail Map's, whatever map type is showing (RECORD -1554: "Offline
 * downloads → Trail Map only"). A function rather than the constant so the one rule has one name at its two readers.
 */
internal fun offlineDownloadStyleUrl(): String = STREET_STYLE_URL

/**
 * The style the map loads when "use offline tiles" is on, from the style URLs the downloaded regions were built
 * against ([downloadedStyleUrls], read from MapLibre's own region definitions). Also what Trail Map is drawn with for an
 * OpenStreetMap or Topographical user with no signal (`NoSignalTrailMap.kt`, RECORD -1556).
 *
 * The store serves a style only under the URL a region stored it with, so the map can only load a style some region
 * holds. The newest Trail Map style any region holds wins ([STREET_STYLE_URLS], v3, v2, then v1): the tiles every region
 * holds are the same tile URLs under each style, and the fonts and icons a Street download stores are shared by URL,
 * so the older regions draw in it too. With no Street region at all, it is [OFFLINE_STYLE_URL], the style every older
 * region holds, so they keep drawing exactly as they did (-1294).
 *
 * v1 is a step of its own (dispatch 2026-10-22-03 Amendment 1): a phone holding only v1 regions has never stored v2,
 * so loading v2 or the offline style there would load a style no region holds and the offline map would not draw. v2 is
 * one for the same reason since the switch to v3 (dispatch 2026-10-26-21). With any v3 region, v2 and v1 regions draw in
 * v3 (the same basemap tile URLs); their areas hold no band tiles.
 */
internal fun offlineRenderStyleUrl(downloadedStyleUrls: Collection<String?>): String =
    STREET_STYLE_URLS.firstOrNull { it in downloadedStyleUrls } ?: OFFLINE_STYLE_URL

/**
 * Whether a region's row shows the -1294 notice ([OLDER_MAP_STYLE_NOTICE]): a downloaded region built against a
 * style other than the one a download would use now. A region whose style URL is unknown (`null`: not downloaded on
 * this phone, or a definition that names no URL) never shows it: the notice is a fact about the region, never a
 * guess. Every region downloaded before Trail Map (in `offline.json`) shows it.
 */
internal fun regionHasOlderMapStyle(
    regionStyleUrl: String?,
    isDownloaded: Boolean,
): Boolean = isDownloaded && regionStyleUrl != null && regionStyleUrl != offlineDownloadStyleUrl()

/**
 * The one-line notice on an older region (-1294; the wording offered in the Part 1 report, question 5, and accepted
 * with the owner's -1300). No re-download is forced or started on its own.
 */
internal const val OLDER_MAP_STYLE_NOTICE = "Older map style. Download again to update."
