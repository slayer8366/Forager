# Forager

An Android app that ranks which mushroom species are worth looking for, in a
chosen region and month, based on how often people have historically logged
them there on [iNaturalist](https://www.inaturalist.org/).

This is a historical-frequency ranking over real observation data, not a
weather-style forecast or a fitted model: the app deliberately doesn't claim
more certainty than the data supports. See `AvailabilityForecast` and
`PredictAvailabilityUseCase` in `domain/` for how the ranking is computed.

## How it works

1. You pick a region — either "use current location" (device GPS/network
   location, with a radius slider) or manually entered latitude/longitude —
   and a month.
2. The app queries iNaturalist's `GET /v1/observations/species_counts` for
   verifiable fungi observations within that radius, filtered to that month
   across all years.
3. Species are ranked by observation count, with the top species normalized
   to a relative-likelihood of 1.0.
4. A **Map** tab shows the searched region on an OpenStreetMap view, with a
   pin per individual verifiable observation (`GET /v1/observations`) —
   real reported sighting locations, not just the aggregate ranking.
   Observations iNaturalist doesn't expose a location for (e.g.
   conservation-sensitive taxa) are left off the map rather than guessed at.
   Sightings are fetched lazily, only when the Map tab is opened, so
   browsing the ranked list alone doesn't cost the extra API call.

## Project layout

- `data/remote/` — the only code that speaks Retrofit/iNaturalist's wire
  format (`INaturalistApi`, DTOs, `INaturalistClient`).
- `data/repository/` — maps the iNaturalist API onto the domain-owned
  `MushroomRepository` interface, including parsing iNaturalist's
  `"lat,lng"` location string and `observed_on` date.
- `domain/` — pure Kotlin: `Region`, `SpeciesObservationCount`, `Sighting`,
  `AvailabilityForecast`, `PredictAvailabilityUseCase`,
  `GetSightingsUseCase`, and the `MushroomRepository`/`LocationProvider`
  interfaces. No Android imports, so it's unit-testable headless (see
  `app/src/test/`).
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
