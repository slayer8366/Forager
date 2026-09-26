# Camera strip Part B: flash on capture, self-timer, save-location — completion report

**Date:** 2026-09-26
**Branch:** `strip-flash-timer-location`, from `origin/pre-main` at `2753a83` (merge of #122)
**Dispatch:** `prompts/preserved/2026-09-26-28.md`; intent `2026-09-26-81` in `RECORD.md`
**Decisions:** B8 in `docs/audits/2026-09-21-camera-strip-basics-decisions.md`
**Device check:** not run. Deferred to promotion by the owner's ruling: *"merge into pre main
without device check. defer device check for later"*. Every device item is listed below.

## What was built

The strip is now **Flash, Timer, Grid, Location**. The flash chip is hidden without a flash
unit, as before; the other three are always shown.

- **Flash on capture (F).** `FlashMode` is Off, Auto, On, Torch, a tap cycling in that order.
  `CameraXCaptureSession.setFlashMode` sets the torch (lit only at Torch) and the installed
  `ImageCapture`'s flash mode (Off and Torch `FLASH_MODE_OFF`, Auto `FLASH_MODE_AUTO`, On
  `FLASH_MODE_ON`). The torch-failure resync moved into `onTorchRequestFailed`, which the
  `enableTorch` listener delegates to: a lit torch reads Torch, an unlit torch contradicts only
  Torch, and Auto and On stand. Glyphs `FlashOff`, `FlashAuto`, `FlashOn`, `FlashlightOn`.
  `installImageCapture` is unchanged; nothing carries a mode into a later bind (planner ruling 3).
- **Self-timer (T).** `photo/CaptureCountdown.kt` holds `TimerMode` (Off, 3 s, 10 s) and
  `CaptureCountdown`, plain Kotlin with no Compose or Android UI imports. `InAppCameraDialog`
  holds one countdown with `remember` on its `rememberCoroutineScope()`. The setting is
  `rememberSaveable` for the camera session, default Off. A shutter press with the timer set starts
  the countdown; at zero it runs the existing capture path unchanged. A press during a countdown
  cancels it with no capture, and meanwhile the shutter's content description is "Cancel timer".
  Back cancels, then closes as before. The numerals are a `displayLarge` `OverlayText`, centred,
  under `rotateWithDevice`, with no pointer input. The chip is `TimerChip.kt`.
- **Save-location chip (L).** `LocationChip.kt` shows `uiState.autoSaveLocationToPhotos` and
  calls `onAutoSaveLocationToPhotosChanged` with the toggle. Both are threaded from
  `AvailabilityScreen` through `InAppCameraHost`, the `InAppCameraSlot` typealias and
  `CameraXInAppCamera` to `InAppCameraDialog`, as required parameters. `AvailabilityScreen`'s own
  signature is unchanged (it already had both), and so is `MainActivity`. There is no new
  repository and no DataStore key. Content descriptions "Save location: On" and "Save location:
  Off".

Every icon resolved at compile time (B8f). `LocationOn` comes from `material-icons-core`, and the
other eight from `material-icons-extended`, version 1.7.8 in the Gradle cache.

## Commits

| Commit | What |
|---|---|
| `6d25662` | Sweep: merge entry `2026-09-26-80` for #122 |
| `bdf351f` | Intent `2026-09-26-81`, with the store copy |
| `cd38624` | F, tests first (compile failure on the new API) |
| `74df24e` | F, stub (old behaviour; tests fail at run time) |
| `ed5c23a` | F, implementation |
| `ca34239` | T, tests first, including the order test changed by planner ruling 1 |
| `2c56736` | T, stub, with one test fix (Back test) |
| `0249643` | T, implementation, with test-harness fixes from probes |
| `29ae60a` | L, tests first, every caller updated for the new parameters |
| `03f8abf` | L, stub (chip declared, not placed) |
| `f252304` | L, implementation |
| `5b98487` | B8 addendum |

## Evidence: failing first

**How tests-first works in this build (a coder's choice, recorded in the intent's Notes).** Each
feature has three commits. The **tests-only** commit fails to compile, and the build log is checked
to confirm every error names only the new API. A **stub** commit then declares that API with no
behaviour, so the same tests fail at run time for their behavioural reasons. The
**implementation** comes last. Every run below had a build log with no compile errors unless it
says otherwise.

**F.**
- **Tests only:** 35 compile errors, all `Auto`, `On`, `onTorchRequestFailed`,
  `FLASH_AUTO_LABEL`, `FLASH_ON_LABEL`, or inference downstream of them.
