# S22: the #440 tools build installed, walk logger on (dispatch 2026-10-29-08)

Hazel (a helper, Opus), 2026-10-30 02:46 to 02:56 UTC (2026-10-29, 7:46 to 7:56 PM Pacific), RECORD -1815.
The phone is the S22 Ultra (SM-S908U, adb R5CT321008R), on the laptop's cable, indoors. Only the S22 had commands
sent to it. Phone data is kept off GitHub, in /home/zynergy-labs/s22-2026-10-29/tools/, and file names below are
in that folder. No coordinates appear in this report. No Gradle was run.

## Step 1: the phone's state before the install

- **Build:** `1.0.4175+g7289aeef` (versionCode 4175), from `dumpsys package` (`00-package.txt`).
- **Database:** `forager.db` user_version **22**, read with sqlite3 on a copy of db, wal and shm (`db0/`).
- **Region 1:** present: one row, `Region 1`, radiusKm 1.
- **The walk track:** present. Track `ddb8d74f…` (13:40 to 13:47 PDT), 76 points, with its 3 waypoints: Start
  (ORIGIN), Waypoint 1, and End (END). No finds or journal entries.
- **Permissions (user 0):** fine and coarse location, notifications and the location foreground service granted.
  `READ_BASIC_PHONE_STATE` already read granted, as an install permission. `ACTIVITY_RECOGNITION` was not granted.

## Step 2: the install

- **Run 40961642397:** `workflow_dispatch` on `tools/pr-440`, head `b18bd56d3`, attempt 2, success.
- **Artifact `app-debug-apk` (12937313982):** the zip's sha256 `639ae617f94899bc50ff9a18ca378018930a8e797583206f34ec7e800e3e5985`
  matches the Actions API digest exactly. The APK's sha256 is `a1f04e56b932918ed4578cbb4cbbeeb263275237c01ef39d442281ea9618a041`.
- **The APK itself:**
  - aapt2 reads versionCode 4180 and versionName `1.0.4180+gb18bd56d`.
  - Its V2 certificate SHA-256 is `cb2f6da5…94f1626`, the same as the earlier builds in `2026-10-29-s22-pr440.md`.
- **Schema:** `ForagerDatabase` declares `version = 22` at `b18bd56d` (`ForagerDatabase.kt:206`). It is the same
  as the phone's 22, so this is no downgrade.
- **The install:** `adb install -r` at 02:48:41 UTC: Success (`01-install.txt`). `dumpsys` then read
  `1.0.4180+gb18bd56d`, versionCode 4180. Settings' footer read "Build 4180 · 1.0.4180+gb18bd56d" (`04-settings.png`).
- **After the app opened the database** (`db1/`): user_version 22; Region 1 present; track `ddb8d74f…` with 76
  points and its 3 waypoints.

## Step 3: the grants

- **`android.permission.ACTIVITY_RECOGNITION`:** `pm grant` rc 0. It reads `granted=true` for user 0
  (`02-package-after-grant.txt`).
  - The dual-app profile (user 95, stopped) still reads false. Nothing in the dispatch covers that profile, and I
    did not touch it.
- **`android.permission.READ_BASIC_PHONE_STATE`:** `pm grant` refused it: "not a changeable permission type". On
  this Android version it is an install-time permission, not a runtime one. It already reads `granted=true`, under
  install permissions, from the install itself.
  - So both permissions read granted. Only one of them took an adb grant.

## Step 4: the walk logger

- **Navigation:** Tools > Settings, scrolled to the end. The **Crash Logs** and **Diagnostics (debug build)**
  rows are both shown, above the build footer (`05-settings-rows.png`).
- **Diagnostics:** "Walk logger" read off (`06-diagnostics-before.png`). I switched it on through the app.
  - The switch then reads checked (`07-diagnostics-on.png`).
  - The app's DataStore file `debug_diagnostics_preferences` holds `diagnostics.walk_logger` = true.
- **It is left on,** for the owner's walk. That is the dispatch's purpose, so it is not a setting I restored.

## Step 5: the indoor check

