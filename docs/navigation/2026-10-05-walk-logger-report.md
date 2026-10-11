# Walk logger: a debug-only recorder of everything the phone senses on a walk

Dispatch 2026-09-28-532 (`prompts/preserved/2026-10-05-02.md`), with Amendment 1 (RECORD -533, two phones), Amendment 2 (RECORD -559, the owner's four answers) and Amendment 3 (RECORD -560, the switch). Branch `walk-logger`, cut from `main` at `4b72b25c` (-516 merged), with `main` at `71c1de9c` (-549 and -527) merged in as `3c025c6f`. Not merged into `main`. The owner's how-to is a separate page: [2026-10-05-how-to-log-a-walk.md](2026-10-05-how-to-log-a-walk.md).

**Status:** built, tested, revert-checked and release-checked. **Not yet on a phone.** The S22's read-only capability check (item 3) is done. The S26's reads and the 10-minute desk run on both phones wait for the owner's word.

## What it does

In a **debug build**, while the Diagnostics screen's **Walk logger** switch is on (off by default, RECORD -560), each track recording writes one plain-text file, `walklog-<start, UTC>.txt`, to `Android/data/com.zynergylabs.forager.app/files/walklogs/` on the phone. The file holds:

- every location fix the phone delivers, from one passive listener (provider, accuracy, vertical accuracy, speed and its accuracy, bearing and its accuracy, altitude, `getTime()`, `getElapsedRealtimeNanos()`);
- each satellite-status callback, one line per satellite: constellation, id, C/N0, elevation, azimuth, used in fix, carrier frequency;
- raw GNSS measurements: a receiver-clock line, then one line per satellite, including the accumulated-delta-range state;
- the measurement status, with "not supported" written once;
- the eight sensors, at the owner's rates (RECORD -559, choice 4): accelerometer, gyroscope and magnetometer at 25 Hz; rotation vector and game rotation vector at 10 Hz; pressure at the platform's normal rate; step detector and step counter as they report. Each sensor's accuracy changes are logged too;
- a battery line at the start and every minute: the phone's clock, level, charging, plug, free space;
- a header: the phone (`Build` fields), the app version, the start time on both clocks, SIM state, data network type and active transports (Amendment 1), every sensor's name, vendor, range, resolution, power and delay limits, the GNSS hardware name and year, and the GNSS capabilities read by API level, including the signal types the chip says it tracks.

Every event line is keyed on the since-boot clock, which can't be set by hand (RECORD -540). Anything the phone doesn't offer is an `UNSUPPORTED` line, never a missing one. A value it didn't report is `none`.

It stops when the recording stops or the service is destroyed. Below 200 MB free it stops, writes a `STOPPED reason=storage-low` line, and the recording carries on. A partial wake lock is held only while it logs (choice 3). Raw measurements are asked for without full tracking (choice 2).

In a **release build** none of this exists: `WalkLogger` is a do-nothing twin (`app/src/release/.../diagnostics/WalkLogger.kt`), with no other logger classes and no new permissions (see "Release check").

## What landed

| Commit | What |
|---|---|
| `816bdc5a` | The failing tests, with stubs so they compile. The release twin in its final form. |
| `401a54b2` | Test harness only: the writer and session tests run under Robolectric (see "The red run"). |
| `4ed83cb9` | The code (written before the red run; see disclosures). |
| `90b45971`, `c0fab08a` | The owner's how-to; then its "close [the mainstream maps app] and other navigation apps" line (the owner, relayed by the planner: "Yes, add the line (Recommended)"). |
| `edf533a0` | Three fixes from the first green run (one code, two test harness). |
| `62f41bc7` | A session reads inactive only once its file is closed (a race the full suite found). |
| `3c025c6f` | Merge of `origin/main` at `71c1de9c`. No conflicts. |

The only change to `src/main` is three calls in `service/TrackRecordingService.kt` (start, stop, destroy), its import, and a doc paragraph. `git diff origin/main` on that file after the merge shows exactly those lines.

## Verify before building (answered by message before any code; RECORD -559)

1. **Debug and release wiring.** `ForagerApplication.kt:53` installs `DebugDiagnostics` from the source-set-split pair; `WalkLogger` follows the same split. The service hooks were chosen by the owner: "Hooks in the service (Recommended)".
2. **Permissions.** `app/src/debug/AndroidManifest.xml` is new: `ACTIVITY_RECOGNITION` and `READ_BASIC_PHONE_STATE` (runtime; granted over USB with `adb shell pm grant`), `ACCESS_NETWORK_STATE` and `WAKE_LOCK` (normal). Fine location is already in `main`.
3. **What the phones offer.** The S22 is done (below). The S26 waits for it to be connected.
4. **Format and size.** Format as above. Estimated size: about 22 KB/s, so about 160 MB for two hours. That is an estimate from the rates and line lengths, **not measured**; the desk run measures size and battery.
5. **Swipe-away and storage.** The process lives as long as the recording service, so a swipe-away doesn't end the log. Lines are flushed every 5 s of since-boot time and synced to disk once a minute, so a process killed outright loses a few seconds and a phone switched off loses at most a minute. A sticky restart starts nothing in the service, so it starts no log either. Low storage stops the log, never the recording (tested through the service).

### Item 3 on the S22 (SM-S908U), read only

Raw output is in `~/Zynergy/device-evidence/2026-10-05-walk-logger/SM-S908U/`; the location dump holds last-known positions, so it stays there.

- Android 16 (SDK 36), build `BP2A.250605.031.A3`.
- **Clock:** automatic time on (`auto_time` = 1); the phone's clock reads +111 to +119 ms against the laptop's, about adb's own delay. **Correction:** `clock.txt` holds only these three millisecond reads. My first comparison mixed milliseconds with nanoseconds, said nothing about the phone, and was discarded. I first told the planner it was kept in that file; it wasn't.
- **Sensors:** all eight are present, and each requested rate is within what the phone reports as possible:

  | Sensor | Phone's limit |
  |---|---|
  | Accelerometer (LSM6DSO) | up to 416 Hz |
  | Gyroscope (LSM6DSO) | up to 416 Hz |
  | Magnetometer (AK09918) | up to 100 Hz |
  | Pressure (LPS22HH) | 1 to 25 Hz |
  | Rotation vector, game rotation vector (QTI) | 5 to 200 Hz |
  | Step detector, step counter (Samsung) | as they report; need `ACTIVITY_RECOGNITION` |

  All are non-wakeup sensors, which is why the wake lock matters.
- **GNSS:** Qualcomm `SM_WAIPIO`. Capabilities, verbatim: `[SCHEDULING MSB MSA GEOFENCING MEASUREMENTS LOW_POWER_MODE SATELLITE_BLOCKLIST SATELLITE_PVT MEASUREMENT_CORRECTIONS MEASUREMENT_CORRECTIONS_FOR_DRIVING ACCUMULATED_DELTA_RANGE(unknown) LOS_SATS EXCESS_PATH_LENGTH REFLECTING_PLANE TOTAL_POWER]`.
  - **Raw measurements:** offered and delivered.
  - **Carrier phase:** "unknown". Only the logged `adrState` values will settle it.
  - **L5:** tracked and used. The phone's own 14-day counters show 1,504,105 L5 satellite reports, 749,218 of them used in a fix.

## Evidence

All runs are `./gradlew testDebugUnitTest` on the laptop. Logs and copies of the JUnit XML are in `~/Zynergy/device-evidence/2026-10-05-walk-logger/runs/`. Each build log was checked for compile-error lines (`^e: `) before its results were read; every count below is from a log with none.

### The red run, at `401a54b2`

The seven new classes: **44 tests, 38 failed**, each on its own assertion against the stubs (empty strings, no file, no node with the toggle's tag).

**Why the red run is at `401a54b2`, not `816bdc5a`.** The code logs through `android.util.Log`, which this module's plain JVM tests can't call (no `returnDefaultValues`). So the writer and session tests had to run under Robolectric, and that change came before any code. `git diff --stat 816bdc5a 401a54b2`: 2 files, 16 insertions, 0 deletions (imports, the runner annotation, a comment). Lines containing an assertion added or removed: 0.

Six tests passed at red, all expected to:

| Test | Why it passes against stubs |
|---|---|
| The switch is off until turned on (service and store tests) | The stub reads off. |
| The 200 MB constant | The stub defines it. |
| The `.gitignore` positive control | It checks a tracked file. |
| With the switch off, no second listener | The guarantee Amendment 3 asked for; it holds before any code. Revert R4 shows it bites. |
| The two switches are independent | **Suspect:** it passes against a stub that stores nothing. Revert R10 makes it fail, so it does bite once there is storage. |

One red failure was for a reason other than the missing behaviour. The `.gitignore` test failed on the stub's empty file name (`git check-ignore` refuses an empty path), not on a missing ignore rule. Revert R8 is what shows the rule is checked.

### The first green runs, and three fixes they found

The new classes on the code (`c0fab08a`): 44 tests, 6 failed.

- **Code:** the header's format legend contained " UNSUPPORTED ", the mark of an unsupported event line, so three session tests counted it as one. The legend was reworded; the tests are unchanged.
- **Test harness:** Robolectric reports **0 bytes free** in the app's external files directory. Found by turning ShadowLog on in one test file, then restoring that file from a saved copy. Its log: `free=0 min=209715200`. Every service-test log stopped at once. The class now stands in 10 GB. The storage-low test sets its own 1,000 bytes; it had passed before on Robolectric's 0, not on its own figure.
- **Test harness:** the fix test reads the file after the stop. The writer flushes every 5 s of since-boot time, and Robolectric's clock doesn't move unless a test moves it, so the line was still in the buffer. Same assertion. It now passes, which also confirms the passive listener receives a simulated fix.

All three are in `edf533a0`. No assertion was weakened or removed.

### The full suite

| Commit | Files | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `edf533a0` | 464 | 3,763 | **1** | 0 | 24 |
| `62f41bc7` | 464 | 3,763 | 0 | 0 | 24 |
| `3c025c6f` (merged with `main` `71c1de9c`) | 467 | 3,817 | 0 | 0 | 24 |

The one failure at `edf533a0` was the fix test, which had passed when its class ran alone. The file had neither the fix nor the end line. `WalkLogSession.stop()` set itself inactive before closing the file, so a reader on another thread could see "stopped" while the end line and buffered lines were unwritten. Fixed in `62f41bc7`: the flag is volatile and set false only after the close. Passing alone wasn't evidence either way, so the full suite was run again. That makes three full runs where the dispatch asked for one: the second because the code changed after the first, the third because the planner asked for one on the merged tree.

On the merged tree, the affected classes alone (the seven new ones, every `TrackRecordingService*` class, `DiagnosticsSyntheticForecastSwitchTest`, `DiagnosticsPanelTest`, `SyntheticForecastTest`): 13 files, 77 tests, 0 failures.

### Revert checks (at `62f41bc7`)

Run by `~/Zynergy/device-evidence/2026-10-05-walk-logger/scripts/revert.py`, one edit per run. The runner:

- saves a copy of the file before editing;
- deletes the old JUnit XML;
- runs the named classes;
- refuses to cite results if the log has a compile-error line;
- restores from the saved copy, never from git;
- asserts the restored file equals the forward version.

Every run below compiled, restored, and failed with a message only its own edit could cause. The working tree was clean afterwards. Summary: `~/Zynergy/device-evidence/2026-10-05-walk-logger/reverts-summary.txt`.

| | Edit | Failed |
|---|---|---|
| R1 | measurements "not supported" said every time, not once | session: told once |
| R2 | no floor check when the file opens | writer and session: below 200 MB at the start |
| R3 | no start call in the service | service: 4 tests, "the logger starts with the recording" |
| R4 | the switch ignored | service: switch off, "expected:<1> but was:<2>" listeners |
| R5 | no wake lock | service: "a wake lock while logging" (and the destroyed test, no lock to read) |
| R6 | step sensors asked for without the permission | session: the permission test |
| R7 | vertical accuracy written from the horizontal | format: `vacc=[none]` but was `vacc=[3.79]` |
| R8 | the `.gitignore` line removed | "git must ignore walklog-20231114T221320Z.txt" |
| R9 | the Diagnostics row not drawn | all 4 panel tests |
| R10 | the switch stored under the forecast switch's key | store: the key, and the two switches' independence |
| R11 | no stop call on a stop | service: "the logger stops with the recording", and the fix test |
| R12 | no stop call on destroy | service: "the logger stops with the service" |
| R13 | storage low at a minute tick not acted on | session: "the log has stopped" |

### Release check (at `62f41bc7`, and again on the merged tree)

`./gradlew :app:processReleaseMainManifest :app:compileReleaseKotlin` (no signing needed; Amendment 2):

- **Merged release manifest:** no `ACTIVITY_RECOGNITION`, no `READ_BASIC_PHONE_STATE`. It does carry `WAKE_LOCK` and `ACCESS_NETWORK_STATE`, from libraries already in the app before this work. The release merger report credits `ACCESS_NETWORK_STATE` to MapLibre 13.5.0 and WorkManager 2.12.0, and `WAKE_LOCK` to WorkManager. The debug merged manifest carries all four.
- **Release classes:** `diagnostics/` holds `DebugDiagnostics` and `WalkLogger` only, and no `walklog` class exists. `javap` on the release `WalkLogger` shows two methods, `onRecordingStarted` and `onRecordingStopped`, and a private static instance, with no thread, session or wake lock.
- Copies are in `runs/5-release/`.

### Robolectric, read from its bytecode rather than assumed

`org.robolectric.shadows.ShadowLocationManager` in `shadows-framework-4.16.1.jar`, read with `javap -c`:

- `simulateLocation(String, Location...)` delivers to the named provider's listeners and then, if it exists, to the `passive` provider's.
- `getRequestLocationUpdateListeners()` returns `getLocationUpdateListeners()`, which collects listeners from every provider into one set.

So the logger's passive listener would have changed the counts in the existing service tests, which is why the switch exists (RECORD -560).

## Against RECORD -519: which rows the logs can test

The logger covers no failure for the walker. Of the rows in [2026-10-05-location-failure-fallbacks.md](2026-10-05-location-failure-fallbacks.md):

| Row | What the logs give |
|---|---|
| **GPS under canopy** | Each fix's reported accuracy beside its satellites (C/N0, used-in-fix) and raw measurements. This is what -526 waits on: whether fixes report the 3.79 m floor while really being off. Also steps and heading, for the filter. |
| **GPS and steps both run out** | How long GPS drops out under real canopy, and what steps and heading did meanwhile. Data for the provisional 5 min or 300 m cap (-538, question 4). |
| **No GPS, network available** | The S26's files (SIM and data) against the S22's (none), on the same walk. |
| **No GPS, no network** | The S22's files. |
| **Compass disturbed** | Magnetometer, gyroscope, both rotation vectors and their accuracy changes. |
| **Step length wrong** | Step events beside GPS speed and distance. |
| **Battery running low** | The minute-by-minute battery lines measure what logging and recording cost. This doesn't fill the gap; it gives the numbers for deciding how. |

The other rows (location lost while navigating, pace not yet measured, walk back unknown, no signal at all, app swiped away) are behaviours of the app, not of the phone's sensing; the logs don't test them.

## Disclosures

### Confirmed, and how; against inferred

**Confirmed by tests here:**
- each event type's line;
- unsupported written once each;
- the start and stop with the recording through the real service;
- no extra listener with the switch off;
- the wake lock held, then let go;
- low storage stopping the log and not the recording;
- the switch's key and the panel toggle by real touch;
- `.gitignore`;
- the release outputs.

**Inferred, not yet seen on a phone:**
- that the passive listener receives the tracker's GPS and network fixes;
- that `getExternalFilesDir` is `Android/data/<package>/files/` and that `adb pull` reaches it (both phones run Android 16);
- the size and battery estimates;
- that the wake lock keeps non-wakeup sensors flowing with the screen off;
- that the rotation vectors honour 10 Hz.

### Could not determine

- **Whether the S22 delivers valid carrier phase without full tracking.** Its capability reads "unknown". The location history shows a mainstream maps app asking for full tracking while Play services got duty cycling. Our logs may show no valid carrier phase even if the chip can produce it.
- **Anything about the S26:** not connected; its reads wait.
- **Battery cost of the wake lock:** the desk run.

### Premises that were wrong

- **The dispatch's `aapt dump permissions` on a release build** was impossible without the release signing identity. It was replaced by the merged manifest and compiled classes, as accepted in RECORD -559.
- **Robolectric's free space:** I assumed the external files directory would report free space under Robolectric. It reports 0. Found by the first green run.
- **The order inside `stop()`:** I assumed a session marked inactive had finished writing. It hadn't; the full suite found it.
- **Report evidence:** my S22 message said `clock.txt` kept both clock comparisons; it doesn't (see item 3).

### Decided beyond the dispatch

- **Where the switch is stored:** on `SyntheticForecastCellStore`, under its own key `diagnostics.walk_logger`, because that store already owns the one debug diagnostics DataStore file, and DataStore refuses a second live instance on a file (accepted by the planner).
- **The header** lists the GNSS signal types (`getGnssSignalTypes`, API 34) and reads each capability by its API level from the SDK's own `api-versions.xml` (accepted by the planner).
- **A battery line at the start,** besides each minute, so the first minute's drop is measured from the start.
- **Pressure** is asked for at the platform's normal rate as the meaning of "as delivered". The S22 offers 1 to 25 Hz.
- **The wake lock** carries a 12-hour ceiling, so a lock left behind by a bug can't drain the phone indefinitely. It's released when the log ends.
- **The switch is read when a recording starts.** Turning it on mid-recording logs from the next recording; the Diagnostics row says so.
- **The code was written before the red run,** on the planner's acceptance. The red run was then taken at `401a54b2`, which holds only tests and stubs, so the red can be reproduced from that commit alone.

## Not tested, and what remains

- **The S26's read-only capability check** (Amendment 1): model and build via `getprop`, sensors, GNSS. Then, before any install there, the installed version and signer from `dumpsys package`. RECORD -539 reads it as signed with the committed debug key; any install that would need an uninstall or a wipe is a stop for the owner.
- **The 10-minute desk run on both phones,** with the owner's word:
  - grant `ACTIVITY_RECOGNITION` and `READ_BASIC_PHONE_STATE` over adb;
  - turn the switch on;
  - record for 10 minutes;
  - pull each file to `~/Zynergy/device-evidence/2026-10-05-walk-logger/<model>/`.

  Each phone reports a count of each event type, the battery drop, the file's size, the header's SIM and data line, and what it says about L5 and carrier phase.
- **Sizes and battery are estimates** until then.

## Superseding note (2026-10-06, RECORD -569): the first recording after an install was never logged

Appended after the S22 desk run; the text above is unchanged. Where it disagrees with this note, this note holds.

**What the desk run found.** On the S22 (1.0.2820+g38058cd8, built from `main` at `38058cd8`), the first recording after the install wrote only `STOPPED reason=storage-low free=0 min=209715200`. The recording itself was unaffected.

The cause: the free-space check read `walklogs/` before that folder existed, and a folder that doesn't exist reports 0 bytes free. Writing the STOPPED line created the folder. The next recording logged normally (98 KB in about 8 s), which confirmed the cause on the phone. So the first recording after every fresh install was never logged.

**Two premises above were wrong.**

1. **"Robolectric reports 0 bytes free in the app's external files directory"** (under "The first green runs"), and the 10 GB stand-in it led to. It was this bug, not a Robolectric quirk: the tests had the same missing folder. The stand-in hid the one case that could fail them. This is CLAUDE.md's family "a check that passes because it never saw the data that could fail it". The storage-low test's earlier pass "on Robolectric's 0" was this bug too.
2. **`READ_BASIC_PHONE_STATE` "runtime; granted over USB with `adb shell pm grant`"** (under "Verify before building", item 2, and "Not tested"). It isn't a runtime permission. `pm grant` refused it as "not a changeable permission type", and the package dump showed it already granted at install. Only `ACTIVITY_RECOGNITION` needs `pm grant`. On the S22 it was granted to user 0; user 95, Samsung's Dual App profile, was left alone.

**The fix,** on branch `walk-logger-first-run` from `38058cd8`:

| Commit | What | Result |
|---|---|---|
| `1b48b357` | The stand-in removed from `WalkLoggerServiceTest`'s setup; a precondition that no `walklogs/` folder exists; the start assertion prints the log. Tests only. | Against `main`'s code: 6 tests, 3 failing. The start test's log reads `STOPPED reason=storage-low free=0 min=209715200`, the phone's line. |
| `ccba34be` | Free space is read from the app's files root, which always exists (`diagnostics/WalkLogger.kt`). | 6 of 6 pass. |

- **Revert R14** (`directory.usableSpace` back, restored from a saved copy, forward change confirmed): the start test fails again with `free=0`.
- **Also in that R14 run, one failure R14 cannot have caused:** "storage below 200 MB stops the log, never the recording" failed at "the recording kept its points" after its 10 s wait. That test sets its own 1,000 bytes, so it never reaches the reverted line, and it passed in the red run on code identical to R14's. Every test in that run was slow (the trivial switch test took 5 s, against 0.06 s in other runs), with the phone sampling and adb running at the same time. **Not explained; possibly timing. Not touched.** It passed in the full suite below.
- **Full suite at `ccba34be`:** 467 files, 3,817 tests, 0 failures, 0 errors, 24 skipped; no compile-error lines.

**Desk run, file size** (S22, the second recording, screen off, on USB, sampled each minute):

- per minute: 792, 800, 832, 818, 820, 796, 751, 786, 802, 801 KB;
- mean about 781 KiB a minute, about 96 MB per two hours, against the estimate of 160 MB above;
- steady with the screen off, consistent with the wake lock keeping sensor data flowing.

These are indoor figures; a walk outdoors may write more. Samples are in `~/Zynergy/device-evidence/2026-10-05-walk-logger/SM-S908U/desk-size-samples.txt`.

**Battery:** not measured. It can't be while the phone is on USB, and the owner chose "Skip it for now" (RECORD -569); it will come from the first real logged walk.
