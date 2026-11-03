# Where "OSM" is used as a style name in anything public

**Dispatch:** 2026-11-03-02 (`prompts/preserved/2026-11-03-02-osm-style-name-check.md` at `6ce6a8d52`, branch records-after-173). RECORD -1987; the owner's words at -1985: "OSM refers to the style, but we don't want to publish that anywhere since it's an inspiration and not the target to copy directly."
**Researcher:** Juniper Sounding (Fable), 2026-11-03.
**Read-only.** Nothing changed in Forager, in zynergy-site or in any service. This file is the one write, on `juniper/osm-style-name-check`, cut from origin/pre-main at `3b9c0b70b`. `docs/audits/README.md` was not touched: the dispatch names this file as the only commit, and the index is a serialization point (CLAUDE.md).

**Trees read.** Forager `origin/pre-main` at `3b9c0b70b` and `origin/main` at `532f45dcf`, through `git grep`/`git show` against the refs, never a checkout. zynergy-site `origin/main` at `1e37879c5` (fetched 2026-11-03; the local clone is checked out on another branch, which was left alone). The Restricted Object was not read, and no file named for it was opened.

**Method.** Case-insensitive `git grep` for `osm|openstreetmap` over each whole tree, then every match on a public surface read in place. A second grep for OSM-as-style phrasing (`osm-like`, `OSM style`, `like OSM`, `OSM Carto`, `openstreetmap-carto`, `OSM's colours/palette`, `copy of OSM`, and `Carto`/`inspir` alone on the public surfaces). Every claim below is **read** unless marked **inferred**. Line numbers are origin/pre-main's unless a branch is named.

---

## 0. The answer in one paragraph

