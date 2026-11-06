# arrived-alone: "Arrived" stands alone, and "Approaching" gets its own 100 m zone

Dispatch 2026-09-28-578 (RECORD -578, `prompts/preserved/2026-10-06-07.md` on `records-after-173`),
with Amendment 1 (RECORD -579). Branch `arrived-alone`, cut from `origin/main` at `f4b6b98e`, with
`main` at `730a7a12` (PR #179, docs only) merged in as `b1f888a5`. Not merged; no pull request opened. Laptop coder,
no phone, no adb.

## What the walker sees now

- **Arrived** (fresh fix): the large slot reads "Arrived" and the status line is empty. Arrival is
  unchanged: within max(15 m, 2 × accuracy), straight line.
- **Arrived, GPS stalls** (30 s to 5 min): "Arrived" dimmed, as before, with "Last fix 33 s ago" or
  "Last fix 2 min ago" under it.
- **Within 100 m, not arrived**: the distance counting down in the large slot, the needle still
  pointing, "Approaching" under it. When stale, "Approaching · last fix N ago".
- **Further than 100 m**: as before.
- **No fix for 5 min**, and the approximate HUD: as before.

## Verify first, and the stop

The first verify (code only, at `f4b6b98e`) stopped on item 3. "Approaching" was decided by
`isApproaching` (`domain/NavigationReadout.kt:46-49`, d ≤ 2 × accuracy, accuracy required). Arrival
(`:61-62`) is d ≤ max(15 m, 2 × accuracy), which always holds that test. Both read the same fix,
target and clock, and both are off on a lost fix. So no state ever showed "Approaching" without
"Arrived", and dropping it when arrived would have removed the word from the app. Seven assertions
showed "Approaching" while arrived, not the "two tests" the owner had been told about.

The owner, through the planner (RECORD -579): "Give Approaching its own zone", then "100 m / 330 ft
(Recommended)". A re-verify found 8 status assertions that change: the 7, plus the 16.46 m case,
which had not arrived and becomes "Approaching". The planner accepted the design below.

## What changed

All app paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`.

- **`domain/NavigationReadout.kt`**:
  - Added `APPROACHING_ZONE_METERS = 100.0` and `isInApproachingZone(d) = d <= 100.0`, beside
    `ARRIVAL_MIN_RADIUS_METERS`, with a doc comment quoting the owner.
  - `isApproaching` is not renamed and its body is unchanged. Its doc now says it no longer decides
    the word.
- **`ui/availability/NavigationHud.kt`, `navigationReadout`**:
  - The old local `approaching` is renamed `withinFixError`. It still gates the needle and the blank
    target column, and nothing else, so neither changes.
  - A new `approaching` = not lost && !`arrivedAtStart(...)` && `isInApproachingZone(distance)`.
  - In the status line's FRESH branch, `arrived -> ""` now comes first. STALE reads the new
    `approaching`, so arrived gives "Last fix N ago".
  - The large-slot arrival branch (`:586` at base) is not touched.
  - The class doc carries a superseding note on the needle paragraph.

**Why the needle stays on twice-accuracy, not on arrival** (accepted by the planner):

- (a) The dispatch keeps everything outside the status line unchanged. Arrival is wider: on the S22,
  whose accuracy reads a constant 3.79 m, tying the needle to arrival would hide it from 15 m out
  instead of 7.6 m.
- (b) With no accuracy reported, arrival still fires within 15 m. That would break the "explicit
  unknown" rule at `NavigationReadout.kt:22-26` and its test.
- (c) The needle is hidden there because the bearing is noise inside the GPS error circle. That is a
  fact about the fix, not about arrival.

### Consequences of the ruling, stated as such

These were raised to the planner, who ruled that they follow from the owner's words ("Drop
"Approaching" when arrived", "Arrived" alone, and a fixed "100 m / 330 ft"):

1. **Arrived with a route and outside twice the accuracy** (on the S22, 7.6 m to 15 m) used to show
   "Straight line X" under "Arrived". It now shows nothing: "Arrived" alone.
2. **"Approaching" now shows with no reported accuracy**, between 15 m and 100 m. The zone is a fixed
   distance. Before, the word never showed without an accuracy.
3. Already true before this change and left as it was: between twice the accuracy and 15 m, "Arrived"
   shows with the needle and "Turn N°" still drawn.

## Tests (seen failing first)

Tests were committed at `0a752a4a` and run on main's code: 144 tests, **13 failed**. Each failure is on
the status line for the predicted reason: "Approaching" where "" was expected; "" or "Straight line …"
where "Approaching" was expected; "Approaching · last fix 45 s ago" against "Last fix 45 s ago".

**Assertions changed** (all on the status line, each with a dated "Before / After" comment in the test):

In `NavigationHudReadoutTest`:
- `:83` → a new 50 m target asserting "Approaching", and the 10 m case asserting "".
- `:215` → "". The test is renamed.
- `:250` (r1) → "".
- `:259` (r2) → "Approaching". The test is renamed, and its doc no longer claims one shared threshold.
- `:295` → "Last fix 45 s ago". The test is renamed.
- `:334` → "".
- `:372` → "". The test is renamed, and a 50 m case is added asserting "Approaching" under an
  unreliable compass.

In `AvailabilityScreenMapIconStackTest`:
- `:728` → "", plus "Approaching" absent from the screen.

No other assertion was removed or weakened.

**New tests:**

In `NavigationHudReadoutTest`:
- In the zone and fresh: "Approaching", "≈ 50 m", needle at 315°, "Turn 315°".
- The zone ends at 100 m: 99.07 m gives "Approaching", 101.08 m gives "", both with and without a
  route.
- Arrived with a route, outside twice the accuracy: "Arrived", status "".
- No accuracy at 50 m: "Approaching", needle drawn. This one tells apart the two reasons the `:271`
  test reads "".
- Stale in the zone: "Approaching · last fix 45 s ago", needle drawn.

In `AvailabilityScreenMapIconStackTest`:
- In the zone and not arrived, on the real screen: "Approaching", no "Arrived".

The code was committed at `18cda007` (green). The four classes passed with 0 failures:
`NavigationHudReadoutTest` 36, `AvailabilityScreenMapIconStackTest` 108 (19 skipped, as before),
`NavigationReadoutTest` 5, `ArrivalTest` 3.

## Revert checks

Each revert was one edit to committed code, run on the two HUD classes (144 tests).

How the runner worked:
- It saved a copy of the file before editing, deleted the old JUnit XML before the run, and refused
  any results if the build log showed compile errors (none did).
- It restored the file from the saved copy, not from git, and asserted the file was byte-identical to
  that copy.
- After each check, `git status` was clean against the committed forward change, and a grep confirmed
  the forward lines were present.

Every failure listed is one that check's edit could cause.

| # | Reverted | Failures (each specific to the edit) |
|---|---|---|
| R1 | Removed `arrived -> ""` from FRESH | 3: the arrived-with-route cases read "Straight line within 41 ft", "Straight line ≈ 50 ft", "Straight line within 13 m" |
| R2 | Removed `!arrived &&` from the zone test | 1: stale and arrived read "Approaching · last fix 45 s ago" |
| R3 | Zone back to `isApproaching` | 8: every in-zone, not-arrived case reads "" or "Straight line …" instead of "Approaching" |
| R4 | `APPROACHING_ZONE_METERS` 100 → 1000 | 1: 101.08 m reads "Approaching" |
| R5 | Needle on the new `approaching` | 6: needle drawn inside twice the accuracy (expected null, was 315.0); in-zone needle absent (NPE on `targetArrowDegrees!!`) |
| R6 | Target column on the new `approaching` | 7: "Turn 315°" / "Turn 85°" inside twice the accuracy; "" in the zone |

## Full suite

- **Before**: 3,817 tests, 24 skipped, 3 failures. Those 3 were my own first edits to existing tests,
  which the baseline run compiled because I edited test files while it was compiling. No test had been
  added yet, so the count is main's. The other 3,814 passed. That run is therefore not a pure-main
  baseline; it was not re-run.
- **After** (merged tree, `main` at `730a7a12` in): **3,823 tests, 0 failures, 0 errors, 24 skipped**.
  That is 3,817 + 6 new tests.

## Not done or not verified

- No phone. The look on a real HUD has not been seen.
- The before-suite was contaminated as described above.
- The merge commit of `origin/main` carries git's default message, without the Co-Authored-By line.
  Not amended (push-before-you-tidy).
- Other places that say "Approaching" are unchanged on purpose: comments in `LiveFixGate.kt`,
  `CompassTrustJudge.kt`, `DistanceUnit.kt`, and the approximate HUD's `far`
  (`ApproximatePositionHud.kt:78`, still `isApproaching`).
