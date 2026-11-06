# Tablet Journal layout: completion report (dispatch 2026-10-18-10, Amendment 1)

Hazel Cairnwright, 2026-10-18 (Pacific). Forager RECORD -1337, -1338, -1340. PR #263 into pre-main, not merged.
Base: pre-main `e431eba9`, checked against the remote before starting.

## Verify-first (reported before building)

- **No wide tree and no `LogPanel`.** Both were removed in 2026-09-28-245 (`AvailabilityScreen.kt:2025`, `CartographyScreen.kt:55`). A tablet runs the phone's tree, and in landscape the phone's sideways Journal (`JournalShortWindow.kt:62-66`).
- **Where the narrow column came from.** The 640 dp landscape cap beside the rail (`AvailabilityCompactScaffold.kt:997-1005` at the base). Tablet portrait had no cap.
- **Maps beside info.** There are two: Offline maps (`OfflineMapsSideBySide`) and the entry page (`EntryReportLayout`).
- **Tablet sizes.** 1318 x 824 dp in landscape and 824 x 1318 dp in portrait (`AvailabilityScreenTabletAsPhoneTest.kt:64`).
- **The stop.** The list form was a choice the owner hadn't made. The owner answered (-1340): "A: wider column", "Match content", "Yes, start now".

## What landed

| Commit | What |
|---|---|
| `fceaaca0` | Known bugs, row 7: the Offline maps panel sliding down with the search reveal, held as a quirk (-1338) |
| `077246fb` | The build: `isLargeScreenWindow` (smallest width 600 dp or more), the Journal's 960 dp cap, square maps, the portrait squares; `TabletJournalLayoutTest`; `LandscapeEntryReportTest`'s tablet case replaced, quoting the old assertions |
| `453a02d2` | Imports for `constrainWidth` and `constrainHeight` (run 39186173639 did not compile); a foldable-size test where the height decides the square |
| `f8efcc7d` | Test fix: the top line's tag sits inside its 16 dp side padding (928 in a 960 column) |
| `f66cc84a` | Test fix: the phone cases scroll to the Offline maps chip before clicking it (a tree dump in run 39187881766 showed the chip past the row's visible end, with Finds still selected) |

## Evidence

- **CI run 39189444524 on `f66cc84a`: green.** All three jobs passed: build and test, release compile, lint. From the JUnit XML: 691 files, 5149 tests, 0 failures, 0 errors, 24 skipped.
- **`TabletJournalLayoutTest`: 18 tests, 0 failures, through the real `AvailabilityScreen`.** Each test first asserts that the screen saw its rotation and its smallest width. It covers:
  - landscape tablet at ROTATION_90 and ROTATION_270: Offline maps and the entry page square within 1 dp, the info beside the map with no overlap, the map and the info spanning the 960 dp column, centred;
  - the top line in the same column on Entries, Records and Offline maps;
  - Entries in two columns;
  - the square holding its bounds in every frame of the search reveal, in and out, by a real touch on the search icon;
  - a foldable size (1000 x 700 dp) where the height decides the square;
  - portrait tablet: both squares on top, the info under them;
  - phone landscape and portrait controls: 640 dp, half and half, 4:3.
- **What was measured** (Robolectric, zero insets):
  - landscape tablet: map 720 x 720 and controls 240 at both rotations; the entry map 720 with 240 of info;
  - foldable: map 608, which is the room under the line, and controls 312;
  - portrait tablet: maps 824 x 824.
- **Screenshots.** Written by the class to the uploaded test report (`tablet-journal-screenshots/`). Copies of run 39186572126's are kept outside the repository. The map is a stand-in and draws nothing.

## Revert checks

Each is a one-line edit on its own branch from `f66cc84a`, run with the five affected classes. The filtered sample is 87 tests, the same 87 those classes hold in the forward run (18 + 10 + 12 + 39 + 8). For every run, the head SHA matches the revert commit and the build log has 0 compile errors.

| # | Run | Edit | Failures (count, and what they say) |
|---|---|---|---|
| 1 | 39190824349 | Journal cap back to 640 | 8: "is the 960 dp column ... was 640"; the foldable's room-height square, 400 (640 - 240 decides there); Records' top line 608 |
| 2 | 39190829958 | Offline maps map back to half width | 3: "the map is square, expected 480 but was 732"; foldable 460 vs 608 |
| 3 | 39190835100 | Square taken outside the search gap | 5: "frame 3 in: the map keeps its bounds (... bottom 768, then 769)" at both rotations; 720 x 676 not square; foldable 652 vs 608 |
| 4 | 39190839698 | Entry map back to 4:3 | 4: "is square, expected 720 but was 540"; portrait 618 vs 824 |
| 5 | 39190844829 | Portrait Offline maps back to 4:3 | 1: "is square, expected 824 but was 618" |
| 6 | 39190849929 | The large-screen gate always true | 10, all phone cases: the B3 Journal and Records cap at 711 dp; phone 4:3 entry and Offline maps; the class's phone controls |

**A runner slip, caught and redone.** The first read of these results came from a folder that was not a git repository. `gh run list` returned no ID, so all six downloads fetched one run's report, and the six results came out identical. Those were discarded. The table above comes from a re-read with the repository named and each run's ID and head SHA checked.

## Decided beyond scope

The Offline maps controls keep 240 dp beside the square. This is the owner's entry-page figure (-1260), reused (`OFFLINE_CONTROLS_MIN_WIDTH`).

## Not verified

- **No real tablet.** Robolectric reports zero insets. On the tablet the status and navigation bars take height, so there the height, not the 240 dp floor, should decide the square. That is the foldable case's regime. Inferred, not seen.
- **The search pill's own place on the tablet** was not touched or re-checked.
- **Phones on hardware.** Not run. Phone layouts are held by the existing classes and revert 6.
