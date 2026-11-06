# Return ends on arrival: report (RECORD -844, -845, -850)

Branch `return-ends-on-arrival`, cut from main `58fbc642` (verified against `origin/main` before starting).
Code and tests are at `a8dfbe1e` and `4216702d`. Written 2026-10-11T05:15Z. No PR: the planner opens it.

## What was asked, in the owner's words

These are RECORD -844 and -845 on `records-after-173`, re-read for this dispatch:

- **The return ends on its own.** When the walker reaches the start, the return ends and the recording carries on.
- **The display holds "Arrived".** It shows "Arrived" for about 10 seconds with the start ringed. The reading is held, not live, so driving off does not turn it back into a distance. Then the display closes by itself to the plain map. The owner chose "Arrived for a few seconds, then closes (Recommended)".
- **Off-track and the route tick stop at arrival.**
- **Must leave first.** Arrival counts only after the walker has been outside the arrival circle at least once since tapping Return. The owner chose "Must leave first (Recommended)".
- **Back by and sundown are unchanged.** Their arrival behaviour stays as it is today, through an "arrived after Return" flag that both read.
- **Waypoint navigation (T8) is not changed.**

## The previous reading, re-checked against main 58fbc642

| Claim | Result |
|---|---|
| `BackByWatch.hasArrivedAfterReturn` uses `hasArrived` and `fixFreshness` | Confirmed: `BackByWatch.kt:273-288` (call at `:287`) |
| `SundownWatch.tick` uses the same test | Confirmed: `SundownWatch.kt:285-290` |
| `hasArrived` is max(15 m, 2 × accuracy) | Confirmed: `NavigationReadout.kt:67-71`, with `ARRIVAL_MIN_RADIUS_METERS = 15.0` and `APPROACHING_ACCURACY_MULTIPLIER = 2.0` |
| `fixFreshness` | Confirmed: `NavigationReadout.kt:111` |
| Arrival is measured to the origin waypoint, else the track's first point | Confirmed in both watches (`BackByWatch.kt:280-286`, `SundownWatch.kt:276-286`) |
| Both evaluate on a 15 s tick | Confirmed: `SUNDOWN_TICK_MILLIS` and `BACK_BY_TICK_MILLIS = 15_000L` in `TrackRecordingService.kt`. Back by is also evaluated on a fix once its time is due. |
| The HUD's `arrivedAtStart` is live, with no flag | Confirmed: `NavigationHud.kt:1023-1026`. It is read at `:1035`, `:1164` and `:1175`, and for the ring at `AvailabilityCompactMapUi.kt:642` |
| Return ended only by walker, recording stopped or service destroyed | Confirmed: `ReturnWatch.kt:209` and `:170`; enum at `ReturnRecord.kt:46-55` |
| The HUD shows only while navigating | Confirmed: `AvailabilityScreen.kt:916` |
| The ViewModel copies the watch's flags | Confirmed: `copyFromWatch`, `TrackRecordingViewModel.kt:380-394` (the copy is at `:383`, the route tick at `:389-393`) |
| Back by and sundown read ReturnWatch's returning flag | Confirmed: `AppContainer.kt:411` and `:427` |

## What was built

**`ReturnWatch`.** On each raw fix while returning, a new function `hasArrivedAfterLeaving` applies the same `hasArrived` test to the watch's start point. It acts on GPS fixes only (`FixProvider.mayAct`), as Back by does, and remembers whether a GPS fix has been outside the circle since Return. Once the walker has been outside and a GPS fix is back inside, `endReturnOnArrival` runs. It drops the path and the off-track judge, clears returning and off-track, sets `arrivedAfterReturn`, and writes `ReturnEnded(…, ARRIVAL)`. The arrival fix itself is not judged, and nothing is judged afterwards until the next Return.

**The record file.** `ReturnEndReason.ARRIVAL` is new. The existing debug writer (`FileReturnRecord`) turns it into `return-ended track=… reason=arrival`.

**The flag Back by and sundown read.** The new `ReturnWatch.arrivalJudgedFor(trackId)` is true while returning, and also once a return has ended on arrival. Both `AppContainer` lambdas now point to it.
- The flag is cleared by the next Return and by the end of the recording.
- Closing the display during the hold leaves it set: that return ended by arrival, not by the walker.
- A return the walker ends before arriving behaves as before.

