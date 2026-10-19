# Journal search room in landscape: completion report (dispatch 2026-10-18-08 Amendment 3)

Hazel (app coder), 2026-10-18, written 22:20Z. RECORD -1320 (the owner: "Option 2"), which supersedes -1318's "screens
without a map behave as today" for the Journal in landscape. Branch `journal-search-space`, cut from origin/pre-main
`8c90626f` (PR #260 merged), as PR #261 into `pre-main`. **Not merged.**

**Update, 2026-10-19 00:15Z (supersedes everything below, which stands as written). PR #261 was reverted before release,
and PR #262 replaces it.** The full account is in "Update: PR #262" at the end. In short:
- On the S22, List and Seasonal had no search bar on 1.0.3477+g836916a9 (RECORD -1327). The cause was older than #261: an
  open Journal entry hid the bar on every tab (RECORD -1329). #262 makes that hide apply on the Journal only.
- The owner then ruled the strip out (RECORD -1330 to -1333). #262 reverts #261. In landscape the switch line is the
  Journal's header and slides down with the bar, and the Offline maps map does not move at all.

**Status: built, tests updated, the revert check done. The full CI 39171539012 at `aa9d64aa` is green: 5,131 tests, 0 failures, 24 skipped (JUnit XML). No Stop was hit:**
- The Offline maps file (`AvailabilityOfflineMapsUi.kt`) is untouched. The reserve is made in the scaffold.
- No test was weakened. One existing claim was replaced, on the ruling (see Tests).

## What landed

| Commit | What |
| --- | --- |
| `598cd918` | The reserve in `AvailabilityCompactScaffold.kt`, the -1244 test's claim replaced, and the new -1320 tests |
| `3b2f46b7` | Test order only: each -1320 reveal test checks the room after the bounds, so a build without the room fails on what moved, not on a missing tag |
| (this commit) | This report and its index row |

- **`journalSearchRoom`** (`AvailabilityCompactScaffold.kt`) is the one place the reserve is decided. It applies when
  the window is landscape (`railBeside`), the tab is Journal, and the map is not fullscreen. Its value is the pill's own
  landscape height, the same value passed to `SearchEntryBar` as `landscapeHeight`. In every other case it is `null`,
  meaning no reserve.
- **How the room is held.** A `Box` wraps the pill's `AnimatedVisibility` with `heightIn(min = room)` and
  `clipToBounds`. The pill slides in and out of it vertically. While the room is held, the expand and shrink are left
  out, so nothing under the room follows the pill. The height is a floor, so a search notice under the pill can still
  add height.
- **The room is there on every Journal screen, including with an entry open** (the planner's ruling on my question).
  The `Box` sits outside the `journalSearchHeld` check, so opening or closing an entry moves nothing either. **The owner
  should see this at the phone check:** an open entry or editor in landscape now has the empty strip at the top as
  well.
- Portrait is unchanged, because the Journal's pill never hides there (`AvailabilityCompactScaffold.kt:697`, `:1073`).
  Other tabs are unchanged.
- Measured under Robolectric (823x384): the room is 36 dp tall, 0 to 36 at the top, 743 dp wide (the rail takes the
  rest). The pill is 0-36 inside it. The Offline maps map is at 52-372 x 84-384 before and after the reveal.

## Tests

All in `AvailabilityScreenJournalShortWindowTest`, through the real `AvailabilityScreen` at `w823dp-h384dp-land`.

- **-1244's slide test: the claim is replaced, not weakened** (the planner's ruling).
  - Its checks that the pill is part way in, part way out, at its place, and gone at the end are unchanged.
  - Its three checks that the row below follows the pill are replaced by "the row has not moved", at the end of the
    reveal, part way in and part way out. These are now equality checks.
  - The replaced assertions are quoted word for word in the test's KDoc.
- **New: the reveal moves nothing below the switch line**, on Entries, All, Finds, Tracks, Waypoints and Offline maps
  at ROTATION_90, and Offline maps at ROTATION_270.
  - Every tagged node from the switch line down keeps the same bounds, within 0.5 dp, at three points: part way
    through the slide (4 frames in), revealed, and hidden again. Between 12 and 20 nodes per screen were compared.
  - On Offline maps the map's top and height don't change, and the pill doesn't overlap it.
  - The pill lies inside the room, clear of the switch line.
- **New: switching screens with the pill hidden doesn't move the switch line.** The line's top and bottom stay the
  same from Entries through All, Finds, Tracks, Waypoints, Offline maps and back to All.
- **New: the room stays while an entry is open.** It keeps the same top and height. The entry being open is a positive
  control.
- The 🔍 icon is touched with real coordinate touches. Chips and the switch are reached by semantic clicks, which only
  get the test to a screen, as the class's own rule allows.

**Filtered CI 39168299870 at `598cd918`:** green, 90 tests, 0 failures, across
`AvailabilityScreenJournalShortWindowTest` (39), `LeavingTheJournalFixesTest` (32), `DrawerBackOverJournalTest` (11)
and `LandscapeOfflinePickerTest` (8).

## Revert check

The edit is one line: `val journalSearchRoom: Dp? = null`, which brings back the resize exactly as it was. I made the
branch from the committed forward head and restored the file from a copy saved before editing. Afterwards `cmp`
confirmed the forward file was intact. In each revert run's log, every compile step reports `BUILD SUCCESSFUL`, and
there are no `e: ` lines and no failed compile task. Only the test task failed.

| Check | Branch / commit | Tests at | CI run | Result |
| --- | --- | --- | --- | --- |
| R4 | `journal-search-space-revert-1` `81522170` | `598cd918` | 39168866696 | 9 of 39 failed; 8 of those fail at the room's tag lookup |
| R4b | `journal-search-space-revert-2` `aa6fad23` | `3b2f46b7` | 39170164095 | 9 of 39 failed, on what moved |

- **R4b:** each of the 7 reveal tests failed on what moved: "…, part way in: the same tagged nodes below the switch
  line expected:<[compact-navigation-rail, …". Without the room, the switch line starts at the top of the window and
  moves down as the pill expands, so the set of nodes at or below it changes. The -1244 test failed with "RECORD -1320:
  revealed, the row has not moved expected:<0.0> but was:<36.0>", which is the 36 dp resize. The entry-open test fails
  at the room lookup, because it measures only the room.
- **The chip-switch test passed under the revert,** as it must. Without any room nothing jumps between chips either.
  It guards against a reserve on some screens only, not against the reserve being removed.
- **R4,** on the earlier test order, failed the same 9 tests. 8 of them failed at the room's tag lookup and never
  reached their bounds checks, so I reordered the checks (`3b2f46b7`) and ran R4b.
- The removed expand and shrink are not covered by a test. Inside the held room, an expand would not move anything
  either. It only changes how the pill is uncovered (a clip growing) compared with a plain slide. The slide is what the
  dispatch describes, so I kept it.

## Full CI

| Run | Head | Result |
| --- | --- | --- |
| 39168872192 (pull_request) | `598cd918` | green: 5,131 tests, 0 failures, 0 errors, 24 skipped (JUnit XML, 690 files) |
| 39170164409 (pull_request) | `3b2f46b7` | 1 failure: `MushroomLogViewModelWiringTest` "a find deleted through the Journal's ViewModel leaves the Maps tab's records". This is the parked intermittent test, row 2 of `docs/process/known-bugs.md`, about 6% of runs. The commit changed six lines of one Journal test file. Reported, not touched. |
| 39171539012 (pull_request) | `aa9d64aa` (report commit) | **green: 5,131 tests, 0 failures, 0 errors, 24 skipped** (JUnit XML, 690 files); every job green |

## Not verified

- **On a phone:** the slide, whether the strip reads as intended, and the Offline maps map holding still without a
  stutter. Robolectric has no real insets. Device-only.
- **The room's height against the real pill on a phone.** The room uses the pill's own `landscapeHeight`, and under
  Robolectric the two match at 36 dp. A search notice can still make the pill taller than the room, and then the
  content moves by the notice's height. That only happens in an error state.
- **The landscape tablet:** not tested here. It uses the same scaffold path (`railBeside`).

## Phone steps (S26 Ultra, then the S22 and the tablet)

1. **Landscape, Journal, each screen.** Open Journal → Entries, then Records with each chip (All, Finds, Tracks,
   Waypoints, Offline maps). Pass: an empty strip at the top, the same height on every screen. The switch line stays
   still when you change chips.
2. **🔍 on each screen.** Tap 🔍, then tap it again. Pass: the bar slides down into the strip and back up. Nothing
   below it moves.
3. **Offline maps.** Tap 🔍, then tap it again. Pass: the map doesn't move, resize or stutter.
4. **An entry open.** Open an entry from Entries, then close it. Pass: the strip stays and nothing jumps. Note for the
   owner: the strip is on the open entry too.
5. **Portrait.** Open the Journal in portrait. Pass: unchanged, with the bar always shown and no strip.

## Update: PR #262 (RECORD -1327 to -1334), 2026-10-19

Branch `revert-journal-search-space`, from pre-main `836916a9`, PR #262 into `pre-main`. **Not merged.**

### What landed

| Commit | What |
| --- | --- |
| `fd1f9589` | `git revert -m 1 836916a9`: PR #261 out. The tree is identical to `8c90626f` (`git diff --quiet`). |
| `b4be5f09` | Part 1: the open-entry hide applies only while the Journal tab shows. Tests. Also a plain 8 dp gap above the Offline maps map, which was held back afterwards |
| `cb94e958` | That plain gap taken back out, on the planner's hold. `AvailabilityOfflineMapsUi.kt` is again as at the revert |
| `b9571d78` | -1333: `JournalSearchPillRoom.kt` (new), the scaffold measuring the pill's slot, `journalSearchGap` on the Offline maps map, and tests |
| `bc3e6963` | The entry-map check starts from the end of the map's arrival (GrowIn kept, RECORD -1334) and runs through the close |
| (this commit) | This update, with this report and its index row restored. The revert of #261 had deleted them both, and an index row is never dropped |

### Why List and Seasonal lost the bar (RECORD -1327, -1329)

- The pill is composed only under `if (!isMapFullscreen() && compactTab() != CompactTab.MAP && !journalSearchHeld)`.
  That is `AvailabilityCompactScaffold.kt:1079` at 8c90626f and `:1096` at 836916a9.
- `journalSearchHeld` (`:595`) is `isEditingJournalEntry || …`, with no tab in it. Since F3 an entry stays open when the
  tab changes, so List and Seasonal had no bar while an entry was open on the Journal.
- **#261 did not cause this, and the revert alone does not fix it.** I probed three paths through the real screen at
  both 836916a9 and 8c90626f (runs 39175123353 and 39175131489). "From the start" and "after the Journal" passed on both
  builds. "With a Journal entry left open" failed on both. The planner reproduced the same thing on the S22 (RECORD
  -1329).
- The probe branches are deleted.

### Part 1: the hide applies on the Journal only

- The condition is now `!(journalSearchHeld && compactTab() == CompactTab.JOURNAL)`. On the Journal an open entry still
  hides the bar and the 🔍 (-692). Backing out of an entry is unchanged.
- Tests in `AvailabilityScreenJournalShortWindowTest`:
  - List and Seasonal show the pill from the start, after the Journal, and with a Journal entry left open. In each case
    the pill must be composed, displayed and sized, and its dropdown must not have opened by itself (F1's focus race).
  - Back on the Journal with the entry still open, there is no bar and no 🔍.
- F1 itself: `LeavingTheJournalFixesTest` (32) is green on every run below.

### The switch line and the map (-1332, -1333)

- **Search hidden:** the switch line is the header at the top (0 dp), with no strip above it.
- **🔍 tapped:** the pill slides in above the line, and the line moves down with it (J5's expand, as at 8c90626f).
- **Search closed:** the line moves back up.
- **A map under the line, Offline maps in landscape:** the map stands where it is with search open, 8 dp
  (`JOURNAL_MAP_TOP_GAP`) below the line.
  - `measuresJournalSearchPill` records the pill slot's height as the scaffold lays it out, on every frame of the slide.
  - The map's `journalSearchGap` reads that height in its own measure, in the same layout pass. The pill is measured
    first, because the Journal content takes the weighted rest of the column.
  - So the map stands below whatever room the pill has not taken yet, plus 8 dp, at every frame.
  - The rejected alternative was carrying the height through `onSizeChanged`. That reports after layout, so the map would
    trail the slide by a frame.
- Measured at 823x384, both rotations:

| | Switch line (top-bottom) | Pill | Map |
| --- | --- | --- | --- |
| Search hidden | 0-48 | none | 52-372 x 92-384 |
| Search open | 36-84 | 0-36 | the same, 52-372 x 92-384 |

  The line's top on the first frames in was 0, 0, 1, 4, 8, 12.
- **An open entry's map:** its arrival a moment after the report opens (GrowIn, -682, kept on the owner's word,
  RECORD -1334) slides it from top -252 to 48 over about 15 frames. From then until the entry has closed, it holds at
  52-452 x 48-348. On the close, the map was on screen for one frame (top 48, height 300) before it went, so the close check
  covers that one frame. While an entry is open, search is held hidden on the Journal and the switch line is at 0.
- `LandscapeOfflinePickerTest`: "the map starts where the controls side does, under the chip row" is replaced, on the
  ruling, by "the pill's height plus 8 dp below the chip row, and unmoved with search open". The old assertion is quoted
  in the test.

### Revert checks

| Check | Branch / commit | Edit | Run | Result |
| --- | --- | --- | --- | --- |
| R5 | `revert-journal-search-space-revert-1` `3f35085a` | `!journalSearchHeld` again (the hide on every tab) | 39177184588 | 2 of 66 failed, both "List with a Journal entry open: the search pill is composed" |
| R6 | `revert-journal-search-space-revert-2` `0f4026da` | the Offline maps map without `journalSearchGap` | 39178994354 | the 4 map tests failed (see below) |

- R6's map failures: both rotations' Offline maps tests ("frame 3 in: the map keeps its bounds (… top=48.0 …, then …
  top=49.0 …)"), and both `LandscapeOfflinePickerTest` sides ("expected:<92.0> but was:<48.0>").
- R6 also failed the two entry-map tests. They failed the same way on the forward head at the time, before `bc3e6963`
  adjusted them to the -1334 ruling, so those two failures are not R6's.
- In both runs' logs every compile step reports `BUILD SUCCESSFUL`, with no `e: ` lines and no failed compile task.
- I made each revert branch from the committed forward head and restored the file from a copy saved before editing.
  `cmp` against the saved copy confirmed the forward file was intact.
- **The entry-map check has no one-line revert.** This PR has no code for the entry map, so there is nothing to undo.
  The evidence that it can fail is its own first version: run 39178444793 failed at "opening, frame 1: the map's top
  does not move expected:<48.0> but was:<-252.0>", on the arrival the owner then kept.

### CI

| Run | Head | Result |
| --- | --- | --- |
| 39177186340 (workflow_dispatch, full) | `cb94e958` (revert plus part 1) | green: 5,126 tests, 0 failures, 24 skipped |
| 39178444793 (filtered, 13 classes) | `b9571d78` | 184 tests, 2 failures, the entry-map arrival before -1334 |
| 39179637908 (pull_request, full) | `bc3e6963` | **green: 5,131 tests, 0 failures, 0 errors, 24 skipped** (JUnit XML, 690 files); every job green |

### Not verified

- Anything on a phone: the slide, the gap, and that the map holds still without stutter. The lockstep depends on the pill
  being measured before the Journal content in the same pass. That is true of a Column with a weighted child, and it is
  what the frame-by-frame test reads under Robolectric. A phone is still the place to see it.
- A search notice under the pill makes the pill taller than the room. The map would then move by the notice's height
  (`coerceAtLeast(0)`). That only happens in an error state.
- The landscape tablet: not tested. It takes the same path.

### Phone steps (S22, S26, tablet)

1. In landscape, open Journal, then Records, then Offline maps. Pass: the switch line is at the top, with a space between
   it and the map.
2. Tap 🔍, then tap it again. Pass: the bar slides in above the line and the line slides down into the space. The map
   doesn't move, resize or stutter. Closing search slides the line back up.
3. Open Entries. Tap 🔍, then tap it again. Pass: the line slides down with the bar and back.
4. Open an entry. Pass: its map slides in as before, then holds still. Close the entry. Pass: the map doesn't jump.
5. With an entry open on the Journal, tap List, then Seasonal. Pass: each shows its search bar. Back on the Journal,
   the entry shows no bar and no 🔍.
6. In portrait. Pass: unchanged.
