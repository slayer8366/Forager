# Step 11, land context (Navigator stage I): report before building (dispatch 2026-10-25-05, part 3)

**Status: a report. Nothing is built.** No app code, no Gradle, no phone. Written by an app coder (Opus) on 2026-10-25 (UTC) for Rowan Waymark, under RECORD -1629.

**Base:** `pre-main` `f5a0cfafc6e44f2bb3843d90d04807db904cc1ca`, `records-after-173` `2b536f576`, both checked with `git ls-remote origin` before reading. `A/` is `app/src/main/java/com/zynergylabs/forager/app/`. Line counts are `wc -l` at `f5a0cfafc`. **This is a claim about the past;** the build dispatch re-reads it.

**What the step is.** Navigator stage I: "Public-land ownership, forest roads and trails, designations, layer cards, "what am I standing in"". It is blocked by "A server-side vector pipeline that does not exist. It would draw through the map-layers framework. The plan's private-parcel data is a paid dataset and stays out unless the owner says otherwise" (`docs/navigation/2026-10-01-navigator-completion-plan.md:183`). It was folded into trail navigation as step 11 (RECORD -1498, `docs/plans/2026-10-21-trail-navigation-plan-update.md:87-90`).

This report sits on two earlier pieces of work, read today and taken as premises:
- the Navigator plan's §3, "Layers, coverage, and scope", and §9 (`docs/navigation/forager-navigator-plan.md:158-195, 403-415`);
- the land-ownership research (`docs/audits/2026-10-11-land-ownership-research.md` on branch `land-ownership-research`, 77252a825, never merged), dispatched by the owner's -880, -882 and -883.

---

## 1. The owner's word on this, and what is still open

- **-880 (2026-10-11), verbatim:** "I want to do property ownership and private properties to guide people away from those areas."
- **-882:** for "guide people away", the owner selected all four: "Show it on the map", "Warn on entering", "Routes avoid it", "Let the research inform it".
- **-883, verbatim:** "Be careful on the warning upon entering item, look for more info that comes back. I can see that triggering by GPS being slightly off and walking on the sidewalk by someone's house."
- **The research's options** (`land-ownership-research.md:246-277`):
  - A, public land only;
  - B, public land plus "Private, owner not shown" in Oregon and Washington, map only;
  - C, B plus routing that penalises off-road travel through private land;
  - D, a narrow "well inside private land" notice, after a walk test;
  - E, a picking-rules lookup.
  - Its recommendation: "B now, C next, D only after a walk test on real lots, and E as a separate decision".
- **No ruling on those options is in RECORD.** A search of RECORD.md for "private land", "owner not shown", "land-ownership" and "PAD-US" finds only -880, -882 and -883. So the owner asked for private property (-880) and has not chosen among the research's options.

**This changes the dispatch's premise.** "The plan keeps paid private-parcel data out unless the owner says otherwise" is true of *paid* parcels. But the research found a *free* source of private-land areas, with no owner names, for Oregon and Washington: BLM Oregon/Washington Management Ownership, 269,446 polygons, 117,516 private, read from the live service on 2026-10-11 (`land-ownership-research.md:25, 49`). So private land is possible without the paid dataset, and the owner has asked for it. Which option is the owner's call (Q1).

## 2. What exists in the app today

All read at `f5a0cfafc`:
- **Protected-area outlines from OSM, already drawn.** Trail Map's `landuse_park_outline` layer draws OSM `national_park`, `park`, `protected_area` and `nature_reserve` outlines in green `#3e7a3a` from zoom 6 (`server/pmtiles-worker/src/street-style-v2.json`, layer index 6, read with a script). That is OSM's mapping, not an agency's, and it carries no manager or access.
- **No agency land data.** No PAD-US, MVUM, Forest Service, BLM or NHD reference in app code, `map-style/` or the Worker.
- **No point-in-polygon code.** Containment today is a rectangle (`GeoBoundingBox.contains`, `A/domain/routedata/RegionRouteData.kt:190-193`, 193 lines) or a tile range (`A/domain/OfflineTileMembership.kt`, 43 lines). `GeoDistance` (193 lines) measures and draws rings, nothing more.
- **The overlay framework** carries what layer cards need:
  - a per-layer credit added to the map caption while the layer shows (`MapLayerSpec.credit`, `A/ui/map/layers/MapLayers.kt:119`, 473 lines; `activeLayerCredits`, `A/ui/map/layers/MapLayerState.kt:80-84`);
  - a switch, opacity and order saved in DataStore under `map.layer.<id>.*` (`A/data/repository/DataStoreMapPreferencesRepository.kt:126-132`, 207 lines);
  - a legend for the forecast fields (`A/ui/map/layers/MapLegend.kt`, 81 lines).
  - Nothing like a layer card exists yet.
