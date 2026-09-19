# The shutter's edge is physical, not a screen side; and the camera's retention across backgrounding is the design, not a defect

**Date:** 2026-09-17 · **Branch:** `claude/new-session-vto65i` · **Commits:** `139727a` (finding 1:
code and tests), plus this report's commit · **Base:** `fe427f2`, app code unchanged since
`1e7d97a` as the dispatch stated — verified, the commits between are documentation only.

**Dispatch:** two findings from the first v4 device-check run, S26 Ultra, 2026-09-17, run stopped
at step 6.1. Both carried an explicit verify-before-building gate. Finding 1 was to be fixed with a
test and a revert check; finding 2 was to be reproduced and reported, with a fix **proposed** and
not built.

**One-paragraph outcome.** Finding 1 is real, is fixed, and the dispatch's framing of it needed one
correction: the code did not get `ROTATION_270` wrong in particular, it had **no rotation input at
all**, so both landscapes received the same screen side and exactly one of them was always wrong.
Finding 2 **is not a defect in the code**; it is the documented, deliberate behaviour of
`InAppCameraViewModel`, and step 6.1 expects a closure that no code path performs and that was
never specified. The rotation in step 2 is **not** necessary to the symptom, and the count
observation that looked like corroborating evidence cannot discriminate — the listed steps take no
photo, so "No photos yet" is the initial state, not a reset. The two findings are **not** related;
the shared-cause hypothesis does not survive contact with either. Suite 1547 tests / 198 classes,
0 failures, 24 skipped unchanged.

---

## Finding 1 — verification before building

The dispatch asked three things before any fix. Answers read from the source, not inferred from the
symptom.

**1. Where the landscape arrangement chooses the shutter's edge.** Two places, and the second is the
one that matters:

- `CameraArrangement.kt:31-32` (at `fe427f2`) — `cameraArrangement(lockToPortrait: Boolean,
  windowIsLandscape: Boolean)`, returning `Portrait` or `Landscape`.
- `InAppCameraDialog.kt:272` (at `fe427f2`) — the `Landscape` branch's control cluster, aligned
  `Modifier.align(Alignment.CenterEnd)`, unconditionally.

**2. What that code computed for each of the four rotations.** This is where the dispatch's framing
needs correcting. `cameraArrangement` **never received a rotation**. Its landscape input was
`LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE` (`InAppCameraDialog.kt:146`), a
boolean. So:

| Display rotation | Window shape reported | Arrangement | Shutter |
|---|---|---|---|
| `ROTATION_0` | portrait | `Portrait` | `BottomCenter` |
| `ROTATION_90` | landscape | `Landscape` | `CenterEnd` |
| `ROTATION_180` | portrait | `Portrait` | `BottomCenter` |
| `ROTATION_270` | landscape | `Landscape` | `CenterEnd` |

**3. Whether `ROTATION_270` is the case that produces the wrong edge.** The dispatch said to stop
and report if the code said otherwise, and it does say something otherwise — not that a different
rotation is broken, but that **no branch distinguishes the two landscapes**. The code gives both
`CenterEnd`. Which of the two is therefore wrong is not a fact about the code at all: it is decided
by where the charger port physically is, which Android exposes no API for. The device measurement
(`deviceRotation=3`, port left, shutter right) is what identifies `ROTATION_270` as the wrong one,
and it remains a one-device reading. **The fix is built on the code's blindness, which is certain,
and the mapping from that blindness to a specific rotation is built on the measurement, which is
not.** Both are stated as such in `CameraArrangement`'s own doc.

**What this also explains, and confirms the dispatch's separate observation.** Opening in portrait
is correct at all four rotations (dispatch §"The defect is specific to opening in landscape")
because with the window locked, `BottomCenter` **is** the natural-bottom edge whatever the phone is
doing. The portrait arrangement never had the question to get wrong. So the dispatch's either/or —
"either two paths compute the edge, or one path is given a different input at open" — resolves to
neither: **one path computes it, and it is given an input that cannot express the answer.**

**And one thing the hypothesis got wrong, stated plainly.** Finding 1 has nothing to do with a
lifecycle or configuration event. It reproduces on a fresh open with no rotation, no backgrounding
and no recreation, on a phone simply picked up in the wrong landscape. The `AvailabilityScreen`
subtree swap is not involved.

## Finding 1 — what was built

`cameraArrangement` now takes the window's rotation at open and returns one of three:
`Portrait`, `LandscapePortRight` (`ROTATION_90`), `LandscapePortLeft` (`ROTATION_270`).
`InAppCameraDialog` reads the rotation through `currentDisplayRotation()` — promoted from private
to internal in `RotateWithDevice.kt` so there is **one** reader of the window's rotation rather than
two spellings of it — outside `remember`, exactly as `windowIsLandscape` already was, so the value
is captured at first composition and held. The held-at-open behaviour is unchanged and deliberate.

