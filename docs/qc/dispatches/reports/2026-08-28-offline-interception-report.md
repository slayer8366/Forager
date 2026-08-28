# Report — do offline tile reads pass through anything interceptable?

**Date:** 2026-08-28. Investigation only, no app behavior changed, no transform built. Verified
against the actual MapLibre Native source at the exact commit the pinned 13.5.0 AAR was built from
(`a666f02633c1d03bd793dc214cbb2dbcacf8d74e` — confirmed two independent ways: the short hash
embedded in `HttpRequestImpl`'s own User-Agent string, and `git ls-remote`'s `android-v13.5.0` tag
resolving to that exact SHA, whose own commit message reads "Release MapLibre Android 13.5.0
(#4477)"). **Method used: reading the real C++ source at that commit, not the empirical
logging-interceptor test the dispatch offered as an alternative** — this environment has no
device or emulator (`/dev/kvm` unavailable, established repeatedly this session), so the airplane-
mode/logging-`Call.Factory` experiment isn't runnable here. Flagged, not silently substituted.

**The prior report's reasoning doesn't hold, and the real answer is more specific and more
useful than either "yes" or "no": something does try to reach the HTTP layer even for a cached
read, but what actually renders on screen for an offline read never passes through an
interceptor — and, separately, this app's specific offline path turns out not to need one at
all.**

---

## 1. Does anything reach `HttpRequestImpl`/`NativeHttpRequest` when a tile is already in a downloaded region?

**Established from source: `platform/default/src/mbgl/storage/main_resource_loader.cpp` at the
pinned commit**, the exact waterfall every resource request goes through
(`MainResourceLoaderThread::request`, lines 64–116): asset → mbtiles → pmtiles → local file →
**database (cache)** → online, in that order, short-circuiting at the first source that
`canRequest()`. Two branches inside the database step matter:

- **`Resource::LoadingMethod::CacheOnly`** (line 83): serves directly from the database, full
  stop. Nothing reaches the online/HTTP layer at all.
- **The normal loading method** (line 85, what a live `MapView` uses): queries the database first,
  and **inside that callback, at line 110, it unconditionally also calls `requestFromNetwork`** —
  even when the cached response was already usable and already delivered to the renderer (line
  93's `callback(response)` fires *before* the code falls through to the network attempt at line
  110). So yes, something does try to reach `onlineFileSource` → `HttpRequestImpl` →
  `HttpRequestUtil`'s installed `Call.Factory` for essentially every non-`CacheOnly` resource read,
  cached or not — it's a background revalidation attempt, not conditional on a cache miss.

**But this doesn't mean an interceptor's transform reaches the screen for a cached tile.** The
cached bytes are handed to the renderer immediately (line 93), before the network attempt is even
made. In genuine airplane mode that background attempt fails immediately with no route to host —
no bytes for an interceptor to transform, and even if it somehow succeeded, nothing in this code
re-renders an already-displayed tile from a second, later response. **What a user sees for a
cache-hit tile is exactly the bytes stored in the database at whatever moment they were
originally cached — never bytes an interceptor produces at read time.**

---

## 2. What layers sit between the native tile store and the renderer?

**None that transform bytes.** Read directly off the database-serve path
(`database_file_source.cpp`) and `main_resource_loader.cpp`: a cache hit returns the stored
`Response` straight to the `callback` that ultimately reaches the renderer — no transform hook
anywhere in that path.

**`FileSource.setResourceTransform`/`ResourceTransformCallback.onURL(int kind, String url):
String` does not fire for cached reads at all.** Confirmed by what it's declared to do: it's a
**pre-request URL rewrite**, invoked (per its name and single method signature) when a request is
about to be *constructed*, not when a cache entry is being *read back*. A cache hit never
constructs a new outbound request for the resource it already has, so this hook has nothing to do
in that path. It's relevant to the online/download side (item 5 below), not the offline-read side.

**Does the offline database store tile bytes verbatim?** Yes — confirmed by
`database_file_source.cpp:48-51`'s `forward()` (invoked from `main_resource_loader.cpp:49-51` the
moment `onlineFileSource`'s request succeeds): the exact `Response` object the online fetch
produced — whatever bytes an interceptor active *at that moment* returned — is what gets written
to the database. Bytes are stored as received once, not re-derived on each read.

---

## 3. If nothing is interceptable on the offline read path — the options, and a correction to the premise

**Before evaluating options: this app's specific offline path doesn't have the problem the
dispatch is asking about, for a reason underneath the caching question.** Re-confirmed this pass,
directly against current `main`: `grep` for `MapView(` across all of `app/src/main` finds exactly
one construction site, `SightingsMap.kt:213`, and it's the only concrete implementation any
`mapSlot` call — including `CentrePinLocationPicker`'s own region-download picker — ever reaches.
**No code path in this app ever points a `MapView` at `OFFLINE_STYLE_URL`.** The offline PMTiles
download is Protomaps-generated **vector** data (protobuf geometry), not rendered raster
pixels — there are no pixel bytes in an offline tile for a luminance-inversion transform to act
on in the first place. Colour, for vector tiles, comes entirely from the *style's* paint
properties applied at render time, not from anything baked into the tile.

**This means, for this app specifically**: a night variant of the offline map — if it were ever
wired up to render at all — needs a **second small vector style document** (a night-paint version
of `offline.json`, a few KB, authored once, shared by every downloaded region), not doubled tile
storage and not a per-tile transform of any kind. The entire "transform at download time and store
inverted tiles" option doesn't apply to this app's offline data. The dispatch's storage-doubling
concern (item 4) is real in general for a raster-tile-interception scheme, but doesn't attach to
this specific path.

**A real, unresolved tension with this finding, flagged rather than smoothed over**: this
dispatch's own preamble reports a hardware observation — offline tiles rendering, in airplane
mode, after the ambient cache was cleared. If "cache cleared" means MapLibre's own
`OfflineManager.clearAmbientCache()` (a real native method, confirmed present in the SDK) and
tiles still rendered afterward, that's consistent with the tiles coming from a *pinned*
`OfflineRegion` entry (which survives an ambient-cache clear by design — that's the whole
distinction between "pinned" and "evictable" cache rows) rather than merely from opportunistic
ambient caching. But per the source-level finding above, this app never renders `OFFLINE_STYLE_URL`
anywhere — so **the most likely explanation is that what was observed rendering was the live
raster basemap (Street/Topo/Satellite), ambiently cached from earlier online browsing of that
same area, not the explicit Offline Maps download feature's own vector tiles.** I can't confirm
this against real hardware from this environment (no device/emulator), so it's reported as the
best-supported reading of the evidence, not a fact — worth settling with a direct look at what the
rendered tiles actually looked like (labeled/street-styled, i.e., the live raster look, vs.
unlabeled Protomaps vector styling) next time this is checked on a device.

**Options, evaluated honestly against both possibilities:**

- If what's actually being asked about is night mode for the **live raster basemaps when their
  tiles happen to be ambient-cached** (the likelier real scenario per the tension above): this is
  the *same* question the prior tile-interception report already answered — interceptable at fetch
  time, with the cache-key fix in item 5 below making toggling safe. No new option needed.
- If the offline **vector** path is ever actually wired up to render live (it isn't today): a
  night paint variant is a style-authoring problem, solved with a second style document, not a
  transform-and-storage problem. Cheap, and this dispatch's storage-doubling concern doesn't apply.
- "Store only day tiles offline, disclose that night mode doesn't apply offline" — moot for the
  same reason: there's no rendered offline appearance today for night mode to apply *to* or be
  disclosed as absent from.

---

## 4. Storage interaction

**Could not compute an exact per-region byte figure — no downloaded region exists to measure in
this environment** (no device; nothing was downloaded this session, consistent with the "no
behaviour change" instruction). What's established from source instead:
`OfflineMapRepository.kt`'s own constants — `MIN_ZOOM = 10.0`, `MAX_ZOOM = 15.0`,
`TILE_COUNT_LIMIT = 6000L` (`OfflineMapRepository.kt:81,105-106`).

**The qualitative correction matters more than a missing number here**: per item 3, this app's
offline tiles are vector geometry rendered by a style, not rendered pixels — so "day and night
variants double the footprint" is the *raster* framing, and doesn't transfer to this specific
system. A night-capable offline vector style costs the size of one more small JSON document,
shared across every region, not a second copy of `TILE_COUNT_LIMIT` tiles per region. If the real
target turns out to be the live raster basemaps' ambient cache instead (per the tension in item 3),
that cache is MapLibre's own general-purpose store, sized and evicted by MapLibre itself
(`setMaximumAmbientCacheSize`, a real native method) rather than this app's own
`TILE_COUNT_LIMIT` — a differently-shaped storage question this report didn't measure, since it's
outside the "downloaded region" scope of the dispatch's own framing.

---

## 5. The cache key — assessing the correction

**Confirmed: yes, this works, for exactly the reason the correction proposes.** Traced the URL's
path through the decompiled `HttpRequestImpl.executeRequest` precisely: `resourceUrl =
HttpRequestUrl.buildResourceUrl(host, resourceUrl, ...)` runs **before** `getHttpClient().newCall(request)`
is ever called — meaning by the time an OkHttp interceptor attached via `setOkHttpClient` sees
anything, the `Request` already carries whatever URL the native `Resource` object was built with
(i.e., whatever `BasemapStyles.kt`'s tile-URL template produced, differentiator included). An
OkHttp application interceptor wraps `chain.proceed(request)` and can freely construct a
*different* `Request` — with the differentiator stripped from its `HttpUrl` — before calling
`proceed`, sending the clean canonical URL out over the wire while the native side, and therefore
its cache key, only ever knows the differentiated one.

Concretely: `NativeHttpRequest`'s response callback
(`onResponse(code, etag, lastModified, cacheControl, expires, retryAfter, rateLimit, body)`,
confirmed from its own `javap` signature) carries **no URL at all** back to native code — native
already has the URL it originally asked for (the differentiated one, since that's what it
constructed the `Resource`/request with) and never learns what URL the interceptor actually fetched
from. So: **native sees and caches against the differentiated URL; the tile server sees and caches
against the canonical one.** Both halves of the correction hold, confirmed independently at the
exact code boundary that matters, not assumed from the general shape of interceptors.

This also directly fixes the day/night collision the prior report flagged: `BasemapStyles.kt`
already fully owns tile-URL-template construction (`styleJsonFor`), so appending something like
`?mode=night` there when `night` is true, and having the `setOkHttpClient`-installed interceptor
strip any `mode` query parameter before `chain.proceed`, gets distinct native cache entries per
mode without adding a single extra byte of real network traffic against OSM or OpenTopoMap.

---

## What this changes about the prior report's recommendation

The tile-interception report's cache-key fix (append a differentiator, strip it before the real
request) is now **confirmed to work**, not just proposed — worth stating in the design record as
settled, not still-open. The bigger change is scope: this dispatch's premise (offline reads need
their own interception strategy) turns out not to apply to this app's actual offline system at
all, for a more fundamental reason than interceptability — there's no rendered raster appearance
offline to intercept in the first place, and no live rendering of the offline style at all today.
Whatever "tiles rendered in airplane mode" turns out to have actually been is worth pinning down
before Phase 2/3 proceed, since the two candidate explanations (ambiently-cached live raster tiles
vs. the offline vector path) lead to different scopes of work.