- **Stub:** 3 classes, 41 tests, 7 failures:
  - *"Auto: the ImageCapture's flash mode expected:<0> but was:<2>"* (CameraX's constants are
    AUTO 0, ON 1, OFF 2);
  - *"expected:<Auto> but was:<Torch>"*;
  - *"Auto stands expected:<Auto> but was:<Off>"*;
  - *"precondition: On reached the first ImageCapture expected:<1> but was:<2>"*;
  - *"tap 1 lands on Auto expected:<Auto> but was:<Torch>"*;
  - the glyph label *Flash auto* vs *Flash off*;
  - *"five taps from Off end on Auto expected:<Auto> but was:<Torch>"*.
- **Passed on the stub, as predicted:**
  - ruling 3 (a), which cannot fail (see B8);
  - refusal without a unit, which is existing behaviour;
  - the lit and unlit-Torch resync, which is existing behaviour.
- **Implementation:** 5 classes, 57 tests, 0 failures.

**T.**
- **Tests only:** 48 compile errors, all on the new T symbols.
- **Stub:** 3 classes, 43 tests, 16 failures, for example:
  - *"exactly one at 3 s expected:<1> but was:<0>"* (headless);
  - *"nothing at 2.9 s expected:<0> but was:<1>"* (dialog);
  - *"no capture, ever expected:<0> but was:<2>"*;
  - "Cancel timer" not found;
  - numerals not found.
- **One test did not fail for its stated reason, so the test was wrong.** `Back during a
  countdown` failed on the dialog's removal count, because with the main clock stopped no frame
  composed the host's removal. After one frame it failed as stated: *"no capture after close
  expected:<0> but was:<1>"*. Fixed in `2c56736`.
- **Passed on the stub, as predicted:**
  - timer off captures at once;
  - cancelling the countdown's scope;
  - the strip order test (the stub places the chip).
- **First run against the implementation:** 7 failures, all harness.
  - Four headless tests: `UncompletedCoroutinesError`, because the test's `childScope` parented an
    open `Job` on the test's own job. It is now under `backgroundScope`.
  - Three dialog UI reads, and then one more. Throwaway probes, not committed, measured four facts:
    - `performClick` moves the stopped clock one frame, and the countdown counts from there: a
      3 s countdown captured at exactly +3000 ms, not at +2999 or +3015;
    - `advanceTimeBy` rounds up to whole frames unless `ignoreFrameDuration` is set (2900 became
      2928);
    - a screen read needs a frame after the change;
    - a click's state change reaches the screen on the second frame when nothing is read in
      between, with or without a countdown running.
  - The tests now advance exactly and count their frames. The two data-gathering probes follow
    CLAUDE.md's "two misses, then data".
- **Then:** 9 camera classes, 84 tests, 0 failures.

**L.**
- **Tests only:** 48 compile errors, all "No parameter with name" for the two new parameters (9
  callers each), the new L symbols, or inference on the slot lambdas.
- **Stub:** 8 classes, 87 tests, 12 failures, every one a new L test and every one because the
  chip is absent or fixed:
  - "Failed to retrieve bounds of the node", for the three layout tests;
  - "Failed: assertExists" and "Failed to inject touch input", for the chip taps and real touches;
  - the missing On and Off descriptions;
  - the glyph label.
- Every existing test in those classes passed with the new parameters.
- **Implementation:** 9 classes, 90 tests, 0 failures.

## Evidence: revert checks

The runner (`/tmp/sftl-revert.sh`, not committed) works in four steps:
- saves a copy of the file and applies one exact-string edit;
- runs the named classes;
- refuses to cite results if the build log has a compile error;
- restores from the saved copy, never from git, then confirms the file is byte-identical to the
  forward version and equal to HEAD.

None of the four runs had a compile error, and all four were restored and confirmed.

1. **The flash mode reaching `ImageCapture`.** `imageCapture?.flashMode = ...` was removed from
   `setFlashMode`. Result: 2 failures, *"Auto: the ImageCapture's flash mode expected:<0> but
   was:<2>"* and *"precondition: On reached the first ImageCapture expected:<1> but was:<2>"*. Both
   are the use case never receiving the mode, and nothing else changed.
2. **The timer delay.** `delay(ONE_SECOND_MILLIS)` was removed from `CaptureCountdown`. Result: 13
   failures across `CaptureCountdownTest` and `InAppCameraTimerTest`. Examples: *"nothing at 2.9
   s expected:<0> but was:<1>"* (headless 3 s and 10 s, dialog 3 s, 10 s and mid-change), *"no
   capture after close expected:<0> but was:<1>"*, "Cancel timer" not found, and numerals not
   found. Each is the countdown completing at once, which is what a countdown with no delay does.
