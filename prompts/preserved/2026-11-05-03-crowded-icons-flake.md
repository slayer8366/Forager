# Dispatch 2026-11-05-03: The intermittent "crowded icons pick survives a restart" test (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2133 (intent). It follows the finding in RECORD -2123: the test passed and failed on the same commit. Every fact below was read at pre-main ee0651fe0 and records-after-173 de5b4b7ee while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim: **"Run the random test failure"**. That is a go to start this dispatch's Phase 1.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Rules
- Verify first: Phase 1 reproduces and explains, reports by message, then stops. Phase 2 fixes, on the planner's go.
- CI only: no Gradle on the laptop.
- Don't touch the phone.
- Never read, name or quote the Restricted Object.
- **Silencing is out of scope** (CLAUDE.md, Testing): no `@Ignore`, no retry rule, no widened tolerance, no removed assertion. Whatever the cause, it is fixed or reported.

## Change
- **The test:** `app/src/test/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreenCrowdedIconsSettingTest.kt` (199 lines), case "real touches across each row pick it, and the pick survives a restart" at :151-152. Last changed in 43312fe8b and 5568f2f1f (2026-10-27).
- **What was seen (-2123),** from Hazel's PR #491 summary:
  - the file held `zoom_then_fan` where `list` was expected;
  - at bb41345b4 it failed in the main job and passed in the job without the Street map; at b1e8ebfe9 it was the reverse;
  - one re-run of the failed job passed.
  - Her inference, not traced: two settings writes landing out of order.
- **Afterwards:** the case passes every time, for the reason it claims to check. If the app itself can lose or reorder a user's pick across a restart, that is a user-facing bug. Report it as one, with what the user would see; don't treat it as test-only.

## Scope boundary
- **Phase 1:** read only, plus throwaway CI runs.
- **Phase 2,** on go: a branch from pre-main, the test, and the code its cause is in. A fix in app code goes to the planner first, since it may change what a user meets.
- **Not:** main, RECORD.md, the audit index, the phone.

## Baseline
pre-main ee0651fe0. CI's workflow_dispatch takes a `tests` input: Gradle `--tests` patterns separated by ';' (ci.yml:25-29).

## Verify first (Phase 1: report these, then stop)
1. **Reproduce it.** Run the class repeatedly on CI with the `tests` filter, on throwaway branches from pre-main, never merged, enough times to give a failure rate. State the number of runs before reading any result.
2. **Count what the evidence ran on** (CLAUDE.md, the "never saw the data" family): which runs, which jobs, how many executions of the case, and how many failed.
3. **The cause,** with file and line: the settings store, its writes and their order, the restart in the test, and anything shared across tests (DataStore instance, scope, dispatcher, file path).
4. **Whether the app can do the same to a user,** with the steps, or why not.
5. **Your mechanism prediction, test plan and file list.** The fix's test is shown failing for the cause's reason first, if that can be done deterministically.

## Predictions
- **Outcome (planner):** the cause is in the test's or the store's handling of two writes or two store instances, not in what a user does. The fix makes the order deterministic.
- **Mechanism (coder):** in your Phase 1 report.

## Stops
Stop and report on:
- a cause in app code a user could meet;
- anything that would silence or weaken the test;
- a wrong premise;
- two failed attempts at one symptom.

## Finish line
Phase 1: your report by message, filed by the planner as `prompts/preserved/2026-11-05-03-phase1-report.md`. Phase 2, on go: a PR into pre-main, green, merged on the owner's word.
