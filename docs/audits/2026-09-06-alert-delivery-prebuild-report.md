# Pre-build report: alert delivery — reach a pocketed phone, survive a silenced one

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Dispatch:** "Alert delivery: reach a pocketed phone, and survive a silenced one" (planner,
owner-directed). All three items are report-before-building; **nothing is built in this commit.**

**Base — a discrepancy to settle first.** The dispatch names `main` at `41ce4e1` and a baseline of
1134 tests / 24 skipped. `main` is now **`a2dcb21`** (three merges since: compass reliability
`909c8ea`, then the two-data-corrections PR #70), and the suite on it is **1161 tests, 0 failed,
24 skipped**, allowlist byte-identical (run this session after #70). The branch
`claude/new-session-102gri` is already restarted from `a2dcb21`. This report reads the code at
`a2dcb21`; none of the intervening merges touched the alert path, the service, or the vibration
code — `git log 41ce4e1..a2dcb21 -- MainActivity.kt service/ ui/track/TrackRecordingViewModel.kt`
lists **no commits at all** — so the findings hold for both SHAs. Say if you want it rebased on `41ce4e1` anyway; I would rather not, since PR #70
is already on `main`.

Every claim names a file and line at `a2dcb21`, or is marked inferred.

---

## The failure, as the code explains it

The decision and the delivery live in two different places with two different lifetimes, and only
one of them is broken.

**The decision runs outside composition already.** `TrackRecordingViewModel.beginLocationTracking`
(`ui/track/TrackRecordingViewModel.kt:270–292`) collects `LocationTracker.fixes` in
`viewModelScope` for as long as a recording is active and calls `returnToStart(point)` on every
fix (`:291`). `returnToStart` (`:412–436`) computes the distance to the start, appends it to the
rolling window, runs `DetectOffTrackUseCase`, applies the 120 s cooldown, and bumps
`offTrackAlertId`. None of that is composed. A `ViewModel` from `by viewModels` (`MainActivity.kt:128`)
lives until the Activity is *destroyed*, not stopped; `viewModelScope` coroutines are not
lifecycle-paused; and the location callbacks arrive on the main looper, which keeps running while
the Activity is stopped. So with the screen off and the recording's foreground service holding the
process, **the heuristic keeps deciding**.

**The delivery is the composed part.** `LaunchedEffect(trackUiState.offTrackAlertId)` inside
`setContent` (`MainActivity.kt:309–313`) is the only reader of `offTrackAlertId`, and it calls
`postOffTrackAlert` and `vibrateOffTrackAlert` (`:491–516`). Compose's window recomposer is
lifecycle-aware: below `STARTED` its frame clock pauses, so a state change made while the Activity
is stopped is not recomposed — and the effect is not launched — until the Activity starts again.
That is exactly the owner's observation: the alert arrived "at the moment the phone was turned
on". *(Inferred from Compose's documented `WindowRecomposerPolicy` behaviour, not observed here;
the device pass is the observation.)*

So: **the decision fires in the background today; the delivery of it is queued behind the next
resume.** The fix is smaller than "move the alert to the service", and I want that on the table
before the service is chosen.

Two premises in the dispatch need correcting before the items, because they change what Item 2 is:

- **The vibration is already a separate limb.** `vibrateOffTrackAlert` issues a direct
  `Vibrator.vibrate(VibrationEffect.createWaveform(…))` (`MainActivity.kt:508–516`), independent of
  the notification. Both are called from the same `LaunchedEffect`, so both were gated by
  composition and both failed together — the symptoms are still one failure — but Item 2 is not
  "add a vibration limb"; it is "give the existing one alarm attributes". Its call today passes
  **no attributes**, which is `USAGE_UNKNOWN`, and a usage that is not alarm class is suppressed by
  the platform in ringer-silent mode *(inferred from AOSP's `VibrationSettings` ringer-mode rule;
  device-confirmable, and consistent with the owner's silenced pass, where nothing arrived for the
  composition reason anyway)*.
- **The double-buzz already exists.** The channel is created with `enableVibration(true)`
  (`:475`) and the direct vibration fires alongside it, so in the foreground, when delivery does
  happen, the user gets the channel's default pattern *and* the two-pulse pattern. Item 2's
  question about the channel is therefore about a present behaviour, not a future one.

---

## ITEM 1 — fire with the Activity stopped

### 1.1 Where it goes — two candidates, and a recommendation

**Candidate A — deliver from the ViewModel through an owned interface.** Introduce
`domain/AlertDelivery` (`fun deliver(alert: Alert)`, where `Alert` carries the kind and the
override flag from the "distinction" section), an Android implementation in `service/` or
`notifications/` that does what `postOffTrackAlert` + `vibrateOffTrackAlert` do today, and inject
it into `TrackRecordingViewModel`. `returnToStart` calls `alertDelivery.deliver(...)` where it
bumps `offTrackAlertId` today; the id field and the `LaunchedEffect` are deleted. The decision does
not move, because it does not need to.

*Lifetime:* the ViewModel's — the Activity's, through stops and config changes, ended by Activity
destruction. While a recording runs, `TrackRecordingService` (foreground, `foregroundServiceType=
"location"`, `AndroidManifest.xml:78–81`) keeps the process alive and keeps the app eligible for
location updates while backgrounded, so the ViewModel's own listener (`AndroidLocationTracker`,
one `callbackFlow` per collector, `location/AndroidLocationTracker.kt`) keeps receiving fixes
*(inferred from the platform's foreground-service location rules; the existing device passes, in
which points were recorded while pocketed and the tint changed, are the evidence the stream runs)*.

