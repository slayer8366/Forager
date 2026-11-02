# Our map at OSM's zoom levels: the comparison (dispatch 2026-11-02-03, step 1, read only)

Juniper, 2026-11-02, started 14:25 PST. Dispatch: `prompts/preserved/2026-11-02-03-osm-zoom-levels.md` on
`origin/records-after-173` (sha256 begins `9f8cfa6809d7`, read). Draft: sections fill in as they are read.

**Bases (read):** origin/main `8d0eefdbc` (2026-11-01 21:45 PST) and origin/pre-main `04746d8f0` (2026-11-02
10:58 PST), both exactly the dispatch's bases. Branch `osm-zoom-levels` cut from origin/records-after-173 `8900e8843`.

**What counts (the owner, to Aspen, 2026-11-02 between 14:06 and 14:13, relayed by Aspen verbatim):** "The style
to match is the zoom levels that objects, icons, names, etc begin appearing on the map when zooming in". So the
figure compared is the first zoom at which each thing appears. Colours, look and hill shading are out.

## A. What "OSM" and "our map" are

### A1. "OSM" is openstreetmap-carto v6.1.0

- **The app's map named "OpenStreetMap"** is `Basemap.OSM_STANDARD`, label `"OpenStreetMap"`, tiles
  `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, maxZoom 19
  (`app/src/main/java/com/zynergylabs/forager/app/ui/map/Basemap.kt:169-174`, pre-main `04746d8f0`, read).
- **Those tiles are drawn with openstreetmap-carto v6.1.0.** OSMF's server configuration pins
  `:revision => "v6.1.0"` for the default style at `roles/tile.rb:104` (openstreetmap/chef `049ef98`,
  2026-11-02 11:11 UTC, read), set by chef commit `e2b54e4` "Update to osm-carto v6.1.0", 2026-09-11 (read).
- **The tag read:** openstreetmap-carto `v6.1.0`, commit `0e52579` (2026-09-10), a released tag (read). It is
  also the newest tag: `v6.0.0` and `v6.1.0` follow `v5.9.0` (`git ls-remote --tags`, 14:26 PST, read).
- **A premise that was wrong:** Aspen's note gave `v5.9.0`. That is the newest `v5` tag, but the tile servers
  left 5.x on 2026-04-12 (chef `0c78316` "Drop support for carto 5.x and default to flex output", read) and
  run v6.1.0.

### A1b. How the two maps' zoom numbers line up in the app (read; this moves every row)

- **OSM's map in the app is a 256 px raster source** (`BasemapStyles.kt:203-219`, `"tileSize": 256` at :210,
  pre-main, read).
- **MapLibre Native 13.5.0** (the app's pin, `gradle/libs.versions.toml:43`, read) picks the tile level for a
  camera zoom with `coveringZoomLevel`: `zoom += log2(512 / tileSize)`, then `round` for raster sources and
  `floor` for vector ones (maplibre-native `android-v13.5.0`, `src/mbgl/util/tile_cover.cpp:149-156`;
  `tileSize_D = 512` at `include/mbgl/util/constants.hpp:13`, read). The app already relies on this
  (`RasterShaderModel.kt:48-55`, read).
- **So carto's tile level N first shows in the app at camera zoom N − 1.5** (computed: round(z + 1) = N from
  z = N − 1.5). It is at its native pixel scale at N − 1.
- **Our map is vector, and its source sets no tileSize,** so it takes the style spec's default of 512 (read:
  `street-style-v4.json` `sources.protomaps` has only `type` and `url`). Tile level z draws from camera zoom z
  (`floor`), and a layer's `minzoom` is a camera zoom.
- **What this means for the comparison:** "OSM's first zoom" below is carto's number N, the tile level.
  The column "in the app" is N − 1.5, the camera zoom at which the owner first sees it on the OpenStreetMap
  map. To appear at the same camera zoom on ours, a layer needs `minzoom` N − 1.5 and its features in our tiles
  from level N − 2 (computed). Matching at N − 1 instead, where OSM's tile is at full scale, needs tiles from
  N − 1. Which of the two to match is a choice for Aspen. It is not made here; see "Decided beyond scope".

### A2. "Our map": the styles the app draws as its own

All five are served by the Worker from `server/pmtiles-worker/src/`, and are byte for byte the same on main
`8d0eefdbc` and pre-main `04746d8f0` (blob hashes compared, read).

| Style | Served at | When the app loads it (pre-main) | Layers | Zooms vs v4 |
|---|---|---|---|---|
| **Street v4** `street-style-v4.json` | `/style/street-v4.json` (`index.ts:502`) | **In use.** Trail Map online (`BasemapStyles.kt:268`), Topographical at night below 9.7 (`:269`), every new download (`StreetStyle.kt:65`) | 104 | — |
| Street v3 `street-style-v3.json` | `/style/street-v3.json` (`index.ts:499`) | Only with "use offline tiles" on and no v4 region held (`StreetStyle.kt:83-84`) | 97 | Same min/max/filter on all 97 (computed). Lacks 7 of v4's layers: `landuse_settled`, `landuse_orchards`, `landuse_orchards_pattern`, `landuse_parks_fill`, `hillshade`, `landuse_edges`, `water_edges` |
| Street v2 `street-style-v2.json` | `/style/street-v2.json` (`index.ts:495`) | As v3, with no v4 or v3 region | 93 | Same on all 93. Also lacks `buildings_outline` and the three `reference_*` layers |
| Street v1 `street-style-v1.json` | `/style/street-v1.json` (`index.ts:491`) | As v3, with only v1 regions | 90 | Same except: `water_stream` minzoom **14** (v4: 13), `landuse_park` without wetland and bare_rock, `pois` without saddle, shelter and huts. Also lacks the forest glyphs, wetland tufts and `pois_area_names` |
| Offline `offline-style.json` | `/style/offline.json` (`index.ts:487`) | Only with "use offline tiles" on and no Street region at all (`StreetStyle.kt:83-84`, `OfflineStyle.kt:18`) | 57 | **No symbol layers at all** (glyph-stripped, `OfflineStyle.kt:12-15`): no names, shields or POIs at any zoom. `water_stream` minzoom 14. No patterns and no paths split by kind |

- **The night handling changes no zoom on the Street styles:** it recolours and swaps images
  (`NightImageApply.kt`, `OfflineNightRecolourApply.kt`: no zoom read in either, grep, read).
- **The one exception is Topographical at night:** Street v4 is drawn under the topo raster, and every Street
  symbol layer gets `maxzoom` 9.5 (`StreetStyleApply.kt:54-58`, called at `MapStyleReload.kt:155`, value
  `BasemapStyles.kt:250`, read). See C2.
- **Not ours, so not compared:** OpenTopoMap (a raster, `Basemap.kt:152-166`) and the OSM raster itself.
- **"The entire map":** I compare Street v4 layer by layer (B1). v3, v2 and v1 share v4's zooms on every layer
  they have, so v4's rows cover them, with the three v1 differences above. The offline style's 57 layers are
  a subset of v4's, with the same zooms apart from `water_stream`. Older regions keep their stored style until
  they are downloaded again (`StreetStyle.kt:26-43`, the -1294 notice, read). A fix to v4 reaches them only
  through a new download (inferred from `StreetStyle.kt:72-84`).
- **Where v4's zooms are written:** v4 is generated from `@protomaps/basemaps` 5.7.2's `layers()`, plus four
  override files (style metadata `forager:generated-from`, read). Two of them, `forager-fill-in.mjs` and
  `forager-reference.mjs`, are on pre-main only, not main (`git ls-tree`, read). A layer with no override
  keeps Protomaps' own minzoom (for example `roads_oneway`, `address_label`, `roads_taxiway`; no override names
  them, grep, read).

## B. The comparison

(Filling in.)

## C. What the change would need

### C1. Style edit or new map data, per difference

(Filling in, with B1.)

### C2. What else in the app reads these zooms (pre-main `04746d8f0`, read unless marked)

| What | Where | What it does with zoom | Effect on a zoom fix |
|---|---|---|---|
| **Offline downloads' stored zooms** | `OfflineMapRepository.kt:159-160` (`MIN_ZOOM` 10.0, `MAX_ZOOM` 15.0), `:195` (`SERVED_MAX_ZOOM` 15.0); used at `MapLibreOfflineMapRepository.kt:187-188, 202-203` | A region stores tiles z10 to z15 only (known bug 14, `docs/process/known-bugs.md:38`) | Offline, nothing draws below camera 10 from downloaded tiles, whatever the style says (inferred). A fix that moves a feature below z10 shows online only. Changing `MIN_ZOOM` changes every download's size, through `EstimateOfflineTileCount.kt:46` and `OfflineTileBudget.kt` |
| **Offline style by URL** | `StreetStyle.kt:17-23, 72-84`; `OfflineStyle.kt:3-18` | A region keeps the style it was downloaded with, keyed by URL | A v4 edit in place reaches downloaded regions only when MapLibre refreshes the stored v4 document. Whether and when it does is not determined. `StreetStyle.kt:20-21` says "a later design ships as `street-v5.json` beside it". Whether a zoom fix counts as a later design is Aspen's call |
| **Tiles already downloaded** | `OfflineMapRepository.kt:159-160` | — | A data fix (new tiles) reaches a downloaded region only when it is downloaded again (inferred). The older-style notice keys on the style URL, not the tiles (`StreetStyle.kt:92-95`), so a tile-only rebuild shows no notice |
| **Topographical at night** | `BasemapStyles.kt:85-86` (`NIGHT_FADE_START_ZOOM` 9.5, `_END_` 9.7), `:269`; `StreetStyleApply.kt:20-43` (the topo layer from 9.5), `:54-58` (every Street `SymbolLayer`'s `maxZoom` set to 9.5) | Street v4 shows below 9.7, and its labels stop at 9.5 | A label whose fixed `minzoom` is 9.5 or more never shows under topo night (inferred). The call overwrites any `maxzoom` a fix gives a symbol layer. No v4 symbol layer has one today (computed) |
| **Camera ceiling** | `StreetStyle.kt:59` (`STREET_STYLE_MAX_ZOOM` 18), `Basemap.kt:172` (OSM 19), applied at `MapStyleReload.kt:120` | Trail Map stops at 18, OSM at 19 | Anything OSM first shows at tile level 20 or above (camera 18.5 or more) can't be matched on Trail Map (computed) |
| **Pattern relayout** | `PatternRelayout.kt:209-214`, `PatternRelayoutApply.kt:39-45` | Picks every fill layer that has a `fill-pattern`, whatever its zoom | A changed `minzoom` on the glyph, tuft or orchard layers needs no change here (read) |
| **Night icon and pattern swap** | `NightImageApply.kt:93-97`; `OfflineNightRecolourApply.kt:89-107` | Swaps by name or colour; no zoom read (grep, read) | None (inferred) |
| **Reference-trails overlay** | `street-style-v4.json:211-217` (source `reference`, minzoom 10, maxzoom 15); layers `reference_fs_band` and `reference_blm_band` minzoom 12, `reference_fs_number` 14; `ReferenceTrailsVisibility.kt:46` toggles visibility only | Forager-only data, no OSM counterpart (B2) | A minzoom below 10 would draw nothing: the source starts at 10 (inferred) |
| **The app's own overlays** | `ForecastCellLayer.kt:30` (`MIN_FORECAST_ZOOM` 7.0); `OverlayLineSpecs.kt:66-73` (track widths by zoom) | The app's own layers, drawn over the basemap | None; not part of the basemap (inferred) |
| **The Worker** | `index.ts:574-594` (the Street archive advertised to `OVERFLOW_MAX_ZOOM`, z16+ served from z15) | Tiles past z15 are z15's, overzoomed | A style `minzoom` above 15 works on data our z15 tiles hold (inferred) |

## Disclosures

### Confirmed vs inferred

(Filling in.)

### Could not determine

(Filling in.)

### Premises that were wrong

- `v5.9.0` as OSM's style: the tile servers run `v6.1.0` (A1).

### Decided beyond scope

(Filling in.)
