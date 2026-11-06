# Report: the S22 way-home check and timing run, plus the 60 m fix (dispatch 2026-10-24-10 Amendment 4)

**Helper:** Hazel (Opus). **Planner:** Rowan. **Run:** 2026-10-25, 00:35 to 02:15 UTC. **Phone:** S22 (SM-S908U, R5CT321008R).
**Rulings:** RECORD -1620, -1621.
**What was read first:** CLAUDE.md, the modular-code skill, the dispatch with Amendment 4, RECORD -1613, -1617, -1618, -1620 and -1621, Sorrel's re-checks of #395 and #396, and my part 2 report (sections 6 and 8).
**Where things are kept:** screenshots, screen recordings and logs stay off GitHub, under `~/.cache/forager-wt/s22-way-home-2/`. This report gives no positions. The test walks ran on a public trail inside the S22's second downloaded area ("Region 2"). Step 11 used a public downtown street outside every area. Area 1 was not touched.

## Summary
- **The 60 m fix is in B and C, and green.** Two commits on `way-home-part2-domain` (`99abdb150` the tests, failing first; `ac6f6826f` the fix). The fail-first run and the revert check both compiled, and each failed in exactly the five tests the 50 m reach should break. The full suite is green on B (5,526 tests) and on C after the merge `679d9c3e8` (5,538). The evidence is on #395.
- **On the phone:** the swap at Return and both re-routes showed no frame with an empty map. Neither re-route showed the track at any point, so for O-396-10 the walker saw the old trail line, then the new one, each time in under a second.
- **Step 4 could not be run as written.** A second stray re-routes again, because the new line starts where the walker stands, and that re-arms the stray judge. This goes to the owner (finding F2).
- **Crash on main's code (finding F1).** The app crashed 4 times with the same exception on a map style change: Night Maps toggled, or the basemap switched. The crashing code is `MapForecastCells.kt:58`, and it is on main, pre-main and dev. Part 2 code is not in the stack. I did not fix it.
- **The test providers were removed 5 times.** Each removal came within 3 ms of a system process being killed for low memory (section 4).

## 1. The 60 m fix (part 2 of the dispatch)

