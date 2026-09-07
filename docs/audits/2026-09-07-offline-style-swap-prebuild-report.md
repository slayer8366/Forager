# Pre-build report: Stage 2e-ii, make the offline map actually render offline

**Type:** report-before-building, per the dispatch's own instruction on every item. **Nothing built: no product code, no tests, no dependency, no gradle change.**
**Date:** 2026-09-07. **Dispatch:** `dispatch-offline-style-swap.md` with its amendment `amendment-offline-render-confirmed.md` (two identical copies were supplied; treated as one).
**Base:** `main` at `0ca2f55fe4f49a33aee296d95ee3004ebde231ca`, re-fetched at the start of this report; it has not moved since the plate pulse ran on it earlier today. **Branch:** `claude/new-session-b7z9bg` already contains `main` (`git merge-base --is-ancestor` confirms), so there was nothing to rebase onto; **continued on the branch**, on top of its four existing commits (the plate pulse, its addendum, the five corrections, their completion report). Not branched fresh, because the dispatch offered either and the branch carries no divergence from `main`'s history.

Every claim names a file and location on `0ca2f55`, or is marked inferred. Where the dispatch and its amendment disagree with the code, the code is quoted and the disagreement stated.

---

## The one thing to read first

**On `main`, no map in this app can render from the downloaded region store, because no map ever loads the style the regions were downloaded against.** Every live map loads a raster style built in-app from public tile servers; the region store holds vector tiles from this project's own worker. The two never share a URL.

- The only `Style.Builder()` in `app/src/main` is `SightingsMap.kt:403`:
  ```kotlin
  map.setStyle(Style.Builder().fromJson(styleJsonFor(basemap, night = nightMode))) { style ->
  ```
  and `styleJsonFor` (`BasemapStyles.kt:73-90`) emits one `"raster"` source whose `tiles` are `basemap.tileUrlTemplate` — `a.tile.opentopomap.org`, `tile.openstreetmap.org`, `basemap.nationalmap.gov` (`Basemap.kt:142-169`).
- `OFFLINE_STYLE_URL` is referenced from exactly one executable site, the download definition (`MapLibreOfflineMapRepository.kt:98`). Its two other mentions under `ui/` are comments (`MapSlot.kt:73`, `AvailabilityOfflineMapsUi.kt:15`). `git log --all -S"fromUri("` over `app/src/main` finds no commit, ever.
- `MapRenderMode.useOfflineTiles` is "read by nothing yet" (`MapSlot.kt:66-85`).

**So the amendment's finding — "a downloaded region serves a live map render, not from the ambient cache" — cannot be what happened on a build of this code.** What rendered after "cache cleared, airplane mode, map opened over a downloaded region" was a **raster** basemap, and raster tiles reach a device only two ways: the network, or MapLibre's ambient cache. The region store cannot have served them; it contains no raster tile from any of those servers. §1.3 below explains why the clear did not remove the ambient tiles either. This is said plainly because the dispatch asked for exactly that: *"if the downloaded region cannot serve a live style load in this app's shape, say so before building"* — and *"a pass that proves nothing is worse than no pass, because it would close the question falsely."*

The store may well work. Nothing here says it does not. What this report says is that **the question the dispatch's Item 1 points 3 and 4 ask is still open**, the amendment's device pass did not close it, and the reason is structural, not a matter of which button was pressed. The corrected device procedure in §5 closes it properly, and it needs the swap built first — which makes this dispatch's build the precondition for its own gating question, a shape the dispatch should know it has.

---

## 1. Item 1 — can this work at all

### 1.1 `OFFLINE_STYLE_URL` exists; what references it; what serves it

`MapLibreOfflineMapRepository.kt:240`:

```kotlin
private const val OFFLINE_STYLE_URL = "https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json"
```

`private` to that file. Every reference in the repository:

| Where | Kind |
|---|---|
| `MapLibreOfflineMapRepository.kt:98` — first argument to `OfflineTilePyramidRegionDefinition(...)` in `download` | **the only executable use** |
| `MapLibreOfflineMapRepository.kt:53, 67` and the constant's own doc comment `:231-239` | comments: why glyph-stripped, why a real URL rather than `asset://` |
| `MapSlot.kt:73` — `useOfflineTiles`'s doc comment | comment: "actually swapping to the offline vector style, `OFFLINE_STYLE_URL`, is Stage 2e-ii" |
| `AvailabilityOfflineMapsUi.kt:15` — file header | comment: the picker's basemap pin "is the exact line 2e-ii touches" |

