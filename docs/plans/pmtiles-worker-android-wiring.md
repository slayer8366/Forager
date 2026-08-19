# Handoff: wire the live PMTiles Worker into the real app

Status as of 2026-08-19: written for a fresh coder session to pick up cold, continuing off
`claude/pmtiles-cloudflare-worker` (this doc's own branch) and PR
[#23](https://github.com/slayer8366/Forager/pull/23) (open, unmerged, branch
`claude/phase1b-offline-packages`). Both are prerequisites for what this doc scopes, not this doc's
own subject — read them before writing code, not after.

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
- Whether `claude/pmtiles-cloudflare-worker` has a PR open yet, or is still branch-only.
