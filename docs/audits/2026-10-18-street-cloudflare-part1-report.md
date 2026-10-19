# Street from the owner's Cloudflare, Part 1: a read-only report (dispatch 2026-10-18-05)

Hazel, for Rowan Waymark (planner) and the owner. RECORD -1298 holds the intent. Written 2026-10-18 between about 18:15Z and 18:45Z.

**The owner's go:** "Yes" (2026-10-18), given to the planner's "start a Hazel on Part 1 now?".

**What was done.** Reading only:
- the repository at pre-main `712ff277`, checked against the remote. It matches the dispatch.
- branches `basemap-launch-plan` (`2c3f4aee`), `records-after-173` (`fcdf0d6f`), `map-launch-fixes` (`db8afc2e`) and `label-check`, all read with `git show` and none touched;
- the owner's Cloudflare connector, read-only, at the coordinator's word: `workers_list`, `r2_buckets_list`, `workers_get_worker_code` and documentation search;
- public HTTPS requests to the Worker's own URLs: `/us.json`, `/style/offline.json`, and ten single tiles.

**What was not done.** Nothing on Cloudflare was created, changed, deployed, uploaded, deleted or purged. No Gradle, no emulator, no global installs. The account's email doesn't appear anywhere in this report.

**Stops.** Three of the dispatch's stop conditions fired. Each is reported and not worked around:
- **Stop 1, a premise that changes Part 2.** The Worker serves zoom 0 to 14 from the owner's Pi today, not from R2 (Q1.2, Q2).
- **Stop 2, coverage.** The archive covers the lower 48 states only. "The whole US" (-1274) can't be met with these tiles (Q1.1).
- **Stop 3, zoom levels.** Several outdoor points of interest exist in the tiles only at zoom 15. Paths arrive a zoom later than designed. Six of the outdoor places have no icon (Q1.3, Q1.4).

One read was refused: `workers_get_worker` (routes, plan, settings) was denied by this session's auto-mode permission check ("Production Reads"). It was not retried. Its gaps are listed under "Could not determine".

---

## Q1. The tiles

### 1.1 What R2's `us.pmtiles` holds (observed)

`curl https://forager-pmtiles.brandonlee1-894.workers.dev/us.json` returned 200 with these headers: `x-forager-origin: r2`, `cf-cache-status: HIT`, `age: 22739`, `last-modified: Sun, 18 Oct 2026 12:01:58 GMT`. TileJSON is always answered by the R2 path (`index.ts:391-407`), so this is R2's archive header and metadata:

| Field | Value |
|---|---|
| `name`, `version` | "Protomaps Basemap", `4.15.2` |
| `bounds` | `[-124.85, 24.4, -66.87, 49.6]`: **the lower 48 states only**, with no Alaska, Hawaii or Puerto Rico. This matches the extract command in `server/pmtiles-worker/README.md:52-53`. |
| `minzoom` / `maxzoom` | `0` / `15`. The 15 is not the archive's own figure: the Worker advertises `max(declared, OVERFLOW_MAX_ZOOM = 15)` (`index.ts:405-406`). The README says the extract was built to zoom 14 (`README.md:52-53`). |
| Tile type | MVT (`content-type: application/x-protobuf` on every tile) |
| Vector layers | `boundaries`, `buildings` (z11-15), `earth`, `landcover` (**z0-7 only**), `landuse` (z2-15), `places`, `pois` (z5-15), `roads` (z3-15), `water` |

`roads` fields include `kind`, `kind_detail`, `access`, `service` and `min_zoom`. There is **no** `sac_scale`, `trail_visibility`, `informal`, `tracktype`, `surface` or `foot`. `landcover` carries only `kind`, and nothing in the metadata carries `leaf_type`.

**The R2 archive's own header couldn't be read.** The Worker has no route that hands out raw archive bytes (`index.ts:332-345`, `shared.ts:13-16`), so the dispatch's "PMTiles header through small range reads" isn't available from outside. Only the TileJSON above is. The archive's own maxzoom of 14 is therefore read from the README, not observed.

### 1.2 Who serves the tiles, and the z15 overflow (observed): **Stop 1**

These are a handful of single tiles over a national-park visitor area in Washington (z7 to z16) and a national-forest area in the same state (z13 to z15):

```
z7  200 147953B  x-forager-origin: pi
z10 200  23604B  x-forager-origin: pi
z12 200  18773B  x-forager-origin: pi
z13 200  21828B  x-forager-origin: pi
z14 200  13618B  x-forager-origin: pi
z15 200  12219B  x-forager-origin: r2   (0.998 s)
z16 404      0B  x-forager-origin: r2
forest z13 200 3240B pi · z14 200 1982B pi · z15 200 1120B r2 (0.745 s)
```

- **Zoom 0 to 14 is served by the owner's Pi today.** The deployed code tries the Pi first (`fetchFromPi`, `index.ts:277-326`, called at `:382-389`). The Pi answers, so the Access token secrets are set and the tunnel is up.
- The planner's own picture was that "Today everything comes from R2" (plan section 2). -879 rules "Cloudflare serves; the Pi builds and keeps a copy, not a live fallback", with the owner's words "Builds the maps and keeps a copy, nothing live".
- **What this means for Part 2:** pointing the on-screen Street at this Worker as it stands would send every user's map, at every zoom up to 14, through the Pi on the owner's home link first. A slow Pi costs up to 4 s per tile before R2 answers (`index.ts:271`).
- **Which archive the Pi serves is unknown.** It may still be the copy identical to R2's (`docs/plans/2026-10-04-pi-origin-part1-report.md`), or something the Pi planner is building now. So the tile samples below at z ≤ 14 describe what the Pi serves, which is presumed but not shown to equal R2.
- **The z15 overflow is working (observed):** z15 tiles come back 200 through the R2 path, and z16 gets 404 as designed (`index.ts:410-415`). From outside, a fresh read from Protomaps' live build can't be told apart from a copy already stored in R2 under `overflow/` (`index.ts:219-225`). The forest z15 tile is unlikely to have been asked for before, which suggests a live read (inferred).

### 1.3 Does the schema carry what the design needs? (observed, from decoding the tiles above)

The tiles were decoded with a 60-line Python protobuf reader written for this report. It was kept outside the repository and has been deleted.

**Roads and paths:**

| Need | In the tiles | Zoom where it first appears in the tiles |
|---|---|---|
| Footpath | `kind=path, kind_detail=footway` | z13 (2 of the 13 seen at z14; `min_zoom` 14) |
| Path | `kind=path, kind_detail=path` | z13 (`min_zoom` 14) |
| Steps | `kind=path, kind_detail=steps` | z13 |
| Vehicle track | `kind=path, kind_detail=track` | z13 (`min_zoom` 13) |
| Informal path | **nothing**: no `informal`, `trail_visibility` or `sac_scale` field | — |
| No paths at all | z12 tiles carry none (9 roads, all major or minor) | — |

**Land:**

| Need | In the tiles |
|---|---|
| Woodland | `landuse` `wood` and `forest` at every sampled zoom (z7: 385 forest, 277 wood); `landcover` `forest` only from z0 to 7 |
| Grass | `landuse` `grassland` |
| Scrub | `landuse` `scrub` |
| Parks | `landuse` `nature_reserve` and `park`; at z7 also `pois` `protected_area` and `forest` |

**Points of interest**, with the tile zoom they first appear at and their `min_zoom` field:

| Need | Kind | First in tile | `min_zoom` |
|---|---|---|---|
| Trailhead | `trailhead` | **z15 only** | 16 |
| Parking | `parking` | z13 | 14-15 |
| Campground | `camp_site` | z12 | — |
| Picnic site | `picnic_site` | z12 | 13 |
| Viewpoint | `viewpoint` | **z15 only** | 16 |
| Peak | `peak` | z13 | 14 |
| Toilets | `toilets` | **z15 only** | 17 |
| Drinking water | `drinking_water` | **z15 only** | 17 |
| Ranger station | `ranger_station` | **z15 only** | 16 |

So every kind the design names **exists** in the tiles. Five of them exist only in z15 tiles, and z15 tiles come from the live overflow, not the R2 archive (Q1.2).

**Icons (observed):** `~/from-flash/map-style/assets/sprites/v4/light.json` (basemaps-assets `028c18f7`, the pinned set) has 53 icons. There are icons for `drinking_water`, `toilets`, `peak` and `bench`, but **none for `trailhead`, `parking`, `camp_site`, `picnic_site`, `viewpoint` or `ranger_station`**. None of the 53 is SDF (`sdf` count 0), so icons can't be recoloured by paint.

