# Topo night at low zoom with pure V1: renders for the owner (dispatch 2026-09-28-310, follow-up)

Owner, after the built paint: *"The map needs to not be dimmed, but match what we see when zoomed in"*, then *"Pure inverted colors is the way, not fine tuned pixel manipulation. We may need to lose some of the topo rendering to make that happen"*, then *"Can we apply hillsides and such after the inversion to keep the natural hues?"* Every image below uses **today's V1 exactly** (`raster-brightness-min 1`, `-max 0`, `-hue-rotate 180`); the built half-amplitude paint is not shown. Nothing was built or run under Gradle. **These are a model's stills, not device screenshots.**

## How they were made
- A view is a **256 x 256 dp square** centred on Oregon City (45.36 N, 122.6 W), drawn at **3 px per dp**, from the real tiles (`../tiles/`, `tiles-extra/`, `osm-tiles/`, `osm-extra/`, `dem-tiles/`), bilinear-resampled, then the paint applied per pixel by `measure.shade`, the Python mirror of `RasterShaderModel.kt`. Mirror checked against `nightColorOf`'s formula on all 15,526 distinct colours in the 81-tile sample: worst channel difference 0. The Kotlin model itself is unchanged and was not re-run (no Gradle).
- Tile level for a source of size S at map zoom z: `round(z + log2(512/S))` (13.5.0 `coveringZoomLevel`, cited in the completion report). For `raster-dem` it is `floor`, not `round`: `tile_cover.cpp` rounds only `Raster` and `Video` sources (read at tag `android-v13.5.0`).
- **A caveat that flatters the smallest sources:** Pillow's bilinear resize filters when it shrinks a tile; a GPU's bilinear sample does not (and whether MapLibre mipmaps raster tiles was not read). Two images shrink tiles: the 64 px source at map zoom 7 (1.3x) and the 32 px crop (2.7x). They look cleaner than a device would. Every other panel magnifies, where the two agree.
- Reproduce: `python3 make_renders.py`, `python3 render_metrics.py`. The letter height used below, **10 px**, is the median label-letter height in the sampled 256 px tiles (8-11 px at every level z8-z14; `labelsize.py`): OpenTopoMap labels are a fixed pixel size at every level, so a label's size on screen is 10 px x (dp per tile / 256).

## Images (`sheet_*.jpg` are half-size overviews; the individual `.jpg` files are full size)
| | map zoom 7 | 8.5 | 9.4 | 11 |
|---|---|---|---|---|
| today, topo V1 | `mz7_0_today_topo_v1` | `mz8.5_0_today_topo_v1` | `mz9.4_0_today_topo_v1` | `mz11_ref_topo_v1` (the reference) |
| (a) deeper tiles | `_a_..._tilesize128`, `_tilesize64`, `_tilesize32_crop96dp` | `_tilesize128` | `_tilesize128`, `_tilesize64` | |
| (b) OSM Standard V1 | `mz7_b_osm_v1` | `mz8.5_b_osm_v1` | `mz9.4_b_osm_v1` | |
| (c) OSM V1 + hillshade | `mz7_c_osm_v1_hillshade` | `mz8.5_c_...` | `mz9.4_c_...` | `mz11_c_topo_v1_hillshade` (over topo V1) |
| the switch at 9.5 under (b) | `sheet_switch_9.5.jpg`: OSM V1 at 9.4, topo V1 at 9.6, topo V1 at 9.4 for comparison | | | |

Mean / median (ground) lightness of each image, channel mean 0-1 (`metrics.md`; one window, so a picture of these six views, not a population):

| | zoom 7 | 8.5 | 9.4 | zoomed-in reference (11) |
|---|---|---|---|---|
| today topo V1 | 0.42 / 0.41 | 0.48 / 0.50 | 0.49 / 0.50 | **0.28 / 0.25** |
| (a) 128 px source | 0.44 / 0.43 (level 9, still tinted) | 0.23 / 0.19 | 0.23 / 0.19 | |
| (a) 64 px source | 0.44 / 0.45 (level 10, still tinted) | not rendered | 0.23 / 0.20 | |
| (a) 32 px source, 96 dp crop | 0.23 / 0.21 | | | |
| (b) OSM V1 | 0.22 / 0.22 | 0.19 / 0.18 | 0.17 / 0.17 | |
| (c) OSM V1 + hillshade | 0.22 / 0.21 | 0.20 / 0.19 | 0.19 / 0.18 | 0.28 / 0.26 (over topo) |

