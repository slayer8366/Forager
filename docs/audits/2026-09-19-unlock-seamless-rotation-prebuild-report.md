# Unlock the camera window, suppress the rotation animation: what the tree and the platform say, before code

**Date:** 2026-09-19 · **Branch:** `claude/new-session-vto65i` at `80855c3` (the dispatch's stated
base; verified against `origin` before starting) · **Dispatch:** "unlock the camera window,
suppress the rotation animation" · **Status:** the five verify-first questions answered from the
code, the android16-release platform source, and the API 36 emulator; the build follows in a
separate record. Nothing in this document was built.

The platform claims below cite `frameworks/base` at `refs/heads/android16-release`, fetched
2026-09-19 from `android.googlesource.com`; line numbers are that snapshot's. The emulator is the
API 36 AVD `forager_measure36` with gesture navigation; every emulator claim names the run.

## The four disclosures first

**Confirmed from the code or measured.** Everything under questions 1–5 except where marked.

**Inferred, not observed.** Which fallback animation a Samsung build plays when seamless cannot
apply; whether One UI honours `ROTATION_ANIMATION_SEAMLESS` at all; the value of
`config_allowSeamlessRotationDespiteNavBarMoving` on the S26 Ultra. All three are device items.

**Could not determine here.** Whether the emulator's own rotation is seamless under the change —
that is the probe the build exists to run, and it is reported with the build, not here.

**Premises in the dispatch that the tree or the platform contradicts.**

1. *"Since Android O it can be declared in the manifest on fullscreen activities rather than set
   through `WindowManager.LayoutParams`."* The attribute exists (`android:rotationAnimation` on
   `<activity>`, public since API 26, `platforms/android-37.1/data/res/values/public-final.xml:2777`),
   but it does not reach the place this design needs. It becomes `ActivityInfo.rotationAnimation`
   → `ActivityRecord.mRotationAnimationHint` (`ActivityRecord.java:1886`) → the **task's**
   animation in `Transition.getTaskRotationAnimation` (`Transition.java:3343-3349`). The window
   manager's own seamless decision reads the top fullscreen opaque **window's** `mAttrs`
   (`DisplayRotation.java:673-676`), which the manifest never sets, and the task path would make
   every rotation anywhere in the app a jump cut. So the request goes on the dialog's own window.
2. *"…falls back to CROSSFADE if rotation cannot be applied without pausing the screen."* That is
   what the constant's documentation says (`WindowManager.java:4145-4152`), and it describes the
   legacy path. Under shell transitions — every build this app runs on — the fallback is the
   ordinary **rotate** animation: `DefaultTransitionHandler.getRotationAnimationHint` starts from
   `ROTATION_ANIMATION_ROTATE` (`DefaultTransitionHandler.java:227`) and returns it whenever the
   seamless conditions fail (`:263-268, :291-306`). A fallback, if it fires, is the flip the owner
   has never accepted, which is why the conditions under which it fires are listed under question 1.
3. *"…forcing all four means setting the Activity's `screenOrientation` to `fullSensor`."* Confirmed,
   and with a consequence the dispatch does not name: `SENSOR`/`FULL_SENSOR` follow the sensor
   **regardless of the user's auto-rotate lock** (`DisplayRotation.java:1151-1161`, the branch that
   takes `sensorRotation` outside the `USER_ROTATION_LOCKED` check) and switch the rotation sensor
   on for the camera's duration (`:973-979`). Reverse portrait is admitted only by `FULL_SENSOR` or
   `FULL_USER` or a device that allows all rotations (`:1157-1161`). `FULL_USER` would honour the
   user's lock — and under it the bar would not travel, which is the requirement — so it is not
   the choice; recorded on `WindowOrientation.kt` when built.

**Decided beyond the dispatch's scope.** Nothing. The file and composable are renamed
(`WindowOrientationLock.kt` → `WindowOrientation.kt`, `LockWindowOrientation` →
`RequestWindowOrientation`) because a function that sets `FULL_SENSOR` cannot be called a lock;
that is naming, not behaviour, and the dispatch's own first sentence is that the lock decision is
reversed.