**The ViewModel hold.**
- `TrackRecordingUiState.isArrivalHeld` is set when the return this screen was showing ends with `arrivedAfterReturn`.
- `ARRIVAL_HOLD_MILLIS = 10_000L` (`TrackRecordingViewModel.kt`, end of file) carries its reason and cites RECORD -845. After it, the hold clears.
- The hold also ends early on the display's exit (`stopReturn`), on a new Return, or when the recording stops.
- A screen opened after the arrival gets no hold.
- The route tick stops at arrival through the existing `copyFromWatch` path.

**The screen.**
- `MainActivity` passes `isArrivalHeld` through. While it is on, the Return control's callback is the exit, so the X closes the hold instead of starting a new Return.
- `AvailabilityScreen` counts the hold as navigating, so the display stays up and then closes when the hold ends.
- `CompactMapNavigation.isArrivalHeld` reaches the ring and the HUD.
- The HUD shows `heldArrivalReadout`: "Arrived" whatever the fix says, with no needle, status or distance label.

**Waypoint navigation is untouched.** `WaypointNavigation.kt` and its arrival path are not changed.

## Tests (written before the code was compiled; "failing first" shown by revert, see below)

The new tests reference new symbols, so on today's code they cannot compile. That a test fails on today's behaviour is shown by revert R1, which removes the arrival end: the drive-off test then fails with `no off-track alert on the drive-off expected:<0> but was:<1>`, which is the 2026-10-10 bug.

**Through the real service and container** (`testDebug/…/service/TrackRecordingServiceReturnArrivalTest`, 2 tests). The walk out and back go through the platform listener. At the start, the return ends and `return-record.log` has `return-ended track=… reason=arrival`. On the container's `BackByWatch` tick, Back by ends with `back-by-record.log` reading `ended … reason=arrival`. On a 14-fix drive-off:
- there is no off-track notification and no `went-off-track` line;
- the track stays open and is still stored (20 points);
- the service is still running.

A second test covers Return tapped at the start: it does not end there, straying posts the off-track notification, and coming back ends the return.

**`ReturnWatchArrivalTest`** (7 tests):
- reason arrival, with the recording still begun;
- a drive-off after arrival posts and records nothing;
- must leave first;
- network fixes neither leave the circle nor arrive;
- straying before arrival still alerts;
- when `arrivalJudgedFor` is true and false;
- a new Return starts afresh.

**`ReturnArrivalAlertsTest`** (4 tests). This class wires `SundownWatch` and `BackByWatch` to `returnWatch::arrivalJudgedFor` the way `AppContainer` does:
- Back by still ends on arrival after the return has already ended, and does not end after a return the walker ended;
- sundown still stops on arrival after the return has ended, and still fires all three alerts after a return the walker ended.

**`TrackRecordingSwipeAwayTest`** (5 new tests, ViewModel plus watch, virtual clock):
- "Arrived" is held through a drive-off, still held at 9,999 ms, and closed at 10,001 ms;
- the recording still runs;
- the exit closes the hold at once;
- stopping the recording ends the hold;
- a return ended by the walker is not held;
- a screen reopened after arrival gets no hold.

**`AvailabilityScreenArrivalHoldTest`** (2 tests, the real screen). While held, with the fix 1.1 km away, the display reads "Arrived", the status is empty, the start is ringed and the control reads "Stop navigating". When the hold ends, the HUD node is gone and no route goes to the map.

**`FileReturnRecordTest`** (1 new test): the exact line for `ARRIVAL`.

**Two existing fixtures were changed.** These are the only edits to existing tests:
- `ReturnWatchReminderTest` "turned back on mid-walk…"
- `ReturnWatchRecordTest` "going off the path … re-armed"

Both used the start point itself as "back on the path", and by RECORD -844 that now ends the return. Their "back on the path" readings moved to 25 m north of the start, which is on the path (within 40 m) and outside the arrival circle (15 m). No assertion changed. Each fixture records why in a comment. I am reporting this because it is a change to existing tests.

**Targeted run (forward).** 15 classes, 156 tests, 0 failures, at `4216702d`:
- the 6 new or changed classes above;
- `ReturnWatchTest`, `ReturnWatchRecordTest`, `ReturnWatchReminderTest`;
- `AvailabilityScreenRouteDrawnTest`, `NavigationHudArrivedTest`;
- `TrackRecordingServiceSwipeAwayTest`, `TrackRecordingServiceBackByLoopTest`;
- `BackByWatchTest`, `SundownWatchTest`.

