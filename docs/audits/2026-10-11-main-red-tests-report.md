# Main's two red tests (RECORD -848, -850)

Branch `main-red-tests`, cut from `origin/main` at `58fbc642`. Neither failure came from PR #212.
Both causes are in the test path. No user-visible behaviour changed.

## 1. `AvailabilityScreenQuickSettingsTest` > "Pick a time, an afternoon hour picked …"

**Cause.** The picker read the real clock, not the screen's pinned one. `MapQuickSettingsButton` had a
`currentTime` parameter that defaulted to the system clock (`MapQuickSettings.kt:163-164` at `58fbc642`).
The picker's starting time read it (`:203`). None of the three callers passed it:
`AvailabilityMapControlsUi.kt:858`, `NavigationHud.kt:415` and `:517`. So the test's
`CurrentTimeProvider { now }` (18:00 UTC) never reached the picker. The dial opened at the real time
plus an hour, in the pinned UTC zone. When that hour is AM, tapping "1 o'clock" gives hour 1, not 13.
By arithmetic, the test fails when the real time is between 23:00 and 10:59 UTC and passes between
11:00 and 22:59. Only the night side was observed: CI at 03:04 and 03:17, and here at 04:59 and 05:01.

**Planner's guess: confirmed.** The CI XML (run 38106745197, both attempts) reads `expected:<[AtTime(hour=13,
minute=55)]> but was:<[AtTime(hour=1, minute=55)]>`. The back-by-fires report's "minutes dial was not
reached" was a different, earlier failure. `e8b65f4f` fixed it, and the CI failures come after the dial.

**Fix.** `MapQuickSettings` now carries the clock (`currentTime`, defaulting to the system clock), and
`AvailabilityScreen` passes its own `currentTime`. All three buttons now read the clock the screen was
given. The button's unused parameter is gone. In production the screen gets `container.currentTimeProvider`
(`MainActivity.kt:542`), which is `SystemCurrentTimeProvider` (`AppContainer.kt:219`), the same clock the
default read. So the app behaves exactly as before.

## 2. `WalkLoggerServiceTest` > "storage below 200 MB stops the log, never the recording"

**Cause.** A race in the test, not disk space. The test already replaces the free-space reading
(`WalkLogger.freeBytesOverride = { 1_000L }`), and the CI failure came after the storage-low assertions
had passed: line 231, "the recording kept its points". The test sent two fixes and then `ACTION_STOP`
straight away. The service takes fixes in a coroutine on `Dispatchers.Default`
(`TrackRecordingService.kt:96`, `:209-244`). `stopRecording` cancels that coroutine at once (`:363-366`).
If the collector thread had not yet taken the fixes, the track ended with no points. That failure took
10.3 s, which is the `await` timeout, consistent with a condition that never became true. The swipe-away
and watch-failure tests already avoid this race by waiting for twenty stored points.

**Planner's guess (real free disk space): refuted** by the override and by the failing line.

**Fix (test only).** The test now sends twenty fixes (the service's write batch) and waits until twenty
points are stored before it stops the recording. After the stop it asserts the track ended with exactly
those twenty points. The failure message now prints `ended=` and `points=`. The original message could
not tell "not ended" from "no points".

## Reproduction and revert checks

Runs used `systemd-run --user --scope -p MemoryMax=5G`, Gradle `-Xmx1536m`, and only the two classes.
Before each run the runner deleted the old results, and before reading any results it checked the build
log for compile errors (none in any run). The fork-and-restore copies were saved before editing, and
everything was restored from those copies. After the last run, `git status` was clean against `62e91e07`.

For (2), the race was made reproducible with one line added before the runs and removed after:
`delay(200L)` at the top of the collector's `Update` branch in `TrackRecordingService`, standing for a
slow collector thread. This is the same condition a loaded CI runner would create, but that CI was slow
in this way is **inferred, not observed**.

| Run | State | Picker test | Storage-low test |
|---|---|---|---|
| A, 04:59Z | `origin/main` code, with the delay | FAIL: `but was:<[AtTime(hour=1, minute=55)]>` (CI's message) | FAIL: "the recording kept its points", 10.375 s (CI's message and timing) |
| B, 05:00Z | fix, with the delay | pass | pass (4.4 s) |
| C, 05:01Z | fix, revert R1 (drop `currentTime = currentTime,`) and R2 (drop the wait for points), with the delay | FAIL: `AtTime(hour=1, minute=55)` | FAIL: `ended=1791694871615 points=0` |
| E, 05:01Z | fix, restored, no delay | pass (15/15) | pass (6/6) |

Run C confirms the mechanism for (2): the track ended with zero points.

## Not done

- Only these two classes were run, not the full suite. The `NavigationHud*` tests that build
  `MapQuickSettings` compiled but were not run.
- The picker test's daytime side was not run here. That it passes then comes from CI history (PRs #210 and
  #211) and the arithmetic. With the fix the test no longer reads the real clock, so the time of day does
  not matter.
- R2 bites only with the slow-collector line in place. Without it, the reverted test usually passes on
  an idle machine, which is the flake itself.
- Not run on a phone. Nothing changed for the user.
