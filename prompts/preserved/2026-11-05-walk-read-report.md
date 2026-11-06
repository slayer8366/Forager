# The S22 walk of 2026-11-05: the re-search burst and the way home's off-trail check, read only (Hazel Cairnwright)

Preserved by the planner from the hand-back message, verbatim. It holds no coordinates, MGRS, tile numbers, street names, house numbers or place names. The data it reads stays in the session's local folder.

**Report, Hazel Cairnwright to Rowan: today's S22 walk, Questions A and B (read only)**

I built nothing, committed nothing, pushed nothing and touched no phone. Code was read with `git show` and `git grep` at 4670712fa. Every file cited below is byte-identical at origin/pre-main a0535ece1: I checked with `git diff --stat` and all eight came back empty. I did not read the Restricted Object. This report contains no coordinates, MGRS, tile numbers, street names, house numbers or place names.

## 1. In short

- **A. The phone was moving at vehicle speed during the re-search burst, not walking.** [observed: GPS Doppler speed]
  - Speed was 11 to 14 m/s from 17:41:40 to 17:42:22.
  - Between successive search starts the phone covered 51 to 77 m, with every interval just over the 50 m stray figure.
  - Each re-route is a plain stray verdict, more than 50 m from the line shown. Each new line starts at the fix its search began from, so it re-arms the stray rule on its first fix. A walker going steadily away therefore gets one search per 50 m away from each new line: about 4 s at 13 m/s, about 38 s on foot. [read and inferred]
  - Nothing in the end chooser caused it.
  - Confidence: high on the timing, and on the motion being vehicle-like. Not verified that it was a car, because no positions exist for that period.
- **B. The 17:49:49 navigation was Return.**
  - Code: the far-band line can only be logged during a return with a trail line, and a waypoint navigation always logs a way-to search line. None appears after 17:43:24.
  - Frames: the track's Start marker, which is drawn only as a navigation target, is visible at the line's end. The arrival ring at 18:04 sits on it, not on Waypoint 1.
  - Waypoint 1 is 8 m from the Start, because the car was parked beside it. That is almost certainly what read as "navigating to Waypoint 1". [inferred]
- **R5, all three excursions: the dim did not come before the far-band line.** No dim, no off words, no "… straight" and no "Back on trail" were seen at all. The new line was always drawn within about 1 to 2 s.
  - E1 and E2 distances are reconstructed. Each stand-in line reproduces its logged far-band fix to within one fix.
  - The rule applied to those reconstructed distances agrees with the screen.
  - E3's distance could not be reconstructed: my stand-in failed its own check.

## 2. Question A: the re-search burst

**Counts** [observed, all.log]
- 11 "The way to the waypoint's ends" lines: one at Navigate (17:39:15) and **10 re-searches** from 17:41:07 to 17:43:24, not 9.
- 8 SEARCHING lines.
- 0 ALREADY_REROUTED lines and 0 straight-line results in the whole log.
- 8 searches ran from 17:41:48 to 17:42:24, 4 to 6 s apart.

**What starts each search** [read]
- `AvailabilityWaypointRoute.kt:173-178`: `stray.judge(shown.metersFrom(here), …)` gives REROUTE, and that calls `startSearch`. The REROUTE verdict itself is never logged.
- The SEARCHING line (`:179-183`) is logged on the *next* fix that arrives while the search is still running and is still more than 50 m from the old line.
  - The logged stack shows it firing from the location callback.
  - So SEARCHING is a consequence of a re-route already under way, not its trigger.
- 2 of the 10 re-searches (ends at 17:42:19 and 17:42:25) have no SEARCHING line. Their searches finished in 0.86 s and 1.01 s, before the next fix arrived. [observed]
- Only GPS fixes reach this code: `acceptLiveFix` refuses network fixes (`LiveFixGate.kt:81-84`, `AvailabilityLiveLocation.kt:136`). So each re-route fix is the GPS fix just before its SEARCHING line or its result. [inferred; searches took 0.86 to 1.68 s]

**Why the phone read as more than 50 m from the line**
- Speeds came from the `speed` field of `ForagerFix`, and were integrated between search-start fixes (300 GPS fixes from 17:38:50 to 17:44:00). [observed]

| Search started | Since the previous search began | Speed |
|---|---|---|
| 17:41:52 | 4 s, 51.3 m | 12.8 m/s |
| 17:41:58 | 6 s, 76.9 m | |
| 17:42:03 | 5 s, 60.8 m | |
| 17:42:09 | 6 s, 59.1 m | |
| 17:42:14 | 5 s, 62.0 m | |
| 17:42:18 | 4 s, 55.2 m | |
| 17:42:24 | 6 s, 57.4 m | |

- The phone went from 0 to 14.6 m/s in about 10 s at 17:40:00. Peak speed was 16.1 m/s, and about 1.9 km was covered from 17:39:13 to 17:44:00.
  - Walking pace of 1.2 to 1.5 m/s begins at 17:43:53, just before the recording started.
  - Before the burst, 780 m was covered with no re-route (17:40:08 to 17:41:06), and 564 m after it (17:42:24 to 17:43:23). That fits driving along the line for those stretches and away from it during the burst. [inferred]
