# Report — find the deep-zoom raster capture path

**Date:** 2026-08-28. Investigation only, no writes made, no transform built. Exhaustive source search
across `app/src/main` (not a sample), plus real source reads of the exact points the search couldn't
settle from grep alone (`offline_download.cpp`'s per-tile-404 handling, at the pinned commit).

**Bottom line up front, since it changes how to read everything below**: there is no second raster
capture path anywhere in this app's code. Every symptom in the dispatch's "what we now know from
hardware" section is fully and precisely explained by a mechanism this workstream has already found
twice — the live raster basemap's ordinary ambient cache — once you account for one fact this report
adds: **OpenTopoMap's own `maxZoom` is 17**, five levels past the vector offline region's z10-14
ceiling, using a tile URL whose baked-in labels really do rotate with the map, because it's a plain
raster layer. Nothing was "captured and added to the offline maps." The two systems were never
connected in the first place.

---

## 1. Find the raster capture — exhaustive search, negative result

Searched all of `app/src/main` (not scoped to a folder that might have hidden it) for every
mechanism the dispatch itself lists as a candidate, plus a few more:

| Searched for | Result |
|---|---|
| A second `createOfflineRegion`/`OfflineTilePyramidRegionDefinition` call site | None. Exactly one, in `MapLibreOfflineMapRepository.kt:98-104`, already accounted for in the prior report. |
| `OfflineManager.putResourceWithUrl` used anywhere in app code | Zero hits, anywhere. The prior report only established that this method *exists* and *couldn't work for tiles even if used* — it turns out the app doesn't call it at all. |
| `prefetch`/`warm.?cache`/`precache`/`deepzoom`/`captureTile`/`tileCapture`/`rasterCapture` (case-insensitive) | Zero hits anywhere in `app/src/main`. |
| A third `MapLibre.getInstance()` call site | None. Exactly two real ones: `SightingsMap.kt:201` (the live `MapView`) and `MapLibreOfflineMapRepository.kt:221` (the downloader). A third, `MapLibreBasemapPreviewActivity`, is referenced only in doc comments as PR #23's throwaway test scaffold — **confirmed not to exist as a file, in git history, or in the manifest**; it was never merged into this codebase. |
| `HttpRequestUtil`/`setOkHttpClient`/`Call.Factory` used anywhere in app code | **Zero hits.** No OkHttp interceptor is installed on MapLibre's HTTP path today, by this app, at all. Every earlier report's finding that one *can* be installed was about SDK capability, never about current behavior — worth stating plainly since it's easy to misread those reports as describing something already wired up. |
| Any `Worker`/`Service`/`JobService`/`BroadcastReceiver` that might fetch tiles as a side effect | One `Service` exists in the whole app: `TrackRecordingService`. Read directly — GPS track recording only, zero references to tiles, MapLibre, or any map API. Ruled out. |
| `OkHttpClient` used anywhere, for any purpose | Three call sites, all unrelated remote API clients: `OpenMeteoClient`, `OpenMeteoArchiveClient`, `INaturalistClient` (weather and species data). None touch map tiles. |

**If this is happening, it isn't app code doing it.** Per the dispatch's own instruction to say so if
that's the finding: this is the finding. The next four sections work out what actually explains the
hardware observations instead.

---

## 2. How the bytes actually get stored (the real mechanism, not a hypothetical one)

No dedicated capture code exists — but ordinary live map browsing was never a hypothetical mechanism
either; it's the one system already fully characterized by the 2026-08-28 tile-interception and
offline-interception reports, and it's sufficient on its own:

- **Every raster basemap tile the live `MapView` requests while online** (`SightingsMap.kt`'s one real
  `MapView`, rendering whichever of `Basemap.OPEN_TOPO_MAP`/`OSM_STANDARD`/`USGS_IMAGERY_ONLY` the user
  selected) goes through the exact same path the offline-interception report already traced in full:
  `main_resource_loader.cpp`'s database-then-network waterfall, `onlineFileSource->request` →
  `HttpRequestImpl` → (today) MapLibre's own default OkHttp-less native HTTP stack, since no
  `Call.Factory` is installed. **Yes, this does go through the same bridge an interceptor would attach
  to** — the "load-bearing question" in the dispatch's item 2 — it's just that nothing is attached to
  it right now.
- **On a successful fetch, `databaseFileSource->forward()` stores the response** (same code path read
  in the offline-interception report, `main_resource_loader.cpp:49-51`) via `OfflineDatabase::put` →
  `putInternal`. Because this is a real tile request (`Resource::Kind::Tile`, not `Kind::Unknown` the
  way `putResourceWithUrl` would produce), it correctly lands in the **`tiles`** table, keyed by
  `(url_template, pixel_ratio, z, x, y)` — not `resources`. This is exactly why it renders: the
  renderer's tile lookup reads `tiles`, and this path writes `tiles` directly, no detour through the
  method the prior report ruled out.
- **No region association is ever created for these rows**, because nothing in app code ever calls a
  region-linking API for a live browse. They are, definitionally, **ambient cache** — the same
  category `region_tiles`-absent rows always fall into per the schema (§1 of the post-download-transform
  report).

**This confirms the dispatch's own stated risk, not a milder version of it**: coverage at any zoom
past the vector region's z10-14 exists **only** where the user happened to browse live, is bounded and
evicted by MapLibre's own ambient-cache size limit (`setMaximumAmbientCacheSize`) rather than this
app's `TILE_COUNT_LIMIT`, and could disappear under storage pressure with nothing in the UI to say so.
"Extra zoom step captured and added to the offline maps" describes what it *looks* like from the
outside; "whatever happened to be visited live, sitting in a general-purpose cache with no
durability guarantee," is what it actually is.

---

## 3. The actual contents — query provided, not run (no device, as every report this workstream has
filed has had to say)

