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

1. Search controls live in a **navigation drawer**. On medium/expanded
   windows (tablets, landscape, foldables) it's opened from the tune icon in
   the app bar; on a compact (phone-width) window there is no app bar or tune
   icon — the drawer is reached from the map's own floating **Search** icon
   instead (or an **Open Search** button before a first search has run — see
   item 6 below). You pick a region there — either "use current location"
   (device GPS/network location, with a radius slider) or manually entered
   latitude/longitude — and a month. The drawer keeps the map, which is the
   primary content, at full height; a one-line strip above it
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
   not just the aggregate ranking. Dots rather than MapLibre's stock pins
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
   bottom nav — **List / Maps / Seasonal / Journal / Settings**, five
   destinations, replacing the old top tab row — and a right-edge floating
   icon stack over the map itself: fullscreen (hides the bottom nav and the
   quick-search strip, leaving only the map and the stack — tap the map or
   the icon again to bring chrome back), GPS locate-me (recenters the map on
   the device's live location; distinct from "use current location" above,
   which sets the *search region* instead — the two never share state), the
   topo/regular toggle described below, **Search** (the only way to reach the
   search drawer on compact — there is no app bar or tune icon here; before a
   first search has run the icon stack doesn't exist yet, so the Maps tab
   shows an **Open Search** button in its place), and an add (+) button. The
   add button opens the exact same "Plan a trip / Log a find" chooser the
   map's long-press gesture opens — it sets the identical pending-location
   state the gesture sets, not a parallel handler, so the two entry points
   can't drift apart — centred on the current search region rather than
   requiring its own GPS fetch. A compass + GPS-altitude strip sits at the
   top of the map (elevation reads `null`, shown as "unavailable," whenever
   the location fix didn't report one — e.g. a network-based fix — never a
   guessed value); the compass reads the device's rotation-vector sensor,
   falling back to accelerometer + magnetometer, behind an owned
   `domain/CompassProvider` interface so it's testable without real
   hardware.

   **The compact search drawer is the whole search feature, not just region
   and month.** The species/category chips and taxon search field that used
   to sit in the app bar, the foraging-areas toggle and summary that used to
   float over the map, and Recent Searches, Advanced Search and Trip Planner
   all live in this one drawer, reached only from the Maps tab (see above).
   The "Fungi · August · 15 km" strip above the map stays visible on every
   compact tab as a read-only summary of the current search, so checking
   what's currently searched doesn't require opening the drawer — it just
   can't *change* anything from there any more. **Settings and the mushroom
   log are their own bottom-nav tabs** (`Journal`, `Settings`) rather than
   drawer entries — see this item's Settings paragraph below and item 10 for
   what changed. **Medium/expanded windows (tablets, landscape, foldables)
   are unaffected** by any of this — they keep the permanent drawer +
   side-by-side List/Map layout described below, with Settings/Offline
   Maps/Mushroom Log still reached as drawer panels behind the app bar's tune
   icon. See `ui/availability/AvailabilityScreen`'s `CompactMapTab`/
   `CompactSearchDrawerContent`/`CompactSettingsTab` and
   `docs/plans/map-redesign.md`.

   **Which basemap draws the tiles is two separate decisions with two
   different lifetimes.** Which *service* to use — **OpenStreetMap** (the
   default) or **USGS** — is occasional, so it lives in **Settings ▸ Choose
   Maps Service** — reached from the sticky entry at the bottom of the search
   drawer on medium/expanded windows, or the **Settings** bottom-nav tab on
   compact. Settings also has an **imperial/metric** toggle for how every
   distance in the app (search radius, offline-download radius, recent
   searches, forecast summaries) is displayed — the underlying data and API
   calls are always kilometres; this only changes the label
   (`ui/availability/DistanceUnit`). Which *mode* that service is in — topo
   or regular — is a
   during-the-walk decision made often, so it's a quick-fire icon (on a
   compact window, the third icon in the floating stack above; on medium+
   windows, still overlaid on the map's own top-right corner) rather than
   buried in a menu: "if a map has two modes, toggle the two." OpenStreetMap's two modes are OpenTopoMap and
   the standard OSM street map; USGS's are USGS Topo and USGS Imagery.
   Switching service never resets the mode — leave the icon on regular mode
   under OpenStreetMap and switch the service to USGS, and the map lands on
   USGS Imagery, not USGS Topo. All four tile sources are plain style-URL
   templates in `ui/map/Basemap`/`ui/map/BasemapStyles` — no API key and no
   vendor SDK naming a tile source directly. See `ui/map/MapService` and
   `ui/map/Basemap`.

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
   15, OpenTopoMap at 17, OpenStreetMap at 19, enforced via MapLibre's
   `setMaxZoomPreference` (`SightingsMap`) rather than trusted from whatever a
   basemap's own tile source claims. This fixed a real bug in the app's
   original osmdroid-based renderer (replaced by MapLibre — see "The
   topographic basemap, specifically" below): osmdroid derived a `MapView`'s
   ceiling as the maximum across its *module providers*, and
   `MapTileProviderBasic` stacked a `MapTileApproximater` that claimed 29, so
   the app let you zoom ten levels past what OpenStreetMap actually serves,
   into upscaled blur. The explicit-ceiling discipline carried forward into
   the MapLibre rewrite rather than being re-discovered the same way twice.
   `scripts/verify-usgs-basemap.sh` is the evidence behind the USGS numbers:
   it fetches real tiles and checks the response bodies really are images, so a
   200 carrying an error page can't pass for coverage. It also records that the
   service's own metadata advertises tile levels up to **23** while tiles stop
   at 16 — the app trusts the observed ceiling, per `CLAUDE.md`'s rule that a
   reported capability range is not an operating limit.

   **Settings ▸ Offline Maps downloads OSM-derived vector tiles for a region
   you pick, for offline use — a different source from any of the four
   basemaps above, always, regardless of which one is selected for ordinary
   browsing.** Reached via a submenu of its own (a "Offline Maps" row inside
   Settings, one tap below "Choose Maps Service"), because it holds an
   interactive map: rather than typing latitude/longitude, you long-press the
   map shown there to set the download's centre point, the same long-press
   gesture used elsewhere in this app to drop a planned-trip pin — a marker
   with the radius in its snippet confirms the pick. Tiles come from a
   self-hosted Cloudflare Worker (`server/pmtiles-worker`) reading a
   continental-US [Protomaps](https://protomaps.com) PMTiles extract out of
   R2 — not from any of the four live basemaps' own tile providers, all of
   which either prohibit bulk/prefetch downloading in their usage terms or
   would cost real money to hit at that volume. MapLibre's own
   `OfflineManager` persists the downloaded region in its own on-device
   store under the app's private files directory, not the cache directory
   ordinary browsing uses, so neither an OS cache-clear nor ordinary map
   panning can evict a region the user explicitly asked to keep. See
   `map/MapLibreOfflineMapRepository` and "Offline map downloads,
   specifically" below for what's hardware-confirmed about this path.

   **Offline zoom goes one level past the stored archive itself.** The flat
   `us.pmtiles` extract is built to zoom 14 — a storage-budget choice (fits
   Cloudflare R2's free tier for continental-US coverage at that depth), not
   a technical ceiling — but Protomaps' own live daily build (the same
   dataset this extract was cut from) goes one level deeper, to 15. Rather
   than re-extracting a much larger flat archive to reach it, the Worker
   range-reads individual zoom-15 tiles directly out of that live build on
   first request and caches each into R2, so a region only ever costs what
   that specific area's tiles actually take up — not the continent's. See
   the "Not yet verified" section below for what's unconfirmed about this
   specific piece: it has not been deployed or exercised against a real
   Cloudflare account from this project's development environment.

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
    deferred.** On medium/expanded windows it's still reached from the
    drawer's **Mushroom Log** entry (a sticky row above Settings), and a new
    find is started by long-pressing the map — which asks "Plan a trip" or
    "Log a find" instead of going straight to the trip dialog, so the
    existing gesture grows a second option rather than the app growing a
    second gesture for the same "I'm pointing at a place" action.

    **On compact, the log has its own bottom-nav tab, labelled `Journal`.**
    It opens a gallery (`ui/log/LogGalleryScreen`, a two-column grid) rather
    than the drawer's plain list: every existing entry as a tile with its
    cover photo (or a placeholder icon and an "Incomplete" label if any
    field is still unrecorded), plus a permanent first tile — a dashed
    outline with a centered `+` — that starts a new one. Since there's no
    map to long-press from a bottom-nav tab, tapping that tile opens a small
    interactive map instead (`ui/log/LogEntryLocationPicker`): long-press to
    place a pin, "Place entry here" to confirm. It calls the exact same
    start-entry handler the map's own long-press "Log a find" option calls,
    so a Journal-started entry and a map-started one are the same code path,
    not two. `ui/log/JournalTab` is what switches between the gallery, the
    picker, and an entry's own detail screen.

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
- `map/` — parallel to `location/`: the one place that touches MapLibre's
  `OfflineManager`/`OfflineRegion` and the filesystem directly, behind the
  `OfflineMapRepository` interface. `MapLibreOfflineMapRepository` downloads
  a picked region's vector tiles from the self-hosted PMTiles Worker (see
  "How it works" above), via a glyph-stripped style deliberately different
  from what a live map renders (see that class's own doc comment — PR #23
  isolated a native crash specific to downloading a style with glyph/label
  layers), into `OfflineManager`'s own persistent store, which already lives
  under the app's private files directory rather than the cache directory
  ordinary browsing uses. `MapLibreOfflineRegionMetadata` is the small
  `Properties`-over-bytes payload stashed in each `OfflineRegion`'s opaque
  metadata field — which `Region` the download was for and when it
  finished, the two facts `OfflineManager`'s own store (tile count/size,
  read live via `OfflineRegion.getStatus`) doesn't carry.
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
  that width instead.

  On a compact window, navigation is a different shape entirely rather than
  a restyle of the drawer above — see `docs/plans/map-redesign.md`'s "Phase
  2" section for why. `CompactTab` (`List`/`Maps`/`Seasonal`/`Journal`/
  `Settings`) drives a 5-item `ForagerBottomNav`, kept in sync with the
  shared `ResultsTab` only for the three destinations they have in common.
  `CompactMapTab` is the map redesign's full-bleed replacement for `MapTab`:
  same long-press flow, plus the right-edge `MapIconStack` and the
  `CompassElevationStrip`; before a first search it shows an "Open Search"
  button instead of the icon stack, since the stack (and its Search icon)
  has nothing to attach to yet. `CompactSearchDrawerContent` is the compact
  drawer's entire content — species/category search, Recent Searches,
  Advanced Search, Trip Planner and the foraging-areas toggle all together,
  reached only from `CompactMapTab`'s Search icon (there is no app bar or
  tune icon on compact) — replacing the old `ForagingAreasOverlay`, which no
  longer exists. `CompactSettingsTab` is what the `Settings` bottom-nav tab
  shows (`SettingsContent` plus `OfflineMapsPanel`, the same composables the
  medium/expanded drawer's `DrawerPanel.Settings`/`DrawerPanel.OfflineMaps`
  use, just hosted outside the drawer); `DistanceUnit`
  (`ui/availability/DistanceUnit.kt`) is the km/mi display toggle it hosts,
  and `formatDistanceKm` is the one function every distance-displaying
  composable in this package routes through. `LocateMeStatus` (in this same
  package) is the icon stack's GPS/locate-me state, deliberately independent
  of `AvailabilityUiState.locationPermissionDenied`, which belongs to the
  unrelated "use current location for search region" control.
