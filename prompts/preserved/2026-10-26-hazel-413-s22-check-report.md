# S22 check of PR #413, step 6 PR 3: the waypoint route on the map and in the HUD (Hazel, 2026-10-26)

- **Asked by:** Rowan, for dispatch `prompts/preserved/2026-10-26-11.md` with Amendment 1 (branch `records-after-173`), RECORD -1652, -1653, -1669. Sorrel reviews the code in parallel; I did not read that review.
- **Build:** `app-debug-apk` from PR run 40310887423 (all five jobs green), **versionName 1.0.4021+g67abb0cd**, versionCode 4021. `67abb0cd` is GitHub's merge commit of the PR head `a00742ffb` into pre-main `c1deeef85` (parents read with `git log`). sha256 `5ffb1112…54fb692`. The dex holds `AvailabilityWaypointRoute` and `WayToRetry`.
- **Install:** the phone had 1.0.3994 (#408's check). Its `forager.db` header read user_version 22; the build declares `version = 22` (`ForagerDatabase.kt:206` at `67abb0cd`). Installed with `adb install -r`; no downgrade, versionCode 3994 to 4021.
- **Phone:** S22 (SM-S908U), R5CT321008R. Times are UTC, 2026-10-26, 11:16 to 12:04.

## Result

| Step | Result |
|---|---|
| 1. Navigate: straight line first, then three parts within about a second, no blank frame, "by trail" | **Met.** 0.70 s from the first frame that answered the tap to the first three-part frame |
| 2. Walk it: the figure counts down | **Met** (with the figure's ruled margin, see below) |
| 3. Stray > 50 m: one quiet re-route, straight line briefly; a second stray re-routes once | **Met** on both strays; no vibration, no sound |
| 4. Arrive within 15 m: "Arrived" | **Met**, at about 12 m |
| 5. Back and X end it | **Met**, both |
| 6. Outside any area: straight line, "straight", the note | **Met**: "No trail routes here · straight line" |
| 7. Item C: walk into the area, the three-part route replaces the straight line | **Met on the second sample, not on the first.** The retry fired on the first fix inside both times. The first sample's search came back "Route leaves your downloaded area", and by design nothing tried again while the walker went on inside (details in Step 7) |
| 8. Observations | Recorded below; none is a gate |

**Code problem: none found.** Step 7's first sample is how Amendment 1's trigger is specified, not a defect, so I have not changed anything. Whether it is what the owner wants is a question for Rowan (see "For the planner").

## Method

**Fixes.** Test fixes came from the gps and network providers, one a second, with whole-second times and 4 m accuracy. I used the previous Hazel's feeder from `/mnt/work/scratch/recording-line-s22/`, copied and re-pointed, with no other change. Paths were built from OpenStreetMap geometry and walked at 1.4 m/s.

**Test waypoints.** All were mine, made in the app with Plan > Waypoint at a test fix. The database read each one back at exactly the intended position.
- **A:** 40 m off Hares Canyon Trail (L.L. Stub Stewart State Park), inside Region 2.
- **B:** outside every area, 30 m off Crown Zellerbach Trail, north of Region 2's route area.
- **C:** 30 m off Crown Zellerbach Trail, 269 m inside Region 2's north edge.
- **D:** 20 m off a forest track (OSM way 260139043), 199 m inside the same edge, with no other way within 248 m.

**Region 2's route area** is the rectangle `GeoDistance.boundingBox` gives for its centre and 24 km (`routeAreaFor`, `RegionRouteData.kt:177`). Its north edge is at 45.848783.

**Timing and frames.** I used `screenrecord`, decoded at a constant 20 fps (one frame every 50 ms). Phone times come from `date` in the same shell as the tap and the recording. The phone clock ran about 0.2 s ahead of the laptop's. HUD text came from a `uiautomator` dump about every 4 s.

**The log.** Logcat was captured from a cleared buffer, 11:16:12 to 12:03:36.

## Step 1: Navigate (waypoint A, walker 40 m off the trail)

Navigate was tapped in the waypoint's bubble at phone time 15,617.65 (written mod 100,000 s; the recording started at 15,615.64).
- **Frame 40 (2.00 s):** the first frame that answers the tap. The Return control becomes Stop navigating.
- **Frames 42 to 53:** the map zooms in. Today's straight dashed line runs to the waypoint, and the HUD shows "right 67°", "straight".
- **Frame 54 (2.70 s):** the three parts appear: the thin dashed lead-in to the trail, the solid trail, and the thin dashed final leg. The HUD reads "0.3 mi", "slight left 30°", "by trail".
- **0.70 s** from the first answering frame to the swap (one frame is 50 ms). Frame 53 has the straight line and frame 54 has the three parts. Every frame from 42 to 54 has one line or the other, so there is **no blank frame**.
- **The figure.** Expected is 40 m lead-in + about 350 m of trail + 40 m final leg = about 0.27 mi. The HUD showed 0.3 mi.

## Steps 2 to 4: the walk (557 fixes: the lead-in, the trail, two strays of 75 m, the trail, the final leg)

**Step 2, countdown.** On the trail the figure fell from 0.3 mi (1,315 ft at index 67) to 328 ft at index 543. It rose only during each stray, while the walker walked away from the line.
- **The figure reads high.** It was 200 to 280 ft above the distance still to walk along the line. For example, at the end of the trail (index 527) the walker had 40 m (131 ft) left, and the HUD read 412 ft.
- **This is the way home's ruled figure,** reused unchanged:
  - it counts from the earliest node within 60 m of the fix (`WAY_HOME_FIGURE_REACH_METERS`, RECORD -1620);
  - it adds 25 m while that node is on the trail (`WAY_HOME_TRAIL_PLACE_ALLOWANCE_METERS`, RECORD -1613);
  - read at `WayHomeProgress.kt:148-152` at `a00742ffb`.
- So the figure is "never short", as ruled. Over the waypoint's short final legs the margin is a large share of what is left.

**Step 3, strays.**
- **Stray 1** (left of the trail at c350, out to 75 m). The re-route showed at 11:24:10.2 (phone). The straight line and "≈ 1000 ft · straight" held for about 0.35 s (frames 222 to 228), then "1115 ft · by trail" with a new dashed lead-in from where the walker was.
  - At that second the fed fix was 50.0 to 51.3 m from the OSM trail. That distance is measured to OSM's geometry, not the engine's line, so it is approximate.
- **Stray 2** (right at c450, out to 75 m). The re-route showed at 11:27:25.3. The straight line held for about 0.25 s (frames 284 to 288), then three parts. The fed fix was 51.2 m out.
- **One re-route per stray.** Neither recording shows a second straight-line interval in its 50 s.
- **Re-routes outside the two recordings are not counted** (see Counts). The HUD log at 4 s intervals shows no figure reset outside the strays.
- **Quiet:**
  - `dumpsys vibrator_manager` history for the app was identical before and after the whole run. Its last app entry is from 2026-10-24, so no vibration fired.
  - `dumpsys audio` named the app nowhere.
  - The phone's ringer was muted the whole time, so "no sound" rests on the audio service, not on listening.

**Step 4, arrival.** "Approaching" showed from about 615 ft. "Arrived" showed at index 547, which is 9 fixes (about 12 m) short of the waypoint, under the 15 m rule.

## Step 5: Back and X

- **X (Stop navigating) at Arrived:** navigation ended, and Return to vehicle came back.
- **Back:** I moved the walker to about 120 m out and opened the bubble, then tapped Navigate. The route came up as "394 ft · by trail". The first Back ended the navigation and returned to the bubble, which is the way in. The second Back closed the bubble.

## Step 6: outside any area (walker and waypoint B both outside)

Navigate was started from Journal > Records > Waypoints. The HUD read "≈ 800 ft", "slight left 10°", "straight", and the status line read **"No trail routes here · straight line"**.
- The log has the matching fallback: `NO_TRAIL_ROUTES: no downloaded area covers either end`.
- In landscape the same note shows in the HUD card at the bottom of the screen. It is fully readable, with its baseline about 28 px above the screen edge.

## Step 7: item C (start outside, walk in)

**First sample: Crown Zellerbach Trail, waypoint C.**
- At the start the walker was 90 m outside the edge. The HUD read "0.3 mi · straight · The waypoint is in another area · straight line" (logged as `WAYPOINT_IN_ANOTHER_AREA`).
- At the first fix inside (index 83, 0.1 m inside, fed at 11:41:59) the retry fired. At 11:41:59.502 the log reads `LEAVES_AREA: the trail route runs outside region 8's area`. The note became "Route leaves your downloaded area", the approved short form.
- After that **nothing tried again for the rest of the walk:** about 330 m inside, all the way to "Arrived" at C, on the straight line.
  - That follows `WayToRetry` as built: after a straight-line result, only a rising edge into an area holding both ends tries again, or 50 m walked for "You're far from any trail" (`WayToRetry.kt`, the class doc).
  - The walker was already inside, so no edge came.
- **Control.** I stopped navigating, moved the walker 98.5 m inside, and Navigated to C again. It gave **"0.9 mi · by trail"**, where the trail distance is about 0.2 mi.
  - The drawn route ran on south-east along the trail past C. I could not zoom the map out far enough to see where it ended (see "Could not determine").
  - **Inferred, not verified:** the engine matched C to a different way than the trail I put it 30 m from. My OSM pull for this spot held only paths and tracks, and Scappoose-Vernonia Highway runs beside this trail. A route that goes to a road point by a detour would explain both the 0.9 mi and the first sample's route leaving the area near the edge.

**Second sample: forest track, waypoint D** (chosen with every highway type pulled, nothing else within 248 m of D).
- At the start the walker was 80 m along the track outside the edge. The HUD read "≈ 1000 ft · straight · The waypoint is in another area · straight line".
- At the first fix inside (index 84, fed at host second 15,599) the recording shows the note go at frame 84. At **frame 85 (4.25 s, phone 15,599.42)** the HUD reads "764 ft · by trail". That is about 0.6 to 1.0 s after the first fix inside was fed, **without restarting Navigate**.
- At the recording's zoom, which was far out (it carried over from a list-started Navigate), the lines cannot be told apart. The HUD is the evidence there. The three parts at this spot were then photographed close up by day and night (Step 8).
- Walked on to "Arrived" at D (index 239).
- **The figure** stayed at 764 ft for the first 40 fixes (about 55 m) after the swap. It rose to 808 ft at index 127, then counted down. That is the same ruled figure as in step 2: it counts from the line's first node until the walker is 60 m past it.

**A download finishing while navigating** was not tested.

## Step 8: observations, not gates

- **Line kinds by day** (Trail basemap, step 1 and stray 2 screenshots): thin dark-blue dashed legs and a solid blue trail line, all distinct on the green basemap. At step 2 the dashed lead-in and the dashed final leg showed together, with the solid trail between them.
- **By night** (Night Maps on, at D):
  - the solid trail is light blue with a dark casing;
  - the thin dashed lead-in is light blue;
  - both are clearly visible on the night green.
- **"by trail" and a note in landscape:** see step 6. I did not view "by trail" itself in landscape. The note's card sits bottom-centre, right of the map controls.
- **Screen off and on mid-route** (at D, the route showing, 15 s off): the route and "775 ft · by trail" were unchanged after.
  - No fixes were received while the screen was off: 15 fixes, as foreground-only (Q5) predicts.
  - No new search was logged. Successful searches are not logged at all (see Counts).
  - After waking, the map was panned with "Re-center" showing. My unlock swipe likely dragged the map, so that pan is not evidence of anything.
- **Not part of this step path:**
  - after Navigate from the bubble, the bubble stayed open over the map;
  - Navigate from Journal > Records leaves the map at its current zoom, while Navigate from the bubble zooms in.
  - I did not compare either with pre-main.

## Counts from the phone's log

**Sample:** one process the whole time, pid 28614 (one `Start proc`, 11:16:21), from a cleared buffer, 11:16:12 to 12:03:36.
- **FATAL EXCEPTION:** 0. **ANR:** 0.
- **Fixes fed, gps:** 2,821 distinct seconds.
- **Fixes received, gps:** 2,771 (`ForagerFix: provider=gps`), and 0 received that were not fed. Network: 2,775 received.
- **The 50 not received:**
  - 18 while the app was starting behind the lock screen (11:16:17 to 11:16:38);
  - 26 in the two screen-off windows (11 and 15);
  - 6 single seconds, each at a moment when the test providers removed themselves. `dumpsys location` shows "removed mock provider override" at each, and the feeder re-added them. This is the same self-removal recorded at RECORD -1643.
  - 18 + 26 + 6 = 50.
- **Searches started.** Only straight-line results are logged (`FindWayToWaypointUseCase.straight`). A successful search and a re-route log nothing. So:
  - **4 straight-line searches** in the log: step 6 (`NO_TRAIL_ROUTES`), step 7 start (`WAYPOINT_IN_ANOTHER_AREA`), step 7 retry (`LEAVES_AREA`), and the second sample's start (`WAYPOINT_IN_ANOTHER_AREA`).
  - **9 successful searches seen on screen:** the step 1 Navigate, the 2 stray re-routes, the step 5 Navigate, the step 7 control, the second sample's retry, and 3 Navigates at D for step 8.
  - So **13 searches in all, by screen and log together**, not a log count.
- **Re-routes: 2,** both seen in recordings. 0 "strayed … not re-routed" warnings, so no stray was met while a search ran or after its re-route was spent. 0 "Working out the way to the waypoint failed". 0 "Couldn't read the waypoint".
- **The app's other warnings:** 54 vendor-property denials, framework input/back noise, 30 map tile HTTP warnings (the phone was online), 9 map notes ("A camera move … is not a touch", night recolouring, a forecast source skipped during a style load). None names the route.

## The phone after

- Test waypoints A to D were deleted in the app (swiped away in Journal > Records). The database reads 5 waypoints, 2 tracks, 1,419 track points, Regions 1 and 2 unchanged, 3 cached searches, user_version 22: all as before.
- The owner's items were not touched.
- All 8 DataStore preference files are byte-identical to before. That includes the map preferences, so basemap Trail and Night Maps off as before (Night Maps was turned off again in Settings).
- Test providers removed (`dumpsys location`: removed at 12:03:20). Mock location `default`. Stay-awake 0. Rotation back to auto (accelerometer 1, user 0).
- The six screen recordings were deleted from the phone. Screenshots and recordings stay in `/mnt/work/scratch/waypoint-route-s22/`, off GitHub.

## Disclosure

**Confirmed vs inferred.**
- **Confirmed, from the recordings, the HUD dumps and the log:**
  - the swap timings and no blank frame at Navigate and at both re-routes;
  - the notes' wording;
  - the retry firing on the first fix inside, both samples;
  - LEAVES_AREA on the first sample;
  - the counts above.
- **Inferred:**
  - that C was matched to a road, which would explain the 0.9 mi route and LEAVES_AREA;
  - the re-route distances (measured to OSM geometry, not the engine's line);
  - "no sound", which rests on the audio service with the ringer muted.

**Could not determine.**
- **The full drawn route to C.** Single-finger input can only pan, and double-tap zoomed only once, so I never saw the whole route.
- **The number of searches from the log.** Successful searches log nothing.
- **Whether the bubble staying open after Navigate is new or today's behaviour.** Not compared.

**Premises that were wrong.**
- "Count searches started from the phone's log": the log carries only straight-line results.
- Nothing else in the dispatch's step path was contradicted.

**Decided beyond scope, for the planner to accept or reject.**
- **A second item C sample (waypoint D)** after the first came back LEAVES_AREA. I chose it to tell "the trigger fails" from "this spot's route leaves the area".
- **Two OpenStreetMap fetches,** for route design near the area edge.
- **Landscape** was set with the rotation settings and put back.
- **Three slips:**
  - My first Overpass request carried the owner's email address in its User-Agent header, sent to the public Overpass API. It should not have; the second request did not. Disclosed, nothing to undo.
  - A mis-aimed gesture moved the Search panel's radius slider from 5 to 30 mi. I set it back to 5 mi before closing, without searching, and the preferences read byte-identical after.
  - I typed "Hazel" into the search field and cleared it unsent. Cached searches are unchanged at 3.

## For the planner

**Item C at an area's edge.** The retry asks from the first fix inside, which is by construction at the edge. If that one answer is LEAVES_AREA, or any note other than "far from any trail", nothing asks again until the walker leaves and re-enters.
- The first sample shows that can strand a walker on the straight line for the whole walk in.
- Whether to also try again after walking on (for example, once per 50 m inside, as the far-from-trail case already does) is a behaviour choice. It is not mine to make here.