Exactly the query the dispatch specifies, plus the same query's `LEFT JOIN` counterpart, ready to run
against a real region database:

```sql
-- Region-linked (durable) rows
SELECT t.url_template, COUNT(*) AS n, SUM(LENGTH(t.data)) AS bytes,
       MIN(t.z) AS min_z, MAX(t.z) AS max_z
FROM tiles t JOIN region_tiles rt ON rt.tile_id = t.id
GROUP BY t.url_template;

-- Ambient (evictable) rows — no region link at all
SELECT t.url_template, COUNT(*) AS n, SUM(LENGTH(t.data)) AS bytes,
       MIN(t.z) AS min_z, MAX(t.z) AS max_z
FROM tiles t LEFT JOIN region_tiles rt ON rt.tile_id = t.id
WHERE rt.tile_id IS NULL
GROUP BY t.url_template;
```

**Predicted result, stated as a falsifiable prediction rather than a claim about data I haven't
seen**: the first query should show exactly one `url_template` — the pmtiles Worker's
`.../us/{z}/{x}/{y}.mvt` — with `min_z`/`max_z` inside 10-14 (per the real per-zoom tile counts
already measured against the live server in the post-download-transform report). The second query
should show zero, one, two, or three of `Basemap`'s real raster templates —
`https://a.tile.opentopomap.org/{z}/{x}/{y}.png`, `https://tile.openstreetmap.org/{z}/{x}/{y}.png`,
and/or the USGS ArcGIS template — with whatever `z` range happens to match this device's own browsing
history, not a fixed or predictable range. If the second query comes back empty on a real device that
still shows "deep zoom" content offline, that would falsify this report's explanation and reopen the
search in §1 — a concrete, cheap way to check this without trusting source reading alone, exactly as
the dispatch asked for.

---

## 4. The zoom seam — there isn't a designed one, and that's the finding

The dispatch frames this as "the exact zoom at which the map transitions" — a single, well-defined
boundary to author a palette against. **That framing doesn't hold once §1-2 are accounted for.** There
are two systems with no coordination between them:

- The vector region: bounded, durable, exactly z10-14, always present if a region was downloaded and
  the app hasn't cleared its data (per the earlier addendum's confirmed storage mechanism).
- The raster ambient cache: unbounded in principle, evictable, present **only** at whatever
  `(url_template, z, x, y)` combinations this specific device happened to fetch while online — which
  could be contiguous with z14, could have gaps starting at z14, could extend to z17 in one direction
  and not exist at all in another part of the same "downloaded" region, and can shrink over time
  without any user action.

