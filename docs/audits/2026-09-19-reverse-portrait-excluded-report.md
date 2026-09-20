# The camera window never enters reverse portrait

**Date:** 2026-09-19 · **Branch:** `claude/new-session-vto65i`, built at `59d96cc` over `c4ee362`
(the dispatch's stated base, verified against `origin` before starting) · **Dispatch:** "the camera
window never enters reverse portrait" · **Status:** built, tested, measured. The half-turn animation
is gone because there is no longer a half turn to animate.

Platform claims cite `frameworks/base` at `refs/heads/android16-release`. Emulator claims are the
API 36 AVD `forager_measure36`, gesture navigation.

## The three verify-first answers, before code

**1. What makes reverse portrait reachable today?** One constant.
`WindowOrientation.kt:121-122` — `windowOrientationFor(lockToPortrait = false)` returns
`ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR`, assigned to `activity.requestedOrientation` at
`:95`. Nothing else competes for it: `AndroidManifest.xml` declares no `screenOrientation` at all
(grep, zero hits) and nothing else under `app/src/main` writes `requestedOrientation` (grep). It was
set earlier the same day, by the unlock dispatch, precisely so the status bar would be on the
phone's top edge in *every* hold.

The platform draws the line in `DisplayRotation.rotationForOrientation` (android16-release,
`:1151-1161`): for `SENSOR` and friends it takes `sensorRotation`, **except** that a reading of
`Surface.ROTATION_180` is honoured only when `getAllowAllRotations()` is enabled — a device
resource, `config_allowAllRotations` (`:1247-1258`), set on tablets — or the request is
`FULL_SENSOR` or `FULL_USER`. Otherwise `preferredRotation = lastRotation`. So on a phone,
`SCREEN_ORIENTATION_SENSOR` gives portrait and both landscapes and leaves the window where it is on a
half turn. That is the dispatch's requirement exactly, and it is one word.

**2. Can it be scoped to the camera session?** It already is, and nothing new was needed. The value
goes through `RequestWindowOrientation`'s existing `DisposableEffect` (`WindowOrientation.kt:90-99`),
which reads the previous `requestedOrientation`, sets the camera's, and writes the previous one back
on dispose. Only the constant changed.

One property is unchanged and worth restating rather than discovering later: `SENSOR` sits in the
same branch as `FULL_SENSOR`, outside the `USER_ROTATION_FREE` guard, so the camera's window follows
the sensor **whether or not the user has auto-rotate locked**, and the rotation sensor runs for the
camera's duration. `USER`/`FULL_USER` would honour the lock; they were not taken, because the
requirement is that the bar travels with the phone.

**3. Is the capture path untouched?** Yes, by construction rather than by arrangement.
`CameraXCaptureSession.onDeviceOrientation` (`:318-321`) sets
`sensorRotation = UseCase.snapToSurfaceRotation(orientationDegrees)` from the
`OrientationEventListener` built on the application context at `:236-238`. Nothing in that path reads
the window, the display or `requestedOrientation`; `deviceRotation` is
`effectiveDeviceRotation(lockToPortrait, sensorRotation)` (`:188`), and the display enters the
capture only as a logged fallback for a shot with no reading yet (`:337`) and as the diagnostic pair
(`:353`). **So the sensor keeps reporting reverse portrait in a hold the window refuses to follow.**
That is the gill shot, and it now has a test of its own.

## What changed

One constant, `FULL_SENSOR` → `SENSOR`, plus the reasoning around it. The seamless request, the
shutter and strip edge rules, `OrientationEventListener`, `snapToSurfaceRotation` and the capture
path are untouched, and no `fullSensor` was added anywhere.

**The arrangement defect is recorded as unreachable, not deleted.** `cameraArrangement` still returns
the portrait case for any non-landscape window, so it would still invert the bands at `ROTATION_180`
— the window simply cannot get there. There is now a comment at the branch itself
(`CameraArrangement.kt`) saying so and naming what would make it reachable again: putting
`FULL_SENSOR` or `FULL_USER` back, or a tablet whose `config_allowAllRotations` is set. That comment
is what stops someone adding `fullSensor` later and reintroducing the bug silently; the doc above it
says to build the fourth arrangement first if they do. **The fourth-arrangement dispatch is
withdrawn.**

## Evidence

**Tests.** `202 classes, 1588 tests, 0 failures, 24 skipped.`

Two tests carry the change. `WindowOrientationTest` asserts `SENSOR` with the setting off and, in
the same test, `assertNotEquals` against `FULL_SENSOR` with a message naming what putting it back
would reintroduce. `CameraXCaptureSessionShotRotationTest` gains the sensor half: 180 degrees in
through `onDeviceOrientation`, `ROTATION_180` onto the shot's `targetRotation`.

**Revert checks.** Two, each restored from a copy saved before editing, each compiled before its
results were read, forward change confirmed present afterwards.

| Reverted | Failures | Message |
|---|---|---|
| `SENSOR` → `FULL_SENSOR` | 2 | *"portrait and both landscapes, never reverse portrait expected:<4> but was:<10>"*, and the window test's own assertion |
| the sensor snapping 180 to 0 | 1 | *"the shot is tagged reverse portrait, so the saved photo is upright expected:<2> but was:<0>"* |

**Emulator, setting off, camera opened in portrait.** The whole result is in the two rows where
`deviceRotation` and `displayRotation` disagree, which is the design working:

| Hold | window `mRotation` | rotation change logged | animation | `capture shot` |
|---|---|---|---|---|
| port-right | 1 | `to 1 from 0` | `r=0->1:3` | `device=1 display=1 target=1 degrees=0` |
| **upside-down** | **1, unmoved** | **none** | **none** | `device=2 display=1 target=2 degrees=270` |
| port-left | 3 | `to 3 from 1` | `r=1->3:3` | `device=3 display=3 target=3 degrees=180` |
| portrait | 0 | `to 0 from 3` | `r=3->0:3` | `device=0 display=0 target=0 degrees=90` |

**The direct half turn, portrait to upside-down**, run separately because that is the transition that
animated before: `mRotation` 0 before and 0 after, the status bar's source frame `[0,0][1080,136]`
before and after, the shutter at `[446,2106]-[635,2295]` before and after, **no rotation change
logged and no transition at all** — and `Shot: deviceRotation=2 displayRotation=0 targetRotation=2
requestDegrees=270`. The window did not move and the photo is still tagged for an upright save.
Screenshot: `img/2026-09-19-reverse-portrait-excluded/half_turn_window_unmoved.png`.

**Quarter turns are still seamless**, all three in the table, each logging `nav bar allows seamless`
and carrying rotation-animation `3`. Note the port-right to port-left turn is itself a 180° change
of display rotation and was seamless, which is consistent with the earlier reading that the platform
has no rule about a turn's magnitude.

## The owner's question, reported and not decided

**What "Lock camera to portrait" does after this change.** Two things, and the second is larger than
the framing in the dispatch, which named only the landscapes.

- **The window.** Off: portrait and both landscapes. On: portrait only. So the setting's remaining
  layout job is preventing the two landscape arrangements. Reverse portrait is excluded either way
  now, so that is no longer a difference between the two states.
- **The capture tag.** Off: `effectiveDeviceRotation` passes the sensor through, so a photo is
  tagged for the hold it was taken in. On: it pins `Surface.ROTATION_0`
  (`CameraXCaptureSession.kt:472-473`), so **every photo is tagged portrait whatever the hold** — a
  landscape shot with the setting on saves as portrait. That is a difference in the saved file, not
  just in what the screen does, and it is the part a reader weighing "does the setting still earn
  its place" needs in front of them.

Decision left alone.

## Device-only, unchanged by anything measured here

1. Turn end-over-end with the camera open: no animation, and the status bar does not move.
2. **A photo taken upside-down comes out upright.** The gill shot, and the point of the whole
   design. The emulator says the shot is tagged `ROTATION_180`; whether the saved JPEG is upright is
   the HAL's, and device-only for the reasons on `CameraXCaptureSession`.
3. Quarter turns still seamless, bar still travels.
4. Shutter on the charger-port edge in portrait and both landscapes.
5. Setting on: unchanged.
