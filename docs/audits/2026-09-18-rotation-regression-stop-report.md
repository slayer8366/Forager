# Rotation inverted and the status bar showing on the S26 Ultra: stopped before code, with what the tree and the emulator can and cannot say

**Date:** 2026-09-18 · **Branch:** `claude/new-session-vto65i` at `bc5796c`, working tree clean ·
**Dispatch:** "rotation is inverted and the status bar is showing", written against `bc5796c` ·
**Status:** **stopped, no code changed.** The dispatch asks for the device's `deviceRotation` before
anything is touched, and there is no device here. Everything below is from the tree, the branch
history, the other branch (read at the owner's instruction), and the emulator.

## What feeds what, from the code

- **Glyph rotation and the capture rotation are one value.** Every control that turns takes
  `session.deviceRotation` (`InAppCameraDialog.kt:224, 230`), and each shot's `targetRotation` is
  the same `deviceRotation` (`CameraXCaptureSession.kt:343`). That value is
  `effectiveDeviceRotation(lockToPortrait, sensorRotation)` (`:188`, function at `:466-467`): the
  sensor's reading with the setting off, `ROTATION_0` with it on. The sensor's reading is
  `UseCase.snapToSurfaceRotation(orientationDegrees)` from the `OrientationEventListener` (`:320`).
  So the dispatch's constraint holds by construction: **one fix, not two**, if that value is wrong.
- **The arrangement takes a different value.** The shutter's and strip's edges come from the
  window's display rotation at open (`currentDisplayRotation()`, `InAppCameraDialog.kt:155`) —
  not from the sensor. The glyph angle is sensor **minus** display (`RotateWithDevice.kt:74-85`).
- **Nothing on this branch touched the sensor or capture path.** `git log 1e7d97a..bc5796c` on
  `CameraXCaptureSession.kt`, `IntendedOrientation.kt`, `CameraCaptureSession.kt` is empty. The
  photos coming out upright in all four holds was confirmed on the device on 2026-09-17 against
  the same capture code.

## The hypothesis, checked against all five observations

If, in the port-left hold, the display reports `ROTATION_270` (3) and the sensor reports
`ROTATION_90` (1):
1. glyph angle = 90 − (−90) = 180 → **inverted** ✔
2. arrangement from display 3 → `LandscapePortLeft` → shutter left ✔, strip right ✔
3. `targetRotation` = 1 in a 3 hold → **photo 180° out** ✔

The only single fault consistent with all of them, **given the premise that the port is on the
left.** The mirror (sensor 3, display 1) also inverts the glyphs but puts the shutter on the right
and saves the photo correctly, so it is excluded by observations 2 and 4. And a display fallback to
`ROTATION_0` (`LocalView.current.display` null in a dialog) would turn the glyphs a quarter, not a
half, so it is excluded by observation 1.

**The premise is the weak point, and it costs nothing to check.** "Nav bar on the left ⇒ port on
the left" is an inference. On the emulator the nav bar is a gesture handle on the long edge in
every hold, and Samsung's three-button bar can be set to a fixed side. **Look at the charger port.**
If it is on the right, the hold is `ROTATION_90`, the display is 1, and the analysis inverts.

## What the emulator measured (API 36 AVD, `bc5796c` build)

Port-left hold (accelerometer `-9.81:0:0`): window `mRotation=3`; nav bar `[0,1017][2400,1080]`
(bottom); shutter bounds x 42–231 (left), "Strip" x 2163–2239 (right); status bar
`visible=false`; and after a shot, from logcat: **`Shot: deviceRotation=3 targetRotation=3
requestDegrees=180`**, tag `is 3, already the shot's 180°` — kept, correct. **The emulator does not
reproduce either symptom.** Whatever inverts the sensor on the S26 Ultra is not in the code path
the emulator runs, or is a property of that device's sensor frame.

## The other branch, read at the owner's instruction

`claude/2026-09-18-camera-session-work` handles orientation identically: the same `ScreenEdge`,
`portEdge` (Portrait→Bottom, PortRight→Right, PortLeft→Left) and `punchHoleEdge = opposite`, its
`DeviceEdges.kt` test table built from the same emulator cut-out readings; its `RotateWithDevice.kt`
and `photo/` diffs against `bc5796c` are **empty**. Its status-bar hide is the same mechanism
(dialog window via `DialogWindowProvider`, `hide(statusBars())`, transient-by-swipe), plus a
`show()` on dispose, which cannot explain a bar visible *while open*. Its report confirms from the
Compose source that `decorFitsSystemWindows = false` gives the dialog the full screen with cut-out
mode `ALWAYS`, matching my counterfactual measurement. **Nothing there diagnoses this; it would
behave the same on the device.**

## The status bar

Hidden on the emulator in every hold (`visible=false` while open, `true` after Back), by the same
call the device build makes. The one anomaly on record: sampling with the camera open gave
`false, false, true, false` over ten seconds, screenshot drawing no bar — likeliest the system
flashing it for the camera-in-use chip. On the device it is reported drawn. Two things I cannot
tell from here: whether the device build is `bc5796c` (`Settings → About → version` should read
`1.0.7xx+gbc5796c…`), and whether One UI honours a status-bar hide requested by a `Dialog` window
rather than the Activity's. Both are device answers.

## An instrument gap, proposed and not built

The shot entry records `deviceRotation` (the sensor) and `targetRotation` but **not the display
rotation**, so the log can say the sensor read 1 but not that the display read 3 at the same
moment — the pair the hypothesis is about. `CameraXCaptureSession` already reads
`previewView?.display?.rotation` (`:337`); adding it to `recordCaptureShot` is a few lines in the
session and both `DebugDiagnostics` twins. Proposed for the next dispatch so the device run answers
both halves at once. Not done here, per "report the actual values before touching anything."

## What the device run should produce

1. **The port side, by looking at the port**, in the failing hold.
2. `grep 'capture shot' diagnostics.log | tail -1` after one photo in that hold: `deviceRotation`
   and `targetRotation`. Prediction if the hypothesis holds: **1 and 1** with the port on the left.
3. The build's version string.
4. Whether the status bar is drawn continuously or flashes.
