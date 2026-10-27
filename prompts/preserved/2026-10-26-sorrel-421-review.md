# Sorrel's review of PR #421: the climbing allowance in the walk-back time

Sorrel Tally (Fable), 2026-10-26, a fresh session. Read-only on code: no edits, no merges, no device, no Gradle (CI-only builds; the laptop is for forecast work). Branch `sorrel-421` off pre-main c445574da; this file is its only change.

**Reviewed:** PR #421, branch `climb-allowance` (f648f8480 stubs, 438aade31 build, b5d464483 test bound), base pre-main c445574da, against dispatch `prompts/preserved/2026-10-26-19.md` with Amendment 1 (records-after-173 d9d2a9f54), RECORD -1687, -1689, -1691, the Phase 1 report (`climb-allowance-report` 188557fbc), Juniper's walk report (`s26-hilly-walk-report` e576021d8). CI runs: fail-first 40416940315, first PR run 40417902441, green 40419543242, reverts 40418022061 (1), 40418024448 (2), 40418027639 (4), 40418030391 (5). The owner's walk copy read with `python3 -I` from `/mnt/work/scratch/s26-hilly-walk/work/forager.db` (`mode=ro`); figures only here, no coordinates or times.

Each claim is marked **read** (file and line, or a run's log), **computed** (my own code, `/mnt/work/scratch/sorrel-421/recompute.py`, output `recompute.out`), or **inferred**.

## 0. Baseline (read)

- `origin/pre-main` is c445574da (#420 merged), the PR's stated base; `origin/climb-allowance` is b5d464483 with exactly the three commits named. `gh pr view 421`: `MERGEABLE`, `CLEAN`; `git merge-tree --write-tree origin/pre-main origin/climb-allowance` writes a tree with no conflict.
- Green run 40419543242: the `unit-test-report` artifact sums to **5,723 tests, 0 failures, 0 errors, 24 skipped** (computed over its 779 XML files). First PR run: "5699 tests completed, 1 failed, 24 skipped" (log), the same 5,723.
- Fail-first 40416940315 at f648f8480: "14 tests completed, 11 failed" (log). The PR body says "13 of 14 new tests red"; the log says 11 of 14 (the cost test and two zero/rule checks pass over stubs, as the body itself lists). A slip in the count, not in the evidence (N1).

## 1. Mechanism prediction, written before computing, scored at the end

- **P1.** At each recording's far point (Juniper's split), the allowance over the self-joined route home from the bridged heights at 4 m: A between 40 and 90 s, because A's outward track criss-crosses and the joined route home is much shorter than the 1,466 m walked; B between 3.5 and 4.5 min. Both move "start back by" earlier, never later.
- **P2.** Juniper's regressor (the sum of per-leg height change over ≥ 50 m legs) reads 10 to 40 % more climb than the 4 m-banked figure on the same series, so 2.2 s per banked metre reads lower than the fit it came from by that much.
- **P3.** On a phone the tick's heights cost 2 to 3 times the CI JVM figure and stay under 300 ms at 2,880 points; no risk to the service (Default dispatcher, one tick at a time, 15 s apart).

## 2. The 10-24 figures, recomputed (computed)

Port check first: my own port of `BarometricHeight.kt` (with #420's bridging, `:117-130`) over each whole recording gives climb/descent **52.8/43.1 m (A)** and **72.4/68.7 m (B)**, equal to my #420 review's and Juniper's bridged figures. Unbridged it gives 31.2/36.9 and 41.0/33.6, Juniper's "app barometric" row. The port is sensitive to the thing it measures.

At the far point, with the heights computed over the track so far (as `pathHome` does on a tick), the self-joined route home (`joinedTrackRoute`, ε 10 m, my own Dijkstra), banked at 4 m along the route (`bankedClimb`), times 2.2 s:

| | A | B |
|---|---|---|
| Kept points so far / pressure coverage | 265 / 96.9 % | 405 / 97.0 % |
| Plain retrace of the track so far | 1,466 m | 2,206 m |
| **Self-joined route home** | **459 m**, 70 route points, 35 joins on it | **1,956 m**, 327 points, 49 joins |
| Pace so far (Doppler) | 0.874 m/s | 0.863 m/s |
| Pace-only walk back (hop 0, no origin leg) | 525 s (8 min 45 s) | 2,266 s (37 min 46 s) |
| Climb / descent along the route, bridged, 4 m | 21.9 / 0.0 = 21.9 m | 50.9 / 61.5 = 112.5 m |
| **Allowance** | **48.2 s** | **247.4 s = 4 min 7 s** |
| Share of the pace time | 9.2 % | 10.9 % |
| Same, unbridged (before #420) | 21.9 m, 48 s | 84.9 m, 3 min 7 s |
| Route walked the other way (banking asymmetry) | 24.6 m | 111.7 m |

**The coder's 48 s and 4 min 7 s are reproduced exactly.** The Phase 1 report's 2 min 12 s for A was the plain retrace of the first half with the whole recording's heights (I get the same 60.1 m, 2 min 12 s for that construction); the app does not walk that path: A's route home at the far point crosses 35 self-joins and is 459 m, so its climb is a third of the retrace's. B's route is close to the retrace and the figure is close to Phase 1's "about 4.3 min" after -18.

**Scoring.** P1 right on both (48 s in 40 to 90; 4 min 7 s in 3.5 to 4.5) and on direction. P2 partly right: on the unbridged series the leg regressor exceeds the 4 m figure by 32 % (A) and 94 % (B), outside my band on B; on the bridged series, which the PR reads, by 5 % and 15 %, under my band. P3 is inferred, not scored (§3.11).

## 3. The dispatch's and the planner's questions, in order

1. **The allowance reaches only the walk-back time (read).** `estimateAlong` adds `climbMillis` into `walkingMillis` (`ReturnWalkingTime.kt:109-116`); `SundownWatch.tick` turns that into `WalkBack.About/AtLeast` (`SundownWatch.kt:305-306`), which feeds the line's `startBackAt` and `decide(countdown, walkBack.millisOrNull, …)` (`:309`, `:317`); `DecideSundownAlertUseCase.kt:72-75` puts the heads-up 30 min before the same leave-by. So "start back by", the heads-up and the leave-by all move by the allowance. The diff touches no Back-by or HUD-distance file (`BackByWatch.kt`, `WayHomeAhead.kt` absent from `git diff --stat`): unchanged by construction.
2. **Before Return, the same self-joined route as the distance (read).** `pathHome` now calls `joinedTrackRoute` and hands `joined.route` to `climbAlongRoute` (`PathHome.kt:110`, `:123`); `joinedTrackHome` is that search with the chain dropped (`TrackSelfJoin.kt:74-75`), so distance and climb cannot walk different routes. The route starts at the most recent stored point, where the distance does; the hop and the origin leg are flat (`PathHome.kt:112-114`, no heights carried).
3. **After Return on a trail way home: the stand-in (read, computed on the fixture by the PR's tests).**
   - *Is the stretch sound?* `standInHeights` scales each sample's `alongMeters` by `trailMeters / trackMeters` (`WayHomeHeights.kt:60-67`), where `trackMeters` is the stand-in part's `joined.meters` and its samples' `along` is the haversine sum over the same route edges (`trackPartHeights`, `:45-55`), so the last sample lands at `trailMeters` (`WayHomeClimbTest`, "laid along the trail part's own metres", 0.01 m). `WayHomeLine` scales each part's drawn geometry to `part.meters` too (`WayHomeProgress.kt:57-74`), so part starts, line metres and sample metres agree. The orientation is right: the stand-in is the track from `leave.index` to `entry.index` in walking order, the direction the trail part is walked (`FindWayHomeUseCase.kt:98`). The origin leg keeps the part's heights (`toStart.copy(points = …, meters = …)`, `:124`), with the leg flat beyond the last sample.
   - *Can it read short?* Yes, two ways, both inherent to the owner's choice (b): where the trail crosses a rise the walker's track went round (stated in the file header and the doc), and where the walker's track between the two trail points is short against the trail (a trail crossed, then re-joined by a hairpin): the samples stretch but the banked climb is the short track's. With the 4 m banking the stretch itself adds nothing. Neither is a defect of the build; both are the stand-in's limits, recorded.
   - *Is it labelled?* In code, `PartHeights.Barometric.standIn = true` (asserted by `WayHomeClimbTest`); in the file header, `WayHomeLine.heights`' doc and `docs/navigation/2026-10-26-climbing-allowance.md`. Nothing on screen says so, and the dispatch asked for code and docs only. The figure stays "about".
4. **The track parts keep heights through `FindWayHomeUseCase` (read).** `trackPart` maps the joined route back to stored indices (`storedIndex`, `:182`) and attaches `trackPartHeights` (`:185-188`); all three track-part calls pass `heights` (`:98`, `:115`, `:119`). `WayHomePart.heights` defaults to `null` (`WayHome.kt:62`), so the waypoint's `STRAIGHT` parts carry none (`FindWayToWaypointUseCase.kt:83-85`) and `lineHeightsOf` gives `null` for that line. That `null` never reaches sundown: `wayHomeFor` reads `wayHomeWatch.progressFor` (`AppContainer.kt:477`), whose lines come from `wayHomeRouting::find` (`:454-457`), while the waypoint use case is handed to the ViewModel only (`:448`) (inferred from the wiring; I did not read `WayHomeRouting`).
5. **`climbFrom` never starts after the distance (read).** `placeOnWayHome` passes `line.alongMeters[figureIndex]`, the distance's own node (`WayHomeProgress.kt:170`), and `climbFrom` starts at the last sample at or before it, `coerceAtLeast(0)` (`WayHomeHeights.kt:94-99`). Banking from that sample's height, the climb covers at least what the distance covers. The 25 m trail allowance and `metersPastLineStart` are flat, as before. Revert 5 (`start = 0`) fails "halfway down the trail only what is left of it is counted" (run 40418030391), which is the right test for it.
6. **No barometer, no allowance, logged once (read).** `isBarometricTrack` false → `WayBackClimb.NoBarometer` (`ClimbAllowance.kt:66-80`); `climbAllowanceMillis` 0 (`:60-64`); `logNoClimbAllowanceOnce` behind a per-recording flag reset in `reset` (`SundownWatch.kt:147`, `:308`, `:402-412`, `:435`). The test runs three ticks and sees one log line. **One wrinkle (N3):** the predicate is over the track so far, so a barometric phone whose first kept point has no pressure (the sensor's first sample can lag the first fix; Juniper found 1 of A's 14 gaps at the start) logs "this recording's heights are not from the barometer" on the first tick, the allowance returns at the second point, and the flag then stops a later, true log for that recording.
7. **The shared predicate (read).** `trackHeights` now calls `isBarometricTrack` (`TrackHeights.kt:18-19`) with the same rule as before (`withPressure > 0 && withPressure * 2 >= size`; Juniper quotes the old form). Behaviour unchanged.
8. **`bankedClimb` copied from `ComputeTrackStatisticsUseCase` (read).** Line for line the same loop (`ClimbAllowance.kt:91-111` against `ComputeTrackStatisticsUseCase.kt:76-91`): both-null reset, `reference ?: previous`, bank at ≥ threshold, else keep the reference. The pin (`ClimbAllowanceTest`, "banked as the journal banks") runs both over one walk with ±0.15 hPa jitter and two hills, 1e-9 tolerance, and asserts the walk has over 50 m to bank. **Is it enough?** It would catch a threshold or reference-rule change in either copy. It does not exercise the gap rule through the use case; after #420 a barometric series is `null` only before the first reading and after the last (`BarometricHeight.kt:46`), so that rule now only decides where banking starts. Adequate; a comment that the pin is what keeps the two copies equal would help the next editor (N4).
9. **The corrected test bound, b5d464483 (read, derived, computed).** The first run failed at 110.0 s against 132 s with ±2 thresholds (`SundownWatchClimbAllowanceTest.kt:90`, log 40417902441). The coder's derivation holds: with a 4 m hysteresis, the climb's unbanked remainder *u* (< 4 m) at the turn is lost once from the climb and again from the descent, since the descent is measured from the last banked height, and up to one threshold is unbanked at the end: under three thresholds for one turn, one for none, never over. 110 s is 50 m of 60, inside that. The new bound [104.6 s, 133 s] still rejects 0 s (no allowance), 44 s (climb only) and 88 s (descent only), and `ClimbAllowanceTest` pins the 2.2 exactly, so the Sundown test's job (through `tick`, the line) is intact. **It is not a weakening to fit a defect:** the under-read is the journal's own rule, which the owner chose as the measure ("the climb measured by the fixed logic", Amendment 1). On the real walk the question "how much does the 4 m hysteresis lose on undulating ground" has an answer in the fit's own terms: along each route home, the 4 m-banked figure is **21.9 m against 23.9 m** of Juniper's leg regressor (A) and **112.5 against 118.2 m** (B), 8 % and 5 % under. The raw step sum along the route (hysteresis 0) is 57.5 m on A, but A's route is stitched through 35 joins between visits minutes apart, and the stitches carry the barometer's drift as false steps (inferred: the leg regressor on the same route agrees with the banked figure, not with the raw sum). So the coefficient and the banked measure are consistent on the data the coefficient came from. One caution stands: `ElevationHysteresis`' own doc calls 4 m a figure for GPS noise (`ComputeTrackStatisticsUseCase.kt:109-115`); it is now also the barometer's, by the owner's choice of measure, and nothing in the code says so (N2).
10. **Never-short and leans-long (-1620, -1623) (read, computed).** The distance's node rules are untouched (`WayHomeProgress.kt:167-170`); the climb counts from the same node (§3.5), so the allowance can only add to a never-short figure. On the lean: at the far points the allowance is 9 to 11 % of the pace time, on top of the +19/+44 % Juniper measured for the pace-only figure, the direction -1623 accepted. **A pre-existing fact worth the owner's eye (N5):** A's route home at the far point is 459 m by self-joins, against the 1,121 m the walker then walked back; the pace-only figure there is 8 min 45 s where the walker's actual moving time was 14 min 51 s. "Never short" is a property of the line shown (-1498 answer 4), not of the walk the walker takes; the allowance follows the same line, so it inherits that, consistently. Not this PR's to change.
11. **The tick cost (read, computed on CI, inferred for the phone).** The tick runs on `Dispatchers.Default` (`TrackRecordingService.kt:91`), once per 15 s (`:273-279`, `SUNDOWN_TICK_MILLIS`), plus once on the first fix and once when an alert's due time has passed (`SundownWatch.onFix`, `:225-236`), all serialised by `tickMutex` (`:239`). `barometricHeights` filters the whole run for every point's window twice (`BarometricHeight.kt:60`, `:83`): O(n²), beside `joinedTrackRoute`, already per tick. CI figures from the green run's XML (`ClimbAllowanceCostTest`, medians of 9 after 10 warm-ups): **1,135 points: heights 9.3 ms, whole estimate 17.8 ms; 2,880 points: heights 88.4 ms, whole estimate 93.7 ms.** For the phone (inferred): 2,880 points is the ceiling of a 4-hour walk at the sampler's 5 s (`TrackRecordingMode.kt:27`); the 10-24 outing made 1,135 in 4.5 h because stops make no points. At 2 to 4 times the runner's speed for boxed-`Double` list code on ART, 200 to 400 ms per tick, 1 to 3 % of one core while recording; an 8-hour walk at 5 s (5,760 points) would be 0.8 to 1.6 s per tick, still well inside the 15 s and off the main thread. **No risk to the service**: no ANR path, ticks cannot pile up, and an exception is caught per tick (`:371-378`). Worth noting for later, not now: a sliding window would make the two passes linear. **The dispatch said "measure … and state it"; the doc does not state the figure** (S1).
12. **The reverts (read).** All four ran at 438aade31, before b5d464483 changed the hill test's bound, filtered to 13 tests:
    - Revert 1 (drop `+ climbMillis`): fails the hill test, the trail-share test, the no-drop-at-Return test.
    - Revert 2 (drop the track parts' heights): fails the three Sundown tests and all four `WayHomeClimbTest` cases.
    - Revert 4 (`trackHeights` instead of barometric-or-null, so GPS heights count): fails the barometric-rule test, the hill test, the no-barometer-says-so-once test, and `WayHomeClimbTest`'s no-barometer case.
    - Revert 5 (`climbFrom` from 0): fails the hill test and "halfway down the trail only what is left".
    Each run has at least one failure only that revert could produce. **But the hill test's failure appears in all four for the same reason it failed in 40417902441: the ±2-threshold bound, not the revert.** Under the final bound I read it as biting on revert 1 (0 s against ≥ 104.6 s) and passing on reverts 2, 4 and 5 (before Return, barometric), which is right; that reading was not run. The coder's report must not cite the hill test as any revert's bite from these runs (S2). There is no revert 3 in the run list; the planner's brief said "reverts 1-4", the runs are 1, 2, 4, 5 (N6).
13. **File sizes (read).** New files: `ClimbAllowance.kt` 112, `WayHomeHeights.kt` 103, tests 60 to 159 lines. Grown files: `SundownWatch.kt` 481, `MovingPace.kt` 299, `WayHomeProgress.kt` 258, `FindWayHomeUseCase.kt` 213. None near 800.
14. **Clean merge (read).** §0: base is current pre-main, merge-tree clean, GitHub `CLEAN`.

## 4. My own revert check

No Gradle ran here (read-only, CI-only builds). In its place: my port with #420's bridging taken out reproduces the pre-#420 figures (31.2/36.9, 41.0/33.6) and with it the post-#420 ones (52.8/43.1, 72.4/68.7); at the far points the two differ on B (84.9 against 112.5 m) and not on A (21.9 both, whose route home has no mid-route gap). The port tells the two states apart, so its agreement with the coder's 48 s and 4 min 7 s is evidence and not coincidence.

## 5. Verdict

**Blocking:** none.

**Should-fix:**
- **S1.** State the cost figure where the dispatch asked (the doc, and the PR or report): 9.3 / 17.8 ms at 1,135 points, 88.4 / 93.7 ms at 2,880 on the CI JVM, with the phone inference marked as such.
- **S2.** The reverts' record: say that all four ran at 438aade31 under the old bound, that the hill test's failure in them is the bound's and not the revert's, and that revert 1's bite on the hill test under the final bound is inferred, not run (or run it once). Account for the missing revert 3.
- **S3.** `logNoClimbAllowanceOnce` can fire on a barometric phone's first tick (§3.6) and then say nothing true later. Gate it on a few points, or word it as "so far".

**Notes:**
- **N1.** PR body: "13 of 14" red over stubs; the log says 11 of 14.
- **N2.** `ElevationHysteresis` documents 4 m as a GPS-noise figure; it is now also the barometer's banking for a safety figure, by the owner's choice of measure. A sentence there, and the §3.9 figures (8 % and 5 % under the fit's regressor on the walk), would stop a later reader from "fixing" either.
- **N3.** (see S3).
- **N4.** A line on `bankedClimb` saying the pin test is what keeps the two copies equal.
- **N5.** The self-joined route home can be far shorter than the way the walker goes back (A: 459 m against 1,121 m walked; 8 min 45 s against 14 min 51 s moving). Pre-existing, by -1498 answer 4; the allowance follows it consistently. For the owner's awareness, not this PR.
- **N6.** No revert 3.
- **N7.** The stand-in's two ways of reading short (§3.3) are recorded in code and doc; nothing on screen distinguishes a stand-in from measured heights, as the dispatch allowed.

**Mergeable:** yes. S1 to S3 are small and can land before or after the merge at the planner's call; none changes behaviour the tests cover.

## 6. Disclosure

**Read:** every file:line above, on `origin/climb-allowance` b5d464483 (and pre-main where unchanged); the seven run logs and the green run's XML; the dispatch, Amendment 1, RECORD -1498, -1620, -1623, -1628, -1629, -1687, -1689, -1691; the Phase 1 report; Juniper's report and scripts; my #420 review's figures.

**Computed:** everything in §2 and §3.9's route figures, from `recompute.py` (my own ports of `BarometricHeight.kt`, `TrackSelfJoin.kt`'s search, `bankedClimb`, `movingPace`, and Juniper's leg regressor), on the two 10-24 recordings; the test count.

**Inferred:** the phone cost (§3.11); that the raw step sum along A's stitched route carries barometric drift; the waypoint line never reaching sundown (from `AppContainer.kt` wiring, `WayHomeRouting` not read); the hill test's bite under the final bound on revert 1.

**Could not determine:** the coder's completion report (not in any branch I could find), so the 48 s and 4 min 7 s were taken from the planner's brief; the phone's actual tick time; what revert 3 was.

**Premises checked and found wrong:** the brief's "reverts 1-4" (the runs are 1, 2, 4, 5); the PR body's "13 of 14".