**No public text names OSM as our style or as a target.** Every (i)-shaped phrase, 26 lines, sits in a Kotlin doc comment, a test, a style build script or a record; none is compiled into a string a user sees, none is in the README, the privacy policy, the store audits or the site. Where "OSM" or "OpenStreetMap" does reach a user it is one of two things: the **reference basemap by name** (the "OSM" chip, the privacy tables, README's service table) or **licence attribution** (map captions, Sources & credits, the style JSONs' `attribution`, the site's forecast credits). The owner's inspiration-not-target framing is already how the public surfaces read. Two side findings, both B3: the README still describes the maps as "Street, topographical and satellite" and never says "Trail Map"; the site's privacy table calls our map "Our map tile service" and "the offline basemap".

---

## 1. Counts

| Category | Public (a user, store visitor or site visitor can see it) | Internal (comments, tests, scripts, records) |
|---|---|---|
| (i) the style named as ours or as a target | **0** | **26** lines (section 2) |
| (ii) the OpenStreetMap reference basemap, by name | **7** lines (section 3) | the rest of the `OSM_STANDARD` / `OPENSTREETMAP` identifier uses |
| (iii) attribution or a licence credit | **26** lines: 8 in app code, 15 in style JSONs, 1 in `map-style/README.md`, 2 on the site (section 4) | 2 generator lines that produce them |
| (iv) internal only | — | everything else (section 5) |

Whole-tree totals, computed (`git grep -c`, grouped by a script run on its output):

| Area | Lines | Files |
|---|---|---|
| `third_party/brouter` (vendored Java; `OsmNode`, `OsmPath`, `OsmTrack`… identifiers) | 726 | 42 |
| `docs/` (records, audits, plans, evidence data) | 576 | 89 |
| `app/src/test`, `app/src/testDebug` | 216 | 44 |
| `app/src/main` Kotlin | 143 | 46 |
| binary false positives (`.png`, `.jpg`, `fungi_index.db` bytes) | 128 | 25 |
| Worker tests and fixtures | 70 | 11 |
| `data/species-index` (species names: *Lactarius evosmus*, *Mixia osmundae*, *Inocybe picrosma*…) | 61 | 3 |
| `RECORD.md`, `STATUS.md`, `CLAUDE.md`, `prompts/` | 54 | 12 |
| `map-style/` (styles, generators, overrides, tools) | 32 | 17 |
| Worker `src/`, README, lockfile | 19 | 12 |
| `app/src/main/assets/routing/profiles2/hiking-mountain.brf` | 6 | 1 |
| `scripts/` | 5 | 2 |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` (osmdroid removal notes), `README.md` | 5 | 3 |
| **Total** | **2041** | **307** |

A plain `git grep | wc -l` gives 1938 lines on pre-main: the difference, 103, is the 25 binary files counted once each there and 128 times by `-c`. origin/main: 1867 lines in 282 files. The 25 files on pre-main only, and the one on main only (`MapLayersSheet.kt`), are listed in section 6; none changes a category count above zero that is zero on the other branch.

The `.db` and species-index hits were checked (`strings | grep -o`): "kosmatka", "Lactarius evosmus", "Mixia osmundae". False positives, not OSM.

---

## 2. Category (i): OSM named as our style or a target — 26 lines, none public

Every item here is the owner's own ruling quoted back into the code that implemented it ("Keep it osm-like", "Invert like OSM", "Tan, like OSM", "A: OSM pink"), or a citation of the openstreetmap-carto source file a colour was read from. **None is a user-visible string**: each is a `//` or `/** */` comment, a test's doc or inline comment, a test method name, a build script's comment, or a record. Visibility is stated per item; a repo visitor on GitHub can read all of them, which is the only sense in which they are public. Read, all of them.

**App code comments (2):**

| # | File:line | Text | Who sees it |
|---|---|---|---|
| i-1 | `app/src/main/java/com/zynergylabs/forager/app/ui/map/StreetNightTables.kt:32` | `* Street v4's night (Forager-cartography docs/design/2026-10-30-fill-in-rulings.md; the owner: "Keep it osm-like",` | Nobody in the app; a doc comment. Repo readers only. |
| i-2 | `…/ui/map/StreetNightTables.kt:36` | `* - Lines: v3's, except the major roads, the motorways and their ramps ("Invert like OSM"), and the rail ("Plain` | Same. |

**App tests (8):**

| # | File:line | Text | Who sees it |
|---|---|---|---|
| i-3 | `app/src/test/java/com/zynergylabs/forager/app/ui/map/BuildingsNightTest.kt:16` | `* from z15, with a #a6a6a6 outline from z15, after the OpenStreetMap Carto style's buildings` | Nobody; test doc comment. |
| i-4 | `…/ui/map/BuildingsNightV4Test.kt:17` | `* industrial and buildings"): the buildings in OSM Carto's tan, "Tan, like OSM", in place of RECORD -1733's greys, which` | Same. |
| i-5 | `…/ui/map/BuildingsNightV4Test.kt:21` | `* #b9a99c (Recommended)"; openstreetmap-carto style/buildings.mss:1-2).` | Same. |
| i-6 | `…/ui/map/NightExceptionsV4Test.kt:22` | `* the owner: "Keep it osm-like", "Yes, ship it", and for ramps "Invert them too (Recommended)"; then motorways "A: OSM` | Same. |
| i-7 | `…/ui/map/NightExceptionsV4Test.kt:23` | `* pink" and "Invert like OSM", tertiary "Dark grey #333333" and rail "Plain inversion"). v4 has its own night tables,` | Same. |
| i-8 | `…/ui/map/NightExceptionsV4Test.kt:102` | `// The major roads, the motorways and their ramps ("Invert like OSM"), and the rail ("Plain inversion") take the` | Same. |
| i-9 | `…/ui/map/NightExceptionsV4Test.kt:121` | `// OSM Carto's motorway fill and casing (the rulings' "Motorways"), so from z12.5 the inversion is #7f2939 and #e83673;` | Same. |
| i-10 | `…/ui/map/NightExceptionsV4Test.kt:256` | `// Industrial in OSM Carto's lilac (style/landcover.mss:22) inverts to #21111e.` | Same. |

**Style build scripts, `map-style/` (13).** These generate the served style JSONs; the comments do not survive into the JSON (section 4 confirms the JSONs carry no OSM text beyond `attribution`).

| # | File:line | Text | Who sees it |
|---|---|---|---|
| i-11 | `map-style/overrides/forager-fill-in.mjs:8` | `//   The night colours: sage-09's "OSM-like" set (the owner, 2026-10-30: "Keep it osm-like", then "Yes, ship it"):` | Repo readers only. |
| i-12 | `…/forager-fill-in.mjs:11` | `//   Then, in the same record: ramps with their roads ("Approve the fix"); motorways in OSM Carto's pink by day ("A: OSM` | Same. |
| i-13 | `…/forager-fill-in.mjs:12` | `//   pink") and inverted at night ("Invert like OSM"); the night tertiary dark grey #333333 and the rail inverted ("Dark` | Same. |
| i-14 | `…/forager-fill-in.mjs:36` | `// OSM Carto's motorway fill and casing: openstreetmap-carto style/road-colors-generated.mss at 9295745d, lines 11 and 7.` | Same. |
| i-15 | `…/forager-fill-in.mjs:39` | `// OSM Carto's industrial lilac (style/landcover.mss:22) and buildings tan (style/buildings.mss:1), in place of RECORD` | Same. |
| i-16 | `…/forager-fill-in.mjs:55` | `* after OSM Carto. The industrial edge is Carto's @industrial-line (style/landcover.mss:23; "OSM's #c6b3c3").` | Same. |
| i-17 | `…/forager-fill-in.mjs:77` | `/** The motorway layers, centres and casings, that take OSM Carto's colours; the tunnels keep their grey. */` | Same. |
| i-18 | `…/forager-fill-in.mjs:102` | `// The low zooms, after OSM Carto (the fill-in rulings, "Low-zoom roads, night borders, notices route": "A: OSM's treatment` | Same. |
| i-19 | `…/forager-fill-in.mjs:276` | `// --- Motorways and their ramps in OSM Carto's pink, with its casing ---` | Same. |
| i-20 | `map-style/overrides/forager-street.mjs:414` | `// The structure is the OpenStreetMap Carto style's buildings (openstreetmap-carto, style/buildings.mss; that project` | Same. |
| i-21 | `map-style/tools/check-street-night.mjs:221` | `// 6. v4's motorways (the fill-in rulings, "Motorways": the owner, "A: OSM pink (Recommended)" by day and "Invert like OSM` | Same. |
| i-22 | `…/check-street-night.mjs:222` | `//    (Recommended)" at night): OSM Carto's fill and casing (style/road-colors-generated.mss at 9295745d, lines 11 and 7),` | Same. |
| i-23 | `…/check-street-night.mjs:298` | `//    at night", "Lilac, like OSM", "Tan, like OSM", then "OSM's #b9a99c", "Keep OSM's steps", "One step darker #ccc0b7",` | Same. |

**Worker test (1):**

| # | File:line | Text | Who sees it |
|---|---|---|---|
| i-24 | `server/pmtiles-worker/test/worker.test.mjs:516` | `// After the OpenStreetMap Carto style's buildings (style/buildings.mss): an opaque fill from z14, darker at z14, and` | Repo readers only. |

**Records (2):**

| # | File:line | Text | Who sees it |
|---|---|---|---|
| i-25 | `RECORD.md:1633` | `… (c) The pink-red road references come from openstreetmap-carto v6.1.0 source, not from the sample. …` | Repo readers only. |
| i-26 | `docs/audits/2026-09-27-marker-swatch-board.md:129` | ``openstreetmap-carto v6.1.0 `style/road-colors-generated.mss`: `@motorway-fill #e892a2` and`` | Same. |

Two borderline lines, read and **not** counted as (i): `app/src/test/…/BasemapStyleTest.kt:219`, the test name ``Forager Trail Map's raster stand-in is OpenStreetMap's style exactly, never its own vector tiles`` — this is about the fallback drawing OpenStreetMap's *own tiles* when Trail Map's style fails, so it is the reference basemap (ii), not a style target; and `TopoNightStreetSwitchTest.kt:77`, the sample label `"OSM Standard sample"`, likewise (ii). `OverlayLayerSetup.kt:120` "unlike osmdroid" matched the phrasing regex but names the old map library, not a style.

What the (i) sweep also shows: the owner's "inspiration" is named in the code as **OSM Carto**, the openstreetmap-carto stylesheet, and the colours taken from it are cited to its source files and commit (`9295745d`). Whether comments that cite a source stylesheet should stay as they are, since they are not public text, is the owner's call; the dispatch says nothing changes until the list has been seen.

---

## 3. Category (ii): the OpenStreetMap reference basemap, by name — 7 public lines

| # | File:line | Exact text | Who sees it |
|---|---|---|---|
| ii-1 | `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapMode.kt:48` | `OPENSTREETMAP(label = "OSM", basemap = Basemap.OSM_STANDARD, storageKey = "openstreetmap"),` | **App users**: the third chip in the map type panel (`MapModeChips.kt:128`, `label = { Text(mode.label) }`). The chips read "Trail", "Topo", "OSM" (RECORD -1554, -1556, quoted at `MapMode.kt:5`). |
| ii-2 | `docs/legal/privacy-policy.md:60` | `\| OpenStreetMap (`tile.openstreetmap.org`), OpenTopoMap (`a.tile.opentopomap.org`) \| Map tile coordinates … \| Drawing the map when you choose OSM or Topo under "Map type" in Layers (the default is Trail, Forager Trail Map, below), and OpenStreetMap's map in place of Forager Trail Map if Trail Map's style fails` | **Repo visitors**: the repository copy of the privacy policy. Names the reference basemap and the "OSM" chip. |
| ii-3 | `README.md:148` | `\| OpenStreetMap, OpenTopoMap, USGS National Map \| the street, topographical and satellite base maps \| a connection, unless you have downloaded an offline region \|` | **Repo visitors**: the README's service table. Also stale (B3, section 7): satellite was removed by dispatch 2026-09-28-708 (`MapMode.kt:16-18`), and the row does not name Trail Map. |
| ii-4 | `docs/beta/README.md:43` | `from OpenStreetMap, OpenTopoMap, USGS and a Cloudflare Worker this project runs — a tile request is` | **Repo visitors**, beta testers sent to it. Stale on USGS as above. |
| ii-5 | zynergy-site `privacy/index.html:111` | `<td>OpenStreetMap, OpenTopoMap<br><code>tile.openstreetmap.org</code>, <code>a.tile.opentopomap.org</code></td>` | **Site visitors**: the published privacy page's third-party table. |
| ii-6 | `app/src/main/java/com/zynergylabs/forager/app/ui/map/Basemap.kt:170` | `label = "OpenStreetMap",` | **Not seen.** `Basemap.label`'s only reader in `app/src/main` is a log line (`AvailabilityMapDisplay.kt:253`). The chip uses `MapMode.label`. Listed because it is a string literal that looks user-facing. |
| ii-7 | `…/ui/map/Basemap.kt:171` | `description = "OpenStreetMap's own standard map: roads, paths and buildings. Online only: never downloaded (RECORD -1554).",` | **Not seen.** `git grep '\.description\b' app/src/main` finds no reader of `Basemap.description` (inferred from the grep: zero readers). If it ever gains one, "RECORD -1554" would leak into the UI. |

Not public, same category: `Basemap.kt:174` (`tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"`, a URL); the log lines `NoSignalTrailMap.kt:124` ("OSM and Topo maps draw Forager Trail Map from the downloaded areas", inside `Log.i`, read at `:121-124`) and `OwnStreetFallback.kt:57` ("drawing OpenStreetMap's Street until the connection returns"); every `OSM_STANDARD` identifier use (`BasemapStyles.kt:94,135,141,149,269`; `AvailabilityOfflineMapsUi.kt:22,110`).

---

## 4. Category (iii): attribution and licence credits — required by the licence; flagged, never to be removed

**App, seen by users (8 lines).**

| # | File:line | Exact text | Where it shows |
|---|---|---|---|
| iii-1 | `app/src/main/java/com/zynergylabs/forager/app/map/OfflineStyle.kt:28` | `internal const val OFFLINE_STYLE_ATTRIBUTION = "Protomaps © OpenStreetMap"` | The map's credit pill (`MapAttribution.kt:15-16`) whenever Trail Map or offline tiles draw: `STREET_STYLE_ATTRIBUTION = OFFLINE_STYLE_ATTRIBUTION` (`StreetStyle.kt:52`) → `Basemap.FORAGER_TRAIL.attribution` (`Basemap.kt:147`) → `mapAttributionFor` (`BasemapStyles.kt:324-329`). |
| iii-2 | `…/ui/map/Basemap.kt:160` | `attribution = "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)",` | The credit pill on Topo. |
| iii-3 | `…/ui/map/Basemap.kt:166` | `fullAttribution = "© OpenStreetMap contributors, SRTM \| © OpenTopoMap (CC-BY-SA)",` | Written into the generated raster style's source `attribution` (`BasemapStyles.kt:186,193,212`). Whether MapLibre's own attribution control then displays it in this app was **not checked**. |
| iii-4 | `…/ui/map/Basemap.kt:173` | `attribution = "© OpenStreetMap contributors",` | The credit pill on OSM. |
| iii-5 | `…/ui/credits/DataSourceCredits.kt:39` | `SourceCredit("openstreetmap", "© OpenStreetMap contributors", "licence", "https://www.openstreetmap.org/copyright"),` | Settings > Sources & credits > Maps (`SourcesAndCreditsPages.kt:64` reads `DATA_SOURCE_SECTIONS`; reached from `AvailabilitySettingsPages.kt:110,116`). |
| iii-6 | `…/ui/credits/DataSourceCredits.kt:43` + `:126` | `SourceCredit("openstreetmap-routes", TRAIL_ROUTES_CREDIT_TEXT, "ODbL", …)` with `TRAIL_ROUTES_CREDIT_TEXT = "Trail routes: © OpenStreetMap contributors"` | Same page. |
| iii-7 | `…/ui/credits/DataSourceCredits.kt:48` | `"Map data: © OpenStreetMap contributors, SRTM; map style: © OpenTopoMap (CC-BY-SA)",` | Same page, the OpenTopoMap row. |
| iii-8 | `…/ui/credits/DataSourceCredits.kt:52` | `// Forager Trail Map's vector tiles and the offline map packs' (StreetStyle.kt); its OpenStreetMap data is` | A comment beside the Protomaps row; not shown. Listed to complete the file. |

**Served style JSONs (15 lines, one per file; each is the only OSM text in its file, confirmed by parsing every style's `name` and `metadata`).** All read: `"attribution": "<a href=\"https://github.com/protomaps/basemaps\">Protomaps</a> © <a href=\"https://osm.org/copyright\">OpenStreetMap</a>"`.

- Served by the Worker: `server/pmtiles-worker/src/offline-style.json:6`, `street-style-v1.json:137`, `street-style-v2.json:152`, `street-style-v3.json:152`, `street-style-v4.json:209`.
- Their sources in `map-style/`: `forager-light.json:11`, `forager-light.maputnik.json:11`, `forager-street-v1.json:137`, `forager-street-v1.preview.json:137`, `forager-street-v2.json:152`, `forager-street-v2.preview.json:152`, `forager-street-v3.json:152`, `forager-street-v3.preview.json:152`, `forager-street-v4.json:209`, `forager-street-v4.preview.json:209`.
- Style names, read from each file's `name`: "Forager light", "Forager light (for Maputnik)", "Forager Street v1" … "Forager Street v4"; `offline-style.json` has no `name`. `metadata` holds `forager:generated-from` ("@protomaps/basemaps 5.7.2, flavor light, with overrides/…"), palettes and sprite lists; **no OSM in any metadata key or value.**
- Generators that write the line: `map-style/generate.mjs:60`, `map-style/generate-street.mjs:121` (internal).

**Public docs (1):** `map-style/README.md:93`: `The map data comes from OpenStreetMap (© OpenStreetMap contributors, ODbL), served by Forager's tile`.

**Site, seen by site visitors (2):**

| # | File:line | Exact text |
|---|---|---|
| iii-9 | zynergy-site `Forager/forecast/app.js:68` | `attribution: '<a href="https://github.com/protomaps/basemaps">Protomaps</a> © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>; landcover <a href="https://esa-worldcover.org/">ESA WorldCover</a>, CC BY 4.0' };` |
| iii-10 | zynergy-site `Forager/forecast/index.html:264` | `<li>Street map: our own tiles, built with the <a href="https://github.com/protomaps/basemaps">Protomaps</a> basemap (style BSD-3-Clause, design CC0) from OpenStreetMap data © <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, ODbL; landcover from …; Natural Earth, public domain. …</li>` |

Both describe the forecast demo's own basemap, built from the Protomaps light flavour, not the app's Trail Map.

---

## 5. Category (iv): internal only

Per-file counts for every file are in the grouped table in section 1; the full `git grep -c` listing is reproducible from the two refs. The kinds, each read in a sample:

- **`third_party/brouter`**, 726 lines in 42 Java files: BRouter's own class and field names (`OsmNode`, `OsmLink`, `OsmPath`, `OsmTrack`, `OsmFile`…). Vendored, compiled into the app, never shown. `FormatGpx.java` (14 lines) is BRouter's GPX writer; **it has no caller in `app/`** (`git grep FormatGpx -- app` finds only two test comments naming `FormatJson.java:109`), so no OSM text from it reaches an exported GPX.
- **`app/src/main` Kotlin**, 143 lines in 46 files: beyond the (ii)/(iii) literals above, these are doc comments on the osmdroid-to-MapLibre migration (`Basemap.kt:12-85`, `SightingsMap.kt:75-136`…), the `osmTimestamp`/`osm_timestamp` fields of the route-data manifest (`RegionRouteRecord.kt`, `RouteAreaClient.kt:197`, `RegionRouteData.kt:43-44`; the Worker's `manifest.ts:5,26,42-59`, `handler.ts:5,184`), BRouter failure-message matching (`BRouterFailures.kt:35-47`), imports of `btools.router.Osm*` (`BRouterTrailRouter.kt:3-5,70,106,178,209,216`), the OSM tile-policy notes behind the User-Agent (`AppUserAgent.kt:10,19`, `MapLibreInitializer.kt:24-25`), and the word "cosmetic" (8 lines, a false positive).
- **`app/src/main/assets/routing/profiles2/hiking-mountain.brf`**, 6 lines: BRouter profile comments linking `wiki.openstreetmap.org` keys and one `osmand-style` turn-instruction mode. Shipped in the APK as an asset, never displayed.
- **`app/src/main/res/values/strings.xml`**: no match. The app's visible words are Compose literals.
- **`app/src/main/assets/credits/software.json`**: no OSM match (its "Trail Map" lines are in section 7).
- **App tests**, 216 lines in 44 files; **Worker tests and fixtures**, 70 lines (`osm_timestamp` fixtures, `OsmFile.java` citations).
- **`docs/`**, 576 lines in 89 files; **`RECORD.md`** 33, **`STATUS.md`** 3, **`CLAUDE.md`** 1, **`prompts/preserved`** 13: records and audits. Read only where the (i) regex matched (section 2) or a file was a public doc (sections 3 and 7); the rest were counted, not read line by line.
- **`scripts/measure-night-inversion.py`** 3, **`scripts/verify-usgs-basemap.sh`** 2; **`app/build.gradle.kts`** 2 and **`gradle/libs.versions.toml`** 2 (notes on the removed osmdroid dependency).
- **Site vendor code**: `Forager/forecast/vendor/maplibre-gl.mjs` (3 minified lines) and `maplibre-gl-worker.mjs` (1): `pixelPosMatrix`, `terrainRttPosMatrix32f`, and MapLibre's own WebGL error text linking `wiki.openstreetmap.org/wiki/This_map_requires_WebGL`. `Forager/forecast/app.js:62` is a comment on the TileJSON's credits.

---

## 6. origin/main against origin/pre-main

Public surfaces are identical on the two branches for this question: `README.md`, `docs/legal/privacy-policy.md` and `docs/beta/README.md` have no diff between `origin/main` and `origin/pre-main` (`git diff --stat`, empty), and the app's user-visible literals in section 3 and 4 are the same text on both (the comm of grep outputs over `app/src/main` differs only in comments and one log line).

The 25 files matching on pre-main only are the v3/v4 style work (`map-style/forager-street-v3*.json`, `-v4*.json`, `overrides/forager-fill-in.mjs`, `overrides/forager-reference.mjs`, `overrides/forager-street.mjs`, `tools/check-street-night.mjs`), their app side (`StreetNightTables.kt`, `MapTypePanel.kt`, `ReferenceTrailsVisibility.kt`, `MapLayersToolsSection.kt`, `fanout/MapTapHandler.kt`, `routing/BRouterTrailSnapper.kt`), nine tests, and four audits (`2026-10-26-ios-…`, `2026-10-28-layers-to-tools-*`, `2026-10-30-trail-heights-first-stop.md`). That is where 23 of the 26 (i) lines live; on origin/main the (i) regex finds 3 of them (`RECORD.md:1633`, `docs/audits/2026-09-27-marker-swatch-board.md:129`, `server/pmtiles-worker/test/worker.test.mjs:516`; run directly against `origin/main`, with the same three borderlines excluded). `BuildingsNightTest.kt` is pre-main only. The one file on main only, `ui/map/MapLayersSheet.kt:241`, is a comment ("a chosen Topo or OSM").

Lines on main whose text differs from pre-main (all comments or logs): `BRouterTrailRouter.kt` (`val track: OsmTrack = engine.getFoundTrack()`), `Basemap.kt:39` (names the Layers sheet instead of the map type panel), `MapModeChips.kt`, `NoSignalTrailMap.kt` (×2), `OwnStreetFallback.kt` log ("until the app restarts" rather than "until the connection returns or the app restarts").

---

## 7. B3: where our own map is called "Trail Map", and where it is called something else

**Called "Trail Map" / "Forager Trail Map", user- or visitor-visible (read):**

- The chip: `MapMode.kt:44` `TRAIL(label = "Trail", …)`; the full name `Basemap.kt:144` `label = "Forager Trail Map"` (reader: a log line only, see ii-6).
- Settings > Sources & credits: `DataSourceCredits.kt:133` `PROTOMAPS_CREDIT_TEXT = "Protomaps (Forager Trail Map and offline maps)"`; `software.json:341,346,374,379` ("53 of Forager Trail Map's 61 icons…", "Noto Sans, Forager Trail Map's labels", both notes).
- `docs/legal/privacy-policy.md:60-61` ("the default is Trail, Forager Trail Map"; "Forager Trail Map's style, its vector map tiles…").
- Worker README `server/pmtiles-worker/README.md:54,55,61` (the v2 row: "Trail Map with its character"; the sprite row).
- In total, `Trail Map` appears in 27 `app/src/main` Kotlin files and `software.json` (28 files under `app/src/main`, `git grep -l`), the privacy policy, the Worker README and `src/index.ts:395,412` (comments).

**Called something else, or not named (read):**

| Where | What it says | Note |
|---|---|---|
| `README.md:32` | "**Maps** is the main screen. Street, topographical (OpenTopoMap) and satellite (USGS imagery) base maps;" | No "Trail Map"; satellite was removed 2026-09-28 (`MapMode.kt:16-18`). |
| `README.md:148-150` | "the street, topographical and satellite base maps"; "MapLibre demo glyph server \| text on the vector map"; "The project's own tile worker … \| the vector tiles behind offline map downloads" | Our map is "the vector map" / "offline map downloads"; the glyph-server row is also stale, since fonts are served by the Worker (`privacy-policy.md:61`, `software.json:379`). Not verified against the running app, inferred from those two files. |
| `docs/beta/README.md:43-44` | "…from OpenStreetMap, OpenTopoMap, USGS and a Cloudflare Worker this project runs" | Our map is "a Cloudflare Worker". |
| Worker README `server/pmtiles-worker/README.md:54` | "Forager's Street style as it was before Trail Map's character" | v1 is called "Street"; the served paths are `/style/street-v1..4.json`. |
| Style JSON `name` fields (section 4) | "Forager Street v1" … "Forager Street v4" | The style a MapLibre client sees is named "Street", not "Trail Map". No UI reads it: a grep of `app/src/main/java` for `style.name`, `getStyle().name` and `styleName` finds no reader of a MapLibre style's `name` (the `styleName` hits in `layers/MapLayers.kt:93,505` are layer paint-property names; the rest are GPX `<name>` elements). Read, from that grep. |
| zynergy-site `privacy/index.html:116-118` | "Our map tile service" / "The offline map style, and vector map tiles by coordinate" / "The offline basemap, and downloading an offline region" | The published privacy page does not say "Trail Map", and describes the Worker as serving only the offline basemap, which predates Trail Map being the default online map (RECORD -1554). The repo copy at `docs/legal/privacy-policy.md:60-61` was updated; the site's was not (inferred from the two texts; the site's deploy state was not checked). |
| zynergy-site `Forager/forecast/*`, `README.md:40,207-214`, `functions/_middleware.js:21` | "our own street map", "Street map only" | The forecast demo's basemap, a different map (Protomaps light flavour, `app.js:14-15`), not the Trail Map; the name is correct for what it is. |
| `app/src/main/java/com/zynergylabs/forager/app/map/StreetStyle.kt`, `OwnStreetFallback.kt`, `STREET_STYLE_URL`, `ownStreet` | identifiers | Internal; `StreetStyle.kt:6` records the rename ("Earlier this file's identifiers called it 'Street'"). |

---

## 8. A2: the store listing

**No store listing text lives in the repository.** There is no `fastlane/` directory and no `metadata/android` tree on origin/pre-main or origin/main (`git ls-tree -r` filtered for `fastlane|store|listing|whatsnew|metadata/android`: only unrelated `Restore*` sources matched). The two documents that engage with it, `docs/audits/2026-09-11-store-description-feature-audit.md` and `docs/audits/2026-09-11-listed-features-phase0-inventory.md`, audit nine claims from it and quote fragments ("plan the best time to go on a trip", "GPX calibration"); **neither contains "OSM" or "OpenStreetMap"** (neither file appears in the whole-tree match list). Where the listing text itself is kept (the Play Console, presumably) is not stated in either document, and I could not read it from here. **The live store listing is therefore unchecked**, and so is its screenshots' text.

---

## 9. What could not be checked

- **The live Play Store listing** (section 8): not in any repository; no access from this session.
- **The deployed site**: I read zynergy-site's `origin/main` tree, not the served pages. Whether `main` is what Cloudflare serves, and whether Functions emit any text at runtime beyond what `functions/*.js` holds (none of which mentions OSM), is inferred from the tree.
- **The running app's attribution control**: whether MapLibre's own attribution button is enabled and shows `fullAttribution` (iii-3) was not checked in code or on a device.
- **Screens and screenshots**: images under `docs/audits/assets|img` matched as bytes (25 files), not as text; no image was read.
- **The Forager-cartography repository**, cited at `StreetNightTables.kt:32` (`docs/design/2026-10-30-fill-in-rulings.md`): outside the dispatch's A1–A4 and not read. If it is public, it is the place most likely to say "osm-like" in prose, since the quoted rulings came from it.
- **GPX exports and backups**: no `app/src/main` exporter matched the grep, and BRouter's `FormatGpx` has no caller in `app/`; the exported files themselves were not generated and inspected.
- **Records in `docs/` and `RECORD.md`**: counted and swept by the (i) regex; not read line by line for other phrasings of "like OSM" the regex would miss (e.g. "the OSM look" without "OSM look" adjacent, or "Carto" alone — "Carto" alone was checked only on the public surfaces, where it does not occur).

## 10. Premises checked against the tree

- The dispatch places the in-app credits at "Tools > Map Layers". On pre-main the data-source credits are a Settings page: Settings > Sources & credits (`AvailabilitySettingsPages.kt:76,110,116`, `software.json:2`). `MapLayersToolsSection.kt` is the Tools section for map layers and holds one OSM comment (`:105`), no credit. Reported, not resolved.
- The dispatch's base for the prompt file was `6ce6a8d52` on records-after-173; the report's base is origin/pre-main `3b9c0b70b`, as instructed. Both were read on 2026-11-03.
