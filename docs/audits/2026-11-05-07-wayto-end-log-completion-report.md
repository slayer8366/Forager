# Navigate logs what each end of a way-to search became: completion report (PR #493)

Dispatch `prompts/preserved/2026-11-05-07-wayto-end-log.md` (on `origin/records-after-173`, 18f74baf4), RECORD -2146. The planner accepted the verify-first calls at RECORD -2149. The owner: "Add one log line first (Recommended)".

Written by Hazel (Opus), 2026-11-05.

Each claim is marked:
- **read**: file and line, or the named CI run's JUnit XML or log;
- **observed**: command output;
- **inferred**;
- or **not verified**.

## Base

- `origin/pre-main` was at **ee0651fe0**, the dispatch's base. It had not moved (observed, `git fetch`).
- The branch is `wayto-end-log`, cut from ee0651fe0, in `~/.cache/forager-wt/wayto-end-log`. Its upstream was unset at creation, so no push could reach pre-main.
- Nothing in the planner's worktree, in `domain/dropoff/` or in `WayToWaypoint.kt` was edited (read, `git diff --stat ee0651fe0 origin/wayto-end-log`: 5 files).

## Premises re-checked

| Premise | How it was checked |
|---|---|
| Where the walker's end is decided | read: `WayToEnds.kt:192-207` at ee0651fe0. The fix gate is at `:193` and classify at `:201`; the gated engine end is at `:203`, the chooser at `:204-206`. |
| The waypoint's end is planned first, and its refusal stops the walker's | read: `WayToEnds.kt:96-97` |
| The existing lines | read: `FindWayToWaypointUseCase.kt:92-95` (the settle call and its branches), `:155` (the straight line) and `:164-166` (by trail). Both go through `errorLog.w`, which is `Log.w` (`AppContainer.kt:204`). The tag is at `:172`. |
| How a search reaches the use case | read: `AvailabilityWaypointRoute.kt:160` and `:169` call `startSearch(here, fix.accuracyMeters)`, and `:226` calls `find(...)`, which is use case `:68`. `ends.settle` at `:92` returns `Settled`. What each end became was private to `StreetDropOffEnds` (`EndPlan`, `:128-139`). |
| A release build keeps `Log.i` | read: no `-assumenosideeffects` in `proguard-rules.pro`, and `isMinifyEnabled = false` (`app/build.gradle.kts:307`). That the line shows in a release build's logcat is **not verified**. |

**Findings at verify, ruled at RECORD -2149:**
- **F1.** `ErrorLog` has only `w`, so the use case had no info-level sink.
- **F2.** Today's by-trail and refusal lines already carry tile ids (`NearbyMapData.kt:86-87`, `:131`; `LegGates.kt:74`), and a route-ways failure carries the `.rd5` name. They stay word for word, and the new line carries none of these.
- **F3.** classify's Trail result has no reason (`StreetDropOff.kt:27`, `:48`), so the line states the whole rule.
- **F4.** No fixture walker reaches the chooser with none qualifying.

## Commits and PR

| Commit | What |
|---|---|
| 0b75e0e95 | **Tests first.** `FindWayToWaypointEndLogTest`, over a stub: only the use case's new `info` parameter, logging nothing. |
| b2dfc73c0 | **The change. The last code commit.** |
| (this report) | This file only. |

PR #493 into pre-main. It merges only on the owner's word.

## What was built

Read: the files at b2dfc73c0.
- **`domain/wayto/WayToEndsRecord.kt`** (new, 93 lines): what each end became (`WayToEndBecame`), the by-trail kinds (`ByTrailBecause`), and the line's words. It holds the words only.
- **`WayToEnds.kt`:**
  - Each `EndPlan` carries what its end became.
  - `settle()` attaches both ends' record to the `Route` or `Refused` it returns (`Settled.ends`).
  - The steps after planning moved verbatim into `drawAndGate()`, and no decision line changed.
  - `read()` names each by-trail kind beside the reason it always gave.
- **`FindWayToWaypointUseCase.kt`:** one `info(TAG, …)` call right after `settle`, before today's lines, so it is logged once per search that reaches the ends step. The parameter defaults to no-op (F1).
- **`AppContainer.kt`:** `info = { tag, message -> Log.i(tag, message) }`, the use case's log sink.

**The line**, at info level, tag `FindWayToWaypoint`, one per search:

