# Grid and level — completion report

**Date:** 2026-09-22 · **Branch:** `strip-grid-level` · **Base:** `1f19604`, the head of PR #112
(`strip-torch`), which is itself on PR #111. **Not `main`.** Both PRs were open when this branch was
cut (`main` at `89f53a4`), so the diff against `main` also shows their commits. · **Dispatch:**
"grid and level" (written 2026-09-22) · **Decision record:**
`2026-09-21-camera-strip-basics-decisions.md`, B7 added here.

**One-paragraph outcome.** The strip has a second chip, after the flash chip, that cycles Off,
Grid, Grid + Level. The mode persists in DataStore, default Off.
- **Grid:** a 3 by 3 grid of white lines with black outlines, over the preview and under both
  bands.
- **Level:** a horizon line that stays parallel to the real horizon and turns yellow within 1° of
  level. It's always shown at Grid + Level (a recorded departure from all four reference apps).
- **Level source:** its own `LevelProvider` on the rotation vector. The compass exposes heading
  only and was not changed. The sensor is registered only while the level line is on screen.

Camera set: **21 classes, 114 tests → 27 classes, 143 tests, 0 failures**, both runs fresh. The
full unit suite at the head is in the table below. Every new test failed first, except one
screen-level test written after the code and proven by its reverts. **32 one-line reverts and 1
positive control** each failed with a message specific to their own edit, on a clean compile. One
revert was built wrongly the first time; it was redone and the first result isn't counted. Nothing
has run on a device.

---

## Verify before building (at `1f19604`)

| # | Claim | Found | Holds |
|---|---|---|---|
| 1 | Nothing drawn over the preview besides the bands | `grep` for `Canvas`, `drawBehind`, `drawWithContent`, `grid`, `level` in the camera UI files: only "dialog-level" in two comments (`RotateWithDevice.kt:115`, `InAppCameraDialog.kt:338`) | yes |
| 2 | The compass provider's interface | `CompassProvider.heading: Flow<CompassReading?>` (`CompassProvider.kt:23`), **heading only**. `AndroidCompassProvider.kt:65–67` are the sensor lookups (rotation vector, accelerometer, magnetometer). Pitch and roll are computed at `:101` and dropped. Rate `SENSOR_DELAY_UI` (`:130–133`). Faked only by private classes inside two screen tests (`AvailabilityScreenMapIconStackTest.kt:3127`, `AvailabilityScreenBackNavigationTest.kt:1017`); there's no shared fake. | → **own `LevelProvider`** (step 2) |
| 3 | How the strip orders chips | `CameraStrip(chips: List<StripChip>)` lays them out in list order (`CameraBands.kt:141–160`); the list is `stripChips(session)` (`InAppCameraDialog.kt:447–449` at base) | yes |
| 4 | `FlashChip` as the pattern | reads `session.hasFlashUnit`/`flashMode` in composition, `OverlayIcon` + `rotateWithDevice`, one glyph mapping; tested with semantic clicks (wiring) and one real-touch dialog test (routing) | yes |
| 5 | The DataStore pattern | `DataStoreCameraOrientationPreferenceRepository.kt:16–34`: its own file, `PreferenceDataStoreFactory.create`, `Result`-returning suspend calls, a top-level default constant. **Injected** through `MainActivity.kt:77–78` as function references into `AvailabilityViewModel` (`:97–98`, loaded at `:138`). **Faked:** no interface fake; only the real DataStore test | yes; see "Decided beyond the dispatch" for how the grid mode differs |
| 6 | The decision record, and #112's entries | B1–B5, plus B6 added by #112 | yes |
| 7 | Where the preview sits relative to the bands | viewfinder `InAppCameraDialog.kt:236`, last child of the frame's `Box` (`:222–237`); strip `:282`, shutter band `:288` | yes; the overlay goes between them |

---

## Commits