**What serves it today:** the Cloudflare Worker in this repo, `server/pmtiles-worker/src/index.ts:254-255`:

```ts
    if (url.pathname === "/style/offline.json") {
      return offlineStyleResponse(resolveAllowedOrigin(request, env));
```

which returns `server/pmtiles-worker/src/offline-style.json` verbatim (`:23`, `:111-117`) with `"Cache-Control": "public, max-age=86400"` (`:114`). That JSON is: `version`, one source, 57 layers; **no `glyphs`, no `sprite`, no `text-field` anywhere** (checked by parsing the file). The source:

```json
"protomaps": {
  "type": "vector",
  "attribution": "<a href=\"https://github.com/protomaps/basemaps\">Protomaps</a> © <a href=\"https://osm.org/copyright\">OpenStreetMap</a>",
  "url": "https://forager-pmtiles.brandonlee1-894.workers.dev/us.json"
}
```

So a load of `OFFLINE_STYLE_URL` pulls **three kinds of resource**: the style document, the TileJSON at `/us.json` (which carries the tile URL template and `maxzoom`, served with `max-age=${BUILD_RESOLUTION_CACHE_SECONDS}`, `index.ts:182`), and the tiles the template names (`Cache-Control: env.CACHE_CONTROL || "public, max-age=86400"`, `:285`). Whether the worker is *currently* up was not checked from this sandbox (outbound HTTPS is proxied here and a probe would prove nothing about the owner's device); the owner verified `us.json` and a zoom-15 tile on 2026-09-06 (`OfflineMapRepository.kt`, `SERVED_MAX_ZOOM`'s doc comment).

### 1.2 What the download writes, and where

`MapLibreOfflineMapRepository.download` (`:92-143`), the parts that decide what lands on disk:

```kotlin
        val definition = OfflineTilePyramidRegionDefinition(
            OFFLINE_STYLE_URL,
            region.toLatLngBounds(),
            OfflineMapRepository.MIN_ZOOM,
            OfflineMapRepository.MAX_ZOOM,
            appContext.resources.displayMetrics.density,
        )
        …
        val offlineRegion = offlineManager().createOfflineRegionSuspend(definition, placeholderMetadata)
        val finalStatus = try {
            offlineRegion.downloadToCompletionSuspend(onProgress)
        } catch (e: Exception) {
            // Never leave a half-downloaded region looking like a complete one.
            offlineRegion.deleteSuspend()
            throw e
        }
        …
        offlineRegion.updateMetadataSuspend(RegionMetadata(name, region, OfflineMapRepository.MIN_ZOOM, OfflineMapRepository.MAX_ZOOM, downloadedAt).toBytes())
        offlineRegionDao.upsert(OfflineRegionEntity(id = offlineRegion.id, name = name, lat = region.lat, lng = region.lng, radiusKm = region.radiusKm, minZoom = …, maxZoom = …, createdAtEpochMillis = downloadedAt))
```

`MIN_ZOOM = 10.0`, `MAX_ZOOM = 15.0` (`OfflineMapRepository.kt`). Two things are written:

1. **MapLibre's own offline database**, via `OfflineManager` — a single SQLite file holding the style document, the TileJSON, and every tile in the z10–15 pyramid over the region's bounding box, plus the region's metadata blob. The schema was read from the pinned SDK's own source by the 2026-08-28 post-download-transform report: `tiles` (bytes, one row per `(url_template, pixel_ratio, z, x, y)`, zlib per row), `resources`, `regions`, `region_tiles`, `region_resources` (that report, §1).
2. **A Room row** in `offline_regions` — the index `OfflineManager`'s opaque list cannot provide (`:34-38`), recoverable from the metadata blob if the row is lost (`:179-195`).

**Where:** `MapLibreStorage.ensureMapLibreStorageOutsideCache` (`MapLibreStorage.kt:53-58`) redirects the whole `FileSource` database before MapLibre's first use:

```kotlin
    val offlineStorageDir = File(appContext.filesDir, "maplibre-offline").apply { mkdirs() }
    FileSource.setResourcesCachePath(appContext, offlineStorageDir.absolutePath, …)
```

That is `filesDir`, **not `cacheDir`** — by design, so neither the OS under storage pressure nor this app's own "Clear cache" control can remove a region (`:10-22`). Landed 2026-09-03 (`1f47ee0`), and its doc comment says a build predating it would still have the database under the old cache path, with no migration (`:49-51`).

### 1.3 The region store and the ambient cache: one database

**The same store.** The repo's own report, from the SDK's schema (`2026-08-28-post-download-transform-report.md:93-97`):

