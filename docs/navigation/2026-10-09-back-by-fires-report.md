# Back by did not fire on the L3 walk: reproduction, record, and the walk logger mid-recording (dispatch 2026-09-28-796)

RECORD intent -796; the walk and the owner's answers -793 to -795; Back by's rulings -645 and -646. Branch
`back-by-fires`, cut from `origin/main` at `6373e2fe` (the base the dispatch names; verified equal to
`origin/main` at the start). Written by the coder, 2026-10-09 (UTC). **Complete as of 72a42693** (2026-10-10). The planner, opening PR #210, renamed
the last section from "PENDING" to "Device checks on the S22" and marked two items left open as such; no finding was changed.

## What the walk showed (from the record, not re-observed)

Back by set on the S22 at about 13:10 with "Pick a time" (the owner: "showed 1:55"); Return at 13:48;
off-track at 13:57:48; return ended by the walker at about 14:19. The owner: "No back-by alert felt or seen".
The planner's read of the S22's `dumpsys usagestats` for 2026-10-09 (not repeated here): interruptions for
`track_recording` at 13:09:45 and `off_track_alert_v2` at 13:57:48, and **no `back_by_alert` interruption**.
So nothing was posted at all; the question is why no post, not why a post was silent.

## Reproduction (step 1)

Run 1, targeted (`TrackRecordingServiceBackByLoopTest`, `AvailabilityScreenQuickSettingsTest`): compiled
clean, 18 tests, 2 failures.

| Case | Test | Result |
|---|---|---|
| (a) a future time set after the service began, reached through its 15 s loop | `a future time set after the service has begun fires through the service's own loop` | passed |
| (b) the same with a Return in progress, 2 km from the start | `with a Return in progress and the walker far from the start, the time still fires through the loop` | passed |
| (c) do the screen and the service share one watch | read, not tested: `MainActivity.kt:63` (`container` is the application's), `:234` (`backBy = container.backByWatch`); the service reads `(application as ForagerApplication).container.backByWatch` (`TrackRecordingService.kt:184, 275`) | same instance |
| the suspected mechanism, modelled | `a GPS fix after the time brings the alert without waiting for the 15 s timer` | **failed**: "no back-by alert within 3 s of a GPS fix 1 ms past the time: the alert waits for the 15 s timer, not the fix" |
| an afternoon time picked on the dial reaches the ViewModel as 13, not 1 | `Pick a time, an afternoon hour picked …` | failed on the harness (the minutes dial was not reached); **not resolved in this dispatch** |

**What the failing case shows, and what it does not.** Back by is evaluated only by the service's own
coroutine loop, `delay(BACK_BY_TICK_MILLIS)` (`TrackRecordingService.kt:259-268`, `:531`); a fix is stored
(`BackByWatch.onFix`, `BackByWatch.kt:105-108`) and never triggers an evaluation. The recording holds no wake
lock (the only one in the app is the debug walk logger's, `WalkLogger.kt`, which did not start on this walk,
RECORD -795). A coroutine `delay` on `Dispatchers.Default` is measured on the monotonic clock, which does not
advance while the CPU is suspended, and nothing wakes the CPU for it. **Inferred, not observed:** with the
screen off, the S22 suspended, and the timer did not reach a tick past 13:55 before the walk ended. Supporting
evidence from an earlier measurement on the same phone (`2026-10-09-battery-saver-report.md:126-139`): a
screen-off recording spent about half its time in light and half in full Doze, and in one run the platform
stopped the app's GPS 5 minutes in. Not confirmed for the walk itself: the S22 desk cases did not reproduce a stall (see Cases B and C below).

**A correction to run 1, found in run 2.** Run 1's failing fix test was not evidence. It sent its second
fix in the same wall-clock second as the first, and Robolectric's location shadow drops a fix that comes
less than the registration's 1 s interval after the last one, so no fix after the time reached the service
at all. Run 2 found this when the fix path was built and the test stayed red. The record the test now prints
showed no fix-woken evaluation. The test now spaces its simulated fixes a second apart. The claim that Back by
ignored fixes still holds, read from the code (`BackByWatch.onFix` returned `Unit`, `BackByWatch.kt:105-108`
at `6373e2fe`). That claim was proven by revert R1 below, not by run 1.

**The owner's answers (RECORD -797):** the menu read "1:55 PM"; the strip line "Didn't look". The picker test
now passes: a picked 1:55 on a clock opened in the afternoon reaches `BackByChoice.AtTime(13, 55)`. So the
time was this afternoon's, not tomorrow's.

**The S22's own reading (the planner's, read-only, cited as consistent with a stalled timer, not proof):** the
screen was off from 13:48:37 to 13:57:10, across 13:55. It woke briefly at 13:57:10, 13:57:58, 14:01:27 and
14:12:39. There was no `back_by_alert` post. batterystats was reset at 14:54, when the phone was plugged in,
so it holds no history of the walk.

