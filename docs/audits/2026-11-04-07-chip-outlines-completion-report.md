# The map type chips' outlines, and the night chip in rust red: completion report (PR #489)

Dispatch `prompts/preserved/2026-11-04-07-map-type-chip-outlines.md` (on `origin/records-after-173`), with Amendment 1 (A6,
A7, B5, B6, and B8 without its 80% parts) and Amendment 2 (A10, B9, C8, C9). RECORD -2089 to -2096. Written by Hazel (Opus),
2026-11-04. Phase 1 was done by an earlier Hazel session; this session wrote Phase 1b and built Phase 2. The Phase 1 and 1b
reports are filed beside the dispatch.

Each claim is marked **read** (file and line, or the named CI run's JUnit XML or log), **observed** (pixels or command
output), **inferred**, or **not verified**.

## Base

- The branch `map-type-chip-outlines` was cut from `origin/pre-main` 9b58c66a4. pre-main was still 9b58c66a4 when this
  report was written, and is an ancestor of the branch (read, `git fetch`, `git merge-base --is-ancestor`).
- Worktree `~/.cache/forager-wt/map-type-chip-outlines`. Nothing was edited in the planner's worktree.

## Commits and PR

| Commit | What |
|---|---|
| 3a80644ef | Tests first, in their own commit: `MapTypePanelChipHarness.kt` and `MapTypePanelChipOutlineTest.kt` (new), and K1 and K3 in `MapTypePanelOfflineChipTest.kt`. |
| 8184f91de | The change, in `MapModeChips.kt` only. |
| (this report) | This file. |

PR #489 into pre-main, opened as a draft so its own checks ran on each commit. It merges only on the owner's word.

## What was built

All of it is in `MapModeChips.kt` (116 to 164 lines, most of the new lines being the reasons) (read, at 8184f91de):

- **`normalChipColors()`:**
  - by day, Material's own colours, `FilterChipDefaults.filterChipColors()`: the pale #F6DFC8 fill and #3A2008 label (A2);
  - at night, `nightNormalChipColors()`, which overrides only the fill, to `MushroomDeep` #A2612C (A3, A6).
  - The night label is Material's own `onSecondaryContainer`, #F6DFC8, the owner's "Pale #F6DFC8" (A7). `PureWhite` is not used.
- **`mapModeChipBorder(selected, standsAside)`:**
  - a 1 dp `onSurface` outline on the selected chip: Bark #3B2E24 by day, #EDE3D0 at night;
  - for the grey chip that stands aside, Material's own border, which draws nothing visible on a selected chip (A4, "No, normal chip only");
  - the unselected chips keep Material's 1 dp `outlineVariant` outline, because only the `selected*` border parameters are set.
- **The row** (`MapModeChipRow`) changed in only two places:
  - its normal-colours branch calls `normalChipColors()`;
  - its `FilterChip` gains `border = mapModeChipBorder(...)`.
  - The grey branch, `chosenMapModeStandsAside`, the unselected chips and the style-load log line are untouched.
- **Every chip fill stays solid** (A10). MapChrome.kt, MapChromeAlphaTest.kt, MapTypePanel.kt, Theme.kt and Color.kt are untouched (read, `git diff --stat 9b58c66a4..8184f91de`: four files, the three tests and MapModeChips.kt).

## Failing first (B2): run 41894154203, at 3a80644ef

The tests ran over pre-main's chip code. The build log shows no compile errors. Totals from the JUnit XML: 948 files, 6,556 tests, 14 failures, 0 errors, 24 skipped (read). All 14 are the predicted ones, and none fails outside the named classes:

| Test(s) | Failure as named in the XML |
|---|---|
| The 4 outline tests (Maps tab and entry map, day and night) | "left edge: a #3B2E24 outline is drawn; the edge reads [#F6DFC8 × 18]" by day, and "[#6B421A × 18]" with #EDE3D0 expected at night. With no outline, the fill reaches the node's own edge. |
| The 2 night-fill tests | "expected:<#A2612C> but was:<#6B421A>" |
| 8 of `MapTypePanelOfflineChipTest`'s 10 (K1) | The night normal fill: expected #A2612C (0.635, 0.380, 0.173), was #6B421A (0.420, 0.259, 0.102) |

