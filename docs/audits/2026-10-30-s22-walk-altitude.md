# The owner's 10-30 walk on the S22, read for altitude (#440) (dispatch 2026-10-30-11)

Hazel (Opus), 2026-10-30. RECORD -1851. Branch `s22-walk-altitude`, cut from `origin/records-after-173` at `d918e987d`. Phone data and scripts are on the laptop in `/home/zynergy-labs/s22-2026-10-30/walk/`, never in git. This report gives heights, times and distances, never coordinates. Times are PDT.

## Verdict in short
- **The strip never showed a height during the walk.** The map was off for the whole walk: its barometer stopped at 12:16:36, a second after the recording started, and came back at 12:37:56, a second before the recording stopped. So no height the owner could see during the walk can be scored. What can be scored is the app's own rule replayed on the phone's own 1 Hz fixes and barometer readings, which is what the strip would have shown with the map open throughout.
- **The dispatch's ground source could not be had.** This build has no routing key. The owner's own area over the walk, Region 2, made at 12:15:58, got no trail data, and its route state says `state=missing`, `reason=no_app_key`. A second area would fail the same way, so I did not add one. Region 1's trail file, the one with heights, does not reach the walk, which is about 12 km away. The only ground I had is the laptop's full trail file from 2026-10-03. Its heights are most likely SRTM, which is inferred, not checked.
- **Against that substitute ground**, the replayed strip is **within 10 m at 84 of 91 matched points**. Its mean is −3.4 m, sd 5.3 m, and the worst is −19.0 m. Plain sea-level GPS is also 84 of 91, with mean −1.2 m, sd 5.8 m, and worst +17.2 m.
- **So the 10 m mark is not passed or failed on the source the owner chose.** On the substitute, most points pass. The misses sit where the reference itself is doubtful, or in the last 20 s, when the phone was in a vehicle (below).

## Before anything else: no recording running
- At 13:02 `dumpsys activity services` for the app was empty.
- The newest track has an end time (12:37:57).
- Build `1.0.4224+gc4a57389` (versionCode 4224). Schema `user_version` 23, and integrity_check ok.

## Copied off (`/home/zynergy-labs/s22-2026-10-30/walk/`)
- **`db/`:** forager.db with its -wal and -shm, pulled together at 13:02. sha256 values:
  - db `cf787ce6…e5f`
  - wal `ba8b58ac…a0`
  - shm `866e8c4d…b59`
- **`walklogs/`:** all four logger files.
  - Today's walk is `walklog-20261030T191635Z.txt`: 30 MB, 1,283 GPS fixes and 6,734 pressure readings, ending `END reason=recording-stopped`.
  - The two `191430Z` and `191447Z` files are the two 1-point false starts at 12:14.
  - The `025128Z` file is from the previous evening.
- **`logcat/`:**
  - The app's process lines (pid 21640, started 11:49:40). The main buffer only reaches back to 12:20:22.
  - The events buffer, which reaches back to 12:24:55.
  - The `dumpsys sensorservice` registration history, which reaches back past 11:49.
  - The full system logcat was too big for the disk, so only the app-related lines were kept from it.