The two landscape layouts are one `LandscapeControls` composable parameterised by which side the
port is on, rather than two near-identical blocks. The shutter is always the **outermost** child,
hard against the port edge, with the count inboard: that ordering is the substance of the rule,
since the motor habit is built on the shutter's distance from the edge the thumb wraps around.

**The assumption, written where the mapping is.** Android exposes no API for the charger port's
position. The fix maps the **natural-orientation bottom edge** and assumes the port is on it — true
of a portrait-natural phone, inverted on a landscape-natural tablet. Confirmed on one device, so it
is recorded as an assumption, not a fact.

**Constraints honoured.** The capture and orientation-reapply path is untouched (not in the diff).
Nothing moves during a session — the arrangement is still chosen once and held. Portrait and
inverted portrait are unchanged: they take the same `Portrait` branch they always did.

## Finding 1 — evidence

**Tests.** `CameraArrangementTest` rewritten: all four rotations with each expectation phrased as
*which physical edge the shutter lands on*, a test that the two landscapes never resolve to one
arrangement, the setting-on column across all four rotations, and an unrecognised rotation.
`InAppCameraDialogLandscapeTest` measures the real dialog at both `ROTATION_90` and `ROTATION_270`,
reading bounds back from the composition.

**The harness precondition, which is the part worth keeping.** The rotation tests pin the display
through `ShadowDisplay` and then **assert the harness actually reports it**. Without that, a
`ShadowDisplay` call that silently failed would leave both tests running at `ROTATION_0` — the one
sample on which neither could fail. That is the CLAUDE.md family "a check that passes because it
never saw the data that could fail it", pre-empted rather than discovered.

**Revert check.** Against a copy saved before editing, not `git checkout --`, since the forward
change was uncommitted at the time — the failure mode CLAUDE.md records. Removing only the
`ROTATION_270` branch:

- **Build log: 0 compile errors**, checked before reading any result.
- Three failures, each specific to this edit:
  - `ROTATION_270, port on the user's left: the port edge is the window's left expected:<LandscapePortLeft> but was:<LandscapePortRight>`
  - `a landscape arrangement that ignores the rotation puts the shutter on the punch-hole edge in one of the two. Actual: LandscapePortRight`
  - `port edge (left at ROTATION_270), inside the frame's padding expected:<16.0> but was:<552.0>`
- The third is **the device symptom reproduced as a number**: the shutter's left edge at 552dp in a
  640dp window — hard against the far side — where the rule puts it at 16dp.
- Forward change confirmed present afterwards by `git diff --stat` (5 files, none missing).

**Suite.** 198 classes, 1547 tests, 0 failures, 24 skipped — the skip count unchanged.

---

## Finding 2 — verification, and why nothing was built

The dispatch's four questions, in order.

**1. Reproduce, and report whether the rotation in step 2 is necessary.** *Not reproduced on
hardware — there is no device in this environment, and that is a limit of who ran this, not a
judgement about the report.* The mechanism is nevertheless decidable from the code, and it says
**the rotation is not necessary**:

`InAppCameraViewModel` (`InAppCameraViewModel.kt:35-48`) holds the open target, and its own class
doc states the contract it was chosen for: `ComponentActivity` keeps its `ViewModelStore` across a
configuration change and clears it in `onDestroy` only when `isChangingConfigurations()` is false.
**Backgrounding with the home button destroys nothing.** The Activity is stopped, the store is
untouched, `target` stays non-null, and `InAppCameraHost` (`InAppCameraHost.kt:97`) composes the
dialog again on return. A rotation is not needed for that and does not change it — and with
`android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"`
(`AndroidManifest.xml:160`) a rotation does not even recreate the Activity.

**2. Where the decision to close the camera on return is supposed to live.** **Nowhere. It does not
exist.** No lifecycle observer anywhere closes the camera: the only `ON_STOP` observers in the tree
belong to `CartographyScreen` (`:204-206`) and `AvailabilityScreen`, and both concern dirty-entry
handling, not the camera. The dialog's own `DisposableEffect`
(`InAppCameraDialog.kt:129-133`) opens and closes the **CameraX session**, not the dialog — that is
the hardware binding releasing on stop and rebinding on resume, which is why the viewfinder comes
back live rather than stale. The camera's open flag has exactly one automatic clearing path: the
ViewModel being cleared on a non-configuration destroy.

**3. What actually happens to that state across background and restore.** Nothing happens to it. It
survives, by construction, and that is what `InAppCameraViewModel`'s doc says it is for: *"a
rotation, where the user is holding the phone and framing a shot and losing the viewfinder is a
bug; and process death or a memory-pressure destruction, where the user was sent away and the
camera should stay closed."* A plain background-and-return is **neither of those cases**, and the
doc does not address it. This is a gap in a recorded decision, not a contradiction of one.

**Which makes step 6.1 and step 6.2 test different mechanisms.** Step 6.2 — "Don't keep activities"
— destroys the Activity non-configurationally, clears the store, and closes the camera. That step
should pass, and it is the one that actually exercises the ViewModel's contract. Step 6.1 expects
the same outcome from an event that clears nothing.

