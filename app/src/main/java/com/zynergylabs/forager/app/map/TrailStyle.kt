package com.zynergylabs.forager.app.map

/**
 * Forager Trail Map: the app's own vector map, served by the owner's mapping Worker (dispatch 2026-10-18-05 Part 2;
 * RECORD -1281, -1297, -1300, -1302), and since dispatch 2026-10-22-01 (RECORD -1554, -1556) one of three map types,
 * the default, beside OpenStreetMap's raster map and Topographical. Earlier this file's identifiers called it "Street".
 *
 * **The release switch is gone.** `STREET_SERVED_BY_OWN_MAP` decided at compile time whether "Street" was this map or
 * OpenStreetMap's raster. With both always offered there is nothing left for it to decide, so it was removed rather
 * than left `true` (#359, which flipped it, is superseded). What it decided at run time is now the -1513 fallback
 * alone (`OwnTrailFallback`): Trail Map draws unless its style failed to load in this process.
 *
 * Offline downloads are Trail Map only (-1554): no OpenStreetMap tile is ever stored, its tile policy forbids it.
 */

/**
 * The Trail Map style document the app loads and downloads in: Trail Map v4, v3 with the fill-in, sprite v6's orchard
 * patterns and the hill shading (the switch is dispatch 2026-11-01-coder-11 step (d); the owner: "Yes, queue it"),
 * served by the Worker at `/style/trail-v4.json` (`server/pmtiles-worker`), built by `map-style/generate-trail.mjs`: v4
 * under the Trail Map's own code name since dispatch 2026-11-03-05 (D1, RECORD -2000; the owner: "Add trail-v4 now"),
 * the same style as `street-v4.json` ([TRAIL_STYLE_V4_STREET_URL]). Like [OFFLINE_STYLE_URL] it is a URL that never
 * changes meaning: MapLibre's offline store keys a downloaded region by the style URL, so a later design ships as
 * `trail-v5.json` beside it.
 */
internal const val TRAIL_STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/trail-v4.json"

/**
 * Trail Map v4 under its old address, `street-v4.json`, what the app loaded from dispatch 2026-11-01-coder-11 step (d)
 * until dispatch 2026-11-03-05 gave our map its own code name (D1, RECORD -2000; the owner: "Add trail-v4 now"). The same
 * style as [TRAIL_STYLE_URL]: the Worker's tests hold trail-v4 to be street-v4 with only its `name` and
 * forager:generated-from changed. So a region downloaded against it is today's style, shows no "Older map style" notice
 * ([isTodaysTrailStyle]; the planner's call, "Don't make me think"), takes v4's night tables and still draws offline.
 * Still served, frozen (D2).
 */
internal const val TRAIL_STYLE_V4_STREET_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v4.json"

/**
 * Trail Map v3, v2 with the official-trails band (step 8a, dispatch 2026-10-26-14; switched to in dispatch 2026-10-26-21):
 * what the app loaded before v4. Still served, frozen, for the regions downloaded against it, which keep drawing in it
 * and show the -1294 notice, as v2's did.
 */
internal const val TRAIL_STYLE_V3_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v3.json"

/**
 * Trail Map with its character, before the band (dispatch 2026-10-22-03, RECORD -1561, the owner: "Yes, v2"): what the
 * app loaded before the switch. Still served, frozen, for the regions downloaded against it, which keep drawing in it
 * and show the -1294 notice (dispatch 2026-10-26-21: "v2 regions keep v2 and show the existing older-style notice").
 */
internal const val TRAIL_STYLE_V2_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v2.json"

/**
 * Trail Map's first style, before its character: still served, unchanged, for apps older than v2 and for the regions
 * downloaded against it, which keep drawing in it and show the -1294 notice ("The existing notice", RECORD -1561).
 */
internal const val TRAIL_STYLE_V1_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v1.json"

/** Every Trail Map style the app may load: the current one first, then the older, newest first. All take the Trail night. */
internal val TRAIL_STYLE_URLS: List<String> =
    listOf(TRAIL_STYLE_URL, TRAIL_STYLE_V4_STREET_URL, TRAIL_STYLE_V3_URL, TRAIL_STYLE_V2_URL, TRAIL_STYLE_V1_URL)

/**
 * Whether [styleUrl] is today's Trail Map style: trail-v4, or the same style under its old address, street-v4 (dispatch
 * 2026-11-03-05 D1). The older-style notice reads this, so a region downloaded against street-v4 never shows it.
 */
