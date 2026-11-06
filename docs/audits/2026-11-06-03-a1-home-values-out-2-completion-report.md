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
