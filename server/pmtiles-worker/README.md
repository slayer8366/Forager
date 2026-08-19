# Forager PMTiles Worker

Serves ordinary `z/x/y` tile requests out of a PMTiles archive stored in Cloudflare R2 — the piece
`maplibre-migration.md` §3 calls Option B/C's "own tile endpoint," which
`OfflineTilePyramidRegionDefinition` needs because MapLibre Android's `OfflineManager` downloads a
tile-server-shaped URL, not a `pmtiles://` source directly.

Adapted from Protomaps' own reference Worker
(`protomaps/PMTiles` repo, `serverless/cloudflare/`, MIT licensed) — this project only needed the
Cloudflare target, so their multi-backend layout is flattened into `src/index.ts` + `src/shared.ts`
here instead of reproducing their monorepo structure.

**Status: deployed via Cloudflare Workers Builds (Git-connected), no PMTiles archive uploaded yet.**
`wrangler login`/`wrangler deploy` from a real terminal turned out to have its own dead end on
Android: Termux's Node reports `process.platform` as `"android"`, and `workerd` (the Workers runtime
`wrangler` depends on) hard-rejects any platform outside `linux`/`darwin`/`win32` — not an ABI issue
rebuilding from source fixes, since Cloudflare doesn't ship an Android build of `workerd` at all.
Pasting the bundled JS into the dashboard's Quick Edit also didn't work (Monaco's mobile clipboard
handling). What actually worked: connecting this Worker to `slayer8366/Forager` via Cloudflare's own
GitHub App (Settings → Build), with **root directory** `server/pmtiles-worker` and **production
branch** `claude/pmtiles-cloudflare-worker` — Cloudflare's own build infrastructure runs
`npx wrangler deploy` on every push to that branch, sidestepping the Termux/`workerd` problem
entirely since none of it runs on-device.

## What's already done

- `wrangler.toml` is filled in with the real account ID and R2 bucket name
  (`forager-maps`) already created on Cloudflare.
- `npm install` succeeds, `npm run typecheck` (`tsc --noEmit`) is clean, and
  `npm run build` (`wrangler deploy --dry-run`) successfully bundles the Worker (34.80 KiB) and
  confirms the R2 binding resolves — all verified in this session. None of that required
  authenticating against the live account; a dry-run only validates config and bundles code.

## What's left — three steps, all needing a real terminal (not this session)

### 1. Get a PMTiles archive into the `forager-maps` bucket

Protomaps publishes a full-planet OpenStreetMap build daily at `build.protomaps.com`, filename
`YYYYMMDD.pmtiles` — check [maps.protomaps.com/builds](https://maps.protomaps.com/builds) for
today's actual filename before running this, since it changes every day and going stale silently
would just 404.

Install `go-pmtiles` (a single Go binary, from
[protomaps/go-pmtiles releases](https://github.com/protomaps/go-pmtiles/releases) — pick the build
for your machine's OS/arch), then extract a US-only slice without downloading the whole planet:

```sh
pmtiles extract https://build.protomaps.com/<TODAYS-DATE>.pmtiles us.pmtiles \
  --bbox=-124.85,24.40,-66.87,49.60 \
  --maxzoom=14
```

That bbox is the continental US's rough bounding rectangle — it'll pull in a little of Canada/Mexico
at the edges, which is harmless. A real US+Mexico extract at full zoom has been reported around
17 GB; capping `--maxzoom` at 14 (this project's field-use zoom ceiling — see
`maplibre-migration.md` §1, "Zoom past 15") should land meaningfully smaller than that, though the
exact size depends on the build. Confirm the resulting file size before uploading — R2's free tier
is 10 GB-month of storage, so if it lands north of that, storage costs a fraction of a cent per
GB-month beyond it, not a wall.

Upload it to the bucket. R2 is S3-compatible, so `rclone` (configured with an R2 remote) is the
standard tool for a multi-GB upload — `wrangler r2 object put` works too but is better suited to
small files. Either way, the object key must match what `wrangler.toml`'s `PMTILES_PATH` expects:
since that var isn't set here, the Worker defaults to `{name}.pmtiles` (see `pmtilesPath` in
`src/shared.ts`), so upload it to the bucket as `us.pmtiles` to match the `/us/{z}/{x}/{y}.mvt`
tile URLs this Worker will serve.

### 2. Deploy the Worker

Already handled — Cloudflare Workers Builds redeploys automatically on every push to
`claude/pmtiles-cloudflare-worker`. Nothing to run manually. The live URL is
`https://forager-pmtiles.brandonlee1-894.workers.dev` — that's the base URL the Android side's
`OfflineTilePyramidRegionDefinition` style JSON will need.

(The `npx wrangler login && npm run deploy` sequence below still works fine from any real terminal —
Mac/Linux/Windows, not Termux — if the Git-connected build ever needs bypassing.)

```sh
cd server/pmtiles-worker
npm install
npx wrangler login
npm run deploy
```

### 3. Verify it actually serves a tile

```sh
curl -I "https://<worker-subdomain>.workers.dev/us/10/200/380.mvt"
```

A `200` with `Content-Type: application/x-protobuf` means it's working; `404` likely means the
uploaded object's key doesn't match `us.pmtiles`, and `500` means something in the Worker itself —
check `npx wrangler tail` while re-requesting for the real error.

## Not built yet

The Android-side style JSON pointing `OfflineTilePyramidRegionDefinition` at this Worker's tile
URLs, and the actual `OfflineManager` wiring into the real app (`Basemap`, `SightingsMap`,
`AvailabilityScreen`) — this Worker being live is a prerequisite for that, not a replacement for it.