`The way to the waypoint's ends: the walker's end <W>; the waypoint's end <P>.`

Each end reads one of:
- `moves to a pick-up at the private way's mouth (rank 1 of 3, ACCESS_WAY), its leg 77 m`. The waypoint's end reads "a drop-off". Ranks 2 and 3 read "the addressed street (… ADDRESS_STREET)" and "the nearest public street (… FRONTAGE)".
- `keeps the engine's end and gates its lead-in: not a street end (no building within 15 m, and not built-up land with a public street within 150 m)`. The waypoint's end reads "its leg".
- `keeps the engine's end and gates its lead-in: street or trail could not be told (the map tile there is not stored or was not read)`. The walker's end only.
- `is not judged for a pick-up, the fix reporting 15.5 m, worse than 15 m, so keeps the engine's end and gates its lead-in`, or "the fix reporting no accuracy". The walker's end only.
- `goes by trail: the map could not be read`, or `a stored tile in reach has no fence mark`, or `the route ways could not be read`.
- `is refused (LEAD_IN_BLOCKED)`. Today's line with the reason follows, unchanged.
- `is not reached, the waypoint's end being refused first`. The walker's end only.

A `WayToEnds` that records nothing (only the `AcceptEnds` test fake) gets `The way to the waypoint's ends were not recorded by the ends step.`

A search that falls back before the ends step logs no end line, since no end was judged.

**What the line describes:** the line says what each end was planned as. A search refused after that, at the drawn legs' gates or the re-run, is followed by today's straight-line line, which says why.

## Failing first

CI run 42108029374 on 0b75e0e95 (read: its log, then the `unit-test-report` JUnit XML).
- The build log has no compile errors, and `compileDebugUnitTestKotlin` ran.
- testDebugUnitTest: 960 files, 6630 tests, **9 failures**, 0 errors, 24 skipped. testDebugUnitTestOlderSdks: 6 files, 24 tests, 0 failures.
- All 9 failures are `FindWayToWaypointEndLogTest`'s line cases. Each reads `expected:<[I/FindWayToWaypoint: The way to the waypoint's ends: …]> but was:<[…]>`, where the actual list lacks only the info line.
- In the refused and by-trail cases, the actual list held today's warning lines exactly as the test expects them.
- Each case's route assertion, which comes first, passed over the stub.
- The Street-map-deleted job showed the same 9 failures.

**Not shown to bite, by design:** "a search that falls back before the ends step logs no end line" passed over the stub. It pins the absence of the line before the ends step, so no revert of the line can fail it.

**Passes before and after, by design:** the by-trail and refusal cases assert today's lines word for word. Those parts pin A3, and no edit in this branch could fail them. They bite only together with the info line.

## Revert check

Branch `wayto-end-log-r1`, commit 03528374b, one edit: the `info(TAG, …)` line removed from `FindWayToWaypointUseCase.kt:105`. Full CI run 42111554422 (workflow_dispatch, no filter).
- **Predicted:** it compiles, and only the nine line cases fail, for the line's absence.
- **Build log first:** no compile errors; `compileDebugKotlin` and `compileDebugUnitTestKotlin` both ran (read).
- **JUnit XML:** 6654 tests, **9 failures**, 0 errors, 24 skipped. The 9 are exactly the predicted cases, each failing on the missing `I/FindWayToWaypoint: The way to the waypoint's ends: …` line. Nothing else failed, and the Street-map-deleted job showed the same 9.
- **Not a stale run:** CI checks out the commit afresh, and the run's head is 03528374b.
- **Restore:** the r1 worktree's file was restored from the copy saved before the edit, not from git.
- **Afterwards:** the forward change was confirmed present at `origin/wayto-end-log` b2dfc73c0 (the line at `:105`, five files in the diff against ee0651fe0).

## CI on the last code commit

CI run 42111506814 on b2dfc73c0 (read: the JUnit XML).
- **Attempt 1** of "Build, test, publish APK": 6654 tests, **1 failure**, 0 errors, 24 skipped.
  - The failure is not this dispatch's: `AvailabilityScreenCrowdedIconsSettingTest > real touches across each row pick it, and the pick survives a restart`, at `AvailabilityScreenCrowdedIconsSettingTest.kt:170`, reading `the file holds it under map.crowded_icons expected:<[list]> but was:<[zoom_then_fan]>`.
  - Dispatch 2026-11-05-03 (`crowded-icons-test-fix`, f773607a1) is fixing exactly that race: "the restart waits until every pick's write has returned".
  - In the same run, the Street-map-deleted job ran every unit test and passed. The stub run and the r1 run also passed this test.
  - It was reported to the planner and not touched. Only the failed job was re-run, which the planner approved.