### Commits and runs
| What | Commit / branch | Run | Result |
|---|---|---|---|
| Tests, failing first | `99abdb150` (also `way-home-b4-failfirst`) | 40147115565 (wayhome package) | 0 `e:` lines. 63 tests, **5 failed** (below) |
| The fix | `ac6f6826f` on `way-home-part2-domain` | 40147173906 (pull_request, #395) | all five jobs success; **737 suites, 5,526 tests, 0 failures, 0 errors, 24 skipped** |
| Revert check b11 | `033b28f6b` on `way-home-b-revert-b11` (one commit on `ac6f6826f`) | 40147195968 (wayhome package) | 0 `e:` lines. 63 tests, **5 failed**, the same five |
| B merged into C | `679d9c3e8` on `way-home-part2-visible` (a merge) | 40147874822 (pull_request, #396) | all jobs success; **742 suites, 5,538 tests, 0 failures, 0 errors, 24 skipped** |

### The change (`WayHomeProgress.kt`)
- **The fix itself:** `WAY_HOME_FIGURE_REACH_METERS = LIVE_FIX_MAX_ACCURACY_METERS.toDouble() + WAY_HOME_LINE_STEP_METERS`, which is 60 m.
- **Its doc** cites RECORD -1620, Amendment 4 and `PathHome.kt`'s "never short" ruling. It gives the argument (the node at or behind the walker is never more than one step behind them) and says how the gate's 50 m alone fell short (B-395-2).
- **L-395-10:**
  - The allowance constant's doc now says the allowance was ruled when the figure counted from the place, and that since Amendments 3 and 4 it is a margin on top of the reach. It stays as ruled (-1613).
  - The `WayHomePlace`, `toPathHome` and function docs now describe the reach.
  - The test class doc now describes the place and the figure as they are.
  - In the PR body, the `WayHomeProgress` bullet is rewritten.
- **Sizes:** `WayHomeProgress.kt` 167 → 175 lines; `WayHomeProgressTest.kt` 142 → 202.
- **Nothing outside the figure changed:** not the place, the needle, the greyed part, the hop bands or the stray judge.

### Tests (Sorrel's B-395-2 cases)
Each test asserts that the figure is at least the true distance, and that the fix is within the gate's 50 m of the walker.
- Shape 1 on the hairpin's track back: walker 1.1 m past the first node, fix 49.9 m ahead.
- Shape 1 on the track to the start: walker at (0, 61), fix at (0, 110.9).
- Shape 2 at the gate's edge on the PR's hairpin, legs 20 m apart: Sorrel's (e), walker (101, 0), fix (105.4, 49.8).
- Shape 2 on the same hairpin with legs 70 m apart: Sorrel's (d), walker (105, 0), fix (105, 49.9).
- **One existing case changed.** The allowance case moves from (200, 0) to (195, 0) and now expects 380 + 25 m. From (200, 0), the node at (140, 0) is 59.9999999995 m away, so that case would turn on rounding.

### Fail-first and revert evidence
- **Fail first (40147115565):** `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran, with 0 Kotlin `e:` lines. The five failures, from the JUnit XML:
  - shape 1, track back: `never short: 561.666… against 568.899…`
  - shape 1, track to start: `54.545… against 59.0`
  - shape 2, 20 m legs: `264.999… against 419.0`
  - shape 2, 70 m legs: `274.998… against 465.0`
  - allowance case: `expected:<405.0> but was:<394.999…>`
- **Those numbers were predicted before the run.** A replica of `WayHomeLine` and `placeOnWayHome` (outside the repo, `~/.cache/forager-wt/hazel-60m-scratch/replica.py`) gave every one of them, and they match Sorrel's re-check.
- **Revert b11 (40147195968):**
  - The whole edit is one line, `WayHomeProgress.kt:168`, dropping `+ WAY_HOME_LINE_STEP_METERS` (`git diff --stat`: one file, 1+/1−).
  - It ran in its own worktree, cut from the pushed commit. The forward file was saved before the edit (SHA-1 `6fc88603…`). The forward worktree was never edited, and its file still matches that copy.
  - Both compile tasks ran, with 0 `e:` lines. The same five tests failed with the same messages, which is what this edit should give: it puts back exactly the reach the fail-first run had. No other test failed.
- **The forward change is still present.** `origin/way-home-part2-domain` and `origin/way-home-part2-visible` both carry the 60 m line at `:168`. The domain file's SHA-1 equals the saved copy.
- **Where the evidence is posted:** the #395 comment, https://github.com/slayer8366/Forager/pull/395#issuecomment-6391833647.

## 2. The timing build (part 1 of the dispatch)
- **The branch:** `way-home-timing`. C's head `44e28ba9b` merged in as `adfcf75ff` (a merge), and pushed. This branch is never merged.
- **The run:** 40146910056 (workflow_dispatch, `*WayHome*` tests), success. The APK is `app-debug-apk`, version **1.0.3948+gadfcf75f** (`aapt2`, and `dumpsys package` after the install).
- **Database:** the installed build was 1.0.3906 at schema **22**, read from the database header and `PRAGMA user_version` on a copy before installing. The timing build declares 22 (`ForagerDatabase.kt:206`). I installed with `adb install -r`, no uninstall. The header read 22 afterwards.
- **This build has the 50 m reach.** It is C at `44e28ba9b`, before the 60 m fix, so every distance figure below is the 50 m reach's.

## 3. The 11 steps
**Method:**
- The fixes came from the gps and network test providers, with whole-second `--time` stamps, one a second, at walking pace (1.4 m/s).
- I took paths from public trail geometry, fetched once from OpenStreetMap's Overpass service.
- Screen recordings came from `screenrecord`, decoded every frame with GStreamer (about 120 frames a second), and read frame by frame.
- I logged the HUD text from `uiautomator` every 2 to 3 s, and kept `logcat` throughout.

**Six test recordings, all deleted afterwards (section 6):**
1. A walk out, Return, then the walk home with two strays. It ran until the providers were removed and real fixes came in. Everything after 01:12:10 on it is discarded.
2. A walk out that leaves the trail, then Return and the walk home with the screen off, then arrival.
3. A short walk, Return, then the touch tests.
4. A short walk with Night Maps on, ended by crash 2.
5. The basemap-switch test, ended by crash 3.
6. Outside every area, for step 11 and step 7.

| Step | Result | What was seen |
|---|---|---|
| 1. The dotted layer, by day | **PASS** (one sub-check not judged) | See 3.1. |
| 2. The dotted layer, by night | **PARTIAL** (Trail basemap PASS; Topo and OSM could not run) | See 3.2. |
| 3. The swap and a re-route, frame by frame | **PASS** (no flash; no "Unable to calculate route") | See 3.3; the clip for O-396-10. |
| 4. Re-routing ended | **could not run as written** | See finding F2. |
| 5. Off-line fallback with a failed re-route | **could not run** | I found no spot where the second search finds nothing. Overpass was busy, so I could not pick one. |
| 6. Screen-off sundown | **PARTIAL** | See 3.4. |
| 7. The note's precedence | **PASS** (recording 6) | See 3.5. |
| 8. Expected false off-track alert | **noted, not filed** | See 3.6. |
| 9. Arrival | **PARTIAL** ("Arrived" and the line cleared; the ring not captured) | See 3.7. |
| 10. Insets and touch | **PASS** (with a premise correction) | See 3.8. |
| 11. Each reachable note | **1 of 4 run, PASS**; 3 could not run | See 3.9. |

### 3.1 Step 1 (recording 1, and again on recordings 2 and 3)
- **The track came at once.** Return was tapped at about 4.95 s into the clip. The track's line was drawn from 5.03 s, solid blue.
- **The swap followed about 0.7 s after the tap,** between frames #637 (5.654 s) and #638 (5.662 s), 8 ms apart. Every frame across it has a line, so there is no empty frame. The camera was zooming in at the same time, so a two-source split (the passed part updated before the ahead part) could not be judged at that zoom.
- **The parts after the swap,** read zoomed out:
  - the dotted run, my track back to where I left the trail;
  - the solid run along the trail;
  - the dotted run to the start.
  - The way home's two kinds are one colour (blue). The dots read as round dots, and at the joint a dot sits on the solid run's end. The solid run is cased.
- **Whether the dotted run is uncased could not be judged.** It is drawn over the recording line's own dotted line, purple on a pale casing, so I could not separate the two.
- **The figure at Return:** "0.4 mi". The planned way back along my own path was 630 m (0.39 mi).
- **The figure on the walk home (recording 2):** at the junction, with 75 m truly left, the HUD read 485 ft (148 m): long by 73 m, never short. On the last stretch it read 453, 421, 390 and 223 ft, then "Arrived".
- **Not checked:** I did not compare zoom levels one in and one out systematically.
- **Observation:** on recordings 2 and 4 the dotted run starts about 20 m short of the walker's dot (screenshots `12-rec2-return`, `35-night-topo-wayhome`). Inferred, not verified: the run starts at the last stored track point. The recorder kept 180 points from about 950 fixes.

### 3.2 Step 2 (recording 4, 01:51 to 01:56, after dark)
- **Trail basemap with Night Maps on: PASS.** The way home's blue dots are clearly visible over the night tiles (crop `35-crop.png`). No greyed part was in frame, so I could not judge whether the dotted run stands apart from it.
- **Topo and OSM could not run.** Switching to Topo crashed the app (crash 2, F1).
- **My error that cost the night screenshots.** My tap to close the Layers sheet landed on the system Home button. I deleted those two screenshots without viewing them, because they showed the launcher.

### 3.3 Step 3: the swap and a re-route, frame by frame (recording 1)
**The clip for the owner (O-396-10):** `~/.cache/forager-wt/s22-way-home-2/video/step3-reroute.mp4`
- It is 180 s long and runs from 01:02:18.2 UTC, which is when SurfaceFlinger created the recorder's virtual display.
- Re-route 1 is at 48.54 s into the clip, and re-route 2 at 111.33 s.
- Frame sheets of each are beside it: `sheet-step3-zone.jpg` and `sheet-step3-zone2.jpg`.
- The Return swap is `video/step1-return.mp4`, at 5.66 s.

**What the walker saw:**
- **Re-route 1.** The walker strayed from the trail part.
  - The watch logged "over the far band" at 01:03:06.135.
  - The old trail line stayed drawn, with its greyed passed part, and the HUD stayed on the old line's figure: 1,248, then 1,228 ft. The HUD never read "Unable to calculate route".
  - At 48.544 s (about 01:03:06.8, ±0.3 s) the new way home replaced it in one frame: dotted back along my stray, then solid along the trail. The figure became 1,215 ft.
  - **The track showed for 0 frames. The re-route took about 0.7 s.**
- **Re-route 2.** The second stray, from the new line: see F2.
  - The far-band log came at 01:04:09.070, and the new line at 111.33 s (about 01:04:09.7).
  - **Again 0 frames of track. About 0.6 s.**
- **Why no track appeared, by reading the code (not measured):**
  - The HUD and the map line take the track only in the route tick (`ROUTE_TICK_MILLIS = 5_000L`), and only while `progress` is `OffLine`.
  - A re-route that publishes in under a second leaves no tick in between, most of the time.
  - So the span the owner is ruling on depends on how long the search takes: under a second here, warm, with the `.rd5` already open. Cold, it would be longer (see the timing in section 5).
- **No re-route log line.** Sorrel's list expected one. The code logs only suppressions and fallbacks, not a re-route that succeeds.
- **One-frame blank frames, not tied to the swap.** Across the 180 s there are 5 single frames, about 8 ms each (36.95, 46.43, 50.64, 88.61 and 103.13 s). In each, the map shows a lighter basemap with trail labels, and no way-home or recording lines. They are spread through the clip, not at the re-routes. Cause: could not determine. It may be how `screenrecord` captures the map.

### 3.4 Step 6 (recording 2)
- **The screen was off 01:24:11 to 01:29:59 UTC (5 min 48 s),** walking the trail part at 1 m/s. On waking, the way home was intact, the HUD read "485 ft · Approaching", and nothing had been lost.
- **The "start back by" comparison could not run.** Sunset was at 6:11 PM PDT, so from then on the strip read "dark in N min", with no start-back time.
- **Before sunset (recording 1, screen on),** the strip's "start back was" moved 5:03 → 5:05 → 5:06 as the way-home figure fell. That is consistent with sundown reading the route shown, but it is not the screen-off check.
- **M-396-2's carried band could not be judged.** I had no stray during the screen-off span.

### 3.5 Step 7 (recording 6)
- With "No trail routes here · following your track" showing, I stopped the fixes.
- About 60 to 75 s later the line read **"Last fix 1 min ago"**, in place of the note.
- Within 15 s of fixes resuming (with movement), the note came back.
- On recording 2 (no note) the same span read "Approaching · last fix 1 min ago".
- **Observation, inferred:** a hold of identical repeated fixes also produced "last fix 1 min ago" (recording 3, 01:38:59). Identical fixes appear not to count as new ones.

### 3.6 Step 8 (recording 2)
- The off-track notification was posted at **01:26:07 UTC** (channel `off_track_alert_v2`).
- The walker was then on the way home's trail part, about 80 m from the outbound track, by design.
- This is the expected false alert from my report section 7, so I noted it and did not file it.
- A true off-track alert also fired during recording 1's stray. That one was correct.

### 3.7 Step 9 (recording 2)
- **Seen:**
  - The HUD read "Arrived" from 01:32:39 to 01:32:45, when the walker came within about 12 m of the start.
  - At 01:32:48 the control went back to idle, and the way-home lines were gone from the map (`17-check`).
  - After Stop, the next recording's Return (recording 3, frame by frame) drew no leftover way-home layer before its own search: the track at 3.05 s, the new way home at 3.57 s.
- **Not captured:** the ring at the start (the screenshots missed the 9 s window), and sundown going back to the track's figure (it was after sunset).

### 3.8 Step 10 (recording 3, way-home layers drawn)
- **The premise was wrong.** There is no waypoint long-press menu in production.
  - `CompactMapMapSlot.kt` passes `{}` for `onLongPress`, and `SightingsMap.kt:77` documents that no production caller consumes it.
  - Long-presses at five points did nothing, as expected.
  - So I tested whether a touch reaches the map with drags: a pan detaches the camera and shows "Return to Route".
- **Portrait: PASS.** Drags of 424 px from five points all panned the map, which I confirmed from screenshots:
  - just below the HUD (300, 720);
  - mid-map (720, 1500);
  - just above the bottom bar's inset (600, 2560);
  - the left edge (60, 2000);
  - the right side (1100, 1150).
- **Smaller drags while following did not pan.** Drags of 113 px and 277 px left the camera following. Inferred, not investigated: a follow-mode threshold.
- **Landscape: PASS.**
  - Drags of 277 px moved the map from seven points: just above the HUD, mid-map, beside the record cluster, near the rail, below the search bar, and two at the bottom edge. The change in a map crop was 8.3 to 10.6 in each case.
  - One drag that started on the record cluster did not move the map. That is chrome, as expected.
  - A tap on a saved track opened its bubble.
- **Rotation:** I set it with `user_rotation` and restored it (section 6).
- **Observation:** in landscape the "Return to Route" pill sits over the HUD's distance figure (`26-l-clean.png`).

### 3.9 Step 11
- **"No trail routes here · following your track": PASS.** It appeared on recording 6, outside every area, logged once: `FindWayHome … (NO_TRAIL_ROUTES): no downloaded area covers the start`.
- **"not downloaded": could not run.** It would mean deleting the routes of an area I did not create.
- **"Getting trail routes…": could not run.** It would mean downloading a region.
- **"No trail on your way out": could not run.** I had no checked spot inside an area with no trail near, and Overpass returned 502/timeout twice.

## 4. Findings for the planner and the owner (not acted on)
- **F1. Crash on a map style change. The code is on main.**
  - **The crash:** `java.lang.IllegalStateException: Calling getSourceAs when a newer style is loading/has loaded`, at `MapForecastCells.kt:58` (`style.getSourceAs<GeoJsonSource>(field.sourceId)`), in a `LaunchedEffect` coroutine.
  - **Four crashes, all with the same stack:**
    - 01:47:05, returning to the Maps tab after ticking Night Maps, while returning;
    - 01:55:54, basemap Trail → Topo, Night Maps on, while returning;
    - 01:58:36, the first basemap switch after Return;
    - 02:12:24, basemap Topo → Trail with no recording at all.
  - **Trials without a crash:** 5 Night Maps toggles with no recording, and 5 basemap switches while recording but not returning. Counted across the session, 4 of 17 style changes crashed, so it is intermittent. It is **not** tied to returning, which I first thought and then disproved.
  - **Where the code is:** `MapForecastCells.kt` is the same at `2c28b71be` on origin/main, pre-main and dev, and no part 2 file is in the stack.
  - **Each crash ended the recording in progress.** Recordings 4 and 5 ended this way, and the walker would lose the way home.
  - **Could not determine:** whether a main build crashes the same way. Installing main's lower build would be a downgrade.
  - The stack trace is in `crash-0147.txt`.
- **F2. Each stray re-routes, so step 4 cannot happen.**
  - After a re-route, the new line starts at the walker's own last track point. The next fix is within 45 m of it, and `judgeStray` re-arms (`WayHomeWatch.kt`, `metersFromLine < WAY_HOME_STRAY_REARM_METERS → armed = true`).
  - So a walker who keeps going the wrong way gets a fresh re-route each time they are 50 m past the newest line: twice in 60 m here.
  - Re-routing ends only on a fallback. "This stray has already re-routed once" appeared only after the real-fix jump (01:12:34), which is discarded.
  - The ruling says "it re-routes once" (Amendment 1, 10.4.4). Whether that means once per stray from the line first shown, or once per stray from whichever line is shown, is the owner's to say. I changed nothing.
- **F3. The span O-396-10 asks about was 0 frames here** (3.3), because a warm re-route took 0.6 to 0.7 s, under one 5 s tick. With a cold search it could be seconds.

## 5. Timing (my report, section 6)
The timing receiver ran `FindWayHomeUseCase` on saved tracks. The two tracks already on the phone (595 and 822 points) are the owner's walks, and were read only. Every engine call is listed in `timing/log-*.txt`.

| Track | Method | Engine calls | Per call (first / min / median / max, ms) | Total | Result |
|---|---|---|---|---|---|
| 595 pts (loop) | SNAP, cold | 3 snaps + 1 route | snaps 147.2 / 27.1 / 52.4 / 147.2; route 21.1 | 357.8 ms | TrackOnly(NO_ROUTE_FOUND) |
| 595 pts | SNAP, warm | 4 | snaps 17.8 / 11.4 / 17.8 / 21.1; route 10.9 | 146.3 ms | same |
| 595 pts | FULL_ROUTE | 4 | snaps 15.2 / 10.9 / 15.2 / 16.6; route 10.1 | 148.7 ms | same |
| 822 pts (loop) | SNAP | 3 | snaps 11.5 / 11.5 / 13.8 / 13.8; route 19.5 | 171.4 ms | AlongTrails, 214 m |
| 822 pts | FULL_ROUTE | 3 | snaps 18.3 / 18.3 / 19.3 / 19.3; route 19.0 | 179.7 ms | AlongTrails, 214 m |
| rec 2 (180 pts) | SNAP | 9 | snaps 28.2 / 20.9 / 25.3 / 29.0; route 21.0 | 329.1 ms | TrackOnly(NO_ROUTE_FOUND) (it ends at its start) |
| rec 2 | FULL_ROUTE | 9 | snaps 21.0 / 16.9 / 21.3 / 31.8; route 15.6 | 295.0 ms | same |
| rec 3 (46 pts) | SNAP / FULL_ROUTE | 7 / 7 | snaps median 15.7 / 13.4; routes 16.3 / 11.7 | 225.4 / 190.4 ms | AlongTrails 63 + 146 + 77 = 286 m, both |
| rec 4 (44 pts) | SNAP / FULL_ROUTE | 9 / 9 | snaps median 12.1 / 13.6; routes 22.0 / 12.2 | 225.0 / 266.6 ms | AlongTrails 63 + 177 + 62 = 303 m, both |
| rec 6 (30 pts, no area) | both | 0 | none | 85 / 94 ms | TrackOnly(NO_TRAIL_ROUTES) |

- **Memory:** `dumpsys meminfo` before and after each run is in `timing/mem-*.txt`. The receiver's Java heap deltas were −11 to +14 MB, and native −2.2 to +0.2 MB, both noisy because of GC.
- **Cancel:** cancels at 30 to 110 ms ended the job 0.5 to 1.7 ms later. Only the 110 ms cancel landed after an engine call had begun. None landed mid-call on a cold open.
- **On the phone itself:**
  - Return → swap: 0.7 s on recording 1 and 0.5 s on recording 3.
  - Re-routes: 0.7 s and 0.6 s.
- **Not available: the long walk with no trail near it (L-389-5).** Both of the owner's long walks are loops near trails. So the stress case was not timed. Recording 1's track is also not a valid timing subject: its end carries the real-fix jump (an AlongTrails with a 62 km track part), and it is not cited above.
- **Battery:** not determined.
- **By my report's rule,** neither method is clearly cheaper, so SNAP stays. That is the planner's call.

## 6. Test location providers, and the phone left as found
- **The providers were removed 5 times without a command of mine.** `dumpsys location` history, in UTC: 01:12:10.942, 01:16:17.847, 01:23:24.249, 02:09:43.864, 02:13:27.268.
- **Each removal followed, within 2 to 10 ms, a process being killed for low memory:** bixby.wakeup at 01:12:10.932, pid 21584 at 01:16:17.845, sohservice at 01:23:24.246, and euicc at 02:09:43.861 and 02:13:27.265. That correlation held 5 of 5. The cause is inferred, not determined.
- **The first removal let real fixes through.** At 01:12:10 the screen had locked. My feeder read only stdout and missed it, so real fixes reached the app for about 2 minutes, which is why recording 1 is discarded after that point.
- **After that, the feeder caught every removal.** It reads stderr too, and checks `dumpsys` every 10 s. It re-added the providers within 0.3 to 0.9 s each time, and logged it (`feeder.log`).
- **The 02:14:51 removal was mine,** at cleanup.

**The phone, as left:**
- **Build:** 1.0.3948+gadfcf75f, the timing build, same schema (22).
- **Database,** read from a copy after cleanup: 2 tracks, 1,419 track points, 5 waypoints, 2 offline regions, `user_version` 22. That is the same as before the check. I deleted my six test tracks and their nine Start/End waypoints through the app, and deleted both local database copies afterwards.
- **Area 1 and Region 2:** route folders untouched, with dates 10-23 and 10-24 unchanged.
- **Settings:**
  - Night Maps unticked, and basemap back to Trail. Every Settings checkbox matches its state before the check.
  - Rotation back to auto (`accelerometer_rotation=1`, `user_rotation=0`).
  - `stay_on_while_plugged_in` back to 0. I had set it to 7 after the first removal, so the screen would not lock mid-walk.
  - Test providers removed, and the shell's mock-location appop back to default.
- **I opened another app by mistake.** After crash 1 and again after a Home press, my next tap landed on the launcher or another app's screen. I took no action in either app, and deleted the screenshots.

## 7. What was verified and how
- **CI:** for every run I read the head SHA, branch and event from the API; the test job's whole log for compile tasks and `e:` lines; and the `unit-test-report` JUnit XML, counted by a parser (`~/.cache/forager-wt/hazel-60m-scratch/readrun.sh`).
- **The forward change:** checked by SHA-1 against a copy saved before the revert, and by reading `:168` on both remote heads.
- **On the phone:** every observation above is from screenshots, frame-by-frame video, the HUD's text dumps or logcat. The times come from logcat, `dumpsys` and SurfaceFlinger's virtual-display events. The video-to-clock mapping is about ±0.3 s.

## 8. What could not be determined
- Whether main crashes the same way (F1). Its cause beyond the stack.
- What removes the test providers, beyond the low-memory-kill correlation.
- How long the track shows during a re-route after a cold `.rd5` open. Only warm re-routes happened.
- The ring at the start.
- Sundown's start-back figure with the screen off (it was after sunset).
- Topo and OSM at night (crash).
- Steps 4 (as written), 5, and three of step 11's notes.
- The long-walk timing (L-389-5).
- Whether the dotted run is uncased under the recording line.
- The cause of the one-frame blank frames.
- **The 60 m figure on the phone.** This build has the 50 m reach. It needs a build with `ac6f6826f` (-1621).

## 9. Decided beyond scope
- **I used drags instead of long-presses in step 10,** because no long-press consumer exists.
- **I changed device settings temporarily and restored them all:** `stay_on_while_plugged_in`, rotation, Night Maps and the basemap.
- **I ran extra trials to separate F1's conditions.** They ended recordings 4 and 5.
- **I fetched public trail geometry once,** to build paths that follow a real trail.