- `ui/map/` — `MapSlot`, the seam the screen fills instead of naming
  MapLibre directly (the `MushroomRepository` pattern applied to the UI
  layer, so the screen can be composed in a test without starting a real
  renderer); `SightingsMap`, a real MapLibre `MapView` wrapped for Compose
  (replacing an earlier osmdroid-based implementation — see "The
  topographic basemap, specifically" below for that migration), including
  the sighting dots and numbered foraging-area markers as MapLibre GeoJSON
  sources/style layers rather than osmdroid `Overlay`s, and the dashed
  order connector's `lineDasharray` scaled to preserve its original
  pixel-based dash:gap ratio exactly; `ForagingAreaLabels`, which holds the
  single wording of the "not a walking route" disclaimer so the on-map
  info window and the on-screen caption can't drift apart; `Basemap`, the
  same own-the-vendor-boundary idea one level down — the basemap catalogue
  is pure Kotlin (labels, coverage limits, zoom ceilings, attribution, no
  MapLibre and no Compose), and `BasemapStyles.styleJsonFor` is the only
  place it becomes a real MapLibre style JSON; and `MapService`, which
  groups those four `Basemap`s into the two services (OpenStreetMap, USGS)
  Settings' "Choose Maps Service" picks between, each with a topo/regular
  mode the map's own quick-fire icon toggles.
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
  `JournalTab` is the compact bottom nav's equivalent of `LogPanel`, added
  in the map redesign's Phase 2 rather than replacing `LogPanel` (see that
  composable's own doc comment for why both exist): it switches between
  `LogGalleryScreen` (the two-column grid gallery, plus its `AddEntryTile`),
  `LogEntryDetailScreen` reused as-is, and `LogEntryLocationPicker` — the
  minimal long-press-a-point map a new Journal entry needs, since there's no
  full map to long-press on a bottom-nav tab the way `MapTab`'s gesture
  works.

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

**The renderer changed since the paragraph below was written, and the fact
it once described no longer applies to the code that ships today.** An
earlier osmdroid-based `SightingsMap` *was* seen once on a physical phone —
its dashed connectors confirmed visibly dashed, and a real painting-outside-
its-own-rectangle bug found and fixed with `Modifier.clipToBounds()` (see
"The topographic basemap, specifically" below for the full migration). That
osmdroid renderer is gone: `SightingsMap` now hosts a real MapLibre
`MapView`, built and reasoned through in an environment with no
`/dev/kvm` and therefore no way to render anything here either. **Nothing
about the new renderer has been observed on hardware at all** — not that
tiles paint inside the map's Compose slot, not that the dashed connector
still reads as dashed, not that MapLibre's own clipping behaviour (which is
not the same mechanism `clipToBounds()` addressed) actually keeps content
inside its bounds. This is a real regression in *what's confirmed*, not
just *what's changed* — the migration traded a hardware-verified renderer
for an unverified one, deliberately, for the capabilities described in
`docs/plans/maplibre-migration.md`. `BasemapStyleTest` and
`SightingsMapOverlayDataTest` establish what a JVM test can about the new
renderer's non-native-backed logic (style JSON shape, GeoJSON feature
construction, the dashed-connector ratio) — see "The topographic basemap,
specifically" for exactly what that does and does not prove.

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
screen hands the map the right box; they prove nothing about what MapLibre
paints in it — same limitation the paragraph above already states, for the
renderer actually running today.