- **No readiness state** of the "available / partial / not downloaded / not covered" kind exists for map layers. The closest are the route data states (`RegionRouteData`, `RegionRouteData.kt:18-63`; per point `RouteDataForPoint`, `A/domain/routedata/FindRouteDataForPointUseCase.kt:42-57`, 57 lines) and the region row's "Not downloaded" (`A/ui/availability/OfflineRegionsList.kt:267`, 288 lines).
- **No long-press consumer.** The map reports long-presses, but both production call sites pass `{}` (`A/ui/availability/CompactMapMapSlot.kt:126`; `A/ui/map/SightingsMap.kt:77-83`, 759 lines). A point is picked by panning under the centre pin (`A/ui/map/CentrePinLocationPicker.kt`, 508 lines). That shapes "what am I standing in" (§5).
- **Tapping basemap features is not possible** today. Taps query only the app's overlay layers (`A/ui/map/fanout/MapTapHandler.kt:92-96`, 248 lines).

## 3. Sources and licences, read at source today

Fetched 2026-10-25 between 06:25 and 06:33 UTC into `~/.cache/later-steps-scratch/web/`. Catalogue and metadata pages only; nothing downloaded.

| Source | For | Licence, quoted | Dates, quoted | Read from |
|---|---|---|---|---|
| **PAD-US 4.1** (USGS Gap Analysis Project) | public land, managers, access, designations | `rights`: "Public Domain. Unless otherwise stated, all data, metadata and related materials are considered to satisfy the quality standards…" | Publication 2025-03-31 | `sciencebase.gov/catalog/item/652d4fc5d34e44db0e2ee45e?format=json` |
| **Forest Service, National Forest System Roads** | forest roads | "Access & Use License https://creativecommons.org/licenses/by/4.0/" | "Dataset Last Updated October 01, 2024". Road Core metadata `pubdate` 20250514, `update` "Daily". | `catalog.data.gov/dataset/national-forest-system-roads-feature-layer`; `…/meta/S_USA.RoadCore_FS.xml` |
| **Forest Service, Motor Vehicle Use Map: Roads** | which forest roads are open to which vehicles, and when | "Access & Use License https://creativecommons.org/licenses/by/4.0/" | "Dataset Last Updated August 25, 2022". Metadata `pubdate` 20250513, `update` "As needed". The use constraint adds: "These geospatial data and related maps or graphics are not legal documents and are not intended to be used as such." | `catalog.data.gov/dataset/motor-vehicle-use-map-roads-feature-layer`; `…/meta/S_USA.Road_MVUM.xml` |
| **Forest Service and BLM trails** | trails | As in the step 8 report §1. | | |
| **BLM National Surface Management Agency** | who manages the surface | `licenseInfo`: "These data are provided by Bureau of Land Management (BLM) "as is"…", "These data are neither legal documents nor land surveys, and must not be used as such", "The BLM should be cited as the data source in any products derived from these data. Any Users wishing to modify the data should describe the types of modifications they have performed." | Item modified 2026-06-30 | `arcgis.com/sharing/rest/content/items/6bf2e737c59d4111be92420ee5ab0b46?f=json` |
| **BLM Oregon/Washington Management Ownership** | private land areas, OR and WA, no names | **Could not be re-read today.** The layer's service answered `{"error":{"code":503,"message":"User couldn't access this resource 'land_status/blm_or_ownership.mapserver'."}}` at about 06:32 UTC. The research read it on 2026-10-11 (`land-ownership-research.md:49`). | | `gis.blm.gov/orarcgis/rest/services/Land_Status/BLM_OR_Ownership/MapServer/0?f=pjson` |

