# Battery saver: built, and measured on the S22 (dispatch 2026-09-28-767, plan T17)

RECORD intent -767; the owner's answers -765, -766, -768. Branch `battery-saver`, cut from `origin/main` at
`c81b326d`. Written by the coder, 2026-10-09 (UTC).

## What the owner chose

- T17: "A choice of mode that also changes how often the phone is asked for a fix. Measure on the S22 first; no
  saving is claimed without it."
- "A Battery saver switch (Recommended)", in the strip's quick menu, off by default (-766).
- After the verify step (-768): "Slow everything, every 5 s (Recommended)"; "Accept up to 4 s (Recommended)";
  "Quick menu, logger off (Recommended)", with the wording "Battery saver" / "Checks your position every 5
  seconds instead of every second. Alerts can come a few seconds later."

## What the verify step found (before any code)

Every claim read in the worktree at `c81b326d`.

1. **One place sets the rate.** `location/AndroidLocationTracker.kt` asked GPS and network each every 1 s, no
   minimum distance, plain `LocationManager` (no priority setting: the provider is the priority).
2. **Each collector registers separately, and the fastest wins.** The recording service, the recording screen's
   ViewModel and the map's ViewModel each collect the tracker's cold flow, so each makes its own platform
   registration; the platform serves a provider at the fastest interval asked. The recording screen's
   registration lasts the whole recording, screen off included (`TrackRecordingViewModel.onLeftForeground`'s
   doc comment). A saver rate in the service alone would have changed nothing on a walk. Hence the owner's
   "Slow everything": the one tracker follows the switch.
3. **What is kept is separate.** Recording samples at `TrackRecordingMode.HIGH_ACCURACY` (5 s, 5 m, 30 m). The
   enum's `BATTERY_SAVER` value changes what is kept and the accuracy limit, not the request rate; not used.
4. **Effects of a 5 s rate** (reported to the planner, accepted by the owner):
   - off-track alert: needs at least 3 GPS readings over at least 15 s, so worst case from leaving the path to
     the alert goes from about 16 s to about 20 s (up to about 4 s later); re-arm about 11 s to about 15 s;
   - arrival noticed up to 5 s later (about 6 m at walking pace); "Approaching" is a 100 m zone, cosmetic;
   - back-by and sundown: on the service's own 15 s timers, unchanged; a fix every 5 s stays FRESH (stale at
     30 s), so nothing greys between fixes;
   - recorded track: kept points may fall from about 5 s to about 10 s apart, because fix times jitter around
     the 5 s sampling floor; distance on winding paths slightly short, by an amount not measured;
   - navigation needle: bearing and distance update every 5 s (about 4 degrees per update at 100 m out).

## What changed

- `domain/BatterySaver.kt`: the repository interface (`enabled` as a `StateFlow`), `FIX_INTERVAL_MILLIS` (1 s),
  `BATTERY_SAVER_FIX_INTERVAL_MILLIS` (5 s).
- `data/repository/DataStoreBatterySaverPreferenceRepository.kt`: DataStore, file `battery_saver_preferences`,
  off by default, per-instance factory as the other settings.
- `location/AndroidLocationTracker.kt`: follows the switch; a change re-registers at once (mid-recording
  included); still ends after one `PermissionDenied` with a switch flow that never completes. Each registration
  logs its interval under `ForagerFixRate`.
- `AppContainer`: the repository, handed to the tracker. `TrackRecordingService`: reads the stored value when a
  recording starts, so a recording with no screen asks at the stored rate.
- Map ViewModel, state, screen, `MainActivity`: load and store the switch; a failed store puts the switch back.
- `MapQuickSettings.kt`: a "Battery saver" checkbox row, last, under a divider, with its one line. A checkbox
  like the Off-track and Sundown rows above it, not a toggle switch.

## Tests and revert checks

New tests, through the real entry points:

- `AndroidLocationTrackerBatterySaverTest` (4): the interval each provider is registered at, as Robolectric's
  `ShadowLocationManager.getLocationRequests` holds it: off 1 s, on 5 s, on then off during one collection
  (re-registered each time, one request per provider, removed at the end), and no permission still ends the
  flow after one `PermissionDenied`.
