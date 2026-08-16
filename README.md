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

1. You pick a region — either "use current location" (device GPS/network
   location, with a radius slider) or manually entered latitude/longitude —
   and a month.
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
5. A **Map** tab shows the searched region on an OpenStreetMap view, with a
   pin per individual verifiable observation (`GET /v1/observations`) —
   real reported sighting locations, not just the aggregate ranking.
   Observations iNaturalist doesn't expose a location for (e.g.
   conservation-sensitive taxa) are left off the map rather than guessed at.
   Sightings are fetched lazily, only when the Map tab is opened, so
   browsing the ranked list alone doesn't cost the extra API call.
6. When the selected month is the current month, a **Current Conditions**
   card shows recent observed rainfall for the region (total precipitation
   and days since the last significant rain), pulled from
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
- `domain/` — pure Kotlin: `Region`, `SpeciesObservationCount`, `Sighting`,
  `TaxonFilter`, `TaxonSearchResult`, `AvailabilityForecast`,
  `ConditionsSummary`, `PredictAvailabilityUseCase`, `GetSightingsUseCase`,
  `SearchTaxaUseCase`, `GetConditionsUseCase`, and the
  `MushroomRepository`/`LocationProvider`/`WeatherProvider` interfaces. No
  Android imports, so it's unit-testable headless (see `app/src/test/`).
- `location/` — the one place that touches `android.location` directly,
  behind the `LocationProvider` interface.
- `ui/availability/` — `AvailabilityViewModel` and the ranked-list Compose
  screen (with the List/Map tab switch).
- `ui/map/` — `SightingsMap`, the osmdroid `MapView` wrapped for Compose.

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
