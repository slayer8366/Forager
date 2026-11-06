# walk-waypoints: a waypoint dropped while recording belongs to that walk (plan T10)

Dispatch 2026-09-28-616 (RECORD -616, `prompts/preserved/2026-10-06-16.md` on `records-after-173`),
corrected by RECORD -617 (the '+' icon is the entry point, and no long-press is built) and amended by
Amendment 1 (RECORD -618). Branch `walk-waypoints`, cut from `origin/main` at `4db24110` (PR #181).
Not merged; no pull request opened. Laptop coder, no phone, no adb.

## What the walker sees now

- **While recording:** '+' > Waypoint > place the pin > name it. The waypoint is saved and linked to
  the walk being recorded. With no recording running it stays standalone, as before.
- **Records > the walk** (from the Tracks chip, the All logbook, or the map's track bubble > Details):
  - The drawing now has a ring at the start and a filled dot at the end, both in the Tracks colour.
    There is no end dot while the walk is still recording.
  - Each waypoint dropped on the walk is a dot in the Waypoints colour. None of the dots can be tapped.
  - Under the drawing is **"Waypoints on this track"**, which lists the dropped waypoints oldest first.
    Each row shows the waypoint's name and when it was dropped. The start and end are not listed. A
    walk with nothing dropped on it shows no section at all.
- **Tapping a row** opens that waypoint's details in the sheet's place, with Navigate and Directions
  as before. **Back** returns to the walk's details. Back again closes the sheet.
- **Sharing the walk as GPX** writes the dropped waypoints as `<wpt>`, along with the start and end.
- **Deleting the walk** keeps its waypoints as ordinary waypoints with no link. This is unchanged
  (`DeleteTrackUseCase`, `detachFromTrack`).

## Verify first (code only, no Gradle), and the stop

The full verify went to the planner by message. Below is the summary. App paths are relative to
`app/src/main/java/com/zynergylabs/forager/app/`.

- **Base and the dispatch's citations.** `origin/main` was at `4db24110`, as the dispatch stated.
  Every citation checked out:
  - `WaypointEntity.trackId` is nullable and indexed (`data/local/WaypointEntity.kt:18,27`).
  - `WaypointDao.getForTrack` is at `:29-30`. Its only production caller is
    `RoomWaypointRepository.kt:25`.
  - `addWaypoint` passed no `trackId`. The origin waypoint (`:898-905`) and the end waypoint
    (`:605-611`) did pass one.
  - **One premise was wrong.** The GPX pre-build report is
    `docs/audits/2026-09-08-gpx-export-full-record-prebuild-report.md`, not a file in `docs/navigation/`.
- **The drop path.** There is exactly one: the map bar's '+' button.
  - The button is `MapChrome.kt:472-479`. It opens the add tile, whose "Waypoint" chip is at
    `AvailabilityMapControlsUi.kt:728`.
  - The chip leads to the centre-pin picker, then `WaypointNameDialog` (`AvailabilityCompactMapUi.kt:1286-1294`).
  - The dialog calls `onDropWaypoint`, which `MainActivity.kt:637` wires to `TrackRecordingViewModel.addWaypoint`.
  - No long-press is wired, and there is no other path. `CreateWaypointUseCase` is the only caller of
    `WaypointRepository.save`.
  - `addWaypoint` knows the recording through `uiState.activeTrack`, the same field the origin and end
    waypoints read.
- **GPX.** `shareTrackGpx` already exported every waypoint with the walk's `trackId`
  (`ui/track/TrackExportPanel.kt:247-252`). Before this change those were only the start and end.
- **The stop.** The walk view in Records has no map. It is a bottom sheet with a 96 dp drawing of
  the line only (`ui/log/TrackThumbnail.kt`). Four options went to the owner.
- **The owner (RECORD -618):**
  - "Dots on the drawing + list (Recommended)".
  - The heading "Waypoints on this track" (Recommended).
  - The start and end are left out of the list, and a walk with no dropped waypoints shows no section.
  - The Gradle go: "Yes, go".
  - Back, as the planner read "Back retraces the way in": the waypoint's details replace the walk's,
    and Back returns to the walk.

## What changed

- **`ui/track/TrackRecordingViewModel.kt:985-1000`.** `addWaypoint` reads
  `uiState.value.activeTrack?.trackId` when the name is confirmed and passes it to `createWaypoint`.
  Nothing else in the class changed. The origin and end links are untouched.
- **`ui/log/TrackThumbnail.kt`:**
  - `projectTracksToBox`'s arithmetic moved, unchanged, into a private `BoxFrame` (`:130`), so the
    walk's dots use the very same projection as its line. Its existing 12 tests pass unchanged.
  - `waypointsDroppedOn` (`:180`) returns the waypoints linked to the walk with no designation,
    oldest first. It filters the list the sheet already holds; there is no new query.
  - `walkMarks` (`:190`) and `projectWalkToBox` (`:211`) choose and place the dots. The bounding box
    takes in every dot, so a waypoint placed off the line still fits inside the drawing.
  - `WalkThumbnail` (`:232`) draws the line and the dots. The old `TrackThumbnail` still draws the
    Tracks-chip rows, unchanged.
- **`ui/log/RecordDetailsSheet.kt`:**
  - `RecordDetailsTarget.WaypointDetails` gains `fromTrackId` (default `null`). `returnsTo()` maps a
    waypoint opened from its walk back to that walk.
  - The saver stores the new form as `walk-waypoint:<track id><U+001F><id>`, so Back still works after
    an Activity is recreated. A saved value with no separator fails loudly.
  - `RecordDetailsSheet` gains `onOpenDetails`. Its dismiss path (Back, a scrim touch, a drag down)
    goes to `returnsTo()` when there is one (`:253`).
  - The sheet is keyed on its target (`:209`), so each target gets a fresh sheet. Without the key the
    sheet that Back has just hidden is reused and stays hidden; the revert check below shows this.
  - `TrackDetails` draws `WalkThumbnail` and then `WalkWaypointsSection` (`:379-384`, `:425`). The
    rows are at least 48 dp tall, tap anywhere, and carry the "Details for <name>" click label.
- **Hosts:** `RecordsTab.kt:360` and `MapBubble.kt:356` pass `onOpenDetails = { detailsTarget = it }`.
- **The 80% rule.** Nothing new is drawn over a map with its own fill. The rows and the heading sit
  on the sheet, which already takes the map chrome's alpha when it is over a map.
- **Not touched:** the database schema, the delete behaviour, the origin and end waypoints' links,
  navigation, and the exporter.

## Commits

- `4a44876a`: tests (red).
- `9813f287`: the code, plus the tests that need its new names (`WalkThumbnailTest`, and the saver
  test's new cases).
- This report and its index rows follow in a third commit.

## Tests, seen failing first

All 19 new tests run through the entry points below.

- **`TrackRecordingViewModelTest`** (4 tests, through `addWaypoint`, which `MainActivity.kt:637` calls):
  - "dropped while recording is linked ... read back as one of its waypoints", through the fake's
    `getForTrack`.
  - "dropped with no recording running stays standalone".
  - "dropped after the recording stopped is not linked".
  - "deleting the walk keeps its dropped waypoint ... with no link", through
    `requestRemoveTrack` and `commitRemoveTrack`.
  - The fake's `getForTrack` and `detachFromTrack` were explicit "unsupported" stubs. They now mirror
    `WaypointDao`'s two queries; this is commented in the test file.
  - The screen half of the path, from '+' to `onDropWaypoint`, is `AvailabilityScreenWaypointFlowTest`'s
    and was not changed.
- **`RecordDetailsSheetTest`** (6 tests, through the real `AvailabilityScreen` > Journal > Records,
  opened by a real touch on the walk's row):
  - The heading and rows are asserted text for text, in order. The start, end, standalone and
    other-track waypoints are asserted absent.
  - A walk with nothing dropped on it has no section.
  - Real touches at three points across a row each open the waypoint's details. A real Back key on
    the sheet's own window returns to the walk, and Back again closes it.
  - Navigate is offered on a waypoint opened from its walk.
  - The same Back test runs in a short landscape window.
  - Share decodes the written GPX with `GpxCodec.decode` and checks the exact set of id, trackId and
    designation: origin, the two dropped waypoints, end, and no others.
- **`AvailabilityScreenMapBubblesTest`** (1 test): track glyph > Details > a real touch on the row >
  the waypoint's details > a real Back > the walk's details, over the map.
- **`WalkThumbnailTest`** (6 tests, headless) and **`RecordDetailsTargetSaverTest`** (+2 tests, plus a
  round-trip case added to an existing test).

**Red run** at `4a44876a` (main's code with the tests). Build log: 0 `e:` lines; 88 tests, 7 failures.
Each failure is for the reason predicted:
- The two VM link tests: `expected:<track-1> but was:<null>`, and `linked before the delete
  expected:<[Chanterelle bank]> but was:<[]>`.
- The five sheet and bubble list and Back tests: no node with tag `record-details-walk-waypoints-heading`
  or `record-details-walk-waypoint-…`.

Some tests passed on `main` and were expected to. They guard behaviour that already held, and none of
them is cited as proof of the change:
- The two standalone tests: no recording, and after a stop.
- "no waypoint section": nothing was drawn before.
- **The GPX test.** Export already filtered by `trackId`. What is new is the link, which the VM tests
  prove; the GPX test pins the export half.

`WalkThumbnailTest` and the saver test's new cases name functions that did not exist yet, so on
`main` their red is a compile error, not an assertion. They were added with the code, and their bite
is shown by the revert checks below.

**Forward run** at `9813f287`, 8 classes. Build log: 0 `e:` lines. All pass:
- `TrackRecordingViewModelTest`: 46.
- `RecordDetailsSheetTest`: 27.
- `AvailabilityScreenMapBubblesTest`: 15.
- `TrackDeleteTest`: 14.
- `TrackThumbnailProjectionTest`: 12.
- `WalkThumbnailTest`: 6.
- `RecordDetailsTargetSaverTest`: 4.
- `RecordsLogbookTest`: 3.

## Revert checks

Each check followed the same steps:
- Save a copy of the file, then make the one-line edit.
- Compile, and refuse to read results if the build log has any `e:` line.
- Stop the Kotlin daemon.
- Delete the results directory, run the classes, and check that every XML file is newer than the run's start.
- Restore the file from the saved copy, never from git. Then confirm the file equals the saved copy
  and `HEAD`, so the forward change is present.

All six compiled with 0 `e:` lines, and all six restored and confirmed.

| Revert | Failures, each specific to the edit |
|---|---|
| `addWaypoint` passes `trackId = null` | The 2 VM link tests: `expected:<track-1> but was:<null>`; `linked before the delete expected:<[Chanterelle bank]> but was:<[]>` |
| `WalkWaypointsSection` not called | 4 sheet tests and 1 bubble test: no node `record-details-walk-waypoint(s)-…` |
| The sheet's dismiss always closes (`onDismiss`) | 2 sheet tests ("Back from the waypoint ... leaves a sheet showing"; title not found) and the bubble test ("record-details-sheet is not displayed") |
| No `key(target)` around the sheet | 2 sheet tests (the walk's heading not displayed after Back; the title is still "Spring pin"). **The bubble test passed under this revert**, so it alone does not guard the key. |
| `projectWalkToBox`'s box from the line only | `WalkThumbnailTest`, the widening case: line at x 50 instead of 0. `TrackThumbnailProjectionTest` still 12/12. |
| `waypointsDroppedOn` without the designation filter | The sheet's list test ("no walk-waypoint row for W-origin") and `WalkThumbnailTest` (`[origin, earlier, later, end]`) |

## Full suite

Both runs were on disk, with `--rerun` and the results directory deleted first. The 5 GB cap
required a change to how the suite runs, recorded below.

- **Before** (`4db24110`, a separate worktree): **3,890 tests, 2 failures, 24 skipped.** 473 XML files.
- **After** (`9813f287`): **3,909 tests, 2 failures, 24 skipped.** 0 `e:` lines. All 474 XML files
  were newer than the run's start. 3,909 = 3,890 + the 19 new `@Test`s; 474 = 473 + `WalkThumbnailTest`.
- **The 2 failures are the same before and after, on unchanged `main`, and are not touched here.**
  They are `AvailabilityScreenLandscapeB2Test` "S10 at ROTATION_90/ROTATION_270 while navigating,
  the HUD and the rest leave the central third clear". The navigation HUD measures 160 dp tall and
  crosses the central third: `navigation-hud DpRect(left=383.0.dp, top=0.0.dp, right=743.0.dp,
  bottom=160.0.dp)`.
  - The same app tree passed this class at `dae48454` earlier on 2026-10-06 (3,890, 0 failures,
    `2026-10-06-sundown-line-report.md`). It fails here at about 03:00 UTC on 2026-10-07.
  - **Inferred, not verified:** the test depends on the time of day. The HUD gained a sundown line
    in -592, and this test uses the real clock (`System.currentTimeMillis()` at
    `AvailabilityScreenLandscapeB2Test.kt:808`). Reported for the owner, not diagnosed further.

## Running under the 5 GB cap

The first baseline run under `MemoryMax=5G` with the repository's defaults was killed by the kernel's
OOM killer. The user journal shows: scope `run-p575659-i579183`, "Failed with result 'oom-kill'", a
5 GB peak. This matches the -592 report's measurement of about 5.4 GB with the Gradle daemon, the
Kotlin daemon and one test executor running together.

Two workarounds were tried:
- **`kotlin.compiler.execution.strategy=in-process` fails.** Compiling then stops with
  `NoSuchMethodError ... CompilerPluginRegistrar$ExtensionStorage.registerExtension`, and the build
  log reports that `e:`.
- **What worked**, and is used for every run above: compile first (`compileDebugUnitTestKotlin`),
  stop the Kotlin daemon, then run the tests with compilation already up to date. No build file
  changed.

`./gradlew --stop` was run at the end, and no Gradle or Kotlin daemon was left running.

## Disclosed edges (the planner asked for both)

- **The take-up gap.** When the app comes back to the foreground during a recording the service
  holds, `activeTrack` is filled by `takeUpRunningRecording` after a database read. A waypoint whose
  name is confirmed in that moment is saved standalone. Reading the service's own `returnWatch.state`
  instead was the alternative. It was not chosen because the screen could then link a waypoint to a
  recording it is not yet showing.
- **A stop mid-dialog.** The link is read when the name is confirmed, not when '+' is tapped. A
  recording stopped while the name dialog is open gives a standalone waypoint.
  - There is no test for this exact sequence. "Dropped after the recording stopped" covers the same
    state.

## Not done, or not verified

- **The drawing's dots are verified only as numbers.** `WalkThumbnailTest` checks which dots appear
  and where they land. Nothing checks that the Canvas draws them, or how the ring, dot and colours
  look. That needs eyes on a device.
- **Navigate from a waypoint opened from its walk** closes the sheet and navigates, as -502 built it.
  Back from that navigation reopens the waypoint's own details with no walk behind it (read at
  `RecordsTab.kt:225`, not tested), so a second
  Back closes the sheet instead of returning to the walk. Fixing this would touch navigation's
  origin type (`WaypointNavigationOrigin`), which the dispatch puts out of scope.
- **A scrim touch or a drag down** on a waypoint opened from its walk also returns to the walk,
  because the bottom sheet reports all three ways of closing through one callback. Only Back was ruled on.
- **The saver round-trip is tested; a recreation with the walk-waypoint sheet open is not.**
- **The Tracks-chip row's thumbnail** is unchanged and has no dots. The ruling covered the walk's sheet.
- **Device-only, listed and not run:** the dots' look on the S22, in light and dark; a real Back on
  the device; and '+' > Waypoint while recording, then Records > the walk.
