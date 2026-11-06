# The hilly walk: climbs, grades, pace and reported accuracy (S26)

Researcher: Juniper Sounding (Fable). For RECORD -1685 (the owner: "Your hilly walk: climbs"), toward -1629's "A hilly walk to log climbs" and the climbing allowance on the walk-back time, and -1623's open question E (reported horizontal accuracy on a walk).

Read-only on the phone. Raw data, coordinates, place names, times of day stay in `/mnt/work/scratch/s26-hilly-walk/` on the laptop; this report carries figures only. Scripts in `evidence/2026-10-26-hilly-walk-climbs/` read from that scratch path, so they reproduce locally and carry no data.

## 1. Method, committed before the data was read

### What was done on the phone (read)

- `adb devices -l`: serial R5GYC4CYJ3X, model SM-S948U, Android 17.
- `dumpsys package com.zynergylabs.forager.app`: `versionName=1.0.4026+gd2c2d18b`, `versionCode=4026`, flags `DEBUGGABLE`; first installed 2026-10-23, last updated 2026-10-26. The build commit `d2c2d18b` is the merge of PR #413 on pre-main; it is an ancestor of pre-main's head `0914bed23`.
- `run-as ... ls databases/`: `forager.db` (827,392 bytes), `forager.db-wal` (206,032 bytes, last written 2026-10-25), `forager.db-shm`. The app was running when read (one process); the WAL had not been written since the day before, so a byte copy of the three files is a consistent snapshot.
- Copy by `adb exec-out run-as <package> cat databases/<file>` for the three files. SHA-256 of `forager.db` and `forager.db-wal` on the phone equal the copies. Nothing installed, launched, changed or written. The copy is opened only with `mode=ro` on a second copy in `scratch/work/`, never the originals in `scratch/db/`.

### How tracks and points are stored at `d2c2d18b` (read from code)

