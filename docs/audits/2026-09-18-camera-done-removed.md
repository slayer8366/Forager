# Done removed from the in-app camera; the navigation bar's Back is the way out

**Date:** 2026-09-18 · **Branch:** `claude/new-session-vto65i`, on top of `94735e5` (the overlay
build) · **Owner's instruction:** "Remove Done entirely, and have the navigation bar back button be
the replacement. The back navigation bar button should carry all the functions the Done button had."

## Verified before changing: Back already carried all of Done's function

Done's `onClick` (`CameraBands.kt:150` at `94735e5`) was `dismiss`, the strip's copy of the
dialog's `onDismiss`; the `Dialog`'s `onDismissRequest` (`InAppCameraDialog.kt:159`) **was the
same `onDismiss`**; and that lambda is `onCloseCamera` (`AvailabilityScreen.kt:2087`) →
`InAppCameraViewModel::close` (`MainActivity.kt:433`). A Compose `Dialog` routes a Back key or
gesture to `onDismissRequest` (`dismissOnBackPress` defaults to true). So there was one close and
two controls for it; nothing had to be added to Back. What Done did, enumerated: close the dialog
(the ViewModel's target nulled, so `InAppCameraHost` composes nothing), keep the photos already
handed over (they were handed over per shot, before any dismissal), and — since the overlay build
— let the status bar return by destroying the dialog's window. Back does each by the same path.

## What changed

- `CameraBands.kt`: `CameraStrip` lost its `onDismiss` parameter and the ✕ icon button. The
  strip's doc records why, and that **the empty strip is now a production state**: with nothing
  resident, gating the placeholder off composes no band at all (rule 9), which was previously
  reachable only from a test.
- `InAppCameraDialog.kt`: the strip call no longer passes `onDismiss`; `CAMERA_DONE_TAG` and
  `DONE_LABEL` are gone; the class doc says Back is the way out and always was the same close.
- Tests: a shared `pressBackOnCameraDialog()` (test source, `CameraDialogBack.kt`) presses Back
  through the dialog's **own** `OnBackPressedDispatcher` — the one a `ComponentDialog` routes the
  key and the gesture to — not the Activity's, which a dialog window never sees, and not the
  `onDismiss` lambda by hand, which would prove nothing about Back. Five classes moved onto it:
  `InAppCameraDialogTest` (Back closes and the photo stands; the unavailable camera can still be
  dismissed; **no node described "Done" exists**), `InAppCameraHostTest`,
  `AvailabilityScreenInAppCameraTest`, and the two rotation tests that used Done as their turning
  glyph now use the count.

## Evidence

**Revert check.** `dismissOnBackPress = false` on the `DialogProperties`, from a saved copy, build
log read first — the results are in the index row and the commit for this change, quoted from the
JUnit XML. If that revert had failed nothing, the Back tests would have been asserting on the
`onDismiss` lambda and not on Back, and would not have been counted.

**Revert result, quoted:** 0 compile errors, four failures — `Back reaches the same close Done used
to expected:<1> but was:<0>`, the unavailable-camera dismissal, the host's close, and the
availability screen's width-flip test. Nothing else failed, so nothing else depended on Back.

**Emulator** (`nodone_p1_portrait_open.png`): the strip holds the placeholder alone; no node
described "Done" anywhere in the accessibility tree; a shot taken; `KEYCODE_BACK` — the dialog is
gone from the tree and `statusBars … visible=true` again.

**One observation, not a defect, recorded with its samples.** With the camera open, the status
bar's reported visibility was sampled at 1, 3, 6 and 10 s after the tap: `false, false, true,
false`. The screenshot at that moment draws no bar (no clock, no icons — only the camera-in-use
privacy chip, a system overlay). Nothing in this change touches `CameraWindowChrome.kt`, and every
earlier single sample with the camera open read `false`. The likeliest reading is the system
showing the bar transiently to present the camera-in-use chip when the camera starts, which
`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` permits; the sustained state is hidden. Not established:
whether that flash is visible to a person. **Device item:** watch the top edge when the camera
starts and say whether the bar flashes.

**Documents.** v4 step 3 amended: the ✕ is gone from the "what to look for" paragraph and items
3.1–3.5; 3.7 now tests Back alone (and the back gesture on a gesture-nav phone), and states that
the photos already taken are kept — which is the function the owner asked Back to carry.

## Not tested

The back *gesture* on a gesture-navigation device is the same dispatcher path as the button, but
only a device shows it; v4 step 3.7 asks for both.
