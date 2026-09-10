# Phase 1 report — night-mode tile inversion: measuring the ground

**Date:** 2026-08-28. Read-only investigation against `main@4e72f06`, plus a measurement script
(`scripts/measure-night-inversion.py`, committed — real network calls against live tile servers,
not a CI check). No app behavior changed. Every number below is re-derived from current source or
real fetched tiles — nothing is carried forward from a design document.

**Headline, before the five numbered items**: two of this dispatch's own framing assumptions
don't hold against current code, found while answering items 1 and 3. Both change the shape of
Phase 2/3, not just this report, so they're surfaced here first.

1. **`nightModeMaps` today has one effect, and it's subtle** — not "night mode" in any way a user
   would likely recognize as such. See item 1.
2. **There is no "offline" rendering path to measure separately.** The offline PMTiles download is
   a completely different tile format (vector, not raster), a completely different provider
   (a self-hosted Cloudflare Worker serving Protomaps, not any of the three live basemap
   services), and — critically — **the live map never renders it**. "Online vs. offline, for each
   night-eligible basemap" isn't two measurements of the same thing; it's one real thing (the
   three live raster basemaps) and one unrelated, currently-unrendered thing. See item 2/3.

---

## 1. What night mode actually does today

Traced `nightModeMaps` from `DataStoreMapPreferencesRepository` outward — every file that reads or
writes it:

- **Storage**: `DataStoreMapPreferencesRepository.getNightModeMaps`/`setNightModeMaps`
  (`data/repository/DataStoreMapPreferencesRepository.kt:59-66`), DataStore-backed.
- **State**: `AvailabilityUiState.nightModeMaps: Boolean = false`
  (`ui/availability/AvailabilityUiState.kt:159`), loaded on `AvailabilityViewModel` init
  (`:676-677`), written by `onNightModeMapsChanged` (`:689-693`).
- **The only write path**: `NightModeMapsSection`, a Settings checkbox
  (`AvailabilityScreen.kt:1751`, `:1788`). No long-press, no automatic trigger — both were deleted
  by `aac8520` (see the 2026-08-28 report on that commit).
- **Where it's read for rendering**: `AvailabilityScreen.kt:524`,
  `val mapRenderMode = MapRenderMode(basemap = basemap, night = isNightMode)` — flows into
  `mapSlot(...)`'s `renderMode`, then `SightingsMap`'s `nightMode: Boolean` parameter.

**What `nightMode = true` actually changes, traced to the end of every path:**

1. `styleJsonFor(basemap, night = true)` (`BasemapStyles.kt:73-90`) appends one paint block to the
   basemap raster layer: `raster-saturation: -0.35`, `raster-contrast: 0.1`
   (`NIGHT_RASTER_PAINT`, `:67-71`). This is the *entire* visual effect on the map itself.
2. `SightingsMap.kt:194-197`: `val mapPalette = MapPalette.DAY` — **unconditional**, regardless of
   `nightMode`. Markers, the search-centre marker, planned-trip diamonds, breadcrumbs, waypoints —
   none of them change with night mode at all (confirmed by `aac8520`, see the earlier report).
3. `MapIconBar`'s Layers-icon content description appends " Night mode on."/" Night mode off."
   (`AvailabilityScreen.kt:3936`) — an accessibility string only, no visual effect.
4. No brightness/dimming change — removed 2026-08-26, per `BasemapStyles.kt`'s own doc comment.

**Answer: "nearly none," and it should be framed that way.** Turning Night Maps on today desaturates
the basemap by 35% and nudges contrast up 10% — a real, measurable difference, but nothing close
to a distinct "night mode" a user would name as such. No dimming, no marker change, no palette
swap. This matters for scoping Phase 2/3: the *current* feature is not "night mode with a
placeholder palette," it's "a subtle basemap filter with a checkbox labeled Night Maps." Framing
the inversion work as "finishing night mode" undersells how much of it doesn't exist yet.