*The gap, stated:* if the Activity is **destroyed** while recording — the user swipes the task
away — the service continues (`START_STICKY`, `TrackRecordingService.kt`), but the ViewModel and
with it the returning state, the window, and the decision are gone. **That gap exists today**: the
decision already lives in the ViewModel. Candidate A does not widen it and does not close it.

**Candidate B — move decision and delivery into the service.** The service has the fix stream
(`TrackRecordingService.kt:startRecording`, its own `locationTracker.fixes` collector) but
**none of the other inputs** (see 1.2): it would need to be told when returning starts and stops,
where the start point is, and to own the window and the cooldown; the ViewModel would become a
mirror of state the service owns, reached by intents or a process-scoped state holder in
`AppContainer`. That closes the swiped-away gap and is the shape the turnaround alert would
eventually want. It is also a re-plumbing of who owns navigation state, which the ViewModel's own
doc comment (`:81–93`) chose *not* to do when it accepted two listener registrations rather than
publishing the service's fixes back to the UI.

**Recommendation: A.** It fixes the failure the owner walked (stopped Activity, pocketed phone)
with one owned interface and the deletion of the composed path, and it is honest about the one
state it does not cover. B is the right answer to a different question — "should the service own
navigation?" — which is a decision this dispatch does not make and the light-budget work will
force anyway. **If the owner wants the swiped-away case covered now, that is B, it is larger than
this dispatch describes, and I stop there.**

### 1.2 What the decision needs, and where each input lives

| Input | Lives now | Composed? |
|---|---|---|
| The current fix | `locationTracker.fixes` collected in `viewModelScope` (`TrackRecordingViewModel.kt:270–292`) | No |
| The start point | `uiState.originWaypoint` (set by `createOriginWaypoint`, `:294–325`) else `breadcrumbPoints.firstOrNull()` (poll, `:249–258`) | No — ViewModel state, read via `uiState.value` |
| `isReturning` | `uiState.isReturning`, toggled by `startReturn`/`stopReturn` (`:221–246`) from the control pill (`MainActivity.kt:446–447`) | The *toggle* is a UI event; the *value* is ViewModel state |
| The rolling window | `recentReturnDistancesMeters` (`:110`) | No |
| The cooldown clock | `lastOffTrackAlertAtMillis` + `CurrentTimeProvider` (`:112`, `:449–452`) | No |
| The heuristic | `DetectOffTrackUseCase` (`domain/DetectOffTrackUseCase.kt`, pure) | No |
| The delivery | `LaunchedEffect` in `setContent` (`MainActivity.kt:309–313`) | **Yes — the only composed link** |

Nothing the decision needs is only available in the composition. Under A nothing moves but the
delivery; under B every row but the heuristic moves.

### 1.3 The no-recording case