- **The recording:** started from the Maps tab's Record button at 02:51:26 UTC. The track started at
  02:51:28.418; I stopped it with the Stop button at 02:52:43. That is about 77 s.
- **The logger started with it.** Logcat at 02:51:28.642 reads: `Walk log started for track '598856a5…':
  walklog-20261030T025128Z.txt`. It registered the magnetometer, gyroscope, pressure, rotation vector, game
  rotation vector, step detector and step counter.
- **The file:** `Android/data/com.zynergylabs.forager.app/files/walklogs/walklog-20261030T025128Z.txt`. It is
  **1,202,381 bytes, 11,267 lines** (a copy is in the folder).
  - It ends `END reason=recording-stopped`, 76.8 s after its start line, by elapsed time.
- **Its first header lines,** verbatim:

  ```
  # walklog version=1
  # format: <since-boot ns> <TYPE> key=value ...; none: the phone did not report that value; UNSUPPORTED: the phone does not offer it
  # start wall=2026-10-30T02:51:28.516Z wallMillis=1793328688516 elapsedNanos=6277273854876
  # device manufacturer=samsung model=SM-S908U device=b0q build=BP2A.250605.031.A3 fingerprint=samsung/b0qsqw/b0q:16/BP2A.250605.031.A3/S908USQSAGZH3:user/release-keys sdk=36
  # app versionName=1.0.4180+gb18bd56d versionCode=4180
  # network sim=UNKNOWN dataNetworkType=LTE transports=none
  # gnss hardware="qcom,SM_WAIPIO,MPSS.DE.2.0-00822.3-WAIPIO_GEN_PACK-1.43425.213.142410.9," year=2020
  # rates accelerometer=25Hz magnetometer=25Hz gyroscope=25Hz pressure=as-delivered rotation_vector=10Hz game_rotation_vector=10Hz step_detector=as-delivered step_counter=as-delivered
  ```

  There are 53 header lines in all; the rest are one line for each sensor, plus GNSS capabilities.
- **What it holds,** by record type:
  - 8,308 SENSOR records: accelerometer 2,036, gyroscope 2,035, magnetometer 1,918, rotation vector 1,162, game
    rotation vector 774, pressure 383.
  - 159 FIX records: gps 78, fused 77, network 4.
  - 1,803 GNSS_SAT, 780 GNSS_MEAS, 77 GNSS_STATUS, 77 GNSS_CLOCK, 6 SENSOR_ACCURACY and 2 BATTERY.
  - It has no step records. That is expected of a phone lying still, but **this check does not show the step
    sensors writing**.
- **Observed, not a gate:** the network line reads `sim=UNKNOWN dataNetworkType=LTE transports=none`, on a phone
  with no SIM and on Wi-Fi. I have not checked what the logger reads to get these values.
- **Deleted:**
  - The test track `598856a5…`, through the app: Journal > Records > Tracks, swipe, Delete. I let the Undo
    snackbar lapse.
  - Deleting the track left its Start and End waypoints in the database, with no track link. They were made by
    this test, so I deleted those two the same way, under Waypoints. Dispatch 2026-10-29-02's rule is "Delete any
    test data you made". This goes beyond this dispatch's words, "delete only that test track", so I am saying so
    here.
- **Kept:** the walk log file, on the phone and as a copy. No step said to remove it.
- **The database after the deletes** (`db4/`): user_version 22, Region 1 present. Only track `ddb8d74f…` remains,
  with 76 points and its 3 waypoints, which is the same as before the session.

## Step 6: how the phone was left

- The app is open on the **Maps** tab, with no recording running (`12-final-maps.png`). Version
  `1.0.4180+gb18bd56d`.
- **System settings were not changed:** brightness mode is auto (1) and the screen timeout is 1,800,000 ms, as at
  the start. The brightness level moved from 187 to 55 under auto mode; I did not set it.
- **The walk logger is on,** on purpose. No other app setting was changed.

## Not verified

- **No walk was taken.** The logger was shown working only indoors, with the phone still: no steps, and the GPS
  was weak indoors.
- **The dual-app profile** (user 95) was not granted activity recognition.
