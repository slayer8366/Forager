# S22 check of the distance figure on pre-main fb692fb57 (Hazel, 2026-10-25)

- **Asked by:** Rowan, with the owner's go (RECORD -1626, "Merge now, phone after").
- **Build:** pre-main `fb692fb57` (A, B and C merged; push run 40165007509 green). The APK is `app-debug-apk` from that run, **versionName 1.0.3952+gfb692fb5**, versionCode 3952. It has the 60 m reach, and it does not have #397.
- **Phone:** S22, R5CT321008R. Times are UTC.

## Summary
- **Ordinary trail: the figure read 68 to 86 m long** on recording 1 (40 samples), and 71 to 84 m long on recording 2 (22 samples). That is in line with the 60 to 85 m expected, given about ±11 m of uncertainty in how the samples are timed. It was never short there.
- **It reads short in one case: just after a re-route, while the walker keeps walking away.** The new line starts where the walker was when the re-route was found. Until the next re-route, the figure stays at that line's total and does not grow as the walker walks on. It was short by about 27 m before the second re-route, and about 12 m at the stray's end. This is the main finding (section 3).
- **At Return, the figure read 37 m below the length of the route I walked.** It was 516 m against 553 m designed. It was not below the drawn line's own length. The line the app drew is shorter than the trail geometry I walked, and the screenshot shows it cutting a bend of Hares Canyon Trail at the far end of my walk.
- **Beside the parallel leg the figure was very long:** up to 279 to 291 m long, while the walker was on the trail within 60 m of the stray's far end. Over that stretch it rose by 81 m while the walker walked home.
- **One swap and two re-routes:** neither showed a frame with no line.
- **Style smoke check:** the app crashed on the 2nd basemap switch, with F1's exception at `MapForecastCells.kt:58`. This was expected, since #397 is not in this build. The crash ended recording 2.
- **One provider removal,** within 2 ms of a low-memory kill. The feeder re-added the providers in 0.16 s.
- **Test tracks deleted, including the 05:25 to 05:30 one.** The pre-main build is left installed.

## 1. Method
**The phone before the check:**
- Installed build: 1.0.3948+gadfcf75f, with `forager.db` at user_version 22 (database header).
- The new build declares `version = 22` (`ForagerDatabase.kt:206` at `fb692fb57`), so there was no downgrade. Same signer.
- I installed with `adb install -r`. The header read 22 afterwards.

**Fixes:**
- From the gps and network test providers, one a second with whole-second `--time` stamps, accuracy 4 m, at 1.4 m/s.
- Every fix fed was logged with its second and its index in the path (`fixes.csv`, 2,494 fixes).

**The HUD:** read with `uiautomator` about every 3.3 s (`hud.log`).

**Units:** set to Metric for the check, so that the figure reads to the metre. Imperial switches to tenths of a mile above 402 m. I put Imperial back afterwards; the preferences file is byte-identical to the copy saved before.

**The walk.** I used Hares Canyon Trail (OSM way 117028118), from geometry fetched once from Overpass on 2026-10-24 by the S22 way-home check. Distances are along the trail from its south-west end, written "c".
- **Start:** 40 m to the right of the trail, opposite c = 250.
- **Out:** to the trail at c = 250, then along the trail to c = 580 (330 m).
- **The stray:** 45 m to the left, then back along a leg parallel to the trail, 45 m from it, as far as c = 460 (138 m).
- **Home:** the same path in reverse, 553 m in all.
- I checked that no other way in the fetched data is within 74 m of the start leg or 146 m of the stray.

**Truth.** For each HUD sample, the truth is the distance left along the route I walked, from the fix fed at that second. The figure may lag the fixes, so each sample gets a range:
- the larger truth is at the fix fed 8 s before the sample. That gives the smaller "long by", the strict bound for "never short";
- the smaller truth is at the fix fed in the sample's second.

The two differ by about 11 m.

**A prediction made before the walk.** I wrote a replica of `placeOnWayHome`'s figure (`WayHomeProgress.kt` at `fb692fb57`: the earliest node within 60 m anywhere on the line, plus 25 m when that node is on the trail part, with nodes every 10 m). It ran on the designed line and gave its expected figure for each fix. It is a check on the reading, not the app's own line.

## 2. The figure against the truth (recording 1, 05:46:15 to 05:52:47; recording 2 repeats it)
"Long by" is the figure minus the truth, given as a range across the samples. Negative means short.

| Part of the walk home | Samples (rec 1) | Long by, rec 1 (strict .. loose) | Rec 2 (strict) | What the code predicts |
|---|---|---|---|---|
| Track back, first 37 m from Return | 12 | **−37 .. +11** | −37 .. 0 | 0 to 60 m long, against the drawn line |
| Track back, the rest of the stray (to the trail) | 24 | +4 .. +68 | +4 .. +59 | about 60 m long |
| Trail beside the parallel leg (about c 460 to 580) | 43 | **+54 .. +291** | +53 .. +279 | up to the line's whole total: the stray's far end is within 60 m |
| Ordinary trail (c 250 to about 440) | 40 | **+68 .. +97**; strict +68 .. +86 | +71 .. +84 (22 samples) | 60 to 85 m long |
| Start leg (the last 26 m) | 2 | +87 .. +102 | (not walked) | nodes on the trail within 60 m, plus 25 m |