## 1. Can the dialog's window request seamless rotation and count as top fullscreen opaque?

**Yes, on this platform, by measurement.** With the camera open (setting off, portrait, emulator run
"Q1", 2026-09-19), `dumpsys window displays` reports:

```
mFocusedWindow=Window{1a0746b u0 com.zynergylabs.forager.app/…MainActivity}
mTopFullscreenOpaqueWindowState=Window{1a0746b u0 com.zynergylabs.forager.app/…MainActivity}
```

and `dumpsys window windows` shows `1a0746b` is the **dialog's** window — `ty=APPLICATION`,
`(0,0)(fillxfill)`, `fmt=TRANSPARENT`, `layoutInDisplayCutoutMode=always` — above the Activity's
`f979f9f` (`ty=BASE_APPLICATION`). With the camera closed the base window holds both roles
(`b407877` in the same session's earlier dump). So the dialog is the window the decision is made on.

**Why, from the source.** `DisplayPolicy.applyPostLayoutPolicyLw` records as
`mTopFullscreenOpaqueWindowState` the first visible window, top-down, that is an application-type
window (`type >= FIRST_APPLICATION_WINDOW && < FIRST_SYSTEM_WINDOW`), unattached, and whose params
are `isFullscreen()` (`DisplayPolicy.java:1524-1557`); `isFullscreen()` is origin `(0,0)` and
`MATCH_PARENT` both ways (`WindowManager.java:6141-6145`); `canAffectSystemUiFlags` excludes only a
window with `alpha == 0` (`WindowState.java:1862-1864, 1871-1885`). A Compose `Dialog` with
`usePlatformDefaultWidth = false` and `decorFitsSystemWindows = false` (`InAppCameraDialog.kt:171`)
is `TYPE_APPLICATION` at `(0,0)(fillxfill)`, which is the measurement above.

**What the decision then requires** (`DisplayRotation.shouldRotateSeamlessly`,
`DisplayRotation.java:659-696`): that window is the focused window (it is); its
`mAttrs.rotationAnimation == ROTATION_ANIMATION_SEAMLESS` (**to be set on the dialog's window** —
`Window.attributes` through the `DialogWindowProvider` the chrome already uses,
`CameraWindowChrome.kt:70`); it is not in multi-window and not animating; `canRotateSeamlessly`
(`:699-712`): the navigation bar can move **or** `config_allowSeamlessRotationDespiteNavBarMoving`,
failing which no turn to or from `ROTATION_180`; the activity matches its parent's bounds; no
pinned task and no alert windows. When it holds, the display's transition change is flagged
`ROTATION_ANIMATION_SEAMLESS` (`DisplayContent.java:3566-3570`, `Transition.java:3020-3021`) and
the Shell takes that as the highest-priority answer (`DefaultTransitionHandler.java:219-223`).

**Where the emulator stands on the nav-bar condition.** `dumpsys window displays` reports
`mNavigationBarCanMove=false` (gesture navigation, `navigation_mode=2`), so on this emulator
seamlessness depends on `config_allowSeamlessRotationDespiteNavBarMoving`, which stock gesture
navigation sets and which is not printed by any dump. The transition log decides it: with
`wm logging enable-text WM_DEBUG_WINDOW_TRANSITIONS` and the Shell's `WM_SHELL_TRANSITIONS` text
logging on, a rotation logs the display change as `r=<from>-><to>:<anim>` and the Shell's reasoning
line by line — on the current build a plain app rotation logs `task 74 isn't requesting seamless,
so not seamless.` (run "transition log sample", 2026-09-19). The probe reads the same lines.

**The alternative if it had failed** was never needed, so it is not priced.

## 2. What does `fullSensor` do to the rest of the app, and can it be scoped?

**Scoped already.** `LockWindowOrientation` sets `activity.requestedOrientation` in a
`DisposableEffect` that lives exactly as long as the dialog is composed and restores the previous
value on dispose (`WindowOrientationLock.kt:98-110` at `80855c3`); `FULL_SENSOR` goes into the same
slot, so it is in force only while the camera is open. The manifest's `screenOrientation` is
untouched (`AndroidManifest.xml:157-166` declares none, i.e. `UNSPECIFIED`).

**Cost while open:** the Activity's window turns with the sensor even if the user has auto-rotate
off, and turns to reverse portrait, which the rest of the app never does; the camera dialog covers
the Activity throughout, so nothing else is seen turning. **Cost at close:** the restore to
`UNSPECIFIED` re-resolves the window against the user's setting, so a camera closed in a hold the
user's setting would not have chosen turns the Activity's window back — with the app's ordinary
rotate animation, the dialog already gone. Device check step 3.7 already looks at the window after
close and is rewritten to say this.

## 3. What breaks when the lock goes?

Everything pinned to "the window does not move", listed with what each asserts at `80855c3`:

| Where | Asserts now | Change |
|---|---|---|
| `WindowOrientationLockTest.kt:93-100` | setting off → `SCREEN_ORIENTATION_LOCKED`, restored after | asserts `FULL_SENSOR`; file and class renamed with the source |
| `WindowOrientationLockTest.kt:104-107` | `windowLockFor` pure mapping | `windowOrientationFor`: `PORTRAIT` / `FULL_SENSOR` |
| `WindowOrientationLock.kt:12-97` | the class doc: why lock, `LOCKED` vs `PORTRAIT`, the re-resolve note | rewritten under a superseding note; the re-resolve observation kept as history |
| `CameraArrangement.kt:42-49` | "the window lock is what makes the choice safe to hold" (already superseded 2026-09-18) | a second superseding note; the paragraph stays |
| `CameraArrangement.kt:114-115` | `ScreenEdge` "only valid while the window is locked" | valid for the arrangement the window currently has, re-derived on every turn |
| `RotateWithDevice.kt:19-20, 40-45` | "while the window stays locked"; the cancel case written for large screens only | the cancel case is now the ordinary setting-off case |
| `InAppCameraDialog.kt:78-82, 139-158` | "nothing in the camera layout moves… chosen once… no visible reflow because the lock holds" | superseded in place |
| `InAppCameraDialogLandscapeTest.kt:244` | the arrangement follows a window turned underneath the dialog | **unchanged** — it is now the main case rather than the return case |
| `InAppCameraDialogLandscapeTest.kt:265` | a device turn alone moves neither shutter nor strip | **unchanged** — still true: the arrangement is keyed on the window, not the sensor |
| `InAppCameraDialogLandscapeTest.kt:172, 317, 378` | glyphs turn in a landscape window when the sensor turns, display term fixed | **unchanged as unit statements** of `rotateWithDevice`; on a device the window now catches up and the net turn is the transient before it does |
| Device check v4 step 3 (3.1–3.5) | "nothing in the layout moves", "no flip animation" on open | rewritten: the layout re-arranges without animation; the bar on the phone's top edge every time |
| Device check v4 step 6.10 | "rotate while open: nothing reflows" | reversed |
| Device check v4 steps 3.7 and 4.3 | "the lock releases" | reworded: the request is put back |

Nothing else references the lock (`grep -rn "LockWindowOrientation\|windowLockFor\|SCREEN_ORIENTATION_LOCKED" app/src`).

## 4. Does the capture angle still hold?

**Yes, and the display was never a term in it.** `CameraXCaptureSession.capture`
(`CameraXCaptureSession.kt:333-343`) sets `targetRotation` from `deviceRotation` — the gated
sensor reading, `effectiveDeviceRotation(lockToPortrait, sensorRotation)` (`:188`, `:472-473`) —
and reads the display only as the logged fallback for a shot with no reading (`:337-342`) and,
since 2026-09-18, for the diagnostic pair (`:353`). CameraX turns the target into the request's
JPEG orientation as the sensor's mounting minus the target, and the window is not in that
arithmetic. "Sensor minus display" is the **glyphs'** angle (`RotateWithDevice.kt:37-45`), and
that expression was written so that a window which carries the controls cancels the turn; with the
window following, the two terms agree in every settled hold and the glyphs stay put. Nothing about
the capture path is touched by the change; nothing about it needs to be.

**Measured before, build `80855c3`, emulator, setting off, camera opened in portrait**, one shot in
each hold, the `Shot:` line from `logcat -s CameraXCaptureSession` (run "BEFORE readings",
2026-09-19):

| Hold | window `mRotation` | `deviceRotation` | `displayRotation` | `targetRotation` | `requestDegrees` |
|---|---|---|---|---|---|
| portrait | 0 | 0 | 0 | 0 | 90 |
| port-right | 0 | 1 | 0 | 1 | 0 |
| upside-down | 0 | 2 | 0 | 2 | 270 |
| port-left | 0 | 3 | 0 | 3 | 180 |

The request follows the sensor's reading (`90 − 90·target`, mod 360, on this AVD's 90° back
sensor) while the display term sits at 0 under the lock. **Prediction for the after-run:** the same
four `requestDegrees`, with `displayRotation` now equal to `deviceRotation` in each settled hold.
The prediction is written before the run so the run can fail it.

