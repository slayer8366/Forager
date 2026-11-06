# Dispatch 2026-11-06-03, Amendment 1: the one-decimal values out of pre-main, and the records branch (completion report)

Hazel Cairnwright (coder), 2026-11-06. RECORD -2182. Branch `home-values-out-2`, cut from pre-main
`a5a2a392f` (the merge of PR #501; checked with `git merge-base --is-ancestor` against the remote
before starting, and pre-main's head was still `a5a2a392f` when the code commit was pushed). PR #502,
ready, not merged. Places are named by kind only; this report holds no coordinate, grid reference,
tile, range, offset or derived value.

## What landed

### B3, the records branch (first, as the dispatch ordered)

On the owner's "Clean them", one commit on `records-after-173` (`d24af3c88`): the coordinate pairs
and the search-list hits the extended scanner finds in the branch's non-app files are replaced.

- **Pairs** (RECORD.md, one review, two saved prompts): each pair is replaced with the earlier
  sweep's point marker, `[a point near the owner's home; removed 2026-11-06]`.
- **Search-list hits** (eleven, all in the trail-data comparison audit of 2026-10-17: ten in its query
  log, one in its results file): each is a single box-edge value inside a query URL or a JSON box.
  Each is replaced with the same marker family worded for what it is,
  `[a box edge near the owner's home; removed 2026-11-06]`; in the JSON file it is quoted, so the file
  stays valid JSON (checked by parsing it).
- Only those values change; a word diff of the commit shows every removed word is a number and every
  added word is a marker. Nothing else in any line is rewritten or deleted.

### B2, the PR to pre-main

On the owner's "Both: 6 pairs and ~240": every value the extended scanner finds on pre-main beyond
Group 3 is replaced with a made-up value, by #501's method. 92 files, 453 lines, each line changed
only in its numbers (word diff: every changed word is a number, except one marker).

- **Test files (84).** In each file the longitude family moves by one constant, and where the file
  has a latitude find its latitude family moves by one constant too, so distances, orderings, boxes
  and identities inside the file hold.
  - Where a file holds an offline region (a centre with a radius), the longitude constant is a whole
    number of zoom-8 tile widths, so every tile column at zoom 8 and above moves by a whole number.
    The latitude constant there is chosen so that every region in the file has the same tile count
    at every zoom from 8 to 16 as before, computed with the app's own estimate (bounding box from
    `GeoDistance.boundingBox`, tiles from `SlippyMapTile`, both read from source and reproduced in a
    private script).
  - **Per-value latitudes in three files** (`JournalPendingDeleteTest`, `OfflineMapDeleteButtonFixtures`,
    `RecordsFilterChipsTest`): their regions sit at different latitudes and feed the tile estimate
    the tests count, and no single latitude constant kept every region's counts. There the longitude
    family moves as above, and only the one found latitude takes a new value chosen to keep its
    region's tile counts at zooms 8 to 16; the other latitudes do not move.
  - **Lines with a Group 3 value** (`GpxImportFixtures`, `GetMapRecordsUseCaseTest`,
    `CartographyEntryReportScreenMapTest`, `EstimateOfflineTileCountTest`): Group 3 values do not move.
    In the first three only the found values change, one value at a time; in the fixture's
    multi-day track and the recording track, the found latitude moves away from its Group 3
    neighbour, so the track keeps two distinct points (the tests read the track's names and point
    counts, not its length). In `EstimateOfflineTileCountTest` the Group 3 fixture line is left as
    it was and the other fixture moves.
- **Docs and saved records.** Only the found lines change, with the same longitude constant as the
  tests they quote, so the two old audits that quote test messages still match their tests: RECORD.md
  (two rounding examples in one entry, where the constant keeps every tie a tie), the
  find-location and photo-location completion reports, and the 2026-08-28 post-download report.
- **One saved prompt** (`prompts/preserved/2026-09-30-18.md`): its pair was written as the owner's
  area, so a made-up value there would read as true. It takes the earlier sweep's point marker
  instead.
- **Scripts** (`scripts/verify-open-meteo-fields.sh`, `scripts/verify-open-meteo-historical-fields.sh`):
  manual checks that the weather API offers the fields the app asks for, at one sample place. The
  sample place moves with the same constant; what they check (that the fields exist) does not depend
  on where the place is. They are not run by CI and were not run here.
- **The tile worker's routing test** (`server/pmtiles-worker/test/routing.test.mjs`): the
  "outside coverage" case's area moves with the same constant and stays in the same routing square,
  so it is still outside the test build. CI's "Tile Worker serves what its tests say" job runs it.
