# Forager

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

## Project layout

- `data/remote/` — the only code that speaks Retrofit/iNaturalist's or
  Open-Meteo's wire formats (`INaturalistApi`, `OpenMeteoApi`, DTOs,
  `INaturalistClient`, `OpenMeteoClient`).
- `data/repository/` — maps the iNaturalist API onto the domain-owned
  `MushroomRepository` interface, including parsing iNaturalist's
  `"lat,lng"` location string and `observed_on` date; and the Open-Meteo
  API onto `WeatherProvider` (`OpenMeteoWeatherProvider`).
- `domain/` — pure Kotlin: `Region`, `LatLng`, `SpeciesObservationCount`,
  `Sighting`, `TaxonFilter`, `TaxonSearchResult`, `AvailabilityForecast`,
  `ConditionsSummary`, `ForagingArea`/`ForagingAreas`, `GeoDistance`,
  `Dbscan`, `PredictAvailabilityUseCase`, `GetSightingsUseCase`,
  `SearchTaxaUseCase`, `GetConditionsUseCase`,
  `ClusterForagingAreasUseCase`, and the
  `MushroomRepository`/`LocationProvider`/`WeatherProvider` interfaces. No
  Android imports, so it's unit-testable headless (see `app/src/test/`).
- `location/` — the one place that touches `android.location` directly,
  behind the `LocationProvider` interface.
- `ui/availability/` — `AvailabilityViewModel` and the Compose screen: a
  `ModalNavigationDrawer` holding every search control over a map-first
  content area with the List/Map tab switch. `AvailabilityScreen`'s doc
  comment records why the controls are in a drawer and what was rejected.
- `ui/map/` — `SightingsMap`, the osmdroid `MapView` wrapped for Compose,
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
./gradlew testDebugUnitTest   # runs the headless domain-layer tests
```

The Gradle wrapper (`gradlew`) downloads its own Gradle distribution on
first run.

## iNaturalist API access

Run `scripts/verify-inaturalist-access.sh` to confirm the environment can
reach `www.inaturalist.org` and `api.inaturalist.org`. No API key is
required for the read-only endpoints this app uses.

## Not yet verified

The app builds, links resources, and its unit tests pass, but the running
UI has not been exercised on a device or emulator — this environment has no
hardware virtualization (`/dev/kvm`), so the Android emulator isn't usable
here. Installing to a real device or an emulator on a machine with KVM is
the next verification step before treating the UI as working end to end.

In particular, **the foraging-area map layer has never been rendered**: the
numbered markers and the dashed order connectors compile and are wired up,
but nobody has seen them drawn. The dashing is the part carrying the
honesty burden — it is what stops the connectors reading as a walking path
— so confirming it renders as visibly dashed, at usable zoom levels, is a
required check before trusting that layer.

The map-first layout is likewise unrendered here. Specifically unchecked:
whether the drawer's open/close gestures behave (swipe-to-open is disabled
on purpose so a horizontal drag over the map pans it instead), whether the
`Scaffold` insets actually keep content clear of the navigation bar, whether
the map now receives the full content height, and how the small dot markers
for individual observations look at a dense radius.
