# Unlock and seamless rotation: the window follows the phone, the animation cannot be suppressed — stopped

**Date:** 2026-09-19 · **Branch:** `claude/new-session-vto65i`, built at `774c1e2` over `ec492c3`
(pre-build report) over `80855c3` (the dispatch's stated base) · **Dispatch:** "unlock the camera
window, suppress the rotation animation" · **Status:** **stopped on the dispatch's own condition** —
*"Do not ship a visible flip as an accepted cost. If seamless cannot be made to work, stop and
report."* It cannot, while the camera is a `Dialog`. The code is on the branch, measured, and **not
shipped**; two decisions are the owner's.

Platform claims cite `frameworks/base` at `refs/heads/android16-release`, fetched 2026-09-19.
Emulator claims are the API 36 AVD `forager_measure36`, gesture navigation, animation scales 1.0.

## Finding 1. The seamless request is accepted by the window manager and ignored by the animation

**Measured.** The dialog's window carries the attribute — `dumpsys window windows` with the camera
open reports `ty=APPLICATION fmt=TRANSPARENT … rotAnim=SEAMLESS`, and it is the only window in the
dump that has it. On **all four** turns of the probe, `DisplayRotation.shouldRotateSeamlessly`
returned true: `Transition.onSeamlessRotating` logged `because seamless rotating` once per turn. So
the whole chain the pre-build report traced holds — the dialog is the top fullscreen opaque window,
it is focused, `canRotateSeamlessly` passes.

**And the animation is still the ordinary one.** On the same four turns the Shell logged
`task N isn't requesting seamless, so not seamless`, and every change in the transition carried
rotation-animation `0` (`ROTATION_ANIMATION_ROTATE`) or `-1` (unspecified). Never `3`.

| Turn | `mRotation` | `because seamless rotating` | Shell's reason | change anims |
|---|---|---|---|---|
| portrait → port-right | 0→1 | 1 | isn't requesting seamless | `r=0->1:0`, `r=0->1:-1` |
| port-right → upside-down | 1→2 | 1 | isn't requesting seamless | `r=1->2:0`, `r=1->2:-1` |
| upside-down → port-left | 2→3 | 1 | isn't requesting seamless | `r=2->3:0`, `r=2->3:-1` |
| port-left → portrait | 3→0 | 1 | isn't requesting seamless | `r=3->0:0`, `r=3->0:-1` |

**Why, and why no placement of the attribute fixes it.** Two independent routes exist and both are
closed:

1. *The display's explicit request*, which the Shell honours first
   (`DefaultTransitionHandler.getRotationAnimationHint`, "The explicit request of display has the
   highest priority"). The display change carries seamless only when `ChangeInfo.FLAG_SEAMLESS_ROTATION`
   is set (`Transition.java:3020-3021`), which only `Transition.setSeamlessRotation` sets
   (`:608-612`), which is called only from `DisplayContent.setSeamlessTransitionForFixedRotation`
   — the fixed-rotation-launch path. An ordinary rotation takes `DisplayContent.java:3566-3570`,
   which calls `onSeamlessRotating` **only**: that overrides the surface sync method and sets no
   animation flag. This is exactly what the probe saw — the log line fires, the animation does not
   change.
2. *The task's animation*, `Transition.getTaskRotationAnimation` (`:3343-3356`). It reads
   `top.findMainWindow(false)`, and `findMainWindow` returns only `TYPE_BASE_APPLICATION` windows
   (`ActivityRecord.java:7182-7190`) — the Activity's window, **never** a `TYPE_APPLICATION` dialog.
   It then returns `ROTATION_ANIMATION_UNSPECIFIED` unless that same window **is** the top
   fullscreen opaque window. With the camera dialog up, the top fullscreen opaque window is the
   dialog (measured, pre-build report question 1). So the two conditions are mutually exclusive
   here: the window that can carry the attribute is not the window that is on top, and the window
   that is on top is not the one the task path reads.

So the attribute is not in the wrong place — **there is no right place** while a fullscreen `Dialog`
covers the Activity. Setting it on the Activity's window, or via `android:rotationAnimation` in the
manifest (which feeds `mRotationAnimationHint` and the same task path), is rejected by the same
check. The dispatch anticipated this: *"Establish first whether a dialog window can satisfy that. If
it cannot, the setting has to sit on the Activity while the camera is open, and that is a different
and larger change."* It is larger than that, in fact: the setting sitting on the Activity is not
enough, because the dialog above it is what disqualifies the task.

**What plays instead.** The rotate animation, at full duration (`window_animation_scale` and
`transition_animation_scale` both 1.0 on the AVD). Not the crossfade the constant's documentation
promises — that is the legacy path, as the pre-build report recorded.

## Finding 2. Reverse portrait puts the shutter on the punch-hole edge

**Measured**, setting off, camera opened in portrait, phone turned upside down: window
`mRotation=2`, shutter at `[446,2033]-[635,2222]` of a 1080×2400 window — the screen's bottom — and
the strip across the screen's top (`img/2026-09-19-unlock-seamless/upside_down_inverted.png`).
At `ROTATION_180` the screen's bottom **is** the device's punch-hole edge and the screen's top is
the charger-port edge. So both bands are on the wrong physical edge, which the device check names as
a failure in as many words.

**Cause.** `cameraArrangement` returns `Portrait` for any non-landscape window
(`CameraArrangement.kt:108-112`) and `portEdge(Portrait)` is `Bottom` (`:151-155`). There is no
reverse-portrait case. There never needed to be: `SCREEN_ORIENTATION_LOCKED` never produced a
reverse-portrait window and `SCREEN_ORIENTATION_PORTRAIT` excludes one by design, which is exactly
why the file could say portrait "was already correct at all four rotations while opening in landscape
was not". `FULL_SENSOR` makes `ROTATION_180` reachable for the first time and the coincidence ends.

**This is the correct-by-coincidence pattern's fourth instance in this area**, and the first one
found *by changing the input the mechanism is supposed to respond to* — which is the practice the
2026-09-18 index row proposed. The row said: before crediting a mechanism for an observation, change
one input it is supposed to respond to and watch whether the observation moves. Unlocking the window
is that change, and portrait's correctness did not survive it.

## What does work, and is measured

**The window follows the phone in all four holds** (setting off, opened in portrait):
`mRotation` 0 → 1 → 2 → 3 → 0 with the sensor, and the arrangement re-derived each time — shutter
`[446,2106]-[635,2295]` (bottom centre, 1080×2400) at rotation 0, `[2169,446]-[2358,635]` (screen's
right, 2400×1080) at 1, `[42,446]-[231,635]` (screen's left) at 3. Opened in landscape port-right
and turned, the same. **The status bar travels with the phone**, which is the whole requirement: its
source frame is `[0,0][1080,136]` in portrait and `[0,0][2400,74]` in landscape — the display's top
edge as the system has it rotated, which is now the phone's top edge.

**The glyphs stay upright without turning**, in every hold: count 244×53 and "Strip" 76×43 in all
four, never the transposed extents. The window carries them, so the two terms of sensor-minus-display
cancel — the expression `rotateWithDevice` was written for.

**The capture angle is unchanged**, which was the dispatch's question 4 and the prediction written
down before the run. Same four `requestDegrees`, with `displayRotation` now equal to `deviceRotation`
in each settled hold rather than pinned at 0:

| Hold | before (`80855c3`) | after (`774c1e2`) |
|---|---|---|
| portrait | `device=0 display=0 target=0 degrees=90` | `device=0 display=0 target=0 degrees=90` |
| port-right | `device=1 display=0 target=1 degrees=0` | `device=1 display=1 target=1 degrees=0` |
| upside-down | `device=2 display=0 target=2 degrees=270` | `device=2 display=2 target=2 degrees=270` |
| port-left | `device=3 display=0 target=3 degrees=180` | `device=3 display=3 target=3 degrees=180` |

**Setting on is untouched**, as required: opened from a landscape hold it flips to portrait and then
`mRotation=0` through all four holds, no glyph turns (244×53 and 76×43 in each), shutter fixed at
`[446,2106]-[635,2295]`, `requestDegrees=90`.

**The status bar stays hidden while the camera is open** — `visible=false` on the `statusBars`
insets source at every sample across four turns and a 20-second settle, and `visible=true` again
after Back. The one exception is the transient below.

## Correction to the pre-build report (question 5)

That report's status-bar table used a screenshot indicator — contrasting pixels in the clock's
corner — and **that indicator is not reliable once the window turns**, because it also catches the
synthetic test scene's own colour edges; the after-run's first table of it read "bar present" in
holds where `dumpsys` says `visible=false`. The right indicator is the `visible=` flag on the
`statusBars` `InsetsSource`, which is what this report uses throughout. The pre-build report's one
finding stands, because it was confirmed by eye in the screenshot rather than by the count: **the
bar is visible for some seconds after a turn made at open** and then hides. The probe reproduced it
with the setting off (`visible=true` about 8 s after opening in portrait, `visible=false` from the
first turn onward). Its cause is still not traced and it is still a separate defect.

## Evidence the dispatch asked for

- **File and line for each verify-first answer, before code:** `2026-09-19-unlock-seamless-rotation-prebuild-report.md`, committed at `ec492c3` before any code.
- **Tests:** `WindowOrientationTest` asserts `FULL_SENSOR` with the setting off and the pure mapping;
  `InAppCameraDialogTest` asserts the seamless attribute on the dialog's own window. Both in device
  anatomy where they touch layout; the pinned rotation is asserted by the landscape class's existing
  `setDisplayRotation`.
- **Revert checks**, both restored from copies saved before editing, both compiled, forward change
  confirmed present afterwards:
  - `FULL_SENSOR` → `LOCKED`: 2 failures. *"setting off: the window follows the device in all four
    orientations, so the status bar is on the phone's top edge in every hold expected:<10> but
    was:<14>"* and *"all four orientations, reverse portrait included expected:<10> but was:<14>"*.
  - the attribute left at `ROTATION_ANIMATION_ROTATE`: 1 failure. *"the dialog's window carries
    ROTATION_ANIMATION_SEAMLESS, so a turn is a re-layout and not an animation expected:<3>"*.
  - Neither test is evidence that the *platform* rotates seamlessly, and finding 1 is why. They
    guard the request being made, which is all a unit test can see.
- **Suite:** 202 classes, 1586 tests, 0 failures, 24 skipped.
- **Emulator `capture shot` readings in all four holds, before and after:** the table above.

## The two decisions, which are the owner's

**A. What to do about the animation.** Three options, priced:

1. **Take the camera out of the `Dialog`** and draw it in the Activity's own window, with the
   seamless attribute set on that window while the camera is open. Then the activity's main window
   is both the window the task path reads and the top fullscreen opaque window, and the Shell's
   gesture-navigation branch returns seamless. This is the only route that reaches the platform's
   camera-app case. Cost: the status-bar design is built on the dialog having its own window — every
   exit restores the bar by destroying that window, which is what made the four-minute timeout path
   free — so hiding would move to the Activity's window with an explicit restore on every exit,
   including the timeout; Back would need its own handling. Days, not hours, and it re-opens a
   design the owner settled on 2026-09-18.
2. **Keep the window following the phone and accept the rotation animation.** The bar requirement is
   met today, at the cost of the flip. The dispatch forbids taking this silently, which is why it is
   here rather than shipped.
3. **Revert to the lock.** The status quo: no animation, and the bar stays on whichever edge was up
   at open — the complaint raised three times.

**B. Reverse portrait.** If the window is ever allowed to reach `ROTATION_180`, the arrangement needs
a fourth case, which is a new enum value and a new `portEdge` mapping — small, mechanical, and a
structural change this dispatch did not authorise. The alternative is `SCREEN_ORIENTATION_SENSOR`
instead of `FULL_SENSOR`, which never reaches reverse portrait and leaves the bar wrong in exactly
that one hold.

## State of the branch

`774c1e2` carries the code; this report and the doc corrections follow it. Everything that asserts
the un-suppressed animation as working has been corrected in place — `CameraWindowChrome.kt`,
`WindowOrientation.kt`, `InAppCameraDialog.kt`, `CameraArrangement.kt`, and device check step 3,
which now opens with a "not yet true" warning and is marked not to be run as a gate. Nothing was
reverted: the `FULL_SENSOR` half is built, measured and correct on its own terms, and reverting it
would throw away the measurements this report rests on.