### 2a. The first 37 m: short against the walked route, not against the drawn line
- **The figure at Return was 516 m on both recordings.** The route I walked is 553 m.
- **The recorder's last point was the walker's position at Return** (0.0 m apart, read from the database). I held still 16 s before tapping Return. So the line starts at the walker.
- **The line the app drew is the shorter one.**
  - I put the designed path and OSM's Hares Canyon geometry over a screenshot of the drawn line (screenshot 10, georeferenced from the walker's dot and the Start pin). Near the trail's far end the drawn trail part runs straight, while OSM's trail bends south (`10-overlay2.png`).
  - My model of the line from the stored points, with OSM's trail geometry for the trail part, gives 542.6 m. The app's 516 m is 27 m shorter.
  - So the engine's trail network here is shorter than the OSM geometry I walked, by about 27 to 37 m. **Inferred:** the engine routes on its own data, which is not the Overpass fetch. Which of the two matches the ground could not be determined.
- **For a walker who follows the drawn line,** the figure at Return equals the true distance home. It was then 0 to 60 m long over the first 60 m, as the code's reach gives.
- **For a walker who keeps to the OSM trail,** it read up to 37 m short for the first 37 m.

### 2b. Ordinary trail: 68 to 86 m long
- On the 40 samples from c 440 down to c 250, the strict range is +68 to +86.
- The app's figure matched the replica's prediction to within −3 to +8 m over the same stretch. For example: 284 against 282.6, 229 against 224.9, 117 against 120.1. So the drawn line and the walked route are the same length west of the bend.
- **Never short.** The highest strict values, 86 and 87, sit within the 60 m reach, the 25 m allowance, and the sampling window.

### 2c. The parallel-leg case (close to Sorrel's hairpin shapes)
- While the walker was on the trail with the stray's far end within 60 m, the figure counted from the start of the line.
  - 05:49:22 to 05:50:14: **516 m, the line's whole total, against 226 to 309 m truly left. Long by up to 291 m.**
  - At 05:50:17 it dropped to 310 m in one step, from 516 m. It dropped at fix index 242, where the replica predicted the drop at about 256.
- **On the approach to that stretch the figure rose.** From 05:48:43 to 05:49:22 it went 435 → 448 → 454 → 461 → 468 → 482 → 497 → 510 → 516, while the walker walked about 55 m towards home. That is the rising figure of M-395-5, seen here on the 60 m build.
- **Never short.**

## 3. The re-route (recording 2): short after it, while the walker walks on
**The setup.** On the walk home I left the home path at index 330, on the trail with 97.8 m truly left, and walked 90 m off it. The recorder stored a point every 5 s (about 7 m).

**What the logs and the screen recording show** (`reroute.mp4`, 75 s from 06:10:03):

| Time | Walker (fix index) | Figure | Walked truth | Long by |
|---|---|---|---|---|
| 06:10:31 | 362 to 364 | 163 m | 142 to 145 m | +18 |
| 06:10:34 | 366 | **WayHomeWatch: "The walker is over the far band from the way home"** | | |
| about 06:10:34.5 (frame) | | 153 → **126 m** in one frame step | 147 m | **−21** |
| 06:10:38 to 06:10:53 | 370 to 385 | **126 m, unchanged** | 153 to 174 m | **−27 to −48** |
| 06:10:54 | 386 | second "over the far band" | | |
| about 06:10:55 | about 388 | 126 → **168 m** | 179 m | −11 |
| 06:11:00 onwards (walker still, at 394) | 394 | 168 m | 188 m | **−20** |

**What this shows, against the drawn line itself.**
- After each re-route, the new line starts at the newest stored track point. The walker walks on away from it.
- Every node within 60 m of the walker is then the line's first node or near it, so **the figure is the line's total and does not grow.**
- From 06:10:34 to 06:10:53 the walker walked 19 fixes, about 27 m, straight on along the stray, away from that first node. The figure stayed 126 m throughout. So it read **at least 27 m short of the drawn line's own distance from the walker** just before the second re-route. That figure is the distance walked since the re-route, and it does not depend on the trail data.
- The second re-route came at 06:10:54, when the walker was about 27 m past the newest point stored at the first. The far band is 50 m, so the first new line probably began further back than that point. **Inferred, not checked:** that re-route's search used the track as stored a few seconds earlier. Either way, the figure was short by at least the 27 m walked. The second line can have used points only up to about index 385, the last stored before 06:10:54. The walker went on to 394 (about 12 m), so at the stray's end the figure was short of the drawn line by about 12 m.
- **The bound is the far band.** The next re-route comes when the walker is 50 m from the new line, so a walker going steadily away reads up to about 50 m short just before each re-route.
- **Inferred, not tested:** the same holds at Return for a walker who taps Return while still walking away. The line starts at the newest stored point, up to one storage interval behind them (7 m here). The S22 way-home check saw the dotted run start about 20 m short of the walker's dot.