internal fun isTodaysTrailStyle(styleUrl: String?): Boolean = styleUrl == TRAIL_STYLE_URL || styleUrl == TRAIL_STYLE_V4_STREET_URL

/**
 * The always-visible credit over Trail Map: the same Protomaps tiles as the offline style, so the
 * same credit ([OFFLINE_STYLE_ATTRIBUTION]).
 */
internal const val TRAIL_STYLE_ATTRIBUTION = OFFLINE_STYLE_ATTRIBUTION

/**
 * Trail Map's maximum zoom (-1289, the owner: "Stop at 18"). The tiles carry detail to zoom 15; past that
 * the map enlarges what it has, which stays sharp because it is drawn, not scaled. An operating limit chosen by the
 * owner, not the tiles' reported range (CLAUDE.md).
 */
internal const val TRAIL_STYLE_MAX_ZOOM = 18

/**
 * The style a new offline download is built against: Trail Map's, whatever map type is showing (RECORD -1554: "Offline
 * downloads → Trail Map only"). A function rather than the constant so the one rule has one name at its two readers.
 */
internal fun offlineDownloadStyleUrl(): String = TRAIL_STYLE_URL

/**
 * The style the map loads when "use offline tiles" is on, from the style URLs the downloaded regions were built
 * against ([downloadedStyleUrls], read from MapLibre's own region definitions). Also what Trail Map is drawn with for an
 * OpenStreetMap or Topographical user with no signal (`NoSignalTrailMap.kt`, RECORD -1556).
 *
 * The store serves a style only under the URL a region stored it with, so the map can only load a style some region
 * holds. The newest Trail Map style any region holds wins ([TRAIL_STYLE_URLS], v4, v3, v2, then v1): the tiles every region
 * holds are the same tile URLs under each style, and the fonts and icons a Trail Map download stores are shared by URL,
 * so the older regions draw in it too. With no Trail Map region at all, it is [OFFLINE_STYLE_URL], the style every older
 * region holds, so they keep drawing exactly as they did (-1294).
 *
 * v1 is a step of its own (dispatch 2026-10-22-03 Amendment 1): a phone holding only v1 regions has never stored v2,
 * so loading v2 or the offline style there would load a style no region holds and the offline map would not draw. v2 is
 * one for the same reason since the switch to v3 (dispatch 2026-10-26-21), and v3 since the switch to v4. With any v4
 * region, the older regions draw in v4 (the same basemap tile URLs); their areas hold no relief or band tiles of v4's.
 */
internal fun offlineRenderStyleUrl(downloadedStyleUrls: Collection<String?>): String =
    TRAIL_STYLE_URLS.firstOrNull { it in downloadedStyleUrls } ?: OFFLINE_STYLE_URL

/**
 * Whether a region's row shows the -1294 notice ([OLDER_MAP_STYLE_NOTICE]): a downloaded region built against a
 * style other than the one a download would use now. A region whose style URL is unknown (`null`: not downloaded on
 * this phone, or a definition that names no URL) never shows it: the notice is a fact about the region, never a
 * guess. Every region downloaded before Trail Map (in `offline.json`) shows it.
 */
internal fun regionHasOlderMapStyle(
    regionStyleUrl: String?,
    isDownloaded: Boolean,
): Boolean = isDownloaded && regionStyleUrl != null && regionStyleUrl != offlineDownloadStyleUrl() && !isTodaysTrailStyle(regionStyleUrl)

/**
 * The notice on an older region (-1294; first worded in the Part 1 report, question 5, accepted with the owner's -1300).
 * No re-download is forced or started on its own. Its words since dispatch 2026-11-04-09 (RECORD -2112) are the owner's
 * choice, "Look and hill shading (Recommended)": "It says exactly what improves, and promises nothing about the map's
 * data." Downloading again brings the area the newest style, which also draws the other areas offline, and stores its
 * hill shading; it reuses the stored map data and does not update it (dispatch 2026-11-04-08's report, section 2). The
 * words replaced "Older map style. Download again to update.": the download updates the style, not the map's data. When
 * the notice shows ([regionHasOlderMapStyle]) did not change. It may wrap to two lines in the row.
 */
internal const val OLDER_MAP_STYLE_NOTICE = "Older map style. Download again for the new look and hill shading."