| # | Commit | What | Dispatch step |
|---|---|---|---|
| 1 | `7a9b36d` | Decision record B7, appended | 1 |
| 2 | `a5ae29e` | `LevelProvider`, `AndroidLevelProvider`, `FakeLevelProvider` | 2 |
| 3 | `30371e8` | `GridMode`, `CameraGridModeRepository`, DataStore implementation, fake | 3 |
| 4 | `ed7d66f` | `GridOverlay` (standalone) | 4 |
| 5 | `cc8724d` | `LevelLine` (standalone) | 5 |
| 6 | `42e3dd3` | `GridChip`, `CameraGridModeViewModel`, wiring, grid and level placed | 6, plus the placement of 4 and 5 |

**Steps 4 and 5 are built standalone and placed in commit 6.** Placing them in the dialog needs
the grid mode to reach the dialog, and that plumbing is the chip's commit. Placing them earlier
would have meant building the plumbing twice, or placing them behind a dead default.

## Changes, file:line at `42e3dd3`

- **Level source.** `LevelProvider.roll` (`LevelProvider.kt:23`): degrees, clockwise-positive as the
  user looks at the screen, 0 upright, in `(-180, 180]`. `AndroidLevelProvider` registers at
  `AndroidLevelProvider.kt:49` (`SENSOR_DELAY_UI`, the compass's rate) and unregisters at `:50`
  (`awaitClose`). `rollDegrees` is at `:68`. The compass is untouched.
- **Store.** `GridMode` and `next()` at `CameraGridModeRepository.kt:11`/`:14`;
  `gridModeFromStored` at `DataStoreCameraGridModeRepository.kt:41`. An unknown stored name is a
  failure, not a silent Off. The container entry is at `AppContainer.kt:196`.
- **Grid.** `GridOverlay.kt:40`: 1 dp white inside 0.5 dp of black each side; no pointer input.
- **Level.** `LevelLine.kt:52`; `horizonAngleOnScreen` `:79`; `isLevel` `:87`; `OverlayLevelFill`
  (yellow) `CameraOverlay.kt:68`. `clockwiseDegrees` went from private to internal at
  `RotateWithDevice.kt:95`, so the window term is reused rather than copied.
- **Chip.** `GridChip.kt:29`, placed after the flash chip at `InAppCameraDialog.kt:463`.
- **Wiring.** `CameraGridModeViewModel.kt:27`, built at `MainActivity.kt:151`, passed at `:449`.
  From there: `AvailabilityScreen.kt:517` (defaulted params) → `:2153` → `InAppCameraHost` → the
  slot → `InAppCameraDialog.kt:141`. The production slot builds `AndroidLevelProvider` at
  `InAppCameraHost.kt:56`. The grid and level are placed at `InAppCameraDialog.kt:248–251`, before
  the strip (`:295`) and the shutter band (`:301`).

---

## Evidence: failing first, then the revert check

The revert runner saves a copy of the file, applies the edit, and refuses to cite results if the
build log has compile errors. It restores from the saved copy and confirms the file is
byte-identical to the forward version. This dispatch extended it to apply several edits at once,
for a revert that moves code. It refused four times: three compile errors and one malformed spec.
None of those was cited.

**Commit 2** (`CameraLevelProviderTest`). The provider test drives the framework's own
`getRotationMatrixFromVector` under Robolectric. Its rotation vectors are built from a physical
description: stand the phone up, then turn it about its screen. So a sign or axis error shows as a
wrong angle, not as agreement with the same arithmetic. Failed first: *"turned 20° clockwise
expected:<20.0> but was:<0.0>"*, *"expected:<30.0> but was:<0.0>"*, and the fake's *"expected:<[0.0,
12.5, -3.0]> but was:<[0.0]>"*. The no-sensor test passed on the skeleton, because that branch was
already real; revert d proves it.

The first green run found **one real edge**: *"upside down expected:<180.0> but was:<-180.0>"*. For
upside down, `atan2` got a negative zero and returned -180. It's now folded to +180, matching the
interface.

Reverts:
- sign flipped → *"turned 20° clockwise expected:<20.0> but was:<-20.0>"*
- fold removed → *"upside down expected:<180.0> but was:<-180.0>"*
- no unregister → *"unregistered once no one collects"*
- no-sensor emits 0 → *"expected null, but was:<0.0>"*
- fake `tilt` does nothing → the fake's message above

**Commit 3** (`DataStoreCameraGridModeRepositoryTest`). Failed first: three tests, *"expected:<Grid>
but was:<Off>"*. The default test passed on the skeleton; revert c proves it. Reverts:
- the write removes the key → round trip *"expected:<Grid> but was:<Off>"*
- unknown name becomes Off → *"an unknown name fails"*
- default becomes Grid → *"expected:<Off> but was:<Grid>"*. The first attempt's spec had a quoting
  error; the runner stopped before editing, and the file was confirmed untouched.
- fake ignores `failWrites` → first a bare `AssertionError`, because the assertion had no message.
  The message was added and the revert rerun: *"a write the fake was told to fail reports failure"*.

**Commit 4** (`CameraGridOverlayTest`, a 300 by 450 dp preview). Failed first: Grid and Grid + Level,
*"could not find any node … in-app-camera-grid-v1"*. The Off test passed on the skeleton; revert a
proves it. Reverts:
- always draw → *"Did not expect any node but found '1' … in-app-camera-grid"*
- quarters instead of thirds → *"first vertical at a third of the width expected:<100.0> but was:<75.0>"*
- no grid at Grid + Level → *"could not find any node"*
- no outline → *"a line is the line plus its outline wide expected:<2.0> but was:<1.0>"*

**Commit 5** (`CameraLevelLineTest`). All five failed first against a skeleton that always drew a
flat, never-snapped line and never read the sensor. Reverts:
- shown at Grid → *"Did not expect any node but found '1' node"*
- sensor collected before the mode check → *"no line, no listener expected:<0> but was:<1>"*
- sign flipped → *"expected:<-12.0> but was:<12.0>"*, and *"expected:<0.0> but was:<180.0>"*
- window term dropped → *"expected:<0.0> but was:<90.0>"*
- snap near 0 only → *"level in landscape"*
- 2° threshold → *"1.5 is not"*
- no reading draws a flat line → *"Did not expect any node but found '1'"*

**Commit 6.** The skeleton run first failed to compile. `WindowOrientationTest` is a fourth harness
that builds the dialog, which I'd missed. After fixing that there were 11 failures:
- **10 as predicted:** four chip tests, four view-model tests, and the dialog's grid and level tests.
- **1 not predicted:** *"the grid chip at the strip's start expected:<0.0> but was:<4.0>"*. That was
  my assertion being wrong: the chip's node is 40 dp inside its 48 dp touch target. It's corrected
  to "the first slot".
- **2 passed on the skeleton:** "after the flash chip" and "at Off no grid". Reverts f and j prove
  them.

Reverts:
- load dropped → *"expected:<GridLevel> but was:<Off>"*, and the chip's *"(ContentDescription =
  [Grid and level on])"*
