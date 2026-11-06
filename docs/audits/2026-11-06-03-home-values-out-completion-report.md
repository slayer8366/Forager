# Dispatch 2026-11-06-03, Part 2: the home's remaining values out of pre-main (completion report)

Hazel Cairnwright (coder), 2026-11-06. RECORD -2182. Branch `home-values-out`, cut from pre-main
`61f99fbe2` (checked against the remote before starting: pre-main's head was `61f99fbe2`). PR #501,
ready, not merged. Places are named by kind only; this report holds no coordinate, grid reference,
tile, range or derived value.

## What landed

The private sorting report on pre-main `61f99fbe2` sorted the scanner's single in-box values into
three groups. On the owner's "Fix the 13 and the 112":

- **Group 2 (lone longitudes in the scanner's band, 112 values).** In each of the test files that
  hold them, every longitude in the same family moves east by one constant offset, with the whole
  file, so every distance, ordering, bounding box and identity inside the file holds (a pure
  longitude translation preserves great-circle distances). Latitudes do not move. The same offset
  is applied to the one source comment (`AvailabilityPureFunctions.kt`, the KDoc example pair, a
  comment only) and to the one old audit that quotes that same example
  (`docs/audits/2026-08-21-phase1-code-audit.md`).
  - **Tile-sensitive fixtures.** The offline-tile fixtures at the tile archive's northern edge
    (`OfflineMapRadiusBudgetGuardTest`, `OfflineTileBudgetTest`, `AvailabilityViewModelOfflineMapsTest`,
    `OfflineMapsPanelTextTest`) have tile counts that depend on where the centre sits against tile
    edges. Their longitude moves instead by a whole number of zoom-10 tile widths; the offline zoom
    range starts at 10 (`OfflineMapRepository.MIN_ZOOM`), so every tile column at zooms 10 to 15
    shifts by a whole number and every count holds.
  - **Kept out of the shift**, because a figure in the same file is computed from a fixture's
    absolute position, not its relations: the offline-region fixture in `RecordDetailsSheetTest`
    (its tile estimate, its centre line and the comment naming it), and the two Group 3 lines of
    `GpxImportFixtures`. One counting-pattern line in `GetJournalEntryHighlightsUseCaseTest` was
    restored to its original after the push-diff scan flagged it (its latitude is a one-decimal
    border value the new scanner counts; the longitude change on that line was not needed).
  - **One expected value moved with its fixture:** `MapBubblesTest`'s forecast-block assertion.
    The forecast block is the whole-degree square under the tapped cell, and the shifted cell sits
    in the next square east. What the test checks (the tapped cell's block is asked for) is
    unchanged.
- **Group 1 (13 values that can place the home, plus the search-list hit).** Each gets independent
  made-up values near point P, not the Group 2 offset:
  - the two helper-split find fixtures (`FindDeleteReappearsOnMapTest`, `MapRecordsDeletedNotDrawnTest`,
    the latter with its photo fixture): latitudes and the helper's longitude;
  - `EntryMapFrameTest`'s mixed-kinds fixture: all five points and the expected frame, chosen so the
    same kind supplies each edge as before (find north, waypoint south and east, photo west, the
    track inside);
  - the camera-snapshot pair shared by `LayoutFixesPortraitTest` and `LayoutFixesMapHelpersTest`;
  - `MapReturnCoverTest`'s "another place" camera longitude (the search-list hit), still far beyond
    the cover's centre tolerance, so it still reads as moved;
  - `docs/audits/2026-09-06-filter-and-tile-cost-prebuild-report.md`: the reference point is a
    made-up stand-in (point P), the places that tied it to a person now say "the reference point",
    and a note under the table says the original point is withheld and every figure was computed at
    it, not at the stand-in;
  - `docs/audits/2026-11-05-05-trip-bubble-actions-completion-report.md`: the real trip pair is
    replaced with the earlier sweep's marker (not a made-up value).
- **Group 3** is unchanged. `app/src/main` changes one comment line.

41 files changed in the code commit (`1d115a366`); this report is the only other file. No index row is added here; the index is the planner's after merge.

## Evidence

**Scanner (private, off GitHub), new version.** Taught two forms, with the previous version kept
beside it: values with one decimal place, and pairs split across a helper (one axis passed in, the
other supplied by the helper's body), reported as their own count. Proved on:

- synthetic cases: a one-decimal in-box pair (old 0, new 1 pair); a helper-split pair with an
  out-of-box value between its halves so adjacency cannot see it, in both directions (old 0 pairs,
  new 1 helper pair each); two negative controls (0 on both versions);
- pre-main `779599c3f`'s `app/src/test`: old 181 pairs (matching the measured figure), new 188 pairs
  plus 4 helper pairs;
- pre-main `61f99fbe2`'s whole tree: old lat 27, lon 120, pair 0, search-list 1 (matching the sorting
  report); new 7 pairs and 4 helper pairs, the 4 helper pairs in exactly the report's two Group 1
  helper files.

**This branch's whole tree** (diff from the empty tree):

| Scanner | lat | lon | pair | helper pair | grid ref | tile | search-list |
|---|---|---|---|---|---|---|---|
| old | 20 | 2 | 0 | n/a | 0 | 0 | 0 |
| new | 63 | 202 | 6 | 0 | 0 | 0 | 0 |

The old scanner's 20 and 2 are Group 3 exactly (20 latitudes, 2 longitudes, in Group 3's files).
The new scanner's further counts are all one-decimal border values the sorting report could not
see; see "Premises that were wrong".

**Push diff:** the new scanner reads 0 on every count for this PR's diff and on each commit message
and the PR text; proved non-zero on the same diff reversed.

**CI.** Run 42286084442 on `1d115a366` (the code commit): all eight jobs green. Results by test name,
read from the run's `unit-test-report` JUnit XML and compared with pre-main's run 42245483101 on
`61f99fbe2`, both test tasks (current and older SDKs) together:

| Run | test cases | passed | skipped | failed |
|---|---|---|---|---|
| pre-main `61f99fbe2` | 6 838 | 6 814 | 24 | 0 |
| this branch `1d115a366` | 6 838 | 6 814 | 24 | 0 |

The same 6 838 names in both, and no name changed result. Checked that the sample holds the
changed classes: every one of ten sampled changed test classes (among them `MapBubblesTest`,
`EntryMapFrameTest`, both helper-split files, the tile guard, `AvailabilityViewModelOfflineMapsTest`,
`RecordDetailsSheetTest`) has its XML in the PR run's report. A suite identical before and after is
what "unchanged in what they check" predicts here, not a sign the edit went unexercised: each
changed fixture is read by the tests in its own file.

## Disclosure

### Confirmed and inferred

- Confirmed: the old scanner on the branch tree finds only Group 3 (counts above).
- Confirmed: no file this PR changes is changed by PR #498 or PR #500 (their file lists against
  pre-main, checked before editing).
- Confirmed by reading the source: the cover's camera match is a pixel tolerance at the camera's
  zoom; the camera restore has no distance logic; the offline zoom range starts at zoom 10; the
  forecast block is a whole-degree square.
- Inferred, resting on CI: that no other test in the shifted files depends on an absolute position
  (a tile, a block, a grid) rather than on relations. CI's result by test name is the check.

### Could not determine

- Whether any shifted fixture is read by a device check or a screenshot record outside the unit
  tests. None was found in `docs/`; not searched beyond it.

### Premises that were wrong

- **"The remaining single values are only Group 3's" cannot hold under the new scanner.** The
  sorting report's note that one-decimal values inside the box are only border values is true, but
  there are many of them: on this branch the new scanner still counts 43 latitudes and 200
  longitudes beyond Group 3, in about 90 files (test fixtures, a few old audits and saved records,
  scripts, the tile worker's tests), and 6 pairs that are box corners written with one decimal, in
  4 test files (`MushroomLogViewModelWiringTest`, `AvailabilityViewModelOfflineRoutesTest`,
  `MapRecordsDeletedNotDrawnTest`'s offline-region fixture, `SiblingDeletesReappearOnMapTest`). None
  of them is in the sorting report, so none is in the owner's go; they are left for the owner.
- The scanner's helper-pair count works on whole files. On a diff it sees a helper split only if
  the helper's own line is in the diff, so a push-diff scan alone cannot vouch for helper splits;
  every push here was also scanned as a whole tree.

### Decided beyond scope

- Longitudes outside the band in the same Group 2 files moved with the band values, so the
  fixtures' relations hold. They place nothing; moving them was the means, not a target.
- The forecast-block expectation in `MapBubblesTest` moved with its fixture (above).
- The tile-cost audit's note under its table, so the stand-in does not read as the place its figures
  describe.