- `metersFrom` (`WayHomeProgress.kt:86`) is the nearest node of the **whole line**: the dashed lead-in, the trail and the final leg. All three parts are filled in at 10 m or less (`:41-81`).
- **The lead-in always starts at the search's own start fix**, whatever the end type (`FindWayToWaypointUseCase.kt:113-123`). That holds for a pick-up, for "not a street end" and for "by trail".
  - A refused end gives a straight line, `line = null`, and no stray judging (`AvailabilityWaypointRoute.kt:258-262`).
  - So no end type produces a line the walker is already far from.
- **Arming:** `WayToStray` is created once per navigation and is not reset on a new line (`:152`, `:242-256`). After a REROUTE, `armed` is false until a fix comes within 45 m of the line shown.
  - The first fix judged against each new line was 3 to 28 m along the path from that line's start fix, so it re-armed at once. [inferred]
  - In practice the rule means "one re-route per line", not "one per stray".

**Confidence:** high that the burst is the stray rule working as written at vehicle speed. [not verified] the direction of travel relative to each line, and that it was a car.

**What would settle it**
- The owner confirming they drove to the walk.
- Or, next time, positions: start a recording before Navigate so the walk logger runs, or add a debug line giving `metersFrom` at each REROUTE.

**Test that would reproduce it (not written)**
- Drive it through `AvailabilityViewModel`: `onNavigateToWaypoint`, with a fake LocationTracker giving GPS fixes 1 s and 13 m apart, heading east.
- Use a fake `findWayToWaypoint` that records each call and returns a line from `from` going west.
- Assert a call on the first fix and then on every 4th fix, with start points about 52 m apart.
- Controls:
  - 1.3 m/s should give one call per about 39 fixes.
  - A line running along the direction of travel should give one call only.
  - A search that suspends past the next fix should log exactly one SEARCHING line per re-route; one that finishes first should log none.
- Cancel the navigation in a `finally`, because `follow`'s freshness loop delays without end, the same risk as CLAUDE.md's stalled-run rule.

Whether navigation should react to vehicle speed at all is a decision for you and the owner. I propose nothing.

**A side note for Part 1** [inferred]: 3 of the 4 pick-ups -2172 counts were chosen from the moving vehicle on the road: 17:41:07 at 3.2 m/s, 17:41:53 at 12.8 m/s and 17:43:24 at 1.7 m/s. Only 17:39:15 was at a standstill. The chooser behaved as coded, but that is not the walker-by-a-house case the check was meant for.

## 3. Question B

**Was it Return? Yes.**
- **From the code** [read]:
  - `WayHomeWatch.onFix` returns early unless `trackId` is set, a trail line is shown and `isReturning` is true (`WayHomeWatch.kt:151-154`).
  - `trackId` is set only by `find`, whose one caller is `startReturn` (`TrackRecordingViewModel.kt:744`).
  - A waypoint navigation pauses the return (`AvailabilityViewModelWaypointNavigation.kt:36-41` calls `stopReturn`, which calls `wayHomeWatch.stop`).
  - A waypoint navigation's first fix always starts a search that logs an ends line, a straight-line line or a failure (`AvailabilityWaypointRoute.kt:166-168`; `FindWayToWaypointUseCase.kt:105`, `:169`).
- **From the log** [observed]:
  - No way-to line after 17:43:24.
  - One navigation, 17:49:49.565 to 18:04:11.376, with navigating=true throughout.
  - No gap of more than 2.5 s in the GPS `ForagerFix` lines from 17:44 to 18:05.
  - The way-to waypoint navigation actually ended at **17:44:39.447** ("leaving navigation"), not 17:47:02.
- **What a Return start shows and logs:**
  - It logs nothing on success. The navigation-view lines are the same as for a waypoint navigation.
  - The card has no target name (`NavigationInfo.kt`), so the two cards really do look alike.
  - The map differs: the Start (ORIGIN) marker is drawn only as the target (`AvailabilityPureFunctions.kt:69-70`), and the arrival ring is drawn on the target (`CompactMapArrivedAt.kt:15-22`).
- **From the frames and the screenshot** [observed]:
  - 17:50:05 screenshot: two pins at the line's end. The only waypoints within 50 m of Waypoint 1 in the database are the Start (8 m) and the End (6 m, created 18:04:14).
  - The Start marker with its label is visible at 18:00:25 to 18:00:45, 18:02:04 and 18:02:19.
  - The arrival ring at about 18:04:04 is drawn about 8 m from Waypoint 1's pin, which stays visible.

**Video timing** [observed]
- Video start is 17:50:34.0 to 17:50:34.2. I matched two events to the log: the card collapsing against "leaving navigation" at 18:04:11.376, and Re-center disappearing against a re-follow at 17:55:36.458.
- I used start = 17:50:34.1.

