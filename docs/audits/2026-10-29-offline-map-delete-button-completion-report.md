# Completion report: a visible Delete button on offline-map rows (dispatch 2026-10-29-10)

Hazel Cairnwright, 2026-10-30 UTC. Dispatch `prompts/preserved/2026-10-29-10-offline-map-delete-button.md` with Amendment 1 (read at `origin/records-after-173`), RECORD -1817, -1818, -1820. PR #443 into pre-main, **not merged**. This session picked the task up from an earlier coder session that was stopped mid-task; the build commits and the scratch runs are that session's, and the reading of them below is this session's.

**Status (2026-10-30 10:41 UTC): CI green on `14ce937fb`.** The first full run was red on one pre-existing test that pinned the old behaviour (see "CI on the PR head" and "The stop"). The owner chose option 1 (Amendment 2, RECORD -1823), and the section "Amendment 2" at the end records the change and its checks. The earlier sections are kept as written.

## Base

- Dispatch base: pre-main `21d846b9e`. `git ls-remote origin refs/heads/pre-main` at 2026-10-30 05:20 UTC: still `21d846b9e`. Read.
- Branch `offline-map-delete-button`, worktree `~/.cache/forager-wt/offline-map-delete-button`, cut from `21d846b9e`.

## Commits

| Commit | What |
|---|---|
| `dfba13e79` | Tests over an unwired stub: `OfflineRegionDeleteButton.kt` (the button composable, composed by no screen), `OfflineMapDeleteButtonTest.kt` (5 tests), `OfflineMapDeleteButtonFixtures.kt`. |
| `9ca77f70c` | Wiring: `OfflineRegionsSection` (Records > Offline maps, `OfflineRegionsList.kt:149`) and `RecordsLogbookList` (Records > All, `RecordsLogbookList.kt:233`) wrap their `OfflineRegionRow` in `OfflineRegionRowWithDelete`, passing the same `onDeleteOfflineRegion(id)` the row's `TwoStageSwipeRow` gets. |
| `38c5f1075` | Amendment 1 wording: `JournalPendingDeleteTest.kt:573` title renamed to `the region row has no text Delete control at rest` (one-line diff, assertion byte-identical); `TwoStageSwipe.kt:161-164` comment says "no text Edit or Delete control". |
| (this report) | Docs only. |

File sizes (read, `wc -l`): new files 59, 154, 239 lines, all under 400. Touched files: `OfflineRegionsList.kt` 289 -> 293, `RecordsLogbookList.kt` 346 -> 350, `TwoStageSwipe.kt` 358 -> 360. `JournalPendingDeleteTest.kt` is 1679 lines, over 800; its change is a rename of one line with no lines added, so I read the modular rule's "split before adding" as not triggered. Flagged for the planner to accept or reject.

## Failing first

Run 41004916662 on `omdb-failing-first` (`dfba13e79`, stub, no wiring), filter `*OfflineMapDeleteButtonTest*;*JournalPendingDeleteTest*`.
- Build log: `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran; zero Kotlin `e:` lines; `testDebugUnitTest` ran. Read.
- JUnit XML (from the run's `unit-test-report` artifact): OfflineMapDeleteButtonTest 5 tests, 5 failed; JournalPendingDeleteTest 52 tests, 0 failed. 57 total, matching the log's "57 tests completed, 5 failed".
- All 5 failures: `performScrollTo() failed ... could not find any node that satisfies: (TestTag = 'offline-region-delete-7')` (or `-8`). This is the predicted reason: the button exists as code but no screen composes it.

## Revert checks

Each scratch branch is `9ca77f70c` plus one one-line edit (read with `git diff FETCH_HEAD~1 FETCH_HEAD`). Same filter as above. For every run I read the build log for compile errors **before** the results: in all four, `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran, there are zero Kotlin `e:` lines, and `testDebugUnitTest` ran. CI checks out fresh, so the XML cannot be left over from an earlier run; each artifact's suite timestamp falls inside its own run. Counts below are from the JUnit XML and match each log's "57 tests completed" line.

