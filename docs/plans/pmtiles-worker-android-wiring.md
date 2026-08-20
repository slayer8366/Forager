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

## Update, 2026-08-19 (later still): a second real bug found by the restart test — fixed

The persistence-across-restart check surfaced a real bug, not a persistence failure:
`getStatus()` — called automatically when the Offline Maps screen loads, to show the
"Downloaded: ..." line — never called `MapLibre.getInstance(appContext)`. `download()` did, so the
bug was invisible whenever a download happened to run first in a process. On a genuinely fresh
process (force-closed, recents cleared, reopened) where `getStatus()` runs first instead, the
native library had never been initialized, and `OfflineManager.getInstance()` inside it threw:
`"Using MapView requires calling MapLibre.getInstance(...) before inflating or creating the view."`
— shown as an inline error on the Offline Maps screen, region-picker map and Download/Delete buttons
all disabled.

Fixed by moving the `MapLibre.getInstance(appContext)` call into the private `offlineManager()`
helper all three public methods (`download`/`delete`/`getStatus`) already call, rather than only in
`download()` — every entry point now initializes the native library first, instead of relying on
one particular call happening to run before the others in a given process.

Verified: `./gradlew testDebugUnitTest` — 438 tests pass. `./gradlew assembleDebug` — succeeds.

## Update, 2026-08-20: the restart fix re-verified on hardware — persistence confirmed

The force-close-and-reopen cycle that found the `getStatus()` crash above was re-run against the fix,
on the owner's device, with a fresh APK built from the `MapLibre.getInstance()` fix. Result: no crash,
and the original persistence question this whole test chain was chasing is now answered.

Sequence: downloaded a region (5 mi radius around 39.7940, -98.5529), force-closed the app, cleared
it from recents, reopened it cold. The Offline Maps screen's `getStatus()` line — the exact code path
that threw before the fix — loaded cleanly and read **"Downloaded: 5 mi around 39.7940, -98.5529 —
139 tiles, 0.1 MB."**, no inline error, no crash, Download/Delete buttons enabled normally. Confirmed
directly from two screenshots of the running device, not inferred: the persisted-region text is the
completed download surviving a genuinely fresh process, distinct from the separate (and expected)
"No location picked yet" line, which reflects the region-picker's *new-pick* selection state, not the
persisted download — Download Maps stays greyed out there until a fresh long-press, unrelated to
whether a previous download persisted.

This closes out the last open item from this doc's update chain: `OfflineManager` region create →
download → persist across restart → replay all now hardware-confirmed against this project's real
Worker-hosted style and real PMTiles geometry (not PR #23's placeholder styles). The one item still
flagged as unverified is below.

**Still not hardware-verified**: offline replay with the radio off (airplane mode) — PR #23 confirmed
this for its own placeholder styles, but this project's real style/region combination hasn't been
taken through it yet.

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

## Open decisions — resolved (recorded here, since the sections above never named the answers)

Per `CLAUDE.md`: record why a decision was made and what alternative was rejected, not just that a
decision happened. These three were the "surface these, don't pick silently" list this doc originally
shipped with; the 2026-08-19 update section says the owner was asked and steps 3-4 went ahead, but
never wrote down the actual answers next to the questions. Answers below are inferred from what
the rest of this doc already states was built, not new guesses:

- **Region picker data source: switched away from USGS-only.** `OsmdroidOfflineMapRepository` (USGS
  Topo/Imagery) was deleted, not kept alongside the new path — `MapLibreOfflineMapRepository` is the
  sole `OfflineMapRepository` implementation in `AppContainer` now. The offline region picker covers
  whatever the continental-US PMTiles archive covers (OSM-derived vector, not USGS raster).
- **PR #23 stayed separate.** This doc's production wiring landed as its own PR (#25, branch
  `claude/offline-maps-integration-21uez7`), not folded into #23. As of this writing #23, #24, and #25
  are all still open, unmerged, separate PRs — confirm this hasn't changed before assuming it.
