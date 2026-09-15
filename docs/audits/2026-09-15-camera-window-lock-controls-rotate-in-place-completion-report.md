# The camera frame spun on rotation: lock the window while the camera is open, turn the controls in place

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Commit:** `8d9761c`, plus this report's · **Base for the reads:** `3fa31dd`.

**Finding, from the device check on `3fa31dd`.** Rotation works: the camera stays open, the viewfinder redraws, the state survives. But the whole window re-lays out and the system plays its rotation animation over it, so the frame visibly spins; a screenshot has the dialog caught mid-animation at an angle. The requirement, in the owner's words: *"the only thing that should rotate is the text, and it should rotate in place. The frame itself should not visually flip, because the screen is already rotating with the phone, so a preview whose transform never changes shows the scene correctly to the person holding it."* The mechanics that make landscape photos upright stay exactly as they are.

**One-paragraph outcome.** The dialog pins the Activity's `requestedOrientation` to `SCREEN_ORIENTATION_LOCKED` while it is composed and restores the previous value on dispose (`LockWindowOrientation`). The session exposes the surface rotation its orientation listener already snaps to, and one shared `Modifier.rotateWithDevice` turns a control about its own centre inside a square footprint, animated the short way round; Done, the photo count and the error text use it. Nothing about capture changed. Suite 189/1491 → 192/1503, 0 failures, 24 skipped unchanged; `assembleDebug` exit 0; six revert checks, each failing on the test that holds its claim. Whether the frame actually stops animating is the device's, with four more items named in §5.

---

## §1 — Verification, before anything was built

**1. What re-transforms the preview today, and whether locking stops it.** By `javap -c` on `camera-view-1.6.2`: `PreviewView.onAttachedToWindow` calls `startListeningToDisplayChange` and adds an `OnLayoutChangeListener`. `DisplayRotationListener.onDisplayChanged` compares the display id with `getDefaultDisplay().getDisplayId()` and calls `redrawPreview()`. The only other bodies that invoke `redrawPreview` are the layout-change lambda (`lambda$new$0`) and `setScaleType`. `updateDisplayRotationIfNeeded` reads `Display.getRotation()` and hands it to `PreviewTransformation.overrideWithDisplayRotation`. So the preview re-transforms on exactly two events while open: the Activity's display rotation changing, and the view's size changing. With the window locked, the Activity's `Display.getRotation()` does not change and the view keeps its size, so neither fires. **Locking is sufficient**; there is no third trigger short of the app changing the scale type, which it never does.

**2. `LOCKED` versus `NOSENSOR`.** `ActivityInfo.SCREEN_ORIENTATION_LOCKED = 14` (from the compile SDK's `android.jar`), API 18, documented as locking to the orientation the Activity is currently in; `NOSENSOR = 5` uses the natural orientation, portrait on a phone, which would itself be a rotation when the camera is opened in landscape. `LOCKED` is what the owner wants and what is used; `minSdk` 26 clears the API level. **Two platform caveats, both on the device check:** OEMs vary in how they honour a runtime request; and from Android 16 the platform ignores orientation requests on large screens (smallest width 600dp and up) for apps targeting API 36+. This app targets 37 (`app/build.gradle.kts:245`). On a phone the lock holds; on a tablet or an unfolded foldable it may not, and there the frame would still animate, with everything else correct.

**3. Restore on dispose, and its edges.** In this app nothing else sets `requestedOrientation` (`grep` over `app/src`, 2026-09-15: no hits before this change), so "previous" is the manifest's `UNSPECIFIED`. The value is read once when the `DisposableEffect` starts, keyed on the Activity, and written back when it ends. Edges, named as the session's epoch edges were:
- *Dismissed normally.* Done leaves the dialog, the effect disposes, restored.
- *Activity recreated while open.* A rotation no longer recreates (`4c7ba23`) and is locked here anyway; a night-mode toggle still does. The old instance's dispose restores on its way out; `InAppCameraViewModel` reopens the dialog on the new instance; the effect, keyed on the new Activity, locks again. The request lives on the Activity's window token, which survives a configuration relaunch, so the order does not matter.
- *Process death.* The token is gone; a new process starts unlocked and, the ViewModel being gone, with the camera closed.
- *No Activity behind the context.* The Activity is found by unwrapping `ContextWrapper`s; if none is found, a WARN line and no lock, rather than a lock on nothing. The effect runs outside the `Dialog` so its context is the Activity's, not the dialog window's.
- *Something else setting the orientation while the camera is open.* Nothing does today; if something ever did, the restore would clobber it. Recorded, not guarded.

**4. Interaction with `configChanges` and the width-class swap.** With the window locked, a device rotation produces no orientation or screen-size configuration change and no `LocalConfiguration` update, so neither `onConfigurationChanged` nor the width-class flip fires while the camera is open. **That makes no part of the ViewModel fix redundant, and I agree with the owner that none should be removed:** the lock only holds while the dialog is composed; a night-mode toggle still recreates the Activity; Android 16 ignores the lock on large screens; a multi-window resize changes `screenSize` with the orientation locked; and the ViewModel is what closes the camera on a real destruction, which the lock has nothing to say about.

**5. Capture is unaffected.** `CameraXCaptureSession.kt` takes each shot's `targetRotation` from `sensorRotation`, which its `OrientationEventListener` sets from `UseCase.snapToSurfaceRotation` (the line is unchanged in `8d9761c`; only the field's backing became Compose state, so the screen can read it). The display is a logged fallback only. The sensor keeps reporting while the window stays put, so a landscape photo is still tagged landscape. Confirmed by reading, as the dispatch asked, and on the device check as a photo.

