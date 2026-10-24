# Report: S22 check before the promotion (dispatch 2026-10-24-03, pre-main 073fc77bd)

**Helper:** Hazel (app coder). **Run:** 2026-10-24, 09:10 to 09:30 UTC. **Phone:** S22 (SM-S908U, R5CT321008R).
Screenshots stay off GitHub (kept on the laptop, outside every repository). No positions in this file; places are described generically.

## Build and database
- **Before:** 1.0.3897+g530cbf8a (versionCode 3897), as the dispatch said.
- **Installed:** the `app-debug-apk` artifact of CI run 40078250926 (push, pre-main, head 073fc77bdc59, green), downloaded with `gh run download` into an empty directory outside the repositories. `aapt2 dump badging` on the file: versionCode 3901, versionName **1.0.3901+g073fc77b**. Installed with `adb install -r` (no uninstall). After the install, `dumpsys package` and the app's own Settings footer both read "Build 3901 · 1.0.3901+g073fc77b".
- **Database versions:** the installed build's `forager.db` header `user_version` read off the device before the install (`run-as … dd`, bytes 60 to 63) = **22**. `ForagerDatabase.kt:206` at 073fc77bd declares `version = 22`. Same version, so not a downgrade, and the install went ahead. (530cbf8a is not in my local clone, so I took the installed version from the device and not from its schema JSON.)
- **Area 1:** I didn't touch it. It is listed as "Region 1 · 8 mi radius · 24.5 MB · downloaded 1 day ago" with "Older map style. Download again to update." Tile budget 1336 / 6000 before the check and again after it. Its route files on disk (`files/offline_routes/2/`) were unchanged.

## Steps

**1. Launch: pass.** Opened to the map, with Trail selected in Layers (Trail / Topo / OSM). No crash. Logcat for the app process since launch: no `FATAL EXCEPTION`, no `AndroidRuntime` crash. The app's error-level lines were all platform noise (gralloc pixel-format probes, `ashmem` pinning, `libpenguin.so` not found). The only app warning was one `Mbgl-HttpRequest: Request failed due to a permanent error: Canceled` (a cancelled tile request).