**What the licences ask of the app** (not legal advice):
- **Forest Service roads and MVUM, CC BY 4.0:** attribution, the licence, and a note of changes, as for the trails.
- **BLM:** "should be cited", and modifications described. Tiling and simplifying is a modification, so the Data sources row carries a one-line note (`A/ui/credits/DataSourceCredits.kt:56-65` is the existing pattern).
- **PAD-US:** nothing required. Credited anyway: "Public land: USGS Protected Areas Database of the United States (PAD-US) 4.1, public domain".

**What the data can and cannot say** (from the research, read there):
- PAD-US is unsuitable at "scale finer than 1:24,000" and for "Representing boundaries as a legal representation".
- "**Not a parcel map: a gap in PAD-US does not mean private.**" (`land-ownership-research.md:47`).
- Picking rules "can't honestly be read from land type" (:30, :149).

## 4. What each part needs

| Part | Server pipeline? | App side |
|---|---|---|
| **Public-land ownership** (PAD-US: manager, type, unit name, public access) | Yes: a vector archive of polygons. | A tinted fill by manager type, with outlines, drawn in the Trail Map style like step 8's Option A, so offline and night come free. |
| **Designations** (wilderness, refuge, park, monument) | The same archive. PAD-US's Designation layer carries them (`land-ownership-research.md:47`). | Outlines and labels; the unit's name in "what am I standing in". |
| **Forest roads** (Forest Service roads, MVUM) | Yes, but it fits step 8's agency archive as more source-layers. | A reference line like the agency trails, never routed (-445's rule for agency data, applied to roads). The MVUM attributes (vehicle types, seasons) answer "is this road open to my car this month". |
| **Forest trails** | Step 8. | Step 8. |
| **Layer cards** | A coverage and date manifest. | One card component for every reference layer, step 8's included (§6). |
| **"What am I standing in"** | Depends on the option (§5). | Point-in-polygon in `domain/`, not the renderer (`forager-navigator-plan.md:250-256`). |
| **Private land** (Q1) | BLM OR/WA ownership for B. Paid parcels stay out. | A grey "Private land, owner not shown" fill in Oregon and Washington only, softened at close zoom (`land-ownership-research.md:211-215`). |

**Combine step 8's and step 11's server output into one style change.** Each Trail Map style change is a new style URL, and regions downloaded under the old one keep it until re-downloaded (`A/map/StreetStyle.kt:18-20, 75-84`, 84 lines). If step 8 ships v3 and step 11 ships v4, walkers re-download twice. One agency archive (trails, roads, MVUM) plus one land archive, both added in a single style version, costs them one re-download. That would move step 11's server work forward to step 8's time. It is a sequencing question for the owner and planner (Q6).

## 5. "What am I standing in": three ways (a stop-and-ask)

The Navigator plan's rule: "'Which management unit contains this coordinate' is a data question whose answer must not change because the user zoomed out", so it is hand-rolled in `domain/` (`forager-navigator-plan.md:250-256`).

| | How | For | Against |
|---|---|---|---|
| **W1. From the drawn tiles** | `VectorSource.querySourceFeatures` (present in 13.5.0: `javap` on the cached AAR), then point-in-polygon in `domain/` | No new data path. | The tiles loaded depend on the zoom, and their shapes are clipped and simplified per zoom, so the answer near a line can change with zoom. That is what the rule above forbids. **Not recommended.** |
| **W2. From one fixed-zoom tile** | The app reads the land archive's z12 tile under the point (from the Worker online; from its own copy per area offline, stored like the route files under `filesDir/offline_routes/<regionId>/`, `A/data/routedata/RegionRouteFolders.kt:92`), decodes it in `data/` and tests the point in `domain/`. | The same answer at every zoom; offline. | Needs a vector-tile decoder. None is in the version catalogue today (`gradle/libs.versions.toml`, searched for protobuf, vector-tile, mvt, wire and jts: no hits). A pinned dependency is a decision under CLAUDE.md, Building. |
| **W3. From a per-area file (recommended to ask Aspen first)** | The Worker serves the land units for an area as GeoJSON clipped to it, like the routing `area.json` (`A/data/routedata/RouteAreaClient.kt:21, 40`, 245 lines). The app stores it with the region's route files and tests points in `domain/`. | The same answer at every zoom; offline; parsing GeoJSON needs no new dependency (the MapLibre GeoJSON classes are already in use). One download per area, beside the routes. | A new Worker endpoint and a server-side cut. |