- **Passed by construction, not shown to bite here:** the day fill and label, the night label (B6: pre-main already draws #F6DFC8), the unselected chips, and the grey chips. The reverts below show each of them bites, except the grey fill and label (see below).
- **The job that deletes the Street map** went red with 13 failures: the same 6 and 7. The fallen-back test returns early there, at `MapTypePanelOfflineChipTest.kt:216` on the branch (read, that run's log).

## Revert checks (B3, B5, B6, K5)

Each is one edit to `MapModeChips.kt` on its own throwaway branch, never merged. Each was run by `gh workflow run ci.yml --ref <branch>` and filtered to the three affected classes: `MapTypePanelChipOutlineTest`, `MapTypePanelOfflineChipTest` and `OwnTrailFallbackRetryTest`.

For every one:
- the build log was read first: 0 compile errors, `assembleDebug` BUILD SUCCESSFUL;
- the XML holds exactly 33 tests (20 + 10 + 3), so each ran on the full sample;
- the file was restored from a copy saved before the edit, not from git;
- afterwards the worktree was clean at 8184f91de, with the forward change's five marker lines present (observed, the runner's output).

| Revert | Branch, run | One-line edit | Failed (and nothing else) |
|---|---|---|---|
| r1 | -r1 2b5745ac5, 41897205441 | delete the `border =` argument | The 4 outline tests: the edges read only the fill, #F6DFC8 or #A2612C |
| r2 | -r2 aad96546b, 41897234560 | the night branch returns `filterChipColors()` | The 2 night-fill tests (was #6B421A) and the same 8 K1 tests: 10. Not the night label (W1) |
| r3 | -r3 e0bb89326, 41897248463 | `mapModeChipBorder`'s `if (standsAside)` becomes `if (false)` | The 4 grey tests ("the edge reads [#3B2E24 × 3, #D5CEBF …]", and #EDE3D0 × 3 then #444444 at night) and K3's check in the fallen-back test: 5 |
| r4 | -r4 0bacf98e0, 41897264504 | add `borderColor = MaterialTheme.colorScheme.onSurface` | The 4 unselected tests ("Material's #D5CEBF outline; the edge reads [#3B2E24 × 3, …]") |
| r5 | -r5 2762c140d, 41897276893 | the day branch returns `nightNormalChipColors()` | The 2 day-fill tests (was #A2612C), the day iteration of 2 `MapTypePanelOfflineChipTest` tests, and `OwnTrailFallbackRetryTest`'s retry test at its :174: 5. Not the day label (W2) |
| r7 | -r7 e6c54ee49, 41897288521 | the night colours add `selectedLabelColor = …PureWhite` | The 2 night label tests: "0 exact pixels of 5280" |
| r8 | -r8 786a6dbaa, 41897303444 | the day colours add `selectedLabelColor = …PureWhite` | The 2 day label tests: "0 exact pixels of 5280" |

**Not shown to bite by any revert:** the grey chip's fill and label in the new class. This dispatch doesn't touch that branch, and the existing grey tests in `MapTypePanelOfflineChipTest` guard it (K5, RECORD -2095).

## CI on the last code commit (B4): run 41897155928, at 8184f91de, PR #489

Every job is green: the build and tests, the Street-deleted build, lint, the release compile, and the map style, cutter and Worker checks (read).

- **Build job JUnit XML** (`unit-test-report`):
  - `testDebugUnitTest`: 942 files, 6,532 tests, 0 failures, 0 errors, 24 skipped;
  - `testDebugUnitTestOlderSdks`: 6 files, 24 tests, 0 failures, 0 errors, 0 skipped;
  - in all, 6,556 tests, 0 failures, 0 errors, 24 skipped.
  - That is the baseline's 6,536 (RECORD -2083) plus the 20 new tests (read).
  - All 33 tests of the three named classes passed.
- **The Street-deleted job:** its own summary of its JUnit XML reads "942 suites: tests 6526, failures 0, errors 0, skipped 24" (read, its log; that job uploads no XML).
- The commit with this report changes only this file. Its run is on the PR.

## Contrast, as measured in the tests

These come from the forward run's `MEASURED` lines, at xxhdpi (observed, the XML's system-out):

| | Maps tab | Entry map |
|---|---|---|
| Day outline #3B2E24 on the fill #F6DFC8 | 10.18:1 | 10.18:1 |
| Day outline on the panel as drawn | 9.75:1 (panel #E2DED3, over #FAF8F3) | 9.76:1 (panel #E2DED4, over #FAFAFA) |
| Day label #3A2008 on the fill | 11.73:1 | 11.73:1 |
| Night outline #EDE3D0 on the fill #A2612C | 3.85:1 | 3.85:1 |
| Night outline on the panel as drawn | 13.24:1 (panel #1D1D1D, over #1B1B1B) | 9.07:1 (panel #393939, over #FAFAFA) |
| Night label #F6DFC8 on the fill | 3.81:1 | 3.81:1 |

- **"Over"** is the root's pixel at the same place just before the panel opened. That is this test's own background, not a map (K6).
  - On the Maps tab, it is the screen's own background.
  - On the entry map, the stand-in map draws nothing, so the window's light background shows through, at night too. That is why the entry map's night panel figure is lower.
  - Over a real map, the panel figures vary with the terrain, and were not measured.
- **The solid-colour figures** match the dispatch's: 10.18, 3.85, 3.81.
- **The drawn outline** is exactly 3 px at xxhdpi, which is ceil(1 dp × 3), on both edges of the selected chip, at the chip's own edge. The four forward outline tests assert that width and pass. r3's and r4's failure messages print the same 3 px run on the chips they reach (observed).
- **Each label** had 585 exact-colour pixels of 5,280 in its own bounds.

## Calls beyond the dispatch, for the planner to accept or reject

1. **The new class reads the fill 4 dp in, not 3 dp as the old harness did.** This keeps the read past a 1 dp outline even if a chip were narrower than its 48 dp touch target and so centred in it. Observed: the Trail chip's fill reaches the node's own edge (B2), so this margin wasn't needed for Trail.
2. **An outline is looked for in the first and last 6 dp of the chip's middle row.** It must be exactly ceil(1 dp) px wide and start within 2 dp of the node's edge. That rules out a hairline and a 2 dp outline.
3. **The entry map's `night` (Night Maps) is passed `false` in the harness.** The chips follow the app's Night Mode, which is `ForagerThemeWithNoSignal(darkTheme = …)`, as A2 defines day.
4. **The label check counts exact-colour pixels** inside the label's own `Text` node, at a floor of 20, and also requires no #FFFFFF pixel.
5. **The expected colours are written as hex in the tests.** They are not read from `Color.kt`, the scheme or the code under test, so a chip drawn any other colour fails. K1's night value in `MapTypePanelOfflineChipTest` is the hex #A2612C for the same reason.
6. **The grey cases on both entry points choose Topo by a real touch with signal,** then set no signal with one downloaded area and reopen Layers by touch.
7. **The revert runs were filtered to the three affected classes.** The forward run is the full suite.
8. **`MapModeChipRow`'s doc comment** now names `normalChipColors` for the normal fill and says the grey chip has no outline. It had named `secondaryContainer`, which is now day only.
9. **22ae67021's `MapModeChipSelectedOutline` semantics key was not re-added** (Phase 1, item 4). The outlines are read from pixels only. The existing `MapModeChipSelectedFill` key now records #A2612C at night on every chip, as it recorded `secondaryContainer` on every chip before.

## Convention checked

- **Material's own `FilterChipDefaults`** (material3 1.5.0-alpha26 bytecode, Phase 1 and 1b): the unselected chip's outline is 1 dp, so the selected chip's new 1 dp matches it.
- **22ae67021's `selectedChipBorder`:** the same mechanism, `filterChipBorder(selectedBorderColor = onSurface, selectedBorderWidth = 1.dp)`.
- **`MapTypePanel.kt:115`:** the panel's own 1 dp border.
- **Apps:** none checked.

## Not verified

- **The phone.** It was not touched; the device check below is the owner's.
- **The press ripple's colour.** It was not observed, and no test reads it (S4: "Leave it to Material").
- **The night-mode fade.** No test changes the theme while the panel is open.
- **Contrast over a real map.** The tests' backgrounds are a stand-in.
- **Layouts tested:** only w384dp-h823dp portrait. The landscape icon cluster and fullscreen reach the same `MapTypePanel`, but weren't run here.
- **The Street-deleted job's counts** were read from its log's summary line, not from XML I read myself.

## Device check (Amendment 2, C9; the owner's to look at)

Nothing shows through the chips.

1. **Day, Maps tab, Layers.** Pass: the selected chip is pale and solid, with a thin dark outline; the other two look as now.
2. **Day, a journal entry's map, Layers.** Pass: the same.
3. **Night, only if the owner chooses to look.** Pass: the selected chip is solid rust red, with a pale label and a thin cream outline. If it is dark brown, the night branch wasn't reached; the owner's 9:11 screenshot shows that old dark brown.

The debug APK from run 41897155928 (`app-debug-apk`) is built from 8184f91de.
