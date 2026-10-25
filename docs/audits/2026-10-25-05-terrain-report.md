# Step 10, terrain (Navigator stage H): report before building (dispatch 2026-10-25-05, part 2)

**Status: a report. Nothing is built.** No app code, no Gradle, no phone. Written by an app coder (Opus) on 2026-10-25 (UTC) for Rowan Waymark, under RECORD -1629.

**Base:** `pre-main` `f5a0cfafc6e44f2bb3843d90d04807db904cc1ca`, `records-after-173` `2b536f576`, both checked with `git ls-remote origin` before reading. `A/` is `app/src/main/java/com/zynergylabs/forager/app/`. Line counts are `wc -l` at `f5a0cfafc`. **This is a claim about the past;** the build dispatch re-reads it.

**What the step is.** Navigator stage H: "Hillshade, slope, aspect, water, elevation profile". Blocked by "A server-side elevation pipeline that does not exist, and the own-tiles scope B choice it overlaps. The elevation profile alone needs no pipeline and can be pulled forward" (`docs/navigation/2026-10-01-navigator-completion-plan.md:182`). It was folded into trail navigation as step 10 by the owner's "Fold H and I in, later (Recommended)" (RECORD -1498; `docs/plans/2026-10-21-trail-navigation-plan-update.md:87-90`).

---

## 1. What already exists, so it is not specified twice

