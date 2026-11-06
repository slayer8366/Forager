# off-track-reminder: a Settings checkbox, and a check at Record that the reminder can buzz (plan T14)

Dispatch 2026-09-28-626 (RECORD -626, `prompts/preserved/2026-10-07-01.md` on `records-after-173`),
amended by Amendment 1 (RECORD -627, the owner's four answers). Branch `off-track-reminder`, cut from
`origin/main` at `d23aeaa6` (PR #183). Not merged; no pull request opened. Laptop coder, no phone,
no adb. Written 2026-10-07 (UTC, from `date -u`).

## What the walker sees now

- **Settings**, under Sundown: a checkbox, **"Off-track reminder: your phone buzzes if you head
  away from your start."** It is on by default and is remembered across restarts.
  - Unticked, the off-track alert does not fire. The Return button still turns its off-track colour.
    The alert's text, sound and vibration are unchanged when it is ticked.
- **Record**, with the reminder on, on a phone set to **Restricted** for Forager (Android 9 and
  later): one prompt in the map's message bar, **"To make sure your off-track reminder can buzz, let
  Forager run in the background"**.
  - A tap anywhere on it opens Forager's own **App info** page. There, Battery > Unrestricted is the
    change. Back returns to the app (from the code; a phone check is listed below).
  - It shows once. It does not show again on later walks while the phone stays Restricted. If the
    phone is later allowed and then Restricted again, the next Record shows it again.
  - Allowed phone: nothing is shown. Reminder off: nothing is shown or checked.
- No hedging lines anywhere.

## Verify first, the stop, and the owner's answers

The verify went to the planner by message before any Gradle run. In short:

- **Base.** `origin/main` was at `d23aeaa6`, as the dispatch stated. The cited lines held, except
  that the alert's two strings are `strings.xml:22-23` (21 is the channel name).
- **Stop: there was no off-track option in Settings, and nothing turned the alert on or off.**
  Settings is `SettingsContent` (`ui/availability/AvailabilitySettingsUi.kt:276` at `d23aeaa6`), and
  the alert was delivered with no condition once Return was tapped (`domain/ReturnWatch.kt:266` at
  `d23aeaa6`).
- **Route.** The permission-free battery list (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) is a
  list of every app, not Forager's page. `ACTION_APPLICATION_DETAILS_SETTINGS` opens Forager's App
  info page with no permission. The only route straight to a Forager yes/no needs
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Google Play's "Device and Network Abuse" page
  (support.google.com/googleplay/android-developer/answer/9888379) lists "Apps that are not eligible
  for allowlisting and attempt to bypass system power management" as a violation. That permission
  is not added.
- **`scripts/verify-policy-permissions.sh`** exists and fails check 2 on `main` today. The manifest
  declares `CAMERA`, and the privacy policy does not account for it. That is unrelated to this
  dispatch; the planner said another session handles it, and it is not touched here. The script
  does not look at battery permissions.

The owner's answers (Amendment 1, RECORD -627), as relayed by the planner:

1. "Add an on/off tick box (Recommended)".
2. "At Record (Recommended)".
3. "Only when truly blocked (Recommended)": `isBackgroundRestricted()`.
4. "App info page, once per block (Recommended)".

## How it is built

App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`.

- **`domain/OffTrackReminder.kt`** (new): the repository interface, `BackgroundRun`
  (`ALLOWED`, `BLOCKED`, `UNSUPPORTED`), the `BackgroundRunCheck` seam, the prompt's words, and
  `OffTrackReminderCheck.atRecordingStart`. It holds the rule:
  - reminder off, or a phone too old to say: no prompt, nothing stored;
  - allowed: store "not blocked";
  - blocked: prompt only if the last state stored was not blocked, then store "blocked".
- **`alert/AndroidBackgroundRunCheck.kt`** (new): `ActivityManager.isBackgroundRestricted` (API 28,
  no permission). Below API 28 it returns `UNSUPPORTED`, not a guess. This app's `minSdk` is 26.
- **`data/repository/DataStoreOffTrackReminderPreferenceRepository.kt`** (new): its own file
  (`off_track_reminder_preferences`), the `Result` shape and per-instance factory of the sibling
  repositories. `enabledNow()` is a cached copy for the one caller that cannot suspend.
- **`domain/ReturnWatch.kt`**: an `isReminderOn` parameter, read when the judge decides to alert.
  Off, the decision is recorded and nothing is delivered. A new Return-record entry,
  `alert-withheld ... reason=reminder-off` (`ReturnRecord.kt`, `FileReturnRecord.kt`), keeps that
  decision from being silent.
- **`ui/track/TrackRecordingViewModel.kt`**: `startRecording` asks the check in its own launch,
  beside the silenced-phone read, and sets `backgroundRunPrompt`.
- **`ui/availability/BackgroundRunPrompt.kt`** (new), `AvailabilityScreen.kt` and
  `AvailabilityCompactScaffold.kt`: the prompt goes through the map's existing message bar. The
  owner gave the prompt's words and no button label, so the whole bar is the tap target
  (`TappableNoticeVisuals`). Every other message in that bar is unchanged and not tappable. The
  intent has no new-task flag, so Back comes back to this task.
- **Settings** (`AvailabilitySettingsUi.kt`, `AvailabilityViewModel.kt`, `AvailabilityUiState.kt`):
  the checkbox row has the same shape as "Sundown alerts".
- **Wiring**: `AppContainer.kt` and `MainActivity.kt`. `service/TrackRecordingService.kt` reads the
  setting when a recording begins (see the next section).

### A decision made in the build, and the alternative rejected

`enabledNow()` was first a live copy read as soon as `AppContainer` built the repository. On the
first green run, the repository tests and the Settings test failed with "There are multiple
DataStores active for the same file". Robolectric builds `ForagerApplication`, and so `AppContainer`,
for every test, and that eager read made its instance active on the same file the test's own
instance used.

The read now happens wherever the setting is used:
- the Settings screen loads it at app start;
- the check reads it at every Record;
- the recording service reads it when it begins, for a recording the system restarts with no screen.

The cost is a few milliseconds after a service restart in which a stored "off" still reads as on.
That is disclosed here, not tested.

## Evidence

### Tests first, seen failing (commit `e7bae976`, pushed)

The tests went in with the behaviour stubbed, and the runner refused results whenever the build log
had an `e:` line (none did). 21 new tests:

| Class | Tests |
|---|---|
| `OffTrackReminderCheckTest` | 6 |
| `ReturnWatchReminderTest` | 3 |
| `DataStoreOffTrackReminderPreferenceRepositoryTest` | 3 |
| `AndroidBackgroundRunCheckTest` | 2 (sdk 36 and sdk 27) |
| `OffTrackReminderSettingsTest` | 2 |
| `BackgroundRunPromptTest` | 3 |
| `TrackRecordingViewModelTest` | 2 added |

- **14 of 21 failed against the stubs**, each on its own assertion: the prompt not shown, the row's
  tag not found, `expected:<BLOCKED> but was:<ALLOWED>`, `[true, false, false]` vs
  `[false, false, false]`, and so on.
- **7 passed against the stubs.** Each asserts an "absent" outcome the stub also gives:
  - reminder off;
  - a phone too old to say;
  - the reminder on delivering;
  - an untouched install;
  - turning it back on;
  - an ordinary notice opening nothing;
  - an allowed phone showing nothing.

  "Turning it back on" passes identically before and after, so on its own it does not guard
  anything. The revert table below shows what does.

### Green (commit `533c516d`, then `f0608de8`, both pushed)

All 21 pass. `f0608de8` fixes a wait in one of them and a cache race (see Full suite).

### Real touches

- **The checkbox:** touches at 10%, 50% and 90% across its row each flip it.
- **The prompt:** touches at five points across its own bounds each open App info:
  - left, centre and right at mid-height;
  - centre near the top, and centre near the bottom.

  Each touch is checked for `ACTION_APPLICATION_DETAILS_SETTINGS`, `package:com.zynergylabs.forager.app`,
  no `FLAG_ACTIVITY_NEW_TASK`, and the prompt gone afterwards.

### Revert checks

Each was a one-line edit to committed code. The file was restored from a copy saved before the
edit, never from git, and `git diff` was confirmed empty after every restore. Every reverted build
compiled with no `e:` line, and every failure below is one that edit can produce.

| Reverted | Failed, with |
|---|---|
| R1 `ReturnWatch`'s gate | `ReturnWatchReminderTest` 2: `expected:<[]> but was:<[Alert(kind=OFF_TRACK...)]>`; `expected:<0> but was:<1>` |
| R2 the Settings row not composed | `OffTrackReminderSettingsTest` 2: no node `settings-off-track-reminder` |
| R3 the prompt not shown | `BackgroundRunPromptTest` 2: the words found 0 times |
| R4 the bar not tappable | `BackgroundRunPromptTest`, touches: `expected:<...APPLICATION_DETAILS_SETTINGS> but was:<null>` |
| R5 `startRecording` not asking | VM: `[words, null, null]` vs `[null, null, null]` |
| R6 "once per block" dropped | `OffTrackReminderCheckTest` 2 (`[true, true, true]`) and the VM test |
| R7 Restricted never read | `AndroidBackgroundRunCheckTest`: `expected:<BLOCKED> but was:<ALLOWED>` |
| R8 the battery-list intent instead | `BackgroundRunPromptTest`: `[APPLICATION_DETAILS]` vs `[IGNORE_BATTERY_OPTIMIZATION]` |
| R9 `setEnabled` not updating the cache (run again on `f0608de8`) | repository test: "set is seen at once by the synchronous read" |
| R10 the ViewModel not storing the tick (run again on `f0608de8`) | `OffTrackReminderSettingsTest`: "all three stores finished: not within 5000 ms" |

### Full suite

All runs used `--rerun` with the results directory deleted first, and test temporary files on disk
(`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=~/.cache/forager-test-tmp`). Each compiled first, then the
Kotlin daemon was stopped, then the tests ran, all under the 5 GB cap.

- **Before** (`d23aeaa6`, a separate worktree): **3,911 tests, 0 failures, 24 skipped.** 474 XML files.
- **After, first run** (`a19b433a`): 3,932 tests, **1 failure**. It was this dispatch's own
  `OffTrackReminderSettingsTest`, "off survives expected:<false> but was:<true>".
  - Reproduced 1 of 6 on disk with that commit's test.
  - The cause was the test's own wait, plus a race it exposed in the repository's cache. The test
    waited until the cache read "off". The first of its three stores (off, on, off) already gives
    that, so the store's scope was cancelled with the other two still in flight.
  - Fixed in `f0608de8`. The test now waits for all three stores to finish. The repository's read
    and store now share one lock, so a read begun before a change cannot write the old value into
    the cache.
  - After the fix, 10 of 10 runs on disk passed (the two Settings tests and the three repository tests).
  - R9 and R10 were run again against the fixed code. R9: "set is seen at once by the synchronous
    read". R10: "all three stores finished: not within 5000 ms".
- **After** (`f0608de8`): **3,932 tests, 0 failures, 0 errors, 24 skipped.** 0 `e:` lines. All 480
  XML files are newer than the run's start. 3,932 = 3,911 + the 21 new tests; 480 = 474 + 6 new
  test classes.

The first baseline attempt did not run. With `/tmp` (tmpfs) holding the test files, the Gradle
daemon died with "Disk quota exceeded", and the user journal shows an `oom-kill` in that scope at
the same minute. Moving the test temporary files to disk fixed it.

**Disclosed:** the red run, the first green run and the R1 to R8 revert runs used the default
temporary directory, which is tmpfs on this laptop. Each was a single-class run and completed.

`./gradlew --stop` was run at the end.

## Not done, or not verified

- **Not tested:** the production wiring in `MainActivity` and `AppContainer` (which check, which
  repository), and the service's read when a recording begins. The tests drive the pieces through
  their own entry points.
- **The prompt over other surfaces.** The bar is tested on the Maps tab only, and the
  80%-over-the-map rule is unchanged for it, since it is the same bar.
- **What the check can't see.** Samsung's "Sleeping apps" and "Deep sleeping apps", and other
  makers' battery managers. A phone can pass the check and still stop the recording.
- **Inferred, not verified:** whether ordinary battery optimisation (not Restricted) ever stops
  this alert. It comes from a running foreground location service. The owner chose to prompt on
  Restricted only.
- **Device-only, not run (no phone):**
  - on the S22, set Forager to Restricted, tap Record, and see the prompt once;
  - tap it, land on Forager's App info page, and press Back to return to the recording;
  - unrestricted: no prompt;
  - the checkbox off, then a stray on Return: no buzz;
  - the S22's Sleeping apps list, as an observation.