**Map overlay legibility has no headless assertion that establishes it.**
`docs/plans/understory-design-system.md` proposes a `MapPalette` that gives
the seven overlay colours a dark-theme variant and a `MapPaletteTest` that
guards it. That test can assert derivation (each colour traces to a named
role and does not equal it) and contrast against a *stated* basemap
luminance range — but that range is a provisional constant, not a measured
one, because the overlay colours are drawn over a basemap raster rather
than a theme surface role, so the WCAG-against-surface check the rest of
the palette gets does not reach them. The test bounds regressions; it does
not establish that any overlay colour is actually legible against real
tiles, in either theme. Whether the sighting dots, the dashed connector,
the planned-trip diamond, the breadcrumb trail and the waypoint pin stay
distinguishable from each other and from the tiles underneath — in sun, in
shade, on topo and on plain — is a device question and is open.

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
alongside MapLibre's own pan/zoom touch handling, which only a real
`MapView` receiving real touches can settle.

**The strip's MGRS line is new, added this project cycle, same
verification gap as the rest of this strip.** It extends `MgrsConverter`
(already hardware-independent — see `MgrsConverterTest`'s pinned reference
points) to show the device's live position, reusing the exact same
explicit-unavailable-state discipline the heading and elevation text
already used. `AvailabilityScreenMapIconStackTest` proves all three text
states render through the real screen, including a real fix resolving to
the exact grid reference `MgrsConverterTest` already pins for that same
point — not a value invented for this test. Not verified, and only a
device can answer: legibility of a denser three-line pill, and whether the
added line crowds the map's top edge on a small screen.