**Against the walked route** the figure was further short, by up to 48 m at 06:10:53. My model of each new line on OSM's trail (145.9 m and 174.1 m) is 6 to 20 m longer than the app's totals (126 and 168 m). That is the same kind of gap as at Return.

**What the walker saw** (frames, about 107 per second):
- At the first re-route the line and figure changed between frames about 0.35 s apart. The figure fell from 153 to 126 m as the walker walked away from home.
- No frame in the 75 s had no line: at least 10 line pixels in every frame of 8,008.
- **"Re-route every stray" (-1625):** it re-routed twice in about 90 m of stray, as F2 found on the 50 m build.

## 4. The swap
- **Recording 1** (`swap.mp4`): the way home was on screen 2.13 s into the clip. I could not place the moment of the tap within the clip to better than about 1.5 s.
  - **One frame at 10.33 s showed no line** (both counts 0, a light-green fill over the map). It was 8 s after the swap and the next frame had the line. It is the single-frame blank already noted on the S22 way-home check (3.3), not the swap.
- **Recording 2** (`swap2.mp4`, tapped about 1.5 s into the clip):
  - The track was drawn in blue at once (frame 178, 1.52 s). The HUD read 508 m for the track.
  - The way home, at 516 m, replaced it by 2.05 s.
  - The recording line was drawn in every frame (at least 37 pixels).
  - **No empty frame.**

## 5. Style smoke check (#397 not in this build)
- **Setup:** recording 2 was running, and navigating, with the walker at the stray's end.
- **Basemap switch 1** (to Topo): no crash.
- **Switch 2** (back to Trail): crashed at 06:17:35.590 with `IllegalStateException: Calling getSourceAs when a newer style is loading/has loaded.` at `MapForecastCells.kt:58`, the same as F1.
- **The crash ended recording 2.** After relaunching, the control read "Start recording track".
- **Logged, not a finding,** as asked. I stopped the smoke check there.

## 6. Provider removals
- **One removal not made by me:** 05:39:00.903 (gps) and .904 (network).
  - The Samsung eUICC service (pid 12913) was killed with signal 9 at the same moment. Zygote logged 05:39:00.905.
  - The feeder caught it and re-added both providers by 05:39:01.128.
  - This matches the pattern on the S22 way-home check (5 of 5 removals beside a low-memory kill).
- **The removal at 06:19:45 was mine,** at cleanup.

## 7. The phone, as left
- **Build:** **1.0.3952+gfb692fb5** (pre-main `fb692fb57`), schema 22. Left installed for the owner's next walk.
- **Database,** read from a copy after cleanup:
  - user_version 22;
  - 2 tracks (the owner's walks, 596 and 823 points) and 1,419 track points;
  - 5 waypoints (Waypoint 1 and the owner's two Start/End pairs);
  - 2 offline regions.
- **Deleted through the app** (Journal, Records):
  - three test tracks: 05:25 to 05:30 (left by the #397 check), and recordings 1 and 2;
  - their five Start/End waypoints. Deleting a track leaves its waypoints with no track, so I deleted those one by one by swipe-Delete.
- **Settings:**
  - Units back to Imperial, and map preferences byte-identical to before (Trail, Night Maps off);
  - `stay_on_while_plugged_in` back to 0, and rotation unchanged;
  - test providers removed, and the shell's mock-location appop back to default.
- **Area 1** and the other region: not touched. The engine only read their routes.
- **Media and logs** stay off GitHub, under `~/.cache/s22-figure-check/`: screenshots, three screen recordings, logcat, `hud.log`, `fixes.csv` and the scripts.
- **One stray process during the check:** a `uiautomator` crash at 05:53:44 (pid 29286, "UiAutomationService already registered"), from two of my HUD reads overlapping. It is not the app.

## 8. Not done, and could not determine
- **Where exactly the drawn line leaves OSM's geometry.** I have it only from one georeferenced screenshot, so the 27 to 37 m is placed by inference, and by the replica matching west of the bend.
- **Which route matches the ground:** the engine's or OSM's.
- **Screen-off, and the "start back by" time:** not run. It was after sunset.
- **Imperial display:** not looked at during the walk, since Metric was set for resolution.

## 9. For the planner and the owner (not acted on)
- **F-1. After a re-route the figure can read short of the drawn line,** by the distance the walker has walked on since the re-route. That is up to the far band's 50 m before the next re-route; seen here at 27 m, and at about 12 m at the stray's end. The 60 m reach covers a fix that is near the line ahead of where the walker truly is. It does not cover a walker past the line's first node. Whether to add the walker's distance from the first node when they are beyond it is the owner's decision.
- **F-2. The drawn line can be shorter than the trail on the base map** (27 to 37 m here, over about 330 m of trail). If the engine's route is the true one, nothing is wrong. If OSM's geometry is, the figure is short for a walker who follows the base map's trail.
- **F-3. The parallel-leg overcount is large.** The figure was up to 291 m long, and rose 81 m while the walker walked home. It is never short.
