# Completion report: alert delivery — reach a pocketed phone, survive a silenced one

Follows `2026-09-06-alert-delivery-prebuild-report.md`. Same dispatch, same branch
(`claude/new-session-102gri` off `main` at `a2dcb21` — base discrepancy settled by the owner: their
SHA was stale). Built as the owner decided on every item.

## What the owner decided

- **Item 1:** deliver from the ViewModel through an owned `AlertDelivery` interface; delete the
  composed effect and the counter field. The swipe-away gap stays open and **is recorded at the
  delivery interface**, not only reported.
- **Item 2:** yes to the channel change (new id, no vibration, old channel deleted); two attribute
  branches at `minSdk 26` are unavoidable; the API 33+ production path being device-only is a real
  coverage gap to name.
- **Item 3:** recording start is the trigger, the map's `SnackbarHost` is the surface, both strings
  as proposed, notifications-off folded in as a third string, no re-ask flow.
- **Override value:** off-track passes `true`. "Arguably rude" was a distinction to preserve in the
  mechanism, not a policy for this case. The interface's doc must say the parameter is deliberate
  so nobody later hard-codes it.

## What was built

### New — domain (`app/src/main/java/com/forager/app/domain/`)

- **`AlertDelivery.kt`** — `enum AlertKind { OFF_TRACK }`, `data class Alert(kind, overridesSilence)`,
  `fun interface AlertDelivery { fun deliver(alert) }`. `Alert`'s doc states the override is
  deliberately a parameter of the call and must not be hard-coded in an implementation.
  `AlertDelivery`'s doc records why it exists (the composed effect that waited for resume) and, in
  full, **the swipe-away hole**: the decision dies with the ViewModel if the task is swiped while
  recording, this predates the change and is not fixed by it, and closing it means moving
  navigation ownership into the service.
- **`AlertAudibility.kt`** — `RingerMode`, `AlertAudibilityState(ringerMode, doNotDisturbOn,
  notificationsEnabled)`, `interface AlertAudibility { fun current() }`, and the pure
  `alertAudibilityWarning(state): String?` with the three owner strings as constants. Precedence:
  DND over silenced ringer; vibrate mode warns of nothing; notifications-off stands alone or is
  appended.

### New — Android (`app/src/main/java/com/forager/app/alert/`)

- **`AndroidAlertDelivery.kt`** — the three functions that lived at the bottom of `MainActivity.kt`,
  moved and changed: channel **`off_track_alert_v2`**, `IMPORTANCE_HIGH`, `enableVibration(false)`,
  and `deleteNotificationChannel("off_track_alert")` on every creation; `postOffTrackNotification`
  unchanged in content; `vibrateForAlert(context, overridesSilence)` picks the vibrator as before
  and calls **`vibrateWith(vibrator, overridesSilence)`**, the seam that carries the usage: API 33+
  `VibrationAttributes.Builder().setUsage(USAGE_ALARM | USAGE_NOTIFICATION)`, API 26–32
  `AudioAttributes` with the same usage and `CONTENT_TYPE_SONIFICATION`. Its doc names the
  coverage gap. The class creates its channel on construction.
- **`AndroidAlertAudibility.kt`** — `AudioManager.ringerMode`, `NotificationManager
  .currentInterruptionFilter` (any value but `ALL` is DND-on; `UNKNOWN` is logged and treated as
  not-on, a visible fallback), `NotificationManagerCompat.areNotificationsEnabled()`.

### Changed

- **`TrackRecordingViewModel`** — two new constructor dependencies, `alertDelivery` and
  `alertAudibility` (no defaults: production must wire them). `returnToStart` calls
  `alertDelivery.deliver(Alert(OFF_TRACK, overridesSilence = true))` where it used to bump the
  counter; the state copy no longer carries an id. `startRecording`'s success branch reads
  `alertAudibility.current()` once and sets `tripStartWarning` with a per-recording id.
- **`TrackRecordingUiState`** — `offTrackAlertId` removed; `tripStartWarning: TripStartWarning?`
  added, with `data class TripStartWarning(id, message)`. Doc explains why the alert no longer
  passes through state.
- **`AvailabilityScreen`** — new `tripStartWarning` parameter; a `LaunchedEffect(tripStartWarning?.id)`
  beside the existing `logDraftSnackbarHostState` shows it with `SnackbarDuration.Long`, no action.
  Threaded from `MainActivity`.
