# Leaving-journal flake diagnosis (dispatch 2026-09-28-349), INTERIM: steps 1 to 3 only

**Status: INTERIM.** Steps 1 to 3 (collect, read, shared cause) are done without Gradle. **Steps 4 to 6
(reproduce, instrument, fix) have not been run:** no Gradle slot was granted, and the pre-run checks
failed (see "Blocked"). Nothing below is a reproduced result. No README row is added yet; it goes in with the final report.

- **Coder model:** `claude-sonnet-5-5` (the session's configured model; not independently read back).
- **Base:** `origin/journal-redesign` at `d94d4ca177c5203545a9c5775f94f0b128131d61`, branch `leaving-journal-flake`.
- **Pushed to:** `leaving-journal-flake` only. No change to `journal-redesign` code or tests; no CI re-run; `gh` read-only.
- **Data:** `docs/audits/data/2026-10-01-leaving-journal-flake/` (`logs/*.gz` job logs, `xml/*.xml` JUnit for the 3 newest failures).

## 1. CI table and its sample

**Sample.** The class was added by `fa4735e3` (2026-09-28). Of the 133 finished runs on `journal-redesign`
(-296's 120 plus 13 since; union of `ci-runs-journal-redesign.tsv` and `gh run list`, deduplicated by run id),
**99 had that commit as an ancestor of their head and reached the unit tests** (34 success, 65 failure; the
SDK-setup failure `36627241206` is excluded because it ran no tests). Success runs were not downloaded:
success means every test passed or was allowlisted. Not in the sample: 359+ cancelled runs, and run
`36799559211` (`d94d4ca1`, still running when counted). **All 13 failure-job logs since -296's cutoff were read.**
Six of them came back empty from `gh run view --log` and were re-fetched through
`gh api .../jobs/<id>/logs` (all 13 now contain `tests completed`). The JUnit artifact was read for the 3
newest Leaving failures; the 3 older ones are from -296's messages table.

**6 failures in 99 runs = 6.1%, Wilson 95% 2.8% to 12.6%** (computed here). -296's "3 in 119" is not comparable: its denominator includes 24 heads before the class existed and its window ends 2026-09-30 18:56.

| Run | Head | Test (F3) | Message (JUnit) | Where the exception is thrown |
|---|---|---|---|---|
| 36487664436 (09-28) | 5d6ffbb9 | a committed find open in its editor with a change… | `Detected multithreaded access to SnapshotStateObserver: previousThreadId=30, currentThread={id=26, name=SDK 36 Main Thread @kotlinx.coroutines.test runner…` | :497 (`typeFindIdentification`) |
| 36564259128 (09-29) | 6bf4e2ed | same | `performMeasureAndLayout called during measure layout` | :995 |
| 36614885083 (09-29) | 47abc89d | same | `performMeasureAndLayout called during measure layout` | InlineClassHelper.kt:36 |
| 36763396348 (09-30) | d610d90f | Log a find on Maps over an unchanged kept re-edit… | `performMeasureAndLayout called during measure layout` | :1094 via `openFindEditor` :412 |
| 36785322394 (09-30) | a0a0c333 | a committed find open in its editor with a change… | `performMeasureAndLayout called during measure layout` | InlineClassHelper.kt:36 |
| 36798317295 (10-01) | 60d626b9 | Log a find on Maps over a changed kept find… | `performMeasureAndLayout called during measure layout` | :497 |

**So the 6 are one message family, not six.** 5 of 6 are `performMeasureAndLayout called during measure layout`; the first is a sibling (multithreaded access to Compose's `SnapshotStateObserver`). All six are F3 tests, which open the find editor.

## 2. What was read (observed)

- `typeFindIdentification` (`LeavingTheJournalFixesTest.kt:495-500`): `onNode(...).performScrollTo().performTextReplacement(text)` then `waitForIdle()`. Line 497 is the `performTextReplacement`.
- The exception at :497 is **not** thrown by the node lookup or the text action. In `36798317295` the stack is: test :497 → `performTextReplacement` → `getNodeAndFocus` → `getAllSemanticsNodes` → `AndroidComposeUiTestEnvironment.waitForIdle` → `RobolectricIdlingStrategy.runUntilIdle` → `Espresso.onIdle` → Robolectric `ShadowPausedLooper.idle` → `Choreographer.doFrame` → `ViewRootImpl.performTraversals` → `AndroidComposeView.onLayout` → `MeasureAndLayoutDelegate.measureAndLayout` (`MeasureAndLayoutDelegate.kt:914`, throws). I.e. a UI frame ran inside the test's own idle wait, and found a measure-and-layout already in progress.
- In `36785322394` the thrower is on **another thread**: the stack bottoms out in `kotlinx.coroutines.scheduling.CoroutineScheduler$Worker` (a `Dispatchers.IO`/`Default` worker), through `DispatchedCoroutine.afterResume` → the Compose test `ApplyingContinuationInterceptor.resumeWith` → `Snapshot.sendApplyNotifications` → `Recomposer` → `TestMonotonicFrameClock.performFrame` → `measureAndLayoutForTest` → same throw.
- The F3 tests' setup attaches a real album photo to the committed find (`LeavingTheJournalFixesTest.kt:219-222`; the file holds 4 junk bytes, `:216`). The find editor shows it through `KeptPhotoOrUnavailable` → `DecodedPhoto` (`CartographyEntryEditScreen.kt:450`).
- `DecodedPhoto` (`DecodedPhoto.kt:63-80`): `LaunchedEffect(relativePath) { bitmap = withContext(Dispatchers.IO) { decodeFile… } }`. It is the only `withContext(Dispatchers.IO)` in a composable on the F3 screen path (grep of `app/src/main` for `Dispatchers.IO|Default`: the other composable hits are `TrackExportPanel`, `CrashLogPanel`, `PhotoViewerDialog`, none opened by F3).
- **-317 did not introduce it.** `851e28fd` changed `DecodedPhoto` to one `Image` with a switching painter. The same `LaunchedEffect` + `withContext(Dispatchers.IO)` + state write existed before, and 3 of the 6 failures (09-28, 09-29, 09-29) predate the 09-30 merge. Whether the editor shows a photo: yes (above). Failures since -317 landed: 2 of 4 runs with the class on the merged head (`60d626b9`) region; too few to say the rate changed (not established).

## 3. What line 497 needs to be true, and what could make it false

Line 497 needs the main thread to be the only thread running Compose frames while the test idles. It is false when a Compose frame
is driven from a non-main thread at the same time as the main thread's own frame. **Inferred, not observed on the failing runs:** the `withContext(Dispatchers.IO)` in `DecodedPhoto` returns on an IO worker; the Compose test environment's interceptor resumes the `LaunchedEffect` **inline on that worker** (`DispatchedCoroutine.afterResume`, observed in the `36785322394` stack), the state write and `sendApplyNotifications` then run there, and the Recomposer's test frame clock performs `measureAndLayoutForTest` on the worker while the main thread is in a Choreographer frame (or the reverse). `measureAndLayout`'s in-progress flag is a plain field, so whichever thread arrives second throws, which also explains why the *same* race shows as `SnapshotStateObserver` multithreaded access in `36487664436`. Production is not shown to be affected: `AndroidUiDispatcher` posts back to the UI thread (-347's inference; not observed on a device).

## 4. Shared cause with the others

- **-348's wrong-thread failure (`DecodedPhotoTest`): same mechanism, by -347's own account** (RECORD.md, -342 "Open": "the harness resumed the effect inline on the IO worker, and the recompose-and-apply ran there too"). The `36785322394` stack above is the same shape. *Evidence level: matches the description in the record and the observed stack; not run by me.*
- **The same run `36785322394` also failed `DiagnosticsSyntheticForecastSwitchTest` with `CalledFromWrongThreadException` (ViewRootImpl.java:11357) and `ArrayIndexOutOfBoundsException` at `SlotTable.kt:4483`**, both consistent with a second thread touching Compose state. (Observed in the log; the test's own IO hop was not read.)
- **`performMeasureAndLayout called during measure layout` is not confined to this class:** `WideJournalTest` (run 36652635172) and `JournalEntriesOnMapFollowUpsTest` (36700604508) have the same message in -296's table, and RECORD.md has the same signature locally (lines 5051, 5677, 5738, including a -126 hang in this class's F3 at `openFindEditor`).
- **-296/-317 album flake: different mechanism, same async source.** The album failures were lost gestures from the placeholder/Image node swap when `DecodedPhoto`'s decode landed (fixed by -317). The Leaving failures are an exception from concurrent frames, not a lost gesture. **They share `DecodedPhoto`'s IO hop as a trigger; they do not share the failure.**
- **-297 `DrawerBackOverJournalTest` stall:** **not established.** It is an album-view test with the same IO hop, but its symptom (a stall at `touchTools`) was not tied to this exception.

## Blocked: why steps 4 to 6 did not run

1. **Disk:** `/` has **1606 MB free** at the last check; the dispatch requires 2048 MB before every run.
2. **A Gradle process is running** (PID 1534293, a `GradleDaemon`/wrapper Java process; mine started none). Rule: no run while one is live; never `./gradlew --stop`, never kill another's process.
3. **No planner session to ask:** `ListAgents` shows no session named "Planner" (peers: `318`, `forager-78`, a "decoded-photo-thread" coder, and `bridge-cse-…` sessions). I did not message a guess. **No "go" has been given; no Gradle run has been started by this session.**

## Plan for steps 4 to 6 once a slot is granted (sizing from the observed rate)

Observed rate p ≈ 6/99 = 6.1% of **full-suite CI runs**. The per-run rate for the class alone, on this machine, is unknown. CLAUDE.md's sizing rule is "n ≈ 9/p" as a heuristic (the project's own pitfall list calls that heuristic crude): for 2 CPUs at p = 6.1%, ~148 runs for a rough 9 expected events; an exact binomial for 80% power to see ≥1 event is n = ln(0.2)/ln(1−0.061) ≈ **26** runs. Which of these to use is the planner's to size once the real per-run rate under pinning is measured by a 10-run pilot; **not decided here**. Each run: compile-error check on the build log, fresh-timestamp check on the XML, copy per-iteration results, `taskset -c 0,1` then `-c 0`, `--no-daemon`.
Two failed hypotheses means instrumentation in a scratch copy of the test (thread name at each state write / frame); a production hook is a stop-and-ask.
Candidate fix to try as a throwaway commit (**not tried**): none selected yet; options are in the final report after instrumentation says which thread writes.

## The four disclosures (interim)

**Confirmed vs inferred.** *Confirmed (read from logs/XML):* the 6 failures, their messages and stacks, the sample counts, `DecodedPhoto`'s IO hop, the F3 setup's attached photo. *Inferred:* that the inline-resume-on-IO-worker race is the cause of these six (the throwing thread is observed on a worker in one run, on main in others; the **other** thread was never observed).
**Could not determine.** Per-run rate of this failure on this machine; whether anything other than `DecodedPhoto` hops threads on the F3 path; whether the pre-`fa4735e3` runs could have failed (class absent); the 36799559211 run's result.
**Premises that were wrong.** The dispatch says "an `IllegalArgumentException at :497`" is the cause to find; the exception is thrown inside the test's `waitForIdle` (the line is only the caller). The dispatch's "3 in 119" mixes heads without the class; the like-for-like sample is 6 in 99. The dispatch names the planner as "Planner"; no such session exists.
**Decided beyond scope.** None. `git branch --unset-upstream` on the new branch (it had inherited `journal-redesign` as upstream) so a bare push cannot reach `journal-redesign`.