### Phase 1a/1c — track recording, waypoints, and the converge screens (breadcrumbs, offline readiness, return-to-vehicle), specifically

**Measured, this project cycle:** `./gradlew testDebugUnitTest` reports
**525 tests, 0 failures, 0 errors**, including everything Phase 1a and 1c
added or extended — `TrackRecordingViewModelTest` (15, covering
start/stop, breadcrumb polling, waypoint CRUD, return-to-start bearing
math, the reactive live-fix path, and returning/off-track state, all
against an in-memory fake, not a real device fix), `DetectOffTrackUseCaseTest`
(5, the distance-trending-away heuristic in isolation),
`AvailabilityScreenWaypointFlowTest` (7, waypoint drop/list/delete through
the real screen), the extended `AvailabilityScreenSettingsPanelTest`
(offline readiness state), the extended `SightingsMapOverlayDataTest`
(breadcrumb and waypoint GeoJSON feature construction), and the extended
`AvailabilityScreenMapIconStackTest` (the record toggle and the
return-to-vehicle line's four text states: blank while idle, a waiting
message before the first fix, and both the over-and-under-a-kilometer
distance formats once one lands).

**One real regression was caught and fixed by this same headless
coverage, not by hardware.** The return-to-vehicle row's
`Modifier.fillMaxWidth()`, with nothing else constraining the strip's
`Column` width, stretched the whole compass/elevation pill into a
full-width banner that covered the map underneath and silently swallowed
the touches `AvailabilityScreenTripPlanningFlowTest` and
`AvailabilityScreenWaypointFlowTest` send to the stubbed map's long-press
button — both suites went from green to failing outright, not flaky, the
moment that row was added, and green again once the `Column` was pinned to
`IntrinsicSize.Max`. Worth naming here because it is exactly the class of
layout mistake this section keeps saying only a device can catch: this
one, a JVM test caught first.

**None of it has been seen running.** Same limitation as the rest of this
map screen, compounded: the foreground `TrackRecordingService` — its
notification, whether Android actually keeps it alive through Doze on a
multi-hour walk, whether `ACTION_START`/`ACTION_STOP` round-trip correctly
through a real `Context` — has never been started on a device, only
reasoned through and unit-tested against fakes. The breadcrumb line and
waypoint pins are GeoJSON source/layer data proven correct by
`SightingsMapOverlayDataTest`, but MapLibre's native `Layer`/`Source`
types can't be constructed in this environment at all (see "The
topographic basemap, specifically" above), so whether either actually
draws on the map is exactly as unverified as the basemap itself. The
offline readiness screen's z0–14/z15-overflow distinction is logic-tested
against the `MapLibreOfflineMapRepository` contract, not against a real
device pulling real tiles through the Cloudflare Worker. The
return-to-vehicle line's bearing/distance/elevation math is verified
against `GeoDistance`'s own pinned test geometry, not against a real GPS
track walked in the field — whether the numbers it shows while actually
returning to a vehicle are useful, at a glance, mid-walk, is unasked.

**Task #15 (off-track alert + check-in timer) is in progress — the
off-track half is built, the check-in timer isn't started.** What
landed: `DetectOffTrackUseCase` (5 tests), a distance-trending-away
heuristic — while a new `isReturning` state is active (distinct from
`isRecording`; outbound travel is never "off track"), if the live
distance back to the track's start has net increased over the last three
readings past a small noise threshold, `TrackRecordingUiState.isOffTrack`
flips true. The return-to-vehicle line is now the toggle for that state
itself — tapping it calls `startReturn()`/`stopReturn()`; bold marks an
active return, error-colored text marks off-track — covered by
`TrackRecordingViewModelTest`'s new returning/off-track tests (7) and
exercised through the same "real ViewModel over fakes" pattern as the
rest of that file.

