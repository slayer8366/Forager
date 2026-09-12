# Tile-policy pulse, before the beta

**Date:** 2026-09-12
**Base: `175b050`** (`origin/main`, PR #100 merged), fetched and verified before writing.
**Type:** pulse — read-only, no code changed. Sent now on the owner's call because it is the one open
item that can present as the app being broken during the fourteen days twelve people are deciding
whether to keep it installed.

**Question:** by what path can a third party's tile-policy action, or an outage, reach a tester's
screen during the beta, and what is the smallest change that closes each path?

---

## The chain nobody had traced, and it is one tile long

Every offline-region download requests z15 (`OfflineMapRepository.MAX_ZOOM = 15.0`), and every
download targets the project's own Worker only (`MapLibreOfflineMapRepository.kt:97-98`,
`OfflineTilePyramidRegionDefinition(OFFLINE_STYLE_URL, ...)`). z15 tiles the Worker has not yet
cached are range-read live from `build.protomaps.com` on first request (`index.ts`, overflow path;
compliance audit finding #2). If that read fails — Protomaps throttles or blocks Cloudflare's
egress, the daily-build filename resolves to nothing past the five-day lookback, or any transient
error — the Worker returns **500** (corrections C4: `resolveRemoteBuildUrl` throws a plain `Error`
the handler does not catch).

What the app does with that, on `175b050`:

```kotlin
// MapLibreOfflineMapRepository.kt:311-315
override fun onError(error: OfflineRegionError) {
    if (continuation.isActive) {
        Log.w(TAG, "Offline region download failed: ${error.reason}: ${error.message}")
        continuation.resumeWithException(java.io.IOException("Offline map download failed."))
    }
}
```

**The first resource error fails the entire download.** No retry at this layer, no partial
completion, no distinction between one missing z15 tile out of ~1,000 and a dead server. Then
`AvailabilityViewModel.kt:1042-1044`:

```kotlin
errorLog.w(TAG, "Couldn't download offline maps.", error)
_uiState.update { it.copy(offlineDownloadStatus = OfflineMapStatus.Failed("Couldn't download offline maps.")) }
```

So the tester sees **"Couldn't download offline maps."** — for a feature the store description
leads with — because one z15 tile, of a level the z14 archive would have over-zoomed cleanly
without, came back 500 from a source the project does not control and Protomaps asks not to be
hot-linked.

**Unverified, stated as such:** whether MapLibre Native 13.5.0 retries a 5xx internally before
surfacing it to the observer. It may; the point stands either way, because whatever surfaces,
surfaces as total failure.

## The four hosts, re-read on `175b050`

| Host | What the app sends | Governing clause | Block risk in 14 days × 12 testers | If it fails, the tester sees |
|---|---|---|---|---|
| `tile.openstreetmap.org` (`Basemap.kt:171`) | MapLibre's default UA `com.zynergylabs.forager.app/<versionName> (<versionCode>)` — app-identifying, not generic (`HttpIdentifier.java`); `If-None-Match`/`If-Modified-Since` on revalidation (`HttpRequestImpl.java:83,85`); no `no-cache`; attribution always visible; no bulk download; prefetch delta 4 = same-viewport look-ahead | OSMF §3.4 targets *generic* defaults "because we cannot identify or contact the actual application"; §4 permits "modest, short-range look-ahead" | **Low.** Twelve interactive users are not "heavy use" by any reading, and every must-do is met | Blank map tiles on the OSM basemap only; two other basemaps unaffected |
| `a.tile.opentopomap.org` (`Basemap.kt:162`) — the default basemap | same | "provided our server is not overly strained by bulk downloads"; no uptime guarantee | **Low** for a block; **medium** for plain unavailability of a volunteer host | Blank tiles on the **default** basemap — the first thing a new tester opens |
| `basemap.nationalmap.gov` (`Basemap.kt:148`) | same | public domain | Low | Blank satellite tiles |
| `demotiles.maplibre.org` glyphs (`BasemapStyles.kt:154`, on **every** online style) | glyph range fetches | none written; stated purpose "web, helloworld and CI tests," GitHub Pages | not a block risk; **medium** availability risk | Numbered foraging-area labels render as **nothing, silently** (`BasemapStyles.kt` class doc) — on every basemap at once |
| own Worker, `forager-pmtiles.*.workers.dev` | style + `z/x/y` vector tiles; z15 via the overflow | Protomaps: "hotlinking to these downloads are discouraged" | **the only path with a total-failure outcome** (above) | "Couldn't download offline maps." |

**The interactive raster paths are fine for the beta.** The compliance audit's remaining
raster-host findings are recommended-only after corrections C1, and nothing in this table blocks.

**The offline path is the exposure**, and it has two triggers, one of which needs no policy action
at all:

1. **Policy:** Protomaps notices sustained per-tile reads from a Cloudflare egress and blocks or
   throttles. The Worker is public, `ALLOWED_ORIGINS = "*"`, zoom-gated only (finding #3), and its
   URL is in the app and in a public repository's README — so the traffic Protomaps sees is not
   bounded by twelve testers.
2. **Plain failure:** the daily build resolution misses (retention is "all builds for the past
   week"; lookback is five days), or one read of ~1,000 per region times out. Same 500, same
   tester-facing message.

## The smallest changes, ranked by tester impact per line, and where each lands

**1. Worker: never answer the overflow path with 500.** Catch around `overflowTileResponse` and the
build resolution; return **404** (or 204) for the tile instead. MapLibre treats a 404 on a tile as
"no tile here," not an error — a download completes without that z15 tile and the z14 beneath it
over-zooms, which `OfflineMapRepository.kt:107-108` already says "renders sharp well past it."
**Unverified, must be checked before relying on it:** that 13.5.0's `OfflineManager` counts a 404
tile as a completed resource rather than an `OfflineRegionError`. If it does, this one change turns
every failure mode above into invisible loss of z15 detail. **It deploys with the Worker, not the
app** — Cloudflare Workers Builds redeploys on push to the Worker's production branch
(`server/pmtiles-worker/README.md`), so it does not touch the beta build the owner is producing
from `main`.

**2. Worker: bbox-gate the overflow to the archive's own bounds** (finding #3's stopgap). Removes
the crawl exposure that feeds trigger 1. Same deploy path, same day.

**3. Worker: cache the resolved build URL longer than six hours and fall through to the last known
good URL on a miss**, so a publishing gap does not become a 500 on its own. Small.

**4. App: do not fail a region on the first tile error** — count, continue, complete with a
warning if the count is non-zero. This is app code, changes a deliberate design (the observer's
own comment records why it throws a fixed message), and needs the ViewModel's status model to
carry "partial." **Not pre-beta**; recorded so the option exists.

**5. Glyphs: bundle the `Open Sans Semibold` ranges** (compliance finding #5, Apache-2.0 notices
per correction W1). Removes the one dependency that can blank labels on every basemap at once.
App change; medium; pre-beta only if the build is not already cut.

**6. OSM recommended items** — `fixthemap` link, contact email on the listing. Optional; the
listing one is not code.

**What the beta build itself needs from this pulse: nothing.** Items 1–3 are Worker-side and land
independently of the AAB. That is the useful finding for the owner's own list: the highest-value
fixes here do not gate "build from current main, verify against the bundle, upload."

## Disclosure

**Verified on `175b050`:** the four host constants and their line numbers; no app-level UA or
OkHttp override for MapLibre; the offline definition's target; the observer's `onError` body and
the ViewModel's user-facing status text, both quoted; `MAX_ZOOM = 15.0`.

**Carried from earlier documents with scope stated:** MapLibre's default UA format and conditional
request headers (`HttpIdentifier.java`, `HttpRequestImpl.java`, read on maplibre-native `main`,
2026-09-11); the Worker's 500 path (corrections C4, read on the Worker source at `f7c9f15`, file
unchanged since); Protomaps' retention and hot-linking sentences (fetched 2026-09-11); the OSMF
clauses (fetched 2026-09-11).

**Unverified, and load-bearing for fix 1:** whether MapLibre 13.5.0's `OfflineManager` treats a 404
tile as completed rather than errored, and whether it retries 5xx before calling `onError`. Both are
answerable from the SDK source and should be before fix 1 is relied on.

**Not measured:** any actual request volume, tile count per region under real use, or Protomaps'
tolerance. The exposure is structural — public endpoint, unbounded gate, single-error total
failure — and does not depend on the numbers.

---

## Correction (2026-09-12, same day): fix 1 must return **204, not 404** — verified against the SDK

The pulse's fix 1 said "return 404 (or 204)" and marked the load-bearing claim unverified. It is now
verified against the `android-v13.5.0` tag of `maplibre-native`, and **404 is wrong for this app**.

**Download side** (`platform/default/src/mbgl/storage/offline_download.cpp:522-531`):

```cpp
if (onlineResponse.error) {
    observer->responseError(*onlineResponse.error);          // fires for a 404 too
    if (onlineResponse.error->reason == Response::Error::Reason::NotFound) {
        // On error 404, we skip this request and go further.
        requests.erase(fileRequestsIt);
        status.requiredResourceCount--;
        continueDownload();
    }
    return;
}
```

The SDK does continue past a 404 — but it reports it to the observer **first**. On Android that is
`OfflineRegionObserver.onError(OfflineRegionError)` with `REASON_NOT_FOUND`, a constant confirmed on
the pinned 13.5.0 AAR by `javap` (`REASON_SUCCESS, REASON_NOT_FOUND, REASON_SERVER,
REASON_CONNECTION, REASON_OTHER`; the JNI hop from `responseError` to `onError` is inferred from
that enum's existence, not read). And `MapLibreOfflineMapRepository.kt:311-315` throws on **any**
`onError`. So a Worker-side 404 still fails the whole region in this app. A **204** carries no
error, is stored as a normal empty resource, and never reaches `onError`.

**Render side**, which is where the pulse's "over-zoom" claim actually lives, traced rather than
taken from `OfflineMapRepository.kt:107-108` (a doc comment about zooming *past* `maxzoom`, a
different case):

1. `tile_loader_impl.hpp:162` — a `NotFound` error is not treated as an error; `:183` —
   `tile.setData(res.noContent ? nullptr : res.data)`. **404 and 204 both become `setData(nullptr)`.**
2. `vector_mvt_tile.cpp:29` — `GeometryTile::setData(data_ ? ... : nullptr)`.
3. `geometry_tile.cpp:264-267` — no null branch; `pending = true`, handed to the worker.
4. `geometry_tile_worker.cpp:420` — `parse()`: `if (!data || !layers) return;` No layout produced.
5. `geometry_tile.cpp:342-345` — `renderable = true` is set only in `onLayout`, which never runs.
   `tile.hpp:153` — `renderable` defaults `false`.
6. `algorithm/update_renderables.hpp:50` — `if (tile->isRenderable()) render(ideal); else` look for
   children, then **parents**. The empty z15 tile is never renderable, so **its z14 parent is drawn,
   over-zoomed**.

That is the same path every empty ocean tile in the archive takes today (the Worker already answers
in-archive misses with 204, `index.ts`), so it is production behaviour, not a theory.

**Corrected fix 1:** the Worker answers any overflow failure — build-URL resolution miss, upstream
error, timeout — with **204**, plus a response header naming why (the owner's round-3 suggestion,
e.g. `X-Forager-Overflow: unavailable`), which also satisfies `CLAUDE.md`'s no-unlogged-fallback rule
without writing a log line. Result: the region download completes, the tester sees nothing, and
the z15 detail degrades to the z14 archive over-zoomed — exactly what the maxzoom-14 fork would give
by design, applied only when the overflow source is unavailable.

**Stated trade, not hidden:** a 204 for a tile that has data upstream presents "no data at z15."
The rendered outcome is real z14 data at coarser resolution, not a fabricated value, and the header
names the degradation; that is the honest form of `CLAUDE.md`'s "explicit unsupported" here.

**Unchanged:** this remains Worker-side and lands independently of the beta build. **What the AAB
needs from this pulse is still nothing.**

**Unverified, non-blocking:** whether `TileLoader` re-requests a never-loaded empty tile on later
updates. It is existing behaviour for every 204 tile in production and has not presented as a
problem.
