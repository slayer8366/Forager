# Tablet search bar and the List/Seasonal columns: completion report for PRs #264 to #267 (dispatch 2026-10-18-10, Amendments 2 to 4), with their revert checks

Hazel Cairnwright, 2026-10-19 (Pacific), for dispatch 2026-10-19-02, items 1 and 2. Forager RECORD -1350, -1352 to -1355 (the work); -1374, -1375 (this dispatch).

Written after the fact. The four PRs were merged into pre-main on 2026-10-19 between 04:08 and 04:51 UTC with no completion report (Elder's third top-up audit, item 4). Three of them were `[skip ci]` on the owner's one-case override (RECORD -1354), so their tests had no revert check at the time (the same audit, item 1). This report is built from the PRs, their commits, RECORD -1352 to -1355 and the job logs. The revert checks below were run for it.

Each claim is marked **read** (with its source), **ran** (a CI run I started for this report), or **inferred**.

## What landed

| PR | Merge | Head | CI at merge | What |
|---|---|---|---|---|
| #264 | `e0975dec` | `6d28f4f9` | PR run 39195879726, green (RECORD -1352) | Amendment 2 (-1350). On a tablet in landscape, the Journal's revealed search bar and its open panel sit in the Journal's centred 960 dp column. One place: `tabLandscapePillPlace` in `AvailabilityCompactScaffold.kt`, applied on the Journal only. Also known-bugs rows 2, 7, 8 and 9. |
| #265 | `019c339d` | `8357b1d3` | none, `[skip ci]` (-1354); the pre-main run on `019c339d` (39198777067) was cancelled | Amendment 3 (-1353). The Journal-only condition on `tabLandscapePillPlace` removed, so List and Seasonal's bar and panel take their own column (640 dp then). Tests for List and Seasonal at both rotations, and phone controls. |
| #266 | `b05db019` | `06ad0ef8` | none on the branch, `[skip ci]` (-1355); pre-main run 39199565610 **failed** the two tablet Seasonal tests | Amendment 4 (-1355). `contentCapWidth` is 960 dp on every large screen (was the Journal only), and `SeasonalTab`'s own cap is 960 dp on a large screen in landscape. Tests moved to the 960 dp column. |
| #267 | `21dc4470` | `3d71c0ff` | none on the branch, `[skip ci]`; pre-main run 39201526634 green | Test-only follow-up to #266. The Seasonal tests' 16 dp check moved from the scrolling column's tag (which reports the scroll's bounds, the column's own edges) to the nodes inside it. |

Read: the PR bodies and merge commits (`gh pr view 264..267`), the three commits, and RECORD -1352 to -1355 on `records-after-173`.

## Evidence at the time

- **#264**: PR run 39195879726 green on `6d28f4f9`. Revert check by the planner from the job log (RECORD -1352): `tablet-search-bar-revert-1` (`d885472c`), run 39197332501, 0 compile errors, 62 tests and 2 failures, both of them this change's own tests ("landscape tablet at ROTATION_90 / ROTATION_270 the revealed search bar and its open panel sit in the 960 dp column"). Read from RECORD, not re-read from the log.
- **#265 and #266**: never compiled before they merged (RECORD -1354, -1355). Their first compile was pre-main run 39199565610 on `b05db019`. The build job failed the two tablet Seasonal tests on their positive control: the column's tag measured 139 to 1099, the 960 column's own edges, where the test expected 16 dp inside them. Read from the #267 commit message and PR body.
- **#267**: pre-main run 39201526634 on `21dc4470`, all three jobs green. The CI summary, which reads the JUnit XML: 691 suites, 5160 tests, 0 failures, 0 errors, 24 skipped; `TabletJournalLayoutTest` 29 tests, 0 failures. Read from the build job's log (job 117895319966). This is the first green run covering #265 to #267.

## Revert checks (dispatch 2026-10-19-02, item 1)

These were run by the CLAUDE.md runner rules (`/mnt/work/scratch/release-cleanup/revert.sh`). Each revert is one edited line on its own scratch branch off pre-main `b2d2f4d5`, run on CI with `-f tests='*TabletJournalLayoutTest'`. For each one:
- the file was copied to scratch before the edit and restored from that copy, never from git;
- the build job's log was read for compile errors (` e: ` lines) before the JUnit XML;
- the XML was read from the run's `unit-test-report` artifact, not from the summary;
- afterwards the forward code was confirmed still in place: `git diff origin/pre-main` was empty for both files on `release-cleanup`, and lines `AvailabilityCompactScaffold.kt:1009`, `:1063` and `AvailabilitySeasonalUi.kt:82` were re-read.

All three edits are on pre-main's lines as they stand. All four runs compiled, with 0 compile errors and `compileDebugUnitTestKotlin` and `testDebugUnitTest` both run. Each run's XML holds one file with 29 tests, the same 29 as the class in pre-main run 39201526634.

| Run | Branch, head | Edit | Prediction | Result |
|---|---|---|---|---|
| 39318277121 (baseline) | `pre-main`, `b2d2f4d5` | none | 29 pass | 29 tests, 0 failures |
| 39318390675 (R1) | `release-cleanup-revert-1`, `d40c0dfe` | `AvailabilityCompactScaffold.kt:1009`: `if (largeScreen && compactTab() != CompactTab.LIST)`, so List alone goes back to 640 dp | the 2 List tests fail on the bar, about 160 dp in | 29 tests, **2 failures**: List at ROTATION_90 and ROTATION_270 |
| 39318493894 (R2) | `release-cleanup-revert-2`, `50937b26` | `AvailabilitySeasonalUi.kt:82`: `seasonalMaxWidth = READABLE_CONTENT_MAX_WIDTH`, so Seasonal's inner cap goes back to 640 dp | the 2 Seasonal tests fail on the scrolling column, about 160 dp in | 29 tests, **2 failures**: Seasonal at ROTATION_90 and ROTATION_270 |
| 39318501705 (R3) | `release-cleanup-revert-3`, `d2b9aa2c` | `AvailabilityCompactScaffold.kt:1063`: `if (railBeside && largeScreen && compactTab() == CompactTab.JOURNAL)`, which is #265's condition undone | all 4 fail on the bar, at the area's left edge | 29 tests, **4 failures**: List and Seasonal at both rotations |

The failure messages, from the XML:
- **R1**, both rotations: `List's search bar (DpRect(left=299.0.dp, top=0.0.dp, right=939.0.dp, bottom=36.0.dp)) starts where the column (DpRect(left=139.0.dp, …, right=1099.0.dp, …)) starts expected:<139.0> but was:<299.0>`. The bar is 640 dp wide, centred in the 960 column. That is List's cap at 640, and this edit is the only thing that could produce it: Seasonal and the Journal kept 960 dp and passed.
- **R2**, both rotations: `Seasonal's scrolling column (DpRect(left=299.0.dp, top=36.0.dp, right=939.0.dp, bottom=72.0.dp)) starts where the column (…) starts expected:<139.0> but was:<299.0>`. This is Seasonal's own column at 640 inside the scaffold's 960. The bar was untouched, so List passed.
- **R3**, all four: `<tab>'s search bar (DpRect(left=0.0.dp, top=0.0.dp, right=963.0.dp, bottom=36.0.dp)) starts where the column (…) starts expected:<139.0> but was:<0.0>`. The bar is back in the Maps pill's place at the left (`landscapeTabPillFit`). For Seasonal, the content checks before the bar passed first, as predicted. The Journal's own two search tests passed, because the Journal stays inside the restored condition.

Each failure names the measurement its own edit moves, so none of them is a stale result. The three branches were deleted after the results were read.

**A slip in the runner, disclosed.** My first attempt ran R1 to R3 in one script without stopping on errors. After R1 was pushed and dispatched, the restored copy left the scratch branch's working tree dirty, so the switch to a new branch failed. R2 and R3 were then committed on top of R1's local branch (`657df2dd`, `83ba3433`) instead of on their own branches. Neither push nor dispatch went through (no such remote branch), so CI never saw them. The forward code was not affected, because it is committed on pre-main. I discarded the dirty tree on that scratch branch, rewrote the runner to stop on any error and to require a clean tree and a one-line edit, and ran R2 and R3 again on their own branches from pre-main (the runs above). Remote `release-cleanup-revert-1` was only ever `d40c0dfe`, R1 alone, which is the head run 39318390675 reports.

## A gap in the List tests (a finding, not changed)

The two List tests are named "landscape tablet at ROTATION_90 / ROTATION_270 List's content, search bar and open panel share the 960 dp column" (`TabletJournalLayoutTest.kt:489`, `:491`). They assert nothing about List's content. `checkTabSearchInColumn` (`:453`) computes the column from the area beside the rail (`readableColumn(TABLET_COLUMN)`) and measures only the search bar, the open panel and the panel's two buttons. For Seasonal it also measures the content (`:458-473`); for List it does not.

So R1 fails the List tests only through the search bar, because the bar and List's content share one modifier, `readableContentCap` (`AvailabilityCompactScaffold.kt:1064` for the bar, `:1258` for the tab's content). A change that narrowed List's content and left the bar alone would pass them. Inferred from reading the code, not run.