## §2 — What was built

**The lock.** `ui/log/WindowOrientationLock.kt`: `LockWindowOrientation()`, called by `InAppCameraDialog` before its `Dialog`. The reasoning above is on the composable.

**The shared piece.** `ui/log/RotateWithDevice.kt`: `Modifier.rotateWithDevice(surfaceRotation: Int?)`, one modifier for every control that turns with the device; the torch icon uses it next, the tap-to-focus indicator simply does not. Two pure functions under it: `uprightRotationDegrees` (`ROTATION_90 → 90°`, `ROTATION_180 → 180°`, `ROTATION_270 → −90°`, else `0°`; clockwise-positive as Compose's `rotationZ`, so a device turned a quarter counter-clockwise gets its controls turned a quarter clockwise) and `shortestRotationTarget` (the value to animate to so the turn is at most a half, the boundary going clockwise). The rotation is a `graphicsLayer` about the control's own centre, so the centre never moves and the touch target turns with the pixels; the footprint is made square, the larger of the two sides, with the control centred in it, so a wide control turned 90° stays inside the space it was given. **The one visible cost:** a 58×40 Done occupies 58×58 and the count's square sits above the shutter, so the bottom cluster is a little taller than before whether or not anything is turned. An icon-and-number badge would remove that later; not done here.

**The session.** `CameraCaptureSession.deviceRotation: Int?`, backed in the CameraX implementation by the existing `sensorRotation` field, now `mutableStateOf`; the fake gained a settable one. The listener and `targetRotation` are the same lines they were.

**The dialog.** `rotateWithDevice(session.deviceRotation)` on the Done button, the count and the error text. Not on the shutter, which is a circle, and not on the viewfinder, which is the point.

## §3 — Evidence

| Reading | Value | Scope |
|---|---|---|
| Suite before | 189 / 1491 / 0 / 24 | JUnit XML at `3fa31dd`, this container |
| Suite after | 192 / 1503 / 0 / 24 | `8d9761c` |
| `assembleDebug` | exit 0 | `8d9761c` |

Twelve new tests. `RotateWithDeviceTest` (6, plain JVM): the four angles and null; the short way round, including 0° to 270° being a quarter back and 180° to −90° being a quarter on; the boundary; a sweep asserting every result is within a half turn of its start and lands on the target mod 360. `RotateWithDeviceModifierTest` (3): a 100×20 box has a 100×100 footprint with the content centred; turned to `ROTATION_90` its centre is where it was, to half a pixel, and its bounds read 20×100 with the footprint unchanged; a control that starts turned is placed turned. `WindowOrientationLockTest` (3): with the previous request set to a non-default `USER`, the dialog's presence reads back `LOCKED` and its removal reads back `USER`; a context with no Activity behind it locks nothing and crashes nothing; on the real dialog, Done and the count keep their centres and swap their extents when the fake session reports `ROTATION_90`.

**What the tests are and are not.** The lock is reachable under Robolectric only as the requested value; whether the window then stops rotating, and whether the OEM honours the request, is the device's. Bounds are read with `getBoundsInRoot`, which maps the whole box through the graphics layer, the same transform hit-testing uses; a first draft used the unclipped variant, which maps only the origin, and read a turned node as a translated one. Recorded because it is the difference between the test measuring the claim and measuring an accident of the accessor.

**Six revert checks, against committed state, restores verified.**

| Revert (one line) | Failed | Message |
|---|---|---|
| the lock not set | 1 of 3 | `expected:<14> but was:<2>` (`LOCKED` vs the `USER` it was left at) |
| the restore not done | 1 of 3 | `expected:<2> but was:<14>` |
| `ROTATION_90 → 0°` | 3 of 12 | the angle test; the modifier's turn test (`expected:<20.0> but was:<100.0>`); the dialog's Done/count test |
| the square footprint removed | 1 of 3 | `expected:<100.0.dp> but was:<20.0.dp>` |
| the shortest-path fold removed | 2 of 6 | `expected:<-90.0> but was:<270.0>`, and the sweep: `from -720.0 to -90.0 moved 270.0` |
| Done not turned | 1 of 3 | `a quarter turn swaps the extents expected:<58.0> but was:<40.0>` |

## §5 — Device-only, by construction

On a build off `8d9761c`, in the owner's order:
- **The frame no longer animates on rotation** with the camera open. The finding itself; §1.1 says why it should not, the device says whether it does.
- **The controls turn in place without moving**: Done at the top-left and the count above the shutter turn about their own centres; nothing slides.
- **A landscape photo is still upright** after turning the phone with the window locked: capture rotation is the sensor's, §1.5.
- **Opening the camera while already in landscape gives a landscape window**, not a portrait one: `LOCKED`, §1.2. If this comes back portrait, that is the OEM or the large-screen rule.
- **The lock restores after Done**: the screen underneath rotates again once the camera is closed. The test pins the value; the device pins the behaviour.
- Also worth a glance: whether the taller bottom cluster (§2, the count's square footprint) reads acceptably.

### Could not determine

- Whether the owner's devices honour a runtime `LOCKED` request (OEM), and whether any of them is a large screen on Android 16 or later, where the platform ignores it.

### Decided beyond scope

- The error text turns with the device too; the dispatch named the Done row and the count. It is text, and the requirement was that text rotates in place; one modifier more, said here.
- `deviceRotation` on the interface. The 2026-09-15 deadlock dispatch said not to widen the interface beyond open, close and what existed; this dispatch asks the controls to follow "the same sensor rotation the session already tracks", which the screen cannot read without the session exposing it. One read-only property, observable, and no method.

---

## Addendum, 2026-09-15 — step 2 results and the portrait lock (`3591df2`)

**Device results on `670e182`, from the owner.** Cases 2.1 to 2.3 pass: the frame no longer animates on rotation, the controls turn in place without moving, a landscape photo is still upright. The restore verified clean: with auto-rotate unlocked, after Done the app follows the phone into its landscape layout. Case 2.4 failed as `LOCKED` predicts: opening the camera while holding the phone landscape gave a landscape window, and the dialog laid out for it, the shutter at the bottom of a landscape frame instead of where it sits in portrait. The requirement is a frame that is identical every time the camera opens.

**Verified before changing anything.** (1) The lock is composed inside `InAppCameraDialog`'s body (`InAppCameraDialog.kt:120`), before its `Dialog`; the host composes the dialog only when a target is set (`InAppCameraHost.kt:90`, `return` on null), so the effect's life is the camera's, not the screen's, which is what the clean restore on device already showed. Confirmed by reading, not assumed. (2) The session file is not in this change's diff at all; the listener line, `sensorRotation = UseCase.snapToSurfaceRotation(orientation)`, and `capture.targetRotation = it` are as they were. Photo orientation, correct on device in both orientations, has nothing here to disturb it.

**Change 1: `SCREEN_ORIENTATION_PORTRAIT` (= 1), not `LOCKED`.** A fixed portrait, so the window is portrait whenever the camera is open and the shutter is always in the same physical place; `rotateWithDevice` turns Done and the count as before. Not `SENSOR_PORTRAIT` (7) or `USER_PORTRAIT` (12): both admit reverse portrait, a half-turn flip of the window when the phone is held upside down, which is the movement this exists to stop; and this app's layouts are portrait and landscape with no reverse variants a reverse-portrait window would serve. The accepted cost, the owner's decision: opening the camera while held landscape shows one rotation animation as the window flips to portrait, not suppressed. The restore path is the same two lines it was: `previous` captured once, written back on dispose.

**Change 2: the angle is sensor rotation minus display rotation.** `uprightRotationDegrees(surfaceRotation, displayRotation)` folds the difference of the two clockwise turns into a half turn either way. With the window held portrait the display term is `ROTATION_0` and every value is what it was; where the platform ignores the lock (Android 16, screens 600dp and wider, API 36+ target) the window turns itself, the terms cancel to zero, and the controls stay put because the window carried them. No branch, no capability check, no large-screen path. A null sensor reading means no turn whatever the window is at, guarded before the subtraction. The display term is read from `LocalView`'s display with `LocalConfiguration` read alongside, so a window that does turn recomposes it; Robolectric reports `ROTATION_0`, so the modifier and dialog tests exercise the locked column only, and the cancelling column is the pure test's.

**Evidence.** Suite 192 / 1503 → 192 / 1506, 0 failures, 24 skipped unchanged; `assembleDebug` exit 0. Three new pure tests: the cancelling column at all four rotations; the `ROTATION_0` column unchanged; a window at a quarter turn with the device elsewhere, including the half-turn fold; plus null with a turned window. Three reverts against committed state: `PORTRAIT` back to `LOCKED` fails the lock test (`expected:<1> but was:<14>`); the display term removed fails the two display tests (`expected:<0.0> but was:<90.0>`, `expected:<-90.0> but was:<0.0>`); the null guard removed fails the null-with-turned-window case (`expected:<0.0> but was:<-90.0>`).

**Device-only, for the re-run on a build off `3591df2`:** opening the camera while holding landscape gives a portrait frame with the shutter in its usual place, after one flip animation; Done and the count sit upright in both orientations; photo orientation still matches how the phone was held; after Done with auto-rotate unlocked the app still follows the phone into landscape. The cancelling column of change 2 can be seen only on a large screen running Android 16 or later; none is named here.