## The fix (step 2): the owner's option B (RECORD -797: "Check on GPS + wake-up alarm (Recommended)")

For Back by, and the same for the sundown alerts (the owner: "Yes, same fix (Recommended)"):

1. **A fix checks.** `BackByWatch.onFix` returns `true` once the time has come and its alert has not
   been given. The service then evaluates at once (`TrackRecordingService`'s collector, `EvaluationTrigger.FIX`).
   `SundownWatch.onFix` returns `true` once the next alert's due time has passed, as well as on the first fix
   as before. It takes that due time as used, so each due time triggers one evaluation, and the evaluation
   works out the next due time.
2. **A wake-up alarm.** `AndroidWakeUpAlarms` (`alert/AndroidWakeUpAlarms.kt`):
   - It uses `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)`, which is inexact and needs no permission.
     `setExactAndAllowWhileIdle` needs `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, which -646 chose not to ask
     for. The inexact alarm may come minutes late in deep Doze.
   - Delivery goes to a non-exported `WakeUpAlarmReceiver`, not to the service. The platform keeps the phone
     awake while a receiver handles its alarm, and through `goAsync` until it finishes (here at most 8 s). The
     app holds no wake lock of its own (release has no `WAKE_LOCK`), so a service coroutine woken by the alarm
     could go back to sleep before it posted anything.
   - Back by asks for the alarm on every set and "+30 min". It cancels it on Clear, I'm back, arrival after
     Return, Stop and the service's destruction.
   - Sundown sets the alarm, after each decision, for the next alert not yet given: the heads-up 30 min before
     the leave-by time, the leave-by time, or sunset. It asks again only when that time moves by more than a
     minute. It cancels the alarm when all three are given, the alerts are off, the walker has arrived, or the
     recording ends.
   - Each alert still fires once, and nothing about wording changed.
3. **The restart path.** A process death ends the recording, as before. An alarm left behind then starts a
   fresh process. Its receiver finds nothing watched, records `alarm-delivered nothing-set`, and does nothing.

## Observability (step 3)

- `files/back-by-record.log` (`FileBackByRecord`) writes one line per event:
  - started, and set (with the time, the track, and accepted or refused with the track being watched);
  - set-ignored with no recording on the screen (the `:315` no-op, which now also logs a warning);
  - +30 min, and each evaluation (due or not; by timer, fix or alarm);
  - fired, with its delivery result;
  - ended, by arrival, I'm back, Clear, Stop or service destroyed;
  - alarm-scheduled, alarm-cancelled and alarm-delivered.
- `files/sundown-record.log` (`FileSundownRecord`) writes the same kinds of line for the sundown alerts.
- No positions are written in either file. The service test checks every line for coordinates.

## The walk logger (step 4)

The Diagnostics switch now tells the logger when it changes (`WalkLogger.onSwitchChanged`, debug source set).
Switched on during a recording, the log starts for that recording at once. Switched off, it stops with
`END reason=switched-off`. Each recording start writes "Walk logger switch at recording start: on|off (track …)"
to diagnostics.log. The row's second line now reads "…Takes effect at once, also during a recording."

## Tests and revert checks

Targeted run 2 (after fixing two test compile errors and the fix spacing above): 17 classes, 110 tests,
0 failures, read from the JUnit XML.

Every revert check followed the same procedure. The runner refuses results when the build log has compile
errors. It restores the file from a copy saved before editing, never from git. `git status` was clean against
the pushed head afterwards.

| | Revert | Failed with |
|---|---|---|
| R1 | Back by fix check off (`onFix` … `&& false`) | "no back-by alert within 3 s of a GPS fix 2 ms past the time…"; "at the time" |
| R2 | Back by alarm delivery evaluates nothing | "no back-by alert within 3 s of the wake-up alarm, delivered 6 ms past the time"; "fired by the alarm… expected:<[…]> but was:<[]>" |
| R3 | Back by alarm never scheduled | "a wake-up for Back by: []" (and the alarm-path test and record story, both needing the alarm) |
| R4 | sundown fix check off | "past the due time, a fix asks" |
| R5 | sundown alarm never scheduled | "List is empty" in four tests: the list of `AlarmScheduled` records, read by the shared setup. This message is generic but names that list |
| R6 | sundown alarm delivery evaluates nothing | "expected:<[HEADS_UP]> but was:<[]>" |
| R7 | Diagnostics switch does not tell the logger | "switched on mid-recording, the log starts for that recording" |

**No local full suite.** The planner ruled it out: root had 1.7 GB free, and the last full run here dipped to 0.94 GB. The full suite runs in CI on this branch's pull request. CI is not triggered by a push to this branch (`.github/workflows/ci.yml` runs on pushes to main and pre-main, and on pull requests), and this dispatch said no PR. So the full suite stays **unrun** until a PR is opened.

`assembleDebug` built at `54274939` (1 min 30 s; free space 1.7 GB before, 1.4 GB after).

`scripts/verify-policy-permissions.sh`: all checks passed, so the manifest gained a receiver and no permission.

## Alerts cleared when a recording ends (RECORD -800, -801)

The owner, verbatim: "Yes, add it now (Recommended)" (-800: sundown id 1003 and Back by id 1004 cleared when a
recording ends any way, including onDestroy, as far as Android allows), and "Yes" to "should off-track clear the
same way?" (-801). Built at `b25145e4` (-800) and `f7eb14b3` (-801), finished at `766c17fb`:

- `cancelRecordingAlerts` (`alert/AndroidAlertDelivery.kt`) cancels 1003, 1004 and off-track 1002.
- Called from three places: `stopRecording` (it replaces the old Back-by-only cancel), `onDestroy`
  (`service/TrackRecordingService.kt`), and `ForagerApplication.onCreate` through `clearStaleRecordingAlerts`.
  The last is for a killed process, whose `onDestroy` never runs. A new process holds no recording, so any of
  these alerts in the shade at process start is left over from one that has ended. A failure there is logged
  with a warning and does not stop the app starting.
- Answering Back by with "I'm back" still takes down only the Back by alert.
- Off-track 1002 is posted only by the return watch, which the recording service owns
  (`AndroidAlertDelivery.kt:170`, `AppContainer.kt:383`). So clearing it on end cannot remove an alert that
  belongs to anything still running.
- Tidy at `766c17fb`: `b25145e4` had put the new function's KDoc between the sundown channel's KDoc and
  `SUNDOWN_CHANNEL_ID`, which left the channel note sitting over the function. It is moved back. No code
  changed.

Tests: `TrackRecordingServiceAlertClearTest` (4) runs the real service and application. It covers Stop,
`onDestroy` without a Stop, `Application.onCreate` with the alerts left in the shade, and "I'm back" leaving the
other two alerts. The targeted run (`alertclear-2`, after the restart) had 9 classes and 45 tests, with 0
failures. The classes were AlertClear 4, BackBy 6, BackByLoop 6, Sundown 3, SwipeAway 9,
TrackRecordingServiceTest 3, StartBackNow 4, AlertDeliveryOutcome 5 and SundownNotificationText 5. Results were
read from JUnit XML written by that run (the results directory was emptied first), and the build log had no
compile errors. The run before the restart (13 tests, green) covered `b25145e4` only.

Revert checks, one edit each. Each run tested `TrackRecordingServiceAlertClearTest` only. Every build log shows
`compileDebugKotlin` and `compileDebugUnitTestKotlin` ran with no `e:` lines, and the build failed only at
`:app:testDebugUnitTest`. The runner refuses to read results when the log has compile errors, and none did.
Each file was restored from a copy saved before the edit, never from git, and checked byte-equal to that copy.
Afterwards `git status` was clean against the pushed head, and the forward lines were present (counted with
grep).

| | Revert | Failed (only) | With |
|---|---|---|---|
| AC1 | Stop: `cancelRecordingAlerts(this)` put back to the old Back-by-only cancel | the Stop test | "the sundown alert is still in the shade after Stop" |
| AC2 | `onDestroy`: the cancel line removed | the onDestroy test | "the sundown alert is still in the shade after onDestroy" |
| AC3 | process start: the `clearStaleRecordingAlerts()` call removed | the new-process test | "the sundown alert is still in the shade after the process started" |
| AC4 | off-track: `manager.cancel(OFF_TRACK_NOTIFICATION_ID)` removed | Stop, onDestroy and new-process tests | "the off-track alert is still in the shade after Stop" / "…after onDestroy" / "…after the process started" |

Each failure is one that only its own revert could cause: the test named matches the path reverted, and AC4's
three name only the off-track alert. The "I'm back" test passed under every revert. It is a negative check, so
no revert here could be expected to fail it.

`assembleDebug` built at `766c17fb` (2 min 23 s). Gradle and the Kotlin daemon were stopped afterwards. Disk
before the build: root 28 GB free, /mnt/work 29 GB, falling to 16 GB during the build because the planner's
own flash-drive copy was landing (the planner confirmed it).

Logs (outside the repository): `~/.cache/forager-gradle-logs/alertclear-2.log`, `revert-AC1-stop-clear.log`,
`revert-AC2-ondestroy-clear.log`, `revert-AC3-process-start-clear.log`, `revert-AC4-off-track-clear.log` and
`bbf-assemble-2.log`.

S22 for this change (2026-10-10, 1.0.3149+g766c17fb, on USB, screen on, driven over adb; evidence outside the
repository at `~/Zynergy/device-evidence/2026-10-09-back-by/launch-check-2.txt` and `desk-clear-on-stop.txt`):

- **Launch check: PASS** (`scripts/s22-launch-check.sh`). `install -r` succeeded, status=verify, cold launch,
  process alive after 8 s, crash buffer empty.
- **Desk check, Stop clears the alert: PASS** (run 2). The steps:
  - Record at 14:11:13Z.
  - Quick settings > Back by > Pick a time > 7:15 AM: `set … at=14:15:00Z accepted` and `alarm-scheduled`.
  - At 14:15:00.290Z it was evaluated due `by=fix`, and at .306 it `fired … notification=posted vibration=done`.
  - The active notification list (read from the "Notification List" section only) then held ids 0, 1001 and 1004.
  - Stop at 14:15:11Z gave `ended reason=stop` and `alarm-cancelled` in both records. The active list then held
    no Forager notification, and no recording service was running.
- Run 1 (Back by 7:10, fired by fix at 14:10:03Z, Stop at 14:10:28Z, `ended reason=stop`) is **not cited** for
  the clearing. My notification read there counted the whole `dumpsys notification`, archive included. 1004
  appeared both before and after Stop, and only the archive explained it after Stop. So run 1 could not tell
  "active" from "archived", and run 2 was done with a reader limited to the active section. The positive
  control: it showed 1001 alone once the recording had started, and 1004 once the alert fired.
- Not checked on the phone: the onDestroy, process-start and off-track paths (JVM tests and revert checks only).
  At a desk, off-track needs a Return and a walk away from the start, and Return at the start ends Back by on
  arrival.
- Disclosed: one `dumpsys notification --noredact` was run with its output sent to `/dev/null`. Nothing was
  printed or kept, and it was not needed. The dispatch's no-`--noredact` rule is written for the usagestats
  read, and it was kept here too in every read that was printed.

## Confirmed vs inferred

- **Confirmed by reading code at `6373e2fe`:**
  - Back by was evaluated only by the service's 15 s coroutine timer (`TrackRecordingService.kt:259-268`), and a fix only stored a position (`BackByWatch.kt:105-108`).
  - The screen and the service share one watch (`MainActivity.kt:63, :234`).
  - The recording held no wake lock in release.
  - Sundown had the same timer-only shape, apart from the first fix (`SundownWatch.kt:182-188`).
- **Confirmed by test, with revert checks:** the fix path and the alarm path, for Back by and for sundown, and the walk logger following its switch.
- **Confirmed on the S22:** Back by fires with the screen on (Case A rerun). Arrival after Return ends Back by and cancels its alarm, and the same holds for sundown (Case A, first run). The time picked on the dial is the afternoon's.
- **Inferred, not proven:** that on the L3 walk the timer stalled while the S22 slept. This rests on the screen being off from 13:48:37 to 13:57:10, no `back_by_alert` post, an earlier measurement of Doze on this phone, and the code. batterystats from the walk was reset, so it cannot confirm it.

## Could not determine

- Whether the S22 was in light or deep Doze at 13:55 on the walk (no history survived).
- Whether the timer stalls on this phone with a recording running: it did not at the desk, even in forced deep idle, so the walk's stall stays inferred. The alarm came about 7 min late in both re-run cases, and no device run has yet had the alarm do the firing.
- Whether the full suite passes: it was not run locally, by the planner's ruling, and no PR was opened.

## Premises that were wrong

- The dispatch's desk step, "start a recording, set Back by…, tap Return, wait. It must fire", cannot fire at a desk. Arrival after Return ends Back by (-646), and a phone at the start has arrived. Case A's first run showed exactly that.
- Run 1's red fix test was taken as showing the fault. It showed a harness artefact: two fixes in the same second, with the second dropped by Robolectric. Corrected in run 2 and proven by R1.
- The dispatch said the S22's walk logger "starts when switched on mid-recording" needed a change. It did: the switch was read only at the start (`WalkLogger.kt:66`), as -795 found.

## Decided beyond scope

- The per-evaluation line in both records, so a device check can see whether the timer ran. Not in the dispatch's list.
- The alarm is delivered to a broadcast receiver rather than to the service, because the platform keeps the phone awake for a receiver and the app has no wake lock.
- Sundown re-asks for the alarm only when its due time moves by more than a minute (`ALARM_RESCHEDULE_SLACK_MILLIS`), so a walk-back estimate that changes slightly each tick does not reschedule every 15 s.
- The Diagnostics row's second line now reads "…Takes effect at once, also during a recording." (debug only).
- The sundown record file `sundown-record.log`, a sibling of the Back by record. The owner asked for "the same logging" without naming a file.

## Device checks on the S22

- S22 launch check: **passed** (`scripts/s22-launch-check.sh`). Install -r, 1.0.3139+g54274939, status=verify, cold launch, process alive after 8 s, crash buffer empty. Output at `~/Zynergy/device-evidence/2026-10-09-back-by/launch-check.txt` (outside the repository).
- **S22 desk check, Case A, first run (with Return): Back by did not fire, as -646 rules. Recorded as a pass for the arrival path.**
  I drove the screen over adb after the owner removed the screen lock. The steps:
  - Record at 17:45:29 PDT.
  - Back by > Pick a time > 5:50 PM. The menu read "Back by 5:50 PM", and the record shows the time set (accepted) and its alarm scheduled.
  - Return at 17:47:20.
  The next evaluation, at 17:47:29, found the phone at the start after Return, which is arrival. The record shows `ended reason=arrival` and `alarm-cancelled`. The sundown record shows the same rule at work: leave-by fired on the first evaluation, with the notification posted and the vibration done. Its alarm was set for sunset at 6:35:35 PM and cancelled on arrival at 17:47:30.
  **The dispatch's desk step ("tap Return, wait. It must fire") cannot fire at a desk.** Arrival after Return ends Back by by ruling, so every desk case after that ran without Return. "Fires during a Return away from the start" is therefore covered **only by the JVM test** (case (b), 2 km out), not on the phone.
- **Case A, rerun (no Return, screen on): PASS.** "Stop navigating" at 17:53:19 ended the return and kept the recording running. Back by was set for 6:00 PM at 17:54:04, accepted, with the alarm scheduled. At 18:00:00.021 the timer evaluated it as due, and at 18:00:00.031 it fired with `notification=posted vibration=done`. The shade showed "You planned to be back by 6:00 PM" with "I'm back" and "+30 min". "I'm back", tapped over adb, gave `ended reason=im-back` and `alarm-cancelled`.
- Disclosed: my first UI dump printed the map strip's grid-reference readout once to my session transcript. Nothing was committed, and the dump file was deleted. Later dumps hide anything position-like.
- **Cases B and C (screen off, then forced deep Doze): NOT RUN validly. No result for either.** A script drove both over Wi-Fi adb once the S22 was unplugged at 19:49:40 PDT.
  - Case B's Record tap at 19:50:08 started no recording: the service count read 0. The app was brought to the front (`START u0 … MainActivity`, logcat 19:50:01). Why the tap did not start a recording is not determined.
  - The Back by step that followed set nothing. The newest line in `back-by-record.log` is still Case A rerun's `ended reason=im-back` at 01:03:17Z (18:03 PDT). The script's "record:" lines at 19:50:22 and 20:10:15 re-printed Case A's 17:54 `set` line, and that is not evidence of a new one.
  - So B (screen off 19:50:24 to 20:10) and C (forced deep idle 20:10:49 to 20:30:00, then unforced) ran with no recording and no Back by. Neither says anything about the alarm in Doze.
  - The script fault: it logged the service count and carried on instead of stopping on 0, and its record read did not filter to lines after the set. Evidence outside the repository: `~/Zynergy/device-evidence/2026-10-09-back-by/caseBC.log` and `batterystats-history.txt`. The latter is from runs with no recording, so it is not useful here.
  - **Re-run, Cases B and C, with a fixed script.** The script stops if: the recording does not start; Forager is not the resumed activity before any tap (positive-controlled, it stopped with the launcher in front); there is no `set` line newer than the set step; or the record has nothing newer than the set. Forager is brought to the front with an explicit intent before each sequence, and controls are found by label. Phone unplugged at 20:40:00 PDT, recording started at 20:40:11.
  - **Case B, screen off, on battery: PASS.**
    - Back by 8:50 PM set at 20:40:32 (accepted, alarm scheduled); screen off at 20:40:38, deep=ACTIVE light=ACTIVE.
    - At 20:50:08.713 it was evaluated due **by=fix**, and at 20:50:08.741 **fired, notification=posted vibration=done**: 8.7 s after the time.
    - The wake-up alarm was delivered at 20:57:05.820, **7 min 6 s late**. It evaluated by=alarm and did not fire again.
    - The 15 s timer kept running throughout, with gaps growing to about 20 s, so the phone did not stall here. B proves the fix path on the device. It does not prove the alarm rescuing a stalled timer.
  - **Case C, screen off, then forced deep idle: PASS.**
    - The first attempt stopped at its guard at 21:00:05, before any tap. With Back by set, the gear's label becomes "Quick settings, Back by set", and the lookup was an exact match. Changed to a substring match. C was then run alone on B's recording.
    - Back by 9:10 PM set at 21:00:59 (accepted, alarm scheduled); screen off at 21:01:05. At 21:01:35 `deviceidle force-idle` gave deep=IDLE, light=OVERRIDE, and it was still IDLE when read at 21:20:01.
    - At 21:10:11.803 it **fired by=timer, notification=posted vibration=done**: 11.8 s after the time.
    - The alarm was delivered at 21:16:45.436, **6 min 45 s late**, and did not fire again.
    - Stop at 21:20:07 cancelled the Back by notification (the archive shows id 1004 removed).
  - **What B and C do not show.** On this desk the timer was never seen to stall, even in forced deep idle (33 timer evaluations, the longest gap about 22 s). The likely reason, inferred and not measured, is that the recording's active GPS request keeps the CPU awake. So the walk's stall is still inferred, not reproduced, and on the device no Back by has yet been fired *by* the alarm. The alarm's measured slack, about 7 minutes, is the upper bound on lateness if the timer and fixes both stall; the target was "within a few minutes". **Decision for the owner:** whether 7 minutes is acceptable, or whether the exact-alarm permission is worth asking for (the owner chose inexact in option B).
- **"Something happened still": no sundown alert was re-posted (re-post investigation).** The owner: "That was swiped long ago. So something happened still"; on the text, "Don't remember exactly".
  - **There is one post path, and it always writes the record.** Id 1003 is posted only by `postSundownNotification` (`AndroidAlertDelivery.kt:315-331`). Its one caller is `deliverReporting`, and the one sundown caller of that is `SundownWatch.tick` (`SundownWatch.kt:323`), which writes `Fired` straight after (`:326`). `sundown-record.log` has one `fired` line all evening, the leave-by at 17:45:29. So by the code and the record, 1003 was posted once. The post has no `setOnlyAlertOnce` or `setSilent`, and nothing re-posts it on service start, first fix or alarm.
  - **usagestats, all apps, 17:45 to 19:50.**
    - Forager's notification events: interruptions at 17:45:30 (track_recording, sundown_alert) and 18:00:00 (back_by_alert); NOTIFICATION_SEEN at 17:45:31, 18:00:01, 18:02:51 and 18:03:00.
    - Then `FOREGROUND_SERVICE_STOP` at 19:05:35, then nothing until **USER_INTERACTION at 19:48:55**.
    - Other apps between 18:30 and 19:49: Facebook at 18:33:56 and Gmail SEEN at 19:48:53, which is the shade being opened two seconds before.
  - **The recording notification has no sunset line.** It reads "Recording a track" / "Forager is tracking your location in the background." (`strings.xml:6-7`), and it ended at 19:05:35.
  - **Samsung.** Notification reminders are off (`notification_reminder_selectable=0`). Snooze is offered in the shade (`show_notification_snooze=1`), and the snoozed list is empty now.
  - **Archive** (`dumpsys notification p <pkg>`, no --noredact, newest first): an autogroup summary, 1001 (recording), 1004 (Back by), 1003 (sundown), and an older autogroup summary on the sundown channel. The plain dump has no post or removal times, so I cannot tell which night's removal each is. No sundown alert was posted during B or C (the record has no `fired` line, and usagestats shows only the 20:40:06 and 20:50:08 interruptions). So the 1003 in the archive is most likely the 17:45 one, removed at some point before 21:20.
  - **Most likely source (inferred).** The 17:45 leave-by, titled "Sunset at 6:35 PM", was still in the shade at 19:48, and what was swiped earlier was something else: its heads-up pop-up (swiping a pop-up sends it to the shade, it does not dismiss it), or another of Forager's notifications.
    - The evidence: at 19:48:55 something of Forager's was interacted with two seconds after the shade opened. The recording's notification was gone (19:05:35), and the Back by alert had been taken down by "I'm back" at 18:03:17 (`TrackRecordingService.kt:295`). So by elimination, 1003 was the only Forager notification that could still be there.
    - Caveat: I have not confirmed that a dismissal on this One UI does not count as USER_INTERACTION. If it does, the interaction could be the swipe itself.
  - **One desk step settles it.** Start a recording in the afternoon so a heads-up or leave-by fires. Swipe it the way the owner did and read `dumpsys notification p com.zynergylabs.forager.app` for id 1003 straight after. Then stop the recording, wait 15 min, and read it again. If 1003 is listed after the swipe, the swipe hid only the pop-up. If it is not listed after the swipe but appears later, something re-posted it, and the sundown record will say whether Forager did.
  - The owner's ruling, "Yes, clear on kill too (Recommended)": when a recording ends any way, including as far as Android allows on a kill, its alerts are cleared. **Not built.** It waits on the desk step above.
    - **Superseded:** built on the owner's -800 answer, "Yes, add it now (Recommended)", and extended to off-track by -801. See "Alerts cleared when a recording ends" above.
- **"Sunset triggered" at the 19:49 unplug (the owner): no sunset alert fired. It was the 17:45 leave-by alert, left in the shade.**
  - Record and history: `sundown-record.log` shows the leave-by `fired … notification=posted vibration=done` at 17:45:29 and its sunset alarm cancelled on arrival at 17:47:30. After that it has only timer evaluations, the last at 19:05:32, with no `fired alert=sunset`. In usagestats, Forager's only `sundown_alert` interruption all evening is 17:45:30. Nothing from Forager was posted after 19:05:35, in either record or in usagestats.
  - The recording stopped at 19:05:35 (`FOREGROUND_SERVICE_STOP`). By the coordinator's account, I stopped it on the coordinator's instruction ("Stop the 17:45 test recording, since it's yours"). I cannot see that exchange in my own context, and the phone's logs neither confirm nor contradict it: logcat's adb command lines only begin at 19:45.
  - Why it read as sunset: the leave-by and heads-up alerts are titled "Sunset at %1$s" (`res/values/strings.xml:20`, used at `AndroidAlertDelivery.kt:358`). So the 17:45 alert read "Sunset at 6:35 PM".
  - The alert was posted at 17:45 with the phone on USB and the screen on, so it is not a screen-off or Doze result.
  - At 20:35 Forager had nothing in the shade. When the alert was removed, and by whom, is not determined.
- **Finding: ending a recording leaves a sundown alert in the shade. Decision for the owner.**
  - Nothing in `main` cancels `SUNDOWN_NOTIFICATION_ID` (1003, `AndroidAlertDelivery.kt:284`, `:330`). The alert has autoCancel, so it goes only when tapped.
  - `stopRecording` (`TrackRecordingService.kt:360-372`) and `onDestroy` (`:159-163`) end the watches and cancel their alarms, but only the Back by alert is taken down (`:372`, with the owner's "Cancel automatically").
  - So tonight's alert still read "Sunset at 6:35 PM" two hours later, after the recording had ended. The owner read it as a new alert.
  - Related: `onDestroy` does not cancel the Back by alert, so a process killed rather than stopped can leave that alert in the shade too.
  - Not changed in this dispatch.
  - **Superseded:** changed after all, under -800 and -801. See "Alerts cleared when a recording ends" above.
- **Finding: the wake-up alarm receiver cannot post without a recording (read from the code, not tested on the device).**
  - `WakeUpAlarmReceiver` (`AndroidWakeUpAlarms.kt:75-105`) only calls `onAlarm()`.
  - With nothing watched, both watches return before any post: `BackByWatch.kt:147-151` and `:232-234`, `SundownWatch.kt:198-205` and `:230-232`. The watch is empty after `end()` and in a fresh process started for a leftover alarm. They write `alarm-delivered` and nothing else.
  - `end()` also cancels the alarm (`BackByWatch.kt:115-124`, `SundownWatch.kt:184-194`).
  - Swiping the app away: no `onTaskRemoved` exists. So, inferred, the foreground recording carries on after a swipe. If the process is killed instead, an armed alarm finds an empty watch and posts nothing.
- **Still open:** how late the allow-while-idle alarm comes on this phone in light and deep Doze. Firing with the screen off is covered by the JVM alarm-path tests (R2, R3) and is not confirmed on the device.
