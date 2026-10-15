# API call count: iNaturalist, Open-Meteo, and the other hosts the app calls (read-only)

Dispatch 2026-10-15-03 (RECORD -1013; owner: "Start 1 and 2"). Read-only: no code changed, no
Gradle, no device, no request sent to any of the services. Two documentation pages were read
(Open-Meteo's terms and pricing pages); iNaturalist's documentation pages could not be read from
here (see §5).

**Base.** `origin/main` at `4ade21f0` (verified with `git ls-remote origin refs/heads/main` on
2026-10-14 local date, matching the dispatch). Every `file:line` below is at that commit, under
`app/src/main/java/com/zynergylabs/forager/app/` unless a path says otherwise.

**Evidence labels.** A claim with a `file:line` was read at that line. INFERRED means reasoned from
code that was read, not observed running. UNVERIFIED means not checkable from here. Estimates are
marked as estimates with their assumptions.

---

## 0. Reachability first: who calls each endpoint

Every JSON API request in the app goes through three Retrofit interfaces built in one place, and
each interface has exactly one production wrapper and one production caller chain. All six
chains end in `AvailabilityViewModel`, and nowhere else.

| Retrofit method | Wrapper (project-owned interface) | Use case | ViewModel call site |
|---|---|---|---|
| `INaturalistApi.getSpeciesCounts` (`data/remote/INaturalistApi.kt:31-42`) | `INaturalistMushroomRepository.getSpeciesCounts` (`data/repository/INaturalistMushroomRepository.kt:28`) behind `MushroomRepository` (`domain/MushroomRepository.kt:19`) | `PredictAvailabilityUseCase` (`domain/PredictAvailabilityUseCase.kt:22`) inside `GetAvailabilityUseCase` (`domain/GetAvailabilityUseCase.kt:112`) | `AvailabilityViewModel.kt:882` (`refresh`) |
| `INaturalistApi.getObservations` (`INaturalistApi.kt:45-56`) | `INaturalistMushroomRepository.getSightings` (`INaturalistMushroomRepository.kt:42`) behind `MushroomRepository` (`domain/MushroomRepository.kt:44`) | `GetSightingsUseCase` (`domain/GetSightingsUseCase.kt:17`) | `AvailabilityViewModel.kt:809` (`onMapTabSelected`), and again through `GetSeasonalPatternUseCase.kt:30` from `AvailabilityViewModel.kt:843` (`onSeasonalTabSelected`) |
| `OpenMeteoApi.getPrecipitation` (`data/remote/OpenMeteoApi.kt:30`, `@GET("v1/forecast")`) | `OpenMeteoWeatherProvider.fetch` (`data/repository/OpenMeteoWeatherProvider.kt:51`), one object behind both `WeatherProvider` and `TripPlanningWeatherProvider` (`AppContainer.kt:191-193`) | `GetConditionsUseCase` (`domain/GetConditionsUseCase.kt:15`), `GetTodaysForecastUseCase` (`domain/GetTodaysForecastUseCase.kt:25`), `GetTripWindowsUseCase` (`domain/GetTripWindowsUseCase.kt:23`) | `AvailabilityViewModel.kt:931`, `:954`, `:987` (all in `refresh`) |
| `OpenMeteoArchiveApi.getHistoricalPrecipitation` (`data/remote/OpenMeteoArchiveApi.kt:23`, `@GET("v1/archive")`) | `OpenMeteoHistoricalWeatherProvider` (`data/repository/OpenMeteoHistoricalWeatherProvider.kt:41`) behind `HistoricalWeatherProvider` | `GetSeasonalPatternUseCase` (`domain/GetSeasonalPatternUseCase.kt:49`) | `AvailabilityViewModel.kt:843` |

Wiring: the three clients are created at `AppContainer.kt:183-185`; the use cases at
`AppContainer.kt:220-240` and `:276`; they reach the ViewModel only through `MainActivity.kt:74-92`.
A grep for every provider/use-case name in `main/` found no other consumer (no worker, service,
receiver or second ViewModel). No path is unreachable; none has only a test caller.

**Interface rule (CLAUDE.md, Architecture).** All four JSON endpoints sit behind project-owned
interfaces; no domain or UI code imports Retrofit or a DTO. The map's online tile, style and glyph
requests (§4) do not go through a project-owned interface: they are URL constants handed to the
map library's style (`ui/map/Basemap.kt:123`, `:131`; `ui/map/BasemapStyles.kt:341`). Offline
downloads do go through `OfflineMapRepository`.

**Not network.** Species-name search reads a bundled offline index, not iNaturalist
(`AppContainer.kt:223-229`; `LocalFungiIndexRepository`). Photo URLs carried by species counts and
sightings (`INaturalistMushroomRepository.kt:90`, `:124`) are never loaded by any UI code: a grep
for `photoUrl` under `ui/` and `map/` returns nothing, and the app has no image-loading library. The
iNaturalist links at `ui/availability/AvailabilityPureFunctions.kt:198`, `:213` open another app or
the browser; the app itself sends nothing.

---

## 1. Triggers: what user action starts a fetch

`refresh(region, month, filter)` (`AvailabilityViewModel.kt:862-1001`) is the only place the ranked
list and the forecast-endpoint requests start. Its callers, each traced to a user action:

| User action | Entry | Calls `refresh` at |
|---|---|---|
| "Use current location" (after the permission result) | `MainActivity.kt:502-504` -> `:283` -> `useCurrentLocation` | `AvailabilityViewModel.kt:526` |
| Search typed coordinates | `MainActivity.kt:522` -> `searchManualCoordinates` | `AvailabilityViewModel.kt:508` |
| Tap a month chip (only when a region is already set) | `AvailabilitySearchUi.kt:1165` -> `MainActivity.kt:524` | `AvailabilityViewModel.kt:403` |
| Tap a recent search in the drawer | `AvailabilitySearchUi.kt:908` -> `MainActivity.kt:533` | `AvailabilityViewModel.kt:1107` |

What does **not** fetch, confirmed by reading:
- Panning or zooming the map: no call path from the map camera to `refresh` or any use case
  (the only `Region(...)` built from a camera centre is the offline-download picker,
  `ui/map/CentrePinLocationPicker.kt:259`).
- Changing the radius slider: `onRadiusChanged` only updates state (`AvailabilityViewModel.kt:397-399`).
- Picking a species suggestion: selects without searching (`AvailabilityViewModel.kt:471-473`, RECORD -725).
- Locate-me: location only (`AvailabilityViewModel.kt:550-560`).
- App start: the ViewModel's `init` loads only local data (`AvailabilityViewModel.kt:228-249`).
  `activeSearch` is null before any search (`:207`), so a recreated process does not refetch.

Tab-driven fetches: `LaunchedEffect(selectedTab, region, selectedMonth, taxonFilter, searchSerial)`
at `ui/availability/AvailabilityScreen.kt:1321-1324` (top level of `AvailabilityScreen`, so both
compact and wide layouts) calls `onMapTabSelected` when the Map tab is selected and
`onSeasonalTabSelected` when Seasonal is. `refresh` bumps `searchSerial` (`AvailabilityViewModel.kt:869`),
so every search re-runs this effect.

---

## 2. Tables per service

### 2a. iNaturalist (`https://api.inaturalist.org/v1/`, `data/remote/INaturalistClient.kt:6`)

| Endpoint | Call site | Trigger | Per trigger | Existing caching / de-dup |
|---|---|---|---|---|
| `GET observations/species_counts`, `per_page=30` (`INaturalistApi.kt:41`) | `INaturalistMushroomRepository.kt:28` | every `refresh` (`AvailabilityViewModel.kt:880-882`): search, month chip, recent search | 1 request per refresh; no fan-out | Room search cache, LRU five deep (`data/repository/RoomSearchCacheRepository.kt:20-23`, `:71`), **read only when the live request fails** (`domain/GetAvailabilityUseCase.kt:112-133`). No freshness window, no expiry other than LRU eviction. Not a de-dup: an identical search always goes to the network first (`AvailabilityViewModel.kt:1023-1027` states this is deliberate). |
| `GET observations`, `per_page=200` (`INaturalistApi.kt:55`) | `INaturalistMushroomRepository.kt:42` | (a) Map tab visible after a search (`AvailabilityScreen.kt:1322` -> `AvailabilityViewModel.kt:802-827`) | 1 request per search while the Map tab is selected. The Map tab is the default (`AvailabilityScreen.kt:832`, `rememberSaveable { ResultsTab.MAP }`), so in practice every search made from the map fetches it at once. INFERRED: the doc comment at `AvailabilityViewModel.kt:797-800` ("a map view the user never opens shouldn't cost an extra API call") describes laziness that the default tab mostly defeats. | In-memory key `loadedSightingsQuery` (`AvailabilityViewModel.kt:188`, checked `:805`, set `:811` **only on success**). Cleared by every `refresh` (`:865`). Lost with the ViewModel. No persistence. |
| `GET observations` (same request) | same | (b) Seasonal tab visible (`AvailabilityScreen.kt:1323` -> `AvailabilityViewModel.kt:836-860` -> `GetSeasonalPatternUseCase.kt:30`) | 1 request per search per Seasonal visit, **identical parameters to (a)** | Keyed separately by `loadedSeasonalPatternQuery` (`AvailabilityViewModel.kt:191`, `:839`, `:845`); does not read the Map tab's already-loaded sightings. |

### 2b. Open-Meteo forecast (`https://api.open-meteo.com/v1/forecast`, `data/remote/OpenMeteoClient.kt:6`)

Request shape: 2 daily variables + 6 hourly variables (`OpenMeteoApi.kt:42-46`,
`DAILY_VARIABLES`, `HOURLY_VARIABLES`), `past_days=14` (`OpenMeteoWeatherProvider.kt:93` ->
`domain/FruitingPatternAssumptions.kt:73`), `forecast_days=7` (`FruitingPatternAssumptions.kt:64`),
location = search centre. The parameters depend only on the region; month and filter do not enter.

| Use | Call site | Trigger | Per trigger | Existing caching / de-dup |
|---|---|---|---|---|
| Recent rainfall (Conditions card) | `AvailabilityViewModel.kt:929-947` -> `GetConditionsUseCase.kt:15` -> `OpenMeteoWeatherProvider.kt:51` | `refresh`, only when the selected month is the current month (`AvailabilityViewModel.kt:925`) | 1 | none |
| Today's forecast | `AvailabilityViewModel.kt:952-967` -> `GetTodaysForecastUseCase.kt:25` -> same | `refresh`, current month only | 1, **identical request to the row above** (the duplication is accepted in a comment: `AvailabilityViewModel.kt:948-951`, `GetTodaysForecastUseCase.kt:11-18`) | none |
| Trip windows | `AvailabilityViewModel.kt:985-1000` -> `GetTripWindowsUseCase.kt:23` -> same | **every** `refresh`, whatever month (`:981-984`) | 1, **identical request again** | none |

So one current-month search sends 3 identical forecast requests concurrently; one other-month search
sends 1. A month-chip tap on an unchanged region sends 1 or 3 more, all identical to the previous
ones, though the weather answer cannot have changed with the month.

### 2c. Open-Meteo historical archive (`https://archive-api.open-meteo.com/v1/archive`, `data/remote/OpenMeteoArchiveClient.kt:11`)

| Use | Call site | Trigger | Per trigger | Existing caching / de-dup |
|---|---|---|---|---|
| Fruiting-lag rainfall | `GetSeasonalPatternUseCase.kt:49` -> `OpenMeteoHistoricalWeatherProvider.kt:41` | Seasonal tab visible after a search, only if at least one sighting is dated (`GetSeasonalPatternUseCase.kt:33`) | 1 request; date range from the earliest dated sighting minus 21 days (`FRUITING_LAG_DAYS = 7..21`, `FruitingPatternAssumptions.kt:54`; `GetSeasonalPatternUseCase.kt:46`) through the latest. 1 variable (`precipitation_sum`). Unchunked by design (`OpenMeteoHistoricalWeatherProvider.kt:24-28`). | `loadedSeasonalPatternQuery` only, in memory, cleared by every `refresh`. Past rainfall never changes, but nothing keeps it. |

### 2d. Shared client facts (all three JSON hosts)

- One builder, `ApiClients.httpClient` (`data/remote/ApiClients.kt:23-37`): User-Agent interceptor,
  15 s connect and read timeouts, request logging (BASIC) in debug builds only. **No `.cache(...)`**:
  no HTTP response cache, so any `Cache-Control` the services send is ignored.
- `ApiClients.create` calls `httpClient(debug)` each time (`ApiClients.kt:47`), and it is called
  three times (`AppContainer.kt:183-185`), so the app holds three separate OkHttp clients with three
  connection pools. Minor; noted because a shared cache would need one client.
- No throttle, no request coalescing, no debounce on any of these calls. No handling of HTTP 429 or
  `Retry-After` anywhere in `data/`, `domain/` or `ui/` (grep). A failure is reported to the user
  and logged (e.g. `AvailabilityViewModel.kt:907`, `:934`). INFERRED from OkHttp's documented
  default, not set here: OkHttp retries silently on some connection failures
  (`retryOnConnectionFailure` defaults on); not on HTTP error codes.
- The only debounce in the ViewModel (`SEARCH_DEBOUNCE_MS = 300`, `AvailabilityViewModel.kt:1888`,
  used `:416`) is on the offline species index, which is not network.

---

## 3. Repeat calls

1. **Forecast fetched three times per current-month search, identical parameters**
   (`AvailabilityViewModel.kt:931`, `:954`, `:987`; all reach `OpenMeteoWeatherProvider.kt:51`).
   The provider comment at `OpenMeteoWeatherProvider.kt:42-47` says one request serves both entry
   points, which is true per call but not per search.
2. **Forecast re-fetched on a month-chip tap for an unchanged region** (`AvailabilityViewModel.kt:401-403`
   -> `refresh`), though the request has no month in it. Trip windows always (`:985`); the other two
   when the new month is the current one.
3. **The observations request is made twice for the same search** when the user visits both Map and
   Seasonal: `AvailabilityViewModel.kt:809` and `GetSeasonalPatternUseCase.kt:30`, same
   region/month/filter, same `per_page=200`.
4. **The same search re-run always goes to the network**, including tapping a recent search whose
   result is in the Room cache seconds old (`GetAvailabilityUseCase.kt:112`; by design,
   `AvailabilityViewModel.kt:1023-1027`). Each re-run also repeats rows 1-3 above.
5. **Map-tab in-flight gap.** `loadedSightingsQuery` is set only on success (`AvailabilityViewModel.kt:811`)
   and no in-flight marker exists, so leaving and returning to the Map tab while the first request is
   still running starts a second identical request (INFERRED from `:803-807`; the `LaunchedEffect`
   re-runs on `selectedTab` change, `AvailabilityScreen.kt:1321`). Same shape for Seasonal
   (`:839`, `:845`). After a failure, every return to the tab re-requests, with no backoff.
6. **species_counts and observations overlap.** Both are made for the same region/month/filter on
   every search from the Map tab. They answer different questions (ranked counts vs. individual
   records), so this is overlap of source, not an identical request.

---

## 4. Other hosts the app calls (named generically)

These are outside the dispatch's two named services; listed so the count is complete, not costed.

| Host (generic) | Where | Trigger | Caching |
|---|---|---|---|
| A community topographic raster tile server | `ui/map/Basemap.kt:123` | map library, per visible tile, on map view/pan/zoom | the map library's own ambient cache; the app sets no size (grep for ambient-cache calls found none). Size and expiry UNVERIFIED. |
| The community street-map raster tile server | `ui/map/Basemap.kt:131` | same | same |
| The map library's public demo glyph server | `ui/map/BasemapStyles.kt:341`, used by both styles (`:167`, `:194`) | label glyph ranges, per style load | same |
| The project's own tile worker (style JSON, TileJSON, vector tiles) | `map/OfflineStyle.kt:18`; `domain/OfflineMapRepository.kt:131` | offline-region download (user action), and rendering from the offline style | MapLibre offline database (persistent, `map/MapLibreOfflineMapRepository.kt`) |

All of these go through `map/MapHttpClient.kt:22-28` (app User-Agent, 20 requests per host, no
timeouts, no cache of its own; `:20`). The server worker's own upstream requests
(`server/pmtiles-worker/`) are server-side and not counted.

---

## 5. Rate limits

**Applied by the app:** none for the JSON APIs (§2d). The map client allows 20 concurrent requests
per host (`map/MapHttpClient.kt:24`); that is a concurrency cap, not a rate limit. The app does
identify itself on every request (`net/AppUserAgent.kt:206-209`).

**Open-Meteo, published** (read 2026-10-14 from open-meteo.com/en/terms and /en/pricing):
free non-commercial use is "600 calls / min", "5.000 calls / hour", "10.000 calls / day",
"300.000 calls / month"; "monthly limits are not enforced" until a usage portal exists. Calls are
weighted: "Requests for data covering more than 10 weather variables or extending over a period of
more than 2 weeks for a single location are considered multiple API calls", with the example "a
request for 2 weeks of data with 15 weather variables will be calculated as 1.5 API calls, while 4
weeks of data equals 3.0 API calls."
- The forecast request covers 21 days (14 past + 7 ahead) and 8 variables, so by that rule it
  counts as **more than one call each** (INFERRED; the exact factor is not stated for this shape).
- The archive request covers the span of the dated sightings, potentially years. The repo records a
  3,288-day request working (`OpenMeteoHistoricalWeatherProvider.kt:24-28`). By the published rule a
  request of that length counts as **many calls, plausibly in the hundreds** (ESTIMATE: 3,288 days /
  14 ≈ 235 if weight scales linearly with two-week blocks; the page's own "4 weeks = 3.0" example
  does not fit a linear rule, so the true factor is UNVERIFIED). Consistent with this, an earlier
  session exhausted the archive's daily limit by manual probing and got HTTP 429 "Daily API request
  limit exceeded" (`docs/audits/2026-10-01-readme-archive.md:1267-1283`). Whether the limits apply
  per device IP or per app is not stated on the pages read; per-IP is the usual reading for a
  keyless API (UNVERIFIED).
- Whether this app's use falls under the free non-commercial terms is not a question this audit
  answers.

**iNaturalist, published: UNVERIFIED from here.** Its recommended-practices and API-reference pages
returned HTTP 403 to the fetch tool, and the API documentation page renders without its text. The
repo carries a second-hand figure, "~1 req/sec, ~10k/day — from the swagger spec and prior
verification" (`docs/plans/mushroom-log-phase2-inaturalist-upload.md:102`; also
`docs/plans/mushroom-log.md:226`), not re-checked here. `docs/plans/maplibre-migration.md:282`
says the two services' limits "are already respected elsewhere in this app"; no code was found that
does so, beyond the low request volume per action.