> `tiles` holds bytes shared by every region (and the ambient cache) that happens to reference the same `(url_template, z, x, y)`; `region_tiles` is only the linking row. A tile with no `region_tiles` row is ambient cache; the same physical row can be linked to more than one region if two downloads overlap.

And the raster-capture-path report (`:118-122`) on what browsing writes there: "The raster ambient cache: unbounded in principle, evictable, present **only** at whatever `(url_template, z, x, y)` combinations this specific device happened to fetch while online". `OfflineManager` in the pinned SDK exposes `clearAmbientCache(FileSourceCallback)`, `invalidateAmbientCache(…)`, `setMaximumAmbientCacheSize(long, …)` and `resetDatabase(…)` (`javap` against `android-sdk-13.5.0.aar`'s `classes.jar`); **the app calls none of them**, and has no UI or debug hook that does.

**Consequence for the amendment's "which button" question, answerable from the code as the amendment asked:**

- **"Clear cache"** removes `cacheDir`. On any build since `1f47ee0`, MapLibre's database — regions *and* ambient tiles, one file — is under `filesDir/maplibre-offline`, so **Clear cache touches neither**. The ambient raster tiles survived that press. On a build *before* `1f47ee0`, the database was under the cache path and Clear cache removed regions and ambient tiles together — and then nothing at all could have rendered offline.
- **"Clear storage"** wipes the app's data directory: the MapLibre database, the Room database, everything. Regions gone. A map rendered afterwards offline only if a region was re-downloaded first — while online — and the region picker that does so shows `Basemap.OPEN_TOPO_MAP` (`AvailabilityOfflineMapsUi.kt:158`), a raster basemap that writes ambient raster tiles for the very area being browsed.

Either way the render is explained by ambient raster tiles, and §"The one thing to read first" explains why the region store could not have contributed on this code. **The reasoning in the amendment does not hold either way**, because it assumes the map loads the offline style. The owner does not need to repeat the walk yet: repeating it on `main` would produce the same ambiguous result, for the same reason. The repeat is the device pass in §5, after the swap exists.

(Android's Clear cache/Clear storage semantics are general platform knowledge, not something in this repo; the `filesDir`/`cacheDir` split is the repo's.)

### 1.4 Does a style loaded by URL consult the downloaded region?

**Nothing in this repo settles it by demonstration**, for the reason above. What the repo does establish from the SDK's source, in the 2026-08-28 reports: the renderer's tile lookup reads the `tiles` table keyed by `(url_template, pixel_ratio, z, x, y)` regardless of region membership — region membership lives in `region_tiles` and governs eviction, not lookup (`raster-capture-path-report.md:55-61`: "the renderer's tile lookup reads `tiles`… No region association is ever created for these rows… They are, definitionally, ambient cache"). That is the mechanism by which ambient raster tiles render offline today, and it is the same mechanism a region's vector tiles would render by: **a request for a resource whose URL matches a stored row is served from the row.** So the answer expected from the SDK's design is "yes, provided the URLs match" — and that is exactly what §1.5 is about. Marked **inferred from the SDK's schema and the repo's reading of `DatabaseFileSource`**, not demonstrated; it becomes demonstrated only by the device pass in §5.

One more SDK fact bears on it: `MapLibre.setConnected(java.lang.Boolean)` / `isConnected()` exist in the pinned SDK (`javap`, `org.maplibre.android.MapLibre`). `setConnected(false)` tells the native file source to make no network requests — a way to force the offline path in-app without airplane mode, useful for the device pass and possibly for the product (§3). The app does not call it.

### 1.5 Which style the regions were downloaded against, and whether the map would load it

**Downloaded against:** `OFFLINE_STYLE_URL`, exactly, in every download since the class took its current shape (`:98`). Through it: `/us.json` and the tile template `us.json` names.

**What the map would load:** today, never that (§"The one thing to read first"). After 2e-ii, it must load **the same string** — `Style.Builder().fromUri(OFFLINE_STYLE_URL)` (`fromUri`/`fromUrl` both exist on `Style.Builder`, `javap`) — not a copy of the style's JSON via `fromJson`. The offline database keys the style document by its URL; a `fromJson` load never asks for that resource, so the stored style document is unused, and while the TileJSON and tiles would still be found by their own URLs, the app would be rendering a copied style that can drift from the one the download stored. The constant is `private` to the repository file; 2e-ii has to expose it (or the URL's home moves), which the dispatch's out-of-scope list ("changing the download… or the worker") does not forbid but which is a decision to state.

