# The walk-back time on the 10-24 walk: long or short, and why two findings disagree

Researcher: Juniper Sounding (Fable). For RECORD -1696 (the owner: "Investigate (Recommended)"): reconcile two findings about the sundown walk-back time on the owner's 2026-10-24 outing, read-only, with options.

- Finding 1 (mine, `docs/navigation/2026-10-26-hilly-walk-climbs.md`, branch s26-hilly-walk-report, "Out-and-back test"): the figure ran **+44 % and +19 % long** against the second half's actual moving time on recordings A and B.
- Finding 2 (Sorrel's #421 review, `prompts/preserved/2026-10-26-sorrel-421-review.md`, branch sorrel-421, N5): on A the self-joined route home at the far point is **459 m where the walker walked 1,121 m back; 8 min 45 s pace-only against 14 min 51 s actual moving**, so the figure read short.

Read-only on code, at pre-main `cfef2f4cb` (#421 merged). No device, no Gradle. The owner's walk copy is read with `python3 -I` from `/mnt/work/scratch/s26-hilly-walk/work/forager.db` (`mode=ro`); raw data, coordinates and times of day stay on the laptop. This report carries figures only: metres, seconds, counts, ratios, and minutes since each recording's start. Scripts in `evidence/2026-10-26-walk-back-reconcile/` read from the scratch path and reproduce locally.

Each claim is marked **read** (file and line), **computed** (a script in the evidence folder, output beside it) or **inferred**.

## 1. Method, committed before any number was computed

### What the app computes on a tick (read)

- `SundownWatch.tick` (`domain/SundownWatch.kt:239-351`) reads the track through the read seam, its origin waypoint, and the newest GPS fix; before Return (or after Return with the track as the way home) the estimate is `returnWalkingTime(track, origin, current, freshness, band)` (`:303`); `walkBack` is `About` or `AtLeast` by `estimate.isAtLeast` (`:305`); the line's "start back by" is `sundownLeaveByAt(turnaround, walkBack)` = sunset − margin − walk back (`SundownLine.kt:66-67`), the heads-up 30 min before that (`DecideSundownAlertUseCase.kt:75`, `SUNDOWN_HEADS_UP_LEAD_MILLIS`). The default margin is 60 min (`SundownPreferencesRepository.kt:62`).
- `returnWalkingTime` → `pathHome` → `estimateAlong` (`ReturnWalkingTime.kt:62-73`, `:91-117`): `walkingMillis = path.totalMeters / pace.speedMetersPerSecond + climbMillis` (`:113`).
- `path.totalMeters = trackMeters + hopCountedMeters + originLegMeters` (`PathHome.kt:175`); `trackMeters` is the shortest route from the most recent stored point to the first through the track joined to itself wherever two non-consecutive points are within ε = 10 m (`PathHome.kt:110`, `:117`; `TrackSelfJoin.kt:32`, `:78-154`, Dijkstra from the last point to the first). The hop counts only above 25 m (`:179-182`); the origin leg is first stored point to origin waypoint (`:114`).
- `pace` is `movingPace(track.points)` (`MovingPace.kt:101-150`): over intervals between consecutive kept points whose implied speed is ≥ 0.5 m/s, the duration-weighted Doppler speed once those carrying a Doppler sample total 5 min, else the differenced speed once all moving intervals do, else 2 mph = 0.889 m/s (`:188-206`, `:255`, `:278`, `:290`). Under 15 min of governing moving time the estimate is labelled "at least" (`ReturnWalkingTime.kt:98`, `MovingPace.kt:299`).
- `climbMillis` (#421) is 2.2 s per metre climbed and per metre descended along the same route (`ClimbAllowance.kt:46`, `:60-63`, `:79-82`), from `barometricHeights` over the whole track so far with #420's bridging of pressure-less points (`BarometricHeight.kt:48-54`, `:117-130`), banked through the 4 m hysteresis (`:91-111`). The hop and origin leg are flat.
- The copy's two recordings both carry an origin waypoint (`tracks.originWaypointId` non-null, read from the copy's schema; the waypoint's position is read from `waypoints`). Whether the owner tapped Return on this outing cannot be told from the copy (Return's state is in memory, `SundownWatch.kt:92-96`); the analysis takes the track's route home, which is also the path after Return when the way home is the walker's track.

### Why the two findings can both be right (inferred, to be tested)

Finding 1 divided the *path the walker actually walked back* (1,121 m on A) by the first half's pace and compared it with the actual moving time. Finding 2 divided the *app's path*, the self-joined route home (459 m on A), by the pace so far. They measure different paths. If that reading is right, the app's pace-only figure against the actual moving time is exactly the product of two ratios, and the whole disagreement is in the first:

```
app ÷ actual moving  =  (route home ÷ path walked back)  ×  (path walked back ÷ pace) ÷ actual moving time
                        [effect 1: which path]              [effect 2: Finding 1's pace/path-length lean, +19 to +44 %]
```

Effect 2 splits further into the metres walked inside intervals slower than the floor (path walked back ÷ moving metres back) and the pace change (actual return moving speed ÷ pace so far). Effect 1 splits into the self-join (route home ÷ plain retrace of the track so far) and the walker's own way back (plain retrace ÷ path walked back). Each is a ratio of two figures, so the split is exact and checked numerically at every sample point.

### Sampling (`reconcile.py`)

Both 2026-10-24 recordings, A (`9685146a…`, 457 kept points) and B (`cad94737…`, 677), kept points only (the read seam's whole-second rule; 0 and 1 excluded), in timestamp order. The ports are `trackmath.py` from the earlier report (`haversine`, `movingPace`, `barometricHeights`, hysteresis banking), with `bridgedAcrossGaps` added from `BarometricHeight.kt:117-130`, and a new port of `joinedTrackRoute` (`TrackSelfJoin.kt:78-154`: every non-consecutive pair within ε is a join, tested with the haversine; Dijkstra from the last point to the first; the grid is only a speed-up in the Kotlin and is not ported, so every pair is tested).

**Positive controls, run first.** The ports must reproduce figures computed independently by Sorrel's own ports (`recompute.out`): whole-recording bridged climb/descent 52.8/43.1 m (A) and 72.4/68.7 m (B); at the far points, route home 459 m with 35 joins (A) and 1,956 m with 49 joins (B), pace 0.874 and 0.863 m/s, banked climb along the route 21.9 m and 112.5 m, allowance 48 s and 247 s. And Finding 1's own: second half path 1,121 m and 1,534 m, actual moving 891 s and 1,489 s. A port that misses any of these by more than 1 % is wrong and is fixed before anything else is read.

**At every kept point i** (the tick is 15 s and a point is kept every 5 s or more, so every point is a tick the app could have made): the track so far is points 0..i; the current fix is point i itself, so the hop is 0 m and omitted (stated limitation: on the phone the hop at a tick is the few metres since the last kept point, under the 25 m band). Computed: route home (metres, joins on route), plain retrace (sum of legs 0..i), straight line from point i to the origin waypoint, origin leg, moving pace so far (speed, source, governing moving time, so the "at least" label), the pace-only walk back `(route + origin leg) ÷ pace`, elapsed and moving time so far and their ratio (the stop ratio so far).

**At every 5 minutes of elapsed recording time and at the far point** (the latest kept point at or before each mark): also the barometric heights over the track so far, bridged, banked along the route: climb, descent, and the #421 allowance; the walk back with allowance.

**The actual return from point i** to the recording's last kept point (where each return ended): elapsed time, moving time (intervals ≥ 0.5 m/s), path walked (sum of legs i..end), moving metres, and the actual moving speed over it.

**The homeward phase** is from the far point (the kept point farthest, straight-line, from the first kept point: the split the earlier report used, so the two findings line up) to the end. Before it the walker is still going out, so "time to get back from here" is not what the figure estimates; the figures are computed everywhere and compared only there, with every sample shown so the reader can see where the phase plausibly starts.

### What is reported

1. The positive controls, pass or fail, with figures.
2. Per recording, a table at every 5 minutes: minutes in, route home, retrace, straight line home, joins on route, pace and source, label, walk back pace-only and with allowance, and (from the far point on) the actual elapsed, moving time and path back, and the ratios.
3. The decomposition at the far point and summarised over the homeward phase (median and range of each ratio): effect 1 (route ÷ walked; split self-join and own way back) and effect 2 (sub-floor metres and pace change), and which dominates where.
4. Against elapsed time: app ÷ actual elapsed, and whether `elapsed back < walk back + 60 min margin` held, i.e. whether a walker starting back at "start back by" and behaving as this one did would have been home by sunset.
5. Predictability from what the app knows at the tick. Candidate predictors, fixed here: C1 route ÷ pace (the app, pace-only); C2 C1 + allowance (#421); C3 plain retrace ÷ pace (the pre-join rule); C4 straight line to origin ÷ pace; and each × the stop ratio so far, for elapsed time. Each is reported as predicted ÷ actual at the far point and as median over the homeward phase. With two recordings of one walker this is a comparison, not a fit; nothing is tuned to the data.
6. Options for the owner, each as a step path with what the line would have read on this walk, the direction of its error, and its cost in code.
7. What one walk cannot tell.

### Predictions, before the data

- P1. The identity holds numerically at every sample point (it is an identity of ratios, so this is a check on the scripts, not on the walk), and the far-point figures reproduce both findings: pace-only 525 s and 2,266 s; actual moving 891 s and 1,489 s; so app ÷ actual moving ≈ 0.59 (A, short) and ≈ 1.52 (B, long).
- P2. Effect 1 dominates on A: route ÷ walked ≈ 0.41, against effect 2 ≈ 1.44. On B both lean long and are of similar size (≈ 1.28 and ≈ 1.19). Within effect 1 the self-join is the larger part on A (route ÷ retrace ≈ 0.31) and small on B (≈ 0.89); within effect 2 the sub-floor metres are the larger part on A (≈ 1.31 against ≈ 1.10) and the two are equal on B (≈ 1.10 each).
- P3. Over the homeward phase the pace-only figure is short of the actual moving time at most sample points on A, and long at most on B. The allowance (about 9 to 11 % at the far points) does not close A's gap at any point in its first half.
- P4. Against actual elapsed time to the end, the figure is short everywhere on both recordings, by a factor of 2.5 or more from the far point; a walker leaving at "start back by" with the 60 min default margin would still have been home before sunset from the far point on both (elapsed back < walk back + 60 min).
- P5. No single candidate is within 15 % of the actual moving time on both recordings at the far point; C3 (retrace) is closer than C1 on A and farther on B. The stop-ratio-scaled candidates land within 30 % of the actual elapsed time on at least one recording and not both.
- P6. The pace source is DEFAULT for roughly the first 10 to 15 minutes of each recording and the label "at least" for its first 30 to 45 minutes (5 and 15 min of moving time at a third-to-half duty), so the label is on for the whole of neither homeward phase. The origin leg is under 30 m on both.
- P7. The hop is 0 by construction; the climb along the route at 5-minute marks before the far point is within 25 % of the far point's own on B, and varies more on A because its stitched route changes shape as joins appear.

A result against a prediction is reported as such, not absorbed.

## 2. Results (computed: `reconcile.py` → `reconcile.out`, `arrival.py` → `arrival.out`, `loops.py` → `loops.out`, `options.py` → `options.out`; figures in `*-reconcile.json`, every kept point in `*-points.csv`)

### Positive controls: all twenty-four pass

Both recordings reproduce every independent figure within 1 %: whole-recording bridged climb/descent 52.76/43.12 m (A) and 72.42/68.72 m (B); far points at indices 264 and 404; route home 458.8 m with 35 joins and 1,955.5 m with 49; pace 0.874 and 0.863 m/s; banked climb along the route 21.9/0.0 and 50.9/61.5 m; allowance 48.2 and 247.4 s; pace-only 524.9 and 2,265.7 s; second-half path 1,121 and 1,534 m; second-half moving time 891 and 1,489 s. The ports are sensitive to what they measure (the earlier report's unbridged figures differ from these by 20 to 30 m of climb), so the agreement is evidence.

### Corrections after seeing the data, recorded openly

Two things in the first table changed what the committed method could conclude. Both are additions; nothing already computed was altered.

1. **Where the return ended.** The method measured "back from here" to each recording's last kept point. On A the walker was within 10 m of the start at 119.8 min and the recording ran to 139.8 min: 20 min 03 s and 450 m of walking (5 min of it moving) around the start after arriving. Both earlier findings measured to the end of the recording, so on A both compared the app's figure with a return that includes that pottering. `arrival.py` adds the return to **first arrival**, the first kept point after the far point within 15 m of the origin waypoint (`ARRIVAL_MIN_RADIUS_METERS`, `NavigationReadout.kt:71`; 30 m as a sensitivity, which moves A's arrival by half a minute). B never came within 30 m of its origin (closest 66 m; it ended 67 m away), so its return is to the end either way.
2. **Why B's route home grew the whole way back.** `arrival.py` adds, for every homeward point, the distance to the nearest outward point, and the route home at ε = 10, 15, 20, 30 and 50 m. `loops.py` runs the nearest-outward check over every recorded walk in the copy longer than 20 min, so the B case has a frequency from seven walks rather than one.

### What the walker did (computed)

| | A | B |
|---|---|---|
| Kept points / duration | 457 / 139.8 min | 677 / 130.6 min |
| Origin leg (first point to origin waypoint) | 0.0 m | 9.2 m |
| Far point: minutes in, straight line to the origin | 88.5 min, 297 m | 91.5 min, 1,174 m |
| Shape of the way back | **A patch, then home over the way out**: 29 % of homeward points within 10 m of an outward point, 76 % within 50 m, median 26 m | **A loop**: 1 % within 10 m, 17 % within 50 m, median 263 m; 75 % more than 100 m from any outward point; by tenths of the way back, median 18, 45, 132, 252, 366, 493, 488, 384, 269, 137 m |
| First arrival (15 m) | 119.8 min; then 20 min 03 s and 450 m more before the recording ended | none; ended 67 m from the origin at 130.6 min |
| Far point → first arrival: elapsed / moving / path | 31 min 17 s / 9 min 37 s / 671 m (447 m of it in moving intervals) | — |
| Far point → end: elapsed / moving / path | 51 min 20 s / 14 min 51 s / 1,121 m | 39 min 08 s / 24 min 49 s / 1,534 m |
| Stop ratio (elapsed ÷ moving): way out, way back | 4.96, 3.25 (to arrival; 3.46 to end) | 2.66, 1.58 |
| Among the copy's 7 long walks with a homeward half (`loops.out`) | one of five that come home over their own track (27 to 100 % within 10 m) | the only loop |

### What the app would have said, and how it compares

Every 5 minutes and at the far point (`*`); "h" marks the homeward phase. Pace-only is the figure before #421; "with" adds #421's allowance (climb + descent along the route, metres). "Actual" is from that point to the recording's end: elapsed, moving, path. Ratios are app ÷ actual.

**A** (origin leg 0 m)

| min in | route m (joins) | retrace m | straight m | pace, source, label | pace-only / with (climb+desc) | actual elapsed, moving, path | pace-only÷moving, with÷moving, pace-only÷elapsed |
|---|---|---|---|---|---|---|---|
| 4.5 | 74 (1) | 82 | 68 | 0.889 default, at least | 1:24 / 1:24 (0+0) | 135:18, 31:38, 2,505 | 0.04, 0.04, 0.01 |
| 24.3 | 176 (12) | 464 | 130 | 0.944 diff., at least | 3:07 / 3:37 (14+0) | 115:31, 27:36, 2,123 | 0.11, 0.13, 0.03 |
| 44.8 | 206 (15) | 732 | 115 | 0.933 Doppler, at least | 3:41 / 4:00 (9+0) | 94:58, 24:30, 1,855 | 0.15, 0.16, 0.04 |
| 64.9 | 311 (23) | 994 | 160 | 0.919 Doppler, at least | 5:38 / 6:16 (17+0) | 74:52, 21:51, 1,593 | 0.26, 0.29, 0.08 |
| 79.8 | 429 (33) | 1,344 | 274 | 0.885 Doppler, at least | 8:05 / 8:49 (20+0) | 60:00, 16:35, 1,243 | 0.49, 0.53, 0.13 |
| **\* 88.5** | **459 (35)** | **1,466** | **297** | **0.874 Doppler, about** | **8:45 / 9:33 (22+0)** | **51:20, 14:51, 1,121** | **0.59, 0.64, 0.17** |
| h 94.7 | 394 (33) | 1,557 | 228 | 0.873 Doppler, about | 7:31 / 8:12 (18+0) | 45:04, 13:22, 1,030 | 0.56, 0.61, 0.17 |
| h 104.9 | 546 (41) | 1,718 | 228 | 0.871 Doppler, about | 10:26 / 11:10 (16+4) | 34:54, 12:14, 870 | 0.85, 0.91, 0.30 |
| h 114.9 | 714 (50) | 1,912 | 199 | 0.867 Doppler, about | 13:44 / 15:13 (17+24) | 24:52, 9:50, 675 | 1.40, 1.55, 0.55 |
| h 119.9 | 37 (3) | 2,143 | 10 | 0.858 Doppler, about | 0:43 / 0:53 (0+5) | 19:55, 5:06, 445 | 0.14, 0.17, 0.04 |
| h 130.0 | 102 (5) | 2,452 | 84 | 0.887 Doppler, about | 1:55 / 2:13 (0+8) | 9:49, 1:30, 136 | 1.27, 1.47, 0.19 |
| h 139.8 | 29 (3) | 2,587 | 26 | 0.907 Doppler, about | 0:32 / 0:32 (0+0) | end | — |

**A, the far point measured to first arrival (119.8 min)** (`arrival.out`): actual elapsed 31 min 17 s, moving 9 min 37 s, path 671 m. **Pace-only 8 min 45 s is 0.91 of the walking time; with the allowance, 9 min 33 s is 0.99.** Against elapsed time, 0.28.

**B** (origin leg 9 m, included in the pace-only figure)

| min in | route m (joins) | retrace m | straight m | pace, source, label | pace-only / with (climb+desc) | actual elapsed, moving, path | pace-only÷moving, with÷moving, pace-only÷elapsed |
|---|---|---|---|---|---|---|---|
| 5.0 | 78 (4) | 132 | 85 | 0.889 default, at least | 1:38 / 1:38 (0+0) | 125:37, 57:41, 3,609 | 0.03, 0.03, 0.01 |
| 20.0 | 376 (14) | 468 | 280 | 0.873 Doppler, at least | 7:21 / 7:57 (0+16) | 110:35, 52:08, 3,273 | 0.14, 0.15, 0.07 |
| 40.0 | 551 (24) | 695 | 425 | 0.841 Doppler, at least | 11:06 / 12:12 (0+30) | 90:36, 49:52, 3,045 | 0.22, 0.24, 0.12 |
| 60.0 | 1,074 (34) | 1,260 | 671 | 0.845 Doppler, about | 21:21 / 23:38 (0+62) | 70:36, 40:32, 2,480 | 0.53, 0.58, 0.30 |
| 79.7 | 1,604 (42) | 1,819 | 939 | 0.851 Doppler, about | 31:36 / 35:02 (32+62) | 50:53, 31:04, 1,922 | 1.02, 1.13, 0.62 |
| **\* 91.5** | **1,956 (49)** | **2,206** | **1,174** | **0.863 Doppler, about** | **37:56 / 42:04 (51+62)** | **39:08, 24:49, 1,534** | **1.53, 1.69, 0.97** |
| h 100.0 | 2,099 (54) | 2,553 | 917 | 0.883 Doppler, about | 39:47 / 44:15 (60+62) | 30:38, 18:32, 1,188 | 2.15, 2.39, 1.30 |
| h 109.8 | 2,304 (59) | 2,781 | 763 | 0.886 Doppler, about | 43:32 / 48:13 (66+62) | 20:44, 15:06, 960 | 2.88, 3.19, 2.10 |
| h 115.0 | 2,514 (62) | 2,996 | 591 | 0.887 Doppler, about | 47:26 / 52:13 (69+62) | 15:36, 11:29, 744 | 4.13, 4.55, 3.04 |
| h 124.7 | 2,856 (71) | 3,387 | 349 | 0.905 Doppler, about | 52:47 / 57:37 (70+62) | 5:54, 5:14, 354 | 10.1, 11.0, 8.95 |
| h 129.9 | 3,179 (72) | 3,719 | 75 | 0.924 Doppler, about | 57:31 / 62:42 (71+70) | 0:38, 0:23, 22 | 150, 164, 91 |
| h 130.6 | 3,198 (73) | 3,741 | 67 | 0.930 Doppler, about | 57:29 / 62:51 (72+74) | end | — |

Over B's whole homeward phase (272 kept points) the pace-only figure is never short of the actual moving time (median 3.8×); over A's (192 points, to the end) it is short at 65 % of points (median 0.77), and with the allowance short at 8 of 11 five-minute marks.

### The two findings reconciled (computed; the identity's residual is 0.0000 at every one of the 464 homeward points)

```
app ÷ actual moving  =  route home ÷ path walked back   ×   (path walked back ÷ moving metres)  ×  (return moving speed ÷ pace so far)
                        [effect 1: which path]              [effect 2a: sub-floor metres]            [effect 2b: pace change]
```

| At the far point | A, to the end (both earlier findings) | A, to first arrival | B, to the end |
|---|---|---|---|
| effect 1, route ÷ walked | **0.41** | 0.68 | **1.27** |
| of which: self-join, route ÷ retrace | 0.31 | 0.31 | 0.89 |
| of which: the walker's own way back, retrace ÷ walked | 1.31 | 2.18 | 1.44 |
| effect 2a, walked ÷ moving metres | 1.31 | **1.50** | 1.10 |
| effect 2b, return speed ÷ pace so far | 1.10 | 0.89 | 1.09 |
| effect 2 (Finding 1's lean) | 1.44 | 1.34 | 1.19 |
| **app pace-only ÷ actual moving** | **0.59** | **0.91** | **1.53** |
| with #421's allowance | 0.64 | 0.99 | 1.69 |
| pace-only ÷ actual elapsed | 0.17 | 0.28 | 0.97 |

**Why the two findings disagreed.** They measured different paths against the same return, and that return was the wrong one on A.

- Finding 1 (+44 %/+19 % long) divided the path the walker *actually walked back* by the pace and compared it with the moving time: that is effect 2 alone (1.44 and 1.19 here). It never used the app's path and so could not see effect 1. It is right about what it measured: the lean is real and comes mostly from the metres walked inside intervals slower than 0.5 m/s (2a), which the path counts and the pace does not.
- Finding 2 (short on A) divided the *app's* path, the 459 m self-joined route, by the pace and compared it with 14 min 51 s of moving to the end of the recording. That return includes 5 min of moving and 450 m of walking around the start after the walker had arrived. To first arrival the walker's moving time was 9 min 37 s over 671 m, and the app's 8 min 45 s is 0.91 of it; with the allowance, 0.99. **On A the app's figure was right as a walking time to the car.** The self-join's shortening (effect 1, 0.68) and the lean (effect 2, 1.34) nearly cancelled: the walker did take a longer way back than the stitched route (671 m against 459 m, through the patch), and walked it slower and with more sub-floor metres than the way out.
- On B neither finding's framing applies, because B is a loop: the way back came within 10 m of the way out at 1 % of its points, and within 50 m at 17 %. The app's path can only be the recorded track (the retrace ruling, `PathHome.kt:72-86`), so the route home *grew* as the walker closed the loop, from 1,956 m at the far point to 3,198 m when they were 67 m from the car. At the far point the figure was 1.53× the walking time and 0.97× the elapsed time; three-quarters of the way home (349 m from the car) it said 57 min 37 s where the walker took 5 min 54 s. **Effect 1 dominates B everywhere and is long; effect 2 is +19 % at the far point and falls toward 1.0.** No ε up to 50 m changes this: the route home at the end goes 3,198 → 3,083 → 2,960 → 2,832 → 2,638 m for ε = 10, 15, 20, 30, 50 (`arrival.out`). ε is not what is wrong on B; nothing about the recorded track can say the walker will come home another way.

**Which dominates where, in one line each.** A patch worked and then retraced (A): the two effects are of opposite sign and similar size; the app lands within 10 % of the walking time to the car. A loop (B): effect 1, long, by the whole length of the way out still unjoined, and growing as the walker nears home; the lean adds a fifth on top at the far point.

### Against the thing safety depends on: elapsed time (computed)

The figure is walking time, by the owner's ruling (`ReturnWalkingTime.kt:19-27`); stops are left to the 60 min darkness margin. On this walk the walker stopped less on the way home than on the way out on both recordings (stop ratio 3.25 against 4.96 on A to arrival; 1.58 against 2.66 on B), about 0.6 of the way out's. A walker who left at "start back by" with the default margin and then behaved as this one did would have been home before sunset from both far points: A, 31 min 17 s elapsed against 9 min 33 s + 60 min (38 min of slack); B, 39 min 08 s against 42 min 04 s + 60 min (63 min). On A the slack came from the margin; on B the figure alone already exceeded the elapsed time.

### Could the actual return have been predicted from what the app knew at the tick? (computed, `reconcile.out`, `arrival.out`)

Predicted ÷ actual at the far points, A to first arrival and B to the end:

| candidate | A: ÷ moving | A: × stop ratio so far, ÷ elapsed | B: ÷ moving | B: × stop ratio, ÷ elapsed |
|---|---|---|---|---|
| C1 route home ÷ pace (the app, pace-only) | 0.91 | 1.39 | 1.52 | 2.57 |
| C2 C1 + allowance (#421, as built) | **0.99** | 1.51 | 1.69 | 2.85 |
| C3 plain retrace ÷ pace (before the self-join) | 2.91 | 4.52 | 1.72 | 2.90 |
| C4 straight line to origin ÷ pace | 0.59 | 0.90 | **0.91** | 1.54 |

No candidate is within 15 % on both. The one that fits A (the app's own) is 1.7× long on B; the one that fits B (the straight line, which the retrace ruling refuses) is 41 % short on A. **What decided each return was the walker's choice of way home, which the track so far does not contain:** on A they came back through the patch they had worked (longer than the stitched route, shorter than the full retrace); on B they came back a way they had never walked, 1.3× the straight line. Scaling by the stop ratio so far over-predicts elapsed time by 1.4 to 2.9×, because the walker stopped less going home. From what the app knows at the tick, the walking time along the track is predictable (A shows it is), and the walker's departure from the track is not.

### The label (computed, a side finding)

The "at least" label (`ReturnWalkingTime.kt:98`: under 15 min of governing moving time) was on until 86.7 min of A's 139.8 and 57.2 min of B's 130.6, because 15 min of moving time is an hour to an hour and a half of wall clock at this walker's stop ratio. On A it switched to "about" two minutes before the far point. It changes nothing the walker sees on the line: "start back by" reads the same for `About` and `AtLeast` (`SundownWatch.kt:425-426`, the owner's "Just 'start back by 5:32'").

## 3. Predictions against results

- P1 **right**: residual 0 at every point; 525 s, 2,266 s, 891 s, 1,489 s reproduced; 0.59 and 1.52.
- P2 **right on the figures, wrong in what they meant**: 0.41 against 1.44 (A), 1.27 against 1.19 (B); self-join 0.31 and 0.89; sub-floor 1.31 against pace 1.10 (A), 1.10 and 1.09 (B), all as predicted. But A's "effect 1 dominates" was an artefact of measuring to the recording's end; to first arrival the two effects cancel (0.68 against 1.34).
- P3 **right as posed** (A short at 65 % of homeward points, 8 of 11 marks with allowance, 5 of 7 in the first half; B never short), and the A half is the pottering after arrival, not the return.
- P4 **half wrong**: A is 0.17 to 0.28 of elapsed (a factor 3.6 to 5.9); B is 0.97 at the far point, not 0.4 or less: the loop's over-read offset the stops. Home before sunset on both: right.
- P5 **mostly right**: no candidate within 15 % on both; stop-ratio scaling within 30 % on at most one (A's C4 at 0.90; nothing on B). Wrong that the retrace is closer than the route on A: it is 2.9× long.
- P6 **wrong on timing**: the pace was measured from 23.1 min (A, differencing first) and 15.5 min (B), later than 10 to 15; the label stayed "at least" until 86.7 and 57.2 min, not 30 to 45. Right that it was off for the homeward phases, and that the origin legs are small (0 and 9 m).
- P7 **ill-posed**: the climb along the route grows with the route (B: 0 → 112 m along the way out), so "within 25 % of the far point's" was the wrong question. The hop is 0 by construction, as stated.

## 4. Options for the owner

Each as a step path, with what the line would have read on this walk at A's far point (actual walking to the car 9 min 37 s, elapsed 31 min 17 s), at B's far point (walking 24 min 49 s, elapsed 39 min 08 s) and at B three-quarters of the way home, 349 m from the car (walking 5 min 14 s, elapsed 5 min 54 s). "Start back by" is sunset − margin − the figure, so a longer figure is an earlier warning. Figures from `options.out` and `reconcile.out`.

**O0. Keep what is built (pre-main cfef2f4cb).** Recording on → the line reads "start back by" sunset − margin − (route home along the self-joined track ÷ moving pace + 2.2 s per metre climbed and descended along it). On this walk: A far point **9 min 33 s** (1 % under the walking time to the car); B far point **42 min 04 s** (69 % over the walking time, 7 % over the elapsed time); B 349 m from home **57 min 37 s** (ten times the walking time). Short risk: none seen; the lean (+34 % at A's far point) was used up by a slower, stoppier walk home, and without it the figure would have read short by a third (O4). Long risk: on a loop the figure is the whole unjoined way out, growing as the walker nears home, up to about an hour early at the car. Cost: none. One of the copy's seven long walks is a loop.

**O1. Notice a loop closing, and floor the figure with the straight line, labelled.** Recording on → for N minutes the straight line to the origin has fallen while the route home has not (on B this is true at every homeward mark from 100 min: straight −157 to −447 m per 10 min, route +208 to +552; on A at none) → the walk back becomes k × straight line ÷ pace, flat, labelled "at least" (its honest reading: a floor) → "start back by" moves later by the difference. On this walk, at B 349 m from home: k = 1.0 **6 min 26 s**, k = 1.3 **8 min 22 s**, k = 2.3 **14 min 48 s**, against 57 min 37 s built and 5 min 54 s actual; at B's far point unchanged (the detector has not fired); at A unchanged. Short risk: real, and the retrace ruling's own case: a straight line across ground not walked (a river, a cliff, a fence) in failing light; k is the only guard, and this walk gives two values for it (1.3 on B, 2.3 on A). The label does not reach the line's text. Long risk: none beyond k. Cost: medium: a few ticks of memory in `SundownWatch` (which already carries the hop band between ticks), a new `DegradeReason`, a detector with two thresholds (metres fallen, minutes), tests. **Reopens the 2026-09-07 retrace ruling and -1623 for the sundown figure**, which is why it is an owner's decision and not a recommendation here.

**O2. Return, and the way home shown (already built; -1498 answer 4, -1589).** Walker taps Return → a trail way home is found from where they are → the walk back follows that route, not the track (`SundownWatch.kt:302-303`, `returnWalkingTimeAlong`). On B this would have replaced the growing loop figure with a route from the walker's place home, if trails exist there and the owner tapped Return; neither is knowable from the copy (no routing data on this laptop; coordinates stay off this report; Return's state is not stored). Short risk: none new; off trails there is no route and O0 stands. Cost: none in code. What this walk shows is that the loop case is exactly the case the way home shown exists for, and that before Return nothing covers it.

**O3. Raise ε (priced to rule out).** At the end of B the route home is 3,198 m at ε = 10 and still 2,638 m at ε = 50; at A's far point ε = 50 shortens the stitched route from 459 m to 308 m, where the walker then walked 671 m. ε cannot join a loop and only shortens the one figure that was right. Reopens the ruling for no gain on this walk.

**O4. Remove the lean (count sub-floor metres in the pace) (priced to rule out).** Pace = path so far ÷ moving time so far: 1.370 m/s on A at the far point (against 0.874), 1.069 on B. A far point **5 min 35 s against 9 min 37 s of walking: 42 % short.** B far point 30 min 30 s (23 % long). The lean is a safety margin that was spent on this walk; taking it out makes A's figure confidently short, the one failure the file exists to avoid.

**O5. A stop allowance from the walker's own stop ratio (priced to rule out; reopens "no attempt to model how much this person stops", `MovingPace.kt:15-19`).** A: 9 min 33 s × 4.96 = **47 min 23 s against 31 min 17 s** elapsed (+51 %); B: 42 min 04 s × 2.66 = **111 min 48 s against 39 min 08 s** (+186 %). The walker stopped 0.59 to 0.66 as much on the way home as on the way out; a ratio from the way out over-predicts by half to threefold.

## 5. What one walk cannot tell

- **How the owner walks home when actually heading home.** Both returns were foraging returns with no sundown pressure (A spent 20 min at the car afterwards). The walking time was predictable on A; whether a walker under the alert retraces, cuts across or loops is the one thing that decided both returns and the one thing not in the data.
- **k for a straight-line floor:** 1.3 (B) and 2.3 (A, a patch) from two returns; nothing says which a loop home across new ground draws.
- **Whether the way-home-shown (O2) would have covered B:** needs the trail data at that place and whether Return was tapped; neither is in the copy.
- **How often the owner loops:** one of seven long walks in this copy; the other six come home over their own track (27 to 100 % of homeward points within 10 m of the way out).
- **Whether the stop ratio on the way home is reliably about 0.6 of the way out's**, as it was twice here.
- **The hop:** 0 at every sampled tick by construction (the current fix is the stored point); on the phone it is the few metres since the last kept point, under the 25 m band, and not measured here.

## 6. Disclosure

**Read.** Every file:line in section 1, on pre-main `cfef2f4cb`; RECORD -1696, -1689, -1687, -1691, -1623, -1620, -1498 (branch records-after-173); Sorrel's review and `recompute.py`/`recompute.out` at `/mnt/work/scratch/sorrel-421/`; the earlier report and its scripts; `NavigationReadout.kt:57-88` (arrival constants), `SundownPreferencesRepository.kt:62`, `SundownLine.kt:66-67`, `DecideSundownAlertUseCase.kt:72-75`, the copy's `tracks`/`waypoints` schema.

**Computed.** Everything in sections 2 to 4 with a figure, from the five scripts in the evidence folder over the read-only copy; the controls against Sorrel's and the earlier report's independent figures, all passing.

**Inferred.** That the two effects "nearly cancel" on A describes this one return; that B's walker "came home a way they had never walked" is read from the nearest-outward distances (median 263 m), not from a map; the phone's hop at a tick; that the route shown after Return would have been a trail route on B.

**Could not determine.** Whether Return was tapped on either recording; whether trails exist along B's way home; the owner's behaviour under an actual sundown alert; the time of day of anything (kept off this report by design).

**Premises that were wrong.** The method's end point for the return (the recording's last kept point) on A, corrected openly above with before-and-after figures (0.59 → 0.91 pace-only, 0.64 → 0.99 with allowance). Finding 2's "1,121 m walked, 14 min 51 s" as the walker's return: 450 m and 5 min of it came after arriving. Finding 1's framing as a statement about the app's figure: it was a statement about the pace/path lean on the walked path and never used the app's path. P2's meaning, P4's B half, P6's timing, P7's question.

**Decided beyond scope.** Adding `arrival.py` (first arrival, nearest-outward distances, the ε sweep), `loops.py` (loop frequency over the copy's other walks) and `options.py` (the option figures) after seeing the first run, all recorded as additions. Keeping every kept point's figures in the CSVs (minutes since the recording's start, no times of day, no coordinates). Reading Sorrel's scratch folder for the control figures. Nothing on the phone or in the copy was changed; the scratch outputs are under `/mnt/work/scratch/s26-hilly-walk/out/reconcile/`.