## (a) Deeper OpenTopoMap tiles below 9.5 (`tileSize` trick)
**What the renders show.** It works only where the fetched tile level is **11 or more**: the tinted, bright-green regime is a property of tile levels 7-10 (the 81-tile sample), however they are fetched. So `tileSize` 128 gives the zoomed-in look from map zoom 8.5 up (level 11), and at zoom 7 even 64 px (level 10) still looks like today's. Level 11 needs `tileSize <= 2^(zoom - 1.5)`: 45 at zoom 7, 91 at zoom 8, 128 at 8.5, 181 at 9.
**Verified in 13.5.0 (source, not run):** the source's `tileSize` enters `coveringZoomLevel` (`render_raster_source.cpp:32` passes `impl().getTileSize()` to the tile pyramid, which calls `coveringZoomLevel` at `tile_pyramid.cpp:101`; `tile_cover.cpp:149-156`), so a source declaring `tileSize` S fetches level `round(zoom + log2(512/S))`; a layer's `maxzoom` hides it at and above that zoom and `minzoom` below it (style spec at the tag). **Not verified:** that a non-power-of-two size is accepted, and that a source used by a hidden layer stops fetching.
**Cost (a 412 x 892 dp view; expected tiles = (W/d+1)(H/d+1), ignoring MapLibre's look-ahead):**

| map zoom | tileSize to reach level 11 | dp per tile | **label letter height** | tiles per view | x today |
|---|---|---|---|---|---|
| 9.4 | 128 (or up to 239) | 169 | **6.6 dp** (today 13.2) | 22 | 2.7x |
| 9 | 128 | 128 | 5.0 dp | 34 | |
| 8.5 | 128 | 90 | **3.5 dp** (today 7.1) | 60 | 3.1x |
| 8 | 64 | 64 | 2.5 dp | 111 | |
| 7 | 32 | 32 | **1.2 dp** (today 10.0) | 401 | **34x** |
| 6 | 16 | 16 | 0.6 dp | 1,518 | |
Map zoom 7 full-window panels for 128 and 64 px are in the set but look like today; the 32 px image is a 96 dp crop because the full window needs about 70 more tiles. **Labels are unreadable below about 8.5 and small at 9.4; below 8 the map is a dark texture with no text.**
**OpenTopoMap usage policy.** As published (`opentopomap.org/about`, section "Verwendung", and the OSM wiki page): CC BY-SA, name the credit; wiki: *"If you plan to use the tiles for bigger projects, please contact us."* **No numeric rate or tile limit is published** in either. The page also says the render server was moved to a smaller vServer on 2026-01-05 ("If there are performance issues, you can select the backup server") and that OpenTopoMap "will be updated to vector tiles soon". My reading, not theirs: 3x-34x the tile requests per view from a volunteer-run server, for a commercial app, is what "bigger projects" exists for; and raster tiles of the style may change when they move to vector.

## (b) OSM Standard below 9.5 under topo night
**Looks:** dark neutral ground (0.17-0.22) with muted green woods, red/pink roads, dim water and white labels that stay readable (Portland and Gladstone are full size): see `sheet_mz*.jpg`. It is the *OSM* look, not the topo look: the 9.4 to 9.6 pair (`sheet_switch_9.5.jpg`) changes road colour (pink/red to olive/yellow), water (dim teal to saturated blue), woods (muted to vivid) and ground tone at one zoom. The raster-opacity crossfade (both layers, `raster-opacity` over zoom; the spec allows a zoom expression) would soften that; not rendered.
**Attribution:** both credits, on the caption and in the style's source `attribution` fields: `© OpenStreetMap contributors` for OSM Standard (the policy: "Show OpenStreetMap licence attribution clearly on the map ... Do not hide attribution beneath UI, behind toggles, or off-screen") and the existing OpenTopoMap line `© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)`. The caption today is built per basemap (`mapCreditsFor`, `BasemapStyles.kt`), so it would need both for a topo map.
**OSM tile usage policy (read at `operations.osmfoundation.org/policies/tiles/`, 2026-09-30):** must use `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, a **valid User-Agent naming the app, not a library default** ("Traffic that uses these defaults will be blocked"), cache per HTTP headers or 7 days; must not prefetch or bulk download; **"Offline use is not permitted on tile.openstreetmap.org"**; "Commercial services ... should be especially aware that access may be withdrawn at any point". **Two findings about the app as it is:** `grep` finds no User-Agent or HTTP-client setup anywhere in `main/`, so whatever MapLibre's default sends is what OSM sees (not read; OSM Standard is already a basemap, so this exists today); and the owner's commercial-safe ruling is exposed to the withdrawal clause for anything served from OSM's own tile server.
**Offline regions are not affected.** `mapStyleSourceFor` returns the offline style whenever `useOfflineTiles` is on, whatever the basemap (`BasemapStyles.kt:121`), regions are built by `OfflineManager` against that vector style (`OfflineStyle.kt`), and neither OSM nor OpenTopoMap rasters are in a region today. So (b) downloads nothing offline; it also means offline keeps its own (vector, V1-recoloured) night.

## (c) OSM V1 + a non-inverted hillshade from Terrarium DEM tiles
**Model.** `hillshade_prepare.fragment` (Sobel over the DEM, scaled by `tileSize / 2^(exaggeration + 28.2562 - zoom)`, stored as `deriv/8 + 0.5` in 8 bits) and `hillshade.fragment` method `standard` (the default), both from `include/mbgl/shaders/gl/` at tag `android-v13.5.0`, ported line by line in `render.py`. Night colours used: shadow `#000000`, highlight `#9FA892` alpha 0.85, accent `#14160F`, exaggeration 0.8, light 335 degrees (defaults except the colours and exaggeration). **Where it may differ:** the blend onto the layer below is modelled as premultiplied alpha (`out = frag + dst x (1 - frag.a)`) and the colours as premultiplied; neither was read in source, both are inferences. MapLibre fills the 1 px derivative border from neighbouring DEM tiles; I clamp at the mosaic edge, which does not reach the views. I compute `cos(latitude)` from the pixel row rather than the tile's two latitudes (under 0.2 % apart). The other four `hillshade-method` values exist in 13.5.0 and are not modelled. Nothing has run on a device.
**Looks:** at zoom 7-9.4 the hillshade adds ridge relief to a dark neutral base and leaves OSM's road and label colours as they are (`mz7_c`, `mz9.4_c`: the Cascade foothills read as shaded hills); it lifts the mean by 0.00-0.02. Over the **topo** V1 at zoom 11 (`mz11_c`) it adds a second, lighter relief on top of the map's own baked hillshade and muddies it; **it does not help above 9.5.** Hues are not "kept natural": the hillshade is neutral grey-olive; it adds relief, not colour.
**DEM licence, as published** (Terrain Tiles attribution document, `github.com/tilezen/joerd/docs/attribution.md`; the AWS registry page names it as the licence): a **composite**; it says "you are responsible for researching each project". It requires one combined credit block (USGS 3DEP/SRTM/GMTED2010 "courtesy of the U.S. Geological Survey"; NOAA ETOPO1; Copernicus EU-DEM; and the regional lines below). Per source, **as the document states it:**

| source | licence as published | CC0? / PD? / CC BY? |
|---|---|---|
| USGS 3DEP (NED) | "public domain ... may be used without restriction"; USGS "requests" credit | public domain (not CC0) |
| SRTM | public domain (US government work); credit requested | public domain |
| GMTED2010 | "No restrictions"; credit "appreciated" | public domain |
| ETOPO1 (NOAA) | public domain; 17 U.S.C. 403 notice required | public domain |
| ArcticDEM | "unlicensed ... may be used, distributed, and modified without permission" | no licence stated |
| LINZ (NZ) | CC BY 3.0 NZ | **CC BY** |
| Austria DGM | CC BY 3.0 AT | **CC BY** |
| Kartverket (Norway) | CC BY 4.0 | **CC BY** |
| Geoscience Australia | CC BY 4.0 | **CC BY** |
| UK Environment Agency | Open Government Licence v3 | OGL, not CC |
| Canada CDEM | Open Government Licence - Canada | OGL, not CC |
| INEGI (Mexico) | "free use of information" | other |
| EU-DEM (Copernicus) | credit line given; no licence named in the document | not stated |
For Oregon the tiles come from USGS sources (public domain); the required credit block still lists every provider. **Nothing in the set is CC0.** Attribution would be a further caption credit. Not found in the pages I read: any usage limit on the AWS bucket (`s3.amazonaws.com/elevation-tiles-prod`).
**Offline cost.** None today (no region carries a DEM). To have hillshade offline, the offline style (server-side, `server/pmtiles-worker`) would need a `raster-dem` source and hillshade layer, and each region would download those tiles too. My sample of four z12 tiles is 111-119 KB each (hilly terrain; flat terrain PNGs are smaller) and z8-z10 tiles 37-92 KB. For a 60 x 60 km region (30 km radius) at latitude 45: about 10 tiles at z10, 29 at z11, 95 at z12, so roughly **15 MB for levels up to 12**; each further level is 4x the tiles (z13 about 380 tiles, z14 about 1,500). An estimate from that sample, not measured on a real region.

## Other off-the-shelf approaches that keep pure V1
1. **Cap topo's minimum zoom at 9.5** (`setMinZoomPreference`): pure V1, nothing else changes, one line. Costs: the topo map could not be zoomed out past about 31 km x 68 km on a phone (75.9 m per dp at this latitude), and **the app's own opening zoom for a region over 30 km is 9.0** (`SightingsMap.kt:2106-2111`), below the cap; there is no min-zoom preference set anywhere today (`grep`).
2. **The app's vector style, V1-recoloured, below 9.5** (the offline style's night path, `NightColour.kt:74-121`; 54 of its 57 colour properties are literals): pure V1 on colours, no tile tint at any zoom, but no contours or hillshade, and it is a whole style loaded by URL, not a layer to put under topo, so this is a larger change. **Not rendered, not measured.**
3. **Crossfade** (b) with opacity (above), which only softens a switch.

## Not done
No Gradle, no change to the built paint. (a)'s 64 px render at zoom 8.5 was skipped (33 more tiles for the least legible option). Not checked: how MapLibre's default User-Agent reads to either tile server; whether a hidden layer's source stops fetching; a non-power-of-two `tileSize`; any of this on the S22.