- mode shown before the write → *"nothing stored, nothing shown expected:<Off> but was:<Grid>"*, and
  the chip's *"(ContentDescription = [Grid off])"*
- write failure not logged → *"logged, not swallowed expected:<1> but was:<0>"*
- tap asks for the same mode → *"written to the repository expected:<Grid> but was:<Off>"*
- glyph pinned to Off → *"(ContentDescription = [Grid on])"*
- grid chip before the flash chip → *"after, along the strip: DpRect(left=60…) then DpRect(left=4…)"*
- grid and level not placed → *"could not find … in-app-camera-grid"*
- grid not placed, level kept → the same
- **grid moved above the strip.** The first attempt was **invalid**: my spec added a second grid
  instead of moving the first, and its *"found '2' nodes"* belongs to the duplicate, so it isn't
  counted. Redone as a real move: *"placed before the strip: [in-app-camera-strip,
  in-app-camera-grid, in-app-camera-shutter-band]"*.
- Off draws the grid → *"Did not expect any node but found '1' … in-app-camera-grid"*
- screen passes Off instead of the mode → *"(ContentDescription = [Grid on])"*
- screen drops the callback → *"expected:<[GridLevel]> but was:<[]>"*

**Positive control for the listener lifecycle.** No line of this change is what releases the
sensor; composition does. So a listener was leaked deliberately:
- The first attempt didn't compile, and was refused.
- The second leaked while the camera was open, and was caught at *"listening while open
  expected:<1> but was:<2>"*. That shows the test notices an extra listener, but not the
  outliving case.
