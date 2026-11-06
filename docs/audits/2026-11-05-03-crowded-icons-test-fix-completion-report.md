# Dispatch 2026-11-05-03, completion report: the crowded-icons restart test waits for every pick's write before the restart (Hazel Cairnwright, Phase 2)

Built from records-after-173 at 8331fa235:
- the dispatch, `prompts/preserved/2026-11-05-03-crowded-icons-flake.md`;
- the Phase 1 report, `prompts/preserved/2026-11-05-03-phase1-report.md` (its section 5 is the plan);
- RECORD -2133 (intent; the owner's "Run the random test failure") and -2139 (the planner's go for Phase 2).

Base: pre-main ee0651fe0, checked against the remote at the start and again before this report. It did not move. **Test-only:** no app code, no CI change, nothing outside `app/src/test/` and this file. Marks: **[read]** is source read at a file and line; **[observed]** is CI output I read; **[inferred]** is reasoning.

## Summary

- **The fix.** Both restart cases now wait until all three picks' writes have returned to the ViewModel, each successfully, and the store reads List. Only then do they restart (`awaitEveryPickStored`). The old wait, "List stored", was already true after the first pick, which is List as well as the last.
- **Failing first.** A new case, over a test-only "late store", fails on every run over the old wait. The message is the one CI showed intermittently: "the file holds it under map.crowded_icons expected:<[list]> but was:<[zoom_then_fan]>". It passes over the fix.
- **Revert check.** The old wait was put back by one line, on a throwaway branch. The late-store case failed with that message at :204, with no compile errors.
- **Repetition.** Three dispatched runs over the fix gave 0 failures in 2,400 executions of the class's cases, the number stated before any result was read.
  - The restart case passed 600 of 600, and the late-store case 600 of 600.
  - Half of each is counted from JUnit XML. The other half is inferred from the full-suite job's totals, since that job uploads no XML.
- **Every existing assertion stays,** word for word. No `@Ignore`, no retry, no widened tolerance, no removed assertion.

## What landed

