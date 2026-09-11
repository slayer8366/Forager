# Data inventory for the privacy policy and the Play data safety form

> **RECOVERED 2026-09-09.** This report was never committed to the retired repository — it existed
> only in an unpushed working tree and was recovered from the local archive during the repository
> migration. It carries no occurrence of the retired package root and is unedited apart from this
> note. Its stated base, `cb16932`, refers to a repository that no longer exists.
>
> **It is not superseded.** The backup approach it was written alongside was later withdrawn, but
> this document is an inventory of *where data goes* — network destinations, outbound intents, and
> the claims the privacy policy and the Play Data safety form rest on. That did not change with the
> backup ruling, and the Data safety form is still outstanding.

**Date:** 2026-09-08
**Type:** Report only. No code, test or dependency change; nothing was fixed.
**Base:** `origin/main` = `cb16932` (the merge of PR #77). See "Base branch" below —
this is *not* the same tree as local `main`.
**Method:** static analysis of the tree, plus one Gradle dependency resolution
(`:app:dependencies --configuration debugRuntimeClasspath`, offline) and direct
inspection of the resolved AAR artifacts in the Gradle cache. **No device, no runtime
observation, and none is claimed anywhere below.**

---

## The five draft claims, answered first

The draft policy's assertions, in the dispatch's own order. Two are false as written
and one is materially misleading; the evidence for each is in the numbered sections.

| Draft claim | Verdict | Why |
|---|---|---|
| No account, no login, no server-side storage of user data | **True** | No auth code, no credential storage, and no `@POST`/`@PUT`/`@Multipart`/`@Body` anywhere in the tree (§1). Every request is a `@GET`. |
| No analytics, no advertising, no advertising identifiers | **True** | 294 artifacts on the resolved runtime classpath; no analytics, ads, attribution or crash SDK among them, and no Google Play Services at all (§1, §2). |
| Location used only while recording or viewing position | **False** | Two independent mechanisms take location outside that description (§4.3). |
| Journal data leaves only by explicit user action | **False** | `android:allowBackup="true"` with no extraction rules (§6.2), plus precise coordinates sent to three third-party APIs as ordinary use (§1.1, §1.2). |
| Tiles come from a self-operated worker | **Partly true, misleading in practice** | The self-operated worker serves the *offline* style only, behind a manual toggle that defaults off. The everyday map draws raster tiles from OpenTopoMap (the default), OpenStreetMap or USGS, and every style fetches glyph fonts from `demotiles.maplibre.org` (§3). |

### The four findings that most change the policy

1. **Android Auto Backup is on.** `app/src/main/AndroidManifest.xml:62` sets
   `android:allowBackup="true"` explicitly, and the manifest declares neither
   `android:dataExtractionRules` nor `android:fullBackupContent`. With `targetSdk = 37`
   and no rules file anywhere in the tree, the platform defaults apply: the Room
   database (every find, track point, waypoint and note, with coordinates), the stored
   photos, the DataStore preference files and the crash logs are all in the default
   backup set and are copied to the user's Google Drive with no user action. This is a
   path by which journal data leaves the device that the draft does not describe.

2. **The map contacts three third-party tile hosts and a fourth for fonts, not the
   self-operated worker.** `Basemap.kt:148,162,171` — `basemap.nationalmap.gov`,
   `a.tile.opentopomap.org`, `tile.openstreetmap.org`. `BasemapStyles.kt:154` sets
   `glyphs` to `https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf` on
   *every* style, MapLibre's public demo server. The `forager-pmtiles` worker is
   reached only when the Cartography trip report's "Offline maps" toggle is on
   (`CartographyEntryReportScreen.kt:238`, `mutableStateOf(false)` per entry) or
   during an offline-region download.

3. **Location starts at app open, not at a user action.** On a compact (phone) window
   `AvailabilityScreen.kt:1358` runs `LaunchedEffect(Unit) { onLocateMe() }`, which in
   `MainActivity.kt:308` launches the runtime location-permission request and then
   takes a fix. Independently, `AvailabilityViewModel`'s `init` (line 103) calls
   `collectLiveFixes()` (line 141), which subscribes to `AndroidLocationTracker.fixes`
   — `requestLocationUpdates` on **both** GPS and network providers at a 1 s floor
   (`AndroidLocationTracker.kt:35,74,102`) — for the ViewModel's whole lifetime, on
   every tab, not only the map.

4. **Precise coordinates are sent to iNaturalist and Open-Meteo as ordinary use.**
   `INaturalistApi.kt:31-33,47-49` and `OpenMeteoApi.kt:33-34` /
   `OpenMeteoArchiveApi.kt:25-26` all take `lat`/`lng` (or `latitude`/`longitude`) as
   query parameters. For the data safety form this is "location, shared with a third
   party", whether the coordinate came from GPS or from manual entry.

**A dispatch premise that was wrong:** `ForagerPace` does not exist in this tree. See
§2.3.

---

## 1. Every network destination

Six hosts, from first-party code. One further outbound mechanism comes from a
dependency (§1.5). Nothing else in the tree opens a socket: there is no update check,
no config fetch, no crash upload, no telemetry endpoint, and no push registration.

`OkHttpClient` is constructed in exactly three places (`INaturalistClient.kt`,
`OpenMeteoClient.kt`, `OpenMeteoArchiveClient.kt`), each `object`-scoped and each the
sole constructor of its Retrofit instance. MapLibre does its own HTTP natively.

### 1.1 `api.inaturalist.org`

- **Base URL:** `INaturalistClient.kt:13`, `https://api.inaturalist.org/v1/`.
- **Endpoints:** `GET observations/species_counts` and `GET observations`
  (`INaturalistApi.kt:38,45`).
- **Sent:** `lat`, `lng`, `radius` (km), `month`, and optionally `iconic_taxa`,
  `taxon_id`, `without_taxon_id`, `verifiable`, `per_page`. **Precise coordinates
  leave the device on every call.** No identifier, no cookie, no auth header, no
  device or install ID — the requests are anonymous apart from the IP address, which
  is inherent to any HTTPS request.
- **Triggered by:** running a search, and by the Maps tab loading sightings for a
  region already searched (`AvailabilityViewModel.onMapTabSelected`, which returns
  early when `state.region` is null). Both require the user to have set a region
  first — by "Use current location", by manual coordinate entry, or by a recent search.
- **Required or user-initiated:** user-initiated. Results are cached in Room
  (`cached_searches`) and served on a later offline miss.

### 1.2 `api.open-meteo.com` and `archive-api.open-meteo.com`

- **Base URLs:** `OpenMeteoClient.kt:13`, `OpenMeteoArchiveClient.kt:18`. Two hosts,
  two clients, deliberately.
- **Endpoints:** `GET v1/forecast` and `GET v1/archive`.
- **Sent:** `latitude`, `longitude`, plus the requested variable set, the day window
  and `timezone=auto`. **Precise coordinates leave the device on every call.** Again
  anonymous — no key, no identifier.
- **Triggered by:** the conditions/forecast panels, the trip-window computation and
  the seasonal pattern — all downstream of a search region existing.
- **Required or user-initiated:** user-initiated, same gate as §1.1.

### 1.3 Map tiles, style, TileJSON and fonts

Five distinct destinations; §3 has the full treatment.

| Host | What | When |
|---|---|---|
| `a.tile.opentopomap.org` | raster PNG tiles | OpenTopoMap basemap — **this is the app's default** (`MapMode.DEFAULT = TOPOGRAPHIC`, `MapMode.kt:30,36`) |
| `tile.openstreetmap.org` | raster PNG tiles | OSM basemap selected |
| `basemap.nationalmap.gov` | ArcGIS raster tiles | "USGS Satellite" basemap selected |
| `demotiles.maplibre.org` | glyph PBFs (`Open Sans Semibold`) | **every** style, online or offline, whenever the map draws a text label |
| `forager-pmtiles.brandonlee1-894.workers.dev` | `/style/offline.json`, `/us.json`, `/us/{z}/{x}/{y}.mvt` | offline-region download, and the Cartography trip report's manual offline toggle |

### 1.4 Outbound intents (not this app's network, but data leaving the process)

- `geo:0,0?q=<lat>,<lng>(<name>)` — `AvailabilityPureFunctions.kt:141-145`. Hands a
  planned trip's or waypoint's coordinates and its user-typed name to whatever maps
  app resolves. User-initiated ("Directions").
- `https://www.inaturalist.org/observations/<id>` —
  `AvailabilityPureFunctions.kt:197-199`, with an explicit
  `setPackage("org.inaturalist.android")` attempt first. Carries no user data, only a
  public observation id. User-initiated.

### 1.5 What a dependency initiates on its own — one real case

I resolved `debugRuntimeClasspath` (294 artifacts) and inspected every resolved AAR's
own manifest. **Five dependency-declared manifest components merge into this app:**

| Component | From | Network? |
|---|---|---|
| `androidx.emoji2.text.EmojiCompatInitializer` | `androidx.emoji2:1.4.0` | **Yes, indirectly — see below** |
| `androidx.lifecycle.ProcessLifecycleInitializer` | `lifecycle-process:2.11.0` | No |
| `androidx.profileinstaller.ProfileInstallerInitializer` + `ProfileInstallReceiver` | `profileinstaller:1.4.0` | No — writes a baseline profile into app storage |
| `androidx.room.MultiInstanceInvalidationService` (`android:exported="false"`) | `room-runtime:2.8.4` | No — local IPC |
| `androidx.compose.ui.tooling.PreviewActivity` (`android:exported="true"`) | `ui-tooling:1.12.0` | No — and `debugImplementation` only, so not in the release APK |

**`EmojiCompatInitializer` is the "library that phones home on init" case.** It is
registered through `androidx.startup`'s `InitializationProvider` in emoji2's own AAR
manifest, so it runs at process start (deferred by emoji2 to just after the first
Activity resumes) with no call from this app. Its default configuration
(`DefaultEmojiCompatConfig$DefaultEmojiCompatConfigFactory`, confirmed by reading the
class's own constant pool) resolves a ContentProvider answering the intent action
`androidx.content.action.LOAD_EMOJI_FONT` and requests the font named
`emojicompat-emoji-font` from it. On a device with Google Play Services that provider
is GMS's font provider, and GMS downloads the font over the network if it is not
already cached.

For the policy: **this app's own process makes no such request, and no user data is
involved** — the query carries a font name. But it does mean a Play-serviced device may
make a Google network request on this app's behalf shortly after launch, and it is the
kind of thing a Play reviewer's traffic capture would show. It cannot be attributed to
any first-party code; removing it would mean excluding emoji2 or swapping to
`emoji2-bundled`, a behaviour change and deliberately out of scope here.

**MapLibre contains no telemetry.** `org.maplibre.gl:android-sdk:13.5.0`'s
`classes.jar` has zero entries matching `telemetry`, `analytics` or `event`. The native
`libmaplibre.so` contains three base URLs — `https://api.mapbox.com`,
`https://api.maptiler.com`, `https://demotiles.maplibre.org` — which are the well-known
tile-server bases used to expand `mapbox://` / `maptiler://` / `maplibre://` style
URIs. No such scheme appears anywhere in `app/src` or `data/`, and this app calls the
single-argument `MapLibre.getInstance(context)` (`MapLibreStorage.kt:94`) with no API
key, so none of the three is contacted for that reason. The Java classes also carry
`mapbox.com/feedback` links, which belong to MapLibre's attribution dialog and are only
opened by a user tapping an attribution entry that declares them; none of this app's
styles do.

**No Play Services of any kind is on the classpath** — no `play-services-*`, no
Firebase, no advertising-identifier library. Location comes from the platform
`LocationManager`, deliberately (`AndroidLocationTracker.kt:21-24`).

---

## 2. Crash and diagnostic reporting

**No third-party crash SDK. Nothing is uploaded anywhere.** The app writes crash traces
to a local file and shows them in Settings; sending one is an explicit share.

### 2.1 The mechanism that exists: `CrashFileStore` (first-party, local)

- `ForagerApplication.onCreate` (line 14) installs `CrashUncaughtExceptionHandler` as
  the default uncaught-exception handler, capturing and chaining to the platform's
  previous handler so process-death behaviour is unchanged.
- `CrashFileStore.write` writes `crash-<epochMillis>.txt` containing: an ISO timestamp,
  the thread name, the API level, and `throwable.stackTraceToString()` — the whole
  `Caused by:` chain including each throwable's own message. Ten files are kept; older
  ones are pruned.
- **Location:** `context.getExternalFilesDir(null)/crashes`
  (`CrashFileStore.kt:76`) — app-specific *external* storage, not internal `filesDir`.
  That matters twice: it is in Auto Backup's default set (§6.2), and it is the one
  thing this app writes outside internal storage.
- **Can it leave the device?** By the user tapping share in the Settings crash panel:
  `CrashLogPanel.kt:201-206` builds an `ACTION_SEND` with a FileProvider URI
  (`file_paths.xml`'s `external-files-path name="crashes"`). No automatic upload path
  exists. **But it is in the auto-backup set**, so a crash file is also copied to
  Google Drive without any share.
- **Can it contain coordinates?** Not by construction — the file's fixed fields are
  timestamp, thread and API level. A coordinate could only appear inside an exception
  *message*, and no code in the tree builds an exception message from a coordinate
  (the only `error(...)` string anywhere near photo/location handling is
  `DecodedPhoto.kt:60`, which interpolates a relative file path). I did not enumerate
  every message the platform or a library could produce, so the honest statement is:
  **no first-party path puts a coordinate in a crash file; a third-party exception
  message doing so cannot be ruled out from the tree alone.**

### 2.2 `ErrorLog` (first-party, `android.util.Log`, local only)

`domain/ErrorLog.kt` is a one-method `fun interface`; the production implementation is
`MainActivity.kt:46`, `Log.w(tag, message, error)`. It exists so the plain-JVM-testable
ViewModels don't call `android.util.Log` directly. **It writes to logcat and nowhere
else — no file, no network.** Its ~25 call sites all pass fixed English strings and a
`Throwable`; some interpolate an entity UUID (`"Couldn't save entry '${entry.id}'"`) but
**none interpolates a coordinate.** Fifteen further files call `android.util.Log`
directly with the same shape.

The one location-adjacent log line is the instrument log at
`AndroidLocationTracker.kt:53-62`, tag `ForagerFix`, `Log.d`, once per platform fix
(≤1 Hz while a fix stream is active). It emits `provider`, `acc`, `hasSpeed`, `speed`,
`hasSpeedAccuracy`, `speedAccuracy`, `hasBearing` and `time`. **It does not emit
latitude or longitude.** Note it is *not* gated on `BuildConfig.DEBUG`, so it runs in
the release build the beta will ship. Logcat is not readable by ordinary apps on a
modern device, but it is captured in a bug report.

The OkHttp `HttpLoggingInterceptor` — which at `Level.BASIC` logs the full request
line, coordinates included — is installed **only** when `debug = true`, and
`AppContainer.kt:117-119` passes `BuildConfig.DEBUG`. A Play release build does not
install it.

### 2.3 `ForagerPace` — the dispatch's premise is wrong for this tree

**`ForagerPace` does not exist anywhere in this checkout** (case-insensitive search of
the whole worktree: zero hits). It exists on the unmerged branch
`origin/claude/new-session-3x1aba` as `domain/PaceLog.kt`'s
`PACE_LOG_TAG = "ForagerPace"`, with `MovingPace.toLogRecord` in
`domain/PaceLogRecord.kt` and one call in `TrackRecordingViewModel`'s poll. I read that
branch's version so the answer is useful if it lands: the record is a `key=value` line
of pace instrumentation — track UUID, call index, timestamps, point counts, distances
in metres, speeds and two threshold constants. **It contains no latitude or
longitude**, and like `ForagerFix` it is a `Log.d` to logcat with no file or network
sink. If it merges before the beta, "can it leave the device" stays no and "can it
contain coordinates" stays no.

### 2.4 Play's built-in reporting

Nothing in the tree opts in or out of Android vitals; it is automatic for any app on
Play, Google is the processor, and the user controls it in device settings. Worth
stating in the policy for completeness, but it is not something this app configures.

---

## 3. The map tile service

### 3.1 The self-operated worker, and how its URLs are built

`OfflineStyle.kt:18` is the single constant:
`https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json`. The chain is:

1. MapLibre fetches `/style/offline.json` (served inline by the Worker from
   `src/offline-style.json`).
2. That style's one source is `"url": ".../us.json"` — a TileJSON document.
3. `us.json` yields the tile template the Worker's `tilePath()` route serves,
   `/us/{z}/{x}/{y}.mvt`, read out of the `forager-maps` R2 bucket.

Offline downloads use the same URL: `MapLibreOfflineMapRepository.kt:97-98` builds an
`OfflineTilePyramidRegionDefinition` against `OFFLINE_STYLE_URL`, with
`MIN_ZOOM = 10.0`, `MAX_ZOOM = 15.0`, `MAX_RADIUS_KM = 24` and
`TILE_COUNT_LIMIT = 6000` (`OfflineMapRepository.kt:86,110,111,188`).

### 3.2 What is knowable about server-side logging, from the repository

- `server/pmtiles-worker/src/index.ts` and `shared.ts` contain **no `console.*` call,
  no Analytics Engine `writeDataPoint`, and no logging of any kind.** The only
  request-header read in either file is `request.headers.get("Origin")` for CORS
  (`index.ts:98`).
- `wrangler.toml` declares `name`, `main`, `compatibility_date`, `account_id`,
  `minify = false`, one `[[r2_buckets]]` binding and one `[vars]` entry
  (`ALLOWED_ORIGINS = "*"`). **There is no `[observability]` block, no
  `analytics_engine_datasets` binding, no Logpush configuration and no tail consumer.**
- Cloudflare's own edge request logging, Workers Logs/observability defaults and R2
  access logging are account-level settings that are **not in this repository**. **The
  owner must check the Cloudflare dashboard**; the tree cannot answer whether request
  logs with IP addresses are retained, and I am not claiming they aren't.

The dispatch's point stands regardless: a tile request tells the server which map
square is being viewed, and paired with an IP that is a location signal even though the
request body carries nothing.

### 3.3 Do downloaded offline regions suppress further requests? **No, not on the everyday map.**

Whether the offline (worker) style is used is `MapRenderMode.useOfflineTiles`, and there
are exactly three construction sites:

- `AvailabilityScreen.kt:780` — the main Maps tab: `MapRenderMode(basemap, night)`, so
  `useOfflineTiles` takes its **default of `false`**.
- `CentrePinLocationPicker.kt:98` — same, default `false`.
- `CartographyEntryReportScreen.kt:377` — the only site that can pass `true`, from a
  per-entry `var useOfflineTiles by remember(entry.id) { mutableStateOf(false) }`
  (line 238) driven by a manual toggle (lines 418, 453).

So downloading a region does **not** change where the main map gets its tiles. The
everyday map keeps requesting raster tiles from OpenTopoMap / OSM / USGS for the area
being viewed, downloaded regions or not. MapLibre's own resource store (redirected to
`filesDir/maplibre-offline` by `MapLibreStorage.kt`) will re-serve tiles it has already
fetched, so repeat views of the same area cost fewer requests — but that is an HTTP
cache, not the offline store, and it is not what "I downloaded this area" means to a
user.

Worth flagging beyond the policy: a user who downloads a region for a trip and believes
the map is now offline is, on the Maps tab, still making tile requests.

### 3.4 Does a tile request happen before the user has interacted with a map? **Yes.**

The app opens on the Maps tab: `AvailabilityScreen.kt:673` is
`mutableStateOf(ResultsTab.MAP)` and line 682 `mutableStateOf(CompactTab.MAP)`. The map
composes immediately with a viewport that is never null
(`AvailabilityScreen.kt:3253-3256`): the searched region if one exists, else a resolved
locate-me fix, else `JOURNAL_PICKER_DEFAULT_REGION` — the geographic centre of the
contiguous US, `LatLng(39.8283, -98.5795)`, radius 15 km
(`AvailabilityOfflineMapsUi.kt:256,265`). Tiles for that viewport are requested with no
user interaction at all, from `a.tile.opentopomap.org` (the default basemap,
`MapMode.kt:30,36`).

More significantly, on a compact window `AvailabilityScreen.kt:1358` fires
`LaunchedEffect(Unit) { onLocateMe() }` once per app open, which requests the location
permission and, on a grant, moves the viewport to the user's actual position — at which
point the tile requests describe **where the user is**, still with no interaction beyond
opening the app.

---

## 4. Permissions

### 4.1 Declared in `app/src/main/AndroidManifest.xml`

| Permission | What requires it | What breaks without it |
|---|---|---|
| `INTERNET` | the three OkHttp clients; MapLibre's native HTTP | every search, weather read and map tile |
| `ACCESS_COARSE_LOCATION` | `AndroidLocationProvider`, `AndroidLocationTracker`, `TrackRecordingService`, `SightingsMap`, `MainActivity` (11 references, paired with FINE) | "Use current location", locate-me, the compass strip's live fix, track recording |
| `ACCESS_FINE_LOCATION` | as above | track points would be coarse or absent; recording is refused without one of the two (`TrackRecordingService.kt:90`) |
| ~~`CAMERA`~~ **REMOVED 2026-09-10** | ~~`PhotoAcquisitionLaunchers.kt:116`, requested at the moment of first capture~~ **Nothing. It was never required:** `ActivityResultContracts.TakePicture` sends `ACTION_IMAGE_CAPTURE`, which the user's camera app services under its own permission. Declaring `CAMERA` did not enable capture, it made the platform *require* the grant before honouring the intent | ~~in-app camera capture for a log entry~~ **Nothing.** Capture works with one fewer prompt. The manifest declaration and the runtime request were removed together; removing either alone breaks capture silently |
| `ACCESS_MEDIA_LOCATION` | `FilePhotoStore.readExifData` via `MediaStore.setRequireOriginal` (API 29+) | an imported gallery photo's GPS EXIF is silently redacted, so the find gets no coordinate from the photo |
| `FOREGROUND_SERVICE` | `TrackRecordingService` | multi-hour recording cannot survive backgrounding |
| `FOREGROUND_SERVICE_LOCATION` | the same service's `foregroundServiceType="location"` (required from API 34) | the service cannot start |
| `POST_NOTIFICATIONS` | requested at recording start (`MainActivity`) | the recording and off-track notifications are not displayed (recording still runs) |
| `VIBRATE` | `AndroidAlertDelivery.vibrateForAlert` (`AndroidAlertDelivery.kt:107-114`) | the off-track alert does not reach a pocketed phone |

**Every declared permission is used.** There is no unused first-party permission to
remove.

### 4.2 Merged in from a dependency — two the app never declares

I scanned every AAR on the resolved runtime classpath.
**`org.maplibre.gl:android-sdk:13.5.0` is the only dependency that declares
permissions**, and it contributes two the app's own manifest does not:

- **`ACCESS_NETWORK_STATE`** — MapLibre uses it to detect connectivity changes and
  retry tile fetches. Normal permission, install-time, no runtime prompt.
- **`ACCESS_WIFI_STATE`** — normal permission, install-time. **Nothing in first-party
  code uses it**, and I found no MapLibre call site for it either; it is inherited from
  the SDK's Mapbox ancestry. It is the one permission in the merged manifest that looks
  unjustifiable when Play asks for a justification, and it is a candidate for removal
  via `tools:node="remove"` — **reported, not changed**, per this dispatch's scope.

MapLibre also merges `<uses-feature android:name="android.hardware.vulkan.version"
android:required="true" ... />`. That is a device-filtering matter rather than a privacy
one, but it is worth knowing before the listing goes up: `required="true"` excludes
devices without Vulkan 1.0 from the store listing.

`androidx.test:core:1.5.0` declares `REORDER_TASKS`, but it is on the test classpath
only; the build's own `verifyNothingTestOnlyReachesTheApk` task scans the built APK's
dex and packaged manifest for exactly this class of leak.

### 4.3 Background location — there is none, and foreground-only is in fact sufficient

**`ACCESS_BACKGROUND_LOCATION` is not declared anywhere.** The app does not request it
and does not need it: a `foregroundServiceType="location"` service started while the app
is in the foreground keeps receiving location updates after the app is backgrounded,
which is exactly the multi-hour-recording case. Nothing in the tree would work better
with background location. **This is the easiest section of the Play form to answer, and
the answer is "not requested".**

> **SUPERSEDED IN PLACE 2026-09-11 (owner-authorised), first clause only.** `5967dd5`
> (2026-09-09 21:40) added a lifecycle gate: the live-fix subscription is now released on the
> hosting Activity's `ON_STOP` and re-acquired on `ON_START`
> (`MainActivity.kt:225-226` → `AvailabilityViewModel.onEnteredForeground`/`onLeftForeground`).
> **So "collected for the whole lifetime of an Activity-scoped ViewModel" and "the entire time
> it is in the foreground" are no longer accurate** — screen-off or backgrounding now releases it.
>
> **The rest of the paragraph stands unchanged and is still true:** not gated on the Maps tab, not
> gated on a search, not gated on a recording, at a 1 Hz two-provider floor, whenever the app is
> foregrounded. `AvailabilityViewModel.onEnteredForeground`'s own doc comment says so —
> "**Deliberately not need-gating.** This still subscribes on every tab, at the same 1-second
> floor, whether or not anything is consuming fixes."
>
> **No submitted Data safety answer changes** (owner, 2026-09-11): location is declared collected
> and shared either way. This head-note correction exists because this table is an upstream source
> the form is filled from, and a claim that outlives its mechanism is how the `CAMERA` answer would
> come back — the second such claim in this one document. The file's own head note, "It is not
> superseded," predates this and is wrong as to this section. See
> `2026-09-11-lifecycle-gate-and-corrections-round-4.md`.

The rest of this section is where the draft's third claim fails. "Location used only
while recording or viewing position" is not what the code does:

- `AvailabilityViewModel.init` (line 103) → `collectLiveFixes()` (line 141) →
  `AndroidLocationTracker.fixes` (line 35). That flow calls `requestLocationUpdates` on
  **every enabled provider** — GPS *and* network — at
  `MIN_UPDATE_INTERVAL_MILLIS = 1_000L` (line 102), and it is collected for the whole
  lifetime of an Activity-scoped ViewModel. Not gated on the Maps tab, not gated on a
  search, not gated on a recording; the function's own doc comment says so ("Runs for
  this ViewModel's whole lifetime"). In practice: with permission granted, the app holds
  a 1 Hz two-provider location subscription the entire time it is in the foreground, on
  the Journal tab as much as on the map.
- `AvailabilityScreen.kt:1358` pings locate-me once per app open on a phone-sized
  window, which raises the OS permission dialog on a device that hasn't granted it.

For the data safety form, the accurate declaration is: **precise location and
approximate location, collected, used for app functionality, and shared with third
parties** (iNaturalist and Open-Meteo receive coordinates; the tile hosts receive an
area of interest). Whether collection is "optional" is a judgement for the owner: the
app is usable with a manually typed coordinate, so location *permission* is optional,
but a coordinate of some kind is required for the core feature.

---

## 5. Everything stored on device

Every store below is app-private. **Nothing is written to shared storage** — no
MediaStore insert, no `ACTION_CREATE_DOCUMENT`, no public-directory write. Nothing
survives uninstall *locally*; but see §6.2, because Auto Backup means a reinstall can
restore most of it from Google Drive.

| Store | Path | Contents | Location? | Personally identifying? | In the backup set? |
|---|---|---|---|---|---|
| Room `forager.db` (schema 15) | `databases/forager.db` | 15 entities — below | **Yes, extensively** | user-written names and notes | **Yes** |
| Fungi index | `databases/fungi_index.db`, copied from `assets/databases/fungi_index.db` | 11,257 bundled taxa; read-only reference | No | No | Yes (rebuildable from the asset) |
| DataStore ×3 | `datastore/map_preferences`, `datastore/distance_unit_preferences`, `datastore/app_theme_preferences` | last-picked offline centre/radius, staleness threshold, night maps, fullscreen, units, theme | the last-picked offline centre is a coordinate | No | **Yes** |
| Log photos | `filesDir/photos/<uuid>.jpg` | the photo bytes as imported or captured, **EXIF untouched** | in the file's own EXIF, if the source had it | photographs | **Yes** |
| Camera scratch | `filesDir/captures/<uuid>.jpg` | a capture in flight before it is persisted | same | same | Yes |
| MapLibre resource store | `filesDir/maplibre-offline/` | downloaded offline regions **and** MapLibre's tile cache | the regions are areas the user chose | No | **Yes** |
| Crash logs | `getExternalFilesDir(null)/crashes/crash-*.txt`, max 10 | timestamp, thread, API level, stack trace | see §2.1 | No | **Yes** |
| GPX exports | `cacheDir/tracks/forager-track-*.gpx` | a full track | **yes** | no | No — `cacheDir` is excluded |

**Written outside internal storage:** only the crash directory, under
`getExternalFilesDir(null)`. It is still app-scoped (removed on uninstall, and not
readable by other apps under scoped storage), but it is the one path that leaves
`filesDir` and it is in the backup set.

**What the Room database holds, concretely** — this is what the policy has to describe:

- `track_points` — `lat`, `lng`, `altitude`, `accuracyMeters`, `timestampEpochMillis`,
  `speedMetersPerSecond`, `speedAccuracyMetersPerSecond`. A complete movement trace.
- `waypoints` — `lat`, `lng`, `altitude`, user-typed `name` and `note`, timestamp.
- `mushroom_log_entries` — nullable `lat`/`lng`, `foundOn` date, free-text
  `entryNotes`, `ownIdentification`, and ~40 morphology fields. **A find's location is
  the most sensitive datum this app holds** — foragers treat patch locations as private.
- `log_photos` — `relativePath`, `createdAtEpochMillis`, and `latitude`/`longitude`
  read from a gallery import's EXIF or patched in from a live fix after a capture.
- `cached_searches` — `lat`, `lng`, `radiusKm`, `month`, filter, the iNaturalist
  response as JSON, and access timestamps. **A history of every place searched.**
- `offline_regions` — `name`, `lat`, `lng`, `radiusKm`, zooms, timestamp.
- `planned_trips` — a named future destination with coordinates.
- `cartography_entries` plus five `cartography_entry_*_ref` tables — the trip journal
  linking all of the above.

**One thing that looks worse than it is:** `mushroom_log_entries` carries
`syncStateKind`, `syncProgress`, `syncRemoteObservationId`, `syncUploadedAtEpochMillis`
and `syncFailureReason`. These are for a *planned* iNaturalist upload
(`docs/plans/mushroom-log-phase2-inaturalist-upload.md`). **No upload code exists** —
there is no `@POST`, `@PUT`, `@Multipart`, `@Body` or `@Part` anywhere in the tree, and
`MushroomLogEntryEntity.kt:59` records that only `DRAFT` is ever written. The columns
are inert. Worth knowing because a reviewer reading the schema could reasonably assume
otherwise, and because when that feature lands the policy will need rewriting.

**A note on backup size:** Auto Backup's per-app quota is small relative to
`filesDir/maplibre-offline`, which can hold up to 6,000 tiles per downloaded region.
That is a functional consequence of §6.2, not a privacy one, but it is the kind of thing
that makes backups silently fail.

---

## 6. Every path by which data can leave

The draft says data leaves only when the user shares or exports. **That is false, and
the exception is the important one.**

### 6.1 User-initiated paths (the draft is right about these)

1. **GPX export and share** — `TrackExportPanel.kt:127-129,140-148`. Writes to
   `cacheDir/tracks/` and hands a FileProvider URI to `ACTION_SEND` with
   `FLAG_GRANT_READ_URI_PERMISSION`. Content is `GpxCodec.encode`: per point, `lat`,
   `lon`, `<ele>` and `<time>`; per waypoint, coordinates, elevation, time, `<name>` and
   `<desc>`. **In this tree the exporter passes `waypoints = emptyList()`**
   (`TrackGpxExporter.kt:28`), so only the track goes out. Fully user-initiated.
2. **Crash report share** — `CrashLogPanel.kt:201-206`. See §2.1.
3. **Directions intent** — `geo:` with coordinates and a user-typed name. §1.4.
4. **iNaturalist observation intent** — a public observation id only. §1.4.

**There is no photo sharing and no journal export.** No `ACTION_SEND` for a photo, no
MediaStore insert, no document-creation flow, and `file_paths.xml` deliberately does not
expose `photos/`. A user cannot get their finds out of this app today; only tracks and
crash logs.

### 6.2 Android Auto Backup — the path the draft misses

`app/src/main/AndroidManifest.xml:62` sets `android:allowBackup="true"`, explicitly, not
by omission. The `<application>` element declares **no `android:dataExtractionRules` and
no `android:fullBackupContent`**, and `app/src/main/res/xml/` contains only
`file_paths.xml` — there is no backup-rules file anywhere in the tree.

With `targetSdk = 37` and no rules, the platform defaults apply. That means the default
backup set — internal `filesDir`, `databases/`, `shared_prefs/`, and the app-specific
external files directory, excluding `cacheDir` and `no_backup/` — is copied to the
user's Google Drive, and is also transferred device-to-device on a phone migration.
Concretely, **the following leave the device with no user action beyond having device
backup switched on**, which is Android's own default:

- `forager.db` — every find with its coordinates and notes, every track point, every
  waypoint, every planned trip, and the searched-location history in `cached_searches`.
- `filesDir/photos/` — the photographs, EXIF intact.
- the three DataStore preference files.
- `getExternalFilesDir(null)/crashes/` — the crash traces.
- `filesDir/maplibre-offline/` — the downloaded regions.

`cacheDir/tracks/` (exported GPX) is excluded, being cache.

**This materially changes the policy**, exactly as the dispatch anticipated. The policy
either has to disclose that app data is backed up to the user's Google account, or the
behaviour has to change — and changing it is a separate dispatch. The fair framing is
not "we upload your data": the backup goes to *the user's own* Google account, Google is
the processor, and this app never sees it. But it is unambiguously a way journal data
leaves the device without the user choosing to share anything.

### 6.3 Not a path

No `WorkManager`, no `JobScheduler`, no `AlarmManager`, no first-party
`BroadcastReceiver`, and no exported `ContentProvider` other than the `FileProvider`
(`android:exported="false"`, `grantUriPermissions="true"`, three narrow paths). The only
exported component in the release APK is `MainActivity`.

---

## 7. Third-party data and attribution

| Source | Used for | Licence | Attribution shown today |
|---|---|---|---|
| OpenTopoMap (+ OSM, SRTM) | `a.tile.opentopomap.org` raster tiles — **the default basemap** | CC-BY-SA for OpenTopoMap's rendering; OSM data ODbL; SRTM public domain | **Yes** — `"© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)"` (`Basemap.kt:161`), drawn as an always-visible caption over the map |
| OpenStreetMap | `tile.openstreetmap.org` raster tiles | ODbL 1.0; the OSMF tile usage policy also governs using their tile servers directly | **Yes** — `"© OpenStreetMap contributors"` (`Basemap.kt:170`) |
| USGS The National Map orthoimagery | `basemap.nationalmap.gov` | public domain | **Yes** — `"USGS The National Map, orthoimagery — public domain"` (`Basemap.kt:147`) |
| Protomaps basemap (from OSM), in the R2 PMTiles archive | the offline vector style | Protomaps basemaps BSD-3-Clause; underlying OSM data ODbL | **Yes, when offline tiles are on** — `"Protomaps © OpenStreetMap"` (`OfflineStyle.kt:28`); the style JSON carries the HTML form |
| MapLibre demo glyph server | `demotiles.maplibre.org` fonts, on every style | the font is Open Sans (Apache 2.0); the *server* is MapLibre's demo infrastructure | **No** — see the note below |
| iNaturalist API | species counts and observations | iNaturalist's API terms; observation data is per-observation CC-licensed | **No dedicated credit.** "iNaturalist" appears in explanatory body text (`AvailabilityResultsUi.kt:163,202,329,352`), but there is no attribution line |
| `data/species-index` bundled index | offline species-name search | **Not recorded anywhere.** `data/species-index/README.md` describes it as "iNaturalist-derived" and states no licence | **No** |
| Open-Meteo | forecast and historical precipitation | Open-Meteo's free tier requires **CC-BY-4.0 attribution** | **No** |
| Protomaps reference Worker code | `server/pmtiles-worker/src/` | MIT, adapted — credited in the worker's own README | n/a (server code) |
| Material Symbols / `material-icons-extended` | UI icons | Apache 2.0 | not required in-app |

**What is missing that a licence appears to require:**

1. **Open-Meteo attribution.** Their free-tier terms ask for a CC-BY-4.0 credit. There
   is none anywhere in the app.
2. **iNaturalist attribution**, both for the live API and for the bundled
   `fungi_index.db` derived from it. There is no credit line, and no recorded licence
   for the bundled index at all.
3. **There is no About / Credits / open-source-licences screen.** `strings.xml` is 11
   lines and contains no attribution string. The only credits that exist are the
   per-basemap map caption. For a Play listing this is the natural place to put all of
   the above.

**A separate concern, not a licence one:** `demotiles.maplibre.org` is MapLibre's public
demonstration server, and this app requests glyphs from it on **every** style, including
the offline one, whenever the map draws a text label (`BasemapStyles.kt:151-154`). That
is a production dependency on someone else's demo infrastructure — a reliability and
courtesy issue, and one more third-party host to name in the policy. Self-hosting the
glyph PBFs on the existing Worker would remove it; **reported, not changed.**

---

## 8. Contradictions with the draft

Summarised in the table at the top; the evidence for each:

**"No account, no login, no server-side storage of user data." — TRUE.** No
authentication code, no token or credential storage, no user identifier generated or
persisted. No write endpoint exists in the tree (no `@POST`/`@PUT`/`@Multipart`/
`@Body`/`@Part`). The Cloudflare Worker is read-only — it returns `405` to any POST
(`index.ts:250`) and only reads from R2. The caveat worth writing into the policy
honestly: the three third-party APIs and the Worker's own edge necessarily see request
metadata (IP, timestamp, URL), and §3.2 says the tree cannot tell you what is retained.

**"No analytics, no advertising, no advertising identifiers." — TRUE.** Verified
against the resolved runtime classpath, not just the version catalog: 294 artifacts,
none of them an analytics, attribution, advertising or crash-reporting SDK, and no
Google Play Services at all. MapLibre carries no telemetry classes (§1.5). No
advertising ID is read anywhere. The only dependency-initiated outbound behaviour is
emoji2's font request (§1.5), which is not analytics.

**"Location used only while recording or viewing position." — FALSE.** §4.3. A 1 Hz
GPS-and-network subscription runs for the whole foreground lifetime of the app on every
tab, and a locate-me fires automatically at app open on a phone. Coordinates are also
sent to iNaturalist and Open-Meteo (§1.1, §1.2), which "used" does not cover.

**"Journal data leaves only by explicit user action." — FALSE.** §6.2. Auto Backup is
on, unrestricted, and copies the database, the photos and the crash logs to the user's
Google Drive. The user-initiated paths the draft describes (§6.1) are accurate as far as
they go — and note the draft over-claims in the other direction too, since there is no
journal export at all today.

**"Tiles come from a self-operated worker." — PARTLY TRUE, misleading as written.**
§3. The worker serves the offline vector style and tiles, but that path is reachable
only through one screen's manual toggle that defaults to off, plus the offline-region
download. Everyday map viewing draws raster tiles from `a.tile.opentopomap.org` (the
default), `tile.openstreetmap.org` or `basemap.nationalmap.gov`, and every style —
including the offline one — fetches fonts from `demotiles.maplibre.org`. A policy saying
tiles come from a self-operated worker would be inaccurate for the great majority of
tile requests a beta tester actually makes.

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed by reading the tree at `cb16932`:** every host, endpoint and query
parameter in §1; the absence of any crash/analytics SDK, verified against the *resolved*
`debugRuntimeClasspath` (294 artifacts) rather than the version catalog;
`allowBackup="true"` and the absence of any backup-rules file; every permission in the
app manifest and its call sites; MapLibre 13.5.0's own AAR manifest permissions and the
absence of telemetry classes in its `classes.jar`; the five dependency-declared manifest
components, and emoji2's `LOAD_EMOJI_FONT` / `emojicompat-emoji-font` constants read
from the class file itself; the Worker's routes, its complete absence of logging calls,
and `wrangler.toml`'s complete contents; every Room entity's columns; every
`ACTION_SEND`/`startActivity` in the tree; the map's default tab, default basemap,
default viewport and the three `MapRenderMode` construction sites.

**Inferred, and flagged as such where it matters:** that Auto Backup's *default*
inclusion set applies, since no rules file exists — I read the manifest, not a running
backup. That the `LOAD_EMOJI_FONT` provider on a typical device is Google Play Services
and that GMS fetches the font over the network — the constants and the initializer
registration are confirmed; what GMS does with the request is not observable from this
tree. That `ACCESS_WIFI_STATE` is unused by MapLibre as well as by this app: I found no
call site, which is weaker than proving there is none, since I did not decompile the
native library looking for one.

**Not observed at all:** anything at runtime. No device, no emulator, no traffic
capture, no `adb`. Nothing in this report is a runtime observation and none is claimed.

### What I could not determine

- **Whether the tile Worker's requests are logged.** The repository configures no
  logging; Cloudflare's account-level defaults (Workers Logs/observability, edge request
  logs, R2 access logs) are not in the tree. **The owner must check the dashboard**, as
  the dispatch anticipated.
- **Whether an exception message from a third-party library could carry a coordinate
  into a crash file.** No first-party path does; I could not enumerate every library
  message (§2.1).
- **What iNaturalist's and Open-Meteo's own retention and logging practices are.** Both
  receive precise coordinates from anonymous requests; what they keep is their policy,
  not ours, and the privacy policy should link to theirs.
- **Whether the manifest merger produces exactly the union I computed.** I did not run
  it — no built merged manifest exists in this worktree, and building one was outside a
  report-only dispatch. §4.2 is the union of the app manifest and every AAR manifest on
  the resolved runtime classpath, which is how the merger behaves for `uses-permission`,
  but it is a derivation and not a dump of a real merged manifest. **An `assembleDebug`
  would settle it in one step and is worth doing before the form is submitted.**

### Premises in this dispatch that were wrong

- **`ForagerPace` does not exist in this tree.** It lives on the unmerged branch
  `origin/claude/new-session-3x1aba`. I read it there and answered for it anyway
  (§2.3): no coordinates, logcat only.
- **"A Cloudflare worker named `forager-pmtiles` exists"** is right, but the draft's
  framing that map tiles come from it is not what the code does (§3.3, §8).
- The dispatch asks to give background location particular attention. There is no
  background-location permission at all (§4.3) — that section is short because the
  answer is an absence.

### Base branch

This worktree is cut from `origin/main` (`cb16932`). Local `main` is 15 commits ahead of
`origin/main` and equals `origin/claude/consolidate-docs-2026-09-08`. I checked what
those 15 commits contain: `git diff --stat cb16932 main -- app` is **empty** — they are
documentation only, including two rows already added to `docs/audits/README.md`. So no
app-code finding in this report is affected by the drift. The index row this report adds
will conflict with those two rows when the branches meet; per CLAUDE.md that is a merge,
never a rebase, and **every row is kept**.

### Anything I decided that this dispatch did not cover

- **I enumerated the merged-manifest permissions from the dependency AARs rather than
  from a built merged manifest**, and said so above rather than presenting a derivation
  as a dump.
- **I read `PaceLog` on an unmerged branch** to answer a dispatch item whose subject does
  not exist here, rather than reporting only "not found".
- **I resolved the dependency graph with Gradle** (`:app:dependencies`, offline,
  read-only) rather than reading the version catalog. The dispatch said to search the
  dependency list; the catalog lists 20 direct dependencies and the real classpath has
  294, and the one finding that mattered most — emoji2 — is not in the catalog at all.
- **I noted three things that are not privacy findings**, because they bear on the same
  submission and were free to observe while looking: MapLibre's
  `uses-feature vulkan required="true"` and its Play device-filtering consequence
  (§4.2); the absence of any journal export (§6.1); and that downloaded offline regions
  do not stop the main map making tile requests (§3.3), which is a user-facing
  correctness problem rather than a policy one.
- **I changed nothing.** No permission removed, no backup flag flipped, no test touched,
  no suite run.