**Read in the app at `f5a0cfafc`:**
- **Per-point height on recorded tracks.** `TrackPointEntity.altitude` (the platform's raw GPS altitude, `A/data/local/TrackPointEntity.kt:37`, 44 lines) and `pressureHpa` (:43, migration 20 to 21). Heights are corrected only when read: `trackHeights()` uses barometer heights when at least half the points carry pressure, else smoothed GPS (`A/domain/TrackHeights.kt:18-21, 40-58`, 82 lines; `A/domain/BarometricHeight.kt`, 158 lines). The stored value is never altered (`TrackHeights.kt:10-11`).
- **Climb and descent:** gain and loss with 4 m hysteresis (`A/domain/ComputeTrackStatisticsUseCase.kt:66-92, 114-116`, 116 lines), shown as the "Climb" and "Descent" tiles on a track's sheet (`A/ui/log/RecordDetailsFormat.kt:64-65, 90`, 132 lines).
- **An elevation profile already exists, for recorded tracks.** `HeightProfile` (NoTrack, PointsUnavailable, TooFewHeights, Drawn) in `A/domain/EntryReport.kt:33-55` (195 lines), drawn by `EntryHeightProfile` with Compose Canvas (`A/ui/log/EntryReportSummary.kt:119-170`, 196 lines). It is used on a record's details and on a journal entry's report (`RecordDetailsContent.kt:135`; `CartographyEntryReportScreen.kt:689`). It was built by dispatch 2026-10-11-01 and merged as PR #219 (RECORD -884 to -886, -889, read).
- **A live height readout:** GPS elevation in the compass strip at the top of the map (`A/ui/availability/CompassElevationStrip.kt:18-24`, 150 lines).
- **No DEM anywhere.** No raster-dem source, hillshade layer, contour source or terrain code exists in the app, `map-style/` or the Worker. The only mentions are comments: the rejected "hillshade layer over Street (Terrarium DEM)" option (`A/ui/map/BasemapStyles.kt:124-126`, 460 lines) and the planned "(later) hillshade" (`A/ui/map/MapChrome.kt:43`).
- **Topographical today is OpenTopoMap's raster** (`A/ui/map/MapMode.kt:45`, 78 lines; `A/ui/map/Basemap.kt:152-167`, 186 lines), with its relief baked into the tiles. At night it is the V1 inversion, with Trail Map or OSM below zoom 9.5 (`BasemapStyles.kt:60-65, 85-86, 140-141`; RECORD -314, -322).
- **Water is already drawn on Trail Map** from OpenStreetMap: a fill, streams from z13 and rivers (`street-style-v2.json` layers `water`, `water_stream`, `water_river`, read with a script), with the owner's night water `#163f4b` (`A/ui/map/NightColour.kt:97`, 296 lines; RECORD -1343).
- **Routing is flat.** The shipped BRouter profile has `assign consider_elevation = false` (`app/src/main/assets/routing/profiles2/hiking-mountain.brf:13`, 550 lines). The app reads only the route's points and length (`A/routing/BRouterTrailRouter.kt:112-117`, 218 lines), and `TrailRoute` has no height field (`A/domain/routing/TrailRouter.kt:41-45`, 70 lines). The walk-back time uses distance over pace and nothing else (`A/domain/ReturnWalkingTime.kt:105-110`, 143 lines).

**Planned on the server side (read from Aspen's repository, Forager-cartography `origin/main` 896fbc5, taken as premises, not facts):**
- **Our own Topo replaces OpenTopoMap.** "Our own Topo: Street v1 with its character layers, plus relief and contours. It replaces the OpenTopoMap raster in the Topographical slot. Street stays flat" (`docs/design/2026-10-22-topo-rulings.md:21`, ruling 1; the owner: "all recommended", :17).
- **Relief:** exaggeration 0.35 at z13 easing to 0.22 at z15, shadow `#4a3f33`, white highlight, light from 335° (ruling 2). Hillshade from z9 (ruling 4).
- **Contours:** 40 ft with a 200 ft index, in feet, brown `#a07040`. Index contours and labels from z13, all contours from z14 (rulings 3 and 4).
- **The data:** "USGS 3DEP 1″ for the US and NRCan MRDEM for Canada, as one download shared with the routing elevation work" (ruling 5).
- **Served as:** "Raster-dem relief to z12 (overzoomed above), and vector contours, as two separate archives built once. Not in the weekly basemap" (ruling 6).
- **Night relief:** "Dark shadows `rgba(0,0,0,0.85)` and a dim highlight `rgba(220,228,236,0.30)`. The hillshade gets its own night pair in the app" (ruling 7).
- **Timing:** build after the character work ships (ruling 8). Since then, "after Sunday's weekly build … the GDAL install and the two-square measurement, each with its own go" (:51-59).
- **Sizing** (`docs/design/2026-10-22-topo-sizing.md`, inferred there): `relief.pmtiles` 40 to 90 GB, `contours.pmtiles` 3 to 10 GB, about 140 GB read once, and the Pi's disk is the blocker (:16-18, :83-99).

**Not found in Forager's RECORD:** the owner's eight topo rulings above. The latest Forager-side topo entry is -1291's "Own topo later" (RECORD, decision 9) and the intent -1552 for a Juniper plan (`docs/plans/2026-10-21-own-topo-plan-report.md` on branch `topo-plan-report`). That intent has no closing entry, and the branch does not exist on the remote (`git fetch origin topo-plan-report`: "couldn't find remote ref"). So the topo design is ruled in Aspen's record only (see Disclosure).

## 2. Each part: which needs a server elevation pipeline

| Part | Needs a server elevation pipeline? | Why |
|---|---|---|
| **Elevation profile of a recorded track** | **No. Built.** | §1: `HeightProfile` and `EntryHeightProfile`, from the phone's own GPS and barometer. |
| **Elevation profile of a route ahead** (the way home, a waypoint) | **Yes, but routing's pipeline, not a map one.** | A route goes where the walker has not been, so no phone reading exists for it. BRouter returns a height per route point when its data carries elevation: `OsmPathElement.getElev()` is `selev / 4.` metres (`third_party/brouter/src/main/java/btools/router/OsmPathElement.java:42-44`), with `Short.MIN_VALUE` where there is none (`OsmTrack.java:344`, read; that this value means "no data" is inferred). The rd5 squares carry elevation only once the Pi adds the `.bef` step (`docs/navigation/2026-10-21-elevation-sizing.md:6`, on records-after-173). No map tiles are needed. |
| **Hillshade** | **Yes.** | Relief needs DEM tiles. MapLibre Native 13.5.0 has `RasterDemSource` and `HillshadeLayer`, with shadow, highlight and accent colours and an illumination direction (read with `javap` from the pinned AAR in the Gradle cache, `android-sdk-13.5.0.aar`). It also has `ColorReliefLayer`, a tint by height, not by slope. |
| **Contours** | **Yes.** | A vector archive cut from the DEM (ruling 6). Not in the Navigator's list, but part of the ruled own Topo. |
| **Slope and aspect** | **Yes for data. No for a new archive.** | Both are computed from the DEM. MapLibre Native has no slope or aspect layer (inferred from the 13.5.0 class list: hillshade and colour relief only). Two ways, in §3. |
| **Water** | **No elevation pipeline.** | OSM water is drawn today (§1). The Navigator plan's NHD water would be a separate vector pipeline (`docs/navigation/forager-navigator-plan.md:304`). See Q4. |

So the dispatch's premise, "the profile alone does not [need a pipeline]", holds for a recorded track, and that profile already exists. A **route** profile needs routing elevation, which is the same server work as elevation-in-routing.

## 3. Slope and aspect: two ways (a stop-and-ask)

**S1. Read from the relief tiles on the phone (recommended).** Slope and aspect at a point, computed in `domain/` from the Terrarium-encoded relief the Topo map already needs. Height is `R×256 + G + B/256 − 32768` metres per pixel; that is the Terrarium encoding, from recall and not read from a source this session, so the build re-reads it. Slope and aspect come from a 3×3 neighbourhood: plain arithmetic, testable headless.
- *Where it shows:* in a readout ("Slope 18° · faces north-east · 4,210 ft") where the walker stands or at a picked point (the two shapes in §6) and, later, on a find's site context (Navigator stage G, which stays in the Navigator plan: `2026-10-21-trail-navigation-plan-update.md:87`).
- *Cost:* the app must read relief pixels. MapLibre's offline database has no public read API for stored tiles (inferred: `OfflineManager` exposes no tile getter in 13.5.0; not exhaustively checked). So the app keeps its own copy of the relief tiles under an area, like the route files (`A/data/routedata/RegionRouteFolders.kt:92`, `filesDir/offline_routes/<regionId>/`). Inferred size, for a 24 km region at 45°N: z10 to z12 is about 70 to 100 tiles at 50 to 100 KB, so about 4 to 10 MB. That is computed from tile widths (z12 is about 6.9 km wide at 45°N) and the topo sizing's per-tile figure, not measured.
- *Online:* one tile fetched from the Worker per readout.

**S2. A slope-shading map layer.** Pre-rendered slope tiles (for example, shaded bands at 30° and 45°, as avalanche maps use), a third archive.
- The Navigator plan priced DEM rasters as roughly tripling the raw footprint (`forager-navigator-plan.md:314-315`).
- Not recommended until the owner asks for slope as a map. A forager's question is "how steep is it here", which S1 answers.

## 4. One elevation source serving both routing and terrain

The elevation sizing (`docs/navigation/2026-10-21-elevation-sizing.md`, Juniper, filed on records-after-173 and not on pre-main) and the topo rulings meet:
- **Routing** reads elevation only from BRouter's `.bef` files, built from 1° `.hgt` files (sizing :6). The app needs no code change for routing to have elevation in the data; to *weigh* climbs, the app's profile copy flips `consider_elevation` (sizing :15; `hiking-mountain.brf:13`).
- **Terrain** reads Terrarium raster-dem tiles (ruling 6) and vector contours.
- **One download, three outputs.** One windowed pass over 3DEP (and MRDEM for Canada) could write both the `.hgt` files and the relief tiles (Forager-cartography `topo-sizing.md:37-39`, inferred there). The sizing recommends building 3″ first while keeping the raw-to-hgt step at 1″ (sizing :17), and ruling 5 takes 1″.
- **What this means for the app:**
  - the route profile (a height per route point) comes with the route, from the rd5;
  - hillshade, contours and slope and aspect come from the relief and contour archives;
  - both trace to one DEM build.
  - If the two are built from different DEM runs, a route's climb and the contours under it could disagree by a few metres. The same build keeps them in step (inferred).
- **Canada is the open seam.** The sizing recommends 3DEP 1″ for Canada too, with MRDEM as the fallback if USGS cannot confirm the Canadian tiles' provenance (sizing :8, :31). Ruling 5 takes MRDEM for Canada outright. Ruling 5 is the later word, but it is recorded only in Aspen's repository.

**Licences, read at source today** (2026-10-25, `~/.cache/later-steps-scratch/web/`):
- **3DEP / The National Map.** "Data from The National Map is free and in the public domain. There are no restrictions on downloaded data; however, we request that the following statement be used when citing, copying, or reprinting data: "Data available from U.S. Geological Survey, National Geospatial Program."" (`usgs.gov/faqs/what-are-terms-uselicensing-map-services-and-data-national-map`). And "All 3DEP products are available, free of charge and without use restrictions" (`usgs.gov/3d-elevation-program/about-3dep-products-services`).
- **NRCan MRDEM.** "Licence: Open Government Licence - Canada". The licence asks the user to "Acknowledge the source of the Information by including any attribution statement specified by the Information Provider(s) and, where possible, provide a link to this licence" (`open.canada.ca/en/open-government-licence-canada`). The dataset carries the notice "This product was in part produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014- 2018 provided under COPERNICUS by the European Union and ESA; all rights reserved", followed by a liability disclaimer (`open.canada.ca/data/en/dataset/18752265-bda3-498c-a4ba-9dfe68cb98da`).
- **Credits the app would add** to its Data sources list (`A/ui/credits/DataSourceCredits.kt`, 80 lines), worded per those terms: "Elevation: U.S. Geological Survey, National Geospatial Program (3DEP), public domain"; "Elevation (Canada): Natural Resources Canada, MRDEM, Open Government Licence – Canada", with the Copernicus notice as its detail line. Whether the Copernicus notice must also show on the map caption is for someone qualified (the sizing's own ODbL note, :27-28, is inferred, not legal advice).

## 5. Night, offline and chrome

- **Night relief does not come free.** Trail Map's night recolour walks only background, fill, line and symbol colours (`A/ui/map/OfflineNightRecolourApply.kt:76-82`, 108 lines: `else -> emptyList()`). A hillshade layer's shadow and highlight would stay in day colours at night. That matches ruling 7 ("The hillshade gets its own night pair in the app"): a small named step after the recolour in the style-load callback (`A/ui/map/MapStyleReload.kt:128`, 177 lines). The contour lines are line layers, so they take V1 automatically. Ruling 7's pair is not an inversion. It is the owner's ruling (in Aspen's record), and it parallels the water exception (-1343).
- **Topo at night today** switches to Trail Map or OSM below zoom 9.5, because the inverted OpenTopoMap relief looked wrong (RECORD -310 to -322). With our own vector Topo, the low-zoom switch exists only for the raster. Whether it can go is a phone question: own Topo at night at zooms 7 to 9.4 (device-only, §7).
- **Offline.** Offline downloads are a MapLibre tile pyramid over Trail Map's style, whatever map is showing (`A/map/StreetStyle.kt:50`, RECORD -1554 "Offline: Trail Map only"). So **own Topo is not offline today by rule**, and relief would not be stored for an area unless the owner changes -1554 for Topo. Options are Q3. Inferred size for a Topo area, if allowed: relief about 4 to 10 MB (S1's figure) plus contours z13 to 14, about 1,000 tiles per 24 km region (computed), of unknown size each.
- **No signal** draws Trail Map for Topo users (RECORD -1556, `A/ui/map/NoSignalTrailMap.kt:53-59`, 140 lines), so offline Topo would also change that rule.
- **Chrome:** a profile or a slope readout shown over the map sits at `MAP_CHROME_OVER_MAP_ALPHA` (`A/ui/map/MapChrome.kt:69`). The existing chart sits on a record's sheet, which is chrome already.

## 6. Step paths for the owner

**Own Topo** (Aspen's design, the app's side):
1. Map → Layers → Map type "Topo" → the map shows our own map with soft shading and brown contour lines (from zoom 9 and 13), in place of OpenTopoMap's.
2. Night Maps on → the shading turns to dark shadows, the contours invert with the rest of the map.
3. Back → closes the sheet.
4. With no signal (today's rule) → Trail Map, flat, until signal returns.

**The way home, with its climb** (needs rd5 elevation):
1. Recording → tap Return → the route home draws along trails, as today.
2. The navigation readout adds "↑ 420 ft · ↓ 1,150 ft on the way back".
3. Tap the readout → a sheet with the route's height profile (the same chart as a journal entry's), "From here to the start".
4. Back → closes the sheet. The route stays.
5. Where the route data has no heights → nothing extra shows. No zero, no guess.

**How steep here** (S1). The map's long-press has no production consumer: both map call sites pass `{}` (`A/ui/availability/CompactMapMapSlot.kt:126`, and the centre-pin picker at `A/ui/map/CentrePinLocationPicker.kt:207-215`; the reason is recorded at `A/ui/map/SightingsMap.kt:77-83`, 759 lines). Picking a point today means panning under the centre pin. So there are two shapes, and the owner picks:
- **(a) Where I stand.**
  1. Tap the compass and elevation strip at the top of the map → it opens one line: "Slope 18° · faces north-east".
  2. Tap again, or Back → it closes.
- **(b) A point.**
  1. Tap the add (+) button → pan the map under the centre pin → the picker shows "Slope 18° · faces north-east · 4,210 ft" for the point under the pin.
  2. Back → the picker closes.

In both, offline in an area without relief tiles the line reads "Slope not known offline here", never a guess.

**A recorded walk:** no change. Records → a track → Climb, Descent and the height chart, as today.

## 7. Device-only items

1. MapLibre Native 13.5.0 drawing the hillshade and contour labels on the S22 (Forager-cartography `topo-rulings.md:35-36`: "The previews were GL JS").
2. The night relief pair against the night Topo, at zooms 7 to 15.
3. Whether Topo's below-9.5 night switch is still needed with vector Topo.
4. The relief and contour size a 24 km area adds, if Topo becomes offline.
5. A route profile against the barometer's heights walking the same route (the owner's planned hilly walk, -1629, is the natural sample).

## 8. What the app needs from the server side (for the owner to take to Aspen)

1. **Route squares with elevation** (`.rd5` built with the `.bef` step), published as today under `routing/<run-id>/` with the manifest (Forager-cartography `docs/audits/2026-10-23-route-squares-upload.md:11-12, 33`, a premise). Nothing else changes for the app. **Before it exists:** the app's height reading can be built against a synthetic route result; whether the Worker's rd5 test fixtures carry elevation is not checked (`server/pmtiles-worker/test/fixtures/rd5`).
2. **`relief.pmtiles`:** raster-dem, Terrarium, 256 px, z0 to z12 (ruling 6), served through the Worker's generic `/{name}/{z}/{x}/{y}.{ext}` path (`server/pmtiles-worker/src/shared.ts:13-41`; that it works for a raster archive unchanged is inferred). Size 40 to 90 GB on R2 (topo sizing, inferred there). Refresh: once, then rarely; elevation does not change weekly.
3. **`contours.pmtiles`:** vector, z13 to z14, a `contour` layer carrying the height in feet and an index flag. Field names are for Aspen to state, so the style can filter index lines and labels.
4. **The DEM run's identity** on both, and on the rd5 manifest (for example a `dem_run` field), so the app or a reviewer can tell when a route's climb and the contours come from different builds.
5. **Questions for Aspen:** the 1″ and 3″ choice for the routing `.bef` (the sizing recommends 3″ first); whether MRDEM's Copernicus notice must travel inside the rd5 (sizing :27-28); and the disk plan, since the Pi's disk is the stated blocker (topo sizing :16-18).

## 9. Proposed PRs, in order

| PR | Does | Files (lines now) | Needs the server? |
|---|---|---|---|
| **10a. Heights on a route** | `TrailRoute` gains per-point heights (nullable). `BRouterTrailRouter` reads `getElev()` where present. A domain function turns a route into the existing `HeightProfile` and a climb and descent figure. No UI. | `A/domain/routing/TrailRouter.kt` (70); `A/routing/BRouterTrailRouter.kt` (218); new `A/domain/RouteHeightProfile.kt` (new, under 150: a route's heights to a profile, reusing `HeightProfile` from `EntryReport.kt`). | Testable now with a fake router. Real heights need server item 1. |
| **10b. The climb on the way back** | The readout line and the profile sheet from §6. | `A/ui/availability/NavigationReadout.kt` (239) or `NavigationHudReadout.kt` (115), whichever owns the line at build time; new `A/ui/availability/RouteProfileSheet.kt` (new, under 200, reusing `EntryHeightProfile`). | Needs item 1 to show anything. |
| **10c. Own Topo in the Topographical slot** | `MapMode.TOPOGRAPHIC` points at a new own-topo style (Street plus relief and contours); the night pair for the hillshade; the credits. | `A/ui/map/MapMode.kt` (78); `A/ui/map/Basemap.kt` (186); `A/ui/map/BasemapStyles.kt` (460); new `A/ui/map/TopoReliefNight.kt` (new, under 100: the night pair, one call in `MapStyleReload.kt` (177)); a new style file in `map-style/` and `server/` (generated); `A/ui/credits/DataSourceCredits.kt` (80). | Needs items 2 and 3; buildable against a tiny synthetic raster-dem fixture (the Worker's fixtures already make tiny archives: `test/fixtures/make-tiny-pmtiles.py`). |
| **10d. Slope and aspect at a point (S1)** | Domain decode and slope/aspect, a relief tile client, and the readout in shape (a) or (b) from §6. Offline relief only if the owner rules Q3. | New `A/domain/terrain/TerrainAtPoint.kt` (new, under 200); new `A/data/terrain/ReliefTileClient.kt` (new, under 200, behind an interface the domain owns: CLAUDE.md, Architecture); for (a) `A/ui/availability/CompassElevationStrip.kt` (150), for (b) `A/ui/map/CentrePinLocationPicker.kt` (508). | Needs item 2. |

**The modular-code skill's four questions:**
1. Files touched and sizes: in the table, `wc -l` at `f5a0cfafc`.
2. Over about 800 lines with the change: none. `BasemapStyles.kt` (460) is the largest; 10c adds a style source choice, not a feature body.
3. New files, one job each: `RouteHeightProfile.kt` (a route's heights to a profile), `RouteProfileSheet.kt` (show it), `TopoReliefNight.kt` (the hillshade's night colours), `TerrainAtPoint.kt` (slope, aspect and height from a pixel neighbourhood), `ReliefTileClient.kt` (fetch and store relief tiles).
4. Order that must stay put: the relief night step runs after the V1 recolour in the style-load callback (`MapStyleReload.kt:128-140`); in the Topo style, contours sit between hillshade and water, with trails and labels above (Forager-cartography `topo-rulings.md:30-31`). Both get a phone check.

10a and 10c touch no common file and could run side by side.

## 10. Questions for the owner

Q1. **The route profile** (10a and 10b): wanted for the way home and for waypoints, once the route data carries heights? It is separate from whether routes *avoid* climbs (the flat-first ruling, -1498 item 2), and from a climbing allowance on the walk-back time (sizing :16, the owner's walk under -1629).
Q2. **Slope and aspect:** S1, a readout (recommended), or S2, a map layer, or neither for now? If S1, shape (a), where I stand, or (b), a picked point (§6)?
Q3. **Own Topo offline:** today only Trail Map is stored offline (-1554). Keep that (Topo users see Trail Map offline, flat), or let a download also carry relief and contours?
Q4. **Water:** OSM water is drawn today. Is a separate national water layer (NHD) wanted, for intermittent streams and springs, or does OSM's water stand?
Q5. **The topo rulings:** the eight were given in Aspen's window (Forager-cartography `topo-rulings.md`). Should they be recorded in Forager's RECORD before 10c is dispatched, since 10c is app code?

## Disclosure

**Confirmed (read today):**
- every app citation at `f5a0cfafc`;
- that the recorded-track profile exists;
- that routing reads no height;
- `consider_elevation = false`;
- MapLibre 13.5.0's hillshade, raster-dem and colour-relief classes (by `javap` on the cached AAR);
- that the night recolour skips hillshade layers;
- BRouter's `getElev()`;
- the 3DEP, National Map and MRDEM licence text.

**Inferred, not observed:**
- the Terrarium formula (recall);
- that MapLibre exposes no stored-tile read;
- the relief and contour sizes per area;
- that one DEM pass can write `.hgt` and relief together (Aspen's inference);
- that `Short.MIN_VALUE` means no height;
- that the Worker serves a raster archive through its generic path.

**Could not determine:**
- the own-topo plan report (-1552), whose branch is not on the remote;
- the size of a contour tile;
- whether the rd5 fixtures carry elevation;
- whether NHD is still maintained (not checked);
- how MapLibre Native draws the relief on the S22.

**Premises that were wrong or have moved:**
- "The elevation profile alone needs no pipeline and can be pulled forward" (`navigator-completion-plan.md:182`). For recorded tracks it has already been pulled forward and built (PR #219). For a route ahead it does need data: routing's elevation, not a map pipeline.
- The elevation sizing is on records-after-173 only, not on pre-main as the dispatch's "docs/navigation/2026-10-21-elevation-sizing.md" might suggest. It was read there.
- The sizing quotes 3DEP's licence as "All 3DEP products are public domain". The 3DEP products page read today says "available, free of charge and without use restrictions", and USGS's National Map terms page says "free and in the public domain". The substance holds; the quoted wording was not found on the page read.
- "The own-tiles scope B choice it overlaps" (`navigator-completion-plan.md:182`): scope B (own topo) has since been chosen, as "Our own Topo" in Aspen's topo rulings, but not in Forager's RECORD (Q5).

**Corrected in this report before hand-off:** the first push (`b7382c8ae`) put the slope readout on "Long-press a point on the map → the usual long-press options". The map has no production long-press consumer (`CompactMapMapSlot.kt:126` passes `{}`), so that step path described a menu that does not exist. §3, §6, 10d and Q2 now give the two shapes that do fit the app.

**Decided beyond scope:** nothing. S1 against S2, offline Topo, NHD and the route profile are each put to the owner. `docs/audits/README.md` was not touched.