---

## 6. Biggest repeat costs, ranked by estimated calls per typical session

**Estimate, with assumptions.** A typical session is assumed to be: one search by current location
in the current month from the Map tab; two month-chip taps to other months; one visit to the Seasonal
tab (on the last search); one tap on a recent search that is in the current month. No failures, no
tab flicking during loads. These assumptions are the planner's to replace; the arithmetic follows the
code paths above.

| Request | Count in that session | Distinct requests among them |
|---|---|---|
| iNaturalist species_counts | 4 (one per refresh) | 4 |
| iNaturalist observations | 5 (4 Map-tab + 1 Seasonal) | 4 |
| Open-Meteo forecast | 3 + 1 + 1 + 3 = 8 | 2 (two regions; ESTIMATE assumes the recent search is a different place; if it is the same place, 1) |
| Open-Meteo archive | 1 request | 1 (weighted, see §5) |

Ranked:
1. **Forecast triplication and month-tap refetch** (§3 items 1-2): about 6 of 8 forecast requests in
   that session are repeats, and each counts as more than one call by the service's own rule.
2. **Archive request weight** (§2c, §5): one request, but by the service's published counting
   plausibly the largest single cost in the session (tens to hundreds of calls, ESTIMATE), refetched
   on every new search + Seasonal visit, though historical rainfall is immutable.