**Method for the readings**
- Positions: the walk logger's FIX lines, 1155 GPS and all used. The stored track has 171 points.
- Mapped ways: the two zoom-15 tiles under the walk. Neither is in Region 2's map copy and one is in Region 1's. Both are in MapLibre's cache, `mbgl-offline.db`. I decoded the cached copies with my own MVT decoder; the Region 1 tile's road kinds match the cache.
- Line stand-in: the shortest path along mapped ways from the fix at each route time to the Start.
- Check: the first fix more than 50 m from the stand-in must be the logged far-band fix. That check can fail, and did for E3.
- Dim: pixels of the solid line were sampled.
  - Undimmed reads as median (4 to 13, 135 to 138, 208 to 213), against the code's #0288D1 (2, 136, 209).
  - A 60% dim over the map's grey (about 180, 180, 172) would read with red around 73 to 91.
  - There is no dimmed frame in the recording, so this control is computed, not measured.
- Card: frames every 10 s across the whole recording (81 of 82 readable; 1 black), plus frames every 1.5 to 4 s in each window.
  - No status words appear anywhere except "Approaching", from 18:01:15.
  - "Approaching" takes precedence over the off words and "straight" (`ui/availability/NavigationReadout.kt:173-190`), so in E3 only the line's pixels can show a dim.

**Readings** (R1 log, R2 accuracy, R3 distance, R4 screen, R5 order)

| | Far-band (log) | New line on screen | Accuracy | Reconstructed distance | Dim before the far-band? |
|---|---|---|---|---|---|
| E1 | 17:53:26.342 | by 17:53:28.1 | 3.79 m (225/225 GPS `ForagerFix` lines in the window) | Stand-in crosses 50 m at 17:53:26, matching the log. Over 43.8 m for 6 fixes (5 s) before the far-band. Earlier stops: 13 s at 42 to 43 m, 16 s at 28 m. | **No**, and none at all: no words, and the new line was at full strength. |
| E2 | 17:56:31.294 | by 17:56:33.1 | 3.79 m (225/225) | Stand-in reads 49.5 m at the far-band fix and crosses 50 m one fix later. Over 43.8 m for 4 fixes at 17:55:19-22, then 8 fixes (7 s) before the far-band. Stops: 18 m 24 s, 36 m 18 s, 35 m 16 s, 39 m 30 s. | **No**, and none at all. |
| E3 | 18:02:32.300 | by 18:02:33.1 | 3.79 m (225/225) | **Not determined.** My stand-in (walked track plus mapped path) reads about 41 m at the far-band fix against the app's more than 50 m, so I discarded it. | **No.** The old line was full strength at 18:02:30.1, and the new line full strength at 18:02:35-41. An off state set earlier would have carried across the re-route (`WayHomeWatch.kt:228-230`) and shown dimmed. |

- For all three: no "not re-routed" or fallback lines, so each far-band fix started a re-route that succeeded along trails.
- W4 check: what the screen showed matches the rule applied to the reconstructed fixes, for E1 and E2.
  - The walker never stood inside the band of 43.8 to 50 m. The 42.3 m stop in E1 is 1.5 m inside the limit, and the stand-in was accurate to about a metre at its check point.
  - So the dim-first case was **not exercised**. In all three, the re-route came first, as section 1 of the plan predicted.
  - Return read "by trail" throughout, never "following your track".

## 4. Premises that were wrong

1. "Searched again 9 times": it was 10.
2. "Each followed SEARCHING": 8 of 10 did. The line is a consequence of a re-route, not its cause.
3. "While navigating to a waypoint … walking": the phone was moving at 11 to 16 m/s. [inferred: a vehicle]
4. -2172, Part 1 as a walker reading: 3 of the 4 pick-ups were judged from the moving vehicle.
5. -2172, "the way home the sundown countdown keeps during a recording": `WayHomeWatch` only works during a return.
6. -2172, "frames show the navigation to Waypoint 1, not Return": wrong. Waypoint 1 lies 8 m from the Return's Start.
7. -2173, "the first navigation ended by 17:47:02": it ended at 17:44:39. The 17:47:02 line is a map pan with no navigation running.

## 5. Could not determine

- Positions from 17:39 to 17:44, and so the direction of travel relative to each way-to line. Also whether the vehicle was a car.
- The engine's actual way-home lines, which are not recorded. The E1 and E2 stand-ins are each checked at one fix only. E3's distance is not determined.
- Whether a dim appeared after the E3 re-route: its trail part was mostly off-screen and the words were hidden by "Approaching". By the rule, none would have.
- The 45 s between Return and the start of the recording.
- The off-track alert's timing: not looked at.
- A measured positive control for the dim, since no dimmed frame exists.

## 6. Clean-up

`/mnt/work/scratch/walk-read` is deleted: scripts, frames, plots, everything. I made no worktree. The planner's worktree `git status` is empty. I did not use stash or Gradle.
