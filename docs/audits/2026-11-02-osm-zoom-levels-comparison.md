# Our map at OSM's zoom levels: the comparison (dispatch 2026-11-02-03, step 1, read only)

Juniper, 2026-11-02, started 14:25 PST (clock). Dispatch: `prompts/preserved/2026-11-02-03-osm-zoom-levels.md` on
`origin/records-after-173` (sha256 begins `9f8cfa6809d7`, read).

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
  N − 1.
- **Aspen's decision** (message to Juniper, received before 14:36:58 PST): "match first appearance, at camera
  zoom N − 1.5", because the owner's words are about where things "begin appearing". The owner can choose
  N − 1 instead when they see the renders. So in C1, "needs new data" is judged against tile level N − 2,
  which is floor(N − 1.5). Not my decision; recorded as Aspen's.

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

### How to read the table

- **OSM N:** the first zoom at which carto v6.1.0 draws the thing. It's carto's tile level, with its rule and
  source.
  - `lc` = `style/landcover.mss`, `rd` = `style/roads.mss`, `wa` = `style/water.mss`,
    `ap` = `style/amenity-points.mss`, `pn` = `style/placenames.mss`, `ad` = `style/admin.mss`,
    `bu` = `style/buildings.mss`, `mml` = `project.mml`.
  - All at tag v6.1.0 (`0e52579`).
- **OSM in app:** N − 1.5, the camera zoom at which it first shows on the app's OpenStreetMap map (A1b).
- **Our style:** the Street v4 layer, its `minzoom` and any zoom filter (`v4:` = line in
  `server/pmtiles-worker/src/street-style-v4.json`, main `8d0eefdbc`).
  - A layer with no `minzoom` draws from whatever zoom the tiles first hold.
  - Where a `line-width` of 0 at the lowest stop hides a layer, the visible start is given.
- **Our tiles:** the first tile level that holds the thing.
  - The source is the Protomaps profile at `ca93fc06` plus patches 0001-0007 (`R` = `layers/Roads.java`,
    `W` = `Water.java`, `L` = `Landuse.java`, `P` = `Pois.java`, `Pl` = `Places.java`, `B` = `Buildings.java`,
    `Bd` = `Boundaries.java`, `E` = `Earth.java`; line numbers at the patched tree).
  - It is checked against Willow's read-only decode of the live archive at Molalla (forager-cartography
    `3e1304b`, `docs/audits/2026-11-02-coder-13-osm-zooms/first_zooms.txt`; Willow's reading, not mine).