**Three ways the match can still fail, all URL-level, none visible in code review:**

1. **The worker's tile template or `/us.json` path changes.** Every existing region then holds tiles under the old template string; a fresh style load names the new one; every request misses. `OfflineTilePyramidRegionDefinition` stores the template it was given at download time, so this is silent and total. Nothing in the repo pins the template across worker deploys.
2. **The TileJSON changes under a stored region.** The worker's `us.json` went from `maxzoom` 14 to 15 on 2026-09-06 (`OfflineMapRepository.kt`, `SERVED_MAX_ZOOM`'s doc comment). A region downloaded before that stored a `us.json` saying 14 and tiles to z14; one downloaded after stores 15 and tiles to z15. Offline, MapLibre uses whichever `us.json` it stored. Online, `max-age` expiry lets it refetch the newer one and then request z15 tiles the store lacks — served from the network while online, missing later when offline. **Inferred** from `Cache-Control` and MapLibre's revalidation behaviour; what MapLibre draws for a missing child tile (overzoomed parent, or nothing) is a device question.
3. **Two regions downloaded against different worker states** hold two `us.json` rows under one URL? No — one URL, one `resources` row; the later download overwrites it. Which region's zoom ceiling wins is then whichever downloaded last. Not a correctness failure, a surprise.

None of these is a reason not to build. They are the reasons the device pass must include a region downloaded *before* any worker change the owner makes next.

### 1.6 `initializeMapLibre` — every path goes through it

Confirmed by grep over `app/src/main`: the only `MapLibre.getInstance` call is inside `initializeMapLibre` itself (`MapLibreStorage.kt:93-96`); the only `OfflineManager.getInstance` is `MapLibreOfflineMapRepository.kt:221`, one line after `initializeMapLibre(appContext)` (`:220`); `SightingsMap.kt:222` calls `initializeMapLibre(context)` inside its `remember` before constructing the `MapView` (`:217-218` records the old bug: "this used to call MapLibre.getInstance(context) directly, missing that half"). No `FileSource.getInstance` anywhere. Two entry points, both through the initialiser; `MapLibreStorage.kt:75-91` records that a third one once named there never existed. **The swap adds no entry point** — it changes what the already-initialised `MapView` loads.

---

## 2. Item 2 — the style-reload hazard

### 2.1 Every source, layer and image, and where each is added

All of them in **one function, `initializeOverlayLayers(style, density, palette)`** (`SightingsMap.kt:539-663`), which is called from **one place: inside the `setStyle` callback** (`:403-404`). The inventory, in add order:

| Source | Layer(s) | Image | Line |
|---|---|---|---|
| — | — | `PLANNED_TRIP_ICON_ID` | `:540` |
| `OFFLINE_REGION_CIRCLE_SOURCE_ID` | fill `:545`, outline `:551` | — | `:544` |
| `SEARCH_CENTER_SOURCE_ID` | circle `:559` | — | `:558` |
| `SIGHTING_SOURCE_ID` | circle `:569` | — | `:568` |
| `BREADCRUMB_SOURCE_ID` | dashed line `:592` | — | `:591` |
| `KEPT_TRACKS_SOURCE_ID` | solid line `:608` | — | `:607` |
| `PLANNED_TRIP_SOURCE_ID` | symbol `:618` | — | `:617` |
| `WAYPOINT_SOURCE_ID` | symbol `:630` | `WAYPOINT_ICON_ID` `:628` | `:629` |
| `FIND_SOURCE_ID` | symbol `:647` | `FIND_ICON_ID` `:645` | `:646` |
| `PHOTO_SOURCE_ID` | symbol `:657` | `PHOTO_ICON_ID` `:655` | `:656` |

Every `style.addSource`/`addLayer`/`addImage` in the file is inside that function (grep; no `addLayerBelow`/`addLayerAbove` anywhere). Not in the style at all, and therefore not at risk from a reload:

- **The live-position puck**: `LocationComponent`, activated per style in the same callback (`:411`: `if (trackLiveLocation) activateLiveLocationIfPermitted(map, style, context, restoreCameraMode = previousCameraMode)`), with the comment that "setStyle discards the previous style's LocationComponent state the same way it does this composable's own layers" (`:405-410`).
- **The attribution caption**: a Compose `Text` over the `AndroidView`, `text = basemap.attribution` (`:505-514`), independent of the style. MapLibre's own tap-to-reveal control reads the style's source `attribution` field (`:499-503`).
- **The icon bar, compass strip, pickers**: Compose, in `MapChrome`/the screens.