3. **Cancel-by-shutter.** `countdown.isRunning -> countdown.cancel()` became `-> Unit`. Result:
   exactly 1 failure, *"pressing the shutter during a countdown cancels it, with no capture,
   ever: no capture, ever expected:<0> but was:<1>"*, meaning the countdown ran on to its capture.
4. **The Location chip's call into the handler.** `onClick` was emptied. Result: 5 failures, each
   the handler never being asked:
   - *"the handler got the toggled value expected:<[false]> but was:<[]>"*, from `AvailabilityScreen`;
   - *"touch 1 ... reached the handler expected:<[false]> but was:<[]>"*;
   - *"the handler was asked expected:<[false]> but was:<[]>"*;
   - *"expected:<[false]> but was:<[]>"*;
   - Settings' checkbox *"(ToggleableState = 'Off')"* not met. This shows the Settings check can
     fail.

## Checks that cannot fail, stated

- **Ruling 3 (a) and the `ImageCapture` half of (b).** `FLASH_MODE_OFF` is a fresh
  `ImageCapture`'s default. They pin the bound state as the planner asked, and are not evidence
  that the mapping works. Revert 1 is that evidence.
- **"Without a flash unit, Auto and On are refused"** is existing behaviour and passed on the stub.
- **The countdown numerals "take no pointer input"** is asserted by construction only: an
  `OverlayText` has no pointer modifier. No test touches the numerals.

## Suite

| Run | Commit | Classes | Tests | Failures | Skipped |
|---|---|---|---|---|---|
| Baseline | `bdf351f` (app identical to `2753a83`) | 211 | 1630 | 0 | 24 |
| After | `5b98487` | 214 | 1668 | 0 | 24 |

- Both runs used `./gradlew testDebugUnitTest --continue` with the results directory deleted
  first, and neither build log has a compile error.
- The baseline matches the last recorded suite (1630 / 0 / 24 at `42e3dd3`).
- **The 38 new tests, by class:**
  - `CameraXCaptureSessionFlashTest` +7;
  - `CameraFlashChipTest` +1;
  - `CaptureCountdownTest` 6 (new);
  - `InAppCameraTimerTest` 12 (new);
  - `InAppCameraLocationChipTest` 6 (new);
  - `AvailabilityScreenInAppCameraTest` +3;
  - `InAppCameraDialogLandscapeTest` +2;
  - `InAppCameraDialogTest` +1.
- The planner predicted 25 to 50.

## Layout

In portrait and in both landscape arrangements (`ROTATION_90`, `ROTATION_270`, in the
`w640dp-h360dp` class), all four chips sit inside the strip's bounds in order, with no overlap.
**Robolectric reports zero window insets**, so this shows the row or column holds four chips. It
says nothing about the punch-hole cut-out, which is device item 1 or 2.

## Deferred device items

None ran. All are deferred to promotion by the owner's ruling. No `connectedAndroidTest` was run,
because the stack's run record warns it wipes app data on the test phone.

| # | Item | Pass condition |
|---|---|---|
| 1 | Four chips in portrait | Flash, Timer, Grid, Location all visible in the strip, none under or clipped by the punch-hole, each tappable. |
| 2 | Four chips in both landscape arrangements | The same at the phone turned left and turned right, the strip on the punch-hole edge each time. |
| 3 | Flash On | With the chip at "Flash on", every capture fires the flash. |
| 4 | Flash Auto | With the chip at "Flash auto", a capture in the dark fires the flash and a capture in daylight does not. |
| 5 | Torch | Torch lights and douses as before, and resets to Off when the camera closes. Record whether Torch plus a capture fires the flash (expected not, since Torch's capture flash is `FLASH_MODE_OFF`; whether CameraX honours an `ImageCapture` flash mode while the torch is lit is unverified). |
| 6 | Timer | 3 s and 10 s each count down on screen (numerals upright in every hold) and capture exactly once at zero; a second shutter press during a countdown cancels it with no photo. |
| 7 | Location | The chip and Settings' checkbox agree after a toggle on the strip in each direction, after a toggle in Settings in each direction, and after an app restart. |
| 8 | Glyph readability | The owner's verdict on each new glyph over a live scene: `FlashAuto`, `FlashOn`, `TimerOff`, `Timer3`, `Timer10`, `LocationOn`, `LocationOff`. |

## Flags outside scope

- **`onAutoSaveLocationToPhotosChanged` updates optimistically** (`AvailabilityViewModel.kt:881`
  updates `uiState` before the write) and **does not revert when the write fails** (`:885` only
  logs). The chip shows whatever Settings shows, so after a failed write both show the unsaved
  value. Not changed, per the dispatch.
- **`CaptureCountdown.start` throws if a countdown is already running.** The dialog never does
  this, because a second press cancels instead. The choice was an exception over silently ignoring
  the call.
