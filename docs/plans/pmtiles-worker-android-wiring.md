# Handoff: wire the live PMTiles Worker into the real app

Status as of 2026-08-19: written for a fresh coder session to pick up cold, continuing off
`claude/pmtiles-cloudflare-worker` (this doc's own branch, PR
[#24](https://github.com/slayer8366/Forager/pull/24), open/unmerged) and PR
[#23](https://github.com/slayer8366/Forager/pull/23) (open, unmerged, branch
`claude/phase1b-offline-packages`). Both are prerequisites for what this doc scopes, not this doc's
own subject — read them before writing code, not after.

## Update, 2026-08-19: steps 3-4 done, steps 1-2 deliberately deferred

A later session on `claude/offline-maps-integration-21uez7` picked this doc up and, after asking the
owner the three open decisions below rather than picking silently, did steps 3 and 4 for real —
**not** steps 1-2. Concretely:

- **Style JSON (step 1's asset, generated but not wired to a live view)**: both the labeled and
  glyph-stripped style JSON were generated with Protomaps' own published `@protomaps/basemaps` npm
  package (`layers(source, LIGHT, options)`), pointed at
  `https://forager-pmtiles.brandonlee1-894.workers.dev/us.json` — not hand-built, and not a guess at
  their layer catalogue. The labeled variant (71 layers, 14 with `text-field`/glyphs, for a future
  live basemap) was generated but **not bundled into the app**, since nothing consumes it yet and an
  unreferenced asset is exactly the "half-finished implementation" CLAUDE.md flags. The glyph-stripped
  variant (57 layers, zero symbol layers — confirmed programmatically, not assumed) **is** bundled, at
  `app/src/main/assets/forager_pmtiles_offline_style.json`.
- **Step 3 (offline-download style, glyph-stripped)**: done, using the asset above.
- **Step 4 (`OfflineManager` wired into the real `OfflineMapRepository`)**: done.
  `com.forager.app.map.MapLibreOfflineMapRepository` replaces `OsmdroidOfflineMapRepository` (deleted,
  along with `PersistentTileWriter` and `OfflineMapStatusFile` — both now fully unused) in
  `AppContainer`. Every `OfflineManager`/`OfflineRegion` method and callback shape used was checked
  with `javap` against the pinned `org.maplibre.gl:android-sdk:13.5.0` artifact rather than assumed
  from documentation — see that class's own doc comment for specifics.
- **Steps 1-2 (a live, labeled vector basemap reachable from `MainActivity`/`MapSlot`/
  `AvailabilityScreen`, `Basemap` becoming a vector-style catalogue)**: **not done.** The owner's
  decision this round was scoped to offline downloads switching to the PMTiles vector source, not to
  replacing the four existing osmdroid basemaps live rendering depends on — and this session had no
  hardware or emulator to verify a live-rendering swap on, unlike PR #23's own hardware-confirmed
  mechanism proof. Doing steps 1-2 blind would mean shipping an unverified rendering-engine change to
  the app's primary map screen, which is a real regression risk this repo has no way to catch short of
  a physical device. Left as explicit future work, not silently dropped.

**Not hardware-verified, flagged rather than assumed working**: the new offline-download path's full
mechanism — create region → download → persist across restart → the glyph-stripped style actually
avoiding PR #23's confirmed native crash — was proven by PR #23 against `raw.githubusercontent.com`
-hosted styles with placeholder content (a demo style, a minimal USGS raster style), not against this
project's real `asset://forager_pmtiles_offline_style.json` or its real 57-layer Protomaps geometry.
The `asset://` scheme for a style URL (as opposed to a `pmtiles://` tile source) is standard
Mapbox-lineage `AssetFileSource` behavior, but was not itself hardware-confirmed by either session.
Spot-check on a real device before trusting this further, per this doc's own "verify this yourself"
callouts below (still accurate, re-read them) and CLAUDE.md's testing standards.

## Update, 2026-08-19 (later): the `asset://` spot-check above found a real bug — fixed and confirmed

The spot-check the previous update flagged as needed was done. Result: the `asset://` theory in that
update's "standard `AssetFileSource` behavior" line was wrong. Hardware repro on
`claude/offline-maps-integration-21uez7`: the real "Offline Maps" screen hangs indefinitely at
`"0 / 1 tiles"` after tapping Download — the exact `completed=0/1` stall PR #23 already isolated for
a *different* style (its raster test style, also loaded via `asset://`), not a new, unrelated bug.
`OfflineManager`'s resource-discovery path doesn't resolve `asset://` style URLs at all, regardless
of whether the read is a small sequential style-JSON read (this case) or PMTiles' byte-range tile
reads (the case PR #23's own doc comment named) — the distinction `MapLibreOfflineMapRepository`'s
original doc comment drew between those two didn't hold up against actual hardware behavior.

Fixed the same way PR #23 fixed it: stopped using `asset://`, host the style at a real HTTPS URL
instead. Concretely:

- `server/pmtiles-worker/src/index.ts` (branch `claude/pmtiles-cloudflare-worker`, PR #24) now serves
  the exact same style content at `/style/offline.json`, bundled directly into the Worker (a static
  38 KB document, not read from R2 per-request).
- `MapLibreOfflineMapRepository.OFFLINE_STYLE_URL` now points at
  `https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json` instead of
  `asset://forager_pmtiles_offline_style.json`.
- `app/src/main/assets/forager_pmtiles_offline_style.json` is deleted — nothing references it anymore
  (verified: `grep -rn forager_pmtiles_offline_style.json app/src` after the change finds only the
  two doc-comment mentions of the old, broken approach, kept deliberately as history).

**Hardware-confirmed on the owner's device**: fresh APK from this fix, real "Offline Maps" screen, a
6 km radius around a real Portland-area coordinate (45.3368, -122.6016) — completed cleanly at
**88 tiles, 1.9 MB**, no hang, no native crash. The tile count moved past `0/1` immediately rather
than sitting there, confirming the `/style/offline.json` route resolves correctly through
`OfflineTilePyramidRegionDefinition` where `asset://` didn't, and that the glyph-stripped 57-layer
style avoids PR #23's confirmed native crash on the app's *real* style, not just PR #23's own
placeholder test styles.

**Still not hardware-verified**: persistence across a full app restart, and offline replay with the
radio off (airplane mode) — PR #23 confirmed both of these for its own placeholder styles, but this
project's real style/region combination hasn't been taken through that same restart+airplane-mode
cycle yet. Worth doing before calling this fully proven, following the same steps PR #23 used.

## What already exists and is verified — don't re-verify, build on it

**The tile-serving infrastructure is live and confirmed working**, per
`server/pmtiles-worker/README.md` on this branch:

- A Cloudflare Worker at `https://forager-pmtiles.brandonlee1-894.workers.dev`, deployed via
  Cloudflare Workers Builds (Git-connected to this repo, root directory `server/pmtiles-worker`,
  branch `claude/pmtiles-cloudflare-worker` — redeploys automatically on push).
- An R2 bucket `forager-maps` (account `a6a899e01e2194ef8fff048c20130e14`) holding a continental-US
  PMTiles archive (`us.pmtiles`, ~8.8 GB, zoom ≤14, Protomaps `20260819` build).
- Confirmed by fetching a real tile: `GET /us/10/200/380.mvt` returns a genuine `.mvt` vector tile
  (decoded and inspected — real `earth`/`landuse`/`roads`/`water` layers, e.g. US Route 191, Big
  Sandy River). This is real US map data, not a stub.
- The tile URL pattern this Worker serves is `/us/{z}/{x}/{y}.mvt` (source name `us`, from the
  object key `us.pmtiles` — see `pmtilesPath` in `server/pmtiles-worker/src/shared.ts`). A
  `/us.json` request returns TileJSON metadata for the same source.

**Separately, PR #23 confirmed on real hardware** (do not re-run this testing, it's done):

- MapLibre Android's `OfflineManager` can download, persist across an app restart, and replay
  offline a region defined by `OfflineTilePyramidRegionDefinition` — the mechanism this doc's task
  depends on.
- **A confirmed, isolated finding**: an offline download of a style with glyph/label (`text-field`)
  layers crashes the whole app natively, regardless of region size. A style with the same vector
  source and geometry but *no* glyph layers downloads cleanly. Full isolation evidence is in
  `docs/plans/maplibre-migration.md` §7 and PR #23's description — read it before choosing what
  style to point the real offline download at (see "Open decision" below).

## What this doc scopes: connecting the two

Nothing here is built yet. The task is making the real app's offline map download actually use
`forager-pmtiles.brandonlee1-894.workers.dev` instead of `OsmdroidOfflineMapRepository`'s
USGS-only, osmdroid-cache-format downloader — per `docs/plans/maplibre-migration.md` §6, Track 2
step 3 (retiring `PersistentTileWriter`), still gated per that plan's own sequencing on steps 1-2
(MapLibre rendering the real app's basemap and overlays) being done first, which as of this writing
**have not been wired into the real app either** — `MapLibreBasemapPreviewActivity` proved the
mechanism in a debug-only scaffolding screen, never reachable from `MainActivity`/`MapSlot`/
`AvailabilityScreen`. Confirm this is still true before assuming it (`git log --oneline -- app/src/main/java/com/forager/app/ui/map/Basemap.kt app/src/main/java/com/forager/app/ui/map/SightingsMap.kt`
should show no MapLibre-related commits if so).

Concretely, in rough dependency order:

1. **A real style JSON for the live app to render this Worker's tiles**, styled like the Protomaps
   basemap actually looks (not the bare geometry MapLibre's demo style used) — with labels for
   online rendering, since there's no crash risk there (the confirmed bug is specific to *offline
   downloads*, not live rendering). Protomaps publishes a reference style for their basemap schema;
   check `docs.protomaps.com` for it (blocked from this session's own sandbox — reachable from a
   real browser) rather than hand-rolling the layer list from scratch.
2. **`Basemap`/`BasemapTileSources` becoming a vector style option**, per
   `maplibre-migration.md` §2 ("`Basemap` ... becomes a catalogue of style URLs instead of a
   catalogue of `ITileSource`s"). This is real product surface, not a debug screen — do this
   carefully, and re-confirm the dashed connector and overlay colours against the new style once
   it's live (§2b flags both as re-opened by a new basemap style).
3. **The offline-download style variant, deliberately different from what's rendered live**: per
   the confirmed glyph-crash finding, whatever style `OfflineTilePyramidRegionDefinition` downloads
   must have its label/glyph layers stripped, independent of what step 1's style shows online. Two
   ways to get there — pick one and record which, don't leave it implicit:
   - A second, label-less style JSON hosted alongside the real one, used only for the offline
     download call.
   - Post-process/filter the real style's layer list at request-construction time before handing it
     to `OfflineTilePyramidRegionDefinition`.
4. **Wiring `OfflineManager` calls into `OfflineMapRepository`'s real implementation**, replacing
   `OsmdroidOfflineMapRepository` — the region-picker UI, download progress, and persistence already
   exist there; PR #23's `MapLibreBasemapPreviewActivity` code is the *reference* for the actual
   `OfflineManager` API calls (region definition, observer, status handling), not code to keep
   long-term — it's explicitly scaffolding, delete it once this is done.

## Open decisions — surface these, don't pick silently

Per `CLAUDE.md`: an unmade architectural decision is a stop-and-ask.

- **Does the region picker stay USGS-only, or switch to whatever this US-only PMTiles archive
  covers?** The current `OsmdroidOfflineMapRepository` is USGS Topo/Imagery, US-only already, so
  coverage is similar — but the *data* (OSM-derived vector vs. USGS raster) and the resulting look
  are a real product change the owner should see and approve, not something to swap silently.
- **Does PR #23 get merged as-is first, or does this work fold into the same branch/PR?** PR #23 is
  scoped as a smoke test with no production wiring; this doc's work is the production wiring. They
  could merge separately or together — ask rather than assume either way.
- **Style JSON sourcing**: hand-building a Protomaps-schema style vs. using their published
  reference style vs. a simpler custom style — affects both what step 1 needs to build and what
  step 3's label-stripping needs to filter. Pick this deliberately.

## Verify-this-yourself callouts

Facts here may have shifted between this doc being written and being picked up:

- The Worker URL, bucket contents, and account ID above — confirm the Worker still responds before
  building against it (`curl -I https://forager-pmtiles.brandonlee1-894.workers.dev/us/10/200/380.mvt`
  should be `200`, `Content-Type: application/x-protobuf`).
- Whether PR #23 has been merged, closed, or changed since — check its current state rather than
  trusting this doc's description of it.
- Whether PR [#24](https://github.com/slayer8366/Forager/pull/24) (`claude/pmtiles-cloudflare-worker`)
  has been merged since — check its current state rather than trusting this doc's description of it.