### 2.2 The existing code already handles a style change — every basemap swap is one

The hazard the dispatch describes is the one this file was built against. The basemap swap **is** a live `setStyle` (`:379-412`):

```kotlin
    LaunchedEffect(mapLibreMap, basemap, mapPalette) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (appliedBasemap == basemap && appliedPalette == mapPalette) return@LaunchedEffect
        …
        val previousCameraMode = if (map.locationComponent.isLocationComponentActivated) { map.locationComponent.cameraMode } else { null }
        map.setMaxZoomPreference(basemap.maxZoom.toDouble())
        map.setStyle(Style.Builder().fromJson(styleJsonFor(basemap, night = nightMode))) { style ->
            initializeOverlayLayers(style, density = …, palette = mapPalette)
            appliedBasemap = basemap
            appliedPalette = mapPalette
            loadedStyle = style
            if (trackLiveLocation) activateLiveLocationIfPermitted(map, style, context, restoreCameraMode = previousCameraMode)
        }
    }
```

and the data refresh is keyed on `loadedStyle` precisely so it re-pushes every source after a swap (`:425-433`; its comment: "loadedStyle changing is what makes this re-populate a freshly blank style after a basemap swap"). Night mode goes through the same path (`:393-395`). So the answer to "has it only ever loaded once" is **no**: there is no separate first-load path; the first load and every swap are the same effect. An offline style is a fourth thing that effect can load.

**What survives a reload, and by what mechanism:**

| Thing | Across a reload | Why |
|---|---|---|
| Camera position | **preserved, and not re-fitted** | `setStyle` does not move the camera; the refresh effect's `shouldMoveCameraToTarget(isGpsTracking, target, lastAppliedCameraTarget)` (`:711-715`) moves it only when `region`/`focusOverride` changed, not because the style did — a real hardware fix (`:700-710`). This is also what satisfies the standing rule "nothing may animate, re-measure, or re-fit the map." |
| Puck camera mode | **restored as it was** | `previousCameraMode` captured before `setStyle`, restored after (`:387-398`), never re-forced to `TRACKING`. |
| `MapRenderMode`, `MapOverlayContent` | **untouched** | Compose state above the `AndroidView`; the refresh effect re-reads them into the new style's sources. |
| Max zoom preference | **re-set per basemap** | `:402`. The offline style has no `Basemap` entry, so it needs its own value — a decision (§3). |
| Attribution caption | **unchanged text** | reads `basemap.attribution`; the offline style has no `Basemap`, so the caption would keep showing the *online* basemap's credit over offline tiles unless 2e-ii gives it a value. Reported under attribution below. |

**One real gap for the swap, quoted so it is not missed:** the early return `if (appliedBasemap == basemap && appliedPalette == mapPalette) return@LaunchedEffect` (`:381`) keys the swap on basemap and palette only. An offline flag that changes without `basemap` changing would not reach `setStyle`. Whatever selects the style has to be in that effect's keys and that guard — a mechanical change, but the one place a "toggle does nothing" bug would come from.

### 2.3 Can the swap be avoided by choosing the style at first load? The trade-off, not a pick

- **Live swap (extend the existing effect).** The style decision becomes an input to the effect above alongside `basemap`/`night`. Cost: none new — the re-add machinery exists and is exercised on every basemap change already. Risk: the same one every basemap swap has, that something added outside the callback vanishes; §2.1 shows nothing is. Behaviour: the report screen's existing toggle (`CartographyEntryReportScreen.kt:452`, `:417`) flips the map in place; a Maps-tab control could do the same.
- **Choose at first load only (per `MapView` lifetime).** There is no first-load path to hook — the decision would be made once when the `MapView` is created (`SightingsMap.kt:215`, the keyless `remember`) and held. Cost: the report screen's toggle, which the user flips while the map is live, could not act until the map is recreated — i.e. leaving and re-opening the entry; the Maps tab's `MapView` lives for the tab's lifetime. Benefit: no reload ever happens for offline, so the reload hazard is avoided by construction rather than by the callback discipline; but that discipline is still needed for the basemap swap, so nothing is actually removed.
- **A middle: swap only on an explicit user act**, never automatically. This is really Item 3's question (§3), but it bears here: an automatic swap on connectivity loss would fire while the user is mid-pan in the woods, and a reload — even one that re-adds everything — blanks the map for the duration of the style fetch from the store. The dispatch's "a map that loses the user's track when it goes offline is worse than one that never went offline" is exactly the case an automatic mid-session swap risks even when every layer comes back.

