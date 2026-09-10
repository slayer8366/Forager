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
  own GitHub App (Settings → Build), with **root directory** `server/pmtiles-worker` and
  **production branch** `claude/pmtiles-cloudflare-worker` — Cloudflare's own build infrastructure
  runs `npx wrangler deploy` on every push to that branch, so none of the Termux-specific problems
  above apply.
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

## What's already done

- `wrangler.toml` is filled in with the real account ID and R2 bucket name (`forager-maps`).
- `npm install`, `npm run typecheck` (`tsc --noEmit`), and `npm run build`
  (`wrangler deploy --dry-run`) all pass.
- The Worker is deployed live via Cloudflare Workers Builds, connected to this repo
  (root directory `server/pmtiles-worker`, branch `claude/pmtiles-cloudflare-worker`) — redeploys
  automatically on every push.
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