- The third starts a listener only as the camera closes, and was caught at *"released once closed
  expected:<0> but was:<1>"*.

**Written after the code:** the screen-level test (`AvailabilityScreenInAppCameraTest`, the grid
mode reaches the camera and a tap reaches the screen's callback). It was added once it was clear
that nothing held the screen's wiring. It didn't fail first; its two reverts are what prove it.

## Suite counts

| Run | Commit | Classes | Tests | Failures | Skipped |
|---|---|---|---|---|---|
| Camera set (`--tests '*Camera*'`), branch point | `1f19604` | 21 | 114 | 0 | 0 |
| Camera set, branch head | `42e3dd3` | 27 | 143 | 0 | 0 |
| Full unit suite, branch head | `42e3dd3` | 211 | 1630 | 0 | 24 |

Both camera runs deleted the results directory first. Neither build log has compile errors. The
branch-point run finished (`09:02:34`) before the first new file was written (`09:02:39`), on a
clean tree. The +29 tests are the six new classes (4 + 4 + 5 + 4 + 3 + 5 = 25), 3 new
`InAppCameraDialogTest` tests and 1 new `AvailabilityScreenInAppCameraTest` test.
`WindowOrientationTest` isn't matched by the filter; its harness changed, and the full suite covers
it. The full suite is +6 classes and +29 tests against #112's head (205 / 1601), the same delta as
the camera set, so nothing outside the camera moved. Its 24 skips are pre-existing. `@Ignore`
count: **53 at `1f19604`, 53 at `42e3dd3`**.

---

## What could not be tested, and why

- **The real sensor.** The provider is tested against the framework's matrix code with synthetic
  vectors, but not against a real rotation-vector sensor. The sign of a real tilt, the rate, and
  any lag are device checks.
- **Drawing order.** The tests read *placement* order from the frame's semantics children (grid
  before the strip and the shutter band). That the pixels land in that order is device step 1.
- **`MainActivity`'s wiring.** The factory that builds `CameraGridModeViewModel` over the container,
  and the collection of its mode, run only in the Activity. The screen's half is tested; the
  Activity's half isn't.
- **Persistence across a restart.** The DataStore round trip is tested inside one process; a real
  restart is device step 6.
- **The phone held flat.** `rollDegrees` is undefined there (both in-screen components go to zero),
  so the line follows noise. Nothing hides it, because no data says where a threshold belongs.
  Device step 8 is that data.

## Device-only, cheap to costly

Pass condition and evidence per step. Observations are not gates.

1. Open the camera and tap the grid chip once. **Pass:** a 3 by 3 grid over the preview, the strip
   and the shutter drawn above it, and the chip showing the grid icon. **Evidence:** screenshot.
2. Tap again. **Pass:** a horizon line through the centre, and the chip showing the ruler icon.
   **Evidence:** screenshot.
3. Tilt the phone about 20° left, then right. **Pass:** the line tilts the opposite way and keeps
   up without visible lag. **Evidence:** two screenshots, plus an observation on lag. The rate is
   `SENSOR_DELAY_UI`, and that's the first thing to raise if it lags.
4. Bring the phone level. **Pass:** the line turns yellow within about a degree, and white again
   past it. **Evidence:** screenshot.
5. Turn to landscape, lock setting off, then on. **Pass:** the grid still divides the preview into
   thirds. The line stays parallel to the real horizon in both, and turns yellow when the phone is
   level in landscape. With the lock on the window stays portrait, so the level line is
   **vertical on screen**, which is correct. **Evidence:** a screenshot for each.
6. Close the camera and reopen it, then force-stop the app and reopen the camera. **Pass:** the mode
   is remembered both times. **Evidence:** screenshots.
7. *Observation, not a gate:* preview frame rate with Grid + Level on, compared with Off.
8. *Observation, not a gate:* hold the phone flat over a scale card and note what the line does
   near 90° of pitch. Expected: it wanders, since the angle is undefined there. This is the input
   for the top-down crosshair (B7d).

