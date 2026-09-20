# The camera draws in the Activity's own window, and the turn is seamless

**Date:** 2026-09-19 · **Branch:** `claude/new-session-vto65i`, built at `1db2bfc` over `1e2e184`
(the dispatch's stated base, verified against `origin` before starting) · **Dispatch:** "draw the
camera in the Activity's own window" · **Status:** built, tested and measured. **Seamless rotation
works**, which is what the whole line of work was for. Two things are reported rather than fixed:
one deviation from the dispatch, and one defect that predates this change.

Platform claims cite `frameworks/base` at `refs/heads/android16-release`, fetched 2026-09-19.
Emulator claims are the API 36 AVD `forager_measure36`, gesture navigation, animation scales 1.0.

## Verify before building, both answered before any code

**1. Is the `ty=BASE_APPLICATION` window the top fullscreen opaque one with the camera open?**
**Yes.** Measured with a throwaway probe built from copies saved first and restored after, never
committed — the same shape as the 2026-09-18 rotation-terms probe. With the camera drawn in the
Activity's window, `dumpsys window displays` reports the same handle for both roles and
`dumpsys window windows` shows **one** window for the app, not two:

```
topFullscreenOpaque=3e51220 focus=3e51220
Window #8 Window{3e51220 … MainActivity}: mAttrs={(0,0)(fillxfill) … ty=BASE_APPLICATION … rotAnim=SEAMLESS
```

The same handle holds both roles with the camera closed and after Back, so the camera opening and
closing no longer changes which window the display policy is looking at. That is the third state
the dispatch asked for; the closed state and the dialog state were already on record.

**The probe also settled the risk the dispatch listed first.** On the turn to landscape the Shell
logged `nav bar allows seamless` and the transition's display change carried rotation-animation
`3` (`ROTATION_ANIMATION_SEAMLESS`), where the same probe against the `Dialog` logged
`task N isn't requesting seamless, so not seamless` and animation `0`. And `MainActivity` stayed
`state=RESUMED` with the camera open, so nothing about `ON_STOP` changed.

**2. What does composing the camera into the Activity's window look like here?**
`AvailabilityScreen` is a `@Composable fun` whose body emits `InAppCameraHost` and then the
window-width branch as siblings; `ForagerTheme` wraps the tree in a `CompositionLocalProvider` and
`MaterialExpressiveTheme`, neither of which is a layout, so both siblings are children of the
composition root. The camera becomes a full-screen `Box` in that tree instead of a `Dialog`. No
restructuring beyond the camera's own composition was needed — **with one exception, below.**

## What was built

- **`InAppCameraDialog`** composes a `Box` instead of a `Dialog`, with a `BackHandler` reaching the
  same `onDismiss` the `Dialog`'s `onDismissRequest` reached.
- **`CameraWindowChrome`** hides the status bar on the Activity's window and restores it in
  `onDispose`, and asks that window for seamless rotation, restoring the previous value on the way
  out. The `DialogWindowProvider` cast is gone.
- **The manifest attribute stays undeclared** and there is a comment on `RequestSeamlessRotation`
  saying why, at length: `android:rotationAnimation` is read first, through
  `ActivityRecord.mRotationAnimationHint`, and short-circuits the runtime request, besides applying
  to every rotation in the app rather than only the camera's. Zero hits in `AndroidManifest.xml`.
- **The name `InAppCameraDialog` is kept** and marked in its own doc as historical rather than
  descriptive. Renaming touches five test classes and a dozen doc references; that churn was not
  part of the ruling, so it is flagged for the owner rather than taken.

## The one deviation from the dispatch, and why

**`InAppCameraHost` moved from above the width-class branch to below it.** The dispatch lists
"stays above the width-class branch" under what must not change, so this is reported rather than
buried.

The camera has no window of its own now, so **nothing but composition order puts it on top**: a
sibling composed first draws and hit-tests underneath. `Modifier.zIndex(1f)` was tried first,
precisely to keep the call where it was, and **measured insufficient** — the occlusion test below
failed with the Camera button behind the open camera still firing.

What the original placement was for is untouched. Its stated reason is that the camera is not a
property of the navigation layout and must not sit inside either width-class tree, so a width flip
cannot dispose it. Below the branch it is still outside both trees, still one call, still one
session; `AvailabilityScreenInAppCameraTest`'s two width-flip tests pass unchanged. The comment at
the call site says all of this. **The owner's to reverse if the letter was meant over the reason.**

## The thing neither review nor the type system would have caught

**A `Box` that only draws a background is not a hit-test target.** `background` is a draw modifier
and attaches no pointer input, so with the ordering fixed the camera still let touches through to
the screen underneath: the Album's Camera button kept firing under the open camera. `Modifier`
`swallowTouchesBelow()` consumes the pointer events that reach the camera's root, on the main pass,
so everything nested inside it still gets them first and the shutter works.

This is CLAUDE.md's own recorded pitfall read backwards. The entry warns that a container attaching
pointer input intercepts its whole bounds even where nothing is drawn; here that interception is
the wanted behaviour, and its absence is the bug. The `Dialog` supplied both properties for free by
being a window, and neither was visible in the diff that removed it.

**One test caught both, and it went red twice for two different reasons.** It is a coordinate touch
rather than a semantic click for the reason CLAUDE.md gives — a semantic click invokes the node's
own action and would have passed on every one of these builds. It taps the **same point twice**:
once with the camera closed, as a positive control that the point is live and really does open the
camera, and once with the camera open. Without the control, "nothing happened" could have been a
touch that simply missed.

## Evidence

**Tests.** `202 classes, 1587 tests, 0 failures, 24 skipped.`

Of the 52 tests across the five classes that construct the camera: **51 were unchanged**, and one
class's two window tests were rewritten for the Activity's window rather than a dialog's. One test
was added (the occlusion test). The Back helper was renamed `pressBackOnCamera` and moved to
`CameraBack.kt`, pressing the Activity's `OnBackPressedDispatcher`; its five call sites changed
shape only in becoming a receiver call. `InAppCameraDialogTest` moved from `createComposeRule()` to
`createAndroidComposeRule<ComponentActivity>()` because it now needs the Activity, and its
status-bar seam became a two-method object so the restore can be observed.

**Revert checks.** Three, each restored from a copy saved before editing, each compiled before its
results were read, with the forward change confirmed still present afterwards.

| Reverted | Failures | Message |
|---|---|---|
| `swallowTouchesBelow()` removed | 1 | *"the camera took the touch; the button behind it never fired expected:<[ALBUM]> but was:<[ALBUM, ALBUM]>"* |
| `onDispose { hider.show(…) }` emptied | 1 | *"restored exactly once on the way out expected:<1> but was:<0>"* |
| seamless attribute left at `ROTATION_ANIMATION_ROTATE` | 1 | *"ROTATION_ANIMATION_SEAMLESS on the Activity's window, so a turn is a re-layout and not an animation expected:<3> but was:<0>"* |

**Emulator, setting off, camera opened in portrait, turned through all four holds.** Every turn was
seamless — the Shell logged `nav bar allows seamless` and the display change carried animation `3`,
on all four:

| Turn | `mRotation` | animation | `capture shot` in the new hold |
|---|---|---|---|
| portrait → port-right | 0→1 | `r=0->1:3` | `device=1 display=1 target=1 degrees=0` |
| port-right → upside-down | 1→2 | `r=1->2:3` | `device=2 display=2 target=2 degrees=270` |
| upside-down → port-left | 2→3 | `r=2->3:3` | `device=3 display=3 target=3 degrees=180` |
| port-left → portrait | 3→0 | `r=3->0:3` | `device=0 display=0 target=0 degrees=90` |

**The capture angle is unchanged**, which was the standing question: the same four `requestDegrees`
as the before-readings at `80855c3` and at `774c1e2`, 90/0/270/180. The layout follows the window —
shutter bottom-centre at rotation 0, the screen's right at 1, the screen's left at 3
(`img/2026-09-19-camera-activity-window/port_right_seamless.png`) — and the glyphs stay upright in
every hold without turning, because the window carries them.

**Setting on is unchanged**, re-run with a landscape-aware route after the first attempt used the
compact tab bar that a landscape window does not have. Opened from a landscape hold the window was
at `mRotation=1` beforehand and `0` at open — the one accepted flip — then `0` through all four
holds, with the glyphs identical every time (244×53 and 76×43), the shutter fixed at
`[446,2106]-[635,2295]`, and `requestDegrees=90` pinned.

**The status bar returns on every exit**, all three that exist:

| Exit | Bar afterwards |
|---|---|
| Back | shown, and the screen behind is back |
| the Activity going away (force-stop with the camera open) | shown |
| the four-minute absence timeout (away ~4 min 10 s) | shown, and the camera had closed |

The fourth exit on the old list, a touch outside, has no equivalent: it was `dismissOnClickOutside`
on a window with no outside, and it was already unreachable.

## The defect this did not fix, measured properly for the first time

**The system draws the status bar over the camera from open until the next window relayout.** The
bar is gone in every hold after the first turn and stays gone, and it is present at open. Confirmed
by eye, not only by a flag: `img/2026-09-19-camera-activity-window/status_bar_drawn_over_camera.png`
shows the clock and icons over the camera's scene. Sampled every two seconds for twenty seconds
after a portrait open, it never went away on its own.

**The hide is applied, and the app's own insets agree.** A probe logging from inside the hider
showed it called twice with the view attached, and
`view.rootWindowInsets.isVisible(statusBars())` reading `false` 300 ms later. So the app is laid
out full-bleed and believes the bar is hidden; what remains is the system drawing it anyway.
Whether that is the transient-bars behaviour, the camera privacy indicator, or something else was
not traced.

**It is not this change's doing.** The same pattern is on record for the `Dialog` build — visible at
open, hidden after the first turn — and yesterday's `setting_on_after_flip_fixed.png` shows the
clock over the camera on that build too. The pre-build report recorded it as "visible for some
seconds after a turn made at open"; that description was too narrow, and this supersedes it: it is
visible from open until a relayout, however long that takes.

## Still open, and named by the owner as next

Reverse portrait. `cameraArrangement` returns the portrait case for any non-landscape window, so at
`mRotation=2` the shutter sits on the punch-hole edge and the strip on the port edge
(`img/2026-09-19-camera-activity-window/upside_down_bands_inverted.png`, shutter at
`[446,2033]-[635,2222]` of a 1080×2400 window). The owner has ruled to add a fourth arrangement,
because reverse portrait is the gill-shot hold. Device check step 3 carries a note that the
upside-down hold is a known failure until that lands, rather than a finding to report.

## Device-only, not covered by anything above

1. No animation on any turn, on real hardware. The emulator's navigation bar cannot change sides,
   so its seamlessness rests on `config_allowSeamlessRotationDespiteNavBarMoving`; a phone whose
   build does not set that will animate turns into and out of reverse portrait.
2. The status bar travels with the phone and stays hidden. The travelling half is measured here
   (the source frame is `[0,0][1080,136]` in portrait and `[0,0][2400,74]` in landscape); the
   staying-hidden half is the open defect above.
3. Shutter on the charger-port edge and strip on the punch-hole edge, all four holds — three of
   four until the fourth arrangement lands.
4. Saved photos upright from all four holds.
5. Back closes, photos kept, bar returns.
6. The four-minute timeout: camera closes, bar returns.
