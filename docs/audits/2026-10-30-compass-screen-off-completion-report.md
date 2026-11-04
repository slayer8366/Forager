# Compass while the screen is off: completion report (PR #450)

Dispatch `prompts/preserved/2026-10-30-14-compass-screen-off.md` (on `origin/records-after-173`), Amendments 1 and 2;
RECORD -1857, -1858, -1862, -1870. Sorrel's review: `docs/reviews/2026-10-30-sorrel-pr450.md` (sorrel-pr450, 745a5797a).
Written by Hazel (Opus), 2026-10-30. Amendment 1 was built by an earlier Hazel session; this session took over at 37ea85576
for Amendment 2. Each claim is marked **read** (file and line, or the named CI run's JUnit XML and log), **inferred**, or
**not verified**.

## Base

- Taken over at `compass-screen-off` 37ea85576, which was the remote head; the worktree was clean (read, `git status`,
  `git rev-parse`).
- `origin/pre-main` was b493cffeb at the start. It moved to 1ac67f12b (#451, `DataSourceCredits.kt` and its test only),
  and was merged in at 044b257ab (a merge, not a rebase).

## Commits (Amendment 2)

| Commit | What |
|---|---|
| b64a61c58 | Tests first: N-1 (paused keeps the compass) and N-4 (a fix flip re-checks trust). The host hands the fix as a `State`. |
| 925f7ece5 | The paused test reads the heading `State`, not the strip's text (see "Two corrections to the tests"). |
| 7ecffc4d3 | The N-4 fix in `TrueHeading.kt`. |
| 93e5e1a6c | The test host remembers its compass provider, as production does (see "Two corrections to the tests"). |
| 044b257ab | Merge of pre-main 1ac67f12b. |
| (this report) | This file. |

## The change for N-4

`TrueHeading.kt` (read, at 93e5e1a6c): the `CompassTrustJudge` and the time of the last stop (`CompassStops`, a small
private holder) are now `remember(compassProvider)`'d outside `produceState`, so they survive the producer's restarts. The
producer still restarts whenever a fix appears or goes, because `hasFix` is a key. Before this change, each restart made a
new judge, so a compass that was calibrating was trusted on its first good reading after the fix came back.

The cancellation catch already stamps the stop time, and it now does so for a restart too. On the next start, the existing
screen-off code runs `restartClearHold()` and the 10 s rule. So a fix flip gets the owner's re-check ("Yes, re-check
(Recommended)"), and its gap is about 0 ms, so a heading is never dashed by a flip. A new provider still gets a new judge,
which is right for a different sensor. The change is +14 −7 lines in one file. That is a small change, so this did not
trigger the dispatch's stop.

N-1 needed no code change. `TrueHeading.kt:123` already uses STARTED; N-1 asks only that a test pin it.

## Tests

Both tests are in `CompassScreenOffTest.kt` and use the real `AndroidCompassProvider` over Robolectric's sensor manager:

- **N-1:** `paused under a dialog or in split-screen, the compass keeps reading and the heading keeps turning`. It moves the
  activity RESUMED → STARTED with `moveToState`, then asserts that one listener is still registered and that a reading sent
  while paused reaches the heading `State`.
- **N-4:** `a compass untrusted when the fix was lost must hold good afresh after the fix comes back`. The compass is
  untrusted, and 1 000 ms into its hold the fix is lost (the producer restarts), and then the fix is regained (it restarts
  again). The test then asserts:
  - the first good reading at 61 000 ms is still `Unreliable`;
  - at 62 999 ms it is still `Unreliable`;
  - at 63 000 ms it is `Available`.

### Two corrections to the tests

Neither correction weakens what the tests claim.

1. **The paused test, strip text.** Fail-first run 41135927202 showed both of the N-1 test's own assertions passing. It then
   failed at the strip-text read with `IllegalStateException: No compose hierarchies found` (read: the stack trace in that
   run's JUnit XML, `CompassScreenOffTest.kt:141` called from `:254`). The Compose test harness does not see a paused
   activity. That assertion was removed (925f7ece5). The heading `State` it still checks is the value the strip and the HUD
   read.
2. **The host's provider (the N-4 test).**
   - After the fix, head run 41138741272 still failed the N-4 test with the same message as before the fix. This was the
     first failed attempt on this symptom. The test host built `AndroidCompassProvider(context)` inline in `setContent`
     (`CompassScreenOffTest.kt:96` at 7ecffc4d3), and the class has identity equality (`AndroidCompassProvider.kt:81`).
     **Inferred** from Compose's scoping rules: reading `hasFix` (`TrueHeading.kt:114`) recomposes the caller's scope on a
     flip, which made a new provider, so `remember(compassProvider)` gave a new judge. That reproduces the old behaviour.
   - Production passes the single `AppContainer.kt:223` instance (`MainActivity.kt:664`) and `SightingsMap`'s remembered
     `MapCompass` (`ScreenMapLocals.kt:44`), both read.
   - 93e5e1a6c makes the host `remember` its provider. With that, the forward N-4 code passes the N-4 test in run 41140807137
     (a6, whose only edit is N-1's line) and in the final head run, and the earlier code fails it (a0, below). The inference fits that data. It was not proved by
     instrumentation.
   - **What this means for the first fail-first run:** 41135927202's N-4 failure could not tell the old code from the new
     one, because the new code failed too. The real fail-first is a0 below.

## Fail-first and revert checks (Amendment 2)

Each scratch branch is one edit from 93e5e1a6c and ran CompassScreenOffTest and CompassTrustJudgeTest through
`workflow_dispatch` with the `tests` filter. For every run:

- the log has 0 Kotlin compile errors (`grep -cE ' e: (file|/)'`);
- "Build the debug APK" reported `BUILD SUCCESSFUL`;
- the test task ran and reported `BUILD FAILED`;
- the counts come from the JUnit XML: 18 tests per run (7 + 2 earlier screen-off tests, 2 new ones, and the 9 judge tests).

Each branch's file was restored from a copy saved before editing (`/mnt/work/scratch/compass-screen-off/a2/saved/`), never
from git. After each one, `cmp` confirmed that the forward `TrueHeading.kt` was still present.

| Branch (scratch) | Edit | Run | Failures (of 18) | Message | For this edit? |
|---|---|---|---|---|---|
| a0-pre-n4-code (f3c0ca742) | `TrueHeading.kt` back to 37ea85576 (fail-first, not a one-line revert) | 41140804474 | 1: the N-4 test | "the first good reading after the fix came back was trusted without a fresh hold", got `Available(degrees=30.0)` | Yes: the old code builds a new judge per producer. N-1 passes, as it should (STARTED was already there). |
| a6-resumed (35f1965e3) | `repeatOnLifecycle(Lifecycle.State.STARTED)` → `RESUMED` | 41140807137 | 1: the N-1 test | "the compass listener was dropped while the app is paused but still on screen expected:<1> but was:<0>" | Yes. The N-4 test passes here. |
| a7-judge-per-producer (9bc9ad127) | `remember(compassProvider) { CompassTrustJudge() }` → `remember(compassProvider, hasFix) { … }` | 41140809626 | 1: the N-4 test | the same as a0 | Yes: a judge per fix state. |
| a8-stops-per-producer (96e94a0b1) | `remember(compassProvider) { CompassStops() }` → `remember(compassProvider, hasFix) { … }` | 41140813155 | 1: the N-4 test | the same as a0 | Yes: the kept judge is not re-checked, so the 1 000 ms hold start counts and clears at 61 000 ms. |

**Superseded runs, kept for the record (not evidence).** r6, r7 and r8 were cut from 7ecffc4d3 (runs 41138784130,
41138787068, 41138790007), before the host fix. r6 failed N-1 for its own reason, and also failed N-4, which was the head's
own failure at that commit. r7 and r8 failed N-4, but so did the forward code, so their N-4 failures prove nothing. Their
branches were deleted and redone as a6 to a8.

## Amendment 1's revert checks (N-2: the six run ids from RECORD -1862)

These are the earlier session's runs against 37ea85576, re-read here from the logs and JUnit XML saved in
`/mnt/work/scratch/compass-screen-off/`, with the patches read through the compare API. All six logs have 0 compile-error
lines. Each tree is one edit from 37ea85576 in one file. The branches were deleted per RECORD -1863.

| Revert | Branch commit | Edit | Run | Failures | Failing messages (abridged) |
|---|---|---|---|---|---|
| r1 no lifecycle | d436690f3 | `repeatOnLifecycle(STARTED) {` → `run {` | 41132190530 | ScreenOff 6 of 7 | listener still registered while off; a reading while off was taken; stale heading still shown; Maps tab still reads while off; first reading blended; untrusted trusted without a fresh hold |
| r2 smoother kept | b08408557 | one smoother across starts | 41132193969 | ScreenOff 3 of 7 | first reading back 46.99 not 90; strip "47° NE" not "90° E"; first reading blended |
| r3 restart is reset | 47fe56359 | `goodSinceMillis = null` → `reset()` | 41132197601 | Judge 2 of 9, ScreenOff 1 of 7 | first good after the gap still untrusted; restarting does not forget untrusted; trusted without a fresh hold |
| r4 restart no-op | 15525c948 | `goodSinceMillis = null` commented out | 41132200758 | Judge 1, ScreenOff 1 | a good stretch before the gap counted; trusted without a fresh hold |
| r5a never dash | d9b64311f | gap check → `false` | 41132203678 | ScreenOff 2 | stale heading still shown after 10 001 ms; strip still shows the old heading |
| r5b always dash | d4e46e6b9 | gap check → `true` | 41132206429 | ScreenOff 2 | a reading while off: got `NeedsFix`; back after exactly 10 s: got `NeedsFix` |

Amendment 1's fail-first was run 41130603801 (788fb5545): 7 of 9 new tests failed. Its green run was 41132051834
(37ea85576), with 5,962 tests (-1862; Sorrel computed the same figure).

## CI on the final head

The final run is on the head that carries this report; its id and counts are in the PR body. Expected: 5,964 tests, which
is 5,962 plus the two new ones, plus whatever #451 added.

## Not verified

- **On a phone:** everything in Sorrel's nine phone checks, including the redraws and the CPU stopping with the screen off,
  and the compass still turning while paused under a real system dialog or in split-screen. Robolectric's `moveToState`
  is the only evidence for STARTED.
- **The N-4 root cause in the test host** (a new provider on each recomposition) is inferred from Compose's scoping rules
  and fits the runs. It was not instrumented.
- **A fix flip while the screen is off:** the producer is then cancelled while `repeatOnLifecycle` waits, not while it
  collects. By reading, the stop stamp from the screen-off stays in place, so the gap is measured from the screen-off. No
  test covers this case.
- **Sorrel's N-3** (the HUD, the facing judge and the puck engine across a stop) and **N-5/N-6** were not in Amendment 2
  and were not touched.

## Calls beyond the dispatch

- The test host now passes the fix as a `State` (the `SightingsMap` overload) instead of a plain value. That lets the N-4
  test lose and regain the fix. The plain-value overload, which the Maps tab uses, forwards to the same function, and the
  real-screen test still goes through the Maps tab.
- The strip-text read was dropped from the paused test, and the host remembers its provider (both under "Two corrections").