**4. A proposed fix — not built.** See the decision below.

## Finding 2 — a correction to the report's own evidence

The dispatch records the photo count reading "No photos yet" and reasons from it: *"whatever holds
the open target and whatever holds the captured-photo list did not survive the same event in the
same way."*

**That observation cannot discriminate.** The steps as listed (dispatch lines 110-113) are: open the
camera, rotate, background, return. **No photo is taken.** A freshly opened camera shows "No photos
yet" already, so the reading is identical whether the count reset or never moved, and the inference
has nothing to stand on. It is the same shape as the legacy-miles migration in CLAUDE.md, where both
branches produced the answer the check was reading.

For completeness, `photosTaken` is `rememberSaveable` (`InAppCameraDialog.kt:120`) inside a dialog
composed above the width-class branch, so on this path there is no mechanism that would reset it
while leaving the dialog open. **To make the observation mean something, re-run with a photo taken
before backgrounding** — the count is then 1, and a 0 on return would be a real finding about state
this report has not otherwise examined.

## Finding 2 — the decision, which is not mine

Closing the camera on backgrounding is a **new behaviour**, not a repair. Nothing implements it and
nothing ever specified it; the device check is the only artefact that asserts it. Per CLAUDE.md, an
unmade architectural decision is a stop-and-ask, so the options are priced here rather than picked:

1. **Close on `ON_STOP`.** A lifecycle observer clearing the ViewModel's target. Satisfies 6.1 as
   written. The cost is that it cannot distinguish "user went to another app" from any other
   `ON_STOP`, and `PhotoAcquisitionLaunchers.kt:80-82` records this exact confusion biting before:
   an app's own camera launch produces identical `ON_PAUSE`/`ON_STOP` events. A naive observer would
   close the camera on events that are not the user leaving.
2. **Leave the behaviour and correct step 6.1.** The camera reopens where the user left it after a
   glance at a notification, which is arguably the better field behaviour for a foraging app —
   and step 6.2 already covers the case the retention rule was actually written for.
3. **Close on `ON_STOP` only when the app is genuinely leaving**, reusing whatever distinction
   `AvailabilityScreen`'s existing heuristic draws. More faithful to the intent, and it inherits a
   heuristic that already has a recorded failure mode.

**No code was written for any of these.** My reading is that (2) is the honest default and (1) is
what the check literally asks for, and the gap between those is exactly the decision to be made.

## Finding 2 — device steps, since it cannot be confirmed here

1. Open the camera in portrait. **Take one photo.** Confirm the count reads 1.
2. Background with the home button. Return via recents. Record: is the dialog open, and what does
   the count read?
3. Repeat without the photo but **without rotating**, to confirm the rotation is irrelevant as the
   code says it is.
4. Run step 6.2 ("Don't keep activities"), which the stopped run never reached. The prediction from
   the code is that it **passes** — the camera closes.

If 6.2 fails, that contradicts the ViewModel contract and is a different and more serious finding
than 6.1.

---

## The shared-cause hypothesis

**It does not hold, and the two findings are unrelated.** The dispatch was right to mark it as a
place to look rather than a conclusion.

Finding 1 is a missing argument: a pure function was asked a question its parameters could not
express. No lifecycle event, no configuration event, no subtree swap; it reproduces on a first open
in a fresh process. Finding 2 involves no defect at all — the state behaves exactly as designed and
documented, and what is missing is a decision, not a re-decision.

The surface similarity is that both concern values decided once at open and held. But holding is
**correct in both cases and is retained in the fix**: the arrangement must not reflow mid-session,
and the target must survive a rotation. Neither is a re-decision failing to fire.

## Documents corrected

- **v4 step 3.3** (`2026-09-16-pr102-device-check-v4.md`) — split into 3.3 and 3.4, one per
  landscape, phrased as the charger-port edge with the device-anatomy definition, plus "the shutter
  on the punch-hole edge is a failure" and a superseding note in the body giving the old wording and
  why it would have passed the broken branch. The sign-off matrix now asks for both results, and the
  cross-reference in step 4.3 was renumbered from 3.5 to 3.6.
- **No runner's card exists in the repository** — checked (`grep -rn "right edge" docs/`); the only
  other occurrences are in the 2026-09-15 completion report, which is a historical record and is
  superseded by this one rather than edited.

## What this report does not establish

- **Whether any other device reverses the port-edge mapping.** One device, one run. The assumption
  is stated in `CameraArrangement`'s doc and would need a landscape-natural device to falsify.
- **Anything about finding 2 on hardware.** Every claim above is from the code.
- **Step 6.2, steps 7 through 12.** The run stopped at 6.1; none of them have been performed.
- **Whether the fix looks right on a real screen.** Robolectric measures bounds in an inset-free
  window (CLAUDE.md). The device items are step 3.3 and the new 3.4.
