# Forager

[![CI](https://github.com/slayer8366/Forager/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/slayer8366/Forager/actions/workflows/ci.yml)

An Android app that ranks which species are worth looking for, in a chosen
region and month, based on how often people have historically logged them
there on [iNaturalist](https://www.inaturalist.org/). Despite the name, it's
not limited to fungi: search by broad category (Fungi, Plants, Lichens) or
by a specific species.

This is a historical-frequency ranking over real observation data, not a
weather-style forecast or a fitted model: the app deliberately doesn't claim
more certainty than the data supports. See `AvailabilityForecast` and
`PredictAvailabilityUseCase` in `domain/` for how the ranking is computed.

## How it works

1. Search controls live in a **navigation drawer**, opened from the tune
   icon in the app bar. You pick a region there — either "use current
   location" (device GPS/network location, with a radius slider) or manually
   entered latitude/longitude — and a month. The drawer keeps the map, which
   is the primary content, at full height; a one-line strip under the app bar
   ("Fungi · August · 15 km") says what the current search is while the
   controls are hidden. See `ui/availability/AvailabilityScreen` for why the
   controls are not stacked above the results.
2. You pick what to search for: the **Fungi**, **Plants**, or **Lichens**
   quick-filter chips, or a specific species by name (autocomplete over
   `GET /v1/taxa/autocomplete`). Lichens has no distinct top-level group on
   iNaturalist — it's approximated via the Lecanoromycetes class, which
   covers most lichen species, and labeled as an approximation in the UI.
   See `domain/model/TaxonFilter`.

   **Fungi excludes most lichens.** iNaturalist files lichens under its Fungi
   iconic taxon (it calls the group "Fungi Including Lichens"), so an
   unfiltered Fungi search returns them ranked among the mushrooms — in one
   real 15 km search, three of the top five species were lichens. Fungi
   therefore subtracts Lecanoromycetes via `without_taxon_id`, and the
   Lichens chip is how you ask for them. Because that class is an
   approximation of "lichen", this removes *most* lichens rather than all of
   them: taxa from other lichenized classes (Candelariomycetes,
   Verrucariales and others) can still appear in Fungi results. Plants
   excludes nothing, and a species you searched for by name is returned
   as-is — an exclusion never overrides an explicit species choice.
   iNaturalist ignores unrecognised query parameters instead of rejecting
   them, so a dropped `without_taxon_id` would fail silently;
   `scripts/verify-lichen-exclusion.sh` re-checks against the live API that
   it still does something.
3. The app queries iNaturalist's `GET /v1/observations/species_counts` for
   verifiable observations matching that filter within the radius, filtered
   to that month across all years.
4. Species are ranked by observation count, with the top species normalized
   to a relative-likelihood of 1.0.
5. The **Map** tab — the tab the app opens on — shows the searched region on
   a **topographic** basemap (see the basemap selector below), with a small
   dot per individual verifiable
   observation (`GET /v1/observations`): real reported sighting locations,
   not just the aggregate ranking. Dots rather than osmdroid's stock pins
   because a dense radius merges a few hundred pins into one unreadable
   mass, which throws away the density that is the signal here.
   Observations iNaturalist doesn't expose a location for (e.g.
   conservation-sensitive taxa) are left off the map rather than guessed at.
   Sightings are fetched lazily, only when the Map tab is opened, so
   browsing the ranked list alone doesn't cost the extra API call.
6. **Foraging areas**, on by default, groups those dots into the spots that
   have produced *repeatedly*, and the drawer's toggle switches the layer
   back off to read the raw observations. It is the default view because
   where observations bunch together across many years is the strongest
   signal in the dataset, and drawing every one as an identical dot throws
   it away. Grouping is
   [DBSCAN](https://en.wikipedia.org/wiki/DBSCAN) in pure Kotlin
   (`domain/Dbscan`), run over the sightings the Map tab already fetched —
   it makes no extra API call. DBSCAN rather than k-means because it takes
   a distance radius instead of a preset cluster count, and because it
   labels isolated points as noise: one observation 8 km from anything else
   is not a foraging spot and isn't promoted into one. Distances are true
   great-circle metres (`domain/GeoDistance`), never Euclidean arithmetic
   over raw lat/lng degrees, which would distort clusters east–west as
   latitude rises. Each area reports its observation count, distinct
   species count, and most recent observation year. The two thresholds —
   how close counts as "the same spot" and how many finds make a pattern —
   are labelled adjustable assumptions in
   `ClusterForagingAreasUseCase`, not data-derived facts.

   **On a compact (phone-width) window, the Maps tab is full-bleed**, with a
   bottom nav (List / Maps / Seasonal, replacing the old top tab row) and a
   right-edge floating icon stack over the map itself: fullscreen (hides the
   app bar and bottom nav, leaving only the map and the stack — tap the map
   or the icon again to bring chrome back), GPS locate-me (recenters the map
   on the device's live location; distinct from "use current location" above,
   which sets the *search region* instead — the two never share state), the
   topo/regular toggle described below, a second entry into this same search
   drawer, and an add (+) button. The add button opens the exact same
   "Plan a trip / Log a find" chooser the map's long-press gesture opens — it
   sets the identical pending-location state the gesture sets, not a parallel
   handler, so the two entry points can't drift apart — centred on the
   current search region rather than requiring its own GPS fetch. A compass +
   GPS-altitude strip sits at the top of the map (elevation reads `null`,
   shown as "unavailable," whenever the location fix didn't report one — e.g.
   a network-based fix — never a guessed value); the compass reads the
   device's rotation-vector sensor, falling back to accelerometer +
   magnetometer, behind an owned `domain/CompassProvider` interface so it's
   testable without real hardware. The foraging-areas toggle and summary
   panel now float as a card over the map instead of sitting in the space
   below it, since full-bleed leaves no reserved space there — a change made
   with the project owner once the full-bleed layout made the old fixed-height
   layout impossible without giving space back. **Medium/expanded windows
   (tablets, landscape, foldables) are unaffected** — they keep the permanent
   drawer + side-by-side List/Map layout described below, none of the above.
   See `ui/availability/AvailabilityScreen`'s `CompactMapTab` and
   `docs/plans/map-redesign.md`.

   **Which basemap draws the tiles is two separate decisions with two
   different lifetimes.** Which *service* to use — **OpenStreetMap** (the
   default) or **USGS** — is occasional, so it lives in **Settings ▸ Choose
   Maps Service**, reached from the sticky entry at the bottom of the search
   drawer. Which *mode* that service is in — topo or regular — is a
   during-the-walk decision made often, so it's a quick-fire icon (on a
   compact window, the third icon in the floating stack above; on medium+
   windows, still overlaid on the map's own top-right corner) rather than
   buried in a menu: "if a map has two modes, toggle the two." OpenStreetMap's two modes are OpenTopoMap and
   the standard OSM street map; USGS's are USGS Topo and USGS Imagery.
   Switching service never resets the mode — leave the icon on regular mode
   under OpenStreetMap and switch the service to USGS, and the map lands on
   USGS Imagery, not USGS Topo. All four tile sources come out of the pinned
   osmdroid's own `TileSourceFactory`, so there is no API key and no
   hand-written URL template. See `ui/map/MapService` and `ui/map/Basemap`.

   This replaces an earlier design (still visible in this project's git
   history) where all four basemaps sat in one flat dropdown in the app bar,
   defaulting to USGS Topo. **The default changed to OpenStreetMap** for the
   same reason USGS was never a hardcoded default there either: **USGS
   National Map covers the United States only**, and an opening basemap that
   is blank for every user outside the US is a worse trade for a first launch
   than it is for a browsing choice the user can already see and change. USGS
   Topo — the better read for a wooded search — is still one tap away in
   Settings. The two alternatives to stating the coverage limit outright are
   recorded in `Basemap`'s doc comment: detecting coverage and falling back
   silently (`CLAUDE.md` forbids an unlogged fallback, and the failure mode
   isn't even detectable — the service returns HTTP 404 outside the US, which
   is indistinguishable from a network error), and guessing from device locale
   or GPS.

   Zoom ceilings differ per basemap and are applied explicitly: USGS stops at
   15, OpenTopoMap at 17, OpenStreetMap at 19. This fixed a bug that predated
   the selector — osmdroid derives a `MapView`'s ceiling as the maximum across
   its *module providers*, and `MapTileProviderBasic` stacks a
   `MapTileApproximater` that claims 29, so the app previously let you zoom
   ten levels past what OpenStreetMap actually serves, into upscaled blur.
   `scripts/verify-usgs-basemap.sh` is the evidence behind the USGS numbers:
   it fetches real tiles and checks the response bodies really are images, so a
   200 carrying an error page can't pass for coverage. It also records that the
   service's own metadata advertises tile levels up to **23** while tiles stop
   at 16 — the app trusts the observed ceiling, per `CLAUDE.md`'s rule that a
   reported capability range is not an operating limit.

   **Settings ▸ Offline Maps downloads USGS Topo tiles for a region you pick,
   for offline use.** Reached via a submenu of its own (a "Offline Maps" row
   inside Settings, one tap below "Choose Maps Service"), because it holds an
   interactive map: rather than typing latitude/longitude, you long-press the
   map shown there to set the download's centre point, the same long-press
   gesture used elsewhere in this app to drop a planned-trip pin — a marker
   with the radius in its snippet confirms the pick. The download always
   fetches USGS Topo specifically, unconditionally: it doesn't read the
   quick-fire icon's live mode or which map service is selected for ordinary
   browsing, both of which were considered and dropped as needless coupling
   for a feature that only ever needs one fixed source. Downloaded tiles are
   stored under the app's private files directory, not the cache directory
   ordinary browsing uses, so neither an OS cache-clear nor ordinary map
   panning can evict a region the user explicitly asked to keep. See
   `map/OsmdroidOfflineMapRepository`.

   **Why USGS only, even though the feature no longer visibly gates on the
   selected map service.** OpenStreetMap's and OpenTopoMap's tile providers
   both prohibit bulk/prefetch downloading in their usage policies, and
   USGS's own low zoom ceiling (15) keeps a region download a practical size
   regardless. osmdroid's pinned artifact encodes the OpenStreetMap half of
   that directly — the standard map's `TileSourcePolicy` sets `FLAG_NO_BULK`,
   citing `operations.osmfoundation.org/policies/tiles/` — but carries no such
   flag for OpenTopoMap, so that half rests on a web search of the same
   domain and `opentopomap.org`'s own usage text rather than a fetch of
   either page: this environment's egress proxy blocks both directly. Worth a
   primary-source spot-check before relying on it further. The enforcement
   itself is structural rather than a live UI gate: `OfflineMapRepository`
   only ever downloads USGS Topo and accepts no other tile source as a
   parameter, so there is nothing for the UI to steer away from — see that
   interface's doc comment for the full citation trail.

   **The numbering is a visiting order, not a walking route.** Areas are
   numbered by greedy nearest-neighbour from the search centre — head for
   the nearest area you haven't done yet — and the connectors between them
   are drawn *dashed* because they are straight lines between area centres
   and nothing more. This project has no trail data, no terrain, no
   land-ownership data and no path graph, only scattered coordinates over
   raster tiles, so a walking path is a capability it does not have; a
   solid line implying one could route you across a river, a motorway, a
   cliff, or private land. Per `CLAUDE.md` an unsupported capability is
   reported as unsupported rather than given a plausible-looking value, so
   the app ships the order and says plainly, on screen, that it is not a
   route. The ordering is also not optimal or shortest — greedy
   nearest-neighbour is neither, and it isn't described as either. If no
   group of observations meets the density threshold, the app says so
   explicitly instead of relaxing the threshold until something appears.
7. When the selected month is the current month, a **Current Conditions**
   card at the top of the **List** tab shows recent observed rainfall for
   the region (total precipitation and days since the last significant
   rain), pulled from
   [Open-Meteo](https://open-meteo.com)'s forecast API. iNaturalist has no
   answer for "has it actually been wet lately here" — historical
   observation frequency says nothing about this week. This is raw current
   data shown next to the ranking, not fused into it: there's no measured
   correlation in this codebase between rainfall and observation upticks,
   so `AvailabilityEntry.relativeLikelihood` is never adjusted by it (see
   `GetConditionsUseCase`'s doc comment and `CLAUDE.md`'s rule against
   unproven correction logic). The card is hidden entirely when browsing a
   different month, since today's rain says nothing about typical
   conditions in some other month.
8. A **Seasonal** tab tests the rain-to-fruiting-lag rule of thumb quoted in
   `FruitingPatternAssumptions.FRUITING_LAG_DAYS` (7–21 days after a soaking
   rain) against real data, instead of leaving it as unmeasured field lore.
   `GetSeasonalPatternUseCase` fetches the region's dated sightings
   (`GetSightingsUseCase`, the same source the Map tab uses) and the
   historical rainfall behind them
   (`HistoricalWeatherProvider`/`OpenMeteoHistoricalWeatherProvider`, over
   Open-Meteo's *historical archive* API — a different host,
   `archive-api.open-meteo.com`, from the forecast API the rest of the app
   uses), padding the fetch backward from the earliest sighting by
   `FRUITING_LAG_DAYS.last` days so a soaking event just before it is still
   visible. `ComputeFruitingLagDistributionUseCase` then finds, for each
   dated sighting, the *nearest* preceding soaking event (reusing
   `ComputeTripWindowsUseCase.findSoakingEvents` — never a second copy of
   that detection) and buckets the lag into 0–6, 7–21 (`FRUITING_LAG_DAYS`
   itself, highlighted), 22–35, 36+, or "no preceding event." The result is
   drawn as a hand-rolled Compose `Canvas` bar chart — no charting
   dependency, the same choice already made for `Dbscan`/`GeoDistance`/
   `MgrsConverter` — with the exact counts also printed as text, since
   Robolectric cannot render `Canvas` content and the honesty this feature
   depends on cannot live in unmeasurable pixels alone.

   **This tests one named hypothesis; it does not change any ranking.** The
   Seasonal tab never writes to `AvailabilityEntry.relativeLikelihood` or
   any other ranked-list state — same restraint `TripWindow` and
   `PredictAvailabilityUseCase` already apply, for the same reason
   (`CLAUDE.md`'s rule against unproven correction logic). Scope follows the
   existing category chip with no new picker UI: an `IconicCategory` search
   (Fungi, Plants) pools every matched species into one histogram, and a
   `SpecificTaxon` search (Lichens, or a species picked from the search box)
   scopes to that one taxon — both fall out of passing the existing
   `TaxonFilter` straight through to `GetSightingsUseCase`, the same call the
   Map tab already makes.

   Every number here is labelled as an estimate, not a finding: the tab
   always shows the sample size, an "estimate from N observations, not a
   guarantee" line, how many of iNaturalist's own reported total were
   actually fetched (`SightingsPage.totalResults`, not a
   size-vs-page-size-cap guess — see below), and an observer-effort caveat
   that raw iNaturalist counts reflect how many people were looking, not
   only whether the species was present. Sightings with no recorded
   observation date are excluded from the histogram and reported as such
   rather than counted as zero-lag or silently dropped.

   **`SightingsPage.totalResults`.** `INaturalistApi.getObservations` is a
   single unpaginated request capped at 200 results, and
   `INaturalistMushroomRepository` additionally drops any observation
   iNaturalist gave no mappable position for. Comparing the returned
   sighting count against the 200 cap to guess whether a search was
   truncated is wrong in the direction that matters least safely — a
   200-result page that loses 30 to missing coordinates yields 170, and
   `170 >= 200` reads as "complete" when it is not. `MushroomRepository.getSightings`
   therefore returns `SightingsPage`, carrying iNaturalist's own
   `total_results` (already parsed in `ObservationsResponseDto`, previously
   unused) alongside the filtered list, so the Map tab, the List tab and this
   feature can all say "based on N of iNaturalist's own M" instead of a
   guess.

9. **Searches you have already run work without a connection.** Every ranked
   list that comes back is written to a local Room table, and if a later
   search for the same region, month and category can't reach iNaturalist,
   the saved copy is shown instead — under an "Offline — showing results
   saved 3 hours ago" banner, never silently in place of a live result
   (`CLAUDE.md`: a fallback is reported as a fallback). The last five
   distinct searches are kept, least-recently-used first, and the drawer's
   **Recent searches** section re-runs any of them in one tap. A tap there
   goes through the ordinary search, so with a connection it returns fresh
   results and only falls back to the stored copy when the live call fails.

   **Only the ranked list is cached.** Current Conditions and Trip Windows
   are deliberately not: both are "as of today" readings (see
   `GetConditionsUseCase` and `OpenMeteoWeatherProvider`), and replaying a
   stored rainfall total offline would present a reading from days ago as
   the current one. A historical-frequency ranking has no such problem —
   last week's copy is the same answer today — and it is labelled with its
   age either way. Matching is exact equality on region + month + filter:
   a search 400m away is a different search and is not answered with this
   one's results. See `domain/SearchCacheRepository` and
   `domain/GetAvailabilityUseCase`.
10. **A mushroom log lets you record a field find as a structured
    observation — Phase 1 (local only) of this feature; see below for what's
    deferred.** Reached from the drawer's **Mushroom Log** entry (a sticky
    row above Settings, the same reach pattern), and a new find is started
    by long-pressing the map — which now asks "Plan a trip" or "Log a find"
    instead of going straight to the trip dialog, so the existing gesture
    grows a second option rather than the app growing a second gesture for
    the same "I'm pointing at a place" action.

    **The app never identifies the mushroom.** No species suggestion,
    candidate list, "likely," or confidence score anywhere in this feature —
    a stated safety property from the project owner, not a scope cut: the
    field key this data set separates includes species that can be lethal.
    `ownIdentification` is the one free-text field that looks adjacent to
    this, and it is explicitly the forager's own claim, never app-generated
    — see `domain/model/MushroomLogEntry`'s doc comment.

    **Three states, not two, for every recorded characteristic.**
    `domain/model/Observed<T>` (`Recorded`/`NotObserved`) is for
    characteristics that necessarily have *some* value once looked at (cap
    shape, gill attachment); `domain/model/Feature<T>`
    (`Present`/`Absent`/`NotObserved`) is for characteristics where absence
    is itself diagnostic (a volva, an annulus, latex, cap decorations) — "no
    volva" and "didn't check the base" are different facts, and a nullable
    field would collapse them into the same fabricated-plausible-value
    failure `CLAUDE.md` forbids. Fields that only make sense once a prior
    choice is made — a gill's attachment/spacing/edge, once the hymenophore
    is known to be gills; a stipe's interior/base, once it's known to be
    present — are nested inside the sealed type that choice selects
    (`HymenophoreDetails`, `StipeDetails`), so an entry recorded as pores has
    no field a gill attachment could be written into. This is enforced by
    the type system, not a validation pass; see `ThreeStateModelTest`,
    which inspects the compiled classes' own declared fields to prove it at
    runtime too, not just at compile time.

    **An entry is created incomplete and finished later, on purpose.** A
    spore print is read overnight, so unlike `PlannedTrip` (which
    deliberately has no rename-after-creation flow), a log entry has a real
    edit path: every field change autosaves immediately (see
    `ui/log/MushroomLogViewModel`'s doc comment for why — a field app has no
    guaranteed graceful exit), and any field still `NotObserved` renders
    with an explicit "Not recorded" label rather than reading as blank or as
    an absent value — see `MushroomLogNotObservedRenderingTest`.

    **Photos: camera capture or gallery pick, no new heavy dependency.**
    `ActivityResultContracts.TakePicture` with a `FileProvider`-issued URI
    for the camera (`photo/CameraCaptureFiles`), and
    `ActivityResultContracts.PickVisualMedia` — the modern photo picker,
    needing no storage permission — for the gallery. Both sit behind the
    owned `PhotoStore` interface, so domain code and the ViewModel never
    name a `Uri` or an `ActivityResultContract`; `photo/FilePhotoStore`
    copies bytes into `context.filesDir/photos/`, never `cacheDir`, since
    these are user-created photos that must survive an OS cache-clear — the
    same reasoning already applied to downloaded offline-map tiles.

    **A real Room migration, not `fallbackToDestructiveMigration()`.**
    `ForagerDatabase` moved from version 3 to 4 to add
    `mushroom_log_entries`/`log_photos`, and — unlike every earlier bump on
    this database — this one ships a hand-written `Migration` and flips
    `exportSchema` to `true`. See `ForagerDatabase`'s doc comment for why:
    field notes are irreplaceable, unlike the search cache or a handful of
    test planned trips. `MushroomLogMigrationTest` builds a real version-3
    database (from the same production entity classes, not a hand-copied
    schema), migrates it, and asserts the pre-existing rows survive intact
    and the new tables are actually usable afterward.

    **Not implemented in this phase: uploading to iNaturalist.** The model
    carries `LogSyncState` (`Draft`/`Uploading`/`Uploaded`/`Failed`) from the
    start per the project owner's decision not to retrofit it later, but
    Phase 1 only ever constructs `Draft`. Upload — OAuth2 + PKCE auth,
    resumable background upload respecting iNaturalist's rate limit, and the
    `observation_field_id` lookups needed to map recorded characteristics
    onto real iNaturalist observation fields — is written up as a design in
    `docs/plans/mushroom-log.md` for a follow-up PR, and is blocked on the
    app owner registering an iNaturalist OAuth application (client ID +
    redirect URI), which only they can do.

## Project layout

- `data/remote/` — the only code that speaks Retrofit/iNaturalist's or
  Open-Meteo's wire formats (`INaturalistApi`, `OpenMeteoApi`,
  `OpenMeteoArchiveApi`, DTOs, `INaturalistClient`, `OpenMeteoClient`,
  `OpenMeteoArchiveClient`). `OpenMeteoArchiveApi`/`OpenMeteoArchiveClient`
  are separate from the forecast pair because the historical archive API is
  served from a different host (`archive-api.open-meteo.com`, not
  `api.open-meteo.com`) with its own request/response shape
  (`HistoricalPrecipitationResponseDto`, not reused from the forecast DTO —
  see that file's own doc comment for why).
- `data/repository/` — maps the iNaturalist API onto the domain-owned
  `MushroomRepository` interface, including parsing iNaturalist's
  `"lat,lng"` location string and `observed_on` date; the Open-Meteo
  forecast API onto `WeatherProvider`/`TripPlanningWeatherProvider`
  (`OpenMeteoWeatherProvider`); the Open-Meteo historical archive API onto
  `HistoricalWeatherProvider` (`OpenMeteoHistoricalWeatherProvider`); and
  Room onto `PlannedTripRepository`, `SearchCacheRepository`
  (`RoomSearchCacheRepository`, which owns the five-entry LRU and is the
  only place a cached row meets an `AvailabilityForecast`), and
  `MushroomLogRepository` (`RoomMushroomLogRepository`, the only place
  `MushroomLogEntryEntity`/`LogPhotoEntity` meet `domain/model/MushroomLogEntry`
  — its `toEntity`/`toDomain` functions are where the column encoding
  described below is actually applied).
- `data/local/` — the Room layer: `ForagerDatabase` and, per table, an
  entity and a DAO. `CachedSearchEntity` holds one cached ranked list —
  its `key` column encodes region + month + filter and *is* the match
  rule — with the ranked entries serialized by `CachedSearchPayload`'s
  `@Serializable` DTOs. Room annotations and serialization stay here,
  never in `domain/`. `CachedSearchDao`'s two `@Transaction` methods are
  what make "write then evict" and "read then mark used" atomic.
  `MushroomLogEntryEntity`/`LogPhotoEntity` hold the mushroom log's two
  tables (real columns per field, not one serialized blob — see that
  entity's doc comment for how `Observed`/`Feature` fields and sealed
  choices map onto columns); `MushroomLogDao`'s `@Transaction` methods keep
  an entry's row and its full photo set in sync the same atomic-write
  pattern `CachedSearchDao` uses. `Migrations.kt` holds `MIGRATION_3_4`,
  the real hand-written migration those two tables shipped with.
- `domain/` — pure Kotlin: `Region`, `LatLng`, `SpeciesObservationCount`,
  `Sighting`, `SightingsPage`, `TaxonFilter`, `TaxonSearchResult`,
  `AvailabilityForecast`, `ConditionsSummary`, `ForagingArea`/`ForagingAreas`,
  `FruitingLagDistribution`, `GeoDistance` (including its point-radius
  `boundingBox` helper, used by the offline-map region picker),
  `GeoBoundingBox`, `Dbscan`, `PredictAvailabilityUseCase`,
  `GetAvailabilityUseCase` (the live-then-cached-fallback decision,
  returning an `AvailabilitySearchResult.Live`/`.Cached` or the original
  failure unchanged), `GetSightingsUseCase`, `GetRecentSearchesUseCase`,
  `SearchTaxaUseCase`, `GetConditionsUseCase`, `ClusterForagingAreasUseCase`,
  `ComputeFruitingLagDistributionUseCase`, `GetSeasonalPatternUseCase`,
  `OfflineMapRepository`/`OfflineMapInfo` (always resolves to USGS Topo — no
  style parameter), and the
  `MushroomRepository`/`LocationProvider`/`WeatherProvider`/`HistoricalWeatherProvider`/`SearchCacheRepository`/`CurrentTimeProvider`/`CompassProvider`
  interfaces (`CompassProvider` is the map redesign's addition — see
  `sensor/` below). No Android imports, so it's unit-testable headless (see
  `app/src/test/`). `CurrentTimeProvider` is why: the cache's LRU stamps
  and the relative times rendered from them are injected rather than read
  off `System.currentTimeMillis()`, so both are assertable. The mushroom
  log's model lives here too: `Observed`/`Feature` (the three-state rule —
  see the "How it works" entry above), `MushroomLogEntry` and its seven
  section types (`CapSection`, `HymenophoreSection`/`HymenophoreDetails`,
  `StipeSection`/`StipeDetails`, `VeilSection`, `ContextFleshSection`,
  `SporePrintSection`/`SporePrint`/`SporePrintColor`,
  `HostSubstrateSection`/`Association`), `LogPhoto`, `LogSyncState`,
  `PhotoSource` (the opaque marker `PhotoStore` takes — see `photo/` below),
  and the `MushroomLogRepository`/`PhotoStore` interfaces plus their use
  cases (`GetMushroomLogEntriesUseCase`, `CreateMushroomLogEntryUseCase`,
  `SaveMushroomLogEntryUseCase`, `DeleteMushroomLogEntryUseCase`,
  `AddPhotoToLogEntryUseCase`/`RemovePhotoFromLogEntryUseCase`, the latter
  two the domain-level "persist/delete then attach/detach then save"
  ordering — see `AddAndRemovePhotoUseCaseTest`).
- `location/` — the one place that touches `android.location` directly,
  behind the `LocationProvider` interface.
- `map/` — parallel to `location/`: the one place that touches osmdroid's
  `CacheManager` and the filesystem directly, behind the `OfflineMapRepository`
  interface. `OsmdroidOfflineMapRepository` downloads tiles for a picked
  region into a persistent store under the app's private files directory
  (not the cache directory `SightingsMap`'s ordinary browsing uses, so
  neither an OS cache-clear nor ordinary panning can evict it) and records
  what's downloaded in a small sidecar file rather than a Room table, read
  back by `getStatus()`; `PersistentTileWriter` is the hand-rolled
  `IFilesystemCache` that writes there instead of through either of
  osmdroid's own writers, both of which resolve their storage path from a
  process-wide `Configuration` singleton the browsing map already claims.
- `photo/` — parallel to `location/`/`map/`: the one place that touches
  `ActivityResultContracts`/`Uri`/`FileProvider` directly, behind the
  `PhotoStore` interface. `ContentUriPhotoSource` is the real,
  `Uri`-carrying `PhotoSource`; `FilePhotoStore` copies bytes from it into
  `context.filesDir/photos/`; `CameraCaptureFiles` issues the
  `FileProvider` URI a camera capture writes into (`filesDir/captures/`, a
  scratch handoff area distinct from the persisted photos).
- `sensor/` — parallel to `location/`/`map/`/`photo/`: the one place that
  touches `android.hardware.SensorManager` directly, behind the
  `CompassProvider` interface. `AndroidCompassProvider` prefers the fused
  rotation-vector sensor and falls back to accelerometer + magnetometer,
  emitting `null` (not a stale or guessed heading) when neither is
  available — the map redesign's compass/elevation strip.
- `ui/availability/` — `AvailabilityViewModel` and the Compose screen: a
  `ModalNavigationDrawer` holding every search control over a map-first
  content area with the List/Map/Seasonal tab switch, plus three further
  drawer panels (`DrawerPanel.Settings`, `DrawerPanel.OfflineMaps`,
  `DrawerPanel.Log`) reached from sticky entry rows at the bottom of the
  search panel. `AvailabilityScreen`'s doc comment records why the controls
  are in a drawer and what was rejected; its `drawerPanel` state doc
  comment covers the panel switch. The search panel's three collapsible
  sections are Recent searches, Advanced search and Trip Planner, in that
  order; `SearchControls` records why the picker is first and a section of
  its own. The List tab's offline banner lives here too, and the Seasonal
  tab's hand-rolled `Canvas` bar chart is `FruitingLagChart`, in the same
  file. `MapTab`'s long-press opens `LongPressActionDialog` — "Plan a trip"
  or "Log a find" — before either the existing `TripDatePickerDialog` or the
  mushroom log's own start-entry flow runs; `MapTab` itself, and the
  `ModalNavigationDrawer`/tab-switch description above, is now
  medium/expanded (tablet, landscape, foldable) width only —
  `currentWindowWidthClass()` (`ui/adaptive/WindowWidthClass`) is what
  branches on it, a `PermanentNavigationDrawer` plus side-by-side
  `CombinedResultsPane` (List and Map together, the M3 "reveal" pattern) at
  that width instead. On a compact window, `CompactMapTab` is the map redesign's
  full-bleed replacement: same long-press flow, plus the right-edge
  `MapIconStack`, the `CompassElevationStrip`, and `ForagingAreasOverlay`
  floating over the map instead of sitting below it (see "How it works"
  above and `docs/plans/map-redesign.md`). `ForagerBottomNav` replaces the
  compact-only top tab row; `LocateMeStatus` (in this same package) is the
  icon stack's GPS/locate-me state, deliberately independent of
  `AvailabilityUiState.locationPermissionDenied`, which belongs to the
  unrelated "use current location for search region" control.
- `ui/map/` — `MapSlot`, the seam the screen fills instead of naming
  osmdroid directly (the `MushroomRepository` pattern applied to the UI
  layer, so the screen can be composed in a test without starting tile
  threads); `SightingsMap`, the osmdroid `MapView` wrapped for Compose,
  including the dot marker for individual observations and the numbered
  foraging-area markers with their dashed order connectors;
  `ForagingAreaLabels`, which holds the single wording of
  the "not a walking route" disclaimer so the on-map info window and the
  on-screen caption can't drift apart; `Basemap`, the same
  own-the-vendor-boundary idea one level down — the basemap catalogue is pure
  Kotlin (labels, coverage limits, zoom ceilings, attribution, no osmdroid and
  no Compose), and `BasemapTileSources.tileSourceFor` is the only place it
  becomes an `ITileSource`; and `MapService`, which groups those four
  `Basemap`s into the two services (OpenStreetMap, USGS) Settings' "Choose
  Maps Service" picks between, each with a topo/regular mode the map's own
  quick-fire icon toggles.
- `ui/log/` — `MushroomLogViewModel` and the log's Compose UI, kept in its
  own package rather than folded into `AvailabilityScreen.kt` alongside
  Settings/OfflineMaps — see `LogPanel`'s doc comment for why. `LogPanel` is
  the drawer destination itself (list vs. detail is `MushroomLogUiState.editingEntry`
  being null or not, not a separate nav enum); `LogEntryListScreen`/`LogEntryDetailScreen`
  are the two screens; `LogFieldEditors.kt` holds the reusable
  `Observed`/`Feature` chip-editors every section builds from
  (`ObservedEnumField`, `FeatureEnumField`, `CapDecorationsField`,
  `FeatureTextField`) plus `NotRecordedIndicator`, the one place the
  "reads as unrecorded" text lives; `LogSectionEditors.kt` holds the seven
  per-section editors, including the sealed-choice pickers
  (`HymenophoreEditor`, `StipeEditor`, `HostSubstrateEditor`) that make
  choosing a variant the only way its sub-fields come into existence.

These boundaries follow `CLAUDE.md`.

## Building

Requires the Android SDK (`compileSdk`/`targetSdk` 37, `minSdk` 26) and JDK
17+. Run `scripts/setup-android-sdk.sh` to install it, or point
`ANDROID_HOME` at an existing SDK install and create `local.properties`:

```
sdk.dir=/path/to/android-sdk
```

Then:

```sh
./gradlew assembleDebug       # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest   # runs the headless domain, ViewModel and layout tests
```

`assembleDebug` is followed by `verifyNothingTestOnlyReachesTheApk`, which
opens the built APK and fails the build if any test-only class or manifest
entry is inside it. The layout tests below need Compose UI Test and
Robolectric on the unit-test classpath; that task is what makes "these are
test-only" a checked fact about the artifact rather than a claim about the
Gradle configuration.

The layout tests run under Robolectric, which downloads an `android-all` jar
from Maven Central on first use.

The Gradle wrapper (`gradlew`) downloads its own Gradle distribution on
first run.

## Continuous integration

Every push to `main` and every pull request runs
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) on a clean Ubuntu
runner: it provisions the SDK with `scripts/setup-android-sdk.sh` — the same
script the section above tells you to run, so CI is a regression test for the
documented setup path — then builds the debug APK and runs the unit tests.
Either one breaking fails the run. Before this workflow existed the project
had no automated checks at all, and every merge to `main` was green on the
word of whoever last ran the build locally.

Two things are checked on the artifact rather than on the build
configuration, because the artifact is what gets installed:

- The APK's `versionCode`/`versionName` are read back with `aapt2 dump
  badging`, and the run fails if the build fell back to an `UNVERSIONED-*`
  identity or if the versionCode doesn't equal the checkout's commit count.
  CI checks out with `fetch-depth: 0` for that reason: `actions/checkout`
  clones shallow by default, and a shallow clone yields an APK that reports
  versionCode 1 and cannot install over a real build.
- The test summary fails the run if no tests ran or if any test was
  skipped — a quietly dropped Robolectric layout test would otherwise leave
  a green tick on a run that measured nothing. Per-suite test counts are
  printed to the run's summary page.

`assembleDebug` also carries `verifyNothingTestOnlyReachesTheApk` (see
above), so the test-only-code check runs on every push and PR too.

### Build artifacts

Each run publishes two artifacts, downloadable from **Actions** → the run →
the **Artifacts** box at the bottom of the run summary:

- `app-debug-apk` — the `app-debug.apk` that run built, so a build is
  obtainable without anyone building one by hand. Check its version in the
  drawer footer or with `aapt2 dump badging`; see "Which build am I
  running?" below.
- `unit-test-report` — the HTML test report and the JUnit XML it was
  generated from, uploaded even when the tests fail.

GitHub keeps both for 90 days.

## iNaturalist API access

Run `scripts/verify-inaturalist-access.sh` to confirm the environment can
reach `www.inaturalist.org` and `api.inaturalist.org`. No API key is
required for the read-only endpoints this app uses.

## Basemap tile access

Run `scripts/verify-usgs-basemap.sh` to re-check what the USGS National Map
services actually do: that they serve real image tiles for a US location at the
zooms the app allows, that they stop above zoom 16 and outside the United
States, and what tile levels their own metadata advertises. No API key is
required — USGS National Map is public domain — and none of the basemaps this
app offers needs one.

## Not yet verified

The app builds, links resources, and its unit tests pass, but nothing has
been *rendered* on a device or emulator — this environment has no hardware
virtualization (`/dev/kvm`), so the Android emulator isn't usable here.
Installing to a real device or an emulator on a machine with KVM is still
the verification step for anything about appearance. Layout geometry is a
separate question and is now measured headlessly; see below.

The foraging-area map layer **has** now been seen once, on a physical
phone, and two things came out of that. The connectors do render as
visibly dashed at the zoom a 15 km search opens at, which is the part
carrying the honesty burden — that much is confirmed. But the map was also
painting outside its
own rectangle: tiles over the tab row and over the caption below it, and a
dashed connector running up into the app bar. Nothing clipped the hosted
`MapView` to its Compose slot, and osmdroid draws beyond its viewport on
purpose (whole edge tiles; polyline geometry out to 2.2x the view's
half-diagonal) because it assumes the host clips. `SightingsMap` now sets
`Modifier.clipToBounds()` on the `AndroidView` and records the mechanism.

**The clip itself has not been re-checked on hardware.** It is reasoned
from the osmdroid and Compose sources, not observed. Still unchecked with
it in place: that tiles now stop at the map's edges, that a connector to an
area near the edge of the radius is cropped there rather than escaping, and
that no numbered marker the panel lists is left unreachable by panning.

The map-first layout's **geometry** is now measured, not just reasoned
about. `AvailabilityScreenLayoutTest` composes the real screen under
Robolectric across three device configurations — a small dense phone, the
same phone at a doubled font scale, and a larger phone — and reads back the
bounds Compose actually assigned. What that establishes: the map's slot gets
316dp of 640dp, 247dp of 640dp and 571dp of 891dp respectively; its top
lands exactly on the tab row's bottom edge rather than above it; the
visiting-order caption starts below the map's bottom edge; the Conditions
card is on screen with non-zero area on the List tab; and every drawer
control, plus the build-identity footer, is reachable and displayed. Those
tests were checked against the original defect — the control stack put back
in the unscrolled content column — and the map slot measured 0dp on all
three configurations, so they fail when the bug is present.

The map inside that slot is **stubbed** in those tests. They prove the
screen hands the map the right box; they prove nothing about what osmdroid
paints in it. The clip above is therefore still unverified, and so is
anything about rendering.

The **offline search cache** is verified headlessly and not on hardware. What
is measured: the Room round trip, the five-entry LRU and its eviction order
against a real in-memory database (`RoomSearchCacheRepositoryTest`); the
live/cached/failed decision and that a cache miss returns the original
exception object (`GetAvailabilityUseCaseTest`); the ViewModel state a
fallback sets (`AvailabilityViewModelOfflineCacheTest`); and that the offline
banner and the recent-searches rows are on screen with the text they claim,
under a fixed clock (`AvailabilityScreenOfflineCacheTest` — checked against
their own absence: removing the banner fails two of those tests, removing the
picker section fails three).

What that does **not** establish, and only a device can: that a real loss of
connectivity produces the failure this falls back on (every failure here is
an injected `IOException`, not a switched-off radio); that the cache survives
the app being killed and restarted, since every test database is in-memory;
that the schema-3 destructive fallback behaves on an install that already has
a version-2 database; and how any of it looks — the banner's colour against
the ranked list, and whether five entries in the drawer's new section read
well at a large font scale. `RoomSearchCacheRepository.save`'s
degrade-and-log path for a failed *write* is also untested, unlike its two
read paths: see the note in `RoomSearchCacheRepositoryTest` for why closing
the database was rejected as the provocation.

Still unchecked, and only a device or emulator can answer it: whether the
drawer's open/close gestures behave (swipe-to-open is disabled on purpose so
a horizontal drag over the map pans it instead), whether the `Scaffold`
insets actually keep content clear of the navigation bar, and how the small
dot markers for individual observations look at a dense radius.

### The compact map redesign (full-bleed map, bottom nav, icon stack, compass/elevation strip), specifically

This environment started with no Android SDK at all (`sdk.dir`/`ANDROID_HOME`
unset), unlike the emulator gap below, which is a hardware limitation
(`/dev/kvm` missing) rather than a missing tool. Command-line tools,
platform 37, and build-tools 37 were fetched from Google's own repository
and installed into the session for this task, so — unlike that gap —
**this one is not merely reasoned about: `./gradlew testDebugUnitTest` and
`./gradlew assembleDebug` were both run for real.** `testDebugUnitTest`
reports **428 tests, 0 failures, 0 errors** across all 59 test classes
(`app/build/test-results/testDebugUnitTest/`), including every test this
task added or rewrote — `AvailabilityViewModelLocateMeTest` (6),
`AvailabilityScreenMapIconStackTest` (10), the rewritten
`AvailabilityScreenLayoutTest` variants (17 each, ×3 configurations), and
the untouched medium/expanded-window tests
(`AvailabilityScreenCompactWidthDrawerTest`,
`AvailabilityScreenWideWindowLayoutTest`), all green. `assembleDebug`
produced a real, signed `app-debug.apk`. Neither the SDK setup nor these
runs happened in a CI system — they ran once, in this session, against
this exact diff — so re-running them in CI or on a real checkout remains
the independent confirmation worth having, but "compiles and its tests
pass" is now a measured fact, not an inference from reading the diff.

What the change reasons through, on top of that measured result:
`LocationResult.altitude` is a new nullable field, read from
`Location.hasAltitude()`/`getAltitude()`, that extends an existing type
without touching either of its two existing call sites' behavior for
callers that don't ask for it (default `null`). The new
`CompassProvider`/`AndroidCompassProvider` mirror the existing
`LocationProvider`/`AndroidLocationProvider` seam exactly, so the same
reasoning that seam has already earned applies. The redesign itself is
scoped to `WindowWidthClass.COMPACT` only, by construction — a separate
`CompactMapTab` composable, not a conditional threaded into the existing
`MapTab` — so the medium/expanded (tablet, landscape, foldable) layout's
code path is byte-for-byte what it was before this change; its own tests
(`AvailabilityScreenWideWindowLayoutTest`,
`AvailabilityScreenCompactWidthDrawerTest`) passing unmodified is now
measured confirmation of that, not just a claim about shared composables.

Not verifiable headlessly even with a working build, and only a device
can answer: live compass heading accuracy and smoothness (the fake used
in tests proves the strip reacts to a heading value, not that a real
rotation-vector sensor produces a usable one); GPS altitude accuracy in
practice (a known limitation independent of this app — reported here as
"the behavior is tested," not as a real-world accuracy number); how the
full-bleed layout, the floating icon stack, and the floating foraging-areas
overlay card read on very small or very large screens, or at a large font
scale (the existing `AvailabilityScreenLayoutTest` measures the compact
map's geometry headlessly, but visual crowding of five overlapping floating
elements over a map is not something bounds-checking alone can catch);
and whether the "tap the map to restore chrome" gesture feels right
alongside osmdroid's own pan/zoom touch handling, which only a real
`MapView` receiving real touches can settle.

### The topographic basemap, specifically

What **is** established about it, and how: the endpoints serve real tiles and
stop at the US border and above zoom 16, checked against the live services by
`scripts/verify-usgs-basemap.sh`, which inspects response bodies rather than
trusting a 200. The osmdroid side — source names, zoom ceilings, copyright
strings, the ArcGIS `z/y/x` URL order, and the tile-cache separation that keeps
two basemaps from mixing — is read off the pinned artifact by
`BasemapTileSourceTest`. That a basemap change leaves every app-drawn overlay
untouched, and that the zoom ceiling really moves with the basemap, are
measured against a real osmdroid `MapView` under Robolectric by
`SightingsMapBasemapSwapTest`; both were confirmed to fail when the swap and
the ceiling were disabled in turn, so they are not passing vacuously.

**Nothing about how it looks has been seen.** No topographic tile has been
rendered by this project. Two specific things follow, and neither is a
formality:

- **That topo tiles render at all** in the `MapView` — the URL is proven to
  return a JPEG, and osmdroid is proven to build that URL, but the two have
  never been observed joined up on a device.
- **Whether the overlay colours still read against topographic terrain.** This
  is a real, named risk rather than a shrug. The sighting dots are bark brown
  at ~70% alpha and the order connector is mushroom orange; both were chosen
  against OpenStreetMap's comparatively flat palette. USGS Topo draws contour
  lines in browns and tans and forest cover in green, and USGS Imagery is
  aerial photography where dark green canopy is exactly where mushroom
  observations cluster. Brown-on-brown and orange-on-contour-line are plausible
  legibility failures. The numbered area markers (white on forest green, opaque)
  are the least at risk.

  **The colours were deliberately not changed.** Adjusting them from reasoning
  alone would be the speculative correction `CLAUDE.md` rules out, and it would
  also alter the one part of this layer that *has* been confirmed on hardware —
  that the connector reads as visibly dashed. Re-tinting it unseen could spend
  that. The right sequence is to look at it on a device first; if the dots or
  the connector do wash out, the fix is a colour change with a reason, not a
  guess made in advance of the evidence.

The build-identity footer's version values are verified separately — they
were read off the packaged APK with `aapt2 dump badging`, not off the Gradle
config.

**The Seasonal tab's chart is unverified for the same reason the map is**:
`FruitingLagChart` is a Compose `Canvas`, and Robolectric does not render
`Canvas` content meaningfully. `AvailabilityScreenSeasonalTabTest` proves the
sample size, the per-page-cap-honest "based on N of M" line, the
no-preceding-event count, the excluded-for-missing-date count, the
fruiting-lag bucket's "(the rule of thumb)" label, and the observer-effort
caveat are all real on-screen text — one of those assertions was checked
against the actual defect (the caveat `Text` temporarily deleted) and
confirmed to fail with "could not find any node" before being restored — but
whether the bars actually draw at the right heights, in the right colors, on
a real screen has not been seen.

**What was checked against the live Open-Meteo historical archive API, and
how.** A prior session concluded `archive-api.open-meteo.com` was blocked by
network policy; that was a misread of a rate limit (HTTP 429, `"Daily API
request limit exceeded"`) as a connection failure. Checked directly this
session, with backoff retries: the endpoint is reachable, `latitude`/
`longitude`/`start_date`/`end_date`/`daily=precipitation_sum`/`timezone=auto`
is the right request shape, the response carries `utc_offset_seconds` and a
`daily.time`/`daily.precipitation_sum` block matching
`HistoricalPrecipitationResponseDto`, and — the one the shipped code actually
depends on — a single request spanning **2016-01-01 to 2024-12-31 (3288
days)** returned every day fully populated with no truncation, so
`OpenMeteoHistoricalWeatherProvider` makes one unchunked request per search
rather than paging a long historical range.
`scripts/verify-open-meteo-historical-fields.sh` encodes these same checks
with retry/backoff so they can be re-run; the daily rate limit from this
session's own repeated manual probing meant the script's own last run in this
session could not complete its second (multi-year) check before exhausting
retries — the multi-year result above was captured from a manual run minutes
earlier and is real, but re-running the script the next day (once the quota
resets) is how to reproduce it end to end.

**Not verified, and not assumed either way:** whether the archive API
populates the most recent few days before "today," or returns nulls for
them — every attempt to check that specific case hit the same daily quota
before a request got through. `OpenMeteoHistoricalWeatherProvider` does not
guess: a missing or null precipitation value on any day is dropped from the
series rather than defaulted to zero, the same rule
`OpenMeteoWeatherProvider` already applies to the forecast endpoint, so an
unpopulated recent day degrades to "not counted" rather than reading as
"confirmed dry."

### The quick-fire mode toggle and the Settings panel, specifically

**Rendering is unverified, same as the rest of this section.** That the icon
sits legibly over real map tiles rather than under them or clipped by the
same bounds `SightingsMap`'s own clip-to-bounds fix addresses, and that its
placement and contrast read well against USGS Imagery's aerial-photo palette
specifically, have not been seen on a device. `AvailabilityScreenSettingsPanelTest`
proves the icon's *measured position* is inside the map's box and biased
top-right, and that tapping it changes which `Basemap` the map slot actually
receives — layout and wiring facts, not appearance ones.

### Offline map downloads, specifically

**Actual tile download/delete I/O is unverified.** Everything about
`OsmdroidOfflineMapRepository` that touches the network or the filesystem —
whether `CacheManager.downloadAreaAsyncNoUI` really writes through
`PersistentTileWriter` end to end, whether a downloaded region survives an OS
cache-clear the way it's designed to, what actually happens on a real network
failure partway through a download — is Android file and network I/O and
cannot be exercised on the JVM. What *is* verified, and how:

- The `CacheManager` API surface this class calls — the `(ITileSource,
  IFilesystemCache, minZoom, maxZoom)` constructor needing no live `MapView`,
  the `downloadAreaAsyncNoUI` progress-callback shape, and that osmdroid's own
  bulk-download policy check does not, by itself, block OpenTopoMap — was read
  directly from `osmdroid-android-6.1.20`'s attached sources, not assumed from
  its sparse Javadoc. See `OsmdroidOfflineMapRepository`'s doc comment for the
  citations.
- The sidecar-file format `getStatus()` reads is pulled out as pure
  `Properties` conversion functions and round-trip tested headlessly
  (`OfflineMapStatusFileTest`), including that a corrupted or partial file
  reads as "nothing downloaded" rather than crashing or guessing.
- The bounding-box math the region picker feeds to `CacheManager` is pure and
  unit-tested (`GeoDistanceTest`), including the antimeridian-wrap and
  near-pole cases.
- The ViewModel's loading → success/failure state machine
  (`AvailabilityViewModelOfflineMapsTest`) is exercised against a fake
  `OfflineMapRepository` this project fully controls, covering progress
  reporting, download/delete failure, and that invalid coordinates never
  reach the repository at all.
- That the "Offline Maps" submenu is reachable regardless of the selected
  map service, that its picker map always resolves to USGS Topo, that its
  entry row navigates in and its back arrow returns to Settings, and that a
  long-press on the picker map sets the region and enables "Download Maps"
  are all measured against the real Compose tree
  (`AvailabilityScreenSettingsPanelTest`), not just reasoned about.

**The OpenTopoMap tile-usage-policy finding is one step removed from its
primary source.** osmdroid's pinned artifact encodes the OpenStreetMap half
of decision #7 directly — `TileSourceFactory.MAPNIK`'s `TileSourcePolicy` sets
`FLAG_NO_BULK`, citing `operations.osmfoundation.org/policies/tiles/` by name
in the library's own source comment — but carries no such flag for
`TileSourceFactory.OpenTopo` at all, so nothing in the library blocks a bulk
request against it. The claim that OpenTopoMap's own tile server (a distinct
service, `tile.openmaps.fr`, run separately from the OpenTopoMap map style
itself) also prohibits bulk/prefetch downloading rests on a web search of
`opentopomap.org`'s and the OSM Foundation's own policy text, not a direct
fetch of either page — this environment's egress proxy blocks both
`opentopomap.org` and `operations.osmfoundation.org` directly, on both the
attempt during this task and an earlier one. Worth a primary-source
spot-check in an environment that can reach them before this is relied on
further; the enforcement in this app does not depend on osmdroid's own
(incomplete) policy check either way — see `OfflineMapRepository`'s doc
comment.