- `TrackRecordingServiceBatterySaverTest` (2): the real service and container, no Activity; the value stored
  by another instance before the service starts (as across a restart): on gives a GPS request at 5 s; off
  (the control) at 1 s.
- `DataStoreBatterySaverPreferenceRepositoryTest` (3): off on a fresh install; on survives a recreated
  instance; back off survives too.
- `AvailabilityScreenQuickSettingsTest`, one new test: the real screen and ViewModel; the row off by default,
  the label and its line present, and real touches at 5%, 30%, 60% and 95% of the row's width each flipping it
  and each change stored, in order.

Targeted run: 34 tests in the five classes, all pass (`AndroidLocationTrackerTest`'s 11 included).

Revert checks. Each run saves a copy of the file, makes one change, runs the named classes, refuses to read results
if the build log has a compile error (none had one), restores from the saved copy and confirms it byte-identical;
`git status` was clean after each.

| # | Revert | Failed, as predicted | Message |
|---|---|---|---|
| R1 | tracker always asks at the off interval | tracker on, tracker mid-collection, service on (3 of 6) | `expected:<[5000]> but was:<[1000]>` |
| R2 | service never reads the stored value | service on (1 of 6) | `expected:<[5000]> but was:<[1000]>` |
| R3 | the flow no longer ends on `PermissionDenied` | the new no-permission test (1 of 15) | `UncompletedCoroutinesError: After waiting for 1m` |
| R4 | the row's checkbox does nothing | quick-menu test | `a touch at 0.05 of the row's width expected:<true> but was:<false>` |
| R5 | the ViewModel does not store | quick-menu test | `each change stored, in order expected:<[true, false, true, false]> but was:<[]>` |
| R6 | the repository does not write DataStore | repository restart, service on (2 of 5) | `expected:<true> but was:<false>` |

What the reverts also show. R3: the 11 existing `AndroidLocationTrackerTest` tests all passed with the
completion removed, because they build the tracker with a switch flow that completes; only the new test, with a
live switch, holds the first-launch contract. R4 failed at the first sample (the checkbox itself, at 5%), so it
says nothing about the other three points under that revert; the forward run shows all four reach the row. The
two "off" tests (service control, repository back-off) cannot fail under any revert here, since off is also the
default; they are controls, not guards.

Full suite (with `assembleDebug`, one Gradle run): 586 classes, 4,520 tests, 0 failures, 0 errors, 24 skipped;
no `@Ignore` added by this branch. `main` was not run here; the index row of the last merged dispatch
(followup-1008, `c81b326d`'s parent work) records 4,510 tests and 24 skipped, and this branch adds 10 tests
(4 + 2 + 3 + 1): 4,520 and 24, consistent, by that record rather than by a run.

## Measurement on the S22

Protocol as agreed (-768): the S22 (SM-S908U, R5CT321008R, rated 4,855 mAh) on the debug build of `385452de`
(`versionName=1.0.3114+g385452de`), still by a window with a clear-sky GPS fix (20 satellites, 3.9 m), Wi-Fi
on, no SIM, walk logger off, Forager open on the map with one recording running, screen locked. Two 2 h runs
back to back off USB; readings over Wi-Fi adb (`dumpsys battery`, `dumpsys batterystats` reset at each run's
start and dumped at its end, `dumpsys location`). Raw files are in `~/Zynergy/device-evidence/2026-10-09-t17/`,
not in the repo.

Screen-off is the comparison the owner intends, not a limitation. The owner, verbatim: "Well the test is the
battery saver. Screen time takes up about 90% of the battery in a typical run, so the screen off is the honest
use case since screen sizes and resolutions vary".

**Timeline (phone clock, PDT).** Unplug event 20:55:13.744 (the phone's own battery broadcast, `usb:false`);
Run A 20:55:31 to 22:55:33, saver off. The owner ticked Battery saver at about 23:01; the phone's log shows
all three of the app's registrations re-made at 5 s at 23:01:10, mid-recording, and the platform's GPS
request moved to `@+5s` (`flip-logcat.txt`, `B-start-location.txt`). Run B 23:01:12 to 01:01:13, saver on.

**The figures.**

| | Run A, saver off | Run B, saver on |
|---|---|---|
| Duration | 2 h 0 m 1 s | 2 h 0 m 1 s |
| Charge counter | 4,298,970 to 4,131,360 µAh | 4,122,300 to 3,836,910 µAh |
| Used | 167.6 mAh, **83.8 mAh/h** (about 1.7% of rated per hour) | 285.4 mAh, **142.7 mAh/h** (about 2.9% per hour) |
| Level | 94% to 91% | 91% to 84% |
| batterystats discharge | 168 mAh | 285 mAh |
| Screen on | 10 s, once | 18 s, twice (the owner's tick) |
| Device idle, light / full | 50.5% / 46.5% | 50.1% / 46.7% |
| GPS on for the app | **4 m 57 s** | **2 h 0 m 1 s** (chip actually running 1 h 13 m, started 1,439 times) |
| Forager's CPU time | 2 m 42 s user | 33 m 58 s user |

**What this does and does not show. The comparison does not measure the saver.** The two runs differ in
something the saver did not cause.

- In Run A the phone switched the app's GPS off about 5 minutes into the run, 3.5 minutes after light Doze
  began (`-gps` at 21:00:28 in the history), and it stayed off for the rest of the run: at its end both of the
  app's GPS registrations are listed `(inactive)` and the GPS provider `ProviderRequest[OFF]`
  (`A-end-location.txt`). The recording received no GPS for about 115 of its 120 minutes, saver off.
- In Run B, with the same idle pattern, the registrations stayed active and the GPS ran the whole two hours,
  the chip duty-cycling at about the 5 s interval (1,439 starts in 2 h).
- So Run B drew more because the phone kept delivering fixes in it and had stopped in Run A, not because 5 s
  costs more than 1 s. Why the platform marked the registrations inactive in one run and not the other is
  **not determined**: same phone, same spot, same idle times; the differences are the interval and the time
  of night. Samsung's location layer appears in the dump (`isFromNsflp=true`, a "Throttling Allow Packages by
  nsflp" list, empty); that it is the cause is a guess.

**Two findings that matter more than the comparison.**

1. **A still phone, screen off, stopped the recording's GPS within minutes, with the saver off** (Run A). On a
   walk the phone moves and need not reach idle, so this may never happen while walking; but a walker who
   stands still for several minutes with the screen off is the same state. If it holds, the off-track alert and
   arrival would get no GPS reading until the phone wakes. Seen once, on one phone; not tested on a walk.
   Reported, not touched.
2. **Forager's own CPU, not the GPS chip, is most of the cost of a fix.** In Run B the app used 34 minutes
   of CPU for about 1,440 GPS fixes (each delivered to the two registrations still live with the screen locked,
   plus network fixes), roughly 1.4 s of CPU per GPS fix, against under 3 minutes in Run A with almost no
   fixes. What runs per fix (the recording service's watches, the screen's ViewModel, which stays alive with the
   activity, `Foreground activities: 2h`) was not profiled. If that cost follows the fix rate, a 5 s rate
   cuts it in proportion against 1 s; that is an inference this night could not test, because the 1 s run had
   no fixes to compare.

**What this licenses saying.** Nothing about a saving, to users or anyone else. The measured per-hour figures
are 83.8 mAh/h (saver off, GPS mostly off) and 142.7 mAh/h (saver on, GPS on throughout), one run each, on a
still S22 by a window under a clear sky; they are not a comparison of the two modes. The wording shipped makes
no saving claim, which this result supports. A fair comparison needs both runs with GPS delivering throughout:
a phone that does not go idle (moving, as on a walk), or the same at-rest set-up with each run's GPS on-time
checked before its figure is read. That is the planner's and the owner's call.

## What was not done or not verified

- No saving measured (above). One run per mode; no repeat.
- The phone being charged and the recording stopped after Run B: left to the owner at 01:01 PDT; not seen by
  me at the time of writing (at 01:01:54 PDT it was still unplugged at 84%, recording).
- Track density at 5 s (points perhaps about 10 s apart) and distance on a winding path: not measured; needs a
  saver walk.
- The GPS-off-at-rest finding and the per-fix CPU cost: observed once, causes not investigated.
- Device-only by construction: the menu's place against real insets (Robolectric reports none).