On `crowded-icons-test-fix` (PR #492 into pre-main), pushed. The tree is clean.

| commit | what it does |
|---|---|
| e9f904332 | Step 1, failing first. `LateCrowdedIconsStore` (new test-only file `CrowdedIconsTestStores.kt`); the new case; the existing case's body moved verbatim into `picksSurviveARestart`, shared by both. Today's wait is unchanged. |
| f773607a1 | Step 2, the fix. `CountingCrowdedIconsStore` (same file); `awaitEveryPickStored` replaces the "List stored" wait in the shared body. The late store hands a released write's result back on IO. |
| the commit adding this file | This report. Docs only. |

## Verification before building (premises re-checked)

- **The test, at ee0651fe0** [read]: 199 lines. The case is at :151-175, the wait at :167, the release at :168, and the asserts at :164-165, :170, :173 and :174, as the handoff states.
- **A write returns only once it is on disk** [read, DataStore 1.2.1 sources from Google's Maven, in scratch]. This is what the fix rests on.
  - `updateData` returns `ack.await()` (DataStoreImpl.kt:167-186).
  - The ack completes in `handleUpdate` (:244-245), after `transformAndWrite` → `writeData` → `writeScope` (:345-380).
  - `writeScope` writes the `.tmp` file and renames it into place (FileStorage.kt:105-128).
  - The actor runs on the store's own scope (:200-202) and consumes one message at a time from a FIFO channel (SimpleActor.kt:45, 78-117).
  - So three successful returns mean all three writes were renamed into place, in tap order.
- **A write's transform runs in the caller's context** [read, DataStoreImpl.kt:351]. For the ViewModel's writes that is the main thread.
- **Reachability** [read]. These are the only callers the late store can be released by.
  - The ViewModel reads the crowded-icons store once, from `init` (AvailabilityViewModel.kt:393 → AvailabilitySettingsPrefs.kt:56, :206-216).
  - It writes it only from `onCrowdedIconsModeChanged` (AvailabilitySettingsPrefs.kt:240-248).
- **No premise was wrong.** One plan detail was changed; it is under "Decided beyond the dispatch's letter".

## What was built

**The shared body.** The existing case's body moved verbatim into `picksSurviveARestart(storeOver)`. The only changes:
- the ViewModel's crowded-icons store is the one the case builds over the real repository;
- the wait reads that store.

The existing case passes `{ it }`, so in step 1 it ran exactly as before: real repository, today's wait.

**`LateCrowdedIconsStore`** (test-only):
- It holds every write.
- Each read through it first reads the real store, then lets the oldest held write through and runs it to completion on IO before returning.
- So a read returns the value from before that write, and the write is on disk when the read returns.
- Over the old wait this gives the interleaving CI showed, on every run:
  - the wait reads the first pick's List;
  - the second pick's write lands just after that read;
  - the third is still held when the restart cancels the store.

**`CountingCrowdedIconsStore`** (test-only) passes every call through and keeps what each write returned.

**`awaitEveryPickStored`** waits until three writes have returned, each successfully, and the store then reads List.
- The returned writes are counted before the store is read, so the read is never older than the last of them.
- The store is read on every check, since the late store releases a write only on a read.
- A failed write never satisfies the wait: it times out after 5 s, and the ViewModel's "Couldn't persist the crowded-icons preference." is in `logged`, which the timeout message prints.

**Not touched:** any app code (the ViewModel, the repository, DataStore setup); ci.yml; the class's other two cases; `awaitIdle`, `release`, `rawStored` and the sibling settings tests.

**Line numbers now** (233 lines): the existing asserts are at :198-199 (were :164-165), :204 (was :170), :207 (was :173) and :208 (was :174). The text of each is unchanged. `CrowdedIconsTestStores.kt` is 75 lines.

## Evidence

All counts are from the JUnit XML of the named run, unless marked as a log.

**Step 1, failing first.** Run 42103982871 (pull_request, e9f904332).
- **Prediction, stated before the run:** the late-store case fails at :186 with list/zoom_then_fan, and the other three pass.
- **Build log** [observed]: 0 Kotlin errors.
- **testDebugUnitTest:** 959 files, 6,621 tests, **1 failure**, 24 skipped, matching Gradle's "6621 tests completed, 1 failed, 24 skipped".
- **The class:** 4 testcases. The late-store case failed with "the file holds it under map.crowded_icons expected:<[list]> but was:<[zoom_then_fan]>" at `picksSurviveARestart(AvailabilityScreenCrowdedIconsSettingTest.kt:186)`. The other three passed, including the existing case.
- **Street-removable job** (full suite, log): the same case failed at :186; "959 suites: tests 6615, failures 1".

**Step 2, the fix.** Run 42107288627 (pull_request, f773607a1, tested as GitHub's merge commit dbc7ee99b onto pre-main).
- **Prediction, stated before the run:** all green.
- **Result:** every job succeeded.
  - testDebugUnitTest: 959 files, 6,621 tests, 0 failures, 24 skipped.
  - testDebugUnitTestOlderSdks: 6 files, 24 tests, 0 failures.
  - The class: 4 of 4 passed.
  - Street job (log): "959 suites: tests 6615, failures 0, skipped 24".
- **Against pre-main's own run** 42094523206 at ee0651fe0, compared by test identity, not by count:
  - pre-main has 6,644 testcases (6,620 + 24) and the branch 6,645;
  - the only identity on the branch and not on pre-main is the new case;
  - none is missing.

**Step 3, revert check.** Run 42107450734 (workflow_dispatch, `crowded-icons-test-fix-r1` at c1a98670a, filtered to the class). Done under CLAUDE.md's runner rules.
- **The edit:** one line, :201. `awaitEveryPickStored(store, picks.size)` went back to today's `awaitIdle("List stored") { … == CrowdedIconsMode.LIST }`, read through the store the ViewModel holds. It was made in a separate scratch worktree, so the fix's worktree was never edited.
- **Saved copy:** the forward file was saved first, sha256 6e04399c….
- **Compile log, read first** [observed]: 0 Kotlin errors. `compileDebugUnitTestKotlin` ran in that job, and the job checked out c1a98670a.
- **Result:** 4 testcases, **1 failure**, the late-store case: "the file holds it under map.crowded_icons expected:<[list]> but was:<[zoom_then_fan]>" at :204.
  - :204 is the line in the fix's layout. Step 1's failure was at :186, so this result cannot be step 1's left over.
  - The existing case passed.
  - Street job (log): the same case failed at :204; "959 suites: tests 6615, failures 1".
- **Restore:** the r1 file was restored from the saved copy, not from git. Afterwards the saved copy, the restored file, the fix worktree's file and origin's `crowded-icons-test-fix` all hashed 6e04399c…, and the fix worktree was clean at f773607a1, the same commit as origin.

**Step 4, repetition over the fix.**
- **Setup:** Phase 1's 100-copy setup over f773607a1, at 47f3eba04. The class was made `open`, and a new file added 99 empty subclasses, `…SettingTestRep01` to `Rep99`. No case, assertion or wait was changed.
- **Branches:** `crowded-icons-test-fix-rep1`, `-rep2` and `-rep3`, all at 47f3eba04, one branch per run as in Phase 1. Which runner machine each job got was not read.
- **Stated before any result was read:**
  - 3 workflow_dispatch runs, one attempt each, no re-runs.
  - Filter: `com.zynergylabs.forager.app.ui.availability.AvailabilityScreenCrowdedIconsSettingTest*`.
  - Build job: 100 classes × 4 cases = 400 testcases per run.
  - Street job (full suite; it ignores the filter): 959 + 99 = 1,058 suites and 6,615 + 396 = 7,011 tests per run.
  - So each case runs 300 times filtered and 300 times in a full suite, 2,400 executions in all. Expected: 0 failures.

| run | branch | build job (filtered, JUnit XML) | street job (full suite, log) |
|---|---|---|---|
| 42110713139 | rep1 | 100 classes, 400 tests, 0 failures; each case 100/100 | 1,058 suites, 7,011 tests, 0 failures |
| 42110717427 | rep2 | 100 classes, 400 tests, 0 failures; each case 100/100 | 1,058 suites, 7,011 tests, 0 failures |
| 42110721333 | rep3 | 100 classes, 400 tests, 0 failures; each case 100/100 | 1,058 suites, 7,011 tests, 0 failures |

- **What the counts ran on.**
  - Every build log has 0 Kotlin errors, checked out 47f3eba04, and echoed the filter pattern. Every run succeeded on every job it runs; the debug-sources job is skipped on a branch run, by design.
  - The build-job counts are read per testcase from the XML: 1,200 testcases, matching the plan.
  - That the class's cases ran 100 times each in the street jobs is [inferred], from the totals matching the plan exactly. That job uploads no XML.
- **Totals.** The restart case: 600 executions, 0 failures. The late-store case: 600, 0. All four cases together: 2,400, 0.
- **Why the zero is evidence.** The repetition alone is weak: Phase 1's run2 gave 0 of 100 with the bug in place. Its weight comes with steps 1 and 3, where the late-store case failed on the old wait every time it ran (4 of 4 executions: both jobs of runs 42103982871 and 42107450734) and passed over the fix 600 of 600 here, plus 2 of 2 in run 42107288627.

**Last real commit.** f773607a1 is the last commit that changes code. Its full CI, run 42107288627 (step 2 above), is green on every job, with the counts given there. The commit adding this report is docs only and gets its own PR run. Its result is posted on PR #492 and in the hand-back, since a report cannot carry the result of the commit that adds it.

## Conventions

- **Checked:** the neighbouring tests' restart pattern (release the scope, then reopen; `AvailabilityScreenBasemapPersistenceTest`, as the class's KDoc names it). It is kept unchanged. Test-only fakes in this package sit in their own files (`MapLayersUiFixtures.kt`), and the two wrappers follow that.
- **Prior art for a user-facing control:** none applies. Nothing a user sees changed.

## Not tested, said plainly

- **Whether every past green of the existing case was a true pass.** The Phase 1 report's open question, whether some passes had the cancel drop both later writes, is not answered for past runs. The fix makes it moot from here on: the restart now waits for all three returns.
- **The late store models one interleaving.** It does not model a write that fails, or a store that reorders writes. A failed write is covered by the wait's success check. Reordering is ruled out by DataStore's source [read], not by a test.
- **The two failure windows** Phase 1 named (the cache, or the old file during the second write's fsync) were not instrumented. The late store reproduces the outcome deterministically, not the CI machine's timing.
- **No device was used,** and none is needed: nothing a user meets changed.

## Device or environment items

None.

## Decided beyond the dispatch's letter

For the planner to accept or reject:

1. **The late store holds every write, the first too.** Section 5 let the first write go straight to the store.
   - That write's transform runs in the ViewModel's main-thread context (DataStoreImpl.kt:351).
   - If it were still queued when the first poll let the second through, the poll's `runBlocking` would block the main thread behind it, and the test would hang.
   - Avoiding that would mean skipping the release, which brings timing back.
   - Holding every write gives exactly one release per read, independent of timing.
   - The interleaving it forces is the one section 5 describes.
2. **"Today's wait" in the late-store case reads through the store the ViewModel holds,** not the bare repository. The late store releases writes only on reads made through it. Read past it, the late-store case would time out at "List stored" rather than reach :170's assertion. For the existing case this is the real repository, so its wait was unchanged in step 1.
3. **The late store's hand-back changed between steps 1 and 2.**
   - Step 1 completed a released write's result on the main thread inside the test's read. Step 2 completes it on IO, as DataStore's actor does (DataStoreImpl.kt:200-202, 244-245).
   - Step 1's failure does not depend on this [inferred: under the old wait the outcome is set by which writes were let through before the restart, not by where the writer resumes].
   - The revert check (step 3) ran the old wait over step 2's version and failed the same way [observed].
4. **The existing case's body moved into a shared function.** The case name and every assertion's text are unchanged. Their line numbers moved (listed above).
5. **Clean-up.**
   - **Deleted from origin and locally:** `crowded-icons-test-fix-rep1`, `-rep2` and `-rep3` (all at 47f3eba04), as the dispatch asks.
   - **Kept on origin:** `crowded-icons-test-fix-r1` (c1a98670a), the revert check. The dispatch doesn't ask for it to go, and it records the exact one-line revert. The planner may delete it.
   - **Removed:** the scratch worktrees for r1 and the repetition. The scratch folder is deleted at the end.
   - **The CI runs stay on GitHub:** 42103982871, 42107288627, 42107450734, 42110713139, 42110717427 and 42110721333.

## Stops

None applied. No app code was needed. Nothing was weakened. No premise was wrong. There was no second attempt at one symptom: the first build went green.
