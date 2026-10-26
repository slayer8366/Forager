# S22 check of PR #415, step 7 PR 3: the quiet off-trail state (Hazel, 2026-10-26)

- **Asked by:** Rowan, for dispatch `prompts/preserved/2026-10-26-13.md` with Amendment 1 (branch `records-after-173`), RECORD -1631, -1676. Sorrel reviews the code in parallel; I did not read that review.
- **Build:** `app-debug-apk` from PR run 40375884163 (all five jobs green), **versionName 1.0.4033+gbc5b9e61**, versionCode 4033. `bc5b9e61` is GitHub's merge of the PR head `cc9eeebd3` into pre-main `c91f34c49` (parents read with `git log`). sha256 `23ec23af…10603ddd`. The dex holds `WayHomeOffTrail` and the string "Off trail · Return to Route along your track".
- **Install:** the phone had 1.0.4021 (#413's check). `forager.db` read user_version 22; the build declares `version = 22` (`ForagerDatabase.kt:206` at `bc5b9e61`). Installed with `adb install -r`: no downgrade, versionCode 4021 to 4033.
- **Phone:** S22 (SM-S908U), R5CT321008R. Times are UTC, 2026-10-26, 18:37 to 19:58.

## Result

| Step | Result |
|---|---|
| 1. Off the trail, Return, about 15 s: trail dims, track unchanged, the notice, quiet | **Met.** The notice came 14 to 17 s after Return; the trail line dimmed, the dotted track did not change; no vibration, no sound, no notification |
| 2. Walk the track back: about 10 s on, "Back on trail" about 5 s, then the usual line | **Met** |
| 3. Off again: 15 s nothing, then quiet; past 50 m a re-route, still quiet; back on: "Back on trail" | **Not met.** The quiet state came on time, and the re-route came at about 50 m. But about 10 s after the re-route, while the walker was still walking away at 55 to 64 m from the trail, the HUD read **"Back on trail"**, then the usual line, then "Off trail" again 15 s later. Quiet throughout (no sound, no vibration) |
| 4. Own track from the trailhead to the start: never "off trail" | **Met** |
| 5. Back unchanged | **Met as far as checked**: Back shows "Exit navigation?" (a dialog that exists on pre-main); not compared on a pre-main build |
| 6. Observations | Below; none is a gate |

**Code problem: yes, step 3.** I have stopped there as asked: nothing is pushed to `step7-quiet-off-trail`, and nothing is changed. The #413 observations were run after it, since they do not depend on this PR's code.

## The step 3 finding

**What the phone showed** (HUD dumps about every 3 s; distances are from each fed fix to the OpenStreetMap trail, so they are approximate):

| Fix (step 3 leg) | Walker, metres from the trail | HUD status line |
|---|---|---|
| 35 to 49 | 45 to 47, held at 47 | usual line |
| 50 | 47 | "Off trail · Return to Route along your track" |
| 64 to 66 | 48 to 51 | the figure changes (re-route); the log has the way home's far-band warning at 18:53:54 |
| **76** | **64, walking away** | **"Back on trail"** |
| 79 to 88 | 69 to 79 | usual line |
| 91 | 80 (held) | "Off trail · Return to Route along your track" |
| 132 | 52, walking back | "Back on trail" |
| 138 | 45 | usual line |

**Why, from the code (read, not instrumented).**
- `WayHomeWatch.onFix` judges each fix against `wayHome.trail.points` and `wayHome.trackToStart.points` (`WayHomeWatch.kt:171` at `bc5b9e61`).
- The trail part runs between two of the walker's own track points, "joined to the engine's route by straight joints of at most 25 m each" (`WayHome.kt`, the `trail` doc). The point where the walker left the trail is the last track point within 25 m of a trail.
- "On" is within 40 m plus the fix's accuracy of any of those points (`WayHomeOffTrail.kt`, `limit`). With 4 m accuracy, that is 44 m around a joint end that already sits up to 25 m out along the walker's own leaving track. **So along the track the walker left by, "on" reaches about 64 to 69 m from the trail, not 44 m.**
- After a re-route during a stray, the new line's leave point is on **this stray's** track, about 20 m out. The walker, 51 to 64 m from the trail and still walking away, is within 44 m of it. After 10 s that reads as back on ("Back on trail"); once they pass about 64 m, it reads off again after 15 s.
- The observed boundary matches: on going out, off from about fix 76 (64 m), so "Off trail" at 91; on coming back, on from about fix 122 (62 m), so "Back on trail" at 132.

**The same widening touches step 1.** With the dispatch's "60+ m", a walker who leaves the trail perpendicular and stops between 60 and about 69 m out would never get the quiet state. I saw this in the code before the run and set the step 1 stray to 100 m so that step 1 would test the timing. That is a call beyond the dispatch (listed below). A 60 to 69 m stop was not run.

**What to do about it** is not mine to choose. Two options that would remove it:
- measure "on" against the engine's route without the two straight joints;
- or measure the leaving end from the trail rather than from the track point.

Either changes what step 7's state measures, so it is a planner and owner decision.

## Method

**Fixes.** Test fixes came from the gps and network providers, one a second, with whole-second times and 4 m accuracy (gps) and 20 m (network). I used the earlier feeder from `/mnt/work/scratch/waypoint-route-s22/`, copied with only its directory changed.

**The route.** Hares Canyon Trail (OSM way 117028118) in Region 2. I used the OSM extract already saved by the #404 and #413 checks (`stub.json`, byte-identical in both). No new map data was fetched. Walked at 1.4 m/s. Distances along the trail are written c (metres from the way's first node); R and L are right and left of increasing c.
- **S**, the start: 70 m R of c300.
- **Out:** S to the trail at c300 (T), along the trail to c380 (P1), then a stray E2 85 m L and back. Then along the trail to c480 (P2), then a stray E1 100 m R. E2 is on the walked-out path, so step 3 can stray along it without the far-from-both alert.
- **Recording:** started at S, High accuracy.
- **Step 1:** Return tapped at E1's end, held 45 s.
- **Step 2:** E1 back to P2, 25 s held on the trail, on to c430.
- **Jitter (observation):** back to P1, out E2 to 40 m, then 120 fixes at 41 to 47 m (random, seeded), back to P1, 30 s held.
- **Step 3:** out E2 to 47 m, held 25 s, on to 80 m, held 20 s, back to P1, 30 s held.
- **Step 4:** the trail P1 to T, then the walker's own track T to S, held.

**Reading the phone.**
- HUD text: `uiautomator` dumps about every 3 s, each with the fix index fed at that moment (`hud.log`).
- Screenshots: every 4 s.
- Quiet: `dumpsys vibrator_manager`, `dumpsys audio` and `dumpsys notification` before and after.
- Log: logcat from a cleared buffer.

## Steps in detail

**Step 1** (Return at 18:45:17.6, walker at E1's end, about 99 m from the trail).
- The way home showed by the first dump after the tap ("1155 ft · by trail").
- The notice was absent at 18:45:31.5 and present at 18:45:34.6, so 14 to 17 s after Return. Its full form showed on its own line under "by trail".
- Screenshots 6 s before and 8 s after: the solid trail line and its casing are visibly lighter; the dotted track and the dashed track part are unchanged.

**Step 2.**
- "Back on trail" first showed at fix 38, about 47 m out, and was gone by fix 43. It showed in two dumps, 18:46:41 and 18:46:43, and not at 18:46:46, so 3 to 8 s on screen.
- I infer "on" began near fix 22 to 26 (about 64 to 69 m out, as above); the dumps only bound it.

**Step 4.** From P1 to T to S, the status line was empty or "Approaching", never "Off trail". "Arrived" showed at fix 102, about 3 m from S, and the return ended.

**Step 5** (a second Return, from E2's end, after walking S to T to P1 to E2 again).
- The quiet state came after about 20 s (checked at 22 s).
- Back opened "Exit navigation?" ("Your track will keep recording."). That dialog is on pre-main (`AvailabilityNavigationUi.kt:455` at `c91f34c49`), and this PR touches no Back path.
- "Keep navigating" left the state and notice as they were. Back again, then "Exit", ended the way home, and the HUD and the line went.

## Observations, not gates

- **By day** (Trail basemap):
  - the dimmed trail line is a pale blue;
  - the dotted track keeps its purple and blue dots;
  - the two are easy to tell apart.
- **By night** (Night Maps on): the dimmed trail is a muted mid-blue with its dark casing on the dark green. It reads less strongly than by day. The dotted track stands out clearly against it.
- **Portrait:** the full notice fits on its own line under "by trail" and covers nothing. The short form "Return to Route" never showed.
- **Landscape** (rotation locked to 90° and put back): the full notice fits on one line at the bottom of the HUD card. It does not cover "774 ft".
  - In that view the **Re-center pill overlaps the turn text** ("right 81°" is half hidden).
  - That pill is pre-existing, not this PR's notice. I did not check pre-main for the same overlap.
- **360 dp:** not tested; the S22 runs at its default width.
- **Flicker at a trail's edge:** 120 fixes at 41 to 47 m from the trail, 120 s.
  - 56 crossings of 44 m (against OSM geometry); the longest run beyond 44 m was 8 s.
  - **The state never went off.** No flicker: the 15 s hold absorbed it.
  - That band was mid-trail, away from the joint ends, so the step 3 widening did not apply there.
- **"Approaching" outranks the notice.** Within 100 m of the start, the status line shows "Approaching" ahead of the off-trail notice (`NavigationReadout.kt:177` before `:187` at `bc5b9e61`). None of my strays went off inside that zone, so I did not see a hidden notice; it is how the order is written.
- **The figure at the start:** 406 ft showed about 5 m before "Arrived" at S. That is the way home's ruled figure; this PR does not touch it.

## #413's device items (observations only)

- **(a) Stale and lost fix, waypoint route** (Night Maps on; fixes stopped at 19:09:49):
  - +40 s: "Last fix 43 s ago". The figure greyed and every line part faded.
  - +120 s: "Last fix 2 min ago", still faded.
  - +330 s: "No fix for 5 min". The figure became a dash and every part of the line turned grey.
  - Matches `STALE_AFTER_MILLIS` 30 s and `LOST_AFTER_MILLIS` 5 min.
- **(b) The figure before "Arrived"** (a test waypoint W1 40 m off Hares Canyon Trail; walked lead-in, trail, final leg):
  - the last figures were 303 ft at about 30 m left, 275 ft at about 23 m and **247 ft at about 14 m**;
  - "Arrived" came at about 10 m.
  - With the 25 m gone, the figure still reads about 60 m (200 ft) high to the end. That fits the 60 m reach (`WAY_HOME_FIGURE_REACH_METERS`); I did not trace it further.
- **(c) The retry at an area's edge, every 50 m inside** (waypoint C's spot again, Crown Zellerbach Trail; walker from 90 m outside Region 2's north edge):
  - At the start: "The waypoint is in another area · straight line" (logged `WAYPOINT_IN_ANOTHER_AREA`).
  - At the first fix inside: `LEAVES_AREA` (19:48:45.5), "Route leaves your downloaded area".
  - Then **again at 19:49:21.5 and 19:49:57.5**, 36 s apart, which is 50 m at 1.4 m/s. Both `LEAVES_AREA`.
  - At about 19:50:31 (fix 193, about 150 m inside) the HUD went to **"0.9 mi · by trail"**. A successful search logs nothing, so that time is from the screen.
  - Walked on to "Arrived". So the 50 m retry fires, and here the fourth ask found a route.
  - The route reads 0.9 mi for about 0.2 mi of trail, as the earlier check's control did. I infer the same road detour; not verified.

## Counts from the phone's log

**Sample:** one process for the whole run, pid 6159 (one `Start proc`, 18:37:30), from a cleared buffer, 18:37:28 to 19:58:13.
- **FATAL EXCEPTION:** 0. **ANR:** 0.
- **Fixes, steps 1 to 4** (18:37:35 to the end of step 4): 1,934 fed, 1,931 received (`ForagerFix: provider=gps`), 0 received that were not fed. The 3 missed:
  - the first second;
  - 2 at moments the test providers removed themselves (feeder log "PROVIDERS GONE", re-added).
- **Fixes, whole session:** 4,482 fed, 4,476 received. The 6 missed are those 3, plus:
  - one at a feeder restart;
  - one more self-removal;
  - one at 19:42:55 I could not explain (the walker was held still).
- **Alerts:**
  - The vibrator history's entries for the app were identical before step 1, after step 4 and at the end. The last one is from 2026-10-25.
  - `dumpsys audio` names the app 0 times.
  - The only notification the app held was its "Recording a track" ongoing notification (importance 2, `vibrate=null sound=null`).
  - 0 NotificationManager posts by the app in the log.
- **The app's way-home warnings:** 1, the far-band warning at the step 3 re-route (18:53:54). 0 "strayed … not re-routed".
- **Way to a waypoint:** 4 straight-line results in the log (1 `WAYPOINT_IN_ANOTHER_AREA`, 3 `LEAVES_AREA`), all in item (c).

## The phone after

- **Test items deleted in the app** (swiped away in Journal > Records): the test track, its Start and End waypoints, and test waypoints W1 and WC.
- **Database, against the copy taken before installing:** tracks, waypoints, offline regions and cached searches are row-for-row identical, the track points are identical per track, and user_version is 22. The owner's items (Areas 1 and 2, the two 2026-10-24 tracks and their waypoints, Waypoint 1) were not touched.
- **Preferences:** all 8 DataStore files are byte-identical to before. That includes the map preferences, so Trail and Night Maps off (Night Maps was turned off again in Settings).
- **Settings restored:**
  - test providers removed (`dumpsys location`: removed at 12:58:12 phone time);
  - mock location `default`;
  - stay-awake 0;
  - rotation back to auto (accelerometer 1, user 0).
- **Phone media:** no screen recordings were made. The uiautomator dump file was deleted.
- Screenshots stay in `/mnt/work/scratch/quiet-off-trail-s22/`, off GitHub.

## Disclosure

**Confirmed vs inferred.**
- **Confirmed** from the HUD dumps, the screenshots and the log:
  - steps 1, 2 and 4 and their timings within the dump spacing;
  - step 3's sequence (off, re-route, "Back on trail" at 64 m walking away, off again);
  - the quiet checks and the counts;
  - #413's (a), (b) and (c) as described.
- **Inferred:**
  - the cause of step 3, from reading `WayHomeWatch.kt:171`, `WayHome.kt` and `WayHomeOffTrail.kt`. No log records the state's changes or the line's geometry. The boundary distances fit that reading, but the joint position was not measured on the phone;
  - all distances, which are to OSM geometry, not the engine's line;
  - "no sound", which rests on the audio service, with the ringer muted;
  - the 0.9 mi road detour in (c).

**Could not determine.**
- Exact transition times: the HUD was read about every 3 s.
- Whether a 60 to 69 m stray in step 1 stays "on" on the phone: inferred, not run.
- Whether "Back" matches pre-main on a pre-main build: not installed.
- Whether the landscape Re-center overlap exists on pre-main.
- The 19:42:55 missed fix.

**Premises that were wrong.**
- Step 1's "60+ m" assumes "off" begins at about 44 m. Along the walker's own leaving track it begins at about 64 to 69 m (above).
- Step 3's "past 50 m a re-route, still quiet" assumes the re-route keeps the walker off; on the phone it brought them back "on".

**Decided beyond scope, for the planner to accept or reject.**
- The step 1 stray was 100 m, not 60, after reading the code.
- E2 was laid on the walked-out path, so step 3 and the jitter could stray without the far-from-both alert.
- The flicker observation was run as a seeded 41 to 47 m jitter mid-trail.
- Step 5 used a second Return at E2.
- I stopped the recording before the #413 items and ran those after finding the step 3 problem.
- Landscape was set with the rotation settings and put back.
- **One slip:** my background wait on the W1 walk matched its own command line, so it ran to its 30-minute limit. The phone sat with the walker held at W1 for about 25 minutes. Nothing else came of it.
- A first naming attempt for W1 was dismissed by a tap outside the dialog. Nothing was saved (database checked).
- No network fetch of mine other than GitHub (`gh run download`, `gh api`, `git fetch`). The phone's own apps went online as usual: a weather app requested a forecast for the test location. That was not my request, but it was the mock location.