Each class's count was read from its JUnit XML. Two runs failed before this one, both in the tests themselves: a test name with an illegal `;` (a compile error), and a Back by assertion that read the last record entry, which is `AlarmCancelled` because that entry follows `Ended`. Both were fixed in `4216702d`. **The full suite was not run here; it runs on CI.**

## Revert checks

How the runner works (`/tmp/rev/lib.sh`, not committed):
- Before each edit it saves a copy of the file, and deletes the earlier results before each run.
- It checks the build log for `e: ` and compile failures before reading any JUnit XML. None of the ten had a compile error.
- It restores the file from the saved copy, then confirms the file matches the committed forward change (`git diff --quiet`) and that the forward line is present. All ten restored cleanly.

Each failure below can only come from its own edit.

| # | Revert (one edit) | Failures | Specific message |
|---|---|---|---|
| R1 | the arrival branch in `ReturnWatch.onFix` disabled | 14 of 44 | "the return ended at the start"; drive-off "expected:<0> but was:<1>" (the 10-10 bug) |
| R2 | `arrivalJudgedFor` without the flag | 4 of 44 | Back by "arrived: the reminder is over expected null"; sundown "expected:<[]> but was:<[HEADS_UP, LEAVE_BY, SUNSET]>"; container Back by not ended |
| R3 | `AppContainer`'s Back by lambda put back to today's | 1 of 2 | "Back by ended on arrival, as it did while a return ran expected null" |
| R4 | must leave first removed | 4 of 44 | "still returning: the walker has not left the circle since Return" |
| R5 | GPS-only gate removed | 1 of 7 | "a network reading outside did not count as having left" |
| R6 | ViewModel never starts the hold | 4 of 29 | "the display holds Arrived" |
| R7 | the hold never closes | 1 of 29 | "the display closed by itself after 10 s" |
| R8 | HUD shows the live readout while held | 1 of 2 | "expected:<[Arrived]> but was:<[—]>" |
| R9 | the screen does not count the hold as navigating | 2 of 2 | HUD `assertExists` failed |
| R10 | the ring reads the live fix while held | 1 of 2 | "the ring at the start expected:<LatLng(…)> but was:<null>" |

Under R1, Back by and sundown's "still ends" tests pass. That is expected: without the arrival end, the return stays on and they behave as today. R2 and R3 are the checks that bite for "as today".

## Build

`assembleDebug` succeeded at `4216702d`. Every Gradle run went under `systemd-run --user --scope -p MemoryMax=5G` with `-Xmx1536m`, one at a time on the planner's go, after checking that no forecast fit was running. `./gradlew --stop` was run afterwards and no Gradle or Kotlin daemon was left.

## Not verified, and what to watch

- **ART register report, read on this build.** `scripts/verify-dex-register-budget.sh` is report-only (RECORD -741). It lists 9 methods over 256 registers:
  - `CompactMainScaffold` at 386 (baseline 385), and its lambdas each 1 or 2 higher;
  - `AvailabilityScreen` at 409 (baseline 402);
  - two `AvailabilityScreen` lambdas listed as NEW, at 334 and 329;
  - `JournalTab` at 292 (baseline 289), a file this change did not touch.

  The baseline is from `dc4b6c2e`, so part of the growth is drift since then. I did not build main for a like-for-like comparison. This change adds one parameter to each of `AvailabilityScreen` and `CompactMainScaffold`. The guard is `scripts/s22-launch-check.sh` on the S22, and it **must be run before merge**.
- **`MainActivity`'s two edits are not exercised by any test.** These are passing the flag through and the toggle closing the hold. No test in this repo composes `MainActivity`.
- **Sundown's container wiring has no container-level test.** It is the same expression as Back by's, which R3 pins. Sundown's behaviour with the flag is pinned at domain level (R2).
- **The arrival test depends on a start point.** The watch's start comes from the screen (`setStartPoint`: the origin waypoint, else the first breadcrumb). A recording whose screen never handed one over has no arrival end. The same is already true of off-track. Back by and sundown read the origin from the database, so the two can disagree slightly about where "the start" is if the origin waypoint and first breadcrumb differ. This was not measured.
- **The off-track rule, read during the build.** Off-track itself stops at arrival. The 2026-10-10 off-track came about 75 s after the watches judged arrival; this change ends the return at the first GPS fix inside the circle.
- **Real system-bar insets and the look of the display** are device-only, as always (CLAUDE.md, the Robolectric insets pitfall).
- **No real-touch test was added for the held display.** It is the same HUD composable at the same bounds; existing touch tests cover it while returning.
- **Waypoint navigation (T8)** never ends on arrival and has no drive-off alert (RECORD -845). It is unchanged and was not re-tested beyond the targeted classes.