**One more finding, not asked for but directly relevant to Phase 2 Q2 (satellite)**: `styleJsonFor`
applies `NIGHT_RASTER_PAINT` to *whichever* `Basemap` is passed — there is no basemap-type check
anywhere in this function or its caller. **Satellite is not currently excluded in code.** If a user
selects Satellite and turns on Night Maps, `USGS_IMAGERY_ONLY`'s imagery gets the same
-0.35 saturation / +0.1 contrast treatment today. "Satellite is night-ineligible" (this dispatch's
own item 2 framing, and `MapMode.kt`'s design intent) is a *design assumption* that has no code
enforcing it yet — Phase 2 Q2 isn't choosing between two future states, it's noticing an
already-live gap.

---

## 2. Basemap inventory

All three from `Basemap.kt`, read directly:

| Basemap (`MapMode` label) | Tile source | Style/URL | `maxZoom` (operating limit) | Night-eligible (by design intent — see item 1's finding that this isn't code-enforced) |
|---|---|---|---|---|
| `OSM_STANDARD` ("Street") | OpenStreetMap standard | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | 19 | Yes |
| `OPEN_TOPO_MAP` ("Topographical") | OpenTopoMap | `https://a.tile.opentopomap.org/{z}/{x}/{y}.png` | 17 | Yes |
| `USGS_IMAGERY_ONLY` ("Satellite") | USGS National Map, orthoimagery | `https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}` (row/col reversed — see `Basemap.kt`'s own warning) | 15 | No (by intent, not code) |

All three are **raster** sources (`BasemapStyles.kt:7`, "Every basemap here stays a raster
source"), built into an inline style JSON per `styleJsonFor`, not four hosted style documents.

**Which sources serve the offline PMTiles path vs. online — the assumption to test, stated by the
dispatch itself:** **None of them.** This is the headline finding. `MapLibreOfflineMapRepository.kt:99,241`:
offline downloads use `OFFLINE_STYLE_URL =
"https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"` — a style generated by
Protomaps' `@protomaps/basemaps` `layers()` function (per that class's own doc comment,
`:52-66`), serving **vector** tiles (not raster) from a self-hosted Cloudflare Worker + R2, not
any of `tile.openstreetmap.org`/`a.tile.opentopomap.org`/USGS. It is glyph-stripped (no
`text-field` layers — PR #23's crash finding, per the same doc comment) and has never been the
labeled style described in `docs/plans/pmtiles-worker-android-wiring.md`.

**Confirmed the live map never renders this style at all**: `grep` for `OFFLINE_STYLE_URL`/
`"offline.json"`/`"forager-pmtiles"` across `app/src/main` finds matches only inside
`MapLibreOfflineMapRepository.kt` itself. `SightingsMap.kt`'s only `setStyle` call
(`:331`) is always `Style.Builder().fromJson(styleJsonFor(basemap, night = nightMode))` — one of
the three raster basemaps above, never the offline URL. `Basemap.kt`'s own class doc comment
(`:44-50`) confirms this is deliberate, not an oversight: the Worker "is explicitly scoped to the
*offline download* path only... reusing it for live browsing too is a real product/infrastructure
decision... this renderer-swap commit does not make silently."

**So "online and offline, for each night-eligible basemap" isn't two measurements of the same
thing.** There's one thing to measure (the three live raster basemaps, item 4 below), and one
separate, currently-invisible thing (the offline vector style) that no code path today applies
night styling — or any live rendering at all — to. Treating them as parallel tracks of the same
feature would be measuring something that doesn't exist yet.

---

## 3. Transform feasibility

**Raster paint properties are already reachable — styles are not URL-only.** `styleJsonFor`
(`BasemapStyles.kt:73-90`) builds a full inline style JSON (`sources`, `layers`, `paint`) per
basemap swap, not a reference to an externally hosted style document. Adding or changing a raster
paint property is a one-line change to that function — no new plumbing needed for *that* part.

**The complete declarative raster paint property set, per this file's own doc comment (verified
against the pinned `13.5.0` artifact with `javap`, not assumed from docs): `opacity`,
`hue-rotate`, `brightness-min`, `brightness-max`, `saturation`, `contrast`, `resampling`,
`fade-duration`. None of these can express a per-pixel luminance-only, hue-preserving inversion.**
`hue-rotate` is the one property that sounds relevant and is exactly the wrong tool: rotating hue
is the failure mode this transform needs to avoid, not a way to achieve it (see item 4's hue
table — a naive full inversion is mathematically a ~180° hue rotation for saturated colours,
demonstrated below with real pixels). `brightness-min`/`max` clamp/scale, they don't invert.
Currently only `raster-saturation`/`raster-contrast` are used (item 1).

**What true inversion needs instead, and what it costs**: per-tile pixel interception before
MapLibre renders — decode each fetched raster tile, apply a hue-preserving lightness inversion
(item 4 measures this exact transform), re-encode, feed the result to MapLibre in place of the
original bytes. This is new infrastructure, confirming rather than just repeating
`BasemapStyles.kt`'s own prior conclusion ("Real inversion would mean intercepting and
transforming tile images before MapLibre renders them"). Costs, as best determined from this
codebase without building it:

- **CPU**: real per-tile image decode/transform/encode work, once per unique tile fetched (256×256
  pixels — cheap per-tile, but every night-mode tile the user's viewport ever requests).
- **Caching**: MapLibre's own raster tile cache is keyed to the tile URL/bytes it fetched — a
  client-side transform means either caching the *transformed* bytes separately (doubling storage
  for anyone who uses both day and night) or re-transforming on every cache miss.
- **Offline implications**: none, currently — per item 2, there is no offline-rendered raster path
  today for this to interact with at all.
- **Exact MapLibre hook**: **not verified in this pass.** This project's networking stack uses
  OkHttp elsewhere (`AppContainer.kt`), and MapLibre's Android SDK is a plausible candidate for an
  OkHttp-interceptor-based tile rewrite, but whether MapLibre 13.5.0's raster tile fetching
  actually routes through an interceptable OkHttp client (vs. its own native HTTP stack) was not
  checked this pass — flagged rather than assumed, since a wrong guess here would misdirect Phase 3
  scoping more than an honest "unknown."

---

## 4. Measure the ground

**Walking zoom band: z14–z17.** Reasoning: `OPEN_TOPO_MAP`'s own `maxZoom` (17) is the hard
ceiling both night-eligible basemaps can be measured at together; `OSM_STANDARD` alone reaches
z18/19 but `OPEN_TOPO_MAP` has no tiles there, so a shared band stops at 17. z14 is where real
trail/road-level detail starts appearing on either source — z13 and below is regional-overview
scale, not on-foot navigation scale.

**Locations**: three real, geographically diverse points — two forest/wilderness (Mount Hood
National Forest, OR; Green Mountain National Forest, VT) matching this app's actual foraging use
case, plus one urban-edge trailhead (Marin Headlands, CA) so `Street`'s tile content isn't only
measured against forest, which it will rarely be navigating.

Full per-tile results (raw output of `scripts/measure-night-inversion.py`, WCAG relative
luminance — the exact formula `MapPaletteTest.kt`'s own `relativeLuminance()` uses):

| Basemap | Location | Zoom | As-is min/max/median/spread | Inverted min/max/median/spread |
|---|---|---|---|---|
| OSM_STANDARD (Street) | Mount Hood NF, OR | z14 | 0.0049 / 0.9195 / 0.5695 / 0.9146 | 0.0030 / 0.8649 / 0.0974 / 0.8619 |
| OSM_STANDARD (Street) | Mount Hood NF, OR | z15 | 0.0048 / 0.9191 / 0.7938 / 0.9143 | 0.0034 / 0.8879 / 0.0114 / 0.8845 |
| OSM_STANDARD (Street) | Mount Hood NF, OR | z16 | 0.0343 / 0.9911 / 0.8649 / 0.9569 | 0.0003 / 0.9126 / 0.0067 / 0.9123 |
| OSM_STANDARD (Street) | Mount Hood NF, OR | z17 | 0.0262 / 0.9841 / 0.8649 / 0.9580 | 0.0004 / 0.6509 / 0.0067 / 0.6505 |
| OSM_STANDARD (Street) | Green Mountain NF, VT | z14–z17 | 0.4135 / 0.5695 / 0.5695 / 0.1560 (identical at every zoom — see note below) | 0.0974 / 0.1643 / 0.0974 / 0.0669 |
| OSM_STANDARD (Street) | Marin Headlands, CA | z14 | 0.0435 / 0.9911 / 0.6382 / 0.9476 | 0.0003 / 0.5596 / 0.0776 / 0.5593 |
| OSM_STANDARD (Street) | Marin Headlands, CA | z15 | 0.0529 / 1.0000 / 0.6382 / 0.9471 | 0.0000 / 0.5149 / 0.0776 / 0.5149 |
| OSM_STANDARD (Street) | Marin Headlands, CA | z16 | 0.0467 / 1.0000 / 0.6382 / 0.9533 | 0.0000 / 0.5395 / 0.0776 / 0.5395 |
| OSM_STANDARD (Street) | Marin Headlands, CA | z17 | 0.0027 / 1.0000 / 0.6382 / 0.9973 | 0.0000 / 0.9216 / 0.0776 / 0.9216 |
| OPEN_TOPO_MAP (Topo) | Mount Hood NF, OR | z14 | 0.0000 / 0.9272 / 0.6730 / 0.9272 | 0.0033 / 1.0000 / 0.1119 / 0.9967 |
| OPEN_TOPO_MAP (Topo) | Mount Hood NF, OR | z15 | 0.0000 / 0.9473 / 0.6724 / 0.9473 | 0.0018 / 1.0000 / 0.0331 / 0.9982 |
| OPEN_TOPO_MAP (Topo) | Mount Hood NF, OR | z16 | 0.0000 / 0.9911 / 0.6724 / 0.9911 | 0.0003 / 1.0000 / 0.0243 / 0.9997 |
| OPEN_TOPO_MAP (Topo) | Mount Hood NF, OR | z17 | 0.0000 / 1.0000 / 0.7011 / 1.0000 | 0.0000 / 1.0000 / 0.0185 / 1.0000 |
| OPEN_TOPO_MAP (Topo) | Green Mountain NF, VT | z14 | 0.1269 / 0.9174 / 0.6465 / 0.7905 | 0.0382 / 0.4986 / 0.1542 / 0.4605 |
| OPEN_TOPO_MAP (Topo) | Green Mountain NF, VT | z15 | 0.1164 / 0.7953 / 0.6245 / 0.6788 | 0.0854 / 0.4941 / 0.1595 / 0.4088 |
| OPEN_TOPO_MAP (Topo) | Green Mountain NF, VT | z16 | 0.2190 / 0.8093 / 0.6329 / 0.5903 | 0.0452 / 0.4402 / 0.1542 / 0.3950 |
| OPEN_TOPO_MAP (Topo) | Green Mountain NF, VT | z17 | 0.1803 / 0.7953 / 0.6801 / 0.6149 | 0.0895 / 0.4343 / 0.1339 / 0.3448 |
| OPEN_TOPO_MAP (Topo) | Marin Headlands, CA | z14 | 0.0000 / 0.9905 / 0.5851 / 0.9905 | 0.0006 / 1.0000 / 0.0684 / 0.9994 |
| OPEN_TOPO_MAP (Topo) | Marin Headlands, CA | z15 | 0.0000 / 1.0000 / 0.6909 / 1.0000 | 0.0000 / 1.0000 / 0.0248 / 1.0000 |
| OPEN_TOPO_MAP (Topo) | Marin Headlands, CA | z16 | 0.0000 / 1.0000 / 0.7127 / 1.0000 | 0.0000 / 1.0000 / 0.0218 / 1.0000 |
| OPEN_TOPO_MAP (Topo) | Marin Headlands, CA | z17 | 0.0000 / 1.0000 / 0.5308 / 1.0000 | 0.0000 / 1.0000 / 0.0663 / 1.0000 |

**Note on the identical Vermont/Street rows**: verified the tile x/y coordinates genuinely differ
at each zoom (`4873,5969` at z14 through `38988,47757` at z17 — real, distinct tiles, not a script
bug). The four zooms rendering identically means this specific sample point sits in unbroken,
unlabeled forest with no rendered road/trail features at any zoom in the band — a real, valid data
point (a genuinely blank, uniform-luminance "worst case for spread," i.e. best case for
legibility-by-uniformity), not a measurement error.

### Worst case, and why raw min/max is the wrong number to author against

Global min across every sample: **0.0000**. Global max: **1.0000**. This is not useful: it's
dominated by outlier pixels (single-pixel-wide black text/road-casing lines, small white gaps
between features) that a marker is never actually asked to sit against as "the ground." The
dispatch asked for min/max/median/spread, so both are reported, but the reference constant a
palette should be authored against is the **pooled percentile across every sampled pixel**, not
per-tile extremes:

| Stat | As-is | Inverted (hue-preserving) |
|---|---|---|
| P5 | 0.3663 | 0.0056 |
| P10 | 0.4572 | 0.0067 |
| P50 (median) | 0.6382 | 0.0776 |
| P90 | 0.8649 | 0.1724 |
| P95 | 0.8903 | 0.2269 |

**Compared against the current provisional constant**: `DAY_TILE_REFERENCE = 0xFFE8E4DC`
evaluates to **0.7781** under this same formula — between the measured P50 (0.638) and P90
(0.865), closer to P90. The provisional value isn't wildly wrong, but it was never measured: it
happens to sit near the *brighter* end of real tiles rather than at the median, which is the safer
direction for a contrast floor (most real tiles are darker than 0.778, so a marker cleared against
0.778 clears most real cases with margin) but not a number anyone verified was doing that on
purpose.

**Do online and offline distributions match?** Not a meaningful question, per item 2/3: there is
no offline-rendered raster distribution to compare against. No.

### Hue behaviour

Full table in the script's own output (`scripts/measure-night-inversion.py`'s stdout); the pattern
holds without exception across all 48 dominant-colour samples measured: **the hue-preserving
(HLS-lightness) transform changes zero degrees of hue on every sample. The naive RGB-channel
invert rotates every sample by exactly 180°.** Two concrete examples, real pixels from real tiles:

- **Water** (`OSM_STANDARD`, Marin Headlands, z14 — coastal, genuinely water-adjacent):
  `RGB(170, 211, 223)`, a light blue, hue **194°**. Hue-preserving invert: still **194°**. Naive
  invert: **14°** — squarely in the red-orange range. This is the literal "water turns orange"
  failure the dispatch named, reproduced with a real sampled pixel, not asserted from theory.
- **Vegetation** (`OSM_STANDARD`, Mount Hood NF, z14 — forest fill): `RGB(173, 209, 158)`, green,
  hue **102°**. Hue-preserving invert: **102°**, unchanged. Naive invert: **282°** — blue-violet.

Confirms the transform this Phase 1 measured (HLS lightness inversion) is the right one to build,
and that the "one transform to avoid" (plain RGB channel invert) is not a hypothetical risk —
it's exactly as wrong as predicted, on real map colours.

---

## 5. What this retires

**Yes — this session's measurements are sufficient to replace `DAY_TILE_REFERENCE`/
`NIGHT_TILE_REFERENCE`'s provisional status with a measured one, for `DAY_TILE_REFERENCE`
specifically.** R5 (`docs/plans/understory-design-system.md:641-665`) declared the constant
provisional because "the test bounds the regression; it does not establish the property" — the
pooled-percentile measurement above (P50 = 0.638, real tiles, real locations, the app's own
`relativeLuminance` formula) is exactly the missing measurement. Recommend authoring against the
**median (0.638)**, not the provisional 0.778 or either percentile extreme — median is the
"typical ground" a marker spends most of its time against; P90/P95 (0.865/0.890) are what a floor
needs to *also* clear for the brighter tail, which is a Phase 3 authoring decision, not this
report's to make.

**For `NIGHT_TILE_REFERENCE` under inversion specifically**: this report measures what the ground
*would be* under a hue-preserving inversion (P50 = 0.078, P95 = 0.227) — sufficient to replace the
current placeholder (`NIGHT_TILE_REFERENCE = DAY_TILE_REFERENCE`, a known stand-in since dimming
was removed) once Phase 2 picks the transform, since this *is* that transform, measured. Not yet
a Phase-3-ready constant on its own: Phase 3's own scope note is right that day's 0.099 separation
floor and this luminance reference need to be considered together against a *newly authored*
`NIGHT` palette, not retrofitted onto the two-colour scaffold being retired.

---

## Deliverables

- This report.
- `scripts/measure-night-inversion.py` — committed, reusable: re-run with different locations,
  zoom bands, or transforms without rebuilding the tile-fetch/luminance plumbing. Requires
  `pip install pillow` and live network access to `tile.openstreetmap.org`/
  `a.tile.opentopomap.org`.