---

## Conventions

Reference apps: **Google Pixel Camera, Apple iPhone Camera, Samsung Camera, Open Camera.** What each
app does is the planner's recollection, unverified.

| Convention | This build |
|---|---|
| All four keep the 3 by 3 grid as a setting, so it persists | **Followed**: DataStore, default Off (B7a) |
| All four show the level only near horizontal or vertical | **Departed**: always shown at Grid + Level, so a forager sees how far off they are (B7b) |
| None of the four rotates the grid with the glyphs | **Followed**: the grid isn't rotated (B7c) |

**Snapped state (open item): colour, yellow** (`OverlayLevelFill`). A change of fill is what the
overlay rule says carries meaning. A thicker line would move its own edges while the user is lining
something up against them. Yellow reads against both the white default and green woodland, and red
means an error here. That Pixel and iPhone use yellow for level is recollection, not checked.

**Level source (open item): its own provider.** The compass is heading only and was not to be
changed.

**Glyphs:** `GridOff`, `GridOn`, and `Straighten` for Grid + Level. Material has no
grid-with-level icon; `Straighten` (a ruler) was chosen by name, not compared with any reference
app.

## Decided beyond the dispatch

- **A ViewModel of its own for the grid mode** (`CameraGridModeViewModel`), rather than
  `AvailabilityViewModel`, where the lock setting lives. The grid mode is written only from inside
  the camera, and a class of its own is testable headless with three arguments, where
  `AvailabilityViewModel` takes about twenty. Not `InAppCameraViewModel` either: it has no
  dependencies by design, and eleven headless tests build it bare.
- **The chip shows the stored mode, not the tapped one.** A change is shown only after its write
  succeeds, which is the lock setting's pattern turned the other way round. This is what "reflects
  repository state" requires.
- **"Level" is within 1° of the nearest quarter turn**, not only of 0. So a phone level in
  landscape with the lock on is level. The dispatch's 0.5 and 1.5 cases hold.
- **The line turns opposite to the phone.** The dispatch's test said "line angle equals fake roll".
  The line's angle is the *negative* of the roll at `ROTATION_0`, which is what device step 3
  expects ("tilts the opposite way"). The tests assert that.
- **No reading, no line**, rather than a flat line that would look level.
- **The grid and level draw only once the camera is Ready**, so they never appear over the spinner
  or the unavailable message.
- **An unknown stored grid name is a failed read**, logged, not a silent Off.
- **`clockwiseDegrees` made internal** (`RotateWithDevice.kt:95`) to reuse, not copy, the window
  term. **`OverlayLevelFill` added to `CameraOverlay.kt`** beside the other fills.
- **Two doc comments corrected:** `CameraBands.kt`'s "on a camera with no flash unit there is
  nothing to put here", and the dialog's `stripChips` comment. The grid chip is always present, so
  the camera's strip is never empty now.
- **One test's claim changed**, disclosed: the no-flash-unit dialog test went from "no strip at
  all" to "the grid chip comes first". Its old claim can no longer be true in the camera. The empty
  case is still held at the container (`CameraStripTest`).
- **Five test harnesses** gained the new parameters: `InAppCameraDialogTest`,
  `InAppCameraDialogLandscapeTest`, `WindowOrientationTest`, `InAppCameraHostTest` and
  `AvailabilityScreenInAppCameraTest`.

## Premises that were wrong or incomplete

- **Verify step 5's "how it is faked":** the lock repository has no fake; it's tested only through
  real DataStore. The grid repository has both, and the DataStore test holds the fake to the same
  round trip.
- **Build step 5's "line angle equals fake roll":** opposite sign, as above.

## Constraints, checked

The torch, the flash chip and `CameraCaptureSession` aren't in the diff. The compass provider and
compass UI aren't in the diff. The level's listener registers when collected and unregisters when
collection stops; it's collected only while the line is shown, and the line exists only while the
camera is open (the positive control above). There's no top-down crosshair. No existing DataStore
default changed; the grid mode's default, Off, is new. The decision record wasn't edited in place;
B7 is appended.
