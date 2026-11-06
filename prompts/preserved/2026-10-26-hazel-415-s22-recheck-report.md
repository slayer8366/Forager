# S22 re-check of PR #415 at f4439827: "on" is the trail itself (Hazel, 2026-10-26)

- **Asked by:** Rowan, for dispatch `2026-10-26-13` Amendment 2 (RECORD -1683: "on" is the engine's trail route without the straight joints). Re-runs only what failed or was not run in the first check (`prompts/preserved/2026-10-26-hazel-415-s22-check-report.md`, branch `quiet-off-trail-s22-report`, `f87d728b8`).
- **Build:** `app-debug-apk` from PR run 40399232541 (all five jobs green), head `f44398276`. versionName **1.0.4042+g52858a09**, versionCode 4042; `52858a09` is GitHub's merge of `f44398276` into pre-main `410802b39` (parents read with `git log`). sha256 `21cd0a55…b3b166b649`.
- **Before installing:** the phone had 1.0.4033 (the first check's build). `forager.db` user_version 22; the build declares `version = 22` (`ForagerDatabase.kt:206` at `52858a09`). versionCode went up from 4033 to 4042. Installed with `adb install -r`.
- **Phone:** S22 (SM-S908U), R5CT321008R. 2026-10-26, 21:10 to 21:30 UTC.

## Result

| Step | Result |
|---|---|
| 1. Return at 64 m out along the track left by, stop there: quiet state after about 15 s | **Met.** Not there at 21:17:31, there at 21:17:34: 14 to 17 s after Return (21:17:17.1). It never appeared in the first build's reading of this band, where "on" reached 64 to 69 m |
| 3. Off past 50 m (one quiet re-route), on to 80 m: "Off trail" all the way out; back: "Back on trail" only within about 44 m | **Met.** "Off trail" in every dump from 47 m (held) through the re-route at 50 m and out to 80 m and back to 45 m (dumps at most 3.1 s apart; "Back on trail" shows 5 s, so it could not fall between them). "Back on trail" first at 31 m on the way back; with the 10 s re-arm that puts "on" at about 44 m |
| 2. Back on (quick) | **Met.** Walking E1 back: "Off trail" to 35 m, "Back on trail" at 31 and 27 m, gone at 24 m (3 to 8 s on screen). "On" therefore from about 44 to 46 m, not the 64 to 69 m of the first check |
| 4. Trailhead to start never off (quick) | **Met.** P1 to T to S: usual line or "Approaching", never "Off trail"; "Arrived" at S, return ended |
| Quiet | **Met.** No sound, no vibration, no notification post (below) |

No step failed. Nothing pushed to `step7-quiet-off-trail`.

## Step 3 in detail (OSM distances, so approximate)

| Fix | Metres from the trail | HUD status line |
|---|---|---|
| 28 to 48 | 35 to 47 (held at 47 from 33) | usual line |
| 51 | 47 (held) | "Off trail · Return to Route along your track" |
| 65 | 50 | far-band warning in the log (21:21:53), the re-route; figure 659 then 676 ft by fix 70 |
| 70 to 90 | 56 to 80 | "Off trail" |
| 90 to 115 | 80 (held 20 s) | "Off trail" |
| 115 to 146 | 74 back to 35 | "Off trail" |
| 149, 151 | 31, 29 | "Back on trail" |
| 154 | 25 | usual line |

The first check's failure, "Back on trail" at 64 m walking away after the re-route, did not happen: the dump at fix 73 (60 m) and every one after it out to 80 m read "Off trail".

## Method

The first check's scripts and its saved OSM extract, copied to `/mnt/work/scratch/quiet-off-trail-s22-recheck/` with only the directory changed (`stub.json` byte-identical, sha256 `b594e87e…`). No map data fetched. Same route: Hares Canyon Trail (OSM way 117028118), Region 2, the same points S, T, P1, P2, E2. The step 3 leg is byte-identical to the first check's (diffed). The one new leg: the stray from P2 stops at E1 64 m R (the first check went to 100 m), and Return is tapped there. gps and network test fixes, one a second, 4 m and 20 m accuracy, 1.4 m/s. Recording started at S. HUD read by `uiautomator` about every 3 s with the fix index; screenshots every 4 s; `dumpsys vibrator_manager`, `audio`, `notification` before and after; logcat from a cleared buffer.

## Counts from the phone's log

**Sample:** one process, pid 18033 (one `Start proc`, 21:10:25), from a cleared buffer, 21:10:17 to 21:30. Each fix is logged about three times by `ForagerFix`; counted by distinct fix time, as the first check did.
- **FATAL EXCEPTION:** 0. **ANR:** 0.
- **Fixes, steps 1 to 4** (out leg loaded 21:11:06 to the end at 21:26:52): **947 fed, 946 received**, 0 received that were not fed. The 1 missed is 21:11:34, the second the test providers removed themselves (feeder log 21:11:33, re-added).
- **Whole session:** 1,033 fed, 1,011 received. The other 21 missed are all before the out leg: 7 before the app's process started (21:10:18 to 21:10:24), 10 between 21:10:26 and 21:10:35 while the app opened and I started the recording (cause not determined), 4 around the providers' self-removals at 21:10:40 to 21:10:47.
- **Quiet:**
  - the vibrator history's entries for the app are identical before and after (the last is from 2026-10-25);
  - `dumpsys audio` names the app 0 times, before and after;
  - the only notification the app held was "Recording a track" (importance 2, `vibrate=null sound=null`);
  - 0 notification posts by the app in the log.
- **Way-home warnings:** 1, the far-band warning at the step 3 re-route (21:21:53). 0 "strayed … not re-routed".

## The phone after

- **Test items deleted in the app** (swiped away in Journal > Records): the test track (Oct 26, 2:10 PM) and its Start and End waypoints.
- **Database, against the copy taken before installing:** tracks, waypoints, offline regions, cached searches and every track point row identical; user_version 22. The owner's items (Areas 1 and 2, the two 2026-10-24 tracks and their waypoints, Waypoint 1) untouched.
- **Preferences:** all 8 DataStore files byte-identical to before (so Trail, Night Maps off). They were also identical to the first check's starting copies.
- **Settings:** test providers removed (`dumpsys location`, 14:29:33 phone time); mock location `default`; stay-awake 0; rotation auto (accelerometer 1, user 0, not changed this run). `/sdcard/ui.xml` deleted.
- Screenshots and logs stay in `/mnt/work/scratch/quiet-off-trail-s22-recheck/`, off GitHub.

## Disclosure

**Confirmed vs inferred.**
- Confirmed from the HUD dumps and the log: each step's sequence and timings within the dump spacing; the quiet checks and counts.
- Inferred: where "on" begins (about 44 m), from the "Back on trail" timing less the 10 s re-arm; that fix 65 is the re-route, from the far-band warning and the figure changing (no log line records a re-route as such); all distances are to OSM geometry, not the engine's line; "no sound" rests on the audio service with the ringer muted.

**Could not determine.**
- Exact transition times (dumps every 3 s).
- Why 10 fixes at 21:10:26 to 21:10:35 were not received, during setup and before the test legs.

**Premises that were wrong.** None found. The build's merge base is pre-main `410802b39` (#417), newer than the first check's `c91f34c49`.

**Decided beyond scope, for the planner to accept or reject.**
- Step 1 was run at 64 m from P2 (the first check's E1 line), inside the asked 60 to 69 m band, with Return tapped there; step 2's quick check is the walk back from that point.
- Steps 2 and 4 were re-run whole, as part of the same walk, rather than as a shorter spot check.
- Steps 5 and 6 and the #413 items were not re-run (not asked).