- `user_version` of the copied database is 22, with tables `tracks`, `track_points`, `waypoints`, `cartography_*`, `mushroom_log_entries`, `log_photos`, `offline_regions`, `planned_trips`, `cached_searches`.
- `track_points` (`data/local/TrackPointEntity.kt:31-44`): `trackId`, `lat`, `lng`, `altitude REAL?`, `accuracyMeters REAL?`, `timestampEpochMillis`, `speedMetersPerSecond REAL?`, `speedAccuracyMetersPerSecond REAL?`, `pressureHpa REAL?`. **No vertical accuracy and no provider column.** `altitude` is `Location.getAltitude()` when `hasAltitude()`, `accuracyMeters` is `getAccuracy()` when `hasAccuracy()`, speed and its accuracy under `hasSpeed()`/`hasSpeedAccuracy()` (`location/AndroidLocationTracker.kt:127-136`). `pressureHpa` is the barometer's latest fresh reading when the point was kept (`service/TrackRecordingService.kt:220`).
- Fixes come from the platform `LocationManager`, GPS and network providers both requested (`AndroidLocationTracker.kt:112-116`). The recording mode is `HIGH_ACCURACY` (`MainActivity.kt:647`): a point is kept when its reported accuracy is at most 30 m (or not reported), at least 5 s and 5 m after the last kept point (`domain/LocationSampler.kt:35-48`, `domain/model/TrackRecordingMode.kt:27`). So stored points already contain the stops as gaps, and network fixes are stored.
- The read seam excludes a stored point whose timestamp has non-zero milliseconds, as a network-provider fix (`domain/NetworkProviderFix.kt:48-54`); points are read in timestamp order (`data/local/TrackDao.kt:52`). The exclusion count rides with the track.
- Heights the app reads (`domain/TrackHeights.kt:18-21`): when at least half the points carry a pressure, the barometric height re-anchored to GPS (`domain/BarometricHeight.kt`: standard atmosphere, 500 to 1100 hPa limits, a 60 s-each-side median, a 0.5 m/s step limit, a 60 s-each-side mean, then an anchor to GPS height, median over the first minute and an hour's time constant, GPS more than 30 m away not used); otherwise smoothed GPS height (`TrackHeights.kt:40-58`: a symmetric median of up to 2 points each side within 30 s, then a symmetric mean within 30 s each side). Climb and descent bank through a 4 m hysteresis (`domain/ComputeTrackStatisticsUseCase.kt:84-91`, `ElevationHysteresis.THRESHOLD_METERS = 4.0`). The storage, height and pace code is identical at `d2c2d18b` and `0914bed23` (`git diff --stat` between them touches only off-track and way-home files).
- The walk-back time on pre-main (`domain/ReturnWalkingTime.kt:86-109`): `walkingMillis = path.totalMeters / pace.speedMetersPerSecond`, where the pace is `movingPace` (`domain/MovingPace.kt:98-147`): over intervals between consecutive kept points whose implied speed is at least 0.5 m/s, the duration-weighted Doppler speed when such intervals total five minutes, else the differenced speed when all moving intervals do, else 2 mph (0.889 m/s). **There is no grade or climb term anywhere in `SundownWatch.kt`, `BackByWatch.kt`, `wayhome/WayHomeWatch.kt`, `SundownLine.kt` or `DecideSundownAlertUseCase.kt`** (grep for climb, grade, slope, elevation, ascent, uphill: no hits). The only climb figures in the navigation code are the routing profile's `uphillcostvalue`/`downhillcostvalue` (`assets/routing/profiles2/hiking-mountain.brf:61-65`, "considered flat speed 4 km/h, climbing speed 600 m/h"), which shape the route, not the time.

### Picking the walk

`inventory.py` lists every track in the copy with: id prefix, named or not, date (UTC), duration, stored points, points excluded by the whole-second rule, altitude and pressure coverage, haversine distance over kept points, raw sum of positive altitude steps, and the app-method climb. The hilly walk is the most recent track whose app-method climb is the largest among the recent tracks and whose duration is more than half an hour; if two recent tracks are both hilly, both are listed here and the analysis stops at the inventory for the planner to choose.

### Computing (`analyze.py`)

All over the kept points (whole-second timestamps), in timestamp order, as the app reads them. Distance is the haversine on a 6,371,008.8 m sphere (`domain/GeoDistance.kt:17-27`).

1. **Distance, duration, moving time.** Duration is last minus first timestamp. Moving time and moving distance are summed over intervals whose implied speed is at least 0.5 m/s, the app's floor. The app's `movingPace` is ported: differenced and Doppler figures, counts of points without speed and below the floor.
2. **Heights, three series, shown side by side.** (a) raw GPS `altitude`; (b) the app's smoothed GPS (port of `smoothedGpsHeights`); (c) the app's barometric height (port of `barometricHeights`), when at least half the points carry pressure. The series the app would show is (c) when it applies, else (b); it is called *chosen* below. Climb and descent for each series at hysteresis 0 m (a raw sum of steps), 4 m (the app's) and 10 m. The ports are checked against the Kotlin by construction only, read line for line; no Gradle build runs here (the laptop is for forecast work), so a port error is a stated risk and the raw figures stand beside them.
3. **Legs and grades.** The kept path is cut into legs of at least 50 m of horizontal distance. A leg's grade is the chosen height change from its first to its last point over its horizontal distance; its moving speed is its moving distance over its moving time (intervals at or above the floor only, so a stop inside a leg does not slow it). Legs with no moving time are dropped and counted. Steepest sustained grade: the largest and smallest grade over sliding windows of 100, 200 and 500 m of path.
4. **Pace against grade.** Legs binned by grade: below −15 %, −15 to −8, −8 to −3, −3 to +3 (flat), 3 to 8, 8 to 15, above 15 %. Per bin: legs, distance, distance-weighted speed, median and quartiles of leg speed, and the Doppler speed where present.
5. **A time model.** Weighted least squares over legs, weights the leg distance: seconds per metre of path = a + b·(ascent per metre) + c·(descent per metre). `a` is the flat pace in s/m, `b` the extra seconds per metre climbed, `b/a` the metres of flat path one metre of climb costs (Naismith: 1 h per 5 km and 1 h per 600 m, so b/a ≈ 8.3). A 2,000-resample bootstrap over legs gives 90 % intervals. The same fit without the descent term.
6. **Out-and-back test.** If the end is within 150 m of the start, the walk is split at its farthest point from the start. The second half's actual moving time is compared with (i) the app's estimate, second-half distance over the first half's whole-walk moving pace, and (ii) a grade-aware estimate from the time model fitted on the first half alone, applied leg by leg to the second half. If the walk is not out-and-back, this step says so and is skipped.
7. **Noise against the climbs.** Raw GPS altitude minus the chosen heights: standard deviation and median absolute deviation. Raw altitude step between consecutive kept points: MAD and 95th percentile. The raw positive-step sum against the chosen climb. Pressure coverage and the number of barometric steps the 0.5 m/s limit took out. No vertical accuracy is stored, so none is reported.
8. **Reported horizontal accuracy (question E).** Over all stored points of the walk, split by the whole-second rule: count, distinct values, minimum, median, 90th, 95th, maximum, and the share above 10 m and 20 m. Whether the value is a constant, as the S22's 3.79 m is (`LocationSampler.kt:23-26`).

### Predictions, before the data

- P1. Stored points carry whole-second GPS timestamps and sub-second network timestamps; the S26 has a SIM, so the excluded share is between 10 % and 35 % (the four S22 evidence tracks ran 0 to 47 %).
- P2. The S26 has a barometer and at least 90 % of the walk's points carry a pressure, so the app's heights are barometric.
- P3. The raw positive-step sum of GPS altitude is at least 1.5 times the barometric climb at 4 m hysteresis.
- P4. Moving speed falls with uphill grade; `b/a` lands between 5 and 12 m of flat path per metre climbed (Naismith's 8.3 inside that band). Descent's `c` is small or negative on gentle grades and positive above about 10 %.
- P5. Reported horizontal accuracy on the S26 varies fix to fix (not a constant), with the GPS-rule median under 10 m and the network-rule values much larger.
- P6. On the out-and-back test, the grade-aware estimate is closer to the second half's actual moving time than the flat estimate when the halves differ in net climb; when they are mirror images the two estimates agree within 10 %.

A result against a prediction is reported as such, not absorbed.

## 2. Which walk (read, from `inventory.py`)

The copy holds 48 tracks, six of them longer than 20 minutes. The two most recent are both dated 2026-10-24 (UTC) and are **one outing in two recordings**: recording A (id `9685146a…`, 140 min, 2.59 km) ends, and recording B (`cad94737…`, 131 min, 3.74 km) starts 11.9 minutes later 104 m away, 80 m from A's start (`candidates-1024.out`). Each closes as an out-and-back (end 26 m and 76 m from its start). Both carry pressure on 97 % of points, so the app reads them barometrically. **No track in the copy is later than 2026-10-24.** RECORD -1629 ("I'll do a walk for the elevation") is stamped 2026-10-25 06:24 UTC, after this outing; -1622/-1625 place the owner's walk on build 3937 the same day. So either this outing is the hilly walk the owner means, or the intended walk is not recorded yet. This report reads the outing as the walk and says so; the planner should confirm with the owner.

Older candidates, for the record (smoothed GPS, no barometer, RECORD -867's method): 2026-10-09, 69 min, 3.41 km, climb 153 m / descent 133 m at 4 m hysteresis; 2026-10-10, 25 min, 2.10 km, 110 / 88 m, which does not close (291 m). Both have more GPS climb than the 2026-10-24 outing, but GPS-smoothed climb over-reads badly on this phone (section 3), so they are not clearly hillier.

## 3. Results (computed, `analyze.py`, `pooled.py`, `accuracy_values.py`; figures in `*-figures.json`, legs in `*-legs.csv`)

### Distance, duration, pace

| | A | B | outing |
|---|---|---|---|
| Stored points / kept / excluded by the whole-second rule | 457 / 457 / 0 | 678 / 677 / 1 | 1,135 / 1,134 / 1 (0.1 %) |
| Distance over kept points | 2,587 m | 3,741 m | 6,328 m |
| Duration (first to last point) | 139.8 min | 130.6 min | 270.4 min, plus an 11.9 min gap |
| Moving time (intervals at or above 0.5 m/s) | 32.7 min | 59.2 min | 91.9 min |
| Moving metres | 1,717 m | 3,050 m | 4,767 m |
| Moving pace the app would use | 0.907 m/s, Doppler | 0.930 m/s, Doppler | |
| Differenced pace, same intervals | 0.876 m/s | 0.858 m/s | |
| Points without Doppler speed / below floor | 0 / 30 | 0 / 35 | |
| Interval between kept points, median / 90th | 8 s / 43 s | 7 s / 20.5 s | |

A foraging walk: a third to a half of the time moving, flat moving pace about 0.9 m/s (2.0 mph), as on the S22's 2026-09-07 walk (0.89 m/s).

### Heights and climbs, raw against smoothed

Climb / descent in metres. "App barometric" is what the installed build shows; "bridged" is the same pipeline with pressure interpolated across the points that have none (a diagnostic, not the app).

| Series | hysteresis | A up / down | B up / down |
|---|---|---|---|
| Raw GPS altitude | 0 m (step sum) | 400 / 403 | 424 / 408 |
| Raw GPS altitude | 4 m | 229 / 230 | 206 / 189 |
| Raw GPS altitude | 10 m | 107 / 104 | 101 / 84 |
| App smoothed GPS | 4 m | 168 / 169 | 106 / 93 |
| App smoothed GPS | 10 m | 59 / 58 | 84 / 73 |
| **App barometric (what the app shows)** | **4 m** | **31 / 37** | **41 / 34** |
| App barometric | 10 m | 18 / 21 | 21 / 22 |
| Barometric, gaps bridged | 4 m | 53 / 43 | 72 / 69 |
| Barometric, gaps bridged | 10 m | 35 / 34 | 76 / 73 |

Height range of the barometric series: A 34.5 m, B 73.6 m (raw GPS 47.6 m and 87.9 m). The barometric step limit removed nothing (0 steps in both). Net change over each recording: within a few metres of zero, as an out-and-back should be.

**The relief is modest.** The outing has one hill of about 70 m and smaller undulations; it is a foraging walk over rolling ground, not the sustained climb -1629 asked for.

**The app's barometric climb drops about 40 % of the climb on this phone, and the reason is in the code, not the hill.** 14 of A's and 20 of B's kept points carry no pressure (3 %), and they fall throughout the walk (13 of 14 and 20 of 20 inside it, spread from 2 % to 92 % of the way along; `pressure_gaps` in the figures), not at the start. `BarometricHeight.kt:40-41` assumes a missing pressure is "a reading missed at the very start, before the sensor's first sample"; each missing point instead breaks a barometric run (`runsOf`, "nothing is bridged across it") and resets the climb reference (`ComputeTrackStatisticsUseCase.kt:78-81`), so the height change across every gap is lost: 31 m shown against 53 m bridged in A, 41 m against 72 m in B. The mechanism, inferred from the constants: `pressureForPoint` stores a pressure only if the latest reading is at most 2 s old (`PressureProvider.kt:29-36`, `PRESSURE_MAX_AGE_NANOS`), while the sensor is registered with a report latency of up to 5 s (`PressureProvider.kt:42`, `PRESSURE_MAX_REPORT_LATENCY_MICROS`), so a point made while a batched reading is 2 to 5 s old gets `null`. The gap between the pressure-carrying neighbours of a missing point runs up to 131 s (A) and 158 s (B) because the kept points themselves are sparse there. This is a finding for the planner, not something this report changes.

### Steepest sustained grades (barometric heights)

| Window of path | A up / down | B up / down |
|---|---|---|
| 100 m | +18.1 % / −16.4 % | +15.8 % / −11.7 % |
| 200 m | +10.2 % / −9.6 % | +10.1 % / −8.5 % |
| 500 m | +5.7 % / −4.1 % | +8.3 % / −6.6 % |

### Pace against grade (pooled legs of at least 50 m; 108 usable, 12 dropped for no moving time or a missing height)

| Grade | legs | path | distance-weighted speed | leg speed q1 / median / q3 |
|---|---|---|---|---|
| −15 to −8 % | 8 | 438 m | 0.745 m/s | 0.657 / 0.768 / 0.794 |
| −8 to −3 % | 16 | 847 m | 0.816 | 0.728 / 0.815 / 0.885 |
| −3 to +3 % (flat) | 56 | 2,957 m | **0.931** | 0.807 / 0.911 / 1.070 |
| +3 to +8 % | 18 | 912 m | 0.830 | 0.746 / 0.810 / 0.905 |
| +8 to +15 % | 9 | 482 m | 0.771 | 0.687 / 0.736 / 0.831 |
| above +15 % | 1 | 51 m | 0.816 | one leg |

Steeper than ±15 % over a 50 m leg happened once. Per recording the picture is the same shape but noisier (`grade_bins` in each figures file): A slows more with grade (flat 0.99, +8 to 15 % 0.67, −15 to −8 % 0.70 m/s), B barely (0.90, 0.85, 0.76).

### The time model

Seconds per metre of path = a + b·(ascent per metre) + c·(descent per metre), weighted least squares over the pooled legs, 90 % intervals from a 2,000-resample bootstrap over legs:

- a = 1.084 s/m (flat 0.922 m/s), 90 % 1.031 to 1.139.
- **b = 2.23 s per metre climbed**, 90 % 1.27 to 3.20.
- c = 3.19 s per metre descended, 90 % 2.15 to 4.20.
- **b/a = 2.05 m of flat path per metre of climb**, 90 % 1.14 to 3.05. Naismith's rule is 8.33.
- With no descent term: b = 1.19 s/m (0.26 to 2.09), b/a = 1.02 (0.21 to 1.82); the descent term matters because descents here are as slow as climbs.

Per recording: A b/a = 3.48 (1.87 to 5.72), B b/a = 1.14 (0.31 to 2.26). The two recordings, same walker and same day, disagree by a factor of three; the pooled interval covers both.

What it costs on a return: by the pooled model, 50 m of climb costs 111 s (90 % 63 to 160 s), about 103 m of flat path; 100 m costs 223 s; 200 m costs 445 s. On a 1.5 km walk back at 0.92 m/s (about 1,630 s), 50 m of climb is +7 %.

### Out-and-back test (first half predicts the second)

Split at the farthest point from the start. The first half carries the climb in both recordings (A: 22 m up / 37 m down, then 9 / 0; B: 33 / 29, then 8 / 4).

| | A | B |
|---|---|---|
| Second half: path / moving metres / actual moving time | 1,121 m / 853 m / 891 s | 1,534 m / 1,395 m / 1,489 s |
| The app's estimate: path ÷ first-half pace | 1,282 s (**+44 %**) | 1,778 s (**+19 %**) |
| Moving metres ÷ first-half pace | 976 s (+10 %) | 1,616 s (+9 %) |
| Grade model fitted on the first half, applied leg by leg | 786 s (−12 %) | 1,649 s (+11 %) |
| Same, flat + ascent only | 855 s (−4 %) | 1,659 s (+11 %) |

The grade-aware estimate was not closer than the flat one in either recording. The error that dominates the app's figure is not grade: it is that the path home counts every metre between kept points, including the metres walked inside intervals slower than the floor (stops, examining finds), while the pace counts only moving intervals; here that reads 19 to 44 % long. That direction is the safe one and is recorded as accepted in `MovingPace.kt` ("leans long"); it is stated here because any climbing allowance would sit on top of it.

### Altitude noise against the climbs

- Raw GPS altitude minus barometric height: A sd 4.2 m, median absolute deviation 2.9 m; B mean −4.4 m, sd 5.3 m, MAD 3.8 m. The smoothed-GPS series wanders ±10 to 13 m from the barometric one over minutes (sd 3.9 and 5.0 m), which is what inflates its climb to 106 to 168 m against a 70 m hill.
- Raw altitude step between consecutive kept points (7 to 8 s apart): median 1.3 m and 1.0 m, 95th percentile 5.3 m and 3.3 m, largest 9.6 m and 9.4 m.
- The raw positive-step sum (400 m and 424 m) is 13 and 10 times the app's barometric climb, and 7.6 and 5.9 times the bridged one. Prediction P3 (at least 1.5×) holds with a wide margin.
- **No vertical accuracy is stored** (`track_points` has no such column), so none can be reported.

### Reported horizontal accuracy (question E)

All 1,134 kept points carry a value; one network-rule point in B reads 4.50 m.

| | A | B |
|---|---|---|
| Distinct values | 186 | 55 |
| min / median / 90th / 95th / max | 3.79 / 9.94 / 10.62 / 10.84 / 11.65 m | 3.79 / 9.94 / 9.94 / 9.94 / 10.82 m |
| Exactly 9.9350 m | 229 (50.1 %) | 330 (48.7 %) |
| Exactly 3.7901 m (the S22's constant) | 44 (9.6 %) | 294 (43.4 %) |
| Other values (each once or twice), range | 184, 6.3 to 11.7 m | 53, 9.1 to 10.8 m |
| Above 10 m / above 20 m | 33.7 % / 0 | 3.5 % / 0 |

Two constants carry 60 % (A) and 92 % (B) of the points; the rest spread 6 to 12 m. The 30 m ceiling (`HIGH_ACCURACY`) never came near. As a proxy for whether the value means anything, points under 6 m ("tight") against the rest: median |raw altitude − barometric height| 2.66 against 3.01 m in A, 6.05 against 5.54 m in B; median |Doppler − differenced speed| on the interval each point ends 0.139 against 0.165 m/s in A, 0.132 against 0.158 m/s in B (`accuracy-values.out`). The tight cluster is no better on altitude and 15 % better on speed agreement. On this phone the field is a few labels, not a per-fix measurement; -1623's objection to building a reach on it stands on this data. Reported speed accuracy, by contrast, varies continuously (454 and 676 distinct values, median 0.78 and 0.75 m/s), unlike the S22's two-state flag.

## 4. What the data supports

- **A climbing allowance of about 2 s per metre climbed (90 % 1.3 to 3.2), roughly 2 m of flat path per metre of climb, with descent costing as much again.** That is a quarter of Naismith's 8.3 m per metre and would add about 7 % to a 1.5 km walk back with 50 m of climb. From one outing with 35 to 74 m of relief and sustained grades under 10 % over 200 m, by one walker, with the two recordings of the same day disagreeing by a factor of three, this is a bound, not a calibration: the data does not show a grade effect large enough to matter beside the walk-back's existing 19 to 44 % long lean, and it does not test the steep, sustained case -1629 asked about. A walk with at least 150 m of climb at 10 % or more over several hundred metres would.
- **Before any allowance is built on barometric climb, the pressure gap is the cheaper finding**: the app shows 31 and 41 m where the same pipeline with bridged gaps shows 53 and 72 m, from a 2 s freshness window against a 5 s sensor report latency. The planner may want this ruled on separately; an allowance fed by the app's own climb figure would see about 60 % of the climb.
- Reported horizontal accuracy on the S26 is two constants plus a spread, carrying little per-fix information (question E).

## 5. Predictions against results

- P1 **wrong**: 0 and 1 points excluded by the whole-second rule (0.1 %), not 10 to 35 %. Every kept point carries a Doppler speed, which on the S22 only GPS fixes did, so the kept points look like GPS fixes; whether the S26's network provider delivered nothing under 30 m or stamps whole seconds cannot be told without a provider column.
- P2 **right** on coverage (97 %, barometric heights), **wrong** on where the gaps fall (throughout, not at the start).
- P3 **right**, by 10 to 13× rather than 1.5×.
- P4 **partly right**: speed falls with uphill grade (0.93 → 0.83 → 0.77 m/s), but b/a = 2.05 is below the predicted 5 to 12 band and below Naismith; and descent is not "small or negative on gentle grades": −3 to −8 % is already slower than flat (0.816 against 0.931 m/s).
- P5 **partly right**: not one constant, but two constants carry 60 to 92 % of points; GPS-rule median 9.94 m (predicted under 10 m); the network-rule half is untestable with one point.
- P6 **wrong**: the grade-aware estimate was not closer in either half (−12 % and +11 % against +10 % and +9 % for the flat estimate over moving metres); the path-against-moving-metres mismatch (+19 to +44 %) swamps both.

## 6. Disclosure

**Confirmed (read or computed).** Installed build, flags and schema (`dumpsys`, `PRAGMA user_version`); SHA-256 of the copied database and WAL equal to the phone's; the storage, sampler, read-seam, height and pace code at `d2c2d18b` and `0914bed23` (identical files); every figure above from the scripts in the evidence folder on the copy.

**Inferred.** The pressure-gap mechanism (2 s freshness against 5 s report latency) from the constants and the gap pattern; not observed in a sensor log. That the two 2026-10-24 recordings are the owner's hilly walk. That kept points are GPS fixes (from Doppler presence, not a provider column). The Python ports match the Kotlin by line-for-line reading only; no Gradle test was run against them (the laptop is kept for forecast work), so a port error is possible and the raw figures stand beside the ported ones.

**Could not determine.** Vertical accuracy (not stored). Which provider each stored point came from. Whether the owner's intended hilly walk has been recorded at all (nothing later than 2026-10-24 is in the copy). The walker's pace on sustained grades above 10 %: one 50 m leg. How much of the leg-speed scatter is foraging behaviour rather than ground.

**Premises that were wrong.** Dispatch: "the owner's most recent recorded track that looks like the hilly walk" assumed one track; it is two recordings of one outing, and the outing predates -1629. P1, P6 and half of P2, P4 and P5 above. The code's own premise at `BarometricHeight.kt:40-41` (gaps only at the start) does not hold on this phone.

**Decided beyond scope.** Analysing both recordings rather than stopping at the inventory (the method said stop if two recent tracks were both hilly; these are one outing). Adding the bridged-barometric diagnostic, the pressure-gap positions and the accuracy proxy test, none of which the dispatch asked for; each is marked as beyond the app. Pooling the legs of both recordings for the headline model. Keeping the full 48-track inventory output in scratch (it lists the dates of every walk) and quoting only the six long tracks here. Nothing on the phone was changed.