**The honest answer near a line.** GPS beside buildings is off by 4 to 10 m, and one fix in three falls outside the radius the phone reports (`land-ownership-research.md:33, 170-176`). PAD-US lines are not survey lines. So the readout has three states:
- inside, by more than the fix's accuracy plus a margin: "On: Mt. Hood National Forest · Forest Service";
- near a line: "Near a boundary: Mt. Hood National Forest or unknown land";
- no unit: "Not known to be public land". Never "private" from PAD-US alone (§3).

Each states that ownership is not permission (the Navigator plan's "Ownership boundary ≠ permission to enter or harvest, as non-negotiable wording", `forager-navigator-plan.md:409-410`).

## 6. Layer cards

The plan's list: "Layer cards carrying source agency, dataset version, coverage, last update, and known limitations" (`forager-navigator-plan.md:411-412`). The owner's guide shape for reference layers (-790): what it shows, why a forager would care, its limits, "and no hidden scoring". One card component serves every reference layer.

A draft card for public land:
- **Shows:** land managed by federal, state and local agencies, from USGS PAD-US 4.1.
- **Why it matters:** each agency has its own picking rules. The card links to the managing unit's page; it does not state the rules (the research's finding, `land-ownership-research.md:149-157`).
- **Limits:** "Lines are approximate, not legal boundaries. A gap does not mean private. Ownership is not permission. Check the unit's rules before you pick."
- **Coverage:** "United States only". Outside it, the layer's row reads "Not available here" rather than drawing empty (`forager-navigator-plan.md:178-191`).
- **Source and date:** "PAD-US 4.1, published 2025-03-31".

Cards sit on the Layers sheet, so they are map chrome at 80% (`A/ui/map/MapChrome.kt:69`; the sheet already uses `mapChromeFill(…, overMap = true)`, `A/ui/map/MapLayersSheet.kt:256`, 544 lines).

## 7. Step paths for the owner

**Public land on the map.**
1. Map → Layers → under Overlays, "Public land" with a switch.
2. Switch on → land managers are tinted (Forest Service, BLM, state, parks, each its own colour at low strength), with outlines. The caption adds "Public land: USGS PAD-US".
3. Back → closes the sheet. The switch is remembered across tabs and restarts.

**What am I standing in** (where it lives is the same choice as terrain's slope readout, terrain report §6: (a) the walker's spot, or (b) a picked point):
1. (a) Tap the compass and elevation strip → it opens "On: Mt. Hood National Forest · Forest Service · Mount Hood Wilderness" and "Ownership isn't permission. Check the forest's rules", with a link to the unit's page.
2. Near a line → "Near a boundary: Mt. Hood National Forest or unknown land".
3. Outside every unit → "Not known to be public land".
4. Tap again, or Back → it closes.

**A layer card.**
1. Layers → the (i) beside "Public land" → the card (§6).
2. Back → the Layers sheet.