**This is worse for the palette question than a clean seam, not better.** A fixed seam at (say) z15
could be authored against directly. An ambient, history-dependent, evictable boundary means the actual
transition zoom on a real device is unknowable in advance and can change between sessions. The
practical implication for night-mode palette work: don't author against "the seam" as a fixed point.
Author the vector region's night style and the live raster basemaps' night transform (already the
subject of the tile-interception and night-inversion reports) to land in the *same target luminance
band independently* — since either one can be what's on screen at any given zoom on any given device,
with no reliable transition point between them to lean on.

---

## 5. The z15/maxzoom mismatch — confirmed from source, and confirmed not to be the raster capture

**Read directly from `offline_download.cpp` at the pinned commit** (`OfflineDownload`'s tile-response
handling, lines ~521-531):

```cpp
if (onlineResponse.error) {
    observer->responseError(*onlineResponse.error);
    if (onlineResponse.error->reason == Response::Error::Reason::NotFound) {
        // On error 404, we skip this request and go further.
        requests.erase(fileRequestsIt);
        assert(status.requiredResourceCount > 0);
        status.requiredResourceCount--;
        continueDownload();
    }
    return;
}
```

**A 404'd tile request decrements `requiredResourceCount` and the download simply continues.** The
region's completion is defined against a denominator that shrinks to exclude the tiles that never
existed — so `MapLibreOfflineMapRepository.kt`'s `downloadToCompletionSuspend` (which only observes
`onStatusChanged`/`onError`/`mapboxTileCountLimitExceeded`, confirmed against the Android
`OfflineRegionObserver` interface's full method set via `javap` — no separate per-tile-error callback
is bound to Java at all) **never sees these 404s as an error.** `finalStatus.isComplete` becomes true
with the z15 tiles simply absent, not counted as missing, not surfaced anywhere. **The download reports
success**, not partial and not error — confirmed, not inferred from the schema alone.

**Confirmed: this is not the raster capture, because there is no raster capture** (§1). The z15 gap
isn't being intentionally backfilled by anything. It's a real, if harmless-to-completion, latent bug —
`OfflineMapRepository.MAX_ZOOM = 15.0` requests a level the tileset's own TileJSON says stops at 14
(the post-download-transform report's own finding), the SDK quietly absorbs the resulting 404s, and
nothing about that absorption connects to why deeper raster detail is visible offline — that's §2's
ambient-cache explanation, entirely unrelated to this bug.

---

## What this actually decides, revised from the dispatch's own framing

The dispatch poses two branches: does the raster capture go through OkHttp, or not. **Neither branch
quite applies, because there's no dedicated capture to ask the question about** — but the answer falls
out anyway, and lands on the same side as the dispatch's first branch, for a cleaner reason:

**The "deep-zoom raster" a user sees offline is produced by the exact same code path the
tile-interception report already fully characterized** — the live `MapView` fetching a raster basemap
tile while online. There is no second mechanism to design a transform for. **An OkHttp interceptor
installed via `HttpRequestUtil.setOkHttpClient`, exactly as the tile-interception and night-inversion
reports already proposed, covers this "second layer" automatically, because it isn't actually a second
layer** — it's ordinary live browsing, already in scope. The cache-key differentiation fix from the
offline-interception report (append a day/night marker, strip it before the real request) applies here
unchanged too: whatever gets ambient-cached while night mode is active gets cached under the
night-differentiated key, and reused correctly later.

**What does change, and matters more than the transform-attachment question**: the durability gap in
§2 and §4. Even with the interceptor built exactly as already designed, coverage at any zoom past
z10-14 remains ambient, evictable, and dependent on this device's own browsing history — not something
"downloading a region" gives a user any guarantee about, silently. That's a real product question this
report surfaces but doesn't answer: whether the offline-maps feature should say so, whether the
region's zoom ceiling should be raised to match what ambient browsing already provides in practice, or
whether the two systems should be connected on purpose instead of coincidentally overlapping. Flagged
for the owner to decide, not resolved here.
