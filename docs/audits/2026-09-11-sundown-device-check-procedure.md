# Device checks for the sundown work: what to run, and what cannot be run yet

**Date:** 2026-09-11
**Build:** branch `claude/beta-signup-website-g3t91u`, Phase 1 slices 1 to 6.

---

## Correction first

An earlier message in this session told the owner to check, as the first priority, whether
`USAGE_ALARM` gets through a silenced phone for the sundown alert.

**That check cannot be run.** `grep -rn "AlertKind.TURNAROUND\|AlertKind.SUNSET"` over `app/src/main/java`
returns the `when` branches inside `AndroidAlertDelivery` and the enum's own doc comment, and
nothing else. `DecideSundownAlertUseCase` has no caller either. **No sundown alert can fire on a
device today**, because the thing that would decide to fire one is the service move, which is
deferred until after the beta.

This is the reachability family this project keeps recording, and the cheap question would have
caught it: who calls this? Asked before recommending the check rather than after, it costs one
grep.

There is also **no `app/src/androidTest`**, so an instrumented test is not an available route
either without creating that source set first.

## What is actually checkable on a device today

| # | Check | Why it is checkable |
|---|---|---|
| 1 | Off-track no longer overrides silence | Off-track has a real caller and this is a **behaviour change** to shipped code |
| 2 | The sundown channel exists and is separable | `AppContainer:237` builds `AndroidAlertDelivery` at process start, whose `init` creates both channels, with no alert needed |

Nothing else from this phase is device-verifiable. The screen row is not built, so there is no new
inset-dependent layout to check; the previously-listed inset check was premature.

---

## Check 1: off-track respects a silenced phone

This is the half of the 2026-09-11 silence ruling that actually shipped, and it is a change to
existing behaviour, so getting it wrong is a regression rather than a missing feature.

### The method that matters: run it twice

A single run on a silent phone proves nothing. "No buzz" is what a correctly silenced alert looks
like **and** what a broken alert looks like, and the two are indistinguishable from one run. The
evidence is the *difference* between two runs that differ in exactly one setting.

### Steps

1. Install the branch build on the test device.
2. Grant location (precise) and notifications. Confirm both in Settings → Apps → Forager →
   Permissions, rather than assuming the prompts were accepted.
3. **Run A, ringer NORMAL.** Start a track recording. Walk far enough to lay several breadcrumbs.
   Tap return-to-vehicle so the recording enters its returning state. Walk *away* from the start
   point, steadily, far enough and long enough for the off-track detector to fire.
   - Record: did the phone vibrate, did a notification appear, how long after you turned away.
4. **Run B, ringer SILENT.** Same device, same place, same walk, same distance, the ringer switched
   to silent and nothing else changed. Repeat step 3.
   - Record the same three things.

### Reading the result

| Run A (normal) | Run B (silent) | Meaning |
|---|---|---|
| buzzed | did **not** buzz | **The ruling shipped correctly.** This is the expected result |
| buzzed | buzzed | The override did not take. `overridesSilence` is still reaching the vibration as `true` somewhere |
| did not buzz | did not buzz | Says nothing about the ruling. The alert did not fire at all, so run A again and get it firing before drawing any conclusion |

That third row is the important one. Without run A succeeding, run B is not evidence.

### If off-track will not fire at all

It needs the *returning* state and a sustained drift, not a single stray step, and its own doc
describes an edge-triggered alert with a cooldown. If several honest attempts produce nothing,
that is a finding about the off-track detector's thresholds on real ground, worth reporting on its
own rather than treated as a failed silence check.

---

## Check 2: the sundown channel exists and is separable from off-track

No walk required. The channels are created when the app's process starts.

### Steps

1. Fresh-install the branch build, or clear the app's data, then launch it once and leave it.
2. Android Settings → Apps → Forager → Notifications.
3. Confirm **both** categories are listed and are separate entries:
   - "Off-track alert"
   - "Sundown alert", described as telling you when to start heading back and when the sun sets.
4. Turn **one** of them off and confirm the other stays on.

### What this proves, and what it does not

It proves the two are independently controllable in Android's own settings, which is what makes
the owner's ruling actionable by a user: silence the advisory one, keep the safety one.

It does **not** prove either alert fires, and for sundown nothing will. The channel existing and
an alert arriving are different claims.

### A note on the old channel

`AndroidAlertDelivery` deletes `off_track_alert` (the pre-`_v2` id) on every channel creation. On a
device that had an older build, Android may list one deleted category. That is expected and
recorded in that file's doc comment, not a defect.

---

## Deferred until the service move wires the alerts

Everything below needs a caller that does not exist yet. Running these after the service move is
the point of doing them at all.

- **Does `USAGE_ALARM` actually get through silent and Do Not Disturb on this hardware?** The whole
  distinction between the sundown alert and off-track rests on it. If it does not hold on real
  devices, the ruling needs revisiting rather than the code.
- Does the turnaround alert fire at sunset minus the darkness margin, once, and not again.
- Does the sunset alert fire, and does a turnaround that was already past stay unfired.
- Do both survive the task being swiped away, which is the hole the service move exists to close.

## Deferred until the screen row is built

- Where the strip sits against the real top system-bar inset. Robolectric reports zero insets and
  this app calls `enableEdgeToEdge()`, so nothing in the JVM suite is evidence about it.
- That a long press still reaches the map through the new row, sampled at several points across
  the row's own bounds rather than at its centre.

---

## Results

| Check | Date | Device, OS | Result | Notes |
|---|---|---|---|---|
| 1 off-track run A, normal | | | | |
| 1 off-track run B, silent | | | | |
| 2 channels listed and separable | | | | |
