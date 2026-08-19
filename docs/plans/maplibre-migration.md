# Migration spec: osmdroid → MapLibre Native

Proposed for `docs/plans/`. Status: spec, not a decision. §3 contains a
fork that CLAUDE.md classifies as a stop-and-ask — it is surfaced here,
not picked.

External claims in this document are sourced in §9. Claims about the
Forager codebase are drawn from the README and CLAUDE.md as of
2026-08-18; the README's verification section is known stale (see the
plan's §0), so anything below that depends on what has been seen on
hardware should be re-checked against the owner's actual field record
before it is relied on.

## 1. Why, in terms of the target rather than the benchmark

Forager's target is a forager-first field navigator: finds tied to tracks,
tracks tied to site context, site context tied to terrain and land status.
Three of those four want geometry the app can style, query, and carry
offline. osmdroid draws raster tiles. That is not a defect in osmdroid — it
is the wrong shape for where this app is going.

One migration retires four separate line items:

1. **The offline basemap ceiling.** The current offline path is USGS Topo
   only, US only, zoom ≤15 — not because that is the right product decision
   but because OSM and OpenTopoMap prohibit bulk prefetch and USGS is the one
   source that doesn't. Self-hosted OSM-derived vector tiles remove the
   prohibition entirely: you aren't hammering someone else's tile server, you
   are reading your own file.
2. **The vector subsystem.** The plan's §2 priced land/access layers as a
   new subsystem — geometry storage, rendering, offline packaging. On a vector
   renderer, most of "rendering" becomes a styled layer over a GeoJSON or
   vector-tile source. The pipeline work (clip, simplify, version) remains
   real. The engine work largely does not.
3. **Hand-rolled offline management.** `PersistentTileWriter` exists because
   osmdroid's own writers resolve their path from a process-wide singleton the
   browsing map already claims. MapLibre ships an offline region manager —
   though see §3, because this is not the straightforward win it appears to
   be.
4. **Zoom past 15.** Reading terrain at the range where you actually decide
   which drainage to walk.

What it does **not** buy: anything about data licensing. Parcel boundaries
remain a commercial dataset either way.

## 2. What survives the migration

More than expected, because the vendor boundaries were already drawn:

- **`MapSlot`** — the seam the screen fills instead of naming osmdroid.
  This is the reason the migration is tractable at all: `AvailabilityScreen`
  never learns which renderer is behind it. Strangler-fig the swap through
  this interface.
- **`Basemap`** — already pure Kotlin (labels, coverage limits, zoom
  ceilings, attribution; no osmdroid, no Compose). It becomes a catalogue of
  style URLs instead of a catalogue of `ITileSource`s. `BasemapTileSources`
  is the only file that has to change shape.
- **`OfflineMapRepository`** — the domain interface holds. Only the
  implementation is replaced.
- **All of `domain/`** — `Dbscan`, `GeoDistance`, `MgrsConverter`,
  `GeoBoundingBox`, every use case. Zero Android imports was the right call
  and it pays here.
- **Every test in `app/src/test/`** that isn't `SightingsMap*` or
  `BasemapTileSource*`.

## 2b. What gets spent

Be clear-eyed: this is not free.

- **`SightingsMap`** — rewritten. Including the `clipToBounds()` fix and its
  recorded reasoning about osmdroid drawing beyond its viewport. MapLibre
  has its own clipping behaviour; that finding does not transfer, and the
  documented mechanism becomes historical.
- **`BasemapTileSourceTest` and `SightingsMapBasemapSwapTest`** — both read
  facts off osmdroid's pinned artifact. Both are retired, and equivalents
  written against MapLibre.
- **`OsmdroidOfflineMapRepository` and `PersistentTileWriter`** — deleted.
- **The dot markers, numbered area markers, and dashed connectors** — all
  become style layers rather than osmdroid `Overlay`s. The dashed
  connector is the one carrying a safety property, and it is one of the few
  things confirmed on hardware. It must be re-confirmed after the swap, not
  assumed.
- **The overlay-colour question re-opens.** Bark brown at 70% alpha and
  mushroom orange were chosen against a specific palette; a new basemap
  style is a new palette. The existing decision to not retint until it had
  been seen still applies, one basemap later.

## 3. The fork: how offline actually works

This is the decision that needs the owner, and it is not obvious.

MapLibre Android supports PMTiles sources — including `pmtiles://file://`
pointing at local device storage. It also ships an offline region manager
with pause/resume, per-tile progress, and eviction policies. The trap is
that these two features do not compose: MapLibre's own Android docs
state plainly that PMTiles sources do not support offline pack downloads or
caching. An open upstream issue proposes bridging them by populating the
offline database from PMTiles extracts; it remains a feature request, noting
that the current offline download mechanism is the existing alternative. A
maintainer response in a related discussion is blunter: the offline
functionality available today is quite limited, and many users have built
their own custom solutions.