Off-track cannot happen without a recording: `startReturn` refuses unless `isRecording`
(`:221–222`), the window is cleared on stop, and the fix collection only runs while a track is
active. So the recording's foreground service is the background runtime, and **this item does not
need to solve the no-recording case.** The finding the dispatch asked for: **the turnaround alert
would need background runtime without a recording** — no service, alarm, job or wake path exists
outside `TrackRecordingService`, and the light-budget pulse's §4 stands. Reported; not built.

### 1.4 `POST_NOTIFICATIONS` on API 33+

Today: requested once, when the user taps record (`MainActivity.kt:423–425`), through a launcher
whose callback does nothing by design (`:156–158`). On denial: `postOffTrackAlert` checks the
permission and returns without posting (`:491–496`); the direct vibration still runs (VIBRATE is
install-time, `:507`); the foreground service's own notice is simply not shown. Nothing records the
denial, nothing surfaces it, nothing re-asks. After two denials Android stops showing the dialog
and the launcher resolves denied immediately, so "requested once at record start" silently becomes
"never askable again" without the app noticing. `NotificationManagerCompat.areNotificationsEnabled()`
(API 24+, no permission) answers the question on every API level, including the pre-33 case of
the user turning the app's notifications off in settings.

**What a denial means for an alert the user relies on:** the shade entry and the heads-up are
gone; under Item 2 the vibration survives, because it does not go through the notification system.
So a denied permission degrades the alert to vibration-only rather than to nothing — which is
worth saying to the user once, and is exactly the moment Item 3 already owns.

**Proposal (not built):** fold it into Item 3's trip-start check — if `areNotificationsEnabled()`
is false, the warning says so in one clause; no re-prompt from this dispatch. A rationale-and-re-
ask flow is a separate decision.

### 1.5 Exactly one delivery

Under A the composed path is **deleted**, not bypassed: `offTrackAlertId` leaves
`TrackRecordingUiState` (`:77`; its only reader is the effect), the `LaunchedEffect` leaves
`MainActivity`, and `postOffTrackAlert`/`vibrateOffTrackAlert` move into the `AlertDelivery`
implementation. There is then one call site, in `returnToStart`, and one path. Proof: a fake
`AlertDelivery` in `TrackRecordingViewModelTest` counting invocations — the existing test
"going off-track bumps offTrackAlertId once, not once per fix" (`TrackRecordingViewModelTest.kt:376`)
becomes "delivers exactly once", and the cooldown and reset tests (`:403`, `:454`) become counts of
deliveries; the structural claim "reachable without a composed tree" is that this test has no
Compose rule in it. A second, Robolectric test drives the Android implementation and asserts the
posted notification and the vibration attributes (Item 2). No lifecycle simulation is offered as
evidence.

**Foreground behaviour is unchanged** in what the user sees — same notification, same pattern —
except the path. One visible difference to state: with the composed effect gone, an alert decided
while the app was stopped is delivered *then*, and no longer replayed on resume.

---

## ITEM 2 — the vibration survives a silenced phone

### 2.1 The API, at `minSdk 26`

From the SDK's own `api-versions.xml` (`/opt/android-sdk/platforms/android-37.1/data/`), not memory:

