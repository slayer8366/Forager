# Editable map parts: a read-only look at the code

Juniper Sounding, 2026-11-02. Dispatch `prompts/preserved/2026-11-02-06-editable-map-parts-pulse.md` (RECORD -1971, origin/records-after-173 at `c9851dfcd`). Bases read: origin/pre-main at `04746d8f0`, origin/route-plot-ui at `a73915df0`. Every claim is marked **read** (from a file at a named commit), **computed** (by a script in `docs/audits/evidence/2026-11-02-editable-map-parts-pulse/`) or **inferred**. File paths without a commit are at pre-main `04746d8f0`. Nothing was built, run under Gradle, or sent to a phone; the only web reads were MapLibre's API pages, and they turned out to be for a later version (B2).

## Method (committed before any file was read in depth)

1. **Styles (A1).** Enumerate every style JSON under `server/pmtiles-worker/src/`, `map-style/` and `app/src/main/assets/`, and every style the app composes in code (`BasemapStyles.kt`, `Basemap.kt`, `StreetStyle.kt`, `OfflineStyle.kt`, `RasterShaderModel.kt`). Count layers with a Python script run with `-I`, grouped by `source-layer` and type, then by a part a user would recognise. Which styles the app actually loads is settled by reading the Kotlin that builds the style URL or JSON, not by the files' existence.
2. **Zoom per part (A2).** Read the tile-build configuration (planetiler/tilemaker profile or equivalent under `data/`, `server/` or `tools/`) for each source layer's minzoom/maxzoom, and the offline download zoom range from `MapLibreOfflineMapRepository.kt`. Where no build config is in the tree, say so and mark the archive's range unverified.
3. **Runtime edits (B1).** Read each named file, record the entry point, the trigger (style load, basemap change, night change, zoom change, setting change), and what MapLibre call it makes. Cross-check with `git grep` for callers.
4. **MapLibre API (B2).** Version from `gradle/libs.versions.toml` (13.5.0, read). Confirm in the library's own documentation for that version the signatures for `Layer.setProperties`, `visibility`, `setMinZoom`/`setMaxZoom`, and paint-property setters.
5. **Cost (C1, C2).** Priced against the dispatch sizes in RECORD this month; unknowns called out.

**Predictions, stated before reading:** the Trail Map v4 style has on the order of 60–120 layers; the offline style fewer; raster styles (Topo, OSM, hill shade) have a handful of layers and no per-part control; the night recolour already touches every paint colour in the loaded style, so step 3 (colours) has the most existing machinery and step 2 (zoom per part) the least.

**How the predictions came out.** v4 has 104 layers (computed), inside the predicted range; the offline style 57. The rasters are one layer each (read), as predicted. Hill shading is not a raster style but one layer of v4 (computed) — the dispatch's list of styles and my prediction both had that wrong. The night recolour does walk every colour property of every layer (read, B1.2), so step 3 has the most machinery; but step 2 has one precedent I had not expected, `hideStreetLabelsUnderTopo`, which already sets `maxZoom` on every label layer at run time (read, B1.5). Method step 2 found no tile-build configuration in the tree: the archive is a Protomaps extract (read, README), and its per-layer zooms are recorded in an earlier audit from the live TileJSON, not re-read here (A2).

## Summary in plain language