- **Ours in app:** the later of the style's and the tiles' first zoom.
- **Δ** = ours − OSM in app. Positive means ours arrives later.
- **Fix** (Aspen's rule, A1b): the target is camera N − 1.5, and the tiles must hold the thing at level N − 2.
  - **style:** the data is already in the tiles at N − 2, or ours is too early, so a `minzoom` or filter edit
    does it.
  - **data:** the tiles first hold it after level N − 2, so the profile changes and the map is rebuilt.
  - **style+data:** a style edit gets part of the way, and the rest needs data.
  - **none:** Δ is 0.
- Every row is read from the cited lines unless marked (c) computed or (i) inferred. The carto figures were
  gathered by three read-only helpers and the profile figures by a fourth. I checked about 60 of their cited
  lines against the two clones myself, and all matched (read).

### Size floors: a difference on every polygon, before any row (c)

- **On OSM,** carto keeps a polygon once it covers about 1 tile pixel² at its tile level.
  - From z10 that's `way_area > 1 px²` (for example `mml:139`, `mml:246`).
  - At z5-9, land cover keeps polygons over 0.01 px² (`mml:79`).
  - In the app a carto tile pixel is 0.7 to 1.4 screen pixels wide (from A1b).
- **On ours,** every land-use polygon needs 2 × 2 Planetiler pixels (`L:267-268`, `setMinPixelSize(2.0)`).
  Water polygons need 1 pixel (`W:363-364`). Below z15, polygons whose bounding box is under 1.5625 px² are
  dropped too (`L:332-348`).
  - At camera zoom C, our tile level is floor(C), and one Planetiler pixel (1/256 of a tile) is 2 to 4 screen
    pixels wide.
  - So a land-use polygon must be about 4 to 8 screen pixels across to be kept, where OSM keeps one about 1
    pixel across.
- **The effect:** small fields, orchards, woods, parks and lakes arrive about 2 to 3 camera zooms later on ours
  than on OSM. Water polygons arrive about 1 to 2 zooms later.
- **Fix: data.** `L:268` and the area filter at `L:332-348`, `W:364`. This may be part of "some parts are not
  coming in properly" (i); nothing here measures it on the screenshots.

### B1 and C1. Things both maps draw (Street v4; v3, v2 and v1 have the same zooms except where marked)

#### Area fills and patterns

| # | What | OSM N (rule, source) | OSM in app | Our style | Our tiles | Ours in app | Δ | Fix |
|---|---|---|---|---|---|---|---|---|
| 1 | Forest, wood fill | 5 (`lc:321-323`, `mml:77-79,86`) | 3.5 | `landuse_park` v4:462, no minzoom | 2, size floor (`L:267-268`); Molalla 2 | 2 for a big polygon | −1.5 (big ones); later for small (size floor) | style (minzoom 3.5 for the kind) + data (size floor) |
| 2 | **Tree glyphs on forest/wood** | **13** (`lc:776`) | **11.5** | `landuse_forest_glyphs` minzoom 12 (v4:586) | forest from 2 | 12 | **+0.5** | **style** (minzoom 11.5) |
| 3 | Scrub, grassland, grass, meadow, sand, bare_rock fills | 5 (`lc:506-510, 353-355, 494-498, 469-472`; `mml:71-72`) | 3.5 | `landuse_park` v4:462 | 2, size floor | 2 | −1.5 | style + data (size floor) |
| 4 | Heath fill | 5 (`lc:500-504`) | 3.5 | `landuse_park` lists heath | **never written** (no rule in `L:66-244`) | never | — | **data** (B2 below) |
| 5 | Farmland fill | 5 (`lc:340-343`) | 3.5 | `landuse_settled` v4:286, opacity 0.6 at z6 | 2, size floor; Molalla 3 | 2 | −1.5 | style + data (size floor) |
| 6 | Orchard, vineyard fill | 5 (`lc:140-145, 126-131`) | 3.5 | `landuse_orchards` v4:339 | kind farmland + kind_detail from 2 (patch 0006, `L:255-257,263,322-324`); Molalla orchard 6, vineyard 8 | 2 for a big polygon; 6 at Molalla | −1.5 by rule; +2.5 at Molalla | style + data (size floor) |
| 7 | Plant-nursery fill | 10 (`lc:189-194`, `mml:109`) | 8.5 | `landuse_orchards` | from 2 (0006); Molalla 8 | 2 | −6.5 for a big one; −0.5 at Molalla | style (8.5 for kind_detail plant_nursery) |
| 8 | **Orchard, vineyard, nursery patterns (the dots)** | **13** (`lc:146-147, 132-137, 195-200`) | **11.5** | `landuse_orchards_pattern` minzoom **13** (v4:391) | from 2 | 13 | **+1.5** | **style** (minzoom 11.5) |
| 9 | Wetland fill (natural=wetland with wetland=swamp, marsh, bog …) | 5 (`lc:512-552`) | 3.5 | `landuse_park` (wetland) | 2, size floor; no wetland=* detail (`L:123-135`) | 2 | −1.5 | style + data (size floor). A wetland with no wetland=* has no fill on OSM (`mml:73,116`, i) |
| 10 | **Wetland pattern** (generic) | **10** (`lc:715-716`) | **8.5** | `landuse_wetland_tufts` minzoom **10** (v4:610) | from 2 | 10 | **+1.5** | **style** (minzoom 8.5). OSM's z13 subtype patterns need wetland=* in the tiles (B2) |
| 11 | Residential, commercial, industrial fill | 8 (`lc:246-247, 434-444, 378-388`) | 6.5 | `landuse_settled`, `landuse_industrial` v4:689; no minzoom | 2; Molalla residential 6, commercial 8, industrial 6 | 2 (6 at Molalla) | −4.5 by rule; −0.5 at Molalla | style (6.5 by kind) |
| 12 | Railway land | 10 (`lc:411-415`) | 8.5 | `landuse_settled` | 2; Molalla 8 | 2 | earlier | style (8.5) |
| 13 | Cemetery, golf course, park, garden, dog park, pitch, landuse=recreation_ground, allotments, village green† | 10 (`mml:102-113`; `lc:203-209, 296-301, 264-270, 154-159, 282-287, 675-683, 89, 303-308`) | 8.5 | `landuse_park`, `landuse_parks_fill` v4:436, `landuse_urban_green` v4:658 | 2, size floor; Molalla cemetery 8, golf 8, park 2, garden 11, pitch 10 | 2 for a big one | −6.5 to −0.5 | style (8.5 by kind). †village green is z5 on OSM (`lc:356`) |
| 14 | Playground | 13 (`lc:90`) | 11.5 | `landuse_urban_green` | 2; Molalla 13 | 13 at Molalla | +1.5 at Molalla; earlier by rule | style (11.5) |
| 15 | Hospital, school, university, college | 10 (`lc:556-569`) | 8.5 | `landuse_hospital` v4:675, `landuse_school` v4:703 | 2; Molalla hospital 9, school 8, college 7 | 2 | earlier | style (8.5) |
| 16 | Beach | 10 (`lc:631-636`) | 8.5 | `landuse_beach` v4:719 | 2 | 2 | earlier | style (8.5) |
| 17 | Aerodrome | 10 (`lc:621-628`) | 8.5 | `landuse_aerodrome` v4:747 | 2; Molalla 6 | 2 | earlier | style (8.5) |
| 18 | Military | 8 if > 900 px, else 13 (`lc:788-790`) | 6.5 / 11.5 | `landuse_park` | 2, size floor | 2 | earlier | style (6.5) + data for the size rule (no area in our tiles, i) |
| 19 | Glacier | 5 (`wa:34-38`, `mml:239-246`) | 3.5 | `landuse_park` + `landcover` | landuse from 2; landcover (Daylight) 0-7 (`Landcover.java:54`) | 0 | −3.5 | style (3.5) |
| 20 | Runway, taxiway areas | 14 (layer `mml:803` overrides `rd:3422,3430`) | 12.5 | `landuse_runway` v4:822 | 2; Molalla runway 9 | 2 | earlier | style (12.5) |
| 21 | Pier area | 12 (`mml:386`) | 10.5 | `landuse_pier` v4:964 | 2 | 2 | earlier | style (10.5) |
| 22 | Dam area | 13 (`mml:340`) | 11.5 | `landuse_pedestrian` v4:949 | 2; Molalla 13 | 13 at Molalla | +1.5 at Molalla | style (11.5) |
| 23 | Pedestrian / footway areas | 15 (`rd:3362-3364`) | 13.5 | `landuse_pedestrian` | 2 | 2 | earlier | style (13.5) |
| 24 | Buildings, fill | 14 (`bu:13-14`) | 12.5 | `buildings` v4:1661, no minzoom | 11 (`B:96,113`); Molalla 11 | 11 | −1.5 | style (12.5) |
| 25 | Buildings, outline | 15 (`bu:17-20`) | 13.5 | `buildings_outline` minzoom 15 (v4:1705) | 11 | 15 | +1.5 | style (13.5) |

#### Outlines

| # | What | OSM N | OSM in app | Our style | Our tiles | Ours in app | Δ | Fix |
|---|---|---|---|---|---|---|---|---|
| 26 | National park, protected area, nature reserve border | 8 if > 750 px; never if smaller (`ad:495-497`, layer `mml:1214`) | 6.5 | `landuse_park_outline` minzoom 6 (v4:628) | park/national_park/nature_reserve from 2 (`L:198-243`); protected_area only as USFS forest (`L:192-197`) | 6 | −0.5 | style (6.5) + data for the size rule |
| 27 | Hospital, school, university, college outline | 13 (`lc:573-577`) | 11.5 | `landuse_edges` minzoom 13 (v4:982) | from 2 | 13 | +1.5 | style (11.5 for these kinds) |
| 28 | Residential, commercial, industrial, farmland, allotments outline | 16 (`lc:250-253, 438-441, 382-385, 344-347, 315-318`) | 14.5 | `landuse_edges`: 13, with residential, commercial and railway at opacity 0 until 14 (v4:978-1152) | from 2 | 14 (residential, commercial); 13 (farmland, industrial, allotments) | −0.5 / −1.5 | style (14.5) |
| 29 | Pitch, landuse=recreation_ground, playground outline | 15 (`lc:677-680, 93-96`) | 13.5 | `landuse_edges` 13 | from 2 | 13 | −0.5 | style (13.5) |

#### Water

| # | What | OSM N | OSM in app | Our style | Our tiles | Ours in app | Δ | Fix |
|---|---|---|---|---|---|---|---|---|
| 30 | Ocean | 0 (`mml:247-270`) | 0 | `water` v4:935 | 0 (`W:36-41, 290-330`) | 0 | 0 | none |
| 31 | Lakes, reservoirs, riverbank areas | 0 once > 1 px² (`mml:241,246`) | by size | `water` | Natural Earth lakes from 0 (per-feature `min_zoom` not read); OSM polygons from 6, 1 px floor (`W:363-364`) | by size, 1-2 later (size floors above) | +1 to +2 (c) | data (`W:363-364`) |
| 32 | **Rivers (lines)** | **8** (`wa:64-75`, `mml:183-187`) | **6.5** | `water_river` minzoom 9, width 0 at 9 (v4:908) | **9** (`W:104-108`); Molalla 9 | 9 | **+2.5** | **data** (river from 6), then style |
| 33 | **Streams (lines)** | **12**; intermittent 13 (`wa:223-228`, `mml:218`) | **10.5** / 11.5 | `water_stream` minzoom **13** (v4:876); **14 in v1 and offline** | **13** (`W:99-103`); Molalla 13 | 13 (v1 and offline 14) | **+2.5** (+3.5) | **data** (stream from 10), then style |
| 34 | Glacier outline | 5 (`wa:34-42`) | 3.5 | no outline layer | — | — | — | see B2 |

#### Roads and paths (lines)

| # | What | OSM N | OSM in app | Our style | Our tiles | Ours in app | Δ | Fix |
|---|---|---|---|---|---|---|---|---|
| 35 | Motorway, US Interstate | 6 (`rd:1419-1423`, `mml:849`) | 4.5 | `roads_highway` v4:5060, no minzoom | 3 (`R:307-311`); Molalla 3 | 3 | −1.5 | style (4.5) |
| 36 | Motorway, not Interstate (US) | 6 | 4.5 | `roads_highway` | 7, or 6 on a US route (`R:297-306`) | 6-7 | +1.5 to +2.5 | data |
| 37 | Trunk | 6 (`rd:1533-1537`) | 4.5 | `roads_major` v4:4727 | 6 (US: 7, or 6 on a US route; `R:254-255, 297-306`); Molalla 6 | 6-7 | +1.5 to +2.5 | data |
| 38 | Primary | 8 (`rd:1639-1643`) | 6.5 | `roads_major` | 7 (`R:259-260`); Molalla 6 | 7 | +0.5 | data (tile 6) |
| 39 | Secondary | 9 (`rd:1741-1742`) | 7.5 | `roads_major` | 9 (`R:264-266`); Molalla 6 | 9 | +1.5 | data (tile 7) |
| 40 | Tertiary | 10 (`rd:1840-1841`) | 8.5 | `roads_major`, width 0 at z8 for tertiary | 9 (`R:270-272`); Molalla 6 | 9 | +0.5 | data (tile 8) |
| 41 | Links (ramps) | 10 (`rd:1424, 1538, 1644, 1743`) | 8.5 | motorway links: only `roads_link` v4:4367 (width 0 at 13), since `roads_highway` excludes `is_link`; trunk to tertiary links: also `roads_major` v4:4727, which has no link filter | with their class (motorway_link 3, trunk_link 6, primary_link 7, others 9; `R:249-275`), but off-ramps are removed below 12 (`R:873-880`) | motorway links 13; other links at their class's zoom (off-ramps 12) | motorway links +4.5; others −2.5 to +0.5; off-ramps +3.5 | style (motorway links to 8.5) + data (keep off-ramps from 8) |
| 42 | Residential, unclassified | 12 (`rd:1939-1946`) | 10.5 | `roads_minor` v4:4440, from 10 | 10 (patch 0005, `R:414-421`); Molalla 10 | 10 | −0.5 | style (10.5) |
| 43 | highway=road | 10 (`rd:2062-2068`) | 8.5 | `roads_minor` | 10 (0005) | 10 | +1.5 | data (tile 8) |
| 44 | Living street | 13 (`rd:2004-2007`) | 11.5 | `roads_other` v4:2205 | 14, as kind `other` (`R:246, 382`); Molalla 14 | 14 | +2.5 | data |
| 45 | Service (alley, untagged …) | 14 (`rd:2115-2118`) | 12.5 | `roads_minor_service` v4:4396, width 0 at 13 | 13 (`R:279`); Molalla 13 | 13 | +0.5 | data (tile 12) |
| 46 | Service: driveway, parking aisle, drive-through | 16 (`rd:2119`) | 14.5 | `roads_minor_service` | 13; the service=* value isn't in the tiles | 13 | −1.5 | data (service=* value), then style |
| 47 | Pedestrian street | 14 (`rd:2205-2208`) | 12.5 | `roads_other` | 12 (`R:281`) | 12 | −0.5 | style (12.5) |
| 48 | Raceway | 12 (`rd:2254-2255`) | 10.5 | `roads_minor` | 12 (`R:278`) | 12 | +1.5 | data |
| 49 | Track | 13 (`rd:2515-2517`) | 11.5 | `roads_path_track` minzoom 12 (v4:2333) | 11 (patch 0001, `R:406-409`); Molalla 11 | 12 | +0.5 | style (11.5) |
| 50 | Path | 14 (`rd:2354-2357`) | 12.5 | `roads_path_*_z13` layers, minzoom 13 (v4:2389, 2652, 2760) | 12 (0001, `R:410-411`); Molalla 12 | 13 | +0.5 | style (12.5) |
| 51 | Footway, steps | 14 (`rd:2354-2357, 2314-2316`) | 12.5 | z13 layers; `roads_path_steps` 13 (v4:2598) | 13 (`R:282-283`); Molalla footway 13 | 13 | +0.5 | data (tile 12) |
| 52 | Cycleway, bridleway | 13 (`rd:2435-2437, 2332-2334`) | 11.5 | z13 layers | 13 | 13 | +1.5 | data (tile 11) |
| 53 | Sidewalk, crossing | 14 (as footway; `mml:713`) | 12.5 | `roads_other` (their kind_detail is not in the path layers' lists) | 14 (`R:82-87, 284`); Molalla 14 | 14 | +1.5 | data |
| 54 | Ways with access=private or no | as their class; access is drawn as a z15 overlay (`rd:3544-3557`) | as class | as class | **15** for every class (`R:400`) | 15 | up to +4.5 (residential) | data |
| 55 | Paths and tracks with access no | 15 (`rd:2357, 2517`) | 13.5 | as class | 15 | 15 | +1.5 | data |
| 56 | Rail (main line) | 8 (`rd:2613-2615`) | 6.5 | `roads_rail` v4:5127 | 11 (`R:96-101`); Molalla 11 | 11 | +4.5 | data |
| 57 | Light rail, narrow gauge, funicular | 8 (`rd:2690-2695`) | 6.5 | `roads_rail` | 14 (`R:111-124`) | 14 | +7.5 | data |
| 58 | Tram, subway, preserved rail | 12 (`rd:2773-2774, 2802-2803, 2660`) | 10.5 | `roads_rail` | 14 | 14 | +3.5 | data |
| 59 | Rail spur, siding, yard | 13 (`rd:2616`) | 11.5 | `roads_rail` | 13, or 14 with service=* (`R:102-110, 129-138`) | 13-14 | +1.5 to +2.5 | data |
| 60 | Bridges (major roads, distinct casing) | 13 (`rd:377-380, 411-414, 445-448, 481-486`) | 11.5 | `roads_bridges_*` minzoom 12 (v4:5238-5706) | `is_bridge` from 12 (`R:477-481`) | 12 | +0.5 | data (is_bridge from 11) |
| 61 | Bridges (tertiary, minor, paths) | 14 (`rd:516-521, 543-548, 701-705`) | 12.5 | minzoom 12 | is_bridge from 12 | 12 | −0.5 | style (12.5) |
| 62 | Tunnels (major roads, dashed) | 12 (`rd:374-376 …`) | 10.5 | `roads_tunnels_*` v4:1185-1610, no minzoom | `is_tunnel` from 12 (`R:477-481`) | 12 | +1.5 | data |
| 63 | Tunnels (residential, living street) | 13 (`rd:527, 540-542`) | 11.5 | as above | 12 | 12 | +0.5 | data (tile 11) |
| 64 | Runway lines | 11 (`rd:3694-3714`) | 9.5 | `roads_runway` v4:761, width 0 at z10 | 9 (`R:162-167`); Molalla 9 | just over 10 | +0.5 | style (width from 9.5) |
| 65 | Taxiway lines | 11 (`rd:3729-3748`) | 9.5 | `roads_taxiway` minzoom 13 (v4:795) | 10 (`R:156-161`); Molalla 10 | 13 | +3.5 | style to 10 (Δ +0.5) + data for 9.5 |
| 66 | Pier lines | 12 (`water-features.mss:85-97`) | 10.5 | `roads_pier` v4:1712, width 0 at 12 | 13, with a 2 px floor (`R:145-150, 538-541`) | 13 | +2.5 | data |
| 67 | Oneway arrows | 16 (`rd:4218-4240`) | 14.5 | `roads_oneway` minzoom 16 (v4:7020) | `oneway` from 14 (`R:429`) | 16 | +1.5 | style (14.5) |
| 68 | Country, admin 3, state boundaries | 4 (`ad:13-37, 131-139, 225-234`; layer `mml:1113`) | 2.5 | `boundaries_country`, `boundaries` v4:5162, 5198, no minzoom | Natural Earth 0-5, then OSM from 6 (`Bd:51-57, 184-201`); Molalla country 0, region 1 | 0 | −2.5 | style (2.5) |
| 69 | admin_level 5 | 8 (`ad:323-331`) | 6.5 | `boundaries` | 8 (`Bd:203-210`) | 8 | +1.5 | data |
| 70 | County (admin 6) | 10 (`ad:357-358`) | 8.5 | `boundaries` | 8; Molalla 8 | 8 | −0.5 | style (8.5) |
| 71 | admin_level 7 | 11 (`ad:381-391`) | 9.5 | `boundaries` | 10 (`Bd:211-218`) | 10 | +0.5 | data (tile 9) |
| 72 | City (admin 8) | 12 (`ad:403-413`) | 10.5 | `boundaries` | 10; Molalla 10 | 10 | −0.5 | style (10.5) |

#### Names, shields and labels

| # | What | OSM N | OSM in app | Our style | Our tiles | Ours in app | Δ | Fix |
|---|---|---|---|---|---|---|---|---|
| 73 | **Motorway names** | **13** (`rd:3956-3963`, layer `mml:2036`) | **11.5** | `roads_labels_major` minzoom 11 (v4:10755) | name from 11 (`R:249`) | 11 | −0.5 | style (11.5) |
| 74 | **Trunk, primary, secondary names** | **13** (`rd:3956-3963, 3992-3994`) | **11.5** | minzoom 11 | name from **12** (`R:254-266`); Molalla primary and secondary 12 | **12** | **+0.5** | **data** (names from 11) |
| 75 | **Tertiary names** | **14** (`rd:4019-4021`) | **12.5** | minzoom 11 | name from **13** (`R:270-272`); Molalla 13 | 13 | **+0.5** | **data** (names from 12) |
| 76 | **Residential, unclassified, road names** | **15** (`rd:4061-4068`) | **13.5** | `roads_labels_minor` minzoom 14 (v4:7041) | name from **14** (`R:246, 278`); Molalla 14 | 14 | **+0.5** | **data** (names from 13) |
| 77 | Living street, pedestrian names | 15 (`rd:4116-4120`) | 13.5 | `roads_labels_minor` | 14 | 14 | +0.5 | data |
| 78 | Track names | 15 (`rd:4159-4162`) | 13.5 | `roads_labels_minor` | 14 | 14 | +0.5 | data |
| 79 | Service names | 16 (`rd:4094-4098`) | 14.5 | `roads_labels_minor` | 14 | 14 | −0.5 | style (14.5 for service) |
| 80 | Path, footway, cycleway, bridleway, steps names | 16 (`rd:4186-4196`) | 14.5 | `roads_labels_minor` | 14 | 14 | −0.5 | style (14.5) |
| 81 | Motorway shields | 10 (`rd:3764-3765`, layer `mml:1927`) | 8.5 | `roads_shields` v4:10651, no minzoom | 7 (`R:249`; the US route rules at `R:297-311` set the line's zoom, not the shield's); Molalla 7 | 7 | −1.5 | style (8.5) |
| 82 | Trunk shields | 11 (`rd:3766`) | 9.5 | `roads_shields` | 8 (`R:254-255`); Molalla 8 | 8 | −1.5 | style (9.5) |
| 83 | Primary shields | 11 (`rd:3767`) | 9.5 | `roads_shields` | 10 (`R:259-260`); Molalla 10 | 10 | +0.5 | data (tile 9) |
| 84 | Secondary shields | 12 (`rd:3768`) | 10.5 | `roads_shields` | 11 | 11 | +0.5 | data |
| 85 | Tertiary shields | 13 (`rd:3801-3807`) | 11.5 | `roads_shields` | 12 | 12 | +0.5 | data |
| 86 | **River names** | **13** (`wa:393-394`, layer `mml:2309`) | **11.5** | `water_waterway_label` minzoom **13** (v4:6124) | names with the line from 9 (`W:384-386`); Molalla 9 | 13 | **+1.5** | **style** (11.5) |
| 87 | **Stream ("creek") names** | **15** (`wa:420-422`) | **13.5** | `water_waterway_label` minzoom 13 | names from 13 | 13 | **−0.5** | style (13.5 for streams). A creek tagged waterway=river follows row 86 (i) |
| 88 | Lake and reservoir names | 5 if > 3000 px; 17 any size (`wa:458-463`) | 3.5 / 15.5 | `water_label_lakes` v4:9742, no minzoom | label point at the first level 6-14 where area > 70,000 m² × 4^(15−z), else 15 (`W:403-435`) | — | **+1.5 for every lake** (c): the two thresholds are almost equal (7.35e13/4^N vs 7.5e13/4^z m²), so the tile level is the same and the app offset remains | data (labels 2 levels earlier). Ours shows small lakes earlier (15 vs 15.5) |
| 89 | Bay, strait, fjord names | 14 (`wa:492-494, 438-439`) | 12.5 | `water_label_ocean` v4:7937 | polygons by area from 6 (`W:403-435`); nodes 15 (`W:391`) | 6-15 | earlier for big polygons; +2.5 for nodes | style + data |
| 90 | Country names | 3 if > 1000 px; hidden once > 360,000 px (`pn:8-9`) | 1.5 | `places_country` v4:16860 | per places.csv (US 1-5, Canada 1-5; `Pl:414-419`); Molalla 1 | 1 | −0.5 | style (1.5) |
| 91 | State names | 5 if > 3000 px; hidden from 7 once > 196,000 px; along the border from 11 (`pn:52-53`, `ad:448-458`) | 3.5 (Oregon: 3.5-5.5, c) | `places_region` v4:14778; the abbreviation below 6 and the name from 6 | 3-7 per places.csv (Oregon 3-7) | 3 (as an abbreviation) | −0.5 | style (3.5); the abbreviation is B2 |
| 92 | Cities | 4-7 by population score (`mml:1330-1342`, `pn` city rules) | 2.5-5.5 | `places_locality` v4:15707 | 7, or 8 with no population; places.csv overrides (Portland 3) (`Pl:131-133, 420-423`) | 3-8 | from −0.5 (csv cities) to +2.5 (70k-400k with no csv) | data |
| 93 | Towns | 9 (`pn:288-289`) | 7.5 | `places_locality` | 7, or 9 with no population (`Pl:134-136`) | 7-9 | −0.5 / +1.5 | style (7.5) + data (no-population towns) |
| 94 | Villages | 12 (`pn:356-357`) | 10.5 | `places_locality` | 10, or 11 if population ≥ 2,000 (`Pl:137-139`) | 10-11 | −0.5 / +0.5 | style (10.5) + data (the ≥ 2,000 rule) |
| 95 | Hamlets | 14 (`pn:427-428`) | 12.5 | `places_locality` (filter on kind only) | 11, or 12 with no population (`Pl:129-130`; the hamlet rows `Pl:140-153` never match); Molalla 12 | 11-12 | −0.5 to −1.5 | style (12.5 by kind_detail) |
| 96 | Isolated dwelling, farm | 15 (`pn:462-463`) | 13.5 | `places_locality` | 11-12 | 11-12 | −1.5 to −2.5 | style (13.5) |
| 97 | Named place=locality | 16 (`ap:1650`) | 14.5 | `places_locality` | 11-12 | 11-12 | −2.5 | style (14.5) |
| 98 | Suburb | 12 (`pn:322`) | 10.5 | `places_subplace` v4:13835 | 12 (`Pl:157-158`) | 12 | +1.5 | data |
| 99 | Quarter | 14 (`pn:398-399`) | 12.5 | `places_subplace` | 10 (`Pl:155`); Molalla 10 | 10 | −2.5 | style (12.5) |
| 100 | Neighbourhood | 15 (`pn:461`) | 13.5 | `places_subplace` | 12 (`Pl:156`) | 12 | −1.5 | style (13.5) |
| 101 | Islands | 4 if > 3000 px; 16 any size (`ap:1625-1626`) | 2.5 / 14.5 | `earth_label_islands` v4:8848 | polygons from 6 with a 20 px floor; nodes not written (`E:68-75`) | 6+ | +3.5 for the largest; island nodes never | data |
| 102 | National park, protected area, nature reserve names | 8 if > 3000 px; 17 any size (`ap:2088-2095`) | 6.5 / 15.5 | `pois_area_names` minzoom 8, and zoom ≥ max(8, `min_zoom`) (v4:11650-11655) | named polygons by bounding-box area, 5-15; `min_zoom` = zoom + 1 (`P:297-366, 544`) | 8 at best | +1.5 for big ones (c) | style (drop the 8 floor and the +1) + data for the largest |
| 103 | Forest, wood names | 8 if > 3000 px; 17 (`ap:2088-2095`) | 6.5 | `pois_area_names`: "National/State Forest …" names from 8, other forest and park names from 11 | B_G table for forest (to 7), natural=wood generic table (11 at best) (`P:299-317`) | 8 / 11 | +1.5 (big National Forests); about +0.5 for a 5 km² forest (c) | style + data |
| 104 | Park names | 10 if > 3000 px; 17 (`ap:2181, 2228-2229`) | 8.5 | `pois_area_names` from 11 | B_G table | 11 | +2.5 for big parks | style (8.5) + data |
| 105 | Peak icon | 11 (`ap:1396`) | 9.5 | `pois` v4:12691, zoom ≥ 12 | 13; 11-14 by QRank (`P:242-244`) | 13 | **+3.5** | data |
| 106 | Peak name (with elevation) | 13 (`ap:1873`) | 11.5 | `pois` (icon and name together) | 13 | 13 | +1.5 | data |
| 107 | Alpine hut, wilderness hut icon | 13 (`ap:46-47`) | 11.5 | `pois`, ≥ 12 | 15 (`P:224`, point rows) | 15 | +3.5 | data |
| 108 | Saddle, mountain pass | 15 (`ap:1408, 1414`) | 13.5 | `pois` | 15 | 15 | +1.5 | data |
| 109 | Camp site, picnic site, viewpoint, shelter icons | 16 (`ap:233, 1278, 773, 48`) | 14.5 | `pois` | 15 (named areas by size, 11-15) | 15 | +0.5 | data (tile 14) |
| 110 | Parking icon | 14 if > 750 px; 17 any (`ap:1478-1484`) | 12.5 / 15.5 | `pois` | nodes 15; named areas by size (Molalla 14) | 14-15 | +1.5 (big lots) / −0.5 (nodes) | style + data |
| 111 | Drinking water | 17 (`ap:735`) | 15.5 | `pois` | 15 | 15 | −0.5 | style (15.5) |
| 112 | Toilets | 18 public, 19 restricted (`ap:723-725`) | 16.5 / 17.5 | `pois` | 15 | 15 | −1.5 | style (16.5) |
| 113 | Restaurant, fast food, cafe, bar | 17 as a dot; icon and name 18 (`ap:84-113, 1682-1687`) | 15.5 | `pois`, ≥ max(16, `min_zoom` 16) | 15 (`P:224`) | 16 | +0.5 | style (15.5) |
| 114 | Supermarket | icon 16 (`ap:868, 884-885`) | 14.5 | `pois`, ≥ max(16, 15) | node 14 (`P:228-236`) | 16 | +1.5 | style (14.5) |
| 115 | Convenience, books, clothes, beauty, electronics | 17 as a dot (`ap:867, 874-877`) | 15.5 | `pois`, ≥ max(16, `min_zoom` 17 or 16) | 15 (`P:253-271`) | 17 (16 for electronics) | +1.5 (+0.5) | style (15.5) |
| 116 | House numbers | 17 (`mml:2281`) | 15.5 | `address_label` minzoom **18** (v4:6096) | 15 (`B:135`) | 18 | +2.5 | style (15.5) |

**POI thinning (data, c):** below z15 our tiles keep one POI per 8 × 8 px cell (`P:565`). OSM thins only by
label collision. Crowded POIs can arrive later on ours than any row above says (i).

### B2. Drawn by one map and not the other (listed both ways, not decided)

**OSM draws, ours doesn't** (carto first zoom; whether our tiles hold it):
- **Heath fill** (5, `lc:500-504`): not in our tiles (`L:66-244`).
- **Canal lines** (12, `wa:311-328`) and canal names (13, `wa:407`): the tiles hold canals from 10 (`W:109-119`),
  but no v4 layer draws kind `canal` (v4 has only `water_river` and `water_stream`, read).
- **Ditch and drain lines** (12, `wa:223-228`) and their names (15): tiles from 15 (`W:89-98`), no v4 layer.
- **Wetland subtype patterns** (13, `lc:723-747`): our tiles carry no wetland=* value (`L:123-135`).
- **Other patterns,** all 13 unless noted (`lc:146-222, 309-314, 473-490, 752-772, 788-793, 288-293`):
  scrub, cemetery, allotments, garden, bare rock, scree, beach, military hatch, and dog park (16).
- **Glacier outline** (5) and **landuse names other than parks and forests** (wetland, meadow, residential …,
  10 if > 3000 px; `ap:2181-2229`), glacier, bare rock, sand and military names.
- **Railway names** (11 high-speed, 14 main lines, 17 others; `rd:4336-4394`); **junction refs** (12,
  `rd:3447-3450`); **ferry routes** (8, `ferry-routes.mss:4-5`; tiles hold ferries from 11, `R:151-155`, but no
  v4 layer draws them); **aerialways** (12, `aerialways.mss:2-42`; tiles hold cable cars only, `R:139-144`).
- **Barriers:** fences, walls and hedges (16; city walls 15) (`lc:840-863`). Our tiles hold barriers at z15
  for routing (`Barriers.java:33-67`), but no v4 layer draws them.
- **Cliffs** (13), **ridges and aretes** (14) (`lc:817-829`). Our tiles hold cliffs from 12 (`E:56-66`), but
  no v4 layer draws them.
- **Springs** (14), **waterfalls** (13-16), **cave entrances** (15), **trees** (16), **fuel** (17),
  **information boards** (19) (`ap:1420-1435, 1426, 3046, 329, 386`): not in v4's `pois` filter.
- **County names** (admin 5: 8, admin 6: 10; `pn:485-487`) and **boundary names along the line** (11+,
  `ad:448-458`). **Building names** (14 if > 3000 px, `addressing.mss:65-66`).
- **Islet names** and island nodes (`ap:1626-1628`): not in our tiles (`E:68-75`).
- **Bridge decks** (man_made=bridge, 12), **piers' breakwaters and groynes** (12), **quarry, brownfield,
  landfill, farmyard, greenhouse and other fills** (10): kinds our tiles fold together or don't write
  (`L:170-179`).

**Ours draws, OSM doesn't:**
- **The Daylight land cover** at z0-7 (`Landcover.java:44-58`; v4 `landcover` v4:256). OSM draws only OSM
  polygons.
- **A fill on national parks** (kind `park`, `L:198-201`, drawn by `landuse_parks_fill`). OSM draws only their
  border.
- **Outlines on forest, wood, scrub, grass, meadow, heath, park, cemetery, golf and wetland** (`landuse_edges`
  from 13), and **outlines on water** (`water_edges` from 13, v4:1157). OSM has none.
- **Trailheads and ranger stations** (`pois` filter; OSM never draws them, per the labels helper's grep).
- **Ocean and sea names** (`water_label_ocean`; OSM never draws marine names).
- **State abbreviations** below camera 6 (`places_region` text-field, v4:14778-). OSM never draws `ref`.
- **The reference trails' FS and BLM bands and numbers** (Forager's own data, z12 and z14).
- **Shields on link roads** (our filter takes any highway or major_road with a `shield_text`; OSM never draws
  shields on links, `mml:1910, 1954`). **Shield text** is limited to 5 characters on ours (v4:10651-) and to 11
  characters (four refs at most) on OSM (`mml:1913`).
- **Military naval bases and airfields** as fills (`L:165-169`); OSM draws only landuse=military.
- **Hill shading** (out of scope, per the owner).

### The owner's screenshots, as worked examples (12:30-12:32, Molalla and Liberal; their zooms are not known)

- **"Tree glyphs arrive later"** is row 2. OSM shows them from camera 11.5 and ours from 12. Style.
- **"Road names arrive later"** is rows 73-76.
  - Ours are half a zoom behind for trunk to residential, because our tiles carry names a level later.
  - Data.
- **"Orchard dots arrive later"** is row 8: 11.5 on OSM against 13 on ours. Style.
- **"Molalla River" arriving later** is row 86: 11.5 on OSM against 13 on ours. Style.
- **"… Creek" arriving later:**
  - On rule, ours shows stream names half a zoom earlier (row 87).
  - Unless that creek is tagged waterway=river (then row 86), or a stream line itself is late (row 33: +2.5).
  - Which one it is was not checked on the data (i).
- **"Forests are flat green farthest out"** fits rows 1-2: both maps draw a flat fill there, and the glyphs come
  in a zoom later.

## C. What the change would need

### C1. Style edit or new map data, per difference

- **Each B1 row's Fix column is the per-difference answer** (c, from Aspen's rule in A1b).
  - Of 116 rows, 114 differ. One matches (ocean, row 30); one moved to B2 (row 34).
  - **52 need a style edit only, 43 need new map data, and 19 need both** (counted from the table).
- **Style-only rows** are a `minzoom` or zoom-filter edit in the generators (A2: `forager-street.mjs`,
  `forager-character.mjs`, `forager-fill-in.mjs`, `forager-reference.mjs` on pre-main). Several rows split one
  layer by kind or kind_detail. Where a layer has no override, its zoom comes from `@protomaps/basemaps` 5.7.2,
  and an override would have to be added.

**What the data rows need in the profile** (target tile level N − 2; Forager's patches 0001 and 0005 already
lower zooms this way, at `R:402-421`):

| Area | Where (patched tree) | Today | For OSM's zoom | Rows |
|---|---|---|---|---|
| Polygon size floors | `L:267-268` (`setMinPixelSize(2.0)`), `L:332-348` (bbox filter below 15), `W:363-364` (1 px) | 2 px; 1.5625 px² bbox; 1 px | about 0.5 px (c: 1 screen px at camera C is ¼ to ½ a Planetiler px) | "Size floors", 1, 3, 5, 6, 9, 31 |
| River and stream lines | `W:99-108` | river 9, stream 13 | river 6, stream 10 (intermittent 11) | 32, 33 |
| Ditch and drain lines | `W:89-98` | 15 | 10 (and a v4 layer, B2) | B2 |
| Lake and water names | `W:413-418` (the loop over 6..14) | first level where area > 70,000 m² × 4^(15−z) | the same test two levels earlier, from 3 | 88 |
| Motorway (not Interstate), trunk | `R:249, 254-258, 297-306` | 6-7 | 4 | 36, 37 |
| Primary, secondary, tertiary | `R:259-275` | 7, 9, 9 | 6, 7, 8 | 38-40 |
| highway=road, raceway, living street, service | `R:278-279, 246, 382` | 10, 12, 14, 13 | 8, 10, 11, 12 | 43-45, 48 |
| Footway, steps; cycleway, bridleway; sidewalk, crossing | `R:282-284` | 13; 13; 14 | 12; 11; 12 | 51-53 |
| Ways with access=private or no | `R:400` (and the guards at `R:407, 418`) | 15 | their class's zoom | 54, 55 |
| Off-ramps | `R:873-880` (LinkSimplify below 12) | removed below 12 | kept from 8 | 41 |
| Road names | `R:246, 254-275` (`pm:minzoomName`) | trunk to secondary 12, tertiary 13, minor and paths 14 | 11, 12, 13 | 74-78 |
| Shields | `R:259-272` (`pm:minzoomShield`) | primary 10, secondary 11, tertiary 12 | 9, 10, 11 | 83-85 |
| `is_bridge`, `is_tunnel` | `R:477-481` | 12 | 10 (major tunnels), 11 (bridges) | 60, 62, 63 |
| Rail | `R:96-138` | rail 11; light rail etc. 14; tram, subway, preserved 14; spur 13-14; disused 15 | 6; 6; 10; 11; 13 | 56-59 |
| Pier lines | `R:145-150` | 13 | 10 | 66 |
| Taxiway lines | `R:156-161` | 10 | 9 | 65 |
| Boundaries admin 5, admin 7 | `Bd:203-218` | 8, 10 | 6, 9 | 69, 71 |
| Cities, towns, villages, suburbs | `Pl:131-139, 157-158` | city 7-8, town 7-9, village 10-11, suburb 12 | carto's population score (2-5 for cities), 7, 10, 10 | 92-94, 98 |
| Islands | `E:68-75` | polygons from 6, 20 px; no nodes | from 2, carto's size rule; nodes at 14 | 101 |
| Peaks, huts, saddles, camp and picnic sites, viewpoints, shelters | `P:224` (default 15), `P:242-244` (peak), point rows `P:253-292` | 15 (peak 13) | peak 9, huts 11, saddle 13, the rest 14 | 105-109 |
| Area-name points | `P:297-366, 544` | by bbox area; `min_zoom` = zoom + 1 | carto's 3000 px rule, 2 levels earlier | 102-104 |
| POI thinning | `P:565` (1 per 8 px cell to z14) | thinned | not thinned, or a smaller cell | note after row 116 |
| Kinds the tiles lack | heath (`L:66-244`), the service=* value (`R:69-76`), wetland=* (`L:123-135`) | absent | add | 4, 46, B2 |

- **Not assessed:** tile sizes and build time after these changes.
  - Moving rail, rivers, streams, footways and names one to four levels earlier adds features to low-zoom
    tiles (i).
  - Protomaps' own feature and size limits were not read here.
- **Offline (C2):** downloads hold z10-15 only, so data fixes below 10 show online only. A downloaded region
  holds the old tiles until it is downloaded again (i).

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

- **Read:**
  - every carto figure, at the cited lines of v6.1.0. Three helpers gathered them; I checked about 45 of their
    lines against the clone myself, and all matched;
  - every profile figure, at the cited lines of `ca93fc06` with 0001-0007 applied (a scratch tree with the
    patches applied in order, all clean). A fourth helper gathered them; I checked 18 ranges, and all matched;
  - every Street v4 layer's zooms and filters, dumped from the JSON on main;
  - the app's code in A2 and C2;
  - MapLibre's tile-level rule (A1b);
  - OSMF's carto pin (A1).
- **Computed:**
  - the in-app column (N − 1.5);
  - Δ;
  - the Fix column (Aspen's rule);
  - the size-floor conversions;
  - the row counts.
- **Inferred, marked (i) where they appear:**
  - how Planetiler measures pixels (1/256 of a tile) and its defaults. Taken from the profile helper; the jar
    was not decompiled;
  - that sub-pixel fills aren't visible;
  - what a downloaded region does after a style or tile change;
  - the tile-size effect of the data rows;
  - which screenshot creek is meant.
- **Willow's decode** (forager-cartography `3e1304b`) is Willow's read of the live archive at Molalla, and is
  used only as a check. Where it and the profile agree (most rows), both are cited. I did not read the archive:
  the dispatch says no Pi.

### Could not determine

- **The zooms of the owner's four screenshots.** The worked examples map each observation to a row, not to a
  zoom.
- **Natural Earth's per-feature `min_zoom`** (oceans and lakes at z0-5, boundary lines at z0-5). It lives in the
  NE data, which was not opened.
- **Planetiler 0.10.2's exact minimum-size defaults.**
- **Whether the live archive was built from exactly 0001-0007: answered after the draft.** Aspen relayed (message
  received after 14:52 PST) that the live archive's manifest names `map_build_commit` `53a7283`, read by Willow
  on the Pi. So the live tiles were built from 0001-0007 (Willow's reading via Aspen; not read by me).
- **Whether and when MapLibre refreshes a downloaded region's stored v4 document** (C2).
- **Tile sizes and build time** after the data changes (C1).
- **What carto's collision handling drops in practice:** the figures are where a rule first allows a label.

### Premises that were wrong

- **`v5.9.0` as OSM's style:** the tile servers run `v6.1.0` (A1). Aspen confirmed it at 14:30 by their clock.
- **Comparing carto's zoom number with our `minzoom` directly:** in the app, carto's level N shows from camera
  N − 1.5 (A1b).
- **"Street v4 is generated from main":** two of its four override files (`forager-fill-in.mjs`,
  `forager-reference.mjs`) exist only on pre-main (A2).
- **The dispatch file path:** prompts/preserved/2026-11-02-03-osm-zoom-levels.md is on `records-after-173` only,
  not main or pre-main. That is as the dispatch implies; I note it because ~/Forager is on `pi-origin`.

### Decided beyond scope

- **Nothing was decided by me.** The match point (camera N − 1.5, data judged at tile N − 2) is Aspen's
  decision, recorded in A1b.
- **Aspen's answers to the open points** (message received after 14:52 PST, recorded by Aspen in
  forager-cartography's coder-13 dispatch, "Update, 14:53"; Aspen's decisions, not mine):
  - **"Equal" goes both ways:** where ours is earlier, it moves later. Those rows are already marked style.
  - **The B2 items stay out** of the fix, as a list for the owner.
  - **The style is written as a new v5 file.** Whether it ships as v5 or as v4 in place is decided with the
    owner at the merge (C2, `StreetStyle.kt:20-21`).
- **Aspen's answers to the last two points** (message received after 14:54 PST, recorded in coder-13):
  - v1 to v3 are frozen, so v1's z14 streams stay.
  - The offline style's missing labels are not added. They go on the owner's list with B2, and its existing
    layers get matched.
- **Scope note:** OSM's maxzoom is reported only where it hides something ours still shows: country names
  above 360,000 px, state names from z7, towns from 16, villages from 17 and hamlets from 18 (`pn` rules in
  rows 90-95).
