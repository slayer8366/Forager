# Dispatch 2026-09-28-348: decoded-photo-thread — INTERIM report (parked, no Gradle run)

**Status: parked by the owner** ("I'll be conducting a flake hunt once dev cools down a bit, this can wait until
then", relayed by the planner). **No test run of any kind was made for this dispatch.** Diagnosis is not done.
Model: configured `claude-sonnet-5-5` (the session could not run `/model`; the serving model is unverified).
Base: `origin/journal-redesign` `60d626b9` (head when I started; contains the -317 merge `851e28fd`). Branch
`decoded-photo-thread`, scratch, never merged. Instrumentation commit `081d9ed5` (test sources only; no production
file or existing test touched). Later work on the harness mechanism by -349 (`leaving-journal-flake`) reuses
`ThreadProbe` by that commit; see its interim report `docs/audits/2026-10-01-leaving-journal-flake-diagnosis.md`
on that branch (head `75fca96f` when read).

## 1. What decides inline versus deferred in the harness (read from bytecode; not yet observed on a failing run)

Source: `javap -p -c` on `ui-test-runtime.jar` (androidx.compose.ui 1.12.1, Gradle transform cache).

- `FrameDeferringContinuationInterceptor` has **one instance-wide** `private boolean isDeferringContinuations`
  guarded by a `lock` object. It is not per thread.
- `FrameDeferredContinuation.resumeWith` (offsets 27-56): under the lock, if the flag is **true** the continuation
  is appended to `toRunTrampolined` and not resumed; if **false** it is resumed inline with
  `continuation.resumeWith(...)` (offsets 81-90). It never examines the calling thread.
- The flag is set true by `runWithoutResumingCoroutines` (it throws `IllegalStateException("isDeferringContinuations
  was not reset")` if already true) and set false again when the trampolined queue drains. Grep over the unpacked
  jar finds that method referenced only from `TestMonotonicFrameClock` and the interceptor itself;
  `TestMonotonicFrameClock.performFrame` is its call site.
- `ApplyingContinuationInterceptor$SendApplyContinuation.resumeWith` resumes the wrapped continuation and then
  calls `Snapshot.sendApplyNotifications()` (offsets 5, 13), on whatever thread resumed.
- `TestMonotonicFrameClock.withFrameNanos` queues an awaiter and launches a coroutine (`$2$1$2`) that does
  `delay(frameDelay)` then `performFrame()`.

**Reading, from the above plus the `pin-3` stack** (`~/Zynergy/forager-wt/album-gesture-evidence/pin-3/`, the
`DecodedPhotoTest` XML): when the IO decode returns, the resume is inline on the IO worker whenever no frame is
in progress anywhere, the recomposer's wake-up from the apply notification is resumed inline too, and
`performFrame` then runs the whole recompose-and-apply on that worker (the stack runs `FrameDeferredContinuation.
resumeWith:187` -> `SendApplyContinuation:53` -> `sendApplyNotifications` -> `advanceGlobalSnapshot` ->
`Recomposer.recompositionRunner` -> `...resumeWith` -> `runRecomposeAndApplyChanges` -> `withFrameNanos` ->
`performFrame` -> `applyChanges` -> `PainterElement.update` -> `requestLayout` -> `checkThread`).
The same XML holds a second failure in that test, `ArrayIndexOutOfBoundsException` in `SlotWriter.moveSlotGapTo`
during dispose; that fits two threads touching one composition and is **inferred, not observed**.

**Inferred, not established:** that the failure needs a main-thread frame (the test thread's `waitUntil` idle
step) to overlap the worker's `performFrame`, with the flag's single value hiding the overlap. The
`check(!isDeferring...)` in `runWithoutResumingCoroutines` is the bytecode's own sign that overlap was not
meant to happen. Nothing here observed an overlap. "What puts the apply on the worker only sometimes" remains
the open question from -317's addendum.

## 2. Planned runs and sizing (none executed)

- Set: the same 87 tests as -317 (`DecodedPhoto*` 17, `JournalPendingDeleteTest` 52, `JournalTabTest` 18) plus
  the 4-test instrumented copy, `taskset -c 0,1`, `--no-daemon`, per-run checks for disk, other Gradle, `e:`
  lines and fresh XML timestamps. Scripts: `dpt-run.sh`, `dpt-loop.sh` in
  `~/Zynergy/forager-wt/decoded-photo-thread-evidence/` (outside the repo; not yet committed).
- Observed rate: 1 of 12, Wilson 95% **1.49% to 35.39%** (recomputed here, not quoted).
- Exact binomial, not `9/p`: n = ceil(ln 0.05 / ln(1-p)): p = 1/12 -> **35**; p = 3.54% -> 84; p = 1.49% -> **201**.
  35 runs give 95.2% chance of at least one failure if the rate is 1/12, and 0 of 35 would exclude a rate above
  8.2% only. **I proposed 35 and did not choose between 35 and 201; that is for the planner/owner.** A second
  35 on the pre-(-317) `DecodedPhoto` (saved copy `DecodedPhoto.kt.base`, hash `79222c49...`) only if the first
  35 fail; the branch's `DecodedPhoto.kt` hash `5eab4b57...` equals the saved `.fixed2`.
- Disk was 1820 MB against the 2048 MB floor when I stopped, so no run could start.

## 3. What was never run

Everything that needs Gradle: the 35 pinned runs, the instrumented copy itself (**it has not been compiled**),
the pre-(-317) comparison, and so any observed thread or flag state at a failing apply. Not done either: the
dispatch's step 6 (CI list since `851e28fd`) beyond `gh run list` (failures present on this branch at run ids
`36785322394`, `36788882444`, `36792079936`, `36794217019`, `36795311788`, `36796914796`, `36798317295`); I did
not read their logs, and rely on -349, which read them: 6 `LeavingTheJournalFixesTest` failures in 99 runs
(5 of 6 `performMeasureAndLayout called during measure layout`, one `Detected multithreaded access to
SnapshotStateObserver`), the `36785322394` stack bottoming out on a `CoroutineScheduler$Worker`. -349 reads
that as the same mechanism as the `pin-3` failure; its own report marks that as inferred. I have not checked it.
No `CalledFromWrongThreadException` on CI is known to me.

## Disclosures

**Confirmed (read):** the bytecode facts in section 1; the `pin-3` stack; the sizing arithmetic (computed).
**Inferred:** the overlap reading; production safety (-317's `AndroidUiDispatcher` argument, not read by me
again, not run on a device). **Could not determine:** everything dynamic. **Premises that were wrong:** the
dispatch said the planner is "Planner" in ListAgents; no such name existed (it is now "forager-78"), so I stopped
without messaging. **Decided beyond scope:** nothing.