- **`MainActivity`** — the `LaunchedEffect(offTrackAlertId)` block, the `createOffTrackNotificationChannel`
  call, the two constants and three top-level functions, and seven now-unused imports removed; the
  ViewModel factory passes `container.alertDelivery` and `container.alertAudibility`.
- **`AppContainer`** — `alertDelivery = AndroidAlertDelivery(context)`, `alertAudibility =
  AndroidAlertAudibility(context)`, built at process start so the channel exists before any
  recording.

### Not changed, by the dispatch's own scope

The off-track heuristic and which stream it reads; the cooldown; `POST_NOTIFICATIONS` requesting
(still once, at record start, callback empty — the denial now reaches the user through the Item 3
string); the service; the turnaround alert; any notification-policy access.

## Tests

- **`TrackRecordingViewModelTest`** — a `RecordingAlertDelivery` fake shared by every ViewModel in a
  test; the four off-track tests count deliveries instead of reading a counter (renamed: "going
  off-track delivers exactly one alert, not one per fix", "staying on track never delivers an
  alert"; the cooldown and `stopReturn` tests keep their names), and the first also asserts the
  delivered value is `Alert(OFF_TRACK, overridesSilence = true)`. Three new: a silenced phone at
  record start sets the warning (literal copy); normal and vibrate modes set none; the same DND text
  on a second recording gets a higher id. No Compose anywhere in the class — the structural claim.
- **`AndroidAlertDeliveryTest`** (replaces `OffTrackAlertTest`, `sdk = [30]`) — channel `_v2` is HIGH,
  `shouldVibrate() == false`, legacy channel deleted (**the existing `shouldVibrate == true`
  assertion changed under Item 2**); notification posted on `_v2` with the same title/text; denied
  permission on 33 posts nothing; an overriding alert vibrates `[0,250,150,250]` with
  **`AudioAttributes.USAGE_ALARM` asserted on the attribute**; a non-overriding one carries
  `USAGE_NOTIFICATION`; and at `sdk = [33]`, through the `vibrateWith` seam, `VibrationAttributes`
  with `USAGE_ALARM`.
- **`AndroidAlertAudibilityTest`** — ringer normal/silent/vibrate through `AudioManager.setRingerMode`
  (the real setter, shadow-backed); each of `PRIORITY`/`ALARMS`/`NONE` reads DND-on and `ALL` off;
  `setNotificationsEnabled(false)` reads disabled.
- **`AlertAudibilityWarningTest`** — the six copy cases as literals.
- **`AvailabilityScreenMapIconStackTest`** — `setScreen` gains `tripStartWarning`; the warning shows
  exactly once as a Snackbar (node count 1, after `waitUntil`), and `null` shows nothing (count 0).

Forward, first run after the last compile fix: `TrackRecordingViewModelTest` 30, `AndroidAlertDeliveryTest`
6, `AndroidAlertAudibilityTest` 4, `AlertAudibilityWarningTest` 6, `AvailabilityScreenMapIconStackTest`
102 — 0 failures.

## Reverted variants

Each a one-line sed on the source, the build log checked for `e:` / `compileDebugKotlin FAILED`
before results were read (none in any of the seven — every reverted build compiled and ran), the
source restored from a copy saved before the sed, and the forward edit grepped for afterwards
(present in every case — the CLAUDE.md second failure mode).

| Revert | Predicted | Observed |
|---|---|---|
| `alertDelivery.deliver(...)` line removed from `returnToStart` | the three delivery-count tests fail at 0 | 3 failures, each `expected:<1> but was:<0>` |
| 26–32 branch: alarm usage → notification usage | the overriding-alert attribute test | 1 failure: `expected:<4> but was:<5>` (`USAGE_ALARM`=4, `USAGE_NOTIFICATION`=5) |
| 33+ branch: alarm usage → notification usage | the API-33 seam test | 1 failure: `expected:<17> but was:<49>` (`VibrationAttributes.USAGE_ALARM`=17, `USAGE_NOTIFICATION`=49) |
| channel `enableVibration(true)` restored | the channel test | 1 failure: "the channel must not vibrate: the direct alarm-usage call is the vibration" |
| `SILENT -> null` in `alertAudibilityWarning` | the silenced-copy tests and the ViewModel's silenced-phone test | 3 failures, all `expected:<Your phone is silenced. …> but was:<null>` (one as the missing prefix of the appended case) |
| `startRecording` no longer reads audibility | both ViewModel warning tests | 2 failures, `…but was:<null>` for the silenced and the DND text |
| Snackbar `LaunchedEffect` short-circuited | the Snackbar node-count test | 1 failure: `ComposeTimeoutException: Condition still not satisfied after 5000 ms` |

Every failure names a value only its own revert could produce; none is a stale result.

## Full suite

**1178 tests, 0 failed, 24 skipped** — up from 1161 on `a2dcb21`: −4 (`OffTrackAlertTest`
removed) +6 (`AndroidAlertDeliveryTest`) +4 (`AndroidAlertAudibilityTest`) +6
(`AlertAudibilityWarningTest`) +3 (ViewModel warning tests) +2 (Snackbar screen tests). Skip set
compared by (class, name) against the CI `SKIPPED_TESTS_ALLOWLIST` parsed from
`.github/workflows/ci.yml`: byte-identical, 24 = 24, nothing skipped outside the list and nothing
listed that did not skip. The `JournalTabTest` "From Album" flake did not fire in this run.

## What Robolectric cannot prove, said plainly

- Delivery with a **real stopped Activity**. The structural test shows the decision and the delivery
  need no composed tree; only the device shows the platform runs them with the screen off.
- The platform's handling of **alarm-usage vibration in silent mode and under DND**. The attribute
  is asserted; its effect is not.
- The **API 33+ production path** (`VibratorManager.defaultVibrator` → `vibrate(effect,
  VibrationAttributes)`). Robolectric has no `VibratorManager` shadow; the 33+ attribute
  construction is tested through the seam with a legacy `Vibrator` only. A real coverage gap.

## Device list the owner must walk

1. **Off-track, phone in pocket, screen off, recording running** — the failing case; must now fire.
2. **The same walk, phone silenced** — the vibration must be felt.
3. **The same walk with Do Not Disturb on** — cannot be predicted from here; report the result and
   which DND mode was set (Priority / Alarms only / Total silence).
4. **Foreground, as before** — exactly one alert and one two-pulse buzz, not two buzzes.
5. **The silenced-phone warning at record start**, its absence when the phone is not silenced, and
   on an API 33+ device once with notifications denied (the third string).
6. Also worth a glance: the app's notification settings should list one deleted category after the
   first launch of this build (the old channel), and the new "Off-track alert" category should show
   vibration off.

## Required disclosure

### What I confirmed vs. what I inferred

Confirmed: everything under "What was built" and "Tests" by reading and running; API levels from the
SDK's `api-versions.xml`; Robolectric's shadow surface from the 4.16.1 jar (including that
`ShadowAudioManager.setRingerMode` is protected, so the real `AudioManager.setRingerMode` is what a
test calls); the 33+ seam test actually receiving `VibrationAttributes` under Robolectric. Inferred:
that the composed effect was the whole failure (the pre-build report's reasoning; the device walk
confirms or refutes it); everything under "What Robolectric cannot prove".

### What I could not determine

Whether the owner's device delivers alarm-usage vibration under their DND policy; whether Doze on a
long screen-off walk delays the ViewModel's location listener (the recording's points arriving is
the existing evidence it does not, meaningfully).

### Premises in this dispatch that were wrong

Recorded in the pre-build report and accepted by the owner: the stale base SHA; the vibration being
"part of the notification"; the decision needing to move. Nothing new here.

### Anything I decided that this dispatch did not cover

- `INTERRUPTION_FILTER_UNKNOWN` is treated as DND-off and logged, rather than warning about a state
  the platform could not read.
- The legacy channel is deleted on **every** channel creation (idempotent), not once behind a flag —
  no flag, no persisted marker.
- `AlertDelivery`/`AlertAudibility` have **no default** constructor values on the ViewModel, unlike
  `errorLog`/`currentTime`: a production wiring that forgot them should fail to compile, not deliver
  nothing.
- The Snackbar shares the log-draft host state rather than getting its own; the pre-build report
  proposed this and the owner took the surface, but "shared host state" specifically was my call.
- Seven imports removed from `MainActivity` and one (`Context`) that the removals left unused.