**Not picked.** Both are buildable; the first is the smaller change and the second removes a class of failure the file already guards against.

---

## 3. Item 3 — when does it swap: what the code makes easy and hard

**Easy, because it exists:**

- **Manual, on the entry report screen.** `useOfflineTiles` is real state with a switch and a fullscreen icon-bar row driving it (`CartographyEntryReportScreen.kt:237`, `:416-417`, `:452`), threaded into `MapRenderMode` (`:375-381`), forwarded by `SightingsMapSlot`. The swap is one consumer away.
- **"A covering region exists" for an entry.** `GetCartographyEntryOfflineRegionUseCase` (kept regions only, any one drawable point inside the tile box at zoom 15); the toggle already appears only when it resolves.
- **"A covering region exists" for a day's records.** `GetTripReportOfflineRegionsUseCase` (every region on the device, any one point).

**Hard, because it does not exist:**

- **Automatic on losing connectivity.** `grep` for `ConnectivityManager`, `NetworkCallback`, `NetworkCapabilities` over `app/src/main`: **zero hits.** The app has no connectivity monitor of any kind. Building one is new infrastructure with its own lifecycle questions (a callback registered where, surviving what), and the swap it would trigger is the mid-session reload §2.3 flags. `MapLibre.isConnected()` (`javap`) reports what MapLibre *thinks*, which by default mirrors the platform's connectivity broadcast — a possible signal source, unverified here.
- **"A covering region exists" for the live map's camera.** Nothing computes "is the region under the camera covered"; both existing checks are about a point set (an entry's, a day's), not a viewport. The Maps tab has no toggle, no offline state, and no place the existing checks apply to.
- **Telling the user which style they are looking at.** Today the only always-visible signal is the attribution caption, and it reads `basemap.attribution` (§2.2). The offline style's own attribution is `Protomaps © OpenStreetMap` (as HTML links in the source's `attribution` field, §1.1); the caption is plain text, so 2e-ii needs a plain-text form — wording is the owner's. `OFFLINE_TOGGLE_CAPTION` already tells the user what they will see: `"Offline maps show shapes only — no place names, road names, or icons."` (`CartographyEntryReportScreen.kt:566`), and the basemap picker is hidden while the toggle is on (`:406`, `:194-196`).
- **Night mode on the offline style.** `NIGHT_RASTER_PAINT` is a raster paint block on the raster layer (`BasemapStyles.kt:67`, `:86`); the offline style has 57 vector layers and no raster layer. Night mode would have **no effect** on the offline style unless 2e-ii gives it one. Decision, not made here.
- **Max zoom.** `map.setMaxZoomPreference(basemap.maxZoom)` per basemap (`SightingsMap.kt:402`); the region's data stops at 15 and "vector tiles overzoom cleanly" (`OfflineMapRepository.kt`, `MAX_ZOOM`'s doc). What ceiling the offline style gets is a decision.
- **The region picker's own map** pins `Basemap.OPEN_TOPO_MAP` (`AvailabilityOfflineMapsUi.kt:158`); its header (`:14-18`) records that as the line 2e-ii touches if the picker should show the offline style. Note the picker is where the user *chooses* an area to download — showing it the offline style would show them nothing for an area they have not downloaded yet, which is the point at which they are using it. Decision.
- **Leaving a covered region mid-session.** With the swap on, the store has no tiles outside the region's z10–15 box; what MapLibre draws there — the style's `background` layer colour, presumably, since the offline style's first layer is `background` (`offline-style.json`) — is a device question. No code anywhere detects the camera leaving coverage.

**The coverage predicate, restated as the dispatch requires:** "covering" is `points.any { isCoordinateWithinRegionTiles(point, region, 15) }` (`GetCartographyEntryOfflineRegionUseCase.kt:42`, `GetTripReportOfflineRegionsUseCase.kt:28`). A region covering the trailhead and not the wood counts as covering the day. **Partial coverage is treated as coverage everywhere the check is used**, and nothing in 2e-ii should present it otherwise without saying so; a per-point or per-viewport check would be new code, and a decision.

**Stopped here, per the dispatch.** Automatic/manual/always, the indicator, the mid-session case and the picker are the owner's.

---

## 4. Attribution, the standing rule

