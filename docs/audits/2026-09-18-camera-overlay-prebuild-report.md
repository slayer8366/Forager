# Camera overlay build spec, section 0: what exists at `6bbd90b`, before any code

**Date:** 2026-09-18 · **Branch:** `claude/new-session-vto65i` at `6bbd90b`, working tree clean,
remote tip verified by `git fetch` · **Spec:** "Camera overlay: build specification", a fresh build
that supersedes the top-strip dispatch and its v2, the region-model decision, the hide-status-bar
dispatch and the seamless-rotation dispatch. Nothing from any other branch is cited here.

## The seven questions, answered from this tree

**1. Is the shutter's edge computed from the device's rotation, not a fixed screen side? — Yes.**
`cameraArrangement(lockToPortrait, windowIsLandscape, displayRotation)` at
`CameraArrangement.kt:84-92` returns `LandscapePortLeft` for `Surface.ROTATION_270` (`:90`) and
`LandscapePortRight` otherwise (`:91`), `Portrait` for any portrait window. The dialog reads the
window's rotation at open through `currentDisplayRotation()` (`InAppCameraDialog.kt:160`) and holds
the arrangement (`:161`). The shutter is `Alignment.BottomCenter` in portrait (`:242`) and
`CenterEnd`/`CenterStart` by arrangement in landscape (`:341`). **Correct; left alone.** What is
missing is an *edge value*: the port edge is implied by three enum cases, never named as an edge,
so "the punch-hole edge is its opposite" has nothing to be the opposite of yet.

**2. Do overlay text and glyphs rotate in place? — Yes, in both arrangements.** `rotateWithDevice`
(`RotateWithDevice.kt:52`, angle from `uprightRotationDegrees` at `:74`) is applied to Done, the
error and the count in portrait (`InAppCameraDialog.kt:234, 251, 259`) and in landscape
(`:332, 356, 364`). The landscape half landed at `6bbd90b`. **Correct; left alone.**

**3. Is there a Done control? — Yes.** A `TextButton(onClick = onDismiss)` at `Alignment.TopStart`
in both branches (`:229-239`, `:327-337`), tagged `CAMERA_DONE_TAG` (`:412`), labelled `DONE_LABEL`
(`:417`). **Exists and disagrees with the rules, reported here before it is changed:** its position
is a *screen-side* rule (top-left), which the spec's vocabulary rejects, and in `Portrait` and
`LandscapePortRight` that corner is on the punch-hole edge, where rule 2 puts the strip. In
`LandscapePortLeft` it is on the *port* edge — so today Done is on a different physical edge
depending on the arrangement, which is exactly the kind of placement rule 1's "why" argues against.
Planned change: Done becomes the first resident of the strip band, so its rule is device anatomy
(punch-hole edge, start end) like everything else. This moves it in one arrangement
(`LandscapePortLeft`: screen left → screen right).

**4. The status bar while the camera is open — visible.** Nothing hides it: `grep` for
`statusBars`/`WindowInsetsController`/`systemBarsBehavior` finds only two `windowInsetsPadding(WindowInsets.statusBars)`
sites in the availability UI and two `keyboardController?.hide()` calls. The Activity calls
`enableEdgeToEdge` (`MainActivity.kt:321`), so the bar is transparent and the app draws under it.
The dialog is a full-window `Dialog` with `decorFitsSystemWindows = false` (`InAppCameraDialog.kt:165`),
and its controls sit inside `windowInsetsPadding(WindowInsets.safeDrawing)` (`:223`) — i.e. the
layout today reserves the bar's space, which rule 5 forbids. **Missing; to build.**

**5. Any strip, band or region structure — none.** `grep -i "strip|CameraBand|CameraRegion|punchHole|cutout"`
over `ui/log/` returns nothing. The only region-named file is `FindLocationPickerRegion.kt`,
unrelated. **Missing; to build.**

**6. Window lock and the setting — present.** `LockWindowOrientation` (`WindowOrientationLock.kt:88`)
pins `SCREEN_ORIENTATION_LOCKED` with the setting off and `SCREEN_ORIENTATION_PORTRAIT` with it on
(reasoning at `:37-47`), called from the dialog at `InAppCameraDialog.kt:147`. The setting is
`CameraOrientationPreferenceRepository.getLockCameraToPortrait()` (`:27`), stored in DataStore under
`camera.lock_to_portrait` (`DataStoreCameraOrientationPreferenceRepository.kt:32`), default `false`
(`:37`). **Correct; not touched.**

**7. `OrientationEventListener` and the capture-rotation path — present.** The listener is
`CameraXCaptureSession.kt:236`, snapping through `UseCase.snapToSurfaceRotation` at `:320`; the
shot's `targetRotation` is set at `:343`; the tag is reapplied by `reapplyIntendedOrientation`
(`IntendedOrientation.kt:112`, called at `CameraXCaptureSession.kt:402`). **Constraint: not touched.**

## What the build therefore is

Missing: a named edge and its opposite (from item 1's gap), the region/band structure (5), the hidden
status bar on the dialog's own window and the collapsed safe area (4), the one-place contrast rule
(nine styling sites in `InAppCameraDialog.kt`: unavailable text `:174`, opening spinner `:184`, Done
`:229`/`:327`, error `:247`/`:352`, count `:255`/`:360`, shutter `:381`), the strip with its
placeholder, and Done's move into the strip (3). Left alone: items 1, 2, 6, 7.

## Emulator, checked before relying on it

`forager_measure36`, API 36, running as `emulator-5554` in the desktop session (`DISPLAY=:0`).
`dumpsys display` reports a **centred punch-hole cut-out**: insets `(0, 136, 0, 0)`, bounding rect
`(480, 0)–(625, 136)` on a 1080×2400 display, `cutoutSpec` a 68 px circle at the top. `adb emu
rotate` answers `OK`, `screencap` returns a PNG, auto-rotate is on, and `:app:installDebug` exists.
The APK installed on it now is not from this branch and will be replaced.
