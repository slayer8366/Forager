# Forager PMTiles Worker

Serves ordinary `z/x/y` tile requests out of a PMTiles archive stored in Cloudflare R2 — the piece
`maplibre-migration.md` §3 calls Option B/C's "own tile endpoint," which
`OfflineTilePyramidRegionDefinition` needs because MapLibre Android's `OfflineManager` downloads a
tile-server-shaped URL, not a `pmtiles://` source directly.

Adapted from Protomaps' own reference Worker
(`protomaps/PMTiles` repo, `serverless/cloudflare/`, MIT licensed) — this project only needed the
Cloudflare target, so their multi-backend layout is flattened into `src/index.ts` + `src/shared.ts`
here instead of reproducing their monorepo structure.

**Status: fully live and verified.** `https://forager-pmtiles.brandonlee1-894.workers.dev/us/{z}/{x}/{y}.mvt`
serves real vector tiles (confirmed: a `.mvt` response decoded to genuine `earth`/`landuse`/`roads`/
`water` layer data — US 191, Big Sandy River) out of a continental-US PMTiles archive in the
`forager-maps` R2 bucket, deployed via a Worker connected to this repo. The whole path — extract,
upload, deploy, serve — was built and debugged entirely from an Android phone (Termux + the
Cloudflare dashboard), no desktop/laptop involved, hitting real problems along the way worth
recording rather than hiding:

- **`wrangler login`/`wrangler deploy` don't work on Termux at all.** Termux's Node reports
  `process.platform` as `"android"`, and `workerd` (the Workers runtime `wrangler` depends on)
  hard-rejects any platform outside `linux`/`darwin`/`win32` — not an ABI issue rebuilding from
  source fixes, since Cloudflare doesn't ship an Android build of `workerd` at all.
- **Pasting the bundled JS into the dashboard's Quick Edit also failed** (Monaco's mobile clipboard
  handling). What actually worked: connecting this Worker to `slayer8366/Forager` via Cloudflare's
  own GitHub App (Settings → Build), with **root directory** `server/pmtiles-worker`. Cloudflare's
  own build infrastructure runs `npx wrangler deploy` on every push to the production branch, so
  none of the Termux-specific problems above apply. The production branch was
  `claude/pmtiles-cloudflare-worker` at first; since 2026-10-24 it is **`main`**, with preview builds
  off (RECORD -1609), so the live Worker is what `main` holds.
- **`rclone copy <local-file> r2:bucket/key` doesn't reliably land at `key`.** An interrupted
  multi-threaded upload (switching networks mid-transfer) left an artifact that made a later
  `rclone copy` land the object at `key/key` (a nested path) instead of `key` — R2's dashboard
  bucket summary also showed a stale `Objects: 0` count with real storage used, which was a red
  herring for an incomplete-multipart-upload state, not the actual final bug. Fixed with
  `rclone moveto r2:bucket/key/key r2:bucket/key --s3-no-check-bucket` (`moveto`, unlike `move`,
  treats both sides as exact file paths rather than inferring directories).
- **Any `rclone` command against R2 that isn't a plain object PUT/GET may need `--s3-no-check-bucket`.**
  The R2 API token here is deliberately scoped to object-level read/write on `forager-maps` only
  (no bucket-admin permission), and rclone's default behavior probes bucket existence via
  `CreateBucket` before some operations (`copy`, `moveto`) — that probe 403s with a scoped token
  even though the actual operation would have succeeded. `--s3-no-check-bucket` skips the probe.

## Routes (dispatch 2026-10-18-05 Part 2)

| Path | What | From |
|---|---|---|
| `/{name}/{z}/{x}/{y}.mvt` | a vector tile | R2's `{name}.pmtiles`; z15 from Protomaps' live build through the overflow, cached into R2 |
| `/us/{z}/{x}/{y}.mvt`, z0-8, tile not in `us.pmtiles` | the world fill (dispatch 2026-10-22-01 Part A) | R2's `world-south.pmtiles`, labelled `X-Forager-Origin: r2-world-south`; 204 if it has no tile or is not on R2 |
| `/{name}.json` | TileJSON | R2's `{name}.pmtiles` header and metadata |
| `/us.json` while `world-south.pmtiles` is on R2 | TileJSON with its south bound taken from the world archive (dispatch 2026-10-22-01 Amendment 3), so MapLibre requests the tiles the fill serves | R2 `head` of `world-south.pmtiles`, then its header |
| `/style/offline.json` | the label-less offline style every pre-Street offline pack was downloaded against; never changed (RECORD -1294) | bundled |
| `/style/street-v1.json` | Forager's Street style as it was before Trail Map's character; frozen, for older apps and regions downloaded against it | bundled |
| `/style/street-v2.json` | Trail Map with its character (dispatch 2026-10-22-03); what the app loads until it switches to v3; frozen, for regions downloaded against it | bundled |
| `/style/street-v3.json` | v2 plus the official-trails band (step 8a, dispatch 2026-10-26-14; water and snow trails left out, dispatch 2026-10-26-22), built by `map-style/generate-street.mjs`; what the app will load once it switches (its own PR) | bundled; its band's tiles from R2's `reference-trails.pmtiles` through the generic tile path |
| `/reference-trails-manifest.json` | the official-trails archive's manifest: build date, sources' dates and licences, coverage (dispatch 2026-10-26-22), which `/reference-trails.json` cannot carry; a day's cache; an uncached 404 until uploaded | R2 `reference-trails-manifest.json` |
| `/fonts/{stack}/{range}.pbf` | Noto Sans glyphs (SIL OFL; `fonts/OFL.txt` beside them) | R2 `fonts/` |
| `/sprites/v4/light{,@2x}.{json,png}` | the icon set (MIT, from tangrams; `sprites/LICENSE-MIT.md` beside it) | R2 `sprites/v4/` |
| `/sprites/v5/light{,@2x}.{json,png}` | v4 unchanged plus Trail Map's own icons and patterns (`map-style/tools/build-sprite-v5.mjs`) | R2 `sprites/v5/` |

