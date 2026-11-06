# Barometer height: the S22's false end-of-walk drop, fixed (RECORD -889)

Branch `journal-entry-height`. PR #219 is held, not merged. Builds ran on CI only, through the run-this-branch trigger. No phone was touched.

## The failing case first

- **Fixture.** `app/src/test/resources/walks/s22-walk-596f5e3d-no-location.json`, committed byte-identical to the file the planner gave (checked with `cmp`). It holds 69 points with relative times, GPS altitude and pressure only. It carries no coordinates and no absolute times.
- **Test.** `domain/S22WalkHeightTest.kt` runs the walk through the chart's own rule, `heightProfileOf`.
- **Bounds, stated before the first run:** end minus start within ±3 m, and spread at most 6 m. For comparison, the S26 on the same stretch read a 5.3 m spread (RECORD -887).
- **Failing run.** Run 38177559844 on `359ca84d`: compile errors 0. It failed with `end minus start -9.44 m, bound ±3 m`, which matches the value a port of the code predicted before the run. The same port gives a spread of 12.53 m. That number was not printed on CI, because the first assertion stops the test.

## The planner's inference, checked against the code (`domain/BarometricHeight.kt` at `69309a18`)

It is confirmed. Three things combine:

1. **The median shrinks to one reading at the walk's ends.** `medianAround` widens its window only while there is a neighbour on both sides. At the first and last points the window is one reading, so the last height is the raw last reading. In the port, the median-only series ends at -8.76 m, exactly the raw last reading's height.
2. **The 2 m/s step limit never fires on this walk.** The final step, -0.21 m to -8.76 m over 8 s, is about 1.07 m/s, under the limit.
3. **The anchor added little.** It contributed only -0.68 m, which is why the drawn end is -9.44 m rather than -8.76 m.

One further cause the inference did not name: even away from the ends, a 5-point median spans only about 25 to 40 s at this phone's 5 to 8 s point spacing. That is too short to settle swings of about ±10 m.

## The fix (`d96d68ad`)

- **Time windows, not a sample count.** Within each unbroken run of readings: a median over the readings within 60 s each side, then a step limit, then a mean over the same 60 s window.
- **The ends keep the window's full width in time,** using whatever readings exist on the one side. So the last height is the median and mean of the last minute, never a single reading.
- **The step limit is now 0.5 m/s** (it was 2 m/s). Briskly climbed steep stairs are about 0.3 m/s.

The limit acts on the median, before the mean. On the mean, a lasting jump would already be spread into a gentle ramp the limit could not catch.

## This walk, before and after

| | End minus start | Spread |
|---|---|---|
| Before (CI run 38177559844, plus the port for spread) | -9.44 m (about -31 ft) | 12.53 m |
| After (CI run 38178570116, printed to the JUnit XML system-out) | **-1.20 m** | **1.59 m** |

## Revert check

Commit `bc28334f` made two one-line edits, labelled (a) and (b): (a) put the window back to 15 s, and (b) put the limit back to 2 m/s.

The port predicted the outcome before the run:
- `S22WalkHeightTest` fails at -7.04 m;
- the new lasting-step test fails at a spread of 9.04 m;
- every other case passes.

Run 38178276058: compile errors 0, and exactly those two tests failed, with -7.04 m and 9.0412 m.

The fix was restored from a saved copy in `2c840b83`. An empty `git diff d96d68ad HEAD` confirmed the forward change was back.

## Full suite at the head

Run 38178570116 on `035fcbf1`: headSha matches, compile errors 0. 612 suites, 4690 tests, 0 failures, 24 skipped (the CI allowlist's own check passed).

## Assertions changed

- **New, in `BarometricHeightTest`:**
  - A lasting 1 hPa step for five minutes is taken out, along with its step back. The old 2 m/s limit let it through.
  - Brisk stairs, 36 m at 0.3 m/s, pass: Climb reads 36 m ± 2.
- **Unchanged:** every existing bound. The real climb is still 50 m ± 6; the port gives 45.35 m, against 45.3 m before. The flat-walk spread is still bounded at 10 m.
- **Comment-only updates:** the worked figures in three comments: flat walk 6.4 to 6.2 m, weather climb 8.0 to 4.0 m, real climb unchanged.

## Not verified

- **How the chart looks on the S22 itself.** The planner re-checks it.
- **The windows are starting values.** At 45 s, the port still shows the S22 walk ending 6.3 m low; 60 s is the narrowest window tried that meets the bounds.
- **A walk that ends partway up a steep climb** has its last minute pulled toward that minute's middle, by up to about half a minute's climb. That case has not been tested.