### 1.4 Design decisions the tiles can't support as ruled, with the closest supported form (inferred from 1.3): **Stops 2 and 3**

| Ruling | What fails | Closest supported form |
|---|---|---|
| Coverage, the whole US (-1274) | The archive's bounds stop at the lower 48 | The lower 48 now. Alaska and Hawaii wait for an archive that includes them (the Pi planner's build). -1282's "no switching while the app is in use" rules out a fallback to OpenStreetMap there, so Street would be blank in those states. |
| Paths from trailhead zoom (-1284) | No paths in z12 tiles; a few at z13; all at z14 | Paths from z13, filling in at z14 |
| Informal paths dotted (-1284) | No field marks a path informal | Drawn as ordinary paths until Track B's build carries `informal` / `trail_visibility` |
| Vehicle tracks dash-dot (-1284) | Supported (`kind_detail=track`) | — |
| Forest, one solid green from the state view (-1285) | Supported: `landcover` forest at z0-7, then `landuse` wood/forest from z2. The fade is a style choice (`forager-light.json` opacity ramps, Elder) | — |
| Grass, scrub, park outline (-1285) | Supported | — |
| Labels (-1286) | Supported (style only) | — |
| Outdoor POIs from mid zoom (-1287) | Campground, picnic site, parking and peak can show from z12 or 13. **Trailhead, viewpoint, ranger station, toilets and drinking water exist only in z15 tiles.** | Those five from z15 (a walking view, a few streets across). Six kinds need icons drawn, or a plain dot with the name for now. |
| Night, pure inversion with labels (-1288) | Text and halo can be recoloured. The icons are fixed-colour images. | Text and halo through the recolour; icons inverted by the app after the style loads (Q8) |
| Zoom 18 (-1289) | Supported: overzoom of z15 | — |

---

## Q2. The deployed Worker

**Last modified (observed):** `workers_list` gave `forager-pmtiles`, `modified_on 2026-10-15T17:04:41.756856Z`, created 2026-08-19. The account has one Worker, and `r2_buckets_list` shows one bucket, `forager-maps` (created 2026-08-19).

**The code matches pre-main (read).** `workers_get_worker_code` returned a 3,423-line bundle with sections `src/shared.ts` (bundle lines 880-902), `src/offline-style.json` (903-3130) and `src/index.ts` (3131-3422). Read side by side:
- `shared.ts` matches pre-main's `src/shared.ts` line for line.
- `index.ts` matches pre-main's `src/index.ts:25-462` statement for statement, including `fetchFromPi`, `PI_ORIGIN_TIMEOUT_MS = 4e3`, `X-Forager-Origin`, the overflow path and `OVERFLOW_MAX_ZOOM = 15`. The only differences are bundler renames and stripped comments.
- The served `/style/offline.json` parses equal to pre-main's `src/offline-style.json`: Python `==` on the parsed JSON gives `True`, with 57 layers, no `glyphs` and no `sprite`. `server/pmtiles-worker/` is identical on main, pre-main and dev (`git diff --stat` empty; last change `b6409829`, 2026-10-04).

A copy of the deployed bundle is saved for rollback, outside the repository:
- `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/deployed-index.js`, sha256 `d92bafa1…55ad`;
- the raw multipart form beside it, sha256 `9a11fd58…abcd`.

**Routes it serves (read from the code):**
- `/style/offline.json`;
- `/{name}.json`, the TileJSON of the archive `{name}.pmtiles`;
- `/{name}/{z}/{x}/{y}.{ext}`, a tile;
- `POST` gets 405, and anything else gets 404 `Invalid URL` (`index.ts:330-344`).

**Seen in use (observed):** only the `workers.dev` host. Whether custom domains or zone routes are attached couldn't be read (`workers_get_worker` refused).

**How it deploys (inferred):** the deploy path is Workers Builds, Cloudflare's own Git integration, which redeploys on every push to its production branch (`README.md:25-30, 49-51`).
- The branch the README names, `claude/pmtiles-cloudflare-worker`, no longer exists on the remote (`git ls-remote`: none).
- The 2026-10-15T17:04:41Z modification comes 4m42s after PR #227 merged into main at 16:59:59Z (`cb25b582`). That fits a redeploy on a push to main. dev was also pushed at 17:00:34Z (`28dd81e9`), so main isn't certain, but the 2026-09-09 finding that the check is "red on every PR head and green on main" points the same way.
- **So the production branch is very likely `main`.** That matters for any deploy made outside Git (Q3).

---

## Q3. Deploying from the laptop, and the owner's dashboard route

**What's on the laptop (observed):**
- No `node`, `npx`, `wrangler` or `pmtiles` on `PATH` (`which`: none).
- There is a Node 24 at `~/from-flash/map-style/node/node-v24.21.0-linux-x64/bin/`, not on `PATH`, with `map-style`'s `node_modules` and the fetched fonts and sprites beside it.
- No global install is needed. `server/pmtiles-worker` would need a local `npm install`, which pulls wrangler and is probably around 100 to 200 MB (not measured).

**The connector can't deploy (read from this session's tool list).** The Cloudflare connector's tools are:
- `workers_list`, `workers_get_worker`, `workers_get_worker_code`;
- `r2_buckets_list`, `r2_bucket_get`, `r2_bucket_create`, `r2_bucket_delete`;
- KV namespace, D1 and Hyperdrive tools;
- documentation search, and a Pages migration guide.

There is no tool to upload or deploy Worker code, put an R2 object, set a secret or route, or change cache settings. Part 2's deploy and uploads need something else: the dashboard, wrangler with a login or token, or Workers Builds from Git.

**Three ways the Worker change can go live:**
1. **Workers Builds from Git (no laptop tooling).** The change goes live when it reaches the production branch, very likely main. Under the promotion gate (-1086), that means the Worker change goes to main with, or ahead of, the release.
2. **Wrangler from the laptop.** Needs the Node above, a local `npm install`, and either `wrangler login` (a browser sign-in by the owner on the laptop) or an API token the owner creates (Account › Workers Scripts › Edit), exported in the shell for one session and never written down. R2 objects: `wrangler r2 object put`, one object per command, files up to 315 MB (CF docs, "Upload objects").
3. **The owner in the dashboard** ("Give me the code and I'll deploy"). This is the route Part 2 is priced on; the steps are in Q9.

**The catch common to 2 and 3 (inferred from Q2):** anything deployed outside Git is overwritten by the next push to the Workers Builds production branch. If main is that branch, the next merge to main that lacks the new Worker code would put the old Worker back. On the old Worker, the new style URL, fonts and icons 404, so Street on a new build stops drawing. That's why the owner steps begin by pausing auto-deploy.

**What only the owner can do (owner steps; none done):**
- **O1.** Confirm the mapping account is on **Workers Paid**. On Free, Workers stop at 100,000 requests a day (CF limits page), which an on-screen map would reach quickly. Not verifiable here.
- **O2.** In Workers & Pages › `forager-pmtiles` › Settings › Builds: note the production branch, then pause or disconnect builds for the deploy window (Q9).
- **O3.** The dashboard deploy itself (Q9).
- **O4, only if the wrangler route is chosen:** `wrangler login` in a browser on the laptop, or create a scoped API token.

---

## Q4. Fonts and icons on the Worker

**Font stacks (read):** `map-style/forager-light.json`'s `text-font` values are `Noto Sans Regular` (6 layers), `Noto Sans Italic` (5) and `Noto Sans Medium` (3). On disk (observed, `~/from-flash/map-style/assets/fonts/`):
- 256 range files per stack, 768 in all;
- Regular 6.9 MB, Medium 4.4 MB, Italic 2.2 MB, about 13.5 MB together;
- sprites 188 KB.

The licence texts are in the repository: `map-style/licences/fonts-Noto-OFL.txt` and `sprites-tangrams-icons-MIT.md`.

**Where to serve them from, for the dashboard route (recommended): R2 under a prefix, read by the Worker.**
- The objects go under `fonts/Noto Sans Regular/0-255.pbf` and so on, and `sprites/v4/light.json`, `light.png`, `light@2x.json` and `light@2x.png`.
- `fonts/OFL.txt` and `sprites/LICENSE-MIT.md` go beside them.
- The Worker gains two small routes, `/fonts/{stack}/{range}.pbf` and `/sprites/{file}`, that read those keys from the existing `BUCKET` binding with a long `Cache-Control`.

Worker static assets would cost nothing per request (CF pricing), but they ship with a wrangler or Git deploy. Whether the dashboard editor can attach them to an existing Worker isn't confirmed (Could not determine), so they suit routes 1 and 2, not the owner's route 3.

