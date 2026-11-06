# Dispatch 2026-11-06-03, Amendment 1, B6: the name everywhere, a made-up tile-count fixture, the audit's tile out (completion report)

Hazel Cairnwright (coder), 2026-11-06. On the owner's D5. PR #504 to pre-main from `b6f0b464d`
(#503's merge), branch `place-name-out-2`; and one commit on `records-after-173`. Ready, not merged.
Places are named by kind only; this report holds no place name, coordinate, tile or derived value.

## What landed

**PR #504, commit `1c3bc65c9`:**

- `MapCameraMovesTest.kt`: a camera value named after the city (two lines) is renamed `cityCamera`.
  Nothing else changes.
- `EstimateOfflineTileCountTest.kt`, the served-ceiling test:
  - The fixture moves to a made-up point near P (three decimals). It was chosen with a private
    model of the app's own estimate, reproduced from `GeoDistance.boundingBox`, `SlippyMapTile` and
    `estimateOfflineTileCount`. On the old fixture that model reproduces every figure the test and
    its comments give, at both ceilings. At every zoom from 10 to 16 the new point gives the same
    tile count as the old one, for 5, 15, 24 and 50 km. So the three asserted counts hold, and so do
    the comments' figures for ceiling 14 and for 50 km. Every tile edge is at least about 0.002 of
    a tile from a boundary, so a floating-point difference between the model and the JVM cannot
    flip a count.
  - The comments no longer call it the owner's point: "the owner's own latitude (...)" becomes "the
    made-up point (see below)", "at the owner's coordinates" becomes "at the same made-up point",
    "the region the owner downloaded" becomes "the size of a region the owner downloaded". A
    closing paragraph says the point is made up and why every figure still holds. The value is
    renamed `madeUpPoint`.
  - The test's claim and all its literals are unchanged.
- `docs/audits/2026-09-30-topo-night-completion-report.md`: the sample tile, given twice (lines 26
  and 36), becomes `[a map tile near the owner's home; removed 2026-11-06]`.

**Records branch, commit `3482abb31`** (on `8acc54bbe`, fast-forwarded first, pushed without
rejection): the name becomes D3's marker in the eight files B5 listed outside `app/`. That is 13
occurrences: `RECORD.md` (1), the 2026-10-28 S22 audit (1), the `records-pi-after-166` RECORD copy
(1), the four 2026-10-30 street-map reviews (2, 2, 3, 2) and `prompts/preserved/2026-10-21-03.md`
(1). The word diff is the name out, the marker in, nothing else.

## Evidence

**Scanner** (private, by path only, files mode):

- It can read non-zero: the PR's removed lines read 1 longitude (the old fixture's).
- PR added lines, the three changed files whole, the commit message and the PR text: 0 on every
  count.
- Records commit, added lines and message: 0.

**Name and tile** (fixed-string grep, which finds both in the PR's removed lines): 0 in every added
line, message and PR text.

**CI. Green.** Run 42309269100 on `1c3bc65c9`: all eight jobs succeeded. Its log (9 843 lines,
fetched) has no compile errors. Compared by test name, from each run's own `unit-test-report` JUnit
XML (downloaded fresh into empty directories), against pre-main's run 42308597327 on `b6f0b464d`
(green):

| Run | test cases | passed | skipped | failed |
|---|---|---|---|---|
| pre-main `b6f0b464d` | 6 838 | 6 814 | 24 | 0 |
| this branch `1c3bc65c9` | 6 838 | 6 814 | 24 | 0 |

Same 6 838 names in both, and every name has the same result. `EstimateOfflineTileCountTest` (7)
and `MapCameraMovesTest` (1) pass. The served-ceiling test passing at the new point is the check
that the model's per-zoom counts hold on the JVM. It would fail if any of the three counts had
moved.

## Disclosure

### Confirmed and inferred

- Confirmed: none of the three files is changed by `nav-card-kind-line` or `next-turn`, checked
  before editing.
- Confirmed by the model: the per-zoom counts above, before and after.
- Inferred: that the model matches the JVM. It reproduces the test's own literals exactly, and the
  edge margins rule out a rounding flip. CI is the check.

### Could not determine

- Whether the other values in `MapCameraMovesTest` (its camera's position) are real. They were not
  in scope; the scanner reads 0 on that file.

### Premises that were wrong

- "Moved ... by whole tile widths." A move by a whole number of tile widths keeps the old point's
  offset inside its tile. Anyone who knew the move was whole tiles could recover the old longitude
  from the new value, to within the spacing of the tile grid, and that spacing is narrower than the
  area the deleted name already gave away. So the new point is chosen freely on a three-decimal grid
  near P, and it is required to give the same tile count at every zoom. That keeps the owner's stated
  aim (the three tile counts stay the same) without that leak.
- The first two free candidates kept every count but each had a latitude the scanner flags. The
  third, which reads 0, is the one used. The comment's latitude figure was dropped, not replaced, so
  no one-decimal value stands in for it.

### Decided beyond scope

- The new names `cityCamera` and `madeUpPoint`, and the tile marker's wording.
- Requiring the counts to match per zoom from 10 to 16, and for 50 km too, so that the comment's
  revert figures still hold, not only the three asserted totals.
- No row in `docs/audits/README.md` (the planner's index).

## Name and tile, after both commits (fixed-string grep, counts only)

- Pre-main (this branch): the name 0 lines; its camel-case identifier form 0; the tile 0.
- Records branch (`3482abb31`): the name 3 lines, the identifier form 6 lines and the tile 2 lines.
  All of them are in pre-main's copies of `prompts/preserved/2026-09-30-18.md`, the topo-night audit,
  `EstimateOfflineTileCountTest.kt` and `MapCameraMovesTest.kt`. None of these files has changed on
  the records branch since the two branches' merge base. D3, #503 and this PR clean each of them on
  pre-main, so merging pre-main in clears them. Outside those four files the records branch reads 0.
