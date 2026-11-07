# Dispatch 2026-11-06-03, Amendment 1, B5: a place name out of three more files (completion report)

Hazel Cairnwright (coder), 2026-11-06. On the owner's D4, "Take it out of all three". PR #503 to
pre-main from `5c623a8ba` (which contains `944eb8de1`), branch `place-name-out`; and one commit on
`records-after-173`. Ready, not merged. Places are named by kind only; this report holds no
place name, coordinate, tile or derived value.

## What landed

The city name that D3 took out of `prompts/preserved/2026-09-30-18.md` was found by word-diffing that
file at D3's commit (`c19f9245c`), into the private scratch area only. Each occurrence below is
replaced with D3's marker, `[a city near the owner's home; removed 2026-11-06]`.

**PR #503, commit `41d0d676e`:**

- `EstimateOfflineTileCountTest.kt`: one name in a KDoc comment becomes the marker. The test's local
  value was itself named after the city (a camel-case form of the name, on four lines); it is renamed
  `cityNearHome`. No number changes, so every literal the test asserts and every fixture is as it was.
- `docs/audits/2026-09-30-topo-night-completion-report.md`: one name, the marker.
- `RECORD.md` (pre-main's copy): one name in the entry recording the topo-night tile measurement,
  the marker.

A word diff of the commit shows every removed word is the name or the identifier, and every added
word is the marker or `cityNearHome`.

**Records branch, commit `a720b1463`** (on `1631441d7`, fast-forwarded first, pushed without
rejection): the same entry's name in that branch's `RECORD.md`, the marker. That value only; the word
diff is one word group out, one in.

## Evidence

**Scanner** (private, used by path only, files mode). Proven non-zero first: the test file at
pre-main and at its pre-B2 version both read non-zero longitudes; scanned one line at a time, the
base's one find is on the fixture line the rename touches.

- PR commit, added lines: 1 longitude, the fixture line's existing value, unchanged and carried on
  the renamed line; every other count 0. The three changed files whole: the same 1 longitude in the
  test, 0 in the audit and RECORD.md. Commit message and PR text: 0.
- Records commit, added lines and message: 0 on every count.

**The name itself** (the scanner does not look for names; counted with a fixed-string grep, which
reads 3 on the PR's removed lines, so it can read non-zero): 0 in both commits' added lines, both
messages and the PR text; 0 in the three files on the branch.

**CI. Green.** Run 42305227291 on `41d0d676e`: all eight jobs succeeded; its log has no compile
errors. Compared by test name, from each run's own `unit-test-report` JUnit XML (downloaded fresh into
empty directories), against pre-main's run 42303916388 on `944eb8de1` (green; pre-main's head
`5c623a8ba` is a `[skip ci]` index row on top of it):

| Run | test cases | passed | skipped | failed |
|---|---|---|---|---|
| pre-main `944eb8de1` | 6 838 | 6 814 | 24 | 0 |
| this branch `41d0d676e` | 6 838 | 6 814 | 24 | 0 |

Same 6 838 names in both, every name with the same result; `EstimateOfflineTileCountTest`'s 7 tests
pass. That the result is identical before and after is expected here (a comment word and a local
name changed, no behaviour), so the run shows the rename compiles and changes nothing, not that any
test covers the change.

## Disclosure

### Confirmed and inferred

- Confirmed: none of the three files is changed by `nav-card-kind-line` or `next-turn`
  (`git diff --name-only origin/pre-main...` each, checked before editing).
- Confirmed: the test's claim is unchanged; only a comment word and a local name changed.
- Inferred: the rename cannot change behaviour (a local `val` used only in its own test).

### Could not determine

- Whether the remaining values tied to the name would place the home. Two are worth naming:
  - The test's fixture beside the old comment is the one the comment calls "the owner's
    coordinates", given to three decimals. The scanner flags its longitude only (the Group 3 single
    earlier sweeps left), not its latitude and not a pair. Changing it would change the three tile
    counts the test asserts, so it would be an assertion change, a stop. Left as it is.
  - The topo-night audit names a map tile beside the old name ("one co-registered tile ... over"
    the city). The scanner does not flag it. At its zoom a tile covers an area about the size of the
    city's surroundings, so it locates roughly what the name did, not the home. Left as it is.
  Both are for the owner (below).

### Premises that were wrong

- "Only the label/name text changes": in the test the name is also a code identifier, so changing it
  means a rename, done here as `cityNearHome`.
- The name has more occurrences than the three files D4 named (see "Other occurrences").

### Decided beyond scope

- The identifier's new name, `cityNearHome`.
- Worktrees under `/mnt/work/forager-wt/` (as dispatched).
- No row added to `docs/audits/README.md` (the planner's index; a serialization point).

## Other occurrences (not changed; a question for the owner)

After both commits, a case-insensitive search for the name (also allowing a space, underscore,
hyphen or nothing between its words):

- **Pre-main (this branch):** `app/src/test/java/com/zynergylabs/forager/app/ui/map/MapCameraMovesTest.kt`
  lines 18 and 30 (the name as an identifier).
- **Records branch, outside `app/`:** `RECORD.md:14697` (a different entry, inside an owner
  quote), `docs/audits/2026-10-28-s22-pr432-round2.md:167`,
  `docs/audits/from-branches/records-pi-after-166/RECORD.md:8563`,
  `docs/reviews/2026-10-30-sorrel-pi35-street-grid.md:49, 102`,
  `docs/reviews/2026-10-30-sorrel-street-v4-relook.md:111, 119`,
  `docs/reviews/2026-10-30-sorrel-street-v4-relook2.md:86, 99, 107`,
  `docs/reviews/2026-10-30-sorrel-street-v4.md:180, 188`, `prompts/preserved/2026-10-21-03.md:23`.
- **Records branch, files that are pre-main's copies** (each unchanged there since the two branches'
  merge base `61f99fbe2`, so merging pre-main in after D3 and this PR brings the cleaned versions):
  `prompts/preserved/2026-09-30-18.md:31` (D3's file), the test's lines 26 and 45 to 48, the
  topo-night audit's line 26, and `MapCameraMovesTest.kt` lines 18 and 30.

## Questions for the owner

1. The other occurrences above: take the name out of those too?
2. The test fixture the comment calls the owner's coordinates, and the map tile beside the old name
   in the topo-night audit: leave them, or replace them (the fixture only with its asserted counts
   re-derived, which changes assertions)?