So there are three real options.

**Option A — Pre-built regional PMTiles extracts**

Produce extracts server-side; ship them to the device; point the style at
`pmtiles://file://`.

The `pmtiles` CLI can extract a region directly from the remote planet build
without downloading the whole planet, and Protomaps maintains a basemap
tileset built from OpenStreetMap that can be downloaded free or extracted by
area. Hosting is a static file on object storage.

For: no server to run, no per-request cost, no rate limits, no bulk-
download policy to violate. Simplest possible operational story, which
matches this project's scale.

Against: region granularity is whatever you pre-built. **The user cannot
draw an arbitrary box around their patch** — which is exactly what the
current long-press picker does today. That is a capability regression
on a shipped, working feature.

Also: `pmtiles://asset://` does not work — `AssetManagerFileSource`
doesn't implement the byte-range reads PMTiles needs, so files must live
in device storage via `file://`. Bundling in the APK is out; it's a
download either way.

**Option B — Own tile endpoint + MapLibre OfflineManager**

Serve z/x/y from your own PMTiles archive (go-pmtiles, or a Worker over R2),
and use `OfflineManager`/`OfflineRegionDefinition` for user-drawn regions.

For: keeps arbitrary user-drawn regions. Gets pause/resume, per-tile
progress, and eviction as library features — most of the plan's "named
offline trip packages" without hand-rolling it.

Against: you now run a service. That is a cost, an uptime obligation,
and an abuse surface for a hobby-scale project with no accounts. It is
also the first piece of always-on infrastructure this project would own.

**Option C — Hybrid**

Ship coarse regional extracts (Option A) as the always-available floor;
offer high-zoom user-drawn packs over an endpoint (Option B) as an online-
time refinement.

For: degrades honestly — there is always something offline.

Against: two offline paths, two failure modes, two sets of readiness
states. Real complexity, and the readiness screen already has five states
to communicate.

**Decision (owner, 2026-08-18): Option C, the hybrid.** Ship coarse
pre-built regional extracts as the always-available offline floor, and
build the own-tile-endpoint path for user-drawn custom regions as part of
this migration rather than deferring it.

Rationale, from the owner: building only Option A now and coming back for
custom regions later means re-touching every file the migration already
touches once — the offline package manager, the readiness-state UI, the
region picker — a second time, on a codebase that will have grown around
the map layer in the meantime. The custom-region capability is not
speculative; it is the same long-press "download this area" gesture the
app already ships today, so this is not new scope invented for the
migration, it is a scope cut (Option A alone) that this decision declines
to take. Doing both pieces of infrastructure in the same migration, while
the renderer swap already has everything opened up, is judged cheaper than
doing them in two passes.

Rejected alternative: Option A alone (defer B indefinitely). Originally
recommended for being the only zero-infrastructure option, but rejected
because the region-picker regression it required was judged a worse
long-term cost than building the server now.

Accepted consequence of C, explicitly: **two offline code paths and two
sets of failure states**, both needing readiness-screen representation —
"coarse regional extract available" is a different state from "custom
high-zoom pack downloaded," and both are different from "not covered."
The five-state readiness model in the main plan's §3 needs to represent
this distinction, not just the offline/online binary.

**Refinement (owner, 2026-08-18): build both, activate one.** Both paths
are built in this migration, but only the pre-built-extract path — free,
static hosting, nothing running — is turned on for real users at launch.
The tile endpoint ships code-complete and reachable from a dev/staging
config, not a production one, until the app has revenue or funding to
sustain running it. This is a launch-config decision, not an engineering
one: no rework needed to flip it on later, which is the entire reason for
building it now instead of after. What Phase 1b actually has to decide is
what the user-drawn region picker does in production while the endpoint
is dark — degrade to the nearest pre-built regional extract, or read as
unavailable — and represent whichever it is as a real readiness state,
not a silent substitution.

## 4. Compose integration

Lowest-risk path: keep the current pattern. `AndroidView` hosting MapLibre's
`MapView`, behind `MapSlot`, exactly as osmdroid is hosted now. MapLibre
Native Android is a View-based SDK; Compose integration exists through
external projects such as Ramani Maps and MapLibre Compose Playground,
plus a first-party `maplibre-compose`. Do not adopt a Compose wrapper as
part of this migration. Two unproven layers at once means a rendering
failure has two candidate causes. Swap the renderer first, behind the seam
that already exists; revisit the wrapper later if the `AndroidView` bridge
proves awkward.

## 5. Point-in-polygon stays in domain/

