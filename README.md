# Forager

[![CI](https://github.com/slayer8366/Forager/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/slayer8366/Forager/actions/workflows/ci.yml)

An Android app for people who look for things in the woods, mushrooms first. It ranks which species are
worth looking for in a place and a month, from how often people have logged them there on
[iNaturalist](https://www.inaturalist.org/); shows them on a map you can take offline; and keeps your own
finds, photos, tracks and waypoints in a journal on the phone.

Read the ranking for what it is. It counts past iNaturalist observations for the region and month you pick
and shows each species as a share of the most-observed one. It is a description of the past, not a forecast
(`domain/model/AvailabilityForecast.kt`, `domain/PredictAvailabilityUseCase.kt`).

This file is the entry point: what the app does, how to build it, and where the project's records are. The
old README, which described an earlier app, is kept whole in
[`docs/audits/2026-10-01-readme-archive.md`](docs/audits/2026-10-01-readme-archive.md); do not rely on it.

## What the app does today

The app has five tabs along the bottom (`CompactTab` in
`ui/availability/AvailabilityNavigationUi.kt`): **List**, **Seasonal**, **Maps**, **Journal** and **Tools**.
The same layout is used at every window size, phone or tablet, portrait or landscape.
Source paths named below that do not start with a top-level folder are under
`app/src/main/java/com/zynergylabs/forager/app/`.

- **Search** floats over the top of the screen, not on a tab. It searches fungi only: you name a species (the
  suggestions come from a list bundled inside the app, so naming one works with no connection) and pick a place, a
  radius and a month. The ranking itself needs the network: it asks iNaturalist for species counts and observations.
- **List** shows the ranking for that search, with a way to see a species on the map.
- **Seasonal** shows the weather for the searched region and a test of the rule of thumb that fruiting
  follows rain by one to three weeks. That test does not change the ranking.
- **Maps** is the main screen. Street, topographical (OpenTopoMap) and satellite (USGS imagery) base maps;
  overlay switches for your finds, photos, waypoints, planned trips, the trail you are recording, saved
  tracks, offline map regions and the journal entries that keep a record; the iNaturalist sightings of the
  current search drawn as dots; centre-on-me, reset-to-north, fullscreen, and an add action for planning a
  trip or logging a find at a spot. Track recording is started from the map's controls.
  - **Stacks.** Records that sit on top of each other (finds, photos, waypoints, planned trips) form a stack.
    Tapping a stack fans it out so each can be tapped. A fan stays open while the map follows your location
    and closes when you move the map yourself. Tapping a fanned icon shows that item's bubble over the open
    fan; tapping another stack closes the bubble and opens that stack's fan. Back steps out one thing at a time:
    the bubble first, then the fan; and Back from a journal entry opened from a bubble's "kept in" line returns
    to the map with that bubble and the fan it was opened from.
  - **Forecast layers.** Two colour-field layers for forecast data exist in the map, but the app has no real
    source for that data today, so every build reports that there is no forecast data.
- **Journal** has two sides, **Entries** and **Records**. Entries are the trip write-ups you author; an
  entry can keep finds, photos, waypoints and tracks. Records holds All, Finds, Tracks, Waypoints and Offline
  maps. A find can carry photos, taken with the camera or imported. Recorded tracks can be exported as GPX.
- **Tools** opens a drawer with the **Trip Planner** (planned trips, and the days in the coming stretch that
  look worth going, from rain history) and **Settings**: night mode, night maps, units, whether to save your
  location into photos, locking the camera to portrait, and backup and restore of the journal to a folder you
  choose.

## Which build am I running?

Open **Tools**, then **Settings**; the line at the bottom reads `Build <versionCode> · <versionName>`, for
example `Build 9 · 1.0.9+g85fa6245`. The version code is the number of commits in the history the build was
made from, and the version name is `1.0.<that number>+g<short commit hash>`. A trailing `.dirty` means the
build had uncommitted changes. A name starting `UNVERSIONED` means the build could not read its identity
from git, and its code cannot be trusted (`app/build.gradle.kts`, `buildIdentity`).

## Building and running

You need a JDK (the project compiles for Java 17; CI uses 21), the Android SDK (`compileSdk` 37,
build-tools 37.0.0) and the git history, because the version is read from it. `scripts/setup-android-sdk.sh`
installs the SDK pieces CI uses. The Gradle wrapper pins Gradle 9.7.0.

```
./gradlew assembleDebug          # the debug APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # the unit tests (on the JVM; Robolectric stands in for Android)
```

The app runs on Android 8.0 (API 26) and up and targets API 37. It has debug and release variants, built
from the same source; development tools are not part of either (removed 2026-10-12, RECORD -930).

## Continuous integration

`.github/workflows/ci.yml` runs on every pull request, on pushes to `main` and `pre-main`, and on request (below), on
`ubuntu-24.04` with JDK 21, a full-history checkout and the SDK from `scripts/setup-android-sdk.sh`. It
builds the debug APK, reads the version back out of the built APK with `aapt2` to prove the history was
there, runs the unit tests, and checks the skipped ones against an exact list of allowed skips. It uploads
two artifacts: `app-debug-apk` and `unit-test-report`.

A second job, `lint`, runs Android lint on the debug variant (`./gradlew lintDebug`) and fails on any
lint error, with no baseline (dispatch 2026-10-17-03, RECORD -1195). The checks switched off, each with its
reason, are in the `lint {}` block of `app/build.gradle.kts`. It uploads `lint-reports`.

A third job, `release-compile`, proves the release variant compiles and packages: it runs
`./gradlew packageRelease packageReleaseBundle` with no signing key, so a release-only compile error, or code in
`src/main` that needs a debug-only dependency, fails CI (dispatch 2026-10-17-04, RECORD -1201). It stops short of
`assembleRelease` and `bundleRelease`, which fail by design without the signing identity, and it does not upload
the unsigned APK or bundle, only its Gradle log, as `release-compile-log`.

### Run this branch on CI

When the laptop is busy, a coder builds and tests a pushed branch on CI instead (RECORD -865). The branch
must carry a `ci.yml` with the `workflow_dispatch` trigger, so cut it from (or merge in) a `main` that has
it. Any branch, open pull request or not:

```bash
gh workflow run ci.yml --ref <branch>                                    # everything, as on a pull request
gh workflow run ci.yml --ref <branch> -f tests='*TrackRecordingViewModelTest'
gh workflow run ci.yml --ref <branch> -f tests='com.example.FooTest;com.example.BarTest.a name with spaces'
```

`tests` is optional. Patterns are Gradle `--tests` patterns separated by `;` (not spaces or commas: test
names here contain both). Each one becomes its own `--tests` argument, and the input never runs as shell.
Empty runs exactly what a pull request runs. A filtered run skips one check: the "stale skip-allowlist
entry" check, because allowlisted tests outside the filter never run. Unallowed skips, zero tests,
failures and errors still fail it. A pattern that matches no test fails the Gradle step.

Find the run, wait for it in a background loop, and download what it made:

```bash
sha=$(git rev-parse HEAD)          # the commit you pushed
id=$(gh run list --workflow ci.yml --branch <branch> --event workflow_dispatch -L 5 \
       --json databaseId,headSha -q "[.[] | select(.headSha == \"$sha\")][0].databaseId")
until [ "$(gh run view "$id" --json status -q .status)" = completed ]; do sleep 60; done
gh run view "$id" --json conclusion,headSha -q '.conclusion + " " + .headSha'
gh run download "$id" -n app-debug-apk      -D ci-$id/apk
gh run download "$id" -n unit-test-report   -D ci-$id/report   # JUnit XML in test-results/
```

The CLAUDE.md revert-runner rules apply to CI runs too:

- **Confirm the run is for your commit.** Its `headSha` must equal the commit you pushed. The run checks out
  the branch head at the moment of the request. If anything was pushed between your push and the request,
  the run tested that instead.
- **Read the build log for compile errors before citing any test result.** Use `gh run view "$id" --log`
  (or `--log-failed`) and look for `e: ` lines and `Compilation error`. A run whose build did not compile
  has no test results to cite.
- For a revert check, push the reverted commit, run it, read it as above, then push the restore. Confirm
  the forward change is back in the branch before citing the revert's result.

## Outside services

What the code talks to, and what each needs. No API key or token for any of them is in the code.

| Service | Used for | Needs |
|---|---|---|
| iNaturalist API (`api.inaturalist.org/v1`) | species counts and observations for the ranking; the sightings on the map | a connection |
| Open-Meteo (forecast and archive) | the weather panel, trip windows and the rain-and-fruiting test | a connection |
| OpenStreetMap, OpenTopoMap, USGS National Map | the street, topographical and satellite base maps | a connection, unless you have downloaded an offline region |
| MapLibre demo glyph server | text on the vector map | a connection |
| The project's own tile worker (`server/pmtiles-worker/`) | the vector tiles behind offline map downloads | a connection while downloading |

`scripts/verify-*.sh` probe some of these services from outside the app.

## Project layout

| Path | What it is |
|---|---|
| `app/` | the Android app: `app/src/main`, `app/src/test` (JVM tests), `app/src/testDebug` (JVM tests run against the debug build only), `app/src/androidTest` |
| `app/src/main/java/.../domain/` | the rules: ranking, trips, tracks, records |
| `.../data/` | the Room database, the iNaturalist and Open-Meteo clients, repositories, backup |
| `.../ui/` | Compose screens: `availability` (tabs, search, settings), `map`, `log` (Journal), `track`, `backup`, `credits`, `format`, `theme`, `motion`, `adaptive` |
| `.../map/` | MapLibre set-up and the offline-region repository |
| `.../photo/`, `.../location/`, `.../sensor/`, `.../service/`, `.../alert/`, `.../export/`, `.../crash/`, `.../net/` | camera and photo storage; location; compass and level; the recording service; alerts; GPX export; crash files; the HTTP user-agent |
| `data/species-index/` | how the bundled species-name index is built, and its inputs |
| `server/pmtiles-worker/` | the tile worker behind offline downloads |
| `scripts/` | SDK set-up and the checks that probe outside services |
| `docs/` | the project's records, below |
| `.github/workflows/` | CI |

Top-level files besides this one: `CLAUDE.md` (the standing working rules), `RECORD.md` (the log of dispatched
work), `STATUS.md`, `TRACK-PULSE.md` and `DISPATCH-REPORT.md` (reports, redacted 2026-09-09), and
`NAVIGATION-SHELL-DISPATCH.md`, `PANEL-CONTENTS-DISPATCH.md` and `STRIP-REVERT-AND-PILL-DISPATCH.md` (dispatch
specifications). Treat them as history, not current documentation.

## Where the project's memory lives

Work here is dispatched and recorded, so much of what is known about the app is in files, not in the code.

- `CLAUDE.md`: the engineering principles and the list of known pitfalls. Read it first.
- `RECORD.md`: append-only. Each piece of work opens with an intent entry and closes with one terminal entry.
- `prompts/preserved/`: the dispatch texts themselves.
- `docs/audits/`: dated reports, one per piece of work, with an index in `docs/audits/README.md`. A later
  report supersedes an earlier one rather than editing it.
- `docs/plans/`: what was intended (journal redesign, map redesign, landscape, offline tiles). A plan says
  what was meant, not what exists; check the code.
- `docs/beta/`, `docs/navigation/`: beta-test reports; navigation, alert and compass work.
- `docs/adr/`: decision records (motion).
- `docs/legal/`: the privacy policy, the delete-data page and the data-safety table.
- `docs/process/`, `docs/qc/`: how planner, coder and pulse roles are kept apart, and the planner's guides.
