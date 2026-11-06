# Sorrel Tally's review of PR #410: the older-SDK test classes in their own test JVM; NoBackBy no longer keeps unwired ViewModels

Sorrel Tally (reviewer, Fable), 2026-10-26, for Rowan Waymark. Read-only on code: nothing edited, merged or pushed but this file; no Gradle ran on the laptop and no device was used. Each claim is marked **read** (from source or a log), **computed** (by my own script from downloaded data) or **inferred**.

## Verdict

- **Blocking:** none.
- **Should-fix (two, both in `gradle/older-sdk-tests.gradle.kts`, neither a reason to hold the merge):**
  1. The nested-class message tells the reader to do something the check will not accept. `ownerClass` adds "an indented @Config with an older SDK sits in a nested class of X; list it by hand" and still returns the outer class, so the problem list is never empty for that file: listing the class by hand does not clear it, and the build fails until the file is restructured. It fails loudly, so nothing drifts, but the instruction cannot be followed. It also fires for an older-SDK `@Config` on a *method* of the outer class whenever any nested class (a private fake, say) sits above it, which is the common shape in this repo's tests. **Read**, `gradle/older-sdk-tests.gradle.kts`, `ownerClass`.
  2. One silent path remains: an older-SDK `@Config` on an abstract or open base class whose subclasses are the runnable tests. Robolectric reads a superclass's `@Config`; the scan attributes the annotation to the base class's name, the list would carry the base, `includeTestsMatching(base)` matches no runnable class, and the subclasses run in the main JVM with the check green. No such base exists today (**computed**: no `abstract`/`open class` in `app/src/test` has a `@Config` within the three lines above it), and `AvailabilityScreenLayoutTest` shows the pattern is used here, so this is a future gap, not a present one. Cheapest fix: have the scan report an older-SDK `@Config` whose owner line reads `abstract class` or `open class`, as it reports `minSdk`.
- **Notes:** the lint-crash attribution is inferred and confounded (section 10); the heap is no longer near its limit anywhere in the run, with the margin now about 270 MiB (section 7); CI's run of current pre-main `c3e132a54` was still in progress when this was written (section 2).
- **Mergeable: yes.** Every class in pre-main's run appears exactly once across the two tasks with the same test count and the same 24 skips; the summary fails on missing, failed or duplicated results; the heap limit is unchanged; the production path never touches `NoBackBy`; the leak test failed first and failed on the revert with its own message; the drift check failed on the plant with its own message.

## Mechanism prediction (profile step 2; scored in section 13)

Formed before downloading anything, written here afterward: (a) the two tasks' XML would partition pre-main's classes exactly, with the six older-SDK classes and 24 tests in the second task; (b) the live heap at the first caption class would fall by at least the 132.8 MiB the report attributes to the four SDKs' tables and jar directories, so to 357 MiB or below; (c) the getter on `NoBackBy.shown` would change no production behaviour because production never constructs the ViewModel without `backBy`.

## 1. Baseline (read)