### The mushroom log, specifically

**What is verified headlessly, and how.** The three-state/inapplicability
type rules, including reflecting on the compiled classes to confirm a
`Pores`/`Teeth`/`SmoothOrWrinkled` hymenophore and an `Absent` stipe
genuinely carry no sub-fields at runtime, not just at compile time
(`ThreeStateModelTest`). The full Room round trip for a barely-populated
entry and a fully-populated one, including every `Observed`/`Feature`
column encoding, the sealed-choice discriminators, and that saving replaces
an entry's photo set rather than appending to it
(`RoomMushroomLogRepositoryTest`). The migration itself: a real version-3
database, built from the same production entity classes, migrated forward,
with the pre-existing `planned_trips`/`cached_searches` rows read back and
compared field-for-field, and the new tables proven usable with a real
save/read round trip afterward (`MushroomLogMigrationTest`). `PhotoStore`'s
persist/delete against real app-private storage, including that a persist
from an unreadable source fails rather than returning a path to a file
that isn't there (`FilePhotoStoreTest`). The domain-level photo
attach/detach ordering — persist-or-delete happens before the entry is
saved, and a failure doesn't reach the repository at all
(`AddAndRemovePhotoUseCaseTest`). And that a `NotObserved` field reads on
screen as "Not recorded," distinctly from an explicitly `Absent` one — not
just in state — measured against the real Compose tree under Robolectric
(`MushroomLogNotObservedRenderingTest`), plus that the map's long-press now
offers "Plan a trip"/"Log a find" and routes each choice correctly
(`AvailabilityScreenTripPlanningFlowTest`).

