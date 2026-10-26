# Diagnosis of the #404 S22 "failure": I misread the map, and #404 works on the phone (Hazel, 2026-10-25 Pacific)

- **Asked by:** Rowan, relaying the owner's "Diagnose now, step 6 waits" (RECORD -1650).
- **This supersedes** the verdict of `2026-10-25-10-s22-check-report.md` (same branch, `7ac84c9dd`). That report stays as written. Its "not met" is wrong, for the reason below. Its measurements stand, but they measured the wrong line.
- **Phone:** S22 (SM-S908U), R5CT321008R. Times are UTC, 2026-10-26.

## The cause

**The line I measured was the recording line, not the way home.** The map draws two dotted lines along the walker's track while they return:

| Line | How it looks | Where its points come from | Where it ends |
|---|---|---|---|
| **The recording line** (breadcrumbs) | Purple dots with a white casing (`MapPalette.breadcrumb` `0xFF650BB1`, `ui/theme/MapPalette.kt:95`; the casing at `ui/map/OverlayLineSpecs.kt:86-101`) | The stored track, re-read by the poll (`ui/track/TrackRecordingPoller.kt:76`, `breadcrumbPoints = track?.points`) | **The last written point** |
| **The way home's track part** | Azure round dots with no casing (`ROUTE_AHEAD_COLOUR_DAY` `0xFF0288D1` and `AHEAD_DOTS`, `ui/map/RouteHomeLayers.kt:82-83`, `:104`), over the grey line at Return (`:86-87`, 60% opacity, `:79-80`) | `WayHomeShown.line`, from `WayHomeWatch`, drawn from the walker's place (`ui/track/TrackRecordingRouteTicks.kt:105-116`; `domain/wayhome/WayHomeAhead.kt:45-46`) | **The newest kept point**, with #404 |

In the first check I took the purple line for the way home, because it was the line with the casing. The short stretch of azure dots on grey, between where the purple ends and the dot, is the way home's own start. I had taken it for the recording's newest points. So the gaps I measured, 12.1 to 13.2 m and 31.4 to 31.7 m, were the recording line ending at the last written point. That is exactly what it does. All file and line references above are on `way-home-fresh-start` at `b50afeb73`.

## The evidence

**A diagnostic build.**
- **Branch:** `way-home-fresh-start-diag` at `8895f80f9`. Never to merge. It is #404's head `b50afeb73`, plus log lines (`f6c14f188`), merged with pre-main `a41761c8f` so that the build installs without a version downgrade.
- **Run:** CI 40268903898, green: the two new test classes, the release compile and lint.
- **APK:** 1.0.3987+g8895f80f (sha256 `88935eae…9171c`). Schema 22 both sides, and the header read 22 after install.
- **What the log lines say,** in words and metres only: the held, stored and appended counts at each search; how far the line's start lies from the newest point used and from the last stored point; and, from the screen's route tick, the place index and how far the drawn start lies from the line's first node.

**The same walk as the first check.** Recording on test fixes along Hares Canyon Trail, then a straight leg off the trail. A database poller timed the writes, every 30 s. The walker was held on a kept point at each Return.

| Case | Time | Stored / held / appended | Line start to the newest point used | Line start to the last stored point | Drawn start to the line's first node; walker to it |
|---|---|---|---|---|---|
| A: Return 12 s after a write | 04:37:19 | 60 / 62 / **2** | **0.0 m** | 12.4 m | 0.0 m; 0.0 m |
| B: Return 25 s after a write, 5 s before the next | 04:38:37 | 70 / 13 / **3** | **0.0 m** | 18.8 m | 0.0 m; 1.3 m |
| C: the re-route, at the stray's 50 m crossing 25 s after a write | 04:43:07 | 75 / 10 / **5** | **0.0 m** | **35.1 m** | 0.0 m; 2.8 m |

