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
   an OpenStreetMap view, with a small dot per individual verifiable
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
  (`OpenMeteoWeatherProvider`); and the Open-Meteo historical archive API
  onto `HistoricalWeatherProvider` (`OpenMeteoHistoricalWeatherProvider`).
- `domain/` — pure Kotlin: `Region`, `LatLng`, `SpeciesObservationCount`,
  `Sighting`, `SightingsPage`, `TaxonFilter`, `TaxonSearchResult`,
  `AvailabilityForecast`, `ConditionsSummary`, `ForagingArea`/`ForagingAreas`,
  `FruitingLagDistribution`, `GeoDistance`, `Dbscan`,
  `PredictAvailabilityUseCase`, `GetSightingsUseCase`, `SearchTaxaUseCase`,
  `GetConditionsUseCase`, `ClusterForagingAreasUseCase`,
  `ComputeFruitingLagDistributionUseCase`, `GetSeasonalPatternUseCase`, and
  the `MushroomRepository`/`LocationProvider`/`WeatherProvider`/
  `HistoricalWeatherProvider` interfaces. No Android imports, so it's
  unit-testable headless (see `app/src/test/`).
- `location/` — the one place that touches `android.location` directly,
  behind the `LocationProvider` interface.
- `ui/availability/` — `AvailabilityViewModel` and the Compose screen: a
  `ModalNavigationDrawer` holding every search control over a map-first
  content area with the List/Map/Seasonal tab switch. `AvailabilityScreen`'s
  doc comment records why the controls are in a drawer and what was
  rejected; the Seasonal tab's hand-rolled `Canvas` bar chart is
  `FruitingLagChart` in the same file.
- `ui/map/` — `MapSlot`, the seam the screen fills instead of naming
  osmdroid directly (the `MushroomRepository` pattern applied to the UI
  layer, so the screen can be composed in a test without starting tile
  threads); `SightingsMap`, the osmdroid `MapView` wrapped for Compose,
  including the dot marker for individual observations and the numbered
  foraging-area markers with their dashed order connectors; and
  `ForagingAreaLabels`, which holds the single wording of
  the "not a walking route" disclaimer so the on-map info window and the
  on-screen caption can't drift apart.

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

Still unchecked, and only a device or emulator can answer it: whether the
drawer's open/close gestures behave (swipe-to-open is disabled on purpose so
a horizontal drag over the map pans it instead), whether the `Scaffold`
insets actually keep content clear of the navigation bar, and how the small
dot markers for individual observations look at a dense radius.

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

## Which build am I running?

Open the drawer; the footer reads `Build <versionCode> · <versionName>`, for
example `Build 9 · 1.0.9+g85fa6245`. The versionCode is the git commit count
— it is what Android compares when deciding whether an install replaces the
existing app or silently no-ops — and the sha names the exact commit. A
`.dirty` suffix means the build had uncommitted changes.

A versionName starting with `UNVERSIONED-` means the build could not derive
its identity (a tarball with no `.git`, or a shallow clone whose commit count
is meaningless) and fell back to versionCode 1. Such a build will not replace
anything on install. Build from a full clone to get a real version; see
`resolveBuildIdentity` in `app/build.gradle.kts`.