**Forest roads** (with step 8's agency layer):
1. Agency layer on → forest roads gain the same violet band as agency trails.
2. Tap a road → "Forest Road 2618 · Open to highway vehicles 05/15 to 10/31 (Motor Vehicle Use Map)".
3. Back → the bubble closes. Routes still follow the trail map's roads only.

**Private land** (only if the owner picks B, Q1):
1. "Public land" on → in Oregon and Washington, private land is a soft grey with "Private land · owner not shown · lines approximate" in its card.
2. No alert on entering. The research's option D is a separate decision after a walk test (-883).

## 8. What the app needs from the server side (for the owner to take to Aspen)

The app can be built against small synthetic archives before any of this exists, as the Worker's tests already do (`server/pmtiles-worker/test/fixtures/make-tiny-pmtiles.py`). Positions are invented.

1. **`land.pmtiles`:** PAD-US 4.1 polygons, Fee and Designation (and Easement, if wanted), as source-layers.
   - Attributes: manager name and type, unit name, designation type, public access ("Open", "Restricted", "Closed", "Unknown", `land-ownership-research.md:47`), and GAP status. Exact field names are for Aspen to state from the data.
   - Zooms 6 to 14, overzoomed above.
   - Size: a third party's national PAD-US archive at z0 to z14 is 3,636,293,548 bytes by HTTP HEAD (`land-ownership-research.md:237`). Ours is inferred to be of that order nationally, and a few MB per offline area (not measured).
   - Refresh: when PAD-US releases. "Annually" by its catalogue, 1 to 4 years in practice (:47). Not weekly.
2. **Forest Service roads and MVUM** as source-layers in step 8's agency archive, with the MVUM vehicle-class and season fields. Refresh: Road Core updates "Daily" and MVUM "As needed" (metadata, read today), so a weekly date check is enough.
3. **For private land (only if Q1 is B):** BLM Oregon/Washington Management Ownership's private classes (PV, PVI, PVN, PVU), no names. Today its service answered 503 (§3). The research could not find a bulk download (`land-ownership-research.md:292`). Whether Aspen can get it, and how, is a question for Aspen.
4. **For "what am I standing in", if W3:** a Worker endpoint returning the land units for an area as GeoJSON, clipped to it, like `routing/area.json` (`server/pmtiles-worker/src/routing/handler.ts:4-8`, 261 lines), with a size cap like the routes' 64 MB (`A/data/routedata/RouteAreaLimits.kt:19`, 24 lines).
5. **A coverage and date manifest** for every reference archive (each source's own date, its coverage), which the layer cards show.
6. **Questions for Aspen:**
   - polygon tiling on the Pi: Planetiler reads GeoPackage (`land-ownership-research.md:240`), and GDAL is not installed (Forager-cartography `docs/design/2026-10-22-topo-sizing.md:61-67`, a premise);
   - R2 space for a national land archive of about 3.6 GB;
   - whether W3's per-area cut belongs in the Worker beside the routes.

## 9. Proposed PRs, in order

| PR | Does | Files (lines now) | Needs the server? |
|---|---|---|---|
| **11a. Public land in the style** | Land source and layers in the same new style version as step 8 (Q6), hidden by default; the switch row; credits. | `map-style/overrides/` (new `forager-land.mjs`, under 250); `map-style/generate-street.mjs` (81); `server/pmtiles-worker/src/index.ts` (612) for the style route; `A/map/StreetStyle.kt` (84); `A/ui/map/layers/MapLayers.kt` (473); `A/ui/map/MapLayersSheet.kt` (544); `A/ui/credits/DataSourceCredits.kt` (80). | A synthetic fixture to build; item 1 to show. |
| **11b. Layer cards** | One card for every reference layer; "Not available here" outside coverage. Extends step 8's 8d card rather than adding a second. | `A/ui/map/layers/ReferenceLayerCard.kt` (8d's, under 250); new `A/domain/layers/LayerCoverage.kt` (new, under 120: in or out of a layer's stated extent). | Item 5 for real content. |
| **11c. What am I standing in** | `domain/` point-in-polygon with the three states; the data path per W2 or W3; the readout in the strip or the picker (the same place as terrain's 10d). | New `A/domain/land/PointInPolygon.kt` (new, under 150); new `A/domain/land/LandUnitAt.kt` (new, under 200: the three states with GPS accuracy); new `A/data/land/LandAreaClient.kt` (new, under 250, behind a domain interface); `A/ui/availability/CompassElevationStrip.kt` (150) or `A/ui/map/CentrePinLocationPicker.kt` (508). | Item 4 (W3), or a decoder decision (W2). |
| **11d. Forest roads** | MVUM and Forest Service roads as more bands in step 8's agency layer, with the road bubble. | Step 8's `forager-reference.mjs` and `ReferenceTrailRecord.kt`, under step 8's ownership if it is still open. | Item 2. |

**The modular-code skill's four questions:**
1. Files touched and sizes: in the table, `wc -l` at `f5a0cfafc`.
2. Over about 800 lines with the change: none today. `MapLayersSheet.kt` (544) gains rows in 8b and 11a. Two rows and a card button are inferred to stay well under 800; the build dispatch re-measures after 8b.
3. New files, one job each: the land style override; `LayerCoverage.kt` (in or out of an extent); `PointInPolygon.kt` (geometry only); `LandUnitAt.kt` (the readout's three states); `LandAreaClient.kt` (fetch and store an area's land units).
4. Order that must stay put: the land fill draws below trails, roads and labels and above landcover in the style's paint order; the band of step 8 sits above it. Both get a phone check.

11c touches no file of 11a or 11b and could run beside them. 11d waits for step 8.

## 10. Device-only items

1. The land tints by day, and their V1 night inversion, over forest and water on the S22. Fill colours are recoloured by the existing walk (`A/ui/map/OfflineNightRecolourApply.kt:76-82`).
2. The readout's three states on a real walk near a forest boundary, with the phone's real accuracy.
3. The size the land archive adds to an area download.
4. Whether the extra fills slow the map on the S22.

## 11. Questions for the owner

Q1. **Private land:** the research's options A (public only), B (plus "Private, owner not shown" in Oregon and Washington, map only), C (B plus routes that avoid it), D (a narrow notice, after a walk test), E (picking rules). The research recommends B now. You asked for private property in -880. Which, if any, for this step?
Q2. **Default:** "Public land" on or off for a new user?
Q3. **What am I standing in:** W3, a per-area file from the server (recommended, if Aspen agrees), or W2, a fixed-zoom tile with a new decoder dependency?
Q4. **Where the readout lives:** the strip at the top (where I stand) or the picker (a point). One answer could serve both this and terrain's slope.
Q5. **Forest roads:** in step 8's agency layer (recommended), or their own switch?
Q6. **Sequencing:** ship step 8's and step 11's map data in one style version, so walkers re-download areas once? That moves step 11's server work forward to step 8's time.

## Disclosure

**Confirmed (read today):**
- the licences and dates in §3, except BLM OR/WA (503);
- every app and Worker citation at `f5a0cfafc`;
- that the app has no point-in-polygon, no layer card, no long-press consumer and no layer readiness state;
- the OSM protected-area outline already in Trail Map;
- `querySourceFeatures` on `VectorSource` in 13.5.0.

**Inferred, not observed:**
- the land archive's size, nationally and per area;
- that the fills recolour acceptably at night;
- that a single style version is cheaper for walkers than two (from how regions key on style URLs, not measured).

**Could not determine:**
- the BLM Oregon/Washington ownership service's state (503 today) and a bulk source for it;
- PAD-US's exact field names (not read; the build reads them from the data);
- whether PAD-US has a release after 4.1 (only the 4.1 item was read).

**Premises that were wrong or have moved:**
- "The plan's private-parcel data is a paid dataset and stays out unless the owner says otherwise." Paid parcels stay out, but the owner has asked for private property (-880), and the research found a free, nameless private-land source for Oregon and Washington. The question is open (Q1), not closed.
- The Navigator plan's catalogue lists "USFS roads, trails, MVUM" under land context (`forager-navigator-plan.md:166-170`). Trails are now step 8's, so step 11's roads belong in the same agency archive.
- "It would draw through the map-layers framework" (`navigator-completion-plan.md:183`). The framework draws app-owned GeoJSON overlays (`A/ui/map/OverlayLayerSetup.kt:63-97`, 197 lines). Drawing land through it would lose offline drawing (the step 8 report, §2, Option B). The proposal draws through the style and keeps the framework for the switch and the credit.

**Decided beyond scope:** nothing. Every option above is the owner's. `docs/audits/README.md` was not touched.