- **Style JSON sourcing: Protomaps' own published `@protomaps/basemaps` npm package**, not a
  hand-built layer list or a simpler custom style — `layers(source, LIGHT, options)` generated both
  the labeled (71-layer, not yet bundled) and glyph-stripped (57-layer, bundled then later moved to
  the Worker's `/style/offline.json` route) variants documented above.

## Verify-this-yourself callouts

Facts here may have shifted between this doc being written and being picked up:

- The Worker URL, bucket contents, and account ID above — confirm the Worker still responds before
  building against it (`curl -I https://forager-pmtiles.brandonlee1-894.workers.dev/us/10/200/380.mvt`
  should be `200`, `Content-Type: application/x-protobuf`).
- Whether PR #23 has been merged, closed, or changed since — check its current state rather than
  trusting this doc's description of it.
- Whether PR [#24](https://github.com/slayer8366/Forager/pull/24) (`claude/pmtiles-cloudflare-worker`)
  has been merged since — check its current state rather than trusting this doc's description of it.

## Handoff, 2026-08-20: where this stands and what's next

**Update, same day, later: #24 and #25 merged to `main`.** The owner explicitly asked for PR #25 to
be merged. Merged via merge commit `01c7710` (merge_method `merge`, not squash, so the individual
commits stayed intact). PR #24 was included in #25's diff (the branch was built directly on top of
it, per the resolved decision recorded above) and GitHub auto-detected its commits as reachable from
`main` and flipped it to `merged` too — no separate close/merge action was needed for #24.

One non-required check on #25 showed red at merge time: "Workers Builds: forager-pmtiles" (Cloudflare's
Git-integration build for `server/pmtiles-worker`) reported `failure`, while `mergeable_state` was
`unstable` (not `blocked` — nothing required was failing). Before merging, this was checked against
the same server code building `success` on PR #24's own branch just hours earlier at the same commit
content, and the PR's other real check ("Build, test, publish APK") was green. Read as Cloudflare's
build system reacting to a non-production branch name rather than an actual defect introduced by this
PR — not independently root-caused beyond that comparison, so revisit if this Worker's deploys look
wrong going forward.

**PR #23 is still open, unmerged, on its own branch** (`claude/phase1b-offline-packages`) — it was
deliberately kept separate (recorded above) and nothing in the #25 merge touched it. Its confirmed
glyph-crash *finding* is now acted on in the merged production code; the PR itself, and its
`MapLibreBasemapPreviewActivity` scaffolding, still need an explicit decision (item 3 below).

**Done and hardware-confirmed this round** (now on `main` via PR #25):
region create → download → persist across a genuinely cold restart → `getStatus()` reads the
persisted region back correctly, all against the real Worker-hosted glyph-stripped style and real
continental-US PMTiles geometry, not placeholder styles. Commit `1d51858` (on the now-merged branch)
records this in this doc and as a PR #25 comment.

**Still open, roughly in priority order for whoever picks this up next:**

1. **Airplane-mode offline replay** — the one item this doc has flagged unverified since the
   2026-08-19 hardware-confirmed update and still hasn't been closed. PR #23 proved this mechanism
   works for its own placeholder styles; this project's real style/region combination has not been
   taken through it. Needs a physical device with radio control, same as every other hardware check
   in this doc's history — don't try to fake this in the sandbox.
2. **Steps 1-2 (live, labeled vector basemap)** — still deliberately deferred, not just unstarted.
   `Basemap`/`BasemapTileSources` still needs to become a vector-style catalogue reachable from
   `MainActivity`/`MapSlot`/`AvailabilityScreen`; the 71-layer labeled style already exists
   (generated, not bundled — see the 2026-08-19 update above) but nothing consumes it yet. Re-run the
   `git log --oneline -- app/src/main/java/com/forager/app/ui/map/Basemap.kt
   app/src/main/java/com/forager/app/ui/map/SightingsMap.kt` check this doc names above before
   assuming that's still true. This is a live-rendering engine swap on the app's primary map screen —
   treat it with the same "don't ship blind" caution steps 3-4 were given, and re-confirm the dashed
   connector/overlay colours against the new style once it's live, per `maplibre-migration.md` §2b.
3. **Decide whether PR #23's `MapLibreBasemapPreviewActivity` scaffolding gets deleted now.** This doc
   said it's reference-only, delete once step 4 is done — step 4 is done (`MapLibreOfflineMapRepository`
   is the real implementation), but the scaffolding activity itself hasn't been checked for whether it
   still exists post-#25, or is now dead weight sitting in PR #23 waiting to be cleaned up as part of
   that PR's own merge.
4. ~~**Merge sequencing**~~ — done: #24 and #25 are both merged to `main` (see the update above). What's
   left on this front is narrower now: decide what happens to PR #23 (item 3 above) — merge its
   scaffolding-removal as its own small cleanup PR, or close it now that its finding is applied and its
   mechanism superseded by the real `MapLibreOfflineMapRepository` on `main`.

Nothing else in this doc's task list (the "What this doc scopes" §1-4 above) needs re-litigating —
steps 3-4 are done, verified twice over (initial hardware pass, then the restart-persistence re-check),
and now on `main`; only step 1-2 and the airplane-mode check remain unbuilt/unverified.
