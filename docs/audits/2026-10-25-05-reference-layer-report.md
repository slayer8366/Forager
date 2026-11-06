# Step 8, the reference layer: report before building (dispatch 2026-10-25-05, part 1)

**Status: a report. Nothing is built.** No app code, no Gradle, no phone. Written by an app coder (Opus) on 2026-10-25 (UTC) for Rowan Waymark, under the owner's go "Start as sketched (Recommended)" (RECORD -1629).

**Base, verified against the remote before reading.** `git ls-remote origin` at 2026-10-25T06:2xZ gave `pre-main` = `f5a0cfafc6e44f2bb3843d90d04807db904cc1ca` (the dispatch's floor, "f5a0cfafc or later") and `records-after-173` = `2b536f576`. App code was read in a detached checkout of `f5a0cfafc`. Paths below are relative to the repository root; `A/` is `app/src/main/java/com/zynergylabs/forager/app/`. Line counts are `wc -l` at that commit.

**This is a claim about the past.** Every statement about the tree is true at `f5a0cfafc`. The build dispatch re-reads what it stands on.

**What the step is.** Plan step 8 (`docs/plans/2026-10-21-trail-navigation-plan-update.md:48`, on pre-main): "Forest Service and BLM trails, drawn apart (-1176)". The rulings: OpenStreetMap is the routable base, and the agency trails are "a separate reference layer, used to flag gaps, not merged into the route network" (RECORD -445, item 1); the layer is the Forest Service's and BLM's trails only, USGS dropped (-1176).

---

## 1. Sources and licences, read at source today

Fetched with a plain HTTP client on 2026-10-25 between 06:25 and 06:31 UTC into `~/.cache/later-steps-scratch/web/` (outside the repository), reduced to text, and quoted. Only catalogue pages and metadata were read; no dataset was downloaded.

| Source | Licence, quoted | Dates, quoted | Read from |
|---|---|---|---|
| **Forest Service, National Forest System Trails** (`EDW_TrailNFSPublish_01/MapServer/0`) | Every distribution on the catalogue page carries `"license" : " https://creativecommons.org/licenses/by/4.0/ "`. Metadata use constraint is a warranty disclaimer only ("The USDA Forest Service makes no claims, promises, or guarantees about the accuracy, completeness, or adequacy of the content…"); access constraint "None". | Catalogue: "Dataset Last Updated: August 25, 2022", "Catalog Last Checked: October 09, 2026". Metadata `pubdate` 20250514, `update` "Irregular". | `catalog.data.gov/dataset/national-forest-system-trails-feature-layer`; `data.fs.usda.gov/geodata/edw/edw_resources/meta/S_USA.TrailNFS_Publish.xml` |
| **BLM, National GTLF Public Managed Trails** | "Access & Use License http://www.usa.gov/publicdomain/label/1.0/ Access Level public" | "Dataset First Published September 15, 2022", "Dataset Last Updated October 13, 2026 at 08:09 PM" | `catalog.data.gov/dataset/blm-natl-gtlf-public-managed-trails` |

These match Juniper's quotes of 2026-10-17 (`docs/audits/2026-10-17-trail-data-comparison/licence-quotes.md:21-29`, branch `trail-data-comparison` 7a10b971), with one change: BLM's "Dataset Last Updated" has moved from September 22 to October 13, 2026 since Juniper read it. The licence is unchanged.

**What the licences ask of the app** (read from the licences' own terms as quoted by Juniper and the catalogue; not legal advice):
- Forest Service, CC BY 4.0: attribution, the licence's name and a link, and a note if the data was changed. Tiling and simplifying lines for a map is a change, so the credit carries a one-line note, the same shape the app already uses for Open-Meteo (`A/ui/credits/DataSourceCredits.kt:56-65`, `detail = "Rain and soil figures…"`).
- BLM, public domain: nothing required. The owner's practice is to credit anyway (`trail-data-comparison.md:172`).
- Credit lines, proposed from Juniper's wording (`trail-data-comparison.md:171-172`): "Trails: National Forest System Trails, USDA Forest Service, CC BY 4.0" with the licence link, and "Trails: Bureau of Land Management, public domain".

**Share-alike stays off the agency data.** Juniper's reading of the Collective Database guideline (`trail-data-comparison.md:182`): separate layers that "do not reference each other" keep ODbL on the OSM part only. The design below keeps them as two sources in one style, never joined. One proposal in §3 (a server-computed gap flag) would make the agency data reference OSM; it is set out there as a licence question, not a recommendation.

**What the data is, from Juniper's measurement** (`trail-data-comparison.md`, read; seven 10 km boxes, not regional estimates):
- where both have a trail they agree to a median 2.6 to 3.9 m, 11 m in the Idaho interior (:138);
- the agency layer adds official names and numbers on 100% of rows, `trail_class`, and, where a forest publishes the `MGMT` subset, uses and seasons (:136, :140);
- it adds a few backcountry trails OSM lacks, about 12.5 km in two of four forested boxes (:136);
- some forests publish no geometry at all: Eagle Cap had 0 rows (:94);
- Forest Service snow trails (nordic routes) are in the same layer, 53.8 km of 142.5 km at Mount Hood (:86);
- BLM layer 7 republishes layers 4 and 5 and must be left out of a merge, or BLM's length doubles (:57).

## 2. How it draws apart from OSM trails

### What the map draws today

- Trail Map's trails are lines from the Protomaps `roads` source-layer with `kind=path`, styled by difficulty and visibility: footpaths `#a8452a`, hard (alpine) `#7a1e10`, tracks `#7a5530`, informal `#a0735a` dotted, each dashed differently (`server/pmtiles-worker/src/street-style-v2.json`, byte-identical to `map-style/forager-street-v2.json`, 15,351 lines; layer indices 40 to 57, read with a script; the design source is `map-style/overrides/forager-street.mjs:215-300`).
- The style has one source, `protomaps`, a vector TileJSON at the Worker's `/us.json` (`street-style-v2.json`, sources block, read).
- The app's own overlays (tracks, the route home, journal entries) are added by the app after the style loads, from GeoJSON sources (`A/ui/map/OverlayLayerSetup.kt:63-97`, 197 lines). The route ahead is `#0288D1` by day and `#4FC3F7` by night (`A/ui/map/RouteHomeLayers.kt:83-84`, 273 lines).

### Prior art (prior-art-before-interface skill)

Checked lightly; the findings are marked by how they were read.
- A leading backcountry GPS app offers agency map layers as optional extras over a base map. Its Forest Service layer is described on the vendor's own page as "Full, detailed topographic coverage of all 172 national forests and grasslands… includes labeled trails, roads" (`gaiagps.com/maps/source/usfs-2016/`, read through a summarising fetch, so the quote may be a close paraphrase).
- The Forest Service's own MVUM map service is drawn as an overlay on another map. Its documentation says the printed MVUM "is best used with a Forest Visitor Map or other detailed map" (from a search-result summary, not opened).
- The convention, then, is an agency layer that is optional, drawn over the everyday base map, and credited to the agency. No app was found that merges agency and community trails into one line. That matches -445.
- Not checked: how any app marks "the agency has a trail here and the community map does not". No prior art for gap-flagging was found. The proposal below is therefore a design, not a convention, and is marked as one.

### The proposed look (a design for the owner, not a ruling)

**A soft band under the trail line.** Each agency trail is drawn as a wide, faint band in its own hue, below the OSM trail lines, with the agency number as a small label along it from zoom 14.
- Where the two agree (most of the time, within about 3 m), the OSM trail sits inside the band, so it reads as one trail with an official halo.
- Where the agency has a trail and OSM does not, the band stands alone with no line inside it. That empty band is the gap, visible without a legend.
- Where OSM has a trail and the agency does not (social and bike trails, Eagle Cap), there is a line with no band, which is what it is.

**Colour.** It must not be confused with water (day fill `#80deea`, streams `#3fa0c8`; street-style-v2 lines 588 and 602 per the survey), the route home (blue `#0288D1`), forest green, or the brown and red trail family. A violet band (an example, not a pick: about `#7b5ea7` at 35% opacity, 6 to 10 dp wide) is the family nothing else on the map uses. Snow (nordic) trails are left out of the band by default; whether to show them at all is a question (Q4).

**Night.** Night on Trail Map is the V1 transform applied to every layer's paint colours after the style loads (`A/ui/map/MapStyleReload.kt:128`, `A/ui/map/OfflineNightRecolourApply.kt:29-66`, 108 lines). It inverts lightness and keeps hue (`A/ui/map/NightColour.kt:39-47`, 296 lines). The owner's named exceptions are kept as overrides: water (-1343) and the per-layer line and fill colours (`NightColour.kt:97-149`). If the band is a line layer inside the Trail Map style, it takes the same V1 recolour automatically, because the recolour walks every layer's `line-color` (`OfflineNightRecolourApply.kt:76-82`). That keeps -314/-322/-1288 "pure inversion" with no new code. Whether the inverted violet reads at night is device-only (§6). If it doesn't, a named night exception is the owner's to rule, as water was.

**80% map chrome.** The band is map content, not chrome. -1176's layer is data drawn on the map, like a trail, so the 80% rule (CLAUDE.md, "Nothing fully obstructs the map view") does not apply to it, and it is translucent anyway. What is chrome, and so sits at `MAP_CHROME_OVER_MAP_ALPHA` 0.8 (`A/ui/map/MapChrome.kt:69`), is the tap bubble and the layer card (§3, §5). They use the existing bubble and sheet surfaces, which already carry it (`MapLayersSheet.kt:256` uses `mapChromeFill(…, overMap = true)`).

### Where it lives: two architectures (a stop-and-ask, not picked)

**Option A, in the Trail Map style (recommended).** A second vector source, `reference`, and its layers are added to a new style version, `street-v3.json`, with the layers' `visibility` set by the app at style load from the user's switch.
- *Offline comes free.* An offline download is a MapLibre tile pyramid over the style URL (`A/map/MapLibreOfflineMapRepository.kt:139-146`, 494 lines; style from `offlineDownloadStyleUrl()`, `A/map/StreetStyle.kt:50`, 84 lines). MapLibre stores every source in the style, so the agency trails ride along in every new region download. The plan's "one download per area" (`trail-navigation-and-own-tiles.md:16`) holds with no new download path. The library behaviour is inferred from how tile pyramids work, not observed on the S22; it is a device check (§6).
- *Night comes free* (above).
- *Cost.* A new style URL. The offline store keys regions by style URL, which is why each design ships as a new file "beside it" (`StreetStyle.kt:18-20`). Regions downloaded under v2 keep drawing in v2 without the layer, and show the existing "older map style" notice (`StreetStyle.kt:75-84`, RECORD -1294/-1561). That is the precedent this follows.
- *Cost.* The Worker bundles the styles (`server/pmtiles-worker/src/index.ts:462-472`, 612 lines), so v3 is a Worker change in `server/`, which is in this repository. Who edits `server/` is the planner's call (see "What the app needs from the server side").

**Option B, an app overlay in the Layers registry.** A `MapLayerSpec` with a vector source added by the app after load, like the forecast cells (`A/ui/map/layers/MapLayers.kt:105-127`, 473 lines; `OverlayLayerSetup.kt:63-97`).
- It fits the existing switch, opacity and credit machinery as it stands.
- But offline breaks. A source the app adds at runtime is not in the style the region was downloaded against, so its tiles are not stored, and offline it would draw nothing. That is the empty-layer-that-looks-like-an-answer failure the Navigator plan names (`docs/navigation/forager-navigator-plan.md:178-181`). This is inferred from how the pyramid is defined, not observed.
- Fixing that means a second offline download path, which "one download per area" rules out.

**Recommendation: A**, with the switch, opacity and credit still in the Layers registry, as a new renderer kind that sets a style layer's visibility instead of owning a GeoJSON source. This is a new kind in an existing framework, so it is the owner's and planner's call, not a coder's.

## 3. How a gap is flagged

"Used to flag gaps" (-445) has three readings. The owner picks one.

| | What the walker sees | What it costs | Honest? |
|---|---|---|---|
| **G1. By sight (recommended first)** | An empty violet band. Tapped, a bubble reads "Forest Service trail 017 · Three Blaze. Not on the trail map, so routes can't follow it." | Nothing beyond §2. The bubble's second sentence is shown for every agency trail whose band has no OSM line inside. Telling that needs G2 or G3; without them the bubble says only what it is (below). | Yes. It shows two sources side by side and claims nothing. |
| **G2. Flagged by the server** | The same, plus a firmer style (for example a dashed edge) on segments the build found more than 15 m from any OSM path, and the bubble says so. | The build compares each agency segment with the OSM extract, Juniper's 15 m method (`trail-data-comparison.md:42`). **Licence question:** an agency attribute computed from OSM is a reference between the two datasets, which the Collective Database guideline lists as what keeps them independent (`licence-quotes.md:15`, "the non-OSM and OSM datasets do not reference each other"). It may make the reference layer an ODbL derivative. Inferred, not legal advice; it needs someone qualified before it is built. | Yes, if the licence question is settled. |
| **G3. Computed on the phone** | As G2. | Renderer feature queries depend on what is loaded at the current zoom, so the answer would change with zoom. The Navigator plan rules out that kind of answer for data questions (`forager-navigator-plan.md:250-256`). | Not reliably. Not recommended. |

**The bubble without G2.** Tapping a band: "Forest Service trail 001 · Chamberlain · Class 3 · Stock: 01/01 to 12/31", from the agency's own fields (`trail_class`, `pack_saddle_managed`; `trail-data-comparison.md:103`), then "Official record. Routes follow the trail map, not this layer." That second line states the -445 rule plainly, so a walker never thinks a route ignored a trail by mistake.

**Taps reach no basemap feature today.** Taps query only the app's overlay layers (`A/ui/map/fanout/MapTapHandler.kt:92-96`, 248 lines; `queryRenderedFeatures` per overlay layer at `A/ui/map/FanOutLayers.kt:347-359`, 380 lines). A tap on the reference band is a new tappable id in that path and a new bubble kind beside Find, Photo and the rest (`A/ui/map/MapFeatureBubble.kt`, 244 lines). Tap precedence is markers, then lines, then colour fields (`A/ui/map/layers/TapPrecedence.kt`, 75 lines). The band is a line under every app line, so it should lose to tracks and the route.

**"No agency data here" is not "no official trails here".** In a forest that publishes no geometry (Eagle Cap), the layer draws nothing. Under the Navigator plan's rule that "unavailable reads as unavailable" (`forager-navigator-plan.md:178-191`), the layer card says which forests publish nothing. That list comes from the server (§"What the app needs").

## 4. The offline story

- **New downloads (Option A):** the agency tiles are stored with the map tiles in MapLibre's own offline database (`A/map/MapLibreStorage.kt:3-17`), in the same download, and their bytes count in the size the row shows (`OfflineRegionSummary.sizeBytes`, `A/domain/OfflineMapRepository.kt:252-253`, 273 lines; `MapLibreOfflineMapRepository.kt:205`).
- **The tile budget.** The app's own 6,000-tile ceiling and 24 km radius were sized against one source's tile count (`OfflineMapRepository.kt:135`, :200-237). The native limit set beside it is "a defensive floor, not relied on" (:119-121). A second source adds tiles MapLibre fetches and stores that the app's estimate does not count. They are mostly empty tiles away from federal land, which the Worker answers with no content (the world-fill comment, `index.ts:274-282`, states 204 for a missing tile). Whether the 6,000 budget is meant to count them is a question for the owner (Q6); that it does not today is read from the estimate's code (`A/domain/EstimateOfflineTileCount.kt:44-48`, 69 lines).
- **Size per region:** not measured. Inferred small. Juniper's boxes hold 0 to 142 km of agency line per 100 km², against 50 to 225 km of OSM path, so agency tiles should be a fraction of the basemap's. A real figure needs one region downloaded on the S22 (§6).
- **Existing regions** keep v2 and show no agency trails until re-downloaded, with the existing older-style notice.
- **No signal** draws Trail Map from downloads (`A/ui/map/NoSignalTrailMap.kt:53-59`, 140 lines), so the layer appears there exactly as in the downloaded style.

## 5. Step paths for the owner

Sketches for the owner to confirm or change. Wording is a draft.

**Turning it on.**
1. Map → tap the Layers button → the sheet shows "Map type" (Trail · Topo · OSM), then "Overlays".
2. Under Overlays: a row "Official trails" with the line "Forest Service and BLM" and a switch. (Default on or off is Q1.)
3. Tap the switch → on the map, official trails gain a faint violet band from zoom 12. The caption at the bottom of the map adds "Trails: USDA Forest Service, CC BY 4.0 · BLM".
4. Back → closes the sheet. The map stays as it is. The switch survives a tab change and an app restart, like the other overlays (`DataStoreMapPreferencesRepository.kt:128-132`, keys `map.layer.<id>.visible`).

**Reading a gap.**
1. Walking in a wilderness → the map shows a violet band with no trail line in it.
2. Tap the band → a bubble at 80%: "Forest Service trail 017 · Three Blaze", then "Official record. Routes follow the trail map, not this layer."
3. Back (or tap elsewhere) → the bubble closes.
4. Tap Return or navigate to a waypoint → the route follows OSM trails and roads only, never the band.

**Learning what it is.**
1. Layers sheet → tap the (i) beside "Official trails" → a card: what it shows; "Where the agency and the trail map agree, the trail sits inside the band. A band with no trail is an official trail the trail map lacks"; its limits ("Some forests publish no trail lines. Seasons are planned seasons, not closures. No live closures"); the sources and dates.
2. Back → returns to the Layers sheet.

This is the owner's "how to read this" guide (RECORD -790), applied to the app's first reference layer.

**Offline.**
1. Download an area → its size includes the official trails.
2. Aeroplane mode → the band shows exactly as online.
3. An area downloaded before this change → no band, and the area's row says it uses an older map style (the existing notice).

## 6. Device-only items

A green suite is not evidence for any of these:
1. MapLibre Native 13.5.0 stores the second source's tiles in a region download (inferred).
2. The band's colour by day, and its V1-inverted colour at night, against forest, water and the brown and red trails on the S22.
3. A tap on the band (a 6 to 10 dp line) by a real finger, at several points across it (CLAUDE.md, "A semantic `performClick` asserts wiring, not routing").
4. The size an agency source adds to a 24 km region download.
5. Aeroplane-mode drawing of the band.

## 7. What the app needs from the server side (for the owner to take to Aspen)

The app can be built against a small synthetic test archive before any of this exists. Positions are invented, so nothing real enters the repository.

1. **One vector archive**, for example `reference-trails.pmtiles`, on R2:
   - **Content:** Forest Service NFS trails (`TrailNFS_Publish`) and BLM GTLF public managed trails (layers 2 to 6 only; layer 7 left out, `trail-data-comparison.md:57`), as **two source-layers**, `fs_trails` and `blm_trails`, never conflated with OSM and never joined to it.
   - **Attributes kept (as published):**
     - Forest Service: `trail_name`, `trail_no`, `trail_class`, `trail_type` (to tell snow trails apart), the managed-use season fields where the forest publishes the `MGMT` subset, the forest's admin unit and `attributesubset`.
     - BLM: route name, `FAMS_ID`, allowed modes, season and access restriction codes, `ACCURACY_FT`.
     - Field names are as Juniper read them (`trail-data-comparison.md:89, 103, 110`). The exact list is for Aspen to confirm from the services.
   - **Zooms:** 10 to 15, matching the offline range (`OfflineMapRepository.kt:159-160`), with no simplification that moves a line more than about a metre at z15. Above 15 the app overzooms.
   - **Size:** unknown. The Forest Service national trails geodatabase is 119 MB (`docs/navigation/2026-10-03-trail-navigation-research.md:46`, read from that document, not re-measured). The archive is inferred to be smaller than the input; a measured figure is the first number to ask for.
   - **Refresh:** on change, not weekly. The Forest Service updates "Irregular" (metadata, read today), and BLM last updated 2026-10-13 (catalogue, read today). Checking the modified dates before each weekly run is enough, as the land research proposed for land data (`docs/audits/2026-10-11-land-ownership-research.md:239`, branch `land-ownership-research`).
2. **Served by the Worker at `/reference-trails/{z}/{x}/{y}.mvt` and `/reference-trails.json`.** The Worker already maps any `/{name}/{z}/{x}/{y}.{ext}` and `/{name}.json` to an R2 object `{name}.pmtiles` (`server/pmtiles-worker/src/shared.ts:6-41`; `index.ts:482`). Whether that generic path works for a second archive with no code change is inferred, not tested: the overflow and world-fill branches name `us` and `world-south` (`index.ts:132-294`).
3. **A coverage list:** which national forests publish no trail geometry, or only the `Centerline` subset, so the layer card can say where the layer is silent. The per-forest subset is in the service's `attributesubset` field (`trail-data-comparison.md:198`, `licence-quotes.md:23`).
4. **A build manifest** like the route squares' (`{run_id, …, files[{path, size, sha256}]}`, Forager-cartography `docs/audits/2026-10-23-route-squares-upload.md:33`, read as a premise from Aspen's repo), recording each source's own last-updated date. The layer card shows it as "Forest Service data from …".
5. **Questions only Aspen can answer:** whether the Pi's toolchain can read the two ArcGIS services or their bulk downloads (Planetiler is installed; GDAL is not: Forager-cartography `docs/design/2026-10-22-topo-sizing.md:61-67`, a premise); disk and time for the build; and whether this joins the weekly unit or runs on its own.

## 8. Proposed PRs, in order

Each is its own dispatch on the owner's go, and each answers the modular-code skill.

| PR | Does | Files (lines now) | Needs the server? |
|---|---|---|---|
| **8a. Style v3 with a hidden reference source** | `street-v3.json` generated with the `reference` source and band layers, visibility none; the Worker serves `/style/street-v3.json`; `StreetStyle.kt` points new downloads and online Trail Map at v3; the older-style notice covers v2. | New `map-style/overrides/forager-reference.mjs` (new, under 200); `map-style/generate-street.mjs` (81); new `server/pmtiles-worker/src/street-style-v3.json` (generated, exempt as a generated file); `server/pmtiles-worker/src/index.ts` (612); `A/map/StreetStyle.kt` (84); Worker tests `test/worker.test.mjs` (388). | Built and tested against a synthetic fixture; visible only once the archive exists. |
| **8b. The switch, the credit and night** | A new renderer kind in the registry that toggles style layers; the Overlays row; DataStore visibility; the caption credit; the Data sources rows. | `A/ui/map/layers/MapLayers.kt` (473); new `A/ui/map/ReferenceTrailsVisibility.kt` (new, under 120: applies visibility in the style-load callback); one call in `A/ui/map/MapStyleReload.kt` (177); `A/ui/map/MapLayersSheet.kt` (544); `A/ui/credits/DataSourceCredits.kt` (80). | No. |
| **8c. Tap a band** | The band's layer ids become tappable; a new bubble kind; the attribute reader in `domain/`. | New `A/domain/referencetrails/ReferenceTrailRecord.kt` (new, under 200: agency fields to plain lines, headless-tested); `A/ui/map/fanout/MapTapHandler.kt` (248); `A/ui/map/MapFeatureBubble.kt` (244); `A/ui/map/MapBubbles.kt` (486). | No (fixture). |
| **8d. The layer card** | The (i) card from §5, with the coverage list and source dates. | New `A/ui/map/layers/ReferenceLayerCard.kt` (new, under 250); `MapLayersSheet.kt` (544). | Needs item 3 (coverage) and item 4 (dates) for real content. |

**The modular-code skill's four questions:**
1. Files touched and sizes: listed in the table, read with `wc -l` at `f5a0cfafc`.
2. Over about 800 lines with the change: none. The largest touched are `index.ts` (612) and `MapLayersSheet.kt` (544). `SightingsMap.kt` (759) is not touched: the style-load callback lives in `MapStyleReload.kt`.
3. New files, one job each: the reference override (style layers), `ReferenceTrailsVisibility.kt` (apply the switch to the style), `ReferenceTrailRecord.kt` (read the agency fields), `ReferenceLayerCard.kt` (the card).
4. Order that must stay put: the visibility call goes after the night recolour and before `initializeOverlayLayers` in the style-load callback (`MapStyleReload.kt:128-140`), so the band is recoloured and sits below the app's overlays. The band layers sit below the OSM trail layers in the style's own order (paint order). Both are positions, so both get a phone check.

8b and 8c touch different files and could run side by side. 8a goes first.

## 9. Questions for the owner

Q1. **Default:** is "Official trails" on or off for a new user? Recommendation: **on**, because it only draws where agencies publish, from zoom 12, and a walker should not have to find a switch to see an official trail the routes can't use ("Don't make me think"). Off means only users who look for it ever see a gap.
Q2. **Where it lives:** Option A, in the Trail Map style (recommended; offline and night come free), or B, an app overlay (no offline).
Q3. **Gap flagging:** G1 by sight now (recommended), and whether G2 is worth a licence reading.
Q4. **Snow trails** (nordic routes in the Forest Service layer): hidden, or shown in the winter months, or always?
Q5. **Topographical and OpenStreetMap:** the band is in the Trail Map style, so it shows only on Trail Map. Should it also draw over Topo and OSM, as an app overlay there (online only)?
Q6. **The tile budget:** should the 6,000-tile budget count the agency source's tiles, which shrinks the 24 km maximum, or stay a budget for the basemap alone?

## Disclosure

**Confirmed (read today):** the licences and dates in §1; every app and Worker citation in §§2 to 8, at `f5a0cfafc`; the style's trail layers and source; that taps reach only overlay layers; that night is a per-layer V1 recolour that walks `line-color`; that MapLibre 13.5.0 is the pinned version (`gradle/libs.versions.toml:43`).

**Inferred, not observed:**
- that a region download stores a second style source;
- that the Worker's generic path serves a second archive unchanged;
- the agency archive's size;
- the licence effect of G2;
- that the band is readable by day and night.

**Could not determine:**
- the archive's real size and build time;
- which forests publish no geometry, nationally;
- whether the inverted band colour reads at night;
- whether any established app flags gaps between agency and community trails.

**Premises that were wrong or have moved:**
- "Night stays V1 inversion (-314, -322)": -314 and -322 rule the Topo raster. Trail Map's night is the same V1 formula applied per layer (-1288), with named exceptions since (water -1343, the ruled forest and line colours, `NightColour.kt:97-149`). The reference band would take plain V1, so the intent holds, but the rulings that govern it on Trail Map are -1288 and its exceptions.
- BLM's dataset date has moved since Juniper's quote (September 22 to October 13, 2026). The licence is unchanged.
- The plan of record's "Attribution in the app … BLM's line is optional" (`trail-navigation-and-own-tiles.md:53`) stands. The app has no per-source caption mechanism beyond `MapLayerSpec.credit` (`MapLayers.kt:119`; `activeLayerCredits`, `A/ui/map/layers/MapLayerState.kt:80-84`), which is what 8b uses.
- `MainActivity.kt` is 736 lines and `AvailabilityScreen.kt` 1,023 (read today), against the plan update's 806 and 1,324 (`2026-10-21-trail-navigation-plan-update.md:34-36`). Neither is touched here.

**Decided beyond scope:** nothing. The look, the default, the architecture and the gap reading are proposals, each put to the owner above. `docs/audits/README.md` (a serialization point) was not touched; the planner adds the index row.