3. **Re-running a known search always hits the network** (§3 item 4): every recent-search tap repeats
   1 species_counts + 1 observations + 1-3 forecast requests.
4. **Observations fetched twice per search when Seasonal is opened** (§3 item 3): 1 repeat per
   Seasonal visit, each a 200-record page.
5. **Map/Seasonal in-flight and failure refetch** (§3 item 5): zero in the assumed session; grows with
   tab flicking during slow loads or offline use.

Candidates (one line each, no design):
- Share one forecast request per region across the three forecast uses.
- Keep the forecast across month changes for the same region.
- Let the Seasonal tab reuse the Map tab's loaded sightings.
- Keep archive rainfall per location and date range, since it does not change.
- A freshness window on the Room search cache before going live.
- An in-flight marker for the Map and Seasonal fetches.
- An HTTP cache on the shared JSON client.

---

## 7. Could not determine

- **Real counts.** Nothing here was observed running. The debug build's BASIC request log
  (`ApiClients.kt:28-35`) would give a real per-session count on a device; not run (no device in scope).
- **iNaturalist's published rate limit** (pages unreadable from here; §5).
- **Open-Meteo's exact weighting** for a 21-day, 8-variable forecast request and for a multi-year
  archive request; and whether limits are counted per IP.
- **Typical archive span**: depends on how far back the 200 returned observations reach, which
  depends on the server's default ordering and on the place (the app passes no `order_by`; grep of
  `data/remote/` finds none). UNVERIFIED.
- **Whether the services send cacheable `Cache-Control` headers**: no request was sent.
- **Map tile request volume** per pan/zoom, and the map library's default ambient cache size and
  expiry: determined inside the library, not set by the app.
- **Whether Compose ever runs the tab `LaunchedEffect` twice for one search** (region and
  `searchSerial` change in the same synchronous call, `AvailabilityViewModel.kt:507-508`/`:869`).
  INFERRED harmless either way: a run before `refresh` sees the old `activeSearch` and returns at
  `:805`.

## Premises checked

- Base `4ade21f0` matches `origin/main` on the remote.
- The dispatch's backlog line ("iNaturalist and Open-Meteo call grouping and caching") fits what
  was found; no premise in the dispatch was found wrong.
- The comment at `AvailabilityViewModel.kt:797-800` (sightings fetched lazily so an unopened map
  costs nothing) is true of the mechanism but not of the default experience, because Map is the
  default tab (§2a). Recorded here, not changed.