Tempting to answer "what am I standing in" with the renderer's feature-query
API. Don't. Those APIs answer questions about rendered geometry — what is
on screen, at the current zoom, after style filtering. "Which management
unit contains this coordinate" is a data question, not a rendering question,
and the answer must not change because the user zoomed out.

Write point-in-polygon in `domain/`, hand-rolled, alongside `GeoDistance`
and `Dbscan` — the same choice this project already made three times, for
the same reason. Great-circle-aware, unit-tested headless, no Android
imports.

## 6. Sequencing — and the parallelism it unlocks

The important observation: most of Phase 1 is map-independent. Splitting
it lets the migration happen without stalling feature work.

**Track 1 (start immediately, no map involvement):**
foreground location service and its notification · track sampling, accuracy
filtering, battery modes · track/waypoint Room schema and migrations · track
statistics · GPX import/export · coordinate formatting and MGRS ·
compass/sensor plumbing · return-to-start bearing computation.

All of it is domain, data, and service work. All of it is headless-testable.
None of it cares which renderer draws the result.

**Track 2 (the migration):**
1. MapLibre + a style behind `MapSlot`, rendering nothing but the basemap.
   Verify on hardware before anything else lands on it.
2. Re-implement sighting dots, area markers, dashed connectors as style
   layers. Re-confirm the dash reads as dashed. Re-open the colour question.
3. Offline packages per §3's resolved decision (Option C): both halves
   ship in this migration, not staged separately —
   - the pre-built regional extract path (static hosting, `pmtiles://file://`),
   - the own-tile-endpoint path (`OfflineManager`/`OfflineRegionDefinition`
     against a self-hosted PMTiles archive) for the user-drawn region
     picker, replacing today's `OsmdroidOfflineMapRepository` behavior
     rather than regressing it.
   Retire `PersistentTileWriter`.
4. Delete osmdroid. Only after 1–3 are seen working.

**Converge:** track breadcrumbs, waypoint markers, and the offline readiness
screen are written once, on the new renderer.

The cost of not splitting this way is writing every map overlay twice.

## 7. Risks, named

- **Style/glyph/sprite assets must be local for true offline.** A style
  referencing remote fonts and sprites renders label-less or fails when the
  user is where they actually need it. Bundle glyphs and sprites; verify
  offline with the radio off, not with a fake network error.