**Offline downloads south of 15.92°N** (the live `us.pmtiles` header's south bound) store only empty tiles. The live map there has world tiles to z8 and empty tiles from z9 up, z15 included: the overflow no longer runs south of the archive's own bounds, by the owner's "Stop it" (dispatch 2026-10-22-01 Amendment 4). Downloads ask for z10 to z15 only (`OfflineMapRepository.kt`, `MapLibreOfflineMapRepository.kt`), all above the world fill's z8, so nothing they store there has data (RECORD -1558; dispatch 2026-10-23-07). Listed in `docs/process/known-bugs.md`.

**The Pi is no longer tried first** (RECORD -879: the Pi "Builds the maps and keeps a copy, nothing live").
Every tile comes from R2. The response header `X-Forager-Origin` still names the source (`r2`).

**Tests:** `npm ci && npm run build && npm test` runs `test/worker.test.mjs` against `dist/index.js`, the
bundle the dashboard is given, with R2 faked from `test/fixtures/tiny.pmtiles`.

## What's already done

- `wrangler.toml` is filled in with the real account ID and R2 bucket name (`forager-maps`).
- `npm install`, `npm run typecheck` (`tsc --noEmit`), and `npm run build`
  (`wrangler deploy --dry-run`) all pass.
- The Worker is deployed live via Cloudflare Workers Builds, connected to this repo
  (root directory `server/pmtiles-worker`, production branch `main` since 2026-10-24, RECORD -1609) —
  redeploys automatically on every push to `main`.
- A continental-US PMTiles extract (`20260819.pmtiles` build, `--bbox=-124.85,24.40,-66.87,49.60
  --maxzoom=14`, 8.8 GB) is uploaded to `forager-maps` as `us.pmtiles`, and the Worker confirmed
  serving real tiles from it (`GET /us/{z}/{x}/{y}.mvt` returns genuine vector tile data).

## Reproducing this (extract + upload), if the archive ever needs replacing

Protomaps publishes a full-planet OpenStreetMap build daily at `build.protomaps.com`, filename
`YYYYMMDD.pmtiles` — check [maps.protomaps.com/builds](https://maps.protomaps.com/builds) for
today's actual filename first, since it changes daily and going stale silently would just 404.

```sh
pmtiles extract https://build.protomaps.com/<TODAYS-DATE>.pmtiles us.pmtiles \
  --bbox=-124.85,24.40,-66.87,49.60 \
  --maxzoom=14
```

`--maxzoom=14` matches this project's field-use zoom ceiling (`maplibre-migration.md` §1, "Zoom
past 15"). R2's free tier is 10 GB-month of storage; an 8.8 GB continental-US extract at that zoom
fits with room to spare.

Upload with `rclone` (an R2 remote configured via `rclone config`, provider `Cloudflare`, endpoint
`https://<account-id>.r2.cloudflarestorage.com`), **as a single command, not a directory copy**:

```sh
rclone copyto us.pmtiles r2:forager-maps/us.pmtiles --s3-no-check-bucket
```

Use `copyto` (or `moveto`), not `copy` — `copy` can land the file nested at `us.pmtiles/us.pmtiles`
instead of `us.pmtiles` if the destination already looks like a directory to rclone (see the status
section above for how this actually happened and was fixed). The object key must stay `us.pmtiles`
to match the `/us/{z}/{x}/{y}.mvt` tile URLs this Worker serves (`pmtilesPath` in `src/shared.ts`).

Verify:

```sh
curl -I "https://forager-pmtiles.brandonlee1-894.workers.dev/us/10/200/380.mvt"
```

A `200` with `Content-Type: application/x-protobuf` means it's working.

## Not built yet

The Android-side style JSON pointing `OfflineTilePyramidRegionDefinition` at this Worker's tile
URLs, and the actual `OfflineManager` wiring into the real app (`Basemap`, `SightingsMap`,
`AvailabilityScreen`) — this Worker being live is a prerequisite for that, not a replacement for it.