## S22 check, to run later

Run on the S22 (SM-S908U, no SIM, GPS only) with a debug build of this branch. Run `scripts/s22-launch-check.sh` first.

1. **Launch (gate).** `scripts/s22-launch-check.sh` passes. Evidence: its output. If it fails, stop.
2. **Desk check: must leave first (gate).** Indoors or at a desk, start recording, wait for the start marker, tap Return.
   - Pass: the display stays up and does **not** say "Arrived" and close within the first minute. Some GPS jitter indoors may cross the 15 m circle and back, which would end it; if it ends, note how long it took.
   - Evidence: `adb shell run-as … cat files/return-record.log` shows `return-started` and no `return-ended … reason=arrival` in that minute, or note the jitter.
3. **Walk: arrival (gate).** Record, walk at least 50 m out, tap Return, walk back to the start.
   - Pass: within a few seconds of reaching the start, the display reads "Arrived" with the start ringed and the Return control as an X. About 10 s later it closes to the plain map by itself, and the recording indicator is still on.
   - Evidence: a timed note or a screen recording kept off GitHub, and `return-record.log` showing `return-ended track=… reason=arrival`.
4. **Drive-off (gate).** Drive away with the recording still on. Pass: no off-track notification. Evidence: `return-record.log` has no `went-off-track` after the arrival line, and no `off_track` post that evening.
5. **Back by (gate, if set).** Set Back by for a time after the walk is expected to end. Pass: `back-by-record.log` shows `ended … reason=arrival` within about 15 s of the return's arrival line, and no back-by alert after it.
6. **Exit during the hold (observation, not a gate).** Repeat 3 and tap X within the 10 s. The display closes at once and no new Return starts (`return-record.log` has no second `return-started`).
7. **Sundown (observation).** If the walk is inside the sundown window, no sundown alert comes after arrival.

## S22 launch check (run 2026-10-11, PR #214)

**Result: PASS.**

**What was tested.** The APK built at `4216702d`. The later commit `969004a6` changed only docs: `git diff --stat 4216702d 969004a6` shows `docs/audits/README.md` and this report. The APK's sha256 is `4a7fdcf8de6490183e9dff6e52687ad74f9edeee154043f263dbf1c1b2a6d3b8`, and the phone reports it installed as `versionName=1.0.3163+g4216702d`.

**How it was run.** `scripts/s22-launch-check.sh` on the S22 (R5CT321008R). The script's output:
- `Install: Success`;
- `compile -m verify -f: Success`, `dexopt: status=verify`;
- `LaunchState: COLD`;
- `PASS: verified (status=verify), launched, process 2789 alive after 8 s, crash buffer empty`.

**Logcat.** There is no `VerifyError` for the app. There are two info lines, both from process 2789: `Method exceeds compiler instruction limit: 21443 in void …AvailabilityScreenKt.AvailabilityScreen(…)`. That means ART will not compile that method ahead of time and runs it in the interpreter. The app kept running: process 2789 was still alive after the check, and the crash buffer is empty. Nothing in logcat names `CompactMainScaffold`. I did not compare with a build of main, so whether this line is new with this change is not known.

**What was not done.** No taps beyond the launch, and the S26 was not touched.

**Evidence.** Saved outside every repo, in `~/Zynergy/device-evidence/2026-10-11-return-arrival/`: `s22-launch-check.txt`, `apk.sha256`, `logcat-full.txt`, `logcat-verify-grep.txt` and `logcat-crash.txt` (0 lines).

## After merging origin/main (PR #213): tests and launch check again

**The merge.** `origin/main` `65bd8ee5` was merged into this branch as `ca0ced10`, a merge and not a rebase. The only conflict was the audit index, resolved by keeping both rows. The base had 336 rows and each side added one, so the merged index has 338. `AvailabilityScreen` kept #213's clock change and this branch's hold parameter, merged automatically.