**2. Offline row: pass, with one part not seen.** I downloaded a test area named "S22test" with signal up: 1 mi radius, about 44 tiles, in an area near trails, away from Area 1's centre. What I saw, from screenshots about 0.3 s apart:
- **The count stage: not seen.** The 44 tiles finished between two captures, so the count never showed on a frame. I can't say whether it rendered.
- **"Getting trail routes": seen.** The row showed a progress bar with "Getting trail routes" under it, and Download Maps was disabled. Logcat shows the matching requests: `routing/area.json` returned 200, then one `.rd5` segment returned 200 with a 476,361-byte body.
- **Finish: seen.** The list showed "S22test · 1 mi radius · 7.3 MB · downloaded just now", and the budget went to 1380 / 6000. The routes are stored: `files/offline_routes/7/` holds that same `.rd5` (476,361 bytes) plus `routes.properties`. The row has no route line, which `OfflineRegionRouteLine.kt` reserves for fetching or failure, so no line means stored.
- **A one-frame flicker.** On the frame between "Getting trail routes" and the finished list, the form showed idle: no progress bar, Download enabled, budget still 1336, and the new row not yet listed. On the next frame the finished row was there. I'm noting it, not calling it a fault.
- **Delete: done.** A swipe on the S22test row deleted it, with an "Offline map deleted · Undo" snackbar. After the undo window: only Region 1 listed, budget 1336 / 6000, and `files/offline_routes/` holds only `2` (Area 1's).

**3. Day glance: pass.** I looked at Trail Map at three zooms over a park with trails: town scale, neighbourhood scale, and close in. Land patches filled, forest symbols, water, dashed trail lines, labels, the waypoint pin and the location puck all drew. Nothing was blank.

**4. Night glance: pass.** I ticked Night Maps (Tools > Settings > Night Mode > Night Maps). The map switched to the night style: dark land, deep blue water, dark green forest with dim tree symbols, light roads, and trail dashes still visible. The mini-map in Journal > Records was also in night style. **Flash after the first load: none seen.** Leaving Maps for Journal and coming back, with screenshots about 0.2 s apart, the map area's mean brightness stayed at about 60 (out of 255) on every Maps frame. The lighter frames were the Journal screen. Each screenshot takes about 0.2 s, so a flash shorter than that could fall between frames. I then unticked Night Maps and the map was back in day style.
- The map chrome (search bar, icon bar, bottom bar) stays light in night style. I'm noting that, not judging it.

**5. Recording across a locked screen: pass on Elder's line. One part of the dispatch's wording does not match by design (see below).**
- **Setup:** the shell's mock-location appop was set to allow (it was "default" before). I added `gps` and `network` test providers and fed fixes from a neutral public spot with `cmd location providers set-test-provider-location gps`, one a second, stepping about 10 m a fix. The phone has no lock-screen security (`locksettings get-disabled` = true, keyguard never showing), so "lock" here means screen off (`KEYCODE_SLEEP`; power state read "Dozing").
- **Run 1 was a test-harness error, not an app failure.** I fed fixes without `--time`, so each carried millisecond clock time. Records showed the track at once, but it read "No usable points — all 16 fixes were from the network provider". That is the app's sub-second-timestamp rule working as written (`domain/NetworkProviderFix.kt:56`, `timestampEpochMillis % 1000 != 0` counts as a network fix). It is not a defect. I fixed the harness and ran the step again.
- **Run 2:** I fed fixes with whole-second `--time`. Recording started, then fixes moved for about 15 s. The screen was off for about 62 s with fixes still moving, then feeding stopped. I woke the screen and pressed Stop about 13 s after the last fix.
  - **Fixes kept arriving with the screen off:** logcat `ForagerFix` lines continued at about 2 a second through the whole off period. Stored points ran at 5 s spacing (0, 5, 10 … 72, 77 s from the start) with no gap across the off period.
  - **End waypoint = the last fix fed, exactly.** This is Elder's line in `2026-10-24-02-promotion-recheck-388-report.md:82`, "confirm the end waypoint lands", the path that reads `lastGatedGpsFix`. The End waypoint's stored position equals, to the stored precision, the last fix the feeder sent. Run 1 shows the same: its End waypoint equals that run's last fix, even though all its track points were excluded.
  - **The track shows in Records at once:** right after Stop, Journal > Records > Tracks listed it, with "16 points" and a thumbnail. Run 1's track also showed at once.
  - **Where the dispatch's wording does not match:** the dispatch says "the track's end point lands at the last fix fed". The track's **last stored point** is 4 s (3 fixes, about 30 m) before the last fix fed, in both runs. That is the sampler's 5 s interval: `TrackRecordingMode.kt:27`, HIGH_ACCURACY `minIntervalMillis = 5_000L`, applied by `LocationSampler`. The last fixes fell inside 5 s of the last stored point. So the end of the drawn track matches the last fix only to within one sampling interval. The End waypoint is the thing that lands exactly. I read Elder's line as being about the End waypoint and record it as passing. Whether the dispatch meant the waypoint or the last track point is for the planner to settle. I changed nothing for it.
- **Two observations (not judged):**
  - While the screen was off, the map's own location puck stopped updating. After waking, it showed the position from when the screen went off, until a new fix came. The recording itself kept every fix, as above.
  - After Stop and after the test tracks were deleted, the purple recording-trail line of run 2 still showed on the map (seen at 02:26 and 02:27 Pacific). Later screenshots are of a different map view, so I don't know whether it clears on its own.

**6. Phone left as found: done.**
- Build 3901 (1.0.3901+g073fc77b) is installed, the build this check is for.
- Night Maps is off (Settings checkbox unticked, map in day style).
- No test area: Offline maps lists only Region 1, the budget is 1336 / 6000, and only Area 1's route folder is on disk.
- Test data deleted through the app: both test tracks (swipe, "Track deleted", past the undo window) and their four Start/End waypoints. Records now reads Tracks 0, Waypoints 1, Offline maps 1, the same as before the check. A database read after cleanup: tracks 0, track_points 0, waypoints 1, offline_regions 1. The temporary database copy on the laptop has been deleted.
- Test providers removed: `remove-test-provider` for gps and network; `dumpsys location` logs "removed mock provider override" for both. The shell's mock-location appop is back to "default".
- **Real fixes are back, but it took one extra action.** After the test providers were removed, real gps and network read `last location=null`. The app was registered on both (1 s, HIGH_ACCURACY / BALANCED), but no fix came for about 2 minutes. I turned the location master switch off and back on (`cmd location set-location-enabled false` then `true`). A real network fix arrived within 45 s, and the app showed its normal "Approximate location, finding …" with the puck at the phone's real place. Turning the switch off raised the system's "No location access" dialog, which I closed. Location is enabled (`is-location-enabled` = true).

## Disclosure
- **Confirmed vs inferred.**
  - Confirmed on the device: versions, database user_version, route files on disk, the database counts after cleanup, End waypoint positions against the feeder's log, and the point spacing.
  - Read from code, not run: what the route line means, the 5 s sampler interval, and the timestamp rule.
- **Could not determine.**
  - Whether the count stage renders. The download was too fast to catch.
  - A night-mode flash shorter than about 0.2 s.
  - Whether the leftover recording-trail line clears by itself.
  - The installed build's version from the schema JSON at 530cbf8a, which is not in my clone. I read it from the device header instead.
- **Premises that were wrong.**
  - "Lock the screen": this phone has no keyguard, so I tested screen off.
  - "The track's end point lands at the last fix fed": true for the End waypoint, not for the last track point (see step 5).
- **Decided beyond scope.**
  - I re-ran step 5 after the harness error in run 1.
  - I turned the location master switch off and on to bring real fixes back.
  - I named the test area "S22test" and used 1 mi, the smallest radius.
