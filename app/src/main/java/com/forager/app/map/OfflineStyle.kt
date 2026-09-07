package com.forager.app.map

/**
 * The style every downloaded region is built against **and** the style a map loads to render from
 * the downloaded store — one constant, in one file, on purpose (Stage 2e-ii). MapLibre's offline
 * database keys the style document, its TileJSON and its tiles by URL; a map that loads any
 * *other* string — including a `fromJson` copy of this style's own content — asks the store for a
 * resource it never stored, and the miss looks identical to the store not working at all. See
 * `docs/audits/2026-09-07-offline-style-swap-prebuild-report.md` §1.5 for the three URL-level ways
 * the match can still fail even with one constant; none of them is visible from here.
 *
 * Served by the Cloudflare Worker in this repo at `/style/offline.json` — see
 * [MapLibreOfflineMapRepository]'s own doc comment for why it must be the glyph-stripped variant
 * (glyph layers crash the download natively, PR #23) and why it is a real HTTPS URL rather than
 * `asset://` (that hangs `OfflineTilePyramidRegionDefinition` at `completed=0/1`; recorded there so
 * the theory doesn't get re-tried).
 */
internal const val OFFLINE_STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"

/**
 * The always-visible credit the map shows over the offline style — `SightingsMap`'s own caption,
 * which otherwise reads `Basemap.attribution` for the online raster basemaps. The offline style's
 * one source declares `Protomaps © OpenStreetMap` as HTML links (`server/pmtiles-worker/src/offline-style.json`);
 * this is that credit as plain text, which is what the caption can draw. A licensing obligation,
 * not a preference (owner ruling, 2e-ii): a map drawing Protomaps geometry under an OpenTopoMap
 * credit would be crediting the wrong source.
 */
internal const val OFFLINE_STYLE_ATTRIBUTION = "Protomaps © OpenStreetMap"