- **The fix works on the phone in all three cases.** The search used the unwritten points, and the line started at the newest of them.
- **What the walker sees:** without them, the line would have started 12.4, 18.8 and 35.1 m back.
- **B's walker had reached the end of the leg** 10 s before the hold. So the newest kept point was 04:38:25, not the predicted 04:38:35, and 3 points were unwritten rather than 5.
- **C's stray** ran straight out from the line's start, at a right angle to the dotted leg, at 1.4 m/s.
  - The re-route fired on the predicted fix (04:43:07). The service logged "over the far band" at the same moment (`WayHomeWatch` W line, the only one in the run).
  - The newest kept point was 04:43:05. The walker went on 4 fixes past the crossing before holding, so the dot then sat about 8.4 m past the line's start, as the walk predicts.

**The screen matches the log, in both runs.** On the four Return screenshots (A and B of both runs), the nearest way-home azure pixel to the dot's centre is 75 to 79 px from it. That is the edge of the dot's halo, which the measurement leaves out (75 px). The nearest purple pixel is 182 to 476 px away. So the first run's build (1.0.3969+g433e1937, #404's merge commit) drew the way home to the dot as well. I misread it; it did not fail.

**Counts from the phone's log** (pid 31841, one process, logcat from a cleared buffer):
- 83 `WayHomeDiag` lines: 73 kept points, 3 searches, 3 lines shown, 4 ticks;
- 1 `WayHomeWatch` warning (the far band, at the re-route);
- 0 `FindWayHome` lines, 0 "Couldn't persist", 0 `FATAL EXCEPTION`.

**The sample:** one recording, two Returns and one re-route, with the walker held on a kept point each time except C, where the walker walked on 4 fixes. One phone.

**The first C attempt, 04:41:01 to 04:42:31, is void.** I left B's hold file in place, so the walker never moved (`fixes.csv` shows index 0 throughout). It was re-run from 04:42:31.

**Three test-provider removals** came during walking: 04:32:52, 04:37:43 and 04:38:45. None came at a Return or at the re-route. The feeder re-added the providers each time. The cause was not found.

## What this means

**#404 does what the dispatch asks on the phone,** at Return just after a write, just before one, and at a re-route. No code fix is needed. I propose none, and none is built.

**My first report's error is mine.** The check measured a figure carefully and never confirmed which line carried it. This is CLAUDE.md's "a correct count is not a correct reading". The pixel distances were right, and the line they belonged to was not the one the claim was about. What caught it was a second, independent reading, the log, that disagreed with the screen.

## An observation for the owner, not a finding against #404

**The recording line trails the walker by up to 30 s** (up to about 42 m at 1.4 m/s). It shows only written points (`TrackRecordingPoller.kt:76`). The rest of the way to the dot is drawn by nothing while the walker is not returning. During a return, the way home's azure dots cover that stretch.
- This predates #404. It is the same write timing (`TrackRecordingService.kt:549-550`) that #404 works around for the way home.
- Whether the recording line should also reach the dot is the owner's call, and it is not in any dispatch. If wanted, the options are:
  - (a) leave it;
  - (b) draw the recording line from the stored points plus the kept points not yet written, as the way home now does;
  - (c) draw a plain tail from the last written point to the dot.
- **Not verified:** what a walker who is not returning sees at the line's end. I looked only during returns.

## The phone, as left

- **Build:** the diagnostic build 1.0.3987+g8895f80f is left installed. Its only difference from #404 plus pre-main `a41761c8f` is the `System.out` log lines. The next pre-main build installs over it without a downgrade, since its versionCode will be higher.
- **Database:** 2 tracks (1,419 points), 5 waypoints, Regions 1 and 2, schema 22. The same as before.
- **Deleted through the app:** the test track (Oct 25, 9:32 PM, 81 points) and its Start and End waypoints, by swipe.
- **Settings:**
  - all eight preference files are byte-identical to the copies saved before;
  - `stay_on_while_plugged_in` is 0;
  - the test providers are removed, and the mock-location appop is back to default.
- **Not touched:** the owner's tracks, Waypoint 1, Area 1 and Region 2.
- **Kept off GitHub,** in `/mnt/work/hazel-scratch/way-home-fresh-start-diag-s22/`: screenshots, logcat, `fixes.csv`, `writes.log`, `events.log`, database copies and scripts.
