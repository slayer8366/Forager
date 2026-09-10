# Report — can we intercept raster tiles?

**Date:** 2026-08-28. Investigation only, no app behavior changed, no transform built. Verified
against the actual pinned `org.maplibre.gl:android-sdk:13.5.0` AAR
(`android-sdk-13.5.0.aar`, Gradle cache SHA `81baf3f80529bc44df7aab3e4eae44d559d3575f`) —
`classes.jar` extracted and decompiled directly (CFR 0.152), not read from documentation or general
knowledge of the library, per the dispatch's own instruction.

**Short answer: yes, interceptable, through one mechanism that covers live and offline tiles
alike — but the interception point sits upstream of MapLibre's own native cache, which creates a
real cache-key collision risk between day and night variants of the same tile that Phase 3 needs
to solve, not just the transform itself.**

---

## 1. Does MapLibre Android 13.5.0 route raster tile fetches through an interceptable HTTP client?

**Yes. `org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient(okhttp3.Call.Factory)`
exists at 13.5.0** — confirmed present in `classes.jar` and decompiled in full. It is a thin public
passthrough to `HttpRequestImpl.setOkHttpClient`, which sets a static, volatile
`okhttp3.Call.Factory client` field.

**What it actually governs — traced through `HttpRequestImpl.executeRequest`, the one place any
HTTP request is made in this SDK**: every request (`getHttpClient().newCall(request).enqueue(...)`)
goes through this static field if it's been set, or a lazily-built default `OkHttpClient`
otherwise. A repo-wide search of `classes.jar` for any class referencing `okhttp3` finds exactly
three: `HttpRequestImpl`, `HttpRequestUtil`, and `HttpRequestImpl$OkHttpCallback` — **there is no
second HTTP implementation anywhere in this SDK.** Raster tiles, vector tiles, style JSON, glyphs,
sprites — everything the native core fetches over HTTP goes through this one class.

**How native code reaches this Java class**: `org.maplibre.android.http.NativeHttpRequest` is the
JNI bridge — constructed by native code with a `nativePtr`, holds an `HttpRequest` instance
(`HttpRequestImpl`), and implements `HttpResponder` itself. `HttpRequestImpl`'s OkHttp callback
delivers the fetched bytes via `httpRequest.onResponse(code, etag, lastModified, cacheControl,
expires, retryAfter, rateLimitReset, body: ByteArray)` →
`NativeHttpRequest.onResponse(...)` → the native `nativeOnResponse(...)` JNI call, handing the
byte array back into the C++ core. This is the sanctioned platform-binding pattern this SDK's
Mapbox GL Native lineage has always used — the native core is deliberately platform-agnostic and
delegates all networking to exactly this kind of callback bridge.

---

## 2. Does the offline rendering path fetch tiles through the same mechanism as live tiles?

**Yes — confirmed by bytecode, not inferred from the shared-singleton pattern alone.**
`org.maplibre.android.storage.FileSource` is a per-`Context` singleton
(`FileSource.getInstance(Context)`) that owns the SDK's native resource layer: `setApiKey`,
`setApiBaseUrl`, `setTileServerOptions`, `setResourceTransform` are all methods on it, and
`OfflineManager`'s own native `initialize(FileSource)` method takes an instance of this exact
class — the same class the live map depends on.