- `app/src/main` is not changed. Group 3 is not changed (the old scanner's counts per file are the
  same on the branch as on the base).

## Evidence

**Scanner (private, off GitHub), the version #501 extended.** Proved non-zero before any 0 was
trusted, on the previous session's synthetic cases: a one-decimal in-box pair reads 1 pair; a
helper-split pair reads 1 helper pair in each direction; the negative controls read 0.

**Records branch, non-app files** (files mode, 2 562 files):

| | lat | lon | pair | helper pair | grid ref | tile | search-list |
|---|---|---|---|---|---|---|---|
| before (`f14fa9d3a`) | 41 | 31 | 6 | 0 | 0 | 0 | 11 |
| after (`d24af3c88`) | 25 | 27 | 0 | 0 | 0 | 0 | 0 |

The single values that remain were not in the owner's go for the records branch (pairs and
search-list hits only). The app tree there is pre-main's and is cleaned by B2's merge.

**Pre-main base and this branch, whole tree** (files mode; the diff-from-empty-tree mode gives the
same singles and 6 pairs on the base):

| | lat | lon | pair | helper pair | grid ref | tile | search-list |
|---|---|---|---|---|---|---|---|
| base `a5a2a392f`, new scanner | 63 | 202 | 15 | 0 | 0 | 0 | 0 |
| base `a5a2a392f`, old scanner | 20 | 2 | 0 | n/a | 0 | 0 | 0 |
| branch, new scanner | 23 | 2 | 0 | 0 | 0 | 0 | 0 |
| branch, old scanner | 20 | 2 | 0 | n/a | 0 | 0 | 0 |

The old scanner's 20 and 2 are Group 3, the same per file on both. The new scanner's further 3
latitudes are the three measurements left for the owner (below).

**Push diff.** The code commit's diff reads 0 on every count except one latitude, which is a Group 3
value on a changed line in `GetMapRecordsUseCaseTest` (already in the tree, unchanged); the same diff
reversed reads non-zero (the values it removed). The records commit's diff reads 0 except three
longitudes on its changed review line, single values already on the branch and outside the go
(above). Each commit message and the PR text read 0.

**CI.** See "CI" below.

## Disclosure

### Confirmed and inferred

- Confirmed: no file this PR changes is changed by PR #498 (`nav-card-kind-line`) or PR #500
  (`next-turn`), checked against both branches' file lists before editing and again before
  pushing. Two shared test files hold only Group 3 values and were not edited.
- Confirmed by reading the source: the tile estimate's box and tile arithmetic, reproduced to keep
  counts.
- Confirmed: the multi-day GPX fixture's tests assert track names and point counts only.
- Inferred, resting on CI: that no test depends on a fixture's absolute position beyond tile counts
  (a forecast block, a grid cell) in a way the uniform constants break. CI's result by test name is
  the check.

### Could not determine

- Whether the two scripts still behave at their new sample place: they call a live API and were not
  run (no network calls were made for this).
- Whether any changed fixture is read by a device check or a screenshot record outside the unit
  tests. None was looked for beyond the scanner's finds.

### Premises that were wrong

- **"The remaining single values are only Group 3's"** does not hold on this branch either: three
  one-decimal values the new scanner counts are not places at all: a climb in metres in a comment of
  `BarometricHeightTest`, the same measurement in the 2026-10-11 barometer audit, and one cell of the
  2026-09-30 topo-night render-metrics table. A made-up value would falsify a measurement, so they are
  left as they are, for the owner to rule on.
- The amendment's count for the records branch read "5 pairs in RECORD.md, one review and three saved
  prompts". The scanner finds them in two saved prompts, not three; it counts 5 pairs in diff mode and
  6 in files mode (one saved prompt holds two pairs on adjacent lines). All six were replaced.
- The base's new-scanner pair count is 6 in diff mode (as #501's report gave) but 15 in files mode,
  where pairs across adjacent lines count too. All are 0 on the branch in both modes.

### Decided beyond scope

- The worktrees are `/mnt/work/forager-wt/home-values-out-2` and `/mnt/work/forager-wt/records-homevals2`,
  not under `~/.cache/forager-wt/`: the root disk was full (a checkout there failed partway and was
  removed).
- The box-edge marker wording on the records branch (a box edge, not a point).
- The saved prompt's owner-tied pair took the marker, not a made-up value (above). The same line names
  a place by name as the owner's area; the scanner does not flag it and it was not changed. That is a
  question for the owner, below.
- In the one RECORD.md entry and the old audits, numbers changed in place; no text was added to say so.