- **Attempt 2** (only the failed job re-run, same commit): **green**, every job of the run passing. Its JUnit XML:
  - testDebugUnitTest: 960 files, 6630 tests, 0 failures, 0 errors, 24 skipped;
  - testDebugUnitTestOlderSdks: 6 files, 24 tests, 0 failures;
  - 6654 in all, the same count as the stub and r1 runs.
  - This is attempt 2's own artifact, not attempt 1's: the crowded-icons class's XML is stamped 21:51:28 with 0 failures, against attempt 1's 21:26:39 with 1.
  - The Street-map-deleted job passed in attempt 1 and was not re-run.
- `FindWayToWaypointEndLogTest`: 10 tests, 0 failures. Every existing way-to class passed:
  - FindWayToWaypointDropOffTest 10, FindWayToWaypointPickUpTest 8 and FindWayToWaypointDropOffCopyTest 5;
  - FindWayToWaypointStoreRefusalTest 1, FindWayToWaypointUseCaseTest 15 and FindWayToWaypointAreasTest 5;
  - FindWayToWaypointOnBRouterTest 4, WayToRetryTest 11, WayToStrayTest 3 and StreetDropOffFixtureTest 12.
- The other jobs passed: release compiles, lint, no debug-only sources, map style checkers, Tile Worker, and route-data cutter.

## Not tested

- **The chooser running at the walker's end with none qualifying** (`EndPlan.Refused` from `NoneQualifies` at the walker's end). The fixture has no such walker (F4, RECORD -2149). The "is refused (…)" words are shared with the waypoint's end, which is tested.
- **A walker whose own tile is not stored** (`street or trail could not be told`). The fixture stores the walker's tile in every case.
- **Rank 3, FRONTAGE.** Cases B and C reach their address street first.
- **The by-trail kinds "the map could not be read" and "the route ways could not be read".** Only "a stored tile in reach has no fence mark" is asserted. The other two are chosen at the same `read()` call sites as today's reasons (read: `WayToEnds.kt:193-198` at b2dfc73c0). That they read as written is inferred.
- **The production wiring** (`AppContainer`, `Log.i`). No unit test reaches it. The device check below covers it.

## Calls beyond the dispatch (accepted at RECORD -2149)

- One line naming both ends, not one per end.
- The line fires on refusals too; today's line still follows it unchanged.
- The by-trail reason is given by kind only, with no tile id or file name (F2).
- The `info` sink defaults to no-op, and the one `AppContainer` line wires it (F1).
- A `WayToEnds` that records nothing is said to have recorded nothing.

## Device check

Debug or release build from b2dfc73c0 or later. Cheapest first.

1. **The line appears.** Navigate to any waypoint whose area has routes stored. In `adb logcat -s FindWayToWaypoint:I`:
   - **Pass:** one line starting `I FindWayToWaypoint: The way to the waypoint's ends:` per search that reached the ends step, before any `W FindWayToWaypoint` line of the same search.
   - **Evidence:** the line, copied.
2. **No position.** Read that line.
   - **Pass:** it holds no coordinate, tile id, file name or street name: only lengths, ranks, reasons and note names.
3. **The field check.** In dispatch 2026-11-05-04's check 1 (section 2.6), L2 now reads this line.
   - "moves to a pick-up at … (rank N …)" is a pick-up chosen.
   - "keeps the engine's end and gates its lead-in: not a street end …" is the gated engine end.
   - "is not judged for a pick-up, the fix reporting X m …" is a run not judged.
   - The rule that "no line" means a pick-up or a gated end no longer holds.

## Disclosure

- The leg lengths in the tests (77 m and 75 m) were predicted before any run, from the fixture's points and the tile's quantisation, in a throwaway script whose output was counts only. They matched on the first green run.
- The stub, r1 and forward runs each ran the full suite, with no test filter. This report's own commit is docs-only. Its CI result is posted on the PR, not here.
- My first wait on the re-run exited on a wrong `jq` field, and the next read hit a GitHub API rate limit. Neither touched a result: the run was read once it showed as completed.