- **Confirmed on hardware (step 3, PR #23): offline *downloads* of a style
  with glyphs can crash outright, not just render label-less.** MapLibre's
  own public vector demo style (two `text-field` layers) reliably crashed
  the whole app during `OfflineManager.createOfflineRegion`/download — a
  native (JNI) crash, invisible to both a `Thread.UncaughtExceptionHandler`
  and `OfflineRegionObserver.onError` — landing consistently right after the
  first `onStatusChanged` callback, independent of region size (reproduced
  from ~110km/z0-8 down to ~4.4km/z10-14, same checkpoint every time). A
  same-bounds, same-zoom raster-only style with no glyphs downloaded cleanly
  every time under identical instrumentation. Before shipping offline
  downloads of any real labeled vector basemap, re-test this specific
  failure against the MapLibre release in use at that time; if it still
  reproduces, offline downloads of that style need to ship without the
  label layer rather than assume region-size tuning will avoid it.
- **Vector rendering is GPU work.** `minSdk` 26 covers old, weak devices —
  precisely what someone carries into the woods as a beater phone. Battery
  draw during multi-hour track recording with a vector map on screen is a
  real unknown and a field-measurable one.
- **Storage.** Regional vector extracts are not small. Storage budgeting
  moves from nice-to-have to required.
- **The migration spends verified ground.** The one thing confirmed on
  hardware in the map layer is the dashed connector. Re-verifying is
  non-optional.
- **ODbL attribution.** Protomaps tilesets are Produced Works of the
  OpenStreetMap dataset under ODbL, and native apps using them must visibly
  attribute OpenStreetMap. The app already renders attribution; it must
  follow the new source, and `Basemap` already owns attribution as data.
- **Standing infrastructure, built here, activated later.** Option C
  means this project builds a live tile-serving endpoint (go-pmtiles, or a
  Worker over R2), but per the owner's activation refinement above it
  stays off for real users until the app can sustain it — so the ongoing
  cost and abuse surface are deferred, not incurred, at this migration's
  launch. They are not eliminated: whenever activation happens, rate-limit
  or otherwise bound the endpoint before flipping it on, the same way
  iNaturalist's and Open-Meteo's rate limits are already respected
  elsewhere in this app. Track activation as its own decision point with
  its own go/no-go, not something that happens implicitly once the code
  exists — this project does not currently own or operate anything that
  stays running between requests, so turning this on is a deliberate step
  up in operational surface, not a formality.

## 8. Verification plan

**Headless (real, and worth doing):**
- Basemap catalogue as pure Kotlin, unchanged in kind — style URLs, zoom
  ceilings, coverage extents, attribution strings.
- Point-in-polygon, including antimeridian and pole cases, per the
  `GeoDistanceTest` precedent.
- Offline package manifest/status round-trip, including corrupt-file reads
  as "nothing downloaded" — port `OfflineMapStatusFileTest` forward.
- ViewModel state machines against a fake repository.

**Hardware only, and must be logged as field checks:**
- Basemap renders; labels present with the radio off.
- Dashed connector still reads as dashed.
- Overlay colours against the new style's palette — then a colour decision
  with a reason, per the existing recorded stance.
- Battery draw over a multi-hour recorded track with the map visible.
- Storage and download behaviour on a real network, including interruption.

## 9. Sources and verification status

Verified by reading vendor documentation this session (2026-08-18):
PMTiles-vs-offline-pack incompatibility and the `asset://` byte-range
limitation (MapLibre Android examples); OfflineManager capabilities
(MapLibre Android API docs); the April 2026 offline-manager rebuild
(MapLibre newsletter); Compose integration status and the Android artifact
(maplibre-native README); PMTiles regional extraction and Protomaps planet
builds (Protomaps site, plus two independent write-ups); ODbL attribution
terms (protomaps/basemaps README).

**Resolved (2026-08-18): raster-DEM sourcing and licensing, scoped.** The
question was never really "is USGS data licensed" — that was settled
elsewhere in this repo already (public domain, no restriction, citation
requested — same basis as USGS Topo). The open question was which actual
*delivery path* gets from that data to a renderable tile, and whether that
specific path permits offline redistribution. Researched this session, by
querying primary/near-primary sources rather than assuming:

- **Source: USGS 3DEP raw elevation, not a third-party pre-built
  tileset.** 3DEP's 1 arc-second and 1/3 arc-second DEMs are public domain
  with no use restriction (per USGS's own FAQ page, "Are there any costs or
  restrictions to usage of data downloaded from The National Map?" — relayed
  via search, since `usgs.gov` is blocked by this environment's egress proxy
  the same way `opentopomap.org` was for the basemap research; a
  primary-source spot-check from a reachable environment is still worth
  doing before this is fully relied on, same caveat as §9's basemap
  citations already carry). Bulk access is a plain, unsigned, public AWS S3
  bucket — `https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/1/TIFF/`
  — pre-tiled GeoTIFF/COG, no API key, no bulk-download prohibition of the
  kind that ruled out OpenStreetMap/OpenTopoMap for offline map tiles.
- **Rejected: the AWS `elevation-tiles-prod` / Tilezen "Terrarium"
  composite tileset**, despite being the obvious pre-built alternative
  (already packaged, already the tileset most MapLibre terrain tutorials
  reach for). Its own attribution doc
  (`tilezen/joerd/docs/attribution.md`) lists per-source terms across
  SRTM, GMTED2010, NED/3DEP, ETOPO1, ArcticDEM, and several *non-US*
  regional government datasets (Australia, Canada, Europe, Mexico, New
  Zealand, Norway, UK), each under its own license, and does not itself
  state whether bulk redistribution inside a mobile app is permitted.
  Since Forager's Phase 3 layer catalogue is already US-only by design
  (§3 of the main plan), none of that composite's non-US complexity is
  actually needed — pulling single-source USGS 3DEP directly avoids the
  multi-license question instead of resolving it.
- **Processing: self-hosted, mirroring the basemap's own Option A/C
  pipeline exactly**, not a new pattern. `gdalwarp` (reproject to
  EPSG:3857) → `gdaldem hillshade`/`gdaldem slope`/`gdaldem aspect` or
  `rio-rgbify` (encode as Terrain-RGB) → the same `pmtiles` CLI already
  used for basemap regional extracts. This is an established, documented
  workflow (multiple independent write-ups converge on the same tool
  chain), not a novel pipeline this project would be first to build.
  Because the source is public-domain and self-processed, there is no
  third party's redistribution terms to satisfy at all — the project
  redistributes its own derived output, the same footing PAD-US/USFS/NHD
  already stand on in the Phase 3 catalogue.

**What this resolves:** Phase 2 is not licensing-blocked. **What it
doesn't resolve, and shouldn't be conflated with licensing:** this adds a
second self-run processing pipeline alongside the basemap's (real
engineering work, scope it into Phase 2's estimate, not assumed free
because the data is free), and DEM-derived rasters at useful resolution
are not small — hillshade+slope+aspect for a region roughly triples the
raw DEM's footprint, so measure real regional sizes before committing to
bundling them offline, the same storage discipline §7 already calls for
on the basemap extracts.

Not verified: everything about how MapLibre performs on this project's
`minSdk` floor. No MapLibre code has been run in this repo.
