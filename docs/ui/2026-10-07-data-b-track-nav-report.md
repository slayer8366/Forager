# Data part B: the track sheet and the navigation display (dispatch 2026-09-28-677)

Written 2026-10-07 (UTC) on branch `data-b-track-nav`, cut from `origin/motion-part-2` at `008ebbab`, with
`origin/data-a-entry` (`fcd9adfc`) merged in at `3a834aa0`. The dispatch is `prompts/preserved/2026-10-07-14.md`
(RECORD intent -677); the owner's choices are RECORD -656; the data scout is `docs/ui/2026-10-07-data-scout.md` on
branch `data-scout` (`00caf406`). App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`.

**Status: code and tests written, nothing compiled or run.** No Gradle, emulator or adb, as the dispatch says. Every
test below is unrun and no revert check has been done; the widths quoted are estimates (below), not measurements from
the app. All of that waits for the planner's go.

## Premises checked

| Premise | Found |
|---|---|
| Cut from `origin/motion-part-2` at 008ebbab | Yes, 008ebbab. Not merged to main (main is 1317369f). |
| Track figures already computed, `RecordDetailsSheet.kt:377` | Yes, at `:380` on this base (the line moved). `TrackStatistics` already carries gain, loss, `averageSpeedMetersPerSecond` and `pointsWithAltitude`; only distance and duration were read. |
| Data part A's tiles, height profile and `TimeSpanFormat` | On `origin/data-a-entry` (fcd9adfc), **not merged to main**. See "How A is shared". |
| Part 2's `WordSwap` | Yes, `ui/motion/WordSwap.kt`: it crossfades when the words change and changes at once when only the numbers do. |
| Landscape display width | `LANDSCAPE_HUD_MAX_WIDTH = 360.dp` (`AvailabilityCompactMapUi.kt:1386`), so "at 360 dp" is both the small phone in portrait and every phone in landscape. |
| A speed formatter exists | No. One was added (`domain/model/SpeedFormat.kt`). |

Found while checking, not in the dispatch:

- **"within 16 ft" can no longer appear on the GPS display.** Arrival (`hasArrived`) counts within `max(15 m, 2 ×
  accuracy)`, which is always more than the accuracy, and `navigationReadout` tests arrival before it formats the
  straight line (`NavigationHud.kt`, the `distanceText` `when`). So any distance inside the fix's circle reads
  "Arrived" first. The doc comments that still describe "within 16 ft" on the display are stale (since -497/-502). The
  new "straight" word is left off a "within" figure anyway, but that branch cannot be reached. Not changed.
- **A status line that is probably cut already.** "Approaching · last fix 45 s ago" is about 187 dp wide, and the
  distance column at 360 dp is about 130 to 175 dp. The line is `maxLines = 1` with no ellipsis, so it is likely cut
  today. This is an estimate, not seen on a phone. It is older than this part and not changed.

## How A is shared

Data part A is merged into this branch (`3a834aa0`) rather than copied, so there is one set of tiles, one height
profile and one `formatTimeSpan`. The only conflict was the audits index, resolved by keeping both rows. Two small
edits were made to A's files so the track sheet can use them:

- `domain/EntryReport.kt`: `heightProfileOf` is no longer private.
- `ui/log/EntryReportSummary.kt`: the tile layout is now `LabelledTiles(tiles, tagOf, overMap)`. `EntrySummaryTiles`
  calls it with no change to its output or tags. Over a map, the tiles are outlined instead of filled (see the track
  sheet).

**Consequence:** this branch carries all of A. It should merge after A, or with A. If A changes before it merges,
this branch needs A merged in again.

Git also merged `AvailabilityCompactScaffold.kt` and `MapBubble.kt` by itself (both A and Part 2 touch them). That
result is not compiled.

## What was built

### Navigation display and strip (the owner: "Plain words + labels (Recommended)")

- **Turns in words**, from `domain/NavigationWords.kt` (`turnFor`, `turnWords`, `turnText`). Examples: "Slight left ·
  10°", "Right · 45°". The number is the size of the turn, 0 to 180, never 0 to 359. The arrow is unchanged. The
  approximate-position display uses the same words.
- **The distance says what it measures**: "by trail" after the route figure, and "straight" after a straight-line
  figure. The word sits on the same line in the smaller status type, after the big figure, which is laid out first so
  it is never the one cut short. While returning, the status line reads "≈ 0.7 mi straight" where it read "Straight
  line ≈ 0.7 mi". The approximate display reads "≈ 3.2 km" followed by "straight". The words fade when they change;
  the numbers change at once (`WordSwap`).
- **Labels**: "Facing" before the heading and "Alt" before the altitude, on the display and on the strip. A status
  that names itself ("Compass unavailable", "Compass unreliable", the facing notices, "Elevation unavailable") gets no
  label. The label is its own text node (`LabelledReadout`, tag `navigation-label`), so the reading's node still holds
  the reading alone.
- **The heading moved on the display**: from under the north arrow to the start of the second row, as "Facing 45° NE
  · Alt 164 ft · 10T …". The north arrow stays in the first row as an icon only. This is **stop 2**, with the widths
  that forced it.
- **Times** on these screens: the sundown line already follows the phone's 12- or 24-hour setting and is unchanged.

### Track sheet (`ui/log/RecordDetailsSheet.kt`; the owner: "Tiles + profile, raw tucked away (Recommended)")

From the top:

1. The title.
2. The drawing.
3. "Waypoints on this track", kept directly under the drawing where -618 put it.
4. Tiles, two to a row: Distance, Time, Climb, Descent, Avg speed.
5. Data part A's height profile, or its one line saying why there isn't one.
6. Started, Ended and Imported.
7. The network-fix note, kept in view because it explains a gap.
8. A **"Details" fold**, closed at first, holding Points and "With height: N of M points".
9. The actions.

What the figures are:

- **Time** is first point to last, as Duration was, in A's words ("1 h 10 min").
- **Climb and Descent** are the existing hysteresis-filtered gain and loss. "Not recorded" with no heights.
- **Avg speed** is distance over that same time, so stops count against it: "1.2 mph" or "1.9 km/h". It is a dash when
  the first and last points share a time.
- A file with no times says "No times in file" for Time and Avg speed.

Other behaviour:

- **Over a map** (a bubble's Details), the tiles are outlined with no fill. The sheet is already at 0.8, and any fill
  on top would composite past it (CLAUDE.md, "Nothing fully obstructs the map view"). Off a map they are filled, as
  A's are.
- **The fold's state** is held in `AvailabilityScreen` (`LocalTrackDetailsFold`, `rememberSaveable`). Opened once, it
  stays open on every track's sheet for the session (CLAUDE.md, UX defaults). It is not kept across a restart.
- **Times** on the sheet follow the phone's 12- or 24-hour setting: "Oct 7, 2026, 2:14 PM" or "Oct 7, 2026, 14:14".
  This applies to the title of an unnamed walk, Started, Ended, Imported and the walk's waypoint times. On a 12-hour
  phone this is exactly the Records rows' existing format.

## Stops for the owner

1. **The turn bands** (the dispatch's stop). The bands are chosen on the turn rounded to a whole degree, so the words
   always match the number shown:

   | Turn | Words |
   |---|---|
   | 0° to 9° | "Ahead · 4°" |
   | 10° to 44° | "Slight left · 10°" / "Slight right · 10°" |
   | 45° to 134° | "Left · 45°" / "Right · 45°" |
   | 135° to 169° | "Sharp left · 150°" / "Sharp right · 150°" |
   | 170° to 180° | "Behind · 175°" (no side; the arrow shows it) |

   10° as Slight and 45° as a plain turn are fixed by the owner's own examples. The 135° and 170° edges, keeping the
   degrees on "Ahead" and "Behind", and having no side on "Behind" are the coder's choices. The constants are in
   `domain/NavigationWords.kt`.
2. **The heading moved to the second row, and that row shows in more states.** This is the layout change and a
   height question.
   - Why it moved: at 360 dp the first row has about 260 dp for the north column, the turn column and the distance.
     "Facing 315° NW" (about 97 dp) plus "Sharp right · 169°" (about 109) leave about 54 dp for "1280 ft by trail"
     (about 102). With the heading moved, the distance gets about 129.
   - **Height with a fix: unchanged.** The labels sit on the second row's existing line, and the kind sits on the
     figure's line. A test compares the longest lines with short ones.
   - **Height without a GPS fix: grows in some states.** With no fix and a status to show ("Compass unavailable",
     "Compass unreliable" or a facing notice), and in the approximate-position display, the second row now shows the
     heading alone. Before, the heading sat under the arrow. The display then grows by one line, up to the same
     height it has with a fix, never past it. With no fix and only the dash, the second row is still absent, so the
     heading line is gone there: the dash under the arrow is no longer drawn, and "Location services unavailable"
     still says it once.
   - The alternative is to keep the heading under the arrow unlabelled on the display, and label it only on the strip.
3. **Decimal-degree coordinates no longer fit at 360 dp** (by estimate). With the labels, "Facing 315° NW · Alt 9843 ft
   · Lat. 45.5152 Long. -122.6784" needs about 377 dp on the strip and in the display's second row. The strip has
   about 326 dp and the row about 344. Before this part it fitted (about 307). The tail of the coordinates now
   ends in "…". MGRS fits: about 323 of 326 on the strip, which is tight, and about 323 of 344 on the display. On the
   S22 Ultra in portrait (384 dp) the strip gains 24 dp, still short for decimals. In landscape the display is capped
   at 360 dp. This is the dispatch's "stop if it doesn't fit". Options:
   - accept the ellipsis in decimal mode;
   - write the decimal pair shorter (an existing string);
   - shorter or no labels.
4. **The label words, "Facing" and "Alt".** "Heading" was the obvious word, but "Heading 123° SE" (about 101 dp)
   leaves the strip's MGRS line about 1 dp over at 360. "Alt" is the altitude's own short word, while the no-height
   status stays "Elevation unavailable", its existing wording.
5. **Merging A into this branch** (above). It ties the order: A merges first, or with this.
6. **The Details fold.**
   - It holds Points and "With height: N of M points". "With height" is new, and it is the figure Climb and the
     profile depend on.
   - It keeps the network-fix note outside the fold, in view.
   - One open/closed flag is shared by every track's sheet, kept for the session only.
7. **Avg speed counts stops** (first point to last). The app has a moving pace (`domain/MovingPace.kt`), but its only
   caller is the return-time estimate, so it is not used here.
8. **The 24-hour title of an unnamed walk will differ from its Records row** until data part D. The sheet follows the
   phone's setting; the row still prints "2:14 PM".
9. **Track sheet order.** The tiles come after "Waypoints on this track", because -618 put that list under the
   drawing. Tiles first is the other choice.

### New user-facing strings, verbatim

- Turns: "Ahead · N°", "Slight left · N°", "Slight right · N°", "Left · N°", "Right · N°", "Sharp left · N°",
  "Sharp right · N°", "Behind · N°". These replace "Turn N°".
- After a distance: "by trail", "straight". The status line reads "≈ 0.7 mi straight", replacing "Straight line ≈ 0.7
  mi".
- Labels: "Facing", "Alt".
- Track sheet:
  - tile labels "Time", "Descent", "Avg speed" ("Distance", "Climb" and "Not recorded" are A's);
  - speeds "1.2 mph" and "1.9 km/h", or "—";
  - "Details", with "Show details" / "Hide details" for screen readers;
  - "With height", whose value reads "12 of 12 points".
  - "Duration" is gone from the sheet; its figure is the Time tile.

## Files

New:

- `domain/NavigationWords.kt`
- `domain/model/SpeedFormat.kt`
- tests:
  - `domain/NavigationWordsTest.kt`
  - `domain/model/SpeedFormatTest.kt`
  - `ui/availability/AvailabilityScreenNavigationWordsTest.kt`, two classes: 360 dp portrait, and 780 × 360 dp
    landscape
  - `ui/log/TrackSheetDataTest.kt`
  - `ui/log/TrackSheetTilesTest.kt`

Changed:

- `ui/availability/NavigationHud.kt`
- `ui/availability/ApproximatePositionHud.kt`
- `ui/availability/AvailabilityMapControlsUi.kt` (the strip)
- `ui/availability/AvailabilityScreen.kt` (the fold's holder)
- `ui/log/RecordDetailsSheet.kt`
- A's `domain/EntryReport.kt` and `ui/log/EntryReportSummary.kt`

Existing tests changed to the new words (the behaviour asked for, not a weakening):

- `NavigationHudReadoutTest`: "Turn N°" and "Straight line …"; four new tests for the kind, the label and the second
  row.
- `AvailabilityScreenReturnRouteTest`, `AvailabilityScreenMapIconStackTest`, `AvailabilityScreenWaypointNavigateTest`,
  `AvailabilityScreenApproximatePositionTest`: the same words. The approximate test also asserts "straight".
- `AvailabilityScreenMapIconStackTest`: the no-fix test expected a dash under the arrow. It now expects no heading line
  (stop 2).
- `RecordDetailsSheetTest` and `GpxImportRecordsTest`: Distance and Duration moved from fields to tiles. Points is now
  read after opening the fold with a real touch.
- `MapChromeOverMapTest`: two new tests. The tiles have no fill over a map, and are solid in Records.

How the tests reach the code:

- The domain wording is tested headless.
- The display, the strip and the sheet are driven through `AvailabilityScreen`: Records, then a real touch on the
  row, then a real touch on the fold's header at three points.
- The fit tests read each line's own text layout: not ellipsised, every character visible, and the box as wide as
  the text needs.

## Revert checks to run at the build (from saved copies, compile log checked first)

| Revert | Expected failure |
|---|---|
| `TURN_SLIGHT_FROM_DEGREES` 10 → 11 | `NavigationWordsTest`: "Slight left · 10°" expected, "Ahead · 10°" read |
| `route is ReturnRoute.Ahead -> DistanceKind.BY_TRAIL` → `STRAIGHT` | readout and screen tests expect "by trail" |
| `LabelledTiles` ignoring `overMap` | `MapChromeOverMapTest`: Transparent expected, surfaceVariant read |
| `DetailsFold` ignoring `LocalTrackDetailsFold` | `TrackSheetDataTest`: the next sheet opens with the fold closed |
| `is24Hour` forced false | `TrackSheetDataTest`: "HH:mm" expected |
| Heading put back under the north arrow, labelled | 360 dp fit test: the distance's kind or the turn cut short (this is the evidence for stop 2) |

## Not verified

- **Nothing is compiled or run.** That includes the auto-merged A files and every expected string in the tests.
- **The widths come from Noto Sans** (the app's font) measured with Pillow at M3's labelMedium (12 sp, 0.5 sp
  tracking) and a bold titleMedium. They leave out tabular figures, font fallback for "≈", "°" and "·", and Compose's
  own rounding. They are a guide to the stops, not evidence.
- **Device-only:**
  - the 1.5 dp clearance in S22 landscape (Robolectric reports no status bar);
  - how the profile's Canvas looks;
  - whether the kind sits on the figure's baseline (`alignByBaseline` through `WordSwap`'s box is inferred to pass the
    baseline up, not checked);
  - how the outlined tiles read over satellite imagery.
- **Robolectric's 24-hour setting** is read through `Settings.System.TIME_12_24`, as `SundownLineTextTest` already
  does. That this reaches `DateFormat.is24HourFormat` in a Compose test is assumed from that precedent.

## Amendment 1 (RECORD -680), applied

Written 2026-10-07 (UTC). This section supersedes stops 1 to 9 above where it says so. The text above is left as it was
written.

**origin/main merged in.** origin/main is at `bc85fd29`, which includes motion Part 2. It is merged into this branch
(`9cd05528`) with no conflicts. Not compiled.

**The owner's answers, verbatim:**

1. Turn bands: "As proposed (Recommended)". No change.
2. Heading: "Move it, labelled (Recommended)". No change.
3. Decimal coordinates: "Shorter decimals, no labels (Recommended)". **Built.**
4. Labels: "'Facing' and 'Alt' (Recommended)". No change.
5. Avg speed: "Moving speed (Recommended)". **Built.**
6. Sheet order: "Keep -618's order (Recommended)". No change.

The planner kept the fold's session-only state and accepted the 24-hour title mismatch until part D. The merge order
(stop 5) is noted.

### Shorter decimals

`coordinatesStripText` (`ui/availability/AvailabilityPureFunctions.kt`) now writes the pair as "45.3262, -122.6340",
where it wrote "Lat. 45.3262 Long. -122.6340".

- The strip and the display both read this one function, so both change. The tap to switch format is unchanged.
- The pair is formatted in `Locale.US`, so a phone set to a comma-decimal language cannot print "45,3262, -122,6340".
  Before, it used the phone's locale. That is a coder's choice, and it is new.
- Estimated fit: the pair is about 115 dp. The full strip line "Facing 315° NW · Alt 9843 ft · 45.5200, -122.6800"
  is about 316 dp, inside the strip's 326 and the display row's 344 (Noto Sans estimate, as above).
- New tests:
  - 360 dp portrait: the display's decimal pair fits whole beside the longest lines, and a second tap switches back to
    MGRS.
  - 360 dp portrait: the strip's decimal pair fits whole beside its labels.
  - The landscape test now checks MGRS and then decimal, after a real touch switches the format.

### Moving speed

New `domain/TrackMovingSpeed.kt` holds `trackMovingSpeedMetersPerSecond`. It returns `movingPace(points)` (the
walk-back estimate's own figure), except that it returns `null` when that pace is `PaceSource.DEFAULT`, meaning under 5
minutes of moving time. The estimate assumes 2 mph in that case. A record must not show an assumed pace as measured
(CLAUDE.md), so the sheet shows "—" there.

**That null case is the coder's call, for the planner.** The alternative is to show the estimate's default.

The tile is labelled "Moving speed"; it was "Avg speed".

- **Unit-tested with a long stop** (`TrackMovingSpeedTest`): 1112 m in 10 min, an hour stopped, then 1112 m in 10 min.
  The moving speed reads 1.853 m/s. Over the whole time the walk averages 0.463 m/s.
- `TrackSheetTilesTest` covers the same case at sheet level: "4.1 mph" where distance over time would give "1.0 mph".
  It also covers the dash under 5 minutes of moving time.

### Tests whose assertions changed in this amendment

- `AvailabilityScreenMapIconStackTest`:
  - "Lat. 45.5152 Long. -122.6784" becomes "45.5152, -122.6784" (7 places, one of them in an already-`@Ignore`d test,
    whose ignore is untouched).
  - The no-fix test's check that no "Lat. " text appears became a check that no decimal pair appears, matched by
    pattern. Otherwise the old check could no longer fail.
- `NavigationHudReadoutTest`: the decimal pair, in the new format.
- `RecordDetailsSheetTest`: T1's speed is now "Moving speed" "1.4 mph". The 40-minute interval is under the 0.5 m/s
  floor and drops out. It was "Avg speed" "1.2 mph".
- `TrackSheetDataTest`: the hill walk's points are now a minute apart, so it has measured moving time. Time "11 min",
  "Moving speed" "4.1 mph". Before, Time was "55 min" and "Avg speed" was "0.8 mph".
- `TrackSheetTilesTest`, `GpxImportRecordsTest`, `MapChromeOverMapTest`: the label "Avg speed" becomes "Moving speed".
  The figures in `TrackSheetTilesTest` are unchanged, since every interval there moves.
- `SpeedFormatTest`: doc comment only.

Revert checks to add at the build:

- `trackMovingSpeedMetersPerSecond` returning `movingPace(points).speedMetersPerSecond` unconditionally: the
  under-5-minutes tests expect `null` and "—".
- The tile reading `stats.averageSpeedMetersPerSecond` again: the long-stop tile test expects "4.1 mph".
- `coordinatesStripText` writing "Lat." again: every decimal assertion fails.

Still unverified: everything above. Nothing is compiled or run.

## The build (Gradle go, RECORD -664's go for the data sweep)

Written 2026-10-07 (UTC). This section supersedes the "nothing compiled or run" status above. The text above is left as
it was written.

**Merge.** origin/main (`f17b24b5`, which includes data part A and motion Parts 1 and 2) was merged in at `9f303502`.
The one conflict was the audits index. Main's side added no row at that point, so this branch's row was kept and no
row was dropped. No source file conflicted, and no logic was changed on both sides.

- The A files this branch edits (`EntryReport.kt`, `EntryReportSummary.kt`) are unchanged on main since `fcd9adfc`,
  the A head merged in earlier.
- **The bounce.** This part adds no icon button. The Details fold is a clickable row with a decorative icon, and the
  display's exit was already `BouncingIconButton`.

**How it ran.**

- Every Gradle run went through `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`.
- Gradle heap was 1536m, the Kotlin daemon 2g, and Java temp `~/.cache/forager-test-tmp` (as a system property and in
  `JAVA_TOOL_OPTIONS`).
- No daemon was running at the start.
- Free disk was checked before each run: 3.6 GB at the start, 3.2 GB at the lowest.
- The Kotlin daemon was stopped after each compile and before each test run.
- `./gradlew --stop` ran at the end, and no Gradle or Kotlin daemon is left.
- No phone or emulator was used.

**Compile.** Main sources compiled on the first run. The test sources had one error, mine: the words test passed a
nullable route where `AvailabilityScreen` takes a non-null one. It now passes `ReturnRoute.Pending` when not
navigating (`66668160`). The second compile had no errors.

**New and edited test classes: 28 classes, 387 tests, 0 failures, 19 skipped.** The skipped tests are `@Ignore`s that
were already there.

Measured under native graphics, these supersede the estimates above:

| What | Measured |
|---|---|
| At 360 dp, the longest lines | each whole, box = needed width |
| "Sharp right · 169°" | 102.75 dp |
| "1280 ft" + "by trail" | 52.75 + 40.25 dp |
| "≈ 1250 ft straight" | 102.25 dp |
| MGRS | 123.75 dp, whole |
| Decimal pair "45.5200, -122.6800" | 116.25 dp, whole |
| "Facing" / "Alt" | 38.75 / 16.25 dp |
| Display height, longest lines vs short lines | 96 dp / 96 dp |
| Landscape 780 × 360, every line, MGRS and decimal | whole |
| Landscape display | ends at 96 dp, central third from 120 dp: 24 dp clear here, with no status bar (the S22's clearance stays a device item) |

**Revert checks: nine, every one failed for its own reason.** The runner saved a copy, applied the edit, compiled, and
refused to read results after any compile error; none had one. It then ran the named classes, read the JUnit XML,
restored from the saved copy, and confirmed the file equals HEAD (forward change present: true for all nine).

| Revert | Result |
|---|---|
| R1 slight band from 11° | 4 failures, e.g. expected "Slight left · 10°", was "Ahead · 10°" |
| R2 route kind STRAIGHT | 2 failures, expected "by trail", was "straight" |
| R3 tiles ignore overMap | 1 failure, Distance tile expected Transparent, was the solid fill |
| R4 fold state local | 1 failure, "the next walk's sheet opens with the fold open" |
| R5 24-hour forced off | 1 failure, title expected "Sep 18, 2026, 21:13", was "9:13 PM" |
| R6 heading back under the arrow, labelled | 2 failures, "by trail" 2 of 8 characters visible at 360 dp: the evidence for moving the heading |
| R7 moving speed with the assumed default | 3 failures, expected null/"—", was 0.889 m/s / "2.0 mph" |
| R8 tile reads distance over whole time | 2 failures, expected "4.1 mph", was "1.0 mph" |
| R9 "Lat." pair back | 3 failures, expected "45.5152, -122.6784", was "Lat. 45.5152 Long. -122.6784" |

**Full suite: 522 suites, 4,167 tests, 0 failures, 24 skipped.** No existing test broke. Every existing assertion
changed by this part is listed above, in "Files" and in Amendment 1's list. No test was ignored or weakened.

**Still device-only:**

- the S22's landscape clearance with its status bar;
- how the profile looks;
- how the outlined tiles read over satellite imagery;
- the 24-hour title beside a 12-hour Records row.