| Run | Branch / edit | Predicted | Failed (of 5 new; JournalPendingDeleteTest 52/52 pass in all) | Message | Verdict |
|---|---|---|---|---|---|
| 41004918922 | `omdb-revert-r1-button-click`: `IconButton(onClick = {})` in `OfflineRegionDeleteButton.kt:46` | the four touch tests fail at the snackbar | the 4 touch tests (both screens, both rows); the label test passes | `'Offline map deleted · used in 2 journal entries'` / `'Offline map deleted'` `is not displayed!` | **Confirmed.** The button is found and touched (no missing-tag message) and only the snackbar is missing, which is exactly what a dead click produces. The label test passing is right: the label is untouched. |
| 41004921463 | `omdb-revert-r2-panel-wiring`: panel's `OfflineRegionRowWithDelete(...)` replaced by `run`, `OfflineRegionsList.kt:149` | panel tests plus label test fail on the missing tag | 2 Offline-maps-chip touch tests + label test; both All tests pass | `could not find any node ... 'offline-region-delete-8'` / `-7` | **Confirmed.** Only the panel lost its button; the label test fails on its first (panel) pass, before it reaches All. |
| 41004924005 | `omdb-revert-r3-all-wiring`: All's `OfflineRegionRowWithDelete(...)` replaced by `run`, `RecordsLogbookList.kt:233` | All tests plus label test fail | 2 All touch tests + label test; both panel tests pass | `could not find any node ... 'offline-region-delete-8'` / `-7` | **Confirmed.** Only All lost its button. The label test's panel pass succeeds and it fails on the All pass, which is the only place this edit can make it fail. |
| 41004926401 | `omdb-revert-r4-label`: `offlineRegionDeleteLabel` returns `"Delete"`, `OfflineRegionDeleteButton.kt:56` | only the label test fails | label test only | `Failed to assert ... (ContentDescription = 'Delete Ridge') ... Tag: 'offline-region-delete-7' ContentDescription = '[Delete]'` | **Confirmed.** The node shows the reverted label verbatim. |

Every failure in every run names a tag, a snackbar or a label that its own edit touches; none belongs to another edit. Forward change still present: the revert edits live only on their scratch branches; `origin/offline-map-delete-button` is `38c5f1075` throughout (`git ls-remote`), and its diff from `21d846b9e` still contains the button and both wirings (read).

What the revert checks do not cover: they ran on a two-class filter, so they say nothing about the rest of the suite (that is what the PR run below found). The swipe's own tests were not reverted (the swipe is unchanged by this dispatch).

## CI on the PR head

Run 41006039562 on `38c5f1075` (pull_request, full suite). Release compile, Android lint, Tile Worker, route-data cutter and the debug-only-sources guard: **success**. "Build, test, publish APK": **failure**.
- Build log: zero Kotlin `e:` lines; tests ran.
- JUnit XML: `testDebugUnitTest` 5886 tests, 1 failed, 24 skipped; `testDebugUnitTestOlderSdks` 24 tests, 0 failed. 5910 in all, 808 result files.
- The one failure: `AvailabilityScreenSettingsPanelTest` (`app/src/testDebug/.../availability/AvailabilityScreenSettingsPanelTest.kt:686`), `a downloaded region's row has no Delete control at rest, and carries the swipe and its Delete action`, at `:699-700`:

```
Did not expect any node but found '1' node that satisfies: ((ContentDescription contains 'Delete' (ignoreCase: true)) && (OnClick is defined))
Node #164406 ... Tag: 'offline-region-delete-5'  ContentDescription = '[Delete Molalla Ridge]'  Role = 'Button'  MergeDescendants = 'true'
```

Root cause (read from the message and the test): the node is this dispatch's trash button on the test's `L5_REGION` (id 5, "Molalla Ridge", `:922-924`). The test (J4b L5) pins that a region row at rest carries **no** clickable node described as a delete. The owner's ruling for this dispatch ("Visible Delete button": "Each row in Offline maps shows a trash button") puts exactly such a node there. So the failure is not a flake and not harness-only: it is a guard written for the old design meeting the new one. The other assertions in that test (no "Delete" text at `:698`, the swipe tag and its "Delete" accessibility action at `:691-697`) still pass, since only `:699-700` failed.

Why it was missed: it is the testDebug sibling of the `JournalPendingDeleteTest` test Amendment 1 renamed, and it sits in `src/testDebug`, not `src/test`; the earlier session's search for affected tests did not reach it, and the two-class filter on the scratch runs could not have. The earlier full run on `9ca77f70c` (41004971457) was cancelled, so this is the first full-suite result for the wiring.

## The stop

Making CI green needs `:699-700` changed. Amendment 1's ruling for the sibling test was "Fix wording only", with the assertion byte-identical; that cannot work here, because this assertion is the one the new button contradicts. Changing an assertion is a stop for me (coder profile, "Stops"; CLAUDE.md, "Silencing a test is never in scope"). I have not touched the test. Options for the planner and owner:

1. **Replace the negative check with a precise one (my recommendation).** Keep the title's spirit as "no *text* Delete control at rest", keep `:698` and the swipe checks unchanged, and change `:699-700` to: exactly one clickable node described as a delete, and it is the trash button, `offline-region-delete-5`, labelled "Delete Molalla Ridge". This still fails if any other hidden delete control appears, and it also pins the new button on this screen.
2. Delete `:699-700` and rely on `OfflineMapDeleteButtonTest` for the button. Simpler, but the "no other delete control" guard goes.
3. Something about the design itself (for example a different button), which would be a new owner question.

## Calls made beyond the dispatch