**Decompiled `MapLibre.getInstance(Context, String, WellKnownTileServer, RenderingEngine.Type)`**
(the SDK's own bootstrap, called by both `SightingsMap.kt` and
`MapLibreOfflineMapRepository.kt`'s `offlineManager()` before any other API use) shows it calling
`FileSource.initializeFileDirsPaths(Context)`, `FileSource.getInstance(Context)`, then
`FileSource.setTileServerOptions(...)` and `FileSource.setApiKey(...)` on the *same* returned
instance. There is exactly one `FileSource` per process, and both the live map and
`OfflineManager` are built on top of it. Since `HttpRequestImpl`/`NativeHttpRequest` is this SDK's
only HTTP bridge and `FileSource` is the only native resource singleton, **one interception point
covers both.**

---

## 3. Interceptable — the hook, the cache relationship, and the constraints

**The exact hook**: `org.maplibre.android.module.http.HttpRequestUtil.setOkHttpClient(okhttp3.Call.Factory)`.
Call it once (e.g. in `AppContainer` or wherever `MapLibre.getInstance()` is first called) with a
real `okhttp3.OkHttpClient` built with an **application interceptor** — `Call.Factory` is
satisfied by any `OkHttpClient` instance, and an interceptor can inspect a response, decode the
tile image, apply a transform, and return a new `Response` wrapping the transformed bytes as a new
`ResponseBody`. `HttpRequestImpl` never distinguishes "real" fetched bytes from
interceptor-rewritten ones — it just calls `responseBody.bytes()` on whatever `Response` comes
back and hands that byte array onward.

**The cache relationship — the real risk, and the thing this dispatch existed to find**:
`HttpRequestImpl` itself caches nothing; decompiling `OkHttpCallback.onResponse` shows it reads
`ETag`/`Last-Modified`/`Cache-Control`/`Expires`/`Retry-After`/`x-rate-limit-reset` headers and the
body, then hands all of it to native code via `onResponse(...)`. **Tile caching happens entirely on
the native side, downstream of this Java layer — which means it caches whatever bytes we return
from the interceptor, not the raw fetched bytes.** That sounds like good news (the transformed
tile gets cached, not re-transformed every time) until the cache *key* is considered:
`executeRequest`'s only URL modification is `HttpRequestUrl.buildResourceUrl(host, resourceUrl,
querySize, offlineUsage)` — a host/offline-based rewrite with **no day/night distinction anywhere**.
The tile URL a night-mode request produces is byte-identical to the day-mode request for the same
tile. **A naive interceptor transforming bytes based on a runtime "is night mode on" flag would
poison the native cache**: whichever mode fetched a given tile first gets cached under that tile's
URL, and the *other* mode would be served the wrong variant from cache until that entry is evicted
or revalidated — not merely "no cache benefit," but visibly wrong tiles. This is the load-bearing
finding for Phase 3: **the tile URL itself needs a day/night-distinguishing element** (e.g. a
synthetic query parameter appended in `BasemapStyles.kt`'s own `styleJsonFor`, which already fully
controls the URL template) so the native cache naturally keys the two variants apart. Without that,
toggling Night Maps doesn't double the cache footprint — it corrupts it.

**Threading and other constraints**: requests are dispatched asynchronously
(`call.enqueue(callback)`, OkHttp's own `Dispatcher`, up to 20 concurrent requests per host on API
21+) — the interceptor and the byte-transform work it does run off the main thread, on OkHttp's
dispatcher pool, already in a context expecting blocking I/O (`responseBody.bytes()` itself
blocks). A per-tile decode/transform/re-encode (256×256, the same scale Phase 1's measurement
script processed in well under a second per tile in pure Python) is well within what this thread
pool is built for. **One real constraint an interceptor must respect**: it must preserve the
`ETag`/`Last-Modified`/`Cache-Control`/`Expires` headers on the response it returns — native code
reads these directly for its own cache-revalidation logic, and a transform that drops or alters
them would break that, independent of the URL-keying issue above. No unusual timeout was found in
this code path beyond OkHttp's own defaults.

---

## Not verified this pass

- Whether MapLibre's *native* (C++) tile cache is itself keyed purely by URL, or incorporates
  anything else (e.g. a request header) that could serve as an alternative day/night
  differentiator to a URL change. The Java-side evidence above is conclusive that the URL is
  identical between modes and that native code receives no day/night signal in this call at all,
  but the native cache's own key construction lives outside `classes.jar` (compiled into the
  bundled `.so`) and wasn't decompiled this pass.
- Whether `FileSource.setResourceTransform`'s `ResourceTransformCallback.onURL(int kind, String
  url): String` (a real, separate hook this pass found — rewrites request URLs *before* fetch,
  keyed by `Resource.{TILE,STYLE,GLYPHS,SPRITE_IMAGE,SPRITE_JSON,SOURCE}`) could itself be used
  to inject the day/night URL differentiator this report recommends, as an alternative to changing
  `BasemapStyles.kt`'s own template construction. Noted as a real, existing hook worth Phase 3
  evaluating, not chosen over the other option here — this dispatch didn't ask for a
  recommendation between them, only whether interception is possible at all.
