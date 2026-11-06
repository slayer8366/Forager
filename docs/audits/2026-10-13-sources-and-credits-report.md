# Sources & credits: completion report (dispatch 2026-10-13-05, RECORD -977)

Date: 2026-10-13 (Pacific; CI timestamps are UTC 2026-10-14). Branch `sources-and-credits`, cut from origin/main at
497423d6 (the dispatch's stated base, unchanged when checked). PR #225, **not merged**: the merge is on the owner's word.
The planner's answers to the step-1 report (Q1 to Q8) were relayed by the coordinator and are cited below as they were given.

## What landed

| Commit | What |
|---|---|
| a1aa8dfc | The Settings row, the Sources & credits page, the open-source software page, `assets/credits/`, `verifyCreditsCoverEveryDependency`, the Open-Meteo credit in three places, the MapLibre reminder in `libs.versions.toml` |
| f3c0875b | Build-script fix (the script's `java` extension shadows `java.util.zip`; CI 38512731792 did not compile it); `SourcesAndCreditsTest` |
| 03485968 | `OpenMeteoCreditTest`, `SoftwareCreditsCatalogTest`; licence paragraphs split at runs of blank lines |
| dfd8fc69 | The ten modules and one NOTICE the build check found on its first real run (CI 38512879020), listed below |
| c7346cb4 | Two test matchers fixed (CI 38513340290: "Maps" is also the bottom bar's tab; two library names contain "Retrofit") |

- **Settings, at the very bottom: "Sources & credits ›"** (`AvailabilitySettingsUi.kt`, `SettingsContent`), after Backup
  and a divider, with no divider under it (RECORD -830's rule kept).
- **The Sources & credits page** (`ui/credits/SourcesAndCreditsPages.kt`, data in `ui/credits/DataSourceCredits.kt`):
  - Maps: © OpenStreetMap contributors (licence › openstreetmap.org/copyright).
  - Maps: "Map data: © OpenStreetMap contributors, SRTM; map style: © OpenTopoMap (CC-BY-SA)" (licence › opentopomap.org/about).
  - Maps: Protomaps (offline maps) (licence › github.com/protomaps/basemaps).
  - Weather: "Weather data by Open-Meteo.com" (CC BY 4.0 › open-meteo.com/en/licence), with the line under it "Rain and
    soil figures in Forager are summaries calculated from this data." (Q5).
  - Sightings and species names: "iNaturalist and its observers" (site › inaturalist.org).
  - Open-source software ›.
  - Each row is one touch target across its whole width. A link opens in the browser. If nothing can open it, a Toast
    tells the person and the log records it.
  - The forager-forecast layers are not listed, with a comment where they will go (the map reads `AbsentForecastCellStore`).
- **Open-source software** is grouped by licence (Q2): libraries and their copyright lines first, then the licence text
  once, then any notices those libraries carry. It is a lazy list, a paragraph per item, and reads
  `assets/credits/software.json` with the texts beside it. A list that cannot be read says so on the page and logs why.
- **Back retraces the way in:** Software to Sources, Sources to Settings. Settings keeps its scroll position, so the
  tapped row is still on screen. A backup notification or the settings-reset snackbar returns to Settings' own page (Q8).
- **"Weather data by Open-Meteo.com"** links to open-meteo.com, as their licence specifies. It appears:
  - at the foot of the Current Conditions card, only while it shows a figure of theirs;
  - under the fruiting-lag bucket counts (Q3);
  - at the foot of the Trip Windows card when it has a report.

## The open-source list and its check (Q1)

`verifyCreditsCoverEveryDependency` (`app/build.gradle.kts`, after `assembleDebug`) resolves `releaseRuntimeClasspath`
(193 modules on CI). It fails the build in five cases:
- a module that no library covers;
- a library that matches nothing;
- a pinned version that moved (MapLibre 13.5.0 and Gestures 0.0.4, whose notices were taken from those tags);
- a NOTICE file in a shipped artifact, nested jars included, that is not reproduced word for word, or one reproduced
  that nothing ships;
- a missing or blank text.

**Beyond the planner's wording:** the NOTICE scan reads every entry whose file name starts with NOTICE, not only
`META-INF/NOTICE*`. OkHttp's NOTICE sits at `okhttp3/internal/publicsuffix/NOTICE`, beside the Public Suffix List it
ships under MPL-2.0. A `META-INF`-only scan would have missed it, and the list now credits it.

**First real run (CI 38512879020)** named ten uncovered modules and one NOTICE. Each licence was read from its POM on
Maven Central; AutoValue's comes from its repository's LICENSE at tag auto-value-1.6.3.

| Module | Version | Licence |
|---|---|---|
| guava | 33.3.1-android | Apache-2.0 |
| failureaccess | 1.0.2 | Apache-2.0 |
| dagger | 2.59 | Apache-2.0 |
| auto-value-annotations | 1.6.3 | Apache-2.0 |
| error_prone_annotations | 2.28.0 | Apache-2.0 |
| j2objc-annotations | 3.0.0 | Apache-2.0 |
| jsr305 | 3.0.2 | Apache-2.0 |
| jakarta.inject-api | 2.0.1 | Apache-2.0; ships `META-INF/NOTICE.md`, now reproduced |
| javax.inject | 1 | Apache-2.0 |
| checker-qual | 3.43.0 | MIT |

The same run showed that the concurrent-futures-ktx NOTICE (present in the cached 1.2.0 jar) is in no shipped artifact,
so it was removed. Which version ships was not determined. The run at c7346cb4 reads "covers all 193 modules … and
reproduces all 2 NOTICE files they carry".

**Revert check of the task itself:**
- r9 removed Gson from the list. Its run (38514386169) failed `assembleDebug` with "com.google.code.gson:gson 2.10.1
  ships and no library in credits/software.json covers it".
- r10 added one line to the reproduced OkHttp NOTICE. Its run (38514391613) failed with "com.squareup.okhttp3:okhttp's
  NOTICE at okhttp3/internal/publicsuffix/NOTICE differs from credits/notices/okhttp-publicsuffix-NOTICE.txt".

The META-INF exclude (`app/build.gradle.kts`, `packaging`) is unchanged, as the planner answered.

## Additions the step-1 report found, now listed

- **MapLibre Native Android's own notices file, taken whole at tag android-v13.5.0** (`platform/android/LICENSE.md`).
  It covers the BSD-2 licence and its Android dependencies. It also covers the native components built into the SDK:
  ISC (earcut, kdbush, supercluster, geojson-vt and others), Boost, BSD-3, MIT, the FreeType licence and more.
- **MapLibre Android Gestures 0.0.4** (BSD-2), with its own notice for the Android Gesture Detectors Framework.
- **MapLibre Java (GeoJSON, Turf) 6.0.1.** Its POM names Apache-2.0, but its repository's LICENSE is MIT
  (© 2018 Mapbox). It is listed under MIT, with a note saying so.
- **NGA mgrs, grid, color** (© 2022 National Geospatial-Intelligence Agency) **and sf** (© 2015 Bit Systems): MIT,
  texts from each repository at its version's tag.
- **Open Sans, the map label font** (Q6). The glyphs come from demotiles.maplibre.org, whose README says they were built
  from github.com/openmaptiles/fonts. That repository's OpenSans-Semibold.ttf (version 1.10) states "Digitized data
  copyright © 2011, Google Corporation" and "Licensed under the Apache License, Version 2.0". It is listed under
  Apache-2.0, not OFL. That the served glyphs come from that exact file rests on the README; no bytes were compared.
- **JSR 305:** its `javax.annotation.concurrent` sources carry "Copyright (c) 2005 Brian Goetz, Released under the
  Creative Commons Attribution License (http://creativecommons.org/licenses/by/2.5)". This is quoted in its note.

## Tests

All Robolectric, through the real `AvailabilityScreen`.

- **`SourcesAndCreditsTest` (6):**
  - the row is the lowest thing in Settings' scroll;
  - the sources and links are written out as values and opened by real touches at 5%, 50% and 95% across each row and
    at its "›", each read as the started Intent;
  - every library on the software page, and each licence's first paragraph equal to its file's;
  - Back from Sources, and Back from Software then Sources.
- **`OpenMeteoCreditTest` (5):**
  - exactly one credit inside each place's bounds, below everything else there, opened by three touches;
  - no credit when both conditions fetches failed, or while trip windows load.
- **`SoftwareCreditsCatalogTest` (5):** the named libraries' licences, every text present and its own, and two
  failure paths of the loader.
- `SettingsEndsAtItsLastRowTest`'s doc line was updated, with its assertion unchanged. `SettingsPageHarness` gained
  `setScreen` (`openSettings` calls it).

## Evidence

- **Full CI on the PR, run 38514272612 at c7346cb4: green.** 612 classes, 4,701 tests, 0 failures, 0 errors,
  24 skipped. For comparison, main's run 38337947268 at 497423d6 had 609 classes and 4,685 tests: +3 classes and
  +16 tests, which is what this adds.
- **Revert checks:**
  - Each revert ran on CI on a throwaway branch `revert-check/sources-credits-rN-…`, one edit from c7346cb4.
  - Every run's build log was read for compile errors before its JUnit XML: none had any.
  - The forward branch was never edited for them. After each batch, HEAD was confirmed still at c7346cb4 and clean.

| Revert | Run | What failed, and only that |
|---|---|---|
| r1 row removed | 38514326365 | 5 of 6 SourcesAndCreditsTest: no node `settings-sources-and-credits-row`; the data-only test passed |
| r2 Sources Back handler removed | 38514337119 | the two Back tests: Back left Settings ("Night Maps" not found), and the row not displayed |
| r3 Settings scroll not hoisted | 38514346549 | the two Back tests: the row is not displayed after Back |
| r4 Conditions credit removed | 38514354108 | the Conditions test only: no `open-meteo-credit` node |
| r5 Trip Windows credit removed | 38514361156 | the Trip Windows test only |
| r6 fruiting-lag credit removed | 38514367706 | the fruiting-lag test only |
| r7 Conditions credit ungated | 38514373822 | the failed-fetch test only: "credits on the Seasonal tab expected:<0> but was:<1>" |
| r8 Weather row links open-meteo.com/ | 38514379887 | the links test only: "…open-meteo row opened its link expected …/en/licence" |
| r9 Gson uncredited | 38514386169 | the build task, naming com.google.code.gson:gson 2.10.1 |
| r10 OkHttp NOTICE altered | 38514391613 | the build task, naming that NOTICE |
| r11 unknown-licence check removed | 38514398340 | the catalog's failure-path test only |
| r12 row moved above Backup | 38515026672 | "last row" only, listing Backup's rows below it; SettingsEndsAtItsLastRowTest passed, as Backup was then last |

## Not tested, or not determined

- **The Back-scroll edge case** is inferred from the code and was not run. After a backup notification has been tapped
  in a session, Back from Sources lands at the Backup section rather than exactly where Settings was left.
  `SettingsContent`'s existing scroll-to-Backup effect re-runs whenever the page is composed again.
- **The 48 dp touch target** of the Open-Meteo credit adds height to the three places. This has not been seen on a phone.
- **Landscape** was not exercised by a test.
- **On a real device** (Robolectric resolves no browser), what opens each link, and the Toast when nothing can, are
  untested.
- **Not determined:** which concurrent-futures-ktx version ships. MapLibre Native's notices are as MapLibre published
  them, not checked against the SDK's binary contents.

## Device items (the dispatch's two, as steps)

1. **The page in both orientations.**
   - Steps: Tools, Settings, scroll to the bottom, Sources & credits. Tap each row: OpenStreetMap, OpenTopoMap,
     Protomaps, Open-Meteo, iNaturalist. Then go into Open-source software and Back twice.
   - Pass: the browser opens openstreetmap.org/copyright, opentopomap.org/about, github.com/protomaps/basemaps,
     open-meteo.com/en/licence and inaturalist.org in turn. Back returns Software, then Sources, then Settings, at the row.
   - Evidence: a screenshot of each opened page's address bar, or the list of addresses seen.
2. **The Open-Meteo credit.**
   - Steps: search a region. Open Seasonal (Current Conditions card; the fruiting-lag section once it loads) and Tools >
     Trip Planner (Trip Windows). Tap the credit in each.
   - Pass: the credit sits at the foot of each, and each tap opens open-meteo.com.
   - Evidence: screenshots of the three places.