- **`diagnostics/diagnostics.log`** (the tools build's log).
- **`export.gpx`:** the walk's GPX, made through the track sheet's Share, with the share sheet dismissed. It was pulled with run-as and then deleted from the phone.
- **Screen recordings:** none were made today. No file in DCIM, Movies or Pictures is newer than midnight.

## The walk
- **The track:** `f6161fc7…`, 12:16:35 to 12:37:57, 193 points about 5 s apart.
  - Length 1,440 m by the stored points. The sheet shows 0.9 mi and 21 min.
- **It was on slopes.** It went down about 40 m into low ground and back up.
  - Barometer: about 237 m to 198 m and back to 237 m, standard-atmosphere scale.
  - Ground: 223 m to 182 m, then 223 m to 234 m.
  - The sheet: Climb 123 ft, Descent 112 ft, height 614 ft to 747 ft. The profile is a smooth U.
- **The end was in a vehicle.** From about 12:35 to 12:37:35 the phone was still (speed 0). In its last 20 s the GPS speed was 9.3 to 9.8 m/s, so it was driving. The barometer's 10 s median rose 12 m in those 20 s, and GPS rose 10 m.
- **Where the points are.** Only 91 of the 193 points lie within 10 m of a routable path. Many are 15 to 30 m from any path, so much of the walk was off routed ways. I could not tell woods from open ground from the data I had (see "What the data cannot tell").
- **The map during the walk.** These are the map barometer's start and stop times, from `dumpsys sensorservice`. The recording service and the walk logger register separately and are told apart by time.

| Map barometer on | Off | Length |
|---|---|---|
| 11:49:40 | 11:49:54 | 14 s |
| 12:12:30 | 12:12:44 | 14 s |
| 12:13:12 | 12:13:24 | 12 s |
| 12:13:30 | 12:13:45 | 15 s |
| 12:14:28 | 12:14:48 or 12:14:54 | ≤ 26 s |
| 12:14:51 | 12:14:54 | 3 s |
| 12:16:33 | 12:16:36 | 3 s |
| 12:37:56 | 12:37:58 | 2 s |
| 12:57:05 | 12:57:08 | 3 s |
| 12:59:34 | 12:59:48 | 14 s |

- **The screen was off for the walk.** The events buffer starts at 12:24:55, and its first `screen_toggled` is `1` at 12:37:56, with MainActivity resumed at the same moment. From 12:16:36 to 12:37:56 the map's barometer was not registered. So the strip was not on screen from 12:16:36 until 12:37:56.

## Altitude against the ground
**The ground source.**
- I used the 10-29 walk's method unchanged (`ground.py`): BRouter 1.7.10 with the app's `hiking-mountain.brf`, a via point every 100 m, and a match within 10 m. That gave 132 route nodes, and 91 of the 193 points matched.
- **Neither trail file with heights reaches this walk.** Both are run `usca-20261027T221400Z`: the S22's Region 1 file, and the S26's 1 MB area file from 10-28. BRouter found no route on either (0 nodes).
- **The laptop's full `W125_N45.rd5`** (`from-flash/engine-spike/brouter/`, 2026-10-03) does reach it.
  - The altitude report (docs/audits/2026-10-28-altitude-report.md) found this file 9 to 25 m high on a woods walk, and inferred SRTM.
  - **My cross-check:** I routed the 10-29 walk (flat roads near home) through it. It reads **2.04 m below** Region 1's file there, with sd 0.34 m, over 55 points.
  - So on roads near home it agrees with the file that has heights. Whether that holds 12 km away, or under trees, is not known.
- **The sea-level offset is the platform's own.** The export puts every point 21.706 to 21.723 m above its raw height (193 of 193). I used 21.715 m.

**On the matched points** (`scratch/gfull/o/cmp-out.txt`):

| Height | Mean against the ground | sd | Worst | Within 10 m |
|---|---|---|---|---|
| Raw GPS (ellipsoid) | −22.9 m | 5.8 m | −38.7 m | 3 of 91 |
| Sea-level GPS (what the strip shows in its first minute) | −1.2 m | 5.8 m | +17.2 m | 84 of 91 |
| The strip's rule replayed on the stored 5 s points | −3.5 m | 5.2 m | −19.2 m | 85 of 91 |
| **The strip's rule replayed on the logger's 1 Hz fixes and barometer** | **−3.4 m** | **5.3 m** | **−19.0 m** | **84 of 91** |
| the same, from 90 s on (after the first minute and the blend) | −3.4 m | 5.5 m | −19.0 m | 79 of 86 |

- **The replay** is `LiveHeight.kt` at `c4a57389` ported to Python (`scratch/scripts/compare.py`), with the map assumed open from the first fix:
  - plain GPS for the first minute, then a 30 s blend;
  - the barometer taken as a 60 s trailing median;
  - the anchor as the median over the first minute, then pulled by dt / 60 min, skipping any fix more than 30 m off.
  - It approximates the strip; it is not a reading of it.
- **Shape.** The replayed strip follows the ground with correlation 0.92, and plain GPS with 0.89.

**The misses (replayed strip, more than 10 m off), when and where:**
- **12:23:48 to 12:24:03, at the bottom of the descent:** −19.0, −18.9, −11.6 and −11.1 m.
  - The worst two match a route node 203.5 m high, while the nodes on either side are 185 to 189 m.
  - Plain GPS reads −17.0 and −15.3 m against the same node. It measures height independently of the barometer, and the two agree with each other, not with that node.
  - So the reference is more likely wrong there, for example a node on a raised way or a surface-model artefact. This is inferred; it is not shown.
- **12:26:09 and 12:27:09, low ground:** −11.0 and −11.7 m. GPS was also low at 12:26:09 (accuracy 10 m, the walk's worst).
- **12:37:55, the last point, in the vehicle:** −10.2 m. The barometer's 60 s median lags a 10 m climb made at about 9 m/s. This is the median's designed lag, not a fault.

**Plain GPS's misses:** +17.2 m at 12:19:56, +11.7 and +12.2 m at 12:22:02-07, −17.0 and −15.3 m at 12:23:48-53, and −12.8 and −11.7 m at 12:33:03-38.

**The sheet's labels** (the Journal's own barometer heights, converted) read 614 ft (187.1 m) lowest and 747 ft (227.7 m) highest. The matched ground runs 182.0 m to 233.8 m, and its highest point is the in-vehicle end. I did not trace which points the labels come from.

## Behaviour checks
**The first minute and the blend.**
- **On the phone: not exercised.** No map window today lasted 60 s; the longest was at most 26 s. So every time the strip was on screen it was in its first minute, showing plain sea-level GPS.
- **Replayed on the walk's own input,** from the first fix:
  - First minute: steps of up to 1.12 m a fix, the same as GPS itself, as built.
  - At the 60 s switch: no step, 0.0 m.
  - Through the blend: at most 1.10 m a fix.

**Steadiness while walking.**
- **Replayed only,** because the screen was off. From 90 s to 25 s before the end, over 1,167 one-second fixes:
  - the replayed strip moves at most 0.82 m a fix (95th percentile 0.22 m);
  - plain GPS moves at most 5.18 m a fix (95th percentile 1.77 m).
- In the vehicle, the replay moves at most 0.24 m a fix, against GPS's 3.17 m.

**The barometer's stops and Amendment 4.**
- **Not exercised.** `LiveHeight.keep()` keeps only an anchor whose first minute is over (`LiveHeight.kt:105` at `c4a57389`). No window reached a minute, so nothing was kept, and every return started fresh.
- That every return started fresh is the code's behaviour for an unsettled anchor. Neither the 10-minute carry-over nor the 10 s hold had anything to act on.
- No `ForagerLiveHeight` line appears in the logcat that survives (12:20:22 to 13:02). That is the hold's "no barometer reading within 10 s" warning.

**Converter errors: none seen.**
- **The logs.** No `ForagerSeaLevel` or `ForagerLiveHeight` line appears in the app's process lines, and none in the app-related system lines. The tools build's `diagnostics.log` names neither.
- **The gap.** The main buffer starts at 12:20:22, and the converter logs each reason once per process (`AndroidSeaLevelConverter.kt:75`). So a failure on the very first conversion, at about 11:49, would no longer be in logcat.
- **The behaviour.** The export at 13:14 converted all 193 points, at a steady 21.71 to 21.72 m. So the converter worked on this phone at that time, through the same class.

## What passed, what failed, what the data cannot tell
- **Passed:**
  - The converter: 193 of 193 points converted, with no error lines.
  - Raw against sea level: raw GPS is 23 m low (3 of 91 within 10 m). Converted, it is −1.2 m (84 of 91).
  - The replayed rule: no step at the switch, steady while walking, and it follows the slope's shape (0.92).
- **Failed:**
  - Nothing shown to fail.
  - Seven of the replay's 91 matched points miss 10 m against the substitute ground. Four of those are where GPS and the barometer agree with each other against the reference.
- **Cannot tell:**
  - **Whether the strip itself is within 10 m on this walk.** It was never on screen during the walk, and no screenshot or recording exists.
  - **Whether the substitute ground is right here.** It is most likely SRTM, and it was checked only against flat roads near home.
  - **Woods or open ground** along the walk.
  - **Amendment 4 on a phone.** It was never triggered.
  - **The first minute and blend on a phone.** No map window reached 60 s.
  - **Converter errors before 12:20:22.**

## Premises that were wrong or did not hold
- **"Region 1's route data is from 10-27 and likely has none."** Region 1's file does carry heights. The 10-29 read found `<ele>` on 18 of 18, 31 of 31 and 28 of 28 routed nodes (docs/audits/2026-10-29-s22-pr440.md on `s22-pr440`). Here it does not matter, because the walk is outside it.
- **"Download one small offline area … through the app's own flow."** This build cannot fetch trail data. Region 2 shows it: `reason=no_app_key`. The routing key is a Gradle property (`app/build.gradle.kts:117-125`), and the laptop build of `c4a57389` was made without it (docs/audits/2026-10-30-s22-tools-447-440.md, "Build it without the CI checks"). That this is the cause is inferred from the reason string and the build; I did not open the APK's BuildConfig.

## For the owner
- **To score the strip against the 10 m mark, two things are needed.**
  - Trail data with heights over the walk. That means a build with the routing key, or one of the other options for the planner to choose.
  - The Maps tab left on screen for at least 90 s at a few points along the way, with a screenshot or a screen recording.
- **Region 2 has map tiles only, not trails,** on this build. Trail navigation there will not work until a keyed build fetches it.
- **An observation outside altitude, not investigated.**
  - While the screen was off, the app logged about 30 lines a second of `SurfaceView … surface is not valid / releaseSurfaces` from MainActivity. That went on for the whole walk, about 33,000 lines from 12:20 to 12:37, and again at 13:00 with no recording running.
  - A garbage collection freeing 26 MB ran about every 25 s through the walk.
  - That looks like the screen being redrawn while it is off, which would cost battery. The cause is not known.

## Left as found
- **Deleted:** only the exported GPX (`cache/tracks/forager-track-2026-10-30-121635.gpx`), which my Share tap created.
- **No area added.** Region 1 (id 3) and the owner's Region 2 (id 7) are untouched.
- **Kept:** the 10-29 track, today's walk and the two 1-point tracks.
- **No setting changed.**
  - Screen timeout is 1,800,000 ms and brightness mode is 1, as found.
  - The Journal's Records filter was put back to All.
  - The app is on the Maps tab.
- **Indoor strip reading discarded.** I tried to read the strip indoors at 13:08. The ui dump then was stale: the launcher was on screen. The samples are kept, marked invalid, and not used.

## Not done
- No `docs/audits/README.md` row, because I don't know whether this branch merges. The planner can add one.