- `TwoStageSwipe.kt` comment: besides the "no text Edit or Delete control" correction Amendment 1 asked for, the earlier session added one sentence naming the trash icon (`OfflineRegionRowWithDelete`). Comment only.
- `OfflineRegionRow`'s own doc comment (`OfflineRegionsList.kt:189-191`) gained a sentence saying callers put the trash button beside it. Comment only.
- `TwoStageSwipe.kt:162` now says a row at rest has "no text Edit or Delete control (what J4b L5's test pins)". J4b L5's test is the one that failed above, and it pins more than text (no clickable node described as a delete). The comment will need matching to whichever option is chosen.
- The report commit carries `[skip ci]`: it is docs only, and the PR head's code result is run 41006039562 above.

## Not verified

- Nothing has run on a phone. The S22 steps are in the PR body.
- The 80% chrome rule: the button is an `IconButton` with no fill of its own (`OfflineRegionDeleteButton.kt:46`), so it adds no surface over a map. Whether the Offline maps panel itself is drawn over a map was not checked in this session.
- The PR body's observation that a swiped-open row's trash icon slides left and a tap on it only closes the row: written by the earlier session; not checked by a test or by me.
- TalkBack's spoken output: the test checks the content description and the click action, not what TalkBack says.
- The rest of the suite beyond the one failure: green in 41006039562, but the fix for `:699-700` will need a fresh full run.

## Amendment 2 (2026-10-30): the J4b L5 guard, exactly one clickable delete

The owner: "Exactly one: the trash (Recommended)" (Amendment 2, `6ada2dbbd` on `origin/records-after-173`, RECORD -1823). The planner accepted the calls listed above.

Commit `14ce937fb`. The test lines' diff (`AvailabilityScreenSettingsPanelTest.kt`). The text check at `:700` and the swipe checks at `:693-699` are unchanged:

```diff
         composeRule.onAllNodesWithText("Delete", ignoreCase = true).assertCountEquals(0)
-        composeRule.onAllNodes(hasContentDescription("Delete", substring = true, ignoreCase = true) and hasClickAction())
-            .assertCountEquals(0)
+        // Dispatch 2026-10-29-10 Amendment 2 (RECORD -1823), the owner: "Exactly one: the trash (Recommended)".
+        val clickableDeletes = composeRule.onAllNodes(hasContentDescription("Delete", substring = true, ignoreCase = true) and hasClickAction())
+        clickableDeletes.assertCountEquals(1)
+        clickableDeletes[0]
+            .assert(hasTestTag(offlineRegionDeleteButtonTag(L5_REGION.id)))
+            .assert(hasContentDescription("Delete ${L5_REGION.name}"))
     }
```

Also in that commit:
- the `hasTestTag` import;
- the test's KDoc, which no longer says the row has no clickable delete;
- the `TwoStageSwipe.kt:161-164` comment, which now reads "J4b L5's test pins no "Delete" text and exactly one clickable delete, that trash button".

The label is matched as the literal `"Delete ${L5_REGION.name}"`, not through `offlineRegionDeleteLabel`, so this test pins the approved wording on its own.

**Revert check.** The scratch branch `omdb-revert-a2-second-delete` is commit `afb1d70d2`, which is `14ce937fb` plus one line. That line adds a second `IconButton` beside the trash in `OfflineRegionDeleteButton.kt`, tagged `revert-second-delete` with the description "Delete again". I made the edit in a separate worktree, so the PR branch's files were never touched; the edited file's original was also saved to scratch before editing.
- Run 41040950352, filtered to `*AvailabilityScreenSettingsPanelTest*`.
- Build log first: `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran, with zero Kotlin `e:` lines.
- JUnit XML: 26 tests, 1 failed, which matches the log's "26 tests completed, 1 failed".
- The failure is the L5 test: `Expected exactly '1' node but found '2' nodes`. It lists `offline-region-delete-5` "[Delete Molalla Ridge]" and `revert-second-delete` "[Delete again]".
- Prediction: only this test fails, at the new count. **Confirmed.** The second node in the message is this edit's own tag.
- Afterwards, the forward change is still on `origin/offline-map-delete-button` at `14ce937fb`: the new assertion is present and `revert-second-delete` is absent (read from origin).

**Full suite on the new head.** Run 41040930086 (pull_request on `14ce937fb`): **all jobs green**, including the debug-only-sources guard.
- Build log: zero Kotlin `e:` lines.
- JUnit XML: `testDebugUnitTest` has 802 files, 5886 tests, 0 failed and 24 skipped. `testDebugUnitTestOlderSdks` has 6 files, 24 tests and 0 failed. 5910 in all, the same total as run 41006039562.

**Calls beyond the amendment:**
- The test's title still reads "has no Delete control at rest". The amendment changed only lines 699-700, so I left the title. It is now true only for text Delete controls. Renaming it, as Amendment 1 did for the sibling, is the planner's call.
- `AvailabilityScreenSettingsPanelTest.kt` was 931 lines and is now 937. The modular rule says to split a file over 800 lines before adding to it, or to stop. I added the six lines without splitting, because the amendment names this exact edit. Flagged for the planner to accept or reject.

**Still not verified:** the phone steps, and the other items under "Not verified" above.