**Building this surfaced and fixed a real bug in the already-shipped
return-to-vehicle screen, not just a gap in the new work.**
`TrackRecordingViewModel.returnToStart()` was being recomputed only from
`AvailabilityViewModel`'s one-shot locate-me fetch — refreshed solely
when the user tapped that icon, never continuously while walking. That
made the bearing/distance shown stale between taps, and made the
off-track heuristic unworkable outright (it needs a running series of
readings, not one). Fixed by having `TrackRecordingViewModel` collect
`LocationTracker.fixes` itself — the same continuous stream
`TrackRecordingService` already collects for the track's own points —
whenever a recording is active, computing `returnToStart` on every fix.
Two independent OS location-listener registrations while recording (the
service's and this one) is the accepted, stated cost of not re-plumbing
the service to publish its fixes back out to the UI layer for this one
reader. `TrackRecordingViewModelTest` covers the reactive path directly
(a fake `LocationTracker` backed by a controllable `MutableSharedFlow`),
not just the direct-call path the original 8 tests already had.

**Not yet built, still task #15:** firing an actual notification when
`isOffTrack` transitions to true (the state and UI toggle exist; nothing
posts a notification on it yet — string resources
`off_track_notification_*` are added but unreferenced), and the check-in
timer entirely. Design for the timer, settled but not started: scheduled
via `AlarmManager` (not `WorkManager`, which is not a project dependency
— see `TrackRecordingService`'s own doc comment) with a setup-time
delivery check that routes to the battery-optimization exemption screen
if a test alarm doesn't actually fire, and wording stating plainly it is
a local phone reminder, not a monitored service. **Next intent:** build
the off-track notification (small — reuse the existing
`POST_NOTIFICATIONS` grant and a `LaunchedEffect` on `isOffTrack` in
`MainActivity`, matching the pattern already used for the foreground
service's own start/stop), then the check-in timer. Once #15 is fully
done, Phase 1 as a whole (1a/1b/1c) is feature-complete and the deferred
PR #27/#28/`phase1-combined` reconciliation and verification decisions
come next, per the project owner's own hold on that until then.

### A hardware-driven design pass on the compact map screen, specifically

Three changes made against a real device screenshot mid-Phase-1c, not
part of the original plan doc: the compass/elevation/return-to-vehicle
strip now spans the map's full width (previously a narrow centered
pill); the search summary bar gets a leading magnifying-glass icon and
opens a quick species-search panel (category chips + a species field,
reusing `SpeciesSearchControls`) rather than doing nothing when tapped in
compact mode — "Advanced search" stays exactly where it was, behind the
drawer; and the map now shows a live "blue dot" that follows the
device's position continuously, using MapLibre's own `LocationComponent`
rather than the pre-existing one-shot locate-me fetch (which still
exists, feeding only the compass strip's own text). Tracking follows by
default once location permission is granted, breaks on any manual pan/
zoom (MapLibre's own gesture detection, not code this app wrote), and
the GPS icon re-engages it.

**Two real regressions were caught and fixed by headless coverage before
either would have reached hardware.** Widening the strip reintroduced the
exact touch-swallowing failure `IntrinsicSize.Max` fixed earlier in this
same cycle — `AvailabilityScreenTripPlanningFlowTest`/
`AvailabilityScreenWaypointFlowTest` went from green to failing outright
the moment the strip became full-width again. This time the fix wasn't
shrinking it back down (the whole point was full width): tracing it down
showed `Surface`, even with no `onClick`, intercepts pointer input for
the area it covers — a plain `Box` + `background()` modifier doesn't, so
switching to that let the map keep receiving touches everywhere except
the strip's own real interactive children (the return-to-vehicle text,
the record toggle). Separately, opening the quick-search panel put two
live instances of `SpeciesSearchControls` in the tree at once —
`ModalNavigationDrawer` keeps its own copy composed even while closed,
translated off-screen rather than removed — real duplication a test
caught as an ambiguous-node failure, not just a test-authoring
inconvenience; `QUICK_SEARCH_PANEL_TAG` lets the two be addressed
unambiguously.

**The live-location puck and camera-follow behavior have since been
confirmed on hardware** — a real-device screenshot showed the blue dot
tracking the device's position on the map. The full-width strip's
on-screen legibility and the quick-search panel's layout at a small
screen size are still only reasoned through and headlessly verified
where a JVM test can reach (529/529 passing at the time), not confirmed
on a device. `LocationComponent`/`MapLibreMap`/`Style` are native-backed
types this project's own established precedent already documents as
unconstructable in a JVM unit test (see "The topographic basemap,
specifically" above), so the live-tracking activation code itself has no
test coverage beyond compiling — the same boundary
`initializeOverlayLayers`/`refreshOverlayData` already sit behind, not a
new gap this pass introduced.

### A single-line compass strip with labeled decimal coordinates, specifically

A follow-up hardware round after the pass above: the project owner's
real-device screenshot showed the compass/elevation/coordinates strip
still wrapping onto two visually blocky lines. Three changes answer
that, all in `CompassElevationStripContent`/`coordinatesStripText`:
heading, elevation, and coordinates now render on one shared `Row`
(`labelMedium`, down from `labelLarge`, to make more likely to fit); the
coordinates segment gained explicit `Lat.`/`Long.` labels alongside the
existing MGRS grid reference (`coordinatesStripText`, renamed from
`mgrsStripText`), at this file's existing `"%.4f"` decimal-degree
precision; and those coordinates now come from a second, independent
continuous `LocationTracker.fixes` collection newly added to
`AvailabilityViewModel`'s own `init` block (`AvailabilityUiState.
liveLocation`/`liveAltitudeMeters`), replacing the previous one-shot
`locateMeStatus`-derived values — "any time the map is open," the same
live-tracking scope the project owner specified earlier in this cycle,
not gated on tapping the GPS icon or on a track recording being active.

**A real regression was caught and fixed by headless coverage, a third
instance of the same touch-interception class this file has now hit
twice before** (`Surface` intercepting pointer input even with no
`onClick`, documented above and in `CompassElevationStripContent`'s own
comment). The first attempt at "don't wrap onto a second line" used
`Modifier.horizontalScroll(rememberScrollState())` on the shared `Row` —
`AvailabilityScreenTripPlanningFlowTest`/`AvailabilityScreenWaypointFlowTest`
immediately went from green to failing: `horizontalScroll` installs a
real pointer-input handler even at zero scroll range, and that handler
sat, in z-order, on top of the map underneath this full-width strip,
swallowing the fake map slot's own "Simulate long press" touch in both
suites' fixtures. The fix was `Modifier.weight(1f, fill = false)` +
`TextOverflow.Ellipsis` on just the coordinates `Text` instead — the
longest of the three segments, and the one most likely to need to give
way on a narrow screen — which adds no pointer input of its own, so the
map stays reachable everywhere under the strip; an ellipsis at the tail
end degrades gracefully rather than a hard clip. Root-caused by
capturing the actual semantics tree mid-test (`onRoot().printToString()`)
rather than guessing after the first fix attempt didn't hold, per this
project's own "two failed attempts, stop guessing" rule.

**Not yet seen running.** Same limitation as everything else in this
section: reasoned through and headlessly verified (529/529 passing) —
including a new `AvailabilityScreenMapIconStackTest` case that pushes a
fix through a fake `LocationTracker` and asserts the strip's combined
MGRS-plus-decimal text renders — but not confirmed on a device. Whether
the single line actually fits without ellipsis-truncating on a typical
phone width, and whether the live coordinates genuinely update in step
with the map and compass as the project owner asked, are both open until
the next hardware round.

### Phase 2 — the compact navigation restructure (search-only drawer, 5-tab bottom nav, Journal gallery, distance unit), specifically

Measured the same way as Phase 1 above, in the same session, against this
exact diff: `./gradlew testDebugUnitTest` reports **422 tests, 0 failures,
0 errors**, and `./gradlew assembleDebug` produced a real, signed
`app-debug.apk`. The count is lower than Phase 1's 428 not because coverage
shrank, but because several tests that asserted the *old* drawer/app-bar
shape were rewritten in place rather than duplicated alongside new ones —
`AvailabilityScreenLayoutTest`, `AvailabilityScreenMapIconStackTest`,
`AvailabilityScreenConditionsMonthTest`,
`AvailabilityScreenAdaptiveLayoutTest`,
`AvailabilityScreenTripPlanningFlowTest`,
`AvailabilityScreenSettingsPanelTest`, and `AvailabilityScreenOfflineCacheTest`
all needed their drawer-opening helpers changed (the "Advanced search
options" app-bar icon they clicked no longer exists on compact) and, in a
few cases, their assertions changed to match where content actually moved
(the foraging-areas toggle, the "15 km" search summary, a Settings-panel
map-service switch) rather than where it used to be.

One of those fixes caught a real navigation bug before it reached a device,
not just a test failure: with the app-bar tune icon removed, the map icon
stack's Search button was the *only* way to reach search — but that stack
doesn't render until `uiState.hasSearched`, which is never true on a fresh
install. A first-time user would have had no way to ever open search. Fixed
by adding an explicit "Open Search" button to `CompactMapTab`'s pre-search
state (see `docs/plans/map-redesign.md`'s "Phase 2" section for the full
account); every test exercising a fresh, unsearched screen was updated to
use that button rather than assuming the icon stack exists.

Not verifiable headlessly, same limitation as Phase 1: how the Journal
gallery grid, its dashed "+" tile, and the small long-press map picker
actually look and feel on a device — Robolectric measures bounds and
semantics, not whether a two-column grid of cover-photo thumbnails reads
well at phone width, or whether long-pressing a small embedded map (rather
than the full-bleed one) is comfortable to hit. Also unverified on a
device: the metric/imperial Settings toggle's actual on-screen labels
(`formatDistanceKm`'s output is unit-tested and exercised through
`AvailabilityScreen`'s Robolectric suite as text, but not seen rendered),
and whether losing the always-available search affordance outside the Maps
tab (see "How it works" item 6 above) is a real friction point in practice
rather than a reasoned tradeoff.

### Phase 4 — back-button navigation, specifically

Measured the same way as the phases above, in the same session: `./gradlew
testDebugUnitTest` reports **438 tests, 0 failures, 0 errors**, and
`./gradlew assembleDebug` produced a real, signed `app-debug.apk`. The new
`AvailabilityScreenBackNavigationTest` (6 tests) is driven through the real
`ComponentActivity.onBackPressedDispatcher` rather than by calling a
`BackHandler`'s callback directly — the point of that suite is verifying
*priority* among several independently-declared `BackHandler`s (the drawer,
fullscreen chrome, the current tab, a nested Journal entry or Settings
submenu), which only the real dispatcher's own stack can actually settle;
reasoning about registration order alone would be exactly the kind of
unverified claim CLAUDE.md rules out. That includes the one combination
only compact width can reach — the drawer open while the map is also
fullscreen, since the icon stack's Search button stays up in fullscreen —
and the case where a nested Journal entry has to unwind before the tab
switches away.

**Not verifiable headlessly:** whether the physical back button and
gesture-nav swipe on a real device actually route through the same
dispatcher path Robolectric exercises here, and whether the multi-step
unwind (drawer → fullscreen → tab → home) *feels* right in the hand rather
than merely being correct step by step — five back presses to leave a deep
state is a real cost this trades for never accidentally exiting from one.

### The topographic basemap, specifically

**The renderer migrated from osmdroid to MapLibre this project cycle**
(`docs/plans/maplibre-migration.md`), and everything below is written
against the renderer actually running today — osmdroid is fully removed
(no dependency, no source file references it).

What **is** established, and how: the USGS endpoints themselves serve real
tiles and stop at the US border and above zoom 16 — checked directly
against the live services by `scripts/verify-usgs-basemap.sh`, which
inspects response bodies rather than trusting a 200; this check is about
the raw HTTP endpoints, not which renderer consumes them, so it carried
over the migration unchanged. The renderer-facing facts that used to be
read off osmdroid's pinned artifact — zoom ceilings, attribution, the
ArcGIS `z/y/x` URL order, that a basemap swap leaves overlays untouched —
are now established differently: `BasemapStyleTest` parses `styleJsonFor`'s
real output (a plain JVM test, no Robolectric) and asserts the zoom
ceilings, attribution string, glyphs URL, and ArcGIS row/column-vs-x/y tile
order for each of the four basemaps. `BasemapTileSourceTest` and
`SightingsMapBasemapSwapTest`, which read those facts off osmdroid, are
deleted along with it.

**MapLibre's real `Style`/`Layer`/`Source` objects cannot be constructed at
all in this project's development environment** — checked directly via
`javap` against the pinned `org.maplibre.gl:android-sdk` artifact: every
`Layer`/`Source` subclass constructor calls a native `initialize()`
immediately, and `Style` has no public constructor, so nothing short of a
real Android runtime (an emulator or device — this environment has neither)
can instantiate one. `SightingsMapOverlayDataTest` therefore tests only the
non-native-backed logic directly: the GeoJSON `Feature`/`FeatureCollection`
building functions `SightingsMap` feeds into those (unconstructable-here)
style layers, and the dashed connector's `lineDasharray` values, asserted to
hold the exact 18:14 ratio the original osmdroid `DashPathEffect(18f, 14f)`
used (an intermediate value drifted that ratio during the port and was
caught by this same assertion before it shipped).

**The first hardware pass of the MapLibre renderer happened this project
cycle, and it closed two of the questions this section used to carry as
fully open.** Portland-metro, USGS Topo, an active search ("Oyster
Mushrooms · August · 9 mi"), sighting dots, a numbered cluster badge, and a
dashed connector, all on screen together on a real device:

- **MapLibre topo tiles render, and glyphs load with them.** Contours,
  hydrography, roads, and place labels ("OREGON") all painted in the
  `MapView` — the first time this has been seen joined up on a device for
  this renderer, closing the gap the prior osmdroid pass's own finding
  didn't transfer across. Glyphs loading also means the confirmed
  glyph-crash finding from `docs/plans/maplibre-migration.md` §7/PR #23
  does not reproduce here — worth noting that finding was specific to
  *offline downloads* of a style with glyph layers
  (`MapLibreOfflineMapRepository`'s own doc comment), a different code path
  from this live-rendering confirmation, so the two aren't in tension.
- **The dashed connector still reads as visibly dashed** at a metro-area
  search's opening zoom, against varied terrain — the property carrying
  the "not a walking route" safety disclaimer survived the migration to
  this renderer, the same way it held on osmdroid.

**Still open, and now with real findings instead of an unasked question.**
The same screenshot surfaced concrete legibility problems with the overlay
colours specifically — recorded here fix-by-fix as each lands, rather than
written speculatively ahead of the evidence the way this section
previously deferred the question entirely.

- **The map control icon stack's four dark-translucent circles let map
  data (contour lines, place labels) composite straight through them**,
  reading as barely-there smudges — the stack's one already-opaque icon
  (the green "add" button) read perfectly on the same terrain in the same
  screenshot. Fixed: opaque fill plus a hairline edge, as a recorded
  decision rather than a style tweak — see `MapIconStackButtonColor`'s doc
  comment in `AvailabilityScreen.kt` for the finding, the fix, and the
  rejected alternative (per-basemap alpha tuning). **Not yet re-confirmed
  on hardware, and not yet checked on the imagery basemap specifically** —
  pale topo was the basemap that happened to get screenshotted, and
  dark-on-dark is the likelier failure mode for an opaque dark control.
- **The sighting dots (bark brown, ~70% alpha) became an unresolvable
  smudge in a dense cluster** near Lake Oswego, overlapping each other and
  the numbered cluster badge. **Deliberately not made opaque** — unlike the
  icon-stack fix above, overlap density here is itself information (a
  muddle of dots is the signal that several sightings cluster there), so
  the fix is a light stroke around each dot for boundary definition, not
  maximum contrast. See `SightingsMap`'s class doc comment, "The overlay
  colours", for the finding and the fix. Same caveat as the icon stack:
  not yet re-confirmed on hardware or checked against the imagery basemap.
  No headless test covers this either — `CircleLayer`'s paint properties
  are native-backed and unconstructable in this project's dev environment
  (same limitation this section already documents for the dashed
  connector and area-marker text).

Two more fixes from the same hardware pass, unrelated to the colour
question:

- **The coordinate strip truncated mid-coordinate** ("Lat. 45.3262
  Lon…") on a metro-width screen — half a coordinate is not a coordinate,
  on the one element whose job is telling you where you are. Fixed: MGRS
  by default (compact, unambiguous), with a tap on the coordinates segment
  to reveal the labeled decimal-degree pair — see `coordinatesStripText`'s
  doc comment for why decimal degrees stay reachable rather than deleted,
  and for the check performed before landing this (no configurable
  coordinate-format setting or "emergency card" exists anywhere in this
  project yet to honor instead — confirmed by grep, not assumed). Covered
  by `AvailabilityScreenMapIconStackTest`: the combined line no longer
  renders, and the tap toggle works both directions.
- **MapLibre's own attribution control and this app's `Basemap.attribution`
  caption painted over each other**, bottom-left — "© MapLibre ©" and
  "USGS The National Map — public domain" both illegible where they
  overlapped. Fixed by moving MapLibre's control to bottom-end via
  `UiSettings.setAttributionGravity` (confirmed via `javap` against the
  pinned SDK — see `SightingsMap`'s `getMapAsync` block), so each
  attribution keeps its own corner rather than needing a margin computed
  from the other's basemap-dependent text height to stack correctly.
  **The attribution content itself was checked, not assumed**: the string
  says USGS, and the screenshot's basemap really was USGS Topo, so
  `Basemap.USGS_TOPO.attribution` matches what's actually being served —
  this is a different question from the one still open above, about what
  the *offline region-picker* map renders on (a different screen from the
  live search map this screenshot is of), which stays unanswered.

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

**The mechanism changed from osmdroid to MapLibre's `OfflineManager`.**
`OsmdroidOfflineMapRepository`, `PersistentTileWriter`, and
`OfflineMapStatusFile` are deleted; `MapLibreOfflineMapRepository` is the
sole `OfflineMapRepository` implementation `AppContainer` wires up now,
downloading real vector tiles from this project's own Cloudflare Worker + R2
archive rather than bulk-fetching any third-party basemap's live tile
servers. That sidesteps the OpenTopoMap/OSM bulk-download-policy question
this section used to carry entirely — this project's own Worker has no such
usage terms to worry about — though the underlying Protomaps/OSM data itself
still carries the ODbL attribution obligation `Basemap` already owns as data.

**Unlike most of this file, this piece has real hardware confirmation** —
see `docs/plans/pmtiles-worker-android-wiring.md` for the full record,
including two real bugs it caught before shipping. What was directly
observed, on the owner's device: a downloaded region (5 mi radius around
39.7940, -98.5529 — 139 tiles, 0.1 MB) survived a force-close, clearing the
app from recents, and a cold reopen; `getStatus()` read the persisted region
back correctly afterward, with no crash and no inline error. An earlier run
(6 km around 45.3368, -122.6016 — 88 tiles, 1.9 MB) confirmed the download
itself completes cleanly against the real Worker-hosted style with no hang.
The two bugs that hardware round caught, not headless coverage: an
`asset://` style URL that hung `OfflineManager`'s resource discovery
indefinitely at `completed=0/1` with no error (fixed by hosting the style at
a real HTTPS URL on the Worker instead of bundling it as an app asset), and
`getStatus()` never initializing MapLibre's native library on a process
where it — rather than `download()` — happened to run first, which crashed
outright on a genuinely fresh process (fixed by centralizing
`MapLibre.getInstance()` into the one helper every entry point now calls
first). Separately, PR #23 isolated a native crash specific to downloading a
style with glyph/label layers — why the download style is deliberately
glyph-stripped, distinct from what a live map renders.

**Still genuinely open, in priority order:**

1. **Airplane-mode offline replay.** A downloaded region is confirmed to
   persist across a restart with a live connection available; it has not
   been confirmed to actually render with the radio off. This is the one
   item that matters most for the "offline" claim, and it stays open on its
   own — it does not get folded into the persistence confirmation above.
2. **The zoom-15 overflow tiles this project cycle added are wholly
   unverified against real infrastructure.** The Worker now range-reads
   individual tiles beyond the local archive's zoom-14 ceiling directly out
   of Protomaps' live daily build and caches them into R2 (see "How it
   works" above) — written and reasoned through, but never deployed to a
   real Cloudflare account or exercised against a live network from this
   project's development environment. Two things worth checking before it's
   relied on at real scale: that `build.protomaps.com` actually serves the
   range requests this assumes the way PMTiles' `FetchSource` expects, and
   Protomaps' tolerance for sustained per-tile production traffic against
   their public daily-build host, as opposed to the occasional bulk
   `pmtiles extract` their own docs describe.
3. **The bounding-box math the region picker feeds to `OfflineManager` is
   pure and unit-tested** (`GeoDistanceTest`, including the antimeridian-wrap
   and near-pole cases), and **the ViewModel's loading →
   success/failure state machine** (`AvailabilityViewModelOfflineMapsTest`)
   is exercised against a fake `OfflineMapRepository` this project fully
   controls, covering progress reporting, download/delete failure, and that
   invalid coordinates never reach the repository at all — both survived the
   swap from osmdroid unchanged, since neither depends on which repository
   implementation is behind the interface.
4. That the "Offline Maps" submenu is reachable regardless of the selected
   map service, that its entry row navigates in and its back arrow returns
   to Settings, and that a long-press on the picker map sets the region and
   enables "Download Maps" are all measured against the real Compose tree
   (`AvailabilityScreenSettingsPanelTest`), not just reasoned about — this
   predates the renderer swap and is unaffected by it. What the picker map
   itself renders on, now that osmdroid is gone, needs re-stating here once
   confirmed — not carried over unchanged from the pre-swap USGS Topo claim.

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