| Member | Since | Deprecated |
|---|---|---|
| `Vibrator.vibrate(VibrationEffect, VibrationAttributes)` | **33** | — |
| `Vibrator.vibrate(VibrationEffect, AudioAttributes)` | 26 | 33 |
| `Vibrator.vibrate(VibrationEffect)` (today's call) | 26 | — |
| `VibrationAttributes`, `.Builder().setUsage(USAGE_ALARM).build()` | 30 | — |
| `VibrationAttributes.createForUsage(int)` | 33 | — |
| `VibratorManager` / `.defaultVibrator` (today's S+ branch) | 31 | — |

`minSdk = 26`, `targetSdk = 37` (`app/build.gradle.kts:122–123`). **What that forces: two
branches.** API 33+: `vibrate(effect, VibrationAttributes.Builder().setUsage(USAGE_ALARM).build())`.
API 26–32: `vibrate(effect, AudioAttributes.Builder().setUsage(USAGE_ALARM)
.setContentType(CONTENT_TYPE_SONIFICATION).build())` — the only attribute-carrying overload that
exists there (a `VibrationAttributes` can be *built* from 30 but no `vibrate` accepts it before 33).
The existing `VibratorManager`/`Vibrator` split at 31 stays as it is.

*Testability, plainly:* Robolectric 4.16.1's `ShadowVibrator` exposes
`getAudioAttributesFromLastVibration()` and `getVibrationAttributesFromLastVibration()` (read from
the shadows jar with `javap`), so **the usage can be asserted on the attribute** as the dispatch
asks. But there is no `ShadowVibratorManager` in 4.16.1, which is why `OffTrackAlertTest` already
runs at `sdk = [30]` (`app/src/test/java/com/zynergylabs/forager/app/OffTrackAlertTest.kt`, its own doc comment).
So the 26–32 `AudioAttributes` branch is testable at sdk 30 and the 33+ `VibrationAttributes`
branch is **not reachable under Robolectric through the production code path**, since production
takes `VibratorManager` on 31+. I would test the 33+ attribute construction through a seam that
takes a `Vibrator` (the legacy service exists at 33 and is shadowed) and say so; the owner's
device is the only test of the real 33+ path. Named now so it is not discovered as a gap later.

### 2.2 Double-buzz and the channel

The channel `off_track_alert` is `IMPORTANCE_HIGH` with `enableVibration(true)` and the default
pattern (`MainActivity.kt:467–477`); the direct call adds the two-pulse pattern. **The channel's
pattern should go**, for two reasons: the double-buzz, and because a vibration routed through the
channel is the ringer-bound one — keeping it means the alert still half-depends on the ringer.

**A channel's vibration setting is immutable after creation** (the platform ignores changes to
`enableVibration` on an existing channel), so the migration is a **new channel id**
(e.g. `off_track_alert_v2`) created without vibration, and `deleteNotificationChannel("off_track_alert")`
for the old one. **Cost:** any per-channel adjustment the user made (importance, sound, lock-screen
visibility) is lost, and Android shows a "1 deleted category" line in the app's notification
settings. Today the app has one user, and the channel is a few days old. **Cost in tests:** the
existing assertion `createOffTrackNotificationChannel creates a HIGH-importance, vibrating channel`
(`OffTrackAlertTest.kt`) pins `shouldVibrate == true` and would change to `false` — an existing
assertion, flagged for authorisation, not absorbed.

### 2.3 Do Not Disturb

What I can establish from the platform's own constants (`api-versions.xml`): the interruption filter
has four states — `ALL`, `PRIORITY`, `ALARMS`, `NONE` (API 23). What follows is **inference from
AOSP's vibrator policy (`VibrationSettings`), not observation:** an alarm-usage vibration is
allowed under `PRIORITY` when the user's DND policy allows alarms (the default) and under `ALARMS`
by definition; under `NONE` ("Total silence") everything is suppressed, alarms included. Separately,
Android 13+ has a per-category "Alarm vibration" toggle under Sound & vibration that, if off,
suppresses alarm-usage vibration regardless of DND. So "usually survives DND" is right and
**"always" is not**: `NONE` and the per-category toggle are the two known ways it dies. Neither is
readable without more than Item 3's checks give (the filter is; the toggle is not). **Device list
item 3 is the only real test**, and the report should say the result cannot be predicted.
Notification policy access is not requested and not needed to *read* the filter (2.3 → 3.1).

### 2.4 What else vibrates

Nothing. `grep` of `app/src/main` for `Vibrat`, `performHapticFeedback`, `HapticFeedbackType`:
only `vibrateOffTrackAlert`. The recording channel `track_recording` is `IMPORTANCE_LOW` with no
vibration (`TrackRecordingService.kt:178–186`). No haptics anywhere in the Compose tree. Nothing to
change; reported.

---

## ITEM 3 — tell the user when the phone is silenced

### 3.1 Permissions

- **Ringer mode:** `AudioManager.getRingerMode()` — API 1, no permission. Values `NORMAL`,
  `VIBRATE`, `SILENT`. *Confirmed from the API data; the no-permission claim is from the platform
  documentation.*
- **Interruption filter:** `NotificationManager.getCurrentInterruptionFilter()` — API 23. Reading it
  requires **no permission**; only *setting* it requires notification-policy access
  (`isNotificationPolicyAccessGranted`). *From the platform documentation; the shadow exists
  (`ShadowNotificationManager.getCurrentInterruptionFilter`/`setInterruptionFilter`, read from the
  jar), so it is testable.* Nothing here asks for policy access.
- **Notifications enabled:** `NotificationManagerCompat.areNotificationsEnabled()` — API 24+, no
  permission — for 1.4's fold-in.

All three go behind one owned interface, `domain/AlertAudibility` (name to settle), with an Android
implementation and a fake — the same seam pattern as `LocationTracker`/`CompassProvider`.

### 3.2 Where "the start of a trip" is

Two candidates exist in the code: **recording start** — `TrackRecordingViewModel.startRecording`'s
success branch (`:118–135`), reached from the control pill's record toggle
(`MainActivity.kt:411–433`) — and **navigation start** — `startReturn` (`:221–224`), the first
moment the off-track heuristic can fire. **Proposal: recording start.** It is the one moment that
is unambiguously "the start of a trip", it is when the user has just chosen to rely on the app, it
is a screen-on moment by construction (they tapped), and it is where the app already surfaces a
one-shot message (3.3). Navigation start can be mid-trip in a pocket. The turnaround alert, when it
exists, will also key off the recording, not off returning.

### 3.3 The surface

What exists on or over the map, on `a2dcb21`:

1. **A one-shot `Toast`** for `startRecordingErrorMessage`, fired from a `LaunchedEffect` in the
   compact map tab (`AvailabilityScreen.kt:3186–3192`) — "an event, not a persistent condition",
   per its own comment. Exactly the moment Item 3 wants, but `Toast.LENGTH_SHORT` is about two
   seconds and a Toast cannot be read back by the user.
2. **A `SnackbarHost` on the map's own `Scaffold`** (`AvailabilityScreen.kt:1498`) and a second
   host in the drawer pane (`:2028`), both bound to `logDraftSnackbarHostState`, used today only
   for "Saved to Drafts / Discard" (`:935–950`).
3. `OfflineResultsBanner` (`AvailabilityResultsUi.kt:112,140`) — in the results list, not the map.
4. The HUD's status row and the compass strip — visible only while navigating / with a fix.

**Proposal: the existing map `SnackbarHost`, `SnackbarDuration.Long`, no action button.** It is
already positioned over the map, it survives long enough to be read, it dismisses on its own, and
"no action" is how the copy avoids telling the user to change a setting. The host state would be
shared (a host is a slot, not a message); a second `SnackbarHostState` is not needed. No new
surface.

### 3.4 Copy — proposed, not built

One sentence, true, no instruction:

- Ringer silent: **"Your phone is silenced. If you go off track, the alert may not be felt."**
- DND on (`PRIORITY`, `ALARMS` or `NONE`): **"Do Not Disturb is on. If you go off track, the alert
  may not be felt."**
- Both: the DND line (it is the stronger state).
- Notifications off for the app (1.4 fold-in, if taken): append **" Notifications are off for
  Forager, so it won't show on screen."**
- `RINGER_MODE_VIBRATE`: **no warning** — vibration is what the alert uses.

"May not be felt" is deliberately not "will not": under Item 2 the vibration is *meant* to survive
these states, and 2.3 says the outcome is not certain either way.

### 3.5 Mid-trip changes

The state can change after the check — the user silences the phone at the trailhead, after tapping
record. A one-time check misses that; a live watcher (`RINGER_MODE_CHANGED_ACTION`,
`ACTION_INTERRUPTION_FILTER_CHANGED` broadcasts) would catch it but would warn mid-trip, in a
pocket, which the dispatch rules out. **Trade-off:** the start-of-trip check covers the case the
owner actually hit — silenced earlier and forgotten — and misses the case where the silence is a
fresh, deliberate act, which is the case least in need of a warning. Not building a watcher.

---

## The distinction: override as a parameter

`Alert(kind: AlertKind, overridesSilence: Boolean)`; `AndroidAlertDelivery` picks `USAGE_ALARM`
when true and `USAGE_NOTIFICATION` when false, on both API branches. Off-track passes **`true`**,
because Item 2 and device list item 2 require the off-track vibration to be felt on a silenced
phone — even though the distinction section calls overriding "arguably rude" for an advisory. That
is a value for the owner to confirm; the *mechanism* is a parameter either way and costs exactly one
parameter. No preference, no turnaround alert.

---

## What "larger than described" would look like

Under A, nothing here is entangled with the composition beyond the one effect, and the service's
lifetime covers every state in which off-track can be decided except the destroyed-Activity one
that is already uncovered today. **Not larger than described.** Under B it is.

## Verification plan (for the build, after answers)

- `TrackRecordingViewModelTest` with a fake `AlertDelivery`: exactly one `deliver` per event; the
  cooldown re-fires once per window; `stopReturn` resets; no Compose in the test.
- Robolectric `sdk = [30]` test of the Android delivery: notification posted on the new channel;
  `ShadowVibrator.getAudioAttributesFromLastVibration().usage == USAGE_ALARM` for an overriding
  alert and `USAGE_NOTIFICATION` for a non-overriding one; channel `shouldVibrate() == false`; old
  channel deleted.
- `AlertAudibility` tests: `ShadowAudioManager.setRingerMode(SILENT)` → warning text pinned as a
  literal; `NORMAL` → null; `VIBRATE` → null; `setInterruptionFilter(PRIORITY)` → the DND text.
- Screen test: the snackbar text appears once after record start in the silenced state and not at
  all in the normal state (node count).
- Each new test run with its behaviour reverted; build log checked for compile errors; the forward
  edit confirmed present after each revert (both CLAUDE.md failure modes).
- **What no test here proves:** delivery with a real stopped Activity, the platform's handling of
  alarm usage in silent mode and under DND, and the 33+ `VibrationAttributes` production branch.

## Device list the owner must walk

1. Off-track, phone in pocket, screen off, recording running — must now fire.
2. The same walk, phone silenced — the vibration must be felt.
3. The same walk with Do Not Disturb on — **cannot be predicted from here**; report the result and
   which DND mode (Priority / Alarms only / Total silence) was set.
4. Foreground, as before — exactly one alert, one buzz pattern, not two.
5. The silenced-phone warning at record start, and its absence when the phone is not silenced;
   and on an API 33+ device, once with notifications denied.

---

## Required disclosure

### What I confirmed vs. what I inferred

Confirmed by reading `a2dcb21`: the decision's inputs and where each lives; the single composed
link; the existing direct vibration with no attributes; the channel's `enableVibration(true)`; the
`POST_NOTIFICATIONS` request site and empty callback; the service's contents and lifetime; the
`callbackFlow`-per-collector tracker; the four existing surfaces; that nothing else vibrates; the
`startReturn` recording guard. Confirmed from the SDK's `api-versions.xml`: every API level in 2.1
and 3.1. Confirmed from the Robolectric 4.16.1 jar: the shadow methods named, and the absence of a
`VibratorManager` shadow.

Inferred: that the recomposer pauses below `STARTED` (Compose's documented policy; matches the
device symptom); that the ViewModel's fix stream keeps flowing while backgrounded under the
location foreground service (platform rules; consistent with the existing device passes); the
ringer-silent and DND handling of alarm-usage vibration (AOSP policy from memory); that
`getCurrentInterruptionFilter` needs no permission (documentation).

### What I could not determine

Whether Doze on a long screen-off walk defers location delivery to the ViewModel's listener
differently from the service's (both are the same process and the same API; the recording's own
points arriving is the existing evidence); the behaviour of the per-category "Alarm vibration"
toggle on the owner's device; what the owner's DND policy allows.

### Premises in this dispatch that were wrong

- Base `41ce4e1` and baseline 1134 tests — `main` is `a2dcb21`, 1161 tests.
- "The vibration is part of the notification, not a separate limb" — it is already a separate
  direct vibration; both fired from one composed effect.
- "Move alert delivery out of the Activity's composition" is the right instruction, but "the
  decision" is not in the composition and does not need to move; the service is not the only, or
  the smallest, home.
- The pulse's "nothing fires while stopped" is right for delivery and wrong for the decision — the
  decision did fire; its result waited for resume.

### Anything I decided that this dispatch did not cover

- Recommending Candidate A over the service, and naming the destroyed-Activity gap as pre-existing
  rather than this dispatch's to close.
- Proposing recording start over navigation start for the warning, and the map Snackbar over the
  Toast.
- Proposing that off-track pass `overridesSilence = true` (per Item 2) despite the "arguably rude"
  note — flagged as the owner's value to set.
- Proposing to fold the notifications-disabled state into the Item 3 copy rather than a re-ask.
- The report reads `a2dcb21`, not `41ce4e1`.