## Questions for the owner

1. The three measurements (a climb in metres in one test comment and one audit, one table cell):
   leave them as they are, or change them anyway?
2. One saved prompt on pre-main names a place by name as the owner's area. Remove the name too?

## CI

**Stopped: five tests changed result.** Run 42294867083 on the code commit `90e336405`: six jobs
green ("Release compiles", lint, the worker, the route-data cutter, the map styles, the debug-source
guard), and the two test jobs red ("Build, test, publish APK" and "The app builds and its tests pass
with the Street map deleted"). Compared by test name, from the run's `unit-test-report` JUnit XML,
against pre-main's run 42292140229 on the base `a5a2a392f` (green):

| Run | test cases | passed | skipped | failed |
|---|---|---|---|---|
| pre-main `a5a2a392f` | 6 838 | 6 814 | 24 | 0 |
| this branch `90e336405` | 6 838 | 6 809 | 24 | 5 |

The same 6 838 names in both. The five that changed from passed to failed:

- `ForecastCellParsingTest`: "a cell belongs to the block holding its centre, whole degrees included,
  south and west of zero too" and "the blocks touching an area are every block it overlaps and no
  other";
- `ForecastCellLayerTest`: "a view asks for the blocks its cells can reach" and "below the minimum
  zoom nothing is requested, and at it even a tablet-sized view stays under the backstop";
- `RecordsFilterChipsTest`: "EDIT_NEW_FIND lands on Finds editing with the Finds chip selected".

What the messages say (by kind): the four forecast tests expect a fixed whole-degree forecast block,
and the moved latitudes put a fixture into, or across the edge of, the next block. That is the same
absolute-position dependence #501 met in `MapBubblesTest`, and the uniform latitude constant does not
keep it. The filter-chips test looks for a find's displayed position text and does not find it; the
per-value latitude in that file is the likely cause (not confirmed).

The dispatch makes a test that changes result a stop, not an edit, so nothing was changed after the
run. The fix is in reach (for the forecast files, a latitude change by whole degrees, or moving the
expected block with the fixture as #501 did; for the filter-chips file, the expected text with its
fixture), but each of those is a decision for the owner and the planner, below.

## Questions for the owner (CI)

3. Five tests changed result because their fixtures moved against a fixed forecast block or a shown
   position text. Should the coder fix the fixtures so the five pass again (moving each expected
   block or text with its fixture, as #501 did once), or stop B2 here?

## B4: the five tests, and D2 and D3 (a second Hazel, after the owner's answers)

Hazel Cairnwright (coder), 2026-11-06. The owner answered the three questions above (Amendment 1,
section D): **"Fix them (Recommended)"** (D1), **"Leave them (Recommended)"** for the three
measurements (D2), and **"Take the name out (Recommended)"** (D3). Started from
`origin/home-values-out-2` at `0e41696a2` (pre-main `b516d520a` merged in), the worktree clean at
that SHA. Commit `c19f9245c`. As above, this section holds no coordinate, tile, range or derived value;
places are named by kind.

### What was read before acting

- Run 42294867083 (the previous Hazel's code commit) and pre-main's run 42292140229 (on
  `a5a2a392f`): both runs' `unit-test-report` JUnit XML, compared by test name with a script in the
  scratch area. Same 6 838 names in both; the five named in the dispatch are the only changes
  (passed to failed), nothing else differs. The failed jobs' log was checked for compile errors
  first: both compile tasks ran and there are none; the failures are assertion failures in those
  five tests.
- `b516d520a`, the branch's base now, differs from `a5a2a392f` by one audit-index row and carries
  `[skip ci]`, so pre-main has no run on it; run 42292140229 on `a5a2a392f` is the comparison.

### Each test's cause, from the code

- **The two `ForecastCellParsingTest` tests.** `ForecastBlock.containing` floors a centre's tenths
  to whole degrees, and `ForecastBlock.touching` floors each edge, edges included
  (`domain/ForecastCells.kt`, the `ForecastBlock` companion). The previous commit moved the file's
  latitudes by one fraction of a degree and left the expected blocks where they were. In the first
  test that put one centre into the next block up (CI's failure). It also took two centres off whole
  degrees, which still passed but no longer tested what the test's name says ("whole degrees
  included"). In the second test it put the north edge of the one-block area exactly on the next
  block's edge, which `touching` counts (CI's failure).
- **The two `ForecastCellLayerTest` tests.** `forecastBlocksToRequest` widens the view by half a
  cell, snaps to the tenths a centre can sit on, and asks `touching` for those blocks
  (`ui/map/ForecastCellLayer.kt`). The view's south edge was placed just above a block's lowest
  centre, so the half-cell reach stays in that block. Moved by a fraction, it sat just above the
  block's second centre, so the reach crossed into the block below (CI's failure in both tests,
  which share the view). The comment explaining the edge was moved with the numbers, so it now
  described a block the code does not compute.
- A Python model of those three functions, written from the source, reproduces the four actual
  values CI reported, exactly.
- **`RecordsFilterChipsTest`, "EDIT_NEW_FIND lands on Finds editing…"** (the one the previous report
  left unconfirmed). The text is built as `"Found at ${"%.4f".format(lat)}, ${"%.4f".format(lng)}"`
  (`ui/log/LogEntryDetailScreen.kt`, the location row, and the same in `LogEntryReportScreen.kt`).
  The previous commit moved the fixture's longitude by a constant with more decimal places and put the
  moved number into the expected text as it stands, with five decimals. A four-decimal format can
  never produce a five-decimal text, and `onNodeWithText` matches the whole text, so the node is never
  found. The latitude is not the cause: it did not change in that test. Confirmed: the base's
  expected text was the four-decimal form, and it passed.

### What changed, and each claim before and after

| Test | Its claim (the same before and after) | What changed |
|---|---|---|
| `ForecastCellParsingTest`, "a cell belongs to the block holding its centre, whole degrees included, south and west of zero too" | A centre on a whole degree belongs to that degree's block; a centre on the block's top tenth belongs to the block; the next whole degree starts the next block; west of zero floors down; the zero and hair-under cases (unchanged lines). | The three moved centres are the base's centres moved by one whole degree, and each expected block is moved by the same whole degree. |
| `ForecastCellParsingTest`, "the blocks touching an area are every block it overlaps and no other" | An area across a block corner touches exactly the four blocks; an area wholly inside one block touches only that block. | Both areas' south and north edges are the base's moved by one whole degree, and the expected blocks by the same. |
| `ForecastCellLayerTest`, "a view asks for the blocks its cells can reach" | A view whose south edge is just above a block's lowest centre asks for that block only; a north edge just below the next block's lowest centre also asks for that next block. | The view's edges are the base's moved by one whole degree, the expected blocks by the same, and the comment's numbers with them. |
| `ForecastCellLayerTest`, "below the minimum zoom nothing is requested, and at it even a tablet-sized view stays under the backstop" | Below the minimum zoom nothing is requested; at it the same view asks for its one block; the tablet-sized worst case is 54 blocks; a tilted camera's view is refused. | The shared view and its one expected block moved as above; the worst case and the tilted view are unchanged (they were never moved). |
| `RecordsFilterChipsTest`, "EDIT_NEW_FIND lands on Finds editing with the Finds chip selected" | A new find opened for editing lands on Records with the Finds chip selected, the Photos section shown, and its position shown as the screen formats it. | The expected text is the fixture's position at four decimals, as the screen draws it. The fixture is unchanged. |

Why a whole degree, not the previous fraction. Each forecast claim sits on whole-degree block edges
(a centre exactly on a whole degree, an area wholly inside one block, a view edge just above a
block's lowest centre). Only a whole-degree move keeps every one of those relations, and moving the
expected blocks alone, with the fractional fixtures, would have changed what three of the
assertions check (two to "edge included", one to "reaches the block below"). So the fixtures in those
four tests were moved again, by a whole degree from the base, and the expected blocks moved with them.
That is D1's "moves each expected forecast square … along with its test point", with the test
point's move changed from a fraction to a whole degree (see "Decided beyond scope").

Checked before pushing:
- A script in the scratch area made the edits as exact-string replacements, each required to match
  once. Run with a zero move, it reproduces the base's text for those four tests exactly, so it moves
  only what it should.
- The model above, run on the edited values, passes every changed assertion.
- The four-decimal text was checked with the JDK's own formatter (`jshell`, not Gradle): the
  fixture's longitude is exact in binary and rounds half-up, as `%f` does, to the text now expected.

No assertion was loosened, dropped or widened. The other tests in the three files are untouched;
their claims do not depend on block edges or shown text. `app/src/main` is not changed.

### D3, the saved prompt

In `prompts/preserved/2026-09-30-18.md`, the place named as the owner's area becomes
`[a city near the owner's home; removed 2026-11-06]`, beside the earlier point marker in the same
parenthesis. A word diff shows one removed word group (the name) and one added (the marker); nothing
else in the file changes. The name is no longer in that file (counted).

### D2, the three measurements

Left as they are, on the owner's word. The scanner counts them because each is a one-decimal number
that falls in the latitude range it watches; they are a climb in metres (one test comment, one audit)
and one render-metrics table cell, not places.

### Evidence (B4)

**Scanner.** Used by path only. Its files mode was proven non-zero this session before any 0 was
trusted: on the base versions of the three test files (latitude and longitude finds), on the added
lines of a diff that puts the base's values back (latitudes, longitudes and pairs), and on #501's
files as they stood before #501 (also helper pairs and search-list hits). Its diff mode was found to
count a diff's removed lines as well as its added ones (a diff and its reverse read the same), so the
push diff was scanned as its added lines only, in files mode.

- Push diff (`c19f9245c`, added lines): 0 on every count. Commit message: 0. The four changed files:
  0 each.
- Whole tree at `c19f9245c` (files mode, 3 676 files): 23 latitudes, 2 longitudes, 0 pairs, 0 helper
  pairs, 0 grid references, 0 tiles, 0 search-list hits. That is the previous section's branch figure,
  unchanged. Per file, the old scanner finds the same 18 files with the same counts (Group 3); the new
  scanner adds exactly the three measurement files of D2 and nothing else.

**CI.** Green; see "CI (B4)" below.

### Disclosure (B4)

#### Confirmed and inferred

- Confirmed from the code: each failure's cause above, the forecast ones also by a model that
  reproduces CI's actual values.
- Confirmed: none of the four changed files is changed by `nav-card-kind-line` or `next-turn`,
  checked against both branches' file lists before editing.
- Inferred: that the edit screen this test lands on draws the `LogEntryDetailScreen` row and not the
  report screen's. Both build the text the same way, so the expected text is the same either way.

#### Could not determine

- Grid-reference and tile counts were not proven non-zero in this session. A grid reference removed
  earlier from a records-branch report did not read non-zero in its old version either, and making a
  case would mean deriving one from the watched area, which I would not do. Their 0s rest on the
  earlier sessions' proofs.
- How the scanner's diff mode is meant to be invoked: it was not read (reading it was refused, and
  the dispatch says by path only). The added-lines method above was used instead and is proven.

#### Premises that were wrong

- "Moving each expected block … with its fixture" was not enough for the four forecast tests: with the
  fractional fixtures, moving the expected blocks would have changed three assertions' claims. The
  fixtures were moved by a whole degree instead (above).
- The previous report's guess for the filter-chips test (the per-value latitude) was not the cause; it
  was the expected text's decimal places.
- The four forecast tests' values were never scanner finds themselves (a zero move reads 0). They moved
  only because each file shared one constant. The finds in those files are in other tests, which are
  untouched here.
- The same place name D3 removes is also in three other files: `RECORD.md`, an app test
  (`EstimateOfflineTileCountTest`), and `docs/audits/2026-09-30-topo-night-completion-report.md`.
  D3 named one file, so they are not changed (question below).

#### Decided beyond scope

- The fixtures' move in the four forecast tests changed from a fraction to a whole degree.
- The whole-degree move's direction was my choice: of the two one-degree moves, the one that leaves
  the values nearer point P. The scanner reads 0 either way and both keep every claim (checked with
  the model for both).
- The marker's wording for the name ("a city", after the earlier marker's form).

### Questions for the owner (B4)

4. The place name D3 took out of one saved prompt is also in three other files (the decision record,
   one app test and one old audit). Take it out of those too, by kind as here?

### CI (B4)

**Green.** Run 42301096531 on `c19f9245c`: all eight jobs succeeded. The build log has no compile
errors. Compared by test name, from this run's own `unit-test-report` JUnit XML (downloaded fresh
into an empty directory), against pre-main's run 42292140229 on `a5a2a392f`:

| Run | test cases | passed | skipped | failed |
|---|---|---|---|---|
| pre-main `a5a2a392f` | 6 838 | 6 814 | 24 | 0 |
| this branch `c19f9245c` | 6 838 | 6 814 | 24 | 0 |

Same 6 838 names, and every name has the same result in both runs. The five tests above pass again.
The previous run's five failures were confirmed as failures of the moved values, not a stale run:
each message named a value its own edit had moved, and a model reproduced it. This run's passes
follow from edits to exactly those lines.

### Hand-back (B4)

Done: B4 (the five tests, causes confirmed from the code), D3 (one name in one saved prompt), D2
(left). CI green by name; whole-tree scan unchanged from the previous section's branch figure. PR #502
stays ready, not merged. Open: question 4 above.
