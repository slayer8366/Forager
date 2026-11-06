# Journal entry page and height: parts 1 to 3 report

Dispatch `prompts/preserved/2026-10-11-01.md`. Rulings: RECORD -864, and -867 (branch `records-after-173`, `0485dfb4`). Branch `journal-entry-height`. Builds ran on CI only. No local Gradle was run and no phone was touched.

## What landed

| Part | Forward commit | Revert commit | Restore commit |
|---|---|---|---|
| 1. Climb leaves out a track of fewer than two points | `432f913e` | `6db454ba` | `65f18e62` |
| 2. Entry page changes; waypoint bubble shows distance from the start | `fadaa88d` | `15f85179` | `7e6a1e82` |
| 3. New entries leave out recordings under a minute | `36b7878f` | `d5966464` | `0a968666` |

`9494d7ae` merges `origin/main` (`af5ea9af`, the run-this-branch trigger) into the branch. Draft PR #217 opened the first runs and was closed once the trigger was available. Its runs were cancelled and none is cited here.

## Evidence

Every run below was started with `gh workflow run ci.yml --ref journal-entry-height`, by a script that refuses to start unless the remote head equals the commit named. For each run:
- its `headSha` was read back;
- its log was searched for `e: ` and `Compilation error`;
- its JUnit XML was downloaded and read.

Each restore was checked with `git diff <forward> HEAD`, which came back empty. So each forward change was present again before its result was cited. The revert script restores from a copy saved before the edit, never from git.

| Run | Head | Filter | Compile errors | Result |
|---|---|---|---|---|
| 38164674451 | `9494d7ae` (part 1) | `*EntryReportTest` | 0 | 14 tests, 0 failures. Both new tests present in the XML. |
| 38165020697 | `6db454ba` (part 1 revert) | `domain.EntryReportTest` | 0 | 9 tests, 1 failure: `a track with fewer than two points is left out of climb...`, `expected:<20.0> but was:<-1.0>`. This is the predicted failure. The other new test (all-short tracks still give no Climb) passes either way, as predicted, because it does not depend on the filter. |
| 38165440497 | `fadaa88d` (part 2) | none, full suite | 0 | 606 suites, 4646 tests, 0 failures, 24 skipped (the CI allowlist's own check passed). |
| 38166546510 | `15f85179` (part 2 revert) | `*EntryDataScreensTest;*CartographyEntryReportScreenTest;*MapBubblesTest` | 0 | 63 tests, 3 failures, exactly the predicted three: the entry-map bubble test (no "0.3 mi walked from the start"), and the two lists-gone tests (a "Find on Aug 1, 2026" line found). |
| 38166937447 | `36b7878f` (part 3) | `*CartographyViewModelTest;*ShortRecordingTest;*EntryDataScreensTest` | 0 | 42 tests (30 + 2 + 10), 0 failures. |
| 38167369410 | `d5966464` (part 3 revert) | `*CartographyViewModelTest;*ShortRecordingTest` | 0 | 32 tests, 1 failure: `...shorter than a minute...`, `expected:<{track-blip=false, track-walk=true}> but was:<{track-blip=true, track-walk=true}>`. |

**The part 2 revert made two one-line edits in one commit**, to save a CI run:
- (a) the bubble's distances fed an empty list;
- (b) the Finds list put back.

Each edit fails different, named tests: (a) the bubble test, (b) the two lists-gone tests. The commit message labels both.

## What each part does

1. **Climb.**
   - `entryReportOf` now leaves out an included track with fewer than two points (`domain/EntryReport.kt`, `MIN_POINTS_FOR_CLIMB`).
   - A longer track with no heights still makes Climb read "Not recorded". So does an entry whose tracks are all short.
   - The `TrackStatistics` doc comment is corrected to say what the code does.
2. **Entry page.**
   - It keeps the map, the four tiles, notes, tags, photos and the offline-map toggle.
   - Removed: the waypoint table and its hoisted open-row state (in `CartographyScreen`, `JournalTab`, `AvailabilityScreen` and `AvailabilityCompactScaffold`), the track rows, the track details sheet those rows opened, and the Finds list.
   - The height chart is not drawn. `EntryHeightProfile` is unchanged and still used by the Records track sheet.
   - A waypoint's bubble on the entry map has a new line, for example "0.3 mi walked from the start". It comes from `MapRecordSources.waypointDistancesFromStart` (`ui/map/MapBubbles.kt`, `ui/map/MapBubble.kt`). Every other map passes none.
3. **Under a minute.**
   - A new entry's recordings shorter than 60 s start with `kept = false`: left out, but shown in the editor and changeable there (`domain/ShortRecording.kt`, `CartographyViewModel.onStartEntry`).
   - Exactly 60 s is not short.
   - A track still recording, or an imported track, is never short.

## Assertions removed or inverted (RECORD -867 requires each to be named)

All paths are under `app/src/test/java/com/zynergylabs/forager/app/`.
- `ui/log/EntryDataScreensTest.kt`:
  - The tiles test: its chart-label assertions (509 ft, 328 ft, 0.8 mi, chart tag present) are inverted to "no chart, no chart line, no 'Height'".
  - Removed: the waypoint table rows test, the coordinate-tap test, and the three track-row touch tests.
  - The no-heights test: the "one line instead of a chart" assertion is inverted. Its Climb "Not recorded" assertion is kept.
  - New: a lists-gone test and an entry-map bubble test.
- `ui/log/CartographyEntryReportScreenTest.kt`:
  - `only kept decisions render, never a withheld one` became `the page lists no finds, tracks or waypoints, only the tiles and offline maps`. Its find, track and waypoint "displayed" assertions are inverted. Offline maps and the Finds tile count are kept.
  - Removed: `a kept track survives as text even though nothing backs its id` and `a kept track gone from a loaded day says it is no longer in Records and has no tap`.
  - **Line 93**, which passes both before and after the change, is explained in a comment there. The removal itself is held by the reworked test above, which keeps all three kinds.
- `ui/availability/LeavingTheJournalFixesTest.kt`: removed `an entry report's waypoint row opened to its coordinates is still open after a tab change`.
- `ui/log/EntryLabelsTest.kt`: removed the `waypointCoordinatesLine` assertion, along with that function (the table was its only caller). The test is renamed `dates read Oct 7, 2026`.

## Decisions for the planner (outside -864 and -867, not chosen)

1. **The short recording's Start and End markers are still included in a new entry.** -867 says "leave out recordings", and those markers are waypoints linked to the recording. So the owner's Oct 10 case would still show two pins at 2:44 PM on the entry map. The options:
   - (a) also leave out ORIGIN and END markers whose track is left out;
   - (b) also leave out every waypoint linked to that track;
   - (c) leave it as built.
2. **The kept offline maps' list is still on the entry page.** -867's keep-list does not name it, and neither does its go-list. I left it, because removing it would be a choice the rulings don't make.

## Not yet done or not verified

- Parts 4 and 5 are not yet built.
- Nothing here has been seen on a phone.
- The bubble's position and look on a real screen are not verified. Robolectric checks the bubble's text and its ancestor tag only.