**The OFL text:** `fonts/OFL.txt` in R2 beside the glyphs, as OFL asks (Juniper Q6). In the app, `app/src/main/assets/credits/software.json` already has `OFL-1.1` (`:25-27`) and a Noto entry for the app's typeface (`:359-362`). Part 2 adds:
- a line saying Noto Sans also draws the map's labels;
- the tangrams icons under `mit.txt`;
- and it drops or replaces the Open Sans entry (`:231-236`). That entry is pre-main's; map-launch-fixes may already change it.

**Juniper's cache-rule point (read from CF docs, "Workers › Cache"):** "A Worker is a zoneless entity… No zone configuration for caching applies to Workers Caching. Cache Rules, … the zone's default cached-file-extensions list… have no effect on a Worker's cache." Juniper's point applies to an R2 bucket on a custom domain, not to this Worker on `workers.dev`.
- **There is no cache rule to add.** Caching is set in the Worker's code. It already puts tiles and TileJSON in `caches.default` with `public, max-age=86400` (`index.ts:360-378`), and `cf-cache-status: HIT` on `/us.json` shows that cache working on `workers.dev` (observed).
- The new font, icon and style routes should do the same, with a longer max-age for fonts and icons.
- Caveat (CF docs, Cache API): that cache is per data centre, not tiered.

**`BasemapStyleTest` on map-launch-fixes (read, `db8afc2e`):**
- `no style the app builds asks MapLibre's demo server for anything` (`BasemapStyleTest.kt:44-51`) can stay as it is.
- `no style declares a glyphs URL, day or night` (`:119-131`) is the one Part 2 changes. Its rule becomes: a raster style (Topographical) declares no `glyphs`, and the vector Street style's `glyphs` and `sprite` start with `https://forager-pmtiles.brandonlee1-894.workers.dev/` and name no other host.
- The vector style is hosted, not built in the app, so the test reads a copy of the generated style checked into the repository. Part 2 adds a test that the hosted copy and the repository copy are the same file.

---

## Q5. The style

**How `forager-light.json` is built (read):**
- `map-style/generate.mjs:4,43` calls `@protomaps/basemaps` 5.7.2 `layers("protomaps", flavor, {lang: "en"})` on the `light` flavour.
- `overrides/forager-colours.json` can do two things only: change named colours (`generate.mjs:17-27`) and hide layers by id (`:44-50`). Today it's empty (`forager-colours.json:1-5`).
- The source is the Worker's `/us.json` (`:10, 56-62`).
- Glyphs and sprite point at `http://localhost:8765` (`:63-64`), with a Maputnik copy pointing at Protomaps' GitHub host (`:72-78`).
- `tools/check-style.mjs` checks the layer count, every font stack and glyph range, every icon name, and the licence files against disk (`check-style.mjs:1-3`).
- The result has 71 layers: 41 line, 15 fill, 14 symbol, 1 background (counted).

**Overrides or layer edits, decision by decision (inferred from what the override file can express):**

| Decision | Overrides alone? |
|---|---|
| Colours: forest green, grass and scrub tones, near-black text, roads that survive inversion | **Yes, mostly**: these are named colours |
| Forest solid from the state view (opacity ramps on `landcover`, `landuse_park`) | **No**: layer edit |
| Paths: dashes by `kind_detail`, from z13, width | **No**: `roads_other` is one layer with one filter (Elder); it needs splitting into three layers, with `line-dasharray` and `minzoom` |
| Parks as an outline | **No**: a new line layer |
| Label sizes and halos | **No**: `text-size` and `text-halo-width` are layout and paint edits |
| POI list and zooms | **No**: the `pois` filter list and zoom expression (`forager-light.json:7790`, Elder) |
| Zoom 18 | App side (`maxZoom`), not the style |

So `generate.mjs` needs one new step: a patch file of layer edits applied after `layers()`, by layer id, failing loudly on an id that doesn't exist, the same way `hide` already does. That's a new step, not a conditional threaded into the old ones (CLAUDE.md).

**Where the built style lives:** on the Worker, beside `/style/offline.json`, as `/style/street-v1.json`, bundled into the Worker the way `offline-style.json` is (`index.ts:23, 116-123`). That keeps it out of R2 and needs no upload. The `v1` matters because the offline store keys packs by the style URL (`OfflineStyle.kt:3-10`). A later design change ships as `street-v2.json`, so packs already downloaded keep working while their URL still serves. `/style/offline.json` stays byte-identical, as -1294 requires.

---

## Q6. The app

**Every place Street's source is set (read, pre-main `712ff277`):**
1. `ui/map/Basemap.kt:126-132`: `OSM_STANDARD` holds `tile.openstreetmap.org`, `maxZoom = 19` and "© OpenStreetMap contributors". The enum is raster-only: every entry is a `{z}/{x}/{y}` template (`:111`).
2. `ui/map/MapMode.kt:36`: `STREET` maps to `Basemap.OSM_STANDARD`. `:42`'s `DEFAULT = TOPOGRAPHIC` is changed to Street on map-launch-fixes (`db8afc2e`, Amendment 2, not merged).
3. `ui/map/BasemapStyles.kt:191-208` builds `singleLayerStyleJson`, the raster style. `:239-240`'s `mapStyleSourceFor` returns `Uri(OFFLINE_STYLE_URL)` when offline tiles are on, and `Json(...)` otherwise.
4. `map/OfflineStyle.kt:18`: `OFFLINE_STYLE_URL`, the download and offline render style. `:28`: `OFFLINE_STYLE_ATTRIBUTION`.
5. The offline repository: `map/MapLibreOfflineMapRepository.kt:114-120` downloads `OfflineTilePyramidRegionDefinition(OFFLINE_STYLE_URL, …, MIN_ZOOM, MAX_ZOOM = 15.0)`. Zoom limits are in `domain/OfflineMapRepository.kt:126, 161`.
6. Night:
   - online, the raster shader `NIGHT_RASTER_PAINT` (`BasemapStyles.kt:57-62`);
   - offline, `applyOfflineNightRecolour` (`SightingsMap.kt:2609-2648`), which walks only background, fill and line (`NightColour.kt:70-78`, `SightingsMap.kt:2651-2656`);
   - it's called at `SightingsMap.kt:887`, only when `useOfflineTiles && night`.