## 5. Is the status bar visible when it should not be?

**Hidden in the steady states, visible for some seconds after a turn at open — an unrequested
transient, not a reveal.** Measured on `80855c3` (runs "Q5" and "flip", 2026-09-19; the indicator
is the screenshot, a count of contrasting pixels in the clock's corner, since the first
`type=statusBars … visible=` line `dumpsys` prints did not match the screenshots):

| State | Bar |
|---|---|
| setting off, opened in portrait, 8 s | hidden (`q5_settingoff_portrait_open.png`) |
| setting off, opened in portrait, away in Settings turned to landscape, returned (window turned) | hidden (`q5_settingoff_return_landscape.png`) |
| after Back from the above | shown — restored, as designed |
| setting on, opened in portrait (no flip), 8 s | hidden |
| setting on, opened from landscape (the flip), **8 s** | **shown** — clock and icons, 406 contrasting pixels (`settingon_landscape_open_8s.png`) |
| the same, **20 s** | hidden (0) |

Yesterday's `setting_on_after_flip_fixed.png`, taken about seven seconds after the same flip, shows
the same clock; no swipe or tap was made in either run. So the owner's screenshot is most likely
this: **rule 4 fails for a window of several seconds after a turn made right at open**, then
recovers. The mechanism was not traced (that is a separate defect and the dispatch says so); what
matters for this change is that a turn at open becomes the ordinary case once the window follows
the device, so the probe run measures the bar second by second after each turn. If the transient
reproduces there it is fixed within item 4's scope — the bar must stay hidden while the camera is
open — by re-requesting the hide on the dialog-level display rotation, and the measurement is
repeated. If it does not reproduce, the setting-on flip is recorded as the one remaining instance.

## Stop-and-ask conditions, evaluated

- *The dialog cannot carry the seamless setting* — it can (question 1). No stop.
- *`fullSensor` cannot be scoped to the camera session* — it is scoped by the existing effect
  (question 2). No stop.
- *The capture angle changes meaning* — it does not (question 4). No stop.
- *Seamless falls back in the common case* — not decidable from the source alone; the probe's
  transition log decides it, and a fallback on the common turns stops the work before it ships.

## Aside, found while driving the emulator and not investigated

The Tools drawer in a **portrait** window shows only "Trip Planner" and "Settings"; the
"Mushroom Log" and "Photo Gallery" rows that `AvailabilityScreen.kt:1076-1077` composes
unconditionally are absent, along with "Recent searches" and "Advanced search" (`seasonal_tools.png`,
not kept). In a landscape window all six are present. The camera was opened through Journal →
Album → Camera instead. Not this dispatch's; recorded so it is not lost.