**Targeted tests on the merged head: 73 tests, 0 failures.** That is 71 in one run plus 2 in a second run for `NavigationHudButtonPlacement360Test` and `NavigationHudButtonPlacement384Test`. The classes:
- this change's six: `ReturnWatchArrivalTest`, `ReturnArrivalAlertsTest`, `TrackRecordingSwipeAwayTest`, `AvailabilityScreenArrivalHoldTest`, `TrackRecordingServiceReturnArrivalTest`, `FileReturnRecordTest`;
- `AvailabilityScreenQuickSettingsTest` (15 tests);
- the HUD tests that build `MapQuickSettings`: `NavigationHudKindInStatusTest`, `NavigationHudQuickSettingsWidthTest`, `NavigationHudArrivedTest` and the two button-placement classes above.

assembleDebug succeeded.

**The S22 launch check was run again**, because the merged code differs from the build checked above. Result: **PASS.**
- APK sha256 `ac0bf846da4fc0efcdda1c85bbb28caa3bc12bbe6d693bcd9786b88cc78f9fec`, installed as `1.0.3169+gca0ced10`.
- `Install: Success`; `dexopt: status=verify`; `LaunchState: COLD`; `PASS: verified (status=verify), launched, process 5692 alive after 8 s, crash buffer empty`.
- Logcat has no VerifyError. As before, `AvailabilityScreen` exceeds the compiler instruction limit (21426, down from 21443), so ART runs it in the interpreter. The app still ran. Nothing in logcat names `CompactMainScaffold`.
- Evidence is in `~/Zynergy/device-evidence/2026-10-11-return-arrival/merged-ca0ced10/`.

## CI on d2a86f4b: WalkLoggerServiceTest red at line 148. Diagnosed, not fixed (stop)

**The failure.** CI run 38115289698 failed one test: `WalkLoggerServiceTest` > "switched on, the log starts with the recording, holds a wake lock, and ends with it", with `NoSuchElementException` at `WalkLoggerServiceTest.kt:148`. Main at 65bd8ee5 passed it.

**What line 148 is.** It is `file.readLines().first()`: the walk-log file exists and is empty. The test reaches it after `await { WalkLogger.of(context).isLogging }`. The failure is not at `walkLogFiles().single()` on line 147, so the file had been created.

**The code path** (debug source set):
- `WalkLogSession.start()` sets `active = true` (`WalkLogSession.kt:41`) right after `writer.open()` has created the file.
- It then writes the header through a `BufferedWriter`, which flushes only once `FLUSH_INTERVAL_NANOS` has passed (`WalkLogWriter.write`, `:65-78`).
- The header reaches the disk at the end of `start()`, when `onMinute()` flushes.
- So `isLogging` (`WalkLogger.kt:58`) is true for a short window in which the file exists but is empty. The test reads it from another thread and can land in that window.

**Neither of the two hypotheses fits.**
- (a), the stop race fixed in #213: this test sends no fixes and fails before Stop.
- (b), this branch: it changes nothing in the walk logger, in `TrackRecordingService`, or in `ReturnWatch.begin` and `end`. `git diff 65bd8ee5 HEAD -- app/src/debug app/src/main/java/com/zynergylabs/forager/app/service` is empty, and the only new testDebug files are this change's own tests.

**Evidence: runs of the whole class** (6 tests, `--rerun`, under the 5 GB memory cap with -Xmx1536m, and no forecast fit running):

| Side | Variant | Runs | Failed |
|---|---|---|---|
| branch d2a86f4b | unchanged | 20 | 0 |
| origin/main 65bd8ee5 | unchanged | 20 | 0 |
| branch | #213's `delay(200L)` in the service's fix collector | 5 | 0 |
| main | #213's `delay(200L)` in the service's fix collector | 5 | 0 |
| branch | `Thread.sleep(200L)` after `active = true` in `WalkLogSession.start` | 5 | 5, all at `WalkLoggerServiceTest.kt:148`, `NoSuchElementException` |
| main | same | 5 | 5, same line and exception |

Each delay was put in for these runs only. The file was restored from a saved copy, and `git diff` confirmed it matched HEAD afterwards.

**Conclusion.** The flake already exists on main: a start-up race between the logger's thread and the test, which neither delay variant links to this branch. It is rare unchanged (0 of 40) and certain once that window is widened, on either side.

**Fix not applied, because neither (a) nor (b) holds.** Two options for the owner or the planner:
1. In the test, wait for the header line before reading it, for example `await { walkLogFiles().singleOrNull()?.readLines()?.firstOrNull() != null }`, with a revert check using the delay above. No assertion is loosened.
2. In the logger, flush the header before reporting active. This changes what `isLogging` means, and on a phone it adds a flush at start.
