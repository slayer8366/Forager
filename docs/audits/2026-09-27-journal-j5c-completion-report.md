# 2026-09-27: Journal redesign J5c (tap a Records row for its details), completion report

Dispatch: `prompts/preserved/2026-09-27-28.md` (build, J5c coder). Plan: `docs/plans/journal-redesign.md`
(the J5c section, M1's Navigate ruling, J8, "Rules for every build stage"). Read before building: the J4,
J4b and J5 completion reports. Written by the J5c coder in a cloud worktree on local branch `j5c`, cut
from `origin/journal-redesign` at `463468f`.

**Resolution after the stop (planner's message, same day).**
- **Navigate is deferred by the owner.** Verbatim: "We can defer the navigation for another time.
  It's going to need more work anyway". And for M1: "Defer for now too. It will go with a review in
  current waypoint navigation." So the sheet stays Directions only. The analysis under "How in-app
  navigation starts today" and Needs a decision 1 is recorded as the input for that later review.
- **rv8 retried once, as the planner allowed, and it compiled and bit.** See the revert table (rv8b).
- **The planner runs the full suite on `3b09e43`,** so this session ran none after the report.

**Status: the sheet is built, tested and pushed for all three row types, with Directions and without
Navigate. Two things stopped the stage; the resolution above answers both:**

1. **Navigate stopped on the dispatch's own rule.** The app has no way to start its navigation HUD
   for a chosen waypoint (Premises, "How in-app navigation starts today"). The sheet ships with
   Directions only; what Navigate would need is under Needs a decision 1.
2. **Abort: a revert build did not compile.** Eight of nine revert checks ran and bit for the right
   reason. The ninth (rv8), an extra pin on the track row's Share button, did not compile. The runner
   refused its results and the file was restored byte-identical, but the dispatch makes this an
   abort, so I stopped there. **Not done because of the stop:** a re-run of rv8 with an edit that
   compiles, and a separate full-suite run after the last commit. The full suite *was* run from a
   cleared results directory on a tree whose production and test files are byte-identical to the
   pushed head (Suite before and after).

## Commits (all pushed to `journal-redesign`)

| SHA | What |
|---|---|
| `85df53a6` | Tests first: `RecordDetailsSheetTest` (21) and `RecordDetailsTargetSaverTest` (2), plus stubs (the Tracks row's test tag; `RecordDetailsTarget` with a saver that saves nothing). At this commit: 23 tests, 21 failures (below). |
| `b6a5ae52` | The build. No existing test changed. |
| this report's commit | This file. |

Production diff `85df53a..b6a5ae5`: 6 files, +528 / −39. New file: `ui/log/RecordDetailsSheet.kt`
(its stub landed in the tests-first commit).

## Premises checked before building

- **Base.** `origin/journal-redesign` was `463468f11f8899c0a635fcf6d91cb8ae152153fd` when fetched: the
  planner's commit after `e36ff86`, as the dispatch allows. It touches only `RECORD.md`,
  `docs/plans/journal-redesign.md` and `prompts/preserved/2026-09-27-28.md`.
- **Baseline**, measured at `463468f`, cleared results directory, `--offline`, `LC_ALL=C.UTF-8`, no
  `e: ` lines: **246 classes / 2029 / 1 / 0 / 24.** The failure is a held test, but a different one
  from the planner's: `JournalPendingDeleteTest` "a long-press anywhere on an album photo opens a menu
  of exactly Delete", with message "Expected exactly '1' node but could not find any node that
  satisfies: ((OnClick is defined) && (hasAnyAncestorThat(IsPopup is defined)))" at
  `JournalPendingDeleteTest.kt:1263`. **Its class alone at `463468f`: 52 / 0 / 0 / 0.** Not
  investigated (held by the owner).
- **Runner** (`app/build/j5c/run.sh`, `tally.py`, `revert.py`, `revert_batch.py` in this worktree's
  build directory, not the shared scratchpad). Every Gradle run clears `test-results` first, runs
  with `LC_ALL=C.UTF-8 --offline`, refuses to read results if the build log has any `e: ` line, and
  refuses a run with no XML. `revert.py` saves a copy, applies one edit (refused unless the old text
  occurs exactly once), runs the classes, restores from the saved copy (never git), compares byte for
  byte and prints `git diff --stat`. After the batch: `git status --short` was empty and `HEAD` was
  `b6a5ae5`, so the forward change was intact.

### The rows, and what each shows today (read at `463468f`)

| Row type | Composable, file | Composed by | What it shows |
|---|---|---|---|
| Waypoint | `WaypointRow`, `ui/availability/AvailabilityTripsWaypointsUi.kt:230` | `WaypointsSection` (`:180`, the Waypoints chip) and `RecordsLogbookList.kt:156` (All), each in a J4b `TwoStageSwipeRow` | name (`titleSmall`); MGRS (left out when `MgrsCoordinate.Unsupported`); lat/lng to 4 places; a Directions `IconButton` (`launchDirections`); inside a `Card`. No time. |
| Recorded track | `TrackExportRow`, `ui/track/TrackExportPanel.kt:92` | `TrackExportList` (`:87`, the Tracks chip) and `RecordsLogbookList.kt:145` (All). No swipe (tracks have no delete). | 40 dp `TrackThumbnail`; start time as the title (`formatTrackTimestamp`, `DISPLAY_FORMAT` "MMM d, yyyy, h:mm a" at `:163`); `trackSubtitle`: "N points", plus the network-fix note when it applies, plus "· recording"; a Share `IconButton` (GPX export). |
| Offline region | `OfflineRegionRow`, `ui/availability/AvailabilityOfflineMapsUi.kt:421` | `OfflineRegionsSection` (`:345`, the Offline maps chip, under the picker) and `RecordsLogbookList.kt:169` (All), each in a `TwoStageSwipeRow` | name; "Stale" when stale; a detail line: radius around lat/lng, tile count, size in MB, "downloaded N days ago" (`relativeTimeLabel`); the zoom-readiness paragraph. No buttons. |
| All logbook | `BadgedRow`, `ui/log/RecordsLogbookList.kt:200` | `RecordsLogbookList` | the rows above with a `RecordTypeBadge` in front. |

**Both trees:** `JournalTab` (`JournalTab.kt:563`) and `LogPanel` (`LogPanel.kt:404`) both compose
`RecordsTab`, and both pass it `waypoints`, `tracks`, `availabilityUiState` and
`waypointEntryReferenceCounts`. So everything the sheet needs is already at `RecordsTab`, and neither
caller changed.

### What the sheet reads, and where it comes from (all already in memory)

- **Waypoint created:** `Waypoint.createdAtEpochMillis` (`domain/model/Waypoint.kt`), printed with
  `DISPLAY_FORMAT` (`TrackExportPanel.kt:163` at base), the Records rows' one existing date-and-time
  formatter (`CrashLogPanel.kt:192` carries the same pattern). Made reachable as
  `formatRecordTimestamp(epochMillis)`; the row's own `formatTrackTimestamp` now calls it.
- **Waypoint's track:** `Waypoint.trackId`, looked up in `RecordsTab`'s `tracks`. Tracks load at the
  ViewModel's init (`TrackRecordingViewModel.kt:191`, `loadTracks()`).
- **"Used in N journal entries":** `RecordsTab`'s `waypointEntryReferenceCounts`, unread since J4 (it
  carried `@Suppress("UNUSED_PARAMETER")`), fed from `TrackRecordingUiState.waypointEntryReferenceCounts`
  (`MainActivity.kt:547`), which `loadWaypoints` fills for every waypoint
  (`TrackRecordingViewModel.kt:579-582`).
- **Track distance and duration:** `ComputeTrackStatisticsUseCase()(track.points)`, the same
  in-composition derivation the entry editor's candidate rows use (`CartographyEntryEditScreen.kt:497`).
  Distance goes through `formatDistanceMeters`, as those rows do (that file's `trackSubtitle` doc comment
  says why not `formatDistanceKm`).
- **Region downloaded date:** `OfflineRegionSummary.createdAtEpochMillis` (`domain/OfflineMapRepository.kt:204`),
  already shown relative on the row.
- **No new query, column, migration or ViewModel state** (planner's prediction 1: holds).

### How in-app navigation starts today (the Navigate premise)

- **The HUD appears only in return mode.** `AvailabilityScreen.kt:775`: `val isNavigating = isReturning`.
- **Return mode starts only through `TrackRecordingViewModel.startReturn()`** (`:414`), which returns at
  once unless a recording is active (`:415`, `val active = uiState.value.activeTrack ?: return`). Its
  one caller is `MainActivity.kt:554` (the map's return toggle).
- **The target is always the active recording's origin waypoint:** `MainActivity.kt:558`,
  `navigationTarget = trackUiState.originWaypoint`. `startReturn`'s own doc comment calls this
  coupling "stage one's deliberate smallness" and expects stage two's target picker to loosen it.
- **The HUD is mounted only in the compact map** (`AvailabilityCompactMapUi.kt:1072` is the one call
  of `NavigationHud`); its exit is `onToggleReturning` (`:1081`). The wide tree's map has no HUD.
- **No route from the Journal to the map tab.** `AvailabilityScreen` can switch `compactTab` to the
  map (`onViewSpeciesOnMap`, `:759-763`), but nothing reaches `JournalTab`, `LogPanel` or `RecordsTab`.

So starting the HUD for a waypoint from Records needs a path that does not exist. **Stop rule applied:
the sheet ships with Directions only.** Needs a decision 1 lists what Navigate would need.

### Premises that were wrong, and predictions

- **The dispatch's "Find how in-app navigation starts today ... if it needs a path that does not
  exist".** It does need one, and more than a route: a navigation mode not tied to recording.
- **Planner's prediction 2** ("Navigate needs a small new route (the HUD starts from the map)"):
  **partly wrong.** The tab route is the small part. The HUD also has no target other than the
  recording's origin, no mode other than return, and no mount in the wide tree.
- **Prediction 1** (no new query or ViewModel state): holds.
- **Prediction 3** (the suite grows by 15 to 35): holds, +23.
- **The baseline's held failure** is a different album test from the one the planner named (above).

## What was built

- **`ui/log/RecordDetailsSheet.kt` (new).**
  - `RecordDetailsTarget`: waypoint, track or region, by id.
  - `RecordDetailsTargetSaver`: saves `waypoint:<id>`, `track:<id>` or `region:<id>`; an unknown
    string fails loudly on restore.
  - `Modifier.opensRecordDetails(name, onClick)`: a `clickable` with the TalkBack label "Details for
    <name>", used by every row type.
  - `RecordDetailsSheet`: a Material 3 `ModalBottomSheet`, opened expanded
    (`skipPartiallyExpanded = true`), with scrolling content:
    - **Waypoint:** title (name); MGRS (left out when unsupported, as on the row); Coordinates;
      Created; Track (the track's name or start time, or "Not loaded" when its track is not in the
      list); "Used in" ("2 journal entries" / "1 journal entry" / "no journal entries"), left out
      when no count was given. Action: **Directions** (`launchDirections`).
    - **Recorded track:** title (name, else start time, as on the row); a 96 dp thumbnail; Started;
      Ended (or "Still recording"); Distance; Duration ("1h 10m"); Points; the network-fix note when
      it applies. Action: **Share** (the row's own GPX export).
    - **Offline region:** title (name) and "Stale" when stale; Radius; Centre; Tiles; Size;
      Downloaded ("<date> (70 days ago)"); the zoom paragraph.
  - Each field is one merged node tagged `record-details-field-<key>`, so TalkBack reads "Radius,
    9 mi" as one item.
  - A target whose record has left the list closes the sheet (not an error: a delete landed or a
    list reloaded).
- **`RecordsTab.kt`:** holds the open target in `rememberSaveable` and passes an open callback to the
  logbook, `WaypointsSection`, `OfflineMapsPanel` and `TrackExportList`. It composes the sheet from
  the lists it already has, and now reads `waypointEntryReferenceCounts` (the `@Suppress` is gone).
- **Rows:**
  - `WaypointRow(onClick)`: the card takes the tap, clipped to the card's shape.
  - `OfflineRegionRow(onClick)`: the row takes the tap.
  - `TrackExportRow(onClick)`: the row takes the tap and carries the tag `track-row-<id>`.
  - In All, `BadgedRow(onClick)` takes the tap over the whole badged row, badge included, and the
    inner rows get `null`.
  - The row's own buttons are children of the clickable node, so they keep their own taps.
- **J4b's rule needed no change.** While a `TwoStageSwipeRow` is open, its overlay
  (`TwoStageSwipe.kt:297`) lies over the content and takes a tap to close the row, so the row's click
  never sees it. rv2 below proves this.
- **Behaviour-preserving extractions,** so the row and the sheet print the same thing:
  - `formatRecordTimestamp` and `trackTitle` (`TrackExportPanel.kt`);
  - `shareTrackGpx`, the row's Share body, now shared with the sheet;
  - `decimalDegreesLabel` (`AvailabilityTripsWaypointsUi.kt`), used by the waypoint row, the region
    row and the sheet;
  - `offlineRegionSizeLabel` and `offlineRegionZoomNote` (`AvailabilityOfflineMapsUi.kt`).
- **`OfflineMapsPanel`** gained one pass-through parameter, `onOpenRegionDetails` (default `null`).
  It is the only way to reach `OfflineRegionsSection`; the picker and download code do not read it.

## Tests: base failures (`85df53a6`, the tests-first tree, run before the build existed)

23 tests, **21 failures**, each for its stated reason:

- **No sheet after a row tap** in every row-tap test: "a tap at Offset(0.1, 0.5) of
  records-swipe-waypoints-W1 opens the details sheet"; likewise for `records-logbook-row-tracks-T1`,
  `track-row-T1` and `records-swipe-offline-maps-7`, and in the landscape and wide-tree tests.
- **The title node absent** where a test taps once and reads the title ("Text = [Ridge loop]",
  "[Oak pin]", "[Creek pin]", "[Molalla Ridge]"), including the two open-swipe tests at their
  closing positive control.
- **The sheet not displayed** for the Back and scrim tests.
- **`performScrollTo` failing** on the sheet's Directions and Share buttons.
- **No "Details for" click label.**
- **The stub saver:** `expected:<WaypointDetails(id=wp-creek)> but was:<null>`, and "expected
  java.lang.IllegalStateException to be thrown, but nothing was thrown".

**Passing at base by construction (2):** "the waypoint row's own Directions button starts directions
and does not open the sheet" and "the track row's own Share button starts the GPX share and does not
open the sheet". They describe today's behaviour. The Directions one is shown to bite by rv7. The
Share one's first revert (rv8) did not compile; its retry (rv8b) compiled and bit.

**Harness fixes made before the tests-first commit** (from iterating the test against the draft build,
before any of it was committed):
- the sheet's actions are scrolled into view before `assertIsDisplayed`, since the content scrolls in
  a 384 dp window;
- the class uses the repo's `FileProviderCacheReset` rule, since two of its tests share through
  `FileProvider` and the second otherwise failed on the first one's stale data directory ("Failed to
  find configured root").

What the tests assert:
- Real `performTouchInput` taps at three points across each row (start edge / badge, upper middle,
  lower right short of the buttons), each a fresh open-and-dismiss.
- Every row type in All and in its own chip, in portrait, in `w823dp-h384dp-land` (All and chips),
  and in the wide tree (`w840dp-h1024dp-mdpi`, waypoint and region).
- Each field's merged text, label and value, e.g. `[Distance, 1.4 mi]`, `[Radius, 9 mi]`,
  `[Size, 12.3 MB]`, `[Used in, 2 journal entries]`.
- The line left out when no count was given; "Not loaded"; "Still recording".
- The row's own Directions and Share start their intents and open no sheet.
- A tap on an open swipe row closes it and opens no sheet (three points in the Waypoints chip; a region
  row in All; a waypoint row in landscape), and the closed row then opens the sheet.
- Back sends a real `KEYCODE_BACK` down and up to the sheet's own dialog window and closes only the
  sheet: the Waypoints chip stays selected.
- A scrim touch (on the node described "Close sheet", at a point the sheet does not cover) closes it.
- The sheet's Directions starts `geo:0,0?q=45.326,-122.634(Creek%20pin)`; its Share starts the GPX
  chooser; there is no "Navigate" text.
- "Details for …" click labels on all three row types, in All and in each chip.

## Revert checks

Each ran through `revert.py` against committed state `b6a5ae5`, restored from its saved copy (identical
by byte comparison each time), with `git status` empty and `HEAD` `b6a5ae5` after the batch.

| Check | Edit | Result (0 `e:` lines unless stated) |
|---|---|---|
| rv1 sheet opening | `RecordsTab`'s `openDetails = { target -> detailsTarget = target }` → `{ _ -> }` | 18/21 fail: every row-tap, Back, scrim and sheet-action test ("a tap at … opens the details sheet", "record-details-sheet is not displayed"). The 3 that pass need no sheet: the two row-button pins and the click-label test. |
| rv2 open-swipe exception | J4b's overlay condition (`TwoStageSwipe.kt:297`) → `if (false)` | 3/21 fail: the three open-swipe tests, "the tap … closed the open row" |
| rv3 waypoint content | sheet's Coordinates `decimalDegreesLabel(lat, lng)` → `(lng, lat)` | 6/21 fail: every waypoint-content test, `[Coordinates, 45.3260, -122.6340]` |
| rv4 track content | sheet's Distance unit → `DistanceUnit.KILOMETERS` | 4/21 fail: every track-content test, `[Distance, 1.4 mi]` |
| rv5 region content | sheet's Radius unit → `DistanceUnit.KILOMETERS` | 5/21 fail: every region-content test, `[Radius, 9 mi]` |
| rv6 no made-up zero | `referenceCounts[id]?.let` → `(referenceCounts[id] ?: 0).let` | 1/21: "a waypoint with no count given has no Used in line, rather than a made-up zero" |
| rv7 row's Directions keeps its tap | the button's `onClick` → `onClick ?: { launchDirections(…) }` | 1/21: `expected:<[geo:0,0?q=45.326,-122.634(Creek%20pin)]> but was:<[null]>` |
| **rv8 row's Share keeps its tap** | the button's `onClick` → `onClick ?: { scope.launch { … } }` | **Did not compile** (`TrackExportPanel.kt:151:23 Argument type mismatch: actual type is 'Function0<Any>', but 'Function0<Unit>' was expected`). **Results refused, nothing cited. The abort.** |
| **rv8b** (the one allowed retry) row's Share keeps its tap | the button's `onClick = { scope.launch { … } }` → `onClick = { if (onClick != null) onClick() else scope.launch { … } }` (the expected type stays `() -> Unit`, so it compiles) | **0 `e:` lines.** 1/21: "the track row's own Share button starts the GPX share and does not open the sheet", `ComposeTimeoutException: Condition still not satisfied after 5000 ms`. The chooser never started, because the button opened the sheet instead. Only this edit makes the row's Share start nothing. Restored from the saved copy, identical; `git status` empty; the forward line is present at `TrackExportPanel.kt:151`. |
| rv9 saver fails loudly | `else -> error(…)` → `else -> null` | 1/2: "expected java.lang.IllegalStateException to be thrown, but nothing was thrown" |

Every failure named is one only its own edit could produce: the field and value in each message
belong to the reverted line.

## Tests moved or rewritten

None. The full suite on the build broke no existing test, so no file needed a count.

## Suite before and after

| | Classes | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| Baseline, `463468f` | 246 | 2029 | 1 (held album long-press test, above) | 0 | 24 |
| Build, cleared results, 0 `e:` lines | **248** | **2052** | **1** (held, below) | **0** | **24** |

- **Which tree the build row is.** It was run on the working tree whose six production files were
  then committed as `b6a5ae5`: the files were backed up after that run and compared byte for byte with
  the committed ones, all identical. Its test files were the ones committed in `85df53a` (unchanged
  after the run before it). So it is the pushed code, but it was run before the commits, and **no
  full suite was run after the last commit**, because of the abort.
- **+2 classes** (`RecordDetailsSheetTest` 21, `RecordDetailsTargetSaverTest` 2), +23 tests, skipped
  unchanged.
- **The one failure is the held `JournalTabTest` photo-pull test:** "From Album on the edit form opens
  the picker and pulls the selected photo into the entry", `The component with ContentDescription =
  'Log photo' (ignoreCase: false) is not displayed!`. **Its class alone at `b6a5ae5`: 17 / 0 / 0 / 0.**
  The held album test that failed at baseline passed in this run. Neither investigated.

## Needs a decision

1. **Navigate (owner, with the planner).** What it would need, from the code above:
   - (a) **A way to navigate to a chosen waypoint without recording.** Today the HUD's only mode is
     return (`isNavigating = isReturning`) and its only target is the recording's origin. So: a new
     ViewModel entry point (on `TrackRecordingViewModel` or a navigation ViewModel of its own) and state
     holding the target. Also: what the HUD's exit does in that mode (today `stopReturn`), and whether
     path home and off-track detection (return-only today) apply.
   - (b) **`AvailabilityScreen`'s `isNavigating` and `navigationTarget`** fed from that state as well as
     from return mode, including which wins if both are live.
   - (c) **A route from `RecordsTab` → `JournalTab` / `LogPanel` → the scaffold → `AvailabilityScreen`**
     that switches to the map tab (the `onViewSpeciesOnMap` pattern exists, but not for this path).
   - (d) **The wide tree has no HUD at all:** Navigate from `LogPanel` would need the HUD mounted in
     the wide map, or would be left out there.

   M1's glyph bubbles need the same Navigate. **Options:** build (a) to (d) once, as its own stage or
   inside M1, and add the sheet's button then; or leave the sheet with Directions only. Built:
   Directions only. **Answered: deferred by the owner** (Resolution, above), for both the sheet and
   M1, until a review of the current waypoint navigation. (a) to (d) are that review's input.
2. **The abort.** rv8 was an extra pin I added, not one of the dispatch's required checks (sheet
   opening, open-swipe exception, each type's content, which all ran and bit). Whether to re-run it
   with a compiling edit (for example `onClick = if (onClick != null) { { onClick() } } else { … }`),
   and whether to run a full suite on `b6a5ae5` itself, is the planner's call. **Answered:** one retry
   allowed, which compiled and bit (rv8b). The planner runs the full suite on `3b09e43`.

## Decisions I made that the dispatch did not

1. **In All, the whole badged row takes the tap, badge included;** in the chips, the row itself does.
   Rejected: the tap on the inner row only, which would leave the badge as a dead area of a row that
   otherwise opens.
2. **The sheet opens fully expanded** (`skipPartiallyExpanded = true`), so every field shows without a
   drag in portrait. Rejected: the half-height first stop, which would hide the actions on a phone.
3. **"Used in" is left out when the waypoint has no entry in the counts map,** instead of J4's
   "missing counts as zero" (which the snackbar uses at pend time). A missing entry means no count was
   given (a caller passing none, or the moment before counts load), and printing zero there would be a
   made-up value (CLAUDE.md). `MainActivity`'s logged zero fallback on a failed count read
   (`waypointEntryReferenceCountOrZero`, J4 D6) is still shown as zero, as the snackbar does.
4. **A waypoint whose track is not in the list shows "Track: Not loaded".** Rejected: leaving the line
   out, which would read as "no track".
5. **Duration is first-to-last point** (`ComputeTrackStatisticsUseCase`, as the entry cards and editor
   use), **while Started/Ended are the track's own start and end.** The two can disagree: the test
   track spans 75 minutes but shows "1h 10m". Both are today's figures from their own sources; I did
   not reconcile them.
6. **The duration format is repeated** in `formatTrackDuration` (the sheet file). The only existing
   one is inside `trackSubtitle(distance, duration, unit)` in `CartographyEntryEditScreen.kt`, which
   returns both figures as one string and is outside J5c's files.
7. **Downloaded shows the date and the age,** "Sep 20, 2026, 6:42 PM (70 days ago)"; the row shows
   only the age.
8. **The open sheet lives in `RecordsTab`'s `rememberSaveable`,** not in `JournalScreenState`. Leaving
   the Journal tab with the sheet open is not possible (the modal scrim covers the bottom bar and the
   rail), and a rotation keeps `RecordsTab` in place (J5 L7).
9. **Not in the sheet:** the waypoint's altitude and note (not on the row, not asked), and a region's
   journal-entry count (not in memory at the row: it is read only when a delete is requested,
   `getOfflineRegionReferenceCount` at `MainActivity.kt:80`).
10. **Wording:** "Used in / 2 journal entries" (J4's nouns); "Ended / Still recording" (standing in for
    the row's "· recording"); "Not loaded"; "Details for <name>", where a track's name is its start
    time when it has none.

## Device-only items

- The sheet's insets: Robolectric reports zero (CLAUDE.md). Check the navigation bar and the cut-out
  in both landscape rotations, and the sheet's top in portrait.
- In a short landscape window the sheet's actions start below its fold and the content scrolls (the
  tests had to scroll to reach Share). Check that this reads clearly on the S22 Ultra.
- Drag-to-dismiss and the predictive Back animation (only the Back key and the scrim were tested).
- TalkBack: "Details for …" announced on each row; each field read as one item; the swipe row's
  Delete action still listed beside the click.
- The ripple on a waypoint card (clipped to the card's shape) and on the badged row in All.
- Directions and Share handing off to real apps from inside the sheet.
- Rotating with the sheet open (saveable state; not driven by a test).

## Flags outside scope

- **`MainActivity`'s wiring is exercised by no test,** as J4 and J4b recorded: the tests drive
  `AvailabilityScreen`. J5c changed nothing in `MainActivity`.
- **The Share pin**, unproven when this report was first written, is proven by rv8b.
- **The baseline and the build run each failed a different held test** (album long-press at base,
  `JournalTabTest` photo pull on the build), and each passed in its class alone. Recorded for the flake
  session, not investigated.