7. The Topo night crossfade: `BasemapStyles.kt:137-138` (`nightLowZoomBasemap`, Topo → `OSM_STANDARD`) and `:164-189` (raster Street under the topo raster). The premise matches.
8. The zoom limit: `SightingsMap.kt:874`, `setMaxZoomPreference(basemap.maxZoom)`.
9. The attribution: `BasemapStyles.kt:248-266`, `mapAttributionFor` and `mapCreditsFor`, drawn at `SightingsMap.kt:1471`.
10. Credits: `ui/credits/DataSourceCredits.kt:32-42` (OpenStreetMap, OpenTopoMap, and "Protomaps (offline maps)"), and `app/src/main/assets/credits/software.json:231-236, 359-362`.
11. map-launch-fixes (`db8afc2e`, PR #257 unmerged) removes `glyphs` and `GLYPHS_URL_TEMPLATE` from every style and adds `fullAttribution`. Part 2 must be built on top of it.

**The smallest Part 2 that switches Street to one vector style URL (proposal; a plan is the planner's call):**
- **(a)** A new constant beside `OFFLINE_STYLE_URL`, `STREET_STYLE_URL = ".../style/street-v1.json"`.
- **(b)** `mapStyleSourceFor` returns `Uri(STREET_STYLE_URL)` for Street online. With offline tiles on, it returns `Uri(STREET_STYLE_URL)` for new regions too, since one URL means the store serves it.
- **(c)** New downloads use `STREET_STYLE_URL`. The 2026-10-04 label check downloaded and drew a labelled style offline on the S22 with MapLibre 13.5.0, with no PR #23 crash (`label-check:docs/audits/2026-10-04-label-check-report.md`, "Results" 2 and 3).
- **(d)** Night becomes the recolour for the vector Street online and offline, extended to `text-color`, `text-halo-color` and the icons (Q8).
- **(e)** `Basemap` gets a vector Street entry with `maxZoom = 18` and the credit "Protomaps © OpenStreetMap".
- **(f)** Topo night below 9.5: load the vector Street by URL, add OpenTopoMap's raster above every vector layer, and give the symbol layers `maxzoom` 9.7, so labels can't show through above the crossfade (-1291).
- **(g)** Credits, and the `BasemapStyleTest` change from Q4.
- **(h)** `MapPaletteTest` ratchets get re-measured against the new ground (Elder, blocker 8). They may move, and that's reported, not silenced.

The `Basemap` doc comment's own warning (`Basemap.kt:26-34`) names this as the product decision it deferred. -1272 and -1276 have since made that decision.

**How the release ships either way, with no runtime switch (-1282):**
- One compile-time constant, `STREET_SERVED_BY_OWN_MAP`, in one Kotlin file, set before the release build. `false` keeps today's raster Street exactly. `true` routes Street to `STREET_STYLE_URL`. Nothing reads the network to decide it.
- A test pins each value to its style: `false` gives the OpenStreetMap template, `true` gives the Worker URL.
- That is a conditional in working code. CLAUDE.md prefers a new path, so the vector Street goes in as its own style builder, and the constant only chooses between the two. A Gradle `buildConfigField` would do the same; the constant is simpler and testable headless.

---

## Q7. Old offline regions

**What's stored per region today (read):**
- The Room row `OfflineRegionEntity` has `id`, `name`, `lat`, `lng`, `radiusKm`, `minZoom`, `maxZoom`, `createdAtEpochMillis` and `isEntryCapture` (`data/local/OfflineRegionEntity.kt:28-46`).
- The MapLibre metadata blob has name, region, zooms and `downloadedAt` (`map/MapLibreOfflineRegionMetadata.kt:21-42`).
- **Neither stores the style URL.** Nothing in `app/src/main` reads `.definition` or `styleURL` (`grep`: none).

**How old can be told from new without a migration (inferred, not checked on a device):**
- MapLibre keeps each region's definition, including the style URL it was downloaded against. The Android SDK exposes it as `OfflineRegion.getDefinition()`, and `OfflineTilePyramidRegionDefinition.getStyleURL()`.
- A region whose definition names `OFFLINE_STYLE_URL` is old, and the list shows the -1294 notice for it.
- These calls are from the SDK's public API as I know it. They were not checked against the pinned 13.5.0 artifact with `javap`, as `SightingsMap.kt:2597-2601` did for its own calls. Part 2's first step checks them.
- **If they don't hold**, the fallback is a new nullable Room column, `styleUrl`, filled on download and read by the list in the same change (CLAUDE.md: no column without a reader). That's a migration number, so it's checked against the base at the time.

**Rendering an old region after the switch:**
- The map loads styles by URL, and the store serves only the URL a pack was stored under (`OfflineStyle.kt:3-10`).
- So with offline tiles on, an old region draws only if the map loads `OFFLINE_STYLE_URL`, and a new one only under `STREET_STYLE_URL`.
- Part 2 chooses the offline style URL by the region in view, or keeps `OFFLINE_STYLE_URL` while any old region exists. That's a design question; owner question 5 asks it in behaviour terms.

---

## Q8. Night

**Can today's offline recolour cover the vector Street online too? Partly (read):**
- `offlineNightRecolourOf` is pure. It works on any colour property's value, literal or expression (`NightColour.kt:93-118`). Only the property list stops it at background, fill and line (`NIGHT_RECOLOURED_PROPERTIES`, `:70-78`), and so does the SDK adapter (`dayColourPropertiesOf`, `SightingsMap.kt:2651-2656`).
- **Text:** adding `SymbolLayer` with `text-color` and `text-halo-color` to both lists covers names and halos the same way. The SDK getters weren't checked with `javap` here.
- **Icons: no.** The sprite isn't SDF (Q1.3), so `icon-color` has no effect. For pure inversion, the app replaces each sprite image after the style loads with its V1-inverted copy, using `Style.getImage(name)` and `addImage`, applying `nightColorOf` per pixel (inferred; SDK calls not checked).
- **Online the same:** the vector Street online is also a style loaded by URL. The same recolour runs at `SightingsMap.kt:887` when `night` is on, whether or not offline tiles are on. "Online and offline the same" (Elder) then holds by construction.
- **Elder's warning holds (read, design review):** white roads (`#ffffff`) invert to `#000000` on a `#24211c` ground. The day colours have to be chosen for the inversion (-1288). That's style work in Q5.

---

## Q9. Part 2, priced, with the owner's dashboard route

### The steps, in order

| # | What it changes | Tests and checks | Needs the owner |
|---|---|---|---|
| 0 | Owner decisions 1 to 5 below (Pi, coverage, POI zooms, icons, old regions) | — | Yes, before anything is built |
| 1 | **Worker:** drop Pi-first, if decision 1 says so; add `/style/street-v1.json` (bundled), `/fonts/…` and `/sprites/…` (from R2); keep `/style/offline.json` byte-identical. Build one bundled `index.js` for pasting. | `tsc --noEmit`; `wrangler deploy --dry-run --outdir dist`, which needs a local `npm install`; a JSON-equality check that `offline.json` is unchanged; a route test with `wrangler dev` if the install allows. About 1.5 h. | No |
| 2 | **Style:** the layer-patch step in `generate.mjs`; the colours; paths split by `kind_detail` from z13; forest ramps; labels; the POI list (z12 and 13 for campground, picnic, parking, peak; z15 for the other five); glyphs and sprite on the Worker; `check-style` | `npm run check`; renders in the existing GL JS preview (Playwright browsers are present under `~/from-flash/map-style/pw-browsers`) at the three places and three zooms Elder's checks name, day and night | **Phone review rounds on the look**: realistically two or three, each about an hour of turnaround |
| 3 | **App** on top of map-launch-fixes: `STREET_STYLE_URL`, the release constant, Street by URL online and offline, the region style-URL read and the notice text (the owner approves the words), night extended to text and icons, the Topo-night composition, `maxZoom` 18, credits and attribution | `BasemapStyleTest` changed (Q4); new tests for the constant, the region old/new rule, the recolour lists, `TopoNightStreetSwitchTest` updated; `MapPaletteTest` re-measured and reported; CI only, with revert checks per CLAUDE.md. About 5 to 7 h of coding, plus CI rounds. | Notice text |
| 4 | **Owner deploys** (steps below) | The curl checks in step D7 | Yes |
| 5 | **S22 check:** Elder's checks 1 to 11, plus an old region drawing in aeroplane mode (-1294) and a new region downloaded and drawn offline | — | Yes, at the phone |

### The owner's dashboard steps (exactly as followed; for step 4)

Hazel hands over:
- `dist/index.js`, the one bundled file;
- a folder `r2-upload/` holding `fonts/` (three subfolders of 256 `.pbf` each, plus `OFL.txt`) and `sprites/` (four files, plus `LICENSE-MIT.md`).

The steps:
- **D0. Save the current Worker.** A copy is already saved (Q2). In the dashboard, also note the current deployment's version under Workers & Pages › `forager-pmtiles` › **Deployments**.
- **D1. Pause auto-deploy.** Workers & Pages › `forager-pmtiles` › **Settings** › **Builds**. Write down the production branch, then disconnect the repository, or turn off automatic production deploys if the page offers that. Otherwise the next push to that branch puts the old code back (Q3).
- **D2. Upload fonts and icons.** R2 object storage › `forager-maps` › **Upload**. Drag in the `fonts` and `sprites` folders, keeping the folder names exactly as given; the key must read `fonts/Noto Sans Regular/0-255.pbf`. **Don't touch `us.pmtiles` or `overflow/`.**
- **D3. Paste the Worker.** Workers & Pages › `forager-pmtiles` › **Edit code**. Select all in `index.js`, paste the new file, then **Deploy**. The bindings (`BUCKET`), variables and the two Access secrets are settings, not code, so they stay.
- **D4. Pi variable**, only if decision 1 is "Cloudflare only": nothing to do, because the new code no longer reads it. Removing `PI_ORIGIN_URL` and the secrets can wait.
- **D5.** Nothing to set for caching (Q4).
- **D6.** Leave builds paused until the same code is on the production branch, then reconnect.
- **D7. Check from a browser or phone:**
  - `…/style/offline.json` still loads;
  - `…/style/street-v1.json` loads;
  - `…/fonts/Noto%20Sans%20Regular/0-255.pbf` downloads;
  - `…/us/14/{x}/{y}.mvt` answers with `x-forager-origin: r2`.

**Rollback:** Workers & Pages › `forager-pmtiles` › Deployments › the 2026-10-15 version › **⋯** › **Rollback** (CF docs, "Rollbacks"). Or paste the saved `deployed-index.js` back in. The R2 uploads can stay; nothing reads them on the old code.

**What may not work from the dashboard (Could not determine, so flagged):**
- Uploading two nested folders with 768 files and spaces in their names by drag and drop is unconfirmed. If the dashboard flattens folders or caps file counts, the fallback is the wrangler route, with a one-line loop of `wrangler r2 object put`, or the owner's existing `rclone` (README).
- A ~0.5 MB paste with the labelled style bundled should be fine from a laptop browser. The README's failed paste (`:25-26`) was on a phone. The 64 MiB Worker limit (CF limits) is far off.

### Time and fit

- **To a phone-checkable build: about 10 to 14 hours of work** (inferred). That's 1.5 h for the Worker, 3 to 5 h for the style's first pass, and 5 to 7 h for the app, plus CI rounds. Then two or three phone review rounds on the look, the owner's 30 to 60 minutes in the dashboard, and the S22 check.
- Decisions 1 to 3 below also come before step 1.
- **It does not fit before tonight's release build.** Per -1282, the release ships Street on OpenStreetMap's raster tiles, and the Cloudflare Street follows in a later update. That's the outcome -1282 provides for, not a failure.
- The planner's prediction in -1298 ("Part 2 doesn't fit before tonight's release build") holds. Its other two predictions:
  - "Deploying needs an owner login" holds for routes 2 and 3; route 1 needs none.
  - "The tiles carry footway, path and track kinds and most of the outdoor POIs, but not an informal-path class" holds, with the zoom caveats in Q1.3.

---

## Confirmed vs inferred

**Observed:**
- the TileJSON: lower-48 bounds, version 4.15.2, layers and fields;
- the Pi serving z7 to 14 (`x-forager-origin: pi`); z15 through R2 and z16 404;
- the kinds and zooms in ten decoded tiles;
- the sprite's 53 non-SDF icons; the fonts on disk;
- the deployed bundle equal to pre-main's source; the served `offline.json` equal to the repository's;
- the cache HIT on `/us.json`; one Worker and one bucket on the account;
- no Node on `PATH`, and the Node at `~/from-flash`.

**Read:**
- every file and line cited;
- the CF docs on Worker caching, static-asset limits, R2 uploads and rollbacks;
- RECORD -879, -1272 to -1298.

**Inferred:**
- Workers Builds deploys from main;
- the archive's own maxzoom of 14 (from the README);
- the z15 tile read live;
- old and new regions told apart by `getStyleURL()`;
- icons inverted at runtime with `getImage`/`addImage`;
- every hour figure.

## Could not determine

- **The Worker's routes, custom domains, plan (Free or Paid) and Builds settings.** `workers_get_worker` was refused by this session's permission check, and the connector has no billing read.
- **Which archive the Pi serves**, and whether it equals R2's; the tile samples at z ≤ 14 came from the Pi.
- **The R2 archive's own header** (maxzoom, tile compression). The Worker doesn't serve raw bytes, and the connector has no object read.
- **Whether the dashboard can upload nested folders**, or attach static assets to an existing Worker.
- **How the five z15-only POI kinds behave at other places.** Two areas were sampled, not a distribution.
- **Whether a missing glyph range would fail an offline download.** It's relevant only if fonts were ever subset; they aren't in this proposal.

## Premises in the dispatch that were wrong or need correcting

1. **"Today everything comes from R2"** (plan section 2, carried into this dispatch's picture of the Worker) is wrong. z0 to 14 come from the Pi first (Q1.2). **Stop 1.** It changes Part 2: retiring Pi-first (-879, plan A1) becomes a required step before the Worker carries on-screen traffic.
2. **"Coverage: the whole US" with `us.pmtiles`** (-1274 with -1297 (a)) can't both hold. The archive is the lower 48 (Q1.1). **Stop 2.**
3. **"The PMTiles header through small range reads"** isn't possible through the Worker. Only TileJSON is exposed (Q1.1).
4. **"There is no wrangler… what would a deploy need"** misses that the Worker deploys from Git through Workers Builds (README). That also makes any dashboard or wrangler deploy temporary unless builds are paused (Q3).
5. **Juniper's cache rule** doesn't apply to a Worker on `workers.dev` (CF docs, Q4). There's nothing to set.
6. **Line references checked and correct:** `Basemap.kt:113-142`, `BasemapStyles.kt:137-138`, `OfflineStyle.kt:18`, `MapMode.kt:42`. The last is about to change on map-launch-fixes.
7. **"Last modified 2026-10-15T17:04Z"** is confirmed (`workers_list`).

## Decided beyond scope

Nothing. The Part 2 shape in Q6 and Q9 is a proposal for the planner, not a plan. No file outside this report and its index row was changed.

## Questions for the owner

1. **Should the Pi stay in the path of the maps people look at?**
   - **Today:** when someone downloads an offline area, most map pieces come from your Pi at home, with Cloudflare as backup. If Street moves to your Cloudflare, every user's everyday map would go through the Pi first, and a slow Pi delays each piece by up to 4 seconds.
   - **(a)** Cloudflare only; the Pi builds and keeps a copy, as you ruled on 11 October (-879). **Recommended.**
   - **(b)** Keep the Pi first.
2. **Alaska and Hawaii.** The map set in Cloudflare covers the lower 48 states only.
   - **(a)** Switch Street when the Pi planner's build includes Alaska and Hawaii. **Recommended:** nobody ever sees a blank Street.
   - **(b)** Switch with the lower 48 now. In Alaska and Hawaii, Street would show an empty map with a plain line, "Street map covers the lower 48 states. Switch to Topographical." Topographical still works there.
   - Falling back to OpenStreetMap there is ruled out by "nothing switches while the app is in use" (-1282), unless you want to revisit that.
3. **When outdoor places appear.**
   - Campgrounds, picnic sites, parking and peaks can show from the trailhead view.
   - Trailheads, viewpoints, ranger stations, toilets and water only exist in the map data at the walking view, about a few streets across.
   - Paths appear one step later than Elder's design: thinly at the trailhead view, all of them one step in.
   - **(a)** Accept that for now; it improves when your own build lands. **Recommended.**
   - **(b)** Wait for the own build before switching Street.
4. **Icons for six outdoor places.** The icon set has none for trailhead, parking, campground, picnic site, viewpoint or ranger station.
   - **(a)** A small dot with the name for now, real icons later. **Recommended.**
   - **(b)** Draw the six icons first. That adds to the time.
5. **Old offline areas after the switch.** An area downloaded before the switch draws in the old look (no names), and areas downloaded after draw in the new one.
   - **(a)** Each area draws in the style it was downloaded with, and old ones show "Older map style. Download again to update." **Recommended;** it matches -1294.
   - **(b)** Old areas stop drawing until downloaded again. This contradicts -1294; listed only to make the choice visible.
6. **Turning off the automatic deploy while you paste.**
   - **Today:** your map service redeploys itself from GitHub whenever main changes. If you paste new code in the dashboard and main changes before the same code is on it, the old service comes back and the new Street stops drawing.
   - **(a)** Pause the automatic deploy in the dashboard for the switch, and turn it back on once main has the new code. **Recommended.**
   - **(b)** Skip the dashboard and let the code reach main, which deploys it.
7. **Please confirm the mapping account is on the paid Workers plan.** On the free plan the service stops after 100,000 requests a day, which an everyday map would reach.
8. **The closest zoom's map pieces.**
   - **Today:** the closest zoom's pieces are fetched from Protomaps' public build the first time anyone looks at a spot, then kept. As an everyday map, that leans on a server Protomaps asks people not to hotlink.
   - **(a)** Accept it until your own build includes that zoom. **Recommended;** ask the Pi planner to build to zoom 15.
   - **(b)** Stop the data one zoom earlier and enlarge it. Trailheads, toilets and water would then not appear at all.

---

# Part 2: the build (dispatch 2026-10-18-05, "Part 2"; RECORD -1300 to -1302)

Hazel, for Rowan Waymark and the owner. Built 2026-10-18 from about 18:45Z. **The owner's go:** "3 go" (-1302). **The shape:** the owner's "Accept Hazel's recommendations" (-1300), and Workers Paid confirmed by the owner (-1301).

**Nothing was deployed or uploaded.** The owner deploys: the steps are below. **The release switch is at raster** (`STREET_SERVED_BY_OWN_MAP = false`), so today's app is unchanged until the owner approves flipping it.

**Base.** pre-main `712ff277`, checked against the remote. PR #257 (map-launch-fixes) was still open when the app part was reached, so `origin/map-launch-fixes` at `31e055c9` is merged into street-cloudflare (merge `127ef772`), as the dispatch directs. Its four `docs/audits/README.md` rows came in above the Part 1 row; the Part 1 row was moved below them so their "row above" references still point at their own rows. Every row is kept.

## What was built

### 1. The Worker (`server/pmtiles-worker/`)

- **No Pi.** `fetchFromPi` and its call are removed (`src/index.ts`; -879, -1300 (1)). Every tile comes from R2's archive, or from the z15 overflow, which is unchanged (-1300 (8)). The response still says `X-Forager-Origin: r2`. `wrangler.toml` no longer sets `PI_ORIGIN_URL`; the deployed variable and the two Access secrets are unused by the new code and can be removed at the owner's convenience.
- **New routes:**
  - `/style/street-v1.json`: the Street style, bundled into the Worker like `offline-style.json`, so it needs no upload.
  - `/fonts/{stack}/{range}.pbf` and `/sprites/v4/light{,@2x}.{json,png}`: read from R2 under `fonts/` and `sprites/v4/`, cached a week. Only those exact shapes are read; anything else under `/fonts/` or `/sprites/` is a 404 with no R2 read. They are matched before the tile parser, which would otherwise take `/sprites/v4/light.json` for a tileset named `sprites/v4/light`.
- **Unchanged:** `/style/offline.json` (byte for byte, below), TileJSON (still `maxzoom` 15), the tile path and the archive name (`us`).
- **Tests:** `test/worker.test.mjs`, nine cases, run with `npm ci && npm run build && npm test` against `dist/index.js`, the bundle the owner pastes. R2 is faked from a 363-byte, three-tile archive (`test/fixtures/tiny.pmtiles`, made by `test/fixtures/make-tiny-pmtiles.py`). The Worker is given the Pi variable and both secrets, as the deployed one has them, so "no Pi request" holds with the Pi configured.
- **The bundle the owner pastes:** 340.4 KiB, one file, `worker/index.js` in the handover folder, sha256 `c483ff69…b668`. The local build reproduced the deployed bundle exactly from pre-main's source before any change (the only difference a trailing newline), so the build is the same one Workers Builds runs.

### 2. The style (`map-style/`)

- `generate-street.mjs` builds `forager-street-v1.json` from the same pinned Protomaps light layers (5.7.2) with `overrides/forager-street.mjs` applied, and copies it to `server/pmtiles-worker/src/street-style-v1.json`. `forager-light.json` is unchanged (regenerated: no diff).
- **Why a separate file:** `overrides/forager-colours.json` can only change named colours and hide layers (`generate.mjs:17-50`); dashes, zoom ranges, filters, sizes and an outline layer are layer edits. Every edit names its layer and fails the build if that layer is gone.
- **What it does, decision by decision:**
  - **Paths (-1284, -1300 (4)):** `roads_other` is split. Footpaths (`kind_detail` footway, path, steps, bridleway, cycleway) are dashed red-brown `#a8452a`; vehicle tracks are dash-dot brown `#7a5530`; both from z13. No dotted informal layer: the tiles have no field for it; the colour is reserved.
  - **Forest (-1285):** `landcover` and `landuse_park` are solid at every zoom (no 5-7 fade out, no 6-11 fade in); one green `#a9d39b` for forest and wood; grass, scrub, meadow, heath, cemetery and golf course lighter `#dde9c1`; national parks, parks, protected areas and nature reserves leave the fill and get a dark green outline `#3e7a3a` from z6.
  - **Labels (-1286):** sizes up 15%, road and path names 14 px, text `#262626` (water `#1f4d86`) on a white 1.75 px halo.
  - **Points of interest (-1287, -1300 (4), (5)):** the nine outdoor kinds from z12, wherever the tiles carry them; shops and cafes (ten kinds) from z16; nothing else. The six with no icon draw the sprite's `townspot` dot with the name; peaks, toilets and drinking water keep their icons. Outdoor places sort ahead of shops.
  - **Night (-1288):** roads get coloured fills and darker casings so they survive the inversion (Elder's finding that white roads go black).
- **Checks:** `npm run check:street` runs `tools/check-style.mjs` on the preview copy (every font range and icon on disk; one icon, `generic_shield-0char`, absent as in `forager-light.json`, reported not failed) and `tools/check-street-night.mjs`: the style is valid against MapLibre's style spec, the Worker's copy is identical, and eleven day/night contrast pairs hold (footpath on ground 4.45 day / 6.47 night; label text on its halo 15.1 / 14.9; table in the script's output). A positive control: setting the footpath to a pale `#d9c9b8` made the check fail on both footpath pairs, then the file was restored from a saved copy and checked.
- **Renders for the owner's review** (GL JS in headless Firefox, phone-sized 412×800 at 2×, ten places, day and a pixel-exact V1 night of each): `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/renders/`, outside the repository (they show real place names). They are the web engine, not the phone: **the phone is the final word**, especially for labels. The tiles in them came through today's deployed Worker, so z7-14 came from the Pi.

### 3. The app (behind the switch, off)

- **`map/StreetStyle.kt`**, new: `STREET_SERVED_BY_OWN_MAP = false`, `STREET_STYLE_URL`, the zoom limit 18, the notice text, the sprite's icon names, and three rules: the style a download uses, the style the offline map loads, and whether a region shows the older-style notice. Each takes the switch as a parameter defaulting to the constant, so tests drive both values.
- **Online Street** (`BasemapStyles.kt`): `ownStreetStyleSourceFor` gives Street by URL, and at Topographical night a new `MapStyleSource.StreetWithTopoOver`: the Street style with the topo raster added on top (V1 paint, fading in 9.5 to 9.7) and the Street's labels ending at 9.5 (-1291). `mapStyleSourceFor`'s raster path is unchanged; with the switch off the new function returns `null`.
- **Zoom 18** (`operatingMaxZoom`, used by the style effect and `navigationMaxZoom`).
- **Credits:** the caption reads "Protomaps © OpenStreetMap" over the own Street, also under topo at night; the Protomaps row reads "Protomaps (Street and offline maps)"; Noto Sans for the Street's labels (OFL) and the tangrams icons (MIT) are added to `software.json`, marked `onlyWithOwnStreetMap` and listed only with the switch on.
- **Night:** the recolour walks `text-color`, `text-halo-color` and `icon-color` too (`NightColour.kt`), and runs for any style loaded by URL; the sprite is inverted image by image (`applyStreetSpriteNight`: `Style.getImage`, V1 per pixel, `addImage`), since its icons are not SDF. An image with no density is left in day colours and logged, because `Style.toImage` takes the pixel ratio from `Bitmap.getDensity` (read with `javap`).
- **Offline:** downloads use `offlineDownloadStyleUrl()`; the map's offline style is `currentOfflineRenderStyleUrl`, which with the switch off returns `OFFLINE_STYLE_URL` without reading the store. Each listed region carries the style URL MapLibre's own definition records (`OfflineRegion.getDefinition().getStyleURL()`, present in 13.5.0 by `javap`), so **no Room change** was needed. An older region's row shows "Older map style. Download again to update."
- **SDK calls checked with `javap`** against the pinned 13.5.0 `classes.jar` (fetched from Maven Central, SHA-1 matched the published one, then deleted): the region definition's `getStyleURL`, `Style.getImage`/`addImage`, the symbol colour getters and setters, `Layer.setMinZoom`/`setMaxZoom`, `RasterSource(String, TileSet, int)`, `TileSet` with `setMaxZoom`/`setAttribution`, `RasterLayer`, the raster `PropertyFactory` setters, `Style.Builder.fromUri`/`withSource`/`withLayer`.
- **Since pre-main moved:** PR #257 merged into pre-main at `d942e022` (RECORD -1307) while this was being built. `origin/pre-main` is merged into street-cloudflare at `2c009309` (merge, not rebase). Its only change beyond map-launch-fixes was `PI_ORIGIN_URL` removed from `wrangler.toml` (Amendment 3, -1303, -1304); the conflict with this branch's own comment there was resolved to one comment saying both. The `docs/audits/README.md` conflict kept every row.

## Evidence

**CI, the forward change:** run 39158767231 (`workflow_dispatch`, head `ce08a66e`): all three jobs green; **5,123 tests, 0 failed, 24 skipped** (counted from the run's JUnit XML). New and changed classes in it: `StreetStyleTest` 10, `OwnStreetStyleSourceTest` 9, `OfflineNightRecolourTest` 12, `BasemapStyleTest` 10, `SoftwareCreditsCatalogTest` 7, `OfflineRegionOlderStyleNoticeTest` 4, `OfflineRegionReconciliationTest` 17, all passing. Three runs before it failed and each was fixed for its own reason: 39156738430 did not compile (`TileSet` is a Kotlin class, so `attribution =`, not `setAttribution`); 39157084039 did not compile the tests (colons in test names); 39157687596 had one failure, the new host test, whose URL pattern took the JSON escape's backslash into the two credit links. That last one was the check being wrong, not the style: the fix is to the pattern, and the style is unchanged. **PR #258** (into pre-main, after pre-main `d942e022` was merged in): run 39160262201 on head `943516bc`, all three jobs green, **5,123 tests, 0 failed, 24 skipped**, counted from its JUnit XML. A later commit that only edits this report carries its own run.

**Revert checks.** Six branches, `street-cloudflare-revert-1` to `-6`, each one commit on `ce08a66e` changing one line, run with `workflow_dispatch` filtered to the affected classes (R6 unfiltered). **Every revert run's log was searched for compile errors first: none in any of the six**, so each result is that revert's own build. The forward change was never touched: the reverts were made in a separate worktree.

| | One-line revert | Failed | Each failure is one this revert can cause? |
|---|---|---|---|
| R1 (`1527b0b3`) | `NightColour.kt`: the `"symbol"` entry removed | 3 of 12: the walk list; "Key labels/text-color is missing"; "expected 85 but was 60" | yes: 25 label colours stop being walked |
| R2 (`b7c0e2be`) | `StreetStyle.kt`: the offline map loads the Street style whenever the switch is on | 1 of 10: "only older regions expected …offline.json but was …street-v1.json" | yes |
| R3 (`cbf068f0`) | `StreetStyle.kt`: the notice no longer requires a downloaded region | 1 of 14: "not downloaded on this phone" (`StreetStyleTest`) | yes. The Compose test for that case did **not** fail: its region also has no style URL, so it shows no notice for that reason too. It is reported, not counted as evidence for this line. |
| R4 (`85d75ca6`) | `BasemapStyles.kt`: the Topographical-night branch removed | 1 of 9: expected `StreetWithTopoOver(…)` | yes |
| R5 (`14e42fc4`) | `SoftwareCredits.kt`: the switch filter removed | 2 of 7: "credited with the switch off"; "exactly one library's name starts 'Noto Sans'" | yes: both new entries show |
| R6 (`5abf25dc`) | `StreetStyle.kt`: `STREET_SERVED_BY_OWN_MAP = true` | 16 of 5,123 | yes, and useful beyond this PR: see below |

**What R6 shows.** With the switch on, the app's own pins fail exactly where the own Street changes what the user sees: zoom 19 becomes 18 (`OwnStreetStyleSourceTest`, `NavigationViewTest`), the caption's credit becomes "Protomaps © OpenStreetMap" (`MapLayerPreferencesStateTest`, `MapAttributionListTest` ×2, `TopoNightStreetSwitchTest`, `OfflineStyleSwapTest`), the Protomaps credit row (`SourcesAndCreditsTest`, `StreetStyleTest`), the Street font credit (`SoftwareCreditsCatalogTest` ×2), the notice on today's regions (`OfflineRegionOlderStyleNoticeTest`, `StreetStyleTest`), the download style (`StreetStyleTest`), and Topographical night is no longer a raster JSON (`OfflineStyleSwapTest`, a `ClassCastException` in its own cast). So the release value is pinned from many sides, and **flipping the switch later is a change that has to update about nine existing tests' expectations** to the own Street's, by an owner-approved dispatch. None of those was touched here.

**The Worker:** 9 of 9 route tests pass against the new bundle. Against the **deployed** bundle (the rollback copy), 7 fail and 2 pass: the tile test fails with "the Worker made outbound requests: … origin.zynergy-labs.com/us/14/2651/5776.mvt … CF-Access-Client-Id", the z15 test with the Pi request listed before the overflow's, and the five new-route tests with 404 or a `sprites/v4/dark.pmtiles` R2 read; **`/style/offline.json` and TileJSON pass on both**, which is the byte-for-byte evidence that the offline style and TileJSON are unchanged. The new bundle was restored from a saved copy after that run and its sha256 checked.

**The style:** valid against the style spec; `check-style` and `check-street-night` pass, with the positive control above; renders as listed.

**Not verified, device only:** everything that runs only in MapLibre Native. Which colour forms `SymbolLayer`'s getters hand back (logged, never dropped); whether `Style.getImage` gives a bitmap with a density (logged and left if not); the topo raster added over a URL style; labels at 9.5; a Street region downloading and drawing offline (the 2026-10-04 label check is the precedent, not this code); the notice on a real old region; the zoom stop at 18; the look itself. With the switch off none of this runs in today's app.

## The owner's steps, in order (nothing here has been done)

The handover folder: `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/handover/`
- `worker/index.js`, the Worker to paste (sha256 `c483ff699f4be6e053f53cd5e3438eaea97a22f821d38b278b5a68a45710b668`);
- `r2-upload/`, 774 files, 14 MB: `fonts/Noto Sans Regular/` (256 files), `fonts/Noto Sans Medium/` (256), `fonts/Noto Sans Italic/` (256), `fonts/OFL.txt`, `sprites/v4/light.json`, `light.png`, `light@2x.json`, `light@2x.png`, `sprites/LICENSE-MIT.md`. All 772 font and sprite files were compared byte for byte with the pinned archive (basemaps-assets `028c18f7`, whose sha256 `fetch-assets.sh` records): 0 mismatches.

**Deploying the Worker changes nothing today's app shows.** Today's app reads only `/style/offline.json`, TileJSON and tiles from it, all unchanged; tiles already come from R2 since the owner removed `PI_ORIGIN_URL` (-1305). The new routes are read only by a build with the switch on.

1. **Pause auto-deploy** (-1300 (7)). Cloudflare dashboard › Workers & Pages › `forager-pmtiles` › **Settings** › **Builds**. Write down the production branch shown there. Disconnect the repository, or turn off automatic deploys for the production branch if the page offers it.
2. **Upload fonts and icons.** R2 object storage › `forager-maps` › **Upload**. Drag in the `fonts` folder and the `sprites` folder from `r2-upload/`, keeping their names. When done, open `fonts` › `Noto Sans Regular`: 256 files from `0-255.pbf` to `65280-65535.pbf`. **Do not touch `us.pmtiles` or the `overflow` folder.** If the page will not take folders, or flattens them, stop there and say so: the fallback is a one-command upload from the laptop with an R2 token or `wrangler login`, which needs your sign-in.
3. **Paste the Worker.** Workers & Pages › `forager-pmtiles` › **Edit code**. Select everything in the editor and delete it, paste the whole of `worker/index.js`, then **Deploy**. Bindings, variables and secrets are settings, not code, and stay as they are.
4. **Check, from any browser:**
   - `https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json` loads as before;
   - `…/style/street-v1.json` loads, and its text names only `forager-pmtiles.brandonlee1-894.workers.dev`;
   - `…/fonts/Noto%20Sans%20Regular/0-255.pbf` downloads a file of about 75 KB;
   - `…/sprites/v4/light.json` shows a list of icons;
   - a fresh tile, `…/us/14/2652/5776.mvt`, downloads.
5. **Leave auto-deploy paused** until main has this Worker code (this PR merged into pre-main, then promoted to main). Then reconnect it with the same production branch as before. Reconnecting earlier would put the old Worker back on the next push to main, and with it the Street routes would 404 (Q3 of Part 1).
6. **Phone review rounds on the look** (-1284 to -1289 checks). The app's switch is off, so a review needs a separate build with it on, which nobody ships: a review APK from a branch with the one line flipped, built by CI and installed on the S22 by the planner. Each round: the places in Elder's checks at three zooms, outdoors in daylight; then Night Maps on at the same places; then Topographical at night zooming out past 9.5. The colours live in `map-style/overrides/forager-street.mjs`; a round's changes are a new style build and a new Worker paste (steps 3 and 4), or a `street-v2.json` if any region has already been downloaded against v1.
7. **Flipping the switch** for users is a later dispatch with the owner's word, after the phone review passes and the Pi's build covers Alaska and Hawaii (-1300 (3)).

**Rollback.** Workers & Pages › `forager-pmtiles` › **Deployments** › the deployment from 2026-10-15 › **⋯** › **Rollback**. Or paste `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/deployed-index.js` (sha256 `d92bafa1…55ad`) in step 3's editor and Deploy. The R2 uploads can stay: the old Worker never reads them. Note that rolling back to the 2026-10-15 code puts the Pi-first step back, which only reads `PI_ORIGIN_URL`; with that variable deleted (-1305) the old code still serves from R2.

## When a new US archive lands in R2

- **Same name, same schema** (the Pi's build uploaded over `us.pmtiles`, built with Protomaps' basemap schema): nothing in the Worker, the style or the app changes. Tiles change as caches expire: a day at the Worker's edge cache (`Cache-Control: public, max-age=86400`, per data centre), and on phones per MapLibre's own cache. Offline regions keep the tiles they downloaded until downloaded again. If the new archive goes to z15 (the Pi's profile does, per the coordinator), the Worker serves z15 from it and the overflow path stops being used; the `overflow/` objects in R2 are then dead weight the owner can delete.
- **Alaska and Hawaii:** covered as soon as the archive's bounds include them; the Worker's TileJSON reports whatever the archive holds.
- **A different name** (for example `us-2026-11.pmtiles`): the style's source URL names `us.json`, so it needs a new style version (`street-v2.json`), because offline packs are keyed by the style URL.
- **A different schema** (layer names or `kind` values other than Protomaps v4's): the style would draw blank or partial maps. **Could not determine** which schema the Pi's build profile writes; it should be checked before the upload.

## Part 2: confirmed vs inferred

- **Observed:** the forward CI run and its counts; the six revert runs, their logs free of compile errors and their failures; the Worker tests against both bundles; the style checks and the control; the renders; the handover files against the archive.
- **Read:** the 13.5.0 API by `javap`; the code cited.
- **Inferred, device only:** everything in "Not verified" above; that a Street region's stored fonts and icons let older regions draw in the Street style too (the store keys resources by URL; not seen on a phone).

## Part 2: could not determine

- Whether the dashboard's R2 upload takes nested folders (step 2's fallback is written for that).
- Which tile schema the Pi's build writes.
- How the design reads on the phone, outdoors.

## Part 2: premises that were wrong

- "The app part goes on top of map-launch-fixes once PR #257 merges": it merged mid-build; both merges are recorded above.
- None that changed what was built.

## Part 2: decided beyond scope (disclosed; each is the owner's to overrule)

1. **Older offline areas once one Street area exists.** -1300 (6) reads "each draws in the style it was downloaded with". As built, the offline map loads the Street style once **any** downloaded area holds it, and the older style until then (`offlineRenderStyleUrl`). With a mix, the older areas then draw in the Street style too (they gain names), rather than each in its own. Drawing each in its own would mean the map changing style as it pans between areas. Question 1 below.
2. **Points of interest other than the outdoor list and shops and cafes are dropped** (museums, schools, bus stops and the like): -1287's list and "shops and cafes only zoomed into a town", read as the whole list.
3. **Left as Protomaps draws them:** the ground colour, water, buildings, shields, tunnels. Steps are drawn as footpaths, not as ticks. None was ruled.
4. **The dot** for the six missing icons is the sprite's own town dot (`townspot`).

## Part 2: questions for the owner

1. **Older offline areas, once you have downloaded one area in the new look.**
   - **(a)** All your areas draw in the new look from then on, the older ones too, with names; until then, the older look. **Recommended:** nothing changes look as you move around. *(As built.)*
   - **(b)** Each area keeps its own look, so the map changes look as you move from an older area into a newer one.
2. **Ready for the first look on the phone?** After the deploy (steps 1 to 4), the planner can build a review copy of the app with the new Street switched on, for the S22 only. **Recommended:** yes, one round in daylight and one at night.

---

# Part 2, review round 1: night water (RECORD -1343, -1344)

**The owner, verbatim (-1343):** "Pretty slick. I like the blue of the water, but looking at creeks is a bit dark against the other dark colors. Can the water be a little bit lighter in blue color?", then "Night only, lighter (Recommended)". **-1344:** "Pure inversion is easy yes, but we can go outside the purity for the UX."

**Built (commit `d6497155`):**
- At night the own Street's water fills and lines (the `water` fill, `water_stream`, `water_river`: rivers, creeks, lakes and the sea) are drawn `#8ec5f0` instead of the inversion's `#005864`. Day water is unchanged (`#80deea`). Water labels keep the inversion.
- Where it lives: `NightColour.kt`, `STREET_NIGHT_WATER` and `streetNightRecolourOf`, called by the night recolour only for the own Street style (`streetWater = usesStreetSprite(source)`). The offline style keeps its inverted water, so today's app is unchanged. Layers are told apart by their vector source layer (`FillLayer`/`LineLayer.getSourceLayer()`, read with `javap` at 13.5.0).
- Contrast: `#8ec5f0` is 3.28:1 on the night forest `#436d35`, 6.68:1 on the night grass `#2d3911`, 8.70:1 on the night ground `#24211c`. The inversion gave 1.35:1 on the forest.
- The design value is `nightWater` in `map-style/overrides/forager-street.mjs`, recorded in the style's metadata (`forager:palette`). `NightWaterTest` holds the app's constant equal to it, holds 3:1 or better on the night forest and grass (computed with the app's own `nightColorOf`), checks that every water fill and line in the served style takes it while every other property is the plain inversion, and checks that the offline path's water is still inverted. `check-street-night` checks the same three pairs; its positive control, the inverted `#005864`, fails all three.
- The Worker bundle changed only by the style's metadata: the handover `worker/index.js` is rebuilt, sha256 `1ef4df8ef0a40a14f5a69202a161b77eb3c1287bbf0d19afbeed4d4bb1506e38` (9/9 route tests).
- pre-main `e431eba9` merged in (merge `368f125e`); the index conflict kept every row.

**The previews were regenerated** into `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/renders/`, 20 files, overwriting round 0's. **The night images are now drawn the way the app draws night**, not as before: round 0's night images were the day render inverted pixel by pixel, which could not show a per-feature exception. Now each night image is the style with every walked colour put through V1 (the app's `nightColorOf`), the water set to `#8ec5f0`, and the icons from a V1-inverted copy of the sprite (the app inverts each icon image the same way). Script: `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/scripts/night_style.py`. The map controls in the corners stay light in the night images; the app's own chrome is not part of this.

## Other features that read weakly at night (measured; nothing applied, the owner chooses)

Night colours are V1 of the day colours, measured against the night ground `#24211c`, night forest `#436d35` and night grass `#2d3911` (WCAG contrast). The proposed colours are night exceptions like the water's, each checked at the same three grounds.

| Feature | Night now | Ground / forest / grass | Proposed night colour | Ground / forest / grass |
|---|---|---|---|---|
| Minor road edge (casing) | `#776f61` | 3.23 / **1.22** / 2.48 | `#c4bcae` | 8.52 / 3.21 / 6.54 |
| Minor road centre | `#000000` | **1.31** / 3.48 / **1.70** | leave: the edge carries it once lightened | |
| Service road edge | `#696153` | 2.62 / **1.01** / 2.02 | `#b3ab9d` | 7.05 / 2.65 / 5.41 |
| Major road centre | `#634300` | **1.78** / **1.49** / **1.37** | `#e0ad4f` | 7.84 / 2.95 / 6.02 |
| Highway centre | `#965f02` | 3.01 / **1.13** / 2.31 | `#f0c060` | 9.48 / 3.57 / 7.28 |
| Railway | `#495355` | 2.03 / **1.31** / **1.56** | `#b4bec0` | 8.45 / 3.18 / 6.49 |
| Boundaries | `#525252` | 2.05 / **1.29** / **1.58** | `#bdbdbd` | 8.54 / 3.21 / 6.56 |
| The dot (six outdoor places, and towns), its ring | `#797979` | 3.68 / **1.39** / 2.83 | `#d6d6d6` | 11.04 / 4.15 / 8.47 |
| Toilets and drinking-water icons, their ring | `#655e89` | 2.69 / **1.01** / 2.06 | `#c6bfea` | 9.20 / 3.46 / 7.06 |
| Footpaths (dashes) | `#ed8a6f` | 6.47 / 2.43 / 4.97 | optional `#f6a58b` | 8.15 / 3.07 / 6.26 |
| Vehicle tracks | `#cfaa85` | 7.44 / 2.80 / 5.71 | optional `#e2c09c` | 9.36 / 3.52 / 7.19 |
| Park outline | `#9cd898` | 9.69 / 3.65 / 7.44 | none needed | |
| Labels (text on halo) | `#d9d9d9` on `#000000` | 14.9 on their own halo | none needed | |
| Buildings (fill) | `#333333` | 1.27 / 2.09 / 1.03 | low priority: a fill, not a line | |

Two notes on the table. The icon figures are each icon's two most common opaque colours read from the pinned sprite, not a rendered measurement. A road's centre and edge read together, so lightening the edges alone may be enough for minor and service roads; the major road and highway centres are the ones that go darkest.