- **Online:** the Compose caption shows `Basemap.attribution` — `"© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)"` and siblings (`Basemap.kt`); MapLibre's tap-to-reveal control shows the raster source's `attribution` from `styleJsonFor`. Both real, in separate corners (`SightingsMap.kt:360-368`).
- **Offline style:** the source's `attribution` is `<a href="https://github.com/protomaps/basemaps">Protomaps</a> © <a href="https://osm.org/copyright">OpenStreetMap</a>` — MapLibre's control will render that; the always-visible caption has no value for it today and would show the wrong credit. **The caption must change with the style**, and its plain-text wording is an owner decision: the pattern the OpenTopoMap entry set (shortened to fit one line, every required credit still named, `Basemap.kt:156-161`) is the precedent.

---

## 5. Verification: what can be tested headless, and the device pass that cannot be faked

### 5.1 Headless

- **Style selection** as a pure function of (`basemap`, `night`, `useOfflineTiles`) → which `Style.Builder` input — testable the way `styleJsonFor` is (`BasemapStyleTest`), if 2e-ii extracts it.
- **The swap guard** (`appliedBasemap == basemap && …`) as a pure predicate — testable the way `shouldMoveCameraToTarget` is.
- **The coverage predicate** — already tested (`GetCartographyEntryOfflineRegionUseCaseTest`, 7 cases).
- **The re-add list** — `initializeOverlayLayers` cannot run headless (native `GeoJsonSource`/`Layer` constructors, `SightingsMapOverlayDataTest`'s doc comment). What *can* be asserted is the feature collections each source receives (`SightingsMapOverlayDataTest`, 26 cases today). That every source is re-added on a reload is not testable headless; it is testable only on a device, and it is the second case below.
- **No test can show a tile came from the store.**

### 5.2 The device pass, designed so the ambient cache cannot fake it

The structural fact that makes a clean design possible: **the ambient cache and the region store are one database, and raster tiles cannot serve a vector style.** So the only contamination that matters is *vector* tiles from the worker reaching the ambient cache — which happens only if the offline style is ever loaded **while online**. Keep that from happening and the ambient cache is irrelevant to the test, whatever raster tiles it holds.

**Cold state, and which button:** *Clear storage* (not Clear cache — §1.3: Clear cache does not touch `filesDir/maplibre-offline` on any build since 2026-09-03). This wipes regions, Room, everything. Then, **online**, download one region through the picker (the picker's OpenTopoMap raster tiles will land in the ambient cache; they are harmless to a vector render). **Do not open any map with the offline toggle on while online.** Then airplane mode. In this state the only vector tiles on the device are the region's, and the only way the offline style can draw is from the store.

1. **Renders from the region.** Airplane mode, offline style on, camera inside the region: a map appears, "shapes only". Any map at all here is proof — nothing else could have supplied vector tiles. If it is blank: the store does not serve the load in this app's shape, and §1.4 resolves the other way. Record which build (`versionCode` is the commit count, `app/build.gradle.kts:53`) and which region.
2. **Every overlay survives the swap.** Online first: on the Maps tab with a track recording, waypoints, an entry map with finds and photos; then toggle offline on and off; then the same after airplane mode. Each of the ten sources in §2.1 and the puck must be present after each swap. Also the camera: it must not move on the swap (standing rule), which is the `shouldMoveCameraToTarget` guard on a device.
3. **The edge, and beyond.** Pan from inside the region across its edge. Record what draws beyond — background colour, nothing, or overzoomed parents — and whether anything tells the user they left coverage. Then zoom past 15 inside the region: overzoom should stay sharp; record.
4. **No region, offline, cold.** Clear storage again, no download, airplane mode, offline style on. **This must not be a silent blank.** Today nothing in the code says anything in this state — the report screen simply hides the toggle when no kept region covers the entry, and the Maps tab has no offline state at all. What the user sees, and what the app should say, is a decision the dispatch lists; the pass records the current behaviour.

Two in-app aids exist in the SDK and not in the app: `MapLibre.setConnected(false)` (force MapLibre offline without airplane mode) and `OfflineManager.clearAmbientCache` (drop ambient rows, keep regions). Either would make the pass repeatable without Clear storage; both are new code and a debug surface, so **not proposed as part of 2e-ii unless the owner wants them** — the Clear-storage procedure needs neither.

**On the amendment's line for the record:** it asked that the owner's finding be put in the audit trail with its method. It is here, in §"The one thing to read first" and §1.3, with the method and with what the method could and could not have shown on this code. The first evidence that the offline map works end to end is still to come, and the pass above is how to get it.

---

## Test and skip counts

Not run for this report — nothing changed. The dispatch's baseline "1216 tests, 24 skipped — confirm against current `main`" is **this branch's** count (1201 on `0ca2f55` plus the 15 tests the plate-corrections dispatch added); `main` itself is 1201 / 24 (the pulse's own run on `0ca2f55`). Both have the same 24 skips, byte-identical to the CI allowlist. Nothing here touches either.

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed from the code on `0ca2f55`:** the single `Style.Builder().fromJson` site; every basemap being raster with public tile URLs; `OFFLINE_STYLE_URL`'s value, its one executable use and its `private` visibility; the worker route, the style's contents (no glyphs/sprite/text), its source URL and attribution, and the three `Cache-Control` values; the download definition and the Room row; the `filesDir/maplibre-offline` redirect and its date; the two `initializeMapLibre` call sites and the absence of any bare `getInstance`; every `addSource`/`addLayer`/`addImage` living in `initializeOverlayLayers`, called only from the `setStyle` callback; the puck's re-activation there; the swap guard's keys; the camera guard; the attribution caption reading `basemap.attribution`; zero connectivity-monitoring code; the picker's basemap pin; the coverage predicate's `any`. **Confirmed from the pinned artifact by `javap`:** `OfflineManager.clearAmbientCache/invalidateAmbientCache/resetDatabase/setMaximumAmbientCacheSize`, `MapLibre.setConnected/isConnected`, `Style.Builder.fromUri/fromUrl/fromJson`. **Confirmed from git history:** no commit under `app/src/main` ever contained `fromUri(`, and `OFFLINE_STYLE_URL`'s only appearances under `ui/` are comments.

**Inferred:** that the offline database serves any URL-matched request regardless of region membership (from the SDK schema as the 2026-08-28 reports read it, not demonstrated); MapLibre's revalidation of expired resources when online; what MapLibre draws for a missing tile or outside a region; Android's Clear cache vs Clear storage semantics (platform knowledge); that `MapLibre.isConnected` mirrors platform connectivity by default.

### What I could not determine

- Whether the owner's device build is `main` or a branch that does load the offline style — the amendment's finding is consistent with the code only if it is not `main`. **This is the one question to answer before anything else**, and only the owner can.
- Which button the owner pressed. The code shows it does not change the conclusion on `main`, but §1.3 gives the answer for each.
- Whether the worker is up right now, and whether `us.json`'s tile template has changed since any existing region was downloaded (§1.5, failure 1).
- What the map draws outside a region and above zoom 15 — device only.
- Whether the region-store lookup actually serves a live load in this app's shape (§1.4) — device only, after the swap exists.

### Premises in this dispatch and amendment that were wrong

- **Amendment: "a downloaded region serves a live map render — not from the ambient cache, but from the true offline copy."** Not possible on this code: no map loads the offline style, and the store holds no raster tile. The render came from the ambient cache (or the network), whichever button was pressed (§"The one thing to read first", §1.3).
- **Amendment: "Item 1 points 3 and 4 are answered."** Point 3 (same store or different) is answered by the code — one database — and that answer is the opposite of the amendment's "distinct in effect". Point 4 is not answered; the device pass in §5.2 is what answers it.
- **Amendment: "Clear cache removes the cache directory. If the ambient tiles live there and the regions live elsewhere, the regions survived and the test reads exactly as described."** On any build since 2026-09-03 neither lives there; both live in `filesDir`, together, by this project's own deliberate redirect (§1.2, §1.3).
- **Dispatch: "Baseline: 1216 tests, 24 skipped — confirm against current `main`."** `main` is 1201; 1216 is this branch.
- **Dispatch: "`MapSlot.kt` carries a deliberately inert offline-region parameter."** It is a boolean on `MapRenderMode` (`useOfflineTiles`), not a region parameter; the region is resolved by the report screen and never reaches the map. Minor, but it matters for what "the swap" has to read.
- **Dispatch, Item 2: "if any piece is added outside that callback… it will silently vanish."** True as a hazard; on this code nothing is, and the basemap swap has exercised the callback path on hardware since the MapLibre migration (§2.1, §2.2). The hazard is real; the exposure on this file is smaller than the dispatch assumes.

### Anything I decided that this dispatch did not cover

- I did not rebase, because there was nothing to rebase onto; said so at the top.
- I read `AvailabilityOfflineMapsUi.kt` (its header and the picker's basemap pin) and `AvailabilityScreen.kt`'s `MapRenderMode` construction site; the dispatch holds seams F and G of that file, and I made no claim about them.
- I treated the amendment's request to "put it in the audit trail" as satisfied by recording the finding *with the code's account of what it could have shown*, rather than recording it as established. Recording it as established would have closed the question falsely, which the dispatch's own last paragraph forbids.
- I did not probe the worker from this sandbox; a proxied probe here says nothing about the owner's device.