Trail Map v4 is 104 style layers from three sources. Grouped by what a walker would recognise, that is **18 parts** (roads, paths and trails, water, water labels, forest/orchard/wetland patterns, parks, other land use, buildings, address labels, hill shading, official-trails band, place names, points of interest, road labels and shields, railways, boundaries, low-zoom landcover, and the ground). The two raster map types, Topographical and OpenStreetMap, are one image layer each: nothing in them can change part by part, only the whole picture (the night inversion is exactly that). The app's own overlays already have per-layer switches, and the official-trails band already is a style part with a switch, a stored choice and a greyed row when the loaded style lacks it. **Step 1 (switches per part) is that band's machinery, repeated 17 times with a table saying which layers make a part**, plus the rows: about one dispatch the size of the Trails-switch PR (#418, 10 files, +635 lines), two if the table is also written into the style's metadata. Steps 2 and 3 are each one to three dispatches and each hides a design decision the code cannot make (what "the colour" of a 36-layer road part is; whether a zoom slider may lower a layer below the zoom its data exists at). Step 4 (presets) is a storage decision first (Room or DataStore, by CLAUDE.md's "will anything join to it?" test) and one or two dispatches after it.

## A. What the map is made of

### A1. Every style the app draws

| Style | Where it is loaded | Layers | Sources | What chooses it |
|---|---|---|---|---|
| **Trail Map v4** (`street-v4.json`) | by URL, `STREET_STYLE_URL` (`app/map/StreetStyle.kt:23`, read); `Style.Builder().fromUri` (`ui/map/MapStyleReload.kt:127`, read) | **104** (computed, `layer-counts-by-source-layer.txt`) | `protomaps` vector (`…/us.json`), `reference` vector tiles z10–15, `dem` raster-dem tiles z5–11 (computed from the JSON's `sources`) | the default map type, and every offline download (`StreetStyle.kt:63-65`, read) |
| Trail Map v3 / v2 / v1 | by URL, only for regions downloaded against them: `offlineRenderStyleUrl` picks the newest Trail Map URL any region holds (`StreetStyle.kt:83-84`, read) | 97 / 93 / 90 (computed) | v3: `protomaps` + `reference`; v2, v1: `protomaps` only (computed) | a phone whose newest Street region is that version |
| **Offline style** (`offline.json`) | by URL, `OFFLINE_STYLE_URL` (`app/map/OfflineStyle.kt:18`, read); loaded when "use offline tiles" is on and no Street region exists (`StreetStyle.kt:84`, read) | **57** (1 background, 15 fill, 41 line: computed; matches `ui/map/NightColour.kt:20`, read) | `protomaps` only | older regions; the no-signal fallback for Topo and OSM users draws from the downloads too (`StreetStyle.kt:70`, read) |
| **Topographical** (OpenTopoMap) | in-app JSON: one raster source, one raster layer (`ui/map/BasemapStyles.kt:203-219`, read) | 1 | one raster, 256 px tiles, operating max zoom 17 (`ui/map/Basemap.kt:155`, read) | map type Topo, by day |
| **OpenStreetMap** | same builder; max zoom 19 (`Basemap.kt:172`, read) | 1 | one raster | map type OSM; and what the -1513 fallback draws in Trail Map's place (`BasemapStyles.kt:149`, read) |
| **Topo at night** | Trail Map v4 by URL with a night topo raster layer added on top from zoom 9.5, fading in to 9.7 (`ui/map/StreetStyleApply.kt:26-47`, `BasemapStyles.kt:245-251, 265-271`, read); once fallen back, two rasters (`BasemapStyles.kt:177-201`, read) | 105 (104 + `basemap`) or 2 | v4's three + one raster | night on, map type Topo |
| **The app's own layers** | added in the `setStyle` callback on every style load (`MapStyleReload.kt:162-169`, read) | 23 registry entries (`ui/map/layers/MapLayers.kt:415-473`, read; counted by hand): 2 colour fields, 20 the app builds, 3 that are Trail Map's own band layers (`LayerRenderer.STYLE_LAYER`, `:54`). Outside the registry: the way-home layers, the fan-out layers and the position-dot images (`ui/map/OverlayLayerSetup.kt:95, 100`, `MapStyleReload.kt:173`, read); on route-plot-ui also the plotted route (`OverlayLayerSetup.kt` diff at `a73915df0`, read) | GeoJSON sources the app owns | always |
| `map-style/forager-light.json` | not loaded by the app: `BasemapStyles.kt` imports only the four URL constants and the raster templates (`:3-6`, read); it is the website's and the label check's (`map-style/generate-street.mjs:5-6`, read) | 71 (computed) | — | — |

One reload path serves every map: `SightingsMap` is composed once, from `MapSlot.kt:103` (read), and `MapStyleReload` is called once, from `SightingsMap.kt:606` (read). Both the Maps tab and the Journal's entry map go through it.

**The 104 layers of v4 as parts** (computed by `parts.py`, grouping by layer-id prefix and type; the grouping is mine, a proposal, not a design): 

| # | Part | Layers | Style `minzoom`/`maxzoom` set on | Colour properties: literal / expression | Present in |
|---|---|---|---|---|---|
| 1 | Ground (`background`, `earth`) | 2 | none | 2 / 0 | all vector styles |
| 2 | Landcover, low zoom (`landcover`) | 1 | none | 0 / 1 | all |
| 3 | Land use fills and edges (settled, hospital, industrial, school, beach, zoo, aerodrome, runway, pedestrian, pier; `landuse_edges` z13+) | 11 | 1 | 9 / 2 | offline 9, v3 9, v4 11 |
| 4 | Forest, orchard and wetland patterns (`landuse_orchards`, `_orchards_pattern` z13+, `_forest_glyphs` z12+, `_wetland_tufts` z10+) | 4 | 3 | 1 / 0, plus 3 `fill-pattern` images | v3 2, v4 4; not in offline, v1, v2 |
| 5 | Parks and green (`landuse_parks_fill`, `landuse_park`, `_park_outline` z6+, `_urban_green`) | 4 | 1 | 3 / 1 | offline 2, v3 3, v4 4 |
| 6 | Roads (casings, centres, tunnels, bridges, runway, taxiway, pier) | 36 | 14 min, 2 max | 25 / 11 | all (36 in each) |
| 7 | Paths and trails (`roads_path_*`, in zoom windows z12+, 13–14, 14–15, 15+) | 18 | 18 min, 7 max | 18 / 0 | v1–v4; none in offline |
| 8 | Railways (`roads_rail`) | 1 | none | 1 / 0 | all |
| 9 | Hill shading (`hillshade`, z5+, `dem` source) | 1 | 1 | 3 / 0 (shadow, highlight, accent) | **v4 only** |
| 10 | Water (fill, `water_river` z9+, `water_stream` z13+, `water_edges` z13+) | 4 | 3 | 4 / 0 | offline 3, v3 3, v4 4 |
| 11 | Water labels (waterway z13+, ocean, lakes) | 3 | 1 | 6 / 0 | v1–v4 |
| 12 | Buildings (fill, `buildings_outline` z15+) | 2 | 1 | 1 / 1 | offline 1, v3–v4 2 |
| 13 | Address labels (`address_label` z18+) | 1 | 1 | 2 / 0 | v1–v4 |
| 14 | Official trails band (`reference_fs_band` z12+, `reference_blm_band` z12+, `reference_fs_number` z14+) | 3 | 3 | 4 / 0 | **v3 and v4 only** |
| 15 | Boundaries (`boundaries_country`, `boundaries`) | 2 | none | 2 / 0 | all |
| 16 | Road labels and shields (`roads_oneway` z16+, `roads_labels_minor` z14+, `roads_shields`, `roads_labels_major` z11+) | 4 | 3 | 5 / 0, plus 2 `icon-image` | v1–v4 |
| 17 | Place names (`earth_label_islands`, `places_subplace`, `_region`, `_locality`, `_country`) | 5 | none | 10 / 0, plus 1 `icon-image` | v1–v4 |
| 18 | Points of interest (`pois_area_names` z8+, `pois`) | 2 | 1 | 4 / 0, plus 1 `icon-image` | v1–v4 |
| | **Total** | **104** | 53 layers carry a `minzoom` or `maxzoom`; 51 carry neither (computed) | 100 literal, 16 expressions (computed) | |

A coarser grouping (labels together, land use together) would give about ten parts; that is a design choice for the owner, not something the code decides. The per-layer listing is `parts-street-v4.txt`; the offline and v3 groupings are `parts-offline.txt` and `parts-street-v3.txt`.

**Which parts exist only in raster styles.** Topographical's contour lines, its own hill shading and its labels are pixels in OpenTopoMap's PNG tiles (one `raster` layer, `BasemapStyles.kt:216`, read): there is no contour source anywhere in the app's styles (computed: v4's sources are `protomaps`, `reference`, `dem`), so **contours exist only where nothing per part can change**. OpenStreetMap's map is likewise one image. What a raster layer can take is the whole-image raster paint: `raster-brightness-min/max`, `raster-hue-rotate`, `raster-saturation`, `raster-contrast` (`ui/map/RasterShaderModel.kt:38-45`, read) and `raster-opacity` (`BasemapStyles.kt:166-168`, read). The night inversion is that paint (`BasemapStyles.kt:60-65`, read).

**Already editable today.** Eight of the app's own overlays have switches in Tools > Map Layers (`ui/availability/MapLayersToolsSection.kt:86-95`, read); the official-trails band has a switch there too (`:123-129`), greyed when the loaded style does not draw it (`:128`, and `ui/map/ReferenceTrailsVisibility.kt:110-121`, read); the colour fields have an opacity slider and reorder handles (`MapLayersToolsSection.kt:173-240`, read). Every one of these persists across restarts in DataStore (`domain/MapLayerPreferencesRepository.kt:24-31`, read). So for the app's own layers, step 1 is done, and step 3's opacity exists for colour fields only (`userOpacity` is `false` on every marker and line: `MapLayers.kt:251, 267`, read).

### A2. The zoom range each part's data exists at

No tile-build configuration is in the tree (method step 2). The archive is a Protomaps extract: `pmtiles extract … --maxzoom=14` (`server/pmtiles-worker/README.md:106-107, 117-119`, read), 8.8 GB on R2; zoom 15 comes from Protomaps' live build through the Worker's overflow (`server/pmtiles-worker/src/index.ts:134-136, 656`, read), and the TileJSON advertises 15 for the Street archive only (`index.ts:578-580`, read). Per source layer, the only record in the repository is the live TileJSON as read on 2026-10-18 (`docs/audits/2026-10-18-street-cloudflare-part1-report.md:36`, read; not re-read now):

| Source layer | Zoom range in the tiles (TileJSON `vector_layers`, 2026-10-18) | Parts it feeds |
|---|---|---|
| `earth` | not stated | 1, 17 (island labels) |
| `landcover` | **z0–7 only** | 2 |
| `landuse` | z2–15 | 3, 4, 5 |
| `roads` | z3–15 | 6, 7, 8, 16 |
| `water` | not stated | 10, 11 |
| `buildings` | **z11–15** | 12, 13 |
| `boundaries` | not stated | 15 |
| `places` | not stated | 17 |
| `pois` | z5–15 | 18 |
| `reference` (`fs_trails`, `blm_trails`) | z10–15, the source's own `minzoom`/`maxzoom` in v4 (computed); the band draws from z12 (`map-style/overrides/forager-reference.mjs:60`, read) | 14 |
| `dem` (relief) | z5–11 (computed from v4's source; `index.ts:392`, read) | 9 |

Two further bounds apply to every part:
- **Offline, nothing below zoom 10.** Downloads ask for z10 to z15 only (`domain/OfflineMapRepository.kt:159-160`: `MIN_ZOOM = 10.0`, `MAX_ZOOM = 15.0`, read; README `:64`, read). Online, z0–8 below the archive's bounds comes from a separate world archive (`index.ts:293`, read), whose own layers this report did not read.
- **Within a source layer, features drop out by zoom inside the tiles.** The 2026-10-18 report found trailhead, viewpoint, ranger station, toilets and drinking water "exist only in z15 tiles" while campground, picnic site, parking and peak can show from z12 or 13 (`…part1-report.md:116`, read), and `roads`/`pois` carry a `min_zoom` field (`:38`, read). So a part's zoom floor is not the TileJSON row but per kind, and the repository holds no table of it. Unverified beyond those five POI kinds.
- **The camera stops at 18 on Trail Map** (`StreetStyle.kt:59`, read); from 15 up the tiles are overzoomed, so a zoom setting above 15 changes drawing, not data.

What this bounds: a "show from zoom N" setting can only show a part where N is at or above the row above and at or above the kind's own `min_zoom`. For buildings that floor is 11; for low-zoom landcover the ceiling is 7 (above it the forest comes from `landuse`, `…part1-report.md:114`, read); for every part offline the floor is 10.

## B. What already changes the loaded style at run time

### B1. Every place the app edits a loaded style

All of it hangs off one effect and its callback, `MapStyleReload` (`ui/map/MapStyleReload.kt`, read). The effect is keyed on `mapLibreMap, basemap, useOfflineTiles, nightMode, nightModeLoaded, ownStreet` (`:76`) and guarded by `needsStyleReload`, which compares the whole `AppliedMapStyle` value (`:91`; `BasemapStyles.kt:371-397, 429`, read). It calls `setStyle` (`:133`) and in the callback runs, in this order:

| Order | Step | File and lines (read) | Runs when | MapLibre calls |
|---|---|---|---|---|
| 1 | Symbol fade off for the whole style | `MapStyleReload.kt:135` → `PlacementTransitions.kt` (not read) | every load | — |
| 2 | **Night recolour**: every background, fill, line and symbol colour of every layer read back and rewritten as its night value; Trail Map's exception tables chosen by the loaded URI | `MapStyleReload.kt:146-151`; `ui/map/OfflineNightRecolourApply.kt:32-69`; properties `ui/map/NightColour.kt:76-81`; the walk `:160-176, 197-263`; tables `ui/map/StreetNightTables.kt:29, 41-71, 83-91` | night on and the source is not the in-app raster JSON | `Style.getLayers`, the typed getters (`getFillColor`…), `Layer.setProperties(PropertyFactory.fillColor(String|Expression))` etc. |
| 3 | Hill-shade night colours | `MapStyleReload.kt:150`; `ui/map/StreetStyleApply.kt:69-82` | night, v4 | `setProperties(hillshadeShadowColor(arrayOf(…)), …)` |
| 4 | **Night images**: bundled night sprite copies added under `night/<name>`, every `icon-image` and `fill-pattern` reference switched, each read back to confirm it took | `MapStyleReload.kt:154`; `ui/map/NightImageSwitch.kt:215-238`; `ui/map/NightImageApply.kt:84-132` | night, Trail Map | `Style.addImages`, `setProperties(iconImage / fillPattern)`, the getters |
| 5 | **Street labels end at 9.5 under topo**: `maxZoom` set on every `SymbolLayer` | `MapStyleReload.kt:155`; `StreetStyleApply.kt:54-63` | Topo at night | `Layer.setMaxZoom(float)` — the one per-layer zoom edit in the app today |
| 6 | Hill shading hidden under topo | `MapStyleReload.kt:157`; `StreetStyleApply.kt:88-92` | Topo at night | `setProperties(visibility(NONE))` |
| 7 | **The Trails switch on load**: the band's three layers shown or hidden from the stored choice; hidden and not offered when the style lacks any of them or the map is Topo | `MapStyleReload.kt:161`; `ReferenceTrailsVisibility.kt:65-82` | every load | `Style.getLayer`, `setProperties(visibility)` |
| 8 | The app's overlays built and added, each with its stored visibility and opacity | `MapStyleReload.kt:162-169`; `OverlayLayerSetup.kt:64-100` | every load | `addSource`, `addLayer`, `setProperties` |
| 9 | Position-dot images | `MapStyleReload.kt:173` | every load | `addImage` |
| 10 | **Pattern relayout**: 400 ms after style and sprite have both loaded, every fill layer with a pattern is read, cleared to `""` and restored at the first full frame at least 100 ms later | `MapStyleReload.kt:207`; `ui/map/PatternRelayout.kt:46-184`; `ui/map/PatternRelayoutApply.kt:24-47` | every reload except the MapView's first style | `getFillPattern`, `setProperties(fillPattern)` |

**Edits without a reload** (the path a user's switch takes today): `MapFan.kt:133-144` (read) is a `LaunchedEffect(state.loadedStyle, drawnLayersState)` that walks every registry spec and sets its visibility and opacities on the live style, through `applyStyleLayerPaint` for the band and `applyLayerPaint` for the app's layers (`ReferenceTrailsVisibility.kt:89-91`; `OverlayLayerSetup.kt:194-201`, read). The user's choice reaches it like this: the row in Tools (`MapLayersToolsSection.kt:115-129`) → `controls.onVisibilityChanged` → `AvailabilityMapDisplay.kt:117-119` (state updated, then `setLayerVisible` stored) → DataStore key `map.layer.<id>.visible` (`data/repository/DataStoreMapPreferencesRepository.kt:103-104, 129-131`, read) → `MapRenderMode.layers` (`ui/map/MapRenderMode.kt:175`, read) → the drawn state, with colour fields without data and the band on a style without it hidden (`SightingsMap.kt:320`, read) → `MapFan`. At start the stored choices are validated against the registry and anything refused is logged (`ui/map/layers/MapLayerPreferencesState.kt:20-48`; `AvailabilityMapDisplay.kt:171-175`, read).

**The -1513 fallback** (`ui/map/OwnStreetFallback.kt:54-59, 66-70`, read): a failed load of Trail Map online flips Compose state that `MapStyleReload` reads in composition (`:72`), so the effect relaunches and loads OpenStreetMap's raster in Trail Map's place (`BasemapStyles.kt:149, 315`, read) until the signal returns. While fallen back there is no vector style and so no part to edit.

**The credit strip's "style hooks".** On pre-main the strip reads no style: its text is `mapCreditsFor(basemap, offline, activeLayerCredits(registry, drawnState), night, ownStreet)` (`ui/map/SightingsMapLayout.kt:139`; `BasemapStyles.kt:336-351`; `ui/map/layers/MapLayerState.kt:80-84`, read), so a layer's credit follows its switch through the registry, as the band's does (`MapLayers.kt:358`). MapLibre's own "i" dialog reads each source's `attribution` field in the style JSON (`Basemap.kt:88-91`, read). On route-plot-ui, D24 removed the strip and lists the credits under Tools > Map Layers (`MapLayersToolsSection.kt` diff at `a73915df0`: `MapCreditEntries`, `layerCredits`; RECORD -1966, -1970, read). Credits are per source, not per part, so hiding a part changes no credit.

**How a user edit would join this.** Two facts set the shape:
1. **Nothing set on a style survives `setStyle`.** The overlays are re-added in the callback for exactly that reason (`MapStyleReload.kt:54-56, 187-193`, read), and a map that leaves its tab and comes back is a new MapView whose first style load restores the camera (`:107-109`, read). So every per-part edit must be re-applied in the callback, from state hoisted above the map, which is where the overlay choices already live (`AvailabilityMapDisplay.kt`, the ViewModel). CLAUDE.md's "what the user has set survives navigating away and back" is met by that shape today.
2. **The callback's order carries meaning.** The night recolour reads day colours and the Trails switch runs after it so a hidden band is already in night colour when shown (`MapStyleReload.kt:158-161`, read); the two "under topo" steps force labels and hill shading off. A user colour applied before step 2 is inverted by it; applied after, it is the night colour itself. A user "hill shading on" must not undo step 6 under topo. Where the new steps sit is a decision, and `NightTablesBySourceTest` is the model for pinning it (`StreetNightTables.kt:88-89`, read).

### B2. What MapLibre Android 13.5.0 supports

The pinned version is `maplibre = "13.5.0"` (`gradle/libs.versions.toml:43, 97`, read). The published API pages at maplibre.org are for **13.7.1**, and javadoc.io has no generated docs for 13.5.0 (web reads by a fetch agent; URLs in the agent's report, summarised here). So the signatures below were read instead from the pinned artifact itself: `android-sdk-13.5.0.aar` in the local Gradle cache (sha256 `eda8c4a1…dfa6a61`, `evidence/maplibre-13.5.0-javap.txt`), `classes.jar` listed with `javap -public` — the same check the coder recorded before writing the night and topo steps (`StreetStyleApply.kt:22-24`; `OfflineNightRecolourApply.kt:17-21`, read). This is a read of class files, not a Gradle run.

Verbatim from `javap` at 13.5.0 (computed):

- `org.maplibre.android.style.layers.Layer`: `public void setMinZoom(float);` `public void setMaxZoom(float);` `public float getMinZoom();` `public float getMaxZoom();` `public void setProperties(PropertyValue<?>...);` `public PropertyValue<String> getVisibility();` `public String getId();`
- `FillLayer`, `LineLayer`, `SymbolLayer`: each has `public void setFilter(Expression);` and `public Expression getFilter();` plus typed getters (`getFillColor(): PropertyValue<String>`, `getFillOpacity(): PropertyValue<Float>`, `getFillPattern()`, `getLineColor()`, `getLineOpacity()`, `getLineWidth()`, `getTextColor()`, `getTextSize()`, `getIconImage()`).
- `PropertyFactory`: `visibility(String)`; `fillColor(int|String|Expression)`, `lineColor(…)`, `textColor(…)`, `textHaloColor(…)`, `backgroundColor(…)`; `fillOpacity(Float|Expression)`, `lineOpacity`, `textOpacity`, `iconOpacity`, `rasterOpacity`; `lineWidth(Float|Expression)`, `textSize(Float|Expression)`; `fillPattern(String|Expression)`; `hillshadeShadowColor(String[]|Expression)`, `hillshadeExaggeration(Float|Expression)`.
- `org.maplibre.android.maps.Style`: `getLayers(): List<Layer>`, `getLayer(String)`, `getLayerAs`, `addLayer`, `addLayerBelow`, `addLayerAbove`, `addLayerAt`, `removeLayer(String|Layer)`, `removeLayerAt`, `getJson()`, `getUri()`, `getTransition()`, `isFullyLoaded()`, `addImage`/`addImages` and their `Async` forms.

The 13.7.1 pages the agent read agree on the names it quoted verbatim (`Layer.setMinZoom(zoom: Float)`, `setProperties`, `getVisibility`, `PropertyFactory.fillColor`/`fillOpacity`/`hillshadeShadowColor`, `Style.getLayers()`, `getLayer`, `addLayer`, `getJson`, `isFullyLoaded`; its `visibility`, `lineColor`, `lineOpacity`, `textColor` and `rasterOpacity` lines were a page summary, not verbatim), which is consistent with, not proof of, the 13.5.0 listing; the `javap` listing is the evidence.

Behaviours of 13.5.0 the code already records and a user edit inherits (all read):
- A value MapLibre refuses is logged natively, the old value kept, and nothing is told to the caller: reading back is the only way to know a write took (`NightImageSwitch.kt:188-190, 232-235`).
- `["zoom"]` is accepted only as the input of a top-level `step` or `interpolate` (`NightImageSwitch.kt:68-70`), which limits how a user zoom can be folded into an existing expression.
- `setStyle` clears the old style at once and every getter on it then throws; readers go through `currentStyle` (`ui/map/MapStyleReads.kt:19-30`).
- The sprite loads about 220 ms after the `setStyle` callback (`NightImageSwitch.kt:15-17`); patterns on tiles kept across a reload need the relayout above (B1.10).
- Zoom in a layer's `filter` is evaluated by MapLibre, and v4 uses it on two layers: `pois` shows outdoor kinds from `[">=", ["zoom"], 12]` and `pois_area_names` from `["max", 8, min_zoom]` (computed, `parts.py` run; filters printed in the evidence). `setMinZoom` can raise those layers' floor but cannot lower it; lowering means `setFilter`.

## C. What each step would cost

Dispatch sizes this month, for scale (computed: `git show --shortstat` of each merge against its first parent, on pre-main): the Trails switch, one style part end to end with registry entry, load-time and live visibility, stored choice, greyed row and credit, PR #418, **10 files, +635**; the band's taps, #419, 19 files, +721; the pattern relayout, #429, 4 files, +416; the Map Layers move to Tools, #432, +380; the v4 night tables by source, #464, 1 file, +40. The suite CI runs is about 6,198 tests (RECORD -1970, read). A dispatch here is one PR of 1–20 files and 40–1,000 lines, with its tests and revert checks.

### C1. The four steps

**Step 1: map parts with switches.**
- *Domain.* A table from part id to the layer ids that make it, with a label, for v4 (and what v3, v2, v1 and the offline style lack of it: A1's "Present in" column). Two homes: (a) a Kotlin table beside the registry, checked by a test that every v4 layer in `server/pmtiles-worker/src/street-style-v4.json` belongs to exactly one part (so a new style layer can never be silently uncovered); or (b) written into the style's `metadata` by `map-style/generate-street.mjs` as `forager:parts`, the way the night tables are (`StreetNightTables.kt:3-6`; metadata keys computed: `forager:night-layers`, `-fills`, `-line-colours`, `-hillshade`, `-sprite-icons`…) and read from `Style.getJson()` as `NightImageSwitch` reads `forager:sprite-icons` (`:169-173`). **(b) has a trap, C2.1.** The part becomes a state owner the way the band's first layer is: `stateOwnerId` lets one switch govern many layers (`MapLayers.kt:132, 438-440`, read), and `LayerRenderer.STYLE_LAYER` already means "the style draws it, the app only shows or hides it" (`:47-54`). Putting 104 ids into `MAP_LAYER_REGISTRY` would stretch a list built for 23 and its `registryProblems` checks (`:483-517`); a part-level type that resolves to layer ids at apply time is the "new path, not a conditional" CLAUDE.md asks for. Which of the two is a planner decision.
- *Storage.* DataStore, flat: `map.layer.<id>.visible` already fits a part id (`DataStoreMapPreferencesRepository.kt:129-131`), and `restoreMapLayersState` refuses ids the registry does not allow (`MapLayerPreferencesState.kt:25-32`), so whatever table is chosen must be what it validates against.
- *Apply.* On load: one call in the callback where `applyReferenceTrailsOnStyleLoad` sits (`MapStyleReload.kt:161`), after the night recolour, before the overlays, passing over layers the style lacks and reporting whether each part is drawn (its `:65-82` shape). On change: the `MapFan.kt:133-144` walk, extended to parts. Under topo, steps 5 and 6 must still win.
- *UI.* Rows in `MapLayersToolsSection` under Trails (`:123-129`), greyed when the loaded style lacks the part, with the Trails row's `ReferenceTrailsShown` report generalised to a set of drawn parts (`ReferenceTrailsVisibility.kt:117-121`). On route-plot-ui the same section gained the credits (D24), so the rows land in a file that branch is changing.
- *Tests.* Headless over a style fixture (`ReferenceTrailsVisibilityTest`'s shape); the one-part-per-layer coverage test above; ViewModel and Tools-row tests beside `AvailabilityViewModelMapLayersTest` and `MapLayersToolsSectionTest` (listed, not read); live-style effects are device-only, since a real MapView cannot be built under Robolectric (`ui/map/MapAttribution.kt:72`, read).
- *Breaks or interacts.* Night: nothing, the recolour walks hidden layers (`ReferenceTrailsVisibility.kt:62-63`). Offline: v1–v3 regions draw styles without parts 4, 9, 14; the rows grey. Pattern workaround: untouched, it reads `fill-pattern`, not visibility. Credits: unchanged. Taps: a hidden symbol or line part leaves `queryRenderedFeatures`, which is the wanted result (inferred). Fallback: all rows grey.
- *Size.* One dispatch of #418's size for the table, apply path and rows; a second if (b) is chosen (a style regeneration and Worker PR, as #460/#462 were). *Not knowable without a spike:* whether 100 `setProperties(visibility)` calls in one frame on the S22 show a hitch; whether hiding `pois` or place labels visibly changes the placement of the labels that remain (it frees collision space, so it should, and that is fine).

**Step 2: zoom per part.**
- *API.* `Layer.setMinZoom(float)`/`setMaxZoom(float)` (B2), with the app's one precedent (B1.5). A part's setting is per-layer arithmetic: 18 path layers are sliced into windows (z13–14, 14–15, 15+) by design, 51 layers carry no zoom at all, and raising a part's floor means `setMinZoom(max(user, layerOwn))` on each.
- *Raising is cheap; lowering is not.* Below a layer's design zoom its line widths and text sizes are zoom ramps (60 `line-width`, 17 `line-gap-width`, 8 `text-size` zoom expressions, computed) that hold their end value; two layers gate on zoom in their filter (B2); and the data has its own floor per kind (A2). A slider that goes below the data's floor shows nothing and makes the user think the map is broken (CLAUDE.md, "Don't make me think"). So the slider's floor should be the A2 table's row, and the per-kind floor is unknown without a spike against the tiles.
- *Storage.* New flat keys `map.part.<id>.minzoom` (float), validated on restore as the others are.
- *UI.* The one step with no precedent in the app; a prior-art check comes first (`prior-art-before-interface`). Topographic apps commonly offer "show labels from zoom …" per class, not a slider per part — unverified, from memory, to be checked.
- *Interacts.* `hideStreetLabelsUnderTopo` sets `maxZoom` 9.5 on every label layer (`StreetStyleApply.kt:54-63`) and must run after and win; the camera's own limit (`MapStyleReload.kt:120`); offline's z10 floor.
- *Size.* One dispatch for raise-only on labels, POIs, buildings and paths; a spike dispatch before any lowering.

**Step 3: colours and opacity, a night colour for each.**
- *Opacity.* `fillOpacity`/`lineOpacity`/`textOpacity`/`iconOpacity(Float|Expression)` exist (B2); the registry's multiplier model (`MapLayerState.kt:4-22`) extends to parts, but a style layer's base is not in the registry: it must be read from the loaded style (`getFillOpacity`), and where it is a zoom expression (3 in v4, computed) the multiplier wraps it. The night recolour's read-transform-write is the template (`OfflineNightRecolourApply.kt:35-56`). The band already carries its 0.35 in the style, not the registry (`MapLayers.kt:49-52`).
- *Colour.* 100 literal colour properties and 16 expressions in v4 (computed). A literal is one `setProperties(fillColor(String))`; an expression (the roads' zoom-stepped colours, `landuse_park`'s match on kind, `buildings`) needs one literal inside it replaced, and `NightColour.kt:230-263` already walks expressions replacing colour literals, so the same walk with a different mapping does it. **The design decision the code cannot make:** a 36-layer road part has casing and centre colours that differ by class; "the road colour" is either the centre colours only with casings derived, or several colours. Likewise forest is one output of `landuse_park`'s match (filter at `parts.py` output), and the four pattern layers have no colour at all — their colour is in the sprite images, whose night copies are bundled (`NightImageApply.kt:25-28`); a user tint of a pattern is a new mechanism (an SDF icon or a painted bitmap), not a property write. Stop-and-ask.
- *Night colour per part.* Today night is derived: `nightColorOf` (`NightColour.kt:39-47`) plus tables keyed by layer id and by **day colour literal** (`:113-131, 145-149`; `StreetNightTables.kt:41-71`). A user day colour no longer matches a by-day-colour entry and falls to the pure inversion, which is an automatic, defensible default; the owner's "night colour for each" is instead a second stored colour per part, applied after the night pass. Either order works; which is a ruling. `NightExceptionsTest`/`NightExceptionsV4Test` hold the app's tables equal to the style's metadata and are untouched if user colours live beside them.
- *Raster map types* take no part colour, only the whole-image paint (A1).
- *Storage.* `map.part.<id>.colour`, `.night_colour` (ints), `.opacity` (float), DataStore while standalone.
- *UI.* A colour control; prior-art check first; CLAUDE.md's "an automatic, sensible default beats a new setting" argues for a short palette per part over a free picker.
- *Size.* Opacity, one dispatch; colour with a palette, one to two; night colour, one. *Not knowable without a spike:* the form MapLibre hands expression colours back in (`NightColour.kt:190-191` marks the array form inside expressions "unverified until a device run", and nothing I read lifts that mark; that the night pass has shipped and the owner has judged its colours on the S22 is consistent with the literal form being read correctly, but that is inferred, not observed here).

**Step 4: presets.**
- *What it is.* A named bundle of steps 1–3's values, with "as designed" as the default. The validate-on-restore pattern (`MapLayerPreferencesState.kt:20-48`) extends to a preset's contents.
- *Room or DataStore* — CLAUDE.md's deciding question is whether anything will reference a preset: a journal entry or an offline region remembering "the preset I was on" puts presets in Room with an index from the start; standalone named settings stay in DataStore (one string per preset, or `map.preset.<name>.*`). This is a stop-and-ask before the first line.
- *Interacts.* A preset chosen on a map whose style lacks some parts (v3 offline, raster types) applies what it can and says what it skipped (the `:76` log line's shape). The night pass runs under any preset.
- *Size.* One dispatch for the model and store, one for the UI (list, "save current as", rename, delete). Only after steps 1–3 exist.

### C2. Harder than the sketch, or unnecessary

1. **A parts list in the style's metadata is invisible to downloaded regions.** MapLibre's offline store keeps the style document as downloaded and serves it by URL (`OfflineStyle.kt:3-10`; `StreetStyle.kt:20-21`, read): a v4 region downloaded before `forager:parts` was added would draw from a stored v4 without it, and the app would read "no parts" offline. The night tables avoided this by shipping inside v4 from the start and by keeping an app-side copy a test holds equal. So the parts table lives in the app (option (a)), or the parts ship as **v5**, a new URL, as every design change has (`StreetNightTables.kt:9-10`). Inferred from the store's keying rule, not tested on a phone.
2. **Every edit is re-applied on every load, in a fixed order** (B1, "How a user edit would join this"). The sketch's "switches" are the easy case; colours and zooms must be ordered against the night pass and the two under-topo steps, and that order needs a test the way the tables' choice has one.
3. **Writes can fail silently** (B2): every per-part write needs a read-back or a counted log line, as `applyNightImages` does. A switch that "did nothing" is the state CLAUDE.md names as the worst.
4. **Raster map types have no parts.** Two of the three map types show the sketch's controls greyed, as Trails is on them today; the UI must make that read as "not on this map", not as broken.
5. **Zoom lowering is a spike, not a setting** (step 2): filters that read zoom, ramps that end, and data that stops per kind.
6. **"The colour" of a part is undefined for roads, forest and patterns** (step 3); that decision precedes the dispatch.
7. **Unnecessary:** per-layer registry entries for all 104 layers; switches for the app's own overlays (done); a zoom control on parts whose data floor is already their design floor (buildings z11, the band z12); a night colour per part where the inversion of the user's day colour would do.
8. **Unnecessary, probably:** step 1 for the offline style's 57 layers as a separate table — they are a subset of v4's ids (computed: the offline parts are v4's parts 1–3, 5–6, 8, 10, 12, 15 with fewer layers), so one table covers both.

## Disclosure

**Confirmed (read or computed) vs inferred.** Layer counts, the parts grouping, zoom and colour-property counts, filters and metadata keys are computed from the JSON at `04746d8f0` (sha256 in `evidence/style-sha256.txt`). The reload path, its order, the switch path and the stores are read at the lines cited. The 13.5.0 signatures are read from the pinned aar's class files. Inferred: that hiding a part removes it from tap results; that the store's URL keying makes metadata changes invisible offline (C2.1); that 13.7.1's pages describe 13.5.0 (used only as a cross-check).

**Could not determine.** The per-kind zoom at which features exist in the tiles (A2 has nine source-layer rows from a 2026-10-18 read, and five POI kinds); the world fill's layers below z9; whether a hundred visibility writes in one frame are smooth on the S22; the array form of expression colours as 13.5.0 returns them; MapLibre's 13.5.0 documentation as published (not on the web); any effort figure — the sizes in C are PR line counts of merged dispatches, not hours.

**Premises that were wrong.** The dispatch lists "the Topo and OSM rasters, the trails overlay, hill shading" as styles: the trails band and the hill shading are layers of v3/v4 and v4, not styles of their own. "The credit strip's style hooks": the strip reads no style, only the registry and the basemap, and on route-plot-ui it is gone. "A night colour for each" assumes night is stored per part today; it is derived, with exceptions keyed by layer id and day colour. The planner's "four steps" and sizes were from memory (RECORD -1971) and none of the steps was wrong as a shape.

**Decided beyond scope.** The 18-part grouping is mine, by id prefix and type; a coarser one is as defensible. I read the 13.5.0 artifact from the Gradle cache instead of the web docs the dispatch allowed, because the web docs were not the pinned version. I did not read `PlacementTransitions.kt`, `MapTapHandler.kt` or the test files beyond their names.