- PR #410, `test-jvm-split-backby`, head `205f2e9cd`; commits `1a2ae9330`, `cf129a9d5`, `4d2709ee6`, `205f2e9cd`; merge base with pre-main `19c85f78e`, as the dispatch states. Pre-main is `c3e132a54`; the PR's files (`.github/workflows/ci.yml`, `app/build.gradle.kts`, `BackBy.kt`, the new test, the new script) are untouched on pre-main since the base, and a `git merge-tree` dry run shows no conflict.
- Dispatch `prompts/preserved/2026-10-26-08.md` and RECORD -1656 read on `records-after-173` (`4c049ad9e`, which also carries -1659 sending #410 to me). Heap report read on `caption-oom-diag-report`.
- The PR's CI run 40275281651 checked out `refs/pull/410/merge` = "Merge 205f2e9cd into 12a073783" (**read**, the job log). So its suite is the PR on pre-main as of 12a073783, not on the base 19c85f78e. This matters for the count below and explains the coder's 743 + 6 = 749 against a base of 748.

## 2. The recount (computed)

Downloaded the `unit-test-report` artifacts of three runs and parsed every `TEST-*.xml` with my own script (`/tmp/claude-1000/-home-zynergy-labs-Zynergy-Forager/dd088739-a5b1-4a34-be62-046602aea9d1/scratchpad/scripts/recount.py`, not committed).

| Run | Checkout | Task | Classes | Tests | Failures | Errors | Skipped |
|---|---|---|---:|---:|---:|---:|---:|
| 40275281651 (PR) | merge of 205f2e9cd into 12a073783 | testDebugUnitTest | 743 | 5,550 | 0 | 0 | 24 |
| | | testDebugUnitTestOlderSdks | 6 | 24 | 0 | 0 | 0 |
| 40273704348 (pre-main, latest green push when written) | 12a073783 | testDebugUnitTest | 748 | 5,572 | 0 | 0 | 24 |
| 40271808291 (the coder's baseline) | 19c85f78e | testDebugUnitTest | 748 | 5,569 | 0 | 0 | 24 |

- **Against pre-main 12a073783:** every one of its 748 classes appears in the PR run exactly once, in one task or the other, with the same `tests`, `failures`, `errors` and `skipped`, the same test-case names, and the same skipped-test names. No class appears in both tasks. The one class in the PR run and not in pre-main's is `TrackRecordingNoBackByLeakTest` (2 tests). 748 + 1 = 749; 5,572 + 2 = 5,574.
- **Against the base 19c85f78e:** the same, plus one per-class difference: `ReturnWatchDottedLineTest` has 5 tests at the base and 8 in the PR run. That is PR #406 (`dotted-start-standing`, merged into pre-main at 12a073783, +68/-2 lines in that file), present through the merge ref, not the PR's change. So the coder's "5,550 + 24 against 5,569" is the base figure; the exact comparison is to 12a073783.
- **The 24 skips** are the same 24 (classname, name) pairs in all three runs: 19 in `AvailabilityScreenMapIconStackTest`, 1 each in `AvailabilityScreenOfflineCacheTest`, `AvailabilityScreenWaypointFlowTest`, `GenerateFungiIndexDbAsset`, and 2 in `AvailabilityScreenTripPlanningFlowTest`. None is in the older-SDK task.
- **The older-SDK task's six classes and counts:** `AlertDeliveryOutcomeTest` 5, `AndroidAlertDeliveryTest` 6, `AndroidBackgroundRunCheckTest` 2, `BackByAlertDeliveryTest` 6, `PhotoViewerSaveRawToFolderTest` 1, `PhotoViewerSaveToFolderTest` 4; sum 24. These are the six and only six classes whose `@Config` names an SDK other than 36 (**computed**: `git grep` over `app/src/test` and `app/src/testDebug` at the PR head; the other 596 `sdk =` arguments are `[36]` or `[ROBOLECTRIC_SDK]`, and `ROBOLECTRIC_SDK` is `private const val ROBOLECTRIC_SDK = 36` at `AvailabilityScreenLayoutTest.kt:124`). The two `PhotoViewer*ToFolderTest` classes are the second top-level class in `PhotoViewerSaveRawTest.kt` (line 119) and `PhotoViewerSaveTest.kt` (line 238); their siblings `PhotoViewerSaveRawTest` and `PhotoViewerSaveTest` (sdk 36) ran in the main task, so the exact-name exclude did not take the siblings with them.
- **Pre-main has moved since the PR's merge ref:** `c3e132a54` (PR #407) adds `TrackRecordingServiceWayHomeNetworkFixTest` (`@Config(sdk = [36])`, line 53) and changes `WayHomeWatchKeptPointTest` (no `@Config`). Both would land in the main task on merge. Its CI run 40278145396 was in progress when this was written, so it is not in the table.

## 3. The CI summary fails when the older-SDK task gives nothing (read)

`.github/workflows/ci.yml`, "Summarize the test results", at the PR head:

- **No XML from either task on an unfiltered run:** the loop over `task_dirs` does `sys.exit("No JUnit XML in app/build/test-results/{}/ ...")` for the first task with no `TEST-*.xml` when `TESTS` is empty. `sys.exit` with a string exits 1. The step runs `if: always()`, so it reads even after the test step fails.
- **Task failed:** the test step itself fails (`set -euo pipefail`, Gradle non-zero), and the summary separately counts `failures`/`errors` from the XML and exits "Test results are not clean: ... failure(s) and ... error(s)". Gradle runs a `finalizedBy` task even when the finalized task fails, and the fail-first run shows it: `testDebugUnitTest` failed and `> Task :app:testDebugUnitTestOlderSdks` still ran, writing its one XML (**read**, run 40274639596's log and its artifact, which holds `TEST-...AndroidBackgroundRunCheckTest.xml` under `testDebugUnitTestOlderSdks/`).
- **Task skipped:** both tasks are named on the command line, `verifyOlderSdkTestClassList` has inputs but no outputs so it never reports UP-TO-DATE, and the build cache is off (`gradle.properties` has no `org.gradle.caching`; `setup-gradle` caches dependencies, not task outputs). A task that did not run writes no XML and falls into the first case.
- **A class in both tasks:** `seen_in` collects basenames; any name with two paths exits "Test classes ran in both test tasks: ...". The PR run printed "testDebugUnitTest: 743 result files / testDebugUnitTestOlderSdks: 6 result files / total across 749 suites 5574 0 0 24" (**read**, job log).
- **Upload:** both tasks' `reports/` and `test-results/` directories are listed with `if-no-files-found: error`; on a filtered run that leaves one task empty the other's files satisfy it.

## 4. A filtered run cannot pass with zero tests (read, and observed)

On a filtered run `-Pforager.filteredTestRun=true` sets `isFailOnNoMatchingTests = false` on both tasks, so the task the filter misses passes empty. The summary then has two independent stops: `if not results: sys.exit("No JUnit XML from either test task ...")` when neither wrote XML, and `if totals["tests"] == 0: problems.append("no tests ran")` when XML exists with no cases. Only the stale-allowlist check is relaxed on a filtered run; unallowed skips, failures and errors still fail it. Observed: the fail-first run's filter named one class in each task and both ran (section 3); the revert and drift-plant runs named one class in the main task only and the older task passed empty without failing the run before the test step's own failure did.

## 5. The drift check reads what it claims (read; reasoned through)

`verifyOlderSdkTestClassList` scans `src/test/java`, `src/test/kotlin`, `src/testDebug/java`, `src/testDebug/kotlin` (`file(...)` resolves against the applying project, `app`, which the drift plant proves: the planted class under `app/src/test` was found with the script in `gradle/`). It blanks comments first, keeping string, char and backtick literals, so an `@Config` quoted in a doc comment is not read; the PR run logged "Older-SDK test classes: 6, all listed (602 @Config read)", and `git grep` finds 603 `@Config(` in those sources of which exactly 1 sits on a comment line (**computed**). It also fails on any `robolectric.properties` that sets `sdk`/`minSdk`/`maxSdk`; none exists (**read**, `git ls-tree`).

- **An SDK via a constant:** `constants` is built per file from `const val NAME(: Int)? = <digits>`; a token in `sdk = [...]` that is neither an integer nor a key of that map returns null, which is reported as "cannot read the sdk in @Config(...)" and fails the build. So `[ROBOLECTRIC_SDK]` with the constant in the same file resolves (the PR run is green with it); a constant in another file, a `Build.VERSION_CODES.X`, or an expression all fail loudly. `4d2709ee6` made the digits-to-Int conversion `toIntOrNull`, after run 40273911138 crashed on `const val ... = 1789237318000L` (`NumberFormatException: "1789237318000"` at `scanFile`, **read**, that run's log); a Long constant is now simply not an SDK candidate.
- **`minSdk`/`maxSdk`:** `\b(minSdk|maxSdk)\s*=` in the arguments adds "minSdk/maxSdk is not read by this check; sort the class by hand" and fails the build. Since `\bsdk` is case-sensitive, `minSdk` does not also read as `sdk`. Loud.
- **A nested class:** an indented `@Config` is attributed to the enclosing top-level class (correct for method-level `@Config`, which is how `AlertDeliveryOutcomeTest` and `AndroidBackgroundRunCheckTest` carry theirs), and if any indented `class` line sits between the outer class and the annotation a problem is added. Loud, but see should-fix 1: the problem cannot be cleared by listing.
- **The gap:** an older-SDK `@Config` inherited from an abstract base (should-fix 2). Not present today.
- **Line-start `@Config` and the next top-level class:** `topLevelClasses` reads column-0 lines for `class Name` (excluding `::class`), and `ownerClass` takes the first such class at or after the annotation, so `@RunWith` lines between them are fine, and a file with several top-level classes attributes each annotation to its own class (the `PhotoViewer*` files, confirmed by the XML).
- **Stale entries:** a listed class whose scan finds no older SDK fails as "is in olderSdkTestClasses but sets no SDK other than 36", so a renamed or re-pinned class cannot leave a dead include that would make the older task fail or run nothing silently.

**The plant (read):** `scratch/older-sdk-drift-plant` `c35af004c` differs from the PR head by one new file, `PlantedOlderSdkTest.kt` with `@Config(sdk = [30])`, not listed. Run 40275309687 failed in `verifyOlderSdkTestClassList` in 11 s, before any test, with "com.zynergylabs.forager.app.alert.PlantedOlderSdkTest sets sdk [30] but is not in olderSdkTestClasses": a message only that edit can produce. The same check is green on the PR head (602 read, 6 listed).

## 6. The heap limit is unchanged (read, and observed)

Neither task sets `maxHeapSize`; the older task copies the main task's (`maxHeapSize = main.maxHeapSize`, null on both) along with `jvmArgs`, `jvmArgumentProviders`, `systemProperties`, classpath and launcher. `gradle.properties` sets only the build JVM's `-Xmx2048m`. The heap-diagnostic run on the split (40275433746, `scratch/older-sdk-heap-diag`, two commits on the PR head that add the listener and the uploads only) recorded the main task's worker with `-Xmx512m` on its command line and `MaxHeapSize=536870912`, `UseG1GC`, `G1HeapRegionSize=1048576` (**read**, `vm-command-line.txt`, `vm-flags.txt`). The dispatch's stop "a heap-limit change" is not tripped.

## 7. The heap figure (computed)

From the same run's `heapdiag-logs` artifact, the class histogram taken at the first caption class (`dump/at-0659-hist.txt`; `GC.class_histogram` runs a full GC first) has `Total 4233630 230811632`: **230,811,632 bytes = 220.1 MiB**, the coder's figure, against 490 MiB on one JVM in the heap report. `suites.tsv` holds 743 distinct classes with start and end rows (1,486 lines), matching the main task's XML count, and the caption classes are #659 to #661 there. The curve along the run, from `hist/`: 94.5 MiB at class 5, 118.2 at 35 (was 296: the SDK 33/30/27 steps are gone), 246.5 at 600, 230.0 at 630 (was 492: the SDK 28 step is gone), 238.9 at 650, 219.7 at 740. So the live heap now peaks around 247 MiB and the margin to the 512 MiB limit is about 265 MiB everywhere, not 22. Prediction (b) held with room to spare: the fall is 270 MiB, not 133, which says the four SDKs' sandboxes and what they held were worth about as much again as their tables and jar directories, as the report said it could not map.

## 8. `NoBackBy`'s getter changes nothing in production (read)

- `BackBy.kt:86-90`: `object NoBackBy`, `shown` now `get() = MutableStateFlow(null)`: a fresh flow, always null, on every read. The diff is that one line plus the doc comment.
- Production never reaches it. `TrackRecordingViewModel` is constructed in production only through `createTrackRecordingViewModel` (`TrackRecordingViewModelFactory.kt:16`, called from `MainActivity.kt:187`), which passes `backBy = container.backByWatch` (`:35`), a `BackByWatch` built at `AppContainer.kt:464`. `NoBackBy` is named in `main/` only as the default at `TrackRecordingViewModel.kt:158` and in that parameter's doc. No other `main/` code refers to it.
- What reads `shown` on a `BackByControl`: `TrackRecordingViewModel.kt:366` (`viewModelScope.launch { backBy.shown.collect { copyBackBy() } }`) and `TrackRecordingWatchCopies.kt:71` (`backBy.shown.value`). With `NoBackBy`, the collector now suspends on a flow only it references, so it is collected with the ViewModel; the value read is null, as it always was, since `NoBackBy.set` returns false and nothing ever wrote the old shared flow either. The other `.shown` hits in `main/` are `SundownWatch`, `WayHomeWatch`, `position.shown` and layout `fit.shown[...]`, unrelated types.
- `TrackRecordingViewModel.kt` is 864 lines at the base and at the PR head; the dispatch's stop on growing it is not tripped. The files the dispatch reserves for other coders are not in the diff (five files changed, listed in section 1).

## 9. The leak test's stability (read)

`TrackRecordingNoBackByLeakTest` builds the ViewModel in its own frame (`buildAndLetGo` returns only a `WeakReference`), then up to 20 rounds of `System.gc()` with 20 ms pauses until the reference clears. A false failure would need HotSpot to leave an unreachable object uncollected through 20 explicit full GCs, which it does not (and `-XX:+DisableExplicitGC` is not set: the worker's flags are in section 6). A false pass is ruled out by the second test, the positive control: the same ViewModel with a `HeldBackBy` whose flow the test holds stays reachable and `subscriptionCount` reads 1, so the probe is shown to see a held collector. Nothing in the first test holds the ViewModel another way: `Dispatchers.setMain(UnconfinedTestDispatcher())` so the launched collectors suspend at once; the other flows collected at construction (`returnWatch.state`, the default `sundownShown`, `recordingHalts`, `SilentTracker.fixes`) are per-instance; `PendingDeleteCommitScope` is only used on a delete; no recording starts, so the poll loop CLAUDE.md warns about never runs. It is plain JUnit (no Robolectric), 2 tests, about 0.5 s of GC pauses at worst. I see no flake path.

## 10. The revert's bite (read)

- `scratch/nobackby-revert` `c5b16c16d` differs from the PR head by exactly the one line: `get() = MutableStateFlow(null)` back to `= MutableStateFlow(null)`. It compiled and ran: run 40275307533's XML has `TrackRecordingNoBackByLeakTest` tests 2, failures 1, and the failure message is the test's own, "the ViewModel built without backBy was still reachable after it was let go: NoBackBy's shared flow kept its Back by collector expected null, but was:<...TrackRecordingViewModel@5e3d57c7>", with the control test passing. That is a message only this revert can produce, so it is not stale output.
- **Fail-first:** run 40274639596 at `4d2709ee6` (test in, fix not yet) fails the same test with the same message (`@7ac2e39b`), control passing. The test file changed between `cf129a9d5` and the head by two lines (an `@OptIn` import and annotation, hence line 66 versus 68 in the two logs); the assertions are unchanged.
- **The forward change is present** at the PR head (`BackBy.kt:87`, read directly, not restored from anything), and the PR run passes both tests.
- I did not run a revert of my own: this review is read-only with CI-only builds and no code pushes. What stands in for it is the three independent reads above (the scratch diff, the XML message, the head file).

## 11. Lint's crash and the script's move to `gradle/` (read; the attribution inferred)

- Run 40273911138 at `cf129a9d5`, with the script at `app/older-sdk-tests.gradle.kts`: "Android lint (debug)" and "Release compiles and packages" both failed in `lintAnalyzeDebug`/`lintVitalAnalyzeRelease` with "Unexpected failure during lint analysis ... `findFirCompiledSymbol` only works on compiled declarations, but the given declaration is not compiled", a Kotlin analysis-API error inside lint. The same run's test job failed separately on the `NumberFormatException` of section 5. Both lint jobs pass on `4d2709ee6` and on the PR head, with the script at `gradle/older-sdk-tests.gradle.kts`.
- The stack in the log names no `.kts` file. `4d2709ee6` both moved the script and changed its content (`toIntOrNull`), so the two fixes are confounded: the runs show that the crash went away with that commit, not that the location alone caused it. The script's doc comment states the location as the cause; that is plausible (lint's Gradle checks do parse Kotlin build scripts in the module, and a 250-line script with a class in it is not what they expect) but **inferred**, and I would word the comment as such. Not a merge question: lint passes now, and `gradle/` is a reasonable home either way; `apply(from = rootProject.file(...))` with `file()` resolving against `app` is confirmed by the plant (section 5).

## 12. Dispatch premises and stops, checked

- One test JVM at 512 MiB, 490 MiB at the first caption class: confirmed by the report and now superseded by section 7.
- The six classes found from the code, not the report: done (section 2); they are the report's six.
- `TrackRecordingViewModel.kt:158` defaults to `NoBackBy`; `:366` collects it; the factory passes `container.backByWatch` at `:35`: all read at the PR head (section 8).
- Stops: no heap-limit change; no test skipped, lost or run twice; the ViewModel file not grown; no other coder's files; no premise found wrong. The dumps' fate is the owner's, as the dispatch says.

## 13. Prediction scored

(a) held exactly (section 2). (b) held, and under-called: 220 MiB against a predicted ceiling of 357 (section 7). (c) held (section 8).

## Not done, and why

- No Gradle on the laptop and no run of my own on CI (read-only, no code pushes): the revert and drift checks are the coder's runs, verified by their diffs, logs and XML rather than re-run.
- Pre-main `c3e132a54`'s CI run was in progress; the exact-partition comparison is to `12a073783`, which is also what the PR's merge ref was built on.
- The heap dumps themselves (2.3 GB, `/mnt/work/scratch/caption-oom-diag/`) were not opened; the histogram totals in the artifact were enough for the figure asked.
- Downloads are under `/home/zynergy-labs/.cache/sorrel-410-scratch/` (the scratchpad's quota refused the 23 MB artifact); nothing there is needed again.
