# Data part A: the entry report and "Leave out" (dispatch 2026-09-28-667)

Written 2026-10-07 (UTC) on branch `data-a-entry`, cut from `origin/main` at `340bdc4a` (PR #190).
The dispatch is `prompts/preserved/2026-10-07-11.md` (RECORD intent -667); the owner's choices are
RECORD -656; the data scout is `docs/ui/2026-10-07-data-scout.md` on branch `data-scout` (`00caf406`).
App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`.

**Status: code and tests written, nothing compiled or run.** No Gradle, emulator or adb, as the
dispatch says. Every test below is unrun, and no revert check has been done. Both wait for the
planner's go.

## What was built

**The entry report** (`ui/log/CartographyEntryReportScreen.kt`, new `ui/log/EntryReportSummary.kt`,
new `domain/EntryReport.kt`):

- The header date reads "Aug 1, 2026".
- Labelled tiles, two to a row: Distance, Time out, Climb, Finds. Each value has its unit.
- A small height profile drawn with Compose `Canvas`. Its highest and lowest heights, "Start" and
  the distance covered are text labels. When the walk has too few heights, there is one plain line
  instead. When a track's points can't be read, there is a different line. With no track included,
  nothing shows.
- The waypoints as a table: Name, Time, From start. A tap on a row shows "Coordinates: 45.0050,
  -122.0000" under it, and a second tap hides it.
- Below the table, the Tracks list stays: each track's name and "0.8 mi · 1 min". Then Finds
  ("Find on Aug 1, 2026") and Offline maps ("6 mi radius").

**Choosing what's in an entry** (`ui/log/CartographyEntryEditScreen.kt`, new
`ui/log/EntryContentsPanel.kt`, new `domain/EntryContents.kt`). The one-card-per-item list is
replaced by one panel:

- A summary line, e.g. "In this entry: 1 track, 2 waypoints, 1 find".
- Then "New since you last saved", listing each new item with Include and Leave out.
- Then one row per group (Tracks, Waypoints, Finds, Offline maps). Each row has a switch and a line:
  "All included", "Some left out", "All left out" or "New, not chosen yet".
- A tap on a group row (anywhere but its switch) opens or closes it. Inside, each item has its own
  switch.
- Under each track sit its Start, its End and the waypoints dropped while it recorded, read from
  `Waypoint.trackId`. They are indented.
- Which groups are open is held in `AvailabilityScreen`, so it survives a tab change.

**Group switch** (`ui/log/CartographyViewModel.kt`): `onSetEntryGroupIncluded(group, included)`. It
writes the same decisions, with the same `kept` values, as the per-item setters, in one change. It
is threaded `MainActivity` → `AvailabilityScreen` → `AvailabilityCompactScaffold` → `JournalTab` →
`CartographyScreen`.

**Words.** "Withhold" is now "Leave out" and "Keep" is now "Include". The report's empty-entry
message and both delete dialogs now say "included" where they said "kept". "Offline regions" is now
"Offline maps" on these screens.

**What a left-out item does is unchanged.** The same `kept` flag is written. Nothing changed in
`GetCartographyEntryMapDataUseCase`, `GetJournalEntryHighlightsUseCase`, the backup or the card
counts. Left-out items stay unreachable from the entry map.

## Premises checked

- **Confirmed: "Withhold" is shown only in the editor**, at the two buttons
  `ui/log/CartographyEntryEditScreen.kt:624,628` on `340bdc4a`. A grep of every quoted string in
  `main/` found no other place.
- **Confirmed: a new entry starts with everything kept, and "New" means a candidate with no
  decision.** See `CartographyViewModel.kt:146-157` and the editor's old `mergeDecisionRows`. This
  is unchanged.
- **Confirmed: the report renders from the decision snapshots only.** `TrackDecision` has no height
  and `WaypointDecision` has no time (`domain/model/CartographyEntry.kt`). So the Climb tile, the
  profile and the Time and From start columns need the live records. They read the day's candidates,
  which `onOpenEntry` already loads before the report's first frame (`CartographyViewModel.kt:203-244`).
  No new fetch and no schema change.
- **Confirmed: there is no chart library.** `gradle/libs.versions.toml` and `app/build.gradle.kts`
  have none, only `ui-graphics`.
- **Confirmed: the map still draws included items only.** `GetCartographyEntryMapDataUseCase` still
  filters on `kept`.
- **The scout is not on `main`.** `docs/ui/` does not exist on `main`; the scout is only on branch
  `data-scout`. This report creates `docs/ui/` on this branch.

## Premises that were wrong, or wider than the dispatch says

1. **"Everywhere it shows" reaches beyond this part's screens.** "Withhold" itself is only in the
   editor. Its opposite, "kept", also shows in two other places, which this branch leaves unchanged:
   - "Kept in N journal entries" in map bubbles (`ui/map/MapBubbles.kt:336`,
     `ui/map/JournalEntriesOnMap.kt:59`).
   - "Nothing kept" on journal cards (`ui/log/CartographyEntryCard.kt:167`,
     `ui/log/SidewaysEntryCard.kt:163`).

   See stop 1.
2. **The report was built to need no live records.** Its doc comment says so. Tiles and the profile
   now read live records for height and time. A track deleted from Records still counts in Distance
   and Time out (from its snapshot), but Climb then says "Not recorded" and the profile says its
   points couldn't be read.

## Stops for the owner

Each is built one way, and the other way is named. Nothing here changes what a left-out item does.

1. **"Kept" outside this part.** Should "Kept in N journal entries" (bubbles) and "Nothing kept"
   (cards) become, for example, "In N journal entries" and "Nothing included"? Left unchanged.
2. **What a group switch shows when some items are in and some are out.** Built: the switch is on
   while anything in the group is included, and its line says "Some left out". Turning it on
   includes everything in the group, new items too. Turning it off leaves everything out. The other
   way: the switch is on only when everything is included.
3. **Where a walk's dropped waypoints belong.** Built: the owner said "a track's Start, End and
   waypoints sit under the track", so every waypoint whose record names a track is in the Tracks
   group, and the Tracks switch leaves them all out with the walk. The Waypoints group holds only
   waypoints on no walk in this entry. The summary counts waypoints without Start and End, to match
   the owner's example ("1 track, 5 waypoints" for five dropped). The other way: the Tracks switch
   reaches only the tracks and their Start and End, and dropped waypoints stay in Waypoints.
4. **When the profile counts as too sparse.** Built: at least 10 points with a height, and at least
   half of all points. These are `MIN_PROFILE_HEIGHT_POINTS` and `MIN_PROFILE_HEIGHT_SHARE` in
   `domain/EntryReport.kt`. This is a judgement. Real altitude coverage on the owner's walks has not
   been read.
5. **What "From start" measures.** Built: the distance walked along the waypoint's own track up to
   the time it was made. It shows "—" for a waypoint on no included track, on a GPX import without
   times, or whose record is gone. The other way: straight-line distance from the walk's start,
   which a loop's End would show as about 0.
6. **"Time out" with two walks.** Built: their times added together, each from its first point to
   its last. The gap between walks is not counted. The other way: first start to last end.
7. **Which waypoint rows have their coordinates open.** This is the report's own state, so it resets
   when the report is left, like its offline switch and basemap. The panel's open groups survive a
   tab change. Say if the coordinate rows should too.
8. **Beyond the dispatch.** Kept below the waypoint table: the Tracks list (each track's name and
   line), the Finds list and the Offline maps list. Without them, a second track's name and the
   finds' names would not appear anywhere on the report.

### New user-facing strings, verbatim

Editor panel:
- "In this entry"
- "In this entry: 1 track, 5 waypoints, 6 finds, 1 offline map". This is the pattern, with
  singulars "track", "waypoint", "find", "offline map" and plurals "tracks", "waypoints", "finds",
  "offline maps".
- "Nothing in this entry yet. Turn on a group below to include it."
- "Nothing from this day to include yet."
- "New since you last saved"
- Group names: "Tracks", "Waypoints", "Finds", "Offline maps".
- Group lines: "All included", "Some left out", "All left out", "New, not chosen yet".
- Kind labels on new items: "Track", "Waypoint", "Find", "Offline map".
- Buttons: "Include", "Leave out".
- Item lines: "0.8 mi · 1 min" (a track); "Find on Aug 1, 2026"; "6 mi radius".
- Screen reader: "Show tracks" / "Hide tracks" (and the same for each group); "Include all tracks"
  (and the same for each group); "Include Ridge Loop" (an item's switch).

Report:
- Tiles: "Distance", "Time out", "Climb", "Finds"; "Not recorded" for an unknown climb.
- Times are written "1 h 12 min", "48 min", "2 h", "Under 1 min".
- "Height", "Start".
- "No height profile: the phone recorded too few heights on this walk."
- "No height profile: this walk's recorded points couldn't be read."
- "Height profile: lowest 328 ft, highest 509 ft, over 0.8 mi" (screen reader).
- Table: "Name", "Time", "From start", "—".
- "Coordinates: 45.0050, -122.0000".
- Screen reader: "Show coordinates" / "Hide coordinates".
- "Offline maps".

Changed:
- Empty report: "This entry has nothing included yet. An entry can hold the finds, tracks,
  waypoints, offline maps, and photos you choose to include from a day's records, plus anything you
  write. Tap the three-dot menu, then Edit, to add something."
- Delete dialog (editor and report): "This removes the entry and the choices made in it. The finds,
  tracks, waypoints, and offline maps it included stay in Records."
- "Photo unavailable (attached Aug 1, 2026)" now uses the new date form.

## Tests (written, not run)

| File | What it holds |
|---|---|
| `domain/EntryContentsTest.kt` (new, 7) | Waypoints sit under their track and loose ones on their own; counts leave out Start and End; group states; new items at the top only, each in the group whose switch settles it; no candidates; order stays put after a toggle; offline maps |
| `domain/EntryReportTest.kt` (new, 7) | Tile sums leave out left-out items; climb summed, and unknown when a track is gone or has no heights; NoTrack and PointsUnavailable; both sparse limits; a two-walk profile runs on from one walk to the next; waypoint time and along-track distance, with "—" cases |
| `domain/model/TimeSpanFormatTest.kt` (new, 1) | "Under 1 min" to "1 h 12 min" |
| `ui/log/EntryLabelsTest.kt` (new, 6) | The summary line (the owner's example, exactly); tiles with units in miles and kilometres; "Not recorded"; "0 ft · Under 1 min" in place of "0 ft · 0m"; "Oct 7, 2026"; the coordinates line |
| `ui/log/CartographyViewModelTest.kt` (+4) | The Tracks switch reaches the walk, its Start, End and dropped waypoint, and nothing else, stored at once for a draft; a saved entry is marked unsaved and stored on Save; a switch already in its state changes nothing; a group switch settles its new items |
| `ui/log/EntryDataScreensTest.kt` (new, 10) | Real `CartographyScreen` and `CartographyViewModel` over in-memory Room, with coordinate touches throughout. Editor: summary and closed groups; no "Withhold" or "Keep"; group row touches at three x positions open and close it, with indented waypoints in time order; five touches across a group switch and across an item switch, each checked in the ViewModel, the database and the switch; Include and Leave out on a new item. Report: tiles and profile labels with figures worked by hand; the waypoint table in order with times and distances; three touches showing and hiding coordinates; a walk with no heights giving its one line and "Not recorded" |
| `ui/log/CartographyEntryReportScreenTest.kt` (edited) | Moved to "Aug 1, 2026", "Offline maps", "Find on Aug 1, 2026" and the new empty message. The Finds count is now read from its tile, because "Finds" is both a tile label and a heading |
| `ui/availability/LeavingTheJournalFixesTest.kt` (edited) | The withheld-waypoint test now opens the Waypoints group and turns the waypoint's switch off with a coordinate touch. After the tab round trip it also shows the group is still open. It now passes `onSetEntryGroupIncluded`, as `MainActivity` does |

**Checks to run on the go.** Restore from saved copies, check the build log for compile errors
first, and confirm afterwards that the forward change is still present. Each revert is one line:

- R1: make the group setter move a changed decision to the end of its list. Expect "a group switch
  already in the asked-for state changes nothing" to fail.
- R2: put the waypoints under a track into Waypoints membership. Expect the Tracks switch tests to
  fail (ViewModel and screen).
- R3: drop `designation == null` from the waypoint count. Expect the counts and summary-line tests to
  fail.
- R4: drop the share test from the sparse limit. Expect "too few heights" (10 of 21) to fail.
- R5: drop the time `break` in `distanceWalkedBy`. Expect the waypoint distance tests to fail.
- R6: make the waypoint row's tap not toggle. Expect the coordinates test to fail.
- R7: use `trackSubtitle` in place of `labelledTrackLine`. Expect `EntryLabelsTest` and the track
  row assertion to fail.

**Phone only.** How the tiles, the chart and the table look at the S22's width; whether "1 h 12 min"
fits a tile; how the switches read with TalkBack. Robolectric does not render a Canvas, so the
profile's line is unseen by any test; only its data and labels are tested.

## What is unverified

- **None of the code has been compiled.** The `when` over `EntryItem`, the nullable `setOrAdd`
  chain in the ViewModel and the Compose imports were checked by reading only.
- The times in the waypoint-table test are formatted with the same Android clock format the screen
  uses. Only "9:00" is checked against the fixture.
- Robolectric's default locale is assumed to be en-US for "0.8 mi" in the screen tests.
  `EntryLabelsTest` pins it.
- The dispatch's citations from the scout that were not re-read are listed as the scout's. The ones
  read here held at `340bdc4a`.

## Amendment 1 (RECORD -671): the owner's answers, applied

The owner answered the stops above. The stops are left as they were asked, and this section records
what changed. The code is still not compiled and the tests are still not run.

**As built, confirmed by the owner:**
- Stop 2: a mixed group's switch is on, with "Some left out".
- Stop 3: dropped waypoints sit under their track.
- Stop 5: "From start" is the distance walked to the waypoint.
- Stop 6: Time out is the walking times added up.
- Stop 8: the strings above are approved.

**Changed:**

1. **"Kept" outside this part (stop 1).**
   - "Kept in N journal entries" is now "In N journal entries" (`ui/map/MapBubbles.kt`,
     `ui/map/JournalEntriesOnMap.kt`).
   - "Nothing kept" is now "Nothing included" (`ui/log/CartographyEntryCard.kt`,
     `ui/log/SidewaysEntryCard.kt`).
   - Observation, not a gate: a photo in a find and in entries now reads "In Chanterelle · In 2
     journal entries", with "In" twice. This is the owner's chosen wording applied as given.
2. **The profile threshold (stop 4).**
   - It is now one named constant, `PROVISIONAL_PROFILE_HEIGHT_LIMIT` in `domain/EntryReport.kt`,
     marked provisional. Its values are unchanged: 10 points with a height and half of all points.
   - The device step below reads real walks against it.
3. **Lists below the table (stop 7).**
   - Finds and Offline maps stay as lists.
   - Each track is now one name row with no distance or time line, since those are in the tiles.
   - A touch on the row opens the track's details sheet (`RecordDetailsSheet`) over the report.
   - A track known to be gone from Records (the day loaded and the track is not in it) cannot be
     opened, and its row says "No longer in Records". This is a **new string**.
   - If the day did not load, a missing track gets no note, because it is not known to be gone.
4. **Open coordinate rows (planner's call under the UX defaults).** Which waypoint rows show their
   coordinates is now held in `AvailabilityScreen`, as the editor panel's open groups are. It survives
   leaving the report for another tab and coming back.

### Tests changed in this amendment

**Assertions changed to the owner's new words:**
- `ui/map/JournalEntriesChipTest.kt`: "In 4 journal entries".
- `ui/map/JournalEntriesOnMapTest.kt`: "In 4 journal entries".
- `ui/map/MapBubblesTest.kt`: "In Chanterelle · In 2 journal entries".
- `ui/availability/JournalEntriesOnMapScreenTest.kt`: four lines.
  - The "one line" check used to filter the bubble's texts on `"Kept in" in it`. It now filters on
    `"journal entr" in it`, because "In" alone would also match the find line.
- `ui/log/JournalEntryCardsTest.kt`: "Nothing included".
- `domain/EntryReportTest.kt`: the sparse-limit test now first asserts the constant's values, so a
  change to the threshold fails there by name rather than through a case built on old figures.

**New tests:**
- `EntryDataScreensTest`: a track row is its name alone, and a coordinate touch on the empty part of
  the row opens its details sheet.
- `CartographyEntryReportScreenTest`: a track gone from a loaded day says "No longer in Records" and
  has no click action. The existing deleted-track test now also asserts no such note when the day did
  not load.
- `LeavingTheJournalFixesTest`: an entry report's waypoint row, opened to its coordinates with a
  coordinate touch, is still open after Maps and back to Journal.

### Device check: altitude coverage on real walks (for `PROVISIONAL_PROFILE_HEIGHT_LIMIT`)

Run on the S22 Ultra, after the build, on at least three real walks recorded on it. Use walks already
in Records; no new walk is needed.

**Step D1.** For each walk:
- Start a journal entry for its day (or open the existing one) and open the entry's report.
- Note which of three things shows under the tiles: a drawn height profile; "No height profile: the
  phone recorded too few heights on this walk."; or "No height profile: this walk's recorded points
  couldn't be read."
- Then open the same walk in Records, share its GPX full record, and count the `<trkpt>` elements
  and how many of them carry an `<ele>`.

**Pass condition.** Every walk with at least 10 points with a height, and with those points at least
half of all its points, shows a drawn profile. Every walk below either figure shows the "too few
heights" line. A mismatch is a bug.

**What D1 is for (an observation, not a gate).** It records the share of points with a height on each
walk. If real walks sit near or below one half, the limit needs the owner's decision, and the figures
from D1 go to the owner as a list.

**Evidence:**
- Per walk: the report's profile area in words (no screenshot committed).
- The two counts from its GPX.
- The share, computed as points with a height divided by all points.

## Amendment 2 (RECORD -673)

- "No longer in Records" is kept as written.
- A photo bubble's line that names both a find and journal entries now says "In" for neither, for
  example "Chanterelle · 2 journal entries". A line that names only one keeps its "In" ("In
  Chanterelle", "In 2 journal entries"). The change is in `photoAttachmentLine` in
  `ui/map/MapBubbles.kt`.
  - Assertions changed: `MapBubblesTest.kt:177`, and `JournalEntriesOnMapScreenTest.kt` at the two
    lines that show both ("Golden chanterelle · 1 journal entry", "Golden chanterelle · 2 journal
    entries").
- The track-row touch test now samples three touches across the row's bounds: near the left edge,
  at the centre, and near the right edge beside the name, each at a different height. Each sample is
  a fresh composition, one test each, in `EntryDataScreensTest`. Still not run.

## The build (RECORD -664's go), 2026-10-07

### Merge
`origin/main` at `1317369f`, which carries failure-fixes and motion Part 1, was merged into this
branch (merge commit `fcd9adfc`).
- **One conflict, `docs/audits/README.md`.** Both sides had appended index rows. Every row is kept:
  motion-part-1, then failure-fixes, then data-a-entry.
- **No logic was changed by both sides.** Main's other changes to files this branch also touches
  merged without conflict:
  - `IconButton` became `BouncingIconButton` in the entry editor, the report and `MapBubble`.
  - `MainActivity` now uses the shared location-permission check.
- **The bounce.** This part adds no icon button. The panel uses `Icon`, `Switch` and `TextButton`.

### How it ran
- Every run went through `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`.
- Gradle heap 1536m, Kotlin daemon 2g, Java temp `~/.cache/forager-test-tmp`.
- No daemon was running before the first run.
- Free disk was checked before each run: 5.0 GB at the start, 4.2 GB at the lowest.
- The Kotlin daemon was stopped after compiling and again before the full suite.
- `./gradlew --stop` ran at the end, and no Gradle or Kotlin daemon is left.
- No phone or emulator was used.

### Compile
Main and unit-test sources compiled on the first run, with no errors.

### New and edited test classes
13 classes, 177 tests, 1 failure.
- The failure was my own edit, `CartographyEntryReportScreenTest`'s "only kept decisions render":
  "Find on Aug 1, 2026" was off screen, below the new tiles, profile line and waypoint table.
- The fix scrolls to the lists before asserting; the assertions are unchanged (`a4077fb8`). On rerun
  the class passes, 13 of 13.
- `JournalEntriesOnMapScreenTest.kt` holds three classes (Portrait, ShortLandscape, FollowUps; 51
  tests). They were missed by the first run's filter and run separately, with 0 failures.
- `EntryDataScreensTest` passed 13 of 13 on its first run.

### Revert checks
Each check saved the file, made a one-line edit, ran the named classes and checked the log for
compile errors first. It then restored the file from the saved copy and confirmed the tree clean
against the committed forward change. Every check compiled, and each failure is one its edit can
cause:

| | One-line revert | Failed, with the message specific to the edit |
|---|---|---|
| R1 | group setter moves a changed decision last | `CartographyViewModelTest` "a group switch already in the asked-for state changes nothing" (1 of 29) |
| R2 | track's waypoints dropped from Tracks membership | 9 of 49: `EntryContentsTest` (4), `CartographyViewModelTest` Tracks switch (1), `EntryDataScreensTest` (4). The counts tests fail as well, because counts are read from membership |
| R3 | Start and End counted as waypoints | 6 of 26: `EntryContentsTest` counts (3), `EntryDataScreensTest` summary lines (3) |
| R4 | share test dropped from the profile limit | `EntryReportTest` "too few heights": expected `TooFewHeights(10, 21)`, was `Drawn(...)` |
| R5 | no time cut-off in distance from start | `EntryReportTest` waypoint rows (expected 0.0, was 1223.15); `EntryDataScreensTest` table ("0 ft") |
| R6 | waypoint row tap does nothing | `EntryDataScreensTest` coordinates, and `LeavingTheJournalFixesTest`'s tab-change test, both failing on the coordinates line |
| R6b | open coordinate rows held locally, not in `AvailabilityScreen` | `LeavingTheJournalFixesTest`'s tab-change test, at line 775, the assertion after the round trip |
| R7 | `trackSubtitle` in place of `labelledTrackLine` | `EntryDataScreensTest` group-row test: no "0.8 mi · 1 min" |

### Full suite
510 classes, 4,093 tests, 24 skipped, **3 failures**. All three are existing tests. They are listed
below and **not touched**, waiting for the planner's word.

`CartographyScreenTest`:
- "a committed entry shows its own Save action, not Finish entry": "Save" not displayed (`:251`).
- "tapping the screen's own Save asks to confirm, then persists ...": "Save this entry?" not
  displayed (`:268`).
- "Cancel in the Save confirmation dismisses it ...": "Save this entry?" not displayed (`:285`).

**Confirmed:** "Save" is not displayed, and the confirmation never opens after
`onNodeWithText("Save").performClick()`.

**Inferred, not measured:**
- The cause is that the editor's Save button now sits below the bottom of the screen.
- The class runs at Robolectric's default size, with no qualifiers. With no candidates, the old four
  sections drew nothing. The panel now always draws its heading and a summary line ("Nothing from
  this day to include yet.") above Save, which plausibly pushes Save past the bottom.
- `performClick` touches the node's centre, so it misses a button that is off screen.

**Proposed, test-only:** scroll to "Save" before asserting and clicking. The assertions would be
unchanged.

### The three CartographyScreenTest failures: cause confirmed, fixed (the owner, "Confirm cause, then scroll (Recommended)")

**The cause is confirmed by measurement.** I added a temporary probe to "a committed entry shows its
own Save action" and ran it alone. It compiled with no errors, and I then restored the file from a
saved copy. Just before the failing assertion it read these unclipped bounds:

| Node | Top | Bottom |
|---|---|---|
| Root | 0 dp | 470 dp (320 dp wide) |
| Save | 530 dp | 582 dp |
| Panel's "Nothing from this day to include yet." | 478 dp | 514 dp |

Save lies entirely below the screen; its clipped bounds are 0 x 0.

**The fix (`9a8a97e4`).** The three Save tests now call `performScrollTo()` on "Save" before
asserting on it and before clicking it. Every assertion is unchanged. The class now passes 14 tests
with 0 failures.

**Full suite, rerun** under the same rules, with no Kotlin daemon running and no compile errors in
the log: **510 classes, 4,093 tests, 24 skipped, 0 failures.** `./gradlew --stop` ran at the end, and
no Gradle or Kotlin daemon is left.

### Device check D2: an empty day's editor on the S22

Run on the S22 Ultra in portrait, at the default font size.
1. Open a saved journal entry for a day with no finds, tracks, waypoints or offline maps, or a
   saved entry where nothing is included and nothing new has arrived.
2. Open its editor (three-dot menu, then Edit entry).
3. Without scrolling, note whether the Save button is fully visible above the bottom bar.

This step records an observation and does not pass or fail. Robolectric's 320 x 470 dp test screen
puts Save at 530 dp, so on a phone it may sit below the fold too. The step records whether the
owner's phone shows it without scrolling.

**Evidence to record:** "Save visible without scrolling: yes or no", plus the last line of the panel
visible above the fold, described in words. Do not commit a screenshot.