The assertion that would close it, in `checkTabSearchInColumn` beside the Seasonal block. `ListTab` has no tag on its own container today: its outer `Column(modifier = modifier.fillMaxWidth())` (`AvailabilityResultsUi.kt:165`) would need one, say `LIST_CONTENT_TAG`. That outer column is the right node because these tests open the tab without running a search, so List shows its "Choose a region" text (inferred from `openTab` and `checkTabSearchInColumn`, `TabletJournalLayoutTest.kt:447-455`; not run), not the `LazyColumn` at `:201`.

```kotlin
if (label == "List") {
    val content = bounds(LIST_CONTENT_TAG)
    assertEdgesOf("List's content", content, column)
}
```

Not added: the dispatch's scope is the revert checks, and the planner is taking it to the owner.

## Not checked

- A tablet look on a real device. Robolectric reports zero window insets (CLAUDE.md), so the bar's place beside a real navigation bar is not covered by any of these runs.
- #264's known-bugs rows were not re-read for this report (RECORD -1352 says the planner had not re-read them either).

## Correction, 2026-10-19

ListTab's outer Column is at AvailabilityResultsUi.kt:88-92, not :165, which is ResultsSection's. The "gap in the List tests" section above names `:165` and quotes `Column(modifier = modifier.fillMaxWidth())`. Both belong to `ResultsSection`, which `ListTab` calls at `:105`. `ListTab`'s own Column is `modifier.fillMaxWidth().padding(horizontal = Spacing.lg)`. Hazel found this in the verify-first for dispatch 2026-10-19-06, which adds the tag there. The line above is left as written.
