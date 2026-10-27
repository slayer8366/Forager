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

## 2. Results

(To be written after the scripts run; nothing below this line existed at the method commit.)