**Not verifiable headlessly, and not claimed as covered:**

- **Camera capture on a real device.** `CameraCaptureFiles`/`FilePhotoStore`
  are tested against a `Uri.fromFile` source standing in for a real capture
  (see `FilePhotoStoreTest`'s own doc comment), and the `FileProvider`
  manifest declaration and `res/xml/file_paths.xml` are reasoned correct
  from the platform docs, not observed: whether `ActivityResultContracts.TakePicture`
  actually launches the device camera app, writes into the granted URI, and
  returns successfully has not been seen. Same for `PickVisualMedia` and
  the real system photo picker.
- **How the entry form reads at a large font scale, or on a small phone.**
  Seven collapsible sections stacked in a scrolling column is reasoned to
  scroll rather than clip — the same pattern `SearchControls`/`SettingsContent`
  already use, measured layout-wise by `AvailabilityScreenLayoutTest` for
  those panels — but the log panel's own layout has not been measured the
  same way, and legibility at a doubled font scale has not been seen at all.
- **Whether the chip-based `FilterChip` selection state is visually legible**
  — selected vs. unselected relies on Material3's default container-colour
  treatment alone (no leading check icon was added), which has not been
  looked at on a real screen.
- **The iNaturalist upload path — not built in this phase at all**, so
  there is nothing to verify yet. See the "How it works" entry above and
  `docs/plans/mushroom-log.md`'s iNaturalist section for what a follow-up
  PR still needs to check against the live API before shipping it (the
  swagger-spec-is-incomplete finding in particular: a wrong parameter name
  produces a successful-looking upload that silently drops data).

## Which build am I running?

Open the drawer, then tap **Settings** at the bottom; its own footer reads
`Build <versionCode> · <versionName>`, for
example `Build 9 · 1.0.9+g85fa6245`. The footer moved here from the bottom
of the search panel when Settings was added — see "Project layout" above.
The versionCode is the git commit count
— it is what Android compares when deciding whether an install replaces the
existing app or silently no-ops — and the sha names the exact commit. A
`.dirty` suffix means the build had uncommitted changes.

A versionName starting with `UNVERSIONED-` means the build could not derive
its identity (a tarball with no `.git`, or a shallow clone whose commit count
is meaningless) and fell back to versionCode 1. Such a build will not replace
anything on install. Build from a full clone to get a real version; see
`resolveBuildIdentity` in `app/build.gradle.kts`.
