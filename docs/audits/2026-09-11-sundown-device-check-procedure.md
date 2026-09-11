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

### On Samsung, step 2 is not enough, and this cost an hour

On the One UI device this was run on, Settings → Apps → Forager → Notifications showed **only**
"Allow notifications" and "Sound and vibration". No category list, no "Categories" heading, nothing
below. The channels existed the whole time.

The list is hidden behind a system-wide Samsung toggle: **Settings → Notifications → Advanced
settings → "Manage notification categories for each app"**. It was off on this device, and while
it is off, One UI hides every app's channel list, not just this one. Turning it on produced the
screen the check asks for immediately.

This is worth more than a navigation footnote. The reason the sundown alert has its own channel is
so a user can silence the advisory alert and keep the safety one **in Android's own settings**. On
a Samsung phone with the default setting, that control is not reachable by a user who does not
already know the toggle exists. The separation is real in the system; its discoverability is not.
Whether that warrants an in-app deep link to the channel (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`,
which this app does not currently use anywhere) is an owner decision, not something to build off
the back of one device.

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
| 1 off-track run A, normal | 2026-09-11 16:22 local | Samsung, One UI | **fired, audible** | Owner: "Notification sound is good." Vibration not separately recorded |
| 1 off-track run B, silent | 2026-09-11 16:26 local | Samsung, One UI | **fired, silent** | Ringer muted (crossed-speaker icon in the status bar at 16:27). Notification posted to the shade and made no sound. **Whether the phone vibrated is not recorded, and that is the datum the ruling actually turns on** — see below |
| 2 channels listed and separable | 2026-09-11 | Samsung, One UI (exact model and One UI version not recorded) | **PASS** | Three categories listed and independently toggleable: "Off-track alert", "Sundown alert", "Track recording". Only visible after enabling the Samsung toggle described above |

### Check 1: what the two runs establish, and the one thing still open

Run A fired audibly on a normal ringer. Run B fired silently with the ringer muted. Same device,
same walk, one setting different, which is the paired shape this check was specified as.

**That pair is not yet the confirmation, because both observations are about sound.** The ruling is
about vibration. The channel is created with `enableVibration(false)`
(`AndroidAlertDelivery.kt:87`), so the channel contributes no buzz at all, and the only vibration
is the direct call at `:116`, which after the change carries `VibrationAttributes.USAGE_NOTIFICATION`
instead of `USAGE_ALARM`. A notification that makes no sound in Mute mode is consistent with the
ruling having shipped and also consistent with a channel that was never going to make sound anyway.

Three readings, and the device was not asked which one applied:

| Ringer mode | Vibrated? | Meaning |
|---|---|---|
| Full Mute | no | **Pass.** `USAGE_NOTIFICATION` was suppressed, which is the ruling |
| Vibrate | yes | **Also a pass.** The platform allows notification-usage vibration in vibrate mode; this is not the override |
| Full Mute | yes | **Fail.** `overridesSilence` is still reaching the vibration as `true` |

The status bar in the run B screenshot carries the crossed-speaker icon, which reads as Mute rather
than Vibrate, but that was not confirmed with the owner and the vibration itself was not observed.
Recorded as **partial**: the sound half is done, the vibration half is one question away.

Worth noting as correct and not a defect: the notification still **appears** in the shade in run B.
Silencing an alert and suppressing it are different things, and only the first was ruled.

### What check 2's pass does and does not establish

It establishes that `createSundownNotificationChannel` runs on real hardware, that the channel
carries the name from `strings.xml:8`, and that Android treats it as separate from off-track. That
is the half of the silence ruling that needed a device.

It establishes nothing about either alert firing, and nothing about `USAGE_ALARM`. Check 1 is still
unrun, and the `USAGE_ALARM` question is still blocked on the service move.

"Track recording" appearing in the list is worth noting on its own: its channel is created in
`TrackRecordingService.onCreate`, not at process start, so its presence means a recording had been
started on this device at some point. It is not evidence about this branch.

---

## An error made while running this check, recorded because it nearly ended the investigation

Reaching check 2's pass took four wrong turns, and the third was mine and serious.

The device reported `Build 598 · 1.0.598+gbed3da7e`. I checked that against the repository and
concluded the APK came from a history this repository does not contain: no branch had 598 commits,
the longest had 347, and `git cat-file -t bed3da7e` returned "not a valid object name". On that
basis I told the owner the install predated the current repository, was probably from the retired
mirror, and would need an uninstall because 598 to 233 is a versionCode downgrade. They uninstalled
their app on that advice.

**Every one of those numbers was meaningless.** This session's clone is shallow.
`git rev-parse --is-shallow-repository` returns `true` and `.git/shallow` exists. After
`git fetch --unshallow`, this branch is 597 commits and `main` is 584. `bed3da7e` is the merge
commit GitHub generates for the pull request, one commit above the branch head, which is what
`actions/checkout` builds on a `pull_request` event and which exists only on GitHub's side. Build
598 was the branch build all along.

The evidence that would have settled it was already downloaded. The CI log for the run contains,
in its own words:

```
APK reports versionCode=598 versionName=1.0.598+gbed3da7e
```

I had that file open and grepped it for test failures without reading what the build step said
about itself.

**This is the family in `CLAUDE.md`, in a new variant.** The rule there is to ask what sample a
check actually ran on before citing it. `git rev-list --count` runs on whatever history the clone
holds, and a shallow clone answers with a plausible small number that is not the answer to the
question asked. `app/build.gradle.kts:67` says exactly this, in a comment I had read earlier the
same session while looking up how the version string is built:

> a tarball export has no git metadata at all and fails loudly on its own, but a shallow clone
> answers `rev-list --count` with a perfectly plausible small number that has nothing to do with
> the real history

The build script implements the guard. I read the guard and then did the thing it guards against.

**The cheap check, for the next session:** run `git rev-parse --is-shallow-repository` before
quoting any commit count, ancestry result, or "this object does not exist" conclusion from this
container's clone. A `fatal: Not a valid object name` here is not evidence that an object does not
exist. Sessions in this environment get shallow clones by default.
